import { useEffect } from "react";
import { Download, ExternalLink, X, FileText } from "lucide-react";
import { Button } from "./ui/button";
import { PdfCanvasViewer } from "./ui/PdfCanvasViewer";
import { isPdfUrl } from "../lib/pdfUrl";

export interface PreviewDoc {
    url: string;
    title: string;
}

/**
 * Globaler, universeller Dokumenten-Vorschau-Modal für PDFs.
 *
 * Dies ist DIE Pflicht-Komponente für einfache PDF-Vorschauen (siehe FRONTEND_UI.md).
 * Sie kapselt den großen Modal-Rahmen, Header mit Download/Neuer-Tab/Schließen und
 * den canvas-basierten {@link PdfCanvasViewer} (inkl. Zoom: Buttons + Strg-Mausrad).
 *
 * Für alle einfachen Stellen, an denen nur ein PDF angezeigt werden soll (kein
 * Bild-Navigations- oder Editor-Beiwerk), diese Komponente verwenden statt einen
 * eigenen Modal nachzubauen.
 *
 * Rendering läuft über PdfCanvasViewer (fetch → Canvas), nicht über ein iframe –
 * dadurch keine X-Frame-Options-Probleme und keine schwarzen Ränder.
 */
export default function DocumentPreviewModal({ doc, onClose, isPdf: isPdfOverride }: { doc: PreviewDoc; onClose: () => void; isPdf?: boolean }) {
    // Wenn der Aufrufer den Dateityp kennt (z.B. aus dateityp), kann er die URL-Heuristik
    // explizit überstimmen – verhindert, dass DOCX/XLSX unter /dokumente/ fälschlich als PDF gerendert werden.
    const isPdf = isPdfOverride ?? isPdfUrl(doc.url);

    // Escape-Taste schließt Modal
    useEffect(() => {
        const handleEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
        window.addEventListener('keydown', handleEsc);
        return () => window.removeEventListener('keydown', handleEsc);
    }, [onClose]);

    const handleDownload = () => {
        const link = document.createElement('a');
        link.href = doc.url;
        // Titel sind für Menschen gedacht ("Projekte in Arbeit") – für die Datei brauchen
        // wir eine Endung, sonst landet sie ohne Dateityp im Download-Ordner.
        link.download = isPdf && !/\.pdf$/i.test(doc.title) ? `${doc.title}.pdf` : doc.title;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    return (
        <div
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm"
            onClick={onClose}
        >
            <div
                className="relative bg-white rounded-2xl shadow-2xl w-[calc(100vw-2cm)] h-[calc(100vh-2cm)] overflow-hidden flex flex-col"
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
                    <h3 className="font-semibold text-slate-900 truncate pr-4">{doc.title}</h3>
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" size="sm" onClick={handleDownload}
                            className="text-slate-500 hover:text-slate-700" title="Herunterladen">
                            <Download className="w-4 h-4 mr-1" />
                            Herunterladen
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => window.open(doc.url, '_blank', 'noopener,noreferrer')}
                            className="text-slate-500 hover:text-slate-700" title="In neuem Tab öffnen">
                            <ExternalLink className="w-4 h-4 mr-1" />
                            Neuer Tab
                        </Button>
                        <Button variant="ghost" size="sm" onClick={onClose}
                            className="text-slate-500 hover:text-slate-700">
                            <X className="w-5 h-5" />
                        </Button>
                    </div>
                </div>

                {/* Content */}
                <div className="flex-1 min-h-0 overflow-hidden bg-slate-100">
                    {isPdf ? (
                        <PdfCanvasViewer
                            url={doc.url}
                            className="w-full h-full overflow-y-auto"
                        />
                    ) : (
                        <div className="flex flex-col items-center justify-center h-full py-12">
                            <FileText className="w-24 h-24 text-slate-300 mb-6" />
                            <p className="text-slate-600 text-lg font-medium">{doc.title}</p>
                            <p className="text-slate-400 mt-2">Vorschau nicht verfügbar</p>
                            <Button variant="outline" size="sm" onClick={handleDownload} className="mt-4">
                                <Download className="w-4 h-4 mr-2" />
                                Herunterladen
                            </Button>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
