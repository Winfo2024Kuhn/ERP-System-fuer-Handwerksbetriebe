import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

const bestellung = {
  id: 73, version: 5, nummer: 'B-2026-0073', lieferantId: 8, angebotsversionId: null, anfrageRevisionId: null,
  empfaenger: { lieferantId: 8, kontaktId: null, lieferantenname: 'Testlieferant', email: 'test@example.com', name: null, anrede: null, eigeneKundennummer: null },
  status: 'TEILGELIEFERT', lieferantenStatus: 'BESTAETIGT',
  revisionen: [{ id: 33, nummer: 1, version: 1, snapshot: {}, sha256: 'hash', versandId: 19, verworfen: false,
    angenommenAm: '2026-09-20T09:00:00Z', externerNachweis: null,
    positionen: [
      { id: 9, snapshot: { bezeichnung: 'Stahlträger', basis: { einheit: 'STUECK' }, dokumente: [] }, menge: 4, nettoEinzelpreis: 10, herkuenfte: [{ bedarfId: 15, version: 2, menge: 4 }] },
      { id: 10, snapshot: { bezeichnung: 'Fremdprofil', basis: { einheit: 'STUECK' }, dokumente: [] }, menge: 1, nettoEinzelpreis: 20, herkuenfte: [{ bedarfId: 16, version: 1, menge: 1 }] },
    ] }],
};

interface E2eErwartung {
  id: number; version: number; revisionId: number; bestellPositionId: number; art: string; grundlage: string;
  grundlageVersion: string; frist: string; status: string; dateiIds: number[]; lieferPositionIds: number[];
  chargeIds: number[]; materialFreigegeben: boolean;
  chargeStaende: Array<{ chargeId: number; status: string; version: number; materialFreigegeben: boolean }>;
}
const expectation = (): E2eErwartung => ({ id: 111, version: 1, revisionId: 33, bestellPositionId: 9, art: 'ZEUGNIS_3_1', grundlage: 'EN 10204', grundlageVersion: '2026', frist: '2026-10-01', status: 'ANGEFORDERT', dateiIds: [], lieferPositionIds: [], chargeIds: [], materialFreigegeben: false,
  chargeStaende: [{ chargeId: 101, status: 'ANGEFORDERT', version: 1, materialFreigegeben: false }, { chargeId: 102, status: 'ANGEFORDERT', version: 1, materialFreigegeben: false }] });

test('erfasst die zweite Teillieferung und ordnet ein Zeugnis mehreren Chargen zu, bevor es geprüft wird', async ({ page }, testInfo) => {
  const fremdeAntworten: string[] = [];
  page.on('response', response => { if (!['localhost', '127.0.0.1'].includes(new URL(response.url()).hostname)) fremdeAntworten.push(response.url()); });
  let lieferungen = [{ id: 81, bestellungId: 73, revisionId: 33, lieferscheinId: null, eingang: '2026-09-20T10:00:00Z', positionen: [
    { id: 91, bestellPositionId: 9, menge: 2, charge: 'CH-1', schmelznummer: 'SM-1', projektAnteile: [{ bedarfId: 15, version: 2, menge: 2 }], chargen: [{ id: 101, kennung: 'CH-1', schmelznummer: 'SM-1' }] },
  ] }];
  let erwartet = [expectation()];
  let gelieferteMenge = 2;
  const pruefRechte = ['ZEUGNIS_PRUEFEN'];
  const createdPayloads: unknown[] = [];

  await page.route('**/api/auth/me', route => route.fulfill({ json: { id: 1, displayName: 'Max Mustermann', username: 'test@example.com', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false } }));
  await page.route('**/api/notifications/summary', route => route.fulfill({ json: {} }));
  await page.route('**/api/einkauf/bestellungen?page=0&size=20', route => route.fulfill({ json: { content: [{ id: 73, nummer: 'B-2026-0073', lieferantId: 8, status: 'TEILGELIEFERT', lieferantenStatus: 'BESTAETIGT', angelegtAm: '2026-09-24' }] } }));
  await page.route('**/api/einkauf/bestellungen/73', route => route.fulfill({ json: bestellung }));
  await page.route('**/api/einkauf/bestellungen/73/mengen', route => route.fulfill({ json: [{ bedarfId: 15, reserviert: 0, bestellt: 4, geliefert: gelieferteMenge, storniert: 0, offen: 4 - gelieferteMenge }, { bedarfId: 16, reserviert: 0, bestellt: 1, geliefert: 0, storniert: 0, offen: 1 }] }));
  await page.route('**/api/einkauf/bestellungen/73/lieferungen', async route => {
    if (route.request().method() === 'GET') return route.fulfill({ json: lieferungen });
    const body = route.request().postDataJSON(); createdPayloads.push(body);
    gelieferteMenge += body.positionen[0].menge;
    lieferungen = [...lieferungen, { id: 82, bestellungId: 73, revisionId: 33, lieferscheinId: null, eingang: body.eingang, positionen: [
      { id: 92, ...body.positionen[0], chargen: [{ id: 102, kennung: body.positionen[0].charge, schmelznummer: body.positionen[0].schmelznummer }] },
    ] }];
    return route.fulfill({ status: 201, json: { id: 82 } });
  });
  await page.route('**/api/einkauf/zeugnisse?bestellungId=73', route => route.fulfill({ json: erwartet }));
  await page.route('**/api/einkauf/berechtigungen', route => route.fulfill({ json: pruefRechte }));
  await page.route('**/api/einkauf/bestellungen/73/belege', route => route.fulfill({ json: [{ lieferantDokumentId: 44, typ: 'SONSTIG', dateiname: 'Stahlzeugnis.pdf', verfuegbar: true }] }));
  await page.route('**/api/einkauf/bestellungen/73/belege/44/datei', route => route.fulfill({ json: { dateiId: 504, lieferantDokumentId: 44, typ: 'SONSTIG', dateiname: 'Stahlzeugnis.pdf', verfuegbar: true } }));
  await page.route('**/api/einkauf/zeugnisse/111/eingang/504', route => route.fulfill({ status: 200, json: {} }));
  await page.route('**/api/einkauf/zeugnisse/504/zuordnen', async route => {
    const body = route.request().postDataJSON(); createdPayloads.push(body);
    erwartet = [{ ...expectation(), dateiIds: [504], lieferPositionIds: [91, 92], chargeIds: [101, 102], status: 'ZUGEORDNET' }];
    return route.fulfill({ json: { erwartungen: erwartet, klaerungNoetig: false, chargen: [
      { zuordnungId: 601, erwartungId: 111, chargeId: 101, status: 'ZUGEORDNET', version: 1, materialFreigegeben: false },
      { zuordnungId: 602, erwartungId: 111, chargeId: 102, status: 'ZUGEORDNET', version: 1, materialFreigegeben: false },
    ] } });
  });
  await page.route('**/api/einkauf/zeugnisse/601/pruefen', route => route.fulfill({ json: { id: 701, erwartungId: 111, ergebnis: 'BESTANDEN', begruendung: 'Dummy-Prüfung', grundlageVersion: '2026', akteurId: 1, geprueftAm: '2026-09-24T12:00:00Z', materialFreigegeben: true } }));
  await page.route('**/api/einkauf/zeugnisse/602/pruefen', route => route.fulfill({ json: { id: 702, erwartungId: 111, ergebnis: 'ABGELEHNT', begruendung: 'Falsche Schmelznummer', grundlageVersion: '2026', akteurId: 1, geprueftAm: '2026-09-24T12:00:00Z', materialFreigegeben: false } }));

  await page.goto('/einkauf/lieferungen');
  await expect(page.getByText('2 von 4 geliefert · 2 offen')).toBeVisible();
  await expect(page.getByText('Zeugnis 3.1 fehlt · EN 10204', { exact: false })).toBeVisible();
  await expect(page.getByText('Material nicht freigegeben')).toBeVisible();

  await page.getByRole('button', { name: 'Lieferung erfassen' }).click();
  await page.getByLabel('Menge (STUECK)').fill('2');
  await page.getByLabel('Charge').fill('CH-2');
  await page.getByLabel('Schmelznummer').fill('SM-2');
  await page.getByRole('button', { name: 'Lieferung erfassen' }).last().click();
  await expect(page.getByText('4 von 5 geliefert · 0 offen')).toBeVisible();
  expect(createdPayloads[0]).toMatchObject({ version: 5, positionen: [{ bestellPositionId: 9, menge: 2, charge: 'CH-2', projektAnteile: [{ bedarfId: 15, version: 2, menge: 2 }] }] });

  await page.getByRole('button', { name: /Zeugnisse und Prüfung anzeigen/ }).click();
  await page.getByRole('button', { name: /Stahlzeugnis\.pdf zuordnen/ }).click();
  await page.getByRole('button', { name: 'Zeugnis zuordnen' }).click();
  await expect(page.getByText('Charge 101: ZUGEORDNET')).toBeVisible();
  await expect(page.getByText('Charge 102: ZUGEORDNET')).toBeVisible();
  expect(createdPayloads[1]).toMatchObject({ dokumentId: 504, erwartungIds: [111], lieferPositionIds: [91, 92], chargeIds: [101, 102] });

  await page.getByRole('button', { name: 'Zeugnis prüfen · Charge 101' }).click();
  await page.getByLabel('Begründung').fill('Dummy-Prüfung');
  await page.getByRole('button', { name: 'Prüfung speichern' }).click();
  await expect(page.getByText('Material ist für diese Charge freigegeben.')).toBeVisible();
  await page.getByRole('button', { name: 'Zeugnis prüfen · Charge 102' }).click();
  await page.getByRole('button', { name: 'Abgelehnt' }).click();
  await page.getByLabel('Begründung').fill('Falsche Schmelznummer');
  await page.getByRole('button', { name: 'Prüfung speichern' }).click();
  await expect(page.getByText('Nur Personen mit Zeugnis-Prüfrecht können Zeugnisse prüfen und Material freigeben.')).toHaveCount(0);
  expect(fremdeAntworten).toEqual([]);
  await designPruefung(page, testInfo, 'einkauf-lieferungen', { primaerAktion: page.getByRole('button', { name: 'Aktualisieren' }) });
});

test('Nutzer ohne Prüfberechtigung kann die Zeugnisprüfung nicht freigeben', async ({ page }) => {
  await page.route('**/api/auth/me', route => route.fulfill({ json: { id: 1, displayName: 'Max Mustermann', username: 'test@example.com', active: true, roles: ['EINKAUF'], admin: false, requiresInitialSetup: false } }));
  await page.route('**/api/notifications/summary', route => route.fulfill({ json: {} }));
  await page.route('**/api/einkauf/bestellungen?page=0&size=20', route => route.fulfill({ json: { content: [{ id: 73, nummer: 'B-2026-0073', lieferantId: 8, status: 'TEILGELIEFERT', lieferantenStatus: 'BESTAETIGT', angelegtAm: '2026-09-24' }] } }));
  await page.route('**/api/einkauf/bestellungen/73/mengen', route => route.fulfill({ json: [{ bedarfId: 15, reserviert: 0, bestellt: 4, geliefert: 2, storniert: 0, offen: 2 }] }));
  await page.route('**/api/einkauf/bestellungen/73/lieferungen', route => route.fulfill({ json: [{ id: 81, bestellungId: 73, revisionId: 33, lieferscheinId: null, eingang: '2026-09-20T10:00:00Z', positionen: [{ id: 91, bestellPositionId: 9, menge: 2, charge: 'CH-1', schmelznummer: 'SM-1', projektAnteile: [], chargen: [{ id: 101, kennung: 'CH-1', schmelznummer: 'SM-1' }] }] }] }));
  await page.route('**/api/einkauf/zeugnisse?bestellungId=73', route => route.fulfill({ json: [{ ...expectation(), dateiIds: [504], chargeIds: [101], materialFreigegeben: false, chargeStaende: [{ chargeId: 101, status: 'ZUGEORDNET', version: 1, materialFreigegeben: false }] }] }));
  await page.route('**/api/einkauf/berechtigungen', route => route.fulfill({ json: [] }));
  await page.goto('/einkauf/lieferungen');
  await page.getByRole('button', { name: /Zeugnisse und Prüfung anzeigen/ }).click();
  await expect(page.getByText('Nur Personen mit Zeugnis-Prüfrecht können Zeugnisse prüfen und Material freigeben.')).toBeVisible();
  await expect(page.getByRole('button', { name: /Zeugnis prüfen/ })).toHaveCount(0);
});
