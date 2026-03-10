import React, { useEffect, useCallback, useState, useRef } from 'react';
import { X, ZoomIn, ZoomOut, RotateCcw, ChevronLeft, ChevronRight } from 'lucide-react';
import { createPortal } from 'react-dom';

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

export const ImageViewer: React.FC<ImageViewerProps> = ({ src, alt, onClose, images, startIndex }) => {
    const [currentIndex, setCurrentIndex] = useState(startIndex ?? 0);
    const [scale, setScale] = useState(1);
    const [position, setPosition] = useState({ x: 0, y: 0 });
    const [isDragging, setIsDragging] = useState(false);
    const [imageLoaded, setImageLoaded] = useState(false);
    const [fadeIn, setFadeIn] = useState(false);
    const [swipeOffset, setSwipeOffset] = useState(0);
    const [isSwiping, setIsSwiping] = useState(false);
    const lastTouchRef = useRef<{ x: number; y: number } | null>(null);
    const lastPinchDistanceRef = useRef<number | null>(null);
    const lastTapTimeRef = useRef<number>(0);
    const swipeStartRef = useRef<{ x: number; y: number; time: number } | null>(null);
    const containerRef = useRef<HTMLDivElement>(null);

    // Derive gallery from props
    const gallery = images && images.length > 0
        ? images
        : src ? [{ url: src, name: alt }] : [];

    const currentImage = gallery[currentIndex];
    const hasMultiple = gallery.length > 1;

    // Reset state when image changes
    useEffect(() => {
        setScale(1);
        setPosition({ x: 0, y: 0 });
        setImageLoaded(false);
        setSwipeOffset(0);
    }, [currentIndex, src]);

    // Set starting index when opening
    useEffect(() => {
        if (startIndex !== undefined) {
            setCurrentIndex(startIndex);
        }
    }, [startIndex]);

    // Animate in
    useEffect(() => {
        if (src || (images && images.length > 0)) {
            requestAnimationFrame(() => setFadeIn(true));
        } else {
            setFadeIn(false);
        }
    }, [src, images]);

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
        if (src || (images && images.length > 0)) {
            document.addEventListener('keydown', handleKeyDown);
            document.body.style.overflow = 'hidden';
        }

        return () => {
            document.removeEventListener('keydown', handleKeyDown);
            document.body.style.overflow = 'unset';
        };
    }, [src, images, handleKeyDown]);

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
            if (now - lastTapTimeRef.current < 300) {
                handleDoubleTap();
                lastTapTimeRef.current = 0;
                return;
            }
            lastTapTimeRef.current = now;

            const touch = { x: e.touches[0].clientX, y: e.touches[0].clientY };
            lastTouchRef.current = touch;

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

    if (!src && (!images || images.length === 0)) return null;
    if (!currentImage) return null;

    return createPortal(
        <div
            ref={containerRef}
            className={`fixed inset-0 bg-black z-[9999] flex flex-col touch-none transition-opacity duration-300 ${fadeIn ? 'opacity-100' : 'opacity-0'}`}
        >
            {/* Header with controls */}
            <div className="flex items-center justify-between px-4 py-3 z-10"
                 style={{ background: 'linear-gradient(to bottom, rgba(0,0,0,0.8), transparent)' }}>
                <button
                    onClick={(e) => {
                        e.stopPropagation();
                        onClose();
                    }}
                    className="p-2.5 bg-white/10 hover:bg-white/20 rounded-full transition-all active:scale-90"
                >
                    <X className="w-5 h-5 text-white" />
                </button>

                {/* Image counter */}
                {hasMultiple && (
                    <div className="text-white/80 text-sm font-medium tracking-wide">
                        {currentIndex + 1} / {gallery.length}
                    </div>
                )}

                <div className="flex items-center gap-1.5">
                    <button
                        onClick={(e) => { e.stopPropagation(); zoomOut(); }}
                        disabled={scale <= 1}
                        className="p-2.5 bg-white/10 hover:bg-white/20 rounded-full transition-all disabled:opacity-30 active:scale-90"
                    >
                        <ZoomOut className="w-4 h-4 text-white" />
                    </button>
                    <span className="text-white/70 text-xs min-w-[2.5rem] text-center font-medium">
                        {Math.round(scale * 100)}%
                    </span>
                    <button
                        onClick={(e) => { e.stopPropagation(); zoomIn(); }}
                        disabled={scale >= 5}
                        className="p-2.5 bg-white/10 hover:bg-white/20 rounded-full transition-all disabled:opacity-30 active:scale-90"
                    >
                        <ZoomIn className="w-4 h-4 text-white" />
                    </button>
                    {scale !== 1 && (
                        <button
                            onClick={(e) => { e.stopPropagation(); resetZoom(); }}
                            className="p-2.5 bg-white/10 hover:bg-white/20 rounded-full transition-all ml-0.5 active:scale-90"
                        >
                            <RotateCcw className="w-4 h-4 text-white" />
                        </button>
                    )}
                </div>
            </div>

            {/* Navigation arrows (desktop) */}
            {hasMultiple && currentIndex > 0 && (
                <button
                    onClick={(e) => { e.stopPropagation(); goPrev(); }}
                    className="hidden sm:flex absolute left-3 top-1/2 -translate-y-1/2 z-10 w-11 h-11 items-center justify-center bg-black/40 hover:bg-black/60 rounded-full transition-all active:scale-90 backdrop-blur-sm"
                >
                    <ChevronLeft className="w-6 h-6 text-white" />
                </button>
            )}
            {hasMultiple && currentIndex < gallery.length - 1 && (
                <button
                    onClick={(e) => { e.stopPropagation(); goNext(); }}
                    className="hidden sm:flex absolute right-3 top-1/2 -translate-y-1/2 z-10 w-11 h-11 items-center justify-center bg-black/40 hover:bg-black/60 rounded-full transition-all active:scale-90 backdrop-blur-sm"
                >
                    <ChevronRight className="w-6 h-6 text-white" />
                </button>
            )}

            {/* Image container */}
            <div
                className="flex-1 flex items-center justify-center overflow-hidden relative"
                onClick={(e) => {
                    e.stopPropagation();
                    if (scale <= 1) onClose();
                }}
                onTouchStart={handleTouchStart}
                onTouchMove={handleTouchMove}
                onTouchEnd={handleTouchEnd}
            >
                {/* Loading spinner */}
                {!imageLoaded && (
                    <div className="absolute inset-0 flex items-center justify-center">
                        <div className="w-10 h-10 border-2 border-white/20 border-t-white/80 rounded-full animate-spin" />
                    </div>
                )}

                <img
                    key={currentImage.url}
                    src={currentImage.url}
                    alt={currentImage.name || alt || 'Vollbild'}
                    className={`max-w-full max-h-full object-contain select-none transition-opacity duration-300 ${imageLoaded ? 'opacity-100' : 'opacity-0'}`}
                    style={{
                        transform: `translate(${position.x + swipeOffset}px, ${position.y}px) scale(${scale})`,
                        transition: isDragging || isSwiping ? 'none' : 'transform 0.3s cubic-bezier(0.25, 0.46, 0.45, 0.94), opacity 0.3s ease'
                    }}
                    draggable={false}
                    onClick={(e) => e.stopPropagation()}
                    onLoad={() => setImageLoaded(true)}
                />
            </div>

            {/* Bottom bar: image name + thumbnail strip */}
            <div className="z-10 bg-black">
                {/* Image name */}
                {currentImage.name && (
                    <p className="text-white/60 text-xs text-center px-4 py-1 truncate">
                        {currentImage.name}
                    </p>
                )}

                {/* Thumbnail strip */}
                {hasMultiple && (
                    <div className="flex justify-center gap-1.5 px-4 py-3 overflow-x-auto">
                        {gallery.map((img, idx) => (
                            <button
                                key={idx}
                                onClick={(e) => {
                                    e.stopPropagation();
                                    setCurrentIndex(idx);
                                }}
                                className={`flex-shrink-0 w-12 h-12 rounded-lg overflow-hidden transition-all duration-200 ${
                                    idx === currentIndex
                                        ? 'ring-2 ring-white scale-110 opacity-100'
                                        : 'opacity-50 hover:opacity-80'
                                }`}
                            >
                                <img
                                    src={img.url + '/thumbnail'}
                                    alt=""
                                    className="w-full h-full object-cover"
                                    loading="lazy"
                                    onError={(e) => {
                                        const el = e.target as HTMLImageElement;
                                        if (el.src.includes('/thumbnail')) {
                                            el.src = img.url;
                                        }
                                    }}
                                />
                            </button>
                        ))}
                    </div>
                )}

                {/* Swipe hint */}
                {!hasMultiple && scale === 1 && (
                    <div className="flex justify-center pb-4 pointer-events-none">
                        <span className="text-white/40 text-xs bg-black/30 px-3 py-1.5 rounded-full">
                            Doppeltippen zum Zoomen
                        </span>
                    </div>
                )}
                {hasMultiple && scale === 1 && (
                    <div className="flex justify-center pb-2 pointer-events-none">
                        <span className="text-white/40 text-xs">
                            Wischen zum Blättern
                        </span>
                    </div>
                )}
            </div>
        </div>,
        document.body
    );
};
