import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test('Zeichnungsteil vollständig anlegen, nach Reload finden und mehrere Anfragemengen gemeinsam prüfen', async ({ page }, testInfo) => {
  type AnfrageAufruf = { positionen: Array<{ bedarfId: number; version: number; menge: number }> };
  const aufrufe: { pfad: string; methode: string; inhalt: string | null }[] = [];
  let gespeicherteBedarfe: ReturnType<typeof bedarf>[] = [];
  let anfrageVersuche = 0;
  const bedarf = (id: number, version: number, nummer: string, beschreibung: string, menge: number) => ({ id, version,
    position: { art: 'ZEICHNUNGSTEIL', artikelId: null, interneReferenz: nummer, zeichnungsnummer: `Z-${nummer}`, zeichnungsrevision: 'B', bezeichnung: beschreibung, werkstoff: 'S355', abmessung: 'IPE 200', basis: { menge, einheit: 'STUECK', stueckzahl: menge, einzelLaengeMm: null, kgJeMeter: null, faktorQuelle: null }, schnittForm: 'GERADE', winkelLinks: null, winkelRechts: null, bearbeitung: null, oberflaeche: null, dokumente: [], anlageVersionIds: [815] },
    liefergruppe: { projektId: 19, lieferadresse: null, bedarfstermin: null, lagerzweck: null },
    mengen: { bedarf: menge, lagergedeckt: 0, angefragt: 0, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: menge, disponierbar: menge }, nachpflegeErforderlich: false, historischerHinweis: null });
  gespeicherteBedarfe = [bedarf(45, 2, 'ZT-42', 'Blech 42', 5)];
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url()); const methode = route.request().method();
    const inhalt = route.request().postData();
    aufrufe.push({ pfad: url.pathname, methode, inhalt });
    if (url.pathname === '/api/auth/me') return route.fulfill({ json: { id: 9, username: 'max.mustermann', displayName: 'Max Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false } });
    if (url.pathname === '/api/notifications/summary') return route.fulfill({ json: { totalCount: 0, categories: [], recentItems: [] } });
    if (url.pathname === '/api/einkauf/berechtigungen') return route.fulfill({ json: ['LESEN', 'BEARBEITEN', 'ANFRAGE_SENDEN'] });
    if (url.pathname === '/api/projekte/simple') return route.fulfill({ json: [{ id: 19, bauvorhaben: 'Balkonanlage Musterstraße', auftragsnummer: 'A-2026-019', kunde: 'Mustermann GmbH', abgeschlossen: false }] });
    if (url.pathname.startsWith('/api/einkauf/bedarf/') && methode === 'GET') {
      const id = Number(url.pathname.split('/').at(-1));
      return route.fulfill({ json: gespeicherteBedarfe.find(eintrag => eintrag.id === id) });
    }
    if (url.pathname === '/api/einkauf/bedarf' && methode === 'GET') return route.fulfill({ json: { content: gespeicherteBedarfe, totalPages: 1, totalElements: gespeicherteBedarfe.length, number: 0, size: 20, empty: gespeicherteBedarfe.length === 0 } });
    if (url.pathname === '/api/einkauf/bedarf/zeichnungsteil' && methode === 'POST') {
      expect(inhalt).toContain('"anlageVersionIds":[]');
      const neu = bedarf(44, 1, 'ZT-41', 'Träger 41', 10); gespeicherteBedarfe = [neu, ...gespeicherteBedarfe];
      return route.fulfill({ status: 201, json: neu });
    }
    if (url.pathname === '/api/einkauf/anfragen' && methode === 'POST') {
      anfrageVersuche++;
      if (anfrageVersuche === 1) {
        gespeicherteBedarfe = [bedarf(44, 2, 'ZT-41', 'Träger 41', 6), ...gespeicherteBedarfe.filter(eintrag => eintrag.id !== 44)];
        return route.fulfill({ status: 409, json: { message: 'Ein Bedarf wurde zwischenzeitlich geändert.' } });
      }
      return route.fulfill({ status: 201, json: { kopf: { id: 81, version: 0 }, positionen: [], lieferanten: [], angezeigteRevisionId: 111, historisch: false } });
    }
    if (url.pathname.startsWith('/api/einkauf/anfragen/')) return route.fulfill({ json: { kopf: { id: 81, version: 0 }, positionen: [], lieferanten: [], angezeigteRevisionId: 111, historisch: false } });
    return route.abort();
  });

  await page.goto('/bestellungen/bedarf');
  await expect(page.getByRole('heading', { name: 'BEDARF' })).toBeVisible();
  await page.getByRole('button', { name: 'Bedarf erfassen' }).click();
  await page.getByRole('combobox', { name: 'Positionsart' }).click();
  await page.getByRole('option', { name: 'Zeichnungsteil' }).click();
  await page.getByLabel('Bezeichnung *').fill('Träger 41');
  await page.getByLabel('Projektkennung *').fill('ZT-41');
  await page.getByLabel('Menge *').fill('10');
  await page.getByLabel('Zeichnungsnummer').fill('Z-41');
  await page.getByLabel('Zeichnungsrevision').fill('B');
  await page.getByRole('button', { name: 'Projekt auswählen' }).click();
  const projektDialog = page.locator('.fixed.inset-0').filter({ hasText: 'Projekt auswählen' }).last();
  await expect(projektDialog).toBeVisible();
  await projektDialog.getByRole('button', { name: /Balkonanlage Musterstraße/ }).click();
  await page.getByLabel('Anlagenrevision').fill('B');
  await page.getByLabel('Zeichnungsdatei').setInputFiles({ name: 'Z-41.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7 Dummy') });
  await page.getByRole('button', { name: 'Bedarf speichern' }).click();
  await expect(page.getByText('Träger 41')).toBeVisible();
  await designPruefung(page, testInfo, 'einkauf-bedarf-zeichnungsteil-erstanlage');
  const erstanlage = aufrufe.find(aufruf => aufruf.pfad === '/api/einkauf/bedarf/zeichnungsteil');
  expect(erstanlage?.methode).toBe('POST');
  expect(erstanlage?.inhalt).toContain('Z-41.pdf');
  await page.reload();
  await expect(page.getByText('Träger 41')).toBeVisible();
  await page.getByLabel('Bedarf ZT-41 auswählen').check();
  await page.getByLabel('Bedarf ZT-42 auswählen').check();
  await page.getByLabel('Anfragemenge ZT-41').fill('4');
  await page.getByLabel('Anfragemenge ZT-42').fill('1,');
  await page.getByRole('button', { name: /Angebote einholen/ }).click();
  await expect(page.getByRole('alert')).toContainText('jeden ausgewählten Bedarf');
  expect(aufrufe.some(aufruf => aufruf.pfad === '/api/einkauf/anfragen' && aufruf.methode === 'POST')).toBe(false);
  await page.getByLabel('Anfragemenge ZT-42').fill('2');
  await designPruefung(page, testInfo, 'einkauf-bedarf-teilmengen', { primaerAktion: page.getByRole('button', { name: /Angebote einholen/ }) });
  await page.getByRole('button', { name: /Angebote einholen/ }).click();
  const konflikt = page.getByRole('dialog').filter({ hasText: 'Zwischenzeitliche Änderungen prüfen' });
  await expect(konflikt).toBeVisible();
  await expect(konflikt).toContainText('Verfügbar 6 STUECK');
  const standwahl = konflikt.getByRole('combobox', { name: 'ZT-41 · Anfragemenge: Stand auswählen' });
  await standwahl.click();
  await page.getByRole('option', { name: 'Meinen Entwurf verwenden' }).click();
  await konflikt.getByRole('button', { name: 'Abgleich übernehmen' }).click();
  await expect(page.getByText('Verfügbar: 6 stueck')).toBeVisible();
  await page.getByRole('button', { name: /Angebote einholen/ }).click();
  await expect(page).toHaveURL(/\/einkaufsanfragen\/81$/);
  const anfrageAufrufe = aufrufe.filter(aufruf => aufruf.pfad === '/api/einkauf/anfragen' && aufruf.methode === 'POST');
  expect(anfrageAufrufe).toHaveLength(2);
  const anfrage = JSON.parse(anfrageAufrufe[1].inhalt!) as AnfrageAufruf;
  expect(anfrage.positionen).toEqual([{ bedarfId: 44, version: 2, menge: 4 }, { bedarfId: 45, version: 2, menge: 2 }]);
  expect(aufrufe.some(aufruf => aufruf.pfad.endsWith('/senden') || aufruf.pfad.endsWith('/freigeben'))).toBe(false);
});
