package org.example.kalkulationsprogramm.dto.Freigabe

import java.math.BigDecimal
import java.time.LocalDateTime

class FreigabeAnsichtDto(
    var uuid: String?,
    var status: String?,
    var dokumentNummer: String?,
    var dokumentArt: String?,
    var dokumentBetrag: BigDecimal?,
    var bauvorhaben: String?,
    var kundeName: String?,
    var kundeEmail: String?,
    var erstelltAm: LocalDateTime?,
    var ablaufDatum: LocalDateTime?,
    var akzeptiertAm: LocalDateTime?,
    var abgelaufen: Boolean,
    var pdfPfad: String?,
    var positionen: List<FreigabePositionDto>?,
    var basisNetto: BigDecimal?,
    var basisBrutto: BigDecimal?,
    var mwstProzent: BigDecimal?,
    var hatAlternativen: Boolean,
) {
    fun isAbgelaufen(): Boolean = abgelaufen
    fun isHatAlternativen(): Boolean = hatAlternativen

    class FreigabeAnsichtDtoBuilder {
        private var uuid: String? = null
        private var status: String? = null
        private var dokumentNummer: String? = null
        private var dokumentArt: String? = null
        private var dokumentBetrag: BigDecimal? = null
        private var bauvorhaben: String? = null
        private var kundeName: String? = null
        private var kundeEmail: String? = null
        private var erstelltAm: LocalDateTime? = null
        private var ablaufDatum: LocalDateTime? = null
        private var akzeptiertAm: LocalDateTime? = null
        private var abgelaufen: Boolean = false
        private var pdfPfad: String? = null
        private var positionen: List<FreigabePositionDto>? = null
        private var basisNetto: BigDecimal? = null
        private var basisBrutto: BigDecimal? = null
        private var mwstProzent: BigDecimal? = null
        private var hatAlternativen: Boolean = false

        fun uuid(uuid: String?) = apply { this.uuid = uuid }
        fun status(status: String?) = apply { this.status = status }
        fun dokumentNummer(dokumentNummer: String?) = apply { this.dokumentNummer = dokumentNummer }
        fun dokumentArt(dokumentArt: String?) = apply { this.dokumentArt = dokumentArt }
        fun dokumentBetrag(dokumentBetrag: BigDecimal?) = apply { this.dokumentBetrag = dokumentBetrag }
        fun bauvorhaben(bauvorhaben: String?) = apply { this.bauvorhaben = bauvorhaben }
        fun kundeName(kundeName: String?) = apply { this.kundeName = kundeName }
        fun kundeEmail(kundeEmail: String?) = apply { this.kundeEmail = kundeEmail }
        fun erstelltAm(erstelltAm: LocalDateTime?) = apply { this.erstelltAm = erstelltAm }
        fun ablaufDatum(ablaufDatum: LocalDateTime?) = apply { this.ablaufDatum = ablaufDatum }
        fun akzeptiertAm(akzeptiertAm: LocalDateTime?) = apply { this.akzeptiertAm = akzeptiertAm }
        fun abgelaufen(abgelaufen: Boolean) = apply { this.abgelaufen = abgelaufen }
        fun pdfPfad(pdfPfad: String?) = apply { this.pdfPfad = pdfPfad }
        fun positionen(positionen: List<FreigabePositionDto>?) = apply { this.positionen = positionen }
        fun basisNetto(basisNetto: BigDecimal?) = apply { this.basisNetto = basisNetto }
        fun basisBrutto(basisBrutto: BigDecimal?) = apply { this.basisBrutto = basisBrutto }
        fun mwstProzent(mwstProzent: BigDecimal?) = apply { this.mwstProzent = mwstProzent }
        fun hatAlternativen(hatAlternativen: Boolean) = apply { this.hatAlternativen = hatAlternativen }

        fun build(): FreigabeAnsichtDto =
            FreigabeAnsichtDto(
                uuid,
                status,
                dokumentNummer,
                dokumentArt,
                dokumentBetrag,
                bauvorhaben,
                kundeName,
                kundeEmail,
                erstelltAm,
                ablaufDatum,
                akzeptiertAm,
                abgelaufen,
                pdfPfad,
                positionen,
                basisNetto,
                basisBrutto,
                mwstProzent,
                hatAlternativen,
            )
    }

    companion object {
        @JvmStatic
        fun builder(): FreigabeAnsichtDtoBuilder = FreigabeAnsichtDtoBuilder()
    }
}
