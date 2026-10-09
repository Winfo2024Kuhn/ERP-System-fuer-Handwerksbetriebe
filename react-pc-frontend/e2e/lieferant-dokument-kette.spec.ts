import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Lieferant → Reiter „Dokumente“: Ein Werkstoffzeugnis, das nicht automatisch
 * an seinen Lieferschein gehängt wurde, lässt sich über „Zu Kette zuordnen“
 * nachträglich zuordnen; an jeder Kette gibt es „Dokument hinzufügen“. Die
 * Belege einer Kette stehen als gerade Linie untereinander (wie git-Commits).
 * /api vollständig gestubbt, nur Fantasienamen (DSGVO).
 */

const LIEFERANT_ID = 7;

function dokument(id: number, typ: string, nummer: string, verknuepft: { id: number; typ: string }[] = [], dokumentDatum: string | null = '2026-09-20', betragBrutto?: number) {
    return {
        id, typ, originalDateiname: `${nummer}.pdf`, uploadDatum: '2026-09-20T10:00:00',
        geschaeftsdaten: { dokumentNummer: nummer, dokumentDatum: dokumentDatum ?? undefined, betragBrutto },
        projektAnteile: [], verknuepfteDokumente: verknuepft,
    };
}

const AB = dokument(1, 'AUFTRAGSBESTAETIGUNG', 'AB-5001', [{ id: 2, typ: 'LIEFERSCHEIN' }]);
const LIEFERSCHEIN = dokument(2, 'LIEFERSCHEIN', 'LS-4711', [{ id: 1, typ: 'AUFTRAGSBESTAETIGUNG' }]);
const ZEUGNIS = dokument(3, 'WERKSTOFFZEUGNIS', 'WZ-31');
const SONSTIG = dokument(4, 'SONSTIG', 'SO-1');

const LIEFERANT = {
    id: LIEFERANT_ID, lieferantenname: 'Musterstahl GmbH', lieferantenTyp: 'Lieferant', rollen: [],
    strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt', emails: [], kommunikation: [], notizen: [], kundenEmails: [],
    dokumente: [AB, LIEFERSCHEIN, ZEUGNIS, SONSTIG],
};

const MINI_PDF = Buffer.from(
    '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
    '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Size 4/Root 1 0 R>>\n%%EOF',
);

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

interface Mitschrift { vorschlagAnfragen: URLSearchParams[]; posts: unknown[]; neuGeladen: number }

async function stubApi(page: Page, mitschrift: Mitschrift, dokumente: unknown[] = LIEFERANT.dokumente) {
    await page.route('**/api/**', route => {
        const request = route.request();
        const url = new URL(request.url());
        const pfad = url.pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (/\/dokumente\/\d+\/download$/.test(pfad)) return route.fulfill({ status: 200, contentType: 'application/pdf', body: MINI_PDF });
        if (pfad === `/api/lieferanten/${LIEFERANT_ID}/dokumente`) {
            mitschrift.neuGeladen++;
            return json(route, dokumente);
        }
        if (pfad === `/api/lieferanten/${LIEFERANT_ID}`) return json(route, { ...LIEFERANT, dokumente });
        if (pfad === '/api/lieferanten') return json(route, { lieferanten: [], gesamt: 0 });
        if (pfad === '/api/bestellungen-uebersicht/ketten-vorschlaege') {
            mitschrift.vorschlagAnfragen.push(url.searchParams);
            const zuZeugnis = url.searchParams.getAll('dokumentIds').includes('3');
            return json(route, [{
                dokument: zuZeugnis
                    ? { id: 2, typ: 'LIEFERSCHEIN', dokumentNummer: 'LS-4711', dokumentDatum: '2026-09-20', betragBrutto: null, betragNetto: null, liefertermin: null, dateiname: 'LS-4711.pdf', pdfUrl: `/api/lieferanten/${LIEFERANT_ID}/dokumente/2/download` }
                    : { id: 3, typ: 'WERKSTOFFZEUGNIS', dokumentNummer: 'WZ-31', dokumentDatum: '2026-09-20', betragBrutto: null, betragNetto: null, liefertermin: null, dateiname: 'WZ-31.pdf', pdfUrl: `/api/lieferanten/${LIEFERANT_ID}/dokumente/3/download` },
                lieferantName: 'Musterstahl GmbH',
                kettenDokumentId: zuZeugnis ? 3 : 2,
                kettenDokumentTyp: zuZeugnis ? 'WERKSTOFFZEUGNIS' : 'LIEFERSCHEIN',
                kettenDokumentNummer: zuZeugnis ? 'WZ-31' : 'LS-4711',
                trefferquote: 88, sicher: true, eindeutig: true, gruende: ['Belegnummer wird genannt', 'Gleicher Lieferant'], gehoertSchonZu: null,
            }]);
        }
        if (pfad === '/api/bestellungen-uebersicht/ketten-verknuepfen') {
            mitschrift.posts.push(request.postDataJSON());
            return json(route, { success: true });
        }
        // Übrige Reiter (E-Mails, Notizen, Kennzahlen …) spielen hier keine Rolle: leer
        return json(route, []);
    });
}

async function oeffne(page: Page) {
    await page.goto(`/lieferanten?lieferantId=${LIEFERANT_ID}&tab=dokumente`);
    await expect(page.getByText(/Dokumentenketten \(1\)/)).toBeVisible();
}

test.describe('Lieferant – Dokument einer Kette zuordnen', () => {
    test('Werkstoffzeugnis per „Zu Kette zuordnen“ an seinen Lieferschein hängen', async ({ page }, testInfo) => {
        const mitschrift: Mitschrift = { vorschlagAnfragen: [], posts: [], neuGeladen: 0 };
        await stubApi(page, mitschrift);
        await oeffne(page);

        // Sonstiges gehört in keine Kette – dort kein Knopf
        await expect(page.getByRole('button', { name: /SO-1 zu Kette zuordnen/ })).toHaveCount(0);
        const zuordnen = page.getByRole('button', { name: 'Werkstoffzeugnis WZ-31 zu Kette zuordnen' });
        await zuordnen.scrollIntoViewIfNeeded();
        await designPruefung(page, testInfo, 'lieferant-dokumente-zu-kette');

        await zuordnen.click();
        const dialog = page.getByRole('dialog', { name: /Passende Bestellung suchen – Musterstahl GmbH/ });
        await expect(dialog).toBeVisible();
        await expect(dialog.getByRole('region', { name: 'Dieses Dokument' })).toBeVisible();
        await expect(dialog.getByText('Belegnummer wird genannt')).toBeVisible();
        await expect(dialog.getByText('88 %').first()).toBeVisible();
        expect(mitschrift.vorschlagAnfragen[0].getAll('dokumentIds')).toEqual(['3']);
        await designPruefung(page, testInfo, 'lieferant-dokumente-zu-kette-dialog', { primaerAktion: dialog.getByRole('button', { name: 'Gehört dazu', exact: true }) });

        const vorher = mitschrift.neuGeladen;
        await dialog.getByRole('button', { name: 'Lieferschein LS-4711 gehört dazu' }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
        expect(mitschrift.posts[0]).toEqual({ kettenDokumentId: 3, dokumentId: 2 });
        await expect(dialog).toHaveCount(0);
        await expect.poll(() => mitschrift.neuGeladen).toBeGreaterThan(vorher);
    });

    test('„Dokument hinzufügen“ an der Kette sucht mit allen Kettendokumenten', async ({ page }) => {
        const mitschrift: Mitschrift = { vorschlagAnfragen: [], posts: [], neuGeladen: 0 };
        await stubApi(page, mitschrift);
        await oeffne(page);

        await page.getByRole('button', { name: /^Dokument zur Kette .*hinzufügen$/ }).click();
        const dialog = page.getByRole('dialog', { name: /Dokument zur Kette hinzufügen/ });
        await expect(dialog.getByText('passt zu: Lieferschein LS-4711')).toBeVisible();
        expect(mitschrift.vorschlagAnfragen[0].getAll('dokumentIds').sort()).toEqual(['1', '2']);
        await dialog.getByRole('button', { name: 'Gehört dazu', exact: true }).click();
        await expect.poll(() => mitschrift.posts.length).toBe(1);
        expect(mitschrift.posts[0]).toEqual({ kettenDokumentId: 2, dokumentId: 3 });
    });

    test('Kette als gerade Linie: Zeugnis unter seinem Lieferschein, Klick öffnet die Details', async ({ page }, testInfo) => {
        // Stahlhandel-Beispiel: AB, zwei Lieferscheine, Zeugnis (ohne Datum) zum ersten Lieferschein, Rechnung
        const kette = [
            dokument(30, 'RECHNUNG', 'R-900', [{ id: 22, typ: 'LIEFERSCHEIN' }], '2026-04-02', 1250),
            dokument(23, 'WERKSTOFFZEUGNIS', '4107891', [{ id: 21, typ: 'LIEFERSCHEIN' }], null),
            dokument(22, 'LIEFERSCHEIN', 'LS-2', [{ id: 20, typ: 'AUFTRAGSBESTAETIGUNG' }], '2026-03-27'),
            dokument(21, 'LIEFERSCHEIN', 'LS-1', [{ id: 20, typ: 'AUFTRAGSBESTAETIGUNG' }], '2026-03-20'),
            dokument(20, 'AUFTRAGSBESTAETIGUNG', 'AB-55', [], '2026-03-14', 1250),
        ];
        await stubApi(page, { vorschlagAnfragen: [], posts: [], neuGeladen: 0 }, kette);
        await oeffne(page);

        const linie = page.getByRole('list', { name: 'Belege der Kette R-900' });
        const zeilen = linie.getByRole('listitem');
        await expect(zeilen).toHaveCount(5);
        for (const [i, nummer] of ['AB-55', 'LS-1', '4107891', 'LS-2', 'R-900'].entries()) await expect(zeilen.nth(i)).toContainText(nummer);
        await expect(zeilen.nth(2).getByText('Eingang', { exact: true })).toBeVisible();
        // Alle Punkte auf derselben senkrechten Linie
        const mitten = await linie.locator('[data-punkt]').evaluateAll(punkte =>
            punkte.map(p => { const r = p.getBoundingClientRect(); return Math.round(r.left + r.width / 2); }));
        expect(mitten).toHaveLength(5);
        expect(new Set(mitten).size).toBe(1);
        await linie.scrollIntoViewIfNeeded();
        await designPruefung(page, testInfo, 'lieferant-dokumente-kette-linie');

        await linie.getByRole('button', { name: /^Lieferschein LS-2/ }).click();
        await expect(page.getByRole('dialog', { name: 'Dokument bearbeiten' })).toBeVisible();
    });
});
