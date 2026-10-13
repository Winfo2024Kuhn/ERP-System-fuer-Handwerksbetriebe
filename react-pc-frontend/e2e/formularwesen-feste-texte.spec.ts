import type { Locator, Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Formularwesen → „Feste Texte“: die Widerrufsbelehrung bearbeiten.
 * /api ist komplett gestubbt, nur Dummy-Daten (DSGVO).
 */

const BASIS = '/api/formulare/rechtstexte';
const ART = `${BASIS}/WIDERRUFSBELEHRUNG`;

const TEXT_FASSUNG_2 = '<h3>Widerrufsrecht</h3><p>Sie haben das Recht, binnen vierzehn Tagen ohne Angabe von Gründen diesen Vertrag zu widerrufen.</p>'
    + '<p>Um Ihr Widerrufsrecht auszuüben, müssen Sie uns ({{FIRMA_KONTAKT}}) mittels einer eindeutigen Erklärung informieren.</p>';
const TEXT_FASSUNG_1 = '<h3>Widerrufsrecht</h3><p>Fassung aus dem September: Sie können den Vertrag binnen vierzehn Tagen widerrufen.</p>';
const STANDARDTEXT = '<h3>Widerrufsbelehrung</h3><p>Standardtext nach dem gesetzlichen Muster.</p>';

/** Ein minimales, gültiges PDF – reicht für die Vorschau. */
const MINI_PDF = '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj '
    + '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 595 842]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF';

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

interface Fassung {
    fassung: number;
    stand: string;
    geaendertAm: string;
    html: string;
}

interface Mitschrift {
    put: string[];
    standard: number;
    vorschau: string[];
}

/**
 * Stub mit Verlauf: Fassung 1 (September) und Fassung 2 (aktuell).
 * @param optionen.putFehler Antwortet auf PUT mit 400 und dieser Meldung.
 */
async function stubApi(page: Page, optionen: { putFehler?: string } = {}) {
    const mitschrift: Mitschrift = { put: [], standard: 0, vorschau: [] };
    const verlauf: Fassung[] = [
        { fassung: 1, stand: '2026-09-15', geaendertAm: '2026-09-15T08:00:00', html: TEXT_FASSUNG_1 },
        { fassung: 2, stand: '2026-10-01', geaendertAm: '2026-10-01T08:00:00', html: TEXT_FASSUNG_2 },
    ];
    const aktuell = () => verlauf[verlauf.length - 1];
    const neueFassung = (html: string) => {
        verlauf.push({ fassung: aktuell().fassung + 1, stand: '2026-10-13', geaendertAm: '2026-10-13T14:05:00', html });
    };
    const kopf = () => {
        const a = aktuell();
        return { art: 'WIDERRUFSBELEHRUNG', bezeichnung: 'Widerrufsbelehrung', fassung: a.fassung, stand: a.stand, standardtext: false, geaendertAm: a.geaendertAm };
    };
    const detail = () => ({
        ...kopf(), html: aktuell().html,
        platzhalter: [
            { token: '{{FIRMA_KONTAKT}}', beschreibung: 'Name, Anschrift, Telefon und E-Mail in einer Zeile', beispiel: 'Max Mustermann GmbH, Musterstraße 1, 12345 Musterstadt, Telefon 01234 56789, E-Mail info@example.de' },
            { token: '{{FIRMENNAME}}', beschreibung: 'Firmenname', beispiel: 'Max Mustermann GmbH' },
            { token: '{{FIRMA_ANSCHRIFT}}', beschreibung: 'Anschrift', beispiel: 'Musterstraße 1, 12345 Musterstadt' },
            { token: '{{FIRMA_TELEFON}}', beschreibung: 'Telefon', beispiel: '01234 56789' },
            { token: '{{FIRMA_EMAIL}}', beschreibung: 'E-Mail', beispiel: 'info@example.de' },
        ],
    });

    await page.route('**/api/**', (route) => {
        const pfad = new URL(route.request().url()).pathname;
        const methode = route.request().method();
        if (pfad === '/api/auth/me') {
            return json(route, { id: 1, username: 'max', displayName: 'Max Mustermann', active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false });
        }
        if (pfad === '/api/notifications/summary') return json(route, { totalCount: 0, categories: [], recentItems: [] });
        if (pfad === BASIS) return json(route, [kopf()]);
        if (pfad === ART && methode === 'GET') return json(route, detail());
        if (pfad === ART && methode === 'PUT') {
            const { html } = route.request().postDataJSON() as { html: string };
            mitschrift.put.push(html);
            if (optionen.putFehler) return json(route, { message: optionen.putFehler }, 400);
            neueFassung(html);
            return json(route, detail());
        }
        if (pfad === `${ART}/standard` && methode === 'POST') {
            mitschrift.standard += 1;
            neueFassung(STANDARDTEXT);
            return json(route, detail());
        }
        if (pfad === `${ART}/vorschau` && methode === 'POST') {
            mitschrift.vorschau.push((route.request().postDataJSON() as { html: string }).html);
            return route.fulfill({ status: 200, contentType: 'application/pdf', body: MINI_PDF });
        }
        if (pfad === `${ART}/fassungen`) {
            return json(route, [...verlauf].reverse().map(({ fassung, stand, geaendertAm }) => ({ fassung, stand, geaendertAm })));
        }
        const alt = /\/fassungen\/(\d+)$/.exec(pfad);
        if (alt) {
            const treffer = verlauf.find(f => f.fassung === Number(alt[1]));
            return treffer ? json(route, treffer) : json(route, { message: 'Diese Fassung gibt es nicht.' }, 404);
        }
        return json(route, []);
    });
    return mitschrift;
}

/** Öffnet Formularwesen und wartet auf die Karte mit den geladenen Daten. */
async function oeffneKarte(page: Page) {
    await page.goto('/formulare');
    const karte = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: 'Widerrufsbelehrung' }) });
    await expect(karte.getByText('Stand 01.10.2026 · Fassung 2')).toBeVisible();
    return karte;
}

/** Klickt „Bearbeiten“ und wartet, bis der Text im Editor steht. */
async function oeffneDialog(page: Page, karte: Locator) {
    await karte.getByRole('button', { name: 'Widerrufsbelehrung bearbeiten' }).click();
    const dialog = page.getByRole('dialog', { name: 'Widerrufsbelehrung' });
    const editor = dialog.getByRole('textbox', { name: 'Text der Widerrufsbelehrung' });
    await expect(editor).toContainText('binnen vierzehn Tagen');
    return { dialog, editor };
}

test('Widerrufsbelehrung bearbeiten: Galerie → Bearbeiten → Text ändern → Vorschau → Speichern → neue Fassung', async ({ page }, testInfo) => {
    const mitschrift = await stubApi(page);
    const karte = await oeffneKarte(page);
    await expect(page.getByRole('heading', { name: 'Feste Texte' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Briefvorlagen' })).toBeVisible();
    await designPruefung(page, testInfo, 'formularwesen-feste-texte-galerie');

    const { dialog, editor } = await oeffneDialog(page, karte);
    await expect(dialog.getByText('Fassung 1 · Stand 15.09.2026')).toBeVisible();
    await expect(dialog.getByText('Das Muster-Widerrufsformular und die Erklärung des Kunden')).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Speichern' })).toBeInViewport();
    await designPruefung(page, testInfo, 'formularwesen-feste-texte-dialog', { strengePruefungen: false });

    // Info zum Online-Widerruf ist ganz lesbar und nicht abgeschnitten.
    await dialog.getByRole('button', { name: 'Mehr zu: Vorschlag: Online-Widerruf' }).click();
    const info = page.getByRole('dialog', { name: 'Vorschlag: Online-Widerruf' });
    await expect(info).toContainText('§ 356a BGB');
    await expect(info).toBeInViewport({ ratio: 1 });
    await page.keyboard.press('Escape');
    await expect(info).toBeHidden();

    // Text ändern: am Ende einen Satz mit Platzhalter ergänzen.
    await editor.getByText('mittels einer eindeutigen Erklärung informieren.').click();
    await page.keyboard.press('End');
    await page.keyboard.type(' Telefon: ');
    await dialog.getByRole('button', { name: /^Telefon/ }).click();
    await expect(editor).toContainText('Telefon: {{FIRMA_TELEFON}}');

    // Vorschau zeigt den ungespeicherten Entwurf; Escape schließt nur die Vorschau.
    await dialog.getByRole('button', { name: 'Vorschau (PDF)' }).click();
    const vorschauTitel = page.getByRole('heading', { name: 'Vorschau: Widerrufsbelehrung' });
    await expect(vorschauTitel).toBeVisible();
    expect(mitschrift.vorschau.at(-1)).toContain('Telefon: {{FIRMA_TELEFON}}');
    await page.keyboard.press('Escape');
    await expect(vorschauTitel).toBeHidden();
    await expect(editor).toContainText('Telefon: {{FIRMA_TELEFON}}');

    const gespeichert = page.waitForResponse(r => r.url().endsWith(ART) && r.request().method() === 'PUT');
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await gespeichert;

    await expect(dialog).toBeHidden();
    await expect(page.getByText('Gespeichert – gilt ab sofort als Fassung 3, Stand 13.10.2026.')).toBeVisible();
    await expect(karte.getByText('Stand 13.10.2026 · Fassung 3')).toBeVisible();
    expect(mitschrift.put).toHaveLength(1);
    expect(mitschrift.put[0]).toContain('informieren. Telefon: {{FIRMA_TELEFON}}</p>');
});

test('frühere Fassung übernehmen und speichern, danach auf Standardtext zurücksetzen', async ({ page }) => {
    const mitschrift = await stubApi(page);
    const karte = await oeffneKarte(page);
    const { dialog, editor } = await oeffneDialog(page, karte);

    // Frühere Fassung ansehen: schreibgeschützt, Speichern solange gesperrt.
    await dialog.getByRole('button', { name: /Fassung 1 · Stand 15\.09\.2026/ }).click();
    await expect(dialog.getByText('Fassung aus dem September')).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Speichern' })).toBeDisabled();

    await dialog.getByRole('button', { name: 'In den Editor übernehmen' }).click();
    await expect(editor).toContainText('Fassung aus dem September');
    await expect(page.getByText('Fassung 1 steht jetzt im Editor – noch nicht gespeichert.')).toBeVisible();

    const gespeichert = page.waitForResponse(r => r.url().endsWith(ART) && r.request().method() === 'PUT');
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await gespeichert;
    await expect(dialog).toBeHidden();
    await expect(karte.getByText('Stand 13.10.2026 · Fassung 3')).toBeVisible();
    expect(mitschrift.put).toHaveLength(1);
    expect(mitschrift.put[0]).toContain('Fassung aus dem September: Sie können den Vertrag binnen vierzehn Tagen widerrufen.');
    expect(mitschrift.put[0]).not.toContain('{{FIRMA_KONTAKT}}');

    // Jetzt zurücksetzen – erst nach Bestätigung geht der Auftrag raus.
    // Fassung 3 enthält „binnen vierzehn Tagen“ ebenfalls – oeffneDialog wartet darauf.
    const neu = await oeffneDialog(page, karte);
    await expect(neu.dialog.getByText('Stand 13.10.2026 · Fassung 3', { exact: true })).toBeVisible();
    await neu.dialog.getByRole('button', { name: 'Auf Standardtext zurücksetzen', exact: true }).click();
    const frage = page.getByRole('dialog', { name: 'Auf Standardtext zurücksetzen?' });
    await expect(frage).toContainText('Ihre bisherigen Fassungen bleiben erhalten.');
    expect(mitschrift.standard).toBe(0);

    const zurueckgesetzt = page.waitForResponse(r => r.url().endsWith(`${ART}/standard`) && r.request().method() === 'POST');
    await frage.getByRole('button', { name: 'Zurücksetzen' }).click();
    await zurueckgesetzt;

    await expect(page.getByText('Standardtext gilt ab sofort als Fassung 4, Stand 13.10.2026.')).toBeVisible();
    await expect(neu.dialog.getByText('Stand 13.10.2026 · Fassung 4', { exact: true })).toBeVisible();
    await expect(neu.editor).toContainText('Standardtext nach dem gesetzlichen Muster.');
    await expect(neu.dialog.getByRole('button', { name: /Fassung 4 · Stand 13\.10\.2026/ })).toBeVisible();
    expect(mitschrift.standard).toBe(1);

    // Dialog schließen: nichts ungespeichert, also keine Rückfrage; die Karte zeigt Fassung 4.
    await neu.dialog.getByRole('button', { name: 'Abbrechen' }).click();
    await expect(neu.dialog).toBeHidden();
    await expect(karte.getByText('Stand 13.10.2026 · Fassung 4')).toBeVisible();
});

test('leerer Text wird abgelehnt, eine 400-Meldung des Servers erscheint im Dialog', async ({ page }) => {
    const mitschrift = await stubApi(page, { putFehler: 'Der Text ist zu lang. Erlaubt sind höchstens 50.000 Zeichen.' });
    const karte = await oeffneKarte(page);
    const { dialog, editor } = await oeffneDialog(page, karte);

    // Alles löschen: das Programm lehnt schon vor dem Senden ab.
    await editor.click();
    await page.keyboard.press('ControlOrMeta+A');
    await page.keyboard.press('Delete');
    await expect(editor).not.toContainText('binnen vierzehn Tagen');
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await expect(dialog.getByRole('alert')).toContainText('Der Text ist leer. Bitte geben Sie einen Text ein.');
    expect(mitschrift.put).toHaveLength(0);

    // Neuer Text, den der Server ablehnt: seine Meldung steht im Dialog, der Dialog bleibt offen.
    // (Nach dem Klick auf „Speichern“ liegt der Fokus auf dem Knopf – erst zurück in den Editor.)
    await editor.click();
    await page.keyboard.type('Widerrufsrecht für Max Mustermann');
    await expect(dialog.getByRole('alert')).toBeHidden();
    const abgelehnt = page.waitForResponse(r => r.url().endsWith(ART) && r.request().method() === 'PUT');
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    expect((await abgelehnt).status()).toBe(400);

    await expect(dialog.getByRole('alert')).toContainText('Der Text ist zu lang. Erlaubt sind höchstens 50.000 Zeichen.');
    await expect(dialog).toBeVisible();
    await expect(editor).toContainText('Widerrufsrecht für Max Mustermann');
    await expect(karte.getByText('Stand 01.10.2026 · Fassung 2')).toBeAttached();
});
