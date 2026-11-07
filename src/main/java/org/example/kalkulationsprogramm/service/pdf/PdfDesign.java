package org.example.kalkulationsprogramm.service.pdf;

import org.openpdf.text.Image;

import java.awt.Color;

/**
 * Das Erscheinungsbild eines Berichts-PDFs aus den Firmendaten: Akzentfarbe
 * (Firmenfarbe oder bisheriges Rot), eine helle Variante für Tabellenzeilen,
 * das Firmenlogo (darf fehlen) und der Firmenname für die Fußzeile.
 */
public record PdfDesign(Color akzent, Color akzentHell, Image logo, String firmenname) {

    /** Gedämpfte Textfarben, wie in den übrigen PDFs (slate). */
    public static final Color TEXT_DUNKEL = new Color(30, 41, 59);
    public static final Color TEXT_ZELLE = new Color(55, 65, 81);
    public static final Color TEXT_GEDAEMPFT = new Color(100, 116, 139);
    public static final Color RAHMEN = new Color(229, 231, 235);
    public static final Color SUMME_HINTERGRUND = new Color(241, 245, 249);

    /**
     * Feste Statusfarben, unabhängig von der Firmenfarbe: Ein Warnhinweis (z. B. „VORLÄUFIG –
     * Monat nicht abgeschlossen“ im Kassenbuch) bleibt immer rot (red-600), eine Erfolgsmeldung
     * („Monat abgeschlossen“) immer grün (green-700). Sie tragen Bedeutung, keine Hausfarbe.
     */
    public static final Color WARNUNG = new Color(220, 38, 38);
    public static final Color ERFOLG = new Color(21, 128, 61);

    public static PdfDesign aus(String firmenfarbe, Image logo, String firmenname) {
        Color akzent = PdfFarben.akzent(firmenfarbe);
        return new PdfDesign(akzent, PdfFarben.hell(akzent), logo, firmenname == null ? "" : firmenname.trim());
    }
}
