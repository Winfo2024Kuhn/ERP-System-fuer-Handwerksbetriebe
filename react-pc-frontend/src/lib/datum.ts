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

/** `2026-03-10` → Date, oder null wenn das Datum nicht real existiert (31.02.). */
export function parseIsoDatum(value: string): Date | null {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null;
    const [jahr, monat, tag] = value.split('-').map(Number);
    const datum = new Date(0); datum.setFullYear(jahr, monat - 1, tag); datum.setHours(0, 0, 0, 0);
    return isoDatum(datum) === value ? datum : null;
}

/**
 * Wandelt eine deutsche Eingabe in ein ISO-Datum. Akzeptiert `9.9.1967`,
 * `09.09.1967` und `09091967`; zweistellige Jahre werden bewusst NICHT
 * geraten, weil bei Geburtsdaten 67 sowohl 1967 als auch 2067 sein kann.
 */
export function parseDeutschesDatum(entwurf: string): string | null {
    const treffer = /^(\d{1,2})[.\-/ ]?(\d{1,2})[.\-/ ]?(\d{4})$/.exec(entwurf.trim());
    if (!treffer) return null;
    const [, tag, monat, jahr] = treffer;
    const iso = `${jahr}-${monat.padStart(2, '0')}-${tag.padStart(2, '0')}`;
    return parseIsoDatum(iso) ? iso : null;
}
