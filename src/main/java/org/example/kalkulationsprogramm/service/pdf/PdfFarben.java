package org.example.kalkulationsprogramm.service.pdf;

import java.awt.Color;
import java.util.regex.Pattern;

/**
 * Wandelt die in den Firmendaten hinterlegte Firmenfarbe (z. B. {@code #500010}) in
 * PDF-Farben um. Ist keine oder eine ungültige Farbe hinterlegt, bleibt es beim
 * bisherigen Rot der PDFs (red-600, #dc2626).
 */
public final class PdfFarben {

    /** Bisherige Akzentfarbe aller PDFs (red-600, #dc2626). */
    public static final Color STANDARD_AKZENT = new Color(220, 38, 38);

    private static final Pattern HEX = Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})");

    /** Anteil Weiß, mit dem die helle Zeilenfarbe aus der Akzentfarbe gemischt wird. */
    private static final double WEISS_ANTEIL = 0.92;

    private PdfFarben() {
    }

    public static Color akzent(String firmenfarbe) {
        if (firmenfarbe == null) {
            return STANDARD_AKZENT;
        }
        String wert = firmenfarbe.trim();
        if (!HEX.matcher(wert).matches()) {
            return STANDARD_AKZENT;
        }
        String hex = wert.substring(1);
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }
        return new Color(Integer.parseInt(hex, 16));
    }

    /** Sehr helle Variante der Akzentfarbe für abwechselnde Tabellenzeilen. */
    public static Color hell(Color akzent) {
        return new Color(mische(akzent.getRed()), mische(akzent.getGreen()), mische(akzent.getBlue()));
    }

    private static int mische(int kanal) {
        return (int) Math.round(kanal * (1 - WEISS_ANTEIL) + 255 * WEISS_ANTEIL);
    }
}
