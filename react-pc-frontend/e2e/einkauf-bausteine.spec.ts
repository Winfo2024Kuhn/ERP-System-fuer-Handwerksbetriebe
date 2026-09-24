import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test('Artikel auswählen, Menge eingeben und validierte Position übernehmen', async ({ page }, testInfo) => {
  const fremdeAntworten: string[] = [];
  page.on('response', response => {
    if (!['localhost', '127.0.0.1'].includes(new URL(response.url()).hostname)) fremdeAntworten.push(response.url());
  });
  await page.route('**/api/artikel**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/artikel' && route.request().method() === 'POST') {
      const payload = route.request().postDataJSON();
      await route.fulfill({ json: { id: 92, ...payload, preis: null } });
      return;
    }
    if (url.pathname.endsWith('/werkstoffe')) return route.fulfill({ json: ['S235JR'] });
    if (url.pathname.endsWith('/filteroptionen')) return route.fulfill({ json: { herstellverfahren: [], fertigungszustand: [] } });
    return route.fulfill({ json: { artikel: [{ id: 88, artikelnummer: 'MAT-0088', produktname: 'Schraube M8', werkstoffName: 'Stahl', abmessung: 'M8 × 40' }], gesamt: 1 } });
  });
  await page.goto('/e2e/harness/einkauf-bausteine.html');
  await expect(page.getByRole('heading', { name: 'POSITION ERFASSEN' })).toBeVisible();
  await page.getByRole('button', { name: 'Artikel auswählen' }).click();
  await expect(page.getByRole('dialog', { name: 'Artikel auswählen' })).toBeVisible();
  await page.getByRole('button', { name: 'Schraube M8 an- oder abwählen' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.getByLabel('Interne Artikelnummer')).toHaveValue('MAT-0088');
  await page.getByRole('combobox', { name: 'Einheit' }).click();
  await page.getByRole('option', { name: 'Meter', exact: true }).click();
  const menge = page.getByLabel('Menge *');
  await menge.fill('2,5');
  await page.getByRole('button', { name: 'Materialangaben übernehmen' }).click();
  await expect(page.getByText('Materialangaben übernommen.', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Übernommene Position')).toContainText('"menge":2.5');
  expect(fremdeAntworten).toEqual([]);
  await designPruefung(page, testInfo, 'einkauf-bausteine', { primaerAktion: page.getByRole('button', { name: 'Materialangaben übernehmen' }) });
});

test('neu angelegten Artikel ohne bekannten Preis direkt als Materialposition übernehmen', async ({ page }) => {
  await page.route('**/api/artikel**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname === '/api/artikel' && route.request().method() === 'POST') {
      const payload = route.request().postDataJSON();
      expect(payload).not.toHaveProperty('preis');
      await route.fulfill({ json: { id: 92, ...payload, preis: null } });
      return;
    }
    if (url.pathname.endsWith('/werkstoffe/details')) return route.fulfill({ json: [] });
    return route.fulfill({ json: [] });
  });
  await page.goto('/e2e/harness/einkauf-bausteine.html');
  await page.getByRole('button', { name: 'Artikel neu anlegen' }).click();
  await page.getByPlaceholder('z.B. Quadratrohr 40x40x2').fill('Quadratrohr');
  await page.getByRole('button', { name: 'Artikel anlegen' }).click();
  await expect(page.getByText('Artikel aus dem Stamm ausgewählt', { exact: false })).toBeVisible();
  await page.getByLabel('Menge *').fill('1');
  await page.getByRole('button', { name: 'Materialangaben übernehmen' }).click();
  await expect(page.getByLabel('Übernommene Position')).toContainText('"artikelId":92');
});


test('Zeichnungsteil mit freigegebener Dateiversion und konsistenter Zuschnittmenge übernehmen', async ({ page }, testInfo) => {
  await page.goto('/e2e/harness/einkauf-bausteine.html');
  await page.getByRole('combobox', { name: 'Positionsart' }).click();
  await page.getByRole('option', { name: 'Zeichnungsteil' }).click();
  await page.getByLabel('Bezeichnung *').fill('Dummy Träger');
  await page.getByLabel('Projektkennung *').fill('DUMMY-ZT');
  await page.getByLabel('Zeichnungsnummer').fill('Z-17');
  await page.getByLabel('Zeichnungsrevision').fill('B');
  await page.getByRole('combobox', { name: 'Einheit', exact: true }).click();
  await page.getByRole('option', { name: 'Meter', exact: true }).click();
  await page.getByLabel('Menge *').fill('5');
  await page.getByLabel('Stückzahl', { exact: true }).fill('2');
  await page.getByLabel('Einzellänge in mm').fill('1250');
  await page.getByRole('combobox', { name: 'Dateiversion auswählen' }).click();
  await page.getByRole('option', { name: 'Traeger.pdf · Revision B · Freigegeben' }).click();
  await page.getByRole('button', { name: 'Version hinzufügen' }).click();
  await page.getByRole('button', { name: 'Materialangaben übernehmen' }).click();
  await expect(page.getByText('Stückzahl und Einzellänge ergeben 2,5 m.', { exact: false })).toBeVisible();
  await expect(page.getByLabel('Übernommene Position')).toHaveCount(0);
  await page.getByLabel('Menge *').fill('2,5');
  await page.getByRole('button', { name: 'Materialangaben übernehmen' }).click();
  await expect(page.getByLabel('Übernommene Position')).toContainText('"art":"ZEICHNUNGSTEIL"');
  await expect(page.getByLabel('Übernommene Position')).toContainText('"menge":2.5');
  await expect(page.getByLabel('Übernommene Position')).toContainText('"anlageVersionIds":[41]');
  await page.evaluate(() => window.scrollTo(0, 0));
  await designPruefung(page, testInfo, 'einkauf-zeichnungsteil', { primaerAktion: page.getByRole('button', { name: 'Materialangaben übernehmen' }) });
});

test('Lieferant bei unbekanntem Preis an die Artikelanlage übergeben', async ({ page }) => {
  const creates: Record<string, unknown>[] = [];
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/lieferanten') return route.fulfill({ json: { lieferanten: [{ id: 7, lieferantenname: 'Dummy Lieferant' }] } });
    if (path === '/api/artikel' && route.request().method() === 'POST') {
      const body = route.request().postDataJSON(); creates.push(body);
      return route.fulfill({ json: { id: 92, ...body, preis: null } });
    }
    return route.fulfill({ json: [] });
  });
  await page.goto('/e2e/harness/einkauf-bausteine.html');
  await page.getByRole('button', { name: 'Artikel neu anlegen' }).click();
  await page.getByPlaceholder('z.B. Quadratrohr 40x40x2').fill('Dummy Profil');
  await page.getByText('Lieferant wählen', { exact: true }).click();
  await page.getByRole('button', { name: 'Dummy Lieferant', exact: true }).click();
  await page.getByRole('button', { name: 'Artikel anlegen' }).click();
  await expect.poll(() => creates.length).toBe(1);
  expect(creates[0]).toMatchObject({ lieferantId: 7, externeArtikelnummer: '' });
  expect(creates[0]).not.toHaveProperty('preis');
});
