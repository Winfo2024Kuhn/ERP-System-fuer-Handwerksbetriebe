import type { Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Textvorlage loeschen: erst nach Rueckfrage, die erklaert, was mit den
 * Standard-Texten der Formularvorlagen passiert (ON DELETE CASCADE).
 * „Abbrechen“ loescht nichts, „Löschen“ schickt genau ein DELETE.
 *
 * /api vollstaendig gestubbt, fremde Hosts riegelt e2e/hilfen/test.ts ab.
 * DSGVO: nur Dummy-Daten.
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const AUTH_ME = {
    id: 1, username: 'chefin', displayName: 'Erika Mustermann',
    active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
};
const VORLAGEN = [
    { id: 1, name: 'Anschreiben', html: '<p>Guten Tag {{Kundenname}},</p><p>Bauvorhaben: {{BAUVORHABEN}}</p>', dokumenttypen: ['Angebot'] },
    { id: 2, name: 'Mahnung', html: '<p>Sehr geehrte Damen und Herren,</p>', dokumenttypen: [] },
];

test('Textvorlage loeschen: Rueckfrage, Abbrechen behaelt, Loeschen schickt ein DELETE', async ({ page }, testInfo) => {
    const loeschAufrufe: string[] = [];
    let geloescht = false;
    // Zuerst der Auffang fuer alles Uebrige, danach die konkreten Antworten (die zuletzt registrierte Route gewinnt)
    await page.route('**/api/**', (route) => json(route, []));
    await page.route('**/api/auth/me', (route) => json(route, AUTH_ME));
    await page.route('**/api/notifications/summary', (route) =>
        json(route, { totalCount: 0, categories: [], recentItems: [] }));
    await page.route('**/api/kunden', (route) => json(route, { kunden: [{ id: 7, name: 'Max Mustermann' }] }));
    // Nach dem DELETE liefert der Server nur noch die zweite Vorlage, wie das echte Backend.
    await page.route('**/api/textbausteine', (route) => json(route, geloescht ? VORLAGEN.slice(1) : VORLAGEN));
    await page.route('**/api/textbausteine/*', (route) => {
        if (route.request().method() === 'DELETE') {
            loeschAufrufe.push(route.request().url());
            geloescht = true;
            return route.fulfill({ status: 204 });
        }
        return json(route, []);
    });

    await page.goto('/textbausteine');
    await expect(page.getByText('Guten Tag Max Mustermann,')).toBeVisible();

    const loeschen = page.getByRole('button', { name: 'Vorlage löschen', exact: true });
    await loeschen.click();

    const dialog = page.getByRole('dialog', { name: 'Textvorlage löschen?' });
    await expect(dialog).toContainText('„Anschreiben“ wird endgültig gelöscht.');
    await expect(dialog).toContainText('als Standard-Text hinterlegt');
    await designPruefung(page, testInfo, 'textvorlage-loeschen-rueckfrage', {
        primaerAktion: dialog.getByRole('button', { name: 'Löschen' }),
    });

    await dialog.getByRole('button', { name: 'Abbrechen' }).click();
    await expect(dialog).toBeHidden();
    expect(loeschAufrufe).toHaveLength(0);

    await loeschen.click();
    await dialog.getByRole('button', { name: 'Löschen' }).click();
    await expect(dialog).toBeHidden();
    await expect.poll(() => loeschAufrufe.length).toBe(1);
    expect(loeschAufrufe[0]).toContain('/api/textbausteine/1');

    // Sichtbares Ergebnis: Anschreiben ist weg, die Ansicht springt auf die verbliebene Vorlage.
    await expect(page.getByRole('button', { name: 'Vorlage „Anschreiben“ löschen' })).toHaveCount(0);
    await expect(page.getByRole('heading', { name: 'Mahnung', level: 3 })).toBeVisible();
    await expect(page.locator('.rich-text-einheitlich')).toHaveText('Sehr geehrte Damen und Herren,');

    await designPruefung(page, testInfo, 'textvorlage-nach-loeschen', {
        primaerAktion: page.getByRole('button', { name: 'Neue Vorlage' }),
    });
});
