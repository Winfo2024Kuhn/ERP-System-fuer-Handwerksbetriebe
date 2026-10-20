import type { Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { stubbeZeiterfassungSichtbarkeit } from './hilfen/api';
import { designPruefung } from './hilfen/design';

/**
 * Issue #269: Projektauswertung mit Zeitraum und Mitarbeiter-Auswahl. Die
 * Filter gehen an die Anzeige und an das PDF, Stunden stehen mit Komma da.
 *
 * DSGVO: nur Dummy-Daten (Max Mustermann). Ohne Backend, alles gestubbt.
 */

function json(route: Route, body: unknown) {
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
}

test('Projektauswertung: Zeitraum und Mitarbeiter gelten für Anzeige und PDF', async ({ page }, testInfo) => {
    const auswertungsAbrufe: string[] = [];
    await page.route('**/api/**', (route) => {
        const url = new URL(route.request().url());
        const pfad = url.pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'anna.buero', vorname: 'Anna', nachname: 'Büro', admin: true, roles: ['ADMIN'], requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/mitarbeiter') {
            return json(route, [
                { id: 7, vorname: 'Max', nachname: 'Mustermann', aktiv: true },
                { id: 8, vorname: 'Erika', nachname: 'Ehemalig', aktiv: false },
            ]);
        }
        if (pfad === '/api/projekte/simple') return json(route, [{ id: 12, bauvorhaben: 'Geländer Musterstraße', auftragsnummer: 'A-26-012', kunde: 'Muster GmbH' }]);
        if (pfad === '/api/zeitverwaltung/auswertung/projekt/12') {
            auswertungsAbrufe.push(url.search);
            return json(route, {
                gesamtStunden: 7.5, anzahlBuchungen: 1,
                taetigkeiten: [{ arbeitsgang: 'Montage', gesamtStunden: 7.5, anzahlBuchungen: 1, buchungen: [
                    { id: 1, mitarbeiterName: 'Max Mustermann', startDateTime: '2026-08-03T07:00:00', startZeit: '07:00', endeZeit: '14:30', dauerMinuten: 450 },
                ] }],
            });
        }
        return json(route, []);
    });
    await stubbeZeiterfassungSichtbarkeit(page);
    await page.addInitScript(() => {
        (window as unknown as { geoeffnet: string[] }).geoeffnet = [];
        window.open = ((url?: string | URL) => { (window as unknown as { geoeffnet: string[] }).geoeffnet.push(String(url)); return null; }) as typeof window.open;
    });

    await page.goto('/auswertung');
    await page.getByText('Projekt wählen...').click();
    await page.getByText('Geländer Musterstraße').click();

    await page.getByRole('button', { name: 'Zeitraum von' }).click();
    await page.getByRole('textbox', { name: 'Datum eingeben' }).fill('01.08.2026');
    await page.keyboard.press('Enter');
    await page.getByRole('button', { name: 'Zeitraum bis' }).click();
    await page.getByRole('textbox', { name: 'Datum eingeben' }).fill('31.08.2026');
    await page.keyboard.press('Enter');

    await page.getByRole('combobox', { name: 'Mitarbeiter' }).click();
    await expect(page.getByRole('option', { name: 'Erika Ehemalig' })).toHaveCount(0);
    await page.getByRole('option', { name: 'Max Mustermann' }).click();

    await page.getByRole('button', { name: 'Mehr zu: Zeitraum' }).click();
    await expect(page.getByRole('dialog', { name: 'Zeitraum' })).toContainText('Der Zeitraum gilt auch für das PDF.');
    await designPruefung(page, testInfo, 'projektauswertung-zeitraum-info', { strengePruefungen: false });
    await page.keyboard.press('Escape');

    await page.getByRole('button', { name: /Auswertung laden/ }).click();
    await expect(page.getByText('Ergebnisse für: Geländer Musterstraße')).toBeVisible();
    expect(auswertungsAbrufe).toEqual(['?von=2026-08-01&bis=2026-08-31&mitarbeiterId=7']);
    await expect(page.getByText('7,50h').first()).toBeVisible();
    await designPruefung(page, testInfo, 'projektauswertung-mit-zeitraum');

    await page.getByRole('button', { name: /PDF Export/ }).click();
    await page.getByRole('button', { name: /PDF erstellen/ }).click();
    await expect.poll(() => page.evaluate(() => (window as unknown as { geoeffnet: string[] }).geoeffnet)).toEqual([
        '/api/zeitverwaltung/auswertung/projekt/12/pdf?von=2026-08-01&bis=2026-08-31&mitarbeiterId=7&sortField=datum&sortDir=asc&groupBy=arbeitsgang',
    ]);
});
