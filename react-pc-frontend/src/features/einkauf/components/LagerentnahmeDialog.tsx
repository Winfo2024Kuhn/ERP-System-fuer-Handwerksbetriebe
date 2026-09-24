import { useState } from 'react';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { useToast } from '../../../components/ui/toast';
import { einheitenAnzeige } from '../einheiten';
import type { BedarfResponse } from '../types';
import { einkaufApi, EinkaufApiError } from '../api';

export interface LagerentnahmeBedarf { id: number; version: number; bezeichnung: string; einheit: string; offen: number }
interface EntnahmeErgebnis { id: number; bewertungOffen: boolean; offenerBedarf: number }
const lesen = (wert: string) => {
  const normalisiert = wert.trim().replace(/\s/g, '').replace(',', '.');
  if (!/^(?:\d+)(?:\.\d{1,6})?$/.test(normalisiert)) return null;
  const gelesen = Number(normalisiert);
  return Number.isFinite(gelesen) && gelesen > 0 ? gelesen : null;
};

export function LagerentnahmeDialog({ bedarf, schließen, bestätigt }: {
  bedarf: LagerentnahmeBedarf; schließen: () => void; bestätigt: (offen: number) => void;
}) {
  const meldungen = useToast();
  const [bedarfsstand, setzeBedarfsstand] = useState(bedarf);
  const [neuerStand, setzeNeuenStand] = useState<LagerentnahmeBedarf | null>(null);
  const [menge, setzeMenge] = useState('');
  const [preis, setzePreis] = useState('');
  const [quelle, setzeQuelle] = useState('');
  const [fehler, setzeFehler] = useState('');
  const [laden, setzeLaden] = useState(false);
  const bestätigen = async () => {
    const mengeWert = lesen(menge);
    const einzelpreis = preis.trim() ? lesen(preis) : null;
    if (!mengeWert || mengeWert > bedarfsstand.offen) { const meldung = `Bitte eine Menge zwischen 0 und ${bedarfsstand.offen.toLocaleString('de-DE')} ${einheitenAnzeige(bedarfsstand.einheit)} eingeben.`; setzeFehler(meldung); meldungen.error(meldung); return; }
    if (preis.trim() && !einzelpreis) { const meldung = 'Bitte einen gültigen Preis größer als 0 eingeben.'; setzeFehler(meldung); meldungen.error(meldung); return; }
    if (einzelpreis && !quelle.trim()) { const meldung = 'Bitte die Quelle für den Entnahmepreis eintragen.'; setzeFehler(meldung); meldungen.error(meldung); return; }
    setzeLaden(true); setzeFehler('');
    try {
      const antwort = await einkaufApi.post<EntnahmeErgebnis>('/api/einkauf/lagerentnahmen', {
        anteil: { bedarfId: bedarf.id, version: bedarfsstand.version, menge: mengeWert },
        preisJeEinheit: einzelpreis, preisQuelle: einzelpreis ? quelle.trim() : null,
        entnommenAm: new Date().toISOString(), idempotenzKey: crypto.randomUUID(),
      });
      bestätigt(antwort.offenerBedarf);
    } catch (fehlerursache) {
      const meldung = fehlerursache instanceof Error ? fehlerursache.message : 'Lagerentnahme konnte nicht gespeichert werden.';
      setzeFehler(meldung); meldungen.error(meldung);
      if (fehlerursache instanceof EinkaufApiError && fehlerursache.status === 409) {
        try {
          const aktuell = await einkaufApi.get<BedarfResponse>(`/api/einkauf/bedarf/${bedarf.id}`);
          setzeNeuenStand({ id: aktuell.id, version: aktuell.version, bezeichnung: aktuell.position.bezeichnung ?? 'Materialbedarf', einheit: aktuell.position.basis?.einheit ?? bedarfsstand.einheit, offen: aktuell.mengen.disponierbar ?? 0 });
        } catch (ladefehler) { meldungen.error(ladefehler instanceof Error ? ladefehler.message : 'Aktueller Bedarf konnte nicht geladen werden.'); }
      }
    } finally { setzeLaden(false); }
  };
  return <Dialog open onOpenChange={offen => { if (!offen && !laden) schließen(); }}><DialogContent>
    <DialogHeader><DialogTitle>Lagerentnahme bewerten</DialogTitle></DialogHeader>
    <p className="text-sm text-slate-600">{bedarfsstand.bezeichnung} · noch offen {bedarfsstand.offen.toLocaleString('de-DE')} {einheitenAnzeige(bedarfsstand.einheit)}. Die Entnahme wird erst nach Bestätigung gebucht.</p>
    <div className="grid gap-3 sm:grid-cols-2">
      <label className="space-y-1 text-sm font-medium">Entnommene Menge<Input aria-label="Entnommene Menge" inputMode="decimal" value={menge} onChange={ereignis => setzeMenge(ereignis.target.value)} placeholder={`Menge in ${einheitenAnzeige(bedarfsstand.einheit)}`} /></label>
      <label className="space-y-1 text-sm font-medium">Preis je Einheit (optional)<Input aria-label="Preis je Einheit" inputMode="decimal" value={preis} onChange={ereignis => setzePreis(ereignis.target.value)} placeholder="Später bewerten" /></label>
      <label className="space-y-1 text-sm font-medium sm:col-span-2">Preisquelle (bei Bewertung)<Input aria-label="Preisquelle" value={quelle} onChange={ereignis => setzeQuelle(ereignis.target.value)} maxLength={240} placeholder="z. B. Lieferschein oder Rechnung" /></label>
    </div>
    {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
    {neuerStand && <section className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm"><p>Geänderter Bedarf: {neuerStand.bezeichnung} · noch offen {neuerStand.offen.toLocaleString('de-DE')} {einheitenAnzeige(neuerStand.einheit)}. Prüfen Sie Menge und Preis vor einer erneuten Bestätigung.</p><Button type="button" variant="outline" className="mt-2" onClick={() => { setzeBedarfsstand(neuerStand); setzeNeuenStand(null); setzeFehler(''); }}>Aktuellen Stand übernehmen</Button></section>}
    <DialogFooter><Button variant="outline" disabled={laden} onClick={schließen}>Abbrechen</Button><Button disabled={laden || neuerStand !== null} onClick={() => void bestätigen()}>{laden ? 'Wird gebucht …' : 'Entnahme bestätigen'}</Button></DialogFooter>
  </DialogContent></Dialog>;
}
