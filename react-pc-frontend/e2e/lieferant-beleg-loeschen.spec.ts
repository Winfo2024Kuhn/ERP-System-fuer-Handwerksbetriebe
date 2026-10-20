import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Lieferanten-Belege: Löschen mit eigenem Bestätigungsdialog, Klartext-Meldung
 * vom Server, wenn ein Beleg nicht gelöscht werden darf, und das Kennzeichen
 * „Ausgeblendet“ samt Erklärung. /api komplett gestubbt, nur Dummy-Daten.
 */

const GOBD_MELDUNG = 'Eine Rechnung kann nach GoBD nicht gelöscht werden. Ändere den Typ auf „Sonstiges“, wenn sie falsch erfasst wurde.';

function beleg(id: number, typ: string, nummer: string, extra: Record<string, unknown> = {}) {
    return {
        id, lieferantId: 7, lieferantName: 'Musterstahl GmbH', typ,
        originalDateiname: `${nummer}.pdf`, uploadDatum: '2026-09-01T10:00:00',
        geschaeftsdaten: { dokumentNummer: nummer, dokumentDatum: '2026-09-01', betragBrutto: 119 },
        projektAnteile: [], verknuepfteDokumente: [], ...extra,
    };
}

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function vorbereiten(page: Page) {
    const geloescht: number[] = [];
    await page.route('**/api/**', route => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        if (pfad === '/api/auth/me') return json(route, {
            id: 1, username: 'max.mustermann', displayName: 'Max Mustermann', active: true,
            roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
        });
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/lieferanten/7') return json(route, {
            id: 7, lieferantenname: 'Musterstahl GmbH', rollen: [], kundenEmails: [], dokumenteAnzahl: 3,
        });
        if (pfad === '/api/lieferanten/7/statistik') return json(route, { gesamtKosten: 0, bestellungAnzahl: 0, artikelAnzahl: 0, lieferzeit: 0 });
        if (pfad === '/api/lieferanten/7/dokumente') return json(route, [
            beleg(41, 'RECHNUNG', 'RE-MUSTER-41'),
            beleg(42, 'SONSTIG', 'SO-MUSTER-42'),
            beleg(43, 'LIEFERSCHEIN', 'LS-MUSTER-43', { ausgeblendet: true }),
        ].filter(d => !geloescht.includes(d.id)));
        if (request.method() === 'DELETE' && pfad === '/api/lieferant-dokumente/41') return json(route, { message: GOBD_MELDUNG }, 422);
        if (request.method() === 'DELETE' && pfad === '/api/lieferant-dokumente/42') {
            geloescht.push(42);
            return route.fulfill({ status: 204 });
        }
        return json(route, []);
    });
    await page.goto('/lieferanten?lieferantId=7&tab=dokumente');
    await expect(page.getByRole('heading', { name: 'SO-MUSTER-42' })).toBeVisible();
}

/** Karte eines Einzelbelegs anhand seiner Nummer. */
function karte(page: Page, nummer: string) {
    return page.locator('div.group').filter({ has: page.getByRole('heading', { name: nummer, exact: true }) });
}

test('Beleg löschen: Rückfrage, Abbrechen, Löschen und GoBD-Sperre mit Klartext', async ({ page }, info) => {
    await vorbereiten(page);

    // Kennzeichen „Ausgeblendet“ nur am ausgeblendeten Lieferschein, mit Erklärung
    const lieferschein = karte(page, 'LS-MUSTER-43');
    await expect(lieferschein.getByText('Ausgeblendet', { exact: true })).toBeVisible();
    await expect(karte(page, 'SO-MUSTER-42').getByText('Ausgeblendet', { exact: true })).toHaveCount(0);
    await lieferschein.getByRole('button', { name: 'Mehr zu: Ausgeblendet' }).click();
    await expect(page.getByText('Auf der Seite Bestellungen wurde dieser Beleg als erledigt ausgeblendet.')).toBeVisible();
    await designPruefung(page, info, 'lieferant-beleg-ausgeblendet-info', { strengePruefungen: false });
    await page.keyboard.press('Escape');

    // Abbrechen löscht nichts
    const sonstiges = karte(page, 'SO-MUSTER-42');
    await sonstiges.hover();
    await sonstiges.getByRole('button', { name: 'Beleg löschen' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText(/SO-MUSTER-42/)).toBeVisible();
    await designPruefung(page, info, 'lieferant-beleg-loeschen-dialog', {
        strengePruefungen: false,
        primaerAktion: dialog.getByRole('button', { name: 'Löschen' }),
    });
    await dialog.getByRole('button', { name: 'Abbrechen' }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByRole('heading', { name: 'SO-MUSTER-42' })).toBeVisible();

    // Bestätigen löscht
    await sonstiges.hover();
    await sonstiges.getByRole('button', { name: 'Beleg löschen' }).click();
    const loeschAntwort = page.waitForResponse(r => r.url().endsWith('/api/lieferant-dokumente/42') && r.request().method() === 'DELETE');
    await page.getByRole('dialog').getByRole('button', { name: 'Löschen' }).click();
    await loeschAntwort;
    await expect(page.getByRole('heading', { name: 'SO-MUSTER-42' })).toHaveCount(0);
    await expect(page.getByText('Beleg wurde gelöscht.')).toBeVisible();

    // Rechnung: Server lehnt ab, Begründung erscheint, Beleg bleibt
    const rechnung = karte(page, 'RE-MUSTER-41');
    await rechnung.hover();
    await rechnung.getByRole('button', { name: 'Beleg löschen' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Löschen' }).click();
    await expect(page.getByText(GOBD_MELDUNG)).toBeVisible();
    await expect(page.getByRole('heading', { name: 'RE-MUSTER-41' })).toBeVisible();
});
