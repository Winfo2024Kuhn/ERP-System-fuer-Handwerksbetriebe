import type { ReactNode } from 'react';
import { File, FileImage, FileText, type LucideIcon } from 'lucide-react';
import { MehrLadenLeiste } from '../../components/ui/mehr-laden-leiste';
import { LadeAnzeige, VerbindungsHinweis } from '../../components/ui/lade-anzeige';
import { formatBytes } from '../../lib/dateiGroesse';
import { DokumentZeilenAktionen, type DokumentAktionen } from './DokumentZeilenAktionen';
import { FaelligAmAnzeige } from './FaelligAmAnzeige';
import { formatDatum } from '../../lib/datum';
import { cn } from '../../lib/utils';
import { dateityp, schreibeGezogenesDokument } from './firmendokumenteLogik';
import type { Dateiart, FirmenDokument } from './typen';

const SYMBOL: Record<Dateiart, LucideIcon> = { PDF: FileText, BILD: FileImage, SONSTIGE: File };

export interface DokumentListeProps {
    /** Vorlese-Name der Tabelle, z. B. „Dokumente in Tore“. */
    beschriftung: string;
    dokumente: FirmenDokument[];
    gesamt: number;
    laedt: boolean;
    laedtMehr: boolean;
    fehler: string | null;
    leer: ReactNode;
    /** Pfad eines Ordners – gesetzt in der Übersicht „Fällige Dokumente“ (Spalte „Ordner“). Die Suche zeigt keine Ordner-Spalte. */
    ordnerPfad?: (ordnerId: number) => string;
    aktionen: DokumentAktionen;
    onMehrLaden: () => void;
    onErneut: () => void;
}

/**
 * Tabelle der Dokumente: Symbol je Dateiart, Name (Klick öffnet bei PDF und
 * Bild die Vorschau, lädt andere Dateien herunter), Dateityp (Endung der
 * Originaldatei in Großbuchstaben, z. B. „PDF“ oder „SZA“, sonst „–“),
 * optional Ordner, Fällig am, Hochgeladen am, Größe, Aktionen. Unten „Mehr laden“, solange
 * der Server mehr hat. Laden, leer und Fehler sehen verschieden aus.
 * Dokumente mit Recht „Ändern“ lassen sich auf einen Ordner im Baum ziehen –
 * nur so werden sie verschoben (mit der Maus, ohne Tastatur-Weg).
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
                            <th scope="col" className="px-3 py-2">Dateityp</th>
                            {ordnerPfad && <th scope="col" className="px-3 py-2">Ordner</th>}
                            <th scope="col" className="px-3 py-2">Fällig am</th>
                            <th scope="col" className="px-3 py-2">Hochgeladen am</th>
                            <th scope="col" className="px-3 py-2 text-right">Größe</th>
                            <th scope="col" className="px-3 py-2 text-right"><span className="sr-only">Aktionen</span></th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100 bg-white">
                        {dokumente.map((dok) => <DokumentZeile key={dok.id} dok={dok} ordnerPfad={ordnerPfad} aktionen={aktionen} />)}
                    </tbody>
                </table>
            </div>
            <MehrLadenLeiste angezeigt={dokumente.length} gesamt={gesamt} laedtMehr={laedtMehr} onMehrLaden={onMehrLaden} />
        </div>
    );
}

/** Eine Zeile der Tabelle. Mit Recht „Ändern“ lässt sie sich auf einen Ordner im Baum ziehen, um sie zu verschieben. */
function DokumentZeile({ dok, ordnerPfad, aktionen }: { dok: FirmenDokument; ordnerPfad?: (ordnerId: number) => string; aktionen: DokumentAktionen }) {
    const Symbol = SYMBOL[dok.dateiart] ?? File;
    const nurHerunterladen = dok.dateiart === 'SONSTIGE';
    return (
        <tr
            className={cn('hover:bg-slate-50', dok.darfAendern && 'cursor-grab active:cursor-grabbing')}
            draggable={dok.darfAendern || undefined}
            onDragStart={dok.darfAendern ? (e) => { schreibeGezogenesDokument(e.dataTransfer, dok); e.dataTransfer.effectAllowed = 'move'; } : undefined}
        >
            <td className="max-w-0 px-3 py-2 sm:w-2/5">
                <button type="button" onClick={() => aktionen.ansehen(dok)}
                    title={nurHerunterladen ? `„${dok.name}“ herunterladen` : `„${dok.name}“ ansehen`}
                    className="flex w-full min-w-0 items-center gap-2 rounded text-left font-medium text-slate-900 hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                    <Symbol className={cn('h-4 w-4 shrink-0', nurHerunterladen ? 'text-slate-400' : 'text-rose-500')} aria-hidden="true" />
                    <span className="min-w-0 break-words">{dok.name}</span>
                </button>
            </td>
            <td className="whitespace-nowrap px-3 py-2 text-slate-600">{dateityp(dok) ?? <span className="text-slate-400">–</span>}</td>
            {ordnerPfad && <td className="px-3 py-2 text-slate-600">{ordnerPfad(dok.ordnerId)}</td>}
            <td className="px-3 py-2"><FaelligAmAnzeige faelligAm={dok.faelligAm} /></td>
            <td className="whitespace-nowrap px-3 py-2 tabular-nums text-slate-600" title={dok.hochgeladenVon ? `von ${dok.hochgeladenVon}` : undefined}>
                {formatDatum(dok.hochgeladenAm)}
            </td>
            <td className="whitespace-nowrap px-3 py-2 text-right tabular-nums text-slate-600">{formatBytes(dok.groesseBytes)}</td>
            <td className="px-3 py-1.5 text-right"><DokumentZeilenAktionen dokument={dok} aktionen={aktionen} /></td>
        </tr>
    );
}
