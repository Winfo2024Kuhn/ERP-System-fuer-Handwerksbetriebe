import { useCallback, useEffect, useId, useRef, useState } from 'react';
import type React from 'react';
import { Loader2, Pause, Play } from 'lucide-react';
import { useToast } from '../../components/ui/toast';
import { toSafeResourceUrl } from '../../lib/htmlSanitizer';
import { cn } from '../../lib/utils';
import { formatSekunden } from './format';

/**
 * Eigener Abspieler für Anrufbeantworter-Nachrichten im Design-System:
 * runder Abspiel-Knopf, Fortschrittsbalken zum Spulen (Maus, Finger und
 * Pfeiltasten) und Zeitangabe.
 *
 * <p>Der native `<audio controls>` sähe in jedem Browser anders aus und
 * passt nicht zu den übrigen Eingaben – deshalb läuft das Element
 * unsichtbar und die Bedienung ist selbst gebaut.</p>
 *
 * <p>Es spielt immer nur eine Nachricht: Startet eine andere, hält diese
 * an. Die Aufnahme wird erst beim ersten Abspielen geladen
 * (`preload="none"`), sonst würde eine lange Liste alle Dateien auf einmal
 * holen.</p>
 */

const WIEDERGABE_EREIGNIS = 'telefon:wiedergabe';
const SPRUNG_SEKUNDEN = 5;

interface AudioPlayerProps {
    src: string;
    /** Länge laut Server – gilt, bis der Browser die echte Länge kennt. */
    dauerSekunden?: number;
    /** Wofür der Player steht, z. B. „Nachricht von Max Mustermann" (für Screenreader). */
    beschriftung: string;
    /** Wird bei jedem Start der Wiedergabe aufgerufen. */
    onWiedergabeStart?: () => void;
    className?: string;
}

export function AudioPlayer({ src, dauerSekunden = 0, beschriftung, onWiedergabeStart, className }: AudioPlayerProps) {
    const toast = useToast();
    const kennung = useId();
    const audioRef = useRef<HTMLAudioElement>(null);
    const balkenRef = useRef<HTMLDivElement>(null);
    const ziehtRef = useRef(false);
    const [spielt, setSpielt] = useState(false);
    const [laedt, setLaedt] = useState(false);
    const [position, setPosition] = useState(0);
    const [echteDauer, setEchteDauer] = useState<number | null>(null);
    const dauer = echteDauer && Number.isFinite(echteDauer) ? echteDauer : dauerSekunden;
    const sichereQuelle = toSafeResourceUrl(src);

    // Startet woanders eine Wiedergabe, hält dieser Player an.
    useEffect(() => {
        const beiFremderWiedergabe = (ereignis: Event) => {
            const detail = (ereignis as CustomEvent<string>).detail;
            if (detail !== kennung) audioRef.current?.pause();
        };
        window.addEventListener(WIEDERGABE_EREIGNIS, beiFremderWiedergabe);
        return () => window.removeEventListener(WIEDERGABE_EREIGNIS, beiFremderWiedergabe);
    }, [kennung]);

    const springeZu = useCallback((sekunden: number) => {
        const ziel = Math.min(Math.max(0, sekunden), dauer || 0);
        if (audioRef.current) audioRef.current.currentTime = ziel;
        setPosition(ziel);
    }, [dauer]);

    const umschalten = async () => {
        const audio = audioRef.current;
        if (!audio) return;
        if (spielt) {
            audio.pause();
            setSpielt(false);
            return;
        }
        window.dispatchEvent(new CustomEvent(WIEDERGABE_EREIGNIS, { detail: kennung }));
        setLaedt(true);
        try {
            await audio.play();
            setSpielt(true);
            onWiedergabeStart?.();
        } catch {
            setSpielt(false);
            toast.error('Die Nachricht konnte nicht abgespielt werden.');
        } finally {
            setLaedt(false);
        }
    };

    const positionAusZeiger = (clientX: number) => {
        const balken = balkenRef.current;
        if (!balken || !dauer) return;
        const rahmen = balken.getBoundingClientRect();
        const anteil = rahmen.width > 0 ? (clientX - rahmen.left) / rahmen.width : 0;
        springeZu(Math.min(1, Math.max(0, anteil)) * dauer);
    };

    const beiTaste = (ereignis: React.KeyboardEvent) => {
        const schritte: Record<string, number> = {
            ArrowRight: SPRUNG_SEKUNDEN, ArrowUp: SPRUNG_SEKUNDEN,
            ArrowLeft: -SPRUNG_SEKUNDEN, ArrowDown: -SPRUNG_SEKUNDEN,
        };
        if (ereignis.key in schritte) {
            ereignis.preventDefault();
            springeZu(position + schritte[ereignis.key]);
        } else if (ereignis.key === 'Home') {
            ereignis.preventDefault();
            springeZu(0);
        } else if (ereignis.key === 'End') {
            ereignis.preventDefault();
            springeZu(dauer);
        }
    };

    const anteil = dauer > 0 ? Math.min(100, (position / dauer) * 100) : 0;
    const SymbolKnopf = laedt ? Loader2 : spielt ? Pause : Play;

    return (
        <div className={cn('flex items-center gap-3 min-w-0', className)}>
            <audio
                ref={audioRef}
                src={sichereQuelle || undefined}
                preload="none"
                onTimeUpdate={(e) => { if (!ziehtRef.current) setPosition(e.currentTarget.currentTime); }}
                onLoadedMetadata={(e) => setEchteDauer(e.currentTarget.duration)}
                onPause={() => setSpielt(false)}
                onPlay={() => setSpielt(true)}
                onEnded={() => { setSpielt(false); setPosition(0); }}
            />
            <button
                type="button"
                onClick={umschalten}
                disabled={laedt || !sichereQuelle}
                aria-label={`${spielt ? 'Anhalten' : 'Abspielen'}: ${beschriftung}`}
                title={spielt ? 'Anhalten' : 'Abspielen'}
                className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-rose-600 text-white shadow-sm transition-colors hover:bg-rose-700 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:ring-offset-2 disabled:opacity-60"
            >
                <SymbolKnopf aria-hidden="true" className={cn('h-4 w-4', laedt && 'motion-safe:animate-spin', !spielt && !laedt && 'ml-0.5')} />
            </button>
            <div
                ref={balkenRef}
                role="slider"
                tabIndex={0}
                aria-label={`Position in der Nachricht: ${beschriftung}`}
                aria-valuemin={0}
                aria-valuemax={Math.round(dauer)}
                aria-valuenow={Math.round(position)}
                aria-valuetext={`${formatSekunden(position)} von ${formatSekunden(dauer)}`}
                onKeyDown={beiTaste}
                onPointerDown={(e) => {
                    ziehtRef.current = true;
                    e.currentTarget.setPointerCapture?.(e.pointerId);
                    positionAusZeiger(e.clientX);
                }}
                onPointerMove={(e) => { if (ziehtRef.current) positionAusZeiger(e.clientX); }}
                onPointerUp={(e) => {
                    ziehtRef.current = false;
                    e.currentTarget.releasePointerCapture?.(e.pointerId);
                }}
                onPointerCancel={() => { ziehtRef.current = false; }}
                className="group relative flex h-6 min-w-[6rem] flex-1 cursor-pointer items-center rounded focus:outline-none focus:ring-2 focus:ring-rose-500"
            >
                <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-200">
                    <div className="h-full rounded-full bg-rose-500" style={{ width: `${anteil}%` }} />
                </div>
                <div
                    aria-hidden="true"
                    className="absolute top-1/2 h-3.5 w-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-white bg-rose-600 shadow transition-transform group-hover:scale-110"
                    style={{ left: `${anteil}%` }}
                />
            </div>
            <span className="shrink-0 text-xs tabular-nums text-slate-500 whitespace-nowrap">
                {formatSekunden(position)} / {formatSekunden(dauer)}
            </span>
        </div>
    );
}
