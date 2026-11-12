import { Eraser, Heading, Highlighter, Type } from 'lucide-react';
import {
    DropdownMenuCheckboxItem, DropdownMenuItem, DropdownMenuSeparator,
} from '../../../components/ui/dropdown-menu';
import { LeistenMenue } from './LeistenMenue';
import { MenueTitel } from './mailLeisteTeile';
import { MAIL_TEXTFARBEN, MARKER_GELB } from './mailFormate';
import type { MailEditor } from './useMailEditor';

const EINTRAG = 'gap-2 [&>svg]:text-slate-400 focus:[&>svg]:text-rose-600';
const GROESSEN = [
    { groesse: 'klein', titel: 'Klein', klasse: 'pl-8 text-xs' },
    { groesse: 'normal', titel: 'Normal', klasse: 'pl-8 text-sm' },
    { groesse: 'gross', titel: 'Groß', klasse: 'pl-8 text-base' },
] as const;

interface Props {
    editor: MailEditor;
}

/** Menü „Schrift“: Größe, Textfarbe, Marker, Durchgestrichen, Formatierung entfernen. */
export function MailSchriftMenue({ editor }: Props) {
    const { aktiv } = editor;
    return (
        <LeistenMenue titel="Schrift" icon={Type}>
            {(waehle) => (
                <>
                    <MenueTitel>Größe</MenueTitel>
                    {GROESSEN.map(({ groesse, titel, klasse }) => (
                        <DropdownMenuItem key={groesse} className={klasse} onSelect={waehle(() => editor.schriftgroesse(groesse))}>
                            {titel}
                        </DropdownMenuItem>
                    ))}
                    <DropdownMenuItem className={EINTRAG} onSelect={waehle(editor.ueberschrift)}>
                        <Heading aria-hidden />
                        <span className="font-semibold">Überschrift</span>
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <MenueTitel>Textfarbe</MenueTitel>
                    <div className="grid grid-cols-6 gap-1 px-1 pb-1">
                        {MAIL_TEXTFARBEN.map(({ name, wert }) => (
                            <DropdownMenuItem
                                key={wert}
                                aria-label={`Textfarbe ${name}`}
                                title={name}
                                className="justify-center p-1.5"
                                onSelect={waehle(() => editor.farbe(wert))}
                            >
                                <span aria-hidden className="h-5 w-5 rounded-full border border-slate-300" style={{ backgroundColor: wert }} />
                            </DropdownMenuItem>
                        ))}
                    </div>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem className={EINTRAG} onSelect={waehle(() => editor.marker(MARKER_GELB))}>
                        <Highlighter aria-hidden />Gelb markieren
                    </DropdownMenuItem>
                    <DropdownMenuItem className={`${EINTRAG} pl-8`} onSelect={waehle(() => editor.marker('transparent'))}>
                        Markierung entfernen
                    </DropdownMenuItem>
                    <DropdownMenuCheckboxItem checked={aktiv.durchgestrichen} onSelect={waehle(() => editor.formatiere('strikeThrough'))}>
                        Durchgestrichen
                    </DropdownMenuCheckboxItem>
                    <DropdownMenuItem className={EINTRAG} onSelect={waehle(() => editor.formatiere('removeFormat'))}>
                        <Eraser aria-hidden />Formatierung entfernen
                    </DropdownMenuItem>
                </>
            )}
        </LeistenMenue>
    );
}
