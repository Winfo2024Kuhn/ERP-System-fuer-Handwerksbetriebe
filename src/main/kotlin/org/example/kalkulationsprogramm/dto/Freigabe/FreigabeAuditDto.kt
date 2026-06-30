package org.example.kalkulationsprogramm.dto.Freigabe

import java.time.LocalDateTime

class FreigabeAuditDto(
    val status: String?,
    val dokumentArt: String?,
    val dokumentNummer: String?,
    val erstelltAm: LocalDateTime?,
    val ablaufDatum: LocalDateTime?,
    val akzeptiertAm: LocalDateTime?,
    val akzeptiertEmail: String?,
    val akzeptiertIp: String?,
    val akzeptiertUserAgent: String?,
    val unterzeichnerVorname: String?,
    val unterzeichnerNachname: String?,
    val unterzeichnerName: String?,
    val hashOriginal: String?,
    val hashAcceptance: String?,
) {
    class FreigabeAuditDtoBuilder {
        private var status: String? = null
        private var dokumentArt: String? = null
        private var dokumentNummer: String? = null
        private var erstelltAm: LocalDateTime? = null
        private var ablaufDatum: LocalDateTime? = null
        private var akzeptiertAm: LocalDateTime? = null
        private var akzeptiertEmail: String? = null
        private var akzeptiertIp: String? = null
        private var akzeptiertUserAgent: String? = null
        private var unterzeichnerVorname: String? = null
        private var unterzeichnerNachname: String? = null
        private var unterzeichnerName: String? = null
        private var hashOriginal: String? = null
        private var hashAcceptance: String? = null

        fun status(status: String?) = apply { this.status = status }
        fun dokumentArt(dokumentArt: String?) = apply { this.dokumentArt = dokumentArt }
        fun dokumentNummer(dokumentNummer: String?) = apply { this.dokumentNummer = dokumentNummer }
        fun erstelltAm(erstelltAm: LocalDateTime?) = apply { this.erstelltAm = erstelltAm }
        fun ablaufDatum(ablaufDatum: LocalDateTime?) = apply { this.ablaufDatum = ablaufDatum }
        fun akzeptiertAm(akzeptiertAm: LocalDateTime?) = apply { this.akzeptiertAm = akzeptiertAm }
        fun akzeptiertEmail(akzeptiertEmail: String?) = apply { this.akzeptiertEmail = akzeptiertEmail }
        fun akzeptiertIp(akzeptiertIp: String?) = apply { this.akzeptiertIp = akzeptiertIp }
        fun akzeptiertUserAgent(akzeptiertUserAgent: String?) = apply { this.akzeptiertUserAgent = akzeptiertUserAgent }
        fun unterzeichnerVorname(unterzeichnerVorname: String?) =
            apply { this.unterzeichnerVorname = unterzeichnerVorname }

        fun unterzeichnerNachname(unterzeichnerNachname: String?) =
            apply { this.unterzeichnerNachname = unterzeichnerNachname }

        fun unterzeichnerName(unterzeichnerName: String?) = apply { this.unterzeichnerName = unterzeichnerName }
        fun hashOriginal(hashOriginal: String?) = apply { this.hashOriginal = hashOriginal }
        fun hashAcceptance(hashAcceptance: String?) = apply { this.hashAcceptance = hashAcceptance }

        fun build(): FreigabeAuditDto =
            FreigabeAuditDto(
                status,
                dokumentArt,
                dokumentNummer,
                erstelltAm,
                ablaufDatum,
                akzeptiertAm,
                akzeptiertEmail,
                akzeptiertIp,
                akzeptiertUserAgent,
                unterzeichnerVorname,
                unterzeichnerNachname,
                unterzeichnerName,
                hashOriginal,
                hashAcceptance,
            )
    }

    companion object {
        @JvmStatic
        fun builder(): FreigabeAuditDtoBuilder = FreigabeAuditDtoBuilder()
    }
}
