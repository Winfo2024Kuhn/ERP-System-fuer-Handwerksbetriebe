import { useId, useState } from 'react';
import { Loader2, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '../../components/ui/dialog';
import { useToast } from '../../components/ui/toast';
import { InfoLeistungLoeschen } from './InfoTexte';
import { dokumentTypText, formatDatum, weitereAnzahl } from './ordnerLogik';
import type { LeistungVerwendung } from './typen';

export interface LeistungLoeschenDialogProps {
    name: string;
    verwendung: LeistungVerwendung;
    onAbbrechen: () => void;
    /** Wirft bei einem Fehler – die Meldung erscheint im Dialog. */
    onLoeschen: () => Promise<void>;
}

/** Rückfrage, wenn eine Leistung schon in Angeboten, Auftragsbestätigungen oder Rechnungen steckt. */
export function LeistungLoeschenDialog({ name, verwendung, onAbbrechen, onLoeschen }: LeistungLoeschenDialogProps) {
    const toast = useToast();
    const titelId = useId();
    const [sendet, setSendet] = useState(false);
    const [fehler, setFehler] = useState<string | null>(null);
    const anzahl = verwendung.dokumentAnzahl;
    const rest = weitereAnzahl(anzahl, verwendung.dokumente.length);

    const loeschen = async () => {
        setSendet(true);
        setFehler(null);
        try {
            await onLoeschen();
        } catch (err) {
            const meldung = err instanceof Error ? err.message : 'Die Leistung konnte nicht gelöscht werden.';
            setFehler(meldung);
            toast.error(meldung);
            setSendet(false);
        }
    };

    return (
        <Dialog open onOpenChange={(offen) => { if (!offen && !sendet) onAbbrechen(); }} className="w-full max-w-2xl" aria-labelledby={titelId}>
            <DialogContent>
                <DialogHeader className="pr-10">
                    <DialogTitle id={titelId}>„{name}“ steckt in {anzahl === 1 ? 'einem Dokument' : `${anzahl} Dokumenten`}</DialogTitle>
                    <DialogDescription>
                        Löschen geht trotzdem – die Dokumente bleiben, wie sie sind.
                    </DialogDescription>
                </DialogHeader>
                <div className="flex items-center gap-1.5 text-sm font-medium text-slate-700">
                    Was passiert beim Löschen? <InfoLeistungLoeschen />
                </div>
                <div className="-mx-1 min-h-0 flex-1 overflow-y-auto px-1">
                    <table className="w-full text-sm">
                        <thead>
                            <tr className="border-b border-slate-200 text-left text-xs font-semibold text-slate-500">
                                <th className="py-2 pr-3">Nummer</th>
                                <th className="py-2 pr-3">Art</th>
                                <th className="py-2 pr-3">Datum</th>
                                <th className="py-2">Bauvorhaben</th>
                            </tr>
                        </thead>
                        <tbody>
                            {verwendung.dokumente.map((d) => (
                                <tr key={d.id} className="border-b border-slate-100 last:border-0">
                                    <td className="py-1.5 pr-3 font-medium text-slate-800 tabular-nums">{d.dokumentNummer || '—'}</td>
                                    <td className="py-1.5 pr-3 text-slate-700">{dokumentTypText(d.typ)}</td>
                                    <td className="py-1.5 pr-3 text-slate-600 tabular-nums">{formatDatum(d.datum) || '—'}</td>
                                    <td className="py-1.5 text-slate-600">{d.bauvorhaben || '—'}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    {rest > 0 && <p className="mt-2 text-sm text-slate-500">und {rest} weitere</p>}
                </div>
                {fehler && <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">{fehler}</p>}
                <DialogFooter className="gap-2">
                    <Button type="button" variant="outline" onClick={onAbbrechen} disabled={sendet}>Abbrechen</Button>
                    <Button type="button" onClick={() => void loeschen()} disabled={sendet}
                        className="border-red-600 bg-red-600 hover:bg-red-700 focus-visible:ring-red-500">
                        {sendet ? <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" /> : <Trash2 className="h-4 w-4" aria-hidden="true" />}
                        Trotzdem löschen
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
