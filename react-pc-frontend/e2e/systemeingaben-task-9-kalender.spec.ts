import { test, expect } from './hilfen/test';
import { designPruefung, uebergaengeAusklingenLassen } from './hilfen/design';

test('Kalender: Uhrzeitentwürfe, Pflichtgrund und eigene Monatsauswahl', async ({ page }, info) => {
    const writes: { path: string; body: Record<string, unknown> }[] = [];
    const dialogs: string[] = [];
    page.on('dialog', async dialog => { dialogs.push(dialog.type()); await dialog.dismiss(); });
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname;
        let body: unknown = [];
        if (path === '/api/auth/me') body = { id: 1, username: 'test', email: 'test@example.com', vorname: 'Max', nachname: 'Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false };
        if (path === '/api/mitarbeiter') body = [{ id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true }];
        if (path.endsWith('/berechtigung')) body = { darfMonatAbschliessen: true };
        if (path === '/api/zeitverwaltung/monatsabschluesse/1/2026/8') body = { mitarbeiterId: 1, jahr: 2026, monat: 8, festgeschrieben: false, version: 1, sollStunden: 8, gesamtIst: 8, differenz: 0, audit: [] };
        if (path === '/api/zeitverwaltung/kalender') body = { jahr: 2026, monat: 8, sollStundenMonat: 8, istStundenMonat: 8, differenz: 0, tage: [{ datum: '2026-08-03', wochentag: 1, istFeiertag: false, feiertagName: null, sollStunden: 8, istStunden: 8, buchungen: [{ id: 1, projektId: 1, projektName: 'Testprojekt', arbeitsgangName: '', startZeit: '08:00:15', endeZeit: '16:00:30', dauerMinuten: 480, dauerFormatiert: '8:00', notiz: '' }] }] };
        if (path.startsWith('/api/zeitkonto/korrekturen/mitarbeiter/')) body = [{ id: 1, datum: '2026-08-03', stunden: 2, grund: 'Testkorrektur', typ: 'MANUELLE_KORREKTUR', storniert: false }];
        if (['POST', 'PUT', 'DELETE'].includes(route.request().method())) { writes.push({ path, body: route.request().postDataJSON() ?? {} }); body = { id: 1 }; }
        await route.fulfill({ json: body });
    });
    await page.goto('/kalender');
    await page.getByRole('button', { name: 'Neuer Termin', exact: true }).click();
    await page.getByPlaceholder('z.B. Montage, Besprechung...').fill('Testtermin');
    const von = page.getByRole('textbox', { name: 'Von', exact: true });
    await von.fill('25:99'); await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect(page.getByText(/Bitte Beginn als gültige Uhrzeit/)).toBeVisible(); expect(writes).toHaveLength(0);
    await von.fill('09:05'); await page.getByRole('textbox', { name: 'Bis', exact: true }).fill('10:15');
    await designPruefung(page, info, 'systemeingaben-task9-termin');
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect.poll(() => writes.find(w => w.path === '/api/kalender')?.body).toMatchObject({ startZeit: '09:05:00', endeZeit: '10:15:00' });
    await page.goto('/zeitbuchungen?mitarbeiterId=1&jahr=2026&monat=8');
    await page.getByText('3', { exact: true }).dblclick();
    const beginn = page.getByRole('textbox', { name: 'Von Buchung 1' });
    await beginn.fill('25:99'); await page.getByRole('button', { name: 'Alle Speichern', exact: true }).click();
    await expect(beginn).toHaveValue('25:99'); expect(writes.filter(w => w.path.includes('/buchungen/'))).toHaveLength(0);
    await beginn.fill('09:05'); await designPruefung(page, info, 'systemeingaben-task9-zeitkalender');
    await page.getByRole('button', { name: 'Alle Speichern', exact: true }).click();
    await expect.poll(() => writes.find(w => w.path === '/api/zeitverwaltung/buchungen/1')?.body).toMatchObject({ startZeit: '2026-08-03T09:05:00', endeZeit: '2026-08-03T16:00:30' });
    await page.goto('/zeitbuchungen?mitarbeiterId=1&jahr=2026&monat=8');
    await page.getByText('3', { exact: true }).click({ button: 'right' });
    await page.getByText('Zeitkonto-Korrektur', { exact: true }).click();
    const stornoOeffner = page.getByTitle('Stornieren', { exact: true });
    await stornoOeffner.click();
    const storno = page.getByRole('dialog', { name: 'Korrektur stornieren' });
    await storno.getByRole('button', { name: 'Stornierung bestätigen' }).click();
    await expect(storno.getByRole('alert')).toBeVisible();
    // Der oberste Dialog darf den absichtlich gesperrten Hintergrund überdecken.
    // Der globale Helper zählt feste Hintergrundelemente trotzdem mit.
    await expect(page.locator('[inert]')).not.toHaveCount(0);
    const grenzen = await storno.boundingBox();
    expect(grenzen).not.toBeNull();
    expect(grenzen!.x).toBeGreaterThanOrEqual(0); expect(grenzen!.y).toBeGreaterThanOrEqual(0);
    expect(grenzen!.x + grenzen!.width).toBeLessThanOrEqual(page.viewportSize()!.width);
    expect(grenzen!.y + grenzen!.height).toBeLessThanOrEqual(page.viewportSize()!.height);
    for (const button of await storno.getByRole('button').all()) await expect(button).toBeInViewport();
    await uebergaengeAusklingenLassen(page);
    await page.screenshot({ path: info.outputPath('stornogrund-offen.png') });
    const grund = storno.getByRole('textbox', { name: 'Stornierungsgrund' });
    const schliessen = storno.getByRole('button').last();
    // Der Pflichtgrund-Fehler erzeugt zugleich eine Meldung. Seit Abschnitt 4
    // gehoert die Meldungsflaeche bewusst zum Tab-Kreis des obersten Dialogs
    // (sie soll per Tastatur erreichbar sein). Entscheidend bleibt: der Fokus
    // verlaesst nie den Dialog samt Meldungsflaeche in den gesperrten
    // Hintergrund -- genau das pruefen wir hier ueber eine volle Runde.
    const meldungen = page.locator('[data-pc-toasts]');
    await expect(meldungen).toBeVisible();
    const imKreis = () => page.evaluate(() => {
        const aktiv = document.activeElement;
        if (!aktiv) return false;
        return !!aktiv.closest('[role="dialog"]') || !!aktiv.closest('[data-pc-toasts]');
    });
    await grund.focus();
    for (let schritt = 0; schritt < 12; schritt++) {
        await page.keyboard.press('Tab');
        expect(await imKreis(), `Fokus ist nach ${schritt + 1}x Tab aus Dialog und Meldungsflaeche entkommen`).toBe(true);
    }
    for (let schritt = 0; schritt < 12; schritt++) {
        await page.keyboard.press('Shift+Tab');
        expect(await imKreis(), `Fokus ist nach ${schritt + 1}x Shift+Tab aus Dialog und Meldungsflaeche entkommen`).toBe(true);
    }
    await grund.focus(); await expect(grund).toBeFocused();
    await expect(schliessen).toBeVisible();
    await storno.getByRole('button', { name: 'Abbrechen', exact: true }).click();
    await expect(stornoOeffner).toBeFocused();
    await stornoOeffner.click(); await grund.press('Escape');
    await expect(storno).toBeHidden(); await expect(stornoOeffner).toBeFocused();
    expect(writes.filter(w => w.path.startsWith('/api/zeitkonto/korrekturen/'))).toHaveLength(0);
    await page.goto('/steuerberater');
    const monat = page.getByRole('combobox', { name: 'Monat', exact: true });
    await monat.focus(); await monat.press('Enter'); await expect(page.getByRole('listbox')).toBeVisible();
    await page.screenshot({ path: info.outputPath('monatsauswahl-offen.png') });
    await page.getByRole('option', { name: 'Mai', exact: true }).click(); await expect(monat).toContainText('Mai');
    await designPruefung(page, info, 'systemeingaben-task9-steuerberater'); expect(dialogs).toEqual([]);
});
