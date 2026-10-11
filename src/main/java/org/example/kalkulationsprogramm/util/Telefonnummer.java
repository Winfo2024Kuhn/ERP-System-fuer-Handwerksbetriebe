package org.example.kalkulationsprogramm.util;

/**
 * Gemeinsame Regeln für Telefon- und Mobilnummern von Kunden und Lieferanten.
 *
 * <p>Das Büro schlägt beim Eingeben der Adresse die Ortsvorwahl vor (z. B. „0931 “).
 * Bleibt das Feld danach unberührt, steht nur die Vorwahl drin – das ist keine
 * Rufnummer. Unter {@link #MINDEST_ZIFFERN} Ziffern (nach +49/0049 → 0) gilt eine
 * Nummer deshalb als unvollständig. {@link #ohneReineVorwahl} gilt genauso im PC-Frontend
 * ({@code ohneReineVorwahl()} in {@code react-pc-frontend/src/lib/phoneUtils.ts}).
 * Die Handy-App ({@code anrufbareNummer()} in {@code react-zeiterfassung/src/lib/telefon.ts})
 * nutzt dieselbe Grenze, blendet aber wie {@link #vergleichsziffern} jede kürzere Nummer aus –
 * auch eine Ortsnummer ohne Vorwahl, die vom Handy aus nicht wählbar ist.
 *
 * <p>Bewusst ohne Regex – eine einfache Zeichenschleife.
 */
public final class Telefonnummer {

    /** Weniger Ziffern hat keine vollständige Rufnummer mit Vorwahl. */
    public static final int MINDEST_ZIFFERN = 7;

    private Telefonnummer() {
    }

    /**
     * Schlüssel zum Vergleichen zweier Nummern: nur Ziffern, +49/0049 als führende 0.
     * "+49 30 1234567", "0049 30 1234567" und "030/123-4567" ergeben alle "0301234567".
     *
     * @return die Ziffern oder {@code null}, wenn leer oder kürzer als {@link #MINDEST_ZIFFERN}
     *         – eine reine Vorwahl würde sonst alle Kunden aus demselben Ort treffen
     */
    public static String vergleichsziffern(String telefon) {
        String ziffern = ziffernMitFuehrenderNull(telefon);
        return ziffern.length() < MINDEST_ZIFFERN ? null : ziffern;
    }

    /**
     * Wert zum Speichern: getrimmt; leere Eingabe und reine Vorwahl werden {@code null}.
     *
     * <p>Als reine Vorwahl zählt, was mit 0 bzw. +49/0049 beginnt und weniger als
     * {@link #MINDEST_ZIFFERN} Ziffern hat ("0931", "+49 931"). Eine kurze Ortsnummer
     * ohne Vorwahl ("12345") bleibt stehen – die Anrufer-Erkennung ergänzt sie mit der
     * eigenen Ortsvorwahl.
     */
    public static String ohneReineVorwahl(String telefon) {
        if (telefon == null || telefon.isBlank()) {
            return null;
        }
        String eingabe = telefon.trim();
        String ziffern = ziffernMitFuehrenderNull(eingabe);
        if (ziffern.startsWith("0") && ziffern.length() < MINDEST_ZIFFERN) {
            return null;
        }
        return eingabe;
    }

    private static String ziffernMitFuehrenderNull(String telefon) {
        if (telefon == null) {
            return "";
        }
        String t = telefon.trim();
        if (t.startsWith("+49")) {
            t = "0" + t.substring(3);
        } else if (t.startsWith("0049")) {
            t = "0" + t.substring(4);
        }
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
