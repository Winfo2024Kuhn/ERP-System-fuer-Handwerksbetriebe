package org.example.kalkulationsprogramm.service.dokumentstand;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Zahlen und Texte für die Änderungssätze der Dokument-Stände, wie ein Handwerker sie liest:
 * „1.234,50 €“, „2,5“, „10 %“, Titel in deutschen Anführungszeichen und auf 60 Zeichen gekürzt.
 */
final class StandTextFormat {

    static final int MAX_TITEL = 60;
    private static final DecimalFormatSymbols DE = DecimalFormatSymbols.getInstance(Locale.GERMANY);

    private StandTextFormat() {
    }

    static String betrag(double wert) {
        return betrag(BigDecimal.valueOf(wert));
    }

    static String betrag(BigDecimal wert) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DE);
        return format.format(wert.setScale(2, RoundingMode.HALF_UP)) + " €";
    }

    /** Menge oder Prozentwert: ohne überflüssige Nachkommastellen, mit Komma. */
    static String zahl(double wert) {
        return new DecimalFormat("#,##0.###", DE).format(wert);
    }

    static String prozent(double wert) {
        return zahl(wert) + " %";
    }

    /** MwSt-Satz als Anteil (0,19) → „19 %“. */
    static String mwst(BigDecimal satz) {
        return satz == null ? "–" : prozent(satz.multiply(BigDecimal.valueOf(100)).doubleValue());
    }

    /** „„Titel““ – gekürzt auf {@value #MAX_TITEL} Zeichen mit „…“. */
    static String zitat(String text) {
        String t = text == null ? "" : text.trim();
        if (t.length() > MAX_TITEL) {
            t = t.substring(0, MAX_TITEL - 1) + "…";
        }
        return "„" + t + "“";
    }
}
