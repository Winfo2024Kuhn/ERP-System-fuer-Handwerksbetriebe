import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';

/**
 * Issue #289/#264: Das Zahlungsziel eines Kunden liess sich nicht aendern.
 * Das Feld sprang beim Leeren sofort auf 8 zurueck, und das Backend speicherte
 * den Wert nie. Nach "Aktualisieren" stand die Akte ausserdem leer da, weil die
 * knappe Antwort aufs Speichern die volle Akte ersetzte.
 *
 * Ein Ablauf: Akte oeffnen -> Bearbeiten -> 14 Tage -> Aktualisieren -> die Akte
 * zeigt 14 Tage und behaelt ihr Projekt. /api komplett gestubbt, nur Dummy-Daten.
 */

const KUNDE_ID = 7;

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

function akte(zahlungsziel: number) {
    return {
        id: KUNDE_ID,
        kundennummer: '1007',
        name: 'Max Mustermann',
        zahlungsziel,
        kundenEmails: ['max@example.com'],
        statistik: { projektAnzahl: 1, anfrageAnzahl: 0, emailAdresseAnzahl: 1, gesamtUmsatz: 1000, gesamtGewinn: 200 },
        kommunikation: [],
        projekte: [
            { id: 5, bauvorhaben: 'Carport Mustermann', auftragsnummer: 'A-2026-0005', anlegedatum: '2026-01-15', bezahlt: false, bruttoPreis: 1000 },
        ],
        anfragen: [],
        geschaeftsdokumente: [],
        notizen: [],
    };
}

async function stubbeKundenApi(page: Page, gespeichert: Record<string, unknown>[]) {
    let zahlungsziel = 8;
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();

        if (pfad === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'anna.buero', displayName: 'Anna Büro',
                active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
            });
        }
        if (pfad === '/api/notifications/summary') {
            return json(route, { totalCount: 0, categories: [], recentItems: [] });
        }
        if (pfad === '/api/kunden' && methode === 'GET') return json(route, { kunden: [], gesamt: 0 });
        if (pfad === `/api/kunden/${KUNDE_ID}` && methode === 'PUT') {
            const body = route.request().postDataJSON() as Record<string, unknown>;
            gespeichert.push(body);
            zahlungsziel = Number(body.zahlungsziel);
            // Wie das echte Backend: nur Stammdaten, keine Projekte.
            return json(route, { id: KUNDE_ID, kundennummer: '1007', name: 'Max Mustermann', zahlungsziel });
        }
        if (pfad === `/api/kunden/${KUNDE_ID}`) return json(route, akte(zahlungsziel));
        return json(route, []);
    });
}

test.describe('Kunde: Zahlungsziel ändern (#289)', () => {
    test('neues Zahlungsziel wird gespeichert und die Akte bleibt vollständig', async ({ page }) => {
        const gespeichert: Record<string, unknown>[] = [];
        await stubbeKundenApi(page, gespeichert);
        await page.goto(`/kunden?kundeId=${KUNDE_ID}`);
        await expect(page.getByText('8 Tage')).toBeVisible();

        await page.getByRole('button', { name: /Bearbeiten/ }).click();
        const feld = page.getByLabel('Zahlungsziel (Tage)');
        // Zu lang: eigene Meldung, nichts gespeichert.
        await feld.fill('366');
        await page.getByRole('button', { name: 'Aktualisieren' }).click();
        await expect(page.getByText('Das Zahlungsziel muss zwischen 1 und 365 Tagen liegen.')).toBeVisible();
        expect(gespeichert).toHaveLength(0);

        await feld.fill('');
        await expect(feld).toHaveValue('');
        await feld.fill('14');
        await page.getByRole('button', { name: 'Aktualisieren' }).click();

        await expect(page.getByText('14 Tage')).toBeVisible();
        expect(gespeichert).toHaveLength(1);
        expect(gespeichert[0].zahlungsziel).toBe(14);
        await expect(page.getByRole('button', { name: /Projekte/ })).toContainText('1');
    });
});
