import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Task 16 (Abschnitt 5) aus
 * docs/superpowers/plans/2026-09-08-langzeitkrankmeldung.md:
 * Der Verrechnungslohn-Rechner muss zeigen, wie viele Tage einer
 * Langzeitkrankmeldung (Krankengeld/Wiedereingliederung) aus Jahressoll und
 * Lohnkosten herausgerechnet wurden -- sonst wirkt der veraenderte
 * Stundensatz wie ein Fehler.
 *
 * Ablauf: /arbeitsgaenge -> Knopf "Was muss meine Stunde kosten?" oeffnet
 * VerrechnungslohnRechnerDialog (siehe src/pages/ArbeitsgangEditor.tsx:540)
 * -> Sektion "Wie viele Stunden kann ich verkaufen?" aufklappen -> neue
 * Spalte "Krankengeld/Wiedereingliederung" pruefen.
 *
 * /api vollstaendig gestubbt, kein Backend. DSGVO: ausschliesslich der
 * Projekt-Dummy "Max Mustermann", keine echten Personendaten.
 *
 * keinHorizontalerUeberlauf ist hier besonders wichtig (siehe
 * .claude/skills/loese-problem/references/kriterien.md): die Stundentabelle
 * bekommt mit dieser Aenderung eine siebte Spalte und ist bei 1440px der
 * wahrscheinlichste Ueberlaufkandidat -- die Tabelle steckt aber bewusst in
 * einem eigenen "overflow-x-auto"-Container (Vorbild: die anderen drei
 * Tabellen in dieser Datei), ein Ueberlauf DORT ist deshalb kein Fehler, nur
 * ein Ueberlauf des Dokuments/Dialogs waere einer.
 */

const MITARBEITER_ID = 1;

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

/** Ein Mitarbeiter mit einer Langzeitkrankmeldung: 122 von 365 Tagen sind
 * als Krankengeld/Wiedereingliederung aus Jahressoll und Lohnkosten
 * herausgerechnet (Task 13, Backend, bereits fertig). */
const DUMMY_ANTWORT = {
    jahr: 2025,
    modus: 'RUECKWIRKEND',
    interneQuoteProzent: 5,
    lohnzeilen: [
        {
            mitarbeiterId: MITARBEITER_ID,
            name: 'Max Mustermann',
            istGeschaeftsfuehrer: false,
            beschaeftigungsart: 'REGULAER',
            bruttoJahr: 35000,
            agAnteilSv: 7000,
            bgBeitrag: 500,
            geldwerterVorteilJahr: 0,
            gesamtkosten: 42500,
            quelle: 'LOHNABRECHNUNG',
            bruttoIstDefault: false,
        },
    ],
    stundenzeilen: [
        {
            mitarbeiterId: MITARBEITER_ID,
            name: 'Max Mustermann',
            istGeschaeftsfuehrer: false,
            sollstunden: 2080,
            urlaubsstunden: 200,
            krankheitsstunden: 64,
            interneStunden: 104,
            feiertagsstunden: 96,
            verkaeuflicheStunden: 1200,
            urlaubIstDefault: false,
            krankheitIstDefault: false,
            interneIstDefault: false,
            sollIstDefault: false,
            ausgeklammerteTage: 122,
        },
    ],
    kostenstellen: [],
    abteilungen: [],
    datenLuecken: [],
    lohnsummeGesamt: 42500,
    verkaeuflicheStundenGesamt: 1200,
    gemeinkostenGesamt: 0,
    selbstkostenProStunde: 35.42,
};

/** Stubbt alle /api-Routen der Arbeitsgaenge-Seite + des Verrechnungslohn-Dialogs. */
async function stubArbeitsgangApi(page: Page, antwort: unknown = DUMMY_ANTWORT) {
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
        if (pfad === '/api/abteilungen') return json(route, []);
        if (pfad === '/api/arbeitsgaenge') return json(route, []);
        if (pfad === '/api/verrechnungslohn') return json(route, antwort);

        // Standardantwort fuer alles Weitere: leere Liste statt 404, damit
        // kein Fehlerzustand die Seite fuellt.
        return json(route, []);
    });
}

test.describe('Verrechnungslohn-Dialog: ausgeklammerte Tage einer Langzeitkrankmeldung', () => {
    test('Spalte "Krankengeld/Wiedereingliederung" zeigt die Tage samt Erklaerung', async ({ page }, testInfo) => {
        await stubArbeitsgangApi(page);
        await page.goto('/arbeitsgaenge');

        await page.getByRole('button', { name: /Was muss meine Stunde kosten\?/ }).click();

        const dialog = page.getByRole('dialog');
        await expect(dialog).toBeVisible();

        // Sektion aufklappen -- die Tabelle ist sonst gar nicht gerendert.
        await dialog.getByRole('button', { name: /Wie viele Stunden kann ich verkaufen\?/ }).click();

        const zeile = dialog.locator('tr', { hasText: 'Max Mustermann' });
        await expect(zeile.getByText('122 Tage')).toBeVisible();
        await expect(zeile.getByText('122 Tage')).toHaveAttribute(
            'title',
            'Diese Tage sind aus Jahressoll und Lohnkosten herausgerechnet.'
        );

        await expect(
            dialog.getByText(
                'Bei 1 Mitarbeitern sind Krankengeld- und Wiedereingliederungszeiten herausgerechnet — insgesamt 122 Tage.'
            )
        ).toBeVisible();

        const uebernehmenKnopf = dialog.getByRole('button', { name: /übernehmen/i });
        await designPruefung(page, testInfo, 'verrechnungslohn-langzeitfall-stunden', {
            primaerAktion: uebernehmenKnopf,
        });
    });

    test('Mitarbeiter ohne Langzeitfall: Spalte zeigt "–", kein erklaerender Satz', async ({ page }) => {
        const antwortOhneLangzeitfall = {
            ...DUMMY_ANTWORT,
            stundenzeilen: [{ ...DUMMY_ANTWORT.stundenzeilen[0], ausgeklammerteTage: 0 }],
        };
        await stubArbeitsgangApi(page, antwortOhneLangzeitfall);
        await page.goto('/arbeitsgaenge');

        await page.getByRole('button', { name: /Was muss meine Stunde kosten\?/ }).click();
        const dialog = page.getByRole('dialog');
        await expect(dialog).toBeVisible();

        await dialog.getByRole('button', { name: /Wie viele Stunden kann ich verkaufen\?/ }).click();

        const zeile = dialog.locator('tr', { hasText: 'Max Mustermann' });
        await expect(zeile.getByText('–')).toBeVisible();
        await expect(zeile.getByText(/Tage$/)).toHaveCount(0);

        await expect(
            dialog.getByText(/Krankengeld- und Wiedereingliederungszeiten/)
        ).toHaveCount(0);
    });
});
