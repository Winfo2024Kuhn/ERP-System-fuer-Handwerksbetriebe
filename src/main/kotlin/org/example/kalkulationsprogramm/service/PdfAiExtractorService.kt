package org.example.kalkulationsprogramm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.Optional
import javax.imageio.ImageIO

@Service
class PdfAiExtractorService(
    private val objectMapper: ObjectMapper,
    private val systemSettingsService: SystemSettingsService,
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    @Value("\${ai.pdf.backend:gemini}")
    private lateinit var aiBackend: String

    @Value("\${ai.gemini.model.pdf-extractor:gemini-3-flash-preview}")
    private lateinit var geminiModel: String

    @Value("\${ai.pdf.timeout-seconds:180}")
    private var timeoutSeconds: Int = 180

    @Value("\${ai.pdf.use-vision:false}")
    private var useVision: Boolean = false

    fun analyze(pdfPath: String): Optional<ZugferdDaten> = analyze(pdfPath, "RECHNUNG")

    fun analyze(pdfPath: String, docType: String?): Optional<ZugferdDaten> {
        val prompt = if (docType?.lowercase()?.contains("angebot") == true) {
            SYSTEM_PROMPT_OFFER
        } else {
            SYSTEM_PROMPT_INVOICE
        }
        return if (aiBackend.equals("gemini", ignoreCase = true)) {
            analyzeWithGemini(pdfPath, prompt)
        } else {
            analyzeWithOllama(pdfPath, prompt)
        }
    }

    private fun analyzeWithGemini(pdfPath: String, systemPrompt: String): Optional<ZugferdDaten> {
        return try {
            val path = Path.of(pdfPath)
            if (!Files.exists(path)) {
                log.warn("[PdfAI/Gemini] PDF nicht gefunden: {}", pdfPath)
                return Optional.empty()
            }
            val pdfBytes = Files.readAllBytes(path)
            val base64Pdf = Base64.getEncoder().encodeToString(pdfBytes)
            log.info("[PdfAI/Gemini] PDF geladen: {} ({} KB)", pdfPath, pdfBytes.size / 1024)
            callGeminiApiWithPdf(base64Pdf, systemPrompt)
        } catch (e: Exception) {
            log.warn("[PdfAI/Gemini] Fehler: {}", e.message)
            Optional.empty()
        }
    }

    private fun callGeminiApiWithPdf(base64Pdf: String, systemPrompt: String): Optional<ZugferdDaten> {
        return try {
            val geminiApiKey = systemSettingsService.geminiApiKey
            if (geminiApiKey.isBlank() || geminiApiKey == "DEIN_API_KEY_HIER") {
                log.error("[PdfAI/Gemini] Kein API-Key! Im System-Setup hinterlegen.")
                return Optional.empty()
            }

            val requestBody = objectMapper.createObjectNode()
            val systemInstruction = objectMapper.createObjectNode()
            val systemPartsArray = objectMapper.createArrayNode()
            val systemTextPart = objectMapper.createObjectNode()
            systemTextPart.put("text", systemPrompt)
            systemPartsArray.add(systemTextPart)
            systemInstruction.set<JsonNode>("parts", systemPartsArray)
            requestBody.set<JsonNode>("systemInstruction", systemInstruction)

            val contents = objectMapper.createArrayNode()
            val userContent = objectMapper.createObjectNode()
            userContent.put("role", "user")
            val parts = objectMapper.createArrayNode()
            val pdfPart = objectMapper.createObjectNode()
            val inlineData = objectMapper.createObjectNode()
            inlineData.put("mimeType", "application/pdf")
            inlineData.put("data", base64Pdf)
            pdfPart.set<JsonNode>("inlineData", inlineData)
            parts.add(pdfPart)
            val textPart = objectMapper.createObjectNode()
            textPart.put("text", "Analysiere dieses PDF-Dokument und extrahiere die Daten gemäß den Anweisungen.")
            parts.add(textPart)
            userContent.set<JsonNode>("parts", parts)
            contents.add(userContent)
            requestBody.set<JsonNode>("contents", contents)

            val generationConfig = objectMapper.createObjectNode()
            generationConfig.put("temperature", 0.1)
            generationConfig.put("maxOutputTokens", 8192)
            requestBody.set<JsonNode>("generationConfig", generationConfig)

            val body = objectMapper.writeValueAsString(requestBody)
            val apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent?key=$geminiApiKey"
            val request = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(maxOf(90, timeoutSeconds).toLong()))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build()

            log.info("[PdfAI/Gemini] Sende PDF direkt an Gemini API (Modell: {})", geminiModel)
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            if (response.statusCode() !in 200..299) {
                log.warn("[PdfAI/Gemini] API Fehler {}: {}", response.statusCode(), response.body())
                return Optional.empty()
            }
            parseGeminiResponse(response.body())
        } catch (e: Exception) {
            log.warn("[PdfAI/Gemini] Anfrage-Fehler: {}", e.message)
            Optional.empty()
        }
    }

    private fun parseGeminiResponse(responseBody: String): Optional<ZugferdDaten> {
        return try {
            log.info(
                "[PdfAI/Gemini] Raw Response (gekürzt): {}",
                if (responseBody.length > 2000) responseBody.substring(0, 2000) + "..." else responseBody,
            )
            val responseJson = objectMapper.readTree(responseBody)
            val errorNode = responseJson.path("error")
            if (!errorNode.isMissingNode) {
                log.error("[PdfAI/Gemini] API-Fehler: {}", errorNode)
                return Optional.empty()
            }

            val candidates = responseJson.path("candidates")
            if (!candidates.isArray || candidates.isEmpty) {
                val promptFeedback = responseJson.path("promptFeedback")
                if (!promptFeedback.isMissingNode) {
                    log.error("[PdfAI/Gemini] Anfrage blockiert! PromptFeedback: {}", promptFeedback)
                }
                log.warn("[PdfAI/Gemini] Keine Candidates in Antwort: {}", responseBody)
                return Optional.empty()
            }

            val firstCandidate = candidates.get(0) ?: return Optional.empty()
            val finishReason = firstCandidate.path("finishReason").asText("UNKNOWN")
            log.info("[PdfAI/Gemini] finishReason: {}", finishReason)
            val safetyRatings = firstCandidate.path("safetyRatings")
            if (!safetyRatings.isMissingNode && safetyRatings.isArray) {
                for (rating in safetyRatings) {
                    val category = rating.path("category").asText()
                    val probability = rating.path("probability").asText()
                    if (probability != "NEGLIGIBLE" && probability != "LOW") {
                        log.warn("[PdfAI/Gemini] Safety-Warnung: {} = {}", category, probability)
                    }
                }
            }

            val partsNode = firstCandidate.path("content").path("parts")
            if (!partsNode.isArray || partsNode.isEmpty) {
                log.warn("[PdfAI/Gemini] Keine Parts in Antwort. FinishReason: {}, Candidate: {}", finishReason, firstCandidate)
                return Optional.empty()
            }
            var content = partsNode.get(0)?.path("text")?.asText() ?: ""
            if (content.isBlank()) {
                log.warn("[PdfAI/Gemini] Leere Antwort")
                return Optional.empty()
            }
            content = cleanJsonResponse(content)
            log.info("[PdfAI/Gemini] Bereinigte Antwort: {}", content)
            Optional.of(mapToZugferdDaten(objectMapper.readTree(content)))
        } catch (e: Exception) {
            log.warn("[PdfAI/Gemini] Parse-Fehler: {}", e.message)
            Optional.empty()
        }
    }

    private fun analyzeWithOllama(pdfPath: String, systemPrompt: String): Optional<ZugferdDaten> {
        if (useVision) {
            val visionResult = analyzeWithOllamaVision(pdfPath, systemPrompt)
            if (visionResult.isPresent) return visionResult
        }
        return analyzeWithOllamaText(pdfPath, systemPrompt)
    }

    private fun analyzeWithOllamaVision(pdfPath: String, systemPrompt: String): Optional<ZugferdDaten> {
        return try {
            if (convertPdfToBase64Image(pdfPath) == null) Optional.empty() else {
                log.info("[PdfAI/Ollama] Vision wird derzeit nicht unterstützt")
                Optional.empty()
            }
        } catch (_: Exception) {
            Optional.empty()
        }
    }

    private fun analyzeWithOllamaText(pdfPath: String, systemPrompt: String): Optional<ZugferdDaten> {
        return try {
            var pdfText = extractTextFromPdf(pdfPath)
            if (pdfText.isBlank()) return Optional.empty()
            if (pdfText.length > MAX_TEXT_LENGTH) {
                pdfText = pdfText.substring(0, MAX_TEXT_LENGTH)
            }
            callOllamaChat(pdfText, systemPrompt)
        } catch (e: Exception) {
            log.warn("[PdfAI/Ollama] Fehler: {}", e.message)
            Optional.empty()
        }
    }

    private fun callOllamaChat(pdfText: String, systemPrompt: String): Optional<ZugferdDaten> {
        return try {
            val payload = objectMapper.createObjectNode()
            payload.put("model", "llama3.2")
            payload.put("stream", false)
            val messages = objectMapper.createArrayNode()
            messages.addObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            }
            messages.addObject().apply {
                put("role", "user")
                put("content", "Hier ist der PDF-Text:\n\n$pdfText")
            }
            payload.set<JsonNode>("messages", messages)
            payload.set<JsonNode>("options", objectMapper.createObjectNode().put("temperature", 0.1))
            val body = objectMapper.writeValueAsString(payload)
            val request = HttpRequest.newBuilder(URI.create("http://localhost:11434/api/chat"))
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(timeoutSeconds.toLong()))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build()
            log.info("[PdfAI/Ollama] Sende Anfrage")
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            if (response.statusCode() !in 200..299) {
                log.warn("[PdfAI/Ollama] Status {}", response.statusCode())
                return Optional.empty()
            }
            parseOllamaResponse(response.body())
        } catch (_: java.net.ConnectException) {
            log.info("[PdfAI/Ollama] Nicht erreichbar")
            Optional.empty()
        } catch (e: Exception) {
            log.warn("[PdfAI/Ollama] Fehler: {}", e.message)
            Optional.empty()
        }
    }

    private fun parseOllamaResponse(responseBody: String): Optional<ZugferdDaten> {
        return try {
            val responseJson = objectMapper.readTree(responseBody)
            var content = responseJson.path("message").path("content").asText()
            if (content.isBlank()) return Optional.empty()
            content = cleanJsonResponse(content)
            Optional.of(mapToZugferdDaten(objectMapper.readTree(content)))
        } catch (_: Exception) {
            Optional.empty()
        }
    }

    @Throws(IOException::class)
    private fun extractTextFromPdf(pdfPath: String): String {
        val path = Path.of(pdfPath)
        if (!Files.exists(path)) throw IOException("PDF nicht gefunden: $pdfPath")
        Loader.loadPDF(path.toFile()).use { document ->
            return PDFTextStripper().getText(document)
        }
    }

    @Throws(IOException::class)
    private fun convertPdfToBase64Image(pdfPath: String): String? {
        val path = Path.of(pdfPath)
        if (!Files.exists(path)) return null
        Loader.loadPDF(path.toFile()).use { document ->
            val renderer = PDFRenderer(document)
            val image = renderer.renderImageWithDPI(0, PDF_RENDER_DPI.toFloat(), ImageType.RGB)
            val baos = ByteArrayOutputStream()
            ImageIO.write(image, "png", baos)
            return Base64.getEncoder().encodeToString(baos.toByteArray())
        }
    }

    private fun cleanJsonResponse(content: String): String {
        var cleaned = content.trim()
        cleaned = when {
            cleaned.startsWith("```json") -> cleaned.substring(7)
            cleaned.startsWith("```") -> cleaned.substring(3)
            else -> cleaned
        }
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length - 3)
        return cleaned.trim()
    }

    private fun mapToZugferdDaten(json: JsonNode): ZugferdDaten {
        val daten = ZugferdDaten()
        json.path("dokumenttyp").takeIf { it.isTextual && it.asText().isNotBlank() }?.let {
            daten.geschaeftsdokumentart = it.asText().trim()
        }
        json.path("dokumentnummer").takeIf { it.isTextual && it.asText().isNotBlank() }?.let {
            daten.rechnungsnummer = it.asText().trim()
        }
        json.path("rechnungsdatum").takeIf { it.isTextual && it.asText().isNotBlank() }?.let {
            daten.rechnungsdatum = parseDate(it.asText())
        }
        json.path("faelligkeitsdatum").takeIf { it.isTextual && it.asText().isNotBlank() }?.let {
            daten.faelligkeitsdatum = parseDate(it.asText())
        }
        val betragNode = json.path("betrag")
        if (betragNode.isNumber) {
            daten.betrag = BigDecimal.valueOf(betragNode.asDouble())
        } else if (betragNode.isTextual && betragNode.asText().isNotBlank()) {
            try {
                daten.betrag = BigDecimal(betragNode.asText().replace(",", ".").replace(Regex("[^0-9.]"), ""))
            } catch (_: NumberFormatException) {
            }
        }
        json.path("kundenname").takeIf { it.isTextual && it.asText().isNotBlank() }?.let {
            daten.kundenName = it.asText().trim()
        }
        json.path("kundennummer").takeIf { it.isTextual && it.asText().isNotBlank() }?.let {
            daten.kundennummer = it.asText().trim()
        }
        return daten
    }

    private fun parseDate(dateStr: String?): LocalDate? {
        if (dateStr.isNullOrBlank()) return null
        val patterns = arrayOf("yyyy-MM-dd", "dd.MM.yyyy", "dd/MM/yyyy", "yyyyMMdd")
        for (pattern in patterns) {
            try {
                return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern(pattern))
            } catch (_: DateTimeParseException) {
            }
        }
        val normalized = dateStr.replace(Regex("[^0-9]"), "")
        if (normalized.length >= 8) {
            try {
                return LocalDate.parse(normalized.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE)
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    companion object {
        private val log = LoggerFactory.getLogger(PdfAiExtractorService::class.java)
        private const val MAX_TEXT_LENGTH = 15_000
        private const val PDF_RENDER_DPI = 150

        private const val SYSTEM_PROMPT_INVOICE = """
            Du bist ein präziser Dokumentenanalysator für Geschäftsdokumente der Firma Thomas Kuhn Bauschlosserei.
            Gib deine Antwort NUR als gültiges JSON zurück, ohne Erklärungen oder Markdown-Codeblöcke.
            Format:
            {
              "dokumenttyp": "Dokumenttyp (ggf. mit Vermerk ' (Kopie)')",
              "dokumentnummer": "nur die Nummer, z.B. 2025/07/00004",
              "rechnungsdatum": "YYYY-MM-DD",
              "faelligkeitsdatum": "YYYY-MM-DD",
              "betrag": 1234.56
            }
            Falls ein Wert nicht erkennbar ist, setze null.
            Antworte NUR mit dem JSON, nichts anderes.
            """

        private const val SYSTEM_PROMPT_OFFER = """
            Du bist ein präziser Dokumentenanalysator für Angebote und Auftragsbestätigungen der Firma Thomas Kuhn Bauschlosserei.
            Prüfe das Dokument EXTREM SORGFÄLTIG auf Wasserzeichen oder großflächige Texte wie "Abschrift", "Kopie", "Entwurf" oder "Duplikat".
            Falls erkannt, hänge zwingend " (Kopie)" an den Dokumenttyp an.
            Gib deine Antwort NUR als gültiges JSON zurück.
            Format:
            {
              "dokumenttyp": "Dokumenttyp (ggf. mit Vermerk ' (Kopie)')",
              "dokumentnummer": "die Nummer",
              "datum": "YYYY-MM-DD",
              "betrag": 1234.56,
              "kundenName": "Name des Kunden",
              "kundenNummer": "Kundennummer"
            }
            "rechnungsdatum" entspricht dabei dem Angebotsdatum.
            Antworte NUR mit dem JSON.
            """
    }
}
