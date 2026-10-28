package org.example.kalkulationsprogramm.dto.Email;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Wie die KI einen E-Mail-Entwurf überarbeitet (Auswahl im Menü „KI-Optimierung“). */
public enum EmailSchreibstil {
    /** Nur Rechtschreibung und Grammatik, Stil und Anrede bleiben. */
    RECHTSCHREIBUNG,
    /** Höflich-geschäftlich mit „Sie“. */
    FOERMLICH,
    /** Kurz und freundlich mit „Du“. */
    LOCKER,
    /** Anrede und Ton der zitierten Vorgänger-Mail übernehmen. */
    VERLAUF;

    /**
     * Liest den Wert aus der Anfrage; fehlt er, bleibt es bei der reinen Rechtschreibprüfung.
     *
     * @return leer bei einem unbekannten Wert
     */
    public static Optional<EmailSchreibstil> ausAnfrage(String wert) {
        if (wert == null || wert.isBlank()) {
            return Optional.of(RECHTSCHREIBUNG);
        }
        String gesucht = wert.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(stil -> stil.name().equals(gesucht)).findFirst();
    }
}
