import { useId, useState } from 'react';
import { ChevronDown, ChevronUp, Info, X } from 'lucide-react';
import { Button } from '../ui/button';
import { useToast } from '../ui/toast';
import { KasseAnleitungDialog } from './KasseAnleitungDialog';

const speicherSchluessel = 'kasse.erklaerkasten.zu';

export function KasseErklaerkasten() {
    const [zu, setZu] = useState(() => {
        try { return localStorage.getItem(speicherSchluessel) === '1'; }
        catch { return false; }
    });
    const [anleitungOffen, setAnleitungOffen] = useState(false);
    const inhaltId = useId();
    const toast = useToast();
    const umschalten = (geschlossen: boolean) => {
        setZu(geschlossen);
        try { localStorage.setItem(speicherSchluessel, geschlossen ? '1' : '0'); }
        catch { toast.error('Die Einstellung kann nicht dauerhaft gespeichert werden.'); }
    };
    return <>
        <section className="min-w-0 rounded-lg border border-indigo-200 bg-indigo-50 p-4 text-indigo-900">
            <div className="flex min-w-0 items-center justify-between gap-3">
                <h2 className="min-w-0 text-sm font-semibold">
                    <button type="button" aria-expanded={!zu} aria-controls={inhaltId} onClick={() => umschalten(!zu)}
                        className="flex min-w-0 items-center gap-2 rounded text-left hover:text-indigo-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                        <Info className="h-4 w-4 shrink-0" aria-hidden />
                        <span className="min-w-0 break-words">So funktioniert die Kasse</span>
                        {zu ? <ChevronDown className="h-4 w-4 shrink-0" aria-hidden /> : <ChevronUp className="h-4 w-4 shrink-0" aria-hidden />}
                    </button>
                </h2>
                {!zu && <button type="button" aria-label="Erklärung ausblenden" onClick={() => umschalten(true)}
                    className="shrink-0 rounded p-1.5 hover:bg-indigo-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                    <X className="h-4 w-4" aria-hidden />
                </button>}
            </div>
            <div hidden={zu} id={inhaltId} className="min-w-0 mt-3">
                <ol className="grid min-w-0 list-decimal gap-x-8 gap-y-2 pl-5 text-sm leading-relaxed lg:grid-cols-2">
                    <li className="min-w-0 break-words">Jede Barbewegung sofort eintragen — auch Bank-Abhebung und eigenes Geld.</li>
                    <li className="min-w-0 break-words">Zu jeder Zeile gehört ein Beleg. Fehlt einer, macht das Programm einen Eigenbeleg (ohne Vorsteuer).</li>
                    <li className="min-w-0 break-words">Die Kasse darf nie unter null. Vorher eigenes Geld einlegen.</li>
                    <li className="min-w-0 break-words">Einmal im Monat: Kasse zählen, dann den Monat abschließen. Danach ist er fest.</li>
                    <li className="min-w-0 break-words">Falsch gebucht? Nicht löschen — stornieren. Das Original bleibt sichtbar.</li>
                </ol>
                <Button variant="outline" size="sm" onClick={() => setAnleitungOffen(true)}
                    className="mt-3 border-indigo-200 bg-white text-indigo-900 hover:bg-indigo-100">Mehr dazu</Button>
            </div>
        </section>
        {anleitungOffen && <KasseAnleitungDialog onClose={() => setAnleitungOffen(false)} />}
    </>;
}
