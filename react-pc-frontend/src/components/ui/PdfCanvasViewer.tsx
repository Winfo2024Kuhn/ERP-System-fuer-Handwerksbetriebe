import { useCallback, useRef, useEffect, useState } from 'react';
import { ZoomIn, ZoomOut, Maximize2, Printer } from 'lucide-react';

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
    /** Zoom-Steuerung ein-/ausblenden (Standard: an). */
    showZoomControls?: boolean;
    /**
     * Drucken-Knopf ein-/ausblenden (Standard: an).
     * Drucken ist rein lesend auf einem bereits erzeugten PDF und löst keine Buchung aus –
     * der GoBD-Pfad "buchen & sperren" hängt am Dokument-Editor, der ein neues PDF erzeugt.
     */
    showPrintButton?: boolean;
}

const ZOOM_MIN = 0.5;
const ZOOM_MAX = 4;
const ZOOM_STEP = 0.25;

function clampZoom(z: number): number {
    return Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, Math.round(z * 100) / 100));
}

/** Aufräumfrist, falls der Browser kein `afterprint` meldet – großzügig, damit niemand mitten im Druckdialog abgeschnitten wird. */
const PRINT_FRAME_TIMEOUT_MS = 10 * 60 * 1000;

/**
 * Öffnet den Druckdialog des Browsers für ein PDF – bewusst außerhalb des React-Lifecycles.
 *
 * Das unsichtbare iframe hängt direkt am `document.body` und überlebt deshalb, wenn die
 * Vorschau während des Druckdialogs geschlossen wird (z.B. Escape im Modal). Aufgeräumt
 * wird erst nach `afterprint` – vorher würde der Browser den Druck abbrechen.
 *
 * @param ownsUrl true, wenn `src` eine eigens erzeugte Object-URL ist, die hier wieder freigegeben werden muss.
 */
function openPrintFrame(src: string, ownsUrl: boolean): void {
    const frame = document.createElement('iframe');
    frame.setAttribute('aria-hidden', 'true');
    frame.style.position = 'fixed';
    frame.style.right = '0';
    frame.style.bottom = '0';
    frame.style.width = '0';
    frame.style.height = '0';
    frame.style.border = '0';
    frame.style.visibility = 'hidden';

    let done = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const cleanup = () => {
        if (done) return;
        done = true;
        if (timer !== undefined) clearTimeout(timer);
        frame.remove();
        if (ownsUrl) URL.revokeObjectURL(src);
    };
    const armTimer = () => {
        if (timer !== undefined) clearTimeout(timer);
        timer = setTimeout(cleanup, PRINT_FRAME_TIMEOUT_MS);
    };

    frame.onload = () => {
        const win = frame.contentWindow;
        if (!win) { cleanup(); return; }
        win.addEventListener('afterprint', cleanup, { once: true });
        armTimer();
        try {
            win.focus();
            win.print();
        } catch (fehler) {
            // Kein Toast hier – die Komponente ist ein Atom ohne solche Abhängigkeit.
            // Der Hinweis in der Konsole verhindert immerhin, dass ein stiller Fehlschlag unbemerkt bleibt.
            console.warn('Drucken nicht möglich:', fehler);
            cleanup();
        }
    };
    frame.onerror = cleanup;

    frame.src = src;
    document.body.appendChild(frame);
    // Bewusst schon hier scharfstellen: Bleibt `onload` aus (z.B. Content-Disposition
    // "attachment" oder ein geblockter Frame), gäbe es sonst nie ein Aufräumen.
    // Ist bereits aufgeräumt, braucht es keinen Timer mehr, der ins Leere läuft.
    if (!done) armTimer();
}

/**
 * Canvas-basierte PDF-Vorschau ohne Browser-PDF-Viewer.
 * Rendert alle Seiten als Canvas-Elemente mit HiDPI-Support.
 *
 * Zoom: Buttons in der schwebenden Toolbar oben links sowie Strg + Mausrad.
 * Das Dokument wird nur einmal pro URL geladen (fetch + parse); ein Zoom-Wechsel
 * rendert lediglich neu – ohne erneuten Netzwerk-Request.
 *
 * Drucken: Gedruckt wird das echte PDF (über ein unsichtbares iframe), nicht die
 * Canvas-Vorschau – dadurch bleiben Schrift und Layout in Druckqualität erhalten.
 *
 * Fallback auf iframe mit ausgeblendetem Toolbar falls PDF.js nicht verfügbar.
 */
export function PdfCanvasViewer({ url, className, showZoomControls = true, showPrintButton = true }: PdfCanvasViewerProps) {
    const scrollContainerRef = useRef<HTMLDivElement>(null);
    const canvasContainerRef = useRef<HTMLDivElement>(null);
    const pdfDocRef = useRef<any>(null);
    /**
     * Bytes des geladenen PDFs samt zugehöriger Quell-URL – wird zum Drucken wiederverwendet,
     * statt erneut zu laden. Die URL wird mitgeführt, damit nach einem Dokumentwechsel niemals
     * das vorherige PDF gedruckt wird. Beide Pfade (Canvas und iframe-Fallback) füllen dieselbe Ref.
     */
    const pdfBlobRef = useRef<{ src: string; blob: Blob } | null>(null);
    const renderingRef = useRef(false);
    const rerenderPendingRef = useRef(false);
    const [pageCount, setPageCount] = useState(0);
    /**
     * URL, zu der druckbare Bytes bereitliegen. Bewusst die URL statt eines Booleans:
     * So prüft die Anzeige exakt dieselbe Bedingung wie `handlePrint` – eine Wahrheit statt zwei.
     */
    const [readySrc, setReadySrc] = useState<string | null>(null);
    const [useFallback, setUseFallback] = useState(false);
    const [fallbackBlobUrl, setFallbackBlobUrl] = useState<string | null>(null);
    const [zoom, setZoom] = useState(1);
    const zoomRef = useRef(1);
    zoomRef.current = zoom;

    /** Rendert alle Seiten des bereits geladenen Dokuments auf Basis von Container-Breite × Zoom. */
    const renderPages = useCallback(async () => {
        const doc = pdfDocRef.current;
        const container = canvasContainerRef.current;
        const scroller = scrollContainerRef.current;
        if (!doc || !container || !scroller) return;

        const containerWidth = scroller.clientWidth;
        if (containerWidth <= 0) return;

        // Läuft schon ein Render? Dann nur vormerken – verhindert überlappende Renders bei schnellem Zoomen.
        if (renderingRef.current) { rerenderPendingRef.current = true; return; }
        renderingRef.current = true;

        try {
            const z = zoomRef.current;
            // In ein Fragment rendern und erst am Ende atomar einhängen → kein Flackern.
            const frag = document.createDocumentFragment();
            for (let i = 1; i <= doc.numPages; i++) {
                const page = await doc.getPage(i);
                const defaultViewport = page.getViewport({ scale: 1 });
                const fitScale = containerWidth / defaultViewport.width;
                const viewport = page.getViewport({ scale: fitScale * z });

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
                // fit-content + margin auto: zentriert bei Zoom < 100%, links bündig + Scrollbar bei Überbreite.
                wrapper.style.width = 'fit-content';
                wrapper.style.margin = '0 auto';
                if (i < doc.numPages) {
                    wrapper.style.marginBottom = '8px';
                }
                wrapper.appendChild(canvas);
                frag.appendChild(wrapper);

                await page.render({ canvasContext: ctx, viewport }).promise;
            }
            container.replaceChildren(frag);
        } catch {
            setUseFallback(true);
        } finally {
            renderingRef.current = false;
            if (rerenderPendingRef.current) {
                rerenderPendingRef.current = false;
                void renderPages();
            }
        }
    }, []);

    /** Lädt das Dokument (fetch + parse) und rendert es anschließend. */
    const loadDocument = useCallback(async (pdfUrl: string) => {
        const pdfjsLib = getPdfjsLib();
        if (!pdfjsLib) {
            setUseFallback(true);
            return;
        }
        // Sofort verwerfen: Ab hier gehört das alte PDF nicht mehr zur angezeigten URL.
        // Schlägt das Laden fehl, bleibt so nichts Veraltetes zum Drucken übrig.
        pdfBlobRef.current = null;
        setReadySrc(null);
        try {
            const response = await fetch(pdfUrl);
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            // Umweg über den Blob, damit dieselben Bytes auch zum Drucken zur Verfügung
            // stehen – PDF.js übernimmt den ArrayBuffer und leert ihn dabei.
            const blob = await response.blob();
            const data = await blob.arrayBuffer();
            pdfBlobRef.current = { src: pdfUrl, blob };
            setReadySrc(pdfUrl);

            if (pdfDocRef.current) {
                try { pdfDocRef.current.destroy(); } catch { /* ok */ }
            }

            const loadingTask = pdfjsLib.getDocument({ data });
            const doc = await loadingTask.promise;
            pdfDocRef.current = doc;
            setPageCount(doc.numPages);
            await renderPages();
        } catch {
            setUseFallback(true);
        }
    }, [renderPages]);

    /**
     * Druckt das Original-PDF – bevorzugt aus den bereits geladenen Bytes, damit
     * nichts erneut über das Netz muss.
     *
     * Der Abgleich auf `url` ist entscheidend: Nach einem Dokumentwechsel darf nie
     * das vorherige PDF im Drucker landen.
     */
    const handlePrint = useCallback(() => {
        const cached = pdfBlobRef.current;
        if (!cached || cached.src !== url) return;
        // Frische Object-URL pro Druck – der Druck-Frame gibt sie selbst wieder frei.
        // Dadurch kann kein Aufräumen der Vorschau die Quelle unter dem Druck wegziehen.
        openPrintFrame(URL.createObjectURL(cached.blob), true);
    }, [url]);

    /** Ohne Bytes zur aktuell angezeigten URL würde ein leeres Dokument im Drucker landen. */
    const canPrint = readySrc === url;

    // Dokument (neu) laden, wenn sich die URL ändert – Zoom dabei zurücksetzen.
    useEffect(() => {
        if (!url) return;
        if (useFallback) return;
        setZoom(1);
        void loadDocument(url);
    }, [url, loadDocument, useFallback]);

    // Bei Zoom-Änderung nur neu rendern (kein erneuter Fetch).
    useEffect(() => {
        if (useFallback) return;
        if (!pdfDocRef.current) return;
        void renderPages();
    }, [zoom, renderPages, useFallback]);

    // Resize-Handler – Fit-Breite an neue Container-Größe anpassen.
    useEffect(() => {
        if (useFallback) return;
        let timer: ReturnType<typeof setTimeout>;
        const onResize = () => {
            clearTimeout(timer);
            timer = setTimeout(() => { void renderPages(); }, 300);
        };
        window.addEventListener('resize', onResize);
        return () => { clearTimeout(timer); window.removeEventListener('resize', onResize); };
    }, [renderPages, useFallback]);

    // Strg + Mausrad zoomt (native, nicht-passiver Listener wegen preventDefault).
    useEffect(() => {
        const el = scrollContainerRef.current;
        if (!el || useFallback) return;
        const onWheel = (e: WheelEvent) => {
            if (!e.ctrlKey) return;
            e.preventDefault();
            setZoom(z => clampZoom(e.deltaY < 0 ? z + ZOOM_STEP : z - ZOOM_STEP));
        };
        el.addEventListener('wheel', onWheel, { passive: false });
        return () => el.removeEventListener('wheel', onWheel);
    }, [useFallback]);

    // Fallback-Pfad: PDF als Blob laden, damit das iframe auch extern gehostete PDFs
    // mit X-Frame-Options anzeigen kann (blob:// ist immer same-origin). Erst danach iframe.
    useEffect(() => {
        if (!useFallback || !url) return;
        let createdUrl: string | null = null;
        let cancelled = false;
        // Auch hier zuerst verwerfen: Solange die neuen Bytes fehlen, darf nichts Altes gedruckt werden.
        pdfBlobRef.current = null;
        setReadySrc(null);
        fetch(url)
            .then(res => res.ok ? res.blob() : Promise.reject(new Error(`HTTP ${res.status}`)))
            .then(blob => {
                if (cancelled) return;
                createdUrl = URL.createObjectURL(blob);
                setFallbackBlobUrl(createdUrl);
                // Der Druck bekommt den Blob, nicht diese URL – die wird beim Aufräumen
                // freigegeben und würde einen laufenden Druck sonst ins Leere laufen lassen.
                pdfBlobRef.current = { src: url, blob };
                setReadySrc(url);
            })
            .catch(() => { if (!cancelled) setFallbackBlobUrl(null); });
        return () => {
            cancelled = true;
            if (createdUrl) URL.revokeObjectURL(createdUrl);
            setFallbackBlobUrl(null);
        };
    }, [useFallback, url]);

    // Cleanup
    useEffect(() => {
        return () => {
            if (pdfDocRef.current) {
                try { pdfDocRef.current.destroy(); } catch { /* ok */ }
                pdfDocRef.current = null;
            }
            // Der Blob wird einfach losgelassen (GC). Bewusst kein Aufräumen des Druck-Frames:
            // Der lebt außerhalb der Komponente weiter, damit ein laufender Druck nicht abbricht,
            // wenn die Vorschau geschlossen wird.
            pdfBlobRef.current = null;
        };
    }, []);

    const zoomLabel = `${Math.round(zoom * 100)}%`;
    const btnClass = "p-1 rounded-full text-slate-600 hover:bg-slate-100 disabled:opacity-40 disabled:hover:bg-transparent transition-colors";
    const toolbarPillClass = "flex items-center gap-0.5 bg-white/90 backdrop-blur-sm rounded-full border border-slate-200 shadow-sm px-1 py-0.5 pointer-events-auto";

    // Deckt beide Fälle ehrlich ab: noch am Laden und dauerhaft nicht ladbar.
    const nichtBereitHinweis = "Dokument steht noch nicht zum Drucken bereit";
    const printButton = (
        <button
            type="button"
            onClick={handlePrint}
            disabled={!canPrint}
            title={canPrint ? "Drucken" : nichtBereitHinweis}
            aria-label="Drucken"
            className={btnClass}
        >
            <Printer className="w-4 h-4" />
        </button>
    );

    if (useFallback) {
        return (
            <div className="relative w-full h-full">
                {showPrintButton && (
                    <div className="absolute top-2 left-2 z-20 pointer-events-none">
                        <div className={toolbarPillClass}>{printButton}</div>
                    </div>
                )}
                <iframe
                    src={`${fallbackBlobUrl ?? url}#toolbar=0&navpanes=0&view=FitH`}
                    className={className || "w-full h-[70vh] rounded-lg border border-slate-200"}
                    style={{ background: 'white' }}
                    title="PDF Vorschau"
                />
            </div>
        );
    }

    return (
        <div
            ref={scrollContainerRef}
            className={className || "w-full h-[70vh] rounded-lg overflow-y-auto"}
            // overflowX inline überschreibt evtl. mitgegebenes overflow-x-hidden – nötig für horizontales Scrollen beim Zoomen.
            style={{ background: '#f8fafc', overflowX: 'auto' }}
        >
            {(showZoomControls || showPrintButton || pageCount > 0) && (
                <div className="sticky top-0 left-0 z-20 flex items-center justify-between px-3 py-1.5 pointer-events-none">
                    {showZoomControls ? (
                        <div className={toolbarPillClass}>
                            <button
                                type="button"
                                onClick={() => setZoom(z => clampZoom(z - ZOOM_STEP))}
                                disabled={zoom <= ZOOM_MIN}
                                title="Verkleinern"
                                aria-label="Verkleinern"
                                className={btnClass}
                            >
                                <ZoomOut className="w-4 h-4" />
                            </button>
                            <span className="text-[11px] tabular-nums text-slate-600 w-10 text-center select-none">
                                {zoomLabel}
                            </span>
                            <button
                                type="button"
                                onClick={() => setZoom(z => clampZoom(z + ZOOM_STEP))}
                                disabled={zoom >= ZOOM_MAX}
                                title="Vergrößern"
                                aria-label="Vergrößern"
                                className={btnClass}
                            >
                                <ZoomIn className="w-4 h-4" />
                            </button>
                            <button
                                type="button"
                                onClick={() => setZoom(1)}
                                title="An Breite anpassen"
                                aria-label="An Breite anpassen"
                                className={btnClass}
                            >
                                <Maximize2 className="w-4 h-4" />
                            </button>
                            {showPrintButton && (
                                <>
                                    <span className="w-px h-4 bg-slate-200 mx-0.5" aria-hidden="true" />
                                    {printButton}
                                </>
                            )}
                        </div>
                    ) : showPrintButton ? (
                        <div className={toolbarPillClass}>{printButton}</div>
                    ) : <span />}
                    {pageCount > 0 && (
                        <span className="text-[11px] text-slate-400 bg-white/80 backdrop-blur-sm px-2 py-0.5 rounded-full border border-slate-100 pointer-events-auto">
                            {pageCount} {pageCount === 1 ? 'Seite' : 'Seiten'}
                        </span>
                    )}
                </div>
            )}
            <div ref={canvasContainerRef} style={{ lineHeight: 0 }} />
        </div>
    );
}
