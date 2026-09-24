import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test('gleicht eine Teilrechnung ab und legt eine Reklamation mit echten Bestellbezügen an', async ({ page }, testInfo) => {
  const fremdeAntworten: string[] = [];
  const belegPayloads: unknown[] = [];
  let abrechnungsStand = 0;
  page.on('response', response => { if (!['localhost', '127.0.0.1'].includes(new URL(response.url()).hostname)) fremdeAntworten.push(response.url()); });
  await page.route('**/api/auth/me', route => route.fulfill({ json: { id: 1, displayName: 'Max Mustermann', username: 'test@example.com', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false } }));
  await page.route('**/api/notifications/summary', route => route.fulfill({ json: {} }));
  await page.route('**/api/einkauf/bestellungen?page=0&size=20', route => route.fulfill({ json: { content: [{ id: 73, nummer: 'B-2026-0073', lieferantId: 8, status: 'TEILGELIEFERT', lieferantenStatus: 'BESTAETIGT', angelegtAm: '2026-09-24' }] } }));
  await page.route('**/api/einkauf/bestellungen/73/mengen', route => route.fulfill({ json: [{ bedarfId: 15, reserviert: 0, bestellt: 4, geliefert: 2, storniert: 0, offen: 2 }] }));
  await page.route('**/api/einkauf/bestellungen/73/lieferungen', route => route.fulfill({ json: [{ id: 81, bestellungId: 73, revisionId: 33, lieferscheinId: 40, eingang: '2026-09-20T10:00:00Z', positionen: [{ id: 91, bestellPositionId: 9, menge: 2, charge: 'CH-1', schmelznummer: 'SM-1', projektAnteile: [], chargen: [{ id: 101, kennung: 'CH-1', schmelznummer: 'SM-1' }] }] }] }));
  await page.route('**/api/einkauf/zeugnisse?bestellungId=73', route => route.fulfill({ json: [] }));
  await page.route('**/api/einkauf/bestellungen/73/rechnungsabgleich', route => route.fulfill({ json: {
    bestellungId: 73,
    positionen: [{ positionId: 9, bezeichnung: 'Stahlträger', einheit: 'STUECK', vereinbart: 4, bestaetigt: 4, geliefert: 2, kumuliertAbgerechnet: abrechnungsStand, offen: 2,
      pruefen: true, quellen: [{ typ: 'BELEG', id: 60, bezeichnung: 'Teilrechnung R-60', betrag: 20 }],
      abweichungen: [{ positionId: 9, feld: 'menge', vereinbart: 4, abgerechnet: 5, differenz: 1, rechenweg: '5 auf der Rechnung − 4 vereinbart = 1 Stück Abweichung', quellen: [{ typ: 'BELEG', id: 60, bezeichnung: 'Teilrechnung R-60', betrag: 50 }] }] }],
    unbelegteDokumentIds: [42],
  } }));
  await page.route('**/api/einkauf/bestellungen/73', route => route.fulfill({ json: {
    id: 73, version: 5, nummer: 'B-2026-0073', lieferantId: 8, angebotsversionId: null, anfrageRevisionId: null,
    empfaenger: { lieferantId: 8, kontaktId: null, lieferantenname: 'Testlieferant', email: 'test@example.com', name: null, anrede: null, eigeneKundennummer: null },
    status: 'TEILGELIEFERT', lieferantenStatus: 'BESTAETIGT',
    revisionen: [{ id: 33, nummer: 1, version: 1, snapshot: {}, sha256: 'hash', versandId: 19, verworfen: false, angenommenAm: '2026-09-20T09:00:00Z', externerNachweis: null,
      positionen: [{ id: 9, snapshot: { bezeichnung: 'Stahlträger', basis: { einheit: 'STUECK' }, dokumente: [] }, menge: 4, nettoEinzelpreis: 10, herkuenfte: [{ bedarfId: 15, version: 2, menge: 4 }] }] }],
  } }));
  await page.route('**/api/einkauf/bestellungen/73/belege', route => route.fulfill({ json: [{ lieferantDokumentId: 42, typ: 'RECHNUNG', dateiname: 'Teilrechnung.pdf', verfuegbar: true }] }));
  await page.route('**/api/einkauf/bestellungen/73/belege/42/datei', route => route.fulfill({ json: { dateiId: 502, lieferantDokumentId: 42, typ: 'RECHNUNG', dateiname: 'Teilrechnung.pdf', verfuegbar: true } }));
  await page.route('**/api/einkauf/belege/42/zuordnung', async route => {
    const body = route.request().postDataJSON(); belegPayloads.push(body);
    abrechnungsStand += body.positionen[0].menge * body.positionen[0].nettoEinzelpreis;
    return route.fulfill({ status: 200, json: { id: 91, dokumentId: 42, bestellungId: 73 } });
  });
  await page.route('**/api/reklamationen/lieferant/8', async route => {
    const body = route.request().postDataJSON(); belegPayloads.push(body);
    return route.fulfill({ status: 201, json: { id: 301, lieferantId: 8, beschreibung: body.beschreibung, bestellungId: body.bestellungId, bestellPositionId: body.bestellPositionId, rechnungId: body.rechnungId, lieferscheinId: body.lieferscheinId, status: 'OFFEN' } });
  });

  await page.goto('/einkauf/lieferungen');
  await expect(page.getByText('2 von 4 geliefert · 2 offen')).toBeVisible();
  await page.getByRole('button', { name: /Rechnungen abgleichen anzeigen/ }).click();
  await expect(page.getByText(/5 auf der Rechnung − 4 vereinbart/)).toBeVisible();
  await expect(page.getByText(/Nicht zugeordnete Belege: 42/)).toBeVisible();
  await expect(page.getByRole('link', { name: /Rechnung Teilrechnung R-60 in den offenen Posten ansehen/ })).toHaveAttribute('href', '/offeneposten?tab=eingang&dokumentId=60');

  await page.getByRole('button', { name: /Teilrechnung\.pdf zuordnen/ }).click();
  await page.getByLabel('Rechnungsmenge · Stahlträger').fill('2');
  await page.getByLabel('Netto-Einzelpreis · Stahlträger').fill('10');
  await page.getByRole('button', { name: 'Rechnung zuordnen' }).click();
  await expect(page.getByText('Rechnungsbeleg wurde zugeordnet.')).toBeVisible();
  expect(belegPayloads[0]).toMatchObject({ bestellungId: 73, version: 5, art: 'RECHNUNG', positionen: [{ bestellPositionId: 9, menge: 2, nettoEinzelpreis: 10 }] });

  await page.getByRole('button', { name: /Abweichung reklamieren/ }).click();
  await expect(page.getByRole('dialog', { name: 'Neue Reklamation erstellen' })).toBeVisible();
  await expect(page.getByText('Bezug: Bestellung 73 · Position 9 · Rechnung 60')).toBeVisible();
  await page.getByLabel('Beschreibung / Grund der Reklamation').fill('Die Rechnungsmenge weicht ab.');
  await page.getByRole('button', { name: 'Reklamation erstellen' }).click();
  await expect(page.getByText('Reklamation mit Belegbezug erstellt.')).toBeVisible();
  expect(belegPayloads[1]).toMatchObject({ bestellungId: 73, bestellPositionId: 9, rechnungId: 60 });
  expect(fremdeAntworten).toEqual([]);
  await page.getByRole('button', { name: 'Aktualisieren' }).scrollIntoViewIfNeeded();
  await designPruefung(page, testInfo, 'einkauf-rechnungsabgleich', { primaerAktion: page.getByRole('button', { name: 'Aktualisieren' }) });
});
