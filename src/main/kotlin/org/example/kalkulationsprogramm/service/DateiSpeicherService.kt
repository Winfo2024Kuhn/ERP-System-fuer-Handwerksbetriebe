package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.*
import org.example.kalkulationsprogramm.dto.Projekt.UmsatzStatistikDto
import org.springframework.core.io.Resource
import org.springframework.core.io.UrlResource
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.nio.file.Path

@Service
open class DateiSpeicherService {
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
    fun holeDokumenteZuProjekt(projektID: Long): List<ProjektDokument> = emptyList()
    fun holeDokumenteZuAnfrage(anfrageID: Long): List<AnfrageDokument> = emptyList()
    fun holeOffeneGeschaeftsdokumente(): List<ProjektGeschaeftsdokument> = emptyList()
    fun holeRechnungenZuProjekt(projektId: Long): List<ProjektGeschaeftsdokument> = emptyList()
    fun holeGeschaeftsdokumenteNachJahrUndFilter(jahr: Int, monat: Int?, bezahlt: Boolean?): List<ProjektGeschaeftsdokument> = emptyList()
    fun berechneProjektArbeitskosten(projekt: Projekt?): Double = 0.0
    fun berechneProjektMaterialkosten(projekt: Projekt?): Double = 0.0
    fun berechneProjektMaterialkosten(projekt: Projekt?, monat: Int?): Double = 0.0
    fun berechneProjektKosten(projekt: Projekt?): Double = 0.0
    fun holeUmsatzStatistiken(jahr: Int, monat: Int?): UmsatzStatistikDto = UmsatzStatistikDto()
    fun holeDokument(dokumentID: Long): ProjektDokument? = null
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
    fun ladeDokumentMetadaten(dateiname: String?): Dokument =
        object : Dokument {
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
