import type { Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { erwarteEineZeile } from './hilfen/mailFeld';
import { inhalt } from './hilfen/seite';
import { json, stubbeEmailCenter } from './hilfen/postfaecher';

/**
 * Mail-Textfeld: Tabelle über das Menü „Tabelle“ einfügen und per Tab füllen,
 * KI-Formulierung lässt die Tabelle stehen, eingefügte Bilder landen als Anhang
 * statt im Text, Bilder aus dem Internet werden mit Hinweis entfernt.
 * Nur Dummy-Daten (DSGVO).
 */

/** 1×1-PNG. */
const PNG_BASE64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

/** Wie das Backend: neuer Text, die Tabelle der Eingabe kommt unverändert zurück. */
async function stubbeKi(page: Page) {
    const anfragen: { body: string; stil?: string }[] = [];
    await page.route('**/api/email/beautify', route => {
        const anfrage = route.request().postDataJSON() as { body: string; stil?: string };
        anfragen.push(anfrage);
        const tabelle = anfrage.body.slice(anfrage.body.indexOf('<table'), anfrage.body.lastIndexOf('</table>') + 8);
        return json(route, { body: `<p>Guten Tag Herr Mustermann,</p><p>anbei die <strong>Positionen</strong>:</p>${tabelle}` });
    });
    return anfragen;
}

/** Eingefügt wie mit Strg+V: echtes ClipboardEvent mit Datei bzw. HTML. */
async function fuegeEin(page: Page, daten: { html?: string; bild?: boolean }, feldName = 'Nachricht') {
    await page.getByRole('textbox', { name: feldName }).evaluate((feld, { html, bild, png }) => {
        const daten = new DataTransfer();
        if (html) daten.setData('text/html', html);
        if (bild) {
            const bytes = Uint8Array.from(atob(png), zeichen => zeichen.charCodeAt(0));
            daten.items.add(new File([bytes], 'image.png', { type: 'image/png' }));
        }
        feld.dispatchEvent(new ClipboardEvent('paste', { clipboardData: daten, bubbles: true, cancelable: true }));
    }, { ...daten, png: PNG_BASE64 });
}

test('Neue Mail: Tabelle einfügen und füllen, KI lässt sie stehen, eingefügtes Bild wird Anhang', async ({ page }, info) => {
    const stub = await stubbeEmailCenter(page);
    const kiAnfragen = await stubbeKi(page);
    let dateinamen: string[] = [];
    await page.route('**/api/emails/send', route => {
        const roh = route.request().postDataBuffer()?.toString('utf8') ?? '';
        dateinamen = Array.from(roh.matchAll(/name="attachments"; filename="([^"]+)"/g), treffer => treffer[1]);
        return route.fallback();
    });
    await page.goto('/emails/inbox/701');
    await page.getByRole('button', { name: 'Neue E-Mail' }).click();

    const feld = page.getByRole('textbox', { name: 'Nachricht' });
    await expect(page.getByRole('toolbar', { name: 'Formatierung' })).toBeVisible();
    await erwarteEineZeile(page);
    await feld.click();
    await page.keyboard.type('hallo, hier die positionen');
    await page.keyboard.press('Enter');

    // Menü „Tabelle“ per Tastatur: Raster auf 2 × 2, Enter fügt ein.
    const tabelleKnopf = page.getByRole('button', { name: 'Tabelle', exact: true });
    await tabelleKnopf.focus();
    await page.keyboard.press('Enter');
    const menue = page.getByRole('menu');
    const raster = menue.getByRole('menuitem', { name: /Tabelle mit .* einfügen/ });
    await expect(raster).toBeFocused();
    await page.keyboard.press('ArrowRight');
    await page.keyboard.press('ArrowDown');
    await expect(menue.getByText('2 × 2')).toBeVisible();
    await designPruefung(page, info, 'email-menue-tabelle');
    await page.keyboard.press('Enter');
    await expect(menue).toHaveCount(0);

    const tabelle = feld.locator('table');
    await expect(tabelle.locator('tr')).toHaveCount(2);
    // Tab springt von Zelle zu Zelle, in der letzten Zelle kommt eine Zeile dazu.
    for (const text of ['Träger', '2 Stk', 'Stütze', '4 Stk']) {
        await page.keyboard.type(text);
        await page.keyboard.press('Tab');
    }
    await expect(tabelle.locator('tr')).toHaveCount(3);
    await expect(feld).toBeFocused();
    await expect(tabelle.locator('td').first()).toHaveCSS('border-top-style', 'solid');

    await tabelleKnopf.click();
    await expect(menue.getByRole('menuitem', { name: 'Zeile darunter' })).toBeVisible();
    await designPruefung(page, info, 'email-menue-tabelle-in-tabelle');
    await menue.getByRole('menuitem', { name: 'Zeile löschen' }).click();
    await expect(tabelle.locator('tr')).toHaveCount(2);
    // Strg+Z holt die gelöschte Zeile zurück, die Tabelle behält ihre Rahmen.
    await page.keyboard.press('ControlOrMeta+z');
    await expect(tabelle.locator('tr')).toHaveCount(3);
    await expect(tabelle).toHaveAttribute('style', 'border-collapse:collapse;width:100%;margin:8px 0');
    await tabelle.locator('tr').last().locator('td').first().click();
    await tabelleKnopf.click();
    await menue.getByRole('menuitem', { name: 'Zeile löschen' }).click();
    await expect(tabelle.locator('tr')).toHaveCount(2);
    await expect(tabelle).toContainText('Stütze');

    // KI: der eigene Text geht als HTML samt Tabelle hin, die Tabelle steht danach unverändert da.
    await page.getByRole('button', { name: 'KI-Optimierung' }).click();
    await menue.getByRole('menuitem', { name: /Förmlicher/ }).click();
    await expect(feld).toContainText('Guten Tag Herr Mustermann,');
    await expect(feld.locator('strong')).toHaveText('Positionen');
    await expect(feld.locator('table tr')).toHaveCount(2);
    await expect(feld.locator('table td').nth(2)).toHaveText('Stütze');
    expect(kiAnfragen).toHaveLength(1);
    expect(kiAnfragen[0].body).toMatch(/^<p>hallo, hier die positionen<\/p><table/);
    expect(kiAnfragen[0].body).toContain('<td style="border:1px solid #cbd5e1;padding:4px 8px;vertical-align:top">Träger</td>');

    // Bild einfügen: kein Bild im Text, sondern ein Anhang mit eigenem Namen.
    await expect(page.getByText('Eingefügte Bilder landen ebenfalls hier.')).toBeVisible();
    await feld.getByText('Guten Tag Herr Mustermann,').click();
    await page.keyboard.press('End');
    await page.keyboard.press('Enter');
    await fuegeEin(page, { bild: true });
    await expect(page.getByText('Bild als Anhang hinzugefügt.')).toBeVisible();
    await expect(page.getByText('Bild-1.png')).toBeVisible();
    await expect(feld.locator('img')).toHaveCount(0);

    // HTML von einer Webseite: Text kommt, das Bild aus dem Internet nicht.
    await fuegeEin(page, { html: '<p>Siehe Katalog <img src="https://example.com/gelaender.jpg"></p>' });
    await expect(page.getByText('1 Bild aus dem Internet wurde nicht übernommen. Bitte als Datei anhängen.')).toBeVisible();
    await expect(feld.locator('p', { hasText: 'Siehe Katalog' })).toBeVisible();
    await expect(feld.locator('table')).not.toContainText('Katalog');
    await expect(feld.locator('img')).toHaveCount(0);

    // Excel: Rahmen stehen dort in Klassen, sie kommen als Inline-Stil in die Mail.
    await page.keyboard.press('Enter');
    await fuegeEin(page, { html: '<style><!--.xl65 {mso-style-parent:style0; border:.5pt solid windowtext;}--></style>'
        + '<table><tr><td class=xl65>Menge</td><td class=xl65>12</td></tr></table>' });
    const excelZelle = feld.locator('td', { hasText: 'Menge' });
    await expect(excelZelle).toHaveAttribute('style', 'border:.5pt solid #000000');
    await expect(excelZelle).toHaveCSS('border-top-style', 'solid');

    await page.getByRole('button', { name: 'Mehr zu: Anhänge' }).click();
    await expect(page.getByText(/kommen nicht in den Text. Das Programm hängt sie hier an/)).toBeVisible();
    await designPruefung(page, info, 'email-anhaenge-info');
    await page.keyboard.press('Escape');

    const anFeld = page.getByRole('textbox', { name: 'Empfänger' });
    await anFeld.fill('max.mustermann@example.org');
    await anFeld.press('Escape');
    await page.getByLabel('Betreff').fill('Positionen Balkongeländer');
    await designPruefung(page, info, 'email-formular-tabelle-anhang');

    await page.getByRole('button', { name: 'E-Mail senden' }).click();
    await page.getByRole('button', { name: 'Trotzdem senden' }).click();
    await expect.poll(() => stub.gesendet.length).toBe(1);
    const body = String(stub.gesendet[0].dto.body);
    expect(body).toContain('<table style="border-collapse:collapse;width:100%;margin:8px 0">');
    expect(body).toContain('Stütze');
    expect(body).not.toContain('<img');
    expect(body).toContain('<td style="border:.5pt solid #000000">Menge</td>');
    expect(dateinamen).toEqual(['Bild-1.png']);
});

test('Abwesenheitsnotiz: Feld ohne Anhänge lehnt Bilder mit Hinweis ab, Text kommt trotzdem', async ({ page }) => {
    await stubbeEmailCenter(page);
    await page.route('**/api/email/**', route => {
        const pfad = new URL(route.request().url()).pathname;
        if (pfad === '/api/email/signatures/default') return route.fulfill({ status: 204, body: '' });
        if (pfad === '/api/email/outofoffice/postfaecher') {
            return json(route, [{ id: 7, emailAdresse: 'max.mustermann@example.com', anzeigename: 'Max Mustermann', eigenes: true }]);
        }
        return json(route, []);
    });
    await page.goto('/email-einstellungen');
    await inhalt(page).getByRole('button', { name: /Abwesenheitsnotizen/ }).click();
    await inhalt(page).getByRole('button', { name: /Erste Abwesenheit planen/ }).click();

    const feld = page.getByRole('textbox', { name: 'Abwesenheitsnachricht' });
    await feld.click();
    await fuegeEin(page, { bild: true, html: '<p>Ab Montag wieder da.</p><img src="https://example.com/logo.png">' }, 'Abwesenheitsnachricht');
    await expect(page.getByText('In dieses Feld lassen sich keine Bilder oder Dateien einfügen.')).toBeVisible();
    await expect(feld).toContainText('Ab Montag wieder da.');
    await expect(feld.locator('img')).toHaveCount(0);
});
