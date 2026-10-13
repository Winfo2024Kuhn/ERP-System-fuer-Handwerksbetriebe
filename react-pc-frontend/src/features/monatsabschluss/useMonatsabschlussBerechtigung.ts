import { useEffect, useRef, useState } from 'react';
import { api } from './api';

/**
 * Darf der angemeldete Benutzer Monate abschließen und wieder öffnen?
 *
 * Lädt das Recht einmal beim Einhängen über
 * `GET /api/zeitverwaltung/monatsabschluesse/berechtigung`. Solange die
 * Antwort fehlt, ist der Wert `null`; schlägt der Abruf fehl, gilt `false`
 * (lieber einen Knopf zu wenig zeigen als einen, der am Server mit 403
 * scheitert). Über `onFehler` kann die Seite die Meldung zusätzlich anzeigen.
 */
export function useMonatsabschlussBerechtigung(onFehler?: (meldung: string) => void): boolean | null {
    const [darfMonatAbschliessen, setDarfMonatAbschliessen] = useState<boolean | null>(null);
    const onFehlerRef = useRef(onFehler);
    useEffect(() => { onFehlerRef.current = onFehler; }, [onFehler]);

    useEffect(() => {
        const controller = new AbortController();
        api.ladeBerechtigung(controller.signal)
            .then(data => { if (!controller.signal.aborted) setDarfMonatAbschliessen(data.darfMonatAbschliessen === true); })
            .catch(err => {
                if (controller.signal.aborted) return;
                setDarfMonatAbschliessen(false);
                onFehlerRef.current?.(err instanceof Error ? err.message : 'Das Abschlussrecht konnte nicht geprüft werden.');
            });
        return () => controller.abort();
    }, []);

    return darfMonatAbschliessen;
}
