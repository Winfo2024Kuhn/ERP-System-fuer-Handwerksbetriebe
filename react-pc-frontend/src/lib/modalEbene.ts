/**
 * Modale Ebenen (gemeinsamer `Dialog`, Bild- und PDF-Vorschau) erkennen sich gegenseitig
 * über `role="dialog" aria-modal="true"` und den z-index. Nur die oberste Ebene reagiert
 * auf Escape, Tab und Fokuswechsel; die darunter halten still.
 */

/** z-index des gemeinsamen Dialogs ohne Verschachtelung; jede Ebene darin liegt eins höher. */
export const DIALOG_Z_BASIS = 50;

/**
 * z-index der Vorschauen (ImageViewer, DocumentPreviewModal): über normalen Dialogen, aber
 * unter dem Anruf-Fenster (70), damit ein eingehender Anruf immer sichtbar bleibt.
 */
export const VORSCHAU_Z_INDEX = 65;

/**
 * z-index einer Vorschau, die in einem Dialog der Tiefe `tiefe` hängt (Wert aus `useDialogTiefe`):
 * mindestens `VORSCHAU_Z_INDEX`, aber immer über dem Dialog, aus dem sie kommt. Aus einem Dialog
 * im Anruf-Fenster (`DialogEbene`) heraus liegt sie damit auch über dem Anruf.
 */
export function vorschauZIndex(tiefe: number): number {
    return Math.max(VORSCHAU_Z_INDEX, DIALOG_Z_BASIS + tiefe + 1);
}

export const FOKUS_SELEKTOR = 'button, a[href], input, select, textarea, [tabindex], [contenteditable="true"]';

/** `false`, wenn das Element oder ein Vorfahre versteckt, inert oder aria-hidden ist. */
export function sichtbar(element: HTMLElement): boolean {
    for (let node: HTMLElement | null = element; node; node = node.parentElement) {
        const style = getComputedStyle(node);
        if (node.hidden || node.hasAttribute('inert') || node.getAttribute('aria-hidden') === 'true'
            || style.display === 'none' || style.visibility === 'hidden') return false;
    }
    return true;
}

/** Sichtbare modale Ebene mit dem höchsten z-index; bei Gleichstand die spätere im DOM. */
export function obersteModaleEbene(ausser?: HTMLElement): HTMLElement | undefined {
    let ergebnis: HTMLElement | undefined;
    let hoechster = -Infinity;
    for (const ebene of document.querySelectorAll<HTMLElement>('[role="dialog"][aria-modal="true"]')) {
        if (ebene === ausser || !sichtbar(ebene)) continue;
        let zIndex = 0;
        for (let node: HTMLElement | null = ebene; node; node = node.parentElement) {
            zIndex = Math.max(zIndex, Number.parseInt(getComputedStyle(node).zIndex) || 0);
        }
        if (zIndex >= hoechster) { hoechster = zIndex; ergebnis = ebene; }
    }
    return ergebnis;
}

/** Per Tab erreichbare, aktive und sichtbare Elemente innerhalb von `wurzel`, in DOM-Reihenfolge. */
export function fokussierbareIn(wurzel: HTMLElement): HTMLElement[] {
    return Array.from(wurzel.querySelectorAll<HTMLElement>(FOKUS_SELEKTOR))
        .filter(element => element.tabIndex >= 0 && !element.matches(':disabled') && sichtbar(element));
}

/** Sichtbare Toast-Bereiche: bleiben bei offenem Dialog bzw. offener Vorschau per Tastatur erreichbar. */
export function meldungsBereiche(): HTMLElement[] {
    return Array.from(document.querySelectorAll<HTMLElement>('[data-pc-toasts]')).filter(sichtbar);
}
