import { useCallback, useRef, useEffect, useState } from 'react';
import { RefreshCw, Download, FileText } from 'lucide-react';
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
                    {(loading || !previewUrl) && <PdfLoadingSkeleton loading={loading} hasPreview={!!previewUrl} />}
                    {previewUrl ? (
                        <iframe
                            src={`${previewUrl}#toolbar=0&navpanes=0&view=FitH`}
                            className="w-full h-full border-none"
                            style={{ background: 'white' }}
                            title="PDF Vorschau"
                        />
                    ) : null}
                </div>
            ) : (
                /* Canvas-basiertes Rendering */
                <div
                    ref={scrollContainerRef}
                    className="flex-1 overflow-y-auto overflow-x-hidden relative"
                    style={{ background: 'white' }}
                >
                    {(loading || (!previewUrl && !error)) && <PdfLoadingSkeleton loading={loading} hasPreview={!!previewUrl} />}

                    <div ref={canvasContainerRef} style={{ lineHeight: 0 }} />

                    {error && !useFallback && (
                        <div className="p-4 text-center">
                            <p className="text-xs text-red-400">{error}</p>
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}

/* ────────────────────────────────────────────────────────────
   Premium PDF Loading Skeleton
   ──────────────────────────────────────────────────────────── */
function PdfLoadingSkeleton({ loading, hasPreview }: { loading: boolean; hasPreview: boolean }) {
    return (
        <div className={cn(
            "absolute inset-0 z-10 flex flex-col items-center pointer-events-none transition-opacity duration-300",
            hasPreview ? "bg-white/80 backdrop-blur-sm" : "bg-white",
        )}>
            {/* Document skeleton */}
            <div className="w-full max-w-[90%] mt-6 flex flex-col items-center gap-6">
                {/* Animated icon + status */}
                <div className="flex flex-col items-center gap-3 py-4">
                    <div className="relative">
                        <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-rose-50 to-rose-100 flex items-center justify-center shadow-sm">
                            <FileText className="w-7 h-7 text-rose-400" />
                        </div>
                        {loading && (
                            <div className="absolute -inset-1.5 rounded-2xl border-2 border-transparent border-t-rose-400 animate-spin" style={{ animationDuration: '1.2s' }} />
                        )}
                    </div>
                    <div className="flex flex-col items-center gap-1">
                        <span className="text-sm font-medium text-slate-500">
                            {loading ? 'PDF wird erstellt…' : 'Vorschau wird vorbereitet…'}
                        </span>
                        <span className="text-[11px] text-slate-400">
                            {loading ? 'Layout und Inhalte werden gerendert' : 'Starten Sie die Vorschau'}
                        </span>
                    </div>
                </div>

                {/* A4 page skeleton */}
                <div
                    className="w-full bg-white rounded-lg overflow-hidden"
                    style={{
                        boxShadow: '0 2px 20px rgba(0,0,0,0.06), 0 0 0 1px rgba(0,0,0,0.04)',
                        aspectRatio: '210 / 297',
                        maxHeight: 'calc(100vh - 220px)',
                    }}
                >
                    <div className="p-[8%] h-full flex flex-col">
                        {/* Header area */}
                        <div className="flex justify-between items-start mb-[8%]">
                            <div className="flex flex-col gap-2 w-[45%]">
                                <div className="skeleton-shimmer h-3 rounded-full w-[70%]" style={{ animationDelay: '0s' }} />
                                <div className="skeleton-shimmer h-2.5 rounded-full w-[50%]" style={{ animationDelay: '0.08s' }} />
                                <div className="skeleton-shimmer h-2.5 rounded-full w-[60%]" style={{ animationDelay: '0.16s' }} />
                            </div>
                            <div className="skeleton-shimmer h-10 w-20 rounded" style={{ animationDelay: '0.1s' }} />
                        </div>

                        {/* Address block */}
                        <div className="flex flex-col gap-1.5 mb-[6%] w-[55%]">
                            <div className="skeleton-shimmer h-2 rounded-full w-[80%]" style={{ animationDelay: '0.2s' }} />
                            <div className="skeleton-shimmer h-2 rounded-full w-[65%]" style={{ animationDelay: '0.28s' }} />
                            <div className="skeleton-shimmer h-2 rounded-full w-[40%]" style={{ animationDelay: '0.36s' }} />
                        </div>

                        {/* Title */}
                        <div className="skeleton-shimmer h-4 rounded-full w-[35%] mb-[5%]" style={{ animationDelay: '0.4s' }} />

                        {/* Text lines */}
                        <div className="flex flex-col gap-2 flex-1">
                            {[90, 55, 70, 40, 85, 60, 75, 50, 65, 45, 80].map((w, i) => (
                                <div
                                    key={i}
                                    className="skeleton-shimmer h-2 rounded-full"
                                    style={{ width: `${w}%`, animationDelay: `${0.5 + i * 0.06}s` }}
                                />
                            ))}

                            {/* Table-like block */}
                            <div className="mt-[4%] flex flex-col gap-1.5">
                                <div className="skeleton-shimmer h-3 rounded w-full" style={{ animationDelay: '1.1s' }} />
                                <div className="skeleton-shimmer h-2.5 rounded w-full opacity-60" style={{ animationDelay: '1.16s' }} />
                                <div className="skeleton-shimmer h-2.5 rounded w-full opacity-60" style={{ animationDelay: '1.22s' }} />
                                <div className="skeleton-shimmer h-2.5 rounded w-[70%] opacity-60" style={{ animationDelay: '1.28s' }} />
                            </div>
                        </div>

                        {/* Footer */}
                        <div className="flex justify-between items-end mt-auto pt-[4%] border-t border-slate-50">
                            <div className="skeleton-shimmer h-2 rounded-full w-[30%]" style={{ animationDelay: '1.4s' }} />
                            <div className="skeleton-shimmer h-2 rounded-full w-[20%]" style={{ animationDelay: '1.46s' }} />
                        </div>
                    </div>
                </div>

                {/* Progress dots */}
                {loading && (
                    <div className="flex gap-1.5 py-2">
                        {[0, 1, 2].map(i => (
                            <div
                                key={i}
                                className="w-1.5 h-1.5 rounded-full bg-rose-300"
                                style={{
                                    animation: 'pdfDotPulse 1.4s ease-in-out infinite',
                                    animationDelay: `${i * 0.2}s`,
                                }}
                            />
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
