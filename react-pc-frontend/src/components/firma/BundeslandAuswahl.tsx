import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarDays, CheckCircle2, ChevronDown, MapPin } from 'lucide-react';
import { Button } from '../ui/button';
import { Label } from '../ui/label';
import { Select } from '../ui/select-custom';
import { InfoAbschnitt, InfoButton } from '../ui/info-button';
import { BUNDESLAND_OPTIONEN, STANDARD_BUNDESLAND, bundeslandName } from '../../lib/bundeslaender';
import { cn } from '../../lib/utils';

/** Antwort von `GET /api/firma/feiertage-vorschau` (API-Vertrag #222, Nachtrag). */
export interface FeiertageVorschau {
    vorgeschlagenesBundesland: string | null;
    bundesland: string;
    bundeslandName: string;
    jahr: number;
    feiertage: { datum: string; bezeichnung: string }[];
    hinweise: string[];
}

/** Wartezeit nach dem letzten Tastendruck in der PLZ, bevor nachgefragt wird. */
export const VORSCHAU_VERZOEGERUNG_MS = 400;

const istPlz = (plz: string) => /^\d{5}$/.test(plz);

function BundeslandInfo() {
    return (
        <InfoButton titel="Bundesland">
            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                <p>Das Programm schlägt das Bundesland anhand der Postleitzahl vor. Es holt die gesetzlichen Feiertage für dieses Bundesland aus dem Internet. Ohne Internet nimmt es eine eingebaute Liste.</p>
            </InfoAbschnitt>
            <InfoAbschnitt titel="Was bewirkt das?">
                <p>Die Feiertage bestimmen Soll-Stunden, Feiertagsgutschrift, Urlaubstage und den Lohnexport. Nach einem Wechsel rechnet das Programm alle noch nicht abgeschlossenen Monate neu. Abgeschlossene Monate bleiben unverändert.</p>
                <p>Eigene Feiertage und Anpassungen gelten nur für das Bundesland, in dem sie angelegt wurden.</p>
            </InfoAbschnitt>
        </InfoButton>
    );
}

interface Props {
    /** Kürzel des gewählten Bundeslands; leer = Vorgabe des Backends (Bayern). */
    value: string | undefined;
    onChange: (kuerzel: string) => void;
    /** PLZ aus den Firmendaten; bei fünf Ziffern schlägt das Programm das Bundesland vor. */
    plz: string | undefined;
}

/**
 * Bundesland-Auswahl in den Firmendaten (Issue #222): Vorschlag anhand der PLZ
 * und eine aufklappbare Liste der Feiertage, die damit gelten. Gespeichert wird
 * erst mit „Speichern“ der Firmendaten.
 */
export function BundeslandAuswahl({ value, onChange, plz }: Props) {
    const gewaehlt = value || STANDARD_BUNDESLAND;
    const plzSauber = (plz ?? '').trim();
    const [vorschau, setVorschau] = useState<FeiertageVorschau | null>(null);
    const [fehler, setFehler] = useState(false);
    const [offen, setOffen] = useState(false);
    const anfrage = useRef(0);
    const listeId = useId();

    useEffect(() => {
        const nummer = ++anfrage.current;
        const timer = setTimeout(async () => {
            const parameter = new URLSearchParams({ bundesland: gewaehlt, jahr: String(new Date().getFullYear()) });
            if (istPlz(plzSauber)) parameter.set('plz', plzSauber);
            try {
                const res = await fetch(`/api/firma/feiertage-vorschau?${parameter.toString()}`);
                if (!res.ok) throw new Error(String(res.status));
                const daten = await res.json() as FeiertageVorschau;
                if (nummer === anfrage.current) { setVorschau(daten); setFehler(false); }
            } catch {
                if (nummer === anfrage.current) { setVorschau(null); setFehler(true); }
            }
        }, VORSCHAU_VERZOEGERUNG_MS);
        return () => clearTimeout(timer);
    }, [gewaehlt, plzSauber]);

    // Der Vorschlag gilt nur für die PLZ, die gerade im Feld steht.
    const vorschlag = istPlz(plzSauber) ? vorschau?.vorgeschlagenesBundesland ?? null : null;
    const vorschlagName = bundeslandName(vorschlag);
    const passt = !!vorschlag && vorschlag === gewaehlt;
    const zeigeVorschau = vorschau && vorschau.bundesland === gewaehlt ? vorschau : null;

    return (
        <div className="space-y-2">
            <div className="flex items-center gap-1">
                <Label htmlFor="firma-bundesland">Bundesland</Label>
                <BundeslandInfo />
                {passt && (
                    <span className="ml-1 inline-flex items-center gap-1 text-xs text-slate-600">
                        <CheckCircle2 className="h-3.5 w-3.5 text-rose-600" aria-hidden="true" /> passt zur Postleitzahl
                    </span>
                )}
            </div>
            <Select
                id="firma-bundesland"
                aria-label="Bundesland"
                value={gewaehlt}
                onChange={onChange}
                options={BUNDESLAND_OPTIONEN}
            />
            {vorschlag && vorschlagName && !passt && (
                <div role="status" className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
                    <p className="flex items-center gap-2">
                        <MapPin className="h-4 w-4 shrink-0 text-rose-600" aria-hidden="true" />
                        Laut Postleitzahl {plzSauber} liegt euer Betrieb in {vorschlagName}.
                    </p>
                    <Button variant="outline" size="sm" onClick={() => onChange(vorschlag)}>Übernehmen</Button>
                </div>
            )}
            {zeigeVorschau && (
                <div className="rounded-lg border border-slate-200">
                    <button
                        type="button"
                        aria-expanded={offen}
                        aria-controls={listeId}
                        onClick={() => setOffen(o => !o)}
                        className="flex w-full items-center justify-between gap-2 rounded-lg px-3 py-2 text-left text-sm font-medium text-slate-700 hover:bg-slate-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500"
                    >
                        <span className="flex items-center gap-2">
                            <CalendarDays className="h-4 w-4 text-slate-400" aria-hidden="true" />
                            Feiertage {zeigeVorschau.jahr} in {zeigeVorschau.bundeslandName} ({zeigeVorschau.feiertage.length})
                        </span>
                        <ChevronDown className={cn('h-4 w-4 text-slate-400 motion-safe:transition-transform', offen && 'rotate-180')} aria-hidden="true" />
                    </button>
                    {offen && (
                        <div id={listeId} className="space-y-3 border-t border-slate-100 px-3 py-2">
                            <ul className="divide-y divide-slate-100 text-sm">
                                {zeigeVorschau.feiertage.map(f => (
                                    <li key={`${f.datum}-${f.bezeichnung}`} className="flex gap-3 py-1.5">
                                        <span className="w-28 shrink-0 tabular-nums text-slate-500">
                                            {new Date(`${f.datum}T00:00:00`).toLocaleDateString('de-DE', { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' })}
                                        </span>
                                        <span className="text-slate-800">{f.bezeichnung}</span>
                                    </li>
                                ))}
                            </ul>
                            {zeigeVorschau.hinweise.length > 0 && (
                                <div className="space-y-1 text-xs text-slate-500">
                                    {zeigeVorschau.hinweise.map(hinweis => <p key={hinweis}>{hinweis}</p>)}
                                </div>
                            )}
                            <Link to="/feiertage" className="inline-block text-sm font-medium text-rose-700 underline underline-offset-2 hover:text-rose-800">
                                Feiertage anpassen
                            </Link>
                        </div>
                    )}
                </div>
            )}
            {fehler && <p className="text-xs text-slate-500">Die Feiertage für dieses Bundesland konnten gerade nicht geladen werden.</p>}
        </div>
    );
}
