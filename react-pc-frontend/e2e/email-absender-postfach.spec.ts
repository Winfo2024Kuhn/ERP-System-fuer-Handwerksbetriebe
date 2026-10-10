import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeEmailCenter } from './hilfen/postfaecher';

/**
 * E-Mail-Center: Absender-Postfach beim Schreiben.
 * Neue Mail → Auswahl „Senden von“; Antwort/Weiterleiten → fester Absender
 * (das Postfach, in dem die Mail ankam). Nur Dummy-Daten, /api gestubbt.
 */

async function sendenBestaetigen(page: Page) {
    await page.getByRole('button', { name: 'E-Mail senden' }).click();
    // Ohne Projekt/Anfrage fragt das Formular einmal nach.
    const trotzdem = page.getByRole('button', { name: 'Trotzdem senden' });
    if (await trotzdem.isVisible().catch(() => false)) await trotzdem.click();
}

test('Neue Mail: „Senden von“ ist mit dem eigenen Postfach vorbelegt und schickt das gewählte postfachId', async ({ page }, info) => {
    const stub = await stubbeEmailCenter(page);
    await page.goto('/emails/inbox');
    await page.getByRole('button', { name: 'Neue E-Mail' }).click();

    const absender = page.getByRole('combobox', { name: 'Senden von' });
    await expect(absender).toContainText('Max Mustermann <max@musterbetrieb.example> – Ihr Postfach');
    await designPruefung(page, info, 'absender-postfach-neue-mail', { primaerAktion: page.getByRole('button', { name: 'E-Mail senden' }) });

    await absender.click();
    await page.getByRole('option', { name: 'rechnungen@musterbetrieb.example' }).click();
    await expect(absender).toContainText('rechnungen@musterbetrieb.example');

    await page.getByPlaceholder('Name, Firma oder E-Mail eingeben').fill('kunde@example.org');
    await page.getByLabel('Betreff').fill('Angebot Balkongeländer');
    await sendenBestaetigen(page);

    await expect.poll(() => stub.gesendet.length).toBe(1);
    expect(stub.gesendet[0].pfad).toBe('/api/emails/send');
    expect(stub.gesendet[0].dto).toMatchObject({ postfachId: 9, einzelversand: false, weitergeleitetVonEmailId: null });
    expect(stub.gesendet[0].dto).not.toHaveProperty('sender');
});

test('Antworten: Absender steht fest auf dem Postfach, in dem die Mail ankam', async ({ page }, info) => {
    const stub = await stubbeEmailCenter(page);
    await page.goto('/emails/inbox/701');
    await expect(page.getByRole('heading', { name: 'Anfrage Balkongeländer' })).toBeVisible();
    // Postfach-Schild im Detail: beide Postfächer der Mail.
    const schild = page.getByTestId('email-detail-header').getByTestId('postfach-schild');
    await expect(schild).toContainText('info@');
    await expect(schild).toContainText('max@');

    await page.getByRole('button', { name: 'Antworten', exact: true }).first().click();
    const fest = page.getByTestId('absender-fest');
    await expect(fest).toHaveText('Musterbetrieb <info@musterbetrieb.example>');
    await expect(page.getByRole('combobox', { name: 'Senden von' })).toHaveCount(0);
    await expect(page.getByText('Antworten gehen über das Postfach raus, in dem die Mail ankam – sonst über Ihr eigenes Postfach oder das Hauptpostfach.')).toBeVisible();
    await expect(page.getByLabel(/Einzeln verschicken/)).toHaveCount(0);
    await designPruefung(page, info, 'absender-postfach-antwort');

    await page.getByRole('button', { name: 'E-Mail senden' }).click();
    await expect.poll(() => stub.gesendet.length).toBe(1);
    expect(stub.gesendet[0].pfad).toBe('/api/emails/701/reply');
});

test('Weiterleiten: fester Absender und weitergeleitetVonEmailId im Versand', async ({ page }) => {
    const stub = await stubbeEmailCenter(page);
    await page.goto('/emails/inbox/701');
    await page.getByRole('button', { name: 'Weitere E-Mail-Aktionen' }).click();
    await page.getByRole('menuitem', { name: 'Weiterleiten' }).click();

    await expect(page.getByTestId('absender-fest')).toHaveText('Musterbetrieb <info@musterbetrieb.example>');
    await expect(page.getByText('Weiterleitungen gehen über das Postfach raus, in dem die Mail ankam – sonst über Ihr eigenes Postfach oder das Hauptpostfach.')).toBeVisible();
    await page.getByPlaceholder('Name, Firma oder E-Mail eingeben').fill('kollege@example.org');
    await sendenBestaetigen(page);

    await expect.poll(() => stub.gesendet.length).toBe(1);
    expect(stub.gesendet[0].pfad).toBe('/api/emails/send');
    expect(stub.gesendet[0].dto).toMatchObject({ weitergeleitetVonEmailId: 701, postfachId: null });
});

test('Liste: jede Mail trägt ein dezentes Postfach-Schild mit voller Adresse im Tooltip', async ({ page }, info) => {
    await stubbeEmailCenter(page);
    await page.goto('/emails/inbox');
    const zeile = page.getByText('Anfrage Balkongeländer').first();
    await expect(zeile).toBeVisible();
    const schild = page.getByTestId('postfach-schild').first();
    await expect(schild).toContainText('info@');
    await expect(schild.locator('[title="Musterbetrieb <info@musterbetrieb.example>"]')).toHaveCount(1);
    await designPruefung(page, info, 'postfach-schild-liste');
});
