package org.example.kalkulationsprogramm.service.zeitbericht;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.service.Urlaubskonto;
import org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine;
import org.example.kalkulationsprogramm.service.pdf.PdfDesign;
import org.example.kalkulationsprogramm.service.pdf.PdfFirmenDesign;
import org.example.kalkulationsprogramm.service.zeitbericht.MitarbeiterBerichtDaten.Tag;
import org.example.kalkulationsprogramm.util.PdfWriterFabrik;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import static org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine.kopf;
import static org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine.kopfZelle;
import static org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine.schrift;
import static org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine.zahl;
import static org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine.zelle;
import static org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine.zwischenueberschrift;

/**
 * Baut den Zeitbericht (Stundenzettel) für einen oder mehrere Mitarbeiter als ein PDF:
 * jeder Mitarbeiter beginnt auf einer neuen Seite. Tagesliste und Unterschriftszeile sind wählbar.
 */
@Service
@RequiredArgsConstructor
public class MitarbeiterBerichtPdfService {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TAG = DateTimeFormatter.ofPattern("dd.MM.");

    private final PdfFirmenDesign pdfFirmenDesign;
    private final Clock clock;

    public byte[] erzeuge(List<MitarbeiterBerichtDaten> berichte, boolean mitTagesliste, boolean mitUnterschrift) {
        PdfDesign design = pdfFirmenDesign.laden();
        Document document = new Document(PageSize.A4, 40, 40, 36, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriterFabrik.writer(document, out);
        writer.setPageEvent(PdfBerichtBausteine.fusszeile(design, LocalDate.now(clock)));
        document.open();
        try {
            for (int i = 0; i < berichte.size(); i++) {
                if (i > 0) {
                    document.newPage();
                }
                seite(document, berichte.get(i), design, mitTagesliste, mitUnterschrift);
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Zeitbericht konnte nicht als PDF erstellt werden.", e);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private static void seite(Document document, MitarbeiterBerichtDaten b, PdfDesign design, boolean mitTagesliste,
            boolean mitUnterschrift) throws DocumentException {
        kopf(document, design, "Zeitbericht " + b.name(), b.von().format(DATUM) + " bis " + b.bis().format(DATUM));
        document.add(uebersicht(b, design));
        document.add(zwischenueberschrift("Urlaub " + b.urlaub().jahr(), design));
        document.add(urlaub(b.urlaub(), design));
        if (mitTagesliste) {
            document.add(zwischenueberschrift("Tag für Tag", design));
            document.add(tagesliste(b.tage(), design));
        }
        document.add(PdfBerichtBausteine.hinweis("Alle Werte in Stunden, Urlaub in Tagen. Das Stundenkonto enthält "
                + "alle Plus- und Minusstunden seit Eintritt bis zum " + b.bis().format(DATUM)
                + " inklusive Korrekturen."));
        if (mitUnterschrift) {
            document.add(unterschrift());
        }
    }

    private static PdfPTable uebersicht(MitarbeiterBerichtDaten b, PdfDesign design) {
        var s = b.summen();
        String[] titel = {"Soll", "Ist", "Plus/Minus", "Urlaub", "Krank", "Fortbildung", "Feiertage",
            "Stundenkonto"};
        BigDecimal[] werte = {s.soll(), s.ist(), s.differenz(), s.urlaub(), s.krankheit(), s.fortbildung(),
            s.feiertage(), b.stundenkontoStand()};
        PdfPTable tabelle = new PdfPTable(titel.length);
        tabelle.setWidthPercentage(100);
        for (String t : titel) {
            tabelle.addCell(kopfZelle(t, design, Element.ALIGN_CENTER));
        }
        Font fett = schrift(10f, Font.BOLD, PdfDesign.TEXT_DUNKEL);
        for (BigDecimal w : werte) {
            tabelle.addCell(zelle(zahl(w), Color.WHITE, Element.ALIGN_CENTER, fett));
        }
        return tabelle;
    }

    private static PdfPTable urlaub(Urlaubskonto u, PdfDesign design) {
        String[] titel = {"Anspruch", "Übertrag Vorjahr", "Verfallen", "Genommen", "Geplant", "Rest"};
        BigDecimal[] werte = {u.jahresanspruch(), u.uebertrag(), u.verfallen(), u.genommen(), u.geplant(),
            u.verbleibend()};
        PdfPTable tabelle = new PdfPTable(titel.length);
        tabelle.setWidthPercentage(100);
        for (String t : titel) {
            tabelle.addCell(kopfZelle(t, design, Element.ALIGN_CENTER));
        }
        for (int i = 0; i < werte.length; i++) {
            Font schrift = schrift(9.5f, i == werte.length - 1 ? Font.BOLD : Font.NORMAL, PdfDesign.TEXT_DUNKEL);
            tabelle.addCell(zelle(zahl(werte[i]), Color.WHITE, Element.ALIGN_CENTER, schrift));
        }
        return tabelle;
    }

    private static PdfPTable tagesliste(List<Tag> tage, PdfDesign design) {
        PdfPTable tabelle = new PdfPTable(new float[] {1.4f, 1f, 1f, 1.6f, 2.2f, 1f, 1.1f});
        tabelle.setWidthPercentage(100);
        tabelle.setHeaderRows(1);
        for (String t : new String[] {"Datum", "Soll", "Arbeit", "Abwesenheit", "Feiertag", "Ist", "Plus/Minus"}) {
            tabelle.addCell(kopfZelle(t, design, t.equals("Datum") || t.equals("Feiertag") || t.equals("Abwesenheit")
                    ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT));
        }
        int rechts = Element.ALIGN_RIGHT;
        for (Tag t : tage) {
            boolean wochenende = t.datum().getDayOfWeek().getValue() >= 6;
            Color hg = wochenende ? design.akzentHell() : Color.WHITE;
            String wochentag = t.datum().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.GERMAN).replace(".", "");
            tabelle.addCell(zelle(wochentag + " " + t.datum().format(TAG), hg, Element.ALIGN_LEFT));
            tabelle.addCell(zelle(leerBeiNull(t.soll()), hg, rechts));
            tabelle.addCell(zelle(leerBeiNull(t.arbeit()), hg, rechts));
            tabelle.addCell(zelle(t.abwesenheit() == null ? "" : t.abwesenheit() + " " + zahl(t.abwesenheitStunden()),
                    hg, Element.ALIGN_LEFT));
            tabelle.addCell(zelle(t.feiertag() == null ? "" : t.feiertag(), hg, Element.ALIGN_LEFT));
            tabelle.addCell(zelle(leerBeiNull(t.ist()), hg, rechts));
            tabelle.addCell(zelle(t.differenz().signum() == 0 ? "" : zahl(t.differenz()), hg, rechts));
        }
        return tabelle;
    }

    private static String leerBeiNull(BigDecimal wert) {
        return wert == null || wert.signum() == 0 ? "" : zahl(wert);
    }

    private static PdfPTable unterschrift() {
        PdfPTable tabelle = new PdfPTable(new float[] {1f, 0.2f, 1f});
        tabelle.setWidthPercentage(100);
        tabelle.setSpacingBefore(36f);
        tabelle.addCell(linie("Datum, Unterschrift Mitarbeiter"));
        PdfPCell luecke = new PdfPCell();
        luecke.setBorder(Rectangle.NO_BORDER);
        tabelle.addCell(luecke);
        tabelle.addCell(linie("Datum, Unterschrift Betrieb"));
        return tabelle;
    }

    private static PdfPCell linie(String text) {
        PdfPCell zelle = new PdfPCell(new Paragraph(text, schrift(8.5f, Font.NORMAL, PdfDesign.TEXT_GEDAEMPFT)));
        zelle.setBorder(Rectangle.TOP);
        zelle.setBorderColor(PdfDesign.TEXT_GEDAEMPFT);
        zelle.setPaddingTop(4f);
        return zelle;
    }
}
