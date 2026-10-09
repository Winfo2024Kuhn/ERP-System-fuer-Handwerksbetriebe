package org.example.kalkulationsprogramm.domain;

/**
 * Dokumenttypen für Lieferanten-Dokumente.
 * Bilden die Dokumentenkette: Anfrage → Auftragsbestätigung → Lieferschein →
 * Rechnung
 * WERKSTOFFZEUGNIS hängt seitlich am Lieferschein (Abnahmeprüfzeugnis 3.1/3.2,
 * Werkszeugnis 2.2) und trägt Werkstoff und Charge der gelieferten Ware.
 * SONSTIG für nicht-geschäftliche Dokumente (Kataloge, Infoblätter etc.)
 * BELEG steuert die Berechtigung für das Buchhaltungs-Beleg-Modul
 * (mobile Scanner + PC-Validierung) über das gleiche Abteilungs-Permission-System.
 */
public enum LieferantDokumentTyp {
    ANGEBOT,
    AUFTRAGSBESTAETIGUNG,
    LIEFERSCHEIN,
    RECHNUNG,
    GUTSCHRIFT, // Gutschriften vom Lieferanten
    SONSTIG, // Nicht-Geschäftsdokumente (Katalog, Info etc.)
    BELEG, // Buchhaltungs-Belege (Kasse, Privatentnahme, Bank, Einmalbelege)
    WERKSTOFFZEUGNIS // Abnahmeprüfzeugnis/Werkszeugnis zum Lieferschein
}
