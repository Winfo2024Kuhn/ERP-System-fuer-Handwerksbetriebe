import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Task 17 (Abschnitt 5, Langzeitkrankmeldung, Issue #91): Warnhinweis auf der
 * Urlaubsanträge-Seite, wenn sich der Zeitraum eines offenen Antrags mit
 * einer laufenden Krankmeldung desselben Mitarbeiters überschneidet.
 *
 * Endpunkt: GET /api/langzeitkrankmeldungen/urlaubs-hinweise?mitarbeiterId=&von=&bis=
 * -- bewusst NICHT /api/urlaub/antraege/hinweise. Der alte Pfad lag auf der
 * permitAll-Kette der Zeiterfassungs-App (siehe SecurityConfig) und hätte
 * Gesundheitsdaten (Art. 9 DSGVO: Existenz + Beginndatum einer
 * Langzeitkrankmeldung) ohne Login preisgegeben -- siehe Kommentar an
 * UrlaubsantragController.getHinweise().
 *
 * Reine Warnung, keine Sperre (Spec Abschnitt 7): Der Antrag wird nicht
 * abgelehnt, "Genehmigen" bleibt anklickbar -- das Büro entscheidet. Diese
 * Spec prüft deshalb auch, dass ein echter Genehmigen-Klick trotz Hinweis
 * durchgeht.
 *
 * DSGVO: ausschließlich Dummy-Mitarbeiter (Max Mustermann, Erika
 * Musterfrau), kein echter Personenbezug. Ohne Backend -- alle /api-Routen
 * gestubbt (Vorbild: stubMitarbeiterApi in e2e/mitarbeiter-layout.spec.ts).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const ANTRAG_MIT_HINWEIS = {
    id: 1,
    mitarbeiterName: 'Max Mustermann',
    mitarbeiter: { id: 10, vorname: 'Max', nachname: 'Mustermann', jahresUrlaub: 30 },
    vonDatum: '2026-03-10',
    bisDatum: '2026-03-20',
    bemerkung: null,
    erstellDatum: '2026-02-01',
    typ: 'URLAUB',
};

const ANTRAG_OHNE_HINWEIS = {
    id: 2,
    mitarbeiterName: 'Erika Musterfrau',
    mitarbeiter: { id: 11, vorname: 'Erika', nachname: 'Musterfrau', jahresUrlaub: 28 },
    vonDatum: '2026-04-01',
    bisDatum: '2026-04-05',
    bemerkung: null,
    erstellDatum: '2026-02-01',
    typ: 'URLAUB',
};

const WARNTEXT = 'In diesem Zeitraum läuft eine Krankmeldung (seit 01.03.2026). Bitte prüfen, ob der Urlaub wirklich passt.';

/**
 * Stubbt alle /api-Routen der Urlaubsanträge-Seite. `statusById` ist
 * veränderlich -- ein echter Genehmigen-Klick (PUT .../approve) setzt den
 * Status im Stub um, genau wie das echte Backend, damit der reale
 * Neu-Laden-nach-Genehmigen-Ablauf geprüft werden kann.
 */
async function stubbeApi(page: Page, opts: { hinweisFehler?: boolean } = {}) {
    const statusById: Record<number, string> = { 1: 'OFFEN', 2: 'OFFEN' };

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
                { ...ANTRAG_MIT_HINWEIS, status: statusById[1] },
                { ...ANTRAG_OHNE_HINWEIS, status: statusById[2] },
            ].filter((a) => !statusFilter || a.status === statusFilter);
            return json(route, liste);
        }
        const genehmigenMatch = pfad.match(/^\/api\/urlaub\/antraege\/(\d+)\/approve$/);
        if (genehmigenMatch && methode === 'PUT') {
            const id = Number(genehmigenMatch[1]);
            statusById[id] = 'GENEHMIGT';
            return json(route, { id, status: 'GENEHMIGT' });
        }
        if (pfad === '/api/langzeitkrankmeldungen/urlaubs-hinweise') {
            if (opts.hinweisFehler) return route.fulfill({ status: 500, body: '' });
            const mitarbeiterId = url.searchParams.get('mitarbeiterId');
            if (mitarbeiterId === '10') return json(route, { warnungen: [WARNTEXT] });
            return json(route, { warnungen: [] });
        }

        // Standardantwort fuer alles Weitere: leere Liste statt 404, damit
        // kein Fehlerzustand die Seite fuellt (Vorbild stubMitarbeiterApi).
        return json(route, []);
    });
}

test.describe('Urlaubsanträge: Hinweis bei laufender Krankmeldung (Task 17)', () => {
    test('zeigt den Warnhinweis nur beim betroffenen Antrag, und "Genehmigen" wirkt trotzdem', async ({ page }, testInfo) => {
        await stubbeApi(page);
        await page.goto('/urlaubsantraege');

        const karteMitHinweis = page.locator('[data-antrag-id="1"]');
        const karteOhneHinweis = page.locator('[data-antrag-id="2"]');
        await expect(karteMitHinweis).toBeVisible();
        await expect(karteOhneHinweis).toBeVisible();

        // Der Hinweis erscheint über den Aktionsknöpfen, bevor irgendein Klick
        // erfolgt ist -- nicht erst als Reaktion auf "Genehmigen".
        await expect(karteMitHinweis.getByText(WARNTEXT)).toBeVisible();
        await expect(karteOhneHinweis.getByText(WARNTEXT)).not.toBeVisible();

        // Warnung, keine Sperre: der Knopf bleibt anklickbar.
        const genehmigenKnopf = karteMitHinweis.getByRole('button', { name: 'Genehmigen' });
        await expect(genehmigenKnopf).toBeEnabled();

        // Design-Pruefung (Ebene 2, Befund 2): Screenshot + automatische
        // Layout-Zusicherungen fuer genau den Zustand, um den es hier geht --
        // eine Karte mit sichtbarem Warnhinweis neben einer Karte ohne.
        await designPruefung(page, testInfo, 'urlaubsantrag-krankmeldung-hinweis', {
            primaerAktion: genehmigenKnopf,
        });

        await genehmigenKnopf.click();

        const bestaetigenDialog = page.getByRole('dialog');
        await expect(bestaetigenDialog).toBeVisible();
        await expect(bestaetigenDialog).toContainText('Urlaubsantrag genehmigen?');
        await bestaetigenDialog.getByRole('button', { name: 'Genehmigen' }).click();

        // Der Antrag verschwindet nach dem echten Genehmigen aus der (weiter
        // auf OFFEN gefilterten) Liste -- der Hinweis hat den Vorgang nicht
        // blockiert, das Büro konnte trotz Warnung entscheiden.
        await expect(karteMitHinweis).not.toBeVisible();
        await expect(karteOhneHinweis).toBeVisible();
    });

    test('ein Fehler bei der Hinweis-Abfrage zeigt keinen Kasten und lässt die Antragsliste unangetastet', async ({ page }) => {
        await stubbeApi(page, { hinweisFehler: true });
        await page.goto('/urlaubsantraege');

        await expect(page.getByText('Mustermann, Max')).toBeVisible();
        await expect(page.getByText('Musterfrau, Erika')).toBeVisible();
        await expect(page.getByText(/Krankmeldung/)).toHaveCount(0);

        // Kein Kasten heißt nicht "kaputte Seite": beide Genehmigen-Knöpfe
        // bleiben bedienbar, obwohl der Hinweis-Abruf fehlgeschlagen ist.
        for (const knopf of await page.getByRole('button', { name: 'Genehmigen' }).all()) {
            await expect(knopf).toBeEnabled();
        }
    });
});
