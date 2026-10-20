import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { stubbeZeiterfassungSichtbarkeit } from './hilfen/api';
import { designPruefung } from './hilfen/design';

/**
 * Issue #269: Tageserfassung im Zeiterfassungs-Kalender.
 * - Verlauf einer gespeicherten Buchung in Klartext ansehen.
 * - „Ende vor Beginn“ wird markiert statt über Mitternacht gerechnet.
 * - Schließen mit ungespeicherten Änderungen fragt nach.
 *
 * DSGVO: nur Dummy-Daten (Max Mustermann). Ohne Backend, alles gestubbt.
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const buchung = {
    id: 1, projektId: 9, arbeitsgangId: 4, projektName: 'Treppe Musterweg', arbeitsgangName: 'Montage',
    startZeit: '08:00:00', endeZeit: '12:00:00', dauerMinuten: 240, dauerFormatiert: '4:00', notiz: '', typ: null,
};

async function stubbeApi(page: Page) {
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'anna.buero', vorname: 'Anna', nachname: 'Büro', admin: true, roles: ['ADMIN'], requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/mitarbeiter') return json(route, [{ id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true }]);
        if (pfad === '/api/projekte/simple') return json(route, [{ id: 9, bauvorhaben: 'Treppe Musterweg', auftragsnummer: 'A-26-001' }]);
        if (pfad === '/api/arbeitsgaenge') return json(route, [{ id: 4, beschreibung: 'Montage' }]);
        if (pfad.endsWith('/berechtigung')) return json(route, { darfMonatAbschliessen: true });
        if (pfad === '/api/zeitverwaltung/monatsabschluesse/1/2026/8') {
            return json(route, { mitarbeiterId: 1, jahr: 2026, monat: 8, festgeschrieben: false, version: 1, sollStunden: 8, gesamtIst: 4, differenz: -4, audit: [] });
        }
        if (pfad === '/api/zeitverwaltung/kalender') {
            return json(route, {
                jahr: 2026, monat: 8, sollStundenMonat: 8, istStundenMonat: 4, differenz: -4,
                tage: [{ datum: '2026-08-03', wochentag: 1, istFeiertag: false, feiertagName: null, sollStunden: 8, istStunden: 4, buchungen: [buchung] }],
            });
        }
        if (pfad === '/api/zeitverwaltung/mitarbeiter/1/saldo') {
            return json(route, { urlaub: { jahresanspruch: 30, genommen: 0, geplant: 0, verbleibend: 30 }, gesamt: { istStunden: 4, sollStunden: 8, saldo: -4 } });
        }
        if (pfad === '/api/zeitverwaltung/buchungen/1/historie') {
            return json(route, [
                { id: 2, version: 2, aktion: 'GEAENDERT', startZeit: '2026-08-03T08:00:00', endeZeit: '2026-08-03T12:00:00', anzahlInStunden: 4, notiz: null, geaendertVon: 'Anna Büro', geaendertAm: '2026-08-04T09:30:00', geaendertVia: 'DESKTOP', aenderungsgrund: 'Korrektur im Zeiterfassungskalender' },
                { id: 1, version: 1, aktion: 'ERSTELLT', startZeit: '2026-08-03T08:00:00', endeZeit: '2026-08-03T12:30:00', anzahlInStunden: 4.5, notiz: null, geaendertVon: 'Max Mustermann', geaendertAm: '2026-08-03T12:31:00', geaendertVia: 'MOBILE_APP', aenderungsgrund: null },
            ]);
        }
        return json(route, []);
    });
}

const zelle = (page: Page, nr: string) => page.locator('[class*="min-h-32"]').filter({ has: page.getByText(nr, { exact: true }) }).first();

test('Tageserfassung: Verlauf ansehen, Ende vor Beginn markieren, Schließen mit Rückfrage', async ({ page }, testInfo) => {
    await stubbeApi(page);
    await stubbeZeiterfassungSichtbarkeit(page);
    await page.goto('/zeitbuchungen?mitarbeiterId=1&jahr=2026&monat=8');

    // Erst wenn die Buchung im Raster steht, sind die Daten da.
    await expect(zelle(page, '3')).toContainText('4,0h');
    await zelle(page, '3').dblclick();
    const editor = page.getByRole('dialog', { name: 'Tageserfassung' });
    await expect(editor).toBeVisible();
    await expect(editor.getByRole('combobox', { name: 'Tätigkeit Buchung 1' })).toContainText('Montage');

    // Verlauf: neueste Änderung oben, alles in Klartext.
    await editor.getByRole('button', { name: 'Verlauf Buchung 1' }).click();
    const verlauf = page.getByRole('dialog', { name: 'Verlauf der Buchung' });
    const eintraege = verlauf.getByRole('listitem');
    await expect(eintraege).toHaveCount(2);
    await expect(eintraege.first()).toContainText('Geändert');
    await expect(eintraege.first()).toContainText('über Büro (PC)');
    await expect(eintraege.last()).toContainText('Angelegt');
    await expect(eintraege.last()).toContainText('über Handy-App');
    await expect(eintraege.last()).toContainText('4,50 Std.');
    await verlauf.getByRole('button', { name: 'Mehr zu: Verlauf' }).click();
    await expect(page.getByRole('dialog', { name: 'Verlauf', exact: true })).toContainText('nicht verändern oder löschen');
    await designPruefung(page, testInfo, 'tageserfassung-verlauf', { strengePruefungen: false });
    await page.keyboard.press('Escape');
    await page.keyboard.press('Escape');
    await expect(verlauf).toHaveCount(0);

    // Ende vor Beginn: Zeile markiert, keine Dauer über Mitternacht, Speichern gesperrt.
    const bis = editor.getByRole('textbox', { name: 'Bis Buchung 1' });
    await bis.fill('07:00');
    await expect(editor.getByText('Ende vor Beginn')).toBeVisible();
    await editor.getByRole('button', { name: 'Alle Speichern' }).click();
    await expect(page.getByText(/Buchungen über Mitternacht bitte auf zwei Tage aufteilen/)).toBeVisible();
    await designPruefung(page, testInfo, 'tageserfassung-ende-vor-beginn', { strengePruefungen: false });

    // Schließen mit ungespeicherter Änderung → Rückfrage; „Weiter bearbeiten“ behält den Stand.
    await editor.getByRole('button', { name: 'Schließen', exact: true }).click();
    const rueckfrage = page.getByRole('dialog', { name: 'Ungespeicherte Änderungen verwerfen?' });
    await expect(rueckfrage).toBeVisible();
    await rueckfrage.getByRole('button', { name: 'Weiter bearbeiten' }).click();
    await expect(rueckfrage).toHaveCount(0);
    await expect(bis).toHaveValue('07:00');

    // Über das X und „Verwerfen“ schließt die Tageserfassung.
    await editor.getByRole('button', { name: 'Tageserfassung schließen' }).click();
    await rueckfrage.getByRole('button', { name: 'Verwerfen' }).click();
    await expect(editor).toHaveCount(0);
});
