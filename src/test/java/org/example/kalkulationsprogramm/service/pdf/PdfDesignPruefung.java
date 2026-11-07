package org.example.kalkulationsprogramm.service.pdf;

import org.openpdf.text.Image;
import org.openpdf.text.pdf.PdfDictionary;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStream;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test-Helfer: prüft, ob ein erzeugtes PDF eine bestimmte Farbe oder Bilder enthält.
 *
 * <p>Farben stehen in den Inhaltsströmen der Seiten als RGB-Operator {@code r g b rg} (Füllung,
 * auch Schrift) bzw. {@code r g b RG} (Linie), jeder Kanal als Anteil von 0 bis 1 – OpenPDF
 * kürzt die Werte (z. B. {@code 0.11373 0.30588 0.84706 rg}). Bilder (Firmenlogo) zählen als
 * Bild-XObjects in den Seiten-Ressourcen.</p>
 */
public final class PdfDesignPruefung {

    private static final String ZAHL = "(\\d++(?:\\.\\d++)?|\\.\\d++)";
    private static final Pattern RGB = Pattern.compile(
            ZAHL + "\\s++" + ZAHL + "\\s++" + ZAHL + "\\s++(?:rg|RG)(?![A-Za-z])");

    private PdfDesignPruefung() {
    }

    /** {@code true}, wenn irgendeine Seite die Farbe als Füll- oder Linienfarbe setzt (±1 je Kanal). */
    public static boolean enthaeltFarbe(byte[] pdf, Color farbe) throws IOException {
        try (PdfReader reader = new PdfReader(pdf)) {
            for (int seite = 1; seite <= reader.getNumberOfPages(); seite++) {
                if (enthaeltFarbe(new String(reader.getPageContent(seite), StandardCharsets.ISO_8859_1), farbe)) {
                    return true;
                }
            }
            return false;
        }
    }

    static boolean enthaeltFarbe(String inhalt, Color farbe) {
        Matcher treffer = RGB.matcher(inhalt);
        while (treffer.find()) {
            if (passt(treffer.group(1), farbe.getRed())
                    && passt(treffer.group(2), farbe.getGreen())
                    && passt(treffer.group(3), farbe.getBlue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean passt(String anteil, int kanal) {
        return Math.abs(Math.round(Double.parseDouble(anteil) * 255) - kanal) <= 1;
    }

    /** Anzahl der eingebetteten Bilder über alle Seiten. */
    public static int anzahlBilder(byte[] pdf) throws IOException {
        try (PdfReader reader = new PdfReader(pdf)) {
            int anzahl = 0;
            for (int seite = 1; seite <= reader.getNumberOfPages(); seite++) {
                anzahl += bilder(reader.getPageN(seite).getAsDict(PdfName.RESOURCES));
            }
            return anzahl;
        }
    }

    private static int bilder(PdfDictionary ressourcen) {
        if (ressourcen == null) {
            return 0;
        }
        PdfDictionary xobjects = ressourcen.getAsDict(PdfName.XOBJECT);
        if (xobjects == null) {
            return 0;
        }
        int anzahl = 0;
        for (PdfName name : xobjects.getKeys()) {
            if (PdfReader.getPdfObject(xobjects.get(name)) instanceof PdfStream stream) {
                if (PdfName.IMAGE.equals(stream.getAsName(PdfName.SUBTYPE))) {
                    anzahl++;
                } else if (PdfName.FORM.equals(stream.getAsName(PdfName.SUBTYPE))) {
                    anzahl += bilder(stream.getAsDict(PdfName.RESOURCES));
                }
            }
        }
        return anzahl;
    }

    /** Kleines PNG als Platzhalter-Firmenlogo. */
    public static Image testLogo() {
        try {
            BufferedImage bild = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(bild, "png", out);
            return Image.getInstance(out.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
