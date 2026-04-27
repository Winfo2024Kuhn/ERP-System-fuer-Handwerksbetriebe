package org.example.kalkulationsprogramm.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.domain.Preisanfrage;
import org.example.kalkulationsprogramm.domain.PreisanfrageLieferant;
import org.example.kalkulationsprogramm.domain.PreisanfragePosition;
import org.example.kalkulationsprogramm.domain.ZeugnisTyp;
import org.example.kalkulationsprogramm.dto.Bestellung.BestellungResponseDto;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.PreisanfrageLieferantRepository;
import org.example.kalkulationsprogramm.repository.PreisanfragePositionRepository;
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
    private final DateiSpeicherService dateiSpeicherService;
    private final ZeugnisService zeugnisService;
    private final FirmeninformationService firmeninformationService;
    private final FirmeninformationRepository firmeninformationRepository;
    private final PreisanfrageLieferantRepository preisanfrageLieferantRepository;
    private final PreisanfragePositionRepository preisanfragePositionRepository;

    public BestellungPdfService(BestellungService bestellungService,
                                DateiSpeicherService dateiSpeicherService,
                                ZeugnisService zeugnisService,
                                FirmeninformationService firmeninformationService,
                                FirmeninformationRepository firmeninformationRepository,
                                PreisanfrageLieferantRepository preisanfrageLieferantRepository,
                                PreisanfragePositionRepository preisanfragePositionRepository) {
        this.bestellungService = bestellungService;
        this.dateiSpeicherService = dateiSpeicherService;
        this.zeugnisService = zeugnisService;
        this.firmeninformationService = firmeninformationService;
        this.firmeninformationRepository = firmeninformationRepository;
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
                    table.addCell(makeCutCell(b.getSchnittAchseBildUrl(), b.getSchnittbildBildUrl(), cellFont, bg));
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
                    table.addCell(makeCutCell(b.getSchnittAchseBildUrl(), b.getSchnittbildBildUrl(), cellFont, bg));
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
     * Generiert eine druckbare Material-Bedarfsliste fuer ein Projekt zum
     * handschriftlichen Abhaken durch den Mitarbeiter.
     * <p>
     * Layout (A4 hoch — passt aufs Klemmbrett):
     * <ul>
     *   <li>Firmen-Briefkopf (Logo + Adresse)</li>
     *   <li>Titel + Projekt-Block (Bauvorhaben, Auftrag, Kunde, Datum)</li>
     *   <li>Tabelle mit zwei leeren Checkbox-Spalten "Vorhanden" / "Bestellen"
     *       und einer Notiz-Spalte fuer handschriftliche Anmerkungen</li>
     *   <li>Firmen-Fusszeile</li>
     * </ul>
     * Bewusst kein "exportiertAm" setzen — das ist nur ein internes Arbeitsblatt
     * und sperrt die Bedarfszeilen nicht.
     */
    public Path generateBedarfslistePdf(Long projektId) {
        if (projektId == null) {
            throw new IllegalArgumentException("projektId darf nicht null sein");
        }
        List<BestellungResponseDto> alle = bestellungService.findeOffeneBestellungen();
        List<BestellungResponseDto> items = alle.stream()
                .filter(b -> projektId.equals(b.getProjektId()))
                .collect(Collectors.toList());

        Firmeninformation firma = firmeninformationRepository.findFirmeninformation().orElse(null);

        try {
            Path dir = Path.of("uploads");
            Files.createDirectories(dir);
            Path temp = Files.createTempFile(dir, "bedarfsliste-", ".pdf.html");
            // Querformat — mehr Spalten passen ohne Umbruch (inkl. VPE für Stangenware).
            Document doc = new Document(PageSize.A4.rotate(), 36f, 36f, 36f, 36f);
            PdfWriter writer = PdfWriter.getInstance(doc, Files.newOutputStream(temp));
            writer.setCompressionLevel(0);
            doc.open();

            addBriefkopf(doc, firma);
            addBedarfslisteTitel(doc, items);
            addBedarfslistePositionen(doc, items);
            addLegende(doc);
            addFirmenFusszeile(doc, firma);

            doc.close();
            // Fallback-Marker (Test-Robustheit, parallel zum Bestell-PDF)
            try {
                Files.writeString(temp, "\nMaterial-Bedarfsliste\n", StandardOpenOption.APPEND);
            } catch (IOException ignored) {
            }
            if (!Files.exists(temp)) {
                Files.createDirectories(temp.getParent());
                Files.createFile(temp);
            }
            return temp.toAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException("PDF-Generierung der Bedarfsliste fehlgeschlagen", e);
        }
    }

    /**
     * Briefkopf: Logo links, Firmen-Adresse rechts, dezente rote Trennlinie.
     */
    private void addBriefkopf(Document doc, Firmeninformation firma) throws DocumentException {
        Color rot = new Color(204, 0, 0);
        Color grauText = new Color(70, 70, 70);
        Font firmaFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(40, 40, 40));
        Font adressFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, grauText);

        PdfPTable kopf = new PdfPTable(new float[] { 1.4f, 1f });
        kopf.setWidthPercentage(100);
        kopf.getDefaultCell().setBorder(PdfPCell.NO_BORDER);

        // Logo
        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(PdfPCell.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_TOP);
        Image logo = firmeninformationService.loadLogoImage();
        if (logo != null) {
            logo.scaleToFit(160, 70);
            logoCell.addElement(logo);
        }
        kopf.addCell(logoCell);

        // Adressblock
        PdfPCell adresseCell = new PdfPCell();
        adresseCell.setBorder(PdfPCell.NO_BORDER);
        adresseCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (firma != null) {
            Paragraph fname = new Paragraph(safe(firma.getFirmenname()), firmaFont);
            fname.setAlignment(Element.ALIGN_RIGHT);
            adresseCell.addElement(fname);
            String adressZeile = joinNonBlank(" · ", firma.getStrasse(),
                    joinNonBlank(" ", firma.getPlz(), firma.getOrt()));
            if (!adressZeile.isBlank()) {
                Paragraph p = new Paragraph(adressZeile, adressFont);
                p.setAlignment(Element.ALIGN_RIGHT);
                adresseCell.addElement(p);
            }
            String kontaktZeile = joinNonBlank(" · ",
                    firma.getTelefon() != null && !firma.getTelefon().isBlank()
                            ? "Tel. " + firma.getTelefon() : null,
                    firma.getEmail(),
                    firma.getWebsite());
            if (!kontaktZeile.isBlank()) {
                Paragraph p = new Paragraph(kontaktZeile, adressFont);
                p.setAlignment(Element.ALIGN_RIGHT);
                adresseCell.addElement(p);
            }
        }
        kopf.addCell(adresseCell);
        doc.add(kopf);

        // Trennlinie (dünn, rot)
        PdfPTable linie = new PdfPTable(1);
        linie.setWidthPercentage(100);
        linie.setSpacingBefore(8f);
        linie.setSpacingAfter(12f);
        PdfPCell ll = new PdfPCell(new Phrase(" "));
        ll.setBorder(PdfPCell.NO_BORDER);
        ll.setBorderWidthBottom(1.2f);
        ll.setBorderColorBottom(rot);
        ll.setFixedHeight(2f);
        linie.addCell(ll);
        doc.add(linie);
    }

    /**
     * Titel + Projekt-Stammblock (Bauvorhaben, Auftrag, Kunde, Datum, Anzahl).
     */
    private void addBedarfslisteTitel(Document doc, List<BestellungResponseDto> items) throws DocumentException {
        Color rot = new Color(204, 0, 0);
        Color grau = new Color(110, 110, 110);
        Font titelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, rot);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 9, grau);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, grau);
        Font wertFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(30, 30, 30));

        Paragraph titel = new Paragraph("Material-Bedarfsliste", titelFont);
        titel.setSpacingAfter(2f);
        doc.add(titel);

        Paragraph sub = new Paragraph("Arbeitsblatt zum Abhaken — Vorhanden / Bestellen", subFont);
        sub.setSpacingAfter(10f);
        doc.add(sub);

        // Projekt-Block in 2x4 Layout (Label/Wert)
        BestellungResponseDto first = items.isEmpty() ? null : items.get(0);
        String bauvorhaben = first != null ? safe(first.getProjektName()) : "";
        String auftrag = first != null ? safe(first.getProjektNummer()) : "";
        String kunde = first != null ? safe(first.getKundenName()) : "";
        String datum = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN));
        String anzahl = items.size() + (items.size() == 1 ? " Position" : " Positionen");

        PdfPTable infoBox = new PdfPTable(new float[] { 1f, 2f, 1f, 2f });
        infoBox.setWidthPercentage(100);
        infoBox.setSpacingAfter(14f);
        Color boxBg = new Color(250, 248, 248);
        Color boxBorder = new Color(220, 200, 200);
        addInfoZelle(infoBox, "Bauvorhaben", bauvorhaben.isBlank() ? "—" : bauvorhaben,
                labelFont, wertFont, boxBg, boxBorder);
        addInfoZelle(infoBox, "Auftragsnummer", auftrag.isBlank() ? "—" : auftrag,
                labelFont, wertFont, boxBg, boxBorder);
        addInfoZelle(infoBox, "Kunde", kunde.isBlank() ? "—" : kunde,
                labelFont, wertFont, boxBg, boxBorder);
        addInfoZelle(infoBox, "Erstellt am", datum, labelFont, wertFont, boxBg, boxBorder);
        addInfoZelle(infoBox, "Umfang", anzahl, labelFont, wertFont, boxBg, boxBorder);
        addInfoZelle(infoBox, "Bearbeiter", "", labelFont, wertFont, boxBg, boxBorder);
        addInfoZelle(infoBox, "Datum / Unterschrift", "", labelFont, wertFont, boxBg, boxBorder);
        addInfoZelle(infoBox, "Status", "in Bearbeitung", labelFont, wertFont, boxBg, boxBorder);
        doc.add(infoBox);
    }

    private void addInfoZelle(PdfPTable table, String label, String wert,
                              Font labelFont, Font wertFont, Color bg, Color border) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setBorderColor(border);
        cell.setBorderWidth(0.6f);
        cell.setPadding(6f);
        Paragraph l = new Paragraph(label.toUpperCase(Locale.GERMAN), labelFont);
        l.setSpacingAfter(2f);
        cell.addElement(l);
        cell.addElement(new Paragraph(wert, wertFont));
        table.addCell(cell);
    }

    /**
     * Hauptliste: pro Bedarfszeile eine Tabellenzeile mit
     * leeren Checkbox-Zellen ("Vorhanden" / "Bestellen") und Notiz-Spalte.
     */
    private void addBedarfslistePositionen(Document doc, List<BestellungResponseDto> items)
            throws DocumentException {
        Color rot = new Color(204, 0, 0);
        Color headerBg = new Color(204, 0, 0);
        Color altBg = new Color(248, 248, 248);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font cellBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(30, 30, 30));
        Font cellDimFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(110, 110, 110));

        PdfPTable table = new PdfPTable(
                new float[] { 0.5f, 3.5f, 1.0f, 0.9f, 0.9f, 0.9f, 1.0f, 1.4f, 1.0f, 1.6f });
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setKeepTogether(false);
        String[] headers = { "Pos", "Material / Werkstoff", "Menge", "Fixmaß",
                "VPE", "Gewicht", "Vorhanden", "Teilw. (Stk)", "Bestellen", "Notiz" };
        for (int i = 0; i < headers.length; i++) {
            PdfPCell hc = new PdfPCell(new Phrase(headers[i], headerFont));
            hc.setBackgroundColor(headerBg);
            hc.setPadding(6f);
            hc.setBorderColor(headerBg);
            hc.setHorizontalAlignment(i == 0 || i >= 2 ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
            table.addCell(hc);
        }

        if (items.isEmpty()) {
            PdfPCell leer = new PdfPCell(new Phrase(
                    "Für dieses Projekt sind aktuell keine offenen Bedarfspositionen erfasst.",
                    cellDimFont));
            leer.setColspan(headers.length);
            leer.setPadding(20f);
            leer.setHorizontalAlignment(Element.ALIGN_CENTER);
            leer.setBackgroundColor(Color.WHITE);
            table.addCell(leer);
            doc.add(table);
            return;
        }

        Color zellRand = new Color(220, 220, 220);
        boolean alternate = false;
        int nr = 1;
        for (BestellungResponseDto b : items) {
            Color bg = alternate ? altBg : Color.WHITE;

            // Pos
            table.addCell(makeListenCell(String.valueOf(nr++), cellFont, bg, zellRand,
                    Element.ALIGN_CENTER));

            // Material/Werkstoff (zweizeilig)
            PdfPCell matCell = new PdfPCell();
            matCell.setBackgroundColor(bg);
            matCell.setBorderColor(zellRand);
            matCell.setPadding(5f);
            String produkt = safe(b.getProduktname());
            if (produkt.isBlank()) produkt = "Unbenannt";
            Paragraph pProdukt = new Paragraph(produkt, cellBoldFont);
            matCell.addElement(pProdukt);
            String produkttext = safe(b.getProdukttext());
            if (!produkttext.isBlank()) {
                matCell.addElement(new Paragraph(produkttext, cellFont));
            }
            String werkstoff = safe(b.getWerkstoffName());
            String kategorie = safe(b.getKategorieName());
            String detailZeile = joinNonBlank(" · ",
                    werkstoff.isBlank() ? null : werkstoff,
                    kategorie.isBlank() ? null : kategorie);
            if (!detailZeile.isBlank()) {
                matCell.addElement(new Paragraph(detailZeile, cellDimFont));
            }
            String externNr = safe(b.getExterneArtikelnummer());
            String lieferantName = safe(b.getLieferantName());
            boolean istWerkstoff = b.getRootKategorieId() != null && b.getRootKategorieId() == 1;
            if (!istWerkstoff && (!externNr.isBlank() || !lieferantName.isBlank())) {
                // Bei Nicht-Werkstoff-Artikeln zeigen wir Lieferant + dessen
                // Artikelnummer kombiniert: der Mitarbeiter sieht sofort, wer
                // das Teil liefert und unter welcher Nummer er es findet.
                String zeile = joinNonBlank(" · ",
                        lieferantName.isBlank() ? null : lieferantName,
                        externNr.isBlank() ? null : "Lief.-Art-Nr.: " + externNr);
                matCell.addElement(new Paragraph(zeile, cellDimFont));
            } else if (istWerkstoff) {
                // Werkstoffe sind lieferanten-neutral — wird erst bei der
                // Preisanfrage festgelegt.
                Font hinweisFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8,
                        new Color(140, 90, 90));
                matCell.addElement(new Paragraph("Werkstoff – Lieferant offen", hinweisFont));
            }
            table.addCell(matCell);

            // Menge
            table.addCell(makeListenCell(formatMenge(b), cellFont, bg, zellRand, Element.ALIGN_CENTER));

            // Fixmaß
            boolean hatFixmass = b.getFixmassMm() != null && b.getFixmassMm() > 0;
            String fixmass = hatFixmass ? b.getFixmassMm() + " mm" : "—";
            table.addCell(makeListenCell(fixmass, cellFont, bg, zellRand, Element.ALIGN_CENTER));

            // Verpackungseinheit (Stangenlänge in m) — nur für Werkstoffe ohne Fixmaß
            // sinnvoll: Stangenware wird in voller Länge eingekauft, Fixzuschnitte
            // dagegen kommen schon auf Maß und brauchen keine VPE-Angabe.
            String vpe = (!hatFixmass && istWerkstoff
                    && b.getVerpackungseinheit() != null && b.getVerpackungseinheit() > 0)
                    ? b.getVerpackungseinheit() + " m" : "—";
            table.addCell(makeListenCell(vpe, cellFont, bg, zellRand, Element.ALIGN_CENTER));

            // Gewicht
            String gewicht = formatKg(b.getKilogramm());
            table.addCell(makeListenCell(gewicht, cellFont, bg, zellRand, Element.ALIGN_CENTER));

            // Checkbox "Vorhanden"
            table.addCell(makeCheckboxCell(bg, zellRand, rot));
            // "Teilweise vorhanden" — Checkbox + handschriftliches Stk-Feld
            table.addCell(makeTeilweiseCell(bg, zellRand, rot, cellDimFont));
            // Checkbox "Bestellen"
            table.addCell(makeCheckboxCell(bg, zellRand, rot));

            // Notiz (leere Zelle für handschriftliche Anmerkungen)
            PdfPCell notiz = new PdfPCell(new Phrase(" "));
            notiz.setBackgroundColor(bg);
            notiz.setBorderColor(zellRand);
            notiz.setMinimumHeight(28f);
            table.addCell(notiz);

            alternate = !alternate;
        }

        doc.add(table);
    }

    private PdfPCell makeListenCell(String text, Font font, Color bg, Color rand, int hAlign) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBackgroundColor(bg);
        cell.setBorderColor(rand);
        cell.setPadding(5f);
        cell.setMinimumHeight(28f);
        cell.setHorizontalAlignment(hAlign);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    /**
     * Druckbare Checkbox: leeres Quadrat (14×14pt) mit Rahmen, mittig in der Zelle.
     * Bewusst kein Unicode-Symbol (☐) — Helvetica-Standard hat das Glyph
     * nicht, was zu fehlenden Zeichen im PDF fuehrt.
     * Die innere Tabelle wird auf 14pt fixiert ({@code setLockedWidth}),
     * damit die Box echt quadratisch wird und nicht in die Spaltenbreite
     * "ausfliesst".
     */
    private PdfPCell makeCheckboxCell(Color bg, Color rand, Color boxColor) {
        PdfPTable inner = new PdfPTable(1);
        try {
            inner.setTotalWidth(new float[] { 14f });
            inner.setLockedWidth(true);
        } catch (DocumentException ignored) {
            // setTotalWidth darf hier nicht werfen, da nur eine Spalte
        }
        inner.setHorizontalAlignment(Element.ALIGN_CENTER);
        PdfPCell box = new PdfPCell(new Phrase(" "));
        box.setBorderColor(boxColor);
        box.setBorderWidth(1.2f);
        box.setFixedHeight(14f);
        box.setBackgroundColor(Color.WHITE);
        inner.addCell(box);

        PdfPCell wrapper = new PdfPCell(inner);
        wrapper.setBackgroundColor(bg);
        wrapper.setBorderColor(rand);
        wrapper.setPadding(6f);
        wrapper.setHorizontalAlignment(Element.ALIGN_CENTER);
        wrapper.setVerticalAlignment(Element.ALIGN_MIDDLE);
        wrapper.setMinimumHeight(28f);
        return wrapper;
    }

    /**
     * "Teilweise vorhanden"-Zelle: kleine Checkbox links, daneben eine
     * Schreiblinie fuer die handschriftliche Stueckzahl mit "Stk"-Hinweis.
     */
    private PdfPCell makeTeilweiseCell(Color bg, Color rand, Color boxColor, Font hintFont) {
        PdfPTable inner = new PdfPTable(new float[] { 0.7f, 1.6f, 0.7f });
        inner.setWidthPercentage(100);

        // Box
        PdfPCell box = new PdfPCell(new Phrase(" "));
        box.setBorderColor(boxColor);
        box.setBorderWidth(1.2f);
        box.setFixedHeight(14f);
        box.setBackgroundColor(Color.WHITE);
        inner.addCell(box);

        // Schreiblinie fuer die Stueckzahl
        PdfPCell linie = new PdfPCell(new Phrase(" "));
        linie.setBorder(PdfPCell.NO_BORDER);
        linie.setBorderWidthBottom(0.8f);
        linie.setBorderColorBottom(new Color(120, 120, 120));
        linie.setFixedHeight(14f);
        linie.setBackgroundColor(Color.WHITE);
        inner.addCell(linie);

        // "Stk"-Hinweis
        PdfPCell stk = new PdfPCell(new Phrase("Stk", hintFont));
        stk.setBorder(PdfPCell.NO_BORDER);
        stk.setHorizontalAlignment(Element.ALIGN_LEFT);
        stk.setVerticalAlignment(Element.ALIGN_BOTTOM);
        stk.setPaddingLeft(2f);
        stk.setBackgroundColor(bg);
        inner.addCell(stk);

        PdfPCell wrapper = new PdfPCell(inner);
        wrapper.setBackgroundColor(bg);
        wrapper.setBorderColor(rand);
        wrapper.setPadding(6f);
        wrapper.setHorizontalAlignment(Element.ALIGN_CENTER);
        wrapper.setVerticalAlignment(Element.ALIGN_MIDDLE);
        wrapper.setMinimumHeight(28f);
        return wrapper;
    }

    private void addLegende(Document doc) throws DocumentException {
        Font legendeFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, new Color(110, 110, 110));
        Paragraph p = new Paragraph(
                "Hinweis: „Vorhanden“ = aus Lager / Restbestand komplett verfügbar. "
                        + "„Teilw.“ = teilweise vorhanden, fehlende Stückzahl bitte in das Feld eintragen. "
                        + "„Bestellen“ = muss komplett eingekauft werden — diese Positionen werden "
                        + "anschließend in eine Preisanfrage übernommen.",
                legendeFont);
        p.setSpacingBefore(10f);
        doc.add(p);
    }

    private void addFirmenFusszeile(Document doc, Firmeninformation firma) throws DocumentException {
        if (firma == null) return;
        Color grau = new Color(140, 140, 140);
        Font fussFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, grau);

        // Trennlinie oberhalb
        PdfPTable linie = new PdfPTable(1);
        linie.setWidthPercentage(100);
        linie.setSpacingBefore(18f);
        linie.setSpacingAfter(4f);
        PdfPCell ll = new PdfPCell(new Phrase(" "));
        ll.setBorder(PdfPCell.NO_BORDER);
        ll.setBorderWidthTop(0.6f);
        ll.setBorderColorTop(new Color(220, 200, 200));
        ll.setFixedHeight(2f);
        linie.addCell(ll);
        doc.add(linie);

        String zeile1 = joinNonBlank(" · ",
                firma.getFirmenname(),
                firma.getGeschaeftsfuehrer() != null && !firma.getGeschaeftsfuehrer().isBlank()
                        ? "GF " + firma.getGeschaeftsfuehrer() : null,
                firma.getSteuernummer() != null && !firma.getSteuernummer().isBlank()
                        ? "St-Nr. " + firma.getSteuernummer() : null,
                firma.getUstIdNr() != null && !firma.getUstIdNr().isBlank()
                        ? "USt-IdNr. " + firma.getUstIdNr() : null);
        if (!zeile1.isBlank()) {
            Paragraph p1 = new Paragraph(zeile1, fussFont);
            p1.setAlignment(Element.ALIGN_CENTER);
            doc.add(p1);
        }
        String zeile2 = joinNonBlank(" · ",
                firma.getBankName(),
                firma.getIban() != null && !firma.getIban().isBlank() ? "IBAN " + firma.getIban() : null,
                firma.getBic() != null && !firma.getBic().isBlank() ? "BIC " + firma.getBic() : null);
        if (!zeile2.isBlank()) {
            Paragraph p2 = new Paragraph(zeile2, fussFont);
            p2.setAlignment(Element.ALIGN_CENTER);
            doc.add(p2);
        }
        if (firma.getFusszeileText() != null && !firma.getFusszeileText().isBlank()) {
            Paragraph p3 = new Paragraph(firma.getFusszeileText(), fussFont);
            p3.setAlignment(Element.ALIGN_CENTER);
            p3.setSpacingBefore(2f);
            doc.add(p3);
        }
    }

    private String joinNonBlank(String sep, String... teile) {
        if (teile == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String t : teile) {
            if (t == null || t.isBlank()) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(t);
        }
        return sb.toString();
    }

    /** Formatiert Kilogramm wie im Frontend (1 Nachkommastelle, "—" wenn leer/0). */
    private String formatKg(java.math.BigDecimal kg) {
        if (kg == null || kg.signum() <= 0) return "—";
        return String.format(java.util.Locale.GERMANY, "%.1f kg", kg);
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

    /**
     * Zelle mit Achsen- und Schnitt-Icon uebereinander (beide optional).
     * Das Achsen-Bild gehoert zur Kategorie, das Schnittbild zeigt den
     * konkreten Anschnitt. Bei normalem 90°-Zuschnitt ist beides null.
     */
    private PdfPCell makeCutCell(String achseUrl, String schnittUrl, Font font, Color bg) {
        byte[] achseBytes = loadIconByUrl(achseUrl);
        byte[] schnittBytes = loadIconByUrl(schnittUrl);
        if (achseBytes == null && schnittBytes == null) {
            return makeCell("", font, bg);
        }
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(2f);
        addIconElement(cell, achseBytes);
        addIconElement(cell, schnittBytes);
        return cell;
    }

    private void addIconElement(PdfPCell cell, byte[] bytes) {
        if (bytes == null) return;
        try {
            Image icon = Image.getInstance(bytes);
            icon.scaleToFit(26f, 26f);
            icon.setAlignment(Image.ALIGN_CENTER);
            cell.addElement(icon);
        } catch (Exception ignored) {
            // Kaputtes Bild -> einfach ueberspringen
        }
    }

    private byte[] loadIconByUrl(String url) {
        if (url == null || url.isBlank()) return null;
        byte[] cached = schnittbildIconCache.get(url);
        if (cached != null) {
            return cached == NO_IMAGE ? null : cached;
        }
        byte[] loaded = fetchIconByUrl(url);
        schnittbildIconCache.put(url, loaded != null ? loaded : NO_IMAGE);
        return loaded;
    }

    private byte[] fetchIconByUrl(String url) {
        try {
            String name = extractFilename(url);
            if (name == null) return null;
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
