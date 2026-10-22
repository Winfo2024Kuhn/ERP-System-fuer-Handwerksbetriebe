import type { Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Artikel anlegen mit Reitern („Das Wichtigste“ bis „Mehr Angaben“), dabei
 * Bild und Datenblatt mitgeben, danach auf der neuen Artikelseite den Preis
 * direkt in der Zeile ändern. /api ist gestubbt, nur Dummy-Daten (DSGVO).
 */

/** Ein 1x1-PNG - reicht als Foto und als ausgeliefertes Vorschaubild. */
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

interface Mitschrift {
    angelegt: Record<string, unknown>[];
    uploads: string[];
    preise: unknown[];
}

function neuerArtikel(preis: number, mitBild: boolean) {
    return {
        artikel: {
            id: 77, artikelnummer: 'A-004143', produktname: 'Flachstahl 20 x 10', werkstoffId: 4, werkstoffName: 'Baustahl S235JR',
            verrechnungseinheit: { name: 'LAUFENDE_METER', anzeigename: 'Laufende Meter' },
            vorschaubildUrl: mitBild ? '/api/artikel/dokumente/901/datei' : null,
            preisHinweis: 'OK', positionsEinheit: 'm', positionsEinzelpreis: preis * 1.2,
        },
        lieferanten: [{
            preisId: preis === 7.28 ? 1 : 2, lieferantId: 5, lieferantName: 'Muster Stahlhandel', externeArtikelnummer: 'MS-2010',
            preis, preisDatum: '2026-10-22T00:00:00Z', quelle: { name: 'MANUELL', anzeigename: 'Von Hand eingetragen' }, guenstigster: true,
        }],
        preisverlauf: [{ preisId: 1, lieferantId: 5, lieferantName: 'Muster Stahlhandel', preis: 7.28, preisDatum: '2026-10-22T00:00:00Z', aktuell: preis === 7.28 }],
    };
}

test('Artikel mit Bild anlegen, Detailseite öffnet sich, Preis in der Zeile ändern', async ({ page }, testInfo) => {
    const mit: Mitschrift = { angelegt: [], uploads: [], preise: [] };
    let preis = 7.28;
    await page.route('**/api/**', (route) => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        const methode = request.method();
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'max', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === '/api/artikel' && methode === 'GET') return json(route, { artikel: [], gesamt: 0 });
        if (pfad === '/api/artikel/filteroptionen') return json(route, { herstellverfahren: [], fertigungszustand: [], profilform: [] });
        if (pfad === '/api/artikel/werkstoffe/details') return json(route, [{ id: 4, name: 'Baustahl S235JR' }]);
        if (pfad === '/api/artikel/naechste-artikelnummer') return json(route, { artikelnummer: 'A-004143' });
        if (pfad === '/api/lieferanten') return json(route, { lieferanten: [{ id: 5, lieferantenname: 'Muster Stahlhandel' }] });
        if (pfad === '/api/artikel' && methode === 'POST') {
            mit.angelegt.push(request.postDataJSON() as Record<string, unknown>);
            return json(route, { id: 77, artikelnummer: 'A-004143', produktname: 'Flachstahl 20 x 10' });
        }
        if (pfad === '/api/artikel/77/dokumente' && methode === 'POST') {
            // Multipart: die Art steht als eigenes Feld "typ" im Rumpf
            const typ = /name="typ"\r\n\r\n([A-Z_]+)/.exec(request.postDataBuffer()?.toString('latin1') ?? '')?.[1] ?? '?';
            mit.uploads.push(typ);
            return json(route, { id: 900 + mit.uploads.length, typ });
        }
        if (pfad === '/api/artikel/dokumente/901/datei') return route.fulfill({ status: 200, contentType: 'image/png', body: PNG });
        if (pfad === '/api/artikel/77/detail') return json(route, neuerArtikel(preis, mit.uploads.includes('VORSCHAUBILD')));
        if (pfad === '/api/lieferanten/5/artikelpreise' && methode === 'POST') {
            const body = request.postDataJSON() as { preis: number };
            mit.preise.push(body);
            preis = body.preis;
            return json(route, {});
        }
        return json(route, []);
    });

    await page.goto('/artikel');
    await page.getByRole('button', { name: 'Neu', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Neuen Artikel anlegen' });
    await expect(dialog).toBeVisible();

    // ---- Das Wichtigste: Nummer kommt automatisch ----
    await expect(dialog.getByText('A-004143 · wird automatisch vergeben')).toBeVisible();
    await expect(dialog.getByLabel('Produktname *')).toBeFocused();
    await dialog.getByLabel('Produktname *').fill('Flachstahl 20 x 10');
    await dialog.getByRole('combobox', { name: 'Einheit' }).click();
    await page.getByRole('option', { name: 'Laufende Meter' }).click();
    await dialog.getByRole('combobox', { name: 'Werkstoff' }).click();
    await page.getByRole('option', { name: 'Baustahl S235JR' }).click();
    await designPruefung(page, testInfo, 'artikel-anlegen-wichtig', { primaerAktion: dialog.getByRole('button', { name: 'Artikel anlegen' }) });

    await dialog.getByRole('button', { name: 'Mehr zu: Artikel-Nr.' }).click();
    await expect(page.getByText(/kann es eine Nummer weiter sein/)).toBeVisible();
    await designPruefung(page, testInfo, 'artikel-anlegen-info');
    await dialog.getByRole('button', { name: 'Mehr zu: Artikel-Nr.' }).click();

    // ---- Preis & Lieferant (optional) ----
    await dialog.getByRole('button', { name: 'Weiter' }).click();
    await expect(dialog.getByRole('tab', { name: /Preis & Lieferant/ })).toHaveAttribute('aria-selected', 'true');
    await dialog.getByRole('button', { name: 'Lieferant', exact: true }).click();
    await page.getByText('Muster Stahlhandel').click();
    await dialog.getByLabel('Artikelnummer beim Lieferanten').fill('MS-2010');
    await dialog.getByLabel('Preis in Euro').fill('7,28');

    // ---- Bilder & Unterlagen (optional) ----
    await dialog.getByRole('tab', { name: /Bilder & Unterlagen/ }).click();
    await dialog.locator('input[type="file"][aria-label="Bild auswählen"]').setInputFiles({ name: 'flachstahl.png', mimeType: 'image/png', buffer: PNG });
    await dialog.locator('input[type="file"][aria-label="Unterlage auswählen"]').setInputFiles({ name: 'datenblatt.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4') });
    await expect(dialog.getByRole('img', { name: 'Gewähltes Bild: flachstahl.png' })).toBeVisible();
    await expect(dialog.getByRole('list', { name: 'Gewählte Unterlagen' })).toContainText('datenblatt.pdf');
    await expect(dialog.getByRole('tab', { name: /Bilder & Unterlagen/ })).toContainText('2 Dateien gewählt');
    await designPruefung(page, testInfo, 'artikel-anlegen-bilder', { primaerAktion: dialog.getByRole('button', { name: 'Artikel anlegen' }) });

    // ---- Anlegen: Artikel, dann Dateien, dann ab auf die Artikelseite ----
    await dialog.getByRole('button', { name: 'Artikel anlegen' }).click();
    await expect(page).toHaveURL(/\/artikel\/77$/);
    await expect(page.getByText('Artikel A-004143 ist angelegt.')).toBeVisible();
    expect(mit.angelegt).toHaveLength(1);
    expect(mit.angelegt[0]).toMatchObject({
        produktname: 'Flachstahl 20 x 10', artikelnummer: null, verrechnungseinheit: 'LAUFENDE_METER', werkstoffId: 4,
        lieferantId: 5, preis: 7.28, externeArtikelnummer: 'MS-2010',
    });
    expect(mit.uploads).toEqual(['VORSCHAUBILD', 'DATENBLATT']);

    // ---- Übersicht des neuen Artikels ----
    await expect(page.getByRole('tab', { name: /Übersicht/ })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByTestId('bester-preis')).toContainText('7,28');
    await expect(page.getByRole('img', { name: 'Vorschaubild: Flachstahl 20 x 10' })).toBeVisible();
    await designPruefung(page, testInfo, 'artikel-detail-uebersicht');

    // ---- Preis in der Zeile ändern ----
    await page.getByRole('tab', { name: /Preise & Lieferanten/ }).click();
    await expect(page).toHaveURL(/\/artikel\/77#preise$/);
    await page.getByRole('button', { name: /^Preis ändern bei Muster Stahlhandel/ }).click();
    const feld = page.getByLabel('Neuer Preis in Euro bei Muster Stahlhandel');
    await feld.fill('7,50');
    const gespeichert = page.waitForResponse((r) => r.url().endsWith('/api/lieferanten/5/artikelpreise'));
    await feld.press('Enter');
    await gespeichert;
    expect(mit.preise).toEqual([{ artikelId: 77, preis: 7.5, externeArtikelnummer: null }]);
    await expect(page.getByRole('button', { name: /^Preis ändern bei Muster Stahlhandel, bisher 7,50/ })).toBeVisible();
    await designPruefung(page, testInfo, 'artikel-detail-preise', { ganzeSeite: true });
});
