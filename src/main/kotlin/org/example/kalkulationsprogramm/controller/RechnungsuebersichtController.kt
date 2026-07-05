package org.example.kalkulationsprogramm.controller

import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.example.kalkulationsprogramm.domain.LieferantDokument
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

@RestController
@RequestMapping("/api/rechnungsuebersicht")
class RechnungsuebersichtController(
    private val projektDokumentRepository: ProjektDokumentRepository,
    private val lieferantGeschaeftsdokumentRepository: LieferantGeschaeftsdokumentRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val lieferantenRepository: LieferantenRepository,
    private val geminiService: GeminiDokumentAnalyseService,
    private val lieferantDokumentRepository: LieferantDokumentRepository,
) {
    private val log = LoggerFactory.getLogger(RechnungsuebersichtController::class.java)

    @Value("\${upload.path:uploads}")
    private lateinit var uploadPath: String

    @GetMapping("/ausgang")
    fun getAusgangsrechnungen(
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
        @RequestParam(required = false) search: String?,
    ): ResponseEntity<List<AusgangsrechnungDto>> {
        var rechnungen: MutableList<ProjektGeschaeftsdokument> = when {
            year != null && month != null -> {
                val ym = YearMonth.of(year, month)
                projektDokumentRepository.findGeschaeftsdokumenteByRechnungsdatumBetween(ym.atDay(1), ym.atEndOfMonth()).toMutableList()
            }
            year != null -> {
                projektDokumentRepository.findGeschaeftsdokumenteByRechnungsdatumBetween(
                    LocalDate.of(year, 1, 1),
                    LocalDate.of(year, 12, 31),
                ).toMutableList()
            }
            else -> projektDokumentRepository.findAllGeschaeftsdokumente()
                .filter {
                    "Rechnung".equals(it.geschaeftsdokumentart, ignoreCase = true) ||
                        (it.geschaeftsdokumentart != null && it.geschaeftsdokumentart!!.lowercase().contains("rechnung"))
                }
                .toMutableList()
        }

        if (!search.isNullOrBlank()) {
            val lowerSearch = search.lowercase()
            rechnungen = rechnungen.filter { matchesAusgangSearch(it, lowerSearch) }.toMutableList()
        }

        rechnungen.sortWith(compareByDescending<ProjektGeschaeftsdokument> { it.rechnungsdatum ?: LocalDate.MIN })
        return ResponseEntity.ok(rechnungen.map { toAusgangsrechnungDto(it) })
    }

    private fun matchesAusgangSearch(r: ProjektGeschaeftsdokument, search: String): Boolean {
        if (r.dokumentid?.lowercase()?.contains(search) == true) return true
        if (r.rechnungsdatum?.toString()?.contains(search) == true) return true
        if (r.bruttoBetrag?.toString()?.contains(search) == true) return true
        val projekt = r.projekt
        if (projekt != null) {
            if (projekt.auftragsnummer?.lowercase()?.contains(search) == true) return true
            if (projekt.getKunde()?.lowercase()?.contains(search) == true) return true
        }
        return false
    }

    @GetMapping("/eingang")
    fun getEingangsrechnungen(
        @RequestParam(required = false) year: Int?,
        @RequestParam(required = false) month: Int?,
        @RequestParam(required = false) search: String?,
    ): ResponseEntity<List<EingangsrechnungDto>> {
        var rechnungen: MutableList<LieferantGeschaeftsdokument> = when {
            year != null && month != null -> {
                val ym = YearMonth.of(year, month)
                lieferantGeschaeftsdokumentRepository.findRechnungenByDatumBetween(ym.atDay(1), ym.atEndOfMonth()).toMutableList()
            }
            year != null -> {
                lieferantGeschaeftsdokumentRepository.findRechnungenByDatumBetween(
                    LocalDate.of(year, 1, 1),
                    LocalDate.of(year, 12, 31),
                ).toMutableList()
            }
            else -> lieferantGeschaeftsdokumentRepository.findAllEingangsrechnungen().toMutableList()
        }

        if (!search.isNullOrBlank()) {
            val lowerSearch = search.lowercase()
            rechnungen = rechnungen.filter { matchesEingangSearch(it, lowerSearch) }.toMutableList()
        }

        rechnungen.sortWith(compareByDescending<LieferantGeschaeftsdokument> { it.dokumentDatum ?: LocalDate.MIN })
        return ResponseEntity.ok(rechnungen.map { toEingangsrechnungDto(it) })
    }

    private fun matchesEingangSearch(r: LieferantGeschaeftsdokument, search: String): Boolean {
        if (r.dokumentNummer?.lowercase()?.contains(search) == true) return true
        if (r.dokumentDatum?.toString()?.contains(search) == true) return true
        if (r.betragNetto?.toString()?.contains(search) == true) return true
        if (r.betragBrutto?.toString()?.contains(search) == true) return true
        if (r.dokument?.lieferant?.lieferantenname?.lowercase()?.contains(search) == true) return true
        return false
    }

    @PostMapping("/merge-pdf")
    fun mergePdfs(@RequestBody request: MergePdfRequest): ResponseEntity<ByteArray> {
        log.info("[merge-pdf] Request received: ausgangIds={}, eingangIds={}", request.ausgangIds, request.eingangIds)
        try {
            val merger = PDFMergerUtility()
            val buffers = ArrayList<RandomAccessReadBuffer>()

            request.ausgangIds?.forEach { id ->
                log.info("[merge-pdf] Looking up Ausgangsrechnung ID={}", id)
                val dokOpt = projektDokumentRepository.findById(id)
                if (dokOpt.isPresent) {
                    val dok = dokOpt.get()
                    log.info("[merge-pdf] Found document type={}", dok.javaClass.simpleName)
                    if (dok is ProjektGeschaeftsdokument) {
                        val pdfPath = resolveProjektDokumentPath(dok)
                        log.info("[merge-pdf] Resolved path={}, exists={}", pdfPath, pdfPath != null && Files.exists(pdfPath))
                        if (pdfPath != null && Files.exists(pdfPath)) {
                            val buffer = RandomAccessReadBuffer(Files.readAllBytes(pdfPath))
                            buffers.add(buffer)
                            merger.addSource(buffer)
                        }
                    }
                } else {
                    log.warn("[merge-pdf] Ausgangsrechnung ID={} not found", id)
                }
            }

            request.eingangIds?.forEach { id ->
                log.info("[merge-pdf] Looking up Eingangsrechnung ID={}", id)
                val gdOpt = lieferantGeschaeftsdokumentRepository.findById(id)
                if (gdOpt.isPresent) {
                    val gd = gdOpt.get()
                    val dokument = gd.dokument
                    if (dokument != null) {
                        val pdfPath = resolveLieferantDokumentPath(dokument)
                        log.info("[merge-pdf] Resolved path={}, exists={}", pdfPath, pdfPath != null && Files.exists(pdfPath))
                        if (pdfPath != null && Files.exists(pdfPath)) {
                            val buffer = RandomAccessReadBuffer(Files.readAllBytes(pdfPath))
                            buffers.add(buffer)
                            merger.addSource(buffer)
                        }
                    } else {
                        log.warn("[merge-pdf] Eingangsrechnung ID={} has no associated dokument", id)
                    }
                } else {
                    log.warn("[merge-pdf] Eingangsrechnung ID={} not found", id)
                }
            }

            log.info("[merge-pdf] Total buffers collected: {}", buffers.size)
            if (buffers.isEmpty()) return ResponseEntity.badRequest().build()

            val outputStream = ByteArrayOutputStream()
            merger.destinationStream = outputStream
            merger.mergeDocuments(null)
            buffers.forEach {
                try {
                    it.close()
                } catch (_: IOException) {
                }
            }

            val filename = "Rechnungen_${LocalDate.now()}.pdf"
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(outputStream.toByteArray())
        } catch (e: Exception) {
            log.error("Fehler beim Zusammenfuehren der PDFs", e)
            return ResponseEntity.internalServerError().build()
        }
    }

    private fun resolveProjektDokumentPath(dok: ProjektGeschaeftsdokument): Path? {
        val gespeicherterDateiname = dok.gespeicherterDateiname ?: return null
        val rootPath = Path.of(uploadPath, gespeicherterDateiname)
        if (Files.exists(rootPath)) return rootPath
        val attachmentsPath = Path.of(uploadPath, "attachments", gespeicherterDateiname)
        if (Files.exists(attachmentsPath)) return attachmentsPath
        return rootPath
    }

    private fun resolveLieferantDokumentPath(dok: LieferantDokument): Path? {
        val gespeicherterDateiname = dok.gespeicherterDateiname
        if (gespeicherterDateiname != null) {
            val rootPath = Path.of(uploadPath, gespeicherterDateiname)
            if (Files.exists(rootPath)) return rootPath
            val attachmentsPath = Path.of(uploadPath, "attachments", gespeicherterDateiname)
            if (Files.exists(attachmentsPath)) return attachmentsPath
            val emailId = dok.attachment?.email?.id
            if (emailId != null) {
                val emailPath = Path.of(uploadPath, "attachments", emailId.toString(), gespeicherterDateiname)
                if (Files.exists(emailPath)) return emailPath
            }
            return rootPath
        }

        val attachment = dok.attachment
        val storedFilename = attachment?.storedFilename
        val emailId = attachment?.email?.id
        if (storedFilename != null && emailId != null) {
            return Path.of(uploadPath, "attachments", emailId.toString(), storedFilename)
        }
        return null
    }

    private fun toAusgangsrechnungDto(gd: ProjektGeschaeftsdokument): AusgangsrechnungDto {
        val dto = AusgangsrechnungDto()
        dto.id = gd.id
        dto.dokumentid = gd.dokumentid
        dto.geschaeftsdokumentart = gd.geschaeftsdokumentart
        dto.rechnungsdatum = gd.rechnungsdatum
        dto.faelligkeitsdatum = gd.faelligkeitsdatum
        dto.bruttoBetrag = gd.bruttoBetrag?.toDouble()
        dto.bezahlt = gd.isBezahlt()
        dto.originalDateiname = gd.originalDateiname
        if (gd.gespeicherterDateiname != null) dto.pdfUrl = "/api/dokumente/${gd.gespeicherterDateiname}"

        gd.projekt?.let {
            dto.projektId = it.id
            dto.projektAuftragsnummer = it.auftragsnummer
            dto.projektKunde = it.getKunde()
        }
        return dto
    }

    private fun toEingangsrechnungDto(gd: LieferantGeschaeftsdokument): EingangsrechnungDto {
        val dto = EingangsrechnungDto()
        dto.id = gd.id
        dto.dokumentNummer = gd.dokumentNummer
        dto.dokumentDatum = gd.dokumentDatum
        dto.betragNetto = gd.betragNetto?.toDouble()
        dto.betragBrutto = gd.betragBrutto?.toDouble()
        dto.bezahlt = gd.bezahlt == true
        dto.zahlungsart = gd.zahlungsart

        gd.dokument?.let { dokument ->
            dto.dokumentId = dokument.id
            dto.originalDateiname = dokument.getEffektiverDateiname()
            dokument.lieferant?.let {
                dto.lieferantId = it.id
                dto.lieferantName = it.lieferantenname
            }
            when {
                dokument.beleg != null -> dto.pdfUrl = "/api/buchhaltung/belege/${dokument.beleg!!.id}/datei"
                dokument.attachment != null -> {
                    val att = dokument.attachment!!
                    if (att.email != null) dto.pdfUrl = "/api/emails/${att.email!!.id}/attachments/${att.id}"
                }
                dokument.gespeicherterDateiname != null -> {
                    dto.pdfUrl = "/api/lieferanten/${dto.lieferantId}/dokumente/${dokument.id}/download"
                }
            }
        }
        return dto
    }

    @PostMapping(value = ["/analyze-upload"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun analyzeUpload(@RequestPart("datei") datei: MultipartFile): ResponseEntity<List<LieferantDokumentDto.MultiInvoiceAnalyzeResponse>> {
        try {
            val originalFilename = StringUtils.cleanPath(requireNotNull(datei.originalFilename))
            if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
                return ResponseEntity.badRequest().build()
            }
            val tempDir = Path.of(System.getProperty("java.io.tmpdir"))
            val tempPath = tempDir.resolve("${UUID.randomUUID()}_$originalFilename")
            datei.transferTo(tempPath)

            try {
                val results = geminiService.analyzeFileForMultipleInvoices(tempPath, originalFilename)
                if (results.isEmpty()) {
                    results.add(
                        LieferantDokumentDto.MultiInvoiceAnalyzeResponse.builder()
                            .pageRange("alle")
                            .analyzeResponse(
                                LieferantDokumentDto.AnalyzeResponse.builder()
                                    .dokumentTyp(LieferantDokumentTyp.RECHNUNG)
                                    .dokumentDatum(LocalDate.now())
                                    .aiConfidence(0.0)
                                    .analyseQuelle("KEINE")
                                    .build(),
                            )
                            .build(),
                    )
                }
                return ResponseEntity.ok(results)
            } finally {
                Files.deleteIfExists(tempPath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ResponseEntity.internalServerError().build()
        }
    }

    @PostMapping(value = ["/import-upload"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Transactional
    fun importUpload(
        @RequestPart("datei") datei: MultipartFile,
        @RequestPart("metadata") request: LieferantDokumentDto.ImportRequest,
        @RequestParam(value = "token", required = false) token: String?,
    ): ResponseEntity<Any> {
        if (request.lieferantId == null) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Bitte waehlen Sie einen Lieferanten aus."))
        }

        try {
            val lieferant = lieferantenRepository.findById(request.lieferantId!!).orElse(null)
                ?: return ResponseEntity.badRequest().body(mapOf("message" to "Lieferant nicht gefunden."))

            if (StringUtils.hasText(request.dokumentNummer)) {
                val dokumentNummer = requireNotNull(request.dokumentNummer)
                val exists = lieferantGeschaeftsdokumentRepository
                    .existsByLieferantIdAndDokumentNummer(lieferant.id, dokumentNummer)
                if (exists) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(mapOf("message" to "Ein Dokument mit dieser Nummer existiert bereits fuer diesen Lieferanten."))
                }
            }

            val originalFilename = StringUtils.cleanPath(requireNotNull(datei.originalFilename))
            val storedFilename = "${UUID.randomUUID()}_$originalFilename"
            val lieferantDir = Paths.get("uploads", "lieferanten", lieferant.id.toString())
            Files.createDirectories(lieferantDir)
            val targetPath = lieferantDir.resolve(storedFilename)
            datei.transferTo(targetPath)

            var dokument = LieferantDokument()
            dokument.lieferant = lieferant
            dokument.typ = request.dokumentTyp ?: LieferantDokumentTyp.RECHNUNG
            dokument.originalDateiname = originalFilename
            dokument.gespeicherterDateiname = storedFilename
            dokument.uploadDatum = LocalDateTime.now()

            if (token != null) {
                val mitarbeiter = mitarbeiterRepository.findByLoginToken(token).orElse(null)
                if (mitarbeiter != null) dokument.uploadedBy = mitarbeiter
            }

            dokument = lieferantDokumentRepository.save(dokument)

            val geschaeftsdaten = LieferantGeschaeftsdokument()
            geschaeftsdaten.dokument = dokument
            geschaeftsdaten.dokumentNummer = request.dokumentNummer
            geschaeftsdaten.dokumentDatum = request.dokumentDatum
            geschaeftsdaten.betragNetto = request.betragNetto
            geschaeftsdaten.betragBrutto = request.betragBrutto
            geschaeftsdaten.mwstSatz = request.mwstSatz
            geschaeftsdaten.liefertermin = request.liefertermin
            geschaeftsdaten.zahlungsziel = request.zahlungsziel
            geschaeftsdaten.bestellnummer = request.bestellnummer
            geschaeftsdaten.referenzNummer = request.referenzNummer
            geschaeftsdaten.skontoTage = request.skontoTage
            geschaeftsdaten.skontoProzent = request.skontoProzent
            geschaeftsdaten.nettoTage = request.nettoTage
            geschaeftsdaten.bereitsGezahlt = request.bereitsGezahlt
            geschaeftsdaten.zahlungsart = request.zahlungsart
            geschaeftsdaten.aiConfidence = 1.0
            geschaeftsdaten.analysiertAm = LocalDateTime.now()
            lieferantGeschaeftsdokumentRepository.save(geschaeftsdaten)

            return ResponseEntity.ok(mapOf("id" to dokument.id, "message" to "Import erfolgreich"))
        } catch (e: Exception) {
            e.printStackTrace()
            return ResponseEntity.internalServerError().body(mapOf("message" to "Fehler beim Import: ${e.message}"))
        }
    }

    class AusgangsrechnungDto {
        var id: Long? = null
        var dokumentid: String? = null
        var geschaeftsdokumentart: String? = null
        var rechnungsdatum: LocalDate? = null
        var faelligkeitsdatum: LocalDate? = null
        var bruttoBetrag: Double? = null
        var bezahlt: Boolean = false
        var originalDateiname: String? = null
        var pdfUrl: String? = null
        var projektId: Long? = null
        var projektAuftragsnummer: String? = null
        var projektKunde: String? = null
    }

    class EingangsrechnungDto {
        var id: Long? = null
        var dokumentId: Long? = null
        var lieferantId: Long? = null
        var lieferantName: String? = null
        var dokumentNummer: String? = null
        var dokumentDatum: LocalDate? = null
        var betragNetto: Double? = null
        var betragBrutto: Double? = null
        var bezahlt: Boolean = false
        var zahlungsart: String? = null
        var originalDateiname: String? = null
        var pdfUrl: String? = null
    }

    data class MergePdfRequest(
        var ausgangIds: List<Long>? = null,
        var eingangIds: List<Long>? = null,
    )
}
