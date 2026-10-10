import type { Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Ein Gewerk, das in den Firmendaten eingetragen ist, laesst sich nicht loeschen.
 *
 * Bis V410 blockierte ein doppelter Fremdschluessel das Loeschen stumm; seitdem
 * wuerde die Datenbank das Gewerk der Firma leeren (ON DELETE SET NULL) und der
 * BG-Satz fiele unbemerkt weg. Das Backend lehnt deshalb mit 409 ab - und die
 * Firma-Seite muss den Grund zeigen, nicht nur "konnte nicht geloescht werden".
 *
 * /api vollstaendig gestubbt, fremde Hosts riegelt e2e/hilfen/test.ts ab.
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const AUTH_ME = {
    id: 1, username: 'chefin', displayName: 'Erika Mustermann',
    active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
};
const GEWERKE = [
    { id: 4, name: 'Metallbau', bgName: 'BGHM', bgSatzProzent: 1.13, aktiv: true },
    { id: 5, name: 'Tischler', bgName: 'BGHM', bgSatzProzent: 1.21, aktiv: true },
];
const MELDUNG = 'Dieses Gewerk ist in den Firmendaten eingetragen. Bitte dort zuerst ein anderes Gewerk wählen '
    + 'oder dieses Gewerk ausschalten.';

test('Gewerk der Firma loeschen: Sperre mit verstaendlichem Grund, Gewerk bleibt stehen', async ({ page }, testInfo) => {
    const loeschVersuche: string[] = [];
    // Zuerst der Auffang fuer alles Uebrige, danach die konkreten Antworten (die zuletzt registrierte Route gewinnt)
    await page.route('**/api/**', (route) => json(route, []));
    await page.route('**/api/auth/me', (route) => json(route, AUTH_ME));
    await page.route('**/api/notifications/summary', (route) =>
        json(route, { totalCount: 0, categories: [], recentItems: [] }));
    await page.route('**/api/firma', (route) => json(route, { id: 1, firmenname: 'Mustermann Metallbau GmbH', gewerkId: 4 }));
    await page.route('**/api/lohn-stammdaten/gewerke**', (route) => {
        if (route.request().method() === 'DELETE') {
            loeschVersuche.push(route.request().url());
            return json(route, { status: 409, message: MELDUNG, constraint: null, fields: [], detail: MELDUNG }, 409);
        }
        return json(route, GEWERKE);
    });

    await page.goto('/firma');
    await page.getByRole('button', { name: 'Lohn-Stammdaten' }).click();
    await page.getByRole('button', { name: /Gewerke \/ BG/ }).click();

    const loeschen = page.getByRole('button', { name: 'Gewerk Metallbau löschen' });
    await designPruefung(page, testInfo, 'gewerke-liste', { primaerAktion: loeschen });

    await loeschen.click();
    await page.getByRole('button', { name: 'Löschen', exact: true }).click();

    await expect(page.getByText(MELDUNG)).toBeVisible();
    await expect(page.getByRole('cell', { name: 'Metallbau', exact: true })).toBeVisible();
    expect(loeschVersuche).toHaveLength(1);
    expect(loeschVersuche[0]).toContain('/api/lohn-stammdaten/gewerke/4');

    await designPruefung(page, testInfo, 'gewerk-loeschen-gesperrt', { primaerAktion: loeschen });
});
