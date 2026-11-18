import { useRef, useState, type KeyboardEvent } from 'react';
import { DropdownMenuItem } from '../../../components/ui/dropdown-menu';
import { cn } from '../../../lib/utils';
import { TABELLE_MAX } from './mailTabelle';

interface Groesse {
    zeilen: number;
    spalten: number;
}

const SCHRITTE: Record<string, [number, number]> = {
    ArrowDown: [1, 0], ArrowUp: [-1, 0], ArrowRight: [0, 1], ArrowLeft: [0, -1],
};

const wort = (anzahl: number, einzahl: string, mehrzahl: string) => `${anzahl} ${anzahl === 1 ? einzahl : mehrzahl}`;

const rasterName = ({ zeilen, spalten }: Groesse) =>
    `Tabelle mit ${wort(zeilen, 'Zeile', 'Zeilen')} und ${wort(spalten, 'Spalte', 'Spalten')} einfügen`;

interface TabellenRasterProps {
    /** Wird ausgelöst, sobald die Größe feststeht (Klick, Enter). */
    onEinfuegen: (zeilen: number, spalten: number) => void;
}

/**
 * Größenwahl „Zeilen × Spalten“ als Raster (bis 8 × 8) in einem Menü-Eintrag.
 * Maus: Zeigen wählt, Klick fügt ein. Tastatur: Pfeiltasten ändern die Größe,
 * Enter fügt ein; am Rand des Rasters wandern die Pfeile weiter durchs Menü.
 */
export function TabellenRaster({ onEinfuegen }: TabellenRasterProps) {
    const [groesse, setGroesse] = useState<Groesse>({ zeilen: 1, spalten: 1 });
    // Die Auswahl liest die Größe synchron: Klick und Zeigen kommen im selben Takt.
    const aktuell = useRef(groesse);
    const setze = (neu: Groesse) => {
        aktuell.current = neu;
        setGroesse(neu);
    };

    const beiTaste = (event: KeyboardEvent<HTMLDivElement>) => {
        const schritt = SCHRITTE[event.key];
        if (!schritt) return;
        const zeilen = aktuell.current.zeilen + schritt[0];
        const spalten = aktuell.current.spalten + schritt[1];
        if (zeilen < 1 || spalten < 1 || zeilen > TABELLE_MAX || spalten > TABELLE_MAX) return;
        event.preventDefault();
        setze({ zeilen, spalten });
    };

    const ausZelle = (ziel: EventTarget | null): Groesse | null => {
        const zelle = (ziel as HTMLElement | null)?.closest<HTMLElement>('[data-zeile]');
        return zelle ? { zeilen: Number(zelle.dataset.zeile), spalten: Number(zelle.dataset.spalte) } : null;
    };

    return (
        <>
            <DropdownMenuItem
                aria-label={rasterName(groesse)}
                className="flex-col items-start gap-0 px-2 py-1.5 focus:bg-slate-50"
                onKeyDown={beiTaste}
                onPointerMove={(event) => {
                    const neu = ausZelle(event.target);
                    if (neu && (neu.zeilen !== aktuell.current.zeilen || neu.spalten !== aktuell.current.spalten)) setze(neu);
                }}
                onClick={(event) => {
                    const neu = ausZelle(event.target);
                    if (neu) setze(neu);
                }}
                onSelect={() => onEinfuegen(aktuell.current.zeilen, aktuell.current.spalten)}
            >
                <div aria-hidden className="grid grid-cols-8 gap-[3px]">
                    {Array.from({ length: TABELLE_MAX * TABELLE_MAX }, (_, i) => {
                        const zeile = Math.floor(i / TABELLE_MAX) + 1;
                        const spalte = (i % TABELLE_MAX) + 1;
                        const gewaehlt = zeile <= groesse.zeilen && spalte <= groesse.spalten;
                        return (
                            <span
                                key={i}
                                data-zeile={zeile}
                                data-spalte={spalte}
                                data-testid={`tabellen-raster-${zeile}-${spalte}`}
                                className={cn(
                                    'h-4 w-4 rounded-[3px] border transition-colors',
                                    gewaehlt ? 'border-rose-400 bg-rose-100' : 'border-slate-300 bg-white',
                                )}
                            />
                        );
                    })}
                </div>
            </DropdownMenuItem>
            <p aria-live="polite" className="flex items-baseline gap-1.5 px-2 pb-1 pt-0.5 text-sm">
                <span className="font-medium tabular-nums text-slate-900">{groesse.zeilen} × {groesse.spalten}</span>
                <span className="text-xs text-slate-500">Zeilen × Spalten</span>
            </p>
        </>
    );
}
