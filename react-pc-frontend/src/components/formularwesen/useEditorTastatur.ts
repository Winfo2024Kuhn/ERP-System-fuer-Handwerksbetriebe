import { useEffect, useRef } from 'react';

export interface EditorTastaturAktionen {
    /** Ist ein Baustein ausgewählt? Ohne Auswahl wirken nur Rückgängig/Wiederholen. */
    hatAuswahl: boolean;
    verschieben: (dx: number, dy: number) => void;
    loeschen: () => void;
    duplizieren: () => void;
    auswahlAufheben: () => void;
    rueckgaengig: () => void;
    wiederholen: () => void;
}

/** Schritt der Pfeiltasten in pt; mit Umschalt das Zehnfache. */
export const PFEIL_SCHRITT = 1;
export const PFEIL_SCHRITT_GROSS = 10;

/** Tippt jemand gerade in ein Feld, gehören Pfeile, Entf und Strg+Z dem Feld. */
function istEingabe(ziel: EventTarget | null): boolean {
    if (!(ziel instanceof HTMLElement)) return false;
    if (ziel.isContentEditable) return true;
    return ziel.closest('input, textarea, select, [contenteditable="true"]') !== null;
}

/** Liegt ein Dialog (Vorschau, Speichern, Bestätigung) über dem Editor? */
const dialogOffen = () => document.querySelector('[role="dialog"][aria-modal="true"]') !== null;

/**
 * Tastenkürzel des Vorlagen-Editors:
 * Pfeile verschieben (Umschalt = 10 pt), Entf/Rücktaste löscht, Strg/Cmd+Z
 * macht rückgängig, Strg/Cmd+Y oder Strg/Cmd+Umschalt+Z wiederholt,
 * Strg/Cmd+D dupliziert, Escape hebt die Auswahl auf.
 */
export function useEditorTastatur(aktionen: EditorTastaturAktionen, aktiv = true): void {
    // Immer die neuesten Aktionen benutzen, ohne den Listener neu anzumelden.
    const ref = useRef(aktionen);
    useEffect(() => { ref.current = aktionen; });

    useEffect(() => {
        if (!aktiv) return;
        const beiTaste = (e: KeyboardEvent) => {
            if (e.defaultPrevented || istEingabe(e.target) || dialogOffen()) return;
            const a = ref.current;
            const strg = e.ctrlKey || e.metaKey;
            const taste = e.key.toLowerCase();

            if (strg && taste === 'z') {
                e.preventDefault();
                if (e.shiftKey) a.wiederholen(); else a.rueckgaengig();
                return;
            }
            if (strg && taste === 'y') {
                e.preventDefault();
                a.wiederholen();
                return;
            }
            if (!a.hatAuswahl) return;
            if (strg && taste === 'd') {
                e.preventDefault();
                a.duplizieren();
                return;
            }
            if (strg || e.altKey) return;

            const schritt = e.shiftKey ? PFEIL_SCHRITT_GROSS : PFEIL_SCHRITT;
            switch (e.key) {
                case 'ArrowLeft': e.preventDefault(); a.verschieben(-schritt, 0); break;
                case 'ArrowRight': e.preventDefault(); a.verschieben(schritt, 0); break;
                case 'ArrowUp': e.preventDefault(); a.verschieben(0, -schritt); break;
                case 'ArrowDown': e.preventDefault(); a.verschieben(0, schritt); break;
                case 'Delete':
                case 'Backspace': e.preventDefault(); a.loeschen(); break;
                case 'Escape': e.preventDefault(); a.auswahlAufheben(); break;
            }
        };
        window.addEventListener('keydown', beiTaste);
        return () => window.removeEventListener('keydown', beiTaste);
    }, [aktiv]);
}
