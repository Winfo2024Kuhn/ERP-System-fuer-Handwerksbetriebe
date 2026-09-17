import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * End-to-End-Test fuer die Vor-Kalkulation (Route /vorkalkulation-dummy).
 *
 * Deckt den Ablauf ab, den zwei Komponententests nicht sehen koennen:
 *  - Das Vollbild-Fenster oeffnet ueberhaupt aus der Leistungs-Karte heraus.
 *  - Der Artikel-Picker liegt VOR dem Vollbild-Fenster (Regression: als eigenes
 *    `fixed inset-0`-Overlay statt eines `Dialog` lag das Vollbild auf z-65,
 *    der Picker auf z-50 — er war unsichtbar, und Escape schloss die ganze
 *    Kalkulation statt nur den Picker).
 *  - Unvollstaendige Zahl -> Toast, und der Preis wird NICHT uebernommen.
 *  - Vollstaendige Kalkulation -> Preis landet an der Leistung.
 *  - Das Anfrage-Gate: im Projekt laesst sich nichts Neues anlegen.
 *
 * /api wird vollstaendig gestubbt (kein Backend). DSGVO: nur Dummy-Namen.
 */

function json(route: Route, body: unknown) {
    return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
}

const ARBEITSGAENGE = [
    { id: 600, beschreibung: 'Meister', abteilungId: 1, abteilungName: 'Werkstatt', stundensatz: 55, stundensatzJahr: 2026 },
    { id: 401, beschreibung: 'Montage', abteilungId: 1, abteilungName: 'Werkstatt', stundensatz: 50, stundensatzJahr: 2026 },
];

/** Ein Profil aus dem Materialstamm — Preis je laufendem Meter, nicht je Kilo. */
const ARTIKEL = {
    id: 42,
    produktname: 'Quadratrohr',
    abmessung: 'HQ 50x50x3',
    artikelnummer: 'HQ-50-50-3',
    verrechnungseinheit: { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' },
    guenstigsterPreis: 5.1,
    guenstigsterLieferantName: 'Dummy-Stahlhandel',
    kgProMeter: 4.25,
    mantelflaeche: 0.2,
    verzinkungsgeeignet: true,
    pulverbeschichtungsgeeignet: true,
    positionsEinheit: 'lfm',
    positionsEinzelpreis: 5.1,
};

async function stub(page: Page) {
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        if (pfad === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'max.mustermann', displayName: 'Max Mustermann',
                active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
            });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/arbeitsgaenge') return json(route, ARBEITSGAENGE);
        if (pfad === '/api/artikel') return json(route, { inhalt: [ARTIKEL], gesamt: 1, seite: 0, seitenGroesse: 20 });
        if (pfad === '/api/artikel/werkstoffe') return json(route, ['S235JR']);
        if (pfad === '/api/artikel/filteroptionen') {
            return json(route, { herstellverfahren: [], fertigungszustand: [], profilform: [] });
        }
        return json(route, []);
    });
}

/** Oeffnet die Vor-Kalkulation an der ersten Leistung der Anfrage. */
async function oeffneKalkulation(page: Page) {
    await page.goto('/vorkalkulation-dummy');
    await page.getByRole('button', { name: 'Vor-Kalkulation anlegen' }).first().click();
    await expect(page.getByRole('dialog')).toBeVisible();
}

/**
 * Springt zum letzten Reiter. Erst dort loest die Fussleiste "Preis
 * übernehmen" aus — davor steht dort "Weiter".
 */
async function geheZurGesamtkalkulation(page: Page) {
    await page.getByRole('button', { name: 'Gesamtkalkulation' }).click();
    await expect(page.getByRole('heading', { name: 'Was es kostet, was wir nehmen' })).toBeVisible();
}

test('Vor-Kalkulation rechnet Material und Arbeitszeit bis zum Verkaufspreis', async ({ page }, info) => {
    await stub(page);
    await oeffneKalkulation(page);

    // Material: Die Beispielzeilen stehen drin und sind durchgerechnet.
    await expect(page.getByRole('heading', { name: 'Was wird verbaut?' })).toBeVisible();
    await expect(page.getByText('Gesamtgewicht')).toBeVisible();
    await expect(page.getByText('92,85 kg', { exact: false }).first()).toBeVisible();
    await designPruefung(page, info, 'vorkalkulation-material');

    // Arbeitszeit: die sechs Zeilen der Mappe ergeben 5.584,90 EUR.
    await page.getByRole('button', { name: 'Weiter' }).click();
    await expect(page.getByRole('heading', { name: 'Wie viel Zeit steckt drin?' })).toBeVisible();
    await expect(page.getByText('5.584,90 €').first()).toBeVisible();
    await designPruefung(page, info, 'vorkalkulation-arbeitszeit');

    // Gesamtkalkulation: die Kette endet beim Verkaufspreis.
    await page.getByRole('button', { name: 'Weiter' }).click();
    await expect(page.getByRole('heading', { name: 'Was es kostet, was wir nehmen' })).toBeVisible();
    await expect(page.getByText('Was uns der Auftrag kostet')).toBeVisible();
    await expect(page.getByText('Was wir dafür nehmen')).toBeVisible();
    await designPruefung(page, info, 'vorkalkulation-gesamt', { ganzeSeite: true });
});

test('Artikel-Picker liegt vor dem Vollbild-Fenster und bleibt bedienbar', async ({ page }) => {
    await stub(page);
    await oeffneKalkulation(page);

    await page.getByRole('button', { name: 'Artikel aus Lager' }).click();

    // Der Picker ist sichtbar — nicht hinter dem Vollbild-Fenster begraben.
    const picker = page.getByRole('dialog').filter({ hasText: 'Artikel aus Lager hinzufügen' });
    await expect(picker).toBeVisible();
    await expect(picker.getByText('Suchen wie in der Materialverwaltung', { exact: false })).toBeVisible();

    // Und er liegt wirklich OBEN: der Punkt in seiner Mitte gehoert ihm.
    const kasten = await picker.boundingBox();
    expect(kasten).not.toBeNull();
    const gehoertZumPicker = await page.evaluate(({ x, y }) => {
        const getroffen = document.elementFromPoint(x, y);
        return !!getroffen?.closest('[role="dialog"]')?.textContent?.includes('Artikel aus Lager hinzufügen');
    }, { x: kasten!.x + kasten!.width / 2, y: kasten!.y + kasten!.height / 2 });
    expect(gehoertZumPicker, 'Der Picker muss vor dem Vollbild-Fenster liegen').toBe(true);

    // Escape schliesst NUR den Picker — die Kalkulation bleibt offen.
    await page.keyboard.press('Escape');
    await expect(picker).toBeHidden();
    await expect(page.getByRole('heading', { name: 'Was wird verbaut?' })).toBeVisible();
});

test('Unvollständige Zahl wird abgelehnt, statt still als Null zu rechnen', async ({ page }) => {
    await stub(page);
    await oeffneKalkulation(page);

    // Menge der ersten Materialzeile halb tippen.
    await page.getByLabel('Menge der Materialzeile 1').fill('12,');

    await geheZurGesamtkalkulation(page);
    await page.getByRole('button', { name: 'Preis übernehmen' }).click();

    await expect(page.getByText('Dezimalkomma', { exact: false })).toBeVisible();
    // Das Fenster bleibt offen, der Preis wurde nicht uebernommen.
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByText('kalkuliert', { exact: true })).toHaveCount(0);
});

test('Fertige Kalkulation schreibt den Preis in die Leistung', async ({ page }) => {
    await stub(page);
    await oeffneKalkulation(page);

    await geheZurGesamtkalkulation(page);
    await page.getByRole('button', { name: 'Preis übernehmen' }).click();

    // Fenster zu, Erfolgsmeldung, Preis und Kennzeichen an der Leistung.
    await expect(page.getByRole('dialog')).toBeHidden();
    await expect(page.getByText('Preis übernommen', { exact: false })).toBeVisible();
    await expect(page.getByText('kalkuliert', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Vor-Kalkulation', exact: true }).first()).toBeVisible();
});

test('Im Projekt lässt sich keine neue Vor-Kalkulation anlegen', async ({ page }, info) => {
    await stub(page);
    await page.goto('/vorkalkulation-dummy');
    await page.getByRole('button', { name: 'Dokument aus einem Projekt' }).click();

    // Position 1 hat eine Kalkulation aus der Anfrage -> weiter bearbeitbar.
    await expect(page.getByRole('button', { name: 'Vor-Kalkulation', exact: true })).toHaveCount(1);
    // Position 2 hat keine -> im Projekt gibt es keinen Knopf zum Anlegen.
    await expect(page.getByRole('button', { name: 'Vor-Kalkulation anlegen' })).toHaveCount(0);

    await designPruefung(page, info, 'vorkalkulation-projekt-gate');
});
