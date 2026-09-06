import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung, keinHorizontalerUeberlauf, keinTextGekuerzt, keinTextLaeuftUeber } from './hilfen/design';
import { blockiereFremdeNetzwerkzugriffe } from './hilfen/api';

/**
 * Task 3 (Abschnitt 3) aus docs/superpowers/plans/2026-09-05-layout-14-zoll.md:
 * Vier Baustellen in ProjektEditor.tsx -- Reiterleiste, Kopfzeile, Hinweistext,
 * Uebersichtskarte. Diese Spec deckt Kopfzeile + Reiterleiste am schlimmsten
 * Fall der ganzen Spec ab: /projekte?projektId=5&tab=geschaeftsdokumente mit
 * dem langen Bauvorhaben aus der Spec.
 *
 * Vier messbare Zielwerte (Kontext-Log, Auftrag an Task 3 nach dem
 * Design-Review von Abschnitt 2):
 *   1. main.scrollWidth - main.clientWidth === 0 bei pc-14zoll (heute 43px,
 *      verursacht vom Knopfblock der Kopfzeile) -- auf allen sieben Reitern.
 *   2. "mit Anfrage zusammenfuehren" (und "Bearbeiten") liegen vollstaendig
 *      innerhalb der Kopf-Karte, bei 1440 und 1920.
 *   3. Reiterleiste: kein horizontaler Ueberlauf (scrollWidth <= clientWidth)
 *      und kein Reiter verschwindet, bei beiden Groessen (heute 1199px
 *      Bedarf; verfuegbar 916px bei 1440, 1084px bei 1920). Nach dem
 *      Umbenennen + px-3 passen bei 1920 alle sieben in eine Zeile (979px
 *      von 1084px). Bei 1440 reicht es fuer sechs in einer Zeile (841px von
 *      916px), der siebte ("Tagebuch") rutscht in eine zweite Zeile -- das
 *      ist die im Plan (Abschnitt B) und im Kontext-Log-Auftrag an Task 3
 *      ausdruecklich erlaubte Alternative zu striktem Einzeiler ("flex-wrap
 *      ... aber nichts darf verschwinden"), siehe Bedenken im Kontext-Log.
 *   4. designPruefung() als Ganzes (strengePruefungen: true, inkl.
 *      keinHorizontalerUeberlauf auf <main>) laeuft auf dieser Route in
 *      beiden Groessen gruen.
 *
 * Heutiger Zustand (vor dem Fix, verifiziert siehe Kontext-Log Abschnitt 2 /
 * Design-Review): die sieben Reiter ("Ein-/ Ausgangsgeschaeftsdokumente (n)"
 * allein 304px) brauchen 1199px und passen nicht neben die neue Spaltenbreite
 * aus Task 2 (916px bei 1440, 1084px bei 1920 verfuegbar) -- der siebte
 * Reiter waere ohne Fix abgeschnitten bzw. nur per stillem Scrollen
 * erreichbar. Die Kopfzeile hat unabhaengig vom Bauvorhaben-Namen zu wenig
 * Platz fuer Kennzahlen-Reihe + Knopfblock; der Knopf "mit Anfrage
 * zusammenfuehren" wird rechts aus der Kopf-Karte geschoben (43px main-
 * Ueberstand bei pc-14zoll).
 *
 * /api vollstaendig gestubbt (Catch-all + gezielte Overrides, Vorbild:
 * stubbeLieferantApi in e2e/bearbeiten-leiste.spec.ts, dieselben Routen wie
 * Task 2 in e2e/rahmen-detailseite.spec.ts), kein Backend, nur Fantasienamen
 * aus der Spec (DSGVO): Bauvorhaben "Treppenanlage mit Podest und
 * Absturzsicherung Buerogebaeude Beispielstrasse", Kunde "Wohnungsbau-
 * gesellschaft Beispielstadt Nord mbH und Co. Verwaltungs KG", Lieferant
 * "Stahlhandel Beispiel GmbH und Co. KG" (fuer die Eingangsrechnung, noetig
 * damit der Hinweistext -- Zeile 1624 -- ueberhaupt erscheint).
 */

const PROJEKT_ID = 5;
const BAUVORHABEN = 'Treppenanlage mit Podest und Absturzsicherung Bürogebäude Beispielstraße';
const KUNDE = 'Wohnungsbaugesellschaft Beispielstadt Nord mbH und Co. Verwaltungs KG';
const LIEFERANT = 'Stahlhandel Beispiel GmbH und Co. KG';

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

// Nacharbeit Abschnitt 4, Punkt 1 (Code-Review-Befund 4 aus Abschnitt 3): drei
// der vier truncate-Stellen ohne title brauchen realistisch LANGE Werte, um auf
// den tatsaechlichen Spaltenbreiten wirklich zu ueberlaufen -- kurze Dummy-Werte
// waeren faelschlich gruen gewesen, obwohl die Stelle im Code als "truncate"
// markiert war. Lieferantenname bleibt exakt die Spec-Vorgabe (LIEFERANT-Konstante,
// Global Constraints), Betreff und Dateiname sind frei waehlbar und deshalb bewusst
// so lang gewaehlt, wie ein echter Handwerksbetrieb sie eintragen wuerde.
const EMAIL_BETREFF_LANG = 'Rechnung RE-2026-0501 zur Treppenanlage mit Podest und Absturzsicherung Bürogebäude Beispielstraße — Zahlungsziel 14 Tage netto, Skonto entfällt';
const EINGANGSRECHNUNG_DATEINAME_LANG = 'eingangsrechnung-stahlhandel-beispiel-gmbh-und-co-kg-fuer-treppenanlage-mit-podest-und-absturzsicherung-buerogebaeude-beispielstrasse-projekt-a-2026-0005-rechnung-er-2026-0601-gescannt-2026-02-05.pdf';
const KUNDEN_EMAIL_LANG = 'verwaltung.rechnungswesen@wohnungsbaugesellschaft-beispielstadt-nord-immobilienverwaltung.example';

const DUMMY_AUSGANGSDOKUMENT = {
    id: 501,
    dokumentNummer: 'RE-2026-0501',
    typ: 'RECHNUNG' as const,
    datum: '2026-02-01',
    betreff: EMAIL_BETREFF_LANG,
    betragNetto: 4200,
    betragBrutto: 4998,
    gebucht: false,
    storniert: false,
    bearbeitbar: true,
    projektId: PROJEKT_ID,
};

const DUMMY_EINGANGSRECHNUNG = {
    id: 601,
    dokumentId: 601,
    geschaeftsdokumentId: 601,
    dokumentNummer: 'ER-2026-0601',
    dateiname: EINGANGSRECHNUNG_DATEINAME_LANG,
    dokumentDatum: '2026-02-05',
    gesamtbetrag: 1500,
    prozent: 100,
    berechneterBetrag: 1500,
    beschreibung: 'Material für Treppenanlage',
    lieferantId: 21,
    lieferantName: LIEFERANT,
    pdfUrl: '/dummy-lieferantenrechnung.pdf',
};

// Bewusst OHNE strasse/plz/ort: sonst rendert GoogleMapsEmbed ein echtes
// <iframe src="https://www.google.com/maps?..."> -- ein Netzwerkzugriff, den
// dieser rein gestubbte Test nicht braucht und nicht ausloesen soll.
const DUMMY_PROJEKT = {
    id: PROJEKT_ID,
    bauvorhaben: BAUVORHABEN,
    kunde: KUNDE,
    kundenId: 3,
    kundennummer: 'K-1003',
    auftragsnummer: 'A-2026-0005',
    anlegedatum: '2026-01-15',
    bruttoPreis: 125000,
    bezahlt: false,
    abgeschlossen: false,
    kundenEmails: [KUNDEN_EMAIL_LANG],
    materialkosten: [],
    artikel: [],
    produktkategorien: [],
    zeiten: [],
    emails: [],
};

/** Stubbt alle /api-Routen fuer den Deep-Link auf die Projekt-Detailseite (Vorbild: Task 2). */
async function stubProjektApi(page: Page) {
    await page.route('**/api/**', (route) => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        const methode = request.method();

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
            return json(route, { projekte: [], gesamt: 0 });
        }
        if (pfad === '/api/projekte/jahre') return json(route, []);
        if (pfad === '/api/projekte/freigabe-status') return json(route, {});
        if (pfad === `/api/projekte/${PROJEKT_ID}`) return json(route, DUMMY_PROJEKT);
        if (pfad === `/api/projekte/${PROJEKT_ID}/notizen`) return json(route, []);
        if (pfad === `/api/projekte/${PROJEKT_ID}/dokumente`) return json(route, []);
        if (pfad === `/api/projekte/${PROJEKT_ID}/eingangsrechnungen`) return json(route, [DUMMY_EINGANGSRECHNUNG]);
        if (pfad === `/api/ausgangs-dokumente/projekt/${PROJEKT_ID}`) return json(route, [DUMMY_AUSGANGSDOKUMENT]);
        if (pfad === '/api/ausgangs-dokumente/freigabe-status') return json(route, {});

        // Standardantwort fuer alles Weitere: leere Liste statt 404 -- fuer
        // diesen Ablauf irrelevante Endpunkte sollen die Seite nicht mit
        // einem Fehlerzustand fuellen.
        return json(route, []);
    });
}

/** Die sieben Reiter-Knoepfe -- gefunden ueber den unveraenderten Anker "Zeiten". */
function reiterContainer(page: Page) {
    return page.getByRole('button', { name: /^Zeiten/ }).locator('xpath=..');
}

/** Naechster Card-Vorfahre (Klasse "shadow-sm", siehe components/ui/card.tsx) -- hier die Kopf-Karte. */
function naechsteKarte(locator: ReturnType<Page['locator']>) {
    return locator.locator(
        'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " shadow-sm ")][1]',
    );
}

function liegtVollstaendigIn(
    innen: { x: number; y: number; width: number; height: number },
    aussen: { x: number; y: number; width: number; height: number },
    toleranz = 1,
) {
    return (
        innen.x >= aussen.x - toleranz &&
        innen.y >= aussen.y - toleranz &&
        innen.x + innen.width <= aussen.x + aussen.width + toleranz &&
        innen.y + innen.height <= aussen.y + aussen.height + toleranz
    );
}

test.describe('Projekt-Detailseite im schlimmsten Fall: Kopfzeile und Reiterleiste', () => {
    test('Reiterleiste ohne Ueberlauf und ohne verschwindende Reiter, Knoepfe in der Kopf-Karte, main ueberlaeuft auf keinem Reiter', async ({ page }, testInfo) => {
        await stubProjektApi(page);
        await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);

        const bearbeitenButton = page.getByRole('button', { name: 'Bearbeiten' }).first();
        const mergeButton = page.getByRole('button', { name: 'mit Anfrage zusammenführen' });
        await expect(bearbeitenButton).toBeVisible();
        await expect(mergeButton).toBeVisible();

        // (a) Reiterleiste: kein horizontaler Ueberlauf (scrollWidth <=
        // clientWidth -- Auftrag Task 3, Punkt 3), kein Reiter verschwindet,
        // und -- verschaerft in der Nacharbeit Abschnitt 4, Punkt 2 -- alle
        // sieben Reiter stehen bei 1440 auf DERSELBEN y-Position. Bis
        // Abschnitt 3 durfte "Tagebuch" allein in eine zweite Zeile rutschen
        // (Design-Review Abschnitt 3: 916px verfuegbar, 978px Bedarf, 62px
        // fehlten); die Trennlinie lag dadurch unter beiden Zeilen und der
        // rose Unterstrich des aktiven Reiters schwebte mitten in der Karte.
        // gap-2 -> gap-1 und px-3 -> px-2 (siehe ProjektEditor.tsx) senken den
        // Bedarf auf 899px -- damit reicht eine Zeile.
        const container = reiterContainer(page);
        const tabButtons = container.getByRole('button');
        await expect(tabButtons, 'Erwartet genau sieben Reiter-Knoepfe').toHaveCount(7);
        for (const button of await tabButtons.all()) {
            await expect(button).toBeVisible();
        }
        const tabBoxen = await Promise.all((await tabButtons.all()).map((b) => b.boundingBox()));
        for (const box of tabBoxen) {
            expect(box, 'jeder Reiter-Knopf muss einen messbaren Rahmen haben').not.toBeNull();
        }
        const containerMasse = await container.evaluate((el) => ({ scrollWidth: el.scrollWidth, clientWidth: el.clientWidth }));
        expect(
            containerMasse.scrollWidth,
            `Reiterleiste laeuft ueber: Container ${containerMasse.clientWidth}px breit, Inhalt braucht ${containerMasse.scrollWidth}px`,
        ).toBeLessThanOrEqual(containerMasse.clientWidth + 2);
        const containerBox = (await container.boundingBox())!;
        for (let i = 0; i < tabBoxen.length; i++) {
            const box = tabBoxen[i]!;
            expect(
                box.x + box.width,
                `Reiter Nr. ${i + 1} (x=${box.x.toFixed(0)}, Breite=${box.width.toFixed(0)}) ragt rechts aus der Reiterleiste (Container-Breite ${containerBox.width.toFixed(0)}px)`,
            ).toBeLessThanOrEqual(containerBox.x + containerBox.width + 2);
        }
        // Verschaerfte Zusicherung (Nacharbeit Abschnitt 4, Punkt 2): alle
        // sieben y-Werte muessen (bis auf Sub-Pixel-Rundung) gleich sein --
        // vorher war "nichts verschwindet" die Abnahme, jetzt "eine Zeile".
        const yWerte = tabBoxen.map((b) => b!.y);
        const yAbweichung = Math.max(...yWerte) - Math.min(...yWerte);
        console.log(
            `[Reiterleiste ${testInfo.project.name}] y-Werte: ${yWerte.map((y) => y.toFixed(1)).join(', ')}; Container ${containerBox.width.toFixed(0)}px breit, Inhalt braucht ${containerMasse.scrollWidth}px.`,
        );
        expect(
            yAbweichung,
            `Reiterleiste ist nicht einzeilig -- y-Werte der sieben Reiter: ${yWerte.map((y) => y.toFixed(1)).join(', ')}`,
        ).toBeLessThanOrEqual(2);

        // Reiterleiste darf kein verstecktes Scrollen zurueckbekommen
        // (Nacharbeit Abschnitt 4, Punkt 7 -- Code-Review-Hinweis 2: ein
        // Rueckfall auf overflow-x-auto wurde bisher nur zufaellig bemerkt,
        // weil die Leiste vorher knapp zu breit war).
        const reiterleisteOverflowX = await container.evaluate((el) => getComputedStyle(el).overflowX);
        expect(
            reiterleisteOverflowX,
            `Reiterleiste hat overflow-x: ${reiterleisteOverflowX} -- verstecktes Scrollen statt Umbruch waere ein Rueckfall`,
        ).toBe('visible');

        // (b) "Bearbeiten" und "mit Anfrage zusammenfuehren" liegen vollstaendig
        // innerhalb der Kopf-Karte (Spec-Abnahme Punkt 2 / Auftrag Task 3).
        const kopfKarte = naechsteKarte(mergeButton);
        const karteBox = await kopfKarte.boundingBox();
        expect(karteBox, 'Kopf-Karte muss einen messbaren Rahmen haben').not.toBeNull();
        const bearbeitenBox = await bearbeitenButton.boundingBox();
        const mergeBox = await mergeButton.boundingBox();
        expect(bearbeitenBox, '"Bearbeiten" muss einen messbaren Rahmen haben').not.toBeNull();
        expect(mergeBox, '"mit Anfrage zusammenführen" muss einen messbaren Rahmen haben').not.toBeNull();
        expect(
            liegtVollstaendigIn(bearbeitenBox!, karteBox!),
            `"Bearbeiten" (x=${bearbeitenBox!.x.toFixed(0)}, rechts=${(bearbeitenBox!.x + bearbeitenBox!.width).toFixed(0)}) liegt nicht vollstaendig in der Kopf-Karte (x=${karteBox!.x.toFixed(0)}, rechts=${(karteBox!.x + karteBox!.width).toFixed(0)})`,
        ).toBe(true);
        expect(
            liegtVollstaendigIn(mergeBox!, karteBox!),
            `"mit Anfrage zusammenführen" (x=${mergeBox!.x.toFixed(0)}, rechts=${(mergeBox!.x + mergeBox!.width).toFixed(0)}) liegt nicht vollstaendig in der Kopf-Karte (x=${karteBox!.x.toFixed(0)}, rechts=${(karteBox!.x + karteBox!.width).toFixed(0)})`,
        ).toBe(true);

        // Nacharbeit Abschnitt 4, Punkt 3: der Knopfblock muss RECHTS stehen
        // (x-Position groesser als die Kartenmitte), nicht nur "irgendwo in
        // der Karte" -- ohne ml-auto faellt er beim Umbruch an den linken
        // Kartenrand (im Design-Review gemessen: x=89 statt x=961 bei 1440px).
        const knopfblock = bearbeitenButton.locator('xpath=..');
        const knopfblockBox = await knopfblock.boundingBox();
        expect(knopfblockBox, 'Knopfblock muss einen messbaren Rahmen haben').not.toBeNull();
        const karteMitteX = karteBox!.x + karteBox!.width / 2;
        expect(
            knopfblockBox!.x,
            `Knopfblock (x=${knopfblockBox!.x.toFixed(0)}) steht nicht rechts von der Kartenmitte (${karteMitteX.toFixed(0)}) -- ml-auto fehlt oder wirkt nicht`,
        ).toBeGreaterThan(karteMitteX);

        // Hinweistext (Zeile 1624) wird mit umbenannt -- sichtbar im Reiter
        // "Material" (vormals "Materialkosten"), weil dort die
        // Eingangsrechnungen-Summe steht. Nur pruefbar, wenn mindestens eine
        // Eingangsrechnung vorhanden ist (siehe DUMMY_EINGANGSRECHNUNG oben).
        await container.getByRole('button', { name: /^Material/ }).click();
        await expect(page.getByText('siehe Reiter "Geschäftsdokumente"')).toBeVisible();

        // (c) designPruefung() als Ganzes (Auftrag Task 3, Punkt 4): auf dem
        // urspruenglich verlinkten Reiter "Geschaeftsdokumente".
        await container.getByRole('button', { name: /^Geschäftsdokumente/ }).click();
        await expect(page.getByRole('heading', { name: 'Ausgangsgeschäftsdokumente' })).toBeVisible();

        // Nacharbeit Abschnitt 4, Punkt 1 (Code-Review-Befund 4): vier
        // truncate-Stellen ohne title schnitten auf echten Daten Wichtiges ab
        // -- Kunden-E-Mail in der schmalen rechten Spalte (~322px, schlimmster
        // Fall), Lieferantenname und Dateiname der Eingangsrechnung,
        // E-Mail-Betreff. keinTextGekuerzt() erwischt alle vier ueber ihre
        // (damals) "truncate"-Klasse und listet sie mit vollstaendigem Text in
        // der Fehlermeldung -- das lange Fixture-Material oben macht den
        // Ueberlauf auf den echten Spaltenbreiten erst sichtbar.
        await keinTextGekuerzt(page);

        await designPruefung(page, testInfo, 'projekt-detail-geschaeftsdokumente', {
            strengePruefungen: true,
            primaerAktion: bearbeitenButton,
        });

        // Spec-Abnahme Punkt 1: main hat auf JEDEM der sieben Reiter keinen
        // Ueberstand -- "auf allen sieben Reitern gleich, mit und ohne Daten"
        // (Spec-Befund 1). Jeden Reiter einmal anklicken und main messen.
        const reiterNamen = [/^Zeiten/, /^Material/, /^E-Mails/, /^Geschäftsdokumente/, /^Dateien/, /^Beschreibung/, /^Tagebuch/];
        for (const name of reiterNamen) {
            await container.getByRole('button', { name }).click();
            const ueberstand = await page.locator('main').evaluate((el) => el.scrollWidth - el.clientWidth);
            expect(
                ueberstand,
                `main hat auf Reiter "${name}" einen Ueberstand von ${ueberstand}px (Ziel: 0)`,
            ).toBe(0);
        }
    });
});

// Nachbesserung 1 (Design-Review, 🔴): bei EINEM einzigen langen Wort (kein
// Leerzeichen, an dem normal umgebrochen werden koennte) lief die <h1> quer
// ueber die Kennzahlen-Reihe -- gemessen 999px breit, 411px (1440) bzw. 187px
// (1920) Ueberstand ueber den Titelblock hinaus, "BRUTTO"/"NETTO" darunter
// unlesbar, der Titel selbst am Knopf "Bearbeiten" abgeschnitten. Ursache: die
// <h1> ist selbst Flex-Item (in "flex items-center gap-3 flex-wrap") und
// behaelt ihr eigenes min-width: auto -- break-words senkt die
// Mindestinhaltsbreite eines Flex-Items nicht, das min-w-0 am umschliessenden
// div (Abschnitt 4) wirkt nur auf den Titelblock als Ganzes, nicht auf die
// <h1> selbst. Keine bisherige Zusicherung (auch nicht designPruefung) hat das
// gefangen: main.scrollWidth blieb 0 (die <h1> ueberlappt nur andere Elemente
// derselben Karte, verursacht aber keinen Seiten-Ueberlauf), und
// h1.scrollWidth == h1.clientWidth (die <h1> selbst ist nicht gekuerzt, sie
// ist einfach zu breit).
const KOMPOSITA_EIN_WORT = 'Absturzsicherungspodesttreppenanlagenmontagearbeitenüberwachungsdokumentation';

// Task 12 (Abschnitt 8, "zweiter Mechanismus", Code-Review Abschnitt 7): die
// Seitenspalte "Projektdaten" (Kunde, Kundennummer, Ansprechpartner,
// Auftragsnummer, Projektadresse) und die Kopf-Untertitelzeilen (Kunde,
// Adresse) rendern Werte als reinen Block-<p> bzw. als anonymes Flex-Item
// ohne eigenes min-w-0 -- DUMMY_PROJEKT oben setzt weder kundeDto noch
// strasse/plz/ort, deshalb wurden diese Stellen nie mit echt ueberlaufendem
// Inhalt gerendert. Bindestrichlose Fantasieworte (DSGVO: kein echter
// Personen-/Firmenbezug), Strasse absichtlich mit einem Leerzeichen
// (schwaechste Haertung, siehe kriterien.md), aber immer noch ein
// 37-Zeichen-Stueck ohne Trennstelle.
const KUNDE_EIN_WORT = 'Beispielstadtverwaltungsimmobiliengesellschaftmbh';
const ANSPRECHPARTNER_LANG = 'Ansprechpartnerkoordinationsverwaltungsbeauftragte';
const STRASSE_LANG = 'Kreisverkehrsplatzrandbebauungsstraße 128a';

// Nachbesserung 1, Messung ohne Fix (Auftrag des Koordinators): die
// Projekt-Reiterleiste hat bei 1440 nur noch 17px Reserve (899px Bedarf bei
// den einstelligen Spec-Zaehlern, 916px verfuegbar). Zweistellige Zaehler
// ("Zeiten (34)" usw., wie sie ein Betrieb mit viel Historie tatsaechlich
// hat) brauchen mehr Platz -- diese Fixture misst, ob und wie die Leiste
// dann umbricht. Kein Fix, nur eine Messung fuers Kontext-Log.
test.describe('Projekt-Reiterleiste: Messung mit zweistelligen Zaehlern (kein Fix)', () => {
    test('misst, ob die Reiterleiste bei zweistelligen Zaehlern umbricht', async ({ page }, testInfo) => {
        const N = 34;
        const viele = <T,>(vorlage: T, anzahl: number): T[] =>
            Array.from({ length: anzahl }, (_, i) => ({ ...vorlage, id: i + 1000 }));

        await page.route('**/api/**', (route) => {
            const request = route.request();
            const pfad = new URL(request.url()).pathname;
            const methode = request.method();

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
            if (pfad === '/api/projekte' && methode === 'GET') return json(route, { projekte: [], gesamt: 0 });
            if (pfad === '/api/projekte/jahre') return json(route, []);
            if (pfad === '/api/projekte/freigabe-status') return json(route, {});
            if (pfad === `/api/projekte/${PROJEKT_ID}`) {
                return json(route, {
                    ...DUMMY_PROJEKT,
                    zeiten: viele({}, N),
                    materialkosten: viele({}, N),
                    emails: viele({}, N),
                });
            }
            if (pfad === `/api/projekte/${PROJEKT_ID}/notizen`) return json(route, viele({ id: 0, kurzbeschreibung: 'x' }, N));
            if (pfad === `/api/projekte/${PROJEKT_ID}/dokumente`) return json(route, viele({ id: 0, dateiname: 'x.pdf' }, N));
            if (pfad === `/api/projekte/${PROJEKT_ID}/eingangsrechnungen`) return json(route, []);
            if (pfad === `/api/ausgangs-dokumente/projekt/${PROJEKT_ID}`) return json(route, viele(DUMMY_AUSGANGSDOKUMENT, N));
            if (pfad === '/api/ausgangs-dokumente/freigabe-status') return json(route, {});
            return json(route, []);
        });
        await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);

        // Zaehler wirklich zweistellig? Sonst waere die Messung wertlos.
        await expect(page.getByRole('button', { name: `Zeiten (${N})` })).toBeVisible();
        await expect(page.getByRole('button', { name: `Material (${N})` })).toBeVisible();
        await expect(page.getByRole('button', { name: `E-Mails (${N})` })).toBeVisible();
        await expect(page.getByRole('button', { name: `Geschäftsdokumente (${N})` })).toBeVisible();
        await expect(page.getByRole('button', { name: `Dateien (${N})` })).toBeVisible();
        await expect(page.getByRole('button', { name: `Tagebuch (${N})` })).toBeVisible();

        const container = reiterContainer(page);
        const tabButtons = container.getByRole('button');
        const tabBoxen = await Promise.all((await tabButtons.all()).map((b) => b.boundingBox()));
        const containerBox = (await container.boundingBox())!;
        const containerMasse = await container.evaluate((el) => ({ scrollWidth: el.scrollWidth, clientWidth: el.clientWidth }));
        const yWerte = tabBoxen.map((b) => b!.y);
        const zeilen = new Set(tabBoxen.map((b) => Math.round(b!.y))).size;
        const proZeile = zeilen === 1
            ? [tabBoxen.length]
            : Array.from(new Set(yWerte)).sort((a, b) => a - b).map((y) => tabBoxen.filter((b) => Math.abs(b!.y - y) < 2).length);

        console.log(
            `[Messung zweistellige Zaehler, ${testInfo.project.name}] Container ${containerBox.width.toFixed(0)}px verfuegbar, Reiter brauchen ${containerMasse.scrollWidth}px insgesamt; ${zeilen} Zeile(n), Verteilung ${JSON.stringify(proZeile)}; y-Werte: ${yWerte.map((y) => y.toFixed(0)).join(', ')}.`,
        );

        // Kein Fix -- nur die Nicht-Regressions-Garantien bleiben Pflicht:
        // kein Reiter verschwindet, kein seitliches Verstecken.
        await expect(tabButtons, 'Erwartet weiterhin genau sieben Reiter-Knoepfe').toHaveCount(7);
        for (const button of await tabButtons.all()) {
            await expect(button).toBeVisible();
        }
        expect(
            containerMasse.scrollWidth,
            `Reiterleiste laeuft ueber: Container ${containerMasse.clientWidth}px breit, Inhalt braucht ${containerMasse.scrollWidth}px`,
        ).toBeLessThanOrEqual(containerMasse.clientWidth + 2);
    });
});

test.describe('Projekt-Kopfzeile: <h1> bei einem einzigen langen Wort ohne Leerzeichen', () => {
    test('<h1> ragt nicht rechts aus dem Titelblock', async ({ page }) => {
        await stubProjektApi(page);
        await page.route(`**/api/projekte/${PROJEKT_ID}`, (route) => {
            if (route.request().method() !== 'GET') return route.fallback();
            return json(route, { ...DUMMY_PROJEKT, bauvorhaben: KOMPOSITA_EIN_WORT });
        });
        await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);

        const titel = page.getByRole('heading', { name: KOMPOSITA_EIN_WORT });
        await expect(titel).toBeVisible();

        // Titelblock: das aeussere "flex-1 min-w-[18rem]"-div (Zurueck-Pfeil,
        // Symbol, Titel, Kunde, Adresse, Auftragsnummer).
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

// Task 12 (Abschnitt 8): systematischer Nachweis fuer die Seitenspalte
// "Projektdaten" und die Kopf-Untertitelzeilen -- alle vier bisher NIE mit
// echt ueberlaufendem Inhalt gerendert (siehe Konstanten oben).
test.describe('Projekt-Seitenspalte "Projektdaten" und Kopf-Untertitel: lange, bindestrichlose Werte', () => {
    test('Kunde, Ansprechpartner und Projektadresse laufen weder im Kasten noch im Titelblock ueber', async ({ page }) => {
        // Muss vor der ersten Navigation stehen: mit gesetzter Adresse rendert
        // GoogleMapsEmbed ein echtes <iframe src="https://www.google.com/maps?...">
        // (siehe Kommentar bei DUMMY_PROJEKT oben) -- dieser Test setzt
        // strasse/plz/ort bewusst, braucht den Riegel also anders als die
        // uebrigen Tests dieser Datei.
        await blockiereFremdeNetzwerkzugriffe(page);
        await stubProjektApi(page);
        await page.route(`**/api/projekte/${PROJEKT_ID}`, (route) => {
            if (route.request().method() !== 'GET') return route.fallback();
            return json(route, {
                ...DUMMY_PROJEKT,
                kunde: KUNDE_EIN_WORT,
                kundeDto: { ansprechspartner: ANSPRECHPARTNER_LANG },
                strasse: STRASSE_LANG,
                plz: '99999',
                ort: 'Musterstadt',
            });
        });
        await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);

        await expect(page.getByRole('heading', { name: BAUVORHABEN })).toBeVisible();

        // Seitenspalte: der Wert-<p> selbst ist ein normaler Block (kein
        // Flex-Item) -- ohne break-words ueberlaeuft er unsichtbar (scrollWidth
        // > clientWidth), OHNE dass sich seine eigene boundingBox() aendert
        // (ein Block ohne explizite Breite bleibt bei "Breite = Elternbreite",
        // der Text malt nur ueber den Rand hinaus). Deshalb direkt scrollWidth/
        // clientWidth pruefen statt eine Positions-Geometrie zu vergleichen,
        // die genau diesen Fall nicht sieht (siehe keinTextLaeuftUeber in
        // e2e/hilfen/design.ts -- dieselbe Messmethode).
        const seitenKarte = page.getByRole('heading', { name: 'Projektdaten' }).locator(
            'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " shadow-sm ")][1]',
        );
        const pruefeWertBleibtImKasten = async (wert: string, feldname: string, exact = true) => {
            const wertElement = seitenKarte.getByText(wert, { exact });
            await expect(wertElement, `${feldname}-Wert "${wert}" fehlt`).toBeVisible();
            const ueberstand = await wertElement.evaluate((el) => el.scrollWidth - el.clientWidth);
            expect(
                ueberstand,
                `${feldname}-Wert "${wert.slice(0, 30)}..." laeuft ${ueberstand}px ueber seinen eigenen Kasten -- braucht break-words am Wert-<p>`,
            ).toBeLessThanOrEqual(2);
        };
        await pruefeWertBleibtImKasten(KUNDE_EIN_WORT, 'Kunde (Seitenspalte)');
        await pruefeWertBleibtImKasten(ANSPRECHPARTNER_LANG, 'Ansprechpartner');
        // exact:false: Strasse und PLZ/Ort stehen in zwei <p>, "Kreisverkehr...
        // 128a" ist der volle Text des ersten -- ein exakter Treffer reicht.
        await pruefeWertBleibtImKasten(STRASSE_LANG, 'Projektadresse (Strasse)');

        // Kopf-Untertitel: derselbe Kunde-Wert steht ein zweites Mal im
        // Titelblock, dort als anonymes Flex-Item in einem eigenen <span
        // min-w-0 break-words> (siehe Kommentar in ProjektEditor.tsx). Ohne
        // min-w-0 auf dem Span waechst er ueber den Titelblock hinaus, auch
        // wenn break-words gesetzt ist (overflow-wrap: break-word senkt die
        // automatische Mindestbreite eines Flex-Items nicht).
        const titelblock = page.getByRole('heading', { name: BAUVORHABEN }).locator(
            'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " min-w-[18rem] ")][1]',
        );
        const kundeImKopf = titelblock.getByText(KUNDE_EIN_WORT, { exact: true });
        await expect(kundeImKopf).toBeVisible();
        const kundeImKopfBox = await kundeImKopf.boundingBox();
        const titelblockBox = await titelblock.boundingBox();
        expect(kundeImKopfBox, 'Kunde im Kopf-Untertitel muss einen messbaren Rahmen haben').not.toBeNull();
        expect(titelblockBox, 'Titelblock muss einen messbaren Rahmen haben').not.toBeNull();
        const kopfUeberstand = (kundeImKopfBox!.x + kundeImKopfBox!.width) - (titelblockBox!.x + titelblockBox!.width);
        expect(
            kopfUeberstand,
            `Kunde "${KUNDE_EIN_WORT}" ragt ${kopfUeberstand.toFixed(0)}px rechts aus dem Titelblock -- braucht min-w-0 am umschliessenden <span>`,
        ).toBeLessThanOrEqual(2);

        // Nacharbeit Abschnitt 9 (Code-Review Abschnitt 8): dieser Test rief
        // bisher nur keinTextGekuerzt() -- anders als seine Anfrage-Schwester
        // in anfrage-layout.spec.ts, die beide allgemeinen Pruefungen zusaetzlich
        // fuehrt. Nachgezogen, damit dieselbe Route auch auf ueberlaufenden
        // (statt nur gekuerzten) Text und main-Ueberstand geprueft wird.
        await keinTextGekuerzt(page);
        await keinTextLaeuftUeber(page);
        await keinHorizontalerUeberlauf(page);
    });
});

/**
 * Nacharbeit Abschnitt 9 (Code-Review Abschnitt 8, "acht der 49 Aenderungen aus
 * Task 12 sind Attrappen"): sieben `break-words`-Spans in dieser Datei waren
 * Flex-Items OHNE eigenes `min-w-0` (die drei Zeiten-Zeilen, zwei Zeilen der
 * Dokumentenketten-Metazeile, zwei der Eingangsrechnungs-Zuordnungen) -- ihre
 * automatische Mindestbreite blieb die Breite ihres einzigen, unteilbaren
 * Wortes, `break-words` kam nie zum Zug. Ein Flex-Item, das seine automatische
 * Mindestbreite behaelt, ueberragt sich NICHT selbst (sein scrollWidth bleibt
 * gleich seinem clientWidth) -- es sprengt nur die Zeile, in der es steckt.
 * Deshalb wird hier konsequent die umschliessende Flex-Zeile gemessen, nicht
 * der Span selbst (siehe kriterien.md, "min-w-0 muss auf jede Ebene").
 *
 * Keine dieser drei Stellen (Zeiten, Dokumentenketten-Metazeile,
 * Eingangsrechnungs-Zuordnung) wurde bisher mit echten Daten gerendert (siehe
 * Code-Review Abschnitt 8: "24 von 24 Aenderungen betreffen Bereiche, die
 * projekt-detail-layout.spec.ts nie fuellt").
 */
function spacelosesWort(laenge: number, praefix = ''): string {
    const stamm = 'Verwaltungskoordinationsbeschaffungsdokumentationsprozessabteilung';
    let ergebnis = praefix;
    while (ergebnis.length < laenge) ergebnis += stamm;
    return ergebnis.slice(0, laenge);
}

const KATEGORIE_LANG = spacelosesWort(140, 'Kategorie');
const ARBEITSGANG_LANG = spacelosesWort(140, 'Arbeitsgang');
const ZEITEN_MITARBEITER_VORNAME = 'Bernhardine';
const ZEITEN_MITARBEITER_NACHNAME_LANG = spacelosesWort(140, 'Nachname');
const DOK_KUNDENNAME_LANG = spacelosesWort(180, 'Kundenname');
const DOK_ERSTELLT_VON_LANG = spacelosesWort(180, 'Ersteller');
const ZUGEORDNET_VON_LANG = spacelosesWort(150, 'Zugeordnetvon');
const WEITERE_ZUORDNUNG_VON_LANG = spacelosesWort(150, 'Weiterezuordnung');
// Nachtrag Abschnitt 10 (Code-Review Abschnitt 9, Hinweis 1; Design-Review
// Abschnitt 9, Punkt 2): "Weitere Zuordnungen" -- max-w-[200px] ist weg
// (Task 13), aber ohne min-w-0 faellt der Beschreibungs-Span auf min-content
// zurueck und break-words wirkt nicht mehr. 200 Zeichen, damit der Effekt bei
// 1440 deutlich rot wird (Design-Review mass 254px Zeilen-/237px
// Karten-Ueberstand mit derselben Laenge).
const BESCHREIBUNG_LANG = spacelosesWort(200, 'Beschreibung');

test.describe('ProjektEditor: sieben min-w-0-Attrappen aus Task 12 (Nacharbeit Abschnitt 9)', () => {
    test('Zeiten-Hierarchie, Dokumentenketten-Metazeile und Eingangsrechnungs-Zuordnung sprengen ihre Zeile nicht', async ({ page }) => {
        const dokMitMetazeile = {
            ...DUMMY_AUSGANGSDOKUMENT,
            kundenName: DOK_KUNDENNAME_LANG,
            erstelltVonName: DOK_ERSTELLT_VON_LANG,
        };
        const eingangsrechnungMitZuordnung = {
            ...DUMMY_EINGANGSRECHNUNG,
            zugeordnetVonName: ZUGEORDNET_VON_LANG,
            zugeordnetAm: '2026-02-06T10:00:00Z',
            alleZuordnungen: [
                {
                    projektId: PROJEKT_ID + 999, // ein ANDERES Projekt -> zaehlt als "andereZuordnung"
                    kostenstelleId: 77,
                    kostenstelleName: 'Zentrale Kostenstelle',
                    prozent: 25,
                    berechneterBetrag: 375,
                    beschreibung: BESCHREIBUNG_LANG,
                    zugeordnetVonName: WEITERE_ZUORDNUNG_VON_LANG,
                },
            ],
        };
        await page.route('**/api/**', (route) => {
            const request = route.request();
            const pfad = new URL(request.url()).pathname;
            const methode = request.method();
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
            if (pfad === '/api/projekte' && methode === 'GET') return json(route, { projekte: [], gesamt: 0 });
            if (pfad === '/api/projekte/jahre') return json(route, []);
            if (pfad === '/api/projekte/freigabe-status') return json(route, {});
            if (pfad === `/api/projekte/${PROJEKT_ID}`) {
                return json(route, {
                    ...DUMMY_PROJEKT,
                    zeiten: [{
                        produktkategorie: { bezeichnung: KATEGORIE_LANG },
                        arbeitsgangBeschreibung: ARBEITSGANG_LANG,
                        mitarbeiterVorname: ZEITEN_MITARBEITER_VORNAME,
                        mitarbeiterNachname: ZEITEN_MITARBEITER_NACHNAME_LANG,
                        anzahlInStunden: 3.5,
                        stundensatz: 45,
                    }],
                });
            }
            if (pfad === `/api/projekte/${PROJEKT_ID}/notizen`) return json(route, []);
            if (pfad === `/api/projekte/${PROJEKT_ID}/dokumente`) return json(route, []);
            if (pfad === `/api/projekte/${PROJEKT_ID}/eingangsrechnungen`) return json(route, [eingangsrechnungMitZuordnung]);
            if (pfad === `/api/ausgangs-dokumente/projekt/${PROJEKT_ID}`) return json(route, [dokMitMetazeile]);
            if (pfad === '/api/ausgangs-dokumente/freigabe-status') return json(route, {});
            return json(route, []);
        });
        await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=zeiten`);

        /** Misst die unmittelbar umschliessende Flex-Zeile des Wert-Spans (xpath=..
         * per Standard), nicht den Span selbst -- siehe Erklaerung im
         * Beschreibungs-Kommentar oben. `xpathZurZeile` erlaubt einen anderen
         * Aufstieg fuer Faelle, in denen der Wert-Span selbst verschachtelt ist
         * (siehe "Zugeordnet von" unten). */
        const pruefeZeileUeberragtNicht = async (
            locator: ReturnType<Page['getByText']>,
            feldname: string,
            xpathZurZeile: string = '..',
        ) => {
            await expect(locator, `${feldname} fehlt`).toBeVisible();
            const zeile = locator.locator(`xpath=${xpathZurZeile}`);
            const ueberstand = await zeile.evaluate((el) => el.scrollWidth - el.clientWidth);
            expect(
                ueberstand,
                `${feldname}: umschliessende Zeile laeuft ${ueberstand}px ueber -- braucht min-w-0 am Span`,
            ).toBeLessThanOrEqual(2);
        };

        await pruefeZeileUeberragtNicht(page.getByText(KATEGORIE_LANG, { exact: true }), 'Zeiten Ebene 1 (Kategorie)');
        await pruefeZeileUeberragtNicht(page.getByText(ARBEITSGANG_LANG, { exact: true }), 'Zeiten Ebene 2 (Arbeitsgang)');
        await pruefeZeileUeberragtNicht(
            page.getByText(`${ZEITEN_MITARBEITER_VORNAME} ${ZEITEN_MITARBEITER_NACHNAME_LANG}`, { exact: true }),
            'Zeiten Ebene 3 (Mitarbeiter)',
        );

        await page.getByRole('button', { name: /^Geschäftsdokumente/ }).click();
        await expect(page.getByText(dokMitMetazeile.dokumentNummer, { exact: true })).toBeVisible();
        await pruefeZeileUeberragtNicht(page.getByText(DOK_KUNDENNAME_LANG, { exact: true }), 'Dokumentenketten-Metazeile (Kunde)');
        await pruefeZeileUeberragtNicht(page.getByText(DOK_ERSTELLT_VON_LANG, { exact: true }), 'Dokumentenketten-Metazeile (Erstellt von)');

        await expect(page.getByText(eingangsrechnungMitZuordnung.dateiname)).toBeVisible();
        // Nachtrag Abschnitt 10 (Code-Review Abschnitt 9, Hinweis 2): der
        // Wert steckt hier in einem VERSCHACHTELTEN Span
        // (<span class="break-words min-w-0">Zugeordnet von <span
        // class="font-medium ...">{name}</span></span>) -- getByText(exact:
        // false) liefert das am engsten umschliessende Element, das ist der
        // INNERE Span. "xpath=.." traf damit den reparierten break-words/
        // min-w-0-Span selbst statt der Flex-Zeile: dessen eigener scrollWidth
        // bleibt bei fehlendem min-w-0 gleich clientWidth (ein Flex-Item ohne
        // min-w-0 wird als Ganzes breiter, nicht ueber sich selbst hinaus),
        // die Zusicherung bliebe also faelschlich gruen. Deshalb bis zur
        // umschliessenden "flex-wrap"-Zeile hochlaufen.
        await pruefeZeileUeberragtNicht(
            page.getByText(ZUGEORDNET_VON_LANG, { exact: false }),
            'Eingangsrechnung: "Zugeordnet von"',
            'ancestor::div[contains(@class,"flex-wrap")][1]',
        );
        await pruefeZeileUeberragtNicht(
            page.getByText(WEITERE_ZUORDNUNG_VON_LANG, { exact: false }),
            'Eingangsrechnung: "Weitere Zuordnungen" (von ...)',
        );
        // Nachtrag Abschnitt 10 (Code-Review Abschnitt 9, Hinweis 1; von
        // Task 13 selbst eingebaute Attrappe): die Beschreibung ist ein
        // eigener Span ohne verschachteltes Kind, "xpath=.." trifft hier also
        // korrekt die umschliessende flex-wrap-Zeile.
        await pruefeZeileUeberragtNicht(
            page.getByText(BESCHREIBUNG_LANG, { exact: false }),
            'Eingangsrechnung: "Weitere Zuordnungen" (Beschreibung)',
        );

        await keinHorizontalerUeberlauf(page);
    });
});

/**
 * Nacharbeit Abschnitt 9 (Code-Review Abschnitt 8, Befunde 4/5): zwei stille
 * Kuerzungen in einem "DialogContent overflow-hidden", ohne
 * `data-kuerzung-erlaubt` -- nach den Global Constraints regelwidrig. Beide
 * Stellen haengen am selben Dialog ("Rechnung erstellen"): der Betreff steht
 * im Dialogkopf, der Bauabschnitt in den Positionen, wenn die Basis ein
 * SECTION_HEADER-Block enthaelt. Fix: beide Stellen umbrechen lassen statt
 * markieren (Global Constraints: "umbrechen lassen, nicht markieren").
 *
 * Der Betreff-<p> ist ein normaler Block (kein Flex-Item) -- ohne break-words
 * ueberlaeuft er unsichtbar (scrollWidth > clientWidth), ohne dass sich seine
 * boundingBox() aendert. Der Bauabschnitt-<span> dagegen ist Flex-Item einer
 * "flex items-center gap-2"-Zeile -- dieselbe Attrappen-Mechanik wie oben,
 * deshalb wird hier die umschliessende Zeile gemessen.
 */
test.describe('ProjektEditor: zwei stille Kuerzungen im Rechnungs-Dialog (Nacharbeit Abschnitt 9)', () => {
    const BETREFF_EIN_WORT = spacelosesWort(180, 'Betreff');
    const BAUABSCHNITT_LANG = spacelosesWort(140, 'Bauabschnitt');
    const ANGEBOT_ID = 701;
    const DOK_FUER_RECHNUNG = {
        id: ANGEBOT_ID,
        dokumentNummer: 'AN-2026-0701',
        typ: 'ANGEBOT' as const,
        datum: '2026-02-01',
        betreff: BETREFF_EIN_WORT,
        betragNetto: 1000,
        betragBrutto: 1190,
        gebucht: false,
        storniert: false,
        bearbeitbar: true,
        projektId: PROJEKT_ID,
    };
    // Zwei SERVICE-Bloecke: "Teilrechnung" ist nur waehlbar ab mindestens zwei
    // Leistungspositionen (hatGenugPositionen = getAllServiceBlocks(...).length >= 2) --
    // erst dann rendert der Dialog ueberhaupt die Positionsliste mit dem
    // SECTION_HEADER-Block, in dem der Bauabschnitt steckt.
    const POSITIONEN_JSON = JSON.stringify({
        blocks: [
            {
                id: 'sec-1',
                type: 'SECTION_HEADER',
                sectionLabel: BAUABSCHNITT_LANG,
                children: [
                    { id: 'svc-1', type: 'SERVICE', title: 'Position 1', quantity: 1, unit: 'Stk', price: 100 },
                    { id: 'svc-2', type: 'SERVICE', title: 'Position 2', quantity: 1, unit: 'Stk', price: 50 },
                ],
            },
        ],
    });

    test('Betreff und Bauabschnitt werden umgebrochen statt still abgeschnitten', async ({ page }) => {
        await page.route('**/api/**', (route) => {
            const request = route.request();
            const pfad = new URL(request.url()).pathname;
            const methode = request.method();
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
            if (pfad === '/api/projekte' && methode === 'GET') return json(route, { projekte: [], gesamt: 0 });
            if (pfad === '/api/projekte/jahre') return json(route, []);
            if (pfad === '/api/projekte/freigabe-status') return json(route, {});
            if (pfad === `/api/projekte/${PROJEKT_ID}`) return json(route, DUMMY_PROJEKT);
            if (pfad === `/api/projekte/${PROJEKT_ID}/notizen`) return json(route, []);
            if (pfad === `/api/projekte/${PROJEKT_ID}/dokumente`) return json(route, []);
            if (pfad === `/api/projekte/${PROJEKT_ID}/eingangsrechnungen`) return json(route, []);
            if (pfad === `/api/ausgangs-dokumente/projekt/${PROJEKT_ID}`) return json(route, [DOK_FUER_RECHNUNG]);
            if (pfad === '/api/ausgangs-dokumente/freigabe-status') return json(route, {});
            if (pfad === `/api/ausgangs-dokumente/${ANGEBOT_ID}/abrechnungsverlauf`) {
                // restbetrag > 0 noetig, sonst blendet die Komponente den
                // ganzen Abschnitt "Rechnungstyp waehlen" aus (Bedingung
                // "!abrechnungsverlauf || abrechnungsverlauf.restbetrag > 0").
                return json(route, {
                    bereitsAbgerechneteBlockIds: [], positionen: [],
                    restbetrag: 1000, basisdokumentBetragNetto: 1000,
                });
            }
            if (pfad === `/api/ausgangs-dokumente/${ANGEBOT_ID}`) {
                return json(route, { ...DOK_FUER_RECHNUNG, positionenJson: POSITIONEN_JSON });
            }
            return json(route, []);
        });
        await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);

        await expect(page.getByText(DOK_FUER_RECHNUNG.dokumentNummer)).toBeVisible();
        // Aktionsmenue oeffnen (Klick auf die Dokumentkarte, siehe onClick der
        // Karte: "Klick fuer Aktionen, Doppelklick zum Oeffnen") und "Rechnung
        // erstellen" waehlen -- dieselbe Nutzeraktion, die rechnungBasisDok setzt.
        await page.getByText(DOK_FUER_RECHNUNG.dokumentNummer).click();
        await page.getByRole('button', { name: 'Rechnung erstellen' }).click();

        const dialogTitel = page.getByRole('heading', { name: 'Rechnung erstellen' });
        await expect(dialogTitel).toBeVisible();
        // Scoped auf das Dialog-Panel: der Betreff steht ein zweites Mal in der
        // (weiterhin sichtbaren) Dokumentenliste dahinter -- ein ungegrenztes
        // getByText waere mehrdeutig (strict-mode violation).
        const dialogPanel = dialogTitel.locator('xpath=ancestor::div[contains(@class,"rounded-2xl")][1]');

        // Der Bauabschnitt steckt in der Positionsliste, die nur bei
        // rechnungTyp === 'TEILRECHNUNG' rendert -- "Teilrechnung" waehlen
        // (mit zwei Leistungspositionen in der Fixture ist der Knopf aktiv).
        await dialogPanel.getByRole('button', { name: /^Teilrechnung/ }).click();

        // Betreff: getByText(exact:false) trifft das am engsten umschliessende
        // Element -- das ist hier der innere <span>, ein INLINE-Element ohne
        // eigene Layout-Box (clientWidth/scrollWidth sind fuer "display: inline"
        // per Spec immer 0). Deshalb auf das umschliessende <p> hochlaufen --
        // das ist der normale Block (kein Flex-Item), der ohne break-words
        // unsichtbar ueberliefe (scrollWidth > clientWidth), ohne dass sich
        // seine boundingBox() aendert.
        const betreffSpan = dialogPanel.getByText(BETREFF_EIN_WORT, { exact: false });
        await expect(betreffSpan, `Betreff "${BETREFF_EIN_WORT}" fehlt -- still abgeschnitten?`).toBeVisible();
        const betreffElement = betreffSpan.locator('xpath=ancestor::p[1]');
        const betreffUeberstand = await betreffElement.evaluate((el) => el.scrollWidth - el.clientWidth);
        expect(
            betreffUeberstand,
            `Betreff laeuft ${betreffUeberstand}px ueber seinen eigenen Kasten -- braucht break-words am <p>`,
        ).toBeLessThanOrEqual(2);

        // Bauabschnitt: Flex-Item ohne eigene Umbruch-Klasse -- dieselbe
        // Attrappen-Mechanik wie im Test oben, deshalb die umschliessende
        // Zeile pruefen statt den Span selbst.
        const bauabschnittSpan = dialogPanel.getByText(BAUABSCHNITT_LANG, { exact: true });
        await expect(bauabschnittSpan, `Bauabschnitt "${BAUABSCHNITT_LANG}" fehlt -- still abgeschnitten?`).toBeVisible();
        const bauabschnittZeile = bauabschnittSpan.locator('xpath=..');
        const bauabschnittUeberstand = await bauabschnittZeile.evaluate((el) => el.scrollWidth - el.clientWidth);
        expect(
            bauabschnittUeberstand,
            `Bauabschnitt-Zeile laeuft ${bauabschnittUeberstand}px ueber -- braucht min-w-0 + break-words am Span`,
        ).toBeLessThanOrEqual(2);

        // Keine der beiden Stellen darf als "gewollte Kuerzung" markiert sein --
        // die Global Constraints verlangen hier "umbrechen lassen, nicht markieren".
        await expect(betreffElement.locator('xpath=ancestor-or-self::*[@data-kuerzung-erlaubt]')).toHaveCount(0);
        await expect(bauabschnittSpan.locator('xpath=ancestor-or-self::*[@data-kuerzung-erlaubt]')).toHaveCount(0);
    });
});
