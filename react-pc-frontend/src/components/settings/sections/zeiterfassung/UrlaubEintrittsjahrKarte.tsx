import { useCallback, useEffect, useId, useState } from 'react';
import { UserPlus } from 'lucide-react';
import { Button } from '../../../ui/button';
import { useToast } from '../../../ui/toast';
import { SaveButton, SectionLoading, SettingsCard } from '../../settingsUi';
import { cn } from '../../../../lib/utils';
import { zeiterfassungApi } from './zeiterfassungApi';
import { UrlaubEintrittsjahrInfo } from './ZeiterfassungInfos';

const meldung = (err: unknown, fallback: string) => (err instanceof Error && err.message ? err.message : fallback);

const OPTIONEN = [
    {
        anteilig: false,
        titel: 'Voller Urlaub bei Eintritt bis 1. Juli',
        text: 'Wer bis zum 1. Juli anfängt, bekommt im ersten Jahr den vollen Jahresurlaub. Bei späterem Eintritt ein Zwölftel je vollem Monat.',
        standard: true,
    },
    {
        anteilig: true,
        titel: 'Immer anteilig (laut Vertrag oder Tarif)',
        text: 'Im ersten Jahr gibt es immer ein Zwölftel je vollem Monat – bei Eintritt bis 1. Juli aber mindestens den gesetzlichen Mindesturlaub.',
        standard: false,
    },
];

/**
 * Einstellungen → Zeiterfassung: Wie viel Urlaub gibt es im Jahr, in dem jemand
 * anfängt? Voreinstellung ist das Gesetz (voller Urlaub bei Eintritt bis 01.07.).
 * Nur wenn Arbeitsvertrag oder Tarif das Zwölfteln vorsehen, stellt der Betrieb
 * auf „immer anteilig“ um.
 */
export function UrlaubEintrittsjahrKarte({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const name = useId();
    const [laedt, setLaedt] = useState(true);
    const [ladeFehler, setLadeFehler] = useState('');
    const [gespeichert, setGespeichert] = useState(false);
    const [entwurf, setEntwurf] = useState(false);
    const [speichert, setSpeichert] = useState(false);

    const laden = useCallback(async () => {
        setLaedt(true);
        setLadeFehler('');
        try {
            const regel = await zeiterfassungApi.ladeUrlaubEintrittsjahr();
            const anteilig = regel?.mehrurlaubAnteilig === true;
            setGespeichert(anteilig);
            setEntwurf(anteilig);
        } catch (err) {
            const text = meldung(err, 'Die Regel für den Urlaub im Eintrittsjahr konnte nicht geladen werden.');
            setLadeFehler(text);
            toast.error(text);
        } finally {
            setLaedt(false);
        }
    }, [toast]);

    useEffect(() => { void laden(); }, [laden]);

    const geaendert = entwurf !== gespeichert;

    const speichern = async () => {
        if (speichert || !geaendert) return;
        setSpeichert(true);
        try {
            const regel = await zeiterfassungApi.speichereUrlaubEintrittsjahr({ mehrurlaubAnteilig: entwurf });
            const anteilig = regel?.mehrurlaubAnteilig === true;
            setGespeichert(anteilig);
            setEntwurf(anteilig);
            toast.success('Regel für den Urlaub im Eintrittsjahr gespeichert.');
            onSaved?.();
        } catch (err) {
            toast.error(meldung(err, 'Die Regel für den Urlaub im Eintrittsjahr konnte nicht gespeichert werden.'));
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <SettingsCard
            icon={<UserPlus aria-hidden="true" className="w-5 h-5 text-rose-600" />}
            title="Urlaub im Eintrittsjahr"
            info={<UrlaubEintrittsjahrInfo />}
            description={<p>Wie viel Urlaub bekommt jemand in dem Jahr, in dem er bei Ihnen anfängt?</p>}
        >
            {laedt ? (
                <SectionLoading />
            ) : ladeFehler ? (
                <div role="alert" className="flex flex-wrap items-center gap-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                    {ladeFehler}
                    <Button variant="outline" size="sm" onClick={() => void laden()}>Erneut laden</Button>
                </div>
            ) : (
                <fieldset disabled={speichert} aria-label="Regel für den Urlaub im Eintrittsjahr" className="min-w-0 space-y-4">
                    <div role="radiogroup" aria-label="Urlaub im Eintrittsjahr" className="grid max-w-4xl gap-3 md:grid-cols-2">
                        {OPTIONEN.map(option => {
                            const gewaehlt = entwurf === option.anteilig;
                            return (
                                <label key={String(option.anteilig)} className={cn(
                                    'flex cursor-pointer items-start gap-3 rounded-lg border p-4 transition-colors focus-within:ring-2 focus-within:ring-rose-500',
                                    gewaehlt ? 'border-rose-300 bg-rose-50/60' : 'border-slate-200 bg-white hover:bg-slate-50',
                                )}>
                                    <input type="radio" name={name} checked={gewaehlt} onChange={() => setEntwurf(option.anteilig)}
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
                    {entwurf && (
                        <p className="max-w-4xl text-sm text-amber-800">
                            Nur wählen, wenn Ihr Arbeitsvertrag oder Tarifvertrag das anteilige Rechnen im ersten Jahr vorsieht.
                        </p>
                    )}
                    <SaveButton
                        onClick={() => void speichern()}
                        saving={speichert}
                        disabled={!geaendert}
                        title={!geaendert ? 'Keine Änderung zum Speichern.' : undefined}
                    >
                        Eintrittsjahr speichern
                    </SaveButton>
                </fieldset>
            )}
        </SettingsCard>
    );
}
