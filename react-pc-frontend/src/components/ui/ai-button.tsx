import React, { useId } from 'react';
import { Gem } from 'lucide-react';
import { Button } from './button';
import { cn } from '../../lib/utils';
import { KI_AUS_ERKLAERUNG, useKiFunktionAktiv } from '../../features/ki/useKiAktiv';

interface AiButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
    isLoading?: boolean;
    label?: string;
    /** Die KI-Funktion hinter dem Knopf (z. B. `EMAIL_TEXT`) – ist sie aus, sperrt sich der Knopf. */
    funktion: string;
}

/**
 * Knopf für KI-Aktionen mitten in Formularen (z. B. „KI-Optimierung“ beim
 * E-Mail-Schreiben).
 *
 * <p>Läuft die KI-Funktion gerade nicht (kein Schlüssel, oder der gewählte
 * Anbieter kann sie nicht), sperrt er sich selbst und erklärt warum: per Tooltip beim Drüberfahren und für Screenreader über
 * `aria-describedby`. Der Knopf bleibt sichtbar, damit man sieht, dass es die
 * Funktion gibt. Gesperrte Knöpfe bekommen in manchen Browsern kein Hover –
 * deshalb hängt der Tooltip an einer Hülle und der Knopf selbst lässt die
 * Maus durch (`pointer-events-none`).</p>
 */
export function AiButton({
    isLoading, label = "KI-Optimierung", funktion, className, disabled, onClick, ...props
}: AiButtonProps) {
    const kiAktiv = useKiFunktionAktiv(funktion);
    const erklaerungId = useId();

    if (!kiAktiv) {
        return (
            <span className="inline-flex cursor-not-allowed" title={KI_AUS_ERKLAERUNG}>
                <Button
                    variant="ghost"
                    size="sm"
                    {...props}
                    disabled
                    aria-describedby={erklaerungId}
                    className={cn("pointer-events-none", className, "text-slate-500")}
                >
                    <Gem aria-hidden="true" className="w-4 h-4 mr-2" />
                    {label}
                </Button>
                <span id={erklaerungId} className="sr-only">{KI_AUS_ERKLAERUNG}</span>
            </span>
        );
    }

    return (
        <Button
            variant="ghost"
            size="sm"
            disabled={disabled || isLoading}
            onClick={onClick}
            className={cn("text-rose-600 hover:bg-rose-100 group transition-all", className)}
            {...props}
        >
            {isLoading ? (
                <div className="relative w-4 h-4 mr-2">
                    <Gem className="w-4 h-4 text-rose-600 motion-safe:animate-pulse" />
                    <div className="absolute inset-0 bg-rose-400 rounded-full blur-sm opacity-50 motion-safe:animate-ping"></div>
                </div>
            ) : (
                <Gem className="w-4 h-4 mr-2 group-hover:drop-shadow-sm transition-all" />
            )}
            {label}
        </Button>
    );
}
