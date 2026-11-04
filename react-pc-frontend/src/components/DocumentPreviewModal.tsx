import { useId } from "react";
import { createPortal } from "react-dom";
import { Download, ExternalLink, X, FileText } from "lucide-react";
import { Button } from "./ui/button";
import { PdfCanvasViewer } from "./ui/PdfCanvasViewer";
import { isPdfUrl } from "../lib/pdfUrl";
import { toSafeResourceUrl } from "../lib/htmlSanitizer";
import { useVorschauFokus } from "../hooks/useVorschauFokus";

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
 *
 * Eigene modale Ebene über jedem Dialog (`role="dialog"`, Name = Titel): Fokus auf
 * „Schließen“, Tab bleibt drin, Escape schließt nur die Vorschau, danach Fokus zurück an
 * den Opener (`useVorschauFokus`).
 */
export default function DocumentPreviewModal({ doc, onClose, isPdf: isPdfOverride }: { doc: PreviewDoc; onClose: () => void; isPdf?: boolean }) {
    // Wenn der Aufrufer den Dateityp kennt (z.B. aus dateityp), kann er die URL-Heuristik
    // explizit überstimmen – verhindert, dass DOCX/XLSX unter /dokumente/ fälschlich als PDF gerendert werden.
    const isPdf = isPdfOverride ?? isPdfUrl(doc.url);
    // Einmal prüfen, dann überall dieselbe Adresse: Vorschau, Herunterladen, neuer Tab.
    // Leer bei unsicherer Adresse (javascript:, data:, //…): dann keine Knöpfe ins Leere.
    const sichereUrl = toSafeResourceUrl(doc.url);

    const titelId = useId();
    const { fensterRef, schliessenRef, zIndex } = useVorschauFokus({ offen: true, onClose });

    const handleDownload = () => {
        if (!sichereUrl) return;
        const link = document.createElement('a');
        link.href = sichereUrl;
        // Titel sind für Menschen gedacht ("Projekte in Arbeit") – für die Datei brauchen
        // wir eine Endung, sonst landet sie ohne Dateityp im Download-Ordner.
        link.download = isPdf && !/\.pdf$/i.test(doc.title) ? `${doc.title}.pdf` : doc.title;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
    };

    return createPortal(
        <div
            ref={fensterRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby={titelId}
            tabIndex={-1}
            style={{ zIndex }}
            className="fixed inset-0 flex items-center justify-center bg-black/70 backdrop-blur-sm focus:outline-none"
            onClick={onClose}
        >
            <div
                className="relative bg-white rounded-2xl shadow-2xl w-[calc(100vw-2cm)] h-[calc(100vh-2cm)] overflow-hidden flex flex-col"
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
                    <h3 id={titelId} className="font-semibold text-slate-900 truncate pr-4">{doc.title}</h3>
                    <div className="flex items-center gap-2">
                        {sichereUrl && (
                            <>
                                <Button variant="ghost" size="sm" onClick={handleDownload}
                                    className="text-slate-500 hover:text-slate-700" title="Herunterladen">
                                    <Download aria-hidden="true" className="w-4 h-4 mr-1" />
                                    Herunterladen
                                </Button>
                                <Button variant="ghost" size="sm" onClick={() => window.open(sichereUrl, '_blank', 'noopener,noreferrer')}
                                    className="text-slate-500 hover:text-slate-700" title="In neuem Tab öffnen">
                                    <ExternalLink aria-hidden="true" className="w-4 h-4 mr-1" />
                                    Neuer Tab
                                </Button>
                            </>
                        )}
                        <Button ref={schliessenRef} variant="ghost" size="sm" onClick={onClose}
                            className="text-slate-500 hover:text-slate-700"
                            aria-label="Vorschau schließen" title="Vorschau schließen">
                            <X aria-hidden="true" className="w-5 h-5" />
                        </Button>
                    </div>
                </div>

                {/* Content */}
                <div className="flex-1 min-h-0 overflow-hidden bg-slate-100">
                    {isPdf && sichereUrl ? (
                        <PdfCanvasViewer
                            url={sichereUrl}
                            className="w-full h-full overflow-y-auto"
                        />
                    ) : (
                        <div className="flex flex-col items-center justify-center h-full py-12">
                            <FileText aria-hidden="true" className="w-24 h-24 text-slate-300 mb-6" />
                            <p className="text-slate-600 text-lg font-medium">{doc.title}</p>
                            <p className="text-slate-500 mt-2">Vorschau nicht verfügbar</p>
                            {sichereUrl && (
                                <Button variant="outline" size="sm" onClick={handleDownload} className="mt-4">
                                    <Download aria-hidden="true" className="w-4 h-4 mr-2" />
                                    Herunterladen
                                </Button>
                            )}
                        </div>
                    )}
                </div>
            </div>
        </div>,
        document.body,
    );
}
