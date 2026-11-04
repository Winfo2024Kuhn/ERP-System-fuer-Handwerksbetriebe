import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * E-Mail-Center: Löschen mit einem Klick (Knopf, Zeilenknopf, Entf) samt „Rückgängig“,
 * endgültiges Löschen im Papierkorb mit Rückfrage, serverseitige Suche mit Ordner-Schildern
 * und Nachladen. /api komplett gestubbt, nur Dummy-Daten.
 */
const basis = { type: 'EMAIL', direction: 'IN', recipient: 'info@musterbetrieb.example', attachments: [], zuordnungTyp: 'KEINE', isRead: true };
const mail = (id: number, subject: string, folder = 'inbox', sentAt = '2026-10-12T09:00:00') => ({
    ...basis, id, subject, folder, sentAt,
    fromAddress: 'Max Mustermann <max.mustermann@example.com>',
    body: 'Bitte um ein Angebot.', htmlBody: '<p>Bitte um ein Angebot.</p>',
});

const POSTEINGANG = [
    mail(901, 'Anfrage Carport', 'inbox', '2026-10-12T09:00:00'),
    mail(902, 'Anfrage Zaun', 'inbox', '2026-10-12T08:30:00'),
    mail(903, 'Lieferschein 4711', 'inbox', '2026-10-11T14:00:00'),
];
const PAPIERKORB = [mail(950, 'Angebot Geländer', 'trash', '2026-10-10T09:00:00'), mail(951, 'Rechnung Zaun', 'trash', '2026-10-11T09:00:00')];

const json = (route: Route, body: unknown, status = 200) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

interface Mitschrift { papierkorb: number[][]; zurueck: number[][]; endgueltig: number[]; suche: URLSearchParams[] }

/** Stubbt /api; der Papierkorb-Zustand lebt im Stub, damit „Rückgängig“ die Mail wirklich zurückbringt. */
async function vorbereiten(page: Page, suchTreffer?: (p: URLSearchParams) => unknown): Promise<Mitschrift> {
    const mit: Mitschrift = { papierkorb: [], zurueck: [], endgueltig: [], suche: [] };
    const imPapierkorb = new Set<number>();
    await page.route('**/api/**', async route => {
        const anfrage = route.request();
        const url = new URL(anfrage.url());
        const pfad = url.pathname;
        const ids = () => (anfrage.postDataJSON() as { ids: number[] }).ids;
        if (pfad === '/api/emails/papierkorb') { mit.papierkorb.push(ids()); ids().forEach(id => imPapierkorb.add(id)); return json(route, { verschoben: ids().length }); }
        if (pfad === '/api/emails/papierkorb/zurueckholen') { mit.zurueck.push(ids()); ids().forEach(id => imPapierkorb.delete(id)); return json(route, { zurueckgeholt: ids().length }); }
        const endgueltig = /\/api\/emails\/(\d+)\/permanent$/.exec(pfad);
        if (endgueltig && anfrage.method() === 'DELETE') { mit.endgueltig.push(Number(endgueltig[1])); return json(route, {}); }
        if (pfad === '/api/emails/suche') { mit.suche.push(url.searchParams); return json(route, suchTreffer?.(url.searchParams) ?? { eintraege: [], hatMehr: false }); }
        if (pfad === '/api/emails/stats') return json(route, { inboxCount: 0, inboxTotal: POSTEINGANG.length - imPapierkorb.size, trashTotal: 1 });
        if (pfad === '/api/emails/drafts/count') return json(route, { count: 0 });
        if (pfad.endsWith('/absender-postfaecher')) return json(route, [{ id: 1, emailAdresse: 'info@musterbetrieb.example', anzeigename: null, eigenes: false, hauptpostfach: true }]);
        if (pfad === '/api/emails/inbox') return json(route, POSTEINGANG.filter(m => !imPapierkorb.has(m.id)));
        if (pfad === '/api/emails/trash') return json(route, PAPIERKORB.filter(m => !mit.endgueltig.includes(m.id) && !mit.zurueck.flat().includes(m.id)));
        const alle = [...POSTEINGANG, ...PAPIERKORB];
        const thread = /\/api\/emails\/(\d+)\/thread$/.exec(pfad);
        if (thread) { const m = alle.find(x => x.id === Number(thread[1])); return json(route, { rootEmailId: m?.id, focusedEmailId: m?.id, emails: [m] }); }
        const detail = /\/api\/emails\/(\d+)$/.exec(pfad);
        if (detail) return json(route, alle.find(x => x.id === Number(detail[1])) ?? {});
        return json(route, []);
    });
    return mit;
}

const zeile = (page: Page, betreff: string) => page.getByTestId('email-listenzeile').filter({ hasText: betreff });

test('Löschen mit einem Klick: Papierkorb, Rückgängig, Entf-Taste und endgültig im Papierkorb', async ({ page }, info) => {
    const mit = await vorbereiten(page);
    await page.goto('/emails/inbox');
    await zeile(page, 'Anfrage Carport').click();
    const kopf = page.getByTestId('email-detail-header');
    await expect(kopf.getByRole('heading', { name: 'Anfrage Carport' })).toBeVisible();

    // Zeilenknopf erscheint beim Drüberfahren und per Tab direkt nach dem Stern – mit Fokus-Ring.
    const zeilenKnopf = zeile(page, 'Anfrage Zaun').getByRole('button', { name: 'In den Papierkorb (Entf)' });
    await expect(zeilenKnopf).toHaveCSS('opacity', '0');
    await zeile(page, 'Anfrage Zaun').hover();
    await expect(zeilenKnopf).toHaveCSS('opacity', '1');
    await page.mouse.move(900, 700);
    await zeile(page, 'Lieferschein 4711').getByRole('button', { name: 'Markieren' }).focus();
    await page.keyboard.press('Tab');
    const fokusKnopf = zeile(page, 'Lieferschein 4711').getByRole('button', { name: 'In den Papierkorb (Entf)' });
    await expect(fokusKnopf).toBeFocused();
    await expect(fokusKnopf).toHaveCSS('opacity', '1');
    const kopfKnopf = kopf.getByRole('button', { name: 'In den Papierkorb (Entf)' });
    await designPruefung(page, info, 'email-loeschen-knoepfe', { primaerAktion: kopfKnopf });
    await fokusKnopf.blur();

    // Ein Klick: weg, Meldung mit „Rückgängig“, die nächste Mail ist offen.
    await kopfKnopf.click();
    await expect(page.getByText('E-Mail in den Papierkorb gelegt.')).toBeVisible();
    await expect(zeile(page, 'Anfrage Carport')).toHaveCount(0);
    await expect(kopf.getByRole('heading', { name: 'Anfrage Zaun' })).toBeVisible();
    expect(mit.papierkorb).toEqual([[901]]);
    await designPruefung(page, info, 'email-loeschen-rueckgaengig', { primaerAktion: page.getByRole('button', { name: 'Rückgängig' }) });

    await page.getByRole('button', { name: 'Rückgängig' }).click();
    await expect(page.getByText('E-Mail ist wieder da.')).toBeVisible();
    await expect(zeile(page, 'Anfrage Carport')).toBeVisible();
    expect(mit.zurueck).toEqual([[901]]);

    // Entf löscht die geöffnete Mail – nicht, solange im Suchfeld getippt wird.
    const suchfeld = page.getByRole('searchbox', { name: 'E-Mails durchsuchen' });
    await suchfeld.click();
    await page.keyboard.press('Delete');
    await suchfeld.blur();
    expect(mit.papierkorb).toHaveLength(1);
    await page.keyboard.press('Delete');
    await expect.poll(() => mit.papierkorb).toEqual([[901], [902]]);
    await expect(kopf.getByRole('heading', { name: 'Lieferschein 4711' })).toBeVisible();

    // Im Papierkorb: „Wiederherstellen“ holt in den Ursprungsordner zurück (nicht „Verschieben in den Posteingang“).
    await page.getByRole('button', { name: 'Papierkorb', exact: true }).click();
    await zeile(page, 'Rechnung Zaun').click();
    await expect(kopf.getByRole('heading', { name: 'Rechnung Zaun' })).toBeVisible();
    await kopf.getByRole('button', { name: 'Weitere E-Mail-Aktionen' }).click();
    await page.getByRole('menuitem', { name: 'Wiederherstellen' }).click();
    await expect.poll(() => mit.zurueck).toEqual([[901], [951]]);
    await expect(zeile(page, 'Rechnung Zaun')).toHaveCount(0);
    await expect(page.getByText('E-Mail ist wieder da.').first()).toBeVisible();

    // Endgültig, mit Rückfrage und Betreff.
    await zeile(page, 'Angebot Geländer').click();
    await kopf.getByRole('button', { name: 'Endgültig löschen (Entf)' }).click();
    const dialog = page.getByRole('dialog', { name: 'Endgültig löschen?' });
    await expect(dialog).toContainText('„Angebot Geländer“ wird endgültig gelöscht und auch vom Mailserver entfernt.');
    await designPruefung(page, info, 'email-endgueltig-loeschen', { primaerAktion: dialog.getByRole('button', { name: 'Endgültig löschen' }) });
    await dialog.getByRole('button', { name: 'Endgültig löschen' }).click();
    await expect.poll(() => mit.endgueltig).toEqual([950]);
    await expect(page.getByText('E-Mail endgültig gelöscht.')).toBeVisible();
    await expect(page.getByText('Keine E-Mails', { exact: true })).toBeVisible();
});

const ORDNER = ['inbox', 'sent', 'trash', 'spam', 'newsletter', 'projects', 'offers', 'suppliers'];
const treffer = (von: number, bis: number) => Array.from({ length: bis - von }, (_, i) => {
    const nr = von + i;
    return mail(1000 + nr, `Geländer Müller ${nr + 1}`, ORDNER[nr % ORDNER.length], `2026-10-${String(28 - (nr % 27)).padStart(2, '0')}T09:00:00`);
});

test('Suche: „müller geländer“ in allen Ordnern mit Schildern, Nachladen und Ordner-Modus', async ({ page }, info) => {
    const mit = await vorbereiten(page, params => {
        if (params.get('ordner') === 'inbox') return { eintraege: treffer(0, 1), hatMehr: false, seite: 0, seitenGroesse: 25 };
        return params.get('page') === '0'
            ? { eintraege: treffer(0, 25), hatMehr: true, seite: 0, seitenGroesse: 25 }
            : { eintraege: treffer(25, 28), hatMehr: false, seite: 1, seitenGroesse: 25 };
    });
    await page.goto('/emails/inbox');
    await expect(zeile(page, 'Anfrage Carport')).toBeVisible();

    await page.getByRole('button', { name: 'Alle Ordner', exact: true }).click();
    await page.getByRole('searchbox', { name: 'E-Mails durchsuchen' }).fill('müller geländer');
    await expect(page.getByText('25 Treffer in allen Ordnern, weitere beim Scrollen')).toBeVisible();
    await expect(zeile(page, 'Geländer Müller 3').getByTestId('email-ordner-chip')).toHaveText('Liegt in: Papierkorb');
    expect(mit.suche.map(p => [p.get('q'), p.get('ordner'), p.get('page'), p.get('size')])).toEqual([['müller geländer', 'alle', '0', '25']]);
    await expect(page.getByRole('button', { name: 'Suchtreffer sind immer nach Datum sortiert, neueste zuerst.' })).toHaveAttribute('aria-disabled', 'true');
    await designPruefung(page, info, 'email-suche-alle-ordner');

    // Info-Knopf neben dem Umschalter: ganz sichtbar.
    await page.getByRole('button', { name: 'Mehr zu: So sucht das E-Mail-Center' }).click();
    const popover = page.getByRole('dialog', { name: 'So sucht das E-Mail-Center' });
    await expect(popover).toContainText('„wurth“, „wuerth“ und „Würth“ finden dasselbe.');
    await expect(popover).toBeInViewport({ ratio: 1 });
    await designPruefung(page, info, 'email-suche-info');
    await page.keyboard.press('Escape');
    await expect(popover).toBeHidden();

    // Ans Listenende scrollen: Seite 2 kommt dazu.
    await zeile(page, 'Geländer Müller 25').scrollIntoViewIfNeeded();
    await expect(zeile(page, 'Geländer Müller 28')).toBeVisible();
    await expect(page.getByText('Ende der Liste')).toBeVisible();
    expect(mit.suche.map(p => p.get('page'))).toEqual(['0', '1']);

    // Ordner-Modus fragt den geöffneten Ordner, ohne Schilder.
    await page.getByRole('button', { name: 'Ordner', exact: true }).click();
    await expect(page.getByText('1 Treffer in diesem Ordner')).toBeVisible();
    expect(mit.suche.at(-1)?.get('ordner')).toBe('inbox');
    await expect(page.getByTestId('email-ordner-chip')).toHaveCount(0);
});
