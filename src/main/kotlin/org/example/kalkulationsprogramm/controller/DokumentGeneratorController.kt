package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten
import org.example.kalkulationsprogramm.service.RechnungPdfService
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto
import org.example.kalkulationsprogramm.service.RechnungPdfService.FormBlockDto
import org.example.kalkulationsprogramm.service.RechnungPdfService.KopfdatenDto
import org.example.kalkulationsprogramm.service.RechnungPdfService.RechnungDto
import org.example.kalkulationsprogramm.service.ZugferdErstellService
import org.slf4j.LoggerFactory
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

@RestController
@RequestMapping("/api/dokument-generator")
class DokumentGeneratorController(
    private val rechnungPdfService: RechnungPdfService,
    private val zugferdErstellService: ZugferdErstellService,
) {
    data class GeneratePdfRequest(
        val dokumentTyp: String?,
        val templateName: String?,
        val kopfdaten: KopfdatenRequest,
        val layoutBlocks: List<FormBlockRequest>,
        val contentBlocks: List<DocBlockRequest>,
        val schlusstext: String?,
        val backgroundImagePage1: String?,
        val backgroundImagePage2: String?,
        val globalRabattProzent: Double?,
        val abrechnungsverlauf: AbrechnungsverlaufRequest?,
        val betragNetto: Double?,
        val abschlagInfo: AbschlagInfoRequest?,
    )

    data class AbschlagInfoRequest(val modus: String?, val eingabeWert: Double?)

    data class AbrechnungsverlaufRequest(
        val basisdokumentNummer: String?,
        val basisdokumentTyp: String?,
        val basisdokumentDatum: String?,
        val basisdokumentBetragNetto: Double?,
        val positionen: List<AbrechnungspositionRequest>?,
    )

    data class AbrechnungspositionRequest(
        val dokumentNummer: String?,
        val typ: String?,
        val datum: String?,
        val betragNetto: Double?,
        val abschlagsNummer: Int?,
    )

    data class KopfdatenRequest(
        val dokumentnummer: String?,
        val rechnungsDatum: String?,
        val leistungsDatum: String?,
        val kundenName: String?,
        val kundenAdresse: String?,
        val betreff: String?,
        val kundennummer: String?,
        val bezugsdokument: String?,
        val projektnummer: String?,
        val bauvorhaben: String?,
        val bezugsdokumentTyp: String?,
        val bezugsdokumentDatum: String?,
        val zahlungszielTage: Int?,
    )

    data class FormBlockRequest(
        val id: String?,
        val type: String?,
        val page: Int,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val content: String?,
        val styles: Map<String, Any>?,
    )

    data class DocBlockRequest(
        val id: String?,
        val type: String?,
        val content: String?,
        val pos: String?,
        val title: String?,
        val quantity: Double?,
        val unit: String?,
        val price: Double?,
        val description: String?,
        val optional: Boolean?,
        val fontSize: Int?,
        val fett: Boolean?,
        val sectionLabel: String?,
        val discount: Double?,
    )

    @PostMapping("/pdf")
    fun generatePdf(@RequestBody request: GeneratePdfRequest): ResponseEntity<ByteArray> {
        return try {
            log.info("PDF-Generierung für {} gestartet", request.dokumentTyp)

            val formBlocks = request.layoutBlocks.map { fb ->
                FormBlockDto(fb.id, fb.type, fb.page, fb.x, fb.y, fb.width, fb.height, fb.content, fb.styles)
            }
            val layout = RechnungPdfService.createLayoutFromFormBlocks(formBlocks, 595f, 842f)
            val dokumentTypLabel = when (request.dokumentTyp ?: "") {
                "ANGEBOT" -> "Angebot"
                "AUFTRAGSBESTAETIGUNG" -> "Auftragsbestätigung"
                "RECHNUNG" -> "Rechnung"
                "TEILRECHNUNG" -> "Teilrechnung"
                "ABSCHLAGSRECHNUNG" -> "Abschlagsrechnung"
                "SCHLUSSRECHNUNG" -> "Schlussrechnung"
                "GUTSCHRIFT" -> "Gutschrift"
                "STORNO" -> "Stornorechnung"
                else -> request.dokumentTyp ?: ""
            }

            val kopfdaten = KopfdatenDto(
                request.kopfdaten.dokumentnummer,
                parseDate(request.kopfdaten.rechnungsDatum),
                parseDate(request.kopfdaten.leistungsDatum),
                request.kopfdaten.kundenName,
                request.kopfdaten.kundenAdresse,
                request.kopfdaten.betreff,
                request.kopfdaten.kundennummer,
                dokumentTypLabel,
                request.kopfdaten.bezugsdokument,
                request.kopfdaten.projektnummer,
                request.kopfdaten.bauvorhaben,
                request.kopfdaten.bezugsdokumentTyp,
                request.kopfdaten.bezugsdokumentDatum,
                request.kopfdaten.zahlungszielTage,
            )

            val contentBlocks = ArrayList<ContentBlockDto>()
            for (block in request.contentBlocks) {
                when (block.type) {
                    "TEXT" -> contentBlocks.add(
                        ContentBlockDto(
                            "TEXT",
                            block.content,
                            block.fett == true,
                            block.fontSize ?: 10,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            false,
                            null,
                            null,
                        ),
                    )
                    "SERVICE" -> {
                        val menge = block.quantity?.let(BigDecimal::valueOf) ?: BigDecimal.ONE
                        val einzelpreis = block.price?.let(BigDecimal::valueOf) ?: BigDecimal.ZERO
                        var gesamt = menge.multiply(einzelpreis)
                        val rabattProzent = if (block.discount != null && block.discount > 0) BigDecimal.valueOf(block.discount) else null
                        if (rabattProzent != null) {
                            val rabattFaktor = BigDecimal.ONE.subtract(rabattProzent.divide(BigDecimal("100"), 4, RoundingMode.HALF_UP))
                            gesamt = gesamt.multiply(rabattFaktor).setScale(2, RoundingMode.HALF_UP)
                        }
                        contentBlocks.add(
                            ContentBlockDto(
                                "SERVICE",
                                null,
                                block.fett == true,
                                block.fontSize ?: 10,
                                block.pos ?: "",
                                block.title ?: "",
                                block.description,
                                menge,
                                block.unit ?: "Stk",
                                einzelpreis,
                                gesamt,
                                block.optional == true,
                                null,
                                rabattProzent,
                            ),
                        )
                    }
                    "CLOSURE", "SEPARATOR", "SUBTOTAL" -> contentBlocks.add(
                        ContentBlockDto(block.type, null, false, 0, null, null, null, null, null, null, null, false, null, null),
                    )
                    "SECTION_HEADER" -> contentBlocks.add(
                        ContentBlockDto(
                            "SECTION_HEADER",
                            null,
                            false,
                            0,
                            block.pos ?: "",
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            false,
                            block.sectionLabel ?: "",
                            null,
                        ),
                    )
                }
            }

            val globalRabatt = if (request.globalRabattProzent != null && request.globalRabattProzent > 0) {
                BigDecimal.valueOf(request.globalRabattProzent)
            } else {
                null
            }

            val avReq = request.abrechnungsverlauf
            val abrechnungsverlaufPdf =
                if (avReq != null && avReq.basisdokumentBetragNetto != null && avReq.basisdokumentBetragNetto > 0) {
                    val posList = avReq.positionen?.map { p ->
                        RechnungPdfService.AbrechnungspositionPdfDto(
                            p.dokumentNummer,
                            p.typ,
                            p.datum?.let(LocalDate::parse),
                            p.betragNetto?.let(BigDecimal::valueOf) ?: BigDecimal.ZERO,
                            p.abschlagsNummer,
                        )
                    } ?: emptyList()
                    RechnungPdfService.AbrechnungsverlaufPdfDto(
                        avReq.basisdokumentNummer,
                        avReq.basisdokumentTyp,
                        avReq.basisdokumentDatum?.takeIf { it.isNotBlank() }?.let(LocalDate::parse),
                        avReq.basisdokumentBetragNetto.let(BigDecimal::valueOf),
                        posList,
                    )
                } else {
                    null
                }

            val abschlagInfoPdf = request.abschlagInfo?.modus?.let { modus ->
                RechnungPdfService.AbschlagInfoPdfDto(
                    modus,
                    request.abschlagInfo.eingabeWert?.let(BigDecimal::valueOf),
                )
            }

            val rechnungDto = RechnungDto(
                layout,
                kopfdaten,
                contentBlocks,
                formBlocks,
                request.schlusstext,
                request.backgroundImagePage1,
                request.backgroundImagePage2,
                globalRabatt,
                abrechnungsverlaufPdf,
                request.betragNetto?.let(BigDecimal::valueOf),
                abschlagInfoPdf,
            )

            val pdfBytes = rechnungPdfService.generatePdfBytes(rechnungDto)
            val rawFilename = (request.dokumentTyp ?: "").lowercase() + "_" + request.kopfdaten.dokumentnummer + ".pdf"
            val filename = rawFilename.replace(Regex("[\\\\/:*?\"<>|]"), "_")

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_PDF
            headers.contentDisposition = ContentDisposition.builder("attachment").filename(filename).build()
            headers.contentLength = pdfBytes.size.toLong()

            log.info("PDF {} erfolgreich generiert ({} bytes)", filename, pdfBytes.size)
            ResponseEntity(pdfBytes, headers, HttpStatus.OK)
        } catch (e: Exception) {
            log.error("Fehler bei PDF-Generierung", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    @PostMapping("/preview")
    fun previewPdf(@RequestBody request: GeneratePdfRequest): ResponseEntity<ByteArray> {
        val response = generatePdf(request)
        if (response.statusCode == HttpStatus.OK) {
            val body = response.body ?: return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_PDF
            headers.contentDisposition = ContentDisposition.builder("inline").build()
            headers.contentLength = body.size.toLong()
            return ResponseEntity(body, headers, HttpStatus.OK)
        }
        return response
    }

    @PostMapping("/zugferd-pdf")
    fun generateZugferdPdf(@RequestBody request: GeneratePdfRequest): ResponseEntity<ByteArray> {
        var tempPdf: Path? = null
        var zugferdPdf: Path? = null
        return try {
            log.info("ZUGFeRD-PDF-Generierung für {} gestartet", request.dokumentTyp)
            val pdfResponse = generatePdf(request)
            if (pdfResponse.statusCode != HttpStatus.OK || pdfResponse.body == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
            }

            tempPdf = Files.createTempFile("zugferd-source-", ".pdf")
            Files.write(tempPdf, pdfResponse.body!!)

            val daten = ZugferdDaten()
            daten.geschaeftsdokumentart = request.dokumentTyp
            daten.rechnungsnummer = request.kopfdaten.dokumentnummer
            daten.rechnungsdatum = parseDate(request.kopfdaten.rechnungsDatum)
            daten.kundenName = request.kopfdaten.kundenName
            daten.kundennummer = request.kopfdaten.kundennummer

            var betrag = BigDecimal.ZERO
            if (request.betragNetto != null) {
                betrag = BigDecimal.valueOf(request.betragNetto)
            } else {
                for (block in request.contentBlocks) {
                    if (block.type == "SERVICE" && block.optional != true) {
                        val menge = block.quantity?.let(BigDecimal::valueOf) ?: BigDecimal.ONE
                        val preis = block.price?.let(BigDecimal::valueOf) ?: BigDecimal.ZERO
                        var pos = menge.multiply(preis)
                        if (block.discount != null && block.discount > 0) {
                            val rabattFaktor = BigDecimal.ONE.subtract(
                                BigDecimal.valueOf(block.discount).divide(BigDecimal("100"), 4, RoundingMode.HALF_UP),
                            )
                            pos = pos.multiply(rabattFaktor).setScale(2, RoundingMode.HALF_UP)
                        }
                        betrag = betrag.add(pos)
                    }
                }
            }
            if (request.betragNetto == null && request.globalRabattProzent != null && request.globalRabattProzent > 0) {
                val rabattFaktor = BigDecimal.ONE.subtract(
                    BigDecimal.valueOf(request.globalRabattProzent).divide(BigDecimal("100"), 4, RoundingMode.HALF_UP),
                )
                betrag = betrag.multiply(rabattFaktor).setScale(2, RoundingMode.HALF_UP)
            }
            daten.betrag = betrag.multiply(BigDecimal("1.19")).setScale(2, RoundingMode.HALF_UP)
            val rechnungsDatum = parseDate(request.kopfdaten.rechnungsDatum)
            daten.faelligkeitsdatum = rechnungsDatum.plusDays(14)

            zugferdPdf = zugferdErstellService.erzeuge(tempPdf.toString(), daten)
            val zugferdBytes = Files.readAllBytes(zugferdPdf)
            val rawFilename = "zugferd_" + (request.dokumentTyp ?: "").lowercase() + "_" + request.kopfdaten.dokumentnummer + ".pdf"
            val filename = rawFilename.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_PDF
            headers.contentDisposition = ContentDisposition.builder("attachment").filename(filename).build()
            headers.contentLength = zugferdBytes.size.toLong()

            log.info("ZUGFeRD-PDF {} erfolgreich generiert ({} bytes)", filename, zugferdBytes.size)
            ResponseEntity(zugferdBytes, headers, HttpStatus.OK)
        } catch (e: Exception) {
            log.error("Fehler bei ZUGFeRD-PDF-Generierung", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        } finally {
            try {
                tempPdf?.let { Files.deleteIfExists(it) }
            } catch (_: Exception) {
            }
            try {
                zugferdPdf?.let { Files.deleteIfExists(it) }
            } catch (_: Exception) {
            }
        }
    }

    private fun parseDate(dateStr: String?): LocalDate {
        if (dateStr.isNullOrBlank()) return LocalDate.now()
        return try {
            LocalDate.parse(dateStr)
        } catch (_: Exception) {
            LocalDate.now()
        }
    }

    @Suppress("unused")
    private fun stripHtml(html: String?): String {
        if (html == null) return ""
        return html
            .replace(Regex("<br\\s*/?>"), "\n")
            .replace("<p>", "")
            .replace("</p>", "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    companion object {
        private val log = LoggerFactory.getLogger(DokumentGeneratorController::class.java)
    }
}
