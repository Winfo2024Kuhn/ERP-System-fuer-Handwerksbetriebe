import { test, expect, type Locator, type Page, type Route } from '@playwright/test';
import { designPruefung } from './hilfen/design';

/**
 * End-to-End-Test fuer LieferantDokumentModal auf dem neuen, verallgemeinerten
 * Sperr-Fundament (useDatensatzLock/BearbeitenLeiste/GesperrtHinweis statt
 * useDocumentLock/DocumentLockedModal). Deckt genau das ab, was ein Unit-Test
 * mit isoliert gerenderten Bausteinen nicht sehen kann: die echten Bausteine
 * zusammen im echten Browser, inklusive Layout und Netzwerk-Timing.
 *
 * /api wird vollstaendig gestubbt (kein Backend). DSGVO: nur Dummy-Namen.
 */

const LIEFERANT_ID = 7;
const DOKUMENT_ID = 42;

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const DUMMY_DOKUMENT = {
    id: DOKUMENT_ID,
    typ: 'RECHNUNG',
    originalDateiname: 'rechnung-dummy.pdf',
    uploadDatum: '2026-08-01T10:00:00',
    geschaeftsdaten: {
        dokumentNummer: 'RE-2026-100',
        dokumentDatum: '2026-08-01',
        betragNetto: 100,
        betragBrutto: 119,
        mwstSatz: 0.19,
    },
    projektAnteile: [],
    verknuepfteDokumente: [],
};

const DUMMY_LIEFERANT = {
    id: LIEFERANT_ID,
    lieferantenname: 'Musterbedarf Baustoffe GmbH',
    lieferantenTyp: 'Lieferant',
    rollen: [],
    strasse: 'Musterweg 1',
    plz: '12345',
    ort: 'Musterstadt',
    emails: [],
    kommunikation: [],
    notizen: [],
    kundenEmails: [],
    dokumente: [DUMMY_DOKUMENT],
};

// Minimaler gueltiger PDF-Inhalt -- der Inhalt selbst spielt keine Rolle,
// nur dass der eingebettete Betrachter kein Fehlerbild zeigt.
const MINI_PDF = Buffer.from(
    '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
    '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Size 4/Root 1 0 R>>\n%%EOF',
);

type AcquireVerhalten = 'frei' | 'fremd' | 'fehler';

function lockDto(overrides: Partial<{ status: 'ACQUIRED' | 'LOCKED_BY_OTHER'; holderDisplayName: string; acquiredAt: string }> = {}) {
    return {
        status: 'ACQUIRED' as const,
        holderUserId: 1,
        holderDisplayName: 'Anna Büro',
        acquiredAt: new Date().toISOString(),
        lastHeartbeatAt: new Date().toISOString(),
        ...overrides,
    };
}

/** Stubbt alle /api-Routen, die das Oeffnen von LieferantDokumentModal ueber /lieferanten anfasst. */
async function stubbeLieferantApi(page: Page, acquireVerhalten: AcquireVerhalten) {
    await page.route('**/api/auth/me', route => json(route, {
        id: 1, username: 'anna.buero', displayName: 'Anna Büro',
        active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
    }));

    // Ein Handler fuer alles unter /api/lieferanten -- Reihenfolge der
    // Unter-Pfade ist damit egal, es gibt keine Ueberschneidung mit anderen
    // page.route()-Registrierungen.
    await page.route('**/api/lieferanten**', route => {
        const pfad = new URL(route.request().url()).pathname;
        if (/\/dokumente\/\d+\/download$/.test(pfad)) {
            return route.fulfill({ status: 200, contentType: 'application/pdf', body: MINI_PDF });
        }
        if (/^\/api\/lieferanten\/\d+$/.test(pfad)) {
            return json(route, DUMMY_LIEFERANT);
        }
        if (pfad === '/api/lieferanten') {
            return json(route, { lieferanten: [], gesamt: 0 });
        }
        return route.fulfill({ status: 404, body: '' });
    });

    await page.route(`**/api/datensatz-locks/EINGANG/${DOKUMENT_ID}/acquire`, route => {
        if (acquireVerhalten === 'frei') return json(route, lockDto());
        if (acquireVerhalten === 'fremd') {
            return json(route, lockDto({
                status: 'LOCKED_BY_OTHER',
                holderDisplayName: 'Thomas Beispiel',
                acquiredAt: new Date(Date.now() - 5 * 60_000).toISOString(),
            }), 409);
        }
        return route.fulfill({ status: 500, body: '' });
    });
    await page.route(`**/api/datensatz-locks/EINGANG/${DOKUMENT_ID}/heartbeat`, route => json(route, lockDto()));
    await page.route(`**/api/datensatz-locks/EINGANG/${DOKUMENT_ID}`, route => {
        if (route.request().method() === 'DELETE') return route.fulfill({ status: 204, body: '' });
        return route.fulfill({ status: 404, body: '' });
    });
}

/** Oeffnet den Dokumente-Reiter des Dummy-Lieferanten und klickt das eine Dokument an. */
/**
 * Das Modal selbst (role="dialog") -- noetig, weil die Lieferanten-Detailseite
 * im Hintergrund einen EIGENEN "Bearbeiten"-Knopf hat (Firmenstammdaten
 * bearbeiten). Gleicher Text, andere Aktion -- ohne diese Eingrenzung waere
 * jede getByRole('button', { name: 'Bearbeiten' })-Abfrage mehrdeutig.
 */
function dialog(page: Page) {
    return page.getByRole('dialog', { name: 'Dokument bearbeiten' });
}

async function oeffneDokumentModal(page: Page) {
    await page.goto(`/lieferanten?lieferantId=${LIEFERANT_ID}&tab=dokumente`);
    await page.getByRole('button', { name: /RE-2026-100/ }).click();
    await expect(dialog(page)).toBeVisible();
}

/**
 * Design-Review-Regression: der Toast-Container lag fest unten rechts, genau
 * dort, wo die Modal-Fussleiste (Abbrechen/Speichern) sitzt. Ein Fehler-Toast
 * beim Oeffnen legte sich auf 1440x900 ueber beide Knoepfe -- ein Klick in
 * ihre Mitte traf den Toast, nicht den Knopf. Prueft per
 * document.elementFromPoint (wie vom Reviewer gemessen), dass die Mitte des
 * Knopfes wirklich noch zum Knopf gehoert.
 */
async function erwarteTreffer(page: Page, knopf: Locator, beschriftung: string) {
    const box = await knopf.boundingBox();
    if (!box) throw new Error(`Kein Bounding-Box fuer "${beschriftung}" gefunden`);
    const mitteX = box.x + box.width / 2;
    const mitteY = box.y + box.height / 2;

    const trifftKnopf = await page.evaluate(
        ({ x, y }) => document.elementFromPoint(x, y)?.closest('button') != null,
        { x: mitteX, y: mitteY },
    );
    expect(trifftKnopf, `elementFromPoint in der Mitte von "${beschriftung}" (${mitteX}, ${mitteY}) trifft keinen Knopf -- vermutlich verdeckt ein fest positioniertes Element (Toast?) die Fussleiste`).toBe(true);

    const istGesuchterKnopf = await page.evaluate(
        ({ x, y, text }) => document.elementFromPoint(x, y)?.closest('button')?.textContent?.includes(text) ?? false,
        { x: mitteX, y: mitteY, text: beschriftung },
    );
    expect(istGesuchterKnopf, `elementFromPoint in der Mitte von "${beschriftung}" trifft einen ANDEREN Knopf`).toBe(true);
}

test.describe('LieferantDokumentModal - Sperr-Fundament', () => {
    test('freies Lock: Formular ist sofort frei, Leiste zeigt "Fertig"; Fertig sperrt wieder und aktiviert "Bearbeiten"', async ({ page }, testInfo) => {
        await stubbeLieferantApi(page, 'frei');
        await oeffneDokumentModal(page);

        const fertig = dialog(page).getByRole('button', { name: 'Fertig' });
        await expect(fertig).toBeVisible();
        await expect(dialog(page).getByPlaceholder('RE-2024-001')).toBeEnabled();
        await expect(dialog(page).getByRole('button', { name: 'Speichern' })).toBeEnabled();

        await designPruefung(page, testInfo, 'lieferant-modal-bearbeiten', { primaerAktion: fertig });

        await fertig.click();

        const bearbeiten = dialog(page).getByRole('button', { name: 'Bearbeiten' });
        await expect(bearbeiten).toBeVisible();
        await expect(bearbeiten).toBeEnabled();
        await expect(dialog(page).getByPlaceholder('RE-2024-001')).toBeDisabled();
        await expect(dialog(page).getByRole('button', { name: 'Speichern' })).toBeDisabled();

        await designPruefung(page, testInfo, 'lieferant-modal-gesperrt', { primaerAktion: bearbeiten });
    });

    test('fremdes Lock (409): GesperrtHinweis mit Namen, Formular gesperrt, "Bearbeiten" versucht die Uebernahme', async ({ page }, testInfo) => {
        await stubbeLieferantApi(page, 'fremd');
        await oeffneDokumentModal(page);

        await expect(dialog(page).getByText('Thomas Beispiel')).toBeVisible();
        await expect(dialog(page).getByText(/bearbeitet das gerade/)).toBeVisible();
        await expect(dialog(page).getByPlaceholder('RE-2024-001')).toBeDisabled();
        await expect(dialog(page).getByRole('button', { name: 'Speichern' })).toBeDisabled();

        const bearbeiten = dialog(page).getByRole('button', { name: 'Bearbeiten' });
        await expect(bearbeiten).toBeEnabled();

        await designPruefung(page, testInfo, 'lieferant-modal-fremdes-lock', { primaerAktion: bearbeiten });

        // Ab jetzt gibt der Kollege frei -- der Uebernahmeversuch gelingt.
        await page.route(`**/api/datensatz-locks/EINGANG/${DOKUMENT_ID}/acquire`, route => json(route, lockDto()));
        await bearbeiten.click();

        await expect(dialog(page).getByRole('button', { name: 'Fertig' })).toBeVisible();
        await expect(dialog(page).getByPlaceholder('RE-2024-001')).toBeEnabled();
    });

    test('Fehlerfall (500) beim Oeffnen: Hinweis im Modal und Toast, Toast verdeckt die Fussleiste nicht, "Bearbeiten" bleibt deaktiviert', async ({ page }, testInfo) => {
        await stubbeLieferantApi(page, 'fehler');
        await oeffneDokumentModal(page);

        await expect(dialog(page).getByRole('alert')).toContainText('Sperre konnte nicht geholt werden');
        await expect(page.getByTestId('toast-container')).toContainText('Sperre konnte nicht geholt werden — bitte neu laden.');
        // Derselbe Wortlaut steht doppelt im Dokument -- einmal im Modal, einmal im Toast.
        await expect(page.getByText('Sperre konnte nicht geholt werden — bitte neu laden.')).toHaveCount(2);

        const bearbeiten = dialog(page).getByRole('button', { name: 'Bearbeiten' });
        await expect(bearbeiten).toBeDisabled();
        const abbrechen = dialog(page).getByRole('button', { name: 'Abbrechen' });
        const speichern = dialog(page).getByRole('button', { name: 'Speichern' });
        await expect(speichern).toBeDisabled();

        // Design-Review-Befund: bei stehendem Toast musste die Fussleiste trotzdem klickbar bleiben.
        await erwarteTreffer(page, abbrechen, 'Abbrechen');
        await erwarteTreffer(page, speichern, 'Speichern');

        await designPruefung(page, testInfo, 'lieferant-modal-fehler', { primaerAktion: bearbeiten });
    });

    test('Speicherfehler (500): Toast verdeckt die Fussleiste ebenfalls nicht', async ({ page }, testInfo) => {
        await stubbeLieferantApi(page, 'frei');
        await page.route(`**/api/lieferant-dokumente/${DOKUMENT_ID}`, route => {
            if (route.request().method() === 'PUT') return route.fulfill({ status: 500, body: '' });
            return route.fulfill({ status: 404, body: '' });
        });
        await oeffneDokumentModal(page);

        const speichern = dialog(page).getByRole('button', { name: 'Speichern' });
        await expect(speichern).toBeEnabled();
        await speichern.click();

        await expect(page.getByTestId('toast-container')).toContainText('Speichern fehlgeschlagen');

        const abbrechen = dialog(page).getByRole('button', { name: 'Abbrechen' });
        await erwarteTreffer(page, abbrechen, 'Abbrechen');
        await erwarteTreffer(page, speichern, 'Speichern');

        await designPruefung(page, testInfo, 'lieferant-modal-speicherfehler-toast', { primaerAktion: speichern });
    });
});
