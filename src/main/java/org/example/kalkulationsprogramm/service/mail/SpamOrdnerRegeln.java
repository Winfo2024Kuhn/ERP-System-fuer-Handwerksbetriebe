package org.example.kalkulationsprogramm.service.mail;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Welcher Ordner auf dem Mail-Server ist der Spam-Ordner – und welcher darf es nie sein.
 * Gemeinsam genutzt vom Spam-Abgleich (Erkennen, Prüfen vor dem Verschieben) und vom
 * Speichern des Postfachs (Prüfen der Auswahl).
 */
public final class SpamOrdnerRegeln {

    static final String POSTEINGANG = "INBOX";

    /** Kennzeichnung des Spam-Ordners nach RFC 6154 (SPECIAL-USE). */
    static final String JUNK_ATTRIBUT = "\\Junk";

    /** Sonderordner, in denen Spam nichts zu suchen hat (Papierkorb wird oft automatisch geleert). */
    static final Set<String> SONDER_ATTRIBUTE = Set.of("\\trash", "\\sent", "\\drafts", "\\all", "\\flagged");

    /** Übliche Namen, falls der Server seinen Spam-Ordner nicht kennzeichnet – in dieser Reihenfolge. */
    private static final List<String> SPAM_NAMEN = List.of(
            "spam", "junk", "junk-e-mail", "junk e-mail", "junk-mail", "junk mail", "junk email",
            "bulk mail", "unerwünscht");

    /** Übliche Namen von Papierkorb, Gesendet, Entwürfen und Postausgang. */
    private static final Set<String> UNGEEIGNETE_NAMEN = Set.of(
            "trash", "papierkorb", "deleted items", "deleted messages", "gelöschte elemente", "gelöschte objekte",
            "sent", "sent items", "sent messages", "gesendet", "gesendete objekte", "gesendete elemente",
            "drafts", "entwürfe", "outbox", "postausgang");

    private static final Pattern STEUERZEICHEN = Pattern.compile("\\p{Cntrl}");

    private SpamOrdnerRegeln() {
    }

    /**
     * Ein Ordner auf dem Server.
     *
     * @param gekennzeichnet der Server meldet ihn als Spam-Ordner ({@code \Junk})
     * @param sonderordner   der Server meldet ihn als Papierkorb, Gesendet, Entwürfe o. Ä.
     */
    public record ServerOrdner(String name, boolean gekennzeichnet, boolean sonderordner) {
    }

    /** Erst der vom Server gekennzeichnete Ordner, dann übliche Namen (auch als Unterordner wie „INBOX.Spam“). */
    public static Optional<String> erkenneSpamOrdner(List<ServerOrdner> ordner) {
        List<ServerOrdner> geeignet = ordner.stream().filter(SpamOrdnerRegeln::eignetSich).toList();
        Optional<String> gekennzeichnet = geeignet.stream()
                .filter(ServerOrdner::gekennzeichnet)
                .map(ServerOrdner::name)
                .findFirst();
        if (gekennzeichnet.isPresent()) {
            return gekennzeichnet;
        }
        for (String name : SPAM_NAMEN) {
            for (ServerOrdner o : geeignet) {
                if (letzterTeil(o.name()).equalsIgnoreCase(name)) {
                    return Optional.of(o.name());
                }
            }
        }
        return Optional.empty();
    }

    /** Nie der Posteingang selbst und nie ein Papierkorb, Gesendet- oder Entwurfsordner. */
    public static boolean eignetSich(ServerOrdner ordner) {
        return !ordner.sonderordner() && eignetSichDemNamenNach(ordner.name());
    }

    /** Prüfung allein am Namen – fürs Speichern im Postfach, wenn der Server nicht gefragt wird. */
    public static boolean eignetSichDemNamenNach(String name) {
        if (name == null || name.isBlank() || STEUERZEICHEN.matcher(name).find()) {
            return false;
        }
        String n = name.trim();
        return !n.equalsIgnoreCase(POSTEINGANG)
                && !UNGEEIGNETE_NAMEN.contains(letzterTeil(n).toLowerCase(Locale.ROOT));
    }

    static String letzterTeil(String ordnerName) {
        int trenner = Math.max(ordnerName.lastIndexOf('.'), ordnerName.lastIndexOf('/'));
        return trenner < 0 ? ordnerName : ordnerName.substring(trenner + 1);
    }
}
