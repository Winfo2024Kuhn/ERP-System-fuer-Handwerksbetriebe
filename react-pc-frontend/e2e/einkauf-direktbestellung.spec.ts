import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

for (const preisOffen of [false, true]) {
test(`erstellt eine Direktbestellung mit ${preisOffen ? 'offenem Preis' : 'belegtem gültigem Preis'}`, async ({ page }, testInfo) => {
  const posts: Array<{ path: string; body: unknown }> = []; const fremd: string[] = [];
  page.on('response', response => { if (!['localhost', '127.0.0.1'].includes(new URL(response.url()).hostname)) fremd.push(response.url()); });
  await page.route('**/api/auth/me', route => route.fulfill({ json: { id: 1, displayName: 'Max Mustermann', username: 'test@example.com', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false } }));
  await page.route('**/api/notifications/summary', route => route.fulfill({ json: {} }));
  // Werkstatt-/Vorratsbedarf ohne Projekt: in der EN1090-Oberfläche startet die Direktbestellung auf der Bedarfsseite "Ohne Projektzuordnung".
  const bedarf = { id: 6, version: 2, position: { art: 'ARTIKEL', artikelId: 9, interneReferenz: 'MAT-9', bezeichnung: 'Profil', werkstoff: null, abmessung: null, basis: { menge: 2, einheit: 'METER', stueckzahl: null, einzelLaengeMm: null, kgJeMeter: null, faktorQuelle: null }, schnittForm: null, winkelLinks: null, winkelRechts: null, bearbeitung: null, oberflaeche: null, dokumente: [], anlageVersionIds: [] },
    mengen: { bedarf: 2, lagergedeckt: 0, angefragt: 0, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: 2, disponierbar: 2 }, nachpflegeErforderlich: false, historischerHinweis: null,
    liefergruppe: { lieferadresse: null, bedarfstermin: null, projektId: null, lagerzweck: 'Werkstatt / auf Vorrat' } };
  await page.route('**/api/einkauf/bedarf?**', route => route.fulfill({ json: { totalPages: 1, content: [bedarf] } }));
  await page.route('**/api/einkauf/bedarf/6', route => route.fulfill({ json: bedarf }));
  await page.route('**/api/projekte/simple?**', route => route.fulfill({ json: [] }));
  await page.route('**/api/lieferanten?**', route => route.fulfill({ json: { lieferanten: [{ id: 8, lieferantenname: 'Musterstahl', istAktiv: true }] } }));
  await page.route('**/api/lieferanten/8', route => route.fulfill({ json: { id: 8, lieferantenname: 'Musterstahl', eigeneKundennummer: '0008' } }));
  await page.route('**/api/lieferanten/8/einkauf-kontakte', route => route.fulfill({ json: [{ id: 4, version: 1, name: 'Einkauf', email: 'einkauf@example.test', standardBestellung: true, aktiv: true }] }));
  await page.route('**/api/einkauf/bestellungen/direkt', async route => { posts.push({ path: new URL(route.request().url()).pathname, body: route.request().postDataJSON() }); await route.fulfill({ status: 201, json: { id: 22 } }); });
  await page.route('**/api/einkauf/bestellungen/22', route => route.fulfill({ json: {
    id: 22, version: 0, nummer: 'B-2026-0022', lieferantId: 8, status: 'ENTWURF', lieferantenStatus: 'AUSSTEHEND', empfaenger: { lieferantenname: 'Musterstahl', email: 'einkauf@example.test' },
    revisionen: [{ id: 222, nummer: 1, version: 0, snapshot: {}, verworfen: false, angenommenAm: null, externerNachweis: null, positionen: [{ id: 223, snapshot: { interneReferenz: 'MAT-9', bezeichnung: 'Profil', basis: { einheit: 'METER' } }, menge: 2, nettoEinzelpreis: preisOffen ? null : 5.5, herkuenfte: [{ bedarfId: 6, version: 2, menge: 2 }] }] }],
  } }));
  for (const path of ['revisionen/222/versandstatus', 'storno-anfragen', 'mengen']) await page.route(`**/api/einkauf/bestellungen/22/${path}`, route => route.fulfill({ json: [] }));
  await page.goto('/bestellungen/bedarf/vorrat');
  await page.getByRole('button', { name: 'Bestellung vorbereiten', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Direktbestellung vorbereiten' })).toBeVisible();
  await expect(page.getByText('MAT-9 · Profil')).toBeVisible();
  await page.getByLabel('Bedarf MAT-9 auswählen').check();
  await page.getByRole('button', { name: 'Lieferant wählen' }).click();
  await page.getByText('Musterstahl', { exact: true }).last().click();
  await expect(page.getByText('Kontakt: Einkauf')).toBeVisible();
  if (!preisOffen) {
  await page.getByLabel('Preis MAT-9', { exact: true }).fill('5,50');
  await page.getByLabel('Preis bestätigt am MAT-9').click();
  await page.getByRole('dialog', { name: 'Preis bestätigt am MAT-9 auswählen' }).getByRole('button', { name: 'Heute' }).click();
  await page.getByLabel('Preisquelle MAT-9').fill('Angebot ANG-8, PDF');
  } else {
    await expect(page.getByText('Preis offen', { exact: true })).toBeVisible();
  }
  await expect(page.getByRole('alert')).toHaveCount(0);
  await designPruefung(page, testInfo, `einkauf-direktbestellung-dialog-${preisOffen ? 'preis-offen' : 'bekannter-preis'}`, { primaerAktion: page.getByRole('button', { name: 'Direktbestellung als Entwurf anlegen' }) });
  await page.getByRole('button', { name: 'Direktbestellung als Entwurf anlegen' }).click();
  await expect(page).toHaveURL(/\/bestellungen\/22$/);
  await expect(page.getByRole('heading', { name: 'B-2026-0022', exact: true }).first()).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Profil', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Vorschau und Freigabe' })).toBeVisible();
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(posts).toHaveLength(1);
  expect(posts[0].body).toMatchObject({ lieferantId: 8, empfaenger: { kontaktId: 4, email: 'einkauf@example.test', eigeneKundennummer: '0008' }, paket: [{ bedarfId: 6, version: 2, menge: 2 }], preise: preisOffen ? [] : [{ bedarfId: 6, preis: 5.5, einheit: 'METER', preisHistorieId: null, bestaetigtAm: new Date().toLocaleDateString('sv-SE'), bestaetigungsbeleg: 'Angebot ANG-8, PDF' }] });
  if (preisOffen) {
    await expect(page.getByText('Preis offen', { exact: true })).toBeVisible();
    await expect(page.getByText(/0,00\s*€/)).toHaveCount(0);
  }
  expect(fremd).toEqual([]);
  await designPruefung(page, testInfo, `einkauf-direktbestellung-${preisOffen ? 'preis-offen' : 'bekannter-preis'}`, { primaerAktion: page.getByRole('button', { name: 'Vorschau und Freigabe' }) });
});

}
