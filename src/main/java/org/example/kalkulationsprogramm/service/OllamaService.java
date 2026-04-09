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
 * Service für die Kommunikation mit einem lokalen Ollama-Server.
 * Nutzt die Ollama REST API (/api/chat) für Chat-Completions.
 */
@Slf4j
@Service
public class OllamaService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String ollamaBaseUrl;
    private final String defaultModel;
    private final int timeoutSeconds;
    private final boolean enabled;

    @Autowired
    public OllamaService(ObjectMapper objectMapper,
                         @Value("${ai.ollama.url:http://localhost:11434}") String ollamaBaseUrl,
                         @Value("${ai.ollama.model:qwen3:8b}") String defaultModel,
                         @Value("${ai.ollama.timeout:120}") int timeoutSeconds,
                         @Value("${ai.ollama.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.defaultModel = defaultModel;
        this.timeoutSeconds = timeoutSeconds;
        this.enabled = enabled;
    }

    // Für Tests: Constructor mit allen Abhängigkeiten
    OllamaService(ObjectMapper objectMapper, HttpClient httpClient,
                  String ollamaBaseUrl, String defaultModel, int timeoutSeconds, boolean enabled) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.ollamaBaseUrl = ollamaBaseUrl;
        this.defaultModel = defaultModel;
        this.timeoutSeconds = timeoutSeconds;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sendet eine Chat-Anfrage an Ollama mit System-Prompt und User-Message.
     *
     * @param systemPrompt Der System-Prompt (Rollenbeschreibung, Anweisungen)
     * @param userMessage  Die Benutzernachricht
     * @return Die Antwort des Modells als String
     */
    public String chat(String systemPrompt, String userMessage) throws IOException, InterruptedException {
        return chat(systemPrompt, userMessage, defaultModel);
    }

    /**
     * Sendet eine Chat-Anfrage an Ollama mit spezifischem Modell.
     */
    public String chat(String systemPrompt, String userMessage, String model) throws IOException, InterruptedException {
        if (!enabled) {
            throw new IllegalStateException("Ollama ist deaktiviert (ai.ollama.enabled=false)");
        }

        String url = ollamaBaseUrl + "/api/chat";

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("stream", false);

        // Options: Temperatur niedrig für konsistente Klassifizierung
        ObjectNode options = objectMapper.createObjectNode();
        options.put("temperature", 0.1);
        options.put("num_predict", 500);
        requestBody.set("options", options);

        // Messages: system + user
        ArrayNode messages = objectMapper.createArrayNode();

        ObjectNode sysMsg = objectMapper.createObjectNode();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        requestBody.set("messages", messages);

        String body = objectMapper.writeValueAsString(requestBody);

        log.debug("[Ollama] Request an {}: model={}, prompt-length={}", url, model, userMessage.length());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("[Ollama] Fehler: HTTP {} – {}", response.statusCode(), response.body());
            throw new IOException("Ollama API Fehler: HTTP " + response.statusCode());
        }

        JsonNode responseJson = objectMapper.readTree(response.body());
        JsonNode messageNode = responseJson.path("message").path("content");

        if (messageNode.isMissingNode() || messageNode.asText().isBlank()) {
            throw new IOException("Ollama: Leere Antwort erhalten");
        }

        String result = messageNode.asText().trim();
        log.debug("[Ollama] Antwort erhalten: {} Zeichen", result.length());
        return result;
    }

    /**
     * Prüft ob der Ollama-Server erreichbar ist.
     */
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/tags"))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("[Ollama] Server nicht erreichbar: {}", e.getMessage());
            return false;
        }
    }
}
