import { Download, Eye, FolderSearch, MoreHorizontal, Pencil, Printer, Trash2 } from 'lucide-react';
import { Button } from '../../components/ui/button';
import { cn } from '../../lib/utils';
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

/**
 * Drucken (nur PDF) und Herunterladen direkt, alles Weitere im „…“-Menü.
 * Bearbeiten und Löschen nur mit Recht „Ändern“. Mit `nurMenue` (Kacheln)
 * steht alles im „…“-Menü, auch Drucken und Herunterladen.
 */
export function DokumentZeilenAktionen({ dokument, aktionen, nurMenue = false }: { dokument: FirmenDokument; aktionen: DokumentAktionen; nurMenue?: boolean }) {
    const name = dokument.name;
    const istPdf = dokument.dateiart === 'PDF';
    return (
        <div className="flex items-center justify-end gap-0.5">
            {!nurMenue && istPdf && (
                <Button variant="ghost" size="sm" onClick={() => aktionen.drucken(dokument)} aria-label={`„${name}“ drucken`} title="Drucken"
                    className="text-slate-500 hover:text-rose-700">
                    <Printer className="h-4 w-4" aria-hidden="true" />
                </Button>
            )}
            {!nurMenue && (
                <Button variant="ghost" size="sm" onClick={() => aktionen.herunterladen(dokument)} aria-label={`„${name}“ herunterladen`} title="Herunterladen"
                    className="text-slate-500 hover:text-rose-700">
                    <Download className="h-4 w-4" aria-hidden="true" />
                </Button>
            )}
            <DropdownMenu modal={false}>
                <DropdownMenuTrigger asChild>
                    <Button variant="ghost" size="sm" aria-label={`Weitere Aktionen für „${name}“`} title="Weitere Aktionen"
                        className={cn('text-slate-500 hover:text-rose-700', nurMenue && 'h-7 px-1.5')}>
                        <MoreHorizontal className="h-4 w-4" aria-hidden="true" />
                    </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" collisionPadding={12} className="w-52 rounded-xl p-1.5 shadow-lg">
                    <DropdownMenuItem onSelect={() => aktionen.ansehen(dokument)}>{dokument.dateiart === 'SONSTIGE' ? <><Download />Herunterladen</> : <><Eye />Ansehen</>}</DropdownMenuItem>
                    {nurMenue && istPdf && <DropdownMenuItem onSelect={() => aktionen.drucken(dokument)}><Printer />Drucken</DropdownMenuItem>}
                    {nurMenue && dokument.dateiart !== 'SONSTIGE' && (
                        <DropdownMenuItem onSelect={() => aktionen.herunterladen(dokument)}><Download />Herunterladen</DropdownMenuItem>
                    )}
                    {dokument.darfAendern && <DropdownMenuItem onSelect={() => aktionen.bearbeiten(dokument)}><Pencil />Bearbeiten …</DropdownMenuItem>}
                    {aktionen.imOrdnerZeigen && (
                        <DropdownMenuItem onSelect={() => aktionen.imOrdnerZeigen?.(dokument)}><FolderSearch />Im Ordner zeigen</DropdownMenuItem>
                    )}
                    {dokument.darfAendern && (
                        <>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem onSelect={() => aktionen.loeschen(dokument)} className="text-red-700 focus:bg-red-50 focus:text-red-700">
                                <Trash2 />Löschen …
                            </DropdownMenuItem>
                        </>
                    )}
                </DropdownMenuContent>
            </DropdownMenu>
        </div>
    );
}
