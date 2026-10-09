import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';

// Browser-Vertrag für bereits serverseitig gefilterte Antworten. Die tatsächliche
// Rechteentscheidung wird mit echter SecurityConfig in MockMvc geprüft.
const rechnung = {
    id: 41, lieferantId: 7, lieferantName: 'Muster Lieferant GmbH', typ: 'RECHNUNG',
    originalDateiname: 'muster-rechnung.pdf', uploadDatum: '2026-01-05T10:00:00',
    geschaeftsdaten: { dokumentNummer: 'RE-MUSTER-41', dokumentDatum: '2026-01-05', betragBrutto: 119 },
    projektAnteile: [], verknuepfteDokumente: [],
};
const angebot = {
    ...rechnung, id: 42, typ: 'ANGEBOT', originalDateiname: 'muster-angebot.pdf',
    geschaeftsdaten: { ...rechnung.geschaeftsdaten, dokumentNummer: 'AN-MUSTER-42' },
};

const szenarien = [
    { name: 'Admin ohne Mitarbeiter-Zuordnung', admin: true, dokumente: [rechnung, angebot], anzahl: 2 },
    { name: 'Mitarbeiter mit Rechnungsrecht', admin: false, dokumente: [rechnung], anzahl: 1 },
    { name: 'Benutzer ohne Mitarbeiter-Zuordnung', admin: false, dokumente: [], anzahl: 0 },
];

function json(route: Route, body: unknown) {
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubbeAntworten(page: Page, szenario: typeof szenarien[number]) {
    await page.route('**/api/**', route => {
        const pfad = new URL(route.request().url()).pathname;
        if (pfad === '/api/auth/me') return json(route, {
            id: 1, username: 'max.mustermann', displayName: 'Max Mustermann', active: true,
            roles: [szenario.admin ? 'ADMIN' : 'USER'], admin: szenario.admin, requiresInitialSetup: false,
        });
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/lieferanten') return json(route, { lieferanten: [], gesamt: 0 });
        if (pfad === '/api/lieferanten/7') return json(route, {
            id: 7, lieferantenname: 'Muster Lieferant GmbH', rollen: [], kundenEmails: [],
            dokumenteAnzahl: szenario.anzahl,
        });
        if (pfad === '/api/lieferanten/7/statistik') return json(route, {
            gesamtKosten: 0, bestellungAnzahl: 0, artikelAnzahl: 0, lieferzeit: 0,
        });
        if (pfad === '/api/lieferanten/7/dokumente') return json(route, szenario.dokumente);
        if (pfad === '/api/dokumentuebersicht/eingang') return json(route, szenario.dokumente.map(d => ({
            id: d.id, dokumentId: d.id, lieferantId: 7, lieferantName: d.lieferantName,
            typ: d.typ, dokumentNummer: d.geschaeftsdaten.dokumentNummer,
            dokumentDatum: d.geschaeftsdaten.dokumentDatum, betragNetto: 100, betragBrutto: 119,
            bezahlt: false, originalDateiname: d.originalDateiname, pdfUrl: null,
        })));
        return json(route, []);
    });
}

for (const szenario of szenarien) {
    test(`${szenario.name}: Lieferanten-Dokumente und Reiterzähler`, async ({ page, context, baseURL }) => {
        await context.addCookies([{ name: 'JSESSIONID', value: 'max-mustermann-session', url: baseURL!, httpOnly: true }]);
        await stubbeAntworten(page, szenario);
        const dokumentAbruf = page.waitForRequest(req => new URL(req.url()).pathname === '/api/lieferanten/7/dokumente');
        await page.goto('/lieferanten?lieferantId=7&tab=dokumente');
        const request = await dokumentAbruf;

        expect(new URL(request.url()).searchParams.has('token')).toBe(false);
        expect(await request.headerValue('cookie')).toContain('JSESSIONID=max-mustermann-session');
        await expect(page.getByRole('tab', { name: new RegExp(`Dokumente\\s+${szenario.anzahl}$`) })).toBeVisible();
        await expect(page.getByText('RE-MUSTER-41', { exact: true })).toHaveCount(szenario.anzahl > 0 ? 1 : 0);
        await expect(page.getByText('AN-MUSTER-42', { exact: true })).toHaveCount(szenario.admin ? 1 : 0);
        if (szenario.anzahl === 0) await expect(page.getByText('Keine Dokumente vorhanden', { exact: true })).toBeVisible();
    });

    test(`${szenario.name}: Dokumentübersicht Eingang`, async ({ page }) => {
        await stubbeAntworten(page, szenario);
        await page.goto('/dokumentuebersicht');
        await page.getByRole('button', { name: /Eingang/ }).click();

        await expect(page.getByText('RE-MUSTER-41', { exact: true })).toHaveCount(szenario.anzahl > 0 ? 1 : 0);
        await expect(page.getByText('AN-MUSTER-42', { exact: true })).toHaveCount(szenario.admin ? 1 : 0);
        if (szenario.anzahl === 0) await expect(page.getByText('Keine Eingangs-Dokumente gefunden.', { exact: true })).toBeVisible();
    });
}
