package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.example.kalkulationsprogramm.util.EmailAiPostProcessor;
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAiService {

    private static final String REKLAMATION_SYSTEM_PROMPT =
            "Du bist ein professioneller Assistent für Handwerksbetriebe. Deine Aufgabe ist es, eine Reklamations-E-Mail an einen Lieferanten zu verfassen.\\n" +
            "WICHTIG:\\n" +
            "- Schreibe eine professionelle, aber bestimmte Reklamation.\\n" +
            "- Beschreibe die Mängel sachlich und klar.\\n" +
            "- Wenn Bilder mitgeliefert werden, beziehe dich auf die sichtbaren Mängel in den Bildern.\\n" +
            "- Fordere eine zeitnahe Stellungnahme oder Nachlieferung.\\n" +
            "- Der Text soll geschäftlich-professionell klingen (Sie-Form).\\n" +
            "- Strukturiere den Text mit HTML-Absätzen (<p>).\\n" +
            "- Entferne KEINE Grußformel — beginne mit 'Sehr geehrte Damen und Herren,' und ende mit 'Mit freundlichen Grüßen'.\\n" +
            "Antworte AUSSCHLIESSLICH mit einem JSON-Objekt im Format { \"subject\": \"...\", \"email\": \"...\" }, " +
            "wobei 'subject' ein passender Betreff und 'email' der vollständige E-Mail-Text als HTML (nur <p>-Tags) ist.";

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

    private final SystemSettingsService systemSettingsService;

    @Value("${ai.email.model:gemini-3-flash-preview}")
    private String model;

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

    /**
     * Generiert eine Reklamations-E-Mail basierend auf Beschreibung und Bildern.
     * Bilder werden als Base64 an Gemini gesendet (multimodal).
     *
     * @return JSON-String mit "subject" und "email" Feldern
     */
    public String generateReklamationEmail(String beschreibung, String lieferantName, List<Path> bildPaths)
            throws IOException, InterruptedException {
        if (!enabled) {
            throw new IOException("KI-Service ist deaktiviert");
        }
        if (beschreibung == null || beschreibung.isBlank()) {
            throw new IllegalArgumentException("Beschreibung darf nicht leer sein");
        }

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Erstelle eine Reklamations-E-Mail an den Lieferanten");
        if (lieferantName != null && !lieferantName.isBlank()) {
            userPrompt.append(" \"").append(lieferantName).append("\"");
        }
        userPrompt.append(".\n\nReklamationsbeschreibung:\n").append(beschreibung.trim()).append("\n");
        if (bildPaths != null && !bildPaths.isEmpty()) {
            userPrompt.append("\nAngehängt sind ").append(bildPaths.size()).append(" Foto(s) der beanstandeten Ware/Mängel. ")
                    .append("Beschreibe die sichtbaren Mängel in der E-Mail.\n");
        }

        try {
            String jsonResponse = rufGeminiApiWithImages(userPrompt.toString(), bildPaths);
            if (jsonResponse == null) {
                throw new IOException("Keine Antwort von KI erhalten");
            }

            JsonNode contentNode = objectMapper.readTree(jsonResponse);
            String subject = contentNode.has("subject") ? contentNode.get("subject").asText() : "Reklamation";
            String emailBody = contentNode.has("email") ? contentNode.get("email").asText() : jsonResponse;

            ObjectNode result = objectMapper.createObjectNode();
            result.put("subject", subject);
            result.put("body", emailBody);
            return objectMapper.writeValueAsString(result);

        } catch (Exception e) {
            log.error("Fehler bei Reklamations-Email-Generierung", e);
            throw new IOException("KI-Verarbeitung fehlgeschlagen: " + e.getMessage(), e);
        }
    }

    private String rufGeminiApiWithImages(String userMessage, List<Path> imagePaths) {
        try {
            if (systemSettingsService.getGeminiApiKey().isBlank()) {
                throw new IOException("Gemini API Key fehlt (ai.gemini.api-key)");
            }

            String url = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s".formatted(
                    model, systemSettingsService.getGeminiApiKey());

            ObjectNode requestBody = objectMapper.createObjectNode();

            // System Prompt
            ObjectNode systemInstruction = objectMapper.createObjectNode();
            ArrayNode sysParts = objectMapper.createArrayNode();
            sysParts.add(objectMapper.createObjectNode().put("text", REKLAMATION_SYSTEM_PROMPT));
            systemInstruction.set("parts", sysParts);
            requestBody.set("systemInstruction", systemInstruction);

            // User Content with text + images
            ArrayNode contents = objectMapper.createArrayNode();
            ObjectNode userMsg = objectMapper.createObjectNode().put("role", "user");
            ArrayNode parts = objectMapper.createArrayNode();

            // Text part
            parts.add(objectMapper.createObjectNode().put("text", userMessage));

            // Image parts (inline_data)
            if (imagePaths != null) {
                for (Path imgPath : imagePaths) {
                    if (Files.exists(imgPath) && Files.size(imgPath) < 10_000_000) {
                        byte[] imageBytes = Files.readAllBytes(imgPath);
                        String base64 = Base64.getEncoder().encodeToString(imageBytes);
                        String mimeType = Files.probeContentType(imgPath);
                        if (mimeType == null) mimeType = "image/jpeg";

                        ObjectNode inlineData = objectMapper.createObjectNode();
                        ObjectNode dataNode = objectMapper.createObjectNode();
                        dataNode.put("mimeType", mimeType);
                        dataNode.put("data", base64);
                        inlineData.set("inlineData", dataNode);
                        parts.add(inlineData);
                    }
                }
            }

            userMsg.set("parts", parts);
            contents.add(userMsg);
            requestBody.set("contents", contents);

            // Config
            ObjectNode config = objectMapper.createObjectNode();
            config.put("temperature", 0.4);
            config.put("responseMimeType", "application/json");
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
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode partsNode = candidates.get(0).path("content").path("parts");
                if (partsNode.isArray() && !partsNode.isEmpty()) {
                    return partsNode.get(0).path("text").asText();
                }
            }

            return null;
        } catch (Exception e) {
            log.error("Fehler beim Aufruf der Gemini API (multimodal)", e);
            throw new RuntimeException(e);
        }
    }

    private String cleanResult(String text) {
        if (text == null) return "";
        String plain = EmailHtmlSanitizer.htmlToPlainText(text);
        return EmailAiPostProcessor.sanitizePlainText(plain).trim();
    }

    private String rufGeminiApi(String userMessage) {
        try {
            if (systemSettingsService.getGeminiApiKey().isBlank()) {
                throw new IOException("Gemini API Key fehlt (ai.gemini.api-key)");
            }

            String url = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s".formatted(
                    model, systemSettingsService.getGeminiApiKey());

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
