import { memo } from 'react';
import { ThumbnailImage } from '../../components/ui/ThumbnailImage';
import { toSafeResourceUrl } from '../../lib/htmlSanitizer';
import { cn } from '../../lib/utils';
import { vorschaubildUrl } from './api';
import { DateiSymbol } from './DateiSymbol';
import { DokumentZeilenAktionen, type DokumentAktionen } from './DokumentZeilenAktionen';
import { FaelligAmAnzeige } from './FaelligAmAnzeige';
import { dateityp, schreibeGezogenesDokument } from './firmendokumenteLogik';
import type { FirmenDokument, FirmenOrdner } from './typen';
import { UnterordnerKachel, type UnterordnerAktionen } from './Unterordner';

/** Ist der Server beim ersten Öffnen eines großen Fotoordners ausgelastet (503), einmal nach 5 s erneut fragen. */
const VORSCHAU_WIEDERHOLEN = { anzahl: 1, nachMs: 5000 };

/** CSS-Variable mit der Kachelbreite – setzt der Regler am umgebenden Element, damit beim Ziehen keine Kachel neu rendert. */
export const KACHEL_GROESSE_VARIABLE = '--kachel-groesse';

export interface DokumentKachelnProps {
    /** Vorlese-Name der Liste, z. B. „Dokumente in Tore“. */
    beschriftung: string;
    dokumente: FirmenDokument[];
    aktionen: DokumentAktionen;
    /** Pfad eines Ordners – nur in der Übersicht „Fällige Dokumente“. */
    ordnerPfad?: (ordnerId: number) => string;
    /** Unterordner des gewählten Ordners, vor den Dokumenten. */
    unterordner?: readonly FirmenOrdner[];
    ordnerAktionen?: UnterordnerAktionen;
}

/**
 * Vorschau wie im Explorer: erst die Unterordner, dann je Dokument eine
 * Kachel mit Vorschaubild (nur PDF und Bild; sonst das Symbol der Dateiart),
 * Name, Dateityp und Fällig am. Die Breite kommt aus der CSS-Variable
 * {@link KACHEL_GROESSE_VARIABLE} am umgebenden Element.
 */
export const DokumentKacheln = memo(function DokumentKacheln({ beschriftung, dokumente, aktionen, ordnerPfad, unterordner = [], ordnerAktionen }: DokumentKachelnProps) {
    return (
        <ul
            aria-label={beschriftung}
            className="grid gap-3"
            style={{ gridTemplateColumns: `repeat(auto-fill, minmax(var(${KACHEL_GROESSE_VARIABLE}, 160px), 1fr))` }}
        >
            {ordnerAktionen && unterordner.map((o) => <UnterordnerKachel key={`ordner-${o.id}`} ordner={o} aktionen={ordnerAktionen} />)}
            {dokumente.map((dok) => <DokumentKachel key={dok.id} dok={dok} aktionen={aktionen} ordnerPfad={ordnerPfad} />)}
        </ul>
    );
});

function DokumentKachel({ dok, aktionen, ordnerPfad }: { dok: FirmenDokument; aktionen: DokumentAktionen; ordnerPfad?: (ordnerId: number) => string }) {
    const nurHerunterladen = dok.dateiart === 'SONSTIGE';
    const pfad = ordnerPfad?.(dok.ordnerId);
    return (
        <li
            className={cn('group min-w-0', dok.darfAendern && 'cursor-grab active:cursor-grabbing')}
            draggable={dok.darfAendern || undefined}
            onDragStart={dok.darfAendern ? (e) => { schreibeGezogenesDokument(e.dataTransfer, dok); e.dataTransfer.effectAllowed = 'move'; } : undefined}
        >
            <button type="button" onClick={() => aktionen.ansehen(dok)}
                title={nurHerunterladen ? `„${dok.name}“ herunterladen` : `„${dok.name}“ ansehen`}
                className="flex w-full flex-col gap-1.5 rounded-lg p-1 text-left hover:bg-slate-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                <span className="flex aspect-square w-full items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-slate-50">
                    <Vorschaubild dok={dok} />
                </span>
                <span className="line-clamp-2 break-words px-0.5 text-sm font-medium text-slate-900" title={dok.name} data-kuerzung-erlaubt>{dok.name}</span>
            </button>
            <div className="flex items-start justify-between gap-1 pl-1.5">
                <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1 pt-1 text-xs text-slate-500">
                    <span>{dateityp(dok) ?? '–'}</span>
                    {dok.faelligAm && <FaelligAmAnzeige faelligAm={dok.faelligAm} className="px-1.5 text-xs" />}
                </div>
                {/* Neben, nicht über der Kachel: so überdeckt das Menü nie das Bild oder den Namen. */}
                {/* Immer schwach sichtbar (auffindbar), bei Hover, Fokus und offenem Menü voll. */}
                <div className="shrink-0 opacity-60 focus-within:opacity-100 group-hover:opacity-100 has-[[data-state=open]]:opacity-100">
                    <DokumentZeilenAktionen dokument={dok} aktionen={aktionen} nurMenue />
                </div>
            </div>
            {pfad && <p className="truncate px-1.5 text-xs text-slate-500" title={pfad} data-kuerzung-erlaubt>{pfad}</p>}
        </li>
    );
}

/**
 * Vorschaubild vom Server – bei PDFs die erste Seite. Klappt es nicht (kein Vorschaubild, kaputte Datei,
 * Server ausgelastet), wird es einmal nach 5 s erneut versucht, danach steht das Symbol der Dateiart da.
 */
function Vorschaubild({ dok }: { dok: FirmenDokument }) {
    const ersatz = (
        <span className="flex h-full w-full items-center justify-center">
            <DateiSymbol dateiart={dok.dateiart} className={cn('h-1/3 max-h-16 w-1/3 max-w-16', dok.dateiart === 'SONSTIGE' ? 'text-slate-400' : 'text-rose-400')} strokeWidth={1.5} />
        </span>
    );
    const quelle = dok.dateiart === 'SONSTIGE' ? undefined : toSafeResourceUrl(vorschaubildUrl(dok.id));
    if (!quelle) return ersatz;
    // Nie auf das Original zurückfallen: ein 20-MB-PDF als <img> hilft niemandem.
    return <ThumbnailImage src={quelle} alt="" ersatz={ersatz} wiederholen={VORSCHAU_WIEDERHOLEN} className="object-contain" />;
}
