import { useEffect, useMemo, useRef, useState } from 'react';
import {
    AlertCircle, CheckCircle2, ChevronDown, ChevronRight, FileText,
    Loader2, Lock, Receipt, Save, Trash2, Truck, Undo2,
} from 'lucide-react';
import { Button } from '../ui/button';
import { Select } from '../ui/select-custom';
import { LieferantSearchModal, type LieferantSuchErgebnis } from '../LieferantSearchModal';
import { KostenstellenSplitsEditor, type KostenstellenSplit } from './KostenstellenSplitsEditor';
import { StornoDialog } from './StornoDialog';
import { VerwerfenDialog } from './VerwerfenDialog';
import { nettoAusBrutto, schluesseleAuf } from '../../lib/mwst';
import type { Beleg, BelegKategorie, Sachkonto, Zahlungsart } from '../../types';
import {
    buildSachkontoOptions, buildZahlungsartOptions,
    formatDateTime, formatEuro, gesperrtCls, inputCls,
} from './belegFormat';
import { VorschlagsChip } from './VorschlagsChip';
import { Dialog } from '../ui/dialog';
import { DecimalInput } from '../ui/decimal-input';
import { DatePicker } from '../ui/datepicker';
import { useToast } from '../ui/toast';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import { validateKostenstellenSplits } from '../../features/finanzen/kostenstellenDrafts';
import { validateNumberDrafts } from '../../lib/numberDrafts';
import { fragtNachZahlung, folgeSatz, giltAlsBezahlt, kategorieAusZahlungsart } from './zahlungsartRegeln';
import { istEinfacheKostenstellenZuordnung } from './kostenstellenModus';

// Task 9 (reine Verschiebung, kein Verhalten geaendert): heutiger
// BelegDetailModal aus BelegeKasseEditor.tsx (Zeilen 1160-2137) samt
// AufteilungsSektion, Field, BelegPreview. Props unveraendert.

// ===================== Detail / Validierungs-Modal =====================

export function BelegDetailModal({ beleg, sachkonten, zahlungsarten, onClose, onSaved, onDeleted }: {
    beleg: Beleg;
    sachkonten: Sachkonto[];
    zahlungsarten: Zahlungsart[];
    onClose: () => void;
    onSaved: (b: Beleg) => void;
    onDeleted: (id: number) => void;
}) {
    const toast = useToast();
    // Detail-Beleg nachladen. Zwei Gruende:
    //  (a) Issue #58: Die Listen-Query liefert positionen[] aus Performance-
    //      Gruenden nicht — der Aufteilungs-Bereich braucht sie aber.
    //  (b) Solange die KI noch analysiert, pollen wir alle 4s nach. Der
    //      Auto-Refresh der Liste pausiert naemlich, sobald ein Beleg offen ist
    //      (`if (!hatOffene || editing) return`) — der Hinweis "Werte erscheinen
    //      automatisch" war im offenen Dialog schlicht gelogen.
    //
    // Wichtig: Das Nachladen fuellt NUR `detailBeleg` (Anzeige), niemals `form`.
    // Sonst wuerde eine spaet eintreffende KI-Antwort die Eingaben ueberschreiben,
    // die der Buchhalter waehrenddessen getippt hat.
    const [detailBeleg, setDetailBeleg] = useState<Beleg>(beleg);
    const detailsBereit = useRef(false);
    const formularBearbeitet = useRef(false);
    const splitsBearbeitet = useRef(false);
    const zahlungBearbeitet = useRef(false);
    // Bewusst nur diese drei Werte als Abhaengigkeit: eine neue `beleg`-Identitaet
    // bei sonst gleichen Werten wuerde das Polling sonst unnoetig neu starten.
    const belegId = beleg.id;
    useEffect(() => {
        const kiOffen = (status?: Beleg['kiAnalyseStatus']) => status === 'PENDING' || status === 'LAEUFT';
        let cancelled = false;
        let timer: ReturnType<typeof setTimeout> | undefined;

        const lade = async () => {
            try {
                const res = await fetch(`/api/buchhaltung/belege/${belegId}`);
                if (!res.ok) return;
                const data: Beleg = await res.json();
                if (cancelled || data.id !== belegId) return;
                setDetailBeleg(data);
                if (!detailsBereit.current) {
                    detailsBereit.current = true;
                    if (!formularBearbeitet.current) setForm(f => ({ ...f, zahlungsart: data.zahlungsart ?? '', lieferantId: data.lieferantId ?? null, lieferantName: data.lieferantName ?? '', sachkontoId: data.sachkontoId ?? null }));
                    if (!splitsBearbeitet.current) {
                        const neueSplits = data.kostenstellenSplits ?? [];
                        const einfach = istEinfacheKostenstellenZuordnung(neueSplits);
                        setSplits(neueSplits); setMehrereKostenstellen(!einfach); setKostenstelleId(einfach ? neueSplits[0]?.kostenstelleId ?? null : null);
                    }
                    if (!zahlungBearbeitet.current) {
                        setBezahlt(data.eingangsrechnungBezahlt ?? false);
                        setBezahltAm(data.eingangsrechnungBezahltAm ?? new Date().toISOString().slice(0, 10));
                    }
                }
                if (kiOffen(data.kiAnalyseStatus)) timer = setTimeout(lade, 4000);
            } catch (e) {
                console.error('Beleg-Detail laden fehlgeschlagen', e);
            }
        };
        lade();

        return () => { cancelled = true; if (timer) clearTimeout(timer); };
    }, [belegId]);

    const [form, setForm] = useState({
        belegKategorie: beleg.belegKategorie,
        belegDatum: beleg.belegDatum ?? '',
        belegNummer: beleg.belegNummer ?? '',
        beschreibung: beleg.beschreibung ?? '',
        betragNetto: beleg.betragNetto == null ? '' : formatDecimalInput(beleg.betragNetto),
        betragBrutto: beleg.betragBrutto == null ? '' : formatDecimalInput(beleg.betragBrutto),
        mwstSatz: beleg.mwstSatz == null ? '' : formatDecimalInput(beleg.mwstSatz),
        zahlungsart: beleg.zahlungsart ?? '',
        lieferantId: beleg.lieferantId ?? null as number | null,
        lieferantName: beleg.lieferantName ?? '',
        sachkontoId: beleg.sachkontoId ?? null as number | null,
        notiz: beleg.notiz ?? '',
    });
    const [zeigeStorno, setZeigeStorno] = useState(false);
    const [zeigeVerwerfen, setZeigeVerwerfen] = useState(false);
    const [splits, setSplits] = useState<KostenstellenSplit[]>(beleg.kostenstellenSplits ?? []);
    const [kostenstellen, setKostenstellen] = useState<{ id: number; bezeichnung: string; nummer?: string | null }[]>([]);
    const [mehrereKostenstellen, setMehrereKostenstellen] = useState(() => !istEinfacheKostenstellenZuordnung(beleg.kostenstellenSplits ?? []));
    const [kostenstelleId, setKostenstelleId] = useState<number | null>(() => {
        const erster = beleg.kostenstellenSplits?.[0];
        return erster && istEinfacheKostenstellenZuordnung(beleg.kostenstellenSplits ?? []) ? erster.kostenstelleId : null;
    });
    const [bezahlt, setBezahlt] = useState(beleg.eingangsrechnungBezahlt ?? false);
    const [bezahltAm, setBezahltAm] = useState(beleg.eingangsrechnungBezahltAm ?? new Date().toISOString().slice(0, 10));
    useEffect(() => {
        fetch('/api/bestellungen-uebersicht/kostenstellen')
            .then(r => r.ok ? r.json() : [])
            .then(data => setKostenstellen(Array.isArray(data) ? data : []))
            .catch(() => toast.error('Baustellen und Bereiche konnten nicht geladen werden.'));
    }, [toast]);
    const [saving, setSaving] = useState(false);
    const [lieferantPicker, setLieferantPicker] = useState(false);
    const [lieferantStartsuche, setLieferantStartsuche] = useState('');
    // Seltene Felder (Beleg-Nr., Netto, MwSt-Satz, Zahlungsart, Lieferant, Notiz)
    // sind eingeklappt. Sie standen bisher gleichberechtigt neben Betrag und
    // Datum — dadurch sahen alle 12 Felder gleich wichtig aus und der Dialog
    // erschlug den Nutzer.
    //
    // Bewusst IMMER zu, auch wenn Werte drinstehen: die KI fuellt Beleg-Nummer
    // und Zahlungsart bei fast jedem Scan, ein "auf, sobald gefuellt" waere
    // also praktisch immer auf. Damit trotzdem nichts unsichtbar verschwindet,
    // zeigt der zugeklappte Knopf, wie viele Felder belegt sind.
    const [mehrDetails, setMehrDetails] = useState(false);
    const [saldoInfo, setSaldoInfo] = useState<{ saldo: number; mindestbestand: number } | null>(null);
    const [konflikt, setKonflikt] = useState<{ projizierterSaldo: number; mindestbestand: number; message: string; entwurf: string } | null>(null);
    // Validierungsfehler als Toast und zusätzlich am Formular anzeigen.
    // Den Hinweis am Formular nach 4s ausblenden.
    const [validationHint, setValidationHintState] = useState<string | null>(null);
    const setValidationHint = (message: string | null) => { setValidationHintState(message); if (message) toast.error(message); };
    useEffect(() => {
        if (!validationHint) return;
        const t = setTimeout(() => setValidationHintState(null), 4000);
        return () => clearTimeout(t);
    }, [validationHint]);

    // Live-Saldo laden, sobald Modal offen — nur fuer Bar-Belege relevant.
    useEffect(() => {
        const barKategorien: BelegKategorie[] = ['KASSE_EINNAHME', 'KASSE_AUSGABE', 'PRIVATENTNAHME', 'PRIVATEINLAGE'];
        if (!barKategorien.includes(form.belegKategorie)) {
            setSaldoInfo(null);
            return;
        }
        fetch('/api/buchhaltung/kasse/saldo')
            .then(r => r.ok ? r.json() : null)
            .then((s) => s && setSaldoInfo(s))
            .catch(err => console.error('Saldo laden fehlgeschlagen', err));
    }, [form.belegKategorie]);

    // Live-Projektion: wie sieht der Saldo nach Validierung dieses Belegs aus?
    const projektion = useMemo(() => {
        if (!saldoInfo) return null;
        const checked = validateNumberDrafts({ brutto: form.betragBrutto }, { brutto: { label: 'Betrag', required: true, maxDecimalPlaces: 2 } });
        if (!checked.valid || checked.values.brutto! <= 0) return null;
        const brutto = checked.values.brutto!;
        const alt = beleg.status === 'VALIDIERT' && beleg.betragBrutto != null
            ? (beleg.belegKategorie === 'KASSE_AUSGABE' || beleg.belegKategorie === 'PRIVATENTNAHME'
                ? -beleg.betragBrutto : beleg.betragBrutto) : 0;
        const neu = form.belegKategorie === 'KASSE_AUSGABE' || form.belegKategorie === 'PRIVATENTNAHME'
            ? -brutto : brutto;
        return saldoInfo.saldo - alt + neu;
    }, [saldoInfo, form.belegKategorie, form.betragBrutto, beleg.status, beleg.belegKategorie, beleg.betragBrutto]);

    const update = <K extends keyof typeof form>(k: K, v: typeof form[K]) => {
        formularBearbeitet.current = true;
        setForm(f => ({ ...f, [k]: v }));
    };

    // Wie viele der eingeklappten Felder sind belegt? Steht als Hinweis am
    // zugeklappten "Mehr Details"-Knopf, damit gefuellte Werte nicht unsichtbar
    // werden (§8 progressive-disclosure darf nichts verstecken, nur ordnen).
    const offeneDetails = [
        form.belegNummer, form.zahlungsart, form.notiz,
        form.betragNetto === '' ? null : form.betragNetto,
        form.mwstSatz === '' ? null : form.mwstSatz,
        form.lieferantId,
    ].filter(v => v != null && v !== '').length;

    // Brutto → Netto/MwSt aufschluesseln. Rein zur Anzeige; geschrieben wird
    // erst beim Klick auf einen MwSt-Knopf. Die Rechnung selbst liegt in
    // src/utils/mwst.ts — dort ist sie einzeln testbar, was bei Geldbetraegen
    // die Stelle ist, an der sich ein Rundungsfehler am teuersten raecht.
    const aufschluesselung = (() => {
        const checked = validateNumberDrafts({ brutto: form.betragBrutto, satz: form.mwstSatz }, {
            brutto: { label: 'Betrag', required: true, maxDecimalPlaces: 2 }, satz: { label: 'MwSt-Satz', required: true, min: 0, max: 100, maxDecimalPlaces: 2 },
        });
        return checked.valid ? schluesseleAuf(checked.values.brutto!, checked.values.satz!) : null;
    })();
    const berechnetesNetto = aufschluesselung?.netto ?? null;
    const berechneteMwst = aufschluesselung?.mwst ?? null;

    // Setzt MwSt-Satz und rechnet das Netto passend aus. Ohne gueltiges Brutto
    // wird nur der Satz gesetzt — sonst schrieben wir NaN ins Netto-Feld.
    const setzeMwstSatz = (satz: number) => {
        const checked = validateNumberDrafts({ brutto: form.betragBrutto }, { brutto: { label: 'Betrag', required: true, maxDecimalPlaces: 2 } });
        const netto = checked.valid ? nettoAusBrutto(checked.values.brutto!, satz) : null;
        setForm(f => ({ ...f, mwstSatz: formatDecimalInput(satz),
            ...(netto !== null && Number.isFinite(netto) ? { betragNetto: formatDecimalInput(netto) } : {}),
        }));
    };

    const pruefeEntwurf = () => {
        if (!detailsBereit.current) { setValidationHint('Belegdetails werden noch geladen. Bitte kurz warten.'); return null; }
        const zahlungsartIstOptional = beleg.istUmbuchung || beleg.belegKategorie === 'PRIVATEINLAGE' || beleg.belegKategorie === 'PRIVATENTNAHME';
        if (!form.zahlungsart && !zahlungsartIstOptional) { setValidationHint('Bitte wählen Sie aus, wie bezahlt wurde.'); return null; }
        const numbers = validateNumberDrafts({ betragBrutto: form.betragBrutto, betragNetto: form.betragNetto, mwstSatz: form.mwstSatz }, {
            betragBrutto: { label: 'Betrag', required: true, maxDecimalPlaces: 2 },
            betragNetto: { label: 'Netto', maxDecimalPlaces: 2 },
            mwstSatz: { label: 'MwSt-Satz', min: 0, max: 100, maxDecimalPlaces: 2 },
        });
        if (!numbers.valid) { setValidationHint(numbers.message); return null; }
        const einfacheSplits = !mehrereKostenstellen
            ? kostenstelleId == null ? [] : [{ kostenstelleId, prozent: 100, absoluterBetrag: null, streckungJahre: 1, streckungStartJahr: null }] as KostenstellenSplit[] : splits;
        const splitResult = validateKostenstellenSplits(einfacheSplits);
        if (!splitResult.valid) { setValidationHint(splitResult.message); return null; }
        return { values: numbers.values, splits: splitResult.splits };
    };

    const save = async (alsValidiert: boolean) => {
        const checked = pruefeEntwurf();
        if (!checked) return;
        setValidationHint(null);

        setSaving(true);
        setKonflikt(null);
        try {
            const richtungAusgabe = beleg.belegKategorie !== 'KASSE_EINNAHME';
            const abgeleiteteKategorie = (beleg.belegKategorie === 'PRIVATEINLAGE' || beleg.belegKategorie === 'PRIVATENTNAHME' || beleg.istUmbuchung)
                ? beleg.belegKategorie : kategorieAusZahlungsart(form.zahlungsart, richtungAusgabe) ?? form.belegKategorie;
            const zahlungSichtbar = fragtNachZahlung(form.zahlungsart, beleg.dokumentTyp);
            const sofortBezahlt = giltAlsBezahlt(form.zahlungsart);
            const body = {
                belegKategorie: abgeleiteteKategorie,
                status: alsValidiert ? 'VALIDIERT' : undefined,
                belegDatum: form.belegDatum || null,
                belegNummer: form.belegNummer || null,
                beschreibung: form.beschreibung || null,
                betragNetto: checked.values.betragNetto,
                betragBrutto: checked.values.betragBrutto,
                mwstSatz: checked.values.mwstSatz,
                zahlungsart: form.zahlungsart || null,
                zahlungsstatus: zahlungSichtbar || sofortBezahlt ? (sofortBezahlt || bezahlt ? 'BEZAHLT' : 'OFFEN') : undefined,
                bezahltAm: zahlungSichtbar || sofortBezahlt ? (sofortBezahlt || bezahlt ? bezahltAm : null) : undefined,
                lieferantId: form.lieferantId,
                sachkontoId: form.sachkontoId,
                notiz: form.notiz || null,
                kostenstellenSplits: checked.splits.map(s => ({
                    kostenstelleId: s.kostenstelleId,
                    prozent: s.prozent,
                    absoluterBetrag: s.absoluterBetrag,
                    beschreibung: s.beschreibung || null,
                    streckungJahre: s.streckungJahre,
                    streckungStartJahr: s.streckungStartJahr,
                })),
            };
            const res = await fetch(`/api/buchhaltung/belege/${beleg.id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body),
            });
            if (res.ok) {
                const updated: Beleg = await res.json();
                onSaved(updated);
                return;
            }
            if (res.status === 409) {
                const body409 = await res.json();
                setKonflikt({
                    projizierterSaldo: Number(body409.projizierterSaldo),
                    mindestbestand: Number(body409.mindestbestand),
                    message: body409.message ?? 'Kasse würde unter Mindestbestand fallen',
                    entwurf: JSON.stringify({ form, splits }),
                });
                return;
            }
            const body400 = await res.json().catch(() => null);
            toast.error(body400?.message ?? 'Speichern fehlgeschlagen');
        } catch (e) {
            console.error(e);
            toast.error('Netzwerkfehler');
        } finally {
            setSaving(false);
        }
    };

    // 1-Klick-Loesung bei 409: vorab eine Privateinlage in der benoetigten
    // Hoehe buchen und dann nochmal speichern.
    const loeseUnterdeckung = async () => {
        if (!konflikt || !pruefeEntwurf()) return;
        if (konflikt.entwurf !== JSON.stringify({ form, splits })) { await save(true); return; }
        const benoetigt = Math.max(0, konflikt.mindestbestand - konflikt.projizierterSaldo);
        if (benoetigt <= 0) {
            setKonflikt(null);
            return;
        }
        setSaving(true);
        try {
            const heute = new Date().toISOString().slice(0, 10);
            const res = await fetch('/api/buchhaltung/kasse/privateinlage', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    betrag: benoetigt,
                    datum: heute,
                    beschreibung: 'Vorab-Einlage für validierten Beleg',
                }),
            });
            if (!res.ok) {
                toast.error('Vorab-Einlage fehlgeschlagen');
                return;
            }
            setKonflikt(null);
            await save(true);
        } catch {
            toast.error('Die Vorab-Einlage konnte nicht gespeichert werden.');
        } finally {
            setSaving(false);
        }
    };

    // Festgeschrieben = der Monat ist abgeschlossen. Ab hier sperren wir die
    // kassenwirksamen Felder schon in der Oberflaeche, statt den Nutzer erst
    // beim Speichern in eine Fehlermeldung laufen zu lassen. Der Server
    // prueft dasselbe noch einmal — die Sperre hier ist Bequemlichkeit,
    // nicht die Absicherung.
    const istFestgeschrieben = detailBeleg.festgeschrieben === true || beleg.festgeschrieben === true;
    const wurdeStorniert = (detailBeleg.storniertDurchBelegId ?? beleg.storniertDurchBelegId) != null;
    const istGegenbuchung = (detailBeleg.stornoFuerBelegId ?? beleg.stornoFuerBelegId) != null;
    const laufendeNummer = detailBeleg.laufendeNummer ?? beleg.laufendeNummer ?? null;

    return (
        <Dialog open onOpenChange={open => { if (!open) { if (lieferantPicker) setLieferantPicker(false); else onClose(); } }} aria-label="Beleg prüfen" className="w-[98vw] max-w-[98vw] p-0 overflow-hidden">
            <div className="bg-white rounded-xl shadow-2xl w-full max-w-[98vw] max-h-[95vh] flex flex-col overflow-hidden">
                <div className="p-4 border-b border-slate-200 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <Receipt className="w-5 h-5 text-rose-600" />
                        <div>
                            <h2 id="beleg-detail-titel" className="font-bold text-slate-900">
                                {istFestgeschrieben ? 'Beleg ansehen' : 'Beleg prüfen & validieren'}
                                {laufendeNummer != null && (
                                    <span className="ml-2 text-sm font-semibold text-slate-500 tabular-nums">Nr. {laufendeNummer}</span>
                                )}
                            </h2>
                            <p className="text-xs text-slate-500">Hochgeladen {formatDateTime(beleg.uploadDatum)} {beleg.uploadedByName ? `von ${beleg.uploadedByName}` : ''}</p>
                        </div>
                    </div>

                </div>

                <div className="flex-1 overflow-hidden grid grid-cols-1 lg:grid-cols-3 gap-0 min-h-0">
                    {/* Vorschau */}
                    <div className="lg:col-span-1 bg-slate-100 flex flex-col items-stretch p-4 border-r border-slate-200 overflow-auto">
                        <BelegPreview belegId={beleg.id} mimeType={beleg.mimeType} originalDateiname={beleg.originalDateiname} />
                    </div>

                    {/* Form */}
                    <div className="lg:col-span-2 overflow-auto p-6 space-y-5">
                        {/* Festschreibungs-Hinweis ganz oben: er erklaert, warum
                            gleich mehrere Felder nicht mehr bedienbar sind. Ohne
                            ihn wirken die gesperrten Felder wie ein Fehler. */}
                        {istFestgeschrieben && (
                            <div className="bg-slate-100 border border-slate-300 rounded-lg p-3 text-sm text-slate-700 flex gap-2">
                                <Lock className="w-4 h-4 shrink-0 mt-0.5 text-slate-500" aria-hidden />
                                <div>
                                    <p className="font-semibold text-slate-900">Dieser Beleg ist fest gebucht.</p>
                                    <p className="mt-0.5">
                                        Datum, Betrag, MwSt, Art der Buchung, Zahlungsart und Verwendungszweck
                                        lassen sich nicht mehr ändern. Sachkonto, Kostenstelle und Notiz schon –
                                        jede Änderung daran wird protokolliert.
                                    </p>
                                    {wurdeStorniert && (
                                        <p className="mt-1 font-medium">
                                            Diese Buchung wurde bereits durch eine Gegenbuchung aufgehoben.
                                            {detailBeleg.stornoGrund ? ` Grund: ${detailBeleg.stornoGrund}` : ''}
                                        </p>
                                    )}
                                    {istGegenbuchung && (
                                        <p className="mt-1 font-medium">
                                            Das ist selbst eine Gegenbuchung – sie hebt eine frühere Buchung auf.
                                        </p>
                                    )}
                                </div>
                            </div>
                        )}
                        {detailBeleg.kiAnalyseStatus === 'FAILED' && (
                            <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-sm text-red-700">
                                <strong>KI-Analyse fehlgeschlagen:</strong> {detailBeleg.kiFehlerText}
                            </div>
                        )}
                        {detailBeleg.kiAnalyseStatus === 'PENDING' || detailBeleg.kiAnalyseStatus === 'LAEUFT' ? (
                            <div className="bg-sky-50 border border-sky-200 rounded-lg p-3 text-sm text-sky-700 inline-flex items-center gap-2">
                                <Loader2 className="w-4 h-4 animate-spin" />
                                KI-Analyse läuft – der Vorschlag erscheint gleich hier.
                            </div>
                        ) : null}

                        <section>
                            <h3 className="text-base font-bold text-slate-900">Wie viel und wann?</h3>
                            <p className="mt-1 text-sm text-slate-600">Prüfen Sie Betrag und Datum auf dem Beleg.</p>
                        <div className="grid grid-cols-2 gap-3">
                            <Field label="Betrag (€)">
                                <div className="flex items-center gap-2"><DecimalInput aria-label="Betrag (€)" value={form.betragBrutto}
                                    onChange={value => update('betragBrutto', value)}
                                    disabled={istFestgeschrieben}
                                    className={`${inputCls} ${gesperrtCls} text-lg font-semibold tabular-nums`} />
                                    {detailBeleg.kiBetragBrutto === Number(form.betragBrutto.replace(',', '.')) && <KiBadge />}</div>
                            </Field>
                            <Field label="Beleg-Datum">
                                <div className="flex items-center gap-2"><DatePicker aria-label="Beleg-Datum" value={form.belegDatum}
                                    onChange={value => update('belegDatum', value)}
                                    disabled={istFestgeschrieben} />{detailBeleg.kiBelegdatum === form.belegDatum && <KiBadge />}</div>
                            </Field>
                        </div>
                        </section>

                        {/* MwSt per Klick statt Kopfrechnen. Der Klick setzt Satz UND Netto —
                            bewusst als ausdrueckliche Nutzer-Aktion, damit sich Betraege auf
                            einem Steuerbeleg nie von selbst aendern. */}
                        <div className="flex flex-wrap items-center gap-2">
                            <span className="text-xs font-semibold uppercase tracking-wide text-slate-500">MwSt</span>
                            {[19, 7, 0].map(satz => {
                                const checked = validateDecimalInput(form.mwstSatz, { label: 'MwSt-Satz' });
                                const aktiv = checked.valid && checked.value === satz;
                                return (
                                    <button key={satz} type="button" onClick={() => setzeMwstSatz(satz)}
                                        aria-pressed={aktiv}
                                        disabled={istFestgeschrieben}
                                        className={`px-3 py-1 rounded-full text-sm border transition-colors disabled:opacity-50 disabled:cursor-not-allowed ${
                                            aktiv
                                                ? 'bg-rose-600 text-white border-rose-600'
                                                : 'bg-white text-slate-600 border-slate-200 enabled:hover:border-rose-300 enabled:hover:text-rose-700'
                                        }`}>
                                        {satz} %
                                    </button>
                                );
                            })}
                            {berechneteMwst != null && berechnetesNetto != null && (
                                <span className="text-xs text-slate-500 tabular-nums">
                                    = {formatEuro(berechneteMwst)} € MwSt · {formatEuro(berechnetesNetto)} € netto
                                </span>
                            )}
                        </div>

                        <Field label="Beschreibung">
                            <input type="text" value={form.beschreibung}
                                onChange={e => update('beschreibung', e.target.value)}
                                placeholder="z.B. Tankquittung, Büromaterial…"
                                disabled={istFestgeschrieben}
                                className={`${inputCls} ${gesperrtCls}`} />
                        </Field>

                        <section>
                            <h3 className="text-base font-bold text-slate-900">Wie wurde bezahlt?</h3>
                            <p className="mt-1 text-sm text-slate-600">Die Auswahl legt fest, wo die Buchung landet.</p>
                            <div className="mt-3 flex items-center gap-2"><Select value={form.zahlungsart} onChange={v => update('zahlungsart', v)} required aria-label="Wie wurde bezahlt?"
                                disabled={istFestgeschrieben} options={buildZahlungsartOptions(zahlungsarten, form.zahlungsart)} />
                                {detailBeleg.kiZahlungsart === form.zahlungsart && <KiBadge text="von KI erkannt" />}</div>
                            <p className="mt-2 text-sm text-slate-600">{folgeSatz(form.zahlungsart)}</p>
                        </section>
                        {fragtNachZahlung(form.zahlungsart, beleg.dokumentTyp) ? <section>
                            <h3 className="text-base font-bold text-slate-900">Ist die Rechnung schon bezahlt?</h3>
                            <p className="mt-1 text-sm text-slate-600">So bleibt offen, ob die Rechnung noch bezahlt werden muss.</p>
                            <label className="mt-3 flex items-center gap-2 text-sm text-slate-800"><input type="radio" checked={bezahlt} onChange={() => { zahlungBearbeitet.current = true; setBezahlt(true); }} /> Ja, bezahlt am …</label>
                            {bezahlt && <div className="mt-2 max-w-xs"><DatePicker aria-label="Bezahlt am" value={bezahltAm} onChange={value => { zahlungBearbeitet.current = true; setBezahltAm(value); }} /></div>}
                            <label className="mt-2 flex items-center gap-2 text-sm text-slate-800"><input type="radio" checked={!bezahlt} onChange={() => { zahlungBearbeitet.current = true; setBezahlt(false); }} /> Nein, noch nicht bezahlt</label>
                            {detailBeleg.eingangsrechnungId && <a className="mt-3 inline-block text-sm font-medium text-rose-700 hover:underline" target="_blank" rel="noreferrer" href="/rechnungsuebersicht">Zur Eingangsrechnung</a>}
                        </section> : giltAlsBezahlt(form.zahlungsart) && <p className="text-sm text-slate-600">Bar und EC-Karte gelten als sofort bezahlt.</p>}
                        <section>
                            <h3 className="text-base font-bold text-slate-900">Wofür war das?</h3>
                            <p className="mt-1 text-sm text-slate-600">Wählen Sie das passende Konto.</p>
                            <div className="mt-3"><VorschlagsChip vorschlag={detailBeleg.vorschlagSachkonto} hinweis={detailBeleg.kiKostenkontoHinweis} aktuelleId={form.sachkontoId} onUebernehmen={id => update('sachkontoId', id)} was="Konto" /></div>
                            <div className="mt-3">
                                <Select
                                    aria-label="Konto"
                                    value={form.sachkontoId != null ? String(form.sachkontoId) : ''}
                                    onChange={v => update('sachkontoId', v ? Number(v) : null)}
                                    placeholder="– kein Konto zugewiesen –"
                                    options={buildSachkontoOptions(sachkonten)}
                                />
                            </div>
                        </section>
                        <section>
                            <h3 className="text-base font-bold text-slate-900">Für welche Baustelle / welchen Bereich?</h3>
                            <p className="mt-1 text-sm text-slate-600">Ordnen Sie den Betrag einer Baustelle oder einem Bereich zu.</p>
                            <div className="mt-3"><VorschlagsChip vorschlag={detailBeleg.vorschlagKostenstelle} aktuelleId={kostenstelleId} onUebernehmen={id => { splitsBearbeitet.current = true; setKostenstelleId(id); setMehrereKostenstellen(false); }} was="Baustelle" /></div>
                            {mehrereKostenstellen ? <KostenstellenSplitsEditor splits={splits} onChange={next => { splitsBearbeitet.current = true; setSplits(next); }} defaultStartJahr={form.belegDatum ? new Date(form.belegDatum).getFullYear() : new Date().getFullYear()} /> : <>
                                <div className="mt-3"><Select aria-label="Baustelle oder Bereich" value={kostenstelleId == null ? '' : String(kostenstelleId)} onChange={v => { splitsBearbeitet.current = true; setKostenstelleId(v ? Number(v) : null); }} options={[{ value: '', label: '– keine Zuordnung –' }, ...kostenstellen.map(k => ({ value: String(k.id), label: `${k.nummer ? `${k.nummer} ` : ''}${k.bezeichnung}` }))]} /></div>
                                <Button type="button" variant="outline" size="sm" className="mt-3 border-rose-300 text-rose-700" onClick={() => setMehrereKostenstellen(true)}>Auf mehrere aufteilen</Button>
                            </>}
                        </section>
                        <section>
                            <h3 className="text-base font-bold text-slate-900">Von wem war der Beleg?</h3>
                            <p className="mt-1 text-sm text-slate-600">Wählen Sie den passenden Lieferanten.</p>
                            <div className="mt-3 flex items-center gap-2"><input type="text" readOnly value={form.lieferantName} placeholder="Kein Lieferant" className={`${inputCls} bg-slate-50`} /><Button variant="outline" type="button" onClick={() => { setLieferantStartsuche(''); setLieferantPicker(true); }}><Truck className="mr-2 h-4 w-4" />Wählen</Button></div>
                            {detailBeleg.kiVorgeschlagenerLieferant && detailBeleg.kiVorgeschlagenerLieferant !== form.lieferantName && <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">Am Handy gewählt: {form.lieferantName || 'kein Lieferant'}. Die KI hat gelesen: {detailBeleg.kiVorgeschlagenerLieferant}. <Button type="button" size="sm" variant="outline" className="ml-2 border-amber-300 text-amber-900" onClick={async () => { const name = detailBeleg.kiVorgeschlagenerLieferant!; try { const response = await fetch(`/api/lieferanten?size=100&q=${encodeURIComponent(name)}`); const data = response.ok ? await response.json() : null; const treffer = data?.lieferanten?.find((l: LieferantSuchErgebnis) => l.lieferantenname === name); if (treffer) { update('lieferantId', treffer.id); update('lieferantName', treffer.lieferantenname); } else { setLieferantStartsuche(name); setLieferantPicker(true); } } catch { setLieferantStartsuche(name); setLieferantPicker(true); } }}>Übernehmen</Button></div>}
                        </section>

                        {/* ---------- Alles Seltene eingeklappt (Progressive Disclosure) ---------- */}

                        <div className="border-t border-slate-200 pt-3">
                            <button type="button"
                                onClick={() => setMehrDetails(v => !v)}
                                aria-expanded={mehrDetails}
                                className="inline-flex items-center gap-1.5 text-sm font-medium text-rose-700 hover:text-rose-800">
                                {mehrDetails
                                    ? <ChevronDown className="w-4 h-4" aria-hidden />
                                    : <ChevronRight className="w-4 h-4" aria-hidden />}
                                Mehr Details
                                {!mehrDetails && offeneDetails > 0 && (
                                    <span className="ml-1 text-xs font-normal text-slate-500">
                                        ({offeneDetails} ausgefüllt)
                                    </span>
                                )}
                            </button>

                            {mehrDetails && (
                                <div className="mt-3 space-y-4">
                                    <div className="grid grid-cols-2 gap-3">
                                        <Field label="Beleg-Nummer">
                                            <input type="text" value={form.belegNummer}
                                                onChange={e => update('belegNummer', e.target.value)}
                                                disabled={istFestgeschrieben}
                                                className={`${inputCls} ${gesperrtCls}`} />
                                        </Field>
                                        <Field label="Netto (€)">
                                            <DecimalInput aria-label="Netto (€)" value={form.betragNetto}
                                                onChange={value => update('betragNetto', value)}
                                                disabled={istFestgeschrieben}
                                                className={`${inputCls} ${gesperrtCls}`} />
                                        </Field>
                                        <Field label="MwSt-Satz (%)">
                                            <DecimalInput aria-label="MwSt-Satz (%)" value={form.mwstSatz}
                                                onChange={value => update('mwstSatz', value)}
                                                disabled={istFestgeschrieben}
                                                className={`${inputCls} ${gesperrtCls}`} />
                                        </Field>
                                    </div>


                                    <Field label="Notiz">
                                        <textarea rows={2} value={form.notiz}
                                            onChange={e => update('notiz', e.target.value)}
                                            className={inputCls} />
                                    </Field>
                                </div>
                            )}
                        </div>

                        {detailBeleg.aufteilungsModus === 'TEILWEISE' && (
                            <AufteilungsSektion beleg={detailBeleg} />
                        )}


                        {/* Live-Saldo-Vorschau + 409-Konflikt-Dialog */}
                        {saldoInfo && (
                            <div className="text-xs text-slate-600 bg-slate-50 border border-slate-200 rounded p-2 space-y-0.5">
                                <div>Kassenstand jetzt: <strong>{formatEuro(saldoInfo.saldo)} €</strong></div>
                                {projektion != null && (
                                    <div>
                                        Nach Validierung: <strong className={projektion < saldoInfo.mindestbestand ? 'text-red-700' : 'text-slate-700'}>
                                            {formatEuro(projektion)} €
                                        </strong>
                                        {saldoInfo.mindestbestand > 0 && (
                                            <span className="text-slate-500"> (Mindestbestand: {formatEuro(saldoInfo.mindestbestand)} €)</span>
                                        )}
                                    </div>
                                )}
                            </div>
                        )}
                        {konflikt && (
                            <div className="bg-amber-50 border border-amber-200 rounded-lg p-3 text-sm text-amber-900">
                                <div className="flex items-start gap-2">
                                    <AlertCircle className="w-4 h-4 mt-0.5 flex-shrink-0" />
                                    <div className="flex-1">
                                        <p className="font-medium">Kasse würde auf {formatEuro(konflikt.projizierterSaldo)} € rutschen.</p>
                                        <p className="text-xs mt-1">Mindestbestand: {formatEuro(konflikt.mindestbestand)} €</p>
                                        <Button size="sm" className="mt-2 bg-rose-600 text-white border border-rose-600 hover:bg-rose-700"
                                            onClick={loeseUnterdeckung} disabled={saving}>
                                            Privateinlage in Höhe {formatEuro(Math.max(0, konflikt.mindestbestand - konflikt.projizierterSaldo))} € vorab buchen?
                                        </Button>
                                    </div>
                                </div>
                            </div>
                        )}
                    </div>
                </div>

                <div className="border-t border-slate-200 p-4 flex flex-col gap-2 bg-slate-50">
                    {validationHint && (
                        <div className="text-sm px-3 py-2 rounded-lg border bg-amber-50 border-amber-200 text-amber-900">
                            {validationHint}
                        </div>
                    )}
                    <div className="flex items-center justify-end gap-3">
                        {/* Fest gebuchte Belege lassen sich nicht mehr verwerfen —
                            an ihre Stelle tritt die Gegenbuchung. Bereits
                            stornierte Belege bieten gar nichts mehr an. */}
                        {istFestgeschrieben ? (
                            wurdeStorniert || istGegenbuchung ? <span /> : (
                                <Button variant="ghost" onClick={() => setZeigeStorno(true)} disabled={saving}
                                    className="text-red-600 hover:bg-red-50">
                                    <Undo2 className="w-4 h-4 mr-2" /> Stornieren
                                </Button>
                            )
                        ) : (
                            <Button variant="ghost" onClick={() => setZeigeVerwerfen(true)} disabled={saving}
                                className="text-red-600 hover:bg-red-50">
                                <Trash2 className="w-4 h-4 mr-2" /> Verwerfen
                            </Button>
                        )}
                        <div className="flex items-center gap-2">
                            <Button variant="outline" onClick={() => save(false)} disabled={saving}>
                                {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Save className="w-4 h-4 mr-2" />}
                                {istFestgeschrieben ? 'Kontierung speichern' : 'Zwischenspeichern'}
                            </Button>
                            {!istFestgeschrieben && (
                                <Button onClick={() => save(true)} disabled={saving}>
                                    {saving ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <CheckCircle2 className="w-4 h-4 mr-2" />}
                                    Prüfen & Übernehmen
                                </Button>
                            )}
                        </div>
                    </div>
                </div>
            </div>

            {lieferantPicker && (
                <LieferantSearchModal
                    isOpen={lieferantPicker}
                    initialSearch={lieferantStartsuche}
                    onClose={() => { setLieferantPicker(false); setLieferantStartsuche(''); }}
                    currentLieferantId={form.lieferantId ?? undefined}
                    onSelect={(l: LieferantSuchErgebnis) => {
                        update('lieferantId', l.id);
                        update('lieferantName', l.lieferantenname);
                        setLieferantStartsuche('');
                    }}
                />
            )}

            {zeigeStorno && (
                <StornoDialog
                    belegId={beleg.id}
                    laufendeNummer={laufendeNummer}
                    beschreibung={detailBeleg.beschreibung ?? beleg.beschreibung}
                    betragBrutto={detailBeleg.betragBrutto ?? beleg.betragBrutto}
                    onClose={() => setZeigeStorno(false)}
                    onStorniert={() => {
                        setZeigeStorno(false);
                        // Der Dialog schliesst sich komplett: nach dem Storno ist
                        // die Gegenbuchung die interessante Zeile, nicht mehr das
                        // Original. Der Aufrufer laedt Liste und Kassenbuch neu
                        // und hat damit den echten neuen Stand -- deshalb hier
                        // bewusst kein zusammengebasteltes Beleg-Objekt.
                        onDeleted(beleg.id);
                    }}
                />
            )}

            {zeigeVerwerfen && (
                <VerwerfenDialog
                    belegId={beleg.id}
                    beschreibung={detailBeleg.beschreibung ?? beleg.beschreibung}
                    onClose={() => setZeigeVerwerfen(false)}
                    onVerworfen={() => {
                        setZeigeVerwerfen(false);
                        onDeleted(beleg.id);
                    }}
                />
            )}
        </Dialog>
    );
}

/**
 * Issue #58: Read-only Anzeige der Beleg-Positionen mit Hervorhebung der am
 * Handy markierten Firma-Positionen. Korrektur am PC ist NICHT vorgesehen —
 * die Mobile-Auswahl ist die Quelle der Wahrheit, der Buchhalter sieht hier
 * nur, was der Scanner gewaehlt hat (und kann es ggf. ueber die Mobile-PWA
 * korrigieren).
 */
function AufteilungsSektion({ beleg }: { beleg: Beleg }) {
    const positionen = beleg.positionen ?? [];
    if (positionen.length === 0) {
        return (
            <div className="bg-rose-50/60 border border-rose-100 rounded-lg p-3 text-sm text-slate-600">
                <strong className="text-rose-700">Teil-Beleg.</strong>{' '}
                Positionen werden geladen oder wurden vom Scanner noch nicht erfasst.
            </div>
        );
    }
    const firmaCount = positionen.filter(p => p.istFuerFirma).length;
    return (
        <div className="border border-rose-200 rounded-lg overflow-hidden">
            <div className="bg-rose-50 px-3 py-2 flex items-center gap-2">
                <span className="text-xs font-semibold uppercase tracking-wide text-rose-700">
                    Aufteilung – nur ein Teil ist betrieblich
                </span>
                <span className="text-xs text-slate-600">
                    {firmaCount} von {positionen.length} Positionen für die Firma
                </span>
            </div>
            <table className="w-full text-xs">
                <thead className="bg-slate-50 text-slate-600">
                    <tr>
                        <th className="text-center px-2 py-1.5 font-medium w-8">✓</th>
                        <th className="text-left px-2 py-1.5 font-medium">Beschreibung</th>
                        <th className="text-right px-2 py-1.5 font-medium">Menge</th>
                        <th className="text-right px-2 py-1.5 font-medium">Einzel</th>
                        <th className="text-right px-2 py-1.5 font-medium">Brutto</th>
                        <th className="text-right px-2 py-1.5 font-medium">MwSt</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                    {positionen.map(p => (
                        <tr key={p.id} className={p.istFuerFirma ? 'bg-rose-50/60' : ''}>
                            <td className="px-2 py-1.5 text-center">
                                {p.istFuerFirma
                                    ? <CheckCircle2 className="w-4 h-4 text-rose-600 inline" />
                                    : <span className="text-slate-300">–</span>}
                            </td>
                            <td className="px-2 py-1.5 text-slate-800">{p.beschreibung || `Pos ${p.sortierung}`}</td>
                            <td className="px-2 py-1.5 text-right tabular-nums text-slate-600">
                                {p.menge != null
                                    ? `${new Intl.NumberFormat('de-DE', { maximumFractionDigits: 2 }).format(p.menge)}${p.einheit ? ' ' + p.einheit : ''}`
                                    : '–'}
                            </td>
                            <td className="px-2 py-1.5 text-right tabular-nums text-slate-600">
                                {p.einzelpreis != null ? `${formatEuro(p.einzelpreis)} €` : '–'}
                            </td>
                            <td className="px-2 py-1.5 text-right tabular-nums font-medium">
                                {p.betragBrutto != null ? `${formatEuro(p.betragBrutto)} €` : '–'}
                            </td>
                            <td className="px-2 py-1.5 text-right tabular-nums text-slate-500">
                                {p.mwstSatz != null ? `${p.mwstSatz}%` : '–'}
                            </td>
                        </tr>
                    ))}
                </tbody>
                <tfoot className="bg-rose-50/40 border-t border-rose-200">
                    <tr>
                        <td colSpan={4} className="px-2 py-1.5 text-right text-xs font-semibold text-rose-700">
                            Summe für Firma
                        </td>
                        <td className="px-2 py-1.5 text-right tabular-nums font-bold text-rose-700">
                            {formatEuro(beleg.betragFirmaBrutto)} €
                        </td>
                        <td className="px-2 py-1.5 text-right tabular-nums text-rose-700">
                            {formatEuro(beleg.betragFirmaMwst)} €
                        </td>
                    </tr>
                    <tr>
                        <td colSpan={4} className="px-2 py-1.5 text-right text-xs text-slate-500">
                            Netto / MwSt davon
                        </td>
                        <td className="px-2 py-1.5 text-right tabular-nums text-slate-600">
                            {formatEuro(beleg.betragFirmaNetto)} € netto
                        </td>
                        <td className="px-2 py-1.5"></td>
                    </tr>
                </tfoot>
            </table>
            <div className="px-3 py-2 text-xs text-slate-500 bg-white border-t border-slate-100">
                Auswahl wurde am Handy getroffen. Zum Korrigieren in der Mobile-App neu auswählen.
            </div>
        </div>
    );
}

function KiBadge({ text = 'von KI gelesen' }: { text?: string }) {
    return <span className="shrink-0 rounded border border-indigo-200 bg-indigo-50 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-indigo-800">{text}</span>;
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
    return (
        <div>
            <label className="block text-xs font-semibold uppercase tracking-wide text-slate-500 mb-1">{label}</label>
            {children}
        </div>
    );
}

function BelegPreview({ belegId, mimeType, originalDateiname }: {
    belegId: number; mimeType?: string | null; originalDateiname?: string | null;
}) {
    const src = `/api/buchhaltung/belege/${belegId}/datei`;
    const istPdf = mimeType?.includes('pdf') || originalDateiname?.toLowerCase().endsWith('.pdf');
    const istBild = mimeType?.startsWith('image/');

    if (istPdf) {
        return <iframe src={src} title={originalDateiname ?? 'Beleg-PDF'} className="w-full h-full min-h-[400px] bg-white rounded border border-slate-200" />;
    }
    if (istBild) {
        return <img src={src} alt={originalDateiname ?? 'Beleg'} className="max-w-full max-h-[600px] rounded shadow" />;
    }
    return (
        <div className="text-center text-slate-500 p-8">
            <FileText className="w-12 h-12 mx-auto mb-2 opacity-30" />
            <p className="text-sm">{originalDateiname}</p>
            <a href={src} target="_blank" rel="noopener noreferrer" className="text-rose-600 text-sm underline mt-2 inline-block">
                Datei öffnen
            </a>
        </div>
    );
}
