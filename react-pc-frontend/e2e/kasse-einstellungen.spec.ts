import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import type { Page, Route } from '@playwright/test';

const EINSTELLUNG = {
    mindestbestand: 50,
    ehegattengehaltAktiv: true,
    ehegattengehaltBetrag: 12.5,
    ehegattengehaltTag: 5,
    ehegattengehaltEmpfaengerName: 'Max Mustermann',
    privateinlageSachkontoId: null,
    datevBeraternummer: '1234567',
    datevMandantennummer: '54321',
    wirtschaftsjahrBeginnMonat: 1,
    kassenkontoNummer: '1000',
    bankkontoNummer: '1200',
};

function json(route: Route, body: unknown) {
    return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stub(page: Page, writes: Record<string, unknown>[]) {
    await page.route('**/api/**', async route => {
        const request = route.request();
        const path = new URL(request.url()).pathname;
        if (path === '/api/auth/me') return json(route, { id: 1, username: 'max.mustermann', displayName: 'Max Mustermann', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        if (path === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (path === '/api/buchhaltung/kasse/einstellung' && request.method() === 'GET') return json(route, EINSTELLUNG);
        if (path === '/api/buchhaltung/kasse/einstellung' && request.method() === 'PUT') { writes.push(request.postDataJSON() as Record<string, unknown>); return json(route, EINSTELLUNG); }
        if (path === '/api/buchhaltung/kasse/saldo') return json(route, { saldo: 250, mindestbestand: 50 });
        if (path === '/api/buchhaltung/kassenbuch') return json(route, { saldoStart: 250, saldoEnde: 250, summeEinnahmen: 0, summeAusgaben: 0, summePrivatentnahmen: 0, summePrivateinlagen: 0, bewegungen: [], letzterAbschluss: null, offeneBewegungen: 0 });
        if (path === '/api/buchhaltung/sachkonten') return json(route, []);
        if (path === '/api/buchhaltung/kasse/lohn-zahlung') return json(route, {});
        return json(route, []);
    });
}

test('Kassen-Einstellungen zeigen DATEV-Daten, prüfen Konten und öffnen Auszahlung', async ({ page }, info) => {
    const writes: Record<string, unknown>[] = [];
    await stub(page, writes);
    await page.goto('/belege-kasse');
    await page.getByRole('button', { name: 'Kassenbuch', exact: true }).click();
    await page.getByTitle('Mindestbestand & Automatik einstellen').click();

    await expect(page.getByRole('heading', { name: 'Kassen-Einstellungen' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Mindestbestand der Kasse' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Für den Steuerberater' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Ehegattengehalt-Automatik' })).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Beraternummer' })).toHaveValue('1234567');
    await expect(page.getByRole('textbox', { name: 'Mandantennummer' })).toHaveValue('54321');
    await expect(page.getByRole('textbox', { name: 'Kassenkonto' })).toHaveValue('1000');
    await expect(page.getByRole('textbox', { name: 'Bankkonto' })).toHaveValue('1200');
    const speichern = page.getByRole('button', { name: 'Speichern', exact: true });
    await speichern.scrollIntoViewIfNeeded();
    await designPruefung(page, info, 'kasse-einstellungen-dialog', { primaerAktion: speichern });
    await expect(speichern).toBeInViewport();

    await page.getByRole('textbox', { name: 'Kassenkonto' }).fill('12x');
    await expect(page.getByText('Bitte nur Ziffern, höchstens 8 Stellen.')).toBeVisible();
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    expect(writes).toHaveLength(0);

    await page.getByRole('textbox', { name: 'Kassenkonto' }).fill('1000');
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect.poll(() => writes.length).toBe(1);
    expect(writes[0]).toMatchObject({ datevBeraternummer: '1234567', datevMandantennummer: '54321', wirtschaftsjahrBeginnMonat: 1, kassenkontoNummer: '1000', bankkontoNummer: '1200' });

    await page.getByTitle('Mindestbestand & Automatik einstellen').click();
    await page.getByRole('button', { name: /Jetzt einmalig auszahlen/ }).click();
    await expect(page.getByRole('heading', { name: 'Ehegattengehalt zahlen' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Ehegattengehalt', exact: true })).toHaveCount(0);
});
