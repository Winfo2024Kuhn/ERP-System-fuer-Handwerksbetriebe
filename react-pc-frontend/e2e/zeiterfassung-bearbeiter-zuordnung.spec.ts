import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { blockiereFremdeNetzwerkzugriffe, stubbeZeiterfassungSichtbarkeit } from './hilfen/api';
import { designPruefung } from './hilfen/design';

/**
 * GoBD: Der Bearbeiter im Protokoll der Zeiterfassung kommt aus der Anmeldung. Ist dem
 * angemeldeten Benutzer kein Mitarbeiter zugeordnet, lehnt der Server Ändern/Stornieren
 * mit 403 ab – die Oberfläche zeigt genau diese Meldung (einmal) statt einer
 * allgemeinen Fehlermeldung, und sie schickt selbst keinen Bearbeiter mit.
 */
const KEIN_MITARBEITER = 'Ihr Benutzer ist keinem Mitarbeiter zugeordnet. '
    + 'Bitte einen Admin bitten, das oben rechts unter „Benutzer verwalten“ nachzuholen – '
    + 'sonst kann das nicht mit Ihrem Namen gespeichert werden.';

type Aufruf = { method: string; path: string; body: unknown };

async function stubbeApi(page: Page, aufrufe: Aufruf[]) {
    await blockiereFremdeNetzwerkzugriffe(page);
    await page.route('**/api/**', async route => {
        const req = route.request();
        const path = new URL(req.url()).pathname;
        const method = req.method();
        if (['POST', 'PUT', 'DELETE'].includes(method)) {
            aufrufe.push({ method, path, body: req.postDataJSON() });
        }

        // Ändern einer Buchung und Anlegen einer Korrektur: Server lehnt ab.
        const abgelehnt = (method === 'PUT' && path.startsWith('/api/zeitverwaltung/buchungen/'))
            || (method === 'POST' && path === '/api/zeitkonto/korrekturen');
        if (abgelehnt) {
            await route.fulfill({
                status: 403,
                contentType: 'application/json',
                body: JSON.stringify({ status: 403, message: KEIN_MITARBEITER }),
            });
            return;
        }

        let body: unknown = [];
        if (path === '/api/auth/me') {
            body = { id: 1, username: 'max.mustermann', vorname: 'Max', nachname: 'Mustermann', admin: false, roles: ['USER'], requiresInitialSetup: false };
        } else if (path === '/api/mitarbeiter') {
            body = [{ id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true }];
        } else if (path.endsWith('/berechtigung')) {
            body = { darfMonatAbschliessen: false };
        } else if (path === '/api/zeitverwaltung/monatsabschluesse/1/2026/8') {
            body = { mitarbeiterId: 1, jahr: 2026, monat: 8, festgeschrieben: false, version: 1, sollStunden: 8, gesamtIst: 8, differenz: 0, audit: [] };
        } else if (path === '/api/zeitverwaltung/kalender') {
            body = {
                jahr: 2026,
                monat: 8,
                sollStundenMonat: 8,
                istStundenMonat: 8,
                differenz: 0,
                tage: [{
                    datum: '2026-08-03',
                    wochentag: 1,
                    istFeiertag: false,
                    feiertagName: null,
                    sollStunden: 8,
                    istStunden: 8,
                    buchungen: [1, 2].map(id => ({
                        id,
                        projektId: 1,
                        arbeitsgangId: 1,
                        projektName: 'Testprojekt',
                        arbeitsgangName: 'Montage',
                        startZeit: id === 1 ? '08:00:00' : '12:30:00',
                        endeZeit: id === 1 ? '12:00:00' : '16:00:00',
                        dauerMinuten: 240,
                        dauerFormatiert: '4:00',
                        notiz: '',
                    })),
                }],
            };
        }
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    });
}

test('Kalender: Ändern ohne Mitarbeiter-Zuordnung zeigt den Hinweis des Servers genau einmal', async ({ page }, info) => {
    const aufrufe: Aufruf[] = [];
    await stubbeApi(page, aufrufe);

    await stubbeZeiterfassungSichtbarkeit(page);
    await page.goto('/zeitbuchungen?mitarbeiterId=1&jahr=2026&monat=8');
    await page.getByText('3', { exact: true }).dblclick();
    const modal = page.getByRole('dialog', { name: 'Tageserfassung' });
    await expect(modal).toBeVisible();

    await modal.getByRole('textbox', { name: 'Von Buchung 1' }).fill('08:15');
    await modal.getByRole('textbox', { name: 'Von Buchung 2' }).fill('12:45');
    await modal.getByRole('button', { name: 'Alle Speichern' }).click();

    const meldungen = page.getByRole('region', { name: 'Meldungen' });
    await expect(meldungen.getByText(KEIN_MITARBEITER)).toHaveCount(1);
    await expect(meldungen.getByText('Einige Buchungen konnten nicht gespeichert werden.')).toHaveCount(0);

    const puts = aufrufe.filter(a => a.method === 'PUT');
    expect(puts).toHaveLength(2);
    for (const put of puts) {
        expect(put.body).not.toHaveProperty('bearbeiterId');
    }

    await designPruefung(page, info, 'kalender-ohne-mitarbeiter-zuordnung');
});

test('Zeitkonto-Korrektur ohne Mitarbeiter-Zuordnung zeigt den Hinweis des Servers', async ({ page }, info) => {
    const aufrufe: Aufruf[] = [];
    await stubbeApi(page, aufrufe);

    await stubbeZeiterfassungSichtbarkeit(page);
    await page.goto('/zeitbuchungen?mitarbeiterId=1&jahr=2026&monat=8');
    await page.getByRole('button', { name: 'Korrekturen', exact: true }).click();
    const modal = page.getByRole('dialog', { name: 'Korrekturen verwalten' });
    await expect(modal).toBeVisible();

    await modal.getByRole('button', { name: 'Neue Korrektur' }).click();
    await modal.getByRole('textbox', { name: 'Anzahl Stunden' }).fill('2');
    await modal.getByRole('textbox', { name: 'Begründung' }).fill('Nachtrag Baustelle Mustermann');
    await modal.getByRole('button', { name: 'Speichern', exact: true }).click();

    await expect(page.getByRole('region', { name: 'Meldungen' }).getByText(KEIN_MITARBEITER)).toBeVisible();
    const post = aufrufe.find(a => a.method === 'POST' && a.path === '/api/zeitkonto/korrekturen');
    expect(post?.body).not.toHaveProperty('erstelltVonId');

    await designPruefung(page, info, 'korrektur-ohne-mitarbeiter-zuordnung');
});
