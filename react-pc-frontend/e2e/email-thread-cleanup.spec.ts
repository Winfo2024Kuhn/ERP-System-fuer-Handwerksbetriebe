import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';

const replyHtml = '<p>Passt, danke.</p><p>Viele Grüße<br>Aktuelle Signatur</p>'
    + '<div>Am 8. September 2026 16:18:29 MESZ schrieb Max Mustermann &lt;test@example.com&gt;:</div><br>'
    + '<blockquote type="cite"><p>Vorherige Antwort</p><p>Vorherige Signatur</p></blockquote>'
    + '<p>Nachtrag: Bitte morgen liefern.</p>';

async function openThread(page: Page, htmlBody = replyHtml) {
    const initial = {
        id: 901, type: 'EMAIL', direction: 'IN', subject: 'Termin für Max Mustermann',
        sender: 'Max Mustermann', fromAddress: '"Max Mustermann" <test@example.com>',
        recipient: 'handwerk@example.com', sentAt: '2026-09-08T16:18:29',
        body: 'Ursprüngliche Anfrage', htmlBody: '<p>Ursprüngliche Anfrage</p>',
        snippet: 'Ursprüngliche Anfrage', isRead: true, attachments: [], zuordnungTyp: 'KEINE',
    };
    await page.route('**/api/**', route => {
        const pathname = new URL(route.request().url()).pathname;
        let body: unknown = [];
        if (pathname === '/api/emails/inbox') body = [initial];
        else if (pathname === '/api/emails/901/thread') body = {
            rootEmailId: 901, focusedEmailId: 902,
            emails: [initial, {
                ...initial, id: 902, direction: 'OUT', htmlBody,
                fromAddress: '"Handwerk Musterbetrieb" <handwerk@example.com>', recipient: 'test@example.com',
                sentAt: '2026-09-09T16:20:00', snippet: 'Veralteter Server-Ausschnitt mit Vorherige Signatur',
            }],
        };
        else if (pathname === '/api/emails/901') body = initial;
        else if (pathname === '/api/emails/stats') body = { inboxCount: 1, sentCount: 1, trashCount: 0, spamCount: 0, unassignedCount: 1 };
        else if (pathname === '/api/emails/from-addresses') body = ['handwerk@example.com'];
        return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    });
    await page.goto('/emails/inbox');
    await page.getByText(initial.subject, { exact: true }).click();
}

test('bereinigter Verlauf, aktuelle Signatur und per Tastatur erreichbares Original', async ({ page }, testInfo) => {
    await openThread(page);
    const frame = page.frameLocator('iframe[title="Email Content"]');
    await expect(frame.getByText('Passt, danke.', { exact: true })).toBeVisible();
    await expect(frame.getByText('Aktuelle Signatur', { exact: false })).toBeVisible();
    await expect(frame.getByText('Nachtrag: Bitte morgen liefern.')).toBeVisible();
    await expect(frame.getByText('Vorherige Signatur')).toBeHidden();
    await expect(frame.getByText('Am 8. September', { exact: false })).toBeHidden();
    const toggle = frame.getByRole('button', { name: 'Zitierten Verlauf anzeigen' });
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await toggle.focus();
    await toggle.press('Enter');
    await expect(frame.getByText('Vorherige Signatur')).toBeVisible();
    await expect(frame.getByText('Am 8. September', { exact: false })).toBeVisible();
    await frame.getByRole('button', { name: 'Zitierten Verlauf ausblenden' }).press('Space');
    await expect(frame.getByText('Vorherige Signatur')).toBeHidden();

    const header = page.getByRole('button', { name: 'Nachricht von Handwerk Musterbetrieb einklappen' });
    await expect(header).toBeVisible();
    expect(await header.evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true);
    expect(await frame.locator('body').evaluate(element => element.scrollWidth <= element.clientWidth + 1)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath('email-thread-clean.png'), animations: 'disabled' });
    await header.press('Space');
    const bubble = page.getByRole('button', { name: 'Nachricht von Handwerk Musterbetrieb öffnen' });
    await expect(bubble).toContainText('Passt, danke.');
    await expect(bubble).not.toContainText('Vorherige Signatur');
    await expect(bubble).not.toContainText('Am 8. September');
    await bubble.press('Enter');
    await expect(frame.getByText('Vorherige Signatur')).toBeHidden();
    await expect(frame.getByText('Nachtrag: Bitte morgen liefern.')).toBeVisible();
});

test('Klartext-Zitate klappen ein, Antworten zwischen Zitaten bleiben sichtbar', async ({ page }) => {
    await openThread(page, 'Aktuelle Antwort\nAm 8. September 2026 schrieb test@example.com:\n> Alte Frage\nMeine erste Antwort\n> Zweite alte Frage\nMeine zweite Antwort');
    const frame = page.frameLocator('iframe[title="Email Content"]');
    await expect(frame.getByText('Alte Frage', { exact: false }).first()).toBeHidden();
    await expect(frame.locator('body')).toContainText('Meine erste Antwort');
    await expect(frame.locator('body')).toContainText('Meine zweite Antwort');
    const visibleText = await frame.locator('body').innerText();
    expect(visibleText).toContain('Meine erste Antwort');
    expect(visibleText).toContain('Meine zweite Antwort');
    expect(visibleText).not.toContain('Zweite alte Frage');
    await expect(frame.getByRole('button', { name: 'Zitierten Verlauf anzeigen' })).toHaveCount(2);
});
