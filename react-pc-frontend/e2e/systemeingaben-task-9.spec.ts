import { type Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

const ARBEITSZEIT = {
    montagStunden: 8, dienstagStunden: 8, mittwochStunden: 8, donnerstagStunden: 8,
    freitagStunden: 8, samstagStunden: 0, sonntagStunden: 0, buchungStartZeit: null, buchungEndeZeit: null,
};

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

test('Systemeingaben Task9: Arbeitszeit mit Kommawerten und eigener Zeitauswahl', async ({ page }, testInfo) => {
    const previews: Record<string, unknown>[] = [];
    const nativeDialogs: string[] = [];
    page.on('dialog', dialog => { nativeDialogs.push(dialog.type()); void dialog.dismiss(); });
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        const mitarbeiter = {
            id: 42, vorname: 'Max', nachname: 'Mustermann', strasse: null, plz: null, ort: null,
            email: 'max.mustermann@example.invalid', telefon: null, festnetz: null, qualifikation: null,
            stundenlohn: null, geburtstag: null, eintrittsdatum: '2026-01-01', aktiv: true,
            fuehrtZeitkonto: true, abteilungIds: [], abteilungNames: null, loginToken: null, jahresUrlaub: 30,
        };
        const zeitkonto = {
            mitarbeiterId: 42, mitarbeiterVersion: 7, mitarbeiterName: 'Max Mustermann', fuehrtZeitkonto: true,
            eingerichtet: false, hinweis: 'Bitte zuerst die Arbeitszeit einrichten.', aktuell: null, letzteVersion: null, historie: [],
        };
        if (pfad === '/api/auth/me') return json(route, { id: 1, username: 'anna.buero', displayName: 'Anna Büro', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/mitarbeiter') return json(route, [mitarbeiter]);
        if (pfad === '/api/abteilungen') return json(route, []);
        if (pfad === '/api/zeitverwaltung/zeitkonten/42') return json(route, zeitkonto);
        if (pfad === '/api/zeitverwaltung/zeitkontenmodelle') return json(route, [{ id: 3, version: 2, bezeichnung: 'Vollzeit Werkstatt', arbeitszeit: ARBEITSZEIT }]);
        if (pfad === '/api/zeitverwaltung/zeitkonten/42/vorschau' && methode === 'POST') {
            previews.push(route.request().postDataJSON());

            return json(route, { zeitkonto, gueltigVon: '2026-09-09', gespeichert: false, bestehendeAbwesenheiten: 1, hinweis: 'Offene Monate werden neu gerechnet.', monate: [{ jahr: 2026, monat: 9, abgeschlossen: false, saldoVorher: 2, saldoNachher: null, geaendert: false }] });
        }
        return json(route, []);
    });

    await page.goto('/mitarbeiter');
    await page.getByText('Mustermann', { exact: true }).click();
    await page.getByRole('button', { name: 'Bearbeiten' }).click();
    await expect(page.getByText('Bitte zuerst die Arbeitszeit einrichten.')).toBeVisible();
    await page.getByRole('button', { name: 'Arbeitszeit einrichten' }).click();
    const dialog = page.getByRole('dialog').filter({ has: page.getByRole('heading', { name: 'Arbeitszeit einrichten' }) });
    await dialog.getByRole('combobox').click();
    await page.getByRole('option', { name: 'Vollzeit Werkstatt' }).click();
    await dialog.getByLabel('Diese Vorlage für diese Person individuell anpassen').check();
    const monday = dialog.getByRole('textbox', { name: 'Montag Stunden' });
    await monday.click(); await expect(monday).toHaveValue('8');
    const saturday = dialog.getByRole('textbox', { name: 'Samstag Stunden' });
    await expect(saturday).toHaveValue('0'); await saturday.click(); await expect(saturday).toHaveValue('');
    await dialog.getByRole('button', { name: 'Vorschau anzeigen' }).click();
    expect(previews).toHaveLength(0);
    await saturday.fill('8,');
    await dialog.getByRole('button', { name: 'Vorschau anzeigen' }).click();
    expect(previews).toHaveLength(0);
    await saturday.fill('8,5'); await saturday.press('Tab');
    const sunday = dialog.getByRole('textbox', { name: 'Sonntag Stunden' });
    await expect(sunday).toBeFocused(); await expect(sunday).toHaveValue(''); await sunday.fill('0');
    const start = dialog.getByRole('textbox', { name: 'Früheste Buchung – optional' });
    await start.fill('25:99'); await dialog.getByRole('button', { name: 'Vorschau anzeigen' }).click();
    expect(previews).toHaveLength(0);
    await start.fill('09:05');
    await expect(dialog.locator('input[type="time"],input[type="number"],select')).toHaveCount(0);
    for (const close of await page.getByRole('button', { name: 'Benachrichtigung schließen' }).all()) await close.click();
    await designPruefung(page, testInfo, 'systemeingaben-task9-arbeitszeit', { primaerAktion: dialog.getByRole('button', { name: 'Vorschau anzeigen' }) });
    await dialog.getByRole('button', { name: 'Vorschau anzeigen' }).click();
    await expect.poll(() => previews.length).toBe(1);
    expect(previews[0]).toMatchObject({ arbeitszeit: { samstagStunden: 8.5, sonntagStunden: 0, buchungStartZeit: '09:05' } });
    expect(nativeDialogs).toEqual([]);
});

test('Systemeingaben Task9: Firmenfarbe und ganze Mahntage', async ({ page }, testInfo) => {
    const saves: Record<string, unknown>[] = [];
    const firma = { id: 1, firmenname: 'Testbetrieb', firmenfarbe: '#500010', mahnverfahrenAktiv: true, tageBisZahlungserinnerung: 7, tageBisErsteMahnung: 7, tageBisZweiteMahnung: 7, mahnverfahrenNeuesZahlungszielTage: 7, bgSatzOverride: 0 };
    await page.route('**/api/**', route => {
        const path = new URL(route.request().url()).pathname;
        if (path === '/api/auth/me') return json(route, { id: 1, username: 'test', displayName: 'Test Büro', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        if (path === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (path === '/api/firma') {
            if (route.request().method() === 'PUT') { saves.push(route.request().postDataJSON()); return json(route, saves.at(-1)); }
            return json(route, firma);
        }
        return json(route, []);
    });
    await page.goto('/firma');
    const days = page.getByRole('textbox', { name: 'Tage nach Fälligkeit' });
    await days.fill(''); await page.getByRole('button', { name: 'Speichern', exact: true }).click(); expect(saves).toHaveLength(0);
    await days.fill('8,5'); await page.getByRole('button', { name: 'Speichern', exact: true }).click(); expect(saves).toHaveLength(0);
    await days.fill('8');
    await page.getByRole('button', { name: 'Firmenfarbe wählen', exact: true }).click();
    const palette = page.getByRole('dialog', { name: 'Firmenfarbe wählen auswählen' });
    await expect(palette).toBeVisible();
    await expect(palette.getByRole('textbox', { name: 'Hex-Farbwert' })).toBeFocused();
    await page.screenshot({ path: testInfo.outputPath('firmenfarbe.png') });
    await palette.getByRole('button', { name: 'Rose (#e11d48)' }).click();
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect.poll(() => saves.length).toBe(1); expect(saves[0]).toMatchObject({ tageBisZahlungserinnerung: 8, firmenfarbe: '#e11d48' });
    await page.getByRole('button', { name: /Unfallversicherung/ }).click();
    const percent = page.getByRole('textbox', { name: 'Tatsächlicher BG-Satz (aus Bescheid)' });
    await percent.click(); await expect(percent).toHaveValue(''); await percent.fill('8,5');
    await expect(page.locator('input[type="number"],input[type="color"],select')).toHaveCount(0);
    await page.screenshot({ path: testInfo.outputPath('unfallversicherung.png') });
});

test('Systemeingaben Task9: Verrechnung erhält leere Entwürfe bis zur gültigen Übernahme', async ({ page }, testInfo) => {
    const applied: Record<string, unknown>[] = [];
    const year = new Date().getFullYear();
    await page.route('**/api/**', route => {
        const url = new URL(route.request().url());
        if (url.pathname === '/api/auth/me') return json(route, { id: 1, username: 'test', displayName: 'Test Büro', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        if (url.pathname === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (url.pathname === '/api/verrechnungslohn/uebernehmen') { applied.push(route.request().postDataJSON()); return json(route, {}); }
        if (url.pathname === '/api/verrechnungslohn') return json(route, {
            jahr: year, modus: 'HOCHRECHNUNG', interneQuoteProzent: Number(url.searchParams.get('internProzent') ?? 5),
            lohnzeilen: [{ mitarbeiterId: 1, name: 'Max Mustermann', istGeschaeftsfuehrer: false, beschaeftigungsart: 'REGULAER', bruttoJahr: 50000, agAnteilSv: 10000, bgBeitrag: 1000, geldwerterVorteilJahr: 0, gesamtkosten: 61000, quelle: 'STUNDENLOHN_HOCHRECHNUNG', bruttoIstDefault: false }],
            stundenzeilen: [{ mitarbeiterId: 1, name: 'Max Mustermann', istGeschaeftsfuehrer: false, sollstunden: 2080, urlaubsstunden: 200, krankheitsstunden: 64, interneStunden: 104, feiertagsstunden: 96, verkaeuflicheStunden: 1712, urlaubIstDefault: false, krankheitIstDefault: false, interneIstDefault: true, sollIstDefault: false, ausgeklammerteTage: 0 }],
            kostenstellen: [], abteilungen: [{ abteilungId: 10, name: 'Werkstatt', aufschlagEuro: 0 }], datenLuecken: [], lohnsummeGesamt: 61000, verkaeuflicheStundenGesamt: 1712, gemeinkostenGesamt: 0, selbstkostenProStunde: 35.63,
        });
        return json(route, []);
    });
    await page.goto('/arbeitsgaenge');
    await page.getByRole('button', { name: 'Was muss meine Stunde kosten?' }).click();
    const field = page.getByRole('textbox', { name: 'Aufschlag oder Abschlag für Werkstatt in Euro' });
    await field.click(); await expect(field).toHaveValue('');
    const apply = page.getByRole('button', { name: /Auf alle Arbeitsgänge.*übernehmen/ });
    await apply.click(); expect(applied).toHaveLength(0);
    await expect(page.getByRole('heading', { name: 'Auf alle Arbeitsgänge übernehmen' })).toHaveCount(0);
    await field.fill('8,'); await apply.click(); expect(applied).toHaveLength(0);
    // Die Fussleiste des Rechners traegt "Schließen"; das X des gemeinsamen
    // Dialogs heisst "Fenster schließen". Hier ist die Fussleiste gemeint --
    // sie darf nicht von Meldungen verdeckt sein.
    await page.getByRole('dialog').getByRole('button', { name: 'Schließen', exact: true }).click({ trial: true, timeout: 5000 });
    await field.fill('8,5'); await field.press('Tab');
    await expect(field).toHaveValue('8,5');
    await page.screenshot({ path: testInfo.outputPath('verrechnungslohn.png') });
    await apply.click();
    await page.getByRole('button', { name: 'Ja, überschreiben' }).click();
    await expect.poll(() => applied.length).toBe(1);
    expect(applied[0]).toMatchObject({ abteilungAufschlaege: [{ abteilungId: 10, aufschlagEuro: 8.5 }] });
});
