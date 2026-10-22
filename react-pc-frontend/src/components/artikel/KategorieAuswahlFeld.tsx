import { Search, X } from "lucide-react";

interface Props {
    /** Angezeigter Pfad der gewählten Kategorie; leer = noch keine gewählt. */
    name: string;
    /** Ist eine Kategorie gewählt? Nur dann gibt es den Knopf zum Entfernen. */
    gewaehlt: boolean;
    onOeffnen: () => void;
    onEntfernen: () => void;
}

/**
 * Auswahlfeld für eine Kategorie: Ein Klick öffnet das Auswahlfenster, das (x) entfernt die Wahl.
 * Auswählen und Entfernen sind zwei getrennte Knöpfe nebeneinander (kein Knopf im Knopf), damit Tastatur und Screenreader sie unterscheiden.
 */
export function KategorieAuswahlFeld({ name, gewaehlt, onOeffnen, onEntfernen }: Props) {
    return (
        <div className="relative">
            <button type="button" onClick={onOeffnen}
                className="flex h-10 w-full items-center justify-between rounded-md border border-slate-200 bg-white py-2 pl-3 pr-10 text-left text-sm text-slate-700 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-rose-500">
                <span className="truncate">{name || "Kategorie wählen"}</span>
                {!gewaehlt && <Search className="w-4 h-4 text-slate-400" aria-hidden="true" />}
            </button>
            {gewaehlt && (
                <button type="button" aria-label="Kategorie entfernen" onClick={onEntfernen}
                    className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-slate-400 hover:text-rose-600 focus:outline-none focus:ring-2 focus:ring-rose-500">
                    <X className="w-4 h-4" aria-hidden="true" />
                </button>
            )}
        </div>
    );
}
