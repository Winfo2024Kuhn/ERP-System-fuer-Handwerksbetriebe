import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Artikel: alle Stammdaten ändern.
 * /api ist gestubbt (kein Backend). Nur Dummy-Daten (DSGVO).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

interface Mitschrift { stammdaten: Record<string, unknown>[] }

const ARTIKEL = {
    id: 1, version: 3, produktname: 'Rundrohr 42,4 x 2', artikelnummer: 'ST-RR-042', werkstoffId: 4, werkstoffName: 'Baustahl S235JR',
    kategorieId: 2, kategoriePfad: 'Werkstoffe > Hohlprofile > Rundrohr', massnorm: 'EN 10219-2', abmessung: '42,4 x 2', durchmesser: 42.4,
    wandstaerke: 2, standardlaenge: 6000, verpackungseinheit: 1, verrechnungseinheit: { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' },
    profilform: { name: 'RUNDROHR', anzeigename: 'Rundrohr' }, kurzbeschreibung: 'Handlauf Lager', beschreibung: '<p>Rundrohr aus Stahl</p>',
    verkaufsaufschlagProzent: 40, guenstigsterPreis: 5.75, preisHinweis: 'OK',
};


async function stubApi(page: Page, optionen: { doppelteNummer?: boolean } = {}): Promise<Mitschrift> {
    const mit: Mitschrift = { stammdaten: [] };
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        if (pfad === '/api/auth/me') return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/artikel/1/detail') return json(route, { artikel: ARTIKEL, lieferanten: [], preisverlauf: [] });
        if (pfad === '/api/artikel/filteroptionen') return json(route, { profilform: [{ wert: 'RUNDROHR', name: 'Rundrohr' }], herstellverfahren: [{ wert: 'GESCHWEISST', name: 'geschweisst' }], fertigungszustand: [] });
        if (pfad === '/api/artikel/werkstoffe/details') return json(route, [{ id: 4, name: 'Baustahl S235JR' }]);
        if (pfad === '/api/artikel/1/stammdaten' && methode === 'PUT') {
            const e = route.request().postDataJSON() as Record<string, unknown>;
            if (optionen.doppelteNummer && e.artikelnummer === 'ST-DOPPELT') return json(route, { message: 'Die Artikelnummer ST-DOPPELT gibt es schon bei einem anderen Artikel.' }, 409);
            mit.stammdaten.push(e);
            return json(route, { ...ARTIKEL, ...e, version: 4 });
        }
        return json(route, []);
    });
    return mit;
}

test('Artikelseite: Stammdaten ändern und Fehler vom Server verstehen', async ({ page }, testInfo) => {
    const mit = await stubApi(page, { doppelteNummer: true });
    await page.goto('/artikel/1');

    await page.getByRole('button', { name: 'Stammdaten bearbeiten' }).click();
    const form = page.getByTestId('artikel-stammdaten-form');
    await expect(form).toBeVisible();
    await expect(form.getByLabel('Produktname *')).toHaveValue('Rundrohr 42,4 x 2');
    await expect(form.getByLabel('Durchmesser (mm)')).toHaveValue('42,4');
    await designPruefung(page, testInfo, 'artikel-stammdaten', { strengePruefungen: false });

    // Info-Button an der Einheit: erklärt, was im Hintergrund passiert
    await form.getByRole('button', { name: /Mehr zu: Verrechnungseinheit/ }).click();
    await expect(page.getByText('Was passiert im Hintergrund?')).toBeVisible();
    await page.keyboard.press('Escape');

    // Eignung: Standard ist der Werkstoff; keine Meldung zu Lager oder Leistungen (gibt es in diesem Stand nicht)
    await expect(form.getByRole('combobox', { name: 'Feuerverzinken' })).toContainText('Vom Werkstoff übernehmen');
    await expect(form.getByRole('note')).toHaveCount(0);

    // doppelte Artikelnummer: klare Meldung, nichts gespeichert
    await form.getByLabel('Artikelnummer').fill('ST-DOPPELT');
    await form.getByRole('button', { name: /^Speichern/ }).click();
    await expect(form.getByRole('alert')).toContainText('gibt es schon bei einem anderen Artikel');
    expect(mit.stammdaten).toHaveLength(0);

    // gültig ändern
    await form.getByLabel('Produktname *').fill('Rundrohr 48,3 x 2,6');
    await form.getByLabel('Artikelnummer').fill('ST-RR-048');
    await form.getByLabel('EAN / Strichcode').fill('4006 3813 33931');
    await form.getByLabel('Durchmesser (mm)').fill('48,3');
    await form.getByRole('combobox', { name: 'Feuerverzinken' }).click();
    await page.getByRole('option', { name: 'Nicht geeignet' }).click();
    await form.getByRole('button', { name: /^Speichern/ }).click();

    await expect(page.getByText('Die Stammdaten sind gespeichert.')).toBeVisible();
    await expect(page.getByTestId('artikel-stammdaten-form')).toHaveCount(0);
    expect(mit.stammdaten).toHaveLength(1);
    expect(mit.stammdaten[0]).toMatchObject({
        version: 3, produktname: 'Rundrohr 48,3 x 2,6', artikelnummer: 'ST-RR-048', ean: '4006381333931', durchmesser: 48.3, stegdicke: null, werkstoffId: 4, verzinkungsgeeignet: false, pulverbeschichtungsgeeignet: null,
    });
});
