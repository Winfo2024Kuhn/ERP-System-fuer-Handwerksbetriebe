import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeDokumentEditorApi, oeffneDokumentEditor } from './hilfen/dokument-editor';

/**
 * Briefanrede im Dokument-Editor: Der Textbaustein `{{Anrede}} {{Ansprechpartner}},`
 * zeigte bei einer Behörde ohne Ansprechpartner „DAMEN_HERREN ,“. Richtig ist
 * „Sehr geehrte Damen und Herren,“. Die Regel selbst prüfen die Unit-Tests
 * (lib/briefanrede.test.ts, platzhalterAufloesen.test.ts); hier zählt, dass der
 * Editor sie mit den echten Kundendaten anzeigt. Dummy-Daten, `/api` gestubbt.
 */

const KUNDE_ID = 7;

const ANSCHREIBEN = JSON.stringify({
    blocks: [{
        id: 'text-1',
        type: 'TEXT',
        content: '<p>{{Anrede}} {{Ansprechpartner}},</p><p>vielen Dank für Ihre Anfrage.</p>',
    }],
    globalRabatt: 0,
});

test('Behörde ohne Ansprechpartner bekommt „Sehr geehrte Damen und Herren,“', async ({ page }, testInfo) => {
    await stubbeDokumentEditorApi(page, {
        dokument: {
            typ: 'ANGEBOT',
            dokumentNummer: 'AN-2026/11/00001',
            kundeId: KUNDE_ID,
            rechnungsadresseOverride: '',
            positionenJson: ANSCHREIBEN,
        },
    });
    await page.route(`**/api/kunden/${KUNDE_ID}`, route => route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
            name: 'Landkreis Musterstadt Abfallwirtschaft',
            strasse: 'Musterweg 1',
            plz: '12345',
            ort: 'Musterstadt',
            anrede: 'DAMEN_HERREN',
            ansprechpartner: '',
        }),
    }));

    await oeffneDokumentEditor(page);

    const anschreiben = page.getByText('Sehr geehrte Damen und Herren,', { exact: true });
    await expect(anschreiben).toBeVisible();
    await expect(page.getByText('DAMEN_HERREN')).toHaveCount(0);
    await expect(page.getByText('vielen Dank für Ihre Anfrage.')).toBeVisible();

    await designPruefung(page, testInfo, 'dokument-editor-briefanrede', {
        primaerAktion: page.getByRole('button', { name: /Speichern/i }),
    });
});
