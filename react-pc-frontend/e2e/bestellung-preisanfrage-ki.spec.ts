import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';

/**
 * Preisanfrage an einen Lieferanten (Bestellungen → Bedarf → „E-Mail“):
 * „KI Formulierung“ schreibt den eigenen Text neu, die Signatur bleibt stehen,
 * ein Fehler der KI wird als Meldung gezeigt. Früher schickte der Dialog ein
 * falsches Feld an /api/email/beautify, und der Knopf blieb wirkungslos.
 * Nur Fantasienamen (DSGVO), /api vollständig gestubbt.
 */

const SIGNATUR = '<p>Viele Grüße<br>Max Mustermann</p>';

interface Mitschrift { beautify: { body: string; context: string | null }[] }

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubApi(page: Page, mitschrift: Mitschrift, beautify: { status?: number; body?: unknown }) {
    await page.route('**/api/**', (route) => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false, kiAktiv: true });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/bestellungen/offen') {
            return json(route, [{
                id: 1, artikelId: 10, produktname: 'Flachstahl 40x5', stueckzahl: 4, einheit: 'Stk',
                bestellt: false, lieferantId: 7, lieferantName: 'Mustermann Stahlhandel',
            }]);
        }
        if (pfad === '/api/lieferanten/7') return json(route, { id: 7, kundenEmails: ['anfrage@stahlhandel.example'] });
        if (pfad === '/api/email/signatures/default') return json(route, { html: SIGNATUR });
        if (pfad === '/api/email/beautify') {
            mitschrift.beautify.push(request.postDataJSON());
            return json(route, beautify.body ?? {}, beautify.status ?? 200);
        }
        return json(route, []);
    });
}

async function oeffnePreisanfrage(page: Page) {
    await page.goto('/bestellungen/bedarf');
    await page.getByRole('button', { name: /^E-Mail$/ }).click();
    const dialog = page.locator('[contenteditable]');
    await expect(dialog).toContainText('bitte erstellen Sie uns ein Angebot');
    await expect(dialog).toContainText('Max Mustermann');
    return dialog;
}

test.describe('Preisanfrage – KI Formulierung', () => {
    test('formuliert den eigenen Text neu, die Signatur bleibt stehen', async ({ page }, testInfo) => {
        const mitschrift: Mitschrift = { beautify: [] };
        await stubApi(page, mitschrift, { body: { body: 'Guten Tag,\n\nwir bitten um ein Angebot für die angehängten Positionen bis Freitag.' } });
        const editor = await oeffnePreisanfrage(page);

        await page.getByRole('button', { name: /KI Formulierung/ }).click();

        await expect(editor).toContainText('bis Freitag');
        await expect(editor).not.toContainText('bitte erstellen Sie uns ein Angebot');
        await expect(editor.locator('.email-signature')).toContainText('Max Mustermann');
        expect(mitschrift.beautify).toEqual([{
            body: 'Sehr geehrte Damen und Herren,\n\nbitte erstellen Sie uns ein Angebot für die angehängten Positionen.',
            context: 'Preisanfrage an den Lieferanten Mustermann Stahlhandel',
        }]);

        // Nur Screenshot statt designPruefung: der Dialog ist ein eigenes fixed-Overlay
        // ohne den gemeinsamen Dialog, die Seite dahinter zaehlt sonst als bedienbar.
        await testInfo.attach('preisanfrage-ki-formuliert', { body: await page.screenshot(), contentType: 'image/png' });
    });

    test('zeigt eine Meldung, wenn die KI nicht antwortet, und lässt den Text stehen', async ({ page }) => {
        await stubApi(page, { beautify: [] }, { status: 502 });
        const editor = await oeffnePreisanfrage(page);

        await page.getByRole('button', { name: /KI Formulierung/ }).click();

        await expect(page.getByText('Formulierung fehlgeschlagen. Bitte erneut versuchen.')).toBeVisible();
        await expect(editor).toContainText('bitte erstellen Sie uns ein Angebot');
    });
});
