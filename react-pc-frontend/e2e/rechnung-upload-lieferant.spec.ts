import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Rechnungsuebersicht -> "Rechnung hochladen": Der von der Analyse erkannte
 * Lieferant wird vorausgewaehlt, auch wenn die Lieferantenliste erst NACH der
 * Analyse ankommt. Die Liste laedt erst, wenn der Dialog aufgeht; frueher wurde
 * nur beim Eintreffen der Analyse zugeordnet, und war die Liste da noch nicht
 * da, blieb die Auswahl leer und "Speichern" gesperrt. Ausserdem las der Dialog
 * die Antwort von GET /api/lieferanten als nacktes Array -- live ist sie eine
 * Seite ({ lieferanten: [...] }), die Liste blieb also immer leer.
 *
 * Die Reihenfolge ist hier erzwungen: Die Antwort auf /api/lieferanten haelt der
 * Stub zurueck, bis der Test sie freigibt.
 *
 * Alle Namen sind Dummy-Daten (DSGVO).
 */
test('erkannter Lieferant wird vorausgewaehlt, auch wenn die Liste erst nach der Analyse kommt', async ({ page }, testInfo) => {
    let listeFreigeben!: () => void;
    const listeFrei = new Promise<void>((resolve) => { listeFreigeben = resolve; });
    const importe: string[] = [];

    await page.context().route('**/api/**', async (route) => {
        const url = new URL(route.request().url());
        const json = (body: unknown) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
        if (url.pathname === '/api/auth/me') return json({ id: 1, username: 'test', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: true, requiresInitialSetup: false });
        if (url.pathname === '/api/notifications/summary') return json({ totalCount: 0, categories: [], recentItems: [] });
        if (url.pathname === '/api/firma') return json({});
        if (url.pathname === '/api/lieferanten') {
            await listeFrei;
            // Echte Antwortform: eine Seite mit "lieferanten", kein nacktes Array.
            return json({
                lieferanten: [{ id: 1, lieferantenname: 'Testlieferant GmbH' }, { id: 2, lieferantenname: 'Mustermann Metallbau' }],
                gesamt: 2, seite: 0, seitenGroesse: 1000,
            });
        }
        if (url.pathname === '/api/rechnungsuebersicht/analyze-upload') {
            return json([{ analyzeResponse: { betragBrutto: 238, dokumentTyp: 'RECHNUNG', dokumentNummer: 'RE-4711', dokumentDatum: '2026-10-01', lieferantName: 'Testlieferant' } }]);
        }
        if (url.pathname === '/api/rechnungsuebersicht/import-upload') {
            importe.push(route.request().postData() ?? '');
            return json({});
        }
        return json([]);
    });

    await page.goto('/rechnungsuebersicht');
    await page.getByRole('button', { name: 'Rechnung hochladen' }).click();
    await page.locator('input[type=file]').setInputFiles({ name: 'rechnung.pdf', mimeType: 'application/pdf', buffer: Buffer.from('dummy') });

    // Analyse ist da, die Liste noch nicht: noch nichts gewaehlt.
    await expect(page.getByText('Analyse erfolgreich')).toBeVisible();
    const auswahl = page.getByRole('combobox', { name: 'Lieferant' });
    const speichern = page.getByRole('button', { name: 'Speichern', exact: true });
    await expect(auswahl).toHaveText('Bitte wählen...');
    await expect(speichern).toBeDisabled();

    listeFreigeben();
    await expect(auswahl).toHaveText('Testlieferant GmbH');
    await expect(speichern).toBeEnabled();

    await designPruefung(page, testInfo, 'rechnung-upload-lieferant-vorausgewaehlt', { primaerAktion: speichern });

    await speichern.click();
    await expect.poll(() => importe.length).toBe(1);
    expect(importe[0]).toMatch(/"lieferantId":1[,}]/);
    await expect(page.getByRole('dialog', { name: 'Rechnung importieren' })).toBeHidden();
});
