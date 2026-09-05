import fs from 'node:fs';
import path from 'node:path';
import { expect, type Locator, type Page, type TestInfo } from '@playwright/test';

/**
 * Hilfen fuer die Design-Pruefung (siehe .claude/skills/playwright-design-pruefung).
 *
 * Prueft automatisch, was sich automatisch pruefen laesst -- Ueberlauf,
 * Abschneiden, Ueberschneidungen, Sichtbarkeit der Primaeraktion -- und legt je
 * Bildschirmgroesse einen Screenshot ab, den der Design-Reviewer anschliessend
 * anschaut. Farben, Design-System, Look-and-Feel und UX kann kein Code
 * beurteilen; dafuer ist der Screenshot da.
 *
 * Lehren aus echten Laeufen, die hier eingebaut sind:
 *   - Screenshots wurden mitten in Uebergaengen gemacht (150-ms-Farbwechsel,
 *     500-ms-Breitenanimation) und belegten damit nicht den Zustand, den sie
 *     belegen sollten. Deshalb wartet uebergaengeAusklingenLassen() vorher.
 *   - Die Ueberschneidungs-Pruefung ignorierte bei offenem Dialog alles
 *     ausserhalb -- auch den fest positionierten Toast, der die Modal-Knoepfe
 *     verdeckte. Fest positionierte Elemente zaehlen immer mit.
 *   - Drei echte Abschneide-Fehler auf 14 Zoll blieben unentdeckt, weil nur
 *     documentElement.scrollWidth gemessen wurde: ein <main> oder ein Container
 *     mit overflow-x: hidden verschluckt den Ueberlauf, ohne dass die Seite
 *     breiter wird; abgeschnittener Text auf einem Blatt-Element waechst nie ins
 *     Dokument; und ein Element mit Breite 0 ist genau der Fall, nicht die
 *     Ausnahme. Deshalb keinAbschneiden() -- siehe dort.
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
    await keinAbschneiden(page);
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

/**
 * Kein horizontaler Ueberlauf -- weder am Dokument noch in einem Container, der
 * ihn verschluckt.
 *
 * documentElement.scrollWidth allein reicht nicht: ein <main> oder irgendein
 * Element mit overflow-x: hidden|clip|auto|scroll nimmt den Ueberlauf in sich
 * auf, die Seite bleibt exakt so breit wie das Fenster, und der Nutzer sieht
 * trotzdem eine abgeschnittene rechte Spalte (so passiert im Projekt-Editor:
 * auf 1920 perfekt, auf 1440 zu 170 px ausserhalb). Deshalb wird jeder Container
 * gegen seinen eigenen scrollWidth gemessen. Absichtlich scrollbare Bereiche
 * (overflow-x: auto|scroll, die tatsaechlich scrollen sollen -- Tabellen,
 * Zeitleisten) markiert man mit data-scroll-gewollt; alles andere gilt als
 * Fehler.
 */
export async function keinHorizontalerUeberlauf(page: Page): Promise<void> {
    const befunde: string[] = await page.evaluate(() => {
        const ergebnis: string[] = [];
        const root = document.documentElement;
        if (root.scrollWidth > root.clientWidth) {
            ergebnis.push(`Seite laeuft horizontal ueber: ${root.scrollWidth}px Inhalt bei ${root.clientWidth}px Breite`);
        }
        const beschreibe = (el: Element): string => {
            const h = el as HTMLElement;
            const rolle = h.getAttribute('role');
            return `${h.tagName.toLowerCase()}${h.id ? '#' + h.id : ''}${rolle ? '[' + rolle + ']' : ''}${h.dataset.testid ? '[' + h.dataset.testid + ']' : ''}`;
        };
        for (const el of Array.from(document.querySelectorAll<HTMLElement>('body *'))) {
            if (el.hasAttribute('data-scroll-gewollt') || el.closest('[data-scroll-gewollt]')) continue;
            const stil = getComputedStyle(el);
            if (stil.display === 'none' || stil.visibility === 'hidden') continue;
            const ox = stil.overflowX;
            if (ox !== 'hidden' && ox !== 'clip' && ox !== 'auto' && ox !== 'scroll') continue;
            // Ein Blatt mit Text ist Sache von keinAbschneiden(); hier geht es um Container.
            if (el.children.length === 0) continue;
            if (el.scrollWidth > el.clientWidth + 1) {
                ergebnis.push(`${beschreibe(el)} verschluckt ${el.scrollWidth - el.clientWidth}px Ueberlauf (overflow-x: ${ox}, ${el.clientWidth}px breit, Inhalt ${el.scrollWidth}px)`);
            }
        }
        return ergebnis;
    });
    expect(befunde, `Horizontaler Ueberlauf:\n${befunde.join('\n')}`).toEqual([]);
}

/**
 * Kein abgeschnittener Text.
 *
 * Ein Blatt-Element mit Text, dessen Inhalt breiter ist als es selbst
 * (scrollWidth > clientWidth), zeigt dem Nutzer nur einen Teil -- und das
 * Dokument wird davon nie breiter. Elemente mit Breite 0 werden dabei NICHT
 * uebersprungen: ein Text in einem 0 px breiten Kasten ist der Extremfall des
 * Abschneidens, nicht eine Ausnahme davon. Eine gewollte Kuerzung
 * (text-overflow: ellipsis, etwa in Tabellenzellen) gilt nicht als Fehler --
 * ob die Ellipse an dieser Stelle auf 14 Zoll vertretbar ist, beurteilt der
 * Design-Reviewer am Screenshot (Frage 6). Ebenso wenig Elemente, die
 * data-kuerzung-gewollt tragen.
 */
export async function keinAbschneiden(page: Page): Promise<void> {
    const befunde: string[] = await page.evaluate(() => {
        const ergebnis: string[] = [];
        for (const el of Array.from(document.querySelectorAll<HTMLElement>('body *'))) {
            if (el.children.length > 0) continue;
            const text = (el.textContent ?? '').trim();
            if (text.length === 0) continue;
            if (el.closest('[data-kuerzung-gewollt]')) continue;
            const stil = getComputedStyle(el);
            if (stil.display === 'none' || stil.visibility === 'hidden' || Number(stil.opacity) === 0) continue;
            if (stil.textOverflow === 'ellipsis') continue; // gewollte Kuerzung, Sache des Reviewers
            // Screenreader-only-Texte (sr-only: 1x1 px, overflow hidden) sind absichtlich unsichtbar.
            if (stil.position === 'absolute' && el.clientWidth <= 1 && el.clientHeight <= 1) continue;
            const ox = stil.overflowX;
            const schneidetAb = ox === 'hidden' || ox === 'clip';
            // Ohne overflow hidden laeuft Text sichtbar ueber -- das ist ein Layoutfehler
            // anderer Art (Ueberschneidung), aber nichts wird versteckt.
            if (!schneidetAb) continue;
            if (el.scrollWidth > el.clientWidth + 1) {
                const rolle = el.getAttribute('role');
                ergebnis.push(`${el.tagName.toLowerCase()}${el.id ? '#' + el.id : ''}${rolle ? '[' + rolle + ']' : ''} "${text.slice(0, 40)}" ist abgeschnitten (${el.clientWidth}px breit, Text ${el.scrollWidth}px)`);
            }
        }
        return ergebnis;
    });
    expect(befunde, `Abgeschnittener Text:\n${befunde.join('\n')}`).toEqual([]);
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
