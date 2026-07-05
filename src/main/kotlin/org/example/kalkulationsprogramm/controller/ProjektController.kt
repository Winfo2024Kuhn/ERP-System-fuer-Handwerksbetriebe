package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.dto.Projekt.ProjektResponseDto
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/projekte")
class ProjektController {
    @GetMapping("/{id}")
    fun getProjektById(@PathVariable id: Long): ResponseEntity<ProjektResponseDto> =
        ResponseEntity.notFound().build()

    data class ProjektSucheDto(
        val id: Long?,
        val bauvorhaben: String?,
        val auftragsnummer: String?,
        val kunde: String?,
        val abgeschlossen: Boolean,
    )

    data class NaechsteAuftragsnummerResponse(val auftragsnummer: String?, val prefix: String?, val zaehler: Long)

    data class AuftragsnummerValidierungResponse(val verfuegbar: Boolean, val message: String?)

    class EingangsrechnungDto {
        var id: Long? = null
        var dokumentId: Long? = null
        var geschaeftsdokumentId: Long? = null
        var dokumentNummer: String? = null
        var dateiname: String? = null
        var dokumentDatum: LocalDate? = null
        var gesamtbetrag: BigDecimal? = null
        var prozent: Int? = null
        var berechneterBetrag: BigDecimal? = null
        var beschreibung: String? = null
        var lieferantId: Long? = null
        var lieferantName: String? = null
        var pdfUrl: String? = null
        var zugeordnetVonName: String? = null
        var zugeordnetAm: LocalDateTime? = null
        var alleZuordnungen: List<AnteilDto>? = null
        var dokumentenKette: List<DokumentKetteRefDto>? = null
    }

    class AnteilDto {
        var projektId: Long? = null
        var projektName: String? = null
        var projektNummer: String? = null
        var kostenstelleId: Long? = null
        var kostenstelleName: String? = null
        var prozent: Int? = null
        var berechneterBetrag: BigDecimal? = null
        var beschreibung: String? = null
        var zugeordnetVonName: String? = null
        var zugeordnetAm: LocalDateTime? = null
    }

    class DokumentKetteRefDto {
        var id: Long? = null
        var typ: String? = null
        var dokumentNummer: String? = null
        var dokumentDatum: LocalDate? = null
        var betragNetto: BigDecimal? = null
        var pdfUrl: String? = null
    }

    class ProjektNotizDto {
        var id: Long? = null
        var notiz: String? = null
        var erstelltAm: LocalDateTime? = null
        var mitarbeiterId: Long? = null
        var mitarbeiterVorname: String? = null
        var mitarbeiterNachname: String? = null
        var mobileSichtbar: Boolean = true
        var nurFuerErsteller: Boolean = false
        var canEdit: Boolean = false
        var bilder: List<ProjektNotizBildDto>? = null
    }

    class ProjektNotizBildDto {
        var id: Long? = null
        var originalDateiname: String? = null
        var url: String? = null
        var erstelltAm: LocalDateTime? = null
    }

    class ProjektNotizCreateDto {
        var notiz: String? = null
        var mobileSichtbar: Boolean = true
        var nurFuerErsteller: Boolean = false
    }
}
