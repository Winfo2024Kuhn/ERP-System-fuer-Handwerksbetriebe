package org.example.kalkulationsprogramm.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Findet E-Mail-Adressen in rohen Header-Werten (Anzeigename plus Adresse, mehrere Adressen),
 * z. B. {@code "Max Mustermann <max@example.com>, info@example.org"}.
 */
public final class EmailAdressen {

    /**
     * Match-Pattern für E-Mail-Adressen in beliebigen Header-Werten (Display-Name + Adresse).
     * Possessive Quantoren (`++`) verhindern Backtracking und damit polynomiales ReDoS
     * (CodeQL java/polynomial-redos) auf Eingaben mit vielen Wiederholungen aus den Zeichenklassen.
     * Die Domain ist als Folge von `label.`-Gruppen + TLD aufgebaut, damit der Punkt in
     * keiner Zeichenklasse mit `++` doppelt vorkommt (sonst würde der TLD-Teil leer laufen).
     */
    private static final Pattern ADRESSE = Pattern
            .compile("[A-Za-z0-9._%+\\-]++@(?:[A-Za-z0-9\\-]++\\.)++[A-Za-z]{2,}+");

    /**
     * Defense-in-depth-Cap, um die Regex-Laufzeit auf adversarialen Eingaben endgültig zu deckeln.
     * RFC 5322 begrenzt Header-Zeilen auf 998 Zeichen; 4096 lässt Display-Namen, Encoded-Words
     * und kurze To-Listen großzügig zu. Bewusster Trade-off: Bei extrem langer To-/Cc-Liste
     * werden nur die vorderen Adressen gesucht, ein Cut innerhalb einer einzelnen Adresse ist daher
     * praktisch ausgeschlossen.
     */
    static final int MAX_HEADER_LAENGE = 4096;

    private EmailAdressen() {
    }

    /** Erste Adresse im Header-Wert, sonst {@code null}. */
    public static String ersteAdresse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher m = ADRESSE.matcher(gekappt(raw));
        return m.find() ? m.group() : null;
    }

    /** Domain der ersten Adresse in Kleinbuchstaben ({@code "kanzlei.de"}), sonst {@code null}. */
    public static String domainVon(String raw) {
        String adresse = ersteAdresse(raw);
        if (adresse == null) return null;
        int at = adresse.lastIndexOf('@');
        return at >= 0 && at + 1 < adresse.length() ? adresse.substring(at + 1).toLowerCase(Locale.ROOT) : null;
    }

    /** Steht im Header-Wert eine Adresse genau aus dieser Domain (keine Subdomain, kein Anhängsel)? */
    public static boolean hatAdresseAusDomain(String raw, String domain) {
        if (raw == null || raw.isBlank()) return false;
        Matcher matcher = ADRESSE.matcher(gekappt(raw));
        while (matcher.find()) {
            String adresse = matcher.group();
            int at = adresse.lastIndexOf('@');
            if (at >= 0 && adresse.substring(at + 1).equalsIgnoreCase(domain)) return true;
        }
        return false;
    }

    private static String gekappt(String raw) {
        return raw.length() > MAX_HEADER_LAENGE ? raw.substring(0, MAX_HEADER_LAENGE) : raw;
    }
}
