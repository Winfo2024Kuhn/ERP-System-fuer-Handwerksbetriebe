import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeDokumentEditorApi, oeffneDokumentEditor } from './hilfen/dokument-editor';

/**
 * Rückfragen vor Export und Versand im Dokument-Editor:
 * - PDF einer offenen Rechnung: Warnung „wird gebucht und gesperrt“, Abbrechen bucht nicht.
 * - E-Mail eines Angebots: Rückfrage, wenn ein alter Online-Link ungültig wird,
 *   danach die Wahl „online annehmen?“.
 *
 * Alle Daten sind Dummy-Daten (DSGVO), `/api` ist gestubbt, kein Backend.
 */

async function zaehleBuchungen(page: Page): Promise<{ anzahl: number }> {
    const zaehler = { anzahl: 0 };
    await page.route('**/api/ausgangs-dokumente/1/buchen', route => {
        zaehler.anzahl += 1;
        return route.fulfill({ status: 500, body: 'darf nicht passieren' });
    });
    return zaehler;
}

test.describe('Dokument-Editor – Rückfragen vor Export und Versand', () => {
    test('PDF einer offenen Rechnung warnt vor dem Buchen; Abbrechen bucht nicht', async ({ page }, testInfo) => {
        await stubbeDokumentEditorApi(page);
        const buchungen = await zaehleBuchungen(page);
        await oeffneDokumentEditor(page);

        await page.getByRole('button', { name: 'PDF', exact: true }).click();
        await page.getByText('Standard PDF-Datei herunterladen').click();

        const warnung = page.getByRole('dialog', { name: 'Dokument exportieren?' });
        await expect(warnung).toContainText('gebucht und gesperrt');
        await designPruefung(page, testInfo, 'pdf-export-buchungswarnung');
        await warnung.getByRole('button', { name: 'Abbrechen' }).click();

        await expect(warnung).toHaveCount(0);
        expect(buchungen.anzahl).toBe(0);
    });

    test('Angebot: Rückfrage zum alten Online-Link, dann Wahl „online annehmen?“', async ({ page }, testInfo) => {
        await stubbeDokumentEditorApi(page, { dokument: { typ: 'ANGEBOT', dokumentNummer: 'AG-2026-0002' } });
        await page.route('**/api/ausgangs-dokumente/1/abzuloesende-freigaben', route => route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify([{ dokumentId: 2, dokumentNummer: 'AG-2026-0001', dokumentArt: 'Angebot' }]),
        }));
        await oeffneDokumentEditor(page);

        await page.getByTitle('Dokument per E-Mail senden').click();
        await page.getByText('PDF-Datei als E-Mail-Anhang versenden').click();

        const rueckfrage = page.getByRole('dialog', { name: 'Alter Online-Link wird ungültig' });
        await expect(rueckfrage).toContainText('Der Online-Link zu Angebot AG-2026-0001 wird damit ungültig');
        await designPruefung(page, testInfo, 'angebot-abloesung-rueckfrage');
        await rueckfrage.getByRole('button', { name: 'Trotzdem fortfahren' }).click();

        const wahl = page.getByRole('dialog', { name: /online annehmen können/ });
        await expect(wahl.getByRole('button', { name: /Ja, mit Link zum Annehmen/ })).toBeVisible();
        await expect(wahl.getByRole('button', { name: /Nein, nur das PDF schicken/ })).toBeVisible();
        await designPruefung(page, testInfo, 'angebot-online-annahme-wahl');
    });
});
