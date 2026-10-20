import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { bestellungenAntwort } from './hilfen/bestellungen';

/**
 * Bestellungen: Je Reiter lädt die Seite nur die 25 neuesten Ketten,
 * „Mehr laden“ holt die nächsten. Die Suche fragt den Server und findet auch
 * Ketten, die noch gar nicht geladen waren. Nur Fantasienamen (DSGVO),
 * /api vollständig gestubbt, „heute“ fest auf den 05.10.2026.
 */

const HEUTE = new Date('2026-10-05T10:00:00');

/** 30 aktuelle laufende Bestellungen, neueste zuerst (Nr. 1 ist die neueste). */
const AKTUELL = Array.from({ length: 30 }, (_, i) => {
    const nr = i + 1;
    const datum = `2026-09-${String(30 - (i % 28)).padStart(2, '0')}`;
    return {
        id: `l${nr}`, lieferantId: nr, lieferantName: `Musterbau ${String(nr).padStart(2, '0')} GmbH`,
        dokumente: [{
            id: 100 + nr, typ: 'AUFTRAGSBESTAETIGUNG' as const, dokumentNummer: `AB-${1000 + nr}`, dokumentDatum: datum,
            betragBrutto: 100 + nr, betragNetto: null, liefertermin: null, eingangsDatum: datum, dateiname: `AB-${1000 + nr}.pdf`, pdfUrl: null,
        }],
        verbindungen: [], rechnungsVorschlag: null,
    };
});

/** 27 überfällige (seit über 2 Monaten keine Rechnung) – der Server kürzt sie getrennt von den aktuellen. */
const UEBERFAELLIG = Array.from({ length: 27 }, (_, i) => {
    const nr = i + 1;
    const datum = `2026-06-${String(28 - i).padStart(2, '0')}`;
    return {
        id: `u${nr}`, lieferantId: 500 + nr, lieferantName: `Altbau ${String(nr).padStart(2, '0')} KG`,
        dokumente: [{
            id: 500 + nr, typ: 'AUFTRAGSBESTAETIGUNG' as const, dokumentNummer: `AB-${2000 + nr}`, dokumentDatum: datum,
            betragBrutto: 200 + nr, betragNetto: null, liefertermin: null, eingangsDatum: datum, dateiname: `AB-${2000 + nr}.pdf`, pdfUrl: null,
        }],
        verbindungen: [], rechnungsVorschlag: null,
    };
});

const DATEN = { offeneAnfragen: [], laufendeBestellungen: [...AKTUELL, ...UEBERFAELLIG], abgeschlossen: [], zugeordnet: [], ausgeblendet: [] };

function json(route: Route, body: unknown) {
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubApi(page: Page, anfragen: URL[]) {
    await page.clock.setFixedTime(HEUTE);
    await page.route('**/api/**', route => {
        const url = new URL(route.request().url());
        const pfad = url.pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/bestellungen-uebersicht') {
            anfragen.push(url);
            return json(route, bestellungenAntwort(DATEN, url, HEUTE));
        }
        return json(route, []);
    });
}

const karten = (page: Page) => page.getByRole('heading', { level: 3, name: /^Musterbau \d+ GmbH$/ });
const ueberfaellige = (page: Page) => page.getByRole('heading', { level: 3, name: /^Altbau \d+ KG$/ });

test.describe('Bestellungen – Mehr laden und Suche über alle', () => {
    test('zeigt je 25 aktuelle und überfällige, „Mehr laden“ holt den Rest, Info-Knopf erklärt es', async ({ page }, testInfo) => {
        const anfragen: URL[] = [];
        await stubApi(page, anfragen);
        await page.goto('/bestellungen');

        await expect(page.getByText('50 von 57 angezeigt')).toBeVisible();
        await expect(karten(page)).toHaveCount(25);
        await expect(ueberfaellige(page)).toHaveCount(25);
        await expect(page.getByRole('heading', { level: 2, name: /Seit über 2 Monaten keine Rechnung \(25 von 27\)/ })).toBeVisible();
        await expect(page.getByRole('tab', { name: /^Bestellt\s*57$/ })).toBeVisible();
        expect(anfragen[0].searchParams.get('anzahl')).toBe('25');
        expect(anfragen[0].searchParams.has('suche')).toBe(false);

        const mehr = page.getByRole('button', { name: 'Mehr laden', exact: true });
        await mehr.scrollIntoViewIfNeeded();
        await page.getByRole('button', { name: 'Mehr zu: Mehr laden' }).click();
        await expect(page.getByText(/lädt sie pro Reiter zuerst nur die 25 neuesten Bestellungen/)).toBeVisible();
        await designPruefung(page, testInfo, 'bestellungen-mehr-laden-info');
        await page.keyboard.press('Escape');

        const antwort = page.waitForResponse(r => r.url().includes('/api/bestellungen-uebersicht?anzahl=50'));
        await mehr.click();
        await antwort;
        await expect(karten(page)).toHaveCount(30);
        await expect(ueberfaellige(page)).toHaveCount(27);
        await expect(page.getByRole('heading', { level: 2, name: /Seit über 2 Monaten keine Rechnung \(27\)/ })).toBeVisible();
        await expect(page.getByRole('button', { name: 'Mehr laden', exact: true })).toHaveCount(0);
        await expect(page.getByText(/von 57 angezeigt/)).toHaveCount(0);
        // die zuerst geladenen Karten stehen noch da, die neuen sind dazugekommen
        await expect(page.getByRole('heading', { level: 3, name: 'Musterbau 30 GmbH' })).toBeVisible();
    });

    test('Suche fragt den Server und findet auch noch nicht geladene Bestellungen', async ({ page }, testInfo) => {
        const anfragen: URL[] = [];
        await stubApi(page, anfragen);
        await page.goto('/bestellungen');
        await expect(karten(page)).toHaveCount(25);
        await expect(page.getByRole('heading', { level: 3, name: 'Musterbau 28 GmbH' })).toHaveCount(0);

        const antwort = page.waitForResponse(r => r.url().includes('suche=AB-1028'));
        await page.getByLabel('Bestellungen durchsuchen').fill('AB-1028');
        await antwort;
        await expect(karten(page)).toHaveCount(1);
        await expect(page.getByRole('heading', { level: 3, name: 'Musterbau 28 GmbH' })).toBeVisible();
        await expect(page.getByRole('tab', { name: /^Bestellt\s*1$/ })).toBeVisible();
        await expect(ueberfaellige(page)).toHaveCount(0);
        await expect(page.getByRole('button', { name: 'Mehr laden', exact: true })).toHaveCount(0);
        const suche = anfragen.find(u => u.searchParams.get('suche') === 'AB-1028');
        expect(suche?.searchParams.get('anzahl')).toBe('25');

        // ein Zeichen fragt den Server nicht, sondern bittet um mehr
        const vorher = anfragen.length;
        await page.getByLabel('Bestellungen durchsuchen').fill('A');
        await expect(page.getByText('Bitte mindestens 2 Zeichen eingeben.')).toBeVisible();
        // „A“ gilt wie keine Suche: die volle Liste kommt (ohne Suchbegriff) zurück
        await expect(page.getByText('50 von 57 angezeigt')).toBeVisible();
        expect(anfragen.slice(vorher).every(u => !u.searchParams.has('suche'))).toBe(true);

        await page.getByLabel('Bestellungen durchsuchen').fill('Gibt es nicht');
        await expect(page.getByText('Keine Bestellung passt zu „Gibt es nicht“.')).toBeVisible();
        await designPruefung(page, testInfo, 'bestellungen-suche-ohne-treffer');

        await page.getByRole('button', { name: 'Suche löschen' }).click();
        await expect(page.getByText('50 von 57 angezeigt')).toBeVisible();
    });
});
