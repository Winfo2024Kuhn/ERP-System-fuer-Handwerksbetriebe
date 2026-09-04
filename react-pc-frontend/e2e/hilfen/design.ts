import { expect, type Locator, type Page, type TestInfo } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';

/**
 * Hilfen fuer die Design-Pruefung (siehe .claude/skills/playwright-design-pruefung).
 *
 * Prueft automatisch, was sich automatisch pruefen laesst -- Ueberlauf,
 * Ueberschneidungen, Sichtbarkeit der Primaeraktion -- und legt je
 * Bildschirmgroesse einen Screenshot ab, den der Agent anschliessend
 * anschaut. Farben, Design-System, Look-and-Feel und UX kann kein Code
 * beurteilen; dafuer ist der Screenshot da.
 */

interface DesignPruefungOptionen {
    /** Die Aktion, um die es im Task geht -- muss ohne Scrollen sichtbar sein. */
    primaerAktion?: Locator;
    /** Ganze Seite statt nur Viewport aufnehmen (Standard: nur Viewport, denn der ist, was der Nutzer sieht). */
    ganzeSeite?: boolean;
}

interface Rahmen {
    beschreibung: string;
    x: number;
    y: number;
    breite: number;
    hoehe: number;
}

/** Screenshot + automatische Checks fuer den aktuellen Zustand der Seite. */
export async function designPruefung(
    page: Page,
    testInfo: TestInfo,
    name: string,
    optionen: DesignPruefungOptionen = {},
): Promise<void> {
    // testInfo.outputPath() erlaubt seit Playwright 1.62 kein Verlassen des
    // eigenen Test-Ausgabeverzeichnisses mehr ('..'-Segmente werfen einen
    // Fehler) -- der Zielordner test-results/design/ liegt aber bewusst
    // NEBEN den einzelnen Test-Ordnern (siehe Skill-Doku), nicht darunter.
    // Deshalb den Pfad direkt aus dem projektweiten outputDir bauen.
    const designOrdner = join(testInfo.project.outputDir, 'design');
    mkdirSync(designOrdner, { recursive: true });
    const pfad = join(designOrdner, `${name}--${testInfo.project.name}.png`);
    await page.screenshot({ path: pfad, fullPage: optionen.ganzeSeite ?? false });
    await testInfo.attach(`design: ${name}`, { path: pfad, contentType: 'image/png' });

    await keinHorizontalerUeberlauf(page);
    await keineUeberschneidungen(page);
    if (optionen.primaerAktion) {
        await expect(optionen.primaerAktion, 'Primaeraktion muss ohne Scrollen sichtbar sein').toBeInViewport();
    }
}

/** Kein horizontaler Scrollbalken -- auf 14 Zoll das haeufigste Symptom fuer "passt nicht". */
export async function keinHorizontalerUeberlauf(page: Page): Promise<void> {
    const ueberlauf = await page.evaluate(() => {
        const el = document.documentElement;
        return { scroll: el.scrollWidth, sichtbar: el.clientWidth };
    });
    expect(
        ueberlauf.scroll,
        `Seite laeuft horizontal ueber: ${ueberlauf.scroll}px Inhalt bei ${ueberlauf.sichtbar}px Breite`,
    ).toBeLessThanOrEqual(ueberlauf.sichtbar);
}

/**
 * Keine zwei sichtbaren interaktiven Elemente ueberlappen sich.
 * Verschachtelte Elemente (Knopf im Link) werden ausgenommen, ebenso
 * absichtlich gestapelte Overlays (role=dialog) gegenueber dem Hintergrund.
 */
export async function keineUeberschneidungen(page: Page): Promise<void> {
    const rahmen: Rahmen[] = await page.evaluate(() => {
        const selektor = 'button, a[href], input, select, textarea, [role="button"], [role="link"], [role="tab"]';
        const ergebnis: Rahmen[] = [];
        const dialog = document.querySelector('[role="dialog"]');
        for (const el of Array.from(document.querySelectorAll<HTMLElement>(selektor))) {
            const r = el.getBoundingClientRect();
            if (r.width === 0 || r.height === 0) continue;
            const stil = getComputedStyle(el);
            if (stil.visibility === 'hidden' || stil.display === 'none' || Number(stil.opacity) === 0) continue;
            // Bei offenem Dialog zaehlt nur, was im Dialog liegt -- der Hintergrund ist absichtlich verdeckt.
            if (dialog && !dialog.contains(el)) continue;
            ergebnis.push({
                beschreibung: `${el.tagName.toLowerCase()}${el.id ? '#' + el.id : ''} "${(el.textContent ?? '').trim().slice(0, 30)}"`,
                x: r.left, y: r.top, breite: r.width, hoehe: r.height,
            });
        }
        return ergebnis;
    });

    const ueberlappungen: string[] = [];
    for (let i = 0; i < rahmen.length; i++) {
        for (let j = i + 1; j < rahmen.length; j++) {
            const a = rahmen[i];
            const b = rahmen[j];
            const ueberlappt =
                a.x < b.x + b.breite && a.x + a.breite > b.x &&
                a.y < b.y + b.hoehe && a.y + a.hoehe > b.y;
            if (!ueberlappt) continue;
            const enthalten =
                (a.x >= b.x && a.y >= b.y && a.x + a.breite <= b.x + b.breite && a.y + a.hoehe <= b.y + b.hoehe) ||
                (b.x >= a.x && b.y >= a.y && b.x + b.breite <= a.x + a.breite && b.y + b.hoehe <= a.y + a.hoehe);
            if (enthalten) continue; // verschachtelt, kein Layoutfehler
            ueberlappungen.push(`${a.beschreibung} ueberlappt ${b.beschreibung}`);
        }
    }
    expect(ueberlappungen, `Interaktive Elemente ueberschneiden sich:\n${ueberlappungen.join('\n')}`).toEqual([]);
}
