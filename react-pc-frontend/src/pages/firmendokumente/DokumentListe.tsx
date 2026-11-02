import type { ReactNode } from 'react';
import { FileText, Loader2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { LadeAnzeige, VerbindungsHinweis } from '../../components/ui/lade-anzeige';
import { formatBytes } from '../../lib/dateiGroesse';
import { DokumentZeilenAktionen, type DokumentAktionen } from './DokumentZeilenAktionen';
import { FaelligAmAnzeige } from './FaelligAmAnzeige';
import { formatDatum } from '../../lib/datum';
import type { FirmenDokument } from './typen';

export interface DokumentListeProps {
    /** Vorlese-Name der Tabelle, z. B. „Dokumente in Tore“. */
    beschriftung: string;
    dokumente: FirmenDokument[];
    gesamt: number;
    laedt: boolean;
    laedtMehr: boolean;
    fehler: string | null;
    leer: ReactNode;
    /** Pfad eines Ordners – gesetzt, wenn die Liste Dokumente aus mehreren Ordnern zeigt. */
    ordnerPfad?: (ordnerId: number) => string;
    aktionen: DokumentAktionen;
    onMehrLaden: () => void;
    onErneut: () => void;
}

/**
 * Tabelle der PDFs: Name (Klick öffnet die Vorschau), optional Ordner,
 * Fällig am, Hochgeladen am, Größe, Aktionen. Unten „Mehr laden“, solange
 * der Server mehr hat. Laden, leer und Fehler sehen verschieden aus.
 */
export function DokumentListe(props: DokumentListeProps) {
    const { beschriftung, dokumente, gesamt, laedt, laedtMehr, fehler, leer, ordnerPfad, aktionen, onMehrLaden, onErneut } = props;

    if (dokumente.length === 0) {
        if (laedt) return <LadeAnzeige text="Dokumente werden geladen …" onErneut={onErneut} />;
        if (fehler) return <div className="py-16"><VerbindungsHinweis text={fehler} onErneut={onErneut} /></div>;
        return <div className="py-16 text-center text-sm text-slate-500">{leer}</div>;
    }

    return (
        <div className="space-y-3">
            <div className="overflow-x-auto rounded-lg border border-slate-200">
                <table className="w-full text-sm" aria-label={beschriftung} aria-busy={laedt}>
                    <thead className="whitespace-nowrap bg-slate-50 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                        <tr>
                            <th scope="col" className="px-3 py-2">Name</th>
                            {ordnerPfad && <th scope="col" className="px-3 py-2">Ordner</th>}
                            <th scope="col" className="px-3 py-2">Fällig am</th>
                            <th scope="col" className="px-3 py-2">Hochgeladen am</th>
                            <th scope="col" className="px-3 py-2 text-right">Größe</th>
                            <th scope="col" className="px-3 py-2 text-right"><span className="sr-only">Aktionen</span></th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 bg-white">
                        {dokumente.map((dok) => (
                            <tr key={dok.id} className="hover:bg-slate-50">
                                <td className="max-w-0 px-3 py-2 sm:w-2/5">
                                    <button type="button" onClick={() => aktionen.ansehen(dok)} title={`„${dok.name}“ ansehen`}
                                        className="flex w-full min-w-0 items-center gap-2 rounded text-left font-medium text-slate-900 hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                                        <FileText className="h-4 w-4 shrink-0 text-rose-500" aria-hidden="true" />
                                        <span className="min-w-0 break-words">{dok.name}</span>
                                    </button>
                                </td>
                                {ordnerPfad && <td className="px-3 py-2 text-slate-600">{ordnerPfad(dok.ordnerId)}</td>}
                                <td className="px-3 py-2"><FaelligAmAnzeige faelligAm={dok.faelligAm} /></td>
                                <td className="whitespace-nowrap px-3 py-2 tabular-nums text-slate-600" title={dok.hochgeladenVon ? `von ${dok.hochgeladenVon}` : undefined}>
                                    {formatDatum(dok.hochgeladenAm)}
                                </td>
                                <td className="whitespace-nowrap px-3 py-2 text-right tabular-nums text-slate-600">{formatBytes(dok.groesseBytes)}</td>
                                <td className="px-3 py-1.5 text-right"><DokumentZeilenAktionen dokument={dok} aktionen={aktionen} /></td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
            <div className="flex items-center justify-between gap-3 text-sm text-slate-500">
                <span className="tabular-nums">{dokumente.length} von {gesamt} angezeigt</span>
                {dokumente.length < gesamt && (
                    <Button variant="outline" size="sm" onClick={onMehrLaden} disabled={laedtMehr}>
                        {laedtMehr && <Loader2 className="h-4 w-4 motion-safe:animate-spin" aria-hidden="true" />}
                        Mehr laden
                    </Button>
                )}
            </div>
        </div>
    );
}
