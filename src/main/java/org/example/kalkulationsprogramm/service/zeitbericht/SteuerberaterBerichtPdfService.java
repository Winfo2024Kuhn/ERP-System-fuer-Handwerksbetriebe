package org.example.kalkulationsprogramm.service.zeitbericht;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.dto.zeitbericht.SteuerberaterBerichtDto;
import org.example.kalkulationsprogramm.dto.zeitbericht.SteuerberaterBerichtDto.Zeile;
import org.example.kalkulationsprogramm.service.pdf.PdfDesign;
import org.example.kalkulationsprogramm.service.pdf.PdfFirmenDesign;
import org.example.kalkulationsprogramm.util.PdfWriterFabrik;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.PageSize;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine.fusszeile;
import static org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine.hinweis;
import static org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine.kopf;
import static org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine.kopfZelle;
import static org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine.zahl;
import static org.example.kalkulationsprogramm.service.pdf.PdfBerichtBausteine.zelle;

/**
 * Baut aus der Stundenaufstellung das PDF für den Steuerberater (Querformat, Firmenlogo,
 * Tabellenkopf in Firmenfarbe). Spalten: Nr., Name, Tagessoll, Arbeitsstunden, Urlaub, Feiertage,
 * Krankheit, Fortbildung, wählbar Stundenkonto und Resturlaub, dazu Monat (abgeschlossen/offen).
 * Bewusst ohne Gesamtstunden – keine Spalte „Soll“ und keine Summenzeile –, damit der
 * Steuerberater sie nicht mit den Arbeitsstunden verwechselt.
 */
@Service
@RequiredArgsConstructor
public class SteuerberaterBerichtPdfService {

    private final PdfFirmenDesign pdfFirmenDesign;
    private final Clock clock;

    private record Spalte(String titel, float breite, int ausrichtung, Function<Zeile, String> wert) {
    }

    public byte[] erzeuge(SteuerberaterBerichtDto bericht, boolean mitResturlaub, boolean mitStundenkonto) {
        PdfDesign design = pdfFirmenDesign.laden();
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 48);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriterFabrik.writer(document, out);
        writer.setPageEvent(fusszeile(design, LocalDate.now(clock)));
        document.open();
        try {
            kopf(document, design, "Stundenaufstellung " + monatsname(bericht) + " " + bericht.jahr(),
                    bericht.zeilen().size() + " Mitarbeiter");
            boolean ist = bericht.stundenart() == SteuerberaterBerichtDto.Stundenart.IST;
            document.add(hinweis("Alle Werte in Stunden" + (mitResturlaub ? ", Resturlaub in Tagen" : "") + ". "
                    + (ist ? "Arbeitsstunden (Ist) = tatsächlich gebuchte Arbeitszeit ohne Pausen."
                            : "Arbeitsstunden (Soll) = Sollstunden abzüglich Urlaub, Feiertage, Krankheit und Fortbildung.")));
            document.add(tabelle(bericht, spalten(mitResturlaub, mitStundenkonto, ist), design));
            if (!bericht.alleFestgeschrieben()) {
                document.add(hinweis("„offen“: Der Monat ist für diesen Mitarbeiter noch nicht abgeschlossen. "
                        + "Die Werte können sich noch ändern."));
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Stundenaufstellung konnte nicht als PDF erstellt werden.", e);
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private static List<Spalte> spalten(boolean mitResturlaub, boolean mitStundenkonto, boolean ist) {
        int rechts = Element.ALIGN_RIGHT;
        List<Spalte> spalten = new ArrayList<>(List.of(
                new Spalte("Name", 3.2f, Element.ALIGN_LEFT, Zeile::name),
                new Spalte("Tagessoll", 1.3f, rechts, z -> zahl(z.tagessoll())),
                new Spalte(ist ? "Arbeitsstunden (Ist)" : "Arbeitsstunden (Soll)", 2.4f, rechts,
                        z -> zahl(z.arbeitsstunden())),
                new Spalte("Urlaub", 1.2f, rechts, z -> zahl(z.urlaubStunden())),
                new Spalte("Feiertage", 1.3f, rechts, z -> zahl(z.feiertagStunden())),
                new Spalte("Krankheit", 1.3f, rechts, z -> zahl(z.krankheitStunden())),
                new Spalte("Fortbildung", 1.5f, rechts, z -> zahl(z.fortbildungStunden()))));
        if (mitStundenkonto) {
            spalten.add(new Spalte("Stundenkonto", 1.8f, rechts, z -> zahl(z.stundenkontoStand())));
        }
        if (mitResturlaub) {
            spalten.add(new Spalte("Resturlaub (Tage)", 2.1f, rechts, z -> zahl(z.resturlaubTage())));
        }
        spalten.add(new Spalte("Monat", 1.7f, Element.ALIGN_CENTER,
                z -> z.festgeschrieben() ? "abgeschlossen" : "offen"));
        return spalten;
    }

    private static PdfPTable tabelle(SteuerberaterBerichtDto bericht, List<Spalte> spalten, PdfDesign design) {
        float[] breiten = new float[spalten.size() + 1];
        breiten[0] = 0.6f;
        for (int i = 0; i < spalten.size(); i++) {
            breiten[i + 1] = spalten.get(i).breite();
        }
        PdfPTable tabelle = new PdfPTable(breiten);
        tabelle.setWidthPercentage(100);
        tabelle.setHeaderRows(1);
        tabelle.addCell(kopfZelle("Nr.", design, Element.ALIGN_CENTER));
        spalten.forEach(s -> tabelle.addCell(kopfZelle(s.titel(), design, s.ausrichtung())));

        for (int i = 0; i < bericht.zeilen().size(); i++) {
            Zeile zeile = bericht.zeilen().get(i);
            Color hintergrund = i % 2 == 1 ? design.akzentHell() : Color.WHITE;
            tabelle.addCell(zelle(String.valueOf(i + 1), hintergrund, Element.ALIGN_CENTER));
            spalten.forEach(s -> tabelle.addCell(zelle(s.wert().apply(zeile), hintergrund, s.ausrichtung())));
        }
        return tabelle;
    }

    private static String monatsname(SteuerberaterBerichtDto bericht) {
        return Month.of(bericht.monat()).getDisplayName(TextStyle.FULL, Locale.GERMAN);
    }
}
