import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiAnfrage, apiJson, fehlerText, KEINE_VERBINDUNG, leseFehlermeldung } from './apiFehler';

const antwort = (body: string, status = 400) => new Response(body, { status });

describe('leseFehlermeldung', () => {
    it('nimmt das Feld error älterer Endpunkte', async () => {
        await expect(leseFehlermeldung(antwort('{"error":"Grund fehlt"}'), 'Ersatz')).resolves.toBe('Grund fehlt');
    });

    it('nimmt das Feld message des zentralen Fehler-Handlers', async () => {
        const res = antwort('{"status":403,"message":"Ihr Benutzer ist keinem Mitarbeiter zugeordnet."}', 403);
        await expect(leseFehlermeldung(res, 'Ersatz')).resolves.toBe('Ihr Benutzer ist keinem Mitarbeiter zugeordnet.');
    });

    it('bevorzugt error vor message', async () => {
        await expect(leseFehlermeldung(antwort('{"error":"A","message":"B"}'), 'Ersatz')).resolves.toBe('A');
    });

    it('übergeht den englischen Statustext aus Springs Standard-Fehlerantwort', async () => {
        const res = antwort(
            '{"timestamp":"2026-10-12T19:00:00.000+00:00","status":500,"error":"Internal Server Error","path":"/api/projekte/dokumente/7/bezahlt"}',
            500,
        );
        await expect(leseFehlermeldung(res, 'Ersatz')).resolves.toBe('Ersatz');
    });

    it('übergeht leere Meldungen und Nicht-Texte', async () => {
        await expect(leseFehlermeldung(antwort('{"error":"  ","message":42}'), 'Ersatz')).resolves.toBe('Ersatz');
    });

    it('liefert den Ersatztext bei JSON ohne Objekt', async () => {
        await expect(leseFehlermeldung(antwort('null'), 'Ersatz')).resolves.toBe('Ersatz');
    });

    it('liefert den Ersatztext, wenn der Body kein JSON ist', async () => {
        await expect(leseFehlermeldung(antwort('<html>500</html>', 500), 'Ersatz')).resolves.toBe('Ersatz');
    });
});

describe('apiAnfrage / apiJson', () => {
    afterEach(() => vi.unstubAllGlobals());

    it('liefert bei Erfolg die Antwort bzw. das JSON', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{"id":7}', { status: 200 })));
        await expect(apiJson('/api/x', undefined, 'Ersatz')).resolves.toEqual({ id: 7 });
        expect(fetch).toHaveBeenCalledWith('/api/x', undefined);
    });

    it('wirft die Meldung des Servers', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(antwort('{"status":400,"message":"Bitte einen Namen eingeben."}')));
        await expect(apiAnfrage('/api/x', { method: 'POST' }, 'Ersatz')).rejects.toThrow('Bitte einen Namen eingeben.');
    });

    it('meldet fehlende Verbindung', async () => {
        vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
        await expect(apiAnfrage('/api/x', undefined, 'Ersatz')).rejects.toThrow(KEINE_VERBINDUNG);
    });
});

describe('fehlerText', () => {
    it('nimmt die Meldung eines Error, sonst den Ersatztext', () => {
        expect(fehlerText(new Error('Ordner fehlt.'), 'Ersatz')).toBe('Ordner fehlt.');
        expect(fehlerText('kaputt', 'Ersatz')).toBe('Ersatz');
    });
});
