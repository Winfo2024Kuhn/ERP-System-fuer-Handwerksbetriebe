/**
 * Die 16 Bundesländer mit dem Kürzel, das das Backend speichert (Firmendaten
 * `bundesland`). Die Feiertage richten sich nach diesem Land (Issue #222).
 */
export const BUNDESLAENDER = [
    { kuerzel: 'BW', name: 'Baden-Württemberg' },
    { kuerzel: 'BY', name: 'Bayern' },
    { kuerzel: 'BE', name: 'Berlin' },
    { kuerzel: 'BB', name: 'Brandenburg' },
    { kuerzel: 'HB', name: 'Bremen' },
    { kuerzel: 'HH', name: 'Hamburg' },
    { kuerzel: 'HE', name: 'Hessen' },
    { kuerzel: 'MV', name: 'Mecklenburg-Vorpommern' },
    { kuerzel: 'NI', name: 'Niedersachsen' },
    { kuerzel: 'NW', name: 'Nordrhein-Westfalen' },
    { kuerzel: 'RP', name: 'Rheinland-Pfalz' },
    { kuerzel: 'SL', name: 'Saarland' },
    { kuerzel: 'SN', name: 'Sachsen' },
    { kuerzel: 'ST', name: 'Sachsen-Anhalt' },
    { kuerzel: 'SH', name: 'Schleswig-Holstein' },
    { kuerzel: 'TH', name: 'Thüringen' },
] as const;

export type BundeslandKuerzel = typeof BUNDESLAENDER[number]['kuerzel'];

/** Vorgabe des Backends, solange nichts eingestellt ist. */
export const STANDARD_BUNDESLAND: BundeslandKuerzel = 'BY';

/** Klartext zum Kürzel (Groß-/Kleinschreibung egal); unbekannt oder leer → null. */
export function bundeslandName(kuerzel: string | null | undefined): string | null {
    if (!kuerzel) return null;
    const gesucht = kuerzel.trim().toUpperCase();
    return BUNDESLAENDER.find(land => land.kuerzel === gesucht)?.name ?? null;
}

/** Auswahl-Optionen für `<Select>`: gespeichert wird das Kürzel, angezeigt der Name. */
export const BUNDESLAND_OPTIONEN = BUNDESLAENDER.map(land => ({ value: land.kuerzel, label: land.name }));
