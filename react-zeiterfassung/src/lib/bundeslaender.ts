/**
 * Klartext der 16 Bundesländer zum Kürzel, das der Server liefert (Feld
 * `bundesland` der Feiertage, Issue #222). Bewusst eigene kleine Liste: Die
 * Handy-App hängt nicht vom PC-Projekt ab.
 */
const BUNDESLAENDER: Record<string, string> = {
    BW: 'Baden-Württemberg', BY: 'Bayern', BE: 'Berlin', BB: 'Brandenburg',
    HB: 'Bremen', HH: 'Hamburg', HE: 'Hessen', MV: 'Mecklenburg-Vorpommern',
    NI: 'Niedersachsen', NW: 'Nordrhein-Westfalen', RP: 'Rheinland-Pfalz', SL: 'Saarland',
    SN: 'Sachsen', ST: 'Sachsen-Anhalt', SH: 'Schleswig-Holstein', TH: 'Thüringen',
}

/** Klartext zum Kürzel; leer oder unbekannt → null. */
export function bundeslandName(kuerzel: string | null | undefined): string | null {
    if (!kuerzel) return null
    return BUNDESLAENDER[kuerzel.trim().toUpperCase()] ?? null
}

/** „12 Feiertage in Bayern" bzw. ohne bekanntes Land „12 Feiertage". */
export function feiertageAnzahlText(anzahl: number, kuerzel: string | null | undefined): string {
    const land = bundeslandName(kuerzel)
    const text = `${anzahl} ${anzahl === 1 ? 'Feiertag' : 'Feiertage'}`
    return land ? `${text} in ${land}` : text
}
