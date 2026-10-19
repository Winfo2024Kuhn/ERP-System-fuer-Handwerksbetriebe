import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { json, stubbeEmailCenter } from './hilfen/postfaecher';

/**
 * Rundmail: mehrere Empfänger als Kärtchen, jeder bekommt eine eigene Mail.
 * Ab zwei Empfängern ist die Rundmail von selbst an.
 * Nur Dummy-Daten (@example.org), /api gestubbt.
 */

const empfaenger = (anzahl: number) => Array.from({ length: anzahl }, (_, i) => `kunde${i + 1}@example.org`).join(', ');

async function neueMail(page: Page, an: string) {
    await page.goto('/emails/inbox');
    await page.getByRole('button', { name: 'Neue E-Mail' }).click();
    const anFeld = page.getByRole('textbox', { name: 'Empfänger' });
    await anFeld.fill(an);
    // Vorschlagsliste schließen – sie liegt sonst über dem Schalter darunter.
    await anFeld.press('Escape');
    await page.getByLabel('Betreff').click();
    await page.getByLabel('Betreff').fill('Betriebsurlaub vom 22.12. bis 02.01.');
}

test('Empfänger per Suche und Plus-Knopf: Rundmail schaltet sich ab dem zweiten ein', async ({ page }, info) => {
    const stub = await stubbeEmailCenter(page, { sendeAntwort: { status: 200, body: { verschickt: 3, fehlgeschlagen: [], emails: [] } } });
    await page.route('**/api/emails/contacts**', route => json(route, [
        { id: 'KUNDE_1', name: 'Max Mustermann', email: 'max.mustermann@example.org', type: 'KUNDE', context: 'K-001' },
        { id: 'LIEFERANT_2', name: 'Muster Stahl GmbH', email: 'info@muster-stahl.example.org', type: 'LIEFERANT', context: 'Stahl' },
    ]));
    await page.goto('/emails/inbox');
    await page.getByRole('button', { name: 'Neue E-Mail' }).click();
    const anFeld = page.getByRole('textbox', { name: 'Empfänger' });
    const rundmail = page.getByLabel(/Als Rundmail verschicken/);

    await anFeld.fill('Muster');
    await page.locator('.email-recipient-dropdown').getByText('Max Mustermann', { exact: true }).click();
    await expect(page.getByTestId('empfaenger-kaertchen')).toHaveCount(1);
    await expect(rundmail).not.toBeChecked();

    // Zweiter Empfänger aus der Suche – der erste bleibt stehen.
    await anFeld.fill('Stahl');
    await page.locator('.email-recipient-dropdown').getByText('Muster Stahl GmbH', { exact: true }).click();
    // Kontakte aus der Suche stehen mit Namen auf dem Kärtchen.
    await expect(page.getByTestId('empfaenger-kaertchen')).toHaveText([/^Max Mustermann/, /^Muster Stahl GmbH/]);
    await expect(rundmail).toBeChecked();
    await expect(page.getByRole('button', { name: '+ CC' })).toHaveCount(0);

    // Dritter per Tippen und Plus-Knopf.
    await anFeld.fill('erika.musterfrau@example.org');
    await anFeld.press('Escape');
    await page.getByRole('button', { name: '„erika.musterfrau@example.org“ als Empfänger hinzufügen' }).click();
    await expect(page.getByTestId('empfaenger-kaertchen')).toHaveCount(3);
    await expect(page.getByText('3 Empfänger bekommen je eine eigene E-Mail.', { exact: false })).toBeVisible();
    await designPruefung(page, info, 'rundmail-kaertchen', { primaerAktion: page.getByRole('button', { name: 'E-Mail senden' }) });

    await page.getByRole('button', { name: 'Mehr zu: Rundmail' }).click();
    await expect(page.getByText('Niemand sieht die Adressen der anderen', { exact: false })).toBeVisible();
    await designPruefung(page, info, 'rundmail-info');
    await page.keyboard.press('Escape');

    await page.getByLabel('Betreff').fill('Betriebsurlaub');
    await page.getByRole('button', { name: 'E-Mail senden' }).click();
    await page.getByRole('button', { name: 'Trotzdem senden' }).click();
    await expect.poll(() => stub.gesendet.length).toBe(1);
    expect(stub.gesendet[0].dto).toMatchObject({
        einzelversand: true, cc: [],
        recipients: [
            '"Max Mustermann" <max.mustermann@example.org>',
            '"Muster Stahl GmbH" <info@muster-stahl.example.org>',
            'erika.musterfrau@example.org',
        ],
    });
});

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

    await expect(page.getByTestId('empfaenger-kaertchen')).toHaveCount(30);
    await expect(page.getByLabel(/Als Rundmail verschicken/)).toBeChecked();
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
    await expect(page.getByLabel(/Als Rundmail verschicken/)).toBeChecked();
    await expect(page.getByText('Höchstens 50 Empfänger pro Sammel-Mail. Eingetragen sind 51.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'E-Mail senden' })).toBeDisabled();
    await page.getByLabel(/Als Rundmail verschicken/).uncheck();
    await expect(page.getByText('Gemeinsame E-Mail: Alle 51 Empfänger sehen die Adressen der anderen.')).toBeVisible();
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
    await expect(page.getByLabel(/Als Rundmail verschicken/)).toBeChecked();
    await page.getByRole('button', { name: 'E-Mail senden' }).click();
    await page.getByRole('button', { name: 'Trotzdem senden' }).click();

    const ergebnis = page.getByTestId('einzelversand-ergebnis');
    await expect(ergebnis).toContainText('0 verschickt, 2 nicht');
    await expect(page.getByText('Keine der E-Mails ging raus – Mail-Server nicht erreichbar.').first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'E-Mail senden' })).toBeEnabled();
});
