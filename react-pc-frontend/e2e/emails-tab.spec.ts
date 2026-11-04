import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * E-Mail-Reiter (`EmailsTab`) in der Anfrage: Filter in Rose über die gemeinsame
 * Segment-Auswahl, Verlauf per Tastatur öffnen und per Escape schließen,
 * Bild- und PDF-Anhang als eigenes Fenster über dem Verlauf (Fokus auf „Schließen“,
 * Tab bleibt drin, Escape schließt nur die Vorschau, Fokus zurück an den Anhang),
 * Entwurf löschen mit „Rückgängig“,
 * Antworten (ein Kopf „Antwort“, genau ein X, Pfeil zurück zum Verlauf sichert den
 * Entwurf). Die Rückgängig-Frist läuft über `page.clock`, nicht über Warten.
 *
 * /api ist gestubbt, kein Backend; nur Dummy-Daten (DSGVO): Max Mustermann.
 */

const ANFRAGE_ID = 9;
const BETREFF = 'Anfrage Geländer Mustermann';
const PIXEL_PNG = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');

const ANFRAGE = {
    id: ANFRAGE_ID, kundenId: 3, kundenName: 'Max Mustermann', bauvorhaben: 'Geländer Mustermann',
    kundennummer: 'K-1003', anfragesnummer: 'AG-2026/11/00009', betrag: 4200,
    kundenEmails: ['max@example.com'], anlegedatum: '2026-11-02', abgeschlossen: false,
    emails: [] as unknown[], dokumente: [] as unknown[],
};

const MAILS = [
    {
        id: 7, subject: BETREFF, fromAddress: 'Max Mustermann <max@example.com>', recipient: 'buero@example.com',
        direction: 'IN', sentAt: '2026-11-02T09:15:00', body: 'Guten Tag, bitte um ein Angebot für ein Geländer.',
        replyCount: 1,
    },
    {
        id: 8, subject: `AW: ${BETREFF}`, fromAddress: 'buero@example.com', recipient: 'max@example.com',
        direction: 'OUT', sentAt: '2026-11-02T14:30:00', body: 'Vielen Dank, wir melden uns.', parentEmailId: 7,
    },
    {
        id: 12, subject: 'Aufmaß Treppe', fromAddress: 'buero@example.com', recipient: 'max@example.com',
        direction: 'OUT', sentAt: '2026-11-03T08:00:00', body: 'Termin für das Aufmaß am Freitag.',
    },
];

function verlauf(mitEntwurf: boolean) {
    const emails: Record<string, unknown>[] = [
        {
            id: 7, subject: BETREFF, fromAddress: 'Max Mustermann <max@example.com>', recipient: 'buero@example.com',
            direction: 'IN', sentAt: '2026-11-02T09:15:00', htmlBody: '<p>Guten Tag, bitte um ein Angebot für ein Geländer.</p>',
            snippet: 'Guten Tag, bitte um ein Angebot', attachments: [{ id: 3, originalFilename: 'skizze.png', sizeBytes: 68 }, { id: 4, originalFilename: 'zeichnung.pdf', sizeBytes: 320 }],
        },
        {
            id: 8, subject: `AW: ${BETREFF}`, fromAddress: 'buero@example.com', recipient: 'max@example.com',
            direction: 'OUT', sentAt: '2026-11-02T14:30:00', htmlBody: '<p>Vielen Dank, wir melden uns.</p>',
            snippet: 'Vielen Dank, wir melden uns.', attachments: [],
        },
    ];
    if (mitEntwurf) {
        emails.push({ id: 90, direction: 'OUT', isDraft: true, draftId: 99, snippet: 'Angebot folgt bis Freitag', sentAt: '2026-11-03T10:00:00', attachments: [] });
    }
    return { rootEmailId: 7, focusedEmailId: 7, emails };
}

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

async function stubbeApi(page: Page) {
    const stand = { entwurfGeloescht: false, loeschAufrufe: 0, entwurfGespeichert: 0 };
    await page.route('**/api/**', (route) => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        const methode = request.method();
        if (pfad === '/api/auth/me') {
            return json(route, {
                id: 1, username: 'max.mustermann', displayName: 'Max Mustermann',
                active: true, roles: ['USER'], admin: false, requiresInitialSetup: false,
            });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (/^\/api\/last-accessed\//.test(pfad)) return methode === 'POST' ? route.fulfill({ status: 204, body: '' }) : json(route, {});
        if (pfad === `/api/anfragen/${ANFRAGE_ID}`) return json(route, ANFRAGE);
        if (pfad === `/api/emails/anfrage/${ANFRAGE_ID}`) return json(route, MAILS);
        if (pfad === '/api/emails/7/thread') return json(route, verlauf(!stand.entwurfGeloescht));
        if (pfad === '/api/emails/8') return json(route, { ...MAILS[1], bodyHtml: '<p>Vielen Dank, wir melden uns.</p>' });
        if (pfad === '/api/emails/7/attachments/3') return route.fulfill({ status: 200, contentType: 'image/png', body: PIXEL_PNG });
        if (pfad === '/api/emails/7/attachments/4') return route.fulfill({ status: 200, contentType: 'application/pdf', body: '%PDF-1.4\n%%EOF\n' });
        if ((pfad === '/api/emails/drafts' && methode === 'POST') || (pfad === '/api/emails/drafts/101' && methode === 'PUT')) {
            stand.entwurfGespeichert++;
            return json(route, { id: 101 });
        }
        if (pfad === '/api/emails/drafts/99' && methode === 'DELETE') {
            stand.loeschAufrufe++;
            stand.entwurfGeloescht = true;
            return route.fulfill({ status: 204, body: '' });
        }
        return json(route, []);
    });
    return stand;
}

test.describe('E-Mail-Reiter in der Anfrage', () => {
    test('filtern, Verlauf per Tastatur, Anhang, Entwurf löschen mit Rückgängig, antworten', async ({ page }, testInfo) => {
        await page.clock.install();
        const stand = await stubbeApi(page);
        await page.goto(`/anfragen?anfrageId=${ANFRAGE_ID}&tab=emails`);

        const zeile = page.getByRole('button', { name: BETREFF, exact: true });
        await expect(zeile).toBeVisible();
        const neueEmail = page.getByRole('button', { name: 'Neue E-Mail' });
        await designPruefung(page, testInfo, 'emails-tab-liste', { primaerAktion: neueEmail });

        // Filter: aktiv in Rose, Pfeiltasten wandern
        const filter = page.getByRole('radiogroup', { name: 'E-Mails filtern' });
        await filter.getByRole('radio', { name: 'Eingang' }).click();
        await expect(filter.getByRole('radio', { name: 'Eingang' })).toHaveAttribute('aria-checked', 'true');
        await expect(filter.getByRole('radio', { name: 'Eingang' })).toHaveClass(/bg-rose-50/);
        await expect(page.getByRole('button', { name: 'Aufmaß Treppe' })).toBeHidden();
        await page.keyboard.press('ArrowRight');
        await expect(filter.getByRole('radio', { name: 'Ausgang' })).toBeFocused();
        await expect(page.getByRole('button', { name: 'Aufmaß Treppe' })).toBeVisible();
        await expect(zeile).toBeHidden();
        await designPruefung(page, testInfo, 'emails-tab-filter-ausgang', { primaerAktion: neueEmail });
        await filter.getByRole('radio', { name: 'Alle' }).click();

        // Verlauf per Tastatur: von „Neue E-Mail“ mit Tab zur ersten Zeile, Enter öffnet
        await neueEmail.focus();
        await page.keyboard.press('Tab');
        await expect(zeile).toBeFocused();
        await designPruefung(page, testInfo, 'emails-tab-zeile-fokus', { primaerAktion: neueEmail });
        await page.keyboard.press('Enter');
        const dialog = page.getByRole('dialog', { name: BETREFF });
        await expect(dialog).toBeVisible();
        await expect(dialog.getByText('2 Nachrichten im Verlauf, 1 Entwurf')).toBeVisible();
        // Der erste „Antworten“ sitzt im Kopf des Verlaufs, die übrigen an den Nachrichten.
        const antwortenImKopf = dialog.getByRole('button', { name: 'Antworten', exact: true }).first();
        await designPruefung(page, testInfo, 'emails-tab-verlauf', { primaerAktion: antwortenImKopf });

        // Escape schließt den Verlauf, der Fokus steht wieder auf der Zeile
        await page.keyboard.press('Escape');
        await expect(dialog).toBeHidden();
        await expect(zeile).toBeFocused();

        // Bild-Anhang: eigenes Fenster über dem Verlauf, Fokus auf „Schließen“, Tab bleibt drin,
        // Escape schließt nur die Vorschau und gibt den Fokus an den Anhang-Knopf zurück
        await page.keyboard.press('Enter');
        await expect(dialog).toBeVisible();
        const bildAnhang = dialog.getByRole('button', { name: /skizze\.png/ });
        await bildAnhang.click();
        const bildVorschau = page.getByRole('dialog', { name: 'skizze.png' });
        await expect(bildVorschau).toBeVisible();
        await expect(page.getByAltText('skizze.png')).toBeVisible();
        const bildSchliessen = bildVorschau.getByRole('button', { name: 'Vorschau schließen' });
        await expect(bildSchliessen).toBeFocused();
        await designPruefung(page, testInfo, 'emails-tab-bild-vorschau', { primaerAktion: bildSchliessen });
        for (let i = 0; i < 5; i++) await page.keyboard.press('Tab');
        await expect(bildVorschau.locator(':focus')).toHaveCount(1);
        await page.keyboard.press('Shift+Tab');
        await expect(bildVorschau.locator(':focus')).toHaveCount(1);
        await page.keyboard.press('Escape');
        await expect(bildVorschau).toBeHidden();
        await expect(dialog).toBeVisible();
        await expect(bildAnhang).toBeFocused();

        // PDF-Anhang in der gemeinsamen Dokumentvorschau: auch hier schließt Escape nur die Vorschau
        const pdfAnhang = dialog.getByRole('button', { name: /zeichnung\.pdf/ });
        await pdfAnhang.click();
        const pdfVorschau = page.getByRole('dialog', { name: 'zeichnung.pdf' });
        await expect(pdfVorschau.getByRole('button', { name: 'Vorschau schließen' })).toBeFocused();
        await page.keyboard.press('Escape');
        await expect(pdfVorschau).toBeHidden();
        await expect(dialog).toBeVisible();
        await expect(pdfAnhang).toBeFocused();

        // Entwurf löschen: sofort weg, „Rückgängig“ holt ihn zurück
        const entwurf = dialog.getByText('Angebot folgt bis Freitag');
        await expect(entwurf).toBeVisible();
        const entwurfLoeschen = dialog.getByRole('button', { name: 'Entwurf löschen' });
        await designPruefung(page, testInfo, 'emails-tab-verlauf-entwurf', { primaerAktion: entwurfLoeschen });
        await entwurfLoeschen.click();
        await expect(entwurf).toBeHidden();
        await expect(dialog.getByText('2 Nachrichten im Verlauf', { exact: true })).toBeVisible();
        const meldung = page.getByText('Entwurf „Angebot folgt bis Freitag“ gelöscht.');
        await expect(meldung).toBeVisible();
        await designPruefung(page, testInfo, 'emails-tab-entwurf-geloescht');
        await page.getByRole('button', { name: 'Rückgängig' }).click();
        await expect(entwurf).toBeVisible();

        // Noch einmal löschen und die Frist ablaufen lassen: erst jetzt geht das DELETE raus
        await entwurfLoeschen.click();
        await expect(entwurf).toBeHidden();
        expect(stand.loeschAufrufe).toBe(0);
        const neuGeladen = page.waitForResponse(r => r.url().endsWith('/api/emails/7/thread'));
        await page.clock.runFor(6_500);
        await neuGeladen;
        expect(stand.loeschAufrufe).toBe(1);
        await expect(page.getByRole('button', { name: 'Rückgängig' })).toBeHidden();
        await expect(entwurf).toBeHidden();

        // Antworten öffnet das Formular im Verlauf: ein Kopf „Antwort“ mit Betreff, genau ein X
        await antwortenImKopf.click();
        const antwortDialog = page.getByRole('dialog', { name: 'Antwort', exact: true });
        const senden = antwortDialog.getByRole('button', { name: 'E-Mail senden' });
        await expect(senden).toBeVisible();
        await expect(antwortDialog.getByText(`AW: ${BETREFF}`, { exact: true })).toBeVisible();
        await expect(page.getByRole('button', { name: 'Fenster schließen' })).toHaveCount(1);
        await expect(antwortDialog.getByRole('button', { name: 'Fenster schließen' })).toHaveAttribute('title', 'Schließen');
        await expect(antwortDialog.getByText('Neue E-Mail')).toHaveCount(0);
        const pfeil = antwortDialog.getByTitle('Zurück zum Verlauf');
        await expect(pfeil).toBeVisible();
        // Der geklickte „Antworten“ ist weg: Der Fokus geht erst auf den Pfeil, dann setzt das
        // Formular den Cursor an den Anfang der Nachricht (nie <body>)
        await expect(antwortDialog.getByRole('textbox', { name: 'Nachricht' })).toBeFocused();
        await expect(antwortDialog.getByRole('button', { name: 'Zurück zum Verlauf' })).toHaveCount(2);
        await expect(antwortDialog.getByRole('button', { name: 'Schließen', exact: true })).toHaveCount(0);
        // Pfeil und X sitzen in derselben Kopfzeile: links der Pfeil, rechts das X
        const pfeilBox = await pfeil.boundingBox();
        const xBox = await antwortDialog.getByRole('button', { name: 'Fenster schließen' }).boundingBox();
        expect(pfeilBox && xBox && Math.abs((pfeilBox.y + pfeilBox.height / 2) - (xBox.y + xBox.height / 2)) < 8).toBe(true);
        expect(pfeilBox!.x).toBeLessThan(xBox!.x);
        await designPruefung(page, testInfo, 'emails-tab-antwort', { primaerAktion: senden });

        // Escape im Empfängerfeld (bei Antworten steht „An“ fest, also die Kopie) schließt
        // nur die Vorschlagsliste, der Verlauf bleibt offen
        await antwortDialog.getByRole('button', { name: /CC hinzufügen/ }).click();
        const empfaenger = antwortDialog.getByRole('textbox', { name: 'Kopie (CC)' });
        await empfaenger.fill('mu');
        await page.clock.runFor(400);
        // Die Vorschlagsliste hängt per Portal am body, nicht im Dialog
        const vorschlaege = page.getByText('Verknüpfte E-Mail-Adressen');
        await expect(vorschlaege).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(vorschlaege).toBeHidden();
        await expect(antwortDialog).toBeVisible();
        await expect(senden).toBeVisible();

        // Pfeil „Zurück zum Verlauf“: Entwurf sichern, dann wieder der Verlauf; der Kopf zeigt den neuen Betreff
        await antwortDialog.getByRole('textbox', { name: 'Betreff' }).fill(`AW: ${BETREFF} – Termin`);
        await expect(antwortDialog.getByText(`AW: ${BETREFF} – Termin`, { exact: true })).toBeVisible();
        const gespeichert = page.waitForResponse(r => r.url().endsWith('/api/emails/drafts') && r.request().method() === 'POST');
        await pfeil.click();
        await gespeichert;
        await expect(dialog).toBeVisible();
        await expect(dialog.getByText('2 Nachrichten im Verlauf', { exact: true })).toBeVisible();
        await expect(senden).toBeHidden();
        await expect(antwortenImKopf).toBeFocused();
        expect(stand.entwurfGespeichert).toBe(1);
    });
});
