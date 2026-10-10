import { useMemo } from 'react';
import { Loader2 } from 'lucide-react';
import { Select } from '../../components/ui/select-custom';
import { cn } from '../../lib/utils';
import { formatPostfach, type AbsenderPostfach, type PostfachKurz } from './postfach';

interface Gemeinsam {
    id: string;
    /** Kompakte Darstellung im E-Mail-Center: Hinweise laufen über beide Grid-Spalten. */
    inline?: boolean;
}

interface Auswahl extends Gemeinsam {
    modus: 'auswahl';
    postfaecher: AbsenderPostfach[];
    value: number | null;
    onChange: (postfachId: number) => void;
    laedt?: boolean;
    ladeFehler?: boolean;
}

interface Fest extends Gemeinsam {
    modus: 'fest';
    /** Postfach bzw. Adresse, über die die Mail fest rausgeht. `undefined` = wird noch ermittelt. */
    postfach: PostfachKurz | string | null | undefined;
    /** Erklärt, warum sich der Absender hier nicht ändern lässt. */
    hinweis?: string;
}

export type AbsenderPostfachAuswahlProps = Auswahl | Fest;

const hinweisKlasse = (inline?: boolean) => cn('text-xs text-slate-500', inline && 'col-span-2');

function zusatz(postfach: AbsenderPostfach): string {
    if (postfach.eigenes) return ' – Ihr Postfach';
    if (postfach.hauptpostfach) return ' – Hauptpostfach';
    return '';
}

/**
 * „Von“ im Schreiben-Fenster.
 *
 * <ul>
 *   <li><b>auswahl</b> – neue Mail: „Senden von“ aus den Postfächern des Betriebs.</li>
 *   <li><b>fest</b> – Antworten, Allen antworten, Weiterleiten und
 *       Geschäftsdokumente: nur eine Textzeile, das Backend legt das Postfach fest
 *       (das, in dem die Mail ankam bzw. das Rechnungs-Postfach).</li>
 * </ul>
 */
export function AbsenderPostfachAuswahl(props: AbsenderPostfachAuswahlProps) {
    const auswahlPostfaecher = props.modus === 'auswahl' ? props.postfaecher : null;
    const optionen = useMemo(
        () => (auswahlPostfaecher ?? []).map(p => ({ value: String(p.id), label: `${formatPostfach(p)}${zusatz(p)}` })),
        [auswahlPostfaecher],
    );

    if (props.modus === 'fest') {
        const { postfach } = props;
        const text = postfach === undefined
            ? null
            : postfach === null
                ? 'Standard-Absender des Betriebs'
                : typeof postfach === 'string' ? postfach : formatPostfach(postfach);
        return (
            <>
                <p
                    id={props.id}
                    data-testid="absender-fest"
                    aria-describedby={props.hinweis ? `${props.id}-hinweis` : undefined}
                    className="min-w-0 break-all rounded border border-slate-100 bg-slate-50 px-3 py-2 text-sm text-slate-700"
                >
                    {text ?? (
                        <span className="inline-flex items-center gap-2 text-slate-500">
                            <Loader2 className="h-3.5 w-3.5 motion-safe:animate-spin" aria-hidden="true" />
                            Absender wird ermittelt …
                        </span>
                    )}
                </p>
                {props.hinweis && <p id={`${props.id}-hinweis`} className={hinweisKlasse(props.inline)}>{props.hinweis}</p>}
            </>
        );
    }

    const { value, onChange, laedt, ladeFehler, inline } = props;
    // Nur ein Postfach: nichts zu wählen – wie eine feste Zeile anzeigen statt eines grauen Feldes.
    if (props.postfaecher.length === 1) {
        return (
            <p id={props.id} data-testid="absender-fest"
                className="min-w-0 break-all rounded border border-slate-100 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                {formatPostfach(props.postfaecher[0])}
            </p>
        );
    }
    // Kein Postfach zur Auswahl (keins freigegeben oder noch keins eingerichtet): statt eines
    // leeren, grauen Auswahlfelds klar sagen, worüber die Mail rausgeht.
    if (!laedt && !ladeFehler && optionen.length === 0) {
        return (
            <>
                <p id={props.id} data-testid="absender-fest" aria-describedby={`${props.id}-hinweis`}
                    className="min-w-0 rounded border border-slate-100 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                    Standard-Absender des Betriebs
                </p>
                <p id={`${props.id}-hinweis`} className={hinweisKlasse(inline)}>
                    Kein Postfach zur Auswahl – die Mail geht über den Standard-Absender des Betriebs raus.
                    Welche Postfächer Sie nutzen dürfen, legt Ihr Admin fest.
                </p>
            </>
        );
    }
    const hinweis = ladeFehler
        ? 'Die Postfächer konnten nicht geladen werden. Die Mail geht über Ihr eigenes Postfach bzw. das Hauptpostfach raus.'
        : null;
    return (
        <>
            <Select
                id={props.id}
                options={optionen}
                value={value != null ? String(value) : ''}
                onChange={(wert) => onChange(Number(wert))}
                placeholder={laedt ? 'Postfächer werden geladen …' : 'Postfach wählen'}
                disabled={optionen.length === 0}
                aria-label="Senden von"
                aria-describedby={hinweis ? `${props.id}-hinweis` : undefined}
            />
            {hinweis && <p id={`${props.id}-hinweis`} className={cn(hinweisKlasse(inline), ladeFehler && 'text-rose-700')}>{hinweis}</p>}
        </>
    );
}
