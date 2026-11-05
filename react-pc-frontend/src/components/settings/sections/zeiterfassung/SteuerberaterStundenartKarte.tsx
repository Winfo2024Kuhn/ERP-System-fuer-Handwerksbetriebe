import { useCallback, useEffect, useId, useState } from 'react';
import { Send } from 'lucide-react';
import { Button } from '../../../ui/button';
import { useToast } from '../../../ui/toast';
import { SaveButton, SectionLoading, SettingsCard } from '../../settingsUi';
import { cn } from '../../../../lib/utils';
import { zeiterfassungApi } from './zeiterfassungApi';
import { SteuerberaterStundenartInfo } from './ZeiterfassungInfos';

type Art = 'SOLL' | 'IST';

const meldung = (err: unknown, fallback: string) => (err instanceof Error && err.message ? err.message : fallback);

const OPTIONEN: { art: Art; titel: string; text: string }[] = [
    {
        art: 'SOLL',
        titel: 'Sollstunden – bezahlt nach Vertrag',
        text: 'Vertragsstunden abzüglich Urlaub, Feiertage, Krankheit und Fortbildung. Überstunden bleiben im Stundenkonto.',
    },
    {
        art: 'IST',
        titel: 'Iststunden – tatsächlich gearbeitet',
        text: 'Die wirklich gebuchte Arbeitszeit des Monats, ohne Pausen.',
    },
];

/**
 * Einstellungen → Zeiterfassung: Welche Stunden schickt das Report Centre dem
 * Steuerberater? Bewusst hier (nur Admins) und nicht im Bericht, damit niemand
 * versehentlich das Falsche schickt. Noch nie gewählt heißt: Sollstunden.
 */
export function SteuerberaterStundenartKarte({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const name = useId();
    const [laedt, setLaedt] = useState(true);
    const [ladeFehler, setLadeFehler] = useState('');
    const [gespeichert, setGespeichert] = useState<Art | null>(null);
    const [entwurf, setEntwurf] = useState<Art>('SOLL');
    const [speichert, setSpeichert] = useState(false);

    // Ladeanzeige und Fehler setzt „Erneut laden“ vorab zurück; beim ersten Laden stimmen die Startwerte.
    const laden = useCallback(() => zeiterfassungApi.ladeSteuerberaterStundenart()
        .then((wert) => {
            const art = wert?.stundenart ?? null;
            setGespeichert(art);
            setEntwurf(art ?? 'SOLL');
        })
        .catch((err: unknown) => {
            const text = meldung(err, 'Die Stunden für den Steuerberater konnten nicht geladen werden.');
            setLadeFehler(text);
            toast.error(text);
        })
        .finally(() => setLaedt(false)), [toast]);

    useEffect(() => { void laden(); }, [laden]);

    // Noch nie gewählt: Speichern ist auch ohne Änderung sinnvoll (legt die Wahl fest).
    const kannSpeichern = entwurf !== gespeichert;

    const speichern = async () => {
        if (speichert || !kannSpeichern) return;
        setSpeichert(true);
        try {
            const wert = await zeiterfassungApi.speichereSteuerberaterStundenart(entwurf);
            const art = wert?.stundenart ?? entwurf;
            setGespeichert(art);
            setEntwurf(art);
            toast.success(`Gespeichert: Der Steuerberater bekommt ab jetzt ${art === 'IST' ? 'Iststunden' : 'Sollstunden'}.`);
            onSaved?.();
        } catch (err) {
            toast.error(meldung(err, 'Die Stunden für den Steuerberater konnten nicht gespeichert werden.'));
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <SettingsCard
            icon={<Send aria-hidden="true" className="w-5 h-5 text-rose-600" />}
            title="Stunden für den Steuerberater"
            info={<SteuerberaterStundenartInfo />}
            description={<p>Welche Arbeitsstunden schickt das Report Centre jeden Monat an den Steuerberater?</p>}
        >
            {laedt ? (
                <SectionLoading />
            ) : ladeFehler ? (
                <div role="alert" className="flex flex-wrap items-center gap-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                    {ladeFehler}
                    <Button variant="outline" size="sm" onClick={() => { setLaedt(true); setLadeFehler(''); void laden(); }}>Erneut laden</Button>
                </div>
            ) : (
                <fieldset disabled={speichert} aria-label="Stunden für den Steuerberater" className="min-w-0 space-y-4">
                    {gespeichert === null && (
                        <p className="max-w-4xl rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
                            Noch nicht festgelegt – bis dahin werden Sollstunden übermittelt.
                        </p>
                    )}
                    <div role="radiogroup" aria-label="Stunden für den Steuerberater" className="grid max-w-4xl gap-3 md:grid-cols-2">
                        {OPTIONEN.map(option => {
                            const gewaehlt = entwurf === option.art;
                            return (
                                <label key={option.art} className={cn(
                                    'flex cursor-pointer items-start gap-3 rounded-lg border p-4 transition-colors focus-within:ring-2 focus-within:ring-rose-500',
                                    gewaehlt ? 'border-rose-300 bg-rose-50/60' : 'border-slate-200 bg-white hover:bg-slate-50',
                                )}>
                                    <input type="radio" name={name} checked={gewaehlt} onChange={() => setEntwurf(option.art)}
                                        className="mt-0.5 h-4 w-4 shrink-0 accent-rose-600 focus:outline-none" />
                                    <span>
                                        <span className="font-medium text-slate-900">{option.titel}</span>
                                        <span className="mt-1 block text-sm text-slate-500">{option.text}</span>
                                    </span>
                                </label>
                            );
                        })}
                    </div>
                    <SaveButton
                        onClick={() => void speichern()}
                        saving={speichert}
                        disabled={!kannSpeichern}
                        title={!kannSpeichern ? 'Keine Änderung zum Speichern.' : undefined}
                    >
                        Stundenart speichern
                    </SaveButton>
                </fieldset>
            )}
        </SettingsCard>
    );
}
