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
import java.util.ArrayList;
import java.util.List;

/**
 * Retrieval-Augmented Generation service using Qdrant vector search.
 * Embeds user queries via Gemini, searches Qdrant for relevant code chunks,
 * and returns contextually relevant source code snippets.
 */
@Slf4j
@Service
public class QdrantRagService {

    private static final String GEMINI_EMBED_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=%s";
    private static final int EMBEDDING_DIMENSION = 768;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    private SystemSettingsService systemSettingsService;

    @Value("${ai.rag.qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${ai.rag.qdrant.port:6333}")
    private int qdrantPort;

    @Value("${ai.rag.qdrant.collection:codebase}")
    private String qdrantCollection;

    @Value("${ai.rag.top-k:10}")
    private int topK;

    @Value("${ai.rag.score-threshold:0.3}")
    private double scoreThreshold;

    @Value("${ai.rag.enabled:false}")
    private boolean ragEnabled;

    public QdrantRagService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record CodeChunkResult(
            String content,
            String filePath,
            String category,
            String chunkType,
            String name,
            double score
    ) {}

    /**
     * Check if RAG (Qdrant) is available and enabled.
     */
    public boolean isEnabled() {
        return ragEnabled;
    }

    public boolean isAvailable() {
        if (!ragEnabled) return false;
        try {
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("http://%s:%d/collections/%s".formatted(qdrantHost, qdrantPort, qdrantCollection)))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Qdrant nicht erreichbar: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Search for relevant code chunks based on a user query.
     * Embeds the query using Gemini, then searches Qdrant.
     */
    public List<CodeChunkResult> search(String query, String pageContext) throws IOException, InterruptedException {
        // Enrich query with page context for better matching
        String enrichedQuery = pageContext != null && !pageContext.isBlank()
                ? query + "\n\nAktueller Seitenkontext: " + pageContext
                : query;

        // Step 1: Embed the query
        long embedStart = System.currentTimeMillis();
        List<Double> queryVector = embedQuery(enrichedQuery);
        log.info("    Embedding erstellt in {} ms", System.currentTimeMillis() - embedStart);

        // Step 2: Search Qdrant
        long searchStart = System.currentTimeMillis();
        var results = searchQdrant(queryVector);
        log.info("    Qdrant-Suche: {} Treffer in {} ms (top-k={}, threshold={})",
                results.size(), System.currentTimeMillis() - searchStart, topK, scoreThreshold);
        return results;
    }

    /**
     * Embed a query string using Gemini text-embedding-004.
     */
    private List<Double> embedQuery(String query) throws IOException, InterruptedException {
        String url = GEMINI_EMBED_URL.formatted(systemSettingsService.getGeminiApiKey());

        ObjectNode requestBody = objectMapper.createObjectNode();
        ObjectNode content = objectMapper.createObjectNode();
        ArrayNode parts = objectMapper.createArrayNode();
        parts.add(objectMapper.createObjectNode().put("text", query));
        content.set("parts", parts);
        requestBody.set("content", content);
        requestBody.put("taskType", "RETRIEVAL_QUERY");

        String body = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            log.error("Gemini Embedding API Error {}: {}", response.statusCode(), response.body());
            throw new IOException("Embedding-Fehler (Status " + response.statusCode() + ")");
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode values = root.path("embedding").path("values");

        if (!values.isArray() || values.isEmpty()) {
            throw new IOException("Keine Embedding-Werte erhalten");
        }

        List<Double> vector = new ArrayList<>(EMBEDDING_DIMENSION);
        for (JsonNode v : values) {
            vector.add(v.asDouble());
        }
        return vector;
    }

    /**
     * Perform vector search against Qdrant REST API.
     */
    private List<CodeChunkResult> searchQdrant(List<Double> queryVector) throws IOException, InterruptedException {
        String url = "http://%s:%d/collections/%s/points/search".formatted(qdrantHost, qdrantPort, qdrantCollection);

        ObjectNode requestBody = objectMapper.createObjectNode();
        ArrayNode vectorNode = objectMapper.createArrayNode();
        for (Double v : queryVector) {
            vectorNode.add(v);
        }
        requestBody.set("vector", vectorNode);
        requestBody.put("limit", topK);
        requestBody.put("score_threshold", scoreThreshold);
        requestBody.put("with_payload", true);

        String body = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            log.error("Qdrant search error {}: {}", response.statusCode(), response.body());
            throw new IOException("Qdrant-Suche fehlgeschlagen (Status " + response.statusCode() + ")");
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode results = root.path("result");

        List<CodeChunkResult> chunks = new ArrayList<>();
        if (results.isArray()) {
            for (JsonNode hit : results) {
                JsonNode payload = hit.path("payload");
                chunks.add(new CodeChunkResult(
                        payload.path("content").asText(),
                        payload.path("file_path").asText(),
                        payload.path("category").asText(),
                        payload.path("chunk_type").asText(),
                        payload.path("name").asText(),
                        hit.path("score").asDouble()
                ));
            }
        }

        log.debug("Qdrant-Suche: {} Treffer (Schwelle: {})", chunks.size(), scoreThreshold);
        return chunks;
    }

    /**
     * Build a formatted context string from search results for the LLM prompt.
     */
    public String buildContextFromResults(List<CodeChunkResult> results) {
        if (results.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("## Relevante Code-Abschnitte (automatisch per Vektor-Suche gefunden)\n\n");

        for (int i = 0; i < results.size(); i++) {
            CodeChunkResult r = results.get(i);
            sb.append("### [%d] %s — %s (%s) · Score: %.2f\n".formatted(
                    i + 1, r.filePath(), r.name(), r.chunkType(), r.score()));
            sb.append("```\n");
            sb.append(r.content());
            sb.append("\n```\n\n");
        }

        return sb.toString();
    }
}
