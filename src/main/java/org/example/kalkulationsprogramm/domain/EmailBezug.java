package org.example.kalkulationsprogramm.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Wozu der E-Mail-Reiter einer Detailseite gehört ({@code GET /api/emails/reiter/{bezug}/{id}}).
 * Welche Mails dazu zählen, steht in {@code EmailReiterSql}.
 */
public enum EmailBezug {

    PROJEKT("projekt", "Projekt nicht gefunden."),
    ANFRAGE("anfrage", "Anfrage nicht gefunden."),
    /** Mails aller Projekte des Kunden und aller seiner Anfragen. */
    KUNDE("kunde", "Kunde nicht gefunden."),
    LIEFERANT("lieferant", "Lieferant nicht gefunden.");

    private final String schluessel;
    private final String nichtGefunden;

    EmailBezug(String schluessel, String nichtGefunden) {
        this.schluessel = schluessel;
        this.nichtGefunden = nichtGefunden;
    }

    /** Pfad-Schlüssel, z. B. {@code projekt}. */
    public String schluessel() {
        return schluessel;
    }

    /** Meldung für HTTP 404, wenn es den Datensatz nicht gibt. */
    public String nichtGefunden() {
        return nichtGefunden;
    }

    /** Bereich zum Pfad-Schlüssel (Groß-/Kleinschreibung egal); leer bei unbekanntem Schlüssel. */
    public static Optional<EmailBezug> vonSchluessel(String schluessel) {
        if (schluessel == null) {
            return Optional.empty();
        }
        String gesucht = schluessel.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(b -> b.schluessel.equals(gesucht)).findFirst();
    }
}
