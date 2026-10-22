import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Artikelübersicht: Werkstoffe pflegen (anlegen, ändern, löschen) im Fenster neben „Kategorien anlegen“.
 * /api ist gestubbt (kein Backend). Nur Dummy-Daten (DSGVO).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

interface Werkstoff {
    id: number; name: string; anzeigename: string | null; werkstattname: string | null; dichte: number | null; werkstoffnorm: string | null;
    verzinkungsgeeignet: boolean; pulverbeschichtungsgeeignet: boolean; beschichtungshinweis: string | null; artikelAnzahl: number;
}

async function stubApi(page: Page) {
    const werkstoffe: Werkstoff[] = [
        { id: 1, name: 'S235JR', anzeigename: 'Baustahl', werkstattname: 'Schwarzstahl', dichte: 7.85, werkstoffnorm: 'EN 10025', verzinkungsgeeignet: true, pulverbeschichtungsgeeignet: true, beschichtungshinweis: null, artikelAnzahl: 12 },
        { id: 2, name: '1.4301', anzeigename: null, werkstattname: 'V2A', dichte: null, werkstoffnorm: null, verzinkungsgeeignet: false, pulverbeschichtungsgeeignet: false, beschichtungshinweis: 'Wird nicht verzinkt', artikelAnzahl: 0 },
    ];
    const gesendet: { methode: string; pfad: string; body: Record<string, unknown> | null }[] = [];
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        if (pfad === '/api/auth/me') return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/artikel') return json(route, { artikel: [], gesamt: 0 });
        if (pfad === '/api/artikel/werkstoffe') return json(route, werkstoffe.map(w => w.name));
        if (pfad === '/api/artikel/filteroptionen') return json(route, { herstellverfahren: [], fertigungszustand: [], profilform: [] });
        if (pfad === '/api/werkstoffe' && methode === 'GET') return json(route, werkstoffe);
        if (pfad === '/api/werkstoffe' && methode === 'POST') {
            const e = route.request().postDataJSON() as Record<string, unknown>;
            gesendet.push({ methode, pfad, body: e });
            if (e.name === 'S235JR') return json(route, { message: 'Einen Werkstoff mit dem Namen „S235JR“ gibt es schon.' }, 409);
            const neu = { id: 3, name: String(e.name), anzeigename: (e.anzeigename as string) || null, werkstattname: null, dichte: (e.dichte as number) ?? null, werkstoffnorm: null,
                verzinkungsgeeignet: !!e.verzinkungsgeeignet, pulverbeschichtungsgeeignet: !!e.pulverbeschichtungsgeeignet, beschichtungshinweis: null, artikelAnzahl: 0 };
            werkstoffe.push(neu);
            return json(route, neu, 201);
        }
        const m = /^\/api\/werkstoffe\/(\d+)$/.exec(pfad);
        if (m && methode === 'PUT') {
            const e = route.request().postDataJSON() as Record<string, unknown>;
            gesendet.push({ methode, pfad, body: e });
            const w = werkstoffe.find(x => x.id === Number(m[1]))!;
            Object.assign(w, { name: e.name, anzeigename: e.anzeigename || null, dichte: e.dichte ?? null });
            return json(route, w);
        }
        if (m && methode === 'DELETE') {
            gesendet.push({ methode, pfad, body: null });
            const w = werkstoffe.find(x => x.id === Number(m[1]))!;
            if (w.artikelAnzahl > 0) return json(route, { message: `Dieser Werkstoff wird von ${w.artikelAnzahl} Artikeln benutzt und kann nicht gelöscht werden.` }, 409);
            werkstoffe.splice(werkstoffe.indexOf(w), 1);
            return route.fulfill({ status: 204 });
        }
        return json(route, []);
    });
    return gesendet;
}

test('Artikelübersicht: Werkstoffe anlegen, ändern und löschen', async ({ page }, testInfo) => {
    const gesendet = await stubApi(page);
    await page.goto('/artikel');

    await page.getByRole('button', { name: /Werkstoffe pflegen/ }).click();
    const fenster = page.getByRole('dialog', { name: 'Werkstoffe' });
    await expect(fenster).toBeVisible();
    await expect(fenster.getByText('Baustahl')).toBeVisible();
    await expect(fenster.getByText('7,85 kg/dm³')).toBeVisible();
    await designPruefung(page, testInfo, 'artikel-werkstoffe');

    // Info-Button an der Dichte
    await fenster.getByRole('button', { name: 'Neuer Werkstoff' }).click();
    await fenster.getByRole('button', { name: /Mehr zu: Dichte/ }).click();
    await expect(page.getByText('Was passiert im Hintergrund?')).toBeVisible();
    // offenes Formular mit offenem Info-Popover: nichts abgeschnitten, nichts überdeckt
    await designPruefung(page, testInfo, 'artikel-werkstoffe-formular');
    await page.keyboard.press('Escape');
    await fenster.getByRole('button', { name: /Mehr zu: Eignung für die Oberfläche/ }).click();
    await expect(page.getByText(/Filtern „verzinkbar“/)).toBeVisible();
    await page.keyboard.press('Escape');

    // doppelter Name: Klartext, nichts angelegt
    await fenster.getByLabel('Name (technisch) *').fill('S235JR');
    await fenster.getByRole('button', { name: 'Speichern' }).click();
    await expect(fenster.getByTestId('werkstoff-formular').getByRole('alert')).toContainText('gibt es schon');

    // gültig anlegen
    await fenster.getByLabel('Name (technisch) *').fill('EN AW-6060');
    await fenster.getByLabel('Anzeigename').fill('Aluminium');
    await fenster.getByLabel('Dichte (kg/dm³)').fill('2,7');
    await fenster.getByLabel('Pulverbeschichten geht').check();
    await fenster.getByRole('button', { name: 'Speichern' }).click();
    await expect(fenster.getByText('2,7 kg/dm³')).toBeVisible();
    expect(gesendet.at(-1)).toMatchObject({ methode: 'POST', body: { name: 'EN AW-6060', dichte: 2.7, pulverbeschichtungsgeeignet: true, verzinkungsgeeignet: false } });

    // ändern
    await fenster.getByRole('button', { name: 'Aluminium ändern' }).click();
    await fenster.getByLabel('Anzeigename').fill('Aluminium 6060');
    await fenster.getByRole('button', { name: 'Speichern' }).click();
    await expect(fenster.getByText('Aluminium 6060')).toBeVisible();

    // ein benutzter Werkstoff lässt sich nicht löschen, ein freier schon
    await fenster.getByRole('button', { name: 'Baustahl löschen' }).click();
    await page.getByRole('button', { name: 'Löschen', exact: true }).click();
    await expect(page.getByText(/wird von 12 Artikeln benutzt/).first()).toBeVisible();
    await fenster.getByRole('button', { name: 'Aluminium 6060 löschen' }).click();
    await page.getByRole('button', { name: 'Löschen', exact: true }).click();
    await expect(fenster.getByText('Aluminium 6060')).toHaveCount(0);
});
