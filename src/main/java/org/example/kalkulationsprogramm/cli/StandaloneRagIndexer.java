package org.example.kalkulationsprogramm.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Standalone RAG Indexer — läuft ohne Spring Boot Server.
 *
 * Nutze diesen für schnelle, isolierte Chunking & Embedding-Läufe:
 *   java -cp target/classes org.example.kalkulationsprogramm.cli.StandaloneRagIndexer <gemini-api-key> [project-root]
 *
 * Beispiel:
 *   java -cp target/classes org.example.kalkulationsprogramm.cli.StandaloneRagIndexer sk-... .
 */
public class StandaloneRagIndexer {

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

    private final String geminiApiKey;
    private final Path projectRoot;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    record RawChunk(String content, String filePath, String category, String chunkType, String name) {}
    record ChunkEntry(String content, String filePath, String category, String chunkType, String name,
                      String contentHash, List<Double> vector) {}

    public StandaloneRagIndexer(String geminiApiKey, String projectRootStr) {
        this.geminiApiKey = geminiApiKey;
        this.projectRoot = Paths.get(projectRootStr).toAbsolutePath().normalize();
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: StandaloneRagIndexer <gemini-api-key> [project-root]");
            System.err.println("Example: StandaloneRagIndexer sk-1234... .");
            System.exit(1);
        }

        String apiKey = args[0];
        String projectRoot = args.length > 1 ? args[1] : ".";

        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║  STANDALONE RAG INDEXER (keine Server nötig)              ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println("📂 Projekt-Root: " + projectRoot);
        System.out.println("🔑 API Key: " + apiKey.substring(0, 10) + "...");
        System.out.println();

        var indexer = new StandaloneRagIndexer(apiKey, projectRoot);
        indexer.run();
    }

    private void run() {
        try {
            Path cacheFile = projectRoot.resolve(".rag-cache.json");

            // 1) Load cache
            System.out.println("📚 Lade Cache...");
            Map<String, ChunkEntry> cache = loadCache(cacheFile);
            System.out.println("   ✓ " + cache.size() + " Einträge geladen");

            // 2) Chunk codebase
            System.out.println("✂️  Chunke Quellcode...");
            List<RawChunk> rawChunks = chunkCodebase();
            System.out.println("   ✓ " + rawChunks.size() + " Chunks extrahiert");

            // 3) Determine what needs embedding
            System.out.println("🔍 Vergleiche mit Cache...");
            List<RawChunk> needsEmbedding = new ArrayList<>();
            List<ChunkEntry> reusedEntries = new ArrayList<>();

            for (RawChunk raw : rawChunks) {
                String hash = sha256(raw.content());
                String cacheKey = raw.filePath() + "::" + raw.name();
                ChunkEntry cached = cache.get(cacheKey);

                if (cached != null && cached.contentHash().equals(hash) &&
                    cached.vector() != null && !cached.vector().isEmpty()) {
                    reusedEntries.add(cached);
                } else {
                    needsEmbedding.add(raw);
                }
            }
            System.out.println("   ✓ " + reusedEntries.size() + " aus Cache, " +
                              needsEmbedding.size() + " neu zu embedden");

            // 4) Embed new chunks
            System.out.println("🧠 Embedde neue Chunks...");
            List<ChunkEntry> newEntries = embedChunksInBatches(needsEmbedding);
            System.out.println("   ✓ " + newEntries.size() + " Chunks embedded");

            // 5) Save
            System.out.println("💾 Speichere Cache...");
            List<ChunkEntry> allEntries = new ArrayList<>();
            allEntries.addAll(reusedEntries);
            allEntries.addAll(newEntries);
            saveCache(cacheFile, allEntries);
            System.out.println("   ✓ " + allEntries.size() + " Einträge gespeichert in " + cacheFile);

            System.out.println();
            System.out.println("✅ Indexierung erfolgreich abgeschlossen!");
            System.out.println("   Cache: " + cacheFile);

        } catch (Exception e) {
            System.err.println("❌ Fehler: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private List<RawChunk> chunkCodebase() {
        List<RawChunk> chunks = new ArrayList<>();

        chunkDirectory(chunks, "react-pc-frontend/src/pages", ".tsx", "frontend-page", 3);
        chunkDirectory(chunks, "react-pc-frontend/src/components", ".tsx", "frontend-component", 4);
        chunkDirectory(chunks, "react-pc-frontend/src/components", ".ts", "frontend-component", 4);
        chunkSingleFile(chunks, "react-pc-frontend/src/App.tsx", "frontend-routing");
        chunkSingleFile(chunks, "react-pc-frontend/src/types.ts", "frontend-types");

        chunkDirectory(chunks, "react-zeiterfassung/src/pages", ".tsx", "zeiterfassung-page", 3);
        chunkDirectory(chunks, "react-zeiterfassung/src/components", ".tsx", "zeiterfassung-component", 3);

        chunkDirectory(chunks, JAVA_BASE + "/controller", ".java", "backend-controller", 3);
        chunkDirectory(chunks, JAVA_BASE + "/service", ".java", "backend-service", 3);
        chunkDirectory(chunks, JAVA_BASE + "/domain", ".java", "backend-entity", 3);
        chunkDirectory(chunks, JAVA_BASE + "/dto", ".java", "backend-dto", 4);

        chunkDirectory(chunks, "docs", ".md", "documentation", 3);

        return chunks;
    }

    private void chunkDirectory(List<RawChunk> chunks, String relPath, String ext, String category, int maxDepth) {
        Path dir = projectRoot.resolve(relPath);
        if (!Files.isDirectory(dir)) return;

        try (Stream<Path> files = Files.walk(dir, maxDepth)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(ext))
                    .filter(p -> !p.getFileName().toString().contains("Test"))
                    .sorted()
                    .forEach(f -> chunkFile(chunks, f, category));
        } catch (IOException e) {
            System.err.println("⚠️  Konnte Verzeichnis nicht lesen: " + dir);
        }
    }

    private void chunkSingleFile(List<RawChunk> chunks, String relPath, String category) {
        Path file = projectRoot.resolve(relPath);
        if (Files.isRegularFile(file)) {
            chunkFile(chunks, file, category);
        }
    }

    private void chunkFile(List<RawChunk> chunks, Path file, String category) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            content = SECRET_PATTERN.matcher(content).replaceAll("$1***REDACTED***");
            String relativePath = projectRoot.relativize(file).toString().replace('\\', '/');
            String fileName = file.getFileName().toString();

            if (fileName.endsWith(".java")) {
                chunkJava(chunks, content, relativePath, category);
            } else if (fileName.endsWith(".tsx") || fileName.endsWith(".ts")) {
                chunkTypeScript(chunks, content, relativePath, category);
            } else if (fileName.endsWith(".md")) {
                chunkMarkdown(chunks, content, relativePath, category);
            } else {
                addChunk(chunks, content, relativePath, category, "file", fileName);
            }
        } catch (IOException e) {
            System.err.println("⚠️  Konnte nicht lesen: " + file);
        }
    }

    private void chunkJava(List<RawChunk> chunks, String content, String filePath, String category) {
        String className = extractJavaClassName(content);
        List<String> methods = splitJavaMethods(content);

        if (methods.size() <= 1 || content.length() <= MAX_CHUNK_CHARS) {
            addChunk(chunks, content, filePath, category, "class", className);
            return;
        }

        String header = extractJavaHeader(content);
        if (!header.isBlank()) {
            addChunk(chunks, header, filePath, category, "class-header", className + " (header)");
        }

        for (String method : methods) {
            String methodName = extractMethodName(method);
            addChunk(chunks, method, filePath, category, "method", className + "." + methodName);
        }
    }

    private void chunkTypeScript(List<RawChunk> chunks, String content, String filePath, String category) {
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

        for (int i = 0; i < boundaries.size(); i++) {
            boundaries.get(i)[1] = (i + 1 < boundaries.size())
                    ? boundaries.get(i + 1)[0]
                    : content.length();
        }

        if (boundaries.get(0)[0] > 50) {
            addChunk(chunks, content.substring(0, boundaries.get(0)[0]).trim(),
                    filePath, category, "imports",
                    filePath.substring(filePath.lastIndexOf('/') + 1) + " (imports)");
        }

        for (int[] b : boundaries) {
            String section = content.substring(b[0], b[1]).trim();
            String name = extractTsExportName(section);
            addChunk(chunks, section, filePath, category, "component", name);
        }
    }

    private void chunkMarkdown(List<RawChunk> chunks, String content, String filePath, String category) {
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

    private void addChunk(List<RawChunk> chunks, String content, String filePath, String category,
                         String type, String name) {
        if (content.isBlank()) return;
        if (content.length() > MAX_CHUNK_CHARS) {
            content = content.substring(0, MAX_CHUNK_CHARS);
        }
        chunks.add(new RawChunk(content, filePath, category, type, name));
    }

    // ── Helpers ──
    private String extractJavaClassName(String content) {
        Matcher m = Pattern.compile("(?:class|interface|enum|record)\\s+(\\w+)").matcher(content);
        return m.find() ? m.group(1) : "Unknown";
    }

    List<String> splitJavaMethods(String content) {
        Pattern methodPattern = Pattern.compile(
                "^[ ]{4}(?>@[a-zA-Z0-9_]+[^\\r\\n]*+\\r?\\n[ ]{4})*+(?:(?:public|private|protected|static|final|synchronized|abstract|default|native|void)\\b|(?:<[a-zA-Z0-9_, ?&]*+>[ ]++)?[a-zA-Z0-9_]+(?:<[^>\\r\\n]*+>)?(?:\\[\\])*+[ ]++[a-zA-Z0-9_]+[ ]*+\\()",
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

            int batchNum = (i / BATCH_SIZE) + 1;
            int totalBatches = (int) Math.ceil((double) total / BATCH_SIZE);
            System.out.println("   Batch " + batchNum + "/" + totalBatches + " (" + batch.size() + " Chunks)...");

            List<List<Double>> vectors = embedBatch(batch.stream().map(RawChunk::content).toList());

            for (int j = 0; j < batch.size(); j++) {
                RawChunk raw = batch.get(j);
                results.add(new ChunkEntry(
                        raw.content(), raw.filePath(), raw.category(),
                        raw.chunkType(), raw.name(), sha256(raw.content()),
                        vectors.get(j)
                ));
            }

            // Rate limiting: 100ms between batches
            if (end < total) {
                Thread.sleep(150);
            }
        }

        return results;
    }

    private List<List<Double>> embedBatch(List<String> texts) throws IOException, InterruptedException {
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

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            throw new IOException("Batch-Embedding Fehler: " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode embeddings = root.path("embeddings");

        List<List<Double>> result = new ArrayList<>();
        for (JsonNode emb : embeddings) {
            JsonNode values = emb.path("values");
            List<Double> vec = new ArrayList<>(EMBEDDING_DIM);
            for (JsonNode v : values) {
                vec.add(v.asDouble());
            }
            result.add(vec);
        }
        return result;
    }

    // ── Cache ──
    private Map<String, ChunkEntry> loadCache(Path cacheFile) {
        if (!Files.exists(cacheFile)) return new HashMap<>();
        try {
            List<ChunkEntry> list = objectMapper.readValue(cacheFile.toFile(),
                    new TypeReference<List<ChunkEntry>>() {});
            Map<String, ChunkEntry> map = new HashMap<>();
            for (ChunkEntry c : list) {
                map.put(c.filePath() + "::" + c.name(), c);
            }
            return map;
        } catch (Exception e) {
            System.err.println("⚠️  Cache konnte nicht geladen werden: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private void saveCache(Path cacheFile, List<ChunkEntry> entries) throws IOException {
        List<ChunkEntry> cached = entries.stream().toList();
        objectMapper.writeValue(cacheFile.toFile(), cached);
    }

    // ── Utils ──
    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
