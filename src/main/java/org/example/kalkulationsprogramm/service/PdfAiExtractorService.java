package org.example.kalkulationsprogramm.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.service.ki.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiDienst;
import org.example.kalkulationsprogramm.service.ki.KiErgebnis;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTeil;
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
 * Läuft über den KI-Anbieter aus den Einstellungen ({@link KiFunktion#DOKUMENT_ANALYSE});
 * mit {@code ai.pdf.backend=ollama} stattdessen über ein lokales Ollama.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfAiExtractorService {

    private static final String SYSTEM_PROMPT_INVOICE = """
            Du bist ein präziser Dokumentenanalysator für Geschäftsdokumente eines Handwerksbetriebs.

            DOKUMENTFORMAT-ERKENNUNG (Rechnungen aus diesem Programm):
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
            Du bist ein präziser Dokumentenanalysator für Angebote und Auftragsbestätigungen eines Handwerksbetriebs.

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
    private final KiDienst kiDienst;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ========== KONFIGURATION ==========
    // Backend: "ollama" für ein lokales Ollama, sonst der KI-Anbieter aus den Einstellungen
    @Value("${ai.pdf.backend:ki}")
    private String aiBackend;

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

        if ("ollama".equalsIgnoreCase(aiBackend)) {
            return analyzeWithOllama(pdfPath, prompt);
        }
        return analyzeWithKi(pdfPath, prompt);
    }

    // ==================== KI-Anbieter (PDF direkt) ====================

    private Optional<ZugferdDaten> analyzeWithKi(String pdfPath, String systemPrompt) {
        if (!kiDienst.verfuegbar(KiFunktion.DOKUMENT_ANALYSE)) {
            log.warn("[PdfAI/KI] Keine KI für das Auslesen von Dokumenten eingerichtet.");
            return Optional.empty();
        }
        try {
            Path path = Path.of(pdfPath);
            if (!Files.exists(path)) {
                log.warn("[PdfAI/KI] PDF nicht gefunden");
                return Optional.empty();
            }

            byte[] pdfBytes = Files.readAllBytes(path);
            // Ohne Pfad: der Dateiname trägt Namen aus den Rechnungen (DSGVO).
            log.info("[PdfAI/KI] PDF geladen ({} KB)", pdfBytes.length / 1024);

            KiAnfrage anfrage = KiAnfrage.mitAnweisung(systemPrompt)
                    .nutzer(KiTeil.datei(pdfBytes, "application/pdf"),
                            KiTeil.text("Analysiere dieses PDF-Dokument und extrahiere die Daten gemäß den Anweisungen."))
                    .temperatur(0.1)
                    .maxAusgabe(8192)
                    .zeitlimit(Duration.ofSeconds(Math.max(90, timeoutSeconds)))
                    .bauen();
            KiErgebnis ergebnis = kiDienst.sende(KiFunktion.DOKUMENT_ANALYSE, anfrage);
            log.info("[PdfAI/KI] Antwort von {} erhalten ({} Zeichen)", ergebnis.anbieter(), ergebnis.text().length());
            return parseKiAntwort(ergebnis.text());

        } catch (Exception e) {
            log.warn("[PdfAI/KI] Fehler: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** Liest die JSON-Antwort der KI. Nie den Inhalt loggen: er ist das Rechnungs-JSON (DSGVO). */
    Optional<ZugferdDaten> parseKiAntwort(String content) {
        if (content == null || content.isBlank()) {
            log.warn("[PdfAI/KI] Leere Antwort");
            return Optional.empty();
        }
        try {
            String bereinigt = cleanJsonResponse(content);
            log.debug("[PdfAI/KI] Antwort bereinigt ({} Zeichen)", bereinigt.length());
            return Optional.of(mapToZugferdDaten(objectMapper.readTree(bereinigt)));
        } catch (Exception e) {
            log.warn("[PdfAI/KI] Parse-Fehler: {}", e.getClass().getSimpleName());
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
            String content = responseJson.path("message").path("content").asString("");
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
        if (dokumenttypNode.isString() && !dokumenttypNode.asString("").isBlank()) {
            daten.setGeschaeftsdokumentart(dokumenttypNode.asString("").trim());
        }

        JsonNode nummerNode = json.path("dokumentnummer");
        if (nummerNode.isString() && !nummerNode.asString("").isBlank()) {
            daten.setRechnungsnummer(nummerNode.asString("").trim());
        }

        JsonNode rechnungsdatumNode = json.path("rechnungsdatum");
        if (rechnungsdatumNode.isString() && !rechnungsdatumNode.asString("").isBlank()) {
            daten.setRechnungsdatum(parseDate(rechnungsdatumNode.asString("")));
        }

        JsonNode faelligkeitNode = json.path("faelligkeitsdatum");
        if (faelligkeitNode.isString() && !faelligkeitNode.asString("").isBlank()) {
            daten.setFaelligkeitsdatum(parseDate(faelligkeitNode.asString("")));
        }

        JsonNode betragNode = json.path("betrag");
        if (betragNode.isNumber()) {
            daten.setBetrag(BigDecimal.valueOf(betragNode.asDouble(0.0)));
        } else if (betragNode.isString() && !betragNode.asString("").isBlank()) {
            try {
                String betragStr = betragNode.asString("").replace(",", ".").replaceAll("[^0-9.]", "");
                daten.setBetrag(new BigDecimal(betragStr));
            } catch (NumberFormatException ignored) {
            }
        }

        JsonNode kundennameNode = json.path("kundenname");
        if (kundennameNode.isString() && !kundennameNode.asString("").isBlank()) {
            daten.setKundenName(kundennameNode.asString("").trim());
        }

        JsonNode kundennummerNode = json.path("kundennummer");
        if (kundennummerNode.isString() && !kundennummerNode.asString("").isBlank()) {
            daten.setKundennummer(kundennummerNode.asString("").trim());
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
