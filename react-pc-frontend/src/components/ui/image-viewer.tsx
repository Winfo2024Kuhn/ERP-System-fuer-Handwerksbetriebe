import React, { useEffect, useState, useCallback } from 'react';
import { X, ChevronLeft, ChevronRight } from 'lucide-react';

interface ImageViewerProps {
    src: string | null;
    alt?: string;
    onClose: () => void;
    /** Optional array of images for gallery navigation */
    images?: { url: string; name?: string }[];
    /** Starting index in the images array */
    startIndex?: number;
}

export const ImageViewer: React.FC<ImageViewerProps> = ({ src, alt, onClose, images, startIndex }) => {
    const [currentIndex, setCurrentIndex] = useState(startIndex ?? 0);

    // Derive gallery from props
    const gallery = images && images.length > 0
        ? images
        : src ? [{ url: src, name: alt }] : [];

    const currentImage = gallery[currentIndex];
    const hasMultiple = gallery.length > 1;

    // Reset index when startIndex changes
    useEffect(() => {
        if (startIndex !== undefined) {
            setCurrentIndex(startIndex);
        }
    }, [startIndex]);

    // Reset index when src changes (single-image mode)
    useEffect(() => {
        if (!images && src) {
            setCurrentIndex(0);
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

    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                onClose();
            } else if (e.key === 'ArrowRight' && hasMultiple) {
                goNext();
            } else if (e.key === 'ArrowLeft' && hasMultiple) {
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
    }, [src, images, onClose, hasMultiple, goNext, goPrev]);

    if (!src && (!images || images.length === 0)) return null;
    if (!currentImage) return null;

    return (
        <div
            className="fixed inset-0 bg-black/80 z-[100] flex items-center justify-center p-8 animate-in fade-in duration-200"
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

            {/* Previous arrow */}
            {hasMultiple && currentIndex > 0 && (
                <button
                    onClick={(e) => { e.stopPropagation(); goPrev(); }}
                    className="absolute left-4 top-1/2 -translate-y-1/2 z-10 w-12 h-12 flex items-center justify-center bg-black/50 hover:bg-white/20 rounded-full transition-all backdrop-blur-sm"
                    title="Vorheriges Bild (←)"
                >
                    <ChevronLeft className="w-7 h-7 text-white" />
                </button>
            )}

            {/* Next arrow */}
            {hasMultiple && currentIndex < gallery.length - 1 && (
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
                className="max-w-full max-h-[90vh] object-contain rounded-lg shadow-2xl animate-in zoom-in-95 duration-200"
                onClick={(e) => e.stopPropagation()}
            />
        </div>
    );
};
