package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provides tool implementations for the KI-Hilfe agentic function calling.
 * Each method corresponds to a Gemini function declaration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KiToolService {

    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final int MAX_FILE_SIZE = 30_000;
    private static final int MAX_LIST_RESULTS = 50;
    private static final int MAX_DB_ROWS = 50;
    private static final int MAX_EMAIL_RESULTS = 10;
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(password|passwd|secret|api[._-]?key|token|credentials)\\s*[=:]",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ALLOWED_SQL = Pattern.compile(
            "^\\s*SELECT\\b", Pattern.CASE_INSENSITIVE);

    private final LocalRagService localRagService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Builds the Gemini functionDeclarations JSON array for all available tools.
     */
    public ArrayNode buildFunctionDeclarations() {
        ArrayNode declarations = objectMapper.createArrayNode();

        // 1) search_code
        ObjectNode searchCode = objectMapper.createObjectNode();
        searchCode.put("name", "search_code");
        searchCode.put("description",
                "Sucht im Quellcode des ERP-Systems nach relevanten Code-Abschnitten. " +
                "Nutze dies um herauszufinden, welche Buttons, Formulare oder Funktionen auf einer Seite existieren, " +
                "oder um Backend-Services und Entities zu finden.");
        ObjectNode searchParams = objectMapper.createObjectNode();
        searchParams.put("type", "OBJECT");
        ObjectNode searchProps = objectMapper.createObjectNode();
        ObjectNode queryProp = objectMapper.createObjectNode();
        queryProp.put("type", "STRING");
        queryProp.put("description", "Die Suchanfrage, z.B. 'Projektdetail Seite Buttons' oder 'RechnungService erstellen'");
        searchProps.set("query", queryProp);
        searchParams.set("properties", searchProps);
        ArrayNode searchRequired = objectMapper.createArrayNode();
        searchRequired.add("query");
        searchParams.set("required", searchRequired);
        searchCode.set("parameters", searchParams);
        declarations.add(searchCode);

        // 2) read_file
        ObjectNode readFile = objectMapper.createObjectNode();
        readFile.put("name", "read_file");
        readFile.put("description",
                "Liest den Inhalt einer Datei aus dem Projekt. Nutze dies um den genauen Quellcode " +
                "einer bestimmten Komponente, eines Services oder einer Konfigurationsdatei zu sehen.");
        ObjectNode readParams = objectMapper.createObjectNode();
        readParams.put("type", "OBJECT");
        ObjectNode readProps = objectMapper.createObjectNode();
        ObjectNode pathProp = objectMapper.createObjectNode();
        pathProp.put("type", "STRING");
        pathProp.put("description", "Relativer Pfad zur Datei, z.B. 'react-pc-frontend/src/pages/ProjektDetail.tsx'");
        readProps.set("path", pathProp);
        readParams.set("properties", readProps);
        ArrayNode readRequired = objectMapper.createArrayNode();
        readRequired.add("path");
        readParams.set("required", readRequired);
        readFile.set("parameters", readParams);
        declarations.add(readFile);

        // 3) list_files
        ObjectNode listFiles = objectMapper.createObjectNode();
        listFiles.put("name", "list_files");
        listFiles.put("description",
                "Listet Dateien in einem Verzeichnis des Projekts auf. " +
                "Nutze dies um die Projektstruktur zu erkunden oder bestimmte Dateien zu finden.");
        ObjectNode listParams = objectMapper.createObjectNode();
        listParams.put("type", "OBJECT");
        ObjectNode listProps = objectMapper.createObjectNode();
        ObjectNode dirProp = objectMapper.createObjectNode();
        dirProp.put("type", "STRING");
        dirProp.put("description", "Relativer Pfad zum Verzeichnis, z.B. 'react-pc-frontend/src/pages'");
        listProps.set("directory", dirProp);
        listParams.set("properties", listProps);
        ArrayNode listRequired = objectMapper.createArrayNode();
        listRequired.add("directory");
        listParams.set("required", listRequired);
        listFiles.set("parameters", listParams);
        declarations.add(listFiles);

        // 4) query_database
        ObjectNode queryDb = objectMapper.createObjectNode();
        queryDb.put("name", "query_database");
        queryDb.put("description",
                "Fuehrt eine READ-ONLY SQL-Abfrage auf der ERP-Datenbank aus. NUR SELECT-Statements erlaubt. " +
                "Nutze dies um Datenbank-Schema, Tabellenstrukturen oder konkrete Daten abzufragen. " +
                "Beispiel: 'SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE()'");
        ObjectNode dbParams = objectMapper.createObjectNode();
        dbParams.put("type", "OBJECT");
        ObjectNode dbProps = objectMapper.createObjectNode();
        ObjectNode sqlProp = objectMapper.createObjectNode();
        sqlProp.put("type", "STRING");
        sqlProp.put("description", "Das SQL SELECT-Statement");
        dbProps.set("sql", sqlProp);
        dbParams.set("properties", dbProps);
        ArrayNode dbRequired = objectMapper.createArrayNode();
        dbRequired.add("sql");
        dbParams.set("required", dbRequired);
        queryDb.set("parameters", dbParams);
        declarations.add(queryDb);

        // 5) search_emails
        ObjectNode searchEmails = objectMapper.createObjectNode();
        searchEmails.put("name", "search_emails");
        searchEmails.put("description",
                "Durchsucht E-Mails nach Betreff, Absender, Empfaenger oder Inhalt. " +
                "Gibt eine Liste mit passenden E-Mails zurueck, inkl. Direkt-Links zum EmailCenter. " +
                "Nutze dies wenn der Benutzer nach einer bestimmten E-Mail fragt, z.B. 'Zeig mir die Mail von Firma XY'.");
        ObjectNode emailParams = objectMapper.createObjectNode();
        emailParams.put("type", "OBJECT");
        ObjectNode emailProps = objectMapper.createObjectNode();
        ObjectNode emailQueryProp = objectMapper.createObjectNode();
        emailQueryProp.put("type", "STRING");
        emailQueryProp.put("description", "Suchbegriff, z.B. Absendername, Betreff oder Stichwort aus dem Inhalt");
        emailProps.set("query", emailQueryProp);
        emailParams.set("properties", emailProps);
        ArrayNode emailRequired = objectMapper.createArrayNode();
        emailRequired.add("query");
        emailParams.set("required", emailRequired);
        searchEmails.set("parameters", emailParams);
        declarations.add(searchEmails);

        return declarations;
    }

    /**
     * Executes a tool call and returns the result as a string.
     */
    public String executeTool(String toolName, JsonNode args) {
        try {
            return switch (toolName) {
                case "search_code" -> searchCode(args.path("query").asText(""));
                case "read_file" -> readFile(args.path("path").asText(""));
                case "list_files" -> listFiles(args.path("directory").asText(""));
                case "query_database" -> queryDatabase(args.path("sql").asText(""));
                case "search_emails" -> searchEmails(args.path("query").asText(""));
                default -> "Unbekanntes Tool: " + toolName;
            };
        } catch (Exception e) {
            log.warn("Tool '{}' Fehler: {}", toolName, e.getMessage());
            return "Fehler bei " + toolName + ": " + e.getMessage();
        }
    }

    private String searchCode(String query) {
        if (query == null || query.isBlank()) {
            return "Keine Suchanfrage angegeben.";
        }
        try {
            if (!localRagService.isAvailable()) {
                return "Code-Suche ist derzeit nicht verfuegbar (RAG nicht bereit).";
            }
            var results = localRagService.search(query, null, null);
            if (results.isEmpty()) {
                return "Keine relevanten Code-Abschnitte gefunden fuer: " + query;
            }
            return localRagService.buildContextFromResults(results);
        } catch (Exception e) {
            log.warn("search_code Fehler: {}", e.getMessage());
            return "Code-Suche fehlgeschlagen: " + e.getMessage();
        }
    }

    private String readFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "Kein Dateipfad angegeben.";
        }

        // Path traversal protection
        Path resolved = PROJECT_ROOT.resolve(relativePath).normalize();
        if (!resolved.startsWith(PROJECT_ROOT)) {
            return "Zugriff verweigert: Pfad liegt ausserhalb des Projektverzeichnisses.";
        }

        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            return "Datei nicht gefunden: " + relativePath;
        }

        try {
            long size = Files.size(resolved);
            if (size > MAX_FILE_SIZE) {
                String content = Files.readString(resolved);
                content = sanitizeSecrets(content);
                return content.substring(0, MAX_FILE_SIZE) + "\n\n... (abgeschnitten bei " + MAX_FILE_SIZE + " Zeichen, Datei hat " + size + " Bytes)";
            }
            String content = Files.readString(resolved);
            return sanitizeSecrets(content);
        } catch (IOException e) {
            return "Fehler beim Lesen: " + e.getMessage();
        }
    }

    private String listFiles(String directory) {
        if (directory == null || directory.isBlank()) {
            return "Kein Verzeichnis angegeben.";
        }

        Path resolved = PROJECT_ROOT.resolve(directory).normalize();
        if (!resolved.startsWith(PROJECT_ROOT)) {
            return "Zugriff verweigert: Pfad liegt ausserhalb des Projektverzeichnisses.";
        }

        if (!Files.exists(resolved) || !Files.isDirectory(resolved)) {
            return "Verzeichnis nicht gefunden: " + directory;
        }

        try (Stream<Path> stream = Files.list(resolved)) {
            List<String> entries = new ArrayList<>();
            stream.sorted().limit(MAX_LIST_RESULTS).forEach(p -> {
                String name = p.getFileName().toString();
                if (Files.isDirectory(p)) {
                    entries.add(name + "/");
                } else {
                    entries.add(name);
                }
            });

            if (entries.isEmpty()) {
                return "Verzeichnis ist leer: " + directory;
            }
            return String.join("\n", entries);
        } catch (IOException e) {
            return "Fehler beim Auflisten: " + e.getMessage();
        }
    }

    private String queryDatabase(String sql) {
        if (sql == null || sql.isBlank()) {
            return "Kein SQL-Statement angegeben.";
        }

        // Only allow SELECT statements
        if (!ALLOWED_SQL.matcher(sql).find()) {
            return "Nur SELECT-Statements sind erlaubt. Schreibende Operationen (INSERT, UPDATE, DELETE, DROP, etc.) sind verboten.";
        }

        // Block dangerous keywords even within SELECT
        String upperSql = sql.toUpperCase();
        if (upperSql.contains("INTO OUTFILE") || upperSql.contains("INTO DUMPFILE")
                || upperSql.contains("LOAD_FILE") || upperSql.contains("BENCHMARK(")
                || upperSql.contains("SLEEP(")) {
            return "Diese SQL-Funktion ist aus Sicherheitsgruenden nicht erlaubt.";
        }

        try {
            List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            if (rows.isEmpty()) {
                return "Keine Ergebnisse.";
            }

            // Limit rows
            if (rows.size() > MAX_DB_ROWS) {
                rows = rows.subList(0, MAX_DB_ROWS);
            }

            // Format as simple table
            StringBuilder sb = new StringBuilder();
            List<String> columns = new ArrayList<>(rows.getFirst().keySet());
            sb.append(String.join(" | ", columns)).append("\n");
            sb.append("-".repeat(columns.size() * 15)).append("\n");

            for (var row : rows) {
                List<String> values = new ArrayList<>();
                for (String col : columns) {
                    Object val = row.get(col);
                    values.add(val != null ? val.toString() : "NULL");
                }
                sb.append(String.join(" | ", values)).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "SQL-Fehler: " + e.getMessage();
        }
    }

    private String searchEmails(String query) {
        if (query == null || query.isBlank()) {
            return "Keine Suchanfrage angegeben.";
        }

        try {
            // Use parameterized query to search emails by subject, sender, recipient and body
            String sql = """
                    SELECT id, subject, from_address, recipient, sent_at, direction,
                           CASE WHEN projekt_id IS NOT NULL THEN 'projects'
                                WHEN anfrage_id IS NOT NULL THEN 'offers'
                                WHEN lieferant_id IS NOT NULL THEN 'suppliers'
                                WHEN direction = 'OUT' THEN 'sent'
                                WHEN is_spam = true THEN 'spam'
                                WHEN is_newsletter = true THEN 'newsletter'
                                WHEN deleted = true THEN 'trash'
                                ELSE 'inbox'
                           END AS folder
                    FROM email
                    WHERE (LOWER(subject) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(from_address) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(recipient) LIKE LOWER(CONCAT('%', :query, '%'))
                        OR LOWER(body) LIKE LOWER(CONCAT('%', :query, '%')))
                    ORDER BY sent_at DESC
                    LIMIT :limit
                    """;

            var params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource();
            params.addValue("query", query);
            params.addValue("limit", MAX_EMAIL_RESULTS);

            var namedJdbc = new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbcTemplate);
            List<java.util.Map<String, Object>> rows = namedJdbc.queryForList(sql, params);

            if (rows.isEmpty()) {
                return "Keine E-Mails gefunden fuer: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Gefundene E-Mails (" + rows.size() + " Treffer):\n\n");

            for (var row : rows) {
                long emailId = ((Number) row.get("id")).longValue();
                String subject = row.get("subject") != null ? row.get("subject").toString() : "(kein Betreff)";
                String from = row.get("from_address") != null ? row.get("from_address").toString() : "unbekannt";
                String to = row.get("recipient") != null ? row.get("recipient").toString() : "unbekannt";
                String sentAt = row.get("sent_at") != null ? row.get("sent_at").toString() : "unbekannt";
                String direction = row.get("direction") != null ? row.get("direction").toString() : "IN";
                String folder = row.get("folder") != null ? row.get("folder").toString() : "inbox";

                sb.append("- **").append(subject).append("**\n");
                if ("OUT".equals(direction)) {
                    sb.append("  An: ").append(to).append("\n");
                } else {
                    sb.append("  Von: ").append(from).append("\n");
                }
                sb.append("  Datum: ").append(sentAt).append("\n");
                sb.append("  Link: [E-Mail oeffnen](/emails/").append(folder).append("/").append(emailId).append(")\n\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("search_emails Fehler: {}", e.getMessage());
            return "E-Mail-Suche fehlgeschlagen: " + e.getMessage();
        }
    }

    private String sanitizeSecrets(String content) {
        return SECRET_PATTERN.matcher(content).replaceAll("$1***REDACTED***");
    }
}
