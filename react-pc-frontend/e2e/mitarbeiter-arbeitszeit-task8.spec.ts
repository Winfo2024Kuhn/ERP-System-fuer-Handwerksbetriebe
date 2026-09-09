import { type Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

const ARBEITSZEIT = {
    montagStunden: 8, dienstagStunden: 8, mittwochStunden: 8, donnerstagStunden: 8,
    freitagStunden: 8, samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: null, buchungEndeZeit: null,
};

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

test('Task 8: Arbeitszeit bewusst einrichten und Vorschau prüfen', async ({ page }, testInfo) => {
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        const mitarbeiter = {
            id: 42, vorname: 'Max', nachname: 'Mustermann', strasse: null, plz: null, ort: null,
            email: 'max.mustermann@example.invalid', telefon: null, festnetz: null, qualifikation: null,
            stundenlohn: null, geburtstag: null, eintrittsdatum: '2026-01-01', aktiv: true,
            fuehrtZeitkonto: true, abteilungIds: [], abteilungNames: null, loginToken: null, jahresUrlaub: 30,
        };
        const zeitkonto = {
            mitarbeiterId: 42, mitarbeiterVersion: 7, mitarbeiterName: 'Max Mustermann', fuehrtZeitkonto: true,
            eingerichtet: false, hinweis: 'Bitte zuerst die Arbeitszeit einrichten.', aktuell: null, letzteVersion: null, historie: [],
        };
        if (pfad === '/api/auth/me') return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/mitarbeiter') return json(route, [mitarbeiter]);
        if (pfad === '/api/abteilungen') return json(route, []);
        if (pfad === '/api/zeitverwaltung/zeitkonten/42') return json(route, zeitkonto);
        if (pfad === '/api/zeitverwaltung/zeitkontenmodelle') return json(route, [{ id: 3, version: 2, bezeichnung: 'Vollzeit Werkstatt', arbeitszeit: ARBEITSZEIT }]);
        if (pfad === '/api/zeitverwaltung/zeitkonten/42/vorschau' && methode === 'POST') {
            return json(route, { zeitkonto, gueltigVon: '2026-09-09', gespeichert: false, bestehendeAbwesenheiten: 1, hinweis: 'Offene Monate werden neu gerechnet.', monate: [{ jahr: 2026, monat: 9, abgeschlossen: false, saldoVorher: 2, saldoNachher: null, geaendert: false }] });
        }
        return json(route, []);
    });

    await page.goto('/mitarbeiter');
    await page.getByText('Mustermann', { exact: true }).click();
    await page.getByRole('button', { name: 'Bearbeiten' }).click();
    await expect(page.getByText('Bitte zuerst die Arbeitszeit einrichten.')).toBeVisible();
    await page.getByRole('button', { name: 'Arbeitszeit einrichten' }).click();
    await page.getByText('Vorlage auswählen').click();
    await page.getByRole('option', { name: 'Vollzeit Werkstatt' }).click();
    await page.getByRole('button', { name: 'Vorschau anzeigen' }).click();
    await expect(page.getByText('Offene Monate werden neu gerechnet.')).toBeVisible();

    await designPruefung(page, testInfo, 'mitarbeiter-arbeitszeit-vorschau-task8', {
        primaerAktion: page.getByRole('button', { name: 'Jetzt übernehmen' }),
    });
});
