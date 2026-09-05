import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung, keinTextGekuerzt } from './hilfen/design';

/**
 * End-to-End-Test fuer die Menueleiste (RibbonNav.tsx) auf 14 Zoll (Spec C,
 * docs/superpowers/specs/2026-09-04-layout-14-zoll.md, Befund 3).
 *
 * Heutiger Zustand (vor dem Fix):
 *   - Kategorie-Leiste (Zeile 249): "no-scrollbar" versteckt einen echten
 *     Ueberlauf des Container-Elements (die fuenf Kategorie-Knoepfe passen bei
 *     1440px nicht nebeneinander, 38px fehlen), niemand sieht den Scrollbalken.
 *   - Menuepunkt-Beschriftungen (Zeile 400, "max-w-[4.5rem] truncate") kuerzen
 *     "Dokumentenrechte" und "Mietabrechnung" unabhaengig von der
 *     Fenstergroesse -- betrifft also auch pc-monitor (1920).
 *   - Der Anzeigename (Zeile 299, "line-clamp-1", keine Hoechstbreite) laesst
 *     bei einem langen Namen die Kategorie-Leiste zusaetzlich nach links
 *     wandern, weil er unbegrenzt Platz beansprucht.
 *
 * Alle drei Befunde werden mit einem langen Nutzernamen provoziert
 * (Friederike Beispiel-Musterfrau, Fantasiename -- DSGVO) und admin: true,
 * weil "Dokumentenrechte" an ADMIN_ONLY_PATHS haengt (RibbonNav.tsx Zeile 174)
 * und ohne Admin-Rechte gar nicht sichtbar waere.
 *
 * /api wird vollstaendig gestubbt (kein Backend, keine echten Personendaten).
 * Landeseite ist /projekte (siehe Task 2 fuer dieselben Stub-Routen) -- Task 8
 * ist laut Plan unabhaengig von Task 2 und aendert an ProjektEditor/
 * DetailLayout/MainLayout nichts.
 */

const LANGER_NUTZERNAME = 'Friederike Beispiel-Musterfrau';

const KATEGORIEN = [
    'Vorlagen & Stammdaten',
    'Projektmanagement',
    'Zeiterfassung',
    'Kommunikation',
    'Finanzen & Controlling',
];

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

/**
 * Stubbt /api fuer die Landeseite /projekte samt Menueleiste. Ein
 * grosszuegiger Default zuerst, gezielte Routen danach -- Playwright wertet
 * zuletzt registrierte passende Routen zuerst aus, die gezielten Antworten
 * ueberschreiben also den Default (Vorbild: stubbeLieferantApi in
 * e2e/bearbeiten-leiste.spec.ts).
 */
async function stubbeMenueleisteApi(page: Page) {
    await page.route('**/api/**', route => json(route, {}));

    await page.route('**/api/auth/me', route => json(route, {
        id: 1,
        displayName: LANGER_NUTZERNAME,
        username: 'friederike.beispiel',
        active: true,
        roles: ['ADMIN'],
        admin: true,
        requiresInitialSetup: false,
    }));

    await page.route('**/api/notifications/summary**', route => json(route, {
        totalCount: 0,
        categories: [],
        recentItems: [],
    }));

    // GET liefert die Zuletzt-aufgerufen-Stempel, POST /PROJEKT/{id} schreibt
    // einen neuen -- fuer die leere Liste hier nicht gebraucht, aber
    // fire-and-forget-sicher gestubbt, falls doch aufgerufen.
    await page.route('**/api/last-accessed/PROJEKT**', route => {
        if (route.request().method() === 'POST') return route.fulfill({ status: 200, body: '' });
        return json(route, {});
    });

    await page.route('**/api/projekte**', route => {
        const pfad = new URL(route.request().url()).pathname;
        if (pfad === '/api/projekte/jahre') return json(route, []);
        if (pfad === '/api/projekte/freigabe-status') return json(route, {});
        if (pfad === '/api/projekte') return json(route, { projekte: [], gesamt: 0 });
        return json(route, {});
    });
}

async function oeffneProjekteMitMenueleiste(page: Page) {
    await stubbeMenueleisteApi(page);
    await page.goto('/projekte');
    await expect(page.getByRole('heading', { name: 'Projektübersicht' })).toBeVisible();
}

/**
 * Misst den Container der fuenf Kategorie-Knoepfe direkt (RibbonNav.tsx,
 * "Category Tabs"-div). keinHorizontalerUeberlauf faengt das nicht ab: der
 * Container hat "overflow-x: auto", nicht "hidden" -- der Ueberlauf ist
 * technisch scrollbar, nur unsichtbar wegen "no-scrollbar".
 */
async function kategorieLeisteMasse(page: Page) {
    return page.evaluate((ersteKategorie) => {
        const button = Array.from(document.querySelectorAll('button'))
            .find(b => b.textContent?.trim() === ersteKategorie);
        const container = button?.parentElement ?? null;
        if (!container) return null;
        return { scrollWidth: container.scrollWidth, clientWidth: container.clientWidth };
    }, KATEGORIEN[0]);
}

test.describe('Menueleiste (RibbonNav): lange Beschriftungen bei 1440 nicht abgeschnitten', () => {
    test('alle fuenf Kategorien vollstaendig lesbar, Kategorie-Leiste ohne Ueberlauf', async ({ page }, testInfo) => {
        await oeffneProjekteMitMenueleiste(page);

        for (const name of KATEGORIEN) {
            await expect(page.getByRole('button', { name, exact: true }), `Kategorie "${name}" fehlt oder ist nicht exakt lesbar`)
                .toHaveText(name);
        }

        const masse = await kategorieLeisteMasse(page);
        expect(masse, 'Kategorie-Leiste (Container der Kategorie-Knoepfe) nicht gefunden').not.toBeNull();
        expect(
            masse!.scrollWidth,
            `Kategorie-Leiste laeuft ueber: Container ${masse!.clientWidth}px breit, Inhalt braucht ${masse!.scrollWidth}px (${masse!.scrollWidth - masse!.clientWidth}px zu wenig Platz)`,
        ).toBeLessThanOrEqual(masse!.clientWidth);

        await designPruefung(page, testInfo, 'menueleiste-kategorien', { strengePruefungen: true });
    });

    test('Kategorie "Vorlagen & Stammdaten": "Dokumentenrechte" steht vollstaendig da', async ({ page }, testInfo) => {
        await oeffneProjekteMitMenueleiste(page);

        await page.getByRole('button', { name: 'Vorlagen & Stammdaten', exact: true }).click();
        const dokumentenrechte = page.getByRole('link', { name: 'Dokumentenrechte' });
        await expect(dokumentenrechte).toBeVisible();
        await keinTextGekuerzt(page);

        await designPruefung(page, testInfo, 'menueleiste-dokumentenrechte', { strengePruefungen: true });
    });

    test('Kategorie "Finanzen & Controlling": "Mietabrechnung" steht vollstaendig da', async ({ page }, testInfo) => {
        await oeffneProjekteMitMenueleiste(page);

        await page.getByRole('button', { name: 'Finanzen & Controlling', exact: true }).click();
        const mietabrechnung = page.getByRole('link', { name: 'Mietabrechnung' });
        await expect(mietabrechnung).toBeVisible();
        await keinTextGekuerzt(page);

        await designPruefung(page, testInfo, 'menueleiste-mietabrechnung', { strengePruefungen: true });
    });

    /**
     * Nicht explizit im Plan-Block gefordert, aber dieselbe Baustelle: Die
     * Begruendung fuer die Kuerzung des Anzeigenamens (Zeile 299) ist, dass
     * der volle Name "im Menue darunter" steht (Zeile 312, Nutzermenue-Panel).
     * Dort stand bisher ebenfalls "line-clamp-1" ohne data-kuerzung-erlaubt --
     * bei einem langen Namen waere die Begruendung falsch, weil auch der
     * "volle" Name dort abgeschnitten wuerde. Deckt denselben Fall ab wie
     * Global-Constraint "jede Kuerzung, die keinTextGekuerzt findet, ist ein
     * Fehler, ausser markiert".
     */
    test('Nutzermenue geoeffnet: voller Anzeigename ohne Kuerzung', async ({ page }, testInfo) => {
        await oeffneProjekteMitMenueleiste(page);

        await page.getByRole('button', { name: LANGER_NUTZERNAME }).click();
        const nutzermenuePanel = page.getByText(LANGER_NUTZERNAME).last();
        await expect(nutzermenuePanel).toBeVisible();
        await keinTextGekuerzt(page);

        await designPruefung(page, testInfo, 'menueleiste-nutzermenue-offen', { strengePruefungen: true });
    });
});
