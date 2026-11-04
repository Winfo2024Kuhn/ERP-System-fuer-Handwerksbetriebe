import { useLayoutEffect, useRef } from 'react';
import { useDialogTiefe } from '../components/ui/dialogTiefe';
import { fokussierbareIn, meldungsBereiche, obersteModaleEbene, vorschauZIndex } from '../lib/modalEbene';

interface VorschauFokusOptionen {
    /** Nur solange `true`, hält der Hook Fokus, Tasten und Scroll-Sperre. */
    offen: boolean;
    onClose: () => void;
    /**
     * Weitere Tasten der Vorschau (z. B. Zoom). Gibt `true` zurück, wenn die Taste behandelt
     * wurde; dann sieht die Seite darunter sie nicht.
     */
    onTaste?: (ereignis: KeyboardEvent) => boolean;
}

/** Liegt das Ziel in einem Toast-Bereich? Der bleibt bei offener Vorschau erreichbar (wie beim Dialog). */
function inMeldungen(ziel: Node | null): boolean {
    return !!ziel && meldungsBereiche().some(bereich => bereich.contains(ziel));
}

/** Tab-Reihenfolge: erst das Fenster, dann die Toasts (Bereich selbst und seine Knöpfe). */
function tabReihenfolge(fenster: HTMLElement): HTMLElement[] {
    const meldungen = meldungsBereiche().flatMap(bereich => [
        ...(bereich.tabIndex >= 0 ? [bereich] : []), ...fokussierbareIn(bereich),
    ]);
    return [...fokussierbareIn(fenster), ...meldungen];
}

/**
 * Fokus und Tasten einer Vollbild-Vorschau (ImageViewer, DocumentPreviewModal), die als
 * eigene modale Ebene über jedem Dialog liegt:
 * - Fokus beim Öffnen auf „Schließen“, Tab/Umschalt+Tab bleiben im Fenster; wie beim Dialog
 *   sind Toasts dabei erreichbar.
 * - Escape schließt nur die Vorschau. Gelauscht wird auf `document` in der Capture-Phase,
 *   vor dem Dialog darunter und allen Tasten-Lauschern der Seite. Escape in einem Toast
 *   schließt nichts, sondern bringt den Fokus zurück ins Fenster.
 * - Tasten mit Strg, Cmd oder Alt bleiben beim Browser (Zoom, Zurück).
 * - Beim Schließen geht der Fokus zurück an den Knopf, der die Vorschau geöffnet hat, sofern er
 *   noch da ist und keine neuere modale Ebene darüberliegt.
 * - Die Seite scrollt nicht mit; beim Schließen gilt wieder der vorherige Wert.
 *
 * Das Fenster braucht `role="dialog"`, `aria-modal="true"` und den zurückgegebenen `zIndex`
 * (inline), sonst hält es sich nicht für die oberste Ebene. Der liegt über dem Dialog, aus dem
 * die Vorschau kommt, und sonst unter dem Anruf-Fenster (`vorschauZIndex`).
 */
export function useVorschauFokus<T extends HTMLElement = HTMLButtonElement>({ offen, onClose, onTaste }: VorschauFokusOptionen) {
    const fensterRef = useRef<HTMLDivElement | null>(null);
    const schliessenRef = useRef<T | null>(null);
    const onCloseRef = useRef(onClose);
    const onTasteRef = useRef(onTaste);
    const zIndex = vorschauZIndex(useDialogTiefe());
    useLayoutEffect(() => {
        onCloseRef.current = onClose;
        onTasteRef.current = onTaste;
    });

    useLayoutEffect(() => {
        const fenster = fensterRef.current;
        if (!offen || !fenster) return;
        const opener = document.activeElement instanceof HTMLElement && !fenster.contains(document.activeElement)
            ? document.activeElement : null;
        const vorherigerUeberlauf = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        let letzterFokus: HTMLElement | null = null;

        const fokusHinein = () => {
            const ziel = letzterFokus?.isConnected && fenster.contains(letzterFokus)
                ? letzterFokus : schliessenRef.current ?? fokussierbareIn(fenster)[0] ?? fenster;
            ziel.focus();
        };
        const istOben = () => obersteModaleEbene() === fenster;

        const beiFokus = (ereignis: FocusEvent) => {
            if (!istOben()) return;
            const ziel = ereignis.target as Node | null;
            if (ziel && fenster.contains(ziel)) letzterFokus = ziel as HTMLElement;
            else if (!inMeldungen(ziel)) fokusHinein();
        };
        const beiTaste = (ereignis: KeyboardEvent) => {
            if (ereignis.defaultPrevented || !istOben()) return;
            if (ereignis.key === 'Escape') {
                ereignis.preventDefault();
                ereignis.stopPropagation();
                if (inMeldungen(ereignis.target as Node)) fokusHinein();
                else onCloseRef.current();
            } else if (ereignis.key === 'Tab') {
                // Fenster und Toasts liegen getrennt im DOM, darum die Reihenfolge selbst führen.
                ereignis.preventDefault();
                const elemente = tabReihenfolge(fenster);
                if (elemente.length === 0) { fenster.focus(); return; }
                const index = elemente.indexOf(document.activeElement as HTMLElement);
                const naechster = index < 0
                    ? (ereignis.shiftKey ? elemente.length - 1 : 0)
                    : (index + (ereignis.shiftKey ? -1 : 1) + elemente.length) % elemente.length;
                elemente[naechster].focus();
            } else if (ereignis.ctrlKey || ereignis.metaKey || ereignis.altKey) {
                // Browser-Zoom (Strg/Cmd +, −, 0) und -Navigation (Alt+Pfeil) nicht abfangen.
            } else if (onTasteRef.current?.(ereignis)) {
                ereignis.preventDefault();
                ereignis.stopPropagation();
            }
        };

        document.addEventListener('focusin', beiFokus, true);
        document.addEventListener('keydown', beiTaste, true);
        if (istOben()) fokusHinein();

        return () => {
            document.removeEventListener('focusin', beiFokus, true);
            document.removeEventListener('keydown', beiTaste, true);
            document.body.style.overflow = vorherigerUeberlauf;
            // Erst nachdem React das Portal entfernt hat; nie einer neueren Ebene den Fokus nehmen.
            queueMicrotask(() => {
                const oben = obersteModaleEbene(fenster);
                if (opener?.isConnected && (!oben || oben.contains(opener))) opener.focus();
            });
        };
    }, [offen]);

    return { fensterRef, schliessenRef, zIndex };
}
