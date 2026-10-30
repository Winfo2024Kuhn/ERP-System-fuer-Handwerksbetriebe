import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const WORKER = 'pdfjs-dist/legacy/build/pdf.worker.min.mjs?url';
const BIBLIOTHEK = 'pdfjs-dist/legacy/build/pdf.mjs';

/** Frischer Modulzustand je Test – der Lader merkt sich sein Ergebnis auf Modulebene. */
async function frischerLader() {
    vi.resetModules();
    return import('./pdfjs');
}

describe('ladePdfjs', () => {
    beforeEach(() => {
        vi.spyOn(console, 'warn').mockImplementation(() => { });
    });

    afterEach(() => {
        vi.doUnmock(BIBLIOTHEK);
        vi.doUnmock(WORKER);
        vi.restoreAllMocks();
    });

    it('lädt Bibliothek und lokalen Worker nur einmal', async () => {
        const fabrik = vi.fn(() => ({ GlobalWorkerOptions: { workerSrc: '' }, getDocument: vi.fn() }));
        vi.doMock(BIBLIOTHEK, fabrik);
        vi.doMock(WORKER, () => ({ default: '/assets/pdf.worker-abc.mjs' }));
        const { ladePdfjs } = await frischerLader();

        const erst = await ladePdfjs();
        const zweit = await ladePdfjs();

        expect(erst).not.toBeNull();
        expect(erst?.GlobalWorkerOptions.workerSrc).toBe('/assets/pdf.worker-abc.mjs');
        expect(zweit).toBe(erst);
        expect(fabrik).toHaveBeenCalledTimes(1);
    });

    it('liefert null, wenn das Laden scheitert, und versucht es beim nächsten PDF erneut', async () => {
        let versuch = 0;
        vi.doMock(BIBLIOTHEK, () => {
            versuch += 1;
            if (versuch === 1) throw new Error('Funkloch');
            return { GlobalWorkerOptions: { workerSrc: '' } };
        });
        vi.doMock(WORKER, () => ({ default: '/assets/pdf.worker-abc.mjs' }));
        const { ladePdfjs } = await frischerLader();

        await expect(ladePdfjs()).resolves.toBeNull();
        expect(console.warn).toHaveBeenCalled();

        vi.resetModules(); // Fehlgeschlagenen Import nicht aus dem Modul-Cache bedienen
        await expect(ladePdfjs()).resolves.not.toBeNull();
    });
});

describe('oeffnePdf', () => {
    it('nennt PDF.js alle Hilfsordner – sonst fehlt bei Fax-PDFs der ganze Inhalt', async () => {
        const { oeffnePdf } = await import('./pdfjs');
        const getDocument = vi.fn(() => ({ promise: Promise.resolve() }));
        const lib = { version: '6.4.299', getDocument } as unknown as Parameters<typeof oeffnePdf>[0];
        const data = new Uint8Array([0x25, 0x50, 0x44, 0x46]);

        oeffnePdf(lib, data);

        expect(getDocument).toHaveBeenCalledWith({
            data,
            wasmUrl: '/assets/pdfjs-6.4.299/wasm/',
            iccUrl: '/assets/pdfjs-6.4.299/iccs/',
            cMapUrl: '/assets/pdfjs-6.4.299/cmaps/',
            standardFontDataUrl: '/assets/pdfjs-6.4.299/standard_fonts/',
        });
    });
});
