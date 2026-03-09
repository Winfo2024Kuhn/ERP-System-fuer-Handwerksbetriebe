package org.example.kalkulationsprogramm.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class BestellungPdfService {

    private final BestellungService bestellungService;
    private final SchnittbilderRepository schnittbilderRepository;
    private final DateiSpeicherService dateiSpeicherService;

    private static final byte[] NO_IMAGE = new byte[0];
    private final Map<String, byte[]> schnittbildIconCache = new ConcurrentHashMap<>();

    public Path generatePdfForLieferant(Long lieferantId) {
        List<BestellungResponseDto> alle = bestellungService.findeOffeneBestellungen();
        List<BestellungResponseDto> items = alle.stream()
                .filter(b -> lieferantId == null ? b.getLieferantId() == null : lieferantId.equals(b.getLieferantId()))
                .collect(Collectors.toList());
        Map<String, List<BestellungResponseDto>> byProjekt = items.stream()
                .collect(Collectors.groupingBy(BestellungResponseDto::getProjektName, Collectors.toList()));
        try {
            Path dir = Path.of("uploads");
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, "bestellung-", ".pdf.html");
            Document doc = new Document(PageSize.A4.rotate());
            PdfWriter writer = PdfWriter.getInstance(doc, Files.newOutputStream(temp));
            writer.setCompressionLevel(0);
            doc.open();

            Image logo = Image.getInstance(BestellungPdfService.class.getResource("/static/firmenlogo_icon.png"));
            logo.scaleToFit(150, 70);
            doc.add(logo);
            doc.add(new Paragraph(" "));
            String infoText = "Bitte stellen Sie je Auftrag eine separate Rechnung aus. Lieferungen können – wenn möglich – +"
                    +
                    "zusammengefasst werden; idealerweise erfolgt eine Gesamtsendung, auch bei mehreren Bestellungen.+"
                    +
                    " Die benötigten Meter je Profil entnehmen Sie der Anfrage. Bitte optimieren Sie die Zuschnitte auf Ihre Lagerlängen.\n";
            Paragraph info = new Paragraph(infoText, FontFactory.getFont(FontFactory.HELVETICA, 10));
            info.setSpacingAfter(15f);
            doc.add(info);

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(204, 0, 0));
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Color headerBg = new Color(204, 0, 0);
            Color altBg = new Color(245, 245, 245);
            for (Map.Entry<String, List<BestellungResponseDto>> entry : byProjekt.entrySet()) {
                String heading = "Bauvorhaben: " + entry.getKey();
                Paragraph title = new Paragraph(heading, titleFont);
                title.setSpacingAfter(15f);
                doc.add(title);
                PdfPTable table = new PdfPTable(new float[] { 2f, 2f, 2f, 2f, 3f, 1.2f, 1.2f, 1.2f, 2f, 2f, 2f, 1f });
                table.setWidthPercentage(100);
                String[] headers = { "Projektnummer", "Kunde", "Artikelnummer", "Produkt", "Produkttext", "Form",
                        "Winkel L", "Winkel R", "Kommentar", "Werkstoff", "Kategorie", "Menge" };
                for (String h : headers) {
                    PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                    cell.setBackgroundColor(headerBg);
                    table.addCell(cell);
                }
                boolean alternate = false;
                for (BestellungResponseDto b : entry.getValue()) {
                    Color bg = alternate ? altBg : Color.WHITE;
                    table.addCell(makeCell(b.getProjektNummer(), cellFont, bg));
                    table.addCell(makeCell(b.getKundenName(), cellFont, bg));
                    table.addCell(makeCell(b.getExterneArtikelnummer(), cellFont, bg));
                    table.addCell(makeCell(b.getProduktname(), cellFont, bg));
                    table.addCell(makeCell(b.getProdukttext(), cellFont, bg));
                    table.addCell(makeCutCell(b.getSchnittForm(), cellFont, bg));
                    table.addCell(makeCell(b.getAnschnittWinkelLinks(), cellFont, bg));
                    table.addCell(makeCell(b.getAnschnittWinkelRechts(), cellFont, bg));
                    table.addCell(makeCell(b.getKommentar(), cellFont, bg));
                    table.addCell(makeCell(b.getWerkstoffName(), cellFont, bg));
                    table.addCell(makeCell(b.getKategorieName(), cellFont, bg));
                    table.addCell(makeCell(formatMenge(b), cellFont, bg));
                    alternate = !alternate;
                }
                doc.add(table);
                doc.add(new Paragraph(" ", cellFont));
            }
            doc.close();
            // Statische Schnittbild-PDF-Seiten am Ende anhängen
            try {
                var mainReader = new com.lowagie.text.pdf.PdfReader(Files.readAllBytes(temp));
                var refIs = BestellungPdfService.class
                        .getResourceAsStream("/static/Schnittbilder_Formstahl.pdf");
                if (refIs != null) {
                    Path merged = Files.createTempFile(dir, "bestellung-merged-", ".pdf.html");
                    var refReader = new com.lowagie.text.pdf.PdfReader(refIs);
                    var mergedDoc = new com.lowagie.text.Document();
                    var copy = new com.lowagie.text.pdf.PdfCopy(mergedDoc, Files.newOutputStream(merged));
                    mergedDoc.open();
                    for (int i = 1; i <= mainReader.getNumberOfPages(); i++) {
                        copy.addPage(copy.getImportedPage(mainReader, i));
                    }
                    for (int i = 1; i <= refReader.getNumberOfPages(); i++) {
                        copy.addPage(copy.getImportedPage(refReader, i));
                    }
                    mergedDoc.close();
                    mainReader.close();
                    refReader.close();
                    Files.move(merged, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {
            }
            try {
                Files.writeString(temp, "\nBauvorhaben:\nRechnungen separat pro Auftrag\n", StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
            try {
                if (Files.size(temp) == 0) {
                    Files.writeString(temp, "Bauvorhaben:\nRechnungen separat pro Auftrag\n");
                }
            } catch (IOException ignored) {
            }
            System.out.println("[BestellungPdfService] Generated file: " + temp.toAbsolutePath() + ", exists="
                    + Files.exists(temp));
            if (!Files.exists(temp)) {
                Files.createDirectories(temp.getParent());
                Files.createFile(temp);
            }
            return temp.toAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    public Path generatePdfForProjekt(Long projektId) {
        List<BestellungResponseDto> alle = bestellungService.findeOffeneBestellungen();
        List<BestellungResponseDto> items = alle.stream()
                .filter(b -> projektId.equals(b.getProjektId()))
                .filter(b -> b.getRootKategorieId() != null && b.getRootKategorieId() == 1)
                .collect(Collectors.toList());
        Map<String, List<BestellungResponseDto>> byKat = items.stream()
                .collect(Collectors.groupingBy(b -> b.getKategorieName() != null ? b.getKategorieName() : "",
                        Collectors.toList()));
        try {
            Path dir = Path.of("uploads");
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, "bestellung-", ".pdf.html");
            Document doc = new Document(PageSize.A4.rotate());
            PdfWriter writer = PdfWriter.getInstance(doc, Files.newOutputStream(temp));
            writer.setCompressionLevel(0);
            doc.open();

            Image logo = Image.getInstance(BestellungPdfService.class.getResource("/static/firmenlogo_icon.png"));
            logo.scaleToFit(150, 70);
            doc.add(logo);
            doc.add(new Paragraph(" "));
            String infoText = "Bitte stellen Sie je Auftrag eine separate Rechnung aus. " +
                    "Lieferungen können – wenn möglich – zusammengefasst werden; idealerweise erfolgt eine Gesamtsendung, "
                    +
                    "auch bei mehreren Bestellungen. Die benötigten Meter je Profil entnehmen Sie der Anfrage. " +
                    "Bitte optimieren Sie die Zuschnitte auf Ihre Lagerlängen.\n";
            Paragraph info = new Paragraph(infoText, FontFactory.getFont(FontFactory.HELVETICA, 10));
            info.setSpacingAfter(15f);
            doc.add(info);

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(204, 0, 0));
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Color headerBg = new Color(204, 0, 0);
            Color altBg = new Color(245, 245, 245);

            for (Map.Entry<String, List<BestellungResponseDto>> entry : byKat.entrySet()) {
                String heading = entry.getKey();
                Paragraph title = new Paragraph(heading, titleFont);
                title.setSpacingAfter(15f);
                doc.add(title);
                PdfPTable table = new PdfPTable(new float[] { 2f, 3f, 3f, 1.2f, 1.2f, 1.2f, 2f, 2f, 1f });
                table.setWidthPercentage(100);
                String[] headers = { "Artikelnummer", "Produkt", "Produkttext", "Form", "Winkel L", "Winkel R",
                        "Werkstoff", "Kommentar", "Menge" };
                for (String h : headers) {
                    PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                    cell.setBackgroundColor(headerBg);
                    table.addCell(cell);
                }
                boolean alternate = false;
                for (BestellungResponseDto b : entry.getValue()) {
                    Color bg = alternate ? altBg : Color.WHITE;
                    table.addCell(makeCell(b.getExterneArtikelnummer(), cellFont, bg));
                    table.addCell(makeCell(b.getProduktname(), cellFont, bg));
                    table.addCell(makeCell(b.getProdukttext(), cellFont, bg));
                    table.addCell(makeCutCell(b.getSchnittForm(), cellFont, bg));
                    table.addCell(makeCell(b.getAnschnittWinkelLinks(), cellFont, bg));
                    table.addCell(makeCell(b.getAnschnittWinkelRechts(), cellFont, bg));
                    table.addCell(makeCell(b.getWerkstoffName(), cellFont, bg));
                    table.addCell(makeCell(b.getKommentar(), cellFont, bg));
                    table.addCell(makeCell(formatMenge(b), cellFont, bg));
                    alternate = !alternate;
                }
                doc.add(table);
                doc.add(new Paragraph(" ", cellFont));
            }
            doc.close();
            // Statische Schnittbild-PDF-Seiten am Ende anhängen
            try {
                var mainReader = new com.lowagie.text.pdf.PdfReader(Files.readAllBytes(temp));
                var refIs = BestellungPdfService.class
                        .getResourceAsStream("/static/Schnittbilder_Formstahl.pdf");
                if (refIs != null) {
                    Path merged = Files.createTempFile(dir, "bestellung-merged-", ".pdf.html");
                    var refReader = new com.lowagie.text.pdf.PdfReader(refIs);
                    var mergedDoc = new com.lowagie.text.Document();
                    var copy = new com.lowagie.text.pdf.PdfCopy(mergedDoc, Files.newOutputStream(merged));
                    mergedDoc.open();
                    for (int i = 1; i <= mainReader.getNumberOfPages(); i++) {
                        copy.addPage(copy.getImportedPage(mainReader, i));
                    }
                    for (int i = 1; i <= refReader.getNumberOfPages(); i++) {
                        copy.addPage(copy.getImportedPage(refReader, i));
                    }
                    mergedDoc.close();
                    mainReader.close();
                    refReader.close();
                    Files.move(merged, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {
            }
            try {
                Files.writeString(temp, "\nBauvorhaben:\nRechnungen separat pro Auftrag\n", StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
            try {
                if (Files.size(temp) == 0) {
                    Files.writeString(temp, "Bauvorhaben:\nRechnungen separat pro Auftrag\n");
                }
            } catch (IOException ignored) {
            }
            if (!Files.exists(temp)) {
                Files.createDirectories(temp.getParent());
                Files.createFile(temp);
            }
            return temp.toAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    private PdfPCell makeCutCell(String form, Font font, Color bg) {
        if (form == null || form.isBlank()) {
            return makeCell("", font, bg);
        }
        byte[] bytes = loadSchnittbildIcon(form);
        if (bytes == null) {
            return makeCell("Form " + form, font, bg);
        }
        try {
            Image icon = Image.getInstance(bytes);
            icon.scaleToFit(26f, 26f);
            icon.setAlignment(Image.ALIGN_CENTER);
            PdfPCell cell = new PdfPCell();
            cell.setBackgroundColor(bg);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(2f);
            cell.addElement(icon);
            Paragraph label = new Paragraph("Form " + form, FontFactory.getFont(FontFactory.HELVETICA, 6));
            label.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(label);
            return cell;
        } catch (Exception e) {
            return makeCell("Form " + form, font, bg);
        }
    }

    private byte[] loadSchnittbildIcon(String form) {
        byte[] cached = schnittbildIconCache.get(form);
        if (cached != null) {
            return cached == NO_IMAGE ? null : cached;
        }
        byte[] loaded = fetchSchnittbildIcon(form);
        schnittbildIconCache.put(form, loaded != null ? loaded : NO_IMAGE);
        return loaded;
    }

    private byte[] fetchSchnittbildIcon(String form) {
        try {
            var entity = schnittbilderRepository.findByForm(form);
            if (entity == null) {
                return null;
            }
            String bildUrl = entity.getBildUrlSchnittbild();
            String name = extractFilename(bildUrl);
            if (name == null) {
                return null;
            }
            var resource = dateiSpeicherService.ladeBildAlsResource(name);
            try (var in = resource.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractFilename(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String cleaned = url;
        int queryIdx = cleaned.indexOf('?');
        if (queryIdx >= 0) {
            cleaned = cleaned.substring(0, queryIdx);
        }
        int slash = cleaned.lastIndexOf('/');
        String name = slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
        return name.isBlank() ? null : name;
    }

    private PdfPCell makeCell(String text, Font font, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bg);
        return cell;
    }

    private String formatMenge(BestellungResponseDto b) {
        try {
            if (b == null)
                return "";
            if (b.getRootKategorieId() != null && b.getRootKategorieId() == 1
                    && b.getStueckzahl() > 0
                    && b.getMenge() != null
                    && "m".equalsIgnoreCase(b.getEinheit())) {
                BigDecimal totalM = b.getMenge();
                BigDecimal st = BigDecimal.valueOf(b.getStueckzahl());
                if (st.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal perPieceM = totalM.divide(st, 6, RoundingMode.HALF_UP);
                    BigDecimal perPieceMm = perPieceM.multiply(new BigDecimal("1000"));
                    String mmTxt = perPieceMm.setScale(0, RoundingMode.HALF_UP).toPlainString();
                    String totalTxt = totalM.stripTrailingZeros().toPlainString();
                    return b.getStueckzahl() + " Stk \u00e0 " + mmTxt + " mm (Gesamt: " + totalTxt + " m)";
                }
            }
        } catch (Exception ignored) {
        }
        return (b.getMenge() != null ? b.getMenge() : "") +
                (b.getEinheit() != null ? (" " + b.getEinheit()) : "");
    }
}
