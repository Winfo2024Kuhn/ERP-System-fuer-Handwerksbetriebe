import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * E-Mail-Center: Kopfzeile „… ungelesen“ mit „Alle gelesen“ und dem (i) „Gelesen gilt nur für
 * dich“. „Gelesen“ gilt pro Person – liest der Chef eine Mail, bleibt sie für die Kollegen
 * ungelesen. Nur Dummy-Daten, /api komplett gestubbt.
 */
const basis = {
    type: 'EMAIL', direction: 'IN', recipient: 'info@musterbetrieb.example',
    attachments: [], zuordnungTyp: 'KEINE',
};

const EMAILS = [
    {
        ...basis, id: 901, subject: 'Anfrage Carport', isRead: false,
        fromAddress: 'Max Mustermann <max.mustermann@example.com>',
        body: 'Was kostet ein Carport?', htmlBody: '<p>Was kostet ein Carport?</p>',
        sentAt: '2026-10-12T09:00:00',
    },
    {
        ...basis, id: 902, subject: 'Anfrage Zaun', isRead: false,
        fromAddress: 'Erika Musterfrau <erika.musterfrau@example.com>',
        body: 'Bitte um ein Angebot für einen Zaun.', htmlBody: '<p>Bitte um ein Angebot für einen Zaun.</p>',
        sentAt: '2026-10-12T08:30:00',
    },
    {
        ...basis, id: 903, subject: 'Lieferschein 4711', isRead: true,
        fromAddress: 'Musterhandel GmbH <verkauf@musterhandel.example>',
        body: 'Ihre Ware ist unterwegs.', htmlBody: '<p>Ihre Ware ist unterwegs.</p>',
        sentAt: '2026-10-11T14:00:00',
    },
];

/** Stubbt /api und merkt sich, für welche Ordner „Alle gelesen“ geschickt wurde. */
async function vorbereiten(page: Page): Promise<string[]> {
    const alleGelesenFuer: string[] = [];
    await page.route('**/api/**', async route => {
        const url = new URL(route.request().url());
        const pfad = url.pathname;
        let body: unknown = [];
        if (pfad.endsWith('/emails/mark-all-read')) {
            alleGelesenFuer.push(url.searchParams.get('folder') ?? '');
            body = { updated: 2 };
        } else if (pfad.endsWith('/emails/stats')) body = { inboxCount: 2, inboxTotal: EMAILS.length };
        else if (pfad.endsWith('/absender-postfaecher')) body = [{ id: 1, emailAdresse: 'info@musterbetrieb.example', anzeigename: null, eigenes: false, hauptpostfach: true }];
        else if (/\/emails\/inbox$/.test(pfad)) body = EMAILS;
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    });
    await page.goto('/emails/inbox');
    return alleGelesenFuer;
}

test('Kopfzeile erklärt „gelesen pro Person“, „Alle gelesen“ markiert den Ordner', async ({ page }, info) => {
    const alleGelesenFuer = await vorbereiten(page);
    const kopf = page.getByTestId('email-ungelesen-kopfzeile');
    await expect(kopf.getByText('2 ungelesen')).toBeVisible();
    const alleGelesen = kopf.getByRole('button', { name: 'Alle gelesen' });

    // (i) direkt neben „Alle gelesen“: Popover ganz sichtbar, auch am Rand der Listenspalte.
    await kopf.getByRole('button', { name: 'Mehr zu: Gelesen gilt nur für dich' }).click();
    const popover = page.getByRole('dialog', { name: 'Gelesen gilt nur für dich' });
    await expect(popover).toBeVisible();
    await expect(popover.getByText('Was passiert im Hintergrund?')).toBeVisible();
    await expect(popover.getByText(/ihre Glocke meldet sie weiter/)).toBeVisible();
    await expect(popover).toBeInViewport({ ratio: 1 });
    await designPruefung(page, info, 'email-ungelesen-info', { primaerAktion: alleGelesen });

    await page.keyboard.press('Escape');
    await expect(popover).toBeHidden();

    await alleGelesen.click();
    await expect.poll(() => alleGelesenFuer).toEqual(['inbox']);
    await expect(page.getByText('2 E-Mails als gelesen markiert')).toBeVisible();
    await expect(kopf).toBeHidden();
});
