import { useEffect, useRef } from 'react';
import { useBlocker, type BlockerFunction } from 'react-router-dom';

export interface NavigationsSperreOptionen {
    /** Wann ein Seiten- oder Adresswechsel angehalten wird: immer (`true`), nie (`false`) oder je Wechsel. */
    sperren: boolean | BlockerFunction;
    /**
     * Wird beim angehaltenen Wechsel einmal aufgerufen. `true` lässt den Wechsel
     * durch, `false` bleibt auf der Seite (z. B. Rückfrage „Verwerfen?“ oder
     * erst den Entwurf sichern).
     */
    entscheiden: () => Promise<boolean>;
    /** Beim Schließen oder Neuladen des Browserfensters die Browser-Warnung zeigen. */
    beimSchliessenWarnen?: boolean;
}

/**
 * Hält Wechsel innerhalb der App (Links, Zurück/Vorwärts, Adressänderungen)
 * an, solange etwas Ungesichertes offen ist, und lässt `entscheiden` sagen, ob
 * es weitergeht. Braucht einen Data-Router (`createBrowserRouter`).
 *
 * Genutzt von „Leistungen & Kategorien“ (Rückfrage bei ungespeicherten
 * Änderungen) und dem E-Mail-Center (Entwurf vor dem Wechsel sichern).
 */
export function useNavigationsSperre({ sperren, entscheiden, beimSchliessenWarnen = false }: NavigationsSperreOptionen) {
    const blocker = useBlocker(sperren);

    // Immer die aktuelle Entscheidung nehmen, ohne bei jeder neuen Funktion erneut zu fragen.
    const entscheidenRef = useRef(entscheiden);
    useEffect(() => { entscheidenRef.current = entscheiden; }, [entscheiden]);

    useEffect(() => {
        if (blocker.state !== 'blocked') return;
        let gueltig = true;
        void entscheidenRef.current().then((weiter) => {
            if (!gueltig) return;
            if (weiter) blocker.proceed(); else blocker.reset();
        });
        return () => { gueltig = false; };
    }, [blocker]);

    useEffect(() => {
        if (!beimSchliessenWarnen) return;
        const warnen = (e: BeforeUnloadEvent) => { e.preventDefault(); };
        window.addEventListener('beforeunload', warnen);
        return () => window.removeEventListener('beforeunload', warnen);
    }, [beimSchliessenWarnen]);
}
