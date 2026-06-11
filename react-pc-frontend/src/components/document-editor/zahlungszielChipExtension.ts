import { Node, mergeAttributes } from '@tiptap/core';
import { Plugin, PluginKey } from '@tiptap/pm/state';
import type { Node as ProseMirrorNode } from '@tiptap/pm/model';

/**
 * Geschützter Inline-Chip für das Zahlungsziel in Textbausteinen.
 *
 * Der Chip wird aus `<span data-zahlungsziel-chip>` geparst (erzeugt aus dem
 * Platzhalter {{ZAHLUNGSZIEL}}, siehe helpers.ts) und ist ein Atom-Node:
 * Der Cursor kann nicht hinein, der Inhalt ist nicht editierbar. Ein
 * ProseMirror-Plugin verwirft zusätzlich jede Transaktion, die die Anzahl
 * der Chips verringern würde — der Chip ist damit auch nicht löschbar
 * (Backspace, Entf, Selektion überschreiben, Ausschneiden). Programmatische
 * Ersetzungen des gesamten Inhalts (setContent mit Chip) bleiben möglich,
 * weil dabei die Chip-Anzahl nicht sinkt.
 *
 * Bearbeitet wird das Zahlungsziel über Klick auf den Chip (Popover im
 * DocumentEditor) oder das Feld im SummenFooter — nie über den Text selbst.
 */
export const ZahlungszielChip = Node.create({
    name: 'zahlungszielChip',
    group: 'inline',
    inline: true,
    atom: true,
    selectable: false,
    draggable: false,

    addAttributes() {
        return {
            display: {
                default: '',
                parseHTML: (element: HTMLElement) => element.textContent || '',
                // Der Text wird in renderHTML als Node-Inhalt ausgegeben,
                // nicht als Attribut — daher hier kein Attribut rendern.
                renderHTML: () => ({}),
            },
        };
    },

    parseHTML() {
        return [{ tag: 'span[data-zahlungsziel-chip]' }];
    },

    renderHTML({ node, HTMLAttributes }) {
        // Styling kommt aus index.css ([data-zahlungsziel-chip]), damit
        // getHTML() exakt dem von helpers.ts erzeugten Markup entspricht
        // und der Controlled-Value-Sync im TiptapEditor nicht oszilliert.
        return [
            'span',
            mergeAttributes(HTMLAttributes, { 'data-zahlungsziel-chip': 'true' }),
            node.attrs.display,
        ];
    },

    addProseMirrorPlugins() {
        const chipTypeName = this.name;
        const countChips = (doc: ProseMirrorNode): number => {
            let count = 0;
            doc.descendants(node => {
                if (node.type.name === chipTypeName) count++;
            });
            return count;
        };

        return [
            new Plugin({
                key: new PluginKey('zahlungszielChipGuard'),
                filterTransaction: (transaction, state) => {
                    if (!transaction.docChanged) return true;
                    return countChips(transaction.doc) >= countChips(state.doc);
                },
            }),
        ];
    },
});
