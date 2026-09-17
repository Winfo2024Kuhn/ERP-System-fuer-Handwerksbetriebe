import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { stubbeDokumentEditorApi, oeffneDokumentEditor } from './hilfen/dokument-editor';

/**
 * End-to-End-Probe fuer das Zahlungsziel im Dokument-Editor.
 *
 * Der Fehler dahinter: `handleSave` schickte `zahlungszielTage` weder im PUT
 * noch im POST mit, und eine reine Zahlungsziel-Aenderung galt nicht als
 * ungespeichert. Im PDF stand dann der neue Wert, in der Datenbank der alte --
 * die Faelligkeit in Offenen Posten und Mahnwesen passte nicht zur Rechnung.
 *
 * Die Unit-Tests in index.test.tsx pruefen dasselbe mit gemocktem `fetch`.
 * Hier zaehlt der echte Weg durch den Browser: Eingabe in der Summenzeile,
 * Rueckfrage ab neun Tagen, Hinweis "Ungespeichert" und der tatsaechlich
 * abgeschickte PUT-Body. Alle Daten sind Dummy-Daten (DSGVO), `/api` ist
 * gestubbt, kein Backend.
 */

/**
 * Das Zahlungsziel-Feld in der Summenzeile (SummenFooter). Bewusst auf die
 * Fusszeile eingegrenzt: bei offenem Chip-Popover gibt es ein zweites Feld mit
 * demselben Titel, und ein ungebundener Locator liefe in Playwrights
 * Strict-Mode-Fehler.
 */
function zahlungszielFeld(page: Page) {
    return page.getByTestId('summenzeile').getByTitle('Zahlungsziel in Tagen');
}

/** Tippt ein Zahlungsziel und schliesst die Eingabe mit Enter ab. */
async function zahlungszielEingeben(page: Page, wert: string) {
    await zahlungszielFeld(page).fill(wert);
    await zahlungszielFeld(page).press('Enter');
}

/**
 * Textbaustein mit beiden Zahlungsziel-Platzhaltern: daraus macht der Editor
 * den Tage-Chip und den Faelligkeitsdatum-Chip.
 */
const TEXTBAUSTEIN_MIT_CHIPS = JSON.stringify({
    blocks: [{
        id: 'text-1',
        type: 'TEXT',
        content: '<p>Zahlbar innerhalb von {{ZAHLUNGSZIEL_TAGE}} Tagen, also bis {{ZAHLUNGSZIEL}}.</p>',
    }],
    globalRabatt: 0,
});

/** Das Faelligkeitsdatum, das der Chip nach der Umstellung auf 30 Tage zeigen muss. */
function faelligkeitInDreissigTagen(): string {
    const faellig = new Date();
    faellig.setDate(faellig.getDate() + 30);
    return faellig.toLocaleDateString('de-DE');
}

test.describe('Dokument-Editor – Zahlungsziel', () => {
    test('fragt ab neun Tagen nach, meldet sich als ungespeichert und speichert den Wert mit', async ({ page }, testInfo) => {
        const mitschrift = await stubbeDokumentEditorApi(page);
        await oeffneDokumentEditor(page);

        // Positiv-Anker: das gespeicherte Zahlungsziel des Dokuments steht da.
        await expect(zahlungszielFeld(page)).toHaveValue('14');
        await zahlungszielEingeben(page, '30');

        const rueckfrage = page.getByRole('button', { name: 'Ja, 30 Tage' });
        await expect(rueckfrage).toBeVisible();
        await designPruefung(page, testInfo, 'zahlungsziel-rueckfrage', { primaerAktion: rueckfrage });
        await rueckfrage.click();

        const speichern = page.getByRole('button', { name: /Speichern/i });
        await expect(page.getByText('Ungespeichert', { exact: true })).toBeVisible();
        await designPruefung(page, testInfo, 'zahlungsziel-geaendert', { primaerAktion: speichern });

        await speichern.click();

        await expect.poll(() => mitschrift.speicherAufrufe.at(-1)?.zahlungszielTage).toBe(30);
        await expect(page.getByText('Ungespeichert', { exact: true })).toBeHidden();
        await expect(zahlungszielFeld(page)).toHaveValue('30');
        await designPruefung(page, testInfo, 'zahlungsziel-gespeichert', { primaerAktion: speichern });
    });

    test('weist ein Zahlungsziel außerhalb der Grenzen mit einer Meldung ab', async ({ page }, testInfo) => {
        const mitschrift = await stubbeDokumentEditorApi(page);
        await oeffneDokumentEditor(page);

        await expect(zahlungszielFeld(page)).toHaveValue('14');
        await zahlungszielEingeben(page, '400');

        await expect(page.getByText(/zwischen 1 und 365 Tagen/i)).toBeVisible();
        await designPruefung(page, testInfo, 'zahlungsziel-abgewiesen', {
            primaerAktion: page.getByRole('button', { name: /Speichern/i }),
        });
        // Der geltende Wert bleibt stehen, gespeichert wird nichts.
        await expect(zahlungszielFeld(page)).toHaveValue('14');
        await expect(page.getByText('Ungespeichert', { exact: true })).toBeHidden();
        expect(mitschrift.speicherAufrufe).toHaveLength(0);
    });

    test('Escape verwirft den Entwurf – auch wenn danach danebengeklickt wird', async ({ page }) => {
        // Der kritische Pfad: Escape setzt den Entwurf zurueck, und das
        // anschliessende Verlassen des Feldes darf den verworfenen Wert nicht
        // doch noch uebernehmen. In jsdom laesst sich diese Ereignisfolge nicht
        // nachstellen, im Browser schon.
        const mitschrift = await stubbeDokumentEditorApi(page);
        await oeffneDokumentEditor(page);

        await expect(zahlungszielFeld(page)).toHaveValue('14');
        await zahlungszielFeld(page).fill('30');
        await zahlungszielFeld(page).press('Escape');
        await expect(zahlungszielFeld(page)).toHaveValue('14');

        // Woanders hinklicken: jetzt faellt der echte Blur an.
        await page.getByText('Rechnungsadresse').click();

        await expect(page.getByRole('button', { name: 'Ja, 30 Tage' })).toHaveCount(0);
        await expect(page.getByText('Ungespeichert', { exact: true })).toBeHidden();
        expect(mitschrift.speicherAufrufe).toHaveLength(0);
    });

    test('übernimmt den Wert auch, wenn das Feld nur verlassen wird', async ({ page }) => {
        const mitschrift = await stubbeDokumentEditorApi(page);
        await oeffneDokumentEditor(page);

        await expect(zahlungszielFeld(page)).toHaveValue('14');
        // Bis acht Tage ohne Rueckfrage: hier geht es allein um den Abschluss
        // per Klick daneben statt per Enter.
        await zahlungszielFeld(page).fill('5');
        await page.getByText('Rechnungsadresse').click();

        await expect(page.getByText('Ungespeichert', { exact: true })).toBeVisible();
        await page.getByRole('button', { name: /Speichern/i }).click();
        await expect.poll(() => mitschrift.speicherAufrufe.at(-1)?.zahlungszielTage).toBe(5);
    });

    test('Chip im Textbaustein: Popover fragt nach und ändert dieselbe Zahl', async ({ page }, testInfo) => {
        // Zweiter Einsatzort derselben Eingabe. Der Textbaustein bringt beide
        // Zahlungsziel-Platzhalter mit, aus denen der Editor Chips macht.
        const mitschrift = await stubbeDokumentEditorApi(page, { dokument: { positionenJson: TEXTBAUSTEIN_MIT_CHIPS } });
        await oeffneDokumentEditor(page);

        const tageChip = page.locator('[data-zahlungsziel-chip="tage"]');
        const datumChip = page.locator('[data-zahlungsziel-chip="datum"]');
        await expect(tageChip).toHaveText('14');
        await tageChip.click();

        const popover = page.getByRole('dialog');
        const feldImPopover = popover.getByTitle('Zahlungsziel in Tagen');
        await expect(feldImPopover).toBeFocused();
        await designPruefung(page, testInfo, 'zahlungsziel-popover', { primaerAktion: feldImPopover });

        // Zweiter Chip bei offenem Popover: es wandert mit, statt zuzugehen.
        await datumChip.click();
        await expect(feldImPopover).toBeVisible();

        await feldImPopover.fill('30');
        await feldImPopover.press('Enter');
        await page.getByRole('button', { name: 'Ja, 30 Tage' }).click();

        // Beide Chips, Summenzeile und gespeicherter Wert zeigen denselben Stand.
        await expect(tageChip).toHaveText('30');
        await expect(datumChip).toHaveText(faelligkeitInDreissigTagen());
        await expect(zahlungszielFeld(page)).toHaveValue('30');
        await page.getByRole('button', { name: /Speichern/i }).click();
        await expect.poll(() => mitschrift.speicherAufrufe.at(-1)?.zahlungszielTage).toBe(30);
    });

    test('Klick auf normalen Text im Textbaustein öffnet kein Popover', async ({ page }) => {
        // Gegenprobe zum `preventDefault()` beim Chip: ausserhalb des Chips
        // gehoert der Klick weiterhin dem Editor.
        await stubbeDokumentEditorApi(page, { dokument: { positionenJson: TEXTBAUSTEIN_MIT_CHIPS } });
        await oeffneDokumentEditor(page);

        await page.getByText('Zahlbar innerhalb von').click();

        await expect(page.getByRole('dialog')).toHaveCount(0);
        await expect(page.locator('.ProseMirror')).toBeFocused();
    });

    test('gebuchte Rechnung: der Chip öffnet kein Popover', async ({ page }) => {
        await stubbeDokumentEditorApi(page, {
            dokument: { positionenJson: TEXTBAUSTEIN_MIT_CHIPS, gebucht: true },
        });
        await oeffneDokumentEditor(page);

        await page.locator('[data-zahlungsziel-chip="tage"]').click();

        await expect(page.getByRole('dialog')).toHaveCount(0);
        // Gegenprobe, dass wirklich der gesperrte Zustand vorliegt.
        await expect(page.getByText('Gebucht', { exact: true })).toBeVisible();
    });

    test('Auto-Save speichert eine reine Zahlungsziel-Aenderung auch ohne Klick', async ({ page }) => {
        // Der Auto-Save prueft alle 10 s; dazu kommt der Seitenaufbau auf einem
        // kalten Dev-Server. Das passt nicht in die 30-s-Voreinstellung.
        test.setTimeout(60_000);
        const mitschrift = await stubbeDokumentEditorApi(page);
        await oeffneDokumentEditor(page);

        await expect(zahlungszielFeld(page)).toHaveValue('14');
        // Bis acht Tage ohne Rueckfrage — hier geht es allein um den Auto-Save.
        await zahlungszielEingeben(page, '5');

        await expect
            .poll(() => mitschrift.speicherAufrufe.at(-1)?.zahlungszielTage, { timeout: 25_000 })
            .toBe(5);
    });
});
