package org.example.kalkulationsprogramm.controller

import jakarta.validation.Valid
import org.example.kalkulationsprogramm.domain.DokumentFreigabe
import org.example.kalkulationsprogramm.domain.FreigabeStatus
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeAkzeptierenRequest
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeAkzeptiertResponse
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeAnsichtDto
import org.example.kalkulationsprogramm.service.DateiSpeicherService
import org.example.kalkulationsprogramm.service.DokumentFreigabeService
import org.slf4j.LoggerFactory
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/internal/freigabe")
class FreigabeInternalController(
    private val freigabeService: DokumentFreigabeService,
    private val dateiSpeicherService: DateiSpeicherService,
) {
    @GetMapping("/{uuid}")
    fun hole(@PathVariable uuid: String): ResponseEntity<FreigabeAnsichtDto> {
        val opt = freigabeService.findByUuidUndAktualisiereStatus(uuid)
        if (opt.isEmpty) {
            return ResponseEntity.notFound().build()
        }
        return ResponseEntity.ok(toDto(opt.get()))
    }

    @GetMapping("/{uuid}/pdf")
    fun ladeDokumentPdf(@PathVariable uuid: String): ResponseEntity<Resource> {
        val opt = freigabeService.findByUuidUndAktualisiereStatus(uuid)
        if (opt.isEmpty) {
            return ResponseEntity.notFound().build()
        }
        val freigabe = opt.get()
        if (freigabe.status == FreigabeStatus.EXPIRED || freigabe.status == FreigabeStatus.REVOKED) {
            return ResponseEntity.status(410).build()
        }
        if (freigabe.dokumentDatei.isNullOrBlank()) {
            return ResponseEntity.notFound().build()
        }

        return try {
            val resource = dateiSpeicherService.ladeDokumentAlsResource(freigabe.dokumentDatei)
            val dokumentArt = freigabe.dokumentArt?.replace(Regex("\\s+"), "") ?: "Dokument"
            val nummer = freigabe.dokumentNummer ?: freigabe.uuid
            val anzeigeName = "${dokumentArt}_${nummer}.pdf"
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$anzeigeName\"")
                .body(resource)
        } catch (ex: Exception) {
            log.warn("PDF zur Freigabe {} nicht ladbar: {}", uuid, ex.message)
            ResponseEntity.notFound().build()
        }
    }

    @DeleteMapping("/{uuid}/pdf")
    fun loeschePdf(@PathVariable uuid: String): ResponseEntity<Void> {
        freigabeService.loeschePdfFuerFreigabe(uuid)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{uuid}/akzeptieren")
    fun akzeptiere(
        @PathVariable uuid: String,
        @Valid @RequestBody request: FreigabeAkzeptierenRequest,
    ): ResponseEntity<*> {
        if (!request.isBestaetigung) {
            return ResponseEntity.badRequest().body(
                mapOf(
                    "success" to false,
                    "message" to "Bitte bestätigen Sie, dass Sie das Dokument geprüft haben.",
                ),
            )
        }

        return try {
            val freigabe = freigabeService.akzeptiere(
                uuid,
                request.clientIp,
                truncate(request.userAgent, 500),
                request.email,
                request.vorname,
                request.nachname,
                request.unterzeichnerName,
                request.ausgewaehlteAlternativen,
            )
            ResponseEntity.ok(
                FreigabeAkzeptiertResponse.builder()
                    .uuid(freigabe.uuid)
                    .dokumentNummer(freigabe.dokumentNummer)
                    .dokumentArt(freigabe.dokumentArt)
                    .akzeptiertAm(freigabe.akzeptiertAm)
                    .hashAcceptance(freigabe.hashAcceptance)
                    .unterzeichnerName(freigabe.unterzeichnerName)
                    .build(),
            )
        } catch (ex: IllegalArgumentException) {
            if (DokumentFreigabeService.UNBEKANNTE_UUID_MESSAGE == ex.message) {
                ResponseEntity.notFound().build<Void>()
            } else {
                ResponseEntity.badRequest().body(
                    mapOf(
                        "success" to false,
                        "message" to (ex.message ?: "Ungültige Eingabe."),
                    ),
                )
            }
        } catch (ex: IllegalStateException) {
            ResponseEntity.status(410).body(
                mapOf(
                    "success" to false,
                    "message" to ex.message,
                ),
            )
        }
    }

    private fun toDto(freigabe: DokumentFreigabe): FreigabeAnsichtDto {
        val builder = FreigabeAnsichtDto.builder()
            .uuid(freigabe.uuid)
            .status(freigabe.status?.name)
            .dokumentNummer(freigabe.dokumentNummer)
            .dokumentArt(freigabe.dokumentArt)
            .dokumentBetrag(freigabe.dokumentBetrag)
            .bauvorhaben(freigabe.bauvorhaben)
            .kundeName(freigabe.kundeName)
            .kundeEmail(freigabe.kundeEmail)
            .erstelltAm(freigabe.erstelltAm)
            .ablaufDatum(freigabe.ablaufDatum)
            .akzeptiertAm(freigabe.akzeptiertAm)
            .abgelaufen(freigabe.istAbgelaufen())
            .pdfPfad("/api/internal/freigabe/${freigabe.uuid}/pdf")

        val ansicht = freigabeService.ladePositionsAnsicht(freigabe)
        if (ansicht != null) {
            builder.positionen(ansicht.positionen())
                .basisNetto(ansicht.basisNetto())
                .basisBrutto(ansicht.basisBrutto())
                .mwstProzent(ansicht.mwstProzent())
                .hatAlternativen(ansicht.hatAlternativen())
        }
        return builder.build()
    }

    private fun truncate(value: String?, max: Int): String? =
        if (value == null || value.length <= max) value else value.substring(0, max)

    companion object {
        private val log = LoggerFactory.getLogger(FreigabeInternalController::class.java)
    }
}
