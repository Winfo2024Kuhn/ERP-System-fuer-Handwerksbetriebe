package org.example.kalkulationsprogramm.domain;

/**
 * Audit-Aktionen für AusgangsGeschaeftsDokument (GoBD-konformes Logging).
 */
public enum AusgangsGeschaeftsDokumentAuditAktion {
    /** Initiale Anlage des Dokuments */
    ERSTELLT,

    /** Inhaltliche Änderung (Betrag, Positionen, Adresse, ...) */
    GEAENDERT,

    /** Buchung (Export/Festschreibung; ab hier unveränderbar nach GoBD) */
    GEBUCHT,

    /** Versand an Kunden (E-Mail, Brief) */
    VERSENDET,

    /** Stornierung (Korrekturbuchung; Dokument bleibt erhalten) */
    STORNIERT,

    /** Hard-Delete eines noch nicht gebuchten/versendeten Entwurfs (Pflicht-Begründung) */
    GELOESCHT,

    /** Digitale Annahme durch den Kunden */
    DIGITAL_ANGENOMMEN
}
