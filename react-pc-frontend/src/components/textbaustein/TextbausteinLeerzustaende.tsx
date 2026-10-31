import { FileText, Plus, RefreshCw, SearchX } from 'lucide-react';
import { Button } from '../ui/button';

/** Noch gar keine Textbausteine: kurz erklären, wofür sie da sind. */
export function KeineTextbausteine({ onAnlegen }: { onAnlegen: () => void }) {
    return (
        <div className="flex flex-col items-center rounded-lg border border-dashed border-slate-300 bg-white px-6 py-16 text-center">
            <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-rose-100 text-rose-600">
                <FileText className="h-6 w-6" aria-hidden="true" />
            </div>
            <h2 className="text-lg font-semibold text-slate-900">Noch keine Textbausteine</h2>
            <p className="mt-2 max-w-md text-sm text-slate-500">
                Ein Textbaustein ist ein fertiger Text, den Sie immer wieder brauchen – etwa die Begrüßung im
                Angebot oder der Hinweis zur Zahlung in der Rechnung. Sie fügen ihn im Dokument mit einem Klick ein.
            </p>
            <Button className="mt-6" size="sm" onClick={onAnlegen}>
                <Plus className="h-4 w-4" aria-hidden="true" /> Ersten Textbaustein anlegen
            </Button>
        </div>
    );
}

/** Filter ohne Treffer, mit Weg zurück. */
export function KeineTreffer({ beschreibung, onZuruecksetzen }: { beschreibung: string; onZuruecksetzen: () => void }) {
    return (
        <div className="flex flex-col items-center rounded-lg border border-dashed border-slate-300 bg-white px-6 py-14 text-center">
            <SearchX className="mb-3 h-6 w-6 text-slate-400" aria-hidden="true" />
            <h2 className="text-base font-semibold text-slate-900">{beschreibung}</h2>
            <p className="mt-1 text-sm text-slate-500">Ändern Sie die Suche oder zeigen Sie wieder alle Textbausteine.</p>
            <Button className="mt-5" variant="outline" size="sm" onClick={onZuruecksetzen}>Filter zurücksetzen</Button>
        </div>
    );
}

/** Laden ist fehlgeschlagen (der Toast nennt den Grund). */
export function LadenFehlgeschlagen({ onErneut }: { onErneut: () => void }) {
    return (
        <div role="alert" className="flex flex-col items-center rounded-lg border border-red-200 bg-red-50 px-6 py-12 text-center">
            <h2 className="text-base font-semibold text-red-800">Die Textbausteine konnten nicht geladen werden.</h2>
            <p className="mt-1 text-sm text-red-700">Prüfen Sie die Verbindung und versuchen Sie es noch einmal.</p>
            <Button className="mt-5" variant="outline" size="sm" onClick={onErneut}>
                <RefreshCw className="h-4 w-4" aria-hidden="true" /> Erneut versuchen
            </Button>
        </div>
    );
}
