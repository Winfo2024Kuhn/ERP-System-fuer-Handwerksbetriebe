package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument
import org.example.kalkulationsprogramm.domain.DokumentFreigabe
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeAuditDto
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabePositionDto
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Optional
import java.util.UUID

@Service
open class DokumentFreigabeService {
    fun erstelleFuerAnfrage(dokument: AnfrageGeschaeftsdokument, kundeName: String?, kundeEmail: String?): DokumentFreigabe =
        erstelleFuerAnfrage(dokument, kundeName, kundeEmail, DEFAULT_GUELTIGKEITS_TAGE)

    fun erstelleFuerAnfrage(
        dokument: AnfrageGeschaeftsdokument,
        kundeName: String?,
        kundeEmail: String?,
        gueltigkeitTage: Int,
    ): DokumentFreigabe = neueFreigabe(kundeEmail)

    fun erstelleFuerProjekt(dokument: ProjektGeschaeftsdokument, kundeName: String?, kundeEmail: String?): DokumentFreigabe =
        erstelleFuerProjekt(dokument, kundeName, kundeEmail, DEFAULT_GUELTIGKEITS_TAGE)

    fun erstelleFuerProjekt(
        dokument: ProjektGeschaeftsdokument,
        kundeName: String?,
        kundeEmail: String?,
        gueltigkeitTage: Int,
    ): DokumentFreigabe = neueFreigabe(kundeEmail)

    fun erstelleFuerAusgangsGeschaeftsDokument(
        dok: AusgangsGeschaeftsDokument,
        kundeEmail: String?,
        pdfDateiname: String?,
    ): DokumentFreigabe = erstelleFuerAusgangsGeschaeftsDokument(dok, kundeEmail, pdfDateiname, DEFAULT_GUELTIGKEITS_TAGE)

    fun erstelleFuerAusgangsGeschaeftsDokument(
        dok: AusgangsGeschaeftsDokument,
        kundeEmail: String?,
        pdfDateiname: String?,
        gueltigkeitTage: Int,
    ): DokumentFreigabe = neueFreigabe(kundeEmail)

    fun buildPublicUrl(freigabe: DokumentFreigabe): String = "/freigabe/${freigabe.uuid}"

    fun loeschePdfFuerFreigabe(uuid: String) {}

    fun erstelleFreigabeBlockFuerDokument(
        dokumentId: Long,
        isAnfrage: Boolean,
        recipient: String?,
        pdfDateiname: String?,
    ): Optional<String> = erstelleFreigabeBlockFuerDokument(dokumentId, isAnfrage, recipient, pdfDateiname, DEFAULT_GUELTIGKEITS_TAGE)

    fun erstelleFreigabeBlockFuerDokument(
        dokumentId: Long,
        isAnfrage: Boolean,
        recipient: String?,
        pdfDateiname: String?,
        gueltigkeitTage: Int,
    ): Optional<String> = Optional.empty()

    fun findByUuidUndAktualisiereStatus(uuid: String): Optional<DokumentFreigabe> = Optional.empty()

    fun akzeptiere(
        uuid: String,
        ip: String?,
        userAgent: String?,
        email: String?,
        name: String?,
    ): DokumentFreigabe = throw IllegalArgumentException(UNBEKANNTE_UUID_MESSAGE)

    fun akzeptiere(
        uuid: String,
        ip: String?,
        userAgent: String?,
        email: String?,
        name: String?,
        ausgewaehlteAlternativIds: List<String>?,
    ): DokumentFreigabe = throw IllegalArgumentException(UNBEKANNTE_UUID_MESSAGE)

    fun akzeptiere(
        uuid: String,
        ip: String?,
        userAgent: String?,
        email: String?,
        vorname: String?,
        nachname: String?,
        unterzeichnerName: String?,
        ausgewaehlteAlternativIds: List<String>?,
    ): DokumentFreigabe = throw IllegalArgumentException(UNBEKANNTE_UUID_MESSAGE)

    fun ladePositionsAnsicht(f: DokumentFreigabe): FreigabePositionsAnsicht =
        FreigabePositionsAnsicht(emptyList(), BigDecimal.ZERO, null, BigDecimal.ZERO, false, null, null)

    fun findAuditByQuelle(typ: FreigabeQuellTyp, quellDokumentId: Long): Optional<FreigabeAuditDto> = Optional.empty()

    fun findJuengsteProQuelle(typ: FreigabeQuellTyp, quellDokumentIds: List<Long>): Map<Long, DokumentFreigabe> = emptyMap()

    fun findJuengsteProAnfrage(anfrageIds: List<Long>): Map<Long, DokumentFreigabe> = emptyMap()

    fun findJuengsteProProjekt(projektIds: List<Long>): Map<Long, DokumentFreigabe> = emptyMap()

    private fun neueFreigabe(email: String?): DokumentFreigabe =
        DokumentFreigabe().apply {
            uuid = UUID.randomUUID().toString()
            kundeEmail = email
            erstelltAm = LocalDateTime.now()
            ablaufDatum = LocalDateTime.now().plusDays(DEFAULT_GUELTIGKEITS_TAGE.toLong())
        }

    data class FreigabePositionsAnsicht(
        val positionen: List<FreigabePositionDto>,
        val basisNetto: BigDecimal?,
        val basisBrutto: BigDecimal?,
        val mwstProzent: BigDecimal?,
        val hatAlternativen: Boolean,
        val alternativAuswahlBetragBrutto: BigDecimal?,
        val alternativAuswahlJson: String?,
    ) {
        fun positionen(): List<FreigabePositionDto> = positionen
        fun basisNetto(): BigDecimal? = basisNetto
        fun basisBrutto(): BigDecimal? = basisBrutto
        fun mwstProzent(): BigDecimal? = mwstProzent
        fun hatAlternativen(): Boolean = hatAlternativen
        fun alternativAuswahlBetragBrutto(): BigDecimal? = alternativAuswahlBetragBrutto
        fun alternativAuswahlJson(): String? = alternativAuswahlJson
    }

    companion object {
        const val DEFAULT_GUELTIGKEITS_TAGE = 14
        const val UNBEKANNTE_UUID_MESSAGE = "Unbekannte Freigabe-UUID"

        @JvmStatic
        fun buildFreigabeBlockHtml(
            url: String,
            dokumentArt: String?,
            gueltigkeitTage: Int,
            ablaufDatum: LocalDateTime?,
        ): String {
            val datum = ablaufDatum?.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")).orEmpty()
            return """<p><a href="$url">${dokumentArt ?: "Dokument"} freigeben</a><br/>Gueltig bis $datum</p>"""
        }
    }
}
