import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test('vergleicht vollständige Lieferantenangebote und erstellt nur für gewählte Fassung einen Bestellentwurf', async ({ page }, testInfo) => {
  const foreign: string[] = []; const orderPayloads: unknown[] = []; const manualPayloads: unknown[] = [];
  page.on('response', response => { if (!['localhost', '127.0.0.1'].includes(new URL(response.url()).hostname)) foreign.push(response.url()); });
  await page.route('**/api/auth/me', route => route.fulfill({ json: { id: 1, displayName: 'Max Mustermann', username: 'test@example.com', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false } }));
  await page.route('**/api/notifications/summary', route => route.fulfill({ json: {} }));
  await page.route('**/api/einkauf/anfragen/51/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/angebote')) return route.fulfill({ json: [
      { lieferantId: 1, lieferantenname: 'Stahl Nord', angebot: { id: 91, beteiligungId: 11, status: 'ANGEBOTEN', versionen: [{ id: 101, angebotId: 91, nummer: 1, version: 0, anfrageRevisionId: 10, status: 'GEPRUEFT', emailId: 88, angebotsnummer: 'A', datum: '2026-09-20', gueltigBis: '2026-10-20', waehrung: 'EUR', positionen: [{ id: 401, anfragePositionId: 31, angeboten: { menge: 2, einheit: 'STUECK' }, mindestmenge: null, verpackungseinheit: null, abweichungen: [], zeugnisse: [], kosten: [{ schluessel: 'GRUNDPREIS', art: 'MATERIAL', betrag: 530, basis: 'STUECK', basisMenge: 1 }] }], kosten: [], zahlungsbedingungen: null, skontoProzent: null, skontoTage: null }] } },
      { lieferantId: 2, lieferantenname: 'Stahl Süd', angebot: { id: 92, beteiligungId: 12, status: 'ANGEBOTEN', versionen: [{ id: 102, angebotId: 92, nummer: 1, version: 0, anfrageRevisionId: 10, status: 'GEPRUEFT', angebotsnummer: 'B', datum: '2026-09-20', gueltigBis: '2026-10-20', waehrung: 'EUR', positionen: [{ anfragePositionId: 31, angeboten: { menge: 2, einheit: 'STUECK' }, mindestmenge: null, verpackungseinheit: null, abweichungen: ['Legierung abweichend'], zeugnisse: [], kosten: [] }], kosten: [], zahlungsbedingungen: null, skontoProzent: 2, skontoTage: 10 }] } },
      { lieferantId: 3, lieferantenname: 'Stahl West', angebot: { id: 93, beteiligungId: 13, status: 'ANGEBOTEN', versionen: [{ id: 103, angebotId: 93, nummer: 1, version: 0, anfrageRevisionId: 10, status: 'ERFASST', angebotsnummer: 'C', datum: '2026-09-20', gueltigBis: '2026-10-20', waehrung: 'EUR', positionen: [], kosten: [], zahlungsbedingungen: null, skontoProzent: null, skontoTage: null }] } },
    ] });
    if (path.endsWith('/vergleich')) return route.fulfill({ json: { anfrageId: 51, stichtag: '2026-09-24', bestesAngebotVersionId: 101, angebote: [
      { angebotVersionId: 101, nettoGesamt: 1060, vollstaendig: true, technischGeeignet: true, gueltig: true, hindernisse: [], rechnung: [{ key: 'MATERIAL', formel: '530 × 2', basis: 530, ergebnis: 1060, quellenbezug: 'Angebot A: Preis pro Stück' }] },
      { angebotVersionId: 102, nettoGesamt: 1080, vollstaendig: true, technischGeeignet: true, gueltig: true, hindernisse: ['Legierung abweichend'], rechnung: [] },
      { angebotVersionId: 103, nettoGesamt: null, vollstaendig: false, technischGeeignet: false, gueltig: true, hindernisse: ['Preis fehlt', 'Zeugnis nicht lieferbar'], rechnung: [] },
    ] } });
    return route.fulfill({ json: { angezeigteRevisionId: 10, historisch: false, kopf: { id: 51, version: 1, paNummer: 'PA-51', aktuelleRevisionId: 10, revisionsNummer: 2, status: 'AUSSTEHEND', antwortfrist: '2026-10-01', liefertermin: '2026-10-14' }, positionen: [{ id: 31, snapshot: { art: 'ARTIKEL', artikelId: 701, interneReferenz: 'MAT-31', bezeichnung: 'Stahlblech', werkstoff: 'S235JR', basis: { menge: 2, einheit: 'STUECK' } }, herkuenfte: [{ bedarfId: 71, version: 3, menge: 2 }] }], lieferanten: [{ id: 11, lieferantId: 1, lieferantenname: 'Stahl Nord', status: 'ANGEBOTEN', version: 1, kontakt: null }, { id: 12, lieferantId: 2, lieferantenname: 'Stahl Süd', status: 'ANGEBOTEN', version: 1, kontakt: null }, { id: 13, lieferantId: 3, lieferantenname: 'Stahl West', status: 'ANGEBOTEN', version: 1, kontakt: null }] } });
  });
  await page.route('**/api/einkauf/anfragen/51', route => route.fulfill({ json: { angezeigteRevisionId: 10, historisch: false, kopf: { id: 51, version: 1, paNummer: 'PA-51', aktuelleRevisionId: 10, revisionsNummer: 2, status: 'AUSSTEHEND', antwortfrist: '2026-10-01', liefertermin: '2026-10-14' }, positionen: [{ id: 31, snapshot: { art: 'ARTIKEL', artikelId: 701, interneReferenz: 'MAT-31', bezeichnung: 'Stahlblech', werkstoff: 'S235JR', basis: { menge: 2, einheit: 'STUECK' } }, herkuenfte: [{ bedarfId: 71, version: 3, menge: 2 }] }], lieferanten: [{ id: 11, lieferantId: 1, lieferantenname: 'Stahl Nord', status: 'ANGEBOTEN', version: 1, kontakt: null }, { id: 12, lieferantId: 2, lieferantenname: 'Stahl Süd', status: 'ANGEBOTEN', version: 1, kontakt: null }, { id: 13, lieferantId: 3, lieferantenname: 'Stahl West', status: 'ANGEBOTEN', version: 1, kontakt: null }] } }));
  await page.route('**/api/einkauf/ANFRAGE/51/verlauf?**', route => route.fulfill({ json: { content: [], totalPages: 1 } }));
  await page.route('**/api/einkauf/anfragen/51/revisionen', route => route.fulfill({ json: [{ id: 10, nummer: 2, status: 'AUSSTEHEND' }] }));
  await page.route('**/api/einkauf/anfragen/51/revisionen/10/versandstatus', route => route.fulfill({ json: [] }));
  await page.route('**/api/einkauf/angebote/91/versionen', async route => { manualPayloads.push(route.request().postDataJSON()); await route.fulfill({ status: 201, json: { id: 104 } }); });
  await page.route('**/api/einkauf/bestellungen/aus-angebot', async route => { orderPayloads.push(route.request().postDataJSON()); await route.fulfill({ json: { id: 73, version: 0, nummer: 'B-2026-0073', lieferantId: 1, revisionen: [] } }); });
  await page.route('**/api/einkauf/bestellungen/73', route => route.fulfill({ json: { id: 73, version: 0, nummer: 'B-2026-0073', lieferantId: 1, anfrageRevisionId: 10, empfaenger: { lieferantenname: 'Stahl Nord' }, status: 'ENTWURF', lieferantenStatus: 'AUSSTEHEND', revisionen: [{ id: 1, nummer: 1, version: 0, snapshot: {}, sha256: 'hash', versandId: null, verworfen: false, angenommenAm: null, externerNachweis: null, positionen: [] }] } }));
  await page.route('**/api/einkauf/bestellungen?page=0&size=20', route => route.fulfill({ json: { content: [] } }));
  await page.route('**/api/einkauf/analysen?**', route => route.fulfill({ json: { id: 700 } }));
  await page.route('**/api/einkauf/analysen/700', route => route.fulfill({ json: { id: 700, angebotId: 91, status: 'ABGESCHLOSSEN' } }));
  await page.route('**/api/einkauf/analysen/700/vorschlaege', route => route.fulfill({ json: [{ feldpfad: 'angebotsnummer', wert: 'A-NEU', quelle: { emailId: 88, dateiId: null, seite: null, zitat: 'Angebot A-NEU' }, confidence: 0.96, hinweis: null }] }));
  await page.route('**/api/einkauf/angebote/91', route => route.fulfill({ json: { versionen: [{ id: 101, version: 0 }] } }));
  await page.route('**/api/einkauf/analysen/700/uebernehmen', route => route.fulfill({ json: {} }));
  await page.route('**/api/projekte/simple?**', route => route.fulfill({ json: [{ id: 78, auftragsnummer: 'P-78', bauvorhaben: 'Werkstatt Muster' }] }));
  await page.goto('/einkaufsanfragen/51');
  await expect(page.getByRole('button', { name: 'Angebot von Stahl Nord bearbeiten' })).toBeInViewport();
  await designPruefung(page, testInfo, 'einkauf-angebote-erstaufruf', { primaerAktion: page.getByRole('button', { name: 'Angebot von Stahl Nord bearbeiten' }) });
  await page.getByRole('button', { name: 'Angebot von Stahl Nord bearbeiten' }).click();
  await page.getByLabel('Angebotspreis MAT-31').fill('530');
  await page.getByRole('combobox', { name: 'Zeugniszusage MAT-31' }).click();
  await designPruefung(page, testInfo, 'einkauf-zeugniszusage-auswahl', { primaerAktion: page.getByRole('option', { name: 'Zeugnis 3.1', exact: true }) });
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('combobox', { name: 'Zeugniszusage MAT-31' })).toBeFocused();
  await designPruefung(page, testInfo, 'einkauf-angeboteditor', { primaerAktion: page.getByRole('button', { name: 'Angebot speichern' }) });
  await page.getByRole('button', { name: 'Angebot speichern' }).click();
  await expect.poll(() => manualPayloads.length).toBe(1);
  expect(manualPayloads[0]).toMatchObject({ anfrageRevisionId: 10, angebotsnummer: 'A', positionen: [{ anfragePositionId: 31, kosten: [{ betrag: 530, basis: 'STUECK' }] }] });
  await page.getByRole('button', { name: 'KI-Vorschläge prüfen · Stahl Nord' }).click();
  await page.getByRole('checkbox', { name: 'Vorschlag übernehmen angebotsnummer' }).check();
  await page.getByRole('button', { name: 'Auswahl übernehmen' }).scrollIntoViewIfNeeded();
  await designPruefung(page, testInfo, 'einkauf-ki-vorschlaege', { primaerAktion: page.getByRole('button', { name: 'Auswahl übernehmen' }) });
  await page.getByRole('button', { name: 'Auswahl übernehmen' }).click();
  await page.getByRole('button', { name: 'Preis übernehmen · MAT-31' }).click();
  await page.getByRole('combobox', { name: 'Geltungsbereich' }).click();
  await page.getByRole('option', { name: 'Nur für ein Projekt' }).click();
  await page.getByRole('button', { name: 'Projekt auswählen' }).click();
  await page.getByText('Werkstatt Muster', { exact: true }).click();
  await page.getByLabel('Begründung', { exact: true }).fill('Projektpreis geprüft');
  await designPruefung(page, testInfo, 'einkauf-preisuebernahme', { primaerAktion: page.getByRole('button', { name: 'Preis übernehmen', exact: true }) });
  await page.getByRole('button', { name: 'Abbrechen', exact: true }).click();
  await expect(page.getByText('1.060,00 €').first()).toBeVisible();
  await expect(page.getByText('1.080,00 €').first()).toBeVisible();
  await expect(page.getByText('Preis fehlt')).toBeVisible();
  await expect(page.getByText('Zeugnis nicht lieferbar')).toBeVisible();
  await expect(page.getByText(/Skonto 2%.*nur Hinweis/)).toBeVisible();
  await page.getByText('Rechenweg und Quellen', { exact: true }).first().click();
  await expect(page.getByText(/530 × 2 = 1.060,00 €/)).toBeVisible();
  expect(await page.getByRole('button', { name: /Als Bestellung vorbereiten · Fassung 1/ }).first().isEnabled()).toBe(true);
  await designPruefung(page, testInfo, 'einkauf-angebote', { primaerAktion: page.getByRole('button', { name: /Als Bestellung vorbereiten · Fassung 1/ }).first() });
  await page.getByRole('button', { name: /Als Bestellung vorbereiten · Fassung 1/ }).first().click();
  await expect(page).toHaveURL(/\/bestellungen\/73$/);
  expect(orderPayloads).toHaveLength(1);
  expect(orderPayloads[0]).toMatchObject({ angebotVersionId: 101, paket: [{ bedarfId: 71, version: 3, menge: 2 }] });
  await expect(page.getByRole('heading', { name: 'B-2026-0073', exact: true }).first()).toBeVisible();
  expect(foreign).toEqual([]);
});
