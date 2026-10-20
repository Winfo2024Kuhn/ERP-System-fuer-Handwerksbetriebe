import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Issue #264 Punkt 5: Kunde aus dem System bringen (Datenschutz).
 *
 * Zwei Abläufe:
 * - Kunde mit Rechnungen: „Löschen…“ öffnet den Datenschutz-Dialog, die Sperre
 *   löscht das Nicht-Nötige, die Akte zeigt danach das Gesperrt-Band.
 * - Kunde ohne Unterlagen: Rückfrage, endgültig löschen, zurück zur Liste.
 * - Gesperrter Kunde nach Fristende: „Anonymisieren…“ im Band, Bestätigung,
 *   danach zeigt das Band „Anonymisiert am …“.
 *
 * /api komplett gestubbt, Admin angemeldet, nur Dummy-Daten.
 */

const KUNDE_ID = 7;

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

function akte(loeschbar: boolean, gesperrt = false) {
    return {
        id: KUNDE_ID,
        kundennummer: '1007',
        name: 'Max Mustermann',
        ansprechspartner: gesperrt ? '' : 'Erika Musterfrau',
        strasse: 'Musterweg 1',
        plz: '12345',
        ort: 'Musterstadt',
        telefon: gesperrt ? '' : '0123 456789',
        zahlungsziel: 8,
        kundenEmails: gesperrt ? [] : ['max@example.com'],
        statistik: { projektAnzahl: 1, anfrageAnzahl: 0, emailAdresseAnzahl: 1, gesamtUmsatz: 1000, gesamtGewinn: 200 },
        kommunikation: [],
        projekte: loeschbar ? [] : [
            { id: 5, bauvorhaben: 'Carport Mustermann', auftragsnummer: 'A-2026-0005', anlegedatum: '2026-01-15', bezahlt: true, bruttoPreis: 1000 },
        ],
        anfragen: [],
        geschaeftsdokumente: [],
        notizen: [],
        gesperrt,
        gesperrtAm: gesperrt ? '2026-10-20T09:30:00' : null,
        aufbewahrenBis: gesperrt ? '2034-12-31' : null,
        loeschPruefung: loeschbar
            ? { loeschbar: true, hindernisse: [], aufbewahrenBis: null }
            : { loeschbar: false, hindernisse: ['1 Projekt', '2 Angebote/Rechnungen'], aufbewahrenBis: '2034-12-31' },
    };
}

interface Mitschrift { geloescht: number; sperren: Record<string, unknown>[] }

async function stubbeApi(page: Page, loeschbar: boolean): Promise<Mitschrift> {
    const mitschrift: Mitschrift = { geloescht: 0, sperren: [] };
    let gesperrt = false;
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();

        if (pfad === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'max.admin', displayName: 'Max Mustermann',
                active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
            });
        }
        if (pfad === '/api/notifications/summary') {
            return json(route, { totalCount: 0, categories: [], recentItems: [] });
        }
        if (pfad === '/api/kunden' && methode === 'GET') {
            return json(route, { kunden: mitschrift.geloescht ? [] : [akte(loeschbar, gesperrt)], gesamt: mitschrift.geloescht ? 0 : 1 });
        }
        if (pfad === `/api/kunden/${KUNDE_ID}/datenschutz-sperre` && methode === 'POST') {
            mitschrift.sperren.push(route.request().postDataJSON() as Record<string, unknown>);
            gesperrt = true;
            return json(route, akte(loeschbar, true));
        }
        if (pfad === `/api/kunden/${KUNDE_ID}` && methode === 'DELETE') {
            mitschrift.geloescht++;
            return route.fulfill({ status: 204 });
        }
        if (pfad === `/api/kunden/${KUNDE_ID}`) return json(route, akte(loeschbar, gesperrt));
        return json(route, []);
    });
    return mitschrift;
}

/** Gesperrter Kunde, Frist abgelaufen; nach dem POST anonymisiert. */
function abgelaufeneAkte(anonymisiert: boolean) {
    return {
        ...akte(false, true),
        name: anonymisiert ? 'Gelöschter Kunde 1007' : 'Max Mustermann',
        strasse: anonymisiert ? '' : 'Musterweg 1',
        plz: anonymisiert ? '' : '12345',
        ort: anonymisiert ? '' : 'Musterstadt',
        gesperrtAm: '2026-02-03T09:30:00',
        aufbewahrenBis: '2025-12-31',
        anonymisiertAm: anonymisiert ? '2026-10-20T09:15:00' : null,
        loeschPruefung: { loeschbar: false, hindernisse: ['1 Projekt'], aufbewahrenBis: '2025-12-31', anonymisierbar: !anonymisiert },
    };
}

async function stubbeAnonymisieren(page: Page): Promise<{ anonymisiert: number }> {
    const mitschrift = { anonymisiert: 0 };
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        if (pfad === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'max.admin', displayName: 'Max Mustermann',
                active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
            });
        }
        if (pfad === '/api/notifications/summary') {
            return json(route, { totalCount: 0, categories: [], recentItems: [] });
        }
        if (pfad === '/api/kunden' && methode === 'GET') {
            return json(route, { kunden: [abgelaufeneAkte(mitschrift.anonymisiert > 0)], gesamt: 1 });
        }
        if (pfad === `/api/kunden/${KUNDE_ID}/anonymisieren` && methode === 'POST') {
            mitschrift.anonymisiert++;
            return json(route, abgelaufeneAkte(true));
        }
        if (pfad === `/api/kunden/${KUNDE_ID}`) return json(route, abgelaufeneAkte(mitschrift.anonymisiert > 0));
        return json(route, []);
    });
    return mitschrift;
}

test.describe('Kunde löschen (Datenschutz, #264)', () => {
    test('Kunde mit Rechnungen wird gesperrt statt gelöscht', async ({ page }, testInfo) => {
        const mitschrift = await stubbeApi(page, false);
        await page.goto(`/kunden?kundeId=${KUNDE_ID}`);
        await expect(page.getByText('Erika Musterfrau').first()).toBeVisible();

        await page.getByRole('button', { name: /Löschen…/ }).click();
        const dialog = page.getByRole('dialog', { name: 'Kundendaten löschen (Datenschutz)' });
        await expect(dialog.getByText('An diesem Kunden hängen: 1 Projekt, 2 Angebote/Rechnungen.')).toBeVisible();
        await expect(dialog.getByText('31.12.2034')).toBeVisible();

        // Info-Button im Dialog: öffnet sich und wird nicht abgeschnitten.
        await dialog.getByRole('button', { name: /Mehr zu/ }).click();
        await expect(page.getByText(/Rechnungen müssen 8 Jahre/)).toBeInViewport();
        await page.keyboard.press('Escape');

        await dialog.getByLabel('Grund (optional)').fill('Löschwunsch per E-Mail vom 20.10.2026');
        await designPruefung(page, testInfo, 'kunde-datenschutz-dialog');
        await dialog.getByRole('button', { name: /Daten löschen und sperren/ }).click();

        await expect(page.getByText('Kundendaten wurden gelöscht, der Kunde ist gesperrt.')).toBeVisible();
        const band = page.getByTestId('kunde-gesperrt-band');
        await expect(band).toContainText('Aus Datenschutzgründen gesperrt seit 20.10.2026');
        await expect(band).toContainText('bis 31.12.2034 aufbewahrt');
        await expect(page.getByRole('button', { name: /Bearbeiten/ })).toHaveCount(0);
        await expect(page.getByRole('button', { name: /Löschen…/ })).toHaveCount(0);
        await expect(page.getByTestId('kunde-reiterleiste').getByText('Notizen')).toHaveCount(0);
        expect(mitschrift.sperren).toEqual([{ grund: 'Löschwunsch per E-Mail vom 20.10.2026' }]);

        await designPruefung(page, testInfo, 'kunde-gesperrt-akte');
    });

    test('Kunde ohne Unterlagen wird nach Rückfrage gelöscht', async ({ page }) => {
        const mitschrift = await stubbeApi(page, true);
        await page.goto(`/kunden?kundeId=${KUNDE_ID}`);
        await expect(page.getByText('Erika Musterfrau').first()).toBeVisible();

        await page.getByRole('button', { name: /Löschen…/ }).click();
        const rueckfrage = page.getByRole('dialog', { name: 'Kunde endgültig löschen?' });
        await expect(rueckfrage).toContainText('1007 · Max Mustermann');
        await expect(rueckfrage).toContainText('Das lässt sich nicht rückgängig machen.');
        await rueckfrage.getByRole('button', { name: 'Endgültig löschen' }).click();

        await expect(page.getByText('Kunde wurde gelöscht.')).toBeVisible();
        await expect(page.getByRole('heading', { name: /Kundenübersicht/i })).toBeVisible();
        await expect(page.getByText('Keine Kunden gefunden.').first()).toBeVisible();
        expect(mitschrift.geloescht).toBe(1);
    });

    test('Gesperrter Kunde wird nach Fristende anonymisiert', async ({ page }, testInfo) => {
        const mitschrift = await stubbeAnonymisieren(page);
        await page.goto(`/kunden?kundeId=${KUNDE_ID}`);
        const band = page.getByTestId('kunde-gesperrt-band');
        await expect(band).toContainText('bis 31.12.2025 aufbewahrt, danach kann der Kunde anonymisiert werden');

        await designPruefung(page, testInfo, 'kunde-gesperrt-band-anonymisierbar');
        await band.getByRole('button', { name: /Anonymisieren…/ }).click();
        const dialog = page.getByRole('dialog', { name: 'Kunde anonymisieren?' });
        await expect(dialog).toContainText('1007 · Max Mustermann');
        await expect(dialog).toContainText('Der Name wird zu „Gelöschter Kunde 1007“');

        // Info-Button im Dialog: öffnet sich und wird nicht abgeschnitten.
        await dialog.getByRole('button', { name: /Mehr zu/ }).click();
        await expect(page.getByText(/Der Kunde ist in den Stammdaten nicht mehr zu erkennen/)).toBeInViewport();
        await page.keyboard.press('Escape');

        await designPruefung(page, testInfo, 'kunde-anonymisieren-dialog');
        await dialog.getByRole('button', { name: /Endgültig anonymisieren/ }).click();

        await expect(page.getByText('Kunde wurde anonymisiert.')).toBeVisible();
        await expect(band).toContainText('Anonymisiert am 20.10.2026. Name und Anschrift wurden entfernt');
        await expect(page.getByText('Gelöschter Kunde 1007').first()).toBeVisible();
        await expect(band.getByRole('button', { name: /Anonymisieren…/ })).toHaveCount(0);
        expect(mitschrift.anonymisiert).toBe(1);
    });
});
