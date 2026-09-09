import { useId, useLayoutEffect, useRef, useState } from 'react';
import { Download, Settings } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Input } from '../../components/ui/input';
import { Select } from '../../components/ui/select-custom';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { api } from './api';
import type { Hinweis, Konfiguration, Stand, Vorpruefung } from './types';
const kategorien = [['ARBEIT', 'Arbeit'], ['FEIERTAG', 'Feiertage'], ['URLAUB', 'Urlaub'], ['KRANKHEIT', 'Krankheit'], ['FORTBILDUNG', 'Fortbildung'], ['ZEITAUSGLEICH', 'Zeitausgleich'], ['KRANKENGELD', 'Krankengeld'], ['WIEDEREINGLIEDERUNG', 'Wiedereingliederung']] as const;
const text = (error: unknown) => error instanceof Error ? error.message : 'Die Anfrage ist fehlgeschlagen. Bitte erneut versuchen.';
const normalisieren = (c: Konfiguration): Konfiguration => ({
    ...c, beraterNr: c.beraterNr ?? '', mandantenNr: c.mandantenNr ?? '',
    zuordnungen: kategorien.map(([kategorie]) => {
        const z = c.zuordnungen.find(row => row.kategorie === kategorie);
        return { kategorie, ausgeschlossen: z?.ausgeschlossen ?? false, lohnart: z?.lohnart ?? '' };
    }),
});
function nummerFehler(value: string, min: number, max: number, label: string) {
    return value && (!new RegExp(`^\\d{${min},${max}}$`).test(value) || !/[1-9]/.test(value)) ? `${label}: Bitte ${min === max ? min : `${min} bis ${max}`} Ziffern und eine Nummer größer als 0 eingeben.` : '';
}
function Nummer({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
    const id = useId();
    return <div className="space-y-1"><label htmlFor={id} className="text-sm font-medium text-slate-700">{label}</label><Input id={id} type="text" inputMode="numeric" autoComplete="off" value={value} onChange={e => onChange(e.target.value)} /></div>;
}
export function DatevBereich({ auswahl, mitarbeiter }: { auswahl: Stand[]; mitarbeiter: { id: number; name: string }[] }) {
    const toast = useToast();
    const [einstellungen, setEinstellungen] = useState(false);
    const [offen, setOffen] = useState(false);
    const [config, setConfig] = useState<Konfiguration | null>(null);
    const [gespeichert, setGespeichert] = useState('');
    const [busy, setBusy] = useState('');
    const [fehler, setFehler] = useState('');
    const [pruefung, setPruefung] = useState<{ key: string; daten: Vorpruefung } | null>(null);
    const [bestaetigt, setBestaetigt] = useState(false);
    const lauf = useRef(0); const sperre = useRef(false); const mounted = useRef(true);
    const configKey = JSON.stringify(config);
    const key = JSON.stringify({ auswahl, config });
    const aktuell = useRef(key);
    useLayoutEffect(() => { aktuell.current = key; lauf.current++; setPruefung(null); setBestaetigt(false); }, [key]);
    useLayoutEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
    const geaendert = config !== null && configKey !== gespeichert;
    const grund = !auswahl.length ? 'Bitte zuerst Mitarbeiter für den Export auswählen.' : auswahl.length > 500 ? 'Bitte höchstens 500 Mitarbeiter auswählen.' : new Set(auswahl.map(s => `${s.jahr}/${s.monat}`)).size !== 1 ? 'Bitte genau einen Monat auswählen.' : auswahl.some(s => s.version === null) ? 'Bitte die ausgewählten Monate zuerst abschließen und aktualisieren.' : '';
    const gueltig = pruefung?.key === key ? pruefung.daten : null;
    const name = (id: number) => mitarbeiter.find(m => m.id === id)?.name ?? `Mitarbeiter ${id}`;
    function invalidieren() { setPruefung(null); setBestaetigt(false); lauf.current++; }
    function aendern(next: Konfiguration) { invalidieren(); setConfig(next); setFehler(''); }
    function melden(error: unknown) { setFehler(text(error)); toast.error(text(error)); }
    async function laden() {
        if (sperre.current) return;
        sperre.current = true; setBusy('Einstellungen werden geladen …'); setFehler(''); invalidieren();
        try { const c = normalisieren(await api.ladeDatevKonfiguration()); if (mounted.current) { setConfig(c); setGespeichert(JSON.stringify(c)); } }
        catch (error) { if (mounted.current) melden(error); }
        finally { sperre.current = false; if (mounted.current) setBusy(''); }
    }
    async function speichern() {
        if (!config || sperre.current) return;
        const nummern = config.personalnummern.filter(p => p.personalnummer !== '');
        const doppelt = new Set(nummern.map(p => p.personalnummer.replace(/^0+/, ''))).size !== nummern.length;
        const problem = nummerFehler(config.beraterNr ?? '', 4, 7, 'Beraternummer') || nummerFehler(config.mandantenNr ?? '', 1, 5, 'Mandantennummer') || config.zuordnungen.map(z => nummerFehler(z.lohnart ?? '', 1, 4, `Lohnart für ${kategorien.find(k => k[0] === z.kategorie)?.[1] ?? z.kategorie}`)).find(Boolean) || nummern.map(p => nummerFehler(p.personalnummer, 1, 5, `Personalnummer für ${name(p.mitarbeiterId)}`)).find(Boolean) || (doppelt ? 'Eine Personalnummer ist mehrfach vergeben. Bitte für jeden Mitarbeiter eine eigene Nummer eintragen.' : '');
        if (problem) { melden(new Error(problem)); return; }
        sperre.current = true; setBusy('Einstellungen werden gespeichert …'); setFehler(''); invalidieren();
        try { const c = normalisieren(await api.speichereDatevKonfiguration({ ...config, personalnummern: nummern })); if (mounted.current) { setConfig(c); setGespeichert(JSON.stringify(c)); toast.success('DATEV-Einstellungen gespeichert.'); } }
        catch (error) { if (mounted.current) melden(error); }
        finally { sperre.current = false; if (mounted.current) setBusy(''); }
    }
    async function vorpruefen() {
        if (!config || grund || geaendert || sperre.current) return;
        invalidieren(); const nummer = lauf.current; const startKey = key;
        sperre.current = true; setBusy('Vorprüfung läuft …'); setFehler('');
        try {
            const daten = await api.pruefeDatev({ auswahl, konfigurationVersion: config.version });
            if (mounted.current && lauf.current === nummer && aktuell.current === startKey) {
                if (JSON.stringify(daten.auswahl) !== JSON.stringify(auswahl) || daten.konfigurationVersion !== config.version) throw new Error('Der geprüfte Stand passt nicht mehr zur Auswahl. Bitte erneut prüfen.');
                setPruefung({ key: startKey, daten });
            }
        } catch (error) { if (mounted.current && lauf.current === nummer && aktuell.current === startKey) melden(error); }
        finally { sperre.current = false; if (mounted.current) setBusy(''); }
    }
    async function herunterladen() {
        if (!config || !gueltig?.gueltig || gueltig.fehler.length || (gueltig.ausschluesse.length > 0 && !bestaetigt) || grund || geaendert || sperre.current) return;
        const nummer = lauf.current; const startKey = key; sperre.current = true; setBusy('Datei wird erstellt …'); setFehler('');
        try {
            const datei = await api.exportiereDatev({ auswahl, konfigurationVersion: config.version });
            if (!mounted.current || nummer !== lauf.current || aktuell.current !== startKey) return;
            const url = URL.createObjectURL(datei.blob); const link = document.createElement('a');
            try { link.href = url; link.download = datei.dateiname; document.body.append(link); link.click(); }
            finally { link.remove(); URL.revokeObjectURL(url); }
            toast.success('Datei heruntergeladen – bitte im Steuerbüro importieren.'); invalidieren();
        } catch (error) { if (mounted.current && nummer === lauf.current && aktuell.current === startKey) { invalidieren(); melden(error); } }
        finally { sperre.current = false; if (mounted.current) setBusy(''); }
    }
    function hinweise(liste: Hinweis[]) { return <ul className="space-y-2">{liste.map((h, i) => <li key={i} className="rounded-md bg-slate-50 p-3 text-sm"><span className="font-medium">{h.referenz?.mitarbeiterId ? `${name(h.referenz.mitarbeiterId)} · ` : ''}{h.referenz ? `${String(h.referenz.monat).padStart(2, '0')}/${h.referenz.jahr} · ` : ''}{kategorien.find(k => k[0] === h.kategorie)?.[1] ?? (h.kategorie === 'KORREKTUR' ? 'Korrektur' : h.kategorie === 'ABWESENHEIT_UNGEGLIEDERT' ? 'Alte Abwesenheit' : h.kategorie === 'STAND' ? 'Monatsstand' : 'Einstellungen')}</span>{h.stunden !== null && <span className="ml-2 tabular-nums">{h.stunden.toLocaleString('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} h</span>}<p className="mt-1">{h.meldung}</p></li>)}</ul>; }
    return <section aria-label="DATEV-Export" className="rounded-lg border border-slate-200 bg-white p-4 space-y-4">
        <div className="flex items-center justify-between gap-4"><div><h2 className="font-semibold">DATEV für das Steuerbüro</h2><p className="text-sm text-slate-500">Stunden als LODAS-Datei übergeben. Keine Lohnberechnung.</p></div><div className="flex shrink-0 gap-2"><Button variant="outline" size="sm" aria-expanded={einstellungen} onClick={() => { setEinstellungen(!einstellungen); if (!config && !einstellungen) void laden(); }}><Settings className="mr-2 h-4 w-4" />DATEV einrichten</Button><Button variant="outline" size="sm" disabled={!!grund || !!busy} onClick={() => { setOffen(true); setFehler(''); invalidieren(); if (!config) void laden(); }}><Download className="mr-2 h-4 w-4" />Für DATEV exportieren</Button></div></div>
        {grund && <p className="text-sm text-slate-500">{grund}</p>}
        {!offen && busy && <p role="status">{busy}</p>}{!offen && fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
        {einstellungen && <div className="border-t border-slate-200 pt-4 space-y-4"><p className="text-sm text-slate-600">Nummern und Lohnarten bitte mit dem Steuerbüro abstimmen. Führende Nullen bleiben erhalten. Leere Angaben können gespeichert werden, verhindern aber gegebenenfalls den Export.</p>{config && <fieldset disabled={!!busy} className="space-y-4 disabled:opacity-60"><div className="grid grid-cols-3 gap-4"><div className="space-y-1"><p className="text-sm font-medium">Zielprogramm</p><Select aria-label="Zielprogramm" value={config.ziel} options={[{ value: 'LODAS', label: 'DATEV LODAS' }]} disabled={!!busy} onChange={ziel => aendern({ ...config, ziel })} /></div><Nummer label="Beraternummer" value={config.beraterNr ?? ''} onChange={beraterNr => aendern({ ...config, beraterNr })} /><Nummer label="Mandantennummer" value={config.mandantenNr ?? ''} onChange={mandantenNr => aendern({ ...config, mandantenNr })} /></div>
            <h3 className="font-medium">Stunden zuordnen</h3><div className="grid grid-cols-2 gap-x-6 gap-y-3">{kategorien.map(([kategorie, label]) => { const z = config.zuordnungen.find(z => z.kategorie === kategorie)!; const modus = z.ausgeschlossen ? 'aus' : z.lohnart !== null && z.lohnart !== '' ? 'lohn' : z.lohnart === null ? 'lohn' : ''; return <div key={kategorie} className="grid grid-cols-2 gap-3 rounded-md bg-slate-50 p-3"><div className="space-y-1"><p className="text-sm font-medium">{label}</p><Select aria-label={`Export für ${label}`} disabled={!!busy} value={modus} options={[{ value: '', label: 'Bitte wählen' }, { value: 'lohn', label: 'Lohnart zuordnen' }, { value: 'aus', label: 'Nicht exportieren' }]} onChange={v => aendern({ ...config, zuordnungen: config.zuordnungen.map(row => row.kategorie === kategorie ? { ...row, ausgeschlossen: v === 'aus', lohnart: v === 'lohn' ? null : '' } : row) })} /></div>{modus === 'lohn' && <Nummer label={`Lohnart für ${label}`} value={z.lohnart ?? ''} onChange={lohnart => aendern({ ...config, zuordnungen: config.zuordnungen.map(row => row.kategorie === kategorie ? { ...row, lohnart: lohnart || null } : row) })} />}</div>; })}</div>
            <p className="text-sm text-slate-600">Zeitkontokorrekturen werden nicht ausgezahlt. Nicht zugeordnete alte Abwesenheiten werden in der Vorprüfung gesondert angezeigt.</p><h3 className="font-medium">Personalnummern</h3><div className="grid grid-cols-3 gap-4">{mitarbeiter.map(m => <Nummer key={m.id} label={`Personalnummer für ${m.name}`} value={config.personalnummern.find(p => p.mitarbeiterId === m.id)?.personalnummer ?? ''} onChange={personalnummer => aendern({ ...config, personalnummern: [...config.personalnummern.filter(p => p.mitarbeiterId !== m.id), { mitarbeiterId: m.id, personalnummer }].sort((a, b) => a.mitarbeiterId - b.mitarbeiterId) })} />)}</div><Button onClick={speichern}>Einstellungen speichern</Button></fieldset>}<Button variant="outline" size="sm" disabled={!!busy} onClick={laden}>Einstellungen neu laden</Button></div>}
        <Dialog open={offen} onOpenChange={v => { setOffen(v); if (!v) { invalidieren(); setFehler(''); } }}><DialogContent className="max-w-2xl" aria-label="DATEV-Export prüfen"><DialogHeader><DialogTitle>DATEV-Export prüfen</DialogTitle></DialogHeader><div className="overflow-y-auto space-y-4 pr-1"><p className="text-sm text-slate-600">Nur diese bewusst ausgewählten Monatsstände werden übergeben:</p><ul className="max-h-40 overflow-auto rounded-md bg-slate-50 p-3 text-sm space-y-1">{auswahl.map(s => <li key={`${s.mitarbeiterId}/${s.jahr}/${s.monat}`}><span className="font-medium">{name(s.mitarbeiterId)}</span> · {String(s.monat).padStart(2, '0')}/{s.jahr} · Stand {s.version ?? 'offen'}</li>)}</ul>{grund && <p role="alert">{grund}</p>}{geaendert && <p role="alert" className="text-sm text-amber-800">Bitte zuerst die geänderten DATEV-Einstellungen speichern.</p>}{busy && <p role="status">{busy}</p>}{fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}{!config && !busy && <Button variant="outline" onClick={laden}>Einstellungen neu laden</Button>}{gueltig && <>{gueltig.gueltig && !gueltig.fehler.length ? <p className="font-medium text-slate-800">Die Vorprüfung ist erfolgreich.</p> : <h4 className="font-medium text-rose-700">Bitte vor dem Export korrigieren</h4>}{hinweise(gueltig.fehler)}{gueltig.ausschluesse.length > 0 && <><h4 className="font-medium">Diese Stunden werden nicht exportiert</h4>{hinweise(gueltig.ausschluesse)}<label className="flex items-start gap-2 text-sm"><input type="checkbox" className="mt-1 h-4 w-4 shrink-0" checked={bestaetigt} disabled={!!busy} onChange={e => setBestaetigt(e.target.checked)} />Ich habe die ausgeschlossenen Stunden geprüft und möchte ohne diese Stunden exportieren.</label></>}</>}</div><DialogFooter><Button variant="outline" disabled={!!busy || !config || !!grund || geaendert} onClick={vorpruefen}>Vorprüfung starten</Button><Button disabled={!!busy || !!grund || geaendert || !gueltig?.gueltig || !!gueltig.fehler.length || (!!gueltig.ausschluesse.length && !bestaetigt)} onClick={herunterladen}>Datei herunterladen</Button></DialogFooter></DialogContent></Dialog>
    </section>;
}
