import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeDokumentEditorApi, oeffneDokumentEditor } from './hilfen/dokument-editor';
import { formatDatum, isoDatum } from '../src/lib/datum';

/**
 * „Datum oben auf dem Dokument“: Beim endgültigen Drucken, Herunterladen und
 * Senden fragt der erste Dialog, welches Datum oben steht (Vorauswahl heute).
 * Alle Daten sind Dummy-Daten (DSGVO), `/api` ist gestubbt, kein Backend.
 */

const VOR_ZWEI_WOCHEN = (() => {
    const d = new Date();
    d.setDate(d.getDate() - 14);
    return isoDatum(d);
})();
const ALS_TEXT = formatDatum(VOR_ZWEI_WOCHEN);

test.describe('Dokument-Editor – Datum beim Versand', () => {
    test('Angebot von vor zwei Wochen per E-Mail: bisheriges Datum behalten', async ({ page }, testInfo) => {
        const mitschrift = await stubbeDokumentEditorApi(page, {
            dokument: { typ: 'ANGEBOT', dokumentNummer: 'AG-2026-0002', datum: VOR_ZWEI_WOCHEN },
        });
        const pdfAnfragen: Array<{ kopfdaten: { rechnungsDatum: string } }> = [];
        await page.route('**/api/dokument-generator/pdf', route => {
            pdfAnfragen.push(route.request().postDataJSON());
            return route.fulfill({ status: 200, contentType: 'application/pdf', body: Buffer.from('%PDF-1.4\n%%EOF') });
        });
        await page.route('**/api/email/template', route => route.fulfill({
            status: 200, contentType: 'application/json', body: JSON.stringify({ subject: 'Angebot', body: '<p>Guten Tag</p>' }),
        }));
        await page.route('**/api/ausgangs-dokumente/1/pdf-speichern', route => route.fulfill({
            status: 200, contentType: 'application/json', body: JSON.stringify({ dateiname: 'abc.pdf' }),
        }));
        await oeffneDokumentEditor(page);

        await page.getByTitle('Dokument per E-Mail senden').click();
        const dialog = page.getByRole('dialog', { name: 'Dokument per E-Mail senden' });
        const wahl = dialog.getByRole('radiogroup', { name: 'Datum oben auf dem Dokument' });
        await expect(wahl.getByRole('radio', { name: /^Heute/ })).toHaveAttribute('aria-checked', 'true');
        await designPruefung(page, testInfo, 'versand-datum-email', {
            primaerAktion: dialog.getByRole('button', { name: /Standard PDF/ }),
        });

        await dialog.getByRole('button', { name: 'Mehr zu: Datum oben auf dem Dokument' }).click();
        await expect(page.getByText('Entwürfe mit Wasserzeichen ändern das Datum nicht.')).toBeVisible();
        await designPruefung(page, testInfo, 'versand-datum-info');
        await page.keyboard.press('Escape');

        await wahl.getByRole('radio', { name: `Bisheriges Datum behalten (${ALS_TEXT})` }).click();
        await dialog.getByRole('button', { name: /Standard PDF/ }).click();
        await page.getByRole('dialog', { name: /online annehmen können/ })
            .getByRole('button', { name: /Nein, nur das PDF schicken/ }).click();

        await expect(page.getByPlaceholder('Name, Firma oder E-Mail eingeben')).toBeVisible();
        expect(pdfAnfragen).toHaveLength(1);
        expect(pdfAnfragen[0].kopfdaten.rechnungsDatum).toBe(VOR_ZWEI_WOCHEN);
        expect(mitschrift.speicherAufrufe.length).toBeGreaterThan(0);
        expect(mitschrift.speicherAufrufe.every(s => s.datum === VOR_ZWEI_WOCHEN)).toBe(true);
    });

    test('Rechnung: Datum im PDF-Dialog und in den Druckoptionen, Zukunft gesperrt', async ({ page }, testInfo) => {
        await stubbeDokumentEditorApi(page, { dokument: { datum: VOR_ZWEI_WOCHEN } });
        await oeffneDokumentEditor(page);

        await page.getByRole('button', { name: 'PDF', exact: true }).click();
        const pdfDialog = page.getByRole('dialog', { name: 'PDF exportieren' });
        await expect(pdfDialog.getByText(/^Zahlbar bis /)).toBeVisible();
        await designPruefung(page, testInfo, 'versand-datum-pdf', {
            primaerAktion: pdfDialog.getByRole('button', { name: /Standard PDF/ }),
        });
        await pdfDialog.getByRole('button', { name: 'Abbrechen' }).click();

        await page.getByRole('button', { name: 'Drucken' }).click();
        const druck = page.getByRole('dialog', { name: 'Druckoptionen' });
        await druck.getByRole('radio', { name: 'Anderes Datum …' }).click();
        await expect(druck.getByText('Bitte ein Datum auswählen.')).toBeVisible();
        await expect(druck.getByRole('button', { name: /Drucken & Buchen/ })).toBeDisabled();
        await designPruefung(page, testInfo, 'versand-datum-druck');
    });
});
