import type { Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung, keinHorizontalerUeberlauf } from './hilfen/design';

/**
 * Task 15 (Abschnitt 5) aus
 * docs/superpowers/plans/2026-09-08-langzeitkrankmeldung.md: End-to-End-Ablauf
 * fuer die neue Desktop-Seite "Lange Krankheit".
 *
 * Geprueft wird genau der geaenderte Ablauf: die Seite ueber den Menuepunkt
 * "Lange Krankheit" erreichen (Auffindbarkeit, Frage 5 der Design-Pruefung),
 * eine Karte aufklappen und die Phasen-Zeitleiste sehen, sowie ein
 * Umbruch-Sicherheitsnetz fuer einen langen Nachnamen OHNE Bindestrich (siehe
 * .claude/skills/loese-problem/references/kriterien.md: Bindestriche sind
 * selbst Umbruchpunkte und wuerden einen echten Ueberlauf verdecken).
 *
 * Nachbesserung Abschnitt 5:
 *   - Befund 1 (BLOCKER): Stundenwerte muessen mit deutschem Komma
 *     erscheinen -- PHASE_WIEDEREINGLIEDERUNG unten traegt bewusst eine
 *     halbe Stunde (4.5), damit ein Ruecksprung auf den englischen Punkt
 *     hier sichtbar rot wuerde.
 *   - Befund 2 (BLOCKER): mindestens ein designPruefung(...)-Aufruf mit
 *     Screenshot -- Vorbild e2e/anfrage-layout.spec.ts:243/:381.
 *   - Befund 3 (BLOCKER-nah): der neue Knopf "Doch noch krank" (erscheint
 *     nur bei status === 'BEENDET', ruft PUT .../oeffnen) und sein
 *     Konfliktverhalten bei 409.
 *
 * /api vollstaendig gestubbt (kein Backend), nur Fantasienamen (DSGVO).
 * blockiereFremdeNetzwerkzugriffe laeuft automatisch ueber e2e/hilfen/test.ts.
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const AUTH_ME = {
    id: 1, username: 'anna.buero', displayName: 'Anna Büro',
    active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
};
const NOTIFICATIONS_LEER = { totalCount: 0, categories: [], recentItems: [] };

const PHASE_LOHNFORTZAHLUNG = {
    id: 100,
    typ: 'LOHNFORTZAHLUNG',
    label: 'Lohnfortzahlung durch den Betrieb',
    vonDatum: '2026-03-01',
    bisDatum: '2026-03-31',
    stundenProTag: null,
};

// Befund 1: halbe Stunde, damit ein Ruecksprung auf den englischen
// Dezimalpunkt ("4.5 Std. pro Tag" statt "4,5 Std. pro Tag") hier sichtbar
// rot wird -- die Ganzzahl 2 aus PHASE_LOHNFORTZAHLUNG-Faellen deckt das
// nicht ab (2 sieht in beiden Formaten gleich aus).
const PHASE_WIEDEREINGLIEDERUNG = {
    id: 101,
    typ: 'WIEDEREINGLIEDERUNG',
    label: 'Wiedereingliederung',
    vonDatum: '2026-04-01',
    bisDatum: null,
    stundenProTag: 4.5,
};

const MELDUNG_KURZ = {
    id: 1,
    mitarbeiterId: 10,
    mitarbeiterName: 'Mustermann, Max',
    beginn: '2026-03-01',
    ende: null,
    status: 'LAUFEND',
    statusLabel: 'Läuft noch',
    lohnfortzahlungBis: '2026-04-11',
    notiz: null,
    version: 3,
    aktuellePhaseTyp: 'LOHNFORTZAHLUNG',
    aktuellePhaseLabel: 'Lohnfortzahlung durch den Betrieb',
    restTageLohnfortzahlung: 12,
    heuteGeplanteStunden: null,
    geplanteRueckkehr: '2026-05-01',
    phasen: [PHASE_LOHNFORTZAHLUNG],
};

// Langer, zusammengesetzter Nachname OHNE Bindestrich -- ein Bindestrich waere
// selbst ein Umbruchpunkt und wuerde einen echten Ueberlauf verdecken
// (kriterien.md, bei 1440px gemessen: 0px Ueberstand mit Bindestrich,
// 272px ohne).
const LANGER_NACHNAME = 'Musterfrauwinterkoetterbergschmidt';

const MELDUNG_LANGER_NAME = {
    ...MELDUNG_KURZ,
    id: 2,
    mitarbeiterId: 11,
    mitarbeiterName: `${LANGER_NACHNAME}, Erika`,
    lohnfortzahlungBis: '2026-03-14',
    restTageLohnfortzahlung: -3,
    geplanteRueckkehr: null,
    phasen: [PHASE_LOHNFORTZAHLUNG, PHASE_WIEDEREINGLIEDERUNG],
};

// Befund 3: eine abgeschlossene Meldung ohne aktuelle Phase (Befund 4:
// aktuellePhaseTyp null statt eines PhasenTyp-Werts) -- genau der Fall, fuer
// den "Doch noch krank" erscheinen muss.
const MELDUNG_BEENDET = {
    ...MELDUNG_KURZ,
    id: 3,
    mitarbeiterId: 12,
    mitarbeiterName: 'Beispiel, Klaus',
    status: 'BEENDET',
    statusLabel: 'Abgeschlossen',
    version: 5,
    aktuellePhaseTyp: null,
    aktuellePhaseLabel: 'Keine aktuelle Phase',
    geplanteRueckkehr: null,
    phasen: [],
};

async function stubApi(page: import('@playwright/test').Page) {
    await page.route('**/api/auth/me', (route) => json(route, AUTH_ME));
    await page.route('**/api/notifications/summary', (route) => json(route, NOTIFICATIONS_LEER));
    await page.route('**/api/mitarbeiter', (route) =>
        json(route, [{ id: 10, vorname: 'Max', nachname: 'Mustermann', aktiv: true }]));
    await page.route('**/api/zeitverwaltung/zeitkonten', (route) => json(route, []));
    await page.route('**/api/langzeitkrankmeldungen/1', (route) =>
        json(route, { ...MELDUNG_KURZ, stufenplanTage: [] }));
    await page.route('**/api/langzeitkrankmeldungen/2', (route) =>
        json(route, { ...MELDUNG_LANGER_NAME, stufenplanTage: [] }));
    await page.route('**/api/langzeitkrankmeldungen/3', (route) =>
        json(route, { ...MELDUNG_BEENDET, stufenplanTage: [] }));
    await page.route('**/api/langzeitkrankmeldungen?status=**', (route) =>
        json(route, [MELDUNG_KURZ, MELDUNG_LANGER_NAME, MELDUNG_BEENDET]));
}

test('Lange Krankheit ueber das Menue erreichen, Karte aufklappen und die Zeitleiste sehen', async ({ page }, testInfo) => {
    await stubApi(page);
    await page.goto('/projekte');

    // Auffindbarkeit: ueber das Menue erreichen, nicht direkt per URL
    // (Task 15, Design-Frage 5).
    await page.getByRole('button', { name: 'Zeiterfassung', exact: true }).click();
    await page.getByRole('link', { name: 'Lange Krankheit' }).click();

    await expect(page.getByRole('heading', { name: 'LANGE KRANKHEIT' })).toBeVisible();
    await expect(page.getByText('Mustermann, Max')).toBeVisible();
    // Der 42-Tage-Hinweis in beiden Varianten: Rest-Tage und ueberfaellig.
    await expect(page.getByText('Noch 12 Tage Lohnfortzahlung')).toBeVisible();
    await expect(page.getByText(/Lohnfortzahlung endete am 14\.03\.2026/)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Auf Krankengeld umstellen' })).toBeVisible();

    // Karte mit dem langen Nachnamen aufklappen -> Zeitleiste sichtbar.
    const karte = page.locator('[data-meldung-id="2"]');
    await karte.getByRole('button', { name: 'Details anzeigen' }).click();
    await expect(karte.getByText('Verlauf')).toBeVisible();
    await expect(karte.getByText('Läuft gerade')).toBeVisible();
    // Das Badge steht sowohl im Kartenkopf als auch in der Zeitleiste --
    // gezielt auf den Zeitleisten-Eintrag pruefen (Vorbild aus Task 14:
    // data-testid="phase-<id>" in PhasenZeitleiste.tsx).
    await expect(karte.getByTestId('phase-100').getByText('Lohnfortzahlung durch den Betrieb')).toBeVisible();
    // Befund 1 (Nachbesserung Abschnitt 5, BLOCKER): 4,5 Std. mit deutschem
    // Komma, nicht "4.5 Std." mit englischem Punkt.
    await expect(karte.getByTestId('phase-101').getByText('4,5 Std. pro Tag')).toBeVisible();

    // Umbruch-Sicherheitsnetz: der lange Nachname bleibt in seinem Kasten.
    // scrollWidth/clientWidth, nicht boundingBox() (kriterien.md:
    // boundingBox() ist kein Ueberlauf-Mass fuer ein Blockelement).
    const name = karte.getByText(`${LANGER_NACHNAME}, Erika`, { exact: true });
    const masse = await name.evaluate((el) => ({ scrollWidth: el.scrollWidth, clientWidth: el.clientWidth }));
    expect(masse.scrollWidth, 'Nachname darf nicht ueber seinen Kasten hinauslaufen').toBeLessThanOrEqual(
        masse.clientWidth + 1,
    );

    // Befund 2 (Nachbesserung Abschnitt 5, BLOCKER): Design-Pruefung mit
    // Screenshot -- Vorbild e2e/anfrage-layout.spec.ts:243/:381. Deckt
    // zusaetzlich zum Umbruch-Sicherheitsnetz oben ab: kein horizontaler
    // Ueberlauf, keine ueberlappenden interaktiven Elemente, Primaeraktion
    // ("Krankmeldung anlegen") ohne Scrollen sichtbar, kein gekuerzter Text.
    await designPruefung(page, testInfo, 'lange-krankheit-details', {
        primaerAktion: page.getByRole('button', { name: 'Krankmeldung anlegen' }),
        strengePruefungen: true,
    });
});

// Befund 3 (Nachbesserung Abschnitt 5, BLOCKER-nah): sobald status !==
// 'LAUFEND' zeigte die Karte gar keinen Knopf mehr -- ein Fehlklick auf
// "Wieder voll im Einsatz" liess sich dann nur noch ueber eine neue Meldung
// mit spaeterem Beginn umgehen (verfaelscht den 42-Tage-Zaehler der
// Lohnfortzahlung). "Doch noch krank" ruft PUT .../{id}/oeffnen -- dieser
// Test deckt: Knopf NICHT bei LAUFEND, Knopf BEI BEENDET, Befund 4 (kein
// kaputtes Badge ohne aktuelle Phase), und dass ein Versionskonflikt (409)
// denselben Konfliktdialog wie ueberall sonst ausloest (useKonfliktMeldung).
test('"Doch noch krank" erscheint nur bei einer abgeschlossenen Meldung, ein Versionskonflikt zeigt den bestehenden Dialog', async ({ page }, testInfo) => {
    await stubApi(page);
    await page.route('**/api/langzeitkrankmeldungen/3/oeffnen**', (route) => {
        if (route.request().method() !== 'PUT') return route.fallback();
        return json(route, { error: 'Jemand anders hat diese Krankmeldung gerade gespeichert.' }, 409);
    });

    await page.goto('/projekte');
    await page.getByRole('button', { name: 'Zeiterfassung', exact: true }).click();
    await page.getByRole('link', { name: 'Lange Krankheit' }).click();
    await expect(page.getByRole('heading', { name: 'LANGE KRANKHEIT' })).toBeVisible();

    // Laufende Meldung (id=1): kein "Doch noch krank".
    const karteLaufend = page.locator('[data-meldung-id="1"]');
    await karteLaufend.getByRole('button', { name: 'Details anzeigen' }).click();
    await expect(karteLaufend.getByText('Verlauf')).toBeVisible();
    await expect(karteLaufend.getByRole('button', { name: 'Doch noch krank' })).toHaveCount(0);

    // Abgeschlossene Meldung (id=3, status BEENDET): Knopf erscheint.
    const karteBeendet = page.locator('[data-meldung-id="3"]');
    await karteBeendet.getByRole('button', { name: 'Details anzeigen' }).click();
    const dochNochKrank = karteBeendet.getByRole('button', { name: 'Doch noch krank' });
    await expect(dochNochKrank).toBeVisible();
    // Befund 4: aktuellePhaseTyp null (keine aktuelle Phase) darf kein
    // kaputtes ("undefined") Badge zeigen, sondern einen sauberen Fallback.
    await expect(karteBeendet.getByText('Keine aktuelle Phase')).toBeVisible();

    await designPruefung(page, testInfo, 'lange-krankheit-beendet', { primaerAktion: dochNochKrank });

    await dochNochKrank.click();

    // Optimistisches Sperren gilt hier wie ueberall: 409 muss den
    // bestehenden Konfliktdialog ausloesen, kein eigenes Fehlerbild.
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading', { name: 'Nicht gespeichert' })).toBeVisible();
    await dialog.getByRole('button', { name: 'Abbrechen' }).click();
    await expect(dialog).toHaveCount(0);

    await keinHorizontalerUeberlauf(page);
});
