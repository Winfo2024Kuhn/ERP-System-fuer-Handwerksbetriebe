import type { KeyboardEvent } from 'react';
import { fokussierbareIn } from '../../../lib/modalEbene';

/** Verhindert, dass ein Klick in die Leiste dem Text Fokus und Markierung nimmt. */
export const auswahlBehalten = (event: { preventDefault: () => void }) => event.preventDefault();

/** Pfeil links/rechts wandert durch die Knöpfe einer Leiste (Toolbar-Muster). */
export function pfeilNavigation(event: KeyboardEvent<HTMLElement>) {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    const knoepfe = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>('button:not(:disabled)'));
    const index = knoepfe.indexOf(document.activeElement as HTMLButtonElement);
    if (index < 0) return;
    event.preventDefault();
    const schritt = event.key === 'ArrowRight' ? 1 : -1;
    knoepfe[(index + schritt + knoepfe.length) % knoepfe.length]?.focus();
}

/**
 * Tab aus der Link-Leiste: Fokus auf das nächste per Tab erreichbare Element hinter
 * dem Feld (im obersten Dialog bzw. auf der Seite).
 */
export function fokussiereNaechstesNach(editor: HTMLElement | null): void {
    if (!editor) return;
    const wurzel = editor.closest<HTMLElement>('[role="dialog"][aria-modal="true"]') ?? editor.ownerDocument.body;
    fokussierbareIn(wurzel).find(element => !editor.contains(element)
        && !!(editor.compareDocumentPosition(element) & Node.DOCUMENT_POSITION_FOLLOWING))?.focus();
}
