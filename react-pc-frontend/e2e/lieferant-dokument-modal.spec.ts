import { test, expect, type Page, type Route } from '@playwright/test';
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

    test('Fehlerfall (500): Hinweis im Modal und Toast, "Bearbeiten" bleibt deaktiviert', async ({ page }, testInfo) => {
        await stubbeLieferantApi(page, 'fehler');
        await oeffneDokumentModal(page);

        await expect(dialog(page).getByRole('alert')).toContainText('Sperre konnte nicht geholt werden');
        await expect(page.getByText('Sperre konnte nicht geholt werden — bitte neu laden.')).toHaveCount(2);

        const bearbeiten = dialog(page).getByRole('button', { name: 'Bearbeiten' });
        await expect(bearbeiten).toBeDisabled();
        await expect(dialog(page).getByRole('button', { name: 'Speichern' })).toBeDisabled();

        await designPruefung(page, testInfo, 'lieferant-modal-fehler', { primaerAktion: bearbeiten });
    });
});
