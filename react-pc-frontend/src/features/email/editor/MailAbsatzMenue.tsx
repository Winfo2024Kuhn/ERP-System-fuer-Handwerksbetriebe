import { Fragment } from 'react';
import { AlignCenter, AlignLeft, AlignRight, IndentDecrease, IndentIncrease, Pilcrow } from 'lucide-react';
import { DropdownMenuCheckboxItem, DropdownMenuItem, DropdownMenuSeparator } from '../../../components/ui/dropdown-menu';
import { LeistenMenue } from './LeistenMenue';
import type { MailBefehl } from './mailFormate';
import type { MailEditor } from './useMailEditor';

const EINTRAG = 'gap-2 [&>svg]:text-slate-400 focus:[&>svg]:text-rose-600';

const AUSRICHTUNG: { befehl: MailBefehl; titel: string; icon: typeof AlignLeft }[] = [
    { befehl: 'justifyLeft', titel: 'Linksbündig', icon: AlignLeft },
    { befehl: 'justifyCenter', titel: 'Zentriert', icon: AlignCenter },
    { befehl: 'justifyRight', titel: 'Rechtsbündig', icon: AlignRight },
];

const EINZUG: { befehl: MailBefehl; titel: string; icon: typeof AlignLeft }[] = [
    { befehl: 'indent', titel: 'Einzug vergrößern', icon: IndentIncrease },
    { befehl: 'outdent', titel: 'Einzug verkleinern', icon: IndentDecrease },
];

/** Menü „Absatz“: Aufzählung, Nummerierung, Ausrichtung, Einzug. */
export function MailAbsatzMenue({ editor }: { editor: MailEditor }) {
    const { aktiv } = editor;
    return (
        <LeistenMenue titel="Absatz" icon={Pilcrow} breite="w-52">
            {(waehle) => (
                <>
                    <DropdownMenuCheckboxItem checked={aktiv.aufzaehlung} onSelect={waehle(() => editor.formatiere('insertUnorderedList'))}>
                        Aufzählung
                    </DropdownMenuCheckboxItem>
                    <DropdownMenuCheckboxItem checked={aktiv.nummerierung} onSelect={waehle(() => editor.formatiere('insertOrderedList'))}>
                        Nummerierung
                    </DropdownMenuCheckboxItem>
                    <DropdownMenuSeparator />
                    {[...AUSRICHTUNG, ...EINZUG].map(({ befehl, titel, icon: Symbol }) => (
                        <Fragment key={befehl}>
                            {befehl === 'indent' && <DropdownMenuSeparator />}
                            <DropdownMenuItem className={EINTRAG} onSelect={waehle(() => editor.formatiere(befehl))}>
                                <Symbol aria-hidden />{titel}
                            </DropdownMenuItem>
                        </Fragment>
                    ))}
                </>
            )}
        </LeistenMenue>
    );
}
