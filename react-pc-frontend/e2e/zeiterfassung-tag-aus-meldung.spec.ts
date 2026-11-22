import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { stubbeZeiterfassungSichtbarkeit } from './hilfen/api';
import { designPruefung } from './hilfen/design';

/**
 * „Öffnen“ an einer Meldung „Überschneidung Handy/Büro“ führt direkt in die Tagesansicht
 * des richtigen Mitarbeiters am richtigen Tag; die betroffene Buchung ist markiert.
 * Beim Schließen verschwinden Tag, Markierung und Anlass aus der Adresse.
 *
 * DSGVO: nur Dummy-Daten (Max Mustermann). Ohne Backend, alles gestubbt.
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const LINK = '/zeitbuchungen?mitarbeiterId=2&jahr=2026&monat=11&tag=2026-11-18&anlass=ueberschneidung&markiert=12';
const TITEL = 'Überschneidung Handy/Büro: Max Mustermann';

const arbeit = (id: number, start: string, ende: string) => ({
    id, projektId: 9, arbeitsgangId: 4, projektName: 'Treppe Musterweg', arbeitsgangName: 'Montage',
    startZeit: start, endeZeit: ende, dauerMinuten: null, dauerFormatiert: null, notiz: '', typ: 'ARBEIT',
});
const pause = (id: number, start: string, ende: string) => ({ ...arbeit(id, start, ende), projektId: -1, arbeitsgangId: null, projektName: '[INTERN] Pause', typ: 'PAUSE' });

const tag = (datum: string, wochentag: number, buchungen: unknown[] = []) => ({
    datum, wochentag, istFeiertag: false, feiertagName: null, sollStunden: wochentag < 6 ? 8 : 0, istStunden: 0, buchungen,
});

async function stubbeApi(page: Page) {
    await page.route('**/api/**', (route) => {
        const url = new URL(route.request().url());
        const pfad = url.pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'anna.buero', vorname: 'Anna', nachname: 'Büro', admin: true, roles: ['ADMIN'], requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/erledigt') return route.fulfill({ status: 204 });
        if (pfad === '/api/notifications/summary') {
            return json(route, {
                totalCount: 1,
                categories: [{ type: 'ZEITEN_UEBERSCHNEIDUNG', label: 'Überschneidung Handy/Büro', count: 1, icon: 'CalendarClock', link: '/zeitbuchungen' }],
                recentItems: [{
                    id: 'ZEIT_UEBERSCHNEIDUNG:1', type: 'ZEIT_UEBERSCHNEIDUNG', title: TITEL,
                    subtitle: 'Max Mustermann am 18.11.2026 – Vom Handy: Arbeit 11:27–12:00, Pause 12:00–13:34. Büro-Eintrag „Pause“ 12:00–13:00 wurde deshalb entfernt.',
                    timestamp: '2026-11-18T13:40:00', link: LINK,
                }],
            });
        }
        if (pfad === '/api/mitarbeiter') {
            return json(route, [{ id: 1, vorname: 'Erika', nachname: 'Musterfrau', aktiv: true }, { id: 2, vorname: 'Max', nachname: 'Mustermann', aktiv: true }]);
        }
        if (pfad === '/api/projekte/simple') return json(route, [{ id: 9, bauvorhaben: 'Treppe Musterweg', auftragsnummer: 'A-26-001' }]);
        if (pfad === '/api/arbeitsgaenge') return json(route, [{ id: 4, beschreibung: 'Montage' }]);
        if (pfad.endsWith('/berechtigung')) return json(route, { darfMonatAbschliessen: true });
        if (pfad.startsWith('/api/zeitverwaltung/monatsabschluesse/')) {
            return json(route, { mitarbeiterId: 2, jahr: 2026, monat: 11, festgeschrieben: false, version: 1, sollStunden: 160, gesamtIst: 150, differenz: -10, audit: [] });
        }
        if (pfad === '/api/zeitverwaltung/kalender') {
            const buchungen = url.searchParams.get('mitarbeiterId') === '2'
                ? [arbeit(10, '07:00', '11:27'), arbeit(11, '11:27', '12:00'), pause(13, '12:00', '13:34'), arbeit(12, '13:34', '16:00')]
                : [];
            return json(route, {
                jahr: 2026, monat: 11, sollStundenMonat: 160, istStundenMonat: 150, differenz: -10,
                tage: [tag('2026-11-17', 2), tag('2026-11-18', 3, buchungen), tag('2026-11-19', 4)],
            });
        }
        if (/^\/api\/zeitverwaltung\/mitarbeiter\/\d+\/saldo$/.test(pfad)) {
            return json(route, { urlaub: { jahresanspruch: 30, genommen: 0, geplant: 0, verbleibend: 30 }, gesamt: { istStunden: 150, sollStunden: 160, saldo: -10 } });
        }
        return json(route, []);
    });
}

test('Meldung „Überschneidung Handy/Büro“ öffnet den Tag mit markierter Buchung', async ({ page }, testInfo) => {
    await stubbeApi(page);
    await stubbeZeiterfassungSichtbarkeit(page);
    await page.goto('/benachrichtigungen');

    await page.getByRole('button', { name: `Öffnen: ${TITEL}` }).click();
    await expect(page).toHaveURL(/tag=2026-11-18/);

    const editor = page.getByRole('dialog', { name: 'Tageserfassung' });
    await expect(editor).toBeVisible();
    await expect(editor).toContainText('18. November 2026');
    await expect(page.getByRole('combobox', { name: 'Mitarbeiter', exact: true })).toContainText('Max Mustermann');

    const hinweis = editor.getByRole('note', { name: 'Überschneidung Handy/Büro' });
    await expect(hinweis).toContainText('Hier haben sich Handy- und Büro-Zeiten überschnitten.');
    const markiert = editor.locator('[data-markiert]');
    await expect(markiert).toHaveCount(1);
    await expect(markiert).toBeInViewport();
    await expect(markiert).toContainText('Aus Meldung');
    await expect(markiert.getByRole('textbox', { name: /^Von Buchung/ })).toHaveValue('13:34');

    await designPruefung(page, testInfo, 'tag-aus-meldung', { primaerAktion: editor.getByRole('button', { name: 'Alle Speichern' }) });

    await hinweis.getByRole('button', { name: 'Mehr zu: Überschneidung Handy/Büro' }).click();
    const info = page.getByText(/hat das Programm die Handy-Zeit behalten/);
    await expect(info).toBeInViewport({ ratio: 1 });
    await expect(page.getByText(/Einträge in abgeschlossenen Monaten ändert das Programm nie von selbst/)).toBeInViewport({ ratio: 1 });
    await designPruefung(page, testInfo, 'tag-aus-meldung-info');
    await page.keyboard.press('Escape');
    await expect(info).toBeHidden();

    await editor.getByRole('button', { name: 'Schließen', exact: true }).click();
    await expect(editor).toBeHidden();
    await expect(page).toHaveURL(/\/zeitbuchungen\?mitarbeiterId=2&jahr=2026&monat=11$/);

    // Neu laden öffnet nichts mehr von selbst.
    await page.reload();
    await expect(page.locator('[class*="min-h-32"]').filter({ hasText: '18' }).first()).toBeVisible();
    await expect(page.getByRole('dialog', { name: 'Tageserfassung' })).toBeHidden();
});
