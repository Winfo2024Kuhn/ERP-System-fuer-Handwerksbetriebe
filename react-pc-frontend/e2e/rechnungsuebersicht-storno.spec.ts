import { test, expect } from './hilfen/test';
import { BEISPIEL_DOKUMENT } from './hilfen/dokument-editor';

const rechnungen = [
    { id: 41, dokumentid: 'RE-2026/09/00001', geschaeftsdokumentart: 'Rechnung', rechnungsdatum: '2026-09-10', bruttoBetrag: 119, bezahlt: false, storniert: true, storno: false, projektKunde: 'Max Mustermann', editorUrl: '/dokument-editor?dokumentId=41&dokumentTyp=RECHNUNG' },
    { id: 42, dokumentid: 'ST-2026/09/00001', geschaeftsdokumentart: 'Stornorechnung', rechnungsdatum: '2026-09-13', bruttoBetrag: -119, bezahlt: false, storniert: false, storno: true, projektKunde: 'Max Mustermann', editorUrl: '/dokument-editor?dokumentId=42&dokumentTyp=STORNO' },
];

test('zeigt Stornos, verrechnet die Monatssumme und exportiert die Ausgangsdokument-IDs', async ({ page }) => {
    const exports: unknown[] = [];
    const filters: string[] = [];
    await page.context().route('**/api/**', async route => {
        const url = new URL(route.request().url());
        const json = (body: unknown) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
        if (url.pathname === '/api/auth/me') return json({ id: 1, username: 'test', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: true, requiresInitialSetup: false });
        if (url.pathname === '/api/notifications/summary') return json({ totalCount: 0, categories: [], recentItems: [] });
        if (url.pathname === '/api/firma') return json({});
        if (url.pathname.startsWith('/api/formulare/templates/selection')) return route.fulfill({ status: 404, body: '' });
        if (url.pathname === '/api/ausgangs-dokumente/42') return json({ ...BEISPIEL_DOKUMENT, id: 42, typ: 'STORNO', gebucht: true });
        if (url.pathname.startsWith('/api/datensatz-locks/')) {
            if (route.request().method() === 'DELETE') return route.fulfill({ status: 204, body: '' });
            return json({ status: 'ACQUIRED', holderUserId: 1, holderDisplayName: 'Max Mustermann', acquiredAt: new Date().toISOString(), lastHeartbeatAt: new Date().toISOString() });
        }
        if (url.pathname === '/api/rechnungsuebersicht/ausgang') {
            filters.push(url.search);
            const search = url.searchParams.get('search')?.toLowerCase();
            return json(search ? rechnungen.filter(r => r.dokumentid.toLowerCase().includes(search)) : rechnungen);
        }
        if (url.pathname === '/api/rechnungsuebersicht/merge-pdf') {
            exports.push(route.request().postDataJSON());
            return route.fulfill({ contentType: 'application/pdf', body: '%PDF-1.4\n%%EOF' });
        }
        return json([]);
    });
    await page.goto('/rechnungsuebersicht');
    const storno = page.getByRole('row').filter({ hasText: 'ST-2026/09/00001' });
    await expect(storno).toContainText('-119,00 €');
    await expect(storno).toContainText('Storno');
    await expect(page.getByRole('row').filter({ hasText: 'RE-2026/09/00001' })).toContainText('Storniert');
    await expect(page.getByText('Gesamtsumme', { exact: true }).locator('..')).toContainText('0,00 €');
    await expect(page.getByRole('link', { name: 'ST-2026/09/00001 im Dokumenteditor öffnen' })).toHaveAttribute('href', '/dokument-editor?dokumentId=42&dokumentTyp=STORNO');
    const popupPromise = page.waitForEvent('popup');
    await page.getByRole('link', { name: 'ST-2026/09/00001 im Dokumenteditor öffnen' }).click();
    const editor = await popupPromise;
    await expect(editor.getByText('Musterweg 1')).toBeVisible();
    expect(await editor.evaluate(() => window.opener === null)).toBe(true);
    const closed = editor.waitForEvent('close');
    await editor.getByRole('button', { name: 'Editor schließen' }).click();
    await closed;
    await expect(storno).toBeVisible();
    await page.getByRole('combobox').filter({ hasText: 'Alle Monate' }).click();
    await page.getByRole('option', { name: 'September', exact: true }).click();
    await expect.poll(() => filters.some(f => f.includes('month=9'))).toBe(true);
    await page.getByRole('columnheader').getByRole('checkbox').check();
    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Gesamt-PDF (2)' }).click();
    expect((await download).suggestedFilename()).toMatch(/^Rechnungen_\d{4}_09\.pdf$/);
    expect(exports).toEqual([{ ausgangIds: [41, 42], eingangIds: [] }]);
    await page.getByPlaceholder(/Suchen/i).fill('ST-2026');
    await expect(page.getByRole('row').filter({ hasText: 'RE-2026/09/00001' })).toHaveCount(0);
    await expect(storno).toBeVisible();
    await expect(page.getByRole('button', { name: /Gesamt-PDF/ })).toHaveCount(0);
    await page.getByRole('button', { name: 'Eingangsrechnungen', exact: true }).click();
    await expect(storno).toHaveCount(0);
});

test('zeigt unbekannten Zahlungsstatus und nennt ein fehlendes PDF ohne Teildownload', async ({ page }) => {
    const downloads: string[] = [];
    page.on('download', download => downloads.push(download.suggestedFilename()));
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname;
        const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
        if (path === '/api/auth/me') return json({ id: 1, username: 'test', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: true, requiresInitialSetup: false });
        if (path === '/api/notifications/summary') return json({ totalCount: 0, categories: [], recentItems: [] });
        if (path === '/api/rechnungsuebersicht/ausgang') return json([{ ...rechnungen[0], storniert: false, bezahlt: null }]);
        if (path === '/api/rechnungsuebersicht/merge-pdf') return json({ message: 'PDF für RE-2026/09/00001 fehlt. Bitte das Dokument im Dokumenteditor exportieren.' }, 400);
        return json([]);
    });
    await page.goto('/rechnungsuebersicht');
    await expect(page.getByText('Unbekannt', { exact: true })).toBeVisible();
    await page.getByRole('row').filter({ hasText: 'RE-2026/09/00001' }).getByRole('checkbox').check();
    await page.getByRole('button', { name: 'Gesamt-PDF (1)' }).click();
    await expect(page.getByText('PDF für RE-2026/09/00001 fehlt. Bitte das Dokument im Dokumenteditor exportieren.', { exact: true })).toBeVisible();
    expect(downloads).toEqual([]);
});
