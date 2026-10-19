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
