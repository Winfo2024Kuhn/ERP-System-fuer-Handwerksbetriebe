import { describe, expect, it } from 'vitest';
import { formatFileSize, isImageAttachment, isPdfAttachment, neuerAnhang } from './anhangGrenze';

describe('anhangGrenze', () => {
    it('gibt jedem Anhang eine feste, eindeutige Kennung', () => {
        const datei = new File(['x'], 'plan.pdf', { type: 'application/pdf' });
        const a = neuerAnhang(datei);
        const b = neuerAnhang(datei, 7);
        expect(a.id).not.toBe(b.id);
        expect(a).toMatchObject({ file: datei });
        expect(b).toMatchObject({ file: datei, sourceDokumentId: 7 });
        expect(a.sourceDokumentId).toBeUndefined();
    });

    it('erkennt Bilder und PDFs und formatiert Größen', () => {
        expect(isImageAttachment(new File([''], 'foto.JPG'))).toBe(true);
        expect(isPdfAttachment(new File([''], 'x', { type: 'application/pdf' }))).toBe(true);
        expect(formatFileSize(10)).toBe('1 KB');
        expect(formatFileSize(3 * 1024 * 1024)).toBe('3.0 MB');
    });
});
