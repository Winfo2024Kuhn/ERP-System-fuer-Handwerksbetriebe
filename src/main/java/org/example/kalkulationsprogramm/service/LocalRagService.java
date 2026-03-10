package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Local RAG (Retrieval-Augmented Generation) service that operates entirely in-process.
 * No Docker, no Qdrant needed. Chunks source code, embeds via Gemini text-embedding-004,
 * stores vectors in memory (persisted to a JSON cache file), and does cosine similarity search.
 */
@Slf4j
@Service
public class LocalRagService {

    private static final String GEMINI_EMBED_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=%s";
    private static final String GEMINI_BATCH_EMBED_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents?key=%s";
    private static final int EMBEDDING_DIM = 768;
    private static final int MAX_CHUNK_CHARS = 6000;
    private static final int BATCH_SIZE = 20;

    private static final String JAVA_BASE = "src/main/java/org/example/kalkulationsprogramm";
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "((?:password|passwd|secret|api[._-]?key|token|credentials)\\s*[=:]\\s*)([^\\s,;\"'}{]+)",
            Pattern.CASE_INSENSITIVE);

    /**
     * Maps frontend routes to file name keywords so we can always include the
     * source code of the page the user is currently looking at.
     */
    private static final Map<String, List<String>> ROUTE_TO_FILE_KEYWORDS = Map.ofEntries(
            Map.entry("/projekte", List.of("ProjektEditor")),
            Map.entry("/angebote", List.of("AngebotEditor")),
            Map.entry("/kunden", List.of("Kundeneditor")),
            Map.entry("/lieferanten", List.of("LieferantenEditor")),
            Map.entry("/artikel", List.of("ArtikelEditor")),
            Map.entry("/bestellungen", List.of("BestellungenUebersicht")),
            Map.entry("/bestellungen/bedarf", List.of("BestellungEditor")),
            Map.entry("/textbausteine", List.of("TextbausteinEditor")),
            Map.entry("/leistungen", List.of("Leistungseditor")),
            Map.entry("/arbeitsgaenge", List.of("ArbeitsgangEditor")),
            Map.entry("/produktkategorien", List.of("ProduktkategorieEditor")),
            Map.entry("/mitarbeiter", List.of("MitarbeiterEditor")),
            Map.entry("/arbeitszeitarten", List.of("ArbeitszeitartEditor")),
            Map.entry("/kalender", List.of("TerminKalender")),
            Map.entry("/emails", List.of("EmailCenter")),
            Map.entry("/formulare", List.of("FormularwesenEditor")),
            Map.entry("/offeneposten", List.of("OffenePostenEditor")),
            Map.entry("/rechnungsuebersicht", List.of("RechnungsuebersichtEditor")),
            Map.entry("/analyse", List.of("ErfolgsanalyseEditor")),
            Map.entry("/miete", List.of("MietabrechnungEditor")),
            Map.entry("/benutzer", List.of("BenutzerEditor")),
            Map.entry("/firma", List.of("FirmaEditor")),
            Map.entry("/abteilung-berechtigungen", List.of("AbteilungBerechtigungenEditor")),
            Map.entry("/zeitbuchungen", List.of("ZeiterfassungKalender")),
            Map.entry("/auswertung", List.of("ZeiterfassungAuswertung")),
            Map.entry("/steuerberater", List.of("ZeiterfassungSteuerberater")),
            Map.entry("/zeitkonten", List.of("ZeiterfassungZeitkonten")),
            Map.entry("/feiertage", List.of("ZeiterfassungFeiertage")),
            Map.entry("/urlaubsantraege", List.of("Urlaubsantraege")),
            Map.entry("/dokument-editor", List.of("DocumentEditorPage"))
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // In-memory vector store
    private final CopyOnWriteArrayList<ChunkEntry> vectorStore = new CopyOnWriteArrayList<>();
    private volatile boolean ready = false;

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.rag.enabled:false}")
    private boolean ragEnabled;

    @Value("${ai.rag.top-k:10}")
    private int topK;

    @Value("${ai.rag.score-threshold:0.3}")
    private double scoreThreshold;

    @Value("${user.dir}")
    private String projectRoot;

    public LocalRagService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Data structures ──

    public record CodeChunkResult(
            String content, String filePath, String category,
            String chunkType, String name, double score
    ) {}

    private record ChunkEntry(
            String content, String filePath, String category,
            String chunkType, String name, String contentHash,
            double[] vector
    ) {}

    private record CachedChunk(
            String content, String filePath, String category,
            String chunkType, String name, String contentHash,
            List<Double> vector
    ) {}

    // ── Public API ──

    public boolean isAvailable() {
        return ragEnabled && ready;
    }

    public boolean isEnabled() {
        return ragEnabled;
    }

    public boolean isReady() {
        return ready;
    }

    /**
     * Search for relevant code chunks by embedding the query and doing cosine similarity.
     * Always includes chunks from the current page's source file so the LLM knows
     * exactly what the user sees on screen.
     *
     * @param query       The user's question
     * @param pageContext A hint like "Seite: PROJEKTE (/projekte)" – used to enrich the query embedding
     * @param currentRoute The frontend route, e.g. "/projekte" – used to deterministically include that page's code
     */
    public List<CodeChunkResult> search(String query, String pageContext, String currentRoute)
            throws IOException, InterruptedException {
        if (!ready || vectorStore.isEmpty()) return List.of();

        String enrichedQuery = pageContext != null && !pageContext.isBlank()
                ? query + "\n\nSeitenkontext: " + pageContext
                : query;

        long t0 = System.currentTimeMillis();
        double[] queryVec = embedSingle(enrichedQuery, "RETRIEVAL_QUERY");
        log.info("    Embedding erstellt in {} ms", System.currentTimeMillis() - t0);

        long t1 = System.currentTimeMillis();

        // 1) Deterministically find chunks belonging to the current page's TSX file
        List<CodeChunkResult> currentPageChunks = findChunksForRoute(currentRoute);
        Set<String> currentPagePaths = new HashSet<>();
        for (CodeChunkResult c : currentPageChunks) {
            currentPagePaths.add(c.filePath() + "::" + c.name());
        }
        if (!currentPageChunks.isEmpty()) {
            log.info("    Aktuelle Seite '{}': {} Chunks deterministisch eingebunden ({})",
                    currentRoute, currentPageChunks.size(),
                    currentPageChunks.stream().map(CodeChunkResult::name).toList());
        }

        // 2) Score all remaining chunks via cosine similarity
        List<CodeChunkResult> similarityResults = new ArrayList<>();
        for (ChunkEntry entry : vectorStore) {
            String key = entry.filePath() + "::" + entry.name();
            if (currentPagePaths.contains(key)) continue; // already included deterministically

            double score = cosineSimilarity(queryVec, entry.vector());
            if (score >= scoreThreshold) {
                similarityResults.add(new CodeChunkResult(
                        entry.content(), entry.filePath(), entry.category(),
                        entry.chunkType(), entry.name(), score
                ));
            }
        }
        similarityResults.sort(Comparator.comparingDouble(CodeChunkResult::score).reversed());

        // 3) Reserve slots: current page gets priority, rest filled by similarity with frontend/backend balance
        int pageSlots = Math.min(currentPageChunks.size(), topK / 2); // up to 50% for current page
        int remainingSlots = topK - pageSlots;

        // Split similarity results into frontend vs backend
        List<CodeChunkResult> frontendSimilarity = new ArrayList<>();
        List<CodeChunkResult> otherSimilarity = new ArrayList<>();
        for (CodeChunkResult r : similarityResults) {
            if (r.category().startsWith("frontend") || r.category().startsWith("zeiterfassung")) {
                frontendSimilarity.add(r);
            } else {
                otherSimilarity.add(r);
            }
        }

        // At least 30% of remaining slots for frontend (on top of current page chunks which are already frontend)
        int frontendSlots = Math.min(frontendSimilarity.size(), Math.max(remainingSlots * 3 / 10, 1));
        int backendSlots = remainingSlots - frontendSlots;

        List<CodeChunkResult> finalResults = new ArrayList<>();
        // Current page chunks first (highest priority)
        finalResults.addAll(currentPageChunks.subList(0, pageSlots));
        // Then frontend similarity
        finalResults.addAll(frontendSimilarity.subList(0, Math.min(frontendSlots, frontendSimilarity.size())));
        // Then backend/other similarity
        finalResults.addAll(otherSimilarity.subList(0, Math.min(backendSlots, otherSimilarity.size())));

        // Fill remaining slots from whichever pool has leftovers
        if (finalResults.size() < topK) {
            int remaining = topK - finalResults.size();
            // Try more frontend first
            for (int i = frontendSlots; i < frontendSimilarity.size() && remaining > 0; i++) {
                finalResults.add(frontendSimilarity.get(i));
                remaining--;
            }
            // Then more backend
            for (int i = backendSlots; i < otherSimilarity.size() && remaining > 0; i++) {
                finalResults.add(otherSimilarity.get(i));
                remaining--;
            }
        }

        log.info("    Lokale Vektor-Suche: {} Treffer in {} ms (seiten-chunks={}, top-k={}, threshold={}, store={})",
                finalResults.size(), System.currentTimeMillis() - t1, pageSlots, topK, scoreThreshold, vectorStore.size());
        return finalResults;
    }

    /**
     * Backward-compatible search without route.
     */
    public List<CodeChunkResult> search(String query, String pageContext) throws IOException, InterruptedException {
        return search(query, pageContext, null);
    }

    /**
     * Find all chunks belonging to the frontend page file(s) for the given route.
     */
    private List<CodeChunkResult> findChunksForRoute(String route) {
        if (route == null || route.isBlank()) return List.of();

        List<String> keywords = ROUTE_TO_FILE_KEYWORDS.get(route);
        if (keywords == null || keywords.isEmpty()) return List.of();

        List<CodeChunkResult> pageChunks = new ArrayList<>();
        for (ChunkEntry entry : vectorStore) {
            for (String keyword : keywords) {
                if (entry.filePath().contains(keyword)) {
                    pageChunks.add(new CodeChunkResult(
                            entry.content(), entry.filePath(), entry.category(),
                            entry.chunkType(), entry.name(), 1.0 // max score for current page
                    ));
                    break;
                }
            }
        }
        return pageChunks;
    }

    /**
     * Build formatted context string from search results for the LLM prompt.
     * Chunks with score 1.0 are from the current page (deterministically included).
     */
    public String buildContextFromResults(List<CodeChunkResult> results) {
        if (results.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            CodeChunkResult r = results.get(i);
            String label = r.score() >= 1.0
                    ? ">>> AKTUELLE SEITE DES BENUTZERS <<<"
                    : "Score: %.2f".formatted(r.score());
            sb.append("### [%d] %s -- %s (%s) | %s\n".formatted(
                    i + 1, r.filePath(), r.name(), r.chunkType(), label));
            sb.append("```\n").append(r.content()).append("\n```\n\n");
        }
        return sb.toString();
    }

    // ── Indexing (runs on startup, async) ──

    @PostConstruct
    void init() {
        if (!ragEnabled) {
            log.info("Lokales RAG ist deaktiviert (ai.rag.enabled=false)");
            return;
        }
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.warn("Lokales RAG: Gemini API Key fehlt, RAG deaktiviert");
            ragEnabled = false;
            return;
        }
        // Start indexing in background thread to not block startup
        Thread.ofVirtual().name("rag-indexer").start(this::buildIndex);
    }

    private void buildIndex() {
        try {
            Path root = Paths.get(projectRoot).toAbsolutePath().normalize();
            Path cacheFile = root.resolve(".rag-cache.json");

            // Load existing cache
            Map<String, CachedChunk> cache = loadCache(cacheFile);
            log.info("RAG-Index: Cache geladen mit {} Eintraegen", cache.size());

            // Chunk all source files
            List<RawChunk> rawChunks = chunkCodebase(root);
            log.info("RAG-Index: {} Chunks aus Codebase extrahiert", rawChunks.size());

            // Determine which chunks need embedding (new or changed)
            List<RawChunk> needsEmbedding = new ArrayList<>();
            List<ChunkEntry> reusedEntries = new ArrayList<>();

            for (RawChunk raw : rawChunks) {
                String hash = sha256(raw.content());
                String cacheKey = raw.filePath() + "::" + raw.name();
                CachedChunk cached = cache.get(cacheKey);

                if (cached != null && cached.contentHash().equals(hash) && cached.vector() != null) {
                    // Reuse cached embedding
                    reusedEntries.add(new ChunkEntry(
                            raw.content(), raw.filePath(), raw.category(),
                            raw.chunkType(), raw.name(), hash,
                            cached.vector().stream().mapToDouble(Double::doubleValue).toArray()
                    ));
                } else {
                    needsEmbedding.add(raw);
                }
            }

            log.info("RAG-Index: {} aus Cache, {} neu zu embedden", reusedEntries.size(), needsEmbedding.size());

            // Embed new chunks in batches
            List<ChunkEntry> newEntries = embedChunksInBatches(needsEmbedding);

            // Build final store
            vectorStore.clear();
            vectorStore.addAll(reusedEntries);
            vectorStore.addAll(newEntries);

            // Save cache
            saveCache(cacheFile, vectorStore);

            ready = true;
            log.info("RAG-Index FERTIG: {} Chunks gespeichert, bereit fuer Anfragen", vectorStore.size());

        } catch (Exception e) {
            log.error("RAG-Index Aufbau fehlgeschlagen: {}", e.getMessage(), e);
        }
    }

    // ── Chunking ──

    private record RawChunk(String content, String filePath, String category, String chunkType, String name) {}

    private List<RawChunk> chunkCodebase(Path root) {
        List<RawChunk> chunks = new ArrayList<>();

        // Frontend pages
        chunkDirectory(chunks, root, "react-pc-frontend/src/pages", ".tsx", "frontend-page", 3);
        // Frontend components
        chunkDirectory(chunks, root, "react-pc-frontend/src/components", ".tsx", "frontend-component", 4);
        chunkDirectory(chunks, root, "react-pc-frontend/src/components", ".ts", "frontend-component", 4);
        // App routing
        chunkSingleFile(chunks, root, "react-pc-frontend/src/App.tsx", "frontend-routing");
        chunkSingleFile(chunks, root, "react-pc-frontend/src/types.ts", "frontend-types");
        // Zeiterfassung
        chunkDirectory(chunks, root, "react-zeiterfassung/src/pages", ".tsx", "zeiterfassung-page", 3);
        chunkDirectory(chunks, root, "react-zeiterfassung/src/components", ".tsx", "zeiterfassung-component", 3);
        // Backend controllers
        chunkDirectory(chunks, root, JAVA_BASE + "/controller", ".java", "backend-controller", 3);
        // Backend services
        chunkDirectory(chunks, root, JAVA_BASE + "/service", ".java", "backend-service", 3);
        // Backend entities
        chunkDirectory(chunks, root, JAVA_BASE + "/domain", ".java", "backend-entity", 3);
        // Backend DTOs
        chunkDirectory(chunks, root, JAVA_BASE + "/dto", ".java", "backend-dto", 4);
        // Documentation
        chunkDirectory(chunks, root, "docs", ".md", "documentation", 3);

        return chunks;
    }

    private void chunkDirectory(List<RawChunk> chunks, Path root, String relPath, String ext, String category, int maxDepth) {
        Path dir = root.resolve(relPath);
        if (!Files.isDirectory(dir)) return;
        Path normalizedDir = dir.toAbsolutePath().normalize();

        try (Stream<Path> files = Files.walk(dir, maxDepth)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(ext))
                    .filter(p -> p.toAbsolutePath().normalize().startsWith(normalizedDir))
                    .filter(p -> !p.getFileName().toString().contains("Test"))
                    .sorted()
                    .forEach(f -> chunkFile(chunks, root, f, category));
        } catch (IOException e) {
            log.warn("Konnte Verzeichnis nicht lesen: {}", dir);
        }
    }

    private void chunkSingleFile(List<RawChunk> chunks, Path root, String relPath, String category) {
        Path file = root.resolve(relPath);
        if (Files.isRegularFile(file)) {
            chunkFile(chunks, root, file, category);
        }
    }

    private void chunkFile(List<RawChunk> chunks, Path root, Path file, String category) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            content = SECRET_PATTERN.matcher(content).replaceAll("$1***REDACTED***");
            String relativePath = root.relativize(file).toString().replace('\\', '/');
            String fileName = file.getFileName().toString();

            if (fileName.endsWith(".java")) {
                chunkJava(chunks, content, relativePath, category);
            } else if (fileName.endsWith(".tsx") || fileName.endsWith(".ts")) {
                chunkTypeScript(chunks, content, relativePath, category);
            } else if (fileName.endsWith(".md")) {
                chunkMarkdown(chunks, content, relativePath, category);
            } else {
                // Whole file as single chunk
                addChunk(chunks, content, relativePath, category, "file", fileName);
            }
        } catch (IOException e) {
            log.warn("Konnte {} nicht lesen", file);
        }
    }

    private void chunkJava(List<RawChunk> chunks, String content, String filePath, String category) {
        // Extract class-level chunk (imports + class declaration + fields)
        String className = extractJavaClassName(content);

        // Split by method boundaries
        List<String> methods = splitJavaMethods(content);
        if (methods.size() <= 1 || content.length() <= MAX_CHUNK_CHARS) {
            // Small file — keep as single chunk
            addChunk(chunks, content, filePath, category, "class", className);
            return;
        }

        // Class header (everything before first method)
        String header = extractJavaHeader(content);
        if (!header.isBlank()) {
            addChunk(chunks, header, filePath, category, "class-header", className + " (header)");
        }

        // Individual methods
        for (String method : methods) {
            String methodName = extractMethodName(method);
            addChunk(chunks, method, filePath, category, "method", className + "." + methodName);
        }
    }

    private void chunkTypeScript(List<RawChunk> chunks, String content, String filePath, String category) {
        // For TSX/TS: try to split by exported functions/components
        Pattern exportPattern = Pattern.compile(
                "^(export\\s+(?:default\\s+)?(?:function|const|class)\\s+\\w+)",
                Pattern.MULTILINE);
        Matcher m = exportPattern.matcher(content);

        List<int[]> boundaries = new ArrayList<>();
        while (m.find()) {
            boundaries.add(new int[]{m.start(), 0});
        }

        if (boundaries.size() <= 1 || content.length() <= MAX_CHUNK_CHARS) {
            String name = filePath.substring(filePath.lastIndexOf('/') + 1);
            addChunk(chunks, content, filePath, category, "component", name);
            return;
        }

        // Set end boundaries
        for (int i = 0; i < boundaries.size(); i++) {
            boundaries.get(i)[1] = (i + 1 < boundaries.size())
                    ? boundaries.get(i + 1)[0]
                    : content.length();
        }

        // Imports as header
        if (boundaries.get(0)[0] > 50) {
            addChunk(chunks, content.substring(0, boundaries.get(0)[0]).trim(),
                    filePath, category, "imports", filePath.substring(filePath.lastIndexOf('/') + 1) + " (imports)");
        }

        for (int[] b : boundaries) {
            String section = content.substring(b[0], b[1]).trim();
            String name = extractTsExportName(section);
            addChunk(chunks, section, filePath, category, "component", name);
        }
    }

    private void chunkMarkdown(List<RawChunk> chunks, String content, String filePath, String category) {
        // Split by ## headings
        String[] sections = content.split("(?=^## )", Pattern.MULTILINE);
        if (sections.length <= 1 || content.length() <= MAX_CHUNK_CHARS) {
            String name = filePath.substring(filePath.lastIndexOf('/') + 1);
            addChunk(chunks, content, filePath, category, "document", name);
            return;
        }

        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isEmpty()) continue;
            String heading = trimmed.lines().findFirst().orElse("").replace("#", "").trim();
            if (heading.isBlank()) heading = "intro";
            addChunk(chunks, trimmed, filePath, category, "section", heading);
        }
    }

    private void addChunk(List<RawChunk> chunks, String content, String filePath, String category, String type, String name) {
        if (content.isBlank()) return;
        if (content.length() > MAX_CHUNK_CHARS) {
            content = content.substring(0, MAX_CHUNK_CHARS);
        }
        chunks.add(new RawChunk(content, filePath, category, type, name));
    }

    // ── Helper extractors ──

    private String extractJavaClassName(String content) {
        Matcher m = Pattern.compile("(?:class|interface|enum|record)\\s+(\\w+)").matcher(content);
        return m.find() ? m.group(1) : "Unknown";
    }

    private List<String> splitJavaMethods(String content) {
        // Simple heuristic: split on method signatures (public/private/protected + return type + name + parens)
        Pattern methodPattern = Pattern.compile(
                "^\\s{4}(?:@\\w+.*\\n)*\\s{4}(?:public|private|protected|static|final|synchronized|abstract|default|void|\\w+)\\s",
                Pattern.MULTILINE);
        Matcher m = methodPattern.matcher(content);

        List<Integer> starts = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
        }

        if (starts.isEmpty()) return List.of(content);

        List<String> methods = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int end = (i + 1 < starts.size()) ? starts.get(i + 1) : content.length();
            methods.add(content.substring(starts.get(i), end).trim());
        }
        return methods;
    }

    private String extractJavaHeader(String content) {
        // Everything before the first method (typically: package, imports, class decl, fields)
        Matcher m = Pattern.compile(
                "^\\s{4}(?:public|private|protected)\\s+(?!class|interface|enum|record)",
                Pattern.MULTILINE).matcher(content);
        if (m.find() && m.start() > 100) {
            return content.substring(0, m.start()).trim();
        }
        return "";
    }

    private String extractMethodName(String method) {
        Matcher m = Pattern.compile("(?:void|\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\(").matcher(method);
        return m.find() ? m.group(1) : "unknown";
    }

    private String extractTsExportName(String section) {
        Matcher m = Pattern.compile("export\\s+(?:default\\s+)?(?:function|const|class)\\s+(\\w+)").matcher(section);
        return m.find() ? m.group(1) : "anonymous";
    }

    // ── Embedding ──

    private List<ChunkEntry> embedChunksInBatches(List<RawChunk> chunks) throws IOException, InterruptedException {
        List<ChunkEntry> results = new ArrayList<>();
        if (chunks.isEmpty()) return results;

        int total = chunks.size();
        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, total);
            List<RawChunk> batch = chunks.subList(i, end);

            log.info("RAG-Index: Embedding Batch {}/{} ({} Chunks)...",
                    (i / BATCH_SIZE) + 1, (int) Math.ceil((double) total / BATCH_SIZE), batch.size());

            List<double[]> vectors = embedBatch(batch.stream().map(RawChunk::content).toList());

            for (int j = 0; j < batch.size(); j++) {
                RawChunk raw = batch.get(j);
                results.add(new ChunkEntry(
                        raw.content(), raw.filePath(), raw.category(),
                        raw.chunkType(), raw.name(), sha256(raw.content()),
                        vectors.get(j)
                ));
            }

            // Rate limiting: 1500 RPM for Gemini free tier → ~100ms between batches
            if (end < total) {
                Thread.sleep(150);
            }
        }

        return results;
    }

    private double[] embedSingle(String text, String taskType) throws IOException, InterruptedException {
        String url = GEMINI_EMBED_URL.formatted(geminiApiKey);

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode content = objectMapper.createObjectNode();
        ArrayNode parts = objectMapper.createArrayNode();
        parts.add(objectMapper.createObjectNode().put("text", truncate(text, 8000)));
        content.set("parts", parts);
        body.set("content", content);
        body.put("taskType", taskType);
        body.put("outputDimensionality", EMBEDDING_DIM);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IOException("Gemini Embedding Fehler: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode values = root.path("embedding").path("values");
        double[] vec = new double[EMBEDDING_DIM];
        for (int i = 0; i < Math.min(values.size(), EMBEDDING_DIM); i++) {
            vec[i] = values.get(i).asDouble();
        }
        return vec;
    }

    private List<double[]> embedBatch(List<String> texts) throws IOException, InterruptedException {
        String url = GEMINI_BATCH_EMBED_URL.formatted(geminiApiKey);

        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode requests = objectMapper.createArrayNode();
        for (String text : texts) {
            ObjectNode req = objectMapper.createObjectNode();
            req.put("model", "models/gemini-embedding-001");
            ObjectNode content = objectMapper.createObjectNode();
            ArrayNode parts = objectMapper.createArrayNode();
            parts.add(objectMapper.createObjectNode().put("text", truncate(text, 8000)));
            content.set("parts", parts);
            req.set("content", content);
            req.put("taskType", "RETRIEVAL_DOCUMENT");
            req.put("outputDimensionality", EMBEDDING_DIM);
            requests.add(req);
        }
        body.set("requests", requests);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            log.error("Gemini Batch Embedding Error {}: {}", response.statusCode(), response.body());
            throw new IOException("Batch-Embedding Fehler: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode embeddings = root.path("embeddings");

        List<double[]> result = new ArrayList<>();
        for (JsonNode emb : embeddings) {
            JsonNode values = emb.path("values");
            double[] vec = new double[EMBEDDING_DIM];
            for (int i = 0; i < Math.min(values.size(), EMBEDDING_DIM); i++) {
                vec[i] = values.get(i).asDouble();
            }
            result.add(vec);
        }
        return result;
    }

    // ── Cache persistence ──

    private Map<String, CachedChunk> loadCache(Path cacheFile) {
        if (!Files.exists(cacheFile)) return new HashMap<>();
        try {
            List<CachedChunk> list = objectMapper.readValue(cacheFile.toFile(),
                    new TypeReference<List<CachedChunk>>() {});
            Map<String, CachedChunk> map = new HashMap<>();
            for (CachedChunk c : list) {
                map.put(c.filePath() + "::" + c.name(), c);
            }
            return map;
        } catch (Exception e) {
            log.warn("RAG-Cache konnte nicht geladen werden, starte frisch: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private void saveCache(Path cacheFile, List<ChunkEntry> entries) {
        try {
            List<CachedChunk> cached = entries.stream().map(e -> new CachedChunk(
                    e.content(), e.filePath(), e.category(), e.chunkType(), e.name(), e.contentHash(),
                    Arrays.stream(e.vector()).boxed().toList()
            )).toList();
            objectMapper.writeValue(cacheFile.toFile(), cached);
            log.info("RAG-Cache gespeichert: {} Eintraege -> {}", cached.size(), cacheFile);
        } catch (IOException e) {
            log.warn("RAG-Cache konnte nicht gespeichert werden: {}", e.getMessage());
        }
    }

    // ── Math & Utils ──

    private static double cosineSimilarity(double[] a, double[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16); // short hash
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
