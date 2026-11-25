import { InfoAbschnitt, InfoButton } from '../../components/ui/info-button';
import { Select } from '../../components/ui/select-custom';
import { LIEFERANT_ROLLEN } from '../../types';
import { LIEFERANTEN_SEITE, type LieferantenFilter } from './useLieferantenListe';

interface Props {
    filters: LieferantenFilter;
    onAendern: (key: keyof LieferantenFilter, value: string) => void;
    onZuruecksetzen: () => void;
    /** Enter im Formular: zurück auf Seite 1, ohne neue Anfrage. */
    onAbschicken: () => void;
}

const FELD = "filter-input w-full mt-1 px-3 py-2 border border-slate-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-rose-500";

/** Filter der Lieferanten-Übersicht. Gefiltert wird live bei jeder Eingabe, ohne Filtern-Knopf. */
export function LieferantenFilterLeiste({ filters, onAendern, onZuruecksetzen, onAbschicken }: Props) {
    return (
        <div className="bg-white p-6 rounded-2xl shadow-lg border border-slate-100">
            {/* Enter im Formular darf die Seite nicht neu laden; gefiltert wird ohnehin live. */}
            <form onSubmit={e => { e.preventDefault(); onAbschicken(); }} className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4">
                <div>
                    <label className="block text-sm font-medium text-gray-700">Freitext</label>
                    <input type="text" className={FELD} placeholder="Name, zweiter Name, Telefon, Ort, Ansprechpartner …" value={filters.q} onChange={e => onAendern('q', e.target.value)} />
                </div>
                <div>
                    <label className="block text-sm font-medium text-gray-700">Name</label>
                    <input type="text" className={FELD} placeholder="Lieferantenname" value={filters.name} onChange={e => onAendern('name', e.target.value)} />
                </div>
                <div>
                    <div className="flex items-center gap-1">
                        <label className="block text-sm font-medium text-gray-700">Rolle</label>
                        <InfoButton titel="Rolle">
                            <InfoAbschnitt titel="Was passiert im Hintergrund?">
                                <p>Jeder Lieferant hat eine oder mehrere Rollen, z. B. Stahlhandel oder Verzinkerei.</p>
                            </InfoAbschnitt>
                            <InfoAbschnitt titel="Was bewirkt das?">
                                <p>Der Filter zeigt nur Lieferanten mit dieser Rolle. Die Rollen steuern auch, welche Lieferanten beim Eintragen von Artikelpreisen vorgeschlagen werden.</p>
                            </InfoAbschnitt>
                        </InfoButton>
                    </div>
                    <Select
                        value={filters.rolle}
                        onChange={(value) => onAendern("rolle", value)}
                        options={[{ value: "", label: "Alle Rollen" }, ...LIEFERANT_ROLLEN]}
                        placeholder="Rolle filtern"
                        className="mt-1"
                        aria-label="Rolle"
                    />
                </div>
                <div>
                    <label className="block text-sm font-medium text-gray-700">Ort</label>
                    <input type="text" className={FELD} placeholder="Ort" value={filters.ort} onChange={e => onAendern('ort', e.target.value)} />
                </div>
                <div className="flex items-end">
                    <button type="button" className="btn-secondary flex-1 px-4 py-2 border rounded-lg hover:bg-slate-50" onClick={onZuruecksetzen}>Filter zurücksetzen</button>
                </div>
            </form>
            <p className="text-xs text-gray-500 mt-3">Für Performance werden immer nur {LIEFERANTEN_SEITE} Einträge auf einmal geladen.</p>
        </div>
    );
}
