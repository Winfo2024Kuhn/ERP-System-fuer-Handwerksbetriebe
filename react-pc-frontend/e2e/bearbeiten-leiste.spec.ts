import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung } from './hilfen/design';

/**
 * End-to-End-Test fuer BearbeitenLeiste (Design-Review-Nachschaerfung zu
 * Abschnitt 6, Task 7c): drei unterscheidbare Baender, ein Fertig-Knopf, der
 * beim Erscheinen eines Bandes nicht mehr springt, und ein deaktivierter
 * Bearbeiten-Knopf. Die Leiste hat noch keine eigene Seite -- sie wird
 * genau wie in e2e/lieferant-dokument-modal.spec.ts ueber das
 * Lieferant-Dokument-Modal gerendert (der einzige heutige Verwender),
 * /api vollstaendig gestubbt, kein Backend, nur Dummy-Namen (DSGVO).
 *
 * Countdown- und Verbindungs-Zustand haengen an echten Timern (useIdleTimer:
 * 60s Vorwarnung ab 240s Untaetigkeit; useDatensatzLock: 30s-Heartbeat, "weg"
 * ab 2 Fehlschlaegen = 60s) -- fuer einen Test in Sekunden statt Minuten
 * nutzt dieser Spec Playwrights virtuelle Uhr (page.clock), NICHT
 * page.waitForTimeout: runFor() laesst dieselben setTimeout/setInterval-
 * Aufrufe wie im echten Browser feuern, nur ohne echte Wartezeit.
 *
 * Bekannte Luecke (siehe Kontext-Log): der neue Prop bearbeitenGesperrtGrund
 * (Tooltip am deaktivierten Bearbeiten-Knopf) und zeigeNurLesenHinweis ("Sie
 * lesen nur mit.") sind in BearbeitenLeiste voll implementiert und
 * unit-getestet (BearbeitenLeiste.test.tsx), werden aber von
 * LieferantDokumentModal.tsx (nicht Teil dieses Tasks, siehe Task-Dateiliste)
 * noch nicht durchgereicht -- der "deaktiviert"-Zustand hier zeigt darum
 * einen deaktivierten Knopf OHNE sichtbaren Tooltip-Text. Das Nachreichen des
 * Tooltips im echten Browser braucht eine kleine Ergaenzung in
 * LieferantDokumentModal.tsx durch eine kuenftige Aufgabe.
 */

const LIEFERANT_ID = 21;
const DOKUMENT_ID = 210;

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const DUMMY_DOKUMENT = {
    id: DOKUMENT_ID,
    typ: 'RECHNUNG',
    originalDateiname: 'rechnung-dummy.pdf',
    uploadDatum: '2026-08-01T10:00:00',
    geschaeftsdaten: {
        dokumentNummer: 'RE-2026-210',
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
    lieferantenname: 'Beispielhandel Baustoffe GmbH',
    lieferantenTyp: 'Lieferant',
    rollen: [],
    strasse: 'Beispielweg 3',
    plz: '54321',
    ort: 'Musterhausen',
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

type AcquireVerhalten = 'frei' | 'fehler';
type HeartbeatVerhalten = 'ok' | 'fehler';

/** Stubbt alle /api-Routen, die das Oeffnen von LieferantDokumentModal anfasst -- siehe lieferant-dokument-modal.spec.ts. */
async function stubbeLieferantApi(
    page: Page,
    optionen: { acquire: AcquireVerhalten; heartbeat?: HeartbeatVerhalten },
) {
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
        if (optionen.acquire === 'frei') return json(route, lockDto());
        return route.fulfill({ status: 500, body: '' });
    });
    await page.route(`**/api/datensatz-locks/EINGANG/${DOKUMENT_ID}/heartbeat`, route => {
        if ((optionen.heartbeat ?? 'ok') === 'ok') return json(route, lockDto());
        return route.fulfill({ status: 500, body: '' });
    });
    await page.route(`**/api/datensatz-locks/EINGANG/${DOKUMENT_ID}`, route => {
        if (route.request().method() === 'DELETE') return route.fulfill({ status: 204, body: '' });
        return route.fulfill({ status: 404, body: '' });
    });
}

/** Das Modal selbst -- eingrenzen noetig, siehe lieferant-dokument-modal.spec.ts (eigener "Bearbeiten"-Knopf im Hintergrund). */
function dialog(page: Page) {
    return page.getByRole('dialog', { name: 'Dokument bearbeiten' });
}

async function oeffneDokumentModal(page: Page) {
    await page.goto(`/lieferanten?lieferantId=${LIEFERANT_ID}&tab=dokumente`);
    await page.getByRole('button', { name: /RE-2026-210/ }).click();
    await expect(dialog(page)).toBeVisible();
}

test.describe('BearbeitenLeiste - Design-Review-Nachschaerfung', () => {
    test('Bearbeiten-Modus: "Fertig" sichtbar, keine Baender', async ({ page }, testInfo) => {
        await stubbeLieferantApi(page, { acquire: 'frei' });
        await oeffneDokumentModal(page);

        const fertig = dialog(page).getByRole('button', { name: 'Fertig' });
        await expect(fertig).toBeVisible();
        await expect(dialog(page).getByRole('status')).toHaveCount(0);
        await expect(dialog(page).getByRole('alert')).toHaveCount(0);

        await designPruefung(page, testInfo, 'leiste-bearbeiten', { primaerAktion: fertig });
    });

    test('Lesen-Modus nach Fertig: "Bearbeiten" wieder aktiv', async ({ page }, testInfo) => {
        await stubbeLieferantApi(page, { acquire: 'frei' });
        await oeffneDokumentModal(page);

        await dialog(page).getByRole('button', { name: 'Fertig' }).click();

        const bearbeiten = dialog(page).getByRole('button', { name: 'Bearbeiten' });
        await expect(bearbeiten).toBeVisible();
        await expect(bearbeiten).toBeEnabled();

        await designPruefung(page, testInfo, 'leiste-lesen', { primaerAktion: bearbeiten });
    });

    test('Countdown sichtbar: amber-Band, Fertig-Knopf bleibt an derselben Stelle stehen', async ({ page }, testInfo) => {
        await page.clock.install();
        await stubbeLieferantApi(page, { acquire: 'frei' });
        await oeffneDokumentModal(page);

        const fertig = dialog(page).getByRole('button', { name: 'Fertig' });
        await expect(fertig).toBeVisible();
        const vorher = await fertig.boundingBox();
        expect(vorher).not.toBeNull();

        // useIdleTimer warnt 60s vor der 300s-Grenze -> Band ab 240s
        // Untaetigkeit. runFor() laesst die echten setTimeout/setInterval-
        // Aufrufe dazwischen feuern (anders als fastForward, das Intervalle
        // ueberspringen wuerde) -- 241s bringt den Countdown sicher zum
        // Anzeigen, ohne schon die 300s-Grenze (automatische Freigabe) zu
        // erreichen.
        await page.clock.runFor(241_000);

        const band = dialog(page).getByRole('status');
        await expect(band).toBeVisible();
        await expect(band).toHaveText(/Wird in \d+ Sekunden freigegeben/);

        const nachher = await fertig.boundingBox();
        expect(nachher).not.toBeNull();
        expect(Math.abs((nachher!.x) - (vorher!.x))).toBeLessThanOrEqual(5);

        await designPruefung(page, testInfo, 'leiste-countdown', { primaerAktion: fertig });
    });

    test('Verbindung weg: rotes Band nach zwei fehlgeschlagenen Heartbeats, Fertig-Knopf bleibt an derselben Stelle stehen', async ({ page }, testInfo) => {
        await page.clock.install();
        await stubbeLieferantApi(page, { acquire: 'frei', heartbeat: 'fehler' });
        await oeffneDokumentModal(page);

        const fertig = dialog(page).getByRole('button', { name: 'Fertig' });
        await expect(fertig).toBeVisible();
        const vorher = await fertig.boundingBox();
        expect(vorher).not.toBeNull();

        // Heartbeat alle 30s, "Verbindung weg" ab dem 2. Fehlschlag in Folge
        // -> 65s deckt beide Fehlschlaege sicher ab, weit unter der
        // 240s-Countdown-Schwelle (kein gleichzeitiges Countdown-Band).
        await page.clock.runFor(65_000);

        const band = dialog(page).getByRole('alert');
        await expect(band).toBeVisible();
        await expect(band).toHaveText('Verbindung weg — Ihre Änderungen sind noch nicht gespeichert.');
        await expect(dialog(page).getByRole('status')).toHaveCount(0);

        const nachher = await fertig.boundingBox();
        expect(nachher).not.toBeNull();
        expect(Math.abs((nachher!.x) - (vorher!.x))).toBeLessThanOrEqual(5);

        await designPruefung(page, testInfo, 'leiste-verbindung-weg', { primaerAktion: fertig });
    });

    test('Knopf deaktiviert: fehlgeschlagener Erwerb (500) laesst "Bearbeiten" deaktiviert stehen', async ({ page }, testInfo) => {
        await stubbeLieferantApi(page, { acquire: 'fehler' });
        await oeffneDokumentModal(page);

        const bearbeiten = dialog(page).getByRole('button', { name: 'Bearbeiten' });
        await expect(bearbeiten).toBeVisible();
        await expect(bearbeiten).toBeDisabled();

        await designPruefung(page, testInfo, 'leiste-deaktiviert', { primaerAktion: bearbeiten });
    });
});
