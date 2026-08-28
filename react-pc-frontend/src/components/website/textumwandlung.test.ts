import { describe, it, expect } from 'vitest';
import DOMPurify from 'dompurify';
import {
    klartextZuHtml,
    leiteKurzbeschreibungAb,
    bereinigeBeitragsHtml,
} from './textumwandlung';

describe('klartextZuHtml', () => {
    it('macht aus Leerzeilen einzelne Absaetze', () => {
        const html = klartextZuHtml('Erster Absatz.\n\nZweiter Absatz.');

        expect(html).toBe('<p>Erster Absatz.</p><p>Zweiter Absatz.</p>');
    });

    it('macht aus einem einzelnen Zeilenumbruch ein br', () => {
        expect(klartextZuHtml('Zeile eins\nZeile zwei')).toBe('<p>Zeile eins<br>Zeile zwei</p>');
    });

    it('macht aus zusammenhaengenden Bindestrich-Zeilen eine Liste', () => {
        const html = klartextZuHtml('Wir haben gemacht:\n\n- Tor gesetzt\n- Gelaender montiert');

        expect(html).toBe(
            '<p>Wir haben gemacht:</p><ul><li>Tor gesetzt</li><li>Gelaender montiert</li></ul>');
    });

    it('maskiert spitze Klammern, damit kein HTML aus dem Modell durchkommt', () => {
        expect(klartextZuHtml('<script>alert(1)</script>'))
            .toBe('<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>');
    });

    it('liefert bei leerem Text einen leeren String', () => {
        expect(klartextZuHtml('   ')).toBe('');
    });
});

describe('leiteKurzbeschreibungAb', () => {
    it('entfernt Tags und faltet Leerraum zusammen', () => {
        expect(leiteKurzbeschreibungAb('<p>Neues   Tor</p><p>in Wuerzburg</p>'))
            .toBe('Neues Tor in Wuerzburg');
    });

    it('kuerzt an der Wortgrenze und haengt Auslassungspunkte an', () => {
        const lang = `<p>${'wort '.repeat(60)}</p>`;

        const kurz = leiteKurzbeschreibungAb(lang);

        expect(kurz.length).toBeLessThanOrEqual(161);
        expect(kurz.endsWith('…')).toBe(true);
        expect(kurz).not.toContain('wor…');
    });

    it('laesst kurze Texte unveraendert und ohne Auslassungspunkte', () => {
        expect(leiteKurzbeschreibungAb('<p>Kurz.</p>')).toBe('Kurz.');
    });
});

describe('bereinigeBeitragsHtml', () => {
    it('behaelt die erlaubten Tags', () => {
        const html = '<p>Ein <strong>fester</strong> Rahmen</p><ul><li>Punkt</li></ul>';

        expect(bereinigeBeitragsHtml(html)).toBe(html);
    });

    it('wirft Ueberschriften und Links weg, behaelt aber deren Text', () => {
        const sauber = bereinigeBeitragsHtml('<h2>Titel</h2><a href="https://x.de">Link</a>');

        expect(sauber).not.toContain('<h2');
        expect(sauber).not.toContain('<a ');
        expect(sauber).toContain('Titel');
    });

    it('entfernt script-Elemente vollstaendig', () => {
        expect(bereinigeBeitragsHtml('<p>Text</p><script>alert(1)</script>'))
            .toBe('<p>Text</p>');
    });

    it('behaelt Farbe an span', () => {
        const html = '<span style="color: #500010;">rot</span>';

        expect(bereinigeBeitragsHtml(html)).toContain('color');
    });

    it('behaelt Schriftgroesse an span', () => {
        const html = '<span style="font-size: 18px;">gross</span>';

        expect(bereinigeBeitragsHtml(html)).toContain('font-size');
    });

    it('entfernt Ereignis-Attribute', () => {
        expect(bereinigeBeitragsHtml('<p onclick="alert(1)">Text</p>'))
            .toBe('<p>Text</p>');
    });

    it('entfernt style an allem ausser span', () => {
        expect(bereinigeBeitragsHtml('<p style="color:#500010">Text</p>'))
            .toBe('<p>Text</p>');
    });

    it('entfernt Farben, die die Website nicht annimmt', () => {
        // Die Website laesst nur #hex und rgb() durch, kein Farbwort.
        expect(bereinigeBeitragsHtml('<span style="color: red">Text</span>'))
            .not.toContain('color');
    });

    it('entfernt Schriftgroessen ohne Einheit', () => {
        expect(bereinigeBeitragsHtml('<span style="font-size: xx-large">Text</span>'))
            .not.toContain('font-size');
    });

    it('behaelt eine gueltige Hex-Farbe am span', () => {
        expect(bereinigeBeitragsHtml('<span style="color: #500010">Text</span>'))
            .toContain('#500010');
    });

    it('laesst die E-Mail-Signaturen unberuehrt', () => {
        // Gegenprobe auf den globalen Hook: der Standard-Import von DOMPurify
        // darf von unserem Hook nichts mitbekommen.
        expect(DOMPurify.sanitize('<p style="color:red">Signatur</p>'))
            .toContain('style');
    });
});
