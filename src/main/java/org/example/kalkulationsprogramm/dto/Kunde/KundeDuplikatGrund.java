package org.example.kalkulationsprogramm.dto.Kunde;

/**
 * Gründe, warum ein Bestandskunde als möglicher Duplikat eines neu anzulegenden
 * Kunden erkannt wurde. Wird sowohl im Backend für die Score-Berechnung als
 * auch im Frontend zur Anzeige der Match-Begründung genutzt.
 *
 * <p>Die Reihenfolge spiegelt die Stärke des Match wider – obere Gründe sind
 * "harte" Treffer (sehr wahrscheinlich Duplikat), untere sind weicher.
 */
public enum KundeDuplikatGrund {
    EMAIL_GLEICH("Gleiche E-Mail-Adresse", 100, true),
    TELEFON_GLEICH("Gleiche Telefonnummer", 90, true),
    MOBILTELEFON_GLEICH("Gleiche Mobilnummer", 90, true),
    NAME_PLZ_GLEICH("Gleicher Name und PLZ", 60, false),
    NAME_STRASSE_GLEICH("Gleicher Name und Straße", 50, false);

    private final String anzeigetext;
    private final int score;
    private final boolean hart;

    KundeDuplikatGrund(String anzeigetext, int score, boolean hart) {
        this.anzeigetext = anzeigetext;
        this.score = score;
        this.hart = hart;
    }

    public String getAnzeigetext() {
        return anzeigetext;
    }

    public int getScore() {
        return score;
    }

    /** Harte Treffer reichen alleine, um einen Duplikat-Verdacht auszulösen. */
    public boolean isHart() {
        return hart;
    }
}
