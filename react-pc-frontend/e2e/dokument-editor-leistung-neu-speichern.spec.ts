import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeDokumentEditorApi, oeffneDokumentEditor } from './hilfen/dokument-editor';

/**
 * End-to-End-Spec „Als neue Leistung speichern“ im Dokumenteditor:
 * gespeicherte Leistung einfuegen → Kurztext aendern → Knopf an der Karte →
 * Dialog mit vorausgewaehltem Ursprungsordner → Speichern → Knopf weg.
 *
 * Vorbild: dokument-editor-rueckgaengig.spec.ts. /api wird vollstaendig
 * gestubbt (kein Backend). DSGVO: nur Dummy-Daten.
 */

const URSPRUNG = {
    id: 7,
    name: 'Geländer montieren',
    description: '<p>Montage inkl. Befestigung</p>',
    price: 120,
    unit: { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' },
    folderId: 12,
    kategoriePfad: 'Geländer > Edelstahl',
};

const KATEGORIEN = [
    { id: 1, bezeichnung: 'Geländer', parentId: null, leaf: false },
    { id: 2, bezeichnung: 'Treppen', parentId: null, leaf: false },
    { id: 12, bezeichnung: 'Edelstahl', parentId: 1, leaf: true },
    { id: 13, bezeichnung: 'Stahl', parentId: 1, leaf: true },
    { id: 21, bezeichnung: 'Podeste', parentId: 2, leaf: true },
];

const NEUER_NAME = 'Geländer montieren, Edelstahl V4A';

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

/** Stubbt Leistungen und Ordnerbaum; liefert die mitgeschriebenen POST-Bodies. */
async function stubbeLeistungenUndOrdner(page: Page): Promise<Record<string, unknown>[]> {
    const posts: Record<string, unknown>[] = [];
    await page.route('**/api/leistungen', route => {
        if (route.request().method() === 'POST') {
            const body = route.request().postDataJSON() as Record<string, unknown>;
            posts.push(body);
            // Wie das Backend: Einheit kommt als Objekt zurueck, nicht als Enum-Name.
            return json(route, {
                ...URSPRUNG, ...body, id: 99, kategoriePfad: 'Geländer > Edelstahl',
                unit: body.unit ? { name: body.unit, anzeigename: 'Laufende Meter' } : null,
            });
        }
        return json(route, [URSPRUNG]);
    });
    // Zeitprognose der Karte: ohne Daten keine Anzeige.
    await page.route(/\/api\/leistungen\/\d+\/zeitprognose/, route => route.fulfill({ status: 404, body: '' }));
    await page.route('**/api/produktkategorien/haupt**', route =>
        json(route, KATEGORIEN.filter(k => k.parentId == null)));
    await page.route(/\/api\/produktkategorien\/\d+\/unterkategorien/, route => {
        const id = Number(new URL(route.request().url()).pathname.split('/')[3]);
        return json(route, KATEGORIEN.filter(k => k.parentId === id));
    });
    await page.route(/\/api\/produktkategorien\/\d+\?light=true$/, route => {
        const id = Number(new URL(route.request().url()).pathname.split('/').pop());
        const k = KATEGORIEN.find(x => x.id === id);
        return k ? json(route, k) : route.fulfill({ status: 404, body: '' });
    });
    return posts;
}

test.describe('Dokument-Editor – Als neue Leistung speichern', () => {
    test('geänderte Position landet als neue Leistung im Ursprungsordner', async ({ page }, testInfo) => {
        await stubbeDokumentEditorApi(page);
        const posts = await stubbeLeistungenUndOrdner(page);
        await oeffneDokumentEditor(page);

        // Leistung einfuegen
        await page.getByRole('button', { name: 'Leistung', exact: true }).click();
        await page.getByRole('button', { name: /Geländer montieren/ }).click();
        const titel = page.getByPlaceholder('Titel / Kurztext');
        await expect(titel).toHaveValue('Geländer montieren');

        // Unveraendert: kein Knopf
        const knopf = page.getByRole('button', { name: 'Als neue Leistung speichern' });
        await expect(knopf).toHaveCount(0);

        // Kurztext aendern → Knopf erscheint (auch zugeklappt)
        await titel.fill(NEUER_NAME);
        await expect(knopf).toBeVisible();
        await knopf.click();

        // Dialog: Ursprungsordner aufgeklappt und vorausgewaehlt
        const dialog = page.getByRole('dialog', { name: 'Als neue Leistung speichern' });
        await expect(dialog).toBeVisible();
        await expect(dialog.getByTestId('speicherziel')).toContainText('Geländer › Edelstahl');
        await expect(dialog.getByRole('button', { name: 'Edelstahl' })).toHaveAttribute('aria-pressed', 'true');
        await expect(dialog.getByRole('textbox', { name: /Name der Leistung/ })).toHaveValue(NEUER_NAME);

        const speichern = dialog.getByRole('button', { name: 'Speichern', exact: true });
        await designPruefung(page, testInfo, 'dokument-editor-leistung-neu-speichern-dialog', { primaerAktion: speichern });

        // Info-Button oeffnet die Erklaerung
        await dialog.getByRole('button', { name: 'Mehr zu: Als neue Leistung speichern' }).click();
        await expect(page.getByText('Die ursprüngliche Leistung bleibt unverändert.', { exact: false })).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(dialog).toBeVisible();

        // Speichern → richtiger Body
        const antwort = page.waitForResponse(r => r.url().endsWith('/api/leistungen') && r.request().method() === 'POST');
        await speichern.click();
        await antwort;
        expect(posts).toEqual([{
            name: NEUER_NAME,
            description: '<p>Montage inkl. Befestigung</p>',
            price: 120,
            unit: 'LAUFENDE_METER',
            folderId: 12,
        }]);

        // Dialog zu, Erfolgsmeldung, Knopf weg (Position ist jetzt mit der neuen Leistung verbunden)
        await expect(dialog).toHaveCount(0);
        await expect(page.getByText(`Neue Leistung „${NEUER_NAME}“ im Ordner „Geländer › Edelstahl“ gespeichert`)).toBeVisible();
        await expect(knopf).toHaveCount(0);
    });
});
