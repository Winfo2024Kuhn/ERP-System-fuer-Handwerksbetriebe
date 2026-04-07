package org.example.kalkulationsprogramm.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Produktkategorie;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjektAuswertungPdfService {

    private final ZeitbuchungRepository zeitbuchungRepository;

    /**
     * Erzeugt den Regiebericht-PDF.
     *
     * @param sortField Spalte: "mitarbeiter" | "datum" | "dauer" | "produktkategorie" | "arbeitsgang"
     * @param sortDir   "asc" | "desc"
     * @param groupBy   Gruppierung: "arbeitsgang" | "qualifikation" | "mitarbeiter" | "datum"
     */
    public Path generatePdf(Long projektId, LocalDate von, LocalDate bis,
                            String sortField, String sortDir, String groupBy) {

        List<Zeitbuchung> alleBuchungen = zeitbuchungRepository.findByProjektId(projektId).stream()
                .filter(b -> {
                    if (von == null && bis == null) return true;
                    LocalDate d = b.getStartZeit() != null ? b.getStartZeit().toLocalDate() : null;
                    if (d == null) return false;
                    if (von != null && d.isBefore(von)) return false;
                    if (bis != null && d.isAfter(bis)) return false;
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

        // Dynamische Gruppierung
        String effectiveGroupBy = groupBy == null ? "arbeitsgang" : groupBy;
        Map<String, List<Zeitbuchung>> gruppen = alleBuchungen.stream()
                .collect(Collectors.groupingBy(b -> resolveGroupKey(b, effectiveGroupBy)));

        // Gruppen sortiert nach Schlüssel
        Map<String, List<Zeitbuchung>> sortierteGruppen = new LinkedHashMap<>();
        gruppen.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sortierteGruppen.put(e.getKey(), e.getValue()));

        try {
            Path dir = Path.of("uploads");
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, "regiebericht-", ".pdf");
            Document doc = new Document(PageSize.A4.rotate());
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

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(50, 50, 50));
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 12, new Color(100, 100, 100));

            doc.add(new Paragraph("REGIEBERICHT / ZEITNACHWEIS", titleFont));
            doc.add(new Paragraph("Gruppiert nach: " + groupByLabel(effectiveGroupBy), subTitleFont));
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
                        + (von != null ? von.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "Anfang")
                        + " – "
                        + (bis != null ? bis.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) : "Ende");
                doc.add(new Paragraph(zeitraum, subTitleFont));
                doc.add(new Paragraph(" "));
            }

            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(55, 65, 81));
            Font sumFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(55, 65, 81));
            Color headerBg = new Color(220, 38, 38);
            Color rowAlt = new Color(254, 242, 242);
            Color borderColor = new Color(229, 231, 235);
            Color sumBg = new Color(241, 245, 249);

            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yy");
            DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

            BigDecimal totalHoursAll = BigDecimal.ZERO;
            Map<String, Long> globalQualifikationMinutes = new HashMap<>();

            for (Map.Entry<String, List<Zeitbuchung>> entry : sortierteGruppen.entrySet()) {
                String groupLabel = entry.getKey();
                List<Zeitbuchung> bookings = new ArrayList<>(entry.getValue());

                // Sortierung
                Comparator<Zeitbuchung> comparator = buildComparator(sortField);
                if ("desc".equalsIgnoreCase(sortDir)) comparator = comparator.reversed();
                bookings.sort(comparator);

                Font activityFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(30, 41, 59));
                Paragraph groupP = new Paragraph(groupLabel, activityFont);
                groupP.setSpacingBefore(8f);
                doc.add(groupP);
                doc.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 6)));

                // 8 Spalten: Mitarbeiter | Qualifikation | Arbeitsgang | Produktkategorie | Datum | Start | Ende | Std.
                PdfPTable table = new PdfPTable(new float[]{ 2.2f, 1.8f, 2f, 3f, 1.6f, 1.2f, 1.2f, 1.1f });
                table.setWidthPercentage(100);
                table.setSpacingBefore(4f);

                String[] headers = { "Mitarbeiter", "Qualifikation", "Arbeitsgang", "Produktkategorie",
                        "Datum", "Start", "Ende", "Std." };
                for (String h : headers) {
                    PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                    cell.setBackgroundColor(headerBg);
                    cell.setPaddingTop(8f); cell.setPaddingBottom(8f);
                    cell.setPaddingLeft(6f); cell.setPaddingRight(6f);
                    cell.setBorder(Rectangle.NO_BORDER);
                    table.addCell(cell);
                }

                long groupMinutes = 0;
                boolean alt = false;
                Map<String, Long> groupQualifikationMinutes = new HashMap<>();

                for (Zeitbuchung b : bookings) {
                    Color bg = alt ? rowAlt : Color.WHITE;

                    String mitarbeiter = b.getMitarbeiter() != null
                            ? b.getMitarbeiter().getVorname() + " " + b.getMitarbeiter().getNachname()
                            : "Unbekannt";

                    String qualifikation = "–";
                    if (b.getMitarbeiter() != null && b.getMitarbeiter().getQualifikation() != null) {
                        qualifikation = b.getMitarbeiter().getQualifikation().getBezeichnung();
                    }

                    String arbeitsgang = b.getArbeitsgangStundensatz() != null
                            && b.getArbeitsgangStundensatz().getArbeitsgang() != null
                            ? b.getArbeitsgangStundensatz().getArbeitsgang().getBeschreibung()
                            : (b.getArbeitsgang() != null ? b.getArbeitsgang().getBeschreibung() : "–");

                    String kategoriePfad = "–";
                    if (b.getProjektProduktkategorie() != null
                            && b.getProjektProduktkategorie().getProduktkategorie() != null) {
                        kategoriePfad = buildKategoriePfad(b.getProjektProduktkategorie().getProduktkategorie());
                    }

                    String datum = b.getStartZeit() != null ? b.getStartZeit().format(dateFmt) : "";
                    String start = b.getStartZeit() != null ? b.getStartZeit().format(timeFmt) : "";
                    String ende = b.getEndeZeit() != null ? b.getEndeZeit().format(timeFmt) : "";

                    long mins = 0;
                    if (b.getStartZeit() != null && b.getEndeZeit() != null) {
                        mins = java.time.Duration.between(b.getStartZeit(), b.getEndeZeit()).toMinutes();
                    }
                    groupMinutes += mins;
                    groupQualifikationMinutes.merge(qualifikation, mins, (a, b2) -> a + b2);
                    globalQualifikationMinutes.merge(qualifikation, mins, (a, b2) -> a + b2);

                    String dauer = "%.2f".formatted((double) mins / 60.0);

                    table.addCell(makeModernCell(mitarbeiter, cellFont, bg, borderColor));
                    table.addCell(makeModernCell(qualifikation, cellFont, bg, borderColor));
                    table.addCell(makeModernCell(arbeitsgang, cellFont, bg, borderColor));
                    table.addCell(makeModernCell(kategoriePfad, cellFont, bg, borderColor));
                    table.addCell(makeModernCell(datum, cellFont, bg, borderColor));
                    table.addCell(makeModernCell(start, cellFont, bg, borderColor));
                    table.addCell(makeModernCell(ende, cellFont, bg, borderColor));
                    table.addCell(makeModernCell(dauer, cellFont, bg, borderColor));

                    alt = !alt;
                }

                BigDecimal groupHours = new BigDecimal(groupMinutes).divide(new BigDecimal(60), 2,
                        java.math.RoundingMode.HALF_UP);
                totalHoursAll = totalHoursAll.add(groupHours);

                // Qualifikations-Summenzeilen
                for (Map.Entry<String, Long> qe : groupQualifikationMinutes.entrySet()) {
                    BigDecimal qh = new BigDecimal(qe.getValue()).divide(new BigDecimal(60), 2,
                            java.math.RoundingMode.HALF_UP);

                    PdfPCell qLabel = new PdfPCell(new Phrase("Summe " + qe.getKey() + "stunden", cellFont));
                    qLabel.setColspan(7);
                    qLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    qLabel.setBackgroundColor(Color.WHITE);
                    qLabel.setPaddingTop(6f); qLabel.setPaddingBottom(6f); qLabel.setPaddingRight(12f);
                    qLabel.setBorder(Rectangle.BOTTOM);
                    qLabel.setBorderColor(borderColor); qLabel.setBorderWidth(0.5f);
                    table.addCell(qLabel);

                    PdfPCell qVal = new PdfPCell(new Phrase(qh + " h", cellFont));
                    qVal.setBackgroundColor(Color.WHITE);
                    qVal.setPaddingTop(6f); qVal.setPaddingBottom(6f); qVal.setPaddingLeft(8f);
                    qVal.setBorder(Rectangle.BOTTOM);
                    qVal.setBorderColor(borderColor); qVal.setBorderWidth(0.5f);
                    table.addCell(qVal);
                }

                // Gruppengesamtzeile
                PdfPCell sumLabel = new PdfPCell(new Phrase("Summe " + groupLabel, sumFont));
                sumLabel.setColspan(7);
                sumLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
                sumLabel.setBackgroundColor(sumBg);
                sumLabel.setPaddingTop(10f); sumLabel.setPaddingBottom(10f); sumLabel.setPaddingRight(12f);
                sumLabel.setBorder(Rectangle.NO_BORDER);
                table.addCell(sumLabel);

                PdfPCell sumVal = new PdfPCell(new Phrase(groupHours + " h", sumFont));
                sumVal.setBackgroundColor(sumBg);
                sumVal.setPaddingTop(10f); sumVal.setPaddingBottom(10f); sumVal.setPaddingLeft(8f);
                sumVal.setBorder(Rectangle.NO_BORDER);
                table.addCell(sumVal);

                doc.add(table);
                doc.add(new Paragraph(" "));
            }

            // Gesamttabelle
            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidths(new float[]{ 3f, 1f });
            totalTable.setWidthPercentage(50);
            totalTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalTable.setSpacingBefore(16f);

            Font totalLabelFont = FontFactory.getFont(FontFactory.HELVETICA, 11, new Color(55, 65, 81));
            Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(220, 38, 38));
            Color totalRowBg = new Color(254, 242, 242);

            for (Map.Entry<String, Long> qe : globalQualifikationMinutes.entrySet()) {
                BigDecimal qh = new BigDecimal(qe.getValue()).divide(new BigDecimal(60), 2,
                        java.math.RoundingMode.HALF_UP);

                PdfPCell lc = new PdfPCell(new Phrase("Summe " + qe.getKey() + "stunden:", totalLabelFont));
                lc.setBackgroundColor(totalRowBg);
                lc.setHorizontalAlignment(Element.ALIGN_RIGHT);
                lc.setPaddingTop(8f); lc.setPaddingBottom(8f);
                lc.setPaddingRight(12f); lc.setPaddingLeft(16f);
                lc.setBorder(Rectangle.NO_BORDER);
                totalTable.addCell(lc);

                PdfPCell vc = new PdfPCell(new Phrase(qh + " h", totalLabelFont));
                vc.setBackgroundColor(totalRowBg);
                vc.setHorizontalAlignment(Element.ALIGN_RIGHT);
                vc.setPaddingTop(8f); vc.setPaddingBottom(8f); vc.setPaddingRight(16f);
                vc.setBorder(Rectangle.NO_BORDER);
                totalTable.addCell(vc);
            }

            PdfPCell totalLabelCell = new PdfPCell(new Phrase("Gesamtstunden:", totalFont));
            totalLabelCell.setBackgroundColor(totalRowBg);
            totalLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalLabelCell.setPaddingTop(12f); totalLabelCell.setPaddingBottom(12f);
            totalLabelCell.setPaddingRight(12f); totalLabelCell.setPaddingLeft(16f);
            totalLabelCell.setBorder(Rectangle.NO_BORDER);
            totalTable.addCell(totalLabelCell);

            PdfPCell totalValCell = new PdfPCell(new Phrase(totalHoursAll + " h", totalFont));
            totalValCell.setBackgroundColor(totalRowBg);
            totalValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalValCell.setPaddingTop(12f); totalValCell.setPaddingBottom(12f);
            totalValCell.setPaddingRight(16f);
            totalValCell.setBorder(Rectangle.NO_BORDER);
            totalTable.addCell(totalValCell);

            doc.add(totalTable);

            doc.add(new Paragraph(" "));
            doc.add(new Paragraph(" "));
            Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(148, 163, 184));
            Paragraph footer = new Paragraph(
                    "Dieses Dokument wurde maschinell erstellt und ist ohne Unterschrift gültig.", footerFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);

            doc.close();
            return temp;

        } catch (Exception e) {
            throw new RuntimeException("Fehler beim Generieren des Regieberichts", e);
        }
    }

    String resolveGroupKey(Zeitbuchung b, String groupBy) {
        return switch (groupBy) {
            case "qualifikation" -> b.getMitarbeiter() != null && b.getMitarbeiter().getQualifikation() != null
                    ? b.getMitarbeiter().getQualifikation().getBezeichnung()
                    : "Keine Qualifikation";
            case "mitarbeiter" -> b.getMitarbeiter() != null
                    ? b.getMitarbeiter().getVorname() + " " + b.getMitarbeiter().getNachname()
                    : "Unbekannter Mitarbeiter";
            case "datum" -> b.getStartZeit() != null
                    ? b.getStartZeit().toLocalDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    : "Kein Datum";
            default -> // arbeitsgang
                b.getArbeitsgangStundensatz() != null && b.getArbeitsgangStundensatz().getArbeitsgang() != null
                        ? b.getArbeitsgangStundensatz().getArbeitsgang().getBeschreibung()
                        : (b.getArbeitsgang() != null ? b.getArbeitsgang().getBeschreibung() : "Sonstiges");
        };
    }

    String groupByLabel(String groupBy) {
        return switch (groupBy) {
            case "qualifikation" -> "Qualifikation";
            case "mitarbeiter" -> "Mitarbeiter";
            case "datum" -> "Datum";
            default -> "Arbeitsgang";
        };
    }

    String buildKategoriePfad(Produktkategorie kategorie) {
        if (kategorie == null) return "–";
        Deque<String> parts = new ArrayDeque<>();
        Produktkategorie current = kategorie;
        while (current != null) {
            parts.addFirst(current.getBezeichnung());
            current = current.getUebergeordneteKategorie();
        }
        return String.join("/", parts);
    }

    Comparator<Zeitbuchung> buildComparator(String sortField) {
        return switch (sortField == null ? "datum" : sortField) {
            case "mitarbeiter" -> Comparator.comparing(
                    b -> b.getMitarbeiter() != null
                            ? b.getMitarbeiter().getNachname() + " " + b.getMitarbeiter().getVorname() : "",
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "arbeitsgang" -> Comparator.comparing(
                    b -> b.getArbeitsgangStundensatz() != null
                            && b.getArbeitsgangStundensatz().getArbeitsgang() != null
                            ? b.getArbeitsgangStundensatz().getArbeitsgang().getBeschreibung()
                            : (b.getArbeitsgang() != null ? b.getArbeitsgang().getBeschreibung() : ""),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "dauer" -> Comparator.comparing(b -> {
                if (b.getStartZeit() == null || b.getEndeZeit() == null) return 0L;
                return java.time.Duration.between(b.getStartZeit(), b.getEndeZeit()).toMinutes();
            }, Comparator.nullsLast(Comparator.naturalOrder()));
            case "produktkategorie" -> Comparator.comparing(
                    b -> b.getProjektProduktkategorie() != null
                            && b.getProjektProduktkategorie().getProduktkategorie() != null
                            ? buildKategoriePfad(b.getProjektProduktkategorie().getProduktkategorie()) : "",
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> // datum
                Comparator.comparing(b -> b.getStartZeit(),
                        Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private PdfPCell makeModernCell(String text, Font font, Color bg, Color borderColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPaddingTop(7f); cell.setPaddingBottom(7f);
        cell.setPaddingLeft(6f); cell.setPaddingRight(6f);
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
