import { test, expect } from './hilfen/test';
import { designPruefung, uebergaengeAusklingenLassen } from './hilfen/design';
test('Finanzen: Kommawerte, Zahlpflicht, eigene Picker und Kassenmeldungen', async ({ page }, info) => {
    const writes: {
        path: string;
        body: Record<string, unknown>;
    }[] = [];
    const native: string[] = [];
    page.on('dialog', async (d) => { native.push(d.type()); await d.dismiss(); });
    const beleg = { id: 1, belegNummer: 'TEST-BELEG', belegKategorie: 'SONSTIGER_BELEG', status: 'NEU', kiAnalyseStatus: 'DONE', uploadDatum: '2026-09-09T10:00:00', belegDatum: '2026-09-09', betragBrutto: 0, betragNetto: 10, mwstSatz: 19, kostenstellenSplits: [{ kostenstelleId: 1, prozent: 0, absoluterBetrag: null, streckungJahre: 1, streckungStartJahr: 2026 }] };
    await page.route('**/api/**', async (route) => {
        const path = new URL(route.request().url()).pathname;
        let body: unknown = [];
        if (path === '/api/auth/me')
            body = { id: 1, username: 'test', email: 'test@example.com', vorname: 'Max', nachname: 'Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false };
        if (path === '/api/buchhaltung/belege')
            body = [beleg];
        if (path === '/api/bestellungen-uebersicht/kostenstellen')
            body = [{ id: 1, bezeichnung: 'Testwerkstatt' }];
        if (path === '/api/buchhaltung/kasse/saldo')
            body = { saldo: 400, mindestbestand: 0 };
        if (path === '/api/buchhaltung/kassenbuch')
            body = { saldoStart: 400, saldoEnde: 400, summeEinnahmen: 0, summeAusgaben: 0, summePrivatentnahmen: 0, summePrivateinlagen: 0, bewegungen: [], letzterAbschluss: null, offeneBewegungen: 1 };
        if (path === '/api/buchhaltung/kasse/einstellung')
            body = { mindestbestand: 0, ehegattengehaltAktiv: true, ehegattengehaltBetrag: 12.5, ehegattengehaltTag: 1 };
        if (path.includes('/zaehlungen'))
            body = { rechnerischerBestand: 400 };
        if (path === '/api/lieferanten')
            body = [{ id: 1, firmenname: 'Testlieferant' }];
        if (route.request().method() === 'PUT' || route.request().method() === 'POST') {
            if (path.endsWith('/analyze-upload'))
                body = [{ analyzeResponse: { betragBrutto: 0, dokumentTyp: 'RECHNUNG', dokumentDatum: '2026-09-09', lieferantName: 'Testlieferant' } }];
            else if (path.endsWith('/import-upload'))
                writes.push({ path, body: {} });
            else {
                writes.push({ path, body: route.request().postDataJSON() });
                body = beleg;
            }
        }
        await route.fulfill({ json: body });
    });
    await page.goto('/belege-kasse');
    await page.getByRole('button', { name: /TEST-BELEG/ }).click();
    const amount = page.getByRole('textbox', { name: 'Betrag (€)', exact: true });
    await expect(amount).toHaveValue('0');
    await amount.click();
    await expect(amount).toHaveValue('');
    const save = page.getByRole('button', { name: 'Prüfen & Übernehmen' });
    await save.click();
    expect(writes).toHaveLength(0);
    await amount.fill('12,');
    await save.click();
    expect(writes).toHaveLength(0);
    await amount.fill('12,50');
    const split = page.getByRole('textbox', { name: 'Anteil Split 1 (%)' });
    await split.focus();
    await expect(split).toHaveValue('');
    await save.click();
    expect(writes).toHaveLength(0);
    await split.fill('100');
    await page.getByRole('button', { name: /Mehr Details/ }).click();
    const netto = page.getByRole('textbox', { name: 'Netto (€)' });
    await netto.focus();
    await expect(netto).toHaveValue('10');
    await page.getByRole('button', { name: 'Wählen', exact: true }).click();
    await page.getByPlaceholder('Suche nach Name, Ort, Typ, Vertreter...').fill('Testlieferant');
    await page.keyboard.press('Escape');
    await expect(amount).toHaveValue('12,50');
    await page.getByRole('button', { name: 'Beleg-Datum' }).click();
    await expect(page.getByRole('dialog', { name: 'Datum auswählen' })).toBeVisible();
    await page.screenshot({ path: info.outputPath('beleg-datum-offen.png') });
    await page.keyboard.press('Escape');
    await designPruefung(page, info, 'task10-beleg', { primaerAktion: save });
    await save.click();
    await expect.poll(() => writes[0]?.body.betragBrutto).toBe(12.5);
    await page.getByRole('button', { name: 'Kassenbuch', exact: true }).click();
    await page.getByRole('button', { name: 'Privateinlage', exact: true }).click();
    const cash = page.getByRole('textbox', { name: 'Betrag (€)', exact: true });
    await cash.fill('12,501');
    await page.getByRole('button', { name: /buchen/i }).last().click();
    expect(writes).toHaveLength(1);
    await cash.fill('12,50');
    await designPruefung(page, info, 'task10-privateinlage');
    await page.getByRole('button', { name: /buchen/i }).last().click();
    await expect.poll(() => writes.length).toBe(2);
    await page.getByRole('button', { name: 'Kasse zählen' }).click();
    const count = page.getByRole('textbox', { name: 'Anzahl 200 €' });
    await count.click();
    await expect(count).toHaveValue('');
    await count.fill('2');
    await count.press('Tab');
    await expect(page.getByRole('textbox', { name: 'Anzahl 100 €' })).toBeFocused();
    await expect(page.getByRole('textbox', { name: 'Anzahl 100 €' })).toHaveValue('');
    await page.getByRole('button', { name: 'Zählung festhalten' }).click();
    expect(writes).toHaveLength(2);
    await page.getByRole('textbox', { name: 'Anzahl 100 €' }).fill('0');
    await designPruefung(page, info, 'task10-zaehlung');
    await page.getByRole('button', { name: 'Zählung festhalten' }).click();
    await expect.poll(() => writes.length).toBe(3);
    await page.getByTitle('Mindestbestand & Automatik einstellen').click();
    const minimum = page.getByRole('textbox', { name: 'Mindestbestand (€)' });
    await minimum.fill('0');
    await page.getByRole('button', { name: 'Speichern', exact: true }).focus();
    await minimum.click();
    await expect(minimum).toHaveValue('');
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    expect(writes).toHaveLength(3);
    await minimum.fill('0');
    const day = page.getByRole('textbox', { name: 'Tag des Monats (1–28)' });
    await day.fill('1,5');
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    expect(writes).toHaveLength(3);
    await day.fill('28');
    const notices = page.getByRole('region', { name: 'Meldungen' });
    const settings = page.getByRole('dialog', { name: 'Kassen-Einstellungen' });
    const noticeBounds = await notices.boundingBox();
    const dialogBounds = await settings.boundingBox();
    expect(noticeBounds!.y + noticeBounds!.height).toBeLessThanOrEqual(dialogBounds!.y);
    expect(noticeBounds!.height).toBeLessThanOrEqual(Math.min(page.viewportSize()!.height * 0.25, 192));
    expect(await notices.evaluate(element => element.scrollHeight > element.clientHeight)).toBe(true);
    await settings.getByRole('button', { name: 'Schließen', exact: true }).focus();
    await page.keyboard.press('Tab'); await expect(notices).toBeFocused();
    await notices.press('Home');
    await day.focus(); await notices.focus(); await page.keyboard.press('Escape');
    await expect(day).toBeFocused(); await expect(settings).toBeVisible();
    await notices.evaluate(element => { element.scrollTop = 0; });
    await expect(notices.getByRole('button', { name: 'Meldung schließen' }).first()).toBeInViewport();
    await notices.getByRole('button', { name: 'Meldung schließen' }).first().click();
    await expect(day).toHaveValue('28');
    await designPruefung(page, info, 'task10-kasseneinstellung');
    while (await notices.getByRole('button', { name: 'Meldung schließen' }).count()) {
        await notices.getByRole('button', { name: 'Meldung schließen' }).last().click();
    }
    await expect(notices).toBeHidden();
    await expect(day).toHaveValue('28');
    await expect(minimum).toHaveValue('0');

    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect.poll(() => writes.length).toBe(4);
    await page.goto('/rechnungsuebersicht');
    await page.getByRole('button', { name: /hochladen/i }).click();
    await page.locator('input[type=file]').setInputFiles({ name: 'test.pdf', mimeType: 'application/pdf', buffer: Buffer.from('dummy') });
    const invoice = page.getByRole('textbox', { name: 'Betrag Brutto' });
    await invoice.click();
    await expect(invoice).toHaveValue('');
    await invoice.fill('12,50');
    await uebergaengeAusklingenLassen(page);
    await designPruefung(page, info, 'task10-rechnung');
    await page.getByRole('button', { name: /speichern/i }).last().click();
    await expect.poll(() => writes.length).toBe(5);
    expect(native).toEqual([]);
    await expect(page.locator('input[type=number],input[type=date],select')).toHaveCount(0);
});
