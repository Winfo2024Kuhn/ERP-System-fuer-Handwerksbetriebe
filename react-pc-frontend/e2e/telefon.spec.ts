import { test, expect } from './hilfen/test';
import fs from 'node:fs';
import path from 'node:path';
import { designPruefung, keinHorizontalerUeberlauf, uebergaengeAusklingenLassen } from './hilfen/design';
import { inhalt } from './hilfen/seite';
import { isoTag, KANZLEI_BEISPIEL, KUNDE_ERIKA, KUNDE_MAX, LIEFERANT_GMBH, stubbeTelefonApi } from './hilfen/telefon';

/**
 * End-to-End-Tests der Telefon-Anbindung (FRITZ!Box): Anrufliste,
 * Anrufbeantworter, Live-Anruf-Fenster, Einstellungen, Glocke und Rechte.
 * Backend gestubbt (e2e/hilfen/telefon.ts), nur Dummy-Daten.
 */

test.describe('Telefon – Anrufe', () => {
    test('Anrufliste filtern und einen unbekannten Anruf zuordnen', async ({ page }, info) => {
        const stub = await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufe');
        await expect(page.getByRole('heading', { name: 'Anrufe' })).toBeVisible();
        await expect(page.getByRole('tablist')).toHaveCount(0);
        const tabelle = inhalt(page).getByRole('table');
        await expect(tabelle.getByRole('link', { name: 'Max Mustermann' })).toBeVisible();
        await expect(tabelle.getByText('AB Nacht')).toBeVisible();
        await expect(tabelle.getByText('Mögliche Kontakte:')).toBeVisible();
        await expect(inhalt(page).getByText(/Zuletzt abgeholt:/)).toBeVisible();

        await designPruefung(page, info, 'telefon-anrufe', { primaerAktion: page.getByRole('button', { name: 'Jetzt abholen' }) });

        // Filter "Verpasst"
        await page.getByRole('button', { name: 'Verpasst', exact: true }).click();
        await expect(page).toHaveURL(/art=VERPASST/);
        await expect(tabelle.getByRole('row')).toHaveCount(2);

        // Filter "Unbekannt" und dort zuordnen
        await page.getByRole('button', { name: 'Unbekannt', exact: true }).click();
        await expect(tabelle.getByText('Nummer unterdrückt')).toBeVisible();
        const zeile = tabelle.getByRole('row').filter({ hasText: '0931 2222222' });
        await zeile.getByRole('button', { name: 'Zuordnen' }).click();

        const dialog = page.getByRole('dialog', { name: 'Anruf zuordnen' });
        await expect(dialog).toBeVisible();
        await expect(dialog.getByRole('checkbox', { name: /Nummer beim Kontakt merken/ })).toBeChecked();
        await dialog.getByRole('button', { name: /Kunde suchen/ }).click();
        // Im Suchfenster (dort steht die Kundennummer mit dabei).
        await page.getByRole('button', { name: /Erika Mustermann.*K-1008/ }).click();
        await expect(dialog.getByText('Erika Mustermann')).toBeVisible();
        await designPruefung(page, info, 'telefon-zuordnen-dialog');
        await dialog.getByRole('button', { name: 'Zuordnen' }).click();

        await expect(dialog).toBeHidden();
        await expect(zeile.getByRole('link', { name: 'Erika Mustermann' })).toBeVisible();
        const zuordnung = stub.mitschrift.find((m) => m.pfad === '/api/telefon/anrufe/2/zuordnung');
        expect(zuordnung?.body).toEqual({ kundeId: 8, lieferantId: null, steuerberaterId: null, nummerMerken: true, ansprechpartnerId: null });
    });

    test('einen der möglichen Kontakte mit einem Klick übernehmen und Zuordnung aufheben', async ({ page }) => {
        const stub = await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufe');
        const tabelle = inhalt(page).getByRole('table');
        const zeile = tabelle.getByRole('row').filter({ hasText: '0931 3333333' });
        await zeile.getByRole('button', { name: KUNDE_ERIKA.name }).click();
        await expect(zeile.getByRole('link', { name: KUNDE_ERIKA.name })).toBeVisible();
        expect(stub.mitschrift.at(-1)?.body).toEqual({ kundeId: 8, lieferantId: null, steuerberaterId: null, nummerMerken: false, ansprechpartnerId: null });

        await zeile.getByRole('button', { name: /Weitere Aktionen/ }).click();
        await page.getByRole('menuitem', { name: 'Zuordnung aufheben' }).click();
        await expect(zeile.getByRole('button', { name: 'Zuordnen' })).toBeVisible();
    });
});

test.describe('Telefon – Anrufbeantworter', () => {
    test('Nachricht abspielen vermerkt "abgehört" und lässt sich wieder als neu markieren', async ({ page }, info) => {
        const stub = await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufe');
        // Aus der Anrufliste direkt zur Nachricht springen.
        await inhalt(page).getByRole('button', { name: /Nachricht von .* anhören/ }).click();
        await expect(page).toHaveURL(/\/telefon\/anrufbeantworter\?nachricht=11/);
        const eintrag = page.locator('#nachricht-11');
        await expect(eintrag).toHaveAttribute('data-hervorgehoben', 'true');
        await expect(eintrag.getByRole('img', { name: 'Neue Nachricht' })).toBeVisible();
        await expect(page.getByRole('button', { name: 'AB Tag' })).toBeVisible();
        await expect(page.getByRole('button', { name: 'AB Nacht' })).toBeVisible();

        await designPruefung(page, info, 'telefon-anrufbeantworter');

        await eintrag.getByRole('button', { name: /^Abspielen:/ }).click();
        await expect(eintrag.getByText(/^Abgehört von Max Mustermann, heute 12:04/)).toBeVisible();
        await expect(eintrag.getByRole('img', { name: 'Neue Nachricht' })).toHaveCount(0);
        expect(stub.mitschrift.find((m) => m.methode === 'PATCH')?.body).toEqual({ abgehoert: true });

        await eintrag.getByRole('button', { name: /Weitere Aktionen/ }).click();
        await page.getByRole('menuitem', { name: 'Wieder als neu markieren' }).click();
        await expect(eintrag.getByRole('img', { name: 'Neue Nachricht' })).toBeVisible();

        // Chip filtert nach Anrufbeantworter.
        await page.getByRole('button', { name: 'AB Tag' }).click();
        await expect(page.locator('#nachricht-11')).toHaveCount(0);
        await expect(page.locator('#nachricht-12')).toBeVisible();
    });
});

test.describe('Telefon – Tagesfilter', () => {
    test('Anrufe per Kalender auf einen Tag eingrenzen und wieder alle zeigen', async ({ page }, info) => {
        await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufe');
        const tabelle = inhalt(page).getByRole('table');
        await expect(tabelle.getByRole('row')).toHaveCount(6);

        await inhalt(page).getByRole('button', { name: /^Tag filtern/ }).click();
        const kalender = page.getByRole('dialog', { name: /^Tag filtern.*auswählen$/ });
        await expect(kalender).toBeVisible();
        await designPruefung(page, info, 'telefon-tagesfilter-kalender');
        await kalender.getByRole('button', { name: 'Heute' }).click();

        await expect(page).toHaveURL(new RegExp(`tag=${isoTag()}`));
        await expect(tabelle.getByRole('row')).toHaveCount(4);
        await expect(tabelle.getByText('Gestern 09:52')).toHaveCount(0);

        await page.goto(`/telefon/anrufe?tag=${isoTag(1)}`);
        await expect(inhalt(page).getByRole('table').getByRole('row')).toHaveCount(3);
        await inhalt(page).getByRole('button', { name: 'Tagesfilter entfernen' }).click();
        await expect(page).not.toHaveURL(/tag=/);
        await expect(inhalt(page).getByRole('table').getByRole('row')).toHaveCount(6);
        await designPruefung(page, info, 'telefon-tagesfilter-anrufe');
    });

    test('Anrufbeantworter nach Tag filtern; leerer Tag zeigt einen klaren Hinweis', async ({ page }) => {
        await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufbeantworter');
        await expect(page.getByRole('heading', { name: 'Anrufbeantworter' })).toBeVisible();
        const liste = inhalt(page).getByRole('list', { name: 'Nachrichten auf dem Anrufbeantworter' });
        await expect(liste.getByRole('listitem')).toHaveCount(2);

        await inhalt(page).getByRole('button', { name: /^Tag filtern/ }).click();
        await page.getByRole('dialog', { name: /^Tag filtern.*auswählen$/ }).getByRole('button', { name: 'Heute' }).click();
        await expect(liste.getByRole('listitem')).toHaveCount(1);

        await page.goto('/telefon/anrufbeantworter?tag=2020-01-01');
        await expect(inhalt(page).getByText('Keine Nachrichten am 01.01.2020.')).toBeVisible();
    });
});

test.describe('Telefon – Steuerberater', () => {
    const vomSteuerberater = {
        id: 9, zeitpunkt: `${isoTag()}T09:30:00`, art: 'ANGENOMMEN', anrufbeantworter: null, nummer: '0931 444412',
        eigeneNummer: '0931 7654321', dauerMinuten: 5, nameFritzbox: null, zuordnung: 'AUTOMATISCH',
        kontakt: KANZLEI_BEISPIEL, kandidaten: [], sprachnachrichtId: null,
    };

    test('Anrufliste nach Kontaktart filtern; Kanzlei steht mit Schild, aber ohne Link', async ({ page }, info) => {
        await stubbeTelefonApi(page, { zusatzAnrufe: [vomSteuerberater] });
        await page.goto('/telefon/anrufe');
        const tabelle = inhalt(page).getByRole('table');
        await expect(tabelle.getByRole('row')).toHaveCount(7);
        await expect(tabelle.getByText('Kanzlei Beispiel')).toBeVisible();
        await expect(tabelle.getByRole('link', { name: 'Kanzlei Beispiel' })).toHaveCount(0);

        await inhalt(page).getByRole('combobox', { name: 'Kontaktart' }).click();
        await designPruefung(page, info, 'telefon-kontaktart-auswahl');
        await page.getByRole('option', { name: 'Steuerberater' }).click();
        await expect(page).toHaveURL(/kontakt=STEUERBERATER/);
        await expect(tabelle.getByRole('row')).toHaveCount(2);
        await expect(tabelle.getByText('Steuerberater')).toBeVisible();

        await inhalt(page).getByRole('combobox', { name: 'Kontaktart' }).click();
        await page.getByRole('option', { name: 'Lieferanten' }).click();
        await expect(tabelle.getByRole('row')).toHaveCount(2);
        await expect(tabelle.getByText('Mustermann GmbH')).toBeVisible();

        await inhalt(page).getByRole('button', { name: 'Unbekannt', exact: true }).click();
        await expect(inhalt(page).getByRole('combobox', { name: 'Kontaktart' })).toBeDisabled();
    });

    test('unbekannten Anruf einer Kanzlei zuordnen', async ({ page }, info) => {
        const stub = await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufe');
        const zeile = inhalt(page).getByRole('table').getByRole('row').filter({ hasText: '0931 2222222' });
        await zeile.getByRole('button', { name: 'Zuordnen' }).click();
        const dialog = page.getByRole('dialog', { name: 'Anruf zuordnen' });
        await dialog.getByRole('radio', { name: 'Steuerberater' }).click();
        await dialog.getByRole('radio', { name: 'Kanzlei Beispiel' }).click();
        await designPruefung(page, info, 'telefon-zuordnen-steuerberater');
        await dialog.getByRole('button', { name: 'Zuordnen', exact: true }).click();
        await expect(dialog).toBeHidden();
        await expect(zeile.getByText('Kanzlei Beispiel')).toBeVisible();
        const zuordnung = stub.mitschrift.find((m) => m.pfad === '/api/telefon/anrufe/2/zuordnung');
        expect(zuordnung?.body).toEqual({ kundeId: null, lieferantId: null, steuerberaterId: 30, nummerMerken: false, ansprechpartnerId: null });
    });

    test('Nummer beim Ansprechpartner der Kanzlei speichern', async ({ page }, info) => {
        const stub = await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufe');
        const zeile = inhalt(page).getByRole('table').getByRole('row').filter({ hasText: '0931 2222222' });
        await zeile.getByRole('button', { name: 'Zuordnen' }).click();
        const dialog = page.getByRole('dialog', { name: 'Anruf zuordnen' });
        await dialog.getByRole('radio', { name: 'Steuerberater' }).click();
        await dialog.getByRole('radio', { name: 'Kanzlei Beispiel' }).click();
        const personen = dialog.getByRole('radiogroup', { name: 'Ansprechpartner wählen' });
        await expect(personen.getByRole('radio', { name: /Max Muster/ })).toBeDisabled();
        await personen.getByRole('radio', { name: /Erika Beispiel/ }).click();
        await expect(personen.getByRole('radio', { name: /Erika Beispiel/ })).toHaveAttribute('aria-checked', 'true');
        await designPruefung(page, info, 'telefon-zuordnen-ansprechpartner');
        await dialog.getByRole('button', { name: 'Zuordnen', exact: true }).click();
        await expect(dialog).toBeHidden();
        await expect(page.getByText('Nummer bei Erika Beispiel gespeichert.', { exact: false })).toBeVisible();
        await expect(zeile.getByText('Kanzlei Beispiel')).toBeVisible();
        await expect(zeile.getByText('Erika Beispiel')).toBeVisible();
        const zuordnung = stub.mitschrift.find((m) => m.pfad === '/api/telefon/anrufe/2/zuordnung');
        expect(zuordnung?.body).toEqual({ kundeId: null, lieferantId: null, steuerberaterId: 30, nummerMerken: true, ansprechpartnerId: 40 });
    });

    test('Anruf-Fenster zeigt die Kanzlei ohne „Akte öffnen“', async ({ page }) => {
        const stub = await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufe');
        await stub.sendeLive({ verbindungsId: 's1', status: 'KLINGELT', kontakt: KANZLEI_BEISPIEL, nummer: '0931 444412' });
        const fenster = page.getByRole('dialog', { name: 'Kanzlei Beispiel' });
        await expect(fenster.getByText('Christine Beispiel')).toBeVisible();
        await expect(fenster.getByText('Steuerberater')).toBeVisible();
        await expect(fenster.getByRole('button', { name: 'Akte öffnen' })).toHaveCount(0);
        await fenster.getByRole('button', { name: 'Schließen' }).click();
        await expect(fenster).toBeHidden();
    });
});

test.describe('Telefon – Anruf-Fenster', () => {
    test('erscheint bei einem Live-Anruf, stiehlt keinen Fokus und schließt beim Auflegen', async ({ page }, info) => {
        const stub = await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufe');
        const suche = page.getByRole('textbox', { name: /durchsuchen/ });
        await suche.click();
        await suche.pressSequentially('09');

        await stub.sendeLive({ verbindungsId: 'v1', status: 'KLINGELT', kontakt: KUNDE_MAX });
        const fenster = page.getByRole('dialog', { name: 'Max Mustermann' });
        await expect(fenster).toBeVisible();
        await expect(fenster.getByText('ruft an')).toBeVisible();
        await expect(fenster.getByText('Kunden-Nr. K-1007')).toBeVisible();
        // Tippen im Hintergrund geht einfach weiter.
        await expect(suche).toBeFocused();
        await page.keyboard.type('31');
        await expect(suche).toHaveValue('0931');
        await designPruefung(page, info, 'telefon-anruf-fenster');

        await stub.sendeLive({ verbindungsId: 'v1', status: 'IM_GESPRAECH', kontakt: KUNDE_MAX, angenommen: true });
        await expect(fenster.getByText(/Im Gespräch · 00:0\d/)).toBeVisible();

        await stub.sendeLive({ verbindungsId: 'v1', status: 'BEENDET', kontakt: KUNDE_MAX, angenommen: true });
        await expect(fenster).toBeHidden();
    });

    test('unbekannter Anruf: verpasst, Escape schließt, "Akte öffnen" führt zur Kundenakte', async ({ page }) => {
        const stub = await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufe');
        await expect(inhalt(page).getByRole('table')).toBeVisible();

        await stub.sendeLive({ verbindungsId: 'v2', status: 'KLINGELT', nummer: '0931 9999999' });
        const unbekannt = page.getByRole('dialog', { name: 'Unbekannte Nummer' });
        await expect(unbekannt).toBeVisible();
        await expect(unbekannt.getByRole('button', { name: 'Akte öffnen' })).toHaveCount(0);
        await stub.sendeLive({ verbindungsId: 'v2', status: 'BEENDET', nummer: '0931 9999999', angenommen: false });
        await expect(unbekannt.getByText('Verpasst')).toBeVisible();
        await expect(unbekannt).toBeHidden({ timeout: 6000 });

        await stub.sendeLive({ verbindungsId: 'v3', status: 'KLINGELT', kontakt: KUNDE_MAX });
        await expect(page.getByRole('dialog', { name: 'Max Mustermann' })).toBeVisible();
        await page.keyboard.press('Escape');
        await expect(page.getByRole('dialog', { name: 'Max Mustermann' })).toBeHidden();

        await stub.sendeLive({ verbindungsId: 'v4', status: 'KLINGELT', kontakt: KUNDE_MAX });
        await page.getByRole('dialog', { name: 'Max Mustermann' }).getByRole('button', { name: 'Akte öffnen' }).click();
        await expect(page).toHaveURL(/\/kunden\?kundeId=7/);
    });
});

test.describe('Telefon – Anruf-Fenster mit Projekten und Anfragen', () => {
    test('zeigt Ansprechpartner, Adresse, Projekte und Anfragen und springt ins Projekt', async ({ page }, info) => {
        const stub = await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufe');
        await stub.sendeLive({ verbindungsId: 'p1', status: 'KLINGELT', kontakt: KUNDE_MAX });
        const fenster = page.getByRole('dialog', { name: 'Max Mustermann' });
        await expect(fenster.getByText('Erika Mustermann')).toBeVisible();
        await expect(fenster.getByText('Musterweg 1')).toBeVisible();
        await expect(fenster.getByText('12345 Musterstadt')).toBeVisible();
        const projekte = fenster.getByRole('region', { name: 'Projekte' });
        await expect(projekte).toContainText('(5)');
        await expect(projekte.getByRole('button', { name: /Wintergarten Musterweg/ })).toContainText('Auftrags-Nr. 2026-001');
        await expect(fenster.getByRole('region', { name: 'Anfragen' }).getByRole('button', { name: /Gartentor/ })).toContainText('Noch kein Angebot');
        await designPruefung(page, info, 'telefon-anruf-fenster-projekte');

        await projekte.getByRole('button', { name: /Wintergarten Musterweg/ }).click();
        await expect(page).toHaveURL(/\/projekte\?projektId=21/);
        await expect(fenster).toBeHidden();
    });

    test('springt in die Anfrage; Lieferanten zeigen den Vertreter ohne Projekte', async ({ page }) => {
        const stub = await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufe');
        await stub.sendeLive({ verbindungsId: 'l1', status: 'KLINGELT', kontakt: LIEFERANT_GMBH });
        const lieferant = page.getByRole('dialog', { name: 'Mustermann GmbH' });
        await expect(lieferant.getByText('Vertreter')).toBeVisible();
        await expect(lieferant.getByText('Hans Beispiel')).toBeVisible();
        await expect(lieferant.getByRole('region', { name: 'Projekte' })).toHaveCount(0);
        await page.keyboard.press('Escape');

        await stub.sendeLive({ verbindungsId: 'a1', status: 'KLINGELT', kontakt: KUNDE_MAX });
        await page.getByRole('dialog', { name: 'Max Mustermann' }).getByRole('button', { name: /Balkongeländer/ }).click();
        await expect(page).toHaveURL(/\/anfragen\?anfrageId=31/);
    });

    test('Ladefehler: Hinweis im Fenster und Toast, Name und "Akte öffnen" bleiben erreichbar', async ({ page }) => {
        const stub = await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufe');
        await stub.sendeLive({ verbindungsId: 'f1', status: 'KLINGELT', kontakt: KUNDE_ERIKA });
        const fenster = page.getByRole('dialog', { name: 'Erika Mustermann' });
        await expect(fenster.getByRole('alert')).toHaveText('Kunde nicht gefunden');
        await expect(page.getByText('Kunde nicht gefunden')).toHaveCount(2);
        await expect(fenster.getByRole('heading', { name: 'Erika Mustermann' })).toBeInViewport();
        await expect(fenster.getByRole('button', { name: 'Akte öffnen' })).toBeVisible();
    });

    test('kleiner Bildschirm: der Name bleibt oben sichtbar, der Rest ist scrollbar', async ({ page }) => {
        await page.setViewportSize({ width: 1280, height: 720 });
        const stub = await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufe');
        await stub.sendeLive({ verbindungsId: 'k1', status: 'KLINGELT', kontakt: KUNDE_MAX });
        const fenster = page.getByRole('dialog', { name: 'Max Mustermann' });
        await expect(fenster.getByRole('region', { name: 'Projekte' })).toContainText('(5)');
        await expect(fenster.getByRole('heading', { name: 'Max Mustermann' })).toBeInViewport();
        await fenster.getByRole('button', { name: /Gartentor/ }).scrollIntoViewIfNeeded();
        await expect(fenster.getByRole('button', { name: /Gartentor/ })).toBeInViewport();
        await expect(fenster.getByRole('button', { name: 'Akte öffnen' })).toBeInViewport();
    });
});

test.describe('Telefon – Einstellungen und Rechte', () => {
    test('Administrator testet die Verbindung und speichert Nummern und Anrufbeantworter', async ({ page }, info) => {
        const stub = await stubbeTelefonApi(page);
        await page.goto('/einstellungen#telefon');
        await expect(page.getByText('Telefon (FRITZ!Box)')).toBeVisible();
        await page.getByRole('button', { name: /Verbindung testen/ }).click();
        await expect(page.getByText('Verbindung zur FRITZ!Box steht.')).toBeVisible();
        await expect(page.getByRole('checkbox', { name: '0931 1111111' })).not.toBeChecked();
        await page.getByRole('checkbox', { name: '0931 1111111' }).check();
        await designPruefung(page, info, 'telefon-einstellungen', { ganzeSeite: true });

        await page.getByRole('button', { name: /Telefon-Einstellungen speichern/ }).click();
        await expect(page.getByText('Telefon-Einstellungen gespeichert.')).toBeVisible();
        const gespeichert = stub.mitschrift.find((m) => m.methode === 'PUT')?.body as Record<string, unknown>;
        expect(gespeichert.passwort).toBeNull();
        expect(gespeichert.geschaeftsnummern).toEqual(['0931 7654321', '0931 1111111']);
        expect(gespeichert.anrufbeantworter).toEqual([{ index: 0, name: 'AB Tag' }, { index: 1, name: 'AB Nacht' }]);
    });

    test('Glocke zeigt die Spalte "Telefon" und das Menü die Einträge mit Zähler', async ({ page }, info) => {
        await stubbeTelefonApi(page);
        await page.goto('/telefon/anrufbeantworter');
        await expect(page.getByRole('link', { name: /Anrufbeantworter/ })).toContainText('1');
        await page.getByTitle('Benachrichtigungen').click();
        await expect(page.getByText('Telefon', { exact: true }).first()).toBeVisible();
        await expect(page.getByText('Verpasst: Max Mustermann')).toBeVisible();
        // Die Glocke ist ein aufgeklapptes Menü -- es liegt gewollt über dem
        // Seiteninhalt (auch über "Jetzt abholen"). Deshalb hier nur Screenshot
        // und Überlauf-Prüfung statt der Überschneidungs-Prüfung.
        await uebergaengeAusklingenLassen(page);
        const bild = path.join(info.project.outputDir, 'design', `telefon-glocke--${info.project.name}.png`);
        fs.mkdirSync(path.dirname(bild), { recursive: true });
        await page.screenshot({ path: bild });
        await info.attach('design: telefon-glocke', { path: bild, contentType: 'image/png' });
        await keinHorizontalerUeberlauf(page);
        await page.getByText('Verpasst: Max Mustermann').click();
        await expect(page).toHaveURL(/\/telefon\/anrufe\?anruf=1/);
        await expect(inhalt(page).getByRole('row').filter({ hasText: '0931 1234567' })).toHaveAttribute('data-hervorgehoben', 'true');
    });

    test('ohne Telefon-Recht: kein Menüpunkt, kein Reiter, keine Live-Verbindung, nur ein Hinweis', async ({ page }, info) => {
        const liveAnfragen: string[] = [];
        page.on('request', (r) => { if (r.url().includes('/api/telefon/live')) liveAnfragen.push(r.url()); });
        await stubbeTelefonApi(page, { darf: false, admin: false });
        await page.goto('/kunden?kundeId=7');
        await expect(page.getByRole('heading', { name: 'Max Mustermann' })).toBeVisible();
        await expect(page.getByRole('button', { name: /E-Mails/ })).toBeVisible();
        await expect(page.getByRole('button', { name: /^Anrufe/ })).toHaveCount(0);
        // Gemerkte Nummern sieht jeder – entfernen nicht.
        await expect(page.getByText('0931 5555555')).toBeVisible();
        await expect(page.getByRole('button', { name: /Rufnummer .* entfernen/ })).toHaveCount(0);

        await page.getByRole('button', { name: 'Kommunikation' }).click();
        await expect(page.getByRole('link', { name: /E-Mail Center/ })).toBeVisible();
        await expect(page.getByRole('link', { name: /Anrufe|Anrufbeantworter/ })).toHaveCount(0);

        await page.goto('/telefon/anrufe');
        await expect(page.getByText('Anrufe sind für Sie nicht freigeschaltet')).toBeVisible();
        await designPruefung(page, info, 'telefon-ohne-recht');
        expect(liveAnfragen).toEqual([]);
    });
});
