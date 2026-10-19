import { test, expect } from './hilfen/test';
import type { Page } from '@playwright/test';
import { designPruefung } from './hilfen/design';

/**
 * Bezahlt-Haken im Offene-Posten-Center: Schlägt das Speichern fehl, sieht der
 * Nutzer die Meldung als Toast und der Haken zeigt weiter den gespeicherten Stand.
 *
 * Alle Namen sind Dummy-Daten (DSGVO).
 */
const rechnung = {
    id: 7,
    rechnungsnummer: 'RE-2026-001',
    rechnungsdatum: '2026-10-01',
    faelligkeitsdatum: '2026-10-15',
    rechnungsbetrag: 1190,
    bezahlt: false,
    mahnung: false,
    projektId: 3,
    projektAuftragsnummer: 'P-2026-003',
    projektKunde: 'Max Mustermann',
};

/** Stubbt die Seite; `bezahltAntwort` beantwortet den PATCH auf den Haken. */
async function stubbeApi(page: Page, bezahltAntwort: { status: number; body?: unknown }, startBezahlt = false) {
    let bezahlt = startBezahlt;
    await page.route('**/api/**', async (route) => {
        const url = new URL(route.request().url());
        const json = (body: unknown, status = 200) =>
            route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
        if (url.pathname === '/api/auth/me') return json({ id: 1, username: 'test', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: true, requiresInitialSetup: false });
        if (url.pathname === '/api/notifications/summary') return json({ totalCount: 0, categories: [], recentItems: [] });
        if (url.pathname === '/api/firma') return json({});
        // Wie das Backend: Die Liste enthält nur unbezahlte Rechnungen.
        if (url.pathname === '/api/projekte/offene-posten') return json(bezahlt ? [] : [rechnung]);
        // Bezahlte Kundenrechnungen der letzten 12 Monate (nur mit „Bezahlte anzeigen“).
        if (url.pathname === '/api/projekte/offene-posten/bezahlt') return json(bezahlt ? [{ ...rechnung, bezahlt: true }] : []);
        if (url.pathname === '/api/projekte/dokumente/7/bezahlt' && route.request().method() === 'PATCH') {
            if (bezahltAntwort.status < 300) bezahlt = url.searchParams.get('bezahlt') === 'true';
            return bezahltAntwort.body === undefined
                ? route.fulfill({ status: bezahltAntwort.status })
                : json(bezahltAntwort.body, bezahltAntwort.status);
        }
        return json([]);
    });
}

test('zeigt bei einem Konflikt die Meldung und lässt den Haken offen', async ({ page }, testInfo) => {
    const meldung = 'Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.';
    await stubbeApi(page, { status: 409, body: { status: 409, message: meldung, fields: [] } });
    await page.goto('/offeneposten');

    const haken = page.getByRole('checkbox', { name: 'Bezahlt', exact: true });
    await haken.click();

    await expect(page.getByRole('region', { name: 'Meldungen' }).getByText(meldung)).toBeVisible();
    await expect(page.getByRole('checkbox', { name: 'Bezahlt', exact: true })).not.toBeChecked();
    await expect(page.getByRole('checkbox', { name: 'Bezahlt', exact: true })).toBeEnabled();
    await designPruefung(page, testInfo, 'offene-posten-bezahlt-konflikt');
});

test('nimmt die Rechnung aus der Liste, wenn das Speichern klappt', async ({ page }) => {
    await stubbeApi(page, { status: 204 });
    await page.goto('/offeneposten');

    await page.getByRole('checkbox', { name: 'Bezahlt', exact: true }).click();

    await expect(page.getByText('Alle Rechnungen wurden bezahlt.')).toBeVisible();
    await expect(page.getByText('RE-2026-001')).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Meldungen' }).getByText(/konnte nicht/)).toHaveCount(0);
    await expect(page.getByRole('region', { name: 'Meldungen' }).getByText(/Über „Bezahlte anzeigen“/)).toBeVisible();
});

test('Bezahlte anzeigen: ein versehentlicher Haken lässt sich zurücknehmen', async ({ page }, testInfo) => {
    await stubbeApi(page, { status: 204 }, true);
    await page.goto('/offeneposten');
    await expect(page.getByText('Alle Rechnungen wurden bezahlt.')).toBeVisible();

    const bezahlteGeladen = page.waitForResponse(res => new URL(res.url()).pathname === '/api/projekte/offene-posten/bezahlt');
    await page.getByRole('checkbox', { name: 'Bezahlte anzeigen' }).check();
    await bezahlteGeladen;
    const zeile = page.locator('tr[data-bezahlt]', { hasText: 'RE-2026-001' });
    await expect(zeile).toBeVisible();
    await expect(zeile.getByRole('checkbox', { name: 'Bezahlt', exact: true })).toBeChecked();
    // Die bezahlte Rechnung zählt nicht zu den offenen Posten.
    await expect(page.getByText('Offene Summe').locator('xpath=following-sibling::p')).toHaveText('0,00 €');

    await page.getByRole('button', { name: 'Mehr zu: Bezahlte anzeigen' }).click();
    await expect(page.getByRole('dialog', { name: 'Bezahlte anzeigen' })).toContainText('Stornierte Rechnungen erscheinen hier nicht.');
    await designPruefung(page, testInfo, 'offene-posten-bezahlte-anzeigen');
    await page.keyboard.press('Escape');

    const zurueck = page.waitForRequest(req => req.method() === 'PATCH' && req.url().includes('/api/projekte/dokumente/7/bezahlt?bezahlt=false'));
    await zeile.getByRole('checkbox', { name: 'Bezahlt', exact: true }).click();
    await zurueck;

    await expect(page.getByRole('region', { name: 'Meldungen' }).getByText('Die Rechnung ist wieder offen.')).toBeVisible();
    const offeneZeile = page.locator('tr[data-dokument-id="7"]:not([data-bezahlt])');
    await expect(offeneZeile).toBeVisible();
    await expect(offeneZeile.getByRole('checkbox', { name: 'Bezahlt', exact: true })).not.toBeChecked();
    await expect(page.getByText('Offene Summe').locator('xpath=following-sibling::p')).toHaveText('1.190,00 €');
});

test('erklärt am Status, was der Haken im Hintergrund auslöst', async ({ page }, testInfo) => {
    await stubbeApi(page, { status: 204 });
    await page.goto('/offeneposten');

    await page.getByRole('button', { name: /Haken „Bezahlt“/ }).click();

    await expect(page.getByText(/gilt das Projekt als bezahlt und wird automatisch beendet/)).toBeVisible();
    await designPruefung(page, testInfo, 'offene-posten-bezahlt-info');
});

test('Eingangsrechnungen: zeigt bei einem Konflikt die Meldung und lässt den Haken offen', async ({ page }, testInfo) => {
    const meldung = 'Jemand anders hat diese Daten gerade gespeichert. Ihre Änderungen wurden nicht übernommen — bitte neu laden.';
    await stubbeApi(page, { status: 204 });
    const lieferantenRechnung = {
        id: 11, dokumentId: 21, lieferantId: 31, lieferantName: 'Musterstahl GmbH',
        dokumentNummer: 'ER-2026-011', dokumentDatum: '2026-10-01', zahlungsziel: '2026-10-31',
        betragNetto: 100, betragBrutto: 119, bezahlt: false, bezahltAm: null, bereitsGezahlt: false,
        zahlungsart: null, dateiname: 'er-011.pdf', pdfUrl: '/api/dokumente/er-011.pdf',
        ueberfaellig: false, genehmigt: true, darfGenehmigen: true,
        skontoTage: null, skontoProzent: null, nettoTage: null, skontoFrist: null,
        skontoVerbleibendeTage: null, skontoAbgelaufen: false, tatsaechlichGezahlt: null, mitSkonto: false,
    };
    // Nach stubbeApi registriert, damit diese Route zuerst greift.
    await page.route('**/api/offene-posten/eingang**', route => route.request().method() === 'PUT'
        ? route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ status: 409, message: meldung, fields: [] }) })
        : route.fulfill({ contentType: 'application/json', body: JSON.stringify([lieferantenRechnung]) }));
    await page.goto('/offeneposten?tab=eingang');

    const haken = page.getByRole('checkbox', { name: 'Offen' });
    await haken.click();

    await expect(page.getByRole('region', { name: 'Meldungen' }).getByText(meldung)).toBeVisible();
    await expect(page.getByRole('checkbox', { name: 'Offen' })).not.toBeChecked();
    await expect(page.getByRole('checkbox', { name: 'Offen' })).toBeEnabled();
    await designPruefung(page, testInfo, 'eingangsrechnungen-bezahlt-konflikt');

    // Der Genehmigt-Haken ist eine versteckte Checkbox: Fokus per Tastatur muss sichtbar sein.
    const genehmigt = page.getByRole('checkbox', { name: 'Genehmigung zurücknehmen' });
    await genehmigt.focus();
    await expect(genehmigt).toBeFocused();
    const ring = await genehmigt.evaluate(el => getComputedStyle(el.closest('label')!).boxShadow);
    expect(ring).not.toBe('none');
    await designPruefung(page, testInfo, 'eingangsrechnungen-genehmigt-fokus');
});
