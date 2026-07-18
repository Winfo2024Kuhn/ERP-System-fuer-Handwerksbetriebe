package org.example.kalkulationsprogramm.util

import org.hibernate.PropertyValueException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.sql.SQLException
import java.util.Locale
import java.util.regex.Pattern

/**
 * Uebersetzt Datenbank-Constraint-Verletzungen in strukturierte, deutschsprachige
 * Fehlermeldungen, die sowohl fuer Nutzer:innen verstaendlich als auch fuer Entwickler:innen
 * nachvollziehbar bleiben.
 */
@Component
class ConstraintMessageResolver(
    private val metadataService: DatabaseConstraintMetadataService,
) {
    fun resolve(exception: DataIntegrityViolationException): ConstraintErrorDetail {
        val root = exception.mostSpecificCause
        if (root is PropertyValueException) {
            return handlePropertyValue(root)
        }
        if (root is SQLException) {
            LOG.debug(
                "SQL integrity error: state={}, code={}, message={}",
                root.sqlState,
                root.errorCode,
                root.message,
            )
        }

        val message = root.message ?: exception.message.orEmpty()
        val duplicateMatcher = DUPLICATE_ENTRY.matcher(message)
        if (duplicateMatcher.find()) {
            return handleDuplicate(duplicateMatcher.group(1), duplicateMatcher.group(2), message)
        }
        val notNullMatcher = NOT_NULL_COLUMN.matcher(message)
        if (notNullMatcher.find()) {
            return handleNotNull(notNullMatcher.group(1), message)
        }
        val tooLongMatcher = DATA_TOO_LONG.matcher(message)
        if (tooLongMatcher.find()) {
            return handleDataTooLong(tooLongMatcher.group(1), message)
        }
        if (message.lowercase(Locale.ROOT).contains("foreign key constraint fails")) {
            return handleForeignKey(message)
        }
        return fallback(message)
    }

    private fun handlePropertyValue(ex: PropertyValueException): ConstraintErrorDetail {
        val propertyName = ex.propertyName
        val label = metadataService.findColumnByName(propertyName)
            .map { it.label }
            .filter { it.isNotBlank() }
            .orElseGet { humanize(propertyName) }
        val fields = listOf(FieldErrorDetail(propertyName, label, "Darf nicht leer sein."))
        val message = "Das Feld '$label' darf nicht leer sein."
        return ConstraintErrorDetail(HttpStatus.BAD_REQUEST, message, ex.message, null, fields)
    }

    private fun handleDuplicate(
        rawValue: String,
        constraintKey: String,
        technicalMessage: String,
    ): ConstraintErrorDetail {
        val value = truncate(rawValue)
        var metadata = metadataService.findConstraint(constraintKey).orElse(null)
        if (metadata == null && constraintKey.contains(".")) {
            metadata = metadataService.findConstraint(constraintKey.substringAfterLast('.')).orElse(null)
        }

        val fieldErrors = mutableListOf<FieldErrorDetail>()
        val effectiveConstraint = metadata?.name ?: constraintKey
        if (metadata != null && metadata.columnNames.isNotEmpty()) {
            val columnNames = metadata.columnNames
            val tableName = metadata.tableName
            val labels = columnNames.map { column ->
                metadataService.findColumn(tableName, column)
                    .map { it.label }
                    .filter { it.isNotBlank() }
                    .orElseGet { humanize(column) }
            }

            val message = if (columnNames.size == 1) {
                val columnName = columnNames.first()
                val columnLabel = labels.first()
                fieldErrors.add(FieldErrorDetail(columnName, columnLabel, "Wert bereits vergeben."))
                "Der Wert '$value' fuer $columnLabel ist bereits vergeben."
            } else {
                columnNames.indices.forEach { index ->
                    fieldErrors.add(
                        FieldErrorDetail(
                            columnNames[index],
                            labels[index],
                            "Kombination muss eindeutig sein.",
                        ),
                    )
                }
                "Die Kombination aus ${joinLabels(labels)} muss eindeutig sein."
            }
            return ConstraintErrorDetail(
                HttpStatus.CONFLICT,
                message,
                technicalMessage,
                effectiveConstraint,
                fieldErrors,
            )
        }

        val message = "Der angegebene Wert ist bereits vorhanden (Datenbank-Constraint '$effectiveConstraint')."
        return ConstraintErrorDetail(HttpStatus.CONFLICT, message, technicalMessage, effectiveConstraint, fieldErrors)
    }

    private fun handleNotNull(columnName: String, technicalMessage: String): ConstraintErrorDetail {
        val column = metadataService.findColumnByName(columnName).orElse(null)
        val label = column?.label?.takeIf { it.isNotBlank() } ?: humanize(columnName)
        val field = FieldErrorDetail(columnName, label, "Darf nicht leer sein.")
        val message = "Das Feld '$label' darf nicht leer sein."
        return ConstraintErrorDetail(HttpStatus.BAD_REQUEST, message, technicalMessage, null, listOf(field))
    }

    private fun handleDataTooLong(columnName: String, technicalMessage: String): ConstraintErrorDetail {
        val column = metadataService.findColumnByName(columnName).orElse(null)
        val label = column?.label?.takeIf { it.isNotBlank() } ?: humanize(columnName)
        val maxLength = column?.maxLength
        val userMessage = buildString {
            append("Der Wert fuer '").append(label).append("' ist zu lang.")
            if (maxLength != null) {
                append(" Maximal erlaubt: ").append(maxLength).append(" Zeichen.")
            }
        }
        val field = FieldErrorDetail(
            columnName,
            label,
            "Wert ist zu lang" + if (maxLength != null) " (max. $maxLength)" else "",
        )
        return ConstraintErrorDetail(HttpStatus.BAD_REQUEST, userMessage, technicalMessage, null, listOf(field))
    }

    private fun handleForeignKey(technicalMessage: String): ConstraintErrorDetail {
        val lower = technicalMessage.lowercase(Locale.ROOT)
        val deleteCase = lower.contains("cannot delete or update a parent row")
        val constraintName = extractFirst(CONSTRAINT_NAME, technicalMessage)
        val metadata = constraintName?.let { metadataService.findConstraint(it).orElse(null) }
        val tableName = metadata?.tableName ?: extractFirst(FK_TABLE, technicalMessage)
        val referencedTable = metadata?.referencedTableName ?: extractFirst(FK_REFERENCED_TABLE, technicalMessage)
        val columnNames = metadata?.columnNames?.takeIf { it.isNotEmpty() }
            ?: optionalList(extractFirst(FK_COLUMN, technicalMessage))
        val referencedColumns = metadata?.referencedColumnNames?.takeIf { it.isNotEmpty() }
            ?: optionalList(extractFirst(FK_REFERENCED_COLUMN, technicalMessage))

        val tableLabel = metadataService.findTable(tableName)
            .map { it.label }
            .filter { it.isNotBlank() }
            .orElseGet { humanize(tableName) }
        val referencedTableLabel = metadataService.findTable(referencedTable)
            .map { it.label }
            .filter { it.isNotBlank() }
            .orElseGet { humanize(referencedTable) }

        val fieldErrors = mutableListOf<FieldErrorDetail>()
        val columnLabels = mutableListOf<String>()
        for (columnName in columnNames) {
            val label = metadataService.findColumn(tableName, columnName)
                .map { it.label }
                .filter { it.isNotBlank() }
                .orElseGet { humanize(columnName) }
            columnLabels.add(label)
            if (!deleteCase) {
                fieldErrors.add(FieldErrorDetail(columnName, label, "Verweis ist ungueltig."))
            }
        }

        val message = when {
            deleteCase ->
                "Der Datensatz kann nicht geloescht werden, da noch Eintraege in '$referencedTableLabel' darauf verweisen."

            columnLabels.isNotEmpty() ->
                "Der angegebene Wert fuer ${joinLabels(columnLabels)} ist ungueltig - es existiert kein passender Eintrag in '$referencedTableLabel'."

            else ->
                "Die ausgewaehlte Referenz ist ungueltig - es existiert kein passender Eintrag in '$referencedTableLabel'."
        }

        return ConstraintErrorDetail(HttpStatus.CONFLICT, message, technicalMessage, constraintName, fieldErrors)
    }

    private fun fallback(technicalMessage: String): ConstraintErrorDetail {
        val message = "Der Vorgang konnte nicht abgeschlossen werden, weil eine Datenbankvorgabe verletzt wurde."
        return ConstraintErrorDetail(HttpStatus.BAD_REQUEST, message, technicalMessage, null, emptyList())
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ConstraintMessageResolver::class.java)
        private val DUPLICATE_ENTRY = Pattern.compile("Duplicate entry '(.+?)' for key '([^']+)'", Pattern.CASE_INSENSITIVE)
        private val NOT_NULL_COLUMN = Pattern.compile("Column '([^']+)' cannot be null", Pattern.CASE_INSENSITIVE)
        private val DATA_TOO_LONG = Pattern.compile("Data too long for column '([^']+)'", Pattern.CASE_INSENSITIVE)
        private val CONSTRAINT_NAME = Pattern.compile("CONSTRAINT '([^']+)'", Pattern.CASE_INSENSITIVE)
        private val FK_COLUMN = Pattern.compile("FOREIGN KEY \\(`([^`]+)`\\)", Pattern.CASE_INSENSITIVE)
        private val FK_REFERENCED_TABLE = Pattern.compile("REFERENCES `([^`]+)`", Pattern.CASE_INSENSITIVE)
        private val FK_REFERENCED_COLUMN = Pattern.compile("REFERENCES `[^`]+` \\(`([^`]+)`\\)", Pattern.CASE_INSENSITIVE)
        private val FK_TABLE = Pattern.compile("fails \\(`[^`]+`\\.`([^`]+)`\\)", Pattern.CASE_INSENSITIVE)

        private const val MAX_VALUE_PREVIEW = 120

        private fun truncate(value: String?): String {
            if (value == null) {
                return ""
            }
            return if (value.length <= MAX_VALUE_PREVIEW) {
                value
            } else {
                value.substring(0, MAX_VALUE_PREVIEW - 3) + "..."
            }
        }

        private fun joinLabels(labels: List<String>?): String {
            if (labels.isNullOrEmpty()) {
                return ""
            }
            if (labels.size == 1) {
                return labels.first()
            }
            return labels.dropLast(1).joinToString(", ") + " und " + labels.last()
        }

        private fun humanize(value: String?): String {
            if (value.isNullOrBlank()) {
                return ""
            }
            val cleaned = value.replace('_', ' ').replace('-', ' ').trim()
            if (cleaned.isEmpty()) {
                return value
            }
            val builder = StringBuilder(cleaned.length)
            var capitalizeNext = true
            for (character in cleaned) {
                if (character.isWhitespace()) {
                    builder.append(' ')
                    capitalizeNext = true
                } else if (capitalizeNext) {
                    builder.append(character.titlecaseChar())
                    capitalizeNext = false
                } else {
                    builder.append(character.lowercaseChar())
                }
            }
            return builder.toString()
        }

        private fun extractFirst(pattern: Pattern, input: String?): String? {
            if (input == null) {
                return null
            }
            val matcher = pattern.matcher(input)
            return if (matcher.find()) matcher.group(1) else null
        }

        private fun optionalList(value: String?): List<String> =
            if (value.isNullOrBlank()) emptyList() else listOf(value)
    }
}
