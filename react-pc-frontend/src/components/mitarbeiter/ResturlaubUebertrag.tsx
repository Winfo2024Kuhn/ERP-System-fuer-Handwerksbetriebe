import { useCallback, useEffect, useState } from 'react';
import { CalendarClock, Loader2, RotateCcw } from 'lucide-react';
import { Button } from '../ui/button';
import { DecimalInput } from '../ui/decimal-input';
import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { Select } from '../ui/select-custom';
import { useToast } from '../ui/toast';
import { leseFehlermeldung } from '../../lib/apiFehler';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import { anspruchHinweis, formatIsoDatumDe, formatUrlaubstageMitEinheit } from '../../lib/urlaubstage';

/** Antwort von `/api/urlaub/uebertrag`. */
interface UebertragStand {
    jahr: number;
    /** Gültiger Übertrag (von Hand oder berechnet). */
    tage: number;
    /** Was das Programm selbst rechnen würde. */
    berechnet: number;
    manuell: boolean;
    /** Letzter Tag für den Vorjahresrest (ISO); `null` = verfällt nicht. */
    verfallsdatum: string | null;
    /** Urlaubsanspruch in diesem Jahr (im Eintrittsjahr anteilig bzw. gesetzlicher Mindesturlaub). */
    anspruch?: number | null;
    /** Herkunft des Anspruchs (`UrlaubskontoService.Anspruchsart`). */
    anspruchArt?: string | null;
    eintrittsdatum?: string | null;
}

const LADEFEHLER = 'Der Resturlaub aus dem Vorjahr konnte nicht geladen werden.';

function istStand(wert: unknown): wert is UebertragStand {
    const stand = wert as UebertragStand | null;
    return !!stand && typeof stand === 'object' && Number.isFinite(stand.tage) && Number.isFinite(stand.berechnet);
}
const SPEICHERFEHLER = 'Der Resturlaub aus dem Vorjahr konnte nicht gespeichert werden.';

/** Erklärt, wie der Urlaubsanspruch nach dem Bundesurlaubsgesetz entsteht (v. a. im Eintrittsjahr). */
function UrlaubsanspruchInfo() {
    return (
        <InfoButton titel="Urlaubsanspruch">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <ul className="list-disc pl-4 space-y-1">
                    <li>Gesetzlich stehen jedem mindestens 24 Werktage bei einer 6-Tage-Woche zu – das sind 20 Tage bei 5 Arbeitstagen und 16 bei 4. Die Arbeitstage nimmt das Programm aus der Arbeitszeit des Mitarbeiters. Liegt der Jahresurlaub darunter, gilt das gesetzliche Minimum.</li>
                    <li>Wer bis einschließlich 1. Juli anfängt, hat die Wartezeit von sechs Monaten noch im selben Jahr erfüllt und bekommt den vollen Jahresurlaub. Wer später anfängt, bekommt ein Zwölftel je vollem Monat (halbe Tage und mehr werden aufgerundet).</li>
                    <li>Sehen Arbeits- oder Tarifvertrag auch bei Eintritt bis 1. Juli Zwölftel vor, stellen Sie das unter Einstellungen → Zeiterfassung → „Urlaub im Eintrittsjahr“ ein. Dann gibt es mindestens den gesetzlichen Mindesturlaub.</li>
                </ul>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Der Anspruch zählt für Urlaubsanträge, Resturlaub und den Übertrag ins nächste Jahr. Hat ein früherer Arbeitgeber im selben Jahr schon Urlaub gewährt oder scheidet jemand im Jahr aus, ziehen Sie das bitte als Urlaubskorrektur ab – das rechnet das Programm nicht selbst. Stimmt der Hinweis „Eintritt am …“ nicht, prüfen Sie das Eintrittsdatum des Mitarbeiters.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

/**
 * Mitarbeiter → Konditionen: Resturlaub, der aus dem Vorjahr übernommen wird.
 *
 * <p>Das Programm rechnet ihn selbst aus den gebuchten Urlaubstagen. Weicht er
 * ab (z. B. weil früher auf Papier geplant wurde), legt das Büro den Wert hier
 * fest; „Wieder ausrechnen lassen“ nimmt den Handwert zurück.</p>
 */
export function ResturlaubUebertrag({ mitarbeiterId }: { mitarbeiterId: number }) {
    const toast = useToast();
    const aktuellesJahr = new Date().getFullYear();
    const [jahr, setJahr] = useState(aktuellesJahr);
    const [stand, setStand] = useState<UebertragStand | null>(null);
    const [laedt, setLaedt] = useState(true);
    const [ladeFehler, setLadeFehler] = useState('');
    const [entwurf, setEntwurf] = useState('');
    const [speichert, setSpeichert] = useState(false);

    /** Setzt Ladeanzeige und Fehler zurück, bevor neu geladen wird. */
    const ladenStarten = () => {
        setLaedt(true);
        setLadeFehler('');
    };

    // Anderer Mitarbeiter oder anderes Jahr: sofort wieder „wird geladen“ zeigen.
    const geladenFuer = `${mitarbeiterId}|${jahr}`;
    const [zuletztGeladenFuer, setZuletztGeladenFuer] = useState(geladenFuer);
    if (zuletztGeladenFuer !== geladenFuer) {
        setZuletztGeladenFuer(geladenFuer);
        ladenStarten();
    }

    const laden = useCallback(() => fetch(`/api/urlaub/uebertrag?mitarbeiterId=${encodeURIComponent(String(mitarbeiterId))}&jahr=${encodeURIComponent(String(jahr))}`)
        .then(async (res) => {
            if (!res.ok) throw new Error(await leseFehlermeldung(res, LADEFEHLER));
            const daten: unknown = await res.json();
            if (!istStand(daten)) throw new Error(LADEFEHLER);
            setStand(daten);
            setEntwurf(formatDecimalInput(daten.tage));
        })
        .catch((err: unknown) => {
            const text = err instanceof Error && err.message ? err.message : LADEFEHLER;
            setLadeFehler(text);
            toast.error(text);
        })
        .finally(() => setLaedt(false)), [mitarbeiterId, jahr, toast]);

    useEffect(() => { void laden(); }, [laden]);

    const speichern = async (tage: number | null) => {
        setSpeichert(true);
        try {
            const res = await fetch('/api/urlaub/uebertrag', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ mitarbeiterId, jahr, tage }),
            });
            if (!res.ok) throw new Error(await leseFehlermeldung(res, SPEICHERFEHLER));
            const daten: unknown = await res.json();
            if (!istStand(daten)) throw new Error(SPEICHERFEHLER);
            setStand(daten);
            setEntwurf(formatDecimalInput(daten.tage));
            toast.success(tage === null ? 'Resturlaub wird wieder ausgerechnet.' : 'Resturlaub aus dem Vorjahr gespeichert.');
        } catch (err) {
            toast.error(err instanceof Error && err.message ? err.message : SPEICHERFEHLER);
        } finally {
            setSpeichert(false);
        }
    };

    const uebernehmen = () => {
        const ergebnis = validateDecimalInput(entwurf, { label: 'Resturlaub aus dem Vorjahr', min: 0, max: 365, required: true });
        if (!ergebnis.valid) {
            toast.error(ergebnis.message);
            return;
        }
        const tage = ergebnis.value ?? 0;
        if (Math.round(tage * 2) !== tage * 2) {
            toast.error('Bitte ganze oder halbe Tage eingeben (z. B. 3 oder 3,5).');
            return;
        }
        void speichern(tage);
    };

    const geaendert = stand !== null && entwurf !== formatDecimalInput(stand.tage);
    const grund = stand ? anspruchHinweis(stand.anspruchArt, stand.eintrittsdatum) : null;
    const jahrOptionen = [aktuellesJahr - 1, aktuellesJahr, aktuellesJahr + 1]
        .map(j => ({ value: String(j), label: `${j - 1} → ${j}` }));

    return (
        <div className="space-y-2">
            <div className="flex items-center gap-1">
                <CalendarClock aria-hidden="true" className="w-4 h-4 text-slate-400 shrink-0" />
                <p className="text-xs text-slate-500">Resturlaub aus dem Vorjahr</p>
                <InfoButton titel="Resturlaub aus dem Vorjahr">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>Das Programm rechnet aus, wie viel Urlaub im Vorjahr übrig war, und nimmt ihn ins neue Jahr mit. Urlaub im neuen Jahr zählt zuerst gegen diesen Rest. Ändern Sie später den Jahresurlaub oder tragen Urlaub im Vorjahr nach, ändert sich der ausgerechnete Rest mit. Wurde mehr genommen als da war, ist der Rest 0 – abgezogen wird nichts.</p>
                    </InfoAbschnitt>
                    <InfoAbschnitt titel="Was bewirkt das?">
                        <p>Ein Wert von Hand ersetzt die Rechnung für dieses Jahr – z. B. wenn früher auf Papier geplant wurde. Gebuchte Urlaubstage ändern sich nicht. Ob und wann der Rest verfällt, steht unter Einstellungen → Zeiterfassung.</p>
                    </InfoAbschnitt>
                </InfoButton>
            </div>
            <Select aria-label="Übertrag in Jahr" className="w-40" value={String(jahr)} options={jahrOptionen}
                onChange={wert => setJahr(Number(wert))} disabled={laedt || speichert} />
            {laedt ? (
                <p className="flex items-center gap-2 text-sm text-slate-500">
                    <Loader2 aria-hidden="true" className="w-4 h-4 motion-safe:animate-spin" /> Wird geladen…
                </p>
            ) : ladeFehler || !stand ? (
                <div role="alert" className="flex flex-wrap items-center gap-2 text-sm text-rose-800">
                    {ladeFehler || LADEFEHLER}
                    <Button variant="outline" size="sm" onClick={() => { ladenStarten(); void laden(); }}>Erneut laden</Button>
                </div>
            ) : (
                <>
                    <div className="flex flex-wrap items-center gap-2">
                        <DecimalInput aria-label="Resturlaub aus dem Vorjahr in Tagen" className="w-24" value={entwurf}
                            onChange={setEntwurf} min={0} max={365} disabled={speichert} />
                        <span className="text-sm text-slate-600">Tage</span>
                        <Button size="sm" onClick={uebernehmen} disabled={!geaendert || speichert}
                            title={!geaendert ? 'Keine Änderung zum Speichern.' : undefined}>
                            {speichert && <Loader2 aria-hidden="true" className="w-4 h-4 motion-safe:animate-spin" />}
                            Speichern
                        </Button>
                    </div>
                    <p className="text-xs text-slate-500">
                        {stand.manuell
                            ? `Von Hand festgelegt. Ausgerechnet wären ${formatUrlaubstageMitEinheit(stand.berechnet)}.`
                            : 'Vom Programm ausgerechnet.'}
                        {' '}
                        {stand.verfallsdatum
                            ? `Verfällt, wenn nicht bis ${formatIsoDatumDe(stand.verfallsdatum)} genommen.`
                            : 'Verfällt nicht.'}
                    </p>
                    {stand.manuell && (
                        <Button variant="ghost" size="sm" onClick={() => void speichern(null)} disabled={speichert}>
                            <RotateCcw aria-hidden="true" className="w-4 h-4" />
                            Wieder ausrechnen lassen
                        </Button>
                    )}
                    {stand.anspruch != null && (
                        <div className="flex items-start gap-1 pt-1">
                            <p className="text-xs text-slate-500" data-testid="urlaubsanspruch-jahr">
                                Urlaubsanspruch {stand.jahr}: {formatUrlaubstageMitEinheit(stand.anspruch)}
                                {grund ? ` – ${grund}` : ''}
                            </p>
                            <UrlaubsanspruchInfo />
                        </div>
                    )}
                </>
            )}
        </div>
    );
}
