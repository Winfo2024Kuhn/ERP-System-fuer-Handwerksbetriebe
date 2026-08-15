import { useEffect, useMemo, useState } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown, BarChart3, Package, Search, Truck, X } from 'lucide-react';
import { LieferantSearchModal, type LieferantSuchErgebnis } from './LieferantSearchModal';

/** Ein Lieferant mit seinen Einkaufszahlen im ausgewerteten Zeitraum. */
export interface LieferantUmsatz {
    name: string;
    bestellungen: number;
    netto: number;
}

interface LieferantenDetailsModalProps {
    isOpen: boolean;
    onClose: () => void;
    /** Alle Lieferanten mit Umsatz, absteigend sortiert (Reihenfolge = Platzierung). */
    lieferanten: LieferantUmsatz[];
    /** Beschriftung des Zeitraums, z.B. "2026" oder "März 2026". */
    zeitraum: string;
    /**
     * Platz des Lieferanten, der zusätzlich in der Grafik steckt. Der Platz dient als
     * Identität, weil das DTO keine ID liefert und Namen im Bestand doppelt vorkommen.
     */
    hervorgehobenerRang: number | null;
    onHervorheben: (rang: number | null) => void;
}

type SortFeld = 'rang' | 'name' | 'bestellungen';
type SortRichtung = 'asc' | 'desc';

const formatEuro = (value: number): string =>
    new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 }).format(value);

const formatProzent = (value: number): string =>
    new Intl.NumberFormat('de-DE', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value);

/** Pfeil im Spaltenkopf: zeigt, wonach und in welche Richtung sortiert wird. */
function SortIcon({ feld, aktivesFeld, richtung }: { feld: SortFeld; aktivesFeld: SortFeld; richtung: SortRichtung }) {
    if (feld !== aktivesFeld) return <ArrowUpDown className="w-3.5 h-3.5 text-slate-400" aria-hidden="true" />;
    return richtung === 'asc'
        ? <ArrowUp className="w-3.5 h-3.5 text-rose-600" aria-hidden="true" />
        : <ArrowDown className="w-3.5 h-3.5 text-rose-600" aria-hidden="true" />;
}

/**
 * Zeigt alle Lieferanten mit ihren Einkaufszahlen – nicht nur die zehn größten
 * aus der Grafik. Wer weiter hinten liegt, lässt sich hier über die Liste oder
 * die Stammdaten-Suche finden und bei Bedarf in die Grafik holen.
 */
export function LieferantenDetailsModal({ isOpen, ...props }: LieferantenDetailsModalProps) {
    // Erst beim Öffnen mounten: So startet die Ansicht jedes Mal ungefiltert und
    // unsortiert, ohne den State per Effect zurücksetzen zu müssen.
    if (!isOpen) return null;
    return <LieferantenDetailsInhalt {...props} />;
}

function LieferantenDetailsInhalt({
    onClose,
    lieferanten,
    zeitraum,
    hervorgehobenerRang,
    onHervorheben,
}: Omit<LieferantenDetailsModalProps, 'isOpen'>) {
    const [filter, setFilter] = useState('');
    const [sortFeld, setSortFeld] = useState<SortFeld>('rang');
    const [sortRichtung, setSortRichtung] = useState<SortRichtung>('asc');
    const [stammSucheOffen, setStammSucheOffen] = useState(false);
    /** Über die Stammdaten-Suche gewählter Lieferant, der im Zeitraum nichts geliefert hat. */
    const [ohneUmsatz, setOhneUmsatz] = useState<string | null>(null);

    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            // Ist die Stammdaten-Suche offen, gehört Escape ihr.
            if (e.key === 'Escape' && !stammSucheOffen) onClose();
        };
        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [onClose, stammSucheOffen]);

    const gesamtVolumen = useMemo(
        () => lieferanten.reduce((summe, l) => summe + l.netto, 0),
        [lieferanten]
    );

    // Der Rang steht vor dem Filtern fest, damit "Platz 11" auch beim Suchen Platz 11 bleibt.
    const mitRang = useMemo(
        () => lieferanten.map((l, index) => ({ ...l, rang: index + 1 })),
        [lieferanten]
    );

    const sichtbar = useMemo(() => {
        const suchbegriff = filter.trim().toLowerCase();
        const gefiltert = suchbegriff
            ? mitRang.filter(l => l.name.toLowerCase().includes(suchbegriff))
            : mitRang;

        const faktor = sortRichtung === 'asc' ? 1 : -1;
        return [...gefiltert].sort((a, b) => {
            if (sortFeld === 'name') return faktor * a.name.localeCompare(b.name, 'de');
            if (sortFeld === 'bestellungen') return faktor * (a.bestellungen - b.bestellungen);
            return faktor * (a.rang - b.rang);
        });
    }, [mitRang, filter, sortFeld, sortRichtung]);

    const toggleSort = (feld: SortFeld) => {
        if (sortFeld === feld) {
            setSortRichtung(r => (r === 'asc' ? 'desc' : 'asc'));
        } else {
            setSortFeld(feld);
            setSortRichtung(feld === 'rang' ? 'asc' : 'desc');
        }
    };

    const handleStammAuswahl = (lieferant: LieferantSuchErgebnis) => {
        const treffer = mitRang.find(l => l.name === lieferant.lieferantenname);
        if (treffer) {
            // Lieferant hat Umsatz – Liste direkt auf ihn einschränken.
            setFilter(lieferant.lieferantenname);
            setOhneUmsatz(null);
        } else {
            setFilter('');
            setOhneUmsatz(lieferant.lieferantenname);
        }
    };

    const sortRichtungAria = sortRichtung === 'asc' ? 'ascending' : 'descending';

    return (
        <>
            <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
                <div
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="lieferanten-details-titel"
                    className="bg-white rounded-xl shadow-2xl w-full max-w-3xl max-h-[85vh] flex flex-col overflow-hidden"
                >
                    <div className="p-4 border-b border-slate-200">
                        <div className="flex items-center justify-between mb-1">
                            <div className="flex items-center gap-2">
                                <BarChart3 className="w-5 h-5 text-rose-600" aria-hidden="true" />
                                <h2 id="lieferanten-details-titel" className="text-lg font-bold text-slate-900">
                                    Alle Lieferanten
                                </h2>
                            </div>
                            <button
                                onClick={onClose}
                                aria-label="Schließen"
                                className="p-1.5 hover:bg-slate-100 rounded-full transition-colors"
                            >
                                <X className="w-5 h-5 text-slate-500" />
                            </button>
                        </div>
                        <p className="text-sm text-slate-500 mb-3">
                            Was Sie {zeitraum} bei welchem Lieferanten eingekauft haben – die Grafik zeigt nur die zehn größten.
                        </p>

                        <div className="flex flex-col sm:flex-row gap-2">
                            <div className="relative flex-1">
                                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-400" aria-hidden="true" />
                                <input
                                    type="text"
                                    value={filter}
                                    onChange={e => setFilter(e.target.value)}
                                    placeholder="Lieferant in der Liste suchen..."
                                    aria-label="Lieferant in der Liste suchen"
                                    className="w-full pl-10 pr-3 py-2.5 bg-slate-50 border border-slate-200 rounded-lg text-slate-900 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500 focus:border-rose-500"
                                    autoFocus
                                />
                            </div>
                            <button
                                onClick={() => setStammSucheOffen(true)}
                                className="flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg border border-rose-300 text-rose-700 hover:bg-rose-50 transition-colors text-sm font-medium whitespace-nowrap"
                            >
                                <Truck className="w-4 h-4" aria-hidden="true" />
                                Aus Lieferantenliste
                            </button>
                        </div>

                        {ohneUmsatz && (
                            <div className="mt-3 flex items-start gap-2 p-3 rounded-lg bg-slate-50 border border-slate-200">
                                <Package className="w-4 h-4 text-slate-400 mt-0.5 flex-shrink-0" aria-hidden="true" />
                                <p className="text-sm text-slate-600">
                                    Bei <span className="font-medium text-slate-900">{ohneUmsatz}</span> haben Sie {zeitraum} nichts eingekauft.
                                </p>
                                <button
                                    onClick={() => setOhneUmsatz(null)}
                                    aria-label="Hinweis ausblenden"
                                    className="ml-auto p-0.5 hover:bg-slate-200 rounded transition-colors flex-shrink-0"
                                >
                                    <X className="w-4 h-4 text-slate-400" />
                                </button>
                            </div>
                        )}
                    </div>

                    <div className="flex-1 overflow-y-auto">
                        {sichtbar.length === 0 ? (
                            <div className="text-center py-12 text-slate-500">
                                <Truck className="w-10 h-10 mx-auto mb-2 text-slate-300" aria-hidden="true" />
                                <p>{filter ? 'Kein Lieferant gefunden' : `Keine Einkäufe in ${zeitraum}`}</p>
                            </div>
                        ) : (
                            <table className="w-full text-sm">
                                <thead className="sticky top-0 bg-slate-50 border-b border-slate-200 z-10">
                                    <tr className="text-left text-slate-600">
                                        <th
                                            scope="col"
                                            aria-sort={sortFeld === 'rang' ? sortRichtungAria : 'none'}
                                            className="px-4 py-2.5 font-medium w-16"
                                        >
                                            <button
                                                onClick={() => toggleSort('rang')}
                                                className="flex items-center gap-1 hover:text-rose-700 transition-colors"
                                            >
                                                Platz <SortIcon feld="rang" aktivesFeld={sortFeld} richtung={sortRichtung} />
                                            </button>
                                        </th>
                                        <th
                                            scope="col"
                                            aria-sort={sortFeld === 'name' ? sortRichtungAria : 'none'}
                                            className="px-2 py-2.5 font-medium"
                                        >
                                            <button
                                                onClick={() => toggleSort('name')}
                                                className="flex items-center gap-1 hover:text-rose-700 transition-colors"
                                            >
                                                Lieferant <SortIcon feld="name" aktivesFeld={sortFeld} richtung={sortRichtung} />
                                            </button>
                                        </th>
                                        <th
                                            scope="col"
                                            aria-sort={sortFeld === 'bestellungen' ? sortRichtungAria : 'none'}
                                            className="px-2 py-2.5 font-medium w-28 text-right"
                                        >
                                            <button
                                                onClick={() => toggleSort('bestellungen')}
                                                title="Zählt nur Bestellungen, zu denen eine Auftragsbestätigung vorliegt. Rechnungen ohne Auftragsbestätigung fehlen hier."
                                                className="flex items-center gap-1 ml-auto hover:text-rose-700 transition-colors"
                                            >
                                                Bestätigt <SortIcon feld="bestellungen" aktivesFeld={sortFeld} richtung={sortRichtung} />
                                            </button>
                                        </th>
                                        <th scope="col" className="px-2 py-2.5 font-medium w-32 text-right">Eingekauft</th>
                                        <th scope="col" className="px-4 py-2.5 font-medium w-40">Anteil</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-slate-100">
                                    {sichtbar.map(l => {
                                        const anteil = gesamtVolumen > 0 ? (l.netto / gesamtVolumen) * 100 : 0;
                                        const istHervorgehoben = l.rang === hervorgehobenerRang;
                                        const inGrafik = l.rang <= 10;
                                        return (
                                            <tr
                                                key={l.rang}
                                                className={istHervorgehoben ? 'bg-rose-50' : 'hover:bg-slate-50 transition-colors'}
                                            >
                                                <td className="px-4 py-3 tabular-nums text-slate-500">{l.rang}</td>
                                                <td className="px-2 py-3">
                                                    <p className="font-medium text-slate-900">{l.name}</p>
                                                    {inGrafik && (
                                                        <span className="text-xs text-rose-600">steht in der Grafik</span>
                                                    )}
                                                </td>
                                                <td className="px-2 py-3 text-right tabular-nums text-slate-600">
                                                    {l.bestellungen}
                                                </td>
                                                <td className="px-2 py-3 text-right tabular-nums font-semibold text-slate-900">
                                                    {formatEuro(l.netto)}
                                                </td>
                                                <td className="px-4 py-3">
                                                    <div className="flex items-center gap-2">
                                                        <div className="flex-1 h-1.5 bg-slate-100 rounded-full overflow-hidden">
                                                            <div
                                                                className="h-full bg-rose-500 rounded-full"
                                                                style={{ width: `${Math.max(2, anteil)}%` }}
                                                            />
                                                        </div>
                                                        <span className="text-xs text-slate-500 tabular-nums w-12 text-right">
                                                            {formatProzent(anteil)} %
                                                        </span>
                                                    </div>
                                                    {!inGrafik && (
                                                        <button
                                                            onClick={() => onHervorheben(istHervorgehoben ? null : l.rang)}
                                                            aria-label={istHervorgehoben
                                                                ? `${l.name} aus Grafik entfernen`
                                                                : `${l.name} in Grafik zeigen`}
                                                            className={`mt-1.5 text-xs font-medium transition-colors
                                                                ${istHervorgehoben
                                                                    ? 'text-rose-700 hover:text-rose-800'
                                                                    : 'text-slate-500 hover:text-rose-700'
                                                                }`}
                                                        >
                                                            {istHervorgehoben ? 'Aus Grafik entfernen' : 'In Grafik zeigen'}
                                                        </button>
                                                    )}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            </table>
                        )}
                    </div>

                    <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 flex items-center justify-between text-sm text-slate-500">
                        <span>
                            {sichtbar.length}
                            {sichtbar.length !== mitRang.length ? ` von ${mitRang.length}` : ''} Lieferanten
                        </span>
                        <span className="tabular-nums">Gesamt: {formatEuro(gesamtVolumen)}</span>
                    </div>
                </div>
            </div>

            <LieferantSearchModal
                isOpen={stammSucheOffen}
                onClose={() => setStammSucheOffen(false)}
                onSelect={handleStammAuswahl}
                nurAktive={false}
            />
        </>
    );
}
