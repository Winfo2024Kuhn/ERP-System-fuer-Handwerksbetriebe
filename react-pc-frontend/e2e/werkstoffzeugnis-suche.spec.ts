import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Werkstoffzeugnis als Lieferanten-Dokumenttyp und die Suche nach Positionen
 * („Flachstahl“ findet Zeugnis, Lieferschein, Rechnung):
 *  - Lieferant → Reiter „Dokumente“: Typ-Filter, Browser-Suche sofort,
 *    Positionssuche verzögert, Trefferzeile, Zeugnis-Detail mit Positionen.
 *  - Projektmanagement → Dokumente → Eingang: Filter und Trefferzeile.
 * /api ist vollständig gestubbt, nur Fantasienamen (DSGVO).
 */

const LIEFERANT_ID = 7;
const LS_ID = 41;
const ZEUGNIS_ID = 42;
const RE_ID = 43;
const ZEUGNIS_ALLEIN_ID = 44;

const TREFFER_TEXT = 'Flachstahl 50x5 · S235JR · Charge 123456 · 12 Stück';

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const MINI_PDF = Buffer.from(
    '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
    '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Size 4/Root 1 0 R>>\n%%EOF',
);

function dokument(id: number, typ: string, nummer: string, datum: string, geschaeftsdaten: Record<string, unknown> = {}) {
    return {
        id, typ, originalDateiname: `${nummer}.pdf`, uploadDatum: `${datum}T10:00:00`,
        geschaeftsdaten: { dokumentNummer: nummer, dokumentDatum: datum, ...geschaeftsdaten },
        projektAnteile: [], verknuepfteDokumente: [] as { id: number; typ: string; dokumentNummer: string }[],
    };
}

// Lieferschein und Zeugnis bilden eine Kette, Rechnung und zweites Zeugnis stehen einzeln
const LIEFERSCHEIN = dokument(LS_ID, 'LIEFERSCHEIN', 'LS-2026-501', '2026-09-10');
LIEFERSCHEIN.verknuepfteDokumente = [{ id: ZEUGNIS_ID, typ: 'WERKSTOFFZEUGNIS', dokumentNummer: 'WZ-2026-77' }];
const ZEUGNIS_IN_KETTE = dokument(ZEUGNIS_ID, 'WERKSTOFFZEUGNIS', 'WZ-2026-77', '2026-09-11', { referenzNummer: 'LS-2026-501' });
ZEUGNIS_IN_KETTE.verknuepfteDokumente = [{ id: LS_ID, typ: 'LIEFERSCHEIN', dokumentNummer: 'LS-2026-501' }];

const DOKUMENTE = [
    LIEFERSCHEIN,
    ZEUGNIS_IN_KETTE,
    dokument(RE_ID, 'RECHNUNG', 'RE-2026-900', '2026-09-20', { betragNetto: 100, betragBrutto: 119 }),
    dokument(ZEUGNIS_ALLEIN_ID, 'WERKSTOFFZEUGNIS', 'WZ-2026-80', '2026-09-25'),
];

const LIEFERANT = {
    id: LIEFERANT_ID, lieferantenname: 'Musterstahl GmbH', lieferantenTyp: 'Lieferant', rollen: [],
    strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt', emails: [], kommunikation: [], notizen: [], kundenEmails: [],
    dokumente: DOKUMENTE,
};

function zeugnisPosition(id: number, bezeichnung: string, werkstoff: string, charge: string, abmessung: string, menge: number) {
    return {
        id, positionNr: id, positionsArt: 'WARE', externeArtikelnummer: null, bezeichnung, menge, mengeneinheit: 'Stück',
        einzelpreis: null, preiseinheit: null, gesamtpreisNetto: null, projektId: null, projektName: null,
        kostenstelleId: null, kostenstelleName: null, werkstoff, charge, abmessung,
    };
}

function lockDto() {
    const jetzt = new Date().toISOString();
    return { status: 'ACQUIRED', holderUserId: 1, holderDisplayName: 'Max Mustermann', acquiredAt: jetzt, lastHeartbeatAt: jetzt };
}

interface Mitschrift {
    positionssuche: string[];
    eingang: URLSearchParams[];
}

async function stubApi(page: Page): Promise<Mitschrift> {
    const mitschrift: Mitschrift = { positionssuche: [], eingang: [] };
    await page.route('**/api/**', async route => {
        const request = route.request();
        const url = new URL(request.url());
        const pfad = url.pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'max.mustermann', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === `/api/lieferanten/${LIEFERANT_ID}`) return json(route, LIEFERANT);
        if (pfad === `/api/lieferanten/${LIEFERANT_ID}/dokumente/positionssuche`) {
            const q = url.searchParams.get('q') ?? '';
            mitschrift.positionssuche.push(q);
            // Positionen kennt nur das Backend: „flachstahl“ steht in keiner Nummer
            if (q.toLowerCase().includes('flachstahl')) {
                return json(route, [
                    { dokumentId: ZEUGNIS_ALLEIN_ID, trefferText: TREFFER_TEXT, weitereTreffer: 2 },
                    { dokumentId: RE_ID, trefferText: 'Flachstahl 50x5 · 12 Stück', weitereTreffer: 0 },
                ]);
            }
            return json(route, []);
        }
        if (/\/dokumente\/\d+\/download$/.test(pfad)) {
            return route.fulfill({ status: 200, contentType: 'application/pdf', body: MINI_PDF });
        }
        if (pfad === `/api/bestellungen-uebersicht/positionen/${ZEUGNIS_ALLEIN_ID}`) {
            return json(route, {
                geschaeftsdokumentId: ZEUGNIS_ALLEIN_ID, dokumentTyp: 'WERKSTOFFZEUGNIS', auslesbar: true,
                betragNetto: null, betragBrutto: null, summePositionen: null, abweichung: null, abweichungAuffaellig: false,
                nachPositionenAufgeteilt: false,
                positionen: [
                    zeugnisPosition(1, 'Flachstahl 50x5', 'S235JR', '123456', '50 x 5', 12),
                    zeugnisPosition(2, 'Rundrohr 33,7x2,6', 'S355J2H', '778899', '33,7 x 2,6', 6),
                ],
            });
        }
        if (pfad.startsWith('/api/datensatz-locks/')) {
            if (request.method() === 'DELETE') return route.fulfill({ status: 204, body: '' });
            return json(route, lockDto());
        }
        if (pfad === '/api/dokumentuebersicht/eingang') {
            mitschrift.eingang.push(url.searchParams);
            const suche = (url.searchParams.get('search') ?? '').toLowerCase();
            const typ = url.searchParams.get('typ');
            const alle = [
                {
                    id: 1, dokumentId: ZEUGNIS_ALLEIN_ID, lieferantId: LIEFERANT_ID, lieferantName: 'Musterstahl GmbH',
                    dokumentNummer: 'WZ-2026-80', typ: 'WERKSTOFFZEUGNIS', dokumentDatum: '2026-09-25', betragNetto: null, betragBrutto: null,
                    bezahlt: false, originalDateiname: 'WZ-2026-80.pdf', pdfUrl: null,
                    positionsTreffer: suche.includes('flachstahl') ? TREFFER_TEXT : null, weitereTreffer: suche.includes('flachstahl') ? 2 : 0,
                },
                {
                    id: 2, dokumentId: RE_ID, lieferantId: LIEFERANT_ID, lieferantName: 'Musterstahl GmbH',
                    dokumentNummer: 'RE-2026-900', typ: 'RECHNUNG', dokumentDatum: '2026-09-20', betragNetto: 100, betragBrutto: 119,
                    bezahlt: false, originalDateiname: 'RE-2026-900.pdf', pdfUrl: null,
                    positionsTreffer: suche.includes('flachstahl') ? 'Flachstahl 50x5 · 12 Stück' : null, weitereTreffer: 0,
                },
            ];
            return json(route, typ ? alle.filter(d => d.typ === typ) : alle);
        }
        if (pfad === '/api/dokumentuebersicht/ausgang') return json(route, []);
        return json(route, []);
    });
    return mitschrift;
}

function karten(page: Page) {
    return page.getByRole('heading', { level: 4 });
}

test.describe('Werkstoffzeugnis – Lieferanten-Dokumente', () => {
    test('Typ-Filter zeigt nur Werkstoffzeugnisse', async ({ page }, testInfo) => {
        await stubApi(page);
        await page.goto(`/lieferanten?lieferantId=${LIEFERANT_ID}&tab=dokumente`);
        await expect(page.getByRole('heading', { name: 'WZ-2026-80' })).toBeVisible();
        await expect(page.getByText('Kette: LS-2026-501')).toBeVisible();

        await page.getByRole('combobox', { name: 'Dokumenttyp' }).click();
        await page.getByRole('option', { name: 'Werkstoffzeugnisse' }).click();

        // Beide Zeugnisse, das aus der Kette steht ohne seinen Lieferschein einzeln da
        await expect(karten(page)).toHaveText(['WZ-2026-80', 'WZ-2026-77']);
        await expect(page.getByText('Kette: LS-2026-501')).toHaveCount(0);
        await designPruefung(page, testInfo, 'werkstoffzeugnis-filter');
    });

    test('Suche: Browser-Treffer sofort, Positionstreffer nach kurzer Pause mit Trefferzeile', async ({ page }, testInfo) => {
        const mitschrift = await stubApi(page);
        await page.goto(`/lieferanten?lieferantId=${LIEFERANT_ID}&tab=dokumente`);
        const suche = page.getByRole('textbox', { name: 'Dokumente durchsuchen' });
        await expect(suche).toHaveAttribute('placeholder', 'Nummer, Material, Charge, Kommission …');

        // Nummer: findet der Browser sofort
        await suche.fill('RE-2026');
        await expect(karten(page)).toHaveText(['RE-2026-900']);

        // Material: steht nur in den Positionen – kommt verzögert vom Server
        await suche.fill('Flachstahl');
        await expect(page.getByRole('heading', { name: 'WZ-2026-80' })).toBeVisible();
        await expect(karten(page)).toHaveText(['WZ-2026-80', 'RE-2026-900']);
        expect(mitschrift.positionssuche.at(-1)).toBe('Flachstahl');
        // Nicht für jeden Tastendruck eine Anfrage (fill tippt in einem Rutsch)
        expect(mitschrift.positionssuche.filter(q => q.startsWith('Flach')).length).toBe(1);

        const zeile = page.getByTestId('positions-treffer').filter({ hasText: 'S235JR' });
        await expect(zeile).toContainText(TREFFER_TEXT);
        await expect(zeile).toContainText('und 2 weitere');
        await expect(zeile.locator('mark')).toHaveText('Flachstahl');
        // Die Inhaltsfläche scrollt selbst – Trefferzeile in den Blick holen
        await zeile.scrollIntoViewIfNeeded();
        await expect(zeile).toBeInViewport();
        await designPruefung(page, testInfo, 'werkstoffzeugnis-positionssuche');
    });

    test('Positionssuche ohne Anmeldung (401): einmal ein Hinweis, Browser-Treffer bleiben', async ({ page }) => {
        await stubApi(page);
        // Später registrierte Route gewinnt: die Positionssuche antwortet mit 401
        await page.route(`**/api/lieferanten/${LIEFERANT_ID}/dokumente/positionssuche**`, route => route.fulfill({ status: 401, body: '' }));
        await page.goto(`/lieferanten?lieferantId=${LIEFERANT_ID}&tab=dokumente`);
        const suche = page.getByRole('textbox', { name: 'Dokumente durchsuchen' });

        await suche.fill('RE-2026');
        const hinweis = page.getByText('Suche in Positionen gerade nicht möglich – Treffer nur nach Nummer und Betrag.');
        await expect(hinweis).toBeVisible();
        await expect(karten(page)).toHaveText(['RE-2026-900']);

        // Zweiter Fehler: kein zweiter Hinweis
        const zweiteAntwort = page.waitForResponse(res => res.url().includes('/positionssuche?q=RE-2026-9'));
        await suche.fill('RE-2026-9');
        await zweiteAntwort;
        await expect(hinweis).toHaveCount(1);
    });

    test('Zeugnis öffnen zeigt Erzeugnisse mit Werkstoff, Charge und Abmessung', async ({ page }, testInfo) => {
        await stubApi(page);
        await page.goto(`/lieferanten?lieferantId=${LIEFERANT_ID}&tab=dokumente`);
        await page.getByRole('button', { name: /WZ-2026-80/ }).click();
        const dialog = page.getByRole('dialog', { name: 'Dokument bearbeiten' });
        await expect(dialog).toBeVisible();

        const tabelle = dialog.getByRole('table');
        await expect(tabelle.getByRole('columnheader')).toHaveText(['Erzeugnis', 'Werkstoff', 'Charge', 'Abmessung', 'Menge']);
        await expect(tabelle.getByRole('row', { name: /Flachstahl 50x5/ })).toContainText('S235JR');
        await expect(tabelle.getByRole('row', { name: /Flachstahl 50x5/ })).toContainText('123456');
        await expect(tabelle.getByRole('row', { name: /Rundrohr/ })).toContainText('6 Stück');
        await expect(dialog.getByText('Beträge')).toHaveCount(0);
        await designPruefung(page, testInfo, 'werkstoffzeugnis-detail', { primaerAktion: dialog.getByRole('button', { name: 'Speichern' }) });
    });
});

test.describe('Werkstoffzeugnis – Dokumentübersicht Eingang', () => {
    test('Filter Werkstoffzeugnis und Trefferzeile unter dem Eingang', async ({ page }, testInfo) => {
        const mitschrift = await stubApi(page);
        await page.goto('/dokumentuebersicht');
        await page.getByRole('button', { name: /^Eingang/ }).click();
        await expect(page.getByRole('cell', { name: 'WZ-2026-80' })).toBeVisible();
        await expect(page.getByRole('cell', { name: 'Werkstoffzeugnis' })).toBeVisible();

        await page.getByPlaceholder(/Volltext/).fill('Flachstahl');
        const zeilen = page.getByTestId('positions-treffer');
        await expect(zeilen).toHaveCount(2);
        await expect(zeilen.first()).toContainText(TREFFER_TEXT);
        await expect(zeilen.first()).toContainText('und 2 weitere');
        await expect(zeilen.first().locator('mark')).toHaveText('Flachstahl');
        expect(mitschrift.eingang.at(-1)?.get('search')).toBe('Flachstahl');
        await designPruefung(page, testInfo, 'werkstoffzeugnis-dokumentuebersicht-suche');
    });

    test('Arten-Filter kennt Werkstoffzeugnis, „Sonstiges“ als SONSTIG und keine „Bestellung“', async ({ page }) => {
        const mitschrift = await stubApi(page);
        await page.goto('/dokumentuebersicht');
        await page.getByRole('button', { name: /^Eingang/ }).click();
        await expect(page.getByRole('cell', { name: 'WZ-2026-80' })).toBeVisible();

        const artFilter = page.getByRole('combobox').filter({ hasText: 'Alle Arten' });
        await artFilter.click();
        const liste = page.getByRole('listbox');
        await expect(liste.getByRole('option', { name: 'Werkstoffzeugnis' })).toBeVisible();
        await expect(liste.getByRole('option', { name: 'Bestellung' })).toHaveCount(0);
        await liste.getByRole('option', { name: 'Sonstiges' }).click();
        await expect.poll(() => mitschrift.eingang.at(-1)?.get('typ')).toBe('SONSTIG');

        await page.getByRole('combobox').filter({ hasText: 'Sonstiges' }).click();
        await page.getByRole('listbox').getByRole('option', { name: 'Werkstoffzeugnis' }).click();
        await expect.poll(() => mitschrift.eingang.at(-1)?.get('typ')).toBe('WERKSTOFFZEUGNIS');
        await expect(page.getByRole('cell', { name: 'RE-2026-900' })).toHaveCount(0);
        await expect(page.getByRole('cell', { name: 'WZ-2026-80' })).toBeVisible();
    });
});
