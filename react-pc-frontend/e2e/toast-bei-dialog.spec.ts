import type { Locator, Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Design-Review-Nachbesserung Abschnitt 6/7, Task 8a, Befund 1+3;
 * Design-Review-Nachbesserung 2, Task 8c:
 *
 * 1. Der Toast-Container wanderte bei offenem Dialog urspruenglich nach OBEN
 *    RECHTS (toast.tsx, seit Task 6b) -- genau dorthin, wo der Schliessen-X-
 *    Knopf und der "Vorschau aktiv"-Umschalter von LieferantDokumentModal
 *    sitzen. Task 8a verlegte ihn deshalb nach OBEN LINKS. Gemessen schnitt
 *    das aber seinerseits auf 14 Zoll die Modal-Ueberschrift an: ein
 *    zweizeiliger Toast [24,24,480,66] endet bei y=90, der Titel
 *    "Dokument bearbeiten" beginnt schon bei y=78 -- die Rechtecke von Toast
 *    und Titel ueberschneiden sich um 12px. Task 8c verlegt
 *    ihn deshalb ein zweites Mal, jetzt nach UNTEN LINKS -- die einzige Ecke,
 *    die weder im Lieferanten-Modal (Fussleiste rechts) noch im Confirm-
 *    Dialog (Knoepfe mittig/rechts) eine Aktion traegt, unabhaengig davon,
 *    wie lang der Toast-Text wird.
 * 2. confirm-dialog.tsx trug bisher KEIN role="dialog" -- der Toast-Umzug
 *    (der ausschliesslich per document.querySelector('[role="dialog"]')
 *    erkennt, ob "irgendein Dialog" offen ist) griff dort nicht.
 * 3. Abschnitt 4 beendet diese Ecken-Suche. Ausloeser war ein weiterer Befund
 *    aus dem 14-Zoll-Designreview: acht bis neun gestapelte Meldungen
 *    verdeckten Eingaben im Kassen-Einstellungs- und im Kostenpositions-
 *    dialog. Eine vierte Ecke haette das nicht geloest, weil ein schwebendes
 *    Overlay bei genug Meldungen jede Ecke fuellt. Meldungen belegen deshalb
 *    seither eine eigene, in der Hoehe begrenzte und intern scrollbare Flaeche
 *    oberhalb der Anwendung (toast.tsx); MainLayout und die Dialogcontainer
 *    rechnen deren gemessene Hoehe ueber --pc-toast-height ab (index.css).
 *    Die Tests unten pruefen darum nicht mehr eine bestimmte Ecke, sondern
 *    den Vertrag dieser Flaeche -- siehe erwarteReservierteMeldungsflaeche.
 *
 * Technischer Hinweis zum "zweizeiligen Toast" in Test 1: alle Toast-Texte,
 * die LieferantDokumentModal ueber echte Nutzerabläufe tatsaechlich ausloest
 * (LOCK_FEHLER_TEXT, "Speichern fehlgeschlagen"), sind feste, kurze Strings
 * und passen auf eine Zeile -- die Produktionslogik liest
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

/**
 * Ueberlappungspruefung per Bounding-Box-Vergleich fuer nicht-interaktive
 * Elemente (Modal-Titel, Eyebrow) -- ein elementFromPoint-Test am
 * Box-MITTELPUNKT (wie bei den Knoepfen unten) haette den urspruenglichen
 * Befund NICHT zuverlaessig erkannt: die vom Design-Reviewer gemessene 12px-
 * Ueberlappung [Toast endet bei y=90, Titel beginnt bei y=78] traf nur den
 * OBEREN Rand des 28px hohen Titel-Elements -- dessen geometrische Mitte
 * (y=92) liegt bereits UNTERHALB des Toast-Endes und wuerde von
 * elementFromPoint gar nicht mehr getroffen, obwohl sich die beiden
 * Rechtecke nachweislich ueberschneiden. Diese Pruefung vergleicht darum
 * direkt die beiden Rechtecke -- dieselbe Methode, mit der der Befund
 * gemessen wurde.
 */
async function erwarteKeineUeberlappungMitToast(toast: Locator, ziel: Locator, beschreibung: string) {
    const toastBox = await toast.boundingBox();
    const zielBox = await ziel.boundingBox();
    if (!toastBox) throw new Error('Kein Bounding-Box fuer den Toast-Container gefunden');
    if (!zielBox) throw new Error(`Kein Bounding-Box fuer "${beschreibung}" gefunden`);

    const ueberlappt =
        toastBox.x < zielBox.x + zielBox.width && toastBox.x + toastBox.width > zielBox.x &&
        toastBox.y < zielBox.y + zielBox.height && toastBox.y + toastBox.height > zielBox.y;

    expect(
        ueberlappt,
        `Toast [${toastBox.x},${toastBox.y},${toastBox.width},${toastBox.height}] ueberlappt "${beschreibung}" ` +
        `[${zielBox.x},${zielBox.y},${zielBox.width},${zielBox.height}]`,
    ).toBe(false);
}

/**
 * Prueft den Vertrag der reservierten Meldungsflaeche (Abschnitt 4): Meldungen
 * liegen nicht mehr als schwebendes Overlay in irgendeiner Bildschirmecke,
 * sondern belegen eine eigene Flaeche oberhalb der Anwendung. Ihre gemessene
 * Hoehe steht in --pc-toast-height; MainLayout und die Dialogcontainer rechnen
 * sie ab (siehe toast.tsx und index.css).
 *
 * Damit ist die Ecken-Suche der Tasks 8a/8c erledigt: die Flaeche KANN
 * Dialoginhalte gar nicht mehr ueberdecken, statt es je nach Textlaenge und
 * Bildschirmgroesse mal zu tun und mal nicht. Diese Pruefung sichert genau
 * das ab -- Hoehe veroeffentlicht und keine Ueberschneidung mit dem offenen
 * Dialog -- statt wie frueher eine bestimmte Ecke festzuschreiben.
 */
async function erwarteReservierteMeldungsflaeche(page: Page, toast: Locator) {
    await expect(toast).toBeVisible();
    const hoehe = await page.evaluate(() =>
        parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--pc-toast-height')) || 0);
    const toastBox = await toast.boundingBox();
    if (!toastBox) throw new Error('Kein Bounding-Box fuer die Meldungsflaeche gefunden');
    expect(hoehe, 'Die Meldungsflaeche muss ihre Hoehe in --pc-toast-height veroeffentlichen').toBeGreaterThan(0);
    expect(Math.abs(hoehe - toastBox.height), '--pc-toast-height muss der tatsaechlichen Hoehe entsprechen').toBeLessThan(2);

    const dialogBox = await dialog(page).boundingBox();
    if (dialogBox) {
        const ueberlappt =
            toastBox.x < dialogBox.x + dialogBox.width && toastBox.x + toastBox.width > dialogBox.x &&
            toastBox.y < dialogBox.y + dialogBox.height && toastBox.y + toastBox.height > dialogBox.y;
        expect(
            ueberlappt,
            `Meldungsflaeche [${toastBox.x},${toastBox.y},${toastBox.width},${toastBox.height}] ueberlappt den offenen Dialog ` +
            `[${dialogBox.x},${dialogBox.y},${dialogBox.width},${dialogBox.height}]`,
        ).toBe(false);
    }
}

/**
 * Prueft, dass der Toast ueber ALLEM anderen liegt -- insbesondere ueber
 * einem gleichzeitig offenen Confirm-Backdrop (Task 8c Nachtrag,
 * Code-Review-Befund 4). `elementFromPoint` in der Toast-Mitte muss ein
 * Element INNERHALB des Toast-Containers treffen; trifft es stattdessen den
 * Confirm-Backdrop, wuerde ein Klick auf den (optisch sichtbaren) Toast in
 * Wirklichkeit `handleCancel` des Confirm-Dialogs ausloesen.
 */
async function erwarteToastLiegtUeberAllem(page: Page, toast: Locator) {
    const box = await toast.boundingBox();
    if (!box) throw new Error('Kein Bounding-Box fuer den Toast-Container gefunden');
    const mitteX = box.x + box.width / 2;
    const mitteY = box.y + box.height / 2;
    const toastHandle = await toast.elementHandle();
    if (!toastHandle) throw new Error('Kein Element-Handle fuer den Toast-Container gefunden');

    const ergebnis = await page.evaluate(
        ([toastEl, x, y]) => {
            const treffer = document.elementFromPoint(x, y);
            return {
                liegtImToast: !!treffer && (treffer === toastEl || (toastEl as Element).contains(treffer)),
                trefferTag: treffer?.tagName ?? null,
                trefferKlasse: (treffer as HTMLElement | null)?.className ?? null,
            };
        },
        [toastHandle, mitteX, mitteY] as const,
    );
    expect(
        ergebnis.liegtImToast,
        `elementFromPoint in der Mitte des Toasts (${mitteX}, ${mitteY}) trifft nicht den Toast, sondern ` +
        `<${ergebnis.trefferTag} class="${ergebnis.trefferKlasse}"> -- vermutlich liegt der Confirm-Backdrop darueber`,
    ).toBe(true);
}

test.describe('Meldungen bei offenem Dialog (Tasks 8a/8c, ab Abschnitt 4 reservierte Meldungsflaeche)', () => {
    test('zweizeiliger Fehler-Toast bei offenem Modal verdeckt weder Modal-Titel, Eyebrow, Schließen-X, "Abbrechen" noch "Speichern"', async ({ page }, testInfo) => {
        await stubbeLieferantApi(page, 'fehler');
        await oeffneDokumentModal(page);

        // Der Sperr-Abruf schlaegt fehl (500) -- LieferantDokumentModal loest
        // automatisch toast.error(LOCK_FEHLER_TEXT) aus (echter Ablauf, siehe
        // LieferantDokumentModal.tsx).
        const toastContainer = page.getByTestId('toast-container');
        await expect(toastContainer).toContainText('Sperre konnte nicht geholt werden');

        // Container muss jetzt unten links stehen (offener Dialog -- das
        // Modal selbst traegt role="dialog"). Task 8c: nicht mehr oben links,
        // das schnitt die Modal-Ueberschrift an (siehe Datei-Kommentar oben).
        await erwarteReservierteMeldungsflaeche(page, toastContainer);

        // Text im ECHT ausgeloesten, ECHT positionierten Toast direkt im DOM
        // verlaengern, bis er zweizeilig umbricht -- siehe Erklaerung im
        // Datei-Kommentar oben. Alles ausser der Textherkunft bleibt der
        // reale Ablauf (reale CSS-Klassen, reales Layout).
        await page.evaluate(() => {
            const absatz = document.querySelector('[data-testid="toast-container"] p');
            if (!absatz) throw new Error('Kein Toast-Text im DOM gefunden');
            // Die Meldungsflaeche ist seit Abschnitt 4 ueber die volle Breite
            // angelegt (max-w-[1600px]) statt max-w-[480px] wie der frueher
            // schwebende Toast. Der Text muss darum deutlich laenger sein, um
            // ueberhaupt noch auf zwei Zeilen umzubrechen -- genau das ist hier
            // der Pruefzweck: eine hohe Meldungsflaeche darf den Dialog nicht
            // anschneiden.
            absatz.textContent =
                'Sperre konnte nicht geholt werden — der Server antwortet gerade nicht zuverlässig. '
                + 'Bitte laden Sie das Dokument in Kürze erneut und prüfen Sie vorher, ob eine Kollegin oder '
                + 'ein Kollege denselben Beleg gerade geöffnet hat. Ungespeicherte Änderungen bleiben so lange '
                + 'in diesem Fenster erhalten und gehen durch das erneute Laden nicht verloren.';
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
        const titel = dialog(page).getByRole('heading', { name: 'Dokument bearbeiten', level: 2 });
        const eyebrow = dialog(page).getByText('PDF-Vorschau', { exact: true });
        const abbrechenKnopf = dialog(page).getByRole('button', { name: 'Abbrechen' });
        const speichernKnopf = dialog(page).getByRole('button', { name: 'Speichern' });

        await designPruefung(page, testInfo, 'toast-bei-dialog-zweizeilig', { primaerAktion: schliessenKnopf });

        // Kernbefund aus dem Review (Task 8a) und aus der Nachbesserung
        // (Task 8c): bei offenem Dialog ueberschneidet sich der (jetzt
        // zweizeilige, unten links stehende) Toast weder mit dem Modal-Titel
        // noch mit der Eyebrow (Bounding-Box-Vergleich, siehe Kommentar an
        // erwarteKeineUeberlappungMitToast), und ein Klick auf Schliessen-X,
        // "Abbrechen" und "Speichern" trifft jeweils den Knopf selbst -- auf
        // beiden Bildschirmgroessen (die Playwright-Konfiguration faehrt
        // pc-14zoll UND pc-monitor automatisch fuer diese Spec).
        await erwarteKeineUeberlappungMitToast(toastContainer, titel, 'Dokument bearbeiten (Modal-Titel)');
        await erwarteKeineUeberlappungMitToast(toastContainer, eyebrow, 'PDF-Vorschau (Eyebrow)');
        await erwarteTrefferPerAriaLabel(page, schliessenKnopf, 'Schließen');
        await erwarteTrefferPerText(page, vorschauKnopf, 'Vorschau');
        await erwarteTrefferPerText(page, abbrechenKnopf, 'Abbrechen');
        await erwarteTrefferPerText(page, speichernKnopf, 'Speichern');
    });

    test('Versionskonflikt: der Confirm-Dialog traegt selbst role="dialog", und die Meldungsflaeche bleibt ausserhalb des Dialogs', async ({ page }, testInfo) => {
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

        // Der 409 oeffnet nur den Confirm-Dialog, er loest keine Meldung aus.
        // Dann darf die Meldungsflaeche auch keinen Platz kosten: leer heisst
        // --pc-toast-height = 0, sonst schoebe eine unsichtbare Flaeche die
        // ganze Anwendung nach unten.
        const toastContainer = page.getByTestId('toast-container');
        await expect(toastContainer).toBeHidden();
        const reservierteHoehe = await page.evaluate(() =>
            parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--pc-toast-height')) || 0);
        expect(reservierteHoehe, 'Ohne Meldung darf die Meldungsflaeche keinen Platz belegen').toBe(0);

        await designPruefung(page, testInfo, 'toast-bei-dialog-versionskonflikt', {
            primaerAktion: konfliktDialog.getByRole('button', { name: 'Neu laden' }),
        });

        await konfliktDialog.getByRole('button', { name: 'Abbrechen' }).click();
        await expect(konfliktDialog).not.toBeVisible();
    });

    test('Meldung bleibt neben offenem Confirm-Dialog lesbar und anklickbar, statt hinter dessen Backdrop zu verschwinden', async ({ page }, testInfo) => {
        // toast.tsx (z-[9999]) und confirm-dialog.tsx (Backdrop z-[10000],
        // Dialog z-[10001]) sind beide `fixed` im selben Stacking-Kontext --
        // der Confirm-Backdrop lag bisher UEBER dem Toast. Reproduziert mit
        // zwei echten, nacheinander ausgeloesten Speicherversuchen: der erste
        // schlaegt mit 500 fehl (echter Fehler-Toast, 5s Anzeigedauer), der
        // zweite (noch waehrend der Toast sichtbar ist) liefert 409 und
        // oeffnet den Confirm-Dialog "Nicht gespeichert" darueber.
        await stubbeLieferantApi(page, 'frei');
        let speicherVersuche = 0;
        await page.route(`**/api/lieferant-dokumente/${DOKUMENT_ID}`, route => {
            if (route.request().method() !== 'PUT') return route.fulfill({ status: 404, body: '' });
            speicherVersuche += 1;
            if (speicherVersuche === 1) {
                return route.fulfill({ status: 500, body: '' });
            }
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

        const speichernKnopf = dialog(page).getByRole('button', { name: 'Speichern' });
        await speichernKnopf.click();

        const toastContainer = page.getByTestId('toast-container');
        await expect(toastContainer).toContainText('Speichern fehlgeschlagen');
        await erwarteReservierteMeldungsflaeche(page, toastContainer);

        // Zweiter Versuch, noch waehrend der erste Toast sichtbar ist (5s-Timer
        // laeuft noch) -- der Confirm-Dialog oeffnet sich jetzt zusaetzlich.
        await speichernKnopf.click();
        const konfliktDialog = page.getByRole('dialog', { name: 'Nicht gespeichert' });
        await expect(konfliktDialog).toBeVisible();
        await expect(toastContainer).toContainText('Speichern fehlgeschlagen');

        await designPruefung(page, testInfo, 'toast-ueber-confirm-backdrop', {
            primaerAktion: konfliktDialog.getByRole('button', { name: 'Neu laden' }),
        });

        await erwarteToastLiegtUeberAllem(page, toastContainer);
    });
});
