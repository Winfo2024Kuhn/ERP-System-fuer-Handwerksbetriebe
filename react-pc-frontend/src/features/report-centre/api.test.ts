import { afterEach, describe, expect, it, vi } from 'vitest';
import { KEINE_VERBINDUNG } from '../../lib/apiFehler';
import {
    ladeBerichtMitarbeiter, ladePdf, ladeSteuerberaterVorschau, mitarbeiterPdfUrl, NICHT_ANGEMELDET, RECHT_FEHLT, steuerberaterPdfUrl,
} from './api';

function antwort(status: number, inhalt: unknown, kopf: Record<string, string> = {}) {
    return {
        ok: status >= 200 && status < 300,
        status,
        headers: new Headers(kopf),
        json: () => Promise.resolve(inhalt),
        blob: () => Promise.resolve(new Blob(['%PDF'], { type: 'application/pdf' })),
    };
}

function fetchLiefert(...antworten: unknown[]) {
    const mock = vi.fn();
    antworten.forEach(a => mock.mockResolvedValueOnce(a));
    global.fetch = mock as unknown as typeof fetch;
    return mock;
}

afterEach(() => vi.restoreAllMocks());

describe('Adressen', () => {
    it('baut die Steuerberater-PDF-Adresse', () => {
        const url = new URL(steuerberaterPdfUrl({ jahr: 2026, monat: 10, mitarbeiterIds: [1, 2], mitResturlaub: true, mitStundenkonto: false, stundenart: 'IST' }), 'http://x');
        expect(url.pathname).toBe('/api/zeitverwaltung/berichte/steuerberater/pdf');
        expect(Object.fromEntries(url.searchParams)).toEqual({
            jahr: '2026', monat: '10', mitarbeiterIds: '1,2', mitResturlaub: 'true', mitStundenkonto: 'false', stundenart: 'IST',
        });
        expect(url.searchParams.get('stundenart')).toBe('IST');
    });
    it('lässt leere Mitarbeiterliste beim Steuerberater weg', () => {
        const url = new URL(steuerberaterPdfUrl({ jahr: 2026, monat: 10, mitarbeiterIds: [], mitResturlaub: true, mitStundenkonto: true, stundenart: 'SOLL' }), 'http://x');
        expect(url.searchParams.has('mitarbeiterIds')).toBe(false);
        expect(url.searchParams.get('stundenart')).toBe('SOLL');
    });
    it('baut die Mitarbeiter-PDF-Adresse', () => {
        const url = new URL(mitarbeiterPdfUrl({ mitarbeiterIds: [1], von: '2026-10-01', bis: '2026-10-31', mitTagesliste: true, mitUnterschrift: false }), 'http://x');
        expect(url.pathname).toBe('/api/zeitverwaltung/berichte/mitarbeiter/pdf');
        expect(Object.fromEntries(url.searchParams)).toEqual({
            mitarbeiterIds: '1', von: '2026-10-01', bis: '2026-10-31', mitTagesliste: 'true', mitUnterschrift: 'false',
        });
    });
});

describe('ladeBerichtMitarbeiter', () => {
    it('lässt Geschäftsführer und Menschen ohne Zeitkonto weg', async () => {
        fetchLiefert(antwort(200, [
            { mitarbeiterId: 1, mitarbeiterName: 'Max Mustermann', fuehrtZeitkonto: true, aktuell: {}, istGeschaeftsfuehrer: false },
            { mitarbeiterId: 2, mitarbeiterName: 'Erika Musterfrau', fuehrtZeitkonto: true, aktuell: {}, istGeschaeftsfuehrer: true },
            { mitarbeiterId: 3, mitarbeiterName: 'Ohne Konto', fuehrtZeitkonto: false, aktuell: {}, istGeschaeftsfuehrer: false },
            { mitarbeiterId: 4, mitarbeiterName: 'Nicht aktuell', fuehrtZeitkonto: true, aktuell: null, istGeschaeftsfuehrer: false },
        ]));
        expect(await ladeBerichtMitarbeiter()).toEqual([{ id: 1, name: 'Max Mustermann' }]);
    });
    it('liefert eine leere Liste bei unerwarteter Antwort', async () => {
        fetchLiefert(antwort(200, { kaputt: true }));
        expect(await ladeBerichtMitarbeiter()).toEqual([]);
    });
});

describe('Fehler', () => {
    it('sagt bei 403, dass das Recht fehlt', async () => {
        fetchLiefert(antwort(403, { status: 403, message: 'Forbidden' }));
        await expect(ladeSteuerberaterVorschau({ jahr: 2026, monat: 10, mitarbeiterIds: [] })).rejects.toThrow(RECHT_FEHLT);
    });
    it('sagt bei 401, dass man neu anmelden muss', async () => {
        fetchLiefert(antwort(401, {}));
        await expect(ladeSteuerberaterVorschau({ jahr: 2026, monat: 10, mitarbeiterIds: [] })).rejects.toThrow(NICHT_ANGEMELDET);
    });
    it('zeigt die Meldung des Servers', async () => {
        fetchLiefert(antwort(400, { status: 400, message: 'Der Zeitraum darf höchstens 366 Tage lang sein.', fields: {} }));
        await expect(ladePdf('/x', 'a.pdf')).rejects.toThrow('Der Zeitraum darf höchstens 366 Tage lang sein.');
    });
    it('meldet fehlende Verbindung', async () => {
        global.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch')) as unknown as typeof fetch;
        await expect(ladePdf('/x', 'a.pdf')).rejects.toThrow(KEINE_VERBINDUNG);
    });
    it('reicht einen Abbruch unverändert weiter', async () => {
        const abbruch = new DOMException('abgebrochen', 'AbortError');
        global.fetch = vi.fn().mockRejectedValue(abbruch) as unknown as typeof fetch;
        await expect(ladePdf('/x', 'a.pdf')).rejects.toBe(abbruch);
    });
});

describe('ladePdf und Vorschau', () => {
    it('liest den Dateinamen aus der Antwort', async () => {
        fetchLiefert(antwort(200, null, { 'Content-Disposition': 'attachment; filename="Stundenaufstellung_2026-10.pdf"' }));
        const pdf = await ladePdf('/x', 'Ersatz.pdf');
        expect(pdf.dateiname).toBe('Stundenaufstellung_2026-10.pdf');
        expect(pdf.blob.size).toBeGreaterThan(0);
    });
    it('nimmt sonst den Ersatznamen', async () => {
        fetchLiefert(antwort(200, null));
        expect((await ladePdf('/x', 'Ersatz.pdf')).dateiname).toBe('Ersatz.pdf');
    });
    it('fragt die Vorschau mit Monat und Mitarbeitern ab', async () => {
        const mock = fetchLiefert(antwort(200, { jahr: 2026, monat: 10, zeilen: [] }));
        await ladeSteuerberaterVorschau({ jahr: 2026, monat: 10, mitarbeiterIds: [1, 2] });
        // Ohne stundenart: Der Server nimmt die Admin-Einstellung.
        expect(mock.mock.calls[0][0]).toBe('/api/zeitverwaltung/berichte/steuerberater?jahr=2026&monat=10&mitarbeiterIds=1%2C2');
    });
});
