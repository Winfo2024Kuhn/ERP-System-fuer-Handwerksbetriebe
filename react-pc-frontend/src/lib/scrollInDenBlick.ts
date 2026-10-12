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
    const reduziert = typeof window.matchMedia === 'function'
        && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const behavior: ScrollBehavior = reduziert ? 'auto' : 'smooth';
    const elementRect = element.getBoundingClientRect();
    // Lage des Elements im Fenster – wandert mit, sobald ein innerer Container scrollt.
    let elementOben = elementRect.top;

    for (let container = element.parentElement; container; container = container.parentElement) {
        const istDokument = container === document.scrollingElement;
        if (!istDokument && !istNutzerScrollbar(container)) continue;
        const maxScroll = container.scrollHeight - container.clientHeight;
        if (maxScroll <= 0 || typeof container.scrollTo !== 'function') continue;

        const sichtOben = istDokument ? 0 : container.getBoundingClientRect().top + container.clientTop;
        const mittig = container.scrollTop + elementOben - sichtOben - (container.clientHeight - elementRect.height) / 2;
        const ziel = Math.min(Math.max(mittig, 0), maxScroll);
        const verschiebung = ziel - container.scrollTop;
        container.scrollTo({ top: ziel, behavior });
        elementOben -= verschiebung;
    }
}

function istNutzerScrollbar(element: Element): boolean {
    const { overflowY } = window.getComputedStyle(element);
    return overflowY === 'auto' || overflowY === 'scroll' || overflowY === 'overlay';
}
