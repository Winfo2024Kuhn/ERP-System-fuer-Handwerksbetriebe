import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test('HiCAD-Vorschau übernimmt nur bestätigte Restmengen und Lagerentnahme erst nach Bestätigung', async ({ page }, testInfo) => {
  type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue };
  type ImportBody = { version: number; zeilen: Array<{ zeilennummer: number }> };
  const requests: { path: string; method: string; body: JsonValue }[] = [];
  let open = 10; let entnahmen = 0; let hicadRuns = 0;
  const bedarf = () => ({ id: 6, version: 4, position: { art: 'ARTIKEL', artikelId: null, interneReferenz: 'MAT-6', bezeichnung: 'Stahlprofil', basis: { menge: 10, einheit: 'STUECK' }, dokumente: [], anlageVersionIds: [] }, liefergruppe: { projektId: 19, lieferadresse: null, bedarfstermin: null, lagerzweck: null }, mengen: { bedarf: 10, lagergedeckt: 0, angefragt: 0, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: open, disponierbar: open }, nachpflegeErforderlich: false, historischerHinweis: null });
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url()); const method = route.request().method(); let body: JsonValue = null;
    try { body = route.request().postDataJSON() as JsonValue; } catch { body = null; }
    requests.push({ path: url.pathname, method, body });
    if (url.pathname === '/api/auth/me') return route.fulfill({ json: { id: 9, username: 'max.mustermann', displayName: 'Max Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false } });
    if (url.pathname === '/api/notifications/summary') return route.fulfill({ json: { totalCount: 0, categories: [], recentItems: [] } });
    if (url.pathname === '/api/einkauf/berechtigungen') return route.fulfill({ json: { rechte: ['LESEN', 'BEARBEITEN'] } });
    if (url.pathname === '/api/projekte/simple') return route.fulfill({ json: [{ id: 19, bauvorhaben: 'Werkstatt Muster', auftragsnummer: 'A-019', kunde: 'Mustermann GmbH', abgeschlossen: false }] });
    if (url.pathname === '/api/einkauf/bedarf') return route.fulfill({ json: { content: [bedarf()], totalPages: 1, totalElements: 1, number: 0, size: 20 } });
    if (url.pathname === '/api/einkauf/hicad/vorschau') { hicadRuns++; return route.fulfill({ status: 201, json: { id: 3, dateiHash: 'hash-test', dateiSchonImportiert: hicadRuns > 1, zeilen: [
      { zeilennummer: 1, rohtext: 'Profil 40x40;10', vorschlag: { art: 'ARTIKEL', bezeichnung: 'Profil 40x40', basis: { menge: 10, einheit: 'STUECK' }, dokumente: [], anlageVersionIds: [] }, artikelKandidaten: [], bereitsUebernommen: hicadRuns > 1, hinweise: [], bilder: [] },
      { zeilennummer: 2, rohtext: 'Blech;5', vorschlag: { art: 'ARTIKEL', bezeichnung: 'Blech', basis: { menge: 5, einheit: 'STUECK' }, dokumente: [], anlageVersionIds: [] }, artikelKandidaten: [], bereitsUebernommen: true, hinweise: ['Bereits teilweise übernommen'], bilder: [] },
    ] } }); }
    if (url.pathname === '/api/einkauf/hicad/3') return route.fulfill({ json: { id: 3, version: hicadRuns, duplikat: hicadRuns > 1, zeilen: [
      { zeilennummer: 1, gesamtmenge: 10, uebernommeneMenge: hicadRuns > 1 ? 10 : 0, verbleibendeMenge: hicadRuns > 1 ? 0 : 10, vollstaendigUebernommen: hicadRuns > 1 },
      { zeilennummer: 2, gesamtmenge: 5, uebernommeneMenge: 2, verbleibendeMenge: 3, vollstaendigUebernommen: false },
    ] } });
    if (url.pathname.endsWith('/uebernehmen')) return route.fulfill({ json: [] });
    if (url.pathname === '/api/einkauf/lagerentnahmen' && method === 'POST') { entnahmen++; if (entnahmen === 1) return route.fulfill({ status: 409, json: { message: 'Der Bedarf wurde zwischenzeitlich geändert.' } }); open = 7; return route.fulfill({ status: 201, json: { id: 1, offenerBedarf: 7 } }); }
    return route.abort();
  });

  await page.goto('/bestellungen/bedarf');
  await page.getByRole('button', { name: 'HiCAD importieren' }).click();
  await page.getByRole('button', { name: 'Projekt auswählen' }).click();
  await page.getByRole('button', { name: /Werkstatt Muster/ }).click();
  await page.getByLabel('HiCAD-Datei').setInputFiles({ name: 'material.sza', mimeType: 'application/octet-stream', buffer: Buffer.from('Profil') });
  await page.getByRole('button', { name: 'Vorschau laden' }).click();
  await expect(page.getByRole('heading', { name: /Profil 40x40/ })).toBeVisible();
  await designPruefung(page, testInfo, 'einkauf-hicad-vorschau', { primaerAktion: page.getByRole('button', { name: 'Ausgewählte Zeilen übernehmen' }) });
  await page.getByLabel('Zeile 1 übernehmen').check();
  await page.getByRole('button', { name: 'Ausgewählte Zeilen übernehmen' }).click();
  const importCall = requests.find(item => item.path.endsWith('/uebernehmen'));
  expect((importCall?.body as ImportBody).zeilen.map(row => row.zeilennummer)).toEqual([1]);
  expect((importCall?.body as ImportBody).version).toBe(1);

  await page.getByRole('button', { name: 'Lagerentnahme erfassen MAT-6' }).click();
  await page.getByLabel('Entnommene Menge').fill('3');
  expect(requests.some(item => item.path === '/api/einkauf/lagerentnahmen')).toBe(false);
  await page.getByRole('button', { name: 'Entnahme bestätigen' }).click();
  await expect(page.getByRole('dialog').getByText(/zwischenzeitlich geändert/)).toBeVisible();
  await expect(page.getByText(/offen 10/)).toBeVisible();
  await page.getByRole('button', { name: 'Entnahme bestätigen' }).click();
  await expect(page.locator('article').filter({ hasText: 'MAT-6' })).toContainText('7');
});

test('HiCAD-Dateigrenze wird vor dem Upload geprüft', async ({ page }, testInfo) => {
  await page.route('**/api/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/auth/me') return route.fulfill({ json: { id: 9, username: 'max.mustermann', displayName: 'Max Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false } });
    if (path === '/api/notifications/summary') return route.fulfill({ json: { totalCount: 0, categories: [], recentItems: [] } });
    if (path === '/api/einkauf/berechtigungen') return route.fulfill({ json: { rechte: ['LESEN', 'BEARBEITEN'] } });
    if (path === '/api/einkauf/bedarf') return route.fulfill({ json: { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 } });
    return route.abort();
  });
  await page.goto('/bestellungen/bedarf');
  await page.getByRole('button', { name: 'HiCAD importieren' }).click();
  await page.getByLabel('HiCAD-Datei').setInputFiles({ name: 'zu-gross.sza', mimeType: 'application/octet-stream', buffer: Buffer.alloc(10 * 1024 * 1024 + 1) });
  await expect(page.getByRole('alert')).toContainText('höchstens 10 MiB');
  await designPruefung(page, testInfo, 'einkauf-hicad-dateigrenze');
});
