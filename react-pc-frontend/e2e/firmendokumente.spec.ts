import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { grundbestand, pdfDatei, stubbeFirmendokumenteApi } from './hilfen/firmendokumente';

/**
 * Seite „Firmendokumente“ (/firmendokumente): Firmen-PDFs in Ordnern ablegen,
 * wiederfinden, ansehen und drucken – für jeden angemeldeten PC-Nutzer.
 *
 * /api ist vollständig gestubbt (e2e/hilfen/firmendokumente.ts hält den Bestand
 * im Speicher), angemeldet ist ein Nutzer OHNE Admin-Rechte. Nur Dummy-Daten.
 */

test('Nicht-Admin findet „Firmendokumente“ im Menü und sieht die fälligen Dokumente', async ({ page }, testInfo) => {
    await stubbeFirmendokumenteApi(page);
    await page.goto('/kunden');

    await page.getByRole('button', { name: 'Vorlagen & Stammdaten', exact: true }).click();
    await page.getByRole('link', { name: 'Firmendokumente' }).click();
    await expect(page).toHaveURL(/\/firmendokumente$/);
    await expect(page.getByRole('heading', { name: 'Firmendokumente', level: 1 })).toBeVisible();

    const baum = page.getByRole('tree', { name: 'Ordner' });
    await expect(baum.getByRole('treeitem', { name: 'Übersicht' })).toHaveAttribute('aria-selected', 'true');
    // Roter Punkt: „Prüfungen“ enthält darunter (in „Tore“) ein überfälliges Dokument.
    await expect(baum.getByRole('treeitem', { name: 'Prüfungen, 0 Dokumente, enthält überfällige Dokumente' })).toBeVisible();

    const faellig = page.getByRole('table', { name: 'Fällige Dokumente' });
    await expect(faellig.getByRole('row')).toHaveCount(3);
    await expect(faellig.getByRole('row').nth(1)).toContainText('Torprüfung Halle 1 – 2025');
    await expect(faellig.getByRole('row').nth(1)).toContainText('Prüfungen › Tore');
    await expect(faellig.getByRole('row').nth(1)).toContainText('überfällig');
    await expect(faellig.getByRole('row').nth(2)).toContainText('bald fällig');
    await designPruefung(page, testInfo, 'firmendokumente-uebersicht');

    // Auch aus der Übersicht lässt sich ein Dokument auf einen Ordner ziehen; die Ordner-Spalte zieht nach.
    await faellig.getByRole('row', { name: /Leiterprüfung 2026/ }).dragTo(baum.getByRole('treeitem', { name: /^Arbeitsschutz,/ }));
    await expect(page.getByText('„Leiterprüfung 2026“ nach „Arbeitsschutz“ verschoben.')).toBeVisible();
    await expect(faellig.getByRole('row', { name: /Leiterprüfung 2026/ }).getByRole('cell').nth(2)).toHaveText('Arbeitsschutz');

    await page.getByRole('button', { name: 'Mehr zu: Firmendokumente' }).click();
    const info = page.getByRole('dialog', { name: 'Firmendokumente' });
    await expect(info.getByText(/werden mit der nächtlichen Sicherung mitgesichert/)).toBeVisible();
    await expect(info.getByText(/Wer sich am PC anmeldet, sieht die Firmendokumente/)).toBeVisible();
    await designPruefung(page, testInfo, 'firmendokumente-info');
});

test('Ordner wählen, PDF hochladen und per Suche wiederfinden', async ({ page }, testInfo) => {
    // Eine HiCAD-Datei ohne Endung im Namen und eine mit Endung, beide mit langem Namen (Spalte „Dateityp“).
    const bestand = grundbestand();
    bestand.dokumente.push(
        { id: 13, ordnerId: 2, name: 'Zaunfeld GZ75 Füllung Rolltor Halle 2 Nordseite mit Schlupftür', originalDateiname: 'ZAUNFELD_GZ75', groesseBytes: 268_288, hochgeladenAm: '2026-10-30T07:45:00', hochgeladenVon: 'Max Mustermann', faelligAm: null },
        { id: 14, ordnerId: 2, name: 'Zaunfeld GZ75 Füllung Halle 2 Ausführungszeichnung', originalDateiname: 'ZAUNFELD_GZ75.sza', groesseBytes: 268_288, hochgeladenAm: '2026-10-30T07:46:00', hochgeladenVon: 'Max Mustermann', faelligAm: null },
    );
    const stand = await stubbeFirmendokumenteApi(page, bestand);
    await page.goto('/firmendokumente');

    const baum = page.getByRole('tree', { name: 'Ordner' });
    const pruefungen = baum.getByRole('treeitem', { name: /^Prüfungen,/ });
    await pruefungen.press('ArrowRight');
    await baum.getByRole('treeitem', { name: /^Tore,/ }).click();
    await expect(page).toHaveURL(/ordner=2/);
    const liste = page.getByRole('table', { name: 'Dokumente in Tore' });
    await expect(liste.getByText('Torprüfung Halle 1 – 2025')).toBeVisible();
    await expect(liste.getByRole('columnheader')).toHaveText(['Name', 'Dateityp', 'Fällig am', 'Hochgeladen am', 'Größe', 'Aktionen']);
    const dateitypIn = (zeile: string) => liste.getByRole('row', { name: zeile }).getByRole('cell').nth(1);
    await expect(dateitypIn('Torprüfung Halle 1 – 2025')).toHaveText('PDF');
    await expect(dateitypIn('Zaunfeld GZ75 Füllung Rolltor Halle 2 Nordseite mit Schlupftür')).toHaveText('–');
    await expect(dateitypIn('Zaunfeld GZ75 Füllung Halle 2 Ausführungszeichnung')).toHaveText('SZA');

    const auswahl = page.waitForEvent('filechooser');
    await page.getByRole('button', { name: 'Datei hochladen' }).click();
    await (await auswahl).setFiles(pdfDatei('scan_0042.pdf'));

    const dialog = page.getByRole('dialog', { name: 'Datei hochladen' });
    const name = dialog.getByLabel('Name *');
    await expect(name).toHaveValue('scan_0042');
    await expect(dialog.getByText('Kommt in: Prüfungen › Tore')).toBeVisible();
    await expect(dialog.getByRole('combobox')).toHaveCount(0);
    await name.fill('Torprüfung Halle 2 – 2026');
    await designPruefung(page, testInfo, 'firmendokumente-hochladen', { primaerAktion: dialog.getByRole('button', { name: 'Hochladen' }) });
    await dialog.getByRole('button', { name: 'Hochladen' }).click();

    await expect(dialog).toBeHidden();
    await expect(page.getByText('„Torprüfung Halle 2 – 2026“ abgelegt.')).toBeVisible();
    await expect(liste.getByText('Torprüfung Halle 2 – 2026')).toBeVisible();
    await expect(page.getByText('4 von 4 angezeigt')).toBeVisible();
    expect(stand.hochgeladen).toEqual([{ datei: '<datei>', ordnerId: '2', name: 'Torprüfung Halle 2 – 2026' }]);
    await designPruefung(page, testInfo, 'firmendokumente-ordner', { primaerAktion: page.getByRole('button', { name: 'Datei hochladen' }) });

    await page.getByRole('searchbox', { name: 'Dokumente in allen Ordnern suchen' }).fill('halle 2');
    const treffer = page.getByRole('table', { name: 'Suchtreffer' });
    await expect(treffer.getByRole('row')).toHaveCount(4);
    await expect(treffer.getByRole('columnheader')).toHaveText(['Name', 'Dateityp', 'Fällig am', 'Hochgeladen am', 'Größe', 'Aktionen']);
    const neu = treffer.getByRole('row').nth(1);
    await expect(neu).toContainText('Torprüfung Halle 2 – 2026');
    await expect(neu.getByRole('cell').nth(1)).toHaveText('PDF');
    await designPruefung(page, testInfo, 'firmendokumente-suche');

    // Aus den Suchtreffern per Ziehen verschieben.
    await treffer.getByRole('row', { name: /Ausführungszeichnung/ }).dragTo(baum.getByRole('treeitem', { name: /^Leitern,/ }));
    await expect(page.getByText('„Zaunfeld GZ75 Füllung Halle 2 Ausführungszeichnung“ nach „Leitern“ verschoben.')).toBeVisible();
    expect(stand.aenderungen.at(-1)).toEqual({ methode: 'PUT', pfad: '/14', body: { name: 'Zaunfeld GZ75 Füllung Halle 2 Ausführungszeichnung', ordnerId: 3, faelligAm: null } });

    await treffer.getByRole('button', { name: 'Torprüfung Halle 2 – 2026', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Torprüfung Halle 2 – 2026', level: 3 })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Drucken', exact: true })).toBeEnabled();
});

test('Dokument bearbeiten: umbenennen und Fällig am setzen, per Ziehen verschieben, Datum wieder entfernen', async ({ page }, testInfo) => {
    const stand = await stubbeFirmendokumenteApi(page);
    await page.goto('/firmendokumente?ordner=2');
    const baum = page.getByRole('tree', { name: 'Ordner' });
    const tore = page.getByRole('table', { name: 'Dokumente in Tore' });
    await tore.getByRole('button', { name: 'Weitere Aktionen für „Torprüfung Halle 1 – 2025“' }).click();
    await page.getByRole('menuitem', { name: 'Bearbeiten …' }).click();

    const dialog = page.getByRole('dialog', { name: 'Dokument bearbeiten' });
    await expect(dialog.getByText('Datei: scan_0040.pdf')).toBeVisible();
    await expect(dialog.getByText('Liegt in: Prüfungen › Tore. Zum Verschieben die Zeile oder Kachel auf einen Ordner ziehen.')).toBeVisible();
    await expect(dialog.getByRole('combobox')).toHaveCount(0);
    await dialog.getByLabel('Name *').fill('Torprüfung Halle 1 – 2026');
    await dialog.getByRole('button', { name: 'Fällig am', exact: true }).click();
    await page.getByRole('textbox', { name: 'Datum eingeben' }).fill('31.12.2099');
    await page.keyboard.press('Enter');
    await expect(dialog.getByRole('button', { name: 'Fällig am', exact: true })).toContainText('31.12.2099');
    await designPruefung(page, testInfo, 'firmendokumente-bearbeiten', { primaerAktion: dialog.getByRole('button', { name: 'Speichern' }) });
    await dialog.getByRole('button', { name: 'Speichern' }).click();

    await expect(page.getByText('„Torprüfung Halle 1 – 2026“ gespeichert.')).toBeVisible();
    expect(stand.aenderungen).toEqual([{ methode: 'PUT', pfad: '/10', body: { name: 'Torprüfung Halle 1 – 2026', ordnerId: 2, faelligAm: '2099-12-31' } }]);
    // Nichts mehr überfällig in „Tore“: der rote Punkt ist weg.
    await expect(baum.getByRole('treeitem', { name: 'Tore, 1 Dokument' })).toBeVisible();

    // Verschieben geht nur per Ziehen auf den Baum.
    await tore.getByRole('row', { name: /Torprüfung Halle 1 – 2026/ }).dragTo(baum.getByRole('treeitem', { name: /^Leitern,/ }));
    await expect(page.getByText('„Torprüfung Halle 1 – 2026“ nach „Leitern“ verschoben.')).toBeVisible();
    await expect(page.getByText('In diesem Ordner liegt noch kein Dokument.')).toBeVisible();
    expect(stand.aenderungen[1]).toEqual({ methode: 'PUT', pfad: '/10', body: { name: 'Torprüfung Halle 1 – 2026', ordnerId: 3, faelligAm: '2099-12-31' } });
    await baum.getByRole('treeitem', { name: 'Leitern, 2 Dokumente' }).click();

    const leitern = page.getByRole('table', { name: 'Dokumente in Leitern' });
    const zeile = leitern.getByRole('row', { name: /Torprüfung Halle 1 – 2026/ });
    await expect(zeile).toContainText('31.12.2099');
    await zeile.getByRole('button', { name: 'Weitere Aktionen für „Torprüfung Halle 1 – 2026“' }).click();
    await page.getByRole('menuitem', { name: 'Bearbeiten …' }).click();
    await expect(dialog.getByText(/^Liegt in: Prüfungen › Leitern\./)).toBeVisible();
    await dialog.getByRole('button', { name: 'Datum entfernen' }).click();
    await expect(dialog.getByRole('button', { name: 'Fällig am', exact: true })).toContainText('Kein Datum');
    await dialog.getByRole('button', { name: 'Speichern' }).click();

    await expect(dialog).toBeHidden();
    // Spalten: Name, Dateityp, Fällig am …
    await expect(zeile.getByRole('cell').nth(2)).toHaveText('–');
    expect(stand.aenderungen[2]).toEqual({ methode: 'PUT', pfad: '/10', body: { name: 'Torprüfung Halle 1 – 2026', ordnerId: 3, faelligAm: null } });
});

test('Dokument löschen erst nach Rückfrage', async ({ page }, testInfo) => {
    const stand = await stubbeFirmendokumenteApi(page);
    await page.goto('/firmendokumente?ordner=4');
    const liste = page.getByRole('table', { name: 'Dokumente in Arbeitsschutz' });
    await liste.getByRole('button', { name: 'Weitere Aktionen für „Gefährdungsbeurteilung Werkstatt“' }).click();
    await page.getByRole('menuitem', { name: 'Löschen …' }).click();

    const rueckfrage = page.getByRole('dialog');
    await expect(rueckfrage).toContainText('„Gefährdungsbeurteilung Werkstatt“ wird endgültig gelöscht. Das lässt sich nicht rückgängig machen.');
    await designPruefung(page, testInfo, 'firmendokumente-loeschen', { primaerAktion: rueckfrage.getByRole('button', { name: 'Dokument löschen' }) });
    await rueckfrage.getByRole('button', { name: 'Dokument löschen' }).click();

    await expect(page.getByText('„Gefährdungsbeurteilung Werkstatt“ gelöscht.')).toBeVisible();
    await expect(page.getByText('In diesem Ordner liegt noch kein Dokument.')).toBeVisible();
    await expect(page.getByRole('tree', { name: 'Ordner' }).getByRole('treeitem', { name: 'Arbeitsschutz, 0 Dokumente' })).toBeVisible();
    expect(stand.aenderungen).toEqual([{ methode: 'DELETE', pfad: '/12', body: null }]);
});

test('Ordner anlegen, Unterordner anlegen, umbenennen, verschieben und löschen', async ({ page }, testInfo) => {
    const stand = await stubbeFirmendokumenteApi(page);
    await page.goto('/firmendokumente');
    const baum = page.getByRole('tree', { name: 'Ordner' });
    const menue = async (ordner: string, punkt: string) => {
        await baum.getByRole('button', { name: `Weitere Aktionen für „${ordner}“` }).click();
        await page.getByRole('menuitem', { name: punkt }).click();
    };

    await page.getByRole('button', { name: 'Neuer Ordner' }).click();
    const neu = page.getByRole('dialog', { name: 'Neuer Ordner' });
    await neu.getByLabel('Name des Ordners *').fill('Zertifikate');
    await neu.getByRole('button', { name: 'Ordner anlegen' }).click();
    await expect(page.getByText('Ordner „Zertifikate“ angelegt.')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Zertifikate', level: 2 })).toBeVisible();

    await menue('Zertifikate', 'Unterordner anlegen');
    const unter = page.getByRole('dialog', { name: 'Unterordner anlegen' });
    await expect(unter).toContainText('Der neue Ordner kommt in „Zertifikate“.');
    await unter.getByLabel('Name des Ordners *').fill('Schweißen');
    await unter.getByRole('button', { name: 'Ordner anlegen' }).click();
    await expect(baum.getByRole('treeitem', { name: /^Schweißen,/ })).toHaveAttribute('aria-selected', 'true');

    await menue('Schweißen', 'Umbenennen');
    const umbenennen = page.getByRole('dialog', { name: 'Ordner umbenennen' });
    await umbenennen.getByLabel('Name des Ordners *').fill('Schweißnachweise');
    await umbenennen.getByRole('button', { name: 'Umbenennen' }).click();
    await expect(page.getByText('Ordner heißt jetzt „Schweißnachweise“.')).toBeVisible();

    await menue('Zertifikate', 'Verschieben …');
    const verschieben = page.getByRole('dialog', { name: '„Zertifikate“ verschieben' });
    await expect(verschieben.getByRole('radio', { name: /^Zertifikate/ })).toBeDisabled();
    await expect(verschieben.getByRole('radio', { name: /^Schweißnachweise/ })).toBeDisabled();
    await expect(verschieben.getByRole('radio', { name: /^Oberste Ebene/ })).toBeDisabled();
    await expect(verschieben.getByText('Das ist der Ordner selbst')).toBeVisible();
    await expect(verschieben.getByText('Liegt in diesem Ordner')).toBeVisible();
    await verschieben.getByRole('radio', { name: /^Prüfungen/ }).check();
    await designPruefung(page, testInfo, 'firmendokumente-ordner-verschieben', { primaerAktion: verschieben.getByRole('button', { name: 'Hierhin verschieben' }) });
    await verschieben.getByRole('button', { name: 'Hierhin verschieben' }).click();
    await expect(page.getByText('„Zertifikate“ nach „Prüfungen“ verschoben.')).toBeVisible();
    await expect(page.getByRole('navigation', { name: 'Ordnerpfad' })).toContainText('Prüfungen');

    await menue('Prüfungen', 'Löschen');
    await page.getByRole('dialog').getByRole('button', { name: 'Ordner löschen' }).click();
    await expect(page.getByText('Der Ordner ist nicht leer. Bitte zuerst die Dokumente und Unterordner verschieben oder löschen.')).toBeVisible();
    await expect(baum.getByRole('treeitem', { name: /^Prüfungen,/ })).toBeVisible();

    await menue('Versicherungen', 'Löschen');
    await page.getByRole('dialog').getByRole('button', { name: 'Ordner löschen' }).click();
    await expect(page.getByText('Ordner „Versicherungen“ gelöscht.')).toBeVisible();
    await expect(baum.getByRole('treeitem', { name: /^Versicherungen,/ })).toHaveCount(0);

    expect(stand.aenderungen.map((a) => `${a.methode} ${a.pfad}`)).toEqual([
        'PUT /ordner/101', 'PUT /ordner/100/verschieben', 'DELETE /ordner/1', 'DELETE /ordner/5',
    ]);
    expect(stand.aenderungen[1].body).toEqual({ parentId: 1 });
});

test('PDF herunterladen und drucken', async ({ page }) => {
    const stand = await stubbeFirmendokumenteApi(page);
    await page.goto('/firmendokumente?ordner=2');
    const liste = page.getByRole('table', { name: 'Dokumente in Tore' });

    const download = page.waitForEvent('download');
    await liste.getByRole('button', { name: '„Torprüfung Halle 1 – 2025“ herunterladen' }).click();
    expect((await download).url()).toMatch(/\/api\/firmendokumente\/10\/datei\?download=true$/);

    await liste.getByRole('button', { name: '„Torprüfung Halle 1 – 2025“ drucken' }).click();
    // Gedruckt wird das echte PDF über ein unsichtbares iframe aus den geladenen Bytes.
    await expect(page.locator('iframe[aria-hidden="true"]')).toHaveAttribute('src', /^blob:/);
    // Den Download-Link verarbeitet der Browser selbst (Ereignis oben); die Druck-Anfrage holt das PDF ohne „download“.
    expect(stand.dateiAbrufe).toContain('/api/firmendokumente/10/datei');
});
