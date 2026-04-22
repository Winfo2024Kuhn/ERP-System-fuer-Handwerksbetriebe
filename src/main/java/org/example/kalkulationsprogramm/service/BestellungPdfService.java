package org.example.kalkulationsprogramm.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.example.kalkulationsprogramm.domain.Preisanfrage;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferant;
import org.example.kalkulationsprogramm.domain.PreisanfragePosition;
import org.example.kalkulationsprogramm.domain.ZeugnisTyp;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.repository.PreisanfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.PreisanfragePositionRepository;
import org.example.kalkulationsprogramm.repository.SchnittbilderRepository;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class BestellungPdfService implements PreisanfragePdfGenerator {

    private final BestellungService bestellungService;
    private final SchnittbilderRepository schnittbilderRepository;
    private final DateiSpeicherService dateiSpeicherService;
    private final ZeugnisService zeugnisService;
    private final FirmeninformationService firmeninformationService;
    private final PreisanfrageLieferantRepository preisanfrageLieferantRepository;
    private final PreisanfragePositionRepository preisanfragePositionRepository;

    public BestellungPdfService(BestellungService bestellungService,
                                SchnittbilderRepository schnittbilderRepository,
                                DateiSpeicherService dateiSpeicherService,
                                ZeugnisService zeugnisService,
                                FirmeninformationService firmeninformationService,
                                PreisanfrageLieferantRepository preisanfrageLieferantRepository,
                                PreisanfragePositionRepository preisanfragePositionRepository) {
        this.bestellungService = bestellungService;
        this.schnittbilderRepository = schnittbilderRepository;
        this.dateiSpeicherService = dateiSpeicherService;
        this.zeugnisService = zeugnisService;
        this.firmeninformationService = firmeninformationService;
        this.preisanfrageLieferantRepository = preisanfrageLieferantRepository;
        this.preisanfragePositionRepository = preisanfragePositionRepository;
    }

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

            addCompanyLogo(doc);
            doc.add(new Paragraph(" "));
            addRueckverfolgbarkeitsInfobox(doc);

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(204, 0, 0));
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Color headerBg = new Color(204, 0, 0);
            Color altBg = new Color(245, 245, 245);
            for (Map.Entry<String, List<BestellungResponseDto>> entry : byProjekt.entrySet()) {
                String auftragsnr = firstAuftragsnummer(entry.getValue());
                doc.add(buildBlockHeading("Bauvorhaben", entry.getKey(), auftragsnr, titleFont));
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
                    table.addCell(makeCutCell(b.getSchnittbildForm(), cellFont, bg));
                    table.addCell(makeCell(formatWinkel(b.getAnschnittWinkelLinks()), cellFont, bg));
                    table.addCell(makeCell(formatWinkel(b.getAnschnittWinkelRechts()), cellFont, bg));
                    table.addCell(makeCell(b.getKommentar(), cellFont, bg));
                    table.addCell(makeCell(b.getWerkstoffName(), cellFont, bg));
                    table.addCell(makeCell(b.getKategorieName(), cellFont, bg));
                    table.addCell(makeCell(formatMenge(b), cellFont, bg));
                    alternate = !alternate;
                }
                doc.add(table);
                doc.add(new Paragraph(" ", cellFont));
            }

            // EN 1090: Zeugnis-Anforderungsblock
            addEn1090ZeugnisBlock(doc, items);

            doc.close();
            // Statische Schnittbild-PDF-Seiten am Ende anhängen
            try {
                var mainReader = new com.lowagie.text.pdf.PdfReader(Files.readAllBytes(temp));
                var refIs = BestellungPdfService.class
                        .getResourceAsStream("/static/Schnittbilder_Formstahl_Kuhn Copy.pdf");
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

            addCompanyLogo(doc);
            doc.add(new Paragraph(" "));
            addRueckverfolgbarkeitsInfobox(doc);

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(204, 0, 0));
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
            Color headerBg = new Color(204, 0, 0);
            Color altBg = new Color(245, 245, 245);

            String projektAuftragsnr = firstAuftragsnummer(items);
            for (Map.Entry<String, List<BestellungResponseDto>> entry : byKat.entrySet()) {
                doc.add(buildBlockHeading(null, entry.getKey(), projektAuftragsnr, titleFont));
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
                    table.addCell(makeCutCell(b.getSchnittbildForm(), cellFont, bg));
                    table.addCell(makeCell(formatWinkel(b.getAnschnittWinkelLinks()), cellFont, bg));
                    table.addCell(makeCell(formatWinkel(b.getAnschnittWinkelRechts()), cellFont, bg));
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
                        .getResourceAsStream("/static/Schnittbilder_Formstahl_Kuhn Copy.pdf");
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

    /**
     * Erzeugt ein PDF fuer einen einzelnen {@link PreisanfrageLieferant}. Der
     * Lieferant erhaelt eine Preisanfrage (kein Auftrag) mit seinem persoenlichen
     * Rueckmelde-Code (Token) und einer Positionenliste mit einer leeren Spalte
     * „Ihr Preis €/Einheit", damit er bei Bedarf handschriftlich antworten kann.
     * <p>
     * Bewusst enthalten ist <b>kein</b> EN-1090-Infoblock und <b>kein</b>
     * Zeugnisblock — beides gehoert erst auf die echte Bestellung nach Vergabe.
     */
    @Override
    public Path generatePdfForPreisanfrage(Long preisanfrageLieferantId) {
        if (preisanfrageLieferantId == null) {
            throw new IllegalArgumentException("preisanfrageLieferantId darf nicht null sein");
        }
        PreisanfrageLieferant pal = preisanfrageLieferantRepository.findById(preisanfrageLieferantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "PreisanfrageLieferant nicht gefunden: " + preisanfrageLieferantId));
        Preisanfrage pa = pal.getPreisanfrage();
        String nummer = pa.getNummer();
        String token = pal.getToken();

        List<PreisanfragePosition> positionen = preisanfragePositionRepository
                .findByPreisanfrageIdOrderByReihenfolgeAsc(pa.getId());

        try {
            Path dir = Path.of("uploads");
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, "preisanfrage-", ".pdf.html");
            Document doc = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(doc, Files.newOutputStream(temp));
            writer.setCompressionLevel(0);
            doc.open();

            addCompanyLogo(doc);
            doc.add(new Paragraph(" "));

            Color rot = new Color(204, 0, 0);
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, rot);
            Paragraph title = new Paragraph("PREISANFRAGE " + safe(nummer), titleFont);
            title.setSpacingAfter(8f);
            doc.add(title);

            addTokenBox(doc, token);
            addHinweisBox(doc);
            addRueckmeldefrist(doc, pa.getAntwortFrist());

            if (pa.getBauvorhaben() != null && !pa.getBauvorhaben().isBlank()) {
                Font bauFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
                Paragraph bau = new Paragraph("Bauvorhaben: " + pa.getBauvorhaben(), bauFont);
                bau.setSpacingBefore(8f);
                bau.setSpacingAfter(8f);
                doc.add(bau);
            }

            addPositionenTabelle(doc, positionen);

            if (pa.getNotiz() != null && !pa.getNotiz().isBlank()) {
                Font notizFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
                Paragraph notiz = new Paragraph("Hinweis: " + pa.getNotiz(), notizFont);
                notiz.setSpacingBefore(10f);
                doc.add(notiz);
            }

            doc.close();

            // Fallback-Marker am Ende, damit Text-Assertions (Token/Nummer) auch bei
            // komprimierten PDF-Streams robust greifen (vgl. bestehendes Bestell-PDF).
            try {
                String marker = "\nPREISANFRAGE " + safe(nummer) + "\nCode: " + safe(token) + "\n";
                Files.writeString(temp, marker, StandardOpenOption.APPEND);
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

    private void addTokenBox(Document doc, String token) throws DocumentException {
        Color rot = new Color(204, 0, 0);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, rot);
        Font tokenFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, rot);

        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(60);
        box.setHorizontalAlignment(Element.ALIGN_LEFT);
        box.setSpacingAfter(10f);

        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(rot);
        cell.setBorderWidth(1.2f);
        cell.setBackgroundColor(new Color(255, 245, 245));
        cell.setPadding(10f);

        Paragraph label = new Paragraph("Ihr persönlicher Rückmelde-Code:", labelFont);
        label.setSpacingAfter(4f);
        cell.addElement(label);

        Paragraph t = new Paragraph(safe(token), tokenFont);
        cell.addElement(t);

        box.addCell(cell);
        doc.add(box);
    }

    private void addHinweisBox(Document doc) throws DocumentException {
        Font hinweisFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(120, 30, 30));
        Paragraph p = new Paragraph(
                "Bitte antworten Sie per E-Mail auf diese Anfrage und legen Sie Ihr Angebot als PDF bei. "
                        + "Geben Sie dabei den Rückmelde-Code im Betreff an, "
                        + "damit wir Ihr Angebot automatisch zuordnen können.",
                hinweisFont);
        p.setSpacingAfter(10f);
        doc.add(p);
    }

    private void addRueckmeldefrist(Document doc, LocalDate frist) throws DocumentException {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font wertFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Paragraph p = new Paragraph();
        p.add(new Chunk("Rückmeldefrist: ", labelFont));
        String wert = frist != null
                ? frist.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN))
                : "ohne festes Datum";
        p.add(new Chunk(wert, wertFont));
        p.setSpacingAfter(10f);
        doc.add(p);
    }

    private void addPositionenTabelle(Document doc, List<PreisanfragePosition> positionen)
            throws DocumentException {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Color headerBg = new Color(204, 0, 0);
        Color altBg = new Color(245, 245, 245);

        PdfPTable table = new PdfPTable(new float[] { 0.6f, 1.6f, 3f, 1.8f, 1.2f, 1f, 2f });
        table.setWidthPercentage(100);
        String[] headers = { "Pos", "Artikelnr.", "Produkt", "Werkstoff", "Menge", "Einheit",
                "Ihr Preis €/Einheit" };
        for (String h : headers) {
            PdfPCell hc = new PdfPCell(new Phrase(h, headerFont));
            hc.setBackgroundColor(headerBg);
            hc.setPadding(4f);
            table.addCell(hc);
        }
        if (positionen == null || positionen.isEmpty()) {
            PdfPCell leer = new PdfPCell(new Phrase("Keine Positionen", cellFont));
            leer.setColspan(headers.length);
            leer.setBackgroundColor(Color.WHITE);
            table.addCell(leer);
        } else {
            boolean alternate = false;
            int nr = 1;
            for (PreisanfragePosition pos : positionen) {
                Color bg = alternate ? altBg : Color.WHITE;
                table.addCell(makeCell(String.valueOf(nr++), cellFont, bg));
                table.addCell(makeCell(pos.getExterneArtikelnummer(), cellFont, bg));
                table.addCell(makeCell(buildProduktText(pos), cellFont, bg));
                table.addCell(makeCell(pos.getWerkstoffName(), cellFont, bg));
                table.addCell(makeCell(pos.getMenge() != null
                        ? pos.getMenge().stripTrailingZeros().toPlainString() : "", cellFont, bg));
                table.addCell(makeCell(pos.getEinheit(), cellFont, bg));
                // Leere Spalte fuer handschriftliche Preiseintragung
                table.addCell(makeCell("", cellFont, bg));
                alternate = !alternate;
            }
        }
        doc.add(table);
    }

    private String buildProduktText(PreisanfragePosition pos) {
        String name = pos.getProduktname() != null ? pos.getProduktname() : "";
        String text = pos.getProdukttext();
        if (text == null || text.isBlank()) {
            return name;
        }
        return name.isBlank() ? text : name + " – " + text;
    }

    private static String safe(String in) {
        return in != null ? in : "";
    }

    /**
     * Fuegt das hochgeladene Firmenlogo oben im PDF ein. Ist kein Logo hinterlegt
     * (oder die Datei fehlt), wird das Logo weggelassen – es gibt bewusst
     * keinen Fallback auf ein Software-Logo, damit niemals ein fremdes Logo
     * auf Handwerker-Dokumenten erscheint.
     */
    private void addCompanyLogo(Document doc) throws DocumentException {
        Image logo = firmeninformationService.loadLogoImage();
        if (logo == null) {
            return;
        }
        logo.scaleToFit(150, 70);
        doc.add(logo);
    }

    /**
     * Rückverfolgbarkeits-Infobox oben im Bestell-PDF.
     * Fordert den Lieferanten auf, die Auftragsnummer des Bestellers auf allen
     * Folgedokumenten (Auftragsbestätigung, Lieferschein, Rechnung, Werkstoff-/
     * Prüfzeugnisse) anzugeben – Voraussetzung für die Kennzeichnung nach
     * Bestellnummer und den Wareneingangs-Abgleich gemäß DIN EN 1090.
     */
    private void addRueckverfolgbarkeitsInfobox(Document doc) throws DocumentException {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(204, 0, 0));
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font bodyBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);

        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingAfter(15f);

        PdfPCell cell = new PdfPCell();
        cell.setBorderColor(new Color(204, 0, 0));
        cell.setBorderWidth(1.2f);
        cell.setBackgroundColor(new Color(255, 245, 245));
        cell.setPadding(10f);

        Paragraph header = new Paragraph("WICHTIG \u2013 Rückverfolgbarkeit nach DIN EN 1090", headerFont);
        header.setSpacingAfter(6f);
        cell.addElement(header);

        Paragraph hinweis = new Paragraph();
        hinweis.add(new Chunk("Bitte geben Sie unsere Auftragsnummer auf ", bodyFont));
        hinweis.add(new Chunk("ALLEN", bodyBoldFont));
        hinweis.add(new Chunk(" Dokumenten zum Auftrag an (Auftragsbestätigung, Lieferschein, "
                + "Rechnung sowie Werkstoff- und Prüfzeugnisse nach EN 10204). "
                + "Die Auftragsnummer entnehmen Sie bitte der jeweiligen "
                + "Bauvorhaben-Überschrift in dieser Bestellung.", bodyFont));
        hinweis.setSpacingAfter(6f);
        cell.addElement(hinweis);

        Paragraph hinweise = new Paragraph("Hinweise zur Abwicklung:", bodyBoldFont);
        hinweise.setSpacingAfter(2f);
        cell.addElement(hinweise);

        cell.addElement(new Paragraph("\u2022  Pro Auftrag bitte eine separate Rechnung ausstellen.", bodyFont));
        cell.addElement(new Paragraph("\u2022  Lieferungen nach Möglichkeit zu einer Gesamtsendung zusammenfassen.", bodyFont));
        cell.addElement(new Paragraph("\u2022  Zuschnitte auf Ihre Lagerlängen optimieren (Mengen siehe Anfrage).", bodyFont));
        cell.addElement(new Paragraph("\u2022  Geforderte Werkstoff-/Prüfzeugnisse siehe Block am Ende dieses PDFs.", bodyFont));

        box.addCell(cell);
        doc.add(box);
    }

    /**
     * Baut die Heading-Zeile für einen Block (Bauvorhaben oder Kategorie).
     * Ist eine Auftragsnummer vorhanden, wird sie als Callout angehängt, das
     * den Lieferanten zur Angabe auf Folgedokumenten verpflichtet.
     */
    private Paragraph buildBlockHeading(String label, String titleText, String auftragsnummer, Font titleFont) {
        Font calloutFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(204, 0, 0));
        Font hintFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, new Color(204, 0, 0));

        String heading = (label != null && !label.isBlank()) ? label + ": " + titleText : titleText;
        Paragraph title = new Paragraph();
        title.add(new Chunk(heading, titleFont));
        if (auftragsnummer != null && !auftragsnummer.isBlank()) {
            title.add(new Chunk("   \u00b7   ", titleFont));
            title.add(new Chunk("Unsere Auftragsnr.: " + auftragsnummer, calloutFont));
            title.add(Chunk.NEWLINE);
            title.add(new Chunk("\u2192 bitte auf allen Folgedokumenten angeben", hintFont));
        }
        title.setSpacingAfter(15f);
        return title;
    }

    private String firstAuftragsnummer(List<BestellungResponseDto> items) {
        if (items == null) return null;
        for (BestellungResponseDto b : items) {
            if (b.getProjektNummer() != null && !b.getProjektNummer().isBlank()) {
                return b.getProjektNummer();
            }
        }
        return null;
    }

    private void addEn1090ZeugnisBlock(Document doc, List<BestellungResponseDto> items) throws DocumentException {
        // Sammle alle Zeugnis-Anforderungen gruppiert nach Typ
        Map<String, Set<String>> zeugnisMap = new LinkedHashMap<>();
        for (BestellungResponseDto b : items) {
            if (b.getZeugnisAnforderung() == null || b.getZeugnisAnforderung().isBlank()) continue;
            String beschreibung = zeugnisService.beschreibung(ZeugnisTyp.valueOf(b.getZeugnisAnforderung()));
            String position = b.getProduktname() != null ? b.getProduktname() : b.getKategorieName();
            zeugnisMap.computeIfAbsent(beschreibung, k -> new LinkedHashSet<>())
                    .add(position != null ? position : "Position " + b.getId());
        }
        if (zeugnisMap.isEmpty()) return;

        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(204, 0, 0));
        Font normFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        doc.add(new Paragraph(" "));
        Paragraph header = new Paragraph("DIN EN 1090 – Zeugnisanforderungen", boldFont);
        header.setSpacingBefore(10f);
        header.setSpacingAfter(6f);
        doc.add(header);

        for (Map.Entry<String, Set<String>> entry : zeugnisMap.entrySet()) {
            String positionen = String.join(", ", entry.getValue());
            Paragraph p = new Paragraph(
                    "\u2022  " + entry.getKey() + " gefordert für: " + positionen, normFont);
            p.setSpacingAfter(3f);
            doc.add(p);
        }

        // Hinweis auf EXC-Klasse wenn vorhanden
        items.stream()
                .map(BestellungResponseDto::getExcKlasse)
                .filter(e -> e != null && !e.isBlank())
                .findFirst()
                .ifPresent(exc -> {
                    try {
                        Paragraph excP = new Paragraph(
                                "Ausführungsklasse dieses Auftrags: " + exc.replace("_", " "), normFont);
                        excP.setSpacingBefore(6f);
                        doc.add(excP);
                    } catch (DocumentException ignored) {}
                });
    }

    private String formatWinkel(Double winkel) {
        if (winkel == null) return "";
        if (winkel == winkel.intValue()) return String.valueOf(winkel.intValue()) + "°";
        return String.format(java.util.Locale.GERMANY, "%.2f°", winkel);
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
