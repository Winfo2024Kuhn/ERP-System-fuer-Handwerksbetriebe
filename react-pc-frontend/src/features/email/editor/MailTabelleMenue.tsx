import { Fragment, type ComponentType } from 'react';
import {
    BetweenHorizontalEnd, BetweenHorizontalStart, BetweenVerticalEnd, BetweenVerticalStart, Table, Trash2,
} from 'lucide-react';
import { DropdownMenuItem, DropdownMenuSeparator } from '../../../components/ui/dropdown-menu';
import { LeistenMenue } from './LeistenMenue';
import { MenueTitel } from './mailLeisteTeile';
import type { TabellenAktion } from './mailTabelle';
import { TabellenRaster } from './TabellenRaster';
import type { MailEditor } from './useMailEditor';

const EINTRAG = 'gap-2 [&>svg]:text-slate-400 focus:[&>svg]:text-rose-600';
const LOESCHEN = 'gap-2 text-red-700 [&>svg]:text-red-500 focus:bg-red-50 focus:text-red-700';

type Icon = ComponentType<{ className?: string; 'aria-hidden'?: boolean }>;

const GRUPPEN: { aktion: TabellenAktion; titel: string; icon: Icon }[][] = [
    [
        { aktion: 'zeileOben', titel: 'Zeile darüber', icon: BetweenHorizontalStart },
        { aktion: 'zeileUnten', titel: 'Zeile darunter', icon: BetweenHorizontalEnd },
    ],
    [
        { aktion: 'spalteLinks', titel: 'Spalte links', icon: BetweenVerticalStart },
        { aktion: 'spalteRechts', titel: 'Spalte rechts', icon: BetweenVerticalEnd },
    ],
];

const LOESCHEN_EINTRAEGE: { aktion: TabellenAktion; titel: string }[] = [
    { aktion: 'zeileLoeschen', titel: 'Zeile löschen' },
    { aktion: 'spalteLoeschen', titel: 'Spalte löschen' },
    { aktion: 'tabelleLoeschen', titel: 'Tabelle löschen' },
];

/**
 * Menü „Tabelle“ der Formatierungsleiste. Außerhalb einer Tabelle: Größe
 * wählen und einfügen. Steht der Cursor in einer Tabelle: Zeilen und Spalten
 * einfügen oder löschen, Tabelle löschen.
 */
export function MailTabelleMenue({ editor }: { editor: MailEditor }) {
    const { aktiv, tabelle } = editor;
    return (
        <LeistenMenue titel="Tabelle" icon={Table} breite="w-52">
            {(waehle) => (aktiv.tabelle ? (
                <>
                    {GRUPPEN.map((gruppe, index) => (
                        <Fragment key={gruppe[0].aktion}>
                            {index > 0 && <DropdownMenuSeparator />}
                            {gruppe.map(({ aktion, titel, icon: Symbol }) => (
                                <DropdownMenuItem key={aktion} className={EINTRAG} onSelect={waehle(() => tabelle.aendere(aktion))}>
                                    <Symbol aria-hidden />{titel}
                                </DropdownMenuItem>
                            ))}
                        </Fragment>
                    ))}
                    <DropdownMenuSeparator />
                    {LOESCHEN_EINTRAEGE.map(({ aktion, titel }) => (
                        <DropdownMenuItem key={aktion} className={LOESCHEN} onSelect={waehle(() => tabelle.aendere(aktion))}>
                            <Trash2 aria-hidden />{titel}
                        </DropdownMenuItem>
                    ))}
                </>
            ) : (
                <>
                    <MenueTitel>Tabelle einfügen</MenueTitel>
                    <TabellenRaster onEinfuegen={(zeilen, spalten) => waehle(() => tabelle.einfuegen(zeilen, spalten))()} />
                    <p className="border-t border-slate-100 px-2 pb-1 pt-1.5 text-xs text-slate-500">
                        Cursor in eine Tabelle setzen, um Zeilen und Spalten zu ändern.
                    </p>
                </>
            ))}
        </LeistenMenue>
    );
}
