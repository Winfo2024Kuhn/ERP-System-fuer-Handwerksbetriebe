import { forwardRef, useImperativeHandle, type ReactNode } from 'react';
import { EditorContent, useEditor, useEditorState } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { Bold, Heading3, List, ListOrdered, Redo, Undo } from 'lucide-react';
import { cn } from '../../../lib/utils';
import { RECHTSTEXT_TYPO } from './rechtstextApi';

/** Was der Dialog von außen mit dem Editor tun darf. */
export interface RechtstextRichtextHandle {
    /** Fügt reinen Text (z. B. einen Platzhalter) an der Cursorposition ein. */
    fuegeTextEin: (text: string) => void;
    /** Hängt einen HTML-Absatz unten an den Text an. */
    haengeAbsatzAn: (html: string) => void;
    /**
     * Ersetzt den ganzen Inhalt. `alsAenderung` meldet das über `onChange`
     * nach oben (z. B. „In den Editor übernehmen“), sonst nicht (Laden).
     */
    setzeInhalt: (html: string, alsAenderung: boolean) => void;
}

export interface RechtstextRichtextProps {
    /** Anfangsinhalt. Spätere Wechsel laufen über {@link RechtstextRichtextHandle.setzeInhalt}. */
    anfangsHtml: string;
    onChange: (html: string) => void;
    /** Sichtbare Beschriftung des Eingabefelds für Screenreader. */
    label: string;
}

/**
 * Schmaler Texteditor für feste Texte wie die Widerrufsbelehrung.
 *
 * Bewusst nur die Auszeichnungen, die das Backend behält (p, br, strong, em,
 * h3, ul, ol, li). Bilder, Farben, Links, Ausrichtung, Zitate und Code sind
 * abgeschaltet – sie würden beim Speichern stillschweigend verschwinden.
 * Kursiv bleibt per Tastenkürzel erhalten, damit vorhandene Hervorhebungen
 * aus dem Standardtext nicht verloren gehen.
 */
export const RechtstextRichtext = forwardRef<RechtstextRichtextHandle, RechtstextRichtextProps>(
    function RechtstextRichtext({ anfangsHtml, onChange, label }, ref) {
        const editor = useEditor({
            extensions: [
                StarterKit.configure({
                    heading: { levels: [3] },
                    blockquote: false,
                    codeBlock: false,
                    code: false,
                    horizontalRule: false,
                    strike: false,
                    link: false,
                    underline: false,
                }),
            ],
            content: anfangsHtml,
            onUpdate: ({ editor: e }) => onChange(e.getHTML()),
            editorProps: {
                attributes: {
                    class: cn('focus:outline-none min-h-[320px] px-5 py-4', RECHTSTEXT_TYPO),
                    role: 'textbox',
                    'aria-multiline': 'true',
                    'aria-label': label,
                },
            },
        });

        useImperativeHandle(ref, () => ({
            fuegeTextEin: (text) => {
                editor?.chain().focus().insertContent({ type: 'text', text }).run();
            },
            haengeAbsatzAn: (html) => {
                if (!editor) return;
                editor.chain().insertContentAt(editor.state.doc.content.size, html).focus('end').scrollIntoView().run();
            },
            setzeInhalt: (html, alsAenderung) => {
                editor?.commands.setContent(html, { emitUpdate: alsAenderung });
            },
        }), [editor]);

        // Tiptap 3 rendert bei Eingaben nicht von selbst neu – die Knopf-Zustände
        // (aktiv, Rückgängig möglich) deshalb gezielt mitlesen.
        const zustand = useEditorState({
            editor,
            selector: ({ editor: e }) => ({
                fett: e?.isActive('bold') ?? false,
                ueberschrift: e?.isActive('heading', { level: 3 }) ?? false,
                aufzaehlung: e?.isActive('bulletList') ?? false,
                nummerierung: e?.isActive('orderedList') ?? false,
                rueckgaengig: e?.can().undo() ?? false,
                wiederholen: e?.can().redo() ?? false,
            }),
        });

        if (!editor || !zustand) return null;

        return (
            <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-lg border border-slate-200 bg-white focus-within:border-rose-300 focus-within:ring-2 focus-within:ring-rose-500/20">
                <div role="toolbar" aria-label="Textformat" className="flex flex-wrap items-center gap-1 border-b border-slate-200 bg-slate-50 px-2 py-1.5">
                    <Werkzeug label="Fett" aktiv={zustand.fett} onClick={() => editor.chain().focus().toggleBold().run()}>
                        <Bold className="h-4 w-4" />
                    </Werkzeug>
                    <Werkzeug
                        label="Überschrift"
                        aktiv={zustand.ueberschrift}
                        onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
                    >
                        <Heading3 className="h-4 w-4" />
                    </Werkzeug>
                    <span className="mx-1 h-5 w-px bg-slate-300" aria-hidden="true" />
                    <Werkzeug label="Aufzählung" aktiv={zustand.aufzaehlung} onClick={() => editor.chain().focus().toggleBulletList().run()}>
                        <List className="h-4 w-4" />
                    </Werkzeug>
                    <Werkzeug label="Nummerierung" aktiv={zustand.nummerierung} onClick={() => editor.chain().focus().toggleOrderedList().run()}>
                        <ListOrdered className="h-4 w-4" />
                    </Werkzeug>
                    <span className="flex-1" />
                    <Werkzeug label="Rückgängig" hinweisWennAus="Es gibt nichts rückgängig zu machen." disabled={!zustand.rueckgaengig} onClick={() => editor.chain().focus().undo().run()}>
                        <Undo className="h-4 w-4" />
                    </Werkzeug>
                    <Werkzeug label="Wiederholen" hinweisWennAus="Es gibt nichts zu wiederholen." disabled={!zustand.wiederholen} onClick={() => editor.chain().focus().redo().run()}>
                        <Redo className="h-4 w-4" />
                    </Werkzeug>
                </div>
                <div className="min-h-0 flex-1 overflow-y-auto">
                    <EditorContent editor={editor} />
                </div>
            </div>
        );
    },
);

function Werkzeug({ label, aktiv, disabled, hinweisWennAus, onClick, children }: {
    label: string;
    /** Tooltip, wenn der Knopf gesperrt ist – sagt, warum. */
    hinweisWennAus?: string;
    aktiv?: boolean;
    disabled?: boolean;
    onClick: () => void;
    children: ReactNode;
}) {
    return (
        <button
            type="button"
            aria-label={label}
            title={disabled && hinweisWennAus ? hinweisWennAus : label}
            aria-pressed={aktiv === undefined ? undefined : aktiv}
            disabled={disabled}
            onClick={onClick}
            className={cn(
                'rounded p-1.5 transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-rose-500',
                'disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent',
                aktiv ? 'bg-rose-100 text-rose-700' : 'text-slate-600 hover:bg-slate-200',
            )}
        >
            {children}
        </button>
    );
}
