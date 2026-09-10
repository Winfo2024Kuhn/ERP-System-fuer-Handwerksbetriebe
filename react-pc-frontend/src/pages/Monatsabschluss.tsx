import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Calendar, ChevronLeft, ChevronRight, FileCheck, History, RefreshCw } from 'lucide-react';
import { Button } from '../components/ui/button';
import { Select } from '../components/ui/select-custom';
import { useToast } from '../components/ui/toast';
import { useConfirm } from '../components/ui/confirm-dialog';
import { DatevBereich } from '../features/monatsabschluss/DatevBereich';
import { api } from '../features/monatsabschluss/api';
import type { Filter, AuswahlStand, Referenz, Uebersicht, Vergleichsmonat, Einzelergebnis, Verlauf, Kennzahlen, Zeile } from '../features/monatsabschluss/types';
const monate = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
const felder: [keyof Kennzahlen, string][] = [['istStunden', 'Arbeit'], ['abwesenheitsStunden', 'Abwesenheit'], ['feiertagsStunden', 'Feiertage'], ['korrekturStunden', 'Korrektur'], ['gesamtIst', 'Gesamt'], ['sollStunden', 'Soll'], ['differenz', 'Differenz']];
const zahl = (value: number) => value.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const key = (r: Referenz) => `${r.mitarbeiterId}/${r.jahr}/${r.monat}`;
const datum = (value: string) => new Date(value).toLocaleString('de-DE');
const meldung = (err: unknown) => err instanceof Error ? err.message : 'Monatsdaten konnten nicht geladen werden.';
export default function Monatsabschluss() {
    const heute = new Date();
    const [filter, setFilter] = useState<Filter>(() => { const last = new Date(heute.getFullYear(), heute.getMonth() - 1, 1); return { jahr: last.getFullYear(), monat: last.getMonth() + 1, status: 'ALLE', page: 0, size: 50 }; });
    const [recht, setRecht] = useState<boolean | null>(null);
    const [fehler, setFehler] = useState('');
    const [mitarbeiter, setMitarbeiter] = useState<{ id: number; name: string }[]>([]);
    const [abteilungen, setAbteilungen] = useState<{ id: number; name: string }[]>([]);
    const [daten, setDaten] = useState<Uebersicht | null>(null);
    const [vergleich, setVergleich] = useState<Vergleichsmonat[]>([]);
    const [auswahl, setAuswahl] = useState<AuswahlStand[]>([]);
    const [ergebnisse, setErgebnisse] = useState<Einzelergebnis[]>([]);
    const [laedt, setLaedt] = useState(false);
    const [busy, setBusy] = useState(false);
    const busyRef = useRef(false);
    const generation = useRef(0);
    const [revision, setRevision] = useState(0);
    const [verlaufZeile, setVerlaufZeile] = useState<Zeile | null>(null);
    const [verlauf, setVerlauf] = useState<Verlauf | null>(null);
    const [verlaufFehler, setVerlaufFehler] = useState('');
    const toast = useToast(); const toastRef = useRef(toast); toastRef.current = toast;
    const confirm = useConfirm();
    const vergangen = filter.jahr * 12 + filter.monat < heute.getFullYear() * 12 + heute.getMonth() + 1;
    useEffect(() => {
        const controller = new AbortController();
        api.ladeBerechtigung(controller.signal).then(data => { if (!controller.signal.aborted) setRecht(data.darfMonatAbschliessen); })
            .catch(err => { if (!controller.signal.aborted) { setFehler(meldung(err)); setRecht(false); toastRef.current.error(meldung(err)); } });
        return () => { controller.abort();
            // Invalidate pending confirmation callbacks when this page unmounts.
            // eslint-disable-next-line react-hooks/exhaustive-deps
            generation.current++;
        };
    }, []);
    useEffect(() => {
        if (!recht) return;
        const controller = new AbortController();
        Promise.all([api.ladeMitarbeiter(controller.signal), api.ladeAbteilungen(controller.signal)]).then(([menschen, teams]) => {
            if (!controller.signal.aborted) { setMitarbeiter(menschen.map(m => ({ id: m.id, name: `${m.vorname} ${m.nachname}` }))); setAbteilungen(teams); }
        }).catch(err => { if (!controller.signal.aborted) { setFehler(meldung(err)); toastRef.current.error(meldung(err)); } });
        return () => controller.abort();
    }, [recht]);
    useEffect(() => {
        if (!recht) return;
        const controller = new AbortController(); setLaedt(true); setFehler('');
        Promise.all([api.ladeUebersicht(filter, controller.signal), api.ladeVergleich(filter, controller.signal)]).then(([uebersicht, monate]) => {
            if (controller.signal.aborted) return;
            if (uebersicht.totalElements > 500 || uebersicht.auswahl.length > 500) throw new Error('Bitte die Auswahl auf höchstens 500 Mitarbeiter eingrenzen.');
            setDaten(uebersicht); setVergleich(monate);
            setAuswahl(current => current.map(stand => uebersicht.auswahl.find(s => key(s) === key(stand)) ?? stand));
        }).catch(err => { if (!controller.signal.aborted) { setDaten(null); setVergleich([]); setFehler(meldung(err)); toastRef.current.error(meldung(err)); } })
            .finally(() => { if (!controller.signal.aborted) setLaedt(false); });
        return () => controller.abort();
    }, [recht, filter, revision]);
    useEffect(() => {
        setVerlauf(null); setVerlaufFehler('');
        if (!verlaufZeile) return;
        const controller = new AbortController();
        api.ladeVerlauf(verlaufZeile.referenz, controller.signal).then(data => { if (!controller.signal.aborted) setVerlauf(data); })
            .catch(err => { if (!controller.signal.aborted) { setVerlaufFehler(meldung(err)); toastRef.current.error(meldung(err)); } });
        return () => controller.abort();
    }, [verlaufZeile]);
    function aendereFilter(next: Partial<Filter>) { generation.current++; setAuswahl([]); setErgebnisse([]); setVerlaufZeile(null); setDaten(null); setVergleich([]); setFilter(prev => ({ ...prev, ...next, page: 0 })); }
    function waehle(stand: AuswahlStand) { generation.current++; setAuswahl(prev => prev.some(s => key(s) === key(stand)) ? prev.filter(s => key(s) !== key(stand)) : [...prev, stand]); }
    async function abschliessen() {
        if (busyRef.current || !recht || !vergangen || laedt || !daten || !auswahl.length) return;
        busyRef.current = true; setBusy(true); const lauf = generation.current; const staende = [...auswahl];
        try {
            const ok = await confirm({ title: 'Auswahl abschließen?', message: `${monate[filter.monat - 1]} ${filter.jahr}: ${staende.length} Mitarbeiter abschließen? Bitte vorher alle Zeiten prüfen. Der geprüfte Stand bleibt festgehalten.`, confirmLabel: 'Abschließen', variant: 'warning' });
            if (!ok || lauf !== generation.current) return;
            const response = await api.sammelabschluss(staende.map(({ mitarbeiterId, jahr, monat }) => ({ mitarbeiterId, jahr, monat })));
            window.dispatchEvent(new Event('notifications:refresh'));
            const fehlgeschlagen = response.ergebnisse.filter(e => e.status === 'FEHLGESCHLAGEN');
            if (lauf === generation.current) { setErgebnisse(response.ergebnisse); setAuswahl(staende.filter(s => fehlgeschlagen.some(e => key(e.referenz) === key(s)))); setVerlaufZeile(null); setFilter(f => ({ ...f, page: 0 })); setRevision(v => v + 1); }
            if (fehlgeschlagen.length) toastRef.current.error(`${fehlgeschlagen.length} Abschlüsse fehlgeschlagen. Bitte die Ergebnisse prüfen.`);
            else toastRef.current.success('Die ausgewählten Monate sind abgeschlossen.');
        } catch (err) { toastRef.current.error(meldung(err)); if (lauf === generation.current) { setFehler(meldung(err)); setRevision(v => v + 1); } }
        finally { busyRef.current = false; setBusy(false); }
    }
    const all = !!daten?.auswahl.length && daten.auswahl.every(s => auswahl.some(a => key(a) === key(s)));
    const grund = !vergangen ? 'Nur vergangene Monate können abgeschlossen werden.' : laedt ? 'Monatsdaten werden geladen.' : !auswahl.length ? 'Bitte zuerst Mitarbeiter auswählen.' : busy ? 'Die Bestätigung oder der Abschluss läuft.' : '';
    return <div className="p-6 space-y-6">
        <header className="flex flex-col md:flex-row justify-between gap-4 md:items-end mb-8"><div><p className="text-sm font-semibold text-rose-600 uppercase tracking-wide">Zeiterfassung</p><h1 className="text-3xl font-bold text-slate-900">MONATSABSCHLUSS</h1><p className="text-slate-500 mt-1">Zeiten prüfen und den Monatsstand für Ihre Mitarbeiter festhalten.</p></div>
            {recht && <div title={grund}><Button size="sm" className="bg-rose-600 hover:bg-rose-700 text-white" disabled={!!grund || !daten} onClick={abschliessen}><FileCheck aria-hidden="true" className="w-4 h-4 mr-2" />{busy ? 'Abschluss läuft …' : 'Auswahl abschließen'}</Button></div>}</header>
        {recht === null ? <p role="status" className="motion-safe:animate-pulse rounded-lg bg-slate-100 p-6">Abschlussrecht wird geprüft …</p> : !recht ? <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-800">{fehler || 'Keine Berechtigung für den Monatsabschluss. Bitte die Administration um das Abschlussrecht bitten.'}</p> : <>
        <section aria-label="Monatsfilter" className="grid grid-cols-5 gap-4 bg-white border border-slate-200 rounded-lg shadow-sm p-4">
            <label className="text-sm font-medium space-y-2">Monat<Select aria-label="Monat" value={String(filter.monat)} options={monate.map((m, i) => ({ value: String(i + 1), label: m }))} onChange={v => aendereFilter({ monat: Number(v) })} /></label>
            <label className="text-sm font-medium space-y-2">Jahr<Select aria-label="Jahr" value={String(filter.jahr)} options={Array.from({ length: 27 }, (_, i) => ({ value: String(heute.getFullYear() + 1 - i), label: String(heute.getFullYear() + 1 - i) }))} onChange={v => aendereFilter({ jahr: Number(v) })} /></label>
            <label className="text-sm font-medium space-y-2">Mitarbeiter<Select aria-label="Mitarbeiter" value={String(filter.mitarbeiterId ?? '')} options={[{ value: '', label: 'Alle Mitarbeiter' }, ...mitarbeiter.map(m => ({ value: String(m.id), label: m.name }))]} onChange={v => aendereFilter({ mitarbeiterId: v ? Number(v) : undefined })} /></label>
            <label className="text-sm font-medium space-y-2">Abteilung<Select aria-label="Abteilung" value={String(filter.abteilungId ?? '')} options={[{ value: '', label: 'Alle Abteilungen' }, ...abteilungen.map(m => ({ value: String(m.id), label: m.name }))]} onChange={v => aendereFilter({ abteilungId: v ? Number(v) : undefined })} /></label>
            <label className="text-sm font-medium space-y-2">Status<Select aria-label="Status" value={filter.status} options={[{ value: 'ALLE', label: 'Alle Stände' }, { value: 'OFFEN', label: 'Noch offen' }, { value: 'ABGESCHLOSSEN', label: 'Abgeschlossen' }]} onChange={v => aendereFilter({ status: v as Filter['status'] })} /></label>
        </section>
        {!vergangen && <p className="text-sm text-slate-600">Nur vergangene Monate können abgeschlossen werden.</p>}
        {fehler && <p role="alert" className="rounded-lg bg-rose-50 text-rose-800 p-4">{fehler}</p>}
        {verlaufZeile && <section aria-label="Abschlussverlauf" className="rounded-lg border border-slate-200 bg-white p-4"><div className="flex items-center justify-between"><h2 className="font-semibold">Verlauf für {verlaufZeile.mitarbeiterName}</h2><Button variant="outline" size="sm" onClick={() => setVerlaufZeile(null)}>Verlauf schließen</Button></div>{verlaufFehler ? <p role="alert">{verlaufFehler}</p> : !verlauf ? <p role="status">Verlauf wird geladen …</p> : verlauf.audit.length ? <ul className="mt-3 space-y-2">{verlauf.audit.map(a => <li key={a.id}>{a.aktion === 'ABSCHLIESSEN' ? 'Abgeschlossen' : 'Wieder geöffnet'} durch {a.akteurName} · {datum(a.zeitpunkt)}</li>)}</ul> : <p className="mt-3 text-slate-500">Noch kein Abschluss vorhanden.</p>}</section>}
        <div className="flex items-center justify-between"><p className="font-medium text-slate-700">{auswahl.length} ausgewählt</p><Button variant="outline" size="sm" disabled={laedt || busy} onClick={() => { generation.current++; setAuswahl([]); setRevision(v => v + 1); }}><RefreshCw aria-hidden="true" className="w-4 h-4 mr-2" />Aktualisieren</Button></div>
        <DatevBereich auswahl={laedt || busy || !daten ? [] : auswahl} mitarbeiter={mitarbeiter} />
        {laedt ? <p role="status" className="bg-slate-100 rounded-lg p-8 motion-safe:animate-pulse">Monatsdaten werden geladen …</p> : daten && <section className="bg-white border border-slate-200 rounded-lg shadow-sm p-4 space-y-4" aria-label="Monatsübersicht">
            <h2 className="font-semibold text-lg">{monate[filter.monat - 1]} {filter.jahr}</h2>
            <p className="text-sm text-slate-600">Alle Angaben in Stunden. Gesamt = Arbeit + Abwesenheit + Feiertage + Korrektur.</p>
            <table className="w-full table-fixed text-sm"><thead><tr className="border-b text-left text-slate-600"><th className="w-9 py-3"><input type="checkbox" className="accent-rose-600 h-4 w-4" aria-label="Alle gefilterten Mitarbeiter auswählen" checked={all} disabled={!daten.auswahl.length || busy} onChange={() => { generation.current++; setAuswahl(all ? [] : daten.auswahl); }} /></th><th className="w-[17%]">Mitarbeiter</th>{felder.map(([id, name]) => <th key={id} className="text-right px-1 break-words">{name}</th>)}<th className="w-[15%] pl-3">Stand</th><th className="w-20">Details</th></tr></thead>
            <tbody>{daten.items.map(zeile => <tr key={key(zeile.referenz)} className="border-b border-slate-100 hover:bg-slate-50"><td className="py-3"><input type="checkbox" className="accent-rose-600 h-4 w-4" aria-label={`${zeile.mitarbeiterName} auswählen`} disabled={busy} checked={auswahl.some(s => key(s) === key(zeile.referenz))} onChange={() => waehle({ ...zeile.referenz, version: zeile.version, festgeschrieben: zeile.festgeschrieben })} /></td><th scope="row" className="text-left font-medium break-words pr-2">{zeile.mitarbeiterName}</th>{felder.map(([id]) => <td key={id} className="text-right tabular-nums px-1">{zahl(zeile.kennzahlen[id])}</td>)}<td className="pl-3"><span className={`inline-block rounded px-2 py-1 text-xs ${zeile.festgeschrieben ? 'bg-slate-100 text-slate-800' : 'bg-amber-50 text-amber-800'}`}>{zeile.festgeschrieben ? 'Abgeschlossen' : 'Noch offen'}</span>{zeile.festgeschriebenAm && <p className="text-xs text-slate-500 mt-1">{datum(zeile.festgeschriebenAm)}</p>}</td><td><div className="flex gap-1"><Button variant="ghost" size="sm" className="px-2" title="Verlauf anzeigen" aria-label={`Verlauf für ${zeile.mitarbeiterName}`} onClick={() => setVerlaufZeile(zeile)}><History className="w-4 h-4" /></Button><Link className="p-2 rounded hover:bg-rose-50 text-rose-700 focus:ring-2 focus:ring-rose-500" title="Im Kalender prüfen" aria-label={`Kalender für ${zeile.mitarbeiterName}`} to={`/zeitbuchungen?${new URLSearchParams({ mitarbeiterId: String(zeile.referenz.mitarbeiterId), jahr: String(zeile.referenz.jahr), monat: String(zeile.referenz.monat) })}`}><Calendar className="w-4 h-4" /></Link></div></td></tr>)}</tbody>
            <tfoot><tr className="font-semibold bg-slate-50"><th colSpan={2} className="text-left py-3 pr-2">Alle gefilterten Mitarbeiter</th>{felder.map(([id]) => <td key={id} className="text-right tabular-nums px-1">{zahl(daten.summen[id])}</td>)}<td colSpan={2} /></tr></tfoot></table>
            {!daten.items.length && <p className="p-4 text-slate-500">Keine Mitarbeiter für diese Filter gefunden.</p>}
            <div className="flex justify-between items-center text-sm"><p>{daten.totalElements} Mitarbeiter · Seite {filter.page + 1} von {Math.max(1, Math.ceil(daten.totalElements / filter.size))}</p><div className="flex gap-2"><Button variant="outline" size="sm" aria-label="Vorherige Seite" disabled={filter.page === 0 || busy} onClick={() => setFilter(f => ({ ...f, page: f.page - 1 }))}><ChevronLeft className="w-4 h-4" />Zurück</Button><Button variant="outline" size="sm" aria-label="Nächste Seite" disabled={(filter.page + 1) * filter.size >= daten.totalElements || busy} onClick={() => setFilter(f => ({ ...f, page: f.page + 1 }))}>Weiter<ChevronRight className="w-4 h-4" /></Button></div></div>
        </section>}
        {ergebnisse.length > 0 && <section aria-label="Abschlussergebnisse" className="rounded-lg border border-slate-200 bg-white p-4"><h2 className="font-semibold text-lg mb-2">Ergebnis des Abschlusses</h2><ul className="max-h-64 overflow-auto space-y-2">{ergebnisse.map(e => <li key={key(e.referenz)} className={e.status === 'FEHLGESCHLAGEN' ? 'text-rose-700' : 'text-slate-700'}>{mitarbeiter.find(m => m.id === e.referenz.mitarbeiterId)?.name ?? `Mitarbeiter ${e.referenz.mitarbeiterId}`} · {monate[e.referenz.monat - 1]} {e.referenz.jahr}: {e.meldung}</li>)}</ul></section>}

        {vergleich.length > 0 && !laedt && <section className="rounded-lg border border-slate-200 bg-white p-4"><h2 className="text-lg font-semibold">Die letzten sechs Monate</h2><p className="text-sm text-slate-500 mt-1 mb-4">Für die gewählten Mitarbeiter und Abteilungen, unabhängig vom Statusfilter. Offene Monate sind vorläufig.</p><table className="w-full text-sm"><thead><tr className="border-b text-left text-slate-600"><th className="py-2">Monat</th><th>Stand</th>{felder.map(([id, name]) => <th key={id} className="text-right">{name}</th>)}</tr></thead><tbody>{vergleich.map(m => <tr key={`${m.jahr}-${m.monat}`} className="border-b border-slate-100"><th className="text-left font-medium py-3">{monate[m.monat - 1]} {m.jahr}</th><td>{m.offen ? `${m.offen} offen · vorläufig` : 'Abgeschlossen'} · {m.abgeschlossen} abgeschlossen</td>{felder.map(([id]) => <td key={id} className="text-right tabular-nums">{zahl(m.summen[id])}</td>)}</tr>)}</tbody></table></section>}
        </>}
    </div>;
}
