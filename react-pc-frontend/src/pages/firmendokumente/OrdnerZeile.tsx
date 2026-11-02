import { useState, type DragEvent, type KeyboardEvent, type ReactNode } from 'react';
import { ChevronRight, Folder, FolderInput, FolderOpen, FolderPlus, MoreHorizontal, Pencil, Trash2 } from 'lucide-react';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '../../components/ui/dropdown-menu';
import { cn } from '../../lib/utils';
import type { BaumZeile } from '../../lib/ordnerBaum';
import { ziehtDateien } from './firmendokumenteLogik';
import type { FirmenOrdner } from './typen';

export type OrdnerAktion = 'unterordner' | 'umbenennen' | 'verschieben' | 'loeschen';

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
    onDateien?: (dateien: FileList) => void;
    children: ReactNode;
}

/** Gemeinsamer Rahmen einer Baumzeile (Übersicht und Ordner): Auswahl, Fokus, Tastatur, PDF-Ablage. */
export function ZeilenRahmen({ ebene, gewaehlt, aktiv, label, ausgeklappt, refSetzen, onFokus, onKlick, onTaste, onDateien, children }: ZeilenRahmenProps) {
    const [ablage, setAblage] = useState(false);
    const ablegbar = (event: DragEvent) => {
        if (!onDateien || !ziehtDateien(event)) return false;
        event.preventDefault();
        return true;
    };
    return (
        <div
            ref={refSetzen}
            role="treeitem"
            aria-level={ebene + 1}
            aria-selected={gewaehlt}
            aria-expanded={ausgeklappt}
            aria-label={label}
            tabIndex={aktiv ? 0 : -1}
            onFocus={(e) => { if (e.target === e.currentTarget) onFokus(); }}
            onClick={onKlick}
            onKeyDown={onTaste}
            onDragOver={(e) => { if (ablegbar(e)) { e.dataTransfer.dropEffect = 'copy'; setAblage(true); } }}
            onDragLeave={(e) => { if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setAblage(false); }}
            onDrop={(e) => { if (ablegbar(e)) { setAblage(false); onDateien?.(e.dataTransfer.files); } }}
            className={cn(
                'group flex min-h-9 cursor-pointer items-center gap-1.5 rounded-lg py-1 pr-1 text-sm outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500',
                gewaehlt ? 'bg-rose-50 font-medium text-rose-800' : 'text-slate-700 hover:bg-slate-100',
                ablage && 'bg-rose-100 ring-2 ring-inset ring-rose-400',
            )}
            style={{ paddingLeft: 8 + ebene * 16 }}
        >
            {children}
        </div>
    );
}

interface OrdnerZeileProps {
    zeile: BaumZeile<FirmenOrdner>;
    gewaehlt: boolean;
    aktiv: boolean;
    ueberfaellig: boolean;
    refSetzen: (element: HTMLDivElement | null) => void;
    onFokus: () => void;
    onWaehlen: () => void;
    onTaste: (event: KeyboardEvent<HTMLDivElement>) => void;
    onAufklappen: (offen: boolean) => void;
    onAktion: (aktion: OrdnerAktion) => void;
    onDateien: (dateien: FileList) => void;
}

/** Eine Ordnerzeile: Pfeil, Ordnersymbol, Name, roter Punkt bei Überfälligem, Anzahl und „…“-Menü. */
export function OrdnerZeile({ zeile, gewaehlt, aktiv, ueberfaellig, refSetzen, onFokus, onWaehlen, onTaste, onAufklappen, onAktion, onDateien }: OrdnerZeileProps) {
    const { ordner, ebene, hatKinder, aufgeklappt } = zeile;
    const Symbol = gewaehlt ? FolderOpen : Folder;
    const label = [
        ordner.name,
        `${ordner.anzahlDokumente} ${ordner.anzahlDokumente === 1 ? 'Dokument' : 'Dokumente'}`,
        ueberfaellig ? 'enthält überfällige Dokumente' : null,
    ].filter(Boolean).join(', ');

    return (
        <ZeilenRahmen ebene={ebene} gewaehlt={gewaehlt} aktiv={aktiv} label={label} ausgeklappt={hatKinder ? aufgeklappt : undefined}
            refSetzen={refSetzen} onFokus={onFokus} onKlick={onWaehlen} onTaste={onTaste} onDateien={onDateien}>
            {hatKinder ? (
                <button type="button" tabIndex={-1} aria-hidden="true"
                    onClick={(e) => { e.stopPropagation(); onAufklappen(!aufgeklappt); }}
                    className="-m-1 rounded p-1 text-slate-400 hover:bg-slate-200 hover:text-slate-700">
                    <ChevronRight className={cn('h-4 w-4 motion-safe:transition-transform', aufgeklappt && 'rotate-90')} />
                </button>
            ) : <span className="w-4 shrink-0" aria-hidden="true" />}
            <Symbol className={cn('h-4 w-4 shrink-0', gewaehlt ? 'text-rose-600' : 'text-slate-400')} aria-hidden="true" />
            <span className="min-w-0 flex-1 truncate" title={ordner.name}>{ordner.name}</span>
            {ueberfaellig && <span className="h-2 w-2 shrink-0 rounded-full bg-red-600" title="Enthält überfällige Dokumente" aria-hidden="true" />}
            {ordner.anzahlDokumente > 0 && (
                <span className="shrink-0 rounded-full bg-slate-100 px-1.5 text-xs tabular-nums text-slate-600" aria-hidden="true">{ordner.anzahlDokumente}</span>
            )}
            <OrdnerMenue name={ordner.name} erreichbar={aktiv} onAktion={onAktion} />
        </ZeilenRahmen>
    );
}

/** „…“-Menü am Ordner. Per Tab erreichbar, solange seine Zeile die aktive ist. */
function OrdnerMenue({ name, erreichbar, onAktion }: { name: string; erreichbar: boolean; onAktion: (aktion: OrdnerAktion) => void }) {
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
                <DropdownMenuItem onSelect={() => onAktion('unterordner')}><FolderPlus />Unterordner anlegen</DropdownMenuItem>
                <DropdownMenuItem onSelect={() => onAktion('umbenennen')}><Pencil />Umbenennen</DropdownMenuItem>
                <DropdownMenuItem onSelect={() => onAktion('verschieben')}><FolderInput />Verschieben …</DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={() => onAktion('loeschen')} className="text-red-700 focus:bg-red-50 focus:text-red-700"><Trash2 />Löschen</DropdownMenuItem>
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
