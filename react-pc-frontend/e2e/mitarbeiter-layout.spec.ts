import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung } from './hilfen/design';
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

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const DUMMY_MITARBEITER = {
    id: MITARBEITER_ID,
    vorname: VORNAME,
    nachname: NACHNAME,
    strasse: null,
    plz: null,
    ort: null,
    email: EMAIL_LANG,
    telefon: null,
    festnetz: null,
    qualifikation: null,
    stundenlohn: null,
    geburtstag: null,
    eintrittsdatum: null,
    aktiv: true,
    abteilungIds: [1],
    abteilungNames: ABTEILUNG,
    loginToken: null, // null -> Knopf "Token erstellen" wird gerendert (dritter Kopf-Knopf)
    jahresUrlaub: 30,
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
        if (pfad === `/api/mitarbeiter/${MITARBEITER_ID}/dokumente`) return json(route, []);
        if (pfad === `/api/mitarbeiter/${MITARBEITER_ID}/notizen`) return json(route, []);
        if (pfad === `/api/lohnabrechnungen/mitarbeiter/${MITARBEITER_ID}`) return json(route, []);

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
        await page.getByText(`${NACHNAME}, ${VORNAME}`, { exact: true }).click();

        const ueberschrift = page.getByRole('heading', { name: `${NACHNAME}, ${VORNAME}` });
        await expect(ueberschrift).toBeVisible();

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
        const pruefeWertImKasten = async (wert: string) => {
            const wertElement = seitenKarte.getByText(wert, { exact: true });
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
        };
        await pruefeWertImKasten(EMAIL_LANG);
        await pruefeWertImKasten(ABTEILUNG);

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
        // Hinweis 2): kein verstecktes Scrollen mehr moeglich. Das ist die
        // einzige Zusicherung dieser Spec, die auf dem heutigen Stand
        // tatsaechlich rot ist -- unabhaengig von jeder Fixture, weil sie
        // den Stil direkt prueft statt einen (hier datenunabhaengigen)
        // Ueberlauf in Pixeln.
        const overflowX = await reiterleiste.evaluate((el) => getComputedStyle(el).overflowX);
        expect(overflowX, 'Reiterleiste darf nicht mehr versteckt scrollen (overflow-x: auto)').toBe('visible');

        await designPruefung(page, testInfo, 'mitarbeiter-detail-reiterleiste', {
            strengePruefungen: true,
            primaerAktion: bearbeiten,
        });

        // Jeden der vier Reiter anklicken und main auf Ueberstand pruefen --
        // dieselbe Abnahme wie bei Projekt/Anfrage/Kunde/Lieferant.
        for (const name of ['Notizen', 'Lohnabrechnungen', 'Stundenlohn-Verlauf', 'Dokumente']) {
            await reiterleiste.getByRole('button', { name: new RegExp(`^${name}`) }).click();
            const mainUeberstand = await page.evaluate(() => {
                const main = document.querySelector('main');
                return main ? main.scrollWidth - main.clientWidth : 0;
            });
            expect(mainUeberstand, `main laeuft nach Klick auf "${name}" ueber`).toBe(0);
        }
    });
});
