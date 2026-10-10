import { readFile } from 'node:fs/promises';
import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { blockiereFremdeNetzwerkzugriffe } from './hilfen/api';
import { designPruefung } from './hilfen/design';
import type { Konfiguration, ExportRequest } from '../src/features/monatsabschluss/types';

/**
 * Stubs fuer den DATEV-Export im Monatsabschluss. Eingerichtet wird DATEV seit
 * dem Umzug unter Einstellungen -> Zeiterfassung; der Monatsabschluss liest die
 * Konfiguration nur noch (GET) und schreibt nie mehr dorthin.
 * Nur Dummy-Daten (DSGVO).
 */
async function stubbeMonatsabschluss(page: Page, optionen: { admin: boolean; config: Konfiguration }) {
 const stand = { vorpruefungen: 0, exports: 0, geprueft: undefined as ExportRequest | undefined, konfigSchreibversuche: 0 };
 const zahlen = { istStunden: 120.5, sollStunden: 160, abwesenheitsStunden: 0, feiertagsStunden: 0, korrekturStunden: 1.5, gesamtIst: 122, differenz: -38, urlaubStunden: 0, krankheitStunden: 0, zeitausgleichStunden: 0, sonstigeAbwesenheitStunden: 0 };
 await page.route('**/api/**', async route => {
  const url = new URL(route.request().url()); const path = url.pathname; let body: unknown = []; let status = 200;
  const jahr = Number(url.searchParams.get('jahr')); const monat = Number(url.searchParams.get('monat'));
  if (path === '/api/auth/me') body = { id: 1, username: 'test', displayName: 'Max Mustermann', active: true, admin: optionen.admin, roles: optionen.admin ? ['ADMIN'] : ['USER'], requiresInitialSetup: false };
  if (path.endsWith('/berechtigung')) body = { darfMonatAbschliessen: true };
  if (path === '/api/mitarbeiter') body = [{ id: 1, vorname: 'Max', nachname: 'Mustermann' }, { id: 2, vorname: 'Erika', nachname: 'Mustermann' }];
  if (path.endsWith('/uebersicht')) body = { items: [1, 2].map(id => ({ referenz: { mitarbeiterId: id, jahr, monat }, mitarbeiterName: id === 1 ? 'Max Mustermann' : 'Erika Mustermann', abteilungIds: [], festgeschrieben: true, version: 3, festgeschriebenAm: '2026-09-01T10:00:00', kennzahlen: zahlen })), totalElements: 2, page: 0, size: 50, summen: Object.fromEntries(Object.entries(zahlen).map(([k, v]) => [k, v * 2])), auswahl: [1, 2].map(mitarbeiterId => ({ mitarbeiterId, jahr, monat, version: 3, festgeschrieben: true })) };
  if (path.endsWith('/datev/konfiguration')) { if (route.request().method() !== 'GET') { stand.konfigSchreibversuche++; status = 405; } body = optionen.config; }
  if (path.endsWith('/vorpruefung')) { stand.vorpruefungen++; stand.geprueft = route.request().postDataJSON(); body = { ...stand.geprueft, gueltig: true, fehler: [], ausschluesse: [{ referenz: stand.geprueft!.auswahl[0], kategorie: 'KORREKTUR', stunden: 1.5, meldung: 'Zeitkontokorrekturen werden nicht ausgezahlt.' }] }; }
  if (path.endsWith('/export')) {
   stand.exports++; expect(route.request().postDataJSON()).toEqual(stand.geprueft);
   if (stand.exports === 1) { status = 409; body = { message: 'Der Monatsstand wurde geändert. Bitte erneut prüfen.' }; }
   else { await route.fulfill({ status: 200, contentType: 'application/octet-stream', headers: { 'Content-Disposition': 'attachment; filename="lodas-2026-08.txt"' }, body: '3;01/08/2026;120,50;01;200;14;\r\n' }); return; }
  }
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
 });
 return stand;
}
const eingerichtet: Konfiguration = { version: 1, ziel: 'LODAS', beraterNr: '001234', mandantenNr: '0012', stundenart: 'SOLL', personalnummern: [{ mitarbeiterId: 1, personalnummer: '00014', stundenart: null }], zuordnungen: ['ARBEIT', 'FEIERTAG', 'URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH', 'KRANKENGELD', 'WIEDEREINGLIEDERUNG'].map(kategorie => ({ kategorie, ausgeschlossen: kategorie !== 'ARBEIT', lohnart: kategorie === 'ARBEIT' ? '0200' : '' })) };

test('DATEV: Einrichten führt Admins in die Einstellungen, Export mit Ausschlüssen, Konflikt und echter Datei', async ({ page }, info) => {
 await blockiereFremdeNetzwerkzugriffe(page);
 const stand = await stubbeMonatsabschluss(page, { admin: true, config: eingerichtet });
 await page.goto('/monatsabschluss'); await page.getByRole('checkbox', { name: 'Max Mustermann auswählen', exact: true }).check();
 const einrichten = page.getByRole('link', { name: 'DATEV einrichten', exact: true });
 await expect(einrichten).toHaveAttribute('href', '/einstellungen#zeiterfassung');
 await expect(page.getByRole('textbox', { name: 'Beraternummer', exact: true })).toHaveCount(0);
 await expect(page.getByText('DATEV richtet ein Admin unter Einstellungen → Zeiterfassung ein.', { exact: true })).toHaveCount(0);
 await designPruefung(page, info, 'monatsabschluss-datev-admin', { primaerAktion: page.getByRole('button', { name: 'Für DATEV exportieren', exact: true }) });
 await page.getByRole('button', { name: 'Mehr zu: Für DATEV exportieren', exact: true }).click();
 await expect(page.getByRole('dialog', { name: 'Für DATEV exportieren' })).toContainText('Es wird nichts automatisch verschickt.');
 await designPruefung(page, info, 'monatsabschluss-datev-info');
 await page.keyboard.press('Escape'); await expect(page.getByRole('dialog', { name: 'Für DATEV exportieren' })).toHaveCount(0);
 await page.getByRole('button', { name: 'Für DATEV exportieren', exact: true }).click();
 const dialog = page.getByRole('dialog', { name: 'DATEV-Export prüfen' }); await expect(dialog).toContainText('Max Mustermann'); await expect(dialog).not.toContainText('Erika Mustermann');
 await expect(dialog.getByText('Als „Arbeit“ gehen die Soll-Stunden (laut Arbeitszeitkonto) ans Steuerbüro.', { exact: true })).toBeVisible();
 await dialog.getByRole('button', { name: 'Vorprüfung starten', exact: true }).click(); await expect(dialog.getByText('1,50 h', { exact: true })).toBeVisible();
 expect(stand.geprueft!.auswahl).toHaveLength(1); expect(stand.geprueft!.auswahl[0].mitarbeiterId).toBe(1); expect(stand.geprueft!.konfigurationVersion).toBe(1);
 const download = dialog.getByRole('button', { name: 'Datei herunterladen', exact: true }); await expect(download).toBeDisabled(); await dialog.getByRole('checkbox').check();
 await designPruefung(page, info, 'datev-vorpruefung', { primaerAktion: download });
 await download.click(); await expect(dialog.getByRole('alert')).toContainText('Bitte erneut prüfen'); await expect(download).toBeDisabled(); expect(stand.vorpruefungen).toBe(1);
 await dialog.getByRole('button', { name: 'Vorprüfung starten', exact: true }).click(); await expect(dialog.getByText('Die Vorprüfung ist erfolgreich.', { exact: true })).toBeVisible(); await expect(dialog.getByRole('checkbox')).not.toBeChecked(); await dialog.getByRole('checkbox').check();
 const received = page.waitForEvent('download'); await download.click(); const file = await received; expect(file.suggestedFilename()).toBe('lodas-2026-08.txt'); await file.saveAs(info.outputPath(file.suggestedFilename())); expect(await file.failure()).toBeNull(); expect(await readFile(info.outputPath(file.suggestedFilename()), 'utf8')).toBe('3;01/08/2026;120,50;01;200;14;\r\n');
 await expect(page.getByText('Datei heruntergeladen – bitte im Steuerbüro importieren.', { exact: true })).toBeVisible();
 expect(stand.konfigSchreibversuche).toBe(0);
});

test('DATEV: Nicht-Admins sehen, wer einrichtet, und den Hinweis auf fehlende Nummern', async ({ page }, info) => {
 await blockiereFremdeNetzwerkzugriffe(page);
 const stand = await stubbeMonatsabschluss(page, { admin: false, config: { ...eingerichtet, beraterNr: '', mandantenNr: '' } });
 await page.goto('/monatsabschluss'); await page.getByRole('checkbox', { name: 'Max Mustermann auswählen', exact: true }).check();
 await expect(page.getByText('DATEV richtet ein Admin unter Einstellungen → Zeiterfassung ein.', { exact: true })).toBeVisible();
 await expect(page.getByRole('link', { name: 'DATEV einrichten', exact: true })).toHaveCount(0);
 await designPruefung(page, info, 'monatsabschluss-datev-ohne-admin', { primaerAktion: page.getByRole('button', { name: 'Für DATEV exportieren', exact: true }) });
 await page.getByRole('button', { name: 'Für DATEV exportieren', exact: true }).click();
 const dialog = page.getByRole('dialog', { name: 'DATEV-Export prüfen' });
 await expect(dialog.getByText(/Beraternummer und Mandantennummer fehlen noch/)).toBeVisible();
 await expect(dialog.getByRole('link')).toHaveCount(0);
 await expect(dialog.getByRole('button', { name: 'Vorprüfung starten', exact: true })).toBeDisabled();
 await designPruefung(page, info, 'monatsabschluss-datev-nummern-fehlen');
 expect(stand.vorpruefungen).toBe(0);
});

test('DATEV sperrt offenen Stand 3 auch außerhalb der sichtbaren Ergebnisseite', async ({ page }, info) => {
 await page.route('**/api/**', async route => {
  const url = new URL(route.request().url()); const path = url.pathname;
  const jahr = Number(url.searchParams.get('jahr')); const monat = Number(url.searchParams.get('monat'));
  const pageNumber = Number(url.searchParams.get('page'));
  const all = Array.from({ length: 51 }, (_, i) => ({ mitarbeiterId: i + 1, jahr, monat, version: 3, festgeschrieben: i < 50 }));
  const kennzahlen = { istStunden: 8, sollStunden: 8, abwesenheitsStunden: 0, feiertagsStunden: 0, korrekturStunden: 0, gesamtIst: 8, differenz: 0, urlaubStunden: 0, krankheitStunden: 0, zeitausgleichStunden: 0, sonstigeAbwesenheitStunden: 0 };
  let body: unknown = [];
  if (path === '/api/auth/me') body = { id: 1, username: 'test', email: 'test@example.com', admin: true, roles: ['ADMIN'], requiresInitialSetup: false };
  if (path.endsWith('/berechtigung')) body = { darfMonatAbschliessen: true };
  if (path === '/api/mitarbeiter') body = all.map(s => ({ id: s.mitarbeiterId, vorname: 'Max', nachname: `Mustermann ${s.mitarbeiterId}` }));
  if (path.endsWith('/uebersicht')) body = { items: all.slice(pageNumber * 50, pageNumber * 50 + 50).map(s => ({ referenz: { mitarbeiterId: s.mitarbeiterId, jahr, monat }, mitarbeiterName: `Max Mustermann ${s.mitarbeiterId}`, abteilungIds: [], festgeschrieben: s.festgeschrieben, version: s.version, festgeschriebenAm: null, kennzahlen })), totalElements: 51, page: pageNumber, size: 50, summen: kennzahlen, auswahl: all };
  await route.fulfill({ json: body });
 });
 await page.goto('/monatsabschluss');
 const exportieren = page.getByRole('button', { name: 'Für DATEV exportieren', exact: true });
 await page.getByRole('checkbox', { name: 'Max Mustermann 1 auswählen', exact: true }).check();
 await expect(exportieren).toBeEnabled();
 await page.getByRole('checkbox', { name: 'Alle gefilterten Mitarbeiter auswählen', exact: true }).check();
 await expect(page.getByText('51 ausgewählt', { exact: true })).toBeVisible();
 await expect(exportieren).toBeDisabled();
 await expect(page.getByText('Bitte die ausgewählten Monate zuerst abschließen und aktualisieren.', { exact: true })).toBeVisible();
 await page.getByRole('button', { name: 'Nächste Seite', exact: true }).click();
 await expect(page.getByRole('checkbox', { name: 'Max Mustermann 51 auswählen', exact: true })).toBeChecked();
 await expect(exportieren).toBeDisabled();
 await page.getByRole('checkbox', { name: 'Alle gefilterten Mitarbeiter auswählen', exact: true }).uncheck();
 await page.getByRole('checkbox', { name: 'Max Mustermann 51 auswählen', exact: true }).check();
 await expect(exportieren).toBeDisabled();
 await page.screenshot({ path: info.outputPath('datev-offener-stand.png') });
});
