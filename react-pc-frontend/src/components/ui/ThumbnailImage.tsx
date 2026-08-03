import { useState } from 'react';
import { ImageOff } from 'lucide-react';
import { cn } from '../../lib/utils';

interface ThumbnailImageProps {
    /** Verkleinertes Vorschaubild vom Server (max. 300 px). */
    src: string;
    /** Original-Bild. Wird nur geladen, wenn das Vorschaubild fehlschlägt. */
    fallbackSrc?: string;
    alt: string;
    /** Zusätzliche Klassen für das <img> selbst. */
    className?: string;
}

type LoadState = 'loading' | 'loaded' | 'failed';

/**
 * Bild-Kachel mit Skeleton-Ladeanzeige.
 *
 * Lädt das kleine Vorschaubild statt des Originals — bei Handy-Fotos spart das
 * pro Kachel schnell mehrere Megabyte. Solange das Bild unterwegs ist, steht ein
 * pulsierendes graues Feld an seiner Stelle, damit die Kachel nicht leer wirkt
 * und das Layout nicht springt.
 *
 * Schlägt das Vorschaubild fehl (z. B. Format, das der Server nicht verkleinern
 * kann), wird einmalig auf das Original zurückgefallen.
 */
export function ThumbnailImage({ src, fallbackSrc, alt, className }: ThumbnailImageProps) {
    const [currentSrc, setCurrentSrc] = useState(src);
    const [state, setState] = useState<LoadState>('loading');
    const [letzteQuelle, setLetzteQuelle] = useState(src);

    // Beim Wechsel der Quelle (z. B. anderes Bild in derselben Kachel) neu starten.
    // Anpassung während des Renderns statt in einem Effect: React rendert direkt neu,
    // ohne dass für einen Frame noch das alte Bild zu sehen ist.
    if (src !== letzteQuelle) {
        setLetzteQuelle(src);
        setCurrentSrc(src);
        setState('loading');
    }

    const handleError = () => {
        if (fallbackSrc && currentSrc !== fallbackSrc) {
            setCurrentSrc(fallbackSrc);
            return;
        }
        setState('failed');
    };

    if (state === 'failed') {
        return (
            <div
                className="w-full h-full flex items-center justify-center bg-slate-100 rounded"
                role="img"
                aria-label={alt}
                title={alt}
            >
                <ImageOff className="w-6 h-6 text-slate-400" aria-hidden="true" />
            </div>
        );
    }

    return (
        <div className="relative w-full h-full">
            {state === 'loading' && (
                <div
                    className="absolute inset-0 bg-slate-200 motion-safe:animate-pulse rounded"
                    aria-hidden="true"
                />
            )}
            <img
                src={currentSrc}
                alt={alt}
                loading="lazy"
                decoding="async"
                onLoad={() => setState('loaded')}
                onError={handleError}
                className={cn(
                    'w-full h-full motion-safe:transition-opacity motion-safe:duration-200',
                    state === 'loaded' ? 'opacity-100' : 'opacity-0',
                    className
                )}
            />
        </div>
    );
}

export default ThumbnailImage;
