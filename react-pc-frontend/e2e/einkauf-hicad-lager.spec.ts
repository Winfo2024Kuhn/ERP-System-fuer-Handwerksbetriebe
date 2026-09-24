import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { readFileSync } from 'node:fs';

test('HiCAD-Vorschau übernimmt nur bestätigte Restmengen und Lagerentnahme erst nach Bestätigung', async ({ page }, testInfo) => {
  type JsonWert = null | boolean | number | string | JsonWert[] | { [key: string]: JsonWert };
  type ÜbernahmeInhalt = { version: number; zeilen: Array<{ zeilennummer: number }> };
  const aufrufe: { pfad: string; methode: string; inhalt: JsonWert }[] = [];
  let offeneMenge = 10; let entnahmen = 0; let hicadLaeufe = 0;
  const bedarf = () => ({ id: 6, version: 4, position: { art: 'ARTIKEL', artikelId: null, interneReferenz: 'MAT-6', bezeichnung: 'Stahlprofil', basis: { menge: 10, einheit: 'STUECK' }, dokumente: [], anlageVersionIds: [] }, liefergruppe: { projektId: 19, lieferadresse: null, bedarfstermin: null, lagerzweck: null }, mengen: { bedarf: 10, lagergedeckt: 0, angefragt: 0, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: offeneMenge, disponierbar: offeneMenge }, nachpflegeErforderlich: false, historischerHinweis: null });
  await page.route('**/api/**', async route => {
    const adresse = new URL(route.request().url()); const methode = route.request().method(); let inhalt: JsonWert = null;
    try { inhalt = route.request().postDataJSON() as JsonWert; } catch { inhalt = null; }
    aufrufe.push({ pfad: adresse.pathname, methode, inhalt });
    if (adresse.pathname === '/api/auth/me') return route.fulfill({ json: { id: 9, username: 'max.mustermann', displayName: 'Max Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false } });
    if (adresse.pathname === '/api/notifications/summary') return route.fulfill({ json: { totalCount: 0, categories: [], recentItems: [] } });
    if (adresse.pathname === '/api/einkauf/berechtigungen') return route.fulfill({ json: ['LESEN', 'BEARBEITEN'] });
    if (adresse.pathname === '/api/projekte/simple') return route.fulfill({ json: [{ id: 19, bauvorhaben: 'Werkstatt Muster', auftragsnummer: 'A-019', kunde: 'Mustermann GmbH', abgeschlossen: false }] });
    if (adresse.pathname === '/api/einkauf/bedarf') return route.fulfill({ json: { content: [bedarf()], totalPages: 1, totalElements: 1, number: 0, size: 20 } });
    if (adresse.pathname === '/api/einkauf/hicad/vorschau') { hicadLaeufe++; return route.fulfill({ status: 201, json: { id: 3, dateiHash: 'hash-test', dateiSchonImportiert: hicadLaeufe > 1, zeilen: [
      { zeilennummer: 2, rohtext: 'ZT-41;Z-41;B;Träger 41;S355;IPE 200;10;Stück', vorschlag: { art: 'ZEICHNUNGSTEIL', artikelId: null, interneReferenz: 'ZT-41', zeichnungsnummer: 'Z-41', zeichnungsrevision: 'B', bezeichnung: 'Träger 41', werkstoff: 'S355', abmessung: 'IPE 200', basis: { menge: 10, einheit: 'STUECK', stueckzahl: 10, einzelLaengeMm: null, kgJeMeter: null, faktorQuelle: null }, schnittForm: null, winkelLinks: null, winkelRechts: null, bearbeitung: null, oberflaeche: null, dokumente: [], anlageVersionIds: [] }, artikelKandidaten: [], bereitsUebernommen: hicadLaeufe > 1, hinweise: ['Eingebettetes Bild gespeichert; bitte vor der Übernahme als ungeprüfte Anlage bestätigen.'], bilder: [{ dateiId: 52, dateiname: 'hicad-pruefbild.png', mimeTyp: 'image/png', byteAnzahl: 68, url: '/api/einkauf/hicad/3/bilder/52' }] },
      { zeilennummer: 3, rohtext: 'ZT-42;Z-42;A;Blech 42;S235JR;5 mm;5;Stück', vorschlag: { art: 'ZEICHNUNGSTEIL', artikelId: null, interneReferenz: 'ZT-42', zeichnungsnummer: 'Z-42', zeichnungsrevision: 'A', bezeichnung: 'Blech 42', werkstoff: 'S235JR', abmessung: '5 mm', basis: { menge: 5, einheit: 'STUECK', stueckzahl: 5, einzelLaengeMm: null, kgJeMeter: null, faktorQuelle: null }, schnittForm: null, winkelLinks: null, winkelRechts: null, bearbeitung: null, oberflaeche: null, dokumente: [], anlageVersionIds: [] }, artikelKandidaten: [], bereitsUebernommen: true, hinweise: ['Bereits teilweise übernommen'], bilder: [] },
    ] } }); }
    if (adresse.pathname === '/api/einkauf/hicad/3') return route.fulfill({ json: { id: 3, version: hicadLaeufe, duplikat: hicadLaeufe > 1, zeilen: [
      { zeilennummer: 2, gesamtmenge: 10, uebernommeneMenge: hicadLaeufe > 1 ? 10 : 0, verbleibendeMenge: hicadLaeufe > 1 ? 0 : 10, vollstaendigUebernommen: hicadLaeufe > 1 },
      { zeilennummer: 3, gesamtmenge: 5, uebernommeneMenge: 2, verbleibendeMenge: 3, vollstaendigUebernommen: false },
    ] } });
    if (adresse.pathname === '/api/einkauf/hicad/3/bilder/52') return route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jqGQAAAAASUVORK5CYII=', 'base64') });
    if (adresse.pathname.endsWith('/uebernehmen')) return route.fulfill({ json: [] });
    if (adresse.pathname === '/api/einkauf/lagerentnahmen' && methode === 'POST') { entnahmen++; if (entnahmen === 1) return route.fulfill({ status: 409, json: { message: 'Der Bedarf wurde zwischenzeitlich geändert.' } }); offeneMenge = 7; return route.fulfill({ status: 201, json: { id: 1, offenerBedarf: 7 } }); }
    return route.abort();
  });

  await page.goto('/bestellungen/bedarf');
  await page.getByRole('button', { name: 'HiCAD importieren' }).click();
  await page.getByRole('button', { name: 'Projekt auswählen' }).click();
  await page.getByRole('button', { name: /Werkstatt Muster/ }).click();
  await page.getByLabel('HiCAD-Exceldatei').setInputFiles({ name: 'hicad-pruefdatei.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: readFileSync('e2e/fixtures/hicad-pruefdatei.xlsx') });
  await page.getByRole('button', { name: 'Vorschau laden' }).click();
  await expect(page.getByRole('heading', { name: /Träger 41/ })).toBeVisible();
  await designPruefung(page, testInfo, 'einkauf-hicad-vorschau', { primaerAktion: page.getByRole('button', { name: 'Ausgewählte Zeilen übernehmen' }) });
  await page.getByLabel('Menge Zeile 2').fill('11');
  await page.getByRole('button', { name: 'Ausgewählte Zeilen übernehmen' }).click();
  await expect(page.getByRole('dialog').getByText('Bitte eine gültige Teilmenge für Zeile 2 eingeben.')).toBeVisible();
  expect(aufrufe.some(aufruf => aufruf.pfad.endsWith('/uebernehmen'))).toBe(false);
  await page.getByLabel('Menge Zeile 2').fill('10');
  await page.getByLabel('Zeile 2 übernehmen').check();
  await page.getByLabel('Bild freigeben: hicad-pruefbild.png').check();
  await page.getByRole('button', { name: 'Ausgewählte Zeilen übernehmen' }).click();
  const übernahmeAufruf = aufrufe.find(aufruf => aufruf.pfad.endsWith('/uebernehmen'));
  expect((übernahmeAufruf?.inhalt as ÜbernahmeInhalt).zeilen.map(zeile => zeile.zeilennummer)).toEqual([2]);
  expect((übernahmeAufruf?.inhalt as ÜbernahmeInhalt).version).toBe(1);

  await page.getByRole('button', { name: 'Lagerentnahme erfassen MAT-6' }).click();
  await page.getByLabel('Entnommene Menge').fill('3');
  expect(aufrufe.some(aufruf => aufruf.pfad === '/api/einkauf/lagerentnahmen')).toBe(false);
  await page.getByRole('button', { name: 'Entnahme bestätigen' }).click();
  await expect(page.getByRole('dialog').getByText(/zwischenzeitlich geändert/)).toBeVisible();
  await expect(page.getByText(/offen 10/)).toBeVisible();
  await page.getByRole('button', { name: 'Entnahme bestätigen' }).click();
  await expect(page.locator('article').filter({ hasText: 'MAT-6' })).toContainText('7');
});

test('HiCAD-Dateigrenze wird vor dem Upload geprüft', async ({ page }, testInfo) => {
  await page.route('**/api/**', route => {
    const pfad = new URL(route.request().url()).pathname;
    if (pfad === '/api/auth/me') return route.fulfill({ json: { id: 9, username: 'max.mustermann', displayName: 'Max Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false } });
    if (pfad === '/api/notifications/summary') return route.fulfill({ json: { totalCount: 0, categories: [], recentItems: [] } });
    if (pfad === '/api/einkauf/berechtigungen') return route.fulfill({ json: ['LESEN', 'BEARBEITEN'] });
    if (pfad === '/api/einkauf/bedarf') return route.fulfill({ json: { content: [], totalPages: 0, totalElements: 0, number: 0, size: 20 } });
    return route.abort();
  });
  await page.goto('/bestellungen/bedarf');
  await page.getByRole('button', { name: 'HiCAD importieren' }).click();
  await page.getByLabel('HiCAD-Exceldatei').setInputFiles({ name: 'zu-gross.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.alloc(10 * 1024 * 1024 + 1) });
  await expect(page.locator('[role="dialog"] p[role="alert"]')).toContainText('höchstens 10 MiB');
  await expect(page.getByTestId('toast-container').getByRole('alert')).toContainText('höchstens 10 MiB');
  await designPruefung(page, testInfo, 'einkauf-hicad-dateigrenze');
});
