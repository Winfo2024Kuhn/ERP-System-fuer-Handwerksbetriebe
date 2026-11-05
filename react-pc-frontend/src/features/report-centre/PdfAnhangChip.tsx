import { AlertCircle, FileText, Loader2 } from 'lucide-react';
import { formatBytes } from '../../lib/dateiGroesse';
import type { PdfAnhangStand } from './usePdfAnhang';

/**
 * Anhang-Chip im Mail-Dialog: zeigt, dass das PDF erstellt wird, fertig ist
 * (Klick = Vorschau in neuem Tab) oder nicht erstellt werden konnte.
 */
export function PdfAnhangChip({ stand }: { stand: PdfAnhangStand }) {
    if (stand.art === 'laedt') {
        return (
            <span role="status" className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-600">
                <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" /> PDF wird erstellt …
            </span>
        );
    }
    if (stand.art === 'fehler') {
        return (
            <span role="alert" className="inline-flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                <AlertCircle aria-hidden="true" className="h-4 w-4 shrink-0" />
                PDF konnte nicht erstellt werden: {stand.meldung}
            </span>
        );
    }
    return (
        <button
            type="button"
            onClick={() => window.open(stand.url, '_blank', 'noopener')}
            title="PDF in neuem Tab ansehen"
            aria-label={`Anhang ${stand.pdf.dateiname} ansehen`}
            className="inline-flex max-w-full items-center gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800 transition-colors hover:bg-rose-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
        >
            <FileText aria-hidden="true" className="h-4 w-4 shrink-0 text-rose-600" />
            <span className="truncate font-medium">{stand.pdf.dateiname}</span>
            <span className="shrink-0 text-rose-600/80">{formatBytes(stand.pdf.blob.size)}</span>
        </button>
    );
}
