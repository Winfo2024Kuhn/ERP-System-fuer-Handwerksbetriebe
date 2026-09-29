import { vi } from 'vitest';
import type { KontaktKurz, Sprachnachricht, TelefonAnruf, TelefonStatus } from './types';

/**
 * Dummy-Daten und ein Fetch-Stub für die Telefon-Tests. Nur erfundene
 * Namen und Nummern (DSGVO).
 */

export const KUNDE_MAX: KontaktKurz = { typ: 'KUNDE', id: 7, name: 'Max Mustermann', nummer: 'K-1007', ort: 'Musterstadt' };
export const KUNDE_ERIKA: KontaktKurz = { typ: 'KUNDE', id: 8, name: 'Erika Mustermann', nummer: 'K-1008', ort: 'Beispielhausen' };
export const LIEFERANT_GMBH: KontaktKurz = { typ: 'LIEFERANT', id: 3, name: 'Mustermann GmbH', nummer: null, ort: 'Würzburg' };

export function anruf(teil: Partial<TelefonAnruf> = {}): TelefonAnruf {
    return {
        id: 1,
        zeitpunkt: '2026-09-29T11:55:00',
        art: 'ANGENOMMEN',
        anrufbeantworter: null,
        nummer: '0931 1234567',
        eigeneNummer: '0931 7654321',
        dauerMinuten: 4,
        nameFritzbox: null,
        zuordnung: 'KEINE',
        kontakt: null,
        kandidaten: [],
        sprachnachrichtId: null,
        ...teil,
    };
}

export function nachricht(teil: Partial<Sprachnachricht> = {}): Sprachnachricht {
    return {
        id: 11,
        anrufbeantworter: 0,
        zeitpunkt: '2026-09-29T11:57:00',
        nummer: '0931 1234567',
        dauerSekunden: 42,
        neu: true,
        abgehoertAm: null,
        abgehoertVon: null,
        zuordnung: 'AUTOMATISCH',
        kontakt: KUNDE_MAX,
        kandidaten: [],
        nameFritzbox: null,
        ...teil,
    };
}

export const STATUS: TelefonStatus = {
    eingerichtet: true,
    anrufbeantworter: [{ index: 0, name: 'AB Tag' }, { index: 1, name: 'AB Nacht' }],
    letzteAbholung: '2026-09-29T11:59:00',
    letzterFehler: null,
    neueSprachnachrichten: 1,
};

export function antwort(body: unknown, status = 200): Response {
    return new Response(body === undefined ? null : JSON.stringify(body), {
        status,
        headers: { 'Content-Type': 'application/json' },
    });
}

type Handler = (url: URL, init: RequestInit | undefined) => Response | Promise<Response> | undefined;

/**
 * Stubbt `fetch`. Jeder Handler bekommt die geparste URL; der erste, der
 * eine Antwort liefert, gewinnt. Ohne Treffer: 404.
 */
export function stubbeFetch(...handler: Handler[]) {
    const mock = vi.fn(async (eingabe: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(String(eingabe), 'http://localhost');
        for (const h of handler) {
            const ergebnis = await h(url, init);
            if (ergebnis) return ergebnis;
        }
        return antwort({ message: 'Nicht gefunden' }, 404);
    });
    vi.stubGlobal('fetch', mock);
    return mock;
}

/** Alle Aufrufe an einen Pfad (optional mit Methode). */
export function aufrufe(mock: ReturnType<typeof stubbeFetch>, pfad: string | RegExp, methode?: string) {
    return mock.mock.calls.filter(([eingabe, init]) => {
        const url = new URL(String(eingabe), 'http://localhost');
        const passt = typeof pfad === 'string' ? url.pathname === pfad : pfad.test(url.pathname);
        return passt && (!methode || (init?.method ?? 'GET') === methode);
    });
}
