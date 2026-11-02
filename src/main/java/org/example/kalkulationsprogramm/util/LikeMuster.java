package org.example.kalkulationsprogramm.util;

import java.util.Locale;

/**
 * Baut Suchmuster für SQL-{@code LIKE} aus Nutzereingaben. Die Platzhalter {@code %} und {@code _}
 * sowie das Escape-Zeichen selbst werden maskiert, damit „50%“ nach genau diesem Text sucht.
 * In der Abfrage immer mit {@code ESCAPE '\'} (JPQL) bzw. {@link #ESCAPE} (Criteria) verwenden.
 */
public final class LikeMuster {

    public static final char ESCAPE = '\\';

    private LikeMuster() {
    }

    /** Maskiert {@code %}, {@code _} und {@code \}. */
    public static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '%' || c == '_' || c == ESCAPE) {
                sb.append(ESCAPE);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** „enthält“-Muster in Kleinbuchstaben: {@code %text%}, für Vergleiche mit {@code lower(spalte)}. */
    public static String enthaelt(String text) {
        return "%" + escape(text.toLowerCase(Locale.ROOT)) + "%";
    }
}
