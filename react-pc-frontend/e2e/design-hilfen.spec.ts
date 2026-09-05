import { test, expect } from '@playwright/test';
import { designPruefung, keinHorizontalerUeberlauf, keinTextGekuerzt, keinTextLaeuftUeber } from './hilfen/design';

/**
 * Rote-dann-gruene Specs fuer die drei Design-Pruefungen aus Spec E
 * (siehe docs/superpowers/specs/2026-09-04-layout-14-zoll.md, Abschnitt E).
 * Jede Pruefung wird direkt aufgerufen -- nicht ueber designPruefung -- mit
 * einer Mini-Seite ueber page.setContent(): eine kaputte, die den Fehler
 * zeigt, und eine heile, die ihn nicht zeigt. Kein /api, kein Backend
 * noetig -- die Pruefungen lesen nur das DOM der aktuellen Seite.
 */

test.describe('keinHorizontalerUeberlauf -- Dokument (bestehendes Verhalten bleibt)', () => {
    test('Dokument mit Ueberstand loest aus', async ({ page }) => {
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                .breiter-inhalt { width: 2000px; height: 20px; background: red; }
            </style>
            <div class="breiter-inhalt">zu breit</div>
        `);
        await expect(keinHorizontalerUeberlauf(page)).rejects.toThrow();
    });

    test('Dokument ohne Ueberstand ist unauffaellig', async ({ page }) => {
        await page.setContent(`
            <style> html, body { margin: 0; padding: 0; } </style>
            <div>passt</div>
        `);
        await keinHorizontalerUeberlauf(page);
    });
});

test.describe('keinHorizontalerUeberlauf -- main', () => {
    test('main mit overflow-x:hidden und breiterem Inhalt loest aus', async ({ page }) => {
        // Nachgebauter Befund: MainLayout.tsx hatte overflow-x-hidden auf
        // <main> und versteckte den Ueberstand, ohne dass das Dokument selbst
        // ueberlief.
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                main { display: block; width: 300px; overflow-x: hidden; }
                .breiter-inhalt { width: 500px; height: 20px; background: red; white-space: nowrap; }
            </style>
            <main><div class="breiter-inhalt">zu breiter Inhalt</div></main>
        `);
        await expect(keinHorizontalerUeberlauf(page)).rejects.toThrow();
    });

    test('main ohne Ueberstand ist unauffaellig', async ({ page }) => {
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                main { display: block; width: 300px; overflow-x: hidden; }
                .passender-inhalt { width: 200px; height: 20px; background: green; }
            </style>
            <main><div class="passender-inhalt">passt</div></main>
        `);
        await keinHorizontalerUeberlauf(page);
    });
});

test.describe('keinHorizontalerUeberlauf -- Element mit overflow-x: hidden', () => {
    test('Element mit overflow-x:hidden und breiterem Inhalt loest aus (auch ohne main)', async ({ page }) => {
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                .karte { width: 200px; overflow-x: hidden; }
                .breiter-inhalt { width: 400px; height: 20px; background: red; white-space: nowrap; }
            </style>
            <div class="karte"><div class="breiter-inhalt">zu breiter Inhalt</div></div>
        `);
        await expect(keinHorizontalerUeberlauf(page)).rejects.toThrow();
    });

    test('Element mit overflow-x:hidden ohne Ueberstand ist unauffaellig', async ({ page }) => {
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                .karte { width: 200px; overflow-x: hidden; }
                .passender-inhalt { width: 100px; height: 20px; background: green; }
            </style>
            <div class="karte"><div class="passender-inhalt">passt</div></div>
        `);
        await keinHorizontalerUeberlauf(page);
    });
});

test.describe('keinTextLaeuftUeber', () => {
    test('Kasten auf Breite 0 gequetscht mit Text darin loest aus (Breite 0 wird NICHT uebersprungen)', async ({ page }) => {
        // Nachgebauter Befund aus der Spec: Kunde mit langem Namen, Kasten
        // wird auf 0px Breite gequetscht, der Text bleibt trotzdem da.
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                .kasten { width: 0; overflow: hidden; white-space: nowrap; font-size: 16px; }
            </style>
            <div class="kasten">Wohnungsbaugesellschaft Beispielstadt Nord mbH</div>
        `);
        await expect(keinTextLaeuftUeber(page)).rejects.toThrow();
    });

    test('Text passt in seinen Kasten', async ({ page }) => {
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                .kasten { width: 300px; }
            </style>
            <div class="kasten">Kurzer Text</div>
        `);
        await keinTextLaeuftUeber(page);
    });

    test('Unsichtbare Elemente (display:none, visibility:hidden, opacity:0) werden nicht gemeldet', async ({ page }) => {
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                .kasten { width: 0; overflow: hidden; white-space: nowrap; font-size: 16px; }
            </style>
            <div class="kasten" style="display: none">Wohnungsbaugesellschaft Beispielstadt Nord mbH</div>
            <div class="kasten" style="visibility: hidden">Wohnungsbaugesellschaft Beispielstadt Nord mbH</div>
            <div class="kasten" style="opacity: 0">Wohnungsbaugesellschaft Beispielstadt Nord mbH</div>
        `);
        await keinTextLaeuftUeber(page);
    });
});

test.describe('keinTextGekuerzt', () => {
    test('text-overflow: ellipsis mit echtem Ueberstand loest aus', async ({ page }) => {
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                .titel { width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            </style>
            <div class="titel">Treppenanlage mit Podest und Absturzsicherung</div>
        `);
        await expect(keinTextGekuerzt(page)).rejects.toThrow();
    });

    test('text-overflow: ellipsis ohne Ueberstand ist unauffaellig', async ({ page }) => {
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                .titel { width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            </style>
            <div class="titel">Kurzer Titel</div>
        `);
        await keinTextGekuerzt(page);
    });

    test('-webkit-line-clamp mit tatsaechlichem Ueberstand loest aus (ohne text-overflow: ellipsis)', async ({ page }) => {
        // Nachgebaut wie src/index.css ".line-clamp-2": -webkit-box +
        // overflow: hidden, OHNE text-overflow: ellipsis.
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                .titel {
                    width: 150px;
                    display: -webkit-box;
                    -webkit-box-orient: vertical;
                    -webkit-line-clamp: 2;
                    overflow: hidden;
                }
            </style>
            <div class="titel">Treppenanlage mit Podest und Absturzsicherung Buerogebaeude Beispielstrasse</div>
        `);
        await expect(keinTextGekuerzt(page)).rejects.toThrow();
    });

    test('-webkit-line-clamp ohne tatsaechlichen Ueberstand ist unauffaellig', async ({ page }) => {
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                .titel {
                    width: 400px;
                    display: -webkit-box;
                    -webkit-box-orient: vertical;
                    -webkit-line-clamp: 2;
                    overflow: hidden;
                }
            </style>
            <div class="titel">Kurzer Titel</div>
        `);
        await keinTextGekuerzt(page);
    });

    test('data-kuerzung-erlaubt am Element selbst gilt als Ausnahme', async ({ page }) => {
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                .titel { width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            </style>
            <div class="titel" data-kuerzung-erlaubt>Treppenanlage mit Podest und Absturzsicherung</div>
        `);
        await keinTextGekuerzt(page);
    });

    test('data-kuerzung-erlaubt an einem Vorfahren gilt als Ausnahme', async ({ page }) => {
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                .titel { width: 100px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            </style>
            <div data-kuerzung-erlaubt><div class="titel">Treppenanlage mit Podest und Absturzsicherung</div></div>
        `);
        await keinTextGekuerzt(page);
    });
});

// ".wrapper" nutzt overflow: clip statt overflow: hidden, damit der IMMER
// laufende keinHorizontalerUeberlauf-Check (weder html/main noch die
// generische "overflow-x: hidden"-Regel greifen bei "clip") hier still
// bleibt -- nur so zeigt dieser Test wirklich, ob strengePruefungen selbst
// den Ausschlag gibt, statt zufaellig ueber einen anderen Check zu laufen.
const SEITE_MIT_NUR_TEXTUEBERLAUF = `
    <style>
        html, body { margin: 0; padding: 0; }
        .wrapper { overflow: clip; width: 200px; }
        .kasten { width: 0; white-space: nowrap; font-size: 16px; }
    </style>
    <div class="wrapper"><div class="kasten">Wohnungsbaugesellschaft Beispielstadt Nord mbH</div></div>
`;

test.describe('designPruefung -- Option strengePruefungen', () => {
    test('Standard false: ueberlaufender Text faellt nicht auf', async ({ page }, testInfo) => {
        await page.setContent(SEITE_MIT_NUR_TEXTUEBERLAUF);
        await designPruefung(page, testInfo, 'strenge-pruefungen-standard-aus');
    });

    test('strengePruefungen: true deckt ueberlaufenden Text auf', async ({ page }, testInfo) => {
        await page.setContent(SEITE_MIT_NUR_TEXTUEBERLAUF);
        await expect(
            designPruefung(page, testInfo, 'strenge-pruefungen-standard-an', { strengePruefungen: true }),
        ).rejects.toThrow();
    });
});

// Nachbesserung 1 (Kontext-Log Abschnitt 1, Befund vor dem Review): Tailwinds
// eingebaute ".sr-only"-Klasse (position: absolute; width: 1px; height: 1px;
// overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap;) macht Text
// absichtlich fuer Screenreader lesbar und auf JEDER Bildschirmgroesse
// unsichtbar -- ein 1x1-Kasten kann nichts Sichtbares abschneiden. Alle drei
// Pruefungen hielten das vorher faelschlich fuer einen Layoutfehler (Befund:
// BearbeitenLeiste.tsx Zeile 145, 4 rote Rauchproben-Tests). Nachgebaut hier,
// da page.setContent() kein Tailwind einbindet.
const SR_ONLY_STIL = `
    .sr-only {
        position: absolute;
        width: 1px;
        height: 1px;
        padding: 0;
        margin: -1px;
        overflow: hidden;
        clip: rect(0, 0, 0, 0);
        white-space: nowrap;
        border-width: 0;
    }
`;
const SR_ONLY_TEXT = 'Sperre konnte nicht geholt werden — bitte neu laden und Auftrag pruefen';

test.describe('unsichtbar versteckt (sr-only-Muster) wird von allen drei Pruefungen ignoriert', () => {
    test('sr-only-Span mit langem Text: keinHorizontalerUeberlauf laeuft durch', async ({ page }) => {
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                ${SR_ONLY_STIL}
            </style>
            <span class="sr-only">${SR_ONLY_TEXT}</span>
        `);
        await keinHorizontalerUeberlauf(page);
    });

    test('sr-only-Span mit langem Text: keinTextLaeuftUeber laeuft durch', async ({ page }) => {
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                ${SR_ONLY_STIL}
            </style>
            <span class="sr-only">${SR_ONLY_TEXT}</span>
        `);
        await keinTextLaeuftUeber(page);
    });

    test('sr-only-Span mit text-overflow: ellipsis: keinTextGekuerzt laeuft durch', async ({ page }) => {
        // Das echte Tailwind-".sr-only" setzt kein text-overflow: ellipsis --
        // ohne diese Zusatzregel gaebe es fuer keinTextGekuerzt hier gar
        // nichts zu pruefen (die Funktion schlaegt sonst schon aus anderem
        // Grund nicht an). Erst mit ellipsis wird die Ausnahme wirklich
        // gefordert.
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                ${SR_ONLY_STIL}
                .sr-only { text-overflow: ellipsis; }
            </style>
            <span class="sr-only">${SR_ONLY_TEXT}</span>
        `);
        await keinTextGekuerzt(page);
    });

    test('Vorfahre im sr-only-Muster schuetzt auch ein verschachteltes Element mit eigener Breite', async ({ page }) => {
        // <b> bekommt eine eigene, viel zu schmale Breite -- ohne die
        // Ausnahme fuer den sr-only-Vorfahren wuerde keinTextLaeuftUeber hier
        // anschlagen (10px Kasten, deutlich breiterer Text).
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                ${SR_ONLY_STIL}
                .sr-only b { display: block; width: 10px; white-space: nowrap; font-size: 16px; }
            </style>
            <span class="sr-only"><b>Sperre konnte nicht geholt werden</b></span>
        `);
        await keinTextLaeuftUeber(page);
    });
});

test.describe('unsichtbar versteckt -- Abgrenzung zu echten Befunden', () => {
    test('0px breiter, 16px hoher Text in einem 26px-Kasten mit overflow:visible bleibt ein Befund', async ({ page }) => {
        // Nicht verwechseln mit dem sr-only-Muster: dieser Kasten ist nicht
        // 1x1px, und nichts hier ist per overflow/clip abgeschnitten -- der
        // Text ist tatsaechlich sichtbar zusammengequetscht (Kennzahl-Kasten
        // aus der Spec, Befund 2).
        await page.setContent(`
            <style>
                html, body { margin: 0; padding: 0; }
                .kasten { width: 26px; overflow: visible; }
                .kasten p { width: 0; height: 16px; white-space: nowrap; margin: 0; font-size: 16px; }
            </style>
            <div class="kasten"><p>Wohnungsbaugesellschaft Beispielstadt Nord mbH</p></div>
        `);
        await expect(keinTextLaeuftUeber(page)).rejects.toThrow();
    });
});
