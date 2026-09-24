import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

for (const sucheMitWiederholung of [false, true]) {
  test(`Material ohne Warengruppe: Kategorie über ${sucheMitWiederholung ? 'Suche nach Ladefehler' : 'Baum'} auswählen und Artikel übernehmen`, async ({ page }, testInfo) => {
    const screenshot = async (name: string) => {
      const path = testInfo.outputPath(`${name}.png`);
      await page.screenshot({ path });
      await testInfo.attach(name, { path, contentType: 'image/png' });
    };
    let kategorienAufrufe = 0;
    const artikelFilter: Array<string | null> = [];
    const falscheKategorieRouten: string[] = [];
    const saves: unknown[] = [];
    const kategorien = [
      { id: 10, bezeichnung: 'Metall', parentId: null, leaf: false },
      { id: 11, bezeichnung: 'Profile', parentId: 10, leaf: false },
      { id: 12, bezeichnung: 'Winkel', parentId: 11, leaf: true },
    ];
    const artikel = { id: 41, produktname: 'Winkel 40 × 40', externeArtikelnummer: 'M-0041', werkstoffName: 'S235JR', abmessung: '40 × 40 × 4 mm', kategorieId: 12, kategoriePfad: 'Metall › Profile › Winkel', kgProMeter: 2.42, verrechnungseinheit: 'LAUFENDE_METER', preis: null };
    await page.route('**/api/**', async route => {
      const url = new URL(route.request().url());
      if (url.pathname === '/api/auth/me') return route.fulfill({ json: { id: 9, username: 'max.mustermann', displayName: 'Max Mustermann', admin: true, roles: ['ADMIN'], requiresInitialSetup: false } });
      if (url.pathname === '/api/notifications/summary') return route.fulfill({ json: { totalCount: 0, categories: [], recentItems: [] } });
      if (url.pathname === '/api/einkauf/berechtigungen') return route.fulfill({ json: ['LESEN', 'BEARBEITEN'] });
      if (url.pathname === '/api/einkauf/bedarf') {
        if (route.request().method() === 'POST') { saves.push(route.request().postDataJSON()); return route.fulfill({ status: 201, json: { id: 81 } }); }
        return route.fulfill({ json: { content: [], totalElements: 0, totalPages: 0 } });
      }
      if (url.pathname === '/api/artikel/kategorien/alle') {
        kategorienAufrufe++;
        if (sucheMitWiederholung && kategorienAufrufe === 1) return route.fulfill({ status: 503, json: { message: 'Kategorien vorübergehend nicht verfügbar' } });
        return route.fulfill({ json: kategorien });
      }
      if (url.pathname.includes('kategorien')) {
        falscheKategorieRouten.push(url.pathname);
        return route.fulfill({ status: 404, json: { message: 'Kein gültiger Kategorien-Endpunkt' } });
      }
      if (url.pathname === '/api/artikel/werkstoffe') return route.fulfill({ json: ['S235JR'] });
      if (url.pathname === '/api/artikel') {
        artikelFilter.push(url.searchParams.get('kategorieId'));
        return route.fulfill({ json: { artikel: url.searchParams.get('kategorieId') === '12' ? [artikel] : [], gesamt: url.searchParams.get('kategorieId') === '12' ? 1 : 0 } });
      }
      return route.fulfill({ json: [] });
    });
    await page.goto('/bestellungen/bedarf');
    await page.getByRole('button', { name: 'Für Werkstatt / auf Vorrat', exact: true }).click();
    await page.getByRole('button', { name: 'Material hinzufügen' }).click();
    const material = page.getByRole('dialog', { name: 'Materialbedarf erfassen', exact: true });
    await expect(material.getByText('Warengruppe', { exact: true })).toHaveCount(0);
    expect(kategorienAufrufe).toBe(0);
    await material.getByRole('button', { name: 'Artikel suchen...', exact: true }).click();
    await page.getByRole('dialog', { name: 'Artikel auswählen', exact: true }).getByRole('button', { name: 'Alle Kategorien', exact: true }).click();
    const kategorie = page.getByRole('dialog', { name: 'Kategorie auswählen', exact: true });
    await expect(kategorie.getByRole('button', { name: /^(Fenster schließen|Schließen)$/ })).toHaveCount(1);
    if (sucheMitWiederholung) {
      await expect(kategorie.getByRole('alert')).toContainText('Kategorien konnten nicht geladen werden.');
      await expect(kategorie.getByText('Keine Kategorien verfügbar', { exact: true })).toHaveCount(0);
      await screenshot('Kategorien-Ladefehler');
      await kategorie.getByRole('button', { name: 'Erneut laden', exact: true }).click();
    }
    await expect(kategorie.getByRole('button', { name: 'Metall', exact: true })).toBeVisible();
    if (sucheMitWiederholung) {
      await kategorie.getByPlaceholder('Kategorie suchen...').fill('Winkel');
      await expect(kategorie.getByText('Metall › Profile › Winkel', { exact: true })).toBeVisible();
      await screenshot('Kategorien-Suche');
      await kategorie.getByRole('button', { name: 'Winkel Metall › Profile › Winkel', exact: true }).click();
    } else {
      await expect(kategorie.getByRole('button', { name: 'Winkel', exact: true })).toHaveCount(0);
      await kategorie.getByRole('button', { name: 'Ausklappen', exact: true }).click();
      await expect(kategorie.getByRole('button', { name: 'Profile', exact: true })).toBeVisible();
      await kategorie.getByRole('button', { name: 'Ausklappen', exact: true }).click();
      await expect(kategorie.getByRole('button', { name: 'Winkel', exact: true })).toBeVisible();
      await screenshot('Kategorien-Baum');
      await kategorie.getByRole('button', { name: 'Winkel', exact: true }).click();
    }
    const artikelsuche = page.getByRole('dialog', { name: 'Artikel auswählen', exact: true });
    await expect(artikelsuche.getByRole('button', { name: 'Metall › Profile › Winkel', exact: true })).toBeVisible();
    await artikelsuche.getByRole('row').filter({ hasText: 'Winkel 40 × 40' }).click();
    await expect(material.getByLabel('Produktname Position 1')).toHaveValue('Winkel 40 × 40');
    await expect(material.getByRole('combobox', { name: 'Einheit Position 1' })).toContainText('m (Meter)');
    await expect(material.getByText('Warengruppe', { exact: true })).toHaveCount(0);
    expect(artikelFilter).toContain('12');
    expect(kategorienAufrufe).toBe(sucheMitWiederholung ? 2 : 1);
    expect(falscheKategorieRouten).toEqual([]);
    await designPruefung(page, testInfo, `beschaffung-kategorie-${sucheMitWiederholung ? 'suche-retry' : 'baum'}-uebernommen`, { primaerAktion: material.getByRole('button', { name: 'Bedarf speichern', exact: true }) });
    await material.getByRole('button', { name: 'Bedarf speichern', exact: true }).click();
    await expect(material).toHaveCount(0);
    expect(saves).toHaveLength(1);
    expect(saves[0]).toMatchObject({ position: { art: 'ARTIKEL', artikelId: 41, bezeichnung: 'Winkel 40 × 40', basis: { menge: 1, einheit: 'METER', kgJeMeter: 2.42 } }, liefergruppe: { projektId: null } });
  });
}
