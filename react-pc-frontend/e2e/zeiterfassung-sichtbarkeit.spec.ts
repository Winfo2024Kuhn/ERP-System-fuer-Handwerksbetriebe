import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { stubbeZeiterfassungSichtbarkeit } from './hilfen/api';
import { designPruefung } from './hilfen/design';
import { inhalt } from './hilfen/seite';

/**
 * Menü-Bereich „Zeiterfassung“ je Abteilung: Der Haken „Zeiterfassung im Menü
 * sehen“ in Einstellungen → Abteilungen & Rechte, der Reiter in der Menüleiste und
 * der Hinweis beim direkten Aufruf einer Seite ohne Recht.
 * Backend gestubbt, nur Dummy-Daten (Max Mustermann).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const ABTEILUNG_WERKSTATT = {
    abteilungId: 2, abteilungName: 'Werkstatt', berechtigungen: [],
    darfMonatAbschliessen: false, darfZeiterfassungSehen: true, darfTelefonSehen: false,
    darfRechnungenGenehmigen: false, darfRechnungenSehen: false, darfFreigabeAnnahmePushen: false, darfWebseitenAnfragenPushen: false,
};

/** Auffangnetz zuerst, gezielte Antworten danach (zuletzt registrierte Route gewinnt). */
async function stubbe(page: Page, optionen: { admin: boolean }) {
    const puts: Record<string, unknown>[] = [];
    await page.route('**/api/**', route => json(route, []));
    await page.route('**/api/auth/me', route => json(route, {
        id: 1, displayName: 'Max Mustermann', username: 'max', active: true,
        roles: optionen.admin ? ['ADMIN'] : [], admin: optionen.admin, requiresInitialSetup: false,
    }));
    await page.route('**/api/notifications/summary', route => json(route, { totalCount: 0, categories: [], recentItems: [] }));
    await page.route('**/api/abteilungen/berechtigungen', route => json(route, [ABTEILUNG_WERKSTATT]));
    await page.route('**/api/abteilungen/2/berechtigungen', route => {
        if (route.request().method() !== 'PUT') return route.fallback();
        puts.push(route.request().postDataJSON());
        return json(route, {});
    });
    return puts;
}

test('Haken „Zeiterfassung im Menü sehen“ wegnehmen, speichern und im Info-Popover nachlesen', async ({ page }, info) => {
    const puts = await stubbe(page, { admin: true });
    await page.goto('/einstellungen#berechtigungen');

    const haken = page.getByRole('checkbox', { name: /Zeiterfassung im Menü sehen/ });
    await expect(haken).toBeChecked();

    await page.getByRole('button', { name: 'Mehr zu: Zeiterfassung im Menü sehen' }).click();
    const popover = page.getByRole('dialog', { name: 'Zeiterfassung im Menü sehen' });
    await expect(popover.getByText('Was passiert im Hintergrund?')).toBeVisible();
    await expect(popover.getByText('Was bewirkt das?')).toBeVisible();
    await expect(popover.getByText(/Administratoren und die Geschäftsführung sehen ihn immer/)).toBeVisible();
    await expect(popover.getByText(/kranke Kollegen nur als „Abwesend“/)).toBeVisible();
    await designPruefung(page, info, 'zeiterfassung-sichtbarkeit-info');
    await page.keyboard.press('Escape');
    await expect(popover).toBeHidden();

    await haken.uncheck();
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Gespeichert', exact: true })).toBeVisible();
    expect(puts).toHaveLength(1);
    expect(puts[0]).toMatchObject({ darfZeiterfassungSehen: false, darfMonatAbschliessen: false });
    await designPruefung(page, info, 'zeiterfassung-sichtbarkeit-haken');
});

test('mit Recht: Reiter „Zeiterfassung“ ist im Menü und öffnet die Seiten', async ({ page }) => {
    await stubbe(page, { admin: false });
    await stubbeZeiterfassungSichtbarkeit(page, true);
    await page.goto('/projekte');
    await page.getByRole('button', { name: 'Zeiterfassung', exact: true }).click();
    await expect(page.getByRole('link', { name: /Urlaubsanträge/ })).toBeVisible();
    await page.goto('/urlaubsantraege');
    await expect(page.getByRole('heading', { name: /URLAUBSANTRÄGE/i })).toBeVisible();
    await expect(inhalt(page).getByText(/ausgeblendet/)).toHaveCount(0);
});

test('ohne Recht: kein Reiter „Zeiterfassung“, Direktaufruf zeigt freundlichen Hinweis', async ({ page }, info) => {
    await stubbe(page, { admin: false });
    await stubbeZeiterfassungSichtbarkeit(page, false);
    await page.goto('/projekte');
    await expect(page.getByRole('button', { name: 'Kommunikation', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Zeiterfassung', exact: true })).toHaveCount(0);

    await page.goto('/monatsabschluss');
    const hinweis = inhalt(page).getByRole('heading', { name: 'Die Zeiterfassung ist für deinen Zugang ausgeblendet.' });
    await expect(hinweis).toBeVisible();
    await expect(inhalt(page).getByText('Frag im Büro nach, wenn du sie brauchst.')).toBeVisible();
    await expect(page.getByRole('heading', { name: /MONATSABSCHLUSS/i })).toHaveCount(0);
    await designPruefung(page, info, 'zeiterfassung-sichtbarkeit-hinweis');
});
