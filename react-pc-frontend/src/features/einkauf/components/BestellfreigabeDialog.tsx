import { useState } from 'react';
import { ExternalLink, FileText, ShieldCheck } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import DocumentPreviewModal from '../../../components/DocumentPreviewModal';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { useToast } from '../../../components/ui/toast';
import { toSafeResourceUrl } from '../../../lib/htmlSanitizer';
import { einkaufApi } from '../api';
import type { VersandDto } from '../types';
import type { BestellVorschau } from '../bestellTypes';

export function BestellfreigabeDialog({ bestellungId, onClose, onReleased }: { bestellungId: number; onClose: () => void; onReleased: (versand: VersandDto) => void }) {
  const toast = useToast(); const [vorschau, setVorschau] = useState<BestellVorschau | null>(null); const [busy, setBusy] = useState(false); const [geprueft, setGeprueft] = useState(false); const [pdfOpen, setPdfOpen] = useState(false);
  const melden = (error: unknown) => toast.error(error instanceof Error ? error.message : 'Bestellvorschau konnte nicht erstellt werden.');
  const laden = async () => {
    setBusy(true);
    try {
      const templates = await einkaufApi.get<Array<{ id: number; dokumentTyp: string; aktiv: boolean; standard: boolean }>>('/api/email-textvorlagen');
      const template = templates.find(item => item.dokumentTyp === 'EINKAUF_BESTELLUNG' && item.aktiv && item.standard)
        ?? templates.find(item => item.dokumentTyp === 'EINKAUF_BESTELLUNG' && item.aktiv);
      if (!template) throw new Error('Bitte zuerst eine aktive Bestellvorlage einrichten.');
      const preview = await einkaufApi.post<BestellVorschau>(`/api/einkauf/bestellungen/${bestellungId}/vorschau?templateId=${template.id}`, {});
      setVorschau(preview); setGeprueft(false);
    } catch (error) { melden(error); } finally { setBusy(false); }
  };
  const freigeben = async () => {
    if (!vorschau || !geprueft) return;
    setBusy(true);
    try {
      const versand = await einkaufApi.post<VersandDto>(`/api/einkauf/bestellungen/${bestellungId}/freigeben`, { version: vorschau.version, vorschauHash: vorschau.vorschauHash, idempotenzKey: crypto.randomUUID() });
      toast.success('Bestellung wurde zur Freigabe übergeben. Der Versandstatus wird aktualisiert.'); onReleased(versand);
    } catch (error) { melden(error); } finally { setBusy(false); }
  };
  const pdfUrl = vorschau?.pdfDateiId ? toSafeResourceUrl(`/api/einkauf/pdf-vorschau/${vorschau.pdfDateiId}`) : null;
  return <Dialog open onOpenChange={open => { if (!open) { if (pdfOpen) setPdfOpen(false); else onClose(); } }} className="w-[min(46rem,calc(100vw-2rem))]">
    <DialogHeader className="px-6 pt-6"><DialogTitle>Bestellung prüfen und freigeben</DialogTitle><DialogDescription>Kontrollieren Sie Fassung, Empfänger, Liefertermin und Anlagen. Der Versand startet erst nach Ihrer Freigabe.</DialogDescription></DialogHeader>
    <DialogContent className="overflow-y-auto px-6 py-4">
      {!vorschau ? <div className="flex justify-center py-5"><Button onClick={() => void laden()} disabled={busy}>{busy ? 'Vorschau wird erstellt …' : 'Vorschau laden'}</Button></div> : <div className="space-y-4">
        <section className="rounded-lg border border-slate-200 bg-slate-50 p-4"><p className="text-xs font-semibold uppercase tracking-wide text-rose-600">Fassung {vorschau.revisionsNummer ?? '–'}</p><h3 className="mt-1 font-semibold">{vorschau.subject}</h3><p className="mt-2 break-all text-sm text-slate-700">Empfänger: {vorschau.empfaenger}</p><p className="mt-1 text-sm text-slate-700">Eigene Kundennummer: {vorschau.eigeneKundennummer ?? 'Nicht angegeben'}</p><p className="mt-1 text-sm text-slate-700">Liefertermin: {vorschau.liefertermin ? new Date(`${vorschau.liefertermin}T00:00:00`).toLocaleDateString('de-DE') : 'Nicht angegeben'}</p><p className="mt-1 text-sm text-slate-700">Bestätigungsfrist: {vorschau.antwortfrist ? new Date(`${vorschau.antwortfrist}T00:00:00`).toLocaleDateString('de-DE') : 'Nicht angegeben'}</p>
          <p className="mt-3 text-sm text-slate-600">Nachrichtentext und unveränderlicher Bestellsnapshot sind für diese Vorschau zusammen vorbereitet.</p>
          {vorschau.anlagen.length > 0 && <ul className="mt-2 space-y-1 text-sm">{vorschau.anlagen.map((anlage, index) => <li key={anlage.id ?? index}><FileText className="mr-1 inline h-4 w-4" />{anlage.dateiname} · Revision {anlage.revision} · {anlage.freigegeben ? 'Freigegeben' : 'Noch nicht freigegeben'}</li>)}</ul>}
          {pdfUrl && <Button variant="outline" size="sm" className="mt-3" onClick={() => setPdfOpen(true)}><ExternalLink className="mr-2 h-4 w-4" />PDF ansehen</Button>}
        </section>
        <label className="flex items-start gap-3 rounded-lg border border-slate-200 p-3 text-sm"><input type="checkbox" checked={geprueft} onChange={event => setGeprueft(event.target.checked)} className="mt-0.5 h-4 w-4 rounded border-slate-300 text-rose-600 focus:ring-rose-500" /><span>PDF und Empfänger geprüft. Ich gebe genau diese Bestellfassung zum Versand frei.</span></label>
        <p className="flex items-start gap-2 text-sm text-slate-600"><ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-rose-600" />Eine spätere Änderung benötigt eine neue Bestellfassung und eine neue Freigabe.</p>
      </div>}
    </DialogContent>
    <DialogFooter className="border-t border-slate-200 px-6 py-4"><Button variant="outline" onClick={onClose}>Abbrechen</Button><Button disabled={!vorschau || !geprueft || busy} onClick={() => void freigeben()}>{busy ? 'Wird freigegeben …' : 'Bestellung freigeben'}</Button></DialogFooter>
    {pdfOpen && pdfUrl && <DocumentPreviewModal isPdf doc={{ url: pdfUrl, title: vorschau?.subject ?? 'Bestellung.pdf' }} onClose={() => setPdfOpen(false)} />}
  </Dialog>;
}
