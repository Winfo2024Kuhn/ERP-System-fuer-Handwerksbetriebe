import { useState } from 'react';
import { Button } from '../../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { Select } from '../../../components/ui/select-custom';
import { useToast } from '../../../components/ui/toast';
import { validateDecimalInput } from '../../../lib/numberInput';
import { StammdatenAuswahl, type StammdatenWahl } from './StammdatenAuswahl';
import { einkaufApi } from '../api';

interface Props { angebotVersionId: number; angebotPositionId: number; artikelId: number; lieferantId: number; preis: number; waehrung: string; einheit: string; gueltigBis: string | null; onClose: () => void; onSaved: () => void }
export function PreisUebernahmeDialog(props: Props) {
  const toast = useToast(); const [scope, setScope] = useState('STANDARD'); const [projekt, setProjekt] = useState<StammdatenWahl | null>(null); const [abMenge, setAbMenge] = useState(''); const [bisMenge, setBisMenge] = useState(''); const [grund, setGrund] = useState(''); const [busy, setBusy] = useState(false);
  const speichern = async () => {
    if (!grund.trim()) { toast.error('Bitte bestätigen Sie kurz, warum dieser Preis übernommen wird.'); return; }
    if (scope === 'PROJEKT' && !projekt) { toast.error('Bitte wählen Sie ein Projekt aus.'); return; }
    const from = scope === 'MENGENSTAFFEL' ? validateDecimalInput(abMenge, { label: 'Ab-Menge', required: true, min: 0 }) : null;
    if (from && !from.valid) { toast.error(from.message); return; }
    const to = scope === 'MENGENSTAFFEL' ? validateDecimalInput(bisMenge, { label: 'Bis-Menge', min: 0 }) : null;
    if (to && !to.valid) { toast.error(to.message); return; }
    if (from?.valid && to?.valid && to.value != null && to.value <= (from.value ?? 0)) { toast.error('Bis-Menge muss größer als Ab-Menge sein.'); return; }
    setBusy(true);
    try { await einkaufApi.post(`/api/einkauf/angebote/${props.angebotVersionId}/positionen/${props.angebotPositionId}/preis-uebernehmen`, { scope, projektId: scope === 'PROJEKT' ? projekt?.id : null, abMenge: from?.valid ? from.value : null, bisMenge: to?.valid ? to.value : null, begruendung: grund.trim(), idempotenzKey: crypto.randomUUID() }); toast.success('Preis wurde in der Preishistorie gespeichert. Es wurde keine Bestellung ausgelöst.'); props.onSaved(); }
    catch (error) { toast.error(error instanceof Error ? error.message : 'Preis konnte nicht übernommen werden.'); }
    finally { setBusy(false); }
  };
  return <Dialog open onOpenChange={open => { if (!open) props.onClose(); }} className="w-[min(38rem,calc(100vw-2rem))]"><DialogHeader className="px-6 pt-6"><DialogTitle>Geprüften Artikelpreis übernehmen</DialogTitle><DialogDescription>Diese Aktion aktualisiert nur die Lieferanten-Preishistorie. Sie erstellt keine Bestellung und bucht keine Kosten.</DialogDescription></DialogHeader><DialogContent className="space-y-4 px-6 py-4"><div className="rounded-md bg-slate-50 p-3 text-sm">Artikel {props.artikelId} · Lieferant {props.lieferantId}<p className="mt-1 font-semibold">{props.preis.toLocaleString('de-DE', { minimumFractionDigits: 2 })} {props.waehrung} / {props.einheit}</p><p className="text-slate-600">Angebotsgültigkeit bis {props.gueltigBis ? new Date(`${props.gueltigBis}T00:00:00`).toLocaleDateString('de-DE') : 'nicht angegeben'}</p></div><label className="block text-sm">Geltungsbereich<Select aria-label="Geltungsbereich" value={scope} options={[{ value: 'STANDARD', label: 'Standardpreis' }, { value: 'PROJEKT', label: 'Nur für ein Projekt' }, { value: 'MENGENSTAFFEL', label: 'Mengenstaffel' }]} onChange={setScope} /></label>{scope === 'PROJEKT' && <StammdatenAuswahl art="Projekt" value={projekt} onChange={setProjekt} />}{scope === 'MENGENSTAFFEL' && <div className="grid gap-3 sm:grid-cols-2"><label className="text-sm">Ab-Menge<input inputMode="decimal" className="mt-1 w-full rounded border border-slate-300 px-3 py-2" value={abMenge} onChange={e => setAbMenge(e.target.value)} /></label><label className="text-sm">Bis-Menge<input inputMode="decimal" className="mt-1 w-full rounded border border-slate-300 px-3 py-2" value={bisMenge} onChange={e => setBisMenge(e.target.value)} /></label></div>}<label className="block text-sm">Begründung<input aria-label="Begründung" className="mt-1 w-full rounded border border-slate-300 px-3 py-2" value={grund} onChange={e => setGrund(e.target.value)} /></label></DialogContent><DialogFooter className="border-t border-slate-200 px-6 py-4"><Button variant="outline" onClick={props.onClose}>Abbrechen</Button><Button disabled={busy} onClick={() => void speichern()}>{busy ? 'Speichert …' : 'Preis übernehmen'}</Button></DialogFooter></Dialog>;
}
