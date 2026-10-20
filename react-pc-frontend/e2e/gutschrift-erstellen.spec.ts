import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Gutschrift zu einer gebuchten Rechnung (#223): Projekt → Rechnung → Aktionsmenü
 * „Gutschrift erstellen“ → Teilbetrag mit Grund → POST mit genau einer
 * Leistungsposition, danach öffnet sich der Editor im neuen Tab.
 * /api ist komplett gestubbt, nur Dummy-Daten (DSGVO).
 */

const PROJEKT_ID = 7;

const RECHNUNG = {
    id: 801,
    dokumentNummer: 'RE-2026/10/00042',
    typ: 'RECHNUNG' as const,
    datum: '2026-10-01',
    betreff: 'Geländer Musterstraße',
    betragNetto: 1000,
    betragBrutto: 1190,
    gebucht: true,
    storniert: false,
    bearbeitbar: false,
    projektId: PROJEKT_ID,
};

const RAHMEN = {
    rechnungId: RECHNUNG.id,
    rechnungNummer: RECHNUNG.dokumentNummer,
    rechnungBrutto: 1190,
    rechnungNetto: 1000,
    bereitsGutgeschriebenBrutto: 0,
    bereitsGutgeschriebenNetto: 0,
    hoechstensNetto: 1000,
    hoechstensBrutto: 1190,
    mwstSatz: 0.19,
};

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const GUTSCHRIFT = {
    id: 802,
    dokumentNummer: 'GU-2026/10/00043',
    typ: 'GUTSCHRIFT' as const,
    datum: '2026-10-05',
    betreff: `Gutschrift zu Rechnung ${RECHNUNG.dokumentNummer}`,
    betragNetto: -100,
    betragBrutto: -119,
    gebucht: true,
    storniert: false,
    bearbeitbar: false,
    projektId: PROJEKT_ID,
    vorgaengerId: RECHNUNG.id,
    vorgaengerNummer: RECHNUNG.dokumentNummer,
};

const STORNO_SPERRE = `Zu dieser Rechnung gibt es die Gutschrift ${GUTSCHRIFT.dokumentNummer}. Bitte zuerst die Gutschrift stornieren.`;

async function stubProjektApi(page: Page, dokumente: unknown[] = [RECHNUNG]) {
    const posts: unknown[] = [];
    await page.context().route('**/api/**', (route) => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        const methode = request.method();

        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'max', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (/^\/api\/last-accessed\/PROJEKT(\/\d+)?$/.test(pfad)) {
            if (methode === 'POST') return route.fulfill({ status: 204, body: '' });
            return json(route, {});
        }
        if (pfad === '/api/projekte' && methode === 'GET') return json(route, { projekte: [], gesamt: 0 });
        if (pfad === `/api/projekte/${PROJEKT_ID}`) {
            return json(route, {
                id: PROJEKT_ID, bauvorhaben: 'Geländer Musterstraße', kunde: 'Max Mustermann', kundenId: 3,
                kundennummer: 'K-1003', auftragsnummer: 'A-2026-0007', anlegedatum: '2026-08-15',
                bruttoPreis: 1190, bezahlt: false, abgeschlossen: false, kundenEmails: [],
                materialkosten: [], artikel: [], produktkategorien: [], zeiten: [], emails: [],
            });
        }
        if (pfad === `/api/ausgangs-dokumente/projekt/${PROJEKT_ID}`) return json(route, dokumente);
        if (pfad === `/api/ausgangs-dokumente/${RECHNUNG.id}/storno` && methode === 'POST') {
            return json(route, { status: 400, message: STORNO_SPERRE }, 400);
        }
        if (pfad === `/api/ausgangs-dokumente/${RECHNUNG.id}/gutschrift-rahmen`) return json(route, RAHMEN);
        if (pfad === '/api/ausgangs-dokumente' && methode === 'POST') {
            posts.push(request.postDataJSON());
            return json(route, { id: 990, dokumentNummer: 'GU-2026/10/00043', typ: 'GUTSCHRIFT', gebucht: false, storniert: false, bearbeitbar: true, vorgaengerId: RECHNUNG.id, projektId: PROJEKT_ID });
        }
        if (pfad === '/api/ausgangs-dokumente/freigabe-status' || pfad === '/api/projekte/freigabe-status') return json(route, {});
        return json(route, []);
    });
    return posts;
}

test('Teil-Gutschrift zu einer gebuchten Rechnung anlegen und im Editor öffnen', async ({ page }, testInfo) => {
    const posts = await stubProjektApi(page);

    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);
    await page.getByTitle('Klick für Aktionen, Doppelklick zum Öffnen').filter({ hasText: RECHNUNG.dokumentNummer }).click();
    // Gebuchte Rechnung: Gutschrift und Storno stehen beide zur Wahl.
    await expect(page.getByRole('button', { name: 'Stornieren' })).toBeVisible();
    await page.getByRole('button', { name: 'Gutschrift erstellen' }).click();

    const dialog = page.getByRole('dialog', { name: 'Gutschrift erstellen' });
    await expect(dialog.getByTestId('gutschrift-hoechstens')).toContainText('1.000,00 € netto');
    await dialog.getByRole('radio', { name: /Teilbetrag gutschreiben/ }).check();
    await dialog.getByLabel('Betrag netto in Euro *').fill('900,00');
    await dialog.getByLabel('Grund *').fill('Preisnachlass wegen Kratzer am Geländer');
    await expect(dialog.getByText(/1\.071,00\s€ brutto/)).toBeVisible();

    // Info-Button erklärt Hintergrund und Wirkung, nichts abgeschnitten.
    await dialog.getByRole('button', { name: 'Mehr zu: Gutschrift' }).click();
    await expect(page.getByText(/mit Nummer GU-… angelegt/)).toBeInViewport({ ratio: 1 });
    await designPruefung(page, testInfo, 'gutschrift-dialog-info', { primaerAktion: dialog.getByRole('button', { name: 'Gutschrift anlegen' }) });
    await page.keyboard.press('Escape');
    await expect(page.getByText(/mit Nummer GU-… angelegt/)).toBeHidden();

    const popupPromise = page.waitForEvent('popup');
    await dialog.getByRole('button', { name: 'Gutschrift anlegen' }).click();
    const editor = await popupPromise;
    await expect.poll(() => editor.url()).toContain('/dokument-editor?projektId=7&dokumentId=990');
    await editor.close();

    expect(posts).toHaveLength(1);
    const body = posts[0] as Record<string, unknown>;
    expect(body).toMatchObject({ typ: 'GUTSCHRIFT', vorgaengerId: RECHNUNG.id, projektId: PROJEKT_ID });
    expect(body.datum).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    // Den Betreff setzt der Server passend zur Rechnungsart.
    expect(body).not.toHaveProperty('betreff');
    const positionen = JSON.parse(String(body.positionenJson));
    expect(positionen.blocks).toHaveLength(1);
    expect(positionen.blocks[0]).toMatchObject({
        type: 'SERVICE',
        title: `Gutschrift zu Rechnung ${RECHNUNG.dokumentNummer}: Preisnachlass wegen Kratzer am Geländer`,
        quantity: 1,
        price: 900,
    });
    await expect(page.getByRole('dialog', { name: 'Gutschrift erstellen' })).toBeHidden();
});

test('Gebuchte Gutschrift lässt sich stornieren; die Rechnung erst danach', async ({ page }) => {
    await stubProjektApi(page, [RECHNUNG, GUTSCHRIFT]);

    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);
    const karten = page.getByTitle('Klick für Aktionen, Doppelklick zum Öffnen');

    // Gutschrift: Minus wie geliefert, „Stornieren“ ja, aber keine Gutschrift zur Gutschrift.
    const gutschriftKarte = karten.filter({ hasText: GUTSCHRIFT.dokumentNummer });
    await expect(gutschriftKarte).toContainText('-100,00');
    await gutschriftKarte.click();
    await expect(page.getByRole('button', { name: 'Stornieren' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Gutschrift erstellen' })).toHaveCount(0);
    await page.keyboard.press('Escape');

    // Rechnung mit offener Gutschrift: Der Server lehnt das Storno ab, die Meldung steht im Toast.
    await karten.filter({ hasText: RECHNUNG.dokumentNummer, hasNotText: GUTSCHRIFT.dokumentNummer }).click();
    await page.getByRole('button', { name: 'Stornieren' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Stornieren' }).click();
    await expect(page.getByText(`Stornierung fehlgeschlagen: ${STORNO_SPERRE}`)).toBeVisible();
});
