import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test('Einkauf-Navigation führt zu den vorhandenen Bereichen und fällige Anfragen bleiben erst Vorschau', async ({ page }, testInfo) => {
  const requests: string[] = [];
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url()); requests.push(`${route.request().method()} ${url.pathname}${url.search}`);
    if (url.pathname === '/api/auth/me') return route.fulfill({ json: { id: 9, username: 'max.mustermann', displayName: 'Max Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false } });
    if (url.pathname === '/api/notifications/summary') return route.fulfill({ json: { totalCount: 0, categories: [], recentItems: [] } });
    if (url.pathname === '/api/auth/me') return route.fulfill({ json: { id: 9 } });
    if (url.pathname === '/api/einkauf/faelligkeiten') return route.fulfill({ json: { content: [
      { typ: 'ANFRAGE_ANTWORTFRIST', vorgangId: 81, nummer: 'PA-81', beteiligungId: 83, frist: null, zustaendigId: 9, hinweis: 'Termin klären' },
      { typ: 'BESTELLBESTAETIGUNG', vorgangId: 22, nummer: 'B-22', beteiligungId: null, frist: '2026-09-23', zustaendigId: 9, hinweis: 'Auftragsbestätigung fehlt' },
      { typ: 'LIEFERTERMIN', vorgangId: 23, nummer: 'B-23', beteiligungId: null, frist: '2026-09-24', zustaendigId: 9, hinweis: 'Liefertermin überschritten' },
      { typ: 'ZEUGNIS', vorgangId: 24, nummer: 'B-24 / Zeugnis 3.1', beteiligungId: null, frist: '2026-09-24', zustaendigId: 9, hinweis: 'Charge fehlt' },
    ], totalPages: 1, totalElements: 4, number: 0 } });
    if (url.pathname === '/api/einkauf/faelligkeiten/nachfrage') return route.fulfill({ json: { typ: 'ANFRAGE_ANTWORTFRIST', vorgangId: 81, beteiligungId: 83, vorlageId: 3, vorlageVersion: 2, empfaenger: 'lieferant@example.test', subject: 'Rückmeldung zu PA-81', htmlBody: '<p>Bitte antworten.</p>', fehlendeNachweise: [] } });
    if (url.pathname === '/api/einkauf/anfragen/81') return route.fulfill({ json: { kopf: { id: 81, paNummer: 'PA-81', version: 0, status: 'VERSENDET', aktuelleRevisionId: 111, revisionsNummer: 1, projektIds: [], antworten: 0, lieferantenAnzahl: 1 }, positionen: [], lieferanten: [], angezeigteRevisionId: 111, historisch: false } });
    if (url.pathname === '/api/einkauf/anfragen/81/revisionen') return route.fulfill({ json: [{ id: 111, nummer: 1, status: 'VERSENDET' }] });
    if (url.pathname === '/api/einkauf/anfragen/81/angebote') return route.fulfill({ json: [] });
    if (url.pathname === '/api/einkauf/anfragen/81/revisionen/111/versandstatus') return route.fulfill({ json: [] });
    return route.abort();
  });
  await page.goto('/einkauf/faelligkeiten');
  await expect(page.getByRole('heading', { name: 'DAS IST FÄLLIG' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Termin klären' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Charge fehlt' })).toBeVisible();
  const filter = page.getByRole('combobox', { name: 'Zuständigkeitsfilter' });
  await filter.click();
  await page.getByRole('option', { name: 'Meine Vorgänge' }).click();
  await expect.poll(() => requests.some(item => item.includes('zustaendigId=9'))).toBe(true);
  const prepare = page.getByRole('button', { name: 'Nachfrage vorbereiten' }).first();
  await designPruefung(page, testInfo, 'einkauf-faelligkeiten', { primaerAktion: prepare });
  await prepare.click();
  await expect.poll(() => requests.some(item => item.includes('/api/einkauf/faelligkeiten/nachfrage'))).toBe(true);
  const preview = page.getByRole('dialog').filter({ hasText: 'Diese Vorschau sendet nichts' });
  await expect(preview).toContainText('lieferant@example.test');
  await expect(preview).toContainText('Diese Vorschau sendet nichts');
  await designPruefung(page, testInfo, 'einkauf-faelligkeiten-vorschau', { primaerAktion: preview.getByRole('link', { name: 'Vorgang und Verlauf öffnen' }) });
  expect(requests.some(item => item.includes('/senden') || item.includes('/antworten'))).toBe(false);
  await preview.getByRole('link', { name: 'Vorgang und Verlauf öffnen' }).click();
  await expect(page).toHaveURL(/\/einkaufsanfragen\/81$/);
  await expect(page.getByRole('heading', { name: /PA-81/ })).toBeVisible();
});
