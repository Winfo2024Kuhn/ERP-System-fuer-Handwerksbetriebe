import { describe, it, expect } from 'vitest';
import { isPdfUrl } from './pdfUrl';

describe('isPdfUrl', () => {
    it('erkennt .pdf-Dateiendungen', () => {
        expect(isPdfUrl('/files/Bodenbelag.pdf')).toBe(true);
        expect(isPdfUrl('https://example.com/x.PDF')).toBe(true);
    });

    it('erkennt typische API-/Dokument-Pfade', () => {
        expect(isPdfUrl('/api/projekte/142/dokumente/7')).toBe(true);
        expect(isPdfUrl('/api/emails/1/attachments/3')).toBe(true);
        expect(isPdfUrl('/api/rechnungen/9/download')).toBe(true);
        expect(isPdfUrl('/api/mahnung/pdf')).toBe(true);
    });

    it('ignoriert Query-String und Hash bei der Erkennung', () => {
        expect(isPdfUrl('/api/mahnung-vorschau?stufe=ZAHLUNGSERINNERUNG')).toBe(true);
        expect(isPdfUrl('/files/x.pdf?token=abc#page=2')).toBe(true);
    });

    it('liefert false für Nicht-PDF-URLs', () => {
        expect(isPdfUrl('/files/tabelle.xlsx')).toBe(false);
        expect(isPdfUrl('/api/projekte/142')).toBe(false);
        expect(isPdfUrl('https://example.com/bild.png')).toBe(false);
    });
});
