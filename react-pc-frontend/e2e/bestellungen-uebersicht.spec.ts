import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung, keinHorizontalerUeberlauf } from './hilfen/design';

/**
 * Bestellungen: Suche über alle Reiter, ältere Angebote eingeklappt, alte
 * Bestellungen oben in Bernstein, Rechnungs-Vorschlag mit „Übernehmen“ und der
 * Dialog „Rechnung suchen“. „Heute“ ist fest auf den 05.10.2026 gesetzt.
 * Nur Fantasienamen (DSGVO), /api vollständig gestubbt.
 */

type Dok = {
    id: number; typ: string; dokumentNummer: string | null; dokumentDatum: string | null; betragBrutto: number | null;
    betragNetto: number | null; liefertermin: string | null; eingangsDatum: string | null; dateiname: string; pdfUrl: string | null;
};

function dok(id: number, typ: string, nummer: string, datum: string, brutto: number | null = null): Dok {
    return {
        id, typ, dokumentNummer: nummer, dokumentDatum: datum, betragBrutto: brutto, betragNetto: brutto ? brutto / 1.19 : null,
        liefertermin: null, eingangsDatum: datum, dateiname: `${nummer}.pdf`, pdfUrl: `/api/test-pdf/${id}`,
    };
}

function kette(id: string, lieferant: string, dokumente: Dok[], rechnungsVorschlag: unknown = null) {
    return { id, lieferantId: 1, lieferantName: lieferant, dokumente, rechnungsVorschlag };
}

const RECHNUNG_1 = dok(901, 'RECHNUNG', 'RE-123', '2026-09-28', 1190);
const RECHNUNG_2 = dok(902, 'RECHNUNG', 'RE-456', '2026-09-30', 595.5);

function vorschlag(rechnung: Dok, quote: number, bestellId: number, eindeutig: boolean, lieferant: string) {
    return {
        rechnung, lieferantName: lieferant, bestellDokumentId: bestellId, bestellDokumentTyp: 'AUFTRAGSBESTAETIGUNG',
        bestellDokumentNummer: null, trefferquote: quote, sicher: quote >= 70, eindeutig,
        gruende: ['Gleicher Lieferant', 'Gleiche Bestellnummer', '19 Tage danach'],
    };
}

const UEBERSICHT = {
    offeneAnfragen: [
        kette('o1', 'Max Mustermann GmbH', [dok(1, 'ANGEBOT', 'AN-2026-17', '2026-09-20', 500)]),
        kette('o2', 'Erika Musterfrau KG', [dok(2, 'ANGEBOT', 'AN-ALT-1', '2026-04-01', 800)]),
        kette('o3', 'Beispiel AG', [dok(3, 'ANGEBOT', 'AN-ALT-2', '2026-03-01', 1200)]),
    ],
    laufendeBestellungen: [
        kette('l1', 'Erika Musterfrau KG', [dok(10, 'ANGEBOT', 'AN-5', '2026-09-01', 1100), dok(11, 'AUFTRAGSBESTAETIGUNG', 'AB-5', '2026-09-10', 1190)],
            vorschlag(RECHNUNG_1, 82, 11, true, 'Erika Musterfrau KG')),
        kette('l2', 'Max Mustermann GmbH', [dok(12, 'AUFTRAGSBESTAETIGUNG', 'AB-7', '2026-06-01', 595.5)],
            vorschlag(RECHNUNG_2, 55, 12, false, 'Max Mustermann GmbH')),
        kette('l3', 'Beispiel AG', [dok(13, 'LIEFERSCHEIN', 'LS-9', '2026-09-25')]),
    ],
    abgeschlossen: [kette('a1', 'Erika Musterfrau KG', [dok(20, 'AUFTRAGSBESTAETIGUNG', 'AB-1', '2026-08-01', 300), dok(21, 'RECHNUNG', 'RE-77', '2026-08-20', 300)])],
    zugeordnet: [kette('z1', 'Max Mustermann GmbH', [dok(30, 'RECHNUNG', 'RE-12', '2026-07-01', 99)])],
    ausgeblendet: [],
};

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

interface Mitschrift { posts: { pfad: string; body: unknown }[]; vorschlagAnfragen: URLSearchParams[] }

async function stubApi(page: Page, mitschrift: Mitschrift, optionen: { verknuepfenFehler?: string } = {}) {
    await page.clock.setFixedTime(new Date('2026-10-05T10:00:00'));
    await page.route('**/api/**', (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const pfad = url.pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad.startsWith('/api/test-pdf/')) {
            return route.fulfill({ status: 200, contentType: 'application/pdf', body: '%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF' });
        }
        if (pfad === '/api/bestellungen-uebersicht' && request.method() === 'GET') return json(route, UEBERSICHT);
        if (pfad === '/api/bestellungen-uebersicht/belege-offen') return json(route, []);
        if (pfad === '/api/bestellungen-uebersicht/rechnung-vorschlaege') {
            mitschrift.vorschlagAnfragen.push(url.searchParams);
            return json(route, [
                vorschlag(RECHNUNG_1, 82, 11, true, 'Erika Musterfrau KG'),
                vorschlag(RECHNUNG_2, 45, 11, false, 'Max Mustermann GmbH'),
                vorschlag(dok(903, 'RECHNUNG', 'RE-789', '2026-08-01', 20), 12, 11, true, 'Beispiel AG'),
            ]);
        }
        if (request.method() === 'POST' && pfad.startsWith('/api/bestellungen-uebersicht/')) {
            mitschrift.posts.push({ pfad, body: request.postDataJSON() });
            if (pfad.endsWith('/rechnung-verknuepfen') && optionen.verknuepfenFehler) {
                return json(route, { message: optionen.verknuepfenFehler }, 400);
            }
            return json(route, { success: true });
        }
        return json(route, []);
    });
}

function neu(): Mitschrift { return { posts: [], vorschlagAnfragen: [] }; }

async function oeffne(page: Page) {
    await page.goto('/bestellungen');
    await expect(page.getByRole('heading', { name: 'BESTELLUNGEN' })).toBeVisible();
}

test.describe('Bestellungen – Übersicht', () => {
    test('Reiter in Handwerker-Sprache mit Zählern; Suche filtert alle Reiter', async ({ page }, testInfo) => {
        await stubApi(page, neu());
        await oeffne(page);

        await expect(page.getByRole('tab', { name: /^Angebote\s*1$/ })).toBeVisible(); // nur frische
        await expect(page.getByRole('tab', { name: /^Bestellt\s*3$/ })).toBeVisible();
        await expect(page.getByRole('tab', { name: /^Rechnung zuordnen\s*1$/ })).toBeVisible();
        await expect(page.getByRole('tab', { name: /^Erledigt\s*1$/ })).toBeVisible();
        await expect(page.getByText('Bestellt oder geliefert – die Rechnung fehlt noch.')).toBeVisible();
        await keinHorizontalerUeberlauf(page);
        await designPruefung(page, testInfo, 'bestellungen-bestellt', { primaerAktion: page.getByLabel('Bestellungen durchsuchen') });

        await page.getByLabel('Bestellungen durchsuchen').fill('RE-77');
        await expect(page.getByRole('tab', { name: /^Rechnung zuordnen\s*1$/ })).toBeVisible();
        await expect(page.getByRole('tab', { name: /^Bestellt\s*0$/ })).toBeVisible();
        // aktiver Reiter „Bestellt“ hat keine Treffer – der Leerzustand sagt, wo es welche gibt
        await expect(page.getByText('Keine Treffer in „Bestellt“')).toBeVisible();
        await designPruefung(page, testInfo, 'bestellungen-suche-leer');
        await page.getByRole('button', { name: 'Rechnung zuordnen (1)' }).click();
        await expect(page.getByRole('heading', { level: 3, name: 'Erika Musterfrau KG' })).toBeVisible();

        await page.getByRole('button', { name: 'Suche löschen' }).click();
        await expect(page.getByRole('tab', { name: /^Bestellt\s*3$/ })).toBeVisible();
    });

    test('ältere Angebote sind eingeklappt, „Alle ausblenden“ fragt nach', async ({ page }, testInfo) => {
        const mitschrift = neu();
        await stubApi(page, mitschrift);
        await oeffne(page);
        await page.getByRole('tab', { name: /^Angebote/ }).click();

        const kopf = page.getByRole('button', { name: /Ältere Angebote ohne Bestellung \(2\)/ });
        await expect(kopf).toHaveAttribute('aria-expanded', 'false');
        await expect(page.getByText('AN-ALT-1')).toHaveCount(0);
        await expect(page.getByText('AN-2026-17')).toBeVisible();
        await designPruefung(page, testInfo, 'bestellungen-angebote-zu');

        await kopf.click();
        await expect(kopf).toHaveAttribute('aria-expanded', 'true');
        await expect(page.getByText('AN-ALT-1')).toBeVisible();
        await designPruefung(page, testInfo, 'bestellungen-angebote-auf');

        await page.getByRole('button', { name: 'Alle ausblenden' }).click();
        const dialog = page.getByRole('dialog');
        await expect(dialog).toContainText('2 ältere Angebote');
        await dialog.getByRole('button', { name: 'Abbrechen' }).click();
        expect(mitschrift.posts).toHaveLength(0);

        await page.getByRole('button', { name: 'Alle ausblenden' }).click();
        await page.getByRole('dialog').getByRole('button', { name: 'Ausblenden' }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
        expect(mitschrift.posts[0].pfad).toBe('/api/bestellungen-uebersicht/ausblenden');
        expect(mitschrift.posts[0].body).toEqual({ dokumentIds: [2, 3] });
    });

    test('bei Suche klappen ältere Angebote mit Treffer von selbst auf', async ({ page }) => {
        await stubApi(page, neu());
        await oeffne(page);
        await page.getByRole('tab', { name: /^Angebote/ }).click();
        await page.getByLabel('Bestellungen durchsuchen').fill('AN-ALT-2');
        await expect(page.getByRole('button', { name: /Ältere Angebote ohne Bestellung \(1\)/ })).toHaveAttribute('aria-expanded', 'true');
        await expect(page.getByText('AN-ALT-2')).toBeVisible();
    });

    test('alte Bestellungen stehen oben mit Bernstein-Hinweis; Vorschlag lässt sich übernehmen', async ({ page }, testInfo) => {
        const mitschrift = neu();
        await stubApi(page, mitschrift);
        await oeffne(page);

        const alt = page.getByRole('region', { name: 'Seit über 2 Monaten keine Rechnung' });
        await expect(alt.getByRole('heading', { level: 2 })).toContainText('(1)');
        await expect(alt.getByText(/Seit 4 Monaten keine Rechnung/)).toBeVisible();
        // alte Bestellung kommt vor der aktuellen im Dokument
        const reihenfolge = await page.getByRole('heading', { level: 3 }).allTextContents();
        expect(reihenfolge[0]).toBe('Max Mustermann GmbH');

        await expect(alt.getByText('Weitere Rechnung gleich wahrscheinlich')).toBeVisible();
        const aktuell = page.getByRole('region', { name: 'Aktuelle Bestellungen' });
        await expect(aktuell.getByText('82 %')).toBeVisible();
        await designPruefung(page, testInfo, 'bestellungen-vorschlag', { ganzeSeite: true });

        await aktuell.getByRole('button', { name: /Rechnung RE-123 übernehmen/ }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
        expect(mitschrift.posts[0]).toEqual({
            pfad: '/api/bestellungen-uebersicht/rechnung-verknuepfen',
            body: { bestellDokumentId: 11, rechnungDokumentId: 901 },
        });
        await expect(page.getByText('Rechnung RE-123 zugeordnet.')).toBeVisible();
    });

    test('unsicherer Vorschlag auf der Karte fragt vor dem Übernehmen nach', async ({ page }) => {
        const mitschrift = neu();
        await stubApi(page, mitschrift);
        await oeffne(page);

        const alt = page.getByRole('region', { name: 'Seit über 2 Monaten keine Rechnung' });
        await alt.getByRole('button', { name: /Rechnung RE-456 übernehmen/ }).click();
        const frage = page.getByRole('dialog', { name: 'Rechnung wirklich zuordnen?' });
        await expect(frage.getByText(/Eine andere Rechnung passt genauso gut/)).toBeVisible();
        await frage.getByRole('button', { name: 'Abbrechen' }).click();
        expect(mitschrift.posts.length).toBe(0);

        await alt.getByRole('button', { name: /Rechnung RE-456 übernehmen/ }).click();
        await frage.getByRole('button', { name: 'Zuordnen' }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
    });

    test('Fehler beim Übernehmen kommt als Toast mit Meldung des Servers', async ({ page }) => {
        await stubApi(page, neu(), { verknuepfenFehler: 'Rechnung gehört schon zu einer anderen Bestellung.' });
        await oeffne(page);
        await page.getByRole('button', { name: /Rechnung RE-123 übernehmen/ }).click();
        await expect(page.getByText('Rechnung gehört schon zu einer anderen Bestellung.')).toBeVisible();
    });

    test('Rechnung suchen: Dialog mit Auswahl und Verknüpfen', async ({ page }, testInfo) => {
        const mitschrift = neu();
        await stubApi(page, mitschrift);
        await oeffne(page);

        // erste aktuelle Bestellung: Erika Musterfrau KG (AB-5)
        await page.getByRole('region', { name: 'Aktuelle Bestellungen' }).getByRole('button', { name: 'Rechnung suchen' }).first().click();

        const dialog = page.getByRole('dialog');
        await expect(dialog.getByRole('heading', { name: /Rechnung suchen – Erika Musterfrau KG/ })).toBeVisible();
        await expect(dialog.getByRole('list', { name: 'Rechnungen' }).getByRole('button', { name: /^RE-/ })).toHaveCount(3);
        expect(mitschrift.vorschlagAnfragen[0].getAll('dokumentIds')).toEqual(['10', '11']);
        // zwei Dokumente in der Bestellung: Reiter zum Umschalten
        await expect(dialog.getByRole('tab')).toHaveCount(2);
        await designPruefung(page, testInfo, 'bestellungen-rechnung-suchen');

        await dialog.getByLabel('Rechnungen durchsuchen').fill('RE-456');
        await expect(dialog.getByRole('list', { name: 'Rechnungen' }).getByRole('button', { name: /^RE-/ })).toHaveCount(1);
        await dialog.getByRole('button', { name: /^RE-456/ }).click();
        await expect(dialog.getByText(/Gewählt:/)).toContainText('RE-456');

        await dialog.getByRole('button', { name: 'Diese Rechnung gehört dazu' }).click();
        // 45 %: unsicher -> eigene Rückfrage
        await page.getByRole('dialog', { name: 'Rechnung wirklich zuordnen?' }).getByRole('button', { name: 'Zuordnen' }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
        expect(mitschrift.posts[0].body).toEqual({ bestellDokumentId: 11, rechnungDokumentId: 902 });
        await expect(page.getByRole('dialog')).toHaveCount(0);
    });

    test('Vorschau der wahrscheinlichen Rechnung direkt auf der Karte', async ({ page }) => {
        await stubApi(page, neu());
        await oeffne(page);

        await page.getByRole('region', { name: 'Aktuelle Bestellungen' })
            .getByRole('button', { name: 'Vorschau Rechnung RE-123' }).click();
        await expect(page.getByRole('heading', { level: 3, name: 'RE-123' })).toBeVisible();
    });

    test('Rechnung suchen: großes Vorschaufenster je Rechnung, von dort zuordnen', async ({ page }, testInfo) => {
        const mitschrift = neu();
        await stubApi(page, mitschrift);
        await oeffne(page);
        await page.getByRole('region', { name: 'Aktuelle Bestellungen' }).getByRole('button', { name: 'Rechnung suchen' }).first().click();

        const suchen = page.getByRole('dialog', { name: /Rechnung suchen/ });
        await suchen.getByRole('button', { name: 'Vorschau Rechnung RE-456' }).click();
        const vorschau = page.getByRole('dialog', { name: 'Rechnung RE-456' });
        await expect(vorschau).toBeVisible();
        await designPruefung(page, testInfo, 'bestellungen-rechnung-vorschau');

        // Escape schließt nur das Vorschaufenster, die Suche bleibt offen
        await page.keyboard.press('Escape');
        await expect(vorschau).toHaveCount(0);
        await expect(suchen).toBeVisible();

        await suchen.getByRole('button', { name: 'Vorschau Rechnung RE-456' }).click();
        await vorschau.getByRole('button', { name: 'Diese Rechnung gehört dazu' }).click();
        await page.getByRole('dialog', { name: 'Rechnung wirklich zuordnen?' }).getByRole('button', { name: 'Zuordnen' }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
        expect(mitschrift.posts[0].body).toEqual({ bestellDokumentId: 11, rechnungDokumentId: 902 });
        await expect(page.getByRole('dialog')).toHaveCount(0);
    });

    test('Rechnung suchen: direkt aus der Liste zuordnen', async ({ page }) => {
        const mitschrift = neu();
        await stubApi(page, mitschrift);
        await oeffne(page);
        await page.getByRole('region', { name: 'Aktuelle Bestellungen' }).getByRole('button', { name: 'Rechnung suchen' }).first().click();

        await page.getByRole('dialog').getByRole('button', { name: 'Rechnung RE-456 zuordnen' }).click();
        const frage = page.getByRole('dialog', { name: 'Rechnung wirklich zuordnen?' });
        // Abbrechen schickt nichts
        await frage.getByRole('button', { name: 'Abbrechen' }).click();
        expect(mitschrift.posts.length).toBe(0);
        await page.getByRole('dialog', { name: /Rechnung suchen/ }).getByRole('button', { name: 'Rechnung RE-456 zuordnen' }).click();
        await frage.getByRole('button', { name: 'Zuordnen' }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
        expect(mitschrift.posts[0].body).toEqual({ bestellDokumentId: 11, rechnungDokumentId: 902 });
    });
});
