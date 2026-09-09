import type { Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

const arbeitszeit = { montagStunden: 8, dienstagStunden: 8, mittwochStunden: 8, donnerstagStunden: 8, freitagStunden: 8, samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: '06:00', buchungEndeZeit: '18:00' };
const zeitkonto = { mitarbeiterId: 7, mitarbeiterVersion: 4, mitarbeiterName: 'Max Mustermann', fuehrtZeitkonto: true, eingerichtet: true, hinweis: null, aktuell: { id: 9, version: 2, mitarbeiterId: 7, gueltigVon: '2026-01-01', gueltigBis: null, vorlageId: 3, arbeitszeit }, letzteVersion: { id: 9, version: 2, mitarbeiterId: 7, gueltigVon: '2026-01-01', gueltigBis: null, vorlageId: 3, arbeitszeit }, historie: [] };
const modell = { id: 3, version: 6, bezeichnung: 'Werkstatt Vollzeit', arbeitszeit };
const ergebnis = { zeitkonto, gueltigVon: '2026-09-09', gespeichert: false, bestehendeAbwesenheiten: 0, hinweis: 'Offene Monate werden neu gerechnet.', monate: [{ jahr: 2026, monat: 8, abgeschlossen: true, saldoVorher: 12, saldoNachher: 12, geaendert: false }, { jahr: 2026, monat: 9, abgeschlossen: false, saldoVorher: 10, saldoNachher: 8, geaendert: true }] };
const json = (route: Route, body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

test('Zeitkonto: Vorschau vor der bewussten Übernahme', async ({ page }, testInfo) => {
    const calls: unknown[] = [];
    await page.route('**/api/auth/me', route => json(route, { id: 1, username: 'anna', vorname: 'Anna', nachname: 'Büro', admin: true, roles: ['ADMIN'], requiresInitialSetup: false }));
    await page.route('**/api/zeitverwaltung/zeitkontenmodelle**', route => {
        if (route.request().method() === 'GET') return json(route, [modell]);
        return json(route, modell);
    });
    await page.route('**/api/zeitverwaltung/zeitkonten**', route => {
        const url = new URL(route.request().url()); const method = route.request().method();
        if (url.pathname.includes('/zeitkontenmodelle')) return json(route, [modell]);
        if (url.pathname.endsWith('/vorschau')) { calls.push(route.request().postDataJSON()); return json(route, ergebnis); }
        if (url.pathname === '/api/zeitverwaltung/zeitkonten' && method === 'GET') return json(route, [zeitkonto]);
        return json(route, { ...ergebnis, gespeichert: true });
    });
    await page.goto('/zeitkonten');
    await expect(page.getByText('Max Mustermann')).toBeVisible();
    await page.getByRole('button', { name: 'Arbeitszeit ändern' }).click();
    await page.getByRole('button', { name: 'Vorschau laden' }).click();
    await expect(page.getByText('Abgeschlossen · unverändert')).toBeVisible();
    expect(calls).toHaveLength(1);
    expect(calls[0]).toMatchObject({ expectedMitarbeiterVersion: 4, expectedLetzteVersionId: 9, expectedLetzteVersion: 2, vorlageId: 3, expectedVorlageVersion: 6, arbeitszeit });
    await designPruefung(page, testInfo, 'zeitkonto-vorschau', { primaerAktion: page.getByRole('button', { name: 'Übernehmen' }) });
});

test('Zeitkonto: Vorlagenänderung wählt niemanden automatisch aus', async ({ page }, testInfo) => {
    await page.route('**/api/auth/me', route => json(route, { id: 1, username: 'anna', vorname: 'Anna', nachname: 'Büro', admin: true, roles: ['ADMIN'], requiresInitialSetup: false }));
    await page.route('**/api/zeitverwaltung/zeitkontenmodelle**', route => json(route, route.request().method() === 'GET' ? [modell] : modell));
    await page.route('**/api/zeitverwaltung/zeitkonten', route => json(route, [zeitkonto]));
    await page.goto('/zeitkonten');
    await page.getByRole('button', { name: 'Bearbeiten' }).click();
    await page.getByRole('button', { name: 'Speichern' }).click();
    await expect(page.getByText(/Vorlagenänderung übernehmen/)).toBeVisible();
    await expect(page.getByRole('checkbox', { name: 'Max Mustermann auswählen' })).not.toBeChecked();
    await expect(page.getByRole('button', { name: 'Für Auswahl übernehmen' })).toBeDisabled();
    await designPruefung(page, testInfo, 'vorlagen-uebernahme-ohne-auswahl');
});
