import { afterEach, describe, expect, it, vi } from 'vitest';
import { fehlermeldungAusAntwort, pruefeFirmendatenFuerERechnung } from './eRechnungPruefung';

const json = (body: unknown, status: number) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

describe('fehlermeldungAusAntwort', () => {
    it('übernimmt die Meldung des Servers', async () => {
        const meldung = 'Für die E-Rechnung fehlen Angaben zu Ihrem Betrieb: Straße, Ort.';
        expect(await fehlermeldungAusAntwort(json({ status: 422, message: meldung }), 'Ersatz')).toBe(meldung);
    });

    it('nimmt den Ersatztext, wenn keine Meldung da ist', async () => {
        expect(await fehlermeldungAusAntwort(json({ status: 500 }), 'Ersatz')).toBe('Ersatz');
        expect(await fehlermeldungAusAntwort(json({ message: '  ' }), 'Ersatz')).toBe('Ersatz');
        expect(await fehlermeldungAusAntwort(json({ message: 42 }), 'Ersatz')).toBe('Ersatz');
        expect(await fehlermeldungAusAntwort(json(null, 500), 'Ersatz')).toBe('Ersatz');
    });

    it('nimmt den Ersatztext, wenn der Körper kein JSON ist', async () => {
        expect(await fehlermeldungAusAntwort(new Response('<html>', { status: 500 }), 'Ersatz')).toBe('Ersatz');
    });
});

describe('pruefeFirmendatenFuerERechnung', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('geht durch, wenn der Server 204 liefert', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
        vi.stubGlobal('fetch', fetchMock);
        await expect(pruefeFirmendatenFuerERechnung()).resolves.toBeUndefined();
        expect(fetchMock).toHaveBeenCalledWith('/api/dokument-generator/zugferd-pruefung');
    });

    it('wirft mit der Meldung des Servers bei fehlenden Firmendaten', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ status: 422, message: 'Es fehlt der Ort.' }, 422)));
        await expect(pruefeFirmendatenFuerERechnung()).rejects.toThrow('Es fehlt der Ort.');
    });

    it('wirft mit Ersatztext, wenn der Server nichts Lesbares liefert', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('boom', { status: 500 })));
        await expect(pruefeFirmendatenFuerERechnung()).rejects.toThrow(/Firmendaten reichen/);
    });
});
