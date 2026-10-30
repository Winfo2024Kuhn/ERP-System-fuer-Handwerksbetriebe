import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Seite Kostenstellen: Knöpfe zum Ändern nur mit dem Recht „Kostenstellen verwalten“.
 * Eine gelöschte Kostenstelle steht unter „Gelöschte Kostenstellen“ und lässt sich zurückholen.
 * /api ist gestubbt (kein Backend), kleiner Speicher-Server. Nur Dummy-Daten (DSGVO).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

interface Ks { id: number; bezeichnung: string; typ: string; beschreibung: string | null; istFixkosten: boolean; istInvestition: boolean; aktiv: boolean; sortierung: number }

async function stubApi(page: Page, darfAendern: boolean) {
    const kostenstellen: Ks[] = [
        { id: 1, bezeichnung: 'Werkstatt', typ: 'GEMEINKOSTEN', beschreibung: 'Strom und Miete', istFixkosten: true, istInvestition: false, aktiv: true, sortierung: 1 },
        { id: 7, bezeichnung: 'Musterlager', typ: 'LAGER', beschreibung: 'Material auf Vorrat', istFixkosten: false, istInvestition: true, aktiv: true, sortierung: 2 },
    ];
    const schreibend: string[] = [];
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        if (pfad === '/api/auth/me') return json(route, { id: 1, username: 'max', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/firma/kostenstellen/berechtigung') return json(route, { darfAendern });
        if (pfad === '/api/bestellungen-uebersicht/kostenstellen/auswertung') {
            return json(route, kostenstellen.filter(k => k.aktiv).map(k => ({
                ...k, summeDiesesJahr: k.id * 100, summeVorjahr: 50, anzahlDiesesJahr: 2,
            })));
        }
        if (pfad === '/api/firma/kostenstellen/geloescht') {
            if (!darfAendern) return json(route, { status: 403, message: 'Du darfst Kostenstellen nicht verwalten.' }, 403);
            return json(route, kostenstellen.filter(k => !k.aktiv));
        }
        if (pfad.startsWith('/api/firma/kostenstellen') && methode !== 'GET') schreibend.push(`${methode} ${pfad}`);
        const loeschen = /^\/api\/firma\/kostenstellen\/(\d+)$/.exec(pfad);
        if (loeschen && methode === 'DELETE') {
            kostenstellen.find(k => k.id === Number(loeschen[1]))!.aktiv = false;
            return route.fulfill({ status: 204 });
        }
        const zurueck = /^\/api\/firma\/kostenstellen\/(\d+)\/wiederherstellen$/.exec(pfad);
        if (zurueck && methode === 'POST') {
            const ks = kostenstellen.find(k => k.id === Number(zurueck[1]))!;
            ks.aktiv = true;
            return json(route, ks);
        }
        return json(route, []);
    });
    return { schreibend };
}

test('Kostenstellen: löschen und wiederherstellen', async ({ page }, testInfo) => {
    const { schreibend } = await stubApi(page, true);
    await page.goto('/kostenstellen');

    // Löschen mit Rückfrage
    await page.getByRole('button', { name: 'Musterlager löschen' }).click();
    const rueckfrage = page.getByRole('dialog', { name: 'Kostenstelle löschen' });
    await expect(rueckfrage.getByText(/Bisherige Buchungen bleiben erhalten/)).toBeVisible();
    await rueckfrage.getByRole('button', { name: 'Löschen' }).click();
    await expect(page.getByText('Kostenstelle gelöscht')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Musterlager löschen' })).toHaveCount(0);

    // Bereich „Gelöschte Kostenstellen“: zugeklappt mit Anzahl, aufgeklappt mit Zeile
    const bereich = page.getByTestId('geloeschte-kostenstellen');
    const umschalter = bereich.getByRole('button', { name: /^Gelöschte Kostenstellen/ });
    await expect(umschalter).toHaveAttribute('aria-expanded', 'false');
    await expect(umschalter).toContainText('1');
    await umschalter.click();
    await expect(bereich.getByText('Material auf Vorrat')).toBeVisible();
    await expect(bereich.getByText('Lager (Investitionen)')).toBeVisible();

    await bereich.getByRole('button', { name: 'Mehr zu: Gelöschte Kostenstellen' }).click();
    await expect(page.getByText(/wird automatisch die alte zurückgeholt/)).toBeVisible();
    await designPruefung(page, testInfo, 'kostenstellen-geloeschte-info', { ganzeSeite: true });
    await page.keyboard.press('Escape');
    await designPruefung(page, testInfo, 'kostenstellen-geloeschte', { ganzeSeite: true });

    // Wiederherstellen: Kachel ist wieder da, Bereich verschwindet
    await bereich.getByRole('button', { name: 'Musterlager wiederherstellen' }).click();
    await expect(page.getByText('Kostenstelle wiederhergestellt')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Musterlager löschen' })).toBeVisible();
    await expect(bereich).toHaveCount(0);
    expect(schreibend).toEqual(['DELETE /api/firma/kostenstellen/7', 'POST /api/firma/kostenstellen/7/wiederherstellen']);
});

test('Kostenstellen: ohne Recht nur ansehen, keine Knöpfe zum Ändern', async ({ page }, testInfo) => {
    const { schreibend } = await stubApi(page, false);
    const recht = page.waitForResponse('**/api/firma/kostenstellen/berechtigung');
    await page.goto('/kostenstellen');
    await recht;

    await expect(page.getByText('Musterlager')).toBeVisible();
    await expect(page.getByText('Werkstatt')).toBeVisible();
    await expect(page.getByRole('button', { name: /Neue Kostenstelle/ })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /bearbeiten$/ })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /löschen$/ })).toHaveCount(0);
    await expect(page.getByTestId('geloeschte-kostenstellen')).toHaveCount(0);
    await designPruefung(page, testInfo, 'kostenstellen-ohne-recht');
    expect(schreibend).toEqual([]);
});
