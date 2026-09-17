import { useEffect, useRef, type RefObject } from 'react';

export interface VerlaufTastaturOptionen {
    /** false = gesperrtes Dokument ODER ein Dialog des Editors ist offen. */
    aktiv: boolean;
    wurzelRef: RefObject<HTMLElement | null>;
    onRueckgaengig: () => void;
    onWiederholen: () => void;
}

/**
 * Entscheidet, ob eine Rueckgaengig/Wiederholen-Taste an dieser Stelle greifen
 * darf. `ziel` ist typischerweise `event.target` eines Tastatur-Events.
 *
 * Zwei Ausschlussgruende, unabhaengig vom genauen Fokusort:
 *  - Irgendwo im Dokument ist ein modaler Dialog offen (`[aria-modal="true"]`):
 *    dessen eigene Tastaturbedienung hat Vorrang, egal wo der Fokus technisch
 *    gerade steht.
 *  - Das Ziel liegt in einem Feld mit `data-eigenes-rueckgaengig` (Bauabschnitts-
 *    Name, Auswahl-Name, Rechnungsadresse): dort gilt das normale Rueckgaengig
 *    des Eingabefelds, nicht der Dokument-Verlauf.
 *
 * Sonderfall `document.body`: Nach dem Loeschen des fokussierten Elements
 * (z.B. eine geloeschte Position) haengt der Browser den Fokus automatisch an
 * `<body>`. Das ist der Hauptfall fuer Strg+Z direkt nach einem Loeschen und
 * zaehlt deshalb bewusst als "innerhalb", obwohl body kein Nachfahre von
 * `wurzel` ist, sondern umgekehrt ein Vorfahre.
 */
export function darfVerlaufTasteGreifen(ziel: EventTarget | null, wurzel: HTMLElement | null): boolean {
    if (document.querySelector('[aria-modal="true"]')) return false;
    if (!wurzel || !(ziel instanceof Node)) return false;
    if (ziel !== document.body && !wurzel.contains(ziel)) return false;
    if (ziel instanceof Element && ziel.closest('[data-eigenes-rueckgaengig]')) return false;
    return true;
}

function istRueckgaengigTaste(e: KeyboardEvent): boolean {
    if (e.altKey) return false;
    return (e.ctrlKey || e.metaKey) && !e.shiftKey && e.key.toLowerCase() === 'z';
}

function istWiederholenTaste(e: KeyboardEvent): boolean {
    if (e.altKey) return false;
    const taste = e.key.toLowerCase();
    if (e.ctrlKey && !e.metaKey && !e.shiftKey && taste === 'y') return true;
    return (e.ctrlKey || e.metaKey) && e.shiftKey && taste === 'z';
}

/**
 * Globaler Tastatur-Handler fuer Strg+Z / Strg+Y (bzw. Cmd auf macOS) im
 * Dokument-Editor. Sitzt bewusst im CAPTURE-Modus auf `window`, damit er VOR
 * ProseMirrors eigenem Rueckgaengig und vor dem Browser-eigenen Rueckgaengig
 * greift.
 *
 * Tastenerkennung ueber `e.key.toLowerCase()`, nicht `e.code`: auf QWERTZ
 * liefert `key` den logischen Buchstaben (Ctrl+Z bleibt Ctrl+Z), genau wie es
 * fuer den Nutzer "Strg+Z" bedeutet.
 */
export function useVerlaufTastatur({
    aktiv,
    wurzelRef,
    onRueckgaengig,
    onWiederholen,
}: VerlaufTastaturOptionen): void {
    // Callbacks in Refs spiegeln (Vorbild useIdleTimer.ts:64-75): eine neue
    // Funktionsreferenz des Aufrufers bei jedem Render soll den Listener nicht
    // neu aufbauen. Schreiben bewusst in einem Effekt OHNE Dep-Array.
    const onRueckgaengigRef = useRef(onRueckgaengig);
    const onWiederholenRef = useRef(onWiederholen);
    useEffect(() => {
        onRueckgaengigRef.current = onRueckgaengig;
        onWiederholenRef.current = onWiederholen;
    });

    useEffect(() => {
        if (!aktiv) return;

        const handler = (e: KeyboardEvent) => {
            const istRueckgaengig = istRueckgaengigTaste(e);
            const istWiederholen = !istRueckgaengig && istWiederholenTaste(e);
            if (!istRueckgaengig && !istWiederholen) return;
            if (!darfVerlaufTasteGreifen(e.target, wurzelRef.current)) return;

            e.preventDefault();
            e.stopPropagation();
            if (istRueckgaengig) {
                onRueckgaengigRef.current();
            } else {
                onWiederholenRef.current();
            }
        };

        window.addEventListener('keydown', handler, true);
        return () => {
            window.removeEventListener('keydown', handler, true);
        };
    }, [aktiv, wurzelRef]);
}
