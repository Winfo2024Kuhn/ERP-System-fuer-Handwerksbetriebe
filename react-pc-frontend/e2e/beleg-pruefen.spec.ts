import { expect, test } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import type { Page, Route } from '@playwright/test';

const ID = 1301;
let putBody: Record<string, unknown> | null = null;
const beleg = { id: ID, belegKategorie: 'KASSE_AUSGABE', dokumentTyp: 'RECHNUNG', istUmbuchung: false, status: 'NEU', kiAnalyseStatus: 'DONE', belegDatum: '2026-09-12', belegNummer: null, beschreibung: 'Material für Musterbaustelle', betragNetto: 84.03, betragBrutto: 100, mwstSatz: 19, zahlungsart: 'Überweisung', lieferantId: 8, lieferantName: 'Am Handy GmbH', sachkontoId: null, uploadDatum: '2026-09-12T08:00:00', originalDateiname: 'musterbeleg.jpg', mimeType: 'image/jpeg', kostenstellenSplits: [], kiBetragBrutto: 100, kiBelegdatum: '2026-09-12', kiZahlungsart: 'Überweisung', kiVorgeschlagenerLieferant: 'KI Baustoffe GmbH', kiKostenkontoHinweis: 'Kein passendes Konto gefunden', vorschlagSachkonto: { id: 1, nummer: '4930', bezeichnung: 'Bürobedarf', quelle: 'KI', begruendung: 'Passt zum Beleg' }, vorschlagKostenstelle: null, eingangsrechnungBezahlt: false, eingangsrechnungBezahltAm: null };
const sachkonten = [{ id: 1, nummer: '4930', bezeichnung: 'Bürobedarf', kontoTyp: 'AUFWAND', aktiv: true, sortierung: 1 }];

function json(route: Route, body: unknown) { return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) }); }
async function stub(page: Page) {
    await page.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname;
        if (route.request().method() === 'PUT' && path === `/api/buchhaltung/belege/${ID}`) { putBody = route.request().postDataJSON() as Record<string, unknown>; return json(route, { ...beleg, ...putBody }); }
        if (path === '/api/auth/me') return json(route, { id: 1, username: 'max.mustermann', displayName: 'Max Mustermann', active: true, roles: ['ADMIN'], admin: true });
        if (path === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (path === '/api/buchhaltung/belege') return json(route, [beleg]);
        if (path === `/api/buchhaltung/belege/${ID}`) return json(route, beleg);
        if (path === '/api/buchhaltung/sachkonten') return json(route, sachkonten);
        if (path === '/api/buchhaltung/zahlungsarten') return json(route, [{ id: 1, bezeichnung: 'Bar', aktiv: true, sortierung: 1 }, { id: 2, bezeichnung: 'Überweisung', aktiv: true, sortierung: 2 }]);
        if (path === '/api/bestellungen-uebersicht/kostenstellen') return json(route, [{ id: 3, nummer: 'B-1', bezeichnung: 'Musterbaustelle' }]);
        if (path === '/api/buchhaltung/kasse/saldo') return json(route, { saldo: 200, mindestbestand: 0 });
        if (path === '/api/buchhaltung/kassenbuch') return json(route, { saldoStart: 0, saldoEnde: 0, summeEinnahmen: 0, summeAusgaben: 0, summePrivatentnahmen: 0, summePrivateinlagen: 0, bewegungen: [] });
        return json(route, []);
    });
}

test('Beleg prüfen führt in fester Reihenfolge durch Zahlung, Zuordnung und Übernahme', async ({ page }, info) => {
    putBody = null;
    await stub(page);
    await page.goto('/belege-kasse');
    await page.getByRole('button', { name: /Am Handy GmbH Kasse/ }).click();
    const dialog = page.getByRole('dialog', { name: 'Beleg prüfen' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByText('Wo gezahlt')).toHaveCount(0);
    const headings = await dialog.getByRole('heading').allTextContents();
    expect(headings.filter(h => h !== 'Beleg prüfen & validieren')).toEqual(['Wie viel und wann?', 'Wie wurde bezahlt?', 'Ist die Rechnung schon bezahlt?', 'Wofür war das?', 'Für welche Baustelle / welchen Bereich?', 'Von wem war der Beleg?']);
    await expect(dialog.getByText('Am Handy gewählt: Am Handy GmbH. Die KI hat gelesen: KI Baustoffe GmbH.')).toBeVisible();
    await dialog.getByRole('button', { name: 'Konto übernehmen' }).click();
    await expect(dialog.getByRole('combobox', { name: /konto/i })).toContainText('4930 Bürobedarf');
    const zahlung = dialog.getByRole('combobox', { name: 'Wie wurde bezahlt?' });
    await zahlung.click(); await page.getByRole('option', { name: 'Bar', exact: true }).click();
    await expect(dialog.getByText('Bar und EC-Karte gelten als sofort bezahlt.')).toBeVisible();
    await expect(dialog.getByText('Ist die Rechnung schon bezahlt?')).toHaveCount(0);
    await zahlung.click(); await page.getByRole('option', { name: 'Überweisung', exact: true }).click();
    await expect(dialog.getByText('Ist die Rechnung schon bezahlt?')).toBeVisible();
    await dialog.getByRole('combobox', { name: 'Baustelle oder Bereich' }).click(); await page.getByRole('option', { name: 'B-1 Musterbaustelle' }).click();
    const form = dialog.locator('.lg\\:col-span-2'); const preview = dialog.locator('.lg\\:col-span-1').first();
    const [formBox, previewBox] = await Promise.all([form.boundingBox(), preview.boundingBox()]);
    expect(formBox!.width / previewBox!.width).toBeGreaterThan(1.8);
    expect(formBox!.width / previewBox!.width).toBeLessThan(2.2);
    await designPruefung(page, info, 'beleg-pruefen', { primaerAktion: dialog.getByRole('button', { name: 'Prüfen & Übernehmen' }) });
    await dialog.getByRole('button', { name: 'Prüfen & Übernehmen' }).click();
    await expect.poll(() => putBody).not.toBeNull();
    expect(putBody).toMatchObject({ zahlungsstatus: 'OFFEN', bezahltAm: null, belegKategorie: 'BANK', kostenstellenSplits: [{ kostenstelleId: 3, prozent: 100 }] });
});
