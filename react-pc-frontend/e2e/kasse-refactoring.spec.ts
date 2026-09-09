import fs from 'node:fs';
import path from 'node:path';
import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung, keinHorizontalerUeberlauf, keinTextGekuerzt, keinTextLaeuftUeber, uebergaengeAusklingenLassen } from './hilfen/design';

/**
 * Task 9 (Plan "Kasse & Belege", Abschnitt 1): reines Refactoring von
 * BelegeKasseEditor.tsx und KasseShortcuts.tsx in einzelne Komponenten-
 * Dateien -- ohne jede Verhaltensaenderung. Diese Spec ist der end-to-end-
 * Beweis dafuer: derselbe Ablauf, dieselben Texte, dieselben Klassen wie vor
 * der Aufteilung.
 *
 * Gestubbt nach dem Muster aus monatsabschluss-task9.spec.ts (ein
 * Catch-all `page.route('**\/api/**')` mit Pfad-Weiche, Default-Antwort
 * `[]`), kein Backend. Nur Dummy-Daten (DSGVO).
 */

const BELEG_ID = 501;

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const SACHKONTEN = [
    { id: 1, nummer: '4930', bezeichnung: 'Bürobedarf', kontoTyp: 'AUFWAND', beschreibung: null, aktiv: true, sortierung: 1 },
    { id: 2, nummer: '8400', bezeichnung: 'Erlöse Leistungen', kontoTyp: 'ERTRAG', beschreibung: null, aktiv: true, sortierung: 1 },
    { id: 3, nummer: '1890', bezeichnung: 'Privatentnahme', kontoTyp: 'PRIVAT', beschreibung: null, aktiv: true, sortierung: 1 },
];

const ZAHLUNGSARTEN = [
    { id: 1, bezeichnung: 'Bar', aktiv: true, sortierung: 1 },
    { id: 2, bezeichnung: 'EC-Karte', aktiv: true, sortierung: 2 },
];

const BELEG = {
    id: BELEG_ID,
    belegKategorie: 'KASSE_AUSGABE',
    dokumentTyp: null,
    istUmbuchung: false,
    status: 'NEU',
    kiAnalyseStatus: 'DONE',
    belegDatum: '2026-09-01',
    belegNummer: null,
    beschreibung: 'Werkzeugkauf Baumarkt',
    betragNetto: 42.02,
    betragBrutto: 50.00,
    mwstSatz: 19,
    zahlungsart: 'Bar',
    lieferantId: null,
    lieferantName: null,
    sachkontoId: null,
    sachkontoBezeichnung: null,
    sachkontoNummer: null,
    sachkontoTyp: null,
    kiVorgeschlagenerLieferant: null,
    kiConfidence: null,
    kiVorgeschlagenerKostenstelleId: null,
    kiVorgeschlagenerKostenstelleBezeichnung: null,
    kiVorgeschlagenerSachkontoId: null,
    kiVorgeschlagenerSachkontoBezeichnung: null,
    kiKostenkontoConfidence: null,
    kiKostenkontoBegruendung: null,
    kiFehlerText: null,
    originalDateiname: 'quittung-baumarkt.jpg',
    mimeType: 'image/jpeg',
    uploadDatum: '2026-09-01T08:00:00',
    uploadedByName: 'Max Mustermann',
    validiertAm: null,
    validiertVonName: null,
    notiz: null,
    eingangsrechnungId: null,
    aufteilungsModus: null,
    betragFirmaNetto: null,
    betragFirmaBrutto: null,
    betragFirmaMwst: null,
    positionen: null,
    kostenstellenSplits: null,
    laufendeNummer: null,
    festgeschrieben: false,
    festgeschriebenAm: null,
    stornoFuerBelegId: null,
    storniertDurchBelegId: null,
    storniertAm: null,
    stornoGrund: null,
};

const KASSENBUCH = {
    saldoStart: 100.00,
    saldoEnde: 250.00,
    summeEinnahmen: 200.00,
    summeAusgaben: 50.00,
    summePrivatentnahmen: 0,
    summePrivateinlagen: 0,
    bewegungen: [
        {
            belegId: 601, datum: '2026-09-02', kategorie: 'KASSE_EINNAHME',
            beschreibung: 'Kleinverkauf Material', lieferantName: null,
            betrag: 200.00, saldoNachher: 300.00, laufendeNummer: null, festgeschrieben: false,
            sachkontoNummer: '8400', sachkontoBezeichnung: 'Erlöse Leistungen',
            zahlungsart: 'Bar', mwstSatz: 19, mwstBetrag: 31.93,
            stornoFuerBelegId: null, storniertDurchBelegId: null,
        },
        {
            belegId: BELEG_ID, datum: '2026-09-01', kategorie: 'KASSE_AUSGABE',
            beschreibung: 'Werkzeugkauf Baumarkt', lieferantName: null,
            betrag: -50.00, saldoNachher: 250.00, laufendeNummer: null, festgeschrieben: false,
            sachkontoNummer: null, sachkontoBezeichnung: null,
            zahlungsart: 'Bar', mwstSatz: 19, mwstBetrag: 7.98,
            stornoFuerBelegId: null, storniertDurchBelegId: null,
        },
    ],
    letzterAbschluss: null,
    offeneBewegungen: 2,
};

const KASSE_EINSTELLUNG = {
    id: 1,
    mindestbestand: 50.00,
    ehegattengehaltAktiv: false,
    ehegattengehaltBetrag: null,
    ehegattengehaltTag: null,
    ehegattengehaltEmpfaengerName: null,
    privateinlageSachkontoId: null,
};

async function stub(page: Page) {
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        const path = url.pathname;
        const method = route.request().method();

        if (path === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'max.mustermann', displayName: 'Max Mustermann',
                active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
            });
        }
        if (path === '/api/notifications/summary') {
            return json(route, { totalCount: 0, categories: [], recentItems: [] });
        }
        if (path === '/api/buchhaltung/belege' && method === 'GET') {
            return json(route, [BELEG]);
        }
        if (path === `/api/buchhaltung/belege/${BELEG_ID}`) {
            return json(route, BELEG);
        }
        if (path === '/api/buchhaltung/sachkonten') {
            return json(route, SACHKONTEN);
        }
        if (path === '/api/buchhaltung/zahlungsarten') {
            return json(route, ZAHLUNGSARTEN);
        }
        if (path === '/api/buchhaltung/kassenbuch') {
            return json(route, KASSENBUCH);
        }
        if (path === '/api/buchhaltung/kasse/saldo') {
            return json(route, { saldo: 250.00, mindestbestand: 50.00 });
        }
        if (path === '/api/buchhaltung/kasse/einstellung') {
            return json(route, KASSE_EINSTELLUNG);
        }
        return json(route, []);
    });
}

test('Belege & Kasse bleibt nach der Verschiebung unveraendert: vier Tabs, Kassenbuch, Pruefen-Dialog, Shortcuts', async ({ page }, info) => {
    await stub(page);
    await page.goto('/belege-kasse');

    // PageLayout stellt den Titel per CSS (uppercase) dar -- Chromium
    // berechnet den Accessible Name danach, siehe z.B.
    // mitarbeiter-layout.spec.ts ("MITARBEITER").
    await expect(page.getByRole('heading', { name: 'BELEGE & KASSE' })).toBeVisible();

    // Die vier Tabs sind da -- gleiche Beschriftung wie vor der Verschiebung.
    const tabEingang = page.getByRole('button', { name: /Eingang \(Validierung\)/ });
    const tabAlle = page.getByRole('button', { name: 'Alle Belege', exact: true });
    const tabKasse = page.getByRole('button', { name: 'Kassenbuch', exact: true });
    const tabAuswertung = page.getByRole('button', { name: 'Auswertung', exact: true });
    await expect(tabEingang).toBeVisible();
    await expect(tabAlle).toBeVisible();
    await expect(tabKasse).toBeVisible();
    await expect(tabAuswertung).toBeVisible();

    // BelegRow zeigt als Titel Beleg-Nummer/KI-Lieferant/Dateiname (in dieser
    // Reihenfolge) -- bei unserem Dummy-Beleg den Dateinamen, nicht die
    // Beschreibung (die erscheint erst im Kassenbuch-Journal, siehe unten).
    const hochladenButton = page.getByRole('button', { name: 'Beleg hochladen' });
    await expect(page.getByRole('button', { name: /quittung-baumarkt\.jpg/ })).toBeVisible();
    await designPruefung(page, info, 'kasse-refactoring-eingang', { primaerAktion: hochladenButton });

    // Kassenbuch-Tab: KassenbuchJournal (heutiger KassenbuchView) zeigt
    // weiterhin "Eingang"/"Ausgang" und den Saldo -- inhaltlich unveraendert.
    await tabKasse.click();
    await expect(page.getByText('Eingang', { exact: true })).toBeVisible();
    await expect(page.getByText('Ausgang', { exact: true })).toBeVisible();
    await expect(page.getByText('Neuer Saldo')).toBeVisible();
    await expect(page.getByText('250,00 €').first()).toBeVisible();

    // Die vier Shortcut-Knoepfe aus KasseShortcuts (jetzt via NeueBuchungDialog
    // + KasseEinstellungenDialog verkabelt) sind sichtbar.
    await expect(page.getByRole('button', { name: /Bank → Kasse/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Ehegattengehalt/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Privateinlage/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /Privatentnahme/ })).toBeVisible();
    await designPruefung(page, info, 'kasse-refactoring-kassenbuch', { primaerAktion: hochladenButton });

    // Prüfen-Dialog: oeffnet sich per Klick auf eine Belegzeile im Kassenbuch-
    // Journal (T-Konto-Zeile) und zeigt denselben Titel wie vorher.
    await page.getByRole('button', { name: /Werkzeugkauf Baumarkt/ }).first().click();
    const modalTitel = page.getByRole('heading', { name: 'Beleg prüfen & validieren', exact: true });
    await expect(modalTitel).toBeVisible();
    const uebernehmenButton = page.getByRole('button', { name: 'Prüfen & Übernehmen' });
    await expect(uebernehmenButton).toBeVisible();

    // designPruefung() selbst wird fuer diesen Zustand NICHT aufgerufen --
    // Vorbild: rahmen-detailseite.spec.ts, die aus demselben Grund nur die
    // einzelnen Teile aufruft. BelegDetailModal.tsx (vorbestehend, von Task 9
    // wortgleich verschoben, siehe Plan-Vorgabe "JSX ... bleiben zeichengleich")
    // traegt auf seinem Wrapper kein role="dialog". keineUeberschneidungen()
    // blendet den Hintergrund eines offenen Dialogs nur aus, wenn sie dieses
    // Attribut findet -- ohne es vergleicht sie Hintergrund-Elemente (hier:
    // die Von/Bis/Suche-Felder des Kassenbuch-Journals und die vier
    // Shortcut-Knoepfe, durch bg-black/50 fuer den Nutzer unsichtbar) gegen
    // Elemente im Modal und meldet Ueberschneidungen, die niemand sieht. Das
    // ist ein vorbestehender Zustand (kein role="dialog" schon vor diesem
    // Task) und keine Verhaltensaenderung von Task 9 -- Beheben wuerde die
    // Modal-JSX aendern, was der Task ausdruecklich verbietet. Siehe
    // Kontext-Log fuer das Bedenken.
    await uebergaengeAusklingenLassen(page);
    const zielOrdner = path.join(info.project.outputDir, 'design');
    fs.mkdirSync(zielOrdner, { recursive: true });
    const bildPfad = path.join(zielOrdner, `kasse-refactoring-beleg-modal--${info.project.name}.png`);
    await page.screenshot({ path: bildPfad, fullPage: false });
    await info.attach('design: kasse-refactoring-beleg-modal', { path: bildPfad, contentType: 'image/png' });
    await keinHorizontalerUeberlauf(page);
    await keinTextLaeuftUeber(page);
    await keinTextGekuerzt(page);
    await expect(uebernehmenButton, 'Primaeraktion muss ohne Scrollen sichtbar sein').toBeInViewport();
});
