import { test, expect, type Locator, type Page } from '@playwright/test';
import { stubbeWebsiteApi, oeffneNeuigkeiten } from './hilfen/api';
import { inhalt, warteAufProjektsuche } from './hilfen/seite';

/**
 * Prueft den Bereich "Website - Neuigkeiten" gegen die Regeln aus
 * docs/agent instructions/docs/FRONTEND_UI.md und die UX-Pflichtpunkte:
 * rose/slate statt blau/indigo, sichtbarer Fokus, Klickflaechen, Alt-Texte,
 * kein waagerechtes Scrollen, Handwerker-Sprache statt Buchhalter-Deutsch.
 *
 * Die Zusicherungen laufen ueber inhalt(page), also den <main>-Bereich:
 * die Menueleiste steht auf jeder Seite und gehoert nicht zu diesem Modul.
 */

/** rose-600 aus der Tailwind-Palette, die das Projekt nutzt (#e11d48). */
const ROSE_600 = [225, 29, 72];

/** Relative Leuchtdichte nach WCAG. */
function leuchtdichte([r, g, b]: number[]): number {
    const k = [r, g, b].map(v => {
        const s = v / 255;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * k[0] + 0.7152 * k[1] + 0.0722 * k[2];
}

function kontrast(vorne: number[], hinten: number[]): number {
    const a = leuchtdichte(vorne), b = leuchtdichte(hinten);
    const [hell, dunkel] = a > b ? [a, b] : [b, a];
    return (hell + 0.05) / (dunkel + 0.05);
}

function alsRgb(farbe: string): number[] {
    const t = /rgba?\(([^)]+)\)/.exec(farbe);
    if (!t) return [0, 0, 0];
    return t[1].split(',').slice(0, 3).map(v => parseFloat(v.trim()));
}

/** Sucht die naechste nicht-transparente Hintergrundfarbe im Elternpfad. */
async function echterHintergrund(el: Locator): Promise<number[]> {
    return alsRgb(await el.evaluate(node => {
        let aktuell: HTMLElement | null = node as HTMLElement;
        while (aktuell) {
            const bg = getComputedStyle(aktuell).backgroundColor;
            if (bg && !bg.includes('rgba(0, 0, 0, 0)') && bg !== 'transparent') return bg;
            aktuell = aktuell.parentElement;
        }
        return 'rgb(255, 255, 255)';
    }));
}

async function oeffneAssistentBisBilder(page: Page) {
    await page.getByRole('button', { name: 'Neuer Beitrag' }).click();
    await page.getByRole('button', { name: /Balkonanlage Musterstraße/ }).click();
    await expect(page.getByRole('heading', { name: /Aus dem Bautagebuch/ })).toBeVisible();
}

test.describe('Design-System', () => {
    test.beforeEach(async ({ page }) => {
        await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);
    });

    test('der Hauptknopf traegt die Primaerfarbe rose-600', async ({ page }) => {
        const knopf = page.getByRole('button', { name: 'Neuer Beitrag' });
        const farbe = await knopf.evaluate(el => getComputedStyle(el).backgroundColor);

        expect(alsRgb(farbe)).toEqual(ROSE_600);
    });

    test('der aktive Reiter ist rose, nicht slate', async ({ page }) => {
        const aktiv = page.getByRole('button', { name: /Beitrag erstellen/ });
        const farbe = alsRgb(await aktiv.evaluate(el => getComputedStyle(el).color));

        // rose-700 (#be123c) -- Hauptsache aus der Rose-Familie, nicht grau.
        expect(farbe[0]).toBeGreaterThan(farbe[2]);
        expect(farbe[0]).toBeGreaterThan(150);
    });

    test('nutzt nirgends indigo oder blue als Akzent', async ({ page }) => {
        const verbotene = await inhalt(page).evaluate(wurzel => {
            // Die Tailwind-Toene, die das Design-System ausschliesst.
            const tabu = [
                'rgb(79, 70, 229)', 'rgb(99, 102, 241)', 'rgb(67, 56, 202)', // indigo-600/500/700
                'rgb(37, 99, 235)', 'rgb(59, 130, 246)', 'rgb(29, 78, 216)', // blue-600/500/700
            ];
            const treffer: string[] = [];
            for (const el of Array.from(wurzel.querySelectorAll('*'))) {
                const s = getComputedStyle(el);
                for (const wert of [s.backgroundColor, s.color, s.borderColor]) {
                    if (tabu.includes(wert)) {
                        treffer.push(`${el.tagName}.${(el as HTMLElement).className} -> ${wert}`);
                    }
                }
            }
            return treffer;
        });

        expect(verbotene).toEqual([]);
    });

    test('der aktive Reiter ist nicht nur ueber Farbe erkennbar', async ({ page }) => {
        // Farbe allein reicht nicht (Farbfehlsichtigkeit): aria-current muss sitzen.
        const aktiv = page.getByRole('button', { name: /Beitrag erstellen/ });
        await expect(aktiv).toHaveAttribute('aria-current', 'page');

        const inaktiv = page.getByRole('button', { name: /Zahlen der Website/ });
        await expect(inaktiv).not.toHaveAttribute('aria-current', 'page');
    });
});

test.describe('Barrierefreiheit und Bedienbarkeit', () => {
    test.beforeEach(async ({ page }) => {
        await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);
    });

    test('der Hauptknopf hat genug Kontrast', async ({ page }) => {
        const knopf = page.getByRole('button', { name: 'Neuer Beitrag' });
        const vorne = alsRgb(await knopf.evaluate(el => getComputedStyle(el).color));
        const hinten = alsRgb(await knopf.evaluate(el => getComputedStyle(el).backgroundColor));

        expect(kontrast(vorne, hinten)).toBeGreaterThanOrEqual(4.5);
    });

    test('der Beschreibungstext der Seite ist lesbar genug', async ({ page }) => {
        const text = inhalt(page)
            .getByText('Beiträge für den Bereich Aktuelles auf der Firmen-Website pflegen.');
        const vorne = alsRgb(await text.evaluate(el => getComputedStyle(el).color));
        const hinten = await echterHintergrund(text);

        // slate-500 auf Weiss liegt bei ~4.8:1.
        expect(kontrast(vorne, hinten)).toBeGreaterThanOrEqual(4.5);
    });

    test('die Knoepfe im Modul sind hoch genug zum Treffen', async ({ page }) => {
        const zuKlein: string[] = [];
        for (const knopf of await inhalt(page).getByRole('button').all()) {
            if (!await knopf.isVisible()) continue;
            const kasten = await knopf.boundingBox();
            // 32 px ist die Untergrenze fuer die dichte Desktop-Maske; die
            // Zeiterfassung am Handy hat eigene, groessere Ziele.
            if (kasten && kasten.height < 32) {
                zuKlein.push(`${(await knopf.textContent())?.trim()} (${kasten.height}px)`);
            }
        }
        expect(zuKlein).toEqual([]);
    });

    test('der Fokus ist sichtbar, wenn man mit Tab navigiert', async ({ page }) => {
        const knopf = page.getByRole('button', { name: 'Neuer Beitrag' });
        await knopf.focus();

        const sichtbar = await knopf.evaluate(el => {
            const s = getComputedStyle(el);
            const ring = s.getPropertyValue('--tw-ring-shadow');
            return s.outlineStyle !== 'none' || s.boxShadow !== 'none' || (!!ring && ring !== '0 0 #0000');
        });
        expect(sichtbar).toBe(true);
    });

    test('erreicht "Neuer Beitrag" allein mit der Tastatur', async ({ page }) => {
        const knopf = page.getByRole('button', { name: 'Neuer Beitrag' });
        await knopf.focus();
        await page.keyboard.press('Enter');

        await warteAufProjektsuche(page);
    });

    test('jedes Bild in der Auswahl hat einen Alt-Text', async ({ page }) => {
        await oeffneAssistentBisBilder(page);

        const ohneAlt = await page.evaluate(() =>
            Array.from(document.querySelectorAll('img'))
                .filter(b => !b.alt || !b.alt.trim())
                .map(b => b.src.slice(0, 60)));

        expect(ohneAlt).toEqual([]);
    });

    test('Eingabefelder im Textschritt haben verknuepfte Beschriftungen', async ({ page }) => {
        await oeffneAssistentBisBilder(page);
        await page.getByRole('img', { name: 'balkon-vorher.jpg' }).click();
        await page.getByRole('button', { name: 'Weiter' }).click();
        await page.getByRole('button', { name: 'Selbst schreiben' }).click();

        // getByLabel findet nur, was ueber for/id sauber verknuepft ist.
        await expect(page.getByLabel('Titel')).toBeVisible();
        await expect(page.getByLabel('Kurzbeschreibung')).toBeVisible();
    });

    test('jeder Knopf hat einen Namen fuer Screenreader', async ({ page }) => {
        await oeffneAssistentBisBilder(page);

        const namenlos = await page.evaluate(() =>
            Array.from(document.querySelectorAll('button'))
                .filter(b => (b as HTMLElement).offsetParent !== null)
                .filter(b => {
                    // Ein Knopf gilt als benannt durch Text, aria-label, title
                    // oder -- bei Bildknoepfen -- den Alt-Text darin.
                    if (b.textContent?.trim()) return false;
                    if (b.getAttribute('aria-label')?.trim()) return false;
                    if (b.getAttribute('title')?.trim()) return false;
                    const bild = b.querySelector('img');
                    if (bild?.alt?.trim()) return false;
                    return true;
                })
                .map(b => b.className));

        expect(namenlos).toEqual([]);
    });
});

test.describe('Layout auf verschiedenen Geraeten', () => {
    for (const [name, breite, hoehe] of [
        ['Handy', 375, 812],
        ['Tablet', 768, 1024],
        ['Laptop', 1440, 900],
    ] as const) {
        test(`kein waagerechtes Scrollen auf ${name} (${breite}px)`, async ({ page }) => {
            await page.setViewportSize({ width: breite, height: hoehe });
            await stubbeWebsiteApi(page);
            await oeffneNeuigkeiten(page);

            const ueberstand = await page.evaluate(() =>
                document.documentElement.scrollWidth - document.documentElement.clientWidth);
            expect(ueberstand).toBeLessThanOrEqual(1);
        });
    }

    test('der Assistent deckt die Seite vollstaendig ab', async ({ page }) => {
        await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);
        await oeffneAssistentBisBilder(page);

        // Die Flaeche muss den Bildschirm fuellen und deckend sein, sonst
        // klickt der Nutzer versehentlich auf die Liste dahinter.
        const flaeche = page.locator('div.fixed.inset-0')
            .filter({ has: page.getByRole('button', { name: 'Assistent schließen' }) });

        const kasten = await flaeche.boundingBox();
        const sicht = page.viewportSize()!;
        expect(kasten!.width).toBeGreaterThanOrEqual(sicht.width - 1);
        expect(kasten!.height).toBeGreaterThanOrEqual(sicht.height - 1);

        const deckend = await flaeche.evaluate(el => {
            const bg = getComputedStyle(el).backgroundColor;
            const t = /rgba?\(([^)]+)\)/.exec(bg);
            const teile = t ? t[1].split(',') : [];
            return teile.length < 4 || parseFloat(teile[3]) === 1;
        });
        expect(deckend).toBe(true);
    });
});

test.describe('Sprache', () => {
    test('nutzt Handwerker-Sprache statt Buchhalter-Deutsch', async ({ page }) => {
        await stubbeWebsiteApi(page);
        await oeffneNeuigkeiten(page);

        const text = (await inhalt(page).innerText()).toLowerCase();
        for (const wort of [
            'debitor', 'kreditor', 'buchungssatz', 'mandant',
            'entität', 'persistieren', 'konsolidierung', 'ressourcenallokation',
        ]) {
            expect(text, `Fachjargon "${wort}" gehoert nicht in die UI`).not.toContain(wort);
        }
    });

    test('erklaert leere Zustaende in ganzen Saetzen', async ({ page }) => {
        await stubbeWebsiteApi(page, { beitraege: [] });
        await oeffneNeuigkeiten(page);

        await expect(page.getByText('Noch kein Beitrag angelegt.')).toBeVisible();
    });
});
