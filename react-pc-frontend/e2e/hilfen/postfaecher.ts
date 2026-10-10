import type { Page, Request, Route } from '@playwright/test';

/**
 * Gemeinsame Stubs für die Postfach-Specs (Einstellungen, Absender, Einzelversand).
 * Nur Dummy-Daten (DSGVO) – Beispiel-Domain aus der Spec.
 */

export const INFO = { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' };
export const MAX = { id: 7, emailAdresse: 'max@musterbetrieb.example', anzeigename: 'Max Mustermann' };
export const RECHNUNGEN = { id: 9, emailAdresse: 'rechnungen@musterbetrieb.example', anzeigename: null };

export const ABSENDER_POSTFAECHER = [
    { ...MAX, eigenes: true, hauptpostfach: false },
    { ...INFO, eigenes: false, hauptpostfach: true },
    { ...RECHNUNGEN, eigenes: false, hauptpostfach: false },
];

export const EINGANG = {
    id: 701, type: 'EMAIL', direction: 'IN', subject: 'Anfrage Balkongeländer',
    fromAddress: 'Erika Musterfrau <erika.musterfrau@example.org>', recipient: 'info@musterbetrieb.example',
    body: 'Können Sie uns ein Angebot machen?', htmlBody: '<p>Können Sie uns ein Angebot machen?</p>',
    sentAt: '2026-10-09T09:30:00', isRead: true, attachments: [], zuordnungTyp: 'KEINE',
    postfaecher: [INFO, MAX], antwortPostfach: INFO,
};

export function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

/** Liest den Teil `dto` aus einer multipart-Anfrage (`POST /api/emails/send`). */
export function dtoAusMultipart(request: Request): Record<string, unknown> {
    const roh = request.postDataBuffer()?.toString('utf8') ?? '';
    const start = roh.indexOf('name="dto"');
    if (start < 0) throw new Error('Kein dto-Teil in der Anfrage');
    const inhaltStart = roh.indexOf('\r\n\r\n', start) + 4;
    const inhaltEnde = roh.indexOf('\r\n--', inhaltStart);
    return JSON.parse(roh.slice(inhaltStart, inhaltEnde));
}

export interface EmailCenterStub {
    gesendet: { pfad: string; dto: Record<string, unknown> }[];
}

/**
 * Stubbt das E-Mail-Center. `sendeAntwort` bestimmt, was `/api/emails/send`
 * zurückgibt (Standard: eine gespeicherte Mail).
 */
export async function stubbeEmailCenter(page: Page, optionen: {
    sendeAntwort?: { status: number; body: unknown };
} = {}): Promise<EmailCenterStub> {
    const stub: EmailCenterStub = { gesendet: [] };
    await page.route('**/api/**', async route => {
        const anfrage = route.request();
        const pfad = new URL(anfrage.url()).pathname;
        const methode = anfrage.method();
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/emails/stats') return json(route, { inboxCount: 1 });
        if (pfad === '/api/emails/absender-postfaecher') return json(route, ABSENDER_POSTFAECHER);
        if (pfad === '/api/emails/inbox') return json(route, [EINGANG]);
        if (pfad === '/api/emails/701') return json(route, EINGANG);
        if (pfad === '/api/emails/701/thread') return json(route, { rootEmailId: 701, focusedEmailId: 701, emails: [EINGANG] });
        if (pfad === '/api/email/signatures/default') return route.fulfill({ status: 204, body: '' });
        if (methode === 'POST' && (pfad === '/api/emails/send' || /^\/api\/emails\/\d+\/reply$/.test(pfad))) {
            stub.gesendet.push({ pfad, dto: dtoAusMultipart(anfrage) });
            const antwort = pfad === '/api/emails/send' && optionen.sendeAntwort
                ? optionen.sendeAntwort
                : { status: 200, body: { ...EINGANG, id: 900, direction: 'OUT' } };
            return json(route, antwort.body, antwort.status);
        }
        if (pfad.startsWith('/api/emails/drafts') && methode !== 'GET') return json(route, { id: 55 });
        return json(route, []);
    });
    return stub;
}
