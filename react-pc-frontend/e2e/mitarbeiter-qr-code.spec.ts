import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { PIXEL_PNG, stubbeZeiterfassungSichtbarkeit } from './hilfen/api';
import { designPruefung } from './hilfen/design';

/**
 * QR-Code-Dialog der Mitarbeiter-Detailseite: Kann die Handy-Kamera den
 * QR-Code nicht lesen, tippt der Mitarbeiter den Code in der App unter
 * „Token manuell eingeben“ ab. Dafür zeigt der Dialog den Code zum Abtippen
 * samt Kopieren-Knopf. DSGVO: erfundener Mitarbeiter, erfundener Code.
 */

const MITARBEITER_ID = 42;
const ANMELDE_CODE = '0f8e2c1a-9b7d-4e3f-a6c5-1d2b3c4e5f60';

function json(route: Route, body: unknown) {
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubMitarbeiterApi(page: Page) {
    const pixel = Buffer.from(PIXEL_PNG.split(',')[1], 'base64');
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        if (pfad === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'anna.buero', displayName: 'Anna Büro',
                active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
            });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/mitarbeiter') {
            return json(route, [{
                id: MITARBEITER_ID, vorname: 'Max', nachname: 'Mustermann', strasse: null, plz: null, ort: null,
                email: null, telefon: null, festnetz: null, qualifikation: null, stundenlohn: null,
                geburtstag: null, eintrittsdatum: null, aktiv: true, abteilungIds: [], abteilungNames: null,
                loginToken: ANMELDE_CODE, jahresUrlaub: 30,
            }]);
        }
        if (pfad === `/api/mitarbeiter/${MITARBEITER_ID}/qr-code`) {
            return route.fulfill({ status: 200, contentType: 'image/png', body: pixel });
        }
        if (pfad === '/api/urlaub/uebertrag') {
            return json(route, { jahr: 2026, tage: 0, berechnet: 0, manuell: false, verfallsdatum: '2026-03-31' });
        }
        return json(route, []);
    });
}

test('QR-Code-Dialog zeigt den Anmelde-Code zum Abtippen', async ({ page, context }, testInfo) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await stubMitarbeiterApi(page);
    await stubbeZeiterfassungSichtbarkeit(page);
    await page.goto('/mitarbeiter');

    await page.getByText('Mustermann', { exact: true }).click();
    await page.getByRole('button', { name: /QR-Code/ }).click();

    const dialog = page.getByRole('dialog').filter({ hasText: 'Zeiterfassungs-QR-Code' });
    await expect(dialog.getByText(ANMELDE_CODE, { exact: true })).toBeVisible();
    await expect(dialog.getByText('Token manuell eingeben', { exact: false })).toBeVisible();

    await dialog.getByRole('button', { name: 'Code kopieren' }).click();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(ANMELDE_CODE);

    await designPruefung(page, testInfo, 'mitarbeiter-qr-code-dialog');
});
