import { expect, test } from './hilfen/test';
import { designPruefung } from './hilfen/design';

test('Postfachwechsel begrenzt die E-Mail-Liste auf Einkauf', async ({ page }, testInfo) => {
    const inboxUrls: string[] = [];
    const imports: string[] = [];
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        if (url.pathname.endsWith('/emails/inbox')) inboxUrls.push(url.href);
        if (url.pathname.endsWith('/einkauf/mail/abruf')) imports.push(url.href);
        const body = url.pathname.endsWith('/emails/stats') ? {} : [];
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });
    });
    await page.goto('/emails/inbox');
    const einkauf = page.getByRole('button', { name: 'Einkaufspostfach' });
    await expect(einkauf).toBeVisible();
    await einkauf.click();
    await expect(page).toHaveURL(/kontoId=EINKAUF/);
    await expect.poll(() => inboxUrls.some(url => new URL(url).searchParams.get('kontoId') === 'EINKAUF')).toBe(true);
    const abrufen = page.getByRole('button', { name: 'Einkaufpostfach abrufen' });
    await expect(abrufen).toBeVisible();
    await designPruefung(page, testInfo, 'einkauf-email-center-postfach', { primaerAktion: abrufen });
    await abrufen.click();
    await expect.poll(() => imports.some(url => new URL(url).pathname.endsWith('/einkauf/mail/abruf'))).toBe(true);
});

test('Einkaufsantwort wird erst nach hashgebundener Vorschau beauftragt', async ({ page }, testInfo) => {
    const requests: { path: string; method: string; body: unknown }[] = [];
    const email = { id: 55, type: 'email', kontoId: 'EINKAUF', einkaufTyp: 'ANFRAGE', einkaufVorgangId: 81,
        einkaufNummer: 'PA-81', zuordnungPruefen: false, direction: 'IN', subject: 'Angebot Rohre',
        fromAddress: 'Musterlieferant <lieferant@example.test>', recipient: 'einkauf@example.test', body: 'Bitte Rückmeldung.',
        htmlBody: '<p>Bitte Rückmeldung.</p>', sentAt: '2026-09-24T09:00:00', attachments: [], hasAttachments: false };
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        const method = route.request().method();
        let body: unknown = [];
        if (method !== 'GET') {
            const raw = route.request().postData();
            try { body = raw ? JSON.parse(raw) : null; } catch { body = raw; }
        }
        requests.push({ path: url.pathname, method, body });
        let response: unknown = [];
        if (url.pathname.endsWith('/emails/from-addresses')) response = [];
        else if (url.pathname.endsWith('/emails/stats')) response = {};
        else if (url.pathname.endsWith('/emails/inbox')) response = [email];
        else if (url.pathname === '/api/emails/55') response = email;
        else if (url.pathname === '/api/emails/55/thread') response = { rootEmailId: 55, focusedEmailId: 55, emails: [{ ...email, id: 55 }] };
        else if (url.pathname === '/api/einkauf/mail/55/antwort-vorschau') response = {
            vorschauHash: 'preview-token-dummy', subject: 'Re: Angebot Rohre', htmlBody: '<p>Danke, wir melden uns.</p>',
            empfaenger: 'lieferant@example.test', anlageIds: [], inReplyTo: '<source@example.test>', references: ['<source@example.test>'],
        };
        else if (url.pathname === '/api/einkauf/mail/55/antworten' && method === 'POST') response = {
            id: 910, version: 0, typ: 'ANTWORT', vorgangId: 55, revisionId: 12, status: 'LAEUFT',
        };
        else if (url.pathname === '/api/einkauf/mail/55/antwortstatus') return route.fulfill({ status: 204 });
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(response) });
    });
    await page.goto('/emails/inbox');
    await page.getByRole('button', { name: 'Einkaufspostfach' }).click();
    await page.getByText('Angebot Rohre').first().click();
    await expect(page.getByTestId('einkauf-mail-bezug')).toContainText('PA-81');
    const reply = page.getByTestId('email-detail-header').getByRole('button', { name: 'Antworten' });
    await reply.click();
    const send = page.getByRole('button', { name: /E-Mail senden/i });
    await expect(send).toBeVisible();
    await designPruefung(page, testInfo, 'einkauf-email-center-antwort-editor', { primaerAktion: send });
    await send.click();
    const previewDialog = page.getByRole('dialog');
    await expect(previewDialog).toContainText('lieferant@example.test');
    await expect(previewDialog).toContainText('Re: Angebot Rohre');
    await expect(previewDialog.locator('svg.lucide-trash-2')).toHaveCount(0);
    await expect(previewDialog.locator('svg.lucide-send')).toHaveClass(/text-rose-600/);
    await designPruefung(page, testInfo, 'einkauf-email-antwort-vorschau', { primaerAktion: previewDialog.getByRole('button', { name: 'Antwort senden' }) });
    await previewDialog.getByRole('button', { name: 'Antwort senden' }).click();
    await expect.poll(() => requests.some(request => request.path === '/api/einkauf/mail/55/antworten' && request.method === 'POST')).toBe(true);
    expect(requests.some(request => request.path === '/api/emails/55/reply' || request.path === '/api/emails/send')).toBe(false);
    expect(requests.find(request => request.path === '/api/einkauf/mail/55/antwort-vorschau')?.body).toMatchObject({ anlageIds: [] });
    expect(requests.find(request => request.path === '/api/einkauf/mail/55/antworten')?.body).toMatchObject({ vorschauHash: 'preview-token-dummy' });
});
