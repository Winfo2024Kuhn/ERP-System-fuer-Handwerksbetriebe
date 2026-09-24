import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

const admin = {
    id: 1, displayName: 'Max Mustermann', username: 'max.admin', active: true,
    roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
};
const mailkonto = {
    id: 'EINKAUF', version: 7, aktiv: true, fromAddress: 'einkauf@example.invalid', fromName: 'Einkauf',
    smtpHost: 'smtp.example.invalid', smtpPort: 465, smtpUsername: 'einkauf@example.invalid', smtpTls: 'TLS',
    imapHost: 'imap.example.invalid', imapPort: 993, imapUsername: 'einkauf@example.invalid', imapTls: 'TLS',
    inbox: 'INBOX', sent: 'Sent', smtpPasswordSet: true, imapPasswordSet: true,
    letzterAbruf: '2026-09-23T10:15:00Z', letzterFehler: null,
};

test('Einkaufs-Mailkonto trennt Verbindungstest, Speichern und bestätigte Testmail', async ({ page }, testInfo) => {
    const actions: { path: string; method: string; body?: unknown }[] = [];
    const responses: string[] = [];
    page.on('response', response => {
        if (new URL(response.url()).hostname !== 'localhost') responses.push(response.url());
    });
    await page.route('**/api/**', async route => {
        const request = route.request();
        const url = new URL(request.url());
        const method = request.method();
        const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
        if (url.pathname === '/api/auth/me') return json(admin);
        if (url.pathname === '/api/settings/smtp') return json({ host: 'smtp.example.invalid', port: 465, username: 'max@example.invalid', passwordSet: true });
        if (url.pathname === '/api/settings/imap') return json({ host: 'imap.example.invalid', port: 993, username: 'max@example.invalid', passwordSet: true });
        if (url.pathname === '/api/settings/mail-from') return json({ address: '', smtpUsername: 'max@example.invalid', name: '' });
        if (url.pathname === '/api/settings/dokument-mail') return json({ aktiv: false, host: '', port: 465, username: '', passwordSet: false, fromAddress: '', fromName: '', imapHost: '' });
        if (url.pathname === '/api/settings/einkauf-mail' && method === 'GET') return json(mailkonto);
        if (url.pathname === '/api/settings/einkauf-mail' && method === 'PUT') {
            actions.push({ path: url.pathname, method, body: request.postDataJSON() });
            return json({ ...mailkonto, version: 8 });
        }
        if (url.pathname === '/api/settings/einkauf-mail/verbindung-testen') {
            actions.push({ path: url.pathname, method });
            return json({ smtpErfolgreich: false, imapErfolgreich: true, fehlerCode: 'SMTP_NICHT_ERREICHBAR' });
        }
        if (url.pathname === '/api/settings/einkauf-mail/testmail') {
            actions.push({ path: url.pathname, method, body: request.postDataJSON() });
            return json({ status: 'ANGENOMMEN', messageId: '<dummy@erp.local>', fehlerCode: null });
        }
        if (url.pathname === '/api/settings/gemini') return json({ apiKeySet: false });
        if (url.pathname === '/api/settings/datei-ordner') return json({ pfad: '', networkUrl: '', konfiguriert: false });
        if (url.pathname === '/api/settings/anfrage-funnel-spamfilter') return json({ aktiv: true });
        if (url.pathname.startsWith('/api/settings/')) return json({});
        return json({}, 404);
    });

    await page.goto('/einstellungen');
    await expect(page.getByRole('heading', { name: 'Einkaufs-Postfach' })).toBeVisible();
    await expect(page.locator('#einkauf-smtpPassword')).toHaveValue('');
    await expect(page.locator('#einkauf-imapPassword')).toHaveValue('');
    await expect(page.getByText('Passwort ist gespeichert. Leer lassen, damit es unverändert bleibt.')).toHaveCount(2);
    await expect(page.getByText('SMTP-Port')).toBeVisible();
    await expect(page.getByText('IMAP-Port')).toBeVisible();

    await page.getByRole('button', { name: 'Einkauf-Einstellungen speichern' }).click();
    await expect.poll(() => actions.length).toBe(1);
    expect(actions[0].path).toBe('/api/settings/einkauf-mail');
    expect(JSON.stringify(actions[0].body)).not.toContain('passwordSet');
    expect(JSON.stringify(actions[0].body)).not.toContain('password');
    expect(actions.some(action => action.path.endsWith('/testmail'))).toBe(false);

    await page.getByRole('button', { name: 'Verbindung prüfen' }).click();
    await expect(page.getByRole('status').filter({ hasText: 'SMTP fehlgeschlagen, IMAP verbunden' }).last()).toBeVisible();
    expect(actions.some(action => action.path.endsWith('/testmail'))).toBe(false);

    const testmailButton = page.getByRole('button', { name: 'Testmail senden' });
    await expect(testmailButton).toBeDisabled();
    await page.getByLabel('Testempfänger').fill('test@example.invalid');
    await expect(testmailButton).toBeDisabled();
    await page.getByLabel('Ich bestätige, dass die Testmail an diese Adresse gesendet werden darf.').check();
    await expect(testmailButton).toBeEnabled();
    await testmailButton.click();
    await expect(page.getByRole('status').filter({ hasText: 'Testmail wurde angenommen.' }).last()).toBeVisible();
    expect(actions.filter(action => action.path.endsWith('/testmail'))).toHaveLength(1);
    expect(actions.at(-1)?.body).toEqual({ empfaenger: 'test@example.invalid', empfaengerBestaetigt: true });
    expect(responses).toEqual([]);

    await page.getByRole('button', { name: 'Einkauf-Einstellungen speichern' }).scrollIntoViewIfNeeded();
    await designPruefung(page, testInfo, 'einkauf-mailkonto', {
        primaerAktion: page.getByRole('button', { name: 'Einkauf-Einstellungen speichern' }),
    });
});

test('zeigt Berechtigungen nur beim gespeicherten Benutzerprofil und speichert einzelne Rechte', async ({ page }, testInfo) => {
    const requests: { method: string; path: string; body?: unknown }[] = [];
    await page.route('**/api/**', async route => {
        const request = route.request();
        const url = new URL(request.url());
        const json = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
        if (url.pathname === '/api/auth/me') return json(admin);
        if (url.pathname === '/api/frontend-users' && request.method() === 'GET') return json([{
            id: 42, displayName: 'Max Mustermann', username: 'max', shortCode: null,
            roles: ['USER'], active: true, defaultSignature: null, mitarbeiter: null, emailAbsender: null,
        }]);
        if (['/api/email/signatures', '/api/mitarbeiter', '/api/firma/email-absender'].includes(url.pathname)) return json([]);
        if (url.pathname === '/api/settings/einkauf-berechtigungen/42' && request.method() === 'GET') return json(['LESEN']);
        if (url.pathname === '/api/settings/einkauf-berechtigungen/42' && request.method() === 'PUT') {
            const body = request.postDataJSON();
            requests.push({ method: request.method(), path: url.pathname, body });
            return json((body as { rechte: string[] }).rechte);
        }
        return json({}, 404);
    });
    await page.goto('/benutzer');
    await expect(page.getByRole('main').getByText('Max Mustermann', { exact: true })).toBeVisible();
    expect(requests).toHaveLength(0);
    await page.locator('div.group.flex.items-center.justify-between').filter({ hasText: 'max' }).click();
    await expect(page.getByLabel('Einkauf lesen')).toBeChecked();
    await page.getByLabel('Anfragen senden').check();
    await page.getByRole('button', { name: 'Rechte speichern' }).click();
    await expect(page.getByRole('button', { name: 'Rechte speichern' })).toBeEnabled();
    expect(requests).toEqual([{ method: 'PUT', path: '/api/settings/einkauf-berechtigungen/42', body: { rechte: ['LESEN', 'ANFRAGE_SENDEN'] } }]);
    await page.getByRole('button', { name: 'Rechte speichern' }).scrollIntoViewIfNeeded();
    await designPruefung(page, testInfo, 'einkauf-berechtigungen', {
        primaerAktion: page.getByRole('button', { name: 'Rechte speichern' }),
    });
});
