package org.example.kalkulationsprogramm.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Erzeugt eine übersichtliche Liste aller Projekte, die noch nicht auf "Beendet" gesetzt wurden. */
@Service
@RequiredArgsConstructor
public class ProjektListenPdfService {

    private static final Color HEADER_BG = new Color(220, 38, 38);
    private static final Color ROW_ALT = new Color(254, 242, 242);
    private static final Color BORDER = new Color(229, 231, 235);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.GERMANY);

    private final ProjektRepository projektRepository;

    /**
     * Listet genau die Projekte auf, bei denen der Haken "Beendet" nicht gesetzt ist –
     * ohne weitere Filter, damit die Liste immer 1:1 zur Projektübersicht passt.
     */
    @Transactional(readOnly = true)
    public byte[] generatePdf() {
        List<Projekt> projekte = projektRepository.findByAbgeschlossenFalseOrderByAnlegedatumDesc();
        // Erst-/Folgeauftrag für alle Zeilen in einer Abfrage klären statt pro Zeile.
        Set<Long> folgeauftraege = projekte.isEmpty()
                ? Set.of()
                : Set.copyOf(projektRepository.findIdsMitVorherigemProjektFuerKunde(
                        projekte.stream().map(Projekt::getId).toList()));

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            // Hochformat: Die Liste wird meist ausgedruckt und abgeheftet – Querformat passt dort nicht.
            Document document = new Document(PageSize.A4, 36, 36, 42, 36);
            PdfWriter.getInstance(document, output);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(30, 41, 59));
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(100, 116, 139));
            document.add(new Paragraph("Projekte in Arbeit", titleFont));
            document.add(new Paragraph("Erstellt am " + LocalDate.now().format(DATE_FORMAT)
                    + " · " + projekte.size() + " Projekte", subtitleFont));
            document.add(new Paragraph(" "));

            // Spaltenbreiten sind auf das schmalere Hochformat abgestimmt: Auftragsnummer,
            // Datum und Betrag brauchen genug Platz, damit sie nicht umbrechen – der Rest
            // geht an Kunde und Bauvorhaben, die ohnehin mehrzeilig laufen dürfen.
            PdfPTable table = new PdfPTable(new float[] { 0.7f, 1.75f, 2.7f, 3.05f, 1.5f, 1.8f });
            table.setWidthPercentage(100);
            // Auf Folgeseiten die Kopfzeile wiederholen – im Hochformat passen weniger Zeilen auf eine Seite.
            table.setHeaderRows(1);
            String[] headers = { "Art", "Auftragsnr.", "Kunde", "Bauvorhaben", "Angelegt", "Betrag" };
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Paragraph(header,
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
                cell.setBackgroundColor(HEADER_BG);
                cell.setPadding(6);
                cell.setBorder(Rectangle.NO_BORDER);
                table.addCell(cell);
            }

            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(55, 65, 81));
            BigDecimal summe = BigDecimal.ZERO;
            for (int i = 0; i < projekte.size(); i++) {
                Projekt projekt = projekte.get(i);
                Color background = i % 2 == 0 ? Color.WHITE : ROW_ALT;
                addCell(table, ermittleAuftragsart(projekt, folgeauftraege), cellFont, background, Element.ALIGN_CENTER);
                addCell(table, projekt.getAuftragsnummer(), cellFont, background, Element.ALIGN_LEFT);
                addCell(table, projekt.getKunde(), cellFont, background, Element.ALIGN_LEFT);
                addCell(table, projekt.getBauvorhaben(), cellFont, background, Element.ALIGN_LEFT);
                addCell(table, projekt.getAnlegedatum() == null ? "" : projekt.getAnlegedatum().format(DATE_FORMAT), cellFont, background, Element.ALIGN_LEFT);
                BigDecimal betrag = projekt.getBruttoPreis() == null ? BigDecimal.ZERO : projekt.getBruttoPreis();
                addCell(table, CURRENCY_FORMAT.format(betrag), cellFont, background, Element.ALIGN_RIGHT);
                summe = summe.add(betrag);
            }

            PdfPCell sumLabel = new PdfPCell(new Paragraph("Gesamtsumme", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
            sumLabel.setColspan(5);
            sumLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumLabel.setPadding(6);
            sumLabel.setBackgroundColor(new Color(241, 245, 249));
            table.addCell(sumLabel);
            PdfPCell sumValue = new PdfPCell(new Paragraph(CURRENCY_FORMAT.format(summe), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
            sumValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumValue.setPadding(6);
            sumValue.setBackgroundColor(new Color(241, 245, 249));
            table.addCell(sumValue);

            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Projektliste konnte nicht als PDF erstellt werden.", exception);
        }
    }

    private void addCell(PdfPTable table, String value, Font font, Color background, int alignment) {
        PdfPCell cell = new PdfPCell(new Paragraph(value == null ? "" : value, font));
        cell.setBackgroundColor(background);
        cell.setBorderColor(BORDER);
        cell.setBorderWidth(0.5f);
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    /** "F" = Folgeauftrag (Kunde hatte schon ein Projekt), "E" = Erstauftrag. */
    private String ermittleAuftragsart(Projekt projekt, Set<Long> folgeauftraege) {
        if (projekt.getKundenId() == null || projekt.getAnlegedatum() == null || projekt.getId() == null) {
            return "–";
        }
        return folgeauftraege.contains(projekt.getId()) ? "F" : "E";
    }
}
