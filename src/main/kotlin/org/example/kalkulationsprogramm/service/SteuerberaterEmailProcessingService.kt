package org.example.kalkulationsprogramm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.example.kalkulationsprogramm.domain.BwaTyp
import org.example.kalkulationsprogramm.domain.BwaUpload
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailAttachment
import org.example.kalkulationsprogramm.domain.Lohnabrechnung
import org.example.kalkulationsprogramm.domain.LohnabrechnungStatus
import org.example.kalkulationsprogramm.domain.Mitarbeiter
import org.example.kalkulationsprogramm.domain.SteuerberaterKontakt
import org.example.kalkulationsprogramm.repository.BwaUploadRepository
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.example.kalkulationsprogramm.repository.LohnabrechnungRepository
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository
import org.example.kalkulationsprogramm.repository.SteuerberaterKontaktRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.regex.Pattern

@Service
class SteuerberaterEmailProcessingService(
    private val steuerberaterRepository: SteuerberaterKontaktRepository,
    private val mitarbeiterRepository: MitarbeiterRepository,
    private val lohnabrechnungRepository: LohnabrechnungRepository,
    private val bwaUploadRepository: BwaUploadRepository,
    private val emailRepository: EmailRepository,
    private val geminiService: GeminiDokumentAnalyseService,
    private val objectMapper: ObjectMapper,
) {
    @Value("\${file.lohnabrechnung-dir:uploads/lohnabrechnungen}")
    private lateinit var lohnabrechnungDir: String

    @Value("\${file.bwa-dir:uploads/bwa}")
    private lateinit var bwaDir: String

    @Value("\${file.mail-attachment-dir}")
    private lateinit var mailAttachmentDir: String

    @Transactional
    fun processSteuerberaterEmail(email: Email?): Boolean {
        if (email?.fromAddress == null) return false
        val steuerberater = findSteuerberaterByEmail(email.fromAddress) ?: return false

        log.info("[Steuerberater] E-Mail von Steuerberater erkannt: {} ({})", steuerberater.name, email.fromAddress)
        email.assignToSteuerberater(steuerberater)
        emailRepository.save(email)

        val attachments = email.attachments
        if (!attachments.isNullOrEmpty()) {
            for (attachment in attachments) {
                processAttachment(attachment, email, steuerberater)
            }
        }
        return true
    }

    private fun findSteuerberaterByEmail(fromAddress: String?): SteuerberaterKontakt? {
        if (fromAddress.isNullOrBlank()) return null
        val emailLower = fromAddress.lowercase().trim()
        val exact = steuerberaterRepository.findByEmailIgnoreCase(emailLower)
        if (exact.isPresent && exact.get().aktiv == true) return exact.get()

        val domain = if (emailLower.contains("@")) emailLower.substring(emailLower.lastIndexOf("@") + 1) else null
        if (domain != null) {
            for (sb in steuerberaterRepository.findByAktivTrueAndAutoProcessEmailsTrue()) {
                val sbEmail = sb.email
                if (sbEmail != null && sbEmail.lowercase().endsWith("@$domain")) return sb
            }
        }
        return null
    }

    private fun processAttachment(attachment: EmailAttachment, email: Email, steuerberater: SteuerberaterKontakt) {
        val filename = attachment.originalFilename ?: return
        val filenameLower = filename.lowercase()
        if (!filenameLower.endsWith(".pdf")) {
            log.debug("[Steuerberater] Ueberspringe Nicht-PDF: {}", filename)
            return
        }
        if (isBwaFilename(filenameLower)) {
            processBwaPdf(attachment, email, steuerberater)
            return
        }
        processSteuerberaterPdf(attachment, email, steuerberater)
    }

    private fun isLohnabrechnungFilename(filename: String): Boolean =
        filename.contains("lohn") || filename.contains("gehalt") || filename.contains("abrechnung") ||
            filename.contains("entgelt") || filename.contains("verdienst")

    private fun isBwaFilename(filename: String): Boolean =
        filename.contains("bwa") || filename.contains("summen") || filename.contains("salden") ||
            filename.contains("betriebswirtschaftlich")

    private fun processSteuerberaterPdf(attachment: EmailAttachment, email: Email, steuerberater: SteuerberaterKontakt) {
        log.info("[Steuerberater] Analysiere PDF: {}", attachment.originalFilename)
        try {
            val originalFilename = attachment.originalFilename ?: return
            val emailId = requireNotNull(email.id)
            val pdfPath = Paths.get(mailAttachmentDir, attachment.storedFilename)
            if (!Files.exists(pdfPath)) {
                log.error("Datei nicht gefunden: {}", pdfPath)
                return
            }
            if (lohnabrechnungRepository.existsBySourceEmailIdAndOriginalDateiname(emailId, originalFilename)) {
                log.info("[Steuerberater] Anhang {} bereits importiert (Skipped)", attachment.originalFilename)
                return
            }

            val fileBytes = Files.readAllBytes(pdfPath)
            val aiPrompt = """
                Du bekommst ein PDF, das ein Steuerberater per E-Mail an einen Handwerksbetrieb geschickt hat.

                1. Klassifiziere das Dokument:
                   - "LOHNABRECHNUNG": Lohn-/Gehalts-/Entgeltabrechnung(en)
                   - "BWA": Betriebswirtschaftliche Auswertung
                   - "SONSTIGES": alles andere

                2. Bei LOHNABRECHNUNG: Das PDF ist meist eine Sammel-PDF mit den Abrechnungen
                   MEHRERER Mitarbeiter hintereinander (je 1-2 Seiten pro Mitarbeiter).
                   Finde JEDE einzelne Abrechnung und gib ihren Seitenbereich an (1-basiert).

                Antworte NUR mit JSON (kein Markdown):
                {
                    "dokumentTyp": "LOHNABRECHNUNG",
                    "abrechnungen": [
                        {
                            "mitarbeiterName": "Vor- und Nachname",
                            "seiten": "1-2",
                            "monat": 1-12,
                            "jahr": YYYY,
                            "bruttolohn": Betrag als Zahl,
                            "nettolohn": Betrag als Zahl
                        }
                    ]
                }
                Bei BWA oder SONSTIGES: "abrechnungen" als leeres Array.
            """.trimIndent()

            val aiResponse = geminiService.rufGeminiApiMitPrompt(fileBytes, "application/pdf", aiPrompt, true)
            val root = parseAiJson(aiResponse)
            val dokumentTyp = if (root != null && root.has("dokumentTyp")) root.get("dokumentTyp").asText("") else ""

            if ("BWA".equals(dokumentTyp, ignoreCase = true)) {
                processBwaPdf(attachment, email, steuerberater)
                return
            }
            if ("LOHNABRECHNUNG".equals(dokumentTyp, ignoreCase = true) &&
                root != null && root.has("abrechnungen") && root.get("abrechnungen").isArray &&
                root.get("abrechnungen").size() > 0
            ) {
                verarbeiteLohnabrechnungen(root.get("abrechnungen"), aiResponse, fileBytes, attachment, email, steuerberater)
                return
            }
            if (isLohnabrechnungFilename(originalFilename.lowercase())) {
                log.warn("[Steuerberater] KI-Klassifikation fehlgeschlagen, Dateiname deutet auf Lohnabrechnung: {}", attachment.originalFilename)
                verarbeiteLohnabrechnungFallback(aiResponse, fileBytes, attachment, email, steuerberater)
                return
            }
            log.info("[Steuerberater] PDF als '{}' klassifiziert, keine Verarbeitung: {}", dokumentTyp.ifBlank { "unbekannt" }, attachment.originalFilename)
        } catch (ex: Exception) {
            log.error("[Steuerberater] Fehler bei PDF-Verarbeitung: {}", ex.message, ex)
        }
    }

    @Throws(IOException::class)
    private fun verarbeiteLohnabrechnungen(
        abrechnungen: JsonNode,
        aiResponse: String?,
        fileBytes: ByteArray,
        attachment: EmailAttachment,
        email: Email,
        steuerberater: SteuerberaterKontakt,
    ) {
        var erstellt = 0
        var nichtZugeordnet = 0
        Loader.loadPDF(fileBytes).use { originalDoc ->
            val totalPages = originalDoc.numberOfPages
            for (node in abrechnungen) {
                val mitarbeiterName = if (node.has("mitarbeiterName")) node.get("mitarbeiterName").asText(null) else null
                val mitarbeiter = mitarbeiterName?.let { findMitarbeiterByName(it) }
                if (mitarbeiter == null) {
                    nichtZugeordnet++
                    log.warn("[Steuerberater] Kein Mitarbeiter gefunden fuer Abrechnung '{}' in {} - Segment uebersprungen", mitarbeiterName, attachment.originalFilename)
                    continue
                }

                var jahr: Int? = if (node.has("jahr") && node.get("jahr").canConvertToInt()) node.get("jahr").asInt() else null
                var monat: Int? = if (node.has("monat") && node.get("monat").canConvertToInt()) node.get("monat").asInt() else null
                if (jahr == null || monat == null || monat !in 1..12) {
                    val periode = extractPeriodFromFilename(attachment.originalFilename)
                    if (jahr == null) jahr = periode[0]
                    if (monat == null || monat !in 1..12) monat = periode[1]
                }

                val seiten = if (node.has("seiten")) node.get("seiten").asText("") else ""
                val teilPdf = extrahiereSeiten(originalDoc, seiten, totalPages)
                val gespeicherterName = speichereLohnabrechnungPdf(teilPdf)
                speichereLohnabrechnung(
                    mitarbeiter,
                    jahr,
                    monat,
                    if (node.has("bruttolohn")) node.get("bruttolohn").decimalValue() else null,
                    if (node.has("nettolohn")) node.get("nettolohn").decimalValue() else null,
                    gespeicherterName,
                    aiResponse,
                    attachment,
                    email,
                    steuerberater,
                )
                erstellt++
            }
        }
        log.info("[Steuerberater] Sammel-PDF {} verarbeitet: {} Lohnabrechnung(en) erstellt, {} nicht zuordenbar", attachment.originalFilename, erstellt, nichtZugeordnet)
    }

    @Throws(IOException::class)
    private fun verarbeiteLohnabrechnungFallback(
        aiResponse: String?,
        fileBytes: ByteArray,
        attachment: EmailAttachment,
        email: Email,
        steuerberater: SteuerberaterKontakt,
    ) {
        val mitarbeiter = findMitarbeiterFromFilename(attachment.originalFilename)
        if (mitarbeiter == null) {
            log.warn("[Steuerberater] Auch im Dateinamen kein Mitarbeiter erkennbar: {} - manuell pruefen!", attachment.originalFilename)
            return
        }
        val periode = extractPeriodFromFilename(attachment.originalFilename)
        val gespeicherterName = speichereLohnabrechnungPdf(fileBytes)
        speichereLohnabrechnung(mitarbeiter, periode[0], periode[1], null, null, gespeicherterName, aiResponse, attachment, email, steuerberater)
    }

    private fun speichereLohnabrechnung(
        mitarbeiter: Mitarbeiter,
        jahr: Int?,
        monat: Int?,
        brutto: BigDecimal?,
        netto: BigDecimal?,
        gespeicherterDateiname: String,
        aiResponse: String?,
        attachment: EmailAttachment,
        email: Email,
        steuerberater: SteuerberaterKontakt,
    ) {
        val la = lohnabrechnungRepository
            .findByMitarbeiterIdAndJahrAndMonat(mitarbeiter.id, jahr, monat)
            .orElseGet { Lohnabrechnung() }
        val ersetzt = la.id != null
        if (ersetzt) loescheAlteSplitDatei(la.gespeicherterDateiname)

        la.mitarbeiter = mitarbeiter
        la.steuerberater = steuerberater
        la.jahr = jahr
        la.monat = monat
        la.originalDateiname = attachment.originalFilename
        la.gespeicherterDateiname = gespeicherterDateiname
        la.sourceEmail = email
        la.aiRawJson = aiResponse
        la.status = LohnabrechnungStatus.ANALYSIERT
        la.bruttolohn = brutto
        la.nettolohn = netto
        la.importDatum = LocalDateTime.now()

        lohnabrechnungRepository.save(la)
        log.info("[Steuerberater] Lohnabrechnung fuer {} ({}/{}) {}", mitarbeiter.nachname, monat, jahr, if (ersetzt) "aktualisiert (Korrektur)" else "erstellt")
    }

    private fun loescheAlteSplitDatei(gespeicherterDateiname: String?) {
        if (gespeicherterDateiname.isNullOrBlank()) return
        try {
            val basis = Paths.get(lohnabrechnungDir).toAbsolutePath().normalize()
            val datei = basis.resolve(gespeicherterDateiname).normalize()
            if (datei.startsWith(basis)) Files.deleteIfExists(datei)
        } catch (ex: IOException) {
            log.warn("[Steuerberater] Alte Lohnabrechnungs-PDF konnte nicht geloescht werden: {}", ex.message)
        }
    }

    @Throws(IOException::class)
    private fun speichereLohnabrechnungPdf(pdfBytes: ByteArray): String {
        val dir = Paths.get(lohnabrechnungDir)
        Files.createDirectories(dir)
        val dateiname = "${UUID.randomUUID()}.pdf"
        Files.write(dir.resolve(dateiname), pdfBytes)
        return dateiname
    }

    @Throws(IOException::class)
    private fun extrahiereSeiten(original: PDDocument, seitenRange: String?, totalPages: Int): ByteArray {
        var von = 1
        var bis = totalPages
        if (!seitenRange.isNullOrBlank()) {
            val matcher = Pattern.compile("(\\d+)\\s*(?:-\\s*(\\d+))?").matcher(seitenRange.trim())
            if (matcher.matches()) {
                von = matcher.group(1).toInt()
                bis = matcher.group(2)?.toInt() ?: von
            }
        }
        von = maxOf(1, minOf(von, totalPages))
        bis = maxOf(von, minOf(bis, totalPages))
        PDDocument().use { teil ->
            ByteArrayOutputStream().use { out ->
                for (i in von - 1 until bis) {
                    teil.addPage(original.getPage(i))
                }
                teil.save(out)
                return out.toByteArray()
            }
        }
    }

    private fun parseAiJson(aiResponse: String?): JsonNode? {
        if (aiResponse == null) return null
        return try {
            val json = aiResponse.replace("```json", "").replace("```", "").trim()
            if (json.startsWith("{")) objectMapper.readTree(json) else null
        } catch (ex: Exception) {
            log.warn("[Steuerberater] KI-Antwort kein gueltiges JSON: {}", ex.message)
            null
        }
    }

    private fun findMitarbeiterByName(name: String): Mitarbeiter? {
        val cleanName = name.lowercase()
        val aktive = mitarbeiterRepository.findByAktivTrue()
        for (mitarbeiter in aktive) {
            val nachname = mitarbeiter.nachname
            val vorname = mitarbeiter.vorname
            if (nachname != null && vorname != null && cleanName.contains(nachname.lowercase()) && cleanName.contains(vorname.lowercase())) {
                return mitarbeiter
            }
        }
        var einziger: Mitarbeiter? = null
        for (mitarbeiter in aktive) {
            val nachname = mitarbeiter.nachname
            if (nachname != null && cleanName.contains(nachname.lowercase())) {
                if (einziger != null) return null
                einziger = mitarbeiter
            }
        }
        return einziger
    }

    private fun extractPeriodFromFilename(filename: String?): Array<Int> {
        val currentYear = LocalDate.now().year
        val currentMonth = LocalDate.now().monthValue
        if (filename == null) return arrayOf(currentYear, currentMonth)

        val yearMonth = Pattern.compile("(20\\d{2})[_-]?(0[1-9]|1[0-2])").matcher(filename)
        if (yearMonth.find()) return arrayOf(yearMonth.group(1).toInt(), yearMonth.group(2).toInt())

        val monthYear = Pattern.compile("(0[1-9]|1[0-2])[_-](20\\d{2})").matcher(filename)
        if (monthYear.find()) return arrayOf(monthYear.group(2).toInt(), monthYear.group(1).toInt())

        val prevMonth = if (currentMonth == 1) 12 else currentMonth - 1
        val prevYear = if (currentMonth == 1) currentYear - 1 else currentYear
        return arrayOf(prevYear, prevMonth)
    }

    private fun findMitarbeiterFromFilename(filename: String?): Mitarbeiter? {
        if (filename == null) return null
        val cleanFilename = filename.lowercase().replace("[_\\-.]".toRegex(), " ").replace("\\s+".toRegex(), " ")
        for (ma in mitarbeiterRepository.findByAktivTrue()) {
            val nachname = ma.nachname?.lowercase() ?: continue
            val vorname = ma.vorname?.lowercase() ?: ""
            if (cleanFilename.contains(nachname)) return ma
            if (cleanFilename.contains(vorname) && cleanFilename.contains(nachname)) return ma
        }
        return null
    }

    private fun processBwaPdf(attachment: EmailAttachment, email: Email, steuerberater: SteuerberaterKontakt) {
        log.info("[Steuerberater] Verarbeite BWA: {}", attachment.originalFilename)
        val originalFilename = attachment.originalFilename ?: return
        val emailId = requireNotNull(email.id)
        if (bwaUploadRepository.existsBySourceEmailIdAndOriginalDateiname(emailId, originalFilename)) {
            log.info("[Steuerberater] BWA {} bereits importiert (Skipped)", attachment.originalFilename)
            return
        }

        try {
            val pdfPath = Paths.get(mailAttachmentDir, attachment.storedFilename)
            if (!Files.exists(pdfPath)) {
                log.error("Datei nicht gefunden: {}", pdfPath)
                return
            }
            val fileBytes = Files.readAllBytes(pdfPath)
            val aiPrompt = """
                Analysiere dieses PDF. Es handelt sich um eine BWA (Betriebswirtschaftliche Auswertung).
                Extrahiere folgende Daten als JSON:
                {
                    "monat": 1-12,
                    "jahr": YYYY,
                    "gesamtkosten": Betrag als Zahl (Summe Gesamtkosten),
                    "gemeinkosten": Betrag als Zahl (Summe Gemeinkosten, falls ausgewiesen),
                    "personalkosten": Betrag als Zahl
                }
            """.trimIndent()
            val aiResponse = geminiService.rufGeminiApiMitPrompt(fileBytes, "application/pdf", aiPrompt, true)

            var jahr: Int? = null
            var monat: Int? = null
            var gemeinkosten: BigDecimal? = null
            if (aiResponse != null) {
                val json = aiResponse.replace("```json", "").replace("```", "").trim()
                if (json.startsWith("{")) {
                    val root = objectMapper.readTree(json)
                    if (root.has("jahr")) jahr = root.get("jahr").asInt()
                    if (root.has("monat")) monat = root.get("monat").asInt()
                    if (root.has("gemeinkosten")) gemeinkosten = BigDecimal.valueOf(root.get("gemeinkosten").asDouble())
                }
            }
            if (jahr == null || monat == null) {
                val periode = extractPeriodFromFilename(attachment.originalFilename)
                if (jahr == null) jahr = periode[0]
                if (monat == null) monat = periode[1]
            }

            val bwa = BwaUpload().apply {
                typ = BwaTyp.MONATLICH
                this.jahr = jahr
                this.monat = monat
                originalDateiname = attachment.originalFilename
                gespeicherterDateiname = attachment.storedFilename
                uploadDatum = LocalDateTime.now()
                this.steuerberater = steuerberater
                sourceEmail = email
                if (gemeinkosten != null) {
                    kostenAusBwa = gemeinkosten
                    analysiert = true
                }
                aiRawJson = aiResponse
            }
            bwaUploadRepository.save(bwa)
            log.info("[Steuerberater] BWA fuer {}/{} erstellt (Gemeinkosten: {})", monat, jahr, gemeinkosten)
        } catch (ex: Exception) {
            log.error("[Steuerberater] Fehler bei BWA-Verarbeitung: {}", ex.message, ex)
            val periode = extractPeriodFromFilename(attachment.originalFilename)
            val bwa = BwaUpload().apply {
                typ = BwaTyp.MONATLICH
                jahr = periode[0]
                monat = periode[1]
                originalDateiname = attachment.originalFilename
                gespeicherterDateiname = attachment.storedFilename
                uploadDatum = LocalDateTime.now()
                this.steuerberater = steuerberater
                sourceEmail = email
            }
            bwaUploadRepository.save(bwa)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SteuerberaterEmailProcessingService::class.java)
    }
}
