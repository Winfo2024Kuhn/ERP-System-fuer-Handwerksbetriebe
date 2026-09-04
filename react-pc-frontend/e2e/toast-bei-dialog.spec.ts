import { test, expect, type Locator, type Page, type Route } from '@playwright/test';
import { designPruefung } from './hilfen/design';

/**
 * Design-Review-Nachbesserung Abschnitt 6/7, Task 8a, Befund 1+3:
 *
 * 1. Der Toast-Container wanderte bei offenem Dialog bisher nach OBEN RECHTS
 *    (toast.tsx, seit Task 6b) -- genau dorthin, wo der Schliessen-X-Knopf und
 *    der "Vorschau aktiv"-Umschalter von LieferantDokumentModal sitzen.
 *    Gemessen blieben zwischen einem einzeiligen Toast und dem X nur 4px Luft
 *    (Toast endet y=70, X beginnt y=74); ein zweizeiliger Toast ueberdeckt
 *    beide Knoepfe komplett. Der Fix (siehe toast.tsx) verlegt den Container
 *    bei offenem Dialog nach OBEN LINKS, wo kein Modal im Projekt Aktionen
 *    platziert -- unabhaengig davon, wie lang der Toast-Text wird.
 * 2. confirm-dialog.tsx trug bisher KEIN role="dialog" -- der Toast-Umzug
 *    (der ausschliesslich per document.querySelector('[role="dialog"]')
 *    erkennt, ob "irgendein Dialog" offen ist) griff dort nicht.
 *
 * Technischer Hinweis zum "zweizeiligen Toast" in Test 1: alle Toast-Texte,
 * die LieferantDokumentModal ueber echte Nutzerabläufe tatsaechlich ausloest
 * (LOCK_FEHLER_TEXT, "Speichern fehlgeschlagen"), sind feste, kurze Strings
 * und passen bei max-w-[480px] auf eine Zeile -- die Produktionslogik liest
 * an keiner Stelle einen laengeren, vom Server/Stub gesteuerten Text in den
 * Toast ein (siehe Kontext-Log, Abschnitt "Bedenken"). Um den vom
 * Design-Reviewer beschriebenen zweizeiligen Fall trotzdem GENAU nachzustellen
 * -- reales Layout, reale CSS-Klassen, reale Positionierung, nur der
 * Textinhalt kommt aus dem Test statt aus der Produktionslogik -- verlaengert
 * der Test den Text des bereits ECHT ausgeloesten Toasts direkt im
 * gerenderten DOM (kein Eingriff in React-State/Produktionscode). Das haelt
 * die Pruefung ehrlich: alles ausser der Textherkunft ist der echte Ablauf.
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

const MINI_PDF = Buffer.from(
    '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
    '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Size 4/Root 1 0 R>>\n%%EOF',
);

type AcquireVerhalten = 'frei' | 'fehler';

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

/** Stubbt alle /api-Routen, die das Oeffnen von LieferantDokumentModal ueber /lieferanten anfasst -- Stil aus e2e/lieferant-dokument-modal.spec.ts uebernommen. */
async function stubbeLieferantApi(page: Page, acquireVerhalten: AcquireVerhalten) {
    await page.route('**/api/auth/me', route => json(route, {
        id: 1, username: 'anna.buero', displayName: 'Anna Büro',
        active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
    }));

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
        return route.fulfill({ status: 500, body: '' });
    });
    await page.route(`**/api/datensatz-locks/EINGANG/${DOKUMENT_ID}/heartbeat`, route => json(route, lockDto()));
    await page.route(`**/api/datensatz-locks/EINGANG/${DOKUMENT_ID}`, route => {
        if (route.request().method() === 'DELETE') return route.fulfill({ status: 204, body: '' });
        return route.fulfill({ status: 404, body: '' });
    });
}

function dialog(page: Page) {
    return page.getByRole('dialog', { name: 'Dokument bearbeiten' });
}

async function oeffneDokumentModal(page: Page) {
    await page.goto(`/lieferanten?lieferantId=${LIEFERANT_ID}&tab=dokumente`);
    await page.getByRole('button', { name: /RE-2026-100/ }).click();
    await expect(dialog(page)).toBeVisible();
}

/** Klickbarkeitspruefung fuer einen sichtbar beschrifteten Knopf (z.B. "Vorschau aktiv") -- per Textinhalt. */
async function erwarteTrefferPerText(page: Page, knopf: Locator, beschriftung: string) {
    const box = await knopf.boundingBox();
    if (!box) throw new Error(`Kein Bounding-Box fuer "${beschriftung}" gefunden`);
    const mitteX = box.x + box.width / 2;
    const mitteY = box.y + box.height / 2;

    const text = await page.evaluate(
        ({ x, y }) => document.elementFromPoint(x, y)?.closest('button')?.textContent ?? null,
        { x: mitteX, y: mitteY },
    );
    expect(
        text?.includes(beschriftung) ?? false,
        `elementFromPoint in der Mitte von "${beschriftung}" (${mitteX}, ${mitteY}) trifft einen anderen/keinen Knopf (Inhalt: "${text}") -- vermutlich verdeckt der Toast die Fussleiste/den Kopf`,
    ).toBe(true);
}

/** Klickbarkeitspruefung fuer einen Icon-only-Knopf (z.B. das Schliessen-X) -- per aria-label statt Textinhalt. */
async function erwarteTrefferPerAriaLabel(page: Page, knopf: Locator, ariaLabel: string) {
    const box = await knopf.boundingBox();
    if (!box) throw new Error(`Kein Bounding-Box fuer aria-label="${ariaLabel}" gefunden`);
    const mitteX = box.x + box.width / 2;
    const mitteY = box.y + box.height / 2;

    const treffer = await page.evaluate(
        ({ x, y }) => document.elementFromPoint(x, y)?.closest('button')?.getAttribute('aria-label') ?? null,
        { x: mitteX, y: mitteY },
    );
    expect(
        treffer,
        `elementFromPoint in der Mitte von aria-label="${ariaLabel}" (${mitteX}, ${mitteY}) trifft einen anderen Knopf (aria-label="${treffer}") -- vermutlich verdeckt der Toast das Element`,
    ).toBe(ariaLabel);
}

test.describe('Toast-Positionierung bei offenem Dialog (Task 8a)', () => {
    test('zweizeiliger Fehler-Toast bei offenem Modal verdeckt weder das Schliessen-X noch "Vorschau aktiv"', async ({ page }, testInfo) => {
        await stubbeLieferantApi(page, 'fehler');
        await oeffneDokumentModal(page);

        // Der Sperr-Abruf schlaegt fehl (500) -- LieferantDokumentModal loest
        // automatisch toast.error(LOCK_FEHLER_TEXT) aus (echter Ablauf, siehe
        // LieferantDokumentModal.tsx).
        const toastContainer = page.getByTestId('toast-container');
        await expect(toastContainer).toContainText('Sperre konnte nicht geholt werden');

        // Container muss jetzt oben links stehen (offener Dialog -- das Modal
        // selbst traegt role="dialog").
        await expect(toastContainer).toHaveClass(/top-6/);
        await expect(toastContainer).toHaveClass(/left-6/);
        await expect(toastContainer).not.toHaveClass(/bottom-6/);
        await expect(toastContainer).not.toHaveClass(/right-6/);

        // Text im ECHT ausgeloesten, ECHT positionierten Toast direkt im DOM
        // verlaengern, bis er zweizeilig umbricht -- siehe Erklaerung im
        // Datei-Kommentar oben. Alles ausser der Textherkunft bleibt der
        // reale Ablauf (reale CSS-Klassen, reales Layout).
        await page.evaluate(() => {
            const absatz = document.querySelector('[data-testid="toast-container"] p');
            if (!absatz) throw new Error('Kein Toast-Text im DOM gefunden');
            absatz.textContent =
                'Sperre konnte nicht geholt werden — der Server antwortet gerade nicht zuverlässig, bitte laden Sie in Kürze erneut.';
        });

        const toastAbsatz = toastContainer.locator('p');
        const zeilenhoehe = await toastAbsatz.evaluate(el => el.getBoundingClientRect().height);
        // Eine Zeile bei text-sm/leading-5 ist 20px hoch -- ab 2 Zeilen sind
        // es mindestens 40px. Schlaegt diese Zusicherung fehl, ist der Text
        // oben zu kurz/lang fuer eine zuverlaessig zweizeilige Probe.
        expect(zeilenhoehe, 'Der injizierte Text sollte auf genau zwei Zeilen umbrechen').toBeGreaterThanOrEqual(36);
        expect(zeilenhoehe).toBeLessThan(60);

        const schliessenKnopf = dialog(page).getByRole('button', { name: 'Schließen' });
        const vorschauKnopf = dialog(page).getByRole('button', { name: /Vorschau/ });

        await designPruefung(page, testInfo, 'toast-bei-dialog-zweizeilig', { primaerAktion: schliessenKnopf });

        // Kernbefund aus dem Review: bei offenem Dialog trifft elementFromPoint
        // in der Mitte des Schliessen-X das X, nicht den (jetzt zweizeiligen)
        // Toast -- auf beiden Bildschirmgroessen (die Playwright-Konfiguration
        // faehrt pc-14zoll UND pc-monitor automatisch fuer diese Spec).
        await erwarteTrefferPerAriaLabel(page, schliessenKnopf, 'Schließen');
        await erwarteTrefferPerText(page, vorschauKnopf, 'Vorschau');
    });

    test('Versionskonflikt: der Confirm-Dialog traegt jetzt selbst role="dialog", und der Toast-Container steht entsprechend oben links', async ({ page }, testInfo) => {
        await stubbeLieferantApi(page, 'frei');
        await page.route(`**/api/lieferant-dokumente/${DOKUMENT_ID}`, route => {
            if (route.request().method() !== 'PUT') return route.fulfill({ status: 404, body: '' });
            return json(
                route,
                {
                    message:
                        'Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.',
                },
                409,
            );
        });
        await oeffneDokumentModal(page);

        await dialog(page).getByRole('button', { name: 'Speichern' }).click();

        // Task 8a, Punkt 3: role="dialog"/aria-modal auf dem Confirm selbst --
        // vorher unauffindbar per getByRole('dialog', ...), und der globale
        // Toast-Umzug erkannte diesen Dialog gar nicht als "offen".
        const konfliktDialog = page.getByRole('dialog', { name: 'Nicht gespeichert' });
        await expect(konfliktDialog).toBeVisible();
        await expect(konfliktDialog).toHaveAttribute('aria-modal', 'true');

        const toastContainer = page.getByTestId('toast-container');
        await expect(toastContainer).toHaveClass(/top-6/);
        await expect(toastContainer).toHaveClass(/left-6/);
        await expect(toastContainer).not.toHaveClass(/bottom-6/);

        await designPruefung(page, testInfo, 'toast-bei-dialog-versionskonflikt', {
            primaerAktion: konfliktDialog.getByRole('button', { name: 'Neu laden' }),
        });

        await konfliktDialog.getByRole('button', { name: 'Abbrechen' }).click();
        await expect(konfliktDialog).not.toBeVisible();
    });
});
