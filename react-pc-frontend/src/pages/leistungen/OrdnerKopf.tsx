import { ChevronRight, Folder, FolderInput, FolderPlus, MoreHorizontal, Pencil, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import {
    DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger,
} from '../../components/ui/dropdown-menu';
import { toSafeResourceUrl } from '../../lib/htmlSanitizer';
import { ordnerKette, type Baum } from './ordnerLogik';
import type { Ordner } from './typen';

export interface OrdnerKopfProps {
    ordner: Ordner;
    baum: Baum;
    onWaehlen: (id: number | null) => void;
    onUnterordner: () => void;
    onUmbenennen: () => void;
    onVerschieben: () => void;
    onLoeschen: () => void;
}

/** Kopf rechts: Bild, Name, klickbarer Pfad und die Ordner-Aktionen. */
export function OrdnerKopf({ ordner, baum, onWaehlen, onUnterordner, onUmbenennen, onVerschieben, onLoeschen }: OrdnerKopfProps) {
    const kette = ordnerKette(baum, ordner.id);
    const bild = toSafeResourceUrl(ordner.bildUrl);
    return (
        <div className="flex items-start justify-between gap-4">
            <div className="flex min-w-0 items-center gap-3">
                {bild ? (
                    <img src={bild} alt={`Bild von „${ordner.name}“`} className="h-14 w-14 shrink-0 rounded-lg object-cover shadow-sm" />
                ) : (
                    <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-lg bg-rose-50" aria-hidden="true">
                        <Folder className="h-7 w-7 text-rose-400" />
                    </span>
                )}
                <div className="min-w-0">
                    <nav aria-label="Pfad" className="text-xs text-slate-500">
                        <ol className="flex flex-wrap items-center gap-0.5">
                            <li>
                                <button type="button" onClick={() => onWaehlen(null)}
                                    className="rounded px-0.5 hover:text-rose-700 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                                    Alle Ordner
                                </button>
                            </li>
                            {kette.slice(0, -1).map((o) => (
                                <li key={o.id} className="flex items-center gap-0.5">
                                    <ChevronRight className="h-3 w-3" aria-hidden="true" />
                                    <button type="button" onClick={() => onWaehlen(o.id)}
                                        className="rounded px-0.5 hover:text-rose-700 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                                        {o.name}
                                    </button>
                                </li>
                            ))}
                        </ol>
                    </nav>
                    <h2 className="text-xl font-bold text-slate-900 [overflow-wrap:anywhere]">{ordner.name}</h2>
                </div>
            </div>
            <div className="flex shrink-0 items-center gap-2">
                <Button size="sm" variant="outline" onClick={onUnterordner}>
                    <FolderPlus className="h-4 w-4" aria-hidden="true" /> Unterordner
                </Button>
                <DropdownMenu modal={false}>
                    <DropdownMenuTrigger asChild>
                        <button type="button" aria-label={`Weitere Aktionen für „${ordner.name}“`} title="Weitere Aktionen"
                            className="rounded border border-rose-200 bg-white p-1.5 text-rose-700 hover:bg-rose-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-rose-500">
                            <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
                        </button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end" onCloseAutoFocus={(e) => e.preventDefault()}>
                        <DropdownMenuItem onSelect={onUmbenennen}><Pencil className="h-4 w-4" aria-hidden="true" /> Umbenennen</DropdownMenuItem>
                        <DropdownMenuItem onSelect={onVerschieben}><FolderInput className="h-4 w-4" aria-hidden="true" /> Verschieben nach …</DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem onSelect={onLoeschen} className="text-red-700 focus:bg-red-50 focus:text-red-700">
                            <Trash2 className="h-4 w-4" aria-hidden="true" /> Löschen …
                        </DropdownMenuItem>
                    </DropdownMenuContent>
                </DropdownMenu>
            </div>
        </div>
    );
}
