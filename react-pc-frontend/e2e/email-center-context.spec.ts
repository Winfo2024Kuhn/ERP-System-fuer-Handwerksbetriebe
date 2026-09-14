import { test, expect } from './hilfen/test';
import type { Page } from '@playwright/test';

const messages = [701, 702].map((id, index) => ({
    id, type: 'EMAIL', direction: 'IN', subject: `Montage ${index + 1}`,
    fromAddress: 'Max Mustermann <test@example.com>', recipient: 'betrieb@example.com',
    body: `Neue Nachricht ${index + 1}`, htmlBody: `<p>Neue Nachricht ${index + 1}</p>`,
    sentAt: '2026-09-14T12:30:00', isRead: true, isStarred: true,
    folder: index === 0 ? 'inbox' : 'newsletter', attachments: [],
}));

async function prepare(page: Page, empty = false) {
    await page.route('**/api/**', async route => {
        const path = new URL(route.request().url()).pathname;
        const message = messages.find(mail => path.includes(`/${mail.id}`));
        let body: unknown = [];
        if (path.endsWith('/stats')) body = { inboxCount: 2, starredCount: 2, unassignedCount: 2 };
        else if (path.endsWith('/from-addresses')) body = ['betrieb@example.com'];
        else if (path.endsWith('/thread')) body = { rootEmailId: message?.id, focusedEmailId: message?.id, emails: [message] };
        else if (path.endsWith('/search')) body = messages;
        else if (/\/emails\/(starred|unassigned|newsletter)$/.test(path)) body = empty ? [] : messages;
        else if (message) body = message;
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    });
}

for (const [folder, label] of [['starred', 'Markiert'], ['unassigned', 'Nicht zugeordnet']]) {
    test(`${label}: Mail öffnen erhält Ordner und lokale Suche`, async ({ page }) => {
        await prepare(page);
        await page.goto(`/emails/${folder}`);
        const search = page.getByPlaceholder('In diesem Ordner suchen...');
        await search.fill('Montage');
        for (const mail of messages) {
            await page.getByText(mail.subject, { exact: true }).first().click();
            await expect(page.getByRole('heading', { name: mail.subject })).toBeVisible();
            await expect(page).toHaveURL(new RegExp(`/emails/${folder}/${mail.id}$`));
            await expect(page.getByRole('button', { name: label, exact: true })).toHaveAttribute('aria-current', 'page');
            await expect(search).toHaveValue('Montage');
            await expect(page.getByText(messages[1].subject, { exact: true }).first()).toBeVisible();
        }
    });
}

test('Globale Suchergebnisse bleiben beim Öffnen und Wechseln einer Mail bestehen', async ({ page }) => {
    await prepare(page, true);
    await page.goto('/emails/unassigned');
    await page.getByRole('button', { name: 'Alle Ordner', exact: true }).click();
    const search = page.getByPlaceholder('Globale Suche (alle Ordner)...');
    await search.fill('Montage');
    for (const mail of messages) {
        await page.getByText(mail.subject, { exact: true }).first().click();
        await expect(page.getByRole('heading', { name: mail.subject })).toBeVisible();
        await expect(page).toHaveURL(new RegExp(`/emails/unassigned/${mail.id}$`));
        await expect(search).toHaveValue('Montage');
        await expect(page.getByRole('button', { name: 'Nicht zugeordnet', exact: true })).toHaveAttribute('aria-current', 'page');
    }
});

test('Direktlink lädt die Nachricht auch bei leerer Ansicht und erhält deren Ordner', async ({ page }) => {
    await prepare(page, true);
    await page.goto('/emails/starred/701');
    await expect(page.getByRole('heading', { name: 'Montage 1' })).toBeVisible();
    await expect(page).toHaveURL(/\/emails\/starred\/701$/);
    await expect(page.getByRole('button', { name: 'Markiert', exact: true })).toHaveAttribute('aria-current', 'page');
});


test('Verspätete Detailantwort überschreibt keine neuere Mehrfachauswahl', async ({ page }) => {
    await prepare(page);
    let release!: () => void;
    const delayed = new Promise<void>(resolve => { release = resolve; });
    await page.route('**/api/emails/701', async route => {
        await delayed;
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(messages[0]) });
    });
    await page.goto('/emails/starred');
    const pending = page.waitForRequest('**/api/emails/701');
    await page.getByText('Montage 1', { exact: true }).first().click();
    await pending;
    await page.getByText('Montage 2', { exact: true }).first().click({ modifiers: ['ControlOrMeta'] });
    await expect(page.getByText('2 ausgewählt', { exact: true })).toBeVisible();
    const delivered = page.waitForResponse('**/api/emails/701');
    release();
    await (await delivered).finished();
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
    await expect(page.getByText('2 E-Mails ausgewählt', { exact: true })).toBeVisible();
    await expect(page.getByText('2 ausgewählt', { exact: true })).toBeVisible();
});

for (const target of messages) {
    test(`Browser Zurück aus Nachricht ${target.id} erhält den Direktlink im vorherigen Ordner`, async ({ page }) => {
        await prepare(page);
        await page.goto('/emails/starred/701');
        await expect(page.getByRole('heading', { name: 'Montage 1' })).toBeVisible();
        await page.getByRole('button', { name: 'Newsletter', exact: true }).click();
        await page.getByText(target.subject, { exact: true }).first().click();
        await expect(page.getByRole('heading', { name: target.subject })).toBeVisible();
        await page.goBack();
        await expect(page).toHaveURL(/\/emails\/starred\/701$/);
        await expect(page.getByRole('heading', { name: 'Montage 1' })).toBeVisible();
    });
}
