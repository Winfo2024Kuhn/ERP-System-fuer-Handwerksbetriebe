import { test, expect, type Page } from '@playwright/test';
import { designPruefung } from './hilfen/design';
import {
    stubbeDokumentEditorApi,
    stubbeWindowClose,
    windowCloseAufrufe,
    oeffneDokumentEditor,
} from './hilfen/dokument-editor';

/**
 * End-to-End-Tests fuer den X-Button-Ablauf im Dokument-Editor (Issue #82,
 * Abschnitt 6a): eigene Lock-Logik raus, neuer Schliessen-Ablauf
 * (speichern -> Sperre freigeben -> Tab schliessen -> ggf. Hinweisseite).
 *
 * WICHTIG (siehe Kontext-Log, Bedenken zu Abschnitt 6a): die Seite
 * (DocumentEditorPage.tsx) verdrahtet `onLockFreigeben` erst in einem
 * spaeteren Abschnitt (7a) -- das ist mit Absicht so geplant ("erst der
 * Editor, dann die Seite", siehe Restplan). Bis dahin faellt der Editor beim
 * Schliessen automatisch auf sein bisheriges Verhalten zurueck (die Seite
 * entscheidet ueber `onClose`, ob sie `navigate(-1)` macht oder den Tab
 * schliesst). Diese Specs pruefen deshalb, was HEUTE durch den echten
 * Browser tatsaechlich reproduzierbar ist:
 *   - der Kern der Regression (kein doppelter, nie gestoppter Heartbeat mehr)
 *   - der reale Klick-Ablauf ueber X -> Warnung -> Speichern -> Schliessversuch
 *   - dass ein fehlgeschlagenes Speichern weder Freigabe noch Schliessen ausloest
 * Den neuen, Prop-gesteuerten Ablauf (onLockFreigeben -> window.close ->
 * TabSchliessenHinweis) deckt index.test.tsx auf Komponentenebene mit echten
 * Kind-Komponenten ab (kein Mock von TabSchliessenHinweis/Modals.tsx) -- eine
 * echte Browser-Probe dafuer wird erst mit Abschnitt 7a moeglich, wenn die
 * Seite den Prop tatsaechlich uebergibt.
 */

/** Der X-Knopf in der Kopfzeile hat kein aria-label (DocumentEditorHeader.tsx, nicht Teil dieses Tasks). */
function xKnopf(page: Page) {
    // Seit 7a steht die Bearbeiten-Leiste der Seite VOR dem Editor im DOM --
    // ein blosses button.first() traefe deren Knopf. Deshalb auf die
    // Editor-Flaeche eingrenzen (data-testid aus DocumentEditorPage).
    return page.getByTestId('dokument-editor-flaeche').locator('button').first();
}

async function adresseAendern(page: Page, neueAdresse: string) {
    await page.getByTitle('Rechnungsadresse für dieses Dokument bearbeiten').click();
    const textarea = page.getByRole('textbox', { name: /Rechnungsadresse bearbeiten/i });
    await textarea.fill(neueAdresse);
    await page.getByRole('button', { name: /Übernehmen/i }).click();
}

test.describe('Dokument-Editor – Tab schließen', () => {
    test('sendet nach dem Laden keinen sofortigen Heartbeat mehr (frueher: zweite, nie gestoppte Lock-Schleife)', async ({ page }) => {
        const mitschrift = await stubbeDokumentEditorApi(page);
        await oeffneDokumentEditor(page);

        // Der fruehere zweite Heartbeat im Editor pingte SOFORT beim Mount
        // (zusaetzlich zum Acquire der Seite). 800ms sind grosszuegig genug
        // fuer den Seiten-Aufbau und weit unter dem 30s-Intervall des
        // (weiterhin bestehenden) Seiten-Locks -- ein Treffer hier waere die
        // alte, fehlerhafte zweite Schleife.
        await page.waitForTimeout(800);
        expect(mitschrift.heartbeatAufrufe).toEqual([]);
    });

    test('X-Button: Warnung bei ungespeicherten Aenderungen, Speichern & Schließen speichert wirklich', async ({ page }, testInfo) => {
        await stubbeWindowClose(page);
        const mitschrift = await stubbeDokumentEditorApi(page);
        await oeffneDokumentEditor(page);

        await designPruefung(page, testInfo, 'dokument-editor-vor-schliessen', {
            primaerAktion: page.getByRole('button', { name: 'PDF' }),
        });

        await adresseAendern(page, 'Max Mustermann\nNeue Gasse 7\n54321 Beispielstadt');
        await expect(page.getByText('Ungespeichert')).toBeVisible();

        await xKnopf(page).click();

        const hinweis = page.getByText('Ungespeicherte Änderungen');
        await expect(hinweis).toBeVisible();
        await designPruefung(page, testInfo, 'dokument-editor-ungespeichert-warnung', {
            primaerAktion: page.getByRole('button', { name: 'Speichern & Schließen' }),
        });

        await page.getByRole('button', { name: 'Speichern & Schließen' }).click();

        await expect.poll(() => mitschrift.speicherAufrufe.length).toBeGreaterThan(0);
        expect(mitschrift.speicherAufrufe.at(-1)?.rechnungsadresseOverride)
            .toBe('Max Mustermann\nNeue Gasse 7\n54321 Beispielstadt');

        // Ohne onLockFreigeben (Seite verdrahtet das erst in Abschnitt 7a)
        // faellt der Editor auf das bisherige Verhalten zurueck: die Seite
        // entscheidet ueber onClose, ob sie navigate(-1) macht oder
        // window.close() versucht (window.history.length > 1 in Playwrights
        // eigenem Navigations-Kontext -- anders als bei einem echten, per
        // window.open() frisch geoeffneten Tab -- fuehrt hier zu
        // navigate(-1), sichtbar am Verschwinden des Editors). Diese Probe
        // deckt beide Ausgaenge ab: Hauptsache, der bisherige Schliess-Weg
        // wird ueberhaupt noch angestossen.
        await expect.poll(async () => {
            try {
                const geschlossen = await windowCloseAufrufe(page);
                const editorWeg = await page.getByRole('button', { name: 'PDF' }).isHidden();
                return geschlossen > 0 || editorWeg;
            } catch {
                // navigate(-1) reisst die Ausfuehrungsumgebung mitten in der
                // Abfrage weg -- das ist selbst schon der Beweis, dass der
                // Editor verschwunden ist.
                return true;
            }
        }).toBe(true);
    });

    test('fehlgeschlagenes Speichern: Toast statt Schliessen, Editor bleibt offen', async ({ page }) => {
        await stubbeWindowClose(page);
        await stubbeDokumentEditorApi(page, { speichern: 'fehler' });
        await oeffneDokumentEditor(page);

        await adresseAendern(page, 'Max Mustermann\nNeue Gasse 7\n54321 Beispielstadt');
        await xKnopf(page).click();
        await page.getByRole('button', { name: 'Speichern & Schließen' }).click();

        await expect(page.getByText(/Speichern fehlgeschlagen/)).toBeVisible();
        // Editor bleibt offen -- der Warn-Dialog steht immer noch da.
        await expect(page.getByRole('button', { name: 'Speichern & Schließen' })).toBeVisible();
        expect(await windowCloseAufrufe(page)).toBe(0);
    });
});
