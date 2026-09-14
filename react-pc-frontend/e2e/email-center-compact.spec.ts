import { test, expect } from './hilfen/test';
import type { Locator, Page } from '@playwright/test';

const email = {
    id: 701, type: 'EMAIL', direction: 'IN', subject: 'Rückfrage zur Montage am Mittwoch',
    fromAddress: 'Max Mustermann <max.mustermann@example.com>', recipient: 'betrieb@example.com',
    body: 'Passt der Termin am Mittwoch?', htmlBody: '<p>Passt der Termin am Mittwoch?</p>',
    sentAt: '2026-09-14T12:30:00', isRead: true, attachments: [],
};

async function prepare(page: Page) {
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname;
        let body: unknown = [];
        if (path.endsWith('/stats')) body = { inboxCount: 1, newsletterCount: 1 };
        else if (path.endsWith('/from-addresses')) body = ['betrieb@example.com'];
        else if (path.endsWith('/thread')) body = { rootEmailId: 701, focusedEmailId: 701, emails: [email] };
        else if (/\/emails\/(inbox|newsletter)$/.test(path)) body = [email];
        else if (path.endsWith('/701')) body = email;
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    });
    await page.goto('/emails/newsletter/701');
    await expect(page.getByRole('heading', { name: email.subject })).toBeVisible();
}

async function insideViewport(page: Page, element: Locator) {
    await expect(element).toBeVisible();
    const box = await element.boundingBox();
    const viewport = page.viewportSize()!;
    expect(box).not.toBeNull();
    expect(box!.x).toBeGreaterThanOrEqual(0);
    expect(box!.x + box!.width).toBeLessThanOrEqual(viewport.width);
    expect(box!.y + box!.height).toBeLessThanOrEqual(viewport.height);
}

test('Kompakte Kopfzeile, erreichbare Aktionen und tastaturbedienbares Menü', async ({ page }, testInfo) => {
    await prepare(page);
    const actions = page.getByRole('button', { name: 'Weitere E-Mail-Aktionen' });
    await insideViewport(page, actions);
    await insideViewport(page, page.getByRole('button', { name: 'Antworten', exact: true }).first());
    const header = page.getByTestId('email-detail-header');
    expect((await header.boundingBox())!.height).toBeLessThan(145);
    await actions.focus();
    await page.keyboard.press('Enter');
    await insideViewport(page, page.getByRole('menuitem', { name: 'Weiterleiten', exact: true }));
    await insideViewport(page, page.getByRole('menuitem', { name: 'In Papierkorb', exact: true }));
    await page.screenshot({ path: testInfo.outputPath('mail-aktionen.png') });
    await page.keyboard.press('Escape');
    await expect(actions).toBeFocused();
    await expect(page.getByRole('menu')).toHaveCount(0);
    await page.screenshot({ path: testInfo.outputPath('mail-kompakt.png') });
});

test('Eingeklappte Ordner behalten alle Ziele als erreichbare Symbole', async ({ page }) => {
    await prepare(page);
    await page.getByTitle('Ordnerleiste einklappen').click();
    const sidebar = page.getByRole('navigation', { name: 'E-Mail-Ordner' });
    for (const name of ['Projekte', 'Anfragen', 'Lieferanten', 'Steuerberater', 'Einstellungen']) {
        await insideViewport(page, sidebar.getByRole('button', { name, exact: true }));
    }
    expect(await sidebar.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
});

test('Große gespeicherte Spaltenbreiten lassen Platz zum Lesen', async ({ page }) => {
    await page.addInitScript(() => {
        localStorage.setItem('email_center_sidebar_width', '400');
        localStorage.setItem('email_center_list_width', '700');
    });
    await prepare(page);
    const readingPane = page.getByRole('region', { name: 'E-Mail lesen und schreiben' });
    expect((await readingPane.boundingBox())!.width).toBeGreaterThanOrEqual(460);
    await insideViewport(page, page.getByRole('button', { name: 'Weitere E-Mail-Aktionen' }));
    const main = page.locator('main');
    expect(await main.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
});

test('Auch bei niedriger Fensterhöhe bleiben Ordner und Aktionen erreichbar', async ({ page }, info) => {
    await page.setViewportSize({ width: 1440, height: 800 });
    await prepare(page);
    await insideViewport(page, page.getByRole('button', { name: 'Weitere E-Mail-Aktionen' }));
    const sidebar = page.getByRole('navigation', { name: 'E-Mail-Ordner' });
    await insideViewport(page, sidebar.getByRole('button', { name: 'Einstellungen', exact: true }));
    await sidebar.getByRole('button', { name: 'Steuerberater', exact: true }).scrollIntoViewIfNeeded();
    await insideViewport(page, sidebar.getByRole('button', { name: 'Steuerberater', exact: true }));
    await page.screenshot({ path: info.outputPath('mail-1440x800.png') });
});
