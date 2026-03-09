package org.example.kalkulationsprogramm.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PDF-Service für Rechnungsgenerierung mit dem "Schütten"-Prinzip (ColumnText).
 * 
 * Seite 1: Großer Briefkopf + kleine Content-Area + Footer
 * Seite 2+: Kleine Kopfzeile + große Content-Area
 * 
 * Der Inhalt wird in definierte Bereiche "geschüttet" und fließt automatisch
 * auf die nächste Seite wenn nötig.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RechnungPdfService {

    // ======================= DTOs =======================

    /**
     * Layout-Definition für die Content-Bereiche
     */
    public record LayoutDto(
            RectDto page1Rect, // Content-Bereich auf Seite 1 (klein)
            RectDto page2Rect, // Content-Bereich auf Folgeseiten (groß)
            RectDto headerRect, // Kopfzeilen-Position
            RectDto footerRect, // Fußzeilen-Position
            String logoPath // Pfad zum Logo
    ) {
    }

    public record RectDto(
            float llx, // Lower Left X
            float lly, // Lower Left Y
            float urx, // Upper Right X
            float ury // Upper Right Y
    ) {
        public Rectangle toRectangle() {
            return new Rectangle(llx, lly, urx, ury);
        }
    }

    /**
     * Rechnungs-Daten
     */
    public record RechnungDto(
            LayoutDto layout,
            KopfdatenDto kopfdaten,
            List<ContentBlockDto> contentBlocks, // Gemischte Blöcke in originaler Reihenfolge
            List<FormBlockDto> formBlocks, // Custom Layout Blocks from Template
            String schlusstext,
            String backgroundImagePage1, // Base64-encoded background for page 1
            String backgroundImagePage2, // Base64-encoded background for page 2+
            BigDecimal globalRabattProzent, // Globaler Rabatt in % auf das gesamte Dokument
            AbrechnungsverlaufPdfDto abrechnungsverlauf, // Abrechnungsverlauf für Abzüge
            BigDecimal betragNetto) { // Überschreibt berechnete Nettosumme (z.B. für Abschlagsrechnungen)

        // Backwards-compatible constructor without betragNetto
        public RechnungDto(LayoutDto layout, KopfdatenDto kopfdaten, List<ContentBlockDto> contentBlocks,
                           List<FormBlockDto> formBlocks, String schlusstext,
                           String backgroundImagePage1, String backgroundImagePage2,
                           BigDecimal globalRabattProzent, AbrechnungsverlaufPdfDto abrechnungsverlauf) {
            this(layout, kopfdaten, contentBlocks, formBlocks, schlusstext,
                 backgroundImagePage1, backgroundImagePage2, globalRabattProzent, abrechnungsverlauf, null);
        }

        // Backwards-compatible constructor without abrechnungsverlauf
        public RechnungDto(LayoutDto layout, KopfdatenDto kopfdaten, List<ContentBlockDto> contentBlocks,
                           List<FormBlockDto> formBlocks, String schlusstext,
                           String backgroundImagePage1, String backgroundImagePage2,
                           BigDecimal globalRabattProzent) {
            this(layout, kopfdaten, contentBlocks, formBlocks, schlusstext,
                 backgroundImagePage1, backgroundImagePage2, globalRabattProzent, null, null);
        }

        // Backwards-compatible constructor without globalRabattProzent
        public RechnungDto(LayoutDto layout, KopfdatenDto kopfdaten, List<ContentBlockDto> contentBlocks,
                           List<FormBlockDto> formBlocks, String schlusstext,
                           String backgroundImagePage1, String backgroundImagePage2) {
            this(layout, kopfdaten, contentBlocks, formBlocks, schlusstext,
                 backgroundImagePage1, backgroundImagePage2, null, null, null);
        }
    }

    /**
     * DTO für den Abrechnungsverlauf (Abzüge vorheriger Rechnungen)
     */
    public record AbrechnungsverlaufPdfDto(
            String basisdokumentNummer,
            String basisdokumentTyp,
            java.time.LocalDate basisdokumentDatum,
            BigDecimal basisdokumentBetragNetto,
            List<AbrechnungspositionPdfDto> positionen) {
    }

    public record AbrechnungspositionPdfDto(
            String dokumentNummer,
            String typ,
            java.time.LocalDate datum,
            BigDecimal betragNetto,
            Integer abschlagsNummer) {
    }

    /**
     * Gemischter Content-Block (entweder TEXT, SERVICE, CLOSURE, SEPARATOR, SECTION_HEADER oder SUBTOTAL)
     */
    public record ContentBlockDto(
            String type, // "TEXT", "SERVICE", "CLOSURE", "SEPARATOR", "SECTION_HEADER", "SUBTOTAL"
            // Für TEXT:
            String text,
            boolean fett,
            int fontSize,
            // Für SERVICE:
            String pos,
            String beschreibung,      // Nur der Titel/Kurztext
            String beschreibungHtml,  // Rich-Text Beschreibung (HTML)
            BigDecimal menge,
            String einheit,
            BigDecimal einzelpreis,
            BigDecimal gesamt,
            boolean optional,
            // Für SECTION_HEADER:
            String sectionLabel,
            // Rabatt pro Position in Prozent (0-100)
            BigDecimal rabattProzent) {

        public boolean isText() {
            return "TEXT".equals(type);
        }

        public boolean isService() {
            return "SERVICE".equals(type);
        }

        public boolean isSeparator() {
            return "SEPARATOR".equals(type);
        }

        public boolean isSectionHeader() {
            return "SECTION_HEADER".equals(type);
        }

        public boolean isSubtotal() {
            return "SUBTOTAL".equals(type);
        }
    }

    public record KopfdatenDto(
            String rechnungsnummer,
            LocalDate rechnungsDatum,
            LocalDate leistungsDatum,
            String kundenName,
            String kundenAdresse,
            String betreff,
            String kundennummer,
            String dokumentTyp,
            String bezugsdokument,
            String projektnummer,
            String bauvorhaben) {
    }

    // ======================= FONTS =======================

    private static final Font FONT_NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);
    private static final Font FONT_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font FONT_SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private static final Font FONT_HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
    private static final Color HEADER_BG = new Color(220, 38, 38); // Rose-600
    private static final Color ALT_ROW_BG = new Color(250, 250, 250);

    // ======================= PDF Generation =======================

    /**
     * Generiert eine Rechnung als PDF mit dem ColumnText-Schütten-Prinzip.
     * 
     * @param data Rechnungsdaten inkl. Layout
     * @param out  OutputStream für das PDF
     */
    public void generatePdf(RechnungDto data, OutputStream out) {
        Document document = new Document(PageSize.A4, 0, 0, 0, 0); // No margins, background fills page

        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();

            PdfContentByte cb = writer.getDirectContent();
            PdfContentByte cbUnder = writer.getDirectContentUnder();
            ColumnText ct = new ColumnText(cb);

            // ===== Seite 1: Hintergrundbild rendern =====
            renderBackgroundImage(cbUnder, data.backgroundImagePage1());

            // ===== Page Number Template vorbereiten =====
            PdfTemplate totalPages = cb.createTemplate(50, 16);
            BaseFont[] totalPagesBaseFontHolder = new BaseFont[1];
            float[] totalPagesFontSizeHolder = new float[] { 10f };

            // ===== Custom Form-Blöcke rendern (Overlay über Hintergrund) =====
            // Wenn Custom Blocks vorhanden sind (mehr als nur 'table'), nutzen wir diese für das Layout.
            // Andernfalls Fallback auf Standard-Briefkopf.
            boolean hasCustomBlocks = data.formBlocks() != null && data.formBlocks().stream().anyMatch(b -> !b.type().equals("table"));
            
            if (data.formBlocks() != null) {
                log.info("FormBlocks empfangen: {} Blöcke, hasCustomBlocks={}", data.formBlocks().size(), hasCustomBlocks);
                for (FormBlockDto fb : data.formBlocks()) {
                    log.info("  Block: id={}, type={}, page={}, x={}, y={}, w={}, h={}, content='{}'",
                            fb.id(), fb.type(), fb.page(), fb.x(), fb.y(), fb.width(), fb.height(),
                            fb.content() != null ? (fb.content().length() > 60 ? fb.content().substring(0, 60) + "..." : fb.content()) : "null");
                }
            } else {
                log.info("Keine FormBlocks vorhanden");
            }
            
            if (hasCustomBlocks) {
                // Seite 1: Page 1 Blocks rendern
                renderFormBlocks(cb, data.formBlocks(), 1, totalPages, 595f, 842f, data.kopfdaten(), totalPagesBaseFontHolder, totalPagesFontSizeHolder);
            } else {
                renderBriefkopf(cb, data.kopfdaten(), data.layout());
                renderFooter(cb, data.layout());
            }

            // ===== Content aufbauen =====
            // Alle Inhalte werden in einer ColumnText-Instanz in ORIGINALER REIHENFOLGE
            // gerendert.
            // Der Summenblock wird direkt nach der letzten Leistung/Bauabschnitt eingefügt,
            // damit nachfolgende Textblöcke NACH dem Summenblock erscheinen.
            boolean hasClosureBlock = data.contentBlocks() != null && data.contentBlocks().stream()
                    .anyMatch(b -> "CLOSURE".equals(b.type()));

            if (!hasClosureBlock && data.contentBlocks() != null && !data.contentBlocks().isEmpty()) {
                // Index des letzten SERVICE/SECTION_HEADER/SUBTOTAL/SEPARATOR Blocks finden
                int lastServiceIdx = -1;
                for (int i = data.contentBlocks().size() - 1; i >= 0; i--) {
                    String type = data.contentBlocks().get(i).type();
                    if ("SERVICE".equals(type) || "SECTION_HEADER".equals(type)
                            || "SUBTOTAL".equals(type) || "SEPARATOR".equals(type)) {
                        lastServiceIdx = i;
                        break;
                    }
                }

                if (lastServiceIdx >= 0 && lastServiceIdx < data.contentBlocks().size() - 1) {
                    // Es gibt nachfolgende Textblöcke nach der letzten Leistung
                    List<ContentBlockDto> serviceBlocks = data.contentBlocks().subList(0, lastServiceIdx + 1);
                    List<ContentBlockDto> trailingBlocks = data.contentBlocks().subList(lastServiceIdx + 1, data.contentBlocks().size());

                    addMixedContent(ct, serviceBlocks, data.globalRabattProzent(), data.abrechnungsverlauf(), data.kopfdaten().dokumentTyp());
                    addSummenBlock(ct, data.contentBlocks(), data.globalRabattProzent(), data.abrechnungsverlauf(), data.betragNetto(), data.kopfdaten().dokumentTyp());
                    addMixedContent(ct, trailingBlocks, data.globalRabattProzent(), data.abrechnungsverlauf(), data.kopfdaten().dokumentTyp());
                } else {
                    // Keine nachfolgenden Textblöcke - normaler Ablauf
                    addMixedContent(ct, data.contentBlocks(), data.globalRabattProzent(), data.abrechnungsverlauf(), data.kopfdaten().dokumentTyp());
                    addSummenBlock(ct, data.contentBlocks(), data.globalRabattProzent(), data.abrechnungsverlauf(), data.betragNetto(), data.kopfdaten().dokumentTyp());
                }
            } else {
                // CLOSURE vorhanden oder keine Blöcke - Summenblock wird in addMixedContent gehandelt
                addMixedContent(ct, data.contentBlocks(), data.globalRabattProzent(), data.abrechnungsverlauf(), data.kopfdaten().dokumentTyp());
            }

            addSchlusstext(ct, data.schlusstext());

            // ===== Schütten in definierte Bereiche =====
            RectDto rect1 = data.layout().page1Rect();
            RectDto rect2 = data.layout().page2Rect();

            // Erste Seite befüllen
            ct.setSimpleColumn(rect1.llx(), rect1.lly(), rect1.urx(), rect1.ury());
            int status = ct.go();

            int pageNum = 1;
            // Wenn noch Text übrig ist -> Folgeseiten
            while (ColumnText.hasMoreText(status)) {
                document.newPage();
                pageNum++;

                // Auf Folgeseiten: Hintergrundbild Page 2+
                String bg = data.backgroundImagePage2() != null ? data.backgroundImagePage2()
                        : data.backgroundImagePage1();
                renderBackgroundImage(writer.getDirectContentUnder(), bg);

                // Custom Blocks für Folgeseiten rendern (Page 2+ nutzt Template Page 2)
                if (hasCustomBlocks) {
                    renderFormBlocks(cb, data.formBlocks(), pageNum, totalPages, 595f, 842f, data.kopfdaten(), totalPagesBaseFontHolder, totalPagesFontSizeHolder);
                } else {
                    renderFolgeSeitenKopf(cb, data.kopfdaten().rechnungsnummer(), pageNum, totalPages, totalPagesBaseFontHolder, totalPagesFontSizeHolder);
                }

                // Größerer Content-Bereich auf Folgeseiten
                ct.setSimpleColumn(rect2.llx(), rect2.lly(), rect2.urx(), rect2.ury());
                status = ct.go();
            }

            // Gesamtzahl der Seiten in das Template schreiben
            try {
                BaseFont bf = totalPagesBaseFontHolder[0] != null
                        ? totalPagesBaseFontHolder[0]
                        : BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
                float totalPagesFontSize = totalPagesFontSizeHolder[0] > 0 ? totalPagesFontSizeHolder[0] : 10f;
                totalPages.beginText();
                totalPages.setFontAndSize(bf, totalPagesFontSize);
                totalPages.setTextMatrix(0, 0); // Same baseline as surrounding text
                totalPages.showText(String.valueOf(pageNum));
                totalPages.endText();
            } catch (Exception e) {
                log.error("Fehler beim Schreiben der Seitenzahl", e);
            }

            document.close();
            log.info("Rechnung {} erfolgreich generiert ({} Seiten)", data.kopfdaten().rechnungsnummer(), pageNum);

        } catch (Exception e) {
            log.error("Fehler bei PDF-Generierung", e);
            throw new RuntimeException("PDF-Generierung fehlgeschlagen", e);
        }
    }

    /**
     * Generiert PDF als byte[] (für Controller)
     */
    public byte[] generatePdfBytes(RechnungDto data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        generatePdf(data, baos);
        return baos.toByteArray();
    }

    /**
     * Rendert ein Base64-kodiertes Hintergrundbild auf die gesamte Seite
     */
    private void renderBackgroundImage(PdfContentByte cb, String base64Image) {
        if (base64Image == null || base64Image.isBlank()) {
            return;
        }

        try {
            // Base64 decodieren - "data:image/..." Prefix entfernen
            String imageData = base64Image;
            if (imageData.contains(",")) {
                imageData = imageData.substring(imageData.indexOf(",") + 1);
            }

            byte[] imageBytes = java.util.Base64.getDecoder().decode(imageData);
            com.lowagie.text.Image backgroundImage = com.lowagie.text.Image.getInstance(imageBytes);

            // Bild auf volle Seitengröße skalieren
            float pageWidth = PageSize.A4.getWidth();
            float pageHeight = PageSize.A4.getHeight();
            backgroundImage.scaleAbsolute(pageWidth, pageHeight);
            backgroundImage.setAbsolutePosition(0, 0);

            cb.addImage(backgroundImage);
            log.debug("Hintergrundbild gerendert ({}x{})", pageWidth, pageHeight);

        } catch (Exception e) {
            log.warn("Konnte Hintergrundbild nicht rendern: {}", e.getMessage());
        }
    }

    // ======================= Content Rendering =======================

    /**
     * Rendert gemischte Content-Blöcke (TEXT + SERVICE + SEPARATOR + SECTION_HEADER + SUBTOTAL) in originaler Reihenfolge.
     * Service-Blöcke werden als inline Zeilen dargestellt.
     * CLOSURE-Block beendet die Tabelle explizit. Nachfolgende Texte werden als Paragraphen gerendert.
     * SEPARATOR erzeugt eine horizontale Trennlinie.
     * SECTION_HEADER erzeugt eine Bauabschnitt-Überschrift.
     * SUBTOTAL erzeugt eine Zwischensumme der vorherigen SERVICE-Blöcke bis zum letzten SECTION_HEADER/SUBTOTAL.
     */
    private void addMixedContent(ColumnText ct, List<ContentBlockDto> blocks, BigDecimal globalRabattProzent, AbrechnungsverlaufPdfDto abrechnungsverlauf, String dokumentTyp) throws DocumentException {
        if (blocks == null || blocks.isEmpty()) return;

        NumberFormat nf = NumberFormat.getNumberInstance(Locale.GERMANY);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        Color textColor = new Color(0, 0, 0); // Schwarz
        Color headerColor = new Color(71, 85, 105); // Slate-600
        Color accentColor = new Color(190, 18, 60); // Rose-700
        Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 10, textColor);
        Font posFont = FontFactory.getFont(FontFactory.HELVETICA, 10, textColor);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, textColor);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, headerColor);

        PdfPTable currentTable = null;

        // Iterate through all blocks
        for (int i = 0; i < blocks.size(); i++) {
            ContentBlockDto block = blocks.get(i);

            if ("CLOSURE".equals(block.type())) {
                // 1. Close existing table if open
                if (currentTable != null) {
                    ct.addElement(currentTable);
                    currentTable = null;
                }

                // 2. Bauabschnitt-Übersicht: Alle Bauabschnitte mit Summen auflisten
                // plus "Sonstige Leistungen" für root-level Services
                addClosureBreakdown(ct, blocks, i, nf, accentColor, textColor);

                // 3. Add Sums (calculating totals up to this point)
                // Filter blocks up to this closure
                List<ContentBlockDto> blocksBefore = new java.util.ArrayList<>();
                for (int k = 0; k < i; k++) {
                    blocksBefore.add(blocks.get(k));
                }
                addSummenBlock(ct, blocksBefore, globalRabattProzent, abrechnungsverlauf, null, dokumentTyp);

                // 3. Render Closure Text (if any) as standalone Paragraph
                if (block.text() != null && !block.text().isBlank()) {
                     float closureFontSize = Math.max(10f, Math.min(20f, block.fontSize()));
                     java.util.List<com.lowagie.text.Element> closureElements = parseHtmlToElements(
                             block.text(), textColor, closureFontSize, block.fett());
                     for (com.lowagie.text.Element e : closureElements) {
                         ct.addElement(e);
                     }
                }
                
                // Do NOT start a new table immediately. 
                // Only a subsequent SERVICE block will trigger a new table.
                continue;
            }

            if (block.isSeparator()) {
                // Flush current table if open
                if (currentTable != null) {
                    ct.addElement(currentTable);
                    currentTable = null;
                }
                // Add a horizontal separator line
                addSeparatorLine(ct, textColor);
                continue;
            }

            if (block.isSectionHeader()) {
                // Flush current table if open
                if (currentTable != null) {
                    ct.addElement(currentTable);
                    currentTable = null;
                }
                // Add section header with position number
                addSectionHeader(ct, block.sectionLabel(), block.pos(), accentColor, textColor);
                continue;
            }

            if (block.isSubtotal()) {
                // Calculate subtotal: sum all SERVICE blocks since last SECTION_HEADER or SUBTOTAL
                BigDecimal subtotal = BigDecimal.ZERO;
                String sectionLabel = "Zwischensumme";
                for (int k = i - 1; k >= 0; k--) {
                    ContentBlockDto prev = blocks.get(k);
                    if (prev.isSectionHeader()) {
                        sectionLabel = prev.sectionLabel() != null && !prev.sectionLabel().isBlank()
                                ? prev.sectionLabel() : "Zwischensumme";
                        break;
                    }
                    if (prev.isSubtotal()) break;
                    if (prev.isService() && !prev.optional() && prev.gesamt() != null) {
                        subtotal = subtotal.add(prev.gesamt());
                    }
                }

                // Flush current table if open
                if (currentTable != null) {
                    ct.addElement(currentTable);
                    currentTable = null;
                }

                // Add subtotal row
                addSubtotalRow(ct, sectionLabel, subtotal, nf, accentColor, textColor);
                continue;
            }

            if (block.isService()) {
                // Ensure we have a table
                if (currentTable == null) {
                    currentTable = createMainTable(headerFont);
                }

                // Add Service Row
                addServiceRow(currentTable, block, textFont, posFont, labelFont, nf, textColor);
                
            } else if (block.isText()) {
                 // Rich-Text-Formatierungen (fett, kursiv, Farben, Schriftgrößen, Listen, Bilder)
                 // werden über den erweiterten HTML-Parser übernommen.
                 float defaultFontSize = Math.max(10f, Math.min(20f, block.fontSize()));
                 boolean defaultBold = block.fett();
                 String content = block.text() != null ? block.text() : "";

                 // CASE A: We are inside a table -> Render as full-width row (Zwischentext)
                 if (currentTable != null) {
                     PdfPCell textCell = new PdfPCell();
                     textCell.setColspan(5);
                     textCell.setBorder(Rectangle.NO_BORDER);
                     textCell.setPaddingTop(6f);
                     textCell.setPaddingBottom(6f);

                     // Rich HTML parser preserves bold, italic, underline, colors, font-sizes, lists & images
                     java.util.List<com.lowagie.text.Element> elements = parseHtmlToElements(content, textColor, defaultFontSize, defaultBold);
                     for (com.lowagie.text.Element e : elements) {
                         textCell.addElement(e);
                     }
                     
                     currentTable.addCell(textCell);

                 } else {
                     // CASE B: We are OUTSIDE a table (e.g. after Closure) -> Render as Standalone
                     java.util.List<com.lowagie.text.Element> elements = parseHtmlToElements(content, textColor, defaultFontSize, defaultBold);
                     for (com.lowagie.text.Element e : elements) {
                         ct.addElement(e);
                     }
                 }
            }
        }

        // Flush remaining table at the end
        if (currentTable != null) {
            ct.addElement(currentTable);
        }
    }

    /**
     * Rendert eine horizontale Trennlinie im PDF.
     */
    private void addSeparatorLine(ColumnText ct, Color color) throws DocumentException {
        PdfPTable lineTable = new PdfPTable(1);
        lineTable.setWidthPercentage(100);
        lineTable.setSpacingBefore(8f);
        lineTable.setSpacingAfter(8f);

        PdfPCell lineCell = new PdfPCell();
        lineCell.setBorder(Rectangle.BOTTOM);
        lineCell.setBorderColor(color);
        lineCell.setBorderWidth(1f);
        lineCell.setFixedHeight(1f);
        lineTable.addCell(lineCell);

        ct.addElement(lineTable);
    }

    /**
     * Rendert eine Bauabschnitt-Überschrift im PDF.
     * Professionelles Design mit Akzentfarbe.
     */
    private void addSectionHeader(ColumnText ct, String label, String positionNr, Color accentColor, Color textColor) throws DocumentException {
        if (label == null || label.isBlank()) label = "Bauabschnitt";

        Color lineColor = new Color(30, 41, 59); // Slate-800 – minimalistisch schwarz

        PdfPTable headerTable = new PdfPTable(1);
        headerTable.setWidthPercentage(100);
        headerTable.setSpacingBefore(12f);
        headerTable.setSpacingAfter(4f);

        // Main cell with dark bottom border
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(lineColor);
        cell.setBorderWidth(1.5f);
        cell.setPaddingTop(6f);
        cell.setPaddingBottom(6f);
        cell.setPaddingLeft(4f);
        
        // Position (e.g. "1.0") + label – alles in gleicher dunkler Farbe
        String posPrefix = (positionNr != null && !positionNr.isBlank()) ? positionNr + "  " : "";
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, textColor);
        
        Paragraph p = new Paragraph();
        p.setLeading(14f);
        if (!posPrefix.isBlank()) {
            p.add(new Chunk(posPrefix, sectionFont));
        }
        p.add(new Chunk(label, sectionFont));
        cell.addElement(p);

        headerTable.addCell(cell);
        ct.addElement(headerTable);
    }

    /**
     * Rendert eine Zwischensumme (Teilsumme) für einen Bauabschnitt.
     */
    private void addSubtotalRow(ColumnText ct, String label, BigDecimal subtotal, NumberFormat nf, Color accentColor, Color textColor) throws DocumentException {
        Color lineColor = new Color(30, 41, 59); // Slate-800 – minimalistisch schwarz

        PdfPTable subtotalTable = new PdfPTable(new float[] { 7f, 3f });
        subtotalTable.setWidthPercentage(100);
        subtotalTable.setSpacingBefore(4f);
        subtotalTable.setSpacingAfter(8f);

        // Label
        Font subtotalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, textColor);
        PdfPCell labelCell = new PdfPCell(new Phrase("Zwischensumme " + label, subtotalFont));
        labelCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        labelCell.setBorderColor(lineColor);
        labelCell.setBorderWidth(0.75f);
        labelCell.setPaddingTop(5f);
        labelCell.setPaddingBottom(5f);
        labelCell.setPaddingLeft(4f);
        subtotalTable.addCell(labelCell);

        // Amount
        PdfPCell amountCell = new PdfPCell(new Phrase(nf.format(subtotal) + " €", subtotalFont));
        amountCell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        amountCell.setBorderColor(lineColor);
        amountCell.setBorderWidth(0.75f);
        amountCell.setPaddingTop(5f);
        amountCell.setPaddingBottom(5f);
        amountCell.setPaddingRight(4f);
        amountCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        subtotalTable.addCell(amountCell);

        ct.addElement(subtotalTable);
    }

    private PdfPTable createMainTable(Font headerFont) throws DocumentException {
        // Spaltenbreiten: Pos. breiter für "Pos." ohne Umbruch
        PdfPTable table = new PdfPTable(new float[] { 0.8f, 1.2f, 4.8f, 1.4f, 1.4f });
        table.setWidthPercentage(100);
        table.setSpacingBefore(8f);
        table.setSpacingAfter(8f);
        table.setHeaderRows(1);
        // Zeilen früh splitten - lange Beschreibungstexte werden am Seitenrand
        // umgebrochen statt die komplette Zeile auf die nächste Seite zu schieben.
        table.setSplitLate(false);
        table.setSplitRows(true);

        // Moderne Header-Bezeichnungen
        String[] headers = { "Pos.", "Menge", "Bezeichnung", "Einzelpreis", "Gesamtpreis" };
        int[] aligns = { Element.ALIGN_CENTER, Element.ALIGN_CENTER, Element.ALIGN_LEFT, Element.ALIGN_RIGHT, Element.ALIGN_RIGHT };
        
        // Schwarze Linie unter Header (Slate-800)
        Color headerLineColor = new Color(30, 41, 59);
        
        for (int i = 0; i < headers.length; i++) {
            Font hFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(71, 85, 105)); // Slate-600
            PdfPCell hCell = new PdfPCell(new Phrase(headers[i], hFont));
            hCell.setBorder(Rectangle.BOTTOM);
            hCell.setBorderColor(headerLineColor);
            hCell.setBorderWidth(1.5f);
            hCell.setPadding(4f);
            hCell.setPaddingBottom(4f);
            hCell.setHorizontalAlignment(aligns[i]);
            hCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            hCell.setBackgroundColor(Color.WHITE);
            // Keine Umbrüche in Header-Zellen
            hCell.setNoWrap(true);
            table.addCell(hCell);
        }
        return table;
    }

    private void addServiceRow(PdfPTable table, ContentBlockDto block, Font textFont, Font posFont, Font labelFont, NumberFormat nf, Color textColor) {
        boolean isAlternative = block.optional();
        Font currentTextFont = isAlternative ? FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, textColor) : textFont;
        Font currentLabelFont = isAlternative ? FontFactory.getFont(FontFactory.HELVETICA_BOLDOBLIQUE, 10, textColor) : labelFont;
        
        // Dezente Zeilentrennung
        Color borderColor = new Color(226, 232, 240); // Slate-200
        boolean hasDescription = block.beschreibungHtml() != null && !block.beschreibungHtml().isBlank();

        // Kompaktere Padding-Werte für Zeilenhöhe passend zur Schriftgröße
        float cellPadding = 3f;
        float cellPaddingTop = 4f;
        float cellPaddingBottom = 4f;

        // Pos - zentriert in einem dezenten Badge-Style
        Font posBadgeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(71, 85, 105)); // Slate-600
        String posText = block.pos() != null ? block.pos() : "";
        PdfPCell posCell = new PdfPCell(new Phrase(posText, posBadgeFont));
        posCell.setBorder(Rectangle.BOTTOM);
        posCell.setBorderColor(borderColor);
        posCell.setBorderWidth(0.5f);
        posCell.setPadding(cellPadding);
        posCell.setPaddingTop(cellPaddingTop);
        posCell.setPaddingBottom(cellPaddingBottom);
        posCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        posCell.setVerticalAlignment(Element.ALIGN_TOP);
        posCell.setNoWrap(true); // Kein Umbruch in Pos-Spalte
        table.addCell(posCell);

        // Menge + Einheit kompakt
        String mengeEinheit = nf.format(block.menge()) + " " + block.einheit();
        PdfPCell mengeCell = new PdfPCell(new Phrase(mengeEinheit, currentTextFont));
        mengeCell.setBorder(Rectangle.BOTTOM);
        mengeCell.setBorderColor(borderColor);
        mengeCell.setBorderWidth(0.5f);
        mengeCell.setPadding(cellPadding);
        mengeCell.setPaddingTop(cellPaddingTop);
        mengeCell.setPaddingBottom(cellPaddingBottom);
        mengeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        mengeCell.setVerticalAlignment(Element.ALIGN_TOP);
        table.addCell(mengeCell);

        // Bezeichnung: Titel + Beschreibung in EINER Zelle kombiniert
        // So bleiben Titel und Beschreibung zusammen und der Seitenumbruch
        // erfolgt nur bei Bedarf innerhalb der Beschreibung (wie bei Word).
        PdfPCell descCell = new PdfPCell();
        descCell.setBorder(Rectangle.BOTTOM);
        descCell.setBorderColor(borderColor);
        descCell.setBorderWidth(0.5f);
        descCell.setPadding(cellPadding);
        descCell.setPaddingTop(cellPaddingTop);
        descCell.setPaddingBottom(cellPaddingBottom);
        descCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        descCell.setVerticalAlignment(Element.ALIGN_TOP);
        
        // Titel (fett)
        String descText = block.beschreibung();
        if (isAlternative) {
            descText = "Alternativ: " + descText;
        }
        Paragraph titleParagraph = new Paragraph(descText, currentLabelFont);
        titleParagraph.setLeading(currentLabelFont.getSize() * 1.3f);
        descCell.addElement(titleParagraph);
        
        // Beschreibung direkt darunter in derselben Zelle
        if (hasDescription) {
            // Kleiner Abstand zwischen Titel und Beschreibung
            Paragraph spacer = new Paragraph(" ");
            spacer.setLeading(3f);
            descCell.addElement(spacer);
            
            try {
                java.util.List<com.lowagie.text.Element> elements = parseHtmlToElements(block.beschreibungHtml(), textColor);
                for (com.lowagie.text.Element el : elements) {
                    descCell.addElement(el);
                }
            } catch (Exception e) {
                // Fallback: Plain Text
                String plainText = stripHtmlForFallback(block.beschreibungHtml());
                descCell.addElement(new Phrase(plainText, currentTextFont));
            }
        }
        
        table.addCell(descCell);

        // Einzelpreis
        PdfPCell epCell = new PdfPCell(new Phrase(nf.format(block.einzelpreis()) + " €", currentTextFont));
        epCell.setBorder(Rectangle.BOTTOM);
        epCell.setBorderColor(borderColor);
        epCell.setBorderWidth(0.5f);
        epCell.setPadding(cellPadding);
        epCell.setPaddingTop(cellPaddingTop);
        epCell.setPaddingBottom(cellPaddingBottom);
        epCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        epCell.setVerticalAlignment(Element.ALIGN_TOP);
        table.addCell(epCell);

        // Gesamtpreis - hervorgehoben
        boolean hasDiscount = block.rabattProzent() != null && block.rabattProzent().compareTo(BigDecimal.ZERO) > 0;
        Font gpFont = isAlternative ? currentLabelFont : FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, textColor);
        
        PdfPCell gpCell = new PdfPCell();
        gpCell.setBorder(Rectangle.BOTTOM);
        gpCell.setBorderColor(borderColor);
        gpCell.setBorderWidth(0.5f);
        gpCell.setPadding(cellPadding);
        gpCell.setPaddingTop(cellPaddingTop);
        gpCell.setPaddingBottom(cellPaddingBottom);
        gpCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        gpCell.setVerticalAlignment(Element.ALIGN_TOP);
        
        if (isAlternative) {
            // Use Phrase directly so vertical alignment works
            gpCell.setPhrase(new Phrase("0,00 €", gpFont));
        } else if (hasDiscount) {
            // Originalpreis (durchgestrichen)
            BigDecimal originalGesamt = block.menge().multiply(block.einzelpreis()).setScale(2, java.math.RoundingMode.HALF_UP);
            Font strikeFont = FontFactory.getFont(FontFactory.HELVETICA, 9, textColor);
            Chunk strikeChunk = new Chunk(nf.format(originalGesamt) + " €", strikeFont);
            strikeChunk.setTextRise(0);
            // Simulate strikethrough manually via underline at middle of text
            strikeChunk.setUnderline(0.5f, 3.5f);
            Paragraph origLine = new Paragraph(strikeChunk);
            origLine.setAlignment(Element.ALIGN_RIGHT);
            origLine.setLeading(11f);
            gpCell.addElement(origLine);
            
            // Rabatt-Hinweis
            Font rabattFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, new Color(220, 38, 38)); // Rose-600
            Paragraph rabattLine = new Paragraph("-" + nf.format(block.rabattProzent()) + "% Rabatt", rabattFont);
            rabattLine.setAlignment(Element.ALIGN_RIGHT);
            rabattLine.setLeading(10f);
            gpCell.addElement(rabattLine);
            
            // Reduzierter Preis (fett)
            Paragraph discountedLine = new Paragraph(nf.format(block.gesamt()) + " €", gpFont);
            discountedLine.setAlignment(Element.ALIGN_RIGHT);
            discountedLine.setLeading(12f);
            gpCell.addElement(discountedLine);
        } else {
            // Use Phrase directly so setVerticalAlignment(ALIGN_TOP) works correctly
            gpCell.setPhrase(new Phrase(nf.format(block.gesamt()) + " €", gpFont));
        }
        table.addCell(gpCell);
    }
    
    /** Helper: Creates a right-aligned Paragraph for use inside PdfPCell.addElement(). */
    private Paragraph createRightAlignedParagraph(String text, Font font) {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_RIGHT);
        return p;
    }
    
    /**
     * Parst HTML zu iText-Elementen mit Unterstützung für Fett, Kursiv, Farben, Schriftgröße und Listen.
     * Schriftgrößen: Frontend sendet pt (10-20pt), werden direkt verwendet.
     * Zeilenhöhe wird dynamisch berechnet (1.3x der größten Schriftgröße).
     */
    private java.util.List<com.lowagie.text.Element> parseHtmlToElements(String html, Color defaultColor) {
        java.util.List<com.lowagie.text.Element> elements = new java.util.ArrayList<>();
        if (html == null || html.isBlank()) return elements;
        
        // Normalisiere HTML
        html = html.replace("<br>", "<br/>").replace("<br >", "<br/>");
        
        Paragraph currentParagraph = new Paragraph();
        // Dynamische Zeilenhöhe wird später gesetzt basierend auf max Schriftgröße
        float maxFontSizeInParagraph = 10f;
        
        // Einfacher HTML-Parser für grundlegende Tags
        String[] parts = html.split("(?=<)|(?<=>)");
        
        boolean isBold = false;
        boolean isItalic = false;
        boolean isUnderline = false;
        boolean isOrderedList = false; // Nummerierte Liste aktiv?
        boolean insideListItem = false; // Innerhalb eines <li>-Elements?
        int listItemCounter = 0; // Zähler für nummerierte Listen
        Color currentColor = defaultColor;
        Color currentBgColor = null; // Hintergrundfarbe (highlight)
        float currentFontSize = 10f; // Default 10pt
        java.util.Deque<Float> fontSizeStack = new java.util.ArrayDeque<>();
        java.util.Deque<Color> colorStack = new java.util.ArrayDeque<>();
        java.util.Deque<Color> bgColorStack = new java.util.LinkedList<>();
        
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;
            
            if (part.startsWith("<")) {
                String tag = part.toLowerCase();
                if (tag.equals("<strong>") || tag.equals("<b>")) {
                    isBold = true;
                } else if (tag.equals("</strong>") || tag.equals("</b>")) {
                    isBold = false;
                } else if (tag.equals("<em>") || tag.equals("<i>")) {
                    isItalic = true;
                } else if (tag.equals("</em>") || tag.equals("</i>")) {
                    isItalic = false;
                } else if (tag.equals("<u>")) {
                    isUnderline = true;
                } else if (tag.equals("</u>")) {
                    isUnderline = false;
                } else if (tag.startsWith("<span")) {
                    // Farbe und Schriftgröße aus style extrahieren
                    float newFontSize = currentFontSize;
                    Color newColor = currentColor;
                    
                    // Font-Size extrahieren
                    // TiptapEditor sendet pt (z.B. "12pt"), Formularwesen sendet auch pt
                    // Falls keine Einheit angegeben: als pt behandeln
                    java.util.regex.Pattern fontSizePattern = java.util.regex.Pattern.compile("font-size:\\s*([\\d.]+)(px|pt|em|rem)?");
                    java.util.regex.Matcher fontSizeMatcher = fontSizePattern.matcher(part);
                    if (fontSizeMatcher.find()) {
                        try {
                            float size = Float.parseFloat(fontSizeMatcher.group(1));
                            String unit = fontSizeMatcher.group(2);
                            // Konvertiere zu pt falls nötig
                            if ("px".equals(unit)) {
                                size = size * 0.75f; // px → pt (z.B. 16px → 12pt)
                            } else if ("em".equals(unit) || "rem".equals(unit)) {
                                size = size * 10f; // Basis 10pt
                            }
                            // pt oder keine Einheit: als pt behandeln, begrenzt auf 10-20pt
                            newFontSize = Math.max(10f, Math.min(20f, size));
                        } catch (NumberFormatException ignored) {}
                    }
                    
                    // Farbe extrahieren (nur text color, nicht background-color)
                    java.util.regex.Pattern colorPattern = java.util.regex.Pattern.compile("(?<!background-)color:\\s*([^;\"']+)");
                    java.util.regex.Matcher colorMatcher = colorPattern.matcher(part);
                    if (colorMatcher.find()) {
                        String colorStr = colorMatcher.group(1).trim();
                        newColor = parseColor(colorStr, currentColor);
                    }
                    
                    // Hintergrundfarbe extrahieren
                    Color newBgColor = currentBgColor;
                    java.util.regex.Pattern bgColorPattern = java.util.regex.Pattern.compile("background-color:\\s*([^;\"']+)");
                    java.util.regex.Matcher bgColorMatcher = bgColorPattern.matcher(part);
                    if (bgColorMatcher.find()) {
                        String bgColorStr = bgColorMatcher.group(1).trim();
                        newBgColor = parseColor(bgColorStr, null);
                    }
                    
                    // Stack für verschachtelte spans
                    fontSizeStack.push(currentFontSize);
                    colorStack.push(currentColor);
                    bgColorStack.push(currentBgColor);
                    currentFontSize = newFontSize;
                    currentColor = newColor;
                    currentBgColor = newBgColor;
                    
                } else if (tag.equals("</span>")) {
                    // Stack pop für verschachtelte spans
                    if (!fontSizeStack.isEmpty()) {
                        currentFontSize = fontSizeStack.pop();
                    }
                    if (!colorStack.isEmpty()) {
                        currentColor = colorStack.pop();
                    }
                    if (!bgColorStack.isEmpty()) {
                        currentBgColor = bgColorStack.pop();
                    }
                } else if (tag.startsWith("<mark")) {
                    // TipTap Highlight: <mark data-color="..." style="background-color: ...">
                    bgColorStack.push(currentBgColor);
                    Color markColor = new Color(255, 255, 0); // Default gelb
                    java.util.regex.Pattern bgPattern = java.util.regex.Pattern.compile("(?:background-color|data-color)[:=]\\s*[\"']?([^;\"']+)");
                    java.util.regex.Matcher bgMatcher = bgPattern.matcher(part);
                    if (bgMatcher.find()) {
                        Color parsed = parseColor(bgMatcher.group(1).trim(), null);
                        if (parsed != null) markColor = parsed;
                    }
                    currentBgColor = markColor;
                } else if (tag.equals("</mark>")) {
                    currentBgColor = bgColorStack.isEmpty() ? null : bgColorStack.pop();
                } else if (tag.equals("<br/>") || tag.equals("<br>")) {
                    currentParagraph.add(Chunk.NEWLINE);
                } else if (tag.equals("<p>") || tag.startsWith("<p ")) {
                    // Innerhalb eines <li>: <p> ignorieren (TipTap wraps li-Inhalt in <p>)
                    if (!insideListItem) {
                        // Öffnendes <p> (auch mit style-Attributen wie text-align)
                        if (!currentParagraph.isEmpty()) {
                            currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                            currentParagraph.setSpacingAfter(2f);
                            elements.add(currentParagraph);
                            currentParagraph = new Paragraph();
                            maxFontSizeInParagraph = 10f;
                        }
                    }
                    // Text-Align aus style extrahieren
                    if (tag.contains("text-align")) {
                        if (tag.contains("text-align: center") || tag.contains("text-align:center")) {
                            currentParagraph.setAlignment(Element.ALIGN_CENTER);
                        } else if (tag.contains("text-align: right") || tag.contains("text-align:right")) {
                            currentParagraph.setAlignment(Element.ALIGN_RIGHT);
                        } else if (tag.contains("text-align: justify") || tag.contains("text-align:justify")) {
                            currentParagraph.setAlignment(Element.ALIGN_JUSTIFIED);
                        }
                    }
                } else if (tag.equals("</p>")) {
                    // Innerhalb eines <li>: </p> ignorieren, <li>-Ende kümmert sich um den Abschluss
                    if (!insideListItem) {
                        if (!currentParagraph.isEmpty()) {
                            // Dynamische Zeilenhöhe setzen
                            currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                            // Absatzabstand für echte Paragraphen-Trennung
                            currentParagraph.setSpacingAfter(2f);
                            elements.add(currentParagraph);
                            currentParagraph = new Paragraph();
                            maxFontSizeInParagraph = 10f;
                        } else {
                            // Leerer Paragraph (<p></p>) erzeugt einen sichtbaren Absatz
                            Paragraph emptyP = new Paragraph(" ");
                            emptyP.setLeading(10f);
                            emptyP.setSpacingAfter(2f);
                            elements.add(emptyP);
                        }
                    }
                } else if (tag.equals("<ul>")) {
                    // Ungeordnete Liste starten
                    isOrderedList = false;
                    listItemCounter = 0;
                    if (!currentParagraph.isEmpty()) {
                        currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                        elements.add(currentParagraph);
                        currentParagraph = new Paragraph();
                        maxFontSizeInParagraph = 10f;
                    }
                } else if (tag.equals("<ol>")) {
                    // Nummerierte Liste starten
                    isOrderedList = true;
                    listItemCounter = 0;
                    if (!currentParagraph.isEmpty()) {
                        currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                        elements.add(currentParagraph);
                        currentParagraph = new Paragraph();
                        maxFontSizeInParagraph = 10f;
                    }
                } else if (tag.equals("</ul>") || tag.equals("</ol>")) {
                    // Liste beenden
                    isOrderedList = false;
                    listItemCounter = 0;
                    if (!currentParagraph.isEmpty()) {
                        currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                        elements.add(currentParagraph);
                        currentParagraph = new Paragraph();
                        maxFontSizeInParagraph = 10f;
                    }
                } else if (tag.equals("<li>")) {
                    // Listenpunkt starten - vorherigen Paragraph abschließen
                    insideListItem = true;
                    if (!currentParagraph.isEmpty()) {
                        currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                        elements.add(currentParagraph);
                    }
                    currentParagraph = new Paragraph();
                    maxFontSizeInParagraph = 10f;
                    // Bullet oder Nummer hinzufügen
                    Font bulletFont = FontFactory.getFont(FontFactory.HELVETICA, currentFontSize, currentColor);
                    if (isOrderedList) {
                        listItemCounter++;
                        currentParagraph.add(new Chunk("  " + listItemCounter + ".  ", bulletFont));
                    } else {
                        currentParagraph.add(new Chunk("  •  ", bulletFont));
                    }
                } else if (tag.equals("</li>")) {
                    // Listenpunkt beenden - Zeilenumbruch
                    insideListItem = false;
                    if (!currentParagraph.isEmpty()) {
                        currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                        elements.add(currentParagraph);
                        currentParagraph = new Paragraph();
                        maxFontSizeInParagraph = 10f;
                    }
                }
            } else {
                // Text-Content
                String text = part
                    .replace("&nbsp;", " ")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">");
                
                if (!text.isEmpty()) {
                    int fontStyle = Font.NORMAL;
                    if (isBold && isItalic) fontStyle = Font.BOLDITALIC;
                    else if (isBold) fontStyle = Font.BOLD;
                    else if (isItalic) fontStyle = Font.ITALIC;
                    
                    Font font = FontFactory.getFont(FontFactory.HELVETICA, currentFontSize, fontStyle, currentColor);
                    if (isUnderline) {
                        font.setStyle(font.getStyle() | Font.UNDERLINE);
                    }
                    Chunk chunk = new Chunk(text, font);
                    if (currentBgColor != null) {
                        chunk.setBackground(currentBgColor);
                    }
                    currentParagraph.add(chunk);
                    
                    // Track max font size für dynamische Zeilenhöhe
                    if (currentFontSize > maxFontSizeInParagraph) {
                        maxFontSizeInParagraph = currentFontSize;
                    }
                }
            }
        }
        
        if (!currentParagraph.isEmpty()) {
            // Dynamische Zeilenhöhe: 1.3x der größten Schriftgröße im Paragraph
            currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
            elements.add(currentParagraph);
        }
        
        return elements;
    }

    /**
     * Erweiterte Version: Parst HTML zu iText-Elementen mit konfigurierbaren Standard-Werten
     * für Schriftgröße und Fett-Markierung, plus Unterstützung für Base64-Images.
     *
     * Wird für TEXT-Blöcke im Dokument-Editor verwendet, bei denen Block-Level-Defaults
     * (fontSize, fett) mit Inline-Formatierungen aus dem TiptapEditor kombiniert werden.
     *
     * @param html             HTML-Inhalt aus dem TiptapEditor
     * @param defaultColor     Standard-Textfarbe
     * @param defaultFontSize  Standard-Schriftgröße (aus Block-Einstellungen, 10-20pt)
     * @param defaultBold      Standard-Fett (aus Block-Einstellungen)
     */
    private java.util.List<com.lowagie.text.Element> parseHtmlToElements(
            String html, Color defaultColor, float defaultFontSize, boolean defaultBold) {
        java.util.List<com.lowagie.text.Element> elements = new java.util.ArrayList<>();
        if (html == null || html.isBlank()) return elements;

        // Normalisiere HTML
        html = html.replace("<br>", "<br/>").replace("<br >", "<br/>");

        // --- 1. Base64-Bilder extrahieren und durch Platzhalter ersetzen ---
        java.util.List<com.lowagie.text.Image> extractedImages = new java.util.ArrayList<>();
        // Capture full img tag (group 0) to extract width/height, plus base64 data (groups 1+2)
        Pattern imgPattern = Pattern.compile(
                "<img([^>]+)src=[\"']data:image/([^;]+);base64,([^\"']+)[\"'][^>]*>",
                Pattern.CASE_INSENSITIVE);
        // Pattern to extract width from img tag attributes or inline style
        Pattern widthAttrPattern = Pattern.compile("\\bwidth=[\"'](\\d+)[\"']", Pattern.CASE_INSENSITIVE);
        Pattern heightAttrPattern = Pattern.compile("\\bheight=[\"'](\\d+)[\"']", Pattern.CASE_INSENSITIVE);
        Pattern styleWidthPattern = Pattern.compile("width:\\s*(\\d+)px", Pattern.CASE_INSENSITIVE);
        Pattern styleHeightPattern = Pattern.compile("height:\\s*(\\d+)px", Pattern.CASE_INSENSITIVE);

        String imgPlaceholderPrefix = "\uFFFDIMG_";
        StringBuffer sb = new StringBuffer();
        Matcher imgMatcher = imgPattern.matcher(html);
        while (imgMatcher.find()) {
            try {
                String fullTag = imgMatcher.group(0);
                String base64Data = imgMatcher.group(3);
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(imageBytes);

                // Try to read width/height from the HTML attributes (set by TiptapEditor ResizableImage)
                float targetWidth = -1;
                float targetHeight = -1;

                Matcher wAttr = widthAttrPattern.matcher(fullTag);
                if (wAttr.find()) {
                    targetWidth = Float.parseFloat(wAttr.group(1)) * 0.75f; // px -> pt
                }
                Matcher hAttr = heightAttrPattern.matcher(fullTag);
                if (hAttr.find()) {
                    targetHeight = Float.parseFloat(hAttr.group(1)) * 0.75f; // px -> pt
                }
                // Fallback: check inline style
                if (targetWidth < 0) {
                    Matcher sw = styleWidthPattern.matcher(fullTag);
                    if (sw.find()) {
                        targetWidth = Float.parseFloat(sw.group(1)) * 0.75f; // px -> pt
                    }
                }
                if (targetHeight < 0) {
                    Matcher sh = styleHeightPattern.matcher(fullTag);
                    if (sh.find()) {
                        targetHeight = Float.parseFloat(sh.group(1)) * 0.75f; // px -> pt
                    }
                }

                // Apply dimensions: editor-specified size takes priority
                float maxWidth = 400f;
                if (targetWidth > 0 && targetHeight > 0) {
                    // Beides explizit gesetzt – trotzdem maxWidth einhalten
                    float w = Math.min(targetWidth, maxWidth);
                    float h = (targetWidth > maxWidth) ? targetHeight * (maxWidth / targetWidth) : targetHeight;
                    img.scaleAbsolute(w, h);
                } else if (targetWidth > 0) {
                    float scale = Math.min(targetWidth, maxWidth) / img.getWidth();
                    img.scaleAbsolute(img.getWidth() * scale, img.getHeight() * scale);
                } else {
                    // Keine expliziten Dimensionen – immer auf maxWidth begrenzen
                    if (img.getWidth() > maxWidth) {
                        float scale = maxWidth / img.getWidth();
                        img.scaleAbsolute(maxWidth, img.getHeight() * scale);
                    }
                }

                extractedImages.add(img);
                imgMatcher.appendReplacement(sb, imgPlaceholderPrefix + (extractedImages.size() - 1) + "\uFFFD");
            } catch (Exception e) {
                log.warn("Fehler beim Extrahieren eines eingebetteten Bildes: {}", e.getMessage());
                imgMatcher.appendReplacement(sb, "");
            }
        }
        imgMatcher.appendTail(sb);
        html = sb.toString();

        // --- 2. Rich-Text-Parsing (wie die bestehende Methode, aber mit konfigurierbaren Defaults) ---
        Paragraph currentParagraph = new Paragraph();
        float maxFontSizeInParagraph = defaultFontSize;

        String[] parts = html.split("(?=<)|(?<=>)");

        boolean isBold = defaultBold;
        boolean isItalic = false;
        boolean isUnderline = false;
        boolean isOrderedList = false;
        boolean insideListItem = false;
        int listItemCounter = 0;
        Color currentColor = defaultColor;
        Color currentBgColor = null; // Hintergrundfarbe (highlight)
        float currentFontSize = defaultFontSize;
        java.util.Deque<Float> fontSizeStack = new java.util.ArrayDeque<>();
        java.util.Deque<Color> colorStack = new java.util.ArrayDeque<>();
        java.util.Deque<Color> bgColorStack = new java.util.LinkedList<>();
        // Track ob Bold durch ein Tag geändert wurde (um Block-Default zu respektieren)
        java.util.Deque<Boolean> boldStack = new java.util.ArrayDeque<>();

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            if (part.startsWith("<")) {
                String tag = part.toLowerCase();
                if (tag.equals("<strong>") || tag.equals("<b>")) {
                    boldStack.push(isBold);
                    isBold = true;
                } else if (tag.equals("</strong>") || tag.equals("</b>")) {
                    isBold = boldStack.isEmpty() ? defaultBold : boldStack.pop();
                } else if (tag.equals("<em>") || tag.equals("<i>")) {
                    isItalic = true;
                } else if (tag.equals("</em>") || tag.equals("</i>")) {
                    isItalic = false;
                } else if (tag.equals("<u>")) {
                    isUnderline = true;
                } else if (tag.equals("</u>")) {
                    isUnderline = false;
                } else if (tag.startsWith("<span")) {
                    float newFontSize = currentFontSize;
                    Color newColor = currentColor;

                    java.util.regex.Pattern fontSizePattern = java.util.regex.Pattern.compile(
                            "font-size:\\s*([\\d.]+)(px|pt|em|rem)?");
                    java.util.regex.Matcher fontSizeMatcher = fontSizePattern.matcher(part);
                    if (fontSizeMatcher.find()) {
                        try {
                            float size = Float.parseFloat(fontSizeMatcher.group(1));
                            String unit = fontSizeMatcher.group(2);
                            if ("px".equals(unit)) {
                                size = size * 0.75f;
                            } else if ("em".equals(unit) || "rem".equals(unit)) {
                                size = size * defaultFontSize;
                            }
                            newFontSize = Math.max(10f, Math.min(20f, size));
                        } catch (NumberFormatException ignored) {}
                    }

                    java.util.regex.Pattern colorPattern = java.util.regex.Pattern.compile(
                            "(?<!background-)color:\\s*([^;\"']+)");
                    java.util.regex.Matcher colorMatcher = colorPattern.matcher(part);
                    if (colorMatcher.find()) {
                        String colorStr = colorMatcher.group(1).trim();
                        newColor = parseColor(colorStr, currentColor);
                    }

                    // Hintergrundfarbe extrahieren
                    Color newBgColor = currentBgColor;
                    java.util.regex.Pattern bgColorPattern = java.util.regex.Pattern.compile(
                            "background-color:\\s*([^;\"']+)");
                    java.util.regex.Matcher bgColorMatcher = bgColorPattern.matcher(part);
                    if (bgColorMatcher.find()) {
                        String bgColorStr = bgColorMatcher.group(1).trim();
                        newBgColor = parseColor(bgColorStr, null);
                    }

                    // Bold aus font-weight im span?
                    boolean newBold = isBold;
                    if (part.toLowerCase().contains("font-weight")) {
                        java.util.regex.Pattern fwPattern = java.util.regex.Pattern.compile(
                                "font-weight:\\s*(bold|[7-9]00)");
                        if (fwPattern.matcher(part.toLowerCase()).find()) {
                            newBold = true;
                        }
                    }

                    fontSizeStack.push(currentFontSize);
                    colorStack.push(currentColor);
                    bgColorStack.push(currentBgColor);
                    boldStack.push(isBold);
                    currentFontSize = newFontSize;
                    currentColor = newColor;
                    currentBgColor = newBgColor;
                    isBold = newBold;

                } else if (tag.equals("</span>")) {
                    if (!fontSizeStack.isEmpty()) currentFontSize = fontSizeStack.pop();
                    if (!colorStack.isEmpty()) currentColor = colorStack.pop();
                    if (!bgColorStack.isEmpty()) currentBgColor = bgColorStack.pop();
                    if (!boldStack.isEmpty()) isBold = boldStack.pop();
                } else if (tag.startsWith("<mark")) {
                    // TipTap Highlight: <mark data-color="..." style="background-color: ...">
                    bgColorStack.push(currentBgColor);
                    Color markColor = new Color(255, 255, 0); // Default gelb
                    java.util.regex.Pattern bgPattern = java.util.regex.Pattern.compile("(?:background-color|data-color)[:=]\\s*[\"']?([^;\"']+)");
                    java.util.regex.Matcher bgMatcher = bgPattern.matcher(part);
                    if (bgMatcher.find()) {
                        Color parsed = parseColor(bgMatcher.group(1).trim(), null);
                        if (parsed != null) markColor = parsed;
                    }
                    currentBgColor = markColor;
                } else if (tag.equals("</mark>")) {
                    currentBgColor = bgColorStack.isEmpty() ? null : bgColorStack.pop();
                } else if (tag.equals("<br/>") || tag.equals("<br>")) {
                    currentParagraph.add(Chunk.NEWLINE);
                } else if (tag.equals("<p>") || tag.startsWith("<p ")) {
                    // Innerhalb eines <li>: <p> ignorieren (TipTap wraps li-Inhalt in <p>)
                    if (!insideListItem) {
                        // Öffnendes <p> (auch mit style-Attributen wie text-align)
                        if (!currentParagraph.isEmpty()) {
                            currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                            currentParagraph.setSpacingAfter(2f);
                            elements.add(currentParagraph);
                            currentParagraph = new Paragraph();
                            maxFontSizeInParagraph = defaultFontSize;
                        }
                    }
                    // Text-Align aus style extrahieren
                    if (tag.contains("text-align")) {
                        if (tag.contains("text-align: center") || tag.contains("text-align:center")) {
                            currentParagraph.setAlignment(Element.ALIGN_CENTER);
                        } else if (tag.contains("text-align: right") || tag.contains("text-align:right")) {
                            currentParagraph.setAlignment(Element.ALIGN_RIGHT);
                        } else if (tag.contains("text-align: justify") || tag.contains("text-align:justify")) {
                            currentParagraph.setAlignment(Element.ALIGN_JUSTIFIED);
                        }
                    }
                } else if (tag.equals("</p>")) {
                    // Innerhalb eines <li>: </p> ignorieren
                    if (!insideListItem) {
                        if (!currentParagraph.isEmpty()) {
                            currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                            // Absatzabstand für echte Paragraphen-Trennung
                            currentParagraph.setSpacingAfter(2f);
                            elements.add(currentParagraph);
                            currentParagraph = new Paragraph();
                            maxFontSizeInParagraph = defaultFontSize;
                        } else {
                            // Leerer Paragraph (<p></p>) erzeugt einen sichtbaren Absatz
                            Paragraph emptyP = new Paragraph(" ");
                            emptyP.setLeading(defaultFontSize * 1.0f);
                            emptyP.setSpacingAfter(2f);
                            elements.add(emptyP);
                        }
                    }
                } else if (tag.equals("<ul>")) {
                    isOrderedList = false;
                    listItemCounter = 0;
                    if (!currentParagraph.isEmpty()) {
                        currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                        elements.add(currentParagraph);
                        currentParagraph = new Paragraph();
                        maxFontSizeInParagraph = defaultFontSize;
                    }
                } else if (tag.equals("<ol>")) {
                    isOrderedList = true;
                    listItemCounter = 0;
                    if (!currentParagraph.isEmpty()) {
                        currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                        elements.add(currentParagraph);
                        currentParagraph = new Paragraph();
                        maxFontSizeInParagraph = defaultFontSize;
                    }
                } else if (tag.equals("</ul>") || tag.equals("</ol>")) {
                    isOrderedList = false;
                    listItemCounter = 0;
                    if (!currentParagraph.isEmpty()) {
                        currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                        elements.add(currentParagraph);
                        currentParagraph = new Paragraph();
                        maxFontSizeInParagraph = defaultFontSize;
                    }
                } else if (tag.equals("<li>")) {
                    insideListItem = true;
                    if (!currentParagraph.isEmpty()) {
                        currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                        elements.add(currentParagraph);
                    }
                    currentParagraph = new Paragraph();
                    maxFontSizeInParagraph = defaultFontSize;
                    Font bulletFont = FontFactory.getFont(FontFactory.HELVETICA, currentFontSize, currentColor);
                    if (isOrderedList) {
                        listItemCounter++;
                        currentParagraph.add(new Chunk("  " + listItemCounter + ".  ", bulletFont));
                    } else {
                        currentParagraph.add(new Chunk("  \u2022  ", bulletFont));
                    }
                } else if (tag.equals("</li>")) {
                    insideListItem = false;
                    if (!currentParagraph.isEmpty()) {
                        currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                        elements.add(currentParagraph);
                        currentParagraph = new Paragraph();
                        maxFontSizeInParagraph = defaultFontSize;
                    }
                }
            } else {
                // Text-Content: prüfe auf Bild-Platzhalter
                String text = part
                        .replace("&nbsp;", " ")
                        .replace("&quot;", "\"")
                        .replace("&#39;", "'")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">");

                // Bild-Platzhalter verarbeiten
                if (text.contains(imgPlaceholderPrefix)) {
                    java.util.regex.Pattern phPattern = java.util.regex.Pattern.compile(
                            "\uFFFDIMG_(\\d+)\uFFFD");
                    java.util.regex.Matcher phMatcher = phPattern.matcher(text);
                    int lastEnd = 0;
                    while (phMatcher.find()) {
                        // Text vor dem Platzhalter
                        String before = text.substring(lastEnd, phMatcher.start());
                        if (!before.isEmpty()) {
                            addRichChunk(currentParagraph, before, currentFontSize, isBold, isItalic, isUnderline, currentColor, currentBgColor);
                            if (currentFontSize > maxFontSizeInParagraph) maxFontSizeInParagraph = currentFontSize;
                        }
                        // Paragraph abschließen, Bild einfügen, neuen Paragraph starten
                        if (!currentParagraph.isEmpty()) {
                            currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
                            elements.add(currentParagraph);
                            currentParagraph = new Paragraph();
                            maxFontSizeInParagraph = defaultFontSize;
                        }
                        int imgIndex = Integer.parseInt(phMatcher.group(1));
                        if (imgIndex < extractedImages.size()) {
                            Paragraph imgP = new Paragraph();
                            imgP.add(new Chunk(extractedImages.get(imgIndex), 0, 0, true));
                            imgP.setSpacingBefore(6f);
                            imgP.setSpacingAfter(6f);
                            elements.add(imgP);
                        }
                        lastEnd = phMatcher.end();
                    }
                    // Rest-Text nach letztem Platzhalter
                    String remaining = text.substring(lastEnd);
                    if (!remaining.isEmpty()) {
                        addRichChunk(currentParagraph, remaining, currentFontSize, isBold, isItalic, isUnderline, currentColor, currentBgColor);
                        if (currentFontSize > maxFontSizeInParagraph) maxFontSizeInParagraph = currentFontSize;
                    }
                } else if (!text.isEmpty()) {
                    addRichChunk(currentParagraph, text, currentFontSize, isBold, isItalic, isUnderline, currentColor, currentBgColor);
                    if (currentFontSize > maxFontSizeInParagraph) maxFontSizeInParagraph = currentFontSize;
                }
            }
        }

        if (!currentParagraph.isEmpty()) {
            currentParagraph.setLeading(maxFontSizeInParagraph * 1.3f);
            elements.add(currentParagraph);
        }

        return elements;
    }

    /**
     * Hilfsmethode: Erstellt einen formatierten Chunk und fügt ihn dem Paragraph hinzu.
     */
    private void addRichChunk(Paragraph paragraph, String text, float fontSize,
                              boolean bold, boolean italic, boolean underline, Color color, Color bgColor) {
        int fontStyle = Font.NORMAL;
        if (bold && italic) fontStyle = Font.BOLDITALIC;
        else if (bold) fontStyle = Font.BOLD;
        else if (italic) fontStyle = Font.ITALIC;

        Font font = FontFactory.getFont(FontFactory.HELVETICA, fontSize, fontStyle, color);
        if (underline) {
            font.setStyle(font.getStyle() | Font.UNDERLINE);
        }
        Chunk chunk = new Chunk(text, font);
        if (bgColor != null) {
            chunk.setBackground(bgColor);
        }
        paragraph.add(chunk);
    }

    /**
     * Parst Farb-String (hex oder rgb) zu Color
     */
    private Color parseColor(String colorStr, Color defaultColor) {
        try {
            colorStr = colorStr.trim();
            if (colorStr.startsWith("#")) {
                return Color.decode(colorStr);
            } else if (colorStr.startsWith("rgb")) {
                java.util.regex.Pattern rgbPattern = java.util.regex.Pattern.compile("rgb\\s*\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)");
                java.util.regex.Matcher m = rgbPattern.matcher(colorStr);
                if (m.find()) {
                    return new Color(
                        Integer.parseInt(m.group(1)),
                        Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3))
                    );
                }
            }
        } catch (Exception e) {
            // Ignorieren, Default verwenden
        }
        return defaultColor;
    }
    
    /**
     * Fallback: Entfernt HTML-Tags für Plain-Text
     */
    private String stripHtmlForFallback(String html) {
        if (html == null) return "";
        return html
            .replaceAll("<br\\s*/?>", "\n")
            .replaceAll("<p>", "")
            .replaceAll("</p>", "\n")
            .replaceAll("<[^>]+>", "")
            .replaceAll("&nbsp;", " ")
            .replaceAll("&quot;", "\"")
            .replaceAll("&#39;", "'")
            .replaceAll("&amp;", "&")
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .trim();
    }

    // addLeistungsTabelle REMOVED - replaced by addMixedContent with inline service
    // rows

    /**
     * Rendert den Summenblock (Netto, MwSt, Brutto) am Ende der Positionen.
     * Wichtig: Dieser Block wird als Einheit zusammengehalten und nicht über Seiten verteilt.
     * 
     * LÖSUNG: Alles in eine einzige Tabellenzelle wrappen, damit OpenPDF es
     * als atomare Einheit behandelt und nie zerstückelt.
     */
    private void addSummenBlock(ColumnText ct, List<ContentBlockDto> blocks, BigDecimal globalRabattProzent, AbrechnungsverlaufPdfDto abrechnungsverlauf, BigDecimal overrideBetragNetto, String dokumentTyp) throws DocumentException {
        // Nettosumme aus allen SERVICE-Blöcken berechnen (exclude optional)
        BigDecimal positionenNetto = BigDecimal.ZERO;
        for (ContentBlockDto block : blocks) {
            if (block.isService() && block.gesamt() != null && !block.optional()) {
                positionenNetto = positionenNetto.add(block.gesamt());
            }
        }

        // Ist Abschlagsrechnung? (overrideBetragNetto gesetzt)
        boolean isAbschlag = overrideBetragNetto != null;
        boolean isSchlussrechnung = "Schlussrechnung".equalsIgnoreCase(dokumentTyp);

        // Für Abschlagsrechnungen: override betragNetto verwenden
        BigDecimal netto = isAbschlag ? overrideBetragNetto : positionenNetto;

        // Gesamtauftragssumme ermitteln (Basisdokument oder Positionensumme)
        BigDecimal gesamtAuftragssumme = positionenNetto;
        boolean hasBasisdokument = abrechnungsverlauf != null && abrechnungsverlauf.basisdokumentBetragNetto() != null
                && abrechnungsverlauf.basisdokumentBetragNetto().compareTo(BigDecimal.ZERO) > 0;
        if (hasBasisdokument) {
            gesamtAuftragssumme = abrechnungsverlauf.basisdokumentBetragNetto();
        }

        // Globalen Rabatt anwenden falls vorhanden
        boolean hasGlobalRabatt = globalRabattProzent != null && globalRabattProzent.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal rabattBetrag = BigDecimal.ZERO;
        BigDecimal nettoNachRabatt = netto;
        if (hasGlobalRabatt) {
            rabattBetrag = netto.multiply(globalRabattProzent).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
            nettoNachRabatt = netto.subtract(rabattBetrag);
        }

        // MwSt und Brutto berechnen (auf Basis des rabattierten Netto)
        BigDecimal mwstSatz = new BigDecimal("0.19");
        BigDecimal mwst = nettoNachRabatt.multiply(mwstSatz).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal brutto = nettoNachRabatt.add(mwst);

        // Formatierung
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.GERMANY);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        Color textColor = new Color(0, 0, 0); // Schwarz
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10, textColor);
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, textColor);
        Color lineColor = new Color(30, 41, 59); // Slate-800

        // === WRAPPER-TABELLE für Summenblock ===
        PdfPTable wrapperTable = new PdfPTable(1);
        wrapperTable.setWidthPercentage(100);
        wrapperTable.setSpacingBefore(15f);
        wrapperTable.setSpacingAfter(10f);
        wrapperTable.setKeepTogether(true);
        wrapperTable.setSplitLate(true);
        wrapperTable.setSplitRows(false);

        // === Innere Summentabelle (immer 3 Spalten: Label | Prozent | Betrag) ===
        PdfPTable sumTable = new PdfPTable(new float[] { 6f, 2f, 2f });
        sumTable.setWidthPercentage(100);

        // Trennlinie
        PdfPCell lineCell = new PdfPCell();
        lineCell.setBorder(Rectangle.TOP);
        lineCell.setBorderColor(lineColor);
        lineCell.setBorderWidth(1.5f);
        lineCell.setColspan(3);
        lineCell.setFixedHeight(8f);
        sumTable.addCell(lineCell);

        // Vorherige Rechnungen vorhanden?
        boolean hasAbrechnung = abrechnungsverlauf != null && abrechnungsverlauf.positionen() != null
                && !abrechnungsverlauf.positionen().isEmpty();

        if (hasBasisdokument) {
            // === INTEGRIERTER FLOW: Auftragsübersicht + Abzüge + Zahlbetrag ===
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 9, textColor);
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");

            // --- Gesamtauftragssumme (netto) ---
            String gesamtLabel = "Gesamtauftragssumme (netto)";
            if (abrechnungsverlauf.basisdokumentTyp() != null && !abrechnungsverlauf.basisdokumentTyp().isBlank()) {
                gesamtLabel += " laut " + abrechnungsverlauf.basisdokumentTyp();
                if (abrechnungsverlauf.basisdokumentNummer() != null && !abrechnungsverlauf.basisdokumentNummer().isBlank()) {
                    gesamtLabel += " Nr. " + abrechnungsverlauf.basisdokumentNummer();
                }
                if (abrechnungsverlauf.basisdokumentDatum() != null) {
                    gesamtLabel += " vom " + abrechnungsverlauf.basisdokumentDatum().format(dateFmt);
                }
            }
            PdfPCell auftragsLabel = new PdfPCell(new Phrase(gesamtLabel, normalFont));
            auftragsLabel.setBorder(Rectangle.NO_BORDER);
            auftragsLabel.setPaddingTop(4f);
            auftragsLabel.setPaddingBottom(3f);
            auftragsLabel.setColspan(2);
            sumTable.addCell(auftragsLabel);

            PdfPCell auftragsValue = new PdfPCell(new Phrase(nf.format(gesamtAuftragssumme) + " €", normalFont));
            auftragsValue.setBorder(Rectangle.NO_BORDER);
            auftragsValue.setPaddingTop(4f);
            auftragsValue.setPaddingBottom(3f);
            auftragsValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumTable.addCell(auftragsValue);

            // --- abzgl. bereits abgerechnete Rechnungen (einzeln aufgelistet) ---
            BigDecimal bereitsAbgerechnetNetto = BigDecimal.ZERO;
            if (hasAbrechnung) {
                int posCounter = 0;
                for (AbrechnungspositionPdfDto pos : abrechnungsverlauf.positionen()) {
                    posCounter++;
                    BigDecimal posNetto = pos.betragNetto() != null ? pos.betragNetto() : BigDecimal.ZERO;
                    bereitsAbgerechnetNetto = bereitsAbgerechnetNetto.add(posNetto);

                    String typLabel = pos.typ() != null ? pos.typ() : "Rechnung";
                    String posLabel = "abzgl. " + posCounter + ". "
                            + typLabel.substring(0, 1).toUpperCase() + typLabel.substring(1).toLowerCase()
                            + " Nr. " + pos.dokumentNummer();

                    PdfPCell posLabelCell = new PdfPCell(new Phrase(posLabel, smallFont));
                    posLabelCell.setBorder(Rectangle.NO_BORDER);
                    posLabelCell.setPaddingTop(1f);
                    posLabelCell.setPaddingBottom(1f);
                    posLabelCell.setPaddingLeft(8f);
                    posLabelCell.setColspan(2);
                    sumTable.addCell(posLabelCell);

                    PdfPCell posValueCell = new PdfPCell(new Phrase("− " + nf.format(posNetto) + " €", smallFont));
                    posValueCell.setBorder(Rectangle.NO_BORDER);
                    posValueCell.setPaddingTop(1f);
                    posValueCell.setPaddingBottom(1f);
                    posValueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    sumTable.addCell(posValueCell);
                }
            }

            // --- Schlussrechnung: Rechnungsbetrag = Restbetrag (Gesamt - bereits abgerechnet) ---
            if (isSchlussrechnung) {
                BigDecimal schlussrechnungNetto = gesamtAuftragssumme.subtract(bereitsAbgerechnetNetto);
                nettoNachRabatt = schlussrechnungNetto;
                // MwSt und Brutto neu berechnen auf Basis des Restbetrags
                mwst = nettoNachRabatt.multiply(mwstSatz).setScale(2, java.math.RoundingMode.HALF_UP);
                brutto = nettoNachRabatt.add(mwst);
            }

            // --- abzgl. diese Rechnung (netto) ---
            String dieseRechnungLabel;
            if (isAbschlag) {
                dieseRechnungLabel = "abzgl. dieser Abschlag (netto)";
            } else if (isSchlussrechnung) {
                dieseRechnungLabel = "abzgl. diese Schlussrechnung (netto)";
            } else {
                dieseRechnungLabel = "abzgl. diese Rechnung (netto)";
            }
            PdfPCell dieseLabel = new PdfPCell(new Phrase(dieseRechnungLabel, normalFont));
            dieseLabel.setBorder(Rectangle.NO_BORDER);
            dieseLabel.setPaddingTop(3f);
            dieseLabel.setPaddingBottom(3f);
            dieseLabel.setColspan(2);
            sumTable.addCell(dieseLabel);

            PdfPCell dieseValue = new PdfPCell(new Phrase("− " + nf.format(nettoNachRabatt) + " €", normalFont));
            dieseValue.setBorder(Rectangle.NO_BORDER);
            dieseValue.setPaddingTop(3f);
            dieseValue.setPaddingBottom(3f);
            dieseValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumTable.addCell(dieseValue);

            // --- Trennlinie ---
            PdfPCell restLine = new PdfPCell();
            restLine.setBorder(Rectangle.TOP);
            restLine.setBorderColor(lineColor);
            restLine.setBorderWidth(0.75f);
            restLine.setColspan(3);
            restLine.setFixedHeight(6f);
            sumTable.addCell(restLine);

            // --- Verbleibender Restbetrag (netto) ---
            BigDecimal restbetragNetto = gesamtAuftragssumme.subtract(bereitsAbgerechnetNetto).subtract(nettoNachRabatt);
            PdfPCell restLabel = new PdfPCell(new Phrase("Verbleibender Restbetrag (netto)", boldFont));
            restLabel.setBorder(Rectangle.NO_BORDER);
            restLabel.setPaddingTop(2f);
            restLabel.setPaddingBottom(8f);
            restLabel.setColspan(2);
            sumTable.addCell(restLabel);

            PdfPCell restValue = new PdfPCell(new Phrase(nf.format(restbetragNetto) + " €", boldFont));
            restValue.setBorder(Rectangle.NO_BORDER);
            restValue.setPaddingTop(2f);
            restValue.setPaddingBottom(8f);
            restValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumTable.addCell(restValue);

            // --- Trennlinie vor Zahlbetrag-Block ---
            PdfPCell zahlTrenn = new PdfPCell();
            zahlTrenn.setBorder(Rectangle.TOP);
            zahlTrenn.setBorderColor(lineColor);
            zahlTrenn.setBorderWidth(1.5f);
            zahlTrenn.setColspan(3);
            zahlTrenn.setFixedHeight(8f);
            sumTable.addCell(zahlTrenn);

            // --- Rechnungsbetrag (netto) ---
            String rechnungLabel;
            if (isAbschlag) {
                rechnungLabel = "Abschlagsbetrag (netto)";
            } else if (isSchlussrechnung) {
                rechnungLabel = "Schlussrechnungsbetrag (netto)";
            } else {
                rechnungLabel = "Rechnungsbetrag (netto)";
            }
            PdfPCell rbLabel = new PdfPCell(new Phrase(rechnungLabel, normalFont));
            rbLabel.setBorder(Rectangle.NO_BORDER);
            rbLabel.setPaddingTop(4f);
            rbLabel.setPaddingBottom(3f);
            sumTable.addCell(rbLabel);

            PdfPCell rbEmpty = new PdfPCell(new Phrase("", normalFont));
            rbEmpty.setBorder(Rectangle.NO_BORDER);
            sumTable.addCell(rbEmpty);

            PdfPCell rbValue = new PdfPCell(new Phrase(nf.format(nettoNachRabatt) + " €", normalFont));
            rbValue.setBorder(Rectangle.NO_BORDER);
            rbValue.setPaddingTop(4f);
            rbValue.setPaddingBottom(3f);
            rbValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumTable.addCell(rbValue);

            // Globaler Rabatt (falls vorhanden)
            if (hasGlobalRabatt) {
                Font rabattFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, new Color(220, 38, 38));

                PdfPCell rabattLabel = new PdfPCell(new Phrase("Rabatt", rabattFont));
                rabattLabel.setBorder(Rectangle.NO_BORDER);
                rabattLabel.setPaddingTop(3f);
                rabattLabel.setPaddingBottom(3f);
                sumTable.addCell(rabattLabel);

                PdfPCell rabattPercentCell = new PdfPCell(new Phrase(nf.format(globalRabattProzent) + " %", rabattFont));
                rabattPercentCell.setBorder(Rectangle.NO_BORDER);
                rabattPercentCell.setPaddingTop(3f);
                rabattPercentCell.setPaddingBottom(3f);
                rabattPercentCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                sumTable.addCell(rabattPercentCell);

                PdfPCell rabattValueCell = new PdfPCell(new Phrase("-" + nf.format(rabattBetrag) + " €", rabattFont));
                rabattValueCell.setBorder(Rectangle.NO_BORDER);
                rabattValueCell.setPaddingTop(3f);
                rabattValueCell.setPaddingBottom(3f);
                rabattValueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                sumTable.addCell(rabattValueCell);
            }

            // --- zzgl. Umsatzsteuer ---
            PdfPCell ustLabel = new PdfPCell(new Phrase("zzgl. Umsatzsteuer", normalFont));
            ustLabel.setBorder(Rectangle.NO_BORDER);
            ustLabel.setPaddingTop(3f);
            ustLabel.setPaddingBottom(3f);
            sumTable.addCell(ustLabel);

            PdfPCell ustPercent = new PdfPCell(new Phrase("19 %", normalFont));
            ustPercent.setBorder(Rectangle.NO_BORDER);
            ustPercent.setPaddingTop(3f);
            ustPercent.setPaddingBottom(3f);
            ustPercent.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumTable.addCell(ustPercent);

            PdfPCell ustValue = new PdfPCell(new Phrase(nf.format(mwst) + " €", normalFont));
            ustValue.setBorder(Rectangle.NO_BORDER);
            ustValue.setPaddingTop(3f);
            ustValue.setPaddingBottom(3f);
            ustValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumTable.addCell(ustValue);

            // --- Spacer ---
            PdfPCell spacer = new PdfPCell(new Phrase("", normalFont));
            spacer.setBorder(Rectangle.NO_BORDER);
            spacer.setFixedHeight(6f);
            spacer.setColspan(3);
            sumTable.addCell(spacer);

            // --- Zahlbetrag (fett) ---
            PdfPCell zahlLabel = new PdfPCell(new Phrase("Zahlbetrag", boldFont));
            zahlLabel.setBorder(Rectangle.NO_BORDER);
            zahlLabel.setPaddingTop(3f);
            zahlLabel.setPaddingBottom(6f);
            sumTable.addCell(zahlLabel);

            PdfPCell zahlEmpty = new PdfPCell(new Phrase("", normalFont));
            zahlEmpty.setBorder(Rectangle.NO_BORDER);
            sumTable.addCell(zahlEmpty);

            PdfPCell zahlValue = new PdfPCell(new Phrase(nf.format(brutto) + " €", boldFont));
            zahlValue.setBorder(Rectangle.NO_BORDER);
            zahlValue.setPaddingTop(3f);
            zahlValue.setPaddingBottom(6f);
            zahlValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumTable.addCell(zahlValue);

            // --- Abschlusslinie ---
            PdfPCell bottomLine = new PdfPCell();
            bottomLine.setBorder(Rectangle.TOP);
            bottomLine.setBorderColor(lineColor);
            bottomLine.setBorderWidth(1.5f);
            bottomLine.setColspan(3);
            bottomLine.setFixedHeight(2f);
            sumTable.addCell(bottomLine);

        } else {
            // === STANDARD FLOW: Netto + MwSt + Gesamt (ohne Basisdokument) ===

            // Nettosumme
            PdfPCell nettoLabel = new PdfPCell(new Phrase("Nettosumme", normalFont));
            nettoLabel.setBorder(Rectangle.NO_BORDER);
            nettoLabel.setPaddingTop(6f);
            nettoLabel.setPaddingBottom(3f);
            sumTable.addCell(nettoLabel);

            PdfPCell emptyCell1 = new PdfPCell(new Phrase("", normalFont));
            emptyCell1.setBorder(Rectangle.NO_BORDER);
            sumTable.addCell(emptyCell1);

            PdfPCell nettoValue = new PdfPCell(new Phrase(nf.format(nettoNachRabatt) + " €", normalFont));
            nettoValue.setBorder(Rectangle.NO_BORDER);
            nettoValue.setPaddingTop(6f);
            nettoValue.setPaddingBottom(3f);
            nettoValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumTable.addCell(nettoValue);

            // Globaler Rabatt (falls vorhanden)
            if (hasGlobalRabatt) {
                Font rabattFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, new Color(220, 38, 38));

                PdfPCell rabattLabel = new PdfPCell(new Phrase("Rabatt", rabattFont));
                rabattLabel.setBorder(Rectangle.NO_BORDER);
                rabattLabel.setPaddingTop(3f);
                rabattLabel.setPaddingBottom(3f);
                sumTable.addCell(rabattLabel);

                PdfPCell rabattPercentCell = new PdfPCell(new Phrase(nf.format(globalRabattProzent) + " %", rabattFont));
                rabattPercentCell.setBorder(Rectangle.NO_BORDER);
                rabattPercentCell.setPaddingTop(3f);
                rabattPercentCell.setPaddingBottom(3f);
                rabattPercentCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                sumTable.addCell(rabattPercentCell);

                PdfPCell rabattValueCell = new PdfPCell(new Phrase("-" + nf.format(rabattBetrag) + " €", rabattFont));
                rabattValueCell.setBorder(Rectangle.NO_BORDER);
                rabattValueCell.setPaddingTop(3f);
                rabattValueCell.setPaddingBottom(3f);
                rabattValueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                sumTable.addCell(rabattValueCell);
            }

            // Umsatzsteuer
            PdfPCell mwstLabel = new PdfPCell(new Phrase("Umsatzsteuer", normalFont));
            mwstLabel.setBorder(Rectangle.NO_BORDER);
            mwstLabel.setPaddingTop(3f);
            mwstLabel.setPaddingBottom(3f);
            sumTable.addCell(mwstLabel);

            PdfPCell mwstPercent = new PdfPCell(new Phrase("19 %", normalFont));
            mwstPercent.setBorder(Rectangle.NO_BORDER);
            mwstPercent.setPaddingTop(3f);
            mwstPercent.setPaddingBottom(3f);
            mwstPercent.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumTable.addCell(mwstPercent);

            PdfPCell mwstValue = new PdfPCell(new Phrase(nf.format(mwst) + " €", normalFont));
            mwstValue.setBorder(Rectangle.NO_BORDER);
            mwstValue.setPaddingTop(3f);
            mwstValue.setPaddingBottom(3f);
            mwstValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumTable.addCell(mwstValue);

            // Leere Trennzeile
            PdfPCell spacer = new PdfPCell(new Phrase("", normalFont));
            spacer.setBorder(Rectangle.NO_BORDER);
            spacer.setFixedHeight(6f);
            spacer.setColspan(3);
            sumTable.addCell(spacer);

            // Gesamtsumme (fett)
            String gesamtLabel = isAbschlag ? "Zahlbetrag" : "Gesamtsumme";
            PdfPCell bruttoLabel = new PdfPCell(new Phrase(gesamtLabel, boldFont));
            bruttoLabel.setBorder(Rectangle.NO_BORDER);
            bruttoLabel.setPaddingTop(3f);
            bruttoLabel.setPaddingBottom(6f);
            sumTable.addCell(bruttoLabel);

            PdfPCell emptyCell3 = new PdfPCell(new Phrase("", normalFont));
            emptyCell3.setBorder(Rectangle.NO_BORDER);
            sumTable.addCell(emptyCell3);

            PdfPCell bruttoValue = new PdfPCell(new Phrase(nf.format(brutto) + " €", boldFont));
            bruttoValue.setBorder(Rectangle.NO_BORDER);
            bruttoValue.setPaddingTop(3f);
            bruttoValue.setPaddingBottom(6f);
            bruttoValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
            sumTable.addCell(bruttoValue);

            // Abschlusslinie nach Gesamtsumme
            PdfPCell bottomLine = new PdfPCell();
            bottomLine.setBorder(Rectangle.TOP);
            bottomLine.setBorderColor(lineColor);
            bottomLine.setBorderWidth(1.5f);
            bottomLine.setColspan(3);
            bottomLine.setFixedHeight(2f);
            sumTable.addCell(bottomLine);
        }

        // Sum-Table in Wrapper einfügen
        PdfPCell wrapperCell = new PdfPCell(sumTable);
        wrapperCell.setBorder(Rectangle.NO_BORDER);
        wrapperCell.setPadding(0);
        wrapperTable.addCell(wrapperCell);

        ct.addElement(wrapperTable);

    }

    /**
     * Rendert die Bauabschnitt-Übersicht im CLOSURE-Block.
     * Listet alle Bauabschnitte mit Position und Summe auf,
     * plus "Sonstige Leistungen" für Services außerhalb von Bauabschnitten.
     * Nur gerendert wenn es mindestens einen Bauabschnitt gibt.
     */
    private void addClosureBreakdown(ColumnText ct, List<ContentBlockDto> blocks, int closureIndex,
                                      NumberFormat nf, Color accentColor, Color textColor) throws DocumentException {
        // Collect section summaries and loose services
        record SectionSummary(String label, String pos, BigDecimal total) {}
        List<SectionSummary> sections = new java.util.ArrayList<>();
        BigDecimal sonstigeTotal = BigDecimal.ZERO;
        
        String currentSectionLabel = null;
        String currentSectionPos = null;
        BigDecimal currentSectionTotal = BigDecimal.ZERO;
        boolean inSection = false;

        for (int k = 0; k < closureIndex; k++) {
            ContentBlockDto b = blocks.get(k);
            if (b.isSectionHeader()) {
                // Save previous section if exists
                if (inSection) {
                    sections.add(new SectionSummary(currentSectionLabel, currentSectionPos, currentSectionTotal));
                }
                currentSectionLabel = b.sectionLabel() != null && !b.sectionLabel().isBlank() 
                    ? b.sectionLabel() : "Bauabschnitt";
                currentSectionPos = b.pos() != null ? b.pos() : "";
                currentSectionTotal = BigDecimal.ZERO;
                inSection = true;
            } else if (b.isSubtotal()) {
                // SUBTOTAL ends a section - save it
                if (inSection) {
                    sections.add(new SectionSummary(currentSectionLabel, currentSectionPos, currentSectionTotal));
                    inSection = false;
                }
            } else if (b.isService() && !b.optional()) {
                BigDecimal serviceTotal = b.gesamt() != null ? b.gesamt() : BigDecimal.ZERO;
                if (inSection) {
                    currentSectionTotal = currentSectionTotal.add(serviceTotal);
                } else {
                    sonstigeTotal = sonstigeTotal.add(serviceTotal);
                }
            }
        }
        // Flush last section if still open
        if (inSection) {
            sections.add(new SectionSummary(currentSectionLabel, currentSectionPos, currentSectionTotal));
        }

        // Only render breakdown if there are actual Bauabschnitte
        boolean hasSections = !sections.isEmpty();
        boolean hasSonstige = sonstigeTotal.compareTo(BigDecimal.ZERO) > 0;
        if (!hasSections) return;

        // === Wrapper table for the breakdown ===
        PdfPTable breakdownTable = new PdfPTable(new float[] { 0.8f, 6.2f, 3f });
        breakdownTable.setWidthPercentage(100);
        breakdownTable.setSpacingBefore(10f);
        breakdownTable.setSpacingAfter(4f);

        // Header row
        Color lineColor = new Color(30, 41, 59); // Slate-800 – minimalistisch schwarz
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, textColor);

        PdfPCell hPos = new PdfPCell(new Phrase("Pos.", headerFont));
        hPos.setBorder(Rectangle.BOTTOM);
        hPos.setBorderColor(lineColor);
        hPos.setBorderWidth(1.5f);
        hPos.setPadding(4f);
        hPos.setPaddingBottom(6f);
        hPos.setHorizontalAlignment(Element.ALIGN_CENTER);
        breakdownTable.addCell(hPos);

        PdfPCell hLabel = new PdfPCell(new Phrase("Bauabschnitt", headerFont));
        hLabel.setBorder(Rectangle.BOTTOM);
        hLabel.setBorderColor(lineColor);
        hLabel.setBorderWidth(1.5f);
        hLabel.setPadding(4f);
        hLabel.setPaddingBottom(6f);
        breakdownTable.addCell(hLabel);

        PdfPCell hTotal = new PdfPCell(new Phrase("Summe", headerFont));
        hTotal.setBorder(Rectangle.BOTTOM);
        hTotal.setBorderColor(lineColor);
        hTotal.setBorderWidth(1.5f);
        hTotal.setPadding(4f);
        hTotal.setPaddingBottom(6f);
        hTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        breakdownTable.addCell(hTotal);

        Color borderColor = new Color(226, 232, 240); // Slate-200
        Font posFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, textColor);
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 10, textColor);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, textColor);

        // Section rows
        for (SectionSummary sec : sections) {
            PdfPCell posCell = new PdfPCell(new Phrase(sec.pos(), posFont));
            posCell.setBorder(Rectangle.BOTTOM);
            posCell.setBorderColor(borderColor);
            posCell.setBorderWidth(0.5f);
            posCell.setPadding(4f);
            posCell.setPaddingTop(5f);
            posCell.setPaddingBottom(5f);
            posCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            breakdownTable.addCell(posCell);

            PdfPCell labelCell = new PdfPCell(new Phrase(sec.label(), labelFont));
            labelCell.setBorder(Rectangle.BOTTOM);
            labelCell.setBorderColor(borderColor);
            labelCell.setBorderWidth(0.5f);
            labelCell.setPadding(4f);
            labelCell.setPaddingTop(5f);
            labelCell.setPaddingBottom(5f);
            breakdownTable.addCell(labelCell);

            PdfPCell totalCell = new PdfPCell(new Phrase(nf.format(sec.total()) + " €", valueFont));
            totalCell.setBorder(Rectangle.BOTTOM);
            totalCell.setBorderColor(borderColor);
            totalCell.setBorderWidth(0.5f);
            totalCell.setPadding(4f);
            totalCell.setPaddingTop(5f);
            totalCell.setPaddingBottom(5f);
            totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            breakdownTable.addCell(totalCell);
        }

        // Sonstige Leistungen row – nur wenn es auch Bauabschnitte gibt
        if (hasSonstige && hasSections) {
            Font sonstigeFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, textColor);

            PdfPCell posCell = new PdfPCell(new Phrase("", posFont));
            posCell.setBorder(Rectangle.BOTTOM);
            posCell.setBorderColor(borderColor);
            posCell.setBorderWidth(0.5f);
            posCell.setPadding(4f);
            posCell.setPaddingTop(5f);
            posCell.setPaddingBottom(5f);
            breakdownTable.addCell(posCell);

            PdfPCell labelCell = new PdfPCell(new Phrase("Sonstige Leistungen", sonstigeFont));
            labelCell.setBorder(Rectangle.BOTTOM);
            labelCell.setBorderColor(borderColor);
            labelCell.setBorderWidth(0.5f);
            labelCell.setPadding(4f);
            labelCell.setPaddingTop(5f);
            labelCell.setPaddingBottom(5f);
            breakdownTable.addCell(labelCell);

            PdfPCell totalCell = new PdfPCell(new Phrase(nf.format(sonstigeTotal) + " €", valueFont));
            totalCell.setBorder(Rectangle.BOTTOM);
            totalCell.setBorderColor(borderColor);
            totalCell.setBorderWidth(0.5f);
            totalCell.setPadding(4f);
            totalCell.setPaddingTop(5f);
            totalCell.setPaddingBottom(5f);
            totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            breakdownTable.addCell(totalCell);
        }

        ct.addElement(breakdownTable);
    }

    private void addSchlusstext(ColumnText ct, String schlusstext) throws DocumentException {
        if (schlusstext == null || schlusstext.isBlank())
            return;

        Paragraph p = new Paragraph(schlusstext, FONT_NORMAL);
        p.setSpacingBefore(15f);
        ct.addElement(p);
    }




    /**
     * Rendert HTML-Inhalt mit eingebetteten Bildern (Base64).
     * Extrahiert img-Tags und rendert sie als PDF-Bilder.
     * Schriftgröße: Frontend sendet pt (10-20pt), wird direkt verwendet.
     */
    private void renderHtmlWithImages(ColumnText ct, String html, ContentBlockDto block, Color textColor) 
            throws DocumentException {
        // Pattern für img-Tags mit Base64-Data-URL (capture full tag for width/height extraction)
        Pattern imgPattern = Pattern.compile("<img([^>]+)src=[\"']data:image/([^;]+);base64,([^\"']+)[\"'][^>]*>", 
                Pattern.CASE_INSENSITIVE);
        Pattern widthAttrP = Pattern.compile("\\bwidth=[\"'](\\d+)[\"']", Pattern.CASE_INSENSITIVE);
        Pattern heightAttrP = Pattern.compile("\\bheight=[\"'](\\d+)[\"']", Pattern.CASE_INSENSITIVE);
        Pattern styleWidthP = Pattern.compile("width:\\s*(\\d+)px", Pattern.CASE_INSENSITIVE);
        Pattern styleHeightP = Pattern.compile("height:\\s*(\\d+)px", Pattern.CASE_INSENSITIVE);
        
        // Schriftgröße: Frontend sendet pt (10-20pt), direkt verwenden
        // Begrenzt auf 10-20pt für Konsistenz
        float fontSizePt = Math.max(10f, Math.min(20f, block.fontSize()));
        
        // Dynamische Zeilenhöhe: 1.3x der Schriftgröße
        float leading = fontSizePt * 1.3f;
        
        Font font = block.fett()
                ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, fontSizePt, textColor)
                : FontFactory.getFont(FontFactory.HELVETICA, fontSizePt, textColor);
        
        Matcher matcher = imgPattern.matcher(html);
        int lastEnd = 0;
        
        while (matcher.find()) {
            // Text vor dem Bild rendern
            String textBefore = html.substring(lastEnd, matcher.start());
            String plainText = textBefore.replaceAll("<[^>]*>", "").trim();
            if (!plainText.isEmpty()) {
                Paragraph textP = new Paragraph(plainText, font);
                textP.setLeading(leading);
                textP.setSpacingAfter(4f);
                ct.addElement(textP);
            }
            
            // Bild rendern
            try {
                String fullTag = matcher.group(0);
                String base64Data = matcher.group(3);
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(imageBytes);
                
                // Read width/height from HTML attributes (set by TiptapEditor)
                float targetWidth = -1;
                float targetHeight = -1;
                Matcher wm = widthAttrP.matcher(fullTag);
                if (wm.find()) targetWidth = Float.parseFloat(wm.group(1)) * 0.75f;
                Matcher hm = heightAttrP.matcher(fullTag);
                if (hm.find()) targetHeight = Float.parseFloat(hm.group(1)) * 0.75f;
                if (targetWidth < 0) {
                    Matcher sw = styleWidthP.matcher(fullTag);
                    if (sw.find()) targetWidth = Float.parseFloat(sw.group(1)) * 0.75f;
                }
                if (targetHeight < 0) {
                    Matcher sh = styleHeightP.matcher(fullTag);
                    if (sh.find()) targetHeight = Float.parseFloat(sh.group(1)) * 0.75f;
                }
                
                // Apply dimensions: editor-specified size takes priority
                float maxWidth = 400f;
                if (targetWidth > 0 && targetHeight > 0) {
                    float w = Math.min(targetWidth, maxWidth);
                    float h = (targetWidth > maxWidth) ? targetHeight * (maxWidth / targetWidth) : targetHeight;
                    img.scaleAbsolute(w, h);
                } else if (targetWidth > 0) {
                    float scale = Math.min(targetWidth, maxWidth) / img.getWidth();
                    img.scaleAbsolute(img.getWidth() * scale, img.getHeight() * scale);
                } else {
                    if (img.getWidth() > maxWidth) {
                        float scale = maxWidth / img.getWidth();
                        img.scaleAbsolute(maxWidth, img.getHeight() * scale);
                    }
                }
                
                // Als Paragraph mit Bild einfügen (damit es im Text-Flow bleibt)
                Paragraph imgParagraph = new Paragraph();
                imgParagraph.add(new Chunk(img, 0, 0, true));
                imgParagraph.setSpacingBefore(6f);
                imgParagraph.setSpacingAfter(6f);
                ct.addElement(imgParagraph);
                
            } catch (Exception e) {
                log.warn("Fehler beim Rendern eines eingebetteten Bildes: {}", e.getMessage());
            }
            
            lastEnd = matcher.end();
        }
        
        // Restlichen Text nach dem letzten Bild rendern
        if (lastEnd < html.length()) {
            String remainingText = html.substring(lastEnd).replaceAll("<[^>]*>", "").trim();
            if (!remainingText.isEmpty()) {
                Paragraph finalP = new Paragraph(remainingText, font);
                finalP.setLeading(leading);
                finalP.setSpacingAfter(6f);
                ct.addElement(finalP);
            }
        }
    }

    // ======================= Static Headers/Footers =======================

    private void renderBriefkopf(PdfContentByte cb, KopfdatenDto kopf, LayoutDto layout) throws DocumentException {
        // Positioniere Elemente absolut auf Seite 1

        // Adressfeld (typisch bei ca. y=700)
        ColumnText adresse = new ColumnText(cb);
        adresse.setSimpleColumn(50, 680, 300, 780);

        Paragraph absender = new Paragraph("Musterfirma GmbH • Musterstraße 1 • 12345 Musterstadt", FONT_SMALL);
        absender.setSpacingAfter(3f);
        adresse.addElement(absender);

        Paragraph kunde = new Paragraph(kopf.kundenName() + "\n" + kopf.kundenAdresse(), FONT_NORMAL);
        adresse.addElement(kunde);
        adresse.go();

        // Rechte Seite: Rechnungsinfo
        ColumnText info = new ColumnText(cb);
        info.setSimpleColumn(350, 680, 550, 780);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        Paragraph infoText = new Paragraph();
        infoText.add(new Chunk("Rechnung Nr. ", FONT_BOLD));
        infoText.add(new Chunk(kopf.rechnungsnummer() + "\n", FONT_NORMAL));
        infoText.add(new Chunk("Datum: ", FONT_BOLD));
        infoText.add(new Chunk(kopf.rechnungsDatum().format(fmt) + "\n", FONT_NORMAL));
        if (kopf.leistungsDatum() != null) {
            infoText.add(new Chunk("Leistungsdatum: ", FONT_BOLD));
            infoText.add(new Chunk(kopf.leistungsDatum().format(fmt), FONT_NORMAL));
        }
        info.addElement(infoText);
        info.go();

        // Betreff
        ColumnText betreff = new ColumnText(cb);
        betreff.setSimpleColumn(50, 620, 550, 660);
        Paragraph betreffText = new Paragraph(kopf.betreff(), FONT_TITLE);
        betreff.addElement(betreffText);
        betreff.go();
    }

    private void renderFooter(PdfContentByte cb, LayoutDto layout) throws DocumentException {
        // Fußzeile mit Bankverbindung etc.
        ColumnText footer = new ColumnText(cb);
        footer.setSimpleColumn(50, 30, 550, 100);

        Paragraph footerText = new Paragraph();
        footerText.setAlignment(Element.ALIGN_CENTER);
        footerText.add(new Chunk("Musterfirma GmbH | Musterstraße 1, 12345 Musterstadt\n", FONT_SMALL));
        footerText.add(
                new Chunk("Bankverbindung: Sparkasse Musterstadt | IBAN: DE12 3456 7890 1234 5678 90 | BIC: ABCDEFGH\n",
                        FONT_SMALL));
        footerText.add(new Chunk(
                "Geschäftsführer: Max Mustermann | Amtsgericht Musterstadt HRB 12345 | USt-IdNr.: DE123456789", FONT_SMALL));

        footer.addElement(footerText);
        footer.go();
    }

    private void renderFolgeSeitenKopf(PdfContentByte cb, String rechnungsnummer, int currentPage, PdfTemplate totalPages,
                                       BaseFont[] totalPagesBaseFontHolder, float[] totalPagesFontSizeHolder) throws DocumentException {
        // Left: Document number
        ColumnText headerLeft = new ColumnText(cb);
        headerLeft.setSimpleColumn(50, 800, 350, 830);
        Paragraph docInfo = new Paragraph("Dokumentnummer: " + rechnungsnummer, FONT_HEADER);
        headerLeft.addElement(docInfo);
        headerLeft.go();

        // Right: Page number with total
        RectDto pageInfoRect = new RectDto(350, 800, 550, 830);
        renderTextWithTotalPagesTemplate(
            cb,
            pageInfoRect,
            FONT_HEADER,
            "Seite: " + currentPage + " / ",
            "",
            totalPages,
            totalPagesBaseFontHolder,
            totalPagesFontSizeHolder);

        // Separator line
        cb.setLineWidth(0.5f);
        cb.setColorStroke(new Color(220, 38, 38));
        cb.moveTo(50, 798);
        cb.lineTo(550, 798);
        cb.stroke();
    }

    /**
     * Zerlegt HTML-Inhalt in eine Liste von iText-Elementen (Paragraphs, Images).
     * Unterstützt Base64-Images aus Tiptap.
     */
    private java.util.List<Element> parseHtmlToElements(String html, Font font) {
        java.util.List<Element> elements = new java.util.ArrayList<>();
        if (html == null || html.isBlank()) return elements;

        // Pattern für img-Tags mit Base64-Data-URL (capture full tag for width/height)
        Pattern imgPattern = Pattern.compile("<img([^>]+)src=[\"']data:image/([^;]+);base64,([^\"']+)[\"'][^>]*>", 
                Pattern.CASE_INSENSITIVE);
        Pattern widthAttrP = Pattern.compile("\\bwidth=[\"'](\\d+)[\"']", Pattern.CASE_INSENSITIVE);
        Pattern heightAttrP = Pattern.compile("\\bheight=[\"'](\\d+)[\"']", Pattern.CASE_INSENSITIVE);
        Pattern styleWidthP = Pattern.compile("width:\\s*(\\d+)px", Pattern.CASE_INSENSITIVE);
        Pattern styleHeightP = Pattern.compile("height:\\s*(\\d+)px", Pattern.CASE_INSENSITIVE);
        
        Matcher matcher = imgPattern.matcher(html);
        int lastEnd = 0;
        
        while (matcher.find()) {
            // Text vor dem Bild
            String textBefore = html.substring(lastEnd, matcher.start());
            // Einfaches HTML-Stripping für den Text-Teil
            String plainText = textBefore.replaceAll("<br\\s*/?>", "\n")
                                         .replaceAll("<p>", "\n")
                                         .replaceAll("</p>", "\n")
                                         .replaceAll("&nbsp;", " ")
                                         .replaceAll("<[^>]*>", "")
                                         .replaceAll("&quot;", "\"")
                                         .replaceAll("&#39;", "'")
                                         .replaceAll("&amp;", "&")
                                         .replaceAll("&lt;", "<")
                                         .replaceAll("&gt;", ">")
                                         .trim();
            if (!plainText.isEmpty()) {
                Paragraph p = new Paragraph(plainText, font);
                elements.add(p);
            }
            
            // Bild
            try {
                String fullTag = matcher.group(0);
                String base64Data = matcher.group(3);
                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(imageBytes);
                
                // Read width/height from HTML attributes (set by TiptapEditor)
                float targetWidth = -1;
                float targetHeight = -1;
                Matcher wm = widthAttrP.matcher(fullTag);
                if (wm.find()) targetWidth = Float.parseFloat(wm.group(1)) * 0.75f;
                Matcher hm = heightAttrP.matcher(fullTag);
                if (hm.find()) targetHeight = Float.parseFloat(hm.group(1)) * 0.75f;
                if (targetWidth < 0) {
                    Matcher sw = styleWidthP.matcher(fullTag);
                    if (sw.find()) targetWidth = Float.parseFloat(sw.group(1)) * 0.75f;
                }
                if (targetHeight < 0) {
                    Matcher sh = styleHeightP.matcher(fullTag);
                    if (sh.find()) targetHeight = Float.parseFloat(sh.group(1)) * 0.75f;
                }
                
                // Apply dimensions: editor-specified size takes priority
                float maxWidth = 450f;
                if (targetWidth > 0 && targetHeight > 0) {
                    float w = Math.min(targetWidth, maxWidth);
                    float h = (targetWidth > maxWidth) ? targetHeight * (maxWidth / targetWidth) : targetHeight;
                    img.scaleAbsolute(w, h);
                } else if (targetWidth > 0) {
                    float scale = Math.min(targetWidth, maxWidth) / img.getWidth();
                    img.scaleAbsolute(img.getWidth() * scale, img.getHeight() * scale);
                } else {
                    if (img.getWidth() > maxWidth) {
                        float scale = maxWidth / img.getWidth();
                        img.scaleAbsolute(maxWidth, img.getHeight() * scale);
                    }
                }
                
                // Bild in Paragraph wrappen
                Paragraph imgP = new Paragraph();
                imgP.add(new Chunk(img, 0, 0, true));
                // Etwas Abstand
                imgP.setSpacingBefore(5f);
                imgP.setSpacingAfter(5f);
                elements.add(imgP);
                
            } catch (Exception e) {
                log.warn("Fehler beim Parsen von Bild: {}", e.getMessage());
            }
            
            lastEnd = matcher.end();
        }
        
        // Rest-Text
        if (lastEnd < html.length()) {
            String remaining = html.substring(lastEnd);
            String plainText = remaining.replaceAll("<br\\s*/?>", "\n")
                                        .replaceAll("<p>", "\n")
                                        .replaceAll("</p>", "\n")
                                        .replaceAll("&nbsp;", " ")
                                        .replaceAll("<[^>]*>", "")
                                        .replaceAll("&quot;", "\"")
                                        .replaceAll("&#39;", "'")
                                        .replaceAll("&amp;", "&")
                                        .replaceAll("&lt;", "<")
                                        .replaceAll("&gt;", ">")
                                        .trim();
            if (!plainText.isEmpty()) {
                elements.add(new Paragraph(plainText, font));
            }
        }
        
        return elements;
    }

    // ======================= Helper Methods =======================

    private PdfPCell makeCell(String text, Font font, Color bg, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(bg);
        cell.setPadding(5f);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private PdfPCell makeSimpleCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5f);
        cell.setHorizontalAlignment(alignment);
        cell.setBorderWidth(0.5f);
        return cell;
    }

    /**
     * Moderne Zelle mit nur Bottom-Border
     */
    private PdfPCell makeModernCell(String text, Font font, Color borderColor, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(10f);
        cell.setHorizontalAlignment(alignment);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(borderColor);
        cell.setBorderWidth(1f);
        return cell;
    }

    // ======================= Default Layout =======================

    /**
     * Standard-Layout für A4, wenn kein benutzerdefiniertes Layout vorhanden
     */
    public static LayoutDto getDefaultLayout() {
        return new LayoutDto(
                new RectDto(50, 120, 550, 600), // Seite 1: kleinerer Bereich (Briefkopf + Footer nehmen Platz)
                new RectDto(50, 50, 550, 780), // Folgeseiten: fast ganze Seite
                new RectDto(50, 750, 550, 840), // Header-Bereich
                new RectDto(50, 20, 550, 100), // Footer-Bereich
                null // Logo-Pfad
        );
    }

    // ======================= FormBlock Integration (Formularwesen)
    // =======================

    // A4 Maße in Points (1 inch = 72 points)
    private static final float A4_WIDTH = 595f;
    private static final float A4_HEIGHT = 842f;

    /**
     * FormBlock aus dem Frontend - entspricht types.ts FormBlock
     * Koordinaten sind in CSS-Pixeln (top-left origin, wie im Frontend-Editor)
     */
    public record FormBlockDto(
            String id,
            String type, // 'table', 'logo', 'adresse', 'text', etc.
            int page,
            float x, // CSS X (von links)
            float y, // CSS Y (von oben)
            float width,
            float height,
            String content,
            java.util.Map<String, Object> styles) {
    }

    /**
     * Konvertiert einen Frontend FormBlock zu einem PDF Rectangle.
     * 
     * Frontend (CSS): Ursprung oben-links, y wächst nach unten
     * PDF (OpenPDF): Ursprung unten-links, y wächst nach oben
     * 
     * HINWEIS: Alle Maße sind in pt (Points), nicht px!
     * DIN A4 = 595 × 842 pt (= 210 × 297 mm)
     * 1 pt = 1/72 Zoll = 0.353 mm
     * 
     * @param block        FormBlock aus dem Formularwesen-Frontend
     * @param pageWidthPt  Breite der Vorlage in pt (595 für DIN A4)
     * @param pageHeightPt Höhe der Vorlage in pt (842 für DIN A4)
     * @return RectDto mit PDF-Koordinaten (llx, lly, urx, ury)
     */
    public static RectDto convertFormBlockToRect(FormBlockDto block, float pageWidthPt, float pageHeightPt) {
        // Skalierung: Frontend-Koordinaten → PDF-Points
        float scaleX = A4_WIDTH / pageWidthPt;
        float scaleY = A4_HEIGHT / pageHeightPt;

        // CSS-Koordinaten in PDF-Koordinaten umrechnen
        float pdfLlx = block.x() * scaleX;
        float pdfUrx = (block.x() + block.width()) * scaleX;

        // Y-Achse invertieren: CSS y=0 ist oben, PDF y=0 ist unten
        float cssBottom = block.y() + block.height();
        float pdfLly = A4_HEIGHT - (cssBottom * scaleY);
        float pdfUry = A4_HEIGHT - (block.y() * scaleY);

        return new RectDto(pdfLlx, pdfLly, pdfUrx, pdfUry);
    }

    /**
     * Erstellt ein LayoutDto aus FormBlock-Daten des Formularwesen-Editors.
     * Der "table"-Block definiert den Content-Bereich für Seite 1.
     * 
     * HINWEIS: Alle Maße sind in pt (Points)!
     * DIN A4 = 595 × 842 pt
     * 
     * @param blocks       Liste aller FormBlocks aus der Vorlage
     * @param pageWidthPt  Seitenbreite in pt (595 für DIN A4)
     * @param pageHeightPt Seitenhöhe in pt (842 für DIN A4)
     * @return LayoutDto mit konvertierten PDF-Koordinaten
     */
    public static LayoutDto createLayoutFromFormBlocks(
            java.util.List<FormBlockDto> blocks,
            float pageWidthPt,
            float pageHeightPt) {

        // Finde den Table-Block für den Content-Bereich
        FormBlockDto tableBlock = blocks.stream()
                .filter(b -> "table".equals(b.type()))
                .findFirst()
                .orElse(null);

        RectDto page1Rect;
        if (tableBlock != null) {
            page1Rect = convertFormBlockToRect(tableBlock, pageWidthPt, pageHeightPt);
        } else {
            // Fallback: Standard-Content-Bereich (in pt)
            page1Rect = new RectDto(50, 120, 550, 600);
        }

        // Finde den Table-Block für Seite 2
        FormBlockDto tableBlockPage2 = blocks.stream()
                .filter(b -> "table".equals(b.type()) && b.page() == 2)
                .findFirst()
                .orElse(null);

        RectDto page2Rect;
        if (tableBlockPage2 != null) {
            page2Rect = convertFormBlockToRect(tableBlockPage2, pageWidthPt, pageHeightPt);
        } else {
            // Fallback: Standard-Content-Bereich für Folgeseiten
            page2Rect = new RectDto(50, 50, 550, 780);
        }

        // Header/Footer bleiben standard
        RectDto headerRect = new RectDto(50, 750, 550, 840);
        RectDto footerRect = new RectDto(50, 20, 550, 100);

        return new LayoutDto(page1Rect, page2Rect, headerRect, footerRect, null);
    }

    /**
     * Löst Platzhalter wie {{DOKUMENTNUMMER}}, {{KUNDENADRESSE}}, {{DATUM}} etc. auf.
     * 
     * Unterstützte Platzhalter:
     *   {{DOKUMENTNUMMER}}       → Rechnungs-/Dokumentnummer
     *   {{RECHNUNGSNUMMER}}      → Alias für DOKUMENTNUMMER
     *   {{KUNDENNUMMER}}         → Kundennummer
     *   {{KUNDENADRESSE}}        → Vollständige Kundenadresse (mehrzeilig)
     *   {{KUNDENNAME}}           → Kundenname
     *   {{DATUM}}                → Rechnungs-/Dokumentdatum
     *   {{DOKUMENTTYP}}          → Typ des Dokuments (Rechnung, Angebot, etc.)
     *   {{BEZUGSDOKUMENT}}       → Bezugsdokument-Nummer
     *   {{BEZUGSDOKUMENTNUMMER}} → Alias für BEZUGSDOKUMENT
     *   {{PROJEKTNUMMER}}        → Projektnummer
     *   {{BAUVORHABEN}}          → Bauvorhaben-Bezeichnung
     *   {{BETREFF}}              → Betreff des Dokuments
     *   {{SEITENZAHL}}           → Wird separat in renderFormBlocks behandelt
     * 
     * @param text         Text mit {{PLATZHALTER}}-Tokens
     * @param kopf         Kopfdaten des Dokuments
     * @param currentPage  Aktuelle Seitennummer (für zukünftige Seitenzahl-Auflösung)
     * @return Text mit aufgelösten Platzhaltern
     */
    private String resolvePlaceholders(String text, KopfdatenDto kopf, int currentPage) {
        if (text == null || !text.contains("{{")) return text;

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d.M.yyyy");

        // Null-sichere Hilfsfunktion via inline Map
        java.util.Map<String, String> placeholders = new java.util.LinkedHashMap<>();
        placeholders.put("DOKUMENTNUMMER",       nullSafe(kopf.rechnungsnummer()));
        placeholders.put("RECHNUNGSNUMMER",      nullSafe(kopf.rechnungsnummer()));
        placeholders.put("KUNDENNUMMER",         nullSafe(kopf.kundennummer()));
        placeholders.put("KUNDENADRESSE",        nullSafe(kopf.kundenAdresse()));
        placeholders.put("KUNDENNAME",           nullSafe(kopf.kundenName()));
        placeholders.put("DATUM",                kopf.rechnungsDatum() != null ? kopf.rechnungsDatum().format(fmt) : "");
        placeholders.put("DOKUMENTTYP",          nullSafe(kopf.dokumentTyp()));
        placeholders.put("BEZUGSDOKUMENT",       nullSafe(kopf.bezugsdokument()));
        placeholders.put("BEZUGSDOKUMENTNUMMER", nullSafe(kopf.bezugsdokument()));
        placeholders.put("PROJEKTNUMMER",        nullSafe(kopf.projektnummer()));
        placeholders.put("BAUVORHABEN",          nullSafe(kopf.bauvorhaben()));
        placeholders.put("BETREFF",              nullSafe(kopf.betreff()));

        String result = text;
        for (var entry : placeholders.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    /** Null-sicherer String: null → "" */
    private static String nullSafe(String value) {
        return value != null ? value : "";
    }

    /**
     * Rendert die benutzerdefinierten Form-Blöcke an ihren absoluten Positionen.
     * 
     * Block-Typen:
     *   table        → Nur für Layout (Content-Bereich), wird nicht gerendert
     *   watermark    → Halbtransparenter 45°-Text (nur explizite Watermark-Blöcke)
     *   seitenzahl   → Dynamische Seitennummer mit Gesamtseiten-Template
     *   logo         → (wird als normaler Text gerendert, falls kein Bild)
     *   Alle anderen → Normaler Text mit Style-Unterstützung und Platzhalter-Auflösung
     * 
     * Seitenfilter:
     *   currentPage == 1 → Nur Blocks mit page=1
     *   currentPage > 1  → Nur Blocks mit page=2 (Folgeseiten-Template)
     *   Watermarks werden auf ALLEN Seiten gerendert
     */
    private void renderFormBlocks(PdfContentByte cb, List<FormBlockDto> blocks, int currentPage,
                                  PdfTemplate totalPages, float pageWidthPt, float pageHeightPt,
                                  KopfdatenDto kopfdaten,
                                  BaseFont[] totalPagesBaseFontHolder,
                                  float[] totalPagesFontSizeHolder) {
        if (blocks == null) return;

        log.debug("renderFormBlocks aufgerufen: {} Blöcke, currentPage={}", blocks.size(), currentPage);
        int templatePage = currentPage == 1 ? 1 : 2;

        for (FormBlockDto block : blocks) {
            // ── Skip: table-Blöcke dienen nur der Layout-Berechnung ──
            if ("table".equals(block.type())) continue;

            // ── Watermark-Erkennung: NUR explizite Watermark-Blöcke (ID oder Typ) ──
            boolean isWatermark = "watermark".equals(block.type())
                    || "watermark".equals(block.id());

            // ── Seitenfilter: Watermarks auf allen Seiten, Rest nur auf zugehöriger Seite ──
            if (block.page() != templatePage && !isWatermark) {
                log.debug("Block übersprungen (falsche Seite): id={}, type={}, blockPage={}, templatePage={}",
                        block.id(), block.type(), block.page(), templatePage);
                continue;
            }

            try {
                RectDto rect = convertFormBlockToRect(block, pageWidthPt, pageHeightPt);

                // Content holen und Platzhalter auflösen
                String text = block.content() != null ? block.content() : "";
                text = resolvePlaceholders(text, kopfdaten, currentPage);

                log.debug("Block rendern: id={}, type={}, page={}, pos=({},{} {}x{}), rect=({},{},{},{}), content='{}'",
                        block.id(), block.type(), block.page(),
                        block.x(), block.y(), block.width(), block.height(),
                        String.format("%.0f", rect.llx()), String.format("%.0f", rect.lly()),
                        String.format("%.0f", rect.urx()), String.format("%.0f", rect.ury()),
                        text.length() > 80 ? text.substring(0, 80) + "..." : text);

                // ── Font aus Styles parsen (oder Default) ──
                Font font = parseBlockFont(block);

                // ── Typ-spezifisches Rendering ──
                if (isWatermark) {
                    renderWatermark(cb, text, rect);
                } else if ("seitenzahl".equals(block.type())) {
                    renderSeitenzahl(cb, rect, font, currentPage, totalPages, totalPagesBaseFontHolder, totalPagesFontSizeHolder);
                } else if (text.contains("{{SEITENZAHL}}")) {
                    renderTextWithSeitenzahl(cb, text, rect, font, currentPage, totalPages, totalPagesBaseFontHolder, totalPagesFontSizeHolder);
                } else {
                    renderTextBlock(cb, text, rect, font);
                }

            } catch (Exception e) {
                log.warn("Fehler beim Rendern von Block {} (Typ={}): {}", block.id(), block.type(), e.getMessage());
            }
        }
    }

    /**
     * Parst Font-Informationen aus den Block-Styles.
     * Unterstützt fontSize, color (hex) und fontWeight (bold/700).
     */
    private Font parseBlockFont(FormBlockDto block) {
        if (block.styles() == null || block.styles().isEmpty()) {
            return FONT_NORMAL;
        }
        try {
            java.util.Map<String, Object> s = block.styles();
            float fontSize = s.containsKey("fontSize") ? ((Number) s.get("fontSize")).floatValue() : 10f;

            Color color = Color.BLACK;
            if (s.containsKey("color")) {
                String hex = String.valueOf(s.get("color"));
                if (hex.startsWith("#")) {
                    color = Color.decode(hex);
                }
            }

            int style = Font.NORMAL;
            if (s.containsKey("fontWeight")) {
                String fw = String.valueOf(s.get("fontWeight"));
                if ("bold".equalsIgnoreCase(fw) || "700".equals(fw)) {
                    style = Font.BOLD;
                }
            }

            return FontFactory.getFont(FontFactory.HELVETICA, fontSize, style, color);
        } catch (Exception ex) {
            log.warn("Fehler beim Parsen der Styles für Block {}: {}", block.id(), ex.getMessage());
            return FONT_NORMAL;
        }
    }

    /**
     * Rendert einen Watermark-Block: Halbtransparent, 45° gedreht, zentriert.
     */
    private void renderWatermark(PdfContentByte cb, String text, RectDto rect) {
        Font watermarkFont = FontFactory.getFont(
                FontFactory.HELVETICA_BOLD, 48, Font.BOLD, new Color(200, 200, 200, 100));
        float centerX = (rect.llx() + rect.urx()) / 2;
        float centerY = (rect.lly() + rect.ury()) / 2;
        ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                new Phrase(text, watermarkFont), centerX, centerY, 45);
    }

    /**
     * Rendert den Seitenzahl-Block: "Seite: X / Y" mit dynamischem Gesamtseiten-Template.
     * Verwendet adaptive Leading analog zu renderTextBlock.
     */
    private void renderSeitenzahl(PdfContentByte cb, RectDto rect, Font font,
                                  int currentPage, PdfTemplate totalPages,
                                  BaseFont[] totalPagesBaseFontHolder,
                                  float[] totalPagesFontSizeHolder) throws DocumentException {
        String prefix = "Seite: " + currentPage + " / ";
        renderTextWithTotalPagesTemplate(cb, rect, font, prefix, "", totalPages, totalPagesBaseFontHolder, totalPagesFontSizeHolder);
    }

    /**
     * Rendert einen Textblock der {{SEITENZAHL}}-Platzhalter enthält.
     * Der Platzhalter wird durch "X / Y" (mit Gesamtseiten-Template) ersetzt.
     * Verwendet adaptive Leading analog zu renderTextBlock.
     */
    private void renderTextWithSeitenzahl(PdfContentByte cb, String text, RectDto rect,
                                          Font font, int currentPage, PdfTemplate totalPages,
                                          BaseFont[] totalPagesBaseFontHolder,
                                          float[] totalPagesFontSizeHolder)
            throws DocumentException {
        String[] parts = text.split("\\{\\{SEITENZAHL\\}\\}", -1);
        if (parts.length == 2) {
            String normalizedPrefix = parts[0];
            String trimmedPrefix = normalizedPrefix.trim();
            if (trimmedPrefix.equalsIgnoreCase("Seite")) {
                normalizedPrefix = "Seite: ";
            } else if (trimmedPrefix.equalsIgnoreCase("Seite:")) {
                normalizedPrefix = normalizedPrefix.endsWith(" ") ? normalizedPrefix : normalizedPrefix + " ";
            } else if (!normalizedPrefix.isEmpty()) {
                char lastChar = normalizedPrefix.charAt(normalizedPrefix.length() - 1);
                if (!Character.isWhitespace(lastChar) && lastChar != ':' && lastChar != '/') {
                    normalizedPrefix = normalizedPrefix + " ";
                }
            }

            String before = normalizedPrefix + currentPage + " / ";
            String after = parts[1];
            renderTextWithTotalPagesTemplate(cb, rect, font, before, after, totalPages, totalPagesBaseFontHolder, totalPagesFontSizeHolder);
            return;
        }

        float blockHeight = rect.ury() - rect.lly();
        float idealLeading = font.getSize() * 1.4f;
        float leading = blockHeight < idealLeading ? blockHeight : idealLeading;

        ColumnText ct = new ColumnText(cb);
        ct.setSimpleColumn(rect.llx(), rect.lly(), rect.urx(), rect.ury());

        Paragraph p = new Paragraph();
        p.setLeading(leading);
        String fallback = text.replace("{{SEITENZAHL}}", currentPage + " / ");
        p.add(new Chunk(fallback, font));
        ct.setAlignment(Element.ALIGN_RIGHT);
        ct.addElement(p);
        ct.go();
    }

    private void renderTextWithTotalPagesTemplate(PdfContentByte cb, RectDto rect, Font font,
                                                  String beforeTemplate, String afterTemplate,
                                                  PdfTemplate totalPages,
                                                  BaseFont[] totalPagesBaseFontHolder,
                                                  float[] totalPagesFontSizeHolder) throws DocumentException {
        try {
            BaseFont bf = font.getBaseFont();
            if (bf == null) {
                bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            }

            float fontSize = font.getSize() > 0 ? font.getSize() : 10f;
            float templateWidth = 50f;
            float beforeWidth = bf.getWidthPoint(beforeTemplate != null ? beforeTemplate : "", fontSize);
            float x = rect.llx();
            float blockHeight = rect.ury() - rect.lly();
            float y = rect.lly() + Math.max(0f, (blockHeight - fontSize) / 2f);

            if (totalPagesBaseFontHolder != null && totalPagesBaseFontHolder.length > 0) {
                totalPagesBaseFontHolder[0] = bf;
            }
            if (totalPagesFontSizeHolder != null && totalPagesFontSizeHolder.length > 0) {
                totalPagesFontSizeHolder[0] = fontSize;
            }

            cb.beginText();
            cb.setFontAndSize(bf, fontSize);
            cb.setColorFill(font.getColor() != null ? font.getColor() : Color.BLACK);
            cb.setTextMatrix(x, y);
            cb.showText(beforeTemplate != null ? beforeTemplate : "");
            if (afterTemplate != null && !afterTemplate.isBlank()) {
                cb.setTextMatrix(x + beforeWidth + templateWidth, y);
                cb.showText(afterTemplate);
            }
            cb.endText();

            cb.addTemplate(totalPages, x + beforeWidth, y);
        } catch (Exception ex) {
            log.warn("Fallback auf Standard-Seitenzahlrendering: {}", ex.getMessage());
            ColumnText ct = new ColumnText(cb);
            ct.setSimpleColumn(rect.llx(), rect.lly(), rect.urx(), rect.ury());
            Paragraph p = new Paragraph((beforeTemplate != null ? beforeTemplate : "") +
                    (afterTemplate != null ? afterTemplate : ""), font);
            ct.addElement(p);
            ct.go();
        }
    }

    /**
     * Rendert einen normalen Textblock mit mehrzeiliger Unterstützung.
     * 
     * Bei sehr kleinen Blöcken (Höhe < 1.5× Schriftgröße) wird die Leading
     * auf die Block-Höhe angepasst, damit der Text überhaupt gerendert wird.
     * OpenPDF's ColumnText rendert NICHTS, wenn die Leading größer als die
     * verfügbare Höhe ist.
     */
    private void renderTextBlock(PdfContentByte cb, String text, RectDto rect, Font font)
            throws DocumentException {
        float blockHeight = rect.ury() - rect.lly();
        float idealLeading = font.getSize() * 1.4f;

        // Wenn der Block zu klein für die Standard-Leading ist, passe an
        float leading = blockHeight < idealLeading ? blockHeight : idealLeading;

        ColumnText ct = new ColumnText(cb);
        ct.setSimpleColumn(rect.llx(), rect.lly(), rect.urx(), rect.ury());

        Paragraph p = new Paragraph();
        p.setLeading(leading);
        String[] lines = text.split("\n");
        for (int l = 0; l < lines.length; l++) {
            p.add(new Chunk(lines[l], font));
            if (l < lines.length - 1) p.add(Chunk.NEWLINE);
        }
        ct.addElement(p);
        ct.go();
    }
}
