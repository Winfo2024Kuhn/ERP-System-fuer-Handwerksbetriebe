package org.example.kalkulationsprogramm.util;

import com.lowagie.text.Font;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfSchriftenTest {

    private static String postScriptName(Font font) {
        assertNotNull(font.getBaseFont(), "Schrift muss aus den eingebetteten TTF kommen, nicht aus dem Times-Ersatz");
        return font.getBaseFont().getPostscriptFontName();
    }

    @Test
    @DisplayName("Brieftext nutzt Open Sans in den vier echten Schnitten")
    void brieftextNutztOpenSans() {
        assertEquals("OpenSans-Regular", postScriptName(PdfSchriften.brieftext(10, Font.NORMAL, PdfSchriften.TEXTFARBE)));
        assertEquals("OpenSans-Bold", postScriptName(PdfSchriften.brieftext(10, Font.BOLD, PdfSchriften.TEXTFARBE)));
        assertEquals("OpenSans-Italic", postScriptName(PdfSchriften.brieftext(10, Font.ITALIC, PdfSchriften.TEXTFARBE)));
        assertEquals("OpenSans-BoldItalic", postScriptName(PdfSchriften.brieftext(10, Font.BOLDITALIC, PdfSchriften.TEXTFARBE)));
    }

    @Test
    @DisplayName("Unterstreichen bleibt erhalten, Fett wird nicht zusätzlich simuliert")
    void unterstreichenBleibtErhalten() {
        Font font = PdfSchriften.brieftext(10, Font.BOLD | Font.UNDERLINE, PdfSchriften.TEXTFARBE);

        assertEquals("OpenSans-Bold", postScriptName(font));
        assertTrue((font.getStyle() & Font.UNDERLINE) != 0);
        assertEquals(0, font.getStyle() & Font.BOLD);
    }

    @Test
    @DisplayName("Betreff nutzt Montserrat SemiBold, Textfarbe ist dunkelgrau #44403c")
    void betreffUndFarbe() {
        Font betreff = PdfSchriften.betreff(PdfSchriften.BETREFF_GROESSE, PdfSchriften.TEXTFARBE);

        assertEquals("Montserrat-SemiBold", postScriptName(betreff));
        assertEquals(11f, betreff.getSize(), 0.01f);
        assertEquals(0x44403c, PdfSchriften.TEXTFARBE.getRGB() & 0xFFFFFF);
    }
}
