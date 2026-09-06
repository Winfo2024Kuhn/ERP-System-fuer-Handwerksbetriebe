import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung } from './hilfen/design';

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
    kundenEmails: [],
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
        // clientWidth -- Auftrag Task 3, Punkt 3) und kein Reiter verschwindet
        // (jeder der sieben Knoepfe bleibt sichtbar und liegt vollstaendig
        // innerhalb des Containers). Bei 1440 reicht der Platz nach dem
        // Umbenennen (Geschaeftsdokumente/Material/Tagebuch) und px-3 fast
        // fuer eine Zeile -- gemessen fehlen nur noch rund 35-40px fuer den
        // siebten Reiter ("Tagebuch"), heute waren es 283px. Genau fuer diesen
        // Rest-Fall erlaubt der Plan (Abschnitt B / Kontext-Log-Auftrag an
        // Task 3) ausdruecklich flex-wrap statt striktem Einzeiler: "nichts
        // darf verschwinden" ist die Abnahme, nicht zwingend eine Zeile. Bei
        // 1920 reicht der Platz bereits fuer eine Zeile (gemessen).
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
        // Zur Dokumentation der gemessenen Zielwerte (Ziel 3 im Auftrag):
        // benoetigte Breite der ersten Zeile gegen die verfuegbare Breite.
        const ersteZeileY = Math.min(...tabBoxen.map((b) => b!.y));
        const ersteZeile = tabBoxen.filter((b) => Math.abs(b!.y - ersteZeileY) < 2);
        const benoetigteBreiteErsteZeile = Math.max(...ersteZeile.map((b) => b!.x + b!.width)) - Math.min(...ersteZeile.map((b) => b!.x));
        console.log(
            `[Reiterleiste ${testInfo.project.name}] erste Zeile: ${ersteZeile.length}/7 Reiter, benoetigt ${benoetigteBreiteErsteZeile.toFixed(0)}px von ${containerBox.width.toFixed(0)}px verfuegbar; insgesamt ${tabBoxen.length} Reiter auf ${new Set(tabBoxen.map((b) => Math.round(b!.y))).size} Zeile(n).`,
        );

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
