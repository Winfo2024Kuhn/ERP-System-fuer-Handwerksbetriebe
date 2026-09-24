import { useState } from 'react';
import { Button } from '../../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { useToast } from '../../../components/ui/toast';
import { validateDecimalInput } from '../../../lib/numberInput';
import { DecimalInput } from '../../../components/ui/decimal-input';
import { einkaufApi } from '../api';
import DOMPurify from 'dompurify';
import { stripHtmlTags, unescapeHtmlEntities } from '../../../lib/htmlSanitizer';

interface Anteil { bedarfId: number; version: number; menge: number }
interface Vorschau { version: number; vorschauId: string; vorschauHash: string; subject: string; htmlBody: string; empfaenger: string; revisionId: number }
interface Props { bestellungId: number; version: number; anteile: Anteil[]; onClose: () => void; onSent: () => void }

export function StornoKlaerungDialog({ bestellungId, version, anteile, onClose, onSent }: Props) {
  const toast = useToast(); const [grund, setGrund] = useState(''); const [vorschau, setVorschau] = useState<Vorschau | null>(null); const [busy, setBusy] = useState(false); const [selected, setSelected] = useState<number[]>(() => anteile.map(item => item.bedarfId)); const [drafts, setDrafts] = useState<Record<number, string>>(() => Object.fromEntries(anteile.map(item => [item.bedarfId, String(item.menge).replace('.', ',')])));
  const melden = (error: unknown) => toast.error(error instanceof Error ? error.message : 'Stornoanfrage konnte nicht verarbeitet werden.');
  const laden = async () => { if (!grund.trim()) { toast.error('Bitte geben Sie einen Grund an.'); return; } if (!selected.length) { toast.error('Bitte mindestens einen Anteil auswählen.'); return; } const chosen: Anteil[] = []; for (const id of selected) { const original = anteile.find(item => item.bedarfId === id)!; const amount = validateDecimalInput(drafts[id] ?? '', { label: `Menge Bedarf ${id}`, required: true, min: 0 }); if (!amount.valid) { toast.error(amount.message); return; } if (!amount.value || amount.value > original.menge) { toast.error(`Die Stornomenge für Bedarf ${id} muss größer als 0 sein und darf ${original.menge.toLocaleString('de-DE')} nicht überschreiten.`); return; } chosen.push({ ...original, menge: amount.value }); } setBusy(true); try { setVorschau(await einkaufApi.post<Vorschau>(`/api/einkauf/bestellungen/${bestellungId}/storno-vorschau`, { version, anteile: chosen, grund: grund.trim() })); } catch (error) { melden(error); } finally { setBusy(false); } };
  const senden = async () => { if (!vorschau) return; setBusy(true); try { await einkaufApi.post(`/api/einkauf/bestellungen/${bestellungId}/storno-anfragen`, { version: vorschau.version, vorschauId: vorschau.vorschauId, vorschauHash: vorschau.vorschauHash, idempotenzKey: crypto.randomUUID() }); toast.success('Stornoanfrage wurde zur sicheren Verarbeitung übergeben.'); onSent(); } catch (error) { melden(error); } finally { setBusy(false); } };
  return <Dialog open onOpenChange={open => { if (!open) onClose(); }} className="w-[min(42rem,calc(100vw-2rem))]">
    <DialogHeader className="px-6 pt-6"><DialogTitle>Storno beim Lieferanten anfragen</DialogTitle><DialogDescription>Die Anfrage ändert keine Mengen. Sie werden erst frei, wenn die Stornierung bestätigt und belegt wurde.</DialogDescription></DialogHeader>
    <DialogContent className="space-y-4 overflow-y-auto px-6 py-4">
      <label className="block text-sm font-medium" htmlFor="storno-grund">Grund der Stornoanfrage</label><textarea id="storno-grund" className="min-h-24 w-full rounded-md border border-slate-300 p-3 text-sm focus:border-rose-600 focus:outline-none focus:ring-2 focus:ring-rose-200" value={grund} onChange={event => setGrund(event.target.value)} />
      <ul className="space-y-2 rounded-md bg-slate-50 p-3 text-sm">{anteile.map(a => <li key={a.bedarfId} className="flex flex-wrap items-center gap-2"><label className="flex items-center gap-2"><input type="checkbox" checked={selected.includes(a.bedarfId)} onChange={event => setSelected(current => event.target.checked ? [...current, a.bedarfId] : current.filter(id => id !== a.bedarfId))} />Bedarf {a.bedarfId}</label>{selected.includes(a.bedarfId) && <><DecimalInput aria-label={`Stornomenge Bedarf ${a.bedarfId}`} className="w-28" min={0} max={a.menge} value={drafts[a.bedarfId] ?? ''} onChange={value => setDrafts(current => ({ ...current, [a.bedarfId]: value }))} /><span className="text-slate-600">von {a.menge.toLocaleString('de-DE')}</span></>}</li>)}</ul>
      {!vorschau ? <Button onClick={() => void laden()} disabled={busy || !grund.trim()}>Vorschau erstellen</Button> : <section className="rounded-lg border border-slate-200 p-4"><h3 className="font-semibold">{vorschau.subject}</h3><p className="mt-1 break-all text-sm text-slate-600">Empfänger: {vorschau.empfaenger}</p><p className="mt-3 whitespace-pre-wrap text-sm text-slate-700">{unescapeHtmlEntities(stripHtmlTags(DOMPurify.sanitize(vorschau.htmlBody, { FORBID_TAGS: ['script', 'style'] }).replace(/<\/p>/gi, '\n'), ' '))}</p></section>}
      <p className="text-sm text-slate-600">Eine unklare Zustellung wird erst nach belegter Klärung erneut gesendet.</p>
    </DialogContent><DialogFooter className="border-t border-slate-200 px-6 py-4"><Button variant="outline" onClick={onClose}>Abbrechen</Button>{vorschau && <Button onClick={() => void senden()} disabled={busy}>Stornoanfrage senden</Button>}</DialogFooter>
  </Dialog>;
}
