import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { spacelosesWort } from './hilfen/testdaten';

/**
 * Task 10: select-custom.tsx reparieren. Diese Spec ist die verbindliche
 * Zusicherung fuer das Konto-Dropdown in "Belege & Kasse" -- es soll mit
 * langen Sachkonto-Bezeichnungen weder Optionen abschneiden noch aus dem
 * Viewport ragen, und am unteren Bildschirmrand nach oben aufklappen.
 *
 * Alle Namen sind Dummy-Daten (DSGVO).
 */

const LANGE_BEZEICHNUNG = 'Bürobedarf und Zeitschriften'; // ergibt zusammen mit dem Praefix "Aufwand · 4930 " genau das im Plan geforderte Label
// Bindestrichloses Testwort -- siehe kriterien.md "Layout: sechs Fallen". Der
// Plan nennt spacelosesWort(48, 'Konto'), aber bei 48 Zeichen (~340px bei
// text-sm) bleibt das Wort unter der festen 480px-Deckelung und die
// min-w-0-Gegenprobe (siehe unten) bleibt wirkungslos -- ohne echten Zwang
// zum Schrumpfen unter die natuerliche Wortbreite greift der Unterschied
// zwischen min-w-0 und dessen Fehlen gar nicht. 120 Zeichen (~700-850px)
// liegt sicher darueber. Abweichung von der Zahl im Plan, siehe Kontext-Log.
const SPACELOSES_WORT = spacelosesWort(120, 'Konto');

/** 1x1-PNG als Data-URL -- reicht als Belegbild fuer die Vorschau (siehe e2e/hilfen/api.ts PIXEL_PNG). */
const PIXEL_PNG_BYTES = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
    'base64',
);

const BELEG = {
    id: 501,
    belegKategorie: 'SONSTIGER_BELEG' as const,
    status: 'NEU' as const,
    kiAnalyseStatus: 'DONE' as const,
    belegDatum: '2026-09-01',
    belegNummer: 'BEL-2026-0501',
    beschreibung: 'Werkzeugkauf Baumarkt',
    betragNetto: 42.5,
    betragBrutto: 50.57,
    mwstSatz: 19,
    mimeType: 'image/png',
    originalDateiname: 'kassenbeleg.png',
    uploadDatum: '2026-09-01T08:00:00',
    sachkontoId: null,
};

// Mehrere Sachkonten, damit das Panel genug Inhalt hat, um sowohl in die
// Breite (langes Label, bindestrichloses Wort) als auch in die Hoehe
// (mehrere Zeilen -> Hochklapp-Test) zu geraten.
const SACHKONTEN = [
    { id: 1, nummer: '4930', bezeichnung: LANGE_BEZEICHNUNG, kontoTyp: 'AUFWAND', aktiv: true, sortierung: 1 },
    { id: 2, nummer: '4400', bezeichnung: SPACELOSES_WORT, kontoTyp: 'ERTRAG', aktiv: true, sortierung: 1 },
    { id: 3, nummer: '4600', bezeichnung: 'Fahrzeugkosten', kontoTyp: 'AUFWAND', aktiv: true, sortierung: 2 },
    { id: 4, nummer: '4830', bezeichnung: 'Werbekosten und Repräsentation', kontoTyp: 'AUFWAND', aktiv: true, sortierung: 3 },
    { id: 5, nummer: '8400', bezeichnung: 'Erlöse aus Dienstleistungen', kontoTyp: 'ERTRAG', aktiv: true, sortierung: 2 },
    { id: 6, nummer: '1890', bezeichnung: 'Privatentnahme allgemein', kontoTyp: 'PRIVAT', aktiv: true, sortierung: 1 },
];

async function stub(page: Page, sachkonten: typeof SACHKONTEN) {
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        const path = url.pathname;
        const json = (body: unknown, status = 200) =>
            route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

        if (path === '/api/auth/me') {
            return json({
                id: 70, username: 'buchhaltung@example.com', displayName: 'Max Mustermann',
                active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
            });
        }
        if (path === '/api/notifications/summary') return json({ totalCount: 0, categories: [], recentItems: [] });
        if (path === `/api/buchhaltung/belege/${BELEG.id}/datei`) {
            return route.fulfill({ status: 200, contentType: 'image/png', body: PIXEL_PNG_BYTES });
        }
        if (path === '/api/buchhaltung/belege') return json([BELEG]);
        if (path === '/api/buchhaltung/sachkonten') return json(sachkonten);
        if (path === '/api/buchhaltung/zahlungsarten') return json([]);
        if (path === '/api/buchhaltung/kasse/saldo') return json({ saldo: 0, mindestbestand: 0 });
        return json([]);
    });
}

/** Oeffnet den Beleg und liefert den (noch nicht geklickten) Konto-Ausloeser. */
async function oeffneBelegUndFindeKontoAusloeser(page: Page, sachkonten: typeof SACHKONTEN) {
    await stub(page, sachkonten);
    await page.goto('/belege-kasse');
    await page.getByText(BELEG.belegNummer, { exact: true }).click();
    // Der Select zeigt hier die erste Option ("– kein Konto –") statt des
    // eigenen placeholder-Props, weil buildSachkontoOptions() selbst einen
    // Leer-Eintrag mit value "" fuehrt und form.sachkontoId anfangs "" ist.
    const ausloeser = page.getByText('– kein Konto –', { exact: true });
    await expect(ausloeser).toBeVisible();
    return ausloeser;
}

test('Sachkonto-Dropdown waechst mit dem Inhalt und bleibt vollstaendig im Viewport', async ({ page }, info) => {
    const ausloeser = await oeffneBelegUndFindeKontoAusloeser(page, SACHKONTEN);
    await ausloeser.click();

    const panel = page.getByRole('listbox');
    await expect(panel).toBeVisible();

    // 1) Keine Option ist abgeschnitten.
    const optionen = panel.getByRole('option');
    for (let i = 0; i < await optionen.count(); i++) {
        const masse = await optionen.nth(i).evaluate(el => ({
            scroll: el.scrollWidth, client: el.clientWidth, text: el.textContent,
        }));
        expect(masse.scroll, `Option "${masse.text}" laeuft ueber`).toBeLessThanOrEqual(masse.client);
    }

    // 2) Das Dropdown liegt vollstaendig im Viewport.
    const rahmen = await panel.boundingBox();
    const sicht = page.viewportSize()!;
    expect(rahmen!.x).toBeGreaterThanOrEqual(0);
    expect(rahmen!.y).toBeGreaterThanOrEqual(0);
    expect(rahmen!.x + rahmen!.width).toBeLessThanOrEqual(sicht.width);
    expect(rahmen!.y + rahmen!.height).toBeLessThanOrEqual(sicht.height);

    await designPruefung(page, info, 'dropdown-offen');
});

test('Sachkonto-Dropdown klappt am unteren Bildschirmrand nach oben auf', async ({ page }) => {
    // Wenige Konten: das Panel bleibt kurz, damit sich in einem verkuerzten
    // Viewport zuverlaessig "oben genug Platz, unten zu wenig" einstellt.
    const ausloeser = await oeffneBelegUndFindeKontoAusloeser(page, SACHKONTEN.slice(0, 2));

    // Der Modal-Dialog ist vertikal zentriert (items-center) und wird ab
    // max-h-[95vh] intern gestaucht/scrollbar -- ein ausreichend kurzer
    // Viewport sorgt dafuer, dass der Ausloeser nah am unteren Rand landet,
    // unabhaengig von der konkreten Bildschirmgroesse des Playwright-Projekts.
    const urspruenglich = page.viewportSize()!;
    await page.setViewportSize({ width: urspruenglich.width, height: 380 });
    const ausloeserRahmen = (await ausloeser.boundingBox())!;

    await ausloeser.click();
    const panel = page.getByRole('listbox');
    await expect(panel).toBeVisible();
    const rahmen = (await panel.boundingBox())!;

    expect(rahmen.y, 'Panel soll nicht ueber den oberen Bildschirmrand ragen').toBeGreaterThanOrEqual(0);
    expect(
        rahmen.y + rahmen.height,
        'Panel soll oberhalb des Ausloesers enden (Hochklappen statt Abschneiden am unteren Rand)',
    ).toBeLessThanOrEqual(ausloeserRahmen.y);
});
