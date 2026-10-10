import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeEmailCenter } from './hilfen/postfaecher';

/**
 * Sammel-Mail einzeln verschicken: jeder Empfänger bekommt eine eigene Mail.
 * Nur Dummy-Daten (@example.org), /api gestubbt.
 */

const empfaenger = (anzahl: number) => Array.from({ length: anzahl }, (_, i) => `kunde${i + 1}@example.org`).join(', ');

async function neueMail(page: Page, an: string) {
    await page.goto('/emails/inbox');
    await page.getByRole('button', { name: 'Neue E-Mail' }).click();
    const anFeld = page.getByPlaceholder('Name, Firma oder E-Mail eingeben');
    await anFeld.fill(an);
    // Vorschlagsliste schließen – sie liegt sonst über dem Schalter darunter.
    await anFeld.press('Escape');
    await page.getByLabel('Betreff').click();
    await page.getByLabel('Betreff').fill('Betriebsurlaub vom 22.12. bis 02.01.');
}

test('30 Empfänger einzeln: CC weg, Teilfehler werden mit Grund gemeldet', async ({ page }, info) => {
    const stub = await stubbeEmailCenter(page, {
        sendeAntwort: {
            status: 200,
            body: {
                verschickt: 28,
                fehlgeschlagen: [
                    { adresse: 'kunde7@example.org', grund: 'Empfänger unbekannt' },
                    { adresse: 'kunde19@example.org', grund: 'Postfach voll' },
                ],
                emails: [],
            },
        },
    });
    await neueMail(page, empfaenger(30));

    await expect(page.getByText('30 Empfänger sehen sich gegenseitig.', { exact: false })).toBeVisible();
    await expect(page.getByRole('button', { name: '+ CC' })).toBeVisible();
    await page.getByLabel(/Einzeln verschicken/).check();
    await expect(page.getByText('30 Empfänger bekommen je eine eigene E-Mail.', { exact: false })).toBeVisible();
    await expect(page.getByRole('button', { name: '+ CC' })).toHaveCount(0);
    await designPruefung(page, info, 'einzelversand-an', { primaerAktion: page.getByRole('button', { name: 'E-Mail senden' }) });

    await page.getByRole('button', { name: 'E-Mail senden' }).click();
    await page.getByRole('button', { name: 'Trotzdem senden' }).click();

    const ergebnis = page.getByTestId('einzelversand-ergebnis');
    await expect(ergebnis).toContainText('28 verschickt, 2 nicht');
    await expect(ergebnis).toContainText('kunde7@example.org – Empfänger unbekannt');
    await expect(ergebnis).toContainText('kunde19@example.org – Postfach voll');
    await designPruefung(page, info, 'einzelversand-teilfehler', { primaerAktion: page.getByRole('button', { name: 'Fertig' }) });

    expect(stub.gesendet).toHaveLength(1);
    const dto = stub.gesendet[0].dto;
    expect(dto).toMatchObject({ einzelversand: true, cc: [], postfachId: 7 });
    expect(dto.recipients).toHaveLength(30);

    await page.getByRole('button', { name: 'Fertig' }).click();
    await expect(ergebnis).toHaveCount(0);
});

test('Mehr als 50 Empfänger: Senden gesperrt mit Hinweis', async ({ page }) => {
    await stubbeEmailCenter(page);
    await neueMail(page, empfaenger(51));
    await page.getByLabel(/Einzeln verschicken/).check();
    await expect(page.getByText('Höchstens 50 Empfänger pro Sammel-Mail. Eingetragen sind 51.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'E-Mail senden' })).toBeDisabled();
    await page.getByLabel(/Einzeln verschicken/).uncheck();
    await expect(page.getByRole('button', { name: 'E-Mail senden' })).toBeEnabled();
});

test('Alle fehlgeschlagen (502): Formular bleibt offen und nennt die Gründe', async ({ page }) => {
    await stubbeEmailCenter(page, {
        sendeAntwort: {
            status: 502,
            body: { message: 'Keine der E-Mails ging raus – Mail-Server nicht erreichbar.', fehlgeschlagen: [
                { adresse: 'kunde1@example.org', grund: 'Server nicht erreichbar' },
                { adresse: 'kunde2@example.org', grund: 'Server nicht erreichbar' },
            ] },
        },
    });
    await neueMail(page, empfaenger(2));
    await page.getByLabel(/Einzeln verschicken/).check();
    await page.getByRole('button', { name: 'E-Mail senden' }).click();
    await page.getByRole('button', { name: 'Trotzdem senden' }).click();

    const ergebnis = page.getByTestId('einzelversand-ergebnis');
    await expect(ergebnis).toContainText('0 verschickt, 2 nicht');
    await expect(page.getByText('Keine der E-Mails ging raus – Mail-Server nicht erreichbar.').first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'E-Mail senden' })).toBeEnabled();
});
