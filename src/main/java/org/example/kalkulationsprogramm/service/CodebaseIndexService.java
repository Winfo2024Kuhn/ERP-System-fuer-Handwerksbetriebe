package org.example.kalkulationsprogramm.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans the project codebase at startup and periodically to build a full-source
 * context for the KI-Hilfe system prompt. Includes complete file contents so the
 * AI can understand end-to-end workflows. Sensitive values are sanitized.
 */
@Slf4j
@Service
public class CodebaseIndexService {

    private static final int MAX_FILE_SIZE = 30_000;      // 30KB per file
    private static final int MAX_TOTAL_SIZE = 1_500_000;  // 1.5MB total cap

    private static final String JAVA_BASE = "src/main/java/org/example/kalkulationsprogramm";

    /** Pattern to match sensitive config values (passwords, keys, tokens) */
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "((?:password|passwd|secret|api[._-]?key|token|credentials)\\s*[=:]\\s*)([^\\s,;\"'}{]+)",
            Pattern.CASE_INSENSITIVE);

    @Value("${user.dir}")
    private String projectRoot;

    private volatile String cachedIndex = "";

    @PostConstruct
    void init() {
        rebuildIndex();
    }

    @Scheduled(fixedDelay = 600_000) // every 10 minutes
    void scheduledRebuild() {
        rebuildIndex();
    }

    public String getIndex() {
        return cachedIndex;
    }

    private void rebuildIndex() {
        try {
            Path root = Paths.get(projectRoot).toAbsolutePath().normalize();
            StringBuilder sb = new StringBuilder();

            // ── 1) Frontend Pages (most important for user navigation) ──
            sb.append("# === FRONTEND: Seiten (Pages) ===\n\n");
            appendDirectory(sb, root, "react-pc-frontend/src/pages", ".tsx", 3);

            // ── 2) Frontend Components ──
            sb.append("\n# === FRONTEND: Komponenten ===\n\n");
            appendDirectory(sb, root, "react-pc-frontend/src/components", ".tsx", 4);
            appendDirectory(sb, root, "react-pc-frontend/src/components", ".ts", 4);

            // ── 3) App routing / navigation / main entry ──
            sb.append("\n# === FRONTEND: Routing & App-Struktur ===\n\n");
            appendSingleFile(sb, root, "react-pc-frontend/src/App.tsx");
            appendSingleFile(sb, root, "react-pc-frontend/src/main.tsx");
            appendSingleFile(sb, root, "react-pc-frontend/src/types.ts");

            // ── 4) Frontend lib & utils ──
            sb.append("\n# === FRONTEND: Lib & Utils ===\n\n");
            appendDirectory(sb, root, "react-pc-frontend/src/lib", ".ts", 2);

            // ── 5) Zeiterfassung-App (Mobile PWA) ──
            sb.append("\n# === ZEITERFASSUNG-APP: Seiten ===\n\n");
            appendDirectory(sb, root, "react-zeiterfassung/src/pages", ".tsx", 3);
            sb.append("\n# === ZEITERFASSUNG-APP: Komponenten & Services ===\n\n");
            appendDirectory(sb, root, "react-zeiterfassung/src/components", ".tsx", 3);
            appendDirectory(sb, root, "react-zeiterfassung/src/services", ".ts", 2);
            appendSingleFile(sb, root, "react-zeiterfassung/src/App.tsx");

            // ── 6) Backend: REST-Controller ──
            sb.append("\n# === BACKEND: Controller (REST-Endpoints) ===\n\n");
            appendDirectory(sb, root, JAVA_BASE + "/controller", ".java", 3);

            // ── 7) Backend: Services (Geschäftslogik) ──
            sb.append("\n# === BACKEND: Services ===\n\n");
            appendDirectory(sb, root, JAVA_BASE + "/service", ".java", 3);

            // ── 8) Backend: Domain-Entities (JPA) ──
            sb.append("\n# === BACKEND: Domain-Entities ===\n\n");
            appendDirectory(sb, root, JAVA_BASE + "/domain", ".java", 3);

            // ── 9) Backend: DTOs ──
            sb.append("\n# === BACKEND: DTOs ===\n\n");
            appendDirectory(sb, root, JAVA_BASE + "/dto", ".java", 4);

            // ── 10) Backend: Mapper ──
            sb.append("\n# === BACKEND: Mapper ===\n\n");
            appendDirectory(sb, root, JAVA_BASE + "/mapper", ".java", 2);

            // ── 11) Backend: Config & Utils ──
            sb.append("\n# === BACKEND: Config & Utils ===\n\n");
            appendDirectory(sb, root, JAVA_BASE + "/config", ".java", 2);
            appendDirectory(sb, root, JAVA_BASE + "/util", ".java", 2);

            // ── 12) Dokumentation ──
            sb.append("\n# === Dokumentation ===\n\n");
            appendDirectory(sb, root, "docs", ".md", 3);

            // Enforce total cap
            String result = sb.toString();
            if (result.length() > MAX_TOTAL_SIZE) {
                result = result.substring(0, MAX_TOTAL_SIZE)
                        + "\n\n[...Index bei " + MAX_TOTAL_SIZE + " Zeichen gekürzt]\n";
            }

            cachedIndex = result;
            log.info("KI-Hilfe Codebase-Index erstellt: {} Zeichen", cachedIndex.length());

        } catch (Exception e) {
            log.error("Fehler beim Erstellen des Codebase-Index", e);
        }
    }

    /**
     * Appends all matching files from a directory (recursively) with their full content.
     */
    private void appendDirectory(StringBuilder sb, Path root, String relPath, String extension, int maxDepth) {
        Path dir = root.resolve(relPath);
        if (!Files.isDirectory(dir)) return;

        Path normalizedDir = dir.toAbsolutePath().normalize();
        try (Stream<Path> files = Files.walk(dir, maxDepth)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(extension))
                    .filter(p -> p.toAbsolutePath().normalize().startsWith(normalizedDir))
                    .filter(p -> !p.getFileName().toString().contains("Test"))  // skip test files
                    .sorted()
                    .forEach(f -> appendFileContent(sb, root, f));
        } catch (IOException e) {
            log.warn("Konnte Verzeichnis nicht lesen: {}", dir);
        }
    }

    /**
     * Appends a single file's content.
     */
    private void appendSingleFile(StringBuilder sb, Path root, String relPath) {
        Path file = root.resolve(relPath);
        if (Files.isRegularFile(file)) {
            appendFileContent(sb, root, file);
        }
    }

    /**
     * Reads a file, sanitizes secrets, and appends it with a header showing
     * the relative path.
     */
    private void appendFileContent(StringBuilder sb, Path root, Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);

            // Sanitize sensitive values
            content = SECRET_PATTERN.matcher(content).replaceAll("$1***REDACTED***");

            // Truncate oversized files
            if (content.length() > MAX_FILE_SIZE) {
                content = content.substring(0, MAX_FILE_SIZE)
                        + "\n// [...Datei gekürzt bei " + MAX_FILE_SIZE + " Zeichen]\n";
            }

            String relativePath = root.relativize(file).toString().replace('\\', '/');
            sb.append("## Datei: ").append(relativePath).append("\n```\n");
            sb.append(content);
            sb.append("\n```\n\n");

        } catch (IOException e) {
            log.warn("Konnte {} nicht lesen", file);
        }
    }
}
