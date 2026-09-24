import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { readFileSync } from 'node:fs';
import type { Page } from '@playwright/test';

/**
 * HiCAD-Import im Projektbedarf über das EN1090-Fenster „HiCAD-Sägeliste importieren“
 * (HicadImportModal + Adapter features/einkauf/originalHicadApi.ts) und die anschließende
 * Werkstattprüfung (Lagerentnahme). Alle Daten sind Dummy-Daten (DSGVO).
 */

type JsonWert = null | boolean | number | string | JsonWert[] | { [kennung: string]: JsonWert };
type Aufruf = { pfad: string; methode: string; inhalt: JsonWert };
type ÜbernahmeInhalt = {
  version: number; duplikatBewusst: boolean; idempotenzKey: string;
  zeilen: Array<{ zeilennummer: number; menge: number; bestaetigteBildDateiIds: number[]; korrigiert: { art: string; artikelId: number | null } }>;
};
type StangenwareInhalt = {
  position: { art: string; artikelId: number | null; bezeichnung: string; basis: { menge: number; einheit: string; stueckzahl: number; einzelLaengeMm: number }; beschaffungsdetails: { lieferantId: number | null } };
  liefergruppe: { projektId: number };
};

const PROJEKT = { id: 19, bauvorhaben: 'Werkstatt Muster', auftragsnummer: 'A-019', kunde: 'Mustermann GmbH', abgeschlossen: false };
const ANMELDUNG = { id: 9, username: 'max.mustermann', displayName: 'Max Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false };

/** Grundstubs der Projektbedarf-Seite; `true`, wenn die Anfrage beantwortet wurde. */
async function beantworteSeitenGrundlagen(pfad: string, route: Parameters<Parameters<Page['route']>[1]>[0]): Promise<boolean> {
  if (pfad === '/api/auth/me') { await route.fulfill({ json: ANMELDUNG }); return true; }
  if (pfad === '/api/notifications/summary') { await route.fulfill({ json: { totalCount: 0, categories: [], recentItems: [] } }); return true; }
  if (pfad === '/api/einkauf/berechtigungen') { await route.fulfill({ json: ['LESEN', 'BEARBEITEN'] }); return true; }
  if (pfad === '/api/projekte/simple') { await route.fulfill({ json: [PROJEKT] }); return true; }
  // Mit echtem Backend lädt die Seite ihr Projekt einzeln – ohne diese Antwort bleibt „HiCAD-Import“ gesperrt.
  if (pfad === `/api/projekte/${PROJEKT.id}`) { await route.fulfill({ json: PROJEKT }); return true; }
  return false;
}

/** Positions-Snapshot einer Sägelisten-Zeile, wie ihn POST /api/einkauf/hicad/vorschau liefert. */
const sägezeile = (zeilennummer: number, werte: { referenz: string; benennung: string; profil: string; werkstoff: string; anzahl: number; laengeMm: number; kgJeMeter: number; winkelLinks?: string | null; winkelRechts?: string | null; bildIds?: number[] }) => ({
  zeilennummer,
  rohtext: `${werte.referenz};${werte.benennung};${werte.profil};${werte.werkstoff};${werte.anzahl};${werte.laengeMm}`,
  vorschlag: {
    art: 'ZEICHNUNGSTEIL', artikelId: null, interneReferenz: werte.referenz, zeichnungsnummer: 'Z-019', zeichnungsrevision: 'A',
    bezeichnung: werte.benennung, werkstoff: werte.werkstoff, abmessung: werte.profil,
    basis: { menge: werte.anzahl, einheit: 'STUECK', stueckzahl: werte.anzahl, einzelLaengeMm: werte.laengeMm, kgJeMeter: werte.kgJeMeter, faktorQuelle: 'HiCAD' },
    schnittForm: werte.winkelLinks || werte.winkelRechts ? 'Anschnitt Steg' : null,
    winkelLinks: werte.winkelLinks ?? null, winkelRechts: werte.winkelRechts ?? null,
    bearbeitung: null, oberflaeche: null, dokumente: [], anlageVersionIds: [],
  },
  artikelKandidaten: [], bereitsUebernommen: false, hinweise: [],
  bilder: (werte.bildIds ?? []).map(dateiId => ({ dateiId, dateiname: `anschnitt-${dateiId}.png`, mimeTyp: 'image/png', byteAnzahl: 68, url: `/api/einkauf/hicad/3/bilder/${dateiId}` })),
});

const PIXEL = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jqGQAAAAASUVORK5CYII=', 'base64');

test('HiCAD-Sägeliste: Gruppen prüfen, Artikel zuordnen, nur offene Zeilen anlegen – Lagerentnahme erst nach Bestätigung', async ({ page: seite }, prüfinformationen) => {
  const aufrufe: Aufruf[] = [];
  let offeneMenge = 10; let entnahmen = 0;
  const bedarf = () => ({ id: 6, version: 4, position: { art: 'ARTIKEL', artikelId: null, interneReferenz: 'MAT-6', bezeichnung: 'Stahlprofil', basis: { menge: 10, einheit: 'STUECK' }, dokumente: [], anlageVersionIds: [] }, liefergruppe: { projektId: 19, lieferadresse: null, bedarfstermin: null, lagerzweck: null }, mengen: { bedarf: 10, lagergedeckt: 10 - offeneMenge, angefragt: 0, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: offeneMenge, disponierbar: offeneMenge }, nachpflegeErforderlich: false, historischerHinweis: null });
  await seite.route('**/api/**', async route => {
    const adresse = new URL(route.request().url()); const pfad = adresse.pathname; const methode = route.request().method(); let inhalt: JsonWert = null;
    try { inhalt = route.request().postDataJSON() as JsonWert; } catch { inhalt = null; }
    aufrufe.push({ pfad, methode, inhalt });
    if (await beantworteSeitenGrundlagen(pfad, route)) return;
    if (pfad === '/api/einkauf/bedarf/6') return route.fulfill({ json: { ...bedarf(), version: 5 } });
    if (pfad === '/api/einkauf/bedarf' && methode === 'POST') return route.fulfill({ status: 201, json: { ...bedarf(), id: 31 } });
    if (pfad === '/api/einkauf/bedarf') return route.fulfill({ json: { content: [bedarf()], totalPages: 1, totalElements: 1, number: 0, size: 100 } });
    if (pfad === '/api/einkauf/hicad/vorschau') return route.fulfill({ status: 201, json: {
      id: 3, dateiHash: 'hash-test', dateiSchonImportiert: false,
      kopf: { zeichnungsnummer: 'Z-019', auftragsnummer: 'A-019', auftragstext: 'Geländer Werkstatt Muster', kunde: 'Mustermann GmbH' },
      zeilen: [
        // 45° links, 0° rechts: nur die 45°-Seite ist ein Anschnitt.
        sägezeile(2, { referenz: 'P-101', benennung: 'Stütze', profil: 'IPE 200', werkstoff: 'S355', anzahl: 2, laengeMm: 3000, kgJeMeter: 22.4, winkelLinks: '45°', winkelRechts: '0°', bildIds: [52] }),
        // 0° auf beiden Seiten ist ein gerader Schnitt: kein Winkel, keine Skizze in der Anzeige.
        sägezeile(3, { referenz: 'P-102', benennung: 'Riegel', profil: 'IPE 200', werkstoff: 'S355', anzahl: 3, laengeMm: 2500, kgJeMeter: 22.4, winkelLinks: '0°', winkelRechts: '0°', bildIds: [53] }),
        // Schon vollständig übernommen (siehe Fortschritt): taucht nicht mehr auf.
        sägezeile(4, { referenz: 'P-103', benennung: 'Konsole', profil: 'IPE 200', werkstoff: 'S355', anzahl: 1, laengeMm: 1234, kgJeMeter: 22.4 }),
        // RR-Profil: wird als Stangenware (selbst schneiden) vorgeschlagen.
        sägezeile(5, { referenz: 'P-104', benennung: 'Geländerholm', profil: 'RR 40x40x3', werkstoff: 'S235JR', anzahl: 4, laengeMm: 1500, kgJeMeter: 3.3 }),
      ],
    } });
    if (pfad === '/api/einkauf/hicad/3') return route.fulfill({ json: { id: 3, version: 4, duplikat: false, zeilen: [
      { zeilennummer: 2, gesamtmenge: 2, uebernommeneMenge: 0, verbleibendeMenge: 2, vollstaendigUebernommen: false },
      { zeilennummer: 3, gesamtmenge: 3, uebernommeneMenge: 0, verbleibendeMenge: 3, vollstaendigUebernommen: false },
      { zeilennummer: 4, gesamtmenge: 1, uebernommeneMenge: 1, verbleibendeMenge: 0, vollstaendigUebernommen: true },
      { zeilennummer: 5, gesamtmenge: 4, uebernommeneMenge: 0, verbleibendeMenge: 4, vollstaendigUebernommen: false },
    ] } });
    if (pfad.startsWith('/api/einkauf/hicad/3/bilder/')) return route.fulfill({ contentType: 'image/png', body: PIXEL });
    if (pfad === '/api/einkauf/hicad/3/uebernehmen') return route.fulfill({ json: [{ ...bedarf(), id: 21 }, { ...bedarf(), id: 22 }] });
    if (pfad === '/api/artikel/werkstoffe') return route.fulfill({ json: [] });
    if (pfad === '/api/artikel/filteroptionen') return route.fulfill({ json: { herstellverfahren: [], fertigungszustand: [] } });
    if (pfad === '/api/artikel') {
      // Automatischer Katalogabgleich (ohne „sort“) findet nichts – der Artikel wird bewusst von Hand zugeordnet.
      if (!adresse.searchParams.has('sort')) return route.fulfill({ json: { artikel: [], gesamt: 0 } });
      return route.fulfill({ json: { artikel: [{ id: 77, produktname: 'IPE 200', abmessung: null, artikelnummer: 'ST-0200', werkstoffName: 'S355', positionsEinheit: 'lfm', preisHinweis: 'OK', guenstigsterPreis: 31.5 }], gesamt: 1, seite: 0, seitenGroesse: 20 } });
    }
    if (pfad === '/api/einkauf/bedarf/werkstattpruefung' && methode === 'PUT') { entnahmen++; if (entnahmen === 1) return route.fulfill({ status: 409, json: { message: 'Der Bedarf wurde zwischenzeitlich geändert.' } }); offeneMenge = 7; return route.fulfill({ json: [bedarf()] }); }
    return route.abort();
  });
  const schreibAufrufe = () => aufrufe.filter(aufruf => aufruf.pfad.endsWith('/uebernehmen') || (aufruf.pfad === '/api/einkauf/bedarf' && aufruf.methode === 'POST'));

  await seite.goto('/bestellungen/bedarf/projekt/19');
  await seite.getByRole('button', { name: 'HiCAD-Import' }).click();
  const fenster = seite.getByRole('dialog', { name: 'HiCAD-Sägeliste importieren' });
  await seite.getByLabel(/Excel-Datei aus HiCAD auswählen/).setInputFiles({ name: 'hicad-pruefdatei.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: readFileSync('e2e/fixtures/hicad-pruefdatei.xlsx') });
  await fenster.getByRole('button', { name: 'Analysieren' }).click();

  // Gruppen prüfen: Projekt fest aus der Seite, Kopfdaten der Sägeliste, zwei Profilgruppen.
  await expect(fenster.getByRole('heading', { name: 'IPE 200', exact: true })).toBeVisible();
  await expect(fenster.getByRole('heading', { name: 'RR 40x40x3', exact: true })).toBeVisible();
  await expect(fenster.getByText('2 Gruppen erkannt · 0 mit Stammartikel')).toBeVisible();
  await expect(fenster.getByText('Import in Projekt')).toBeVisible();
  await expect(fenster.getByText('Werkstatt Muster', { exact: true })).toBeVisible();
  await expect(fenster.getByText('Geländer Werkstatt Muster')).toBeVisible();
  const ipe = fenster.locator('div.rounded-xl').filter({ has: seite.getByRole('heading', { name: 'IPE 200', exact: true }) });
  const rohr = fenster.locator('div.rounded-xl').filter({ has: seite.getByRole('heading', { name: 'RR 40x40x3', exact: true }) });
  await expect(ipe.getByText('3.000 mm')).toBeVisible();
  await expect(ipe.getByText('2.500 mm')).toBeVisible();
  // Schon vollständig übernommene Zeile 4 (1.234 mm) wird nicht noch einmal angeboten.
  await expect(fenster.getByText('1.234 mm')).toHaveCount(0);
  // 0° = gerader Schnitt: nur die 45°-Seite von Zeile 2 zeigt Winkel und Skizze.
  await expect(ipe.getByText('45°', { exact: true })).toBeVisible();
  await expect(fenster.getByText('0°', { exact: true })).toHaveCount(0);
  await expect(fenster.getByAltText('Anschnitt Steg')).toHaveCount(1);
  await expect(rohr.getByText('Stangenware (selbst schneiden)')).toBeVisible();
  await expect(rohr.getByText('1 Stange bestellen')).toBeVisible();

  // Artikel über die normale Artikelauswahl (Einzelauswahl) zuordnen, Lieferant bleibt leer.
  await ipe.getByRole('button', { name: 'Artikel zuordnen' }).click();
  const artikelAuswahl = seite.getByRole('dialog', { name: 'Artikel zuordnen' });
  await expect(artikelAuswahl.getByText(/Welcher Artikel ist „IPE 200“/)).toBeVisible();
  await artikelAuswahl.getByLabel('IPE 200 auswählen').check();
  await artikelAuswahl.getByRole('button', { name: 'Übernehmen' }).click();
  await expect(artikelAuswahl).toHaveCount(0);
  await expect(ipe.getByText('· Nr. ST-0200')).toBeVisible();
  await expect(ipe.getByRole('button', { name: 'Artikel ändern' })).toBeVisible();
  await expect(fenster.getByText('2 Gruppen erkannt · 1 mit Stammartikel')).toBeVisible();
  await expect(rohr.getByRole('button', { name: 'Lieferant (optional)' })).toBeVisible();

  // Ungültige Stangenlänge wird vor dem ersten Schreibzugriff abgelehnt.
  const stangenlänge = rohr.getByLabel('Stangenlänge für RR 40x40x3 in Metern');
  await stangenlänge.fill('0');
  const anlegen = fenster.getByRole('button', { name: '2 Positionen anlegen' });
  await anlegen.click();
  await expect(seite.getByTestId('toast-container').getByRole('alert')).toContainText('RR 40x40x3: die Stangenlänge muss mindestens 1 sein');
  expect(schreibAufrufe()).toEqual([]);
  // Meldung wegklicken, damit der Screenshot den eigentlichen Fensterzustand zeigt.
  await seite.getByTestId('toast-container').getByRole('button', { name: 'Meldung schließen' }).click();
  await expect(seite.getByTestId('toast-container').getByRole('alert')).toHaveCount(0);
  await stangenlänge.fill('6');
  await expect(rohr.getByText('1 Stange bestellen')).toBeVisible();

  await expect(anlegen).toBeInViewport({ ratio: 1 });
  await designPruefung(seite, prüfinformationen, 'einkauf-hicad-vorschau', { primaerAktion: anlegen });

  await anlegen.click();
  await expect(fenster).toHaveCount(0);
  await expect(seite.getByTestId('toast-container').getByRole('status')).toContainText('3 Positionen angelegt');

  // Fixzuschnitt: nur die offenen Zeilen 2 und 3, mit zugeordnetem Artikel und allen Bild-IDs der Zeile.
  const übernahme = aufrufe.find(aufruf => aufruf.pfad === '/api/einkauf/hicad/3/uebernehmen')?.inhalt as ÜbernahmeInhalt;
  expect(übernahme.version).toBe(4);
  expect(übernahme.duplikatBewusst).toBe(false);
  expect(übernahme.idempotenzKey).toBeTruthy();
  expect(übernahme.zeilen.map(zeile => [zeile.zeilennummer, zeile.menge, zeile.bestaetigteBildDateiIds])).toEqual([[2, 2, [52]], [3, 3, [53]]]);
  expect(übernahme.zeilen.every(zeile => zeile.korrigiert.art === 'ARTIKEL' && zeile.korrigiert.artikelId === 77)).toBe(true);
  // Stangenware: ein Meter-Bedarf über die optimierte Stangenzahl, als Freitext und ohne Lieferant.
  const stangenware = aufrufe.filter(aufruf => aufruf.pfad === '/api/einkauf/bedarf' && aufruf.methode === 'POST').map(aufruf => aufruf.inhalt as StangenwareInhalt);
  expect(stangenware).toHaveLength(1);
  expect(stangenware[0].position).toMatchObject({ art: 'FREITEXT', artikelId: null, bezeichnung: 'RR 40x40x3', basis: { menge: 6, einheit: 'METER', stueckzahl: 1, einzelLaengeMm: 6000 }, beschaffungsdetails: { lieferantId: null } });
  expect(stangenware[0].liefergruppe.projektId).toBe(19);

  // Lagerentnahme: erst beim Speichern der Werkstattprüfung, nicht schon beim Eintippen.
  const zeile = seite.getByRole('row').filter({ hasText: 'Stahlprofil' });
  await zeile.getByLabel('Vorhandene Menge').fill('3');
  await zeile.getByLabel('Vorhandene Menge').press('Enter');
  await expect(zeile.getByLabel('Bestellmenge')).toHaveValue('7');
  expect(aufrufe.some(aufruf => aufruf.pfad === '/api/einkauf/bedarf/werkstattpruefung')).toBe(false);
  // Konflikt: Bedarf wurde zwischenzeitlich geändert – Servermeldung als Fehler, nichts gilt als gespeichert.
  await seite.getByRole('button', { name: 'Werkstattprüfung speichern' }).click();
  await expect(seite.getByTestId('toast-container').getByRole('alert')).toContainText('Der Bedarf wurde zwischenzeitlich geändert.');
  // Aktuellen Stand holen (Seite neu laden) und erneut eintragen.
  await seite.reload();
  await expect(zeile.getByLabel('Vorhandene Menge')).toHaveValue('0');
  await zeile.getByLabel('Vorhandene Menge').fill('3');
  await zeile.getByLabel('Vorhandene Menge').press('Enter');
  await seite.getByRole('button', { name: 'Werkstattprüfung speichern' }).click();
  await expect(seite.getByTestId('toast-container').getByRole('status').filter({ hasText: 'Werkstattprüfung gespeichert.' })).toBeVisible();
  const entnahmeAufrufe = aufrufe.filter(aufruf => aufruf.pfad === '/api/einkauf/bedarf/werkstattpruefung');
  expect(entnahmeAufrufe.map(aufruf => aufruf.inhalt)).toEqual([
    { positionen: [{ bedarfId: 6, version: 4, vorhanden: 3 }] },
    { positionen: [{ bedarfId: 6, version: 4, vorhanden: 3 }] },
  ]);
  await expect(zeile.getByLabel('Vorhandene Menge')).toHaveValue('3');
  await expect(zeile.getByLabel('Bestellmenge')).toHaveValue('7');
});

test('HiCAD-Dateigrenze wird vor dem Upload geprüft', async ({ page: seite }, prüfinformationen) => {
  const pfade: string[] = [];
  await seite.route('**/api/**', async route => {
    const pfad = new URL(route.request().url()).pathname;
    pfade.push(pfad);
    if (await beantworteSeitenGrundlagen(pfad, route)) return;
    if (pfad === '/api/einkauf/bedarf') return route.fulfill({ json: { content: [], totalPages: 0, totalElements: 0, number: 0, size: 100 } });
    return route.abort();
  });
  await seite.goto('/bestellungen/bedarf/projekt/19');
  await seite.getByRole('button', { name: 'HiCAD-Import' }).click();
  await seite.getByLabel(/Excel-Datei aus HiCAD auswählen/).setInputFiles({ name: 'zu-gross.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.alloc(10 * 1024 * 1024 + 1) });
  await expect(seite.locator('[role="dialog"] p[role="alert"]')).toContainText('höchstens 10 MiB');
  await expect(seite.getByTestId('toast-container').getByRole('alert')).toContainText('höchstens 10 MiB');
  await expect(seite.getByRole('button', { name: 'Analysieren' })).toBeDisabled();
  expect(pfade.some(pfad => pfad.startsWith('/api/einkauf/hicad'))).toBe(false);
  await designPruefung(seite, prüfinformationen, 'einkauf-hicad-dateigrenze');
});
