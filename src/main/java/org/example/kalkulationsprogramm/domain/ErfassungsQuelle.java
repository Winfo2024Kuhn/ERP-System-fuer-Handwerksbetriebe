package org.example.kalkulationsprogramm.domain;

/**
 * Enum für die Erfassungsquelle einer Zeitbuchung.
 * Für GoBD-Konformität: Nachvollziehbarkeit woher die Buchung stammt.
 */
public enum ErfassungsQuelle {
    /** Stempelung über die Mobile-PWA (Mitarbeiter am Handy) */
    MOBILE_APP,

    /** Erfassung/Korrektur über das PC-Frontend im Büro */
    DESKTOP,

    /** Administrative Korrektur (z.B. durch Chef/Buchhaltung) */
    ADMIN_KORREKTUR,

    /** Import aus externem System */
    IMPORT,

    /** Automatische Systemverarbeitung (z.B. Auto-Stop bei überschrittenem Zeitfenster) */
    SYSTEM
}
