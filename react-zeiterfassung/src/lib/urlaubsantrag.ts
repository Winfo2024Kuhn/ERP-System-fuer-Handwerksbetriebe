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

/** Was der Server zum Resturlaub aus dem Vorjahr liefert (Saldo und `/api/urlaub/resturlaub`). */
export interface VorjahresRest {
    /** Tage aus dem Vorjahr, die noch offen sind (stecken im Resturlaub). */
    ausVorjahrOffen?: number | null
    /** Tage aus dem Vorjahr, die schon verfallen sind. */
    verfallen?: number | null
    /** Letzter Tag für den Vorjahresrest (ISO); `null` = verfällt nicht. */
    verfallsdatum?: string | null
}

/** „12,5 Tage" bzw. „1 Tag". */
export function tageMitEinheit(tage: number | null | undefined): string {
    const text = formatUrlaubstage(tage)
    return `${text} ${text === '1' ? 'Tag' : 'Tage'}`
}

/**
 * Ein kurzer Satz zum Vorjahresrest – oder `null`, wenn es keinen gibt.
 * `verfallsdatum` ist der letzte Tag, an dem der Rest genommen werden kann:
 * „davon 3 Tage aus dem Vorjahr – bis 31.03.2027 nehmen, danach verfallen sie",
 * „davon 3 Tage aus dem Vorjahr, verfallen nicht" bzw.
 * „2 Tage aus dem Vorjahr sind nach dem 31.03.2027 verfallen".
 * Gleicher Wortlaut wie `vorjahresHinweis` im PC-Frontend (`lib/urlaubstage.ts`).
 */
export function vorjahresHinweis(rest: VorjahresRest | null | undefined): string | null {
    if (!rest) return null
    const offen = Number(rest.ausVorjahrOffen)
    if (Number.isFinite(offen) && offen > 0) {
        const text = tageMitEinheit(offen)
        const teil = `davon ${text} aus dem Vorjahr`
        if (!rest.verfallsdatum) return `${teil}, verfallen nicht`
        const danach = text.endsWith(' Tag') ? 'verfällt er' : 'verfallen sie'
        return `${teil} – bis ${formatIsoDatumDe(rest.verfallsdatum)} nehmen, danach ${danach}`
    }
    const verfallen = Number(rest.verfallen)
    if (Number.isFinite(verfallen) && verfallen > 0 && rest.verfallsdatum) {
        const text = tageMitEinheit(verfallen)
        return `${text} aus dem Vorjahr ${text.endsWith(' Tag') ? 'ist' : 'sind'} nach dem ${formatIsoDatumDe(rest.verfallsdatum)} verfallen`
    }
    return null
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

/** Herkunft des Jahresanspruchs, wie der Server sie liefert (`UrlaubskontoService.Anspruchsart`). */
export type Anspruchsart = 'VOLL' | 'MINDESTURLAUB' | 'ANTEILIG_EINTRITT' | 'MINDESTURLAUB_EINTRITT' | 'VOR_EINTRITT'

/**
 * Kurzer Hinweis, warum der Jahresanspruch vom Vertragsurlaub abweicht – oder `null`:
 * „anteilig wegen Eintritt am 15.03.2027", „gesetzlicher Mindesturlaub (Eintritt am 01.06.2027)",
 * „gesetzlicher Mindesturlaub" bzw. „Eintritt erst am 01.02.2028".
 * Gleicher Wortlaut wie `anspruchHinweis` im PC-Frontend (`lib/urlaubstage.ts`).
 */
export function anspruchHinweis(art: string | null | undefined, eintrittsdatum: string | null | undefined): string | null {
    const eintritt = eintrittsdatum ? formatIsoDatumDe(eintrittsdatum) : null
    switch (art) {
        case 'ANTEILIG_EINTRITT':
            return eintritt ? `anteilig wegen Eintritt am ${eintritt}` : 'anteilig wegen Eintritt im Jahr'
        case 'MINDESTURLAUB_EINTRITT':
            return eintritt ? `gesetzlicher Mindesturlaub (Eintritt am ${eintritt})` : 'gesetzlicher Mindesturlaub'
        case 'MINDESTURLAUB':
            return 'gesetzlicher Mindesturlaub'
        case 'VOR_EINTRITT':
            return eintritt ? `Eintritt erst am ${eintritt}` : 'vor dem Eintritt'
        default:
            return null
    }
}
