import { afterEach, describe, expect, it, vi } from 'vitest';
import { DATEV_KONFLIKT, EinstellungenFehler, zeiterfassungApi } from './zeiterfassungApi';

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

afterEach(() => vi.unstubAllGlobals());

describe('zeiterfassungApi', () => {
    it('liest und schreibt den Start der Zeiterfassung unter /api/settings/zeiterfassung', async () => {
        const fetchMock = vi.fn(async () => json({ eingefuehrtAm: null }));
        vi.stubGlobal('fetch', fetchMock);
        await expect(zeiterfassungApi.ladeStart()).resolves.toEqual({ eingefuehrtAm: null });
        await zeiterfassungApi.speichereStart(null);
        expect(fetchMock).toHaveBeenLastCalledWith('/api/settings/zeiterfassung', {
            method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: '{"eingefuehrtAm":null}',
        });
    });

    it('schreibt DATEV nur noch unter /api/settings/datev', async () => {
        const fetchMock = vi.fn(async () => json({ version: 1 }));
        vi.stubGlobal('fetch', fetchMock);
        await zeiterfassungApi.ladeDatev();
        await zeiterfassungApi.speichereDatev({ version: 1, ziel: 'LODAS', beraterNr: '', mandantenNr: '', stundenart: 'SOLL', zuordnungen: [], personalnummern: [] });
        expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(['/api/settings/datev', '/api/settings/datev']);
    });

    it('bildet Namen wie im Monatsabschluss und stellt Deaktivierte ans Ende', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json([
            { id: 3, vorname: 'Moritz', nachname: 'Muster', aktiv: false },
            { id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true },
            { id: 2, vorname: 'Erika', nachname: 'Mustermann', aktiv: null },
        ])));
        await expect(zeiterfassungApi.ladeMitarbeiter()).resolves.toEqual([
            { id: 1, name: 'Max Mustermann', aktiv: true },
            { id: 2, name: 'Erika Mustermann', aktiv: true },
            { id: 3, name: 'Moritz Muster', aktiv: false },
        ]);
    });

    it('nimmt die Meldung des Servers und fällt je Status auf eigene Texte zurück', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => json({ message: 'Das Datum darf nicht in der Zukunft liegen.' }, 400)));
        await expect(zeiterfassungApi.speichereStart('2999-01-01')).rejects.toMatchObject({ message: 'Das Datum darf nicht in der Zukunft liegen.', status: 400 });

        vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 409 })));
        await expect(zeiterfassungApi.speichereDatev({} as never)).rejects.toMatchObject({ message: DATEV_KONFLIKT, status: 409 });

        vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 403 })));
        await expect(zeiterfassungApi.ladeDatev()).rejects.toThrow('Diese Einstellungen dürfen nur Admins ändern.');

        vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 500 })));
        await expect(zeiterfassungApi.ladeStart()).rejects.toThrow('Der Start der Zeiterfassung konnte nicht geladen werden.');
    });

    it('meldet einen Verbindungsfehler verständlich', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch'); }));
        const fehler = await zeiterfassungApi.ladeMitarbeiter().catch((err: unknown) => err);
        expect(fehler).toBeInstanceOf(EinstellungenFehler);
        expect(fehler).toMatchObject({ message: 'Verbindung zum Server fehlgeschlagen.', status: 0 });
    });
});
