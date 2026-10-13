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
 * Laden zählt als „kein Recht“.</p>
 */
export interface BerechtigungsSpeicher {
    useBerechtigung: () => boolean | null;
    setzeZurueck: () => void;
}

export function erzeugeBerechtigungsSpeicher(lade: () => Promise<boolean>): BerechtigungsSpeicher {
    let stand: boolean | null = null;
    let laufend: Promise<void> | null = null;
    let generation = 0;
    const zuhoerer = new Set<() => void>();

    const melde = () => zuhoerer.forEach((z) => z());

    const ladeEinmal = () => {
        if (stand !== null || laufend) return;
        const meineGeneration = generation;
        laufend = lade()
            .catch(() => false)
            .then((darf) => {
                // Inzwischen zurückgesetzt (z. B. Abmelden) – Ergebnis verwerfen.
                if (meineGeneration !== generation) return;
                stand = darf;
                laufend = null;
                melde();
            });
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
            laufend = null;
            melde();
        },
        useBerechtigung: () => {
            const aktuell = useSyncExternalStore(abonniere, momentaufnahme, momentaufnahme);
            useEffect(() => {
                if (aktuell === null) ladeEinmal();
            }, [aktuell]);
            return aktuell;
        },
    };
}
