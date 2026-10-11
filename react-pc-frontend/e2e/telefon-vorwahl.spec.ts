import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';

/**
 * Reine Ortsvorwahl wird nicht als Telefonnummer gespeichert.
 *
 * Das Telefon-Feld (PhoneInput mit autoPrefillAreaCode) schlägt beim Eingeben von
 * PLZ/Ort die Ortsvorwahl vor ("0931 "). Wird das Feld danach nicht mehr angefasst,
 * hat es die Vorwahl früher mitgespeichert – die Handy-App zeigte sie dann als
 * anrufbare Nummer. Jetzt leeren Kunden- und Lieferanten-Formular eine reine Vorwahl
 * beim Speichern (lib/phoneUtils.ts → ohneReineVorwahl), eine vom Nutzer ergänzte
 * Nummer geht unverändert raus.
 *
 * /api vollständig gestubbt, kein Backend, nur Dummy-Daten (DSGVO).
 */

interface Mitschrift {
    gesendet: Record<string, unknown>[];
}

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubbeApi(page: Page): Promise<Mitschrift> {
    const mitschrift: Mitschrift = { gesendet: [] };
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();

        if (pfad === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'anna.buero', displayName: 'Anna Büro',
                active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
            });
        }
        if (pfad === '/api/notifications/summary') {
            return json(route, { totalCount: 0, categories: [], recentItems: [] });
        }
        if (pfad === '/api/kunden/duplikat-check') return json(route, { duplikate: [], harterTreffer: false });
        if (pfad === '/api/kunden/next-kundennummer') return json(route, { kundennummer: 'K-2001' });
        if (pfad === '/api/kunden' && methode === 'GET') return json(route, { kunden: [], gesamt: 0 });
        if (pfad === '/api/kunden' && methode === 'POST') {
            mitschrift.gesendet.push(route.request().postDataJSON());
            return json(route, { id: 77, kundennummer: 'K-2001', name: 'Max Mustermann' }, 201);
        }
        if (pfad === '/api/lieferanten' && methode === 'GET') return json(route, { lieferanten: [], gesamt: 0 });
        if (pfad === '/api/lieferanten' && methode === 'POST') {
            mitschrift.gesendet.push(route.request().postDataJSON());
            return json(route, { id: 88, lieferantenname: 'Stahlbau Mustermann' }, 201);
        }
        // Für diesen Ablauf irrelevante Endpunkte: leere Liste statt 404.
        return json(route, []);
    });
    return mitschrift;
}

test.describe('Telefon: vorgeschlagene Ortsvorwahl', () => {
    test('Neuer Kunde: unberührte Vorwahl wird nicht gespeichert', async ({ page }) => {
        const mitschrift = await stubbeApi(page);
        await page.goto('/kunden');
        await page.getByRole('button', { name: 'Neuer Kunde' }).click();

        await page.locator('#name').fill('Max Mustermann');
        await page.getByLabel('PLZ').fill('97070');
        await page.getByLabel('Ort').fill('Würzburg');
        const telefon = page.locator('#telefon');
        await expect(telefon).toHaveValue('0931 ');

        await page.getByRole('button', { name: 'Speichern', exact: true }).click();

        await expect.poll(() => mitschrift.gesendet.length).toBe(1);
        expect(mitschrift.gesendet[0].telefon).toBe('');
    });

    test('Neuer Kunde: ergänzte Nummer wird gespeichert', async ({ page }) => {
        const mitschrift = await stubbeApi(page);
        await page.goto('/kunden');
        await page.getByRole('button', { name: 'Neuer Kunde' }).click();

        await page.locator('#name').fill('Max Mustermann');
        await page.getByLabel('PLZ').fill('97070');
        await page.getByLabel('Ort').fill('Würzburg');
        const telefon = page.locator('#telefon');
        await expect(telefon).toHaveValue('0931 ');
        await telefon.fill('0931 123456');

        await page.getByRole('button', { name: 'Speichern', exact: true }).click();

        await expect.poll(() => mitschrift.gesendet.length).toBe(1);
        expect(mitschrift.gesendet[0].telefon).toBe('0931 123456');
    });

    test('Neuer Lieferant: unberührte Vorwahl wird nicht gespeichert', async ({ page }) => {
        const mitschrift = await stubbeApi(page);
        await page.goto('/lieferanten');
        await page.getByRole('button', { name: 'Neuer Lieferant' }).click();

        const dialog = page.locator('div.fixed.inset-0').filter({ hasText: 'Neuer Lieferant' });
        await dialog.locator('#lieferant-name').fill('Stahlbau Mustermann');
        await dialog.getByLabel('PLZ').fill('97070');
        await dialog.getByLabel('Ort').fill('Würzburg');
        const telefon = dialog.locator('input[type="tel"]').first();
        await expect(telefon).toHaveValue('0931 ');

        await dialog.getByRole('button', { name: 'Speichern', exact: true }).click();

        await expect.poll(() => mitschrift.gesendet.length).toBe(1);
        expect(mitschrift.gesendet[0].telefon).toBe('');
        expect(mitschrift.gesendet[0].lieferantenname).toBe('Stahlbau Mustermann');
    });
});
