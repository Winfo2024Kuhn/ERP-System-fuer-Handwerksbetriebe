import type { Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { keinHorizontalerUeberlauf } from './hilfen/design';

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
    bisDatum: null,
    stundenProTag: null,
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
};

async function stubApi(page: import('@playwright/test').Page) {
    await page.route('**/api/auth/me', (route) => json(route, AUTH_ME));
    await page.route('**/api/notifications/summary', (route) => json(route, NOTIFICATIONS_LEER));
    await page.route('**/api/mitarbeiter', (route) =>
        json(route, [{ id: 10, vorname: 'Max', nachname: 'Mustermann', aktiv: true }]));
    await page.route('**/api/zeitverwaltung/zeitkonten', (route) => json(route, []));
    await page.route('**/api/langzeitkrankmeldungen/2', (route) =>
        json(route, { ...MELDUNG_LANGER_NAME, stufenplanTage: [] }));
    await page.route('**/api/langzeitkrankmeldungen?status=**', (route) =>
        json(route, [MELDUNG_KURZ, MELDUNG_LANGER_NAME]));
}

test('Lange Krankheit ueber das Menue erreichen, Karte aufklappen und die Zeitleiste sehen', async ({ page }) => {
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

    // Umbruch-Sicherheitsnetz: der lange Nachname bleibt in seinem Kasten.
    // scrollWidth/clientWidth, nicht boundingBox() (kriterien.md:
    // boundingBox() ist kein Ueberlauf-Mass fuer ein Blockelement).
    const name = karte.getByText(`${LANGER_NACHNAME}, Erika`, { exact: true });
    const masse = await name.evaluate((el) => ({ scrollWidth: el.scrollWidth, clientWidth: el.clientWidth }));
    expect(masse.scrollWidth, 'Nachname darf nicht ueber seinen Kasten hinauslaufen').toBeLessThanOrEqual(
        masse.clientWidth + 1,
    );

    await keinHorizontalerUeberlauf(page);
});
