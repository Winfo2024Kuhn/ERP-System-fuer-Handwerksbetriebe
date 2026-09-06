import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung, keinHorizontalerUeberlauf } from './hilfen/design';

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

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const DUMMY_ANFRAGE_DETAIL = {
    id: ANFRAGE_ID,
    kundenId: 3,
    kundenName: KUNDE,
    bauvorhaben: BAUVORHABEN,
    kundennummer: 'K-1003',
    anfragesnummer: 'AG-2026/09/00009',
    betrag: 84500,
    // Bewusst OHNE kundenEmails/Telefon/Adresse: sonst rendert GoogleMapsEmbed
    // ein echtes <iframe src="https://www.google.com/maps?..."> -- ein
    // Netzwerkzugriff, den dieser rein gestubbte Test nicht braucht (siehe
    // gleicher Kommentar in e2e/rahmen-detailseite.spec.ts).
    kundenEmails: [] as string[],
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
        // Beschreibung, Bau Tagebuch) muessen dieselbe boundingBox().y haben --
        // sonst ist die Leiste zweizeilig bzw. laeuft ueber (overflow-x-auto
        // versteckt das heute still).
        const reiter = [
            page.getByRole('button', { name: /^E-Mails/ }),
            page.getByRole('button', { name: /^Geschäftsdokumente/ }),
            page.getByRole('button', { name: /^Dateien/ }),
            page.getByRole('button', { name: 'Beschreibung', exact: true }),
            page.getByRole('button', { name: /^Bau Tagebuch/ }),
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
});
