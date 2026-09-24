import { useState } from 'react';
import DOMPurify from 'dompurify';
import { Button } from '../../../components/ui/button';
import { stripHtmlTags } from '../../../lib/htmlSanitizer';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '../../../components/ui/dialog';
export type VersandVorschau = { version: number; vorschauHash: string; subject: string; htmlBody: string; empfaenger: string; pdfDateiId: number | null; anlageVersionIds: number[] };
export function VersandVorschauDialog({ vorschau, onFreigeben, onSchliessen }: { vorschau: VersandVorschau; onFreigeben: (hash: string) => Promise<void>; onSchliessen: () => void }) {
 const [laedt, setLaedt] = useState(false); const [fehler, setFehler] = useState('');
 const freigeben = async () => { setLaedt(true); setFehler(''); try { await onFreigeben(vorschau.vorschauHash); } catch (e) { setFehler(e instanceof Error ? e.message : 'Anfrage konnte nicht freigegeben werden.'); } finally { setLaedt(false); } };
 return <Dialog open onOpenChange={open => { if (!open) onSchliessen(); }}><DialogContent className="max-h-[90vh] overflow-y-auto"><DialogHeader><DialogTitle>Versand prüfen und freigeben</DialogTitle></DialogHeader><dl className="space-y-3 rounded-lg bg-slate-50 p-4 text-sm"><div><dt className="font-semibold">Empfänger</dt><dd>{vorschau.empfaenger}</dd></div><div><dt className="font-semibold">Betreff</dt><dd>{vorschau.subject}</dd></div><div><dt className="font-semibold">Nachricht</dt><dd className="whitespace-pre-wrap">{stripHtmlTags(DOMPurify.sanitize(vorschau.htmlBody, { ALLOWED_TAGS: ['p', 'br'], FORBID_TAGS: ['script', 'style'] }))}</dd></div><div><dt className="font-semibold">Anlagen</dt><dd>{vorschau.anlageVersionIds.length} freigegebene Zeichnungs-/Dokumentversion(en)</dd></div><p>Diese Vorschau ist an Revision {vorschau.version} gebunden.</p></dl>{fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}<DialogFooter><Button variant="outline" onClick={onSchliessen}>Zurück</Button><Button disabled={laedt} onClick={() => void freigeben()}>{laedt ? 'Wird freigegeben …' : 'Anfrage freigeben und senden'}</Button></DialogFooter></DialogContent></Dialog>;
}
