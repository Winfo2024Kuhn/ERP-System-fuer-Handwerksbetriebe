import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { stubbeZeiterfassungSichtbarkeit } from './hilfen/api';
import { designPruefung } from './hilfen/design';

/**
 * Mitarbeiter → Konditionen: Resturlaub aus dem Vorjahr ansehen, von Hand
 * festlegen und wieder ausrechnen lassen (Issue #270, Punkt 4).
 * Kein Backend: alle /api-Routen gestubbt. Nur Dummy-Daten (Max Mustermann).
 */

interface Stand { jahr: number; tage: number; berechnet: number; manuell: boolean; verfallsdatum: string | null }

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const MAX = {
    id: 7, vorname: 'Max', nachname: 'Mustermann', aktiv: true, abteilungIds: [], abteilungNames: '',
    jahresUrlaub: 30, stundenlohn: null, loginToken: null, istGeschaeftsfuehrer: false,
};

async function stubbe(page: Page) {
    const puts: { mitarbeiterId: number; jahr: number; tage: number | null }[] = [];
    let stand: Stand = { jahr: new Date().getFullYear(), tage: 4, berechnet: 4, manuell: false, verfallsdatum: `${new Date().getFullYear()}-03-31` };
    await page.route('**/api/**', async (route) => {
        const anfrage = route.request();
        const pfad = new URL(anfrage.url()).pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/mitarbeiter') return json(route, [MAX]);
        if (pfad === '/api/urlaub/uebertrag') {
            if (anfrage.method() === 'PUT') {
                const body = anfrage.postDataJSON() as { mitarbeiterId: number; jahr: number; tage: number | null };
                puts.push(body);
                stand = body.tage === null
                    ? { ...stand, tage: stand.berechnet, manuell: false }
                    : { ...stand, tage: body.tage, manuell: true };
            }
            return json(route, stand);
        }
        return json(route, []);
    });
    await stubbeZeiterfassungSichtbarkeit(page);
    return puts;
}

test('Resturlaub aus dem Vorjahr von Hand festlegen und zurücknehmen', async ({ page }, info) => {
    const puts = await stubbe(page);
    await page.goto('/mitarbeiter');
    await page.getByText('Mustermann', { exact: true }).click();

    const feld = page.getByRole('textbox', { name: 'Resturlaub aus dem Vorjahr in Tagen' });
    await expect(feld).toHaveValue('4');
    await expect(page.getByText(/Vom Programm ausgerechnet\. Verfällt, wenn nicht bis 31\.03\.\d{4} genommen\./)).toBeVisible();
    await feld.scrollIntoViewIfNeeded();
    await designPruefung(page, info, 'mitarbeiter-resturlaub-berechnet', { primaerAktion: feld });

    await page.getByRole('button', { name: 'Mehr zu: Resturlaub aus dem Vorjahr' }).click();
    await expect(page.getByRole('dialog', { name: 'Resturlaub aus dem Vorjahr' })).toContainText('Gebuchte Urlaubstage ändern sich nicht');
    await designPruefung(page, info, 'mitarbeiter-resturlaub-info');
    await page.keyboard.press('Escape');

    await feld.fill('2,5');
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect(page.getByText('Resturlaub aus dem Vorjahr gespeichert.')).toBeVisible();
    await expect(page.getByText(/Von Hand festgelegt\. Ausgerechnet wären 4 Tage\./)).toBeVisible();
    await designPruefung(page, info, 'mitarbeiter-resturlaub-manuell');

    await page.getByRole('button', { name: /Wieder ausrechnen lassen/ }).click();
    await expect(page.getByText('Resturlaub wird wieder ausgerechnet.')).toBeVisible();
    await expect(feld).toHaveValue('4');
    expect(puts.map(p => p.tage)).toEqual([2.5, null]);
    expect(puts.every(p => p.mitarbeiterId === 7)).toBe(true);
});
