import type { Route } from '@playwright/test';
import { test, expect } from './hilfen/test';

/**
 * Ein Angebot, das direkt am Kunden hängt (ohne Projekt und ohne Anfrage), steht in
 * der Kundenakte unter „Dokumente“ und öffnet beim Klick den Dokument-Editor in einem
 * neuen Tab. Vorher zeigte die Akte „Dokumente 0“, während die Datenschutz-Löschung
 * „1 Angebot/Rechnung“ meldete.
 *
 * /api komplett gestubbt (auch für den neuen Tab), Admin angemeldet, nur Dummy-Daten.
 */

const KUNDE_ID = 7;

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const AKTE = {
    id: KUNDE_ID,
    kundennummer: '1007',
    name: 'Max Mustermann',
    strasse: 'Musterweg 1',
    plz: '12345',
    ort: 'Musterstadt',
    zahlungsziel: 8,
    kundenEmails: [],
    statistik: { projektAnzahl: 0, anfrageAnzahl: 0, emailAdresseAnzahl: 0, gesamtUmsatz: 0, gesamtGewinn: 0 },
    kommunikation: [],
    projekte: [],
    anfragen: [],
    geschaeftsdokumente: [{ id: 88, dokumentNummer: 'AN-2026-001', typ: 'ANGEBOT', datum: '2026-10-01', betragBrutto: 1190 }],
    notizen: [],
    gesperrt: false,
    loeschPruefung: { loeschbar: false, hindernisse: ['1 Angebot/Rechnung'], aufbewahrenBis: '2032-12-31' },
};

test('Dokument ohne Projekt steht in der Kundenakte und öffnet den Dokument-Editor', async ({ page }) => {
    await page.context().route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        if (pfad === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'max.admin', displayName: 'Max Mustermann',
                active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
            });
        }
        if (pfad === '/api/notifications/summary') {
            return json(route, { totalCount: 0, categories: [], recentItems: [] });
        }
        if (pfad === '/api/kunden') return json(route, { kunden: [AKTE], gesamt: 1 });
        if (pfad === `/api/kunden/${KUNDE_ID}`) return json(route, AKTE);
        return json(route, []);
    });

    await page.goto(`/kunden?kundeId=${KUNDE_ID}`);
    await page.getByRole('button', { name: /Dokumente/ }).click();
    await expect(page.getByText('Ohne Projekt')).toBeVisible();

    const neuerTab = page.context().waitForEvent('page');
    await page.getByText('AN-2026-001').click();
    const editor = await neuerTab;
    await expect(editor).toHaveURL(/\/dokument-editor\?dokumentId=88&dokumentTyp=ANGEBOT$/);
});
