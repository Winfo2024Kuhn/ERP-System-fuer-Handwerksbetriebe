import { test, expect } from './hilfen/test';

// Die Backend-Tests sichern die Normalisierung vor dem Speichern ab.
// Hier wird geprüft, wie die gespeicherten Werte im Benutzerablauf wirken.
test('Lieferantengutschrift zeigt negatives Netto und Brutto und mindert die Gesamtkosten', async ({ page }) => {
    const beleg = {
        dokumentDatum: '2026-09-14', lieferantId: 10, lieferantName: 'Musterlieferant',
        bezahlt: false, zahlungsart: null, originalDateiname: null, pdfUrl: null,
    };
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname;
        const json = (body: unknown) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
        if (path === '/api/auth/me') return json({ id: 1, username: 'test', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: true, requiresInitialSetup: false });
        if (path === '/api/notifications/summary') return json({ totalCount: 0, categories: [], recentItems: [] });
        if (path === '/api/rechnungsuebersicht/eingang') return json([
            { ...beleg, id: 51, dokumentId: 51, dokumentNummer: 'RE-2026-001', betragNetto: 200, betragBrutto: 238 },
            { ...beleg, id: 52, dokumentId: 52, dokumentNummer: 'GS-2026-001', betragNetto: -100, betragBrutto: -119 },
        ]);
        return json([]);
    });

    await page.goto('/rechnungsuebersicht');
    await page.getByRole('button', { name: 'Eingangsrechnungen', exact: true }).click();
    const gutschrift = page.getByRole('row').filter({ hasText: 'GS-2026-001' });
    await expect(gutschrift).toContainText('-100,00 €');
    await expect(gutschrift).toContainText('-119,00 €');
    await expect(page.getByText('Gesamtsumme', { exact: true }).locator('..')).toContainText('119,00 €');

    // Nach erneutem Laden werden dieselben gespeicherten Minuswerte angezeigt.
    await page.reload();
    await page.getByRole('button', { name: 'Eingangsrechnungen', exact: true }).click();
    await expect(gutschrift).toContainText('-100,00 €');
    await expect(gutschrift).toContainText('-119,00 €');
    await expect(page.getByText('Gesamtsumme', { exact: true }).locator('..')).toContainText('119,00 €');
});
