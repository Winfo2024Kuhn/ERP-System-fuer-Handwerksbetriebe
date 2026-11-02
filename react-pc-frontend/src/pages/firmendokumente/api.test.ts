import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    benenneOrdnerUm, dateiUrl, ladeDokumente, ladeFaellige, ladeHoch, ladeOrdner, legeOrdnerAn, loescheDokument, loescheOrdner,
    sucheDokumente, speichereDokument, verschiebeOrdner,
} from './api';

const ok = (body: unknown, status = 200) => new Response(body === undefined ? null : JSON.stringify(body), { status });

describe('Firmendokumente-API', () => {
    let abruf: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        abruf = vi.fn().mockImplementation(async () => ok({ eintraege: [], gesamt: 0, seite: 0, seitenGroesse: 50 }));
        vi.stubGlobal('fetch', abruf);
    });
    afterEach(() => vi.unstubAllGlobals());

    const aufruf = (i = 0) => abruf.mock.calls[i] as [string, RequestInit | undefined];
    const body = (i = 0) => JSON.parse(String(aufruf(i)[1]?.body));

    it('lädt Ordner und behandelt eine Nicht-Liste als leer', async () => {
        abruf.mockResolvedValueOnce(ok([{ id: 1, name: 'Prüfungen', parentId: null }])).mockResolvedValueOnce(ok({}));
        await expect(ladeOrdner()).resolves.toHaveLength(1);
        await expect(ladeOrdner()).resolves.toEqual([]);
        expect(aufruf()[0]).toBe('/api/firmendokumente/ordner');
    });

    it('schickt Ordner-Änderungen als JSON', async () => {
        await legeOrdnerAn('Tore', 1);
        await benenneOrdnerUm(2, 'Tore & Türen');
        await verschiebeOrdner(2, null);
        expect(aufruf(0)[0]).toBe('/api/firmendokumente/ordner');
        expect(aufruf(0)[1]?.method).toBe('POST');
        expect(body(0)).toEqual({ name: 'Tore', parentId: 1 });
        expect(aufruf(1)[0]).toBe('/api/firmendokumente/ordner/2');
        expect(aufruf(1)[1]?.method).toBe('PUT');
        expect(body(1)).toEqual({ name: 'Tore & Türen' });
        expect(aufruf(2)[0]).toBe('/api/firmendokumente/ordner/2/verschieben');
        expect(body(2)).toEqual({ parentId: null });
    });

    it('löscht Ordner und Dokumente', async () => {
        abruf.mockImplementation(async () => ok(undefined, 204));
        await loescheOrdner(3);
        await loescheDokument(7);
        expect(aufruf(0)).toEqual(['/api/firmendokumente/ordner/3', { method: 'DELETE' }]);
        expect(aufruf(1)).toEqual(['/api/firmendokumente/7', { method: 'DELETE' }]);
    });

    it('fragt Listen seitenweise ab und kodiert den Suchbegriff', async () => {
        const signal = new AbortController().signal;
        await ladeDokumente(4, 0, signal);
        await sucheDokumente('Tor & Tür', 2, signal);
        await ladeFaellige(1, signal);
        expect(aufruf(0)).toEqual(['/api/firmendokumente?ordnerId=4&page=0&size=50', { signal }]);
        expect(aufruf(1)[0]).toBe('/api/firmendokumente/suche?q=Tor%20%26%20T%C3%BCr&page=2&size=50');
        expect(aufruf(2)[0]).toBe('/api/firmendokumente/faellig?page=1&size=50');
    });

    it('lädt ein PDF als multipart hoch – Fällig am nur, wenn gesetzt', async () => {
        const datei = new File(['%PDF-1.7'], 'scan.pdf', { type: 'application/pdf' });
        await ladeHoch(datei, { name: 'Torprüfung', ordnerId: 2, faelligAm: '2027-10-31' });
        await ladeHoch(datei, { name: 'Ohne Datum', ordnerId: 2, faelligAm: null });
        const form = aufruf(0)[1]?.body as FormData;
        expect(aufruf(0)[0]).toBe('/api/firmendokumente');
        expect(form.get('datei')).toBeInstanceOf(File);
        expect(form.get('ordnerId')).toBe('2');
        expect(form.get('name')).toBe('Torprüfung');
        expect(form.get('faelligAm')).toBe('2027-10-31');
        expect((aufruf(1)[1]?.body as FormData).has('faelligAm')).toBe(false);
    });

    it('schickt beim Bearbeiten immer alle drei Felder', async () => {
        await speichereDokument(7, { name: 'Neu', ordnerId: 3, faelligAm: null });
        expect(aufruf()[0]).toBe('/api/firmendokumente/7');
        expect(aufruf()[1]?.method).toBe('PUT');
        expect(body()).toEqual({ name: 'Neu', ordnerId: 3, faelligAm: null });
    });

    it('reicht die Meldung des Servers durch', async () => {
        abruf.mockImplementation(async () => ok({ status: 400, message: 'Hier gibt es schon einen Ordner „Tore“.', fields: {} }, 400));
        await expect(legeOrdnerAn('Tore', 1)).rejects.toThrow('Hier gibt es schon einen Ordner „Tore“.');
    });

    it('baut die Datei-Adresse aus der ID', () => {
        expect(dateiUrl(7)).toBe('/api/firmendokumente/7/datei');
        expect(dateiUrl(7, true)).toBe('/api/firmendokumente/7/datei?download=true');
    });
});
