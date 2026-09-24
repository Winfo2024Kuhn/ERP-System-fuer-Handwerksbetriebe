import { useMemo, useState } from 'react';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { CreateArticleModal } from '../../../components/CreateArticleModal';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { useToast } from '../../../components/ui/toast';
import { toSafeResourceUrl } from '../../../lib/htmlSanitizer';
import { einkaufApi } from '../api';
import type { HiCadImportFortschritt, HiCadVorschau, HiCadZeilenAuswahl, PositionSnapshot } from '../types';
import { StammdatenAuswahl, type StammdatenWahl } from './StammdatenAuswahl';

const MAX_DATEI = 10 * 1024 * 1024;
const zahl = (wert: string) => {
  const normal = wert.trim().replace(/\s/g, '').replace(',', '.');
  if (!/^(?:\d+)(?:\.\d{1,6})?$/.test(normal)) return null;
  const zahlenwert = Number(normal); return Number.isFinite(zahlenwert) && zahlenwert > 0 ? zahlenwert : null;
};
const dateiTyp = (datei: File) => /\.(xls|xlsx)$/i.test(datei.name);

export function HiCadImportDialog({ schließen, übernommen }: { schließen: () => void; übernommen: () => void }) {
  const meldungen = useToast();
  const [projekt, setzeProjekt] = useState<StammdatenWahl | null>(null);
  const [datei, setzeDatei] = useState<File | null>(null);
  const [spalten, setzeSpalten] = useState<Record<string, string>>({});
  const [spaltenZuordnungOffen, setzeSpaltenZuordnungOffen] = useState(false);
  const [vorschau, setzeVorschau] = useState<HiCadVorschau | null>(null);
  const [fortschritt, setzeFortschritt] = useState<HiCadImportFortschritt | null>(null);
  const [auswahl, setzeAuswahl] = useState<Record<number, boolean>>({});
  const [mengen, setzeMengen] = useState<Record<number, string>>({});
  const [artikel, setzeArtikel] = useState<Record<number, StammdatenWahl>>({});
  const [bilder, setzeBilder] = useState<Record<number, number[]>>({});
  const [duplikatBestätigt, setzeDuplikatBestätigt] = useState(false);
  const [artikelAnlageZeile, setzeArtikelAnlageZeile] = useState<number | null>(null);
  const [laden, setzeLaden] = useState(false);
  const [fehler, setzeFehler] = useState('');
  const fortschrittJeZeile = useMemo(() => new Map((fortschritt?.zeilen ?? []).map(zeile => [zeile.zeilennummer, zeile])), [fortschritt]);

  const dateiWählen = (datei: File | null) => {
    setzeDatei(datei); setzeArtikel({}); setzeBilder({}); setzeVorschau(null); setzeFortschritt(null); setzeFehler(''); setzeDuplikatBestätigt(false);
    if (datei && datei.size > MAX_DATEI) { const meldung = 'Die HiCAD-Datei darf höchstens 10 MiB groß sein.'; setzeDatei(null); setzeFehler(meldung); meldungen.error(meldung); }
    else if (datei && !dateiTyp(datei)) { setzeDatei(null); const meldung = 'Bitte eine XLS- oder XLSX-Datei auswählen.'; setzeFehler(meldung); meldungen.error(meldung); }
  };
  const vorschauLaden = async () => {
    if (!projekt) { const meldung = 'Bitte zuerst ein Projekt auswählen.'; setzeFehler(meldung); meldungen.error(meldung); return; }
    if (!datei) { const meldung = 'Bitte eine HiCAD-Datei auswählen.'; setzeFehler(meldung); meldungen.error(meldung); return; }
      const anfrageinhalt = new FormData(); anfrageinhalt.append('file', datei);
      const zuordnung = Object.fromEntries(Object.entries(spalten).filter(([, spalte]) => spalte.trim()).map(([feld, spalte]) => [feld, Number(spalte) - 1]));
      if (Object.keys(zuordnung).length) anfrageinhalt.append('mapping', new Blob([JSON.stringify({ spalten: zuordnung })], { type: 'application/json' }));
    setzeLaden(true); setzeFehler('');
    try {
      const antwort = await fetch(`/api/einkauf/hicad/vorschau?projektId=${projekt.id}`, { method: 'POST', body: anfrageinhalt });
      if (!antwort.ok) throw new Error(`HiCAD-Vorschau konnte nicht geladen werden (HTTP ${antwort.status}).`);
      const ergebnis = await antwort.json() as HiCadVorschau;
      const zustand = await einkaufApi.get<HiCadImportFortschritt>(`/api/einkauf/hicad/${ergebnis.id}`);
      setzeVorschau(ergebnis); setzeFortschritt(zustand);
      setzeAuswahl(Object.fromEntries(ergebnis.zeilen.map(zeile => [zeile.zeilennummer, !zeile.bereitsUebernommen && (zustand.zeilen.find(wert => wert.zeilennummer === zeile.zeilennummer)?.verbleibendeMenge ?? 0) > 0])));
      setzeMengen(Object.fromEntries(ergebnis.zeilen.map(zeile => { const zeilenstand = zustand.zeilen.find(wert => wert.zeilennummer === zeile.zeilennummer); return [zeile.zeilennummer, String(zeilenstand?.verbleibendeMenge ?? zeile.vorschlag?.basis?.menge ?? '').replace('.', ',')]; })));
    } catch (fehlerursache) { const meldung = fehlerursache instanceof Error ? fehlerursache.message : 'HiCAD-Vorschau konnte nicht geladen werden.'; setzeFehler(meldung); meldungen.error(meldung); }
    finally { setzeLaden(false); }
  };
  const anlageErgänzen = async (zeilennummer: number, datei: File | null) => {
    if (!datei || !vorschau) return;
    if (datei.size > MAX_DATEI) { const meldung = 'Die technische Anlage darf höchstens 10 MiB groß sein.'; setzeFehler(meldung); meldungen.error(meldung); return; }
    setzeLaden(true); setzeFehler('');
    try {
      const formulardaten = new FormData(); formulardaten.append('datei', datei);
      const antwort = await fetch(`/api/einkauf/hicad/${vorschau.id}/zeilen/${zeilennummer}/anlagen`, { method: 'POST', body: formulardaten });
      if (!antwort.ok) throw new Error(`Die Anlage konnte nicht ergänzt werden (HTTP ${antwort.status}).`);
      const anlage = await antwort.json() as HiCadVorschau['zeilen'][number]['bilder'][number];
      setzeVorschau(aktuell => aktuell ? { ...aktuell, zeilen: aktuell.zeilen.map(zeile => zeile.zeilennummer === zeilennummer ? { ...zeile, bilder: [...zeile.bilder.filter(bild => bild.dateiId !== anlage.dateiId), anlage] } : zeile) } : null);
      meldungen.success('Anlage ergänzt. Bitte prüfen und vor der Übernahme freigeben.');
    } catch (ursache) { const meldung = ursache instanceof Error ? ursache.message : 'Die Anlage konnte nicht ergänzt werden.'; setzeFehler(meldung); meldungen.error(meldung); }
    finally { setzeLaden(false); }
  };
  const positionsfeldÄndern = (zeilennummer: number, feld: 'interneReferenz' | 'zeichnungsnummer' | 'zeichnungsrevision' | 'bezeichnung' | 'werkstoff' | 'abmessung', wert: string) => {
    setzeVorschau(aktuell => aktuell ? { ...aktuell, zeilen: aktuell.zeilen.map(zeile => zeile.zeilennummer === zeilennummer && zeile.vorschlag ? { ...zeile, vorschlag: { ...zeile.vorschlag, [feld]: wert } } : zeile) } : null);
  };
  const übernehmen = async () => {
    if (!vorschau || !fortschritt) return;
    let zeilen: HiCadZeilenAuswahl[];
    try {
      zeilen = vorschau.zeilen.filter(zeile => auswahl[zeile.zeilennummer]).map(zeile => {
        const teilmenge = zahl(mengen[zeile.zeilennummer] ?? '');
        if (!teilmenge || teilmenge > (fortschrittJeZeile.get(zeile.zeilennummer)?.verbleibendeMenge ?? 0)) throw new Error(`Bitte eine gültige Teilmenge für Zeile ${zeile.zeilennummer} eingeben.`);
        let korrigiert: PositionSnapshot | null = zeile.vorschlag;
        const artikelwahl = artikel[zeile.zeilennummer];
        if (korrigiert && artikelwahl) korrigiert = { ...korrigiert, art: 'ARTIKEL', artikelId: artikelwahl.id, bezeichnung: artikelwahl.name };
        if (zeile.bilder.some(bild => !(bilder[zeile.zeilennummer] ?? []).includes(bild.dateiId))) throw new Error(`Bitte geben Sie alle Bilder und Anlagen für Zeile ${zeile.zeilennummer} frei.`);
        if (korrigiert?.art === 'ZEICHNUNGSTEIL' && !bilder[zeile.zeilennummer]?.length) throw new Error(`Bitte ergänzen und bestätigen Sie eine technische Anlage für Zeichnungsteil Zeile ${zeile.zeilennummer} frei.`);
        return { zeilennummer: zeile.zeilennummer, menge: teilmenge, korrigiert, bestaetigteBildDateiIds: bilder[zeile.zeilennummer] ?? [] };
      });
      if (!zeilen.length) throw new Error('Bitte mindestens eine Zeile zur Übernahme auswählen.');
    } catch (fehlerursache) {
      const meldung = fehlerursache instanceof Error ? fehlerursache.message : 'Bitte prüfen Sie die ausgewählten Zeilen.';
      setzeFehler(meldung); meldungen.error(meldung); return;
    }
    setzeLaden(true); setzeFehler('');
    try {
      await einkaufApi.post(`/api/einkauf/hicad/${vorschau.id}/uebernehmen`, { version: fortschritt.version, zeilen, duplikatBewusst: duplikatBestätigt, idempotenzKey: crypto.randomUUID() });
      übernommen();
    } catch (fehlerursache) { const meldung = fehlerursache instanceof Error ? fehlerursache.message : 'HiCAD-Zeilen konnten nicht übernommen werden.'; setzeFehler(meldung); meldungen.error(meldung); }
    finally { setzeLaden(false); }
  };

  return <Dialog className="w-[min(64rem,calc(100vw-2rem))]" open onOpenChange={offen => { if (!offen && !laden) schließen(); }}><DialogContent className="overflow-hidden">
    <DialogHeader><DialogTitle>HiCAD-Import prüfen</DialogTitle></DialogHeader>
    <div className="min-h-0 flex-1 space-y-4 overflow-y-auto pr-2">
      <StammdatenAuswahl art="Projekt" value={projekt} onChange={auswahl => { if (projekt?.id !== auswahl?.id) { setzeProjekt(auswahl); setzeVorschau(null); setzeFortschritt(null); setzeAuswahl({}); setzeMengen({}); setzeArtikel({}); setzeBilder({}); setzeDuplikatBestätigt(false); } }} />
      <div className="flex flex-wrap items-end gap-3"><label className="space-y-1 text-sm font-medium">HiCAD-Exceldatei (.xls oder .xlsx)<input className="hidden" aria-label="HiCAD-Exceldatei" type="file" accept=".xls,.xlsx" onChange={ereignis => dateiWählen(ereignis.target.files?.[0] ?? null)} /><span className="block"><Button type="button" variant="outline" onClick={ereignis => { const eingabe = ereignis.currentTarget.parentElement?.previousElementSibling; if (eingabe instanceof HTMLInputElement) eingabe.click(); }}>Datei auswählen</Button> <span className="text-slate-600">{datei?.name ?? 'Keine Datei ausgewählt'}{datei ? ` · ${(datei.size / 1024 / 1024).toLocaleString('de-DE', { maximumFractionDigits: 2 })} MiB` : ''}</span></span></label>
        <Button disabled={laden || !datei || !projekt} onClick={() => void vorschauLaden()}>{laden ? 'Vorschau wird geladen …' : 'Vorschau laden'}</Button></div>
      <section className="rounded-lg border border-slate-200 p-3"><Button type="button" variant="ghost" aria-expanded={spaltenZuordnungOffen} onClick={() => setzeSpaltenZuordnungOffen(wert => !wert)}>Spalten manuell zuordnen</Button>{spaltenZuordnungOffen && <><p className="mt-2 text-sm text-slate-600">Tragen Sie die Spaltennummer aus der Tabellenkopfzeile ein. Ohne Angaben erkennt HiCAD die üblichen deutschen Spaltenüberschriften selbst. Bei manueller Zuordnung müssen Sie alle benötigten Spalten einschließlich Menge angeben.</p><div className="mt-3 grid gap-3 sm:grid-cols-3">{[['interneReferenz','Interne Nummer'],['zeichnungsnummer','Zeichnungsnummer'],['zeichnungsrevision','Zeichnungsrevision'],['bezeichnung','Bezeichnung'],['werkstoff','Werkstoff'],['abmessung','Abmessung'],['menge','Menge'],['einheit','Einheit'],['stueckzahl','Stückzahl'],['einzelLaengeMm','Einzellänge mm'],['winkelLinks','Linker Winkel'],['winkelRechts','Rechter Winkel']].map(([feld, titel]) => <label key={feld} className="space-y-1 text-sm">{titel}<Input aria-label={`Spalte ${titel}`} inputMode="numeric" value={spalten[feld] ?? ''} onChange={ereignis => setzeSpalten(aktuell => ({ ...aktuell, [feld]: ereignis.target.value.replace(/[^0-9]/g, '') }))} placeholder="z. B. 1" /></label>)}</div></>}</section>
      {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
      {vorschau && <div className="space-y-3"><p className="rounded-md bg-slate-50 p-3 text-sm">{vorschau.zeilen.length} Zeilen gefunden. Vorschau {vorschau.dateiSchonImportiert ? 'wurde bereits importiert; vorhandene Teilmengen bleiben erhalten.' : 'prüfen und nur bestätigte Zeilen übernehmen.'}</p>
        {vorschau.dateiSchonImportiert && <label className="flex items-start gap-2 text-sm"><input type="checkbox" checked={duplikatBestätigt} onChange={ereignis => setzeDuplikatBestätigt(ereignis.target.checked)} />Erneuten Import bewusst zulassen</label>}
        <div className="space-y-3">{vorschau.zeilen.map(zeile => { const aktuell = fortschrittJeZeile.get(zeile.zeilennummer); return <article key={zeile.zeilennummer} className="grid min-w-0 gap-3 rounded-lg border border-slate-200 p-3 md:grid-cols-[auto_minmax(0,1fr)_9rem]"><input type="checkbox" aria-label={`Zeile ${zeile.zeilennummer} übernehmen`} checked={Boolean(auswahl[zeile.zeilennummer])} disabled={!aktuell || aktuell.verbleibendeMenge <= 0} onChange={ereignis => setzeAuswahl(wert => ({ ...wert, [zeile.zeilennummer]: ereignis.target.checked }))} />
          <div className="min-w-0 space-y-2"><h3 className="font-medium">Zeile {zeile.zeilennummer}: {zeile.vorschlag?.bezeichnung ?? zeile.rohtext}</h3><p className="break-words text-sm text-slate-600">{zeile.rohtext}</p><p className="text-sm text-slate-600">Artikel-Kandidaten: {zeile.artikelKandidaten.length ? 'Bitte passenden Artikel auswählen' : 'Kein passender Artikel gefunden'} · schon übernommen {aktuell?.uebernommeneMenge ?? 0}, noch offen {aktuell?.verbleibendeMenge ?? 0}</p>{<StammdatenAuswahl art="Artikel" value={artikel[zeile.zeilennummer] ?? null} onChange={wert => setzeArtikel(aktuell => wert ? { ...aktuell, [zeile.zeilennummer]: wert } : Object.fromEntries(Object.entries(aktuell).filter(([kennung]) => Number(kennung) !== zeile.zeilennummer)))} />}
          <Button type="button" variant="outline" size="sm" disabled={laden} onClick={() => setzeArtikelAnlageZeile(zeile.zeilennummer)}>Artikel neu anlegen</Button>
          {!artikel[zeile.zeilennummer] && zeile.vorschlag?.art === 'ZEICHNUNGSTEIL' && <div className="grid gap-2 sm:grid-cols-2">{([['interneReferenz', 'Projektkennung'], ['zeichnungsnummer', 'Zeichnungsnummer'], ['zeichnungsrevision', 'Zeichnungsrevision'], ['bezeichnung', 'Bezeichnung'], ['werkstoff', 'Werkstoff'], ['abmessung', 'Abmessung']] as const).map(([feld, titel]) => <label key={feld} className="text-sm">{titel}<Input aria-label={`${titel} Zeile ${zeile.zeilennummer}`} value={zeile.vorschlag?.[feld] ?? ''} onChange={ereignis => positionsfeldÄndern(zeile.zeilennummer, feld, ereignis.target.value)} /></label>)}</div>}
          <label className="block text-sm"><input hidden aria-label={`Technische Anlage Zeile ${zeile.zeilennummer}`} type="file" accept=".pdf,.dxf,.step,.stp,.png,.jpg,.jpeg" disabled={laden} onChange={ereignis => { void anlageErgänzen(zeile.zeilennummer, ereignis.target.files?.[0] ?? null); ereignis.target.value = ''; }} /><Button type="button" variant="outline" size="sm" disabled={laden || !aktuell || aktuell.verbleibendeMenge <= 0} onClick={ereignis => { const eingabe = ereignis.currentTarget.previousElementSibling; if (eingabe instanceof HTMLInputElement) eingabe.click(); }}>Technische Anlage ergänzen</Button></label>
          {zeile.bilder.length > 0 && <div className="flex flex-wrap gap-3">{zeile.bilder.map(bild => <label key={bild.dateiId} className="flex items-center gap-2 text-sm"><input type="checkbox" aria-label={`${bild.mimeTyp?.startsWith('image/') ? 'Bild' : 'Anlage'} freigeben: ${bild.dateiname}`} checked={(bilder[zeile.zeilennummer] ?? []).includes(bild.dateiId)} onChange={ereignis => setzeBilder(aktuell => ({ ...aktuell, [zeile.zeilennummer]: ereignis.target.checked ? [...(aktuell[zeile.zeilennummer] ?? []), bild.dateiId] : (aktuell[zeile.zeilennummer] ?? []).filter(id => id !== bild.dateiId) }))} />{bild.mimeTyp?.startsWith('image/') && toSafeResourceUrl(bild.url) && <img className="h-16 w-20 rounded border border-slate-200 object-contain" src={toSafeResourceUrl(bild.url) ?? undefined} alt={bild.dateiname} />}{bild.mimeTyp?.startsWith('image/') ? 'Bild' : 'Anlage'} freigeben: {bild.dateiname}{toSafeResourceUrl(bild.url) && <a className="font-medium text-rose-700 underline" href={toSafeResourceUrl(bild.url) ?? undefined} target="_blank" rel="noopener noreferrer">Anlage ansehen</a>}</label>)}</div>}
          {zeile.hinweise.map(hinweis => <p key={hinweis} className="text-sm text-amber-800">{hinweis}</p>)}</div>
          <label className="space-y-1 text-sm font-medium">Teilmenge<Input aria-label={`Menge Zeile ${zeile.zeilennummer}`} inputMode="decimal" value={mengen[zeile.zeilennummer] ?? ''} onChange={ereignis => setzeMengen(wert => ({ ...wert, [zeile.zeilennummer]: ereignis.target.value }))} /></label>
        </article>; })}</div>
      </div>}
    </div>
    <DialogFooter><Button variant="outline" disabled={laden} onClick={schließen}>Schließen</Button>{vorschau && <Button disabled={laden || (vorschau.dateiSchonImportiert && !duplikatBestätigt)} onClick={() => void übernehmen()}>{laden ? 'Wird übernommen …' : 'Ausgewählte Zeilen übernehmen'}</Button>}</DialogFooter>
    {artikelAnlageZeile !== null && <CreateArticleModal onClose={() => setzeArtikelAnlageZeile(null)} onSave={() => undefined} onCreated={neu => { setzeArtikel(aktuell => ({ ...aktuell, [artikelAnlageZeile]: { id: neu.id, name: neu.produktname } })); setzeArtikelAnlageZeile(null); }} />}
  </DialogContent></Dialog>;
}
