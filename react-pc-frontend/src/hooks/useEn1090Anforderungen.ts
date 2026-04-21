import { useEffect, useState } from 'react';

/**
 * Antwort des Backend-Endpoints `GET /api/en1090/anforderungen/{projektId}`.
 * Zentrale Single Source of Truth: Fällt das Projekt unter den vollen
 * EN-1090-2-Ablauf?
 *
 * Die EXC-Stufe (1 vs. 2) bleibt in der DB als interne Info erhalten, wird
 * hier aber nicht differenziert: beide Stufen lösen denselben Ablauf aus.
 */
export interface En1090Anforderungen {
    en1090Pflichtig: boolean;
}

const KEINE: En1090Anforderungen = { en1090Pflichtig: false };

const cache = new Map<number, { data: En1090Anforderungen; ts: number }>();
const CACHE_TTL_MS = 5 * 60 * 1000;

/**
 * Lädt die EN-1090-Anforderungen für ein Projekt. Cache wird pro Projekt-ID
 * für 5 Minuten gehalten. Solange kein Ergebnis vorliegt oder der Fetch
 * fehlschlägt, liefert der Hook den sicheren Default (kein EN 1090) zurück –
 * Folge-Features blenden also nie versehentlich Pflichten ein, bevor das
 * Backend geantwortet hat.
 *
 * Der Cache dient als Source of Truth: der Render-Wert wird direkt daraus
 * abgeleitet, der Effect triggert nur einen Re-Render, wenn der Fetch neue
 * Daten liefert. Bei Projekt-ID-Wechsel sieht der nächste Render automatisch
 * den Wert der neuen ID (oder den Default).
 */
export function useEn1090Anforderungen(
    projektId: number | null | undefined
): En1090Anforderungen {
    // Versions-Counter – einziges React-State-Element, dient nur dazu, nach
    // einem erfolgreichen Fetch einen Re-Render auszulösen, damit der
    // Cache-Wert unten neu gelesen wird.
    const [, setVersion] = useState(0);

    useEffect(() => {
        if (projektId == null) return;
        const cached = cache.get(projektId);
        if (cached && Date.now() - cached.ts < CACHE_TTL_MS) return;

        let abgebrochen = false;
        fetch(`/api/en1090/anforderungen/${projektId}`)
            .then((r) => {
                if (!r.ok) throw new Error(`HTTP ${r.status}`);
                return r.json() as Promise<En1090Anforderungen>;
            })
            .then((data) => {
                if (abgebrochen) return;
                cache.set(projektId, { data, ts: Date.now() });
                setVersion((v) => v + 1);
            })
            .catch(() => {
                // Default bleibt – keine Fehlermeldung nötig.
            });

        return () => {
            abgebrochen = true;
        };
    }, [projektId]);

    if (projektId == null) return KEINE;
    return cache.get(projektId)?.data ?? KEINE;
}

/**
 * Nur für Tests: Cache leeren, damit Komponenten neu laden.
 */
export function _resetEn1090AnforderungenCache(): void {
    cache.clear();
}
