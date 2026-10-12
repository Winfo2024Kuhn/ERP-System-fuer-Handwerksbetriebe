import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Rechnungen kommen nur noch über „Belege & Kasse“ ins Programm. Die
 * Rechnungsübersicht bietet deshalb kein „Rechnung hochladen“ mehr an und die
 * Offenen Posten kein „Manuell erfassen“. Der Rest der Kopfzeile bleibt.
 *
 * Alle Namen sind Dummy-Daten (DSGVO).
 */
test.beforeEach(async ({ page }) => {
    await page.route('**/api/**', async (route) => {
        const url = new URL(route.request().url());
        const json = (body: unknown) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
        if (url.pathname === '/api/auth/me') return json({ id: 1, username: 'test', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: true, requiresInitialSetup: false });
        if (url.pathname === '/api/notifications/summary') return json({ totalCount: 0, categories: [], recentItems: [] });
        if (url.pathname === '/api/firma') return json({});
        return json([]);
    });
});

test('Rechnungsübersicht bietet kein Hochladen mehr an', async ({ page }, testInfo) => {
    await page.goto('/rechnungsuebersicht');
    await expect(page.getByRole('button', { name: 'Aktualisieren' })).toBeVisible();
    await expect(page.getByRole('button', { name: /hochladen/i })).toHaveCount(0);
    await designPruefung(page, testInfo, 'rechnungsuebersicht-ohne-upload');
});

test('Offene Posten bieten kein manuelles Erfassen mehr an', async ({ page }, testInfo) => {
    await page.goto('/offeneposten');
    await expect(page.getByRole('button', { name: 'Aktualisieren' })).toBeVisible();
    await expect(page.getByRole('button', { name: /manuell erfassen/i })).toHaveCount(0);
    await designPruefung(page, testInfo, 'offene-posten-ohne-manuell');
});

test('Gesamt-PDF nennt Eingangsrechnungen ohne PDF-Datei', async ({ page }, testInfo) => {
    await page.route('**/api/rechnungsuebersicht/eingang**', route => route.fulfill({ json: [
        { id: 11, dokumentNummer: 'ER-11', lieferantName: 'Musterlieferant GmbH', dokumentDatum: '2026-10-01', betragBrutto: 119, bezahlt: false },
        { id: 12, dokumentNummer: 'ER-12', lieferantName: 'Musterlieferant GmbH', dokumentDatum: '2026-10-02', betragBrutto: 59.5, bezahlt: false },
    ] }));
    await page.route('**/api/rechnungsuebersicht/merge-pdf', route => route.fulfill({
        status: 200,
        contentType: 'application/pdf',
        headers: { 'X-Nicht-Enthalten': '12' },
        body: '%PDF-1.7\n%%EOF',
    }));
    await page.goto('/rechnungsuebersicht');
    await page.getByRole('button', { name: /Eingangsrechnungen/ }).click();
    await expect(page.getByText('ER-12')).toBeVisible();
    await page.getByRole('checkbox').first().check();
    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: /Gesamt-PDF \(2\)/ }).click();
    await download;
    await expect(page.getByText('Nicht im Gesamt-PDF, weil es keine PDF-Datei dazu gibt: ER-12')).toBeVisible();
    await designPruefung(page, testInfo, 'gesamt-pdf-hinweis-fehlende-rechnung');
});
