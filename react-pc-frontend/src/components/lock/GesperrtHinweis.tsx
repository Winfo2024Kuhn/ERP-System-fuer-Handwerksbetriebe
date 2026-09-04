import { Lock } from 'lucide-react';
import { cn } from '../../lib/utils';

export interface GesperrtHinweisProps {
    /**
     * Anzeigename des Kollegen, der den Datensatz gerade bearbeitet.
     * Fehlt der Wert (oder ist er nur Leerraum), zeigt die Zeile den
     * generischen Fallback "Ein Kollege".
     */
    halterName?: string;
    /**
     * Optionale Minutenangabe fuer den Zusatz "Seit {x} Min.". Wird als
     * fertig formatierter Wert uebergeben (z.B. "5"), nicht als Zahl --
     * die Berechnung "seit wann" liegt beim Aufrufer (useDatensatzLock).
     */
    seit?: string;
    className?: string;
}

/**
 * Ruhige, einzeilige Hinweiszeile fuer den Nur-Lesen-Zustand, wenn ein
 * anderer Nutzer denselben Datensatz gerade bearbeitet. Bewusst KEIN Modal
 * und KEIN Overlay -- der Nutzer soll ungehindert weiterlesen koennen. Eine
 * eigene Aktion bietet der Baustein nicht an (kein Button); die gehoert in
 * BearbeitenLeiste. Loest das bisherige, hart blockierende
 * DocumentLockedModal ab.
 */
export function GesperrtHinweis({ halterName, seit, className }: GesperrtHinweisProps) {
    const wer = halterName?.trim() || 'Ein Kollege';
    const zusatz = seit ? ` Seit ${seit} Min.` : '';

    return (
        <div
            role="status"
            aria-live="polite"
            className={cn(
                'flex items-center gap-2 bg-rose-50 border border-rose-100 text-slate-700 rounded-xl px-3 py-2 text-sm',
                className,
            )}
        >
            <Lock className="w-4 h-4 text-rose-600 shrink-0" aria-hidden="true" />
            <span>
                <span className="font-semibold text-slate-900">{wer}</span>
                {` bearbeitet das gerade — Sie sehen den aktuellen Stand.${zusatz}`}
            </span>
        </div>
    );
}

export default GesperrtHinweis;
