import { AlertTriangle, CheckCircle2, Info, MoveDownRight } from 'lucide-react';
import { Button } from '../ui/button';
import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { cn } from '../../lib/utils';
import type { AnschriftStatus } from './normMasse';

const TEXTE: Record<AnschriftStatus, string> = {
    passt: 'Anschrift sitzt im Fenster',
    zusatzzone: 'Die Anschrift rutscht in die Zeilen für die Rücksendeangabe. Im Fenster ist sie zu sehen, nach DIN 5008 gehört sie aber darunter.',
    'ragt-heraus': 'Die Anschrift ragt aus dem Fenster – im Umschlag wäre sie teilweise verdeckt.',
    'keine-anschrift': 'Auf Seite 1 gibt es noch keine Anschrift. Fügen Sie links den Baustein „Anschrift“ hinzu – er landet direkt im Fenster.',
};

interface AnschriftHinweisProps {
    status: AnschriftStatus;
    onInsFenster: () => void;
    /** Kompakte Zeile für die Editor-Leiste statt der Karte in der Vorschau. */
    kompakt?: boolean;
    className?: string;
}

/**
 * Zeigt, ob die Anschrift im Fenster des Umschlags sitzt, und bietet die
 * Korrektur an. Gelb = Hinweis, grün = passt.
 */
export function AnschriftHinweis({ status, onInsFenster, kompakt = false, className }: AnschriftHinweisProps) {
    const passt = status === 'passt';
    const keine = status === 'keine-anschrift';
    const Icon = passt ? CheckCircle2 : keine ? Info : AlertTriangle;
    const farbe = passt
        ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
        : keine ? 'border-slate-200 bg-slate-50 text-slate-700' : 'border-amber-200 bg-amber-50 text-amber-900';

    return (
        <div
            data-testid="anschrift-hinweis"
            data-status={status}
            className={cn(
                'flex items-center gap-3 rounded-lg border',
                kompakt ? 'px-2.5 py-1.5 text-xs' : 'px-4 py-3 text-sm',
                farbe,
                className,
            )}
        >
            <Icon aria-hidden="true" className={cn('shrink-0', kompakt ? 'h-4 w-4' : 'h-5 w-5', passt ? 'text-emerald-600' : keine ? 'text-slate-400' : 'text-amber-500')} />
            <p role="status" className={cn('min-w-0 flex-1', passt && 'font-medium')}>{TEXTE[status]}</p>
            {!passt && !keine && (
                <span className="flex shrink-0 items-center gap-1">
                    <Button size="sm" variant="outline" onClick={onInsFenster} className={cn('bg-white', kompakt && 'h-7 px-2 text-xs')}>
                        <MoveDownRight aria-hidden="true" className="mr-1 h-4 w-4" />Anschrift ins Fenster setzen
                    </Button>
                    <InfoButton titel="Anschrift ins Fenster setzen">
                        <InfoAbschnitt titel="Was passiert im Hintergrund?">
                            <p>Setzt den Anschrift-Baustein genau auf die Anschriftzone nach DIN 5008.</p>
                        </InfoAbschnitt>
                        <InfoAbschnitt titel="Was bewirkt das?">
                            <p>Gilt für diese Vorlage und damit für alle Dokumente, die sie verwenden. Mit Rückgängig holen Sie die alte Position zurück.</p>
                        </InfoAbschnitt>
                    </InfoButton>
                </span>
            )}
        </div>
    );
}
