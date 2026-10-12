import { test, expect } from './hilfen/test';
import type { Page } from '@playwright/test';
import { designPruefung } from './hilfen/design';

/**
 * Bezahlt-Haken im Offene-Posten-Center: Schlägt das Speichern fehl, sieht der
 * Nutzer die Meldung als Toast und der Haken zeigt weiter den gespeicherten Stand.
 *
 * Alle Namen sind Dummy-Daten (DSGVO).
 */
const rechnung = {
    id: 7,
    rechnungsnummer: 'RE-2026-001',
    rechnungsdatum: '2026-10-01',
    faelligkeitsdatum: '2026-10-15',
    rechnungsbetrag: 1190,
    bezahlt: false,
    mahnung: false,
    projektId: 3,
    projektAuftragsnummer: 'P-2026-003',
    projektKunde: 'Max Mustermann',
};

/** Stubbt die Seite; `bezahltAntwort` beantwortet den PATCH auf den Haken. */
async function stubbeApi(page: Page, bezahltAntwort: { status: number; body?: unknown }) {
    let bezahlt = false;
    await page.route('**/api/**', async (route) => {
        const url = new URL(route.request().url());
        const json = (body: unknown, status = 200) =>
            route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
        if (url.pathname === '/api/auth/me') return json({ id: 1, username: 'test', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: true, requiresInitialSetup: false });
        if (url.pathname === '/api/notifications/summary') return json({ totalCount: 0, categories: [], recentItems: [] });
        if (url.pathname === '/api/firma') return json({});
        // Wie das Backend: Die Liste enthält nur unbezahlte Rechnungen.
        if (url.pathname === '/api/projekte/offene-posten') return json(bezahlt ? [] : [rechnung]);
        if (url.pathname === '/api/projekte/dokumente/7/bezahlt' && route.request().method() === 'PATCH') {
            if (bezahltAntwort.status < 300) bezahlt = url.searchParams.get('bezahlt') === 'true';
            return bezahltAntwort.body === undefined
                ? route.fulfill({ status: bezahltAntwort.status })
                : json(bezahltAntwort.body, bezahltAntwort.status);
        }
        return json([]);
    });
}

test('zeigt bei einem Konflikt die Meldung und lässt den Haken offen', async ({ page }, testInfo) => {
    const meldung = 'Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.';
    await stubbeApi(page, { status: 409, body: { status: 409, message: meldung, fields: [] } });
    await page.goto('/offeneposten');

    const haken = page.getByRole('checkbox', { name: 'Bezahlt' });
    await haken.click();

    await expect(page.getByRole('region', { name: 'Meldungen' }).getByText(meldung)).toBeVisible();
    await expect(page.getByRole('checkbox', { name: 'Bezahlt' })).not.toBeChecked();
    await expect(page.getByRole('checkbox', { name: 'Bezahlt' })).toBeEnabled();
    await designPruefung(page, testInfo, 'offene-posten-bezahlt-konflikt');
});

test('nimmt die Rechnung aus der Liste, wenn das Speichern klappt', async ({ page }) => {
    await stubbeApi(page, { status: 204 });
    await page.goto('/offeneposten');

    await page.getByRole('checkbox', { name: 'Bezahlt' }).click();

    await expect(page.getByText('Alle Rechnungen wurden bezahlt.')).toBeVisible();
    await expect(page.getByText('RE-2026-001')).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Meldungen' }).getByText(/konnte nicht/)).toHaveCount(0);
});

test('erklärt am Status, was der Haken im Hintergrund auslöst', async ({ page }, testInfo) => {
    await stubbeApi(page, { status: 204 });
    await page.goto('/offeneposten');

    await page.getByRole('button', { name: /Haken „Bezahlt“/ }).click();

    await expect(page.getByText(/gilt das Projekt als bezahlt und wird automatisch beendet/)).toBeVisible();
    await designPruefung(page, testInfo, 'offene-posten-bezahlt-info');
});
