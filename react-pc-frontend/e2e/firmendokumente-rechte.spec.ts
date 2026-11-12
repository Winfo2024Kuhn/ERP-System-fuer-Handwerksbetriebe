import type { Locator, Page } from '@playwright/test';
import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { grundbestand, MINI_PNG_BASE64, stubbeFirmendokumenteApi } from './hilfen/firmendokumente';

/**
 * Firmendokumente: alle Dateiarten, Ziehen & Ablegen und Ordnerrechte je Abteilung.
 * /api ist vollständig gestubbt (e2e/hilfen/firmendokumente.ts). Nur Dummy-Daten.
 */

const ABTEILUNGEN = [{ abteilungId: 3, name: 'Werkstatt' }, { abteilungId: 4, name: 'Büro' }];

/** Bestand mit einem eingeschränkten Ordner, den der Angemeldete nur ansehen darf, und einem Bild. */
function bestandMitRechten() {
    const bestand = grundbestand();
    bestand.ordner.push({ id: 6, name: 'Geschäftsleitung', parentId: null, eingeschraenkt: true, darfAendern: false });
    bestand.dokumente.push(
        { id: 13, ordnerId: 6, name: 'Gesellschaftervertrag', originalDateiname: 'vertrag.pdf', groesseBytes: 90_000, hochgeladenAm: '2026-02-01T08:00:00', hochgeladenVon: 'Max Mustermann', faelligAm: null },
        { id: 14, ordnerId: 4, name: 'Foto Fluchtweg', originalDateiname: 'IMG_0042.png', groesseBytes: 70, hochgeladenAm: '2026-02-01T08:00:00', hochgeladenVon: 'Max Mustermann', faelligAm: null },
        { id: 15, ordnerId: 4, name: 'Unterweisung 2026', originalDateiname: 'unterweisung.docx', groesseBytes: 12_000, hochgeladenAm: '2026-02-01T08:00:00', hochgeladenVon: 'Max Mustermann', faelligAm: null },
    );
    return bestand;
}

/** Zieht eine Datei „aus dem Explorer“ auf das Ziel: DataTransfer mit echter Datei im Browser. */
async function dateiAblegen(page: Page, ziel: Locator, name: string) {
    const dataTransfer = await page.evaluateHandle(({ base64, dateiname }) => {
        const bytes = Uint8Array.from(atob(base64), (z) => z.charCodeAt(0));
        const transfer = new DataTransfer();
        transfer.items.add(new File([bytes], dateiname, { type: 'image/png' }));
        return transfer;
    }, { base64: MINI_PNG_BASE64, dateiname: name });
    await ziel.dispatchEvent('dragover', { dataTransfer });
    await ziel.dispatchEvent('drop', { dataTransfer });
}

test('Admin stellt ein, welche Abteilung einen Ordner sehen darf', async ({ page }, testInfo) => {
    const stand = await stubbeFirmendokumenteApi(page, grundbestand(), { admin: true, abteilungen: ABTEILUNGEN });
    await page.goto('/firmendokumente');
    const baum = page.getByRole('tree', { name: 'Ordner' });
    await baum.getByRole('button', { name: 'Weitere Aktionen für „Prüfungen“' }).click();
    await page.getByRole('menuitem', { name: 'Wer darf das sehen?' }).click();

    const dialog = page.getByRole('dialog', { name: 'Wer darf „Prüfungen“ sehen?' });
    await expect(dialog.getByRole('radio', { name: 'Alle Mitarbeiter (sehen und ändern)' })).toHaveAttribute('aria-checked', 'true');
    await dialog.getByRole('radio', { name: 'Nur bestimmte Abteilungen' }).click();
    await expect(dialog.getByText('Nur Admins sehen diesen Ordner.')).toBeVisible();
    await dialog.getByRole('radiogroup', { name: 'Recht für „Werkstatt“' }).getByRole('radio', { name: 'Ansehen' }).click();
    await dialog.getByRole('radiogroup', { name: 'Recht für „Büro“' }).getByRole('radio', { name: 'Ändern' }).click();
    await expect(dialog.getByText('Nur Admins sehen diesen Ordner.')).toBeHidden();

    await dialog.getByRole('button', { name: 'Mehr zu: Wer darf das sehen?' }).click();
    await expect(page.getByRole('dialog', { name: 'Wer darf das sehen?' }).getByText(/Admins sehen und ändern immer alles/)).toBeVisible();
    await designPruefung(page, testInfo, 'firmendokumente-rechte-info');
    await page.keyboard.press('Escape');
    await designPruefung(page, testInfo, 'firmendokumente-rechte', { primaerAktion: dialog.getByRole('button', { name: 'Speichern' }) });
    await dialog.getByRole('button', { name: 'Speichern' }).click();

    await expect(dialog).toBeHidden();
    await expect(page.getByText('„Prüfungen“ ist jetzt nur für bestimmte Abteilungen sichtbar.')).toBeVisible();
    await expect(baum.getByRole('treeitem', { name: /^Prüfungen, 0 Dokumente, nur für bestimmte Abteilungen sichtbar/ })).toBeVisible();
    expect(stand.aenderungen).toEqual([{
        methode: 'PUT', pfad: '/ordner/1/rechte',
        body: { eingeschraenkt: true, abteilungen: [{ abteilungId: 4, stufe: 'AENDERN' }, { abteilungId: 3, stufe: 'ANSEHEN' }] },
    }]);
    // Unterordner haben keinen eigenen Rechte-Knopf.
    await baum.getByRole('treeitem', { name: /^Prüfungen,/ }).press('ArrowRight');
    await baum.getByRole('button', { name: 'Weitere Aktionen für „Tore“' }).click();
    await expect(page.getByRole('menuitem', { name: 'Umbenennen' })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: 'Wer darf das sehen?' })).toHaveCount(0);
});

test('Nur-Ansehen-Ordner: kein Hochladen, kein Ablegen – Bild und andere Dateien ansehen bzw. herunterladen', async ({ page }, testInfo) => {
    await stubbeFirmendokumenteApi(page, bestandMitRechten());
    await page.goto('/firmendokumente?ordner=6');
    const baum = page.getByRole('tree', { name: 'Ordner' });
    await expect(baum.getByRole('treeitem', { name: /^Geschäftsleitung, 1 Dokument, nur für bestimmte Abteilungen sichtbar/ })).toBeVisible();
    await expect(page.getByText('Dieser Ordner lässt sich nur ansehen.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Datei hochladen' })).toHaveCount(0);
    await expect(baum.getByRole('button', { name: 'Weitere Aktionen für „Geschäftsleitung“' })).toHaveCount(0);
    const liste = page.getByRole('table', { name: 'Dokumente in Geschäftsleitung' });
    await expect(liste.getByRole('button', { name: '„Gesellschaftervertrag“ drucken' })).toBeVisible();
    await liste.getByRole('button', { name: 'Weitere Aktionen für „Gesellschaftervertrag“' }).click();
    await expect(page.getByRole('menuitem', { name: 'Ansehen' })).toBeVisible();
    await expect(page.getByRole('menuitem', { name: 'Löschen …' })).toHaveCount(0);
    await page.keyboard.press('Escape');
    await designPruefung(page, testInfo, 'firmendokumente-nur-ansehen');

    await baum.getByRole('treeitem', { name: /^Arbeitsschutz,/ }).click();
    const arbeitsschutz = page.getByRole('table', { name: 'Dokumente in Arbeitsschutz' });
    await expect(arbeitsschutz.getByRole('button', { name: '„Foto Fluchtweg“ drucken' })).toHaveCount(0);
    await arbeitsschutz.getByRole('button', { name: 'Foto Fluchtweg', exact: true }).click();
    const bild = page.getByRole('img', { name: 'Foto Fluchtweg' });
    await expect(bild).toBeVisible();
    await expect(bild).toHaveAttribute('src', '/api/firmendokumente/14/datei');
    await page.keyboard.press('Escape');
    await expect(bild).toBeHidden();

    const download = page.waitForEvent('download');
    await arbeitsschutz.getByRole('button', { name: 'Unterweisung 2026', exact: true }).click();
    expect((await download).suggestedFilename()).toBe('Unterweisung 2026.docx');
});

test('Bild per Ziehen auf einen Ordner ablegen und ein Dokument auf einen anderen Ordner ziehen', async ({ page }) => {
    const stand = await stubbeFirmendokumenteApi(page, bestandMitRechten());
    await page.goto('/firmendokumente?ordner=2');
    const baum = page.getByRole('tree', { name: 'Ordner' });
    const tore = page.getByRole('table', { name: 'Dokumente in Tore' });
    await expect(tore.getByText('Torprüfung Halle 1 – 2025')).toBeVisible();

    // Auf einen Nur-Ansehen-Ordner geht nichts.
    await dateiAblegen(page, baum.getByRole('treeitem', { name: /^Geschäftsleitung,/ }), 'Baustelle.png');
    await expect(page.getByRole('dialog', { name: 'Datei hochladen' })).toHaveCount(0);

    await dateiAblegen(page, baum.getByRole('treeitem', { name: /^Leitern,/ }), 'Baustelle Mustermann.png');
    const dialog = page.getByRole('dialog', { name: 'Datei hochladen' });
    await expect(dialog.getByLabel('Name *')).toHaveValue('Baustelle Mustermann');
    await expect(dialog.getByText('Kommt in: Prüfungen › Leitern')).toBeVisible();
    await expect(dialog.getByRole('combobox')).toHaveCount(0);
    await dialog.getByRole('button', { name: 'Hochladen' }).click();
    await expect(page.getByText('„Baustelle Mustermann“ abgelegt.')).toBeVisible();
    expect(stand.hochgeladen).toEqual([{ datei: '<datei>', ordnerId: '3', name: 'Baustelle Mustermann' }]);
    expect(stand.dokumente.find((d) => d.name === 'Baustelle Mustermann')?.originalDateiname).toBe('Baustelle Mustermann.png');

    // Dokument aus der Liste auf „Arbeitsschutz“ ziehen.
    await tore.getByRole('row', { name: /Torprüfung Halle 1 – 2025/ }).dragTo(baum.getByRole('treeitem', { name: /^Arbeitsschutz,/ }));
    await expect(page.getByText('„Torprüfung Halle 1 – 2025“ nach „Arbeitsschutz“ verschoben.')).toBeVisible();
    await expect(page.getByText('In diesem Ordner liegt noch kein Dokument.')).toBeVisible();
    expect(stand.aenderungen.at(-1)).toEqual({
        methode: 'PUT', pfad: '/10', body: { name: 'Torprüfung Halle 1 – 2025', ordnerId: 4, faelligAm: stand.dokumente.find((d) => d.id === 10)?.faelligAm },
    });
});

test('Aus einem eingeschränkten Ordner bietet die Seite Nicht-Admins keinen anderen Hauptordner an', async ({ page }) => {
    const bestand = grundbestand();
    // Personal ist eingeschränkt, der Angemeldete darf dort aber ändern.
    bestand.ordner.push(
        { id: 7, name: 'Personal', parentId: null, eingeschraenkt: true, darfAendern: true },
        { id: 8, name: 'Verträge', parentId: 7 },
        { id: 9, name: 'Alt', parentId: 7 },
    );
    bestand.dokumente.push({ id: 16, ordnerId: 8, name: 'Arbeitsvertrag Max Mustermann', originalDateiname: 'vertrag.pdf', groesseBytes: 9_000, hochgeladenAm: '2026-02-01T08:00:00', hochgeladenVon: 'Max Mustermann', faelligAm: null });
    const stand = await stubbeFirmendokumenteApi(page, bestand);
    await page.goto('/firmendokumente?ordner=8');
    const baum = page.getByRole('tree', { name: 'Ordner' });
    const liste = page.getByRole('table', { name: 'Dokumente in Verträge' });
    await expect(liste.getByText('Arbeitsvertrag Max Mustermann')).toBeVisible();

    // Verschieben-Dialog: andere Hauptordner gesperrt, „ganz nach oben“ und der eigene Zweig frei.
    await baum.getByRole('button', { name: 'Weitere Aktionen für „Verträge“' }).click();
    await page.getByRole('menuitem', { name: 'Verschieben …' }).click();
    const verschieben = page.getByRole('dialog', { name: '„Verträge“ verschieben' });
    await expect(verschieben.getByRole('radio', { name: /^Prüfungen/ })).toBeDisabled();
    await expect(verschieben.getByText('Nur ein Admin kann in einen anderen Hauptordner verschieben').first()).toBeVisible();
    await expect(verschieben.getByRole('radio', { name: /^Oberste Ebene/ })).toBeEnabled();
    await expect(verschieben.getByRole('radio', { name: /^Alt/ })).toBeEnabled();
    await verschieben.getByRole('button', { name: 'Mehr zu: Ordner verschieben' }).click();
    await expect(page.getByRole('dialog', { name: 'Ordner verschieben' }).getByText(/sonst könnten Dokumente versehentlich für alle sichtbar werden/)).toBeVisible();
    await page.keyboard.press('Escape');
    await verschieben.getByRole('button', { name: 'Abbrechen' }).click();

    // Bearbeiten: kein Ordner zur Auswahl – verschoben wird nur per Ziehen.
    await liste.getByRole('button', { name: 'Weitere Aktionen für „Arbeitsvertrag Max Mustermann“' }).click();
    await page.getByRole('menuitem', { name: 'Bearbeiten …' }).click();
    const bearbeiten = page.getByRole('dialog', { name: 'Dokument bearbeiten' });
    await expect(bearbeiten.getByText(/^Liegt in: Personal › Verträge\. Zum Verschieben/)).toBeVisible();
    await expect(bearbeiten.getByRole('combobox')).toHaveCount(0);
    await bearbeiten.getByRole('button', { name: 'Abbrechen' }).click();
    await expect(bearbeiten).toBeHidden();

    // Ziehen auf einen anderen Hauptordner: Hinweis statt Verschieben.
    await liste.getByRole('row', { name: /Arbeitsvertrag Max Mustermann/ }).dragTo(baum.getByRole('treeitem', { name: /^Arbeitsschutz,/ }));
    await expect(page.getByText('Aus einem eingeschränkten Ordner kann nur ein Admin in einen anderen Hauptordner verschieben.')).toBeVisible();
    expect(stand.aenderungen).toEqual([]);
    // Im selben Zweig geht es.
    await liste.getByRole('row', { name: /Arbeitsvertrag Max Mustermann/ }).dragTo(baum.getByRole('treeitem', { name: /^Alt,/ }));
    await expect(page.getByText('„Arbeitsvertrag Max Mustermann“ nach „Alt“ verschoben.')).toBeVisible();
    expect(stand.aenderungen).toEqual([{ methode: 'PUT', pfad: '/16', body: { name: 'Arbeitsvertrag Max Mustermann', ordnerId: 9, faelligAm: null } }]);
});

