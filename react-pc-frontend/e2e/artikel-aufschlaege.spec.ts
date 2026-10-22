import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Artikelübersicht: Aufschläge einstellen, nur für Benutzer mit dem Recht „Aufschläge ändern“.
 * /api ist gestubbt (kein Backend). Nur Dummy-Daten (DSGVO).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

interface Zeile { id: number; oberkategorieId: number | null; pfad: string; prozent: number | null; wirksamProzent: number | null; wirksamQuelle: string; wirksamVon: string | null }

async function stubApi(page: Page, darfAendern: boolean) {
    let allgemein: number | null = 20;
    const kategorien: Zeile[] = [
        { id: 1, oberkategorieId: null, pfad: 'Stahl', prozent: 25, wirksamProzent: 25, wirksamQuelle: 'KATEGORIE', wirksamVon: 'Stahl' },
        { id: 2, oberkategorieId: 1, pfad: 'Stahl › Rohre', prozent: null, wirksamProzent: 25, wirksamQuelle: 'KATEGORIE', wirksamVon: 'Stahl' },
    ];
    const gesendet: { pfad: string; body: Record<string, unknown> }[] = [];
    const aufrufe: string[] = [];
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        if (pfad === '/api/auth/me') return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/artikel') return json(route, { artikel: [], gesamt: 0 });
        if (pfad === '/api/artikel/werkstoffe') return json(route, []);
        if (pfad === '/api/artikel/filteroptionen') return json(route, { herstellverfahren: [], fertigungszustand: [], profilform: [] });
        if (pfad === '/api/aufschlaege/berechtigung') return json(route, { darfAendern });
        if (pfad.startsWith('/api/aufschlaege') && pfad !== '/api/aufschlaege/berechtigung') aufrufe.push(`${methode} ${pfad}`);
        if (pfad === '/api/aufschlaege' && methode === 'GET') return json(route, { allgemein, kategorien });
        if (pfad === '/api/aufschlaege/allgemein' && methode === 'PUT') {
            const e = route.request().postDataJSON() as { prozent: number | null };
            gesendet.push({ pfad, body: e });
            allgemein = e.prozent;
            return json(route, { allgemein, kategorien });
        }
        const m = /^\/api\/aufschlaege\/kategorien\/(\d+)$/.exec(pfad);
        if (m && methode === 'PUT') {
            const e = route.request().postDataJSON() as { prozent: number | null };
            gesendet.push({ pfad, body: e });
            const z = kategorien.find(k => k.id === Number(m[1]))!;
            z.prozent = e.prozent;
            if (e.prozent != null) { z.wirksamProzent = e.prozent; z.wirksamQuelle = 'KATEGORIE'; z.wirksamVon = z.pfad; }
            return json(route, { allgemein, kategorien });
        }
        return json(route, []);
    });
    return { gesendet, aufrufe };
}

test('Artikelübersicht: mit dem Recht Aufschläge einstellen', async ({ page }, testInfo) => {
    const { gesendet } = await stubApi(page, true);
    await page.goto('/artikel');

    await page.getByRole('button', { name: /^Aufschläge/ }).click();
    const fenster = page.getByRole('dialog', { name: 'Aufschläge' });
    await expect(fenster).toBeVisible();
    await expect(fenster.getByLabel('Aufschlag in Prozent')).toHaveValue('20');
    await expect(fenster.getByTestId('aufschlag-kategorie-2').getByText('25 % (von „Stahl“)')).toBeVisible();
    await designPruefung(page, testInfo, 'artikel-aufschlaege');

    // Info-Button: erklärt, was im Hintergrund passiert
    await fenster.getByRole('button', { name: /Mehr zu: Aufschläge/ }).click();
    await expect(page.getByText('Was passiert im Hintergrund?')).toBeVisible();
    await designPruefung(page, testInfo, 'artikel-aufschlaege-info');
    await page.keyboard.press('Escape');

    // allgemeinen Aufschlag ändern (Komma)
    await fenster.getByLabel('Aufschlag in Prozent', { exact: true }).fill('12,5');
    await fenster.getByRole('button', { name: 'Allgemeinen Aufschlag speichern' }).click();
    await expect(page.getByText('Allgemeiner Aufschlag: 12,5 % gespeichert.')).toBeVisible();
    expect(gesendet.at(-1)).toEqual({ pfad: '/api/aufschlaege/allgemein', body: { prozent: 12.5 } });

    // Aufschlag einer Kategorie setzen: „Es gilt“ folgt
    await fenster.getByLabel('Aufschlag für Stahl › Rohre', { exact: true }).fill('30');
    await fenster.getByRole('button', { name: 'Aufschlag für Stahl › Rohre speichern' }).click();
    await expect(fenster.getByTestId('aufschlag-kategorie-2').getByText('30 %')).toBeVisible();
    expect(gesendet.at(-1)).toEqual({ pfad: '/api/aufschlaege/kategorien/2', body: { prozent: 30 } });

    // ein unmöglicher Wert wird nicht gesendet
    const anzahl = gesendet.length;
    await fenster.getByLabel('Aufschlag in Prozent', { exact: true }).fill('1000');
    await fenster.getByRole('button', { name: 'Allgemeinen Aufschlag speichern' }).click();
    expect(gesendet).toHaveLength(anzahl);
});

test('Artikelübersicht: ohne das Recht gibt es keinen Knopf und keine Abfrage der Aufschläge', async ({ page }) => {
    const { aufrufe } = await stubApi(page, false);
    await page.goto('/artikel');

    await expect(page.getByRole('button', { name: /Kategorien anlegen/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /^Aufschläge/ })).toHaveCount(0);
    expect(aufrufe).toEqual([]);
});
