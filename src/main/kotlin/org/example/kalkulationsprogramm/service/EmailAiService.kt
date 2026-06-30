package org.example.kalkulationsprogramm.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.example.kalkulationsprogramm.util.EmailAiPostProcessor
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Service
open class EmailAiService(
    private val objectMapper: ObjectMapper,
    private val systemSettingsService: SystemSettingsService,
) {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(60))
        .build()

    @Value("\${ai.email.model:gemini-3-flash-preview}")
    private lateinit var model: String

    @Value("\${ai.email.temperature:0.2}")
    private var temperature: Double = 0.2

    @Value("\${ai.email.enabled:true}")
    private var enabled: Boolean = true

    @Throws(IOException::class, InterruptedException::class)
    open fun beautify(originalText: String?): String? = beautify(originalText, null)

    @Throws(IOException::class, InterruptedException::class)
    open fun beautify(originalText: String?, replyContext: String?): String? {
        if (!enabled || originalText.isNullOrBlank()) {
            return originalText
        }

        val trimmed = normalizeLineEndings(originalText).trim()
        if (trimmed.isEmpty()) return ""

        val normalizedContext = if (replyContext == null) "" else normalizeLineEndings(replyContext).trim()

        val userPrompt = StringBuilder()
        userPrompt.append("Original-Text:\n").append(trimmed).append("\n")
        if (normalizedContext.isNotEmpty()) {
            userPrompt.append("\nKontext (Antwort auf vorherige E-Mail):\n").append(normalizedContext).append("\n")
            userPrompt.append(
                "\nHinweis: Beziehe dich auf den Kontext nur, um den Sinn zu verstehen. Veraendere nicht den Stil des Original-Textes deswegen.\n",
            )
        }
        userPrompt.append("\nOptimiere diesen Entwurf (Grammatik/Rechtschreibung), aber behalte den Stil bei.")

        try {
            val jsonResponse = rufGeminiApi(userPrompt.toString())
            if (jsonResponse == null) {
                log.warn("Keine Antwort von Gemini AI")
                throw IOException("Keine Antwort von KI erhalten")
            }

            return try {
                val contentNode = objectMapper.readTree(jsonResponse)
                when {
                    contentNode.has("email") -> cleanResult(contentNode.get("email").asText())
                    contentNode.has("text") -> cleanResult(contentNode.get("text").asText())
                    else -> cleanResult(jsonResponse)
                }
            } catch (e: Exception) {
                log.warn("Konnte KI-Antwort nicht als JSON parsen, verwende Roh-Antwort: {}", e.message)
                cleanResult(jsonResponse)
            }
        } catch (e: Exception) {
            log.error("Fehler bei Gemini-Aufruf", e)
            throw IOException("KI-Verarbeitung fehlgeschlagen: ${e.message}", e)
        }
    }

    private fun cleanResult(text: String?): String {
        if (text == null) return ""
        val plain = EmailHtmlSanitizer.htmlToPlainText(text)
        return EmailAiPostProcessor.sanitizePlainText(plain)?.trim() ?: ""
    }

    private fun rufGeminiApi(userMessage: String): String? {
        try {
            val geminiApiKey = systemSettingsService.geminiApiKey
            if (geminiApiKey.isNullOrBlank()) {
                throw IOException("Gemini API Key fehlt (ai.gemini.api-key)")
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"
                .format(model, geminiApiKey)

            val requestBody = objectMapper.createObjectNode()

            val systemInstruction = objectMapper.createObjectNode()
            val sysParts = objectMapper.createArrayNode()
            sysParts.add(objectMapper.createObjectNode().put("text", BASE_SYSTEM_PROMPT))
            systemInstruction.set<ArrayNode>("parts", sysParts)
            requestBody.set<ObjectNode>("systemInstruction", systemInstruction)

            val contents = objectMapper.createArrayNode()
            val userMsg = objectMapper.createObjectNode().put("role", "user")
            val parts = objectMapper.createArrayNode()
            parts.add(objectMapper.createObjectNode().put("text", userMessage))
            userMsg.set<ArrayNode>("parts", parts)
            contents.add(userMsg)
            requestBody.set<ArrayNode>("contents", contents)

            val config = objectMapper.createObjectNode()
            config.put("temperature", temperature)
            config.put("responseMimeType", "application/json")
            requestBody.set<ObjectNode>("generationConfig", config)

            val body = objectMapper.writeValueAsString(requestBody)
            val request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, Charsets.UTF_8))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            if (response.statusCode() != 200) {
                log.error("Gemini API Error {}: {}", response.statusCode(), response.body())
                throw IOException("Gemini API Error: ${response.statusCode()}")
            }

            val root = objectMapper.readTree(response.body())
            val candidates = root.path("candidates")
            if (candidates.isArray && !candidates.isEmpty) {
                val partsNode = candidates.get(0).path("content").path("parts")
                if (partsNode.isArray && !partsNode.isEmpty) {
                    return partsNode.get(0).path("text").asText()
                }
            }
            return null
        } catch (e: Exception) {
            log.error("Fehler beim Aufruf der Gemini API", e)
            throw RuntimeException(e)
        }
    }

    private fun normalizeLineEndings(value: String?): String =
        value?.replace("\r\n", "\n")?.replace("\r", "\n") ?: ""

    companion object {
        private val log = LoggerFactory.getLogger(EmailAiService::class.java)

        private const val BASE_SYSTEM_PROMPT =
            "Du bist ein Assistent, der E-Mails verbessert. Deine Aufgabe ist es, Rechtschreibung und Grammatik zu korrigieren.\\n" +
                "WICHTIG: Behalte den urspruenglichen Schreibstil und Tonfall des Nutzers UNBEDINGT bei!\\n" +
                "- Wenn der Nutzer 'Du' schreibt, bleibe beim 'Du'.\\n" +
                "- Wenn der Nutzer formell 'Sie' schreibt, bleibe formell.\\n" +
                "- Wenn der Text salopp/kurz ist (z.B. 'Passt morgen?'), verbessere nur die Fehler, aber mache daraus keinen Roman.\\n" +
                "- Der Text darf NICHT nach einer kuenstlichen Intelligenz klingen.\\n" +
                "- Strukturiere den Text mit HTML-Absaetzen (<p>), um die Lesbarkeit zu verbessern.\\n" +
                "Entferne Signaturen, Grussformeln und den Namen des Absenders am Ende vollstaendig (z.B. 'LG Marvin', 'Marvin Kuhn', 'Mit freundlichen Gruessen'), da diese vom Programm automatisch angefuegt werden. Der Text soll ohne Schlussformel enden.\\n" +
                "Antworte AUSSCHLIESSLICH mit einem JSON-Objekt im Format { \"email\": \"...\" }, wobei der Wert der verbesserte E-Mail-Text als HTML (ohne <html>/<body> Tags, nur <p>...) ist."
    }
}
