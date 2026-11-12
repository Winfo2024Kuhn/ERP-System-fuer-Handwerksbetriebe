import type { FocusEvent, FormEvent } from 'react';
import { cn } from '../../../lib/utils';
import { MailLinkDialog } from './MailLinkDialog';
import { MailLinkLeiste } from './MailLinkLeiste';
import type { MailEditor } from './useMailEditor';

export interface MailTextFeldProps {
    editor: MailEditor;
    ariaLabel?: string;
    id?: string;
    /** Klassen des editierbaren Elements (Höhe, Innenabstand, Rahmen). */
    className?: string;
    onInput?: (event: FormEvent<HTMLDivElement>) => void;
    onBlur?: (event: FocusEvent<HTMLDivElement>) => void;
    /** Nach Klick oder Taste im Feld, z. B. um die Cursorstelle für Platzhalter zu merken. */
    onAuswahl?: () => void;
}

/**
 * Das editierbare Mail-Textfeld (natives contentEditable) samt Link-Dialog und
 * Link-Leiste. Die Formatierungsleiste setzt der Aufrufer selbst
 * (`MailFormatLeiste`), z. B. in eine Kopfzeile; mit Leiste darüber gibt es
 * `MailNachrichtFeld`.
 *
 * <p>Bewusst kein Tiptap: Es würde zitierte fremde Mails und Signaturen über
 * sein Schema umbauen (Tabellen, Inline-Stile gingen verloren). Befüllt wird
 * das Feld vom Aufrufer über `editor.editorRef` und `setzeEditorHtml()`.</p>
 */
export function MailTextFeld({ editor, ariaLabel = 'Nachricht', id, className, onInput, onBlur, onAuswahl }: MailTextFeldProps) {
    const { editorRef, links, feldEreignisse, linkLeisteId, zurueckInsFeld, verlasseFeld } = editor;
    return (
        <>
            <div
                ref={editorRef}
                id={id}
                role="textbox"
                aria-label={ariaLabel}
                aria-multiline="true"
                aria-controls={links.leistenLink ? linkLeisteId : undefined}
                contentEditable
                suppressContentEditableWarning
                className={cn('mail-text-feld outline-none', className)}
                onInput={onInput}
                {...feldEreignisse}
                onKeyUp={(event) => { feldEreignisse.onKeyUp(event); onAuswahl?.(); }}
                onMouseUp={() => { feldEreignisse.onMouseUp(); onAuswahl?.(); }}
                onBlur={(event) => {
                    feldEreignisse.onBlur(event);
                    onBlur?.(event);
                }}
            />
            {links.leistenLink && (
                <MailLinkLeiste
                    id={linkLeisteId}
                    link={links.leistenLink}
                    onAendern={links.aendereLeistenLink}
                    onEntfernen={links.entferneLeistenLink}
                    onZurueck={zurueckInsFeld}
                    onWeiter={verlasseFeld}
                    onSchliessen={() => links.schliesseLeiste()}
                />
            )}
            {links.dialog && (
                <MailLinkDialog stand={links.dialog} onUebernehmen={links.uebernehme} onSchliessen={links.schliesseDialog} />
            )}
        </>
    );
}
