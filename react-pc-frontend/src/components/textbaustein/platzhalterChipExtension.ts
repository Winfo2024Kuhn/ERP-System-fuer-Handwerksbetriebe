import { Node, mergeAttributes } from '@tiptap/core';
import type { Editor } from '@tiptap/core';
import { CHIP_ATTRIBUT, chipAttribute, chipAusElement, type ChipDarstellung } from './platzhalter';

export const PLATZHALTER_CHIP_NAME = 'platzhalterChip';

/**
 * Platzhalter im Textbaustein-Editor als Chip „Bezeichnung · Beispiel“.
 *
 * Atom-Node: Der Cursor kann nicht hinein, der Text darin ist nicht
 * bearbeitbar. Anders als der Zahlungsziel-Chip im Dokument-Editor ist er
 * aber als Ganzes löschbar – Rücktaste davor bzw. Entf dahinter entfernt ihn.
 * Das ursprüngliche Kürzel steht im Attribut und wird beim Speichern
 * unverändert zurückgeschrieben (siehe `chipHtmlZuKuerzel`).
 */
export const PlatzhalterChip = Node.create({
    name: PLATZHALTER_CHIP_NAME,
    group: 'inline',
    inline: true,
    atom: true,
    selectable: true,
    draggable: false,

    addAttributes() {
        const lies = (feld: keyof ChipDarstellung) => ({
            default: feld === 'bekannt' ? false : '',
            parseHTML: (element: HTMLElement) => chipAusElement(element)[feld],
            // Die Attribute schreibt renderHTML gesammelt über chipAttribute().
            renderHTML: () => ({}),
        });
        return { token: lies('token'), label: lies('label'), beispiel: lies('beispiel'), bekannt: lies('bekannt') };
    },

    parseHTML() {
        return [{ tag: `span[${CHIP_ATTRIBUT}]` }];
    },

    renderHTML({ node, HTMLAttributes }) {
        const chip = node.attrs as ChipDarstellung;
        return [
            'span',
            mergeAttributes(HTMLAttributes, chipAttribute(chip)),
            ['span', { 'data-chip-bezeichnung': '' }, chip.label],
            ['span', { 'data-chip-beispiel': '' }, chip.beispiel],
        ];
    },

    renderText({ node }) {
        return String(node.attrs.token ?? '');
    },

    addKeyboardShortcuts() {
        const loesche = (editor: Editor, richtung: 'davor' | 'dahinter') => {
            const { selection } = editor.state;
            if (!selection.empty) return false;
            const { $from } = selection;
            const nachbar = richtung === 'davor' ? $from.nodeBefore : $from.nodeAfter;
            if (nachbar?.type.name !== PLATZHALTER_CHIP_NAME) return false;
            const von = richtung === 'davor' ? $from.pos - nachbar.nodeSize : $from.pos;
            return editor.commands.deleteRange({ from: von, to: von + nachbar.nodeSize });
        };
        return {
            Backspace: ({ editor }) => loesche(editor, 'davor'),
            Delete: ({ editor }) => loesche(editor, 'dahinter'),
        };
    },
});

/** Fügt einen Chip an der Cursorstelle ein. */
export function fuegePlatzhalterEin(editor: Editor, chip: ChipDarstellung): void {
    editor.chain().focus().insertContent({ type: PLATZHALTER_CHIP_NAME, attrs: { ...chip } }).run();
}
