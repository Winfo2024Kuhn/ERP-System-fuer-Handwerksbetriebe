import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung, keinTextGekuerzt, keinTextLaeuftUeber } from './hilfen/design';
import { blockiereFremdeNetzwerkzugriffe } from './hilfen/api';

/**
 * Task 6 (Abschnitt 4) aus docs/superpowers/plans/2026-09-05-layout-14-zoll.md,
 * Spec-Befund 2 (Lieferant, docs/superpowers/specs/2026-09-04-layout-14-zoll.md)
 * und Spec-Befund 4 (Kartenraster).
 *
 * Befund 2, Kopfzeile (LieferantenEditor.tsx): Schon beim Namen "Stahlhandel
 * Beispiel GmbH und Co. KG" laufen "Gesamtkosten" (34px zu wenig) und
 * "Bestellungen" (29px zu wenig) aus ihren Kaesten (siehe
 * docs/superpowers/specs/bilder/2026-09-04-layout-14-zoll/lieferant-detail-1440.png:
 * "GESAMTKOSTE" abgeschnitten, "BESTELLUNGEN" zweizeilig und unten abgeschnitten).
 * Ursache: die Kennzahlen-Reihe hat "flex-1 max-w-4xl" (Zeile 110), nimmt sich
 * also Platz, den Titelblock und Knopfblock ebenfalls brauchen -- keiner der
 * drei Blöcke darf umbrechen oder hat eine Mindestbreite.
 *
 * Bauweise nach der "Gemeinsamen Rezeptur fuer Kopfzeile und Reiterleiste"
 * (Abschnitt 4 im Plan, Ergebnis aus dem Abschnitt-3-Review): aeusseres
 * "flex flex-wrap items-start gap-4", Titelblock "flex-1 min-w-[18rem]" mit
 * "min-w-0" am inneren Textblock, "<h1>" mit "break-words", Icons der
 * Untertitel-Zeilen "shrink-0", Kennzahlen ohne "flex-1"/"max-w-*" mit
 * "min-w-[7rem]" je Kasten, Knopfblock "shrink-0 ml-auto flex flex-wrap
 * items-start gap-2" (das "ml-auto" haelt den Knopf beim Umbruch rechts statt
 * links -- beim Projekt-Editor war das ohne "ml-auto" x=89 statt x=961,
 * gemessen vom Design-Reviewer in Abschnitt 3). Reiterleiste "flex flex-wrap
 * min-w-0", Knoepfe "px-3", zusaetzlich zugesichert:
 * getComputedStyle(leiste).overflowX === 'visible' (Nachtrag aus dem
 * Abschnitt-3-Review, Bedenken 2: ein Rueckfall auf "overflow-x-auto" wuerde
 * sonst nur zufaellig auffallen).
 *
 * Rote Spec zuerst (TDD, Skill superpowers:test-driven-development): Vor dem
 * Fix schlaegt designPruefung(..., { strengePruefungen: true }) fehl, weil
 * keinTextLaeuftUeber() (design.ts, Task 1) genau die gequetschten Kaesten
 * "Gesamtkosten"/"Bestellungen" erwischt. Die eigene Kasten-Breiten-Zusicherung
 * schlaegt ebenfalls vorher fehl.
 *
 * Befund 4, Uebersicht: Kartentitel waren "truncate" (einzeilig, "…"),
 * Kartenraster "xl:grid-cols-4" statt "2xl:grid-cols-4" (vier statt drei
 * Karten je Reihe bei 1440).
 *
 * /api vollstaendig gestubbt (Catch-all + gezielte Overrides, Vorbild
 * stubbeLieferantApi in e2e/bearbeiten-leiste.spec.ts), kein Backend, nur
 * Fantasienamen (DSGVO).
 *
 * Nachbesserung 1 (Abschnitt-4-Review, zwei Befunde in dieser Datei/ihrer
 * Produktivdatei):
 *
 * 1. 🔴 Blocker (Design-Review): Bei einem Lieferantennamen aus einem
 *    einzigen langen Wort (kein Leerzeichen zum Umbrechen) lief die `<h1>`
 *    quer ueber die Kennzahlen. Ursache: die `<h1>` ist Flex-Item in
 *    `<div class="flex items-center gap-3 flex-wrap">` und hat deshalb
 *    `min-width: auto` -- ihre Mindestinhaltsbreite bleibt ihre volle
 *    Wortbreite, `break-words` (overflow-wrap: break-word) senkt diese
 *    Mindestbreite nicht. Das vorhandene `min-w-0` sitzt am umschliessenden
 *    Textblock, nicht an der `<h1>` selbst. Fix: `min-w-0` zusaetzlich auf
 *    die `<h1>`. Neuer Testfall unten sichert zu: rechte Kante der `<h1>`
 *    <= rechte Kante ihres Titelblocks (die bisherige Knopf-Zusicherung
 *    haette das nicht gefangen, siehe Kommentar dort).
 * 2. 🟡 (Code- und Design-Review bestaetigt): Diese Spec telefonierte ins
 *    Internet. `DUMMY_LIEFERANT` fuellt strasse/plz/ort, dadurch rendert
 *    `LieferantenEditor.tsx` ein echtes `GoogleMapsEmbed` mit `<iframe
 *    src="https://www.google.com/maps?...">`; dazu laedt `index.html` in
 *    jedem Fall `pdf.js` von cdnjs.cloudflare.com. `page.route('**\/api/**')`
 *    faengt das nicht ab. Fix: `blockiereFremdeNetzwerkzugriffe()`
 *    (`e2e/hilfen/api.ts`, neu) bricht jede Anfrage ab, die nicht an
 *    localhost geht -- die Adressfelder duerfen dadurch gefuellt bleiben
 *    (die Karte ist im Screenshot weiterhin sichtbar, es geht nur nichts
 *    mehr wirklich raus). Am Ende der Detail-Tests ein Netzwerk-Mitschnitt,
 *    der das belegt.
 */

const LIEFERANT_ID = 21;
const LIEFERANT_LANG = 'Stahlhandel Beispiel GmbH und Co. KG';

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const DUMMY_LIEFERANT = {
    id: LIEFERANT_ID,
    lieferantenname: LIEFERANT_LANG,
    lieferantenTyp: 'STAHL',
    rollen: [],
    aliasName: 'Beispiel Stahl',
    strasse: 'Industriestraße 44',
    plz: '30179',
    ort: 'Hannover',
    telefon: '0511 9876543',
    mobiltelefon: '0171 1234567',
    vertreter: 'Hans Beispiel',
    vorauskasse: false,
    kundenEmails: ['bestellung@beispiel-stahl.example'],
    standardKostenstelleName: undefined,
    statistik: {
        gesamtKosten: 128450.75,
        bestellungAnzahl: 37,
        artikelAnzahl: 214,
        lieferzeit: 6,
    },
    kommunikation: [],
    dokumente: [],
    notizen: [],
    emails: [],
};

// Fantasie-Firmenname aus einem einzigen, unteilbaren Wort -- genau der Fall,
// den der Design-Reviewer am Projekt-Editor mit 999px Titelbreite und 411px
// Ueberstand ueber den Titelblock nachgewiesen hat (kein Leerzeichen, an dem
// die Zeile umbrechen koennte).
const LIEFERANT_EINWORT_LANG = 'Baustahlgewindestangenspezialgroßhandelsvertriebsgesellschaft';

/**
 * Stubbt alle /api-Routen der Lieferanten-Detailseite (Deep-Link ?lieferantId=)
 * UND riegelt jeden Zugriff auf ein fremdes Ziel ab (Nachbesserung 1, Befund 2):
 * `strasse`/`plz`/`ort` bleiben absichtlich gefuellt (die Kopfzeile soll mit
 * echter Adresse geprueft werden), ohne dass GoogleMapsEmbed dadurch wirklich
 * ins Netz geht -- blockiereFremdeNetzwerkzugriffe() muss deshalb VOR der
 * Navigation stehen, damit sie schon den allerersten Dokument-Request faengt.
 */
async function stubLieferantDetailApi(page: Page, optionen: { name?: string } = {}) {
    await blockiereFremdeNetzwerkzugriffe(page);

    const lieferant = { ...DUMMY_LIEFERANT, lieferantenname: optionen.name ?? DUMMY_LIEFERANT.lieferantenname };

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
        if (pfad === '/api/lieferanten' && methode === 'GET') {
            return json(route, { lieferanten: [], gesamt: 0 });
        }
        if (pfad === `/api/lieferanten/${LIEFERANT_ID}`) return json(route, lieferant);

        // Standardantwort fuer alles Weitere: leere Liste statt 404 -- fuer
        // diesen Ablauf irrelevante Endpunkte sollen die Seite nicht mit
        // einem Fehlerzustand fuellen (Catch-all-Empfehlung aus dem Plan).
        return json(route, []);
    });
}

const LIEFERANTEN_LANG = [
    'Stahlhandel Beispiel GmbH und Co. KG',
    'Baubeschläge und Verbindungstechnik Musterhausen Vertriebs GmbH',
    'Beschichtungs- und Verzinkereibetrieb Beispieltal eingetragene Genossenschaft',
    'Aluminium- und Edelstahlhandel Nordmuster Handelsgesellschaft mbH',
];

/**
 * Stubbt /api fuer die Lieferanten-Uebersicht mit vier langen Namen. Blockt
 * ebenfalls fremde Ziele -- `index.html` laedt `pdf.js` von cdnjs unabhaengig
 * von der Route (Nachbesserung 1, Befund 2).
 */
async function stubLieferantenUebersichtApi(page: Page) {
    await blockiereFremdeNetzwerkzugriffe(page);

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
        if (pfad === '/api/lieferanten' && methode === 'GET') {
            const lieferanten = LIEFERANTEN_LANG.map((name, i) => ({
                id: i + 1,
                lieferantenname: name,
                lieferantenTyp: 'STAHL',
                rollen: [],
                ort: 'Hannover',
                telefon: '0511 1234567',
                vertreter: 'Hans Beispiel',
            }));
            return json(route, { lieferanten, gesamt: lieferanten.length });
        }
        return json(route, []);
    });
}

test.describe('Lieferanten-Detailseite: Kopfzeile mit langem Lieferantennamen (Spec-Befund 2)', () => {
    test('Kennzahlen-Kaesten bleiben lesbar, "Bearbeiten" bleibt rechts in der Kopf-Karte, Reiterleiste einzeilig ohne verstecktes Scrollen', async ({ page }, testInfo) => {
        // Netzwerk-Mitschnitt (Nachbesserung 1, Befund 2): DUMMY_LIEFERANT hat
        // strasse/plz/ort gefuellt, die Kopfzeile rendert also ein echtes
        // GoogleMapsEmbed-<iframe>. Ohne blockiereFremdeNetzwerkzugriffe() (siehe
        // stubLieferantDetailApi) ginge das tatsaechlich an google.com/maps,
        // maps.gstatic.com und maps.googleapis.com raus, dazu index.html laedt
        // pdf.js von cdnjs.cloudflare.com. Die Listener muessen VOR dem goto
        // registriert sein, sonst verpassen sie den allerersten Dokument-Request.
        const fremdeAnfragen: string[] = [];
        const fremdeAntworten: string[] = [];
        const istFremd = (url: string) => {
            const host = new URL(url).hostname;
            return host !== 'localhost' && host !== '127.0.0.1';
        };
        page.on('request', (request) => {
            if (istFremd(request.url())) fremdeAnfragen.push(request.url());
        });
        page.on('response', (response) => {
            if (istFremd(response.url())) fremdeAntworten.push(response.url());
        });

        await stubLieferantDetailApi(page);
        await page.goto(`/lieferanten?lieferantId=${LIEFERANT_ID}&tab=dokumente`);

        const ueberschrift = page.getByRole('heading', { name: LIEFERANT_LANG });
        await expect(ueberschrift).toBeVisible();

        const bearbeiten = page.getByRole('button', { name: 'Bearbeiten' });
        await expect(bearbeiten).toBeVisible();

        // Kern von Spec-Befund 2: die Kennzahl-Kaesten duerfen nicht mehr auf
        // 34px/29px zusammengequetscht werden. Gemessen am umschliessenden
        // Kasten (Elternelement der Beschriftung). min-w-[7rem] = 112px, Toleranz
        // wie im Kunde-Vorbild.
        // Scoped auf den Kennzahlen-Container (data-testid), weil "Artikel" auch
        // als Menuepunkt in der Ribbon-Navigation exakt so heisst -- ohne Scope
        // findet getByText beide (strict-mode violation).
        const kennzahlen = page.getByTestId('lieferant-kennzahlen');
        const gesamtkostenKasten = kennzahlen.getByText('Gesamtkosten', { exact: true }).locator('xpath=..');
        const bestellungenKasten = kennzahlen.getByText('Bestellungen', { exact: true }).locator('xpath=..');
        const artikelKasten = kennzahlen.getByText('Artikel', { exact: true }).locator('xpath=..');
        const lieferzeitKasten = kennzahlen.getByText('Lieferzeit Ø', { exact: true }).locator('xpath=..');

        for (const [name, kasten] of [
            ['Gesamtkosten', gesamtkostenKasten],
            ['Bestellungen', bestellungenKasten],
            ['Artikel', artikelKasten],
            ['Lieferzeit Ø', lieferzeitKasten],
        ] as const) {
            const box = await kasten.boundingBox();
            expect(box, `Kasten "${name}" muss einen messbaren Rahmen haben`).not.toBeNull();
            expect(
                box!.width,
                `Kasten "${name}" ist auf ${box!.width.toFixed(0)}px gequetscht (Spec-Befund 2 nennt 34px/29px; min-w-[7rem] = 112px)`,
            ).toBeGreaterThanOrEqual(100);
        }

        // Reiterleiste: einzeilig, kein overflow-x-auto mehr. Zusaetzlich die
        // Eigenschaft direkt zusichern, die den Fix ausmacht (Nachtrag aus dem
        // Abschnitt-3-Review, Bedenken 2) -- sonst faellt ein Rueckfall auf
        // verstecktes Scrollen nur zufaellig auf.
        const leiste = page.getByRole('tablist', { name: 'Bereiche des Lieferanten' });
        const leisteOverflow = await leiste.evaluate((el) => getComputedStyle(el).overflowX);
        expect(leisteOverflow, 'Reiterleiste darf kein verstecktes Scrollen mehr haben (overflow-x-auto)').toBe('visible');

        const reiterY = await leiste.locator('button').evaluateAll((buttons) =>
            buttons.map((b) => b.getBoundingClientRect().y),
        );
        expect(reiterY.length, 'Reiterleiste: vier Reiter-Knoepfe erwartet').toBe(4);
        const reiterSpanne = Math.max(...reiterY) - Math.min(...reiterY);
        expect(reiterSpanne, `Reiter liegen auf unterschiedlichen Zeilen: ${JSON.stringify(reiterY)}`).toBeLessThanOrEqual(2);

        // Knopfblock: bleibt vollstaendig in der Kopf-Karte UND steht rechts
        // (nicht nur "irgendwo innerhalb") -- die Rezeptur verlangt "ml-auto" am
        // Knopfblock genau dafuer. Rechts heisst hier: die rechte Kante des
        // Knopfs liegt naeher an der rechten Kartenkante als am Kartenmittelpunkt.
        const kopfKarte = page.locator('main').getByRole('heading', { name: LIEFERANT_LANG }).locator('xpath=ancestor::div[contains(@class, "p-6")][1]');
        const karteBox = await kopfKarte.boundingBox();
        const knopfBox = await bearbeiten.boundingBox();
        expect(karteBox, 'Kopf-Karte muss einen messbaren Rahmen haben').not.toBeNull();
        expect(knopfBox, '"Bearbeiten" muss einen messbaren Rahmen haben').not.toBeNull();
        expect(
            knopfBox!.x,
            `"Bearbeiten" (x=${knopfBox!.x.toFixed(0)}) darf nicht links von der Kopf-Karte (x=${karteBox!.x.toFixed(0)}) liegen`,
        ).toBeGreaterThanOrEqual(karteBox!.x - 1);
        expect(
            knopfBox!.x + knopfBox!.width,
            `"Bearbeiten" (rechte Kante ${(knopfBox!.x + knopfBox!.width).toFixed(0)}) muss innerhalb der Kopf-Karte (rechte Kante ${(karteBox!.x + karteBox!.width).toFixed(0)}) liegen`,
        ).toBeLessThanOrEqual(karteBox!.x + karteBox!.width + 1);
        const karteMitte = karteBox!.x + karteBox!.width / 2;
        expect(
            knopfBox!.x,
            `"Bearbeiten" (x=${knopfBox!.x.toFixed(0)}) soll rechts stehen, nicht links (Kartenmitte bei x=${karteMitte.toFixed(0)}) -- ohne "ml-auto" faellt der Knopfblock beim Umbruch nach links (Design-Review Abschnitt 3: x=89 statt x=961 beim Projekt-Editor)`,
        ).toBeGreaterThanOrEqual(karteMitte);

        // Ebene 2: Screenshot + designPruefung inkl. strengePruefungen, muss vor
        // dem Fix an keinTextLaeuftUeber() scheitern (Kasten enger als der Text).
        await designPruefung(page, testInfo, 'lieferant-detail-langer-name', {
            strengePruefungen: true,
            primaerAktion: bearbeiten,
        });

        // Auswertung des Netzwerk-Mitschnitts: die Seite MUSS versuchen, nach
        // draussen zu greifen (GoogleMapsEmbed + pdf.js) -- sonst würde diese
        // Zusicherung nichts beweisen. Aber keine dieser Anfragen darf jemals
        // eine echte Antwort bekommen (der Design-Reviewer hat vor dem Riegel
        // genau 5 Anfragen mitgeschnitten: 2x google.com/maps, je 1x
        // maps.gstatic.com, maps.googleapis.com, cdnjs.cloudflare.com/pdf.js).
        expect(
            fremdeAnfragen.length,
            'Diese Seite muss mindestens einen Griff nach aussen versuchen (GoogleMapsEmbed/pdf.js) -- sonst zeigt der Mitschnitt nichts',
        ).toBeGreaterThan(0);
        expect(
            fremdeAntworten,
            `Es darf keine echte Antwort von ausserhalb kommen (abgebrochen statt beantwortet): ${fremdeAntworten.join(', ')}`,
        ).toEqual([]);
    });

    // Nachbesserung 1, Befund 1 (🔴 Blocker, Design-Review): ein Name aus einem
    // einzigen langen Wort hat kein Leerzeichen, an dem die Zeile umbrechen
    // koennte. Die bisherige Bauweise (min-w-0 am umschliessenden Textblock,
    // break-words auf der <h1>) reicht dafuer nicht -- die <h1> ist selbst ein
    // Flex-Item in "flex items-center gap-3 flex-wrap" und behaelt deshalb
    // min-width: auto (ihre volle, unteilbare Wortbreite). Am Projekt-Editor
    // gemessen: <h1> 999px breit, 411px davon ausserhalb des Titelblocks.
    test('Titel aus einem einzigen langen Wort laeuft nicht ueber die Kennzahlen (kein Leerzeichen zum Umbrechen)', async ({ page }, testInfo) => {
        await stubLieferantDetailApi(page, { name: LIEFERANT_EINWORT_LANG });
        await page.goto(`/lieferanten?lieferantId=${LIEFERANT_ID}&tab=dokumente`);

        const ueberschrift = page.getByRole('heading', { name: LIEFERANT_EINWORT_LANG });
        await expect(ueberschrift).toBeVisible();

        // Titelblock = das aeussere "flex-1 min-w-[18rem]"-div (Rezeptur), nicht
        // der innere "min-w-0"-Textblock -- das ist der Kasten, ueber den die
        // <h1> laut Design-Review tatsaechlich hinauslief.
        const titelblock = ueberschrift.locator('xpath=ancestor::div[contains(@class, "min-w-[18rem]")][1]');
        const titelBox = await titelblock.boundingBox();
        const h1Box = await ueberschrift.boundingBox();
        expect(titelBox, 'Titelblock muss einen messbaren Rahmen haben').not.toBeNull();
        expect(h1Box, '<h1> muss einen messbaren Rahmen haben').not.toBeNull();
        expect(
            h1Box!.x + h1Box!.width,
            `<h1> (rechte Kante ${(h1Box!.x + h1Box!.width).toFixed(0)}px) laeuft ueber ihren Titelblock (rechte Kante ${(titelBox!.x + titelBox!.width).toFixed(0)}px) hinaus -- die <h1> braucht selbst min-w-0, sonst behaelt sie als Flex-Item ihre volle Wortbreite (min-width: auto), unabhaengig von break-words`,
        ).toBeLessThanOrEqual(titelBox!.x + titelBox!.width + 1);

        // Der eigentliche Schaden aus dem Design-Review: die Kennzahlen werden
        // durch den ueberlaufenden Titel unlesbar/ueberlagert. keinTextLaeuftUeber
        // deckt genau das ab (Kennzahl-Kaesten enger als ihr Text).
        await keinTextLaeuftUeber(page);

        await designPruefung(page, testInfo, 'lieferant-detail-einwort-lang', {
            strengePruefungen: true,
            primaerAktion: page.getByRole('button', { name: 'Bearbeiten' }),
        });
    });
});

test.describe('Lieferanten-Uebersicht: vier lange Lieferantennamen (Spec-Befund 4)', () => {
    test('kein Kartentitel einzeilig abgehackt, 3 Karten je Reihe bei 1440, 4 bei 1920', async ({ page }, testInfo) => {
        await stubLieferantenUebersichtApi(page);
        await page.goto('/lieferanten');

        const ueberschrift = page.getByRole('heading', { name: 'LIEFERANTENÜBERSICHT' });
        await expect(ueberschrift).toBeVisible();

        for (const name of LIEFERANTEN_LANG) {
            await expect(page.getByText(name, { exact: true }), `Kartentitel "${name}" fehlt`).toBeVisible();
        }
        // Kuerzung nur als sanktionierter line-clamp-2-Fall mit data-kuerzung-erlaubt
        // erlaubt -- keinTextGekuerzt prueft das unabhaengig von getByText, das
        // auch gekuerzten Text vollstaendig im DOM sieht (siehe Hinweis 5 aus dem
        // Abschnitt-3-Review: irrefuehrende Meldung bei getByText vermeiden).
        await keinTextGekuerzt(page);

        // Kartenraster (Spec-Befund 4): xl:grid-cols-4 -> 2xl:grid-cols-4, also
        // 3 Spalten bei 1440 (< 1536px 2xl-Breakpoint), 4 Spalten bei 1920.
        const kartenY = await page.evaluate((namen: string[]) =>
            namen.map((name) => {
                const heading = Array.from(document.querySelectorAll('h3')).find((h) => h.textContent?.trim() === name);
                return heading ? heading.getBoundingClientRect().y : null;
            }), LIEFERANTEN_LANG);

        const ersteReiheAnzahl = kartenY.filter((y) => y !== null && Math.abs(y - (kartenY[0] ?? 0)) < 5).length;
        const erwartet = Math.min(testInfo.project.name === 'pc-monitor' ? 4 : 3, LIEFERANTEN_LANG.length);
        expect(
            ersteReiheAnzahl,
            `Erwartet ${erwartet} Karten in der ersten Reihe bei ${testInfo.project.name} (${testInfo.project.name === 'pc-monitor' ? 1920 : 1440}px), gemessen: ${JSON.stringify(kartenY)}`,
        ).toBe(erwartet);

        await designPruefung(page, testInfo, 'lieferant-uebersicht-lange-namen', {
            strengePruefungen: true,
            primaerAktion: page.getByRole('button', { name: 'Neuer Lieferant' }),
        });
    });
});
