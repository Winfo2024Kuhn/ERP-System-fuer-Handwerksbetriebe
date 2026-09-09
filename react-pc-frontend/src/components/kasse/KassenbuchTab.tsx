import type { Kassenbuch, Sachkonto } from '../../types';
import { KasseShortcuts } from './KasseShortcuts';
import { KassenbuchJournal } from './KassenbuchJournal';

// Task 9 (reine Verschiebung, kein Verhalten geaendert): neu zusammengesetzt
// aus dem activeTab === 'kasse'-Ast der Seite (BelegeKasseEditor.tsx:530-548).
// Rendert KasseShortcuts und KassenbuchJournal und bekommt alle Werte per
// Props -- gleiche Reihenfolge, gleiche Klassen, gleiche Aufrufe wie vorher.

export function KassenbuchTab({
    sachkonten,
    kassenbuch,
    kassenLoading,
    kasseVon,
    kasseBis,
    onVonChange,
    onBisChange,
    kasseSearch,
    onSearchChange,
    onSelectBeleg,
    onGeaendert,
}: {
    sachkonten: Sachkonto[];
    kassenbuch: Kassenbuch | null;
    kassenLoading: boolean;
    kasseVon: string;
    kasseBis: string;
    onVonChange: (v: string) => void;
    onBisChange: (v: string) => void;
    kasseSearch: string;
    onSearchChange: (v: string) => void;
    onSelectBeleg: (id: number) => void;
    onGeaendert: () => void;
}) {
    return (
        <div className="space-y-3">
            <KasseShortcuts
                sachkonten={sachkonten}
                onChanged={onGeaendert}
            />
            <KassenbuchJournal
                kassenbuch={kassenbuch}
                loading={kassenLoading}
                von={kasseVon}
                bis={kasseBis}
                onVonChange={onVonChange}
                onBisChange={onBisChange}
                search={kasseSearch}
                onSearchChange={onSearchChange}
                onSelectBeleg={onSelectBeleg}
                onGeaendert={onGeaendert}
            />
        </div>
    );
}
