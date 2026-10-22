import type { Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Artikel: Im Reiter „Preise & Lieferanten“ wird der Preis direkt in der
 * Tabellenzeile geändert – anklicken, neuen Preis tippen, Enter. Die Nummer
 * beim Lieferanten bleibt dabei, wie sie ist (`externeArtikelnummer: null`).
 * Nur Dummy-Daten (DSGVO).
 */

const ARTIKEL = {
    id: 1, artikelnummer: 'QR-40-2', produktname: 'Quadratrohr 40x40x2',
    werkstoffName: 'Baustahl S235JR', kategorieId: 12, kategoriePfad: 'Werkstoffe > Hohlprofile',
    preisHinweis: 'KEIN_AUFSCHLAG',
};

function detail(preis: number) {
    return {
        artikel: ARTIKEL,
        lieferanten: [
            {
                preisId: preis === 10 ? 10 : 11, lieferantId: 5, lieferantName: 'Musterfirma GmbH', externeArtikelnummer: 'MF-4711',
                preis, preisDatum: '2026-09-01T00:00:00Z',
                quelle: { name: 'MANUELL', anzeigename: 'Von Hand eingetragen' }, guenstigster: true,
            },
        ],
        preisverlauf: [
            { preisId: 10, lieferantId: 5, lieferantName: 'Musterfirma GmbH', preis: 10, preisDatum: '2026-09-01T00:00:00Z', aktuell: preis === 10 },
        ],
    };
}

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

test('Preis direkt in der Zeile ändern: Enter speichert, die Nummer bleibt', async ({ page }, testInfo) => {
    const posts: { pfad: string; body: unknown }[] = [];
    let preis = 10;
    await page.route('**/api/**', (route) => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'max', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/artikel/1/detail') return json(route, detail(preis));
        if (pfad === '/api/lieferanten/5/artikelpreise' && request.method() === 'POST') {
            const body = request.postDataJSON() as { preis: number };
            posts.push({ pfad, body });
            preis = body.preis;
            return json(route, {});
        }
        return json(route, []);
    });

    await page.goto('/artikel/1#preise');
    await expect(page.getByRole('tab', { name: /Preise & Lieferanten/ })).toHaveAttribute('aria-selected', 'true');

    // Info-Button an der Überschrift erklärt, was mit dem alten Preis passiert
    await page.getByRole('button', { name: 'Mehr zu: Preis ändern' }).click();
    await expect(page.getByText(/bleibt im Preisverlauf stehen/)).toBeVisible();
    await designPruefung(page, testInfo, 'artikel-preis-info');
    await page.getByRole('button', { name: 'Mehr zu: Preis ändern' }).click();

    await page.getByRole('button', { name: /^Preis ändern bei Musterfirma GmbH/ }).click();
    const feld = page.getByLabel('Neuer Preis in Euro bei Musterfirma GmbH');
    await expect(feld).toBeFocused();
    await expect(feld).toHaveValue('10');
    await feld.fill('1.234,50');
    await designPruefung(page, testInfo, 'artikel-preis-aendern');

    const antwort = page.waitForResponse((r) => r.url().endsWith('/api/lieferanten/5/artikelpreise'));
    await feld.press('Enter');
    await antwort;

    expect(posts).toEqual([{ pfad: '/api/lieferanten/5/artikelpreise', body: { artikelId: 1, preis: 1234.5, externeArtikelnummer: null } }]);
    await expect(page.getByText('Preis gespeichert. Der bisherige Preis bleibt im Verlauf erhalten.')).toBeVisible();
    // Die Zeile zeigt den neuen Preis, das Feld ist wieder zu, der Fokus steht auf dem Preis
    await expect(page.getByRole('button', { name: /^Preis ändern bei Musterfirma GmbH, bisher 1\.234,50/ })).toBeFocused();
    await expect(feld).toHaveCount(0);

    // Escape verwirft eine angefangene Eingabe, ohne zu speichern
    await page.getByRole('button', { name: /^Preis ändern bei Musterfirma GmbH/ }).click();
    await feld.fill('5');
    await feld.press('Escape');
    await expect(feld).toHaveCount(0);
    expect(posts).toHaveLength(1);
});
