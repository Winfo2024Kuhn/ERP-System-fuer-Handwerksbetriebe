/**
 * Wording und Zahlenformat rund um Abwesenheitsanträge – gemeinsam für die
 * Antragsseite und die Entscheidungs-Karten auf dem Dashboard.
 */

export type AntragStatus = 'OFFEN' | 'GENEHMIGT' | 'ABGELEHNT' | 'STORNIERT'
export type AntragTyp = 'URLAUB' | 'KRANKHEIT' | 'FORTBILDUNG' | 'ZEITAUSGLEICH'

/** Klartext je Stand – der Rohwert („STORNIERT") erscheint nie auf dem Bildschirm. */
export const STATUS_LABELS: Record<AntragStatus, string> = {
    OFFEN: 'Offen',
    GENEHMIGT: 'Genehmigt',
    ABGELEHNT: 'Abgelehnt',
    STORNIERT: 'Storniert',
}

export const TYP_LABELS: Record<AntragTyp, string> = {
    URLAUB: 'Urlaub',
    KRANKHEIT: 'Krankheit',
    FORTBILDUNG: 'Fortbildung',
    ZEITAUSGLEICH: 'Zeitausgleich',
}

export function statusLabel(status: string): string {
    return (STATUS_LABELS as Record<string, string>)[status] ?? 'Unbekannt'
}

export function typLabel(typ: string): string {
    return (TYP_LABELS as Record<string, string>)[typ] ?? 'Abwesenheit'
}

/**
 * Urlaubstage im deutschen Zahlenformat: 12 → „12", 12.5 → „12,5".
 * Halbe Urlaubstage zählen 0,5; ungültige Werte werden als „0" gezeigt.
 */
export function formatUrlaubstage(tage: number | null | undefined): string {
    const wert = Number(tage)
    if (!Number.isFinite(wert)) return '0'
    return wert.toLocaleString('de-DE', { maximumFractionDigits: 2 })
}

/** „2026-08-03" → „03.08.2026", ohne Zeitzonen-Umweg über Date. */
export function formatIsoDatumDe(iso: string): string {
    const [jahr, monat, tag] = iso.slice(0, 10).split('-')
    return `${tag}.${monat}.${jahr}`
}

export interface EntscheidungAntrag {
    vonDatum: string
    bisDatum: string
    status: AntragStatus | string
    typ: AntragTyp | string
}

/** Wie der Antrag im Satz heißt – mit passendem Artikel je Art. */
const ANTRAG_IM_SATZ: Record<AntragTyp, { subjekt: string; objekt: string }> = {
    URLAUB: { subjekt: 'Dein Urlaub', objekt: 'Deinen Urlaubsantrag' },
    KRANKHEIT: { subjekt: 'Deine Krankmeldung', objekt: 'Deine Krankmeldung' },
    FORTBILDUNG: { subjekt: 'Deine Fortbildung', objekt: 'Deinen Fortbildungsantrag' },
    ZEITAUSGLEICH: { subjekt: 'Dein Zeitausgleich', objekt: 'Deinen Zeitausgleichsantrag' },
}

function antragImSatz(typ: string) {
    return (ANTRAG_IM_SATZ as Record<string, { subjekt: string; objekt: string }>)[typ] ?? ANTRAG_IM_SATZ.URLAUB
}

function zeitraumText(vonDatum: string, bisDatum: string): string {
    return vonDatum === bisDatum
        ? `am ${formatIsoDatumDe(vonDatum)}`
        : `vom ${formatIsoDatumDe(vonDatum)} bis ${formatIsoDatumDe(bisDatum)}`
}

/**
 * Satz für eine Entscheidungs-Nachricht, z. B.
 * „Dein Urlaub vom 03.08.2026 bis 14.08.2026 wurde genehmigt." bzw.
 * „… wurde vom Büro storniert."
 */
export function entscheidungsText(antrag: EntscheidungAntrag): string {
    const ergebnis = antrag.status === 'ABGELEHNT' ? 'abgelehnt'
        : antrag.status === 'STORNIERT' ? 'vom Büro storniert'
        : 'genehmigt'
    return `${antragImSatz(antrag.typ).subjekt} ${zeitraumText(antrag.vonDatum, antrag.bisDatum)} wurde ${ergebnis}.`
}

/** Rückfrage vor dem Zurückziehen, z. B. „Deinen Urlaubsantrag vom … bis … zurückziehen?" */
export function zurueckziehenFrage(antrag: { typ: string; vonDatum: string; bisDatum: string }): string {
    return `${antragImSatz(antrag.typ).objekt} ${zeitraumText(antrag.vonDatum, antrag.bisDatum)} zurückziehen?`
}
