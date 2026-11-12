import { formatBytes } from '../../lib/dateiGroesse';
import { formatDatum } from '../../lib/datum';
import { cn } from '../../lib/utils';
import { DateiSymbol } from './DateiSymbol';
import { DokumentZeilenAktionen, type DokumentAktionen } from './DokumentZeilenAktionen';
import { FaelligAmAnzeige } from './FaelligAmAnzeige';
import { dateityp, schreibeGezogenesDokument } from './firmendokumenteLogik';
import type { FirmenDokument, FirmenOrdner } from './typen';
import { UnterordnerZeile, type UnterordnerAktionen } from './Unterordner';

export interface DokumentTabelleProps {
    beschriftung: string;
    dokumente: FirmenDokument[];
    laedt: boolean;
    ordnerPfad?: (ordnerId: number) => string;
    aktionen: DokumentAktionen;
    unterordner?: readonly FirmenOrdner[];
    ordnerAktionen?: UnterordnerAktionen;
}

/**
 * Tabelle der Dokumente: erst die Unterordner (wie im Explorer), dann je
 * Dokument Symbol, Name (Klick öffnet bei PDF und Bild die Vorschau, lädt
 * andere Dateien herunter), Dateityp, optional Ordner, Fällig am,
 * Hochgeladen am, Größe, Aktionen.
 */
export function DokumentTabelle({ beschriftung, dokumente, laedt, ordnerPfad, aktionen, unterordner = [], ordnerAktionen }: DokumentTabelleProps) {
    const spalten = ordnerPfad ? 7 : 6;
    return (
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
                    {ordnerAktionen && unterordner.map((o) => <UnterordnerZeile key={`ordner-${o.id}`} ordner={o} aktionen={ordnerAktionen} spalten={spalten} />)}
                    {dokumente.map((dok) => <DokumentZeile key={dok.id} dok={dok} ordnerPfad={ordnerPfad} aktionen={aktionen} />)}
                </tbody>
            </table>
        </div>
    );
}

/** Eine Zeile der Tabelle. Mit Recht „Ändern“ lässt sie sich auf einen Ordner ziehen, um sie zu verschieben. */
function DokumentZeile({ dok, ordnerPfad, aktionen }: { dok: FirmenDokument; ordnerPfad?: (ordnerId: number) => string; aktionen: DokumentAktionen }) {
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
                    <DateiSymbol dateiart={dok.dateiart} className={cn('h-4 w-4 shrink-0', nurHerunterladen ? 'text-slate-400' : 'text-rose-500')} />
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
