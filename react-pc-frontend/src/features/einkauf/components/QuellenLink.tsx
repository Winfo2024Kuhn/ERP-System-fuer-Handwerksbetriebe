import { toSafeResourceUrl, stripHtmlTags } from '../../../lib/htmlSanitizer';
import type { Quelle } from '../types';

export interface QuellenLinkQuelle extends Quelle {
  url?: string | null;
}

export function QuellenLink({ quelle }: { quelle: QuellenLinkQuelle }) {
  const label = stripHtmlTags(quelle.bezeichnung?.trim() || quelle.typ);
  const safeUrl = toSafeResourceUrl(quelle.url);
  const betrag = quelle.betrag === null ? null : new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' }).format(quelle.betrag);
  return <span className="inline-flex min-w-0 flex-wrap items-center gap-2 text-sm">
    {safeUrl ? <a href={safeUrl} target="_blank" rel="noreferrer" className="font-medium text-rose-700 underline decoration-rose-300 underline-offset-2 hover:text-rose-800 focus:outline-none focus:ring-2 focus:ring-rose-500">{label}</a> : <span className="font-medium text-slate-800">{label}</span>}
    {quelle.id !== null && <span className="text-xs text-slate-500">{quelle.typ} {quelle.id}</span>}
    {betrag && <span className="text-slate-700">{betrag}</span>}
  </span>;
}
