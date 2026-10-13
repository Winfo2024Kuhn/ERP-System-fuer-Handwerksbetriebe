import { describe, expect, it } from 'vitest';
import { PDF_SCHRIFTGROESSE, PDF_TEXTFARBE, pdfStil, stilMitAenderung } from './pdfStil';

describe('pdfStil', () => {
    it('ohne Stil wie im PDF: 10 pt, normal, Textfarbe', () => {
        expect(pdfStil({})).toEqual({ schriftgroesse: PDF_SCHRIFTGROESSE, fett: false, farbe: PDF_TEXTFARBE, betreff: false });
        expect(pdfStil({ styles: {} })).toEqual(pdfStil({}));
    });

    it('übernimmt Größe, Fett (700 oder bold) und Hex-Farbe', () => {
        expect(pdfStil({ styles: { fontSize: 14, fontWeight: '700', color: '#e11d48' } })).toEqual({ schriftgroesse: 14, fett: true, farbe: '#e11d48', betreff: false });
        expect(pdfStil({ styles: { fontWeight: 'Bold' } }).fett).toBe(true);
    });

    it('fällt bei fehlender Größe oder Nicht-Hex-Farbe auf den Druckstandard zurück', () => {
        expect(pdfStil({ styles: { fontSize: 0, color: 'red', fontWeight: '400' } })).toEqual({ schriftgroesse: 10, fett: false, farbe: PDF_TEXTFARBE, betreff: false });
    });

    it('Betreff druckt immer in der festen Betreff-Schrift', () => {
        expect(pdfStil({ content: '{{BETREFF}}', styles: { fontSize: 20 } })).toMatchObject({ schriftgroesse: 11, betreff: true });
    });
});

describe('stilMitAenderung', () => {
    it('schreibt den bisher gedruckten Stil aus und ändert nur das Gewünschte', () => {
        expect(stilMitAenderung({}, { fontWeight: '700' })).toEqual({ fontSize: 10, fontWeight: '700', color: PDF_TEXTFARBE });
    });

    it('schaltet Fett auch wieder aus', () => {
        expect(stilMitAenderung({ styles: { fontWeight: '700' } }, {})).toMatchObject({ fontWeight: '700' });
    });

    it('behält unbekannte gespeicherte Felder', () => {
        expect(stilMitAenderung({ styles: { fontSize: 12, textAlign: 'right' } }, { fontSize: 9 })).toEqual({ fontSize: 9, fontWeight: '400', color: PDF_TEXTFARBE, textAlign: 'right' });
    });
});
