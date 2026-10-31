import { AlertTriangle, ImageIcon, RefreshCw } from 'lucide-react';
import { SearchableSelect } from '../../../ui/searchable-select';
import type { SearchableSelectOption } from '../../../ui/searchable-select';
import { Button } from '../../../ui/button';
import { cn } from '../../../../lib/utils';
import { useGemerkterWert } from '../../../../hooks/useGemerkterWert';
import { pruefeModellname } from '../../../../features/ki/kiEinstellungenApi';
import type { KiAnbieter, KiFaehigkeit, KiModellEintrag } from '../../../../features/ki/kiEinstellungenApi';
import type { KiModellListeZustand } from '../../../../features/ki/useKiModellListe';

/** Seitenweit gemerkte Filter aller Modellsuchen (siehe {@link useGemerkterWert}). */
export interface KiModellFilter {
    nurBilder: boolean;
}

const FILTER_STANDARD: KiModellFilter = { nurBilder: false };
const FILTER_SCHLUESSEL = 'ki-modellsuche-filter';

function istFilter(wert: unknown): wert is KiModellFilter {
    return !!wert && typeof wert === 'object' && typeof (wert as KiModellFilter).nurBilder === 'boolean';
}

function bilderText(bilder: boolean | null): string {
    if (bilder === true) return 'liest Bilder';
    if (bilder === false) return 'keine Bilder';
    return 'Bilder: unbekannt';
}

interface KiModellSucheProps {
    id?: string;
    /** Nur Modelle dieses Anbieters werden angeboten. */
    anbieter: KiAnbieter;
    /** Gewähltes Modell; leer = Standard-Modell. */
    wert: string;
    onChange: (modell: string) => void;
    /** Text für „leer“, z. B. „Standard-Modell (mistral-medium-latest)“. */
    standardText: string;
    modellListe: KiModellListeZustand;
    /** Name für Screenreader, wenn kein Label mit `htmlFor` auf `id` zeigt. */
    ariaLabel?: string;
    /** Was die Funktion braucht – für den Hinweis, wenn das Modell keine Bilder kann. */
    braucht?: KiFaehigkeit[];
    disabled?: boolean;
}

/**
 * Modellauswahl mit Suche für einen Anbieter.
 *
 * <p>Die Liste kommt live vom Anbieter (oft 30 bis 80 Modelle) und wird erst
 * beim Öffnen geladen. Der Filter „Nur Modelle, die Bilder lesen“ gilt
 * seitenweit und bleibt beim nächsten Öffnen erhalten. Fehlt ein Modell in
 * der Liste – oder kommt gar keine –, lässt sich der Name auch selbst
 * eintragen.</p>
 */
export function KiModellSuche({
    id, anbieter, wert, onChange, standardText, modellListe, ariaLabel, braucht = [], disabled,
}: KiModellSucheProps) {
    const [filter, setFilter] = useGemerkterWert<KiModellFilter>(FILTER_SCHLUESSEL, FILTER_STANDARD, istFilter);

    const vomAnbieter = modellListe.modelle.filter((m) => m.anbieter === anbieter.typ);
    const sichtbar = filter.nurBilder ? vomAnbieter.filter((m) => m.bilder === true) : vomAnbieter;
    const optionen: SearchableSelectOption[] = sichtbar.map((m: KiModellEintrag) => ({
        value: m.modell,
        label: m.name && m.name !== m.modell ? m.name : m.modell,
        sublabel: m.name && m.name !== m.modell ? `${m.modell} · ${bilderText(m.bilder)}` : bilderText(m.bilder),
    }));
    const gewaehlt = vomAnbieter.find((m) => m.modell === wert);
    const brauchtBilder = braucht.includes('BILD');

    const filterLeiste = (
        <div className="flex flex-wrap items-center justify-between gap-2">
            <button
                type="button"
                aria-pressed={filter.nurBilder}
                onClick={() => setFilter({ ...filter, nurBilder: !filter.nurBilder })}
                className={cn(
                    'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500',
                    filter.nurBilder ? 'border-rose-300 bg-rose-50 text-rose-700' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50',
                )}
            >
                <ImageIcon aria-hidden="true" className="h-3.5 w-3.5" />
                Nur Modelle, die Bilder lesen
            </button>
            {modellListe.status === 'fertig' && (
                <span className="text-xs text-slate-500 tabular-nums">
                    {sichtbar.length} von {vomAnbieter.length} Modellen
                </span>
            )}
        </div>
    );

    const meldungen = modellListe.status === 'fehler' ? (
        <div role="alert" className="flex items-center justify-between gap-2 text-red-700">
            <span>{modellListe.fehler}</span>
            <Button variant="outline" size="sm" onClick={() => { modellListe.verwerfen(); modellListe.laden(); }}>
                <RefreshCw aria-hidden="true" className="h-3.5 w-3.5" />
                Erneut laden
            </Button>
        </div>
    ) : modellListe.hinweise.length > 0 ? (
        <ul className="space-y-0.5">
            {modellListe.hinweise.map((h) => <li key={h}>{h}</li>)}
        </ul>
    ) : null;

    const keineTreffer = modellListe.status === 'fehler'
        ? 'Keine Liste vom Anbieter – Namen unten selbst eintragen.'
        : vomAnbieter.length === 0
            ? (anbieter.schluesselGesetzt
                ? `${anbieter.name} hat keine Modelle gemeldet.`
                : `Für ${anbieter.name} ist noch kein Schlüssel hinterlegt – seine Modelle erscheinen erst danach.`)
            : 'Kein Modell passt zu Suche und Filter.';

    return (
        <div>
            <SearchableSelect
                id={id}
                ariaLabel={ariaLabel}
                value={wert}
                onChange={(neu) => onChange(neu)}
                options={optionen}
                anzeigeText={wert ? (gewaehlt?.name && gewaehlt.name !== wert ? `${gewaehlt.name} (${wert})` : wert) : undefined}
                placeholder={standardText}
                searchPlaceholder="Modell suchen …"
                loading={modellListe.status === 'laedt'}
                onOpenChange={(offen) => {
                    if (offen) modellListe.laden();
                }}
                filter={filterLeiste}
                meldungen={meldungen}
                freieEingabe={{
                    hinweis: 'Modell nicht in der Liste? Namen ins Suchfeld tippen und hier übernehmen.',
                    knopfText: (name) => `„${name}“ als Modellnamen übernehmen`,
                    pruefen: pruefeModellname,
                }}
                keineTrefferText={keineTreffer}
                leerenText="Zurück auf das Standard-Modell"
                disabled={disabled}
            />
            {brauchtBilder && gewaehlt?.bilder === false && (
                <p className="mt-1 flex items-start gap-1.5 text-xs text-amber-800">
                    <AlertTriangle aria-hidden="true" className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                    Dieses Modell kann laut {anbieter.name} keine Bilder lesen – diese Funktion braucht das.
                </p>
            )}
        </div>
    );
}
