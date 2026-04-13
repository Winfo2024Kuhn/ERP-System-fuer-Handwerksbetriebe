package org.example.kalkulationsprogramm.domain;

/**
 * Dokumenttypen für Lieferanten-Dokumente.
 * Bilden die Dokumentenkette: Anfrage → Auftragsbestätigung → Lieferschein →
 * Rechnung
 * SONSTIG für nicht-geschäftliche Dokumente (Kataloge, Infoblätter etc.)
 */
public enum LieferantDokumentTyp {
    ANGEBOT,
    AUFTRAGSBESTAETIGUNG,
    LIEFERSCHEIN,
    WERKSTOFFZEUGNIS, // EN 10204 Zeugnisse (3.1 / 3.2) – kein Buchungsvorgang
    RECHNUNG,
    GUTSCHRIFT, // Gutschriften vom Lieferanten
    SONSTIG // Nicht-Geschäftsdokumente (Katalog, Info etc.)
}
