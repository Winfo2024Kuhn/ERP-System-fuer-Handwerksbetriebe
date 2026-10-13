import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { ALTE_VORLAGE_HTML, VORLAGE_NAME, oeffneFormularwesen, stubbeFormularwesenApi } from './hilfen/formularwesen';

/**
 * Vorlagen-Editor im Formularwesen: Anschrift verschieben, in der Vorschau den
 * Norm-Hinweis sehen, mit einem Klick zurück ins Fenster setzen, speichern.
 * Das gespeicherte HTML muss danach Zeichen für Zeichen der alten Vorlage
 * entsprechen – das Backend druckt die PDFs aus genau diesem Format.
 */
test('Anschrift aus dem Fenster gezogen, in der Vorschau korrigiert und unverändert gespeichert', async ({ page }, testInfo) => {
    const mitschrift = await stubbeFormularwesenApi(page);
    await oeffneFormularwesen(page);

    // Galerie → Vorlage öffnen
    const oeffnen = page.getByRole('button', { name: `Vorlage „${VORLAGE_NAME}“ bearbeiten` });
    await expect(oeffnen).toBeVisible();
    await designPruefung(page, testInfo, 'formularwesen-galerie', { ganzeSeite: true });
    await oeffnen.click();
    const editor = page.getByTestId('vorlagen-editor');
    await expect(editor.getByTestId('speicherstand')).toHaveText('Gespeichert');
    const hinweis = editor.getByTestId('anschrift-hinweis');
    await expect(hinweis).toHaveAttribute('data-status', 'passt');
    await designPruefung(page, testInfo, 'formularwesen-editor', {
        primaerAktion: page.getByRole('button', { name: 'Speichern' }),
    });

    // Anschrift mit der Maus nach oben aus dem Fenster ziehen
    const anschrift = editor.locator('[data-baustein="Anschrift"]');
    const rahmen = (await anschrift.boundingBox())!;
    await page.mouse.move(rahmen.x + rahmen.width / 2, rahmen.y + rahmen.height / 2);
    await page.mouse.down();
    await page.mouse.move(rahmen.x + rahmen.width / 2, rahmen.y + rahmen.height / 2 - 30, { steps: 4 });
    await page.mouse.move(rahmen.x + rahmen.width / 2, rahmen.y + rahmen.height / 2 - 80, { steps: 4 });
    await page.mouse.up();
    await expect(hinweis).toHaveAttribute('data-status', 'ragt-heraus');
    await expect(editor.getByTestId('speicherstand')).toHaveText('Ungespeicherte Änderungen');
    await expect(editor.getByTestId('editor-auswahl')).toContainText('Anschrift');
    await designPruefung(page, testInfo, 'formularwesen-editor-auswahl');

    // Vorschau zeigt den Hinweis samt Norm-Hilfen
    await page.getByRole('button', { name: 'Vorschau' }).click();
    const vorschau = page.getByRole('dialog', { name: 'Vorschau' });
    const vorschauHinweis = vorschau.getByTestId('anschrift-hinweis');
    await expect(vorschauHinweis).toContainText('Die Anschrift ragt aus dem Fenster');
    await expect(vorschau.getByTestId('norm-overlay')).toBeVisible();
    await designPruefung(page, testInfo, 'formularwesen-vorschau-hinweis', {
        primaerAktion: vorschau.getByRole('button', { name: 'Anschrift ins Fenster setzen', exact: true }),
    });

    // Info-Button erklärt die Korrektur
    await vorschau.getByRole('button', { name: 'Mehr zu: Anschrift ins Fenster setzen' }).click();
    await expect(page.getByRole('dialog', { name: 'Anschrift ins Fenster setzen' })).toContainText('Anschriftzone nach DIN 5008');
    await designPruefung(page, testInfo, 'formularwesen-vorschau-info');
    await page.keyboard.press('Escape');

    await vorschau.getByRole('button', { name: 'Anschrift ins Fenster setzen', exact: true }).click();
    await expect(vorschauHinweis).toHaveAttribute('data-status', 'passt');
    await expect(vorschauHinweis).toContainText('Anschrift sitzt im Fenster');

    await vorschau.getByRole('switch', { name: 'Umschlag-Ansicht' }).click();
    await designPruefung(page, testInfo, 'formularwesen-vorschau-umschlag');

    await page.keyboard.press('Escape');
    await expect(vorschau).toBeHidden();
    await expect(hinweis).toHaveAttribute('data-status', 'passt');

    // Speichern: Format unverändert
    const anfrage = page.waitForRequest(r => r.url().endsWith('/api/formulare/templates') && r.method() === 'POST');
    await page.getByRole('button', { name: 'Speichern' }).click();
    const daten = (await anfrage).postDataJSON() as { name: string; html: string; assignedDokumenttypen: string[] };
    expect(daten.name).toBe(VORLAGE_NAME);
    expect(daten.assignedDokumenttypen).toEqual(['Rechnung', 'Angebot']);
    expect(daten.html).toBe(ALTE_VORLAGE_HTML);
    await expect(editor.getByTestId('speicherstand')).toHaveText('Gespeichert');
    expect(mitschrift.gespeichert).toHaveLength(1);
});

test('Neue Vorlage mit Form A: Namensdialog, Speichern schreibt Name und Briefform ins gewohnte Format', async ({ page }, testInfo) => {
    const mitschrift = await stubbeFormularwesenApi(page);
    await oeffneFormularwesen(page);

    await page.getByRole('button', { name: 'Neue Vorlage', exact: true }).click();
    const editor = page.getByTestId('vorlagen-editor');
    await expect(editor.getByRole('button', { name: /Vorlage „Neue Vorlage“ umbenennen/ })).toBeVisible();
    await expect(editor.getByTestId('speicherstand')).toHaveText('Ungespeicherte Änderungen');

    // Briefform A: das Fenster rückt nach oben, die Anschrift des Standard-Layouts sitzt nicht mehr darin
    await editor.getByRole('radio', { name: 'Form A' }).click();
    await expect(editor.getByRole('radio', { name: 'Form A' })).toHaveAttribute('aria-checked', 'true');
    await expect(editor.getByTestId('anschrift-hinweis')).not.toHaveAttribute('data-status', 'passt');

    await page.getByRole('button', { name: 'Speichern' }).click();
    const dialog = page.getByRole('dialog', { name: 'Vorlage speichern' });
    await expect(dialog.getByLabel('Name der Vorlage')).toBeFocused();
    await designPruefung(page, testInfo, 'formularwesen-neue-vorlage-name', {
        primaerAktion: dialog.getByRole('button', { name: 'Speichern' }),
    });
    await dialog.getByLabel('Name der Vorlage').fill('Briefpapier Form A');

    const anfrage = page.waitForRequest(r => r.url().endsWith('/api/formulare/templates') && r.method() === 'POST');
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    const daten = (await anfrage).postDataJSON() as { name: string; html: string; assignedDokumenttypen: string[] };
    expect(daten.name).toBe('Briefpapier Form A');
    expect(daten.assignedDokumenttypen).toEqual([]);
    expect(daten.html).toMatch(/^<meta name="page-format" content="A4" \/><meta name="briefform" content="A" \/><style>/);
    expect(daten.html).toContain('<div class="page"><div data-block-type="logo" data-page="1"');
    expect(daten.html.match(/<div class="page">/g)).toHaveLength(2);

    await expect(dialog).toBeHidden();
    await expect(editor.getByTestId('speicherstand')).toHaveText('Gespeichert');
    await expect(editor.getByRole('button', { name: /Vorlage „Briefpapier Form A“ umbenennen/ })).toBeVisible();
    expect(mitschrift.gespeichert).toHaveLength(1);
});
