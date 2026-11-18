import { describe, expect, it } from 'vitest';
import { MAX_ATTACHMENT_BYTES, formatFileSize, isImageAttachment, isPdfAttachment, neuerAnhang, passendeAnhaenge } from './anhangGrenze';

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

    it('nimmt nur so viele Dateien an, wie in die Grenze passen', () => {
        const gross = new File(['x'], 'Gross.pdf');
        Object.defineProperty(gross, 'size', { value: MAX_ATTACHMENT_BYTES - 10 });
        const klein = new File(['12345'], 'Klein.pdf');
        const zuViel = new File(['1234567890123'], 'ZuViel.pdf');
        expect(passendeAnhaenge([gross, zuViel, klein], 0)).toEqual({ angenommen: [gross, klein], zuGross: true });
        expect(passendeAnhaenge([klein], MAX_ATTACHMENT_BYTES)).toEqual({ angenommen: [], zuGross: true });
        expect(passendeAnhaenge([klein], 0)).toEqual({ angenommen: [klein], zuGross: false });
    });
});
