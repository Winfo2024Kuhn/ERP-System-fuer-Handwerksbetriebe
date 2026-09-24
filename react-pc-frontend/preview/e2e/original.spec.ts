import { test, expect } from '@playwright/test';
import { mkdir } from 'node:fs/promises';

// Isolated IDS test server; a different port can be chosen so running previews stay untouched.
const isolatedBaseUrl = `http://127.0.0.1:${process.env.EN1090_PREVIEW_PORT ?? 8098}`;

test.beforeEach(async ({ context, baseURL, request }) => {
    expect((await (await request.get('/api/preview/mode')).json()).idsTestMode).toBe(true);
    expect((await request.post('/api/preview/reset')).status()).toBe(200);
    await context.route('**/*', route => {
        const url = new URL(route.request().url());
        return url.origin === baseURL || url.protocol === 'data:' || url.protocol === 'blob:' ? route.continue() : route.abort();
    });
});

test('Originalbedarf: Übersicht, Mengen, Original-PDF, Material und Kategorien', async ({ page, request }, testInfo) => {
    const apiErrors: string[] = [];
    page.on('response', response => { if (response.url().includes('/api/') && response.status() >= 400) apiErrors.push(`${response.status()} ${response.url()}`); });
    const dir = '/tmp/en1090-original-screenshots';
    await mkdir(dir, { recursive: true });
    await page.goto('/bestellungen/bedarf');
    await expect(page.getByRole('heading', { name: 'Bedarf je Projekt' })).toBeVisible();
    await expect(page.getByText('Werkhalle Musterstadt', { exact: true })).toBeVisible();
    await page.screenshot({ path: `${dir}/uebersicht-${testInfo.project.name}.png`, fullPage: true });
    await page.getByText('Werkhalle Musterstadt', { exact: true }).click();
    await expect(page).toHaveURL(/\/bestellungen\/bedarf\/projekt\/101$/);
    await expect(page.getByRole('heading', { name: 'Werkhalle Musterstadt' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Vorhanden/ })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Bestellen/ })).toBeVisible();
    await page.screenshot({ path: `${dir}/projekt-${testInfo.project.name}.png`, fullPage: true });

    const pdf = await request.get('/api/bestellungen/projekt/101/bedarfsliste-pdf');
    expect(pdf.status()).toBe(200);
    expect(pdf.headers()['content-type']).toContain('application/pdf');
    expect((await pdf.body()).subarray(0, 5).toString()).toBe('%PDF-');
    const popupPromise = page.context().waitForEvent('page');
    const printRequest = page.context().waitForEvent('request', req => req.url().endsWith('/api/bestellungen/projekt/101/bedarfsliste-pdf'));
    await page.getByRole('button', { name: 'Stückliste drucken' }).click();
    const popup = await popupPromise;
    // Headless Chromium may download PDFs and leave the opened tab at about:blank.
    expect((await printRequest).url()).toContain('/projekt/101/bedarfsliste-pdf');
    await popup.close();

    await page.getByRole('button', { name: 'Material hinzufügen' }).click();
    const modal = page.getByRole('dialog', { name: 'Materialbestellung' });
    await expect(modal).toBeVisible();
    await expect(modal.getByText('Warengruppe', { exact: true })).toHaveCount(0);
    const material = `Mock-Material ${testInfo.project.name}`;
    await modal.getByPlaceholder('z. B. IPE 200, S235').fill(material);
    await modal.getByPlaceholder('1', { exact: true }).fill('4');
    await page.screenshot({ path: `${dir}/material-${testInfo.project.name}.png`, fullPage: true });
    await modal.getByRole('button', { name: 'Alle speichern' }).click();
    await expect(modal).toHaveCount(0);
    await expect(page.getByText(material, { exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByText(material, { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Material hinzufügen' }).click();
    await page.getByRole('button', { name: 'Artikel suchen...' }).click();
    await page.getByRole('button', { name: 'Alle Kategorien' }).click();
    const category = page.getByRole('heading', { name: 'Kategorie auswählen' }).locator('..').locator('..').locator('..').locator('..');
    await expect(category).toBeVisible();
    await expect(category.getByText('Keine Kategorien verfügbar')).toHaveCount(0);
    await expect(category.locator('li').first()).toBeVisible();
    await page.screenshot({ path: `${dir}/kategorien-${testInfo.project.name}.png`, fullPage: true });
    expect(apiErrors).toEqual([]);
});

test('Originalseiten: Bestellübersicht und Preisanfragen mit PDF', async ({ page, request }, testInfo) => {
    const apiErrors: string[] = [];
    page.on('response', response => { if (response.url().includes('/api/') && response.status() >= 400) apiErrors.push(`${response.status()} ${response.url()}`); });
    await page.goto('/bestellungen');
    await expect(page.getByRole('heading', { name: 'BESTELLUNGEN', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: /Aktualisieren/ })).toBeEnabled();
    await page.screenshot({ path: `/tmp/en1090-original-screenshots/bestellungen-${testInfo.project.name}.png`, fullPage: true });
    await page.goto('/einkauf/preisanfragen');
    await expect(page.getByRole('heading', { name: 'Preisanfragen', exact: true })).toBeVisible();
    const pdfLink = page.getByRole('link', { name: 'PDF', exact: true }).first();
    await expect(pdfLink).toBeVisible();
    const response = await request.get((await pdfLink.getAttribute('href'))!);
    expect(response.status()).toBe(200);
    expect((await response.body()).subarray(0, 5).toString()).toBe('%PDF-');
    await page.screenshot({ path: `/tmp/en1090-original-screenshots/preisanfragen-${testInfo.project.name}.png`, fullPage: true });
    expect(apiErrors).toEqual([]);
});

test('IDS: Originalauswahl sendet multipart und übernimmt einen Warenkorb sichtbar', async ({ page, context, request, baseURL }, testInfo) => {
    expect(baseURL).toBe(isolatedBaseUrl);
    // Only dummy credentials reach the stub shop. Never exercise real vendor credentials in E2E.
    await context.route('**/api/ids/lieferanten', route => route.fulfill({ json: [{ id: 203, name: 'Würth' }] }));
    const cart = '<Warenkorb><WarenkorbInfo><RueckgabeKZ>Warenkorbrückgabe</RueckgabeKZ></WarenkorbInfo><Order><OrderInfo><Cur>EUR</Cur></OrderInfo><OrderItem><ArtNo>DEMO-E2E</ArtNo><Qty>3</Qty><QU>PCE</QU><Kurztext>IDS Testschraube</Kurztext><Langtext>Verzinkt, mit Unterlegscheibe</Langtext><EAN>0012345678901</EAN><NetPrice>10</NetPrice><PriceBasis>100</PriceBasis><VAT>19</VAT></OrderItem></Order></Warenkorb>';
    let callback = '';
    await context.route('**/api/ids/punchout/203/start', async route => {
        // Forward the project assignment unchanged: the cart must be parked at the project it was started from.
        const startBody = route.request().postData() ?? '';
        expect(JSON.parse(startBody)).toMatchObject({ projektId: 101, projektName: 'Werkhalle Musterstadt' });
        // The isolated test server uses dummy credentials only, loaded from its own private config.
        const response = await request.post('/api/ids/punchout/203/start', { headers: { Origin: baseURL!, 'Content-Type': route.request().headers()['content-type'] ?? 'application/json' }, data: startBody });
        expect(response.status()).toBe(200);
        const metadata = await response.json();
        expect(metadata.fields.pw_kunde === 'dummy-only').toBe(true);
        expect(metadata.fields.action).toBe('WKE');
        callback = metadata.fields.hookurl;
        await route.fulfill({ json: { action: `${baseURL}/ids-dummy-shop`, enctype: 'multipart/form-data', fields: { kndnr: 'dummy', name_kunde: 'dummy', pw_kunde: 'dummy-only', hookurl: callback } } });
    });
    await context.route('**/ids-dummy-shop', async route => {
        expect(route.request().method()).toBe('POST');
        expect(route.request().headers()['content-type']).toContain('multipart/form-data');
        expect(route.request().postData()).toContain('dummy-only');
        await route.fulfill({ contentType: 'text/html; charset=utf-8', body: `<meta charset="utf-8"><form action="${callback}" method="post" enctype="multipart/form-data"><textarea name="warenkorb">${cart.replaceAll('<', '&lt;')}</textarea><button>Warenkorb übernehmen</button></form>` });
    });
    await page.goto('/bestellungen/bedarf/projekt/101');
    await page.getByRole('button', { name: 'Im Lieferanten-Shop', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Würth', exact: true })).toBeVisible();
    await page.screenshot({ path: `/tmp/en1090-original-screenshots/ids-${testInfo.project.name}.png`, fullPage: true });
    const shopPromise = context.waitForEvent('page');
    await page.getByRole('button', { name: 'Würth', exact: true }).click();
    const shop = await shopPromise;
    await shop.getByRole('button', { name: 'Warenkorb übernehmen' }).click();
    // Started from the project need: the cart is parked there instead of opening the cart page directly.
    await expect(shop).toHaveURL(/\/bestellungen\/bedarf\/projekt\/101\?warenkorb=[^&]+$/);
    const cartId = new URL(shop.url()).searchParams.get('warenkorb')!;
    await expect(shop.getByText('Warenkorb am Projekt geparkt', { exact: true })).toBeVisible();
    const parked = shop.getByRole('region', { name: 'Geparkte Shop-Warenkörbe' });
    const parkedCart = parked.getByRole('link', { name: /Würth · B-/ });
    await expect(parkedCart).toHaveAttribute('aria-current', 'true');
    await expect(parkedCart).toHaveAttribute('href', `/bestellungen/ids/${cartId}`);
    await expect(parkedCart).toContainText('1 Position');
    await expect(parkedCart).toContainText('Noch nicht bestellt');
    expect((await request.post(callback, { form: { warenkorb: cart } })).status()).toBe(403);
    await shop.screenshot({ path: `/tmp/en1090-original-screenshots/ids-parked-${testInfo.project.name}.png`, fullPage: true });
    await parkedCart.click();
    await expect(shop).toHaveURL(new RegExp(`/bestellungen/ids/${cartId}$`));
    await expect(shop.getByText('IDS Testschraube', { exact: true })).toBeVisible();
    await expect(shop.getByRole('link', { name: 'Projekt: Werkhalle Musterstadt' })).toHaveAttribute('href', '/bestellungen/bedarf/projekt/101');
    await expect(shop.getByRole('link', { name: 'Zurück zum Projektbedarf' })).toHaveAttribute('href', '/bestellungen/bedarf/projekt/101');
    await expect(shop.getByText('Am Projekt geparkt – noch nicht bestellt', { exact: true })).toBeVisible();
    await shop.screenshot({ path: `/tmp/en1090-original-screenshots/ids-return-${testInfo.project.name}.png`, fullPage: true });
    await expect(shop.getByText('Verzinkt, mit Unterlegscheibe')).toBeVisible();
    await expect(shop.getByText('EAN 0012345678901')).toBeVisible();
    // NetPrice is the net amount of the whole position: 3 pieces for 10,00 EUR.
    await expect(shop.getByText('Warenwert netto: 10,00 EUR')).toBeVisible();
    await expect(shop.getByText('3,3333 EUR')).toBeVisible();
    const reviewUrl = shop.url();
    let sendBack = '';
    await context.route('**/api/ids/warenkoerbe/*/senden', async route => {
        const response = await request.post(route.request().url(), { headers: { Origin: baseURL! } });
        expect(response.status()).toBe(200);
        const metadata = await response.json();
        expect(metadata.fields.action).toBe('WKS');
        expect(metadata.fields.warenkorb).toContain('<Qty>3</Qty>');
        expect(metadata.fields.warenkorb).toContain('<PriceBasis>100</PriceBasis>');
        expect(metadata.fields.warenkorb).toContain('<PartNo>B-');
        sendBack = metadata.fields.hookurl;
        await route.fulfill({ json: { ...metadata, action: `${baseURL}/ids-send-dummy-shop` } });
    });
    await context.route('**/ids-send-dummy-shop', async route => {
        expect(route.request().postData()).toContain('WKS');
        const ordered = cart.replace('Warenkorbrückgabe', 'Warenkorbrückgabe mit Bestellung');
        await route.fulfill({ contentType: 'text/html; charset=utf-8', body: `<meta charset="utf-8"><form action="${sendBack}" method="post" enctype="multipart/form-data"><textarea name="warenkorb">${ordered.replaceAll('<', '&lt;')}</textarea><button>Testbestellung zurückgeben</button></form>` });
    });
    await expect(shop.getByRole('button', { name: 'Bei Würth bestellen' })).toBeInViewport();
    expect(await shop.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    const sendPromise = context.waitForEvent('page');
    await shop.getByRole('button', { name: 'Bei Würth bestellen' }).click();
    const checkout = await sendPromise;
    await checkout.getByRole('button', { name: 'Testbestellung zurückgeben' }).click();
    // The ordered cart returns to its project; the entry there is marked as ordered.
    await expect(checkout).toHaveURL(new RegExp(`/bestellungen/bedarf/projekt/101\\?warenkorb=${cartId}$`));
    await expect(checkout.getByRole('region', { name: 'Geparkte Shop-Warenkörbe' }).getByRole('link', { name: /Würth · B-/ })).toContainText('Bestellt');
    await expect(checkout.getByText(/^Warenkorb B-.+ wurde bei Würth bestellt\.$/)).toBeVisible();
    await checkout.goto(reviewUrl);
    await expect(checkout.getByText('Bei Würth bestellt', { exact: true })).toBeVisible();
    await expect(checkout.getByRole('button', { name: 'Bei Würth bestellen' })).toHaveCount(0);
    const openNeeds = await (await request.get('/api/bestellungen/offen')).json();
    expect(openNeeds.some((row: { externeArtikelnummer: string }) => row.externeArtikelnummer === 'DEMO-E2E')).toBe(false);
    await checkout.screenshot({ path: `/tmp/en1090-original-screenshots/ids-ordered-${testInfo.project.name}.png`, fullPage: true });
    await checkout.close();
    await shop.close();
});

test('IDS-Einstellungen: Zugang speichern, maskiert neu laden und leeres Passwort erhalten', async ({ page, request, baseURL }, testInfo) => {
    expect(baseURL).toBe(isolatedBaseUrl);
    await page.goto('/einstellungen#ids');
    await expect(page.getByRole('tab', { name: 'IDS-Schnittstellen', exact: true })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByLabel('Passwort', { exact: true })).toHaveValue('********');
    await page.getByLabel('Kundennummer', { exact: true }).fill('000123');
    await page.getByLabel('Login-Name', { exact: true }).fill('dummy');
    await page.getByLabel('Passwort', { exact: true }).fill('dummy-only');
    await page.getByLabel('Notizen (optional)').fill(`Test ${testInfo.project.name}`);
    await page.getByRole('button', { name: 'Schnittstelle speichern', exact: true }).click();
    await expect(page.getByText('Schnittstelle gespeichert.', { exact: true })).toBeVisible();
    const saved = await (await request.get('/api/admin/lieferanten/203/ids-konfig')).json();
    expect(saved).toMatchObject({ kundennummer: '000123', passwort: '********' });
    await page.reload();
    await expect(page.getByLabel('Kundennummer', { exact: true })).toHaveValue('000123');
    await expect(page.getByLabel('Passwort', { exact: true })).toHaveValue('********');
    await page.getByLabel('Passwort', { exact: true }).fill('');
    await page.getByRole('button', { name: 'Schnittstelle speichern', exact: true }).click();
    await expect(page.getByText('Schnittstelle gespeichert.', { exact: true })).toBeVisible();
    const start = await request.post('/api/ids/punchout/203/start', { headers: { Origin: baseURL! } });
    expect((await start.json()).fields.pw_kunde === 'dummy-only').toBe(true);
    await expect(page.getByRole('button', { name: 'Gespeicherten Zugang testen' })).toBeVisible();
    await page.screenshot({ path: `/tmp/en1090-original-screenshots/ids-settings-${testInfo.project.name}.png`, fullPage: true });
    await page.getByLabel('Kundennummer', { exact: true }).fill('');
    await page.getByRole('button', { name: 'Schnittstelle speichern', exact: true }).click();
    await expect(page.getByText(/Bitte Würth-URL, IDS-Protokoll und Zugangsdaten prüfen/)).toBeVisible();
    expect((await (await request.get('/api/admin/lieferanten/203/ids-konfig')).json()).kundennummer).toBe('000123');
});

test('IDS: Ein fehlender Warenkorb darf keinen vorherigen Warenkorb bestellen', async ({ page, context }) => {
    const dummy = { id: 'test-a', number: 'B-TEST-A', ordered: false, reference: '', items: [{ article: 'A', name: 'Testartikel A', quantity: 1, unit: 'Stück', netPrice: null, priceBasis: 1 }] };
    await context.route('**/api/ids/warenkoerbe/test-a', route => route.fulfill({ json: dummy }));
    await context.route('**/api/ids/warenkoerbe/test-b', route => route.fulfill({ status: 404, json: { message: 'Nicht gefunden' } }));
    await context.route('**/api/ids/warenkoerbe', route => route.fulfill({ json: [{ ...dummy, id: 'test-b', number: 'B-TEST-B' }] }));
    await page.goto('/bestellungen/ids/test-a');
    await expect(page.getByRole('button', { name: 'Bei Würth bestellen' })).toBeVisible();
    await page.getByRole('link', { name: 'Alle Shop-Warenkörbe' }).click();
    await page.getByRole('link', { name: /Würth · B-TEST-B/ }).click();
    await expect(page.getByRole('main').getByRole('alert')).toContainText('Würth-Warenkorb konnte nicht geladen werden.');
    await expect(page.getByRole('button', { name: 'Bei Würth bestellen' })).toHaveCount(0);
    await expect(page.getByText('Testartikel A', { exact: true })).toHaveCount(0);
});
