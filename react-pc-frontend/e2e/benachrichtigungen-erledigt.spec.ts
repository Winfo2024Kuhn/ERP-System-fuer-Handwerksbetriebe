import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Benachrichtigungen und E-Mail-Center nach der Umstellung „Erledigt merkt sich der Server":
 * - Die Glocke führt auf die Seite „Benachrichtigungen“. Ein dort geöffneter
 *   Eintrag geht als id an POST /api/notifications/erledigt und bleibt weg,
 *   weil der Server ihn ab dann nicht mehr ausliefert.
 * - Der Filter „Ungelesen" im E-Mail-Center fragt den Server mit
 *   nurUngelesen=true – so erscheinen auch ältere ungelesene Mails, die nicht
 *   unter den ersten 50 geladenen stehen.
 * Ohne Backend, alle Daten Dummy (DSGVO).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const TERMIN = {
    id: 'TERMIN:123',
    type: 'TERMIN',
    title: 'Aufmaß Bad',
    subtitle: 'Heute 10:00',
    timestamp: '2026-10-11',
    link: '/kalender?date=2026-10-11',
};

const ANFRAGE_WEBSEITE = {
    id: 'ANFRAGE_WEBSEITE:9',
    type: 'ANFRAGE_WEBSEITE',
    title: 'Anfrage: Max Mustermann',
    subtitle: 'Geländer Balkon',
    timestamp: '2026-10-11T08:00:00',
    link: '/anfragen?anfrageId=9',
};

const GELESENE_MAIL = {
    id: 201,
    type: 'EMAIL',
    direction: 'IN',
    subject: 'Rückfrage Lieferung Stahlträger',
    sender: 'Max Mustermann',
    fromAddress: 'max.mustermann@example.com',
    recipient: 'info@musterbetrieb.example',
    body: 'Wann kommt die Lieferung?',
    sentAt: '2026-10-10T09:00:00',
    isRead: true,
    zuordnungTyp: 'KEINE',
    attachments: [],
};

const ALTE_UNGELESENE_MAIL = {
    ...GELESENE_MAIL,
    id: 202,
    subject: 'Alte Anfrage Treppengeländer',
    body: 'Bitte um ein Angebot.',
    sentAt: '2026-08-01T09:00:00',
    isRead: false,
};

const STATS = {
    inboxCount: 1, sentCount: 0, trashCount: 0, spamCount: 0, newsletterCount: 0, unassignedCount: 0,
    projectCount: 0, offerCount: 0, supplierCount: 0, taxAdvisorCount: 0, inboxTotal: 51,
};

interface Mitschrift {
    erledigt: unknown[];
    ordnerAbrufe: URL[];
}

async function stubbeApi(page: Page): Promise<Mitschrift> {
    const mitschrift: Mitschrift = { erledigt: [], ordnerAbrufe: [] };
    await page.route('**/api/**', async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const pfad = url.pathname;

        if (pfad === '/api/notifications/erledigt' && request.method() === 'POST') {
            mitschrift.erledigt.push(request.postDataJSON());
            return route.fulfill({ status: 204 });
        }
        if (pfad === '/api/notifications/summary') {
            // Was erledigt ist, liefert der Server nicht mehr aus.
            const terminErledigt = mitschrift.erledigt.length > 0;
            return json(route, terminErledigt
                ? {
                    totalCount: 1,
                    categories: [{ type: 'ANFRAGEN_WEBSEITE', label: 'Neue Anfragen', count: 1, icon: 'Globe', link: '/anfragen' }],
                    recentItems: [ANFRAGE_WEBSEITE],
                }
                : {
                    totalCount: 2,
                    categories: [
                        { type: 'ANFRAGEN_WEBSEITE', label: 'Neue Anfragen', count: 1, icon: 'Globe', link: '/anfragen' },
                        { type: 'TERMINE', label: 'Bevorstehende Termine', count: 1, icon: 'CalendarClock', link: '/kalender' },
                    ],
                    recentItems: [ANFRAGE_WEBSEITE, TERMIN],
                });
        }
        if (/^\/api\/emails\/(inbox|spam)$/.test(pfad)) {
            mitschrift.ordnerAbrufe.push(url);
            const nurUngelesen = url.searchParams.get('nurUngelesen') === 'true';
            if (pfad === '/api/emails/spam') return json(route, nurUngelesen ? [] : [GELESENE_MAIL]);
            return json(route, nurUngelesen ? [ALTE_UNGELESENE_MAIL] : [GELESENE_MAIL]);
        }
        if (pfad === '/api/emails/stats') return json(route, STATS);
        if (pfad === '/api/emails/drafts/count') return json(route, { count: 0 });
        if (pfad === '/api/emails/absender-postfaecher') {
            return json(route, [{ id: 1, emailAdresse: 'info@musterbetrieb.example', anzeigename: null, eigenes: false, hauptpostfach: true }]);
        }
        // Fallback fuer sonstige Endpunkte (Benutzer, Kalender etc.)
        return json(route, []);
    });
    return mitschrift;
}

test.describe('Benachrichtigungen: Erledigt merkt sich der Server', () => {
    test('„Öffnen“ an einem Termin meldet seine id als erledigt, danach bleibt er weg', async ({ page }, testInfo) => {
        const mitschrift = await stubbeApi(page);
        await page.goto('/emails/inbox');

        const glocke = page.getByRole('button', { name: /^Benachrichtigungen/ });
        await expect(glocke).toContainText('2');
        await glocke.click();
        await expect(page).toHaveURL(/\/benachrichtigungen$/);
        const termin = page.getByRole('button', { name: 'Öffnen: Aufmaß Bad' });
        await expect(termin).toBeVisible();
        await designPruefung(page, testInfo, 'benachrichtigungen-termin', { primaerAktion: termin });

        const erledigtAnfrage = page.waitForRequest(r =>
            r.url().endsWith('/api/notifications/erledigt') && r.method() === 'POST');
        await termin.click();
        const anfrage = await erledigtAnfrage;
        expect(anfrage.postDataJSON()).toEqual({ ids: ['TERMIN:123'] });
        expect(mitschrift.erledigt).toEqual([{ ids: ['TERMIN:123'] }]);
        // Der Kalender übernimmt ?date= selbst und räumt den Parameter danach weg.
        await expect(page).toHaveURL(/\/kalender/);

        // Zähler sinkt, und auch nach dem Neuladen vom Server ist der Termin weg.
        await expect(glocke).toContainText('1');
        await glocke.click();
        await expect(page.getByText('Anfrage: Max Mustermann')).toBeVisible();
        await expect(page.getByText('Aufmaß Bad')).toHaveCount(0);

        // Kein eigener Browser-Merker mehr.
        const merker = await page.evaluate(() => localStorage.getItem('notification_dismissed_items_v2'));
        expect(merker).toBeNull();
    });
});

test.describe('Benachrichtigungen: ganzes Thema erledigt', () => {
    test('meldet die Kategorien des Themas, leert die Liste und das E-Mail-Center zeigt die neuen Zahlen', async ({ page }) => {
        const MAIL_EINTRAG = {
            id: 'EMAIL:301',
            type: 'EMAIL',
            title: 'Angebot Carport',
            subtitle: 'Von: max.mustermann@example.com',
            timestamp: '2026-10-11T07:30:00',
            link: '/emails/inbox/301',
        };
        const kategorienAnfragen: unknown[] = [];
        const statsAbrufe: number[] = [];
        await page.route('**/api/**', async (route) => {
            const request = route.request();
            const pfad = new URL(request.url()).pathname;
            if (pfad === '/api/notifications/erledigt/kategorien' && request.method() === 'POST') {
                kategorienAnfragen.push(request.postDataJSON());
                return json(route, { erledigt: 3 });
            }
            if (pfad === '/api/notifications/summary') {
                return json(route, kategorienAnfragen.length > 0
                    ? { totalCount: 0, categories: [], recentItems: [] }
                    : {
                        totalCount: 3,
                        categories: [{ type: 'EMAILS', label: 'Ungelesene E-Mails', count: 3, icon: 'Mail', link: '/emails' }],
                        recentItems: [MAIL_EINTRAG],
                    });
            }
            if (pfad === '/api/emails/stats') {
                statsAbrufe.push(kategorienAnfragen.length);
                return json(route, { ...STATS, inboxCount: kategorienAnfragen.length > 0 ? 0 : 3 });
            }
            if (pfad === '/api/emails/inbox') return json(route, [GELESENE_MAIL]);
            if (pfad === '/api/emails/drafts/count') return json(route, { count: 0 });
            return json(route, []);
        });
        await page.goto('/emails/inbox');

        const glocke = page.getByRole('button', { name: /^Benachrichtigungen/ });
        await expect(glocke).toContainText('3');
        await glocke.click();
        await expect(page.getByText('Angebot Carport')).toBeVisible();

        const anfrage = page.waitForRequest(r =>
            r.url().endsWith('/api/notifications/erledigt/kategorien') && r.method() === 'POST');
        await page.getByRole('button', { name: 'Posteingänge: alle erledigt' }).click();
        expect((await anfrage).postDataJSON()).toEqual({ kategorien: ['EMAILS'] });

        // Liste sofort leer, Zähler an der Glocke weg.
        await expect(page.getByText('Angebot Carport')).toHaveCount(0);
        await expect(page.getByText('Keine neuen Benachrichtigungen – alles erledigt.')).toBeVisible();
        await expect(glocke).not.toContainText('3');

        // Zurück im E-Mail-Center stimmen die Ordnerzahlen (Abruf nach dem Erledigen).
        await page.goBack();
        await expect(page).toHaveURL(/\/emails\/inbox/);
        await expect.poll(() => statsAbrufe.some(nachPost => nachPost > 0)).toBe(true);
    });
});

test.describe('E-Mail-Center: Filter „Ungelesen" fragt den Server', () => {
    test('zeigt die ältere ungelesene Mail und einen klaren Hinweis, wenn nichts ungelesen ist', async ({ page }, testInfo) => {
        const mitschrift = await stubbeApi(page);
        await page.goto('/emails/inbox');

        await expect(page.getByText('Rückfrage Lieferung Stahlträger')).toBeVisible();
        expect(mitschrift.ordnerAbrufe.some(u => u.searchParams.has('nurUngelesen'))).toBe(false);

        const gefilterteAnfrage = page.waitForRequest(r => {
            const url = new URL(r.url());
            return url.pathname === '/api/emails/inbox' && url.searchParams.get('nurUngelesen') === 'true';
        });
        await page.getByRole('button', { name: 'Ungelesen', exact: true }).click();
        await gefilterteAnfrage;

        await expect(page.getByText('Alte Anfrage Treppengeländer')).toBeVisible();
        await designPruefung(page, testInfo, 'email-filter-ungelesen', { strengePruefungen: false });
        await expect(page.getByText('Rückfrage Lieferung Stahlträger')).toHaveCount(0);

        // Leerer Ordner bei „Ungelesen": eigener Hinweis statt einer stummen leeren Liste.
        await page.goto('/emails/spam');
        await page.getByRole('button', { name: 'Ungelesen', exact: true }).click();
        await expect(page.getByText('Keine ungelesenen E-Mails in diesem Ordner')).toBeVisible();
        await designPruefung(page, testInfo, 'email-filter-ungelesen-leer', { strengePruefungen: false });
        expect(mitschrift.ordnerAbrufe.some(u =>
            u.pathname === '/api/emails/spam' && u.searchParams.get('nurUngelesen') === 'true')).toBe(true);

        // Zurück auf „Alle": wieder die volle Liste, nicht die gefilterte aus dem Zwischenspeicher.
        await page.getByRole('button', { name: 'Alle', exact: true }).click();
        await expect(page.getByText('Rückfrage Lieferung Stahlträger')).toBeVisible();
    });
});
