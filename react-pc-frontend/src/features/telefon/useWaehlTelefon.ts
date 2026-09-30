import { useCallback, useSyncExternalStore } from 'react';

/**
 * „Telefon an diesem Rechner": das Telefon der FRITZ!Box, das beim
 * Zurückrufen zuerst klingelt (meist das Telefon-Programm mit Headset).
 *
 * <p>Gilt pro Rechner, nicht pro Benutzer – deshalb liegt es im Browser
 * (`localStorage`) und nicht auf dem Server. Alle Stellen, die den Hook
 * nutzen, sehen eine Änderung sofort; auch aus anderen Tabs.</p>
 */

export const WAEHL_TELEFON_SCHLUESSEL = 'telefon.waehlTelefon';

const zuhoerer = new Set<() => void>();

function melde() {
    zuhoerer.forEach((z) => z());
}

/** Gespeichertes Telefon oder `null`. Ohne Zugriff auf den Speicher (z. B. gesperrt) ebenfalls `null`. */
export function liesWaehlTelefon(): string | null {
    try {
        const wert = window.localStorage.getItem(WAEHL_TELEFON_SCHLUESSEL);
        return wert && wert.trim() ? wert : null;
    } catch {
        return null;
    }
}

function schreibe(name: string | null) {
    try {
        if (name && name.trim()) window.localStorage.setItem(WAEHL_TELEFON_SCHLUESSEL, name);
        else window.localStorage.removeItem(WAEHL_TELEFON_SCHLUESSEL);
    } catch {
        // Speicher gesperrt – dann wird beim nächsten Mal wieder gefragt.
    }
    melde();
}

function abonniere(zuhoererFn: () => void) {
    zuhoerer.add(zuhoererFn);
    const ausAnderemTab = (ereignis: StorageEvent) => {
        if (ereignis.key === null || ereignis.key === WAEHL_TELEFON_SCHLUESSEL) zuhoererFn();
    };
    window.addEventListener('storage', ausAnderemTab);
    return () => {
        zuhoerer.delete(zuhoererFn);
        window.removeEventListener('storage', ausAnderemTab);
    };
}

export function useWaehlTelefon() {
    const telefon = useSyncExternalStore(abonniere, liesWaehlTelefon, () => null);
    const setzeTelefon = useCallback((name: string) => schreibe(name), []);
    const vergiss = useCallback(() => schreibe(null), []);
    return { telefon, setzeTelefon, vergiss };
}
