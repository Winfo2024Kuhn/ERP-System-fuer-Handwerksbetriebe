/**
 * IDS-Aufrufe: richtige Adressen, Methoden und Fehlertexte. Nur Dummy-Daten.
 */
import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import {
    IdsApiFehler,
    entferneIdsZugang,
    ladeIdsEinstellungen,
    ladeIdsVerfuegbar,
    ladeIdsVorgang,
    speichereIdsGrundschalter,
    speichereIdsZugang,
    starteIdsVorgang,
    uebernimmIdsArtikelstamm,
} from './api';
import { useIdsVerfuegbar } from './useIdsVerfuegbar';

const antwort = (body: unknown, status = 200) =>
    new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

function stubFetch(...antworten: Response[]) {
    const mock = vi.fn();
    antworten.forEach((a) => mock.mockResolvedValueOnce(a));
    vi.stubGlobal('fetch', mock);
    return mock;
}

afterEach(() => vi.unstubAllGlobals());

describe('IDS-API', () => {
    it('ruft die Einrichtungs-Endpunkte richtig auf', async () => {
        const daten = { aktiv: true, verschluesselungEingerichtet: true, zugaenge: [] };
        const fetchMock = stubFetch(antwort(daten), antwort(daten), antwort({ lieferantId: 7 }), new Response(null, { status: 204 }));
        await expect(ladeIdsEinstellungen()).resolves.toEqual(daten);
        await speichereIdsGrundschalter(false);
        await speichereIdsZugang(7, { aktiv: true, protokoll: 'IDS_CONNECT_2_5', shopUrl: 'https://shop.example.de', kundennummer: null, benutzername: null, passwort: null });
        await entferneIdsZugang(7);
        expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method ?? 'GET'])).toEqual([
            ['/api/ids/einstellungen', 'GET'],
            ['/api/ids/einstellungen', 'PUT'],
            ['/api/ids/einstellungen/zugaenge/7', 'PUT'],
            ['/api/ids/einstellungen/zugaenge/7', 'DELETE'],
        ]);
        expect(JSON.parse(fetchMock.mock.calls[1][1].body)).toEqual({ aktiv: false });
    });

    it('ruft die Benutzungs-Endpunkte richtig auf', async () => {
        const fetchMock = stubFetch(
            antwort({ vorgangId: 1, formular: { action: 'https://x', enctype: '', felder: {} } }, 201),
            antwort({ vorgangId: 1 }),
            antwort({ aktualisiert: 1, angelegt: 0, unveraendert: 0, artikelIds: {} }),
        );
        await starteIdsVorgang(7);
        await ladeIdsVorgang(1);
        await uebernimmIdsArtikelstamm(1, { aktualisieren: [{ index: 0, einkaufspreis: 4.2 }], neuAnlegen: [] });
        expect(fetchMock.mock.calls.map(([url, init]) => [url, init?.method ?? 'GET'])).toEqual([
            ['/api/ids/vorgaenge', 'POST'],
            ['/api/ids/vorgaenge/1', 'GET'],
            ['/api/ids/vorgaenge/1/artikelstamm', 'POST'],
        ]);
        expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ lieferantId: 7 });
    });

    it('gibt die Meldung des Servers samt Status weiter', async () => {
        stubFetch(antwort({ status: 400, message: 'Die Shop-Adresse muss mit https:// beginnen.' }, 400));
        const fehler = await speichereIdsZugang(7, { aktiv: true, protokoll: 'IDS_CONNECT_2_5', shopUrl: 'http://x', kundennummer: null, benutzername: null, passwort: null })
            .catch((e: unknown) => e);
        expect(fehler).toBeInstanceOf(IdsApiFehler);
        expect((fehler as IdsApiFehler).status).toBe(400);
        expect((fehler as IdsApiFehler).message).toBe('Die Shop-Adresse muss mit https:// beginnen.');
    });

    it('nimmt bei unbrauchbarer Verfügbarkeits-Antwort „nichts verfügbar“', async () => {
        stubFetch(antwort([]), antwort({ aktiv: false, lieferanten: [{ lieferantId: 1, lieferantName: 'X' }] }), antwort({ aktiv: true, lieferanten: null }), antwort(null),
            antwort({ aktiv: true, lieferanten: [{ lieferantId: 1, lieferantName: 'Muster GmbH' }] }));
        const leer = { aktiv: false, lieferanten: [] };
        await expect(ladeIdsVerfuegbar()).resolves.toEqual(leer);
        await expect(ladeIdsVerfuegbar()).resolves.toEqual(leer);
        await expect(ladeIdsVerfuegbar()).resolves.toEqual(leer);
        await expect(ladeIdsVerfuegbar()).resolves.toEqual(leer);
        await expect(ladeIdsVerfuegbar()).resolves.toEqual({ aktiv: true, lieferanten: [{ lieferantId: 1, lieferantName: 'Muster GmbH' }] });
    });
});

describe('useIdsVerfuegbar', () => {
    it('liefert die Shops und bleibt bei Fehlern still leer', async () => {
        stubFetch(antwort({ aktiv: true, lieferanten: [{ lieferantId: 1, lieferantName: 'Muster GmbH' }] }));
        const gut = renderHook(() => useIdsVerfuegbar());
        await waitFor(() => expect(gut.result.current).toHaveLength(1));

        stubFetch(antwort({ status: 500, message: 'kaputt' }, 500));
        const schlecht = renderHook(() => useIdsVerfuegbar());
        await waitFor(() => expect(vi.mocked(fetch)).toHaveBeenCalled());
        expect(schlecht.result.current).toEqual([]);
    });
});
