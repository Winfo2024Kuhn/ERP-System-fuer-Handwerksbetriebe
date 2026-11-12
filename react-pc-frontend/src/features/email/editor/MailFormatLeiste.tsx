import { Bold, Italic, Link2, Underline } from 'lucide-react';
import { cn } from '../../../lib/utils';
import { MailAbsatzMenue } from './MailAbsatzMenue';
import { MailSchriftMenue } from './MailSchriftMenue';
import { LeistenKnopf, Trenner } from './mailLeisteTeile';
import { pfeilNavigation } from './mailLeisteTasten';
import type { MailEditor } from './useMailEditor';

const MAC = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.userAgent);
const kuerzel = (taste: string) => (MAC ? `⌘${taste}` : `Strg+${taste}`);

interface MailFormatLeisteProps {
    editor: MailEditor;
    className?: string;
}

/**
 * Eine Zeile Formatierung für Mail-Textfelder: Fett, Kursiv, Unterstrichen und
 * Link direkt, alles Weitere in den Menüs „Schrift“ und „Absatz“. Braucht nie
 * eine zweite Zeile und kann außerhalb des Felds stehen (Kopfzeile).
 */
export function MailFormatLeiste({ editor, className }: MailFormatLeisteProps) {
    const { aktiv } = editor;
    return (
        <div
            role="toolbar"
            aria-label="Formatierung"
            aria-orientation="horizontal"
            onKeyDown={pfeilNavigation}
            className={cn('flex shrink-0 flex-nowrap items-center gap-0.5', className)}
        >
            <LeistenKnopf titel="Fett" icon={Bold} kuerzel={kuerzel('B')} aktiv={aktiv.fett} onClick={() => editor.formatiere('bold')} />
            <LeistenKnopf titel="Kursiv" icon={Italic} kuerzel={kuerzel('I')} aktiv={aktiv.kursiv} onClick={() => editor.formatiere('italic')} />
            <LeistenKnopf
                titel="Unterstrichen" icon={Underline} kuerzel={kuerzel('U')} aktiv={aktiv.unterstrichen}
                onClick={() => editor.formatiere('underline')}
            />
            <LeistenKnopf
                titel={aktiv.link ? 'Link ändern' : 'Link einfügen'}
                icon={Link2}
                kuerzel={kuerzel('K')}
                aktiv={aktiv.link}
                onClick={editor.links.oeffneDialog}
            />
            <Trenner />
            <MailSchriftMenue editor={editor} />
            <MailAbsatzMenue editor={editor} />
        </div>
    );
}
