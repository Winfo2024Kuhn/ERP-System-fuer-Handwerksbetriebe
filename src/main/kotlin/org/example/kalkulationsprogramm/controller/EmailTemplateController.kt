package org.example.kalkulationsprogramm.controller

import org.example.email.EmailService
import org.example.kalkulationsprogramm.service.DokumentFreigabeService
import org.example.kalkulationsprogramm.service.EmailTextTemplateService
import org.example.kalkulationsprogramm.service.FirmeninformationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@RestController
@RequestMapping("/api/email/template")
class EmailTemplateController(
    private val emailTextTemplateService: EmailTextTemplateService,
    private val dokumentFreigabeService: DokumentFreigabeService,
    private val firmeninformationService: FirmeninformationService,
) {
    @PostMapping
    fun generateTemplate(@RequestBody request: EmailTemplateRequest): ResponseEntity<EmailTemplateResponse> {
        val requestedDokumentTyp = request.dokumentTyp
        if (requestedDokumentTyp.isNullOrBlank()) {
            return ResponseEntity.badRequest().build()
        }

        val dokumentTyp = requestedDokumentTyp.uppercase(Locale.ROOT)
        return try {
            val content = renderFromDbTemplate(dokumentTyp, request) ?: renderFallback(dokumentTyp, request)
            var body = content.htmlBody()

            if ((dokumentTyp == "ANGEBOT" || dokumentTyp == "NACHTRAGSANGEBOT") && request.dokumentId != null) {
                val isAnfrage = request.isAnfrage == true
                val recipient = request.recipient ?: ""
                val gueltigkeitTage = request.gueltigkeitTage ?: DokumentFreigabeService.DEFAULT_GUELTIGKEITS_TAGE
                body += dokumentFreigabeService
                    .erstelleFreigabeBlockFuerDokument(
                        request.dokumentId,
                        isAnfrage,
                        recipient,
                        request.pdfDateiname,
                        gueltigkeitTage,
                    )
                    .orElse("")
            }

            ResponseEntity.ok(EmailTemplateResponse(content.subject(), body))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().build()
        }
    }

    private fun renderFromDbTemplate(dokumentTyp: String, req: EmailTemplateRequest): EmailService.EmailContent? {
        val ctx = mutableMapOf<String, String>()
        ctx["ANREDE"] = nullToEmpty(req.anrede, "Sehr geehrte Damen und Herren")
        ctx["KUNDENNAME"] = nullToEmpty(req.kundenName, "")
        ctx["ANSPRECHPARTNER"] = nullToEmpty(req.ansprechpartner, "")
        ctx["BAUVORHABEN"] = nullToEmpty(req.bauvorhaben, "")
        ctx["PROJEKTNUMMER"] = nullToEmpty(req.projektnummer, "")
        ctx["DOKUMENTNUMMER"] = nullToEmpty(req.dokumentnummer, "")
        ctx["BENUTZER"] = nullToEmpty(req.benutzer, "")
        ctx["BETRAG"] = nullToEmpty(req.betrag, "")
        ctx["RECHNUNGSDATUM"] = formatDate(req.rechnungsdatum)
        ctx["FAELLIGKEITSDATUM"] = formatDate(req.faelligkeitsdatum)
        ctx["REVIEW_LINK"] = buildReviewLink()
        return emailTextTemplateService.render(dokumentTyp, ctx)
    }

    private fun renderFallback(dokumentTyp: String, request: EmailTemplateRequest): EmailService.EmailContent {
        val anrede = request.anrede ?: "Sehr geehrte Damen und Herren"
        val bauvorhaben = request.bauvorhaben ?: ""
        val projektnummer = request.projektnummer ?: ""
        val benutzer = request.benutzer ?: ""

        return when (dokumentTyp) {
            "RECHNUNG", "TEILRECHNUNG", "SCHLUSSRECHNUNG", "ABSCHLAGSRECHNUNG" -> {
                val dokumentnummer = request.dokumentnummer ?: ""
                val rechnungsdatum = parseDate(request.rechnungsdatum, LocalDate.now())
                val faelligkeitsdatum = parseDate(request.faelligkeitsdatum, LocalDate.now().plusDays(14))
                val betrag = request.betrag ?: "0,00 €"
                val kundenName = request.kundenName ?: ""
                EmailService.buildInvoiceEmailWithTypeHints(
                    dokumentTyp.lowercase(Locale.ROOT),
                    anrede,
                    kundenName,
                    bauvorhaben,
                    projektnummer,
                    dokumentnummer,
                    rechnungsdatum,
                    faelligkeitsdatum,
                    betrag,
                    benutzer,
                    dokumentTyp,
                )
            }
            "MAHNUNG", "ZAHLUNGSERINNERUNG", "ERSTE_MAHNUNG", "ZWEITE_MAHNUNG" -> {
                val dokumentnummer = request.dokumentnummer ?: ""
                val rechnungsdatum = parseDate(request.rechnungsdatum, LocalDate.now())
                val faelligkeitsdatum = parseDate(request.faelligkeitsdatum, LocalDate.now().plusDays(14))
                val betrag = request.betrag ?: "0,00 €"
                val kundenName = request.kundenName ?: ""
                EmailService.buildInvoiceEmailWithTypeHints(
                    "mahnung",
                    anrede,
                    kundenName,
                    bauvorhaben,
                    projektnummer,
                    dokumentnummer,
                    rechnungsdatum,
                    faelligkeitsdatum,
                    betrag,
                    benutzer,
                    dokumentTyp,
                )
            }
            "ANGEBOT", "NACHTRAGSANGEBOT" -> {
                val anfragesnummer = request.dokumentnummer ?: ""
                val kundenName = request.kundenName ?: ""
                EmailService.buildOfferEmail(anrede, kundenName, bauvorhaben, anfragesnummer, benutzer, null)
            }
            "AUFTRAGSBESTAETIGUNG" -> {
                val auftragsnummer = request.dokumentnummer ?: projektnummer
                val kundenName = request.kundenName ?: ""
                EmailService.buildOrderConfirmationEmail(
                    null,
                    anrede,
                    kundenName,
                    bauvorhaben,
                    projektnummer,
                    auftragsnummer,
                    request.betrag,
                    benutzer,
                )
            }
            "ZEICHNUNG" -> EmailService.buildDrawingEmail(anrede, benutzer, bauvorhaben)
            else -> EmailService.EmailContent("", "")
        }
    }

    private fun buildReviewLink(): String {
        val url = firmeninformationService.getFirmeninformation().googleBewertungsLink
        if (url.isNullOrBlank()) {
            return ""
        }
        val safeUrl = url.trim().replace("\"", "%22")
        return "<a href=\"$safeUrl\" target=\"_blank\" rel=\"noopener noreferrer\">$REVIEW_LINK_LABEL</a>"
    }

    private fun parseDate(dateStr: String?, defaultValue: LocalDate): LocalDate {
        if (dateStr.isNullOrBlank()) {
            return defaultValue
        }
        return try {
            LocalDate.parse(dateStr, DATE_FORMAT)
        } catch (e: Exception) {
            defaultValue
        }
    }

    data class EmailTemplateRequest(
        var dokumentTyp: String? = null,
        var anrede: String? = null,
        var kundenName: String? = null,
        var ansprechpartner: String? = null,
        var bauvorhaben: String? = null,
        var projektnummer: String? = null,
        var dokumentnummer: String? = null,
        var rechnungsdatum: String? = null,
        var faelligkeitsdatum: String? = null,
        var betrag: String? = null,
        var benutzer: String? = null,
        var dokumentId: Long? = null,
        var isAnfrage: Boolean? = null,
        var recipient: String? = null,
        var pdfDateiname: String? = null,
        var gueltigkeitTage: Int? = null,
    )

    data class EmailTemplateResponse(
        var subject: String? = null,
        var body: String? = null,
    )

    companion object {
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val DISPLAY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private const val REVIEW_LINK_LABEL = "Jetzt Bewertung abgeben"

        private fun formatDate(dateStr: String?): String {
            if (dateStr.isNullOrBlank()) {
                return ""
            }
            return try {
                LocalDate.parse(dateStr, DATE_FORMAT).format(DISPLAY_DATE_FORMAT)
            } catch (e: Exception) {
                dateStr
            }
        }

        private fun nullToEmpty(value: String?, fallback: String?): String {
            return if (value.isNullOrBlank()) fallback ?: "" else value
        }
    }
}
