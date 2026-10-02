import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung, keinHorizontalerUeberlauf } from './hilfen/design';

/**
 * Anfragen-Übersicht: Filter für Status (offen/beendet), Herkunft
 * (Webseite/selbst angelegt) und Sortierung nach Anlegedatum.
 *
 * Gefiltert und sortiert wird im Backend vor dem Blättern. Der Stub spielt das
 * nach, damit sichtbar wird, was der Nutzer nach jedem Klick tatsächlich sieht.
 * Nur Fantasienamen (DSGVO), /api vollständig gestubbt.
 */

type AnfrageStub = {
    id: number;
    bauvorhaben: string;
    kundenName: string;
    anfragesnummer: string;
    anlegedatum: string;
    createdAt: string;
    abgeschlossen: boolean;
    ausWebseite: boolean;
};

const ANFRAGEN: AnfrageStub[] = [
    {
        id: 1, bauvorhaben: 'Geländer Musterweg 3', kundenName: 'Max Mustermann',
        anfragesnummer: 'AG-2026/09/00001', anlegedatum: '2026-09-01', createdAt: '2026-09-01T08:00:00',
        abgeschlossen: false, ausWebseite: true,
    },
    {
        id: 2, bauvorhaben: 'Balkon Beispielplatz 7', kundenName: 'Erika Musterfrau',
        anfragesnummer: 'AG-2026/09/00002', anlegedatum: '2026-09-10', createdAt: '2026-09-10T08:00:00',
        abgeschlossen: true, ausWebseite: true,
    },
    {
        id: 3, bauvorhaben: 'Vordach Musterallee 12', kundenName: 'Hans Beispiel',
        anfragesnummer: 'AG-2026/09/00003', anlegedatum: '2026-09-20', createdAt: '2026-09-20T08:00:00',
        abgeschlossen: false, ausWebseite: false,
    },
    {
        id: 4, bauvorhaben: 'Treppe Beispielstraße 5', kundenName: 'Petra Probe',
        anfragesnummer: 'AG-2026/09/00004', anlegedatum: '2026-09-25', createdAt: '2026-09-25T08:00:00',
        abgeschlossen: true, ausWebseite: false,
    },
];

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

/** Spielt Filter und Sortierung des Backends nach und schreibt die Abfragen mit. */
async function stubAnfragenApi(page: Page, abfragen: URLSearchParams[]) {
    await page.route('**/api/**', (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const pfad = url.pathname;

        if (pfad === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'anna.buero', displayName: 'Anna Büro',
                active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
            });
        }
        if (pfad === '/api/notifications/summary') {
            return json(route, { totalCount: 0, categories: [], recentItems: [] });
        }
        if (pfad === '/api/anfragen' && request.method() === 'GET') {
            const p = url.searchParams;
            abfragen.push(p);
            let treffer = [...ANFRAGEN];
            if (p.get('status') === 'offen') treffer = treffer.filter(a => !a.abgeschlossen);
            if (p.get('status') === 'beendet') treffer = treffer.filter(a => a.abgeschlossen);
            if (p.get('herkunft') === 'webseite') treffer = treffer.filter(a => a.ausWebseite);
            if (p.get('herkunft') === 'manuell') treffer = treffer.filter(a => !a.ausWebseite);
            treffer.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
            if (p.get('sortierung') !== 'alt') treffer.reverse();
            return json(route, { anfragen: treffer, gesamt: treffer.length });
        }
        if (pfad === '/api/anfragen/jahre') return json(route, [2026]);
        if (pfad === '/api/anfragen/freigabe-status') return json(route, {});
        return json(route, []);
    });
}

async function waehleFilter(page: Page, feld: string, aktuell: string, eintrag: string) {
    const feldContainer = page.getByText(feld, { exact: true }).locator('..');
    await feldContainer.getByText(aktuell, { exact: true }).click();
    await page.getByRole('listbox').getByRole('option', { name: eintrag, exact: true }).click();
}

async function kartentitel(page: Page): Promise<string[]> {
    return page.getByRole('heading', { level: 3 }).allTextContents();
}

test.describe('Anfragen-Übersicht – Filter und Sortierung', () => {
    test('zeigt standardmäßig offene Anfragen nach Anlegedatum und filtert nach Herkunft', async ({ page }, testInfo) => {
        const abfragen: URLSearchParams[] = [];
        await stubAnfragenApi(page, abfragen);
        await page.goto('/anfragen');
        await expect(page.getByRole('heading', { name: 'ANFRAGENÜBERSICHT' })).toBeVisible();

        // Standard: nur offene, neueste zuerst – keine Webseiten-Anfrage wird nach oben gezogen.
        await expect.poll(() => kartentitel(page)).toEqual(['Vordach Musterallee 12', 'Geländer Musterweg 3']);
        await expect(page.getByText('Webseite · neu')).toBeVisible();
        await keinHorizontalerUeberlauf(page);
        await designPruefung(page, testInfo, 'anfragen-filter-standard', {
            primaerAktion: page.getByRole('button', { name: 'Neue Anfrage' }),
        });

        await waehleFilter(page, 'Herkunft', 'Alle', 'Über die Webseite');
        await waehleFilter(page, 'Status', 'Nur offene', 'Offene und beendete');
        await expect.poll(() => kartentitel(page)).toEqual(['Balkon Beispielplatz 7', 'Geländer Musterweg 3']);
        // Beendete Webseiten-Anfrage: neutrale Kennzeichnung ohne "neu".
        await expect(page.getByText('Webseite', { exact: true })).toBeVisible();

        await waehleFilter(page, 'Sortierung', 'Neueste zuerst', 'Älteste zuerst');
        await expect.poll(() => kartentitel(page)).toEqual(['Geländer Musterweg 3', 'Balkon Beispielplatz 7']);

        const letzte = abfragen[abfragen.length - 1];
        expect(letzte.get('herkunft')).toBe('webseite');
        expect(letzte.has('status')).toBe(false);
        expect(letzte.get('sortierung')).toBe('alt');
        expect(letzte.get('page')).toBe('0');

        await keinHorizontalerUeberlauf(page);
        await designPruefung(page, testInfo, 'anfragen-filter-webseite-alle', {
            primaerAktion: page.getByRole('button', { name: 'Neue Anfrage' }),
        });

        await page.getByRole('button', { name: 'Filter zurücksetzen' }).click();
        await expect.poll(() => kartentitel(page)).toEqual(['Vordach Musterallee 12', 'Geländer Musterweg 3']);
    });
});
