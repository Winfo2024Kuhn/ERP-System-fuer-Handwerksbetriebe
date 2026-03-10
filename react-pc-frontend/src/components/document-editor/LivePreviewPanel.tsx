import { useCallback, useRef, useEffect, useState } from 'react';
import { RefreshCw, Download } from 'lucide-react';
import { cn } from '../../lib/utils';

/* eslint-disable @typescript-eslint/no-explicit-any */

interface LivePreviewPanelProps {
    previewUrl: string | null;
    loading: boolean;
    stale: boolean;
    onRefresh: () => void;
    isOpen: boolean;
}

/**
 * Prüft ob PDF.js (v3 UMD) als window.pdfjsLib verfügbar ist.
 */
function getPdfjsLib(): any | null {
    const lib = (window as any).pdfjsLib;
    if (lib && typeof lib.getDocument === 'function') return lib;
    return null;
}

/**
 * Canvas-basierte PDF-Vorschau.
 * - Keine schwarzen Ränder (kein Browser-PDF-Viewer)
 * - PDF füllt die Breite des Containers aus
 * - Scroll-Position bleibt beim Aktualisieren erhalten
 * - HiDPI-Support für scharfe Darstellung
 * - Fallback auf iframe falls PDF.js nicht geladen ist
 */
export function LivePreviewPanel({ previewUrl, loading, stale, onRefresh, isOpen }: LivePreviewPanelProps) {
    const scrollContainerRef = useRef<HTMLDivElement>(null);
    const canvasContainerRef = useRef<HTMLDivElement>(null);
    const pdfDocRef = useRef<any>(null);
    const renderingRef = useRef(false);
    const scrollFractionRef = useRef(0);
    const prevUrlRef = useRef<string | null>(null);
    const [pageCount, setPageCount] = useState(0);
    const [useFallback, setUseFallback] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const handleDownload = useCallback(() => {
        if (!previewUrl) return;
        const a = document.createElement('a');
        a.href = previewUrl;
        a.download = 'vorschau.pdf';
        a.click();
    }, [previewUrl]);

    /**
     * Rendert alle Seiten des PDFs als Canvas-Elemente.
     */
    const renderPdf = useCallback(async (url: string) => {
        const pdfjsLib = getPdfjsLib();
        if (!pdfjsLib) {
            console.warn('PDF.js nicht verfügbar – verwende iframe Fallback');
            setUseFallback(true);
            return;
        }

        if (!canvasContainerRef.current || !scrollContainerRef.current) return;
        if (renderingRef.current) return;
        renderingRef.current = true;
        setError(null);

        // Scroll-Position als Fraktion merken (0–1)
        const scrollEl = scrollContainerRef.current;
        if (prevUrlRef.current && scrollEl.scrollHeight > scrollEl.clientHeight) {
            scrollFractionRef.current = scrollEl.scrollTop / (scrollEl.scrollHeight - scrollEl.clientHeight);
        }
        prevUrlRef.current = url;

        try {
            // PDF-Daten laden
            const response = await fetch(url);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const data = await response.arrayBuffer();

            // Altes Dokument aufräumen
            if (pdfDocRef.current) {
                try { pdfDocRef.current.destroy(); } catch { /* ok */ }
            }

            const loadingTask = pdfjsLib.getDocument({ data });
            const doc = await loadingTask.promise;
            pdfDocRef.current = doc;
            setPageCount(doc.numPages);

            const container = canvasContainerRef.current;
            if (!container) { renderingRef.current = false; return; }

            // Container-Breite
            const containerWidth = container.clientWidth;
            if (containerWidth <= 0) { renderingRef.current = false; return; }

            // Alte Canvases entfernen
            container.innerHTML = '';

            // Seiten nacheinander rendern
            for (let i = 1; i <= doc.numPages; i++) {
                const page = await doc.getPage(i);
                const defaultViewport = page.getViewport({ scale: 1 });

                // Scale: PDF-Seite auf Container-Breite skalieren
                const scale = containerWidth / defaultViewport.width;
                const viewport = page.getViewport({ scale });

                const canvas = document.createElement('canvas');
                const ctx = canvas.getContext('2d');
                if (!ctx) continue;

                // HiDPI für scharfe Darstellung
                const dpr = window.devicePixelRatio || 1;
                canvas.width = Math.floor(viewport.width * dpr);
                canvas.height = Math.floor(viewport.height * dpr);
                canvas.style.width = `${Math.floor(viewport.width)}px`;
                canvas.style.height = `${Math.floor(viewport.height)}px`;
                canvas.style.display = 'block';
                ctx.scale(dpr, dpr);

                // Seiten-Wrapper
                const wrapper = document.createElement('div');
                wrapper.style.background = 'white';
                wrapper.style.lineHeight = '0';
                if (i < doc.numPages) {
                    wrapper.style.marginBottom = '2px';
                }
                wrapper.appendChild(canvas);
                container.appendChild(wrapper);

                // Rendern
                await page.render({ canvasContext: ctx, viewport }).promise;
            }

            // Scroll-Position wiederherstellen
            requestAnimationFrame(() => {
                if (scrollEl && scrollEl.scrollHeight > scrollEl.clientHeight && scrollFractionRef.current > 0) {
                    const maxScroll = scrollEl.scrollHeight - scrollEl.clientHeight;
                    scrollEl.scrollTop = Math.round(scrollFractionRef.current * maxScroll);
                }
            });

        } catch (err) {
            console.error('PDF Render-Fehler:', err);
            setError('PDF konnte nicht gerendert werden');
            // Fallback auf iframe
            setUseFallback(true);
        } finally {
            renderingRef.current = false;
        }
    }, []);

    // PDF rendern wenn sich die URL ändert
    useEffect(() => {
        if (!previewUrl) {
            if (canvasContainerRef.current) canvasContainerRef.current.innerHTML = '';
            setPageCount(0);
            if (pdfDocRef.current) {
                try { pdfDocRef.current.destroy(); } catch { /* ok */ }
                pdfDocRef.current = null;
            }
            return;
        }

        if (useFallback) return; // Im Fallback-Modus: iframe kümmert sich

        renderPdf(previewUrl);
    }, [previewUrl, renderPdf, useFallback]);

    // Resize-Handler
    useEffect(() => {
        if (useFallback) return;
        let timer: ReturnType<typeof setTimeout>;
        const onResize = () => {
            clearTimeout(timer);
            timer = setTimeout(() => {
                if (previewUrl) renderPdf(previewUrl);
            }, 300);
        };
        window.addEventListener('resize', onResize);
        return () => { clearTimeout(timer); window.removeEventListener('resize', onResize); };
    }, [previewUrl, renderPdf, useFallback]);

    // Cleanup
    useEffect(() => {
        return () => {
            if (pdfDocRef.current) {
                try { pdfDocRef.current.destroy(); } catch { /* ok */ }
                pdfDocRef.current = null;
            }
        };
    }, []);

    // Animation: keep mounted during exit transition
    const [shouldRender, setShouldRender] = useState(isOpen);
    const [animationState, setAnimationState] = useState<'entering' | 'visible' | 'exiting' | 'hidden'>(isOpen ? 'entering' : 'hidden');

    useEffect(() => {
        if (isOpen) {
            setShouldRender(true);
            // Start entering on next frame so the initial styles apply first
            requestAnimationFrame(() => {
                requestAnimationFrame(() => setAnimationState('visible'));
            });
            setAnimationState('entering');
        } else {
            setAnimationState('exiting');
            const timer = setTimeout(() => {
                setShouldRender(false);
                setAnimationState('hidden');
            }, 500); // Match transition duration
            return () => clearTimeout(timer);
        }
    }, [isOpen]);

    // Re-render PDF after open animation completes so canvas has correct width
    useEffect(() => {
        if (animationState === 'visible' && previewUrl && !useFallback) {
            const timer = setTimeout(() => renderPdf(previewUrl), 520);
            return () => clearTimeout(timer);
        }
    }, [animationState, previewUrl, renderPdf, useFallback]);

    if (!shouldRender) return null;

    return (
        <div
            className="border-l border-slate-200 bg-white flex flex-col overflow-hidden flex-shrink-0"
            style={{
                width: animationState === 'visible' ? '45%' : '0%',
                minWidth: animationState === 'visible' ? 340 : 0,
                opacity: animationState === 'visible' ? 1 : 0,
                transition: 'width 500ms cubic-bezier(0.4, 0, 0.2, 1), min-width 500ms cubic-bezier(0.4, 0, 0.2, 1), opacity 400ms ease',
            }}
        >            {/* Header */}
            <div className="flex items-center justify-between px-4 h-10 border-b border-slate-100 bg-white flex-shrink-0">
                <div className="flex items-center gap-2">
                    <span className="text-xs font-semibold text-slate-500 tracking-wide">Vorschau</span>
                    {pageCount > 0 && !useFallback && (
                        <span className="text-[10px] text-slate-400">
                            {pageCount} {pageCount === 1 ? 'Seite' : 'Seiten'}
                        </span>
                    )}
                    {stale && !loading && (
                        <span className="w-1.5 h-1.5 bg-amber-400 rounded-full animate-pulse" title="Veraltet" />
                    )}
                </div>
                <div className="flex items-center gap-0.5">
                    <button onClick={handleDownload} disabled={!previewUrl} className="p-1.5 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-40" title="Herunterladen">
                        <Download className="w-3.5 h-3.5 text-slate-400" />
                    </button>
                    <button onClick={onRefresh} disabled={loading} className="p-1.5 hover:bg-slate-100 rounded-lg transition-colors disabled:opacity-40" title="Aktualisieren">
                        <RefreshCw className={cn("w-3.5 h-3.5 text-slate-400", loading && "animate-spin")} />
                    </button>
                </div>
            </div>

            {/* Content-Bereich */}
            {useFallback ? (
                /* Fallback: iframe mit weißem Hintergrund */
                <div className="flex-1 relative" style={{ background: 'white' }}>
                    {loading && (
                        <div className="absolute inset-0 z-10 bg-white/70 backdrop-blur-sm flex items-center justify-center pointer-events-none">
                            <div className="animate-spin rounded-full h-6 w-6 border-2 border-rose-200 border-t-rose-600" />
                        </div>
                    )}
                    {previewUrl ? (
                        <iframe
                            src={`${previewUrl}#toolbar=0&navpanes=0&view=FitH`}
                            className="w-full h-full border-none"
                            style={{ background: 'white' }}
                            title="PDF Vorschau"
                        />
                    ) : (
                        <div className="h-full flex items-center justify-center">
                            <p className="text-xs text-slate-300">Wird generiert…</p>
                        </div>
                    )}
                </div>
            ) : (
                /* Canvas-basiertes Rendering */
                <div
                    ref={scrollContainerRef}
                    className="flex-1 overflow-y-auto overflow-x-hidden relative"
                    style={{ background: 'white' }}
                >
                    {loading && (
                        <div className="absolute inset-0 z-10 bg-white/70 backdrop-blur-sm flex items-center justify-center pointer-events-none">
                            <div className="animate-spin rounded-full h-6 w-6 border-2 border-rose-200 border-t-rose-600" />
                        </div>
                    )}

                    <div ref={canvasContainerRef} style={{ lineHeight: 0 }} />

                    {error && !useFallback && (
                        <div className="p-4 text-center">
                            <p className="text-xs text-red-400">{error}</p>
                        </div>
                    )}

                    {!previewUrl && !loading && (
                        <div className="h-full flex items-center justify-center">
                            <p className="text-xs text-slate-300">Wird generiert…</p>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
