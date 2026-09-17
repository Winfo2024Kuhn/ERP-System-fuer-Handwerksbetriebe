/**
 * Vitest-Suite fuer tiptapVerlauf – Klassifizierung von Tiptap-Transaktionen
 * (Tippen vs. Sonstiges) und externe Wert-Synchronisation im Verlaufsmodus.
 *
 * Laeuft mit einem echten (kopflosen) Tiptap-Editor in jsdom, kein Mock: nur so
 * lassen sich ProseMirror-Transaktionen (ReplaceStep, Paste-Meta, ...) echt pruefen.
 *
 * DSGVO: keine Personendaten, nur Beispieltext ("Hallo").
 */
import { afterEach, describe, expect, it } from 'vitest';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import { aenderungsArtVon, setzeInhaltVonAussen, type TiptapAenderungsArt } from './tiptapVerlauf';

let editor: Editor | undefined;
let container: HTMLElement | undefined;

/** Kopfloser Editor mit demselben undoRedo:false wie im Verlaufsmodus von TiptapEditor. */
function baueEditor(content = '<p>Hallo</p>'): Editor {
    container = document.createElement('div');
    document.body.appendChild(container);
    editor = new Editor({
        element: container,
        extensions: [
            StarterKit.configure({ undoRedo: false }),
            // inline:true wie ResizableImage in TiptapEditor.tsx - sonst ersetzt
            // insertContent bei leerem Absatz den Absatz statt ein Inline-Bild einzufuegen.
            Image.configure({ inline: true, allowBase64: true }),
        ],
        content,
    });
    return editor;
}

afterEach(() => {
    editor?.destroy();
    container?.remove();
    editor = undefined;
    container = undefined;
});

describe('aenderungsArtVon', () => {
    it('erkennt reines Tippen (Zeichen einfuegen) als "tippen"', () => {
        const ed = baueEditor();
        const arten: TiptapAenderungsArt[] = [];
        ed.on('update', ({ transaction, appendedTransactions }) => {
            arten.push(aenderungsArtVon(transaction, appendedTransactions));
        });

        ed.view.dispatch(ed.state.tr.insertText('x', 6));

        expect(arten).toEqual(['tippen']);
    });

    it('erkennt einen Absatzumbruch (splitBlock) als "tippen"', () => {
        const ed = baueEditor();
        const arten: TiptapAenderungsArt[] = [];
        ed.on('update', ({ transaction, appendedTransactions }) => {
            arten.push(aenderungsArtVon(transaction, appendedTransactions));
        });

        ed.commands.splitBlock();

        expect(arten).toEqual(['tippen']);
    });

    it('erkennt Loeschen (deleteRange) als "tippen"', () => {
        const ed = baueEditor();
        const arten: TiptapAenderungsArt[] = [];
        ed.on('update', ({ transaction, appendedTransactions }) => {
            arten.push(aenderungsArtVon(transaction, appendedTransactions));
        });

        ed.commands.deleteRange({ from: 1, to: 3 });

        expect(arten).toEqual(['tippen']);
    });

    it('erkennt eine Formatierung (Fett) als "sonstiges"', () => {
        const ed = baueEditor();
        const arten: TiptapAenderungsArt[] = [];
        ed.on('update', ({ transaction, appendedTransactions }) => {
            arten.push(aenderungsArtVon(transaction, appendedTransactions));
        });

        ed.chain().setTextSelection({ from: 1, to: 3 }).toggleBold().run();

        expect(arten).toEqual(['sonstiges']);
    });

    it('erkennt einen Einfuegevorgang aus der Zwischenablage als "sonstiges"', () => {
        const ed = baueEditor();
        const arten: TiptapAenderungsArt[] = [];
        ed.on('update', ({ transaction, appendedTransactions }) => {
            arten.push(aenderungsArtVon(transaction, appendedTransactions));
        });

        ed.view.dispatch(
            ed.state.tr.insertText('P', 6).setMeta('paste', true).setMeta('uiEvent', 'paste'),
        );

        expect(arten).toEqual(['sonstiges']);
    });

    it('erkennt das Einfuegen eines Bilds als "sonstiges"', () => {
        const ed = baueEditor();
        const arten: TiptapAenderungsArt[] = [];
        ed.on('update', ({ transaction, appendedTransactions }) => {
            arten.push(aenderungsArtVon(transaction, appendedTransactions));
        });

        ed.commands.insertContent({
            type: 'image',
            attrs: { src: 'data:image/png;base64,iVBORw0KGgo=' },
        });

        expect(arten).toEqual(['sonstiges']);
    });
});

describe('setzeInhaltVonAussen', () => {
    it('setzt den Wert im Verlaufsmodus ohne Update-Meldung und mit Cursor an der ersten abweichenden Stelle', () => {
        const ed = baueEditor('<p>ab</p>');
        let updateCount = 0;
        ed.on('update', () => { updateCount += 1; });

        setzeInhaltVonAussen(ed, '<p>aXb</p>', true);

        expect(updateCount).toBe(0);
        expect(ed.getHTML()).toBe('<p>aXb</p>');
        // Alter Inhalt "ab", neuer Inhalt "aXb": das gemeinsame Prefix ist "a",
        // die erste abweichende Stelle liegt direkt danach (Dokumentposition 2).
        expect(ed.state.selection.from).toBe(2);
    });

    it('meldet ausserhalb des Verlaufsmodus wie bisher ein Update', () => {
        const ed = baueEditor('<p>ab</p>');
        let updateCount = 0;
        ed.on('update', () => { updateCount += 1; });

        setzeInhaltVonAussen(ed, '<p>aXb</p>', false);

        expect(updateCount).toBe(1);
        expect(ed.getHTML()).toBe('<p>aXb</p>');
    });
});
