import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeZeiterfassungSichtbarkeit } from './hilfen/api';

/**
 * Issue #222: Feiertage je Bundesland.
 * - Feiertage-Seite: halber Tag umschalten, eigenen Feiertag anlegen und löschen.
 * - Firmendaten: Bundesland-Vorschlag aus der PLZ übernehmen und speichern.
 *
 * /api vollständig gestubbt, fremde Hosts riegelt e2e/hilfen/test.ts ab. Dummy-Daten.
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const AUTH_ME = {
    id: 1, username: 'chefin', displayName: 'Erika Mustermann',
    active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
};

const heute = new Date();
const jahr = heute.getFullYear();
const monat = String(heute.getMonth() + 1).padStart(2, '0');

interface Feiertag { id: number; datum: string; bezeichnung: string; bundesland: string; halbTag: boolean; aktiv: boolean; eigener: boolean }

async function grundStubs(page: Page, firma: Record<string, unknown>) {
    // Zuerst der Auffang, danach die konkreten Antworten (die zuletzt registrierte Route gewinnt).
    await page.route('**/api/**', route => json(route, []));
    await page.route('**/api/auth/me', route => json(route, AUTH_ME));
    await page.route('**/api/notifications/summary', route => json(route, { totalCount: 0, categories: [], recentItems: [] }));
    await page.route('**/api/firma', route => {
        if (route.request().method() === 'PUT') return json(route, JSON.parse(route.request().postData() ?? '{}'));
        return json(route, firma);
    });
    await stubbeZeiterfassungSichtbarkeit(page);
}

test('Feiertage: halber Tag umschalten, eigenen Feiertag anlegen und wieder löschen', async ({ page }, testInfo) => {
    const liste: Feiertag[] = [
        { id: 1, datum: `${jahr}-01-01`, bezeichnung: 'Neujahr', bundesland: 'BY', halbTag: false, aktiv: true, eigener: false },
        { id: 2, datum: `${jahr}-08-15`, bezeichnung: 'Mariä Himmelfahrt', bundesland: 'BY', halbTag: false, aktiv: false, eigener: false },
        { id: 3, datum: `${jahr}-12-24`, bezeichnung: 'Heiligabend', bundesland: 'BY', halbTag: true, aktiv: true, eigener: true },
    ];
    const puts: unknown[] = [];
    const posts: unknown[] = [];
    const deletes: string[] = [];
    await grundStubs(page, { id: 1, firmenname: 'Mustermann Metallbau GmbH', bundesland: 'BY' });
    await page.route('**/api/zeitverwaltung/feiertage**', route => {
        const req = route.request();
        const url = new URL(req.url());
        const id = Number(url.pathname.match(/feiertage\/(\d+)$/)?.[1]);
        if (req.method() === 'GET' && url.pathname.endsWith('/feiertage')) return json(route, [...liste].sort((a, b) => a.datum.localeCompare(b.datum)));
        if (req.method() === 'PUT' && id) {
            const body = JSON.parse(req.postData() ?? '{}');
            puts.push(body);
            const f = liste.find(x => x.id === id)!;
            Object.assign(f, body);
            return json(route, f);
        }
        if (req.method() === 'POST' && url.pathname.endsWith('/feiertage')) {
            const body = JSON.parse(req.postData() ?? '{}');
            posts.push(body);
            const neu = { id: 10, bundesland: 'BY', aktiv: true, eigener: true, ...body };
            liste.push(neu);
            return json(route, neu);
        }
        if (req.method() === 'DELETE' && id) {
            deletes.push(url.pathname);
            liste.splice(liste.findIndex(x => x.id === id), 1);
            return route.fulfill({ status: 204 });
        }
        return json(route, { message: 'Unbekannt' }, 404);
    });

    await page.goto('/feiertage');
    await expect(page.getByRole('heading', { name: `Feiertage in Bayern ${jahr}` })).toBeVisible();
    const halbNeujahr = page.getByRole('switch', { name: 'Halber Tag: Neujahr' });
    await expect(halbNeujahr).toHaveAttribute('aria-checked', 'false');
    await expect(page.getByText('Abgeschaltet')).toBeVisible();

    // Info-Knopf „Gilt bei uns“ geöffnet prüfen (nichts abgeschnitten/überdeckt).
    await page.getByRole('button', { name: 'Mehr zu: Gilt bei uns' }).click();
    await expect(page.getByText(/Mariä Himmelfahrt oder Fronleichnam/)).toBeVisible();
    await designPruefung(page, testInfo, 'feiertage-info-gilt-bei-uns', { strengePruefungen: false });
    await page.keyboard.press('Escape');

    await designPruefung(page, testInfo, 'feiertage-liste', { primaerAktion: page.getByRole('button', { name: 'Eigenen Feiertag hinzufügen' }) });

    await halbNeujahr.click();
    await expect(halbNeujahr).toHaveAttribute('aria-checked', 'true');
    expect(puts).toEqual([{ halbTag: true, aktiv: true }]);

    await page.getByRole('button', { name: 'Eigenen Feiertag hinzufügen' }).click();
    const dialog = page.getByRole('dialog', { name: 'Eigenen Feiertag hinzufügen' });
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: 'Datum' }).click();
    await page.getByRole('button', { name: `02.${monat}.${jahr}` }).click();
    await dialog.getByRole('textbox', { name: 'Bezeichnung *' }).fill('Betriebsausflug');
    await dialog.getByRole('switch', { name: 'Halber Tag' }).click();
    await designPruefung(page, testInfo, 'feiertage-neu-dialog', { primaerAktion: dialog.getByRole('button', { name: 'Hinzufügen' }) });
    await dialog.getByRole('button', { name: 'Hinzufügen' }).click();
    await expect(dialog).toBeHidden();
    await expect(page.getByText('Betriebsausflug', { exact: true })).toBeVisible();
    expect(posts).toEqual([{ datum: `${jahr}-${monat}-02`, bezeichnung: 'Betriebsausflug', halbTag: true }]);

    await page.getByRole('button', { name: 'Betriebsausflug löschen' }).click();
    const rueckfrage = page.getByRole('dialog', { name: 'Eigenen Feiertag löschen' });
    await expect(rueckfrage).toBeVisible();
    await rueckfrage.getByRole('button', { name: 'Löschen', exact: true }).click();
    await expect(page.getByText('Betriebsausflug', { exact: true })).toBeHidden();
    expect(deletes).toEqual(['/api/zeitverwaltung/feiertage/10']);
});

test('Firmendaten: Bundesland aus der Postleitzahl vorschlagen, übernehmen und speichern', async ({ page }, testInfo) => {
    const puts: Record<string, unknown>[] = [];
    await grundStubs(page, { id: 1, firmenname: 'Mustermann Metallbau GmbH', plz: '', ort: 'Musterstadt', bundesland: 'NW', tageBisZahlungserinnerung: 7, tageBisErsteMahnung: 7, tageBisZweiteMahnung: 7, mahnverfahrenNeuesZahlungszielTage: 7 });
    await page.route('**/api/firma', route => {
        if (route.request().method() === 'PUT') {
            const body = JSON.parse(route.request().postData() ?? '{}');
            puts.push(body);
            return json(route, body);
        }
        return route.fallback();
    });
    await page.route('**/api/firma/feiertage-vorschau**', route => {
        const params = new URL(route.request().url()).searchParams;
        const land = params.get('bundesland') ?? 'BY';
        return json(route, {
            vorgeschlagenesBundesland: params.get('plz') === '97259' ? 'BY' : null,
            bundesland: land,
            bundeslandName: land === 'BY' ? 'Bayern' : 'Nordrhein-Westfalen',
            jahr,
            feiertage: [{ datum: `${jahr}-01-01`, bezeichnung: 'Neujahr' }, ...(land === 'BY' ? [{ datum: `${jahr}-01-06`, bezeichnung: 'Heilige Drei Könige' }] : [])],
            hinweise: ['Der Vorschlag richtet sich nach der Postleitzahl. Liegt euer Betrieb nahe einer Landesgrenze, bitte kurz prüfen.'],
        });
    });

    await page.goto('/firma');
    const auswahl = page.getByRole('combobox', { name: 'Bundesland' });
    await expect(auswahl).toHaveText(/Nordrhein-Westfalen/);
    await page.getByText('PLZ', { exact: true }).locator('xpath=following-sibling::input[1]').fill('97259');

    const hinweis = page.getByText('Laut Postleitzahl 97259 liegt euer Betrieb in Bayern.');
    await expect(hinweis).toBeVisible();
    await hinweis.scrollIntoViewIfNeeded();
    await designPruefung(page, testInfo, 'firma-bundesland-vorschlag', { strengePruefungen: false });
    await page.getByRole('button', { name: 'Übernehmen', exact: true }).click();
    await expect(auswahl).toHaveText(/Bayern/);
    await expect(page.getByText('passt zur Postleitzahl')).toBeVisible();

    const vorschau = page.getByRole('button', { name: `Feiertage ${jahr} in Bayern (2)` });
    await vorschau.click();
    await expect(page.getByText('Heilige Drei Könige')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Feiertage anpassen' })).toHaveAttribute('href', '/feiertage');
    await page.getByRole('link', { name: 'Feiertage anpassen' }).scrollIntoViewIfNeeded();
    await designPruefung(page, testInfo, 'firma-bundesland-vorschau', { strengePruefungen: false });

    await page.getByRole('button', { name: 'Speichern', exact: true }).first().click();
    await expect.poll(() => puts.length).toBe(1);
    expect(puts[0]).toMatchObject({ plz: '97259', bundesland: 'BY' });
});
