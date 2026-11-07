import { useState, type KeyboardEvent, type ReactNode } from 'react';
import {
    Briefcase, ChevronDown, ChevronRight, Folder, FolderInput, FolderOpen, FolderPlus, LayoutGrid, List,
    MoreHorizontal, Pencil, Trash2,
} from 'lucide-react';
import {
    DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from '../../components/ui/dropdown-menu';
import { toSafeResourceUrl } from '../../lib/htmlSanitizer';
import { cn } from '../../lib/utils';
import type { OrdnerBaumProps } from './OrdnerBaum';
import { NamensEingabe } from './OrdnerNamensEingabe';
import { formatZahl, zaehlerAriaLabel, type BaumZeile, type Zaehler } from './ordnerLogik';
import { useAblage } from './useAblage';
import type { ZiehObjekt } from './typen';

/**
 * Zeilen des Ordnerbaums: „Alle Ordner (Übersicht)“ und je Ordner eine Zeile
 * mit Zählern, „+ Unterordner“, „…“-Menü, Umbenennen direkt in der Zeile und
 * Ablage beim Ziehen (Ordner, Leistungen, Bilddateien).
 */

const ZEILE = cn(
    'group/zeile relative flex items-center gap-1.5 rounded-md py-1.5 pr-1 text-sm outline-none select-none',
    'focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500',
);

export function UebersichtZeile({ refSetzen, ...props }: {
    refSetzen: (element: HTMLDivElement | null) => void;
    gewaehlt: boolean;
    aktiv: boolean;
    onFokus: () => void;
    onKlick: () => void;
    onTaste: (event: KeyboardEvent<HTMLDivElement>) => void;
    ziehen: ZiehObjekt | null;
    ablageErlaubt: boolean;
    onAblegen: () => void;
}) {
    const { drueber, handler } = useAblage({
        ziehen: props.ziehen, erlaubt: props.ablageErlaubt, bilderErlaubt: false, onAblegen: props.onAblegen,
    });
    return (
        <div
            ref={refSetzen}
            role="treeitem"
            aria-level={1}
            aria-selected={props.gewaehlt}
            tabIndex={props.aktiv ? 0 : -1}
            onFocus={props.onFokus}
            onClick={props.onKlick}
            onKeyDown={props.onTaste}
            {...handler}
            className={cn(
                ZEILE, 'cursor-pointer pl-2',
                props.gewaehlt ? 'bg-rose-50 font-semibold text-rose-700' : 'text-slate-700 hover:bg-slate-50',
                drueber && 'bg-rose-50 ring-2 ring-inset ring-rose-400',
                props.ziehen && !props.ablageErlaubt && 'opacity-50',
            )}
        >
            <LayoutGrid className="h-4 w-4 shrink-0 text-slate-500" aria-hidden="true" />
            <span className="flex-1 truncate">Alle Ordner (Übersicht)</span>
            {drueber && <span className="text-xs font-medium text-rose-700">In oberste Ebene</span>}
        </div>
    );
}

export interface OrdnerZeileProps extends Omit<OrdnerBaumProps, 'zaehler' | 'gewaehlt'> {
    zeile: BaumZeile;
    zaehler: Zaehler;
    gewaehlt: boolean;
    aktiv: boolean;
    refSetzen: (element: HTMLDivElement | null) => void;
    onFokus: () => void;
    onTaste: (event: KeyboardEvent<HTMLDivElement>) => void;
}

export function OrdnerZeile({ refSetzen, ...props }: OrdnerZeileProps) {
    const { zeile, gewaehlt, aktiv, ziehen } = props;
    const { ordner, ebene, hatKinder, aufgeklappt: offen } = zeile;
    const [menueOffen, setMenueOffen] = useState(false);
    const erlaubt = ziehen ? props.ablageErlaubt(ordner.id) : false;
    const { drueber, handler } = useAblage({
        ziehen,
        erlaubt,
        bilderErlaubt: true,
        onAblegen: () => props.onAblegen(ordner.id),
        onBild: (datei) => props.onBildAblegen(ordner.id, datei),
    });
    const bild = toSafeResourceUrl(ordner.bildUrl);
    const umbenennen = props.umbenennenId === ordner.id;
    const aktionenSichtbar = gewaehlt || menueOffen;

    return (
        <div
            ref={refSetzen}
            role="treeitem"
            aria-level={ebene + 1}
            aria-selected={gewaehlt}
            aria-expanded={hatKinder ? offen : undefined}
            aria-label={`${ordner.name}, ${zaehlerAriaLabel(props.zaehler.gesamt, ordner.projektAnzahl)}`}
            tabIndex={aktiv ? 0 : -1}
            draggable={!umbenennen}
            onDragStart={(e) => {
                e.dataTransfer.effectAllowed = 'move';
                e.dataTransfer.setData('text/plain', ordner.name);
                props.onZiehenStart({ art: 'ordner', id: ordner.id });
            }}
            onDragEnd={props.onZiehenEnde}
            onFocus={(e) => { if (e.target === e.currentTarget) props.onFokus(); }}
            onClick={() => { if (!umbenennen) props.onWaehlen(ordner.id); }}
            onKeyDown={props.onTaste}
            {...handler}
            className={cn(
                ZEILE, 'cursor-pointer',
                gewaehlt ? 'bg-rose-50 text-rose-800' : 'text-slate-700 hover:bg-slate-50',
                drueber && 'bg-rose-50 ring-2 ring-inset ring-rose-400',
                ziehen && !erlaubt && 'opacity-50',
            )}
            style={{ paddingLeft: 4 + ebene * 16 }}
        >
            <span
                aria-hidden="true"
                onClick={(e) => { if (hatKinder) { e.stopPropagation(); props.onAufklappen(ordner.id, !offen); } }}
                className={cn('flex h-5 w-5 shrink-0 items-center justify-center rounded text-slate-400', hatKinder && 'hover:bg-slate-200 hover:text-slate-700')}
            >
                {hatKinder && (offen ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />)}
            </span>
            {bild ? (
                <img src={bild} alt="" className="h-6 w-6 shrink-0 rounded object-cover" draggable={false} />
            ) : (
                <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded bg-rose-50" aria-hidden="true">
                    {offen ? <FolderOpen className="h-4 w-4 text-rose-500" /> : <Folder className="h-4 w-4 text-rose-500" />}
                </span>
            )}

            {umbenennen ? (
                <NamensEingabe
                    start={ordner.name}
                    beschriftung={`Neuer Name für „${ordner.name}“`}
                    onAbbrechen={() => { props.onUmbenennenAbbrechen(); }}
                    onSpeichern={(name) => props.onUmbenennenSpeichern(ordner.id, name)}
                />
            ) : (
                <>
                    <span className={cn('min-w-0 flex-1 truncate', gewaehlt && 'font-semibold')} title={ordner.name} data-kuerzung-erlaubt>{ordner.name}</span>
                    {drueber ? (
                        <span className="shrink-0 text-xs font-medium text-rose-700">{drueber === 'bild' ? 'Als Ordnerbild' : 'Hier ablegen'}</span>
                    ) : (
                        <span className={cn('flex shrink-0 items-center gap-2 text-xs tabular-nums text-slate-500', aktionenSichtbar ? 'hidden' : 'group-hover/zeile:hidden group-focus-within/zeile:hidden')} aria-hidden="true">
                            <span className="inline-flex items-center gap-0.5" title="Leistungen"><List className="h-3 w-3" />{formatZahl(props.zaehler.gesamt, 0)}</span>
                            <span className="inline-flex items-center gap-0.5" title="Projekte"><Briefcase className="h-3 w-3" />{formatZahl(ordner.projektAnzahl, 0)}</span>
                        </span>
                    )}
                    {!drueber && (
                        <span className={cn('shrink-0 items-center gap-0.5', aktionenSichtbar ? 'flex' : 'hidden group-hover/zeile:flex group-focus-within/zeile:flex')}>
                            <ZeilenKnopf
                                label={`Unterordner in „${ordner.name}“ anlegen`}
                                tabIndex={aktiv ? 0 : -1}
                                onClick={() => { props.onAufklappen(ordner.id, true); props.onNeuerOrdner(ordner.id); }}
                            >
                                <FolderPlus className="h-4 w-4" aria-hidden="true" />
                            </ZeilenKnopf>
                            <DropdownMenu modal={false} onOpenChange={setMenueOffen}>
                                <DropdownMenuTrigger asChild>
                                    <button
                                        type="button"
                                        aria-label={`Weitere Aktionen für „${ordner.name}“`}
                                        title="Weitere Aktionen"
                                        tabIndex={aktiv ? 0 : -1}
                                        onClick={(e) => e.stopPropagation()}
                                        onKeyDown={(e) => e.stopPropagation()}
                                        className="rounded p-1 text-slate-500 hover:bg-white hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                                    >
                                        <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
                                    </button>
                                </DropdownMenuTrigger>
                                <DropdownMenuContent align="end" onClick={(e) => e.stopPropagation()} onCloseAutoFocus={(e) => e.preventDefault()}>
                                    <DropdownMenuItem onSelect={() => props.onUmbenennen(ordner.id)}>
                                        <Pencil className="h-4 w-4" aria-hidden="true" /> Umbenennen
                                    </DropdownMenuItem>
                                    <DropdownMenuItem onSelect={() => props.onVerschieben(ordner.id)}>
                                        <FolderInput className="h-4 w-4" aria-hidden="true" /> Verschieben nach …
                                    </DropdownMenuItem>
                                    <DropdownMenuSeparator />
                                    <DropdownMenuItem onSelect={() => props.onLoeschen(ordner.id)} className="text-red-700 focus:bg-red-50 focus:text-red-700">
                                        <Trash2 className="h-4 w-4" aria-hidden="true" /> Löschen …
                                    </DropdownMenuItem>
                                </DropdownMenuContent>
                            </DropdownMenu>
                        </span>
                    )}
                </>
            )}
        </div>
    );
}

function ZeilenKnopf({ label, tabIndex, onClick, children }: { label: string; tabIndex: number; onClick: () => void; children: ReactNode }) {
    return (
        <button
            type="button"
            aria-label={label}
            title={label}
            tabIndex={tabIndex}
            onClick={(e) => { e.stopPropagation(); onClick(); }}
            onKeyDown={(e) => e.stopPropagation()}
            className="rounded p-1 text-slate-500 hover:bg-white hover:text-rose-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
        >
            {children}
        </button>
    );
}
