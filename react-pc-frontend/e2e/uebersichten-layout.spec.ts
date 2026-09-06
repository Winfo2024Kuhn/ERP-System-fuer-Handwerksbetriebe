import type { Locator, Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung, keinHorizontalerUeberlauf } from './hilfen/design';

/**
 * Task 9 (Abschnitt 5) aus docs/superpowers/plans/2026-09-05-layout-14-zoll.md:
 * Sicherheitsnetz der Spec-Abnahme Punkt 4. Keine der vier Uebersichten
 * (Projekte, Anfragen, Kunden, Lieferanten) wird hier neu gebaut -- alle vier
 * wurden bereits in Abschnitt 3/4 (Task 3, 4, 5, 6) umgebaut. Diese Spec
 * prueft sie alle vier in EINEM Durchlauf, mit einer Fixture, die lange UND
 * kurze Titel MISCHT (Vorgabe "Zusaetzlich in Task 9"), und haelt zwei Dinge
 * fest, die die einzelnen Task-Specs bisher nicht (oder nur teilweise)
 * geprueft haben:
 *
 *  1. Kartenhoehe RICHTIG: zwei Karten derselben Reihe mit unterschiedlich
 *     langem Titel haben dieselbe Kartenhoehe UND ihre Trennlinie (der
 *     mt-auto-Meta-Block) liegt auf derselben y-Position. Genau daran ist im
 *     Design-Review von Abschnitt 4 aufgefallen, dass mt-auto in fuenf von
 *     sechs Karten wirkungslos war, weil space-y-3 (Spezifitaet 0-3-0) es
 *     niederschlaegt -- ein bloss "Kartentitel ohne Luecke"-Test haette das
 *     nicht gesehen (siehe .claude/skills/loese-problem/references/
 *     kriterien.md).
 *  2. Drei Karten je Reihe bei 1440 (pc-14zoll), vier bei 1920 (pc-monitor).
 *
 * Kein `src/`-Code wird hier angefasst -- findet diese Spec etwas, das in
 * `src/` liegt, gehoert der Befund ins Kontext-Log und der Fix in den Task,
 * dem die Datei gehoert (3, 4, 5 oder 6), nicht hierher.
 *
 * /api vollstaendig gestubbt (Catch-all + gezielte Overrides je Seite,
 * Vorbild stubbeLieferantApi in e2e/bearbeiten-leiste.spec.ts), kein
 * Backend, nur Fantasienamen (DSGVO). Zusaetzlich blockiereFremdeNetzwerk-
 * zugriffe() (e2e/hilfen/api.ts) vor jeder Navigation -- index.html laedt
 * pdf.js bei jedem Seitenaufruf von cdnjs.cloudflare.com (Befund aus dem
 * Abschnitt-4-Review), unabhaengig von der Route. Diese Spec bleibt bewusst
 * fuer sich lesbar: keine Stub-Funktionen aus den Task-3-6-Specs importiert,
 * auch wenn die Route-Listen dort Vorbild waren.
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const AUTH_ME = {
    id: 1, username: 'anna.buero', displayName: 'Anna Büro',
    active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
};
const NOTIFICATIONS_LEER = { totalCount: 0, categories: [], recentItems: [] };

/** Gruppiert Rechtecke mit (fast) gleicher y-Koordinate zu Zeilen, oben nach unten. */
function zeilenGroessen(boxen: { y: number }[], toleranz = 3): number[] {
    const sortiert = [...boxen].sort((a, b) => a.y - b.y);
    const zeilen: number[][] = [];
    for (const box of sortiert) {
        const zeile = zeilen.find((z) => Math.abs(z[0] - box.y) <= toleranz);
        if (zeile) zeile.push(box.y);
        else zeilen.push([box.y]);
    }
    return zeilen.map((z) => z.length);
}

/** Die gemeinsame <Card> (erkennbar an "shadow-sm") oberhalb eines Elements der Karte. */
function kartenBox(locator: Locator): Locator {
    return locator.locator(
        'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " shadow-sm ")][1]',
    );
}

/** Die Trennlinie/der Meta-Block (erkennbar an "mt-auto") oberhalb eines Elements mit bekanntem Text. */
function metaZeile(page: Page, text: string): Locator {
    return page.getByText(text, { exact: true }).locator(
        'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " mt-auto ")][1]',
    );
}

/**
 * Vergleicht zwei Karten derselben Reihe (kurzer und langer Titel): gleiche
 * Kartenhoehe UND ihre Trennlinie auf gleicher y-Position. Das ist die
 * Zusicherung, die im Design-Review von Abschnitt 4 gefehlt hat -- die
 * bisherigen Kurztitel-Tests maszen nur, dass min-h-[3rem] weg ist, nicht ob
 * mt-auto am Meta-Block wirklich greift (space-y-3 schlaegt es sonst nieder,
 * siehe kriterien.md).
 */
async function pruefeGleicheKartenhoeheUndTrennlinie(
    page: Page,
    kurzerTitel: string,
    kurzeMetaZeile: string,
    langerTitel: string,
    langeMetaZeile: string,
): Promise<void> {
    const kurzeKarte = kartenBox(page.getByRole('heading', { level: 3, name: kurzerTitel, exact: true }));
    const langeKarte = kartenBox(page.getByRole('heading', { level: 3, name: langerTitel, exact: true }));
    const kurzeKarteBox = await kurzeKarte.boundingBox();
    const langeKarteBox = await langeKarte.boundingBox();
    expect(kurzeKarteBox, `Karte "${kurzerTitel}" muss einen messbaren Rahmen haben`).not.toBeNull();
    expect(langeKarteBox, `Karte "${langerTitel}" muss einen messbaren Rahmen haben`).not.toBeNull();
    expect(
        Math.abs(kurzeKarteBox!.height - langeKarteBox!.height),
        `Karten "${kurzerTitel}" (${kurzeKarteBox!.height.toFixed(0)}px) und "${langerTitel}" (${langeKarteBox!.height.toFixed(0)}px) sind unterschiedlich hoch`,
    ).toBeLessThanOrEqual(2);

    const kurzeMetaBox = await metaZeile(page, kurzeMetaZeile).boundingBox();
    const langeMetaBox = await metaZeile(page, langeMetaZeile).boundingBox();
    expect(kurzeMetaBox, `Meta-Zeile "${kurzeMetaZeile}" muss einen messbaren Rahmen haben`).not.toBeNull();
    expect(langeMetaBox, `Meta-Zeile "${langeMetaZeile}" muss einen messbaren Rahmen haben`).not.toBeNull();
    expect(
        Math.abs(kurzeMetaBox!.y - langeMetaBox!.y),
        `Trennlinie liegt bei "${kurzerTitel}" (y=${kurzeMetaBox!.y.toFixed(0)}) und "${langerTitel}" (y=${langeMetaBox!.y.toFixed(0)}) nicht auf gleicher Hoehe -- mt-auto wirkt nicht (space-y-3-Spezifitaet?)`,
    ).toBeLessThanOrEqual(2);
}

// ==================== PROJEKTE ====================

const PROJEKTE_MIX = [
    { id: 301, bauvorhaben: 'Carport', kunde: 'Meier Bau GmbH', auftragsnummer: 'A-2026-9301' },
    { id: 302, bauvorhaben: 'Treppenanlage mit Podest und Absturzsicherung Bürogebäude Beispielstraße', kunde: 'Wohnungsbaugesellschaft Beispielstadt Nord mbH und Co. Verwaltungs KG', auftragsnummer: 'A-2026-9302' },
    { id: 303, bauvorhaben: 'Terrassenüberdachung mit Glasschiebewänden am Musterweiher', kunde: 'Wohnbau Beispielstadt eG', auftragsnummer: 'A-2026-9303' },
    { id: 304, bauvorhaben: 'Balkonanlage mit Absturzsicherung Wohnanlage Musterring', kunde: 'Beispiel Immobilien KG', auftragsnummer: 'A-2026-9304' },
].map((p, i) => ({
    ...p,
    anlegedatum: '2026-01-15',
    bruttoPreis: 42000 + i * 1000,
    bezahlt: false,
    abgeschlossen: false,
}));

async function stubProjekteUebersicht(page: Page): Promise<void> {
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        if (pfad === '/api/auth/me') return json(route, AUTH_ME);
        if (pfad === '/api/notifications/summary') return json(route, NOTIFICATIONS_LEER);
        if (/^\/api\/last-accessed\/PROJEKT(\/\d+)?$/.test(pfad)) {
            if (methode === 'POST') return route.fulfill({ status: 204, body: '' });
            return json(route, {});
        }
        if (pfad === '/api/projekte' && methode === 'GET') return json(route, { projekte: PROJEKTE_MIX, gesamt: PROJEKTE_MIX.length });
        if (pfad === '/api/projekte/jahre') return json(route, [2026]);
        if (pfad === '/api/projekte/freigabe-status') return json(route, {});
        return json(route, []);
    });
}

// ==================== ANFRAGEN ====================

const ANFRAGEN_MIX = [
    { id: 401, bauvorhaben: 'Zaun', kundenName: 'Beispiel GmbH', anfragesnummer: 'AG-2026/09/09401', betrag: 3200, anlegedatum: '2026-02-01', abgeschlossen: false },
    { id: 402, bauvorhaben: 'Treppenanlage mit Podest und Absturzsicherung Bürogebäude Beispielstraße', kundenName: 'Wohnungsbaugesellschaft Beispielstadt Nord mbH und Co. Verwaltungs KG', anfragesnummer: 'AG-2026/09/09402', betrag: 45000, anlegedatum: '2026-02-02', abgeschlossen: false },
    { id: 403, bauvorhaben: 'Terrassenüberdachung mit Glasdach und Sonnenschutz Musterhof Nordflügel', kundenName: 'Musterbau Nordflügel GmbH', anfragesnummer: 'AG-2026/09/09403', betrag: 8600, anlegedatum: '2026-02-03', abgeschlossen: false },
    { id: 404, bauvorhaben: 'Geländer und Handlauf Dachterrasse Mehrfamilienhaus Beispielallee Süd', kundenName: 'Wohnbau Beispielallee eG', anfragesnummer: 'AG-2026/09/09404', betrag: 5400, anlegedatum: '2026-02-04', abgeschlossen: false },
];

async function stubAnfragenUebersicht(page: Page): Promise<void> {
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        if (pfad === '/api/auth/me') return json(route, AUTH_ME);
        if (pfad === '/api/notifications/summary') return json(route, NOTIFICATIONS_LEER);
        if (/^\/api\/last-accessed\/ANFRAGE(\/\d+)?$/.test(pfad)) {
            if (methode === 'POST') return route.fulfill({ status: 204, body: '' });
            return json(route, {});
        }
        if (pfad === '/api/anfragen' && methode === 'GET') return json(route, { anfragen: ANFRAGEN_MIX, gesamt: ANFRAGEN_MIX.length });
        if (pfad === '/api/anfragen/jahre') return json(route, []);
        if (pfad === '/api/anfragen/funnel-ids') return json(route, []);
        if (pfad === '/api/anfragen/freigabe-status') return json(route, {});
        return json(route, []);
    });
}

// ==================== KUNDEN ====================

const KUNDEN_MIX = [
    { id: 501, kundennummer: 'K-9501', name: 'Meier', plz: '30159', ort: 'Hannover', ansprechspartner: 'Klaus Meier', hatProjekte: true },
    { id: 502, kundennummer: 'K-9502', name: 'Wohnungsbaugesellschaft Beispielstadt Nord mbH und Co. Verwaltungs KG', plz: '30159', ort: 'Hannover', ansprechspartner: 'Erika Musterfrau', hatProjekte: true },
    { id: 503, kundennummer: 'K-9503', name: 'Bauträgergesellschaft Musterhausen Süd mbH & Co. Projektentwicklungs KG', plz: '30159', ort: 'Hannover', ansprechspartner: 'Peter Muster', hatProjekte: false },
    { id: 504, kundennummer: 'K-9504', name: 'Hausverwaltung und Instandhaltung Beispieldorf-West Genossenschaft eG', plz: '30159', ort: 'Hannover', ansprechspartner: 'Anna Beispiel', hatProjekte: true },
];

async function stubKundenUebersicht(page: Page): Promise<void> {
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        if (pfad === '/api/auth/me') return json(route, AUTH_ME);
        if (pfad === '/api/notifications/summary') return json(route, NOTIFICATIONS_LEER);
        if (pfad === '/api/kunden' && methode === 'GET') return json(route, { kunden: KUNDEN_MIX, gesamt: KUNDEN_MIX.length });
        return json(route, []);
    });
}

// ==================== LIEFERANTEN ====================

const LIEFERANTEN_MIX = [
    { id: 601, lieferantenname: 'Stahlbau Nord', lieferantenTyp: 'STAHL', rollen: [] as string[], ort: 'Hannover', telefon: '0511 1111111', vertreter: 'Hans Nord' },
    { id: 602, lieferantenname: 'Stahlhandel Beispiel GmbH und Co. KG', lieferantenTyp: 'STAHL', rollen: [] as string[], ort: 'Hannover', telefon: '0511 2222222', vertreter: 'Hans Beispiel' },
    { id: 603, lieferantenname: 'Baustoffhandel Beispielstadt Nord und Umgebung GmbH', lieferantenTyp: 'STAHL', rollen: [] as string[], ort: 'Hannover', telefon: '0511 3333333', vertreter: 'Petra Beispiel' },
    { id: 604, lieferantenname: 'Elektrogroßhandel Mustertal Handelsgesellschaft mbH', lieferantenTyp: 'STAHL', rollen: [] as string[], ort: 'Hannover', telefon: '0511 4444444', vertreter: 'Jonas Mustertal' },
];

async function stubLieferantenUebersicht(page: Page): Promise<void> {
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        if (pfad === '/api/auth/me') return json(route, AUTH_ME);
        if (pfad === '/api/notifications/summary') return json(route, NOTIFICATIONS_LEER);
        if (pfad === '/api/lieferanten' && methode === 'GET') return json(route, { lieferanten: LIEFERANTEN_MIX, gesamt: LIEFERANTEN_MIX.length });
        return json(route, []);
    });
}

// ==================== TESTS ====================

test.describe('Uebersichten-Abnahme (Task 9): Projekte, Anfragen, Kunden, Lieferanten', () => {
    test('Projekte-Uebersicht: gemischt lange/kurze Bauvorhaben', async ({ page }, testInfo) => {
        await stubProjekteUebersicht(page);
        await page.goto('/projekte');
        await expect(page.getByRole('heading', { name: 'Projektübersicht' })).toBeVisible();

        const titelBoxen: { y: number }[] = [];
        for (const p of PROJEKTE_MIX) {
            const titel = page.getByRole('heading', { level: 3, name: p.bauvorhaben, exact: true });
            await expect(titel, `Titel "${p.bauvorhaben}" fehlt`).toBeVisible();
            const box = await titel.boundingBox();
            expect(box, `Titel "${p.bauvorhaben}" muss einen messbaren Rahmen haben`).not.toBeNull();
            titelBoxen.push(box!);
        }
        const erwarteteSpalten = testInfo.project.name === 'pc-monitor' ? 4 : 3;
        const zeilen = zeilenGroessen(titelBoxen);
        expect(
            zeilen[0],
            `Erste Kartenreihe bei ${testInfo.project.name} hat ${zeilen[0]} Karte(n), erwartet ${erwarteteSpalten} (Zeilen: ${zeilen.join(', ')})`,
        ).toBe(erwarteteSpalten);

        await pruefeGleicheKartenhoeheUndTrennlinie(
            page,
            PROJEKTE_MIX[0].bauvorhaben, PROJEKTE_MIX[0].auftragsnummer,
            PROJEKTE_MIX[1].bauvorhaben, PROJEKTE_MIX[1].auftragsnummer,
        );

        await keinHorizontalerUeberlauf(page);
        await designPruefung(page, testInfo, 'uebersichten-projekte-gemischt', {
            strengePruefungen: true,
            primaerAktion: page.getByRole('button', { name: 'Neues Projekt' }),
        });
    });

    test('Anfragen-Uebersicht: gemischt lange/kurze Bauvorhaben', async ({ page }, testInfo) => {
        await stubAnfragenUebersicht(page);
        await page.goto('/anfragen');
        await expect(page.getByRole('heading', { name: 'ANFRAGENÜBERSICHT' })).toBeVisible();

        const titelBoxen: { y: number }[] = [];
        for (const a of ANFRAGEN_MIX) {
            const titel = page.getByRole('heading', { level: 3, name: a.bauvorhaben, exact: true });
            await expect(titel, `Titel "${a.bauvorhaben}" fehlt`).toBeVisible();
            const box = await titel.boundingBox();
            expect(box, `Titel "${a.bauvorhaben}" muss einen messbaren Rahmen haben`).not.toBeNull();
            titelBoxen.push(box!);
        }
        const erwarteteSpalten = testInfo.project.name === 'pc-monitor' ? 4 : 3;
        const zeilen = zeilenGroessen(titelBoxen);
        expect(
            zeilen[0],
            `Erste Kartenreihe bei ${testInfo.project.name} hat ${zeilen[0]} Karte(n), erwartet ${erwarteteSpalten} (Zeilen: ${zeilen.join(', ')})`,
        ).toBe(erwarteteSpalten);

        await pruefeGleicheKartenhoeheUndTrennlinie(
            page,
            ANFRAGEN_MIX[0].bauvorhaben, ANFRAGEN_MIX[0].anfragesnummer,
            ANFRAGEN_MIX[1].bauvorhaben, ANFRAGEN_MIX[1].anfragesnummer,
        );

        await keinHorizontalerUeberlauf(page);
        await designPruefung(page, testInfo, 'uebersichten-anfragen-gemischt', {
            strengePruefungen: true,
            primaerAktion: page.getByRole('button', { name: 'Neue Anfrage' }),
        });
    });

    test('Kunden-Uebersicht: gemischt lange/kurze Namen', async ({ page }, testInfo) => {
        await stubKundenUebersicht(page);
        await page.goto('/kunden');
        await expect(page.getByRole('heading', { name: 'KUNDENÜBERSICHT' })).toBeVisible();

        const titelBoxen: { y: number }[] = [];
        for (const k of KUNDEN_MIX) {
            const titel = page.getByRole('heading', { level: 3, name: k.name, exact: true });
            await expect(titel, `Titel "${k.name}" fehlt`).toBeVisible();
            const box = await titel.boundingBox();
            expect(box, `Titel "${k.name}" muss einen messbaren Rahmen haben`).not.toBeNull();
            titelBoxen.push(box!);
        }
        const erwarteteSpalten = testInfo.project.name === 'pc-monitor' ? 4 : 3;
        const zeilen = zeilenGroessen(titelBoxen);
        expect(
            zeilen[0],
            `Erste Kartenreihe bei ${testInfo.project.name} hat ${zeilen[0]} Karte(n), erwartet ${erwarteteSpalten} (Zeilen: ${zeilen.join(', ')})`,
        ).toBe(erwarteteSpalten);

        await pruefeGleicheKartenhoeheUndTrennlinie(
            page,
            KUNDEN_MIX[0].name, KUNDEN_MIX[0].ansprechspartner,
            KUNDEN_MIX[1].name, KUNDEN_MIX[1].ansprechspartner,
        );

        await keinHorizontalerUeberlauf(page);
        await designPruefung(page, testInfo, 'uebersichten-kunden-gemischt', {
            strengePruefungen: true,
            primaerAktion: page.getByRole('button', { name: 'Neuer Kunde' }),
        });
    });

    test('Lieferanten-Uebersicht: gemischt lange/kurze Namen', async ({ page }, testInfo) => {
        await stubLieferantenUebersicht(page);
        await page.goto('/lieferanten');
        await expect(page.getByRole('heading', { name: 'LIEFERANTENÜBERSICHT' })).toBeVisible();

        const titelBoxen: { y: number }[] = [];
        for (const l of LIEFERANTEN_MIX) {
            const titel = page.getByRole('heading', { level: 3, name: l.lieferantenname, exact: true });
            await expect(titel, `Titel "${l.lieferantenname}" fehlt`).toBeVisible();
            const box = await titel.boundingBox();
            expect(box, `Titel "${l.lieferantenname}" muss einen messbaren Rahmen haben`).not.toBeNull();
            titelBoxen.push(box!);
        }
        const erwarteteSpalten = testInfo.project.name === 'pc-monitor' ? 4 : 3;
        const zeilen = zeilenGroessen(titelBoxen);
        expect(
            zeilen[0],
            `Erste Kartenreihe bei ${testInfo.project.name} hat ${zeilen[0]} Karte(n), erwartet ${erwarteteSpalten} (Zeilen: ${zeilen.join(', ')})`,
        ).toBe(erwarteteSpalten);

        // Task 11 (Abschnitt 7): MIX[1] (36 Zeichen) ist bei BEIDEN Groessen
        // genauso einzeilig wie MIX[0] (13 Zeichen) und verschiebt sich mit ihm
        // gemeinsam -- die Mutationsprobe des Design-Reviewers (gap-3 ->
        // space-y-3) blieb deshalb gruen (Kontext-Log Abschnitt 6). MIX[2]
        // (51 Zeichen) ist dagegen in beiden Groessen zweizeilig und liegt bei
        // 1440 in derselben Reihe -- ein Vergleich MIX[0] gegen MIX[2] haelt die
        // Mutation fest (siehe Kontext-Log-Block dieses Tasks fuer die
        // wiederholte Mutationsprobe).
        await pruefeGleicheKartenhoeheUndTrennlinie(
            page,
            LIEFERANTEN_MIX[0].lieferantenname, LIEFERANTEN_MIX[0].vertreter,
            LIEFERANTEN_MIX[2].lieferantenname, LIEFERANTEN_MIX[2].vertreter,
        );

        await keinHorizontalerUeberlauf(page);
        await designPruefung(page, testInfo, 'uebersichten-lieferanten-gemischt', {
            strengePruefungen: true,
            primaerAktion: page.getByRole('button', { name: 'Neuer Lieferant' }),
        });
    });
});
