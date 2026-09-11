import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const RUNDMAIL_EMPFAENGER = [
    'info@schmitt-eisingen.de',
    'mennig.elektro@t-online.de',
    'p.feineis@keller-kiesel.de',
    'IBC.Kiesel@t-online.de',
    'info@siedler-Bedachungen.de',
    'p.geier@geier-malerundstuck.de',
    'fschredinger@krines-online.de',
    'info@tfm-bodensysteme.de',
    'info.gpbau.de@gmail.com',
    'bauschlosserei-kuhn@t-online.de',
    'info@melchior-natursteinhandel.de',
    'p.botzem@holzbotzem.de',
    'philipp@zech-privat.de',
    'karlthomasbach@web.de'
].join(', ');

const MOCK_EMAILS = [
    {
        id: 101,
        type: 'EMAIL',
        direction: 'IN',
        subject: 'BV Zech Hangleiten 1 - Protokoll Nr. 40',
        sender: 'Architekturbüro Schlotz',
        fromAddress: 'info@schlotz-architekten.de',
        recipient: RUNDMAIL_EMPFAENGER,
        body: 'Sehr geehrte Damen und Herren, anbei das Protokoll.',
        htmlBody: '<p>Sehr geehrte Damen und Herren, anbei das Protokoll.</p>',
        sentAt: '2026-09-11T15:52:00',
        isRead: false,
        zuordnungTyp: 'PROJEKT',
        projektName: 'Neubau Hangleiten 1',
        attachments: []
    },
    {
        id: 102,
        type: 'EMAIL',
        direction: 'OUT',
        subject: 'Re: Rückfrage Geländer Maße',
        sender: 'Bauschlosserei Kuhn',
        fromAddress: 'bauschlosserei-kuhn@t-online.de',
        recipient: 'kunde@schlotz-architekten.de',
        body: 'Hier sind die Maße für das Geländer.',
        htmlBody: '<p>Hier sind die Maße für das Geländer.</p>',
        sentAt: '2026-09-11T16:00:00',
        isRead: true,
        zuordnungTyp: 'KEINE',
        kundeName: 'Schlotz Architekten',
        attachments: []
    }
];

const MOCK_STATS = {
    inboxCount: 1,
    sentCount: 1,
    trashCount: 0,
    spamCount: 0,
    newsletterCount: 0,
    unassignedCount: 0,
    inquiriesCount: 0,
    projectCount: 1,
    offerCount: 0,
    supplierCount: 0,
    taxAdvisorCount: 0
};

async function stubEmailApi(page: Page) {
    await page.route('**/api/**', async (route) => {
        const url = route.request().url();

        if (url.includes('/api/emails/inbox')) {
            return json(route, MOCK_EMAILS);
        }
        if (url.includes('/api/emails/sent')) {
            return json(route, [MOCK_EMAILS[1]]);
        }
        if (url.includes('/api/emails/stats')) {
            return json(route, MOCK_STATS);
        }
        if (url.includes('/api/emails/from-addresses')) {
            return json(route, ['bauschlosserei-kuhn@t-online.de']);
        }
        if (url.includes('/api/emails/101/thread')) {
            return json(route, {
                rootEmailId: 101,
                focusedEmailId: 101,
                emails: [{
                    id: 101,
                    subject: 'BV Zech Hangleiten 1 - Protokoll Nr. 40',
                    fromAddress: 'info@schlotz-architekten.de',
                    recipient: RUNDMAIL_EMPFAENGER,
                    sentAt: '2026-09-11T15:52:00',
                    direction: 'IN',
                    snippet: 'Sehr geehrte Damen und Herren, anbei das Protokoll.',
                    htmlBody: '<p>Sehr geehrte Damen und Herren, anbei das Protokoll.</p>',
                    attachments: []
                }]
            });
        }
        if (url.includes('/api/emails/102/thread')) {
            return json(route, {
                rootEmailId: 102,
                focusedEmailId: 102,
                emails: [{
                    id: 102,
                    subject: 'Re: Rückfrage Geländer Maße',
                    fromAddress: 'bauschlosserei-kuhn@t-online.de',
                    recipient: 'kunde@schlotz-architekten.de',
                    sentAt: '2026-09-11T16:00:00',
                    direction: 'OUT',
                    snippet: 'Hier sind die Maße für das Geländer.',
                    htmlBody: '<p>Hier sind die Maße für das Geländer.</p>',
                    attachments: []
                }]
            });
        }
        if (url.includes('/api/emails/101')) {
            return json(route, MOCK_EMAILS[0]);
        }
        if (url.includes('/api/emails/102')) {
            return json(route, MOCK_EMAILS[1]);
        }

        // Fallback fuer sonstige Endpunkte (User, Benachrichtigungen etc.)
        return json(route, []);
    });
}

test.describe('E-Mail-Center: Resizable Layout, Rundmail-Dropdown & Antwort-Logik', () => {
    test('1. Spaltenbreiten lassen sich verschieben und die Ordnerleiste einklappen', async ({ page }) => {
        await stubEmailApi(page);
        await page.goto('/emails/inbox');

        // Warten bis E-Mail-Center geladen ist
        await expect(page.getByRole('heading', { name: 'E-Mail Center' })).toBeVisible();
        await expect(page.getByText('BV Zech Hangleiten 1 - Protokoll Nr. 40')).toBeVisible();

        // Splitter 1 und Splitter 2 müssen vorhanden sein
        const sidebarSplitter = page.getByTitle('Ordnerspalte verschieben');
        const listSplitter = page.getByTitle('Listenbreite verschieben');
        await expect(sidebarSplitter).toBeVisible();
        await expect(listSplitter).toBeVisible();

        // Splitter 1 ziehen (Drag-Interaktion pruefen)
        const box = await sidebarSplitter.boundingBox();
        expect(box).not.toBeNull();
        if (box) {
            await page.mouse.move(box.x + box.width / 2, box.y + 50);
            await page.mouse.down();
            await page.mouse.move(box.x + 60, box.y + 50, { steps: 5 });
            await page.mouse.up();

            const storedWidth = await page.evaluate(() => localStorage.getItem('email_center_sidebar_width'));
            expect(storedWidth).not.toBeNull();
            expect(Number(storedWidth)).toBeGreaterThan(240);
        }

        // Sidebar einklappen
        const collapseBtn = page.getByTitle('Ordnerleiste einklappen');
        await collapseBtn.click();

        // Nach Einklappen: Ausklappen-Button sichtbar und im localStorage gemerkt
        const expandBtn = page.getByTitle('Ordnerleiste ausklappen');
        await expect(expandBtn).toBeVisible();

        const isCollapsedInStorage = await page.evaluate(() => localStorage.getItem('email_center_sidebar_collapsed'));
        expect(isCollapsedInStorage).toBe('true');

        // Wieder ausklappen
        await expandBtn.click();
        await expect(page.getByTitle('Ordnerleiste einklappen')).toBeVisible();
    });

    test('2. Rundmail zeigt kompaktes Dropdown (+ 12 weitere) statt ueberlangem Text', async ({ page }) => {
        await stubEmailApi(page);
        await page.goto('/emails/inbox');

        // Rundmail anklicken
        await page.getByText('BV Zech Hangleiten 1 - Protokoll Nr. 40').click();

        // Erst die ersten beiden Empfaenger und das "+12 weitere"-Badge muessen sichtbar sein
        await expect(page.getByText('info@schmitt-eisingen.de').first()).toBeVisible();
        await expect(page.getByText('mennig.elektro@t-online.de').first()).toBeVisible();
        const badge = page.getByText('+12 weitere').first();
        await expect(badge).toBeVisible();

        // Klick auf das Badge oeffnet das Popover
        await badge.click();

        // Alle 14 Empfaenger muessen im Popover stehen
        await expect(page.getByText('Alle Empfänger (14)').first()).toBeVisible();
        await expect(page.getByText('karlthomasbach@web.de').first()).toBeVisible();
        await expect(page.getByTitle('Alle E-Mail-Adressen in die Zwischenablage kopieren').first()).toBeVisible();

        // Schließen per Escape-Taste
        await page.keyboard.press('Escape');
        await expect(page.getByText('Alle Empfänger (14)')).not.toBeVisible();
    });

    test('3. Antworten auf Ausgangsmail setzt den Kunden als Empfaenger und nicht die eigene Firmenadresse', async ({ page }) => {
        await stubEmailApi(page);
        await page.goto('/emails/inbox');

        // Ausgangsmail / Mail mit Rueckfrage Geländer anklicken
        await page.getByText('Re: Rückfrage Geländer Maße').click();
        await expect(page.getByText('Hier sind die Maße für das Geländer.')).toBeVisible();

        // Auf "Antworten" klicken
        const antwortenButtons = page.getByRole('button', { name: 'Antworten' });
        await antwortenButtons.first().click();

        // Compose-Formular oeffnet sich: Empfaenger muss der Kunde sein!
        await expect(page.getByText('E-Mail senden')).toBeVisible();
        const recipientInput = page.locator('input[value*="kunde@schlotz-architekten.de"]');
        await expect(recipientInput).toBeVisible();

        // Eigene Firmenadresse darf NICHT im Empfaenger stehen
        const ownRecipientInput = page.locator('input[value*="bauschlosserei-kuhn@t-online.de"]');
        await expect(ownRecipientInput).not.toBeVisible();
    });
});
