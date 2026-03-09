package org.example.kalkulationsprogramm.util;

import org.hibernate.PropertyValueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Übersetzt Datenbank-Constraint-Verletzungen in strukturierte, deutschsprachige
 * Fehlermeldungen, die sowohl für Nutzer:innen verständlich als auch für Entwickler:innen
 * nachvollziehbar bleiben.
 */
@Component
public class ConstraintMessageResolver {
    private static final Logger LOG = LoggerFactory.getLogger(ConstraintMessageResolver.class);

    private static final Pattern DUPLICATE_ENTRY = Pattern.compile("Duplicate entry '(.+?)' for key '([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_NULL_COLUMN = Pattern.compile("Column '([^']+)' cannot be null", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_TOO_LONG = Pattern.compile("Data too long for column '([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONSTRAINT_NAME = Pattern.compile("CONSTRAINT '([^']+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern FK_COLUMN = Pattern.compile("FOREIGN KEY \\(`([^`]+)`\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FK_REFERENCED_TABLE = Pattern.compile("REFERENCES `([^`]+)`", Pattern.CASE_INSENSITIVE);
    private static final Pattern FK_REFERENCED_COLUMN = Pattern.compile("REFERENCES `[^`]+` \\(`([^`]+)`\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern FK_TABLE = Pattern.compile("fails \\(`[^`]+`\\.`([^`]+)`\\)", Pattern.CASE_INSENSITIVE);

    private static final int MAX_VALUE_PREVIEW = 120;

    private final DatabaseConstraintMetadataService metadataService;

    /**
     * Erstellt einen neuen Resolver und merkt sich den Zugriff auf Metadaten über Tabellen,
     * Spalten und Constraints.
     */
    public ConstraintMessageResolver(DatabaseConstraintMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    /**
     * Zerlegt eine {@link DataIntegrityViolationException} in einen erklärenden
     * {@link ConstraintErrorDetail}. Dabei wird zuerst der konkrete Fehler-Typ erkannt
     * (z. B. Duplicate Key, NOT NULL) und anschließend eine passgenaue Meldung erzeugt.
     */
    public ConstraintErrorDetail resolve(DataIntegrityViolationException exception) {
        Throwable root = exception.getMostSpecificCause();
        if (root instanceof PropertyValueException propertyValueException) {
            return handlePropertyValue(propertyValueException);
        }
        if (root instanceof SQLException sqlException) {
            LOG.debug("SQL integrity error: state={}, code={}, message={}", sqlException.getSQLState(), sqlException.getErrorCode(), sqlException.getMessage());
        }
        String message = root != null && root.getMessage() != null ? root.getMessage() : exception.getMessage();
        if (message == null) {
            message = "";
        }
        Matcher duplicateMatcher = DUPLICATE_ENTRY.matcher(message);
        if (duplicateMatcher.find()) {
            return handleDuplicate(duplicateMatcher.group(1), duplicateMatcher.group(2), message);
        }
        Matcher notNullMatcher = NOT_NULL_COLUMN.matcher(message);
        if (notNullMatcher.find()) {
            return handleNotNull(notNullMatcher.group(1), message);
        }
        Matcher tooLongMatcher = DATA_TOO_LONG.matcher(message);
        if (tooLongMatcher.find()) {
            return handleDataTooLong(tooLongMatcher.group(1), message);
        }
        if (message.toLowerCase(Locale.ROOT).contains("foreign key constraint fails")) {
            return handleForeignKey(message);
        }
        return fallback(message);
    }

    /**
     * Wandelt Hibernate-spezifische Property-Value-Fehler (z. B. fehlende Pflichtfelder)
     * in eine konsistente Nutzermeldung um und reichert sie mit Feldinformationen an.
     */
    private ConstraintErrorDetail handlePropertyValue(PropertyValueException ex) {
        String propertyName = ex.getPropertyName();
        String label = metadataService.findColumnByName(propertyName)
                .map(DatabaseConstraintMetadataService.ColumnMetadata::label)
                .filter(l -> !l.isBlank())
                .orElseGet(() -> humanize(propertyName));
        List<FieldErrorDetail> fields = List.of(new FieldErrorDetail(propertyName, label, "Darf nicht leer sein."));
        String message = "Das Feld '" + label + "' darf nicht leer sein.";
        return new ConstraintErrorDetail(HttpStatus.BAD_REQUEST, message, ex.getMessage(), null, fields);
    }

    /**
     * Erklärt Eindeutigkeitsverletzungen und benennt – sofern Metadaten vorhanden – die
     * betroffenen Spalten inklusive sprechender Labels.
     */
    private ConstraintErrorDetail handleDuplicate(String rawValue, String constraintKey, String technicalMessage) {
        String value = truncate(rawValue);
        Optional<DatabaseConstraintMetadataService.ConstraintMetadata> metadataOptional = metadataService.findConstraint(constraintKey);
        DatabaseConstraintMetadataService.ConstraintMetadata metadata = metadataOptional.orElse(null);
        if (metadata == null && constraintKey.contains(".")) {
            metadata = metadataService.findConstraint(constraintKey.substring(constraintKey.lastIndexOf('.') + 1)).orElse(null);
        }
        List<FieldErrorDetail> fieldErrors = new ArrayList<>();
        String effectiveConstraint = metadata != null ? metadata.name() : constraintKey;
        if (metadata != null && !metadata.columnNames().isEmpty()) {
            List<String> columnNames = metadata.columnNames();
            String tableName = metadata.tableName();
            List<String> labels = columnNames.stream()
                    .map(column -> metadataService.findColumn(tableName, column)
                            .map(DatabaseConstraintMetadataService.ColumnMetadata::label)
                            .filter(label -> !label.isBlank())
                            .orElseGet(() -> humanize(column)))
                    .toList();
            String message;
            if (columnNames.size() == 1) {
                String columnName = columnNames.getFirst();
                String columnLabel = labels.getFirst();
                message = "Der Wert '" + value + "' für " + columnLabel + " ist bereits vergeben.";
                fieldErrors.add(new FieldErrorDetail(columnName, columnLabel, "Wert bereits vergeben."));
            } else {
                message = "Die Kombination aus " + joinLabels(labels) + " muss eindeutig sein.";
                for (int i = 0; i < columnNames.size(); i++) {
                    fieldErrors.add(new FieldErrorDetail(columnNames.get(i), labels.get(i), "Kombination muss eindeutig sein."));
                }
            }
            return new ConstraintErrorDetail(HttpStatus.CONFLICT, message, technicalMessage, effectiveConstraint, fieldErrors);
        }
        String message = "Der angegebene Wert ist bereits vorhanden (Datenbank-Constraint '" + effectiveConstraint + "').";
        return new ConstraintErrorDetail(HttpStatus.CONFLICT, message, technicalMessage, effectiveConstraint, fieldErrors);
    }

    /**
     * Übersetzt NOT-NULL-Verletzungen in eine klare Meldung und markiert das fehlerhafte Feld.
     */
    private ConstraintErrorDetail handleNotNull(String columnName, String technicalMessage) {
        DatabaseConstraintMetadataService.ColumnMetadata column = metadataService.findColumnByName(columnName).orElse(null);
        String label = column != null && !column.label().isBlank() ? column.label() : humanize(columnName);
        FieldErrorDetail field = new FieldErrorDetail(columnName, label, "Darf nicht leer sein.");
        String message = "Das Feld '" + label + "' darf nicht leer sein.";
        return new ConstraintErrorDetail(HttpStatus.BAD_REQUEST, message, technicalMessage, null, List.of(field));
    }

    /**
     * Baut eine Nutzerfehlermeldung für überlange Werte inklusive maximal zulässiger Länge auf.
     */
    private ConstraintErrorDetail handleDataTooLong(String columnName, String technicalMessage) {
        DatabaseConstraintMetadataService.ColumnMetadata column = metadataService.findColumnByName(columnName).orElse(null);
        String label = column != null && !column.label().isBlank() ? column.label() : humanize(columnName);
        Integer maxLength = column != null ? column.maxLength() : null;
        StringBuilder userMessage = new StringBuilder("Der Wert für '").append(label).append("' ist zu lang.");
        if (maxLength != null) {
            userMessage.append(" Maximal erlaubt: ").append(maxLength).append(" Zeichen.");
        }
        FieldErrorDetail field = new FieldErrorDetail(columnName, label, "Wert ist zu lang" + (maxLength != null ? " (max. " + maxLength + ")" : ""));
        return new ConstraintErrorDetail(HttpStatus.BAD_REQUEST, userMessage.toString(), technicalMessage, null, List.of(field));
    }

    /**
     * Analysiert fehlgeschlagene Fremdschlüsselprüfungen und liefert – je nach Situation –
     * Hinweise zum nicht löschbaren Datensatz oder zur ungültigen Referenz.
     */
    private ConstraintErrorDetail handleForeignKey(String technicalMessage) {
        String lower = technicalMessage.toLowerCase(Locale.ROOT);
        boolean deleteCase = lower.contains("cannot delete or update a parent row");
        String constraintName = extractFirst(CONSTRAINT_NAME, technicalMessage);
        DatabaseConstraintMetadataService.ConstraintMetadata metadata = constraintName != null
                ? metadataService.findConstraint(constraintName).orElse(null)
                : null;
        String tableName = metadata != null ? metadata.tableName() : extractFirst(FK_TABLE, technicalMessage);
        String referencedTable = metadata != null ? metadata.referencedTableName() : extractFirst(FK_REFERENCED_TABLE, technicalMessage);
        List<String> columnNames = metadata != null && !metadata.columnNames().isEmpty()
                ? metadata.columnNames()
                : optionalList(extractFirst(FK_COLUMN, technicalMessage));
        List<String> referencedColumns = metadata != null && !metadata.referencedColumnNames().isEmpty()
                ? metadata.referencedColumnNames()
                : optionalList(extractFirst(FK_REFERENCED_COLUMN, technicalMessage));
        String tableLabel = metadataService.findTable(tableName)
                .map(DatabaseConstraintMetadataService.TableMetadata::label)
                .filter(label -> !label.isBlank())
                .orElseGet(() -> humanize(tableName));
        String referencedTableLabel = metadataService.findTable(referencedTable)
                .map(DatabaseConstraintMetadataService.TableMetadata::label)
                .filter(label -> !label.isBlank())
                .orElseGet(() -> humanize(referencedTable));
        List<FieldErrorDetail> fieldErrors = new ArrayList<>();
        List<String> columnLabels = new ArrayList<>();
        for (String columnName : columnNames) {
            String label = metadataService.findColumn(tableName, columnName)
                    .map(DatabaseConstraintMetadataService.ColumnMetadata::label)
                    .filter(l -> !l.isBlank())
                    .orElseGet(() -> humanize(columnName));
            columnLabels.add(label);
            if (!deleteCase) {
                fieldErrors.add(new FieldErrorDetail(columnName, label, "Verweis ist ungültig."));
            }
        }
        String message;
        if (deleteCase) {
            message = "Der Datensatz kann nicht gelöscht werden, da noch Einträge in '" + referencedTableLabel + "' darauf verweisen.";
        } else if (!columnLabels.isEmpty()) {
            message = "Der angegebene Wert für " + joinLabels(columnLabels) + " ist ungültig – es existiert kein passender Eintrag in '" + referencedTableLabel + "'.";
        } else {
            message = "Die ausgewählte Referenz ist ungültig – es existiert kein passender Eintrag in '" + referencedTableLabel + "'.";
        }
        return new ConstraintErrorDetail(HttpStatus.CONFLICT, message, technicalMessage, constraintName, fieldErrors);
    }

    /**
     * Rückfallpfad für unbekannte Fehlerbilder: liefert eine generische Meldung, die den
     * technischen Fehlertext dennoch bewahrt.
     */
    private ConstraintErrorDetail fallback(String technicalMessage) {
        String message = "Der Vorgang konnte nicht abgeschlossen werden, weil eine Datenbankvorgabe verletzt wurde.";
        return new ConstraintErrorDetail(HttpStatus.BAD_REQUEST, message, technicalMessage, null, Collections.emptyList());
    }

    /** Kürzt einen angezeigten Wert auf eine sinnvolle Vorschau-Länge ab. */
    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= MAX_VALUE_PREVIEW) {
            return value;
        }
        return value.substring(0, MAX_VALUE_PREVIEW - 3) + "...";
    }

    /**
     * Verbindet mehrere Feldbezeichnungen zu einer natürlich klingenden Aufzählung.
     */
    private static String joinLabels(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return "";
        }
        if (labels.size() == 1) {
            return labels.getFirst();
        }
        return String.join(", ", labels.subList(0, labels.size() - 1)) + " und " + labels.getLast();
    }

    /** Wandelt Datenbanknamen wie "ARTIKEL_NR" in lesbare Beschriftungen um. */
    private static String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = value.replace('_', ' ').replace('-', ' ').trim();
        if (cleaned.isEmpty()) {
            return value;
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

    /** Holt den ersten Treffer eines regulären Ausdrucks oder {@code null}, wenn keiner existiert. */
    private static String extractFirst(Pattern pattern, String input) {
        if (input == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Erzeugt eine optional gefüllte Liste aus einem einzelnen String-Wert. */
    private static List<String> optionalList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value);
    }
}
