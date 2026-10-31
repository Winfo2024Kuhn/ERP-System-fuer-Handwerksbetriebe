import { afterEach, describe, expect, it, vi } from 'vitest';
import { TextbausteinFehler, fehlerMeldung, ladeTextbausteine, loescheTextbaustein, speichereTextbaustein } from './textbausteinApi';
import { testBaustein } from './__tests__/testdaten';

function antwort(daten: unknown, status = 200) {
    return Promise.resolve({ ok: status < 400, status, json: () => Promise.resolve(daten) } as Response);
}

const DATEN = { name: 'Gruß', typ: 'FREITEXT', beschreibung: null, html: '<p>Hallo</p>', placeholders: [], dokumenttypen: [], sortOrder: null };

afterEach(() => vi.unstubAllGlobals());

describe('textbausteinApi', () => {
    it('legt mit POST an und ändert mit PUT', async () => {
        const fetchMock = vi.fn(() => antwort(testBaustein({ id: 5, name: 'Gruß' })));
        vi.stubGlobal('fetch', fetchMock);
        await speichereTextbaustein(null, DATEN);
        await speichereTextbaustein(5, DATEN);
        expect(fetchMock.mock.calls.map(([url, init]) => [url, (init as RequestInit).method])).toEqual([
            ['/api/textbausteine', 'POST'],
            ['/api/textbausteine/5', 'PUT'],
        ]);
        expect(JSON.parse((fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].body as string)).toEqual(DATEN);
    });

    it('nennt bei 400 die Feldmeldungen und die betroffenen Felder', async () => {
        vi.stubGlobal('fetch', vi.fn(() => antwort({
            status: 400,
            message: 'Die Eingaben sind unvollstaendig oder ungueltig.',
            fields: [{ field: 'name', label: 'Name', message: 'Bitte einen Namen angeben.' }],
        }, 400)));
        const fehler = await speichereTextbaustein(null, DATEN).catch((e: unknown) => e);
        expect(fehler).toBeInstanceOf(TextbausteinFehler);
        expect((fehler as TextbausteinFehler).message).toBe('Bitte einen Namen angeben.');
        expect((fehler as TextbausteinFehler).felder).toEqual(['name']);
    });

    it('nimmt sonst die allgemeine Meldung oder den Rückfall', async () => {
        vi.stubGlobal('fetch', vi.fn(() => antwort({ status: 404, message: 'Diesen Textbaustein gibt es nicht mehr.' }, 404)));
        await expect(speichereTextbaustein(3, DATEN)).rejects.toThrow('Diesen Textbaustein gibt es nicht mehr.');
        vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ ok: false, status: 500, json: () => Promise.reject(new Error('kein JSON')) })));
        await expect(loescheTextbaustein(3)).rejects.toThrow('Der Textbaustein konnte nicht gelöscht werden.');
    });

    it('ergänzt fehlende Listen und übersetzt Netzfehler in eine verständliche Meldung', async () => {
        vi.stubGlobal('fetch', vi.fn(() => antwort([{ id: 1, name: 'Alt', html: null }])));
        const [alt] = await ladeTextbausteine();
        expect(alt.dokumenttypen).toEqual([]);
        expect(alt.placeholders).toEqual([]);
        expect(fehlerMeldung(new TypeError('Failed to fetch'), 'Rückfall')).toBe('Rückfall');
    });
});
