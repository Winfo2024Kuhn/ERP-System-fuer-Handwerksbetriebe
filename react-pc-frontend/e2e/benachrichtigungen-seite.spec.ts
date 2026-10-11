import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { inhalt } from './hilfen/seite';

/**
 * Seite „Benachrichtigungen“: Die Glocke öffnet kein Aufklapp-Fenster mehr,
 * sondern führt auf diese Seite. Dort stehen alle Meldungen nach Themen,
 * „Sicherung & Updates“ (nur für Admins) ganz oben.
 * Ohne Backend, alle Daten Dummy (DSGVO).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const SICHERUNG = {
    id: 'SICHERUNG:20261011-020017',
    type: 'SICHERUNG',
    title: 'Sicherung nur teilweise',
    subtitle: 'Externe Festplatte nicht gefunden. Bitte anschließen und den Rechner neu starten.\nDatenbank und Cloud-Ordner sind gesichert.',
    timestamp: '2026-10-11T02:00:17',
    link: '',
};

const UPDATE = {
    id: 'UPDATE:20261011-030000',
    type: 'UPDATE',
    title: 'Update fehlgeschlagen – alte Version läuft wieder',
    subtitle: 'Version 6f4efa2 -> 48d63f6\nGrund: Neue Version ist nicht sauber hochgefahren\n\nVolles Log auf dem Server: logs/gescheitert-20261011-030000-app.log',
    timestamp: '2026-10-11T03:00:00',
    link: '',
};

const TERMIN = {
    id: 'TERMIN:123',
    type: 'TERMIN',
    title: 'Aufmaß Bad',
    subtitle: 'Musterstraße 1, Musterstadt',
    timestamp: '2026-10-11',
    link: '/kalender?date=2026-10-11',
};

const SPAM_MAIL = {
    id: 'EMAIL:789',
    type: 'EMAIL',
    title: 'Gewinnspiel',
    subtitle: 'Von: werbung@example.com',
    timestamp: '2026-10-10T08:00:00',
    link: '/emails/spam/789',
};

interface Mitschrift {
    erledigt: unknown[];
    kategorien: unknown[];
    abrufe: URL[];
}

async function stubbeApi(page: Page): Promise<Mitschrift> {
    const mitschrift: Mitschrift = { erledigt: [], kategorien: [], abrufe: [] };
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const pfad = url.pathname;

        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'max', displayName: 'Max Mustermann', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/erledigt' && request.method() === 'POST') {
            mitschrift.erledigt.push(request.postDataJSON());
            return route.fulfill({ status: 204 });
        }
        if (pfad === '/api/notifications/erledigt/kategorien' && request.method() === 'POST') {
            mitschrift.kategorien.push(request.postDataJSON());
            return json(route, { erledigt: 2 });
        }
        if (pfad === '/api/notifications/summary') {
            mitschrift.abrufe.push(url);
            // Was erledigt ist, liefert der Server nicht mehr aus.
            const ids = new Set(mitschrift.erledigt.flatMap(e => (e as { ids: string[] }).ids));
            const kategorien = new Set(mitschrift.kategorien.flatMap(e => (e as { kategorien: string[] }).kategorien));
            const categories = [
                { type: 'SICHERUNG', label: 'Sicherung', count: ids.has(SICHERUNG.id) ? 0 : 1, icon: 'HardDrive', link: '' },
                { type: 'UPDATE', label: 'Update', count: ids.has(UPDATE.id) ? 0 : 1, icon: 'RefreshCw', link: '' },
                { type: 'TERMINE', label: 'Bevorstehende Termine', count: ids.has(TERMIN.id) ? 0 : 1, icon: 'CalendarClock', link: '/kalender' },
                { type: 'EMAILS_SPAM', label: 'Spam', count: 1234, icon: 'ShieldAlert', link: '/emails/spam' },
            ].filter(c => c.count > 0 && !kategorien.has(c.type));
            const recentItems = [SICHERUNG, UPDATE, TERMIN, SPAM_MAIL]
                .filter(i => !ids.has(i.id))
                .filter(i => categories.some(c => c.type === (i.type === 'EMAIL' ? 'EMAILS_SPAM' : i.type === 'TERMIN' ? 'TERMINE' : i.type)));
            return json(route, { totalCount: categories.reduce((s, c) => s + c.count, 0), categories, recentItems });
        }
        if (pfad === '/api/emails/stats') {
            return json(route, { inboxCount: 0, sentCount: 0, trashCount: 0, spamCount: 1234, newsletterCount: 0, unassignedCount: 0, projectCount: 0, offerCount: 0, supplierCount: 0, taxAdvisorCount: 0, inboxTotal: 0 });
        }
        if (pfad === '/api/emails/drafts/count') return json(route, { count: 0 });
        // Fallback für sonstige Endpunkte (Kalender, Projekte …)
        return json(route, []);
    });
    return mitschrift;
}

test.describe('Seite „Benachrichtigungen“', () => {
    test('Glocke öffnet die Seite, Sicherungs-Meldung steht oben, „Erledigt“ und Filter funktionieren', async ({ page }, testInfo) => {
        const mitschrift = await stubbeApi(page);
        await page.goto('/projekte');

        // 1. Glocke: nur Zahl, Klick führt auf die Seite.
        const glocke = page.getByRole('button', { name: /^Benachrichtigungen/ });
        await expect(glocke).toHaveAccessibleName('Benachrichtigungen (1237 neu)');
        await expect(glocke).toContainText('99+');
        await glocke.click();
        await expect(page).toHaveURL(/\/benachrichtigungen$/);
        await expect(glocke).toHaveAttribute('aria-current', 'page');
        await expect(page.getByRole('menu')).toHaveCount(0);

        // 2. Seite fragt alle Einträge ab; „Sicherung & Updates“ steht ganz oben.
        const seite = inhalt(page);
        await expect(seite.getByRole('heading', { level: 1 })).toHaveText(/Benachrichtigungen/i);
        await expect.poll(() => mitschrift.abrufe.some(u => u.searchParams.get('alle') === 'true')).toBe(true);
        const ueberschriften = seite.getByRole('heading', { level: 2 });
        await expect(ueberschriften).toHaveText(['Sicherung & Updates', 'Posteingänge', 'Termine']);

        // 3. Sicherungs-Meldung vollständig, mehrzeilig, ohne „Öffnen“.
        const betrieb = seite.getByRole('region', { name: 'Sicherung & Updates' });
        await expect(betrieb.getByText('Sicherung nur teilweise')).toBeVisible();
        await expect(betrieb.getByText(/Datenbank und Cloud-Ordner sind gesichert\./)).toBeVisible();
        await expect(betrieb.getByText(/Volles Log auf dem Server: logs\/gescheitert-20261011-030000-app\.log/)).toBeVisible();
        await expect(betrieb.getByRole('button', { name: /^Öffnen/ })).toHaveCount(0);
        // Zwei Zeilen statt einer: der Zeilenumbruch vom Server bleibt stehen.
        const sicherungsText = betrieb.getByText(/Externe Festplatte nicht gefunden/);
        const hoehe = await sicherungsText.evaluate(el => el.getBoundingClientRect().height);
        expect(hoehe).toBeGreaterThan(30);
        // Jede Zeile des Themas trägt den roten Rand links – auch die zweite
        // (Befund aus der Design-Prüfung: die Trennlinien färbten ihn grau).
        const raender = await betrieb.getByRole('listitem').evaluateAll(zeilen =>
            zeilen.map(z => [getComputedStyle(z).borderLeftWidth, getComputedStyle(z).borderLeftColor]));
        expect(raender).toEqual([['4px', 'rgb(239, 68, 68)'], ['4px', 'rgb(239, 68, 68)']]);

        // 4. „und X weitere“ bei vielen ungelesenen Spam-Mails.
        const post = seite.getByRole('region', { name: 'Posteingänge' });
        await expect(post.getByText(/und\s*1\.233\s*weitere/)).toBeVisible();
        await expect(post.getByRole('button', { name: 'Spam: zur Übersicht' })).toBeVisible();

        await designPruefung(page, testInfo, 'benachrichtigungen-seite', {
            primaerAktion: betrieb.getByRole('button', { name: 'Erledigt: Sicherung nur teilweise' }),
        });

        // 5. Info-Knopf neben der Überschrift: Popover lesbar, nichts abgeschnitten.
        await betrieb.getByRole('button', { name: 'Mehr zu: Sicherung & Updates' }).click();
        const info = page.getByRole('dialog', { name: 'Sicherung & Updates' });
        await expect(info).toBeVisible();
        await expect(info).toContainText('Jede Nacht (standardmäßig um 2 Uhr)');
        await expect(info).toContainText('Diese Meldungen sehen nur Admins.');
        await expect(info).toBeInViewport({ ratio: 1 });
        await designPruefung(page, testInfo, 'benachrichtigungen-info');
        await page.keyboard.press('Escape');
        await expect(info).toHaveCount(0);

        // 6. „Erledigt“ an der Sicherung: id an den Server, Eintrag weg, Glocke zählt runter.
        const erledigt = page.waitForRequest(r => r.url().endsWith('/api/notifications/erledigt') && r.method() === 'POST');
        await betrieb.getByRole('button', { name: 'Erledigt: Sicherung nur teilweise' }).click();
        expect((await erledigt).postDataJSON()).toEqual({ ids: [SICHERUNG.id] });
        await expect(betrieb.getByText('Sicherung nur teilweise')).toHaveCount(0);
        await expect(betrieb.getByText('Update fehlgeschlagen – alte Version läuft wieder')).toBeVisible();
        await expect(glocke).toHaveAccessibleName('Benachrichtigungen (1236 neu)');

        // 7. Filter über die Themenleiste – das Thema steht in der Adresse.
        const themen = page.getByRole('group', { name: 'Nach Thema filtern' });
        await themen.getByRole('button', { name: /^Termine/ }).click();
        await expect(page).toHaveURL(/\/benachrichtigungen\?thema=termine$/);
        await expect(ueberschriften).toHaveText(['Termine']);
        await expect(themen.getByRole('button', { name: /^Termine/ })).toHaveAttribute('aria-pressed', 'true');
        await designPruefung(page, testInfo, 'benachrichtigungen-filter');

        // Neu laden mit Thema in der Adresse: der Filter bleibt.
        await page.reload();
        await expect(ueberschriften).toHaveText(['Termine']);

        await themen.getByRole('button', { name: /^Alle/ }).click();
        await expect(page).toHaveURL(/\/benachrichtigungen$/);
        await expect(ueberschriften).toHaveText(['Sicherung & Updates', 'Posteingänge', 'Termine']);
    });

    test('„Alle erledigt“ bei Sicherung & Updates meldet beide Kategorien', async ({ page }) => {
        const mitschrift = await stubbeApi(page);
        await page.goto('/benachrichtigungen?thema=betrieb');

        const seite = inhalt(page);
        await expect(seite.getByRole('heading', { level: 2 })).toHaveText(['Sicherung & Updates']);
        const anfrage = page.waitForRequest(r => r.url().endsWith('/api/notifications/erledigt/kategorien') && r.method() === 'POST');
        await seite.getByRole('button', { name: 'Sicherung & Updates: alle erledigt' }).click();
        expect((await anfrage).postDataJSON()).toEqual({ kategorien: ['SICHERUNG', 'UPDATE'] });
        expect(mitschrift.kategorien).toEqual([{ kategorien: ['SICHERUNG', 'UPDATE'] }]);

        await expect(seite.getByText('In diesem Thema ist alles erledigt.')).toBeVisible();
        await seite.getByRole('button', { name: 'Alle Themen zeigen' }).click();
        await expect(seite.getByRole('heading', { level: 2 })).toHaveText(['Posteingänge', 'Termine']);
    });

    test('ohne Meldungen: klarer leerer Zustand', async ({ page }, testInfo) => {
        await page.route('**/api/**', async (route) => {
            const pfad = new URL(route.request().url()).pathname;
            if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
            return json(route, []);
        });
        await page.goto('/benachrichtigungen');

        await expect(inhalt(page).getByText('Keine neuen Benachrichtigungen – alles erledigt.')).toBeVisible();
        await expect(page.getByRole('button', { name: 'Benachrichtigungen', exact: true })).toBeVisible();
        await designPruefung(page, testInfo, 'benachrichtigungen-leer');
    });

    test('Server nicht erreichbar: Fehlermeldung mit „Neu laden“', async ({ page }) => {
        // Bis zum Klick auf „Neu laden“ scheitert jeder Abruf (im Dev-Modus lädt
        // React beim Start doppelt – ein Zähler „erster Abruf“ reicht deshalb nicht).
        let erreichbar = false;
        await page.route('**/api/**', async (route) => {
            const url = new URL(route.request().url());
            if (url.pathname === '/api/notifications/summary' && url.searchParams.get('alle') === 'true') {
                if (!erreichbar) return json(route, { message: 'Fehler' }, 500);
                return json(route, { totalCount: 1, categories: [{ type: 'TERMINE', label: 'Bevorstehende Termine', count: 1, icon: 'CalendarClock', link: '/kalender' }], recentItems: [TERMIN] });
            }
            if (url.pathname === '/api/notifications/summary') return json(route, { totalCount: 1, categories: [], recentItems: [] });
            return json(route, []);
        });
        await page.goto('/benachrichtigungen');

        const fehler = inhalt(page).getByRole('alert');
        await expect(fehler).toContainText('Benachrichtigungen konnten nicht geladen werden.');
        await expect(fehler.getByRole('button', { name: 'Neu laden' })).toBeVisible();
        erreichbar = true;
        await fehler.getByRole('button', { name: 'Neu laden' }).click();
        await expect(inhalt(page).getByText('Aufmaß Bad')).toBeVisible();
    });
});
