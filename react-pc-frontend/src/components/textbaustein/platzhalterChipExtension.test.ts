import { afterEach, describe, expect, it } from 'vitest';
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import { chipDarstellung, chipHtmlZuKuerzel, kuerzelZuChipHtml } from './platzhalter';
import { PLATZHALTER_CHIP_NAME, PlatzhalterChip, fuegePlatzhalterEin } from './platzhalterChipExtension';
import { TEST_KATALOG } from './__tests__/testdaten';

let editor: Editor | null = null;

function erzeuge(html: string) {
    editor = new Editor({ extensions: [StarterKit, PlatzhalterChip], content: kuerzelZuChipHtml(html, TEST_KATALOG) });
    return editor;
}

function chipAnzahl(ed: Editor) {
    let anzahl = 0;
    ed.state.doc.descendants((node) => { if (node.type.name === PLATZHALTER_CHIP_NAME) anzahl++; });
    return anzahl;
}

afterEach(() => {
    editor?.destroy();
    editor = null;
});

describe('PlatzhalterChip', () => {
    it('liest Chips als Atom ein und gibt beim Speichern exakt die Original-Kürzel zurück', () => {
        const ed = erzeuge('<p>Hallo {{Kundenname}}, {{Irgendwas}}</p>');
        expect(chipAnzahl(ed)).toBe(2);
        expect(ed.getHTML()).toContain('Max Mustermann');
        expect(chipHtmlZuKuerzel(ed.getHTML())).toBe('<p>Hallo {{Kundenname}}, {{Irgendwas}}</p>');
        expect(ed.getText()).toBe('Hallo {{Kundenname}}, {{Irgendwas}}');
    });

    it('Rücktaste direkt hinter dem Chip löscht ihn als Ganzes', () => {
        const ed = erzeuge('<p>A {{Kundenname}}</p>');
        ed.commands.setTextSelection(ed.state.doc.content.size - 1);
        ed.commands.keyboardShortcut('Backspace');
        expect(chipAnzahl(ed)).toBe(0);
        expect(chipHtmlZuKuerzel(ed.getHTML())).toBe('<p>A </p>');
    });

    it('Entf direkt vor dem Chip löscht ihn als Ganzes', () => {
        const ed = erzeuge('<p>{{Kundenname}} B</p>');
        ed.commands.setTextSelection(1);
        ed.commands.keyboardShortcut('Delete');
        expect(chipAnzahl(ed)).toBe(0);
        expect(chipHtmlZuKuerzel(ed.getHTML())).toBe('<p> B</p>');
    });

    it('fügt einen Chip an der Cursorstelle ein', () => {
        const ed = erzeuge('<p>Guten Tag X</p>');
        ed.commands.setTextSelection(1 + 'Guten Tag '.length);
        fuegePlatzhalterEin(ed, chipDarstellung(TEST_KATALOG, '{{Anrede}}'));
        expect(chipHtmlZuKuerzel(ed.getHTML())).toBe('<p>Guten Tag {{Anrede}}X</p>');
    });
});
