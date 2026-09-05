import { test, expect, type Page, type Route } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';
import { keineUeberschneidungen, uebergaengeAusklingenLassen } from './hilfen/design';

/**
 * Task 2 (Abschnitt 2) aus docs/superpowers/plans/2026-09-05-layout-14-zoll.md,
 * Spec A / Befund 1: Der Projekt-Editor ist der schlimmste Fall des
 * Zwei-Spalten-Rasters (DetailLayout.tsx) -- sieben Reiter ohne Umbruch treiben
 * die Mindestbreite der linken Spalte auf 1247px, das Raster kann bei 1440px
 * nicht mehr schrumpfen (min-width: auto auf Rasterzellen -- Rasterzellen
 * verhalten sich darin wie Flex-Items), und MainLayout.tsx versteckt den
 * Ueberstand still per overflow-x-hidden statt ihn zu zeigen.
 *
 * Rote Spec zuerst (TDD, Skill superpowers:test-driven-development): auf dem
 * heutigen Stand (vor dem Fix) zwingt die Reiterleiste das Raster in
 * DetailLayout.tsx zu einem internen Ueberstand von rund 227px bei 1440px
 * breit, und die Karte "Projektdaten" wird dadurch so weit nach rechts
 * geschoben, dass sie rund 163-166px aus dem Fenster ragt (in derselben
 * Groessenordnung wie Spec-Befund 1: 185px bzw. 170px -- die genaue Zahl
 * haengt leicht von den Dummy-Feldlaengen ab, das Prinzip ist identisch).
 * Verifiziert per manueller Vorher/Nachher-Messung (Fix als Patch gesichert,
 * `git checkout --` auf den Alt-Stand, Spec rot gefahren, Patch zurueck via
 * `git apply`, Spec gruen gefahren -- siehe Kontext-Log-Block zu diesem Task;
 * bewusst ohne `git stash`, weil der Stash-Bereich sitzungsuebergreifend
 * geteilt ist).
 *
 * WICHTIGER BEFUND ausserhalb dieses Tasks (siehe Kontext-Log): Die Kopfzeile
 * in ProjektEditor.tsx (Zeile ~1045 ff., "Files" dieses Tasks umfassen diese
 * Datei NICHT) hat unabhaengig von jedem Bauvorhaben-Namen -- selbst mit
 * einem einzigen kurzen Wort wie "Carport" -- bei 1440px zu wenig Platz fuer
 * Kennzahlen-Reihe + Knopfblock ("Bearbeiten" / "mit Anfrage zusammenfuehren");
 * der Knopfblock wird dadurch ca. 40-60px nach rechts aus der Kopf-Karte
 * geschoben (Spec-Befund 2). Das ist ein vorbestehender, von diesem Task
 * unabhaengiger Fehler (verifiziert per Vorher/Nachher-Messung: der Effekt
 * ist exakt gleich gross vor und nach dem DetailLayout/MainLayout-Fix) und
 * gehoert zu
 * Task 3 (Kopfzeile umbauen). Er sorgt dafuer, dass <main> auf DIESER Route
 * bei pc-14zoll auch nach diesem Fix noch einen kleinen, nicht von diesem
 * Task verursachten Ueberstand zeigt -- deshalb prueft diese Spec gezielt das
 * Raster selbst (DetailLayout.tsx) statt pauschal <main>, und ruft
 * designPruefung() aus e2e/hilfen/design.ts hier bewusst NICHT als Ganzes
 * auf: deren keinHorizontalerUeberlauf() misst <main> ohne Toleranz und
 * wuerde wegen Spec-Befund 2 (Task 3) immer rot bleiben, unabhaengig von der
 * Korrektheit dieses Tasks. Die uebrigen, hier ehrlich pruefbaren Teile von
 * designPruefung (Screenshot, keineUeberschneidungen, Sichtbarkeit der
 * Primaeraktion) laufen unten trotzdem mit.
 *
 * /api vollstaendig gestubbt (Catch-all + gezielte Overrides, Vorbild:
 * stubbeLieferantApi in e2e/bearbeiten-leiste.spec.ts), kein Backend, nur die
 * Fantasienamen aus der Spec (DSGVO): Bauvorhaben "Treppenanlage mit Podest
 * und Absturzsicherung Bürogebäude Beispielstraße", Kunde
 * "Wohnungsbaugesellschaft Beispielstadt Nord mbH und Co. Verwaltungs KG".
 */

const PROJEKT_ID = 5;
const BAUVORHABEN = 'Treppenanlage mit Podest und Absturzsicherung Bürogebäude Beispielstraße';
const KUNDE = 'Wohnungsbaugesellschaft Beispielstadt Nord mbH und Co. Verwaltungs KG';

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const DUMMY_AUSGANGSDOKUMENT = {
    id: 501,
    dokumentNummer: 'RE-2026-0501',
    typ: 'RECHNUNG' as const,
    datum: '2026-02-01',
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
    dateiname: 'lieferantenrechnung-dummy.pdf',
    dokumentDatum: '2026-02-05',
    gesamtbetrag: 1500,
    prozent: 100,
    berechneterBetrag: 1500,
    beschreibung: 'Material für Treppenanlage',
    lieferantId: 21,
    lieferantName: 'Stahlhandel Beispiel GmbH und Co. KG',
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
    kundenEmails: [],
    materialkosten: [],
    artikel: [],
    produktkategorien: [],
    zeiten: [],
    emails: [],
};

/**
 * Stubbt alle /api-Routen, die das Oeffnen der Projekt-Detailseite (Deep-Link
 * ueber ?projektId=&tab=) anfasst -- Catch-all zuerst (robustestes Vorbild
 * laut Plan-Block Task 2), gezielte Overrides fuer die im Plan gelisteten
 * Routen danach.
 */
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
        // einem Fehlerzustand fuellen (Catch-all-Empfehlung aus dem Plan).
        return json(route, []);
    });
}

test.describe('Rahmen und Zwei-Spalten-Raster: Projekt-Detailseite im schlimmsten Fall', () => {
    test('Raster erzeugt keinen Ueberstand mehr, Karte "Projektdaten" ragt nicht mehr aus dem Fenster', async ({ page }, testInfo) => {
        await stubProjektApi(page);
        await page.goto(`/projekte?projektId=${PROJEKT_ID}&tab=geschaeftsdokumente`);

        const projektdatenUeberschrift = page.getByRole('heading', { name: 'Projektdaten' });
        await expect(projektdatenUeberschrift).toBeVisible();

        // Die Karte ist der direkte Eltern-Container der Ueberschrift
        // (DetailLayout.tsx: <Card><h2>Projektdaten</h2>...</Card>), das
        // Raster der naechste Vorfahre mit der Klasse "grid" (DetailLayout.tsx
        // Zeile ~17: <div className="grid grid-cols-1 xl:grid-cols-[...] ...">).
        const projektdatenKarte = projektdatenUeberschrift.locator('xpath=..');
        const raster = projektdatenKarte.locator(
            'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " grid ")][1]',
        );

        // Spec-Befund 1, Kern: das Raster selbst darf keinen internen
        // Ueberstand mehr erzeugen. Vor dem Fix (min-width: auto auf den
        // Rasterzellen) misst das rund 227px bei 1440px -- die linke Spalte
        // kann nicht unter ihre Mindestinhaltsbreite (Reiterleiste, 1247px)
        // schrumpfen. Bewusst am Raster gemessen und nicht an <main>: <main>
        // traegt zusaetzlich einen vorbestehenden, von diesem Task
        // unabhaengigen Ueberstand aus der noch ungefixten Kopfzeile
        // (ProjektEditor.tsx, Task 3 -- siehe Kommentar am Dateianfang).
        // +4px Toleranz fuer Sub-Pixel-Rundung bei der fr-Aufteilung (gemessen:
        // bis zu 3px bei pc-monitor, unabhaengig vom Fix -- ein echter
        // Regressions-Befund liegt bei >200px, siehe Spec-Befund 1).
        const rasterUeberstand = await raster.evaluate((el) => el.scrollWidth - el.clientWidth);
        expect(
            rasterUeberstand,
            `Raster hat ${rasterUeberstand}px internen Ueberstand (Spec-Befund 1: Reiterleiste zwingt die linke Spalte auf eine Mindestbreite, die das 3fr/1fr-Raster ohne minmax(0, ...) nicht mehr unterschreiten kann)`,
        ).toBeLessThanOrEqual(4);

        // Spec-Befund 1: "die rechte Spalte ragt X px hinaus" -- die Karte
        // "Projektdaten" muss vollstaendig innerhalb der Fensterbreite liegen,
        // nicht rechts (oder links) herausragen. Manuell statt
        // toBeInViewport({ratio:1}) gemessen: Letzteres prueft auch die
        // Hoehe, und die Kopf-Karte ist bei einem langen Bauvorhaben schon
        // heute mehrzeilig (Spec-Befund 2, Task 3) -- das wuerde die y-Achse
        // dieser Pruefung unabhaengig von diesem Task verfaelschen. Die
        // x-Achse ist exakt das, was DetailLayout.tsx/MainLayout.tsx hier
        // reparieren.
        const karteBox = await projektdatenKarte.boundingBox();
        expect(karteBox, 'Karte "Projektdaten" muss einen messbaren Rahmen haben').not.toBeNull();
        const viewportBreite = page.viewportSize()!.width;
        expect(
            karteBox!.x,
            `Karte "Projektdaten" beginnt bei x=${karteBox!.x.toFixed(0)} -- darf nicht links aus dem Fenster ragen`,
        ).toBeGreaterThanOrEqual(0);
        const rechtsUeberstand = karteBox!.x + karteBox!.width - viewportBreite;
        expect(
            rechtsUeberstand,
            `Karte "Projektdaten" ragt ${rechtsUeberstand.toFixed(0)}px rechts aus dem Fenster (Spec-Befund 1 nennt 170px bei 1440px breit)`,
        ).toBeLessThanOrEqual(0);

        // Schwerpunkt aus dem Auftrag: bei pc-monitor (1920px) messbar ueber
        // getComputedStyle(grid).gridTemplateColumns pruefen, dass das Raster
        // weiterhin sauber zwei Spalten aufteilt (kein Scrollbalken, keine
        // Spalte auf einen schmalen Streifen zusammengequetscht -- genau das
        // Muster aus Spec-Befund 1).
        //
        // Messung (siehe Kontext-Log-Block zu diesem Task): schon VOR dem Fix
        // ist die rechte Spalte bei 1920px durch dieselbe Reiterleiste, die
        // bei 1440px main ueberlaufen laesst, leicht schmaeler als ihr reiner
        // 1fr-Anteil (gemessen 265px statt 384px) -- 1920px hat zwar genug
        // Platz, um main insgesamt ueberlaufsfrei zu halten (main.scrollWidth
        // === main.clientWidth, oben schon covered durch die main-unabhaengige
        // Raster-Pruefung), aber das Grid-Sizing beruecksichtigt den
        // Mindestinhalt der Reiterleiste trotzdem mit. Nach dem Fix bekommt
        // die rechte Spalte ihren vollen 1fr-Anteil (gemessen 378px, exakt
        // 3fr:1fr) -- eine Verbreiterung der rechten Spalte um rund 113px,
        // keine Verschlechterung. Ein exaktes "byte-identisches Vorher/Nachher"
        // ist bei diesem Fix technisch nicht moeglich (beide vom Plan
        // genannten Varianten -- minmax(0, Nfr) und min-w-0 -- liefern
        // nachweislich dasselbe Ergebnis); deshalb wird hier auf eine sinnvoll
        // aufgeteilte, nicht zusammengequetschte Spaltenstruktur geprueft statt
        // auf exakte Pixelgleichheit.
        if (testInfo.project.name === 'pc-monitor') {
            const gridTemplateColumns = await raster.evaluate((el) => getComputedStyle(el).gridTemplateColumns);
            // parseFloat statt Number: getComputedStyle liefert "1249.47px 265.188px" -- mit
            // "px"-Einheit -- Number() davon waere NaN.
            const spalten = gridTemplateColumns.split(' ').map((wert) => parseFloat(wert));
            expect(spalten, `unerwartetes grid-template-columns: "${gridTemplateColumns}"`).toHaveLength(2);
            expect(spalten[0], `linke Spalte unerwartet schmal: "${gridTemplateColumns}"`).toBeGreaterThan(800);
            expect(spalten[1], `rechte Spalte unerwartet schmal: "${gridTemplateColumns}"`).toBeGreaterThan(250);
            expect(
                spalten[0] / spalten[1],
                `Spaltenverhaeltnis ausserhalb des plausiblen Rahmens (2:1 bis 4:1) fuer ein 3fr/1fr-Raster: "${gridTemplateColumns}"`,
            ).toBeGreaterThan(2);
        }

        // Die Teile von designPruefung() (e2e/hilfen/design.ts), die auf
        // dieser Route ehrlich pruefbar sind: Screenshot fuer die
        // Design-Review, keine Ueberschneidung interaktiver Elemente,
        // Primaeraktion ("Bearbeiten") ohne Scrollen sichtbar.
        // designPruefung() selbst wird hier NICHT aufgerufen -- deren
        // keinHorizontalerUeberlauf() misst <main> ohne Toleranz und würde
        // wegen des vorbestehenden Kopfzeilen-Befunds (siehe oben, Task 3)
        // unabhaengig von diesem Task immer fehlschlagen.
        await uebergaengeAusklingenLassen(page);
        const zielOrdner = path.join(testInfo.project.outputDir, 'design');
        fs.mkdirSync(zielOrdner, { recursive: true });
        const bildPfad = path.join(zielOrdner, `rahmen-projekt-detail--${testInfo.project.name}.png`);
        await page.screenshot({ path: bildPfad, fullPage: false });
        await testInfo.attach('design: rahmen-projekt-detail', { path: bildPfad, contentType: 'image/png' });
        await keineUeberschneidungen(page);
        // .first(): die Eingangsrechnung traegt einen eigenen
        // "Zuordnung bearbeiten"-Knopf mit derselben zugaenglichen Bezeichnung
        // "Bearbeiten" (title wird zur Beschreibung, nicht zum Namen) --
        // strict mode von Playwright meldet sonst zwei Treffer. Die Kopfzeile
        // rendert vor dem Raster (DetailLayout.tsx: {header} kommt vor dem
        // Grid), der Kopf-Knopf ist also zuverlaessig der erste Treffer.
        await expect(
            page.getByRole('button', { name: 'Bearbeiten' }).first(),
            'Primaeraktion muss ohne Scrollen sichtbar sein',
        ).toBeInViewport();
    });
});
