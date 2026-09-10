/**
 * Gemeinsame Datumsumwandlung fuer das PC-Frontend.
 *
 * Nicht durch `toISOString()` ersetzen: das rechnet nach UTC um. In
 * Deutschland (UTC+1/+2) wird aus dem 1. Januar 00:00 Ortszeit der
 * 31. Dezember des Vorjahres — Stichtage, Filter und Belegdaten
 * griffen dann einen Tag daneben.
 */
export function isoDatum(datum: Date): string {
    const zweistellig = (n: number) => String(n).padStart(2, '0');
    return `${datum.getFullYear()}-${zweistellig(datum.getMonth() + 1)}-${zweistellig(datum.getDate())}`;
}

/**
 * Heutiges Datum als `YYYY-MM-DD` in lokaler Zeit.
 *
 * Bewusst eine Funktion und keine Modulkonstante: die Anwendung bleibt
 * oft ueber Mitternacht hinaus geoeffnet, ein beim Laden eingefrorener
 * Wert waere dann falsch.
 */
export function heuteIso(): string {
    return isoDatum(new Date());
}
