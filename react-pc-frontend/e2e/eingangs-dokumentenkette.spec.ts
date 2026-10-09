import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Lieferanten-Belege, die zusammengehören, stehen überall gleich als gerade Linie
 * (wie git-Commits auf einem Branch):
 *   - Projekt → Geschäftsdokumente → Eingangsrechnungen: die ganze Kette, das
 *     Werkstoffzeugnis direkt unter seinem Lieferschein, Klick öffnet das PDF.
 *   - Dokumentübersicht → Eingang: ohne erkanntes Dokumentdatum steht das
 *     Eingangsdatum mit dezentem Hinweis „Eingang“.
 * /api vollständig gestubbt, nur Fantasienamen (DSGVO).
 */

const PROJEKT_ID = 8;
const LIEFERANT = 'Stahlhandel Beispiel GmbH';

const MINI_PDF = Buffer.from(
    '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
    '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Size 4/Root 1 0 R>>\n%%EOF',
);

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

function kettenDok(id: number, typ: string, nummer: string, dokumentDatum: string | null, extra: Record<string, unknown> = {}) {
    return {
        id, typ, dokumentNummer: nummer, dokumentDatum, betragNetto: null, betragBrutto: null,
        eingangsDatum: dokumentDatum ?? '2026-03-20', dateiname: `${nummer}.pdf`, ausgeblendet: false,
        pdfUrl: `/api/test-pdf/${id}`, ...extra,
    };
}

const EINGANGSRECHNUNG = {
    id: 701,
    dokumentId: 50,
    geschaeftsdokumentId: 50,
    dokumentNummer: 'R-900',
    dateiname: 'R-900.pdf',
    dokumentDatum: '2026-04-02',
    gesamtbetrag: 1050.42,
    prozent: 100,
    berechneterBetrag: 1050.42,
    beschreibung: 'Flachstahl für Geländer',
    lieferantId: 21,
    lieferantName: LIEFERANT,
    pdfUrl: '/api/test-pdf/50',
    // Absichtlich ungeordnet: die Seite sortiert selbst
    dokumentenKette: [
        kettenDok(50, 'RECHNUNG', 'R-900', '2026-04-02', { betragNetto: 1050.42, betragBrutto: 1250 }),
        kettenDok(44, 'LIEFERSCHEIN', 'LS-2', '2026-03-27'),
        kettenDok(43, 'WERKSTOFFZEUGNIS', '4107891', null),
        kettenDok(42, 'LIEFERSCHEIN', 'LS-1', '2026-03-20'),
        kettenDok(41, 'AUFTRAGSBESTAETIGUNG', 'AB-55', '2026-03-14', { betragNetto: 1050.42, betragBrutto: 1250 }),
    ],
    dokumentenKetteVerbindungen: [
        { vonId: 42, zuId: 41 }, { vonId: 44, zuId: 41 }, { vonId: 43, zuId: 42 }, { vonId: 50, zuId: 44 },
    ],
};

// Ohne strasse/plz/ort: sonst lädt GoogleMapsEmbed eine echte Karte
const PROJEKT = {
    id: PROJEKT_ID,
    bauvorhaben: 'Geländer Musterstraße 1',
    kunde: 'Max Mustermann',
    kundenId: 3,
    kundennummer: 'K-1003',
    auftragsnummer: 'A-2026-0008',
    anlegedatum: '2026-03-01',
    bruttoPreis: 9800,
    bezahlt: false,
    abgeschlossen: false,
    kundenEmails: [],
    materialkosten: [],
    artikel: [],
    produktkategorien: [],
    zeiten: [],
    emails: [],
};

function eingangsDokument(id: number, typ: string, nummer: string, dokumentDatum: string | null, eingangsDatum: string | null) {
    return {
        id, dokumentId: id, lieferantId: 21, lieferantName: LIEFERANT, dokumentNummer: nummer, typ,
        dokumentDatum, eingangsDatum, betragNetto: null, betragBrutto: null, bezahlt: false,
        originalDateiname: `${nummer}.pdf`, pdfUrl: null, positionsTreffer: null, weitereTreffer: 0,
    };
}

/** Welche Dokumente ihre Artikelpositionen abgerufen haben. */
let positionenAnfragen: number[] = [];

async function stubApi(page: Page) {
    positionenAnfragen = [];
    await page.route('**/api/**', (route) => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad.startsWith('/api/test-pdf/')) return route.fulfill({ status: 200, contentType: 'application/pdf', body: MINI_PDF });
        if (/^\/api\/last-accessed\/PROJEKT(\/\d+)?$/.test(pfad)) {
            if (request.method() === 'POST') return route.fulfill({ status: 204, body: '' });
            return json(route, {});
        }
        if (pfad === '/api/projekte' && request.method() === 'GET') return json(route, { projekte: [], gesamt: 0 });
        if (pfad === '/api/projekte/freigabe-status') return json(route, {});
        if (pfad === `/api/projekte/${PROJEKT_ID}`) return json(route, PROJEKT);
        if (pfad === `/api/projekte/${PROJEKT_ID}/eingangsrechnungen`) return json(route, [EINGANGSRECHNUNG]);
        if (pfad === '/api/ausgangs-dokumente/freigabe-status') return json(route, {});
        if (pfad === '/api/bestellungen-uebersicht/positionen/8') {
            positionenAnfragen.push(8);
            return json(route, { positionen: [
                { id: 1, positionNr: 1, positionsArt: 'WARE', externeArtikelnummer: null, bezeichnung: 'Flachstahl', menge: 12, mengeneinheit: 'Stk',
                    einzelpreis: null, preiseinheit: null, gesamtpreisNetto: null, projektId: null, projektName: null, kostenstelleId: null,
                    kostenstelleName: null, werkstoff: 'S235JR', charge: '123456', abmessung: '50 x 5' },
                { id: 2, positionNr: 2, positionsArt: 'WARE', externeArtikelnummer: null, bezeichnung: 'Rundrohr', menge: 6, mengeneinheit: 'Stk',
                    einzelpreis: null, preiseinheit: null, gesamtpreisNetto: null, projektId: null, projektName: null, kostenstelleId: null,
                    kostenstelleName: null, werkstoff: 'S355J2H', charge: '778899', abmessung: '33,7 x 2,6' },
            ] });
        }
        if (pfad.startsWith('/api/bestellungen-uebersicht/positionen/')) return json(route, {}, 404);
        if (pfad === '/api/dokumentuebersicht/eingang') {
            return json(route, [
                eingangsDokument(7, 'RECHNUNG', 'R-900', '2026-04-02', '2026-04-03'),
                eingangsDokument(8, 'WERKSTOFFZEUGNIS', '4107891', null, '2026-03-20'),
            ]);
        }
        // Übrige Endpunkte spielen hier keine Rolle: leere Liste
        return json(route, []);
    });
}

test.describe('Eingangs-Dokumentenkette', () => {
    test('Projekt: ganze Kette als gerade Linie, Zeugnis unter seinem Lieferschein, Klick öffnet das PDF', async ({ page }, testInfo) => {
        await stubApi(page);
        await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);

        const linie = page.getByRole('list', { name: 'Dokumentenkette zu Rechnung R-900' });
        const zeilen = linie.getByRole('listitem');
        await expect(zeilen).toHaveCount(5);
        for (const [i, nummer] of ['AB-55', 'LS-1', '4107891', 'LS-2', 'R-900'].entries()) await expect(zeilen.nth(i)).toContainText(nummer);
        // Projekt rechnet netto
        await expect(zeilen.last()).toContainText('1.050,42 €');
        await expect(zeilen.nth(2).getByText('Eingang', { exact: true })).toBeVisible();

        // Eine gerade senkrechte Linie: alle Punkte mit derselben Mitte, Rechnung grün hervorgehoben
        const mitten = await linie.locator('[data-punkt]').evaluateAll(punkte =>
            punkte.map(p => { const r = p.getBoundingClientRect(); return Math.round(r.left + r.width / 2); }));
        expect(mitten).toHaveLength(5);
        expect(new Set(mitten).size).toBe(1);
        await expect(linie.locator('[data-punkt="rechnung"]')).toHaveCount(1);
        await linie.scrollIntoViewIfNeeded();
        await designPruefung(page, testInfo, 'projekt-eingangsrechnung-kette');

        await linie.getByRole('button', { name: /^Werkstoffzeugnis 4107891/ }).click();
        // Vorschau mit der Nummer als Titel
        await expect(page.getByRole('heading', { level: 3, name: '4107891' })).toBeVisible();
    });

    test('Dokumentübersicht: ohne Dokumentdatum steht das Eingangsdatum mit Hinweis', async ({ page }, testInfo) => {
        await stubApi(page);
        await page.goto('/dokumentuebersicht');
        await page.getByRole('button', { name: /^Eingang/ }).click();

        const zeugnis = page.getByRole('row').filter({ has: page.getByRole('cell', { name: '4107891', exact: true }) });
        await expect(zeugnis).toBeVisible();
        const hinweis = zeugnis.getByText('Eingang', { exact: true });
        await expect(hinweis).toBeVisible();
        await expect(zeugnis.getByTitle('Kein Dokumentdatum erkannt – Eingangsdatum')).toContainText('20.3.2026');

        const rechnung = page.getByRole('row').filter({ has: page.getByRole('cell', { name: 'R-900', exact: true }) });
        await expect(rechnung).toContainText('2.4.2026');
        await expect(rechnung.getByText('Eingang', { exact: true })).toHaveCount(0);
        await designPruefung(page, testInfo, 'dokumentuebersicht-eingangsdatum');
    });

    test('Dokumentübersicht: Artikelpositionen je Eingangsdokument aufklappen', async ({ page }, testInfo) => {
        await stubApi(page);
        await page.goto('/dokumentuebersicht');
        await page.getByRole('button', { name: /^Eingang/ }).click();
        await expect(page.getByRole('cell', { name: '4107891', exact: true })).toBeVisible();
        expect(positionenAnfragen).toEqual([]);

        const knopf = page.getByRole('button', { name: 'Positionen von Werkstoffzeugnis 4107891 anzeigen' });
        await knopf.click();
        await expect(page.getByRole('button', { name: 'Positionen von Werkstoffzeugnis 4107891 ausblenden' })).toHaveAttribute('aria-expanded', 'true');
        const tabelle = page.getByRole('table', { name: 'Artikelpositionen' });
        await expect(tabelle.getByRole('row')).toHaveCount(3);
        await expect(tabelle.getByText('Werkstoff S235JR · Charge 123456 · Abmessung 50 x 5')).toBeVisible();

        // Rechnung ohne Positionen: ehrlicher Leerzustand
        await page.getByRole('button', { name: 'Positionen von Rechnung R-900 anzeigen' }).click();
        await expect(page.getByText('Keine Artikelpositionen erkannt.')).toBeVisible();
        await designPruefung(page, testInfo, 'dokumentuebersicht-positionen');

        // Zu- und wieder aufklappen lädt nicht neu
        await page.getByRole('button', { name: 'Positionen von Werkstoffzeugnis 4107891 ausblenden' }).click();
        await expect(tabelle).toHaveCount(0);
        await knopf.click();
        await expect(tabelle).toBeVisible();
        expect(positionenAnfragen).toEqual([8]);
    });
});
