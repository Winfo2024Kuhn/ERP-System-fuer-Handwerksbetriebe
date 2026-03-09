package org.example.kalkulationsprogramm.domain;

/**
 * Enum für Audit-Aktionen bei Zeitbuchungsänderungen.
 */
public enum AuditAktion {
    /** Initiale Erfassung (Start am Handy oder manuelle Anlage im Büro) */
    ERSTELLT,

    /** Nachträgliche Korrektur (z.B. Endzeit angepasst) */
    GEAENDERT,

    /** Stornierung/Löschung */
    STORNIERT
}
