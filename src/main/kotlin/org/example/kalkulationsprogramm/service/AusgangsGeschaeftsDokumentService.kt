package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument
import org.example.kalkulationsprogramm.domain.Projekt
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AbrechnungsverlaufDto
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentErstellenDto
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentUpdateDto
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabePositionDto
import org.example.kalkulationsprogramm.dto.Produktkategroie.KategorieVorschlagDto
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
open class AusgangsGeschaeftsDokumentService {
    fun erstellen(dto: AusgangsGeschaeftsDokumentErstellenDto): AusgangsGeschaeftsDokument = erstellen(dto, null)
    fun erstellen(dto: AusgangsGeschaeftsDokumentErstellenDto, ipAdresse: String?): AusgangsGeschaeftsDokument = AusgangsGeschaeftsDokument()
    fun findGeerbteRechnungsadresse(projektId: Long?, anfrageId: Long?): String? = null
    fun findGeerbteRechnungsadresseFuerAnfrage(anfrageId: Long?): String? = null
    fun ensureAnfrageDokument(anfrageId: Long): String = ""
    fun resolveAnfragesnummer(anfrageId: Long?): String? = anfrageId?.let { "" }
    fun resolveAngebotsnummer(angebotId: Long): String = ""
    fun aktualisiereAngebotPreisAusDokumenten(angebotId: Long) {}
    fun aktualisieren(id: Long, dto: AusgangsGeschaeftsDokumentUpdateDto): AusgangsGeschaeftsDokument = AusgangsGeschaeftsDokument().apply { this.id = id }
    fun buchen(id: Long): AusgangsGeschaeftsDokument = buchen(id, null, null)
    fun buchen(id: Long, bearbeiterId: Long?, ipAdresse: String?): AusgangsGeschaeftsDokument = AusgangsGeschaeftsDokument().apply { this.id = id }
    fun buchenNachEmailVersand(id: Long): AusgangsGeschaeftsDokument = buchenNachEmailVersand(id, null, null)
    fun buchenNachEmailVersand(id: Long, bearbeiterId: Long?, ipAdresse: String?): AusgangsGeschaeftsDokument = AusgangsGeschaeftsDokument().apply { this.id = id }
    fun stornieren(id: Long): AusgangsGeschaeftsDokument = stornieren(id, null, null, null)
    fun stornieren(id: Long, bearbeiterId: Long?, ipAdresse: String?, grund: String?): AusgangsGeschaeftsDokument = AusgangsGeschaeftsDokument().apply { this.id = id }
    fun speicherePdfFuerDokument(dokumentId: Long, pdfBytes: ByteArray): String = "dokument-$dokumentId.pdf"
    fun findByProjekt(projektId: Long): List<AusgangsGeschaeftsDokumentResponseDto> = emptyList()
    fun findByAnfrage(anfrageId: Long): List<AusgangsGeschaeftsDokumentResponseDto> = emptyList()
    fun migrateFromAnfrageToProjekt(anfrageId: Long, projekt: Projekt) {}
    fun findById(id: Long): AusgangsGeschaeftsDokumentResponseDto? = null
    fun loeschen(id: Long, begruendung: String?) = loeschen(id, begruendung, null, null)
    fun loeschen(id: Long, begruendung: String?, geloeschtVonId: Long?, ipAdresse: String?) {}
    fun getAbrechnungsverlauf(basisdokumentId: Long): AbrechnungsverlaufDto = AbrechnungsverlaufDto(basisdokumentId = basisdokumentId)
    fun aktualisiereProjektPreisAusDokumenten(projektId: Long) {}
    fun aktualisiereAnfragePreisAusDokumenten(anfrageId: Long?) {}
    fun aktualisiereProjektProduktkategorienAusDokumenten(projektId: Long) {}
    fun berechneKategorieVorschlagFuerAnfrage(anfrageId: Long): List<KategorieVorschlagDto> = emptyList()
    fun baueKundenPositionen(positionenJson: String?): List<FreigabePositionDto> = emptyList()
    fun sammleOptionaleAlternativIds(positionenJson: String?): Set<String> = emptySet()
    fun summeAusgewaehlterAlternativenNetto(positionenJson: String?, blockIds: Set<String>?): BigDecimal = BigDecimal.ZERO
    fun markiereAlternativenAlsBeauftragt(positionenJson: String?, blockIds: Set<String>?): String = positionenJson.orEmpty()
    fun bereitePositionenFuerTypwechsel(positionenJson: String?): String = positionenJson.orEmpty()
}
