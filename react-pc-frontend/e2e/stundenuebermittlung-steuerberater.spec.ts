import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test('Stundenübermittlung an den Steuerberater: Geschäftsführer wird ausgeblendet', async ({ page }, info) => {
    const dialogs: string[] = [];
    page.on('dialog', async dialog => { dialogs.push(dialog.type()); await dialog.dismiss(); });

    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        const path = url.pathname;

        if (path === '/api/auth/me') {
            await route.fulfill({
                json: { id: 1, username: 'test', email: 'test@example.com', vorname: 'Max', nachname: 'Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false }
            });
            return;
        }

        if (path === '/api/zeitverwaltung/zeitkonten') {
            const arbeitszeit = {
                montagStunden: 8, dienstagStunden: 8, mittwochStunden: 8, donnerstagStunden: 8, freitagStunden: 8,
                samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: '07:00', buchungEndeZeit: '16:00'
            };
            await route.fulfill({
                json: [
                    {
                        mitarbeiterId: 10,
                        mitarbeiterVersion: 1,
                        mitarbeiterName: 'Max Mustermann',
                        fuehrtZeitkonto: true,
                        istGeschaeftsfuehrer: false,
                        eingerichtet: true,
                        hinweis: null,
                        aktuell: { id: 1, version: 1, mitarbeiterId: 10, gueltigVon: '2026-01-01', gueltigBis: null, vorlageId: 1, arbeitszeit },
                        letzteVersion: null,
                        historie: []
                    },
                    {
                        mitarbeiterId: 20,
                        mitarbeiterVersion: 1,
                        mitarbeiterName: 'Chef Boss',
                        fuehrtZeitkonto: true,
                        istGeschaeftsfuehrer: true,
                        eingerichtet: true,
                        hinweis: null,
                        aktuell: { id: 2, version: 1, mitarbeiterId: 20, gueltigVon: '2026-01-01', gueltigBis: null, vorlageId: 1, arbeitszeit },
                        letzteVersion: null,
                        historie: []
                    }
                ]
            });
            return;
        }

        if (path === '/api/zeitverwaltung/kalender') {
            await route.fulfill({
                json: {
                    jahr: 2026,
                    monat: 9,
                    sollStundenMonat: 160,
                    istStundenMonat: 160,
                    tage: []
                }
            });
            return;
        }

        if (path === '/api/zeitverwaltung/monatsabschluesse/uebersicht') {
            await route.fulfill({
                json: {
                    items: [
                        { referenz: { mitarbeiterId: 10, jahr: 2026, monat: 9 }, festgeschrieben: true }
                    ],
                    auswahl: [
                        { mitarbeiterId: 10, festgeschrieben: true }
                    ]
                }
            });
            return;
        }

        if (path === '/api/firma/steuerberater') {
            await route.fulfill({ json: [] });
            return;
        }

        if (path === '/api/firma') {
            await route.fulfill({ json: { firmenname: 'Testbetrieb' } });
            return;
        }

        await route.fulfill({ json: {} });
    });

    await page.goto('/steuerberater');

    // Button "Vorschau laden" klicken
    const vorschauBtn = page.getByRole('button', { name: 'Vorschau laden', exact: true });
    await expect(vorschauBtn).toBeVisible();
    await vorschauBtn.click();

    // Nur der reguläre Mitarbeiter (Max Mustermann) soll erscheinen
    await expect(page.getByText('Max Mustermann')).toBeVisible();
    // Der Geschäftsführer (Chef Boss) darf NICHT angezeigt werden
    await expect(page.getByText('Chef Boss')).toHaveCount(0);

    // E-Mail-Button muss nur für 1 Mitarbeiter aktiv sein (nicht für 2)
    const emailBtn = page.getByRole('button', { name: /Per E-Mail senden/ });
    await expect(emailBtn).toBeVisible();
    await expect(emailBtn).toContainText('(1)');
    await expect(emailBtn).toBeEnabled();

    // Keine unerwarteten Dialoge
    expect(dialogs).toEqual([]);

    await designPruefung(page, info, 'stundenuebermittlung-ohne-geschaeftsfuehrer');
});
