import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * E-Mail-Center: Zugeordnete Mails zeigen, zu welchem Projekt / welcher Anfrage /
 * welchem Lieferanten sie gehören, und springen mit einem Klick dorthin.
 * Nur Dummy-Daten, /api komplett gestubbt.
 */
const basis = {
    type: 'EMAIL', direction: 'IN', recipient: 'info@musterbetrieb.example',
    isRead: true, attachments: [], projektAuftragsnummer: null,
};

const EMAILS = [
    {
        ...basis, id: 801, subject: 'Re: Garagentor', isRead: false,
        fromAddress: 'Max Mustermann <max.mustermann@example.com>',
        body: 'Passt der Termin am Mittwoch?', htmlBody: '<p>Passt der Termin am Mittwoch?</p>',
        sentAt: '2026-10-08T09:30:00',
        zuordnungTyp: 'ANFRAGE', anfrageId: 12, anfrageName: 'Garagentor Mustermann',
    },
    {
        ...basis, id: 802, subject: 'Aufmaß Vordach',
        fromAddress: 'Erika Musterfrau <erika.musterfrau@example.com>',
        body: 'Anbei das Aufmaß.', htmlBody: '<p>Anbei das Aufmaß.</p>',
        sentAt: '2026-10-08T08:15:00',
        zuordnungTyp: 'PROJEKT', projektId: 41, projektAuftragsnummer: '2026-041',
        projektName: 'Garagentor und Vordach mit Edelstahlgeländer Familie Mustermann, Musterstraße 12',
    },
    {
        ...basis, id: 803, subject: 'Lieferschein 4711',
        fromAddress: 'Musterhandel GmbH <verkauf@musterhandel.example>',
        body: 'Ihre Ware ist unterwegs.', htmlBody: '<p>Ihre Ware ist unterwegs.</p>',
        sentAt: '2026-10-07T14:00:00',
        zuordnungTyp: 'LIEFERANT', lieferantId: 7, lieferantName: 'Musterhandel GmbH',
    },
    {
        ...basis, id: 804, subject: 'Unterlagen Quartal 3',
        fromAddress: 'Kanzlei Muster <kanzlei@steuer-muster.example>',
        body: 'Bitte Belege schicken.', htmlBody: '<p>Bitte Belege schicken.</p>',
        sentAt: '2026-10-07T10:00:00',
        zuordnungTyp: 'STEUERBERATER',
    },
];

async function vorbereiten(page: Page, start: string) {
    await page.route('**/api/**', async route => {
        const pfad = new URL(route.request().url()).pathname;
        let body: unknown = [];
        if (pfad.endsWith('/emails/stats')) body = { inboxCount: EMAILS.length };
        else if (pfad.endsWith('/absender-postfaecher')) body = [{ id: 1, emailAdresse: 'info@musterbetrieb.example', anzeigename: null, eigenes: false, hauptpostfach: true }];
        else if (/\/emails\/inbox$/.test(pfad)) body = EMAILS;
        else {
            const treffer = pfad.match(/\/emails\/(\d+)(\/thread)?$/);
            const email = treffer && EMAILS.find(e => e.id === Number(treffer[1]));
            if (email) body = treffer![2] ? { rootEmailId: email.id, focusedEmailId: email.id, emails: [email] } : email;
        }
        await route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    });
    await page.goto(start);
}

test('Liste zeigt die Zuordnung als Link statt als Enum-Text', async ({ page }, info) => {
    await vorbereiten(page, '/emails/inbox');
    const anfrage = page.getByRole('link', { name: 'Anfrage · Garagentor Mustermann öffnen' });
    await expect(anfrage).toBeVisible();
    await expect(anfrage).toHaveAttribute('href', '/anfragen?anfrageId=12');
    await expect(page.getByRole('link', { name: 'Lieferant · Musterhandel GmbH öffnen' }))
        .toHaveAttribute('href', '/lieferanten?lieferantId=7');

    // Langer Projektname wird im Chip gekürzt, steht aber vollständig im Tooltip.
    const projekt = page.getByRole('link', { name: /^Projekt 2026-041 · Garagentor und Vordach/ });
    await expect(projekt).toHaveAttribute('href', '/projekte?projektId=41');
    await expect(projekt).toHaveAttribute('title', `Projekt 2026-041 · ${EMAILS[1].projektName} öffnen`);
    expect((await projekt.boundingBox())!.width).toBeLessThanOrEqual(16 * 16 + 1);

    // Steuerberater: Klartext, kein Link.
    const steuerberater = page.getByTestId('email-zuordnung').filter({ hasText: 'Steuerberater' });
    await expect(steuerberater).toBeVisible();
    await expect(steuerberater.getByRole('link')).toHaveCount(0);

    for (const roh of ['ANFRAGE', 'PROJEKT', 'LIEFERANT', 'STEUERBERATER']) {
        await expect(page.getByText(roh, { exact: true })).toHaveCount(0);
    }

    // Fokus-Ring sichtbar und nicht abgeschnitten.
    await anfrage.focus();
    await designPruefung(page, info, 'email-zuordnung-liste', { primaerAktion: anfrage });
});

test('Klick auf den Chip springt in die Anfrage, ohne die Mail zu öffnen', async ({ page }) => {
    await vorbereiten(page, '/emails/inbox');
    await page.getByRole('link', { name: 'Anfrage · Garagentor Mustermann öffnen' }).click();
    await expect(page).toHaveURL(/\/anfragen\?anfrageId=12$/);
});

test('Ziehen am Chip startet kein Verschieben der Mail', async ({ page }) => {
    await vorbereiten(page, '/emails/inbox');
    const chip = page.getByRole('link', { name: 'Lieferant · Musterhandel GmbH öffnen' });
    const box = (await chip.boundingBox())!;
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
    await page.mouse.down();
    await page.mouse.move(box.x + box.width / 2 + 40, box.y + box.height / 2 + 30, { steps: 5 });
    // dnd-kit setzt die gezogene Zeile auf opacity-40 – das darf hier nicht passieren.
    await expect(page.locator('[aria-roledescription="draggable"].opacity-40')).toHaveCount(0);
    await page.mouse.up();
    await expect(page).toHaveURL(/\/emails\/inbox$/);
});

test('Strg/Cmd-Klick öffnet die Lieferantenakte in einem neuen Tab', async ({ page, context }) => {
    await vorbereiten(page, '/emails/inbox');
    const neuerTab = context.waitForEvent('page');
    await page.getByRole('link', { name: 'Lieferant · Musterhandel GmbH öffnen' }).click({ modifiers: ['ControlOrMeta'] });
    const tab = await neuerTab;
    await tab.waitForLoadState('domcontentloaded');
    expect(tab.url()).toMatch(/\/lieferanten\?lieferantId=7$/);
    await expect(page).toHaveURL(/\/emails\/inbox$/);
});

test('Mail-Kopf zeigt „Gehört zu:“ und springt ins Projekt', async ({ page }, info) => {
    await vorbereiten(page, '/emails/inbox/802');
    const kopf = page.getByTestId('email-detail-header');
    await expect(kopf.getByRole('heading', { name: 'Aufmaß Vordach' })).toBeVisible();
    const zeile = kopf.getByTestId('email-gehoert-zu');
    await expect(zeile).toContainText('Gehört zu:');
    const link = zeile.getByRole('link', { name: /^Projekt 2026-041 · Garagentor und Vordach/ });
    await expect(link).toBeInViewport();
    // Der Chip bleibt in der Kopfbreite – nichts ragt in die Nachbarspalte.
    const kopfBox = (await kopf.boundingBox())!;
    const linkBox = (await link.boundingBox())!;
    expect(linkBox.x + linkBox.width).toBeLessThanOrEqual(kopfBox.x + kopfBox.width);
    expect(kopfBox.height).toBeLessThan(175);

    await designPruefung(page, info, 'email-zuordnung-kopf', { primaerAktion: link });

    await link.click();
    await expect(page).toHaveURL(/\/projekte\?projektId=41$/);
});
