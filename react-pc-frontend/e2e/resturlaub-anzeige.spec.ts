import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { stubbeZeiterfassungSichtbarkeit } from './hilfen/api';
import { designPruefung } from './hilfen/design';

/**
 * Issue #270, Punkt 4: Resturlaub aus dem Vorjahr erscheint getrennt – in der
 * Resturlaub-Kachel des Kalenders und bei offenen Urlaubsanträgen (Stand zum
 * ersten Urlaubstag). Ohne Backend, alle /api-Routen gestubbt.
 * DSGVO: nur Dummy-Daten (Max Mustermann).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const ANTRAG = {
    id: 1,
    mitarbeiterName: 'Max Mustermann',
    mitarbeiter: { id: 10, vorname: 'Max', nachname: 'Mustermann', jahresUrlaub: 30 },
    vonDatum: '2027-03-22',
    bisDatum: '2027-03-26',
    bemerkung: null,
    status: 'OFFEN',
    erstellDatum: '2027-02-01',
    typ: 'URLAUB',
    entschiedenAm: null,
    entscheidungGesehen: false,
};

async function stubbeApi(page: Page) {
    const resturlaubAbfragen: string[] = [];
    await page.route('**/api/**', (route) => {
        const url = new URL(route.request().url());
        const pfad = url.pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/urlaub/antraege') return json(route, [ANTRAG]);
        if (pfad === '/api/urlaub/resturlaub') {
            resturlaubAbfragen.push(url.search);
            return json(route, { verbleibend: 33.5, jahr: 2027, uebertragVorjahr: 3.5, ausVorjahrOffen: 3.5, verfallen: 0, verfallsdatum: '2027-03-31' });
        }
        if (pfad === '/api/langzeitkrankmeldungen/urlaubs-hinweise') return json(route, { warnungen: [] });
        if (pfad === '/api/mitarbeiter') return json(route, [{ id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true }]);
        if (pfad.endsWith('/berechtigung')) return json(route, { darfMonatAbschliessen: true });
        if (pfad === '/api/zeitverwaltung/kalender') {
            return json(route, { jahr: 2027, monat: 3, sollStundenMonat: 160, istStundenMonat: 0, differenz: 0, tage: [] });
        }
        if (pfad === '/api/zeitverwaltung/mitarbeiter/1/saldo') {
            return json(route, {
                urlaub: { jahresanspruch: 30, genommen: 2, geplant: 0, verbleibend: 31.5, uebertragVorjahr: 3.5, ausVorjahrOffen: 1.5, verfallen: 0, verfallsdatum: '2027-03-31' },
                gesamt: { istStunden: 100, sollStunden: 90, saldo: 10 },
            });
        }
        return json(route, []);
    });
    await stubbeZeiterfassungSichtbarkeit(page);
    return resturlaubAbfragen;
}

test('Urlaubsanträge: Resturlaub zum Urlaubsbeginn mit Rest aus dem Vorjahr', async ({ page }, testInfo) => {
    const abfragen = await stubbeApi(page);
    await page.goto('/urlaubsantraege');

    const karte = page.locator('[data-antrag-id="1"]');
    await expect(karte.getByText('Resturlaub zum Urlaubsbeginn:')).toBeVisible();
    await expect(karte.getByText('33,5 Tage')).toBeVisible();
    await expect(karte.getByText('(davon 3,5 Tage aus dem Vorjahr – bis 31.03.2027 nehmen, danach verfallen sie)')).toBeVisible();
    expect(abfragen).toEqual(['?mitarbeiterId=10&jahr=2027&stichtag=2027-03-22']);
    await designPruefung(page, testInfo, 'urlaubsantraege-resturlaub-vorjahr', {
        primaerAktion: karte.getByRole('button', { name: 'Genehmigen' }),
    });
});

test('Kalender: Resturlaub-Kachel zeigt den Übertrag aus dem Vorjahr', async ({ page }, testInfo) => {
    await stubbeApi(page);
    await page.goto('/zeitbuchungen?mitarbeiterId=1&jahr=2027&monat=3');

    await expect(page.getByText('31,5 Tage')).toBeVisible();
    await expect(page.getByText('davon 1,5 Tage aus dem Vorjahr – bis 31.03.2027 nehmen, danach verfallen sie')).toBeVisible();
    await expect(page.getByText('Aus 2026: 3,5')).toBeVisible();
    await designPruefung(page, testInfo, 'kalender-resturlaub-vorjahr');
});
