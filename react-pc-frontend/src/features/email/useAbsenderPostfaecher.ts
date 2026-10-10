import { useEffect, useState } from 'react';
import { parseAbsenderPostfaecher, type AbsenderPostfach } from './postfach';

export interface AbsenderPostfaecherZustand {
    postfaecher: AbsenderPostfach[];
    laedt: boolean;
    fehler: boolean;
}

/**
 * Lädt die Postfächer, aus denen der angemeldete Benutzer schreiben darf
 * (`GET /api/emails/absender-postfaecher`, eigenes zuerst). Fehler meldet der
 * Aufrufer selbst – das E-Mail-Center mit Toast, das Schreiben-Fenster mit
 * einem Hinweis direkt am Feld.
 */
export function useAbsenderPostfaecher(): AbsenderPostfaecherZustand {
    const [zustand, setZustand] = useState<AbsenderPostfaecherZustand>({ postfaecher: [], laedt: true, fehler: false });

    useEffect(() => {
        let aktiv = true;
        fetch('/api/emails/absender-postfaecher')
            .then(async res => {
                if (!res.ok) throw new Error(`HTTP ${res.status}`);
                const daten = await res.json();
                if (aktiv) setZustand({ postfaecher: parseAbsenderPostfaecher(daten), laedt: false, fehler: false });
            })
            .catch(() => {
                if (aktiv) setZustand({ postfaecher: [], laedt: false, fehler: true });
            });
        return () => { aktiv = false; };
    }, []);

    return zustand;
}
