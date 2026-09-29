import { useEffect, useState } from 'react';
import { ladeAnzahlNeueNachrichten } from './api';

/**
 * Zahl der noch nicht abgehörten Nachrichten – für den Zähler am Menüpunkt
 * „Anrufbeantworter".
 *
 * <p>Lädt beim Einbinden, jede Minute und sofort, wenn irgendwo
 * `notifications:refresh` gefeuert wird (z. B. nach dem Abhören). Ohne
 * Telefon-Recht (`aktiv = false`) passiert gar nichts.</p>
 */
export function useNeueSprachnachrichten(aktiv: boolean): number {
    const [anzahl, setAnzahl] = useState(0);

    useEffect(() => {
        if (!aktiv) return;
        let abbruch: AbortController | null = null;
        const aktualisiere = () => {
            abbruch?.abort();
            abbruch = new AbortController();
            const signal = abbruch.signal;
            ladeAnzahlNeueNachrichten(signal)
                .then((neu) => {
                    if (!signal.aborted) setAnzahl(neu);
                })
                .catch(() => {
                    // Nur ein Zähler – bleibt beim letzten Stand, nächster Abruf korrigiert.
                });
        };
        aktualisiere();
        const intervall = window.setInterval(aktualisiere, 60_000);
        window.addEventListener('notifications:refresh', aktualisiere);
        return () => {
            abbruch?.abort();
            window.clearInterval(intervall);
            window.removeEventListener('notifications:refresh', aktualisiere);
        };
    }, [aktiv]);

    return aktiv ? anzahl : 0;
}
