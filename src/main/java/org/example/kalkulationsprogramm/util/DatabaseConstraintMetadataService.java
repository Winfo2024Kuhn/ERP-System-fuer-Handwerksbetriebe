package org.example.kalkulationsprogramm.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Lädt Beschreibungen von Tabellen, Spalten und Constraints aus der Datenbank und stellt
 * sie gecacht zur Verfügung, damit Fehlerausgaben sprechende Labels verwenden können.
 */
@Service
public class DatabaseConstraintMetadataService {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseConstraintMetadataService.class);

    private static final String SQL_SELECT_SCHEMA = "SELECT DATABASE()";
    private static final String SQL_SELECT_TABLES =
            "SELECT table_name, IFNULL(NULLIF(table_comment, ''), table_name) AS table_comment " +
            "FROM information_schema.tables WHERE table_schema = ?";
    private static final String SQL_SELECT_COLUMNS =
            "SELECT table_name, column_name, IFNULL(NULLIF(column_comment, ''), column_name) AS column_comment, " +
            "character_maximum_length, is_nullable FROM information_schema.columns WHERE table_schema = ?";
    private static final String SQL_SELECT_CONSTRAINTS =
            "SELECT tc.constraint_name, tc.table_name, tc.constraint_type, kcu.column_name, " +
            "rc.referenced_table_name, kcu.referenced_column_name " +
            "FROM information_schema.table_constraints tc " +
            "LEFT JOIN information_schema.key_column_usage kcu " +
            "  ON tc.constraint_name = kcu.constraint_name " +
            " AND tc.table_schema = kcu.table_schema " +
            " AND tc.table_name = kcu.table_name " +
            "LEFT JOIN information_schema.referential_constraints rc " +
            "  ON tc.constraint_name = rc.constraint_name " +
            " AND tc.table_schema = rc.constraint_schema " +
            "WHERE tc.table_schema = ?";

    private final JdbcTemplate jdbcTemplate;
    private final AtomicBoolean initialised = new AtomicBoolean(false);
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile String schemaName = "";
    private volatile Map<String, ConstraintMetadata> constraintsByName = Map.of();
    private volatile Map<String, ColumnMetadata> columnsByKey = Map.of();
    private volatile Map<String, List<ColumnMetadata>> columnsByName = Map.of();
    private volatile Map<String, TableMetadata> tablesByName = Map.of();

    /** Erstellt den Dienst und merkt sich das zu verwendende {@link JdbcTemplate}. */
    public DatabaseConstraintMetadataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Sucht einen Constraint anhand seines Namens oder optional des mitgelieferten
     * Schema-Präfixes.
     */
    public Optional<ConstraintMetadata> findConstraint(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return Optional.empty();
        }
        ensureLoaded();
        String normalised = normaliseConstraintKey(rawName);
        ConstraintMetadata direct = constraintsByName.get(normalised);
        if (direct != null) {
            return Optional.of(direct);
        }
        int dotIndex = normalised.lastIndexOf('.');
        if (dotIndex > -1) {
            String tail = normalised.substring(dotIndex + 1);
            direct = constraintsByName.get(tail);
            if (direct != null) {
                return Optional.of(direct);
            }
        }
        return Optional.empty();
    }

    /** Liefert Metadaten zu einer konkreten Spalte einer Tabelle, sofern vorhanden. */
    public Optional<ColumnMetadata> findColumn(String tableName, String columnName) {
        if (tableName == null || columnName == null || tableName.isBlank() || columnName.isBlank()) {
            return Optional.empty();
        }
        ensureLoaded();
        return Optional.ofNullable(columnsByKey.get(columnKey(tableName, columnName)));
    }

    /**
     * Findet die erste Spalte mit dem angegebenen Namen, unabhängig von der Tabelle – nützlich
     * bei Fehlern, die nur den Spaltennamen liefern.
     */
    public Optional<ColumnMetadata> findColumnByName(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return Optional.empty();
        }
        ensureLoaded();
        List<ColumnMetadata> matches = columnsByName.get(normalise(columnName));
        if (matches == null || matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matches.getFirst());
    }

    /** Liefert Metadaten zu einer Tabelle inklusive ihres lesbaren Labels. */
    public Optional<TableMetadata> findTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return Optional.empty();
        }
        ensureLoaded();
        return Optional.ofNullable(tablesByName.get(normalise(tableName)));
    }

    /** Gibt das aktuell verwendete Datenbank-Schema zurück (leer, wenn unbekannt). */
    public String getSchemaName() {
        ensureLoaded();
        return schemaName;
    }

    /** Erzwingt das erneute Laden aller Metadaten und verwirft den Cache. */
    public void refresh() {
        loadMetadata(true);
    }

    /** Stellt sicher, dass der Cache initialisiert ist, bevor auf Daten zugegriffen wird. */
    private void ensureLoaded() {
        if (!initialised.get()) {
            loadMetadata(false);
        }
    }

    /**
     * Lädt Tabellen-, Spalten- und Constraint-Informationen aus dem INFORMATION_SCHEMA und
     * speichert sie threadsicher zwischen.
     */
    private void loadMetadata(boolean force) {
        if (!force && initialised.get()) {
            return;
        }
        lock.writeLock().lock();
        try {
            if (!force && initialised.get()) {
                return;
            }
            String schema = "";
            try {
                schema = jdbcTemplate.queryForObject(SQL_SELECT_SCHEMA, String.class);
            } catch (DataAccessException ex) {
                LOG.warn("Could not determine active database schema", ex);
            }
            if (schema == null) {
                schema = "";
            }
            String finalSchema = schema;

            Map<String, TableMetadata> tableBuffer = new HashMap<>();
            Map<String, ColumnMetadata> columnBuffer = new HashMap<>();
            Map<String, List<ColumnMetadata>> columnByNameBuffer = new HashMap<>();
            Map<String, ConstraintMetadataBuilder> builders = new LinkedHashMap<>();

            try {
                jdbcTemplate.query(SQL_SELECT_TABLES, ps -> ps.setString(1, finalSchema), rs -> {
                    TableMetadata meta = new TableMetadata(
                            rs.getString("table_name"),
                            toDisplayName(rs.getString("table_comment"))
                    );
                    tableBuffer.put(normalise(meta.tableName()), meta);
                });

                jdbcTemplate.query(SQL_SELECT_COLUMNS, ps -> ps.setString(1, finalSchema), rs -> {
                    Integer charMaxLen = null;
                    Object rawLen = rs.getObject("character_maximum_length");
                    if (rawLen != null) {
                        long longVal = ((Number) rawLen).longValue();
                        charMaxLen = longVal <= Integer.MAX_VALUE ? (int) longVal : Integer.MAX_VALUE;
                    }
                    ColumnMetadata meta = new ColumnMetadata(
                            rs.getString("table_name"),
                            rs.getString("column_name"),
                            toDisplayName(rs.getString("column_comment")),
                            charMaxLen,
                            !"NO".equalsIgnoreCase(rs.getString("is_nullable"))
                    );
                    columnBuffer.put(columnKey(meta.tableName(), meta.columnName()), meta);
                    columnByNameBuffer.computeIfAbsent(normalise(meta.columnName()), key -> new ArrayList<>()).add(meta);
                });

                jdbcTemplate.query(SQL_SELECT_CONSTRAINTS, ps -> ps.setString(1, finalSchema), (ResultSet rs) -> {
                    while (rs.next()) {
                        String name = rs.getString("constraint_name");
                        String table = rs.getString("table_name");
                        ConstraintMetadata.ConstraintType type = ConstraintMetadata.ConstraintType.fromDatabase(rs.getString("constraint_type"));
                        if (type == ConstraintMetadata.ConstraintType.UNKNOWN) {
                            continue;
                        }
                        ConstraintMetadataBuilder builder = builders.computeIfAbsent(normaliseConstraintKey(name),
                                key -> new ConstraintMetadataBuilder(name, table, type));

                        String column = rs.getString("column_name");
                        if (column != null) {
                            builder.columns.add(column);
                        }

                        if (type == ConstraintMetadata.ConstraintType.FOREIGN_KEY) {
                            builder.referencedTable = rs.getString("referenced_table_name");
                            String refColumn = rs.getString("referenced_column_name");
                            if (refColumn != null) {
                                builder.referencedColumns.add(refColumn);
                            }
                        }
                    }
                    return null;
                });
            } catch (DataAccessException ex) {
                LOG.warn("Could not load database metadata", ex);
            }

            Map<String, ConstraintMetadata> constraintBuffer = new HashMap<>();
            builders.forEach((key, builder) -> constraintBuffer.put(key, builder.build()));

            schemaName = finalSchema;
            constraintsByName = Collections.unmodifiableMap(constraintBuffer);
            columnsByKey = Collections.unmodifiableMap(columnBuffer);

            Map<String, List<ColumnMetadata>> immutableColumnByName = new HashMap<>();
            columnByNameBuffer.forEach((key, list) -> immutableColumnByName.put(key, List.copyOf(list)));
            columnsByName = Collections.unmodifiableMap(immutableColumnByName);

            tablesByName = Collections.unmodifiableMap(new HashMap<>(tableBuffer));
            initialised.set(true);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Kombiniert Tabellen- und Spaltennamen zu einem Schlüssel für die Lookup-Map. */
    private static String columnKey(String tableName, String columnName) {
        return normalise(tableName) + "." + normalise(columnName);
    }

    /** Vereinheitlicht Strings für Map-Lookups, indem sie getrimmt und uppercased werden. */
    private static String normalise(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Entfernt Backticks und Mehrfach-Leerzeichen aus Constraint-Namen, um unterschiedliche
     * Schreibweisen der Datenbank zusammenzuführen.
     */
    private static String normaliseConstraintKey(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('`', ' ').trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    /** Formatiert technische Datenbankkommentare zu lesbaren Labels für das Frontend. */
    private static String toDisplayName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String cleaned = raw.replace('_', ' ').trim();
        if (cleaned.isEmpty()) {
            return raw;
        }
        StringBuilder builder = new StringBuilder(cleaned.length());
        boolean capitalizeNext = true;
        for (char character : cleaned.toCharArray()) {
            if (Character.isWhitespace(character)) {
                builder.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                builder.append(Character.toTitleCase(character));
                capitalizeNext = false;
            } else {
                builder.append(Character.toLowerCase(character));
            }
        }
        return builder.toString();
    }

    private static final class ConstraintMetadataBuilder {
        private final String name;
        private final String table;
        private final ConstraintMetadata.ConstraintType type;
        private final LinkedHashSet<String> columns = new LinkedHashSet<>();
        private final LinkedHashSet<String> referencedColumns = new LinkedHashSet<>();
        private String referencedTable;

        private ConstraintMetadataBuilder(String name, String table, ConstraintMetadata.ConstraintType type) {
            this.name = name;
            this.table = table;
            this.type = type;
        }

        private ConstraintMetadata build() {
            return new ConstraintMetadata(
                    name,
                    table,
                    type,
                    List.copyOf(columns),
                    referencedTable,
                    List.copyOf(referencedColumns)
            );
        }
    }

    public record ColumnMetadata(String tableName, String columnName, String label, Integer maxLength, boolean nullable) { }

    public record TableMetadata(String tableName, String label) { }

    public record ConstraintMetadata(String name,
                                     String tableName,
                                     ConstraintType type,
                                     List<String> columnNames,
                                     String referencedTableName,
                                     List<String> referencedColumnNames) {

        public enum ConstraintType {
            UNIQUE,
            FOREIGN_KEY,
            PRIMARY_KEY,
            CHECK,
            UNKNOWN;

            static ConstraintType fromDatabase(String value) {
                if (value == null) {
                    return UNKNOWN;
                }
                return switch (value.toUpperCase(Locale.ROOT)) {
                    case "UNIQUE" -> UNIQUE;
                    case "FOREIGN KEY" -> FOREIGN_KEY;
                    case "PRIMARY KEY" -> PRIMARY_KEY;
                    case "CHECK" -> CHECK;
                    default -> UNKNOWN;
                };
            }

            public boolean isUnique() {
                return this == UNIQUE;
            }

            public boolean isForeignKey() {
                return this == FOREIGN_KEY;
            }
        }
    }
}
