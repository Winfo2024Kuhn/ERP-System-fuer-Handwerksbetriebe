import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

const sachkonten = [
    { id: 1, nummer: '4930', bezeichnung: 'Bürobedarf', kontoTyp: 'AUFWAND', aktiv: true, sortierung: 1 },
    { id: 2, nummer: '8400', bezeichnung: 'Erlöse Leistungen', kontoTyp: 'ERTRAG', aktiv: true, sortierung: 1 },
];
const kassenbuch = { saldoStart: 100, saldoEnde: 100, summeEinnahmen: 0, summeAusgaben: 0, summePrivateinlagen: 0, summePrivatentnahmen: 0, bewegungen: [], letzterAbschluss: null, offeneBewegungen: 0 };

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stub(page: Page, konflikt = false) {
    const arts: string[] = [];
    let ersterKonflikt = konflikt;
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname;
        const method = route.request().method();
        if (path === '/api/auth/me') return json(route, { id: 1, username: 'max.mustermann', displayName: 'Max Mustermann', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        if (path === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (path === '/api/buchhaltung/belege') return json(route, []);
        if (path === '/api/buchhaltung/sachkonten') return json(route, sachkonten);
        if (path === '/api/buchhaltung/zahlungsarten') return json(route, []);
        if (path === '/api/buchhaltung/kassenbuch') return json(route, kassenbuch);
        if (path === '/api/buchhaltung/kasse/saldo') return json(route, { saldo: 100, mindestbestand: 0 });
        if (path === '/api/buchhaltung/kasse/einstellung') return json(route, { id: 1, mindestbestand: 0, ehegattengehaltAktiv: false });
        if (path === '/api/bestellungen-uebersicht/kostenstellen') return json(route, [{ id: 8, nummer: 'B-1', bezeichnung: 'Musterbaustelle' }]);
        if (path === '/api/buchhaltung/kassenbuch/offene-ausgangsrechnungen') return json(route, [{ id: 7, dokumentNummer: 'RE-7', datum: '2026-09-01', bruttoBetrag: 119 }]);
        if (path === '/api/buchhaltung/kasse/privateinlage' && method === 'POST') return json(route, {});
        if (path === '/api/buchhaltung/kassenbuch/buchungen' && method === 'POST') {
            const raw = route.request().postData() ?? '';
            const art = /"art"\s*:\s*"([^"]+)/.exec(raw)?.[1] ?? '';
            arts.push(art);
            if (ersterKonflikt) { ersterKonflikt = false; return json(route, { message: 'Mindestbestand unterschritten', projizierterSaldo: 2, mindestbestand: 20 }, 409); }
            return json(route, { id: 99 });
        }
        return json(route, []);
    });
    return arts;
}

async function oeffne(page: Page) {
    await page.goto('/belege-kasse');
    await page.getByRole('button', { name: 'Kassenbuch', exact: true }).click();
    await page.getByRole('button', { name: 'Neue Buchung', exact: true }).click();
}

async function fuelleUndBuche(page: Page, titel: string) {
    await page.getByRole('button', { name: new RegExp(`^${titel}`) }).click();
    await page.getByRole('textbox', { name: 'Betrag (€)' }).fill('12,50');
    if (titel === 'Geld eingenommen') await page.getByRole('textbox', { name: 'Von wem?' }).fill('Max Mustermann');
    if (titel === 'Geld ausgegeben') {
        await page.getByRole('textbox', { name: 'An wen?' }).fill('Musterbaustoffe GmbH');
        await page.getByRole('combobox', { name: 'Wofür? (Konto)' }).click();
        await page.getByRole('option', { name: /4930 Bürobedarf/ }).click();
        await page.getByRole('button', { name: 'Kein Beleg vorhanden', exact: true }).click();
        await page.getByRole('textbox', { name: 'Warum gibt es keinen Beleg?' }).fill('Beleg verloren');
    }
    await page.getByRole('button', { name: 'Buchen', exact: true }).click();
    await expect(page.getByRole('dialog')).not.toBeVisible();
}

test('bucht alle sechs Kacheln mit dem neuen Multipart-Endpunkt', async ({ page }, info) => {
    const arts = await stub(page);
    await oeffne(page);
    await expect(page.getByText('Geld eingenommen', { exact: true })).toBeVisible();
    await expect(page.getByText('Geld privat entnommen', { exact: true })).toBeVisible();
    await designPruefung(page, info, 'neue-buchung-auswahl', { primaerAktion: page.getByRole('button', { name: /^Geld eingenommen/ }) });
    for (const titel of ['Geld eingenommen', 'Geld ausgegeben', 'Geld von der Bank geholt', 'Geld zur Bank gebracht', 'Eigenes Geld eingelegt', 'Geld privat entnommen']) {
        await fuelleUndBuche(page, titel);
        if (titel !== 'Geld privat entnommen') await oeffne(page);
    }
    expect(arts).toEqual(['GELD_EINGENOMMEN', 'GELD_AUSGEGEBEN', 'VON_BANK_GEHOLT', 'ZUR_BANK_GEBRACHT', 'EIGENES_GELD_EINGELEGT', 'GELD_PRIVAT_ENTNOMMEN']);
});

test('Ausgabe ohne Beleg setzt die Mehrwertsteuer auf 0 und erklärt die Vorsteuer', async ({ page }, info) => {
    await stub(page); await oeffne(page);
    await page.getByRole('button', { name: /^Geld ausgegeben/ }).click();
    await page.getByRole('button', { name: 'Kein Beleg vorhanden', exact: true }).click();
    await expect(page.getByText('Ohne Fremdbeleg gibt es keine Vorsteuer.')).toBeVisible();
    await expect(page.getByRole('button', { name: '0 %', exact: true })).toBeDisabled();
    await designPruefung(page, info, 'neue-buchung-ohne-beleg');
});

test('Unterdeckung bietet eine Privateinlage an und Abbrechen sendet keine Buchung', async ({ page }) => {
    const arts = await stub(page, true); await oeffne(page);
    await page.getByRole('button', { name: /^Geld zur Bank gebracht/ }).click();
    await page.getByRole('textbox', { name: 'Betrag (€)' }).fill('12,50');
    await page.getByRole('button', { name: 'Buchen', exact: true }).click();
    await expect(page.getByRole('button', { name: /Privateinlage in Höhe 18,00 € vorab buchen/ })).toBeVisible();
    await page.getByRole('button', { name: /Privateinlage in Höhe 18,00 € vorab buchen/ }).click();
    await expect(page.getByRole('dialog')).not.toBeVisible();
    await oeffne(page); await page.getByRole('button', { name: /^Eigenes Geld eingelegt/ }).click();
    await page.getByRole('button', { name: 'Abbrechen', exact: true }).click();
    expect(arts).toEqual(['ZUR_BANK_GEBRACHT', 'ZUR_BANK_GEBRACHT']);
});
