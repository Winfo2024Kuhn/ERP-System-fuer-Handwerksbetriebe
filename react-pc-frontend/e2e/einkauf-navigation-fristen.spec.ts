import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test('Einkauf-Navigation führt zu den vorhandenen Bereichen und fällige Anfragen bleiben erst Vorschau', async ({ page: seite }, prüfinformationen) => {
  const requests: string[] = [];
  await seite.route('**/api/**', async route => {
    const adresse = new URL(route.request().url()); requests.push(`${route.request().method()} ${adresse.pathname}${adresse.search}`);
    if (adresse.pathname === '/api/auth/me') return route.fulfill({ json: { id: 9, username: 'max.mustermann', displayName: 'Max Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false } });
    if (adresse.pathname === '/api/notifications/summary') return route.fulfill({ json: { totalCount: 0, categories: [], recentItems: [] } });
    if (adresse.pathname === '/api/einkauf/berechtigungen') return route.fulfill({ json: ['LESEN', 'BEARBEITEN'] });
    if (adresse.pathname === '/api/projekte/simple') return route.fulfill({ json: [] });
    if (adresse.pathname === '/api/auth/me') return route.fulfill({ json: { id: 9 } });
    if (adresse.pathname === '/api/einkauf/faelligkeiten') return route.fulfill({ json: { content: [
      { typ: 'ANFRAGE_ANTWORTFRIST', vorgangId: 81, nummer: 'PA-81', beteiligungId: 83, frist: null, zustaendigId: 9, hinweis: 'Termin klären' },
      { typ: 'BESTELLBESTAETIGUNG', vorgangId: 22, nummer: 'B-22', beteiligungId: null, frist: '2026-09-23', zustaendigId: 9, hinweis: 'Auftragsbestätigung fehlt' },
      { typ: 'LIEFERTERMIN', vorgangId: 23, nummer: 'B-23', beteiligungId: null, frist: '2026-09-24', zustaendigId: 9, hinweis: 'Liefertermin überschritten' },
      { typ: 'ZEUGNIS', vorgangId: 24, nummer: 'B-24 / Zeugnis 3.1', beteiligungId: null, frist: '2026-09-24', zustaendigId: 9, hinweis: 'Charge fehlt' },
    ], totalPages: 1, totalElements: 4, number: 0 } });
    if (adresse.pathname === '/api/einkauf/faelligkeiten/nachfrage') return route.fulfill({ json: { typ: 'ANFRAGE_ANTWORTFRIST', vorgangId: 81, beteiligungId: 83, vorlageId: 3, vorlageVersion: 2, empfaenger: 'lieferant@example.test', subject: 'Rückmeldung zu PA-81', htmlBody: '<p>Bitte antworten.</p>', fehlendeNachweise: [] } });
    if (adresse.pathname === '/api/einkauf/anfragen/81') return route.fulfill({ json: { kopf: { id: 81, paNummer: 'PA-81', version: 0, status: 'VERSENDET', aktuelleRevisionId: 111, revisionsNummer: 1, projektIds: [], antworten: 0, lieferantenAnzahl: 1 }, positionen: [], lieferanten: [], angezeigteRevisionId: 111, historisch: false } });
    if (adresse.pathname === '/api/einkauf/anfragen/81/revisionen') return route.fulfill({ json: [{ id: 111, nummer: 1, status: 'VERSENDET' }] });
    if (adresse.pathname === '/api/einkauf/anfragen/81/angebote') return route.fulfill({ json: [] });
    if (adresse.pathname === '/api/einkauf/anfragen/81/revisionen/111/versandstatus') return route.fulfill({ json: [] });
    return route.abort();
  });
  await seite.goto('/einkauf/faelligkeiten');
  await expect(seite.getByRole('heading', { name: 'DAS IST FÄLLIG' })).toBeVisible();
  await expect(seite.getByRole('heading', { name: 'Termin klären' })).toBeVisible();
  await expect(seite.getByRole('heading', { name: 'Charge fehlt' })).toBeVisible();
  const filter = seite.getByRole('combobox', { name: 'Zuständigkeitsfilter' });
  await filter.click();
  await seite.getByRole('option', { name: 'Meine Vorgänge' }).click();
  await expect.poll(() => requests.some(eintrag => eintrag.includes('zustaendigId=9'))).toBe(true);
  const prepare = seite.getByRole('button', { name: 'Nachfrage vorbereiten' }).first();
  await designPruefung(seite, prüfinformationen, 'einkauf-faelligkeiten', { primaerAktion: prepare });
  await prepare.click();
  await expect.poll(() => requests.some(eintrag => eintrag.includes('/api/einkauf/faelligkeiten/nachfrage'))).toBe(true);
  const preview = seite.getByRole('dialog').filter({ hasText: 'Diese Vorschau sendet nichts' });
  await expect(preview).toContainText('lieferant@example.test');
  await expect(preview).toContainText('Diese Vorschau sendet nichts');
  await designPruefung(seite, prüfinformationen, 'einkauf-faelligkeiten-vorschau', { primaerAktion: preview.getByRole('link', { name: 'Vorgang und Verlauf öffnen' }) });
  expect(requests.some(eintrag => eintrag.includes('/senden') || eintrag.includes('/antworten'))).toBe(false);
  await preview.getByRole('link', { name: 'Vorgang und Verlauf öffnen' }).click();
  await expect(seite).toHaveURL(/\/einkaufsanfragen\/81$/);
  await expect(seite.getByRole('heading', { name: /PA-81/ })).toBeVisible();
});
