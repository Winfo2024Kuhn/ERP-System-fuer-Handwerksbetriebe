import { useEffect, useState, type ReactNode } from 'react';
import { ImageOff } from 'lucide-react';
import { cn } from '../../lib/utils';

interface ThumbnailImageProps {
    /** Verkleinertes Vorschaubild vom Server (Größe legt der jeweilige Endpunkt fest). */
    src: string;
    /** Original-Bild. Wird nur geladen, wenn das Vorschaubild fehlschlägt. */
    fallbackSrc?: string;
    alt: string;
    /** Zusätzliche Klassen für das <img> selbst. */
    className?: string;
    /** Was bei Fehler statt des `ImageOff`-Kästchens erscheint, z. B. das Symbol der Dateiart. */
    ersatz?: ReactNode;
    /**
     * Nach einem Fehler so oft nach `nachMs` erneut laden (mit `?v=<versuch>`), z. B. wenn der
     * Server gerade ausgelastet ist (503). Ein `<img>` sieht den Statuscode nicht, also gilt das
     * für jeden Fehler – begrenzt, nie endlos.
     */
    wiederholen?: { anzahl: number; nachMs: number };
}

type LoadState = 'loading' | 'loaded' | 'waiting' | 'failed';

/** Hängt die Versuchsnummer an, damit der Browser wirklich neu fragt. */
function mitVersuch(src: string, versuch: number): string {
    return `${src}${src.includes('?') ? '&' : '?'}v=${versuch}`;
}

/**
 * Bild-Kachel mit Skeleton-Ladeanzeige.
 *
 * Lädt das kleine Vorschaubild statt des Originals — bei Handy-Fotos spart das
 * pro Kachel schnell mehrere Megabyte. Solange das Bild unterwegs ist, steht ein
 * pulsierendes graues Feld an seiner Stelle, damit die Kachel nicht leer wirkt
 * und das Layout nicht springt.
 *
 * Schlägt das Vorschaubild fehl (z. B. Format, das der Server nicht verkleinern
 * kann), wird einmalig auf das Original zurückgefallen – nur, wenn `fallbackSrc`
 * gesetzt ist. Mit `wiederholen` wird vorher begrenzt erneut versucht (Skeleton
 * bleibt stehen). Danach zeigt die Kachel `ersatz` bzw. ein `ImageOff`-Kästchen.
 */
export function ThumbnailImage({ src, fallbackSrc, alt, className, ersatz, wiederholen }: ThumbnailImageProps) {
    const [currentSrc, setCurrentSrc] = useState(src);
    const [state, setState] = useState<LoadState>('loading');
    const [letzteQuelle, setLetzteQuelle] = useState(src);
    const [versuch, setVersuch] = useState(0);
    const warteMs = wiederholen?.nachMs ?? 0;

    useEffect(() => {
        if (state !== 'waiting') return;
        const uhr = setTimeout(() => {
            setCurrentSrc(mitVersuch(src, versuch));
            setState('loading');
        }, warteMs);
        return () => clearTimeout(uhr);
    }, [state, src, versuch, warteMs]);

    // Beim Wechsel der Quelle (z. B. anderes Bild in derselben Kachel) neu starten.
    // Anpassung während des Renderns statt in einem Effect: React rendert direkt neu,
    // ohne dass für einen Frame noch das alte Bild zu sehen ist.
    if (src !== letzteQuelle) {
        setLetzteQuelle(src);
        setCurrentSrc(src);
        setState('loading');
        setVersuch(0);
    }

    const handleError = () => {
        if (fallbackSrc && currentSrc !== fallbackSrc) {
            setCurrentSrc(fallbackSrc);
            return;
        }
        if (wiederholen && versuch < wiederholen.anzahl) {
            setVersuch(versuch + 1);
            setState('waiting');
            return;
        }
        setState('failed');
    };

    if (state === 'failed') {
        if (ersatz !== undefined) return <>{ersatz}</>;
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
            {(state === 'loading' || state === 'waiting') && (
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
