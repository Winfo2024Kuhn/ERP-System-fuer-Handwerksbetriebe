import { useId, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import ReactDOM from 'react-dom';
import { Info } from 'lucide-react';
import { cn } from '../../lib/utils';

const BREITE = 384; // 24rem – breit genug, dass längere Erklärungen auf 14 Zoll ohne Scrollen passen
const ABSTAND = 6; // Abstand Popover <-> Knopf
const RAND = 8; // Sicherheitsabstand zum Fensterrand
const FOKUSSIERBAR = 'a[href], button, input, select, textarea, [tabindex]:not([tabindex="-1"]), [contenteditable="true"]';

/**
 * Farbe des (i):
 * - `standard`: graues (i) auf hellen Flächen (Formulare, Karten).
 * - `hell`: helles (i) auf farbigen Flächen, z. B. im roten Kopf der KI-Hilfe.
 */
export type InfoButtonVariante = 'standard' | 'hell';

const VARIANTEN: Record<InfoButtonVariante, { ruhe: string; offen: string }> = {
    standard: {
        ruhe: 'text-slate-400 hover:bg-rose-50 hover:text-rose-600 focus-visible:ring-rose-500',
        offen: 'bg-rose-50 text-rose-600',
    },
    hell: {
        ruhe: 'text-rose-100 hover:bg-white/20 hover:text-white focus-visible:ring-white',
        offen: 'bg-white/20 text-white',
    },
};

export interface InfoButtonProps {
    /** Worum es geht – Überschrift im Popover und Teil des Knopf-Namens („Mehr zu: …“). */
    titel: string;
    children: ReactNode;
    className?: string;
    /** Farbe des (i), siehe {@link InfoButtonVariante}. Standard: `standard`. */
    variante?: InfoButtonVariante;
}

/**
 * Kleines (i) neben einer Überschrift oder einem Feld, das auf Klick genauer
 * erklärt, was im Hintergrund passiert und was eine Einstellung bewirkt.
 *
 * <p>Bewusst Klick statt Hover: So geht es auch per Tastatur und am
 * Touchscreen. Das Popover hängt als Portal am `body` und wird fest
 * positioniert – Tabellen mit `overflow-hidden` schneiden es dadurch nicht ab,
 * und am Fensterrand rückt es nach innen bzw. klappt nach oben.</p>
 *
 * <p>Beim Öffnen wandert der Fokus ins Popover, damit Screenreader den Text
 * vorlesen. Escape, ein zweiter Klick auf das (i) oder ein Klick daneben
 * schließen es; der Fokus kehrt dann zum (i) zurück (außer man hat daneben
 * etwas anderes Bedienbares angeklickt).</p>
 */
export function InfoButton({ titel, children, className, variante = 'standard' }: InfoButtonProps) {
    const id = useId();
    const panelId = `${id}-info`;
    const titelId = `${id}-titel`;
    const [offen, setOffen] = useState(false);
    const [position, setPosition] = useState({ top: 0, left: 0, width: BREITE, maxHeight: 400 });
    const knopfRef = useRef<HTMLButtonElement>(null);
    const panelRef = useRef<HTMLDivElement>(null);

    const schliessen = () => {
        setOffen(false);
        knopfRef.current?.focus();
    };

    useLayoutEffect(() => {
        if (!offen) return;
        const aktualisieren = () => {
            const rect = knopfRef.current?.getBoundingClientRect();
            if (!rect) return;
            const width = Math.min(BREITE, window.innerWidth - RAND * 2);
            const hoehe = panelRef.current?.scrollHeight ?? 0;
            const unten = window.innerHeight - rect.bottom - ABSTAND - RAND;
            const oben = rect.top - ABSTAND - RAND;
            const nachOben = hoehe > unten && oben > unten;
            const maxHeight = Math.max(120, nachOben ? oben : unten);
            const sichtbareHoehe = Math.min(hoehe, maxHeight);
            setPosition({
                top: nachOben ? Math.max(RAND, rect.top - ABSTAND - sichtbareHoehe) : rect.bottom + ABSTAND,
                left: Math.max(RAND, Math.min(rect.left - 12, window.innerWidth - width - RAND)),
                width,
                maxHeight,
            });
        };
        const daneben = (event: MouseEvent) => {
            const ziel = event.target as HTMLElement;
            if (knopfRef.current?.contains(ziel) || panelRef.current?.contains(ziel)) return;
            setOffen(false);
            // Wer daneben etwas Bedienbares anklickt, soll dort landen – sonst zurück zum (i).
            if (!ziel.closest?.(FOKUSSIERBAR)) knopfRef.current?.focus();
        };
        aktualisieren();
        panelRef.current?.focus();
        window.addEventListener('resize', aktualisieren);
        window.addEventListener('scroll', aktualisieren, true);
        document.addEventListener('mousedown', daneben);
        return () => {
            window.removeEventListener('resize', aktualisieren);
            window.removeEventListener('scroll', aktualisieren, true);
            document.removeEventListener('mousedown', daneben);
        };
    }, [offen]);

    return (
        <>
            <button
                ref={knopfRef}
                type="button"
                aria-label={`Mehr zu: ${titel}`}
                title={`Mehr zu: ${titel}`}
                aria-haspopup="dialog"
                aria-expanded={offen}
                aria-controls={offen ? panelId : undefined}
                onClick={() => (offen ? schliessen() : setOffen(true))}
                className={cn(
                    'inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full align-middle transition-colors',
                    'focus-visible:outline-none focus-visible:ring-2',
                    'disabled:cursor-not-allowed disabled:opacity-50',
                    VARIANTEN[variante].ruhe,
                    offen && VARIANTEN[variante].offen,
                    className,
                )}
            >
                <Info aria-hidden="true" className="h-4 w-4" />
            </button>
            {offen && ReactDOM.createPortal(
                <div
                    ref={panelRef}
                    id={panelId}
                    role="dialog"
                    aria-modal="false"
                    aria-labelledby={titelId}
                    tabIndex={-1}
                    className="overflow-auto rounded-lg border border-slate-200 bg-white p-4 text-left text-sm text-slate-600 shadow-2xl focus:outline-none"
                    style={{ position: 'fixed', ...position, zIndex: 99999 }}
                    onKeyDown={(event) => {
                        if (event.key === 'Escape') {
                            event.preventDefault();
                            event.stopPropagation();
                            schliessen();
                        }
                        // Tab verlässt das Popover dort, wo es hingehört: direkt beim (i).
                        if (event.key === 'Tab') {
                            if (event.shiftKey) event.preventDefault();
                            schliessen();
                        }
                    }}
                >
                    <p id={titelId} className="flex items-center gap-2 font-semibold text-slate-900">
                        <Info aria-hidden="true" className="h-4 w-4 shrink-0 text-rose-600" />
                        {titel}
                    </p>
                    <div className="mt-2 space-y-2.5">{children}</div>
                </div>,
                document.body,
            )}
        </>
    );
}

/** Ein Absatz im Info-Popover, z. B. „Was passiert im Hintergrund?“ oder „Was bewirkt das?“. */
export function InfoAbschnitt({ titel, children }: { titel: string; children: ReactNode }) {
    return (
        <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{titel}</p>
            <div className="mt-1 space-y-1">{children}</div>
        </div>
    );
}
