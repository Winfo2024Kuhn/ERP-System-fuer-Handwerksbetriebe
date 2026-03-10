package org.example.kalkulationsprogramm.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjektAuswertungPdfService {

    private final ZeitbuchungRepository zeitbuchungRepository;

    public Path generatePdf(Long projektId, LocalDate von, LocalDate bis) {
        // Alle Buchungen laden und filtern
        List<Zeitbuchung> alleBuchungen = zeitbuchungRepository.findByProjektId(projektId).stream()
                .filter(b -> {
                    if (von == null && bis == null)
                        return true;
                    LocalDate buchungsDatum = b.getStartZeit() != null ? b.getStartZeit().toLocalDate() : null;
                    if (buchungsDatum == null)
                        return false;
                    if (von != null && buchungsDatum.isBefore(von))
                        return false;
                    if (bis != null && buchungsDatum.isAfter(bis))
                        return false;
                    return true;
                })
                .sorted(Comparator.comparing(Zeitbuchung::getStartZeit))
                .collect(Collectors.toList());

        if (alleBuchungen.isEmpty()) {
            throw new RuntimeException("Keine Buchungen für diesen Zeitraum gefunden.");
        }

        Zeitbuchung reference = alleBuchungen.getFirst();
        String bauvorhaben = reference.getProjekt().getBauvorhaben();
        String kunde = reference.getProjekt().getKunde();
        String auftrag = reference.getProjekt().getAuftragsnummer();

        // Gruppieren
        Map<String, List<Zeitbuchung>> nachArbeitsgang = alleBuchungen.stream()
                .collect(Collectors.groupingBy(b -> b.getArbeitsgangStundensatz() != null
                        && b.getArbeitsgangStundensatz().getArbeitsgang() != null
                                ? b.getArbeitsgangStundensatz().getArbeitsgang().getBeschreibung()
                                : (b.getArbeitsgang() != null ? b.getArbeitsgang().getBeschreibung() : "Sonstiges")));

        try {
            Path dir = Path.of("uploads");
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, "regiebericht-", ".pdf");
            Document doc = new Document(PageSize.A4);
            PdfWriter.getInstance(doc, Files.newOutputStream(temp));
            doc.open();

            // Logo
            try {
                Image logo = Image.getInstance(getClass().getResource("/static/firmenlogo.png"));
                logo.scaleToFit(150, 70);
                doc.add(logo);
            } catch (Exception ignored) {
            }
            doc.add(new Paragraph(" "));

            // Header Info
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(50, 50, 50));
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 12, new Color(100, 100, 100));

            doc.add(new Paragraph("REGIEBERICHT / ZEITNACHWEIS", titleFont));
            doc.add(new Paragraph(" ", subTitleFont));

            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.addCell(noBorderCell("Bauvorhaben: " + bauvorhaben, subTitleFont));
            headerTable.addCell(noBorderCell("Kunde: " + kunde, subTitleFont));
            headerTable.addCell(noBorderCell("Auftrag: " + (auftrag != null ? auftrag : "-"), subTitleFont));
            headerTable.addCell(noBorderCell(
                    "Datum: " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), subTitleFont));
            doc.add(headerTable);
            doc.add(new Paragraph(" "));

            if (von != null || bis != null) {
                String zeitraum = "Zeitraum: "
                        + (von != null ? von.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "Anfang") +
                        " - " + (bis != null ? bis.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "Ende");
                doc.add(new Paragraph(zeitraum, subTitleFont));
                doc.add(new Paragraph(" "));
            }

            // Tables per Activity - Modern styling
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(55, 65, 81)); // slate-700
            Font sumFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(55, 65, 81));
            Color headerBg = new Color(220, 38, 38); // Rose-600
            Color rowAlt = new Color(254, 242, 242); // Rose-50 - subtle alternating
            Color borderColor = new Color(229, 231, 235); // slate-200
            Color sumBg = new Color(241, 245, 249); // slate-100

            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yy");
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

            BigDecimal totalHoursAll = BigDecimal.ZERO;
            // Global tracking of hours per qualification
            Map<String, Long> globalQualifikationMinutes = new HashMap<>();

            for (Map.Entry<String, List<Zeitbuchung>> entry : nachArbeitsgang.entrySet()) {
                String activity = entry.getKey();
                List<Zeitbuchung> bookings = entry.getValue();

                // Activity Section Header - modern pill-style
                Font activityFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(30, 41, 59)); // slate-800
                Paragraph activityP = new Paragraph(activity, activityFont);
                activityP.setSpacingBefore(8f);
                doc.add(activityP);
                doc.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 6)));

                PdfPTable table = new PdfPTable(new float[] { 3f, 2.5f, 2f, 2f, 2f, 1.5f });
                table.setWidthPercentage(100);
                table.setSpacingBefore(4f);

                // Modern header - no vertical borders, generous padding
                String[] headers = { "Mitarbeiter", "Qualifikation", "Datum", "Start", "Ende", "Std." };
                for (int i = 0; i < headers.length; i++) {
                    PdfPCell cell = new PdfPCell(new Phrase(headers[i], headerFont));
                    cell.setBackgroundColor(headerBg);
                    cell.setPaddingTop(8f);
                    cell.setPaddingBottom(8f);
                    cell.setPaddingLeft(8f);
                    cell.setPaddingRight(8f);
                    cell.setBorder(Rectangle.NO_BORDER);
                    table.addCell(cell);
                }

                long activityMinutes = 0;
                boolean alt = false;
                // Track hours per qualification for this activity
                Map<String, Long> activityQualifikationMinutes = new HashMap<>();

                for (Zeitbuchung b : bookings) {
                    Color bg = alt ? rowAlt : Color.WHITE;

                    String mitarbeiter = b.getMitarbeiter() != null
                            ? b.getMitarbeiter().getVorname() + " " + b.getMitarbeiter().getNachname()
                            : "Unbekannt";
                    
                    // Get qualification
                    String qualifikation = "Unbekannt";
                    if (b.getMitarbeiter() != null && b.getMitarbeiter().getQualifikation() != null) {
                        qualifikation = b.getMitarbeiter().getQualifikation().getBezeichnung();
                    }
                    
                    String datum = b.getStartZeit() != null ? b.getStartZeit().format(dateFmt) : "";
                    String start = b.getStartZeit() != null ? b.getStartZeit().format(timeFmt) : "";
                    String ende = b.getEndeZeit() != null ? b.getEndeZeit().format(timeFmt) : "";

                    long mins = 0;
                    if (b.getStartZeit() != null && b.getEndeZeit() != null) {
                        mins = java.time.Duration.between(b.getStartZeit(), b.getEndeZeit()).toMinutes();
                    }
                    activityMinutes += mins;
                    
                    // Track minutes per qualification (activity-level)
                    activityQualifikationMinutes.merge(qualifikation, mins, Long::sum);
                    // Track minutes per qualification (global)
                    globalQualifikationMinutes.merge(qualifikation, mins, Long::sum);

                    String dauer = "%.2f".formatted((double) mins / 60.0);

                    table.addCell(makeModernCell(mitarbeiter, cellFont, bg, borderColor));
                    table.addCell(makeModernCell(qualifikation, cellFont, bg, borderColor));
                    table.addCell(makeModernCell(datum, cellFont, bg, borderColor));
                    table.addCell(makeModernCell(start, cellFont, bg, borderColor));
                    table.addCell(makeModernCell(ende, cellFont, bg, borderColor));
                    table.addCell(makeModernCell(dauer, cellFont, bg, borderColor));

                    alt = !alt;
                }

                // Modern Sum row - full width background, right-aligned
                BigDecimal activityHours = new BigDecimal(activityMinutes).divide(new BigDecimal(60), 2,
                        java.math.RoundingMode.HALF_UP);
                totalHoursAll = totalHoursAll.add(activityHours);

                // First add qualification sums for this activity
                for (Map.Entry<String, Long> qEntry : activityQualifikationMinutes.entrySet()) {
                    BigDecimal qHours = new BigDecimal(qEntry.getValue()).divide(new BigDecimal(60), 2,
                            java.math.RoundingMode.HALF_UP);
                    
                    PdfPCell qLabel = new PdfPCell(new Phrase("Summe " + qEntry.getKey() + "stunden", cellFont));
                    qLabel.setColspan(5);
                    qLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    qLabel.setBackgroundColor(Color.WHITE);
                    qLabel.setPaddingTop(6f);
                    qLabel.setPaddingBottom(6f);
                    qLabel.setPaddingRight(12f);
                    qLabel.setBorder(Rectangle.BOTTOM);
                    qLabel.setBorderColor(borderColor);
                    qLabel.setBorderWidth(0.5f);
                    table.addCell(qLabel);

                    PdfPCell qVal = new PdfPCell(new Phrase(qHours.toString() + " h", cellFont));
                    qVal.setBackgroundColor(Color.WHITE);
                    qVal.setPaddingTop(6f);
                    qVal.setPaddingBottom(6f);
                    qVal.setPaddingLeft(8f);
                    qVal.setBorder(Rectangle.BOTTOM);
                    qVal.setBorderColor(borderColor);
                    qVal.setBorderWidth(0.5f);
                    table.addCell(qVal);
                }

                // Then add total sum row for this activity
                PdfPCell sumLabel = new PdfPCell(new Phrase("Summe " + activity, sumFont));
                sumLabel.setColspan(5);
                sumLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
                sumLabel.setBackgroundColor(sumBg);
                sumLabel.setPaddingTop(10f);
                sumLabel.setPaddingBottom(10f);
                sumLabel.setPaddingRight(12f);
                sumLabel.setBorder(Rectangle.NO_BORDER);
                table.addCell(sumLabel);

                PdfPCell sumVal = new PdfPCell(new Phrase(activityHours.toString() + " h", sumFont));
                sumVal.setBackgroundColor(sumBg);
                sumVal.setPaddingTop(10f);
                sumVal.setPaddingBottom(10f);
                sumVal.setPaddingLeft(8f);
                sumVal.setBorder(Rectangle.NO_BORDER);
                table.addCell(sumVal);

                doc.add(table);
                doc.add(new Paragraph(" "));
            }

            // Modern Total Sum - Card style with qualification breakdown
            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidths(new float[] { 3f, 1f });
            totalTable.setWidthPercentage(50);
            totalTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalTable.setSpacingBefore(16f);

            Font totalLabelFont = FontFactory.getFont(FontFactory.HELVETICA, 11, new Color(55, 65, 81)); // slate-700
            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(220, 38, 38)); // Rose-600
            Color totalRowBg = new Color(254, 242, 242); // Rose-50

            // First add qualification breakdown
            for (Map.Entry<String, Long> qEntry : globalQualifikationMinutes.entrySet()) {
                BigDecimal qHours = new BigDecimal(qEntry.getValue()).divide(new BigDecimal(60), 2,
                        java.math.RoundingMode.HALF_UP);
                
                PdfPCell qLabelCell = new PdfPCell(new Phrase("Summe " + qEntry.getKey() + "stunden:", totalLabelFont));
                qLabelCell.setBackgroundColor(totalRowBg);
                qLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                qLabelCell.setPaddingTop(8f);
                qLabelCell.setPaddingBottom(8f);
                qLabelCell.setPaddingRight(12f);
                qLabelCell.setPaddingLeft(16f);
                qLabelCell.setBorder(Rectangle.NO_BORDER);
                totalTable.addCell(qLabelCell);

                PdfPCell qValCell = new PdfPCell(new Phrase(qHours.toString() + " h", totalLabelFont));
                qValCell.setBackgroundColor(totalRowBg);
                qValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                qValCell.setPaddingTop(8f);
                qValCell.setPaddingBottom(8f);
                qValCell.setPaddingRight(16f);
                qValCell.setBorder(Rectangle.NO_BORDER);
                totalTable.addCell(qValCell);
            }

            // Then add total row
            PdfPCell totalLabelCell = new PdfPCell(new Phrase("Gesamtstunden:", totalFont));
            totalLabelCell.setBackgroundColor(totalRowBg);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setPaddingTop(12f);
            totalLabelCell.setPaddingBottom(12f);
            totalLabelCell.setPaddingRight(12f);
            totalLabelCell.setPaddingLeft(16f);
            totalLabelCell.setBorder(Rectangle.NO_BORDER);
            totalTable.addCell(totalLabelCell);

            PdfPCell totalValCell = new PdfPCell(new Phrase(totalHoursAll.toString() + " h", totalFont));
            totalValCell.setBackgroundColor(totalRowBg);
            totalValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalValCell.setPaddingTop(12f);
            totalValCell.setPaddingBottom(12f);
            totalValCell.setPaddingRight(16f);
            totalValCell.setBorder(Rectangle.NO_BORDER);
            totalTable.addCell(totalValCell);

            doc.add(totalTable);

            // Modern Footer
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));

            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(148, 163, 184)); // slate-400
            Paragraph footer = new Paragraph(
                    "Dieses Dokument wurde maschinell erstellt und ist ohne Unterschrift gültig.",
                    footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return temp;

        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Generieren des Regieberichts", e);
        }
    }

    private PdfPCell makeCell(String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(4f);
        return cell;
    }

    /**
     * Creates a modern styled cell with only bottom border for a clean, minimal look
     */
    private PdfPCell makeModernCell(String text, Font font, Color bg, Color borderColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPaddingTop(8f);
        cell.setPaddingBottom(8f);
        cell.setPaddingLeft(8f);
        cell.setPaddingRight(8f);
        // Only bottom border for clean separator lines
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(borderColor);
        cell.setBorderWidth(0.5f);
        return cell;
    }

    private PdfPCell noBorderCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(4f);
        return cell;
    }
}
