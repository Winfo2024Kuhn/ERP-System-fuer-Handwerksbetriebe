import { useCallback, useEffect, useState } from 'react';
import { CalendarClock, CalendarX } from 'lucide-react';
import { Button } from '../../../ui/button';
import { DatePicker } from '../../../ui/datepicker';
import { Label } from '../../../ui/label';
import { useToast } from '../../../ui/toast';
import { SaveButton, SectionLoading, SettingsCard } from '../../settingsUi';
import { heuteIso, parseIsoDatum } from '../../../../lib/datum';
import { refreshNotifications } from '../../../../lib/notificationRefresh';
import { zeiterfassungApi } from './zeiterfassungApi';
import { StartDerZeiterfassungInfo } from './ZeiterfassungInfos';

const FELD_ID = 'zeiterfassung-eingefuehrt-am';
const FEHLER_ID = `${FELD_ID}-fehler`;
const STATUS_ID = `${FELD_ID}-status`;

const meldung = (err: unknown, fallback: string) => (err instanceof Error && err.message ? err.message : fallback);
const tagText = (iso: string) => parseIsoDatum(iso)?.toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' }) ?? iso;
const monatText = (iso: string) => parseIsoDatum(iso)?.toLocaleDateString('de-DE', { month: 'long', year: 'numeric' }) ?? iso;

/** Prüft den Entwurf vor dem Speichern – leer heißt „keine Grenze“ und ist erlaubt. */
function pruefe(entwurf: string): string {
    if (!entwurf) return '';
    if (!parseIsoDatum(entwurf)) return 'Bitte ein gültiges Datum wählen.';
    if (entwurf > heuteIso()) return 'Das Datum darf nicht in der Zukunft liegen.';
    return '';
}

/**
 * Einstellungen → Zeiterfassung: Ab wann erfasst der Betrieb Zeiten?
 *
 * <p>Ohne dieses Datum verlangt die Glocke („Zeiten prüfen“) auch Monate wie
 * „05/2014 abschließen“, obwohl es damals noch gar keine Zeiterfassung gab.
 * Monate vor dem Datum müssen nicht mehr abgeschlossen werden. Leer heißt:
 * keine Grenze.</p>
 */
export function StartDerZeiterfassungKarte({ onSaved }: { onSaved?: () => void }) {
    const toast = useToast();
    const [laedt, setLaedt] = useState(true);
    const [ladeFehler, setLadeFehler] = useState('');
    /** Was auf dem Server steht (`''` = kein Datum). */
    const [gespeichert, setGespeichert] = useState('');
    const [entwurf, setEntwurf] = useState('');
    const [speichert, setSpeichert] = useState(false);
    const [fehler, setFehler] = useState('');

    // Ladeanzeige und Fehler setzt „Erneut laden“ vorab zurück; beim ersten Laden stimmen die Startwerte.
    const laden = useCallback(() => zeiterfassungApi.ladeStart()
        .then((stand) => {
            const wert = stand.eingefuehrtAm ?? '';
            setGespeichert(wert);
            setEntwurf(wert);
            setFehler('');
        })
        .catch((err: unknown) => {
            const text = meldung(err, 'Der Start der Zeiterfassung konnte nicht geladen werden.');
            setLadeFehler(text);
            toast.error(text);
        })
        .finally(() => setLaedt(false)), [toast]);

    useEffect(() => { void laden(); }, [laden]);

    const geaendert = entwurf !== gespeichert;

    const aendern = (wert: string) => {
        setEntwurf(wert);
        setFehler('');
    };

    const speichern = async () => {
        if (speichert || !geaendert) return;
        const problem = pruefe(entwurf);
        if (problem) {
            setFehler(problem);
            toast.error(problem);
            return;
        }
        setSpeichert(true);
        setFehler('');
        try {
            const stand = await zeiterfassungApi.speichereStart(entwurf || null);
            const wert = stand.eingefuehrtAm ?? '';
            setGespeichert(wert);
            setEntwurf(wert);
            toast.success(wert
                ? 'Start der Zeiterfassung gespeichert.'
                : 'Datum entfernt – es gibt keine Grenze mehr.');
            // Die Glocke soll alte Monate sofort weglassen, nicht erst beim nächsten Abruf.
            refreshNotifications();
            onSaved?.();
        } catch (err) {
            const text = meldung(err, 'Der Start der Zeiterfassung konnte nicht gespeichert werden.');
            setFehler(text);
            toast.error(text);
        } finally {
            setSpeichert(false);
        }
    };

    return (
        <SettingsCard
            icon={<CalendarClock aria-hidden="true" className="w-5 h-5 text-rose-600" />}
            title="Start der Zeiterfassung"
            info={<StartDerZeiterfassungInfo />}
            description={
                <p>
                    Ab welchem Tag erfasst Ihr Betrieb Arbeitszeiten mit diesem Programm? Monate davor
                    müssen nicht abgeschlossen werden und tauchen unter „Zeiten prüfen“ nicht mehr auf.
                </p>
            }
        >
            {laedt ? (
                <SectionLoading />
            ) : ladeFehler ? (
                <div role="alert" className="flex flex-wrap items-center gap-3 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">
                    {ladeFehler}
                    <Button variant="outline" size="sm" onClick={() => { setLaedt(true); setLadeFehler(''); void laden(); }}>Erneut laden</Button>
                </div>
            ) : (
                <>
                    <Label htmlFor={FELD_ID}>Zeiterfassung eingeführt am</Label>
                    <div className="flex flex-wrap items-center gap-2">
                        <DatePicker
                            id={FELD_ID}
                            aria-label="Zeiterfassung eingeführt am"
                            aria-describedby={[STATUS_ID, fehler ? FEHLER_ID : ''].filter(Boolean).join(' ')}
                            className="w-full sm:w-64"
                            value={entwurf}
                            min="1900-01-01"
                            max={heuteIso()}
                            placeholder="Kein Datum – keine Grenze"
                            disabled={speichert}
                            onChange={aendern}
                        />
                        <Button
                            variant="outline"
                            className="h-10"
                            disabled={!entwurf || speichert}
                            title={!entwurf ? 'Es ist kein Datum eingetragen.' : undefined}
                            onClick={() => aendern('')}
                        >
                            <CalendarX aria-hidden="true" className="w-4 h-4" />
                            Datum entfernen
                        </Button>
                    </div>
                    {fehler && <p id={FEHLER_ID} role="alert" className="mt-2 text-sm text-rose-700">{fehler}</p>}
                    <p id={STATUS_ID} className="mt-3 text-sm text-slate-600">
                        {gespeichert
                            ? `Gespeichert: Zeiterfassung seit ${tagText(gespeichert)}. Monate vor ${monatText(gespeichert)} müssen nicht abgeschlossen werden.`
                            : 'Noch kein Datum gespeichert – alle Monate mit Zeiten müssen abgeschlossen werden.'}
                    </p>
                    <SaveButton
                        onClick={() => void speichern()}
                        saving={speichert}
                        disabled={!geaendert}
                        title={!geaendert ? 'Keine Änderung zum Speichern.' : undefined}
                    >
                        Start speichern
                    </SaveButton>
                </>
            )}
        </SettingsCard>
    );
}
