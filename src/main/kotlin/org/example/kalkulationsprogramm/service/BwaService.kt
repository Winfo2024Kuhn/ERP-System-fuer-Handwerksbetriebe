package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.BwaPosition
import org.example.kalkulationsprogramm.domain.BwaUpload
import org.example.kalkulationsprogramm.dto.BwaPositionDto
import org.example.kalkulationsprogramm.dto.BwaUploadDto
import org.example.kalkulationsprogramm.repository.BwaUploadRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Comparator
import java.util.Optional

@Service
class BwaService(
    private val bwaUploadRepository: BwaUploadRepository,
) {
    @Transactional(readOnly = true)
    fun findByJahr(jahr: Int?): List<BwaUploadDto> =
        bwaUploadRepository.findByJahrOrderByMonatDesc(jahr).map { toDto(it) }

    @Transactional(readOnly = true)
    fun findById(id: Long?): BwaUploadDto? =
        if (id == null) null else bwaUploadRepository.findById(id).map { toDto(it) }.orElse(null)

    @Transactional
    fun delete(id: Long?) {
        if (id != null) {
            bwaUploadRepository.deleteById(id)
        }
    }

    @Transactional(readOnly = true)
    fun findAvailableYears(): List<Int> =
        bwaUploadRepository.findAll()
            .mapNotNull { it.jahr }
            .distinct()
            .sortedWith(Comparator.reverseOrder())

    private fun toDto(b: BwaUpload): BwaUploadDto {
        val dto = BwaUploadDto()
        dto.id = b.id
        dto.typ = b.typ
        dto.jahr = b.jahr
        dto.monat = b.monat
        dto.originalDateiname = b.originalDateiname
        dto.pdfUrl = "/api/bwa/${b.id}/pdf"
        dto.uploadDatum = b.uploadDatum
        dto.analyseDatum = b.analyseDatum
        dto.aiConfidence = b.aiConfidence
        dto.analysiert = b.analysiert
        dto.freigegeben = b.freigegeben
        dto.freigegebenAm = b.freigegebenAm

        val freigegebenVon = b.freigegebenVon
        if (freigegebenVon != null) {
            dto.freigegebenVonName = "${freigegebenVon.vorname} ${freigegebenVon.nachname}"
        }

        dto.gesamtGemeinkosten = b.gesamtGemeinkosten
        dto.kostenAusRechnungen = b.kostenAusRechnungen
        dto.kostenAusBwa = b.kostenAusBwa

        val steuerberater = b.steuerberater
        if (steuerberater != null) {
            dto.steuerberaterName = steuerberater.stringValue("getName")
        }

        dto.positionen = b.positionen.map { toPositionDto(it) }
        return dto
    }

    private fun toPositionDto(p: BwaPosition): BwaPositionDto {
        val dto = BwaPositionDto()
        dto.id = p.id
        dto.kontonummer = p.kontonummer
        dto.bezeichnung = p.bezeichnung
        dto.betragMonat = p.betragMonat
        dto.betragKumuliert = p.betragKumuliert
        dto.kategorie = p.kategorie

        val kostenstelle = p.kostenstelle
        if (kostenstelle != null) {
            dto.kostenstelleId = kostenstelle.longValue("getId")
            dto.kostenstelleBezeichnung = kostenstelle.stringValue("getBezeichnung")
        }

        dto.inRechnungenGefunden = p.inRechnungenGefunden
        dto.rechnungssumme = p.rechnungssumme
        dto.differenz = p.differenz
        dto.manuellKorrigiert = p.manuellKorrigiert
        dto.notiz = p.notiz
        return dto
    }

    @Transactional(readOnly = true)
    fun findStoredFilename(id: Long?): Optional<String> =
        if (id == null) Optional.empty() else bwaUploadRepository.findById(id).map { it.gespeicherterDateiname }

    private fun Any.longValue(getter: String): Long? =
        javaClass.getMethod(getter).invoke(this) as? Long

    private fun Any.stringValue(getter: String): String? =
        javaClass.getMethod(getter).invoke(this) as? String
}
