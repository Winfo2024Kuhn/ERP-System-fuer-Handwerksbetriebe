/**
 * Tests für die Tabellen-Bausteine des Mail-Textfelds (reine DOM-Funktionen).
 * DSGVO: nur Dummy-Daten.
 */
import { describe, expect, it } from 'vitest';
import {
    TABELLE_MAX,
    fuegeSpalteEin,
    fuegeZeileEin,
    loescheSpalte,
    loescheTabelle,
    loescheZeile,
    nachbarZelle,
    tabellenHtml,
    zelleBeiAuswahl,
} from './mailTabelle';

function feld(html: string) {
    const editor = document.createElement('div');
    editor.innerHTML = html;
    document.body.replaceChildren(editor);
    return editor;
}

const zellen = (editor: HTMLElement) => Array.from(editor.querySelectorAll('tr'))
    .map(zeile => Array.from(zeile.cells).map(zelle => zelle.textContent));

const RASTER = '<table><tbody><tr><td>A1</td><td>B1</td></tr><tr><td>A2</td><td>B2</td></tr></tbody></table>';

describe('tabellenHtml', () => {
    it('baut Zeilen und Spalten mit Inline-Rahmen und klickbaren leeren Zellen', () => {
        const editor = feld(tabellenHtml(3, 4));
        const tabelle = editor.querySelector('table')!;
        expect(tabelle.getAttribute('style')).toContain('border-collapse:collapse');
        expect(tabelle.rows).toHaveLength(3);
        expect(tabelle.rows[0].cells).toHaveLength(4);
        const zelle = tabelle.rows[0].cells[0];
        expect(zelle.tagName).toBe('TD');
        expect(zelle.getAttribute('style')).toBe('border:1px solid #cbd5e1;padding:4px 8px;vertical-align:top');
        expect(zelle.innerHTML).toBe('<br>');
        expect(editor.querySelector('th')).toBeNull();
        // Absatz dahinter, damit man unter der Tabelle weiterschreiben kann.
        expect(tabelle.nextElementSibling?.outerHTML).toBe('<p><br></p>');
    });

    it('begrenzt die Größe auf 1 bis 8', () => {
        const editor = feld(tabellenHtml(0, 99));
        const tabelle = editor.querySelector('table')!;
        expect(tabelle.rows).toHaveLength(1);
        expect(tabelle.rows[0].cells).toHaveLength(TABELLE_MAX);
    });
});

describe('zelleBeiAuswahl', () => {
    it('findet die Zelle am Cursor, aber nur im Feld', () => {
        const editor = feld(`<p>Text</p>${RASTER}`);
        const range = document.createRange();
        range.setStart(editor.querySelectorAll('td')[3].firstChild!, 1);
        expect(zelleBeiAuswahl(editor, range)?.textContent).toBe('B2');

        range.setStart(editor.querySelector('p')!.firstChild!, 1);
        expect(zelleBeiAuswahl(editor, range)).toBeNull();
        expect(zelleBeiAuswahl(editor, null)).toBeNull();

        const fremd = document.createElement('table');
        fremd.innerHTML = '<tbody><tr><td>X</td></tr></tbody>';
        document.body.append(fremd);
        range.setStart(fremd.querySelector('td')!.firstChild!, 0);
        expect(zelleBeiAuswahl(editor, range)).toBeNull();
    });
});

describe('Zeilen und Spalten', () => {
    it('fügt Zeilen darüber und darunter mit den Stilen der Zeile ein', () => {
        const editor = feld('<table><tbody><tr><td style="border:1px solid red">A1</td><td>B1</td></tr></tbody></table>');
        const neu = fuegeZeileEin(editor.querySelectorAll('td')[1], 'unten');
        expect(zellen(editor)).toEqual([['A1', 'B1'], ['', '']]);
        expect(neu.cellIndex).toBe(1);
        expect(editor.querySelectorAll('tr')[1].cells[0].getAttribute('style')).toBe('border:1px solid red');
        expect(neu.innerHTML).toBe('<br>');

        fuegeZeileEin(editor.querySelector('td')!, 'oben');
        expect(zellen(editor)).toEqual([['', ''], ['A1', 'B1'], ['', '']]);
    });

    it('fügt Spalten links und rechts in jeder Zeile ein', () => {
        const editor = feld(RASTER);
        const neu = fuegeSpalteEin(editor.querySelectorAll('td')[0], 'rechts');
        expect(zellen(editor)).toEqual([['A1', '', 'B1'], ['A2', '', 'B2']]);
        expect(neu.cellIndex).toBe(1);
        fuegeSpalteEin(editor.querySelectorAll('td')[0], 'links');
        expect(zellen(editor)).toEqual([['', 'A1', '', 'B1'], ['', 'A2', '', 'B2']]);
    });

    it('übernimmt bei Kopfzellen den Zelltyp', () => {
        const editor = feld('<table><tbody><tr><th>Kopf</th></tr><tr><td>Wert</td></tr></tbody></table>');
        fuegeSpalteEin(editor.querySelector('th')!, 'rechts');
        expect(editor.querySelectorAll('tr')[0].cells[1].tagName).toBe('TH');
        expect(editor.querySelectorAll('tr')[1].cells[1].tagName).toBe('TD');
    });

    it('löscht Zeile und Spalte und liefert die Zelle für den Cursor', () => {
        const editor = feld(RASTER);
        const nachZeile = loescheZeile(editor.querySelectorAll('td')[1]);
        expect(zellen(editor)).toEqual([['A2', 'B2']]);
        expect(nachZeile?.textContent).toBe('B2');

        const nachSpalte = loescheSpalte(editor.querySelectorAll('td')[0]);
        expect(zellen(editor)).toEqual([['B2']]);
        expect(nachSpalte?.textContent).toBe('B2');
    });

    it('entfernt die ganze Tabelle, wenn die letzte Zeile oder Spalte geht', () => {
        let editor = feld(`<p>vor</p><table><tbody><tr><td>A1</td></tr></tbody></table><p>nach</p>`);
        expect(loescheZeile(editor.querySelector('td')!)).toBeNull();
        expect(editor.innerHTML).toBe('<p>vor</p><p>nach</p>');

        editor = feld(`<table><tbody><tr><td>A1</td></tr><tr><td>A2</td></tr></tbody></table>`);
        expect(loescheSpalte(editor.querySelector('td')!)).toBeNull();
        expect(editor.querySelector('table')).toBeNull();
    });

    it('löscht die Tabelle und lässt den Text drumherum stehen', () => {
        const editor = feld(`<p>vor</p>${RASTER}<p>nach</p>`);
        const danach = loescheTabelle(editor.querySelector('td')!);
        expect(editor.innerHTML).toBe('<p>vor</p><p>nach</p>');
        expect(danach?.textContent).toBe('nach');
    });
});

describe('nachbarZelle', () => {
    it('geht zeilenweise vor und zurück und endet an den Rändern', () => {
        const editor = feld(RASTER);
        const [a1, b1, a2, b2] = Array.from(editor.querySelectorAll('td'));
        expect(nachbarZelle(a1, 1)).toBe(b1);
        expect(nachbarZelle(b1, 1)).toBe(a2);
        expect(nachbarZelle(a2, -1)).toBe(b1);
        expect(nachbarZelle(b2, 1)).toBeNull();
        expect(nachbarZelle(a1, -1)).toBeNull();
    });

    it('springt nicht in verschachtelte Tabellen', () => {
        const editor = feld('<table><tbody><tr><td>A1<table><tbody><tr><td>innen</td></tr></tbody></table></td><td>B1</td></tr></tbody></table>');
        const aussen = editor.querySelector('table')!;
        expect(nachbarZelle(aussen.rows[0].cells[0], 1)?.textContent).toBe('B1');
    });
});
