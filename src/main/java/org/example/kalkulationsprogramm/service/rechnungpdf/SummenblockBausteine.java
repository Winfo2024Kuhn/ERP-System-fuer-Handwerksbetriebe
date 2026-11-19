package org.example.kalkulationsprogramm.service.rechnungpdf;

import static org.example.kalkulationsprogramm.service.rechnungpdf.RechnungPdfStil.AKZENTFARBE;
import static org.example.kalkulationsprogramm.service.rechnungpdf.RechnungPdfStil.LINIE_MITTEL;
import static org.example.kalkulationsprogramm.service.rechnungpdf.RechnungPdfStil.LINIE_STAERKE;
import static org.example.kalkulationsprogramm.service.rechnungpdf.RechnungPdfStil.TEXT_GEDAEMPFT;

import java.awt.Color;
import java.text.NumberFormat;
import java.util.Locale;

import org.example.kalkulationsprogramm.util.PdfSchriften;
import org.openpdf.text.Chunk;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;

/**
 * Zeichen-Bausteine des Summenblocks im Rechnungs-PDF. Jede Methode haengt Zellen an eine
 * dreispaltige Summentabelle (Label | Mitte | Betrag) an. Texte werden als Klartext gesetzt.
 */
public final class SummenblockBausteine {

    private SummenblockBausteine() {
    }

    /** Deutsches Betragsformat mit genau zwei Nachkommastellen. */
    public static NumberFormat betragsformat() {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.GERMANY);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf;
    }

    /**
     * Abschnittsüberschrift im Summenblock: kleine graue Versalien über einer kräftigen
     * Linie — dieselbe Optik wie der Kopf der Positionstabelle. Das Dokument kennt keine
     * Kästen, deshalb trennt hier die Linie statt eines Rahmens.
     */
    public static void abschnittsUeberschrift(PdfPTable sumTable, String text, float abstandDavor) {
        if (abstandDavor > 0f) {
            PdfPCell abstand = new PdfPCell(new Phrase(" "));
            abstand.setBorder(Rectangle.NO_BORDER);
            abstand.setColspan(3);
            abstand.setFixedHeight(abstandDavor);
            sumTable.addCell(abstand);
        }

        Chunk beschriftung = new Chunk(text.toUpperCase(Locale.GERMANY),
                PdfSchriften.brieftext(9, Font.BOLD, TEXT_GEDAEMPFT));
        beschriftung.setCharacterSpacing(0.8f);

        PdfPCell zelle = new PdfPCell(new Phrase(beschriftung));
        zelle.setBorder(Rectangle.BOTTOM);
        zelle.setBorderColor(AKZENTFARBE);
        zelle.setBorderWidth(LINIE_STAERKE);
        zelle.setColspan(3);
        zelle.setPaddingBottom(5f);
        sumTable.addCell(zelle);
    }

    /**
     * Fortschrittsbalken über die volle Breite plus Beschriftung darunter, in der
     * Akzentfarbe: gefuellter Teil satt, offener Teil als heller Ton derselben Farbe.
     */
    public static void fortschrittsBalken(PdfPTable sumTable, double prozent) {
        float anteil = (float) Math.max(0d, Math.min(100d, prozent));
        Color gefuellt = AKZENTFARBE;
        Color offen = aufhellen(AKZENTFARBE, 0.88f);

        // Ein PdfPTable braucht positive Spaltenbreiten, daher die Randfaelle einzeln.
        boolean nurGefuellt = anteil >= 100f;
        boolean nurOffen = anteil <= 0f;
        PdfPTable balken = nurGefuellt || nurOffen
                ? new PdfPTable(new float[] { 1f })
                : new PdfPTable(new float[] { anteil, 100f - anteil });
        balken.setWidthPercentage(100);

        if (!nurOffen) {
            balken.addCell(balkenSegment(gefuellt));
        }
        if (!nurGefuellt) {
            balken.addCell(balkenSegment(offen));
        }

        PdfPCell balkenZelle = new PdfPCell(balken);
        balkenZelle.setBorder(Rectangle.NO_BORDER);
        balkenZelle.setColspan(3);
        balkenZelle.setPaddingTop(6f);
        balkenZelle.setPaddingBottom(2f);
        sumTable.addCell(balkenZelle);

        NumberFormat pf = NumberFormat.getNumberInstance(Locale.GERMANY);
        pf.setMinimumFractionDigits(0);
        pf.setMaximumFractionDigits(0);
        PdfPCell beschriftung = new PdfPCell(new Phrase(
                "Mit dieser Rechnung sind " + pf.format(anteil) + " % des Auftrags abgerechnet.",
                PdfSchriften.brieftext(8, Font.ITALIC, TEXT_GEDAEMPFT)));
        beschriftung.setBorder(Rectangle.NO_BORDER);
        beschriftung.setColspan(3);
        beschriftung.setPaddingBottom(6f);
        sumTable.addCell(beschriftung);
    }

    /**
     * Zeile der Abrechnungsübersicht: links die Bezeichnung mit optionaler Unterzeile,
     * rechts der Bruttobetrag mit dem Nettobetrag klein und grau darunter.
     */
    public static void uebersichtsZeile(PdfPTable sumTable, String bezeichnung, String unterzeile,
                                        String bruttoText, String nettoText, boolean hervorgehoben) {
        Font textFont = PdfSchriften.brieftext(hervorgehoben ? 11 : 10,
                hervorgehoben ? Font.BOLD : Font.NORMAL, PdfSchriften.TEXTFARBE);
        Font unterFont = PdfSchriften.brieftext(8, Font.NORMAL, TEXT_GEDAEMPFT);
        Font nettoFont = PdfSchriften.brieftext(8, Font.ITALIC, TEXT_GEDAEMPFT);

        Phrase links = new Phrase(new Chunk(bezeichnung, textFont));
        if (unterzeile != null && !unterzeile.isBlank()) {
            links.add(Chunk.NEWLINE);
            links.add(new Chunk(unterzeile, unterFont));
        }
        PdfPCell linkeZelle = new PdfPCell(links);
        linkeZelle.setBorder(Rectangle.NO_BORDER);
        linkeZelle.setColspan(2);
        linkeZelle.setLeading(0f, 1.25f);
        linkeZelle.setPaddingTop(4f);
        linkeZelle.setPaddingBottom(3f);
        sumTable.addCell(linkeZelle);

        Phrase rechts = new Phrase(new Chunk(bruttoText, textFont));
        if (nettoText != null && !nettoText.isBlank()) {
            rechts.add(Chunk.NEWLINE);
            rechts.add(new Chunk(nettoText, nettoFont));
        }
        PdfPCell rechteZelle = new PdfPCell(rechts);
        rechteZelle.setBorder(Rectangle.NO_BORDER);
        rechteZelle.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rechteZelle.setLeading(0f, 1.25f);
        rechteZelle.setPaddingTop(4f);
        rechteZelle.setPaddingBottom(3f);
        sumTable.addCell(rechteZelle);
    }

    /** Zeile im Zahlblock: Label, optionaler Mittelwert (z.B. der Steuersatz) und Betrag. */
    public static void zahlZeile(PdfPTable sumTable, String label, String mitte, String betrag,
                                 Font labelFont, Font betragFont) {
        PdfPCell labelZelle = new PdfPCell(new Phrase(label, labelFont));
        labelZelle.setBorder(Rectangle.NO_BORDER);
        labelZelle.setPaddingTop(4f);
        labelZelle.setPaddingBottom(3f);
        sumTable.addCell(labelZelle);

        PdfPCell mittelZelle = new PdfPCell(new Phrase(mitte != null ? mitte : "", labelFont));
        mittelZelle.setBorder(Rectangle.NO_BORDER);
        mittelZelle.setHorizontalAlignment(Element.ALIGN_RIGHT);
        mittelZelle.setPaddingTop(4f);
        mittelZelle.setPaddingBottom(3f);
        sumTable.addCell(mittelZelle);

        PdfPCell betragZelle = new PdfPCell(new Phrase(betrag, betragFont));
        betragZelle.setBorder(Rectangle.NO_BORDER);
        betragZelle.setHorizontalAlignment(Element.ALIGN_RIGHT);
        betragZelle.setPaddingTop(4f);
        betragZelle.setPaddingBottom(3f);
        sumTable.addCell(betragZelle);
    }

    /** Schlusszeile des Summenblocks: Label links, Betrag rechts, fett ohne Hintergrund. */
    public static void gesamtZeile(PdfPTable sumTable, String label, String betrag) {
        Font font = PdfSchriften.brieftext(12, Font.BOLD, AKZENTFARBE);

        PdfPCell labelZelle = new PdfPCell(new Phrase(label, font));
        labelZelle.setBorder(Rectangle.NO_BORDER);
        labelZelle.setPaddingTop(7f);
        labelZelle.setPaddingBottom(6f);
        sumTable.addCell(labelZelle);

        PdfPCell leer = new PdfPCell(new Phrase("", font));
        leer.setBorder(Rectangle.NO_BORDER);
        sumTable.addCell(leer);

        PdfPCell betragZelle = new PdfPCell(new Phrase(betrag, font));
        betragZelle.setBorder(Rectangle.NO_BORDER);
        betragZelle.setPaddingTop(7f);
        betragZelle.setPaddingBottom(6f);
        betragZelle.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sumTable.addCell(betragZelle);
    }

    /** Feine graue Linie ueber die volle Breite, z.B. vor „Noch offen“ und vor dem Zahlbetrag. */
    public static void trennLinie(PdfPTable sumTable) {
        PdfPCell linie = new PdfPCell();
        linie.setBorder(Rectangle.TOP);
        linie.setBorderColor(LINIE_MITTEL);
        linie.setBorderWidth(0.75f);
        linie.setColspan(3);
        linie.setFixedHeight(5f);
        sumTable.addCell(linie);
    }

    /** Kräftige Abschlusslinie unter der Gesamtsumme bzw. dem Zahlbetrag. */
    public static void abschlussLinie(PdfPTable sumTable) {
        PdfPCell linie = new PdfPCell();
        linie.setBorder(Rectangle.TOP);
        linie.setBorderColor(AKZENTFARBE);
        linie.setBorderWidth(LINIE_STAERKE);
        linie.setColspan(3);
        linie.setFixedHeight(2f);
        sumTable.addCell(linie);
    }

    /** Mischt eine Farbe mit Weiss — 0f bleibt die Farbe, 1f ist reines Weiss. */
    private static Color aufhellen(Color farbe, float anteil) {
        return new Color(
                Math.round(farbe.getRed() + (255 - farbe.getRed()) * anteil),
                Math.round(farbe.getGreen() + (255 - farbe.getGreen()) * anteil),
                Math.round(farbe.getBlue() + (255 - farbe.getBlue()) * anteil));
    }

    private static PdfPCell balkenSegment(Color farbe) {
        PdfPCell segment = new PdfPCell(new Phrase(" "));
        segment.setBorder(Rectangle.NO_BORDER);
        segment.setBackgroundColor(farbe);
        segment.setFixedHeight(5f);
        // Ohne das bleibt zwischen gefuelltem und offenem Teil ein weisser Spalt stehen.
        segment.setPadding(0f);
        return segment;
    }
}
