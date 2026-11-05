import { describe, expect, it } from 'vitest';
import { dateinameAusDisposition, formatZahl } from './berichtFormat';

describe('formatZahl', () => {
    it('zeigt eine Nachkommastelle mit Komma', () => {
        expect(formatZahl(152)).toBe('152,0');
        expect(formatZahl(7.5)).toBe('7,5');
        expect(formatZahl(1234.56)).toBe('1.234,6');
    });
    it('nimmt für Minusstunden das Minuszeichen', () => {
        expect(formatZahl(-3.5)).toBe('−3,5');
    });
    it('zeigt fast null nicht als „−0,0“', () => {
        expect(formatZahl(-0.04)).toBe('0,0');
    });
});

describe('dateinameAusDisposition', () => {
    const ERSATZ = 'Bericht.pdf';
    it('liest den Namen in Anführungszeichen', () => {
        expect(dateinameAusDisposition('attachment; filename="Stundenaufstellung_2026-10.pdf"', ERSATZ))
            .toBe('Stundenaufstellung_2026-10.pdf');
    });
    it('liest den Namen ohne Anführungszeichen', () => {
        expect(dateinameAusDisposition('attachment; filename=Zeitbericht.pdf', ERSATZ)).toBe('Zeitbericht.pdf');
    });
    it('bevorzugt die UTF-8-Form', () => {
        expect(dateinameAusDisposition("attachment; filename=\"x.pdf\"; filename*=UTF-8''Bericht%20M%C3%A4rz.pdf", ERSATZ))
            .toBe('Bericht März.pdf');
    });
    it('fällt bei kaputter UTF-8-Form auf den einfachen Namen zurück', () => {
        expect(dateinameAusDisposition("attachment; filename*=UTF-8''%E0%A4%A; filename=\"a.pdf\"", ERSATZ)).toBe('a.pdf');
    });
    it('entfernt Pfade und Sonderzeichen', () => {
        expect(dateinameAusDisposition('attachment; filename="../../etc/pa<ss>.pdf"', ERSATZ)).toBe('pa_ss_.pdf');
        expect(dateinameAusDisposition('attachment; filename="C:\\temp\\a.pdf"', ERSATZ)).toBe('a.pdf');
    });
    it('nimmt den Ersatz ohne brauchbaren Namen', () => {
        expect(dateinameAusDisposition(null, ERSATZ)).toBe(ERSATZ);
        expect(dateinameAusDisposition('inline', ERSATZ)).toBe(ERSATZ);
        expect(dateinameAusDisposition('attachment; filename=".."', ERSATZ)).toBe(ERSATZ);
    });
});
