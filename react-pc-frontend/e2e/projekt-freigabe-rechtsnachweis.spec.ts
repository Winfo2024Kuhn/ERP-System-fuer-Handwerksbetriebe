import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';

/**
 * Annahme-Nachweis einer digitalen Freigabe im Projekt: Das Büro muss sehen, ob der
 * Kunde den Beginn vor Ablauf der Widerrufsfrist verlangt hat, welche Fassungen der
 * Rechtstexte galten und mit welchem Knopf die Annahme ausgelöst wurde.
 * /api ist komplett gestubbt, nur Dummy-Daten (DSGVO).
 */

const PROJEKT_ID = 7;
const DOK_ID = 41;

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubApi(page: Page, audit: Record<string, unknown>) {
    const projekt = {
        id: PROJEKT_ID, bauvorhaben: 'Geländer Musterstraße', kunde: 'Max Mustermann', kundenId: 3,
        kundennummer: 'K-1003', auftragsnummer: '2026/10/00007', anlegedatum: '2026-10-01',
        bruttoPreis: 5000, bezahlt: false, abgeschlossen: false, kundenEmails: [],
        kundeDto: { id: 3, name: 'Max Mustermann', kundennummer: 'K-1003', kundenEmails: [] },
        projektArt: 'PAUSCHAL', materialkosten: [], artikel: [], produktkategorien: [], zeiten: [], emails: [],
    };
    const angebot = {
        id: DOK_ID, typ: 'ANGEBOT', dokumentNummer: 'AN-2026-0041', datum: '2026-10-10',
        betragNetto: 4201.68, betragBrutto: 5000, storniert: false, gebucht: true, digitalAngenommen: true,
        projektId: PROJEKT_ID,
    };

    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'max', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (/^\/api\/last-accessed\/PROJEKT(\/\d+)?$/.test(pfad)) {
            if (methode === 'POST') return route.fulfill({ status: 204, body: '' });
            return json(route, {});
        }
        if (pfad === '/api/projekte' && methode === 'GET') return json(route, { projekte: [], gesamt: 0 });
        if (pfad === `/api/projekte/${PROJEKT_ID}`) return json(route, projekt);
        if (pfad === `/api/ausgangs-dokumente/projekt/${PROJEKT_ID}`) return json(route, [angebot]);
        if (pfad === '/api/ausgangs-dokumente/freigabe-status') {
            return json(route, { [DOK_ID]: { status: 'ACCEPTED', akzeptiertAm: '2026-10-13T09:30:00', ablaufDatum: '2026-10-24T00:00:00' } });
        }
        if (pfad === `/api/ausgangs-dokumente/${DOK_ID}/freigabe-audit`) return json(route, audit);
        if (pfad === '/api/projekte/freigabe-status') return json(route, {});
        return json(route, []);
    });
}

const BASIS_AUDIT = {
    status: 'ACCEPTED', dokumentArt: 'Angebot', dokumentNummer: 'AN-2026-0041',
    erstelltAm: '2026-10-10T08:00:00', ablaufDatum: '2026-10-24T00:00:00', akzeptiertAm: '2026-10-13T09:30:00',
    akzeptiertEmail: null, akzeptiertIp: '192.0.2.10', akzeptiertUserAgent: 'Mozilla/5.0',
    unterzeichnerVorname: 'Max', unterzeichnerNachname: 'Mustermann', unterzeichnerName: 'Max Mustermann',
    hashOriginal: 'a'.repeat(64), hashAcceptance: 'b'.repeat(64),
    codeBestaetigtAm: '2026-10-13T09:30:00', codeBestaetigtEmail: 'max@example.de',
    widerrufStand: '2026-10-13', datenschutzStand: '2026-10-13',
    bestaetigungText: 'Ich beauftrage Angebot AN-2026-0041 verbindlich und zahlungspflichtig.',
    knopfText: 'Zahlungspflichtig beauftragen',
};

test('Annahme-Nachweis zeigt Wartepflicht, Fassungen und Knopf, Info erklärt die Folgen', async ({ page }) => {
    await stubApi(page, { ...BASIS_AUDIT, vorzeitigerBeginn: false });
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);

    await page.getByRole('button', { name: /Angenommen · 13\.10\.26/ }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Rechtsnachweis')).toBeVisible();
    await expect(dialog.getByText('Erst nach Ablauf der Widerrufsfrist beginnen (bis 27.10.2026).')).toBeVisible();
    await expect(dialog.getByText('Widerrufsbelehrung vom 13.10.2026 · Datenschutzerklärung vom 13.10.2026')).toBeVisible();
    await expect(dialog.getByText('„Zahlungspflichtig beauftragen“')).toBeVisible();
    await expect(dialog.getByText(/an max@example\.de/)).toBeVisible();

    // „Schließen“ bleibt auch auf dem 14-Zoll-Bildschirm innerhalb des Dialogs.
    const dialogBox = await dialog.boundingBox();
    const schliessenBox = await dialog.getByRole('button', { name: 'Schließen', exact: true }).boundingBox();
    expect(dialogBox && schliessenBox && schliessenBox.y + schliessenBox.height <= dialogBox.y + dialogBox.height).toBeTruthy();

    await dialog.getByRole('button', { name: /Arbeitsbeginn und Widerrufsfrist/ }).click();
    const info = page.getByText(/bekommen Sie für die geleistete Arbeit kein Geld/);
    await expect(info).toBeVisible();
    // Popover darf nicht aus dem Fenster ragen.
    const box = await info.boundingBox();
    const viewport = page.viewportSize();
    expect(box && viewport && box.x >= 0 && box.x + box.width <= viewport.width).toBeTruthy();
    if (process.env.E2E_SCREENSHOTS) {
        await page.screenshot({ path: `test-results/rechtsnachweis-${test.info().project.name}.png` });
    }
});

test('Annahme mit verlangtem Sofortbeginn zeigt grünen Hinweis', async ({ page }) => {
    await stubApi(page, { ...BASIS_AUDIT, vorzeitigerBeginn: true });
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);
    await page.getByRole('button', { name: /Angenommen · 13\.10\.26/ }).click();
    await expect(page.getByRole('dialog').getByText(/Sofort beginnen erlaubt/)).toBeVisible();
});

test('Altbestand ohne Rechtsnachweis zeigt den Abschnitt nicht', async ({ page }) => {
    await stubApi(page, {
        ...BASIS_AUDIT, vorzeitigerBeginn: null, widerrufStand: null, datenschutzStand: null,
        bestaetigungText: null, knopfText: null, codeBestaetigtAm: null, codeBestaetigtEmail: null,
    });
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);
    await page.getByRole('button', { name: /Angenommen · 13\.10\.26/ }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Kryptographischer Beweis')).toBeVisible();
    await expect(dialog.getByText('Rechtsnachweis')).toBeHidden();
});
