import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { cn } from '../../lib/utils';
import { Schalter } from '../ui/schalter';
import type { Briefform } from './normMasse';

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
