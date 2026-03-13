package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiScannerService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${ai.gemini.api-key}")
    private String geminiApiKey;

    @Value("${ai.gemini.model.scanner:gemini-3-flash-preview}")
    private String geminiModel;

    private static final String SYSTEM_PROMPT_FILENAME = """
            You are a smart file naming assistant.
            Analyze the document image and generate a concise, professional filename.

            Format: "{Type}_{Sender/Context}_{Date}"
            - Type: Rechnung, Vertrag, Brief, Notiz, etc. (German)
            - Sender: Company name or person if visible.
            - Date: YYYY-MM-DD (from the document) or TODAY if not found.

            Examples:
            - Rechnung_Amazon_2023-12-24
            - Brief_Finanzamt_2024-01-05
            - Notiz_Meeting_2024-03-10

            Rules:
            1. Return ONLY the filename string.
            2. No file extension.
            3. No spaces (use underscores).
            4. If text is illegible, return "Scan_{Timestamp}".
            """;

    public String generateFilename(byte[] imageBytes) {
        try {
            if (geminiApiKey == null || geminiApiKey.isBlank())
                return "Scan_" + System.currentTimeMillis();

            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // Build Request
            ObjectNode requestBody = objectMapper.createObjectNode();

            // System Prompt
            ObjectNode systemInstruction = objectMapper.createObjectNode();
            ArrayNode sysParts = objectMapper.createArrayNode();
            sysParts.add(objectMapper.createObjectNode().put("text", SYSTEM_PROMPT_FILENAME));
            systemInstruction.set("parts", sysParts);
            requestBody.set("systemInstruction", systemInstruction);

            // User Content
            ArrayNode contents = objectMapper.createArrayNode();
            ObjectNode userMsg = objectMapper.createObjectNode().put("role", "user");
            ArrayNode parts = objectMapper.createArrayNode();

            ObjectNode imgPart = objectMapper.createObjectNode();
            ObjectNode inlineData = objectMapper.createObjectNode();
            inlineData.put("mimeType", "image/jpeg");
            inlineData.put("data", base64Image);
            imgPart.set("inlineData", inlineData);
            parts.add(imgPart);

            parts.add(objectMapper.createObjectNode().put("text", "Generate a filename."));

            userMsg.set("parts", parts);
            contents.add(userMsg);
            requestBody.set("contents", contents);

            // Config
            ObjectNode config = objectMapper.createObjectNode();
            config.put("temperature", 0.0);
            config.put("responseMimeType", "text/plain");
            requestBody.set("generationConfig", config);

            // Send
            String body = objectMapper.writeValueAsString(requestBody);
            String apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s".formatted(
                    geminiModel, geminiApiKey);

            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.error("Gemini Naming Error {}: {}", response.statusCode(), response.body());
                return "Scan_" + System.currentTimeMillis();
            }

            // Parse
            JsonNode root = objectMapper.readTree(response.body());
            String text = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText()
                    .trim();

            // Cleanup
            text = text.replace(" ", "_").replaceAll("[^a-zA-Z0-9_\\-]", "");
            return text.isEmpty() ? "Scan_" + System.currentTimeMillis() : text;

        } catch (Exception e) {
            log.error("Failed to generate filename", e);
            return "Scan_" + System.currentTimeMillis();
        }
    }
}
