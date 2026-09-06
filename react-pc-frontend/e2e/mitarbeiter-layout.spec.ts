import { test, expect, type Page, type Route } from '@playwright/test';
import { designPruefung } from './hilfen/design';

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
 */

const MITARBEITER_ID = 42;
const VORNAME = 'Bernhardine';
const NACHNAME = 'Beispielmusterfrauenbergwaldschmidtstein';
const ABTEILUNG = 'Sonderaufgabenkoordinationsstellenverwaltung';

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
    email: null,
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
        await stubMitarbeiterApi(page);
        await page.goto('/mitarbeiter');

        await expect(page.getByRole('heading', { name: 'MITARBEITER' })).toBeVisible();
        await page.getByText(`${NACHNAME}, ${VORNAME}`, { exact: true }).click();

        const ueberschrift = page.getByRole('heading', { name: `${NACHNAME}, ${VORNAME}` });
        await expect(ueberschrift).toBeVisible();

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
