import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { stubbeZeiterfassungSichtbarkeit } from './hilfen/api';
import { designPruefung } from './hilfen/design';

/** Report Centre: geführter Dialog „Neuer Bericht“ (nur Dummy-Daten, kein Backend). */

const ARBEITSZEIT = {
    montagStunden: 8, dienstagStunden: 8, mittwochStunden: 8, donnerstagStunden: 8, freitagStunden: 8,
    samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: '07:00', buchungEndeZeit: '16:00',
};

const konto = (mitarbeiterId: number, mitarbeiterName: string, istGeschaeftsfuehrer = false) => ({
    mitarbeiterId, mitarbeiterVersion: 1, mitarbeiterName, fuehrtZeitkonto: true, istGeschaeftsfuehrer, eingerichtet: true, hinweis: null,
    aktuell: { id: mitarbeiterId, version: 1, mitarbeiterId, gueltigVon: '2026-01-01', gueltigBis: null, vorlageId: 1, arbeitszeit: ARBEITSZEIT },
    letzteVersion: null, historie: [],
});

const zeile = (mitarbeiterId: number, name: string, teil: Record<string, unknown> = {}) => ({
    mitarbeiterId, name, tagessoll: 8, sollStunden: 176, arbeitsstunden: 152, urlaubStunden: 16, feiertagStunden: 8,
    krankheitStunden: 0, fortbildungStunden: 0, stundenkontoStand: 12.5, resturlaubTage: 7.5, festgeschrieben: true, ...teil,
});

const PDF = Buffer.from('%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n');

const pdf = (route: Route, dateiname: string) => route.fulfill({
    status: 200, body: PDF,
    headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': `attachment; filename="${dateiname}"` },
});

interface Mitschrift { vorschau: URL[]; pdf: URL[] }

async function stubbeApi(page: Page, stundenart: 'SOLL' | 'IST' = 'SOLL'): Promise<Mitschrift> {
    const mitschrift: Mitschrift = { vorschau: [], pdf: [] };
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        const pfad = url.pathname;
        if (pfad === '/api/auth/me') {
            return route.fulfill({ json: { id: 1, username: 'test', email: 'test@example.com', vorname: 'Max', nachname: 'Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false } });
        }
        if (pfad === '/api/zeitverwaltung/zeitkonten') {
            return route.fulfill({ json: [konto(10, 'Max Mustermann'), konto(11, 'Erika Musterfrau'), konto(20, 'Chef Musterchef', true)] });
        }
        if (pfad === '/api/zeitverwaltung/berichte/steuerberater') {
            mitschrift.vorschau.push(url);
            return route.fulfill({
                json: {
                    jahr: 2026, monat: 10, stundenart,
                    zeilen: [zeile(10, 'Max Mustermann'), zeile(11, 'Erika Musterfrau', { stundenkontoStand: -3.5, festgeschrieben: false })],
                    summe: { sollStunden: 352, arbeitsstunden: 304, urlaubStunden: 32, feiertagStunden: 16, krankheitStunden: 0, fortbildungStunden: 0 },
                    alleFestgeschrieben: false,
                },
            });
        }
        if (pfad === '/api/zeitverwaltung/berichte/steuerberater/pdf') { mitschrift.pdf.push(url); return pdf(route, 'Stundenaufstellung_2026-10.pdf'); }
        if (pfad === '/api/zeitverwaltung/berichte/mitarbeiter/pdf') { mitschrift.pdf.push(url); return pdf(route, 'Zeitbericht_2026-10-01_bis_2026-10-31.pdf'); }
        if (pfad === '/api/firma/steuerberater') {
            return route.fulfill({
                json: [{
                    id: 1, name: 'Kanzlei Mustermann', email: 'kanzlei@example.com', telefon: '', weitereEmails: [],
                    ansprechpartnerListe: [{ id: 2, anrede: 'FRAU', vorname: 'Erika', nachname: 'Musterfrau', email: 'lohn@example.com', telefon: '', istLohnAnsprechpartner: true }],
                }],
            });
        }
        if (pfad === '/api/firma') return route.fulfill({ json: { firmenname: 'Musterbetrieb' } });
        return route.fulfill({ json: {} });
    });
    await stubbeZeiterfassungSichtbarkeit(page);
    await page.clock.setFixedTime(new Date('2026-11-05T10:00:00'));
    return mitschrift;
}

test('Report Centre: Steuerberater-Bericht bis zur Vorschau und Mail mit PDF-Anhang', async ({ page }, info) => {
    const mitschrift = await stubbeApi(page);

    // Die alte Steuerberater-Seite leitet ins Report Centre um.
    await page.goto('/steuerberater');
    await expect(page).toHaveURL(/\/report-centre$/);
    await expect(page.getByRole('heading', { name: /Report Centre/i })).toBeVisible();
    await expect(page.getByText('Noch kein Bericht erstellt')).toBeVisible();
    const neu = page.getByRole('button', { name: 'Neuer Bericht' });
    await designPruefung(page, info, 'report-centre-leer', { primaerAktion: neu });

    await neu.click();
    const assistent = page.getByRole('dialog', { name: 'Neuer Bericht' });
    await expect(assistent.getByText('Was möchtest du machen?')).toBeVisible();
    await assistent.getByRole('button', { name: /An Steuerberater schicken/ }).click();
    await designPruefung(page, info, 'report-centre-auswahl');
    await assistent.getByRole('button', { name: 'Weiter' }).click();

    await expect(assistent.getByRole('checkbox', { name: 'Max Mustermann' })).toBeChecked();
    await expect(assistent.getByRole('checkbox', { name: 'Chef Musterchef' })).toHaveCount(0);
    await expect(assistent.getByRole('combobox', { name: 'Monat' })).toContainText('Oktober');
    await designPruefung(page, info, 'report-centre-steuerberater-filter');
    await assistent.getByRole('button', { name: 'Weiter' }).click();

    const erika = assistent.getByRole('row', { name: /Erika Musterfrau/ });
    await expect(erika).toContainText('−3,5');
    await expect(erika).toContainText('noch offen');
    await expect(assistent.getByText(/Für 1 Mitarbeiter ist der Monat noch nicht abgeschlossen/)).toBeVisible();
    // Alle gewählt: keine IDs, keine Stundenart – der Server nimmt alle und die Admin-Einstellung.
    expect(mitschrift.vorschau.at(-1)?.searchParams.has('mitarbeiterIds')).toBe(false);
    expect(mitschrift.vorschau.at(-1)?.searchParams.has('stundenart')).toBe(false);
    await expect(assistent.getByRole('columnheader', { name: 'Fortbildung' })).toBeVisible();
    await expect(assistent.getByText('Übermittelt werden: Sollstunden')).toBeVisible();
    await expect(assistent.getByRole('link', { name: 'Einstellungen → Zeiterfassung' })).toBeVisible();
    await assistent.getByRole('button', { name: 'Mehr zu: Arbeitsstunden (Soll)' }).click();
    await expect(page.getByRole('dialog', { name: 'Arbeitsstunden (Soll)' })).toContainText('Überstunden landen nicht im Lohn');
    await designPruefung(page, info, 'report-centre-steuerberater-vorschau-info');
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog', { name: 'Arbeitsstunden (Soll)' })).toHaveCount(0);
    await expect(assistent).toBeVisible();
    await designPruefung(page, info, 'report-centre-steuerberater-vorschau');

    await assistent.getByRole('button', { name: 'An Steuerberater senden' }).click();
    const mail = page.getByRole('dialog', { name: 'Stundenübermittlung' });
    await expect(mail.getByRole('button', { name: 'Anhang Stundenaufstellung_2026-10.pdf ansehen' })).toBeVisible();
    await expect(mail.getByRole('textbox', { name: 'Nachricht' })).toContainText('Stundenaufstellung (Sollstunden) unserer Mitarbeiter für den Monat Oktober 2026 als PDF');
    await expect(mail.getByText('Übermittelt werden: Sollstunden')).toBeVisible();
    await expect(mail.getByRole('button', { name: 'Senden' })).toBeEnabled();
    expect(mitschrift.pdf.at(-1)?.searchParams.get('mitStundenkonto')).toBe('true');
    expect(mitschrift.pdf.at(-1)?.searchParams.get('stundenart')).toBe('SOLL');
    await designPruefung(page, info, 'report-centre-steuerberater-mail');

    // Link-Dialog liegt über der Stundenübermittlung; Escape schließt nur ihn.
    const nachricht = mail.getByRole('textbox', { name: 'Nachricht' });
    await nachricht.click();
    await page.keyboard.press('Control+k');
    const linkDialog = page.getByRole('dialog', { name: 'Link einfügen' });
    await expect(linkDialog.getByRole('textbox', { name: 'Angezeigter Text' })).toBeFocused();
    await designPruefung(page, info, 'report-centre-steuerberater-link-dialog');
    await page.keyboard.press('Escape');
    await expect(linkDialog).toHaveCount(0);
    await expect(mail).toBeVisible();
    await expect(nachricht).toBeFocused();

    // Escape schließt nur den Mail-Dialog, der Assistent bleibt offen.
    await page.keyboard.press('Escape');
    await expect(mail).toHaveCount(0);
    await expect(assistent).toBeVisible();
});

test('Report Centre: Mitarbeiter-Bericht prüft die Auswahl und landet in „Zuletzt erstellt“', async ({ page }, info) => {
    const mitschrift = await stubbeApi(page);
    await page.goto('/report-centre');
    await page.getByRole('button', { name: 'Neuer Bericht' }).click();
    const assistent = page.getByRole('dialog', { name: 'Neuer Bericht' });
    await assistent.getByRole('button', { name: /Bericht für Mitarbeiter/ }).click();
    await assistent.getByRole('button', { name: 'Weiter' }).click();

    const max = assistent.getByRole('checkbox', { name: 'Max Mustermann' });
    await expect(max).not.toBeChecked();
    await assistent.getByRole('button', { name: 'Weiter' }).click();
    await expect(assistent.getByRole('alert')).toHaveText('Bitte mindestens einen Mitarbeiter wählen.');
    await max.check();
    await assistent.getByRole('button', { name: 'Mehr zu: Unterschriftszeile' }).click();
    await expect(page.getByRole('dialog', { name: 'Unterschriftszeile' })).toBeVisible();
    await designPruefung(page, info, 'report-centre-mitarbeiter-filter');
    await page.keyboard.press('Escape');
    await assistent.getByRole('button', { name: 'Weiter' }).click();

    await expect(assistent.getByText('Oktober 2026')).toBeVisible();
    const download = page.waitForEvent('download');
    await assistent.getByRole('button', { name: 'PDF erstellen' }).click();
    expect((await download).suggestedFilename()).toBe('Zeitbericht_2026-10-01_bis_2026-10-31.pdf');
    expect(Object.fromEntries(mitschrift.pdf.at(-1)!.searchParams)).toEqual({
        mitarbeiterIds: '10', von: '2026-10-01', bis: '2026-10-31', mitTagesliste: 'true', mitUnterschrift: 'true',
    });

    await page.keyboard.press('Escape');
    await expect(assistent).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Zuletzt erstellt' }).getByText('Zeitbericht_2026-10-01_bis_2026-10-31.pdf', { exact: true })).toBeVisible();
    await designPruefung(page, info, 'report-centre-zuletzt-erstellt');
});

test('Report Centre: Steuerberater mit Iststunden – Vorschau und PDF zeigen dieselbe Stundenart', async ({ page }) => {
    const mitschrift = await stubbeApi(page, 'IST');
    await page.goto('/report-centre');
    await page.getByRole('button', { name: 'Neuer Bericht' }).click();
    const assistent = page.getByRole('dialog', { name: 'Neuer Bericht' });
    await assistent.getByRole('button', { name: /An Steuerberater schicken/ }).click();
    await assistent.getByRole('button', { name: 'Weiter' }).click();
    await expect(assistent.getByRole('checkbox', { name: 'Max Mustermann' })).toBeChecked();
    await assistent.getByRole('button', { name: 'Weiter' }).click();

    await expect(assistent.getByText('Übermittelt werden: Iststunden')).toBeVisible();
    await expect(assistent.getByRole('columnheader', { name: /Arbeitsstunden \(Ist\)/ })).toBeVisible();
    const download = page.waitForEvent('download');
    await assistent.getByRole('button', { name: 'PDF herunterladen' }).click();
    expect((await download).suggestedFilename()).toBe('Stundenaufstellung_2026-10.pdf');
    expect(mitschrift.pdf.at(-1)?.searchParams.get('stundenart')).toBe('IST');
});
