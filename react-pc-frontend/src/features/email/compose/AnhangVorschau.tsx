import { useMemo } from 'react';
import { X } from 'lucide-react';
import { Button } from '../../../components/ui/button';
import { PdfCanvasViewer } from '../../../components/ui/PdfCanvasViewer';
import { toSafeResourceUrl } from '../../../lib/htmlSanitizer';
import type { AnhangVorschauStand } from './useAnhangVorschau';

/** PDF- und Bildvorschau der Anhänge als Vollbild über dem Formular. */
export function AnhangVorschau({ vorschau }: { vorschau: AnhangVorschauStand }) {
    const { pdfUrl, bild } = vorschau;
    const sichereBildUrl = useMemo(() => {
        if (!bild?.url) return '';
        const sicher = toSafeResourceUrl(bild.url);
        return sicher.startsWith('blob:') || sicher.startsWith('https://') || sicher.startsWith('/') ? sicher : '';
    }, [bild]);

    return (
        <>
            {pdfUrl && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
                    <div className="bg-white rounded-xl shadow-2xl w-[90%] max-w-4xl h-[85vh] flex flex-col">
                        <div className="flex justify-between items-center px-4 py-3 border-b border-slate-200">
                            <h3 className="font-semibold text-slate-900">Dokument-Vorschau</h3>
                            <Button variant="ghost" size="sm" onClick={vorschau.schliessePdf} aria-label="Vorschau schließen">
                                <X className="w-5 h-5" />
                            </Button>
                        </div>
                        <div className="flex-1 p-4 bg-slate-50 overflow-hidden">
                            <PdfCanvasViewer url={pdfUrl} className="w-full h-full rounded overflow-y-auto overflow-x-hidden" />
                        </div>
                    </div>
                </div>
            )}
            {bild && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-6">
                    <div className="flex h-[85vh] w-full max-w-5xl flex-col overflow-hidden rounded-xl bg-white shadow-2xl">
                        <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
                            <h3 className="truncate pr-4 font-semibold text-slate-900">{bild.name}</h3>
                            <Button type="button" variant="ghost" size="sm" onClick={vorschau.schliesseBild} aria-label="Bildvorschau schließen">
                                <X className="h-5 w-5" />
                            </Button>
                        </div>
                        <div className="flex flex-1 items-center justify-center overflow-hidden bg-slate-100 p-4">
                            {sichereBildUrl && (
                                <img src={sichereBildUrl} alt={bild.name} className="max-h-full max-w-full rounded object-contain shadow" />
                            )}
                        </div>
                    </div>
                </div>
            )}
        </>
    );
}
