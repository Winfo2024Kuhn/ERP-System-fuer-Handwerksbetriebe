package org.example.kalkulationsprogramm.service.spam;

import java.util.Locale;
import java.util.Set;

/**
 * Kleine Helfer rund um Mail-Domains für die Spam- und Abzocke-Erkennung. Reine Logik.
 */
public final class MailDomain {

    /**
     * Zweite Ebenen, unter denen Firmen erst eine Ebene tiefer ihre Domain haben
     * ({@code firma.co.uk}, {@code amt.gv.at}). Bewusst keine vollständige Public-Suffix-Liste:
     * Ein Fehler hier führt höchstens zu einem fehlenden Signal, nie zu einem Fehlalarm.
     */
    private static final Set<String> ZWEITE_EBENEN = Set.of("co", "com", "org", "net", "ac", "gv", "or", "gov", "edu");

    private MailDomain() {
    }

    /**
     * Domain einer Adresse in Kleinbuchstaben ({@code Max <max@Firma.de>} → {@code firma.de}),
     * {@code null}, wenn keine erkennbar ist.
     */
    public static String vonAdresse(String adresse) {
        if (adresse == null) {
            return null;
        }
        String reine = adresse;
        int auf = reine.indexOf('<');
        int zu = reine.indexOf('>', auf + 1);
        if (auf >= 0 && zu > auf) {
            reine = reine.substring(auf + 1, zu);
        }
        int at = reine.lastIndexOf('@');
        if (at < 0 || at == reine.length() - 1) {
            return null;
        }
        String domain = reine.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        while (domain.endsWith(".")) {
            domain = domain.substring(0, domain.length() - 1);
        }
        return domain.isEmpty() ? null : domain;
    }

    /**
     * Die Haupt-Domain ohne Subdomains ({@code mail.firma.de} → {@code firma.de},
     * {@code shop.firma.co.uk} → {@code firma.co.uk}).
     */
    public static String hauptDomain(String domain) {
        if (domain == null) {
            return null;
        }
        String[] teile = domain.toLowerCase(Locale.ROOT).split("\\.");
        int n = teile.length;
        if (n < 2) {
            return domain.toLowerCase(Locale.ROOT);
        }
        if (n >= 3 && teile[n - 1].length() == 2 && ZWEITE_EBENEN.contains(teile[n - 2])) {
            return teile[n - 3] + "." + teile[n - 2] + "." + teile[n - 1];
        }
        return teile[n - 2] + "." + teile[n - 1];
    }

    /**
     * Gehören beide Domains zur selben Firma (gleiche Haupt-Domain)?
     */
    public static boolean gleicheOrganisation(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        return hauptDomain(a).equals(hauptDomain(b));
    }

    /**
     * Ist {@code domain} gleich {@code erlaubt} oder eine Subdomain davon
     * ({@code mail.paypal.de} gehört zu {@code paypal.de}, {@code paypal.de.example} nicht)?
     */
    public static boolean gehoertZu(String domain, String erlaubt) {
        if (domain == null || erlaubt == null) {
            return false;
        }
        String d = domain.toLowerCase(Locale.ROOT);
        return d.equals(erlaubt) || d.endsWith("." + erlaubt);
    }
}
