import { useCallback, useEffect, useState } from 'react';

/** Alle gemerkten Werte liegen unter diesem Präfix im Browser-Speicher. */
const PRAEFIX = 'erp.gemerkt.';
const EREIGNIS = 'erp-gemerkter-wert';

function lesen<T>(schluessel: string, standard: T, pruefen?: (wert: unknown) => wert is T): T {
    try {
        const roh = localStorage.getItem(PRAEFIX + schluessel);
        if (roh === null) return standard;
        const wert: unknown = JSON.parse(roh);
        if (pruefen && !pruefen(wert)) return standard;
        return wert as T;
    } catch {
        // Kein Speicher (privates Fenster) oder kaputter Eintrag: dann eben der Standard.
        return standard;
    }
}

/**
 * Ein Wert, den sich das Programm im Browser merkt – z. B. gewählte Filter
 * einer Suche. Er gilt **seitenweit**: Alle Stellen mit demselben Schlüssel
 * zeigen sofort denselben Wert (setzt man den Filter in einer Modellsuche,
 * steht er auch in der nächsten), und beim nächsten Öffnen ist er wieder da.
 *
 * <p>Gespeichert wird als JSON im `localStorage`. Mit `pruefen` lassen sich
 * veraltete oder kaputte Einträge verwerfen; dann gilt der Standard.</p>
 *
 * @param schluessel kurzer, eindeutiger Name, z. B. `ki-modellsuche-filter`
 * @param standard Wert, solange nichts gemerkt ist (muss stabil sein, z. B. eine Konstante)
 */
export function useGemerkterWert<T>(
    schluessel: string,
    standard: T,
    pruefen?: (wert: unknown) => wert is T,
): [T, (neu: T) => void] {
    const [wert, setWert] = useState<T>(() => lesen(schluessel, standard, pruefen));

    useEffect(() => {
        const aktualisieren = (event: Event) => {
            if (event instanceof CustomEvent) {
                // Gleiche Seite: Der Wert kommt direkt mit – klappt auch, wenn der Speicher gesperrt ist.
                const { schluessel: von, wert: neu } = event.detail as { schluessel: string; wert: T };
                if (von === schluessel) setWert(neu);
                return;
            }
            if (event instanceof StorageEvent && event.key !== PRAEFIX + schluessel) return;
            setWert(lesen(schluessel, standard, pruefen));
        };
        // Andere Stellen derselben Seite (eigenes Ereignis) und andere Tabs (storage).
        window.addEventListener(EREIGNIS, aktualisieren);
        window.addEventListener('storage', aktualisieren);
        return () => {
            window.removeEventListener(EREIGNIS, aktualisieren);
            window.removeEventListener('storage', aktualisieren);
        };
    }, [schluessel, standard, pruefen]);

    const merken = useCallback((neu: T) => {
        setWert(neu);
        try {
            localStorage.setItem(PRAEFIX + schluessel, JSON.stringify(neu));
        } catch {
            // Speicher voll oder gesperrt: Der Wert gilt dann nur bis zum Neuladen.
        }
        window.dispatchEvent(new CustomEvent(EREIGNIS, { detail: { schluessel, wert: neu } }));
    }, [schluessel]);

    return [wert, merken];
}
