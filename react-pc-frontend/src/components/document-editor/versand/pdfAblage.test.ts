import { describe, it, expect, vi, beforeEach } from 'vitest';
import { druckePdf, erzeugePdf, ladePdfHerunter, legePdfAb, pdfEndpunkt } from './pdfAblage';

const pdf = () => new Blob(['%PDF-1.4'], { type: 'application/pdf' });

beforeEach(() => {
    vi.restoreAllMocks();
});

describe('pdfEndpunkt', () => {
    it('wählt den Dienst nach Format', () => {
        expect(pdfEndpunkt('pdf')).toBe('/api/dokument-generator/pdf');
        expect(pdfEndpunkt('zugferd')).toBe('/api/dokument-generator/zugferd-pdf');
    });
});

describe('erzeugePdf', () => {
    it('schickt die Anfrage als JSON und liefert das PDF', async () => {
        const blob = pdf();
        const fetchMock = vi.fn(() => Promise.resolve({ ok: true, blob: () => Promise.resolve(blob) }));
        global.fetch = fetchMock as unknown as typeof fetch;

        await expect(erzeugePdf('/api/dokument-generator/pdf', { a: 1 })).resolves.toBe(blob);
        expect(fetchMock).toHaveBeenCalledWith('/api/dokument-generator/pdf', expect.objectContaining({
            method: 'POST',
            body: JSON.stringify({ a: 1 }),
        }));
    });

    it('wirft die lesbare Meldung des Servers', async () => {
        global.fetch = vi.fn(() => Promise.resolve({
            ok: false,
            json: () => Promise.resolve({ message: 'Firmenname fehlt' }),
        })) as unknown as typeof fetch;

        await expect(erzeugePdf('/x', {})).rejects.toThrow('Firmenname fehlt');
    });
});

describe('legePdfAb', () => {
    it('legt das PDF beim Dokument ab', async () => {
        const antwort = { ok: true };
        const fetchMock = vi.fn(() => Promise.resolve(antwort));
        global.fetch = fetchMock as unknown as typeof fetch;

        await expect(legePdfAb(7, pdf())).resolves.toBe(antwort);
        expect(fetchMock).toHaveBeenCalledWith('/api/ausgangs-dokumente/7/pdf-speichern', expect.objectContaining({
            method: 'POST',
            headers: { 'Content-Type': 'application/pdf' },
        }));
    });

    it('liefert null bei Netzfehler, statt den Versand abzubrechen', async () => {
        vi.spyOn(console, 'warn').mockImplementation(() => { });
        global.fetch = vi.fn(() => Promise.reject(new Error('offline'))) as unknown as typeof fetch;

        await expect(legePdfAb(7, pdf())).resolves.toBeNull();
        expect(console.warn).toHaveBeenCalled();
    });
});

describe('ladePdfHerunter', () => {
    it('löst einen Download mit Dateinamen aus und räumt auf', () => {
        global.URL.createObjectURL = vi.fn(() => 'blob:test');
        global.URL.revokeObjectURL = vi.fn();
        const klick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => { });

        ladePdfHerunter(pdf(), 'angebot_AG-1.pdf');

        expect(klick).toHaveBeenCalledTimes(1);
        const link = klick.mock.contexts[0] as HTMLAnchorElement;
        expect(link.download).toBe('angebot_AG-1.pdf');
        expect(document.body.contains(link)).toBe(false);
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:test');
    });
});

describe('druckePdf', () => {
    it('ersetzt das vorherige Druck-iframe durch ein neues', () => {
        global.URL.createObjectURL = vi.fn(() => 'blob:druck');
        druckePdf(pdf());
        const erstes = document.getElementById('print-iframe');
        druckePdf(pdf());
        const zweites = document.getElementById('print-iframe') as HTMLIFrameElement;

        expect(document.querySelectorAll('#print-iframe')).toHaveLength(1);
        expect(zweites).not.toBe(erstes);
        expect(zweites.src).toBe('blob:druck');
    });
});
