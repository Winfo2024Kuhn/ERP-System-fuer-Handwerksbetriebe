package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;

/**
 * Service zur KI-gestützten Analyse von PDF-Dokumenten.
 * Unterstützt Google Gemini API (PDF direkt) und Ollama (Fallback).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfAiExtractorService {

    private static final String SYSTEM_PROMPT_INVOICE = """
            Du bist ein präziser Dokumentenanalysator für Geschäftsdokumente der Firma Thomas Kuhn Bauschlosserei.

            DOKUMENTFORMAT-ERKENNUNG (Thomas Kuhn Rechnungen):
            - Rechnungsnummer steht als "Rechnung YYYY/MM/NNNNN" (z.B. "Rechnung 2025/07/00004")
            - Datum steht bei "Datum:" (z.g. "Datum: 09.07.2025")
            - Fälligkeitsdatum steht bei "Zahlbar bis spätestens DD.MM.YYYY"
            - Rechnungsbetrag steht bei "Restsumme" oder "Gesamtsumme"
            - Kunden-Nr. und Projekt-Nr. sind ebenfalls angegeben

            DOKUMENTTYP-ERKENNUNG:
            - "Rechnung" wenn "Rechnung YYYY/MM/NNNNN" vorkommt
            - "Angebot" wenn "Angebot" oder "Angebotsnummer" vorkommt
            - "Auftragsbestätigung" wenn "Auftragsbestätigung" oder "AB-" vorkommt
            - "Zahlungserinnerung" wenn "Zahlungserinnerung" vorkommt
            - "1. Mahnung" wenn "1. Mahnung" vorkommt
            - "2. Mahnung" wenn "2. Mahnung" oder "letzte Mahnung" vorkommt

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
            """;

    private static final String SYSTEM_PROMPT_OFFER = """
            Du bist ein präziser Dokumentenanalysator für Angebote und Auftragsbestätigungen der Firma Thomas Kuhn Bauschlosserei.

            !!! KRITISCHE PRÜFUNG: DOKUMENT-GÜLTIGKEIT !!!
            Prüfe das Dokument EXTREM SORGFÄLTIG auf Wasserzeichen oder großflächige Texte wie "Abschrift", "Kopie", "Entwurf" oder "Duplikat".
            Falls erkannt, hänge zwingend " (Kopie)" an den Dokumenttyp an.

            DOKUMENTTYP-ERKENNUNG:
            - "Angebot" wenn "Angebot" oder "Angebotsnummer" vorkommt
            - "Auftragsbestätigung" wenn "Auftragsbestätigung" oder "AB-" vorkommt

            EXTRAHIERE FOLGENDE DATEN:
            - Nummer: Angebotsnummer (z.B. 2025/AG/001) oder AB-Nummer
            - Datum: Angebotsdatum oder Datum der Auftragsbestätigung
            - Betrag: Gesamtbetrag Brutto
            - Kundenname und Kundennummer

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
            """;

    private static final int MAX_TEXT_LENGTH = 15_000;
    private static final int PDF_RENDER_DPI = 150;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ========== KONFIGURATION ==========
    // Backend: "gemini" oder "ollama"
    @Value("${ai.pdf.backend:gemini}")
    private String aiBackend;

    // ========== GEMINI API ==========
    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.gemini.model.pdf-extractor:gemini-3-flash-preview}")
    private String geminiModel;

    // ========== OLLAMA (Fallback) ==========
    @Value("${ai.pdf.timeout-seconds:180}")
    private int timeoutSeconds;

    @Value("${ai.pdf.use-vision:false}")
    private boolean useVision;

    /**
     * Analysiert einen PDF-Pfad mittels KI und extrahiert Dokumentdaten.
     */
    public Optional<ZugferdDaten> analyze(String pdfPath) {
        return analyze(pdfPath, "RECHNUNG");
    }

    public Optional<ZugferdDaten> analyze(String pdfPath, String docType) {
        String prompt = (docType != null && docType.toLowerCase().contains("angebot"))
                ? SYSTEM_PROMPT_OFFER
                : SYSTEM_PROMPT_INVOICE;

        if ("gemini".equalsIgnoreCase(aiBackend)) {
            return analyzeWithGemini(pdfPath, prompt);
        } else {
            return analyzeWithOllama(pdfPath, prompt);
        }
    }

    // ==================== GEMINI API (PDF direkt als Base64) ====================

    private Optional<ZugferdDaten> analyzeWithGemini(String pdfPath, String systemPrompt) {
        try {
            Path path = Path.of(pdfPath);
            if (!Files.exists(path)) {
                log.warn("[PdfAI/Gemini] PDF nicht gefunden: {}", pdfPath);
                return Optional.empty();
            }

            // PDF direkt als Base64 lesen
            byte[] pdfBytes = Files.readAllBytes(path);
            String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);

            log.info("[PdfAI/Gemini] PDF geladen: {} ({} KB)", pdfPath, pdfBytes.length / 1024);
            return callGeminiApiWithPdf(base64Pdf, systemPrompt);

        } catch (Exception e) {
            log.warn("[PdfAI/Gemini] Fehler: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ZugferdDaten> callGeminiApiWithPdf(String base64Pdf, String systemPrompt) {
        try {
            // API Key Prüfung
            if (geminiApiKey == null || geminiApiKey.isBlank() || geminiApiKey.equals("DEIN_API_KEY_HIER")) {
                log.error("[PdfAI/Gemini] Kein API-Key! Setze in application.properties: ai.gemini.api-key=...");
                return Optional.empty();
            }

            // Request Body erstellen
            ObjectNode requestBody = objectMapper.createObjectNode();

            // System Instruction
            ObjectNode systemInstruction = objectMapper.createObjectNode();
            ArrayNode systemPartsArray = objectMapper.createArrayNode();
            ObjectNode systemTextPart = objectMapper.createObjectNode();
            systemTextPart.put("text", systemPrompt);
            systemPartsArray.add(systemTextPart);
            systemInstruction.set("parts", systemPartsArray);
            requestBody.set("systemInstruction", systemInstruction);

            // Contents: PDF als inline_data + Text
            ArrayNode contents = objectMapper.createArrayNode();
            ObjectNode userContent = objectMapper.createObjectNode();
            userContent.put("role", "user");

            ArrayNode parts = objectMapper.createArrayNode();

            // PDF als inline_data (direkt an Gemini senden)
            ObjectNode pdfPart = objectMapper.createObjectNode();
            ObjectNode inlineData = objectMapper.createObjectNode();
            inlineData.put("mimeType", "application/pdf");
            inlineData.put("data", base64Pdf);
            pdfPart.set("inlineData", inlineData);
            parts.add(pdfPart);

            // Text-Anweisung
            ObjectNode textPart = objectMapper.createObjectNode();
            textPart.put("text", "Analysiere dieses PDF-Dokument und extrahiere die Daten gemäß den Anweisungen.");
            parts.add(textPart);

            userContent.set("parts", parts);
            contents.add(userContent);
            requestBody.set("contents", contents);

            // Generation Config
            ObjectNode generationConfig = objectMapper.createObjectNode();
            generationConfig.put("temperature", 0.1);
            generationConfig.put("maxOutputTokens", 8192);
            requestBody.set("generationConfig", generationConfig);

            String body = objectMapper.writeValueAsString(requestBody);

            // API URL
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s".formatted(
                    geminiModel, geminiApiKey);

            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(Math.max(90, timeoutSeconds)))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            log.info("[PdfAI/Gemini] Sende PDF direkt an Gemini API (Modell: {})", geminiModel);
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[PdfAI/Gemini] API Fehler {}: {}", response.statusCode(), response.body());
                return Optional.empty();
            }

            return parseGeminiResponse(response.body());

        } catch (Exception e) {
            log.warn("[PdfAI/Gemini] Anfrage-Fehler: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ZugferdDaten> parseGeminiResponse(String responseBody) {
        try {
            log.info("[PdfAI/Gemini] Raw Response (gekürzt): {}",
                    responseBody.length() > 2000 ? responseBody.substring(0, 2000) + "..." : responseBody);
            JsonNode responseJson = objectMapper.readTree(responseBody);

            // Prüfe auf API-Fehler
            JsonNode errorNode = responseJson.path("error");
            if (!errorNode.isMissingNode()) {
                log.error("[PdfAI/Gemini] API-Fehler: {}", errorNode);
                return Optional.empty();
            }

            JsonNode candidates = responseJson.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                // Prüfe auf promptFeedback (blockierte Anfrage)
                JsonNode promptFeedback = responseJson.path("promptFeedback");
                if (!promptFeedback.isMissingNode()) {
                    log.error("[PdfAI/Gemini] Anfrage blockiert! PromptFeedback: {}", promptFeedback);
                }
                log.warn("[PdfAI/Gemini] Keine Candidates in Antwort: {}", responseBody);
                return Optional.empty();
            }

            JsonNode firstCandidate = candidates.get(0);
            if (firstCandidate == null) {
                log.warn("[PdfAI/Gemini] Erster Candidate ist null");
                return Optional.empty();
            }

            // Prüfe finishReason - zeigt warum Gemini gestoppt hat
            String finishReason = firstCandidate.path("finishReason").asText("UNKNOWN");
            log.info("[PdfAI/Gemini] finishReason: {}", finishReason);

            // Prüfe safetyRatings
            JsonNode safetyRatings = firstCandidate.path("safetyRatings");
            if (!safetyRatings.isMissingNode() && safetyRatings.isArray()) {
                for (JsonNode rating : safetyRatings) {
                    String category = rating.path("category").asText();
                    String probability = rating.path("probability").asText();
                    if (!"NEGLIGIBLE".equals(probability) && !"LOW".equals(probability)) {
                        log.warn("[PdfAI/Gemini] Safety-Warnung: {} = {}", category, probability);
                    }
                }
            }

            JsonNode contentNode = firstCandidate.path("content");
            JsonNode partsNode = contentNode.path("parts");

            if (!partsNode.isArray() || partsNode.isEmpty()) {
                log.warn("[PdfAI/Gemini] Keine Parts in Antwort. FinishReason: {}, Candidate: {}",
                        finishReason, firstCandidate);
                return Optional.empty();
            }

            JsonNode firstPart = partsNode.get(0);
            if (firstPart == null) {
                log.warn("[PdfAI/Gemini] Erster Part ist null");
                return Optional.empty();
            }

            String content = firstPart.path("text").asText();

            if (content == null || content.isBlank()) {
                log.warn("[PdfAI/Gemini] Leere Antwort");
                return Optional.empty();
            }

            content = cleanJsonResponse(content);
            log.info("[PdfAI/Gemini] Bereinigte Antwort: {}", content);

            JsonNode dataJson = objectMapper.readTree(content);
            return Optional.of(mapToZugferdDaten(dataJson));

        } catch (Exception e) {
            log.warn("[PdfAI/Gemini] Parse-Fehler: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ==================== OLLAMA (Fallback) ====================

    private Optional<ZugferdDaten> analyzeWithOllama(String pdfPath, String systemPrompt) {
        if (useVision) {
            Optional<ZugferdDaten> visionResult = analyzeWithOllamaVision(pdfPath, systemPrompt);
            if (visionResult.isPresent()) {
                return visionResult;
            }
        }
        return analyzeWithOllamaText(pdfPath, systemPrompt);
    }

    private Optional<ZugferdDaten> analyzeWithOllamaVision(String pdfPath, String systemPrompt) {
        try {
            String base64Image = convertPdfToBase64Image(pdfPath);
            if (base64Image == null)
                return Optional.empty();

            log.info("[PdfAI/Ollama] Vision wird derzeit nicht unterstützt");
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<ZugferdDaten> analyzeWithOllamaText(String pdfPath, String systemPrompt) {
        try {
            String pdfText = extractTextFromPdf(pdfPath);
            if (pdfText == null || pdfText.isBlank())
                return Optional.empty();

            if (pdfText.length() > MAX_TEXT_LENGTH) {
                pdfText = pdfText.substring(0, MAX_TEXT_LENGTH);
            }

            return callOllamaChat(pdfText, systemPrompt);
        } catch (Exception e) {
            log.warn("[PdfAI/Ollama] Fehler: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ZugferdDaten> callOllamaChat(String pdfText, String systemPrompt) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("model", "llama3.2");
            payload.put("stream", false);

            ArrayNode messages = objectMapper.createArrayNode();

            ObjectNode systemNode = messages.addObject();
            systemNode.put("role", "system");
            systemNode.put("content", systemPrompt);

            ObjectNode userNode = messages.addObject();
            userNode.put("role", "user");
            userNode.put("content", "Hier ist der PDF-Text:\n\n" + pdfText);

            payload.set("messages", messages);

            ObjectNode options = objectMapper.createObjectNode();
            options.put("temperature", 0.1);
            payload.set("options", options);

            String body = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:11434/api/chat"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            log.info("[PdfAI/Ollama] Sende Anfrage");
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[PdfAI/Ollama] Status {}", response.statusCode());
                return Optional.empty();
            }

            return parseOllamaResponse(response.body());

        } catch (java.net.ConnectException e) {
            log.info("[PdfAI/Ollama] Nicht erreichbar");
            return Optional.empty();
        } catch (Exception e) {
            log.warn("[PdfAI/Ollama] Fehler: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ZugferdDaten> parseOllamaResponse(String responseBody) {
        try {
            JsonNode responseJson = objectMapper.readTree(responseBody);
            String content = responseJson.path("message").path("content").asText();
            if (content == null || content.isBlank())
                return Optional.empty();

            content = cleanJsonResponse(content);
            JsonNode dataJson = objectMapper.readTree(content);
            return Optional.of(mapToZugferdDaten(dataJson));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ==================== HELPER METHODS ====================

    private String extractTextFromPdf(String pdfPath) throws IOException {
        Path path = Path.of(pdfPath);
        if (!Files.exists(path)) {
            throw new IOException("PDF nicht gefunden: " + pdfPath);
        }
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(path.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String convertPdfToBase64Image(String pdfPath) throws IOException {
        Path path = Path.of(pdfPath);
        if (!Files.exists(path))
            return null;

        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(path.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            BufferedImage image = renderer.renderImageWithDPI(0, PDF_RENDER_DPI, ImageType.RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    private String cleanJsonResponse(String content) {
        content = content.trim();
        if (content.startsWith("```json"))
            content = content.substring(7);
        else if (content.startsWith("```"))
            content = content.substring(3);
        if (content.endsWith("```"))
            content = content.substring(0, content.length() - 3);
        return content.trim();
    }

    private ZugferdDaten mapToZugferdDaten(JsonNode json) {
        ZugferdDaten daten = new ZugferdDaten();

        JsonNode dokumenttypNode = json.path("dokumenttyp");
        if (dokumenttypNode.isTextual() && !dokumenttypNode.asText().isBlank()) {
            daten.setGeschaeftsdokumentart(dokumenttypNode.asText().trim());
        }

        JsonNode nummerNode = json.path("dokumentnummer");
        if (nummerNode.isTextual() && !nummerNode.asText().isBlank()) {
            daten.setRechnungsnummer(nummerNode.asText().trim());
        }

        JsonNode rechnungsdatumNode = json.path("rechnungsdatum");
        if (rechnungsdatumNode.isTextual() && !rechnungsdatumNode.asText().isBlank()) {
            daten.setRechnungsdatum(parseDate(rechnungsdatumNode.asText()));
        }

        JsonNode faelligkeitNode = json.path("faelligkeitsdatum");
        if (faelligkeitNode.isTextual() && !faelligkeitNode.asText().isBlank()) {
            daten.setFaelligkeitsdatum(parseDate(faelligkeitNode.asText()));
        }

        JsonNode betragNode = json.path("betrag");
        if (betragNode.isNumber()) {
            daten.setBetrag(BigDecimal.valueOf(betragNode.asDouble()));
        } else if (betragNode.isTextual() && !betragNode.asText().isBlank()) {
            try {
                String betragStr = betragNode.asText().replace(",", ".").replaceAll("[^0-9.]", "");
                daten.setBetrag(new BigDecimal(betragStr));
            } catch (NumberFormatException ignored) {
            }
        }

        JsonNode kundennameNode = json.path("kundenname");
        if (kundennameNode.isTextual() && !kundennameNode.asText().isBlank()) {
            daten.setKundenName(kundennameNode.asText().trim());
        }

        JsonNode kundennummerNode = json.path("kundennummer");
        if (kundennummerNode.isTextual() && !kundennummerNode.asText().isBlank()) {
            daten.setKundennummer(kundennummerNode.asText().trim());
        }

        return daten;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank())
            return null;

        String[] patterns = { "yyyy-MM-dd", "dd.MM.yyyy", "dd/MM/yyyy", "yyyyMMdd" };
        for (String pattern : patterns) {
            try {
                return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
            }
        }

        String normalized = dateStr.replaceAll("[^0-9]", "");
        if (normalized.length() >= 8) {
            try {
                return LocalDate.parse(normalized.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }
}
