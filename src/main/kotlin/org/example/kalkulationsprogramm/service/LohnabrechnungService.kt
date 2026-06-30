package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Lohnabrechnung
import org.example.kalkulationsprogramm.dto.LohnabrechnungDto
import org.example.kalkulationsprogramm.repository.LohnabrechnungRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

@Service
open class LohnabrechnungService(
    private val lohnabrechnungRepository: LohnabrechnungRepository,
) {

    @Value("\${file.lohnabrechnung-dir:uploads/lohnabrechnungen}")
    private lateinit var lohnabrechnungDir: String

    @Value("\${file.mail-attachment-dir}")
    private lateinit var mailAttachmentDir: String

    @Transactional(readOnly = true)
    open fun findByMitarbeiterId(mitarbeiterId: Long?): List<LohnabrechnungDto> =
        lohnabrechnungRepository.findByMitarbeiterIdOrderByJahrDescMonatDesc(mitarbeiterId)
            .map(::toDto)

    @Transactional(readOnly = true)
    open fun findByJahr(jahr: Int?): List<LohnabrechnungDto> =
        lohnabrechnungRepository.findByJahrOrderByMonatDescMitarbeiterNachnameAsc(jahr)
            .map(::toDto)

    @Transactional(readOnly = true)
    open fun findBySteuerberaterAndJahr(steuerberaterId: Long?, jahr: Int?): List<LohnabrechnungDto> =
        lohnabrechnungRepository
            .findBySteuerberaterIdAndJahrOrderByMonatDescMitarbeiterNachnameAsc(steuerberaterId, jahr)
            .map(::toDto)

    @Transactional(readOnly = true)
    open fun findAvailableYears(): List<Int> = lohnabrechnungRepository.findDistinctJahre()

    @Transactional(readOnly = true)
    open fun findById(id: Long?): LohnabrechnungDto? =
        lohnabrechnungRepository.findById(id!!).map(::toDto).orElse(null)

    @Transactional
    open fun save(lohnabrechnung: Lohnabrechnung): Lohnabrechnung =
        lohnabrechnungRepository.save(lohnabrechnung)

    data class PdfDatei(
        val pfad: Path,
        val anzeigeName: String,
    ) {
        fun pfad(): Path = pfad

        fun anzeigeName(): String = anzeigeName
    }

    @Transactional(readOnly = true)
    open fun findPdf(id: Long?): Optional<PdfDatei> =
        lohnabrechnungRepository.findById(id!!).flatMap { la ->
            val gespeichert = la.gespeicherterDateiname
            if (gespeichert.isNullOrBlank()) {
                return@flatMap Optional.empty()
            }

            val anzeigeName = la.originalDateiname ?: "lohnabrechnung.pdf"
            for (dir in listOf(lohnabrechnungDir, mailAttachmentDir)) {
                val basis = Path.of(dir).toAbsolutePath().normalize()
                val datei = basis.resolve(gespeichert).normalize()
                if (!datei.startsWith(basis)) {
                    log.warn("[Lohnabrechnung] Ungueltiger Dateipfad fuer ID {}: {}", id, gespeichert)
                    return@flatMap Optional.empty()
                }
                if (Files.exists(datei)) {
                    return@flatMap Optional.of(PdfDatei(datei, anzeigeName))
                }
            }

            log.warn("[Lohnabrechnung] PDF-Datei nicht gefunden fuer ID {} ({})", id, gespeichert)
            Optional.empty()
        }

    @Transactional
    open fun delete(id: Long?) {
        lohnabrechnungRepository.deleteById(id!!)
    }

    private fun toDto(la: Lohnabrechnung): LohnabrechnungDto {
        val dto = LohnabrechnungDto()
        val mitarbeiter = la.mitarbeiter
        val steuerberater = la.steuerberater
        dto.id = la.id
        dto.mitarbeiterId = mitarbeiter?.id
        dto.mitarbeiterName = listOfNotNull(mitarbeiter?.vorname, mitarbeiter?.nachname)
            .joinToString(" ")

        if (steuerberater != null) {
            dto.steuerberaterId = invokeLong(steuerberater, "getId")
            dto.steuerberaterName = invokeString(steuerberater, "getName")
        }

        dto.jahr = la.jahr
        dto.monat = la.monat
        dto.originalDateiname = la.originalDateiname
        dto.downloadUrl = "/api/lohnabrechnungen/${la.id}/download"
        dto.bruttolohn = la.bruttolohn
        dto.nettolohn = la.nettolohn
        dto.importDatum = la.importDatum
        dto.status = la.status?.name

        return dto
    }

    private fun invokeLong(target: Any, methodName: String): Long? =
        target.javaClass.getMethod(methodName).invoke(target) as? Long

    private fun invokeString(target: Any, methodName: String): String? =
        target.javaClass.getMethod(methodName).invoke(target) as? String

    companion object {
        private val log = LoggerFactory.getLogger(LohnabrechnungService::class.java)
    }
}
