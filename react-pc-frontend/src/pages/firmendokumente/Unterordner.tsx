import type { DragEvent } from 'react';
import { Folder } from 'lucide-react';
import { cn } from '../../lib/utils';
import { schreibeGezogenenOrdner } from './ordnerZiehen';
import type { FirmenOrdner } from './typen';
import { useAblageZiel, type AblageRegeln } from './useAblageZiel';

/** Was man mit einem Unterordner im rechten Bereich machen kann. */
export interface UnterordnerAktionen {
    /** Ordner öffnen – wie ein Klick im Baum. */
    onOeffnen: (ordnerId: number) => void;
    /** Ablegen von Dateien, Dokumenten und Ordnern – dieselben Regeln wie im Baum. */
    ablage: AblageRegeln;
}

/** „5 Dokumente“ – mit geschütztem Leerzeichen, damit Zahl und Wort nicht auseinanderbrechen (`trenner` für Vorlesenamen). */
const anzahlText = (n: number, trenner = '\u00a0') => `${n}${trenner}${n === 1 ? 'Dokument' : 'Dokumente'}`;

/** Ziehen und Ablegen eines Unterordners – gleich für Tabellenzeile und Kachel. */
function useUnterordnerZiehen(ordner: FirmenOrdner, ablage: AblageRegeln) {
    const ziel = useAblageZiel(ablage, ordner.id);
    return {
        aktiv: ziel.aktiv,
        ereignisse: {
            draggable: ordner.darfAendern || undefined,
            onDragStart: ordner.darfAendern ? (e: DragEvent) => schreibeGezogenenOrdner(e.dataTransfer, ordner) : undefined,
            onDragOver: ziel.onDragOver,
            onDragLeave: ziel.onDragLeave,
            onDrop: ziel.onDrop,
        },
    };
}

/** Unterordner als Tabellenzeile vor den Dokumenten: Ordnersymbol, Name mit Anzahl Dokumente darunter, Dateityp „Ordner“, Größe „–“. */
export function UnterordnerZeile({ ordner, aktionen, spalten }: { ordner: FirmenOrdner; aktionen: UnterordnerAktionen; spalten: number }) {
    const { aktiv, ereignisse } = useUnterordnerZiehen(ordner, aktionen.ablage);
    return (
        <tr {...ereignisse} className={cn('hover:bg-slate-50', ordner.darfAendern && 'cursor-grab active:cursor-grabbing',
            aktiv && 'bg-rose-50 outline outline-2 -outline-offset-2 outline-rose-400')}>
            <td className="max-w-0 px-3 py-2 sm:w-2/5">
                <button type="button" onClick={() => aktionen.onOeffnen(ordner.id)} title={`„${ordner.name}“ öffnen`}
                    className="flex w-full min-w-0 items-center gap-2 rounded text-left font-medium text-slate-900 hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                    <Folder className="h-4 w-4 shrink-0 text-slate-400" aria-hidden="true" />
                    <span className="min-w-0 break-words">{ordner.name}</span>
                </button>
                <span className="block pl-6 text-xs text-slate-500">{anzahlText(ordner.anzahlDokumente)}</span>
            </td>
            <td className="whitespace-nowrap px-3 py-2 text-slate-600">Ordner</td>
            {/* Fällig am und Hochgeladen am (und ggf. Ordner) gibt es für Ordner nicht. */}
            <td colSpan={spalten - 4} />
            {/* Größe: wie im Explorer keine für Ordner. */}
            <td className="whitespace-nowrap px-3 py-2 text-right text-slate-400">–</td>
            <td />
        </tr>
    );
}

/** Unterordner als Kachel vor den Dokumenten: großes Ordnersymbol, Name, Anzahl Dokumente. */
export function UnterordnerKachel({ ordner, aktionen }: { ordner: FirmenOrdner; aktionen: UnterordnerAktionen }) {
    const { aktiv, ereignisse } = useUnterordnerZiehen(ordner, aktionen.ablage);
    return (
        <li {...ereignisse} className={cn('min-w-0 rounded-lg', aktiv && 'bg-rose-50 ring-2 ring-inset ring-rose-400')}>
            <button type="button" onClick={() => aktionen.onOeffnen(ordner.id)} aria-label={`${ordner.name}, Ordner, ${anzahlText(ordner.anzahlDokumente, ' ')}`}
                className="flex w-full flex-col gap-1.5 rounded-lg p-1 text-left hover:bg-slate-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                <span className="flex aspect-square w-full items-center justify-center rounded-lg border border-slate-200 bg-slate-50">
                    <Folder className="h-2/5 w-2/5 text-slate-400" strokeWidth={1.5} aria-hidden="true" />
                </span>
                <span className="line-clamp-2 break-words px-0.5 text-sm font-medium text-slate-900" title={ordner.name} data-kuerzung-erlaubt>{ordner.name}</span>
                <span className="px-0.5 text-xs text-slate-500">Ordner · {anzahlText(ordner.anzahlDokumente)}</span>
            </button>
        </li>
    );
}
