import React, { useEffect, useCallback, useState, useRef } from 'react';
import { X, ZoomIn, ZoomOut, RotateCcw, ChevronLeft, ChevronRight, ImageOff, Loader2 } from 'lucide-react';
import { createPortal } from 'react-dom';
import { mobileOverlayStyle } from './toast';

interface ImageViewerProps {
    /** Single image URL (legacy) or currently selected URL */
    src: string | null;
    alt?: string;
    onClose: () => void;
    /** All images for gallery navigation */
    images?: { url: string; name?: string }[];
    /** Starting index in images array */
    startIndex?: number;
}

/**
 * Gespeicherte Bilder liegen unter /api/dokumente/<name> oder /api/images/<name>.
 * Für beide liefert der Server unter /api/dokumente/<name>/… ein Vorschaubild
 * (300 px) und eine Anzeigegröße (1600 px). Andere Adressen (z. B. blob:) haben
 * keine Varianten und werden unverändert verwendet.
 */
const GESPEICHERTES_BILD = /^\/api\/(?:dokumente|images)\/([^/?#]+)$/;

function bildVarianten(url: string): { thumbnail: string; anzeige: string } {
    const treffer = GESPEICHERTES_BILD.exec(url);
    if (!treffer) return { thumbnail: url, anzeige: url };
    const basis = `/api/dokumente/${treffer[1]}`;
    return { thumbnail: `${basis}/thumbnail`, anzeige: `${basis}/anzeige` };
}

// 44 pt Tippfläche (iOS-Empfehlung) – auch mit Arbeitshandschuhen treffsicher
const iconButton = 'w-11 h-11 flex items-center justify-center bg-white border border-slate-200 shadow-sm rounded-full text-slate-700 transition-all active:scale-90 disabled:opacity-40';

export const ImageViewer: React.FC<ImageViewerProps> = ({ src, alt, onClose, images, startIndex }) => {
    const [currentIndex, setCurrentIndex] = useState(startIndex ?? 0);
    const [scale, setScale] = useState(1);
    const [position, setPosition] = useState({ x: 0, y: 0 });
    const [isDragging, setIsDragging] = useState(false);
    const [fadeIn, setFadeIn] = useState(false);
    const [swipeOffset, setSwipeOffset] = useState(0);
    const [isSwiping, setIsSwiping] = useState(false);
    // Ladezustand je Bildadresse statt eines einzelnen Flags: Ein Flag, das beim
    // Bildwechsel zeitversetzt zurückgesetzt wird, kann das onLoad eines schnellen
    // (gecachten) Bildes überschreiben – dann drehte sich der Spinner für immer.
    const [geladen, setGeladen] = useState<Record<string, true>>({});
    const [fehlgeschlagen, setFehlgeschlagen] = useState<Record<string, true>>({});
    const [originalBereit, setOriginalBereit] = useState<Record<string, true>>({});
    const [versuch, setVersuch] = useState(0);
    const lastTouchRef = useRef<{ x: number; y: number } | null>(null);
    const lastPinchDistanceRef = useRef<number | null>(null);
    // Ende des letzten echten Tipps. Nur ein Tipp (kurz, kaum bewegt) zählt als erster
    // Teil eines Doppeltipps – sonst zoomte schnelles Weiterwischen ungewollt hinein.
    const letzterTippRef = useRef<number>(0);
    const fingerAufRef = useRef<{ x: number; y: number; time: number } | null>(null);
    const swipeStartRef = useRef<{ x: number; y: number; time: number } | null>(null);
    const vorschauKnoepfe = useRef(new Map<number, HTMLButtonElement>());

    // Derive gallery from props
    const gallery = images && images.length > 0
        ? images
        : src ? [{ url: src, name: alt }] : [];
    const offen = gallery.length > 0;
    const galerieSchluessel = gallery.map(g => g.url).join('|');

    // Beim Öffnen auf das angetippte Bild springen. Bewusst während des Renderns
    // statt per Effekt, damit nie kurz das falsche Bild angefordert wird.
    const oeffnungsSchluessel = offen ? (startIndex ?? 0) : null;
    const [letzteOeffnung, setLetzteOeffnung] = useState<number | null>(null);
    if (oeffnungsSchluessel !== letzteOeffnung) {
        setLetzteOeffnung(oeffnungsSchluessel);
        if (oeffnungsSchluessel !== null) {
            setCurrentIndex(Math.min(Math.max(oeffnungsSchluessel, 0), gallery.length - 1));
            setScale(1);
            setPosition({ x: 0, y: 0 });
            setSwipeOffset(0);
        }
    }

    const currentImage = gallery[currentIndex];
    const hasMultiple = gallery.length > 1;

    const zeigeBild = useCallback((index: number) => {
        setCurrentIndex(index);
        setScale(1);
        setPosition({ x: 0, y: 0 });
        setSwipeOffset(0);
    }, []);

    // Animate in
    useEffect(() => {
        if (!offen) {
            const timeoutId = window.setTimeout(() => setFadeIn(false), 0);
            return () => window.clearTimeout(timeoutId);
        }
        const frameId = requestAnimationFrame(() => setFadeIn(true));
        return () => cancelAnimationFrame(frameId);
    }, [offen]);

    const goNext = useCallback(() => {
        if (currentIndex < gallery.length - 1) {
            zeigeBild(currentIndex + 1);
        }
    }, [currentIndex, gallery.length, zeigeBild]);

    const goPrev = useCallback(() => {
        if (currentIndex > 0) {
            zeigeBild(currentIndex - 1);
        }
    }, [currentIndex, zeigeBild]);

    // Handle ESC key and arrow keys
    const handleKeyDown = useCallback((e: KeyboardEvent) => {
        if (e.key === 'Escape') {
            onClose();
        } else if (e.key === 'ArrowRight' && hasMultiple) {
            goNext();
        } else if (e.key === 'ArrowLeft' && hasMultiple) {
            goPrev();
        }
    }, [onClose, hasMultiple, goNext, goPrev]);

    useEffect(() => {
        if (!offen) return;
        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [offen, handleKeyDown]);

    useEffect(() => {
        if (!offen) return;
        document.body.style.overflow = 'hidden';
        return () => {
            document.body.style.overflow = 'unset';
        };
    }, [offen]);

    // Nachbarbilder vorladen, damit das Weiterwischen sofort ein scharfes Bild zeigt
    useEffect(() => {
        if (!offen) return;
        const urls = galerieSchluessel.split('|');
        for (const index of [currentIndex + 1, currentIndex - 1]) {
            if (index >= 0 && index < urls.length) {
                new Image().src = bildVarianten(urls[index]).anzeige;
            }
        }
    }, [offen, currentIndex, galerieSchluessel]);

    // Die Vorschauleiste läuft mit: Das aktive Bild steht immer sichtbar in der Mitte
    useEffect(() => {
        if (!offen) return;
        vorschauKnoepfe.current.get(currentIndex)?.scrollIntoView?.({ inline: 'center', block: 'nearest', behavior: 'smooth' });
    }, [offen, currentIndex]);

    const varianten = currentImage ? bildVarianten(currentImage.url) : null;
    const originalUrl = currentImage?.url ?? '';

    // Wer hineinzoomt, will Details sehen (Typenschild, Maße): Dann im Hintergrund
    // das Original nachladen und erst tauschen, wenn es fertig ist.
    const brauchtOriginal = scale > 1 && !!varianten && varianten.anzeige !== originalUrl
        && !originalBereit[originalUrl];
    useEffect(() => {
        if (!brauchtOriginal) return;
        // Ohne onerror: Lädt das Original nicht, bleibt einfach die Anzeigegröße stehen.
        const bild = new Image();
        bild.onload = () => {
            setGeladen(prev => ({ ...prev, [originalUrl]: true }));
            setOriginalBereit(prev => ({ ...prev, [originalUrl]: true }));
        };
        bild.src = originalUrl;
        return () => {
            bild.onload = null;
        };
    }, [brauchtOriginal, originalUrl]);

    // Double-tap to zoom
    const handleDoubleTap = useCallback(() => {
        if (scale === 1) {
            setScale(2.5);
        } else {
            setScale(1);
            setPosition({ x: 0, y: 0 });
        }
    }, [scale]);

    // Touch handlers for swipe, pinch-to-zoom and pan
    const handleTouchStart = useCallback((e: React.TouchEvent) => {
        e.stopPropagation();

        if (e.touches.length === 1) {
            const now = Date.now();
            if (now - letzterTippRef.current < 300) {
                handleDoubleTap();
                letzterTippRef.current = 0;
                fingerAufRef.current = null;
                return;
            }

            const touch = { x: e.touches[0].clientX, y: e.touches[0].clientY };
            lastTouchRef.current = touch;
            fingerAufRef.current = { ...touch, time: now };

            if (scale > 1) {
                setIsDragging(true);
            } else {
                // Start swipe tracking
                swipeStartRef.current = { ...touch, time: now };
                setIsSwiping(true);
            }
        } else if (e.touches.length === 2) {
            const distance = Math.hypot(
                e.touches[0].clientX - e.touches[1].clientX,
                e.touches[0].clientY - e.touches[1].clientY
            );
            lastPinchDistanceRef.current = distance;
        }
    }, [scale, handleDoubleTap]);

    const handleTouchMove = useCallback((e: React.TouchEvent) => {
        e.stopPropagation();

        if (e.touches.length === 1 && lastTouchRef.current) {
            if (isDragging && scale > 1) {
                // Panning when zoomed
                const deltaX = e.touches[0].clientX - lastTouchRef.current.x;
                const deltaY = e.touches[0].clientY - lastTouchRef.current.y;

                setPosition(prev => ({
                    x: prev.x + deltaX,
                    y: prev.y + deltaY
                }));

                lastTouchRef.current = {
                    x: e.touches[0].clientX,
                    y: e.touches[0].clientY
                };
            } else if (isSwiping && hasMultiple && scale <= 1 && swipeStartRef.current) {
                // Horizontal swipe for navigation
                const deltaX = e.touches[0].clientX - swipeStartRef.current.x;
                setSwipeOffset(deltaX);
            }
        } else if (e.touches.length === 2 && lastPinchDistanceRef.current !== null) {
            const distance = Math.hypot(
                e.touches[0].clientX - e.touches[1].clientX,
                e.touches[0].clientY - e.touches[1].clientY
            );

            const scaleDelta = distance / lastPinchDistanceRef.current;
            const newScale = Math.min(Math.max(scale * scaleDelta, 0.5), 5);

            setScale(newScale);
            lastPinchDistanceRef.current = distance;

            if (newScale <= 1) {
                setPosition({ x: 0, y: 0 });
            }
        }
    }, [isDragging, scale, isSwiping, hasMultiple]);

    const handleTouchEnd = useCallback((e: React.TouchEvent) => {
        e.stopPropagation();
        const auf = fingerAufRef.current;
        const ab = e.changedTouches[0];
        const warTipp = !!auf && !!ab && e.touches.length === 0
            && Math.hypot(ab.clientX - auf.x, ab.clientY - auf.y) < 10
            && Date.now() - auf.time < 250;
        letzterTippRef.current = warTipp ? Date.now() : 0;
        fingerAufRef.current = null;
        setIsDragging(false);
        lastTouchRef.current = null;
        lastPinchDistanceRef.current = null;

        // Handle swipe end
        if (isSwiping && swipeStartRef.current) {
            const swipeThreshold = 60;
            const velocityThreshold = 0.3;
            const elapsed = Date.now() - swipeStartRef.current.time;
            const velocity = Math.abs(swipeOffset) / elapsed;

            if (swipeOffset < -swipeThreshold || (swipeOffset < -20 && velocity > velocityThreshold)) {
                goNext();
            } else if (swipeOffset > swipeThreshold || (swipeOffset > 20 && velocity > velocityThreshold)) {
                goPrev();
            }
        }

        setIsSwiping(false);
        setSwipeOffset(0);
        swipeStartRef.current = null;
    }, [isSwiping, swipeOffset, goNext, goPrev]);

    const zoomIn = () => setScale(s => Math.min(s + 0.5, 5));
    const zoomOut = () => {
        const newScale = Math.max(scale - 0.5, 1);
        setScale(newScale);
        if (newScale <= 1) setPosition({ x: 0, y: 0 });
    };
    const resetZoom = () => {
        setScale(1);
        setPosition({ x: 0, y: 0 });
    };

    const erneutVersuchen = () => {
        setFehlgeschlagen({});
        setVersuch(v => v + 1);
    };

    if (!offen || !currentImage || !varianten) return null;

    // Anzeigegröße zuerst; das Original nur nach dem Hineinzoomen oder wenn die
    // Anzeigegröße nicht geliefert werden konnte.
    const quelle = originalBereit[originalUrl] || fehlgeschlagen[varianten.anzeige]
        ? originalUrl
        : varianten.anzeige;
    const istGeladen = !!geladen[quelle];
    const istKaputt = !!fehlgeschlagen[quelle];
    const platzhalterSichtbar = !istGeladen && !istKaputt && varianten.thumbnail !== quelle
        && !fehlgeschlagen[varianten.thumbnail];

    const transform = `translate(${position.x + swipeOffset}px, ${position.y}px) scale(${scale})`;
    const transition = isDragging || isSwiping
        ? 'none'
        : 'transform 0.3s cubic-bezier(0.25, 0.46, 0.45, 0.94), opacity 0.2s ease';

    return createPortal(
        <div
            className={`fixed inset-0 bg-slate-50 z-[9999] flex flex-col touch-none transition-opacity duration-300 ${fadeIn ? 'opacity-100' : 'opacity-0'}`}
            style={mobileOverlayStyle}
            role="dialog"
            aria-modal="true"
            aria-label={currentImage.name || alt || 'Bildansicht'}
        >
            {/* Kopfleiste: hell wie die Statusleiste, mit Sockel unter dem iOS-Weichzeichner */}
            <div className="flex items-center justify-between gap-2 px-4 pb-3 safe-area-top z-10">
                <button
                    type="button"
                    onClick={(e) => {
                        e.stopPropagation();
                        onClose();
                    }}
                    className={iconButton}
                    aria-label="Schließen"
                >
                    <X className="w-5 h-5" />
                </button>

                <div className="flex items-center gap-2">
                    <button
                        type="button"
                        onClick={(e) => { e.stopPropagation(); zoomOut(); }}
                        disabled={scale <= 1}
                        className={iconButton}
                        aria-label="Verkleinern"
                    >
                        <ZoomOut className="w-5 h-5" />
                    </button>
                    <span className="text-slate-500 text-xs min-w-[2.5rem] text-center font-medium tabular-nums">
                        {Math.round(scale * 100)}%
                    </span>
                    <button
                        type="button"
                        onClick={(e) => { e.stopPropagation(); zoomIn(); }}
                        disabled={scale >= 5}
                        className={iconButton}
                        aria-label="Vergrößern"
                    >
                        <ZoomIn className="w-5 h-5" />
                    </button>
                    {scale !== 1 && (
                        <button
                            type="button"
                            onClick={(e) => { e.stopPropagation(); resetZoom(); }}
                            className={iconButton}
                            aria-label="Zoom zurücksetzen"
                        >
                            <RotateCcw className="w-5 h-5" />
                        </button>
                    )}
                </div>
            </div>

            {/* Navigation arrows (desktop) */}
            {hasMultiple && currentIndex > 0 && (
                <button
                    type="button"
                    onClick={(e) => { e.stopPropagation(); goPrev(); }}
                    className={`hidden sm:flex absolute left-3 top-1/2 -translate-y-1/2 z-10 ${iconButton}`}
                    aria-label="Vorheriges Bild"
                >
                    <ChevronLeft className="w-6 h-6" />
                </button>
            )}
            {hasMultiple && currentIndex < gallery.length - 1 && (
                <button
                    type="button"
                    onClick={(e) => { e.stopPropagation(); goNext(); }}
                    className={`hidden sm:flex absolute right-3 top-1/2 -translate-y-1/2 z-10 ${iconButton}`}
                    aria-label="Nächstes Bild"
                >
                    <ChevronRight className="w-6 h-6" />
                </button>
            )}

            {/* Image container */}
            <div
                className="flex-1 min-h-0 flex items-center justify-center overflow-hidden relative"
                onClick={(e) => {
                    e.stopPropagation();
                    if (scale <= 1) onClose();
                }}
                onTouchStart={handleTouchStart}
                onTouchMove={handleTouchMove}
                onTouchEnd={handleTouchEnd}
            >
                {istKaputt ? (
                    <div
                        className="flex flex-col items-center gap-3 rounded-2xl bg-white border border-slate-200 shadow-sm px-6 py-8 mx-6 text-center"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <ImageOff className="w-10 h-10 text-slate-400" aria-hidden="true" />
                        <p className="text-sm text-slate-600">Bild konnte nicht geladen werden.</p>
                        <button
                            type="button"
                            onClick={erneutVersuchen}
                            className="px-4 py-2 rounded-lg border border-rose-300 text-rose-700 text-sm font-medium active:bg-rose-50"
                        >
                            Erneut versuchen
                        </button>
                    </div>
                ) : (
                    <>
                        {/* Das Vorschaubild kennt die Liste schon – es steht im Cache und
                            erscheint sofort, bis die scharfe Fassung da ist. */}
                        {platzhalterSichtbar && (
                            <img
                                key={`vorschau-${varianten.thumbnail}-${versuch}`}
                                src={varianten.thumbnail}
                                alt=""
                                aria-hidden="true"
                                className="absolute inset-0 w-full h-full object-contain select-none pointer-events-none"
                                style={{ transform, transition }}
                                draggable={false}
                                onError={() => setFehlgeschlagen(prev => ({ ...prev, [varianten.thumbnail]: true }))}
                            />
                        )}

                        {!istGeladen && (platzhalterSichtbar ? (
                            <div
                                className="absolute bottom-3 right-4 z-10 rounded-full bg-white/80 p-1.5 pointer-events-none"
                                role="status"
                                aria-label="Bild wird geladen"
                            >
                                <Loader2 className="w-4 h-4 text-slate-500 motion-safe:animate-spin" />
                            </div>
                        ) : (
                            <div
                                className="absolute inset-0 flex items-center justify-center pointer-events-none"
                                role="status"
                                aria-label="Bild wird geladen"
                            >
                                <div className="w-3/4 max-w-sm aspect-[4/3] rounded-lg bg-slate-200 motion-safe:animate-pulse" />
                            </div>
                        ))}

                        <img
                            key={`${quelle}-${versuch}`}
                            src={quelle}
                            alt={currentImage.name || alt || 'Vollbild'}
                            decoding="async"
                            className={`relative max-w-full max-h-full object-contain select-none ${istGeladen ? 'opacity-100' : 'opacity-0'}`}
                            style={{ transform, transition }}
                            draggable={false}
                            onClick={(e) => e.stopPropagation()}
                            onLoad={() => setGeladen(prev => ({ ...prev, [quelle]: true }))}
                            onError={() => setFehlgeschlagen(prev => ({ ...prev, [quelle]: true }))}
                        />
                    </>
                )}
            </div>

            {/* Bottom bar: image name + thumbnail strip */}
            <div className="z-10 pb-3">
                {/* Zähler + Dateiname */}
                {(hasMultiple || currentImage.name) && (
                    <p className="flex items-center justify-center gap-1.5 text-xs px-4 pt-2 min-w-0">
                        {hasMultiple && (
                            <span className="text-slate-700 font-medium tabular-nums shrink-0">
                                {currentIndex + 1} / {gallery.length}
                            </span>
                        )}
                        {hasMultiple && currentImage.name && <span className="text-slate-400" aria-hidden="true">·</span>}
                        {currentImage.name && <span className="text-slate-500 truncate">{currentImage.name}</span>}
                    </p>
                )}

                {/* Thumbnail strip – touch-pan-x, weil der Dialog sonst jede Wischgeste schluckt */}
                {hasMultiple && (
                    <div className="overflow-x-auto hide-scrollbar touch-pan-x px-4 py-2">
                        <div className="flex gap-2 w-max mx-auto p-1">
                            {gallery.map((img, idx) => (
                                <button
                                    type="button"
                                    key={img.url}
                                    ref={(el) => {
                                        if (el) vorschauKnoepfe.current.set(idx, el);
                                        else vorschauKnoepfe.current.delete(idx);
                                    }}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        zeigeBild(idx);
                                    }}
                                    className={`flex-shrink-0 w-12 h-12 rounded-lg overflow-hidden bg-slate-200 transition-all duration-200 ${
                                        idx === currentIndex
                                            ? 'ring-2 ring-rose-600 ring-offset-2 ring-offset-slate-50'
                                            : 'opacity-60'
                                    }`}
                                    aria-label={`Bild ${idx + 1} anzeigen`}
                                    aria-current={idx === currentIndex ? 'true' : undefined}
                                >
                                    <img
                                        src={bildVarianten(img.url).thumbnail}
                                        alt=""
                                        className="w-full h-full object-cover"
                                        loading="lazy"
                                        decoding="async"
                                        onError={(e) => {
                                            // Ohne Vorschau einmalig auf das Original zurückfallen
                                            const el = e.currentTarget;
                                            if (el.getAttribute('src') !== img.url) {
                                                el.src = img.url;
                                            }
                                        }}
                                    />
                                </button>
                            ))}
                        </div>
                    </div>
                )}

                {/* Bedien-Hinweis – immer eine Zeile, damit die Leiste beim Zoomen nicht springt */}
                <p className="text-slate-500 text-xs text-center pointer-events-none">
                    {scale !== 1
                        ? 'Doppeltippen zum Zurücksetzen'
                        : hasMultiple ? 'Wischen zum Blättern' : 'Doppeltippen zum Zoomen'}
                </p>
            </div>
        </div>,
        document.body
    );
};
