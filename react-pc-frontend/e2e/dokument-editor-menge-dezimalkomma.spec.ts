import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeDokumentEditorApi, oeffneDokumentEditor } from './hilfen/dokument-editor';

/**
 * End-to-End-Spec: Menge und Einzelpreis einer Position nehmen Werte unter 1
 * mit Dezimalkomma an („0,5“ Std., „0,50 €“).
 *
 * Fehlerbild vorher: Die getippte 0 verschwand sofort wieder, uebrig blieb nur
 * das Komma. /api wird vollstaendig gestubbt (kein Backend). DSGVO: nur Dummy-Daten.
 */

const LEISTUNG = {
    id: 7,
    name: 'Geländer montieren',
    description: '<p>Montage inkl. Befestigung</p>',
    price: 120,
    unit: { name: 'STUNDE', anzeigename: 'Std' },
    folderId: 12,
    kategoriePfad: 'Geländer > Edelstahl',
};

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubbeLeistungen(page: Page) {
    await page.route('**/api/leistungen', route => json(route, [LEISTUNG]));
    // Zeitprognose der Karte: ohne Daten keine Anzeige.
    await page.route(/\/api\/leistungen\/\d+\/zeitprognose/, route => route.fulfill({ status: 404, body: '' }));
}

test.describe('Dokument-Editor – Menge und Einzelpreis mit Dezimalkomma', () => {
    test('0,5 als Menge und 0,50 € als Einzelpreis lassen sich eintippen', async ({ page }, testInfo) => {
        await stubbeDokumentEditorApi(page);
        await stubbeLeistungen(page);
        await oeffneDokumentEditor(page);

        await page.getByRole('button', { name: 'Leistung', exact: true }).click();
        await page.getByRole('button', { name: /Geländer montieren/ }).click();
        await page.getByRole('button', { name: 'Aufklappen' }).click();

        const menge = page.getByRole('textbox', { name: 'Menge' });
        await menge.click();
        await menge.press('ControlOrMeta+a');
        await menge.pressSequentially('0');
        await expect(menge).toHaveValue('0');
        await menge.pressSequentially(',5');
        await expect(menge).toHaveValue('0,5');
        await expect(page.getByText('60,00 €').first()).toBeVisible();

        const preis = page.getByRole('textbox', { name: 'Einzelpreis' });
        await preis.click();
        await preis.press('ControlOrMeta+a');
        await preis.pressSequentially('0,50');
        await expect(preis).toHaveValue('0,50');
        await expect(page.getByText('0,25 €').first()).toBeVisible();

        // Feld verlassen: Werte bleiben so stehen, wie sie getippt wurden.
        await page.keyboard.press('Tab');
        await expect(menge).toHaveValue('0,5');
        await expect(preis).toHaveValue('0,50');

        await designPruefung(page, testInfo, 'dokument-editor-menge-dezimalkomma');
    });
});
