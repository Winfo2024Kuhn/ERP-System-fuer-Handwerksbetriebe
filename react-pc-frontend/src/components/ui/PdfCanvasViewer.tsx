import { useCallback, useRef, useEffect, useState } from 'react';

/* eslint-disable @typescript-eslint/no-explicit-any */

/**
 * Prüft ob PDF.js (v3 UMD) als window.pdfjsLib verfügbar ist.
 */
function getPdfjsLib(): any | null {
    const lib = (window as any).pdfjsLib;
    if (lib && typeof lib.getDocument === 'function') return lib;
    return null;
}

interface PdfCanvasViewerProps {
    url: string;
    className?: string;
}

/**
 * Canvas-basierte PDF-Vorschau ohne Browser-PDF-Viewer.
 * Rendert alle Seiten als Canvas-Elemente mit HiDPI-Support.
 * Fallback auf iframe mit ausgeblendetem Toolbar falls PDF.js nicht verfügbar.
 */
export function PdfCanvasViewer({ url, className }: PdfCanvasViewerProps) {
    const scrollContainerRef = useRef<HTMLDivElement>(null);
    const canvasContainerRef = useRef<HTMLDivElement>(null);
    const pdfDocRef = useRef<any>(null);
    const renderingRef = useRef(false);
    const [pageCount, setPageCount] = useState(0);
    const [useFallback, setUseFallback] = useState(false);

    const renderPdf = useCallback(async (pdfUrl: string) => {
        const pdfjsLib = getPdfjsLib();
        if (!pdfjsLib) {
            setUseFallback(true);
            return;
        }

        if (!canvasContainerRef.current) return;
        if (renderingRef.current) return;
        renderingRef.current = true;

        try {
            const response = await fetch(pdfUrl);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            const data = await response.arrayBuffer();

            if (pdfDocRef.current) {
                try { pdfDocRef.current.destroy(); } catch { /* ok */ }
            }

            const loadingTask = pdfjsLib.getDocument({ data });
            const doc = await loadingTask.promise;
            pdfDocRef.current = doc;
            setPageCount(doc.numPages);

            const container = canvasContainerRef.current;
            if (!container) { renderingRef.current = false; return; }

            const containerWidth = container.clientWidth;
            if (containerWidth <= 0) { renderingRef.current = false; return; }

            container.innerHTML = '';

            for (let i = 1; i <= doc.numPages; i++) {
                const page = await doc.getPage(i);
                const defaultViewport = page.getViewport({ scale: 1 });
                const scale = containerWidth / defaultViewport.width;
                const viewport = page.getViewport({ scale });

                const canvas = document.createElement('canvas');
                const ctx = canvas.getContext('2d');
                if (!ctx) continue;

                const dpr = window.devicePixelRatio || 1;
                canvas.width = Math.floor(viewport.width * dpr);
                canvas.height = Math.floor(viewport.height * dpr);
                canvas.style.width = `${Math.floor(viewport.width)}px`;
                canvas.style.height = `${Math.floor(viewport.height)}px`;
                canvas.style.display = 'block';
                ctx.scale(dpr, dpr);

                const wrapper = document.createElement('div');
                wrapper.style.background = 'white';
                wrapper.style.lineHeight = '0';
                if (i < doc.numPages) {
                    wrapper.style.marginBottom = '8px';
                }
                wrapper.appendChild(canvas);
                container.appendChild(wrapper);

                await page.render({ canvasContext: ctx, viewport }).promise;
            }
        } catch {
            setUseFallback(true);
        } finally {
            renderingRef.current = false;
        }
    }, []);

    useEffect(() => {
        if (!url) return;
        if (useFallback) return;
        renderPdf(url);
    }, [url, renderPdf, useFallback]);

    // Resize-Handler
    useEffect(() => {
        if (useFallback) return;
        let timer: ReturnType<typeof setTimeout>;
        const onResize = () => {
            clearTimeout(timer);
            timer = setTimeout(() => {
                if (url) renderPdf(url);
            }, 300);
        };
        window.addEventListener('resize', onResize);
        return () => { clearTimeout(timer); window.removeEventListener('resize', onResize); };
    }, [url, renderPdf, useFallback]);

    // Cleanup
    useEffect(() => {
        return () => {
            if (pdfDocRef.current) {
                try { pdfDocRef.current.destroy(); } catch { /* ok */ }
                pdfDocRef.current = null;
            }
        };
    }, []);

    if (useFallback) {
        return (
            <iframe
                src={`${url}#toolbar=0&navpanes=0&view=FitH`}
                className={className || "w-full h-[70vh] rounded-lg border border-slate-200"}
                style={{ background: 'white' }}
                title="PDF Vorschau"
            />
        );
    }

    return (
        <div
            ref={scrollContainerRef}
            className={className || "w-full h-[70vh] rounded-lg overflow-y-auto overflow-x-hidden"}
            style={{ background: '#f8fafc' }}
        >
            {pageCount > 0 && (
                <div className="sticky top-0 z-10 flex justify-end px-3 py-1.5">
                    <span className="text-[11px] text-slate-400 bg-white/80 backdrop-blur-sm px-2 py-0.5 rounded-full border border-slate-100">
                        {pageCount} {pageCount === 1 ? 'Seite' : 'Seiten'}
                    </span>
                </div>
            )}
            <div ref={canvasContainerRef} style={{ lineHeight: 0 }} />
        </div>
    );
}
