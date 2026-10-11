import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Projekt-Dateien: Hochladen in "Diverse Dokumente" geht direkt, wie in jeder
 * anderen Gruppe. Früher kam hier ein Zwischendialog "Lieferant zuweisen". Er
 * stürzte ab (GET /api/lieferanten liefert eine Seite, kein Array) und die
 * Zuordnung hatte ohnehin keine sichtbare Wirkung -- deshalb ist er entfallen.
 *
 * Nur Dummy-Daten (DSGVO).
 */

const PROJEKT_ID = 5;

const DUMMY_PROJEKT = {
    id: PROJEKT_ID,
    bauvorhaben: 'Treppengeländer Musterstraße',
    kunde: 'Max Mustermann',
    kundenId: 3,
    kundennummer: 'K-1003',
    auftragsnummer: 'A-2026-0005',
    anlegedatum: '2026-01-15',
    bruttoPreis: 12500,
    bezahlt: false,
    abgeschlossen: false,
    kundenEmails: [],
    materialkosten: [],
    artikel: [],
    produktkategorien: [],
    zeiten: [],
    emails: [],
};

const HOCHGELADEN = {
    id: 41,
    originalDateiname: 'lieferschein-muster.pdf',
    dateityp: 'application/pdf',
    url: '/api/dokumente/41',
    dokumentGruppe: 'DIVERSE_DOKUMENTE',
    uploadDatum: '2026-10-11',
};

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

interface Mitschrift {
    /** Wert des Headers X-Lieferant-Id je Upload (null = ohne). */
    lieferantIds: (string | null)[];
    gruppen: (string | null)[];
    lieferantenAbrufe: number;
}

async function stubbeProjektApi(page: Page): Promise<Mitschrift> {
    const mitschrift: Mitschrift = { lieferantIds: [], gruppen: [], lieferantenAbrufe: 0 };
    const gespeichert: typeof HOCHGELADEN[] = [];
    await page.route('**/api/**', (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const pfad = url.pathname;
        const methode = request.method();

        if (pfad === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'max.mustermann', displayName: 'Max Mustermann',
                active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
            });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (/^\/api\/last-accessed\/PROJEKT(\/\d+)?$/.test(pfad)) {
            if (methode === 'POST') return route.fulfill({ status: 204, body: '' });
            return json(route, {});
        }
        if (pfad === '/api/projekte' && methode === 'GET') return json(route, { projekte: [], gesamt: 0 });
        if (pfad === '/api/projekte/freigabe-status') return json(route, {});
        if (pfad === `/api/projekte/${PROJEKT_ID}`) return json(route, DUMMY_PROJEKT);
        if (pfad === `/api/projekte/${PROJEKT_ID}/dokumente`) {
            if (methode === 'POST') {
                mitschrift.lieferantIds.push(request.headers()['x-lieferant-id'] ?? null);
                mitschrift.gruppen.push(url.searchParams.get('gruppe'));
                gespeichert.push(HOCHGELADEN);
                return json(route, [HOCHGELADEN]);
            }
            return json(route, gespeichert);
        }
        if (pfad === '/api/ausgangs-dokumente/freigabe-status') return json(route, {});
        if (pfad.startsWith('/api/lieferanten')) {
            mitschrift.lieferantenAbrufe++;
            return json(route, { lieferanten: [], gesamt: 0, seite: 0, seitenGroesse: 50 });
        }
        return json(route, []);
    });
    return mitschrift;
}

test.describe('Projekt-Dateien: Hochladen in "Diverse Dokumente"', () => {
    test('lädt direkt hoch, ohne Zwischendialog und ohne Lieferantenliste', async ({ page }, testInfo) => {
        const mitschrift = await stubbeProjektApi(page);

        await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=dokumente`);
        const gruppe = page.getByRole('button', { name: /Diverse Dokumente/ });
        await gruppe.click();
        await page.locator('input[type=file]').last().setInputFiles({
            name: 'lieferschein-muster.pdf',
            mimeType: 'application/pdf',
            buffer: Buffer.from('%PDF-1.4 dummy'),
        });

        // Die Datei landet sofort in ihrer Gruppe -- kein Dialog dazwischen.
        await expect(page.getByRole('button', { name: /Diverse Dokumente\s*\(1\)/ })).toBeVisible();
        await expect(page.getByText('lieferschein-muster.pdf')).toBeVisible();
        await expect(page.getByRole('dialog')).toHaveCount(0);
        await expect(page.getByText('Lieferant zuweisen')).toHaveCount(0);

        expect(mitschrift.gruppen).toEqual(['DIVERSE_DOKUMENTE']);
        expect(mitschrift.lieferantIds).toEqual([null]);
        expect(mitschrift.lieferantenAbrufe).toBe(0);

        // "Diverse Dokumente" ist die unterste Gruppe: ihr Inhalt klappt unter den
        // Bildrand auf. Sichtbar ohne Scrollen muss das Ergebnis sein (Zähler "(1)").
        const gruppeMitDatei = page.getByRole('button', { name: /Diverse Dokumente\s*\(1\)/ });
        await designPruefung(page, testInfo, 'dokument-upload-diverse-direkt', { primaerAktion: gruppeMitDatei });

        const weitere = page.getByText('Weitere Dateien hinzufügen');
        await weitere.scrollIntoViewIfNeeded();
        await designPruefung(page, testInfo, 'dokument-upload-diverse-karte', { primaerAktion: weitere });
    });
});
