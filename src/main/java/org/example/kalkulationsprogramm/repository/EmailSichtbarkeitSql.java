package org.example.kalkulationsprogramm.repository;

import java.util.Set;

/**
 * Postfach-Sichtbarkeit als native SQL-Bedingung auf {@code email e}, mit dem benannten Parameter
 * {@value #PARAMETER} (sichtbare Postfach-IDs). Gleiche Regeln wie
 * {@code PostfachSichtbarkeit.siehtEmail} bzw. {@code darfLesen} – hier für Abfragen, die vor dem
 * Seitenschnitt filtern müssen (E-Mail-Suche, E-Mail-Reiter).
 */
final class EmailSichtbarkeitSql {

    /** Benannter Parameter: IDs der sichtbaren Postfächer (nie leer, siehe {@link #parameterWert}). */
    static final String PARAMETER = "sichtbarePostfaecher";

    /** Mail ohne Postfach-Zuordnung = Hauptpostfach-Mail, sonst in mindestens einem sichtbaren Postfach. */
    static final String SIEHT = "(NOT EXISTS (SELECT 1 FROM email_postfach_zuordnung z WHERE z.email_id = e.id)"
            + " OR EXISTS (SELECT 1 FROM email_postfach_zuordnung z WHERE z.email_id = e.id"
            + " AND z.postfach_id IN (:" + PARAMETER + ")))";

    /** Darf geöffnet werden: sichtbar oder einem Projekt, einer Anfrage oder einem Lieferanten zugeordnet. */
    static final String LIEST = "(e.projekt_id IS NOT NULL OR e.anfrage_id IS NOT NULL OR e.lieferant_id IS NOT NULL"
            + " OR " + SIEHT + ")";

    private static final Long KEINE_ID = -1L;

    private EmailSichtbarkeitSql() {
    }

    /** Leere Menge als {@code {-1}}: {@code IN ()} ist kein gültiges SQL. */
    static Set<Long> parameterWert(Set<Long> sichtbarePostfachIds) {
        return sichtbarePostfachIds.isEmpty() ? Set.of(KEINE_ID) : sichtbarePostfachIds;
    }
}
