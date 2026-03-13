package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.util.EmailAiPostProcessor;
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;


@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAiService {

    private static final String BASE_SYSTEM_PROMPT =
            "Du bist ein Assistent, der E-Mails verbessert. Deine Aufgabe ist es, Rechtschreibung und Grammatik zu korrigieren.\\n" +
            "WICHTIG: Behalte den ursprünglichen Schreibstil und Tonfall des Nutzers UNBEDINGT bei!\\n" +
            "- Wenn der Nutzer 'Du' schreibt, bleibe beim 'Du'.\\n" +
            "- Wenn der Nutzer formell 'Sie' schreibt, bleibe formell.\\n" +
            "- Wenn der Text salopp/kurz ist (z.B. 'Passt morgen?'), verbessere nur die Fehler, aber mache daraus keinen Roman.\\n" +
            "- Der Text darf NICHT nach einer künstlichen Intelligenz klingen.\\n" +
            "- Strukturiere den Text mit HTML-Absätzen (<p>), um die Lesbarkeit zu verbessern.\\n" +
            "Entferne Signaturen, Grußformeln und den Namen des Absenders am Ende vollständig (z.B. 'LG Marvin', 'Marvin Kuhn', 'Mit freundlichen Grüßen'), da diese vom Programm automatisch angefügt werden. Der Text soll ohne Schlussformel enden.\\n" +
            "Antworte AUSSCHLIESSLICH mit einem JSON-Objekt im Format { \"email\": \"...\" }, wobei der Wert der verbesserte E-Mail-Text als HTML (ohne <html>/<body> Tags, nur <p>...) ist.";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();

    @Value("${ai.email.model:gemini-3-flash-preview}")
    private String model;

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.email.temperature:0.2}")
    private double temperature;

    @Value("${ai.email.enabled:true}")
    private boolean enabled;

    public String beautify(String originalText) throws IOException, InterruptedException {
        return beautify(originalText, null);
    }

    public String beautify(String originalText, String replyContext) throws IOException, InterruptedException {
        if (!enabled || originalText == null || originalText.isBlank()) {
            return originalText;
        }

        String trimmed = normalizeLineEndings(originalText).trim();
        if (trimmed.isEmpty()) return "";

        String normalizedContext = replyContext == null ? "" : normalizeLineEndings(replyContext).trim();

        // Build User Prompt
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Original-Text:\n").append(trimmed).append("\n");
        if (!normalizedContext.isEmpty()) {
            // Context helps Gemini understand what we are replying to
            userPrompt.append("\nKontext (Antwort auf vorherige E-Mail):\n").append(normalizedContext).append("\n");
            userPrompt.append("\nHinweis: Beziehe dich auf den Kontext nur, um den Sinn zu verstehen. Verändere nicht den Stil des Original-Textes deswegen.\n");
        }
        userPrompt.append("\nOptimiere diesen Entwurf (Grammatik/Rechtschreibung), aber behalte den Stil bei.");

        try {
            String jsonResponse = rufGeminiApi(userPrompt.toString());
            if (jsonResponse == null) {
                log.warn("Keine Antwort von Gemini AI");
                throw new IOException("Keine Antwort von KI erhalten");
            }
            
            // Die Antwort ist selbst ein JSON-String, wir müssen ihn parsen
            // Format erwartet: { "email": "..." }
            try {
                JsonNode contentNode = objectMapper.readTree(jsonResponse);
                if (contentNode.has("email")) {
                    String optimizedEmail = contentNode.get("email").asText();
                    return cleanResult(optimizedEmail);
                } else if (contentNode.has("text")) {
                     String optimizedEmail = contentNode.get("text").asText();
                     return cleanResult(optimizedEmail);
                } else {
                     // Fallback: vielleicht ist es kein JSON sondern direkt Text?
                     return cleanResult(jsonResponse);
                }
            } catch (Exception e) {
                // Falls Parsing des Inner-JSON fehlschlägt, nehmen wir an es ist Plain Text
                log.warn("Konnte KI-Antwort nicht als JSON parsen, verwende Roh-Antwort: {}", e.getMessage());
                return cleanResult(jsonResponse);
            }

        } catch (Exception e) {
            log.error("Fehler bei Gemini-Aufruf", e);
            throw new IOException("KI-Verarbeitung fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private String cleanResult(String text) {
        if (text == null) return "";
        String plain = EmailHtmlSanitizer.htmlToPlainText(text);
        return EmailAiPostProcessor.sanitizePlainText(plain).trim();
    }

    private String rufGeminiApi(String userMessage) {
        try {
            if (geminiApiKey == null || geminiApiKey.isBlank()) {
                throw new IOException("Gemini API Key fehlt (ai.gemini.api-key)");
            }

            String url = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s".formatted(
                    model, geminiApiKey);

            ObjectNode requestBody = objectMapper.createObjectNode();

            // System Prompt
            ObjectNode systemInstruction = objectMapper.createObjectNode();
            ArrayNode sysParts = objectMapper.createArrayNode();
            sysParts.add(objectMapper.createObjectNode().put("text", BASE_SYSTEM_PROMPT));
            systemInstruction.set("parts", sysParts);
            requestBody.set("systemInstruction", systemInstruction);

            // User Content
            ArrayNode contents = objectMapper.createArrayNode();
            ObjectNode userMsg = objectMapper.createObjectNode().put("role", "user");
            ArrayNode parts = objectMapper.createArrayNode();
            parts.add(objectMapper.createObjectNode().put("text", userMessage));
            userMsg.set("parts", parts);
            contents.add(userMsg);
            requestBody.set("contents", contents);

            // Config
            ObjectNode config = objectMapper.createObjectNode();
            config.put("temperature", temperature);
            config.put("responseMimeType", "application/json"); // Force JSON output
            requestBody.set("generationConfig", config);

            String body = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.error("Gemini API Error {}: {}", response.statusCode(), response.body());
                throw new IOException("Gemini API Error: " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            
            // Extract text from Gemini response structure
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode partsNode = candidates.get(0).path("content").path("parts");
                if (partsNode.isArray() && !partsNode.isEmpty()) {
                    return partsNode.get(0).path("text").asText();
                }
            }
            
            return null;

        } catch (Exception e) {
            log.error("Fehler beim Aufruf der Gemini API", e);
            throw new RuntimeException(e);
        }
    }

    private String normalizeLineEndings(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace("\r", "\n");
    }
}
