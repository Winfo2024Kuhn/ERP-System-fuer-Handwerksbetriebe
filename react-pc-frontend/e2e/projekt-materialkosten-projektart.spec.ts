import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';

/**
 * Zwei Fehler auf der Projektseite, die Daten überschrieben haben:
 * - „Kosten erfassen“ ersetzte die ganze Liste manueller Materialkosten durch
 *   den einen neuen Eintrag (und lud danach die ganze Seite neu).
 * - „Bearbeiten“ setzte die Projektart still auf „Pauschalpreis“ zurück.
 * /api ist komplett gestubbt, nur Dummy-Daten (DSGVO).
 */

const PROJEKT_ID = 7;

interface Eintrag { id: number; beschreibung: string; betrag: number; rechnungsnummer?: string }

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

interface Mitschrift {
    erfasst: unknown[];
    entfernt: string[];
    gespeichert: Record<string, unknown>[];
    patchAufMaterialkosten: number;
}

async function stubProjektApi(page: Page, start: { materialkosten: Eintrag[]; projektArt: string }) {
    const stand = { materialkosten: [...start.materialkosten], projektArt: start.projektArt };
    const mitschrift: Mitschrift = { erfasst: [], entfernt: [], gespeichert: [], patchAufMaterialkosten: 0 };
    const projekt = () => ({
        id: PROJEKT_ID, bauvorhaben: 'Geländer Musterstraße', kunde: 'Max Mustermann', kundenId: 3,
        kundennummer: 'K-1003', auftragsnummer: '2026/08/00007', anlegedatum: '2026-08-15',
        bruttoPreis: 5000, bezahlt: false, abgeschlossen: false, kundenEmails: [],
        kundeDto: { id: 3, name: 'Max Mustermann', kundennummer: 'K-1003', kundenEmails: [] },
        projektArt: stand.projektArt,
        materialkosten: stand.materialkosten, artikel: [], produktkategorien: [], zeiten: [], emails: [],
    });

    await page.route('**/api/**', (route) => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        const methode = request.method();

        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'max', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (/^\/api\/last-accessed\/PROJEKT(\/\d+)?$/.test(pfad)) {
            if (methode === 'POST') return route.fulfill({ status: 204, body: '' });
            return json(route, {});
        }
        if (pfad === '/api/projekte' && methode === 'GET') return json(route, { projekte: [], gesamt: 0 });
        if (pfad === `/api/projekte/${PROJEKT_ID}/materialkosten`) {
            if (methode === 'PATCH') {
                mitschrift.patchAufMaterialkosten++;
                return json(route, { message: 'Nicht erlaubt' }, 405);
            }
            if (methode === 'POST') {
                const body = request.postDataJSON() as { beschreibung: string; betrag: number; rechnungsnummer?: string };
                mitschrift.erfasst.push(body);
                stand.materialkosten = [...stand.materialkosten, { id: 99, ...body }];
                return json(route, projekt());
            }
        }
        const loeschen = pfad.match(new RegExp(`^/api/projekte/${PROJEKT_ID}/materialkosten/(\\d+)$`));
        if (loeschen && methode === 'DELETE') {
            mitschrift.entfernt.push(loeschen[1]);
            stand.materialkosten = stand.materialkosten.filter(m => String(m.id) !== loeschen[1]);
            return json(route, projekt());
        }
        if (pfad === `/api/projekte/${PROJEKT_ID}` && methode === 'PUT') {
            const body = request.postDataJSON() as Record<string, unknown>;
            mitschrift.gespeichert.push(body);
            if (typeof body.projektArt === 'string' && body.projektArt) stand.projektArt = body.projektArt;
            return json(route, projekt());
        }
        if (pfad === `/api/projekte/${PROJEKT_ID}`) return json(route, projekt());
        if (pfad === '/api/projekte/auftragsnummer-verfuegbar') return json(route, { verfuegbar: true });
        if (pfad === '/api/ausgangs-dokumente/freigabe-status' || pfad === '/api/projekte/freigabe-status') return json(route, {});
        return json(route, []);
    });
    return mitschrift;
}

const KLEINMATERIAL: Eintrag = { id: 10, beschreibung: 'Kleinmaterial', betrag: 12.5, rechnungsnummer: 'RE-2026-001' };
const SCHRAUBEN: Eintrag = { id: 11, beschreibung: 'Schrauben', betrag: 4.2 };

/** Merkt sich am window, ob die Seite neu geladen wurde – ein Reload löscht die Marke. */
async function setzeReloadMarke(page: Page) {
    await page.evaluate(() => { (window as unknown as { __ohneReload?: boolean }).__ohneReload = true; });
}
async function ohneReload(page: Page) {
    return page.evaluate(() => (window as unknown as { __ohneReload?: boolean }).__ohneReload === true);
}

test('Kosten erfassen hängt einen Eintrag an und lässt die bisherigen stehen', async ({ page }) => {
    const mitschrift = await stubProjektApi(page, { materialkosten: [KLEINMATERIAL, SCHRAUBEN], projektArt: 'PAUSCHAL' });
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=materialkosten`);
    await expect(page.getByText('Kleinmaterial', { exact: true })).toBeVisible();
    await setzeReloadMarke(page);

    await page.getByRole('button', { name: 'Kosten erfassen' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Beschreibung').fill('Dübel');
    await dialog.getByLabel('Betrag (€)').fill('7,99');
    await dialog.getByRole('button', { name: 'Speichern' }).click();

    await expect(dialog).toBeHidden();
    await expect(page.getByText('Dübel erfasst.')).toBeVisible();
    // Genau ein Eintrag geht als Objekt raus – keine Liste, die alles ersetzt.
    expect(mitschrift.erfasst).toEqual([
        { beschreibung: 'Dübel', betrag: 7.99, monat: expect.any(Number), lieferantId: null, rechnungsnummer: null },
    ]);
    expect(mitschrift.patchAufMaterialkosten).toBe(0);
    // Liste neu geladen, ohne die Seite neu zu laden; alte Einträge stehen noch da.
    for (const text of ['Kleinmaterial', 'Schrauben', 'Dübel']) {
        await expect(page.getByText(text, { exact: true })).toBeVisible();
    }
    await expect(page.getByRole('button', { name: 'Material (3)' })).toBeVisible();
    expect(await ohneReload(page)).toBe(true);
});

test('Kosten erfassen prüft den Betrag vor dem Speichern', async ({ page }) => {
    const mitschrift = await stubProjektApi(page, { materialkosten: [], projektArt: 'PAUSCHAL' });
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=materialkosten`);

    await page.getByRole('button', { name: 'Kosten erfassen' }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Beschreibung').fill('Kleinmaterial');
    await dialog.getByLabel('Betrag (€)').fill('1,234');
    await dialog.getByRole('button', { name: 'Speichern' }).click();

    await expect(page.getByText('Betrag: Bitte höchstens 2 Nachkommastellen eingeben.')).toBeVisible();
    await expect(dialog).toBeVisible();
    expect(mitschrift.erfasst).toEqual([]);
});

test('Manuelle Materialkosten lassen sich nach Rückfrage einzeln entfernen', async ({ page }) => {
    const mitschrift = await stubProjektApi(page, { materialkosten: [KLEINMATERIAL, SCHRAUBEN], projektArt: 'PAUSCHAL' });
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=materialkosten`);

    await page.getByRole('button', { name: 'Kleinmaterial aus dem Projekt entfernen' }).click();
    const rueckfrage = page.getByRole('dialog');
    await expect(rueckfrage.getByText('„Kleinmaterial“ (12,50 €) aus dem Projekt entfernen?')).toBeVisible();
    await rueckfrage.getByRole('button', { name: 'Entfernen' }).click();

    await expect(page.getByText('Kleinmaterial entfernt.')).toBeVisible();
    expect(mitschrift.entfernt).toEqual(['10']);
    await expect(page.getByText('Kleinmaterial', { exact: true })).toBeHidden();
    await expect(page.getByText('Schrauben', { exact: true })).toBeVisible();
});

test('Abbrechen der Rückfrage entfernt nichts', async ({ page }) => {
    const mitschrift = await stubProjektApi(page, { materialkosten: [KLEINMATERIAL], projektArt: 'PAUSCHAL' });
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=materialkosten`);

    await page.getByRole('button', { name: 'Kleinmaterial aus dem Projekt entfernen' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Abbrechen' }).click();

    await expect(page.getByText('Kleinmaterial', { exact: true })).toBeVisible();
    expect(mitschrift.entfernt).toEqual([]);
});

for (const [art, kachel] of [['REGIE', 'Regie'], ['INTERN', 'Internes Projekt'], ['GARANTIE', 'Garantie']] as const) {
    test(`Bearbeiten behält die Projektart ${kachel}`, async ({ page }) => {
        const mitschrift = await stubProjektApi(page, { materialkosten: [], projektArt: art });
        await page.goto(`/projekte?projektId=${PROJEKT_ID}`);

        await page.getByRole('button', { name: 'Bearbeiten', exact: true }).click();
        await expect(page.getByRole('button', { name: new RegExp(`^${kachel}`) })).toHaveAttribute('aria-pressed', 'true');
        await expect(page.getByRole('button', { name: /^Pauschalpreis/ })).toHaveAttribute('aria-pressed', 'false');
        await page.getByRole('button', { name: 'Änderungen speichern' }).click();

        await expect.poll(() => mitschrift.gespeichert.length).toBe(1);
        expect(mitschrift.gespeichert[0].projektArt).toBe(art);
    });
}
