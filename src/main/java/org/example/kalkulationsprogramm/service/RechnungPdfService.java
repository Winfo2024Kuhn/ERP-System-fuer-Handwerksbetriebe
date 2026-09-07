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

import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.util.RabattRechner;

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

    /**
     * Grenzen für Schriftgrößen, die direkt im HTML stehen (z.B. aus dem Größen-Dropdown des
     * Editors oder per Copy-Paste aus Word/Outlook mitgebracht). Sie sind bewusst weit gefasst:
     * Was der Editor anzeigt, soll auch im PDF stehen. Beschnitten wird nur, was auf Papier
     * unlesbar klein oder unbrauchbar groß wäre.
     */
    private static final float MIN_HTML_FONT_SIZE = 4f;
    private static final float MAX_HTML_FONT_SIZE = 36f;

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
            BigDecimal betragNetto, // Überschreibt berechnete Nettosumme (z.B. für Abschlagsrechnungen)
            AbschlagInfoPdfDto abschlagInfo) { // Info zum Abschlag-Eingabemodus

        // Backwards-compatible constructor without abschlagInfo
        public RechnungDto(LayoutDto layout, KopfdatenDto kopfdaten, List<ContentBlockDto> contentBlocks,
                           List<FormBlockDto> formBlocks, String schlusstext,
                           String backgroundImagePage1, String backgroundImagePage2,
                           BigDecimal globalRabattProzent, AbrechnungsverlaufPdfDto abrechnungsverlauf,
                           BigDecimal betragNetto) {
            this(layout, kopfdaten, contentBlocks, formBlocks, schlusstext,
                 backgroundImagePage1, backgroundImagePage2, globalRabattProzent, abrechnungsverlauf, betragNetto, null);
        }

        // Backwards-compatible constructor without betragNetto
        public RechnungDto(LayoutDto layout, KopfdatenDto kopfdaten, List<ContentBlockDto> contentBlocks,
                           List<FormBlockDto> formBlocks, String schlusstext,
                           String backgroundImagePage1, String backgroundImagePage2,
                           BigDecimal globalRabattProzent, AbrechnungsverlaufPdfDto abrechnungsverlauf) {
            this(layout, kopfdaten, contentBlocks, formBlocks, schlusstext,
                 backgroundImagePage1, backgroundImagePage2, globalRabattProzent, abrechnungsverlauf, null, null);
        }

        // Backwards-compatible constructor without abrechnungsverlauf
        public RechnungDto(LayoutDto layout, KopfdatenDto kopfdaten, List<ContentBlockDto> contentBlocks,
                           List<FormBlockDto> formBlocks, String schlusstext,
                           String backgroundImagePage1, String backgroundImagePage2,
                           BigDecimal globalRabattProzent) {
            this(layout, kopfdaten, contentBlocks, formBlocks, schlusstext,
                 backgroundImagePage1, backgroundImagePage2, globalRabattProzent, null, null, null);
        }

        // Backwards-compatible constructor without globalRabattProzent
        public RechnungDto(LayoutDto layout, KopfdatenDto kopfdaten, List<ContentBlockDto> contentBlocks,
                           List<FormBlockDto> formBlocks, String schlusstext,
                           String backgroundImagePage1, String backgroundImagePage2) {
            this(layout, kopfdaten, contentBlocks, formBlocks, schlusstext,
                 backgroundImagePage1, backgroundImagePage2, null, null, null, null);
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
            List<AbrechnungspositionPdfDto> positionen,
            /**
             * Fortschrittsbalken und Prozentangabe im Abrechnungsstand zeigen.
             * Pro Rechnung im Editor abschaltbar; die Betragszeilen darunter bleiben
             * immer stehen, weil §14 Abs. 5 UStG den Ausweis der bereits
             * abgerechneten Betraege verlangt.
             */
            boolean balkenAnzeigen,
            /**
             * Namen der Leistungen hinter der Differenzzeile der Schlussrechnung,
             * z.B. "Geruststellung, Bauendreinigung". Leer, wenn sich der Vergleich
             * zur Auftragsbestaetigung nicht eindeutig aufloesen laesst — dann steht
             * dort nur der Betrag.
             */
            String differenzHinweis) {

        // Backwards-compatible constructor ohne Anzeigeoptionen: Balken an, kein Hinweis.
        public AbrechnungsverlaufPdfDto(String basisdokumentNummer, String basisdokumentTyp,
                                        java.time.LocalDate basisdokumentDatum,
                                        BigDecimal basisdokumentBetragNetto,
                                        List<AbrechnungspositionPdfDto> positionen) {
            this(basisdokumentNummer, basisdokumentTyp, basisdokumentDatum,
                 basisdokumentBetragNetto, positionen, true, null);
        }
    }

    public record AbrechnungspositionPdfDto(
            String dokumentNummer,
            String typ,
            java.time.LocalDate datum,
            BigDecimal betragNetto,
            Integer abschlagsNummer) {
    }

    /**
     * Info zum Abschlag-Eingabemodus (prozentual, netto-absolut, brutto-absolut)
     */
    public record AbschlagInfoPdfDto(
            String modus,        // "prozent", "netto", "brutto"
            BigDecimal eingabeWert) { // Originalwert der Eingabe (z.B. 30 bei 30%)
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
            BigDecimal rabattProzent,
            /** Name der Entweder-Oder-Gruppe; null = frei dazubuchbare Zusatzposition. */
            String alternativGruppe) {

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
            String bauvorhaben,
            String bezugsdokumentTyp,
            String bezugsdokumentDatum,
            Integer zahlungszielTage) {

        /** Abwärtskompatibel: ohne die neuen Felder */
        public KopfdatenDto(String rechnungsnummer, LocalDate rechnungsDatum, LocalDate leistungsDatum,
                            String kundenName, String kundenAdresse, String betreff, String kundennummer,
                            String dokumentTyp, String bezugsdokument, String projektnummer, String bauvorhaben) {
            this(rechnungsnummer, rechnungsDatum, leistungsDatum, kundenName, kundenAdresse, betreff,
                    kundennummer, dokumentTyp, bezugsdokument, projektnummer, bauvorhaben, null, null, null);
        }
    }

    // ======================= FONTS =======================

    private static final Font FONT_NORMAL = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10);
    private static final Font FONT_BOLD = FontFactory.getFont(FontFactory.TIMES_BOLD, 10);
    private static final Font FONT_SMALL = FontFactory.getFont(FontFactory.TIMES_ROMAN, 8);
    private static final Font FONT_HEADER = FontFactory.getFont(FontFactory.TIMES_BOLD, 12);
    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.TIMES_BOLD, 14);
    private static final Color HEADER_BG = new Color(220, 38, 38); // Rose-600
    /** Firmenfarbe, wenn in den Firmeninformationen keine hinterlegt ist. */
    private static final Color FIRMENFARBE_STANDARD = new Color(0x50, 0x00, 0x10);

    /**
     * Die Firmenfarbe steht in den Firmeninformationen. Optional injiziert, damit der
     * Service auch ohne Spring-Kontext gerendert werden kann (Tests, Vorschauen) — dann
     * greift die Standardfarbe.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private FirmeninformationRepository firmeninformationRepository;
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
                    addSummenBlock(ct, data.contentBlocks(), data.globalRabattProzent(), data.abrechnungsverlauf(), data.betragNetto(), data.kopfdaten().dokumentTyp(), data.abschlagInfo());
                    addMixedContent(ct, trailingBlocks, data.globalRabattProzent(), data.abrechnungsverlauf(), data.kopfdaten().dokumentTyp());
                } else {
                    // Keine nachfolgenden Textblöcke - normaler Ablauf
                    addMixedContent(ct, data.contentBlocks(), data.globalRabattProzent(), data.abrechnungsverlauf(), data.kopfdaten().dokumentTyp());
                    addSummenBlock(ct, data.contentBlocks(), data.globalRabattProzent(), data.abrechnungsverlauf(), data.betragNetto(), data.kopfdaten().dokumentTyp(), data.abschlagInfo());
                }
            } else {
                // CLOSURE vorhanden oder keine Blöcke - Summenblock wird in addMixedContent gehandelt.
                // betragNetto + abschlagInfo durchreichen, damit Abschlags-/Schlussrechnungen
                // ihren korrekten Nettobetrag und Eingabe-Hint behalten (sonst Regression!).
                addMixedContent(ct, data.contentBlocks(), data.globalRabattProzent(), data.abrechnungsverlauf(), data.kopfdaten().dokumentTyp(), data.betragNetto(), data.abschlagInfo());
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
                        : BaseFont.createFont(BaseFont.TIMES_ROMAN, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
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
        addMixedContent(ct, blocks, globalRabattProzent, abrechnungsverlauf, dokumentTyp, null, null);
    }

    /**
     * Erweiterte Variante: Reicht overrideBetragNetto und abschlagInfo an den
     * CLOSURE-Branch durch. Diese werden vom Summenblock nur bei Abschlags-/
     * Schlussrechnungen ausgewertet (overrideBetragNetto != null) und sind fuer
     * normale Rechnungen unkritisch. Backwards-kompatibel via 5-args-Delegate.
     */
    private void addMixedContent(ColumnText ct, List<ContentBlockDto> blocks, BigDecimal globalRabattProzent, AbrechnungsverlaufPdfDto abrechnungsverlauf, String dokumentTyp, BigDecimal overrideBetragNetto, AbschlagInfoPdfDto abschlagInfo) throws DocumentException {
        if (blocks == null || blocks.isEmpty()) return;

        NumberFormat nf = NumberFormat.getNumberInstance(Locale.GERMANY);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        Color textColor = new Color(0, 0, 0); // Schwarz
        Color headerColor = new Color(71, 85, 105); // Slate-600
        Color accentColor = new Color(190, 18, 60); // Rose-700
        Font textFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10, textColor);
        Font posFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10, textColor);
        Font labelFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 10, textColor);
        Font headerFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 9, headerColor);

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

                // Hinweis zu zwei Datenpfaden:
                //  1. positionenJson (persistiert): Frontend filtert CLOSURE beim Save raus.
                //     Bestehende Dokumente bleiben bytegenau lade-/speicherbar.
                //  2. PDF-Request (createPdfRequest → flattenBlocksForPdf): Frontend reicht
                //     den CLOSURE-Marker durch, damit dieser Backend-Branch produktiv wird
                //     und Vor-/Nachtexte sauber relativ zum Summenblock platziert werden.
                //
                // addClosureBreakdown (Bauabschnitt-Uebersicht) wird bewusst NICHT mehr
                // aufgerufen: bis 2026-05 war der CLOSURE-Pfad toter Code (Frontend
                // filterte CLOSURE beim Laden), heute soll der Render-Output exakt dem
                // alten lastServiceIdx-Pfad entsprechen. Die Methode bleibt erhalten,
                // falls die Uebersicht spaeter pro Dokumenttyp/Template aktiviert wird.

                // 3. Add Sums (calculating totals up to this point).
                // overrideBetragNetto und abschlagInfo durchreichen, damit Abschlags-/
                // Schlussrechnungen ihren hinterlegten Nettobetrag und Eingabe-Hint
                // im PDF behalten (sonst wuerde die Summe aus den Positionen statt
                // aus dem hinterlegten betragNetto berechnet).
                List<ContentBlockDto> blocksBefore = new java.util.ArrayList<>();
                for (int k = 0; k < i; k++) {
                    blocksBefore.add(blocks.get(k));
                }
                addSummenBlock(ct, blocksBefore, globalRabattProzent, abrechnungsverlauf, overrideBetragNetto, dokumentTyp, abschlagInfo);

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
                 //
                 // Vertrag fuer TEXT/SERVICE: Die Block-Werte fett/fontSize sollen hier
                 // neutral (false/10) ankommen — die gesamte Formatierung steckt im HTML.
                 // Grund: der Parser benutzt sie als Vorgabe fuer JEDEN Chunk ausserhalb
                 // der <strong>/<span>-Tags, ein "true" wuerde also den ganzen Block fett
                 // setzen. Die beiden aktiven Zulieferer normalisieren deshalb vorher:
                 // document-editor/index.tsx (manueller Export) und
                 // AutoAuftragsbestaetigungVersandService#appendBlock (Auto-Versand).
                 // ACHTUNG: Serverseitig erzwungen ist das nicht — DokumentGeneratorController
                 // reicht die Werte des Clients ungefiltert durch. Ein neuer Client dieses
                 // Endpoints muss die Neutralisierung selbst mitbringen.
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
                     // Etwas mehr vertikalen Abstand zwischen aufeinanderfolgenden Textbausteinen,
                     // damit Vor-/Nachtexte visuell klar getrennt sind. Wirkt nur auf den letzten
                     // Paragraph; Zeilenabstand innerhalb des Bausteins bleibt unveraendert.
                     for (int idx = elements.size() - 1; idx >= 0; idx--) {
                         if (elements.get(idx) instanceof Paragraph p) {
                             p.setSpacingAfter(p.getSpacingAfter() + 8f);
                             break;
                         }
                     }
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
        Font sectionFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 11, textColor);
        
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
        Font subtotalFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 10, textColor);
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
            Font hFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 9, new Color(71, 85, 105)); // Slate-600
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

    /**
     * Kennzeichnung einer Wahlposition hinter dem Titel. Bewusst nüchtern und ohne
     * Gruppennamen: das PDF ist ein Angebot, kein Formular — die Entscheidung trifft
     * der Kunde über die Freigabe-Seite. Welche Varianten zusammengehören, zeigt
     * die Positionsnummer (3a/3b), die das Frontend bereits liefert.
     */
    static String wahlpositionSuffix(ContentBlockDto block) {
        if (!block.optional()) return "";
        return block.alternativGruppe() != null && !block.alternativGruppe().isBlank()
                ? " (Alternative)"
                : " (Optional)";
    }

    private void addServiceRow(PdfPTable table, ContentBlockDto block, Font textFont, Font posFont, Font labelFont, NumberFormat nf, Color textColor) {
        boolean isAlternative = block.optional();
        Font currentTextFont = isAlternative ? FontFactory.getFont(FontFactory.TIMES_ITALIC, 10, textColor) : textFont;
        Font currentLabelFont = isAlternative ? FontFactory.getFont(FontFactory.TIMES_BOLDITALIC, 10, textColor) : labelFont;
        
        // Dezente Zeilentrennung
        Color borderColor = new Color(226, 232, 240); // Slate-200
        boolean hasDescription = block.beschreibungHtml() != null && !block.beschreibungHtml().isBlank();

        // Kompaktere Padding-Werte für Zeilenhöhe passend zur Schriftgröße
        float cellPadding = 3f;
        float cellPaddingTop = 4f;
        float cellPaddingBottom = 4f;

        // Pos - zentriert in einem dezenten Badge-Style
        Font posBadgeFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 9, new Color(71, 85, 105)); // Slate-600
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
        // Pos/Menge/Einzelpreis/Gesamt sind reine Phrase-Zellen, die Bezeichnung ist
        // eine Composite-Zelle (addElement mit Paragraph + Leading). Ohne useAscender
        // verankert iText ALIGN_TOP in der Composite-Zelle an der Leading-Box-Oberkante
        // (Paragraph startet eine Leading-Strecke unterhalb), in Phrase-Zellen aber
        // an der Font-Box-Oberkante. Das fuehrt zu sichtbarem Versatz: Pos-Nr. liegt
        // hoeher als der Leistungstext. useAscender(true) verankert ALIGN_TOP einheitlich
        // an der Ascender-Linie der ersten Glyphe, sodass alle Spalten auf gleicher
        // Hoehe starten.
        posCell.setUseAscender(true);
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
        mengeCell.setUseAscender(true);
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
        descCell.setUseAscender(true);
        
        // Bezeichnung und Beschreibung:
        // Der Kurztext (block.beschreibung()) ist eine reine Innensicht — er hilft dem
        // Bediener, die Leistung im Editor wiederzufinden, und gehört nicht aufs
        // Kundendokument. Gedruckt wird deshalb allein der Beschreibungstext.
        if (hasDescription) {
            // Die Wahlpositions-Kennzeichnung steht immer als eigene Zeile über dem Text.
            // Früher hing sie mal hinter dem Titel und mal darüber, je nachdem ob der
            // Beschreibungstext den Titel wiederholte — für den Leser wirkte das zufällig.
            if (isAlternative) {
                Paragraph altHint = new Paragraph(wahlpositionSuffix(block).trim(), currentLabelFont);
                altHint.setLeading(currentLabelFont.getSize() * 1.3f);
                descCell.addElement(altHint);
            }
            try {
                // Block-Default-Schriftgröße/Fett aus dem ContentBlockDto — fuer SERVICE
                // bewusst neutral (10/false), siehe Vertrag beim TEXT-Zweig oben. Der Wert
                // gilt fuer jeden Chunk ausserhalb der <strong>/<span>-Tags; er darf die
                // im HTML markierten Stellen nur ergaenzen, nie den ganzen Block umsetzen.
                float serviceDefaultFontSize = Math.max(10f, Math.min(20f, block.fontSize()));
                java.util.List<com.lowagie.text.Element> elements = parseHtmlToElements(
                        block.beschreibungHtml(), textColor, serviceDefaultFontSize, block.fett());
                for (com.lowagie.text.Element el : elements) {
                    descCell.addElement(el);
                }
            } catch (Exception e) {
                // Fallback: Plain Text
                String plainText = stripHtmlForFallback(block.beschreibungHtml());
                descCell.addElement(new Phrase(plainText, currentTextFont));
            }
        } else {
            // Notnagel: Ohne Beschreibungstext bliebe die Bezeichnungsspalte sonst leer.
            // Dann springt der Kurztext ein — lieber die Innensicht auf dem Papier als
            // eine namenlose Position mit Preis.
            String titel = block.beschreibung() != null ? block.beschreibung() : "";
            String descText = titel + wahlpositionSuffix(block);
            Paragraph titleParagraph = new Paragraph(descText, currentLabelFont);
            titleParagraph.setLeading(currentLabelFont.getSize() * 1.3f);
            descCell.addElement(titleParagraph);
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
        epCell.setUseAscender(true);
        table.addCell(epCell);

        // Gesamtpreis - hervorgehoben
        boolean hasDiscount = block.rabattProzent() != null && block.rabattProzent().compareTo(BigDecimal.ZERO) > 0;
        Font gpFont = isAlternative ? currentLabelFont : FontFactory.getFont(FontFactory.TIMES_BOLD, 10, textColor);

        PdfPCell gpCell = new PdfPCell();
        gpCell.setBorder(Rectangle.BOTTOM);
        gpCell.setBorderColor(borderColor);
        gpCell.setBorderWidth(0.5f);
        gpCell.setPadding(cellPadding);
        gpCell.setPaddingTop(cellPaddingTop);
        gpCell.setPaddingBottom(cellPaddingBottom);
        gpCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        gpCell.setVerticalAlignment(Element.ALIGN_TOP);
        gpCell.setUseAscender(true);
        
        if (isAlternative) {
            // Use Phrase directly so vertical alignment works
            gpCell.setPhrase(new Phrase("0,00 €", gpFont));
        } else if (hasDiscount) {
            // Originalpreis (durchgestrichen)
            BigDecimal originalGesamt = block.menge().multiply(block.einzelpreis()).setScale(2, java.math.RoundingMode.HALF_UP);
            Font strikeFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, 9, textColor);
            Chunk strikeChunk = new Chunk(nf.format(originalGesamt) + " €", strikeFont);
            strikeChunk.setTextRise(0);
            // Simulate strikethrough manually via underline at middle of text
            strikeChunk.setUnderline(0.5f, 3.5f);
            Paragraph origLine = new Paragraph(strikeChunk);
            origLine.setAlignment(Element.ALIGN_RIGHT);
            origLine.setLeading(11f);
            gpCell.addElement(origLine);
            
            // Rabatt-Hinweis
            Font rabattFont = FontFactory.getFont(FontFactory.TIMES_ITALIC, 8, new Color(220, 38, 38)); // Rose-600
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
     * Parst HTML zu iText-Elementen mit Unterstützung für Fett, Kursiv, Farben, Schriftgröße, Listen und Base64-Images.
     * Delegiert an die erweiterte Überladung mit Standard-Defaults (10pt, nicht fett).
     */
    private java.util.List<com.lowagie.text.Element> parseHtmlToElements(String html, Color defaultColor) {
        return parseHtmlToElements(html, defaultColor, 10f, false);
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
    // Package-private statt private, damit RechnungPdfServiceTest die Schriftgrößen-Umrechnung
    // direkt prüfen kann — über den extrahierten PDF-Text sind Schriftgrößen nicht sichtbar.
    java.util.List<com.lowagie.text.Element> parseHtmlToElements(
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

        for (String originalPart : parts) {
            String part = originalPart.trim();
            if (part.isEmpty()) {
                // Leerzeichen zwischen Tags beibehalten (z.B. zwischen </strong> und Text)
                if (originalPart.contains(" ") || originalPart.contains("\u00a0")) {
                    addRichChunk(currentParagraph, " ", currentFontSize, isBold, isItalic, isUnderline, currentColor, currentBgColor);
                }
                continue;
            }

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
                            newFontSize = Math.max(MIN_HTML_FONT_SIZE, Math.min(MAX_HTML_FONT_SIZE, size));
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
                    Font bulletFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, currentFontSize, currentColor);
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
                // Text-Content: originalPart verwenden um Leerzeichen zu erhalten
                String text = originalPart
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

        Font font = FontFactory.getFont(FontFactory.TIMES_ROMAN, fontSize, fontStyle, color);
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
        addSummenBlock(ct, blocks, globalRabattProzent, abrechnungsverlauf, overrideBetragNetto, dokumentTyp, null);
    }

    private void addSummenBlock(ColumnText ct, List<ContentBlockDto> blocks, BigDecimal globalRabattProzent, AbrechnungsverlaufPdfDto abrechnungsverlauf, BigDecimal overrideBetragNetto, String dokumentTyp, AbschlagInfoPdfDto abschlagInfo) throws DocumentException {
        // Mahnungen brauchen keinen Summen-Block: der offene Betrag steht schon
        // prominent in der Forderungs-Box im Inhalt — eine weitere Netto/USt/
        // Zahlbetrag-Tabelle wäre redundant und verwirrend.
        boolean isMahnung = dokumentTyp != null
                && (dokumentTyp.contains("Mahnung") || dokumentTyp.equalsIgnoreCase("Zahlungserinnerung"));
        if (isMahnung) {
            return;
        }

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

        // Globalen Rabatt anwenden falls vorhanden. Rundung zentral ueber RabattRechner,
        // damit gespeicherter Betrag, ZUGFeRD-Betrag und diese Anzeige nicht um einen
        // Cent auseinanderlaufen.
        boolean hasGlobalRabatt = RabattRechner.normalisiereProzent(globalRabattProzent) != null;
        BigDecimal rabattBetrag = RabattRechner.rabattBetrag(netto, globalRabattProzent);
        BigDecimal nettoNachRabatt = RabattRechner.nettoNachRabatt(netto, globalRabattProzent);

        // MwSt und Brutto berechnen (auf Basis des rabattierten Netto)
        BigDecimal mwstSatz = new BigDecimal("0.19");
        BigDecimal mwst = nettoNachRabatt.multiply(mwstSatz).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal brutto = nettoNachRabatt.add(mwst);

        // Formatierung
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.GERMANY);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        Color textColor = new Color(0, 0, 0); // Schwarz
        Font normalFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10, textColor);
        Font boldFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 11, textColor);
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

        // Vorherige Rechnungen vorhanden?
        boolean hasAbrechnung = abrechnungsverlauf != null && abrechnungsverlauf.positionen() != null
                && !abrechnungsverlauf.positionen().isEmpty();

        if (hasBasisdokument) {
            // === ABRECHNUNGSSTAND + ZU ZAHLEN ===
            // Zwei klar getrennte Abschnitte: oben was der Auftrag insgesamt kostet und
            // was davon noch offen ist, unten was mit dieser Rechnung faellig wird.
            // In der Uebersicht steht brutto gross und netto klein darunter — der Kunde
            // rechnet in Bruttobetraegen. Der Steuerausweis folgt im Zahlblock.
            DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
            Color firmenfarbe = ermittleFirmenfarbe();

            // --- Summen vorab, damit Fortschrittsbalken und Restbetrag stimmen ---
            BigDecimal bereitsAbgerechnetNetto = BigDecimal.ZERO;
            if (hasAbrechnung) {
                for (AbrechnungspositionPdfDto pos : abrechnungsverlauf.positionen()) {
                    bereitsAbgerechnetNetto = bereitsAbgerechnetNetto.add(
                            pos.betragNetto() != null ? pos.betragNetto() : BigDecimal.ZERO);
                }
            }

            // Die Schlussrechnung rechnet die Positionen ab, die tatsaechlich in ihr
            // stehen — abzueglich dessen, was bereits in Rechnung gestellt wurde.
            //
            // Bis 2026-09 stand hier "gesamtAuftragssumme minus bereitsAbgerechnet".
            // Damit landete jede aus der Schlussrechnung geloeschte Position (etwa
            // eine nicht benoetigte Geruststellung) trotzdem im Rechnungsbetrag, und
            // unvorhergesehene Zusatzpositionen fielen umgekehrt unter den Tisch. Die
            // Differenz zur Auftragsbestaetigung wird stattdessen unten als eigene
            // Zeile ausgewiesen.
            if (isSchlussrechnung) {
                nettoNachRabatt = nettoNachRabatt.subtract(bereitsAbgerechnetNetto);
                mwst = nettoNachRabatt.multiply(mwstSatz).setScale(2, java.math.RoundingMode.HALF_UP);
                brutto = nettoNachRabatt.add(mwst);
            }

            BigDecimal auftragBrutto = bruttoMitSteuer(gesamtAuftragssumme, mwstSatz);

            // === Abschnitt 1: Abrechnungsstand ===
            addAbschnittsUeberschrift(sumTable, "Abrechnungsstand", 0f, lineColor);

            if (abrechnungsverlauf.balkenAnzeigen() && gesamtAuftragssumme.compareTo(BigDecimal.ZERO) > 0) {
                // Eine Schlussrechnung schliesst den Auftrag ab — danach ist er per
                // Definition zu 100 % abgerechnet, auch wenn weniger berechnet wurde
                // als die Auftragsbestaetigung vorsah. Der nicht berechnete Teil steht
                // als eigene Differenzzeile darunter, nicht als offener Rest.
                double prozent;
                if (isSchlussrechnung) {
                    prozent = 100d;
                } else {
                    BigDecimal abgerechnetNetto = bereitsAbgerechnetNetto.add(nettoNachRabatt);
                    prozent = abgerechnetNetto.multiply(new BigDecimal("100"))
                            .divide(gesamtAuftragssumme, 1, java.math.RoundingMode.HALF_UP)
                            .doubleValue();
                }
                addFortschrittsBalken(sumTable, prozent, firmenfarbe);
            }

            // --- Auftragssumme ---
            String basisZeile = null;
            if (abrechnungsverlauf.basisdokumentTyp() != null && !abrechnungsverlauf.basisdokumentTyp().isBlank()) {
                basisZeile = abrechnungsverlauf.basisdokumentTyp();
                if (abrechnungsverlauf.basisdokumentNummer() != null && !abrechnungsverlauf.basisdokumentNummer().isBlank()) {
                    basisZeile += " Nr. " + abrechnungsverlauf.basisdokumentNummer();
                }
                if (abrechnungsverlauf.basisdokumentDatum() != null) {
                    basisZeile += " vom " + abrechnungsverlauf.basisdokumentDatum().format(dateFmt);
                }
            }
            addUebersichtsZeile(sumTable, "Auftragssumme", basisZeile,
                    nf.format(auftragBrutto) + " €",
                    "netto " + nf.format(gesamtAuftragssumme) + " €", false, firmenfarbe);

            // --- bereits gestellte Rechnungen, einzeln ---
            // Die Bruttobetraege werden zeilenweise gerundet und die Restsumme daraus
            // abgeleitet, damit die Spalte auf dem Papier exakt aufgeht.
            BigDecimal vorRechnungenBrutto = BigDecimal.ZERO;
            if (hasAbrechnung) {
                int posCounter = 0;
                for (AbrechnungspositionPdfDto pos : abrechnungsverlauf.positionen()) {
                    posCounter++;
                    BigDecimal posNetto = pos.betragNetto() != null ? pos.betragNetto() : BigDecimal.ZERO;
                    BigDecimal posBrutto = bruttoMitSteuer(posNetto, mwstSatz);
                    vorRechnungenBrutto = vorRechnungenBrutto.add(posBrutto);

                    String typLabel = pos.typ() != null && !pos.typ().isBlank() ? pos.typ() : "Rechnung";
                    typLabel = typLabel.substring(0, 1).toUpperCase() + typLabel.substring(1).toLowerCase();
                    String bezeichnung = posCounter + ". " + typLabel;
                    if (pos.dokumentNummer() != null && !pos.dokumentNummer().isBlank()) {
                        bezeichnung += " Nr. " + pos.dokumentNummer();
                    }
                    String datumsZeile = pos.datum() != null ? "vom " + pos.datum().format(dateFmt) : null;

                    addUebersichtsZeile(sumTable, bezeichnung, datumsZeile,
                            "- " + nf.format(posBrutto) + " €",
                            "netto " + nf.format(posNetto) + " €", false, firmenfarbe);
                }
            }

            // --- diese Rechnung ---
            String dieseBezeichnung;
            if (isAbschlag) {
                dieseBezeichnung = "diese Abschlagsrechnung";
            } else if (isSchlussrechnung) {
                dieseBezeichnung = "diese Schlussrechnung";
            } else {
                dieseBezeichnung = "diese Rechnung";
            }

            // Wie der Abschlag zustande kam, steht als Unterzeile — das ersetzt die
            // frueheren freistehenden Hinweiszeilen.
            String dieseUnterzeile = null;
            if (isAbschlag && abschlagInfo != null && abschlagInfo.eingabeWert() != null) {
                if ("prozent".equals(abschlagInfo.modus())) {
                    String prozentStr = abschlagInfo.eingabeWert().stripTrailingZeros().toPlainString().replace('.', ',');
                    dieseUnterzeile = "ca. " + prozentStr + " % der Auftragssumme";
                } else if ("brutto".equals(abschlagInfo.modus())) {
                    dieseUnterzeile = "Eingabe: " + nf.format(abschlagInfo.eingabeWert()) + " € brutto";
                }
            }
            if (dieseUnterzeile == null && isAbschlag && gesamtAuftragssumme.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal anteil = nettoNachRabatt.multiply(new BigDecimal("100"))
                        .divide(gesamtAuftragssumme, 1, java.math.RoundingMode.HALF_UP);
                NumberFormat pf = NumberFormat.getNumberInstance(Locale.GERMANY);
                pf.setMinimumFractionDigits(0);
                pf.setMaximumFractionDigits(1);
                dieseUnterzeile = "ca. " + pf.format(anteil) + " % der Auftragssumme";
            }

            addUebersichtsZeile(sumTable, dieseBezeichnung, dieseUnterzeile,
                    "- " + nf.format(brutto) + " €",
                    "netto " + nf.format(nettoNachRabatt) + " €", false, firmenfarbe);

            // --- Differenz zur Auftragsbestaetigung (nur Schlussrechnung) ---
            // Als Ausgleichsgroesse gerechnet, nicht aus einem Positionsvergleich:
            // damit geht die Spalte immer auf, egal ob Positionen geloescht, ergaenzt
            // oder in der Menge geaendert wurden oder ein Rabatt dazukam.
            //   > 0  Auftrag war hoeher als abgerechnet -> Leistungen sind entfallen
            //   < 0  mehr abgerechnet als beauftragt    -> Zusatzleistungen
            BigDecimal differenzNetto = BigDecimal.ZERO;
            if (isSchlussrechnung) {
                differenzNetto = gesamtAuftragssumme
                        .subtract(bereitsAbgerechnetNetto)
                        .subtract(nettoNachRabatt);
            }
            if (differenzNetto.abs().compareTo(new BigDecimal("0.01")) >= 0) {
                boolean entfallen = differenzNetto.compareTo(BigDecimal.ZERO) > 0;
                BigDecimal differenzBrutto = bruttoMitSteuer(differenzNetto.abs(), mwstSatz);
                String differenzLabel = entfallen ? "Nicht angefallen" : "Zusätzliche Leistungen";
                String differenzVorzeichen = entfallen ? "- " : "+ ";
                // Ohne aufloesbaren Positionsvergleich bleibt die Zeile trotzdem erklaert —
                // gleicher Text wie in der Editor-Vorschau (ClosureBlock.tsx), damit
                // Vorschau und Druck nicht auseinanderlaufen.
                String differenzUnterzeile = abrechnungsverlauf.differenzHinweis() != null
                        && !abrechnungsverlauf.differenzHinweis().isBlank()
                        ? abrechnungsverlauf.differenzHinweis()
                        : "Unterschied zur Auftragsbestätigung";

                addUebersichtsZeile(sumTable, differenzLabel, differenzUnterzeile,
                        differenzVorzeichen + nf.format(differenzBrutto) + " €",
                        "netto " + nf.format(differenzNetto.abs()) + " €", false, firmenfarbe);
            }

            // --- Noch offen ---
            // Nach Abzug der Differenzzeile steht bei der Schlussrechnung hier 0,00 € —
            // der Auftrag ist abgeschlossen, es bleibt nichts nachzufordern.
            BigDecimal restNetto = gesamtAuftragssumme.subtract(bereitsAbgerechnetNetto)
                    .subtract(nettoNachRabatt).subtract(differenzNetto);
            // Bewusst aus dem Nettorest gerechnet und nicht als Differenz der Bruttozeilen:
            // sonst bleiben bei krummen Betraegen Rundungsreste stehen und eine restlos
            // abgerechnete Schlussrechnung meldet "Noch offen 0,01 €".
            BigDecimal restBrutto = bruttoMitSteuer(restNetto, mwstSatz);

            // Rechnet die Rechnung den kompletten Auftrag ab und gab es keine
            // Vorrechnungen, ist der Rest per Definition 0,00 € — die Zeile waere nur
            // Rauschen. Bei Abschlags-, Teil- und Schlussrechnungen bleibt sie stehen:
            // dort ist der noch offene Rest die eigentliche Information.
            boolean zeigeRestbetrag = isAbschlag || isSchlussrechnung || hasAbrechnung
                    || restNetto.abs().compareTo(new BigDecimal("0.01")) >= 0;

            if (zeigeRestbetrag) {
                PdfPCell restLinie = new PdfPCell();
                restLinie.setBorder(Rectangle.TOP);
                restLinie.setBorderColor(new Color(203, 213, 225)); // Slate-300
                restLinie.setBorderWidth(0.75f);
                restLinie.setColspan(3);
                restLinie.setFixedHeight(5f);
                sumTable.addCell(restLinie);

                addUebersichtsZeile(sumTable, "Noch offen", null,
                        nf.format(restBrutto) + " €",
                        "netto " + nf.format(restNetto) + " €", true, firmenfarbe);
            }

            // === Abschnitt 2: Zu zahlen ===
            addAbschnittsUeberschrift(sumTable, "Zu zahlen", 14f, lineColor);

            // Rabatt getrennt ausweisen, damit der Nettobetrag nachvollziehbar bleibt.
            // Bei der Schlussrechnung ergibt sich der Betrag aus dem offenen Rest, dort
            // greift der Globalrabatt nicht.
            if (hasGlobalRabatt && !isSchlussrechnung) {
                addZahlZeile(sumTable, "Nettobetrag vor Rabatt", null, nf.format(netto) + " €", false, normalFont, boldFont);

                Font rabattFont = FontFactory.getFont(FontFactory.TIMES_ITALIC, 10, new Color(220, 38, 38));
                addZahlZeile(sumTable, "Rabatt", nf.format(globalRabattProzent) + " %",
                        "- " + nf.format(rabattBetrag) + " €", false, rabattFont, rabattFont);
            }

            addZahlZeile(sumTable, "Nettobetrag", null, nf.format(nettoNachRabatt) + " €", false, normalFont, boldFont);
            addZahlZeile(sumTable, "Umsatzsteuer", "19 %", nf.format(mwst) + " €", false, normalFont, boldFont);

            // Schlussrechnung: §14 Abs. 5 UStG verlangt, dass die Steuer auf die bereits
            // abgerechneten Betraege erkennbar ist.
            if (isSchlussrechnung && vorRechnungenBrutto.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ustAufVorrechnungen = vorRechnungenBrutto.subtract(bereitsAbgerechnetNetto);
                Font hinweisFont = FontFactory.getFont(FontFactory.TIMES_ITALIC, 8, new Color(100, 116, 139));
                PdfPCell hinweis = new PdfPCell(new Phrase(
                        "in den bereits gestellten Rechnungen enthaltene Umsatzsteuer: "
                                + nf.format(ustAufVorrechnungen) + " €", hinweisFont));
                hinweis.setBorder(Rectangle.NO_BORDER);
                hinweis.setColspan(3);
                hinweis.setPaddingTop(1f);
                hinweis.setPaddingBottom(3f);
                sumTable.addCell(hinweis);
            }

            PdfPCell zahlLinie = new PdfPCell();
            zahlLinie.setBorder(Rectangle.TOP);
            zahlLinie.setBorderColor(new Color(203, 213, 225)); // Slate-300
            zahlLinie.setBorderWidth(0.75f);
            zahlLinie.setColspan(3);
            zahlLinie.setFixedHeight(5f);
            sumTable.addCell(zahlLinie);

            Font zahlbetragFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 12, textColor);
            addZahlZeile(sumTable, "Zahlbetrag", null, nf.format(brutto) + " €", true, zahlbetragFont, zahlbetragFont);

            PdfPCell bottomLine = new PdfPCell();
            bottomLine.setBorder(Rectangle.TOP);
            bottomLine.setBorderColor(lineColor);
            bottomLine.setBorderWidth(1.5f);
            bottomLine.setColspan(3);
            bottomLine.setFixedHeight(2f);
            sumTable.addCell(bottomLine);

        } else {
            // === STANDARD FLOW: Netto + MwSt + Gesamt (ohne Basisdokument) ===

            // Trennlinie. Der Abrechnungsstand-Zweig oben bringt seine eigene Linie
            // unter der Abschnittsueberschrift mit und braucht diese hier nicht.
            PdfPCell lineCell = new PdfPCell();
            lineCell.setBorder(Rectangle.TOP);
            lineCell.setBorderColor(lineColor);
            lineCell.setBorderWidth(1.5f);
            lineCell.setColspan(3);
            lineCell.setFixedHeight(8f);
            sumTable.addCell(lineCell);

            // Abschlagsrechnung: Gesamtauftragssumme anzeigen (aus Positionensumme)
            if (isAbschlag && positionenNetto.compareTo(BigDecimal.ZERO) > 0) {
                PdfPCell gesamtLabel = new PdfPCell(new Phrase("Gesamtauftragssumme (netto)", normalFont));
                gesamtLabel.setBorder(Rectangle.NO_BORDER);
                gesamtLabel.setPaddingTop(6f);
                gesamtLabel.setPaddingBottom(3f);
                gesamtLabel.setColspan(2);
                sumTable.addCell(gesamtLabel);

                PdfPCell gesamtValue = new PdfPCell(new Phrase(nf.format(positionenNetto) + " €", normalFont));
                gesamtValue.setBorder(Rectangle.NO_BORDER);
                gesamtValue.setPaddingTop(6f);
                gesamtValue.setPaddingBottom(3f);
                gesamtValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
                sumTable.addCell(gesamtValue);
            }

            // Nettosumme (für Abschlag = Abschlagsbetrag)
            String nettoLabelText = isAbschlag ? "Abschlagsbetrag (netto)" : "Nettosumme";
            PdfPCell nettoLabel = new PdfPCell(new Phrase(nettoLabelText, normalFont));
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

            // Abschlag-Typ Hinweis (prozentual / brutto)
            if (isAbschlag && abschlagInfo != null && abschlagInfo.eingabeWert() != null) {
                Font hintFont = FontFactory.getFont(FontFactory.TIMES_ITALIC, 8, new Color(100, 116, 139));
                String hint = null;
                if ("prozent".equals(abschlagInfo.modus())) {
                    String prozentStr = abschlagInfo.eingabeWert().stripTrailingZeros().toPlainString().replace('.', ',');
                    hint = "(ca. " + prozentStr + "% der Gesamtsumme)";
                } else if ("brutto".equals(abschlagInfo.modus())) {
                    hint = "(Eingabe: " + nf.format(abschlagInfo.eingabeWert()) + " € brutto)";
                }
                if (hint != null) {
                    PdfPCell hintCell = new PdfPCell(new Phrase(hint, hintFont));
                    hintCell.setBorder(Rectangle.NO_BORDER);
                    hintCell.setPaddingTop(0f);
                    hintCell.setPaddingBottom(3f);
                    hintCell.setPaddingLeft(8f);
                    hintCell.setColspan(3);
                    sumTable.addCell(hintCell);
                }
            }

            // Globaler Rabatt (falls vorhanden)
            if (hasGlobalRabatt) {
                Font rabattFont = FontFactory.getFont(FontFactory.TIMES_ITALIC, 10, new Color(220, 38, 38));

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

    /** Bruttobetrag zu einem Nettobetrag, kaufmaennisch auf Cent gerundet. */
    private static BigDecimal bruttoMitSteuer(BigDecimal netto, BigDecimal mwstSatz) {
        if (netto == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal steuer = netto.multiply(mwstSatz).setScale(2, java.math.RoundingMode.HALF_UP);
        return netto.add(steuer);
    }

    /**
     * Firmenfarbe aus den Firmeninformationen. Faellt auf die Standardfarbe zurueck,
     * wenn nichts hinterlegt oder der Wert unbrauchbar ist.
     */
    private Color ermittleFirmenfarbe() {
        if (firmeninformationRepository == null) {
            return FIRMENFARBE_STANDARD;
        }
        try {
            return firmeninformationRepository.findById(1L)
                    .map(Firmeninformation::getFirmenfarbe)
                    .filter(hex -> hex != null && !hex.isBlank())
                    .map(hex -> parseColor(hex, FIRMENFARBE_STANDARD))
                    .orElse(FIRMENFARBE_STANDARD);
        } catch (Exception e) {
            log.warn("Firmenfarbe konnte nicht geladen werden, nutze Standardfarbe", e);
            return FIRMENFARBE_STANDARD;
        }
    }

    /** Mischt eine Farbe mit Weiss — 0f bleibt die Farbe, 1f ist reines Weiss. */
    private static Color aufhellen(Color farbe, float anteil) {
        return new Color(
                Math.round(farbe.getRed() + (255 - farbe.getRed()) * anteil),
                Math.round(farbe.getGreen() + (255 - farbe.getGreen()) * anteil),
                Math.round(farbe.getBlue() + (255 - farbe.getBlue()) * anteil));
    }

    /**
     * Abschnittsüberschrift im Summenblock: kleine graue Versalien über einer kräftigen
     * Linie — dieselbe Optik wie der Kopf der Positionstabelle. Das Dokument kennt keine
     * Kästen, deshalb trennt hier die Linie statt eines Rahmens.
     */
    private void addAbschnittsUeberschrift(PdfPTable sumTable, String text, float abstandDavor, Color lineColor) {
        if (abstandDavor > 0f) {
            PdfPCell abstand = new PdfPCell(new Phrase(" "));
            abstand.setBorder(Rectangle.NO_BORDER);
            abstand.setColspan(3);
            abstand.setFixedHeight(abstandDavor);
            sumTable.addCell(abstand);
        }

        Chunk beschriftung = new Chunk(text.toUpperCase(Locale.GERMANY),
                FontFactory.getFont(FontFactory.TIMES_BOLD, 9, new Color(71, 85, 105))); // Slate-600
        beschriftung.setCharacterSpacing(0.8f);

        PdfPCell zelle = new PdfPCell(new Phrase(beschriftung));
        zelle.setBorder(Rectangle.BOTTOM);
        zelle.setBorderColor(lineColor);
        zelle.setBorderWidth(1.5f);
        zelle.setColspan(3);
        zelle.setPaddingBottom(5f);
        sumTable.addCell(zelle);
    }

    /**
     * Fortschrittsbalken über die volle Breite plus Beschriftung darunter, in der
     * Firmenfarbe: gefuellter Teil satt, offener Teil als heller Ton derselben Farbe.
     */
    private void addFortschrittsBalken(PdfPTable sumTable, double prozent, Color firmenfarbe) {
        float anteil = (float) Math.max(0d, Math.min(100d, prozent));
        Color gefuellt = firmenfarbe;
        Color offen = aufhellen(firmenfarbe, 0.88f);

        // Ein PdfPTable braucht positive Spaltenbreiten, daher die Randfaelle einzeln.
        boolean nurGefuellt = anteil >= 100f;
        boolean nurOffen = anteil <= 0f;
        PdfPTable balken = nurGefuellt || nurOffen
                ? new PdfPTable(new float[] { 1f })
                : new PdfPTable(new float[] { anteil, 100f - anteil });
        balken.setWidthPercentage(100);

        if (!nurOffen) {
            balken.addCell(balkenSegment(gefuellt));
        }
        if (!nurGefuellt) {
            balken.addCell(balkenSegment(offen));
        }

        PdfPCell balkenZelle = new PdfPCell(balken);
        balkenZelle.setBorder(Rectangle.NO_BORDER);
        balkenZelle.setColspan(3);
        balkenZelle.setPaddingTop(6f);
        balkenZelle.setPaddingBottom(2f);
        sumTable.addCell(balkenZelle);

        NumberFormat pf = NumberFormat.getNumberInstance(Locale.GERMANY);
        pf.setMinimumFractionDigits(0);
        pf.setMaximumFractionDigits(0);
        PdfPCell beschriftung = new PdfPCell(new Phrase(
                "Mit dieser Rechnung sind " + pf.format(anteil) + " % des Auftrags abgerechnet.",
                FontFactory.getFont(FontFactory.TIMES_ITALIC, 8, new Color(100, 116, 139)))); // Slate-500
        beschriftung.setBorder(Rectangle.NO_BORDER);
        beschriftung.setColspan(3);
        beschriftung.setPaddingBottom(6f);
        sumTable.addCell(beschriftung);
    }

    private static PdfPCell balkenSegment(Color farbe) {
        PdfPCell segment = new PdfPCell(new Phrase(" "));
        segment.setBorder(Rectangle.NO_BORDER);
        segment.setBackgroundColor(farbe);
        segment.setFixedHeight(5f);
        // Ohne das bleibt zwischen gefuelltem und offenem Teil ein weisser Spalt stehen.
        segment.setPadding(0f);
        return segment;
    }

    /**
     * Zeile der Abrechnungsübersicht: links die Bezeichnung mit optionaler Unterzeile,
     * rechts der Bruttobetrag mit dem Nettobetrag klein und grau darunter.
     */
    private void addUebersichtsZeile(PdfPTable sumTable, String bezeichnung, String unterzeile,
                                     String bruttoText, String nettoText, boolean hervorgehoben,
                                     Color firmenfarbe) {
        String schrift = hervorgehoben ? FontFactory.TIMES_BOLD : FontFactory.TIMES_ROMAN;
        Font textFont = FontFactory.getFont(schrift, hervorgehoben ? 11 : 10, new Color(0, 0, 0));
        Font unterFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, 8, new Color(100, 116, 139)); // Slate-500
        Font nettoFont = FontFactory.getFont(FontFactory.TIMES_ITALIC, 8, firmenfarbe);

        Phrase links = new Phrase(new Chunk(bezeichnung, textFont));
        if (unterzeile != null && !unterzeile.isBlank()) {
            links.add(Chunk.NEWLINE);
            links.add(new Chunk(unterzeile, unterFont));
        }
        PdfPCell linkeZelle = new PdfPCell(links);
        linkeZelle.setBorder(Rectangle.NO_BORDER);
        linkeZelle.setColspan(2);
        linkeZelle.setLeading(0f, 1.25f);
        linkeZelle.setPaddingTop(4f);
        linkeZelle.setPaddingBottom(3f);
        sumTable.addCell(linkeZelle);

        Phrase rechts = new Phrase(new Chunk(bruttoText, textFont));
        if (nettoText != null && !nettoText.isBlank()) {
            rechts.add(Chunk.NEWLINE);
            rechts.add(new Chunk(nettoText, nettoFont));
        }
        PdfPCell rechteZelle = new PdfPCell(rechts);
        rechteZelle.setBorder(Rectangle.NO_BORDER);
        rechteZelle.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rechteZelle.setLeading(0f, 1.25f);
        rechteZelle.setPaddingTop(4f);
        rechteZelle.setPaddingBottom(3f);
        sumTable.addCell(rechteZelle);
    }

    /**
     * Zeile im Zahlblock: Label, optionaler Mittelwert (z.B. der Steuersatz) und Betrag.
     */
    private void addZahlZeile(PdfPTable sumTable, String label, String mitte, String betrag,
                              boolean extraAbstand, Font labelFont, Font betragFont) {
        float unten = extraAbstand ? 6f : 3f;

        PdfPCell labelZelle = new PdfPCell(new Phrase(label, labelFont));
        labelZelle.setBorder(Rectangle.NO_BORDER);
        labelZelle.setPaddingTop(4f);
        labelZelle.setPaddingBottom(unten);
        sumTable.addCell(labelZelle);

        PdfPCell mittelZelle = new PdfPCell(new Phrase(mitte != null ? mitte : "", labelFont));
        mittelZelle.setBorder(Rectangle.NO_BORDER);
        mittelZelle.setHorizontalAlignment(Element.ALIGN_RIGHT);
        mittelZelle.setPaddingTop(4f);
        mittelZelle.setPaddingBottom(unten);
        sumTable.addCell(mittelZelle);

        PdfPCell betragZelle = new PdfPCell(new Phrase(betrag, betragFont));
        betragZelle.setBorder(Rectangle.NO_BORDER);
        betragZelle.setHorizontalAlignment(Element.ALIGN_RIGHT);
        betragZelle.setPaddingTop(4f);
        betragZelle.setPaddingBottom(unten);
        sumTable.addCell(betragZelle);
    }

    /**
     * Rendert die Bauabschnitt-Übersicht im CLOSURE-Block.
     * Listet alle Bauabschnitte mit Position und Summe auf,
     * plus "Sonstige Leistungen" für Services außerhalb von Bauabschnitten.
     * Nur gerendert wenn es mindestens einen Bauabschnitt gibt.
     */
    @SuppressWarnings("unused") // Aktuell deaktiviert (s. addMixedContent CLOSURE-Branch). Bewusst behalten.
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
        Font headerFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 9, textColor);

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
        Font posFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 9, textColor);
        Font labelFont = FontFactory.getFont(FontFactory.TIMES_ROMAN, 10, textColor);
        Font valueFont = FontFactory.getFont(FontFactory.TIMES_BOLD, 10, textColor);

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
            Font sonstigeFont = FontFactory.getFont(FontFactory.TIMES_ITALIC, 10, textColor);

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
                ? FontFactory.getFont(FontFactory.TIMES_BOLD, fontSizePt, textColor)
                : FontFactory.getFont(FontFactory.TIMES_ROMAN, fontSizePt, textColor);
        
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

        Paragraph absender = new Paragraph("Kuhn Gerüstbau GmbH • Musterstraße 1 • 12345 Musterstadt", FONT_SMALL);
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
        footerText.add(new Chunk("Kuhn Gerüstbau GmbH | Musterstraße 1, 12345 Musterstadt\n", FONT_SMALL));
        footerText.add(
                new Chunk("Bankverbindung: Sparkasse Musterstadt | IBAN: DE12 3456 7890 1234 5678 90 | BIC: ABCDEFGH\n",
                        FONT_SMALL));
        footerText.add(new Chunk(
                "Geschäftsführer: Max Kuhn | Amtsgericht Musterstadt HRB 12345 | USt-IdNr.: DE123456789", FONT_SMALL));

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
     *   {{DOKUMENTTYP}}          → Typ des Dokuments (Rechnung, Anfrage, etc.)
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
        placeholders.put("BEZUGSDOKUMENTTYP",    nullSafe(kopf.bezugsdokumentTyp()));
        placeholders.put("BEZUGSDOKUMENTDATUM",  nullSafe(kopf.bezugsdokumentDatum()));
        placeholders.put("PROJEKTNUMMER",        nullSafe(kopf.projektnummer()));
        placeholders.put("BAUVORHABEN",          nullSafe(kopf.bauvorhaben()));
        placeholders.put("BETREFF",              nullSafe(kopf.betreff()));
        placeholders.put("ZAHLUNGSZIEL_TAGE",    kopf.zahlungszielTage() != null ? String.valueOf(kopf.zahlungszielTage()) : "");
        placeholders.put("ANSPRECHPARTNER",      ""); // Kunden-Ansprechpartner – wird ggf. vom Frontend befüllt

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

            return FontFactory.getFont(FontFactory.TIMES_ROMAN, fontSize, style, color);
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
                FontFactory.TIMES_BOLD, 48, Font.BOLD, new Color(200, 200, 200, 100));
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
                bf = BaseFont.createFont(BaseFont.TIMES_ROMAN, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
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
