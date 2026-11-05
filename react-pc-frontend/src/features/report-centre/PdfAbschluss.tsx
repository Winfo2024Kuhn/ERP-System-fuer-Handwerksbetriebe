import { FileDown, Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { DialogFooter } from '../../components/ui/dialog';
import type { BerichtAbschlussProps } from './berichtTypen';
import { zusammenfassung } from './berichtWerte';
import { usePdfErstellen } from './usePdfErstellen';

/**
 * Letzter Schritt für Berichte ohne eigene Vorschau: Zusammenfassung der
 * Auswahl und „PDF erstellen“. Gilt für jeden neuen Bericht ohne Sonderwunsch.
 */
export function PdfAbschluss(props: BerichtAbschlussProps) {
    const { definition, werte, mitarbeiter, zurueck } = props;
    const url = definition.pdfUrl?.(werte, mitarbeiter.map(m => m.id)) ?? null;
    const { erstellen, laedt } = usePdfErstellen(props, url);
    const zeilen = zusammenfassung(definition.filterFelder, werte, mitarbeiter);

    return (
        <>
            <div className="min-h-0 flex-1 overflow-y-auto">
                <p className="mb-3 text-sm text-slate-600">Bitte prüfe deine Auswahl. Mit „PDF erstellen“ wird der Bericht heruntergeladen.</p>
                <dl className="divide-y divide-slate-100 rounded-lg border border-slate-200">
                    {zeilen.map(([beschriftung, wert]) => (
                        <div key={beschriftung} className="grid grid-cols-1 gap-1 px-4 py-2.5 text-sm sm:grid-cols-[12rem_1fr]">
                            <dt className="font-medium text-slate-500">{beschriftung}</dt>
                            <dd className="text-slate-900">{wert}</dd>
                        </div>
                    ))}
                </dl>
            </div>
            <DialogFooter className="gap-2 sm:justify-between">
                {zurueck}
                <Button onClick={erstellen} disabled={laedt || !url}>
                    {laedt
                        ? <Loader2 aria-hidden="true" className="h-4 w-4 motion-safe:animate-spin" />
                        : <FileDown aria-hidden="true" className="h-4 w-4" />}
                    {laedt ? 'PDF wird erstellt …' : 'PDF erstellen'}
                </Button>
            </DialogFooter>
        </>
    );
}
