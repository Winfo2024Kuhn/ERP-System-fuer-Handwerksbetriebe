import { useEffect, useSyncExternalStore } from 'react';
import { ladeBerechtigung } from './api';

/**
 * Darf der angemeldete Benutzer Anrufe und Anrufbeantworter sehen?
 *
 * <p>Viele Stellen fragen das gleichzeitig (Menü, Glocke, Anruf-Fenster,
 * Kunden- und Lieferantenakte). Damit daraus nicht ein Dutzend gleicher
 * Anfragen wird, hält ein kleiner gemeinsamer Speicher das Ergebnis und
 * bündelt laufende Anfragen. {@link setzeTelefonBerechtigungZurueck} leert
 * ihn – das Layout ruft das bei jedem Benutzerwechsel auf.</p>
 *
 * <p>Rückgabe: `null` solange noch geprüft wird, danach `true`/`false`.
 * Wer `null` bekommt, zeigt einfach noch nichts – so blitzt kein Menüpunkt
 * kurz auf, der danach wieder verschwindet.</p>
 */

let stand: boolean | null = null;
let laufend: Promise<void> | null = null;
let generation = 0;
const zuhoerer = new Set<() => void>();

function melde() {
    zuhoerer.forEach((z) => z());
}

function lade() {
    if (stand !== null || laufend) return;
    const meineGeneration = generation;
    laufend = ladeBerechtigung()
        .catch(() => false)
        .then((darf) => {
            // Inzwischen zurückgesetzt (z. B. Abmelden) – Ergebnis verwerfen.
            if (meineGeneration !== generation) return;
            stand = darf;
            laufend = null;
            melde();
        });
}

function abonniere(zuhoererFn: () => void) {
    zuhoerer.add(zuhoererFn);
    return () => {
        zuhoerer.delete(zuhoererFn);
    };
}

function momentaufnahme() {
    return stand;
}

/** Vergisst das Ergebnis; alle eingebundenen Stellen fragen neu. */
export function setzeTelefonBerechtigungZurueck(): void {
    generation += 1;
    stand = null;
    laufend = null;
    melde();
}

export function useTelefonBerechtigung(): boolean | null {
    const aktuell = useSyncExternalStore(abonniere, momentaufnahme, momentaufnahme);
    useEffect(() => {
        if (aktuell === null) lade();
    }, [aktuell]);
    return aktuell;
}
