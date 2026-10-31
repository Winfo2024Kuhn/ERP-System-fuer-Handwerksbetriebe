import type { Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { geminiAnbieter, mistralAnbieter, stubbeKiEinstellungen } from './hilfen/ki';

/**
 * KI-Anbieter wählen: Der Admin stellt von Google Gemini auf Mistral um,
 * trägt den Mistral-Schlüssel ein und sieht in „Welche Funktion nutzt was?“
 * jederzeit, was über welchen Anbieter läuft – inklusive Ersatz.
 *
 * Kein Backend: alle /api-Routen sind gestubbt. Nur Test-Schlüssel und Dummy-Daten (DSGVO).
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

test('Mistral wählen, Schlüssel eintragen, Modell je Funktion festlegen – die Liste zeigt Läuft und Ersatz', async ({ page }, info) => {
    // Auffangnetz zuerst: Playwright fragt die zuletzt registrierte Route zuerst.
    await page.route('**/api/**', route => json(route, {}));
    await page.route('**/api/auth/me', route => json(route, {
        id: 1, displayName: 'Erika Mustermann', username: 'erika', active: true,
        roles: ['ADMIN'], admin: true, requiresInitialSetup: false, kiAktiv: true,
    }));
    await page.route('**/api/notifications/summary**', route => json(route, { totalCount: 0, categories: [], recentItems: [] }));
    await page.route('**/api/telefon/berechtigung', route => json(route, { darfTelefonSehen: false }));
    await page.route('**/api/settings/anfrage-funnel-spamfilter', route => json(route, { aktiv: true }));
    const ki = await stubbeKiEinstellungen(page, { anbieterListe: [mistralAnbieter(), geminiAnbieter(true)] });

    await page.goto('/einstellungen#ki');
    const gruppe = page.getByRole('radiogroup', { name: 'KI-Anbieter wählen' });
    const mistral = gruppe.getByRole('radio', { name: 'Mistral' });
    await expect(gruppe.getByRole('radio', { name: 'Google Gemini' })).toHaveAttribute('aria-checked', 'true');
    const liste = page.getByRole('list', { name: 'KI-Funktionen' });
    const sprache = liste.getByRole('listitem', { name: 'Spracheingabe in der Handy-App' });
    const dokumente = liste.getByRole('listitem', { name: 'Rechnungen, Lieferscheine und Belege auslesen' });
    await expect(sprache).toContainText('Läuft');

    // 1. Mistral wählen – noch ohne Schlüssel: deutlicher Hinweis, alles läuft über den Ersatz.
    await mistral.click();
    await expect(mistral).toHaveAttribute('aria-checked', 'true');
    await expect(page.getByRole('note')).toContainText('Für Mistral ist noch kein Schlüssel hinterlegt.');
    await expect(dokumente).toContainText('Ersatz');
    await expect(dokumente).toContainText('Google Gemini');
    expect(ki.aenderungen[0]).toEqual({ methode: 'PUT', pfad: '/api/settings/ki', body: { anbieter: 'MISTRAL', ersatzErlaubt: true } });

    // 2. Schlüssel für Mistral eintragen: Dokumente laufen über Mistral, Sprache bleibt beim Ersatz.
    const karte = page.getByTestId('ki-anbieter-MISTRAL');
    await karte.getByLabel('Schlüssel für Mistral', { exact: true }).fill('test-schluessel-123');
    await karte.getByRole('button', { name: 'Schlüssel speichern' }).click();
    await expect(karte).toContainText('Schlüssel hinterlegt');
    await expect(page.getByRole('note')).toHaveCount(0);
    await expect(dokumente).toContainText('Läuft');
    await expect(dokumente).toContainText('mistral-medium-latest');
    await expect(sprache).toContainText('Ersatz');
    await expect(sprache).toContainText('Mistral kann das in diesem Programm noch nicht. Es springt Google Gemini ein.');
    await expect(page.getByRole('region', { name: 'KI-Funktionen sind an' })).toContainText('Alle KI-Funktionen laufen – 1 über Mistral, 2 über einen Ersatz-Anbieter.');

    // 3. Für Rechnungen ein eigenes, gründlicheres Modell festlegen: Ändern → Mistral → Modell suchen.
    await dokumente.getByRole('button', { name: 'Ändern: Rechnungen, Lieferscheine und Belege auslesen' }).click();
    const segmente = dokumente.getByRole('radiogroup', { name: /Anbieter für/ });
    await expect(segmente.getByRole('radio', { name: /Standard \(Mistral\)/ })).toHaveAttribute('aria-checked', 'true');
    await segmente.getByRole('radio', { name: /^Mistral/ }).click();
    const modellWahl = dokumente.getByLabel('Modell von Mistral');
    const modellListe = page.waitForResponse(res => new URL(res.url()).pathname === '/api/settings/ki/modelle');
    await modellWahl.click();
    await modellListe;
    await dokumente.getByRole('combobox').fill('large');
    await expect(dokumente.getByRole('option')).toHaveCount(1);
    await designPruefung(page, info, 'ki-anbieter-funktion-modellsuche');
    await dokumente.getByRole('option', { name: /Mistral Large/ }).click();
    await expect(modellWahl).toContainText('Mistral Large (mistral-large-latest)');
    await dokumente.getByRole('button', { name: 'Übernehmen' }).click();
    await expect(dokumente).toContainText('Eigene Wahl: Mistral · mistral-large-latest');
    await expect(dokumente).toContainText('Läuft');
    expect(ki.aenderungen.at(-1)).toEqual({
        methode: 'PUT', pfad: '/api/settings/ki/funktionen/DOKUMENT_ANALYSE', body: { anbieter: 'MISTRAL', modell: 'mistral-large-latest' },
    });

    // 4. Erweitert: Standard-Modelle aufklappen – ganze Seite prüfen.
    await karte.getByRole('button', { name: 'Erweitert: Standard-Modelle' }).click();
    await expect(karte.getByLabel('Gründlich', { exact: true })).toContainText('Standard (mistral-large-latest)');
    await designPruefung(page, info, 'ki-anbieter-mistral-gewaehlt', { ganzeSeite: true });

    // 5. Info zur Funktionsliste: nichts abgeschnitten, auch weiter unten auf der Seite.
    await page.getByRole('button', { name: 'Mehr zu: Welche Funktion nutzt was?' }).click();
    await expect(page.getByRole('dialog', { name: 'Welche Funktion nutzt was?' })).toContainText('„Ersatz“: Ihr Anbieter kann das noch nicht');
    await designPruefung(page, info, 'ki-anbieter-info-funktionsliste');
});
