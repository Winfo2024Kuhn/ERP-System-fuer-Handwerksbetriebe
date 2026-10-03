package org.example.kalkulationsprogramm.util;

import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.pdf.BaseFont;
import lombok.extern.slf4j.Slf4j;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;

/**
 * Die einheitlichen Schriften der Dokument-PDFs.
 * <ul>
 *   <li>Brieftext: Open Sans (Regular, Bold, Italic, BoldItalic), Standardfarbe {@link #TEXTFARBE}</li>
 *   <li>Betreff: Montserrat SemiBold</li>
 * </ul>
 * Die Schriften liegen als TTF unter {@code resources/fonts} (Lizenz: SIL OFL 1.1) und werden
 * ins PDF eingebettet. Lassen sie sich nicht laden, faellt jede Methode auf Times zurueck, damit
 * ein Dokument nie am Schriftladen scheitert.
 */
@Slf4j
public final class PdfSchriften {

    /** Dunkelgrau (stone-700) statt reinem Schwarz. */
    public static final Color TEXTFARBE = new Color(0x44, 0x40, 0x3c);

    /** Standardgroesse des Brieftexts in pt. */
    public static final float BRIEFTEXT_GROESSE = 10f;

    /** Groesse des Betreffs in pt. */
    public static final float BETREFF_GROESSE = 11f;

    private static final BaseFont REGULAR = laden("OpenSans-Regular.ttf");
    private static final BaseFont BOLD = laden("OpenSans-Bold.ttf");
    private static final BaseFont ITALIC = laden("OpenSans-Italic.ttf");
    private static final BaseFont BOLD_ITALIC = laden("OpenSans-BoldItalic.ttf");
    private static final BaseFont BETREFF = laden("Montserrat-SemiBold.ttf");

    private PdfSchriften() {
    }

    /**
     * Brieftext-Schrift. {@code style} sind die {@link Font}-Konstanten (NORMAL, BOLD, ITALIC,
     * BOLDITALIC, optional UNDERLINE/STRIKETHRU). Fett und kursiv kommen aus den echten
     * Schnitten, nicht aus simuliertem Fettdruck.
     */
    public static Font brieftext(float groesse, int style, Color farbe) {
        if (style < 0) style = Font.NORMAL; // Font.UNDEFINED
        boolean fett = (style & Font.BOLD) != 0;
        boolean kursiv = (style & Font.ITALIC) != 0;
        BaseFont basis = fett && kursiv ? BOLD_ITALIC : fett ? BOLD : kursiv ? ITALIC : REGULAR;
        if (basis == null) {
            return FontFactory.getFont(FontFactory.TIMES_ROMAN, groesse, style, farbe);
        }
        int dekoration = style & (Font.UNDERLINE | Font.STRIKETHRU);
        return new Font(basis, groesse, dekoration, farbe);
    }

    public static Font brieftext(float groesse, Color farbe) {
        return brieftext(groesse, Font.NORMAL, farbe);
    }

    /** Betreff-Schrift (Montserrat SemiBold). */
    public static Font betreff(float groesse, Color farbe) {
        if (BETREFF == null) {
            return FontFactory.getFont(FontFactory.TIMES_BOLD, groesse, Font.BOLD, farbe);
        }
        return new Font(BETREFF, groesse, Font.NORMAL, farbe);
    }

    private static BaseFont laden(String datei) {
        String pfad = "/fonts/" + datei;
        try (InputStream in = PdfSchriften.class.getResourceAsStream(pfad)) {
            if (in == null) {
                log.warn("Schrift {} nicht gefunden - Times als Ersatz", pfad);
                return null;
            }
            return BaseFont.createFont(datei, BaseFont.CP1252, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException | RuntimeException e) {
            log.warn("Schrift {} konnte nicht geladen werden - Times als Ersatz: {}", pfad, e.getMessage());
            return null;
        }
    }
}
