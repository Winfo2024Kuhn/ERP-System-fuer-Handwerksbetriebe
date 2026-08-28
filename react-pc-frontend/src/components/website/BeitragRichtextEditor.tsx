import { useEffect } from 'react';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { TextStyle } from '@tiptap/extension-text-style';
import Color from '@tiptap/extension-color';
import { Bold, Italic, List, ListOrdered, Palette, Redo, Undo } from 'lucide-react';

export interface BeitragRichtextEditorProps {
    html: string;
    onChange: (html: string) => void;
}

/** Farben, die zum Auftritt der Website passen. */
const FARBEN = [
    { name: 'Standard', wert: '#57534e' },
    { name: 'Dunkelrot', wert: '#500010' },
    { name: 'Grau', wert: '#a8a29e' },
];

/**
 * Richtext-Editor fuer Website-Beitraege.
 *
 * Bewusst schmaler als components/TiptapEditor.tsx: erlaubt sind nur
 * Auszeichnungen, die die Website in sanitizePostContent durchlaesst
 * (p, br, b, strong, i, em, ul, ol, li, span). Ueberschriften, Zitate,
 * Codebloecke und Trennlinien sind deshalb abgeschaltet - sie wuerden beim
 * Speichern stillschweigend verschwinden.
 */
export function BeitragRichtextEditor({ html, onChange }: BeitragRichtextEditorProps) {
    const editor = useEditor({
        extensions: [
            StarterKit.configure({
                heading: false,
                blockquote: false,
                codeBlock: false,
                code: false,
                horizontalRule: false,
                strike: false,
            }),
            TextStyle,
            Color,
        ],
        content: html,
        onUpdate: ({ editor: e }) => onChange(e.getHTML()),
        editorProps: {
            attributes: {
                class: 'prose-sm focus:outline-none min-h-[240px] px-4 py-3 text-slate-800',
            },
        },
    });

    // Wechselt der Nutzer den Beitrag oder setzt die KI einen neuen Text,
    // muss der Inhalt von aussen nachgezogen werden. Der Vergleich mit
    // getHTML verhindert, dass jeder eigene Tastendruck den Cursor zuruecksetzt.
    useEffect(() => {
        if (editor && html !== editor.getHTML()) {
            editor.commands.setContent(html, { emitUpdate: false });
        }
    }, [html, editor]);

    if (!editor) return null;

    return (
        <div className="border border-slate-200 rounded-lg overflow-hidden bg-white">
            <div className="flex items-center gap-1 px-2 py-1.5 border-b border-slate-200 bg-slate-50 flex-wrap">
                <Werkzeug
                    label="Fett"
                    aktiv={editor.isActive('bold')}
                    onClick={() => editor.chain().focus().toggleBold().run()}
                >
                    <Bold className="w-4 h-4" />
                </Werkzeug>
                <Werkzeug
                    label="Kursiv"
                    aktiv={editor.isActive('italic')}
                    onClick={() => editor.chain().focus().toggleItalic().run()}
                >
                    <Italic className="w-4 h-4" />
                </Werkzeug>

                <span className="w-px h-5 bg-slate-300 mx-1" />

                <Werkzeug
                    label="Aufzaehlung"
                    aktiv={editor.isActive('bulletList')}
                    onClick={() => editor.chain().focus().toggleBulletList().run()}
                >
                    <List className="w-4 h-4" />
                </Werkzeug>
                <Werkzeug
                    label="Nummerierte Liste"
                    aktiv={editor.isActive('orderedList')}
                    onClick={() => editor.chain().focus().toggleOrderedList().run()}
                >
                    <ListOrdered className="w-4 h-4" />
                </Werkzeug>

                <span className="w-px h-5 bg-slate-300 mx-1" />

                <div className="flex items-center gap-1">
                    <Palette className="w-4 h-4 text-slate-400" />
                    {FARBEN.map(farbe => (
                        <button
                            key={farbe.wert}
                            type="button"
                            aria-label={farbe.name}
                            title={farbe.name}
                            onClick={() => editor.chain().focus().setColor(farbe.wert).run()}
                            className="w-5 h-5 rounded-full border border-slate-300"
                            style={{ backgroundColor: farbe.wert }}
                        />
                    ))}
                </div>

                <span className="flex-1" />

                <Werkzeug label="Rueckgaengig" onClick={() => editor.chain().focus().undo().run()}>
                    <Undo className="w-4 h-4" />
                </Werkzeug>
                <Werkzeug label="Wiederholen" onClick={() => editor.chain().focus().redo().run()}>
                    <Redo className="w-4 h-4" />
                </Werkzeug>
            </div>

            <EditorContent editor={editor} />
        </div>
    );
}

function Werkzeug({ label, aktiv, onClick, children }: {
    label: string;
    aktiv?: boolean;
    onClick: () => void;
    children: React.ReactNode;
}) {
    return (
        <button
            type="button"
            aria-label={label}
            title={label}
            onClick={onClick}
            className={`p-1.5 rounded transition-colors
                ${aktiv ? 'bg-rose-100 text-rose-700' : 'text-slate-600 hover:bg-slate-200'}`}
        >
            {children}
        </button>
    );
}
