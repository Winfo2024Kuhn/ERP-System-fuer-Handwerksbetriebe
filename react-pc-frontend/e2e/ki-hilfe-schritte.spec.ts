import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Die KI-Hilfe schlägt bei schwierigen Fragen mehrmals im Programm nach. Der
 * Chat zeigt dabei, was sie gerade tut, und unter der Antwort zugeklappt,
 * wo sie nachgeschaut hat. Das Backend schickt dafür einen Ereignisstrom
 * (`text/event-stream`, Ereignisse `schritt` und `antwort`).
 *
 * Kein Backend: alle /api-Routen sind gestubbt. Nur Dummy-Daten (DSGVO).
 * Dass die Schritte einzeln nacheinander erscheinen, prüft der Vitest-Test
 * neben `KiHilfeChat.tsx` – `route.fulfill` liefert den Strom am Stück.
 */

const SCHRITTE = [
    'Passende Stellen im Programm gesucht',
    'Schaut nach: Wie man eine Rechnung storniert',
    'Liest genauer: Rechnungen im Projekt',
];

const ANTWORT = 'So stornierst du eine Rechnung:\n'
    + '1. Öffne unter **Rechnungen** die gewünschte Rechnung.\n'
    + '2. Klicke auf **Stornieren** und bestätige die Rückfrage.';

function sse(ereignis: string, daten: unknown): string {
    return `event:${ereignis}\ndata:${JSON.stringify(daten)}\n\n`;
}

const STROM = ':ping\n\n'
    + SCHRITTE.map((text) => sse('schritt', { text })).join('')
    + sse('antwort', { reply: ANTWORT, sources: [], schritte: SCHRITTE });

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

/** Angemeldeter Benutzer mit KI-Schlüssel auf einer leeren Projektübersicht. */
async function stubbeGrundgeruest(page: Page) {
    // Auffangnetz zuerst: Playwright fragt die zuletzt registrierte Route zuerst.
    await page.route('**/api/**', (route) => json(route, {}));
    await page.route('**/api/auth/me', (route) => json(route, {
        id: 1, displayName: 'Max Mustermann', username: 'max', active: true,
        roles: [], admin: false, requiresInitialSetup: false, kiAktiv: true,
    }));
    await page.route('**/api/notifications/summary**', (route) => json(route, { totalCount: 0, categories: [], recentItems: [] }));
    await page.route('**/api/telefon/berechtigung', (route) => json(route, { darfTelefonSehen: false }));
    await page.route('**/api/projekte**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        if (pfad === '/api/projekte') return json(route, { projekte: [], gesamt: 0 });
        if (pfad === '/api/projekte/jahre') return json(route, []);
        return json(route, {});
    });
}

test('KI-Hilfe zeigt beim Suchen, was sie tut, und unter der Antwort, wo sie nachgeschaut hat', async ({ page }, info) => {
    await stubbeGrundgeruest(page);
    // Die Antwort wird erst freigegeben, wenn die Lade-Blase geprüft ist.
    let freigeben!: () => void;
    const freigabe = new Promise<void>((weiter) => { freigeben = weiter; });
    const anfragen: unknown[] = [];
    await page.route('**/api/ki-hilfe/chat', async (route) => {
        anfragen.push(route.request().postDataJSON());
        await freigabe;
        await route.fulfill({ status: 200, contentType: 'text/event-stream', body: STROM });
    });

    await page.goto('/projekte');
    await page.getByRole('button', { name: 'KI-Hilfe' }).click();
    await expect(page.getByRole('heading', { name: 'KI-Hilfe' })).toBeVisible();
    await expect(page.getByText('Gemini · Programmhilfe')).toBeVisible();

    // Info-Knopf im roten Kopf erklärt, was im Hintergrund passiert.
    const infoKnopf = page.getByRole('button', { name: 'Mehr zu: So arbeitet die KI-Hilfe' });
    await infoKnopf.click();
    const erklaerung = page.getByRole('dialog', { name: 'So arbeitet die KI-Hilfe' });
    await expect(erklaerung).toContainText('Was passiert im Hintergrund?');
    await expect(erklaerung).toContainText('in höchstens vier Runden');
    await expect(erklaerung).toContainText('Sie liest und erklärt nur und ändert nichts im Programm.');
    await expect(erklaerung).toBeInViewport({ ratio: 1 });
    await designPruefung(page, info, 'ki-hilfe-info');
    await page.keyboard.press('Escape');
    await expect(erklaerung).toHaveCount(0);
    await expect(infoKnopf).toBeFocused();

    await page.getByPlaceholder('Frage stellen...').fill('Wie storniere ich eine Rechnung?');
    await page.getByRole('button', { name: 'Frage senden' }).click();

    const live = page.getByRole('list', { name: 'Was die KI gerade tut' });
    await expect(live).toContainText('Denkt nach …');
    await expect(live).toHaveAttribute('aria-live', 'polite');
    await expect(page.getByRole('button', { name: 'Abbrechen' })).toBeVisible();
    await designPruefung(page, info, 'ki-hilfe-denkt-nach');
    expect(anfragen).toEqual([expect.objectContaining({
        messages: [{ role: 'user', text: 'Wie storniere ich eine Rechnung?' }],
        context: expect.objectContaining({ route: '/projekte' }),
    })]);

    freigeben();
    await expect(page.getByText('Klicke auf')).toBeVisible();
    await expect(live).toHaveCount(0);

    const aufklapper = page.getByRole('button', { name: 'So habe ich nachgeschaut (3)' });
    await expect(aufklapper).toHaveAttribute('aria-expanded', 'false');
    await expect(page.getByText(SCHRITTE[1])).toHaveCount(0);
    await designPruefung(page, info, 'ki-hilfe-antwort-zugeklappt');

    // Per Tastatur aufklappen.
    await aufklapper.focus();
    await page.keyboard.press('Enter');
    await expect(aufklapper).toHaveAttribute('aria-expanded', 'true');
    for (const schritt of SCHRITTE) await expect(page.getByText(schritt)).toBeVisible();
    await designPruefung(page, info, 'ki-hilfe-antwort-aufgeklappt');
});
