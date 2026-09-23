import { AlertTriangle, CheckCircle2, CircleDashed, XCircle } from 'lucide-react';
import type { VersandDto } from '../types';

export function VersandStatus({ versand, onKlaeren }: { versand: VersandDto; onKlaeren?: () => void }) {
  if (versand.status === 'ANGENOMMEN') return <div role="status" className="flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-emerald-900">
    <CheckCircle2 aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0" />
    <div><p className="font-semibold">Versand angenommen</p><p className="text-sm">Die Nachricht wurde vom Mailserver angenommen.</p>{versand.messageId && <p className="mt-1 break-all text-xs">Message-ID: {versand.messageId}</p>}</div>
  </div>;
  if (versand.status === 'UNKLAR') return <div role="alert" className="flex items-start gap-3 rounded-lg border border-amber-300 bg-amber-50 p-4 text-amber-950">
    <AlertTriangle aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0" />
    <div className="min-w-0 flex-1"><p className="font-semibold">Versandstatus unklar</p><p className="text-sm">Prüfe erst den Versandnachweis. Ein erneuter Versand könnte eine doppelte Nachricht auslösen.</p>{versand.fehlerCode && <p className="mt-1 text-xs">Hinweis: {versand.fehlerCode}</p>}</div>
    {onKlaeren && <button type="button" onClick={onKlaeren} className="shrink-0 rounded-md border border-amber-400 bg-white px-3 py-1.5 text-sm font-medium text-amber-950 hover:bg-amber-100 focus:outline-none focus:ring-2 focus:ring-rose-500">Versand klären</button>}
  </div>;
  if (versand.status === 'SICHER_FEHLGESCHLAGEN') return <div role="alert" className="flex items-start gap-3 rounded-lg border border-rose-200 bg-rose-50 p-4 text-rose-900">
    <XCircle aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0" /><div><p className="font-semibold">Versand sicher fehlgeschlagen</p><p className="text-sm">Die Nachricht wurde nicht angenommen. Prüfe die Ursache, bevor du einen neuen Versand vorbereitest.</p>{versand.fehlerCode && <p className="mt-1 text-xs">Hinweis: {versand.fehlerCode}</p>}</div>
  </div>;
  return <div role="status" className="flex items-start gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 text-slate-800"><CircleDashed aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0" /><div><p className="font-semibold">Versand wird verarbeitet</p><p className="text-sm">Der Status ist noch nicht abgeschlossen.</p></div></div>;
}
