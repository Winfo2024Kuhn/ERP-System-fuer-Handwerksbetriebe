package org.example.kalkulationsprogramm.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.regex.Pattern
import kotlin.io.path.isRegularFile
import kotlin.math.sqrt

@Service
class LocalRagService(
    private val objectMapper: ObjectMapper,
    private val systemSettingsService: SystemSettingsService,
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    private val vectorStore = CopyOnWriteArrayList<ChunkEntry>()

    @Volatile
    private var ready = false

    @Value("\${ai.rag.enabled:false}")
    private var ragEnabled = false

    @Value("\${ai.rag.top-k:10}")
    private var topK = 10

    @Value("\${ai.rag.score-threshold:0.3}")
    private var scoreThreshold = 0.3

    @Value("\${user.dir}")
    private lateinit var projectRoot: String

    data class CodeChunkResult(
        val content: String,
        val filePath: String,
        val category: String,
        val chunkType: String,
        val name: String,
        val score: Double,
    ) {
        fun content(): String = content
        fun filePath(): String = filePath
        fun category(): String = category
        fun chunkType(): String = chunkType
        fun name(): String = name
        fun score(): Double = score
    }

    private data class ChunkEntry(
        val content: String,
        val filePath: String,
        val category: String,
        val chunkType: String,
        val name: String,
        val contentHash: String,
        val vector: DoubleArray,
    )

    private data class CachedChunk(
        val content: String = "",
        val filePath: String = "",
        val category: String = "",
        val chunkType: String = "",
        val name: String = "",
        val contentHash: String = "",
        val vector: List<Double> = emptyList(),
    )

    private data class RawChunk(
        val content: String,
        val filePath: String,
        val category: String,
        val chunkType: String,
        val name: String,
    )

    fun isAvailable(): Boolean = ragEnabled && ready

    fun isEnabled(): Boolean = ragEnabled

    fun isReady(): Boolean = ready

    @Throws(IOException::class, InterruptedException::class)
    fun search(query: String, pageContext: String?, currentRoute: String?): List<CodeChunkResult> {
        if (!ready || vectorStore.isEmpty()) return emptyList()
        val enrichedQuery = if (!pageContext.isNullOrBlank()) "$query\n\nSeitenkontext: $pageContext" else query

        val t0 = System.currentTimeMillis()
        val queryVec = embedSingle(enrichedQuery, "RETRIEVAL_QUERY")
        log.info("    Embedding erstellt in {} ms", System.currentTimeMillis() - t0)

        val t1 = System.currentTimeMillis()
        val currentPageChunks = findChunksForRoute(currentRoute)
        val currentPagePaths = currentPageChunks.map { "${it.filePath}::${it.name}" }.toSet()
        if (currentPageChunks.isNotEmpty()) {
            log.info(
                "    Aktuelle Seite '{}': {} Chunks deterministisch eingebunden ({})",
                currentRoute,
                currentPageChunks.size,
                currentPageChunks.map { it.name },
            )
        }

        val similarityResults = vectorStore.mapNotNull { entry ->
            val key = "${entry.filePath}::${entry.name}"
            if (key in currentPagePaths) return@mapNotNull null
            val score = cosineSimilarity(queryVec, entry.vector)
            if (score >= scoreThreshold) {
                CodeChunkResult(entry.content, entry.filePath, entry.category, entry.chunkType, entry.name, score)
            } else {
                null
            }
        }.sortedByDescending { it.score }

        val pageSlots = minOf(currentPageChunks.size, topK / 2)
        val remainingSlots = topK - pageSlots
        val frontendSimilarity = similarityResults.filter { it.category.startsWith("frontend") || it.category.startsWith("zeiterfassung") }
        val otherSimilarity = similarityResults.filterNot { it.category.startsWith("frontend") || it.category.startsWith("zeiterfassung") }
        val frontendSlots = minOf(frontendSimilarity.size, maxOf(remainingSlots * 3 / 10, 1))
        val backendSlots = remainingSlots - frontendSlots

        val finalResults = mutableListOf<CodeChunkResult>()
        finalResults += currentPageChunks.take(pageSlots)
        finalResults += frontendSimilarity.take(frontendSlots)
        finalResults += otherSimilarity.take(backendSlots)

        var remaining = topK - finalResults.size
        var i = frontendSlots
        while (i < frontendSimilarity.size && remaining > 0) {
            finalResults += frontendSimilarity[i++]
            remaining--
        }
        i = backendSlots
        while (i < otherSimilarity.size && remaining > 0) {
            finalResults += otherSimilarity[i++]
            remaining--
        }

        log.info(
            "    Lokale Vektor-Suche: {} Treffer in {} ms (seiten-chunks={}, top-k={}, threshold={}, store={})",
            finalResults.size,
            System.currentTimeMillis() - t1,
            pageSlots,
            topK,
            scoreThreshold,
            vectorStore.size,
        )
        return finalResults
    }

    @Throws(IOException::class, InterruptedException::class)
    fun search(query: String, pageContext: String?): List<CodeChunkResult> = search(query, pageContext, null)

    private fun findChunksForRoute(route: String?): List<CodeChunkResult> {
        if (route.isNullOrBlank()) return emptyList()
        val keywords = ROUTE_TO_FILE_KEYWORDS[route].orEmpty()
        if (keywords.isEmpty()) return emptyList()
        return vectorStore.filter { entry -> keywords.any { entry.filePath.contains(it) } }
            .map { CodeChunkResult(it.content, it.filePath, it.category, it.chunkType, it.name, 1.0) }
    }

    fun buildContextFromResults(results: List<CodeChunkResult>): String {
        if (results.isEmpty()) return ""
        val sb = StringBuilder()
        results.forEachIndexed { index, r ->
            val label = if (r.score >= 1.0) ">>> AKTUELLE SEITE DES BENUTZERS <<<" else "Score: %.2f".format(r.score)
            sb.append("### [%d] %s -- %s (%s) | %s\n".format(index + 1, r.filePath, r.name, r.chunkType, label))
            sb.append("```\n").append(r.content).append("\n```\n\n")
        }
        return sb.toString()
    }

    @PostConstruct
    fun init() {
        if (!ragEnabled) {
            log.info("Lokales RAG ist deaktiviert (ai.rag.enabled=false)")
            return
        }
        val geminiApiKey = systemSettingsService.geminiApiKey
        if (geminiApiKey.isNullOrBlank()) {
            log.warn("Lokales RAG: Gemini API Key fehlt, RAG deaktiviert")
            ragEnabled = false
            return
        }
        Thread.ofVirtual().name("rag-indexer").start(this::buildIndex)
    }

    private fun buildIndex() {
        try {
            val root = Paths.get(projectRoot).toAbsolutePath().normalize()
            val cacheFile = root.resolve(".rag-cache.json")
            val cache = loadCache(cacheFile)
            log.info("RAG-Index: Cache geladen mit {} Eintraegen", cache.size)

            val rawChunks = chunkCodebase(root)
            log.info("RAG-Index: {} Chunks aus Codebase extrahiert", rawChunks.size)

            if (rawChunks.isEmpty() && cache.isNotEmpty()) {
                val cachedEntries = cache.values.filter { it.vector.isNotEmpty() }
                    .map { ChunkEntry(it.content, it.filePath, it.category, it.chunkType, it.name, it.contentHash, it.vector.toDoubleArray()) }
                vectorStore.clear()
                vectorStore.addAll(cachedEntries)
                ready = true
                log.info("RAG-Index FERTIG (Cache-Modus): {} Chunks geladen, bereit fuer Anfragen", vectorStore.size)
                return
            }

            val needsEmbedding = mutableListOf<RawChunk>()
            val reusedEntries = mutableListOf<ChunkEntry>()
            rawChunks.forEach { raw ->
                val hash = sha256(raw.content)
                val cacheKey = "${raw.filePath}::${raw.name}"
                val cached = cache[cacheKey]
                if (cached != null && cached.contentHash == hash && cached.vector.isNotEmpty()) {
                    reusedEntries += ChunkEntry(raw.content, raw.filePath, raw.category, raw.chunkType, raw.name, hash, cached.vector.toDoubleArray())
                } else {
                    needsEmbedding += raw
                }
            }
            log.info("RAG-Index: {} aus Cache, {} neu zu embedden", reusedEntries.size, needsEmbedding.size)

            val newEntries = embedChunksInBatches(needsEmbedding)
            vectorStore.clear()
            vectorStore.addAll(reusedEntries)
            vectorStore.addAll(newEntries)
            saveCache(cacheFile, vectorStore)
            ready = true
            log.info("RAG-Index FERTIG: {} Chunks gespeichert, bereit fuer Anfragen", vectorStore.size)
        } catch (e: Exception) {
            log.error("RAG-Index Aufbau fehlgeschlagen: {}", e.message, e)
        }
    }

    private fun chunkCodebase(root: Path): List<RawChunk> {
        val chunks = mutableListOf<RawChunk>()
        chunkDirectory(chunks, root, "react-pc-frontend/src/pages", ".tsx", "frontend-page", 3)
        chunkDirectory(chunks, root, "react-pc-frontend/src/components", ".tsx", "frontend-component", 4)
        chunkDirectory(chunks, root, "react-pc-frontend/src/components", ".ts", "frontend-component", 4)
        chunkSingleFile(chunks, root, "react-pc-frontend/src/App.tsx", "frontend-routing")
        chunkSingleFile(chunks, root, "react-pc-frontend/src/types.ts", "frontend-types")
        chunkDirectory(chunks, root, "react-zeiterfassung/src/pages", ".tsx", "zeiterfassung-page", 3)
        chunkDirectory(chunks, root, "react-zeiterfassung/src/components", ".tsx", "zeiterfassung-component", 3)
        chunkDirectory(chunks, root, "$JAVA_BASE/controller", ".java", "backend-controller", 3)
        chunkDirectory(chunks, root, "$JAVA_BASE/service", ".java", "backend-service", 3)
        chunkDirectory(chunks, root, "$JAVA_BASE/domain", ".java", "backend-entity", 3)
        chunkDirectory(chunks, root, "$JAVA_BASE/dto", ".java", "backend-dto", 4)
        chunkDirectory(chunks, root, "docs", ".md", "documentation", 3)
        return chunks
    }

    private fun chunkDirectory(chunks: MutableList<RawChunk>, root: Path, relPath: String, ext: String, category: String, maxDepth: Int) {
        val dir = root.resolve(relPath)
        if (!Files.isDirectory(dir)) return
        val normalizedDir = dir.toAbsolutePath().normalize()
        try {
            Files.walk(dir, maxDepth).use { files ->
                files.filter(Files::isRegularFile)
                    .filter { it.toString().endsWith(ext) }
                    .filter { it.toAbsolutePath().normalize().startsWith(normalizedDir) }
                    .filter { !it.fileName.toString().contains("Test") }
                    .sorted()
                    .forEach { chunkFile(chunks, root, it, category) }
            }
        } catch (_: IOException) {
            log.warn("Konnte Verzeichnis nicht lesen: {}", dir)
        }
    }

    private fun chunkSingleFile(chunks: MutableList<RawChunk>, root: Path, relPath: String, category: String) {
        val file = root.resolve(relPath)
        if (file.isRegularFile()) chunkFile(chunks, root, file, category)
    }

    private fun chunkFile(chunks: MutableList<RawChunk>, root: Path, file: Path, category: String) {
        try {
            var content = Files.readString(file, StandardCharsets.UTF_8)
            content = SECRET_PATTERN.matcher(content).replaceAll("$1***REDACTED***")
            val relativePath = root.relativize(file).toString().replace('\\', '/')
            val fileName = file.fileName.toString()
            when {
                fileName.endsWith(".java") -> chunkJava(chunks, content, relativePath, category)
                fileName.endsWith(".tsx") || fileName.endsWith(".ts") -> chunkTypeScript(chunks, content, relativePath, category)
                fileName.endsWith(".md") -> chunkMarkdown(chunks, content, relativePath, category)
                else -> addChunk(chunks, content, relativePath, category, "file", fileName)
            }
        } catch (_: IOException) {
            log.warn("Konnte {} nicht lesen", file)
        }
    }

    private fun chunkJava(chunks: MutableList<RawChunk>, content: String, filePath: String, category: String) {
        val className = extractJavaClassName(content)
        val methods = splitJavaMethods(content)
        if (methods.size <= 1 || content.length <= MAX_CHUNK_CHARS) {
            addChunk(chunks, content, filePath, category, "class", className)
            return
        }
        val header = extractJavaHeader(content)
        if (header.isNotBlank()) addChunk(chunks, header, filePath, category, "class-header", "$className (header)")
        methods.forEach { method -> addChunk(chunks, method, filePath, category, "method", "$className.${extractMethodName(method)}") }
    }

    private fun chunkTypeScript(chunks: MutableList<RawChunk>, content: String, filePath: String, category: String) {
        val matcher = TS_EXPORT_PATTERN.matcher(content)
        val starts = mutableListOf<Int>()
        while (matcher.find()) starts += matcher.start()
        if (starts.size <= 1 || content.length <= MAX_CHUNK_CHARS) {
            addChunk(chunks, content, filePath, category, "component", filePath.substringAfterLast('/'))
            return
        }
        if (starts.first() > 50) {
            addChunk(chunks, content.substring(0, starts.first()).trim(), filePath, category, "imports", "${filePath.substringAfterLast('/')} (imports)")
        }
        starts.forEachIndexed { index, start ->
            val end = starts.getOrNull(index + 1) ?: content.length
            val section = content.substring(start, end).trim()
            addChunk(chunks, section, filePath, category, "component", extractTsExportName(section))
        }
    }

    private fun chunkMarkdown(chunks: MutableList<RawChunk>, content: String, filePath: String, category: String) {
        val sections = content.split(Regex("(?=^## )", RegexOption.MULTILINE))
        if (sections.size <= 1 || content.length <= MAX_CHUNK_CHARS) {
            addChunk(chunks, content, filePath, category, "document", filePath.substringAfterLast('/'))
            return
        }
        sections.map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            val heading = it.lineSequence().firstOrNull().orEmpty().replace("#", "").trim().ifBlank { "intro" }
            addChunk(chunks, it, filePath, category, "section", heading)
        }
    }

    private fun addChunk(chunks: MutableList<RawChunk>, content: String, filePath: String, category: String, type: String, name: String) {
        if (content.isBlank()) return
        chunks += RawChunk(content.take(MAX_CHUNK_CHARS), filePath, category, type, name)
    }

    private fun extractJavaClassName(content: String): String =
        JAVA_CLASS_PATTERN.matcher(content).let { if (it.find()) it.group(1) else "Unknown" }

    private fun splitJavaMethods(content: String): List<String> {
        val matcher = JAVA_METHOD_PATTERN.matcher(content)
        val starts = mutableListOf<Int>()
        while (matcher.find()) starts += matcher.start()
        if (starts.isEmpty()) return listOf(content)
        return starts.mapIndexed { index, start -> content.substring(start, starts.getOrNull(index + 1) ?: content.length).trim() }
    }

    private fun extractJavaHeader(content: String): String {
        val matcher = JAVA_HEADER_PATTERN.matcher(content)
        return if (matcher.find() && matcher.start() > 100) content.substring(0, matcher.start()).trim() else ""
    }

    private fun extractMethodName(method: String): String =
        JAVA_METHOD_NAME_PATTERN.matcher(method).let { if (it.find()) it.group(1) else "unknown" }

    private fun extractTsExportName(section: String): String =
        TS_EXPORT_NAME_PATTERN.matcher(section).let { if (it.find()) it.group(1) else "anonymous" }

    @Throws(IOException::class, InterruptedException::class)
    private fun embedChunksInBatches(chunks: List<RawChunk>): List<ChunkEntry> {
        val results = mutableListOf<ChunkEntry>()
        if (chunks.isEmpty()) return results
        chunks.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            log.info("RAG-Index: Embedding Batch {}/{} ({} Chunks)...", batchIndex + 1, kotlin.math.ceil(chunks.size.toDouble() / BATCH_SIZE).toInt(), batch.size)
            val vectors = embedBatch(batch.map { it.content })
            batch.forEachIndexed { index, raw ->
                results += ChunkEntry(raw.content, raw.filePath, raw.category, raw.chunkType, raw.name, sha256(raw.content), vectors[index])
            }
            if ((batchIndex + 1) * BATCH_SIZE < chunks.size) Thread.sleep(150)
        }
        return results
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun embedSingle(text: String, taskType: String): DoubleArray {
        val url = GEMINI_EMBED_URL.format(systemSettingsService.geminiApiKey)
        val body = objectMapper.createObjectNode()
        val content = objectMapper.createObjectNode()
        val parts = objectMapper.createArrayNode()
        parts.add(objectMapper.createObjectNode().put("text", truncate(text, 8000)))
        content.set<ArrayNode>("parts", parts)
        body.set<ObjectNode>("content", content)
        body.put("taskType", taskType)
        body.put("outputDimensionality", EMBEDDING_DIM)

        val response = httpClient.send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
        )
        if (response.statusCode() != 200) throw IOException("Gemini Embedding Fehler: ${response.statusCode()}")
        val values = objectMapper.readTree(response.body()).path("embedding").path("values")
        return DoubleArray(EMBEDDING_DIM) { i -> if (i < values.size()) values.get(i).asDouble() else 0.0 }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun embedBatch(texts: List<String>): List<DoubleArray> {
        val url = GEMINI_BATCH_EMBED_URL.format(systemSettingsService.geminiApiKey)
        val body = objectMapper.createObjectNode()
        val requests = objectMapper.createArrayNode()
        texts.forEach { text ->
            val req = objectMapper.createObjectNode()
            req.put("model", "models/gemini-embedding-001")
            val content = objectMapper.createObjectNode()
            val parts = objectMapper.createArrayNode()
            parts.add(objectMapper.createObjectNode().put("text", truncate(text, 8000)))
            content.set<ArrayNode>("parts", parts)
            req.set<ObjectNode>("content", content)
            req.put("taskType", "RETRIEVAL_DOCUMENT")
            req.put("outputDimensionality", EMBEDDING_DIM)
            requests.add(req)
        }
        body.set<ArrayNode>("requests", requests)
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
            .build()

        var response: HttpResponse<String>? = null
        for (attempt in 1..3) {
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                break
            } catch (e: java.net.http.HttpTimeoutException) {
                if (attempt == 3) {
                    log.error("Gemini Batch Embedding: Timeout nach {} Versuchen", 3)
                    throw e
                }
                val waitMs = 2000L * attempt
                log.warn("Gemini Batch Embedding: Timeout (Versuch {}/{}), warte {}ms...", attempt, 3, waitMs)
                Thread.sleep(waitMs)
            }
        }
        val res = response ?: throw IOException("Batch-Embedding ohne Antwort")
        if (res.statusCode() != 200) {
            log.error("Gemini Batch Embedding Error {}: {}", res.statusCode(), res.body())
            throw IOException("Batch-Embedding Fehler: ${res.statusCode()}")
        }
        val embeddings = objectMapper.readTree(res.body()).path("embeddings")
        return embeddings.map { emb ->
            val values = emb.path("values")
            DoubleArray(EMBEDDING_DIM) { i -> if (i < values.size()) values.get(i).asDouble() else 0.0 }
        }
    }

    private fun loadCache(cacheFile: Path): Map<String, CachedChunk> {
        if (!Files.exists(cacheFile)) return emptyMap()
        return try {
            val list = objectMapper.readValue(cacheFile.toFile(), object : TypeReference<List<CachedChunk>>() {})
            list.associateBy { "${it.filePath}::${it.name}" }
        } catch (e: Exception) {
            log.warn("RAG-Cache konnte nicht geladen werden, starte frisch: {}", e.message)
            emptyMap()
        }
    }

    private fun saveCache(cacheFile: Path, entries: List<ChunkEntry>) {
        try {
            val cached = entries.map {
                CachedChunk(it.content, it.filePath, it.category, it.chunkType, it.name, it.contentHash, it.vector.toList())
            }
            objectMapper.writeValue(cacheFile.toFile(), cached)
            log.info("RAG-Cache gespeichert: {} Eintraege -> {}", cached.size, cacheFile)
        } catch (e: IOException) {
            log.warn("RAG-Cache konnte nicht gespeichert werden: {}", e.message)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(LocalRagService::class.java)
        private const val GEMINI_EMBED_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key=%s"
        private const val GEMINI_BATCH_EMBED_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:batchEmbedContents?key=%s"
        private const val EMBEDDING_DIM = 768
        private const val MAX_CHUNK_CHARS = 6000
        private const val BATCH_SIZE = 20
        private const val JAVA_BASE = "src/main/java/org/example/kalkulationsprogramm"
        private val SECRET_PATTERN = Pattern.compile("((?:password|passwd|secret|api[._-]?key|token|credentials)\\s*[=:]\\s*)([^\\s,;\"'}{]+)", Pattern.CASE_INSENSITIVE)
        private val JAVA_CLASS_PATTERN = Pattern.compile("(?:class|interface|enum|record)\\s+(\\w+)")
        private val JAVA_METHOD_PATTERN = Pattern.compile("^\\s{4}(?:@\\w+.*\\n)*\\s{4}(?:public|private|protected|static|final|synchronized|abstract|default|void|\\w+)\\s", Pattern.MULTILINE)
        private val JAVA_HEADER_PATTERN = Pattern.compile("^\\s{4}(?:public|private|protected)\\s+(?!class|interface|enum|record)", Pattern.MULTILINE)
        private val JAVA_METHOD_NAME_PATTERN = Pattern.compile("(?:void|\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\(")
        private val TS_EXPORT_PATTERN = Pattern.compile("^(export\\s+(?:default\\s+)?(?:function|const|class)\\s+\\w+)", Pattern.MULTILINE)
        private val TS_EXPORT_NAME_PATTERN = Pattern.compile("export\\s+(?:default\\s+)?(?:function|const|class)\\s+(\\w+)")

        private val ROUTE_TO_FILE_KEYWORDS = mapOf(
            "/projekte" to listOf("ProjektEditor"),
            "/anfragen" to listOf("AnfrageEditor"),
            "/kunden" to listOf("Kundeneditor"),
            "/lieferanten" to listOf("LieferantenEditor"),
            "/artikel" to listOf("ArtikelEditor"),
            "/bestellungen" to listOf("BestellungenUebersicht"),
            "/bestellungen/bedarf" to listOf("BestellungEditor"),
            "/textbausteine" to listOf("TextbausteinEditor"),
            "/leistungen" to listOf("Leistungseditor"),
            "/arbeitsgaenge" to listOf("ArbeitsgangEditor"),
            "/produktkategorien" to listOf("ProduktkategorieEditor"),
            "/mitarbeiter" to listOf("MitarbeiterEditor"),
            "/arbeitszeitarten" to listOf("ArbeitszeitartEditor"),
            "/kalender" to listOf("TerminKalender"),
            "/emails" to listOf("EmailCenter"),
            "/formulare" to listOf("FormularwesenEditor"),
            "/offeneposten" to listOf("OffenePostenEditor"),
            "/rechnungsuebersicht" to listOf("RechnungsuebersichtEditor"),
            "/analyse" to listOf("ErfolgsanalyseEditor"),
            "/miete" to listOf("MietabrechnungEditor"),
            "/benutzer" to listOf("BenutzerEditor"),
            "/firma" to listOf("FirmaEditor"),
            "/abteilung-berechtigungen" to listOf("AbteilungBerechtigungenEditor"),
            "/zeitbuchungen" to listOf("ZeiterfassungKalender"),
            "/auswertung" to listOf("ZeiterfassungAuswertung"),
            "/steuerberater" to listOf("ZeiterfassungSteuerberater"),
            "/zeitkonten" to listOf("ZeiterfassungZeitkonten"),
            "/feiertage" to listOf("ZeiterfassungFeiertage"),
            "/urlaubsantraege" to listOf("Urlaubsantraege"),
            "/dokument-editor" to listOf("DocumentEditorPage"),
        )

        private fun cosineSimilarity(a: DoubleArray, b: DoubleArray): Double {
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (i in a.indices) {
                dot += a[i] * b[i]
                normA += a[i] * a[i]
                normB += b[i] * b[i]
            }
            val denom = sqrt(normA) * sqrt(normB)
            return if (denom == 0.0) 0.0 else dot / denom
        }

        private fun sha256(input: String): String =
            try {
                val hash = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(StandardCharsets.UTF_8))
                HexFormat.of().formatHex(hash).substring(0, 16)
            } catch (_: Exception) {
                input.hashCode().toString()
            }

        private fun truncate(s: String, max: Int): String = if (s.length <= max) s else s.substring(0, max)
    }
}
