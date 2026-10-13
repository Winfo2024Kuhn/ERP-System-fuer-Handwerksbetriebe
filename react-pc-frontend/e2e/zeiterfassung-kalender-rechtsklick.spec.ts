import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { stubbeZeiterfassungSichtbarkeit } from './hilfen/api';
import { designPruefung } from './hilfen/design';

/**
 * Issue #269: Rechtsklick im Zeiterfassungs-Kalender bucht den angeklickten
 * Tag (nicht die alte Markierung), Resturlaub zeigt halbe Tage mit Komma und
 * erklärt sich über einen Info-Knopf, und „Monatsabschluss zurücksetzen"
 * erscheint nur mit Abschlussrecht.
 *
 * DSGVO: nur Dummy-Daten (Max Mustermann). Ohne Backend, alles gestubbt.
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const tag = (nr: number, wochentag: number) => ({
    datum: `2026-08-${String(nr).padStart(2, '0')}`,
    wochentag,
    istFeiertag: false,
    feiertagName: null,
    sollStunden: wochentag < 6 ? 8 : 0,
    istStunden: 0,
    buchungen: [],
});

async function stubbeApi(page: Page, opts: { darfMonatAbschliessen?: boolean; festgeschrieben?: boolean } = {}) {
    const abwesenheiten: Array<{ datum: string; typ: string; halberTag: boolean }> = [];
    await page.route('**/api/**', (route) => {
        const url = new URL(route.request().url());
        const pfad = url.pathname;
        const methode = route.request().method();

        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'anna.buero', vorname: 'Anna', nachname: 'Büro', admin: true, roles: ['ADMIN'], requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/mitarbeiter') return json(route, [{ id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true }]);
        if (pfad.endsWith('/berechtigung')) return json(route, { darfMonatAbschliessen: opts.darfMonatAbschliessen ?? true });
        if (pfad === '/api/zeitverwaltung/monatsabschluesse/1/2026/8') {
            return json(route, {
                mitarbeiterId: 1, jahr: 2026, monat: 8, festgeschrieben: opts.festgeschrieben ?? false, version: 1,
                sollStunden: 160, gesamtIst: 160, differenz: 0,
                audit: opts.festgeschrieben ? [{ id: 1, aktion: 'ABSCHLIESSEN', akteurName: 'Anna Büro', zeitpunkt: '2026-09-01T10:00:00' }] : [],
            });
        }
        if (pfad === '/api/zeitverwaltung/kalender') {
            return json(route, { jahr: 2026, monat: 8, sollStundenMonat: 160, istStundenMonat: 0, differenz: 0, tage: [tag(3, 1), tag(4, 2), tag(9, 7), tag(14, 5)] });
        }
        if (pfad === '/api/zeitverwaltung/mitarbeiter/1/saldo') {
            return json(route, { urlaub: { jahresanspruch: 30, genommen: 10.5, geplant: 7, verbleibend: 12.5 }, gesamt: { istStunden: 820, sollStunden: 800, saldo: 20 } });
        }
        if (pfad === '/api/abwesenheit' && methode === 'POST') {
            const body = route.request().postDataJSON();
            abwesenheiten.push(body);
            return json(route, { id: 500 + abwesenheiten.length, ...body });
        }
        return json(route, []);
    });
    return abwesenheiten;
}

/** Die Tageszelle: der Elternkasten um die Tageszahl. */
const zelle = (page: Page, nr: string) => page.locator('[class*="min-h-32"]').filter({ has: page.getByText(nr, { exact: true }) }).first();

test('Rechtsklick bucht den angeklickten Tag; Resturlaub mit halben Tagen und Info-Knopf', async ({ page }, testInfo) => {
    const abwesenheiten = await stubbeApi(page);
    await stubbeZeiterfassungSichtbarkeit(page);
    await page.goto('/zeitbuchungen?mitarbeiterId=1&jahr=2026&monat=8');

    // Resturlaub: 12,5 Tage mit Komma, Erklärung über (i).
    await expect(page.getByText('12,5 Tage')).toBeVisible();
    await expect(page.getByText('Genommen: 10,5')).toBeVisible();
    await page.getByRole('button', { name: 'Mehr zu: Resturlaub' }).click();
    const info = page.getByRole('dialog', { name: 'Resturlaub' });
    await expect(info).toContainText('Halbe Urlaubstage zählen als 0,5 Tage');
    await designPruefung(page, testInfo, 'kalender-resturlaub-info');
    await page.keyboard.press('Escape');
    await expect(info).toHaveCount(0);

    // Erst den 3. linksklicken (markiert ihn), dann den 14. rechtsklicken.
    await zelle(page, '3').click();
    await zelle(page, '14').click({ button: 'right' });
    const menue = page.getByText('Abwesenheit buchen');
    await expect(menue).toBeVisible();
    // Das Menü nennt den rechtsgeklickten Tag, nicht den markierten 3.
    await expect(menue.locator('..')).toContainText('14. August');
    // Screenshot des offenen Menüs (es schwebt bewusst über dem Raster, deshalb
    // hier ohne die Überschneidungs-Prüfung – die folgt unten im Ruhezustand).
    await testInfo.attach('design: kalender-rechtsklick-menue', {
        body: await page.screenshot(), contentType: 'image/png',
    });
    await page.getByRole('button', { name: 'Urlaub (ganzer Tag)' }).click();

    await expect.poll(() => abwesenheiten.length).toBe(1);
    expect(abwesenheiten[0]).toMatchObject({ mitarbeiterId: 1, datum: '2026-08-14', typ: 'URLAUB', halberTag: false });
    await expect(menue).toHaveCount(0);
    await designPruefung(page, testInfo, 'kalender-nach-rechtsklick-buchung');

    // Rechtsklick auf einen Sonntag: kein Arbeitstag → Hinweis statt stillem Nichts.
    await zelle(page, '9').click({ button: 'right' });
    await page.getByRole('button', { name: 'Urlaub (ganzer Tag)' }).click();
    await expect(page.getByText('Im markierten Zeitraum liegt kein Arbeitstag.')).toBeVisible();
    expect(abwesenheiten).toHaveLength(1);
});

test('ohne Abschlussrecht fehlt „Monatsabschluss zurücksetzen" und der Dialog erklärt warum', async ({ page }, testInfo) => {
    await stubbeApi(page, { darfMonatAbschliessen: false, festgeschrieben: true });
    await stubbeZeiterfassungSichtbarkeit(page);
    await page.goto('/zeitbuchungen?mitarbeiterId=1&jahr=2026&monat=8');
    await expect(page.getByText(/Abgeschlossen – die Monatssummen/)).toBeVisible();

    await zelle(page, '3').dblclick();
    const dialog = page.getByRole('dialog', { name: 'Monatsabschluss ist festgeschrieben' });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('button', { name: /Monatsabschluss zurücksetzen/ })).toHaveCount(0);
    await expect(dialog).toContainText('fehlt Ihnen das Abschlussrecht');
    await designPruefung(page, testInfo, 'kalender-festgeschrieben-ohne-recht', {
        primaerAktion: dialog.getByRole('button', { name: 'Nur ansehen' }),
    });

    await dialog.getByRole('button', { name: 'Nur ansehen' }).click();
    const editor = page.getByRole('dialog', { name: 'Tageserfassung' });
    await expect(editor).toBeVisible();
    await expect(editor.getByRole('button', { name: /Monatsabschluss zurücksetzen/ })).toHaveCount(0);
});
