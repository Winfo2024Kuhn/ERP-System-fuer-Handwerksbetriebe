import { useEffect, useLayoutEffect, useRef } from 'react';

/** Hier gehört Entf/⌫ dem Feld, dem Editor oder dem Dialog – nicht dem Löschen. */
const EIGENE_TASTEN = [
    'input', 'textarea', 'select',
    '[contenteditable]:not([contenteditable="false"])',
    '[role="dialog"]', '[role="alertdialog"]', '[role="menu"]', '[role="listbox"]', '[role="combobox"]',
].join(', ');

/** `true`, wenn ein Tastendruck an diesem Ziel nicht als „Löschen“ gelten darf. */
export function istEigeneTasteneingabe(ziel: EventTarget | null): boolean {
    if (!(ziel instanceof Element)) return false;
    if (ziel.closest(EIGENE_TASTEN)) return true;
    // Ein offener Dialog sperrt das Löschen auch dann, wenn der Fokus gerade außerhalb liegt.
    return document.querySelector('[role="dialog"][aria-modal="true"], [role="alertdialog"]') !== null;
}

/**
 * Entf und ⌫ (Backspace) lösen `loeschen` aus – nur ohne Zusatztaste, nicht beim Gedrückthalten
 * und nie, solange der Fokus in einem Eingabefeld, Editor, Menü oder Dialog liegt.
 */
export function useLoeschTaste(aktiv: boolean, loeschen: () => void) {
    const loeschenRef = useRef(loeschen);
    useLayoutEffect(() => { loeschenRef.current = loeschen; });

    useEffect(() => {
        if (!aktiv) return;
        const beiTaste = (ereignis: KeyboardEvent) => {
            if (ereignis.key !== 'Delete' && ereignis.key !== 'Backspace') return;
            if (ereignis.metaKey || ereignis.ctrlKey || ereignis.altKey || ereignis.shiftKey || ereignis.repeat) return;
            if (ereignis.defaultPrevented || istEigeneTasteneingabe(ereignis.target)) return;
            ereignis.preventDefault();
            loeschenRef.current();
        };
        window.addEventListener('keydown', beiTaste);
        return () => window.removeEventListener('keydown', beiTaste);
    }, [aktiv]);
}
