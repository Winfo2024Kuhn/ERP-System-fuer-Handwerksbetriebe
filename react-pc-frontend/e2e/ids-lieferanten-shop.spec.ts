import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { oeffneDokumentEditor, stubbeDokumentEditorApi } from './hilfen/dokument-editor';

/**
 * Lieferanten-Shops (IDS): Einrichtung in den System-Einstellungen und der
 * Ablauf „Aus Lieferanten-Shop“ im Dokumenteditor.
 *
 * Kein Backend und keine echte Außenverbindung: alle /api-Routen sind
 * gestubbt, der Shop-Tab wird abgefangen (das Formular an die Shop-Adresse
 * läuft in den Netz-Riegel aus hilfen/test.ts). Nur Dummy-Daten (DSGVO).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const ADMIN = { id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false };

const ZUGANG = {
    lieferantId: 9, lieferantName: 'Muster Großhandel GmbH', aktiv: true, protokoll: 'IDS_CONNECT_2_5',
    shopUrl: 'https://ids.muster-grosshandel.example/einstieg', kundennummer: '4711', benutzername: 'max.mustermann',
    passwortGesetzt: true, geaendertAm: '2026-10-31T09:00:00Z',
};

test.describe('Lieferanten-Shops – Einrichtung', () => {
    test('Admin legt einen Shop-Zugang an', async ({ page }, info) => {
        const gespeichert: { url: string; body: Record<string, unknown> }[] = [];
        let zugaenge: unknown[] = [];
        await page.route('**/api/**', async (route) => {
            const anfrage = route.request();
            const pfad = new URL(anfrage.url()).pathname;
            if (pfad === '/api/auth/me') return json(route, ADMIN);
            if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
            if (pfad === '/api/ids/einstellungen') return json(route, { aktiv: true, verschluesselungEingerichtet: true, zugaenge });
            if (pfad === '/api/ids/einstellungen/zugaenge/9' && anfrage.method() === 'PUT') {
                const body = anfrage.postDataJSON() as Record<string, unknown>;
                gespeichert.push({ url: pfad, body });
                zugaenge = [ZUGANG];
                return json(route, ZUGANG);
            }
            if (pfad === '/api/lieferanten') {
                return json(route, { lieferanten: [{ id: 9, lieferantenname: 'Muster Großhandel GmbH', ort: 'Musterstadt', istAktiv: true }], gesamt: 1 });
            }
            return json(route, {});
        });

        await page.goto('/einstellungen#ids');
        await expect(page.getByRole('tab', { name: /Lieferanten-Shops/ })).toHaveAttribute('aria-selected', 'true');
        await expect(page.getByText('Noch kein Shop-Zugang eingerichtet.')).toBeVisible();

        await page.getByRole('button', { name: 'Mehr zu: Lieferanten-Shops (IDS)' }).click();
        await expect(page.getByRole('dialog', { name: 'Lieferanten-Shops (IDS)' })).toContainText('Im Shop wird dabei nichts bestellt.');
        await designPruefung(page, info, 'ids-einstellungen-info');
        await page.keyboard.press('Escape');

        await page.getByRole('button', { name: 'Shop-Zugang hinzufügen' }).click();
        await page.getByRole('button', { name: 'Lieferant wählen' }).click();
        await page.getByText('Muster Großhandel GmbH').click();
        await page.getByRole('textbox', { name: 'Shop-Adresse' }).fill('https://ids.muster-grosshandel.example/einstieg');
        await page.getByLabel('Kundennummer').fill('4711');
        await page.getByLabel('Benutzername').fill('max.mustermann');
        await page.locator('#ids-passwort').fill('Test-Passwort-1.');
        await expect(page.getByRole('radio', { name: 'IDS-Connect 2.5 (Standard)' })).toHaveAttribute('aria-checked', 'true');
        await page.getByRole('radio', { name: 'Nur Zugangsfelder (wie Würth-Zugangsblatt)' }).click();
        await designPruefung(page, info, 'ids-einstellungen-formular');

        await page.getByRole('button', { name: 'Zugang speichern' }).click();
        await expect(page.getByText('Shop-Zugang für Muster Großhandel GmbH gespeichert.')).toBeVisible();
        const liste = page.getByRole('list', { name: 'Shop-Zugänge' });
        await expect(liste.getByText('Muster Großhandel GmbH')).toBeVisible();
        await expect(liste.getByText('gesetzt')).toBeVisible();
        expect(gespeichert).toEqual([{
            url: '/api/ids/einstellungen/zugaenge/9',
            body: {
                aktiv: true, protokoll: 'IDS_NUR_ZUGANGSFELDER', shopUrl: 'https://ids.muster-grosshandel.example/einstieg',
                kundennummer: '4711', benutzername: 'max.mustermann', passwort: 'Test-Passwort-1.',
            },
        }]);
    });
});

/**
 * Statt des echten Shops antwortet eine lokale Attrappe (nichts geht nach außen).
 * Auf dem Kontext registriert, damit auch der neue Tab sie sieht.
 */
async function stubbeUebungsShop(page: Page): Promise<void> {
    await page.context().route('https://ids.muster-grosshandel.example/**', (route) =>
        route.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: '<p>Übungs-Shop</p>' }));
}

/** Wartet, bis der Shop-Tab angemeldet im (Übungs-)Shop steht, und stubbt dort die Anmeldung fürs Programm. */
async function stubbeShopTab(shopTab: Page): Promise<void> {
    await expect(shopTab.getByText('Übungs-Shop')).toBeVisible();
    await shopTab.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        return pfad === '/api/auth/me'
            ? json(route, { ...ADMIN, admin: false, roles: ['USER'] })
            : json(route, {});
    });
}

const VORGANG = {
    vorgangId: 42, lieferantId: 9, lieferantName: 'Muster Großhandel GmbH', status: 'EMPFANGEN', fehlertext: null,
    mitBestellung: false, empfangenAm: '2026-10-31T10:00:00Z', standardAufschlagProzent: 20,
    positionen: [
        { index: 0, artikelnummer: '0892 100', bezeichnung: 'Silikon-Kartusche', langtext: null, menge: 5, einheit: 'Stk', einkaufspreis: 4,
            hinweis: null, aufschlagProzent: 25, aufschlagQuelle: 'ARTIKEL', stamm: { artikelId: 11, bezeichnung: 'Silikon', aktuellerPreis: 3.5 } },
        { index: 1, artikelnummer: '0903 200', bezeichnung: 'Injektionsmörtel', langtext: null, menge: 2, einheit: 'Stk', einkaufspreis: 10,
            hinweis: 'Nur solange Vorrat reicht', aufschlagProzent: 10, aufschlagQuelle: 'KATEGORIE', stamm: null },
        { index: 2, artikelnummer: '0157 610', bezeichnung: 'Schraube 6x100', langtext: null, menge: 100, einheit: 'Stk', einkaufspreis: null,
            hinweis: null, aufschlagProzent: null, aufschlagQuelle: 'ALLGEMEIN', stamm: null },
    ],
};

interface ShopStub {
    warenkorbDa: boolean;
    /** Für „Weitere Artikel aus dem Shop holen“: Ist der Warenkorb des zweiten Besuchs (43) schon da? */
    zweiterWarenkorbDa: boolean;
    artikelstammAufrufe: number;
    /** Was an /artikelstamm geschickt wurde. */
    artikelstammAuftraege: unknown[];
}

async function stubbeShop(
    page: Page, verfuegbar: unknown, vorgang: Record<string, unknown> = VORGANG, zweiterVorgang?: Record<string, unknown>,
): Promise<ShopStub> {
    const stub: ShopStub = { warenkorbDa: false, zweiterWarenkorbDa: false, artikelstammAufrufe: 0, artikelstammAuftraege: [] };
    let besuche = 0;
    const warten = (v: Record<string, unknown>) => ({ ...v, status: 'WARTET', positionen: [] });
    await page.route('**/api/ids/**', async (route) => {
        const anfrage = route.request();
        const pfad = new URL(anfrage.url()).pathname;
        if (pfad === '/api/ids/verfuegbar') return json(route, verfuegbar);
        if (pfad === '/api/ids/vorgaenge' && anfrage.method() === 'POST') {
            besuche += 1;
            return json(route, {
                vorgangId: besuche === 1 ? 42 : 43,
                formular: { action: 'https://ids.muster-grosshandel.example/einstieg', enctype: 'application/x-www-form-urlencoded', felder: { KND_NR: '4711', PW: 'Test-Passwort-1' } },
            }, 201);
        }
        if (pfad === '/api/ids/vorgaenge/42') {
            return json(route, stub.warenkorbDa ? vorgang : warten(vorgang));
        }
        if (pfad === '/api/ids/vorgaenge/43' && zweiterVorgang) {
            return json(route, stub.zweiterWarenkorbDa ? zweiterVorgang : warten(zweiterVorgang));
        }
        if (pfad === '/api/ids/vorgaenge/42/artikelstamm') {
            stub.artikelstammAufrufe += 1;
            stub.artikelstammAuftraege.push(anfrage.postDataJSON());
            return json(route, { aktualisiert: 0, angelegt: 0, unveraendert: 0, artikelIds: {} });
        }
        return json(route, {}, 404);
    });
    return stub;
}

/** Einstieg im Editor: „Material“ → „Aus Lieferanten-Shop“. */
async function oeffneShopUeberMaterial(page: Page): Promise<void> {
    await page.getByRole('button', { name: 'Material' }).click();
    await page.getByRole('menuitem', { name: /Aus Lieferanten-Shop/ }).click();
}

test.describe('Lieferanten-Shops – Dokumenteditor', () => {
    test('Warenkorb holen, Aufschlag setzen, als eine Position einfügen, Artikelstamm überspringen', async ({ page }, info) => {
        await stubbeDokumentEditorApi(page);
        const stub = await stubbeShop(page, { aktiv: true, lieferanten: [{ lieferantId: 9, lieferantName: 'Muster Großhandel GmbH', passwortGesetzt: true }] });
        await oeffneDokumentEditor(page);

        await page.getByRole('button', { name: 'Material' }).click();
        const menue = page.getByRole('menu');
        await expect(menue.getByRole('menuitem', { name: /Aus Artikelstamm/ })).toContainText('Artikel, die schon im Programm gepflegt sind');
        await expect(menue.getByRole('menuitem', { name: /Aus Lieferanten-Shop/ })).toContainText('Muster Großhandel GmbH – aktuelle Einkaufspreise holen');
        await designPruefung(page, info, 'ids-material-menue');
        await menue.getByRole('menuitem', { name: /Aus Lieferanten-Shop/ }).click();
        const dialog = page.getByRole('dialog', { name: /Aus Lieferanten-Shop übernehmen/ });
        await expect(dialog.getByRole('radio', { name: /Muster Großhandel GmbH/ })).toHaveAttribute('aria-checked', 'true');
        await designPruefung(page, info, 'ids-dialog-shop-waehlen', { primaerAktion: dialog.getByRole('button', { name: 'Shop öffnen' }) });

        await stubbeUebungsShop(page);
        const popupKommt = page.waitForEvent('popup');
        await dialog.getByRole('button', { name: 'Shop öffnen' }).click();
        const shopTab = await popupKommt;
        await expect(dialog.getByText(/ist in einem neuen Tab geöffnet/)).toBeVisible();
        await designPruefung(page, info, 'ids-dialog-warten');

        // Der Shop schickt den Warenkorb – der Server leitet den Tab auf die Rücksprung-Seite.
        await stubbeShopTab(shopTab);
        stub.warenkorbDa = true;
        await shopTab.goto('/ids/warenkorb-angekommen?vorgang=42&ergebnis=ok');
        await expect(shopTab.getByRole('heading', { name: 'Warenkorb übernommen' })).toBeVisible();
        await shopTab.close();

        // Preise prüfen
        await expect(dialog.getByText('Nur solange Vorrat reicht')).toBeVisible();
        await expect(dialog.getByText('Kein Preis vom Shop – bitte Einkaufspreis eintragen')).toBeVisible();
        await dialog.getByLabel('Einkaufspreis von „Schraube 6x100“').fill('0,2');
        await dialog.getByLabel('Aufschlag für alle (%)').fill('50');
        await dialog.getByRole('button', { name: 'Auf alle anwenden' }).click();
        await expect(dialog.getByLabel('Aufschlag von „Injektionsmörtel“')).toHaveValue('50');
        await dialog.getByRole('radio', { name: 'Alles in einer Position' }).click();
        // EK 20 + 20 + 20 = 60 €, VK mit 50 % = 90 €
        await expect(dialog.getByLabel('Vorschau der Position')).toContainText('Material · 1 pauschal · 90,00 €');
        await designPruefung(page, info, 'ids-dialog-preise', { primaerAktion: dialog.getByRole('button', { name: 'In Dokument einfügen' }) });
        await dialog.getByRole('button', { name: 'In Dokument einfügen' }).click();

        // Artikelstamm
        await expect(dialog.getByRole('checkbox', { name: 'Preis aktualisieren' })).toBeChecked();
        await expect(dialog.getByRole('button', { name: /^Als Artikel anlegen…/ })).toHaveCount(2);
        await designPruefung(page, info, 'ids-dialog-artikelstamm');
        await dialog.getByRole('button', { name: 'Überspringen' }).click();
        await expect(dialog).toBeHidden();

        await expect(page.locator('[data-verlauf-feld="title"]')).toHaveValue('Material');
        expect(stub.artikelstammAufrufe).toBe(0);
    });

    test('Artikel entfernen, weitere aus dem Shop holen und alle einfügen', async ({ page }, info) => {
        await stubbeDokumentEditorApi(page);
        const ersterBesuch = { ...VORGANG, positionen: VORGANG.positionen.slice(0, 2) };
        const zweiterBesuch = {
            ...VORGANG, vorgangId: 43, empfangenAm: '2026-10-31T10:20:00Z',
            positionen: [{ index: 0, artikelnummer: '0681 300', bezeichnung: 'Dübel 8 mm', langtext: null, menge: 50, einheit: 'Stk', einkaufspreis: 0.1,
                hinweis: null, aufschlagProzent: 40, aufschlagQuelle: 'KATEGORIE', stamm: null }],
        };
        const stub = await stubbeShop(page, { aktiv: true, lieferanten: [{ lieferantId: 9, lieferantName: 'Muster Großhandel GmbH', passwortGesetzt: true }] },
            ersterBesuch, zweiterBesuch);
        await oeffneDokumentEditor(page);
        await stubbeUebungsShop(page);

        await oeffneShopUeberMaterial(page);
        const dialog = page.getByRole('dialog', { name: /Aus Lieferanten-Shop übernehmen/ });
        const ersterTabKommt = page.waitForEvent('popup');
        await dialog.getByRole('button', { name: 'Shop öffnen' }).click();
        const ersterTab = await ersterTabKommt;
        await expect(dialog.getByText(/ist in einem neuen Tab geöffnet/)).toBeVisible();
        await stubbeShopTab(ersterTab);
        stub.warenkorbDa = true;
        await ersterTab.goto('/ids/warenkorb-angekommen?vorgang=42&ergebnis=ok');
        await ersterTab.close();

        // Zwei Artikel angekommen – den Mörtel aus der Liste nehmen.
        const bezeichnungen = dialog.getByRole('textbox', { name: /^Bezeichnung von Artikel/ });
        await expect(bezeichnungen).toHaveCount(2);
        await dialog.getByRole('row', { name: /0903 200/ }).getByRole('button', { name: 'Artikel aus der Liste entfernen' }).click();
        await expect(bezeichnungen).toHaveCount(1);
        const entferntMeldung = page.getByRole('status').filter({ hasText: '„Injektionsmörtel“ aus der Liste entfernt.' });
        await expect(entferntMeldung.getByRole('button', { name: 'Rückgängig' })).toBeVisible();
        await entferntMeldung.getByRole('button', { name: 'Meldung schließen' }).click();

        // Weitere Artikel holen: neuer Shop-Tab, Hinweis oben im Dialog.
        const zweiterTabKommt = page.waitForEvent('popup');
        await dialog.getByRole('button', { name: 'Weitere Artikel aus dem Shop holen', exact: true }).click();
        const zweiterTab = await zweiterTabKommt;
        const hinweis = dialog.getByRole('group', { name: 'Weiterer Shop-Besuch' });
        await expect(hinweis).toContainText('Der Shop von Muster Großhandel GmbH ist offen');
        await expect(dialog.getByRole('button', { name: 'In Dokument einfügen' })).toBeDisabled();
        await designPruefung(page, info, 'ids-dialog-weitere-artikel-warten');

        await stubbeShopTab(zweiterTab);
        stub.zweiterWarenkorbDa = true;
        await zweiterTab.goto('/ids/warenkorb-angekommen?vorgang=43&ergebnis=ok');
        await zweiterTab.close();

        await expect(page.getByText('1 weiterer Artikel aus dem Shop übernommen.')).toBeVisible();
        await expect(hinweis).toBeHidden();
        await expect(bezeichnungen).toHaveCount(2);
        await expect(bezeichnungen.nth(0)).toHaveValue('Silikon-Kartusche');
        await expect(bezeichnungen.nth(1)).toHaveValue('Dübel 8 mm');
        await dialog.getByRole('button', { name: 'In Dokument einfügen' }).click();

        await expect(dialog.getByRole('listitem').filter({ hasText: 'Artikelstamm' })).toHaveAttribute('aria-current', 'step');
        await dialog.getByRole('button', { name: 'Überspringen' }).click();
        await expect(dialog).toBeHidden();
        const titel = page.locator('[data-verlauf-feld="title"]');
        await expect(titel).toHaveCount(2);
        await expect(titel.nth(0)).toHaveValue('Silikon-Kartusche');
        await expect(titel.nth(1)).toHaveValue('Dübel 8 mm');
    });

    test('Unbekannten Artikel im gewohnten Anlegen-Fenster anlegen – vorausgefüllt, mit Kategorie', async ({ page }, info) => {
        await stubbeDokumentEditorApi(page);
        const angelegt: Record<string, unknown>[] = [];
        await page.route('**/api/artikel/werkstoffe/details', (route) => json(route, [{ id: 4, name: 'Kunststoff' }]));
        await page.route('**/api/artikel/naechste-artikelnummer', (route) => json(route, { artikelnummer: 'A-000501' }));
        await page.route('**/api/artikel/kategorien/haupt', (route) => json(route, [{ id: 3, bezeichnung: 'Dichtstoffe', leaf: true }]));
        await page.route('**/api/artikel', (route) => {
            if (route.request().method() !== 'POST') return route.fallback();
            angelegt.push(route.request().postDataJSON() as Record<string, unknown>);
            return json(route, { id: 501, artikelnummer: 'A-000501', produktname: 'Injektionsmörtel Vinylester 300 ml' });
        });
        const stub = await stubbeShop(page, { aktiv: true, lieferanten: [{ lieferantId: 9, lieferantName: 'Muster Großhandel GmbH', passwortGesetzt: true }] },
            { ...VORGANG, positionen: VORGANG.positionen.slice(0, 2) });
        await oeffneDokumentEditor(page);
        await stubbeUebungsShop(page);

        await oeffneShopUeberMaterial(page);
        const dialog = page.getByRole('dialog', { name: /Aus Lieferanten-Shop übernehmen/ });
        const popupKommt = page.waitForEvent('popup');
        await dialog.getByRole('button', { name: 'Shop öffnen' }).click();
        const shopTab = await popupKommt;
        await expect(dialog.getByText(/ist in einem neuen Tab geöffnet/)).toBeVisible();
        await stubbeShopTab(shopTab);
        stub.warenkorbDa = true;
        await shopTab.goto('/ids/warenkorb-angekommen?vorgang=42&ergebnis=ok');
        await shopTab.close();

        await expect(dialog.getByRole('textbox', { name: /^Bezeichnung von Artikel/ })).toHaveCount(2);
        await dialog.getByRole('button', { name: 'In Dokument einfügen' }).click();

        // Schritt 4: Silikon bekommt einen neuen Preis, der Mörtel ist unbekannt.
        await expect(dialog.getByRole('checkbox', { name: 'Preis aktualisieren' })).toBeChecked();
        await dialog.getByRole('button', { name: 'Als Artikel anlegen… („Injektionsmörtel“)' }).click();

        const fenster = page.getByRole('dialog', { name: 'Neuen Artikel anlegen' });
        await expect(fenster.getByRole('note')).toContainText('Vom Lieferanten-Shop übernommen.');
        const name = fenster.getByLabel('Produktname *');
        await expect(name).toHaveValue('Injektionsmörtel');
        await expect(name).toBeFocused();
        await designPruefung(page, info, 'ids-artikel-anlegen-vorbelegt', { primaerAktion: fenster.getByRole('button', { name: 'Artikel anlegen' }) });
        await name.fill('Injektionsmörtel Vinylester 300 ml');
        await fenster.getByRole('button', { name: 'Kategorie wählen' }).click();
        await page.getByText('Dichtstoffe', { exact: true }).click();
        await expect(fenster.getByRole('button', { name: 'Dichtstoffe' })).toBeVisible();
        await fenster.getByRole('button', { name: 'Artikel anlegen' }).click();

        await expect(fenster).toBeHidden();
        await expect(dialog.getByText('Angelegt als A-000501')).toBeVisible();
        await expect(dialog.getByRole('button', { name: /^Als Artikel anlegen…/ })).toHaveCount(0);
        expect(angelegt).toEqual([expect.objectContaining({
            produktname: 'Injektionsmörtel Vinylester 300 ml', externeArtikelnummer: '0903 200', lieferantId: 9,
            preis: 10, verrechnungseinheit: 'STUECK', preiseinheit: 'Stk', kategorieId: 3, preisQuelle: 'IDS_SHOP',
        })]);
        await designPruefung(page, info, 'ids-dialog-artikelstamm-angelegt');

        // „Übernehmen“ schickt nur noch die Preis-Aktualisierung.
        await dialog.getByRole('button', { name: 'Übernehmen' }).click();
        await expect(dialog).toBeHidden();
        expect(stub.artikelstammAufrufe).toBe(1);
        expect(stub.artikelstammAuftraege).toEqual([{ aktualisieren: [{ index: 0, einkaufspreis: 4 }], neuAnlegen: [] }]);
    });

    test('Rücksprung-Seite erklärt Fehlschläge; ein schon bestellter Warenkorb wird deutlich gewarnt', async ({ page }) => {
        await stubbeDokumentEditorApi(page);
        const stub = await stubbeShop(page, { aktiv: true, lieferanten: [{ lieferantId: 9, lieferantName: 'Muster Großhandel GmbH', passwortGesetzt: true }] },
            { ...VORGANG, mitBestellung: true });
        await oeffneDokumentEditor(page);

        await oeffneShopUeberMaterial(page);
        const dialog = page.getByRole('dialog', { name: /Aus Lieferanten-Shop übernehmen/ });
        await stubbeUebungsShop(page);
        const popupKommt = page.waitForEvent('popup');
        await dialog.getByRole('button', { name: 'Shop öffnen' }).click();
        const shopTab = await popupKommt;
        await expect(dialog.getByText(/ist in einem neuen Tab geöffnet/)).toBeVisible();
        await stubbeShopTab(shopTab);

        await shopTab.goto('/ids/warenkorb-angekommen?vorgang=42&ergebnis=abgelaufen');
        await expect(shopTab.getByRole('heading', { name: 'Der Shop-Besuch ist abgelaufen' })).toBeVisible();
        await expect(shopTab.getByRole('status')).toContainText('länger als 60 Minuten offen');

        await shopTab.goto('/ids/warenkorb-angekommen?vorgang=42&ergebnis=fehler');
        await expect(shopTab.getByRole('heading', { name: 'Das hat nicht geklappt' })).toBeVisible();
        await expect(shopTab.getByRole('status')).toContainText('Der Warenkorb konnte nicht gelesen werden. Bitte erneut versuchen.');
        // Ein Fehlschlag meldet nichts an den Dokument-Tab – dort wird weiter gewartet.
        await expect(dialog.getByText(/ist in einem neuen Tab geöffnet/)).toBeVisible();

        stub.warenkorbDa = true;
        await shopTab.goto('/ids/warenkorb-angekommen?vorgang=42&ergebnis=ok');
        await shopTab.close();
        await expect(dialog.getByRole('alert')).toContainText('Achtung: Dieser Warenkorb wurde im Shop bereits bestellt.');
        await expect(dialog.getByRole('alert')).toContainText('Nicht noch einmal bestellen.');
        await expect(dialog.getByText('Nur solange Vorrat reicht')).toBeVisible();
    });

    test('Ohne Lieferanten-Shops öffnet „Material“ direkt den Artikelstamm', async ({ page }) => {
        await stubbeDokumentEditorApi(page);
        await stubbeShop(page, { aktiv: false, lieferanten: [] });
        const verfuegbar = page.waitForResponse('**/api/ids/verfuegbar');
        await oeffneDokumentEditor(page);
        await verfuegbar;
        const material = page.getByRole('button', { name: 'Material' });
        await expect(material).not.toHaveAttribute('aria-haspopup', 'menu');
        await material.click();
        await expect(page.getByRole('menu')).toHaveCount(0);
        await expect(page.getByRole('dialog')).toBeVisible();
        await expect(page.getByText('Aus Lieferanten-Shop')).toHaveCount(0);
    });

    test('Lange Dokumentnummer wird gekürzt und überlappt die Knöpfe nicht', async ({ page }, info) => {
        await stubbeDokumentEditorApi(page, { dokument: { dokumentNummer: 'RE-2026/10/00031-NACHTRAG-BAUABSCHNITT-NORD-HALLE-2' } });
        await stubbeShop(page, { aktiv: true, lieferanten: [{ lieferantId: 9, lieferantName: 'Muster Großhandel GmbH', passwortGesetzt: true }] });
        await oeffneDokumentEditor(page);
        const titel = page.getByRole('heading', { name: /^RE-2026\/10\/00031/ });
        await expect(titel).toHaveAttribute('title', /^RE-2026\/10\/00031-NACHTRAG-BAUABSCHNITT-NORD-HALLE-2/);
        await expect(page.getByRole('button', { name: 'Material' })).toHaveAttribute('aria-haspopup', 'menu');
        // Titel endet links vor der ersten Knopfgruppe (Rückgängig bzw. Textbaustein).
        const titelBox = await titel.boundingBox();
        const ersterKnopf = page.getByRole('button', { name: 'Rückgängig' });
        const knopfBox = await ((await ersterKnopf.count()) > 0 ? ersterKnopf : page.getByRole('button', { name: 'Textbaustein' })).boundingBox();
        expect(titelBox && knopfBox && titelBox.x + titelBox.width < knopfBox.x).toBe(true);
        await designPruefung(page, info, 'ids-kopfleiste-lange-nummer');
    });

    test('Shop ohne Passwort: deutliche Warnung, öffnen bleibt möglich', async ({ page }, info) => {
        await stubbeDokumentEditorApi(page);
        await stubbeShop(page, { aktiv: true, lieferanten: [{ lieferantId: 9, lieferantName: 'Muster Großhandel GmbH', passwortGesetzt: false }] });
        await oeffneDokumentEditor(page);
        await oeffneShopUeberMaterial(page);
        const dialog = page.getByRole('dialog', { name: /Aus Lieferanten-Shop übernehmen/ });
        await expect(dialog.getByRole('note')).toContainText('Für Muster Großhandel GmbH ist kein Passwort gespeichert');
        // Kein Admin: kein Link in die Einstellungen.
        await expect(dialog.getByRole('link', { name: /Zugang in den Einstellungen ergänzen/ })).toHaveCount(0);
        await expect(dialog.getByRole('button', { name: 'Shop öffnen' })).toBeEnabled();
        await designPruefung(page, info, 'ids-dialog-ohne-passwort', { primaerAktion: dialog.getByRole('button', { name: 'Shop öffnen' }) });
    });
});
