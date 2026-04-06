import { useState, useEffect } from "react";
import { Download, ExternalLink, X, FileText } from "lucide-react";
import { Button } from "./ui/button";

export interface PreviewDoc {
    url: string;
    title: string;
}

/**
 * PDF-Erkennung: greift alle üblichen Muster ab
 * (Dateiendung, API-Pfade, Download-Endpoints)
 */
function isPdfUrl(url: string): boolean {
    const lower = url.toLowerCase();
    return lower.includes('.pdf') ||
        lower.includes('/dokumente/') ||
        lower.includes('/attachments/') ||
        lower.includes('/download') ||
        lower.endsWith('/pdf');
}

/**
 * Universeller Dokumenten-Vorschau-Modal.
 *
 * WICHTIG – Warum Blob-URL statt direktem iframe-src?
 * Externe Dokument-URLs (z.B. Tailscale, S3) setzen `X-Frame-Options: deny`,
 * was den iframe im Browser blockiert. Die Lösung: das PDF wird über fetch()
 * als Blob geladen (läuft über den Backend-Proxy oder direkt als API-Call),
 * und die resultierende `blob://`-URL ist always same-origin → kein Problem.
 *
 * REGEL: Niemals eine externe URL direkt als iframe-src verwenden.
 * Diese Komponente immer für PDF-Vorschauen nutzen.
 */
export default function DocumentPreviewModal({ doc, onClose }: { doc: PreviewDoc; onClose: () => void }) {
    const [blobUrl, setBlobUrl] = useState<string | null>(null);
    const [loadError, setLoadError] = useState(false);
    const isPdf = isPdfUrl(doc.url);

    // Escape-Taste schließt Modal
    useEffect(() => {
        const handleEsc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
        window.addEventListener('keydown', handleEsc);
        return () => window.removeEventListener('keydown', handleEsc);
    }, [onClose]);

    // PDF als Blob laden → X-Frame-Options wird umgangen
    useEffect(() => {
        if (!isPdf) return;
        const controller = new AbortController();
        let currentUrl: string | null = null;
        let didCancel = false;

        fetch(doc.url, { signal: controller.signal, credentials: 'same-origin' })
            .then(res => {
                if (!res.ok) {
                    throw new Error('Fetch failed');
                }
                return res.blob();
            })
            .then(blob => {
                currentUrl = URL.createObjectURL(blob);
                setBlobUrl(currentUrl);
            })
            .catch(() => {
                if (!controller.signal.aborted && !didCancel) {
                    setLoadError(true);
                }
            });

        return () => {
            didCancel = true;
            controller.abort();
            if (currentUrl) URL.revokeObjectURL(currentUrl);
            setBlobUrl(null);
            setLoadError(false);
        };
    }, [doc.url, isPdf]);

    // Download über Original-URL (nicht Blob – funktioniert ohne X-Frame-Options)
    const handleDownload = () => {
        const link = document.createElement('a');
        link.href = blobUrl || doc.url;
        link.download = doc.title;
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
                className="relative bg-white rounded-2xl shadow-2xl w-full max-w-5xl mx-4 max-h-[90vh] overflow-hidden flex flex-col"
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div className="flex items-center justify-between px-6 py-4 border-b border-slate-200">
                    <h3 className="font-semibold text-slate-900 truncate">{doc.title}</h3>
                    <div className="flex items-center gap-2">
                        <Button variant="ghost" size="sm" onClick={handleDownload}
                            className="text-slate-500 hover:text-slate-700" title="Herunterladen">
                            <Download className="w-4 h-4" />
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => window.open(blobUrl || doc.url, '_blank')}
                            className="text-slate-500 hover:text-slate-700" title="In neuem Tab öffnen">
                            <ExternalLink className="w-4 h-4" />
                        </Button>
                        <Button variant="ghost" size="sm" onClick={onClose}
                            className="text-slate-500 hover:text-slate-700">
                            <X className="w-5 h-5" />
                        </Button>
                    </div>
                </div>

                {/* Content */}
                <div className="flex-1 overflow-auto bg-slate-100 min-h-[500px]">
                    {isPdf ? (
                        loadError ? (
                            <div className="flex flex-col items-center justify-center py-12">
                                <FileText className="w-24 h-24 text-slate-300 mb-6" />
                                <p className="text-slate-600 text-lg font-medium">PDF konnte nicht geladen werden</p>
                                <Button variant="outline" size="sm" onClick={() => window.open(doc.url, '_blank')} className="mt-4">
                                    <ExternalLink className="w-4 h-4 mr-2" />
                                    In neuem Tab öffnen
                                </Button>
                            </div>
                        ) : blobUrl ? (
                            <iframe src={blobUrl} className="w-full h-full min-h-[600px]" title={doc.title} />
                        ) : (
                            <div className="flex items-center justify-center h-full min-h-[500px] text-slate-500">
                                <div className="animate-spin w-5 h-5 border-2 border-rose-500 border-t-transparent rounded-full mr-3" />
                                PDF wird geladen...
                            </div>
                        )
                    ) : (
                        <div className="flex flex-col items-center justify-center py-12">
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
