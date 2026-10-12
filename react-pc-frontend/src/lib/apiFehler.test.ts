import { describe, expect, it } from 'vitest';
import { leseFehlermeldung } from './apiFehler';

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
