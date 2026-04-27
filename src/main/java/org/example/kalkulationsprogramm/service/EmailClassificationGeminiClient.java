package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Gemini-Client speziell fuer die Email-Zuordnung (Klassifizierung von eingehenden
 * Emails zu Projekten/Anfragen). Verwendet ein leichtgewichtiges Gemini-Modell
 * (Standard: gemini-2.5-flash-lite), damit die Zuordnung schnell und kostenguenstig
 * laeuft und keine lokale Ollama-Installation noetig ist.
 */
@Slf4j
@Service
public class EmailClassificationGeminiClient {

    private final ObjectMapper objectMapper;
    private final SystemSettingsService systemSettingsService;
    private final HttpClient httpClient;
    private final String model;

    @Autowired
    public EmailClassificationGeminiClient(ObjectMapper objectMapper,
                                           SystemSettingsService systemSettingsService,
                                           @Value("${ai.gemini.model.email-classification:gemini-2.5-flash-lite}") String model) {
        this.objectMapper = objectMapper;
        this.systemSettingsService = systemSettingsService;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Test-Konstruktor mit injizierbarem HttpClient. */
    EmailClassificationGeminiClient(ObjectMapper objectMapper,
                                    SystemSettingsService systemSettingsService,
                                    HttpClient httpClient,
                                    String model) {
        this.objectMapper = objectMapper;
        this.systemSettingsService = systemSettingsService;
        this.httpClient = httpClient;
        this.model = model;
    }

    public boolean isEnabled() {
        String key = systemSettingsService.getGeminiApiKey();
        return key != null && !key.isBlank() && !"OVERRIDE_IN_LOCAL".equals(key);
    }

    /**
     * Sendet eine Klassifizierungs-Anfrage an Gemini.
     *
     * @param systemPrompt System-Anweisungen (Rollenbeschreibung, Output-Format)
     * @param userMessage  Benutzer-Inhalt (eingehende Email + Kandidaten)
     * @return Roher Antwort-Text (typischerweise JSON)
     */
    public String chat(String systemPrompt, String userMessage) throws IOException, InterruptedException {
        String apiKey = systemSettingsService.getGeminiApiKey();
        if (apiKey == null || apiKey.isBlank() || "OVERRIDE_IN_LOCAL".equals(apiKey)) {
            throw new IOException("Gemini API Key fehlt (ai.gemini.api-key)");
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"
                .formatted(model, apiKey);

        ObjectNode requestBody = objectMapper.createObjectNode();

        // System-Instruction
        ObjectNode systemInstruction = objectMapper.createObjectNode();
        ArrayNode sysParts = objectMapper.createArrayNode();
        sysParts.add(objectMapper.createObjectNode().put("text", systemPrompt));
        systemInstruction.set("parts", sysParts);
        requestBody.set("systemInstruction", systemInstruction);

        // User-Content
        ArrayNode contents = objectMapper.createArrayNode();
        ObjectNode userMsg = objectMapper.createObjectNode().put("role", "user");
        ArrayNode parts = objectMapper.createArrayNode();
        parts.add(objectMapper.createObjectNode().put("text", userMessage));
        userMsg.set("parts", parts);
        contents.add(userMsg);
        requestBody.set("contents", contents);

        // Niedrige Temperatur fuer konsistente Klassifizierung, JSON erzwingen
        ObjectNode config = objectMapper.createObjectNode();
        config.put("temperature", 0.1);
        config.put("responseMimeType", "application/json");
        requestBody.set("generationConfig", config);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            log.warn("[Gemini-Classify] HTTP {} – {}", response.statusCode(), response.body());
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
        return "";
    }
}
