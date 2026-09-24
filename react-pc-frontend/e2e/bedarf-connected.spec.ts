import { test, expect } from '@playwright/test';
import { mkdir } from 'node:fs/promises';
import type { BedarfResponse } from '../src/features/einkauf/types';

// All APIs are intercepted; this test never writes to the populated database.
test('Bedarf laden, Werkstattprüfung speichern und nach Neuladen Bestellung vorbereiten', async ({ page, context, baseURL }, testInfo) => {
    await context.route('**/*', route => {
        const url = new URL(route.request().url());
        return url.origin === baseURL || url.protocol === 'data:' || url.protocol === 'blob:' ? route.continue() : route.abort();
    });
    let version = 3;
    let vorhanden = 1.5;
    let writes = 0;
    const bedarf = () => ({ id: 701, version,
        position: { art: 'FREITEXT', artikelId: null, interneReferenz: null, zeichnungsnummer: null, zeichnungsrevision: null,
            bezeichnung: 'Testmaterial Kommamenge', werkstoff: 'Stahl', abmessung: '20 × 20',
            basis: { menge: 12.5, einheit: 'METER', stueckzahl: null, einzelLaengeMm: null, kgJeMeter: 2, faktorQuelle: 'Test' },
            schnittForm: null, winkelLinks: null, winkelRechts: null, bearbeitung: null, oberflaeche: null, dokumente: [], anlageVersionIds: [] },
        liefergruppe: { projektId: 7, lagerzweck: null, lieferadresse: null, bedarfstermin: null },
        mengen: { bedarf: 12.5, lagergedeckt: vorhanden, angefragt: 0, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: 12.5 - vorhanden, disponierbar: 12.5 - vorhanden },
        nachpflegeErforderlich: false, historischerHinweis: null,
    });
    await page.route('**/api/**', async route => {
        const req = route.request(); const url = new URL(req.url());
        const reply = (body: unknown) => route.fulfill({ json: body });
        if (url.pathname === '/api/auth/me') return reply({ id: 1, username: 'test', displayName: 'Max Mustermann', active: true, admin: true, roles: ['ADMIN'], requiresInitialSetup: false });
        if (url.pathname === '/api/projekte/7') return reply({ id: 7, bauvorhaben: 'Testprojekt Backend', kunde: 'Max Mustermann' });
        if (url.pathname === '/api/projekte/simple') return reply([{ id: 7, bauvorhaben: 'Testprojekt Backend', kunde: 'Max Mustermann' }]);
        if (url.pathname === '/api/einkauf/bedarf') return reply({ content: [bedarf()], totalPages: 1 });
        if (url.pathname === '/api/einkauf/bedarf/werkstattpruefung') {
            const data = req.postDataJSON(); writes++;
            expect(data.positionen).toEqual([{ bedarfId: 701, version: 3, vorhanden: 2.5 }]);
            vorhanden = data.positionen[0].vorhanden; version++;
            return reply([bedarf()]);
        }
        if (url.pathname === '/api/einkauf/berechtigungen') return reply(['LESEN', 'BEARBEITEN', 'FREIGEBEN']);
        if (url.pathname === '/api/features') return reply({ en1090: true, echeck: true, email: false, rag: false });
        if (url.pathname === '/api/notifications/summary') return reply({ totalCount: 0, categories: [], recentItems: [] });
        return reply({});
    });
    await page.goto('/bestellungen/bedarf');
    await expect(page.getByText('Testprojekt Backend', { exact: true })).toBeVisible();
    await page.getByText('Testprojekt Backend', { exact: true }).click();
    await expect(page.getByText('Testmaterial Kommamenge', { exact: true })).toBeVisible();
    const field = page.getByRole('textbox', { name: 'Vorhandene Menge', exact: true });
    await expect(field).toHaveValue('1,5');
    await field.fill('2,5'); await field.press('Tab');
    await page.getByRole('button', { name: 'Werkstattprüfung speichern' }).click();
    await expect(page.getByText('Werkstattprüfung gespeichert.', { exact: true })).toBeVisible();
    expect(writes).toBe(1);
    await page.reload();
    await expect(field).toHaveValue('2,5');
    await expect(page.getByRole('textbox', { name: 'Bestellmenge', exact: true })).toHaveValue('10');
    await mkdir('/tmp/bedarf-connected-screenshots', { recursive: true });
    await page.screenshot({ path: `/tmp/bedarf-connected-screenshots/werkstatt-${testInfo.project.name}.png`, fullPage: true });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.getByRole('button', { name: 'Bestellung vorbereiten', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Direktbestellung vorbereiten' })).toBeVisible();
    await page.screenshot({ path: `/tmp/bedarf-connected-screenshots/bestellung-${testInfo.project.name}.png`, fullPage: true });
});

test('Materialbedarf wird über die echte Schnittstelle gespeichert und erneut geladen', async ({ page, context, baseURL }, testInfo) => {
    await context.route('**/*', route => {
        const url = new URL(route.request().url());
        return url.origin === baseURL || ['data:', 'blob:'].includes(url.protocol) ? route.continue() : route.abort();
    });
    const project = { id: 7, bauvorhaben: 'Testprojekt Backend', kunde: 'Max Mustermann' };
    let stored: BedarfResponse | null = null;
    let edits = 0;
    let writes = 0;
    await page.route('**/api/**', async route => {
        const request = route.request(); const url = new URL(request.url());
        const reply = (json: unknown) => route.fulfill({ json });
        if (url.pathname === '/api/auth/me') return reply({ id: 1, username: 'test', displayName: 'Max Mustermann', active: true, admin: true, roles: ['ADMIN'], requiresInitialSetup: false });
        if (url.pathname === '/api/projekte/7') return reply(project);
        if (url.pathname === '/api/projekte/simple') return reply([project]);
        if (url.pathname === '/api/einkauf/bedarf' && request.method() === 'POST') {
            const data = request.postDataJSON();
            expect(data.position.bezeichnung).toBe('Prüfmaterial aus Datenbank');
            expect(data.position.basis.menge).toBe(4);
            expect(data.position.basis.einheit).toBe('STUECK');
            expect(data.liefergruppe.projektId).toBe(7);
            writes++;
            stored = { ...data, id: 703, version: 0, nachpflegeErforderlich: false, historischerHinweis: null,
                mengen: { bedarf: 4, lagergedeckt: 0, angefragt: 0, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: 4, disponierbar: 4 } };
            return route.fulfill({ status: 201, json: stored });
        }
        if (url.pathname === '/api/einkauf/bedarf/703' && request.method() === 'PUT') {
            const data = request.postDataJSON();
            expect(data.version).toBe(0);
            expect(data.position.beschaffungsdetails).toEqual({ lieferantId: 42, kategorieId: 64, schnittbildId: 8, schnittAchseId: 7, externeArtikelnummer: '0017' });
            expect(data.position.winkelLinks).toBe('45°');
            expect(data.position.winkelRechts).toBe('90°');
            expect(data.position.anlageVersionIds).toEqual([41]);
            expect(data.position.bearbeitung).toBe('Kommentar nach Bearbeitung');
            expect(data.liefergruppe.projektId).toBe(7);
            stored = { ...stored!, ...data, version: 1 };
            edits++;
            return reply(stored);
        }
        if (url.pathname === '/api/lieferanten/42') return reply({ id: 42, lieferantenname: 'Musterlieferant' });
        if (url.pathname === '/api/artikel/kategorien/alle') return reply([{ id: 64, bezeichnung: 'Testkategorie', parentId: null }]);
        if (url.pathname === '/api/einkauf/bedarf') return reply({ content: stored ? [stored] : [], totalPages: 1 });
        if (url.pathname === '/api/einkauf/berechtigungen') return reply(['LESEN', 'BEARBEITEN', 'FREIGEBEN']);
        if (url.pathname === '/api/features') return reply({ en1090: true, email: false });
        if (url.pathname === '/api/notifications/summary') return reply({ totalCount: 0, categories: [], recentItems: [] });
        expect(url.pathname).not.toMatch(/^\/api\/bestellungen\//);
        return reply({});
    });
    await page.goto('/bestellungen/bedarf/projekt/7');
    await page.getByRole('button', { name: 'Material hinzufügen' }).click();
    const dialog = page.getByRole('dialog', { name: 'Materialbestellung' });
    await dialog.getByPlaceholder('z. B. IPE 200, S235').fill('Prüfmaterial aus Datenbank');
    await dialog.getByPlaceholder('1', { exact: true }).fill('4');
    await mkdir('/tmp/bedarf-connected-screenshots', { recursive: true });
    await page.screenshot({ path: `/tmp/bedarf-connected-screenshots/material-${testInfo.project.name}.png`, fullPage: true });
    await dialog.getByRole('button', { name: 'Alle speichern' }).click();
    await expect(dialog).toHaveCount(0);
    await expect(page.getByText('Prüfmaterial aus Datenbank', { exact: true })).toBeVisible();
    expect(writes).toBe(1);
    // Simulate an existing technical snapshot returned by the database on a fresh load.
    const existing = stored as unknown as BedarfResponse;
    existing.position.beschaffungsdetails = { lieferantId: 42, kategorieId: 64, schnittbildId: 8, schnittAchseId: 7, externeArtikelnummer: '0017' };
    existing.position.basis!.einzelLaengeMm = 1000;
    existing.position.winkelLinks = '45°'; existing.position.winkelRechts = '90°';
    existing.position.anlageVersionIds = [41];
    await page.reload();
    await expect(page.getByText('Prüfmaterial aus Datenbank', { exact: true })).toBeVisible();
    await page.getByTitle('Bearbeiten', { exact: true }).click();
    const edit = page.getByRole('dialog', { name: 'Bestellposition bearbeiten' });
    await edit.getByPlaceholder('z. B. Lieferung KW 22').fill('Kommentar nach Bearbeitung');
    await page.screenshot({ path: `/tmp/bedarf-connected-screenshots/bearbeiten-${testInfo.project.name}.png`, fullPage: true });
    await edit.getByRole('button', { name: 'Änderungen speichern' }).click();
    await expect(edit).toHaveCount(0);
    expect(edits).toBe(1); expect(writes).toBe(1);
    await page.reload();
    await page.getByTitle('Bearbeiten', { exact: true }).click();
    await expect(page.getByPlaceholder('z. B. Lieferung KW 22')).toHaveValue('Kommentar nach Bearbeitung');
});

test('Freien Bedarf löschen, weiterverarbeiteten Bedarf gesperrt lassen und Konflikt melden', async ({ page, context, baseURL }, testInfo) => {
    await context.route('**/*', route => {
        const url = new URL(route.request().url());
        return url.origin === baseURL || ['data:', 'blob:'].includes(url.protocol) ? route.continue() : route.abort();
    });
    const project = { id: 7, bauvorhaben: 'Testprojekt Backend', kunde: 'Max Mustermann' };
    const bedarf = (id: number, bezeichnung: string, angefragt = 0) => ({ id, version: 2,
        position: { art: 'FREITEXT', artikelId: null, interneReferenz: null, zeichnungsnummer: null, zeichnungsrevision: null,
            bezeichnung, werkstoff: 'S235JR', abmessung: null,
            basis: { menge: 4, einheit: 'STUECK', stueckzahl: 4, einzelLaengeMm: null, kgJeMeter: null, faktorQuelle: null },
            schnittForm: null, winkelLinks: null, winkelRechts: null, bearbeitung: null, oberflaeche: null, dokumente: [], anlageVersionIds: [] },
        liefergruppe: { projektId: 7, lagerzweck: null, lieferadresse: null, bedarfstermin: null },
        mengen: { bedarf: 4, lagergedeckt: 0, angefragt, reserviert: 0, bestellt: 0, geliefert: 0, storniert: 0, ungedeckt: 4, disponierbar: 4 },
        nachpflegeErforderlich: false, historischerHinweis: null,
    });
    let bedarfe = [bedarf(801, 'Flachstahl zum Löschen'), bedarf(802, 'Winkel mit Konflikt'), bedarf(803, 'Rohr in Preisanfrage', 4)];
    const deletes: string[] = [];
    await page.route('**/api/**', async route => {
        const request = route.request(); const url = new URL(request.url());
        const reply = (json: unknown) => route.fulfill({ json });
        if (url.pathname === '/api/auth/me') return reply({ id: 1, username: 'test', displayName: 'Max Mustermann', active: true, admin: true, roles: ['ADMIN'], requiresInitialSetup: false });
        if (url.pathname === '/api/projekte/7') return reply(project);
        if (url.pathname === '/api/projekte/simple') return reply([project]);
        if (request.method() === 'DELETE' && url.pathname.startsWith('/api/einkauf/bedarf/')) {
            deletes.push(`${url.pathname}?${url.searchParams}`);
            if (url.pathname.endsWith('/802')) return route.fulfill({ status: 409, json: {
                message: 'Der Bedarf ist bereits in Preisanfrage PA-2026-0001 enthalten und kann nicht gelöscht werden.', fieldErrors: [] } });
            bedarfe = bedarfe.filter(b => `/api/einkauf/bedarf/${b.id}` !== url.pathname);
            return route.fulfill({ status: 204 });
        }
        if (url.pathname === '/api/einkauf/bedarf') return reply({ content: bedarfe, totalPages: 1 });
        if (url.pathname === '/api/einkauf/berechtigungen') return reply(['LESEN', 'BEARBEITEN', 'FREIGEBEN']);
        if (url.pathname === '/api/features') return reply({ en1090: true, email: false });
        if (url.pathname === '/api/notifications/summary') return reply({ totalCount: 0, categories: [], recentItems: [] });
        expect(url.pathname).not.toMatch(/^\/api\/bestellungen\//);
        return reply({});
    });
    await page.goto('/bestellungen/bedarf/projekt/7');
    await expect(page.getByText('Flachstahl zum Löschen', { exact: true })).toBeVisible();

    const gesperrt = page.getByRole('button', { name: /Löschen nicht möglich: Steht in einer Preisanfrage/ });
    await expect(gesperrt).toBeDisabled();
    await expect(page.getByTitle('Steht in einer Preisanfrage – nicht mehr löschbar')).toBeVisible();

    await page.getByRole('button', { name: 'Flachstahl zum Löschen löschen' }).click();
    await expect(page.getByText('Bedarf wirklich löschen?')).toBeVisible();
    await mkdir('/tmp/bedarf-connected-screenshots', { recursive: true });
    await page.screenshot({ path: `/tmp/bedarf-connected-screenshots/loeschen-bestaetigen-${testInfo.project.name}.png`, fullPage: true });
    await page.getByRole('button', { name: 'Löschen', exact: true }).click();
    await expect(page.getByText('Bedarf gelöscht.', { exact: true })).toBeVisible();
    await expect(page.getByText('Flachstahl zum Löschen', { exact: true })).toHaveCount(0);

    await page.getByRole('button', { name: 'Winkel mit Konflikt löschen' }).click();
    await page.getByRole('button', { name: 'Löschen', exact: true }).click();
    await expect(page.getByText('Der Bedarf ist bereits in Preisanfrage PA-2026-0001 enthalten und kann nicht gelöscht werden.')).toBeVisible();
    await expect(page.getByText('Winkel mit Konflikt', { exact: true })).toBeVisible();
    await page.screenshot({ path: `/tmp/bedarf-connected-screenshots/loeschen-konflikt-${testInfo.project.name}.png`, fullPage: true });
    expect(deletes).toEqual(['/api/einkauf/bedarf/801?version=2', '/api/einkauf/bedarf/802?version=2']);
});
