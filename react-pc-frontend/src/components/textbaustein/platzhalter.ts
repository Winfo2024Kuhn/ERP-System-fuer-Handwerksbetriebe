import { parseHtmlInert, sanitizeHtml } from '../../lib/htmlSanitizer';
import type { Platzhalter } from './typen';

/**
 * Platzhalter als Chips mit Beispielwert.
 *
 * Gespeichert wird immer das Kürzel (`{{Kundenname}}`), angezeigt wird ein Chip
 * „Kundenname · Max Mustermann“. Karte und Editor bauen den Chip aus denselben
 * Attributen ({@link chipAttribute}), das Aussehen kommt aus `index.css`
 * (`[data-platzhalter-chip]`).
 */

/** Attribut am Chip; sein Wert ist das Kürzel genau so, wie es im Text stand. */
export const CHIP_ATTRIBUT = 'data-platzhalter-chip';

/** Bezeichnung des Chips für Kürzel, die das Programm nicht kennt. */
export const UNBEKANNT_LABEL = 'Unbekannter Platzhalter';

/**
 * Findet `{{…}}`. Begrenzte Länge und keine verschachtelte Wiederholung, damit
 * auch sehr lange Texte ohne Rückverfolgungs-Lawine durchlaufen (ReDoS).
 */
const TOKEN_QUELLE = /\{\{([^{}]{1,80})\}\}/.source;

/** Jedes Mal ein frisches Muster: ein geteiltes /g-Muster trägt `lastIndex` von Aufruf zu Aufruf. */
function findeKuerzel(text: string): RegExpMatchArray[] {
    return Array.from(text.matchAll(new RegExp(TOKEN_QUELLE, 'g')));
}

export interface ChipDarstellung {
    /** Originales Kürzel, z. B. `{{Kundenname}}` – wird beim Speichern wieder eingesetzt. */
    token: string;
    label: string;
    beispiel: string;
    bekannt: boolean;
}

/** Vergleichsschlüssel: ohne Klammern und Leerraum, Großschreibung egal. */
function schluessel(token: string): string {
    return token.replace(/[{}\s]/g, '').toUpperCase();
}

/** Sucht den Katalog-Eintrag zu einem Kürzel, Groß-/Kleinschreibung egal. */
export function findePlatzhalter(katalog: readonly Platzhalter[], token: string): Platzhalter | undefined {
    const gesucht = schluessel(token);
    return katalog.find((eintrag) => schluessel(eintrag.token) === gesucht);
}

/** Wie ein Kürzel als Chip erscheint: bekannte mit Bezeichnung und Beispiel, unbekannte grau mit dem Kürzel. */
export function chipDarstellung(katalog: readonly Platzhalter[], token: string): ChipDarstellung {
    const eintrag = findePlatzhalter(katalog, token);
    if (!eintrag) return { token, label: UNBEKANNT_LABEL, beispiel: token, bekannt: false };
    return { token, label: eintrag.label, beispiel: eintrag.beispiel, bekannt: true };
}

/** HTML-Attribute des äußeren Chip-Elements – gemeinsam für Karte und Editor. */
export function chipAttribute(chip: ChipDarstellung): Record<string, string> {
    return {
        [CHIP_ATTRIBUT]: chip.token,
        'data-platzhalter-bekannt': String(chip.bekannt),
        'data-label': chip.label,
        'data-beispiel': chip.beispiel,
    };
}

/** Liest die Darstellung aus einem Chip-Element zurück (Editor: Einlesen von HTML). */
export function chipAusElement(element: HTMLElement): ChipDarstellung {
    const token = element.getAttribute(CHIP_ATTRIBUT) ?? '';
    return {
        token,
        label: element.getAttribute('data-label') ?? UNBEKANNT_LABEL,
        beispiel: element.getAttribute('data-beispiel') ?? token,
        bekannt: element.getAttribute('data-platzhalter-bekannt') === 'true',
    };
}

function erzeugeChip(dokument: Document, chip: ChipDarstellung): HTMLElement {
    const aussen = dokument.createElement('span');
    Object.entries(chipAttribute(chip)).forEach(([name, wert]) => aussen.setAttribute(name, wert));
    const bezeichnung = dokument.createElement('span');
    bezeichnung.setAttribute('data-chip-bezeichnung', '');
    bezeichnung.textContent = chip.label;
    const beispiel = dokument.createElement('span');
    beispiel.setAttribute('data-chip-beispiel', '');
    beispiel.textContent = chip.beispiel;
    aussen.append(bezeichnung, beispiel);
    return aussen;
}

function textknoten(wurzel: HTMLElement): Text[] {
    const walker = wurzel.ownerDocument.createTreeWalker(wurzel, NodeFilter.SHOW_TEXT);
    const knoten: Text[] = [];
    for (let k = walker.nextNode(); k; k = walker.nextNode()) {
        if (!(k.parentElement?.closest(`[${CHIP_ATTRIBUT}]`))) knoten.push(k as Text);
    }
    return knoten;
}

function ersetzeKuerzelImText(knoten: Text, katalog: readonly Platzhalter[]): void {
    const text = knoten.data;
    const alleTreffer = findeKuerzel(text);
    if (alleTreffer.length === 0) return;
    const dokument = knoten.ownerDocument;
    const teile = dokument.createDocumentFragment();
    let rest = 0;
    for (const treffer of alleTreffer) {
        const start = treffer.index ?? 0;
        if (start > rest) teile.append(text.slice(rest, start));
        teile.append(erzeugeChip(dokument, chipDarstellung(katalog, treffer[0])));
        rest = start + treffer[0].length;
    }
    if (rest < text.length) teile.append(text.slice(rest));
    knoten.replaceWith(teile);
}

/**
 * Bereinigt gespeichertes HTML und ersetzt jedes `{{…}}` im Text durch einen
 * Chip. Kürzel in Attributen (z. B. in einem Link) bleiben unberührt.
 */
export function kuerzelZuChipHtml(html: string | null | undefined, katalog: readonly Platzhalter[]): string {
    const wurzel = parseHtmlInert(sanitizeHtml(html));
    textknoten(wurzel).forEach((knoten) => ersetzeKuerzelImText(knoten, katalog));
    return wurzel.innerHTML;
}

/** Macht aus jedem Chip wieder genau sein ursprüngliches Kürzel (zum Speichern). */
export function chipHtmlZuKuerzel(html: string | null | undefined): string {
    const wurzel = parseHtmlInert(html);
    wurzel.querySelectorAll<HTMLElement>(`[${CHIP_ATTRIBUT}]`).forEach((chip) => {
        chip.replaceWith(wurzel.ownerDocument.createTextNode(chip.getAttribute(CHIP_ATTRIBUT) ?? ''));
    });
    return wurzel.innerHTML;
}

/** Die Kürzel, die im Text wirklich vorkommen – jedes einmal, in der Schreibweise des Textes. */
export function verwendeteKuerzel(html: string | null | undefined): string[] {
    const text = parseHtmlInert(html).textContent ?? '';
    return [...new Set(findeKuerzel(text).map((treffer) => treffer[0]))];
}

/** Gruppiert den Katalog nach `gruppe`, in der Reihenfolge des Backends. */
export function platzhalterNachGruppe(katalog: readonly Platzhalter[]): { gruppe: string; eintraege: Platzhalter[] }[] {
    const gruppen = new Map<string, Platzhalter[]>();
    katalog.forEach((eintrag) => {
        const liste = gruppen.get(eintrag.gruppe) ?? [];
        liste.push(eintrag);
        gruppen.set(eintrag.gruppe, liste);
    });
    return Array.from(gruppen, ([gruppe, eintraege]) => ({ gruppe, eintraege }));
}
