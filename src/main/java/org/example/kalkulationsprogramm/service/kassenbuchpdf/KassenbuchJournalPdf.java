package org.example.kalkulationsprogramm.service.kassenbuchpdf;

import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.Kassenzaehlung;
import org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine;
import org.example.kalkulationsprogramm.service.pdf.PdfDesign;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;

import java.awt.Color;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.formatEuro;
import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.formatProzent;
import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.gegenkonto;
import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.kuerze;
import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.mwstBetrag;
import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.nullSafe;
import static org.example.kalkulationsprogramm.service.kassenbuchpdf.KassenbuchTexte.verwendungszweck;

/**
 * Die Tabellen des Kassenbuch-PDFs: das Journal (eine Zeile pro Bargeldbewegung mit
 * dem Bestand nach jeder Buchung), der Summenblock darunter, der Hinweis auf nicht
 * bar bezahlte Belege und die Kassenstürze des Monats.
 *
 * <p>Tabellenkopf und hervorgehobener Endbestand stehen in der Firmenfarbe, die
 * abwechselnden Zeilen in deren heller Variante (beides aus {@link PdfDesign}).</p>
 */
public final class KassenbuchJournalPdf {

    private static final Color STORNO_BG = new Color(248, 250, 252); // slate-50
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("dd.MM.yy");

    private final PdfDesign design;

    public KassenbuchJournalPdf(PdfDesign design) {
        this.design = design;
    }

    /**
     * Das eigentliche Kassenbuch: eine Zeile pro Bewegung, chronologisch, mit dem
     * Bestand nach jeder Buchung; danach Summenblock und Hinweis auf Nicht-Bar-Belege.
     */
    public void journal(Document doc, List<Beleg> belege, BigDecimal anfangsbestand) throws DocumentException {
        doc.add(abschnitt("Kasse · Bargeldbewegungen", 6f));

        List<Beleg> kasse = belege.stream()
                .filter(b -> b.getBelegKategorie() != null && b.getBelegKategorie().istKassenBewegung())
                .sorted(Comparator
                        .comparing(Beleg::getBelegDatum, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(b -> b.getLaufendeNummer() == null ? Long.MAX_VALUE : b.getLaufendeNummer())
                        .thenComparing(Beleg::getId))
                .toList();

        //           Nr   Datum Beleg-Nr Zweck Gegenkonto Zahlart MwSt% MwSt€ Ein   Aus   Bestand
        PdfPTable t = new PdfPTable(new float[]{
                0.7f, 1.0f, 1.2f, 4.2f, 1.9f, 1.2f, 0.8f, 1.1f, 1.2f, 1.2f, 1.3f });
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        kopfzeile(t, "Nr.", "Datum", "Beleg-Nr.", "Verwendungszweck", "Gegenkonto",
                "Zahlungsart", "MwSt", "MwSt €", "Einnahme €", "Ausgabe €", "Bestand €");
        uebertrag(t, anfangsbestand);
        if (kasse.isEmpty()) {
            t.addCell(leerZeile());
        }

        BigDecimal bestand = anfangsbestand;
        BigDecimal sumEin = BigDecimal.ZERO;
        BigDecimal sumAus = BigDecimal.ZERO;
        boolean alt = false;
        for (Beleg b : kasse) {
            BigDecimal brutto = nullSafe(b.getBetragBrutto());
            boolean ausgang = b.getBelegKategorie().istAusgang();
            if (ausgang) {
                bestand = bestand.subtract(brutto);
                sumAus = sumAus.add(brutto);
            } else {
                bestand = bestand.add(brutto);
                sumEin = sumEin.add(brutto);
            }
            // Stornierte Zeilen und Gegenbuchungen faerben wir grau ein --
            // sie bleiben stehen (das verlangt die Aufbewahrung), sollen aber
            // beim Ueberfliegen nicht mit gueltigen Buchungen verwechselt werden.
            boolean stornoBezug = b.getStornoFuerBelegId() != null || b.getStorniertDurchBelegId() != null;
            Color bg = stornoBezug ? STORNO_BG : wechselfarbe(alt);
            zeile(t, b, brutto, ausgang, bestand, bg);
            alt = !alt;
        }

        doc.add(t);
        summenblock(doc, anfangsbestand, sumEin, sumAus, bestand);
        nichtBarHinweis(doc, belege);
    }

    /** Kassenstuerze des Monats: gezaehltes Bargeld gegen den rechnerischen Bestand. */
    public void kassenstuerze(Document doc, List<Kassenzaehlung> zaehlungen) throws DocumentException {
        if (zaehlungen.isEmpty()) return;
        doc.add(abschnitt("Kassenstürze in diesem Monat", 16f));

        PdfPTable t = new PdfPTable(new float[]{ 1.3f, 1.6f, 1.6f, 1.4f, 5f });
        t.setWidthPercentage(100);
        kopfzeile(t, "Stichtag", "Gezählt €", "Laut Kassenbuch €", "Differenz €", "Bemerkung");
        boolean alt = false;
        for (Kassenzaehlung z : zaehlungen) {
            Color bg = wechselfarbe(alt);
            t.addCell(zelle(z.getStichtag().format(DATE_FMT), bg, Element.ALIGN_LEFT));
            t.addCell(zelle(formatEuro(z.getGezaehlterBestand()), bg, Element.ALIGN_RIGHT));
            t.addCell(zelle(formatEuro(z.getRechnerischerBestand()), bg, Element.ALIGN_RIGHT));
            t.addCell(zelle(formatEuro(z.getDifferenz()), bg, Element.ALIGN_RIGHT));
            String bem = z.getBemerkung() != null ? z.getBemerkung() : "";
            if (z.getAusgleichBelegId() != null) {
                bem = (bem.isBlank() ? "" : bem + " · ") + "Differenz wurde ausgebucht";
            }
            t.addCell(zelle(bem, bg, Element.ALIGN_LEFT));
            alt = !alt;
        }
        doc.add(t);
    }

    private Color wechselfarbe(boolean alt) {
        return alt ? design.akzentHell() : Color.WHITE;
    }

    private void zeile(PdfPTable t, Beleg b, BigDecimal brutto, boolean ausgang, BigDecimal bestand, Color bg) {
        t.addCell(zelle(b.getLaufendeNummer() != null ? b.getLaufendeNummer().toString() : "–",
                bg, Element.ALIGN_RIGHT));
        t.addCell(zelle(b.getBelegDatum() != null ? b.getBelegDatum().format(DATE_SHORT) : "–",
                bg, Element.ALIGN_LEFT));
        t.addCell(zelle(kuerze(b.getBelegNummer(), 14), bg, Element.ALIGN_LEFT));
        t.addCell(zelle(verwendungszweck(b), bg, Element.ALIGN_LEFT));
        t.addCell(zelle(gegenkonto(b), bg, Element.ALIGN_LEFT));
        t.addCell(zelle(kuerze(b.getZahlungsart(), 12), bg, Element.ALIGN_LEFT));
        t.addCell(zelle(b.getMwstSatz() != null ? formatProzent(b.getMwstSatz()) : "–", bg, Element.ALIGN_RIGHT));
        BigDecimal mwst = mwstBetrag(b);
        t.addCell(zelle(mwst != null ? formatEuro(mwst) : "–", bg, Element.ALIGN_RIGHT));
        t.addCell(zelle(ausgang ? "" : formatEuro(brutto), bg, Element.ALIGN_RIGHT));
        t.addCell(zelle(ausgang ? formatEuro(brutto) : "", bg, Element.ALIGN_RIGHT));
        t.addCell(betragZelle(formatEuro(bestand), bg, false));
    }

    /** Erste Zeile: der Uebertrag aus dem Vormonat. */
    private void uebertrag(PdfPTable t, BigDecimal anfangsbestand) {
        PdfPCell uebertrag = new PdfPCell(new Phrase("Übertrag aus dem Vormonat",
                PdfBerichtBausteine.schrift(9, Font.ITALIC, PdfDesign.TEXT_GEDAEMPFT)));
        uebertrag.setColspan(10);
        uebertrag.setBackgroundColor(PdfDesign.SUMME_HINTERGRUND);
        uebertrag.setHorizontalAlignment(Element.ALIGN_RIGHT);
        uebertrag.setPadding(5f);
        uebertrag.setBorder(Rectangle.BOTTOM);
        uebertrag.setBorderColor(PdfDesign.RAHMEN);
        t.addCell(uebertrag);
        t.addCell(betragZelle(formatEuro(anfangsbestand), PdfDesign.SUMME_HINTERGRUND, true));
    }

    private static PdfPCell leerZeile() {
        PdfPCell leer = new PdfPCell(new Phrase("Keine Bargeldbewegungen in diesem Monat",
                PdfBerichtBausteine.schrift(9, Font.ITALIC, PdfDesign.TEXT_GEDAEMPFT)));
        leer.setColspan(11);
        leer.setHorizontalAlignment(Element.ALIGN_CENTER);
        leer.setPaddingTop(12f);
        leer.setPaddingBottom(12f);
        leer.setBorder(Rectangle.NO_BORDER);
        return leer;
    }

    /** Anfangsbestand, Summen und Endbestand -- ersetzt das frueher gezeigte T-Konto. */
    private void summenblock(Document doc, BigDecimal anfang, BigDecimal ein,
                             BigDecimal aus, BigDecimal ende) throws DocumentException {
        PdfPTable s = new PdfPTable(new float[]{ 3f, 1.4f });
        s.setWidthPercentage(45);
        s.setHorizontalAlignment(Element.ALIGN_RIGHT);
        s.setSpacingBefore(10f);

        s.addCell(summeLabel("Bestand am Monatsanfang"));
        s.addCell(summeWert(formatEuro(anfang) + " €", false));
        s.addCell(summeLabel("+ Einnahmen und Einlagen"));
        s.addCell(summeWert(formatEuro(ein) + " €", false));
        // Gedankenstrich statt Rechenzeichen U+2212: die PDF-Schriften sind CP1252-kodiert und
        // kennen "−" nicht, das Zeichen fiel bisher still weg.
        s.addCell(summeLabel("– Ausgaben und Entnahmen"));
        s.addCell(summeWert(formatEuro(aus) + " €", false));
        s.addCell(summeLabel("= Bestand am Monatsende"));
        s.addCell(summeWert(formatEuro(ende) + " €", true));

        doc.add(s);
    }

    /**
     * Belege, die nicht bar bezahlt wurden (Bank, Kreditkarte, Sonstiges),
     * gehoeren nicht ins Kassenbuch, liegen aber mit im Ordner. Ein Hinweis
     * erspart dem Steuerberater die Rueckfrage, warum die Belegnummern im
     * Journal Luecken haben.
     */
    private static void nichtBarHinweis(Document doc, List<Beleg> alle) throws DocumentException {
        List<Beleg> nichtBar = alle.stream()
                .filter(b -> b.getBelegKategorie() == null || !b.getBelegKategorie().istKassenBewegung())
                .toList();
        if (nichtBar.isEmpty()) return;

        BigDecimal summe = BigDecimal.ZERO;
        for (Beleg b : nichtBar) summe = summe.add(nullSafe(b.getBetragBrutto()));

        Font f = FontFactory.getFont(FontFactory.HELVETICA, 9, PdfDesign.TEXT_GEDAEMPFT);
        Paragraph p = new Paragraph(
                "Hinweis: " + nichtBar.size() + " Belege dieses Monats über zusammen "
                + formatEuro(summe) + " € wurden nicht bar bezahlt (Bank, Kreditkarte, Sonstiges). "
                + "Sie stehen deshalb nicht im Kassenbuch, liegen aber mit im Belegteil dieses Pakets. "
                + "Daher können im Journal Nummern fehlen.", f);
        p.setSpacingBefore(10f);
        doc.add(p);
    }

    private static Paragraph abschnitt(String text, float abstandVorher) {
        Paragraph section = new Paragraph(text,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, PdfDesign.TEXT_DUNKEL));
        section.setSpacingBefore(abstandVorher);
        section.setSpacingAfter(6f);
        return section;
    }

    private void kopfzeile(PdfPTable t, String... spalten) {
        for (String h : spalten) {
            t.addCell(PdfBerichtBausteine.kopfZelle(h, design, Element.ALIGN_LEFT));
        }
    }

    private static PdfPCell zelle(String text, Color bg, int alignment) {
        return mitUnterstrich(new PdfPCell(new Phrase(text == null ? "" : text,
                PdfBerichtBausteine.schrift(8, Font.NORMAL, PdfDesign.TEXT_ZELLE))), bg, alignment);
    }

    /** Bestand-Spalte: fett, damit man die Fortschreibung mit dem Auge verfolgen kann. */
    private static PdfPCell betragZelle(String text, Color bg, boolean hervorheben) {
        Font f = PdfBerichtBausteine.schrift(8, Font.BOLD,
                hervorheben ? PdfDesign.TEXT_DUNKEL : PdfDesign.TEXT_ZELLE);
        return mitUnterstrich(new PdfPCell(new Phrase(text, f)), bg, Element.ALIGN_RIGHT);
    }

    private static PdfPCell mitUnterstrich(PdfPCell c, Color bg, int alignment) {
        c.setBackgroundColor(bg);
        c.setHorizontalAlignment(alignment);
        c.setPadding(5f);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(PdfDesign.RAHMEN);
        c.setBorderWidth(0.5f);
        return c;
    }

    private static PdfPCell summeLabel(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text,
                PdfBerichtBausteine.schrift(9, Font.NORMAL, PdfDesign.TEXT_ZELLE)));
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(3f);
        c.setPaddingBottom(3f);
        return c;
    }

    /** Der Endbestand steht fett in der Firmenfarbe, die übrigen Summen schlicht. */
    private PdfPCell summeWert(String text, boolean hervorheben) {
        PdfPCell c = new PdfPCell(new Phrase(text, PdfBerichtBausteine.schrift(
                hervorheben ? 11 : 9,
                hervorheben ? Font.BOLD : Font.NORMAL,
                hervorheben ? design.akzent() : PdfDesign.TEXT_ZELLE)));
        c.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c.setBorder(hervorheben ? Rectangle.TOP : Rectangle.NO_BORDER);
        c.setBorderColor(PdfDesign.TEXT_DUNKEL);
        c.setPaddingTop(3f);
        c.setPaddingBottom(3f);
        return c;
    }
}
