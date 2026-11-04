import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeDokumentEditorApi } from './hilfen/dokument-editor';

/**
 * Rechnung, die nur an einem Kunden hängt (kein Projekt, keine Anfrage, nicht
 * gebucht), mit gespeicherter abweichender Rechnungsadresse: Beim Öffnen stand
 * die Adresse leer da, und das nächste Speichern hätte sie gelöscht. Die
 * Auswahlregel prüfen die Unit-Tests (dokumentKontextAbbildung.test.ts,
 * useDokumentKontext.test.ts); hier zählt der Ablauf Öffnen → Zahlungsziel ändern →
 * speichern im Browser. Dummy-Daten, `/api` gestubbt.
 */

const KUNDE_ID = 7;
const GESPEICHERTE_ADRESSE = 'Max Mustermann\nAndere Str. 5\n12345 Musterstadt';

test('Dokument nur am Kunden zeigt die gespeicherte Rechnungsadresse und speichert sie wieder mit', async ({ page }, testInfo) => {
    const mitschrift = await stubbeDokumentEditorApi(page, {
        dokument: {
            kundeId: KUNDE_ID,
            rechnungsadresse: GESPEICHERTE_ADRESSE,
            rechnungsadresseOverride: GESPEICHERTE_ADRESSE,
        },
    });
    await page.route(`**/api/kunden/${KUNDE_ID}`, route => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
            name: 'Max Mustermann',
            strasse: 'Musterweg 1',
            plz: '12345',
            ort: 'Musterstadt',
            anrede: 'HERR',
            ansprechpartner: 'Mustermann',
        }),
    }));

    const kundeGeladen = page.waitForResponse(`**/api/kunden/${KUNDE_ID}`);
    await page.goto('/dokument-editor?dokumentId=1');
    await kundeGeladen;

    const adressBlock = page.locator('div.group').filter({ has: page.getByText('Rechnungsadresse', { exact: true }) });
    await expect(adressBlock).toContainText('Andere Str. 5');
    await expect(adressBlock).not.toContainText('Musterweg 1');
    await expect(adressBlock).not.toContainText('Keine Adresse verfügbar');

    await designPruefung(page, testInfo, 'dokument-editor-rechnungsadresse-kunde', {
        primaerAktion: page.getByRole('button', { name: /Speichern/i }),
    });

    // Eine andere Änderung als die Adresse: Zahlungsziel 14 → 7 Tage (bis 8 Tage ohne Rückfrage).
    const zahlungsziel = page.getByTestId('summenzeile').getByTitle('Zahlungsziel in Tagen');
    await expect(zahlungsziel).toHaveValue('14');
    await zahlungsziel.fill('7');
    await zahlungsziel.press('Enter');
    await expect(page.getByText('Ungespeichert', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: /Speichern/i }).click();

    await expect.poll(() => mitschrift.speicherAufrufe.at(-1)?.zahlungszielTage).toBe(7);
    expect(mitschrift.speicherAufrufe.at(-1)?.rechnungsadresseOverride).toBe(GESPEICHERTE_ADRESSE);
    await expect(adressBlock).toContainText('Andere Str. 5');
});
