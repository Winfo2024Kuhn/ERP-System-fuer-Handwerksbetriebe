import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeDokumentEditorApi, oeffneDokumentEditor } from './hilfen/dokument-editor';

/**
 * End-to-End-Spec fuer "Rückgängig & Wiederholen" im Dokumenteditor (Task 4,
 * Issue #160). Der Verlaufskern, die Hooks, der Tiptap-Verlaufsmodus und die
 * Knopf-Komponente kommen aus Abschnitt 1 (bereits gemergt und E2E-frei --
 * ohne Verdrahtung gab es nichts Sichtbares); diese Spec ist der erste Test,
 * der den kompletten, sichtbaren Ablauf ueber die echte Oberflaeche prueft.
 *
 * Vorbild: e2e/dokument-editor-seite.spec.ts (Aufbau, Stubs, Design-Pruefung).
 * /api wird vollstaendig gestubbt (kein Backend). DSGVO: nur Dummy-Daten.
 */

/** Drei einfache Leistungen, flach (kein Bauabschnitt) -- reicht fuer
 *  Loeschen/Tippen/Mehrfach-Rueckgaengig ueber die echte Oberflaeche. */
const DREI_POSITIONEN = JSON.stringify({
    blocks: [
        { id: 'pos-1', type: 'SERVICE', title: 'Dachrinne reinigen', quantity: 2, unit: 'Stk', price: 100, fontSize: 10 },
        { id: 'pos-2', type: 'SERVICE', title: 'Ziegel ersetzen', quantity: 5, unit: 'Stk', price: 20, fontSize: 10 },
        { id: 'pos-3', type: 'SERVICE', title: 'Dachrinne streichen', quantity: 1, unit: 'Stk', price: 150, fontSize: 10 },
    ],
    globalRabatt: 0,
});

function loeschKnopf(page: import('@playwright/test').Page, blockId: string) {
    return page.locator(`[data-block-id="${blockId}"] button:has(svg.lucide-trash2)`);
}

test.describe('Dokument-Editor – Rückgängig & Wiederholen', () => {
    test('Knöpfe stehen links neben "Textbaustein", ohne Überlappung', async ({ page }, testInfo) => {
        await stubbeDokumentEditorApi(page, { dokument: { positionenJson: DREI_POSITIONEN } });
        await oeffneDokumentEditor(page);

        const rueckgaengig = page.getByRole('button', { name: 'Rückgängig' });
        const textbaustein = page.getByRole('button', { name: 'Textbaustein' });
        await expect(rueckgaengig).toBeVisible();
        await expect(textbaustein).toBeVisible();

        const rGBox = await rueckgaengig.boundingBox();
        const tbBox = await textbaustein.boundingBox();
        if (!rGBox || !tbBox) throw new Error('Kein Bounding-Box gefunden');

        // Rechte Kante des Rückgängig-Knopfs (samt Pfeil/Wiederholen dazwischen)
        // liegt links von "Textbaustein", beide in derselben Zeile.
        expect(rGBox.x).toBeLessThan(tbBox.x);
        const mitteRG = rGBox.y + rGBox.height / 2;
        const mitteTB = tbBox.y + tbBox.height / 2;
        expect(Math.abs(mitteRG - mitteTB)).toBeLessThan(4);

        // Zielfläche des Chevron-Knopfs (Dropdown-Auslöser) mindestens 24px breit.
        const chevron = page.getByRole('button', { name: 'Liste der letzten Änderungen' });
        const chevronBox = await chevron.boundingBox();
        if (!chevronBox) throw new Error('Kein Bounding-Box für den Chevron-Knopf gefunden');
        expect(chevronBox.width).toBeGreaterThanOrEqual(24);

        await designPruefung(page, testInfo, 'dokument-editor-rueckgaengig-kopfleiste', { primaerAktion: rueckgaengig });
    });

    test('Dokumentnummer wird durch die Verlauf-Knöpfe nicht abgeschnitten, auch nicht bei "Ungespeichert"', async ({ page }) => {
        // Nachbesserung (Design-Review, 🔴): die Verlaufsgruppe drückt bei
        // 1440px die linke Seite der Kopfleiste zusammen -- h1 (die
        // Dokumentnummer) trägt data-kuerzung-erlaubt="true" und wird daher
        // von der generischen Überlauf-Prüfung in design.ts NICHT erfasst.
        // Diese Zusicherung schließt genau diese Lücke.
        await stubbeDokumentEditorApi(page, { dokument: { positionenJson: DREI_POSITIONEN } });
        await oeffneDokumentEditor(page);

        const h1 = page.locator('h1');
        await expect(h1).toHaveText('RE-2026/09/00001');

        const keinUeberlauf = async () => {
            const masse = await h1.evaluate((el) => ({ scrollWidth: el.scrollWidth, clientWidth: el.clientWidth }));
            expect(
                masse.scrollWidth,
                `Dokumentnummer läuft über: scrollWidth ${masse.scrollWidth}px > clientWidth ${masse.clientWidth}px`,
            ).toBeLessThanOrEqual(masse.clientWidth);
        };
        await keinUeberlauf();

        // "Ungespeichert" ist der schlimmste Fall: ein Badge mehr in derselben
        // Zeile, direkt links von der (nicht mehr sichtbaren) Kontextzeile.
        await loeschKnopf(page, 'pos-1').click();
        await expect(page.getByText(/^Ungespeichert$/)).toBeVisible();
        await keinUeberlauf();
    });

    test('Löschen → Strg+Z → Strg+Y stellt die Position wieder her bzw. löscht erneut', async ({ page }) => {
        await stubbeDokumentEditorApi(page, { dokument: { positionenJson: DREI_POSITIONEN } });
        await oeffneDokumentEditor(page);

        await loeschKnopf(page, 'pos-2').click();
        await expect(page.locator('[data-block-id="pos-2"]')).toHaveCount(0);

        await page.keyboard.press('Control+z');
        await expect(page.locator('[data-block-id="pos-2"]')).toHaveCount(1);
        await expect(page.locator('[data-block-id="pos-2"] [data-verlauf-feld="title"]')).toHaveValue('Ziegel ersetzen');

        await page.keyboard.press('Control+y');
        await expect(page.locator('[data-block-id="pos-2"]')).toHaveCount(0);
    });

    test('Tippen wird zu einem Schritt: Strg+Z im Titelfeld stellt den ursprünglichen Titel wieder her', async ({ page }) => {
        await stubbeDokumentEditorApi(page, { dokument: { positionenJson: DREI_POSITIONEN } });
        await oeffneDokumentEditor(page);

        const titelfeld = page.locator('[data-block-id="pos-1"] [data-verlauf-feld="title"]');
        await expect(titelfeld).toHaveValue('Dachrinne reinigen');

        await titelfeld.click();
        await titelfeld.pressSequentially(' XY');
        await expect(titelfeld).toHaveValue('Dachrinne reinigen XY');

        // Belegt zugleich: das Kürzel greift auch mit Cursor im Eingabefeld.
        await page.keyboard.press('Control+z');
        await expect(titelfeld).toHaveValue('Dachrinne reinigen');
    });

    test('Liste nimmt mehrere Schritte auf einmal zurück', async ({ page }, testInfo) => {
        await stubbeDokumentEditorApi(page, { dokument: { positionenJson: DREI_POSITIONEN } });
        await oeffneDokumentEditor(page);

        await loeschKnopf(page, 'pos-3').click();
        await expect(page.locator('[data-block-id="pos-3"]')).toHaveCount(0);
        await loeschKnopf(page, 'pos-2').click();
        await expect(page.locator('[data-block-id="pos-2"]')).toHaveCount(0);
        await loeschKnopf(page, 'pos-1').click();
        await expect(page.locator('[data-block-id="pos-1"]')).toHaveCount(0);

        await page.getByRole('button', { name: 'Liste der letzten Änderungen' }).click();
        const eintraege = page.getByRole('menuitem');
        await expect(eintraege).toHaveCount(3);

        const dritterEintrag = eintraege.nth(2);
        await dritterEintrag.hover();
        await expect(page.getByText('3 Schritte rückgängig machen')).toBeVisible();

        // Screenshot bei offenem Menü: das Dokument ist an dieser Stelle leer
        // (alle drei Positionen geloescht), unter dem Menü liegt nichts
        // Interaktives -- genau der vom Plan geforderte Zustand fuer
        // keineUeberschneidungen.
        await designPruefung(page, testInfo, 'dokument-editor-rueckgaengig-liste', { primaerAktion: dritterEintrag });

        await dritterEintrag.click();
        await expect(page.locator('[data-block-id="pos-1"]')).toHaveCount(1);
        await expect(page.locator('[data-block-id="pos-2"]')).toHaveCount(1);
        await expect(page.locator('[data-block-id="pos-3"]')).toHaveCount(1);
    });

    test('Gebuchte Rechnung: weder Rückgängig noch Wiederholen sichtbar, Strg+Z ändert nichts', async ({ page }, testInfo) => {
        await stubbeDokumentEditorApi(page, {
            dokument: { positionenJson: DREI_POSITIONEN, gebucht: true },
        });
        await oeffneDokumentEditor(page);

        await expect(page.getByRole('button', { name: 'Rückgängig' })).toHaveCount(0);
        await expect(page.getByRole('button', { name: 'Wiederholen' })).toHaveCount(0);

        await page.keyboard.press('Control+z');
        await expect(page.locator('[data-block-id="pos-1"]')).toHaveCount(1);
        await expect(page.locator('[data-block-id="pos-1"] [data-verlauf-feld="title"]')).toHaveValue('Dachrinne reinigen');

        await designPruefung(page, testInfo, 'dokument-editor-rueckgaengig-gebucht');
    });
});
