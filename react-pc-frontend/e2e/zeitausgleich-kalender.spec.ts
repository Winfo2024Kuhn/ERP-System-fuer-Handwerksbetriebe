import { test, expect } from './hilfen/test';
import { blockiereFremdeNetzwerkzugriffe } from './hilfen/api';
import { designPruefung } from './hilfen/design';

test('Kalender: Zeitausgleich buchen, smarter Buchungsbeginn, Pausenschnitt & Monatsabschluss-Link', async ({ page }, info) => {
    await blockiereFremdeNetzwerkzugriffe(page);

    const apiCalls: Array<{ method: string; path: string; body: unknown }> = [];

    await page.route('**/api/**', async route => {
        const req = route.request();
        const url = new URL(req.url());
        const path = url.pathname;
        const method = req.method();

        let body: unknown = [];

        if (['POST', 'PUT', 'DELETE'].includes(method)) {
            apiCalls.push({ method, path, body: req.postDataJSON() });
        }

        if (path === '/api/auth/me') {
            body = { id: 1, username: 'test', email: 'test@example.com', vorname: 'Max', nachname: 'Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false };
        } else if (path === '/api/mitarbeiter') {
            body = [{ id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true }];
        } else if (path.endsWith('/berechtigung')) {
            body = { darfMonatAbschliessen: true };
        } else if (path === '/api/zeitverwaltung/monatsabschluesse/1/2026/8') {
            body = { mitarbeiterId: 1, jahr: 2026, monat: 8, festgeschrieben: false, version: 1, sollStunden: 8, gesamtIst: 8, differenz: 0, audit: [] };
        } else if (path === '/api/zeitverwaltung/kalender') {
            body = {
                jahr: 2026,
                monat: 8,
                sollStundenMonat: 8,
                istStundenMonat: 8,
                differenz: 0,
                tage: [{
                    datum: '2026-08-03',
                    wochentag: 1,
                    istFeiertag: false,
                    feiertagName: null,
                    sollStunden: 8,
                    istStunden: 8,
                    buchungen: [{
                        id: 1,
                        projektId: 1,
                        projektName: 'Testprojekt',
                        arbeitsgangName: '',
                        startZeit: '08:00:00',
                        endeZeit: '16:00:00',
                        dauerMinuten: 480,
                        dauerFormatiert: '8:00',
                        notiz: ''
                    }]
                }]
            };
        } else if (path.startsWith('/api/zeitkonto/korrekturen/mitarbeiter/')) {
            body = [];
        } else if (path === '/api/abwesenheit') {
            body = { id: 101, mitarbeiterId: 1, typ: 'ZEITAUSGLEICH', datum: '2026-08-03' };
        } else if (path.startsWith('/api/zeitverwaltung/buchungen')) {
            body = { id: 1 };
        }

        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    });

    // 1. Kalenderseite öffnen
    await page.goto('/zeitbuchungen?mitarbeiterId=1&jahr=2026&monat=8');

    // 2. Prüfen, dass "Zum Monatsabschluss" Link vorhanden ist
    const monatsabschlussLink = page.getByRole('link', { name: /Zum Monatsabschluss/i });
    await expect(monatsabschlussLink).toBeVisible();
    await expect(monatsabschlussLink).toHaveAttribute('href', /\/monatsabschluss\?jahr=2026&monat=8&mitarbeiterId=1/);

    // 3. Rechtsklick Context-Menu prüfen: Zeitausgleich ist vorhanden
    const tagZelle = page.getByText('3', { exact: true });
    await tagZelle.click({ button: 'right' });
    const zeitausgleichMenu = page.getByText('Zeitausgleich', { exact: true });
    await expect(zeitausgleichMenu).toBeVisible();

    // Context-Menu mit Escape schließen
    await page.keyboard.press('Escape');
    await expect(zeitausgleichMenu).toBeHidden();

    // 4. Tag doppelt klicken, um DayEditorModal zu öffnen
    await tagZelle.dblclick();
    const modal = page.getByRole('dialog', { name: 'Tageserfassung' });
    await expect(modal).toBeVisible();

    // 5. Schnellauswahl-Buttons prüfen: Urlaub, Krankheit, Zeitausgleich
    await expect(modal.getByRole('button', { name: 'Urlaub', exact: true })).toBeVisible();
    await expect(modal.getByRole('button', { name: 'Krankheit', exact: true })).toBeVisible();
    const zeitausgleichBtn = modal.getByRole('button', { name: 'Zeitausgleich', exact: true });
    await expect(zeitausgleichBtn).toBeVisible();

    // 6. Zeitausgleich per Button buchen
    await zeitausgleichBtn.click();
    await expect.poll(() => apiCalls.some(c => c.path === '/api/abwesenheit' && (c.body as Record<string, unknown>)?.typ === 'ZEITAUSGLEICH')).toBe(true);

    await designPruefung(page, info, 'kalender-zeitausgleich-modal');

    // 7. Modal schließen
    await modal.getByRole('button', { name: 'Schließen', exact: true }).click();
    await expect(modal).toBeHidden();
});

test('Kalender: Smarter Startzeit-Standard und automatisches Einschneiden von Pausen', async ({ page }, info) => {
    await blockiereFremdeNetzwerkzugriffe(page);

    await page.route('**/api/**', async route => {
        const req = route.request();
        const url = new URL(req.url());
        const path = url.pathname;

        let body: unknown = [];
        if (path === '/api/auth/me') {
            body = { id: 1, username: 'test', email: 'test@example.com', vorname: 'Max', nachname: 'Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false };
        } else if (path === '/api/mitarbeiter') {
            body = [{ id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true }];
        } else if (path.endsWith('/berechtigung')) {
            body = { darfMonatAbschliessen: true };
        } else if (path === '/api/zeitverwaltung/monatsabschluesse/1/2026/8') {
            body = { mitarbeiterId: 1, jahr: 2026, monat: 8, festgeschrieben: false, version: 1, sollStunden: 8, gesamtIst: 8, differenz: 0, audit: [] };
        } else if (path === '/api/zeitverwaltung/kalender') {
            body = {
                jahr: 2026,
                monat: 8,
                sollStundenMonat: 8,
                istStundenMonat: 8,
                differenz: 0,
                tage: [{
                    datum: '2026-08-03',
                    wochentag: 1,
                    istFeiertag: false,
                    feiertagName: null,
                    sollStunden: 8,
                    istStunden: 8,
                    buchungen: [{
                        id: 1,
                        projektId: 1,
                        projektName: 'Testprojekt',
                        arbeitsgangName: '',
                        startZeit: '08:00:00',
                        endeZeit: '16:00:00',
                        dauerMinuten: 480,
                        dauerFormatiert: '8:00',
                        notiz: ''
                    }]
                }]
            };
        } else if (path.startsWith('/api/zeitkonto/korrekturen/mitarbeiter/')) {
            body = [];
        } else if (['POST', 'PUT', 'DELETE'].includes(req.method())) {
            body = { id: 1 };
        }

        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    });

    await page.goto('/zeitbuchungen?mitarbeiterId=1&jahr=2026&monat=8');
    const tagZelle = page.getByText('3', { exact: true });
    await tagZelle.dblclick();

    const modal = page.getByRole('dialog', { name: 'Tageserfassung' });
    await expect(modal).toBeVisible();

    // 1. Initialer Zustand: Eine Buchung von 08:00 bis 16:00
    await expect(modal.getByRole('textbox', { name: 'Von Buchung 1' })).toHaveValue('08:00');
    await expect(modal.getByRole('textbox', { name: 'Bis Buchung 1' })).toHaveValue('16:00');

    // 2. Pause hinzufügen (Standard 12:00–12:30 schneidet in 08:00–16:00 ein)
    const pauseBtn = modal.getByRole('button', { name: 'Pause', exact: true });
    await pauseBtn.click();

    // 3. Prüfen, dass die Buchung in 3 Teile geschnitten wurde:
    // Buchung 1: 08:00–12:00
    // Buchung 2 (Pause): 12:00–12:30
    // Buchung 3: 12:30–16:00
    await expect(modal.getByRole('textbox', { name: 'Von Buchung 1' })).toHaveValue('08:00');
    await expect(modal.getByRole('textbox', { name: 'Bis Buchung 1' })).toHaveValue('12:00');

    await expect(modal.getByRole('textbox', { name: 'Von Buchung 2' })).toHaveValue('12:00');
    await expect(modal.getByRole('textbox', { name: 'Bis Buchung 2' })).toHaveValue('12:30');

    await expect(modal.getByRole('textbox', { name: 'Von Buchung 3' })).toHaveValue('12:30');
    await expect(modal.getByRole('textbox', { name: 'Bis Buchung 3' })).toHaveValue('16:00');

    // 4. "Neue Buchung" hinzufügen: Startzeit soll smart an das Ende der vorherigen Buchung (16:00) anschließen
    const neueBuchungBtn = modal.getByRole('button', { name: 'Neue Buchung', exact: true });
    await neueBuchungBtn.click();

    await expect(modal.getByRole('textbox', { name: 'Von Buchung 4' })).toHaveValue('16:00');

    await designPruefung(page, info, 'kalender-pausenschnitt-smartstart');

    await modal.getByRole('button', { name: 'Schließen', exact: true }).click();
    await expect(modal).toBeHidden();
});

test('Kalender: Mitarbeiter-Filter, geschlossener Monat Schutzdialog & GF-Ansicht', async ({ page }, info) => {
    await blockiereFremdeNetzwerkzugriffe(page);

    let monatFestgeschrieben = true;
    let oeffnenCalled = false;

    await page.route('**/api/**', async route => {
        const req = route.request();
        const url = new URL(req.url());
        const path = url.pathname;
        const method = req.method();

        let body: unknown = [];

        if (path === '/api/auth/me') {
            body = { id: 1, username: 'test', email: 'test@example.com', vorname: 'Max', nachname: 'Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false };
        } else if (path === '/api/mitarbeiter') {
            body = [
                { id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true, istGeschaeftsfuehrer: false },
                { id: 2, vorname: 'Erika', nachname: 'Inaktiv', aktiv: false, istGeschaeftsfuehrer: false },
                { id: 3, vorname: 'Erika', nachname: 'Musterfrau', aktiv: true, istGeschaeftsfuehrer: true }
            ];
        } else if (path.endsWith('/berechtigung')) {
            body = { darfMonatAbschliessen: true };
        } else if (path.includes('/monatsabschluesse/1/2026/8/oeffnen') && method === 'POST') {
            oeffnenCalled = true;
            monatFestgeschrieben = false;
            body = { mitarbeiterId: 1, jahr: 2026, monat: 8, festgeschrieben: false, version: 2, sollStunden: 8, gesamtIst: 8, differenz: 0, audit: [] };
        } else if (path === '/api/zeitverwaltung/monatsabschluesse/1/2026/8') {
            body = { mitarbeiterId: 1, jahr: 2026, monat: 8, festgeschrieben: monatFestgeschrieben, version: 1, sollStunden: 8, gesamtIst: 8, differenz: 0, audit: [] };
        } else if (path === '/api/zeitverwaltung/kalender') {
            body = {
                jahr: 2026,
                monat: 8,
                sollStundenMonat: 8,
                istStundenMonat: 8,
                differenz: 0,
                tage: [{
                    datum: '2026-08-03',
                    wochentag: 1,
                    istFeiertag: false,
                    feiertagName: null,
                    sollStunden: 8,
                    istStunden: 8,
                    buchungen: [{
                        id: 1,
                        projektId: 1,
                        projektName: 'Testprojekt',
                        arbeitsgangName: '',
                        startZeit: '08:00:00',
                        endeZeit: '16:00:00',
                        dauerMinuten: 480,
                        dauerFormatiert: '8:00',
                        notiz: ''
                    }]
                }]
            };
        } else if (path.startsWith('/api/zeitkonto/korrekturen/mitarbeiter/')) {
            body = [];
        } else if (path === '/api/projekte/simple') {
            body = [];
        } else if (path === '/api/arbeitsgaenge') {
            body = [];
        }

        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    });

    await page.goto('/zeitbuchungen?mitarbeiterId=1&jahr=2026&monat=8');

    // 1. Filterprüfung: Standardmäßig "Aktive Mitarbeiter"
    await expect(page.getByRole('combobox', { name: 'Mitarbeiter-Filter' })).toContainText('Aktive Mitarbeiter');
    await page.getByRole('combobox', { name: 'Mitarbeiter', exact: true }).click();
    await expect(page.getByRole('option', { name: 'Max Mustermann', exact: true })).toBeVisible();
    await expect(page.getByRole('option', { name: /Erika Inaktiv/ })).toHaveCount(0);
    // Dropdown wieder schließen
    await page.keyboard.press('Escape');

    // Filter auf "Nicht aktive Mitarbeiter" ändern
    await page.getByRole('combobox', { name: 'Mitarbeiter-Filter' }).click();
    await page.getByRole('option', { name: 'Nicht aktive Mitarbeiter', exact: true }).click();
    await page.getByRole('combobox', { name: 'Mitarbeiter', exact: true }).click();
    await expect(page.getByRole('option', { name: /Erika Inaktiv/ })).toBeVisible();
    await expect(page.getByRole('option', { name: 'Max Mustermann', exact: true })).toHaveCount(0);
    await page.keyboard.press('Escape');

    // Zurück zu Aktive Mitarbeiter
    await page.getByRole('combobox', { name: 'Mitarbeiter-Filter' }).click();
    await page.getByRole('option', { name: 'Aktive Mitarbeiter', exact: true }).click();

    // 2. Abgeschlossener Monat: Schutzdialog beim Doppelklick
    const tagZelle = page.getByText('3', { exact: true });
    await tagZelle.dblclick();

    // Dialog "Monatsabschluss ist festgeschrieben" erscheint
    const lockDialog = page.getByRole('dialog', { name: 'Monatsabschluss ist festgeschrieben' });
    await expect(lockDialog).toBeVisible();
    await expect(lockDialog).toContainText('bereits abgeschlossen und festgeschrieben');

    // "Nur ansehen" wählen
    await lockDialog.getByRole('button', { name: 'Nur ansehen' }).click();
    await expect(lockDialog).toBeHidden();

    // DayEditorModal im Read-Only-Modus prüfen
    const modal = page.getByRole('dialog', { name: 'Tageserfassung' });
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/Dieser Monat ist abgeschlossen \(schreibgeschützt\)/)).toBeVisible();
    await expect(modal.getByRole('button', { name: 'Neue Buchung' })).toHaveCount(0);
    await expect(modal.getByRole('button', { name: 'Alle Speichern' })).toHaveCount(0);

    // Klick auf "Monatsabschluss zurücksetzen" im Banner
    await modal.getByRole('button', { name: 'Monatsabschluss zurücksetzen' }).click();
    expect(oeffnenCalled).toBe(true);

    // Modal schließen
    await modal.getByRole('button', { name: 'Schließen', exact: true }).click();
    await expect(modal).toBeHidden();

    // 3. Geschäftsführer-Ansicht: Erika Musterfrau auswählen
    await page.getByRole('combobox', { name: 'Mitarbeiter', exact: true }).click();
    await page.getByRole('option', { name: /Erika Musterfrau/ }).click();

    // Bei GF darf Monatsabschluss-Karte und Korrekturen-Button nicht sichtbar sein
    await expect(page.getByRole('region', { name: 'Monatsabschluss' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Korrekturen' })).toHaveCount(0);
    await expect(page.getByText('Erfasste Arbeitszeit')).toBeVisible();

    await designPruefung(page, info, 'kalender-filter-schutzdialog-gf');
});


