import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { json } from './hilfen/postfaecher';

/**
 * E-Mail-Center: „Vielleicht Spam“ bleibt im Posteingang, bis jemand entscheidet, und
 * Warnhinweise zu Betrugsmaschen stehen fest an der Mail. Nur Dummy-Daten, /api komplett gestubbt.
 */
const basis = {
    type: 'EMAIL', direction: 'IN', recipient: 'info@musterbetrieb.example',
    isRead: true, attachments: [], zuordnungTyp: 'KEINE', spamGruende: [], warnhinweis: null,
};

function mails() {
    return [
        {
            ...basis, id: 901, subject: 'Ihr Konto wird gesperrt',
            fromAddress: 'Musterbank Kundenservice <service@muster-versand.example>',
            body: 'Bitte bestätigen Sie Ihre Daten.', htmlBody: '<p>Bitte bestätigen Sie Ihre Daten.</p>',
            sentAt: '2026-10-30T09:30:00', spamScore: 55, spamVerdacht: true,
            spamGruende: ['ABSENDER_GEFAELSCHT', 'ANTWORT_UMGELEITET', '<img src=x onerror=alert(1)>'],
        },
        {
            ...basis, id: 902, subject: 'Korrekturabzug Ihres Firmeneintrags',
            fromAddress: 'Branchenverzeichnis Muster <info@verzeichnis-muster.example>',
            body: 'Bitte prüfen und unterschreiben Sie den Eintrag.', htmlBody: '<p>Bitte prüfen und unterschreiben Sie den Eintrag.</p>',
            sentAt: '2026-10-30T08:15:00', warnhinweis: 'BRANCHENBUCH',
        },
        {
            ...basis, id: 903, subject: 'Anfrage Balkongeländer',
            fromAddress: 'Erika Musterfrau <erika.musterfrau@example.org>',
            body: 'Können Sie uns ein Angebot machen?', htmlBody: '<p>Können Sie uns ein Angebot machen?</p>',
            sentAt: '2026-10-29T14:00:00',
        },
    ];
}

interface Mitschrift { aufrufe: string[] }

async function vorbereiten(page: Page, start: string): Promise<Mitschrift> {
    const mitschrift: Mitschrift = { aufrufe: [] };
    let bestand = mails();
    await page.route('**/api/**', async route => {
        const anfrage = route.request();
        const pfad = new URL(anfrage.url()).pathname;
        if (anfrage.method() === 'POST') {
            mitschrift.aufrufe.push(pfad);
            const treffer = pfad.match(/^\/api\/emails\/(\d+)\/(mark-spam|mark-not-spam)$/);
            if (treffer) {
                const id = Number(treffer[1]);
                bestand = treffer[2] === 'mark-spam'
                    ? bestand.filter(m => m.id !== id)
                    : bestand.map(m => m.id === id ? { ...m, spamVerdacht: false } : m);
                return route.fulfill({ status: 200, contentType: 'text/plain', body: 'ok' });
            }
            return json(route, {});
        }
        if (pfad === '/api/emails/stats') return json(route, { inboxCount: bestand.length });
        if (pfad === '/api/emails/absender-postfaecher') {
            return json(route, [{ id: 1, emailAdresse: 'info@musterbetrieb.example', anzeigename: null, eigenes: false, hauptpostfach: true }]);
        }
        if (pfad === '/api/emails/inbox') return json(route, bestand);
        const treffer = pfad.match(/^\/api\/emails\/(\d+)(\/thread)?$/);
        const email = treffer && bestand.find(m => m.id === Number(treffer[1]));
        if (email) return json(route, treffer![2] ? { rootEmailId: email.id, focusedEmailId: email.id, emails: [email] } : email);
        return json(route, []);
    });
    await page.goto(start);
    return mitschrift;
}

function zeile(page: Page, betreff: string) {
    return page.locator('[aria-roledescription="draggable"]').filter({ hasText: betreff });
}

test('Liste zeigt „Spam-Verdacht“ und ein Warnzeichen statt Prozent', async ({ page }, info) => {
    await vorbereiten(page, '/emails/inbox');
    const verdacht = zeile(page, 'Ihr Konto wird gesperrt');
    await expect(verdacht.getByTestId('spam-verdacht-schild')).toHaveText('Spam-Verdacht');
    await expect(zeile(page, 'Korrekturabzug').getByRole('img', { name: 'Vorsicht: mögliche Branchenbuch-Abzocke' })).toBeVisible();
    await expect(zeile(page, 'Anfrage Balkongeländer').getByTestId('spam-verdacht-schild')).toHaveCount(0);
    await expect(page.getByText(/% Spam/)).toHaveCount(0);

    await designPruefung(page, info, 'email-spam-verdacht-liste');
});

test('„Vielleicht Spam“ erklärt die Gründe, „Kein Spam“ lässt die Mail im Posteingang', async ({ page }, info) => {
    const mitschrift = await vorbereiten(page, '/emails/inbox/901');
    const kopf = page.getByTestId('email-detail-header');
    const hinweis = kopf.getByTestId('spam-verdacht-hinweis');
    await expect(hinweis).toContainText('Vielleicht Spam');
    await expect(hinweis).toContainText('Sie bleibt im Posteingang, bis du entscheidest.');
    const keinSpam = hinweis.getByRole('button', { name: 'Kein Spam' });
    await expect(keinSpam).toBeInViewport();

    await hinweis.getByRole('button', { name: 'Mehr zu: Warum?' }).click();
    const popover = page.getByRole('dialog', { name: 'Warum?' });
    await expect(popover).toContainText('Der Absendername passt nicht zur Adresse.');
    await expect(popover).toContainText('Antworten gingen an eine andere Adresse.');
    await expect(popover).toContainText('auch auf dem Mail-Server');
    await expect(popover).not.toContainText('onerror');
    await expect(popover).toBeInViewport({ ratio: 1 });
    await designPruefung(page, info, 'email-spam-verdacht-warum');
    await page.keyboard.press('Escape');
    await expect(popover).toHaveCount(0);

    await keinSpam.click();
    await expect(hinweis).toHaveCount(0);
    expect(mitschrift.aufrufe).toContain('/api/emails/901/mark-not-spam');
    // Mail bleibt in Liste und Ansicht, nur das Schild ist weg.
    await expect(zeile(page, 'Ihr Konto wird gesperrt')).toBeVisible();
    await expect(zeile(page, 'Ihr Konto wird gesperrt').getByTestId('spam-verdacht-schild')).toHaveCount(0);
    await expect(kopf.getByRole('heading', { name: 'Ihr Konto wird gesperrt' })).toBeVisible();
});

test('„Ist Spam“ nimmt die Mail aus dem Posteingang', async ({ page }) => {
    const mitschrift = await vorbereiten(page, '/emails/inbox/901');
    await page.getByTestId('spam-verdacht-hinweis').getByRole('button', { name: 'Ist Spam' }).click();
    await expect(zeile(page, 'Ihr Konto wird gesperrt')).toHaveCount(0);
    expect(mitschrift.aufrufe).toContain('/api/emails/901/mark-spam');
    await expect(zeile(page, 'Anfrage Balkongeländer')).toBeVisible();
});

test('Warnhinweis steht fest an der Mail und erklärt die Erkennung', async ({ page }, info) => {
    await vorbereiten(page, '/emails/inbox/902');
    const warnung = page.getByTestId('email-detail-header').getByRole('note', { name: 'Vorsicht: mögliche Branchenbuch-Abzocke' });
    await expect(warnung).toContainText('teuren Vertrag über mehrere Jahre');
    // Nicht wegklickbar: einziger Knopf ist das (i).
    await expect(warnung.getByRole('button')).toHaveCount(1);
    await expect(page.getByTestId('spam-verdacht-hinweis')).toHaveCount(0);

    await warnung.getByRole('button', { name: 'Mehr zu: Woran erkennt das Programm das?' }).click();
    const popover = page.getByRole('dialog', { name: 'Woran erkennt das Programm das?' });
    await expect(popover).toContainText('Branchenbuch');
    await expect(popover).toContainText('Der Hinweis verschiebt nichts');
    await expect(popover).toBeInViewport({ ratio: 1 });
    await designPruefung(page, info, 'email-spam-warnhinweis');
});
