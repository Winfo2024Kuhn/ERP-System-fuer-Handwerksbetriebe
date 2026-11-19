import type { Page, Request } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeDokumentEditorApi, oeffneDokumentEditor, type AusgangsDokumentStand } from './hilfen/dokument-editor';

/**
 * Abschluss-Block einer einfachen Rechnung, die den ganzen Auftrag abrechnet:
 * Links steht derselbe Abrechnungsstand wie im PDF, die Zeilen „Diese Rechnung“
 * und „Zusätzliche Leistungen“ lassen sich umbenennen und von der Rechnung nehmen.
 *
 * Rechenlogik und Einzelbedienung prüfen die Vitest-Tests neben den Dateien
 * (abschluss/*.test.ts*, ClosureBlock.test.tsx, index.test.tsx). Hier zählt
 * der echte Ablauf im Browser und das Layout in allen Größen.
 * Nur Dummy-Daten (DSGVO), /api ist gestubbt.
 */

const ANGEBOT_ID = 10;
const GELAENDER = { id: 's1', type: 'SERVICE', title: 'Geländer Stahl verzinkt', quantity: 1, unit: 'psch', price: 5404, fontSize: 10 };
const GLAS = { id: 's2', type: 'SERVICE', title: 'Treppengeländer Edelstahl mit Glasfüllung', quantity: 1, unit: 'psch', price: 1075.75, fontSize: 10 };

async function stubbeEinfacheRechnung(page: Page) {
    await stubbeDokumentEditorApi(page, {
        dokument: {
            vorgaengerId: ANGEBOT_ID,
            positionenJson: JSON.stringify({ blocks: [GELAENDER, GLAS], globalRabatt: 0 }),
        } as Partial<AusgangsDokumentStand>,
    });
    await page.route(`**/api/ausgangs-dokumente/${ANGEBOT_ID}/abrechnungsverlauf`, route => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
            basisdokumentId: ANGEBOT_ID,
            basisdokumentNummer: 'AN-2026-0010',
            basisdokumentTyp: 'ANGEBOT',
            basisdokumentBetragNetto: 5404,
            positionen: [{ id: 1, dokumentNummer: 'RE-2026/09/00001', typ: 'RECHNUNG', datum: '2026-09-20', betragNetto: 6479.75, storniert: false }],
            bereitsAbgerechnet: 6479.75,
            restbetrag: 0,
            basisdokumentPositionenJson: JSON.stringify({ blocks: [GELAENDER], globalRabatt: 0 }),
        }),
    }));
}

/** Wartet auf die nächste Vorschau, deren Abrechnungsstand die Bedingung erfüllt. */
function naechsteVorschau(page: Page, bedingung: (verlauf: Record<string, unknown>) => boolean): Promise<Request> {
    return page.waitForRequest(anfrage => {
        if (!anfrage.url().endsWith('/api/dokument-generator/preview')) return false;
        const verlauf = (anfrage.postDataJSON() as { abrechnungsverlauf?: Record<string, unknown> }).abrechnungsverlauf;
        return !!verlauf && bedingung(verlauf);
    }, { timeout: 15_000 });
}

test.describe('Dokument-Editor – Zeilen im Abrechnungsstand', () => {
    test('einfache Rechnung: Abrechnungsstand zeigen, Text ändern, Zeile ausblenden und zurückholen', async ({ page }, testInfo) => {
        await stubbeEinfacheRechnung(page);
        await oeffneDokumentEditor(page);

        const stand = page.getByRole('heading', { name: 'Abrechnungsstand' });
        await stand.scrollIntoViewIfNeeded();
        await expect(page.getByText('diese Rechnung', { exact: true })).toBeVisible();
        await expect(page.getByText('Zusätzliche Leistungen', { exact: true })).toBeVisible();
        await expect(page.getByText(/\+\s*1\.280,14 €/)).toBeVisible();

        const stifte = page.getByRole('button', { name: 'Text ändern' });
        const koerbe = page.getByRole('button', { name: 'Zeile von der Rechnung entfernen' });
        await expect(stifte).toHaveCount(2);
        await page.getByText('Zahlbetrag').scrollIntoViewIfNeeded();
        await designPruefung(page, testInfo, 'abschluss-zeilen-normal', { primaerAktion: stifte.first() });

        // Info-Button öffnen
        await page.getByRole('button', { name: /Mehr zu: Abrechnungsstand/ }).click();
        await expect(page.getByText(/Mit dem durchgestrichenen Auge verschwindet die Zeile/)).toBeVisible();
        await designPruefung(page, testInfo, 'abschluss-zeilen-info');
        await page.keyboard.press('Escape');

        // Text der Differenzzeile ändern
        await stifte.nth(1).click();
        const bezeichnung = page.getByRole('textbox', { name: 'Bezeichnung' });
        await expect(bezeichnung).toBeFocused();
        await expect(bezeichnung).toHaveAttribute('placeholder', 'Zusätzliche Leistungen');
        await bezeichnung.fill('Mehrleistung');
        await page.getByRole('textbox', { name: 'Unterzeile' }).fill('Glasfüllung statt Lochblech');
        await designPruefung(page, testInfo, 'abschluss-zeilen-bearbeiten');
        const mitText = naechsteVorschau(page, v => (v.differenzZeile as { bezeichnung?: string } | null)?.bezeichnung === 'Mehrleistung');
        await page.getByRole('textbox', { name: 'Unterzeile' }).press('Enter');
        await expect(page.getByText('Mehrleistung', { exact: true })).toBeVisible();
        const textAnfrage = await mitText;
        expect((textAnfrage.postDataJSON() as { abrechnungsverlauf: Record<string, unknown> }).abrechnungsverlauf.differenzZeile)
            .toEqual({ bezeichnung: 'Mehrleistung', unterzeile: 'Glasfüllung statt Lochblech', ausblenden: false });

        // Zeile „Diese Rechnung“ von der Rechnung nehmen
        const ausgeblendet = naechsteVorschau(page, v => (v.dieseRechnungZeile as { ausblenden?: boolean } | null)?.ausblenden === true);
        await koerbe.first().click();
        await expect(page.getByText('Steht nicht auf der Rechnung')).toBeVisible();
        await ausgeblendet;
        await designPruefung(page, testInfo, 'abschluss-zeilen-ausgeblendet', {
            primaerAktion: page.getByRole('button', { name: 'Wieder einblenden' }),
        });

        // Strg+Z holt die Zeile zurück
        await page.keyboard.press('Control+z');
        await expect(page.getByText('Steht nicht auf der Rechnung')).toHaveCount(0);
        await expect(page.getByText('Mehrleistung', { exact: true })).toBeVisible();
    });
});
