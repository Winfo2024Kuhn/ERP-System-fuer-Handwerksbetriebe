import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * E-Mail-Reiter (`EmailsTab`) in der Anfrage: erste Seite mit 25 Verläufen, „Mehr laden“,
 * Suche beim Server (springt auf Seite 1), Filter in Rose über die gemeinsame
 * Segment-Auswahl, Verlauf per Tastatur öffnen und per Escape schließen,
 * Bild- und PDF-Anhang als eigenes Fenster über dem Verlauf (Fokus auf „Schließen“,
 * Tab bleibt drin, Escape schließt nur die Vorschau, Fokus zurück an den Anhang),
 * Entwurf löschen mit „Rückgängig“,
 * Antworten (ein Kopf „Antwort“, genau ein X, Pfeil zurück zum Verlauf sichert den
 * Entwurf). Dazu der Kunden-Reiter mit seiner Erklärung. Entprellen und
 * Rückgängig-Frist laufen über `page.clock`, nicht über Warten.
 *
 * /api ist gestubbt, kein Backend; nur Dummy-Daten (DSGVO): Max Mustermann.
 */

const ANFRAGE_ID = 9;
const KUNDE_ID = 3;
const BETREFF = 'Anfrage Geländer Mustermann';
const PIXEL_PNG = Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64');

const ANFRAGE = {
    id: ANFRAGE_ID, kundenId: 3, kundenName: 'Max Mustermann', bauvorhaben: 'Geländer Mustermann',
    kundennummer: 'K-1003', anfragesnummer: 'AG-2026/11/00009', betrag: 4200,
    kundenEmails: ['max@example.com'], anlegedatum: '2026-11-02', abgeschlossen: false,
    emailAnzahl: 30, dokumente: [] as unknown[],
};

const KUNDE = {
    id: KUNDE_ID, kundennummer: '1003', name: 'Max Mustermann', kundenEmails: ['max@example.com'],
    emailAnzahl: 3, projekte: [], anfragen: [], geschaeftsdokumente: [], notizen: [],
};

type Eintrag = Record<string, unknown> & { id: number; subject: string; direction: 'IN' | 'OUT' };

function eintrag(id: number, subject: string, direction: 'IN' | 'OUT', sentAt: string, mehr: Partial<Eintrag> = {}): Eintrag {
    return {
        id, subject, direction, sentAt,
        fromAddress: direction === 'IN' ? 'Max Mustermann <max@example.com>' : 'buero@example.com',
        recipient: direction === 'IN' ? 'buero@example.com' : 'max@example.com',
        bodyHtml: `<p>${subject}: bitte kurz Bescheid geben.</p>`,
        nachrichtenAnzahl: 1, mitAnhang: false, zustellStatus: null, zustellFehler: null, ...mehr,
    };
}

/** Der Angebots-Verlauf: Max Mustermann hat zuletzt geantwortet, darum steht er oben. */
const VERLAUF = eintrag(7, BETREFF, 'IN', '2026-11-03T09:15:00', {
    bodyHtml: '<p>Guten Tag, bitte um ein Angebot für ein Geländer.</p>', nachrichtenAnzahl: 2, mitAnhang: true,
});
const AUFMASS = eintrag(12, 'Aufmaß Treppe', 'OUT', '2026-11-02T08:00:00');
const FUELLER = Array.from({ length: 28 }, (_, i) => eintrag(
    100 + i, `Rückfrage Geländer Teil ${i + 1}`, i % 2 ? 'OUT' : 'IN', `2026-10-${String(28 - i).padStart(2, '0')}T10:00:00`));

/** Was der Server je Richtung liefert: je Verlauf die neueste passende Mail. */
const VERLAEUFE: Record<'ALLE' | 'IN' | 'OUT', Eintrag[]> = {
    ALLE: [VERLAUF, AUFMASS, ...FUELLER],
    IN: [VERLAUF, ...FUELLER.filter(e => e.direction === 'IN')],
    OUT: [eintrag(8, `AW: ${BETREFF}`, 'OUT', '2026-11-02T14:30:00', { nachrichtenAnzahl: 2 }), AUFMASS,
        ...FUELLER.filter(e => e.direction === 'OUT')],
};

/** Wie der Server: Richtung und Suche filtern, `page`/`size` schneiden. */
function reiterSeite(url: URL) {
    const richtung = url.searchParams.get('richtung');
    const q = url.searchParams.get('q')?.toLowerCase();
    const seite = Number(url.searchParams.get('page'));
    const groesse = Number(url.searchParams.get('size'));
    const passend = VERLAEUFE[richtung === 'IN' || richtung === 'OUT' ? richtung : 'ALLE']
        .filter(e => !q || e.subject.toLowerCase().includes(q));
    return { eintraege: passend.slice(seite * groesse, (seite + 1) * groesse), gesamt: passend.length, seite, seitenGroesse: groesse };
}

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
    const stand = { entwurfGeloescht: false, loeschAufrufe: 0, entwurfGespeichert: 0, reiterAbfragen: [] as string[] };
    await page.route('**/api/**', (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const pfad = url.pathname;
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
        if (pfad === `/api/kunden/${KUNDE_ID}`) return json(route, KUNDE);
        if (pfad === `/api/emails/reiter/anfrage/${ANFRAGE_ID}`) {
            stand.reiterAbfragen.push(url.search);
            return json(route, reiterSeite(url));
        }
        if (pfad === `/api/emails/reiter/kunde/${KUNDE_ID}`) return json(route, { eintraege: [VERLAUF, AUFMASS, FUELLER[0]], gesamt: 3, seite: 0, seitenGroesse: 25 });
        if (pfad === '/api/emails/7/thread') return json(route, verlauf(!stand.entwurfGeloescht));
        if (pfad === '/api/emails/8') {
            return json(route, {
                id: 8, subject: `AW: ${BETREFF}`, fromAddress: 'buero@example.com', recipient: 'max@example.com',
                direction: 'OUT', sentAt: '2026-11-02T14:30:00', bodyHtml: '<p>Vielen Dank, wir melden uns.</p>',
            });
        }
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
    test('Mehr laden, suchen, filtern, Verlauf per Tastatur, Anhang, Entwurf löschen mit Rückgängig, antworten', async ({ page }, testInfo) => {
        await page.clock.install();
        const stand = await stubbeApi(page);
        await page.goto(`/anfragen?anfrageId=${ANFRAGE_ID}&tab=emails`);

        // Erste Seite: 25 von 30 Verläufen, der zuletzt beantwortete oben, die Zahl am Reiter vom Server
        const zeile = page.getByRole('button', { name: BETREFF, exact: true });
        await expect(zeile).toBeVisible();
        await expect(page.getByRole('button', { name: /^E-Mails \(30\)/ })).toBeVisible();
        const zaehler = page.getByText(/^\d+ von \d+ angezeigt$/);
        await expect(zaehler).toHaveText('25 von 30 angezeigt');
        await expect(zaehler).toHaveClass(/tabular-nums/);
        expect(stand.reiterAbfragen).toEqual(['?page=0&size=25']);
        const neueEmail = page.getByRole('button', { name: 'Neue E-Mail' });
        await designPruefung(page, testInfo, 'emails-tab-liste', { primaerAktion: neueEmail });

        // „Mehr laden“ hängt die nächsten an, dann ist alles da
        const mehrLaden = page.getByRole('button', { name: 'Mehr laden' });
        await mehrLaden.scrollIntoViewIfNeeded();
        await designPruefung(page, testInfo, 'emails-tab-mehr-laden');
        await mehrLaden.click();
        await expect(zaehler).toHaveText('30 von 30 angezeigt');
        await expect(mehrLaden).toBeHidden();
        await expect(page.getByRole('button', { name: 'Rückfrage Geländer Teil 28', exact: true })).toBeVisible();
        expect(stand.reiterAbfragen[1]).toBe('?page=1&size=25');

        // Suche fragt den Server erst nach der Pause und beginnt wieder bei Seite 1
        const suche = page.getByRole('textbox', { name: 'E-Mails durchsuchen' });
        await suche.fill('Treppe');
        const gesucht = page.waitForResponse(r => r.url().includes(`/api/emails/reiter/anfrage/${ANFRAGE_ID}?page=0&size=25&q=Treppe`));
        await page.clock.runFor(400);
        await gesucht;
        await expect(zaehler).toHaveText('1 von 1 angezeigt');
        await expect(page.getByRole('button', { name: 'Aufmaß Treppe', exact: true })).toBeVisible();
        await expect(zeile).toBeHidden();
        await designPruefung(page, testInfo, 'emails-tab-suche', { primaerAktion: neueEmail });
        // Ohne Treffer zeigt der Reiter den nächsten Schritt; „Filter zurücksetzen“ holt sofort alles zurück
        await suche.fill('Carport');
        await page.clock.runFor(400);
        await expect(page.getByText('Keine E-Mails gefunden.')).toBeVisible();
        await expect(page.getByText('Suche oder Filter ändern.')).toBeVisible();
        const zuruecksetzen = page.getByRole('button', { name: 'Filter zurücksetzen' });
        await designPruefung(page, testInfo, 'emails-tab-keine-treffer', { primaerAktion: zuruecksetzen });
        await zuruecksetzen.click();
        await expect(suche).toHaveValue('');
        await expect(zaehler).toHaveText('25 von 30 angezeigt');

        // Filter: aktiv in Rose, Pfeiltasten wandern
        const filter = page.getByRole('radiogroup', { name: 'E-Mails filtern' });
        await filter.getByRole('radio', { name: 'Eingang' }).click();
        await expect(filter.getByRole('radio', { name: 'Eingang' })).toHaveAttribute('aria-checked', 'true');
        await expect(zaehler).toHaveText('15 von 15 angezeigt');
        await expect(filter.getByRole('radio', { name: 'Eingang' })).toHaveClass(/bg-rose-50/);
        await expect(page.getByRole('button', { name: 'Aufmaß Treppe' })).toBeHidden();
        await page.keyboard.press('ArrowRight');
        await expect(filter.getByRole('radio', { name: 'Ausgang' })).toBeFocused();
        await expect(page.getByRole('button', { name: 'Aufmaß Treppe' })).toBeVisible();
        await expect(zeile).toBeHidden();
        await designPruefung(page, testInfo, 'emails-tab-filter-ausgang', { primaerAktion: neueEmail });
        await filter.getByRole('radio', { name: 'Alle' }).click();
        // Erst die neu geladene Liste abwarten, sonst springt Tab an den Platzhaltern vorbei
        await expect(zaehler).toHaveText('25 von 30 angezeigt');

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

test.describe('E-Mail-Reiter beim Kunden', () => {
    test('Kunden-Reiter erklärt, woher die E-Mails kommen', async ({ page }, testInfo) => {
        await stubbeApi(page);
        await page.goto(`/kunden?kundeId=${KUNDE_ID}`);

        await expect(page.getByRole('button', { name: BETREFF, exact: true })).toBeVisible();
        await expect(page.getByText('3 von 3 angezeigt')).toBeVisible();
        // Kein Verfassen im Kunden: geschrieben wird im Projekt oder in der Anfrage
        await expect(page.getByRole('button', { name: 'Neue E-Mail' })).toBeHidden();

        await page.getByRole('button', { name: 'Mehr zu: E-Mails des Kunden' }).click();
        const info = page.getByRole('dialog', { name: 'E-Mails des Kunden' });
        await expect(info.getByText(/aus allen Projekten und Anfragen dieses Kunden/)).toBeVisible();
        await expect(info.getByText(/Die finden Sie im E-Mail-Center\./)).toBeVisible();
        await expect(info).toBeInViewport({ ratio: 1 });
        await designPruefung(page, testInfo, 'emails-tab-kunde-info');
        await page.keyboard.press('Escape');
        await expect(info).toBeHidden();
    });
});
