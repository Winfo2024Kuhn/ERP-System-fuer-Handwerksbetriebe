import { Diamond } from 'lucide-react';

interface AlternativVerbinderProps {
    /** Name der Gruppe, der beigetreten wird — null = neue Gruppe anlegen. */
    zielGruppe: string | null;
    onVerbinden: () => void;
}

/**
 * Erscheint zwischen zwei benachbarten Wahlpositionen und fasst sie zu einer
 * Entweder-Oder-Gruppe zusammen.
 *
 * Bewusst kein Mehrfach-Auswahlmodus: Varianten einer Gruppe muessen ohnehin
 * nebeneinander liegen (sonst kann der Editor den Kasten nicht zeichnen und
 * buildPositionMap keine a/b-Nummern vergeben). Der Knopf zwischen genau zwei
 * Nachbarn deckt damit jeden gueltigen Fall ab — auch die dritte Variante, die
 * einfach an eine bestehende Gruppe angrenzt.
 */
export function AlternativVerbinder({ zielGruppe, onVerbinden }: AlternativVerbinderProps) {
    return (
        <div className="flex justify-center -my-0.5 py-1 opacity-0 hover:opacity-100 focus-within:opacity-100 transition-opacity">
            <button
                type="button"
                onClick={(e) => { e.stopPropagation(); onVerbinden(); }}
                className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full border border-dashed border-amber-400 bg-white text-amber-700 text-[11px] font-medium hover:bg-amber-50 hover:border-amber-500 hover:shadow-sm focus:outline-none focus-visible:ring-2 focus-visible:ring-amber-400/40 transition-all"
            >
                <Diamond className="w-3 h-3" />
                {zielGruppe
                    ? `Zur Auswahl „${zielGruppe}" hinzufügen`
                    : 'Zur Auswahl zusammenfassen'}
            </button>
        </div>
    );
}
