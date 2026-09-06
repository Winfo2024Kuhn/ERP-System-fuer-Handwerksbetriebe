import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung, keinHorizontalerUeberlauf, keinTextGekuerzt } from './hilfen/design';
import { blockiereFremdeNetzwerkzugriffe } from './hilfen/api';

/**
 * Task 4 (Abschnitt 3) aus docs/superpowers/plans/2026-09-05-layout-14-zoll.md:
 * AnfrageEditor -- Kopf, Reiterleiste, Uebersichtskarte.
 *
 * Zwei Ablaeufe, je mit den langen Fantasienamen aus der Spec (DSGVO: keine
 * echten Personendaten):
 *   1. Detailseite `/anfragen?anfrageId=9&tab=geschaeftsdokumente` mit langem
 *      Bauvorhaben UND langem Kundennamen -- prueft Reiterleiste (einzeilig)
 *      und Kopfzeile (Bearbeiten/Loeschen bleiben in der Kopf-Karte).
 *   2. Uebersicht `/anfragen` mit vier langen Titeln -- prueft Kartenraster
 *      (drei Karten je Reihe bei 1440, vier bei 1920) und dass kein Titel
 *      einzeilig abgehackt wird.
 *
 * Rote Spec zuerst (TDD, Skill superpowers:test-driven-development): auf dem
 * heutigen Stand (vor dem Fix) sind ueberwiegend zwei Dinge kaputt --
 * (a) der Knopfblock "Bearbeiten"/"Loeschen" der Kopfzeile ragt aus der
 * Kopf-Karte, weil die Kennzahlen-Reihe `flex-1 max-w-2xl` sich den Platz
 * nimmt und weder Titelblock noch Knopfblock eine Mindest- bzw. Fixbreite
 * haben, und (b) das Kartenraster nutzt `xl:grid-cols-4` (Breakpoint 1280px),
 * zeigt bei 1440px also schon vier statt drei Karten je Reihe, und die
 * Kartentitel sind `truncate` (einzeilig abgeschnitten) statt `line-clamp-2`.
 * Beide Effekte wurden vor der Umsetzung gegen den unveraenderten Code
 * gemessen (siehe Kontext-Log-Block zu diesem Task fuer die genauen Zahlen).
 *
 * /api vollstaendig gestubbt (Catch-all + gezielte Overrides, Vorbild:
 * stubbeLieferantApi in e2e/bearbeiten-leiste.spec.ts und stubProjektApi in
 * e2e/rahmen-detailseite.spec.ts), kein Backend, nur Fantasienamen.
 */

const ANFRAGE_ID = 9;
const BAUVORHABEN = 'Treppenanlage mit Podest und Absturzsicherung Bürogebäude Beispielstraße';
const KUNDE = 'Wohnungsbaugesellschaft Beispielstadt Nord mbH und Co. Verwaltungs KG';

// Nacharbeit Abschnitt 4, Punkt 8 (Code-Review-Bedenken 2, vom Design-Reviewer
// bestaetigt): die Detail-Zusicherungen oben hielten mit dem BAUVORHABEN-Wert
// (Woerter mit Leerzeichen) rot/gruen NICHT fest, weil ein normaler Zeilenumbruch
// an Leerzeichen die Kopfzeile schon vorher rettete -- getestet war effektiv nur
// die Uebersicht. Ein Komposita-Bauvorhaben OHNE Leerzeichen ist genau der Fall,
// den min-w-[18rem] + break-words auf der <h1> loest: ohne break-words zwingt ein
// unteilbares Wort den Titelblock ueber seine Mindestbreite hinaus und drueckt
// den Knopfblock aus der Kopf-Karte bzw. erzeugt horizontalen Ueberlauf.
const BAUVORHABEN_KOMPOSITA_OHNE_LEERZEICHEN =
    'Absturzsicherungspodesttreppenanlagenmontagearbeitenüberwachungsdokumentation';

// Task 12 (Abschnitt 8, "zweiter Mechanismus", Code-Review Abschnitt 7): die
// Seitenspalte "Anfragedaten" (Ansprechpartner, Telefon, Mobiltelefon,
// Projektadresse) rendert Werte als reinen Block-<p> ohne break-words --
// DUMMY_ANFRAGE_DETAIL unten setzt keinen dieser Werte, deshalb wurden sie nie
// mit echt ueberlaufendem Inhalt gerendert. Bindestrichlose Fantasieworte
// (DSGVO), Strasse mit einem Leerzeichen (schwaechste Haertung, siehe
// kriterien.md), aber weiterhin ein 37-Zeichen-Stueck ohne Trennstelle.
const ANSPRECHPARTNER_LANG = 'Ansprechpartnerkoordinationsverwaltungsbeauftragte';
const TELEFON_LANG = '05119876543212345678901234567890';
const MOBILTELEFON_LANG = '01711234567890123456789012345678901234';
const STRASSE_LANG = 'Kreisverkehrsplatzrandbebauungsstraße 128a';

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

// Nachbesserung 1 (Design-Review, 🟡): eine lange E-Mail-Adresse, damit die
// "Kunden-E-Mails"-Zeile im Seitenbereich (AnfrageEditor.tsx, vormals
// "truncate" ohne title) tatsaechlich auf die Probe gestellt wird. Genau
// dieselbe Kuerzung wurde im Projekt-Editor in Abschnitt 4 behoben, blieb hier
// aber unentdeckt, weil kundenEmails: [] gesetzt war -- die Stelle wurde nie
// gerendert.
const KUNDEN_EMAIL_LANG = 'verwaltung.rechnungswesen@wohnungsbaugesellschaft-beispielstadt-nord-immobilienverwaltung.example';

const DUMMY_ANFRAGE_DETAIL = {
    id: ANFRAGE_ID,
    kundenId: 3,
    kundenName: KUNDE,
    bauvorhaben: BAUVORHABEN,
    kundennummer: 'K-1003',
    anfragesnummer: 'AG-2026/09/00009',
    betrag: 84500,
    // Adresse bleibt bewusst leer: sonst rendert GoogleMapsEmbed ein echtes
    // <iframe src="https://www.google.com/maps?..."> -- ein Netzwerkzugriff,
    // den dieser rein gestubbte Test nicht braucht (siehe gleicher Kommentar
    // in e2e/rahmen-detailseite.spec.ts). GoogleMapsEmbed haengt nur an
    // Strasse/PLZ/Ort, nicht an kundenEmails -- die duerfen gefuellt sein.
    kundenEmails: [KUNDEN_EMAIL_LANG] as string[],
    anlegedatum: '2026-02-01',
    abgeschlossen: false,
    emails: [] as unknown[],
    dokumente: [] as unknown[],
};

/**
 * Stubbt alle /api-Routen, die das Oeffnen der Anfrage-Detailseite (Deep-Link
 * ueber ?anfrageId=&tab=) bzw. der Anfragen-Uebersicht anfasst. Catch-all
 * zuerst, gezielte Overrides fuer die im Plan gelisteten Routen danach.
 */
async function stubAnfrageApi(page: Page, optionen: { uebersicht?: Record<string, unknown>[] } = {}) {
    const uebersicht = optionen.uebersicht ?? [];
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
        if (/^\/api\/last-accessed\/ANFRAGE(\/\d+)?$/.test(pfad)) {
            if (methode === 'POST') return route.fulfill({ status: 204, body: '' });
            return json(route, {});
        }
        if (pfad === '/api/anfragen' && methode === 'GET') {
            return json(route, { anfragen: uebersicht, gesamt: uebersicht.length });
        }
        if (pfad === '/api/anfragen/jahre') return json(route, []);
        if (pfad === '/api/anfragen/funnel-ids') return json(route, []);
        if (pfad === '/api/anfragen/freigabe-status') return json(route, {});
        if (pfad === `/api/anfragen/${ANFRAGE_ID}`) return json(route, DUMMY_ANFRAGE_DETAIL);
        if (pfad === `/api/anfragen/${ANFRAGE_ID}/notizen`) return json(route, []);
        if (pfad === `/api/anfragen/${ANFRAGE_ID}/dokumente`) return json(route, []);
        if (pfad === `/api/ausgangs-dokumente/anfrage/${ANFRAGE_ID}`) return json(route, []);
        if (pfad === `/api/emails/anfrage/${ANFRAGE_ID}`) return json(route, []);

        // Standardantwort fuer alles Weitere: leere Liste statt 404 -- fuer
        // diesen Ablauf irrelevante Endpunkte sollen die Seite nicht mit
        // einem Fehlerzustand fuellen.
        return json(route, []);
    });
}

/** Gruppiert Rechtecke nach y-Position (Zeile) mit +/-3px Toleranz fuer Sub-Pixel-Rundung. */
function nachZeileGruppieren(boxen: { x: number; y: number }[]): number[][] {
    const sortiert = [...boxen].sort((a, b) => a.y - b.y || a.x - b.x);
    const zeilen: { y: number; indizes: number[] }[] = [];
    for (const box of sortiert) {
        const originalIndex = boxen.indexOf(box);
        const letzteZeile = zeilen[zeilen.length - 1];
        if (letzteZeile && Math.abs(box.y - letzteZeile.y) <= 3) {
            letzteZeile.indizes.push(originalIndex);
        } else {
            zeilen.push({ y: box.y, indizes: [originalIndex] });
        }
    }
    return zeilen.map((z) => z.indizes);
}

test.describe('Anfrage-Detailseite: Kopf und Reiterleiste im schlimmsten Fall', () => {
    test('Reiterleiste bleibt einzeilig, "Bearbeiten"/"Löschen" bleiben in der Kopf-Karte', async ({ page }, testInfo) => {
        await stubAnfrageApi(page);
        await page.goto(`/anfragen?anfrageId=${ANFRAGE_ID}&tab=geschaeftsdokumente`);

        const bearbeiten = page.getByRole('button', { name: 'Bearbeiten' });
        const loeschen = page.getByRole('button', { name: 'Löschen' });
        await expect(bearbeiten).toBeVisible();
        await expect(loeschen).toBeVisible();

        // Reiterleiste: die fuenf Reiter (E-Mails, Geschaeftsdokumente, Dateien,
        // Beschreibung, Tagebuch) muessen dieselbe boundingBox().y haben --
        // sonst ist die Leiste zweizeilig bzw. laeuft ueber (overflow-x-auto
        // versteckt das heute still). "Tagebuch" statt "Bau Tagebuch" seit der
        // Nacharbeit Abschnitt 4, Punkt 9 (Wortlaut mit dem Projekt-Editor
        // vereinheitlicht, der seit Abschnitt 3 nur noch "Tagebuch" sagt).
        const reiter = [
            page.getByRole('button', { name: /^E-Mails/ }),
            page.getByRole('button', { name: /^Geschäftsdokumente/ }),
            page.getByRole('button', { name: /^Dateien/ }),
            page.getByRole('button', { name: 'Beschreibung', exact: true }),
            page.getByRole('button', { name: /^Tagebuch/ }),
        ];
        const reiterBoxen = await Promise.all(reiter.map((r) => r.boundingBox()));
        for (const box of reiterBoxen) {
            expect(box, 'Reiter-Knopf muss einen messbaren Rahmen haben').not.toBeNull();
        }
        const yWerte = reiterBoxen.map((b) => b!.y);
        const abweichung = Math.max(...yWerte) - Math.min(...yWerte);
        expect(
            abweichung,
            `Reiterleiste ist nicht einzeilig -- y-Werte der fuenf Reiter: ${yWerte.map((y) => y.toFixed(1)).join(', ')}`,
        ).toBeLessThanOrEqual(2);

        // Nacharbeit Abschnitt 4, Punkt 7 (Code-Review-Hinweis 2): Reiterleiste
        // darf kein verstecktes Scrollen zurueckbekommen. Bei Anfrage faengt das
        // bisher NICHTS ab, weil die fuenf kurzen Reiter ohnehin in eine Zeile
        // passen -- die y-Pruefung oben bliebe bei overflow-x-auto zufaellig gruen.
        const reiterleisteOverflowX = await reiter[0]!.evaluate((el) => getComputedStyle(el.parentElement!).overflowX);
        expect(
            reiterleisteOverflowX,
            `Reiterleiste hat overflow-x: ${reiterleisteOverflowX} -- verstecktes Scrollen statt Umbruch waere ein Rueckfall`,
        ).toBe('visible');

        // Kopf-Karte: naechster Vorfahre von "Bearbeiten" mit Card-Klassen
        // (siehe src/components/ui/card.tsx: "rounded-lg shadow-sm").
        const kopfKarte = bearbeiten.locator(
            'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " shadow-sm ")][1]',
        );
        const karteBox = await kopfKarte.boundingBox();
        expect(karteBox, 'Kopf-Karte muss einen messbaren Rahmen haben').not.toBeNull();

        for (const [label, locator] of [['Bearbeiten', bearbeiten], ['Löschen', loeschen]] as const) {
            const box = await locator.boundingBox();
            expect(box, `"${label}"-Knopf muss einen messbaren Rahmen haben`).not.toBeNull();
            const rechtsUeberstand = box!.x + box!.width - (karteBox!.x + karteBox!.width);
            expect(
                rechtsUeberstand,
                `"${label}" ragt ${rechtsUeberstand.toFixed(0)}px rechts aus der Kopf-Karte`,
            ).toBeLessThanOrEqual(0);
            const untenUeberstand = box!.y + box!.height - (karteBox!.y + karteBox!.height);
            expect(
                untenUeberstand,
                `"${label}" ragt ${untenUeberstand.toFixed(0)}px unten aus der Kopf-Karte`,
            ).toBeLessThanOrEqual(0);
            expect(
                box!.y,
                `"${label}" beginnt oberhalb der Kopf-Karte`,
            ).toBeGreaterThanOrEqual(karteBox!.y - 1);
        }

        // Nacharbeit Abschnitt 4, Punkt 3: der Knopfblock muss RECHTS stehen
        // (x-Position groesser als die Kartenmitte), nicht nur "irgendwo in
        // der Karte" -- ohne ml-auto faellt er beim Umbruch an den linken
        // Kartenrand (am Projekt-Editor gemessen: x=89 statt x=961 bei 1440px).
        const knopfblock = bearbeiten.locator('xpath=..');
        const knopfblockBox = await knopfblock.boundingBox();
        expect(knopfblockBox, 'Knopfblock muss einen messbaren Rahmen haben').not.toBeNull();
        const karteMitteX = karteBox!.x + karteBox!.width / 2;
        expect(
            knopfblockBox!.x,
            `Knopfblock (x=${knopfblockBox!.x.toFixed(0)}) steht nicht rechts von der Kartenmitte (${karteMitteX.toFixed(0)}) -- ml-auto fehlt oder wirkt nicht`,
        ).toBeGreaterThan(karteMitteX);

        // designPruefung deckt zusaetzlich ab: main ohne Ueberstand (Kern von
        // Spec A/Befund 1), keine ueberlappenden interaktiven Elemente,
        // Primaeraktion sichtbar, und -- ueber strengePruefungen -- dass kein
        // Text ueber seinen Kasten laeuft bzw. ungewollt gekuerzt ist.
        await designPruefung(page, testInfo, 'anfrage-detail-kopf', {
            primaerAktion: bearbeiten,
            strengePruefungen: true,
        });

        // Jeden der uebrigen vier Reiter einmal anklicken: main darf auf
        // keinem Reiter ueberlaufen (nicht nur auf dem Deep-Link-Ziel).
        for (const locator of [reiter[0], reiter[2], reiter[3], reiter[4]]) {
            await locator.click();
            await keinHorizontalerUeberlauf(page);
        }
    });

    // Nacharbeit Abschnitt 4, Punkt 8: der Test oben mit dem Spec-BAUVORHABEN
    // (Woerter mit Leerzeichen) haelt die Kopf-/Reiterleisten-Haertung NICHT
    // fest -- ein Zeilenumbruch an Leerzeichen rettet die Kopfzeile schon ohne
    // break-words/min-w-[18rem] (bestaetigt vom Design-Reviewer: Rueckbau auf
    // 69fb5c7f~1 liess genau diesen Testfall in beiden Groessen gruen). Ein
    // Komposita-Bauvorhaben OHNE Leerzeichen ist der Fall, den die Bauweise
    // wirklich loest.
    test('Kopfzeile uebersteht ein langes Komposita-Bauvorhaben ohne Leerzeichen', async ({ page }, testInfo) => {
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
            if (/^\/api\/last-accessed\/ANFRAGE(\/\d+)?$/.test(pfad)) {
                if (methode === 'POST') return route.fulfill({ status: 204, body: '' });
                return json(route, {});
            }
            if (pfad === `/api/anfragen/${ANFRAGE_ID}`) {
                return json(route, { ...DUMMY_ANFRAGE_DETAIL, bauvorhaben: BAUVORHABEN_KOMPOSITA_OHNE_LEERZEICHEN });
            }
            return json(route, []);
        });
        await page.goto(`/anfragen?anfrageId=${ANFRAGE_ID}&tab=geschaeftsdokumente`);

        const bearbeiten = page.getByRole('button', { name: 'Bearbeiten' });
        const loeschen = page.getByRole('button', { name: 'Löschen' });
        const titel = page.getByRole('heading', { name: BAUVORHABEN_KOMPOSITA_OHNE_LEERZEICHEN });
        await expect(titel).toBeVisible();
        await expect(bearbeiten).toBeVisible();
        await expect(loeschen).toBeVisible();

        // Nachbesserung 1 (Design-Review, 🔴): die <h1> selbst ist Flex-Item
        // (in "flex items-center gap-3 flex-wrap") und behielt ihr eigenes
        // min-width: auto -- break-words + min-w-0 am umschliessenden div
        // reichten bei EINEM einzigen langen Wort nicht, die <h1> lief 999px
        // breit quer ueber die Kennzahlen. Diese Zusicherung fehlte bisher
        // hier komplett (nur Knopfposition + keinHorizontalerUeberlauf).
        const titelblock = titel.locator(
            'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " min-w-[18rem] ")][1]',
        );
        const titelBox = await titel.boundingBox();
        const titelblockBox = await titelblock.boundingBox();
        expect(titelBox, '<h1> muss einen messbaren Rahmen haben').not.toBeNull();
        expect(titelblockBox, 'Titelblock muss einen messbaren Rahmen haben').not.toBeNull();
        const titelUeberstand = (titelBox!.x + titelBox!.width) - (titelblockBox!.x + titelblockBox!.width);
        expect(
            titelUeberstand,
            `<h1> (Breite ${titelBox!.width.toFixed(0)}px) ragt ${titelUeberstand.toFixed(0)}px rechts aus dem Titelblock (Breite ${titelblockBox!.width.toFixed(0)}px) -- min-w-0 an der <h1> fehlt oder wirkt nicht`,
        ).toBeLessThanOrEqual(1);

        // Dieselben Zusicherungen wie im Test oben: Knoepfe vollstaendig in der
        // Kopf-Karte und kein horizontaler Ueberlauf -- OHNE break-words + die
        // min-w-0/min-w-[18rem]-Kombination auf dem Titelblock zwingt das
        // unteilbare Wort die Kopfzeile zum Ueberlaufen bzw. schiebt die
        // Knoepfe aus der Karte.
        const kopfKarte = bearbeiten.locator(
            'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " shadow-sm ")][1]',
        );
        const karteBox = await kopfKarte.boundingBox();
        expect(karteBox, 'Kopf-Karte muss einen messbaren Rahmen haben').not.toBeNull();
        for (const [label, locator] of [['Bearbeiten', bearbeiten], ['Löschen', loeschen]] as const) {
            const box = await locator.boundingBox();
            expect(box, `"${label}"-Knopf muss einen messbaren Rahmen haben`).not.toBeNull();
            const rechtsUeberstand = box!.x + box!.width - (karteBox!.x + karteBox!.width);
            expect(
                rechtsUeberstand,
                `"${label}" ragt ${rechtsUeberstand.toFixed(0)}px rechts aus der Kopf-Karte (Komposita-Bauvorhaben)`,
            ).toBeLessThanOrEqual(0);
        }
        await keinHorizontalerUeberlauf(page);
        await designPruefung(page, testInfo, 'anfrage-detail-kopf-komposita', {
            primaerAktion: bearbeiten,
            strengePruefungen: true,
        });
    });
});

test.describe('Anfragen-Uebersicht: vier Karten mit langen Titeln', () => {
    const UEBERSICHT = [
        { id: 101, bauvorhaben: BAUVORHABEN, kundenName: KUNDE, anfragesnummer: 'AG-2026/09/00101', betrag: 12000, anlegedatum: '2026-02-01', abgeschlossen: false },
        { id: 102, bauvorhaben: 'Terrassenüberdachung mit Glasdach und Sonnenschutz Musterhof Nordflügel', kundenName: 'Musterbau Nordflügel GmbH', anfragesnummer: 'AG-2026/09/00102', betrag: 8600, anlegedatum: '2026-02-02', abgeschlossen: false },
        { id: 103, bauvorhaben: 'Geländer und Handlauf Dachterrasse Mehrfamilienhaus Beispielallee Süd', kundenName: 'Wohnbau Beispielallee eG', anfragesnummer: 'AG-2026/09/00103', betrag: 5400, anlegedatum: '2026-02-03', abgeschlossen: false },
        { id: 104, bauvorhaben: 'Balkonanlage mit Sichtschutz und Blumentrog Wohnanlage Beispielpark West', kundenName: 'Beispielpark Verwaltungs GmbH', anfragesnummer: 'AG-2026/09/00104', betrag: 9100, anlegedatum: '2026-02-04', abgeschlossen: false },
    ];

    test('kein Titel einzeilig abgehackt, Kartenraster passt zur Fenstergröße', async ({ page }, testInfo) => {
        await stubAnfrageApi(page, { uebersicht: UEBERSICHT });
        await page.goto('/anfragen');

        await expect(page.getByRole('heading', { level: 3 })).toHaveCount(4);

        // Kartenraster: bei pc-14zoll (1440) drei Karten je Reihe, bei
        // pc-monitor (1920) vier -- Spec D / Plan-Vorgabe "2xl:grid-cols-4".
        const karten = page.locator('div.group.relative.cursor-pointer');
        await expect(karten).toHaveCount(4);
        const boxen = await karten.evaluateAll((elemente) =>
            elemente.map((el) => {
                const r = el.getBoundingClientRect();
                return { x: r.x, y: r.y };
            }),
        );
        const zeilen = nachZeileGruppieren(boxen);
        const zeilenGroessen = zeilen.map((z) => z.length);

        if (testInfo.project.name === 'pc-monitor') {
            expect(
                zeilenGroessen,
                `Bei 1920px sollten alle vier Karten in einer Reihe stehen, gemessene Zeilen: ${JSON.stringify(zeilenGroessen)}`,
            ).toEqual([4]);
        } else {
            expect(
                zeilenGroessen,
                `Bei 1440px sollten drei Karten in der ersten und eine in der zweiten Reihe stehen, gemessene Zeilen: ${JSON.stringify(zeilenGroessen)}`,
            ).toEqual([3, 1]);
        }

        // designPruefung mit strengePruefungen deckt "kein Titel einzeilig
        // abgehackt" ab (keinTextGekuerzt erkennt text-overflow: ellipsis mit
        // echtem Ueberstand) -- eine Kuerzung ist hier nur mit
        // data-kuerzung-erlaubt (line-clamp-2 + title) zulaessig.
        await designPruefung(page, testInfo, 'anfragen-uebersicht-karten', { strengePruefungen: true });
    });

    // Nacharbeit Abschnitt 4, Punkt 4 (Design-Review-Befund): min-h-[3rem] auf
    // dem Kartentitel riss bei einem KURZEN Bauvorhaben eine 24px-Luecke
    // zwischen Titel und Kundenname (gemessen: 48px Titelhoehe fuer 24px Text).
    // h-full flex flex-col an der Karte + mt-auto am Meta-Block darunter loesen
    // das, ohne die gleiche Kartenhoehe in einer Reihe zu verlieren.
    //
    // Nachtrag Abschnitt 5 (Task 9), Code-Review Runde 2 Hinweis 4: die
    // Trennlinien-Zusicherung (space-y-3 -> gap-3, damit mt-auto am
    // Meta-Block wirklich greift) stand bisher nur in kunde-layout.spec.ts --
    // ein Rueckfall auf space-y-3 waere hier unbemerkt geblieben. Zweite
    // Anfrage mit langem Bauvorhaben in DERSELBEN Reihe ergaenzt.
    test('kurzes Bauvorhaben reisst keine Luecke, Trennlinie bleibt auf Hoehe der Nachbarkarte', async ({ page }, testInfo) => {
        const LANGE_ANFRAGE = { id: 202, bauvorhaben: BAUVORHABEN, kundenName: KUNDE, anfragesnummer: 'AG-2026/09/00202', betrag: 45000, anlegedatum: '2026-02-11', abgeschlossen: false };
        const KURZ = [
            { id: 201, bauvorhaben: 'Carport', kundenName: 'Meier Bau GmbH', anfragesnummer: 'AG-2026/09/00201', betrag: 4200, anlegedatum: '2026-02-10', abgeschlossen: false },
            LANGE_ANFRAGE,
        ];
        await stubAnfrageApi(page, { uebersicht: KURZ });
        await page.goto('/anfragen');

        const titel = page.getByRole('heading', { level: 3, name: 'Carport' });
        await expect(titel).toBeVisible();
        const kundenname = page.getByText('Meier Bau GmbH', { exact: true });
        await expect(kundenname).toBeVisible();

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

        // Trennlinie: die Anfragenummer ist die erste Zeile des mt-auto-
        // Meta-Blocks. space-y-3 (Spezifitaet 0-3-0) schlaegt mt-auto
        // (0-1-0) nieder -- ohne gap-3 waere die Trennlinie bei "Carport"
        // 24px hoeher als bei der langen Anfrage (Design-Review Abschnitt 4).
        const nummerKurz = page.getByText('AG-2026/09/00201', { exact: true });
        const nummerLang = page.getByText(LANGE_ANFRAGE.anfragesnummer, { exact: true });
        await expect(nummerKurz).toBeVisible();
        await expect(nummerLang).toBeVisible();
        const boxKurz = await nummerKurz.boundingBox();
        const boxLang = await nummerLang.boundingBox();
        expect(boxKurz, 'Anfragenummer der kurzen Karte muss einen messbaren Rahmen haben').not.toBeNull();
        expect(boxLang, 'Anfragenummer der langen Karte muss einen messbaren Rahmen haben').not.toBeNull();
        const versatz = Math.abs(boxKurz!.y - boxLang!.y);
        expect(
            versatz,
            `Trennlinien-Meta-Zeilen sind ${versatz.toFixed(0)}px versetzt (y-Werte: ${boxKurz!.y.toFixed(0)}, ${boxLang!.y.toFixed(0)}) -- mt-auto wirkt nicht (space-y-3-Spezifitaet?)`,
        ).toBeLessThanOrEqual(2);

        await designPruefung(page, testInfo, 'anfragen-uebersicht-kurzer-titel', { strengePruefungen: true });
    });
});

// Task 12 (Abschnitt 8): systematischer Nachweis fuer die Seitenspalte
// "Anfragedaten" -- Ansprechpartner, Telefon, Mobiltelefon und Projektadresse
// wurden bisher NIE mit echt ueberlaufendem Inhalt gerendert.
test.describe('Anfrage-Seitenspalte "Anfragedaten": lange, bindestrichlose Werte', () => {
    test('Ansprechpartner, Telefon, Mobiltelefon und Projektadresse laufen nicht ueber ihre Kaesten', async ({ page }) => {
        // Muss vor der ersten Navigation stehen: mit gesetzter Adresse rendert
        // GoogleMapsEmbed ein echtes <iframe src="https://www.google.com/maps?...">
        // (siehe Kommentar bei DUMMY_ANFRAGE_DETAIL oben).
        await blockiereFremdeNetzwerkzugriffe(page);
        await stubAnfrageApi(page);
        await page.route(`**/api/anfragen/${ANFRAGE_ID}`, (route) => {
            if (route.request().method() !== 'GET') return route.fallback();
            return json(route, {
                ...DUMMY_ANFRAGE_DETAIL,
                kundenAnsprechpartner: ANSPRECHPARTNER_LANG,
                kundenTelefon: TELEFON_LANG,
                kundenMobiltelefon: MOBILTELEFON_LANG,
                projektStrasse: STRASSE_LANG,
                projektPlz: '99999',
                projektOrt: 'Musterstadt',
            });
        });
        await page.goto(`/anfragen?anfrageId=${ANFRAGE_ID}&tab=geschaeftsdokumente`);

        await expect(page.getByRole('heading', { name: BAUVORHABEN })).toBeVisible();

        const seitenKarte = page.getByRole('heading', { name: 'Anfragedaten' }).locator(
            'xpath=ancestor::div[contains(concat(" ", normalize-space(@class), " "), " shadow-sm ")][1]',
        );
        // Jeder Wert-<p>/<a> ist ein normaler Block (kein Flex-Item, das
        // eigenmaechtigen "min-w-0"-Regeln unterliegt) -- ohne break-words
        // ueberlaeuft er unsichtbar (scrollWidth > clientWidth), OHNE dass
        // sich seine eigene boundingBox() aendert (ein Block ohne explizite
        // Breite bleibt bei "Breite = Elternbreite", der Text malt nur ueber
        // den Rand hinaus). Direkt scrollWidth/clientWidth pruefen statt eine
        // Positions-Geometrie, die genau diesen Fall nicht sieht (dieselbe
        // Messmethode wie keinTextLaeuftUeber in e2e/hilfen/design.ts) --
        // empirisch verifiziert: eine boundingBox-Variante blieb hier auch
        // OHNE break-words gruen (siehe Kontext-Log-Block dieses Tasks).
        const pruefeWertBleibtImKasten = async (wert: string, feldname: string, exact = true) => {
            const wertElement = seitenKarte.getByText(wert, { exact });
            await expect(wertElement, `${feldname}-Wert "${wert}" fehlt`).toBeVisible();
            const ueberstand = await wertElement.evaluate((el) => el.scrollWidth - el.clientWidth);
            expect(
                ueberstand,
                `${feldname}-Wert "${wert.slice(0, 30)}..." laeuft ${ueberstand}px ueber seinen eigenen Kasten -- braucht break-words am Wert`,
            ).toBeLessThanOrEqual(2);
        };
        await pruefeWertBleibtImKasten(ANSPRECHPARTNER_LANG, 'Ansprechpartner');
        await pruefeWertBleibtImKasten(TELEFON_LANG, 'Telefon');
        // exact:false: das <a> haengt " (Mobil)" an den Wert an
        // (AnfrageEditor.tsx), ein exakter Treffer auf nur die Ziffernkette
        // waere nie moeglich.
        await pruefeWertBleibtImKasten(MOBILTELEFON_LANG, 'Mobiltelefon', false);
        await pruefeWertBleibtImKasten(STRASSE_LANG, 'Projektadresse (Strasse)', false);

        await keinTextGekuerzt(page);
        await keinHorizontalerUeberlauf(page);
    });
});
