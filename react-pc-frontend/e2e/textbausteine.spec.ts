import type { Page, Route } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';

/**
 * Textbausteine: Karten mit Beispielwerten, Filter „Verwendet in“, Bearbeiten
 * im großen Dialog mit Platzhalter-Chips, Neuanlage und Löschen.
 *
 * /api vollständig gestubbt, fremde Hosts riegelt e2e/hilfen/test.ts ab.
 * DSGVO: nur Dummy-Daten.
 */

function json(route: Route, body: unknown, status = 200) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
}

const AUTH_ME = {
    id: 1, username: 'chefin', displayName: 'Erika Mustermann',
    active: true, roles: ['ADMIN'], admin: true, requiresInitialSetup: false,
};

const PLATZHALTER = [
    { token: '{{Anrede}}', label: 'Anrede', beschreibung: 'Briefanrede', beispiel: 'Sehr geehrter Herr', gruppe: 'Kunde' },
    { token: '{{KUNDENNAME}}', label: 'Kundenname', beschreibung: 'Name bzw. Firma des Kunden', beispiel: 'Max Mustermann', gruppe: 'Kunde' },
    { token: '{{Ansprechpartner}}', label: 'Ansprechpartner', beschreibung: 'Ansprechpartner beim Kunden', beispiel: 'Erika Musterfrau', gruppe: 'Kunde' },
    { token: '{{KUNDENNUMMER}}', label: 'Kundennummer', beschreibung: 'Nummer des Kunden', beispiel: 'K-1001', gruppe: 'Kunde' },
    { token: '{{KUNDENADRESSE}}', label: 'Kundenadresse', beschreibung: 'Anschrift des Kunden', beispiel: 'Musterstraße 1, 12345 Musterstadt', gruppe: 'Kunde' },
    { token: '{{DOKUMENTTYP}}', label: 'Dokumentart', beschreibung: 'Art des Dokuments', beispiel: 'Rechnung', gruppe: 'Dokument' },
    { token: '{{DOKUMENTNUMMER}}', label: 'Dokumentnummer', beschreibung: 'Nummer des Dokuments', beispiel: 'RE-2026-0108', gruppe: 'Dokument' },
    { token: '{{DATUM}}', label: 'Datum', beschreibung: 'Datum des Dokuments', beispiel: '15.03.2026', gruppe: 'Dokument' },
    { token: '{{SEITENZAHL}}', label: 'Seitenzahl', beschreibung: 'Seite im Dokument', beispiel: 'Seite 1 von 2', gruppe: 'Dokument' },
    { token: '{{PROJEKTNUMMER}}', label: 'Projektnummer', beschreibung: 'Nummer des Projekts', beispiel: 'P-2026-017', gruppe: 'Projekt' },
    { token: '{{BAUVORHABEN}}', label: 'Bauvorhaben', beschreibung: 'Bauvorhaben des Projekts', beispiel: 'Neubau Carport', gruppe: 'Projekt' },
    { token: '{{ZAHLUNGSZIEL}}', label: 'Zahlungsziel (Datum)', beschreibung: 'Fälligkeitsdatum', beispiel: '29.03.2026', gruppe: 'Zahlung' },
    { token: '{{ZAHLUNGSZIEL_TAGE}}', label: 'Zahlungsziel (Tage)', beschreibung: 'Tage bis zur Fälligkeit', beispiel: '14', gruppe: 'Zahlung' },
    { token: '{{BEZUGSDOKUMENTTYP}}', label: 'Art des Vorgängers', beschreibung: 'Art des Vorgänger-Dokuments', beispiel: 'Angebot', gruppe: 'Vorgänger-Dokument' },
    { token: '{{BEZUGSDOKUMENTNUMMER}}', label: 'Nummer des Vorgängers', beschreibung: 'Nummer des Vorgänger-Dokuments', beispiel: 'AN-2026-0042', gruppe: 'Vorgänger-Dokument' },
    { token: '{{BEZUGSDOKUMENTDATUM}}', label: 'Datum des Vorgängers', beschreibung: 'Datum des Vorgänger-Dokuments', beispiel: '01.03.2026', gruppe: 'Vorgänger-Dokument' },
];

const GRUPPEN = [
    { gruppe: 'Angebote & Aufträge', dokumenttypen: ['Angebot', 'Nachtragsangebot', 'Auftragsbestätigung'] },
    { gruppe: 'Rechnungen', dokumenttypen: ['Rechnung', 'Teilrechnung', 'Abschlagsrechnung', 'Schlussrechnung'] },
    { gruppe: 'Mahnungen', dokumenttypen: ['Zahlungserinnerung', '1. Mahnung', '2. Mahnung'] },
    { gruppe: 'Korrekturen', dokumenttypen: ['Stornorechnung', 'Gutschrift'] },
];

const LANGER_TEXT = Array.from({ length: 14 }, (_, i) => `<p>Absatz ${i + 1}: Wir führen die Arbeiten am {{BAUVORHABEN}} fachgerecht aus.</p>`).join('');

function grundbestand() {
    return [
        {
            id: 1, name: 'Anschreiben Angebot', typ: 'VORTEXT', beschreibung: 'Einleitung Angebot', sortOrder: 2,
            html: '<p>{{Anrede}} {{Ansprechpartner}},</p><p>vielen Dank für Ihre Anfrage zum Bauvorhaben {{BAUVORHABEN}}.</p>',
            placeholders: ['{{Anrede}}', '{{Ansprechpartner}}', '{{BAUVORHABEN}}'], dokumenttypen: ['Angebot', 'Nachtragsangebot'],
        },
        {
            id: 2, name: 'Zahlungshinweis', typ: 'NACHTEXT', beschreibung: 'Zahlung', sortOrder: 5,
            html: '<p>Bitte überweisen Sie den Betrag bis zum {{Zahlungsziel}} ohne Abzug.</p>',
            placeholders: ['{{Zahlungsziel}}'], dokumenttypen: ['Rechnung', 'Schlussrechnung'],
        },
        {
            id: 3, name: 'Leistungsbeschreibung lang', typ: 'FREITEXT', beschreibung: null, sortOrder: null,
            html: LANGER_TEXT, placeholders: ['{{BAUVORHABEN}}'], dokumenttypen: [],
        },
        {
            id: 4, name: 'Gruß mit altem Platzhalter', typ: 'NACHTEXT', beschreibung: null, sortOrder: null,
            html: '<p>Mit freundlichen Grüßen {{Sachbearbeiter}}</p>', placeholders: ['{{Sachbearbeiter}}'], dokumenttypen: ['1. Mahnung'],
        },
    ];
}

interface Mitschrift {
    gesendet: { methode: string; url: string; body: Record<string, unknown> | null }[];
}

async function stubbeTextbausteine(page: Page): Promise<Mitschrift> {
    const mitschrift: Mitschrift = { gesendet: [] };
    let bestand = grundbestand();
    let naechsteId = 10;
    // Auffang zuerst, die konkreten Antworten danach (die zuletzt registrierte Route gewinnt).
    await page.route('**/api/**', (route) => json(route, []));
    await page.route('**/api/auth/me', (route) => json(route, AUTH_ME));
    await page.route('**/api/notifications/summary', (route) => json(route, { totalCount: 0, categories: [], recentItems: [] }));
    await page.route('**/api/textbausteine/placeholders', (route) => json(route, PLATZHALTER));
    await page.route('**/api/textbausteine/dokumenttyp-gruppen', (route) => json(route, GRUPPEN));
    await page.route('**/api/textbausteine', (route) => {
        const anfrage = route.request();
        if (anfrage.method() === 'POST') {
            const body = anfrage.postDataJSON() as Record<string, unknown>;
            mitschrift.gesendet.push({ methode: 'POST', url: anfrage.url(), body });
            const neu = { ...(body as object), id: naechsteId++ } as (typeof bestand)[number];
            bestand = [...bestand, neu];
            return json(route, neu);
        }
        return json(route, bestand);
    });
    await page.route(/\/api\/textbausteine\/\d+$/, (route) => {
        const anfrage = route.request();
        const id = Number(anfrage.url().split('/').pop());
        if (anfrage.method() === 'DELETE') {
            mitschrift.gesendet.push({ methode: 'DELETE', url: anfrage.url(), body: null });
            bestand = bestand.filter((b) => b.id !== id);
            return route.fulfill({ status: 204 });
        }
        const body = anfrage.postDataJSON() as Record<string, unknown>;
        mitschrift.gesendet.push({ methode: anfrage.method(), url: anfrage.url(), body });
        const gespeichert = { ...(body as object), id } as (typeof bestand)[number];
        bestand = bestand.map((b) => (b.id === id ? gespeichert : b));
        return json(route, gespeichert);
    });
    return mitschrift;
}

test('Textbausteine: nach Dokumentart filtern, Karte öffnen, Platzhalter einfügen und speichern', async ({ page }, testInfo) => {
    const mitschrift = await stubbeTextbausteine(page);
    await page.goto('/textbausteine');

    const anschreiben = page.getByRole('article').filter({ has: page.getByRole('heading', { name: 'Anschreiben Angebot' }) });
    await expect(anschreiben).toContainText('Erika Musterfrau');
    await expect(anschreiben.locator('[data-platzhalter-chip="{{Anrede}}"]')).toContainText('Sehr geehrter Herr');
    // Unbekannte Kürzel bleiben sichtbar, als grauer Chip.
    await expect(page.locator('[data-platzhalter-chip="{{Sachbearbeiter}}"]')).toContainText('Unbekannter Platzhalter');
    await expect(page.getByRole('button', { name: 'Ganzen Text zeigen' })).toBeVisible();
    await designPruefung(page, testInfo, 'textbausteine-uebersicht', {
        primaerAktion: page.getByRole('button', { name: 'Neuer Textbaustein' }),
    });

    await page.getByRole('combobox', { name: 'Verwendet in' }).click();
    await designPruefung(page, testInfo, 'textbausteine-filter-offen');
    await page.getByRole('listbox').getByRole('option', { name: 'Rechnung (1)', exact: true }).click();
    await expect(page).toHaveURL(/dokumentart=Rechnung/);
    await expect(page.getByRole('article')).toHaveCount(1);
    await expect(page.getByText('1 von 4 Textbausteinen')).toBeVisible();

    await page.getByRole('button', { name: 'Textbaustein „Zahlungshinweis“ bearbeiten' }).click();
    const dialog = page.getByRole('dialog', { name: 'Textbaustein bearbeiten' });
    const editor = dialog.locator('.ProseMirror');
    // {{Zahlungsziel}} (gemischte Schreibweise) wird dem Katalog trotzdem zugeordnet.
    await expect(editor.locator('[data-platzhalter-chip="{{Zahlungsziel}}"]')).toContainText('29.03.2026');
    await expect(dialog.getByRole('switch', { name: 'Rechnung', exact: true })).toHaveAttribute('aria-checked', 'true');

    await dialog.getByRole('button', { name: 'Mehr zu: Platzhalter' }).click();
    await expect(page.getByText('Gespeichert wird die Lücke, nicht das Beispiel.', { exact: false })).toBeVisible();
    await designPruefung(page, testInfo, 'textbausteine-dialog-info', {
        primaerAktion: dialog.getByRole('button', { name: 'Speichern' }),
    });
    await dialog.getByRole('button', { name: 'Mehr zu: Platzhalter' }).click();

    // Rechts neben die Zeile klicken: Cursor ans Zeilenende, nicht auf einen Chip.
    const zeile = editor.locator('p').first();
    const box = await zeile.boundingBox();
    await zeile.click({ position: { x: (box?.width ?? 10) - 4, y: (box?.height ?? 10) / 2 } });
    await page.keyboard.press('End');
    await page.keyboard.type(' Kunde: ');
    await dialog.getByRole('button', { name: /^Kundenname/ }).click();
    await expect(editor.locator('[data-platzhalter-chip="{{KUNDENNAME}}"]')).toContainText('Max Mustermann');

    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await expect(dialog).toBeHidden();
    const put = mitschrift.gesendet.find((g) => g.methode === 'PUT');
    expect(put?.url).toContain('/api/textbausteine/2');
    expect(put?.body?.html).toBe('<p>Bitte überweisen Sie den Betrag bis zum {{Zahlungsziel}} ohne Abzug. Kunde: {{KUNDENNAME}}</p>');
    expect(put?.body).toMatchObject({
        name: 'Zahlungshinweis',
        typ: 'NACHTEXT',
        beschreibung: 'Zahlung',
        sortOrder: 5,
        dokumenttypen: ['Rechnung', 'Schlussrechnung'],
        placeholders: ['{{Zahlungsziel}}', '{{KUNDENNAME}}'],
    });
    expect(put?.body?.html).toBe('<p>Bitte überweisen Sie den Betrag bis zum {{Zahlungsziel}} ohne Abzug. Kunde: {{KUNDENNAME}}</p>');
    await expect(page.getByRole('article')).toContainText('Max Mustermann');
});

test('Textbausteine: neuen Textbaustein anlegen und einen löschen', async ({ page }, testInfo) => {
    const mitschrift = await stubbeTextbausteine(page);
    await page.goto('/textbausteine?dokumentart=1.%20Mahnung');
    await expect(page.getByRole('heading', { name: 'Gruß mit altem Platzhalter' })).toBeVisible();

    await page.getByRole('button', { name: 'Neuer Textbaustein' }).click();
    const dialog = page.getByRole('dialog', { name: 'Neuer Textbaustein' });
    await expect(dialog.locator('.ProseMirror [data-platzhalter-chip="{{Anrede}}"]')).toBeVisible();
    // Der Filter stand auf „1. Mahnung“ – die Dokumentart ist vorausgewählt.
    await expect(dialog.getByRole('switch', { name: '1. Mahnung' })).toHaveAttribute('aria-checked', 'true');
    await designPruefung(page, testInfo, 'textbausteine-dialog-neu', {
        primaerAktion: dialog.getByRole('button', { name: 'Speichern' }),
    });

    // Alle 16 Platzhalter sind erreichbar: die Leiste scrollt selbst, ein Verlauf zeigt, dass es weitergeht.
    const leiste = dialog.locator('[data-platzhalter-liste]');
    const vorgaengerDatum = leiste.getByRole('button', { name: /^Datum des Vorgängers/ });
    const scrollt = await leiste.evaluate((el) => el.scrollHeight > el.clientHeight + 4);
    await expect(dialog.locator('[data-weitere-platzhalter]')).toHaveCount(scrollt ? 1 : 0);
    await vorgaengerDatum.scrollIntoViewIfNeeded();
    await expect(vorgaengerDatum).toBeInViewport();
    await expect(dialog.locator('[data-weitere-platzhalter]')).toHaveCount(0);
    await designPruefung(page, testInfo, 'textbausteine-dialog-leiste-unten', {
        primaerAktion: dialog.getByRole('button', { name: 'Speichern' }),
    });
    await vorgaengerDatum.click();
    await expect(dialog.locator('.ProseMirror [data-platzhalter-chip="{{BEZUGSDOKUMENTDATUM}}"]')).toContainText('01.03.2026');

    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await expect(dialog.getByText('Bitte geben Sie einen Namen ein.')).toBeVisible();
    await dialog.getByLabel(/Name/).fill('Erinnerung freundlich');
    await dialog.getByRole('button', { name: 'Ganze Gruppe „Mahnungen“ einschalten' }).click();
    await dialog.getByRole('button', { name: 'Speichern' }).click();
    await expect(dialog).toBeHidden();

    const post = mitschrift.gesendet.find((g) => g.methode === 'POST');
    expect(post?.body).toMatchObject({
        name: 'Erinnerung freundlich',
        typ: 'FREITEXT',
        beschreibung: null,
        // Eine ganze Gruppe kommt in ihrer Reihenfolge dazu.
        dokumenttypen: ['Zahlungserinnerung', '1. Mahnung', '2. Mahnung'],
    });
    expect(post?.body?.placeholders).toEqual(expect.arrayContaining(['{{Anrede}}', '{{Ansprechpartner}}', '{{BEZUGSDOKUMENTDATUM}}']));
    expect(post?.body?.placeholders).toHaveLength(3);
    await expect(page.getByRole('heading', { name: 'Erinnerung freundlich' })).toBeVisible();

    await page.getByRole('button', { name: 'Textbaustein „Gruß mit altem Platzhalter“ bearbeiten' }).click();
    const bearbeiten = page.getByRole('dialog', { name: 'Textbaustein bearbeiten' });
    await bearbeiten.getByRole('button', { name: 'Löschen' }).click();
    const frage = page.getByRole('dialog', { name: 'Textbaustein löschen?' });
    await expect(frage).toContainText('als Standard-Text hinterlegt');
    await frage.getByRole('button', { name: 'Löschen' }).click();
    await expect(bearbeiten).toBeHidden();
    await expect.poll(() => mitschrift.gesendet.filter((g) => g.methode === 'DELETE').map((g) => g.url)).toEqual([
        expect.stringContaining('/api/textbausteine/4'),
    ]);
    await expect(page.getByRole('heading', { name: 'Gruß mit altem Platzhalter' })).toHaveCount(0);
});
