import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test.beforeEach(async ({ page }) => {
  for (const path of ['bestellungen/73/revisionen/3/versandstatus', 'bestellungen/73/belege', 'bestellungen/73/lieferungen', 'berechtigungen', 'zeugnisse?bestellungId=73']) {
    await page.route(`**/api/einkauf/${path}`, route => route.fulfill({ json: [] }));
  }
  await page.route('**/api/einkauf/bestellungen/73/rechnungsabgleich', route => route.fulfill({ json: { bestellungId: 73, positionen: [], unbelegteDokumentIds: [] } }));
});

test('Bestellung zeigt Kontakt, Versandstatus und unveränderlichen Positionssnapshot', async ({ page }, testInfo) => {
  const fremdeAntworten: string[] = [];
  let freigegeben = false;
  let pdfAbrufe = 0;
  const pdf = dummyPdf();
  await page.route('**/api/einkauf/pdf-vorschau/55', route => { pdfAbrufe++; return route.fulfill({ contentType: 'application/pdf', body: pdf }); });
  page.on('response', response => { if (!['localhost', '127.0.0.1'].includes(new URL(response.url()).hostname)) fremdeAntworten.push(response.url()); });
  await page.route('**/api/notifications/summary', route => route.fulfill({ json: {} }));
  await page.route('**/api/auth/me', route => route.fulfill({ json: { id: 1, displayName: 'Max Mustermann', username: 'test@example.com', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false } }));
  await page.route('**/api/email-textvorlagen', route => route.fulfill({ json: [{ id: 4, dokumentTyp: 'EINKAUF_BESTELLUNG', aktiv: true, standard: true }] }));
  await page.route('**/api/einkauf/bestellungen/73/vorschau?templateId=4', route => route.fulfill({ json: { version: 4, vorschauHash: 'hash', subject: 'Bestellung B-2026-0073', htmlBody: '<p>Dummy</p>', empfaenger: 'einkauf@example.test', pdfDateiId: 55, anlageVersionIds: [], revisionsNummer: 2, eigeneKundennummer: '00017', liefertermin: '2026-10-14', antwortfrist: '2026-10-01', anlagen: [] } }));
  await page.route('**/api/einkauf/bestellungen/73/freigeben', async route => { freigegeben = true; await route.fulfill({ json: { id: 91, version: 1, typ: 'BESTELLUNG', vorgangId: 73, revisionId: 4, status: 'ANGENOMMEN', fehlerCode: null, erstelltAm: '2026-09-24T08:00:00Z', angenommenAm: '2026-09-24T08:00:03Z' } }); });
  await page.route('**/api/einkauf/bestellungen/73/revisionen/4/versandstatus', route => route.fulfill({ json: freigegeben ? [{ id: 91, version: 1, typ: 'BESTELLUNG', vorgangId: 73, revisionId: 4, status: 'ANGENOMMEN', fehlerCode: null, erstelltAm: '2026-09-24T08:00:00Z', angenommenAm: '2026-09-24T08:00:03Z', archiviert: true, messageId: 'dummy-message' }] : [] }));
  await page.route('**/api/einkauf/bestellungen/73/storno-anfragen', route => route.fulfill({ json: [] }));
  await page.route('**/api/einkauf/bestellungen/73/mengen', route => route.fulfill({ json: [{ bedarfId: 71, reserviert: freigegeben ? 0 : 3, bestellt: freigegeben ? 3 : 0, geliefert: 0, storniert: 0, offen: freigegeben ? 3 : 0 }] }));
  await page.route('**/api/einkauf/bestellungen/73', route => route.fulfill({ json: {
    id: 73, version: 4, nummer: 'B-2026-0073', lieferantId: 8, angebotsversionId: 22, anfrageRevisionId: 13,
    empfaenger: { lieferantenname: 'Musterstahl GmbH', email: 'einkauf@example.test' }, status: freigegeben ? 'BESTELLT' : 'ENTWURF', lieferantenStatus: 'AUSSTEHEND',
    revisionen: [{ id: 3, nummer: 1, version: 3, snapshot: { liefertermin: '2026-10-13' }, sha256: 'hash-alt', versandId: 90, verworfen: false, angenommenAm: '2026-09-23T08:10:00Z', externerNachweis: null, positionen: [{ id: 8, snapshot: { bezeichnung: 'Träger Revision A', werkstoff: 'S235JR', basis: { menge: 2, einheit: 'METER' } }, menge: 2, nettoEinzelpreis: 12, herkuenfte: [] }] }, { id: 4, nummer: 2, version: 4, snapshot: { liefertermin: '2026-10-14' }, sha256: 'hash', versandId: freigegeben ? 91 : null, verworfen: false, angenommenAm: freigegeben ? '2026-09-24T08:00:03Z' : null, externerNachweis: null, positionen: [{ id: 9, snapshot: { bezeichnung: 'Träger Revision B', werkstoff: 'S235JR', basis: { menge: 3, einheit: 'METER' } }, menge: 3, nettoEinzelpreis: 12.5, herkuenfte: [{ bedarfId: 71, version: 3, menge: 3 }] }] }],
  } }));
  await page.goto('/bestellungen/73');
  await expect(page.getByRole('heading', { name: 'B-2026-0073', exact: true }).first()).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Träger Revision B', exact: true })).toBeVisible();
  await expect(page.getByText('Noch nicht versandt', { exact: true })).toBeVisible();
  await page.getByRole('combobox', { name: 'Bestellfassung' }).click();
  await page.getByRole('option', { name: 'Fassung 1' }).click();
  await expect(page.getByText(/Angenommen am 23\.9\.2026/)).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Träger Revision A', exact: true })).toBeVisible();
  await page.getByRole('combobox', { name: 'Bestellfassung' }).click();
  await page.getByRole('option', { name: 'Fassung 2' }).click();
  await page.getByRole('button', { name: 'Vorschau und Freigabe' }).click();
  await page.getByRole('button', { name: 'Vorschau laden' }).click();
  await expect(page.getByText('Eigene Kundennummer: 00017')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Bestellung freigeben' })).toBeDisabled();
  await page.getByRole('button', { name: 'PDF ansehen' }).click();
  await expect.poll(() => pdfAbrufe).toBeGreaterThan(0);
  // The network-isolated suite uses the viewer's supported blob fallback (no CDN).
  const pdfFrame = page.locator('iframe[src^="blob:"]');
  await expect(pdfFrame).toBeVisible();
  const pdfLength = await pdfFrame.evaluate(async element => (await (await fetch((element as HTMLIFrameElement).src.split('#')[0])).arrayBuffer()).byteLength);
  expect(pdfLength).toBe(pdf.length);
  await page.keyboard.press('Escape');
  await page.getByRole('checkbox', { name: /PDF und Empfänger geprüft/i }).check();
  await designPruefung(page, testInfo, 'einkauf-bestellfreigabe-dialog', { primaerAktion: page.getByRole('button', { name: 'Bestellung freigeben' }) });
  await page.getByRole('button', { name: 'Bestellung freigeben' }).click();
  await expect(page.getByText('Bestellung wurde zur Freigabe übergeben.', { exact: false })).toBeVisible();
  await expect(page.getByText('3 bestellt · 0 geliefert · 3 offen')).toBeVisible();
  await expect(page.getByText(/Versand: Vom Mailserver angenommen/)).toBeVisible();
  await page.route('**/api/einkauf/bestellungen/73/mengen', route => route.fulfill({ json: [{ bedarfId: 71, bestellt: 3, geliefert: 1, offen: 2 }] }));
  await page.route('**/api/einkauf/bestellungen/73/storno-vorschau', route => { expect(route.request().postDataJSON().anteile).toEqual([{ bedarfId: 71, version: 3, menge: 2 }]); return route.fulfill({ json: { version: 4, vorschauId: 'dummy', vorschauHash: 'hash', subject: 'Stornoanfrage B-2026-0073', htmlBody: '<p>Bitte zwei Meter stornieren.</p>', empfaenger: 'einkauf@example.test', revisionId: 4 } }); });
  await page.reload();
  await expect(page.getByText('3 bestellt · 1 geliefert · 2 offen')).toBeVisible();
  await page.getByRole('button', { name: 'Storno anfragen', exact: true }).click();
  await expect(page.getByLabel('Stornomenge Bedarf 71')).toHaveValue('2');
  await page.getByLabel('Grund der Stornoanfrage').fill('Restmenge entfällt');
  await page.getByRole('button', { name: 'Vorschau erstellen' }).click();
  await designPruefung(page, testInfo, 'einkauf-stornodialog', { primaerAktion: page.getByRole('button', { name: 'Stornoanfrage senden' }) });
  await page.getByRole('button', { name: 'Abbrechen', exact: true }).click();
  expect(fremdeAntworten).toEqual([]);
  await designPruefung(page, testInfo, 'einkauf-bestellungen', { primaerAktion: page.getByRole('button', { name: 'Zur Übersicht' }) });
});

test('externer Versand wird erst mit registrierter PDF-Datei und Versanddatum bestätigt', async ({ page }, testInfo) => {
  let externPayload: Record<string, unknown> | null = null;
  page.on('response', response => { if (!['localhost', '127.0.0.1'].includes(new URL(response.url()).hostname)) throw new Error(`Unerwartete externe Antwort: ${response.url()}`); });
  await page.route('**/api/notifications/summary', route => route.fulfill({ json: {} }));
  await page.route('**/api/auth/me', route => route.fulfill({ json: { id: 1, displayName: 'Max Mustermann', username: 'test@example.com', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false } }));
  await page.route('**/api/einkauf/bestellungen/73/revisionen/4/versandstatus', route => route.fulfill({ json: [] }));
  await page.route('**/api/einkauf/bestellungen/73/storno-anfragen', route => route.fulfill({ json: [] }));
  await page.route('**/api/einkauf/bestellungen/73/mengen', route => route.fulfill({ json: [] }));
  await page.route('**/api/einkauf/bestellungen/73/belege', route => route.fulfill({ json: [{ lieferantDokumentId: 56, typ: 'SONSTIG', dateiname: 'uebergabe.pdf', verfuegbar: true }] }));
  await page.route('**/api/einkauf/bestellungen/73/belege/56/datei', route => route.fulfill({ json: { lieferantDokumentId: 56, typ: 'SONSTIG', dateiname: 'uebergabe.pdf', verfuegbar: true, dateiId: 89 } }));
  await page.route('**/api/einkauf/bestellungen/73/extern-gesendet', async route => { externPayload = route.request().postDataJSON(); await route.fulfill({ status: 200 }); });
  await page.route('**/api/einkauf/bestellungen/73', route => route.fulfill({ json: {
    id: 73, version: 4, nummer: 'B-2026-0073', lieferantId: 8, angebotsversionId: null, anfrageRevisionId: null,
    empfaenger: { lieferantenname: 'Musterstahl GmbH', email: 'einkauf@example.test' }, status: 'ENTWURF', lieferantenStatus: 'AUSSTEHEND',
    revisionen: [{ id: 4, nummer: 1, version: 4, snapshot: { liefertermin: '2026-10-14' }, sha256: 'hash', versandId: null, verworfen: false,
      angenommenAm: externPayload ? '2026-09-24T08:00:00Z' : null, externerNachweis: externPayload ? { dateiId: externPayload.dateiId } : null,
      positionen: [{ id: 9, snapshot: { bezeichnung: 'Träger Revision B', werkstoff: 'S235JR', basis: { menge: 3, einheit: 'METER' } }, menge: 3, nettoEinzelpreis: 12.5, herkuenfte: [] }] }],
  } }));
  await page.goto('/bestellungen/73');
  await page.getByRole('button', { name: 'Extern versandt' }).click();
  await page.getByRole('button', { name: 'Versanddatum' }).click();
  await page.getByRole('button', { name: 'Heute', exact: true }).click();
  await page.getByRole('button', { name: 'uebergabe.pdf zuordnen' }).click();
  await page.getByRole('textbox', { name: 'Begründung' }).fill('Persönlich übergeben');
  await page.getByRole('button', { name: 'Versand bestätigen' }).click();
  await expect.poll(() => externPayload).toMatchObject({ version: 4, dateiId: 89, begruendung: 'Persönlich übergeben', versendetAm: expect.any(String) });
  await expect(page.getByText(/Externer Versand mit Beleg dokumentiert/).first()).toBeVisible();
  await designPruefung(page, testInfo, 'einkauf-bestellungen-extern', { primaerAktion: page.getByRole('button', { name: 'Zur Übersicht' }) });
});

// Minimal one-page dummy PDF; byte offsets are calculated for a real viewer parse.
function dummyPdf(): Buffer {
  let source = '%PDF-1.4\n'; const offsets = [0];
  const objects = ['<< /Type /Catalog /Pages 2 0 R >>', '<< /Type /Pages /Kids [3 0 R] /Count 1 >>', '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 400] /Resources << >> /Contents 4 0 R >>', '<< /Length 0 >>\nstream\n\nendstream'];
  objects.forEach((body, index) => { offsets.push(Buffer.byteLength(source)); source += `${index + 1} 0 obj\n${body}\nendobj\n`; });
  const xref = Buffer.byteLength(source);
  source += `xref\n0 5\n0000000000 65535 f \n${offsets.slice(1).map(offset => `${String(offset).padStart(10, '0')} 00000 n \n`).join('')}trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;
  return Buffer.from(source);
}
