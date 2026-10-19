import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * „Neues Projekt → Von Anfrage übernehmen“: Scheitert die Übernahme im Backend,
 * wird nichts gespeichert und die Anfrage bleibt bestehen. Der Dialog muss das
 * verständlich sagen und offen bleiben, damit der Nutzer es noch einmal versuchen
 * kann. Nur Fantasienamen (DSGVO), /api vollständig gestubbt.
 */

const UEBERNAHME_FEHLGESCHLAGEN =
    'Die Anfrage konnte nicht übernommen werden. Es wurde nichts gespeichert – die Anfrage ist'
    + ' unverändert da. Bitte versuchen Sie es noch einmal.';

const KUNDE = { id: 10, name: 'Max Mustermann', kundennummer: 'K-1010', strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt', kundenEmails: [] };

const ANFRAGE = {
    id: 1,
    bauvorhaben: 'Geländer Musterweg',
    kundenId: KUNDE.id,
    kundenName: KUNDE.name,
    kundennummer: KUNDE.kundennummer,
    anfragesnummer: 'AG-2026/10/00001',
    anlegedatum: '2026-10-01',
    kurzbeschreibung: 'Geländer 12 m, verzinkt',
    abgeschlossen: false,
};

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubProjektAusAnfrage(page: Page, anlegeVersuche: unknown[]) {
    await page.route('**/api/**', (route) => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        const methode = request.method();

        if (pfad === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'anna.buero', displayName: 'Anna Büro',
                active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
            });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (/^\/api\/last-accessed\/[A-Z]+(\/\d+)?$/.test(pfad)) {
            return methode === 'POST' ? route.fulfill({ status: 204, body: '' }) : json(route, {});
        }
        if (pfad === '/api/projekte' && methode === 'GET') return json(route, { projekte: [], gesamt: 0 });
        if (pfad === '/api/projekte' && methode === 'POST') {
            anlegeVersuche.push(request.postDataJSON());
            return json(route, { status: 500, message: UEBERNAHME_FEHLGESCHLAGEN, fields: {} }, 500);
        }
        if (pfad === '/api/projekte/jahre') return json(route, [2026]);
        if (pfad === '/api/projekte/freigabe-status') return json(route, {});
        if (pfad === '/api/projekte/naechste-auftragsnummer') {
            return json(route, {
                auftragsnummer: '2026/10/00101', prefix: '2026/10/', zaehler: 101,
                kundenLogik: false, neuerKundeImJahr: false, kundenSlot: 0, auftragImJahr: 1,
            });
        }
        if (pfad === '/api/projekte/auftragsnummer-verfuegbar') return json(route, { verfuegbar: true });
        if (pfad === '/api/anfragen' && methode === 'GET') return json(route, { anfragen: [ANFRAGE], gesamt: 1 });
        if (pfad === `/api/anfragen/${ANFRAGE.id}/produktkategorien-vorschlag`) return json(route, []);
        if (pfad === `/api/kunden/${KUNDE.id}`) return json(route, KUNDE);
        if (pfad === '/api/kunden' && methode === 'GET') return json(route, [KUNDE]);
        return json(route, []);
    });
}

test.describe('Neues Projekt aus Anfrage', () => {
    test('scheitert die Übernahme, bleibt der Dialog offen und sagt, dass nichts verloren ist', async ({ page }, testInfo) => {
        const anlegeVersuche: unknown[] = [];
        await stubProjektAusAnfrage(page, anlegeVersuche);
        await page.goto('/projekte');

        await page.getByRole('button', { name: 'Neues Projekt' }).click();
        await expect(page.getByRole('heading', { name: 'Neues Projekt anlegen' })).toBeVisible();
        await page.getByText(ANFRAGE.bauvorhaben, { exact: true }).click();
        await expect(page.getByText(`Basierend auf Anfrage: ${ANFRAGE.anfragesnummer}`)).toBeVisible();

        await page.getByRole('button', { name: 'Projekt anlegen' }).click();

        await expect(page.getByText(UEBERNAHME_FEHLGESCHLAGEN)).toBeVisible();
        await expect(page.getByRole('heading', { name: 'Neues Projekt anlegen' })).toBeVisible();
        expect(anlegeVersuche).toHaveLength(1);
        expect(anlegeVersuche[0]).toMatchObject({ anfrageIds: [ANFRAGE.id] });
        await designPruefung(page, testInfo, 'projekt-aus-anfrage-uebernahme-fehlgeschlagen');
    });

    test('„Ohne Anfrage anlegen“ nach gewählter Anfrage übernimmt die Anfrage nicht', async ({ page }, testInfo) => {
        const anlegeVersuche: unknown[] = [];
        await stubProjektAusAnfrage(page, anlegeVersuche);
        await page.goto('/projekte');

        await page.getByRole('button', { name: 'Neues Projekt' }).click();
        await page.getByText(ANFRAGE.bauvorhaben, { exact: true }).click();
        await expect(page.getByText('Nach dem Anlegen gehört die Anfrage zum Projekt und steht nicht mehr in der Anfragenliste.')).toBeVisible();
        await expect(page.getByText(`(${KUNDE.kundennummer})`)).toBeVisible();
        await designPruefung(page, testInfo, 'projekt-aus-anfrage-hinweis');

        await page.getByRole('button', { name: 'Ohne Anfrage anlegen' }).click();
        await expect(page.getByText('Basierend auf Anfrage')).toHaveCount(0);
        await expect(page.getByText('Kein Kunde ausgewählt')).toBeVisible();

        await page.getByPlaceholder('z.B. Neubau Einfamilienhaus').fill('Carport Musterweg');
        await page.getByRole('button', { name: 'Kunde auswählen' }).click();
        await page.getByText(KUNDE.name, { exact: true }).click();
        await page.getByRole('button', { name: 'Projekt anlegen' }).click();

        await expect.poll(() => anlegeVersuche.length).toBe(1);
        expect(anlegeVersuche[0]).not.toHaveProperty('anfrageIds');
        expect(anlegeVersuche[0]).toMatchObject({ bauvorhaben: 'Carport Musterweg', kundenId: KUNDE.id });
    });
});
