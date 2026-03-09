package org.example.kalkulationsprogramm.domain;

/**
 * Typ einer Kostenstelle für die Kostenverteilung.
 */
public enum KostenstellenTyp {
    /**
     * Lagerbestand - Investitionen, keine echten Kosten.
     * Wird nicht in die Gemeinkostenberechnung einbezogen.
     */
    LAGER,

    /**
     * Gemeinkosten - Fixkosten für Gemeinkostenberechnung.
     * Wird zur Berechnung des Gemeinkostensatzes verwendet.
     */
    GEMEINKOSTEN,

    /**
     * Projektzuordnung - bestehende Logik für Projekt-Kosten.
     */
    PROJEKT,

    /**
     * Sonstige Kostenstellen für spezifische Zuordnungen.
     */
    SONSTIG
}
