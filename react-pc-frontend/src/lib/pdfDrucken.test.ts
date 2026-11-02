import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { druckePdfAusUrl, istPdfBlob, oeffneDruckFenster } from './pdfDrucken';

const pdf = (inhalt = '%PDF-1.7 Inhalt') => new Blob([inhalt], { type: 'application/pdf' });

function druckFrame(): HTMLIFrameElement | null {
    return document.body.querySelector('iframe[aria-hidden="true"]');
}

/** Löst das `load` des Druck-iframes aus und gibt die gestubbte print-Funktion zurück. */
function laden(frame: HTMLIFrameElement, print: () => void = () => {}) {
    const win = frame.contentWindow as Window;
    const druck = vi.spyOn(win, 'print').mockImplementation(print);
    vi.spyOn(win, 'focus').mockImplementation(() => {});
    frame.onload?.(new Event('load'));
    return { win, druck };
}

describe('istPdfBlob', () => {
    it('erkennt PDFs am Dateianfang', async () => {
        expect(await istPdfBlob(pdf())).toBe(true);
        expect(await istPdfBlob(pdf('<html><script>alert(1)</script></html>'))).toBe(false);
        expect(await istPdfBlob(pdf('%PD'))).toBe(false);
    });

    it('gibt bei einem kaputten Blob false zurück', async () => {
        const kaputt = { size: 10, slice: () => { throw new Error('kaputt'); } } as unknown as Blob;
        expect(await istPdfBlob(kaputt)).toBe(false);
    });
});

describe('oeffneDruckFenster', () => {
    let revoke: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
        document.body.querySelectorAll('iframe').forEach((f) => f.remove());
    });

    it('druckt beim Laden und räumt nach afterprint samt Object-URL auf', () => {
        oeffneDruckFenster('blob:http://localhost/abc', true);
        const frame = druckFrame() as HTMLIFrameElement;
        expect(frame.src).toBe('blob:http://localhost/abc');
        const { win, druck } = laden(frame);
        expect(druck).toHaveBeenCalledTimes(1);
        win.dispatchEvent(new Event('afterprint'));
        expect(druckFrame()).toBeNull();
        expect(revoke).toHaveBeenCalledWith('blob:http://localhost/abc');
    });

    it('gibt fremde URLs nicht frei', () => {
        oeffneDruckFenster('/api/firmendokumente/7/datei', false);
        const frame = druckFrame() as HTMLIFrameElement;
        const { win } = laden(frame);
        win.dispatchEvent(new Event('afterprint'));
        expect(druckFrame()).toBeNull();
        expect(revoke).not.toHaveBeenCalled();
    });

    it('räumt auf, wenn der Browser das Drucken verweigert', () => {
        const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
        oeffneDruckFenster('blob:http://localhost/x', true);
        laden(druckFrame() as HTMLIFrameElement, () => { throw new Error('blockiert'); });
        expect(warn).toHaveBeenCalled();
        expect(druckFrame()).toBeNull();
    });

    it('räumt auf, wenn das iframe kein Fenster hat', () => {
        oeffneDruckFenster('blob:http://localhost/y', true);
        const frame = druckFrame() as HTMLIFrameElement;
        Object.defineProperty(frame, 'contentWindow', { value: null });
        frame.onload?.(new Event('load'));
        expect(druckFrame()).toBeNull();
    });

    it('räumt spätestens nach der Frist auf, auch ohne load und afterprint', () => {
        vi.useFakeTimers();
        oeffneDruckFenster('blob:http://localhost/z', true);
        expect(druckFrame()).not.toBeNull();
        vi.advanceTimersByTime(10 * 60 * 1000);
        expect(druckFrame()).toBeNull();
        expect(revoke).toHaveBeenCalledTimes(1);
    });

    it('räumt nur einmal auf, auch wenn Druckende und Fehler zusammenfallen', () => {
        vi.spyOn(console, 'warn').mockImplementation(() => {});
        oeffneDruckFenster('blob:http://localhost/v', true);
        const frame = druckFrame() as HTMLIFrameElement;
        const win = frame.contentWindow as Window;
        laden(frame, () => { win.dispatchEvent(new Event('afterprint')); throw new Error('abgebrochen'); });
        expect(druckFrame()).toBeNull();
        expect(revoke).toHaveBeenCalledTimes(1);
    });

    it('stellt die Frist nach dem Laden neu und räumt nur einmal auf', () => {
        vi.useFakeTimers();
        oeffneDruckFenster('blob:http://localhost/w', true);
        const { win } = laden(druckFrame() as HTMLIFrameElement);
        vi.advanceTimersByTime(10 * 60 * 1000);
        expect(druckFrame()).toBeNull();
        win.dispatchEvent(new Event('afterprint'));
        expect(revoke).toHaveBeenCalledTimes(1);
    });

    it('hängt bei unsicherer URL kein iframe an', () => {
        oeffneDruckFenster('javascript:alert(1)', true);
        expect(druckFrame()).toBeNull();
        expect(revoke).toHaveBeenCalledWith('javascript:alert(1)');
    });
});

describe('druckePdfAusUrl', () => {
    afterEach(() => {
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
        document.body.querySelectorAll('iframe').forEach((f) => f.remove());
    });

    it('lädt das PDF und öffnet den Druck aus einer eigenen Object-URL', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, blob: async () => pdf() }));
        const erzeugen = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:http://localhost/neu');
        await druckePdfAusUrl('/api/firmendokumente/7/datei');
        expect(fetch).toHaveBeenCalledWith('/api/firmendokumente/7/datei');
        expect(erzeugen).toHaveBeenCalled();
        expect(druckFrame()?.src).toBe('blob:http://localhost/neu');
    });

    it('meldet fehlende Verbindung', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
        await expect(druckePdfAusUrl('/x')).rejects.toThrow('Keine Verbindung zum Server. Das Dokument konnte nicht gedruckt werden.');
    });

    it('meldet einen Serverfehler', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 404 }));
        await expect(druckePdfAusUrl('/x')).rejects.toThrow('Das Dokument konnte nicht geladen werden.');
    });

    it('druckt keine Nicht-PDFs', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, blob: async () => pdf('<html></html>') }));
        await expect(druckePdfAusUrl('/x')).rejects.toThrow('Die Datei ist kein lesbares PDF und kann nicht gedruckt werden.');
        expect(druckFrame()).toBeNull();
    });
});
