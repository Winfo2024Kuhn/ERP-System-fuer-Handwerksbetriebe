import { Download, Eye, FolderSearch, MoreHorizontal, Pencil, Printer, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '../../components/ui/dropdown-menu';
import type { FirmenDokument } from './typen';

/** Was man mit einem Dokument in der Liste machen kann. */
export interface DokumentAktionen {
    ansehen: (dok: FirmenDokument) => void;
    drucken: (dok: FirmenDokument) => void;
    herunterladen: (dok: FirmenDokument) => void;
    bearbeiten: (dok: FirmenDokument) => void;
    loeschen: (dok: FirmenDokument) => void;
    /** Nur in Listen über mehrere Ordner (Übersicht, Suche). */
    imOrdnerZeigen?: (dok: FirmenDokument) => void;
}

/** Drucken und Herunterladen direkt, alles Weitere im „…“-Menü. */
export function DokumentZeilenAktionen({ dokument, aktionen }: { dokument: FirmenDokument; aktionen: DokumentAktionen }) {
    const name = dokument.name;
    return (
        <div className="flex items-center justify-end gap-0.5">
            <Button variant="ghost" size="sm" onClick={() => aktionen.drucken(dokument)} aria-label={`„${name}“ drucken`} title="Drucken"
                className="text-slate-500 hover:text-rose-700">
                <Printer className="h-4 w-4" aria-hidden="true" />
            </Button>
            <Button variant="ghost" size="sm" onClick={() => aktionen.herunterladen(dokument)} aria-label={`„${name}“ herunterladen`} title="Herunterladen"
                className="text-slate-500 hover:text-rose-700">
                <Download className="h-4 w-4" aria-hidden="true" />
            </Button>
            <DropdownMenu modal={false}>
                <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="sm" aria-label={`Weitere Aktionen für „${name}“`} title="Weitere Aktionen"
                        className="text-slate-500 hover:text-rose-700">
                        <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
                    </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" collisionPadding={12} className="w-52 rounded-xl p-1.5 shadow-lg">
                    <DropdownMenuItem onSelect={() => aktionen.ansehen(dokument)}><Eye />Ansehen</DropdownMenuItem>
                    <DropdownMenuItem onSelect={() => aktionen.bearbeiten(dokument)}><Pencil />Bearbeiten …</DropdownMenuItem>
                    {aktionen.imOrdnerZeigen && (
                        <DropdownMenuItem onSelect={() => aktionen.imOrdnerZeigen?.(dokument)}><FolderSearch />Im Ordner zeigen</DropdownMenuItem>
                    )}
                    <DropdownMenuSeparator />
                    <DropdownMenuItem onSelect={() => aktionen.loeschen(dokument)} className="text-red-700 focus:bg-red-50 focus:text-red-700">
                        <Trash2 />Löschen …
                    </DropdownMenuItem>
                </DropdownMenuContent>
            </DropdownMenu>
        </div>
    );
}
