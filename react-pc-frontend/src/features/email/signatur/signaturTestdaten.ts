import { vi } from 'vitest';
import type { SignaturDto } from './signaturTypen';

/**
 * Dummy-Daten und Fetch-Stub für die Signatur-Tests. Nur erfundene Namen
 * und Adressen (DSGVO).
 */

export const MAX_USER = { id: 7, displayName: 'Max Mustermann', username: 'max', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false };
export const ADMIN_USER = { ...MAX_USER, id: 1, displayName: 'Erika Admin', username: 'erika', roles: ['ADMIN'], admin: true };

export const FIRMA = {
    firmenname: 'Mustermann Metallbau GmbH', strasse: 'Musterstraße 1', plz: '12345', ort: 'Musterstadt',
    telefon: '0931 123456', fax: null, email: 'info@example.com', website: 'www.mustermann-metallbau.example', firmenfarbe: '#7c2d12',
};

export const BENUTZER = [
    { id: 1, displayName: 'Erika Admin', active: true },
    { id: 7, displayName: 'Max Mustermann', active: true },
    { id: 9, displayName: 'Moritz Muster', active: true },
    { id: 11, displayName: 'Alt Ausgeschieden', active: false },
];

export function signatur(teil: Partial<SignaturDto> = {}): SignaturDto {
    return {
        id: 5,
        name: 'Standard',
        html: '<table cellpadding="0" cellspacing="0" border="0"><tr><td>Max Mustermann</td></tr></table>',
        vorlageDaten: null,
        defaultSignature: false,
        isSystemDefault: false,
        besitzerId: 7,
        besitzerName: 'Max Mustermann',
        eigene: true,
        createdAt: '2026-10-01T10:00:00',
        updatedAt: '2026-10-01T10:00:00',
        images: [],
        ...teil,
    };
}

export function antwort(body: unknown, status = 200): Response {
    return new Response(body === undefined ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

type Handler = (url: URL, methode: string, init?: RequestInit) => Response | undefined | Promise<Response | undefined>;

export interface StubOptionen {
    admin?: boolean;
    signaturen?: SignaturDto[];
    speichern?: { status: number; body: unknown };
    extra?: Handler;
}

export function stubbeSignaturApi(optionen: StubOptionen = {}) {
    const liste = [...(optionen.signaturen ?? [])];
    const mock = vi.fn(async (eingabe: RequestInfo | URL, init?: RequestInit) => {
        const url = new URL(String(eingabe), 'http://localhost');
        const methode = init?.method ?? 'GET';
        const extra = await optionen.extra?.(url, methode, init);
        if (extra) return extra;
        if (url.pathname === '/api/auth/me') return antwort(optionen.admin ? ADMIN_USER : MAX_USER);
        if (url.pathname === '/api/firma') return antwort(FIRMA);
        if (url.pathname === '/api/frontend-users') return optionen.admin ? antwort(BENUTZER) : antwort({ message: 'Kein Recht' }, 403);
        // Wie das Backend: die Liste ohne Formulardaten, erst die Einzelabfrage liefert sie.
        if (url.pathname === '/api/email/signatures' && methode === 'GET') return antwort(liste.map((s) => ({ ...s, vorlageDaten: null })));
        const einzeln = /^\/api\/email\/signatures\/(\d+)$/.exec(url.pathname);
        if (einzeln && methode === 'GET') {
            const treffer = liste.find((s) => s.id === Number(einzeln[1]));
            return treffer ? antwort(treffer) : antwort({ message: 'Nicht gefunden' }, 404);
        }
        if (url.pathname === '/api/email/signatures' && methode === 'POST') {
            if (optionen.speichern) return antwort(optionen.speichern.body, optionen.speichern.status);
            const body = JSON.parse(String(init?.body)) as Record<string, unknown>;
            return antwort(signatur({ id: (body.id as number | null) ?? 99, name: body.name as string, html: body.html as string, vorlageDaten: body.vorlageDaten as string | null }));
        }
        if (/^\/api\/email\/signatures\/\d+\/preview$/.test(url.pathname)) return antwort({ html: '<p>Vorschau mit Bild</p>' });
        if (/^\/api\/email\/signatures\/\d+$/.test(url.pathname) && methode === 'DELETE') return new Response(null, { status: 204 });
        if (/^\/api\/email\/signatures\/\d+\/(standard|system-default)$/.test(url.pathname) && methode === 'PUT') return antwort(signatur());
        if (url.pathname === '/api/email/signatures/standard' && methode === 'DELETE') return new Response(null, { status: 204 });
        if (url.pathname === '/api/email/signatures/default') return new Response(null, { status: 204 });
        return antwort({ message: 'Nicht gefunden' }, 404);
    });
    vi.stubGlobal('fetch', mock);
    return mock;
}

export function aufrufe(mock: ReturnType<typeof stubbeSignaturApi>, pfad: string | RegExp, methode?: string) {
    return mock.mock.calls.filter(([eingabe, init]) => {
        const url = new URL(String(eingabe), 'http://localhost');
        const passt = typeof pfad === 'string' ? url.pathname === pfad : pfad.test(url.pathname);
        return passt && (!methode || (init?.method ?? 'GET') === methode);
    });
}

export function gesendeterBody(mock: ReturnType<typeof stubbeSignaturApi>, index = 0): Record<string, unknown> {
    const [, init] = aufrufe(mock, '/api/email/signatures', 'POST')[index];
    return JSON.parse(String(init?.body)) as Record<string, unknown>;
}
