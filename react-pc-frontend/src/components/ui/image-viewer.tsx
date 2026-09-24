import React, { useEffect, useState, useCallback, useRef } from 'react';
import { X, ChevronLeft, ChevronRight, ZoomIn, ZoomOut } from 'lucide-react';

interface ImageViewerProps {
    src: string | null;
    alt?: string;
    onClose: () => void;
    /** Optional array of images for gallery navigation */
    images?: { url: string; name?: string }[];
    /** Starting index in the images array */
    startIndex?: number;
}

const MIN_SCALE = 1;
const MAX_SCALE = 6;
const WHEEL_STEP = 0.2;
const BUTTON_STEP = 0.5;
const DBL_CLICK_TARGET = 2.5;

export const ImageViewer: React.FC<ImageViewerProps> = ({ src, alt, onClose, images, startIndex }) => {
    const [currentIndex, setCurrentIndex] = useState(startIndex ?? 0);
    const [scale, setScale] = useState(1);
    const [offset, setOffset] = useState({ x: 0, y: 0 });
    const [isDragging, setIsDragging] = useState(false);
    const dragStart = useRef({ x: 0, y: 0, ox: 0, oy: 0 });
    const stageRef = useRef<HTMLDivElement>(null);

    // Latest values for use inside stable event listeners without re-binding
    const scaleRef = useRef(scale);
    useEffect(() => {
        scaleRef.current = scale;
    }, [scale]);

    // Derive gallery from props
    const gallery = images && images.length > 0
        ? images
        : src ? [{ url: src, name: alt }] : [];

    const currentImage = gallery[currentIndex];
    const hasMultiple = gallery.length > 1;
    const isZoomed = scale > 1;

    const resetZoom = useCallback(() => {
        setScale(1);
        setOffset({ x: 0, y: 0 });
    }, []);

    // Reset index when startIndex changes
    useEffect(() => {
        if (startIndex !== undefined) {
            // eslint-disable-next-line react-hooks/set-state-in-effect
            setCurrentIndex(startIndex);
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
        // eslint-disable-next-line react-hooks/set-state-in-effect
        resetZoom();
    }, [currentIndex, resetZoom]);

    const goNext = useCallback(() => {
        if (currentIndex < gallery.length - 1) {
            setCurrentIndex(i => i + 1);
        }
    }, [currentIndex, gallery.length]);

    const goPrev = useCallback(() => {
        if (currentIndex > 0) {
            setCurrentIndex(i => i - 1);
        }
    }, [currentIndex]);

    /**
     * Stable zoom function — uses functional setState so it never closes over `scale`.
     * Pass a relative `delta`. The optional focus point keeps the pixel under the cursor stationary.
     * Returns true if the scale actually changed (so the wheel handler can decide whether to swallow the scroll).
     */
    const zoomBy = useCallback((delta: number, focusClientX?: number, focusClientY?: number): boolean => {
        const stage = stageRef.current;
        let changed = false;
        setScale(prev => {
            const next = Math.max(MIN_SCALE, Math.min(MAX_SCALE, prev + delta));
            if (next === prev) return prev;
            changed = true;
            if (next === 1) {
                setOffset({ x: 0, y: 0 });
                return next;
            }
            if (stage && focusClientX !== undefined && focusClientY !== undefined) {
                const rect = stage.getBoundingClientRect();
                const centerX = rect.left + rect.width / 2;
                const centerY = rect.top + rect.height / 2;
                const focusX = focusClientX - centerX;
                const focusY = focusClientY - centerY;
                const ratio = next / prev;
                setOffset(o => ({
                    x: focusX - (focusX - o.x) * ratio,
                    y: focusY - (focusY - o.y) * ratio,
                }));
            }
            return next;
        });
        return changed;
    }, []);

    const zoomIn = useCallback(() => { zoomBy(BUTTON_STEP); }, [zoomBy]);
    const zoomOut = useCallback(() => { zoomBy(-BUTTON_STEP); }, [zoomBy]);

    // Non-passive wheel listener so we can preventDefault on the stage
    useEffect(() => {
        const stage = stageRef.current;
        if (!stage) return;
        const onWheel = (e: WheelEvent) => {
            const delta = e.deltaY < 0 ? WHEEL_STEP : -WHEEL_STEP;
            const changed = zoomBy(delta, e.clientX, e.clientY);
            if (changed) e.preventDefault();
        };
        stage.addEventListener('wheel', onWheel, { passive: false });
        return () => stage.removeEventListener('wheel', onWheel);
    }, [zoomBy]);

    // Drag-to-pan when zoomed
    useEffect(() => {
        if (!isDragging) return;
        const onMove = (e: MouseEvent) => {
            setOffset({
                x: dragStart.current.ox + (e.clientX - dragStart.current.x),
                y: dragStart.current.oy + (e.clientY - dragStart.current.y),
            });
        };
        const onUp = () => setIsDragging(false);
        window.addEventListener('mousemove', onMove);
        window.addEventListener('mouseup', onUp);
        return () => {
            window.removeEventListener('mousemove', onMove);
            window.removeEventListener('mouseup', onUp);
        };
    }, [isDragging]);

    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                onClose();
            } else if (e.key === '+' || e.key === '=') {
                zoomBy(BUTTON_STEP);
            } else if (e.key === '-' || e.key === '_') {
                zoomBy(-BUTTON_STEP);
            } else if (e.key === '0') {
                resetZoom();
            } else if (e.key === 'ArrowRight' && hasMultiple && scaleRef.current === 1) {
                goNext();
            } else if (e.key === 'ArrowLeft' && hasMultiple && scaleRef.current === 1) {
                goPrev();
            }
        };

        if (src || (images && images.length > 0)) {
            window.addEventListener('keydown', handleKeyDown);
            document.body.style.overflow = 'hidden';
        }

        return () => {
            window.removeEventListener('keydown', handleKeyDown);
            document.body.style.overflow = '';
        };
    }, [src, images, onClose, hasMultiple, goNext, goPrev, zoomBy, resetZoom]);

    if (!src && (!images || images.length === 0)) return null;
    if (!currentImage) return null;

    const handleImageMouseDown = (e: React.MouseEvent) => {
        if (!isZoomed) return;
        e.stopPropagation();
        e.preventDefault();
        setIsDragging(true);
        dragStart.current = { x: e.clientX, y: e.clientY, ox: offset.x, oy: offset.y };
    };

    const handleImageDoubleClick = (e: React.MouseEvent) => {
        e.stopPropagation();
        if (isZoomed) {
            resetZoom();
        } else {
            zoomBy(DBL_CLICK_TARGET - 1, e.clientX, e.clientY);
        }
    };

    return (
        <div
            ref={stageRef}
            className="fixed inset-0 bg-black/80 z-[100] flex items-center justify-center p-8 animate-in fade-in duration-200 select-none"
            onClick={onClose}
        >
            {/* Close button */}
            <button
                onClick={onClose}
                className="absolute top-6 right-6 p-2 bg-black/50 hover:bg-white/20 text-white rounded-full transition-all backdrop-blur-sm z-10"
                title="Schließen (ESC)"
            >
                <X className="w-6 h-6" />
            </button>

            {/* Image counter */}
            {hasMultiple && (
                <div className="absolute top-7 left-1/2 -translate-x-1/2 text-white/80 text-sm font-medium bg-black/50 px-3 py-1 rounded-full backdrop-blur-sm z-10">
                    {currentIndex + 1} / {gallery.length}
                </div>
            )}

            {/* Zoom toolbar */}
            <div
                className="absolute bottom-6 left-1/2 -translate-x-1/2 z-10 flex items-center gap-1 bg-black/50 backdrop-blur-sm rounded-full px-2 py-1"
                onClick={(e) => e.stopPropagation()}
            >
                <button
                    onClick={zoomOut}
                    disabled={scale <= MIN_SCALE}
                    className="p-2 text-white rounded-full hover:bg-white/20 disabled:opacity-40 disabled:hover:bg-transparent transition-all"
                    title="Verkleinern (-)"
                >
                    <ZoomOut className="w-5 h-5" />
                </button>
                <button
                    onClick={resetZoom}
                    className="px-3 py-1 text-white text-sm font-medium rounded-full hover:bg-white/20 transition-all min-w-[60px]"
                    title="Zoom zurücksetzen (0)"
                >
                    {Math.round(scale * 100)}%
                </button>
                <button
                    onClick={zoomIn}
                    disabled={scale >= MAX_SCALE}
                    className="p-2 text-white rounded-full hover:bg-white/20 disabled:opacity-40 disabled:hover:bg-transparent transition-all"
                    title="Vergrößern (+)"
                >
                    <ZoomIn className="w-5 h-5" />
                </button>
            </div>

            {/* Previous arrow */}
            {hasMultiple && currentIndex > 0 && !isZoomed && (
                <button
                    onClick={(e) => { e.stopPropagation(); goPrev(); }}
                    className="absolute left-4 top-1/2 -translate-y-1/2 z-10 w-12 h-12 flex items-center justify-center bg-black/50 hover:bg-white/20 rounded-full transition-all backdrop-blur-sm"
                    title="Vorheriges Bild (←)"
                >
                    <ChevronLeft className="w-7 h-7 text-white" />
                </button>
            )}

            {/* Next arrow */}
            {hasMultiple && currentIndex < gallery.length - 1 && !isZoomed && (
                <button
                    onClick={(e) => { e.stopPropagation(); goNext(); }}
                    className="absolute right-4 top-1/2 -translate-y-1/2 z-10 w-12 h-12 flex items-center justify-center bg-black/50 hover:bg-white/20 rounded-full transition-all backdrop-blur-sm"
                    title="Nächstes Bild (→)"
                >
                    <ChevronRight className="w-7 h-7 text-white" />
                </button>
            )}

            {/* Image */}
            <img
                src={currentImage.url}
                alt={currentImage.name || alt || 'Vollbild'}
                draggable={false}
                className="max-w-full max-h-[90vh] object-contain rounded-lg shadow-2xl animate-in zoom-in-95 duration-200"
                style={{
                    transform: `translate(${offset.x}px, ${offset.y}px) scale(${scale})`,
                    transition: isDragging ? 'none' : 'transform 150ms ease-out',
                    cursor: isZoomed ? (isDragging ? 'grabbing' : 'grab') : 'zoom-in',
                    willChange: isZoomed || isDragging ? 'transform' : 'auto',
                }}
                onClick={(e) => e.stopPropagation()}
                onMouseDown={handleImageMouseDown}
                onDoubleClick={handleImageDoubleClick}
            />
        </div>
    );
};
