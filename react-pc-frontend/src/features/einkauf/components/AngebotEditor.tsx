import { Select } from '../../../components/ui/select-custom';
import { useState } from 'react';
import { Button } from '../../../components/ui/button';
import { DatePicker } from '../../../components/ui/datepicker';
import { useToast } from '../../../components/ui/toast';
import { validateDecimalInput } from '../../../lib/numberInput';
import { einkaufApi } from '../api';
import type { AngebotErfassung, AngebotsPosition, Dokumentart } from '../types';

interface AnfragePosition { id: number; snapshot: { interneReferenz: string | null; bezeichnung: string | null; basis?: { menge: number | null; einheit: string | null } | null } }
interface Props { beteiligungId: number; anfrageRevisionId: number; positionen: AnfragePosition[]; angebotId?: number; angebotVersionId?: number; initial?: AngebotErfassung; onSaved: () => void }
const emptyPosition = (p: AnfragePosition): AngebotsPosition => ({ anfragePositionId: p.id, originalNummer: p.snapshot.interneReferenz, originalText: p.snapshot.bezeichnung, angeboten: p.snapshot.basis ? { menge: p.snapshot.basis.menge, einheit: p.snapshot.basis.einheit as never, stueckzahl: null, einzelLaengeMm: null, kgJeMeter: null, faktorQuelle: null } : null, mindestmenge: null, verpackungseinheit: null, liefertermin: null, abweichungen: [], zeugnisse: [], kosten: [] });
const docTypes: Dokumentart[] = ['ZEUGNIS_2_1', 'ZEUGNIS_2_2', 'ZEUGNIS_3_1', 'ZEUGNIS_3_2', 'LEISTUNGSERKLAERUNG', 'CE_NACHWEIS'];

export function AngebotEditor({ beteiligungId, anfrageRevisionId, positionen, angebotId, initial, onSaved }: Props) {
  const toast = useToast(); const [nummer, setNummer] = useState(initial?.angebotsnummer ?? ''); const [datum, setDatum] = useState(initial?.datum ?? ''); const [gueltigBis, setGueltigBis] = useState(initial?.gueltigBis ?? ''); const [preise, setPreise] = useState<Record<number, string>>(() => Object.fromEntries(positionen.map(p => { const price = initial?.positionen.find(row => row.anfragePositionId === p.id)?.kosten.find(cost => cost.art === 'MATERIAL')?.betrag; return [p.id, price == null ? '' : String(price).replace('.', ',')]; }))); const [abweichungen, setAbweichungen] = useState<Record<number, string>>(() => Object.fromEntries((initial?.positionen ?? []).map(row => [row.anfragePositionId ?? 0, row.abweichungen.join('\n')]))); const [zeugnis, setZeugnis] = useState<Record<number, Dokumentart | ''>>({}); const [busy, setBusy] = useState(false);
  const speichern = async () => {
    const mapped: AngebotsPosition[] = [];
    for (const position of positionen) {
      const parsed = validateDecimalInput(preise[position.id] ?? '', { label: `Angebotspreis ${position.snapshot.interneReferenz ?? position.id}`, required: true, min: 0 });
      if (!parsed.valid) { toast.error(parsed.message); return; }
      if (parsed.value == null) { toast.error(`Bitte einen Preis für ${position.snapshot.interneReferenz ?? position.id} eingeben.`); return; }
      const preis = parsed.value;
      const original = initial?.positionen.find(item => item.anfragePositionId === position.id) ?? emptyPosition(position);
      const art = zeugnis[position.id];
      const materialIndex = original.kosten.findIndex(cost => cost.art === 'MATERIAL');
      mapped.push({ ...original, anfragePositionId: position.id, abweichungen: (abweichungen[position.id] ?? '').split('\n').map(v => v.trim()).filter(Boolean), zeugnisse: art ? [{ art, status: 'LIEFERBAR', aufpreis: null }] : original.zeugnisse,
        kosten: materialIndex >= 0 ? original.kosten.map((cost, index) => index === materialIndex ? { ...cost, betrag: preis } : cost) : [...original.kosten, { schluessel: 'MANUELLER_PREIS', art: 'MATERIAL', betrag: preis, basis: position.snapshot.basis?.einheit === 'METER' ? 'M' : position.snapshot.basis?.einheit === 'KILOGRAMM' ? 'KG' : position.snapshot.basis?.einheit === 'TONNE' ? 'T' : 'STUECK', basisMenge: 1, prozentBasisSchluessel: null, enthalten: false, variabel: false, quelle: 'Manuell erfasst' }] });
    }
    const body: AngebotErfassung = { anfrageRevisionId, angebotsnummer: nummer.trim() || null, datum: datum || null, gueltigBis: gueltigBis || null, waehrung: 'EUR', positionen: mapped, kosten: initial?.kosten ?? [], zahlungsbedingungen: initial?.zahlungsbedingungen ?? null, skontoProzent: initial?.skontoProzent ?? null, skontoTage: initial?.skontoTage ?? null, emailId: initial?.emailId ?? null, originalDateiId: initial?.originalDateiId ?? null };
    setBusy(true);
    try { await einkaufApi.post(angebotId ? `/api/einkauf/angebote/${angebotId}/versionen` : `/api/einkauf/anfrage-lieferanten/${beteiligungId}/angebote`, body); toast.success('Angebot wurde gespeichert.'); onSaved(); }
    catch (error) { toast.error(error instanceof Error ? error.message : 'Angebot konnte nicht gespeichert werden.'); }
    finally { setBusy(false); }
  };
  return <section className="space-y-4 rounded-lg border border-slate-200 bg-white p-4"><header className="flex flex-wrap items-start justify-between gap-3"><div><h2 className="font-semibold">Angebot manuell erfassen</h2><p className="text-sm text-slate-600">Eingaben bleiben bearbeitbar, auch wenn eine automatische Analyse nicht verfügbar ist.</p></div><Button onClick={() => void speichern()} disabled={busy || positionen.length === 0}>{busy ? 'Speichert …' : 'Angebot speichern'}</Button></header>
    <div className="grid gap-3 sm:grid-cols-3"><label className="text-sm">Angebotsnummer<input aria-label="Angebotsnummer" className="mt-1 w-full rounded border border-slate-300 px-3 py-2" value={nummer} onChange={e => setNummer(e.target.value)} /></label><label className="text-sm">Angebotsdatum<DatePicker aria-label="Angebotsdatum" className="mt-1 w-full" value={datum} onChange={setDatum} /></label><label className="text-sm">Gültig bis<DatePicker aria-label="Gültig bis" className="mt-1 w-full" value={gueltigBis} onChange={setGueltigBis} /></label></div>
      <div className="space-y-3">{positionen.map(p => <article key={p.id} className="grid gap-3 rounded-md border border-slate-200 p-3 sm:grid-cols-2"><div><h3 className="font-medium">{p.snapshot.interneReferenz ?? `Position ${p.id}`} · {p.snapshot.bezeichnung}</h3><p className="text-sm text-slate-600">Angefragte Menge {p.snapshot.basis?.menge?.toLocaleString('de-DE') ?? 'offen'} {p.snapshot.basis?.einheit?.toLowerCase() ?? ''}</p><label className="mt-2 block text-sm">Angebotspreis {p.snapshot.interneReferenz ?? p.id}<input inputMode="decimal" className="mt-1 w-full rounded border border-slate-300 px-3 py-2" value={preise[p.id] ?? ''} onFocus={e => { if (e.currentTarget.value === '0' || e.currentTarget.value === '0,00') setPreise(s => ({ ...s, [p.id]: '' })); }} onChange={e => setPreise(s => ({ ...s, [p.id]: e.target.value }))} /></label></div><div><label className="block text-sm">Technische Abweichungen<textarea className="mt-1 w-full rounded border border-slate-300 p-2" value={abweichungen[p.id] ?? ''} onChange={e => setAbweichungen(s => ({ ...s, [p.id]: e.target.value }))} /></label><label className="mt-2 block text-sm">Zeugniszusage<Select aria-label={`Zeugniszusage ${p.snapshot.interneReferenz ?? p.id}`} value={zeugnis[p.id] ?? ''} onChange={value => setZeugnis(s => ({ ...s, [p.id]: value as Dokumentart | '' }))} options={[{ value: '', label: 'Nicht zugesagt' }, ...docTypes.map(type => ({ value: type, label: type === 'LEISTUNGSERKLAERUNG' ? 'Leistungserklärung' : type === 'CE_NACHWEIS' ? 'CE-Nachweis' : type.replace('ZEUGNIS_', 'Zeugnis ').replaceAll('_', '.') }))]} /></label></div></article>)}</div>

  </section>;
}
