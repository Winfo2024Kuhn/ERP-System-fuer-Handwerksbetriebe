import { useState } from 'react';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { Button } from '../../../components/ui/button';
import { Input } from '../../../components/ui/input';
import { useToast } from '../../../components/ui/toast';
import { einkaufApi } from '../api';

export interface LagerentnahmeBedarf { id: number; version: number; bezeichnung: string; einheit: string; offen: number }
interface EntnahmeErgebnis { id: number; bewertungOffen: boolean; offenerBedarf: number }
const lesen = (value: string) => {
  const normalized = value.trim().replace(/\s/g, '').replace(',', '.');
  if (!/^(?:\d+)(?:\.\d{1,6})?$/.test(normalized)) return null;
  const parsed = Number(normalized);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
};

export function LagerentnahmeDialog({ bedarf, onClose, onBestaetigt }: {
  bedarf: LagerentnahmeBedarf; onClose: () => void; onBestaetigt: (offen: number) => void;
}) {
  const toast = useToast();
  const [menge, setMenge] = useState('');
  const [preis, setPreis] = useState('');
  const [quelle, setQuelle] = useState('');
  const [fehler, setFehler] = useState('');
  const [laden, setLaden] = useState(false);
  const bestaetigen = async () => {
    const amount = lesen(menge);
    const unitPrice = preis.trim() ? lesen(preis) : null;
    if (!amount || amount > bedarf.offen) { const meldung = `Bitte eine Menge zwischen 0 und ${bedarf.offen.toLocaleString('de-DE')} ${bedarf.einheit} eingeben.`; setFehler(meldung); toast.error(meldung); return; }
    if (preis.trim() && !unitPrice) { const meldung = 'Bitte einen gültigen Preis größer als 0 eingeben.'; setFehler(meldung); toast.error(meldung); return; }
    if (unitPrice && !quelle.trim()) { const meldung = 'Bitte die Quelle für den Entnahmepreis eintragen.'; setFehler(meldung); toast.error(meldung); return; }
    setLaden(true); setFehler('');
    try {
      const response = await einkaufApi.post<EntnahmeErgebnis>('/api/einkauf/lagerentnahmen', {
        anteil: { bedarfId: bedarf.id, version: bedarf.version, menge: amount },
        preisJeEinheit: unitPrice, preisQuelle: unitPrice ? quelle.trim() : null,
        entnommenAm: new Date().toISOString(), idempotenzKey: crypto.randomUUID(),
      });
      onBestaetigt(response.offenerBedarf);
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Lagerentnahme konnte nicht gespeichert werden.';
      setFehler(message); toast.error(message);
    } finally { setLaden(false); }
  };
  return <Dialog open onOpenChange={open => { if (!open && !laden) onClose(); }}><DialogContent>
    <DialogHeader><DialogTitle>Lagerentnahme bewerten</DialogTitle></DialogHeader>
    <p className="text-sm text-slate-600">{bedarf.bezeichnung} · noch offen {bedarf.offen.toLocaleString('de-DE')} {bedarf.einheit}. Die Entnahme wird erst nach Bestätigung gebucht.</p>
    <div className="grid gap-3 sm:grid-cols-2">
      <label className="space-y-1 text-sm font-medium">Entnommene Menge<Input aria-label="Entnommene Menge" inputMode="decimal" value={menge} onChange={event => setMenge(event.target.value)} placeholder={`Menge in ${bedarf.einheit}`} /></label>
      <label className="space-y-1 text-sm font-medium">Preis je Einheit (optional)<Input aria-label="Preis je Einheit" inputMode="decimal" value={preis} onChange={event => setPreis(event.target.value)} placeholder="Später bewerten" /></label>
      <label className="space-y-1 text-sm font-medium sm:col-span-2">Preisquelle (bei Bewertung)<Input aria-label="Preisquelle" value={quelle} onChange={event => setQuelle(event.target.value)} maxLength={240} placeholder="z. B. Lieferschein oder Rechnung" /></label>
    </div>
    {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
    <DialogFooter><Button variant="outline" disabled={laden} onClick={onClose}>Abbrechen</Button><Button disabled={laden} onClick={() => void bestaetigen()}>{laden ? 'Wird gebucht …' : 'Entnahme bestätigen'}</Button></DialogFooter>
  </DialogContent></Dialog>;
}
