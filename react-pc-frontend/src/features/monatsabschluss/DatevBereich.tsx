import { useLayoutEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, Download, Settings } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../../components/ui/dialog';
import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import { useToast } from '../../components/ui/toast';
import { api } from './api';
import { kategorieName, normalisiereKonfiguration, STUNDENART_TEXT, zugangEingetragen } from './datevRegeln';
import type { Hinweis, Konfiguration, AuswahlStand, Vorpruefung } from './types';

/** Hier wird DATEV eingerichtet – nur für Admins erreichbar. */
const DATEV_EINSTELLUNGEN_PFAD = '/einstellungen#zeiterfassung';
const NUR_ADMIN_HINWEIS = 'DATEV richtet ein Admin unter Einstellungen → Zeiterfassung ein.';
const linkKlasse = 'inline-flex shrink-0 items-center justify-center gap-2 rounded border border-rose-300 bg-white px-3 py-1.5 text-sm text-rose-700 transition-colors hover:bg-rose-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-1';
const text = (error: unknown) => error instanceof Error ? error.message : 'Die Anfrage ist fehlgeschlagen. Bitte erneut versuchen.';
/** Erklärt kurz, was „Für DATEV exportieren“ tut – und was nicht. */
function DatevExportInfo() {
    return <InfoButton titel="Für DATEV exportieren">
        <InfoAbschnitt titel="Was passiert im Hintergrund?">
            <ol className="list-decimal space-y-0.5 pl-4">
                <li>Vorprüfung: Das Programm prüft die ausgewählten, abgeschlossenen Monate und die DATEV-Einstellungen.</li>
                <li>Datei: Danach laden Sie eine LODAS-Textdatei herunter.</li>
                <li>Steuerbüro: Sie geben die Datei Ihrem Steuerbüro, das sie in DATEV importiert.</li>
            </ol>
        </InfoAbschnitt>
        <InfoAbschnitt titel="Gut zu wissen">
            <p>Es wird nichts automatisch verschickt. Der Download heißt nicht, dass das Steuerbüro die Datei schon übernommen hat.</p>
        </InfoAbschnitt>
    </InfoButton>;
}
const hinweisName = (kategorie: string) => kategorieName(kategorie) ?? (kategorie === 'KORREKTUR' ? 'Korrektur' : kategorie === 'ABWESENHEIT_UNGEGLIEDERT' ? 'Alte Abwesenheit' : kategorie === 'STAND' ? 'Monatsstand' : 'Einstellungen');

/**
 * „DATEV für das Steuerbüro“ im Monatsabschluss: Vorprüfung und Download
 * der LODAS-Datei für die bewusst ausgewählten Monatsstände.
 *
 * <p>Eingerichtet wird DATEV nicht mehr hier, sondern unter Einstellungen →
 * Zeiterfassung (nur Admins). Die Konfiguration wird beim Öffnen des
 * Export-Dialogs jedes Mal frisch gelesen – sie liefert die Version, gegen die
 * der Server Vorprüfung und Export abgleicht.</p>
 *
 * @param darfEinrichten Admin? Dann führt „DATEV einrichten“ zu den Einstellungen, sonst steht dort ein Hinweis.
 */
export function DatevBereich({ auswahl, mitarbeiter, darfEinrichten }: { auswahl: AuswahlStand[]; mitarbeiter: { id: number; name: string }[]; darfEinrichten: boolean }) {
    const toast = useToast();
    const [offen, setOffen] = useState(false);
    const [config, setConfig] = useState<Konfiguration | null>(null);
    const [busy, setBusy] = useState('');
    const [fehler, setFehler] = useState('');
    const [pruefung, setPruefung] = useState<{ key: string; daten: Vorpruefung } | null>(null);
    const [bestaetigt, setBestaetigt] = useState(false);
    const lauf = useRef(0); const sperre = useRef(false); const mounted = useRef(true);
    const key = JSON.stringify({ auswahl, config });
    const exportAuswahl = auswahl.map(({ mitarbeiterId, jahr, monat, version }) => ({ mitarbeiterId, jahr, monat, version }));
    const aktuell = useRef(key);
    useLayoutEffect(() => { aktuell.current = key; lauf.current++; setPruefung(null); setBestaetigt(false); }, [key]);
    useLayoutEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
    const grund = !auswahl.length ? 'Bitte zuerst Mitarbeiter für den Export auswählen.' : auswahl.length > 500 ? 'Bitte höchstens 500 Mitarbeiter auswählen.' : new Set(auswahl.map(s => `${s.jahr}/${s.monat}`)).size !== 1 ? 'Bitte genau einen Monat auswählen.' : auswahl.some(s => s.festgeschrieben !== true || s.version === null) ? 'Bitte die ausgewählten Monate zuerst abschließen und aktualisieren.' : '';
    const zugangFehlt = config !== null && !zugangEingetragen(config);
    const gueltig = pruefung?.key === key ? pruefung.daten : null;
    const name = (id: number) => mitarbeiter.find(m => m.id === id)?.name ?? `Mitarbeiter ${id}`;
    function invalidieren() { setPruefung(null); setBestaetigt(false); lauf.current++; }
    function melden(error: unknown) { setFehler(text(error)); toast.error(text(error)); }
    async function laden() {
        if (sperre.current) return;
        sperre.current = true; setBusy('Einstellungen werden geladen …'); setFehler(''); invalidieren();
        try { const c = normalisiereKonfiguration(await api.ladeDatevKonfiguration()); if (mounted.current) setConfig(c); }
        catch (error) { if (mounted.current) melden(error); }
        finally { sperre.current = false; if (mounted.current) setBusy(''); }
    }
    async function vorpruefen() {
        if (!config || grund || zugangFehlt || sperre.current) return;
        invalidieren(); const nummer = lauf.current; const startKey = key;
        sperre.current = true; setBusy('Vorprüfung läuft …'); setFehler('');
        try {
            const daten = await api.pruefeDatev({ auswahl: exportAuswahl, konfigurationVersion: config.version });
            if (mounted.current && lauf.current === nummer && aktuell.current === startKey) {
                if (JSON.stringify(daten.auswahl) !== JSON.stringify(exportAuswahl) || daten.konfigurationVersion !== config.version) throw new Error('Der geprüfte Stand passt nicht mehr zur Auswahl. Bitte erneut prüfen.');
                setPruefung({ key: startKey, daten });
            }
        } catch (error) { if (mounted.current && lauf.current === nummer && aktuell.current === startKey) melden(error); }
        finally { sperre.current = false; if (mounted.current) setBusy(''); }
    }
    async function herunterladen() {
        if (!config || !gueltig?.gueltig || gueltig.fehler.length || (gueltig.ausschluesse.length > 0 && !bestaetigt) || grund || sperre.current) return;
        const nummer = lauf.current; const startKey = key; sperre.current = true; setBusy('Datei wird erstellt …'); setFehler('');
        try {
            const datei = await api.exportiereDatev({ auswahl: exportAuswahl, konfigurationVersion: config.version });
            if (!mounted.current || nummer !== lauf.current || aktuell.current !== startKey) return;
            const url = URL.createObjectURL(datei.blob); const link = document.createElement('a');
            try { link.href = url; link.download = datei.dateiname; document.body.append(link); link.click(); }
            finally { link.remove(); URL.revokeObjectURL(url); }
            toast.success('Datei heruntergeladen – bitte im Steuerbüro importieren.'); invalidieren();
        } catch (error) { if (mounted.current && nummer === lauf.current && aktuell.current === startKey) { invalidieren(); melden(error); } }
        finally { sperre.current = false; if (mounted.current) setBusy(''); }
    }
    function hinweise(liste: Hinweis[]) { return <ul className="space-y-2">{liste.map((h, i) => <li key={`${h.kategorie}/${h.referenz?.mitarbeiterId ?? ''}/${h.referenz?.jahr ?? ''}/${h.referenz?.monat ?? ''}/${i}`} className="rounded-md bg-slate-50 p-3 text-sm"><span className="font-medium">{h.referenz?.mitarbeiterId ? `${name(h.referenz.mitarbeiterId)} · ` : ''}{h.referenz ? `${String(h.referenz.monat).padStart(2, '0')}/${h.referenz.jahr} · ` : ''}{hinweisName(h.kategorie)}</span>{h.stunden !== null && <span className="ml-2 tabular-nums">{h.stunden.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} h</span>}<p className="mt-1">{h.meldung}</p></li>)}</ul>; }
    const abweichend = !!config?.personalnummern.some(p => p.stundenart && p.stundenart !== config.stundenart);
    return <section aria-label="DATEV-Export" className="rounded-lg border border-slate-200 bg-white p-4 space-y-4">
        <div className="flex items-center justify-between gap-4">
            <div><div className="flex items-center gap-1.5"><h2 className="font-semibold">DATEV für das Steuerbüro</h2><DatevExportInfo /></div><p className="text-sm text-slate-500">Stunden als LODAS-Datei übergeben. Keine Lohnberechnung.</p></div>
            <div className="flex shrink-0 items-center gap-2">
                {darfEinrichten && <Link to={DATEV_EINSTELLUNGEN_PFAD} className={linkKlasse}><Settings aria-hidden="true" className="h-4 w-4" />DATEV einrichten</Link>}
                <Button variant="outline" size="sm" disabled={!!grund || !!busy} title={grund || undefined} onClick={() => { setOffen(true); setFehler(''); invalidieren(); void laden(); }}><Download aria-hidden="true" className="mr-2 h-4 w-4" />Für DATEV exportieren</Button>
            </div>
        </div>
        {!darfEinrichten && <p className="text-sm text-slate-500">{NUR_ADMIN_HINWEIS}</p>}
        {grund && <p className="text-sm text-slate-500">{grund}</p>}
        <Dialog aria-label="DATEV-Export prüfen" open={offen} onOpenChange={v => { setOffen(v); if (!v) { invalidieren(); setFehler(''); } }}><DialogContent className="max-w-2xl"><DialogHeader><DialogTitle>DATEV-Export prüfen</DialogTitle></DialogHeader><div className="overflow-y-auto space-y-4 pr-1">
            <p className="text-sm text-slate-600">Nur diese bewusst ausgewählten Monatsstände werden übergeben:</p>
            <ul className="max-h-40 overflow-auto rounded-md bg-slate-50 p-3 text-sm space-y-1">{auswahl.map(s => <li key={`${s.mitarbeiterId}/${s.jahr}/${s.monat}`}><span className="font-medium">{name(s.mitarbeiterId)}</span> · {String(s.monat).padStart(2, '0')}/{s.jahr} · {s.festgeschrieben ? `Abgeschlossen · Stand ${s.version}` : 'Noch offen'}</li>)}</ul>
            {config && <p className="text-sm text-slate-600">Als „Arbeit“ gehen die {STUNDENART_TEXT[config.stundenart].lang} ans Steuerbüro{abweichend ? ' – bei einzelnen Mitarbeitern abweichend' : ''}.</p>}
            {zugangFehlt && <div role="note" className="flex items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                <AlertTriangle aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
                <div className="space-y-2"><p>Beraternummer und Mandantennummer fehlen noch. Ohne sie nimmt DATEV keine Datei an.</p>
                    {darfEinrichten ? <Link to={DATEV_EINSTELLUNGEN_PFAD} className="font-medium text-rose-700 underline underline-offset-2 hover:text-rose-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 rounded">Jetzt in den Einstellungen eintragen</Link> : <p>{NUR_ADMIN_HINWEIS}</p>}</div>
            </div>}
            {grund && <p role="alert">{grund}</p>}{busy && <p role="status">{busy}</p>}{fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}{!config && !busy && <Button variant="outline" onClick={laden}>Einstellungen neu laden</Button>}
            {gueltig && <>{gueltig.gueltig && !gueltig.fehler.length ? <p className="font-medium text-slate-800">Die Vorprüfung ist erfolgreich.</p> : <h4 className="font-medium text-rose-700">Bitte vor dem Export korrigieren</h4>}{hinweise(gueltig.fehler)}{gueltig.ausschluesse.length > 0 && <><h4 className="font-medium">Diese Stunden werden nicht exportiert</h4>{hinweise(gueltig.ausschluesse)}<label className="flex items-start gap-2 text-sm"><input type="checkbox" className="mt-1 h-4 w-4 shrink-0 accent-rose-600" checked={bestaetigt} disabled={!!busy} onChange={e => setBestaetigt(e.target.checked)} />Ich habe die ausgeschlossenen Stunden geprüft und möchte ohne diese Stunden exportieren.</label></>}</>}
        </div><DialogFooter>
            <span title={zugangFehlt ? 'Bitte zuerst Beraternummer und Mandantennummer eintragen.' : undefined}><Button variant="outline" disabled={!!busy || !config || !!grund || zugangFehlt} onClick={vorpruefen}>Vorprüfung starten</Button></span>
            <Button disabled={!!busy || !!grund || !gueltig?.gueltig || !!gueltig.fehler.length || (!!gueltig.ausschluesse.length && !bestaetigt)} onClick={herunterladen}>Datei herunterladen</Button>
        </DialogFooter></DialogContent></Dialog>
    </section>;
}
