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
 *
 * Nachtrag Task 8b (Abschnitt 5, Review-Funde aus Abschnitt 2): Anzeigename
 * zurueck auf "max-w-[10rem] truncate" (der Ausweichgrund line-clamp-1 ist mit
 * Task 1b weggefallen), zusaetzlich "2xl:max-w-none" fuer pc-monitor (dort
 * sind rund 300px frei) und "no-scrollbar" in der Menuepunkt-Zeile entfernt.
 *
 * Fremder Befund dabei (design.ts, nicht in dieser Files-Liste, siehe
 * Kontext-Log): keinTextLaeuftUeber() hat -- anders als keinHorizontalerUeberlauf,
 * das Task 1b genau dafuer angepasst hat -- keine Ausnahme fuer reine
 * Text-Kuerzung (text-overflow: ellipsis) und keine fuer data-kuerzung-erlaubt.
 * Jedes tatsaechlich gekuerzte "truncate"-Element hat zwangslaeufig
 * scrollWidth > clientWidth auf sich selbst -- keinTextLaeuftUeber meldet das
 * immer, auch wenn die Kuerzung ausdruecklich erlaubt ist.
 *
 * Task 10b (Abschnitt 7, Design-Review Abschnitt 6 Befund c): der
 * Design-Reviewer hat "strengePruefungen" testweise auf
 * "testInfo.project.name === 'pc-monitor'" gestellt und einen vollstaendig
 * gruenen Lauf gemessen (8/8) -- fuer pc-monitor ist das also sicher scharf zu
 * stellen. Fuer pc-14zoll (und das neue pc-uebergang, siehe unten) bleibt es
 * aus derselben Grunde aus wie bisher: keinTextLaeuftUeber kennt noch keine
 * Ausnahme fuer die gewollte "max-w-[10rem] truncate"-Kuerzung des
 * Anzeigenamens (nur keinTextGekuerzt kennt data-kuerzung-erlaubt). Das ist
 * die im Plan (Task 10) angekuendigte Nacharbeit an design.ts -- hier nicht
 * anfassen (fremde Datei). Deshalb rufen die folgenden Tests designPruefung()
 * mit "strengePruefungen: testInfo.project.name === 'pc-monitor'" auf und
 * pruefen keinTextGekuerzt() (das die Ausnahme korrekt kennt) zusaetzlich
 * einzeln fuer alle Groessen -- identische Abdeckung fuer den hier relevanten
 * Fall, ohne den fremden Fehlalarm bei pc-14zoll/pc-uebergang auszuloesen.
 */

const LANGER_NUTZERNAME = 'Friederike Beispiel-Musterfrau';

/**
 * Task 10b, roter Ausgangsbefund (Design-Review Abschnitt 6, Befund c):
 * Fiktiver, ca. 55 Zeichen langer Anzeigename (kein echter Nutzer, DSGVO) --
 * exakt die Laenge, mit der der Design-Reviewer bei 1536px 119px Ueberstand
 * der Kategorie-Leiste gemessen hat (heutiger Code: "2xl:max-w-none" macht
 * den Anzeigenamen ab 1536px unbegrenzt breit, obwohl dort noch nicht genug
 * Platz frei ist). Bei 1440px (max-w-[10rem] greift) und bei 1920px (genug
 * Platz frei) bleibt die Kategorie-Leiste auch mit diesem Namen bei 0px
 * Ueberstand -- nur das neue pc-uebergang-Projekt (1536px) deckt die Luecke
 * dazwischen ab.
 */
const LANGER_NUTZERNAME_GRENZFALL = 'Friederike Charlotte Beispiel-Musterfrau-Bergwaldschmidt';

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
async function stubbeMenueleisteApi(page: Page, displayName: string = LANGER_NUTZERNAME) {
    await page.route('**/api/**', route => json(route, {}));

    await page.route('**/api/auth/me', route => json(route, {
        id: 1,
        displayName,
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

async function oeffneProjekteMitMenueleiste(page: Page, displayName: string = LANGER_NUTZERNAME) {
    await stubbeMenueleisteApi(page, displayName);
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

/**
 * Misst das Anzeigename-Element selbst (RibbonNav.tsx, "data-kuerzung-erlaubt").
 * Eindeutig per Attribut auffindbar -- in der Menueleiste traegt sonst nichts
 * dieses Attribut.
 */
async function anzeigenameMasse(page: Page) {
    return page.evaluate(() => {
        const el = document.querySelector('[data-kuerzung-erlaubt]');
        if (!el) return null;
        return { scrollWidth: el.scrollWidth, clientWidth: el.clientWidth };
    });
}

/**
 * Misst die Menuepunkt-Zeile einer aufgeklappten Kategorie (RibbonNav.tsx,
 * "px-3 py-2 flex gap-1 overflow-x-auto", frueher zusaetzlich "no-scrollbar").
 * Das Entfernen von "no-scrollbar" aendert die gemessenen Werte selbst nicht
 * (die Klasse blendet nur die Scrollbar-Grafik aus, overflow-x bleibt "auto"),
 * sichert aber ab, dass ein kuenftiger Ueberlauf sichtbar bliebe statt lautlos
 * zu verschwinden -- Regressionswaechter fuer denselben Fund wie bei der
 * Kategorie-Leiste (siehe kategorieLeisteMasse).
 */
async function menuepunktZeileMasse(page: Page) {
    return page.evaluate(() => {
        const zeile = document.querySelector('.flex.gap-1.overflow-x-auto');
        if (!zeile) return null;
        return { scrollWidth: zeile.scrollWidth, clientWidth: zeile.clientWidth };
    });
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

        // Task 8b, Punkt 3: bei pc-monitor (1920) sind rund 300px frei, dort
        // darf der Anzeigename nicht mehr gekuerzt werden ("2xl:max-w-none").
        // Bei pc-14zoll (1440) bleibt die Kuerzung gewollt (data-kuerzung-erlaubt,
        // von keinTextGekuerzt bereits als Ausnahme geprueft) -- deshalb hier nur
        // bei pc-monitor scharf.
        if (testInfo.project.name === 'pc-monitor') {
            const anzeigename = await anzeigenameMasse(page);
            expect(anzeigename, 'Anzeigename-Element (data-kuerzung-erlaubt) nicht gefunden').not.toBeNull();
            expect(
                anzeigename!.scrollWidth,
                `Anzeigename ist bei 1920 unnoetig gekuerzt: Kasten ${anzeigename!.clientWidth}px, Inhalt braucht ${anzeigename!.scrollWidth}px`,
            ).toBeLessThanOrEqual(anzeigename!.clientWidth);
        }

        // Task 8b, Punkt 4: Menuepunkt-Zeile der aufgeklappten Kategorie ohne
        // Ueberlauf, in beiden Groessen (Regressionswaechter fuer "no-scrollbar"-
        // Entfernung, siehe menuepunktZeileMasse).
        const zeile = await menuepunktZeileMasse(page);
        expect(zeile, 'Menuepunkt-Zeile nicht gefunden').not.toBeNull();
        expect(
            zeile!.scrollWidth,
            `Menuepunkt-Zeile laeuft ueber: ${zeile!.clientWidth}px breit, Inhalt braucht ${zeile!.scrollWidth}px`,
        ).toBeLessThanOrEqual(zeile!.clientWidth);

        // keinTextGekuerzt() zusaetzlich zu designPruefung() -- siehe Kommentar
        // bei den Imports zu keinTextLaeuftUeber/strengePruefungen.
        await keinTextGekuerzt(page);
        await designPruefung(page, testInfo, 'menueleiste-kategorien', {
            strengePruefungen: testInfo.project.name === 'pc-monitor',
        });
    });

    test('Kategorie "Vorlagen & Stammdaten": "Dokumentenrechte" steht vollstaendig da', async ({ page }, testInfo) => {
        await oeffneProjekteMitMenueleiste(page);

        await page.getByRole('button', { name: 'Vorlagen & Stammdaten', exact: true }).click();
        const dokumentenrechte = page.getByRole('link', { name: 'Dokumentenrechte' });
        await expect(dokumentenrechte).toBeVisible();
        await keinTextGekuerzt(page);

        await designPruefung(page, testInfo, 'menueleiste-dokumentenrechte', {
            strengePruefungen: testInfo.project.name === 'pc-monitor',
        });
    });

    test('Kategorie "Finanzen & Controlling": "Mietabrechnung" steht vollstaendig da', async ({ page }, testInfo) => {
        await oeffneProjekteMitMenueleiste(page);

        await page.getByRole('button', { name: 'Finanzen & Controlling', exact: true }).click();
        const mietabrechnung = page.getByRole('link', { name: 'Mietabrechnung' });
        await expect(mietabrechnung).toBeVisible();
        await keinTextGekuerzt(page);

        await designPruefung(page, testInfo, 'menueleiste-mietabrechnung', {
            strengePruefungen: testInfo.project.name === 'pc-monitor',
        });
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

        await designPruefung(page, testInfo, 'menueleiste-nutzermenue-offen', {
            strengePruefungen: testInfo.project.name === 'pc-monitor',
        });
    });

    /**
     * Task 10b (Abschnitt 7): die Luecke zwischen den beiden Pflichtgroessen.
     * "2xl:max-w-none" (Zeile 318 vor dem Fix) greift technisch schon ab
     * 1536px -- dort ist aber noch nicht genug Platz frei. Mit einem rund
     * 55 Zeichen langen Anzeigenamen sprengt der dadurch unbegrenzt breite
     * Anzeigename die Kategorie-Leiste bei 1536px um 119px (Design-Review
     * Abschnitt 6, Befund c, exakt nachgemessen). Kein bestehender Check sah
     * das: "html" und "main" bleiben 0, weil die Kategorie-Leiste selbst
     * "overflow-x: auto" hat, und fuer 1536-1919px gab es bisher gar kein
     * Playwright-Projekt (siehe pc-uebergang in playwright.config.ts).
     *
     * Vor dem Fix rot NUR bei pc-uebergang (1536px) -- bei pc-14zoll greift
     * "max-w-[10rem]" (Kuerzung), bei pc-monitor (1920px) ist genug Platz frei
     * (siehe Tabelle im Kontext-Log: 0px Ueberstand bei 1440 und 1920, 119px
     * bei 1536, mit demselben Namen).
     */
    test('Anzeigename an der 1536-Grenze: Kategorie-Leiste laeuft nicht ueber, alle Kategorien lesbar', async ({ page }) => {
        await oeffneProjekteMitMenueleiste(page, LANGER_NUTZERNAME_GRENZFALL);

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
    });
});
