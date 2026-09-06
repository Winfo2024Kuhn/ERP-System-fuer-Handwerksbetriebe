import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung, keinTextLaeuftUeber } from './hilfen/design';
import { blockiereFremdeNetzwerkzugriffe } from './hilfen/api';

/**
 * Task 7 (Abschnitt 4) aus docs/superpowers/plans/2026-09-05-layout-14-zoll.md,
 * "Gemeinsame Rezeptur fuer Kopfzeile und Reiterleiste" (Ergebnis aus
 * Abschnitt 3, Design-/Code-Review).
 *
 * Kleinster Task des Vorhabens. Der Plan sagt ausdruecklich voraus, dass die
 * Mitarbeiter-Detailseite bei 1440px heute schon passt ("Mindestbreite links
 * 677px") und schreibt fuer diese Datei nur die Reiterleiste vor ("Sonst
 * nichts an dieser Datei -- Kopf und Karten der Mitarbeiterseite sind nicht
 * Teil der Spec"). Diese Spec prueft trotzdem zusaetzlich die Kopfzeile
 * (Auftrag des Orchestrators: "die ganze Seite gegen die Rezeptur pruefen") --
 * mit einer bewusst extremen Fantasie-Fixture, um einen echten roten Fund zu
 * suchen, statt eine Zusicherung zu schreiben, die nie etwas festhaelt.
 *
 * Ergebnis der Rot-Verifikation (siehe Kontext-Log-Block dieses Tasks fuer
 * die volle Messung): mit einem 41-stelligen, nicht umbrechbaren
 * Fantasie-Nachnamen ("Beispielmusterfrauenbergwaldschmidtstein", EIN Wort
 * ohne Leerzeichen/Bindestrich -- genau der Komposita-Fall, der bei
 * Projekt/Anfrage/Kunde erst durch `min-w-[18rem]` + `break-words` geloest
 * wurde) und einer 44-stelligen Fantasie-Abteilung bleibt die Kopfzeile in
 * BEIDEN Groessen ueberlaufsfrei, die drei Knoepfe stehen vollstaendig
 * rechts, und auch das rechte Seitenpanel (Kontaktdaten-Karte, "nicht Teil
 * der Spec" laut Plan) uebersteht dieselbe Fixture unbeschadet. Grund,
 * nachgemessen: die Kopfzeile hat -- anders als bei Projekt/Anfrage/Kunde/
 * Lieferant -- KEINE Kennzahlen-Reihe, die dem Titelblock Platz wegnimmt,
 * und der Knopfblock (3 Knoepfe, kein Kennzahlen-Kasten) braucht nur rund
 * 390px von 1376px verfuegbarer Breite bei 1440px. Die Kopfzeilen-Zusicherung
 * unten ist deshalb kein TDD-Beweis, sondern ein Regressionswaechter (siehe
 * Kontext-Log: "wenn eine Zusicherung nicht rot wird, ist das kein Fehler").
 *
 * Die Reiterleisten-Beschriftungen ("Dokumente", "Notizen",
 * "Lohnabrechnungen", "Stundenlohn-Verlauf") sind statische Strings ohne
 * dynamische Zaehler -- anders als bei Projekt/Anfrage/Kunde/Lieferant kann
 * hier KEINE Fixture einen Ueberlauf erzwingen, weil die Reiterbreite nicht
 * von den Testdaten abhaengt. Echt rot vor dem Fix ist ausschliesslich die
 * Stil-Zusicherung `getComputedStyle(reiterleiste).overflowX === 'visible'`
 * (heute 'auto') -- unabhaengig von jeder Fixture, weil sie den Stil direkt
 * prueft statt einen datenabhaengigen Ueberlauf.
 *
 * Kein Deep-Link: MitarbeiterEditor liest keinen Query-Parameter. Die Spec
 * geht auf /mitarbeiter, wartet auf die Liste und klickt die Karte des
 * Dummy-Mitarbeiters.
 *
 * /api vollstaendig gestubbt (Catch-all + gezielte Overrides, Vorbild
 * stubbeLieferantApi in e2e/bearbeiten-leiste.spec.ts), kein Backend. DSGVO:
 * ausschliesslich ein erfundener Mitarbeiter, gebaut aus den im Vorhaben
 * etablierten Fantasie-Wortstaemmen "Beispiel"/"Muster" -- kein echter
 * Personenbezug, keine echte Adresse, kein echter Lohn (stundenlohn: null).
 *
 * Nachtrag Abschnitt 6 (Task 7b): Design-Review und Code-Review von Abschnitt 5
 * haben unabhaengig voneinander dieselbe fuenfte Fundstelle gemeldet -- die
 * Kontakt-Spalte (SideInfo, Z. 442-535) traegt dieselbe Luecke, die dieses
 * Vorhaben bei Projekt/Anfrage/Kunde/Lieferant schon geschlossen hat: jede
 * Zeile ist "flex items-center gap-3" ohne min-w-0 am umschliessenden <div>
 * und ohne break-words am Wert. Unentdeckt, weil email/strasse/plz/ort bisher
 * null waren -- die Zeilen wurden nie mit echtem Inhalt gerendert. Fix hier:
 * Fixture um eine lange, bindestrichlose E-Mail-Adresse ergaenzt (Bindestriche
 * sind selbst Umbruchpunkte und wuerden den Fehler verdecken, siehe
 * .claude/skills/loese-problem/references/kriterien.md). Die Abteilung war
 * schon vorher lang und bindestrichlos genug (44 Zeichen, ein Wort) -- genau
 * die Zeichenkette, an der der Design-Reviewer 21px Ueberstand ueber die
 * Kartenkante gemessen hat.
 */

const MITARBEITER_ID = 42;
const VORNAME = 'Bernhardine';
const NACHNAME = 'Beispielmusterfrauenbergwaldschmidtstein';
const ABTEILUNG = 'Sonderaufgabenkoordinationsstellenverwaltung';
// Bindestrichlose Fantasie-E-Mail (Task 7b): ein Bindestrich waere selbst ein
// Umbruchpunkt und wuerde den Fehler verdecken (kriterien.md, "Testdaten fuer
// Umbruch-Fehler brauchen ein langes Wort ohne Trennstellen"). 102 Zeichen,
// .example-Domain, keine echte Adresse (DSGVO).
const EMAIL_LANG = 'personalaktenverwaltungspostfachfuermitarbeiterkommunikationsservice@musterstadtnordwestgebiet.example';

// Task 11 (Abschnitt 7): Mitarbeiter-Fixture vervollstaendigen. strasse, plz,
// ort, qualifikation und stundenlohn blieben bisher null -- Adresse und
// Qualifikation lagen vor Task 7b ebenfalls ueber der Kante (80px bzw. 132px
// bei 1440, Design-Review Abschnitt 6), ungetestet, weil nie mit echtem
// Inhalt gerendert. Strasse und Ort sind bindestrichlose Fantasie-Komposita
// (kein echter Personenbezug, DSGVO).
const STRASSE_LANG = 'Kreisverkehrsplatzrandbebauungsstraße 128a';
const PLZ = '99999';
const ORT_LANG = 'Musterstadtnordwestgebietsiedlung';
const QUALIFIKATION_LANG = 'Sondermaschinenbautechnikmeisterqualifikation';
const STUNDENLOHN = 45.5;

// Task 11 (Abschnitt 7), Gruppe 4 (Code-Reviewer, Abschnitt 6, Fundstelle 4 --
// "der realistischste Fall von allen"): {doc.originalDateiname} in der
// Dokumentenliste und als Rueckfalltext in den Lohnabrechnungen. Unterstriche
// sind nach UAX #14 KEINE Umbruchstelle -- ein Dateiname ist ein einziges
// unteilbares Wort, kein Fantasiename mit Leerzeichen.
// Bewusst deutlich laenger als das Kurzbeispiel aus dem Code-Review-Befund
// (nur ~66 Zeichen) -- die Dokumentenliste steht in der BREITEN Hauptspalte
// (rund 900px bei 1440, DetailLayout minmax(0,3fr)), nicht in der schmalen
// Seitenspalte wie Gruppe 2/3. Rot verifiziert: ein 66-Zeichen-Dateiname
// passt dort noch hinein, erst ab deutlich ueber 100 Zeichen ueberlaeuft main.
const DOKUMENT_DATEINAME_LANG = 'Arbeitsvertragsdokumentationsverwaltungsablagesystembeispielmusterfrauenbergwaldschmidtsteinundpartnergesellschaftmbhundcokgverwaltungsstelle_2024.pdf';
const LOHNABRECHNUNG_DATEINAME_LANG = 'Lohnabrechnungsimportverwaltungsdokumentationsablagebeispielmusterfrauenbergwaldschmidtsteinundpartnergesellschaftmbhundcokgverwaltungsstelle_2026.pdf';

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const DUMMY_MITARBEITER = {
    id: MITARBEITER_ID,
    vorname: VORNAME,
    nachname: NACHNAME,
    strasse: STRASSE_LANG,
    plz: PLZ,
    ort: ORT_LANG,
    email: EMAIL_LANG,
    telefon: null,
    festnetz: null,
    qualifikation: QUALIFIKATION_LANG,
    stundenlohn: STUNDENLOHN,
    geburtstag: null,
    eintrittsdatum: null,
    aktiv: true,
    abteilungIds: [1],
    abteilungNames: ABTEILUNG,
    loginToken: null, // null -> Knopf "Token erstellen" wird gerendert (dritter Kopf-Knopf)
    jahresUrlaub: 30,
};

const DUMMY_DOKUMENT = {
    id: 9001,
    originalDateiname: DOKUMENT_DATEINAME_LANG,
    dateityp: 'application/pdf',
    dateigroesse: 245760,
    uploadDatum: '2026-01-15T09:00:00Z',
    dokumentGruppe: 'VERTRAG',
};

const DUMMY_LOHNABRECHNUNG = {
    id: 9101,
    mitarbeiterId: MITARBEITER_ID,
    mitarbeiterName: `${NACHNAME}, ${VORNAME}`,
    steuerberaterId: null,
    steuerberaterName: null,
    jahr: 2026,
    monat: 1,
    originalDateiname: LOHNABRECHNUNG_DATEINAME_LANG,
    downloadUrl: '#',
    // bruttolohn/nettolohn bewusst null: nur dann rendert die Zeile den
    // Dateinamen als Rueckfalltext (MitarbeiterEditor.tsx Z. 704) statt
    // "Brutto: ... / Netto: ...".
    bruttolohn: null,
    nettolohn: null,
    importDatum: '2026-01-05T00:00:00Z',
    status: 'IMPORTIERT',
};

/** Stubbt alle /api-Routen der Mitarbeiter-Uebersicht + Detailseite. */
async function stubMitarbeiterApi(page: Page) {
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;

        if (pfad === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'anna.buero', displayName: 'Anna Büro',
                active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
            });
        }
        if (pfad === '/api/notifications/summary') {
            return json(route, { totalCount: 0, categories: [], recentItems: [] });
        }
        if (pfad === '/api/mitarbeiter') return json(route, [DUMMY_MITARBEITER]);
        if (pfad === '/api/abteilungen') return json(route, [{ id: 1, name: ABTEILUNG }]);
        if (pfad === `/api/mitarbeiter/${MITARBEITER_ID}/dokumente`) return json(route, [DUMMY_DOKUMENT]);
        if (pfad === `/api/mitarbeiter/${MITARBEITER_ID}/notizen`) return json(route, []);
        if (pfad === `/api/lohnabrechnungen/mitarbeiter/${MITARBEITER_ID}`) return json(route, [DUMMY_LOHNABRECHNUNG]);

        // Standardantwort fuer alles Weitere (z.B. Stundenlohn-Historie):
        // leere Liste statt 404, damit kein Fehlerzustand die Seite fuellt.
        return json(route, []);
    });
}

test.describe('Mitarbeiter-Detailseite: Reiterleiste (Task 7) + Kopfzeile (Regressionswaechter)', () => {
    test('Reiterleiste ohne verstecktes Scrollen, Kopf-Knoepfe vollstaendig rechts in der Karte', async ({ page }, testInfo) => {
        // Muss vor der ersten Navigation stehen (siehe playwright-design-pruefung
        // SKILL.md): index.html laedt pdf.js von cdnjs bei jeder Navigation.
        await blockiereFremdeNetzwerkzugriffe(page);
        await stubMitarbeiterApi(page);
        await page.goto('/mitarbeiter');

        await expect(page.getByRole('heading', { name: 'MITARBEITER' })).toBeVisible();

        // Task 12 (Abschnitt 8): die Mitarbeiter-Uebersicht hatte bisher gar
        // keine Zusicherung -- die Spec sprang bisher sofort in die
        // Detailansicht. Genau hier steht die "bekannte Stelle" aus dem
        // Code-Review von Abschnitt 7 (MitarbeiterEditor.tsx, Uebersichtskarte):
        // <h3> ohne break-words, darunter eine "flex items-center gap-1"-Zeile
        // mit dem Abteilungsnamen als anonymem Flex-Item ohne min-w-0 und
        // einem Icon ohne shrink-0 -- Zeichen fuer Zeichen das Muster, das
        // Task 11 in der Kundenkarte repariert hat.
        //
        // Mutationsprobe (Kontext-Log dieses Tasks): mit NACHNAME/ABTEILUNG
        // bleiben beide Zusicherungen unten auch OHNE den jeweiligen Fix gruen
        // -- bei text-sm/text-lg-Schriftgroesse und der 3-spaltigen Karte
        // (~371px Innenbreite bei 1440) passen 41 bzw. 44 bindestrichlose
        // Zeichen gerade noch hinein (306px bzw. 372px gemessen). Deckt sich
        // mit dem Code-Review-Befund "passt bei 1440 heute noch knapp ...
        // latent, nicht akut". Beide bleiben deshalb Regressionswaechter statt
        // TDD-Beweis (dieselbe Einordnung wie die Kopfzeilen-Zusicherung
        // unten) -- NACHNAME/ABTEILUNG absichtlich nicht weiter verlaengert,
        // weil beide Konstanten in vielen anderen Zusicherungen dieser Datei
        // wiederverwendet werden.
        //
        // Zwei bewusste Zeilen statt "Nachname, Vorname" in einem umbrechenden
        // Text (Nacharbeit Abschnitt 9, Design-Review Abschnitt 8, Hinweis 2).
        // Abschnitt 10 (Design-Review Abschnitt 9, Hinweis 2): das Komma wurde
        // dort zunaechst nur mit auf die erste Zeile genommen ("NACHNAME,") --
        // bei einem die Zeile exakt ausfuellenden Nachnamen rutschte das Komma
        // dadurch trotzdem allein in eine dritte, winzige Zeile (5px breiter
        // Kasten). Komma jetzt ganz gestrichen: NACHNAME auf der ersten
        // <span class="block">, VORNAME auf der zweiten, ohne Trennzeichen.
        // getByText(exact) auf den KOMBINIERTEN String "NACHNAME, VORNAME"
        // faende seit der Aufteilung nichts mehr (kein Element traegt den Text
        // mehr als Ganzes) -- genau das beweist, dass es jetzt zwei eigene
        // Textknoten sind, keine umbrechende Einheit. Jede Zeile ist ein
        // normaler Block (kein Flex-Item) -- ohne break-words liefe sie
        // unsichtbar ueber (scrollWidth > clientWidth), OHNE dass sich ihre
        // eigene boundingBox() aendert.
        const nachnameZeile = page.getByText(NACHNAME, { exact: true });
        const vornameZeile = page.getByText(VORNAME, { exact: true });
        await expect(nachnameZeile).toBeVisible();
        await expect(vornameZeile).toBeVisible();
        const nachnameUeberstand = await nachnameZeile.evaluate((el) => el.scrollWidth - el.clientWidth);
        expect(
            nachnameUeberstand,
            `Nachname-Zeile "${NACHNAME}" laeuft ${nachnameUeberstand}px ueber ihren eigenen Kasten -- braucht break-words an der <h3>`,
        ).toBeLessThanOrEqual(2);
        // Kein Komma mehr im Nachname-Span (Regressionswaechter fuer den
        // Design-Review-Befund: das Komma darf nicht wieder auftauchen und
        // allein in eine eigene Zeile rutschen).
        const nachnameText = (await nachnameZeile.textContent())?.trim();
        expect(nachnameText, 'Nachname-Zeile darf kein Komma mehr enthalten').toBe(NACHNAME);
        const nachnameBox = await nachnameZeile.boundingBox();
        const vornameBox = await vornameZeile.boundingBox();
        expect(nachnameBox, 'Nachname-Zeile muss einen messbaren Rahmen haben').not.toBeNull();
        expect(vornameBox, 'Vorname-Zeile muss einen messbaren Rahmen haben').not.toBeNull();
        expect(
            vornameBox!.y,
            `Vorname "${VORNAME}" (y=${vornameBox!.y.toFixed(0)}) steht nicht unterhalb von "${NACHNAME}" (y=${nachnameBox!.y.toFixed(0)}) -- soll zwei bewusste Zeilen sein, kein umbrechender Komma-Text`,
        ).toBeGreaterThan(nachnameBox!.y);

        // Abteilungs-Zeile: getByText traefe hier (wie bei der Kundenkarte,
        // siehe kunde-layout.spec.ts) das innerste Element -- bei einem
        // "<span min-w-0 break-words>" um den Wert waere das der Span, der
        // sich nie selbst ueberragt. Deshalb ausdruecklich auf die Flex-Zeile
        // (<p>) hochlaufen und DEREN scrollWidth/clientWidth pruefen.
        const abteilungsWert = page.getByText(ABTEILUNG, { exact: true });
        await expect(abteilungsWert).toBeVisible();
        const abteilungsZeile = abteilungsWert.locator(
            'xpath=ancestor-or-self::p[contains(concat(" ", normalize-space(@class), " "), " flex ")][1]',
        );
        const abteilungsUeberstand = await abteilungsZeile.evaluate((el) => el.scrollWidth - el.clientWidth);
        expect(
            abteilungsUeberstand,
            `Abteilungs-Zeile "${ABTEILUNG}" laeuft ${abteilungsUeberstand}px ueber ihren eigenen Kasten -- braucht min-w-0 am <span> und shrink-0 am Icon`,
        ).toBeLessThanOrEqual(2);

        // Netz-Effekt und die volle Design-Pruefung (inkl. strengePruefungen)
        // fuer die Uebersicht selbst -- bisher nie gelaufen.
        await designPruefung(page, testInfo, 'mitarbeiter-uebersicht', {
            strengePruefungen: true,
            primaerAktion: page.getByRole('button', { name: 'Neu' }),
        });

        await nachnameZeile.click();

        const ueberschrift = page.getByRole('heading', { name: `${NACHNAME}, ${VORNAME}` });
        await expect(ueberschrift).toBeVisible();

        // Task 11 (Abschnitt 7): "Dokumente" ist der default-aktive Reiter und
        // wird beim Auswaehlen des Mitarbeiters sofort nachgeladen (loadDokumente
        // in MitarbeiterEditor.tsx) -- vor der main-Ueberstand-Messung unten
        // abwarten, sonst misst die erste Zusicherung moeglicherweise den Stand
        // vor dem Nachladen (Race).
        await expect(page.getByText(DOKUMENT_DATEINAME_LANG, { exact: true })).toBeVisible();

        // Task 11 (Abschnitt 7), Gruppe 4 (Code-Reviewer, Abschnitt 6,
        // Fundstelle 4 -- "der realistischste Fall von allen"): die
        // Dokumentenliste steht in der BREITEN Hauptspalte (minmax(0,3fr),
        // rund 916-966px bei 1440), nicht in der schmalen Seitenspalte wie
        // Gruppe 2/3. Nachgemessen (Kontext-Log dieses Tasks): main selbst
        // laeuft dabei NICHT ueber (main.scrollWidth == main.clientWidth ==
        // 1440, weil die 1265px breite Zeile immer noch unter der
        // Gesamtbreite des Fensters bleibt) -- der Fehler zeigt sich zuerst
        // als Ueberstand ueber die eigene Karte (Zeile 964px breit, Text
        // 1265px), nicht als Dokument-Ueberlauf. Deshalb direkt gegen die
        // Karte pruefen, nicht nur gegen main.
        // Nacharbeit Abschnitt 9 (Code-Review Abschnitt 8, "Kasten-Zusicherungen
        // doppelt messen"): der gemessene Wert ist ein Block-<p> INNERHALB des
        // Flex-Items "<div class='min-w-0 flex-1'>" -- seine eigene boundingBox()
        // kann die Karte nie ueberragen, sie bleibt bei "Breite = Elternbreite".
        // min-w-0 entfernen macht die boundingBox-Messung unten rot (das Flex-Item
        // waechst und der Block waechst mit), break-words entfernen NICHT -- das
        // faengt erst die zusaetzliche scrollWidth/clientWidth-Messung ab.
        const pruefeDateinameBleibtInKarte = async (dateiname: string) => {
            const wertElement = page.getByText(dateiname, { exact: true });
            await expect(wertElement, `Dateiname "${dateiname}" fehlt`).toBeVisible();
            const karte = wertElement.locator(
                'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " shadow-sm ")][1]',
            );
            const wertBox = await wertElement.boundingBox();
            const karteBox = await karte.boundingBox();
            expect(wertBox, 'Dateiname-Wert muss einen messbaren Rahmen haben').not.toBeNull();
            expect(karteBox, 'Karte muss einen messbaren Rahmen haben').not.toBeNull();
            const ueberstand = (wertBox!.x + wertBox!.width) - (karteBox!.x + karteBox!.width);
            expect(
                ueberstand,
                `Dateiname "${dateiname.slice(0, 40)}..." ragt ${ueberstand.toFixed(0)}px rechts aus der Karte -- braucht min-w-0 (flex-1) auf jeder Ebene der Zeile und break-words am Wert`,
            ).toBeLessThanOrEqual(2);
            const eigenerUeberstand = await wertElement.evaluate((el) => el.scrollWidth - el.clientWidth);
            expect(
                eigenerUeberstand,
                `Dateiname "${dateiname.slice(0, 40)}..." laeuft ${eigenerUeberstand}px ueber seinen eigenen Kasten -- braucht break-words am Wert-<p>`,
            ).toBeLessThanOrEqual(2);
        };
        await pruefeDateinameBleibtInKarte(DOKUMENT_DATEINAME_LANG);

        // Task 7b: Kontakt-Spalte (SideInfo). Zielwert Nr. 1 des gesamten
        // Vorhabens -- main darf mit der langen E-Mail/Abteilung nicht
        // ueberlaufen (Design-Review Abschnitt 5: 184px bei 1440, 192px bei
        // 1920, vor dem Fix).
        const mainUeberstandBeimOeffnen = await page.evaluate(() => {
            const main = document.querySelector('main');
            return main ? main.scrollWidth - main.clientWidth : 0;
        });
        expect(
            mainUeberstandBeimOeffnen,
            `main laeuft beim Oeffnen der Detailseite ueber (lange E-Mail/Abteilung in der Kontakt-Spalte): ${mainUeberstandBeimOeffnen}px`,
        ).toBe(0);

        // Wert innerhalb seines Kastens: die Kontakt-Spalte hat keine eigenen
        // "bg-slate-50"-Kaesten je Zeile (anders als Lieferant/Kunde) -- der
        // Kasten ist die Karte, die SideInfo umschliesst (DetailLayout.tsx,
        // <Card className="p-6 h-full">). "shadow-sm" traegt laut card.tsx
        // ausschliesslich die Karte selbst. Auf diese Karte eingegrenzt, weil
        // die Kopfzeile (Z. 335) dieselbe Abteilungs-Zeichenkette im Untertitel
        // wiederholt -- ein ungegrenztes getByText(exact) waere sonst mehrdeutig.
        const seitenKarte = page.getByRole('heading', { name: 'Persönliche Daten' }).locator(
            'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " shadow-sm ")][1]',
        );
        // exact=false fuer die Adresse (Task 11): {strasse}<br/>{plz} {ort}
        // rendert als EIN <p>, dessen textContent Strasse und PLZ ohne
        // Trennzeichen aneinanderhaengt (<br/> traegt keinen Text bei) -- ein
        // exakter Treffer auf nur die Strasse waere nie moeglich. Playwright
        // matcht bei exact:false das am engsten umschliessende Element, hier
        // exakt das Werte-<p>.
        // Nacharbeit Abschnitt 9 ("Kasten-Zusicherungen doppelt messen", siehe
        // Kommentar bei pruefeDateinameBleibtInKarte oben): boundingBox() gegen
        // die Karte UND scrollWidth/clientWidth am Wert selbst -- sonst waere
        // break-words entfernen hier nicht rot zu bekommen.
        const pruefeWertImKasten = async (wert: string, exact = true) => {
            const wertElement = seitenKarte.getByText(wert, { exact });
            await expect(wertElement).toBeVisible();
            const wertBox = await wertElement.boundingBox();
            const karteBox = await seitenKarte.boundingBox();
            expect(wertBox, `Wert "${wert.slice(0, 30)}..." muss einen messbaren Rahmen haben`).not.toBeNull();
            expect(karteBox, 'Kontakt-Karte muss einen messbaren Rahmen haben').not.toBeNull();
            const ueberstand = (wertBox!.x + wertBox!.width) - (karteBox!.x + karteBox!.width);
            expect(
                ueberstand,
                `Wert "${wert.slice(0, 30)}..." ragt ${ueberstand.toFixed(0)}px rechts aus der Kontakt-Karte -- braucht min-w-0 flex-1 am umschliessenden div und break-words am Wert`,
            ).toBeLessThanOrEqual(2);
            const eigenerUeberstand = await wertElement.evaluate((el) => el.scrollWidth - el.clientWidth);
            expect(
                eigenerUeberstand,
                `Wert "${wert.slice(0, 30)}..." laeuft ${eigenerUeberstand}px ueber seinen eigenen Kasten -- braucht break-words am Wert`,
            ).toBeLessThanOrEqual(2);
        };
        await pruefeWertImKasten(EMAIL_LANG);
        await pruefeWertImKasten(ABTEILUNG);
        // Task 11 (Abschnitt 7): Abdeckungsluecke aus dem Design-Review
        // Abschnitt 6 -- Adresse und Qualifikation lagen vor Task 7b ebenfalls
        // ueber der Kante (80px bzw. 132px bei 1440), wurden aber nie mit
        // echtem Inhalt getestet (strasse/plz/ort/qualifikation waren null).
        // Die Zeilen selbst sind seit Task 7b bereits gefixt (min-w-0 flex-1 +
        // break-words auf der ganzen SideInfo) -- diese Zusicherungen schliessen
        // nur die Testluecke.
        await pruefeWertImKasten(STRASSE_LANG, false);
        await pruefeWertImKasten(QUALIFIKATION_LANG);

        // Kopfzeile: die drei Knoepfe muessen vollstaendig sichtbar UND
        // rechts von der Bildschirmmitte liegen -- nicht nur "irgendwo in
        // main". "Token erstellen" erscheint, weil loginToken null ist.
        // Siehe Kommentar oben: auf dem heutigen Stand nicht rot (die Seite
        // hat keine Kennzahlen-Reihe, die dem Titelblock Platz wegnimmt) --
        // bleibt trotzdem als Regressionswaechter stehen, falls spaeter eine
        // Kennzahlen-Reihe oder ein weiterer Knopf dazukommt.
        const tokenErstellen = page.getByRole('button', { name: 'Token erstellen' });
        const zurueck = page.getByRole('button', { name: 'Zurück' });
        const bearbeiten = page.getByRole('button', { name: 'Bearbeiten' });
        for (const knopf of [tokenErstellen, zurueck, bearbeiten]) {
            await expect(knopf).toBeVisible();
            await expect(knopf).toBeInViewport();
        }
        const viewportBreite = page.viewportSize()!.width;
        for (const [name, knopf] of [
            ['Token erstellen', tokenErstellen],
            ['Zurück', zurueck],
            ['Bearbeiten', bearbeiten],
        ] as const) {
            const box = await knopf.boundingBox();
            expect(box, `Knopf "${name}" hat keinen messbaren Rahmen`).not.toBeNull();
            expect(
                box!.x,
                `Knopf "${name}" liegt bei x=${box!.x.toFixed(0)}, erwartet rechts von der Bildschirmmitte (${(viewportBreite / 2).toFixed(0)}) -- Rezeptur-Warnung: "Knopfblock faellt ohne ml-auto beim Umbruch nach links unten"`,
            ).toBeGreaterThan(viewportBreite / 2);
        }

        // Reiterleiste: vier Reiter (Dokumente, Notizen, Lohnabrechnungen,
        // Stundenlohn-Verlauf). data-testid grenzt praezise ein (ein
        // Selektor ueber Klassen traf in der Kunde-Spec auch fremde
        // "border-b"-Knoepfe anderswo auf der Seite).
        const reiterleiste = page.getByTestId('mitarbeiter-reiterleiste');
        const reiterButtons = reiterleiste.locator('button');
        await expect(reiterButtons).toHaveCount(4);

        const reiterY = await reiterButtons.evaluateAll((buttons) => buttons.map((b) => b.getBoundingClientRect().y));
        const reiterSpanne = Math.max(...reiterY) - Math.min(...reiterY);
        expect(reiterSpanne, `Reiter liegen auf unterschiedlichen Zeilen: ${JSON.stringify(reiterY)}`).toBeLessThanOrEqual(2);

        // Kern der Rezeptur (Befund des Code-Reviewers aus Abschnitt 3,
        // Hinweis 2): kein verstecktes Scrollen mehr moeglich. Vor Task 11 war
        // das die einzige Zusicherung dieser Spec, die unabhaengig von jeder
        // Fixture rot war (sie prueft den Stil direkt statt einen
        // datenabhaengigen Ueberlauf in Pixeln) -- seit Task 11 kommen die
        // Kasten-Ueberstand-Zusicherungen oben/unten dazu, die aber von den
        // gehaerteten Fixtures abhaengen.
        const overflowX = await reiterleiste.evaluate((el) => getComputedStyle(el).overflowX);
        expect(overflowX, 'Reiterleiste darf nicht mehr versteckt scrollen (overflow-x: auto)').toBe('visible');

        await designPruefung(page, testInfo, 'mitarbeiter-detail-reiterleiste', {
            strengePruefungen: true,
            primaerAktion: bearbeiten,
        });

        // Jeden der vier Reiter anklicken und main auf Ueberstand pruefen --
        // dieselbe Abnahme wie bei Projekt/Anfrage/Kunde/Lieferant. Fuer
        // "Lohnabrechnungen" (Gruppe 4, Task 11) erst auf den Dateinamen warten
        // -- die Lohnabrechnungen werden erst beim Aktivieren des Reiters
        // nachgeladen (loadLohnabrechnungen), ohne Warten wuerde main
        // moeglicherweise vor dem Nachladen gemessen (Race). "Dokumente" ist
        // schon beim Oeffnen geladen (siehe Wartepunkt oben), ein erneutes
        // Warten dort schadet aber nicht.
        for (const name of ['Notizen', 'Lohnabrechnungen', 'Stundenlohn-Verlauf', 'Dokumente']) {
            await reiterleiste.getByRole('button', { name: new RegExp(`^${name}`) }).click();
            if (name === 'Lohnabrechnungen') {
                await expect(page.getByText(LOHNABRECHNUNG_DATEINAME_LANG, { exact: true })).toBeVisible();
                // Task 11 (Abschnitt 7), Gruppe 4: {la.originalDateiname} als
                // Rueckfalltext (MitarbeiterEditor.tsx Z. 704, bruttolohn/
                // nettolohn beide null) -- dieselbe Karten-Ueberstand-Pruefung
                // wie bei den Dokumenten oben.
                await pruefeDateinameBleibtInKarte(LOHNABRECHNUNG_DATEINAME_LANG);
                // Abdeckungsluecke (Task 12, Code-Review Abschnitt 7): break-words
                // am Lohnabrechnungs-<p> war bisher von KEINER Zusicherung
                // gedeckt, weil designPruefung(strengePruefungen) oben (Z. 337)
                // laeuft, WAEHREND noch der Dokumente-Reiter aktiv ist -- lange vor
                // diesem Reiterwechsel. keinTextLaeuftUeber() direkt hier schliesst
                // die Luecke, ohne einen weiteren Screenshot zu brauchen.
                await keinTextLaeuftUeber(page);
            } else if (name === 'Dokumente') {
                await expect(page.getByText(DOKUMENT_DATEINAME_LANG, { exact: true })).toBeVisible();
            }
            const mainUeberstand = await page.evaluate(() => {
                const main = document.querySelector('main');
                return main ? main.scrollWidth - main.clientWidth : 0;
            });
            expect(mainUeberstand, `main laeuft nach Klick auf "${name}" ueber`).toBe(0);
        }
    });
});
