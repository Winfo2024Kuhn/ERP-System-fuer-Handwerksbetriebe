import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
    WebsiteApiFehler,
    ladeBeitraege,
    ladeAnalyticsAktuell,
    ladeBildHoch,
    ladeProjektBilder,
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

/**
 * ladeProjektBilder ist die einzige Funktion dieser Datei mit echter Logik.
 * Stimmt das Zusammenfuehren der zwei Quellen nicht, faellt das erst im
 * Assistenten auf und sieht dort wie ein Fehler der Oberflaeche aus.
 * DSGVO: nur Dummy-Daten.
 */
describe('ladeProjektBilder', () => {
    // Eigene Aufraeumung: das afterEach des Blocks darueber gilt hier nicht.
    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    const notizen = [
        {
            notiz: 'Tor am Montag montiert',
            erstelltAm: '2026-08-20T10:00:00',
            bilder: [{
                id: 1,
                originalDateiname: 'tor.jpg',
                url: '/api/dokumente/tor.jpg',
                thumbnailUrl: '/api/dokumente/tor.jpg/thumbnail',
                erstelltAm: '2026-08-20T10:00:00',
            }],
        },
        // Notiz ohne Bilder darf nicht stoeren.
        { notiz: 'Nur Text', erstelltAm: '2026-08-21T08:00:00', bilder: [] },
    ];

    const dokumente = [
        {
            id: 7, originalDateiname: 'plan.pdf', dateityp: 'application/pdf',
            url: '/api/dokumente/plan.pdf', thumbnailUrl: '',
            dokumentGruppe: 'PLANUNGSDOKUMENTE', uploadDatum: '2026-08-01',
        },
        {
            id: 8, originalDateiname: 'halle.jpg', dateityp: 'image/jpeg',
            url: '/api/dokumente/halle.jpg', thumbnailUrl: '/api/dokumente/halle.jpg/thumbnail',
            dokumentGruppe: 'BILDER', uploadDatum: '2026-08-02',
        },
    ];

    function serverMitBeidenQuellen() {
        return vi.fn((url: string) => Promise.resolve({
            ok: true,
            status: 200,
            json: () => Promise.resolve(url.includes('/notizen') ? notizen : dokumente),
        }));
    }

    it('fuehrt Bautagebuch und Projektdokumente zu einer Liste zusammen', async () => {
        vi.stubGlobal('fetch', serverMitBeidenQuellen());

        const bilder = await ladeProjektBilder(1);

        expect(bilder).toHaveLength(2);
        // Bautagebuch zuerst, dort liegen die Baustellenfotos.
        expect(bilder[0].quelle).toBe('bautagebuch');
        expect(bilder[1].quelle).toBe('dokument');
    });

    it('vergibt eindeutige Schluessel je Quelle', async () => {
        vi.stubGlobal('fetch', serverMitBeidenQuellen());

        const bilder = await ladeProjektBilder(1);

        expect(bilder[0].schluessel).toBe('notiz-1');
        expect(bilder[1].schluessel).toBe('dokument-8');
    });

    it('nimmt aus den Dokumenten nur die Gruppe BILDER auf', async () => {
        vi.stubGlobal('fetch', serverMitBeidenQuellen());

        const bilder = await ladeProjektBilder(1);

        expect(bilder.map(b => b.originalDateiname)).not.toContain('plan.pdf');
    });

    it('haengt den Anfang des Notiztexts als Hinweis an', async () => {
        vi.stubGlobal('fetch', serverMitBeidenQuellen());

        const bilder = await ladeProjektBilder(1);

        expect(bilder[0].hinweis).toBe('Tor am Montag montiert');
        // Projektdokumente haben keinen Notiztext.
        expect(bilder[1].hinweis).toBeNull();
    });

    it('kuerzt einen langen Notiztext auf 120 Zeichen', async () => {
        const langeNotiz = [{
            notiz: 'a'.repeat(200),
            erstelltAm: '2026-08-20T10:00:00',
            bilder: [{
                id: 1, originalDateiname: 'x.jpg',
                url: '/u', thumbnailUrl: '/t', erstelltAm: '2026-08-20T10:00:00',
            }],
        }];
        vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(url.includes('/notizen') ? langeNotiz : []),
        })));

        const bilder = await ladeProjektBilder(1);

        expect(bilder[0].hinweis).toHaveLength(120);
    });

    it('kommt mit einer Notiz ohne bilder-Feld zurecht', async () => {
        const ohneFeld = [{ notiz: 'Nur Text', erstelltAm: '2026-08-20T10:00:00' }];
        vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(url.includes('/notizen') ? ohneFeld : []),
        })));

        await expect(ladeProjektBilder(1)).resolves.toEqual([]);
    });

    it('faellt beim Datum auf den Zeitstempel der Notiz zurueck', async () => {
        const ohneBilddatum = [{
            notiz: 'Ohne Bilddatum',
            erstelltAm: '2026-08-20T10:00:00',
            bilder: [{
                id: 2, originalDateiname: 'y.jpg',
                url: '/u', thumbnailUrl: '/t', erstelltAm: null,
            }],
        }];
        vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve({
            ok: true, status: 200,
            json: () => Promise.resolve(url.includes('/notizen') ? ohneBilddatum : []),
        })));

        const bilder = await ladeProjektBilder(1);

        expect(bilder[0].datum).toBe('2026-08-20T10:00:00');
    });

    it('meldet einen Fehlschlag einer der beiden Quellen weiter', async () => {
        vi.stubGlobal('fetch', vi.fn((url: string) => Promise.resolve(
            url.includes('/notizen')
                ? { ok: true, status: 200, json: () => Promise.resolve([]) }
                : { ok: false, status: 500, json: () => Promise.resolve({ message: 'kaputt' }) },
        )));

        await expect(ladeProjektBilder(1)).rejects.toBeInstanceOf(WebsiteApiFehler);
    });
});
