import { readFile } from 'node:fs/promises';
import { test, expect } from './hilfen/test';
import { blockiereFremdeNetzwerkzugriffe } from './hilfen/api';
import { designPruefung } from './hilfen/design';
import type { Konfiguration, ExportRequest } from '../src/features/monatsabschluss/types';
test('DATEV: eigene Eingaben, bewusste Auswahl, Ausschlüsse, Konflikt und echte Datei', async ({ page }, info) => {
 await blockiereFremdeNetzwerkzugriffe(page);
 let config: Konfiguration = { version: 0, ziel: 'LODAS', beraterNr: '', mandantenNr: '', personalnummern: [], zuordnungen: [] };
 let vorpruefungen = 0; let exports = 0; let geprueft: ExportRequest | undefined;
 const zahlen = { istStunden: 120.5, sollStunden: 160, abwesenheitsStunden: 0, feiertagsStunden: 0, korrekturStunden: 1.5, gesamtIst: 122, differenz: -38 };
 await page.route('**/api/**', async route => {
  const url = new URL(route.request().url()); const path = url.pathname; let body: unknown = []; let status = 200;
  const jahr = Number(url.searchParams.get('jahr')); const monat = Number(url.searchParams.get('monat'));
  if (path === '/api/auth/me') body = { id: 1, username: 'test', email: 'test@example.com', vorname: 'Max', nachname: 'Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false };
  if (path.endsWith('/berechtigung')) body = { darfMonatAbschliessen: true };
  if (path === '/api/mitarbeiter') body = [{ id: 1, vorname: 'Max', nachname: 'Mustermann' }, { id: 2, vorname: 'Erika', nachname: 'Mustermann' }];
  if (path.endsWith('/uebersicht')) body = { items: [1, 2].map(id => ({ referenz: { mitarbeiterId: id, jahr, monat }, mitarbeiterName: id === 1 ? 'Max Mustermann' : 'Erika Mustermann', abteilungIds: [], festgeschrieben: true, version: 3, festgeschriebenAm: '2026-09-01T10:00:00', kennzahlen: zahlen })), totalElements: 2, page: 0, size: 50, summen: Object.fromEntries(Object.entries(zahlen).map(([k, v]) => [k, v * 2])), auswahl: [1, 2].map(mitarbeiterId => ({ mitarbeiterId, jahr, monat, version: 3 })) };
  if (path.endsWith('/konfiguration')) { if (route.request().method() === 'PUT') config = { ...route.request().postDataJSON(), version: 1 }; body = config; }
  if (path.endsWith('/vorpruefung')) { vorpruefungen++; geprueft = route.request().postDataJSON(); body = { ...geprueft, gueltig: true, fehler: [], ausschluesse: [{ referenz: geprueft!.auswahl[0], kategorie: 'KORREKTUR', stunden: 1.5, meldung: 'Zeitkontokorrekturen werden nicht ausgezahlt.' }] }; }
  if (path.endsWith('/export')) {
   exports++; expect(route.request().postDataJSON()).toEqual(geprueft);
   if (exports === 1) { status = 409; body = { message: 'Der Monatsstand wurde geändert. Bitte erneut prüfen.' }; }
   else { await route.fulfill({ status: 200, contentType: 'application/octet-stream', headers: { 'Content-Disposition': 'attachment; filename="lodas-2026-08.txt"' }, body: '3;01/08/2026;120,50;01;200;14;\r\n' }); return; }
  }
  await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
 });
 await page.goto('/monatsabschluss'); await page.getByRole('checkbox', { name: 'Max Mustermann auswählen', exact: true }).check();
 await page.getByRole('button', { name: 'DATEV einrichten', exact: true }).click();
 const adviser = page.getByRole('textbox', { name: 'Beraternummer', exact: true }); await expect(adviser).toBeEmpty(); await adviser.fill('001234'); await adviser.blur(); await adviser.focus(); await expect(adviser).toHaveValue('001234');
 await page.getByRole('textbox', { name: 'Mandantennummer', exact: true }).fill('0012');
 await page.getByRole('textbox', { name: 'Personalnummer für Max Mustermann', exact: true }).fill('00014');
 const categories = ['Arbeit', 'Feiertage', 'Urlaub', 'Krankheit', 'Fortbildung', 'Zeitausgleich', 'Krankengeld', 'Wiedereingliederung'];
 for (const label of categories) {
  await page.getByRole('combobox', { name: `Export für ${label}`, exact: true }).click();
  if (label === 'Arbeit') { await page.screenshot({ path: info.outputPath('lohnart-auswahl.png') }); await page.getByRole('option', { name: 'Lohnart zuordnen', exact: true }).click(); await page.getByRole('textbox', { name: 'Lohnart für Arbeit', exact: true }).fill('0200'); }
  else await page.getByRole('option', { name: 'Nicht exportieren', exact: true }).click();
 }
 await adviser.scrollIntoViewIfNeeded(); await page.screenshot({ path: info.outputPath('datev-einstellungen-oben.png') });
 await page.getByRole('button', { name: 'Einstellungen speichern', exact: true }).click(); await expect.poll(() => config.version).toBe(1);
 expect(config.beraterNr).toBe('001234'); expect(config.personalnummern).toEqual([{ mitarbeiterId: 1, personalnummer: '00014' }]); expect(config.zuordnungen).toHaveLength(8);
 await page.screenshot({ path: info.outputPath('datev-einstellungen.png'), fullPage: true });
 await page.getByRole('button', { name: 'DATEV einrichten', exact: true }).click(); await page.getByRole('button', { name: 'Für DATEV exportieren', exact: true }).click();
 const dialog = page.getByRole('dialog'); await expect(dialog).toContainText('Max Mustermann'); await expect(dialog).not.toContainText('Erika Mustermann');
 await dialog.getByRole('button', { name: 'Vorprüfung starten', exact: true }).click(); await expect(dialog.getByText('1,50 h', { exact: true })).toBeVisible();
 expect(geprueft!.auswahl).toHaveLength(1); expect(geprueft!.auswahl[0].mitarbeiterId).toBe(1);
 const download = dialog.getByRole('button', { name: 'Datei herunterladen', exact: true }); await expect(download).toBeDisabled(); await dialog.getByRole('checkbox').check();
 await designPruefung(page, info, 'datev-vorpruefung', { primaerAktion: download });
 await download.click(); await expect(dialog.getByRole('alert')).toContainText('Bitte erneut prüfen'); await expect(download).toBeDisabled(); expect(vorpruefungen).toBe(1);
 await dialog.getByRole('button', { name: 'Vorprüfung starten', exact: true }).click(); await expect(dialog.getByText('Die Vorprüfung ist erfolgreich.', { exact: true })).toBeVisible(); await expect(dialog.getByRole('checkbox')).not.toBeChecked(); await dialog.getByRole('checkbox').check();
 const received = page.waitForEvent('download'); await download.click(); const file = await received; expect(file.suggestedFilename()).toBe('lodas-2026-08.txt'); await file.saveAs(info.outputPath(file.suggestedFilename())); expect(await file.failure()).toBeNull(); expect(await readFile(info.outputPath(file.suggestedFilename()), 'utf8')).toBe('3;01/08/2026;120,50;01;200;14;\r\n');
 await expect(page.getByText('Datei heruntergeladen – bitte im Steuerbüro importieren.', { exact: true })).toBeVisible();
});
