import { useCallback, useEffect, useId, useState } from 'react';
import { Palmtree, Scale } from 'lucide-react';
import { Button } from '../../../ui/button';
import { Select } from '../../../ui/select-custom';
import { useToast } from '../../../ui/toast';
import { SaveButton, SectionLoading, SettingsCard } from '../../settingsUi';
import { cn } from '../../../../lib/utils';
import { zeiterfassungApi, type UrlaubsverfallRegel } from './zeiterfassungApi';
import { ResturlaubInfo } from './ZeiterfassungInfos';

const STANDARD_REGEL: UrlaubsverfallRegel = { verfaellt: true, tag: 31, monat: 3 };

const MONATE = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
const MONAT_OPTIONEN = MONATE.map((name, i) => ({ value: String(i + 1), label: name }));
/** Tage je Monat; der 29. Februar wird in Jahren ohne Schalttag zum 28. */
const TAGE_IM_MONAT = [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];

const meldung = (err: unknown, fallback: string) => (err instanceof Error && err.message ? err.message : fallback);

function verfallsTagText(regel: UrlaubsverfallRegel): string {
    return `${regel.tag}. ${MONATE[regel.monat - 1] ?? ''}`;
}

/** Prüft die Regel vor dem Speichern – gibt den Fehlertext oder `''` zurück. */
function pruefeRegel(regel: UrlaubsverfallRegel): string {
    const max = TAGE_IM_MONAT[regel.monat - 1];
    if (!max || regel.tag < 1 || regel.tag > max) return 'Diesen Tag gibt es nicht. Bitte ein gültiges Datum wählen.';
    return '';
}

/** Unvollständige Antworten fallen auf den gesetzlichen Standard zurück – wie im Backend. */
function bereinige(regel: Partial<UrlaubsverfallRegel> | null | undefined): UrlaubsverfallRegel {
    const kandidat = {
        verfaellt: regel?.verfaellt !== false,
        tag: Number.isInteger(regel?.tag) ? Number(regel?.tag) : STANDARD_REGEL.tag,
        monat: Number.isInteger(regel?.monat) ? Number(regel?.monat) : STANDARD_REGEL.monat,
    };
    return pruefeRegel(kandidat) ? { ...STANDARD_REGEL, verfaellt: kandidat.verfaellt } : kandidat;
}

const gleich = (a: UrlaubsverfallRegel, b: UrlaubsverfallRegel) =>
    a.verfaellt === b.verfaellt && a.tag === b.tag && a.monat === b.monat;

/**
 * Einstellungen → Zeiterfassung: Was passiert mit Urlaub, der im alten Jahr
 * übrig bleibt? Voreinstellung ist die gesetzliche Regel (Übertrag, Verfall am
 * 31. März). Der Betrieb kann einen anderen Tag wählen oder den Verfall abschalten.
 */
export function ResturlaubKarte({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const name = useId();
    const [laedt, setLaedt] = useState(true);
    const [ladeFehler, setLadeFehler] = useState('');
    const [gespeichert, setGespeichert] = useState<UrlaubsverfallRegel>(STANDARD_REGEL);
    const [entwurf, setEntwurf] = useState<UrlaubsverfallRegel>(STANDARD_REGEL);
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');

    const laden = useCallback(async () => {
        setLaedt(true);
        setLadeFehler('');
        try {
            const regel = bereinige(await zeiterfassungApi.ladeUrlaubsverfall());
            setGespeichert(regel);
            setEntwurf(regel);
            setFehler('');
        } catch (err) {
            const text = meldung(err, 'Die Regel für Resturlaub konnte nicht geladen werden.');
            setLadeFehler(text);
            toast.error(text);
        } finally {
            setLaedt(false);
        }
    }, [toast]);

    useEffect(() => { void laden(); }, [laden]);

    const geaendert = !gleich(entwurf, gespeichert);

    const aendern = (aenderung: Partial<UrlaubsverfallRegel>) => {
        setEntwurf(alt => ({ ...alt, ...aenderung }));
        setFehler('');
    };

    const speichern = async () => {
        if (speichert || !geaendert) return;
        const problem = pruefeRegel(entwurf);
        if (problem) {
            setFehler(problem);
            toast.error(problem);
            return;
        }
        setSpeichert(true);
        try {
            const regel = bereinige(await zeiterfassungApi.speichereUrlaubsverfall(entwurf));
            setGespeichert(regel);
            setEntwurf(regel);
            toast.success('Regel für Resturlaub gespeichert.');
            onSaved?.();
        } catch (err) {
            const text = meldung(err, 'Die Regel für Resturlaub konnte nicht gespeichert werden.');
            setFehler(text);
            toast.error(text);
        } finally {
            setSpeichert(false);
        }
    };

    const tagOptionen = Array.from({ length: TAGE_IM_MONAT[entwurf.monat - 1] ?? 31 }, (_, i) => ({ value: String(i + 1), label: `${i + 1}.` }));

    const optionen = [
        {
            verfaellt: true,
            titel: 'Resturlaub wird übertragen und verfällt',
            text: 'Nicht genommener Urlaub geht ins neue Jahr mit und muss bis zum gewählten Tag genommen werden.',
            standard: true,
        },
        {
            verfaellt: false,
            titel: 'Resturlaub verfällt nicht',
            text: 'Nicht genommener Urlaub geht ins neue Jahr mit und bleibt erhalten, bis er genommen ist.',
            standard: false,
        },
    ];

    return (
        <SettingsCard
            icon={<Palmtree aria-hidden="true" className="w-5 h-5 text-rose-600" />}
            title="Resturlaub ins neue Jahr"
            info={<ResturlaubInfo />}
            description={
                <p>
                    Was passiert mit Urlaub, der am Jahresende übrig ist? Er wird ins neue Jahr übertragen.
                    Hier legen Sie fest, ob und wann er dort verfällt.
                </p>
            }
        >
            {laedt ? (
                <SectionLoading />
            ) : ladeFehler ? (
                <div role="alert" className="flex flex-wrap items-center gap-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                    {ladeFehler}
                    <Button variant="outline" size="sm" onClick={() => void laden()}>Erneut laden</Button>
                </div>
            ) : (
                <fieldset disabled={speichert} aria-label="Regel für Resturlaub" className="min-w-0 space-y-4">
                    <div role="radiogroup" aria-label="Verfällt Resturlaub?" className="grid max-w-4xl gap-3 md:grid-cols-2">
                        {optionen.map(option => {
                            const gewaehlt = entwurf.verfaellt === option.verfaellt;
                            return (
                                <label key={String(option.verfaellt)} className={cn(
                                    'flex cursor-pointer items-start gap-3 rounded-lg border p-4 transition-colors focus-within:ring-2 focus-within:ring-rose-500',
                                    gewaehlt ? 'border-rose-300 bg-rose-50/60' : 'border-slate-200 bg-white hover:bg-slate-50',
                                )}>
                                    <input type="radio" name={name} checked={gewaehlt} onChange={() => aendern({ verfaellt: option.verfaellt })}
                                        className="mt-0.5 h-4 w-4 shrink-0 accent-rose-600 focus:outline-none" />
                                    <span>
                                        <span className="font-medium text-slate-900">{option.titel}</span>
                                        {option.standard && <span className="ml-2 inline-block whitespace-nowrap rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">Gesetzlicher Standard</span>}
                                        <span className="mt-1 block text-sm text-slate-500">{option.text}</span>
                                    </span>
                                </label>
                            );
                        })}
                    </div>

                    {entwurf.verfaellt && (
                        <div className="max-w-4xl">
                            <p className="mb-1.5 block text-sm text-slate-800">Verfällt im Folgejahr nach dem</p>
                            <div className="flex flex-wrap items-center gap-2">
                                <Select aria-label="Verfallstag" className="w-28" value={String(entwurf.tag)} options={tagOptionen}
                                    onChange={tag => aendern({ tag: Number(tag) })} disabled={speichert} />
                                <Select aria-label="Verfallsmonat" className="w-44" value={String(entwurf.monat)} options={MONAT_OPTIONEN}
                                    onChange={monat => {
                                        const neu = Number(monat);
                                        aendern({ monat: neu, tag: Math.min(entwurf.tag, TAGE_IM_MONAT[neu - 1] ?? 31) });
                                    }} disabled={speichert} />
                                {!gleich({ ...entwurf, verfaellt: true }, STANDARD_REGEL) && (
                                    <Button variant="ghost" size="sm" onClick={() => aendern({ tag: STANDARD_REGEL.tag, monat: STANDARD_REGEL.monat })}>
                                        Auf 31. März zurücksetzen
                                    </Button>
                                )}
                            </div>
                        </div>
                    )}

                    <div className="flex max-w-4xl items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                        <Scale aria-hidden="true" className="mt-0.5 h-4 w-4 shrink-0" />
                        <p>
                            Gesetzlich verfällt Resturlaub am 31. März – aber nur, wenn Sie Ihre Leute vorher rechtzeitig
                            darauf hingewiesen haben, dass noch Urlaub offen ist und er sonst verfällt. Die Handy-App
                            erinnert zwei Monate vorher daran; ein schriftlicher Hinweis vom Betrieb ist trotzdem sicherer.
                        </p>
                    </div>

                    {fehler && <p role="alert" className="text-sm text-rose-700">{fehler}</p>}
                    <p className="text-sm text-slate-600">
                        {gespeichert.verfaellt
                            ? `Gespeichert: Resturlaub verfällt nach dem ${verfallsTagText(gespeichert)} des Folgejahres.`
                            : 'Gespeichert: Resturlaub verfällt nicht.'}
                    </p>
                    <SaveButton
                        onClick={() => void speichern()}
                        saving={speichert}
                        disabled={!geaendert}
                        title={!geaendert ? 'Keine Änderung zum Speichern.' : undefined}
                    >
                        Regel speichern
                    </SaveButton>
                </fieldset>
            )}
        </SettingsCard>
    );
}
