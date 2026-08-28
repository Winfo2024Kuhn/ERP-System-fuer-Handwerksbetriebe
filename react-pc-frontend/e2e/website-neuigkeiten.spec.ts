import { test, expect, type Page } from '@playwright/test';
import { stubbeWebsiteApi, oeffneNeuigkeiten } from './hilfen/api';
import { inhalt, projektsuche, warteAufProjektsuche } from './hilfen/seite';

/**
 * End-to-End-Tests fuer "Website - Neuigkeiten".
 *
 * Anlass war ein Fehler, den kein Unit-Test sehen konnte: der Assistent
 * schloss sich, sobald man ein Projekt gewaehlt hatte. Die Unit-Tests
 * ersetzten ProjektSearchModal durch eine Attrappe, die -- anders als das
 * echte Modal -- nach onSelect kein onClose ausloeste. Fuer den Nutzer sah
 * es aus, als tue "Neuer Beitrag" gar nichts.
 *
 * Deshalb laufen diese Tests gegen die echten Komponenten im echten Browser;
 * nur die Netzwerkschicht ist gestubbt.
 */

/** Klickt sich vom Start bis in den Textschritt durch. */
async function bisZumText(
    page: Page,
    weg: 'Selbst schreiben' | 'Von der KI vorschlagen lassen' = 'Selbst schreiben',
) {
    await page.getByRole('button', { name: 'Neuer Beitrag' }).click();
    await page.getByRole('button', { name: /Balkonanlage Musterstraße/ }).click();
    await expect(page.getByRole('heading', { name: 'Balkonanlage Musterstraße' })).toBeVisible();

    await page.getByRole('img', { name: 'balkon-vorher.jpg' }).click();
    await page.getByRole('button', { name: 'Weiter' }).click();
    await page.getByRole('button', { name: weg }).click();
}

/** Fuellt den Textschritt vollstaendig aus. */
async function fuelleText(page: Page, titel: string, kurz: string, text: string) {
    await page.getByLabel('Titel').fill(titel);
    await page.getByLabel('Kurzbeschreibung').fill(kurz);
    await page.locator('.ProseMirror').click();
    await page.keyboard.type(text);
}

test.describe('Website - Neuigkeiten', () => {
    test('zeigt Kopfzeile, beide Reiter und die Beitragsliste', async ({ page }) => {
        await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);

        await expect(inhalt(page).getByText('Website', { exact: true })).toBeVisible();
        await expect(page.getByRole('heading', { name: 'NEUIGKEITEN' })).toBeVisible();
        await expect(inhalt(page)
            .getByText('Beiträge für den Bereich Aktuelles auf der Firmen-Website pflegen.')).toBeVisible();

        await expect(page.getByRole('button', { name: /Beitrag erstellen/ })).toBeVisible();
        await expect(page.getByRole('button', { name: /Zahlen der Website/ })).toBeVisible();
        await expect(inhalt(page).getByText('Alte Dachrinne erneuert')).toBeVisible();
    });

    test('der Knopf "Neuer Beitrag" ist bedienbar und oeffnet den Assistenten', async ({ page }) => {
        await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);

        const knopf = page.getByRole('button', { name: 'Neuer Beitrag' });
        await expect(knopf).toBeEnabled();

        await knopf.click();

        await warteAufProjektsuche(page);
        await expect(page.getByRole('button', { name: /Balkonanlage Musterstraße/ })).toBeVisible();
    });

    /**
     * Der eigentliche Regressionstest zum gemeldeten Fehler.
     */
    test('bleibt nach dem Waehlen eines Projekts offen und geht zum Bilderschritt', async ({ page }) => {
        await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);

        await page.getByRole('button', { name: 'Neuer Beitrag' }).click();
        await page.getByRole('button', { name: /Balkonanlage Musterstraße/ }).click();

        // Frueher schloss sich hier der ganze Assistent.
        await expect(page.getByRole('heading', { name: 'Balkonanlage Musterstraße' })).toBeVisible();
        await expect(page.getByRole('heading', { name: /Aus dem Bautagebuch/ })).toBeVisible();
        await expect(page.getByText('Noch kein Bild ausgewählt.')).toBeVisible();

        // Die Projektsuche selbst ist zu, der Assistent aber offen.
        await expect(projektsuche(page)).toBeHidden();
    });

    test('legt genau einen Beitrag an und veroeffentlicht ihn', async ({ page }) => {
        const mitschrift = await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);

        await bisZumText(page);
        await fuelleText(page, 'Neuer Balkon in zwei Tagen', 'Alte Konstruktion raus, neue rein.',
            'Wir haben die alte Unterkonstruktion abgebaut und neu gestellt.');

        await page.getByRole('button', { name: 'Veröffentlichen' }).click();

        // Erst abwarten, bis der Assistent wirklich zu ist: das Speichern
        // laeuft in mehreren Schritten (anlegen, Bild, Titelbild, Status).
        await expect(page.getByRole('button', { name: 'Veröffentlichen' })).toBeHidden();

        // Die Liste zeigt den neuen Beitrag ganz oben, samt Titelbild.
        await expect(inhalt(page).getByRole('button', { name: /Neuer Balkon in zwei Tagen/ }))
            .toBeVisible();

        // Genau ein POST -- kein doppelt angelegter Beitrag.
        expect(mitschrift.angelegt).toHaveLength(1);
        expect(mitschrift.angelegt[0].title).toBe('Neuer Balkon in zwei Tagen');
        expect(mitschrift.statusWechsel).toEqual([{ id: 100, status: 'published' }]);
        expect(mitschrift.bildUploads).toEqual([100]);
    });

    test('speichert auf Wunsch nur als Entwurf', async ({ page }) => {
        const mitschrift = await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);

        await bisZumText(page);
        await fuelleText(page, 'Dachrinne gereinigt', 'Kurz und schmerzlos.',
            'Laub raus, Rinne gespült, fertig.');

        await page.getByRole('button', { name: 'Als Entwurf speichern' }).click();

        await expect(page.getByRole('button', { name: 'Als Entwurf speichern' })).toBeHidden();
        await expect(inhalt(page).getByRole('button', { name: /Dachrinne gereinigt/ })).toBeVisible();
        expect(mitschrift.angelegt).toHaveLength(1);
        expect(mitschrift.statusWechsel).toEqual([]);
    });

    test('sperrt Speichern, solange Titel, Kurzbeschreibung oder Text fehlen', async ({ page }) => {
        await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);

        await bisZumText(page);

        await expect(page.getByRole('button', { name: 'Veröffentlichen' })).toBeDisabled();
        await expect(page.getByRole('button', { name: 'Als Entwurf speichern' })).toBeDisabled();

        await page.getByLabel('Titel').fill('Nur ein Titel');
        await expect(page.getByRole('button', { name: 'Veröffentlichen' })).toBeDisabled();
    });

    test('schliesst den Assistenten ueber das X, ohne etwas anzulegen', async ({ page }) => {
        const mitschrift = await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);

        await bisZumText(page);
        await page.getByRole('button', { name: 'Assistent schließen' }).click();

        await expect(page.getByRole('button', { name: 'Neuer Beitrag' })).toBeVisible();
        expect(mitschrift.angelegt).toHaveLength(0);
    });

    test('bricht ueber das X der Projektsuche den ganzen Assistenten ab', async ({ page }) => {
        await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);

        await page.getByRole('button', { name: 'Neuer Beitrag' }).click();
        await warteAufProjektsuche(page);

        // Das X sitzt im Kopf des Modals, direkt neben dem Suchfeld.
        await page.locator('div').filter({ has: projektsuche(page) })
            .last().locator('xpath=..').getByRole('button').first().click();

        await expect(projektsuche(page)).toBeHidden();
        await expect(page.getByRole('button', { name: 'Neuer Beitrag' })).toBeVisible();
    });

    test('geht im Assistenten wieder einen Schritt zurueck', async ({ page }) => {
        await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);

        await bisZumText(page);
        await expect(page.getByLabel('Titel')).toBeVisible();

        await page.getByRole('button', { name: 'Zurück' }).click();
        await expect(page.getByRole('button', { name: 'Selbst schreiben' })).toBeVisible();

        await page.getByRole('button', { name: 'Zurück' }).click();
        await expect(page.getByRole('heading', { name: /Aus dem Bautagebuch/ })).toBeVisible();
    });

    test('wechselt auf "Zahlen der Website" und zurueck', async ({ page }) => {
        await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);

        await page.getByRole('button', { name: /Zahlen der Website/ }).click();
        await expect(page.getByTestId('tab-insights')).toBeVisible();

        await page.getByRole('button', { name: /Beitrag erstellen/ }).click();
        await expect(page.getByTestId('tab-beitraege')).toBeVisible();
    });

    test('oeffnet einen bestehenden Beitrag zum Bearbeiten', async ({ page }) => {
        await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);

        await inhalt(page).getByText('Alte Dachrinne erneuert').click();

        await expect(page.getByLabel('Titel')).toHaveValue('Alte Dachrinne erneuert');
    });
});

test.describe('Vorschaubild in der Beitragsliste', () => {
    test('zeigt das Titelbild statt eines Platzhalter-Symbols', async ({ page }) => {
        await stubbeWebsiteApi(page);
        // Das Bild selbst kommt ueber die Durchreiche des ERP.
        await page.route('**/api/beitraege/bild/**', route => route.fulfill({
            status: 200,
            contentType: 'image/png',
            body: Buffer.from(
                'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
                'base64'),
        }));
        await oeffneNeuigkeiten(page);

        const bild = page.getByRole('img', { name: 'Titelbild von Alte Dachrinne erneuert' });
        await expect(bild).toBeVisible();

        // Wirklich geladen, nicht nur im DOM.
        await expect.poll(() => bild.evaluate((el: HTMLImageElement) => el.naturalWidth))
            .toBeGreaterThan(0);
    });

    test('faellt auf ein Symbol zurueck, wenn kein Titelbild da ist', async ({ page }) => {
        await stubbeWebsiteApi(page, {
            beitraege: [{
                id: 8, slug: 'ohne-bild', title: 'Beitrag ohne Bild',
                excerpt: 'Kurztext.', content: '<p>Text.</p>',
                status: 'draft', publishedAt: null, coverImagePath: null, images: [],
            }],
        });
        await oeffneNeuigkeiten(page);

        await expect(inhalt(page).getByText('Beitrag ohne Bild')).toBeVisible();
        await expect(page.getByRole('img', { name: /Titelbild von/ })).toHaveCount(0);
    });

    test('faellt auf ein Symbol zurueck, wenn das Bild nicht laedt', async ({ page }) => {
        await stubbeWebsiteApi(page);
        // Durchreiche antwortet mit 404 -- z.B. Website gerade weg.
        await page.route('**/api/beitraege/bild/**', route => route.fulfill({ status: 404, body: '' }));
        await oeffneNeuigkeiten(page);

        // Die Kachel bleibt, das kaputte Bild verschwindet: kein Bruchsymbol.
        await expect(inhalt(page).getByText('Alte Dachrinne erneuert')).toBeVisible();
        await expect(page.getByRole('img', { name: /Titelbild von/ })).toHaveCount(0);
    });
});

test.describe('Fehler- und Leerzustaende', () => {
    test('meldet eine nicht erreichbare Website verstaendlich', async ({ page }) => {
        await stubbeWebsiteApi(page, { beitraegeFehler: true });
        await oeffneNeuigkeiten(page);

        await expect(inhalt(page).getByText(/nicht erreichbar/)).toBeVisible();
        await expect(page.getByRole('button', { name: 'Erneut versuchen' })).toBeVisible();
    });

    test('erklaert ein Projekt ohne Bilder, statt leer zu bleiben', async ({ page }) => {
        await stubbeWebsiteApi(page, { ohneProjektBilder: true });
        await oeffneNeuigkeiten(page);

        await page.getByRole('button', { name: 'Neuer Beitrag' }).click();
        await page.getByRole('button', { name: /Balkonanlage Musterstraße/ }).click();

        await expect(page.getByText('Zu diesem Projekt gibt es noch keine Bilder.')).toBeVisible();
    });

    test('zeigt einen Hinweis, wenn noch kein Beitrag angelegt ist', async ({ page }) => {
        await stubbeWebsiteApi(page, { beitraege: [] });
        await oeffneNeuigkeiten(page);

        await expect(page.getByText('Noch kein Beitrag angelegt.')).toBeVisible();
        await expect(page.getByText('Links einen Beitrag wählen oder einen neuen anlegen.')).toBeVisible();
    });
});
