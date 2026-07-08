package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.*
import org.example.kalkulationsprogramm.dto.Projekt.UmsatzStatistikDto
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.time.Month
import java.time.LocalDate
import java.nio.file.Path

@Service
open class DateiSpeicherService(
    private val projektDokumentRepository: ProjektDokumentRepository,
    private val anfrageDokumentRepository: AnfrageDokumentRepository,
) {
    fun speichereDatei(datei: MultipartFile, projektID: Long): ProjektDokument = speichereDatei(datei, projektID, DokumentGruppe.DIVERSE_DOKUMENTE)
    fun speichereDatei(datei: MultipartFile, projektID: Long, gruppe: DokumentGruppe?): ProjektDokument = ProjektDokument()
    fun speichereDatei(datei: MultipartFile, projektID: Long, gruppe: DokumentGruppe?, geschaeftsdokumentart: String?): ProjektDokument = ProjektDokument()
    fun speichereAnfragesDatei(datei: MultipartFile, anfrageID: Long, gruppe: DokumentGruppe?): AnfrageDokument = AnfrageDokument()
    fun speichereZugferdDatei(zugferdPfad: Path, originalDateiname: String?, projektID: Long, art: String?): ProjektGeschaeftsdokument = ProjektGeschaeftsdokument()
    fun speichereAnfragesZugferdDatei(zugferdPfad: Path, originalDateiname: String?, anfrageID: Long, art: String?): AnfrageGeschaeftsdokument = AnfrageGeschaeftsdokument()
    fun speichereAnfragesZugferdDatei(zugferdPfad: Path, originalDateiname: String?, anfrageID: Long, daten: Any?): AnfrageGeschaeftsdokument = AnfrageGeschaeftsdokument()
    fun speichereErzeugteDatei(inhalt: ByteArray, dateiname: String?, projektID: Long, gruppe: DokumentGruppe?): ProjektDokument = ProjektDokument()
    fun verschiebeAnfragesDatei(anfrageDokument: AnfrageDokument, projekt: Projekt) {}
    fun aktualisiereProjektFinanzstatus(projektID: Long) {}
    fun holeDokumenteZuProjekt(projektID: Long): List<ProjektDokument> =
        projektDokumentRepository.findByProjektId(projektID)

    fun holeDokumenteZuAnfrage(anfrageID: Long): List<AnfrageDokument> =
        anfrageDokumentRepository.findByAnfrageId(anfrageID)

    fun holeOffeneGeschaeftsdokumente(): List<ProjektGeschaeftsdokument> =
        projektDokumentRepository.findOffeneGeschaeftsdokumente()

    fun holeRechnungenZuProjekt(projektId: Long): List<ProjektGeschaeftsdokument> =
        projektDokumentRepository.findRechnungenByProjektId(projektId)

    fun holeGeschaeftsdokumenteNachJahrUndFilter(jahr: Int, monat: Int?, bezahlt: Boolean?): List<ProjektGeschaeftsdokument> {
        val start = if (monat == null) {
            LocalDate.of(jahr, Month.JANUARY, 1)
        } else {
            LocalDate.of(jahr, monat.coerceIn(1, 12), 1)
        }
        val end = if (monat == null) start.plusYears(1).minusDays(1) else start.plusMonths(1).minusDays(1)
        return projektDokumentRepository.findGeschaeftsdokumenteByRechnungsdatumBetween(start, end)
            .filter { bezahlt == null || it.bezahlt == bezahlt }
    }

    fun berechneProjektArbeitskosten(projekt: Projekt?): Double =
        projekt?.zeitbuchungen.orEmpty().fold(BigDecimal.ZERO) { sum, zeit ->
            val stunden = zeit.anzahlInStunden ?: BigDecimal.ZERO
            val satz = zeit.arbeitsgangStundensatz?.satz ?: BigDecimal.ZERO
            sum.add(stunden.multiply(satz))
        }.toDouble()

    fun berechneProjektMaterialkosten(projekt: Projekt?): Double =
        projekt?.materialkosten.orEmpty().fold(BigDecimal.ZERO) { sum, material -> sum.add(material.betrag) }.toDouble()

    fun berechneProjektMaterialkosten(projekt: Projekt?, monat: Int?): Double =
        projekt?.materialkosten.orEmpty()
            .asSequence()
            .filter { monat == null || it.monat == monat }
            .fold(BigDecimal.ZERO) { sum, material -> sum.add(material.betrag) }
            .toDouble()

    fun berechneProjektKosten(projekt: Projekt?): Double =
        berechneProjektArbeitskosten(projekt) + berechneProjektMaterialkosten(projekt)
    fun holeUmsatzStatistiken(jahr: Int, monat: Int?): UmsatzStatistikDto = UmsatzStatistikDto()
    fun holeDokument(dokumentID: Long): ProjektDokument? =
        projektDokumentRepository.findById(dokumentID).orElse(null)
    fun setzeGeschaeftsdokumentBezahlt(dokumentID: Long, bezahlt: Boolean) {}
    fun loescheDatei(dokumentID: Long) {}
    fun loescheAnfrageDatei(dokumentID: Long?) {}
    fun speichereBild(datei: MultipartFile): String = ""
    fun kopiereBildZuDokumenten(quellDateiname: String?): String = quellDateiname.orEmpty()
    fun loescheBild(bildUrl: String?) {}
    fun ladeBildAlsResource(dateiname: String?): Resource = UrlResource(Path.of(dateiname.orEmpty()).toUri())
    fun loescheDokumentPdfByDateiname(dateiname: String?) {}
    fun ladeDokumentAlsResource(dateiname: String?): Resource = UrlResource(Path.of(dateiname.orEmpty()).toUri())
    fun liegtInHicadSpeicher(dateiname: String?): Boolean = false
    fun holeNetzwerkPfad(relativerPfad: String?): String = relativerPfad.orEmpty()
    fun holeWindowsLaufwerkPfad(relativerPfad: String?): String = relativerPfad.orEmpty()
    fun ladeDokumentMetadaten(dateiname: String?): Dokument {
        val normalized = dateiname?.trim().orEmpty()
        if (normalized.isNotEmpty()) {
            projektDokumentRepository.findByGespeicherterDateinameIgnoreCase(normalized).orElse(null)?.let { return it }
            anfrageDokumentRepository.findByGespeicherterDateinameIgnoreCase(normalized).orElse(null)?.let { return it }
        }
        return object : Dokument {
                override val id: Long? = null
                override val originalDateiname: String? = dateiname
                override val gespeicherterDateiname: String? = dateiname
                override val dateityp: String? = null
                override val dateigroesse: Long? = null
                override val uploadDatum: LocalDate? = null
                override val emailVersandDatum: LocalDate? = null
                override val dokumentGruppe: DokumentGruppe? = null
            }
    }
}
