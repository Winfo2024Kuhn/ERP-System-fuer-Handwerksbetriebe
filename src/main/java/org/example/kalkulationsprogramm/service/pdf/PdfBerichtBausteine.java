package org.example.kalkulationsprogramm.service.pdf;

import org.example.kalkulationsprogramm.util.PdfSchriften;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Image;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfPageEventHelper;
import org.openpdf.text.pdf.PdfWriter;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.util.Locale;
import java.time.format.DateTimeFormatter;

/**
 * Wiederkehrende Bausteine der Berichts-PDFs: Kopf mit Titel und Firmenlogo (oder nur das
 * Logo), Tabellenzellen in Firmenfarbe und eine Fußzeile mit Firmenname, Datum und Seitenzahl.
 */
public final class PdfBerichtBausteine {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private PdfBerichtBausteine() {
    }

    /** Deutsche Zahl mit einer Nachkommastelle, z. B. „1.234,5“; Minus als „-“. */
    public static String zahl(BigDecimal wert) {
        if (wert == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.GERMANY));
        String text = format.format(wert.setScale(1, RoundingMode.HALF_UP).abs());
        return wert.setScale(1, RoundingMode.HALF_UP).signum() < 0 ? "-" + text : text;
    }

    public static Font schrift(float groesse, int stil, Color farbe) {
        return PdfSchriften.brieftext(groesse, stil, farbe);
    }

    /** Titel und Untertitel links, Firmenlogo rechts oben. */
    public static void kopf(Document document, PdfDesign design, String titel, String untertitel)
            throws DocumentException {
        PdfPTable zeile = new PdfPTable(new float[] {65f, 35f});
        zeile.setWidthPercentage(100);

        PdfPCell links = ohneRahmen(new PdfPCell());
        links.addElement(new Paragraph(titel, schrift(18, Font.BOLD, design.akzent())));
        if (untertitel != null && !untertitel.isBlank()) {
            links.addElement(new Paragraph(untertitel, schrift(11, Font.NORMAL, PdfDesign.TEXT_GEDAEMPFT)));
        }
        zeile.addCell(links);

        PdfPCell rechts = ohneRahmen(new PdfPCell());
        rechts.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Image logo = design.logo();
        if (logo != null) {
            Image kopie = Image.getInstance(logo);
            kopie.scaleToFit(150, 60);
            kopie.setAlignment(Image.ALIGN_RIGHT);
            rechts.addElement(kopie);
        }
        zeile.addCell(rechts);

        zeile.setSpacingAfter(14f);
        document.add(zeile);
    }

    /**
     * Firmenlogo als eigener Absatz (links oben), auf höchstens {@code breite} × {@code hoehe}
     * verkleinert. Ohne hinterlegtes Logo passiert nichts – bewusst kein Software-Logo als Ersatz.
     */
    public static void logo(Document document, PdfDesign design, float breite, float hoehe)
            throws DocumentException {
        if (design.logo() == null) {
            return;
        }
        Image kopie = Image.getInstance(design.logo());
        kopie.scaleToFit(breite, hoehe);
        document.add(kopie);
    }

    public static PdfPCell kopfZelle(String text, PdfDesign design, int ausrichtung) {
        PdfPCell zelle = new PdfPCell(new Phrase(text, schrift(8f, Font.BOLD, Color.WHITE)));
        zelle.setBackgroundColor(design.akzent());
        zelle.setHorizontalAlignment(ausrichtung);
        zelle.setVerticalAlignment(Element.ALIGN_MIDDLE);
        zelle.setPadding(5f);
        zelle.setBorderColor(design.akzent());
        return zelle;
    }

    public static PdfPCell zelle(String text, Color hintergrund, int ausrichtung) {
        return zelle(text, hintergrund, ausrichtung, schrift(8.5f, Font.NORMAL, PdfDesign.TEXT_ZELLE));
    }

    public static PdfPCell zelle(String text, Color hintergrund, int ausrichtung, Font schrift) {
        PdfPCell zelle = new PdfPCell(new Phrase(text == null ? "" : text, schrift));
        zelle.setBackgroundColor(hintergrund);
        zelle.setHorizontalAlignment(ausrichtung);
        zelle.setVerticalAlignment(Element.ALIGN_MIDDLE);
        zelle.setPadding(4.5f);
        zelle.setBorderColor(PdfDesign.RAHMEN);
        zelle.setBorderWidth(0.5f);
        return zelle;
    }

    /** Kleiner grauer Hinweis unter oder über einer Tabelle. */
    public static Paragraph hinweis(String text) {
        Paragraph absatz = new Paragraph(text, schrift(8.5f, Font.ITALIC, PdfDesign.TEXT_GEDAEMPFT));
        absatz.setSpacingBefore(6f);
        absatz.setSpacingAfter(6f);
        return absatz;
    }

    public static Paragraph zwischenueberschrift(String text, PdfDesign design) {
        Paragraph absatz = new Paragraph(text, schrift(11.5f, Font.BOLD, design.akzent()));
        absatz.setSpacingBefore(10f);
        absatz.setSpacingAfter(6f);
        return absatz;
    }

    private static PdfPCell ohneRahmen(PdfPCell zelle) {
        zelle.setBorder(Rectangle.NO_BORDER);
        zelle.setPadding(0f);
        return zelle;
    }

    /** Fußzeile: Firmenname links, „Erstellt am …“ mittig, „Seite n“ rechts. */
    public static PdfPageEventHelper fusszeile(PdfDesign design, LocalDate erstelltAm) {
        return new Fusszeile(design.firmenname(), "Erstellt am " + erstelltAm.format(DATUM));
    }

    private static final class Fusszeile extends PdfPageEventHelper {
        private final String firmenname;
        private final String erstellt;
        private final Font schrift = schrift(7.5f, Font.NORMAL, PdfDesign.TEXT_GEDAEMPFT);

        private Fusszeile(String firmenname, String erstellt) {
            this.firmenname = firmenname;
            this.erstellt = erstellt;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            float y = document.bottomMargin() - 18f;
            var inhalt = writer.getDirectContent();
            ColumnText.showTextAligned(inhalt, Element.ALIGN_LEFT, new Phrase(firmenname, schrift),
                    document.left(), y, 0);
            ColumnText.showTextAligned(inhalt, Element.ALIGN_CENTER, new Phrase(erstellt, schrift),
                    (document.left() + document.right()) / 2, y, 0);
            ColumnText.showTextAligned(inhalt, Element.ALIGN_RIGHT,
                    new Phrase("Seite " + writer.getPageNumber(), schrift), document.right(), y, 0);
        }
    }
}
