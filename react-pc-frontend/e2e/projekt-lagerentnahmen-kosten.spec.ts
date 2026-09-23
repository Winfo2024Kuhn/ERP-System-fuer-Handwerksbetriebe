import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

const projektId = 9;
const basisProjekt = () => ({
    id: projektId,
    bauvorhaben: 'Dummy Werkstattdach',
    anlegedatum: '2026-09-23',
    bruttoPreis: 1190,
    bezahlt: false,
    artikel: [{ id: 4, artikelId: 3, produktname: 'Historisches Profil', ausLager: true, stueckzahl: 2, gesamtpreis: 40 }],
    materialkosten: [],
    zeiten: [],
});

async function stubProjektUndNebendienste(page: import('@playwright/test').Page, projekt: ReturnType<typeof basisProjekt>) {
    const mutations: { path: string; body: unknown }[] = [];
    await page.route('**/api/**', async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === `/api/projekte/${projektId}`) {
            await route.fulfill({ json: projekt });
        } else if (path === `/api/projekte/${projektId}/eingangsrechnungen`) {
            await route.fulfill({ json: [{ id: 12, berechneterBetrag: 100, lieferantenname: 'Dummy Lieferant' }] });
        } else if (path === `/api/ausgangs-dokumente/projekt/${projektId}` || path.endsWith('/notizen') || path.endsWith('/dokumente') || path.includes('/freigabe-status')) {
            await route.fulfill({ json: [] });
        } else if (path === '/api/artikel/werkstoffe' || path === '/api/artikel/filteroptionen') {
            await route.fulfill({ json: [] });
        } else if (path === '/api/artikel') {
            await route.fulfill({ json: { artikel: [{
                id: 31, produktname: 'Dummy Bohrer', verrechnungseinheit: 'STUECK',
                preis: 8, guenstigsterPreis: 8, lieferantId: 7, guenstigsterLieferantId: 7,
                lieferantenname: 'Dummy Lieferant', guenstigsterLieferantName: 'Dummy Lieferant',
            }], gesamt: 1 } });
        } else if ((path === `/api/projekte/${projektId}/materialkosten/artikel` || path === `/api/projekte/${projektId}/materialkosten`) && request.method() === 'POST') {
            const body = request.postDataJSON();
            mutations.push({ path, body });
            const kosten = Array.isArray(body) ? body[0] as Record<string, unknown> : {};
            projekt.materialkosten = [...projekt.materialkosten, {
                id: 20 + projekt.materialkosten.length,
                beschreibung: path.endsWith('/artikel') ? 'Dummy Bohrer' : String(kosten.beschreibung),
                betrag: path.endsWith('/artikel') ? Number(kosten.menge) * Number(kosten.preis) : Number(kosten.betrag),
                artikelIdSnapshot: path.endsWith('/artikel') ? 31 : undefined,
                mengeSnapshot: path.endsWith('/artikel') ? Number(kosten.menge) : undefined,
                einheitSnapshot: path.endsWith('/artikel') ? 'STUECK' : undefined,
                preisJeEinheitSnapshot: path.endsWith('/artikel') ? Number(kosten.preis) : undefined,
                lieferantennameSnapshot: path.endsWith('/artikel') ? 'Dummy Lieferant' : undefined,
            }];
            await route.fulfill({ status: 200, json: projekt });
        } else if (request.method() === 'GET') {
            await route.fulfill({ json: [] });
        } else {
            await route.fulfill({ json: {} });
        }
    });
    return mutations;
}

test('Katalogartikelpreis ist sichtbar und änderbar; die Kosten bleiben nach Reload im Projekt', async ({ page }) => {
    const projekt = basisProjekt();
    const mutations = await stubProjektUndNebendienste(page, projekt);
    const fremdeAntworten: string[] = [];

    await page.goto(`/projekte?projektId=${projektId}&tab=materialkosten`);
    const eigeneOrigin = new URL(page.url()).origin;
    page.on('response', response => { if (new URL(response.url()).origin !== eigeneOrigin) fremdeAntworten.push(response.url()); });
    await page.getByRole('button', { name: 'Artikel aus Stamm auswählen', exact: true }).click();
    await expect(page.getByText('8,00 €').last()).toBeVisible();
    await page.getByRole('button', { name: 'Dummy Bohrer an- oder abwählen' }).click();
    await expect(page.getByLabel('Menge für Dummy Bohrer')).toHaveValue('1');
    await expect(page.getByLabel('Preis je Stück (€) für Dummy Bohrer')).toHaveValue('8');
    await designPruefung(page, test.info(), 'projekt-materialkosten-artikel-ausgewaehlt');
    await page.getByLabel('Menge für Dummy Bohrer').fill('2');
    await page.getByLabel('Preis je Stück (€) für Dummy Bohrer').fill('10');
    await page.getByRole('button', { name: 'Kosten speichern (1)' }).click();

    await expect.poll(() => mutations.length).toBe(1);
    expect(mutations[0].path).toBe(`/api/projekte/${projektId}/materialkosten/artikel`);
    expect(mutations[0].body).toEqual([expect.objectContaining({ artikelId: 31, menge: 2, preis: 10, lieferantId: 7 })]);
    expect(JSON.stringify(mutations[0].body)).not.toContain('ausLager');
    expect(mutations.some(m => m.path.includes('/einkauf/') || m.path.includes('/bestellung'))).toBe(false);
    await expect(page.getByText('2 Stück · 10,00 € je Einheit · Dummy Lieferant', { exact: true })).toBeVisible();
    await expect(page.getByText('20,00 €', { exact: true })).toBeVisible();
    await expect(page.getByText('160,00 €', { exact: true })).toBeVisible();

    await page.reload();
    await expect(page.getByText('Dummy Bohrer')).toBeVisible();
    await expect(page.getByText('2 Stück · 10,00 € je Einheit · Dummy Lieferant', { exact: true })).toBeVisible();
    await expect(page.getByText('20,00 €', { exact: true })).toBeVisible();
    expect(fremdeAntworten).toEqual([]);
});

test('manuelle Beschreibung und Kosten werden ausschließlich als Projektkosten gespeichert', async ({ page }) => {
    const projekt = basisProjekt();
    const mutations = await stubProjektUndNebendienste(page, projekt);
    await page.goto(`/projekte?projektId=${projektId}&tab=materialkosten`);
    await page.getByRole('button', { name: 'Kosten erfassen', exact: true }).click();
    await page.getByLabel('Beschreibung').fill('Dummy Befestigung');
    await page.getByLabel('Kosten (€)').fill('12,5');
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();

    await expect.poll(() => mutations.length).toBe(1);
    expect(mutations[0].path).toBe(`/api/projekte/${projektId}/materialkosten`);
    expect(mutations[0].body).toEqual([expect.objectContaining({ beschreibung: 'Dummy Befestigung', betrag: 12.5 })]);
    expect(mutations.some(m => m.path.includes('/einkauf/') || m.path.includes('/bestellung'))).toBe(false);
    await expect(page.getByText('Dummy Befestigung')).toBeVisible();
    await expect(page.getByText('12,50 €', { exact: true })).toBeVisible();

    await page.reload();
    await expect(page.getByText('Dummy Befestigung')).toBeVisible();
    await expect(page.getByText('12,50 €', { exact: true })).toBeVisible();
    await expect(page.getByText('152,50 €', { exact: true })).toBeVisible();
});
