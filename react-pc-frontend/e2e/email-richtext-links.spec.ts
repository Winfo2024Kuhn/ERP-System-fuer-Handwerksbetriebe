import type { Locator, Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { EINGANG, json, stubbeEmailCenter } from './hilfen/postfaecher';

/**
 * Mail-Textfeld mit Formatierungsleiste und Links mit eigenem Text (z. B. OneDrive-
 * Freigabe hinter „Hier klicken“), dazu automatisch verlinkte Adressen im Verlauf
 * und beim Versand. Nur Dummy-Daten (DSGVO), Dummy-Freigabelinks.
 */

const FREIGABE_ALT = 'https://onedrive.example.com/f/c/0123abcd/EaMuster?e=Alt123';
const FREIGABE_NEU = 'https://onedrive.example.com/f/c/0123abcd/EaMuster?e=Neu456';
const PLAN_IM_VERLAUF = 'https://onedrive.example.com/f/c/0123abcd/PlanMuster?e=Plan789';

async function stubbeMitAdresseImVerlauf(page: Page) {
    const stub = await stubbeEmailCenter(page);
    const eingang = {
        ...EINGANG,
        htmlBody: `<p>Können Sie uns ein Angebot machen? Der Plan liegt hier: ${PLAN_IM_VERLAUF}.</p>`,
    };
    // Nach stubbeEmailCenter registriert – gewinnt also für diese Pfade.
    await page.route('**/api/emails/701', route => json(route, eingang));
    await page.route('**/api/emails/701/thread', route => json(route, { rootEmailId: 701, focusedEmailId: 701, emails: [eingang] }));
    return stub;
}

/** Markiert im Feld genau den angegebenen Text (wie mit der Maus). */
async function markiereText(feld: Locator, text: string) {
    await feld.evaluate((element, gesucht) => {
        const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
        for (let knoten = walker.nextNode(); knoten; knoten = walker.nextNode()) {
            const start = knoten.textContent?.indexOf(gesucht) ?? -1;
            if (start < 0) continue;
            const range = document.createRange();
            range.setStart(knoten, start);
            range.setEnd(knoten, start + gesucht.length);
            window.getSelection()?.removeAllRanges();
            window.getSelection()?.addRange(range);
            return;
        }
        throw new Error(`Text nicht gefunden: ${gesucht}`);
    }, text);
}

/** Kopfzeile „Nachricht“: Leiste und KI-Knopf stehen in einer Zeile, nichts bricht um. */
async function erwarteEineZeile(page: Page) {
    const leiste = await page.getByRole('toolbar', { name: 'Formatierung' }).boundingBox();
    const ki = await page.getByRole('button', { name: 'KI-Optimierung' }).boundingBox();
    const label = await page.getByText('Nachricht', { exact: true }).boundingBox();
    expect(leiste && ki && label).toBeTruthy();
    expect(Math.abs(leiste!.y + leiste!.height / 2 - (ki!.y + ki!.height / 2))).toBeLessThan(4);
    // Gleiche Zeile wie „Nachricht“ (im Dialog steht darunter noch ein Hinweis).
    expect(leiste!.y).toBeLessThan(label!.y + label!.height);
    expect(leiste!.y + leiste!.height).toBeGreaterThan(label!.y);
}

test('Neue Mail: Text markieren, Link mit eigenem Text einfügen, ändern und senden', async ({ page }, info) => {
    const stub = await stubbeMitAdresseImVerlauf(page);
    await page.goto('/emails/inbox/701');

    // Verlauf: die nackte Adresse ist ein Link, der in einem neuen Tab öffnet (nicht anklicken).
    const verlaufLink = page.frameLocator('iframe[title="Email Content"]').first().locator(`a[href="${PLAN_IM_VERLAUF}"]`);
    await expect(verlaufLink).toHaveText(PLAN_IM_VERLAUF);
    await expect(verlaufLink).toHaveAttribute('target', '_blank');
    await expect(verlaufLink).toHaveAttribute('rel', 'noopener noreferrer');

    await page.getByRole('button', { name: 'Neue E-Mail' }).click();
    const feld = page.getByRole('textbox', { name: 'Nachricht' });
    await expect(page.getByRole('toolbar', { name: 'Formatierung' })).toBeVisible();
    await erwarteEineZeile(page);

    await feld.click();
    await page.keyboard.type('Die Zeichnungen: bitte hier klicken. Infos unter www.example.com');
    await markiereText(feld, 'hier klicken');
    await page.keyboard.press('Control+k');

    const dialog = page.getByRole('dialog', { name: 'Link einfügen' });
    await expect(dialog.getByRole('textbox', { name: 'Angezeigter Text' })).toHaveValue('hier klicken');
    await expect(dialog.getByRole('textbox', { name: 'Link-Adresse' })).toBeFocused();
    await dialog.getByRole('textbox', { name: 'Angezeigter Text' }).fill('Hier klicken');
    await dialog.getByRole('textbox', { name: 'Link-Adresse' }).fill(FREIGABE_ALT);
    await designPruefung(page, info, 'email-link-dialog', { primaerAktion: dialog.getByRole('button', { name: 'Link einfügen' }) });
    await dialog.getByRole('button', { name: 'Mehr zu: Link-Adresse' }).click();
    await expect(page.getByText(/die Adresse liegt unsichtbar dahinter/)).toBeVisible();
    await designPruefung(page, info, 'email-link-dialog-info');
    await page.keyboard.press('Escape');
    await expect(page.getByText(/die Adresse liegt unsichtbar dahinter/)).toHaveCount(0);
    await expect(dialog).toBeVisible();
    await dialog.getByRole('button', { name: 'Link einfügen' }).click();
    await expect(dialog).toHaveCount(0);

    const link = feld.locator('a');
    await expect(link).toHaveText('Hier klicken');
    await expect(link).toHaveAttribute('href', FREIGABE_ALT);
    // Weiterschreiben landet hinter dem Link, nicht darin.
    await page.keyboard.type(' sofort');
    await expect(link).toHaveText('Hier klicken');
    await expect(feld).toContainText('Hier klicken sofort.');

    // Link anklicken: kleine Leiste am Link, Adresse ändern.
    await link.click();
    const linkLeiste = page.getByRole('toolbar', { name: 'Link' });
    // Lange Adressen stehen gekürzt da, die volle Adresse im Tooltip.
    await expect(linkLeiste).toContainText('onedrive.example.com/f/c/0123abcd/');
    await expect(linkLeiste.getByTitle(FREIGABE_ALT)).toBeVisible();
    await designPruefung(page, info, 'email-link-leiste');
    await linkLeiste.getByRole('button', { name: 'Link ändern' }).click();
    const aendern = page.getByRole('dialog', { name: 'Link ändern' });
    await expect(aendern.getByRole('textbox', { name: 'Angezeigter Text' })).toHaveValue('Hier klicken');
    await aendern.getByRole('textbox', { name: 'Link-Adresse' }).fill(FREIGABE_NEU);
    await aendern.getByRole('button', { name: 'Übernehmen' }).click();
    await expect(aendern).toHaveCount(0);
    await expect(link).toHaveAttribute('href', FREIGABE_NEU);

    // Untermenüs: geöffnet prüfen, Escape schließt und gibt den Fokus zurück.
    for (const menue of ['Schrift', 'Absatz']) {
        const knopf = page.getByRole('button', { name: menue, exact: true });
        await knopf.click();
        await expect(page.getByRole('menu')).toBeVisible();
        await designPruefung(page, info, `email-menue-${menue.toLowerCase()}`);
        await page.keyboard.press('Escape');
        await expect(page.getByRole('menu')).toHaveCount(0);
        await expect(knopf).toBeFocused();
    }

    // Fett über die Leiste auf markiertes Wort.
    await markiereText(feld, 'Zeichnungen');
    await page.getByRole('button', { name: 'Fett' }).click();
    await expect(feld.locator('b')).toHaveText('Zeichnungen');
    await expect(page.getByRole('button', { name: 'Fett' })).toHaveAttribute('aria-pressed', 'true');

    const anFeld = page.getByRole('textbox', { name: 'Empfänger' });
    await anFeld.fill('erika.musterfrau@example.org');
    // Vorschlagsliste schließen – sie liegt sonst über den Feldern darunter.
    await anFeld.press('Escape');
    await page.getByLabel('Betreff').click();
    await page.getByLabel('Betreff').fill('Zeichnungen Balkongeländer');
    await designPruefung(page, info, 'email-formular-mit-link');

    await page.getByRole('button', { name: 'E-Mail senden' }).click();
    await page.getByRole('button', { name: 'Trotzdem senden' }).click();
    await expect.poll(() => stub.gesendet.length).toBe(1);
    const body = String(stub.gesendet[0].dto.body);
    expect(body).toContain(`<a href="${FREIGABE_NEU.replace(/&/g, '&amp;')}">Hier klicken</a>`);
    expect(body).toContain('<a href="https://www.example.com">www.example.com</a>');
    expect(body).toContain('<b>Zeichnungen</b>');
});

const ANFRAGE_ID = 9;
const BETREFF = 'Anfrage Geländer Mustermann';

/** Minimaler Stub für den E-Mail-Reiter einer Anfrage mit einem Verlauf. */
async function stubbeEmailReiter(page: Page) {
    const eingang = {
        id: 7, subject: BETREFF, fromAddress: 'Max Mustermann <max@example.com>', recipient: 'buero@example.com',
        direction: 'IN', sentAt: '2026-11-02T09:15:00', htmlBody: '<p>Guten Tag, bitte um ein Angebot.</p>',
        snippet: 'Guten Tag, bitte um ein Angebot', attachments: [],
    };
    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'max', displayName: 'Max Mustermann', active: true, roles: ['USER'], admin: false, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === `/api/anfragen/${ANFRAGE_ID}`) {
            return json(route, {
                id: ANFRAGE_ID, kundenId: 3, kundenName: 'Max Mustermann', bauvorhaben: 'Geländer Mustermann', kundennummer: 'K-1003',
                anfragesnummer: 'AG-2026/11/00009', betrag: 4200, kundenEmails: ['max@example.com'], anlegedatum: '2026-11-02',
                abgeschlossen: false, emailAnzahl: 1, dokumente: [],
            });
        }
        if (pfad === `/api/emails/reiter/anfrage/${ANFRAGE_ID}`) {
            return json(route, { eintraege: [{ ...eingang, bodyHtml: eingang.htmlBody, nachrichtenAnzahl: 1, mitAnhang: false }], gesamt: 1, seite: 0, seitenGroesse: 25 });
        }
        if (pfad === '/api/emails/7/thread') return json(route, { rootEmailId: 7, focusedEmailId: 7, emails: [eingang] });
        if (pfad.startsWith('/api/emails/drafts')) return json(route, { id: 101 });
        if (pfad === '/api/email/signatures/default') return route.fulfill({ status: 204, body: '' });
        return json(route, []);
    });
}

test('Antwort im E-Mail-Reiter: Link-Dialog liegt über dem Verlauf, Escape schließt nur ihn', async ({ page }, info) => {
    await stubbeEmailReiter(page);
    await page.goto(`/anfragen?anfrageId=${ANFRAGE_ID}&tab=emails`);
    await page.getByRole('button', { name: BETREFF, exact: true }).click();
    await page.getByRole('dialog', { name: BETREFF }).getByRole('button', { name: 'Antworten', exact: true }).first().click();

    const antwort = page.getByRole('dialog', { name: 'Antwort', exact: true });
    const feld = antwort.getByRole('textbox', { name: 'Nachricht' });
    await expect(feld).toBeFocused();
    await expect(antwort.getByRole('toolbar', { name: 'Formatierung' })).toBeVisible();
    await erwarteEineZeile(page);
    await page.keyboard.type('Die Fotos liegen hier.');
    await markiereText(feld, 'hier');

    await page.keyboard.press('Control+k');
    const linkDialog = page.getByRole('dialog', { name: 'Link einfügen' });
    await expect(linkDialog).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(linkDialog).toHaveCount(0);
    await expect(antwort).toBeVisible();
    await expect(feld).toBeFocused();

    await antwort.getByRole('button', { name: 'Link einfügen' }).click();
    await linkDialog.getByRole('textbox', { name: 'Link-Adresse' }).fill(FREIGABE_ALT);
    await designPruefung(page, info, 'emails-tab-antwort-link-dialog', { primaerAktion: linkDialog.getByRole('button', { name: 'Link einfügen' }) });
    await linkDialog.getByRole('button', { name: 'Link einfügen' }).click();
    await expect(linkDialog).toHaveCount(0);
    await expect(antwort).toBeVisible();
    await expect(feld.locator(`a[href="${FREIGABE_ALT}"]`)).toHaveText('hier');

    // Menü im Dialog: Escape schließt nur das Menü, die Antwort bleibt offen.
    const schrift = antwort.getByRole('button', { name: 'Schrift', exact: true });
    await schrift.click();
    await expect(page.getByRole('menu')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('menu')).toHaveCount(0);
    await expect(antwort).toBeVisible();
    await expect(schrift).toBeFocused();

    // Escape am Link schließt nur die Link-Leiste, nicht die Antwort.
    await feld.locator('a').click();
    await expect(page.getByRole('toolbar', { name: 'Link' })).toBeVisible();
    await designPruefung(page, info, 'emails-tab-antwort-link-leiste');
    await page.keyboard.press('Escape');
    await expect(page.getByRole('toolbar', { name: 'Link' })).toHaveCount(0);
    await expect(antwort).toBeVisible();
});
