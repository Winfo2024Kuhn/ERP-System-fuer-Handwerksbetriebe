import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
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

    // Nachtrag Abschnitt 5 (Task 9), Bedenken aus dem Code-Review von
    // Abschnitt 4 (Hinweis 4 der Runde 2): die Trennlinien-Zusicherung
    // (space-y-3 -> gap-3, damit mt-auto am Meta-Block wirklich greift)
    // stand bisher nur in kunde-layout.spec.ts. Fuer ProjektCard gab es
    // ueberhaupt keinen "kurzer Titel"-Testfall -- ein Rueckfall auf
    // space-y-3 waere hier unbemerkt geblieben. Zwei Projekte in derselben
    // Reihe (kurzer und langer Titel): beide muessen dieselbe Kartenhoehe
    // UND dieselbe y-Position der Trennlinie (Meta-Block mit mt-auto) haben.
    test('kurzes Bauvorhaben reisst keine Luecke, Trennlinie bleibt auf Hoehe der Nachbarkarte', async ({ page }) => {
        const KURZ = { id: 201, bauvorhaben: 'Carport', kunde: 'Meier Bau GmbH', auftragsnummer: 'A-2026-9201', anlegedatum: '2026-02-10', bruttoPreis: 4200, bezahlt: false, abgeschlossen: false };
        const LANG = { id: 202, bauvorhaben: 'Treppenanlage mit Podest und Absturzsicherung Bürogebäude Beispielstraße', kunde: 'Beispielbau Nord GmbH', auftragsnummer: 'A-2026-9202', anlegedatum: '2026-02-10', bruttoPreis: 125000, bezahlt: false, abgeschlossen: false };

        await page.route('**/api/**', (route) => {
            const pfad = new URL(route.request().url()).pathname;
            const methode = route.request().method();
            if (pfad === '/api/auth/me') {
                return json(route, {
                    id: 1, username: 'anna.buero', displayName: 'Anna Büro',
                    active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
                });
            }
            if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
            if (/^\/api\/last-accessed\/PROJEKT(\/\d+)?$/.test(pfad)) {
                if (methode === 'POST') return route.fulfill({ status: 204, body: '' });
                return json(route, {});
            }
            if (pfad === '/api/projekte' && methode === 'GET') return json(route, { projekte: [KURZ, LANG], gesamt: 2 });
            if (pfad === '/api/projekte/jahre') return json(route, [2026]);
            if (pfad === '/api/projekte/freigabe-status') return json(route, {});
            return json(route, []);
        });
        await page.goto('/projekte');

        const titelKurz = page.getByRole('heading', { level: 3, name: KURZ.bauvorhaben, exact: true });
        await expect(titelKurz).toBeVisible();

        // min-h-[3rem] reserviert den Platz INNERHALB der eigenen Titel-Box
        // (48px Boxhoehe fuer 24px einzeiligen Text) -- die Boxhoehe selbst
        // ist der richtige Messpunkt (siehe kunde-layout.spec.ts / Design-
        // Review-Befund aus Abschnitt 4): mit min-h-[3rem] 48px, ohne
        // (h-full flex flex-col + mt-auto am Meta-Block) 24px.
        const titelBox = await titelKurz.boundingBox();
        expect(titelBox, 'Titel muss einen messbaren Rahmen haben').not.toBeNull();
        expect(
            titelBox!.height,
            `Titel-Box ist ${titelBox!.height.toFixed(0)}px hoch fuer einzeiligen Text -- min-h-[3rem] (48px) reisst hier eine Luecke`,
        ).toBeLessThan(32);

        // Trennlinie: die Auftragsnummer ist die erste Zeile des mt-auto-
        // Meta-Blocks. space-y-3 (Spezifitaet 0-3-0) schlaegt mt-auto
        // (0-1-0) nieder -- ohne gap-3 waere die Trennlinie beim kurzen
        // Titel 24px hoeher als beim langen (Design-Review Abschnitt 4).
        const nummerKurz = page.getByText(KURZ.auftragsnummer, { exact: true });
        const nummerLang = page.getByText(LANG.auftragsnummer, { exact: true });
        await expect(nummerKurz).toBeVisible();
        await expect(nummerLang).toBeVisible();
        const boxKurz = await nummerKurz.boundingBox();
        const boxLang = await nummerLang.boundingBox();
        expect(boxKurz, 'Auftragsnummer der kurzen Karte muss einen messbaren Rahmen haben').not.toBeNull();
        expect(boxLang, 'Auftragsnummer der langen Karte muss einen messbaren Rahmen haben').not.toBeNull();
        const versatz = Math.abs(boxKurz!.y - boxLang!.y);
        expect(
            versatz,
            `Trennlinien-Meta-Zeilen sind ${versatz.toFixed(0)}px versetzt (y-Werte: ${boxKurz!.y.toFixed(0)}, ${boxLang!.y.toFixed(0)}) -- mt-auto wirkt nicht (space-y-3-Spezifitaet?)`,
        ).toBeLessThanOrEqual(2);
    });
});
