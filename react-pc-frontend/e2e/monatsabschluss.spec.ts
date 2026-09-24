import { test, expect } from './hilfen/test';
import { blockiereFremdeNetzwerkzugriffe } from './hilfen/api';
import { designPruefung } from './hilfen/design';
test('Monatsabschluss: Filter, vollständige Auswahl, Teilerfolg und Verlauf', async ({ page }, info) => {
 await blockiereFremdeNetzwerkzugriffe(page);
 const zahlen = { istStunden: 120.5, sollStunden: 160, abwesenheitsStunden: 16, feiertagsStunden: 8, korrekturStunden: 1, gesamtIst: 145.5, differenz: -14.5, urlaubStunden: 10, krankheitStunden: 4, zeitausgleichStunden: 0, sonstigeAbwesenheitStunden: 2 };
 const summen = { istStunden: 241, sollStunden: 320, abwesenheitsStunden: 32, feiertagsStunden: 16, korrekturStunden: 2, gesamtIst: 291, differenz: -29, urlaubStunden: 20, krankheitStunden: 8, zeitausgleichStunden: 0, sonstigeAbwesenheitStunden: 4 };
 let abgeschlossen = false; let posts = 0; let historie = 0;
 await page.route('**/api/**', async route => {
  const url = new URL(route.request().url()); const path = url.pathname; const jahr = Number(url.searchParams.get('jahr')); const monat = Number(url.searchParams.get('monat')); let body: unknown = [];
  if (path === '/api/auth/me') body = { id: 1, username: 'test', email: 'test@example.com', vorname: 'Max', nachname: 'Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false };
  if (path.endsWith('/berechtigung')) body = { darfMonatAbschliessen: true };
  if (path === '/api/mitarbeiter') body = [{ id: 1, vorname: 'Max', nachname: 'Mustermann' }, { id: 2, vorname: 'Erika', nachname: 'Mustermann' }];
  if (path === '/api/abteilungen') body = [{ id: 2, name: 'Werkstatt' }];
  if (path.endsWith('/uebersicht')) body = { items: [1, 2].map(id => ({ referenz: { mitarbeiterId: id, jahr, monat }, mitarbeiterName: id === 1 ? 'Max Mustermann' : 'Erika Mustermann', abteilungIds: [2], festgeschrieben: abgeschlossen && id === 2, version: 3, festgeschriebenAm: abgeschlossen && id === 2 ? '2026-09-01T10:00:00' : null, kennzahlen: zahlen })), totalElements: 2, page: 0, size: 50, summen, auswahl: [1, 2].map(mitarbeiterId => ({ mitarbeiterId, jahr, monat, version: 3, festgeschrieben: abgeschlossen && mitarbeiterId === 2 })) };
  if (path.endsWith('/vergleich')) { expect(url.searchParams.has('status')).toBe(false); body = Array.from({ length: 6 }, (_, i) => ({ jahr: 2026, monat: i + 1, summen, offen: 1, abgeschlossen: 1 })); }
  if (path.endsWith('/sammelabschluss')) { posts++; const refs = route.request().postDataJSON().auswahl; expect(refs).toHaveLength(2); abgeschlossen = true; body = { ergebnisse: refs.map((referenz: { mitarbeiterId: number }) => ({ referenz, status: referenz.mitarbeiterId === 1 ? 'FEHLGESCHLAGEN' : 'ABGESCHLOSSEN', meldung: referenz.mitarbeiterId === 1 ? 'Zeiten bitte prüfen.' : 'Monat abgeschlossen.' })) }; }
  if (/monatsabschluesse\/\d+\/\d+\/\d+$/.test(path)) { historie++; body = { audit: [{ id: 1, aktion: 'ABSCHLIESSEN', akteurMitarbeiterId: 1, akteurName: 'Max Mustermann', zeitpunkt: '2026-08-01T10:00:00' }] }; }
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
 });
 await page.goto('/monatsabschluss'); await expect(page.getByRole('heading', { name: 'MONATSABSCHLUSS', exact: true })).toBeVisible();
 await expect(page.getByRole('checkbox', { name: 'Max Mustermann auswählen', exact: true })).toBeVisible();
 await expect(page.getByRole('row').filter({ hasText: 'Alle gefilterten Mitarbeiter' }).getByRole('cell')).toHaveText(['241,00', '20,00', '8,00', '0,00', '4,00', '16,00', '2,00', '291,00', '320,00', '-29,00', '']);
 // Spaltenköpfe dürfen nur an Worttrennstellen umbrechen, nie mitten im Wort (z. B. "Zeitausgl|eich" auf 14 Zoll)
 const zerbrocheneKoepfe = await page.locator('thead th').evaluateAll(koepfe => koepfe.flatMap(th => {
  const text = th.firstChild?.nodeType === Node.TEXT_NODE ? th.firstChild : null; if (!text?.textContent) return [];
  const teile: string[] = []; let start = 0;
  // Zeile eines einzelnen Buchstabens; das letzte Rechteck, weil Chromium den Trennstrich der Vorzeile mitzählt
  const zeile = (i: number) => { const r = document.createRange(); r.setStart(text, i); r.setEnd(text, i + 1); return Array.from(r.getClientRects()).at(-1)?.top; };
  for (const stueck of text.textContent.split(/[\s\u00AD]/)) { if (stueck && zeile(start) !== zeile(start + stueck.length - 1)) teile.push(stueck); start += stueck.length + 1; }
  return teile;
 }));
 expect(zerbrocheneKoepfe).toEqual([]);
 await page.getByRole('combobox', { name: 'Abteilung', exact: true }).click(); await expect(page.getByRole('listbox')).toBeVisible();
 await page.screenshot({ path: info.outputPath('abteilung-offen.png') }); await page.getByRole('option', { name: 'Werkstatt' }).click();
 await page.getByRole('checkbox', { name: 'Alle gefilterten Mitarbeiter auswählen' }).check();
 const primary = page.getByRole('button', { name: 'Monat jetzt abschließen', exact: true });
 await designPruefung(page, info, 'monatsabschluss-uebersicht', { primaerAktion: primary });
 await primary.click(); await expect(page.getByRole('dialog')).toContainText('2 Mitarbeiter'); await page.screenshot({ path: info.outputPath('bestaetigung.png') }); await page.getByRole('button', { name: 'Abschließen', exact: true }).click();
 await expect(page.getByText('1 ausgewählt', { exact: true })).toBeVisible(); expect(posts).toBe(1); expect(historie).toBe(0);
 await page.getByRole('button', { name: 'Verlauf für Max Mustermann', exact: true }).click(); await expect(page.getByText(/Abgeschlossen durch Max Mustermann/)).toBeVisible(); expect(historie).toBe(1);
 const region = page.getByRole('region', { name: 'Abschlussverlauf' }); await expect(region).toBeInViewport();
 await expect(page.getByRole('link', { name: 'Kalender für Max Mustermann', exact: true })).toHaveAttribute('href', /^\/zeitbuchungen\?mitarbeiterId=1&jahr=\d+&monat=\d+$/);
 await page.screenshot({ path: info.outputPath('teilerfolg-verlauf.png'), fullPage: true });
});
test('Monatsabschluss prüft zuerst das Recht und erklärt fehlenden Zugriff', async ({ page }, info) => {
 await blockiereFremdeNetzwerkzugriffe(page); const datenabrufe: string[] = []; let freigeben!: () => void;
 const warten = new Promise<void>(resolve => { freigeben = resolve; });
 await page.route('**/api/**', async route => {
  const path = new URL(route.request().url()).pathname;
  let body: unknown = [];
  if (path === '/api/auth/me') body = { id: 1, username: 'test', email: 'test@example.com', vorname: 'Max', nachname: 'Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false };
  if (path.endsWith('/berechtigung')) { await warten; body = { darfMonatAbschliessen: false }; }
  if (/\/(uebersicht|vergleich|mitarbeiter|abteilungen)$/.test(path)) datenabrufe.push(path);
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
 });
 await page.goto('/monatsabschluss'); await expect(page.getByRole('status').filter({ hasText: 'Abschlussrecht wird geprüft' })).toBeVisible(); await page.screenshot({ path: info.outputPath('rechte-laden.png') });
 freigeben(); await expect(page.getByRole('alert').filter({ hasText: 'Keine Berechtigung' })).toBeVisible(); expect(datenabrufe).toEqual([]); await designPruefung(page, info, 'monatsabschluss-kein-recht');
});
