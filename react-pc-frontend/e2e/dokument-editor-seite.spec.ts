import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung } from './hilfen/design';
import { stubbeDokumentEditorApi, stubbeWindowClose, BEISPIEL_DOKUMENT } from './hilfen/dokument-editor';

/**
 * End-to-End-Test fuer DocumentEditorPage auf dem neuen, verallgemeinerten
 * Sperr-Fundament (useDatensatzLock/BearbeitenLeiste/GesperrtHinweis statt
 * useDocumentLock/DocumentLockedModal) -- Issue #82, Abschnitt 7a. Das ist
 * der letzte Verbraucher: mit dieser Spec ist der neue X-Button-Ablauf
 * (speichern -> Sperre freigeben -> window.close() -> TabSchliessenHinweis,
 * bisher nur ueber Komponententests belegt, siehe Abschnitt 6a/7c) zum
 * ERSTEN MAL ueber die echte Route im Browser erreichbar.
 *
 * Wiederverwendet `stubbeDokumentEditorApi` aus e2e/hilfen/dokument-editor.ts
 * fuer alles, was der Editor selbst braucht (Dokument, Firma, Vorlagen, PDF-
 * Vorschau) -- NUR gelesen, nicht veraendert. Die (neuen) Datensatz-Lock-
 * Routen stubbt diese Datei selbst, weil die geteilte Hilfsdatei noch die
 * ALTEN /api/dokument-locks/-Pfade bedient (das aeltere Seiten-Lock der
 * anderen Spec, dokument-editor-tab-schliessen.spec.ts, die bewusst nicht
 * Teil dieses Tasks ist).
 *
 * /api wird vollstaendig gestubbt (kein Backend). DSGVO: nur Dummy-Namen.
 */

const DOKUMENT_ID = 1;
const HALTER_NAME = 'Anna Beispiel';
const LOCK_FEHLER_TEXT = 'Sperre konnte nicht geholt werden — bitte neu laden.';

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

type AcquireVerhalten = 'frei' | 'fremd' | 'fehler';

function lockDto(
    overrides: Partial<{ status: 'ACQUIRED' | 'LOCKED_BY_OTHER'; holderDisplayName: string; acquiredAt: string }> = {},
) {
    return {
        status: 'ACQUIRED' as const,
        holderUserId: 9,
        holderDisplayName: HALTER_NAME,
        acquiredAt: new Date().toISOString(),
        lastHeartbeatAt: new Date().toISOString(),
        ...overrides,
    };
}

/** Stubbt die neuen Datensatz-Lock-Routen (AUSGANG) fuer das Beispieldokument. */
async function stubbeDatensatzLock(page: Page, verhalten: AcquireVerhalten, onDelete?: () => void) {
    await page.route(`**/api/datensatz-locks/AUSGANG/${DOKUMENT_ID}/acquire`, route => {
        if (verhalten === 'frei') return json(route, lockDto());
        if (verhalten === 'fremd') {
            return json(
                route,
                lockDto({
                    status: 'LOCKED_BY_OTHER',
                    holderDisplayName: HALTER_NAME,
                    acquiredAt: new Date(Date.now() - 5 * 60_000).toISOString(),
                }),
                409,
            );
        }
        return route.fulfill({ status: 500, body: '' });
    });
    await page.route(`**/api/datensatz-locks/AUSGANG/${DOKUMENT_ID}/heartbeat`, route => json(route, lockDto()));
    await page.route(`**/api/datensatz-locks/AUSGANG/${DOKUMENT_ID}`, route => {
        if (route.request().method() === 'DELETE') {
            onDelete?.();
            return route.fulfill({ status: 204, body: '' });
        }
        return route.fulfill({ status: 404, body: '' });
    });
}

async function oeffneSeite(page: Page) {
    await page.goto(`/dokument-editor?dokumentId=${DOKUMENT_ID}&dokumentTyp=${BEISPIEL_DOKUMENT.typ}`);
    await page.getByText('Musterweg 1').waitFor({ timeout: 10_000 });
}

/**
 * Der X-Knopf in der Kopfzeile hat kein aria-label (DocumentEditorHeader.tsx,
 * nicht Teil dieses Tasks). Er ist der erste Button INNERHALB des Editor-
 * Bereichs -- explizit dorthin eingegrenzt, weil die neue Bearbeiten-Leiste
 * der Seite jetzt DAVOR im DOM steht und sonst faelschlich "Fertig"/
 * "Bearbeiten" getroffen wuerde (siehe Kontext-Log, roter Test).
 */
function xKnopf(page: Page) {
    return page.getByTestId('dokument-editor-flaeche').locator('button').first();
}

async function adresseAendern(page: Page, neueAdresse: string) {
    await page.getByTitle('Rechnungsadresse für dieses Dokument bearbeiten').click();
    const textarea = page.getByRole('textbox', { name: /Rechnungsadresse bearbeiten/i });
    await textarea.fill(neueAdresse);
    await page.getByRole('button', { name: /Übernehmen/i }).click();
}

test.describe('DocumentEditorPage - Sperr-Fundament', () => {
    test('oeffnet mit freiem Lock editierbar, Leiste zeigt "Fertig"', async ({ page }, testInfo) => {
        await stubbeDokumentEditorApi(page);
        await stubbeDatensatzLock(page, 'frei');
        await oeffneSeite(page);

        const fertig = page.getByRole('button', { name: 'Fertig' });
        await expect(fertig).toBeVisible();
        await expect(page.getByTitle('Rechnungsadresse für dieses Dokument bearbeiten')).toBeVisible();

        await designPruefung(page, testInfo, 'editor-seite-bearbeiten', { primaerAktion: fertig });
    });

    test('"Fertig" gibt frei (readOnly, "Sie lesen nur mit."), "Bearbeiten" erwirbt danach neu', async ({ page }, testInfo) => {
        await stubbeDokumentEditorApi(page);
        await stubbeDatensatzLock(page, 'frei');
        await oeffneSeite(page);
        await page.getByRole('button', { name: 'Fertig' }).click();

        const bearbeiten = page.getByRole('button', { name: 'Bearbeiten' });
        await expect(bearbeiten).toBeEnabled();
        await expect(page.getByText('Sie lesen nur mit.')).toBeVisible();
        // Kein Bearbeiten-Zugriff mehr auf die Rechnungsadresse -- Editor ist readOnly.
        await expect(page.getByTitle('Rechnungsadresse für dieses Dokument bearbeiten')).toHaveCount(0);

        await designPruefung(page, testInfo, 'editor-seite-lesen', { primaerAktion: bearbeiten });

        await bearbeiten.click();

        await expect(page.getByRole('button', { name: 'Fertig' })).toBeVisible();
        await expect(page.getByTitle('Rechnungsadresse für dieses Dokument bearbeiten')).toBeVisible();
    });

    test('fremdes Lock (409): GesperrtHinweis mit Dummy-Namen, Editor readOnly', async ({ page }, testInfo) => {
        await stubbeDokumentEditorApi(page);
        await stubbeDatensatzLock(page, 'fremd');
        await oeffneSeite(page);

        await expect(page.getByText(HALTER_NAME)).toBeVisible();
        await expect(page.getByText(/bearbeitet das gerade/)).toBeVisible();
        await expect(page.getByTitle('Rechnungsadresse für dieses Dokument bearbeiten')).toHaveCount(0);

        const bearbeiten = page.getByRole('button', { name: 'Bearbeiten' });
        await expect(bearbeiten).toBeEnabled();

        await designPruefung(page, testInfo, 'editor-seite-gesperrt', { primaerAktion: bearbeiten });
    });

    test('Acquire-Fehler (500): Hinweis + Toast, Bearbeiten-Knopf deaktiviert mit Tooltip', async ({ page }, testInfo) => {
        await stubbeDokumentEditorApi(page);
        await stubbeDatensatzLock(page, 'fehler');
        await oeffneSeite(page);

        await expect(page.getByRole('alert')).toContainText(LOCK_FEHLER_TEXT);
        await expect(page.getByTestId('toast-container')).toContainText(LOCK_FEHLER_TEXT);
        // Derselbe Wortlaut steht doppelt im Dokument -- einmal auf der Seite, einmal im Toast.
        await expect(page.getByText(LOCK_FEHLER_TEXT)).toHaveCount(2);

        const bearbeiten = page.getByRole('button', { name: 'Bearbeiten' });
        await expect(bearbeiten).toBeDisabled();
        await expect(bearbeiten).toHaveAttribute('title', LOCK_FEHLER_TEXT);

        await designPruefung(page, testInfo, 'editor-seite-fehler', { primaerAktion: bearbeiten });
    });

    test('X-Button-Ablauf ueber die echte Route: speichern -> Sperre freigeben -> Tab-Schliessen-Hinweis', async ({ page }, testInfo) => {
        await stubbeWindowClose(page);
        const mitschrift = await stubbeDokumentEditorApi(page);
        let freigegeben = false;
        await stubbeDatensatzLock(page, 'frei', () => {
            freigegeben = true;
        });
        await oeffneSeite(page);
        await expect(page.getByRole('button', { name: 'Fertig' })).toBeVisible();

        await adresseAendern(page, 'Max Mustermann\nNeue Gasse 7\n54321 Beispielstadt');
        await expect(page.getByText('Ungespeichert')).toBeVisible();

        await xKnopf(page).click();
        await expect(page.getByText('Ungespeicherte Änderungen')).toBeVisible();
        await page.getByRole('button', { name: 'Speichern & Schließen' }).click();

        await expect.poll(() => mitschrift.speicherAufrufe.length).toBeGreaterThan(0);
        expect(mitschrift.speicherAufrufe.at(-1)?.rechnungsadresseOverride).toBe(
            'Max Mustermann\nNeue Gasse 7\n54321 Beispielstadt',
        );

        // Zum ERSTEN MAL ueber die echte Route erreichbar (siehe Kontext-Log,
        // Abschnitt 6/7-1): die Seite haelt jetzt tatsaechlich ein Lock, der
        // Editor uebernimmt daher nach dem Speichern selbst per
        // onLockFreigeben -> window.close() -> TabSchliessenHinweis.
        await expect.poll(() => freigegeben).toBe(true);
        await expect(page.getByRole('status')).toContainText('Sie können diesen Tab jetzt schließen');

        await designPruefung(page, testInfo, 'editor-seite-tab-schliessen');
    });
});
