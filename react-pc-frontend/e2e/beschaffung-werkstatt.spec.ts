import { readFile } from 'node:fs/promises';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test('EN1090-Bedarf je Projekt und Vorrat: Werkstattprüfung bleibt erhalten, nur Fehlmenge geht in Bestellung', async ({ page }, testInfo) => {
  const need = (id: number, projektId: number | null, name: string) => ({ id, version: 0,
    position: { art: 'FREITEXT', artikelId: null, interneReferenz: null, bezeichnung: name, zeichnungsnummer: null, zeichnungsrevision: null, werkstoff: null, abmessung: null, basis: { menge: 10, einheit: 'METER', stueckzahl: null, einzelLaengeMm: null, kgJeMeter: null, faktorQuelle: null }, schnittForm: null, winkelLinks: null, winkelRechts: null, bearbeitung: null, oberflaeche: null, dokumente: [], anlageVersionIds: [] },
    liefergruppe: { projektId, lagerzweck: projektId ? null : 'Werkstatt / auf Vorrat', lieferadresse: null, bedarfstermin: null },
    mengen: { bedarf: 10, lagergedeckt: 0, reserviert: 0, bestellt: 0, angefragt: 0, geliefert: 0, storniert: 0, ungedeckt: 10, disponierbar: 10 }, nachpflegeErforderlich: false, historischerHinweis: null });
  const needs = [need(1, 19, 'Dichtband für Balkonanlage'), need(2, null, 'Dichtband für Werkstatt')];
  const saves: unknown[] = [];
  const prints: unknown[] = [];
  const orders: unknown[] = [];
  const releases: unknown[] = [];
  const createdNeeds: unknown[] = [];
  const pdf = '%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF';
  const order = { id: 22, version: 0, nummer: 'B-2026-0022', lieferantId: 8, status: 'ENTWURF', lieferantenStatus: 'AUSSTEHEND', empfaenger: { lieferantenname: 'Musterstahl', email: 'einkauf@example.test' },
    revisionen: [{ id: 222, nummer: 1, version: 0, snapshot: {}, verworfen: false, angenommenAm: null, externerNachweis: null,
      positionen: [{ id: 223, snapshot: needs[0].position, menge: 7.5, nettoEinzelpreis: null, herkuenfte: [{ bedarfId: 1, version: 1, menge: 7.5 }] }] }] };

  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    const method = route.request().method();
    if (url.pathname === '/api/auth/me') return route.fulfill({ json: { id: 9, username: 'max.mustermann', displayName: 'Max Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false } });
    if (url.pathname === '/api/notifications/summary') return route.fulfill({ json: { totalCount: 0, categories: [], recentItems: [] } });
    if (url.pathname === '/api/einkauf/berechtigungen') return route.fulfill({ json: ['LESEN', 'BEARBEITEN', 'BESTELLUNG_SENDEN'] });
    if (url.pathname === '/api/projekte/19') return route.fulfill({ json: { id: 19, bauvorhaben: 'Balkonanlage Musterstraße', auftragsnummer: 'A-2026-019', kunde: 'Mustermann GmbH' } });
    if (url.pathname === '/api/projekte/simple') return route.fulfill({ json: [{ id: 19, bauvorhaben: 'Balkonanlage Musterstraße', auftragsnummer: 'A-2026-019', kunde: 'Mustermann GmbH' }] });
    if (url.pathname === '/api/einkauf/bedarf/werkstattpruefung' && method === 'PUT') {
      const body = route.request().postDataJSON() as { positionen: { bedarfId: number; vorhanden: number; version: number }[] };
      saves.push(body);
      const updated = body.positionen.map(entry => {
        const row = needs.find(n => n.id === entry.bedarfId)!;
        row.mengen.lagergedeckt = entry.vorhanden;
        row.mengen.disponierbar = 10 - entry.vorhanden;
        row.mengen.ungedeckt = 10 - entry.vorhanden;
        row.version++;
        return row;
      });
      return route.fulfill({ json: updated });
    }
    if (url.pathname === '/api/einkauf/bedarf/pdf' && method === 'POST') {
      prints.push(route.request().postDataJSON());
      return route.fulfill({ contentType: 'application/pdf', body: pdf });
    }
    if (url.pathname === '/api/einkauf/bedarf' && method === 'POST') {
      const body = route.request().postDataJSON(); createdNeeds.push(body);
      const row = need(3, body.liefergruppe.projektId, body.position.bezeichnung);
      row.position = body.position; row.liefergruppe = body.liefergruppe;
      row.mengen.bedarf = body.position.basis.menge; row.mengen.disponierbar = body.position.basis.menge; row.mengen.ungedeckt = body.position.basis.menge;
      needs.push(row);
      return route.fulfill({ status: 201, json: row });
    }
    if (url.pathname === '/api/lieferanten') return route.fulfill({ json: { lieferanten: [{ id: 8, lieferantenname: 'Musterstahl', istAktiv: true }] } });
    if (url.pathname === '/api/lieferanten/8') return route.fulfill({ json: { id: 8, lieferantenname: 'Musterstahl', eigeneKundennummer: '0008' } });
    if (url.pathname === '/api/lieferanten/8/einkauf-kontakte') return route.fulfill({ json: [{ id: 4, version: 1, name: 'Einkauf', email: 'einkauf@example.test', standardBestellung: true, aktiv: true }] });
    if (url.pathname === '/api/einkauf/bestellungen/direkt' && method === 'POST') {
      orders.push(route.request().postDataJSON());
      needs[0].mengen.reserviert = 7.5; needs[0].mengen.disponierbar = 0; needs[0].version++;
      return route.fulfill({ status: 201, json: order });
    }
    if (url.pathname === '/api/einkauf/bestellungen/22') return route.fulfill({ json: order });
    if (url.pathname === '/api/email-textvorlagen') return route.fulfill({ json: [{ id: 4, dokumentTyp: 'EINKAUF_BESTELLUNG', aktiv: true, standard: true }] });
    if (url.pathname === '/api/einkauf/bestellungen/22/vorschau' && method === 'POST') return route.fulfill({ json: { version: 0, vorschauHash: 'bestellung-22-preis-offen', subject: 'Bestellung B-2026-0022', htmlBody: '<p>Dichtband 7,5 Meter – Preis offen</p>', empfaenger: 'einkauf@example.test', pdfDateiId: 55, anlageVersionIds: [], revisionsNummer: 1, eigeneKundennummer: '0008', liefertermin: null, antwortfrist: null, anlagen: [] } });
    if (url.pathname === '/api/einkauf/pdf-vorschau/55') return route.fulfill({ contentType: 'application/pdf', body: pdf });
    if (url.pathname === '/api/einkauf/bestellungen/22/freigeben' && method === 'POST') {
      releases.push(route.request().postDataJSON());
      return route.fulfill({ json: { id: 88, version: 1, status: 'VORBEREITET' } });
    }
    if (url.pathname === '/api/einkauf/bestellungen/22/revisionen/222/versandstatus') return route.fulfill({ json: releases.length ? [{ id: 88, version: 1, status: 'VORBEREITET', erstelltAm: '2026-09-24T12:00:00Z' }] : [] });
    if (url.pathname === '/api/einkauf/bedarf') {
      const rows = needs.filter(row => url.searchParams.get('ohneProjekt') === 'true' ? row.liefergruppe.projektId === null : !url.searchParams.has('projektId') || String(row.liefergruppe.projektId) === url.searchParams.get('projektId'));
      return route.fulfill({ json: { content: rows, totalPages: 1, totalElements: rows.length } });
    }
    if (/^\/api\/einkauf\/bedarf\/\d+$/.test(url.pathname)) return route.fulfill({ json: needs.find(row => row.id === Number(url.pathname.split('/').at(-1))) });
    return route.fulfill({ json: [] });
  });
  await page.goto('/bestellungen/bedarf');
  await expect(page.getByRole('heading', { name: 'Bedarf je Projekt' })).toBeVisible();
  await designPruefung(page, testInfo, 'beschaffung-uebersicht');
  // Die Projektzeile ist die Schaltfläche; ihr Name kommt aus dem sichtbaren Inhalt (EN1090-Oberfläche).
  await page.getByRole('button', { name: /Balkonanlage Musterstraße/ }).click();
  await expect(page.getByRole('heading', { name: 'Balkonanlage Musterstraße' })).toBeVisible();
  const vorhanden = page.getByRole('textbox', { name: 'Vorhandene Menge', exact: true });
  const speichern = page.getByRole('button', { name: 'Werkstattprüfung speichern' });
  await vorhanden.focus();
  await expect(vorhanden).toHaveValue('');
  await vorhanden.fill('2,');
  await vorhanden.press('Tab');
  await expect(page.getByRole('alert')).toContainText('vollständige Zahl');
  await expect(speichern).toBeDisabled();
  expect(saves).toHaveLength(0);
  await vorhanden.fill('2,5');
  await vorhanden.press('Tab');
  await speichern.click();
  await expect(page.getByText('Werkstattprüfung gespeichert.')).toBeVisible();
  expect(saves).toEqual([{ positionen: [{ bedarfId: 1, version: 0, vorhanden: 2.5 }] }]);
  await page.reload();
  await expect(vorhanden).toHaveValue('2,5');
  await expect(page.getByRole('textbox', { name: 'Bestellmenge', exact: true })).toHaveValue('7,5');
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Stückliste drucken' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe('Bedarfsliste.pdf');
  const downloadPath = await download.path();
  expect(await readFile(downloadPath!, 'utf8')).toBe(pdf);
  expect(prints).toEqual([{ bedarfIds: [1] }]);
  const bestellungVorbereiten = page.getByRole('button', { name: 'Bestellung vorbereiten', exact: true });
  await designPruefung(page, testInfo, 'beschaffung-werkstatt-geprueft', { primaerAktion: bestellungVorbereiten });
  await bestellungVorbereiten.click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('dialog').getByRole('textbox', { name: /Menge/ }).first()).toHaveValue('7,5');
  await page.getByRole('button', { name: 'Lieferant wählen' }).click();
  await page.getByText('Musterstahl', { exact: true }).last().click();
  await expect(page.getByText('Kontakt: Einkauf')).toBeVisible();
  await expect(page.getByRole('dialog').getByLabel('Preis 1', { exact: true })).toHaveValue('');
  await designPruefung(page, testInfo, 'beschaffung-fehlmenge-bestellen', { primaerAktion: page.getByRole('button', { name: 'Direktbestellung als Entwurf anlegen' }) });
  await page.getByRole('button', { name: 'Direktbestellung als Entwurf anlegen' }).click();
  await expect(page).toHaveURL(/\/bestellungen\/22$/);
  await expect(page.getByRole('heading', { name: 'B-2026-0022', exact: true }).first()).toBeVisible();
  await expect(page.getByText('Preis offen', { exact: true })).toBeVisible();
  expect(orders).toHaveLength(1);
  expect(orders[0]).toMatchObject({ lieferantId: 8, paket: [{ bedarfId: 1, version: 1, menge: 7.5 }], preise: [] });
  await page.getByRole('button', { name: 'Vorschau und Freigabe' }).click();
  await expect(page.getByText(/Diese Bestellung enthält offene Preise/)).toBeVisible();
  await page.getByRole('button', { name: 'Vorschau laden' }).click();
  await expect(page.getByText('Bestellung B-2026-0022', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Bestellung freigeben', exact: true })).toBeDisabled();
  await page.getByRole('checkbox', { name: /PDF und Empfänger geprüft/ }).check();
  await designPruefung(page, testInfo, 'beschaffung-freigabe-preis-offen', { primaerAktion: page.getByRole('button', { name: 'Bestellung freigeben', exact: true }) });
  await page.getByRole('button', { name: 'Bestellung freigeben', exact: true }).click();
  await expect(page.getByText('VORBEREITET', { exact: true })).toBeVisible();
  expect(releases).toHaveLength(1);
  expect(releases[0]).toMatchObject({ version: 0, vorschauHash: 'bestellung-22-preis-offen' });
  // Zurück zum Bedarf: die reservierte Fehlmenge darf nicht ein zweites Mal bestellt werden.
  await page.getByRole('link', { name: 'Bedarf', exact: true }).first().click();
  await page.getByRole('button', { name: /Balkonanlage Musterstraße/ }).click();
  await expect(page.getByRole('textbox', { name: 'Bestellmenge', exact: true })).toHaveValue('0');
  await expect(bestellungVorbereiten).toBeDisabled();
  await page.reload();
  await expect(bestellungVorbereiten).toBeDisabled();
  expect(orders).toHaveLength(1);
  await designPruefung(page, testInfo, 'beschaffung-keine-doppelte-bestellung');
  // Werkstatt-/Vorratsbedarf liegt in der EN1090-Oberfläche unter "Ohne Projektzuordnung".
  await page.getByRole('link', { name: 'Zur Übersicht' }).click();
  await page.getByRole('button', { name: /Ohne Projektzuordnung/ }).click();
  await expect(page).toHaveURL(/\/bestellungen\/bedarf\/vorrat$/);
  await expect(page.getByText('Dichtband für Werkstatt', { exact: true })).toBeVisible();
  await expect(page.getByText('Dichtband für Balkonanlage', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Position hinzufügen' }).click();
  const material = page.getByRole('dialog', { name: 'Materialbestellung' });
  await material.getByLabel('Produktname Position 1').fill('Dichtmasse für Werkstatt');
  await material.getByLabel('Menge Position 1', { exact: true }).fill('3,5');
  await material.getByRole('combobox', { name: 'Einheit Position 1' }).click();
  await page.getByRole('option', { name: 'm (Meter)', exact: true }).click();
  await designPruefung(page, testInfo, 'beschaffung-vorrat-erfassen', { primaerAktion: material.getByRole('button', { name: 'Alle speichern', exact: true }) });
  await material.getByRole('button', { name: 'Alle speichern', exact: true }).click();
  await expect(material).toHaveCount(0);
  await expect(page.getByText('Dichtmasse für Werkstatt', { exact: true })).toBeVisible();
  expect(createdNeeds).toHaveLength(1);
  expect(createdNeeds[0]).toMatchObject({ position: { art: 'FREITEXT', artikelId: null, bezeichnung: 'Dichtmasse für Werkstatt', basis: { menge: 3.5, einheit: 'METER' } }, liefergruppe: { projektId: null, lagerzweck: 'Werkstatt / auf Vorrat' } });
  await page.reload();
  await expect(page.getByText('Dichtmasse für Werkstatt', { exact: true })).toBeVisible();
  await designPruefung(page, testInfo, 'beschaffung-ohne-projekt');
});
