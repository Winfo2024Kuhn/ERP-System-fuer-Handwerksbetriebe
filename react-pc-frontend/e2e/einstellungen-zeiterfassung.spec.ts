import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { stubbeZeiterfassungSichtbarkeit } from './hilfen/api';
import { designPruefung } from './hilfen/design';
import type { Konfiguration } from '../src/features/monatsabschluss/types';

/**
 * Einstellungen -> Zeiterfassung: Start der Zeiterfassung und die aus dem
 * Monatsabschluss umgezogenen DATEV-Einstellungen (inkl. Soll/Ist).
 * Kein Backend: alle /api-Routen sind gestubbt. Nur Dummy-Daten (DSGVO).
 */

const KATEGORIEN = ['ARBEIT', 'FEIERTAG', 'URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH', 'KRANKENGELD', 'WIEDEREINGLIEDERUNG'];
const LEER: Konfiguration = {
    version: 3, ziel: 'LODAS', beraterNr: '', mandantenNr: '', stundenart: 'IST',
    zuordnungen: KATEGORIEN.map(kategorie => ({ kategorie, ausgeschlossen: false, lohnart: '' })),
    personalnummern: [],
};

const NULL_KENNZAHLEN = { istStunden: 0, sollStunden: 0, abwesenheitsStunden: 0, feiertagsStunden: 0, korrekturStunden: 0, gesamtIst: 0, differenz: 0, urlaubStunden: 0, krankheitStunden: 0, zeitausgleichStunden: 0, sonstigeAbwesenheitStunden: 0 };

interface Mitschrift {
    startPuts: { eingefuehrtAm: string | null }[];
    datevPuts: Konfiguration[];
    datevGets: number;
}

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubbe(page: Page, optionen: { start: string | null; config: Konfiguration; konfliktBeimErstenSpeichern?: boolean }): Promise<Mitschrift> {
    const mitschrift: Mitschrift = { startPuts: [], datevPuts: [], datevGets: 0 };
    let start = optionen.start;
    let config = optionen.config;
    await page.route('**/api/**', async (route) => {
        const anfrage = route.request();
        const pfad = new URL(anfrage.url()).pathname;
        const schreibt = anfrage.method() === 'PUT';
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, displayName: 'Max Mustermann', username: 'max', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/settings/zeiterfassung') {
            if (schreibt) {
                const body = anfrage.postDataJSON() as { eingefuehrtAm: string | null };
                mitschrift.startPuts.push(body);
                start = body.eingefuehrtAm;
            }
            return json(route, { eingefuehrtAm: start });
        }
        if (pfad === '/api/settings/datev') {
            if (!schreibt) {
                mitschrift.datevGets++;
                return json(route, config);
            }
            const body = anfrage.postDataJSON() as Konfiguration;
            mitschrift.datevPuts.push(body);
            if (optionen.konfliktBeimErstenSpeichern && mitschrift.datevPuts.length === 1) {
                config = { ...config, version: (config.version ?? 0) + 1 };
                return json(route, { message: 'Die DATEV-Einstellungen wurden geändert. Bitte neu laden.' }, 409);
            }
            config = { ...body, version: (config.version ?? 0) + 1 };
            return json(route, config);
        }
        if (pfad === '/api/mitarbeiter') {
            return json(route, [
                { id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true },
                { id: 2, vorname: 'Erika', nachname: 'Mustermann', aktiv: true },
                { id: 3, vorname: 'Moritz', nachname: 'Muster', aktiv: false },
            ]);
        }
        // Für den Weg vom Monatsabschluss in die Einstellungen.
        if (pfad.endsWith('/monatsabschluesse/berechtigung')) return json(route, { darfMonatAbschliessen: true });
        if (pfad.endsWith('/monatsabschluesse/uebersicht')) return json(route, { items: [], totalElements: 0, page: 0, size: 50, summen: NULL_KENNZAHLEN, auswahl: [] });
        if (pfad.endsWith('/monatsabschluesse/jahresvergleich')) return json(route, { jahr: 2026, aktuellesJahr: [], vorjahrDaten: [] });
        if (pfad.startsWith('/api/settings/')) return json(route, {});
        return json(route, []);
    });
    return mitschrift;
}

test('Start der Zeiterfassung: Datum wählen, speichern und wieder entfernen', async ({ page }, info) => {
    const mitschrift = await stubbe(page, { start: null, config: LEER });
    await page.goto('/einstellungen#zeiterfassung');

    await expect(page.getByRole('tab', { name: 'Zeiterfassung' })).toHaveAttribute('aria-selected', 'true');
    // Die Karte ist der innerste Kasten, der Überschrift UND Datumsfeld enthält.
    const karte = page.locator('div')
        .filter({ has: page.getByRole('heading', { name: 'Start der Zeiterfassung' }) })
        .filter({ has: page.getByRole('button', { name: 'Zeiterfassung eingeführt am' }) })
        .last();
    await expect(karte.getByText('Noch kein Datum gespeichert – alle Monate mit Zeiten müssen abgeschlossen werden.')).toBeVisible();
    const feld = page.getByRole('button', { name: 'Zeiterfassung eingeführt am' });
    const speichern = page.getByRole('button', { name: /Start speichern/ });
    await expect(speichern).toBeDisabled();
    await designPruefung(page, info, 'einstellungen-zeiterfassung-oben', { primaerAktion: feld });

    // Info-Knopf direkt an der Überschrift: Klick öffnet, Escape schließt und gibt den Fokus zurück.
    const startInfo = page.getByRole('button', { name: 'Mehr zu: Start der Zeiterfassung' });
    await startInfo.click();
    const startErklaerung = page.getByRole('dialog', { name: 'Start der Zeiterfassung' });
    await expect(startErklaerung).toContainText('frühestens im Monat dieses Tages');
    await designPruefung(page, info, 'einstellungen-zeiterfassung-info');
    await page.keyboard.press('Escape');
    await expect(startErklaerung).toHaveCount(0);
    await expect(startInfo).toBeFocused();

    await feld.click();
    const kalender = page.getByRole('dialog', { name: 'Zeiterfassung eingeführt am auswählen' });
    await kalender.getByLabel('Datum eingeben').fill('01.03.2025');
    await designPruefung(page, info, 'einstellungen-zeiterfassung-kalender');
    await kalender.getByRole('button', { name: 'Übernehmen' }).click();
    await expect(feld).toHaveText('01.03.2025');

    await speichern.click();
    await expect(page.getByText('Start der Zeiterfassung gespeichert.')).toBeVisible();
    await expect(karte.getByText('Gespeichert: Zeiterfassung seit 01.03.2025. Monate vor März 2025 müssen nicht abgeschlossen werden.')).toBeVisible();
    expect(mitschrift.startPuts).toEqual([{ eingefuehrtAm: '2025-03-01' }]);
    await expect(speichern).toBeDisabled();
    await designPruefung(page, info, 'einstellungen-zeiterfassung-start-gespeichert', { primaerAktion: feld });

    await page.getByRole('button', { name: /Datum entfernen/ }).click();
    await expect(feld).toHaveText('Kein Datum – keine Grenze');
    await speichern.click();
    await expect(page.getByText('Datum entfernt – es gibt keine Grenze mehr.')).toBeVisible();
    expect(mitschrift.startPuts).toEqual([{ eingefuehrtAm: '2025-03-01' }, { eingefuehrtAm: null }]);
});

test('DATEV einrichten: Nummern, Soll/Ist für Betrieb und Mitarbeiter, Lohnarten und Versionskonflikt', async ({ page }, info) => {
    const mitschrift = await stubbe(page, { start: '2025-03-01', config: LEER, konfliktBeimErstenSpeichern: true });
    await page.goto('/einstellungen#zeiterfassung');

    const berater = page.getByRole('textbox', { name: 'Beraternummer' });
    await expect(berater).toBeEmpty();
    await expect(page.getByRole('radio', { name: /Ist-Stunden \(tatsächlich gearbeitet\)/ })).toBeChecked();
    await expect(page.getByRole('note')).toHaveCount(0);

    await berater.fill('001234');
    await page.getByRole('textbox', { name: 'Mandantennummer' }).fill('0012');
    await page.getByRole('radio', { name: /Soll-Stunden \(laut Arbeitszeitkonto\)/ }).check();
    await expect(page.getByRole('note')).toContainText('nichts wird doppelt gezählt');
    await page.getByRole('heading', { name: 'Stunden ans Steuerbüro & DATEV' }).evaluate(el => el.scrollIntoView({ block: 'start' }));
    await designPruefung(page, info, 'einstellungen-datev-oben', { primaerAktion: berater });

    // Erklärung zu Soll/Ist direkt an der Frage; Klick daneben schließt.
    await page.getByRole('button', { name: 'Mehr zu: Stunden ans Steuerbüro' }).first().click();
    const stundenInfo = page.getByRole('dialog', { name: 'Stunden ans Steuerbüro' });
    await expect(stundenInfo).toContainText('nichts wird doppelt gezählt');
    await designPruefung(page, info, 'einstellungen-datev-info-stunden');
    await page.getByRole('heading', { name: 'DATEV-Zugang' }).click();
    await expect(stundenInfo).toHaveCount(0);

    for (const label of ['Arbeit', 'Feiertage', 'Urlaub', 'Krankheit', 'Fortbildung', 'Zeitausgleich', 'Krankengeld', 'Wiedereingliederung']) {
        await page.getByRole('combobox', { name: `Export für ${label}`, exact: true }).click();
        if (label === 'Arbeit') {
            await page.getByRole('option', { name: 'Lohnart zuordnen', exact: true }).click();
            await page.getByRole('textbox', { name: 'Lohnart für Arbeit', exact: true }).fill('0200');
        } else {
            await page.getByRole('option', { name: 'Nicht exportieren', exact: true }).click();
        }
    }

    // Info in der Tabellenspalte: läuft rechts nicht aus dem Fenster.
    await page.getByRole('button', { name: 'Mehr zu: Personalnummer' }).click();
    await expect(page.getByRole('dialog', { name: 'Personalnummer' })).toContainText('keine eigene Soll/Ist-Auswahl');
    await designPruefung(page, info, 'einstellungen-datev-info-personalnummer');
    await page.keyboard.press('Escape');

    const erikaStunden = page.getByRole('combobox', { name: 'Stunden ans Steuerbüro für Erika Mustermann' });
    await expect(erikaStunden).toBeDisabled();
    await page.getByRole('textbox', { name: 'Personalnummer für Max Mustermann' }).fill('00014');
    const maxStunden = page.getByRole('combobox', { name: 'Stunden ans Steuerbüro für Max Mustermann' });
    await expect(maxStunden).toHaveText('Wie Betrieb (Soll-Stunden)');
    await maxStunden.click();
    await designPruefung(page, info, 'einstellungen-datev-stundenauswahl');
    await page.getByRole('option', { name: 'Ist-Stunden', exact: true }).click();
    await expect(page.getByText('Es gibt ungespeicherte Änderungen.')).toBeVisible();

    const speichern = page.getByRole('button', { name: /DATEV-Einstellungen speichern/ });
    await speichern.scrollIntoViewIfNeeded();
    await designPruefung(page, info, 'einstellungen-datev-formular', { primaerAktion: speichern });
    await page.screenshot({ path: info.outputPath('einstellungen-datev-ganz.png'), fullPage: true });

    // Erster Versuch: jemand anderes hat inzwischen gespeichert.
    await speichern.click();
    // Toast und Meldung in der Karte tragen denselben Text; die Karte bietet zusätzlich „Neu laden“.
    const alarm = page.getByRole('alert').filter({ hasText: 'Die DATEV-Einstellungen wurden geändert. Bitte neu laden.' })
        .filter({ has: page.getByRole('button', { name: /Neu laden/ }) });
    await expect(alarm).toBeVisible();
    await expect(page.getByTestId('toast-container')).toContainText('Die DATEV-Einstellungen wurden geändert. Bitte neu laden.');
    await expect(berater).toHaveValue('001234');
    await designPruefung(page, info, 'einstellungen-datev-konflikt');
    // Der Entwurf wurde bis hierher nicht still überschrieben – neu geladen wird erst auf Klick.
    const getsVorher = mitschrift.datevGets;
    await alarm.getByRole('button', { name: /Neu laden/ }).click();
    await expect(berater).toBeEmpty();
    expect(mitschrift.datevGets).toBe(getsVorher + 1);

    // Zweiter Anlauf mit frischem Stand.
    await berater.fill('001234');
    await page.getByRole('textbox', { name: 'Mandantennummer' }).fill('0012');
    await page.getByRole('radio', { name: /Soll-Stunden \(laut Arbeitszeitkonto\)/ }).check();
    await page.getByRole('textbox', { name: 'Personalnummer für Max Mustermann' }).fill('00014');
    await maxStunden.click();
    await page.getByRole('option', { name: 'Ist-Stunden', exact: true }).click();
    await speichern.click();
    await expect(page.getByText('DATEV-Einstellungen gespeichert.')).toBeVisible();
    await expect(page.getByText('Alle Änderungen sind gespeichert.')).toBeVisible();

    const gespeichert = mitschrift.datevPuts.at(-1)!;
    expect(gespeichert.version).toBe(4);
    expect(gespeichert).toMatchObject({ ziel: 'LODAS', beraterNr: '001234', mandantenNr: '0012', stundenart: 'SOLL' });
    expect(gespeichert.personalnummern).toEqual([{ mitarbeiterId: 1, personalnummer: '00014', stundenart: 'IST' }]);
    expect(mitschrift.datevPuts[0].zuordnungen.find(z => z.kategorie === 'ARBEIT')).toEqual({ kategorie: 'ARBEIT', ausgeschlossen: false, lohnart: '0200' });
    expect(mitschrift.datevPuts[0].zuordnungen.filter(z => z.ausgeschlossen)).toHaveLength(7);
});

test('Monatsabschluss führt über „DATEV einrichten“ direkt in diesen Reiter', async ({ page }) => {
    await stubbe(page, { start: null, config: LEER });
    await stubbeZeiterfassungSichtbarkeit(page);
    await page.goto('/monatsabschluss');
    await page.getByRole('link', { name: 'DATEV einrichten', exact: true }).click();
    await expect(page).toHaveURL(/\/einstellungen#zeiterfassung$/);
    await expect(page.getByRole('tab', { name: 'Zeiterfassung' })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('heading', { name: 'Stunden ans Steuerbüro & DATEV' })).toBeVisible();
});
