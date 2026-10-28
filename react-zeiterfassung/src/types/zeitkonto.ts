export interface ZeitkontoStatus {
    fuehrtZeitkonto: boolean
    eingerichtet: boolean
    istGeschaeftsfuehrer?: boolean
    kontenGefuehrt?: boolean
    hinweis: string | null
}

export interface ZeitkontoSaldoStatus extends ZeitkontoStatus {
    monat: {
        festgeschrieben?: boolean
    }
    gesamt: {
        geprueftBis?: string | null
        vorlaeufig?: boolean
    }
}

export const hatEingerichtetesZeitkonto = (status: ZeitkontoStatus | null) =>
    status?.fuehrtZeitkonto === true && (status.eingerichtet === true || status.istGeschaeftsfuehrer === true)

export const zeitkontoHinweis = (status: ZeitkontoStatus | null) =>
    status?.hinweis || 'Für Sie ist noch keine Arbeitszeit eingerichtet.'

/** Nur eine vollständige Antwort (beide Schalter als echte Wahrheitswerte) taugt als Entscheidung. */
export const istVollstaendigerZeitkontoStatus = (wert: unknown): wert is ZeitkontoStatus =>
    !!wert && typeof wert === 'object'
    && typeof (wert as ZeitkontoStatus).fuehrtZeitkonto === 'boolean'
    && typeof (wert as ZeitkontoStatus).eingerichtet === 'boolean'

/**
 * Sperrt neue Zeitbuchungen nur, wenn der Server (oder sein auf dem Handy gespeicherter
 * Stand) ausdrücklich sagt: keine Arbeitszeit eingerichtet. Ein unbekannter Stand (null,
 * z. B. ohne Netz und noch nie abgeglichen) sperrt nicht – das Handy bucht, der Server
 * entscheidet beim Übertragen.
 */
export const zeitbuchungGesperrt = (status: ZeitkontoStatus | null) =>
    status !== null && !hatEingerichtetesZeitkonto(status)
