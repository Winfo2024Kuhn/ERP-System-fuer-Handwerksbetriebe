import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';

/**
 * Storno einer Abschlagsrechnung im Projekt: Der Server schickt Hinweise zu
 * betroffenen Schlussrechnungen mit (mitstorniert bzw. Entwurf prüfen). Beide
 * müssen als Toast erscheinen, nicht nur im Server-Log. /api ist komplett
 * gestubbt, nur Dummy-Daten (DSGVO).
 */

const PROJEKT_ID = 7;

const ABSCHLAG = {
    id: 701,
    dokumentNummer: 'AR-2026-0701',
    typ: 'ABSCHLAGSRECHNUNG' as const,
    datum: '2026-09-01',
    betreff: 'Abschlag Geländer',
    betragNetto: 1000,
    betragBrutto: 1190,
    gebucht: true,
    storniert: false,
    bearbeitbar: false,
    projektId: PROJEKT_ID,
};

const HINWEIS_MIT = 'Die Schlussrechnung SR-2026-0702 wurde mitstorniert (Stornorechnung ST-2026-0002), weil sie die Abschlagsrechnung AR-2026-0701 abgezogen hat.';
const HINWEIS_ENTWURF = 'Die Schlussrechnung SR-2026-0703 ist noch ein Entwurf. Die Abschlagsrechnung AR-2026-0701 wird dort nicht mehr abgezogen. Bitte öffnen Sie die Schlussrechnung, prüfen Sie die Beträge und speichern Sie sie, bevor Sie sie buchen.';

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubProjektApi(page: Page, stornoAntwort: unknown) {
    const stornoAufrufe: string[] = [];
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
        if (pfad === `/api/ausgangs-dokumente/projekt/${PROJEKT_ID}`) return json(route, [ABSCHLAG]);
        if (pfad === `/api/ausgangs-dokumente/${ABSCHLAG.id}/storno` && methode === 'POST') {
            stornoAufrufe.push(pfad);
            return json(route, stornoAntwort);
        }
        if (pfad === '/api/ausgangs-dokumente/freigabe-status' || pfad === '/api/projekte/freigabe-status') return json(route, {});
        return json(route, []);
    });
    return stornoAufrufe;
}

async function abschlagStornieren(page: Page) {
    await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);
    await page.getByTitle('Klick für Aktionen, Doppelklick zum Öffnen').filter({ hasText: ABSCHLAG.dokumentNummer }).click();
    await page.getByRole('button', { name: 'Stornieren' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Stornieren' }).click();
}

test('Storno einer Abschlagsrechnung zeigt Hinweise zu den Schlussrechnungen als Toast', async ({ page }) => {
    const aufrufe = await stubProjektApi(page, {
        id: 900, dokumentNummer: 'ST-2026-0001', typ: 'STORNO',
        hinweise: [
            { art: 'ENTWURF_PRUEFEN', text: HINWEIS_ENTWURF },
            { art: 'MITSTORNIERT', text: HINWEIS_MIT },
        ],
    });

    await abschlagStornieren(page);

    await expect(page.getByText('Stornorechnung ST-2026-0001 wurde erstellt')).toBeVisible();
    await expect(page.getByRole('status').filter({ hasText: HINWEIS_MIT })).toBeVisible();
    const entwurfToast = page.getByRole('alert').filter({ hasText: HINWEIS_ENTWURF });
    await expect(entwurfToast).toBeVisible();
    // Die Warnung steht zuletzt, der Stapel rollt dorthin: sie ist ganz zu sehen.
    await expect(entwurfToast).toBeInViewport({ ratio: 1 });
    expect(aufrufe).toHaveLength(1);
});

test('Storno ohne betroffene Schlussrechnung zeigt nur die Erfolgsmeldung', async ({ page }) => {
    await stubProjektApi(page, { id: 900, dokumentNummer: 'ST-2026-0001', typ: 'STORNO', hinweise: [] });

    await abschlagStornieren(page);

    await expect(page.getByText('Stornorechnung ST-2026-0001 wurde erstellt')).toBeVisible();
    await expect(page.getByText(/Schlussrechnung SR-/)).toHaveCount(0);
});
