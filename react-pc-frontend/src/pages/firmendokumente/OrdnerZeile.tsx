import type { DragEvent, KeyboardEvent, ReactNode } from 'react';
import { ChevronRight, Folder, FolderInput, FolderOpen, FolderPlus, Lock, MoreHorizontal, Pencil, ShieldCheck, Trash2 } from 'lucide-react';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '../../components/ui/dropdown-menu';
import { cn } from '../../lib/utils';
import type { BaumZeile } from '../../lib/ordnerBaum';
import { schreibeGezogenenOrdner } from './ordnerZiehen';
import type { FirmenOrdner } from './typen';
import { useAblageZiel, type AblageRegeln } from './useAblageZiel';
import { useAufklappenBeimZiehen } from './useAufklappenBeimZiehen';

export type OrdnerAktion = 'unterordner' | 'umbenennen' | 'verschieben' | 'loeschen' | 'rechte';

export const EINGESCHRAENKT_TEXT = 'Nur für bestimmte Abteilungen sichtbar';

/** Ablage-Ereignisse einer Zeile; `aktiv` = gerade wird etwas Passendes darübergezogen. */
export interface ZiehenProps {
    aktiv: boolean;
    onDragOver: (event: DragEvent<HTMLDivElement>) => void;
    onDragLeave: (event: DragEvent<HTMLDivElement>) => void;
    onDrop: (event: DragEvent<HTMLDivElement>) => void;
}

interface ZeilenRahmenProps {
    ebene: number;
    gewaehlt: boolean;
    aktiv: boolean;
    label: string;
    ausgeklappt?: boolean;
    refSetzen: (element: HTMLDivElement | null) => void;
    onFokus: () => void;
    onKlick: () => void;
    onTaste: (event: KeyboardEvent<HTMLDivElement>) => void;
    /** Nur gesetzt, wenn hier etwas abgelegt werden kann. */
    ziehen?: ZiehenProps;
    /** Nur gesetzt, wenn sich die Zeile selbst ziehen lässt. */
    onZiehStart?: (event: DragEvent<HTMLDivElement>) => void;
    children: ReactNode;
}

/** Gemeinsamer Rahmen einer Baumzeile (Übersicht und Ordner): Auswahl, Fokus, Tastatur, Ziehen und Ablegen. */
export function ZeilenRahmen({ ebene, gewaehlt, aktiv, label, ausgeklappt, refSetzen, onFokus, onKlick, onTaste, ziehen, onZiehStart, children }: ZeilenRahmenProps) {
    return (
        <div
            ref={refSetzen}
            role="treeitem"
            aria-level={ebene + 1}
            aria-selected={gewaehlt}
            aria-expanded={ausgeklappt}
            aria-label={label}
            tabIndex={aktiv ? 0 : -1}
            draggable={onZiehStart ? true : undefined}
            onDragStart={onZiehStart}
            onFocus={(e) => { if (e.target === e.currentTarget) onFokus(); }}
            onClick={onKlick}
            onKeyDown={onTaste}
            onDragOver={ziehen?.onDragOver}
            onDragLeave={ziehen?.onDragLeave}
            onDrop={ziehen?.onDrop}
            className={cn(
                'group flex min-h-9 cursor-pointer items-center gap-1.5 rounded-lg py-1 pr-1 text-sm outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500',
                gewaehlt ? 'bg-rose-50 font-medium text-rose-800' : 'text-slate-700 hover:bg-slate-100',
                ziehen?.aktiv && 'bg-rose-100 ring-2 ring-inset ring-rose-400',
            )}
            style={{ paddingLeft: 8 + ebene * 16 }}
        >
            {children}
        </div>
    );
}

interface OrdnerZeileProps {
    zeile: BaumZeile<FirmenOrdner>;
    /** Admins dürfen an obersten Ordnern einstellen, wer sie sehen darf. */
    istAdmin: boolean;
    gewaehlt: boolean;
    aktiv: boolean;
    ueberfaellig: boolean;
    refSetzen: (element: HTMLDivElement | null) => void;
    onFokus: () => void;
    onWaehlen: () => void;
    onTaste: (event: KeyboardEvent<HTMLDivElement>) => void;
    onAufklappen: (offen: boolean) => void;
    onAktion: (aktion: OrdnerAktion) => void;
    /** Was beim Ablegen von Dateien, Dokumenten und Ordnern passiert. */
    ablage: AblageRegeln;
}

/**
 * Eine Ordnerzeile: Pfeil, Ordnersymbol, Name, Schloss bei eingeschränkten
 * Ordnern, roter Punkt bei Überfälligem, Anzahl und „…“-Menü. Mit Recht
 * „Ändern“ lässt sie sich auf einen anderen Ordner ziehen und nimmt Dateien,
 * Dokumente und Ordner an. Beim Ziehen klappt sie nach kurzem Verharren auf.
 */
export function OrdnerZeile({ zeile, istAdmin, gewaehlt, aktiv, ueberfaellig, refSetzen, onFokus, onWaehlen, onTaste, onAufklappen, onAktion, ablage }: OrdnerZeileProps) {
    const { ordner, ebene, hatKinder, aufgeklappt } = zeile;
    const ziel = useAblageZiel(ablage, ordner.id);
    const feder = useAufklappenBeimZiehen(hatKinder && !aufgeklappt, () => onAufklappen(true));
    const rechteEinstellbar = istAdmin && ordner.parentId === null;
    const Symbol = gewaehlt ? FolderOpen : Folder;
    const label = [
        ordner.name,
        `${ordner.anzahlDokumente} ${ordner.anzahlDokumente === 1 ? 'Dokument' : 'Dokumente'}`,
        ordner.eingeschraenkt ? 'nur für bestimmte Abteilungen sichtbar' : null,
        ueberfaellig ? 'enthält überfällige Dokumente' : null,
    ].filter(Boolean).join(', ');
    const ziehen: ZiehenProps = {
        aktiv: ziel.aktiv,
        onDragOver: (e) => { feder.drueber(); ziel.onDragOver(e); },
        onDragLeave: (e) => {
            if (!e.currentTarget.contains(e.relatedTarget as Node | null)) feder.weg();
            ziel.onDragLeave(e);
        },
        onDrop: (e) => { feder.weg(); ziel.onDrop(e); },
    };

    return (
        <ZeilenRahmen ebene={ebene} gewaehlt={gewaehlt} aktiv={aktiv} label={label} ausgeklappt={hatKinder ? aufgeklappt : undefined}
            refSetzen={refSetzen} onFokus={onFokus} onKlick={onWaehlen} onTaste={onTaste} ziehen={ziehen}
            onZiehStart={ordner.darfAendern ? (e) => schreibeGezogenenOrdner(e.dataTransfer, ordner) : undefined}>
            {hatKinder ? (
                <button type="button" tabIndex={-1} aria-hidden="true"
                    onClick={(e) => { e.stopPropagation(); onAufklappen(!aufgeklappt); }}
                    className="-m-1 rounded p-1 text-slate-400 hover:bg-slate-200 hover:text-slate-700">
                    <ChevronRight className={cn('h-4 w-4 motion-safe:transition-transform', aufgeklappt && 'rotate-90')} />
                </button>
            ) : <span className="w-4 shrink-0" aria-hidden="true" />}
            <Symbol className={cn('h-4 w-4 shrink-0', gewaehlt ? 'text-rose-600' : 'text-slate-400')} aria-hidden="true" />
            <span className="min-w-0 flex-1 truncate" title={ordner.name} data-kuerzung-erlaubt>{ordner.name}</span>
            {ordner.eingeschraenkt && (
                <span className="shrink-0 text-slate-400" title={EINGESCHRAENKT_TEXT} aria-hidden="true"><Lock className="h-3.5 w-3.5" /></span>
            )}
            {ueberfaellig && <span className="h-2 w-2 shrink-0 rounded-full bg-red-600" title="Enthält überfällige Dokumente" aria-hidden="true" />}
            {ordner.anzahlDokumente > 0 && (
                <span className="shrink-0 rounded-full bg-slate-100 px-1.5 text-xs tabular-nums text-slate-600" aria-hidden="true">{ordner.anzahlDokumente}</span>
            )}
            {(ordner.darfAendern || rechteEinstellbar) && (
                <OrdnerMenue name={ordner.name} erreichbar={aktiv} darfAendern={ordner.darfAendern} rechteEinstellbar={rechteEinstellbar} onAktion={onAktion} />
            )}
        </ZeilenRahmen>
    );
}

interface OrdnerMenueProps {
    name: string;
    erreichbar: boolean;
    darfAendern: boolean;
    rechteEinstellbar: boolean;
    onAktion: (aktion: OrdnerAktion) => void;
}

/** „…“-Menü am Ordner. Per Tab erreichbar, solange seine Zeile die aktive ist. */
function OrdnerMenue({ name, erreichbar, darfAendern, rechteEinstellbar, onAktion }: OrdnerMenueProps) {
    return (
        <DropdownMenu modal={false}>
            <DropdownMenuTrigger asChild>
                <button type="button" tabIndex={erreichbar ? 0 : -1} aria-label={`Weitere Aktionen für „${name}“`} title="Weitere Aktionen"
                    onClick={(e) => e.stopPropagation()}
                    onKeyDown={(e) => e.stopPropagation()}
                    className="shrink-0 rounded p-1 text-slate-400 hover:bg-slate-200 hover:text-slate-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 data-[state=open]:bg-slate-200 data-[state=open]:text-slate-700">
                    <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
                </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" collisionPadding={12} className="w-56 rounded-xl p-1.5 shadow-lg" onClick={(e) => e.stopPropagation()}>
                {darfAendern && (
                    <>
                        <DropdownMenuItem onSelect={() => onAktion('unterordner')}><FolderPlus />Unterordner anlegen</DropdownMenuItem>
                        <DropdownMenuItem onSelect={() => onAktion('umbenennen')}><Pencil />Umbenennen</DropdownMenuItem>
                        <DropdownMenuItem onSelect={() => onAktion('verschieben')}><FolderInput />Verschieben …</DropdownMenuItem>
                    </>
                )}
                {rechteEinstellbar && <DropdownMenuItem onSelect={() => onAktion('rechte')}><ShieldCheck />Wer darf das sehen?</DropdownMenuItem>}
                {darfAendern && (
                    <>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem onSelect={() => onAktion('loeschen')} className="text-red-700 focus:bg-red-50 focus:text-red-700"><Trash2 />Löschen</DropdownMenuItem>
                    </>
                )}
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
