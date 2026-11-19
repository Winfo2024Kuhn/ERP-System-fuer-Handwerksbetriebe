import { useState } from 'react';

/** Flacher Vergleich: gleiche Schlüssel, jeder Wert identisch (===). */
function gleich(a: Record<string, unknown>, b: Record<string, unknown>): boolean {
    const schluessel = Object.keys(b);
    return Object.keys(a).length === schluessel.length && schluessel.every(k => a[k] === b[k]);
}

/**
 * Merkt sich, was in der PDF-Vorschau steckt, und meldet, wenn sich davon etwas
 * geändert hat. Alles, was über `createPdfRequest` ins PDF geht, gehört in
 * `inhalt` (Blöcke, Datum, Kontext, Balken, Abschluss-Zeilen …), sonst bleibt
 * die Vorschau rechts alt.
 *
 * Abgeglichen wird beim Rendern (Zustand beim Rendern anpassen), damit Effekte
 * den Marker schon im selben Commit sehen. `schluessel` wechselt seine Identität
 * genau dann, wenn sich ein Feld ändert: als Abhängigkeit für die verzögerte
 * Vorschau und `erzeugeVorschau` statt einer zweiten Feldliste.
 *
 * @param beiAenderung läuft beim Rendern, einmal je Änderung (z. B. Vorschau als veraltet markieren).
 *        Darf nur eigenen State derselben Komponente setzen: kein Fetch, kein fremder State.
 */
export function useVorschauMarker<T extends Record<string, unknown>>(
    inhalt: T,
    beiAenderung?: () => void,
): { schluessel: T; geaendert: boolean } {
    const [vorher, setVorher] = useState(inhalt);
    const geaendert = !gleich(vorher, inhalt);
    if (geaendert) {
        setVorher(inhalt);
        beiAenderung?.();
    }
    return { schluessel: geaendert ? inhalt : vorher, geaendert };
}
