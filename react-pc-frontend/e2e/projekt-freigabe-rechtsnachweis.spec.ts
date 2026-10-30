import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Annahme-Nachweis einer digitalen Freigabe im Projekt und im Verlauf: Das Büro muss
 * lückenlos sehen, welcher Name eingetragen wurde, welche Häkchen mit welchem Wortlaut
 * angehakt waren, mit welchem Knopf die Annahme ausgelöst wurde, ob der Kunde den Beginn
 * vor Ablauf der Widerrufsfrist verlangt hat, und den vollen Annahme-Hash zum Vergleich
 * mit der Mail an den Kunden. /api ist komplett gestubbt, nur Dummy-Daten (DSGVO).
 */

const PROJEKT_ID = 7;
const DOK_ID = 41;
const AB_ID = 42;

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubApi(page: Page, audit: Record<string, unknown>, mitAuftragsbestaetigung = false) {
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
    // Aus der Online-Annahme entstandene Auftragsbestätigung; ihr Nachweis ist der des Angebots.
    const auftragsbestaetigung = {
        id: AB_ID, typ: 'AUFTRAGSBESTAETIGUNG', dokumentNummer: 'AB-2026-0042', datum: '2026-10-13',
        betragNetto: 4201.68, betragBrutto: 5000, storniert: false, gebucht: true, digitalAngenommen: true,
        projektId: PROJEKT_ID, vorgaengerId: DOK_ID,
    };
    const abVerlauf = [{
        id: 900, aktion: 'DIGITAL_ANGENOMMEN', dokumentId: AB_ID, dokumentNummer: 'AB-2026-0042',
        typ: 'AUFTRAGSBESTAETIGUNG', betragNetto: 4201.68, betragBrutto: 5000, gebucht: true, storniert: false,
        digitalAngenommen: true, inhaltHash: 'c'.repeat(64), geaendertVon: null, geaendertVonId: null,
        geaendertAm: '2026-10-13T09:30:00', aenderungsgrund: 'Digitale Annahme durch Kunden', ipAdresse: '192.0.2.10',
    }];

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
        if (pfad === `/api/ausgangs-dokumente/projekt/${PROJEKT_ID}`) {
            return json(route, mitAuftragsbestaetigung ? [angebot, auftragsbestaetigung] : [angebot]);
        }
        if (pfad === `/api/ausgangs-dokumente/${AB_ID}/historie`) return json(route, abVerlauf);
        if (pfad === `/api/ausgangs-dokumente/${AB_ID}/freigabe-audit`) return json(route, audit);
        if (pfad === '/api/ausgangs-dokumente/freigabe-status') {
            return json(route, { [DOK_ID]: { status: 'ACCEPTED', akzeptiertAm: '2026-10-13T09:30:00', ablaufDatum: '2026-10-24T00:00:00' } });
        }
        if (pfad === `/api/ausgangs-dokumente/${DOK_ID}/freigabe-audit`) return json(route, audit);
        if (pfad === '/api/projekte/freigabe-status') return json(route, {});
        return json(route, []);
    });
}

const PFLICHT_TEXT = 'Ich beauftrage Angebot AN-2026-0041 verbindlich und zahlungspflichtig.';
const FREIWILLIG_TEXT = 'Ich verlange ausdrücklich, dass Sie mit der Ausführung schon vor Ablauf der Widerrufsfrist '
    + 'beginnen. Mir ist bekannt, dass mein Widerrufsrecht erlischt, sobald Sie die Leistung vollständig erbracht haben.';
const ANNAHME_HASH = '3f9a0c7e5b21d84f'.repeat(4);

const BASIS_AUDIT = {
    status: 'ACCEPTED', dokumentArt: 'Angebot', dokumentNummer: 'AN-2026-0041',
    erstelltAm: '2026-10-10T08:00:00', ablaufDatum: '2026-10-24T00:00:00', akzeptiertAm: '2026-10-13T09:30:00',
    akzeptiertEmail: 'max@example.de', akzeptiertIp: '192.0.2.10', akzeptiertUserAgent: 'Mozilla/5.0',
    unterzeichnerVorname: 'Max', unterzeichnerNachname: 'Mustermann', unterzeichnerName: 'Max Mustermann',
    hashOriginal: 'a'.repeat(64), hashAcceptance: ANNAHME_HASH,
    codeBestaetigtAm: '2026-10-13T09:29:00', codeBestaetigtEmail: 'max@example.de',
    widerrufStand: '2026-10-13', datenschutzStand: '2026-10-13',
    bestaetigungText: PFLICHT_TEXT,
    knopfText: 'Zahlungspflichtig beauftragen',
    vorzeitigerBeginnText: FREIWILLIG_TEXT,
    akzeptierterBetrag: null,
    mitbeauftragteAlternativen: [] as string[],
};

/** Prüft, dass ein geöffnetes Info-Popover vollständig im Fenster liegt. */
async function popoverImFenster(page: Page, text: RegExp) {
    const info = page.getByText(text);
    await expect(info).toBeVisible();
    const box = await info.boundingBox();
    const viewport = page.viewportSize();
    expect(box && viewport && box.x >= 0 && box.x + box.width <= viewport.width).toBeTruthy();
}

test('Annahme-Beweis zeigt Name, Checkliste, Wartepflicht und vollen Hash, Info erklärt die Folgen', async ({ page }, testInfo) => {
    await stubApi(page, { ...BASIS_AUDIT, vorzeitigerBeginn: false });
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);

    await page.getByRole('button', { name: /Angenommen · 13\.10\.26/ }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Vorname (eingetragen)')).toBeVisible();
    await expect(dialog.getByText('Nachname (eingetragen)')).toBeVisible();
    await expect(dialog.getByText(/13\.10\.2026, 09:29 an max@example\.de/)).toBeVisible();

    const haekchen = dialog.getByRole('list', { name: 'Häkchen bei der Annahme' });
    const pflicht = haekchen.getByRole('listitem').filter({ hasText: 'Pflicht-Häkchen' });
    await expect(pflicht.getByText('angehakt', { exact: true })).toBeVisible();
    await expect(pflicht.getByText(`„${PFLICHT_TEXT}“`)).toBeVisible();
    const freiwillig = haekchen.getByRole('listitem').filter({ hasText: 'Freiwilliges Häkchen' });
    await expect(freiwillig.getByText('nicht angehakt')).toBeVisible();
    await expect(freiwillig.getByText(`„${FREIWILLIG_TEXT}“`)).toBeVisible();
    await expect(dialog.getByText('„Zahlungspflichtig beauftragen“')).toBeVisible();
    await expect(dialog.getByText('Widerrufsbelehrung vom 13.10.2026 · Datenschutzerklärung vom 13.10.2026')).toBeVisible();
    await expect(dialog.getByText('Erst nach Ablauf der Widerrufsfrist beginnen (bis 27.10.2026).')).toBeVisible();
    await expect(dialog.getByText(ANNAHME_HASH, { exact: true })).toBeVisible();

    // „Schließen“ bleibt auch auf dem 14-Zoll-Bildschirm innerhalb des Dialogs.
    const dialogBox = await dialog.boundingBox();
    const schliessenBox = await dialog.getByRole('button', { name: 'Schließen', exact: true }).boundingBox();
    expect(dialogBox && schliessenBox && schliessenBox.y + schliessenBox.height <= dialogBox.y + dialogBox.height).toBeTruthy();
    await designPruefung(page, testInfo, 'annahme-beweis-dialog');

    await dialog.getByRole('button', { name: /Arbeitsbeginn und Widerrufsfrist/ }).click();
    await popoverImFenster(page, /bekommen Sie für die geleistete Arbeit kein Geld/);
    if (process.env.E2E_SCREENSHOTS) {
        await page.screenshot({ path: `test-results/rechtsnachweis-${test.info().project.name}.png` });
    }
});

test('Annahme mit verlangtem Sofortbeginn und Wahlposition zeigt grünen Hinweis und Betrag', async ({ page }) => {
    await stubApi(page, {
        ...BASIS_AUDIT, vorzeitigerBeginn: true,
        mitbeauftragteAlternativen: ['3.2 Geländer Edelstahl'], akzeptierterBetrag: 5450,
    });
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);
    await page.getByRole('button', { name: /Angenommen · 13\.10\.26/ }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText(/Sofort beginnen erlaubt/)).toBeVisible();
    const freiwillig = dialog.getByRole('listitem').filter({ hasText: 'Freiwilliges Häkchen' });
    await expect(freiwillig.getByText('angehakt', { exact: true })).toBeVisible();
    await expect(dialog.getByText('3.2 Geländer Edelstahl')).toBeVisible();
    await expect(dialog.getByText(/5\.450,00\s€/)).toBeVisible();
});

test('Altbestand ohne Rechtsnachweis zeigt keine Checkliste, aber Fingerabdruck und alten Namen', async ({ page }) => {
    await stubApi(page, {
        ...BASIS_AUDIT, vorzeitigerBeginn: null, widerrufStand: null, datenschutzStand: null,
        bestaetigungText: null, knopfText: null, codeBestaetigtAm: null, codeBestaetigtEmail: null,
        vorzeitigerBeginnText: null, unterzeichnerVorname: null, unterzeichnerNachname: null,
    });
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);
    await page.getByRole('button', { name: /Angenommen · 13\.10\.26/ }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Fingerabdruck', { exact: true })).toBeVisible();
    await expect(dialog.getByText('Angenommen von')).toBeVisible();
    await expect(dialog.getByText('Was der Kunde bestätigt hat')).toBeHidden();
});

test('Verlauf der Auftragsbestätigung zeigt den Nachweis des Angebots mit Checkliste und vollem Hash', async ({ page }) => {
    await stubApi(page, { ...BASIS_AUDIT, vorzeitigerBeginn: false }, true);
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);

    await page.getByText('AB-2026-0042').click();
    await page.getByRole('button', { name: 'Verlauf anzeigen' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByText('Annahme-Nachweis', { exact: true })).toBeVisible();
    await expect(dialog.getByText('Angebot AN-2026-0041', { exact: true })).toBeVisible();
    await expect(dialog.getByText('Kunde: Max Mustermann')).toBeVisible();
    await expect(dialog.getByText('Vorname (eingetragen)')).toBeVisible();
    await expect(dialog.getByText(`„${FREIWILLIG_TEXT}“`)).toBeVisible();
    await expect(dialog.getByText('nicht angehakt')).toBeVisible();
    await expect(dialog.getByText('Erst nach Ablauf der Widerrufsfrist beginnen (bis 27.10.2026).')).toBeVisible();
    // Voller Annahme-Hash ohne Aufklappen, der Dokument-Fingerabdruck ist klar anders benannt.
    const hash = dialog.getByText(ANNAHME_HASH, { exact: true });
    await hash.scrollIntoViewIfNeeded();
    await expect(hash).toBeVisible();
    await expect(dialog.getByRole('button', { name: /^Fingerabdruck des Dokuments: c{12}…$/ })).toBeVisible();
    await expect(dialog.getByText(/SHA-256/)).toBeHidden();
    await expect(dialog.getByText('Auftragsbestätigung', { exact: true })).toBeVisible();
    await expect(dialog.getByText('AUFTRAGSBESTAETIGUNG')).toBeHidden();

    await dialog.getByRole('button', { name: 'Mehr zu: Annahme-Nachweis' }).click();
    await popoverImFenster(page, /Die Auftragsbestätigung zeigt den Nachweis des Angebots/);
});

test('Info am Annahme-Hash im Verlauf erklärt den Vergleich mit der Kunden-Mail', async ({ page }, testInfo) => {
    await stubApi(page, { ...BASIS_AUDIT, vorzeitigerBeginn: true }, true);
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);

    await page.getByText('AB-2026-0042').click();
    await page.getByRole('button', { name: 'Verlauf anzeigen' }).click();
    const dialog = page.getByRole('dialog');
    const infoKnopf = dialog.getByRole('button', { name: 'Mehr zu: Annahme-Hash' });
    await infoKnopf.scrollIntoViewIfNeeded();
    await infoKnopf.click();
    await popoverImFenster(page, /der Kunde kann ihn deshalb nicht selbst nachrechnen/);
    await expect(page.getByText(/Er gehört zum Inhalt der Auftragsbestätigung/)).toBeVisible();
    await designPruefung(page, testInfo, 'verlauf-annahme-hash-info');
});
