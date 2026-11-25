import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { Lieferant } from '../../types';

export const LIEFERANTEN_SEITE = 12;

export interface LieferantenFilter {
    q: string;
    name: string;
    rolle: string;
    ort: string;
    email: string;
}

const LEERE_FILTER: LieferantenFilter = { q: "", name: "", rolle: "", ort: "", email: "" };

/**
 * Seitenweise Lieferanten-Übersicht mit Filtern (12 je Seite, gefiltert beim Server).
 * Lädt nur, solange die Liste sichtbar ist (`aktiv`).
 */
export function useLieferantenListe(aktiv: boolean) {
    const [lieferanten, setLieferanten] = useState<Lieferant[]>([]);
    const [loading, setLoading] = useState(true);
    const [total, setTotal] = useState(0);
    const [page, setPage] = useState(0);
    const [filters, setFilters] = useState<LieferantenFilter>(LEERE_FILTER);

    // Laufende Ladevorgänge durchnummerieren: Beim schnellen Tippen in den Filterfeldern
    // starten mehrere Requests gleichzeitig. Ohne diesen Zähler kann eine späte Antwort
    // auf eine alte Filter-/Seiten-Kombination die aktuelle Liste überschreiben.
    const ladeVorgangRef = useRef(0);

    // Andere Seite, andere Filter oder zurück in der Liste: Ladeanzeige an, bevor der Effekt neu lädt.
    const [geladeneAbfrage, setGeladeneAbfrage] = useState({ page, filters, aktiv });
    if (geladeneAbfrage.page !== page || geladeneAbfrage.filters !== filters || geladeneAbfrage.aktiv !== aktiv) {
        setGeladeneAbfrage({ page, filters, aktiv });
        if (aktiv) setLoading(true);
    }

    // Promise-Kette: Zustand wird erst gesetzt, wenn die Antwort da ist.
    const loadLieferanten = useCallback(() => {
        const ladeVorgang = ++ladeVorgangRef.current;
        const istAktuell = () => ladeVorgangRef.current === ladeVorgang;
        const params = new URLSearchParams();
        params.set("page", String(page));
        params.set("size", String(LIEFERANTEN_SEITE));
        for (const [schluessel, wert] of Object.entries(filters)) {
            if (wert) params.set(schluessel, wert);
        }

        return fetch(`/api/lieferanten?${params.toString()}`)
            .then(async (res) => {
                if (!res.ok) throw new Error("Fehler beim Laden");
                const data = await res.json();
                if (!istAktuell()) return;
                setLieferanten(Array.isArray(data.lieferanten) ? data.lieferanten : []);
                setTotal(typeof data.gesamt === "number" ? data.gesamt : 0);
            })
            .catch((err) => {
                console.error(err);
                if (!istAktuell()) return;
                setLieferanten([]);
                setTotal(0);
            })
            .finally(() => {
                if (istAktuell()) setLoading(false);
            });
    }, [page, filters]);

    const neuLaden = () => {
        setLoading(true);
        void loadLieferanten();
    };

    useEffect(() => {
        if (aktiv) void loadLieferanten();
    }, [loadLieferanten, aktiv]);

    // Jede Filter-Änderung springt zurück auf Seite 1: Sonst bliebe man z.B. auf
    // Seite 5 stehen, während die gefilterte Liste nur noch zwei Seiten hat.
    const filterAendern = (key: keyof LieferantenFilter, value: string) => {
        setFilters((prev) => ({ ...prev, [key]: value }));
        setPage(0);
    };

    const filterZuruecksetzen = () => {
        setFilters(LEERE_FILTER);
        setPage(0);
    };

    const totalPages = Math.max(1, Math.ceil(total / LIEFERANTEN_SEITE));

    // Zeigt die Seitenzahl hinter das Ergebnis (z.B. nachdem der letzte Eintrag einer
    // Seite gelöscht wurde), springen wir auf die letzte gültige Seite zurück. Erst nach
    // dem Laden, denn währenddessen ist `total` noch der alte Wert.
    if (!loading && page > totalPages - 1) {
        setPage(totalPages - 1);
    }

    const statusText = useMemo(() => {
        if (loading) return 'Lieferanten werden geladen...';
        if (total === 0) return 'Keine Lieferanten gefunden.';
        const start = page * LIEFERANTEN_SEITE + 1;
        const end = Math.min(start + lieferanten.length - 1, total);
        return `Zeige ${start}-${end} von ${total} Lieferanten`;
    }, [loading, total, page, lieferanten.length]);

    return {
        lieferanten, loading, setLoading, page, setPage, totalPages, statusText,
        filters, filterAendern, filterZuruecksetzen, neuLaden,
    };
}
