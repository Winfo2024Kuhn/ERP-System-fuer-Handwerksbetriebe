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
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
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

/** Erzeugt übersichtliche Projektlisten für Nachkalkulation und Abrechnung. */
@Service
@RequiredArgsConstructor
public class ProjektListenPdfService {

    private static final Color HEADER_BG = new Color(220, 38, 38);
    private static final Color ROW_ALT = new Color(254, 242, 242);
    private static final Color BORDER = new Color(229, 231, 235);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.GERMANY);

    private final ProjektRepository projektRepository;
    private final AusgangsGeschaeftsDokumentRepository ausgangsGeschaeftsDokumentRepository;

    @Transactional(readOnly = true)
    public byte[] generatePdf(ProjektListenTyp typ) {
        List<Projekt> kandidaten = typ == ProjektListenTyp.IN_ARBEIT
                ? projektRepository.findByAbgeschlossenFalseOrderByAnlegedatumDesc()
                : projektRepository.findByBezahltFalseOrderByAnlegedatumDesc();
        // Altprojekte besitzen nur ProjektGeschaeftsdokumente. Für die Liste sollen
        // ausschließlich Projekte berücksichtigt werden, die im neuen
        // AusgangsGeschaeftsDokument-System geführt werden.
        Set<Long> projektIdsMitNeuemDokument = Set.copyOf(ausgangsGeschaeftsDokumentRepository.findDistinctProjektIds());
        List<Projekt> projekte = kandidaten.stream()
                .filter(projekt -> projektIdsMitNeuemDokument.contains(projekt.getId()))
                .toList();

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 36, 36, 42, 36);
            PdfWriter.getInstance(document, output);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(30, 41, 59));
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(100, 116, 139));
            document.add(new Paragraph(typ.titel(), titleFont));
            document.add(new Paragraph("Erstellt am " + LocalDate.now().format(DATE_FORMAT)
                    + " · " + projekte.size() + " Projekte", subtitleFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[] { 0.7f, 1.5f, 3.6f, 3.1f, 1.5f, 1.5f });
            table.setWidthPercentage(100);
            String[] headers = { "Art", "Auftragsnr.", "Bauvorhaben", "Kunde", "Angelegt", "Betrag" };
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Paragraph(header,
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
                cell.setBackgroundColor(HEADER_BG);
                cell.setPadding(7);
                cell.setBorder(Rectangle.NO_BORDER);
                table.addCell(cell);
            }

            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(55, 65, 81));
            BigDecimal summe = BigDecimal.ZERO;
            for (int i = 0; i < projekte.size(); i++) {
                Projekt projekt = projekte.get(i);
                Color background = i % 2 == 0 ? Color.WHITE : ROW_ALT;
                addCell(table, ermittleAuftragsart(projekt), cellFont, background, Element.ALIGN_CENTER);
                addCell(table, projekt.getAuftragsnummer(), cellFont, background, Element.ALIGN_LEFT);
                addCell(table, projekt.getBauvorhaben(), cellFont, background, Element.ALIGN_LEFT);
                addCell(table, projekt.getKunde(), cellFont, background, Element.ALIGN_LEFT);
                addCell(table, projekt.getAnlegedatum() == null ? "" : projekt.getAnlegedatum().format(DATE_FORMAT), cellFont, background, Element.ALIGN_LEFT);
                BigDecimal betrag = projekt.getBruttoPreis() == null ? BigDecimal.ZERO : projekt.getBruttoPreis();
                addCell(table, CURRENCY_FORMAT.format(betrag), cellFont, background, Element.ALIGN_RIGHT);
                summe = summe.add(betrag);
            }

            PdfPCell sumLabel = new PdfPCell(new Paragraph("Gesamtsumme", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
            sumLabel.setColspan(5);
            sumLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumLabel.setPadding(8);
            sumLabel.setBackgroundColor(new Color(241, 245, 249));
            table.addCell(sumLabel);
            PdfPCell sumValue = new PdfPCell(new Paragraph(CURRENCY_FORMAT.format(summe), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
            sumValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumValue.setPadding(8);
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
        cell.setPadding(6);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private String ermittleAuftragsart(Projekt projekt) {
        if (projekt.getKundenId() == null || projekt.getAnlegedatum() == null || projekt.getId() == null) {
            return "–";
        }
        return projektRepository.existsVorherigesProjektFuerKunde(
                projekt.getKundenId().getId(), projekt.getAnlegedatum(), projekt.getId()) ? "F" : "E";
    }

    public enum ProjektListenTyp {
        NICHT_ABGERECHNET("Nicht abgerechnete Projekte"),
        IN_ARBEIT("Projekte in Arbeit");

        private final String titel;

        ProjektListenTyp(String titel) {
            this.titel = titel;
        }

        public String titel() {
            return titel;
        }
    }
}
