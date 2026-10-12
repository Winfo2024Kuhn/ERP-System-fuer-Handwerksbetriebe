/**
 * Holt ein Element mittig in den sichtbaren Bereich – wie
 * `scrollIntoView({ block: 'center' })`, aber nur über Container, die der
 * Nutzer auch selbst scrollen kann (`overflow-y: auto | scroll`).
 *
 * Warum nicht scrollIntoView: Das scrollt jeden Vorfahren mit, auch Hüllen mit
 * `overflow-hidden` (z. B. `h-dvh … overflow-hidden` in MainLayout). Die lassen
 * sich danach von Hand nicht mehr zurückscrollen – die Seite rutscht unter die
 * Menüleiste, und unten bleibt eine leere Fläche.
 *
 * Nur senkrecht; waagerecht bleibt alles stehen. Bei „Bewegung reduzieren“
 * springt die Ansicht ohne Animation.
 */
export function scrolleMittigInDenBlick(element: HTMLElement): void {
    // Ein fixiertes Element bewegt sich mit keinem Container – nichts zu scrollen.
    if (window.getComputedStyle(element).position === 'fixed') return;

    const reduziert = typeof window.matchMedia === 'function'
        && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const behavior: ScrollBehavior = reduziert ? 'auto' : 'smooth';
    const { top, height: hoehe } = element.getBoundingClientRect();
    // Lage des Elements im Fenster – wandert mit, sobald ein innerer Container scrollt.
    let elementOben = top;

    for (let container = element.parentElement; container; container = container.parentElement) {
        const stil = window.getComputedStyle(container);
        const istDokument = container === document.scrollingElement;
        if (istDokument || istNutzerScrollbar(stil)) {
            elementOben -= zentriere(container, istDokument, elementOben, hoehe, behavior);
        }
        // Alles außerhalb eines fixierten Vorfahren (z. B. die Seite hinter einem
        // Dialog) bewegt das Element nicht mehr – dort aufhören, wie scrollIntoView.
        if (stil.position === 'fixed') break;
    }
}

/** Scrollt den Container so, dass das Element mittig steht; gibt die Verschiebung zurück. */
function zentriere(container: HTMLElement, istDokument: boolean, elementOben: number, hoehe: number, behavior: ScrollBehavior): number {
    const maxScroll = container.scrollHeight - container.clientHeight;
    if (maxScroll <= 0 || typeof container.scrollTo !== 'function') return 0;

    const sichtOben = istDokument ? 0 : container.getBoundingClientRect().top + container.clientTop;
    const mittig = container.scrollTop + elementOben - sichtOben - (container.clientHeight - hoehe) / 2;
    const ziel = Math.min(Math.max(mittig, 0), maxScroll);
    const verschiebung = ziel - container.scrollTop;
    container.scrollTo({ top: ziel, behavior });
    return verschiebung;
}

function istNutzerScrollbar(stil: CSSStyleDeclaration): boolean {
    return stil.overflowY === 'auto' || stil.overflowY === 'scroll' || stil.overflowY === 'overlay';
}
