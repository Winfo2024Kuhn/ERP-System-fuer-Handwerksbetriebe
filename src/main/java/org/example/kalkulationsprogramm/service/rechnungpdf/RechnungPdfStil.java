package org.example.kalkulationsprogramm.service.rechnungpdf;

import java.awt.Color;

import org.example.kalkulationsprogramm.util.PdfSchriften;

/**
 * Farben und Linienstaerke des Rechnungs-PDFs (eigene Vorlage, siehe {@code RechnungPdfService}).
 * Eine Quelle fuer den Service und die ausgelagerten Summenblock-Bausteine.
 */
public final class RechnungPdfStil {

    /** Einzige Akzentfarbe des Dokuments: Linien, Tabellenkopf, Summen, Fortschrittsbalken - identisch zur Textfarbe (#44403c). */
    public static final Color AKZENTFARBE = PdfSchriften.TEXTFARBE;
    /** Mittlere Trennlinie, z.B. vor dem Zahlbetrag (stone-300). */
    public static final Color LINIE_MITTEL = new Color(0xd6, 0xd3, 0xd1);
    /** Gedaempfter Nebentext: Kopfzeilen-Reste, Hinweise, Unterzeilen (stone-500). */
    public static final Color TEXT_GEDAEMPFT = new Color(0x78, 0x71, 0x6c);
    /** Staerke der Tabellen- und Summenlinien. */
    public static final float LINIE_STAERKE = 1f;

    private RechnungPdfStil() {
    }
}
