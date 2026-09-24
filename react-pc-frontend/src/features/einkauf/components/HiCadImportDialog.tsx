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
const dateiTyp = (file: File) => /\.(sza|tcd)$/i.test(file.name);

export function HiCadImportDialog({ onClose, onImported }: { onClose: () => void; onImported: () => void }) {
  const toast = useToast();
  const [projekt, setProjekt] = useState<StammdatenWahl | null>(null);
  const [datei, setDatei] = useState<File | null>(null);
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
    if (file && file.size > MAX_DATEI) { setDatei(null); setFehler('Die HiCAD-Datei darf höchstens 10 MiB groß sein.'); }
    else if (file && !dateiTyp(file)) { setDatei(null); setFehler('Bitte eine .sza- oder .tcd-Datei auswählen.'); }
  };
  const vorschauLaden = async () => {
    if (!projekt) { setFehler('Bitte zuerst ein Projekt auswählen.'); return; }
    if (!datei) { setFehler('Bitte eine HiCAD-Datei auswählen.'); return; }
    const body = new FormData(); body.append('file', datei);
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
    const rows: HiCadZeilenAuswahl[] = vorschau.zeilen.filter(row => auswahl[row.zeilennummer]).map(row => {
      const menge = zahl(mengen[row.zeilennummer] ?? '');
      if (!menge || menge > (fortschrittMap.get(row.zeilennummer)?.verbleibendeMenge ?? 0)) throw new Error(`Bitte eine gültige Teilmenge für Zeile ${row.zeilennummer} eingeben.`);
      let korrigiert: PositionSnapshot | null = row.vorschlag;
      const match = artikel[row.zeilennummer];
      if (korrigiert && match) korrigiert = { ...korrigiert, art: 'ARTIKEL', artikelId: match.id, bezeichnung: match.name };
      if (korrigiert?.art === 'ZEICHNUNGSTEIL' && (!korrigiert.anlageVersionIds?.length || !bilder[row.zeilennummer]?.length)) throw new Error(`Für Zeichnungsteil Zeile ${row.zeilennummer} muss eine Anlage ausgewählt und freigegeben sein.`);
      return { zeilennummer: row.zeilennummer, menge, korrigiert, bestaetigteBildDateiIds: bilder[row.zeilennummer] ?? [] };
    });
    if (!rows.length) { setFehler('Bitte mindestens eine Zeile zur Übernahme auswählen.'); return; }
    setLaden(true); setFehler('');
    try {
      await einkaufApi.post(`/api/einkauf/hicad/${vorschau.id}/uebernehmen`, { version: fortschritt.version, zeilen: rows, duplikatBewusst: duplikatBestaetigt, idempotenzKey: crypto.randomUUID() });
      onImported();
    } catch (error) { const message = error instanceof Error ? error.message : 'HiCAD-Zeilen konnten nicht übernommen werden.'; setFehler(message); toast.error(message); }
    finally { setLaden(false); }
  };

  return <Dialog open onOpenChange={open => { if (!open && !laden) onClose(); }}><DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-4xl">
    <DialogHeader><DialogTitle>HiCAD-Import prüfen</DialogTitle></DialogHeader>
    <div className="space-y-4">
      <StammdatenAuswahl art="Projekt" value={projekt} onChange={setProjekt} />
      <div className="flex flex-wrap items-end gap-3"><label className="space-y-1 text-sm font-medium">HiCAD-Datei (.sza oder .tcd)<input className="sr-only" aria-label="HiCAD-Datei" type="file" accept=".sza,.tcd" onChange={event => dateiWaehlen(event.target.files?.[0] ?? null)} /><span className="block"><Button type="button" variant="outline" onClick={event => { const input = event.currentTarget.parentElement?.previousElementSibling; if (input instanceof HTMLInputElement) input.click(); }}>Datei auswählen</Button> <span className="text-slate-600">{datei?.name ?? 'Keine Datei ausgewählt'}{datei ? ` · ${(datei.size / 1024 / 1024).toLocaleString('de-DE', { maximumFractionDigits: 2 })} MiB` : ''}</span></span></label>
        <Button disabled={laden || !datei || !projekt} onClick={() => void vorschauLaden()}>{laden ? 'Vorschau wird geladen …' : 'Vorschau laden'}</Button></div>
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
