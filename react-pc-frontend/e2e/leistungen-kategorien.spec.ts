import { test, expect } from './hilfen/test';
import { designPruefung } from './hilfen/design';
import { grundbestand, stubbeLeistungenApi } from './hilfen/leistungen';

/**
 * Seite „Leistungen & Kategorien“ (/leistungen): eine Seite für die Ordner
 * der Leistungen, die zugleich die Projekt-Kategorien sind.
 *
 * /api ist vollständig gestubbt (e2e/hilfen/leistungen.ts hält einen kleinen
 * Bestand im Speicher), fremde Hosts riegelt e2e/hilfen/test.ts ab.
 * DSGVO: nur Dummy-Daten.
 */

test('Unterordner direkt im Baum anlegen – Leistungen wandern mit – und darin eine Leistung anlegen', async ({ page }, testInfo) => {
    const stand = await stubbeLeistungenApi(page);
    await page.goto('/leistungen?ordner=2');

    const baum = page.getByRole('tree', { name: 'Ordner' });
    await expect(baum.getByRole('treeitem', { name: /^Geländer, 4 Leistungen, 3 Projekte$/ })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('heading', { name: 'Geländer', level: 2 })).toBeVisible();

    await baum.getByRole('button', { name: 'Unterordner in „Geländer“ anlegen' }).click();
    const eingabe = baum.getByRole('textbox', { name: 'Name des neuen Unterordners in „Geländer“' });
    await expect(eingabe).toBeFocused();
    await expect(baum.getByText('Die 4 Leistungen aus „Geländer“ wandern in den neuen Unterordner.')).toBeVisible();

    // Leerer Name: Hinweis direkt an der Zeile, nichts wird gesendet.
    await eingabe.press('Enter');
    await expect(baum.getByText('Bitte geben Sie dem Ordner einen Namen.')).toBeVisible();
    expect(stand.mitschrift.ordnerAngelegt).toHaveLength(0);

    await eingabe.fill('Edelstahl');
    await eingabe.press('Enter');
    await expect(page).toHaveURL(/ordner=100/);
    expect(stand.mitschrift.ordnerAngelegt[0]).toMatchObject({ bezeichnung: 'Edelstahl', parentId: '2', leistungenMitnehmen: 'true' });
    await expect(page.getByText('Ordner „Edelstahl“ angelegt – 4 Leistungen sind mitgewandert.')).toBeVisible();
    await expect(baum.getByRole('treeitem', { name: /^Edelstahl, 4 Leistungen, 0 Projekte$/ })).toHaveAttribute('aria-selected', 'true');

    // Neue Leistung: übernimmt die Beschreibungen der Ordnerkette als Starttext.
    await page.getByRole('button', { name: 'Neue Leistung' }).click();
    const formular = page.getByRole('region', { name: 'Leistung bearbeiten' });
    await expect(formular.getByText('Geländer nach DIN 18065.')).toBeVisible();
    await formular.getByLabel('Name *').fill('Geländer Edelstahl eckig');
    await formular.getByLabel('Preis in € *').fill('209,90');
    await designPruefung(page, testInfo, 'leistungen-neue-leistung', {
        primaerAktion: formular.getByRole('button', { name: 'Leistung anlegen' }),
    });
    await formular.getByRole('button', { name: 'Leistung anlegen' }).click();

    await expect(page.getByText('Leistung „Geländer Edelstahl eckig“ angelegt.')).toBeVisible();
    expect(stand.mitschrift.leistungAngelegt[0]).toMatchObject({ name: 'Geländer Edelstahl eckig', price: 209.9, folderId: 100, unit: 'LAUFENDE_METER' });
    await expect(page.getByRole('list', { name: 'Leistungen' }).getByRole('button', { name: /^Geländer Edelstahl eckig 209,90/ })).toBeVisible();
});

test('Ordner mit Projekten löschen: Inhalt zieht in einen anderen Ordner um', async ({ page }, testInfo) => {
    const stand = await stubbeLeistungenApi(page);
    await page.goto('/leistungen?ordner=3');
    await expect(page.getByRole('heading', { name: 'Treppen', level: 2 })).toBeVisible();

    await page.getByRole('button', { name: 'Weitere Aktionen für „Treppen“' }).first().click();
    await page.getByRole('menuitem', { name: 'Löschen …' }).click();

    const dialog = page.getByRole('dialog', { name: '„Treppen“ wird noch verwendet' });
    await expect(dialog.getByText('Neubau Mustermann')).toBeVisible();
    await expect(dialog.getByText('Wangentreppe')).toBeVisible();
    await expect(dialog.getByText('24 Arbeitszeiten')).toBeVisible();
    await expect(dialog.getByRole('radio', { name: /In anderen Ordner umziehen/ })).toBeChecked();
    // Treppen hat direkte Leistungen – „Metallbau“ hat Unterordner und ist deshalb gesperrt (mit Grund).
    await expect(dialog.getByRole('radio', { name: /^Metallbau/ })).toBeDisabled();
    await expect(dialog.getByText('Hat Unterordner – Leistungen passen nicht hinein').first()).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Umziehen und löschen' })).toBeDisabled();

    // Andere Einheit → gelber Hinweis
    await dialog.getByRole('radio', { name: /^Holzbau/ }).check();
    await expect(dialog.getByText(/zählt in Quadratmeter/)).toBeVisible();
    await dialog.getByRole('radio', { name: /^Geländer/ }).check();
    await expect(dialog.getByText(/zählt in Quadratmeter/)).toBeHidden();
    await designPruefung(page, testInfo, 'leistungen-ordner-loeschen-umziehen', {
        primaerAktion: dialog.getByRole('button', { name: 'Umziehen und löschen' }),
    });

    await dialog.getByRole('button', { name: 'Umziehen und löschen' }).click();
    await expect(dialog).toBeHidden();
    expect(stand.mitschrift.loeschen).toEqual([{ id: 3, body: { modus: 'UMZIEHEN', zielKategorieId: 2 } }]);
    await expect(page.getByText('„Treppen“ gelöscht – der Inhalt liegt jetzt in „Geländer“.')).toBeVisible();
    await expect(page).toHaveURL(/ordner=2/);
    await expect(page.getByRole('tree', { name: 'Ordner' }).getByRole('treeitem', { name: /^Treppen/ })).toHaveCount(0);
    await expect(page.getByRole('list', { name: 'Leistungen' }).getByRole('button', { name: /^Wangentreppe 4\.800,00/ })).toBeVisible();
});

test('Übersicht aller Ordner führt in die Auswertung eines Ordners und seiner Unterordner', async ({ page }, testInfo) => {
    await stubbeLeistungenApi(page);
    await page.goto('/leistungen');

    const uebersicht = page.getByRole('table', { name: 'Auswertung aller Hauptordner' });
    await expect(uebersicht.getByRole('row', { name: /Metallbau/ })).toContainText('312,5');
    await expect(uebersicht.getByRole('row', { name: /Metallbau/ })).toContainText('1,74');
    await designPruefung(page, testInfo, 'leistungen-uebersicht');

    await uebersicht.getByRole('button', { name: /Metallbau/ }).click();
    await expect(page).toHaveURL(/ordner=1&reiter=auswertung/);
    await expect(page.getByRole('tab', { name: /Auswertung/ })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByText('312,5 Std.')).toBeVisible();
    await expect(page.getByText('1,74 Std.')).toBeVisible();

    const aufteilung = page.getByRole('table', { name: 'Unterordner von „Metallbau“' });
    await expect(aufteilung.getByRole('row', { name: /Geländer/ })).toContainText('250');
    await expect(page.getByRole('heading', { name: 'Stunden nach Menge' })).toBeVisible();

    // Erklärung „Stunden je Einheit“ direkt an der Kennzahl
    await page.getByRole('button', { name: 'Mehr zu: Stunden je Einheit' }).first().click();
    await expect(page.getByRole('dialog', { name: 'Stunden je Einheit' })).toContainText('Offene Projekte zählen noch nicht mit.');
    await page.keyboard.press('Escape');
    await designPruefung(page, testInfo, 'leistungen-auswertung');

    await aufteilung.getByRole('button', { name: /Geländer/ }).click();
    await expect(page).toHaveURL(/ordner=2&reiter=auswertung/);
    await expect(page.getByRole('heading', { name: 'Geländer', level: 2 })).toBeVisible();
});

test('alte Adresse /produktkategorien führt auf „Leistungen & Kategorien“, Menüeintrag unter Dokumente', async ({ page }) => {
    await stubbeLeistungenApi(page);
    await page.goto('/produktkategorien');
    await expect(page).toHaveURL(/\/leistungen$/);
    await expect(page.getByRole('heading', { name: 'Leistungen & Kategorien', level: 1 })).toBeVisible();

    await page.getByRole('button', { name: 'Vorlagen & Stammdaten', exact: true }).click();
    const eintrag = page.getByRole('link', { name: 'Leistungen & Kategorien' });
    await expect(eintrag).toHaveAttribute('href', '/leistungen');
    await expect(page.getByRole('link', { name: 'Projekt-Kategorien' })).toHaveCount(0);
});

test('leeren Ordner löschen: verschwindet sofort, „Rückgängig“ holt ihn zurück, kein DELETE', async ({ page }, testInfo) => {
    const stand = await stubbeLeistungenApi(page);
    await page.clock.install();
    await page.goto('/leistungen?ordner=4');
    const baum = page.getByRole('tree', { name: 'Ordner' });
    await expect(baum.getByRole('treeitem', { name: /^Holzbau/ })).toHaveAttribute('aria-selected', 'true');

    await page.getByRole('button', { name: 'Weitere Aktionen für „Holzbau“' }).first().click();
    await page.getByRole('menuitem', { name: 'Löschen …' }).click();
    await expect(page.getByText('„Holzbau“ gelöscht.')).toBeVisible();
    await expect(baum.getByRole('treeitem', { name: /^Holzbau/ })).toHaveCount(0);
    await expect(page).toHaveURL(/\/leistungen$/);
    await designPruefung(page, testInfo, 'leistungen-ordner-geloescht-rueckgaengig');

    await page.getByRole('button', { name: 'Rückgängig' }).click();
    await expect(baum.getByRole('treeitem', { name: /^Holzbau/ })).toBeVisible();
    await expect(page.getByText('„Holzbau“ ist wieder da.')).toBeVisible();
    // Frist sicher verstreichen lassen: es darf trotzdem nichts gelöscht werden.
    await page.clock.runFor(10_000);
    expect(stand.mitschrift.geloescht).toEqual([]);
});

test('Leistung, die in Dokumenten steckt: Rückfrage mit Liste, „Trotzdem löschen“ löscht', async ({ page }, testInfo) => {
    const stand = await stubbeLeistungenApi(page, grundbestand(), {
        verwendung: {
            13: {
                dokumentAnzahl: 2,
                dokumente: [
                    { id: 77, dokumentNummer: 'AN-2026-031', typ: 'ANGEBOT', datum: '2026-03-02', projektId: 9, bauvorhaben: 'Neubau Mustermann' },
                    { id: 78, dokumentNummer: 'RE-2026-007', typ: 'RECHNUNG', datum: '2026-04-15', projektId: 9, bauvorhaben: 'Neubau Mustermann' },
                ],
            },
        },
    });
    await page.goto('/leistungen?ordner=2');
    const liste = page.getByRole('list', { name: 'Leistungen' });
    await expect(liste.getByRole('button', { name: /^Glasbrüstung 420,00/ })).toBeVisible();
    await liste.getByRole('button', { name: /^Glasbrüstung 420,00/ }).click();
    await page.getByRole('button', { name: '„Glasbrüstung“ löschen' }).click();

    const dialog = page.getByRole('dialog', { name: '„Glasbrüstung“ steckt in 2 Dokumenten' });
    await expect(dialog.getByRole('cell', { name: 'AN-2026-031' })).toBeVisible();
    await expect(dialog.getByRole('cell', { name: 'Rechnung' })).toBeVisible();
    await expect(dialog.getByRole('cell', { name: '15.04.2026' })).toBeVisible();
    await designPruefung(page, testInfo, 'leistungen-leistung-in-dokumenten', {
        primaerAktion: dialog.getByRole('button', { name: 'Trotzdem löschen' }),
    });
    await dialog.getByRole('button', { name: 'Trotzdem löschen' }).click();

    await expect(dialog).toBeHidden();
    await expect(page.getByText('„Glasbrüstung“ gelöscht.')).toBeVisible();
    expect(stand.mitschrift.geloescht).toEqual(['/api/leistungen/13']);
    await expect(liste.getByRole('button', { name: /^Glasbrüstung/ })).toHaveCount(0);
});

test('Ordner mit Bezügen samt Inhalt löschen', async ({ page }) => {
    const stand = await stubbeLeistungenApi(page);
    await page.goto('/leistungen?ordner=3');
    await expect(page.getByRole('heading', { name: 'Treppen', level: 2 })).toBeVisible();
    await page.getByRole('button', { name: 'Weitere Aktionen für „Treppen“' }).first().click();
    await page.getByRole('menuitem', { name: 'Löschen …' }).click();

    const dialog = page.getByRole('dialog', { name: '„Treppen“ wird noch verwendet' });
    await dialog.getByRole('radio', { name: /Ordner und Inhalt löschen/ }).check();
    await expect(dialog.getByText(/Löscht auch 1 Leistung\. 2 Projekte verlieren diese Kategorie/)).toBeVisible();
    await expect(dialog.getByRole('button', { name: 'Umziehen und löschen' })).toHaveCount(0);
    await dialog.getByRole('button', { name: 'Endgültig löschen' }).click();

    await expect(dialog).toBeHidden();
    expect(stand.mitschrift.loeschen).toEqual([{ id: 3, body: { modus: 'ALLES' } }]);
    await expect(page.getByText('„Treppen“ und sein Inhalt wurden gelöscht.')).toBeVisible();
    await expect(page).toHaveURL(/ordner=1/);
    await expect(page.getByRole('tree', { name: 'Ordner' }).getByRole('treeitem', { name: /^Treppen/ })).toHaveCount(0);
});

test('Ordner per „Verschieben nach …“ in einen anderen Ordner legen', async ({ page }, testInfo) => {
    const stand = await stubbeLeistungenApi(page);
    await page.goto('/leistungen?ordner=4');
    const baum = page.getByRole('tree', { name: 'Ordner' });
    await expect(baum.getByRole('treeitem', { name: /^Holzbau/ })).toHaveAttribute('aria-level', '1');
    await page.getByRole('button', { name: 'Weitere Aktionen für „Holzbau“' }).first().click();
    await page.getByRole('menuitem', { name: 'Verschieben nach …' }).click();

    const dialog = page.getByRole('dialog', { name: '„Holzbau“ verschieben' });
    await expect(dialog.getByRole('radio', { name: /^Geländer/ })).toBeDisabled();
    await expect(dialog.getByText('Hier liegen Leistungen – kein Unterordner möglich').first()).toBeVisible();
    await dialog.getByRole('radio', { name: /^Metallbau/ }).check();
    await designPruefung(page, testInfo, 'leistungen-ordner-verschieben', {
        primaerAktion: dialog.getByRole('button', { name: 'Hierhin verschieben' }),
    });
    await dialog.getByRole('button', { name: 'Hierhin verschieben' }).click();

    await expect(dialog).toBeHidden();
    expect(stand.mitschrift.verschoben).toEqual([{ id: 4, parentId: 1 }]);
    await expect(page.getByText('„Holzbau“ liegt jetzt in „Metallbau“.')).toBeVisible();
    await expect(baum.getByRole('treeitem', { name: /^Holzbau/ })).toHaveAttribute('aria-level', '2');
});

test('Ordnerbild wählen und speichern, danach wieder entfernen', async ({ page }, testInfo) => {
    const stand = await stubbeLeistungenApi(page);
    await page.goto('/leistungen?ordner=4&reiter=ordner');
    await expect(page.getByLabel('Name *')).toHaveValue('Holzbau');
    await expect(page.getByRole('img', { name: 'Bild von „Holzbau“' })).toHaveCount(0);

    await page.locator('input[type="file"][accept*="image/webp"]').setInputFiles({
        name: 'carport.png', mimeType: 'image/png',
        buffer: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==', 'base64'),
    });
    await expect(page.getByText('Neues Bild „carport.png“ – wird beim Speichern übernommen.')).toBeVisible();
    await designPruefung(page, testInfo, 'leistungen-ordnerbild-gewaehlt', {
        primaerAktion: page.getByRole('button', { name: 'Speichern', exact: true }),
    });
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect(page.getByText('Ordner „Holzbau“ gespeichert.')).toBeVisible();
    expect(stand.mitschrift.ordnerGeaendert[0].felder).toMatchObject({ bezeichnung: 'Holzbau', bild: '<datei>' });
    await expect(page.getByRole('img', { name: 'Bild von „Holzbau“' })).toBeVisible();

    await page.getByRole('button', { name: 'Bild entfernen' }).click();
    await expect(page.getByText('Das Bild wird beim Speichern entfernt.')).toBeVisible();
    await page.getByRole('button', { name: 'Speichern', exact: true }).click();
    await expect.poll(() => stand.mitschrift.ordnerGeaendert.length).toBe(2);
    expect(stand.mitschrift.ordnerGeaendert[1].felder).toMatchObject({ bezeichnung: 'Holzbau', bildEntfernen: 'true' });
    expect(stand.mitschrift.ordnerGeaendert[1].felder.bild).toBeUndefined();
    await expect(page.getByRole('img', { name: 'Bild von „Holzbau“' })).toHaveCount(0);
});
