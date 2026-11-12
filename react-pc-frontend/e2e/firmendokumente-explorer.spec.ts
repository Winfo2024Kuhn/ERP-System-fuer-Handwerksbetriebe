import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { bildDatei, pdfDatei, stubbeFirmendokumenteApi, tageAbHeute, type DokumentStub, type OrdnerStub } from './hilfen/firmendokumente';

/**
 * Firmendokumente wie ein Explorer-Ordner: Liste oder Vorschau mit Regler,
 * Unterordner rechts, Ordner per Ziehen verschieben und mehrere Dateien auf
 * einmal hochladen. /api ist gestubbt (e2e/hilfen/firmendokumente.ts), nur
 * Dummy-Daten.
 */

const LANGER_NAME = 'Lieferschein Stahlbau Max Mustermann GmbH Treppengeländer Obergeschoss mit Podest und Handlauf';

function explorerBestand(): { ordner: OrdnerStub[]; dokumente: DokumentStub[] } {
    const dok = (id: number, name: string, originalDateiname: string, faelligAm: string | null = null): DokumentStub => ({
        id, ordnerId: 1, name, originalDateiname, groesseBytes: 2_400_000, hochgeladenAm: '2026-11-10T07:30:00', hochgeladenVon: 'Max Mustermann', faelligAm,
    });
    return {
        ordner: [
            { id: 1, name: 'Fotos Baustelle Mustermann', parentId: null },
            { id: 2, name: 'Montag', parentId: 1 },
            { id: 3, name: 'Vormittag', parentId: 2 },
            { id: 4, name: 'Prüfungen', parentId: null },
            // Nur für bestimmte Abteilungen sichtbar – Verschieben hinein fragt nach.
            { id: 5, name: 'Arbeitsschutz', parentId: null, eingeschraenkt: true },
        ],
        dokumente: [
            ...[1, 2, 3, 4, 5, 6].map((n) => dok(10 + n, `Baustelle Foto ${String(n).padStart(2, '0')}`, `IMG_00${40 + n}.JPG`)),
            dok(20, 'Aufmaß Treppengeländer', 'aufmass.pdf'),
            dok(21, LANGER_NAME, 'lieferschein.pdf', tageAbHeute(12)),
            dok(22, 'Stückliste Geländer', 'stueckliste.docx'),
        ],
    };
}

test('Vorschau einschalten, Größe per Regler, Kachel öffnet die Datei – die Wahl bleibt gemerkt', async ({ page }, testInfo) => {
    const stand = await stubbeFirmendokumenteApi(page, explorerBestand());
    await page.goto('/firmendokumente?ordner=1');

    // Liste: der Unterordner steht wie im Explorer vor den Dateien.
    const tabelle = page.getByRole('table', { name: 'Dokumente in Fotos Baustelle Mustermann' });
    await expect(tabelle.getByRole('row').nth(1)).toContainText('Montag');
    await expect(tabelle.getByRole('row').nth(1)).toContainText('Ordner');

    await page.getByRole('radiogroup', { name: 'Ansicht' }).getByRole('radio', { name: 'Vorschau' }).click();
    const kacheln = page.getByRole('list', { name: 'Dokumente in Fotos Baustelle Mustermann' });
    await expect(kacheln.getByRole('listitem')).toHaveCount(10);
    await expect(kacheln.getByRole('button', { name: 'Montag, Ordner, 0 Dokumente' })).toBeVisible();
    await expect(kacheln.locator('img').first()).toHaveJSProperty('complete', true);

    const regler = page.getByRole('slider', { name: 'Größe der Vorschaubilder' });
    const ersteBreite = () => kacheln.getByRole('listitem').first().evaluate((el) => el.getBoundingClientRect().width);
    await regler.focus();
    await page.keyboard.press('End');
    await expect.poll(ersteBreite).toBeGreaterThanOrEqual(320);
    await expect(kacheln.getByRole('button', { name: 'Baustelle Foto 01', exact: true }).locator('img')).toHaveJSProperty('naturalWidth', 48);
    await designPruefung(page, testInfo, 'firmendokumente-vorschau-gross', { primaerAktion: regler });

    await page.keyboard.press('Home');
    await expect.poll(ersteBreite).toBeLessThan(192);
    await expect(kacheln.getByTitle(LANGER_NAME, { exact: true })).toBeVisible();
    await expect.poll(() => stand.vorschaubildAbrufe.length).toBeGreaterThanOrEqual(8);
    // Nur PDFs und Bilder fragen ein Vorschaubild an, die Word-Datei zeigt ihr Symbol.
    expect(stand.vorschaubildAbrufe).not.toContain('/api/firmendokumente/22/vorschaubild');
    await designPruefung(page, testInfo, 'firmendokumente-vorschau-klein');

    await page.getByRole('button', { name: 'Mehr zu: Liste oder Vorschau' }).click();
    await expect(page.getByRole('dialog', { name: 'Liste oder Vorschau' })).toContainText('Ansicht und Größe merkt sich dieser Browser.');
    await designPruefung(page, testInfo, 'firmendokumente-vorschau-info');
    await page.keyboard.press('Escape');

    // Fokus-Ring an erster und letzter Kachel ist ganz zu sehen.
    await kacheln.getByRole('button', { name: 'Montag, Ordner, 0 Dokumente' }).focus();
    await designPruefung(page, testInfo, 'firmendokumente-kachel-fokus-erste');
    await kacheln.getByRole('button', { name: 'Weitere Aktionen für „Stückliste Geländer“' }).focus();
    await expect(kacheln.getByRole('button', { name: 'Weitere Aktionen für „Stückliste Geländer“' })).toHaveCSS('opacity', '1');
    await designPruefung(page, testInfo, 'firmendokumente-kachel-fokus-letzte');

    await kacheln.getByRole('button', { name: 'Baustelle Foto 03', exact: true }).click();
    await expect(page.getByRole('dialog', { name: 'Baustelle Foto 03' })).toBeVisible();
    await page.keyboard.press('Escape');

    await page.reload();
    await expect(page.getByRole('radio', { name: 'Vorschau' })).toHaveAttribute('aria-checked', 'true');
    await expect(page.getByRole('slider', { name: 'Größe der Vorschaubilder' })).toHaveAttribute('aria-valuenow', '96');
});

test('Ordner per Ziehen verschieben – in einen anderen Ordner, als Hauptordner und von rechts in den Baum', async ({ page }, testInfo) => {
    const stand = await stubbeFirmendokumenteApi(page, explorerBestand());
    await page.goto('/firmendokumente?ordner=2');
    const baum = page.getByRole('tree', { name: 'Ordner' });
    const zeile = (name: string) => baum.getByRole('treeitem', { name: new RegExp(`^${name},`) });

    await zeile('Montag').dragTo(zeile('Prüfungen'));
    await expect(page.getByText('„Montag“ nach „Prüfungen“ verschoben.')).toBeVisible();
    expect(stand.aenderungen.at(-1)).toEqual({ methode: 'PUT', pfad: '/ordner/2/verschieben', body: { parentId: 4 } });
    // Die Auswahl bleibt, der Pfad zeigt den neuen Platz.
    await expect(zeile('Montag')).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('navigation', { name: 'Ordnerpfad' })).toContainText('Prüfungen');
    // „Rückgängig“ in der Meldung holt ihn an seinen alten Platz zurück.
    await page.getByRole('button', { name: 'Rückgängig' }).click();
    await expect(page.getByText('„Montag“ ist wieder in „Fotos Baustelle Mustermann“.')).toBeVisible();
    expect(stand.aenderungen.at(-1)).toEqual({ methode: 'PUT', pfad: '/ordner/2/verschieben', body: { parentId: 1 } });
    await expect(page.getByRole('navigation', { name: 'Ordnerpfad' })).toContainText('Fotos Baustelle Mustermann');

    // Auf die Überschrift „Ordner“ gezogen, wird er zum Hauptordner.
    await zeile('Montag').hover();
    await page.mouse.down();
    const kopf = page.locator('[data-ablage="hauptordner"]');
    const box = await kopf.boundingBox();
    if (!box) throw new Error('Kopfzeile nicht gefunden');
    await page.mouse.move(box.x + 20, box.y + box.height / 2, { steps: 8 });
    await expect(kopf).toContainText('Hier ablegen: als Hauptordner');
    await designPruefung(page, testInfo, 'firmendokumente-ordner-ziehen-kopf');
    await page.mouse.up();
    await expect(page.getByText('„Montag“ ist jetzt ein Hauptordner.')).toBeVisible();
    expect(stand.aenderungen.at(-1)).toEqual({ methode: 'PUT', pfad: '/ordner/2/verschieben', body: { parentId: null } });
    await expect(page.getByRole('heading', { name: 'Ordner', level: 2 })).toBeVisible();

    // Ein Unterordner rechts lässt sich auf einen Ordner im Baum ziehen.
    const vormittag = page.getByRole('table', { name: 'Dokumente in Montag' }).getByRole('row', { name: /Vormittag/ });
    await vormittag.dragTo(zeile('Arbeitsschutz'));
    // Arbeitsschutz ist eingeschränkt: Vorher kommt die Rückfrage zur Sichtbarkeit.
    const rueckfrage = page.getByRole('dialog', { name: 'Ordner verschieben?' });
    await expect(rueckfrage).toContainText('„Vormittag“ nach „Arbeitsschutz“ verschieben? Danach sehen ihn nur noch die Abteilungen, die „Arbeitsschutz“ sehen dürfen.');
    await designPruefung(page, testInfo, 'firmendokumente-ordner-rueckfrage', { primaerAktion: rueckfrage.getByRole('button', { name: 'Verschieben' }) });
    await rueckfrage.getByRole('button', { name: 'Verschieben' }).click();
    await expect(page.getByText('„Vormittag“ nach „Arbeitsschutz“ verschoben.')).toBeVisible();
    expect(stand.aenderungen.at(-1)).toEqual({ methode: 'PUT', pfad: '/ordner/3/verschieben', body: { parentId: 5 } });
    await expect(page.getByText('In diesem Ordner liegt noch kein Dokument.', { exact: false })).toBeVisible();
});

test('Mehrere Dateien auf einmal hochladen – ohne Dialog, mit Fortschritt und einer Meldung am Ende', async ({ page }, testInfo) => {
    const stand = await stubbeFirmendokumenteApi(page, explorerBestand());
    // Uploads anhalten, bis der Fortschritt geprüft ist.
    let freigeben = () => {};
    const freigabe = new Promise<void>((weiter) => { freigeben = weiter; });
    await page.route(/\/api\/firmendokumente$/, async (route) => {
        if (route.request().method() === 'POST') await freigabe;
        await route.fallback();
    });
    await page.goto('/firmendokumente?ordner=4');
    await expect(page.getByRole('heading', { name: 'Prüfungen', level: 2 })).toBeVisible();

    await page.getByRole('button', { name: 'Mehr zu: Mehrere Dateien hochladen' }).click();
    await expect(page.getByRole('dialog', { name: 'Mehrere Dateien hochladen' })).toContainText('ohne Nachfrage in diesem Ordner ab');
    await designPruefung(page, testInfo, 'firmendokumente-hochladen-info');
    await page.keyboard.press('Escape');

    const auswahl = page.waitForEvent('filechooser');
    await page.getByRole('button', { name: 'Datei hochladen' }).click();
    await (await auswahl).setFiles([
        bildDatei('IMG_0101.JPG'), bildDatei('IMG_0102.JPG'), bildDatei('IMG_0103.JPG'), pdfDatei('Prüfbericht Leiter 2026.pdf'),
        { name: 'setup.exe', mimeType: 'application/octet-stream', buffer: Buffer.from('MZ') },
    ]);

    await expect(page.getByText('„setup.exe“: Programmdateien (z. B. .exe oder .bat) können aus Sicherheitsgründen nicht abgelegt werden.')).toBeVisible();
    await expect(page.getByRole('dialog', { name: /Datei hochladen/ })).toHaveCount(0);
    await expect(page.getByRole('progressbar', { name: 'Dateien werden hochgeladen' })).toHaveAttribute('aria-valuemax', '4');
    await expect(page.getByText('Lade hoch … 0 von 4')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Datei hochladen' })).toBeDisabled();
    await designPruefung(page, testInfo, 'firmendokumente-mehrere-hochladen');

    // Abbrechen: die vierte Datei wartet noch und geht nicht mehr hoch, die drei laufenden werden fertig.
    await page.getByRole('button', { name: 'Hochladen abbrechen' }).click();
    await expect(page.getByRole('button', { name: 'Wird abgebrochen …' })).toBeDisabled();
    freigeben();
    await expect(page.getByText('3 von 4 abgelegt, Rest abgebrochen.')).toBeVisible();
    await expect(page.getByRole('progressbar')).toHaveCount(0);
    await expect(page.getByRole('table', { name: 'Dokumente in Prüfungen' }).getByRole('row')).toHaveCount(4);
    expect(stand.hochgeladen.map((f) => [f.name, f.ordnerId, f.faelligAm]).sort()).toEqual([
        ['IMG_0101', '4', undefined], ['IMG_0102', '4', undefined], ['IMG_0103', '4', undefined],
    ]);

    // Danach geht ein neuer Stapel ganz normal.
    await expect(page.getByRole('button', { name: 'Datei hochladen' })).toBeEnabled();
    const nochmal = page.waitForEvent('filechooser');
    await page.getByRole('button', { name: 'Datei hochladen' }).click();
    await (await nochmal).setFiles([pdfDatei('Prüfbericht Leiter 2026.pdf'), bildDatei('IMG_0104.JPG')]);
    await expect(page.getByText('2 Dateien in „Prüfungen“ abgelegt.')).toBeVisible();
    await expect(page.getByRole('table', { name: 'Dokumente in Prüfungen' }).getByRole('row')).toHaveCount(6);
});
