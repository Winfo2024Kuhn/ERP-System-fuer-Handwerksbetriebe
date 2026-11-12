import { useState } from 'react';
import { useGemerkterWert } from '../../hooks/useGemerkterWert';

/** Liste (Tabelle) oder Vorschau (Kacheln mit Vorschaubild). */
export type DarstellungsArt = 'liste' | 'vorschau';

export interface Darstellung {
    art: DarstellungsArt;
    /** Breite einer Kachel in Pixeln. */
    groesse: number;
}

export const GROESSE_MIN = 96;
export const GROESSE_MAX = 320;
const STANDARD: Darstellung = { art: 'liste', groesse: 160 };
const SCHLUESSEL = 'firmendokumente.ansicht';

/** Gespeicherter Wert → gültige Darstellung. Unbekanntes wird zum Standard, die Größe bleibt in ihren Grenzen. */
export function normalisiereDarstellung(roh: unknown): Darstellung {
    if (!roh || typeof roh !== 'object') return STANDARD;
    const { art, groesse } = roh as Record<string, unknown>;
    if (art !== 'liste' && art !== 'vorschau') return STANDARD;
    if (typeof groesse !== 'number' || !Number.isFinite(groesse)) return { art, groesse: STANDARD.groesse };
    return { art, groesse: Math.round(Math.min(GROESSE_MAX, Math.max(GROESSE_MIN, groesse))) };
}

/**
 * Liste oder Vorschau und die Größe der Vorschaubilder – merkt sich dieser
 * Browser. Beim Ziehen am Regler gilt die Größe sofort (`setGroesse`),
 * gespeichert wird erst beim Loslassen (`groesseMerken`).
 */
export function useDarstellung() {
    const [roh, merken] = useGemerkterWert<unknown>(SCHLUESSEL, STANDARD);
    const gespeichert = normalisiereDarstellung(roh);
    const [entwurf, setEntwurf] = useState<number | null>(null);

    return {
        art: gespeichert.art,
        groesse: entwurf ?? gespeichert.groesse,
        setArt: (art: DarstellungsArt) => merken({ ...gespeichert, art }),
        setGroesse: (groesse: number) => setEntwurf(groesse),
        groesseMerken: (groesse: number) => {
            setEntwurf(null);
            merken(normalisiereDarstellung({ ...gespeichert, groesse }));
        },
    };
}
