import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { AlertTriangle, ArrowDownToLine, ArrowLeft, ArrowUpFromLine, Banknote, Loader2, PiggyBank, ShoppingCart, Wallet } from 'lucide-react';
import { Button } from '../ui/button';
import { DatePicker } from '../ui/datepicker';
import { DecimalInput } from '../ui/decimal-input';
import { Dialog, DialogHeader, DialogTitle } from '../ui/dialog';
import { Select } from '../ui/select-custom';
import { validateMoneyDraft } from '../../features/finanzen/moneyDrafts';
import { formatDecimalInput } from '../../lib/numberInput';
import type { KasseEinstellung, Sachkonto } from '../../types';
import { formatEuro, modalInputCls } from './belegFormat';
import { KACHELN, pflichtfelderFehlen, type BuchungsArt, type KachelDefinition, type NeueBuchungFormular } from './neueBuchungRegeln';

export interface SaldoInfo { saldo: number; mindestbestand: number; }
interface Kostenstelle { id: number; bezeichnung: string; nummer?: string | null; }
interface OffeneRechnung { id: number; dokumentNummer: string; datum: string; bruttoBetrag: number; }
const heute = () => new Date().toISOString().slice(0, 10);
const icons = { Banknote, ShoppingCart, ArrowDownToLine, ArrowUpFromLine, PiggyBank, Wallet };
const leer = (): NeueBuchungFormular => ({ betrag: '', datum: heute(), gegenpartei: '', beschreibung: '', sachkontoId: '', grundOhneBeleg: '', keinBelegVorhanden: false });

export function NeueBuchungDialog({ offen, sachkonten, onClose, onGebucht }: { offen: boolean; sachkonten: Sachkonto[]; onClose: () => void; onGebucht: (meldung: string) => void; }) {
    const [art, setArt] = useState<BuchungsArt | null>(null);
    const [formular, setFormular] = useState(leer);
    const [mwstSatz, setMwstSatz] = useState(19);
    const [kostenstelleId, setKostenstelleId] = useState('');
    const [ausgangsrechnungId, setAusgangsrechnungId] = useState('');
    const [datei, setDatei] = useState<File | null>(null);
    const [kostenstellen, setKostenstellen] = useState<Kostenstelle[]>([]);
    const [rechnungen, setRechnungen] = useState<OffeneRechnung[]>([]);
    const [fehler, setFehler] = useState<string | null>(null);
    const [konflikt, setKonflikt] = useState<SaldoInfo | null>(null);
    const [hinweis, setHinweis] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);
    const kachel = useMemo(() => KACHELN.find(item => item.art === art) ?? null, [art]);

    useEffect(() => {
        if (!offen || !art) return;
        let abgebrochen = false;
        void Promise.all([
            fetch('/api/bestellungen-uebersicht/kostenstellen').then(r => r.ok ? r.json() : []).catch(() => []),
            art === 'GELD_EINGENOMMEN' ? fetch('/api/buchhaltung/kassenbuch/offene-ausgangsrechnungen').then(r => r.ok ? r.json() : []).catch(() => []) : Promise.resolve([]),
        ]).then(([stellen, offene]) => {
            if (!abgebrochen) { setKostenstellen(Array.isArray(stellen) ? stellen : []); setRechnungen(Array.isArray(offene) ? offene : []); }
        });
        return () => { abgebrochen = true; };
    }, [offen, art]);

    const waehleArt = (neu: KachelDefinition) => {
        setArt(neu.art);
        setFormular({ ...leer(), sachkontoId: neu.art === 'GELD_EINGENOMMEN' ? String(sachkonten.find(k => k.nummer === '8400')?.id ?? '') : '' });
        setMwstSatz(19); setKostenstelleId(''); setAusgangsrechnungId(''); setDatei(null); setFehler(null); setKonflikt(null); setHinweis(null);
    };
    const schliessen = () => { setArt(null); setFehler(null); setKonflikt(null); setHinweis(null); onClose(); };
    const request = () => {
        const geld = validateMoneyDraft(formular.betrag);
        return { art, belegDatum: formular.datum, betragBrutto: geld.valid ? geld.value : null, mwstSatz: kachel?.brauchtMwst ? mwstSatz : null,
            gegenpartei: formular.gegenpartei.trim() || null, beschreibung: formular.beschreibung.trim() || null,
            sachkontoId: formular.sachkontoId ? Number(formular.sachkontoId) : null, kostenstelleId: kostenstelleId ? Number(kostenstelleId) : null,
            ausgangsrechnungId: ausgangsrechnungId ? Number(ausgangsrechnungId) : null, keinBelegVorhanden: formular.keinBelegVorhanden,
            grundOhneBeleg: formular.grundOhneBeleg.trim() || null };
    };
    const senden = async () => {
        if (!art || !kachel) return;
        const pflichtfehler = pflichtfelderFehlen(art, formular);
        const geld = validateMoneyDraft(formular.betrag);
        if (pflichtfehler) { setFehler(pflichtfehler); return; }
        if (!geld.valid || geld.value <= 0) { setFehler(!geld.valid ? geld.message : 'Bitte trag einen positiven Betrag ein.'); return; }
        if (art === 'GELD_AUSGEGEBEN' && !formular.keinBelegVorhanden && !datei) { setFehler('Bitte häng einen Beleg an oder wähle „Kein Beleg vorhanden“.'); return; }
        setSaving(true); setFehler(null); setHinweis(null);
        try {
            const daten = new FormData();
            daten.append('daten', new Blob([JSON.stringify(request())], { type: 'application/json' }));
            if (datei) daten.append('datei', datei);
            const antwort = await fetch('/api/buchhaltung/kassenbuch/buchungen', { method: 'POST', body: daten });
            const body = await antwort.json().catch(() => ({}));
            if (antwort.ok) { onGebucht(art === 'GELD_EINGENOMMEN' ? 'Das Programm hat eine Quittung erzeugt und an die Buchung gehängt.' : 'Buchung gespeichert.'); schliessen(); return; }
            if (antwort.status === 409 && typeof body.projizierterSaldo === 'number') { setKonflikt({ saldo: body.projizierterSaldo, mindestbestand: body.mindestbestand ?? 0 }); return; }
            setFehler(body.message ?? 'Die Buchung konnte nicht gespeichert werden.'); setHinweis(body.hinweis ?? null);
        } catch { setFehler('Die Buchung konnte nicht gespeichert werden. Prüfe bitte die Internetverbindung.'); }
        finally { setSaving(false); }
    };
    const loeseUnterdeckung = async () => {
        if (!konflikt) return;
        setSaving(true);
        try {
            const betrag = Math.max(0, konflikt.mindestbestand - konflikt.saldo);
            const res = await fetch('/api/buchhaltung/kasse/privateinlage', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ betrag, datum: formular.datum, beschreibung: 'Privateinlage vor Buchung' }) });
            if (!res.ok) { const body = await res.json().catch(() => ({})); setFehler(body.message ?? 'Die Privateinlage konnte nicht gebucht werden.'); return; }
            setKonflikt(null); await senden();
        } catch { setFehler('Die Privateinlage konnte nicht gebucht werden.'); } finally { setSaving(false); }
    };
    if (!offen) return null;
    return <ModalShell title={art ? kachel?.titel ?? 'Neue Buchung' : 'Neue Buchung'} onClose={schliessen} wide>
        {!art ? <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">{KACHELN.map(item => {
            const Icon = icons[item.icon as keyof typeof icons];
            return <button key={item.art} type="button" onClick={() => waehleArt(item)} className="min-w-0 rounded-lg border border-slate-200 bg-white p-4 text-left transition hover:border-rose-300 hover:bg-rose-50 focus:outline-none focus:ring-2 focus:ring-rose-500">
                <Icon className="mb-3 h-5 w-5 text-rose-600" /><div className="font-semibold text-slate-900">{item.titel}</div><div className="text-sm text-slate-500">{item.untertitel}</div>
            </button>;
        })}</div> : <Formular kachel={kachel!} art={art} formular={formular} setFormular={setFormular} mwstSatz={mwstSatz} setMwstSatz={setMwstSatz} sachkonten={sachkonten} kostenstellen={kostenstellen} kostenstelleId={kostenstelleId} setKostenstelleId={setKostenstelleId} rechnungen={rechnungen} ausgangsrechnungId={ausgangsrechnungId} setAusgangsrechnungId={setAusgangsrechnungId} datei={datei} setDatei={setDatei} fehler={fehler} hinweis={hinweis} konflikt={konflikt} saving={saving} onBack={() => { setArt(null); setFehler(null); }} onClose={schliessen} onSubmit={senden} onResolve={loeseUnterdeckung} />}
    </ModalShell>;
}

function Formular(props: { kachel: KachelDefinition; art: BuchungsArt; formular: NeueBuchungFormular; setFormular: React.Dispatch<React.SetStateAction<NeueBuchungFormular>>; mwstSatz: number; setMwstSatz: (n: number) => void; sachkonten: Sachkonto[]; kostenstellen: Kostenstelle[]; kostenstelleId: string; setKostenstelleId: (v: string) => void; rechnungen: OffeneRechnung[]; ausgangsrechnungId: string; setAusgangsrechnungId: (v: string) => void; datei: File | null; setDatei: (f: File | null) => void; fehler: string | null; hinweis: string | null; konflikt: SaldoInfo | null; saving: boolean; onBack: () => void; onClose: () => void; onSubmit: () => void; onResolve: () => void; }) {
    const { kachel, art, formular, setFormular } = props;
    return <><Button variant="ghost" size="sm" className="mb-3 text-rose-700 hover:bg-rose-50" onClick={props.onBack}><ArrowLeft className="mr-1 h-4 w-4" /> Zurück</Button>
        <FieldRow label="Betrag (€)"><DecimalInput aria-label="Betrag (€)" value={formular.betrag} onChange={betrag => setFormular(v => ({ ...v, betrag }))} className={modalInputCls} autoFocus /></FieldRow>
        <FieldRow label="Datum"><DatePicker aria-label="Buchungsdatum" value={formular.datum} onChange={datum => setFormular(v => ({ ...v, datum }))} /></FieldRow>
        {kachel.brauchtGegenpartei && <FieldRow label={art === 'GELD_EINGENOMMEN' ? 'Von wem?' : 'An wen?'}><input aria-label={art === 'GELD_EINGENOMMEN' ? 'Von wem?' : 'An wen?'} value={formular.gegenpartei} onChange={e => setFormular(v => ({ ...v, gegenpartei: e.target.value }))} className={modalInputCls} /></FieldRow>}
        {(art === 'GELD_EINGENOMMEN' || art === 'GELD_AUSGEGEBEN') && <FieldRow label="Wofür?"><input aria-label="Wofür?" value={formular.beschreibung} onChange={e => setFormular(v => ({ ...v, beschreibung: e.target.value }))} className={modalInputCls} /></FieldRow>}
        {kachel.brauchtKonto && <FieldRow label="Wofür? (Konto)"><Select aria-label="Wofür? (Konto)" value={formular.sachkontoId} onChange={sachkontoId => setFormular(v => ({ ...v, sachkontoId }))} options={[{ value: '', label: '– bitte wählen –' }, ...props.sachkonten.filter(k => k.kontoTyp === kachel.kontoTyp).map(k => ({ value: String(k.id), label: `${k.nummer ?? ''} ${k.bezeichnung}`.trim(), gruppe: k.kontoTyp === 'ERTRAG' ? 'Einnahmen' : 'Ausgaben' }))]} /></FieldRow>}
        {kachel.brauchtMwst && <FieldRow label="Mehrwertsteuer"><div className="flex gap-2">{[19, 7, 0].map(satz => <Button key={satz} type="button" size="sm" variant={props.mwstSatz === satz ? 'default' : 'outline'} disabled={formular.keinBelegVorhanden} className={props.mwstSatz === satz ? 'bg-rose-600 text-white hover:bg-rose-700' : 'border-rose-300 text-rose-700 hover:bg-rose-50'} onClick={() => props.setMwstSatz(satz)}>{satz} %</Button>)}</div></FieldRow>}
        {art === 'GELD_EINGENOMMEN' && <FieldRow label="Zu welcher Rechnung?"><Select aria-label="Zu welcher Rechnung?" value={props.ausgangsrechnungId} onChange={props.setAusgangsrechnungId} options={[{ value: '', label: '– keine Rechnung zuordnen –' }, ...props.rechnungen.map(r => ({ value: String(r.id), label: `${r.dokumentNummer} · ${r.datum} · ${formatEuro(r.bruttoBetrag)} €` }))]} /><p className="mt-2 rounded-md border border-indigo-200 bg-indigo-50 p-2 text-xs text-indigo-900">Wenn du eine Rechnung wählst, wird sie automatisch als bezahlt markiert.</p></FieldRow>}
        <FieldRow label="Baustelle / Bereich (optional)"><Select aria-label="Baustelle / Bereich" value={props.kostenstelleId} onChange={props.setKostenstelleId} options={[{ value: '', label: '– keine Auswahl –' }, ...props.kostenstellen.map(k => ({ value: String(k.id), label: `${k.nummer ? `${k.nummer} ` : ''}${k.bezeichnung}` }))]} /></FieldRow>
        {art === 'GELD_AUSGEGEBEN' && <BelegAuswahl formular={formular} setFormular={setFormular} setMwstSatz={props.setMwstSatz} setDatei={props.setDatei} />}
        {props.fehler && <p role="alert" className="rounded-md border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{props.fehler}</p>}{props.hinweis && <p className="mt-2 rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">{props.hinweis}</p>}
        {props.konflikt && <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900"><p className="font-medium">Kasse würde auf {formatEuro(props.konflikt.saldo)} € rutschen.</p><p className="mt-1 text-xs">Mindestbestand: {formatEuro(props.konflikt.mindestbestand)} €</p><Button size="sm" disabled={props.saving} onClick={props.onResolve} className="mt-2 bg-rose-600 text-white hover:bg-rose-700">Privateinlage in Höhe {formatEuro(Math.max(0, props.konflikt.mindestbestand - props.konflikt.saldo))} € vorab buchen</Button></div>}
        <ModalFooter onClose={props.onClose} onSubmit={props.onSubmit} saving={props.saving} label="Buchen" /></>;
}

function BelegAuswahl({ formular, setFormular, setMwstSatz, setDatei }: { formular: NeueBuchungFormular; setFormular: React.Dispatch<React.SetStateAction<NeueBuchungFormular>>; setMwstSatz: (satz: number) => void; setDatei: (datei: File | null) => void; }) {
    return <div className="mb-3 rounded-lg border border-slate-200 p-3"><div className="flex flex-wrap gap-2"><Button type="button" size="sm" variant={!formular.keinBelegVorhanden ? 'default' : 'outline'} className={!formular.keinBelegVorhanden ? 'bg-rose-600 text-white hover:bg-rose-700' : 'border-rose-300 text-rose-700 hover:bg-rose-50'} onClick={() => setFormular(v => ({ ...v, keinBelegVorhanden: false }))}>Beleg anhängen</Button><Button type="button" size="sm" variant={formular.keinBelegVorhanden ? 'default' : 'outline'} className={formular.keinBelegVorhanden ? 'bg-rose-600 text-white hover:bg-rose-700' : 'border-rose-300 text-rose-700 hover:bg-rose-50'} onClick={() => { setFormular(v => ({ ...v, keinBelegVorhanden: true })); setMwstSatz(0); setDatei(null); }}>Kein Beleg vorhanden</Button></div>{formular.keinBelegVorhanden ? <><FieldRow label="Warum gibt es keinen Beleg?"><input aria-label="Warum gibt es keinen Beleg?" value={formular.grundOhneBeleg} onChange={e => setFormular(v => ({ ...v, grundOhneBeleg: e.target.value }))} className={`${modalInputCls} mt-3`} /></FieldRow><p className="flex gap-2 rounded-md border border-amber-200 bg-amber-50 p-2 text-xs text-amber-900"><AlertTriangle className="h-4 w-4 shrink-0" />Ohne Fremdbeleg gibt es keine Vorsteuer.</p></> : <input aria-label="Beleg anhängen" type="file" accept="image/*,application/pdf" className="mt-3 block w-full text-sm" onChange={e => setDatei(e.target.files?.[0] ?? null)} />}</div>;
}

/** Bleibt für Task 14 exportiert. */
export function LohnZahlungModal({ onClose, onSuccess, onError }: { onClose: () => void; onSuccess: (meldung: string) => void; onError: (meldung: string) => void; }) {
    const [einstellung, setEinstellung] = useState<KasseEinstellung | null>(null);
    const [betrag, setBetrag] = useState(''); const [datum, setDatum] = useState(heute()); const [empfaenger, setEmpfaenger] = useState('');
    const [saldo, setSaldo] = useState<SaldoInfo | null>(null); const [saving, setSaving] = useState(false);
    useEffect(() => {
        fetch('/api/buchhaltung/kasse/einstellung').then(r => r.ok ? r.json() : null).then((e: KasseEinstellung | null) => {
            if (!e) return; setEinstellung(e); if (e.ehegattengehaltBetrag) setBetrag(formatDecimalInput(e.ehegattengehaltBetrag)); if (e.ehegattengehaltEmpfaengerName) setEmpfaenger(e.ehegattengehaltEmpfaengerName);
        }).catch(() => {});
        fetch('/api/buchhaltung/kasse/saldo').then(r => r.ok ? r.json() : null).then((s: SaldoInfo | null) => s && setSaldo(s)).catch(() => {});
    }, []);
    const geld = validateMoneyDraft(betrag); const wert = geld.valid ? geld.value : 0; const valid = geld.valid && wert > 0;
    const buchen = async () => { if (!geld.valid || !valid) { onError(!geld.valid ? geld.message : 'Bitte einen positiven Betrag eingeben.'); return; } setSaving(true); try { const res = await fetch('/api/buchhaltung/kasse/lohn-zahlung', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ betrag: wert, datum, empfaengerName: empfaenger || null }) }); if (!res.ok) { const body = await res.json().catch(() => ({})); onError(body.message ?? 'Buchung fehlgeschlagen'); return; } onSuccess(`Ehegattengehalt ${formatEuro(wert)} € gebucht`); } catch { onError('Netzwerkfehler'); } finally { setSaving(false); } };
    const projiziert = saldo && valid ? saldo.saldo - wert : null;
    return <ModalShell title="Ehegattengehalt zahlen" onClose={onClose}>{einstellung?.ehegattengehaltAktiv && <div className="mb-3 rounded border border-slate-200 bg-slate-50 p-2 text-xs text-slate-500">Default-Werte aus den Einstellungen geladen. Anpassbar für diese Buchung.</div>}<FieldRow label="Empfänger (Name)"><input value={empfaenger} onChange={e => setEmpfaenger(e.target.value)} className={modalInputCls} /></FieldRow><FieldRow label="Monat (zur Notiz)"><DatePicker aria-label="Buchungsdatum" value={datum} onChange={setDatum} /></FieldRow><FieldRow label="Betrag (€)"><DecimalInput aria-label="Betrag (€)" value={betrag} onChange={setBetrag} className={modalInputCls} autoFocus /></FieldRow>{projiziert != null && <div className="rounded border border-slate-200 bg-slate-50 p-2 text-xs text-slate-600">Kassenstand nach Zahlung: <strong>{formatEuro(projiziert)} €</strong></div>}<ModalFooter onClose={onClose} onSubmit={buchen} saving={saving} label="Lohn buchen" /></ModalShell>;
}

export function ModalShell({ title, onClose, wide, children }: { title: string; onClose: () => void; wide?: boolean; children: ReactNode; }) { return <Dialog open onOpenChange={open => { if (!open) onClose(); }} aria-label={title} className={`w-full ${wide ? 'max-w-xl' : 'max-w-md'}`}><DialogHeader><DialogTitle>{title}</DialogTitle></DialogHeader><div className="mt-4 min-h-0 overflow-y-auto p-1 -mx-1">{children}</div></Dialog>; }
export function FieldRow({ label, children }: { label: string; children: ReactNode; }) { return <div className="mb-3"><label className="mb-1 block text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</label>{children}</div>; }
export function ModalFooter({ onClose, onSubmit, saving, label }: { onClose: () => void; onSubmit: () => Promise<void> | void; saving: boolean; label: string; }) { return <div className="mt-4 flex justify-end gap-2 border-t border-slate-200 pt-3"><Button variant="outline" size="sm" onClick={onClose} disabled={saving}>Abbrechen</Button><Button size="sm" onClick={onSubmit} disabled={saving} className="bg-rose-600 text-white hover:bg-rose-700">{saving && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}{label}</Button></div>; }
