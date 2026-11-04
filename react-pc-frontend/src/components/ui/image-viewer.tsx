import React, { useEffect, useState, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { X, ChevronLeft, ChevronRight, ZoomIn, ZoomOut, Download } from 'lucide-react';
import { toSafeResourceUrl } from '../../lib/htmlSanitizer';
import { cn } from '../../lib/utils';
import { useVorschauFokus } from '../../hooks/useVorschauFokus';
import { BUTTON_STEP, MAX_SCALE, MIN_SCALE, useBildZoom } from './useBildZoom';

interface ImageViewerProps {
    src: string | null;
    alt?: string;
    onClose: () => void;
    /** Optional array of images for gallery navigation */
    images?: { url: string; name?: string }[];
    /** Starting index in the images array */
    startIndex?: number;
}

/**
 * Dateiname für den Download: der mitgegebene Name, sonst der letzte Teil der
 * Adresse (ohne Abfrage), sonst „Bild“.
 */
function downloadName(url: string, name?: string): string {
    const vorgegeben = name?.trim();
    if (vorgegeben) return vorgegeben;
    const pfad = url.split(/[?#]/, 1)[0];
    const letzterTeil = pfad.substring(pfad.lastIndexOf('/') + 1);
    try {
        return decodeURIComponent(letzterTeil) || 'Bild';
    } catch {
        return letzterTeil || 'Bild';
    }
}

/** Runde Icon-Knöpfe auf dunklem Grund: weißer Fokus-Ring, nur Farbwechsel beim Hover. */
const ICON_KNOPF = 'text-white rounded-full hover:bg-white/20 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white';
const RUNDER_KNOPF = cn(ICON_KNOPF, 'bg-black/50 backdrop-blur-sm');

/**
 * Vollbild-Bildbetrachter (Pflicht-Komponente): eigene modale Ebene über jedem Dialog
 * (`role="dialog"`, Name = Dateiname), Fokus auf „Schließen“, Escape schließt nur ihn,
 * danach Fokus zurück an den Opener (`useVorschauFokus`). Zoom per Mausrad, Knöpfen,
 * +/−/0 und Doppelklick; Pfeiltasten blättern in einer Galerie.
 */
export const ImageViewer: React.FC<ImageViewerProps> = ({ src, alt, onClose, images, startIndex }) => {
    const [currentIndex, setCurrentIndex] = useState(Math.max(0, startIndex ?? 0));

    // Galerie nur, wenn das gewünschte Bild darin steht; sonst (startIndex -1) nur `src` zeigen.
    const inGalerie = !!images && images.length > 0 && (startIndex ?? 0) >= 0;
    const gallery = inGalerie
        ? images
        : src ? [{ url: src, name: alt }] : [];

    const currentImage = gallery[currentIndex];
    const offen = !!currentImage;
    const hasMultiple = gallery.length > 1;
    const name = currentImage?.name || alt || 'Bild';

    const goNext = useCallback(() => {
        if (currentIndex < gallery.length - 1) setCurrentIndex(i => i + 1);
    }, [currentIndex, gallery.length]);

    const goPrev = useCallback(() => {
        if (currentIndex > 0) setCurrentIndex(i => i - 1);
    }, [currentIndex]);

    // Läuft erst bei einem Tastendruck, dann ist `zoom` (braucht das Fenster) längst gesetzt.
    const beiTaste = (e: KeyboardEvent): boolean => {
        if (e.key === '+' || e.key === '=') zoom.zoomBy(BUTTON_STEP);
        else if (e.key === '-' || e.key === '_') zoom.zoomBy(-BUTTON_STEP);
        else if (e.key === '0') zoom.resetZoom();
        else if (e.key === 'ArrowRight' && hasMultiple && !zoom.isZoomed) goNext();
        else if (e.key === 'ArrowLeft' && hasMultiple && !zoom.isZoomed) goPrev();
        else return false;
        return true;
    };

    const { fensterRef, schliessenRef, zIndex } = useVorschauFokus({ offen, onClose, onTaste: beiTaste });
    const zoom = useBildZoom(fensterRef, offen);
    const { scale, offset, isDragging, isZoomed, resetZoom, zoomBy } = zoom;

    // Reset index when startIndex changes
    useEffect(() => {
        if (startIndex !== undefined) {
            // eslint-disable-next-line react-hooks/set-state-in-effect
            setCurrentIndex(Math.max(0, startIndex));
        }
    }, [startIndex]);

    // Reset index when src changes (single-image mode)
    useEffect(() => {
        if (!images && src) {
            // eslint-disable-next-line react-hooks/set-state-in-effect
            setCurrentIndex(0);
        }
    }, [src, images]);

    // Reset zoom on image change
    useEffect(() => {
        resetZoom();
    }, [currentIndex, resetZoom]);

    if (!currentImage) return null;

    const sichereUrl = toSafeResourceUrl(currentImage.url);
    const prozent = Math.round(scale * 100);

    return createPortal(
        <div
            ref={fensterRef}
            role="dialog"
            aria-modal="true"
            aria-label={name}
            tabIndex={-1}
            style={{ zIndex }}
            className="fixed inset-0 bg-black/80 flex items-center justify-center p-8 select-none focus:outline-none motion-safe:animate-in motion-safe:fade-in motion-safe:duration-200"
            onClick={onClose}
        >
            {/* Herunterladen + Schließen */}
            <div className="absolute top-6 right-6 z-10 flex items-center gap-2">
                {sichereUrl && (
                    <a
                        href={sichereUrl}
                        download={downloadName(currentImage.url, currentImage.name)}
                        onClick={(e) => e.stopPropagation()}
                        className={cn(RUNDER_KNOPF, 'p-2')}
                        title="Bild herunterladen"
                        aria-label="Bild herunterladen"
                    >
                        <Download aria-hidden="true" className="w-6 h-6" />
                    </a>
                )}
                <button
                    ref={schliessenRef}
                    type="button"
                    onClick={(e) => { e.stopPropagation(); onClose(); }}
                    className={cn(RUNDER_KNOPF, 'p-2')}
                    title="Vorschau schließen (Esc)"
                    aria-label="Vorschau schließen"
                >
                    <X aria-hidden="true" className="w-6 h-6" />
                </button>
            </div>

            {hasMultiple && (
                <div className="absolute top-7 left-1/2 -translate-x-1/2 text-white/80 text-sm font-medium bg-black/50 px-3 py-1 rounded-full backdrop-blur-sm z-10">
                    {currentIndex + 1} / {gallery.length}
                </div>
            )}

            {/* Zoom-Leiste */}
            <div
                className="absolute bottom-6 left-1/2 -translate-x-1/2 z-10 flex items-center gap-1 bg-black/50 backdrop-blur-sm rounded-full px-2 py-1"
                onClick={(e) => e.stopPropagation()}
            >
                <button
                    type="button"
                    onClick={() => zoomBy(-BUTTON_STEP)}
                    disabled={scale <= MIN_SCALE}
                    className={cn(ICON_KNOPF, 'p-2 disabled:opacity-40 disabled:hover:bg-transparent')}
                    title="Verkleinern (-)"
                    aria-label="Verkleinern"
                >
                    <ZoomOut aria-hidden="true" className="w-5 h-5" />
                </button>
                <button
                    type="button"
                    onClick={resetZoom}
                    className={cn(ICON_KNOPF, 'px-3 py-1 text-sm font-medium min-w-[60px] tabular-nums')}
                    title="Zoom zurücksetzen (0)"
                    aria-label={`Zoom zurücksetzen, jetzt ${prozent} %`}
                >
                    {prozent}%
                </button>
                <button
                    type="button"
                    onClick={() => zoomBy(BUTTON_STEP)}
                    disabled={scale >= MAX_SCALE}
                    className={cn(ICON_KNOPF, 'p-2 disabled:opacity-40 disabled:hover:bg-transparent')}
                    title="Vergrößern (+)"
                    aria-label="Vergrößern"
                >
                    <ZoomIn aria-hidden="true" className="w-5 h-5" />
                </button>
            </div>

            {hasMultiple && currentIndex > 0 && !isZoomed && (
                <button
                    type="button"
                    onClick={(e) => { e.stopPropagation(); goPrev(); }}
                    className={cn(RUNDER_KNOPF, 'absolute left-4 top-1/2 -translate-y-1/2 z-10 w-12 h-12 flex items-center justify-center')}
                    title="Vorheriges Bild (←)"
                    aria-label="Vorheriges Bild"
                >
                    <ChevronLeft aria-hidden="true" className="w-7 h-7" />
                </button>
            )}

            {hasMultiple && currentIndex < gallery.length - 1 && !isZoomed && (
                <button
                    type="button"
                    onClick={(e) => { e.stopPropagation(); goNext(); }}
                    className={cn(RUNDER_KNOPF, 'absolute right-4 top-1/2 -translate-y-1/2 z-10 w-12 h-12 flex items-center justify-center')}
                    title="Nächstes Bild (→)"
                    aria-label="Nächstes Bild"
                >
                    <ChevronRight aria-hidden="true" className="w-7 h-7" />
                </button>
            )}

            <img
                src={sichereUrl}
                alt={currentImage.name || alt || 'Vollbild'}
                draggable={false}
                className={cn(
                    'max-w-full max-h-[90vh] object-contain rounded-lg shadow-2xl motion-safe:animate-in motion-safe:zoom-in-95 motion-safe:duration-200',
                    // Beim Ziehen folgt das Bild direkt der Maus, sonst weich (nur bei erlaubter Bewegung).
                    !isDragging && 'motion-safe:transition-transform motion-safe:duration-150 motion-safe:ease-out',
                )}
                style={{
                    transform: `translate(${offset.x}px, ${offset.y}px) scale(${scale})`,
                    cursor: isZoomed ? (isDragging ? 'grabbing' : 'grab') : 'zoom-in',
                    willChange: isZoomed || isDragging ? 'transform' : 'auto',
                }}
                onClick={(e) => e.stopPropagation()}
                onMouseDown={zoom.beiMausDruck}
                onDoubleClick={zoom.beiDoppelklick}
            />
        </div>,
        document.body,
    );
};
