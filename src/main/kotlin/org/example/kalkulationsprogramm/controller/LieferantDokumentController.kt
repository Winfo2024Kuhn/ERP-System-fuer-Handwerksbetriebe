package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.LieferantDokument
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto
import org.example.kalkulationsprogramm.repository.EmailAttachmentRepository
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository
import org.example.kalkulationsprogramm.service.DokumentLockService
import org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService
import org.example.kalkulationsprogramm.service.LieferantDokumentService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/lieferant-dokumente")
class LieferantDokumentController(
    private val dokumentRepository: LieferantDokumentRepository,
    private val geschaeftsdokumentRepository: LieferantGeschaeftsdokumentRepository,
    private val dokumentService: LieferantDokumentService,
    private val analyseService: GeminiDokumentAnalyseService,
    private val emailRepository: EmailRepository,
    private val emailAttachmentProcessingService: EmailAttachmentProcessingService,
    private val emailAttachmentRepository: EmailAttachmentRepository,
    private val dokumentLockService: DokumentLockService,
) {
    @Value("\${file.upload-dir}")
    private lateinit var uploadDir: String

    @Value("\${file.mail-attachment-dir}")
    private lateinit var mailAttachmentDir: String

    private fun principal(authentication: Authentication?): FrontendUserPrincipal? =
        authentication?.principal as? FrontendUserPrincipal

    @PutMapping("/{dokumentId}")
    @Transactional
    fun updateDokument(
        @PathVariable dokumentId: Long,
        @RequestBody request: UpdateDokumentRequest,
        authentication: Authentication?,
    ): ResponseEntity<LieferantDokumentDto.Response> {
        val principal = principal(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        val userId = principal.id ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!dokumentLockService.isHeldBy(DokumentLockService.TYP_EINGANG, dokumentId, userId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build()
        }
        val dokument = dokumentRepository.findById(dokumentId).orElse(null) ?: return ResponseEntity.notFound().build()
        request.typ?.let { runCatching { dokument.typ = LieferantDokumentTyp.valueOf(it) } }
        val gd = dokument.geschaeftsdaten ?: LieferantGeschaeftsdokument().also {
            it.dokument = dokument
            it.analysiertAm = LocalDateTime.now()
            dokument.geschaeftsdaten = it
        }
        request.geschaeftsdaten?.let { applyGeschaeftsdaten(gd, it) }
        geschaeftsdokumentRepository.save(gd)
        dokumentRepository.save(dokument)
        return ResponseEntity.ok(dokumentService.getDokumentById(dokumentId))
    }

    data class UpdateDokumentRequest(
        var typ: String? = null,
        var geschaeftsdaten: GeschaeftsdatenRequest? = null,
    )

    data class GeschaeftsdatenRequest(
        var dokumentNummer: String? = null,
        var dokumentDatum: String? = null,
        var liefertermin: String? = null,
        var betragNetto: Double? = null,
        var betragBrutto: Double? = null,
        var mwstSatz: Double? = null,
        var referenzNummer: String? = null,
        var bestellnummer: String? = null,
        var zahlungsart: String? = null,
        var zahlungsziel: String? = null,
        var skontoTage: Int? = null,
        var skontoProzent: Double? = null,
        var nettoTage: Int? = null,
        var bezahlt: Boolean? = null,
        var bezahltAm: String? = null,
        var tatsaechlichGezahlt: Double? = null,
        var mitSkonto: Boolean? = null,
    )

    private fun applyGeschaeftsdaten(gd: LieferantGeschaeftsdokument, req: GeschaeftsdatenRequest) {
        req.dokumentNummer?.let { gd.dokumentNummer = it }
        req.dokumentDatum?.takeIf { it.isNotEmpty() }?.let { gd.dokumentDatum = LocalDate.parse(it) }
        req.liefertermin?.takeIf { it.isNotEmpty() }?.let { gd.liefertermin = LocalDate.parse(it) }
        req.betragNetto?.let { gd.betragNetto = BigDecimal.valueOf(it) }
        req.betragBrutto?.let { gd.betragBrutto = BigDecimal.valueOf(it) }
        req.mwstSatz?.let { gd.mwstSatz = BigDecimal.valueOf(it) }
        req.referenzNummer?.let { gd.referenzNummer = it }
        req.bestellnummer?.let { gd.bestellnummer = it }
        req.zahlungsart?.let { gd.zahlungsart = it }
        req.zahlungsziel?.takeIf { it.isNotEmpty() }?.let { gd.zahlungsziel = LocalDate.parse(it) }
        req.skontoTage?.let { gd.skontoTage = it }
        req.skontoProzent?.let { gd.skontoProzent = BigDecimal.valueOf(it) }
        req.nettoTage?.let { gd.nettoTage = it }
        req.bezahlt?.let {
            gd.bezahlt = it
            if (it && gd.bezahltAm == null) gd.bezahltAm = LocalDate.now()
        }
        req.bezahltAm?.takeIf { it.isNotEmpty() }?.let { gd.bezahltAm = LocalDate.parse(it) }
        req.tatsaechlichGezahlt?.let { gd.tatsaechlichGezahlt = BigDecimal.valueOf(it) }
        req.mitSkonto?.let { gd.mitSkonto = it }
    }

    @PostMapping("/lieferant/{lieferantId}/reanalyze")
    fun reanalyzeByLieferant(@PathVariable lieferantId: Long): ResponseEntity<Map<String, Any>> {
        log.info("Starte Re-Analyse aller Dokumente fuer Lieferant {}", lieferantId)
        val dokumentIds = dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(lieferantId).mapNotNull { it.id }
        var erfolgreich = 0
        var fehlgeschlagen = 0
        dokumentIds.forEachIndexed { index, dokumentId ->
            try {
                log.info("Re-Analyse Dokument {}/{} (ID: {}) fuer Lieferant {}", index + 1, dokumentIds.size, dokumentId, lieferantId)
                val result = analyseService.reanalysiereDokumentById(dokumentId)
                if (result != null) {
                    erfolgreich++
                    log.info("Re-Analyse erfolgreich fuer Dokument {}: {}", dokumentId, result.dokumentNummer)
                } else {
                    fehlgeschlagen++
                    log.warn("Re-Analyse ohne Ergebnis fuer Dokument {}", dokumentId)
                }
            } catch (e: Exception) {
                fehlgeschlagen++
                log.error("Re-Analyse fehlgeschlagen fuer Dokument {}: {}", dokumentId, e.message)
            }
        }
        return ResponseEntity.ok(mapOf("lieferantId" to lieferantId, "gesamt" to dokumentIds.size, "erfolgreich" to erfolgreich, "fehlgeschlagen" to fehlgeschlagen))
    }

    @GetMapping("/{dokumentId}")
    @Transactional(readOnly = true)
    fun getDokument(@PathVariable dokumentId: Long): ResponseEntity<LieferantDokumentDto.Response> =
        dokumentService.getDokumentById(dokumentId)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @PostMapping("/{dokumentId}/reanalyze")
    fun reanalyzeDokument(@PathVariable dokumentId: Long): ResponseEntity<LieferantDokumentDto.Response> {
        log.info("Starte Re-Analyse fuer Dokument {}", dokumentId)
        val dokument = dokumentRepository.findById(dokumentId).orElse(null) ?: return ResponseEntity.notFound().build()
        return try {
            val result = analyseService.analysiereDokument(dokument)
            if (result != null) log.info("Re-Analyse erfolgreich fuer Dokument {}: {}", dokumentId, result.dokumentNummer)
            else log.warn("Re-Analyse ohne Ergebnis fuer Dokument {}", dokumentId)
            ResponseEntity.ok(dokumentService.getDokumentById(dokumentId))
        } catch (e: Exception) {
            log.error("Re-Analyse fehlgeschlagen fuer Dokument {}: {}", dokumentId, e.message)
            ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping("/relink-all")
    fun relinkAlleDokumente(): ResponseEntity<Map<String, Any?>> =
        try {
            val verknuepft = analyseService.relinkAlleDokumente()
            ResponseEntity.ok(mapOf("message" to "Neuverknuepfung abgeschlossen", "neuVerknuepft" to verknuepft))
        } catch (e: Exception) {
            log.error("Neuverknuepfung fehlgeschlagen: {}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }

    @PostMapping("/lieferant/{lieferantId}/relink")
    fun relinkByLieferant(@PathVariable lieferantId: Long): ResponseEntity<Map<String, Any?>> =
        try {
            val verknuepft = analyseService.relinkDokumenteByLieferant(lieferantId)
            ResponseEntity.ok(mapOf("lieferantId" to lieferantId, "message" to "Neuverknuepfung abgeschlossen", "neuVerknuepft" to verknuepft))
        } catch (e: Exception) {
            log.error("Neuverknuepfung fehlgeschlagen fuer Lieferant {}: {}", lieferantId, e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message))
        }

    @PostMapping("/process-assigned-emails")
    fun processAssignedEmailAttachments(): ResponseEntity<Map<String, Any>> {
        val lieferantEmails = emailRepository.findLieferantEmails()
        var emailsVerarbeitet = 0
        var dokumenteErstellt = 0
        var emailsUebersprungen = 0
        var fehler = 0
        lieferantEmails.forEach { email ->
            try {
                if (email.attachments.isNullOrEmpty()) {
                    emailsUebersprungen++
                    return@forEach
                }
                val created = emailAttachmentProcessingService.processLieferantAttachments(email)
                if (created > 0) {
                    dokumenteErstellt += created
                    emailsVerarbeitet++
                } else {
                    emailsUebersprungen++
                }
            } catch (e: Exception) {
                fehler++
                log.error("Fehler bei Verarbeitung von E-Mail {}: {}", email.id, e.message)
            }
        }
        return ResponseEntity.ok(mapOf("emailsGesamt" to lieferantEmails.size, "emailsVerarbeitet" to emailsVerarbeitet, "dokumenteErstellt" to dokumenteErstellt, "emailsUebersprungen" to emailsUebersprungen, "fehler" to fehler))
    }

    @PostMapping("/process-email/{emailId}")
    fun processEmailAttachments(@PathVariable emailId: Long): ResponseEntity<Map<String, Any?>> {
        val email = emailRepository.findById(emailId).orElse(null) ?: return ResponseEntity.notFound().build()
        val lieferant = email.lieferant ?: return ResponseEntity.badRequest().body(mapOf("error" to "E-Mail ist keinem Lieferanten zugeordnet", "emailId" to emailId))
        return try {
            val dokumenteErstellt = emailAttachmentProcessingService.processLieferantAttachments(email)
            ResponseEntity.ok(mapOf("emailId" to emailId, "lieferantId" to lieferant.id, "lieferantName" to lieferant.lieferantenname, "dokumenteErstellt" to dokumenteErstellt))
        } catch (e: Exception) {
            log.error("Fehler bei Verarbeitung von E-Mail {}: {}", emailId, e.message)
            ResponseEntity.internalServerError().body(mapOf("error" to e.message, "emailId" to emailId))
        }
    }

    @PostMapping("/lieferant/{lieferantId}/process-emails")
    fun processLieferantEmailAttachments(@PathVariable lieferantId: Long): ResponseEntity<Map<String, Any?>> {
        val lieferantEmails = emailRepository.findByLieferantIdOrderBySentAtDesc(lieferantId)
        if (lieferantEmails.isEmpty()) {
            return ResponseEntity.ok(mapOf("lieferantId" to lieferantId, "message" to "Keine E-Mails fuer diesen Lieferanten gefunden", "emailsGesamt" to 0, "dokumenteErstellt" to 0))
        }
        val lieferantName = lieferantEmails.firstOrNull()?.lieferant?.lieferantenname ?: "Unbekannt"
        var emailsVerarbeitet = 0
        var dokumenteErstellt = 0
        var emailsUebersprungen = 0
        var fehler = 0
        lieferantEmails.forEach { email ->
            try {
                if (email.attachments.isNullOrEmpty()) {
                    emailsUebersprungen++
                    return@forEach
                }
                val created = emailAttachmentProcessingService.processLieferantAttachments(email)
                if (created > 0) {
                    dokumenteErstellt += created
                    emailsVerarbeitet++
                } else {
                    emailsUebersprungen++
                }
            } catch (e: Exception) {
                fehler++
                log.error("Fehler bei Verarbeitung von E-Mail {}: {}", email.id, e.message)
            }
        }
        return ResponseEntity.ok(mapOf("lieferantId" to lieferantId, "lieferantName" to lieferantName, "emailsGesamt" to lieferantEmails.size, "emailsVerarbeitet" to emailsVerarbeitet, "dokumenteErstellt" to dokumenteErstellt, "emailsUebersprungen" to emailsUebersprungen, "fehler" to fehler))
    }

    @GetMapping("/duplicates")
    @Transactional(readOnly = true)
    fun findDuplicates(): ResponseEntity<Map<String, Any>> {
        val duplicates = geschaeftsdokumentRepository.findAllDuplicates()
        if (duplicates.isEmpty()) return ResponseEntity.ok(mapOf("message" to "Keine Duplikate gefunden", "duplikateGesamt" to 0, "gruppen" to emptyList<Any>()))
        val gruppen = linkedMapOf<String, MutableList<Map<String, Any?>>>()
        duplicates.forEach { row ->
            val id = (row[0] as Number).toLong()
            val dokumentNummer = row[1] as String
            val lieferantId = (row[2] as Number).toLong()
            val lieferantName = row[3] as String
            gruppen.computeIfAbsent("${lieferantId}_$dokumentNummer") { mutableListOf() }
                .add(mapOf("id" to id, "dokumentNummer" to dokumentNummer, "lieferantId" to lieferantId, "lieferantName" to lieferantName))
        }
        var zuLoeschenGesamt = 0
        val gruppenListe = gruppen.values.map { items ->
            val loescheIds = items.drop(1).map { it["id"] as Long }
            zuLoeschenGesamt += loescheIds.size
            linkedMapOf("dokumentNummer" to items[0]["dokumentNummer"], "lieferantName" to items[0]["lieferantName"], "behalteId" to items[0]["id"], "loescheIds" to loescheIds, "anzahlDuplikate" to items.size)
        }
        return ResponseEntity.ok(mapOf("message" to "$zuLoeschenGesamt Duplikate in ${gruppenListe.size} Gruppen gefunden", "duplikateGesamt" to zuLoeschenGesamt, "gruppen" to gruppenListe))
    }

    @DeleteMapping("/duplicates")
    @Transactional
    fun deleteDuplicates(): ResponseEntity<Map<String, Any>> {
        val duplicates = geschaeftsdokumentRepository.findAllDuplicates()
        if (duplicates.isEmpty()) return ResponseEntity.ok(mapOf("message" to "Keine Duplikate gefunden", "geloescht" to 0))
        val gruppen = linkedMapOf<String, MutableList<Long>>()
        duplicates.forEach { row ->
            val id = (row[0] as Number).toLong()
            val dokumentNummer = row[1] as String
            val lieferantId = (row[2] as Number).toLong()
            gruppen.computeIfAbsent("${lieferantId}_$dokumentNummer") { mutableListOf() }.add(id)
        }
        var geloescht = 0
        var fehler = 0
        gruppen.forEach { (_, ids) ->
            ids.drop(1).forEach { idToDelete ->
                try {
                    val dokument = dokumentRepository.findById(idToDelete).orElse(null) ?: return@forEach
                    emailAttachmentRepository.findByLieferantDokumentId(idToDelete).forEach { att ->
                        att.lieferantDokument = null
                        att.aiProcessed = false
                        emailAttachmentRepository.save(att)
                    }
                    dokument.verknuepfteDokumente.clear()
                    dokument.verknuepftVon.forEach { it.verknuepfteDokumente.remove(dokument) }
                    dokument.projektAnteile.clear()
                    dokument.attachment = null
                    dokumentRepository.saveAndFlush(dokument)
                    dokumentRepository.delete(dokument)
                    geloescht++
                } catch (e: Exception) {
                    fehler++
                    log.error("Fehler beim Loeschen von Duplikat {}: {}", idToDelete, e.message)
                }
            }
        }
        return ResponseEntity.ok(mapOf("message" to "$geloescht Duplikate geloescht", "geloescht" to geloescht, "fehler" to fehler, "gruppenBereinigt" to gruppen.size))
    }

    @GetMapping("/{dokumentId}/download")
    fun downloadDokument(@PathVariable dokumentId: Long): ResponseEntity<ByteArray> {
        val dokument = dokumentRepository.findById(dokumentId).orElse(null) ?: return ResponseEntity.notFound().build()
        val filePath = resolveDokumentPath(dokument)
        if (filePath == null || !Files.exists(filePath)) {
            log.warn("Datei nicht gefunden fuer Dokument {}", dokumentId)
            return ResponseEntity.notFound().build()
        }
        return try {
            val bytes = Files.readAllBytes(filePath)
            val filename = dokument.getEffektiverDateiname()
            val contentType = Files.probeContentType(filePath) ?: "application/octet-stream"
            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"$filename\"")
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(bytes)
        } catch (e: IOException) {
            log.error("Fehler beim Lesen der Datei fuer Dokument {}: {}", dokumentId, e.message)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    private fun resolveDokumentPath(dokument: LieferantDokument): Path? {
        val dateiname = dokument.getEffektiverGespeicherterDateiname() ?: dokument.attachment?.storedFilename ?: return null
        val lieferantId = dokument.lieferant?.id
        val uploadBase = Path.of(uploadDir).toAbsolutePath().normalize()
        val direct = uploadBase.resolve(dateiname).normalize()
        if (direct.startsWith(uploadBase) && Files.exists(direct)) return direct
        if (lieferantId != null) {
            val candidates = listOf(
                Path.of(mailAttachmentDir).resolve("email").resolve(lieferantId.toString()).resolve(dateiname),
                Path.of(uploadDir).resolve("lieferanten").resolve(lieferantId.toString()).resolve(dateiname),
                Path.of(uploadDir).resolve("attachments").resolve("lieferanten").resolve(lieferantId.toString()).resolve(dateiname),
            )
            candidates.firstOrNull { Files.exists(it) }?.let { return it }
        }
        return listOf(
            Path.of(uploadDir).resolve("attachments").resolve("vendor-invoices").resolve(dateiname),
            Path.of(mailAttachmentDir).resolve(dateiname),
        ).firstOrNull { Files.exists(it) }
    }

    companion object {
        private val log = LoggerFactory.getLogger(LieferantDokumentController::class.java)
    }
}
