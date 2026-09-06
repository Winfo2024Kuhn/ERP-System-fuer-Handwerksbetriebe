import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung } from './hilfen/design';

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
        const reiterleiste = page.getByTestId('kunde-reiterleiste');
        const reiterY = await reiterleiste.locator('button').evaluateAll((buttons) =>
            buttons.map((b) => b.getBoundingClientRect().y),
        );
        expect(reiterY.length, 'Reiterleiste: fuenf Reiter-Knoepfe erwartet').toBe(5);
        // +/-2px Toleranz fuer Sub-Pixel-Rundung (Flex-Layout) statt exakter
        // Gleichheit -- eine echte zweite Zeile liegt um mehr als eine
        // Knopfhoehe auseinander, nicht um 1px.
        const reiterSpanne = Math.max(...reiterY) - Math.min(...reiterY);
        expect(reiterSpanne, `Reiter liegen auf unterschiedlichen Zeilen: ${JSON.stringify(reiterY)}`).toBeLessThanOrEqual(2);

        // Nacharbeit Abschnitt 4, Punkt 7 (Code-Review-Hinweis 2): Reiterleiste
        // darf kein verstecktes Scrollen zurueckbekommen -- bei Kunde faengt
        // das bisher NICHTS ab, weil die fuenf Reiter ohnehin in eine Zeile
        // passen und die y-Pruefung oben bei overflow-x-auto zufaellig gruen
        // bliebe.
        const reiterleisteOverflowX = await reiterleiste.evaluate((el) => getComputedStyle(el).overflowX);
        expect(
            reiterleisteOverflowX,
            `Reiterleiste hat overflow-x: ${reiterleisteOverflowX} -- verstecktes Scrollen statt Umbruch waere ein Rueckfall`,
        ).toBe('visible');

        // Nacharbeit Abschnitt 4, Punkt 3: der Knopfblock muss RECHTS stehen
        // (x-Position groesser als die Kartenmitte), nicht nur "irgendwo in
        // der Karte" -- ohne ml-auto faellt er beim Umbruch an den linken
        // Kartenrand (am Projekt-Editor gemessen: x=89 statt x=961 bei 1440px).
        const kopfKarte = bearbeiten.locator(
            'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " shadow-sm ")][1]',
        );
        const kopfKarteBox = await kopfKarte.boundingBox();
        expect(kopfKarteBox, 'Kopf-Karte muss einen messbaren Rahmen haben').not.toBeNull();
        const knopfblock = bearbeiten.locator('xpath=..');
        const knopfblockBox = await knopfblock.boundingBox();
        expect(knopfblockBox, 'Knopfblock muss einen messbaren Rahmen haben').not.toBeNull();
        const karteMitteX = kopfKarteBox!.x + kopfKarteBox!.width / 2;
        expect(
            knopfblockBox!.x,
            `Knopfblock (x=${knopfblockBox!.x.toFixed(0)}) steht nicht rechts von der Kartenmitte (${karteMitteX.toFixed(0)}) -- ml-auto fehlt oder wirkt nicht`,
        ).toBeGreaterThan(karteMitteX);

        // Ebene 2: Screenshot + designPruefung inkl. strengePruefungen, muss
        // vor dem Fix an keinTextLaeuftUeber() scheitern (Kasten enger als der
        // Text, der wieder ueberlaufen wuerde -- design.ts ueberspringt
        // Breite-0-Elemente absichtlich NICHT).
        await designPruefung(page, testInfo, 'kunde-detail-langer-name', {
            strengePruefungen: true,
            primaerAktion: bearbeiten,
        });

        // Mini-Karten der Tab-Bereiche: dieselben langen Bauvorhaben-Namen
        // duerfen dort ebenfalls nicht abgeschnitten werden. Nacharbeit
        // Abschnitt 4, Punkt 9 (Design-Review-Hinweis 4): volle designPruefung
        // mit strengePruefungen statt nur keinTextGekuerzt() -- ein Rueckfall
        // auf truncate MIT beibehaltenem Marker faengt sonst nur
        // keinTextLaeuftUeber() (Teil von designPruefung), nicht keinTextGekuerzt()
        // allein.
        await page.getByRole('button', { name: /^Projekte/ }).click();
        await expect(page.getByText(BAUVORHABEN_LANG, { exact: true })).toBeVisible();
        await designPruefung(page, testInfo, 'kunde-mini-karten-projekte', { strengePruefungen: true });

        await page.getByRole('button', { name: /^Anfragen/ }).click();
        await expect(page.getByText(BAUVORHABEN_LANG, { exact: true })).toBeVisible();
        await designPruefung(page, testInfo, 'kunde-mini-karten-anfragen', { strengePruefungen: true });

        await page.getByRole('button', { name: /^Dokumente/ }).click();
        await expect(page.getByText(`Projekt: ${BAUVORHABEN_LANG}`, { exact: true })).toBeVisible();
        await designPruefung(page, testInfo, 'kunde-mini-karten-dokumente', { strengePruefungen: true });
    });
});

// Nachbesserung 1 (Design-Review, 🔴): bei EINEM einzigen langen Wort (kein
// Leerzeichen) lief die <h1> quer ueber die Kennzahlen-Reihe -- gemessen 999px
// breit, weit ueber den Titelblock hinaus, "GESAMTUMSATZ"/"GEWINN" darunter
// unlesbar. Ursache: die <h1> ist selbst Flex-Item (in "flex items-center
// gap-3 flex-wrap") und behielt ihr eigenes min-width: auto -- break-words +
// min-w-0 am umschliessenden div reichten nicht, min-w-0 muss an der <h1>
// selbst stehen. Keine bisherige Zusicherung hat das gefangen.
const KOMPOSITA_EIN_WORT_KUNDE = 'Wohnungsbaugesellschaftsverwaltungsimmobilienbetriebsgenossenschaft';

test.describe('Kunden-Detailseite: <h1> bei einem einzigen langen Wort ohne Leerzeichen', () => {
    test('<h1> ragt nicht rechts aus dem Titelblock', async ({ page }) => {
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
            if (pfad === '/api/kunden' && methode === 'GET') return json(route, { kunden: [], gesamt: 0 });
            if (pfad === `/api/kunden/${KUNDE_ID}`) return json(route, { ...DUMMY_KUNDE, name: KOMPOSITA_EIN_WORT_KUNDE });
            return json(route, []);
        });
        await page.goto(`/kunden?kundeId=${KUNDE_ID}`);

        const titel = page.getByRole('heading', { name: KOMPOSITA_EIN_WORT_KUNDE });
        await expect(titel).toBeVisible();

        // Titelblock: das aeussere "flex-1 min-w-[18rem]"-div (Zurueck-Pfeil,
        // Initialen-Kreis, Titel, Ansprechpartner, Adresse).
        const titelblock = titel.locator(
            'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " min-w-[18rem] ")][1]',
        );
        const titelBox = await titel.boundingBox();
        const titelblockBox = await titelblock.boundingBox();
        expect(titelBox, '<h1> muss einen messbaren Rahmen haben').not.toBeNull();
        expect(titelblockBox, 'Titelblock muss einen messbaren Rahmen haben').not.toBeNull();
        const ueberstand = (titelBox!.x + titelBox!.width) - (titelblockBox!.x + titelblockBox!.width);
        expect(
            ueberstand,
            `<h1> (Breite ${titelBox!.width.toFixed(0)}px) ragt ${ueberstand.toFixed(0)}px rechts aus dem Titelblock (Breite ${titelblockBox!.width.toFixed(0)}px) -- min-w-0 an der <h1> fehlt oder wirkt nicht`,
        ).toBeLessThanOrEqual(1);
    });
});

test.describe('Kunden-Uebersicht: vier lange Kundennamen (Spec-Befund 4)', () => {
    test('kein Kartentitel einzeilig abgehackt, 3 Karten je Reihe bei 1440, 4 bei 1920', async ({ page }, testInfo) => {
        await stubKundenUebersichtApi(page);
        await page.goto('/kunden');

        const ueberschrift = page.getByRole('heading', { name: 'KUNDENÜBERSICHT' });
        await expect(ueberschrift).toBeVisible();

        // Nacharbeit Abschnitt 4, Punkt 9 (Code-Review-Hinweis 5): die
        // urspruengliche Meldung "fehlt oder ist gekuerzt" war irrefuehrend --
        // getByText() sieht den vollstaendigen DOM-Text auch bei "truncate"
        // (CSS kuerzt nur visuell). Diese Zusicherung prueft ausschliesslich,
        // ob der Name ueberhaupt im DOM steht; die Kuerzung selbst faengt
        // designPruefung() weiter unten (keinTextGekuerzt).
        for (const name of KUNDEN_LANG) {
            await expect(page.getByText(name, { exact: true }), `Kartentitel "${name}" nicht im DOM gefunden`).toBeVisible();
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

    // Nacharbeit Abschnitt 4, Punkt 4 (Design-Review-Befund): min-h-[3rem] auf
    // dem Kartentitel riss bei einem KURZEN Kundennamen eine 24px-Luecke
    // zwischen Titel und der Ort-Zeile darunter (gemessen: 48px Titelhoehe fuer
    // 24px Text). h-full flex flex-col an der Karte + mt-auto am
    // Kontakt-Meta-Block loesen das.
    test('kurzer Kundenname reisst keine Luecke zwischen Titel und Ort', async ({ page }) => {
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
            if (pfad === '/api/kunden' && methode === 'GET') {
                return json(route, {
                    kunden: [
                        { id: 301, kundennummer: 'K-1301', name: 'Meier', plz: '30159', ort: 'Hannover', ansprechspartner: 'Erika Musterfrau', hatProjekte: true },
                        // Zweiter Kunde mit langem, zweizeiligem Titel in
                        // DERSELBEN Reihe (bei 1440 stehen drei Karten
                        // nebeneinander) -- fuer die Trennlinien-Ausrichtung
                        // unten (Nachbesserung 1, 🟡).
                        { id: 302, kundennummer: 'K-1302', name: KUNDE_LANG, plz: '30159', ort: 'Hannover', ansprechspartner: 'Erika Musterfrau', hatProjekte: true },
                    ],
                    gesamt: 2,
                });
            }
            return json(route, []);
        });
        await page.goto('/kunden');

        const titel = page.getByRole('heading', { level: 3, name: 'Meier' });
        await expect(titel).toBeVisible();
        const ort = page.getByText('30159 Hannover', { exact: true }).first();
        await expect(ort).toBeVisible();

        // min-h-[3rem] reserviert den Platz INNERHALB der eigenen Titel-Box
        // (48px Boxhoehe fuer 24px einzeiligen Text) -- eine Messung "Luecke
        // zum naechsten Geschwister" saehe die Boxhoehe faelschlich als Teil
        // des Titels an und bliebe deshalb unauffaellig. Die Boxhoehe selbst
        // ist der richtige Messpunkt: mit min-h-[3rem] gemessen 48px, ohne
        // (h-full flex flex-col + mt-auto am Meta-Block) 24px.
        const titelBox = await titel.boundingBox();
        expect(titelBox, 'Titel muss einen messbaren Rahmen haben').not.toBeNull();
        expect(
            titelBox!.height,
            `Titel-Box ist ${titelBox!.height.toFixed(0)}px hoch fuer einzeiligen Text -- min-h-[3rem] (48px) reisst hier eine Luecke`,
        ).toBeLessThan(32);

        // Nachbesserung 1 (Design-Review, 🟡): space-y-3 -> gap-3. Tailwinds
        // space-y-3 erzeugt den Selektor "> * + *" (Spezifitaet 0-3-0), der
        // mt-auto (0-1-0) am Meta-Block nieder-schlaegt -- der Titel-Boxhoehen-
        // Check oben allein haette das NICHT gefangen (der misst nur den Titel
        // selbst, nicht ob der Meta-Block wirklich unten sitzt). Belegt am
        // gebauten CSS und im Bild: die Trennlinien einer Kartenreihe lagen
        // 24px versetzt. Zusicherung: der Ansprechpartner-Text (erste Zeile
        // des Meta-Blocks, den mt-auto nach unten schiebt) muss bei BEIDEN
        // Karten derselben Reihe auf derselben y-Position stehen, unabhaengig
        // davon, ob der Titel ein- oder zweizeilig ist.
        const ansprechpartnerZeilen = await page.getByText('Erika Musterfrau', { exact: true }).all();
        expect(ansprechpartnerZeilen.length, 'Erwartet zwei Ansprechpartner-Zeilen (eine je Kunde)').toBe(2);
        const ansprechpartnerBoxen = await Promise.all(ansprechpartnerZeilen.map((el) => el.boundingBox()));
        for (const box of ansprechpartnerBoxen) {
            expect(box, 'Ansprechpartner-Zeile muss einen messbaren Rahmen haben').not.toBeNull();
        }
        const yWerte = ansprechpartnerBoxen.map((b) => b!.y);
        const versatz = Math.max(...yWerte) - Math.min(...yWerte);
        expect(
            versatz,
            `Meta-Block-Zeilen sind ${versatz.toFixed(0)}px versetzt (y-Werte: ${yWerte.map((y) => y.toFixed(0)).join(', ')}) -- mt-auto wirkt nicht (space-y-3-Spezifitaet?)`,
        ).toBeLessThanOrEqual(2);
    });
});
