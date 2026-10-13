import { useCallback, useEffect, useState } from 'react';
import { CalendarClock, Loader2, RotateCcw } from 'lucide-react';
import { Button } from '../ui/button';
import { DecimalInput } from '../ui/decimal-input';
import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { Select } from '../ui/select-custom';
import { useToast } from '../ui/toast';
import { leseFehlermeldung } from '../../lib/apiFehler';
import { formatDecimalInput, validateDecimalInput } from '../../lib/numberInput';
import { formatIsoDatumDe, formatUrlaubstageMitEinheit } from '../../lib/urlaubstage';

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
}

const LADEFEHLER = 'Der Resturlaub aus dem Vorjahr konnte nicht geladen werden.';

function istStand(wert: unknown): wert is UebertragStand {
    const stand = wert as UebertragStand | null;
    return !!stand && typeof stand === 'object' && Number.isFinite(stand.tage) && Number.isFinite(stand.berechnet);
}
const SPEICHERFEHLER = 'Der Resturlaub aus dem Vorjahr konnte nicht gespeichert werden.';

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

    const laden = useCallback(async () => {
        setLaedt(true);
        setLadeFehler('');
        try {
            const res = await fetch(`/api/urlaub/uebertrag?mitarbeiterId=${encodeURIComponent(String(mitarbeiterId))}&jahr=${encodeURIComponent(String(jahr))}`);
            if (!res.ok) throw new Error(await leseFehlermeldung(res, LADEFEHLER));
            const daten: unknown = await res.json();
            if (!istStand(daten)) throw new Error(LADEFEHLER);
            setStand(daten);
            setEntwurf(formatDecimalInput(daten.tage));
        } catch (err) {
            const text = err instanceof Error && err.message ? err.message : LADEFEHLER;
            setLadeFehler(text);
            toast.error(text);
        } finally {
            setLaedt(false);
        }
    }, [mitarbeiterId, jahr, toast]);

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
    const jahrOptionen = [aktuellesJahr - 1, aktuellesJahr, aktuellesJahr + 1]
        .map(j => ({ value: String(j), label: `${j - 1} → ${j}` }));

    return (
        <div className="space-y-2">
            <div className="flex items-center gap-1">
                <CalendarClock aria-hidden="true" className="w-4 h-4 text-slate-400 shrink-0" />
                <p className="text-xs text-slate-500">Resturlaub aus dem Vorjahr</p>
                <InfoButton titel="Resturlaub aus dem Vorjahr">
                    <InfoAbschnitt titel="Was passiert im Hintergrund?">
                        <p>Das Programm rechnet aus, wie viel Urlaub im Vorjahr übrig war, und nimmt ihn ins neue Jahr mit. Urlaub im neuen Jahr zählt zuerst gegen diesen Rest.</p>
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
                    <Button variant="outline" size="sm" onClick={() => void laden()}>Erneut laden</Button>
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
                </>
            )}
        </div>
    );
}
