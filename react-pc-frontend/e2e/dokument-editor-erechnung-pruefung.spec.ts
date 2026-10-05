import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeDokumentEditorApi, oeffneDokumentEditor, BEISPIEL_DOKUMENT } from './hilfen/dokument-editor';

/**
 * End-to-End-Probe fuer die Firmendaten-Pruefung vor dem E-Rechnung-Export.
 *
 * Der Fehler dahinter: Fehlten Name, Anschrift oder Steuernummer des Betriebs,
 * wurde die Rechnung erst gebucht und gesperrt, danach scheiterte das
 * ZUGFeRD-PDF mit einem allgemeinen "fehlgeschlagen". Jetzt fragt der Editor
 * VOR dem Buchen /api/dokument-generator/zugferd-pruefung; bei 422 zeigt er
 * die Meldung des Servers und bucht nichts.
 *
 * Alle Daten sind Dummy-Daten (DSGVO), `/api` ist gestubbt, kein Backend.
 */

const MELDUNG = 'Für die E-Rechnung fehlen Angaben zu Ihrem Betrieb: Straße, Ort. Bitte unter „Firma“ ergänzen.';

interface ExportMitschrift {
    buchungen: number;
    zugferdPdfAufrufe: number;
}

async function stubbeERechnungExport(page: Page, pruefung: 'ok' | 'unvollstaendig'): Promise<ExportMitschrift> {
    const mitschrift: ExportMitschrift = { buchungen: 0, zugferdPdfAufrufe: 0 };

    await page.route('**/api/dokument-generator/zugferd-pruefung', route => (pruefung === 'ok'
        ? route.fulfill({ status: 204, body: '' })
        : route.fulfill({
            status: 422,
            contentType: 'application/json',
            body: JSON.stringify({ status: 422, message: MELDUNG }),
        })));

    await page.route('**/api/ausgangs-dokumente/1/buchen', route => {
        mitschrift.buchungen += 1;
        return route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify({ ...BEISPIEL_DOKUMENT, gebucht: true }),
        });
    });

    await page.route('**/api/dokument-generator/zugferd-pdf', route => {
        mitschrift.zugferdPdfAufrufe += 1;
        return route.fulfill({
            status: 200,
            contentType: 'application/pdf',
            body: Buffer.from('%PDF-1.4\n%%EOF'),
        });
    });

    return mitschrift;
}

async function waehleERechnungImExport(page: Page) {
    await page.getByRole('button', { name: 'PDF', exact: true }).click();
    await page.getByRole('button', { name: /ZUGFeRD PDF/ }).click();
}

test.describe('Dokument-Editor – E-Rechnung prüft Firmendaten vor dem Buchen', () => {
    test('zeigt die Meldung des Servers und bucht nicht, wenn Firmendaten fehlen', async ({ page }, testInfo) => {
        await stubbeDokumentEditorApi(page);
        const mitschrift = await stubbeERechnungExport(page, 'unvollstaendig');
        await oeffneDokumentEditor(page);

        await waehleERechnungImExport(page);

        await expect(page.getByText(MELDUNG)).toBeVisible();
        await designPruefung(page, testInfo, 'erechnung-firmendaten-fehlen');
        expect(mitschrift.buchungen).toBe(0);
        expect(mitschrift.zugferdPdfAufrufe).toBe(0);
    });

    test('bucht und erzeugt das ZUGFeRD-PDF, wenn die Firmendaten reichen', async ({ page }) => {
        await stubbeDokumentEditorApi(page);
        const mitschrift = await stubbeERechnungExport(page, 'ok');
        await oeffneDokumentEditor(page);

        await waehleERechnungImExport(page);

        await expect.poll(() => mitschrift.zugferdPdfAufrufe).toBe(1);
        await expect(page.getByText(MELDUNG)).toHaveCount(0);
    });
});
