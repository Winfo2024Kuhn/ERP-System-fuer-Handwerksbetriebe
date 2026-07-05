package org.example.kalkulationsprogramm.controller

import jakarta.servlet.http.HttpServletRequest
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal
import org.example.kalkulationsprogramm.domain.FreigabeQuellTyp
import org.example.kalkulationsprogramm.domain.Mahnstufe
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentErstellenDto
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentUpdateDto
import org.example.kalkulationsprogramm.dto.DokumentLockDto
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeAuditDto
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeStatusKurzDto
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentAuditService
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService
import org.example.kalkulationsprogramm.service.AutoMahnVersandService
import org.example.kalkulationsprogramm.service.DokumentFreigabeService
import org.example.kalkulationsprogramm.service.DokumentLockService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ausgangs-dokumente")
class AusgangsGeschaeftsDokumentController(
    private val service: AusgangsGeschaeftsDokumentService,
    private val dokumentFreigabeService: DokumentFreigabeService,
    private val auditService: AusgangsGeschaeftsDokumentAuditService,
    private val autoMahnVersandService: AutoMahnVersandService,
    private val dokumentLockService: DokumentLockService,
) {
    @GetMapping("/{id}/mahnung-vorschau")
    fun mahnungVorschau(@PathVariable id: Long, @RequestParam("stufe") stufe: Mahnstufe): ResponseEntity<ByteArray> =
        try {
            val pdf = autoMahnVersandService.generiereVorschauPdfFuerAusgangsRechnung(id, stufe)
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=mahn-vorschau-$id.pdf")
                .body(pdf)
        } catch (e: IllegalArgumentException) {
            log.warn("Mahn-Vorschau fuer Dokument {} fehlgeschlagen: {}", id, e.message)
            ResponseEntity.notFound().build()
        }

    @GetMapping("/freigabe-status")
    fun freigabeStatus(@RequestParam("ids") ids: List<Long>): ResponseEntity<Map<Long, FreigabeStatusKurzDto>> {
        val byDokumentId = dokumentFreigabeService.findJuengsteProQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, ids)
        val result = HashMap<Long, FreigabeStatusKurzDto>()
        byDokumentId.forEach { (dokumentId, freigabe) ->
            result[dokumentId] = FreigabeStatusKurzDto.builder()
                .status(freigabe.status!!.name)
                .dokumentArt(freigabe.dokumentArt)
                .dokumentNummer(freigabe.dokumentNummer)
                .akzeptiertAm(freigabe.akzeptiertAm)
                .ablaufDatum(freigabe.ablaufDatum)
                .erstelltAm(freigabe.erstelltAm)
                .build()
        }
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{id}/freigabe-audit")
    fun freigabeAudit(@PathVariable id: Long): ResponseEntity<FreigabeAuditDto> =
        dokumentFreigabeService.findAuditByQuelle(FreigabeQuellTyp.AUSGANGS_DOKUMENT, id)
            .map { ResponseEntity.ok(it) }
            .orElseGet { ResponseEntity.notFound().build() }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<AusgangsGeschaeftsDokumentResponseDto> {
        val dto = service.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(dto)
    }

    @GetMapping("/projekt/{projektId}")
    fun getByProjekt(@PathVariable projektId: Long): ResponseEntity<List<AusgangsGeschaeftsDokumentResponseDto>> =
        ResponseEntity.ok(service.findByProjekt(projektId))

    @GetMapping("/anfrage/{anfrageId}")
    fun getByAnfrage(@PathVariable anfrageId: Long): ResponseEntity<List<AusgangsGeschaeftsDokumentResponseDto>> =
        ResponseEntity.ok(service.findByAnfrage(anfrageId))

    @GetMapping("/projekt/{projektId}/geerbte-rechnungsadresse")
    fun getGeerbteRechnungsadresseByProjekt(@PathVariable projektId: Long): ResponseEntity<Map<String, String?>> =
        ResponseEntity.ok(mapOf("rechnungsadresseOverride" to service.findGeerbteRechnungsadresse(projektId, null)))

    @GetMapping("/anfrage/{anfrageId}/geerbte-rechnungsadresse")
    fun getGeerbteRechnungsadresseByAnfrage(@PathVariable anfrageId: Long): ResponseEntity<Map<String, String?>> =
        ResponseEntity.ok(mapOf("rechnungsadresseOverride" to service.findGeerbteRechnungsadresseFuerAnfrage(anfrageId)))

    @PostMapping
    fun create(
        @RequestBody dto: AusgangsGeschaeftsDokumentErstellenDto,
        authentication: Authentication?,
        request: HttpServletRequest?,
    ): ResponseEntity<Any> {
        val created = try {
            service.erstellen(dto, clientIp(request))
        } catch (e: RuntimeException) {
            return ResponseEntity.badRequest().body(e.message)
        }
        val principal = principal(authentication)
        if (principal != null) {
            try {
                val lockResult = dokumentLockService.acquire(
                    DokumentLockService.TYP_AUSGANG,
                    created.id!!,
                    principal.id!!,
                    principal.displayName,
                )
                if (DokumentLockDto.ACQUIRED != lockResult.status()) {
                    log.warn("Lock-Vergabe nach Create fuer Dokument {} unerwartet: {}", created.id, lockResult.status())
                }
            } catch (lockEx: RuntimeException) {
                log.warn("Lock-Vergabe nach Create fuer Dokument {} fehlgeschlagen: {}", created.id, lockEx.message)
            }
        }
        return ResponseEntity.ok(service.findById(created.id!!))
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody dto: AusgangsGeschaeftsDokumentUpdateDto,
        authentication: Authentication?,
    ): ResponseEntity<Any> {
        val principal = principal(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!dokumentLockService.isHeldBy(DokumentLockService.TYP_AUSGANG, id, principal.id!!)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Dokument wird gerade von einem anderen Benutzer bearbeitet.")
        }
        return try {
            val updated = service.aktualisieren(id, dto)
            ResponseEntity.ok(service.findById(updated.id!!))
        } catch (e: RuntimeException) {
            log.error("Fehler beim Aktualisieren von Dokument {}: {}", id, e.message, e)
            ResponseEntity.badRequest().body(e.message)
        }
    }

    @PostMapping("/{id}/buchen")
    fun buchen(
        @PathVariable id: Long,
        @RequestParam(required = false) userId: Long?,
        request: HttpServletRequest?,
    ): ResponseEntity<Any> =
        try {
            val result = service.buchen(id, userId, clientIp(request))
            ResponseEntity.ok(service.findById(result.id!!))
        } catch (e: RuntimeException) {
            ResponseEntity.badRequest().body(e.message)
        }

    @PostMapping("/{id}/email-versendet")
    fun emailVersendet(
        @PathVariable id: Long,
        @RequestParam(required = false) userId: Long?,
        request: HttpServletRequest?,
    ): ResponseEntity<Any> =
        try {
            service.buchenNachEmailVersand(id, userId, clientIp(request))
            ResponseEntity.ok(service.findById(id))
        } catch (e: RuntimeException) {
            ResponseEntity.badRequest().body(e.message)
        }

    @PostMapping("/{id}/pdf-speichern")
    fun pdfSpeichern(@PathVariable id: Long, @RequestBody pdfBytes: ByteArray?): ResponseEntity<Any> =
        try {
            if (pdfBytes == null || pdfBytes.isEmpty()) {
                return ResponseEntity.badRequest().body("Keine PDF-Daten erhalten")
            }
            val dateiname = service.speicherePdfFuerDokument(id, pdfBytes)
            ResponseEntity.ok(mapOf("dateiname" to dateiname))
        } catch (e: RuntimeException) {
            log.error("Fehler beim Speichern der PDF fuer Dokument {}: {}", id, e.message, e)
            ResponseEntity.badRequest().body(e.message)
        }

    @GetMapping("/{id}/pdf")
    fun getPdf(@PathVariable id: Long): ResponseEntity<Any> =
        ResponseEntity.status(404).body("PDF nicht verfuegbar. Bitte exportieren Sie das Dokument erneut.")

    @PostMapping("/{id}/storno")
    fun stornieren(
        @PathVariable id: Long,
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) grund: String?,
        request: HttpServletRequest?,
    ): ResponseEntity<Any> =
        try {
            val storno = service.stornieren(id, userId, clientIp(request), grund)
            ResponseEntity.ok(service.findById(storno.id!!))
        } catch (e: RuntimeException) {
            log.error("Fehler beim Stornieren von Dokument {}: {}", id, e.message, e)
            ResponseEntity.badRequest().body(e.message)
        }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: Long,
        @RequestParam begruendung: String,
        @RequestParam(required = false) userId: Long?,
        request: HttpServletRequest?,
    ): ResponseEntity<Any> =
        try {
            service.findById(id) ?: return ResponseEntity.notFound().build()
            service.loeschen(id, begruendung, userId, clientIp(request))
            ResponseEntity.ok().build()
        } catch (e: RuntimeException) {
            ResponseEntity.badRequest().body(e.message)
        }

    @GetMapping("/{id}/historie")
    fun historie(@PathVariable id: Long): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(auditService.getHistorie(id))

    @GetMapping("/historie/nummer/{dokumentNummer}")
    fun historieByNummer(@PathVariable dokumentNummer: String): ResponseEntity<List<Map<String, Any?>>> =
        ResponseEntity.ok(auditService.getHistorieByNummer(dokumentNummer))

    @GetMapping("/{id}/abrechnungsverlauf")
    fun getAbrechnungsverlauf(@PathVariable id: Long): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(service.getAbrechnungsverlauf(id))
        } catch (e: RuntimeException) {
            ResponseEntity.badRequest().body(e.message)
        }

    private fun principal(authentication: Authentication?): FrontendUserPrincipal? {
        if (authentication?.principal == null) {
            return null
        }
        return authentication.principal as? FrontendUserPrincipal
    }

    private fun clientIp(request: HttpServletRequest?): String? {
        if (request == null) return null
        val xff = request.getHeader("X-Forwarded-For")
        if (!xff.isNullOrBlank()) {
            val comma = xff.indexOf(',')
            return (if (comma > 0) xff.substring(0, comma) else xff).trim()
        }
        return request.remoteAddr
    }

    private companion object {
        private val log = LoggerFactory.getLogger(AusgangsGeschaeftsDokumentController::class.java)
    }
}
