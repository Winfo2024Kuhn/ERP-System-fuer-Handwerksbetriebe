import { useEffect, useSyncExternalStore } from 'react';

/**
 * Gemeinsamer Speicher für ein Ja/Nein-Recht des angemeldeten Benutzers.
 *
 * <p>Viele Stellen fragen dasselbe Recht gleichzeitig (Menü, Seitenschutz,
 * Glocke, ...). Damit daraus nicht ein Dutzend gleicher Anfragen wird, hält der
 * Speicher das Ergebnis und bündelt laufende Anfragen. {@code setzeZurueck}
 * leert ihn – das Layout ruft das bei jedem Benutzerwechsel auf.</p>
 *
 * <p>{@code useBerechtigung} liefert `null`, solange noch geprüft wird, danach
 * `true`/`false`. Wer `null` bekommt, zeigt einfach noch nichts – so blitzt
 * kein Menüpunkt kurz auf, der danach wieder verschwindet. Ein Fehler beim
 * Laden zählt als „kein Recht“ – aber nur vorläufig: Die nächste Stelle, die
 * das Recht abfragt, lädt neu (sonst fehlte ein Menü nach einem kurzen
 * Netzaussetzer die ganze Sitzung).</p>
 */
export interface BerechtigungsSpeicher {
    useBerechtigung: () => boolean | null;
    setzeZurueck: () => void;
}

export function erzeugeBerechtigungsSpeicher(lade: () => Promise<boolean>): BerechtigungsSpeicher {
    let stand: boolean | null = null;
    let ausFehler = false;
    let laufend: Promise<void> | null = null;
    let generation = 0;
    const zuhoerer = new Set<() => void>();

    const melde = () => zuhoerer.forEach((z) => z());

    const ladeEinmal = () => {
        if ((stand !== null && !ausFehler) || laufend) return;
        const meineGeneration = generation;
        laufend = lade()
            .then((darf) => ({ darf, fehler: false }))
            .catch(() => ({ darf: false, fehler: true }))
            .then(({ darf, fehler }) => {
                // Inzwischen zurückgesetzt (z. B. Abmelden) – Ergebnis verwerfen.
                if (meineGeneration !== generation) return;
                stand = darf;
                ausFehler = fehler;
                laufend = null;
                if (fehler) wiederholeBeiRueckkehr();
                melde();
            });
    };

    /** Nach einem Ladefehler erneut versuchen, sobald das Fenster wieder aktiv oder online ist. */
    const wiederholeBeiRueckkehr = () => {
        if (typeof window === 'undefined') return;
        const erneut = () => {
            window.removeEventListener('focus', erneut);
            window.removeEventListener('online', erneut);
            if (ausFehler) ladeEinmal();
        };
        window.addEventListener('focus', erneut);
        window.addEventListener('online', erneut);
    };

    const abonniere = (zuhoererFn: () => void) => {
        zuhoerer.add(zuhoererFn);
        return () => {
            zuhoerer.delete(zuhoererFn);
        };
    };

    const momentaufnahme = () => stand;

    return {
        setzeZurueck: () => {
            generation += 1;
            stand = null;
            ausFehler = false;
            laufend = null;
            melde();
        },
        useBerechtigung: () => {
            const aktuell = useSyncExternalStore(abonniere, momentaufnahme, momentaufnahme);
            // Beim Einbinden: nach einem früheren Ladefehler neu versuchen (nur dann,
            // nicht sofort im selben Moment, in dem der Fehler eintrifft).
            useEffect(() => {
                if (ausFehler) ladeEinmal();
            }, []);
            useEffect(() => {
                if (aktuell === null) ladeEinmal();
            }, [aktuell]);
            return aktuell;
        },
    };
}
