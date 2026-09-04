import fs from 'node:fs';
import path from 'node:path';
import { expect, type Locator, type Page, type TestInfo } from '@playwright/test';

/**
 * Hilfen fuer die Design-Pruefung (siehe .claude/skills/playwright-design-pruefung).
 *
 * Prueft automatisch, was sich automatisch pruefen laesst -- Ueberlauf,
 * Ueberschneidungen, Sichtbarkeit der Primaeraktion -- und legt je
 * Bildschirmgroesse einen Screenshot ab, den der Design-Reviewer anschliessend
 * anschaut. Farben, Design-System, Look-and-Feel und UX kann kein Code
 * beurteilen; dafuer ist der Screenshot da.
 *
 * Zwei Lehren aus dem ersten Design-Review sind hier eingebaut:
 *   - Screenshots wurden mitten in Uebergaengen gemacht (150-ms-Farbwechsel,
 *     500-ms-Breitenanimation) und belegten damit nicht den Zustand, den sie
 *     belegen sollten. Deshalb wartet uebergaengeAusklingenLassen() vorher.
 *   - Die Ueberschneidungs-Pruefung ignorierte bei offenem Dialog alles
 *     ausserhalb -- auch den fest positionierten Toast, der die Modal-Knoepfe
 *     verdeckte. Fest positionierte Elemente zaehlen jetzt immer mit.
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
    fest: boolean;
}

/** Screenshot + automatische Checks fuer den aktuellen Zustand der Seite. */
export async function designPruefung(
    page: Page,
    testInfo: TestInfo,
    name: string,
    optionen: DesignPruefungOptionen = {},
): Promise<void> {
    await uebergaengeAusklingenLassen(page);

    // testInfo.outputPath() laesst kein Verlassen des Pro-Test-Ordners zu (auch
    // nicht ueber '..'). Der gemeinsame Ordner ueber alle Specs hinweg wird
    // deshalb direkt aus dem Projekt-Ausgabeordner gebildet:
    // test-results/design/<name>--<projekt>.png
    const zielOrdner = path.join(testInfo.project.outputDir, 'design');
    fs.mkdirSync(zielOrdner, { recursive: true });
    const pfad = path.join(zielOrdner, `${name}--${testInfo.project.name}.png`);
    await page.screenshot({ path: pfad, fullPage: optionen.ganzeSeite ?? false });
    await testInfo.attach(`design: ${name}`, { path: pfad, contentType: 'image/png' });

    await keinHorizontalerUeberlauf(page);
    await keineUeberschneidungen(page);
    if (optionen.primaerAktion) {
        await expect(optionen.primaerAktion, 'Primaeraktion muss ohne Scrollen sichtbar sein').toBeInViewport();
    }
}

/**
 * Wartet, bis laufende CSS-Uebergaenge und -Animationen fertig sind (hoechstens
 * `maxMs`). Endlos laufende Animationen (Spinner, animate-pulse) werden nicht
 * abgewartet -- die haben kein Ende.
 */
export async function uebergaengeAusklingenLassen(page: Page, maxMs = 1500): Promise<void> {
    await page.evaluate(async (grenze) => {
        const endlich = document.getAnimations().filter((a) => {
            const timing = a.effect?.getComputedTiming();
            return timing != null && Number.isFinite(timing.endTime as number) && a.playState === 'running';
        });
        if (endlich.length === 0) return;
        const alleFertig = Promise.all(endlich.map((a) => a.finished.catch(() => undefined)));
        const zeitLimit = new Promise((r) => setTimeout(r, grenze));
        await Promise.race([alleFertig, zeitLimit]);
    }, maxMs);
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
 *
 * Bei offenem Dialog zaehlt der verdeckte Hintergrund nicht -- der ist
 * absichtlich weg. Fest positionierte Elemente (Toasts, Sticky-Leisten)
 * zaehlen dagegen IMMER, weil sie ueber dem Dialog liegen und dessen Knoepfe
 * verdecken koennen. Verschachtelte Elemente (Knopf im Link) gelten nicht
 * als Ueberschneidung.
 */
export async function keineUeberschneidungen(page: Page): Promise<void> {
    const rahmen: Rahmen[] = await page.evaluate(() => {
        const selektor = 'button, a[href], input, select, textarea, [role="button"], [role="link"], [role="tab"], [role="alert"], [role="status"]';
        const ergebnis: Rahmen[] = [];
        const dialog = document.querySelector('[role="dialog"]');

        const istFestPositioniert = (el: HTMLElement): boolean => {
            for (let k: HTMLElement | null = el; k != null && k !== document.body; k = k.parentElement) {
                if (getComputedStyle(k).position === 'fixed') return true;
            }
            return false;
        };

        for (const el of Array.from(document.querySelectorAll<HTMLElement>(selektor))) {
            const r = el.getBoundingClientRect();
            if (r.width === 0 || r.height === 0) continue;
            const stil = getComputedStyle(el);
            if (stil.visibility === 'hidden' || stil.display === 'none' || Number(stil.opacity) === 0) continue;
            const fest = istFestPositioniert(el);
            // Hintergrund hinter einem Dialog ist absichtlich verdeckt -- ausser er ist fest positioniert.
            if (dialog && !dialog.contains(el) && !fest) continue;
            if (el === dialog) continue;
            const rolle = el.getAttribute('role');
            ergebnis.push({
                beschreibung: `${el.tagName.toLowerCase()}${el.id ? '#' + el.id : ''}${rolle ? '[' + rolle + ']' : ''} "${(el.textContent ?? '').trim().slice(0, 30)}"`,
                x: r.left, y: r.top, breite: r.width, hoehe: r.height, fest,
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
            // Verschachtelt (Knopf im Link) ist kein Layoutfehler -- ausser einer der
            // beiden ist fest positioniert: ein Toast, der einen Knopf komplett
            // abdeckt, ist genau der Fehler, den diese Pruefung finden soll.
            if (enthalten && !a.fest && !b.fest) continue;
            ueberlappungen.push(`${a.beschreibung} ueberlappt ${b.beschreibung}`);
        }
    }
    expect(ueberlappungen, `Interaktive Elemente ueberschneiden sich:\n${ueberlappungen.join('\n')}`).toEqual([]);
}
