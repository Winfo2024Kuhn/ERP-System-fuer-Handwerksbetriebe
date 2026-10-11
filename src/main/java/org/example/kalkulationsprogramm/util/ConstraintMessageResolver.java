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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Übersetzt Datenbank-Constraint-Verletzungen in strukturierte, deutschsprachige
 * Fehlermeldungen, die sowohl für Nutzer:innen verständlich als auch für Entwickler:innen
 * nachvollziehbar bleiben.
 *
 * <p>Gelesen werden die (englischen) Fehlertexte von PostgreSQL, der einzigen Datenbank:
 * 23505 (Wert doppelt), 23502 (Pflichtfeld leer), 23503 (Verweis), 22001 (Text zu lang,
 * zusätzlich am SQLState erkannt). Alles andere, etwa 23514 (CHECK), bekommt die
 * allgemeine Meldung.
 */
@Component
public class ConstraintMessageResolver {
    private static final Logger LOG = LoggerFactory.getLogger(ConstraintMessageResolver.class);

    private static final Pattern PG_DUPLICATE = Pattern.compile("duplicate key value violates unique constraint \"([^\"]++)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern PG_DUPLICATE_VALUE = Pattern.compile("Key \\([^)]*+\\)=\\((.+?)\\) already exists", Pattern.CASE_INSENSITIVE);
    private static final Pattern PG_NOT_NULL = Pattern.compile("null value in column \"([^\"]++)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern PG_FOREIGN_KEY = Pattern.compile("violates foreign key constraint \"([^\"]++)\"", Pattern.CASE_INSENSITIVE);
    // Tabellennamen aus dem PostgreSQL-Text - Rueckfall, wenn die Metadaten sie nicht kennen
    private static final Pattern PG_FK_TABELLE_LOESCHEN = Pattern.compile("violates foreign key constraint \"[^\"]++\" on table \"([^\"]++)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern PG_FK_ZIELTABELLE = Pattern.compile("is not present in table \"([^\"]++)\"", Pattern.CASE_INSENSITIVE);
    // PostgreSQL nennt bei zu langen Texten nur den Typ, nicht die Spalte
    private static final Pattern PG_ZU_LANG = Pattern.compile("value too long for type character(?: varying)?\\((\\d++)\\)", Pattern.CASE_INSENSITIVE);

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
     * {@link ConstraintErrorDetail}. Dabei wird zuerst der konkrete Fehler-Typ am
     * PostgreSQL-Fehlertext erkannt (z. B. doppelter Wert, NOT NULL) und anschließend eine
     * passgenaue Meldung erzeugt.
     */
    public ConstraintErrorDetail resolve(DataIntegrityViolationException exception) {
        Throwable root = exception.getMostSpecificCause();
        if (root instanceof PropertyValueException propertyValueException) {
            return handlePropertyValue(propertyValueException);
        }
        if (root instanceof SQLException sqlException) {
            LOG.debug("SQL integrity error: state={}, message={}", sqlException.getSQLState(), sqlException.getMessage());
        }
        String message = root != null && root.getMessage() != null ? root.getMessage() : exception.getMessage();
        if (message == null) {
            message = "";
        }
        Matcher pgDuplicate = PG_DUPLICATE.matcher(message);
        if (pgDuplicate.find()) {
            String wert = extractFirst(PG_DUPLICATE_VALUE, message);
            return handleDuplicate(wert != null ? wert : "", pgDuplicate.group(1), message);
        }
        Matcher pgNotNull = PG_NOT_NULL.matcher(message);
        if (pgNotNull.find()) {
            return handleNotNull(pgNotNull.group(1), message);
        }
        Matcher pgForeignKey = PG_FOREIGN_KEY.matcher(message);
        if (pgForeignKey.find()) {
            // "update or delete on table ..." = Datensatz wird noch gebraucht
            boolean deleteCase = message.toLowerCase(Locale.ROOT).contains("update or delete on table");
            return handleForeignKey(message, pgForeignKey.group(1), deleteCase);
        }
        // 22001 = Wert zu lang - erkannt am SQLState, damit es auch mit uebersetzten
        // Servermeldungen greift; die Hoechstlaenge nur, wenn der (englische) Text sie nennt
        Matcher pgZuLang = PG_ZU_LANG.matcher(message);
        boolean zuLang = pgZuLang.find();
        if (zuLang || root instanceof SQLException sql && "22001".equals(sql.getSQLState())) {
            String userMessage = "Ein eingegebener Text ist zu lang."
                    + (zuLang ? " Maximal erlaubt: " + pgZuLang.group(1) + " Zeichen." : "");
            return new ConstraintErrorDetail(HttpStatus.BAD_REQUEST, userMessage, message, null, Collections.emptyList());
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
        DatabaseConstraintMetadataService.ConstraintMetadata metadata = metadataService.findConstraint(constraintKey).orElse(null);
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
     * Analysiert fehlgeschlagene Fremdschlüsselprüfungen und liefert – je nach Situation –
     * Hinweise zum nicht löschbaren Datensatz oder zur ungültigen Referenz. Tabellen und
     * Spalten kommen aus den Metadaten; kennen sie eine Tabelle nicht, wird sie aus dem
     * PostgreSQL-Text gelesen. Beim Löschen zählt die Tabelle, deren Einträge noch
     * verweisen, beim Einfügen die, in der der Eintrag fehlt.
     */
    private ConstraintErrorDetail handleForeignKey(String technicalMessage, String constraintName, boolean deleteCase) {
        DatabaseConstraintMetadataService.ConstraintMetadata metadata = metadataService.findConstraint(constraintName).orElse(null);
        String tableName = metadata != null ? metadata.tableName()
                : (deleteCase ? extractFirst(PG_FK_TABELLE_LOESCHEN, technicalMessage) : null);
        String referencedTable = metadata != null && metadata.referencedTableName() != null ? metadata.referencedTableName()
                : extractFirst(PG_FK_ZIELTABELLE, technicalMessage);
        List<String> columnNames = metadata != null ? metadata.columnNames() : List.of();
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
            message = "Der Datensatz kann nicht gelöscht werden, da noch Einträge" + inTabelle(tableLabel) + " darauf verweisen.";
        } else if (!columnLabels.isEmpty()) {
            message = "Der angegebene Wert für " + joinLabels(columnLabels) + " ist ungültig – es existiert kein passender Eintrag" + inTabelle(referencedTableLabel) + ".";
        } else {
            message = "Die ausgewählte Referenz ist ungültig – es existiert kein passender Eintrag" + inTabelle(referencedTableLabel) + ".";
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

    /** " in 'Projekt'" - oder nichts, wenn die Tabelle unbekannt ist (statt " in ''"). */
    private static String inTabelle(String label) {
        return label.isBlank() ? "" : " in '" + label + "'";
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
}
