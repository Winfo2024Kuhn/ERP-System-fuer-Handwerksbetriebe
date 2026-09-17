import {
    EINTRAG_ARTEN,
    EINTRAG_ART_REIHENFOLGE,
    type Bereich,
    type Feiertag,
    type KalenderEintrag,
    type KalenderTermin,
    type TeamAbwesenheit,
    type Verknuepfung,
} from './typen'

export const WOCHENTAGE_KURZ = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']
export const MONATE = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember']

// ==================== Datum-Helfer (lokale Zeit, kein UTC-Versatz) ====================

/** `Date` → `YYYY-MM-DD` in lokaler Zeit. */
export function formatLocalDate(date: Date): string {
    const jahr = date.getFullYear()
    const monat = String(date.getMonth() + 1).padStart(2, '0')
    const tag = String(date.getDate()).padStart(2, '0')
    return `${jahr}-${monat}-${tag}`
}

/** `YYYY-MM-DD` → `Date` um Mitternacht lokaler Zeit (kein `new Date(iso)`, das wäre UTC). */
export function parseLocalDate(iso: string): Date {
    const [jahr, monat, tag] = iso.split('-').map(Number)
    return new Date(jahr, monat - 1, tag)
}

export function heuteIso(): string {
    return formatLocalDate(new Date())
}

/** Prüft ein Datum aus der URL: Form `YYYY-MM-DD` und ein echter Kalendertag. */
export function istGueltigesIsoDatum(wert: string | null | undefined): wert is string {
    if (!wert || !/^\d{4}-\d{2}-\d{2}$/.test(wert)) return false
    const date = parseLocalDate(wert)
    return !Number.isNaN(date.getTime()) && formatLocalDate(date) === wert
}

export function addiereTage(iso: string, tage: number): string {
    const date = parseLocalDate(iso)
    date.setDate(date.getDate() + tage)
    return formatLocalDate(date)
}

export function addiereMonate(iso: string, monate: number): string {
    const date = parseLocalDate(iso)
    const tag = date.getDate()
    date.setDate(1)
    date.setMonth(date.getMonth() + monate)
    const letzterTag = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
    date.setDate(Math.min(tag, letzterTag))
    return formatLocalDate(date)
}

/** Montag der Woche, in der `iso` liegt. */
export function wochenStart(iso: string): string {
    const date = parseLocalDate(iso)
    const wochentag = date.getDay() === 0 ? 7 : date.getDay()
    date.setDate(date.getDate() - (wochentag - 1))
    return formatLocalDate(date)
}

export function istWochenende(iso: string): boolean {
    const tag = parseLocalDate(iso).getDay()
    return tag === 0 || tag === 6
}

export interface RasterTag {
    datum: string
    tag: number
    imMonat: boolean
    wochenende: boolean
}

/**
 * Alle Zellen des Monatsrasters: vom Montag vor dem 1. bis zum Sonntag nach dem
 * Monatsletzten, damit jede Zeile vollständig ist. Tage aus Nachbarmonaten
 * sind mit `imMonat: false` markiert.
 */
export function monatsRaster(jahr: number, monat0: number): RasterTag[] {
    const erster = formatLocalDate(new Date(jahr, monat0, 1))
    const letzter = formatLocalDate(new Date(jahr, monat0 + 1, 0))
    const start = wochenStart(erster)
    const ende = addiereTage(wochenStart(letzter), 6)
    const tage: RasterTag[] = []
    for (let datum = start; datum <= ende; datum = addiereTage(datum, 1)) {
        const date = parseLocalDate(datum)
        tage.push({ datum, tag: date.getDate(), imMonat: date.getMonth() === monat0, wochenende: istWochenende(datum) })
    }
    return tage
}

/** Der Datumsbereich, den das Monatsraster rund um das gewählte Datum zeigt (inklusive Randtage). */
export function sichtbarerBereich(datum: string): Bereich {
    const date = parseLocalDate(datum)
    const raster = monatsRaster(date.getFullYear(), date.getMonth())
    return { von: raster[0].datum, bis: raster[raster.length - 1].datum }
}

export interface MonatsSchluessel {
    jahr: number
    /** 1-basiert, wie die API es erwartet. */
    monat: number
}

export function monatsKey(m: MonatsSchluessel): string {
    return `${m.jahr}-${String(m.monat).padStart(2, '0')}`
}

/** Alle Monate, die ein Bereich berührt (chronologisch). */
export function monateImBereich(bereich: Bereich): MonatsSchluessel[] {
    const von = parseLocalDate(bereich.von)
    const bis = parseLocalDate(bereich.bis)
    const monate: MonatsSchluessel[] = []
    const cursor = new Date(von.getFullYear(), von.getMonth(), 1)
    while (cursor <= bis) {
        monate.push({ jahr: cursor.getFullYear(), monat: cursor.getMonth() + 1 })
        cursor.setMonth(cursor.getMonth() + 1)
    }
    return monate
}

export function jahreImBereich(bereich: Bereich): number[] {
    const von = parseLocalDate(bereich.von).getFullYear()
    const bis = parseLocalDate(bereich.bis).getFullYear()
    return Array.from({ length: bis - von + 1 }, (_, i) => von + i)
}

/** Erster und letzter Tag eines Monats als ISO-Datum. */
export function monatsGrenzen(m: MonatsSchluessel): Bereich {
    return {
        von: formatLocalDate(new Date(m.jahr, m.monat - 1, 1)),
        bis: formatLocalDate(new Date(m.jahr, m.monat, 0)),
    }
}

// ==================== Anzeigemodell ====================

/**
 * Rot (#dc2626) ist für Feiertage reserviert. Alte Termine tragen es noch als
 * früheren Standardwert – die werden auf Violett gezogen, damit die Legende stimmt.
 */
export function terminFarbe(farbe: string | null): string {
    if (!farbe || farbe === '#dc2626') return EINTRAG_ARTEN.TERMIN.farbe
    return farbe
}

function kuerzeZeit(zeit: string | null): string | null {
    return zeit ? zeit.substring(0, 5) : null
}

export function terminAusEintrag(e: KalenderEintrag): KalenderTermin {
    const verknuepfungen: Verknuepfung[] = []
    if (e.projektId && e.projektName) verknuepfungen.push({ art: 'projekt', id: e.projektId, name: e.projektName })
    if (e.kundeId && e.kundeName) verknuepfungen.push({ art: 'kunde', id: e.kundeId, name: e.kundeName })
    if (e.lieferantId && e.lieferantName) verknuepfungen.push({ art: 'lieferant', id: e.lieferantId, name: e.lieferantName })
    if (e.anfrageId && e.anfrageBetreff) verknuepfungen.push({ art: 'anfrage', id: e.anfrageId, name: e.anfrageBetreff })
    return {
        key: `t-${e.id}`,
        terminId: e.id,
        art: 'TERMIN',
        titel: e.titel,
        untertitel: verknuepfungen[0]?.name ?? EINTRAG_ARTEN.TERMIN.label,
        datum: e.datum,
        startZeit: e.ganztaegig ? null : kuerzeZeit(e.startZeit),
        endeZeit: e.ganztaegig ? null : kuerzeZeit(e.endeZeit),
        ganztaegig: e.ganztaegig || !e.startZeit,
        farbe: terminFarbe(e.farbe),
        beschreibung: e.beschreibung,
        erstellerName: e.erstellerName,
        teilnehmer: e.teilnehmer ?? [],
        verknuepfungen,
    }
}

export function terminAusAbwesenheit(a: TeamAbwesenheit): KalenderTermin {
    const art = EINTRAG_ARTEN[a.typ] ? a.typ : 'URLAUB'
    return {
        key: `a-${a.id}`,
        terminId: null,
        art,
        titel: a.mitarbeiterName,
        untertitel: EINTRAG_ARTEN[art].label,
        datum: a.datum,
        startZeit: null,
        endeZeit: null,
        ganztaegig: true,
        farbe: EINTRAG_ARTEN[art].farbe,
        beschreibung: null,
        erstellerName: null,
        teilnehmer: [],
        verknuepfungen: [],
    }
}

export function terminAusFeiertag(f: Feiertag): KalenderTermin {
    return {
        key: `f-${f.datum}-${f.bezeichnung}`,
        terminId: null,
        art: 'FEIERTAG',
        titel: f.bezeichnung,
        untertitel: 'Gesetzlicher Feiertag',
        datum: f.datum,
        startZeit: null,
        endeZeit: null,
        ganztaegig: true,
        farbe: EINTRAG_ARTEN.FEIERTAG.farbe,
        beschreibung: null,
        erstellerName: null,
        teilnehmer: [],
        verknuepfungen: [],
    }
}

/**
 * Reihenfolge innerhalb eines Tages: Feiertag, dann Abwesenheiten nach Art und
 * Name, dann ganztägige Termine, dann Termine nach Uhrzeit.
 */
export function sortiereTermine(termine: KalenderTermin[]): KalenderTermin[] {
    return [...termine].sort((a, b) => {
        const artA = EINTRAG_ART_REIHENFOLGE.indexOf(a.art)
        const artB = EINTRAG_ART_REIHENFOLGE.indexOf(b.art)
        if (artA !== artB) return artA - artB
        if (a.ganztaegig !== b.ganztaegig) return a.ganztaegig ? -1 : 1
        if (a.startZeit && b.startZeit && a.startZeit !== b.startZeit) return a.startZeit.localeCompare(b.startZeit)
        return a.titel.localeCompare(b.titel, 'de')
    })
}

/** Gruppiert nach Datum; jede Tagesliste ist bereits sortiert. */
export function gruppiereProTag(termine: KalenderTermin[]): Map<string, KalenderTermin[]> {
    const gruppen = new Map<string, KalenderTermin[]>()
    for (const termin of termine) {
        const liste = gruppen.get(termin.datum)
        if (liste) liste.push(termin)
        else gruppen.set(termin.datum, [termin])
    }
    for (const [datum, liste] of gruppen) gruppen.set(datum, sortiereTermine(liste))
    return gruppen
}

/** Bis zu `max` verschiedene Farben eines Tages – für die Punkte im Raster. */
export function punktFarben(termine: KalenderTermin[], max = 3): string[] {
    const farben: string[] = []
    for (const termin of termine) {
        if (!farben.includes(termin.farbe)) farben.push(termin.farbe)
        if (farben.length >= max) break
    }
    return farben
}

// ==================== Beschriftungen ====================

/** „Donnerstag, 17. September 2026“ */
export function formatDatumLang(iso: string): string {
    return parseLocalDate(iso).toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
}

/** „Donnerstag, 17. September“ */
export function formatDatumOhneJahr(iso: string): string {
    return parseLocalDate(iso).toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'long' })
}

/** „Heute“, „Morgen“, „Gestern“ oder null. */
export function relativerTag(iso: string, heute: string): string | null {
    if (iso === heute) return 'Heute'
    if (iso === addiereTage(heute, 1)) return 'Morgen'
    if (iso === addiereTage(heute, -1)) return 'Gestern'
    return null
}

/** „09:00 – 10:30 Uhr“, „09:00 Uhr“ oder „Ganztägig“. */
export function formatZeitraum(termin: KalenderTermin): string {
    if (termin.ganztaegig || !termin.startZeit) return 'Ganztägig'
    return termin.endeZeit ? `${termin.startZeit} – ${termin.endeZeit} Uhr` : `${termin.startZeit} Uhr`
}

/** „September 2026“ */
export function formatMonatsTitel(iso: string): string {
    const date = parseLocalDate(iso)
    return `${MONATE[date.getMonth()]} ${date.getFullYear()}`
}

export function anzahlText(anzahl: number, einzahl: string, mehrzahl: string): string {
    return `${anzahl} ${anzahl === 1 ? einzahl : mehrzahl}`
}
