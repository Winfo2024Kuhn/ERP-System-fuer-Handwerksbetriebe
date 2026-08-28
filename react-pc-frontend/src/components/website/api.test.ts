import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
    WebsiteApiFehler,
    ladeBeitraege,
    ladeAnalyticsAktuell,
    ladeBildHoch,
} from './api';

let fetchMock: ReturnType<typeof vi.fn>;

function antwort(status: number, body: unknown) {
    return {
        ok: status >= 200 && status < 300,
        status,
        json: () => Promise.resolve(body),
    };
}

describe('website/api', () => {
    beforeEach(() => {
        fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('liest die Beitragsliste', async () => {
        fetchMock.mockResolvedValue(antwort(200, [{ id: 1, title: 'Neues Tor' }]));

        const beitraege = await ladeBeitraege();

        expect(fetchMock).toHaveBeenCalledWith('/api/beitraege', expect.anything());
        expect(beitraege).toHaveLength(1);
        expect(beitraege[0].title).toBe('Neues Tor');
    });

    it('meldet einen Serverfehler als WebsiteApiFehler mit Status', async () => {
        fetchMock.mockResolvedValue(antwort(502, { message: 'Website nicht erreichbar.' }));

        await expect(ladeBeitraege()).rejects.toBeInstanceOf(WebsiteApiFehler);
        await expect(ladeBeitraege()).rejects.toMatchObject({ status: 502 });
    });

    it('macht aus einem Netzwerkabbruch ebenfalls einen WebsiteApiFehler', async () => {
        fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

        await expect(ladeBeitraege()).rejects.toBeInstanceOf(WebsiteApiFehler);
    });

    it('liefert null, wenn noch kein Schnappschuss da ist (HTTP 204)', async () => {
        fetchMock.mockResolvedValue({
            ok: true,
            status: 204,
            json: () => Promise.reject(new Error('kein Body')),
        });

        await expect(ladeAnalyticsAktuell()).resolves.toBeNull();
    });

    it('schickt das Bild als FormData im Feld bild', async () => {
        fetchMock.mockResolvedValue(antwort(201, { id: 5, images: [] }));

        await ladeBildHoch(5, new Blob(['x'], { type: 'image/jpeg' }), 'baustelle.jpg');

        const [url, optionen] = fetchMock.mock.calls[0];
        expect(url).toBe('/api/beitraege/5/bilder');
        expect(optionen.method).toBe('POST');
        const daten = optionen.body as FormData;
        expect(daten.get('bild')).toBeInstanceOf(Blob);
        // Kein Content-Type von Hand setzen, sonst fehlt die multipart-boundary.
        expect(optionen.headers).toBeUndefined();
    });
});
