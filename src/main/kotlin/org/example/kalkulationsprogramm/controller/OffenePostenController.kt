package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal
import org.example.kalkulationsprogramm.domain.Abteilung
import org.example.kalkulationsprogramm.domain.DokumentGruppe
import org.example.kalkulationsprogramm.domain.FrontendUserProfile
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.example.kalkulationsprogramm.service.DateiSpeicherService
import org.example.kalkulationsprogramm.service.FrontendUserProfileService
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@RestController
@RequestMapping("/api/offene-posten")
class OffenePostenController(
    private val geschaeftsdokumentRepository: LieferantGeschaeftsdokumentRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val frontendUserProfileService: FrontendUserProfileService,
    private val geminiDokumentAnalyseService: GeminiDokumentAnalyseService,
    private val projektRepository: ProjektRepository,
    private val projektDokumentRepository: ProjektDokumentRepository,
    private val dateiSpeicherService: DateiSpeicherService,
) {
    @Value("\${file.upload-dir}")
    private lateinit var uploadDir: String

    @GetMapping("/eingang")
    fun getOffeneEingangsrechnungen(
        @RequestHeader(value = "X-Auth-Token", required = false) token: String?,
        authentication: Authentication?,
    ): ResponseEntity<List<EingangsrechnungDto>> {
        val mitarbeiter = resolveMitarbeiter(token, authentication)
        val darfGenehmigen = hatBerechtigung(mitarbeiter) { it.darfRechnungenGenehmigen }
        val darfSehen = hatBerechtigung(mitarbeiter) { it.darfRechnungenSehen }
        val rechnungen = when {
            darfGenehmigen -> geschaeftsdokumentRepository.findAllOffeneEingangsrechnungen()
            darfSehen -> geschaeftsdokumentRepository.findAllOffeneGenehmigte()
            else -> emptyList()
        }
        return ResponseEntity.ok(rechnungen.map { toDto(it, darfGenehmigen) })
    }

    @GetMapping("/eingang/alle")
    fun getAlleEingangsrechnungen(
        @RequestHeader(value = "X-Auth-Token", required = false) token: String?,
        authentication: Authentication?,
    ): ResponseEntity<List<EingangsrechnungDto>> {
        val mitarbeiter = resolveMitarbeiter(token, authentication)
        val darfGenehmigen = hatBerechtigung(mitarbeiter) { it.darfRechnungenGenehmigen }
        val darfSehen = hatBerechtigung(mitarbeiter) { it.darfRechnungenSehen }
        val rechnungen = when {
            darfGenehmigen -> geschaeftsdokumentRepository.findAllEingangsrechnungen()
            darfSehen -> geschaeftsdokumentRepository.findAllGenehmigte()
            else -> emptyList()
        }
        return ResponseEntity.ok(rechnungen.map { toDto(it, darfGenehmigen) })
    }

    @PutMapping("/eingang/{id}/bezahlt")
    @Transactional
    fun setBezahltStatus(
        @PathVariable id: Long,
        @RequestBody body: Map<String, Boolean>,
        @RequestHeader(value = "X-Auth-Token", required = false) token: String?,
        authentication: Authentication?,
    ): ResponseEntity<EingangsrechnungDto> {
        val gd = geschaeftsdokumentRepository.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        val bezahlt = body.getOrDefault("bezahlt", false)
        gd.bezahlt = bezahlt
        if (bezahlt) {
            gd.bezahltAm = LocalDate.now()
            val skontoTage = gd.skontoTage
            val skontoProzent = gd.skontoProzent
            val dokumentDatum = gd.dokumentDatum
            val betragBrutto = gd.betragBrutto
            if (skontoTage != null && skontoProzent != null && dokumentDatum != null && betragBrutto != null) {
                val skontoFrist = dokumentDatum.plusDays(skontoTage.toLong())
                if (!LocalDate.now().isAfter(skontoFrist)) {
                    val skontoBetrag = betragBrutto
                        .multiply(skontoProzent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    gd.tatsaechlichGezahlt = betragBrutto.subtract(skontoBetrag)
                    gd.mitSkonto = true
                } else {
                    gd.tatsaechlichGezahlt = betragBrutto
                    gd.mitSkonto = false
                }
            } else {
                gd.tatsaechlichGezahlt = gd.betragBrutto
                gd.mitSkonto = false
            }
        } else {
            gd.bezahltAm = null
            gd.tatsaechlichGezahlt = null
            gd.mitSkonto = false
        }
        geschaeftsdokumentRepository.save(gd)
        val darfGenehmigen = hatBerechtigung(resolveMitarbeiter(token, authentication)) { it.darfRechnungenGenehmigen }
        return ResponseEntity.ok(toDto(gd, darfGenehmigen))
    }

    @PatchMapping("/eingang/{id}/genehmigen")
    @Transactional
    fun setGenehmigtStatus(
        @PathVariable id: Long,
        @RequestBody body: Map<String, Boolean>,
        @RequestHeader(value = "X-Auth-Token", required = false) token: String?,
        authentication: Authentication?,
    ): ResponseEntity<EingangsrechnungDto> {
        val mitarbeiter = resolveMitarbeiter(token, authentication)
        val darfGenehmigen = hatBerechtigung(mitarbeiter) { it.darfRechnungenGenehmigen }
        if (!darfGenehmigen) return ResponseEntity.status(403).build()
        val gd = geschaeftsdokumentRepository.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        gd.genehmigt = body.getOrDefault("genehmigt", false)
        return ResponseEntity.ok(toDto(geschaeftsdokumentRepository.save(gd), darfGenehmigen))
    }

    @PostMapping("/ausgang/analyze")
    fun analyzeAusgangsrechnung(@RequestParam("datei") datei: MultipartFile): ResponseEntity<Any> {
        if (datei.isEmpty) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Keine Datei hochgeladen"))
        }
        return try {
            val originalFilename = StringUtils.cleanPath(datei.originalFilename ?: "upload.pdf")
            if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
                return ResponseEntity.badRequest().body(mapOf("error" to "Ungültiger Dateiname"))
            }
            val tempDir = Files.createTempDirectory("ausgang-analyze-")
            val tempFile = tempDir.resolve(originalFilename)
            datei.transferTo(tempFile.toFile())
            try {
                val result: LieferantDokumentDto.AnalyzeResponse? =
                    geminiDokumentAnalyseService.analyzeFile(tempFile, originalFilename)
                ResponseEntity.ok(result ?: mapOf("error" to "Analyse fehlgeschlagen - bitte manuell ausfüllen"))
            } finally {
                Files.deleteIfExists(tempFile)
                Files.deleteIfExists(tempDir)
            }
        } catch (e: Exception) {
            log.error("Fehler bei Ausgangsrechnung-Analyse: {}", e.message, e)
            ResponseEntity.ok(mapOf("error" to "Analyse fehlgeschlagen: ${e.message}"))
        }
    }

    @PostMapping("/ausgang/import")
    @Transactional
    fun importAusgangsrechnung(
        @RequestParam("datei") datei: MultipartFile,
        @RequestParam("projektId") projektId: Long,
        @RequestParam("rechnungsnummer") rechnungsnummer: String,
        @RequestParam(value = "rechnungsdatum", required = false) rechnungsdatumStr: String?,
        @RequestParam(value = "faelligkeitsdatum", required = false) faelligkeitsdatumStr: String?,
        @RequestParam(value = "betragBrutto", required = false) betragBruttoStr: String?,
        @RequestParam(value = "geschaeftsdokumentart", required = false, defaultValue = "Rechnung") geschaeftsdokumentart: String,
    ): ResponseEntity<Any> {
        if (datei.isEmpty) return ResponseEntity.badRequest().body(mapOf("error" to "Keine Datei hochgeladen"))
        if (!StringUtils.hasText(rechnungsnummer)) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Rechnungsnummer ist erforderlich"))
        }
        val projekt = projektRepository.findById(projektId).orElse(null)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Projekt nicht gefunden"))
        if (projektDokumentRepository.existsByDokumentid(rechnungsnummer)) {
            return ResponseEntity.status(409).body(mapOf("error" to "Rechnungsnummer '$rechnungsnummer' existiert bereits"))
        }

        return try {
            val saved = dateiSpeicherService.speichereDatei(datei, projektId, DokumentGruppe.GESCHAEFTSDOKUMENTE)
            val geschaeftsdokument = if (saved is ProjektGeschaeftsdokument) {
                saved
            } else {
                val gespeicherterDateiname = saved.gespeicherterDateiname
                val originalDateiname = saved.originalDateiname
                val dateityp = saved.dateityp
                val dateigroesse = saved.dateigroesse
                projektDokumentRepository.delete(saved)
                projektDokumentRepository.flush()
                ProjektGeschaeftsdokument().apply {
                    this.projekt = projekt
                    this.originalDateiname = originalDateiname
                    this.gespeicherterDateiname = gespeicherterDateiname
                    this.dateityp = dateityp
                    this.dateigroesse = dateigroesse
                    this.uploadDatum = LocalDate.now()
                    this.dokumentGruppe = DokumentGruppe.GESCHAEFTSDOKUMENTE
                }
            }

            geschaeftsdokument.dokumentid = rechnungsnummer
            geschaeftsdokument.geschaeftsdokumentart = geschaeftsdokumentart
            geschaeftsdokument.bezahlt = false
            if (StringUtils.hasText(rechnungsdatumStr)) geschaeftsdokument.rechnungsdatum = LocalDate.parse(rechnungsdatumStr)
            if (StringUtils.hasText(faelligkeitsdatumStr)) geschaeftsdokument.faelligkeitsdatum = LocalDate.parse(faelligkeitsdatumStr)
            if (StringUtils.hasText(betragBruttoStr)) geschaeftsdokument.bruttoBetrag = BigDecimal(betragBruttoStr)

            val result = projektDokumentRepository.save(geschaeftsdokument)
            log.info("Manuelle Ausgangsrechnung importiert: {} für Projekt {} (ID: {})", rechnungsnummer, projekt.bauvorhaben, result.id)
            ResponseEntity.ok(mapOf("id" to result.id, "rechnungsnummer" to rechnungsnummer, "projektId" to projektId))
        } catch (e: Exception) {
            log.error("Fehler beim Import der Ausgangsrechnung: {}", e.message, e)
            ResponseEntity.internalServerError().body(mapOf("error" to "Import fehlgeschlagen: ${e.message}"))
        }
    }

    private fun resolveMitarbeiter(token: String?, authentication: Authentication?): Mitarbeiter? {
        if (StringUtils.hasText(token)) {
            val mitarbeiter = mitarbeiterRepository.findByLoginToken(token!!).orElse(null)
            if (mitarbeiter != null) return mitarbeiter
        }
        val principal = authentication?.principal
        if (principal is FrontendUserPrincipal) {
            return frontendUserProfileService.findById(principal.id)
                .map(FrontendUserProfile::mitarbeiter)
                .orElse(null)
        }
        return null
    }

    private fun hatBerechtigung(mitarbeiter: Mitarbeiter?, flagGetter: (Abteilung) -> Boolean?): Boolean {
        if (mitarbeiter?.abteilungen == null) return false
        return mitarbeiter.abteilungen.any { flagGetter(it) == true }
    }

    private fun toDto(gd: LieferantGeschaeftsdokument, darfGenehmigen: Boolean): EingangsrechnungDto {
        val dto = EingangsrechnungDto()
        dto.id = gd.id
        dto.dokumentNummer = gd.dokumentNummer
        dto.dokumentDatum = gd.dokumentDatum
        dto.zahlungsziel = gd.zahlungsziel
        dto.betragNetto = gd.betragNetto?.toDouble()
        dto.betragBrutto = gd.betragBrutto?.toDouble()
        dto.bezahlt = gd.bezahlt == true
        dto.bezahltAm = gd.bezahltAm
        dto.bereitsGezahlt = gd.bereitsGezahlt == true
        dto.zahlungsart = gd.zahlungsart
        dto.genehmigt = gd.genehmigt == true
        dto.darfGenehmigen = darfGenehmigen
        dto.skontoTage = gd.skontoTage
        dto.skontoProzent = gd.skontoProzent?.toDouble()
        dto.nettoTage = gd.nettoTage
        dto.tatsaechlichGezahlt = gd.tatsaechlichGezahlt?.toDouble()
        dto.mitSkonto = gd.mitSkonto == true

        val skontoTage = gd.skontoTage
        val dokumentDatum = gd.dokumentDatum
        if (skontoTage != null && dokumentDatum != null && !dto.bezahlt) {
            dto.skontoFrist = dokumentDatum.plusDays(skontoTage.toLong())
            val verbleibend = ChronoUnit.DAYS.between(LocalDate.now(), dto.skontoFrist)
            dto.skontoVerbleibendeTage = verbleibend.toInt()
            dto.skontoAbgelaufen = verbleibend < 0
        }

        val dokument = gd.dokument
        if (dokument != null) {
            dto.dokumentId = dokument.id
            dto.dateiname = dokument.getEffektiverDateiname()
            val lieferant = dokument.lieferant
            if (lieferant != null) {
                dto.lieferantId = lieferant.id
                dto.lieferantName = lieferant.lieferantenname
            }
            val att = dokument.attachment
            if (att != null) {
                val email = att.email
                if (email != null) {
                    dto.pdfUrl = "/api/emails/${email.id}/attachments/${att.id}"
                }
            } else if (dokument.gespeicherterDateiname != null) {
                dto.pdfUrl = "/api/lieferanten/${dto.lieferantId}/dokumente/${dokument.id}/download"
            }
            dto.typ = dokument.typ?.name
        }
        if (dto.zahlungsziel != null && !dto.bezahlt) {
            dto.ueberfaellig = LocalDate.now().isAfter(dto.zahlungsziel)
        }
        dto.referenzNummer = gd.referenzNummer
        return dto
    }

    class EingangsrechnungDto {
        @JvmField var id: Long? = null
        @JvmField var dokumentId: Long? = null
        @JvmField var lieferantId: Long? = null
        @JvmField var lieferantName: String? = null
        @JvmField var dokumentNummer: String? = null
        @JvmField var dokumentDatum: LocalDate? = null
        @JvmField var zahlungsziel: LocalDate? = null
        @JvmField var betragNetto: Double? = null
        @JvmField var betragBrutto: Double? = null
        @JvmField var bezahlt: Boolean = false
        @JvmField var bezahltAm: LocalDate? = null
        @JvmField var bereitsGezahlt: Boolean = false
        @JvmField var zahlungsart: String? = null
        @JvmField var dateiname: String? = null
        @JvmField var pdfUrl: String? = null
        @JvmField var ueberfaellig: Boolean = false
        @JvmField var genehmigt: Boolean = false
        @JvmField var darfGenehmigen: Boolean = false
        @JvmField var referenzNummer: String? = null
        @JvmField var typ: String? = null
        @JvmField var skontoTage: Int? = null
        @JvmField var skontoProzent: Double? = null
        @JvmField var nettoTage: Int? = null
        @JvmField var skontoFrist: LocalDate? = null
        @JvmField var skontoVerbleibendeTage: Int? = null
        @JvmField var skontoAbgelaufen: Boolean = false
        @JvmField var tatsaechlichGezahlt: Double? = null
        @JvmField var mitSkonto: Boolean = false
    }

    companion object {
        private val log = LoggerFactory.getLogger(OffenePostenController::class.java)
    }
}
