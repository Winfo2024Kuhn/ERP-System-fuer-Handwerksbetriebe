package org.example.kalkulationsprogramm.util.suche;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Macht Text für die E-Mail-Suche vergleichbar – für den gespeicherten Suchtext
 * ({@link EmailSuchtext}) und für die Suchwörter gleichermaßen.
 *
 * <p>Regeln: Kleinbuchstaben ({@link Locale#ROOT}), {@code ä→a ö→o ü→u ß→ss}, übrige Akzente
 * fallen weg (NFD, dann die Markierungen entfernen: „Café“ → „cafe“), dazu die Umschreibung
 * {@code ae→a oe→o ue→u}, jeder Leerraum wird zu einem Leerzeichen. Weil Suchtext und Suchwort
 * gleich behandelt werden, finden „wurth“, „wuerth“, „Würth“ und „WÜRTH“ in beide Richtungen
 * dasselbe („Mueller“ in der Mail findet auch „Müller“). Nebenwirkung: aus „Steuer“ wird „steur“ –
 * auf beiden Seiten, deshalb findet „steuer“ weiter „Steuerberater“.</p>
 *
 * <p>Die Regeln bestimmen das Speicherformat von {@code email.suchtext}. Wer sie ändert, braucht
 * eine Flyway-Migration mit {@code UPDATE email SET suchtext = NULL}, damit die Nachpflege
 * ({@code EmailSuchtextNachpflege}) alle Mails neu berechnet.</p>
 *
 * <p>Reine Hilfsklasse ohne Spring und Datenbank.</p>
 */
public final class EmailSuchNormalisierung {

    /** Höchstens so viele Suchwörter zählen, der Rest wird ignoriert. */
    public static final int MAX_WOERTER = 10;

    /** Akzent-Markierungen nach der NFD-Zerlegung. */
    private static final Pattern MARKIERUNGEN = Pattern.compile("\\p{M}++");
    /** Leerraum inklusive geschütztem Leerzeichen; possessiv gegen Backtracking. */
    private static final Pattern LEERRAUM = Pattern.compile("[\\s\\u00A0\\u2007\\u202F]++");

    private EmailSuchNormalisierung() {
    }

    /** Normalisiert beliebigen Text; {@code null} wird zu {@code ""}. */
    public static String normalisiere(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String klein = text.toLowerCase(Locale.ROOT)
                .replace("ä", "a")
                .replace("ö", "o")
                .replace("ü", "u")
                .replace("ß", "ss");
        String ohneAkzente = MARKIERUNGEN.matcher(Normalizer.normalize(klein, Normalizer.Form.NFD)).replaceAll("");
        String umschrieben = ohneAkzente.replace("ae", "a").replace("oe", "o").replace("ue", "u");
        return LEERRAUM.matcher(umschrieben).replaceAll(" ").strip();
    }

    /**
     * Zerlegt die Eingabe an Leerraum in höchstens {@value #MAX_WOERTER} verschiedene,
     * normalisierte Suchwörter (Reihenfolge wie eingegeben).
     */
    public static List<String> suchwoerter(String eingabe) {
        String normal = normalisiere(eingabe);
        if (normal.isEmpty()) {
            return List.of();
        }
        Set<String> eindeutig = new LinkedHashSet<>(List.of(normal.split(" ")));
        return eindeutig.stream().limit(MAX_WOERTER).toList();
    }
}
