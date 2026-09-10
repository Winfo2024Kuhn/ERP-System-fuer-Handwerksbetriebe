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
