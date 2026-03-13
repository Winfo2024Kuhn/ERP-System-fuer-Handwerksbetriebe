package org.example.kalkulationsprogramm.dto;

import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;

/**
 * DTO für das Ergebnis der KI-Klassifizierung eines E-Mail-Anhangs.
 * Entscheidet ob ein Dokument als Geschäftsdokument oder sonstiges Dokument
 * gespeichert wird.
 */
public record DokumentKlassifizierung(
        // Erkannter Dokumenttyp
        LieferantDokumentTyp typ,

        // true wenn es sich um ein Geschäftsdokument handelt (Anfrage/AB/LS/RE)
        boolean istGeschaeftsdokument,

        // true wenn dieses Dokument bereits existiert (gleiche Dokumentnummer beim
        // selben Lieferanten)
        boolean istDuplikat,

        // Referenznummer für Verkettung (z.B. "AB-2024-001" auf einer Rechnung)
        String referenzNummer,

        // Vertrauenswert der KI-Klassifizierung (0.0 - 1.0)
        double confidence,

        // Optional: Erkannte Dokumentnummer
        String dokumentNummer) {
    /**
     * Factory-Methode für ein Geschäftsdokument.
     */
    public static DokumentKlassifizierung geschaeftsdokument(
            LieferantDokumentTyp typ,
            String dokumentNummer,
            String referenzNummer,
            double confidence) {
        return new DokumentKlassifizierung(typ, true, false, referenzNummer, confidence, dokumentNummer);
    }

    /**
     * Factory-Methode für ein sonstiges Dokument (kein Geschäftsdokument).
     */
    public static DokumentKlassifizierung sonstigesDokument(double confidence) {
        return new DokumentKlassifizierung(LieferantDokumentTyp.SONSTIG, false, false, null, confidence, null);
    }

    /**
     * Factory-Methode für ein nicht-relevantes Dokument (wird ignoriert).
     */
    public static DokumentKlassifizierung nichtRelevant() {
        return new DokumentKlassifizierung(null, false, false, null, 0.0, null);
    }

    /**
     * Prüft ob das Dokument ignoriert werden soll.
     */
    public boolean sollIgnoriertWerden() {
        return typ == null;
    }
}
