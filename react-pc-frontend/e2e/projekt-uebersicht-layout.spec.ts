import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung } from './hilfen/design';

/**
 * Task 3 (Abschnitt 3), Baustelle D (Uebersichtskarte) aus
 * docs/superpowers/plans/2026-09-05-layout-14-zoll.md: die Projektkarten in
 * der Uebersicht (/projekte) kuerzen den Titel heute per "truncate" (eine
 * Zeile, "…") -- bei einem langen Bauvorhaben faellt fast der ganze Name weg
 * (Spec-Befund 4). Ziel: "line-clamp-2 min-h-[3rem]" + data-kuerzung-erlaubt
 * (voller Name im title-Attribut, wie im Kartentitel dokumentiert erlaubt --
 * Spec E.3 / Global Constraint "Kuerzung mit …"), dazu das Kartenraster von
 * "xl:grid-cols-4" auf "2xl:grid-cols-4" (lg:grid-cols-3 bleibt) -- bei 1440
 * (>=1024px, <1536px) also drei Karten je Reihe statt vier, bei 1920
 * (>=1536px) weiterhin vier.
 *
 * Vier Projekte mit langen Bauvorhaben (Fantasienamen, DSGVO), damit die
 * Kuerzung ueberhaupt sichtbar wird -- kurze Namen wie "Carport" wuerden das
 * Problem nicht zeigen.
 *
 * /api vollstaendig gestubbt, kein Backend.
 */

const PROJEKTE = [
    { id: 101, bauvorhaben: 'Treppenanlage mit Podest und Absturzsicherung Bürogebäude Beispielstraße', kunde: 'Beispielbau Nord GmbH' },
    { id: 102, bauvorhaben: 'Terrassenüberdachung mit Glasschiebewänden am Musterweiher', kunde: 'Wohnbau Beispielstadt eG' },
    { id: 103, bauvorhaben: 'Balkonanlage mit Absturzsicherung Wohnanlage Musterring', kunde: 'Beispiel Immobilien KG' },
    { id: 104, bauvorhaben: 'Carportanlage mit Solardach und Ladepunkt am Beispielhof', kunde: 'Mustermann Grundbesitz GmbH' },
].map((p, i) => ({
    ...p,
    auftragsnummer: `A-2026-${(1000 + i).toString()}`,
    anlegedatum: '2026-01-15',
    bruttoPreis: 42000 + i * 1000,
    bezahlt: false,
    abgeschlossen: false,
}));

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubProjektUebersichtApi(page: Page) {
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
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
        if (/^\/api\/last-accessed\/PROJEKT(\/\d+)?$/.test(pfad)) {
            if (methode === 'POST') return route.fulfill({ status: 204, body: '' });
            return json(route, {});
        }
        if (pfad === '/api/projekte' && methode === 'GET') {
            return json(route, { projekte: PROJEKTE, gesamt: PROJEKTE.length });
        }
        if (pfad === '/api/projekte/jahre') return json(route, [2026]);
        if (pfad === '/api/projekte/freigabe-status') return json(route, {});

        return json(route, []);
    });
}

/** Gruppiert Rahmen mit (fast) gleicher y-Koordinate zu Zeilen, oben nach unten. */
function zeilenGroessen(boxen: { x: number; y: number }[], toleranz = 2): number[] {
    const sortiert = [...boxen].sort((a, b) => a.y - b.y);
    const zeilen: number[][] = [];
    for (const box of sortiert) {
        const zeile = zeilen.find((z) => Math.abs(z[0] - box.y) <= toleranz);
        if (zeile) zeile.push(box.y);
        else zeilen.push([box.y]);
    }
    return zeilen.map((z) => z.length);
}

test.describe('Projektuebersicht: lange Bauvorhaben-Titel nicht abgehackt, Kartenraster', () => {
    test('kein Titel einzeilig abgehackt, drei Karten je Reihe bei 1440 / vier bei 1920', async ({ page }, testInfo) => {
        await stubProjektUebersichtApi(page);
        await page.goto('/projekte');
        await expect(page.getByRole('heading', { name: 'Projektübersicht' })).toBeVisible();

        const titelBoxen: { x: number; y: number }[] = [];
        for (const projekt of PROJEKTE) {
            const titel = page.getByRole('heading', { name: projekt.bauvorhaben, level: 3 });
            await expect(titel, `Titel "${projekt.bauvorhaben}" nicht gefunden`).toBeVisible();
            const box = await titel.boundingBox();
            expect(box, `Titel "${projekt.bauvorhaben}" muss einen messbaren Rahmen haben`).not.toBeNull();
            titelBoxen.push(box!);
        }

        const erwarteteSpalten = testInfo.project.name === 'pc-14zoll' ? 3 : 4;
        const zeilen = zeilenGroessen(titelBoxen);
        expect(
            zeilen[0],
            `Erste Kartenreihe bei ${testInfo.project.name} (${page.viewportSize()!.width}px) hat ${zeilen[0]} Karte(n), erwartet ${erwarteteSpalten} (Zeilen insgesamt: ${zeilen.join(', ')})`,
        ).toBe(erwarteteSpalten);

        // "kein Titel einzeilig abgehackt": mit strengePruefungen greift
        // keinTextGekuerzt -- das laesst line-clamp-2 + data-kuerzung-erlaubt
        // (der dokumentiert erlaubte Ausnahmefall) durch, meldet aber jede
        // andere Kuerzung (insbesondere die heutige einzeilige "truncate").
        await designPruefung(page, testInfo, 'projekt-uebersicht-lange-titel', { strengePruefungen: true });
    });
});
