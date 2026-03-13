package org.example.kalkulationsprogramm.domain;

/**
 * Typ der Email-Verarbeitung.
 * Ersetzt die separaten Tabellen ProcessedOfferEmail und
 * ProcessedLieferantEmail.
 */
public enum EmailProcessingType {
    PROJEKT, // Email wurde für Projekt-Import verarbeitet
    ANFRAGE, // Email wurde für Anfrages-Import verarbeitet
    LIEFERANT, // Email wurde für Lieferanten-Import verarbeitet
    STEUERBERATER // Email wurde für BWA-Import vom Steuerberater verarbeitet
}
