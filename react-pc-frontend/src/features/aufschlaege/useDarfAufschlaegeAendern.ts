import { useEffect, useState } from 'react';

/**
 * Darf der angemeldete Benutzer die Aufschläge ändern (Recht „Aufschläge ändern“ der Abteilung, Administratoren immer)?
 * Fragt bei jedem Einhängen neu, merkt sich nichts zwischen Benutzern: Nach einem Benutzerwechsel kann so kein fremdes Recht stehen bleiben.
 * Solange die Antwort fehlt oder die Abfrage scheitert, gilt „nein“: Der Knopf bleibt dann einfach weg. Der Server prüft das Recht bei jedem Zugriff ohnehin selbst.
 */
export function useDarfAufschlaegeAendern(): boolean {
    const [darf, setDarf] = useState(false);
    useEffect(() => {
        const abbruch = new AbortController();
        fetch('/api/aufschlaege/berechtigung', { signal: abbruch.signal })
            .then(res => (res.ok ? res.json() as Promise<{ darfAendern?: boolean }> : { darfAendern: false }))
            .then(d => setDarf(d.darfAendern === true))
            .catch(() => { /* abgebrochen oder nicht erreichbar: der Knopf bleibt weg */ });
        return () => abbruch.abort();
    }, []);
    return darf;
}
