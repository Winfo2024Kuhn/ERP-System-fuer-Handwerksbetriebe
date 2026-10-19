import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';

/**
 * PDF-Vorschau mit dem lokal ausgelieferten PDF.js: Früher kam PDF.js synchron vom CDN
 * (cdnjs) in jedem Seitenaufruf. Jetzt lädt die Vorschau Bibliothek und Worker erst beim
 * Öffnen einer PDF vom eigenen Server – und malt die Seite auf ein <canvas> (kein
 * iframe-Fallback). /api vollständig gestubbt, nur Fantasienamen (DSGVO).
 */

const LIEFERANT_ID = 7;

/** Eine leere Seite mit gültiger Querverweistabelle – PDF.js muss nichts reparieren. */
function miniPdf(): Buffer {
    const objekte = [
        '<</Type/Catalog/Pages 2 0 R>>',
        '<</Type/Pages/Kids[3 0 R]/Count 1>>',
        '<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>',
    ];
    let pdf = '%PDF-1.4\n';
    const versatz: number[] = [];
    objekte.forEach((inhalt, i) => {
        versatz.push(pdf.length);
        pdf += `${i + 1} 0 obj${inhalt}endobj\n`;
    });
    const xref = pdf.length;
    pdf += `xref\n0 ${objekte.length + 1}\n0000000000 65535 f \n`;
    pdf += versatz.map(v => `${String(v).padStart(10, '0')} 00000 n \n`).join('');
    pdf += `trailer<</Size ${objekte.length + 1}/Root 1 0 R>>\nstartxref\n${xref}\n%%EOF`;
    return Buffer.from(pdf);
}

function dokument(id: number, typ: string, nummer: string, verknuepft: { id: number; typ: string }[] = []) {
    return {
        id, typ, originalDateiname: `${nummer}.pdf`, uploadDatum: '2026-09-20T10:00:00',
        geschaeftsdaten: { dokumentNummer: nummer, dokumentDatum: '2026-09-20' },
        projektAnteile: [], verknuepfteDokumente: verknuepft,
    };
}

const DOKUMENTE = [
    dokument(1, 'AUFTRAGSBESTAETIGUNG', 'AB-5001', [{ id: 2, typ: 'LIEFERSCHEIN' }]),
    dokument(2, 'LIEFERSCHEIN', 'LS-4711', [{ id: 1, typ: 'AUFTRAGSBESTAETIGUNG' }]),
    dokument(3, 'WERKSTOFFZEUGNIS', 'WZ-31'),
];

function json(route: Route, body: unknown) {
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubApi(page: Page) {
    await page.route('**/api/**', route => {
        const pfad = new URL(route.request().url()).pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'max.mustermann', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (/\/dokumente\/\d+\/download$/.test(pfad)) return route.fulfill({ status: 200, contentType: 'application/pdf', body: miniPdf() });
        if (pfad === `/api/lieferanten/${LIEFERANT_ID}`) {
            return json(route, {
                id: LIEFERANT_ID, lieferantenname: 'Musterstahl GmbH', lieferantenTyp: 'Lieferant', rollen: [],
                strasse: 'Musterweg 1', plz: '12345', ort: 'Musterstadt', emails: [], kommunikation: [], notizen: [], kundenEmails: [],
                dokumente: DOKUMENTE,
            });
        }
        if (pfad === '/api/lieferanten') return json(route, { lieferanten: [], gesamt: 0 });
        if (pfad === '/api/bestellungen-uebersicht/ketten-vorschlaege') {
            return json(route, [{
                dokument: { id: 2, typ: 'LIEFERSCHEIN', dokumentNummer: 'LS-4711', dokumentDatum: '2026-09-20', betragBrutto: null, betragNetto: null, liefertermin: null, dateiname: 'LS-4711.pdf', pdfUrl: `/api/lieferanten/${LIEFERANT_ID}/dokumente/2/download` },
                lieferantName: 'Musterstahl GmbH',
                kettenDokumentId: 3, kettenDokumentTyp: 'WERKSTOFFZEUGNIS', kettenDokumentNummer: 'WZ-31',
                trefferquote: 88, sicher: true, eindeutig: true, gruende: ['Belegnummer wird genannt'], gehoertSchonZu: null,
            }]);
        }
        return json(route, []);
    });
}

test.describe('PDF-Vorschau mit lokalem PDF.js', () => {
    test('lädt PDF.js erst beim Öffnen einer PDF vom eigenen Server und malt die Seite', async ({ page }) => {
        const geladen: string[] = [];
        page.on('request', anfrage => geladen.push(new URL(anfrage.url()).pathname));
        await stubApi(page);

        await page.goto(`/lieferanten?lieferantId=${LIEFERANT_ID}&tab=dokumente`);
        await expect(page.getByText(/Dokumentenketten \(1\)/)).toBeVisible();
        // Ohne geöffnete PDF kein PDF.js – das hielt früher jeden Seitenaufruf auf.
        expect(geladen.filter(pfad => /pdf\.worker|\/assets\/pdf-/.test(pfad))).toEqual([]);

        await page.getByRole('button', { name: 'Werkstoffzeugnis WZ-31 zu Kette zuordnen' }).click();
        const dialog = page.getByRole('dialog', { name: /Passende Bestellung suchen – Musterstahl GmbH/ });
        await expect(dialog).toBeVisible();

        const seite = dialog.locator('canvas').first();
        await expect(seite).toBeVisible();
        await expect.poll(() => seite.evaluate((c: HTMLCanvasElement) => c.width)).toBeGreaterThan(0);
        await expect(dialog.getByTitle('PDF Vorschau')).toHaveCount(0);
        await expect(dialog.getByTestId('pdf-fallback-error')).toHaveCount(0);

        // Bibliothek und Worker kamen vom eigenen Server, nicht vom CDN.
        expect(geladen.some(pfad => /^\/assets\/pdf\.worker\.min-[\w-]+\.js$/.test(pfad))).toBe(true);
        expect(geladen.some(pfad => pfad.includes('cdnjs'))).toBe(false);
    });
});
