import { useEffect, useMemo, useState } from 'react';
import { Button } from '../../../components/ui/button';
import { DatePicker } from '../../../components/ui/datepicker';
import { DecimalInput } from '../../../components/ui/decimal-input';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { Input } from '../../../components/ui/input';
import { useToast } from '../../../components/ui/toast';
import { heuteIso, parseIsoDatum } from '../../../lib/datum';
import { validateDecimalInput } from '../../../lib/numberInput';
import { einkaufApi } from '../api';
import type { BestellungMitNachweisen, Bestellmenge, Lieferung } from '../bestellTypes';
import { BelegAuswahl } from './BelegAuswahl';
import type { BestellBelegDatei } from '../belegApi';

interface ZeileEntwurf { menge: string; charge: string; schmelznummer: string }
interface Props { bestellungId: number; onClose: () => void; onSaved: () => void }

export function LieferungDialog({ bestellungId, onClose, onSaved }: Props) {
  const toast = useToast();
  const [bestellung, setBestellung] = useState<BestellungMitNachweisen | null>(null);
  const [mengen, setMengen] = useState<Bestellmenge[]>([]);
  const [lieferungen, setLieferungen] = useState<Lieferung[]>([]);
  const [zeilen, setZeilen] = useState<Record<number, ZeileEntwurf>>({});
  const [datum, setDatum] = useState(heuteIso);
  const [lieferschein, setLieferschein] = useState<BestellBelegDatei | null>(null);
  const [fehler, setFehler] = useState('');
  const [laedt, setLaedt] = useState(true);
  const [speichert, setSpeichert] = useState(false);

  useEffect(() => {
    let aktiv = true;
    Promise.all([
      einkaufApi.get<BestellungMitNachweisen>(`/api/einkauf/bestellungen/${bestellungId}`),
      einkaufApi.get<Bestellmenge[]>(`/api/einkauf/bestellungen/${bestellungId}/mengen`),
      einkaufApi.get<Lieferung[]>(`/api/einkauf/bestellungen/${bestellungId}/lieferungen`),
    ]).then(([order, orderMengen, batches]) => {
      if (!aktiv) return;
      setBestellung(order); setMengen(orderMengen); setLieferungen(batches);
      const current = [...order.revisionen].filter(r => r.angenommenAm && !r.verworfen)
        .sort((a, b) => b.nummer - a.nummer)[0];
      setZeilen(Object.fromEntries((current?.positionen ?? []).map(p => [p.id, { menge: '', charge: '', schmelznummer: '' }])));
    }).catch(error => {
      if (!aktiv) return;
      const message = error instanceof Error ? error.message : 'Bestellung konnte nicht geladen werden.';
      setFehler(message); toast.error(message);
    }).finally(() => { if (aktiv) setLaedt(false); });
    return () => { aktiv = false; };
  }, [bestellungId, toast]);

  const revision = useMemo(() => bestellung?.revisionen.filter(r => r.angenommenAm && !r.verworfen)
    .sort((a, b) => b.nummer - a.nummer)[0] ?? null, [bestellung]);
  const geliefertJePosition = useMemo(() => {
    const sums = new Map<number, number>();
    lieferungen.forEach(batch => batch.positionen.forEach(line => sums.set(line.bestellPositionId, (sums.get(line.bestellPositionId) ?? 0) + line.menge)));
    return sums;
  }, [lieferungen]);
  const openByNeed = useMemo(() => new Map(mengen.map(m => [m.bedarfId, Math.max(0, m.offen)])), [mengen]);

  const update = (id: number, field: keyof ZeileEntwurf, value: string) => setZeilen(current => {
    const previous = current[id] ?? { menge: '', charge: '', schmelznummer: '' };
    return { ...current, [id]: { ...previous, [field]: value } };
  });

  const speichern = async () => {
    if (!bestellung || !revision) { setFehler('Es gibt keine angenommene Bestellfassung für den Wareneingang.'); return; }
    if (!parseIsoDatum(datum)) {
      const message = 'Bitte wählen Sie ein gültiges Eingangsdatum aus.';
      setFehler(message); toast.error(message); return;
    }
    const positions: Array<{ bestellPositionId: number; menge: number; charge: string | null; schmelznummer: string | null; projektAnteile: Array<{ bedarfId: number; version: number; menge: number }> }> = [];
    const needRemaining = new Map(openByNeed);
    for (const line of revision.positionen) {
      const draft = zeilen[line.id] ?? { menge: '', charge: '', schmelznummer: '' };
      if (!draft.menge.trim()) continue;
      const parsed = validateDecimalInput(draft.menge, { label: 'Liefermenge', min: 0.000001, required: true });
      if (!parsed.valid || parsed.value === null) { setFehler('Bitte geben Sie eine gültige Liefermenge ein.'); return; }
      const remainingOnLine = Math.max(0, line.menge - (geliefertJePosition.get(line.id) ?? 0));
      if (parsed.value > remainingOnLine) { setFehler(`Die Liefermenge für Position ${line.id} übersteigt den noch offenen Bestellanteil.`); return; }
      let left = parsed.value;
      const projectParts = [];
      for (const source of line.herkuenfte) {
        if (left <= 0 || source.bedarfId == null) continue;
        const sourceOpen = needRemaining.get(source.bedarfId) ?? 0;
        const allocated = Math.min(left, sourceOpen, source.menge ?? 0);
        if (allocated > 0) {
          projectParts.push({ bedarfId: source.bedarfId, version: source.version, menge: allocated });
          needRemaining.set(source.bedarfId, sourceOpen - allocated);
          left -= allocated;
        }
      }
      if (left > 0.000001) { setFehler('Die Liefermenge überschreitet den offenen Bedarf dieser Bestellung.'); return; }
      positions.push({ bestellPositionId: line.id, menge: parsed.value, charge: draft.charge.trim() || null, schmelznummer: draft.schmelznummer.trim() || null, projektAnteile: projectParts });
    }
    if (!positions.length) { setFehler('Bitte geben Sie für mindestens eine Position eine Liefermenge ein.'); return; }

    setFehler(''); setSpeichert(true);
    try {
      await einkaufApi.post(`/api/einkauf/bestellungen/${bestellungId}/lieferungen`, {
        version: bestellung.version,
        lieferscheinId: lieferschein?.lieferantDokumentId ?? null,
        eingang: new Date(`${datum}T12:00:00`).toISOString(),
        positionen: positions,
        idempotenzKey: crypto.randomUUID(),
      });
      toast.success('Lieferung erfasst.');
      onSaved(); onClose();
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Lieferung konnte nicht erfasst werden.';
      setFehler(message); toast.error(message);
    } finally { setSpeichert(false); }
  };

  return <Dialog open onOpenChange={(open) => { if (!open && !speichert) onClose(); }} className="w-full max-w-3xl overflow-y-auto">
    <DialogContent>
      <DialogHeader><DialogTitle>Lieferung erfassen</DialogTitle><DialogDescription>Jede Teillieferung und Charge bleibt einzeln nachvollziehbar. Zeugnisse werden danach separat zugeordnet und geprüft.</DialogDescription></DialogHeader>
      {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
      {laedt ? <p role="status">Bestellung wird geladen …</p> : !revision ? <p className="rounded-md bg-amber-50 p-3 text-sm text-amber-900">Für diese Bestellung liegt keine angenommene Fassung vor.</p> : <div className="min-h-0 space-y-4 overflow-y-auto pr-1">
        <p className="text-sm text-slate-700">Bestellung {bestellung?.nummer} · angenommene Fassung {revision.nummer}</p>
        <label className="block text-sm font-medium text-slate-700">Eingangsdatum
          <DatePicker aria-label="Eingangsdatum" value={datum} onChange={setDatum} required disabled={speichert} />
        </label>
        <BelegAuswahl bestellungId={bestellungId} typ="LIEFERSCHEIN" value={lieferschein} onChange={setLieferschein} />
        {revision.positionen.map(line => {
          const remaining = Math.max(0, line.menge - (geliefertJePosition.get(line.id) ?? 0));
          return <fieldset key={line.id} className="grid gap-3 rounded-lg border border-slate-200 p-3 sm:grid-cols-3">
            <legend className="px-1 text-sm font-semibold text-slate-800">{line.snapshot.bezeichnung ?? `Position ${line.id}`} · noch {remaining.toLocaleString('de-DE')} offen</legend>
            <DecimalInput label={`Menge (${line.snapshot.basis?.einheit ?? 'Einheit'})`} value={zeilen[line.id]?.menge ?? ''} onChange={value => update(line.id, 'menge', value)} min={0} max={remaining} />
            <label className="text-sm font-medium text-slate-700">Charge<Input aria-label="Charge" value={zeilen[line.id]?.charge ?? ''} onChange={event => update(line.id, 'charge', event.target.value)} maxLength={100} /></label>
            <label className="text-sm font-medium text-slate-700">Schmelznummer<Input aria-label="Schmelznummer" value={zeilen[line.id]?.schmelznummer ?? ''} onChange={event => update(line.id, 'schmelznummer', event.target.value)} maxLength={100} /></label>
          </fieldset>;
        })}
        {revision.positionen.length === 0 && <p className="text-sm text-slate-600">Die angenommene Fassung enthält keine Positionen.</p>}
      </div>}
      <DialogFooter>
        <Button type="button" variant="outline" onClick={onClose} disabled={speichert}>Abbrechen</Button>
        <Button type="button" onClick={() => void speichern()} disabled={laedt || speichert || !revision}>Lieferung erfassen</Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>;
}
