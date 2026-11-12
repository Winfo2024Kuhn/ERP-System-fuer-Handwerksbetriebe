import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    aendereLink, entferneLink, fuegeLinkEin, fuehreAus, linkBeiAuswahl, machAuswahlZumLink, setzeSchriftgroesse,
} from './mailEditorDom';

type ExecCommand = (befehl: string, ui?: boolean, wert?: string) => boolean;
const dok = document as unknown as { execCommand?: ExecCommand };

function editorMit(html: string): HTMLDivElement {
    const editor = document.createElement('div');
    editor.contentEditable = 'true';
    editor.innerHTML = html;
    document.body.appendChild(editor);
    return editor;
}

function markiere(knoten: Node, start: number, ende: number): Range {
    const range = document.createRange();
    range.setStart(knoten, start);
    range.setEnd(knoten, ende);
    const auswahl = window.getSelection()!;
    auswahl.removeAllRanges();
    auswahl.addRange(range);
    return range;
}

beforeEach(() => { delete dok.execCommand; });
afterEach(() => { document.body.innerHTML = ''; delete dok.execCommand; });

describe('fuehreAus', () => {
    it('meldet false, wenn der Browser keine Befehle kennt', () => {
        expect(fuehreAus('bold')).toBe(false);
    });

    it('reicht Befehl und Wert an den Browser weiter', () => {
        const exec = vi.fn<ExecCommand>(() => true);
        dok.execCommand = exec;
        expect(fuehreAus('foreColor', '#e11d48')).toBe(true);
        expect(exec).toHaveBeenCalledWith('foreColor', false, '#e11d48');
    });

    it('fängt Fehler des Browsers ab', () => {
        dok.execCommand = () => { throw new Error('nicht erlaubt'); };
        expect(fuehreAus('bold')).toBe(false);
    });
});

describe('fuegeLinkEin', () => {
    it('setzt einen Link mit eigenem Text an den Cursor und den Cursor dahinter', () => {
        const editor = editorMit('<p>Hallo Max</p>');
        const text = editor.querySelector('p')!.firstChild!;
        const range = markiere(text, 6, 6);

        const link = fuegeLinkEin(editor, range, 'Hier klicken', 'https://onedrive.example.com/f/s!Abc');

        expect(editor.innerHTML).toBe('<p>Hallo <a href="https://onedrive.example.com/f/s!Abc">Hier klicken</a>Max</p>');
        expect(link.hasAttribute('data-mail-link-neu')).toBe(false);
        const auswahl = window.getSelection()!.getRangeAt(0);
        expect(auswahl.collapsed).toBe(true);
        expect(auswahl.startContainer).toBe(link.parentNode);
    });

    it('ersetzt markierten Text', () => {
        const editor = editorMit('<p>Bitte hier lesen</p>');
        const range = markiere(editor.querySelector('p')!.firstChild!, 6, 10);
        fuegeLinkEin(editor, range, 'Freigabe', 'https://example.com');
        expect(editor.innerHTML).toBe('<p>Bitte <a href="https://example.com">Freigabe</a> lesen</p>');
    });

    it('schreibt HTML im angezeigten Text nur als Text', () => {
        const editor = editorMit('<p>x</p>');
        const range = markiere(editor.querySelector('p')!.firstChild!, 1, 1);
        fuegeLinkEin(editor, range, '<img src=x onerror=alert(1)>', 'https://example.com');
        expect(editor.querySelector('img')).toBeNull();
        expect(editor.querySelector('a')!.textContent).toBe('<img src=x onerror=alert(1)>');
    });

    it('schickt das HTML für insertHTML bereinigt an den Browser', () => {
        const editor = editorMit('<p>x</p>');
        const range = markiere(editor.querySelector('p')!.firstChild!, 1, 1);
        let gesendet = '';
        dok.execCommand = (befehl, _ui, wert) => {
            if (befehl !== 'insertHTML') return false;
            gesendet = wert ?? '';
            const vorlage = document.createElement('template');
            vorlage.innerHTML = gesendet;
            range.insertNode(vorlage.content);
            return true;
        };

        const link = fuegeLinkEin(editor, range, '<script>alert(1)</script>', 'https://example.com');

        expect(gesendet).toContain('&lt;script&gt;');
        expect(gesendet).not.toContain('<script');
        expect(link.getAttribute('href')).toBe('https://example.com');
        expect(editor.querySelector('[data-mail-link-neu]')).toBeNull();
    });
});

describe('machAuswahlZumLink', () => {
    it('macht den markierten Text samt Formatierung zum Link', () => {
        const editor = editorMit('<p>Bitte <b>hier</b> klicken</p>');
        const range = document.createRange();
        range.selectNode(editor.querySelector('b')!);
        const link = machAuswahlZumLink(editor, range, 'https://example.com');
        expect(editor.innerHTML).toBe('<p>Bitte <a href="https://example.com"><b>hier</b></a> klicken</p>');
        expect(link?.tagName).toBe('A');
    });

    it('nutzt createLink des Browsers, wenn vorhanden', () => {
        const editor = editorMit('<p>Bitte hier klicken</p>');
        const range = markiere(editor.querySelector('p')!.firstChild!, 6, 10);
        const exec = vi.fn<ExecCommand>((befehl, _ui, wert) => {
            if (befehl !== 'createLink') return false;
            const a = document.createElement('a');
            a.setAttribute('href', wert ?? '');
            a.appendChild(range.extractContents());
            range.insertNode(a);
            return true;
        });
        dok.execCommand = exec;

        const link = machAuswahlZumLink(editor, range, 'https://example.com');

        expect(exec).toHaveBeenCalledWith('createLink', false, 'https://example.com');
        expect(link?.textContent).toBe('hier');
    });
});

describe('aendereLink und entferneLink', () => {
    it('ändert Adresse und Text', () => {
        const editor = editorMit('<p>Siehe <a href="https://alt.example.com">alt</a>.</p>');
        const link = editor.querySelector('a')!;
        aendereLink(link, 'Hier klicken', 'https://neu.example.com');
        expect(editor.innerHTML).toBe('<p>Siehe <a href="https://neu.example.com">Hier klicken</a>.</p>');
    });

    it('behält die Formatierung, wenn nur die Adresse geändert wird', () => {
        const editor = editorMit('<p><a href="https://alt.example.com"><b>Fett</b></a></p>');
        aendereLink(editor.querySelector('a')!, 'Fett', 'https://neu.example.com');
        expect(editor.innerHTML).toBe('<p><a href="https://neu.example.com"><b>Fett</b></a></p>');
    });

    it('entfernt den Link und lässt den Text stehen', () => {
        const editor = editorMit('<p>Siehe <a href="https://alt.example.com"><b>hier</b> lesen</a>.</p>');
        entferneLink(editor.querySelector('a')!);
        expect(editor.innerHTML).toBe('<p>Siehe <b>hier</b> lesen.</p>');
    });
});

describe('linkBeiAuswahl', () => {
    it('findet den Link, in dem der Cursor steht', () => {
        const editor = editorMit('<p>Siehe <a href="https://x.example.com"><b>hier</b></a></p>');
        const range = markiere(editor.querySelector('b')!.firstChild!, 2, 2);
        expect(linkBeiAuswahl(editor, range)).toBe(editor.querySelector('a'));
    });

    it('ignoriert Links außerhalb des Felds und Stellen ohne Link', () => {
        const editor = editorMit('<p>Text</p>');
        const draussen = document.createElement('a');
        draussen.href = 'https://x.example.com';
        draussen.textContent = 'x';
        document.body.appendChild(draussen);
        expect(linkBeiAuswahl(editor, markiere(editor.querySelector('p')!.firstChild!, 1, 1))).toBeNull();
        expect(linkBeiAuswahl(editor, markiere(draussen.firstChild!, 0, 0))).toBeNull();
        expect(linkBeiAuswahl(editor, null)).toBeNull();
    });
});

describe('setzeSchriftgroesse', () => {
    function simuliereFontSize(range: Range) {
        dok.execCommand = (befehl, _ui, wert) => {
            if (befehl === 'styleWithCSS') return true;
            if (befehl !== 'fontSize') return false;
            const font = document.createElement('font');
            font.setAttribute('size', wert ?? '');
            font.appendChild(range.extractContents());
            range.insertNode(font);
            return true;
        };
    }

    it('setzt die Größe als Inline-Stil statt <font size>', () => {
        const editor = editorMit('<p>Wichtiger Hinweis</p>');
        const range = markiere(editor.querySelector('p')!.firstChild!, 0, 9);
        simuliereFontSize(range);

        expect(setzeSchriftgroesse(editor, 'gross')).toBe(true);

        expect(editor.querySelector('font')).toBeNull();
        const span = editor.querySelector('span')!;
        expect(span.style.fontSize).toBe('18px');
        expect(span.textContent).toBe('Wichtiger');
        expect(window.getSelection()!.toString()).toBe('Wichtiger');
    });

    it('nimmt bei „Normal“ alte Größen aus dem markierten Text', () => {
        const editor = editorMit('<p><span style="font-size: 13px;">klein</span></p>');
        const range = document.createRange();
        range.selectNode(editor.querySelector('span')!);
        simuliereFontSize(range);

        setzeSchriftgroesse(editor, 'normal');

        expect(editor.querySelector('font')).toBeNull();
        expect(editor.innerHTML).not.toContain('font-size');
        expect(editor.textContent).toBe('klein');
    });

    it('meldet false, wenn der Browser die Größe nicht setzen kann', () => {
        const editor = editorMit('<p>x</p>');
        expect(setzeSchriftgroesse(editor, 'klein')).toBe(false);
    });
});
