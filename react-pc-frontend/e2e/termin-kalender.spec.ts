import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Terminkalender (#267): Woche und Monat laden den sichtbaren Zeitraum über
 * `von`/`bis`, Feiertage und Abwesenheiten öffnen keinen Termin-Dialog, und
 * fremde Termine erscheinen nur lesend. Ohne Backend, alle Daten Dummy (DSGVO).
 */

const termin = (teil: Record<string, unknown>) => ({
    id: 11, titel: 'Montage Treppe', beschreibung: null, datum: '2026-10-02', startZeit: '09:00:00', endeZeit: '11:00:00',
    ganztaegig: false, farbe: '#dc2626', projektId: null, projektName: null, kundeId: null, kundeName: null,
    lieferantId: null, lieferantName: null, anfrageId: null, anfrageBetreff: null, erstellerId: 2, erstellerName: 'Erika Musterfrau',
    teilnehmer: [], darfBearbeiten: true, ...teil,
});

const TERMINE = [
    termin({}),
    termin({ id: 12, titel: 'Abnahme Geländer', datum: '2026-09-29', startZeit: '14:00:00', endeZeit: '15:00:00', farbe: '#2563eb', darfBearbeiten: false }),
];

async function stubbeApi(page: Page) {
    const kalenderAbrufe: string[] = [];
    const schreibzugriffe: string[] = [];
    await page.route('**/api/**', async route => {
        const request = route.request();
        const url = new URL(request.url());
        const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
        if (request.method() !== 'GET') {
            schreibzugriffe.push(`${request.method()} ${url.pathname}`);
            return json({ id: 1 });
        }
        if (url.pathname === '/api/auth/me') return json({ id: 1, username: 'max', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        if (url.pathname === '/api/notifications/summary') return json({ totalCount: 0, categories: [], recentItems: [] });
        if (url.pathname === '/api/firma') return json({});
        if (url.pathname === '/api/kalender') {
            kalenderAbrufe.push(url.search);
            const von = url.searchParams.get('von') ?? '';
            const bis = url.searchParams.get('bis') ?? '';
            return json(TERMINE.filter(t => t.datum >= von && t.datum <= bis));
        }
        if (url.pathname === '/api/zeiterfassung/feiertage') {
            return json(url.searchParams.get('jahr') === '2026' ? [{ datum: '2026-10-03', bezeichnung: 'Tag der Deutschen Einheit' }] : []);
        }
        if (url.pathname === '/api/abwesenheit/team') {
            return json([{ id: 5, datum: '2026-10-01', typ: 'URLAUB', stunden: 8, mitarbeiterId: 2, mitarbeiterName: 'Erika Musterfrau' }]);
        }
        if (url.pathname === '/api/mitarbeiter') {
            return json([{ id: 1, vorname: 'Max', nachname: 'Mustermann', aktiv: true }, { id: 2, vorname: 'Erika', nachname: 'Musterfrau', aktiv: true }]);
        }
        return json([]);
    });
    return { kalenderAbrufe, schreibzugriffe };
}

test('Kalender: Woche über die Monatsgrenze, Feiertag ohne Dialog, fremder Termin nur lesend', async ({ page }, testInfo) => {
    const { kalenderAbrufe, schreibzugriffe } = await stubbeApi(page);
    await page.goto('/kalender?date=2026-09-30');
    await expect(page.getByText('Mittwoch, 30. September 2026')).toBeVisible();

    // Wochenansicht Mo 28.09. – So 04.10. lädt genau diesen Zeitraum.
    const wochenAbruf = page.waitForRequest(req => req.url().includes('/api/kalender?von=2026-09-28&bis=2026-10-04'));
    await page.getByRole('button', { name: 'Woche', exact: true }).click();
    await wochenAbruf;
    await expect(page.getByText('KW 40')).toBeVisible();

    // Termine aus beiden Monaten erscheinen, Rot bleibt Rot.
    const montage = page.getByRole('button', { name: /Montage Treppe/ });
    await expect(montage).toBeVisible();
    await expect(montage).toHaveClass(/bg-red-100/);
    await expect(page.getByRole('button', { name: /Abnahme Geländer/ })).toBeVisible();

    // Feiertag und Urlaub sind nur Hinweise: Klick öffnet nichts.
    const feiertag = page.getByTitle('Tag der Deutschen Einheit – Gesetzlicher Feiertag');
    await expect(feiertag).toBeVisible();
    await feiertag.click();
    await page.getByTitle('Erika Musterfrau – Urlaub').click();
    await expect(page.getByRole('dialog')).toHaveCount(0);

    // Fremder Termin: nur lesen, mit Hinweis und erreichbarem Info-Knopf.
    await page.getByRole('button', { name: /Abnahme Geländer/ }).click();
    const dialog = page.getByRole('dialog', { name: 'Termin ansehen' });
    await expect(dialog).toContainText('Diesen Termin können nur der Ersteller, die Teilnehmer und Admins ändern.');
    await expect(dialog.getByLabel(/Titel/)).toBeDisabled();
    await expect(dialog.getByRole('button', { name: 'Speichern' })).toHaveCount(0);
    await dialog.getByRole('button', { name: 'Mehr zu: Teilnehmer' }).click();
    await expect(page.getByRole('dialog', { name: 'Teilnehmer' })).toContainText('Firmentermine und für alle sichtbar');
    await designPruefung(page, testInfo, 'kalender-termin-nur-lesen');
    await page.keyboard.press('Escape');
    await dialog.getByRole('button', { name: 'Schließen', exact: true }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);

    // Nächste Woche liegt ganz im Oktober – auch sie wird nachgeladen, das Monatsfeld folgt.
    const naechsteWoche = page.waitForRequest(req => req.url().includes('/api/kalender?von=2026-10-05&bis=2026-10-11'));
    await page.getByRole('button', { name: 'Nächste Woche' }).click();
    await naechsteWoche;
    await expect(page.getByRole('button', { name: 'Monat: Oktober' })).toBeVisible();

    // Monatsraster mit angeschnittenen Tagen: 28.09. bis 01.11.
    const monatsAbruf = page.waitForRequest(req => req.url().includes('/api/kalender?von=2026-09-28&bis=2026-11-01'));
    await page.getByRole('button', { name: 'Monat', exact: true }).click();
    await monatsAbruf;
    await expect(page.locator('[data-datum="2026-09-29"]').getByRole('button', { name: /Abnahme Geländer/ })).toBeVisible();
    await expect(page.locator('[data-datum="2026-10-03"]')).toContainText('Tag der Deutschen Einheit');
    await designPruefung(page, testInfo, 'kalender-monat-angeschnitten');

    expect(kalenderAbrufe.some(suche => suche.includes('jahr='))).toBe(false);
    expect(schreibzugriffe).toEqual([]);
});
