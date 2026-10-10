import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';

/**
 * Verläufe im E-Mail-Center: eine Zeile pro Verlauf, eingeklappter Outlook-Verlauf
 * und "Allen antworten" mit CC. Backend gestubbt, nur Dummy-Daten (DSGVO).
 */

// Struktur wie neues Outlook/OWA: Trennlinie + Kopf-Div ohne Kennung, Verlauf als lose Geschwister.
const owaHtml = '<div>Hallo Herr Mustermann,</div><div>erste grobe Zeitschiene: Aufbau Anfang November.</div>'
    + '<div id="Signature"><p>Mit freundlichen Grüßen</p><p>Erika Musterfrau</p></div>'
    + '<hr style="display: inline-block; width: 98%;">'
    + '<div><b>Von:</b> Musterverwaltung &lt;info@example.org&gt;<br><b>Gesendet:</b> Dienstag, 23. Juni 2026 17:23<br>'
    + '<b>An:</b> handwerk@example.com<br><b>Betreff:</b> AW: Angebot</div>'
    + '<div>anbei der Auftrag für die Musterstraße 2.</div><div id="x_Signature"><p>Alte Signatur</p></div>'
    + '<hr style="display: inline-block; width: 98%;"><div id="x_divRplyFwdMsg"><b>Von:</b> handwerk@example.com<br>'
    + '<b>Gesendet:</b> Montag, 12. Januar 2026 10:49<br><b>An:</b> info@example.org<br><b>Betreff:</b> Angebot</div>'
    + '<div>Sehr geehrte Frau Musterfrau, im Anhang das Angebot.</div>';

const anfrage = {
    id: 16, type: 'EMAIL', direction: 'IN', subject: 'Angebot Musterstraße 2',
    fromAddress: 'info@example.org', recipient: 'handwerk@example.com',
    body: 'Bitte um Angebot.', sentAt: '2026-06-23T17:23:00', isRead: true,
    attachments: [], zuordnungTyp: 'KEINE', threadRootId: 16, replyCount: 2,
};
const zeitschiene = {
    ...anfrage, id: 17, subject: 'AW: Angebot Musterstraße 2', parentEmailId: 99,
    recipient: 'handwerk@example.com, max@example.com', cc: 'architekt@example.net',
    body: 'Hallo Herr Mustermann, erste grobe Zeitschiene: Aufbau Anfang November.',
    sentAt: '2026-07-15T12:40:00', isRead: false,
};

interface Mitschrift { antworten: string[] }

async function oeffneVerlauf(page: Page): Promise<Mitschrift> {
    const mitschrift: Mitschrift = { antworten: [] };
    await page.route('**/api/**', route => {
        const request = route.request();
        const pathname = new URL(request.url()).pathname;
        if (request.method() === 'POST' && pathname === '/api/emails/17/reply') {
            mitschrift.antworten.push(request.postData() ?? '');
            return route.fulfill({ contentType: 'application/json', body: JSON.stringify({ ...zeitschiene, id: 100 }) });
        }
        let body: unknown = [];
        if (pathname === '/api/emails/inbox') body = [zeitschiene, anfrage];
        else if (pathname === '/api/emails/17') body = { ...zeitschiene, htmlBody: owaHtml };
        else if (pathname === '/api/emails/16') body = anfrage;
        else if (pathname === '/api/emails/17/thread' || pathname === '/api/emails/16/thread') body = {
            rootEmailId: 16, focusedEmailId: 17,
            emails: [
                { ...anfrage, htmlBody: '<p>Bitte um Angebot.</p>', snippet: 'Bitte um Angebot.' },
                { ...zeitschiene, htmlBody: owaHtml, snippet: 'Hallo Herr Mustermann' },
            ],
        };
        else if (pathname === '/api/emails/stats') body = { inboxCount: 2, sentCount: 0, trashCount: 0, spamCount: 0, unassignedCount: 0 };
        else if (pathname === '/api/emails/absender-postfaecher') body = [{ id: 1, emailAdresse: 'handwerk@example.com', anzeigename: null, eigenes: false, hauptpostfach: true }];
        else if (request.method() !== 'GET') body = {};
        return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    });
    await page.goto('/emails/inbox');
    return mitschrift;
}

test('ein Verlauf steht als eine Zeile in der Liste, der Outlook-Verlauf ist eingeklappt', async ({ page }, testInfo) => {
    await oeffneVerlauf(page);
    const liste = page.getByText('AW: Angebot Musterstraße 2', { exact: true });
    await expect(liste).toBeVisible();
    await expect(page.getByText('Angebot Musterstraße 2', { exact: true })).toHaveCount(0);
    await expect(page.getByText('3 Nachrichten')).toHaveCount(1);

    await liste.click();
    const frame = page.frameLocator('iframe[title="Email Content"]').last();
    await expect(frame.getByText('erste grobe Zeitschiene', { exact: false })).toBeVisible();
    await expect(frame.getByText('Erika Musterfrau')).toBeVisible();
    await expect(frame.getByText('anbei der Auftrag', { exact: false })).toBeHidden();
    await expect(frame.getByText('im Anhang das Angebot', { exact: false })).toBeHidden();
    await page.screenshot({ path: testInfo.outputPath('verlauf-eingeklappt.png'), animations: 'disabled' });

    await frame.getByRole('button', { name: 'Zitierten Verlauf anzeigen' }).click();
    await expect(frame.getByText('anbei der Auftrag', { exact: false })).toBeVisible();
    await expect(frame.getByText('im Anhang das Angebot', { exact: false })).toBeVisible();
    await frame.getByRole('button', { name: 'Zitierten Verlauf ausblenden' }).click();
    await expect(frame.getByText('anbei der Auftrag', { exact: false })).toBeHidden();
});

test('"Allen antworten" belegt An und CC vor und schickt beide mit', async ({ page }, testInfo) => {
    const mitschrift = await oeffneVerlauf(page);
    await page.getByText('AW: Angebot Musterstraße 2', { exact: true }).click();
    const kopf = page.getByTestId('email-detail-header');
    await kopf.getByRole('button', { name: 'Allen antworten' }).click();

    await expect(page.getByPlaceholder('Name, Firma oder E-Mail eingeben')).toHaveValue('info@example.org, max@example.com');
    await expect(page.getByPlaceholder('CC Empfänger hinzufügen')).toHaveValue('architekt@example.net');
    await expect(page.getByPlaceholder('CC Empfänger hinzufügen')).toHaveCount(1);
    await page.screenshot({ path: testInfo.outputPath('allen-antworten.png'), animations: 'disabled' });

    await page.getByRole('button', { name: 'E-Mail senden' }).click();
    await expect.poll(() => mitschrift.antworten.length).toBe(1);
    expect(mitschrift.antworten[0]).toContain('"recipients":["info@example.org, max@example.com"]');
    expect(mitschrift.antworten[0]).toContain('"cc":["architekt@example.net"]');
});
