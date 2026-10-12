import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';

/*
 * Fehlerbild: Beim Öffnen eines Verlaufs holte scrollIntoView die fokussierte
 * Nachricht in die Mitte und schob dabei auch die overflow-hidden-Hüllen des
 * Layouts mit. Das ganze E-Mail-Center rutschte unter die Menüleiste, unten
 * blieb eine leere Fläche, die sich nicht zurückscrollen ließ.
 *
 * Auftreten nur „manchmal“: Die Hüllen brauchen dafür einen Überhang. Den
 * lieferten bei mehreren Postfächern die unsichtbaren Vorlese-Texte der
 * Postfach-Schilder in einer langen Liste – deshalb die Testdaten unten.
 * Beide Ursachen sind einzeln abgesichert: kein Überhang mehr, und selbst mit
 * Überhang scrollt beim Öffnen nur der Verlauf.
 */

const INFO = { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' };
const MAX = { id: 7, emailAdresse: 'max@musterbetrieb.example', anzeigename: 'Max Mustermann' };

const langerText = (titel: string) => `<p>${titel}</p>` + Array.from({ length: 18 },
    (_, i) => `<p>Zeile ${i + 1}: Bitte um ein Angebot für die Terrassen-Verkleidung.</p>`).join('');

const basis = {
    type: 'EMAIL', subject: 'Re: Anfrage Terrasse', sender: 'Max Mustermann',
    fromAddress: '"Max Mustermann" <test@example.com>', recipient: 'handwerk@example.com',
    isRead: true, attachments: [], zuordnungTyp: 'KEINE', postfaecher: [INFO],
};
const nachrichten = [951, 952, 953, 954, 955].map((id, i) => ({
    ...basis, id, direction: 'IN', sentAt: `2026-09-1${i + 3}T09:00:00`,
    htmlBody: langerText(`Nachricht ${i + 1}`), snippet: `Nachricht ${i + 1}`,
}));

async function stubVerlauf(page: Page, fokusId: number) {
    const ausgewaehlt = nachrichten.find(n => n.id === fokusId)!;
    const weitereMails = Array.from({ length: 45 }, (_, i) => ({
        ...basis, id: 1000 + i, direction: 'IN', subject: `Anfrage ${i + 1}`,
        sentAt: '2026-09-10T09:00:00', htmlBody: '<p>Kurze Anfrage</p>', snippet: 'Kurze Anfrage',
    }));
    await page.route('**/api/**', route => {
        const pathname = new URL(route.request().url()).pathname;
        let body: unknown = [];
        if (pathname === '/api/emails/inbox') body = [ausgewaehlt, ...weitereMails];
        else if (pathname === `/api/emails/${fokusId}/thread`) body = { rootEmailId: 951, focusedEmailId: fokusId, emails: nachrichten };
        else if (pathname === `/api/emails/${fokusId}`) body = ausgewaehlt;
        else if (pathname === '/api/emails/stats') body = { inboxCount: 1, sentCount: 0, trashCount: 0, spamCount: 0, unassignedCount: 1 };
        else if (pathname === '/api/emails/absender-postfaecher') body = [
            { ...MAX, eigenes: true, hauptpostfach: false, waehlbar: true },
            { ...INFO, eigenes: false, hauptpostfach: true, waehlbar: true },
        ];
        return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    });
}

/** Vorfahren des Verlaufs, die verschoben wurden – scrollen darf nur der Verlauf selbst. */
function verschobeneVorfahren(page: Page) {
    return page.getByTestId('email-verlauf').evaluate(verlauf => {
        const verschoben: string[] = [];
        for (let el = verlauf.parentElement; el; el = el.parentElement) {
            if (el.scrollTop !== 0) verschoben.push(`<${el.tagName.toLowerCase()} class="${el.className}"> um ${el.scrollTop}px`);
        }
        if (window.scrollY !== 0) verschoben.push(`Fenster um ${window.scrollY}px`);
        return verschoben;
    });
}

test('Postfach-Schilder in einer langen Liste geben dem E-Mail-Center keinen Überhang', async ({ page }) => {
    await stubVerlauf(page, 955);
    await page.goto('/emails/inbox');
    await expect(page.getByTestId('postfach-schild')).toHaveCount(46);
    // Früher hingen die sr-only-Texte der Schilder an <main> und ragten bis zu 4782px heraus.
    expect(await page.locator('main').evaluate(main => main.scrollHeight - main.clientHeight)).toBe(0);
});

for (const [fall, fokusId] of [['mittlere', 953], ['letzte', 955]] as const) {
    test(`Verlauf öffnen (${fall} Nachricht gewählt) verschiebt das E-Mail-Center nicht`, async ({ page }) => {
        await stubVerlauf(page, fokusId);
        // Ohne Animation springt der Verlauf sofort an seine Stelle – kein Warten aufs weiche Scrollen.
        await page.emulateMedia({ reducedMotion: 'reduce' });
        await page.goto('/emails/inbox');
        const lesebereich = page.getByRole('region', { name: 'E-Mail lesen und schreiben' });
        await expect(lesebereich).toBeVisible();
        const obenVorher = (await lesebereich.boundingBox())!.y;
        // Auch wenn das Layout künftig wieder einen Überhang bekommt, darf nur der Verlauf scrollen.
        await page.locator('main').evaluate(main => {
            const ueberhang = document.createElement('div');
            ueberhang.style.cssText = 'position:absolute;top:0;left:0;width:1px;height:5000px;pointer-events:none';
            main.appendChild(ueberhang);
        });

        await page.getByText('Re: Anfrage Terrasse', { exact: true }).first().click();
        const verlauf = page.getByTestId('email-verlauf');
        await expect(page.getByRole('button', { name: 'Nachricht von Max Mustermann einklappen' })).toBeVisible();
        await expect.poll(() => verlauf.evaluate(el => el.scrollTop)).toBeGreaterThan(0);

        expect(await verschobeneVorfahren(page)).toEqual([]);
        expect((await lesebereich.boundingBox())!.y).toBe(obenVorher);
        await expect(page.getByRole('heading', { name: 'E-Mail Center' })).toBeInViewport();
    });
}
