import { useEffect, useState } from 'react';

/**
 * Fragt beim Server nach, ob der angemeldete Benutzer ein bestimmtes Recht hat.
 * Erwartet als Antwort `{ darfAendern: boolean }` (z. B. `/api/aufschlaege/berechtigung`).
 *
 * Fragt bei jedem Einhängen neu und merkt sich nichts zwischen Benutzern: Nach einem Benutzerwechsel
 * kann so kein fremdes Recht stehen bleiben. Solange die Antwort fehlt oder die Abfrage scheitert,
 * gilt „nein“: Die Knöpfe bleiben dann einfach weg. Der Server prüft das Recht bei jedem Zugriff ohnehin selbst.
 */
export function useRechtAbfragen(url: string): boolean {
    const [darf, setDarf] = useState(false);
    useEffect(() => {
        const abbruch = new AbortController();
        fetch(url, { signal: abbruch.signal })
            .then(res => (res.ok ? res.json() as Promise<{ darfAendern?: boolean }> : { darfAendern: false }))
            .then(d => setDarf(d?.darfAendern === true))
            .catch(() => { /* abgebrochen oder nicht erreichbar: die Knöpfe bleiben weg */ });
        return () => abbruch.abort();
    }, [url]);
    return darf;
}
