import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    beschreibeStand, bereinigeRechtstextHtml, ohneLeereAbsaetzeAmEnde, formatiereDatum, formatiereZeitpunkt, ladeFassung, ladeFassungen,
    ladeRechtstext, ladeRechtstexte, ladeVorschau, RechtstextFehler, setzeAufStandard, speichereRechtstext,
} from './rechtstextApi';

function antwort(body: unknown, status = 200): Response {
    return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

describe('rechtstextApi – Anzeige', () => {
    it('formatiert Stand und Zeitpunkt ohne Zeitzonen-Verschiebung', () => {
        expect(formatiereDatum('2026-10-13')).toBe('13.10.2026');
        expect(formatiereDatum('2026-01-01T00:30:00')).toBe('01.01.2026');
        expect(formatiereDatum(null)).toBe('');
        expect(formatiereDatum('kein Datum')).toBe('');
        expect(formatiereZeitpunkt('2026-10-13T14:05:00')).toBe('13.10.2026, 14:05 Uhr');
        expect(formatiereZeitpunkt('2026-10-13')).toBe('13.10.2026');
        expect(formatiereZeitpunkt(undefined)).toBe('');
    });

    it('beschreibt Standardtext (nie gespeichert) und gespeicherte Fassung', () => {
        expect(beschreibeStand({ fassung: 0, stand: '2026-10-13' }))
            .toBe('Stand 13.10.2026 · Standardtext nach gesetzlichem Muster');
        expect(beschreibeStand({ fassung: 3, stand: '2026-10-13' }))
            .toBe('Stand 13.10.2026 · Fassung 3');
    });

    it('lässt nur die erlaubten Tags ohne Attribute durch', () => {
        const roh = '<h3 class="x">Titel</h3><p style="color:red">Text <strong>fett</strong> <a href="javascript:alert(1)">Link</a></p>'
            + '<img src=x onerror="alert(1)"><script>alert(1)</script><ul><li>eins</li></ul><h1>gross</h1>';
        const sauber = bereinigeRechtstextHtml(roh);
        expect(sauber).toBe('<h3>Titel</h3><p>Text <strong>fett</strong> Link</p><ul><li>eins</li></ul>gross');
        expect(sauber).not.toMatch(/img|script|onerror|href|style|class/);
        expect(bereinigeRechtstextHtml(null)).toBe('');
    });
});

describe('ohneLeereAbsaetzeAmEnde', () => {
    it('entfernt nur leere Absätze ganz am Ende', () => {
        expect(ohneLeereAbsaetzeAmEnde('<h3>A</h3><p></p><p></p>')).toBe('<h3>A</h3>');
        expect(ohneLeereAbsaetzeAmEnde('<p></p><p>B</p>')).toBe('<p></p><p>B</p>');
        expect(ohneLeereAbsaetzeAmEnde('<p></p>')).toBe('');
    });
});

describe('rechtstextApi – Aufrufe', () => {
    const fetchMock = vi.fn();
    beforeEach(() => { vi.stubGlobal('fetch', fetchMock); fetchMock.mockReset(); });
    afterEach(() => vi.unstubAllGlobals());

    it('ruft die vereinbarten Adressen mit Methode und Körper auf', async () => {
        fetchMock.mockImplementation(() => Promise.resolve(antwort({})));

        await ladeRechtstexte();
        await ladeRechtstext('WIDERRUFSBELEHRUNG');
        await speichereRechtstext('WIDERRUFSBELEHRUNG', '<p>Neu</p>');
        await setzeAufStandard('WIDERRUFSBELEHRUNG');
        await ladeFassungen('WIDERRUFSBELEHRUNG');
        await ladeFassung('WIDERRUFSBELEHRUNG', 2);

        const aufrufe = fetchMock.mock.calls.map(([url, init]) => [url, init?.method ?? 'GET', init?.body ?? null]);
        expect(aufrufe).toEqual([
            ['/api/formulare/rechtstexte', 'GET', null],
            ['/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG', 'GET', null],
            ['/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG', 'PUT', JSON.stringify({ html: '<p>Neu</p>' })],
            ['/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/standard', 'POST', null],
            ['/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/fassungen', 'GET', null],
            ['/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/fassungen/2', 'GET', null],
        ]);
        expect(fetchMock.mock.calls[2][1].headers).toEqual({ 'Content-Type': 'application/json' });
    });

    it('holt die Vorschau als PDF und schickt null für den gespeicherten Stand', async () => {
        fetchMock.mockResolvedValue(new Response('%PDF-1.4', { headers: { 'Content-Type': 'application/pdf' } }));

        const pdf = await ladeVorschau('WIDERRUFSBELEHRUNG', null);

        expect(pdf.size).toBeGreaterThan(0);
        expect(fetchMock).toHaveBeenCalledWith('/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG/vorschau', expect.objectContaining({
            method: 'POST', body: JSON.stringify({ html: null }),
        }));
    });

    it('wirft die Meldung des Backends bei 400', async () => {
        fetchMock.mockResolvedValue(antwort({ message: 'Der Text darf nicht leer sein.' }, 400));

        const fehler = await speichereRechtstext('WIDERRUFSBELEHRUNG', '').catch(e => e);

        expect(fehler).toBeInstanceOf(RechtstextFehler);
        expect(fehler.message).toBe('Der Text darf nicht leer sein.');
        expect(fehler.status).toBe(400);
    });

    it('nimmt einen verständlichen Ersatztext ohne Meldung und bei Netzwerkfehler', async () => {
        fetchMock.mockResolvedValueOnce(new Response('kaputt', { status: 500 }));
        await expect(ladeRechtstexte()).rejects.toThrow('Die festen Texte konnten nicht geladen werden.');

        fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
        const fehler = await ladeRechtstexte().catch(e => e);
        expect(fehler.message).toBe('Keine Verbindung zum Server. Bitte später erneut versuchen.');
        expect(fehler.status).toBe(0);
    });

    it('kodiert die Art in der Adresse', async () => {
        fetchMock.mockResolvedValue(antwort({}));
        await ladeRechtstext('a/b?c');
        expect(fetchMock.mock.calls[0][0]).toBe('/api/formulare/rechtstexte/a%2Fb%3Fc');
    });
});
