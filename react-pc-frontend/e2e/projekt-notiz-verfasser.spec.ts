import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { blockiereFremdeNetzwerkzugriffe } from './hilfen/api';
import { designPruefung } from './hilfen/design';

/**
 * Verfasser von Notizen und Hochlader von Dokumenten kommen aus der Anmeldung.
 * Das PC-Frontend schickt deshalb keine Identität (früher `X-User-Profile-Id`) mehr
 * mit. Ist dem Benutzer kein Mitarbeiter zugeordnet, lehnt der Server das Anlegen
 * einer Notiz mit 403 ab – die Oberfläche zeigt diese Meldung, statt still zu
 * scheitern (und 401 würde abmelden).
 */
const PROJEKT_ID = 5;
const KEIN_MITARBEITER = 'Ihr Benutzer ist keinem Mitarbeiter zugeordnet. '
    + 'Bitte einen Admin bitten, das oben rechts unter „Benutzer verwalten“ nachzuholen – '
    + 'sonst kann das nicht mit Ihrem Namen gespeichert werden.';

// Ohne strasse/plz/ort: sonst lädt GoogleMapsEmbed ein externes iframe.
const DUMMY_PROJEKT = {
    id: PROJEKT_ID,
    bauvorhaben: 'Treppenanlage Musterstraße',
    kunde: 'Max Mustermann',
    kundenId: 3,
    kundennummer: 'K-1003',
    auftragsnummer: 'A-2026-0005',
    anlegedatum: '2026-01-15',
    bruttoPreis: 12000,
    bezahlt: false,
    abgeschlossen: false,
    kundenEmails: [],
    materialkosten: [],
    artikel: [],
    produktkategorien: [],
    zeiten: [],
    emails: [],
};

type Aufruf = { method: string; path: string; headers: Record<string, string> };

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubbeProjektApi(page: Page, aufrufe: Aufruf[]) {
    await blockiereFremdeNetzwerkzugriffe(page);
    await page.route('**/api/**', route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        const method = request.method();
        if (method !== 'GET') aufrufe.push({ method, path, headers: request.headers() });

        if (path === '/api/auth/me') {
            return json(route, { id: 1, username: 'max.mustermann', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (path === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (/^\/api\/last-accessed\/PROJEKT(\/\d+)?$/.test(path)) {
            return method === 'POST' ? route.fulfill({ status: 204, body: '' }) : json(route, {});
        }
        if (path === '/api/projekte' && method === 'GET') return json(route, { projekte: [], gesamt: 0 });
        if (path === '/api/projekte/freigabe-status') return json(route, {});
        if (path === `/api/projekte/${PROJEKT_ID}`) return json(route, DUMMY_PROJEKT);
        if (path === `/api/projekte/${PROJEKT_ID}/notizen` && method === 'POST') {
            return json(route, { status: 403, message: KEIN_MITARBEITER }, 403);
        }
        if (path === `/api/projekte/${PROJEKT_ID}/notizen`) return json(route, []);
        if (path === '/api/ausgangs-dokumente/freigabe-status') return json(route, {});
        return json(route, []);
    });
}

test('Tagebuch: Eintrag ohne Mitarbeiter-Zuordnung zeigt den Hinweis und schickt keine Identität mit', async ({ page }, info) => {
    const aufrufe: Aufruf[] = [];
    await stubbeProjektApi(page, aufrufe);

    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=notizen`);
    await page.getByRole('button', { name: 'Neuer Eintrag' }).click();
    await page.getByPlaceholder('Notiz eingeben...').fill('Aufmaß vor Ort erledigt');
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();

    await expect(page.getByRole('region', { name: 'Meldungen' }).getByText(KEIN_MITARBEITER)).toBeVisible();
    // 403 meldet nicht ab: die Seite bleibt offen.
    await expect(page.getByPlaceholder('Notiz eingeben...')).toBeVisible();

    const post = aufrufe.find(a => a.method === 'POST' && a.path === `/api/projekte/${PROJEKT_ID}/notizen`);
    expect(post).toBeDefined();
    expect(post!.headers).not.toHaveProperty('x-user-profile-id');
    expect(post!.headers).not.toHaveProperty('x-mitarbeiter-id');

    await designPruefung(page, info, 'projekt-notiz-ohne-mitarbeiter-zuordnung');
});
