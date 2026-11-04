import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Verlauf im E-Mail-Center mit eingehender Mail (Rose), eigener Antwort (Slate, nicht
 * mehr Grün) und Entwurf (Amber, Papierkorb mit Namen). Ein Foto-Anhang öffnet den
 * ImageViewer als eigenes Fenster: Name = Dateiname, Fokus auf „Schließen“, Entf löscht
 * nichts, Escape schließt nur die Vorschau und gibt den Fokus an den Anhang zurück.
 *
 * /api ist gestubbt, kein Backend; nur Dummy-Daten (DSGVO): Max Mustermann.
 */

const BETREFF = 'Geländer Baustelle Mustermann';
const FOTO = 'Max-Mustermann-Baustelle.jpg';
const POSTFACH = { id: 3, emailAdresse: 'info@musterbetrieb.example', anzeigename: 'Musterbetrieb' };

/** Ein ruhiges Platzhalterbild statt eines echten Fotos. */
const BILD_SVG = `<svg xmlns="http://www.w3.org/2000/svg" width="960" height="600" viewBox="0 0 960 600">
<rect width="960" height="600" fill="#cbd5e1"/><rect x="80" y="380" width="800" height="24" fill="#475569"/>
<rect x="120" y="200" width="16" height="180" fill="#475569"/><rect x="824" y="200" width="16" height="180" fill="#475569"/>
<rect x="120" y="200" width="720" height="14" fill="#475569"/></svg>`;

const anhang = { id: 31, originalFilename: FOTO, mimeType: 'image/jpeg', sizeBytes: 482_000 };

const eingang = {
    id: 961, type: 'EMAIL', subject: BETREFF, sender: 'Max Mustermann',
    fromAddress: '"Max Mustermann" <max@example.com>', recipient: 'info@musterbetrieb.example',
    direction: 'IN', sentAt: '2026-11-03T09:15:00', isRead: true, zuordnungTyp: 'KEINE',
    postfaecher: [POSTFACH], htmlBody: '<p>Guten Tag, anbei ein Foto vom Treppenaufgang.</p>',
    snippet: 'Guten Tag, anbei ein Foto vom Treppenaufgang.', attachments: [anhang],
};

const verlauf = {
    rootEmailId: 961, focusedEmailId: 961, emails: [
        { ...eingang },
        {
            id: 962, subject: `AW: ${BETREFF}`, fromAddress: '"Musterbetrieb" <info@musterbetrieb.example>',
            recipient: '"Max Mustermann" <max@example.com>', direction: 'OUT', sentAt: '2026-11-03T14:30:00',
            htmlBody: '<p>Danke, wir kommen am Freitag zum Aufmaß.</p>', snippet: 'Danke, wir kommen am Freitag zum Aufmaß.',
            attachments: [],
        },
        {
            id: 990, direction: 'OUT', isDraft: true, draftId: 99, recipient: '"Max Mustermann" <max@example.com>',
            subject: `AW: ${BETREFF}`, snippet: 'Angebot folgt bis Freitag', sentAt: '2026-11-04T08:00:00', attachments: [],
        },
    ],
};

async function stubbeApi(page: Page) {
    const loeschAufrufe: string[] = [];
    await page.route('**/api/**', route => {
        const request = route.request();
        const pfad = new URL(request.url()).pathname;
        if (request.method() === 'DELETE' || pfad.includes('/papierkorb') || pfad.includes('/trash')) loeschAufrufe.push(pfad);
        if (pfad === '/api/emails/961/attachments/31') {
            return route.fulfill({ status: 200, contentType: 'image/svg+xml', body: BILD_SVG });
        }
        let body: unknown = [];
        if (pfad === '/api/emails/inbox') body = [eingang];
        else if (pfad === '/api/emails/961/thread') body = verlauf;
        else if (pfad === '/api/emails/961') body = eingang;
        else if (pfad === '/api/emails/stats') body = { inboxCount: 1, sentCount: 1, trashCount: 0, spamCount: 0, unassignedCount: 1 };
        else if (pfad === '/api/emails/absender-postfaecher') body = [{ ...POSTFACH, eigenes: true, hauptpostfach: true, waehlbar: true }];
        return route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) });
    });
    return loeschAufrufe;
}

test('E-Mail-Center: Verlauf in Rose/Slate, Foto-Vorschau als eigenes Fenster', async ({ page }, testInfo) => {
    const loeschAufrufe = await stubbeApi(page);
    await page.goto('/emails/inbox');

    await page.getByText(BETREFF, { exact: true }).first().click();
    const verlaufsBereich = page.getByTestId('email-verlauf');
    const fotoKnopf = verlaufsBereich.getByRole('button', { name: new RegExp(FOTO.replace(/\./g, '\\.')) });
    await expect(fotoKnopf).toBeVisible();
    await expect(verlaufsBereich.getByText('Danke, wir kommen am Freitag zum Aufmaß.')).toBeVisible();

    // Eigene Antwort: Name und Avatar in Slate, nirgends Grün; Entwurf mit beschriftetem Papierkorb
    await expect(verlaufsBereich.getByText('Musterbetrieb', { exact: true })).toHaveClass(/text-slate-700/);
    expect(await verlaufsBereich.locator('[class*="emerald"]').count()).toBe(0);
    const entwurfLoeschen = verlaufsBereich.getByRole('button', { name: 'Entwurf löschen' });
    await expect(entwurfLoeschen).toBeVisible();
    const flaeche = (await entwurfLoeschen.boundingBox())!;
    expect(flaeche.width).toBeGreaterThanOrEqual(24);
    expect(flaeche.height).toBeGreaterThanOrEqual(24);
    await designPruefung(page, testInfo, 'email-center-verlauf', { primaerAktion: fotoKnopf });

    // Foto öffnen: Fenster mit Dateinamen, Fokus auf „Schließen“
    await fotoKnopf.click();
    const vorschau = page.getByRole('dialog', { name: FOTO });
    await expect(vorschau).toBeVisible();
    await expect(page.getByAltText(FOTO)).toBeVisible();
    const schliessen = vorschau.getByRole('button', { name: 'Vorschau schließen' });
    await expect(schliessen).toBeFocused();
    await designPruefung(page, testInfo, 'email-center-foto-vorschau', { primaerAktion: schliessen });

    // Entf löscht bei offener Vorschau nichts, Tab bleibt im Fenster
    await page.keyboard.press('Delete');
    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');
    await expect(vorschau.locator(':focus')).toHaveCount(1);
    await expect(vorschau).toBeVisible();

    // Escape schließt nur die Vorschau, der Fokus steht wieder auf dem Anhang
    await page.keyboard.press('Escape');
    await expect(vorschau).toBeHidden();
    await expect(fotoKnopf).toBeFocused();
    await expect(page.getByText(BETREFF, { exact: true }).first()).toBeVisible();
    expect(loeschAufrufe).toEqual([]);
});
