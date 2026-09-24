import { useState } from 'react';
import DOMPurify from 'dompurify';
import { Button } from '../../../components/ui/button';
import { stripHtmlTags, unescapeHtmlEntities } from '../../../lib/htmlSanitizer';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
import { useToast } from '../../../components/ui/toast';
import type { EinkaufAnlage } from '../types';
export type VersandVorschau = { version: number; vorschauHash: string; subject: string; htmlBody: string; empfaenger: string;
  pdfDateiId: number | null; anlageVersionIds: number[]; revisionsNummer?: number | null; eigeneKundennummer?: string | null;
  antwortfrist?: string | null; liefertermin?: string | null; anlagen?: EinkaufAnlage[] };
const datum = (value?: string | null) => value ? new Date(`${value}T00:00:00`).toLocaleDateString('de-DE') : 'Nicht angegeben';
export function VersandVorschauDialog({ vorschau, onFreigeben, onSchliessen }: { vorschau: VersandVorschau; onFreigeben: (hash: string) => Promise<void>; onSchliessen: () => void }) {
  const toast = useToast(); const [laedt, setLaedt] = useState(false); const [fehler, setFehler] = useState('');
  const vollstaendig = Boolean(vorschau.revisionsNummer && vorschau.pdfDateiId && vorschau.empfaenger &&
    vorschau.anlageVersionIds.every(id => vorschau.anlagen?.some(a => a.id === id && a.freigegeben)));
  const freigeben = async () => {
    if (!vollstaendig) return;
    setLaedt(true); setFehler('');
    try { await onFreigeben(vorschau.vorschauHash); }
    catch (error) { const message = error instanceof Error ? error.message : 'Anfrage konnte nicht freigegeben werden.'; setFehler(message); toast.error(message); }
    finally { setLaedt(false); }
  };
  return <Dialog open onOpenChange={open => { if (!open && !laedt) onSchliessen(); }} className="max-w-3xl"><DialogContent className="max-h-[90vh] overflow-y-auto">
    <DialogHeader><DialogTitle>Versand prüfen und freigeben · Fassung {vorschau.revisionsNummer ?? 'unbekannt'}</DialogTitle></DialogHeader>
    <dl className="grid gap-3 rounded-lg bg-slate-50 p-4 text-sm sm:grid-cols-2">
      <div><dt className="font-semibold">Empfänger</dt><dd className="break-all">{vorschau.empfaenger}</dd></div>
      <div><dt className="font-semibold">Unsere Kundennummer</dt><dd>{vorschau.eigeneKundennummer || 'Nicht hinterlegt'}</dd></div>
      <div><dt className="font-semibold">Antwortfrist</dt><dd>{datum(vorschau.antwortfrist)}</dd></div>
      <div><dt className="font-semibold">Liefertermin</dt><dd>{datum(vorschau.liefertermin)}</dd></div>
      <div className="sm:col-span-2"><dt className="font-semibold">Betreff</dt><dd>{vorschau.subject}</dd></div>
      <div className="sm:col-span-2"><dt className="font-semibold">Nachricht</dt><dd className="whitespace-pre-wrap">{unescapeHtmlEntities(stripHtmlTags(DOMPurify.sanitize(vorschau.htmlBody, { FORBID_TAGS: ['script', 'style'] }).replace(/<\/p>/gi, '\n')))}</dd></div>
      <div className="sm:col-span-2"><dt className="font-semibold">PDF und Anlagen</dt><dd>
        {vorschau.pdfDateiId && <a className="text-rose-700 underline" target="_blank" rel="noreferrer" href={`/api/einkauf/pdf-vorschau/${vorschau.pdfDateiId}`}>Anfrage-PDF dieser Fassung öffnen</a>}
        <ul>{(vorschau.anlagen ?? []).map(a => <li key={a.id}>{a.dateiname} · Revision {a.revision} · {a.freigegeben ? 'Freigegeben' : 'Nicht freigegeben'}</li>)}</ul>
        {!vorschau.anlageVersionIds.length && <p>Keine zusätzlichen Anlagen.</p>}
      </dd></div>
    </dl>
    {!vollstaendig && <p role="alert" className="text-sm text-rose-700">Fassung, Anfrage-PDF oder freigegebene Anlagenangaben fehlen. Bitte Vorschau neu laden.</p>}
    {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
    <DialogFooter><Button variant="outline" disabled={laedt} onClick={onSchliessen}>Zurück</Button><Button disabled={laedt || !vollstaendig} title={!vollstaendig ? 'Die Versandangaben müssen vollständig prüfbar sein.' : undefined} onClick={() => void freigeben()}>{laedt ? 'Wird freigegeben …' : 'Anfrage freigeben und senden'}</Button></DialogFooter>
  </DialogContent></Dialog>;
}
