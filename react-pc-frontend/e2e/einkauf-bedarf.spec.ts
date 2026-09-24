import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test('Bedarf speichern, nach Reload wiederfinden und Teilmenge für eine Anfrage vorbereiten', async ({ page }, testInfo) => {
  type AnfrageBody = { position: { basis: { menge: number } }; liefergruppe: { projektId: number | null } };
  type AnfrageCallBody = { positionen: Array<{ bedarfId: number; version: number; menge: number }> };
  const requests: { path: string; method: string; body: unknown }[] = [];
  const bedarfe: ReturnType<typeof need>[] = [];
  const need = (id: number, version: number, menge: number) => ({ id, version,
    position: { art: 'ARTIKEL', artikelId: null, interneReferenz: 'MAT-10', bezeichnung: 'Stahlprofil 40 mm', basis: { menge, einheit: 'STUECK', stueckzahl: menge, einzelLaengeMm: null, kgJeMeter: null, faktorQuelle: null }, dokumente: [], anlageVersionIds: [] },
    liefergruppe: { projektId: 19, lieferadresse: null, bedarfstermin: null, lagerzweck: null },
    mengen: { bedarf: menge, lagergedeckt: 0, angefragt: 0, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: menge, disponierbar: menge }, nachpflegeErforderlich: false, historischerHinweis: null });
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url()); const method = route.request().method(); let body: unknown = null;
    try { body = route.request().postDataJSON(); } catch { body = null; }
    requests.push({ path: url.pathname, method, body });
    if (url.pathname === '/api/auth/me') return route.fulfill({ json: { id: 9, username: 'max.mustermann', displayName: 'Max Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false } });
    if (url.pathname === '/api/notifications/summary') return route.fulfill({ json: { totalCount: 0, categories: [], recentItems: [] } });
    if (url.pathname === '/api/einkauf/berechtigungen') return route.fulfill({ json: { rechte: ['LESEN', 'BEARBEITEN', 'ANFRAGE_SENDEN'] } });
    if (url.pathname === '/api/projekte/simple') return route.fulfill({ json: [{ id: 19, bauvorhaben: 'Balkonanlage Musterstraße', auftragsnummer: 'A-2026-019', kunde: 'Mustermann GmbH', abgeschlossen: false }] });
    if (url.pathname === '/api/einkauf/bedarf' && method === 'GET') return route.fulfill({ json: { content: bedarfe, totalPages: 1, totalElements: bedarfe.length, number: 0, size: 20, empty: bedarfe.length === 0 } });
    if (url.pathname === '/api/einkauf/bedarf' && method === 'POST') { const row = need(44, 0, (body as AnfrageBody).position.basis.menge); bedarfe.push(row); return route.fulfill({ status: 201, json: row }); }
    if (url.pathname === '/api/einkauf/anfragen' && method === 'POST') return route.fulfill({ status: 201, json: { kopf: { id: 81, version: 0, paNummer: 'PA-81', zustaendigId: 9, aktuelleRevisionId: 111, revisionsNummer: 1, status: 'ENTWURF', antwortfrist: null, liefertermin: null, projektIds: [19], antworten: 0, lieferantenAnzahl: 0 }, positionen: [], lieferanten: [], angezeigteRevisionId: 111, historisch: false } });
    if (url.pathname === '/api/einkauf/anfragen/81') return route.fulfill({ json: { kopf: { id: 81, version: 0, paNummer: 'PA-81', zustaendigId: 9, aktuelleRevisionId: 111, revisionsNummer: 1, status: 'ENTWURF', antwortfrist: null, liefertermin: null, projektIds: [19], antworten: 0, lieferantenAnzahl: 0 }, positionen: [], lieferanten: [], angezeigteRevisionId: 111, historisch: false } });
    if (url.pathname === '/api/einkauf/anfragen/81/revisionen') return route.fulfill({ json: [{ id: 111, nummer: 1, status: 'ENTWURF' }] });
    if (url.pathname === '/api/einkauf/anfragen/81/angebote') return route.fulfill({ json: [] });
    return route.abort();
  });

  await page.goto('/bestellungen/bedarf');
  await expect(page.getByRole('heading', { name: 'BEDARF' })).toBeVisible();
  await page.getByRole('button', { name: 'Bedarf erfassen' }).click();
  await page.getByLabel('Bezeichnung').fill('Stahlprofil 40 mm');
  await page.getByLabel('Interne Nummer').fill('MAT-10');
  await page.getByLabel('Menge').fill('10');
  await page.getByRole('button', { name: 'Projekt auswählen' }).click();
  const projektDialog = page.locator('.fixed.inset-0').filter({ hasText: 'Projekt auswählen' }).last();
  await expect(projektDialog).toBeVisible();
  await projektDialog.getByRole('button', { name: /Balkonanlage Musterstraße/ }).click();
  await page.getByRole('button', { name: 'Bedarf speichern' }).click();
  await expect(page.getByText('Stahlprofil 40 mm')).toBeVisible();
  await designPruefung(page, testInfo, 'einkauf-bedarf-projekt-und-listenansicht');
  expect((requests.find(item => item.path === '/api/einkauf/bedarf' && item.method === 'POST')?.body as AnfrageBody).liefergruppe.projektId).toBe(19);
  await page.reload();
  await expect(page.getByText('Stahlprofil 40 mm')).toBeVisible();
  await page.getByLabel('Bedarf MAT-10 auswählen').check();
  await page.getByLabel('Anfragemenge MAT-10').fill('4');
  await designPruefung(page, testInfo, 'einkauf-bedarf-teilmenge', { primaerAktion: page.getByRole('button', { name: /Angebote einholen/ }) });
  await page.getByRole('button', { name: /Angebote einholen/ }).click();
  await expect(page).toHaveURL(/\/einkaufsanfragen\/81$/);
  expect((requests.find(item => item.path === '/api/einkauf/anfragen' && item.method === 'POST')?.body as AnfrageCallBody).positionen).toEqual([{ bedarfId: 44, version: 0, menge: 4 }]);
  expect(requests.some(item => item.path.endsWith('/senden') || item.path.endsWith('/freigeben'))).toBe(false);
});
