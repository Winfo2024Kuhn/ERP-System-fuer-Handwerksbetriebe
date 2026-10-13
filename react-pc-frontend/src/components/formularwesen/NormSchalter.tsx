import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { cn } from '../../lib/utils';
import type { Briefform } from './normMasse';

/** Kleiner Ein/Aus-Schalter (role="switch"), rose wenn an. */
export function Schalter({ an, onChange, beschriftung }: { an: boolean; onChange: (an: boolean) => void; beschriftung: string }) {
    return (
        <button
            type="button"
            role="switch"
            aria-checked={an}
            onClick={() => onChange(!an)}
            className="group inline-flex items-center gap-2 rounded-md px-1 py-1 text-sm font-medium text-slate-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
        >
            <span className={cn('relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors', an ? 'bg-rose-600' : 'bg-slate-300 group-hover:bg-slate-400')}>
                <span className={cn('inline-block h-4 w-4 rounded-full bg-white shadow-sm transition-transform', an ? 'translate-x-[18px]' : 'translate-x-0.5')} />
            </span>
            {beschriftung}
        </button>
    );
}

/** Umschalter Form A / Form B. */
export function BriefformWahl({ form, onChange }: { form: Briefform; onChange: (form: Briefform) => void }) {
    return (
        <div role="radiogroup" aria-label="Briefform nach DIN 5008" className="inline-flex rounded-lg border border-slate-200 bg-white p-0.5">
            {(['A', 'B'] as const).map(f => (
                <button
                    key={f}
                    type="button"
                    role="radio"
                    aria-checked={form === f}
                    onClick={() => onChange(f)}
                    className={cn(
                        'rounded-md px-2.5 py-1 text-xs font-semibold transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                        form === f ? 'bg-rose-50 text-rose-700' : 'text-slate-500 hover:bg-slate-50 hover:text-slate-700',
                    )}
                >
                    Form {f}
                </button>
            ))}
        </div>
    );
}

export function NormInfo() {
    return (
        <InfoButton titel="Norm-Hilfslinien">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>
                    Die Linien zeigen, wo nach DIN 5008 die Anschrift stehen muss, damit sie im Fenster eines
                    DIN-lang-Umschlags zu sehen ist, und wo gefaltet und gelocht wird. Sie erscheinen nur hier im
                    Editor, nicht im fertigen PDF.
                </p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>
                    Form B ist der übliche Geschäftsbrief (Anschrift weiter unten, mehr Platz für den Briefkopf),
                    Form A setzt die Anschrift höher. Nehmen Sie die Form, für die Ihr Briefpapier gedruckt ist.
                    Die Umstellung verschiebt keine Bausteine.
                </p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

interface NormSchalterProps {
    an: boolean;
    onAn: (an: boolean) => void;
    form: Briefform;
    onForm: (form: Briefform) => void;
}

/** „Norm-Hilfslinien“ an/aus + Form A/B + Erklärung – im Editor und in der Vorschau gleich. */
export function NormSchalter({ an, onAn, form, onForm }: NormSchalterProps) {
    return (
        <div className="flex items-center gap-2">
            <Schalter an={an} onChange={onAn} beschriftung="Norm-Hilfslinien" />
            <BriefformWahl form={form} onChange={onForm} />
            <NormInfo />
        </div>
    );
}
