import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { stubbeZeiterfassungSichtbarkeit } from './hilfen/api';
import { designPruefung } from './hilfen/design';

/**
 * Issue #270: Urlaubsanträge stornieren (PC) und Server-Meldungen sichtbar machen.
 *
 * Geprüft wird der echte Ablauf über Seite und Dialoge: „Stornieren" fragt
 * zurück, ruft PUT /api/urlaub/antraege/{id}/storno, lädt die Liste neu und
 * bestätigt per Toast. Lehnt der Server mit 409 ab (Monat festgeschrieben),
 * steht die Meldung des Servers im Toast statt stillem Nichts. Dasselbe gilt
 * für „Genehmigen" mit 400.
 *
 * DSGVO: ausschließlich Dummy-Mitarbeiter (Max Mustermann, Erika Musterfrau).
 * Ohne Backend: alle /api-Routen sind gestubbt.
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const ANTRAG_OFFEN = {
    id: 1,
    mitarbeiterName: 'Max Mustermann',
    mitarbeiter: { id: 10, vorname: 'Max', nachname: 'Mustermann', jahresUrlaub: 30 },
    vonDatum: '2026-08-03',
    bisDatum: '2026-08-14',
    bemerkung: null,
    erstellDatum: '2026-07-01',
    typ: 'URLAUB',
    entschiedenAm: null,
    entscheidungGesehen: false,
};

const ANTRAG_GENEHMIGT = {
    id: 2,
    mitarbeiterName: 'Erika Musterfrau',
    mitarbeiter: { id: 11, vorname: 'Erika', nachname: 'Musterfrau', jahresUrlaub: 28 },
    vonDatum: '2026-09-07',
    bisDatum: '2026-09-11',
    bemerkung: 'Herbstferien',
    erstellDatum: '2026-07-02',
    typ: 'URLAUB',
    entschiedenAm: '2026-07-05T09:00:00',
    entscheidungGesehen: true,
};

const MELDUNG_409 = 'Der Monat 09/2026 ist bereits festgeschrieben. Bitte zuerst den Monatsabschluss zurücksetzen.';
const MELDUNG_400 = 'Für den 03.08.2026 ist noch keine Arbeitszeit hinterlegt. Bitte zuerst Arbeitszeit zuweisen.';

/**
 * Stubbt die Seite. `statusById` ist veränderlich: ein echtes Storno setzt den
 * Stand um, damit die Liste nach dem Neuladen wirklich anders aussieht.
 */
async function stubbeApi(page: Page, opts: { stornoFehler?: boolean; approveFehler?: boolean } = {}) {
    const statusById: Record<number, string> = { 1: 'OFFEN', 2: 'GENEHMIGT' };
    const aktionen: string[] = [];

    await page.route('**/api/**', (route) => {
        const url = new URL(route.request().url());
        const pfad = url.pathname;
        const methode = route.request().method();

        if (pfad === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'anna.buero', displayName: 'Anna Büro',
                active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
            });
        }
        if (pfad === '/api/notifications/summary') {
            return json(route, { totalCount: 0, categories: [], recentItems: [] });
        }
        if (pfad === '/api/urlaub/antraege' && methode === 'GET') {
            const statusFilter = url.searchParams.get('status');
            const liste = [
                { ...ANTRAG_OFFEN, status: statusById[1] },
                { ...ANTRAG_GENEHMIGT, status: statusById[2] },
            ].filter((a) => !statusFilter || a.status === statusFilter);
            return json(route, liste);
        }
        const aktion = pfad.match(/^\/api\/urlaub\/antraege\/(\d+)\/(approve|reject|storno)$/);
        if (aktion && methode === 'PUT') {
            const id = Number(aktion[1]);
            aktionen.push(`${aktion[2]}:${id}`);
            if (aktion[2] === 'storno') {
                if (opts.stornoFehler) return json(route, { status: 409, message: MELDUNG_409 }, 409);
                statusById[id] = 'STORNIERT';
                return json(route, { ...(id === 1 ? ANTRAG_OFFEN : ANTRAG_GENEHMIGT), status: 'STORNIERT' });
            }
            if (opts.approveFehler) return json(route, { status: 400, message: MELDUNG_400 }, 400);
            statusById[id] = aktion[2] === 'approve' ? 'GENEHMIGT' : 'ABGELEHNT';
            return json(route, { ...ANTRAG_OFFEN, status: statusById[id] });
        }
        if (pfad === '/api/langzeitkrankmeldungen/urlaubs-hinweise') {
            return json(route, { warnungen: [] });
        }
        return json(route, []);
    });

    return aktionen;
}

/** Wechselt den Statusfilter über das eigene Auswahlfeld der Seite. */
async function filtereNach(page: Page, option: string) {
    await page.getByRole('combobox').filter({ hasText: /Anträge$/ }).click();
    await page.getByRole('option', { name: option }).click();
}

test.describe('Urlaubsanträge: Stornieren und Server-Meldungen (#270)', () => {
    test('storniert einen genehmigten Antrag nach Rückfrage und zeigt den Erfolg', async ({ page }, testInfo) => {
        const aktionen = await stubbeApi(page);
        await stubbeZeiterfassungSichtbarkeit(page);
        await page.goto('/urlaubsantraege');
        await expect(page.locator('[data-antrag-id="1"]')).toBeVisible();

        await filtereNach(page, 'Genehmigte Anträge');
        const karte = page.locator('[data-antrag-id="2"]');
        await expect(karte).toBeVisible();
        await expect(karte.getByText('Genehmigt')).toBeVisible();

        // Genehmigte Anträge haben nur noch „Stornieren" – kein Genehmigen/Ablehnen.
        await expect(karte.getByRole('button', { name: 'Genehmigen' })).toHaveCount(0);
        const stornoKnopf = karte.getByRole('button', { name: 'Stornieren', exact: true });
        await expect(stornoKnopf).toBeVisible();

        // Der Info-Knopf erklärt, was im Hintergrund passiert – geöffnet mit prüfen.
        await karte.getByRole('button', { name: 'Mehr zu: Stornieren' }).click();
        const info = page.getByRole('dialog', { name: 'Stornieren' });
        await expect(info).toContainText('Was passiert im Hintergrund?');
        await expect(info).toContainText('Bei Urlaub steigt der Resturlaub');
        await designPruefung(page, testInfo, 'urlaubsantraege-storno-info', { primaerAktion: stornoKnopf });
        await page.keyboard.press('Escape');
        await expect(info).toHaveCount(0);

        await stornoKnopf.click();
        const rueckfrage = page.getByRole('dialog');
        await expect(rueckfrage).toContainText('Urlaubsantrag stornieren?');
        await expect(rueckfrage).toContainText('Resturlaub steigt wieder');
        await designPruefung(page, testInfo, 'urlaubsantraege-storno-rueckfrage', {
            primaerAktion: rueckfrage.getByRole('button', { name: 'Stornieren', exact: true }),
        });

        // Abbrechen tut nichts.
        await rueckfrage.getByRole('button', { name: 'Abbrechen' }).click();
        expect(aktionen).toEqual([]);

        await stornoKnopf.click();
        await page.getByRole('dialog').getByRole('button', { name: 'Stornieren', exact: true }).click();

        await expect(page.getByText('Antrag storniert.')).toBeVisible();
        expect(aktionen).toEqual(['storno:2']);
        // Nach dem Neuladen ist der Antrag nicht mehr unter „Genehmigt" …
        await expect(karte).toHaveCount(0);
        await expect(page.getByText('Keine genehmigten Anträge.')).toBeVisible();

        // … sondern unter „Storniert", mit Klartext-Badge und ohne weitere Aktionen.
        await filtereNach(page, 'Stornierte Anträge');
        const storniert = page.locator('[data-antrag-id="2"]');
        await expect(storniert).toBeVisible();
        await expect(storniert.getByText('Storniert')).toBeVisible();
        await expect(storniert.getByRole('button', { name: 'Stornieren', exact: true })).toHaveCount(0);
        await expect(page.getByText('STORNIERT', { exact: true })).toHaveCount(0);
    });

    test('zeigt bei 409 die Meldung des Servers und lässt den Antrag stehen', async ({ page }, testInfo) => {
        const aktionen = await stubbeApi(page, { stornoFehler: true });
        await stubbeZeiterfassungSichtbarkeit(page);
        await page.goto('/urlaubsantraege');
        await expect(page.locator('[data-antrag-id="1"]')).toBeVisible();

        await filtereNach(page, 'Genehmigte Anträge');
        const karte = page.locator('[data-antrag-id="2"]');
        await karte.getByRole('button', { name: 'Stornieren', exact: true }).click();
        await page.getByRole('dialog').getByRole('button', { name: 'Stornieren', exact: true }).click();

        const toast = page.getByRole('alert').filter({ hasText: MELDUNG_409 });
        await expect(toast).toBeVisible();
        expect(aktionen).toEqual(['storno:2']);
        await expect(karte).toBeVisible();
        await designPruefung(page, testInfo, 'urlaubsantraege-storno-409', {
            primaerAktion: karte.getByRole('button', { name: 'Stornieren', exact: true }),
        });
    });

    test('zeigt beim Genehmigen die Meldung des Servers statt stillem Nichts', async ({ page }) => {
        const aktionen = await stubbeApi(page, { approveFehler: true });
        await stubbeZeiterfassungSichtbarkeit(page);
        await page.goto('/urlaubsantraege');
        const karte = page.locator('[data-antrag-id="1"]');
        await expect(karte).toBeVisible();

        await karte.getByRole('button', { name: 'Genehmigen' }).click();
        await page.getByRole('dialog').getByRole('button', { name: 'Genehmigen' }).click();

        await expect(page.getByRole('alert').filter({ hasText: MELDUNG_400 })).toBeVisible();
        expect(aktionen).toEqual(['approve:1']);
        // Der Antrag bleibt offen und bedienbar.
        await expect(karte.getByRole('button', { name: 'Genehmigen' })).toBeEnabled();
    });
});
