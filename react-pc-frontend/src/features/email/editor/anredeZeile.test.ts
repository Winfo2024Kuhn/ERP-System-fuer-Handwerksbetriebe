import { describe, expect, it, vi } from 'vitest';
import { ersetzeAnredeZeile } from './anredeZeile';

function editorMit(html: string): HTMLDivElement {
    const editor = document.createElement('div');
    editor.contentEditable = 'true';
    // Testaufbau mit festem, eigenem HTML (keine Fremddaten).
    editor.innerHTML = html;
    return editor;
}

const MAIL = '<p>Sehr geehrter Herr Mustermann,</p><p><br></p><p>anbei die Stundenaufstellung.</p><p>Mit freundlichen Grüßen</p>';

describe('ersetzeAnredeZeile', () => {
    it('tauscht nur die Anredezeile und lässt den übrigen Text stehen', () => {
        const editor = editorMit(MAIL.replace('anbei die', 'anbei wie besprochen die'));
        expect(ersetzeAnredeZeile(editor, 'Sehr geehrter Herr Mustermann,', 'Sehr geehrte Frau Musterfrau,')).toBe(true);
        expect(editor.innerHTML).toBe('<p>Sehr geehrte Frau Musterfrau,</p><p><br></p><p>anbei wie besprochen die Stundenaufstellung.</p><p>Mit freundlichen Grüßen</p>');
    });

    it('löst am Feld ein input-Ereignis aus', () => {
        const editor = editorMit(MAIL);
        const gehoert = vi.fn();
        editor.addEventListener('input', gehoert);
        ersetzeAnredeZeile(editor, 'Sehr geehrter Herr Mustermann,', 'Sehr geehrte Damen und Herren,');
        expect(gehoert).toHaveBeenCalledTimes(1);
    });

    it('findet die Zeile auch, wenn davor etwas eingefügt wurde', () => {
        const editor = editorMit(`<p>Vorab: kurze Notiz</p>${MAIL}`);
        expect(ersetzeAnredeZeile(editor, 'Sehr geehrter Herr Mustermann,', 'Sehr geehrte Damen und Herren,')).toBe(true);
        expect(editor.querySelectorAll('p')[1].textContent).toBe('Sehr geehrte Damen und Herren,');
        expect(editor.querySelectorAll('p')[0].textContent).toBe('Vorab: kurze Notiz');
    });

    it('lässt eine selbst umgeschriebene Anrede in Ruhe', () => {
        const editor = editorMit(MAIL.replace('Sehr geehrter Herr Mustermann,', 'Hallo Max,'));
        const vorher = editor.innerHTML;
        const gehoert = vi.fn();
        editor.addEventListener('input', gehoert);
        expect(ersetzeAnredeZeile(editor, 'Sehr geehrter Herr Mustermann,', 'Sehr geehrte Damen und Herren,')).toBe(false);
        expect(editor.innerHTML).toBe(vorher);
        expect(gehoert).not.toHaveBeenCalled();
    });

    it('erzwingt nichts, wenn die Anrede gelöscht wurde', () => {
        const editor = editorMit('<p>anbei die Stundenaufstellung.</p>');
        expect(ersetzeAnredeZeile(editor, 'Sehr geehrter Herr Mustermann,', 'Sehr geehrte Damen und Herren,')).toBe(false);
        expect(editor.innerHTML).toBe('<p>anbei die Stundenaufstellung.</p>');
    });

    it('nimmt den innersten Absatz, nicht einen umschließenden Block', () => {
        const editor = editorMit('<div><p>Sehr geehrter Herr Mustermann,</p></div><p>Text</p>');
        ersetzeAnredeZeile(editor, 'Sehr geehrter Herr Mustermann,', 'Sehr geehrte Frau Musterfrau,');
        expect(editor.innerHTML).toBe('<div><p>Sehr geehrte Frau Musterfrau,</p></div><p>Text</p>');
    });

    it('setzt Namen als Text, nie als HTML', () => {
        const editor = editorMit(MAIL);
        ersetzeAnredeZeile(editor, 'Sehr geehrter Herr Mustermann,', 'Sehr geehrte Frau <img src=x onerror=alert(1)><script>alert(2)</script>,');
        expect(editor.querySelector('img')).toBeNull();
        expect(editor.querySelector('script')).toBeNull();
        expect(editor.querySelector('p')?.textContent).toBe('Sehr geehrte Frau <img src=x onerror=alert(1)><script>alert(2)</script>,');
    });

    it('macht ohne Editor oder bei gleicher Anrede nichts', () => {
        expect(ersetzeAnredeZeile(null, 'a', 'b')).toBe(false);
        const editor = editorMit(MAIL);
        const gehoert = vi.fn();
        editor.addEventListener('input', gehoert);
        expect(ersetzeAnredeZeile(editor, 'Sehr geehrter Herr Mustermann,', 'Sehr geehrter Herr Mustermann,')).toBe(false);
        expect(gehoert).not.toHaveBeenCalled();
    });
});
