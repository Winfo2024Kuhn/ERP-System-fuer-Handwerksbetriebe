import type { FocusEvent, FormEvent, RefObject } from 'react';
import { cn } from '../../../lib/utils';
import { MailFormatLeiste } from './MailFormatLeiste';
import { MailTextFeld } from './MailTextFeld';
import { useMailEditor } from './useMailEditor';

export interface MailNachrichtFeldProps {
    /** Ref auf das editierbare Element: Befüllen per `setzeEditorHtml()`, Versand per `innerHTML`. */
    editorRef: RefObject<HTMLDivElement | null>;
    ariaLabel?: string;
    id?: string;
    /** Klassen des editierbaren Bereichs (Höhe, Innenabstand). */
    className?: string;
    /** Klassen des Rahmens um Leiste und Feld. */
    rahmenClassName?: string;
    onInput?: (event: FormEvent<HTMLDivElement>) => void;
    onBlur?: (event: FocusEvent<HTMLDivElement>) => void;
    /** Nach Klick oder Taste im Feld (Cursorstelle merken). */
    onAuswahl?: () => void;
}

/**
 * Mail-Textfeld mit schmaler Formatierungsleiste darüber – für alle Mailfelder
 * außer dem E-Mail-Formular (dort sitzt die Leiste in der Kopfzeile).
 */
export function MailNachrichtFeld({ editorRef, ariaLabel, id, className, rahmenClassName, onInput, onBlur, onAuswahl }: MailNachrichtFeldProps) {
    const editor = useMailEditor({ editorRef });
    return (
        <div className={cn(
            'overflow-hidden rounded-lg border border-slate-200 bg-white transition-colors focus-within:border-rose-300',
            rahmenClassName,
        )}>
            <div className="flex items-center border-b border-slate-200 bg-slate-50 px-1.5 py-1">
                <MailFormatLeiste editor={editor} />
            </div>
            <MailTextFeld
                editor={editor}
                ariaLabel={ariaLabel}
                id={id}
                className={cn('min-h-[200px] overflow-auto p-4', className)}
                onInput={onInput}
                onBlur={onBlur}
                onAuswahl={onAuswahl}
            />
        </div>
    );
}
