import fs from 'node:fs';
import path from 'node:path';
import type { Page, Route, TestInfo } from '@playwright/test';
import { test, expect } from './hilfen/test';
import {
    designPruefung, keinHorizontalerUeberlauf, keinTextGekuerzt, keinTextLaeuftUeber, uebergaengeAusklingenLassen,
} from './hilfen/design';
import { stubbeEmailCenter } from './hilfen/postfaecher';
import { oeffneNeuigkeiten, stubbeWebsiteApi } from './hilfen/api';

/**
 * KI ist freiwillig: Ohne hinterlegten Gemini-Schlüssel bleiben die
 * KI-Funktionen aus, alles andere läuft. Die Ersteinrichtung verlangt nur
 * noch den E-Mail-Server.
 *
 * Kein Backend: alle /api-Routen sind gestubbt. Nur Dummy-Daten (DSGVO).
 */

const KI_AUS_ERKLAERUNG = 'KI-Funktionen sind aus, weil kein KI-Schlüssel hinterlegt ist. '
    + 'Ein Administrator kann ihn unter Einstellungen → KI-Funktionen eintragen.';

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

/**
 * Design-Pruefung fuer den Beitrags-Assistenten: Screenshot in denselben
 * Ordner wie designPruefung() plus alle Pruefungen ausser
 * keineUeberschneidungen. Der Assistent ist eine feste Vollbild-Ebene ohne
 * role="dialog"; die Pruefung kennt keine Verdeckung und meldet sonst sein
 * Schliessen-X gegen den darunter liegenden, verdeckten Einklapp-Pfeil der
 * Menueleiste (beide oben rechts). Das ist unabhaengig von der KI so.
 */
async function assistentPruefung(page: Page, testInfo: TestInfo, name: string) {
    await uebergaengeAusklingenLassen(page);
    const zielOrdner = path.join(testInfo.project.outputDir, 'design');
    fs.mkdirSync(zielOrdner, { recursive: true });
    const pfad = path.join(zielOrdner, `${name}--${testInfo.project.name}.png`);
    await page.screenshot({ path: pfad });
    await testInfo.attach(`design: ${name}`, { path: pfad, contentType: 'image/png' });
    await keinHorizontalerUeberlauf(page);
    await keinTextLaeuftUeber(page);
    await keinTextGekuerzt(page);
}

interface Zustand {
    /** Was das Backend als `requiresInitialSetup` meldet (Admin + Mailserver unvollständig). */
    einrichtungOffen: boolean;
    geminiAbfragen: number;
}

/** Admin ohne KI-Schlüssel. Landeseite /projekte, Einstellungen und Ersteinrichtung sind gestubbt. */
async function stubbeAdminOhneKi(page: Page, einrichtungOffen = false): Promise<Zustand> {
    const zustand: Zustand = { einrichtungOffen, geminiAbfragen: 0 };

    // Auffangnetz zuerst: Playwright fragt die zuletzt registrierte Route zuerst.
    await page.route('**/api/**', route => json(route, {}));

    await page.route('**/api/auth/me', route => json(route, {
        id: 1, displayName: 'Erika Mustermann', username: 'erika', active: true,
        roles: ['ADMIN'], admin: true,
        requiresInitialSetup: zustand.einrichtungOffen,
        kiAktiv: false,
    }));
    await page.route('**/api/notifications/summary**', route => json(route, { totalCount: 0, categories: [], recentItems: [] }));
    await page.route('**/api/last-accessed/PROJEKT**', route => {
        if (route.request().method() === 'POST') return route.fulfill({ status: 200, body: '' });
        return json(route, {});
    });
    await page.route('**/api/projekte**', route => {
        const pfad = new URL(route.request().url()).pathname;
        if (pfad === '/api/projekte') return json(route, { projekte: [], gesamt: 0 });
        if (pfad === '/api/projekte/jahre') return json(route, []);
        return json(route, {});
    });
    await page.route('**/api/settings/smtp', route => json(route, {
        host: 'mail.musterbetrieb.example', port: 465, username: 'info@musterbetrieb.example', passwordSet: true,
    }));
    await page.route('**/api/settings/gemini', route => {
        zustand.geminiAbfragen += 1;
        return json(route, { apiKeySet: false });
    });
    await page.route('**/api/settings/anfrage-funnel-spamfilter', route => json(route, { aktiv: true }));
    await page.route('**/api/postfaecher', route => json(route, [{
        id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb', aktiv: true, sortierung: 0,
        hauptpostfach: true, fuerGeschaeftsdokumente: false, benutzername: 'info@musterbetrieb.example', passwortGesetzt: true,
        smtpHost: 'mail.musterbetrieb.example', smtpPort: 465, imapHost: 'mail.musterbetrieb.example', imapPort: 993,
        abrufAktiv: true, letzterAbrufAm: null, letzterAbrufFehler: null, zugewieseneBenutzer: [],
    }]));
    await page.route('**/api/telefon/berechtigung', route => json(route, { darfTelefonSehen: false }));
    return zustand;
}

test('Admin ohne KI-Schlüssel arbeitet normal – ohne Umleitung und ohne KI-Hilfe in der Menüleiste', async ({ page }, info) => {
    await stubbeAdminOhneKi(page);
    await page.goto('/projekte');

    await expect(page.getByRole('heading', { name: 'Projektübersicht' })).toBeVisible();
    await expect(page).toHaveURL(/\/projekte$/);
    // Erst wenn der Name in der Leiste steht, ist die Anmeldung geladen.
    await expect(page.getByText('Erika Mustermann').first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'KI-Hilfe' })).toHaveCount(0);
    await designPruefung(page, info, 'ki-optional-projekte-ohne-ki');
});

test('Einstellungen → KI-Funktionen sagt klar „KI-Funktionen sind aus“ und erklärt die Folgen', async ({ page }, info) => {
    await stubbeAdminOhneKi(page);
    await page.goto('/einstellungen#ki');

    await expect(page.getByRole('tab', { name: /KI-Funktionen/ })).toHaveAttribute('aria-selected', 'true');
    const status = page.getByRole('region', { name: 'KI-Funktionen sind aus' });
    await expect(status).toBeVisible();
    await expect(status).toContainText('Das Programm funktioniert trotzdem vollständig');
    await expect(status).toContainText('Spracheingabe in der Handy-App');
    await expect(page.getByText('Freiwillig.')).toBeVisible();
    await designPruefung(page, info, 'ki-optional-einstellungen-aus', { ganzeSeite: true });

    const infoKnopf = page.getByRole('button', { name: 'Mehr zu: KI-Funktionen' });
    await infoKnopf.click();
    const erklaerung = page.getByRole('dialog', { name: 'KI-Funktionen' });
    await expect(erklaerung).toContainText('Was passiert im Hintergrund?');
    await expect(erklaerung).toContainText('Ohne Schlüssel verlässt nichts davon den Betrieb.');
    await designPruefung(page, info, 'ki-optional-einstellungen-info');
    await page.keyboard.press('Escape');
    await expect(erklaerung).toHaveCount(0);
    await expect(infoKnopf).toBeFocused();
});

test('Ersteinrichtung verlangt nur den E-Mail-Server und schließt ohne KI-Schlüssel ab', async ({ page }, info) => {
    const zustand = await stubbeAdminOhneKi(page, true);
    await page.goto('/projekte');

    // Mailserver fehlt laut Backend → Umleitung zur Ersteinrichtung.
    await expect(page).toHaveURL(/\/onboarding$/);
    await expect(page.getByRole('heading', { name: 'SYSTEM EINRICHTEN' })).toBeVisible();
    await expect(page.getByText(/KI-Funktionen sind freiwillig/)).toBeVisible();
    const abschliessen = page.getByRole('button', { name: /Einrichtung abschließen/ });
    await designPruefung(page, info, 'ki-optional-ersteinrichtung');

    // Der Admin hat den E-Mail-Server inzwischen gespeichert – das Backend meldet die Einrichtung als fertig.
    zustand.einrichtungOffen = false;
    const geminiVorher = zustand.geminiAbfragen;
    await abschliessen.click();

    await expect(page).toHaveURL(/\/projekte$/);
    await expect(page.getByText('Ersteinrichtung abgeschlossen.')).toBeVisible();
    // Der KI-Schlüssel spielt für den Abschluss keine Rolle mehr.
    expect(zustand.geminiAbfragen).toBe(geminiVorher);
});

test('Beim E-Mail-Schreiben ist „KI-Optimierung“ ohne KI-Schlüssel gesperrt und erklärt warum', async ({ page }, info) => {
    await stubbeEmailCenter(page, { kiAktiv: false });
    await page.goto('/emails/inbox');
    await page.getByRole('button', { name: 'Neue E-Mail' }).click();

    const kiKnopf = page.getByRole('button', { name: /KI-Optimierung/ });
    await expect(kiKnopf).toBeDisabled();
    await expect(kiKnopf).toHaveAccessibleDescription(KI_AUS_ERKLAERUNG);
    // Tooltip an der Hülle – ein gesperrter Knopf bekommt nicht in jedem Browser ein Hover.
    const huelle = page.locator('span[title]', { has: kiKnopf });
    await expect(huelle).toHaveAttribute('title', KI_AUS_ERKLAERUNG);
    await huelle.hover();
    await designPruefung(page, info, 'ki-optional-email-ki-gesperrt');
});

test('Mit KI-Schlüssel bleibt „KI-Optimierung“ klickbar', async ({ page }) => {
    await stubbeEmailCenter(page);
    await page.goto('/emails/inbox');
    await page.getByRole('button', { name: 'Neue E-Mail' }).click();

    await expect(page.getByRole('button', { name: /KI-Optimierung/ })).toBeEnabled();
    await expect(page.getByRole('button', { name: 'KI-Hilfe' })).toBeVisible();
});

test('Webseiten-Beitrag ohne KI-Schlüssel: KI-Weg gesperrt mit Grund, Textschritt ohne leere Chat-Spalte', async ({ page }, info) => {
    await stubbeWebsiteApi(page);
    // Spaeter registriert, gewinnt also: derselbe Admin, aber ohne KI-Schluessel.
    await page.route('**/api/auth/me', route => json(route, {
        id: 1, username: 'admin', displayName: 'Max Mustermann', active: true,
        admin: true, roles: ['ADMIN'], requiresInitialSetup: false, kiAktiv: false,
    }));
    await oeffneNeuigkeiten(page);

    await page.getByRole('button', { name: 'Neuer Beitrag' }).click();
    await page.getByRole('button', { name: /Balkonanlage Musterstraße/ }).click();
    await page.getByRole('img', { name: 'balkon-vorher.jpg' }).click();
    await page.getByRole('button', { name: 'Weiter' }).click();

    const kiWeg = page.getByRole('button', { name: 'Von der KI vorschlagen lassen' });
    await expect(kiWeg).toBeDisabled();
    await expect(kiWeg).toHaveAccessibleDescription(KI_AUS_ERKLAERUNG);
    await expect(kiWeg.getByText(KI_AUS_ERKLAERUNG)).toBeVisible();
    const selbst = page.getByRole('button', { name: 'Selbst schreiben' });
    await expect(selbst).not.toContainText('Die KI kannst du danach trotzdem noch fragen.');
    await expect(selbst).toBeInViewport();
    await assistentPruefung(page, info, 'ki-optional-beitrag-weg');

    await selbst.click();
    await expect(page.getByLabel('Titel')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'KI-Hilfe' })).toHaveCount(0);
    await expect(page.getByText(KI_AUS_ERKLAERUNG)).toBeVisible();
    await assistentPruefung(page, info, 'ki-optional-beitrag-text');
});

test('Einstellungen → KI-Funktionen: „Schlüssel entfernen“ fragt nach und schaltet die KI aus', async ({ page }, info) => {
    await stubbeAdminOhneKi(page);
    // Nach stubbeAdminOhneKi registriert, damit diese Routen zuerst greifen: Schlüssel ist hinterlegt.
    let schluesselDa = true;
    const loeschungen: string[] = [];
    await page.route('**/api/settings/gemini', route => {
        if (route.request().method() === 'DELETE') {
            loeschungen.push(route.request().url());
            schluesselDa = false;
            return json(route, { message: 'KI-Schlüssel entfernt.' });
        }
        return json(route, { apiKeySet: schluesselDa });
    });
    await page.route('**/api/auth/me', route => json(route, {
        id: 1, displayName: 'Erika Mustermann', username: 'erika', active: true,
        roles: ['ADMIN'], admin: true, requiresInitialSetup: false, kiAktiv: schluesselDa,
    }));
    await page.goto('/einstellungen#ki');

    await expect(page.getByRole('region', { name: 'KI-Funktionen sind an' })).toBeVisible();
    const entfernen = page.getByRole('button', { name: 'Schlüssel entfernen', exact: true });
    await entfernen.click();
    const rueckfrage = page.getByRole('dialog', { name: 'KI-Schlüssel entfernen?' });
    await expect(rueckfrage).toContainText('Schon ausgelesene Daten bleiben erhalten.');
    await designPruefung(page, info, 'ki-schluessel-entfernen-rueckfrage');

    const loeschung = page.waitForRequest(req => req.method() === 'DELETE' && new URL(req.url()).pathname === '/api/settings/gemini');
    await rueckfrage.getByRole('button', { name: 'Schlüssel entfernen', exact: true }).click();
    await loeschung;

    await expect(page.getByRole('region', { name: 'Meldungen' }).getByText('KI-Schlüssel entfernt.')).toBeVisible();
    await expect(page.getByRole('region', { name: 'KI-Funktionen sind aus' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Schlüssel entfernen', exact: true })).toHaveCount(0);
    expect(loeschungen).toHaveLength(1);
});
