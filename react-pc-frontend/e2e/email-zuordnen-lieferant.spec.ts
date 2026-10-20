import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * E-Mail-Center: Mail einem Lieferanten zuordnen und die Zuordnung wieder
 * aufheben. /api komplett gestubbt, nur Dummy-Daten.
 */
const basis = {
    type: 'EMAIL', direction: 'IN', recipient: 'info@musterbetrieb.example',
    isRead: true, attachments: [], projektAuftragsnummer: null,
};

interface Mail extends Record<string, unknown> { id: number; subject: string }

function startMails(): Mail[] {
    return [
        {
            ...basis, id: 901, subject: 'Lieferschein 4711',
            fromAddress: 'Verkauf <verkauf@musterstahl.example>',
            body: 'Ihre Ware ist unterwegs.', htmlBody: '<p>Ihre Ware ist unterwegs.</p>',
            sentAt: '2026-10-08T09:30:00', zuordnungTyp: 'KEINE',
        },
        {
            ...basis, id: 902, subject: 'Aufmaß Vordach',
            fromAddress: 'Max Mustermann <max.mustermann@example.com>',
            body: 'Anbei das Aufmaß.', htmlBody: '<p>Anbei das Aufmaß.</p>',
            sentAt: '2026-10-08T08:15:00',
            zuordnungTyp: 'PROJEKT', projektId: 41, projektAuftragsnummer: '2026-041', projektName: 'Vordach Mustermann',
        },
    ];
}

async function vorbereiten(page: Page, start: string) {
    const mails = startMails();
    const aufrufe: string[] = [];
    await page.route('**/api/**', async route => {
        const request = route.request();
        const url = new URL(request.url());
        const pfad = url.pathname;
        const json = (body: unknown) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
        if (request.method() === 'POST') aufrufe.push(pfad);

        const zuordnen = pfad.match(/^\/api\/emails\/(\d+)\/assign\/lieferant\/(\d+)$/);
        if (zuordnen && request.method() === 'POST') {
            const mail = mails.find(m => m.id === Number(zuordnen[1]))!;
            Object.assign(mail, { zuordnungTyp: 'LIEFERANT', lieferantId: Number(zuordnen[2]), lieferantName: 'Musterstahl GmbH' });
            return json(mail);
        }
        const aufheben = pfad.match(/^\/api\/emails\/(\d+)\/unassign$/);
        if (aufheben && request.method() === 'POST') {
            const mail = mails.find(m => m.id === Number(aufheben[1]))!;
            Object.assign(mail, { zuordnungTyp: 'KEINE', projektId: null, projektName: null, projektAuftragsnummer: null });
            return json(mail);
        }
        if (pfad === '/api/emails/stats') return json({ inboxCount: mails.length });
        if (pfad === '/api/emails/absender-postfaecher') return json([{ id: 1, emailAdresse: 'info@musterbetrieb.example', anzeigename: null, eigenes: false, hauptpostfach: true }]);
        if (pfad === '/api/emails/inbox') return json(mails);
        if (pfad === '/api/lieferanten' && url.searchParams.get('q')) {
            return json({ lieferanten: [{ id: 33, lieferantenname: 'Musterstahl GmbH', ort: 'Musterstadt' }], gesamt: 1 });
        }
        if (/^\/api\/emails\/\d+\/possible-assignments$/.test(pfad)) return json({ projekte: [], anfragen: [] });
        const einzeln = pfad.match(/^\/api\/emails\/(\d+)(\/thread)?$/);
        const mail = einzeln && mails.find(m => m.id === Number(einzeln[1]));
        if (mail) return json(einzeln![2] ? { rootEmailId: mail.id, focusedEmailId: mail.id, emails: [mail] } : mail);
        return json([]);
    });
    await page.goto(start);
    return aufrufe;
}

async function zuordnenOeffnen(page: Page) {
    await page.getByRole('button', { name: 'Weitere E-Mail-Aktionen' }).click();
    await page.getByRole('menuitem', { name: 'Zuordnen' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading', { name: 'E-Mail zuordnen' })).toBeVisible();
    return dialog;
}

test('Mail einem Lieferanten zuordnen', async ({ page }, info) => {
    const aufrufe = await vorbereiten(page, '/emails/inbox/901');
    await expect(page.getByTestId('email-detail-header').getByRole('heading', { name: 'Lieferschein 4711' })).toBeVisible();

    const dialog = await zuordnenOeffnen(page);
    await expect(dialog.getByRole('button', { name: 'Zuordnung aufheben' })).toHaveCount(0);
    await dialog.getByRole('button', { name: 'Lieferant', exact: true }).click();
    await dialog.getByPlaceholder('Lieferant suchen...').fill('muster');
    const treffer = dialog.getByRole('button', { name: /Musterstahl GmbH/ });
    await expect(treffer).toBeVisible();
    await designPruefung(page, info, 'email-zuordnen-lieferant', { strengePruefungen: false });

    const antwort = page.waitForResponse(r => r.url().endsWith('/api/emails/901/assign/lieferant/33'));
    await treffer.click();
    await antwort;
    await expect(dialog).toBeHidden();
    expect(aufrufe).toContain('/api/emails/901/assign/lieferant/33');
});

test('Zuordnung einer Mail aufheben', async ({ page }, info) => {
    const aufrufe = await vorbereiten(page, '/emails/inbox/902');
    await expect(page.getByTestId('email-detail-header').getByRole('heading', { name: 'Aufmaß Vordach' })).toBeVisible();

    const dialog = await zuordnenOeffnen(page);
    await expect(dialog.getByText('Projekt 2026-041 · Vordach Mustermann')).toBeVisible();
    const aufhebenKnopf = dialog.getByRole('button', { name: 'Zuordnung aufheben' });
    await designPruefung(page, info, 'email-zuordnung-aufheben', { strengePruefungen: false, primaerAktion: aufhebenKnopf });

    const antwort = page.waitForResponse(r => r.url().endsWith('/api/emails/902/unassign'));
    await aufhebenKnopf.click();
    await antwort;
    await expect(dialog).toBeHidden();
    await expect(page.getByText('Zuordnung wurde aufgehoben.')).toBeVisible();
    expect(aufrufe).toContain('/api/emails/902/unassign');
    // Der Kopf zeigt die Projektzuordnung nicht mehr
    await expect(page.getByTestId('email-detail-header').getByTestId('email-gehoert-zu')).toHaveCount(0);
});
