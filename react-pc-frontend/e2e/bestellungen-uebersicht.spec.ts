import type { Locator, Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung, keinHorizontalerUeberlauf } from './hilfen/design';

/**
 * Bestellungen: Suche über alle Reiter, ältere Angebote eingeklappt, alte
 * Bestellungen oben in Bernstein, Rechnungs-Vorschlag mit „Übernehmen“, die
 * Belege als gerade Linie wie git-Commits (Teillieferungen, Abhängen, Rechnung hochladen) und der
 * Dialog „Rechnung suchen“ / „Dokument zur Kette hinzufügen“ (alle
 * Dokumentarten, Filter-Chips). „Heute“ ist fest auf den 05.10.2026 gesetzt.
 * Nur Fantasienamen (DSGVO), /api vollständig gestubbt.
 */

type Dok = {
    id: number; typ: string; dokumentNummer: string | null; dokumentDatum: string | null; betragBrutto: number | null;
    betragNetto: number | null; liefertermin: string | null; eingangsDatum: string | null; dateiname: string; pdfUrl: string | null;
    ausgeblendet?: boolean;
};

function dok(id: number, typ: string, nummer: string, datum: string, brutto: number | null = null): Dok {
    return {
        id, typ, dokumentNummer: nummer, dokumentDatum: datum, betragBrutto: brutto, betragNetto: brutto ? brutto / 1.19 : null,
        liefertermin: null, eingangsDatum: datum, dateiname: `${nummer}.pdf`, pdfUrl: `/api/test-pdf/${id}`,
    };
}

type Verbindung = { vonId: number; zuId: number };

function kette(id: string, lieferant: string, dokumente: Dok[], rechnungsVorschlag: unknown = null, verbindungen: Verbindung[] = []) {
    return { id, lieferantId: 1, lieferantName: lieferant, dokumente, rechnungsVorschlag, verbindungen };
}

/** Vier Teillieferungen zur selben AB, eine Rechnung über alles. */
const LIEFERSCHEINE = [41, 42, 43, 44].map((id, i) => dok(id, 'LIEFERSCHEIN', `LS-${i + 1}`, `2026-09-0${i + 2}`));
const GABEL = kette('a2', 'Beispiel AG', [
    dok(40, 'AUFTRAGSBESTAETIGUNG', 'AB-40', '2026-09-01', 2380),
    ...LIEFERSCHEINE,
    dok(45, 'RECHNUNG', 'RE-88', '2026-09-20', 2380),
], null, [
    ...LIEFERSCHEINE.map(l => ({ vonId: l.id, zuId: 40 })),
    ...LIEFERSCHEINE.map(l => ({ vonId: 45, zuId: l.id })),
]);

const RECHNUNG_1 = dok(901, 'RECHNUNG', 'RE-123', '2026-09-28', 1190);
const RECHNUNG_2 = dok(902, 'RECHNUNG', 'RE-456', '2026-09-30', 595.5);

function vorschlag(rechnung: Dok, quote: number, bestellId: number, eindeutig: boolean, lieferant: string, gehoertSchonZu: string | null = null) {
    return {
        rechnung, lieferantName: lieferant, bestellDokumentId: bestellId, bestellDokumentTyp: 'AUFTRAGSBESTAETIGUNG',
        bestellDokumentNummer: null, trefferquote: quote, sicher: quote >= 70, eindeutig,
        gruende: ['Gleicher Lieferant', 'Gleiche Bestellnummer', '19 Tage danach'], gehoertSchonZu,
    };
}

/** Vorschlag im Suchdialog (Endpoint ketten-vorschlaege) – jede Dokumentart. */
function kettenVorschlag(dokument: Dok, quote: number, kettenId: number, kettenTyp: string, kettenNummer: string | null,
    eindeutig: boolean, lieferant: string, gehoertSchonZu: string | null = null) {
    return {
        dokument, lieferantName: lieferant, kettenDokumentId: kettenId, kettenDokumentTyp: kettenTyp, kettenDokumentNummer: kettenNummer,
        trefferquote: quote, sicher: quote >= 70, eindeutig, gruende: ['Gleicher Lieferant', 'Belegnummer wird genannt'], gehoertSchonZu,
    };
}

const ZEUGNIS = dok(910, 'WERKSTOFFZEUGNIS', 'WZ-31', '2026-09-26');

/**
 * Mehrere Rechnungen: Angebot, zwei ABs, drei Lieferscheine, zwei Rechnungen.
 * Rechnung 1 deckt Lieferschein /01, Rechnung 2 deckt beide ABs, /03 und /01;
 * Lieferschein /05 ist noch nicht abgerechnet. Werkstoffzeugnis ohne Belegdatum am
 * Lieferschein /01. → alles auf einer Linie, das Zeugnis direkt unter /01.
 */
const JE_RECHNUNG = kette('z2', 'Muster Stahlhandel GmbH', [
    dok(60, 'ANGEBOT', 'AN-7000123', '2026-04-08', 1582.78),
    { ...dok(61, 'AUFTRAGSBESTAETIGUNG', 'RL 99 - 7000123/1', '2026-04-08', 1604.68), liefertermin: '2026-04-14' },
    { ...dok(62, 'AUFTRAGSBESTAETIGUNG', 'RL 99 - 7000123/2', '2026-04-08', 1647.25), liefertermin: '2026-04-14' },
    dok(63, 'LIEFERSCHEIN', '7000123/03', '2026-04-08'),
    dok(64, 'LIEFERSCHEIN', '7000123/01', '2026-04-14', 0),
    dok(65, 'LIEFERSCHEIN', '7000123/05', '2026-04-22'),
    dok(66, 'RECHNUNG', '8000777', '2026-04-16', 1517.64),
    dok(67, 'RECHNUNG', '8000778', '2026-04-20', 99),
    { ...dok(68, 'WERKSTOFFZEUGNIS', '4107891', '2026-04-15'), dokumentDatum: null },
], null, [[61, 60], [62, 60], [63, 61], [64, 62], [65, 62], [66, 64], [67, 61], [67, 62], [67, 63], [67, 64], [68, 64]]
    .map(([vonId, zuId]) => ({ vonId, zuId })));

/** Eine Rechnung über fünf Teillieferungen auf zwei ABs – früher fünf Spuren, jetzt eine gerade Linie. */
const VIELE_SPUREN = kette('z3', 'Beispiel Holz KG', [
    dok(70, 'ANGEBOT', 'AN-7000124', '2026-04-08'),
    { ...dok(71, 'AUFTRAGSBESTAETIGUNG', 'RL 99 - 7000124/01', '2026-04-08', 1604.68), liefertermin: '2026-04-14' },
    { ...dok(72, 'AUFTRAGSBESTAETIGUNG', 'RL 99 - 7000124/02', '2026-04-08', 1647.25), liefertermin: '2026-04-14' },
    ...[73, 74, 75, 76, 77].map((id, i) => dok(id, 'LIEFERSCHEIN', `7000124/0${i + 1}`, `2026-04-1${i}`)),
    dok(78, 'RECHNUNG', '8000779', '2026-04-20', 3251.93),
], null, [[71, 70], [72, 70], [73, 71], [74, 71], [75, 71], [76, 72], [77, 72], [78, 73], [78, 74], [78, 75], [78, 76], [78, 77]]
    .map(([vonId, zuId]) => ({ vonId, zuId })));

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
    abgeschlossen: [
        kette('a1', 'Erika Musterfrau KG', [dok(20, 'AUFTRAGSBESTAETIGUNG', 'AB-1', '2026-08-01', 300), dok(21, 'RECHNUNG', 'RE-77', '2026-08-20', 300)],
            null, [{ vonId: 21, zuId: 20 }]),
        GABEL,
    ],
    zugeordnet: [kette('z1', 'Max Mustermann GmbH', [dok(30, 'RECHNUNG', 'RE-12', '2026-07-01', 99)]), JE_RECHNUNG, VIELE_SPUREN],
    ausgeblendet: [],
};

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

interface Mitschrift { posts: { pfad: string; body: unknown }[]; vorschlagAnfragen: URLSearchParams[]; hochgeladen: string[]; positionenAnfragen: number[] }

function position(id: number, bezeichnung: string, extra: Record<string, unknown> = {}) {
    return {
        id, positionNr: id, positionsArt: 'WARE', externeArtikelnummer: `ART-${id}`, bezeichnung, menge: 6, mengeneinheit: 'Stk',
        einzelpreis: 12.5, preiseinheit: null, gesamtpreisNetto: 75, projektId: null, projektName: null, kostenstelleId: null, kostenstelleName: null,
        werkstoff: null, charge: null, abmessung: null, ...extra,
    };
}

/** Artikelpositionen je Dokument-ID (Rechnung 8000777 und das Zeugnis 4107891). */
const POSITIONEN: Record<number, unknown[]> = {
    66: [position(1, 'Flachstahl 50x5 S235JR'), position(2, 'Rundrohr 33,7x2,6')],
    68: [position(3, 'Flachstahl', { externeArtikelnummer: null, einzelpreis: null, gesamtpreisNetto: null, werkstoff: 'S235JR', charge: '123456', abmessung: '50 x 5' })],
};

interface StubOptionen {
    verknuepfenFehler?: string;
    /** Beim eigenen Lieferanten keine Rechnung – erst „andere Lieferanten“ findet eine schon zugeordnete. */
    eigenerLieferantOhneRechnung?: boolean;
}

async function stubApi(page: Page, mitschrift: Mitschrift, optionen: StubOptionen = {}) {
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
        const positionen = /^\/api\/bestellungen-uebersicht\/positionen\/(\d+)$/.exec(pfad);
        if (positionen) {
            mitschrift.positionenAnfragen.push(Number(positionen[1]));
            return POSITIONEN[Number(positionen[1])] ? json(route, { positionen: POSITIONEN[Number(positionen[1])] }) : json(route, {}, 404);
        }
        if (pfad === '/api/bestellungen-uebersicht/ketten-vorschlaege') {
            mitschrift.vorschlagAnfragen.push(url.searchParams);
            const typ = url.searchParams.get('typ');
            if (optionen.eigenerLieferantOhneRechnung) {
                if (url.searchParams.get('alleLieferanten') !== 'true') return json(route, []);
                return json(route, [kettenVorschlag(dok(904, 'RECHNUNG', 'RE-555', '2026-09-29', 900), 35, 11, 'AUFTRAGSBESTAETIGUNG', 'AB-5', true, 'Max Mustermann GmbH', 'Lieferschein LS-77')]);
            }
            const rechnungen = [
                kettenVorschlag(RECHNUNG_1, 82, 11, 'AUFTRAGSBESTAETIGUNG', 'AB-5', true, 'Erika Musterfrau KG'),
                kettenVorschlag(RECHNUNG_2, 45, 11, 'AUFTRAGSBESTAETIGUNG', 'AB-5', false, 'Max Mustermann GmbH'),
                kettenVorschlag(dok(903, 'RECHNUNG', 'RE-789', '2026-08-01', 20), 12, 11, 'AUFTRAGSBESTAETIGUNG', 'AB-5', true, 'Beispiel AG'),
            ];
            const zeugnisse = [kettenVorschlag(ZEUGNIS, 91, 13, 'LIEFERSCHEIN', 'LS-9', true, 'Beispiel AG')];
            if (typ === 'RECHNUNG') return json(route, rechnungen);
            if (typ === 'WERKSTOFFZEUGNIS') return json(route, zeugnisse);
            if (typ) return json(route, []);
            return json(route, [...zeugnisse, ...rechnungen]);
        }
        if (pfad === '/api/bestellungen-uebersicht/rechnung-hochladen') {
            // multipart: nur den Rohtext mitschreiben (enthält bestellDokumentId und Dateiname)
            mitschrift.hochgeladen.push(request.postDataBuffer()?.toString('latin1') ?? '');
            return json(route, dok(950, 'RECHNUNG', 'RE-NEU', '2026-10-05'));
        }
        if (pfad === '/api/bestellungen-uebersicht/abhaengen') {
            mitschrift.posts.push({ pfad, body: request.postDataJSON() });
            return json(route, { geloest: 2 });
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

function neu(): Mitschrift { return { posts: [], vorschlagAnfragen: [], hochgeladen: [], positionenAnfragen: [] }; }

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
        await expect(page.getByRole('tab', { name: /^Rechnung zuordnen\s*2$/ })).toBeVisible();
        await expect(page.getByRole('tab', { name: /^Erledigt\s*3$/ })).toBeVisible();
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
        await page.getByRole('region', { name: 'Aktuelle Bestellungen' }).getByRole('button', { name: 'Suchen', exact: true }).first().click();

        const dialog = page.getByRole('dialog');
        await expect(dialog.getByRole('heading', { name: /Rechnung suchen – Erika Musterfrau KG/ })).toBeVisible();
        await expect(eintraege(dialog)).toHaveCount(3);
        expect(mitschrift.vorschlagAnfragen[0].getAll('dokumentIds')).toEqual(['10', '11']);
        expect(mitschrift.vorschlagAnfragen[0].get('typ')).toBe('RECHNUNG');
        await expect(dialog.getByRole('button', { name: 'Rechnung', exact: true })).toHaveAttribute('aria-pressed', 'true');
        // zwei Dokumente in der Bestellung: Reiter zum Umschalten
        await expect(dialog.getByRole('tab')).toHaveCount(2);
        await designPruefung(page, testInfo, 'bestellungen-rechnung-suchen');

        await dialog.getByLabel('Vorschläge durchsuchen').fill('RE-456');
        await expect(eintraege(dialog)).toHaveCount(1);
        await eintraege(dialog).first().locator('button[aria-pressed]').click();
        await expect(dialog.getByText(/Gewählt:/)).toContainText('RE-456');

        await dialog.getByRole('button', { name: 'Gehört dazu', exact: true }).click();
        // 45 %: unsicher -> eigene Rückfrage
        await page.getByRole('dialog', { name: 'Wirklich zur Kette hinzufügen?' }).getByRole('button', { name: 'Hinzufügen' }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
        expect(mitschrift.posts[0]).toEqual({ pfad: '/api/bestellungen-uebersicht/ketten-verknuepfen', body: { kettenDokumentId: 11, dokumentId: 902 } });
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
        await page.getByRole('region', { name: 'Aktuelle Bestellungen' }).getByRole('button', { name: 'Suchen', exact: true }).first().click();

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
        await vorschau.getByRole('button', { name: 'Gehört dazu' }).click();
        await page.getByRole('dialog', { name: 'Wirklich zur Kette hinzufügen?' }).getByRole('button', { name: 'Hinzufügen' }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
        expect(mitschrift.posts[0].body).toEqual({ kettenDokumentId: 11, dokumentId: 902 });
        await expect(page.getByRole('dialog')).toHaveCount(0);
    });

    test('Rechnung suchen: direkt aus der Liste zuordnen', async ({ page }) => {
        const mitschrift = neu();
        await stubApi(page, mitschrift);
        await oeffne(page);
        await page.getByRole('region', { name: 'Aktuelle Bestellungen' }).getByRole('button', { name: 'Suchen', exact: true }).first().click();

        await page.getByRole('dialog').getByRole('button', { name: 'Rechnung RE-456 gehört dazu' }).click();
        const frage = page.getByRole('dialog', { name: 'Wirklich zur Kette hinzufügen?' });
        // Abbrechen schickt nichts
        await frage.getByRole('button', { name: 'Abbrechen' }).click();
        expect(mitschrift.posts.length).toBe(0);
        await page.getByRole('dialog', { name: /Rechnung suchen/ }).getByRole('button', { name: 'Rechnung RE-456 gehört dazu' }).click();
        await frage.getByRole('button', { name: 'Hinzufügen' }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
        expect(mitschrift.posts[0].body).toEqual({ kettenDokumentId: 11, dokumentId: 902 });
    });

    test('Linie: Teillieferungen stehen über der Rechnung, Zeile öffnet das Dokument, Abhängen fragt nach', async ({ page }, testInfo) => {
        const mitschrift = neu();
        await stubApi(page, mitschrift);
        await oeffne(page);
        await page.getByRole('tab', { name: /^Rechnung zuordnen/ }).click();

        const karte = page.locator('div.break-inside-avoid').filter({ has: page.getByRole('heading', { level: 3, name: 'Beispiel AG' }) });
        const belege = karte.getByRole('list', { name: 'Belege der Bestellung' });
        await expect(belege.getByRole('listitem')).toHaveCount(6);
        // Rechnung unten, die AB oben
        await expect(belege.getByRole('listitem').first()).toContainText('Auftragsbestätigung');
        await expect(belege.getByRole('listitem').last()).toContainText('RE-88');
        await designPruefung(page, testInfo, 'bestellungen-linie', { ganzeSeite: true });

        await belege.getByRole('button', { name: /^Lieferschein LS-3 \d/ }).click();
        await expect(page.getByRole('heading', { level: 3, name: 'LS-3' })).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(page.getByRole('heading', { level: 3, name: 'LS-3' })).toHaveCount(0);

        await belege.getByRole('button', { name: 'Lieferschein LS-3 von der Bestellung abhängen' }).click();
        const frage = page.getByRole('dialog', { name: 'Beleg abhängen?' });
        await expect(frage).toContainText('nicht mehr automatisch zugeordnet');
        await designPruefung(page, testInfo, 'bestellungen-linie-abhaengen');
        await frage.getByRole('button', { name: 'Abhängen' }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
        expect(mitschrift.posts[0]).toEqual({ pfad: '/api/bestellungen-uebersicht/abhaengen', body: { dokumentId: 43 } });
        await expect(page.getByText('Lieferschein LS-3 abgehängt.')).toBeVisible();
    });

    test('Offenes Ende: Rechnung hochladen hängt sie an das jüngste Bestelldokument', async ({ page }, testInfo) => {
        const mitschrift = neu();
        await stubApi(page, mitschrift);
        await oeffne(page);

        const aktuell = page.getByRole('region', { name: 'Aktuelle Bestellungen' });
        await expect(aktuell.getByText('Rechnung fehlt noch')).toHaveCount(2);
        await designPruefung(page, testInfo, 'bestellungen-offenes-ende');

        // zweite aktuelle Karte: Beispiel AG mit Lieferschein LS-9 (id 13)
        const karte = page.locator('div.break-inside-avoid').filter({ has: page.getByRole('heading', { level: 3, name: 'Beispiel AG' }) });
        const auswahl = page.waitForEvent('filechooser');
        await karte.getByRole('button', { name: 'Rechnung hochladen' }).click();
        await (await auswahl).setFiles({ name: 'rechnung.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%%EOF') });

        await expect.poll(() => mitschrift.hochgeladen.length).toBe(1);
        expect(mitschrift.hochgeladen[0]).toMatch(/name="bestellDokumentId"\r\n\r\n13\r\n/);
        expect(mitschrift.hochgeladen[0]).toContain('filename="rechnung.pdf"');
        await expect(page.getByText(/Rechnung hochgeladen und zugeordnet/)).toBeVisible();
    });

    test('Rechnung suchen: ehrlicher Leerzustand, andere Lieferanten, Rückfrage bei Teillieferung', async ({ page }, testInfo) => {
        const mitschrift = neu();
        await stubApi(page, mitschrift, { eigenerLieferantOhneRechnung: true });
        await oeffne(page);
        await page.getByRole('region', { name: 'Aktuelle Bestellungen' }).getByRole('button', { name: 'Suchen', exact: true }).first().click();

        const dialog = page.getByRole('dialog', { name: /Rechnung suchen/ });
        await expect(dialog.getByText('Von Erika Musterfrau KG ist noch keine Rechnung da.')).toBeVisible();
        expect(mitschrift.vorschlagAnfragen[0].get('alleLieferanten')).toBe('false');
        await designPruefung(page, testInfo, 'bestellungen-rechnung-suchen-leer');

        await dialog.getByRole('button', { name: 'Bei anderen Lieferanten suchen' }).click();
        await expect(dialog.getByText('1 Rechnung von allen Lieferanten')).toBeVisible();
        // Dev-Modus lädt doppelt (StrictMode) – entscheidend ist die letzte Anfrage
        expect(mitschrift.vorschlagAnfragen[mitschrift.vorschlagAnfragen.length - 1].get('alleLieferanten')).toBe('true');
        await expect(dialog.getByText('Hängt schon an Lieferschein LS-77')).toBeVisible();
        await designPruefung(page, testInfo, 'bestellungen-rechnung-suchen-andere');

        await dialog.getByRole('button', { name: 'Rechnung RE-555 gehört dazu' }).click();
        const frage = page.getByRole('dialog', { name: 'Dokument hängt schon an einer anderen Bestellung' });
        await expect(frage).toContainText('Beide Bestellungen werden dann zusammengefasst.');
        await frage.getByRole('button', { name: 'Zusammenfassen' }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
        expect(mitschrift.posts[0].body).toEqual({ kettenDokumentId: 11, dokumentId: 904 });
    });

    test('Dokument hinzufügen: Werkstoffzeugnis oben mit Quote und Gründen, Filter-Chips, ein Klick ordnet zu', async ({ page }, testInfo) => {
        const mitschrift = neu();
        await stubApi(page, mitschrift);
        await oeffne(page);

        // Beispiel AG mit Lieferschein LS-9 (id 13): Knopf in der Fußzeile
        const karte = page.locator('div.break-inside-avoid').filter({ has: page.getByRole('heading', { level: 3, name: 'Beispiel AG' }) });
        await karte.getByRole('button', { name: 'Dokument hinzufügen' }).click();

        const dialog = page.getByRole('dialog', { name: /Dokument zur Kette hinzufügen – Beispiel AG/ });
        await expect(dialog).toBeVisible();
        await expect(dialog.getByRole('button', { name: 'Alle', exact: true })).toHaveAttribute('aria-pressed', 'true');
        expect(mitschrift.vorschlagAnfragen[0].getAll('dokumentIds')).toEqual(['13']);
        expect(mitschrift.vorschlagAnfragen[0].get('typ')).toBeNull();
        await expect(eintraege(dialog)).toHaveCount(4);
        // Zeugnis oben, vorgewählt, mit Quote, Grund und „passt zu“
        const erster = eintraege(dialog).first();
        await expect(erster).toContainText('Werkstoffzeugnis');
        await expect(erster).toContainText('WZ-31');
        await expect(erster).toContainText('91 %');
        await expect(erster).toContainText('Belegnummer wird genannt');
        await expect(erster).toContainText('passt zu: Lieferschein LS-9');
        await expect(dialog.getByRole('button', { name: /WZ-31/, pressed: true })).toBeVisible();
        await designPruefung(page, testInfo, 'bestellungen-dokument-hinzufuegen', { primaerAktion: dialog.getByRole('button', { name: 'Gehört dazu', exact: true }) });

        // Chip „Werkstoffzeugnis“ fragt nur diese Art an
        await dialog.getByRole('button', { name: 'Werkstoffzeugnis', exact: true }).click();
        await expect(dialog.getByRole('button', { name: 'Werkstoffzeugnis', exact: true })).toHaveAttribute('aria-pressed', 'true');
        await expect(eintraege(dialog)).toHaveCount(1);
        expect(mitschrift.vorschlagAnfragen[mitschrift.vorschlagAnfragen.length - 1].get('typ')).toBe('WERKSTOFFZEUGNIS');

        // Gutschrift: nichts da – kein Hochladen, dafür „Alle Dokumentarten zeigen“
        await dialog.getByRole('button', { name: 'Gutschrift', exact: true }).click();
        await expect(dialog.getByText('Von Beispiel AG ist noch keine Gutschrift da, die passen könnte.')).toBeVisible();
        await expect(dialog.getByRole('button', { name: 'Rechnung hochladen' })).toHaveCount(0);
        await designPruefung(page, testInfo, 'bestellungen-dokument-hinzufuegen-leer');
        await dialog.getByRole('button', { name: 'Alle Dokumentarten zeigen' }).click();
        await expect(eintraege(dialog)).toHaveCount(4);

        await dialog.getByRole('button', { name: 'Werkstoffzeugnis WZ-31 gehört dazu' }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
        expect(mitschrift.posts[0]).toEqual({ pfad: '/api/bestellungen-uebersicht/ketten-verknuepfen', body: { kettenDokumentId: 13, dokumentId: 910 } });
        await expect(page.getByText('Werkstoffzeugnis WZ-31 zur Kette hinzugefügt.')).toBeVisible();
        await expect(page.getByRole('dialog')).toHaveCount(0);
    });

    test('Dokument hinzufügen steht auch bei „Rechnung zuordnen“ und „Erledigt“, nicht bei Angeboten', async ({ page }) => {
        await stubApi(page, neu());
        await oeffne(page);
        await page.getByRole('tab', { name: /^Rechnung zuordnen/ }).click();
        await expect(page.getByRole('button', { name: 'Dokument hinzufügen' })).toHaveCount(2);
        await page.getByRole('tab', { name: /^Erledigt/ }).click();
        await expect(page.getByRole('button', { name: 'Dokument hinzufügen' })).toHaveCount(3);
        await page.getByRole('tab', { name: /^Angebote/ }).click();
        await expect(page.getByRole('button', { name: 'Dokument hinzufügen' })).toHaveCount(0);
    });

    test('Lange Kette bleibt eine gerade Linie; Betrag und Abhängen überlappen nie', async ({ page }, testInfo) => {
        await stubApi(page, neu());
        await oeffne(page);
        await page.getByRole('tab', { name: /^Erledigt/ }).click();

        const karte = page.locator('div.break-inside-avoid').filter({ has: page.getByRole('heading', { level: 3, name: 'Beispiel Holz KG' }) });
        const belege = karte.getByRole('list', { name: 'Belege der Bestellung' });
        await expect(belege.getByRole('listitem')).toHaveCount(9);
        await alleAufEinerLinie(belege, 9);
        await betragVorAbhaengen(karte);
        await expect(belege.getByTitle('RL 99 - 7000124/01')).toBeVisible();
        await karte.scrollIntoViewIfNeeded();
        await designPruefung(page, testInfo, 'bestellungen-linie-lang', { ganzeSeite: true });
    });

    test('Mehrere Rechnungen: alles auf einer Linie, Zeugnis unter seinem Lieferschein, keine Kästen', async ({ page }, testInfo) => {
        const mitschrift = neu();
        await stubApi(page, mitschrift);
        await oeffne(page);
        await page.getByRole('tab', { name: /^Erledigt/ }).click();

        const karte = page.locator('div.break-inside-avoid').filter({ has: page.getByRole('heading', { level: 3, name: 'Muster Stahlhandel GmbH' }) });
        // Eine einzige Linie je Karte – keine Kästen je Rechnung mehr
        await expect(karte.getByTestId('ketten-linie')).toHaveCount(1);
        await expect(karte.getByRole('list', { name: /^Belege zu Rechnung/ })).toHaveCount(0);
        const belege = karte.getByRole('list', { name: 'Belege der Bestellung' });
        const zeilen = belege.getByRole('listitem');
        await expect(zeilen).toHaveCount(9);
        const erwartet = ['AN-7000123', '7000123/1', '7000123/2', '7000123/03', '7000123/01', '4107891', '7000123/05', '8000777', '8000778'];
        for (const [i, nummer] of erwartet.entries()) await expect(zeilen.nth(i)).toContainText(nummer);
        // Zeugnis ohne Belegdatum: Eingangsdatum mit Hinweis
        await expect(zeilen.nth(5).getByText('Eingang', { exact: true })).toBeVisible();
        await expect(karte.getByText('auch oben')).toHaveCount(0);
        await alleAufEinerLinie(belege, 9);
        await betragVorAbhaengen(karte);
        await karte.scrollIntoViewIfNeeded();
        await designPruefung(page, testInfo, 'bestellungen-linie-mehrere-rechnungen', { ganzeSeite: true });

        await belege.getByRole('button', { name: /^Werkstoffzeugnis 4107891 \d/ }).click();
        await expect(page.getByRole('heading', { level: 3, name: '4107891' })).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(page.getByRole('heading', { level: 3, name: '4107891' })).toHaveCount(0);

        // Artikelpositionen aufklappen: lädt erst jetzt, öffnet kein PDF, Linie läuft daneben weiter
        expect(mitschrift.positionenAnfragen).toEqual([]);
        await belege.getByRole('button', { name: 'Positionen von Werkstoffzeugnis 4107891 anzeigen' }).click();
        await belege.getByRole('button', { name: 'Positionen von Rechnung 8000777 anzeigen' }).click();
        const tabellen = belege.getByRole('table', { name: 'Artikelpositionen' });
        await expect(tabellen).toHaveCount(2);
        await expect(zeilen.nth(5).getByText('Werkstoff S235JR · Charge 123456 · Abmessung 50 x 5')).toBeVisible();
        await expect(zeilen.nth(7).getByRole('table')).toContainText('Rundrohr 33,7x2,6');
        await expect(page.getByRole('heading', { level: 3, name: '4107891' })).toHaveCount(0);
        await alleAufEinerLinie(belege, 9);
        await karte.scrollIntoViewIfNeeded();
        await designPruefung(page, testInfo, 'bestellungen-linie-positionen', { ganzeSeite: true });

        // Zu- und wieder aufklappen lädt nicht neu
        await belege.getByRole('button', { name: 'Positionen von Rechnung 8000777 ausblenden' }).click();
        await expect(tabellen).toHaveCount(1);
        await belege.getByRole('button', { name: 'Positionen von Rechnung 8000777 anzeigen' }).click();
        await expect(tabellen).toHaveCount(2);
        expect([...mitschrift.positionenAnfragen].sort()).toEqual([66, 68]);
    });
});

/** Alle Punkte liegen auf derselben senkrechten Linie (gleiche Mitte von links). */
async function alleAufEinerLinie(liste: Locator, anzahl: number) {
    const mitten = await liste.locator('[data-punkt]').evaluateAll(punkte =>
        punkte.map(p => { const r = p.getBoundingClientRect(); return Math.round(r.left + r.width / 2); }));
    expect(mitten).toHaveLength(anzahl);
    expect(new Set(mitten).size).toBe(1);
}

/** Einträge der Vorschlagsliste im Suchdialog. */
function eintraege(dialog: Locator) {
    return dialog.getByRole('list', { name: 'Vorschläge' }).locator(':scope > li');
}

/** In jeder Zeile endet der Betrag links vom Abhängen-Knopf. */
async function betragVorAbhaengen(karte: Locator) {
    for (const zeile of await karte.getByRole('listitem').all()) {
        const betrag = zeile.getByText(/\d,\d{2} €$/);
        const knopf = zeile.getByRole('button', { name: /abhängen$/ });
        if (await betrag.count() === 0 || await knopf.count() === 0) continue;
        const b = await betrag.boundingBox();
        const k = await knopf.boundingBox();
        expect(b && k && b.x + b.width <= k.x).toBe(true);
    }
}
