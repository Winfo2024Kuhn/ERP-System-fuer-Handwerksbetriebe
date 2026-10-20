import type { Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Artikel (#265): In „Wer liefert das?“ belegt „Preis ändern“ das Formular mit
 * Lieferant und dessen Nummer vor. Wer nur den Preis einträgt, schickt keine
 * neue Nummer – der Server behält die bisherige. Nur Dummy-Daten (DSGVO).
 */

const DETAIL = {
    artikel: {
        id: 1, artikelnummer: 'QR-40-2', produktname: 'Quadratrohr 40x40x2',
        werkstoffName: 'Baustahl S235JR', kategorieId: 12, kategoriePfad: 'Werkstoffe > Hohlprofile',
        preisHinweis: 'KEIN_AUFSCHLAG',
    },
    lieferanten: [
        {
            preisId: 10, lieferantId: 5, lieferantName: 'Musterfirma GmbH', externeArtikelnummer: 'MF-4711',
            preis: 10, preisDatum: '2026-09-01T00:00:00Z',
            quelle: { name: 'MANUELL', anzeigename: 'Von Hand eingetragen' }, guenstigster: true,
        },
    ],
    preisverlauf: [
        { preisId: 10, lieferantId: 5, lieferantName: 'Musterfirma GmbH', preis: 10, preisDatum: '2026-09-01T00:00:00Z', aktuell: true },
    ],
};

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

test('„Preis ändern“ in der Lieferantenzeile: nur Preis eintragen, Nummer bleibt', async ({ page }, testInfo) => {
    const posts: { pfad: string; body: unknown }[] = [];
    await page.route('**/api/**', (route) => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'max', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/artikel/1/detail') return json(route, DETAIL);
        if (pfad === '/api/lieferanten/5/artikelpreise' && request.method() === 'POST') {
            posts.push({ pfad, body: request.postDataJSON() });
            return json(route, {});
        }
        return json(route, []);
    });

    await page.goto('/artikel/1');
    await page.getByRole('button', { name: 'Preis ändern bei Musterfirma GmbH' }).click();

    await expect(page.getByRole('heading', { name: 'Preis ändern' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Lieferant' })).toHaveText('Musterfirma GmbH');
    await expect(page.getByLabel('Dessen Artikelnummer')).toHaveValue('MF-4711');
    const preis = page.getByLabel('Preis in Euro');
    await expect(preis).toBeFocused();
    await preis.fill('1.234,50');
    await designPruefung(page, testInfo, 'artikel-preis-aendern', { strengePruefungen: false });

    const antwort = page.waitForResponse((r) => r.url().endsWith('/api/lieferanten/5/artikelpreise'));
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    await antwort;

    expect(posts).toEqual([{ pfad: '/api/lieferanten/5/artikelpreise', body: { artikelId: 1, preis: 1234.5, externeArtikelnummer: null } }]);
    await expect(page.getByText('Preis gespeichert. Der bisherige Preis bleibt im Verlauf erhalten.')).toBeVisible();
});
