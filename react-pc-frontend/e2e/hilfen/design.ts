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
    /**
     * Schaltet keinTextLaeuftUeber und keinTextGekuerzt scharf. Standard
     * vorerst false: mehrere bestehende Specs (u.a. e2e/bearbeiten-leiste.spec.ts,
     * e2e/lieferant-dokument-modal.spec.ts) laufen ueber den Lieferanten-Kopf,
     * dessen Kennzahl-Beschriftungen heute noch ueberlaufen -- ein scharfer
     * Standard wuerde diese unveraenderten Specs sofort rot drehen, bevor die
     * betroffenen Seiten repariert sind (siehe Plan-Tasks 2-9 in
     * docs/superpowers/plans/2026-09-05-layout-14-zoll.md). Sobald alle
     * betroffenen Seiten repariert sind, dreht Task 10 den Standard auf true.
     */
    strengePruefungen?: boolean;
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
    if (optionen.strengePruefungen) {
        await keinTextLaeuftUeber(page);
        await keinTextGekuerzt(page);
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
 * Kein horizontaler Scrollbalken -- auf 14 Zoll das haeufigste Symptom fuer
 * "passt nicht". Prueft drei Ebenen:
 *   - das Dokument selbst (html.scrollWidth > html.clientWidth) -- das
 *     urspruengliche Verhalten dieser Funktion,
 *   - <main>, weil MainLayout.tsx frueher overflow-x-hidden gesetzt hat und
 *     einen Ueberstand dort still versteckt hat, statt ihn ueber einen
 *     Scrollbalken zu zeigen (siehe Befund 1 der Spec: 185px Ueberstand bei
 *     1440px, ohne dass das Dokument das gemerkt haette),
 *   - jedes Element mit overflow-x: hidden, dessen Inhalt breiter ist als es
 *     selbst -- jede Karte kann diesen Fehler machen, nicht nur <main>.
 * <main> wird aus der dritten Schleife ausgenommen, damit derselbe Fund
 * nicht doppelt gemeldet wird.
 */
export async function keinHorizontalerUeberlauf(page: Page): Promise<void> {
    const treffer = await page.evaluate(() => {
        const ergebnis: { beschreibung: string; ueberstandPx: number }[] = [];
        const beschreibe = (el: Element) => {
            const klassen = el.classList.length > 0 ? `.${Array.from(el.classList).join('.')}` : '';
            return `${el.tagName.toLowerCase()}${klassen}`;
        };

        // Nachbesserung 1 (Kontext-Log Abschnitt 1): Tailwinds ".sr-only"
        // (position: absolute; width: 1px; height: 1px; overflow: hidden;
        // clip: rect(0,0,0,0); ...) macht Text absichtlich fuer Screenreader
        // lesbar und auf JEDER Groesse unsichtbar -- ein 1x1-Kasten kann
        // nichts Sichtbares abschneiden, das ist kein Layoutfehler. Anders
        // als bei "Breite 0" (siehe keinTextLaeuftUeber, Kennzahl-Kasten der
        // Spec: 0px breit, aber 16px hoch UND per overflow:visible gar nicht
        // abgeschnitten) zaehlt hier NUR das Muster "faktisch 1x1px UND
        // abgeschnitten/geklippt" -- gilt fuer das Element selbst oder einen
        // Vorfahren, damit auch verschachtelter Inhalt (z.B. <b> in einem
        // sr-only-<span>) mitgeschuetzt wird.
        const istUnsichtbarVersteckt = (el: Element): boolean => {
            for (let k: Element | null = el; k != null; k = k.parentElement) {
                if (k.clientWidth > 1 || k.clientHeight > 1) continue;
                const stil = getComputedStyle(k);
                const nichtSichtbarerOverflow = stil.overflowX !== 'visible' || stil.overflowY !== 'visible';
                const geklippt = stil.clip === 'rect(0px, 0px, 0px, 0px)' || (stil.clipPath !== 'none' && stil.clipPath !== '');
                if (nichtSichtbarerOverflow || geklippt) return true;
            }
            return false;
        };

        const html = document.documentElement;
        if (html.scrollWidth > html.clientWidth) {
            ergebnis.push({ beschreibung: 'html', ueberstandPx: html.scrollWidth - html.clientWidth });
        }

        const main = document.querySelector('main');
        if (main && main.scrollWidth > main.clientWidth) {
            ergebnis.push({ beschreibung: beschreibe(main), ueberstandPx: main.scrollWidth - main.clientWidth });
        }

        for (const el of Array.from(document.querySelectorAll<HTMLElement>('*'))) {
            if (el === html || el === main) continue; // schon oben erfasst
            if (getComputedStyle(el).overflowX !== 'hidden') continue;
            if (istUnsichtbarVersteckt(el)) continue; // z.B. Tailwind ".sr-only" -- siehe Kommentar dort
            if (el.scrollWidth > el.clientWidth + 2) {
                ergebnis.push({ beschreibung: beschreibe(el), ueberstandPx: el.scrollWidth - el.clientWidth });
            }
        }

        return ergebnis;
    });

    expect(
        treffer,
        `Element(e) laufen horizontal ueber (bei overflow-x: hidden still versteckt statt sichtbar zu scrollen):\n${treffer
            .map((t) => `${t.beschreibung}: ${t.ueberstandPx}px zu wenig Platz`)
            .join('\n')}`,
    ).toEqual([]);
}

/**
 * Kein Blatt-Element mit Text (keine Element-Kinder) ist breiter als sein
 * Kasten. Anders als bei keinHorizontalerUeberlauf werden Elemente mit
 * Breite 0 bewusst NICHT uebersprungen -- ein auf 0px gequetschter Kasten mit
 * Text darin ist genau der Fehlerfall, den diese Pruefung finden soll (Kunde
 * mit langem Namen: Kasten 26px, Textbreite 95px, siehe Spec Befund 2).
 * Unsichtbares (display: none, visibility: hidden, opacity: 0) bleibt
 * draussen -- das ist kein Layoutfehler, sondern absichtlich verborgen.
 */
export async function keinTextLaeuftUeber(page: Page): Promise<void> {
    const treffer = await page.evaluate(() => {
        const ergebnis: { beschreibung: string; text: string; ueberstandPx: number }[] = [];

        // Gleiche Ausnahme wie in keinHorizontalerUeberlauf (siehe Kommentar
        // dort): Element oder Vorfahre faktisch 1x1px UND abgeschnitten/
        // geklippt (Tailwind ".sr-only"). NICHT dasselbe wie "Breite 0" --
        // ein 0px breiter, aber hoher und nicht abgeschnittener Kasten bleibt
        // ein Befund.
        const istUnsichtbarVersteckt = (el: Element): boolean => {
            for (let k: Element | null = el; k != null; k = k.parentElement) {
                if (k.clientWidth > 1 || k.clientHeight > 1) continue;
                const stil = getComputedStyle(k);
                const nichtSichtbarerOverflow = stil.overflowX !== 'visible' || stil.overflowY !== 'visible';
                const geklippt = stil.clip === 'rect(0px, 0px, 0px, 0px)' || (stil.clipPath !== 'none' && stil.clipPath !== '');
                if (nichtSichtbarerOverflow || geklippt) return true;
            }
            return false;
        };

        for (const el of Array.from(document.querySelectorAll<HTMLElement>('*'))) {
            if (el.children.length > 0) continue; // nur Blatt-Elemente
            const text = (el.textContent ?? '').trim();
            if (text.length === 0) continue;

            const stil = getComputedStyle(el);
            if (stil.display === 'none' || stil.visibility === 'hidden' || Number(stil.opacity) === 0) continue;
            if (istUnsichtbarVersteckt(el)) continue;

            if (el.scrollWidth > el.clientWidth + 2) {
                const klassen = el.classList.length > 0 ? `.${Array.from(el.classList).join('.')}` : '';
                ergebnis.push({
                    beschreibung: `${el.tagName.toLowerCase()}${klassen}`,
                    text: text.slice(0, 80),
                    ueberstandPx: el.scrollWidth - el.clientWidth,
                });
            }
        }

        return ergebnis;
    });

    expect(
        treffer,
        `Text laeuft ueber seinen Kasten:\n${treffer
            .map((t) => `${t.beschreibung} "${t.text}": ${t.ueberstandPx}px zu wenig Platz`)
            .join('\n')}`,
    ).toEqual([]);
}

/**
 * Kein Element ist tatsaechlich gekuerzt -- ein "…" an einer Stelle, die auf
 * 14 Zoll lesbar sein muss, ist ein Fehler, keine Geschmacksfrage. Zwei
 * Faelle, weil das Projekt beide Kuerzungsarten nutzt (siehe Spec-Korrektur
 * zu index.css Zeile 27):
 *   (a) text-overflow: ellipsis (Tailwind "truncate") mit echtem Ueberstand
 *       (scrollWidth > clientWidth + 1),
 *   (b) -webkit-line-clamp gesetzt (Projekt-Klasse ".line-clamp-2", OHNE
 *       text-overflow: ellipsis) mit echtem Ueberstand
 *       (scrollHeight > clientHeight + 1).
 * Ausnahme: das Element selbst oder ein Vorfahre traegt
 * data-kuerzung-erlaubt (heute genau zwei Faelle: Nutzername in der
 * Menueleiste -- voller Name im Menue darunter -- und Kartentitel mit
 * line-clamp-2 -- voller Name im title-Attribut).
 */
export async function keinTextGekuerzt(page: Page): Promise<void> {
    const treffer = await page.evaluate(() => {
        const hatAusnahme = (el: Element): boolean => {
            for (let k: Element | null = el; k != null; k = k.parentElement) {
                if (k.hasAttribute('data-kuerzung-erlaubt')) return true;
            }
            return false;
        };

        // Gleiche Ausnahme wie in keinHorizontalerUeberlauf (siehe Kommentar
        // dort): Element oder Vorfahre faktisch 1x1px UND abgeschnitten/
        // geklippt (Tailwind ".sr-only") -- eine zweite, von
        // data-kuerzung-erlaubt unabhaengige Ausnahme.
        const istUnsichtbarVersteckt = (el: Element): boolean => {
            for (let k: Element | null = el; k != null; k = k.parentElement) {
                if (k.clientWidth > 1 || k.clientHeight > 1) continue;
                const kStil = getComputedStyle(k);
                const nichtSichtbarerOverflow = kStil.overflowX !== 'visible' || kStil.overflowY !== 'visible';
                const geklippt = kStil.clip === 'rect(0px, 0px, 0px, 0px)' || (kStil.clipPath !== 'none' && kStil.clipPath !== '');
                if (nichtSichtbarerOverflow || geklippt) return true;
            }
            return false;
        };

        const ergebnis: { beschreibung: string; text: string; art: string }[] = [];
        for (const el of Array.from(document.querySelectorAll<HTMLElement>('*'))) {
            const stil = getComputedStyle(el);
            const lineClampWert = stil.getPropertyValue('-webkit-line-clamp');

            const perEllipsis = stil.textOverflow === 'ellipsis' && el.scrollWidth > el.clientWidth + 1;
            const perLineClamp = lineClampWert !== '' && lineClampWert !== 'none' && el.scrollHeight > el.clientHeight + 1;
            if (!perEllipsis && !perLineClamp) continue;
            if (hatAusnahme(el)) continue;
            if (istUnsichtbarVersteckt(el)) continue;

            const klassen = el.classList.length > 0 ? `.${Array.from(el.classList).join('.')}` : '';
            const text = (el.textContent ?? '').trim();
            ergebnis.push({
                beschreibung: `${el.tagName.toLowerCase()}${klassen}`,
                text,
                art: perEllipsis ? 'text-overflow: ellipsis' : '-webkit-line-clamp',
            });
        }
        return ergebnis;
    });

    expect(
        treffer,
        `Text ist gekuerzt, ohne data-kuerzung-erlaubt:\n${treffer
            .map((t) => `${t.beschreibung} (${t.art}) -- vollstaendiger Text: "${t.text}"`)
            .join('\n')}`,
    ).toEqual([]);
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
