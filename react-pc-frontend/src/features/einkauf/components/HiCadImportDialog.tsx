import { useMemo, useState } from 'react';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { useToast } from '../../../components/ui/toast';
import { toSafeResourceUrl } from '../../../lib/htmlSanitizer';
import { einkaufApi } from '../api';
import type { HiCadImportFortschritt, HiCadVorschau, HiCadZeilenAuswahl, PositionSnapshot } from '../types';
import { StammdatenAuswahl, type StammdatenWahl } from './StammdatenAuswahl';

const MAX_DATEI = 10 * 1024 * 1024;
const zahl = (value: string) => {
  const normal = value.trim().replace(/\s/g, '').replace(',', '.');
  if (!/^(?:\d+)(?:\.\d{1,6})?$/.test(normal)) return null;
  const number = Number(normal); return Number.isFinite(number) && number > 0 ? number : null;
};
const dateiTyp = (datei: File) => /\.(xls|xlsx)$/i.test(datei.name);

export function HiCadImportDialog({ onClose, onImported }: { onClose: () => void; onImported: () => void }) {
  const toast = useToast();
  const [projekt, setProjekt] = useState<StammdatenWahl | null>(null);
  const [datei, setDatei] = useState<File | null>(null);
  const [spalten, setSpalten] = useState<Record<string, string>>({});
  const [spaltenZuordnungOffen, setSpaltenZuordnungOffen] = useState(false);
  const [vorschau, setVorschau] = useState<HiCadVorschau | null>(null);
  const [fortschritt, setFortschritt] = useState<HiCadImportFortschritt | null>(null);
  const [auswahl, setAuswahl] = useState<Record<number, boolean>>({});
  const [mengen, setMengen] = useState<Record<number, string>>({});
  const [artikel, setArtikel] = useState<Record<number, StammdatenWahl>>({});
  const [bilder, setBilder] = useState<Record<number, number[]>>({});
  const [duplikatBestaetigt, setDuplikatBestaetigt] = useState(false);
  const [laden, setLaden] = useState(false);
  const [fehler, setFehler] = useState('');
  const fortschrittMap = useMemo(() => new Map((fortschritt?.zeilen ?? []).map(row => [row.zeilennummer, row])), [fortschritt]);

  const dateiWaehlen = (file: File | null) => {
    setDatei(file); setVorschau(null); setFortschritt(null); setFehler(''); setDuplikatBestaetigt(false);
    if (file && file.size > MAX_DATEI) { const meldung = 'Die HiCAD-Datei darf höchstens 10 MiB groß sein.'; setDatei(null); setFehler(meldung); toast.error(meldung); }
    else if (file && !dateiTyp(file)) { setDatei(null); const meldung = 'Bitte eine XLS- oder XLSX-Datei auswählen.'; setFehler(meldung); toast.error(meldung); }
  };
  const vorschauLaden = async () => {
    if (!projekt) { const meldung = 'Bitte zuerst ein Projekt auswählen.'; setFehler(meldung); toast.error(meldung); return; }
    if (!datei) { const meldung = 'Bitte eine HiCAD-Datei auswählen.'; setFehler(meldung); toast.error(meldung); return; }
      const body = new FormData(); body.append('file', datei);
      const zuordnung = Object.fromEntries(Object.entries(spalten).filter(([, spalte]) => spalte.trim()).map(([feld, spalte]) => [feld, Number(spalte) - 1]));
      if (Object.keys(zuordnung).length) body.append('mapping', new Blob([JSON.stringify({ spalten: zuordnung })], { type: 'application/json' }));
    setLaden(true); setFehler('');
    try {
      const response = await fetch(`/api/einkauf/hicad/vorschau?projektId=${projekt.id}`, { method: 'POST', body });
      if (!response.ok) throw new Error(`HiCAD-Vorschau konnte nicht geladen werden (HTTP ${response.status}).`);
      const result = await response.json() as HiCadVorschau;
      const status = await einkaufApi.get<HiCadImportFortschritt>(`/api/einkauf/hicad/${result.id}`);
      setVorschau(result); setFortschritt(status);
      setAuswahl(Object.fromEntries(result.zeilen.map(row => [row.zeilennummer, !row.bereitsUebernommen && (status.zeilen.find(value => value.zeilennummer === row.zeilennummer)?.verbleibendeMenge ?? 0) > 0])));
      setMengen(Object.fromEntries(result.zeilen.map(row => { const statusRow = status.zeilen.find(value => value.zeilennummer === row.zeilennummer); return [row.zeilennummer, String(statusRow?.verbleibendeMenge ?? row.vorschlag?.basis?.menge ?? '').replace('.', ',')]; })));
    } catch (error) { const message = error instanceof Error ? error.message : 'HiCAD-Vorschau konnte nicht geladen werden.'; setFehler(message); toast.error(message); }
    finally { setLaden(false); }
  };
  const uebernehmen = async () => {
    if (!vorschau || !fortschritt) return;
    let zeilen: HiCadZeilenAuswahl[];
    try {
      zeilen = vorschau.zeilen.filter(zeile => auswahl[zeile.zeilennummer]).map(zeile => {
        const teilmenge = zahl(mengen[zeile.zeilennummer] ?? '');
        if (!teilmenge || teilmenge > (fortschrittMap.get(zeile.zeilennummer)?.verbleibendeMenge ?? 0)) throw new Error(`Bitte eine gültige Teilmenge für Zeile ${zeile.zeilennummer} eingeben.`);
        let korrigiert: PositionSnapshot | null = zeile.vorschlag;
        const artikelwahl = artikel[zeile.zeilennummer];
        if (korrigiert && artikelwahl) korrigiert = { ...korrigiert, art: 'ARTIKEL', artikelId: artikelwahl.id, bezeichnung: artikelwahl.name };
        if (korrigiert?.art === 'ZEICHNUNGSTEIL' && !bilder[zeile.zeilennummer]?.length) throw new Error(`Bitte geben Sie mindestens ein eingebettetes Bild für Zeichnungsteil Zeile ${zeile.zeilennummer} frei.`);
        return { zeilennummer: zeile.zeilennummer, menge: teilmenge, korrigiert, bestaetigteBildDateiIds: bilder[zeile.zeilennummer] ?? [] };
      });
      if (!zeilen.length) throw new Error('Bitte mindestens eine Zeile zur Übernahme auswählen.');
    } catch (error) {
      const meldung = error instanceof Error ? error.message : 'Bitte prüfen Sie die ausgewählten Zeilen.';
      setFehler(meldung); toast.error(meldung); return;
    }
    setLaden(true); setFehler('');
    try {
      await einkaufApi.post(`/api/einkauf/hicad/${vorschau.id}/uebernehmen`, { version: fortschritt.version, zeilen, duplikatBewusst: duplikatBestaetigt, idempotenzKey: crypto.randomUUID() });
      onImported();
    } catch (error) { const message = error instanceof Error ? error.message : 'HiCAD-Zeilen konnten nicht übernommen werden.'; setFehler(message); toast.error(message); }
    finally { setLaden(false); }
  };

  return <Dialog open onOpenChange={open => { if (!open && !laden) onClose(); }}><DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-4xl">
    <DialogHeader><DialogTitle>HiCAD-Import prüfen</DialogTitle></DialogHeader>
    <div className="space-y-4">
      <StammdatenAuswahl art="Projekt" value={projekt} onChange={auswahl => { if (projekt?.id !== auswahl?.id) { setProjekt(auswahl); setVorschau(null); setFortschritt(null); setAuswahl({}); setMengen({}); setBilder({}); setDuplikatBestaetigt(false); } }} />
      <div className="flex flex-wrap items-end gap-3"><label className="space-y-1 text-sm font-medium">HiCAD-Exceldatei (.xls oder .xlsx)<input className="hidden" aria-label="HiCAD-Exceldatei" type="file" accept=".xls,.xlsx" onChange={event => dateiWaehlen(event.target.files?.[0] ?? null)} /><span className="block"><Button type="button" variant="outline" onClick={event => { const eingabe = event.currentTarget.parentElement?.previousElementSibling; if (eingabe instanceof HTMLInputElement) eingabe.click(); }}>Datei auswählen</Button> <span className="text-slate-600">{datei?.name ?? 'Keine Datei ausgewählt'}{datei ? ` · ${(datei.size / 1024 / 1024).toLocaleString('de-DE', { maximumFractionDigits: 2 })} MiB` : ''}</span></span></label>
        <Button disabled={laden || !datei || !projekt} onClick={() => void vorschauLaden()}>{laden ? 'Vorschau wird geladen …' : 'Vorschau laden'}</Button></div>
      <section className="rounded-lg border border-slate-200 p-3"><Button type="button" variant="ghost" aria-expanded={spaltenZuordnungOffen} onClick={() => setSpaltenZuordnungOffen(wert => !wert)}>Spalten manuell zuordnen</Button>{spaltenZuordnungOffen && <><p className="mt-2 text-sm text-slate-600">Tragen Sie die Spaltennummer aus der Tabellenkopfzeile ein. Ohne Angaben erkennt HiCAD die üblichen deutschen Spaltenüberschriften selbst.</p><div className="mt-3 grid gap-3 sm:grid-cols-3">{[['interneReferenz','Interne Nummer'],['zeichnungsnummer','Zeichnungsnummer'],['zeichnungsrevision','Zeichnungsrevision'],['bezeichnung','Bezeichnung'],['werkstoff','Werkstoff'],['abmessung','Abmessung'],['menge','Menge'],['einheit','Einheit'],['stueckzahl','Stückzahl'],['einzelLaengeMm','Einzellänge mm'],['winkelLinks','Linker Winkel'],['winkelRechts','Rechter Winkel']].map(([feld, titel]) => <label key={feld} className="space-y-1 text-sm">{titel}<Input aria-label={`Spalte ${titel}`} inputMode="numeric" value={spalten[feld] ?? ''} onChange={ereignis => setSpalten(aktuell => ({ ...aktuell, [feld]: ereignis.target.value.replace(/[^0-9]/g, '') }))} placeholder="z. B. 1" /></label>)}</div></>}</section>
      {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
      {vorschau && <div className="space-y-3"><p className="rounded-md bg-slate-50 p-3 text-sm">{vorschau.zeilen.length} Zeilen gefunden. Vorschau {vorschau.dateiSchonImportiert ? 'wurde bereits importiert; vorhandene Teilmengen bleiben erhalten.' : 'prüfen und nur bestätigte Zeilen übernehmen.'}</p>
        {vorschau.dateiSchonImportiert && <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={duplikatBestaetigt} onChange={event => setDuplikatBestaetigt(event.target.checked)} />Erneuten Import bewusst zulassen</label>}
        <div className="space-y-3">{vorschau.zeilen.map(row => { const current = fortschrittMap.get(row.zeilennummer); return <article key={row.zeilennummer} className="grid min-w-0 gap-3 rounded-lg border border-slate-200 p-3 md:grid-cols-[auto_minmax(0,1fr)_9rem]"><input type="checkbox" aria-label={`Zeile ${row.zeilennummer} übernehmen`} checked={Boolean(auswahl[row.zeilennummer])} disabled={!current || current.verbleibendeMenge <= 0} onChange={event => setAuswahl(value => ({ ...value, [row.zeilennummer]: event.target.checked }))} />
          <div className="min-w-0 space-y-2"><h3 className="font-medium">Zeile {row.zeilennummer}: {row.vorschlag?.bezeichnung ?? row.rohtext}</h3><p className="break-words text-sm text-slate-600">{row.rohtext}</p><p className="text-sm text-slate-600">Artikel-Kandidaten: {row.artikelKandidaten.length ? 'Bitte passenden Artikel auswählen' : 'Kein passender Artikel gefunden'} · schon übernommen {current?.uebernommeneMenge ?? 0}, noch offen {current?.verbleibendeMenge ?? 0}</p>{row.artikelKandidaten.length > 0 && <StammdatenAuswahl art="Artikel" value={artikel[row.zeilennummer] ?? null} onChange={value => setArtikel(current => value ? { ...current, [row.zeilennummer]: value } : current)} />}
          {row.bilder.length > 0 && <div className="flex flex-wrap gap-3">{row.bilder.map(image => <label key={image.dateiId} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={(bilder[row.zeilennummer] ?? []).includes(image.dateiId)} onChange={event => setBilder(current => ({ ...current, [row.zeilennummer]: event.target.checked ? [...(current[row.zeilennummer] ?? []), image.dateiId] : (current[row.zeilennummer] ?? []).filter(id => id !== image.dateiId) }))} />{toSafeResourceUrl(image.url) && <img className="h-16 w-20 rounded border border-slate-200 object-contain" src={toSafeResourceUrl(image.url) ?? undefined} alt={image.dateiname} />}Bild freigeben: {image.dateiname}</label>)}</div>}
          {row.hinweise.map(note => <p key={note} className="text-sm text-amber-800">{note}</p>)}</div>
          <label className="space-y-1 text-sm font-medium">Teilmenge<Input aria-label={`Menge Zeile ${row.zeilennummer}`} inputMode="decimal" value={mengen[row.zeilennummer] ?? ''} onChange={event => setMengen(value => ({ ...value, [row.zeilennummer]: event.target.value }))} /></label>
        </article>; })}</div>
      </div>}
    </div>
    <DialogFooter><Button variant="outline" disabled={laden} onClick={onClose}>Schließen</Button>{vorschau && <Button disabled={laden || (vorschau.dateiSchonImportiert && !duplikatBestaetigt)} onClick={() => void uebernehmen()}>{laden ? 'Wird übernommen …' : 'Ausgewählte Zeilen übernehmen'}</Button>}</DialogFooter>
  </DialogContent></Dialog>;
}
