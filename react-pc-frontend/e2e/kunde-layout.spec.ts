import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung, keinTextGekuerzt } from './hilfen/design';

/**
 * Task 5 (Abschnitt 3) aus docs/superpowers/plans/2026-09-05-layout-14-zoll.md,
 * Spec-Befund 2 (Kunde, docs/superpowers/specs/2026-09-04-layout-14-zoll.md) und
 * Spec-Befund 4 (Kartenraster).
 *
 * Befund 2, Kopfzeile (Kundeneditor.tsx): Bei einem langen Kundennamen
 * ("Wohnungsbaugesellschaft Beispielstadt Nord mbH und Co. Verwaltungs KG")
 * schrumpften die Kennzahlen-Kaesten "Gesamtumsatz" und "Gewinn" bei 1440px
 * auf rund 26px -- Beschriftung und Betrag lagen uebereinander und ueber dem
 * Knopf "Bearbeiten" (siehe
 * docs/superpowers/specs/bilder/2026-09-04-layout-14-zoll/kunde-detail-langer-name-1440.png).
 * Ursache: die Kennzahlen-Reihe hatte "flex-1 max-w-md", nahm sich also den
 * Platz, den der Titelblock eigentlich brauchte (der wiederum weder umbrechen
 * durfte noch eine Mindestbreite hatte). Vorbestehend, vom Design-Reviewer in
 * Abschnitt 2 erneut bestaetigt (Kontext-Log:
 * docs/superpowers/plans/2026-09-05-layout-14-zoll-log.md, Abschnitt "Kunden-
 * Detailseite") -- nachweislich NICHT durch DetailLayout/MainLayout (Task 2)
 * verursacht (die Alt-Simulation dort liefert bei 1440 exakt dieselben
 * Spaltenbreiten und dasselbe Bild). Gehoert zu dieser Kopfzeile.
 *
 * Befund 4, Uebersicht: Karten-Titel waren "truncate" (einzeilig, "…"), bei
 * vier Kunden mit langen Namen bei 1440 zusaetzlich vier statt drei Karten je
 * Reihe (xl:grid-cols-4 statt 2xl:grid-cols-4).
 *
 * Rote Spec zuerst (TDD, Skill superpowers:test-driven-development): Vor dem
 * Fix schlaegt designPruefung(..., { strengePruefungen: true }) fehl, weil
 * keinTextLaeuftUeber() (design.ts, Task 1) genau den gequetschten Kasten
 * erwischt -- kein Blatt-Element mit Text darf breiter sein als sein Kasten,
 * und Elemente mit Breite 0 werden dabei bewusst NICHT uebersprungen (siehe
 * design.ts-Kommentar: "Kunde mit langem Namen: Kasten 26px, Textbreite
 * 95px"). Die Uebersichts-Spec schlaegt vor dem Fix zusaetzlich an
 * keinTextGekuerzt() (unmarkierte truncate-Titel) und an der eigenen
 * Spalten-Zusicherung (vier statt drei Karten bei 1440) an.
 *
 * /api vollstaendig gestubbt (Catch-all + gezielte Overrides, Vorbild
 * stubbeLieferantApi in e2e/bearbeiten-leiste.spec.ts), kein Backend, nur
 * Fantasienamen (DSGVO): Kunde "Wohnungsbaugesellschaft Beispielstadt Nord
 * mbH und Co. Verwaltungs KG" (Spec-Vorgabe) und das lange Bauvorhaben
 * "Treppenanlage mit Podest und Absturzsicherung Buerogebaeude
 * Beispielstrasse" (Spec-Vorgabe) fuer die Mini-Karten Projekt/Anfrage/
 * Dokument.
 */

const KUNDE_ID = 3;
const KUNDE_LANG = 'Wohnungsbaugesellschaft Beispielstadt Nord mbH und Co. Verwaltungs KG';
const BAUVORHABEN_LANG = 'Treppenanlage mit Podest und Absturzsicherung Bürogebäude Beispielstraße';

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

// Bewusst kurze Adress-/Kontaktfelder: Ziel dieser Spec ist die Kopfzeile und
// die Karten-Titel, nicht jede denkbare Kuerzung im Sidebar-Kontaktblock.
// GoogleMapsEmbed rendert ohnehin nur bei gesetzter Adresse ein echtes
// <iframe src="https://www.google.com/maps?...">; die bleibt hier bewusst
// leer, damit der gestubbte Test keinen echten Netzwerkzugriff ausloest.
const DUMMY_KUNDE = {
    id: KUNDE_ID,
    kundennummer: 'K-1003',
    name: KUNDE_LANG,
    ansprechspartner: 'Erika Musterfrau',
    telefon: '0511 123456',
    mobiltelefon: '',
    zahlungsziel: 8,
    kundenEmails: ['info@beispiel-bau.example'],
    hatProjekte: true,
    statistik: {
        projektAnzahl: 1,
        anfrageAnzahl: 1,
        emailAdresseAnzahl: 1,
        gesamtUmsatz: 225000,
        gesamtGewinn: 42000,
    },
    kommunikation: [],
    projekte: [
        { id: 5, bauvorhaben: BAUVORHABEN_LANG, auftragsnummer: 'A-2026-0005', anlegedatum: '2026-01-15', bezahlt: false, bruttoPreis: 125000 },
    ],
    anfragen: [
        { id: 9, bauvorhaben: BAUVORHABEN_LANG, anfragesnummer: 'AN-2026-0009', anlegedatum: '2026-02-01', betrag: 45000 },
    ],
    geschaeftsdokumente: [
        {
            id: 501, dokumentNummer: 'RE-2026-0501', typ: 'RECHNUNG', datum: '2026-02-01',
            betragNetto: 4200, betragBrutto: 4998, gebucht: false, storniert: false, bearbeitbar: true,
            projektId: 5, projektBauvorhaben: BAUVORHABEN_LANG,
        },
    ],
    notizen: [],
};

/** Stubbt alle /api-Routen der Kunden-Detailseite (Deep-Link ?kundeId=). */
async function stubKundeDetailApi(page: Page) {
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
        if (pfad === '/api/kunden' && methode === 'GET') {
            return json(route, { kunden: [], gesamt: 0 });
        }
        if (pfad === `/api/kunden/${KUNDE_ID}`) return json(route, DUMMY_KUNDE);

        // Standardantwort fuer alles Weitere: leere Liste statt 404 -- fuer
        // diesen Ablauf irrelevante Endpunkte sollen die Seite nicht mit
        // einem Fehlerzustand fuellen (Catch-all-Empfehlung aus dem Plan).
        return json(route, []);
    });
}

const KUNDEN_LANG = [
    'Wohnungsbaugesellschaft Beispielstadt Nord mbH und Co. Verwaltungs KG',
    'Bauträgergesellschaft Musterhausen Süd mbH & Co. Projektentwicklungs KG',
    'Hausverwaltung und Instandhaltung Beispieldorf-West Genossenschaft eG',
    'Immobilienservice Mustertal Betriebs- und Verwaltungsgesellschaft mbH',
];

/** Stubbt /api fuer die Kunden-Uebersicht mit vier langen Kundennamen. */
async function stubKundenUebersichtApi(page: Page) {
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
        if (pfad === '/api/kunden' && methode === 'GET') {
            const kunden = KUNDEN_LANG.map((name, i) => ({
                id: i + 1,
                kundennummer: `K-${1001 + i}`,
                name,
                plz: '30159',
                ort: 'Hannover',
                ansprechspartner: 'Erika Musterfrau',
                hatProjekte: i % 2 === 0,
            }));
            return json(route, { kunden, gesamt: kunden.length });
        }
        return json(route, []);
    });
}

test.describe('Kunden-Detailseite: Kopfzeile mit langem Kundennamen (Spec-Befund 2)', () => {
    test('Kennzahlen-Kaesten bleiben lesbar, "Bearbeiten" bleibt in der Kopf-Karte, keine Kuerzung', async ({ page }, testInfo) => {
        await stubKundeDetailApi(page);
        await page.goto(`/kunden?kundeId=${KUNDE_ID}`);

        const ueberschrift = page.getByRole('heading', { name: KUNDE_LANG });
        await expect(ueberschrift).toBeVisible();

        const bearbeiten = page.getByRole('button', { name: 'Bearbeiten' });
        await expect(bearbeiten).toBeVisible();

        // Kern von Spec-Befund 2: die Kennzahl-Kaesten "Gesamtumsatz"/"Gewinn"
        // duerfen nicht mehr auf ~26px zusammengequetscht werden. Gemessen am
        // umschliessenden Kasten (Elternelement der Beschriftung).
        const gesamtumsatzKasten = page.getByText('Gesamtumsatz', { exact: true }).locator('xpath=..');
        const gewinnKasten = page.getByText('Gewinn', { exact: true }).locator('xpath=..');
        const gesamtumsatzBox = await gesamtumsatzKasten.boundingBox();
        const gewinnBox = await gewinnKasten.boundingBox();
        expect(gesamtumsatzBox, 'Kasten "Gesamtumsatz" muss einen messbaren Rahmen haben').not.toBeNull();
        expect(gewinnBox, 'Kasten "Gewinn" muss einen messbaren Rahmen haben').not.toBeNull();
        expect(
            gesamtumsatzBox!.width,
            `Kasten "Gesamtumsatz" ist auf ${gesamtumsatzBox!.width.toFixed(0)}px gequetscht (Spec-Befund 2 nennt 26px; min-w-[7rem] = 112px)`,
        ).toBeGreaterThanOrEqual(100);
        expect(
            gewinnBox!.width,
            `Kasten "Gewinn" ist auf ${gewinnBox!.width.toFixed(0)}px gequetscht (Spec-Befund 2 nennt 26px; min-w-[7rem] = 112px)`,
        ).toBeGreaterThanOrEqual(100);

        // Reiterleiste: einzeilig, kein overflow-x-auto mehr (auch bei 1920
        // pruefen, siehe Hinweis aus Abschnitt 2 zur breiteren rechten Spalte).
        // data-testid grenzt praezise auf die fuenf Reiter-Knoepfe ein --
        // ein Selektor ueber "border-b" traf sonst auch Knoepfe anderswo auf
        // der Seite (z.B. RibbonNav), weil "border-b-2" ebenfalls "border-b"
        // enthaelt.
        const reiterY = await page.getByTestId('kunde-reiterleiste').locator('button').evaluateAll((buttons) =>
            buttons.map((b) => b.getBoundingClientRect().y),
        );
        expect(reiterY.length, 'Reiterleiste: fuenf Reiter-Knoepfe erwartet').toBe(5);
        // +/-2px Toleranz fuer Sub-Pixel-Rundung (Flex-Layout) statt exakter
        // Gleichheit -- eine echte zweite Zeile liegt um mehr als eine
        // Knopfhoehe auseinander, nicht um 1px.
        const reiterSpanne = Math.max(...reiterY) - Math.min(...reiterY);
        expect(reiterSpanne, `Reiter liegen auf unterschiedlichen Zeilen: ${JSON.stringify(reiterY)}`).toBeLessThanOrEqual(2);

        // Ebene 2: Screenshot + designPruefung inkl. strengePruefungen, muss
        // vor dem Fix an keinTextLaeuftUeber() scheitern (Kasten enger als der
        // Text, der wieder ueberlaufen wuerde -- design.ts ueberspringt
        // Breite-0-Elemente absichtlich NICHT).
        await designPruefung(page, testInfo, 'kunde-detail-langer-name', {
            strengePruefungen: true,
            primaerAktion: bearbeiten,
        });

        // Mini-Karten der Tab-Bereiche: dieselben langen Bauvorhaben-Namen
        // duerfen dort ebenfalls nicht abgeschnitten werden.
        await page.getByRole('button', { name: /^Projekte/ }).click();
        await expect(page.getByText(BAUVORHABEN_LANG, { exact: true })).toBeVisible();
        await keinTextGekuerzt(page);

        await page.getByRole('button', { name: /^Anfragen/ }).click();
        await expect(page.getByText(BAUVORHABEN_LANG, { exact: true })).toBeVisible();
        await keinTextGekuerzt(page);

        await page.getByRole('button', { name: /^Dokumente/ }).click();
        await expect(page.getByText(`Projekt: ${BAUVORHABEN_LANG}`, { exact: true })).toBeVisible();
        await keinTextGekuerzt(page);
    });
});

test.describe('Kunden-Uebersicht: vier lange Kundennamen (Spec-Befund 4)', () => {
    test('kein Kartentitel einzeilig abgehackt, 3 Karten je Reihe bei 1440, 4 bei 1920', async ({ page }, testInfo) => {
        await stubKundenUebersichtApi(page);
        await page.goto('/kunden');

        const ueberschrift = page.getByRole('heading', { name: 'KUNDENÜBERSICHT' });
        await expect(ueberschrift).toBeVisible();

        for (const name of KUNDEN_LANG) {
            await expect(page.getByText(name, { exact: true }), `Kartentitel "${name}" fehlt oder ist gekuerzt`).toBeVisible();
        }

        // Kartenraster (Spec-Befund 4): xl:grid-cols-4 -> 2xl:grid-cols-4, also
        // 3 Spalten bei 1440 (< 1536px 2xl-Breakpoint), 4 Spalten bei 1920.
        const kartenY = await page.evaluate((namen: string[]) =>
            namen.map((name) => {
                const heading = Array.from(document.querySelectorAll('h3')).find((h) => h.textContent?.trim() === name);
                return heading ? heading.getBoundingClientRect().y : null;
            }), KUNDEN_LANG);

        const ersteReiheAnzahl = kartenY.filter((y) => y !== null && Math.abs(y - (kartenY[0] ?? 0)) < 5).length;
        const erwartet = Math.min(testInfo.project.name === 'pc-monitor' ? 4 : 3, KUNDEN_LANG.length);
        expect(
            ersteReiheAnzahl,
            `Erwartet ${erwartet} Karten in der ersten Reihe bei ${testInfo.project.name} (${testInfo.project.name === 'pc-monitor' ? 1920 : 1440}px), gemessen: ${JSON.stringify(kartenY)}`,
        ).toBe(erwartet);

        await designPruefung(page, testInfo, 'kunde-uebersicht-lange-namen', {
            strengePruefungen: true,
            primaerAktion: page.getByRole('button', { name: 'Neuer Kunde' }),
        });
    });
});
