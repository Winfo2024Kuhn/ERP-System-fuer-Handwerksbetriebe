import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

const projektId = 9;
const projekt = {
    id: projektId,
    bauvorhaben: 'Dummy Werkstattdach',
    anlegedatum: '2026-09-23',
    bruttoPreis: 1190,
    bezahlt: false,
    artikel: [{ id: 4, artikelId: 3, produktname: 'Historisches Lagerprofil', ausLager: true, gesamtpreis: 40 }],
    materialkosten: [],
    zeiten: [],
    lagerentnahmenKosten: 6,
    lagerentnahmenBewertungOffen: true,
};

test('zeigt echte Teilentnahmen getrennt und erhält offene Bewertung sowie Rechnungsbetrag', async ({ page }) => {
    let entnahmeRequest: Record<string, unknown> | null = null;
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        const path = url.pathname;
        if (path === '/api/einkauf/bedarf') {
            await route.fulfill({ json: { content: [{
                id: 22, version: 0,
                position: { bezeichnung: 'Dummy Stahlprofil', basis: { einheit: 'STUECK' } },
                mengen: { disponierbar: 10 },
            }] } });
        } else if (path === '/api/einkauf/lagerentnahmen' && route.request().method() === 'POST') {
            entnahmeRequest = route.request().postDataJSON();
            await route.fulfill({ json: { id: 51, bewertungOffen: true, bewerteterBetrag: null } });
        } else if (path === `/api/projekte/${projektId}`) {
            await route.fulfill({ json: projekt });
        } else if (path === `/api/projekte/${projektId}/eingangsrechnungen`) {
            await route.fulfill({ json: [{ id: 12, berechneterBetrag: 100, lieferantenname: 'Dummy Lieferant' }] });
        } else if (path === `/api/ausgangs-dokumente/projekt/${projektId}`) {
            await route.fulfill({ json: [] });
        } else if (path.endsWith('/notizen') || path.endsWith('/dokumente') || path.includes('/freigabe-status')) {
            await route.fulfill({ json: [] });
        } else {
            await route.fulfill({ json: {} });
        }
    });

    await page.goto(`/projekte?projektId=${projektId}&tab=materialkosten`);
    await expect(page.getByText('Teilentnahmen aus Lager')).toBeVisible();
    await page.getByRole('button', { name: 'Teilentnahme erfassen' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByRole('combobox', { name: 'Lagerbedarf' })).toBeVisible();
    await page.getByLabel('Entnommene Menge').fill('3');
    await designPruefung(page, test.info(), 'projekt-lagerentnahme-dialog');
    await page.getByRole('button', { name: 'Teilentnahme bestätigen' }).click();
    await expect.poll(() => entnahmeRequest).not.toBeNull();
    expect(entnahmeRequest).toMatchObject({
        anteil: { bedarfId: 22, version: 0, menge: 3 },
        preisJeEinheit: null,
        preisQuelle: null,
    });
    await expect(page.getByText('Bewertung offen').last()).toBeVisible();
    await expect(page.getByText(/6,00\s*€/u).last()).toBeVisible();
    await expect(page.getByText(/146,00\s*€/u)).toBeVisible();
    await expect(page.getByText('Historisches Lagerprofil')).toBeVisible();
    await page.reload();
    await expect(page.getByText('Teilentnahmen aus Lager')).toBeVisible();
    await expect(page.getByText('Bewertung offen')).toBeVisible();
    await expect(page.getByText(/146,00\s*€/u)).toBeVisible();

    await designPruefung(page, test.info(), 'projekt-lagerentnahmen-kosten');
});

test('Katalogauswahl erfasst nur Materialkosten und bietet keine Bestellaktion', async ({ page }) => {
    const gespeicherteRequests: { url: string; body: unknown }[] = [];
    let gibArtikelFrei!: () => void;
    const artikelFreigabe = new Promise<void>(resolve => { gibArtikelFrei = resolve; });
    await page.route('**/api/**', async route => {
        const request = route.request();
        const url = new URL(request.url());
        const path = url.pathname;
        if (path === `/api/projekte/${projektId}`) {
            await route.fulfill({ json: { ...projekt, lagerentnahmenKosten: 0, lagerentnahmenBewertungOffen: false } });
        } else if (path === '/api/artikel/werkstoffe' || path === '/api/artikel/filteroptionen') {
            await route.fulfill({ json: [] });
        } else if (path === '/api/artikel') {
            await artikelFreigabe;
            await route.fulfill({ json: { artikel: [{
                id: 31, produktname: 'Dummy Stahlprofil', verrechnungseinheit: 'STUECK',
                preis: 2, lieferantId: 7, lieferantenname: 'Dummy Lieferant',
            }], gesamt: 1 } });
        } else if (path === `/api/projekte/${projektId}/materialkosten/artikel` && request.method() === 'POST') {
            gespeicherteRequests.push({ url: path, body: request.postDataJSON() });
            await route.fulfill({ status: 200, json: {} });
        } else if (path.endsWith('/eingangsrechnungen') || path.endsWith('/notizen') || path.endsWith('/dokumente') || path.includes('/freigabe-status') || path.includes('/ausgangs-dokumente/')) {
            await route.fulfill({ json: [] });
        } else {
            await route.fulfill({ json: {} });
        }
    });

    await page.goto(`/projekte?projektId=${projektId}&tab=materialkosten`);
    await page.getByRole('button', { name: 'Materialkosten erfassen', exact: true }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await expect(page.getByText('Materialkosten aus Katalog erfassen')).toBeVisible();
    await expect(page.getByText(/Dadurch wird keine Bestellung ausgelöst/)).toBeVisible();
    await expect(page.getByText('Bestellen', { exact: true })).toHaveCount(0);
    await designPruefung(page, test.info(), 'projekt-materialkosten-katalog-dialog');
    gibArtikelFrei();
    await page.getByRole('checkbox', { name: 'Dummy Stahlprofil auswählen' }).check();
    await page.getByRole('button', { name: 'Materialkosten speichern (1)' }).click();

    await expect.poll(() => gespeicherteRequests.length).toBe(1);
    expect(gespeicherteRequests[0].body).toEqual([expect.objectContaining({ artikelId: 31, menge: 1 })]);
    expect(JSON.stringify(gespeicherteRequests[0].body)).not.toContain('ausLager');
    expect(gespeicherteRequests.some(r => r.url.includes('/einkauf/bedarf'))).toBe(false);
});
