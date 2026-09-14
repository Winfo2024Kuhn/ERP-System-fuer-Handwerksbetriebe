import { type Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

const ARBEITSZEIT = {
    montagStunden: 8, dienstagStunden: 8, mittwochStunden: 8, donnerstagStunden: 8,
    freitagStunden: 8, samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: null, buchungEndeZeit: null,
};

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

test('Systemeingaben Task7: eigene Kalender- und Vorlagenauswahl per Tastatur', async ({ page }, testInfo) => {
    const nativeDialogs: string[] = [];
    page.on('dialog', dialog => { nativeDialogs.push(dialog.type()); void dialog.dismiss(); });
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        const mitarbeiter = {
            id: 42, vorname: 'Max', nachname: 'Mustermann', strasse: null, plz: null, ort: null,
            email: 'max.mustermann@example.invalid', telefon: null, festnetz: null, qualifikation: null,
            stundenlohn: null, geburtstag: null, eintrittsdatum: '2026-01-01', aktiv: true,
            fuehrtZeitkonto: true, abteilungIds: [], abteilungNames: null, loginToken: null, jahresUrlaub: 30,
        };
        const zeitkonto = {
            mitarbeiterId: 42, mitarbeiterVersion: 7, mitarbeiterName: 'Max Mustermann', fuehrtZeitkonto: true,
            eingerichtet: false, hinweis: 'Bitte zuerst die Arbeitszeit einrichten.', aktuell: null, letzteVersion: null, historie: [],
        };
        if (pfad === '/api/auth/me') return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/mitarbeiter') return json(route, [mitarbeiter]);
        if (pfad === '/api/abteilungen') return json(route, []);
        if (pfad === '/api/zeitverwaltung/zeitkonten/42') return json(route, zeitkonto);
        if (pfad === '/api/zeitverwaltung/zeitkontenmodelle') return json(route, [{ id: 3, version: 2, bezeichnung: 'Vollzeit Werkstatt', arbeitszeit: ARBEITSZEIT }]);
        if (pfad === '/api/zeitverwaltung/zeitkonten/42/vorschau' && methode === 'POST') {

            return json(route, { zeitkonto, gueltigVon: '2026-09-09', gespeichert: false, bestehendeAbwesenheiten: 1, hinweis: 'Offene Monate werden neu gerechnet.', monate: [{ jahr: 2026, monat: 9, abgeschlossen: false, saldoVorher: 2, saldoNachher: null, geaendert: false }] });
        }
        return json(route, []);
    });

    await page.goto('/mitarbeiter');
    await page.getByText('Mustermann', { exact: true }).click();
    await page.getByRole('button', { name: 'Bearbeiten' }).click();
    await expect(page.getByText('Bitte zuerst die Arbeitszeit einrichten.')).toBeVisible();
    await page.getByRole('button', { name: 'Arbeitszeit einrichten' }).click();
    const dialog = page.getByRole('dialog').filter({ has: page.getByRole('heading', { name: 'Arbeitszeit einrichten' }) });
    const select = dialog.getByRole('combobox');
    await select.focus();
    await page.keyboard.press('ArrowDown');
    await expect(page.getByRole('listbox')).toBeVisible();
    await expect.poll(async () => (await page.getByRole('listbox').boundingBox())!.width).toBeGreaterThan(100);
    const selectBounds = (await select.boundingBox())!;
    const listBounds = (await page.getByRole('listbox').boundingBox())!;
    expect(listBounds.x).toBeGreaterThanOrEqual(selectBounds.x - 1);
    await designPruefung(page, testInfo, 'systemeingaben-task7-auswahl');
    await page.keyboard.press('Enter');
    await expect(select).toContainText('Vollzeit Werkstatt');
    const dateButton = dialog.locator('button[aria-haspopup="dialog"]');
    await dateButton.focus();
    await page.keyboard.press('Enter');
    const calendar = page.getByRole('dialog', { name: 'Datum auswählen', exact: true });
    await expect(calendar).toBeVisible();
    await expect(calendar.locator('button[data-date]:focus')).toHaveCount(1);
    await page.keyboard.press('ArrowRight');
    // Der Popup überdeckt bewusst Hintergrundfelder; geometrisch zählt
    // deshalb hier sein Inhalt, nach Schließen wieder der globale Designcheck.
    const box = await calendar.boundingBox();
    const viewport = page.viewportSize()!;
    expect(box).not.toBeNull();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.y).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(viewport.width);
    expect(box!.y + box!.height).toBeLessThanOrEqual(viewport.height);
    for (const button of await calendar.getByRole('button').all()) {
        const bounds = await button.boundingBox();
        expect(bounds!.x).toBeGreaterThanOrEqual(box!.x);
        expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(box!.x + box!.width);
        expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(box!.y + box!.height);
    }
    await testInfo.attach('systemeingaben-task7-kalender', { body: await page.screenshot(), contentType: 'image/png' });
    await page.keyboard.press('Escape');
    await expect(calendar).not.toBeVisible();
    await expect(dateButton).toBeFocused();
    expect(nativeDialogs).toEqual([]);
    await designPruefung(page, testInfo, 'systemeingaben-task7-geschlossen');
});
