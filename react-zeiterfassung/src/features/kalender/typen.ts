/** Datenmodell der mobilen Kalenderseite: Rohdaten der API und das vereinheitlichte Anzeigemodell. */

export interface Teilnehmer {
    id: number
    name: string
}

/** Kalendereintrag, wie ihn /api/kalender/mobile liefert. */
export interface KalenderEintrag {
    id: number
    titel: string
    beschreibung: string | null
    datum: string
    startZeit: string | null
    endeZeit: string | null
    ganztaegig: boolean
    farbe: string | null
    projektId: number | null
    projektName: string | null
    kundeId: number | null
    kundeName: string | null
    lieferantId: number | null
    lieferantName: string | null
    anfrageId: number | null
    anfrageBetreff: string | null
    erstellerId: number | null
    erstellerName: string | null
    teilnehmer: Teilnehmer[]
}

export type AbwesenheitTyp = 'URLAUB' | 'KRANKHEIT' | 'FORTBILDUNG' | 'ZEITAUSGLEICH'

/** Team-Abwesenheit, wie sie /api/abwesenheit/team liefert. */
export interface TeamAbwesenheit {
    id: number
    datum: string
    typ: AbwesenheitTyp
    stunden: number
    mitarbeiterId: number
    mitarbeiterName: string
}

/** Gesetzlicher Feiertag, wie ihn /api/zeiterfassung/feiertage liefert. */
export interface Feiertag {
    datum: string
    bezeichnung: string
}

/** Art eines Eintrags – bestimmt Farbe, Beschriftung und Icon. */
export type EintragArt = AbwesenheitTyp | 'FEIERTAG' | 'TERMIN'

export const EINTRAG_ARTEN: Record<EintragArt, { farbe: string; label: string }> = {
    URLAUB: { farbe: '#0ea5e9', label: 'Urlaub' },
    KRANKHEIT: { farbe: '#64748b', label: 'Krankheit' },
    FORTBILDUNG: { farbe: '#10b981', label: 'Fortbildung' },
    ZEITAUSGLEICH: { farbe: '#f59e0b', label: 'Zeitausgleich' },
    FEIERTAG: { farbe: '#f43f5e', label: 'Feiertag' },
    TERMIN: { farbe: '#7c3aed', label: 'Termin' },
}

/** Reihenfolge der Legende und der Sortierung innerhalb eines Tages. */
export const EINTRAG_ART_REIHENFOLGE: EintragArt[] = ['FEIERTAG', 'URLAUB', 'KRANKHEIT', 'FORTBILDUNG', 'ZEITAUSGLEICH', 'TERMIN']

export type VerknuepfungArt = 'projekt' | 'kunde' | 'lieferant' | 'anfrage'

export interface Verknuepfung {
    art: VerknuepfungArt
    id: number
    name: string
}

/**
 * Vereinheitlichtes Anzeigemodell: Termine, Team-Abwesenheiten und Feiertage
 * sehen für Raster, Listen und Sheet gleich aus.
 */
export interface KalenderTermin {
    /** Eindeutig über alle Quellen hinweg, z. B. `t-12`, `a-5`, `f-2026-10-03`. */
    key: string
    /** API-ID bei echten Terminen (für Deep-Links), sonst null. */
    terminId: number | null
    art: EintragArt
    titel: string
    /** Kurze Einordnung unter dem Titel, z. B. „Urlaub“ oder der Projektname. */
    untertitel: string
    datum: string
    startZeit: string | null
    endeZeit: string | null
    ganztaegig: boolean
    farbe: string
    beschreibung: string | null
    erstellerName: string | null
    teilnehmer: Teilnehmer[]
    verknuepfungen: Verknuepfung[]
}

/** Sichtbarer Datumsbereich (beide Grenzen inklusive, ISO-Datum). */
export interface Bereich {
    von: string
    bis: string
}
