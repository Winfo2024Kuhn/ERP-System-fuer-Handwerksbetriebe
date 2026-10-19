import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Anmeldeseite und Benutzer verwalten (#274):
 * - „Konto anlegen“ gibt es nur, solange noch kein Benutzer mit Anmeldung existiert.
 * - Benutzer verwalten prüft die Passwortlänge schon im Browser, zeigt die
 *   Meldung des Servers und bestätigt das Speichern.
 * Ohne Backend, alle Daten Dummy (DSGVO).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

test('Anmeldeseite: „Konto anlegen“ nur ohne vorhandene Benutzer', async ({ page }, info) => {
    let hasLoginUsers = true;
    await page.route('**/api/**', route => json(route, {}));
    await page.route('**/api/auth/me', route => json(route, { message: 'Nicht angemeldet.' }, 401));
    await page.route('**/api/auth/bootstrap-status', route => json(route, { hasLoginUsers, setupRequired: !hasLoginUsers }));

    // Phase 1: Es gibt schon Benutzer → nur Anmelden.
    const status1 = page.waitForResponse(res => res.url().includes('/api/auth/bootstrap-status'));
    await page.goto('/login');
    await status1;
    await expect(page.getByRole('button', { name: 'Anmelden' })).toBeVisible();
    await expect(page.getByRole('button', { name: /Konto anlegen/ })).toHaveCount(0);
    await designPruefung(page, info, 'anmeldung-ohne-konto-anlegen');

    // Phase 2: Noch kein Benutzer → „Konto anlegen“ ist da und öffnet das Formular.
    hasLoginUsers = false;
    await page.reload();
    const kontoAnlegen = page.getByRole('button', { name: /Konto anlegen/ });
    await expect(kontoAnlegen).toBeVisible();
    await kontoAnlegen.click();
    await expect(page.getByRole('button', { name: /Konto erstellen/ })).toBeVisible();
});

const BENUTZER = {
    id: 7, displayName: 'Max Mustermann', username: 'max', shortCode: 'MM', roles: ['USER'], active: true,
    mitarbeiter: null, emailAbsender: null,
};

async function stubbeBenutzer(page: Page, speichern: () => { status: number; body: unknown }) {
    const gespeichert: unknown[] = [];
    await page.route('**/api/**', async route => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        if (pfad === '/api/auth/me') return json(route, { id: 1, username: 'admin', displayName: 'Erika Musterfrau', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/frontend-users' && request.method() === 'POST') {
            gespeichert.push(request.postDataJSON());
            const antwort = speichern();
            return json(route, antwort.body, antwort.status);
        }
        if (pfad === '/api/frontend-users') return json(route, [BENUTZER]);
        if (pfad === '/api/mitarbeiter' || pfad === '/api/postfaecher') return json(route, []);
        return json(route, {});
    });
    return gespeichert;
}

test('Benutzer verwalten: Passwortlänge, Server-Meldung und Bestätigung', async ({ page }, info) => {
    let antwort = { status: 400, body: { status: 400, message: 'Benutzername ist bereits vergeben.' } as unknown };
    const gespeichert = await stubbeBenutzer(page, () => antwort);
    await page.goto('/benutzer');

    await page.getByText('Max Mustermann', { exact: true }).first().click();
    const passwort = page.getByLabel(/Passwort/);
    const speichern = page.getByRole('button', { name: 'Speichern', exact: true });
    await expect(speichern).toBeEnabled();

    // Zu kurzes neues Passwort: Hinweis am Feld, Speichern gesperrt.
    await passwort.fill('kurz');
    await expect(page.getByText('Das Passwort muss mindestens 8 Zeichen lang sein.')).toBeVisible();
    await expect(passwort).toHaveAttribute('aria-invalid', 'true');
    await expect(speichern).toBeDisabled();
    await designPruefung(page, info, 'benutzer-passwort-zu-kurz');

    // Server lehnt ab: seine Meldung erscheint statt „Fehler beim Speichern.“
    await passwort.fill('lang-genug');
    await speichern.click();
    const meldungen = page.getByRole('region', { name: 'Meldungen' });
    await expect(meldungen.getByText('Benutzername ist bereits vergeben.')).toBeVisible();
    await expect(meldungen.getByText('Fehler beim Speichern.')).toHaveCount(0);

    // Erfolg: Bestätigung als Toast.
    antwort = { status: 200, body: BENUTZER };
    await speichern.click();
    await expect(meldungen.getByText('Benutzer gespeichert.')).toBeVisible();
    expect(gespeichert).toHaveLength(2);
    expect(gespeichert[1]).toMatchObject({ id: 7, password: 'lang-genug' });
});
