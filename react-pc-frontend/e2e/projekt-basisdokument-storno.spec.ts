import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Issue #262: Wurde im Projekt eine Rechnung direkt (ohne Angebot) angelegt und
 * storniert, muss „Dokument erstellen“ → „Rechnung“ wieder möglich sein. Eine
 * stornierte Rechnung samt Stornorechnung zählt nicht als Basisdokument.
 * /api ist komplett gestubbt, nur Dummy-Daten (DSGVO).
 */

const PROJEKT_ID = 7;

const RECHNUNG = {
    id: 801,
    dokumentNummer: 'RE-2026-0801',
    typ: 'RECHNUNG' as const,
    datum: '2026-09-01',
    betreff: 'Geländer Musterstraße',
    betragNetto: 1000,
    betragBrutto: 1190,
    gebucht: true,
    storniert: false,
    bearbeitbar: false,
    projektId: PROJEKT_ID,
};

const STORNIERT = { ...RECHNUNG, storniert: true, storniertAm: '2026-09-05' };
const STORNO = {
    ...RECHNUNG,
    id: 802,
    dokumentNummer: 'ST-2026-0802',
    typ: 'STORNO' as const,
    betreff: 'Stornorechnung ST-2026-0802 (zu Rechnung RE-2026-0801)',
    betragNetto: -1000,
    betragBrutto: -1190,
    vorgaengerId: RECHNUNG.id,
};

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubProjektApi(page: Page, dokumente: unknown[]) {
    await page.route('**/api/**', (route) => {
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
                bruttoPreis: 5000, bezahlt: false, abgeschlossen: false, kundenEmails: [],
                materialkosten: [], artikel: [], produktkategorien: [], zeiten: [], emails: [],
            });
        }
        if (pfad === `/api/ausgangs-dokumente/projekt/${PROJEKT_ID}`) return json(route, dokumente);
        if (pfad === '/api/ausgangs-dokumente/freigabe-status' || pfad === '/api/projekte/freigabe-status') return json(route, {});
        return json(route, []);
    });
}

async function oeffneDokumentErstellen(page: Page) {
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);
    await page.getByRole('button', { name: 'Dokument erstellen' }).click();
    const dialog = page.getByRole('dialog').filter({ hasText: 'Welche Art von Dokument möchten Sie erstellen?' });
    await expect(dialog).toBeVisible();
    return dialog;
}

test('nach Storno der direkt angelegten Rechnung ist „Rechnung“ wieder wählbar', async ({ page }, testInfo) => {
    await stubProjektApi(page, [STORNIERT, STORNO]);

    const dialog = await oeffneDokumentErstellen(page);

    const rechnung = dialog.getByRole('button', { name: 'Rechnung', exact: true });
    await expect(rechnung).toBeEnabled();
    await expect(dialog.getByRole('button', { name: 'Angebot', exact: true })).toBeEnabled();
    await expect(dialog.getByRole('button', { name: 'Nachtragsangebot', exact: true })).toBeDisabled();
    await designPruefung(page, testInfo, 'projekt-dokument-erstellen-nach-storno', { primaerAktion: rechnung });
});

test('solange die Rechnung aktiv ist, bleibt „Rechnung“ gesperrt und erklärt warum', async ({ page }) => {
    await stubProjektApi(page, [RECHNUNG]);

    const dialog = await oeffneDokumentErstellen(page);

    const rechnung = dialog.getByRole('button', { name: 'Rechnung', exact: true });
    await expect(rechnung).toBeDisabled();
    await expect(rechnung).toHaveAttribute('title', 'Es existiert bereits ein Basisdokument');
});
