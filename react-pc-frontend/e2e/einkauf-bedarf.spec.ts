import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test('Zeichnungsteil vollständig anlegen, nach erneutem Laden finden und mehrere Anfragemengen gemeinsam prüfen', async ({ page: seite }, prüfinformationen) => {
  type AnfrageAufruf = { positionen: Array<{ bedarfId: number; version: number; menge: number }> };
  const aufrufe: { pfad: string; methode: string; inhalt: string | null }[] = [];
  let gespeicherteBedarfe: ReturnType<typeof bedarf>[] = [];
  let anfrageVersuche = 0;
  const bedarf = (id: number, version: number, nummer: string, beschreibung: string, menge: number) => ({ id, version,
    position: { art: 'ZEICHNUNGSTEIL', artikelId: null, interneReferenz: nummer, zeichnungsnummer: `Z-${nummer}`, zeichnungsrevision: 'B', bezeichnung: beschreibung, werkstoff: 'S355', abmessung: 'IPE 200', basis: { menge, einheit: 'STUECK', stueckzahl: menge, einzelLaengeMm: null, kgJeMeter: null, faktorQuelle: null }, schnittForm: 'GERADE', winkelLinks: null, winkelRechts: null, bearbeitung: null, oberflaeche: null, dokumente: [], anlageVersionIds: [815] },
    liefergruppe: { projektId: 19, lieferadresse: null, bedarfstermin: null, lagerzweck: null },
    mengen: { bedarf: menge, lagergedeckt: 0, angefragt: 0, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: menge, disponierbar: menge }, nachpflegeErforderlich: false, historischerHinweis: null });
  gespeicherteBedarfe = [bedarf(45, 2, 'ZT-42', 'Blech 42', 5)];
  await seite.route('**/api/**', async route => {
    const adresse = new URL(route.request().url()); const methode = route.request().method();
    const inhalt = route.request().postData();
    aufrufe.push({ pfad: adresse.pathname, methode, inhalt });
    if (adresse.pathname === '/api/auth/me') return route.fulfill({ json: { id: 9, username: 'max.mustermann', displayName: 'Max Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false } });
    if (adresse.pathname === '/api/notifications/summary') return route.fulfill({ json: { totalCount: 0, categories: [], recentItems: [] } });
    if (adresse.pathname === '/api/einkauf/berechtigungen') return route.fulfill({ json: ['LESEN', 'BEARBEITEN', 'ANFRAGE_SENDEN'] });
    if (adresse.pathname === '/api/projekte/simple') return route.fulfill({ json: [{ id: 19, bauvorhaben: 'Balkonanlage Musterstraße', auftragsnummer: 'A-2026-019', kunde: 'Mustermann GmbH', abgeschlossen: false }] });
    if (adresse.pathname === '/api/einkauf/bedarf/werkstattpruefung' && methode === 'PUT') {
      const body = route.request().postDataJSON() as { positionen: { bedarfId: number; vorhanden: number }[] };
      const result = body.positionen.map(entry => {
        const row = gespeicherteBedarfe.find(b => b.id === entry.bedarfId)!;
        row.version++; row.mengen.lagergedeckt = entry.vorhanden;
        row.mengen.disponierbar = row.mengen.bedarf - entry.vorhanden;
        row.mengen.ungedeckt = row.mengen.disponierbar;
        return row;
      });
      return route.fulfill({ json: result });
    }
    if (adresse.pathname.startsWith('/api/einkauf/bedarf/') && methode === 'GET') {
      const id = Number(adresse.pathname.split('/').at(-1));
      return route.fulfill({ json: gespeicherteBedarfe.find(eintrag => eintrag.id === id) });
    }
    if (adresse.pathname === '/api/einkauf/bedarf' && methode === 'GET') return route.fulfill({ json: { content: gespeicherteBedarfe, totalPages: 1, totalElements: gespeicherteBedarfe.length, number: 0, size: 20, empty: gespeicherteBedarfe.length === 0 } });
    if (adresse.pathname === '/api/einkauf/bedarf/zeichnungsteil' && methode === 'POST') {
      expect(inhalt).toContain('"anlageVersionIds":[]');
      const neu = bedarf(44, 1, 'ZT-41', 'Träger 41', 10); gespeicherteBedarfe = [neu, ...gespeicherteBedarfe];
      return route.fulfill({ status: 201, json: neu });
    }
    if (adresse.pathname === '/api/einkauf/anfragen' && methode === 'POST') {
      anfrageVersuche++;
      if (anfrageVersuche === 1) {
        const row = gespeicherteBedarfe.find(b => b.id === 44)!; row.version = 3; row.mengen.bedarf = 8; row.mengen.disponierbar = 2; row.mengen.ungedeckt = 2;
        return route.fulfill({ status: 409, json: { message: 'Ein Bedarf wurde zwischenzeitlich geändert.' } });
      }
      return route.fulfill({ status: 201, json: { kopf: { id: 81, version: 0 }, positionen: [], lieferanten: [], angezeigteRevisionId: 111, historisch: false } });
    }
    if (adresse.pathname.startsWith('/api/einkauf/anfragen/')) return route.fulfill({ json: { kopf: { id: 81, version: 0 }, positionen: [], lieferanten: [], angezeigteRevisionId: 111, historisch: false } });
    return route.abort();
  });

  await seite.goto('/bestellungen/bedarf/projekt/19');
  await expect(seite.getByRole('heading', { name: 'Balkonanlage Musterstraße' })).toBeVisible();
  await seite.getByRole('button', { name: 'Zeichnungsteil erfassen' }).click();
  await seite.getByRole('combobox', { name: 'Positionsart' }).click();
  await seite.getByRole('option', { name: 'Zeichnungsteil' }).click();
  await seite.getByLabel('Bezeichnung *').fill('Träger 41');
  await seite.getByLabel('Projektkennung *').fill('ZT-41');
  await seite.getByLabel('Menge *').fill('10');
  await seite.getByLabel('Zeichnungsnummer').fill('Z-41');
  await seite.getByLabel('Zeichnungsrevision').fill('B');
  await seite.getByRole('button', { name: 'Projekt auswählen' }).click();
  const projektDialog = seite.locator('.fixed.inset-0').filter({ hasText: 'Projekt auswählen' }).last();
  await expect(projektDialog).toBeVisible();
  await projektDialog.getByRole('button', { name: /Balkonanlage Musterstraße/ }).click();
  await seite.getByLabel('Anlagenrevision').fill('B');
  await seite.getByLabel('Anlagenrevision').scrollIntoViewIfNeeded();
  await expect(seite.getByRole('heading', { name: 'Bedarf erfassen' })).toBeInViewport({ ratio: 1 });
  await expect(seite.getByRole('button', { name: 'Bedarf speichern' })).toBeInViewport({ ratio: 1 });
  await seite.getByLabel('Zeichnungsdatei').setInputFiles({ name: 'Z-41.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7 Dummy') });
  await seite.getByRole('button', { name: 'Bedarf speichern' }).click();
  await expect(seite.getByText('Träger 41')).toBeVisible();
  await designPruefung(seite, prüfinformationen, 'einkauf-bedarf-zeichnungsteil-erstanlage');
  const erstanlage = aufrufe.find(aufruf => aufruf.pfad === '/api/einkauf/bedarf/zeichnungsteil');
  expect(erstanlage?.methode).toBe('POST');
  expect(erstanlage?.inhalt).toContain('Z-41.pdf');
  await seite.reload();
  await expect(seite.getByText('Träger 41')).toBeVisible();
  await seite.getByLabel('Vorhanden Träger 41').fill('6');
  await seite.getByLabel('Vorhanden Blech 42').fill('1,');
  await seite.getByRole('button', { name: 'Werkstattprüfung speichern' }).click();
  await expect(seite.getByRole('alert')).toContainText('vollständige Zahl');
  expect(aufrufe.some(aufruf => aufruf.pfad === '/api/einkauf/anfragen' && aufruf.methode === 'POST')).toBe(false);
  await seite.getByLabel('Vorhanden Blech 42').fill('3');
  await seite.getByRole('button', { name: 'Werkstattprüfung speichern' }).click();
  await expect(seite.getByText('Werkstattprüfung gespeichert.')).toBeVisible();
  await designPruefung(seite, prüfinformationen, 'einkauf-bedarf-teilmengen', { primaerAktion: seite.getByRole('button', { name: /In Bestellung übernehmen/ }) });
  await seite.getByRole('button', { name: 'Preisanfrage vorbereiten' }).click();
  await expect(seite.getByRole('button', { name: 'Aktuellen Stand neu laden' })).toBeVisible();
  await seite.getByRole('button', { name: 'Aktuellen Stand neu laden' }).click();
  await expect(seite.getByText('2 Stück', { exact: true })).toHaveCount(2);
  await seite.getByRole('button', { name: 'Preisanfrage vorbereiten' }).click();
  await expect(seite).toHaveURL(/\/einkaufsanfragen\/81$/);
  const anfrageAufrufe = aufrufe.filter(aufruf => aufruf.pfad === '/api/einkauf/anfragen' && aufruf.methode === 'POST');
  expect(anfrageAufrufe).toHaveLength(2);
  const anfrage = JSON.parse(anfrageAufrufe[1].inhalt!) as AnfrageAufruf;
  expect(anfrage.positionen).toEqual([{ bedarfId: 44, version: 3, menge: 2 }, { bedarfId: 45, version: 3, menge: 2 }]);
  expect(aufrufe.some(aufruf => aufruf.pfad.endsWith('/senden') || aufruf.pfad.endsWith('/freigeben'))).toBe(false);
});
