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
    RECHNUNG,
    GUTSCHRIFT, // Gutschriften vom Lieferanten
    SONSTIG // Nicht-Geschäftsdokumente (Katalog, Info etc.)
}
