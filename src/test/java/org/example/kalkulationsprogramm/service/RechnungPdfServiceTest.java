package org.example.kalkulationsprogramm.service;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.example.kalkulationsprogramm.service.RechnungPdfService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit-Tests für RechnungPdfService.
 * 
 * Testet den kompletten PDF-Generierungs-Flow:
 * - FormBlocks (Formularwesen-Blöcke) werden korrekt gerendert
 * - Platzhalter werden korrekt aufgelöst
 * - Verschiedene Block-Typen (text, doknr, adresse, datum, etc.) erscheinen im PDF
 * - Watermark-Blöcke werden nur für explizite Watermarks gerendert
 * - Content-Blöcke (TEXT, SERVICE, CLOSURE) werden korrekt verarbeitet
 */
class RechnungPdfServiceTest {

    private RechnungPdfService service;

    @BeforeEach
    void setUp() {
        service = new RechnungPdfService();
    }

    // ======================= Test-Daten Hilfsmethoden =======================

    private KopfdatenDto createTestKopfdaten() {
        return new KopfdatenDto(
                "RE-2026/03/00042",
                LocalDate.of(2026, 3, 2),
                LocalDate.of(2026, 2, 28),
                "Wiesner GmbH",
                "Wiesner GmbH\nHauptstraße 3\n97295 Waldbrunn",
                "Gerüstbauarbeiten Hauptstraße 3",
                "KD-10023",
                "Rechnung",
                "AN-2026-001",
                "PRJ-2026-0815",
                "Anbau Hauptstraße 3"
        );
    }

    private LayoutDto createTestLayout() {
        return new LayoutDto(
                new RectDto(40, 120, 555, 500),  // Content Seite 1
                new RectDto(40, 50, 555, 780),   // Content Folgeseiten
                new RectDto(50, 750, 550, 840),  // Header
                new RectDto(50, 20, 550, 100),   // Footer
                null
        );
    }

    /**
     * Erstellt ein realistisches Set von FormBlocks wie sie vom DocumentEditor kommen.
     * Simuliert: Template mit allen Standard-Block-Typen, Content bereits vom Frontend befüllt.
     */
    private List<FormBlockDto> createRealisticFormBlocks(KopfdatenDto kopf, boolean isPreview) {
        List<FormBlockDto> blocks = new ArrayList<>();

        // Heading-Block (z.B. Firmenname)
        blocks.add(new FormBlockDto("blk-1", "heading", 1, 24, 24, 300, 72,
                "Musterfirma GmbH",
                Map.of("fontSize", 20, "fontWeight", "700", "color", "#111827")));

        // Dokumenttyp-Block
        blocks.add(new FormBlockDto("blk-2", "dokumenttyp", 1, 400, 24, 160, 48,
                kopf.dokumentTyp(),
                Map.of("fontSize", 16, "fontWeight", "700", "color", "#111827")));

        // Dokumentnummer-Block (doknr) - Wie vom DocumentEditor befüllt
        String doknr = kopf.rechnungsnummer() != null ? kopf.rechnungsnummer() : (isPreview ? "VORSCHAU" : "ENTWURF");
        blocks.add(new FormBlockDto("blk-3", "doknr", 1, 400, 80, 160, 52,
                doknr,
                Map.of("fontSize", 12, "fontWeight", "600", "color", "#111827")));

        // Datum-Block
        String datum = kopf.rechnungsDatum() != null
                ? kopf.rechnungsDatum().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                : "02.03.2026";
        blocks.add(new FormBlockDto("blk-4", "datum", 1, 400, 140, 160, 48,
                datum,
                Map.of("fontSize", 12, "fontWeight", "600", "color", "#111827")));

        // Kundennummer-Block
        blocks.add(new FormBlockDto("blk-5", "kundennummer", 1, 400, 200, 160, 52,
                kopf.kundennummer() != null ? kopf.kundennummer() : "",
                Map.of("fontSize", 12, "fontWeight", "600", "color", "#111827")));

        // Projektnummer-Block
        blocks.add(new FormBlockDto("blk-6", "projektnr", 1, 400, 260, 160, 52,
                kopf.projektnummer() != null ? kopf.projektnummer() : "",
                Map.of("fontSize", 12, "fontWeight", "600", "color", "#111827")));

        // Adresse-Block
        blocks.add(new FormBlockDto("blk-7", "adresse", 1, 24, 200, 280, 150,
                kopf.kundenAdresse() != null ? kopf.kundenAdresse() : "",
                Map.of("fontSize", 13, "fontWeight", "400", "color", "#111827")));

        // Text-Block mit Platzhaltern (Freitext)
        blocks.add(new FormBlockDto("blk-8", "text", 1, 24, 130, 340, 60,
                "Bauvorhaben: " + (kopf.bauvorhaben() != null ? kopf.bauvorhaben() : ""),
                Map.of("fontSize", 12, "fontWeight", "400", "color", "#111827")));

        // Table-Block (nur für Layout, wird nicht gerendert)
        blocks.add(new FormBlockDto("blk-9", "table", 1, 24, 370, 540, 300,
                "",
                null));

        return blocks;
    }

    /**
     * Erstellt FormBlocks mit unaufgelösten Platzhaltern (wie sie direkt aus der Vorlage kommen).
     * Das Backend muss diese per resolvePlaceholders auflösen.
     */
    private List<FormBlockDto> createFormBlocksWithPlaceholders() {
        List<FormBlockDto> blocks = new ArrayList<>();

        // Text-Block mit {{DOKUMENTNUMMER}} Platzhalter
        blocks.add(new FormBlockDto("blk-ph1", "text", 1, 400, 80, 160, 52,
                "Nr: {{DOKUMENTNUMMER}}",
                Map.of("fontSize", 12, "color", "#111827")));

        // Text-Block mit {{KUNDENADRESSE}} Platzhalter
        blocks.add(new FormBlockDto("blk-ph2", "text", 1, 24, 200, 280, 150,
                "{{KUNDENADRESSE}}",
                Map.of("fontSize", 13, "color", "#111827")));

        // Text-Block mit {{RECHNUNGSNUMMER}} Platzhalter (Alias)
        blocks.add(new FormBlockDto("blk-ph3", "text", 1, 400, 140, 160, 52,
                "Rechnung: {{RECHNUNGSNUMMER}}",
                Map.of("fontSize", 12, "color", "#111827")));

        // Text-Block mit {{DATUM}} Platzhalter
        blocks.add(new FormBlockDto("blk-ph4", "text", 1, 400, 200, 160, 52,
                "Datum: {{DATUM}}",
                Map.of("fontSize", 12, "color", "#111827")));

        // Text-Block mit {{BAUVORHABEN}} und {{PROJEKTNUMMER}} Platzhaltern
        blocks.add(new FormBlockDto("blk-ph5", "text", 1, 24, 360, 500, 52,
                "Projekt: {{PROJEKTNUMMER}} - {{BAUVORHABEN}}",
                Map.of("fontSize", 12, "color", "#111827")));

        // Table-Block
        blocks.add(new FormBlockDto("blk-table", "table", 1, 24, 420, 540, 300,
                "", null));

        return blocks;
    }

    private List<ContentBlockDto> createTestContentBlocks() {
        List<ContentBlockDto> blocks = new ArrayList<>();

        // Service-Block 1
        blocks.add(new ContentBlockDto(
                "SERVICE", null, false, 0,
                "1", "Edelstahlgeländer", "Hier Geländer Beschreibung",
                BigDecimal.valueOf(1), "lfm", BigDecimal.valueOf(67), BigDecimal.valueOf(67),
                false, null, null));

        // Text-Block dazwischen
        blocks.add(new ContentBlockDto(
                "TEXT", "<p>Zwischentext: Vielen Dank</p>", false, 10,
                null, null, null, null, null, null, null, false, null, null));

        // Service-Block 2
        blocks.add(new ContentBlockDto(
                "SERVICE", null, false, 0,
                "2", "Edelstahlgeländer 2", null,
                BigDecimal.valueOf(2), "lfm", BigDecimal.valueOf(45), BigDecimal.valueOf(90),
                false, null, null));

        return blocks;
    }

    /**
     * Generiert ein PDF und extrahiert den gesamten Text.
     */
    private String generateAndExtractText(RechnungDto dto) {
        byte[] pdfBytes = service.generatePdfBytes(dto);
        assertNotNull(pdfBytes, "PDF-Bytes dürfen nicht null sein");
        assertTrue(pdfBytes.length > 100, "PDF muss Inhalt haben (>100 bytes), actual: " + pdfBytes.length);

        try {
            PdfReader reader = new PdfReader(pdfBytes);
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                sb.append(extractor.getTextFromPage(i)).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            fail("PDF-Text-Extraktion fehlgeschlagen: " + e.getMessage());
            return "";
        }
    }

    // ======================= Tests =======================

    @Nested
    @DisplayName("FormBlock-Rendering: Typed Blocks (doknr, datum, adresse, ...)")
    class FormBlockTypedTests {

        @Test
        @DisplayName("Alle typed FormBlocks (doknr, datum, kundennummer, projektnr, adresse) erscheinen im PDF")
        void allTypedFormBlocksAppearInPdf() {
            KopfdatenDto kopf = createTestKopfdaten();
            List<FormBlockDto> formBlocks = createRealisticFormBlocks(kopf, false);
            LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(formBlocks, 595f, 842f);

            RechnungDto dto = new RechnungDto(
                    layout, kopf, createTestContentBlocks(), formBlocks,
                    "Vielen Dank für Ihren Auftrag!", null, null);

            String text = generateAndExtractText(dto);
            System.out.println("=== EXTRAHIERTER PDF-TEXT ===");
            System.out.println(text);
            System.out.println("=== ENDE ===");

            // Heading
            assertTrue(text.contains("Musterfirma"), "Heading 'Musterfirma' fehlt im PDF");

            // Dokumenttyp
            assertTrue(text.contains("Rechnung"), "Dokumenttyp 'Rechnung' fehlt im PDF");

            // Dokumentnummer (doknr-Block)
            assertTrue(text.contains("RE-2026/03/00042"), "Dokumentnummer 'RE-2026/03/00042' fehlt im PDF");

            // Datum
            assertTrue(text.contains("02.03.2026"), "Datum '02.03.2026' fehlt im PDF");

            // Kundennummer
            assertTrue(text.contains("KD-10023"), "Kundennummer 'KD-10023' fehlt im PDF");

            // Projektnummer
            assertTrue(text.contains("PRJ-2026-0815"), "Projektnummer 'PRJ-2026-0815' fehlt im PDF");

            // Adresse
            assertTrue(text.contains("Wiesner"), "Kundenname 'Wiesner' fehlt im PDF");
            assertTrue(text.contains("Hauptstraße"), "Straße 'Hauptstraße' fehlt im PDF");
            assertTrue(text.contains("97295"), "PLZ '97295' fehlt im PDF");

            // Bauvorhaben (im Freitext-Block)
            assertTrue(text.contains("Anbau Hauptstraße"), "Bauvorhaben 'Anbau Hauptstraße' fehlt im PDF");
        }

        @Test
        @DisplayName("Vorschau-Modus: doknr zeigt 'VORSCHAU' wenn keine Dokumentnummer vorhanden")
        void previewModeShowsVorschau() {
            KopfdatenDto kopf = new KopfdatenDto(
                    "VORSCHAU", LocalDate.now(), null,
                    "Testkunde", "Testkunde\nTeststraße 1\n12345 Teststadt",
                    "Testbetreff", "KD-99999", "Rechnung",
                    null, null, null);

            List<FormBlockDto> formBlocks = createRealisticFormBlocks(kopf, true);
            LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(formBlocks, 595f, 842f);

            RechnungDto dto = new RechnungDto(
                    layout, kopf, List.of(), formBlocks,
                    null, null, null);

            String text = generateAndExtractText(dto);
            System.out.println("=== VORSCHAU PDF-TEXT ===");
            System.out.println(text);
            System.out.println("=== ENDE ===");

            assertTrue(text.contains("VORSCHAU"), "Vorschau-Marker 'VORSCHAU' fehlt im PDF");
            assertTrue(text.contains("Testkunde"), "Kundenname 'Testkunde' fehlt im PDF");
            assertTrue(text.contains("KD-99999"), "Kundennummer 'KD-99999' fehlt im PDF");
        }
    }

    @Nested
    @DisplayName("Platzhalter-Auflösung in Text-Blöcken")
    class PlaceholderResolutionTests {

        @Test
        @DisplayName("{{DOKUMENTNUMMER}}, {{KUNDENADRESSE}}, {{DATUM}} etc. werden in Text-Blöcken aufgelöst")
        void placeholdersInTextBlocksAreResolved() {
            KopfdatenDto kopf = createTestKopfdaten();
            List<FormBlockDto> formBlocks = createFormBlocksWithPlaceholders();
            LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(formBlocks, 595f, 842f);

            RechnungDto dto = new RechnungDto(
                    layout, kopf, List.of(), formBlocks,
                    null, null, null);

            String text = generateAndExtractText(dto);
            System.out.println("=== PLATZHALTER PDF-TEXT ===");
            System.out.println(text);
            System.out.println("=== ENDE ===");

            // {{DOKUMENTNUMMER}} aufgelöst
            assertTrue(text.contains("RE-2026/03/00042"),
                    "{{DOKUMENTNUMMER}} wurde nicht aufgelöst. Text: " + text);
            assertFalse(text.contains("{{DOKUMENTNUMMER}}"),
                    "{{DOKUMENTNUMMER}} Platzhalter ist noch im PDF vorhanden");

            // {{RECHNUNGSNUMMER}} als Alias aufgelöst
            assertTrue(text.contains("RE-2026/03/00042"),
                    "{{RECHNUNGSNUMMER}} wurde nicht aufgelöst");
            assertFalse(text.contains("{{RECHNUNGSNUMMER}}"),
                    "{{RECHNUNGSNUMMER}} Platzhalter ist noch vorhanden");

            // {{KUNDENADRESSE}} aufgelöst
            assertTrue(text.contains("Wiesner"),
                    "{{KUNDENADRESSE}} wurde nicht aufgelöst");

            // {{DATUM}} aufgelöst
            assertTrue(text.contains("2.3.2026") || text.contains("02.03.2026"),
                    "{{DATUM}} wurde nicht aufgelöst. Text: " + text);

            // {{PROJEKTNUMMER}} und {{BAUVORHABEN}} aufgelöst
            assertTrue(text.contains("PRJ-2026-0815"),
                    "{{PROJEKTNUMMER}} wurde nicht aufgelöst");
            assertTrue(text.contains("Anbau Hauptstraße"),
                    "{{BAUVORHABEN}} wurde nicht aufgelöst");
        }

        @Test
        @DisplayName("Nicht vorhandene Platzhalter werden zu leerem String aufgelöst")
        void emptyValuesResolveToEmptyString() {
            KopfdatenDto kopf = new KopfdatenDto(
                    null, null, null, null, null, null, null, null, null, null, null);

            List<FormBlockDto> formBlocks = List.of(
                    new FormBlockDto("t1", "text", 1, 50, 200, 300, 60,
                            "Doku: {{DOKUMENTNUMMER}} Kunde: {{KUNDENNUMMER}}",
                            Map.of("fontSize", 12, "color", "#000000")),
                    new FormBlockDto("t2", "table", 1, 50, 300, 500, 400, "", null)
            );

            LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(formBlocks, 595f, 842f);
            RechnungDto dto = new RechnungDto(layout, kopf, List.of(), formBlocks, null, null, null);

            // Darf nicht abstürzen bei null-Werten
            byte[] pdfBytes = service.generatePdfBytes(dto);
            assertNotNull(pdfBytes);
            assertTrue(pdfBytes.length > 100);

            String text = generateAndExtractText(dto);
            // Platzhalter sollten durch leere Strings ersetzt sein (nicht als {{...}} bleiben)
            assertFalse(text.contains("{{DOKUMENTNUMMER}}"),
                    "{{DOKUMENTNUMMER}} sollte aufgelöst sein (auch wenn leer)");
            assertFalse(text.contains("{{KUNDENNUMMER}}"),
                    "{{KUNDENNUMMER}} sollte aufgelöst sein (auch wenn leer)");
        }
    }

    @Nested
    @DisplayName("Watermark-Handling")
    class WatermarkTests {

        @Test
        @DisplayName("Nur explizite Watermark-Blöcke (type=watermark oder id=watermark) werden als Watermark gerendert")
        void onlyExplicitWatermarksAreRenderedAsWatermarks() {
            KopfdatenDto kopf = createTestKopfdaten();

            List<FormBlockDto> formBlocks = new ArrayList<>();

            // Normaler Text-Block mit "VORSCHAU" im Content - darf NICHT als Watermark gerendert werden
            formBlocks.add(new FormBlockDto("blk-normal", "text", 1, 400, 80, 160, 52,
                    "Nr: VORSCHAU",
                    Map.of("fontSize", 12, "color", "#111827")));

            // Expliziter Watermark-Block
            formBlocks.add(new FormBlockDto("watermark", "watermark", 1, 100, 300, 400, 200,
                    "VORSCHAU / ENTWURF",
                    null));

            // Adresse (darf nicht zu Watermark werden)
            formBlocks.add(new FormBlockDto("blk-addr", "adresse", 1, 24, 200, 280, 150,
                    "Wiesner GmbH\nHauptstraße 3\n97295 Waldbrunn",
                    Map.of("fontSize", 13, "color", "#111827")));

            // Table
            formBlocks.add(new FormBlockDto("blk-table", "table", 1, 24, 420, 540, 300,
                    "", null));

            LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(formBlocks, 595f, 842f);
            RechnungDto dto = new RechnungDto(
                    layout, kopf, List.of(), formBlocks,
                    null, null, null);

            String text = generateAndExtractText(dto);
            System.out.println("=== WATERMARK TEST PDF-TEXT ===");
            System.out.println(text);
            System.out.println("=== ENDE ===");

            // Der normale Text-Block "Nr: VORSCHAU" muss als normaler Text erscheinen
            assertTrue(text.contains("Nr: VORSCHAU") || text.contains("VORSCHAU"),
                    "Normaler Text-Block mit 'VORSCHAU' muss als Text gerendert werden, nicht als Watermark");

            // Adresse muss vorhanden sein
            assertTrue(text.contains("Wiesner"), "Adresse fehlt im PDF");
            assertTrue(text.contains("97295"), "PLZ fehlt im PDF");
        }
    }

    @Nested
    @DisplayName("Content-Blöcke (Leistungstabelle)")
    class ContentBlockTests {

        @Test
        @DisplayName("SERVICE-Blöcke erscheinen in der Leistungstabelle")
        void serviceBlocksAppearInTable() {
            KopfdatenDto kopf = createTestKopfdaten();
            List<FormBlockDto> formBlocks = List.of(
                    new FormBlockDto("t", "table", 1, 24, 100, 540, 600, "", null));
            LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(formBlocks, 595f, 842f);

            RechnungDto dto = new RechnungDto(
                    layout, kopf, createTestContentBlocks(), formBlocks,
                    "Vielen Dank!", null, null);

            String text = generateAndExtractText(dto);

            // Service-Positionen
            assertTrue(text.contains("Edelstahlgeländer"), "Service-Position 'Edelstahlgeländer' fehlt");
            assertTrue(text.contains("67,00"), "Preis '67,00' fehlt");

            // Zwischentext
            assertTrue(text.contains("Vielen Dank"), "Zwischentext 'Vielen Dank' fehlt");

            // Schlusstext
            assertTrue(text.contains("Vielen Dank"), "Schlusstext fehlt");
        }
    }

    @Nested
    @DisplayName("Reale Template-Simulation (Rechnungen.json)")
    class RealTemplateTests {

        /**
         * Simuliert EXAKT das echte "Rechnungen"-Template aus der Datenbank.
         * Alle Blöcke sind type="text" mit {{PLATZHALTER}} Content.
         * Einige Blöcke haben height=18 bei fontSize=14 — das muss trotzdem rendern!
         */
        @Test
        @DisplayName("Echtes Rechnungen-Template: Kleine Blöcke (h=18, fontSize=14) rendern korrekt")
        void realTemplateSmallBlocksRender() {
            KopfdatenDto kopf = createTestKopfdaten();

            // EXAKT die Blöcke aus dem echten Template:
            List<FormBlockDto> formBlocks = new ArrayList<>();

            // Leistungstabelle
            formBlocks.add(new FormBlockDto("bg-0", "table", 1, 48, 424, 500, 337.78f,
                    "", null));

            // Adresse — 176x90 — funktioniert (groß genug)
            formBlocks.add(new FormBlockDto("bg-1", "text", 1, 48, 162, 176, 90,
                    "Wiesner GmbH\nHauptstraße 3\n97295 Waldbrunn",
                    Map.of("fontSize", 14, "fontWeight", "400", "color", "#111827", "textAlign", "left")));

            // Label "Dokumentnummer:" — 128x18 — KRITISCH!
            formBlocks.add(new FormBlockDto("bg-2", "text", 1, 48, 364, 128, 18,
                    "Dokumentnummer:",
                    Map.of("fontSize", 14, "fontWeight", "400", "color", "#111827", "textAlign", "left")));

            // Wert {{DOKUMENTNUMMER}} → aufgelöst — 128x18 — KRITISCH!
            formBlocks.add(new FormBlockDto("bg-3", "text", 1, 176, 364, 128, 18,
                    "RE-2026/03/00042",
                    Map.of("fontSize", 14, "fontWeight", "400", "color", "#111827", "textAlign", "left")));

            // Label "Kundennummer:" — 128x18 — KRITISCH!
            formBlocks.add(new FormBlockDto("bg-4", "text", 1, 319, 364, 128, 18,
                    "Kundennummer:",
                    Map.of("fontSize", 14, "fontWeight", "400", "color", "#111827", "textAlign", "left")));

            // Wert {{KUNDENNUMMER}} → aufgelöst — 80x18 — KRITISCH!
            formBlocks.add(new FormBlockDto("bg-5", "text", 1, 439, 364, 80, 18,
                    "KD-10023",
                    Map.of("fontSize", 14, "fontWeight", "400", "color", "#111827", "textAlign", "left")));

            // Label "Erstellungsdatum:" — 120x18 — KRITISCH!
            formBlocks.add(new FormBlockDto("bg-6", "text", 1, 319, 382, 120, 18,
                    "Erstellungsdatum:",
                    Map.of("fontSize", 14, "fontWeight", "400", "color", "#111827", "textAlign", "left")));

            // Wert {{DATUM}} → aufgelöst — 77x18 — KRITISCH!
            formBlocks.add(new FormBlockDto("bg-7", "text", 1, 439, 382, 77, 18,
                    "02.03.2026",
                    Map.of("fontSize", 14, "fontWeight", "400", "color", "#111827", "textAlign", "left")));

            // Dokumenttyp — 128x25
            formBlocks.add(new FormBlockDto("bg-8", "text", 1, 48, 320, 128, 25,
                    "Rechnung",
                    Map.of("fontSize", 20, "fontWeight", "400", "color", "#bc2f49", "textAlign", "left")));

            // Label "Bezugsdokument:" — 128x18 — KRITISCH!
            formBlocks.add(new FormBlockDto("bg-9", "text", 1, 48, 382, 128, 18,
                    "Bezugsdokument:",
                    Map.of("fontSize", 14, "fontWeight", "400", "color", "#111827", "textAlign", "left")));

            // Wert {{BEZUGSDOKUMENT}} → aufgelöst — 128x18 — KRITISCH!
            formBlocks.add(new FormBlockDto("bg-10", "text", 1, 176, 382, 128, 18,
                    "AN-2026-001",
                    Map.of("fontSize", 14, "fontWeight", "400", "color", "#111827", "textAlign", "left")));

            LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(formBlocks, 595f, 842f);
            RechnungDto dto = new RechnungDto(
                    layout, kopf, createTestContentBlocks(), formBlocks,
                    "Vielen Dank!", null, null);

            String text = generateAndExtractText(dto);
            System.out.println("=== REAL TEMPLATE PDF-TEXT ===");
            System.out.println(text);
            System.out.println("=== ENDE ===");

            // Alle Felder mit height=18 MÜSSEN im PDF erscheinen
            assertAll("Alle Felder aus dem echten Template müssen im PDF erscheinen",
                    () -> assertTrue(text.contains("Dokumentnummer"),
                            "Label 'Dokumentnummer:' fehlt (h=18)! PDF-Text:\n" + text),
                    () -> assertTrue(text.contains("RE-2026/03/00042"),
                            "Dokumentnummer-Wert fehlt (h=18)! PDF-Text:\n" + text),
                    () -> assertTrue(text.contains("Kundennummer"),
                            "Label 'Kundennummer:' fehlt (h=18)! PDF-Text:\n" + text),
                    () -> assertTrue(text.contains("KD-10023"),
                            "Kundennummer-Wert fehlt (h=18)! PDF-Text:\n" + text),
                    () -> assertTrue(text.contains("Erstellungsdatum"),
                            "Label 'Erstellungsdatum:' fehlt (h=18)! PDF-Text:\n" + text),
                    () -> assertTrue(text.contains("02.03.2026"),
                            "Datum-Wert fehlt (h=18)! PDF-Text:\n" + text),
                    () -> assertTrue(text.contains("Rechnung"),
                            "Dokumenttyp fehlt (h=25)"),
                    () -> assertTrue(text.contains("Bezugsdokument"),
                            "Label 'Bezugsdokument:' fehlt (h=18)! PDF-Text:\n" + text),
                    () -> assertTrue(text.contains("AN-2026-001"),
                            "Bezugsdokument-Wert fehlt (h=18)! PDF-Text:\n" + text),
                    () -> assertTrue(text.contains("Wiesner"),
                            "Adresse fehlt (h=90)"),
                    () -> assertTrue(text.contains("97295"),
                            "PLZ fehlt")
            );
        }

        /**
         * Simuliert den Seitenzahl-Block aus dem echten Template:
         * type="text", content="{{SEITENZAHL}}", height=18, fontSize=14.
         * 
         * Die {{SEITENZAHL}} muss als "X / Y" gerendert werden.
         * Gleiche Leading-Problematik: h=18 < fontSize*1.5=21 → Phrase-Default versagt.
         */
        @Test
        @DisplayName("Seitenzahl-Block (h=18, fontSize=14, {{SEITENZAHL}}) rendert korrekt")
        void seitenzahlPlaceholderSmallBlockRenders() {
            KopfdatenDto kopf = createTestKopfdaten();

            List<FormBlockDto> formBlocks = new ArrayList<>();

            // Table-Block (Layout)
            formBlocks.add(new FormBlockDto("tbl", "table", 1, 48, 424, 500, 337.78f,
                    "", null));

            // Label "Seite:" — 50x18, fontSize=14 — KRITISCH!
            formBlocks.add(new FormBlockDto("seite-label", "text", 1, 319, 327, 50, 18,
                    "Seite:",
                    Map.of("fontSize", 14, "fontWeight", "400", "color", "#111827", "textAlign", "left")));

            // Seitenzahl-Platzhalter "{{SEITENZAHL}}" — 50x18, fontSize=14 — KRITISCH!
            formBlocks.add(new FormBlockDto("seite-nr", "text", 1, 360, 327, 50, 18,
                    "{{SEITENZAHL}}",
                    Map.of("fontSize", 14, "fontWeight", "400", "color", "#111827", "textAlign", "left")));

            LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(formBlocks, 595f, 842f);
            RechnungDto dto = new RechnungDto(
                    layout, kopf, createTestContentBlocks(), formBlocks,
                    "Danke!", null, null);

            String text = generateAndExtractText(dto);
            System.out.println("=== SEITENZAHL PDF-TEXT ===");
            System.out.println(text);
            System.out.println("=== ENDE ===");

            assertAll("Seitenzahl muss im PDF erscheinen",
                    () -> assertTrue(text.contains("Seite"),
                            "Label 'Seite:' fehlt (h=18)! PDF-Text:\n" + text),
                    () -> assertTrue(text.contains("1"),
                            "Seitennummer '1' fehlt! PDF-Text:\n" + text)
            );
        }

        /**
         * Testet typed seitenzahl-Block (type="seitenzahl") mit kleiner Höhe.
         */
        @Test
        @DisplayName("Typed Seitenzahl-Block (type=seitenzahl, h=18) rendert korrekt")
        void typedSeitenzahlSmallBlockRenders() {
            KopfdatenDto kopf = createTestKopfdaten();

            List<FormBlockDto> formBlocks = new ArrayList<>();

            // Table-Block (Layout)
            formBlocks.add(new FormBlockDto("tbl", "table", 1, 48, 424, 500, 337.78f,
                    "", null));

            // Typed Seitenzahl-Block — 120x18, fontSize=14 — KRITISCH!
            formBlocks.add(new FormBlockDto("sz-1", "seitenzahl", 1, 400, 327, 120, 18,
                    "",
                    Map.of("fontSize", 14, "fontWeight", "400", "color", "#111827")));

            LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(formBlocks, 595f, 842f);
            RechnungDto dto = new RechnungDto(
                    layout, kopf, createTestContentBlocks(), formBlocks,
                    "Danke!", null, null);

            String text = generateAndExtractText(dto);
            System.out.println("=== TYPED SEITENZAHL PDF-TEXT ===");
            System.out.println(text);
            System.out.println("=== ENDE ===");

            assertAll("Typed Seitenzahl muss im PDF erscheinen",
                    () -> assertTrue(text.contains("Seite"),
                            "'Seite:' fehlt bei typed seitenzahl-Block! PDF-Text:\n" + text),
                    () -> assertTrue(text.contains("1"),
                            "Seitennummer '1' fehlt! PDF-Text:\n" + text)
            );
        }
    }

    @Nested
    @DisplayName("Layout-Konvertierung")
    class LayoutTests {

        @Test
        @DisplayName("convertFormBlockToRect konvertiert CSS-Koordinaten korrekt zu PDF-Koordinaten")
        void convertFormBlockToRectCorrectly() {
            // Block oben-links: CSS x=0, y=0, w=100, h=50
            FormBlockDto topLeft = new FormBlockDto("t", "text", 1, 0, 0, 100, 50, "", null);
            RectDto rect = RechnungPdfService.convertFormBlockToRect(topLeft, 595f, 842f);
            assertEquals(0, rect.llx(), 0.1, "llx sollte 0 sein");
            assertEquals(842 - 50, rect.lly(), 0.1, "lly sollte 842-50=792 sein (PDF-Koordinaten)");
            assertEquals(100, rect.urx(), 0.1, "urx sollte 100 sein");
            assertEquals(842, rect.ury(), 0.1, "ury sollte 842 sein (oberer Rand)");

            // Block mittig: CSS x=200, y=400, w=195, h=42
            FormBlockDto middle = new FormBlockDto("m", "text", 1, 200, 400, 195, 42, "", null);
            RectDto rectM = RechnungPdfService.convertFormBlockToRect(middle, 595f, 842f);
            assertEquals(200, rectM.llx(), 0.1);
            assertEquals(395, rectM.urx(), 0.1);
            assertEquals(842 - 442, rectM.lly(), 0.1, "lly = 842 - (400+42)");
            assertEquals(842 - 400, rectM.ury(), 0.1, "ury = 842 - 400");
        }

        @Test
        @DisplayName("createLayoutFromFormBlocks findet den table-Block für Content-Bereich")
        void createLayoutFromFormBlocksFindsTable() {
            List<FormBlockDto> blocks = List.of(
                    new FormBlockDto("h", "heading", 1, 24, 24, 300, 72, "Test", null),
                    new FormBlockDto("t", "table", 1, 40, 200, 515, 500, "", null)
            );

            LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(blocks, 595f, 842f);
            assertNotNull(layout.page1Rect());

            // Der Content-Bereich sollte dem table-Block entsprechen
            RectDto tableRect = RechnungPdfService.convertFormBlockToRect(blocks.get(1), 595f, 842f);
            assertEquals(tableRect.llx(), layout.page1Rect().llx(), 0.1);
            assertEquals(tableRect.urx(), layout.page1Rect().urx(), 0.1);
        }
    }

    @Nested
    @DisplayName("Vollständiger End-to-End Test (wie DocumentEditor)")
    class EndToEndTests {

        @Test
        @DisplayName("Simuliert kompletten DocumentEditor-Preview-Flow mit allen Daten")
        void fullDocumentEditorPreviewFlow() {
            // 1. Kopfdaten wie vom DocumentEditor erstellt
            KopfdatenDto kopf = new KopfdatenDto(
                    "RE-2026/03/00042",       // dokumentnummer
                    LocalDate.of(2026, 3, 2),  // rechnungsDatum
                    LocalDate.of(2026, 2, 28), // leistungsDatum
                    "Wiesner GmbH",            // kundenName
                    "Wiesner GmbH\nHauptstraße 3\n97295 Waldbrunn",  // kundenAdresse
                    "Gerüstbauarbeiten Hauptstraße 3",  // betreff
                    "KD-10023",                // kundennummer
                    "Rechnung",                // dokumentTyp
                    "AN-2026-001",             // bezugsdokument
                    "PRJ-2026-0815",           // projektnummer
                    "Anbau Hauptstraße 3"      // bauvorhaben
            );

            // 2. FormBlocks wie vom Frontend befüllt (typed blocks mit Content)
            List<FormBlockDto> formBlocks = new ArrayList<>();

            formBlocks.add(new FormBlockDto("blk-0", "heading", 1, 24, 24, 300, 60,
                    "Musterfirma GmbH",
                    Map.of("fontSize", 20, "fontWeight", "700", "color", "#111827")));

            formBlocks.add(new FormBlockDto("blk-1", "dokumenttyp", 1, 400, 24, 160, 48,
                    "Rechnung",
                    Map.of("fontSize", 16, "fontWeight", "700", "color", "#111827")));

            formBlocks.add(new FormBlockDto("blk-2", "doknr", 1, 400, 80, 160, 52,
                    "RE-2026/03/00042",
                    Map.of("fontSize", 12, "fontWeight", "600", "color", "#111827")));

            formBlocks.add(new FormBlockDto("blk-3", "datum", 1, 400, 140, 160, 48,
                    "02.03.2026",
                    Map.of("fontSize", 12, "fontWeight", "600", "color", "#111827")));

            formBlocks.add(new FormBlockDto("blk-4", "kundennummer", 1, 400, 200, 160, 52,
                    "KD-10023",
                    Map.of("fontSize", 12, "fontWeight", "600", "color", "#111827")));

            formBlocks.add(new FormBlockDto("blk-5", "projektnr", 1, 400, 260, 160, 52,
                    "PRJ-2026-0815",
                    Map.of("fontSize", 12, "fontWeight", "600", "color", "#111827")));

            formBlocks.add(new FormBlockDto("blk-6", "adresse", 1, 24, 200, 280, 150,
                    "Wiesner GmbH\nHauptstraße 3\n97295 Waldbrunn",
                    Map.of("fontSize", 13, "fontWeight", "400", "color", "#111827")));

            formBlocks.add(new FormBlockDto("blk-7", "text", 1, 24, 360, 300, 40,
                    "Bauvorhaben: Anbau Hauptstraße 3",
                    Map.of("fontSize", 12, "fontWeight", "400", "color", "#111827")));

            // Watermark (Preview)
            formBlocks.add(new FormBlockDto("watermark", "watermark", 1, 100, 300, 400, 200,
                    "VORSCHAU / ENTWURF\nKein Beleg", null));

            // Table
            formBlocks.add(new FormBlockDto("blk-9", "table", 1, 24, 420, 540, 300,
                    "", null));

            // 3. Content-Blöcke
            List<ContentBlockDto> contentBlocks = List.of(
                    new ContentBlockDto("SERVICE", null, false, 0,
                            "1", "Edelstahlgeländer", "Hier Geländer Bratan",
                            BigDecimal.valueOf(1), "lfm", BigDecimal.valueOf(67), BigDecimal.valueOf(67), false, null, null),
                    new ContentBlockDto("SERVICE", null, false, 0,
                            "2", "Edelstahlgeländer 2", null,
                            BigDecimal.valueOf(1), "lfm", BigDecimal.valueOf(67), BigDecimal.valueOf(67), false, null, null)
            );

            // 4. Layout aus FormBlocks erstellen
            LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(formBlocks, 595f, 842f);

            // 5. RechnungDto zusammenbauen
            RechnungDto dto = new RechnungDto(
                    layout, kopf, contentBlocks, formBlocks,
                    "Vielen Dank für Ihren Auftrag!", null, null);

            // 6. PDF generieren und Text extrahieren
            String text = generateAndExtractText(dto);

            System.out.println("=== FULL E2E PDF-TEXT ===");
            System.out.println(text);
            System.out.println("=== ENDE ===");

            // 7. ALLE wichtigen Felder prüfen
            assertAll("Alle Dokument-Felder müssen im PDF erscheinen",
                    // Header/Firmenname
                    () -> assertTrue(text.contains("Musterfirma"),
                            "Firmenname 'Musterfirma' fehlt"),

                    // Dokumenttyp
                    () -> assertTrue(text.contains("Rechnung"),
                            "Dokumenttyp 'Rechnung' fehlt"),

                    // Dokumentnummer
                    () -> assertTrue(text.contains("RE-2026/03/00042"),
                            "Dokumentnummer 'RE-2026/03/00042' fehlt! Text:\n" + text),

                    // Datum
                    () -> assertTrue(text.contains("02.03.2026"),
                            "Datum '02.03.2026' fehlt! Text:\n" + text),

                    // Kundennummer
                    () -> assertTrue(text.contains("KD-10023"),
                            "Kundennummer 'KD-10023' fehlt! Text:\n" + text),

                    // Projektnummer
                    () -> assertTrue(text.contains("PRJ-2026-0815"),
                            "Projektnummer 'PRJ-2026-0815' fehlt! Text:\n" + text),

                    // Kundenadresse
                    () -> assertTrue(text.contains("Wiesner"),
                            "Kundenname 'Wiesner' fehlt"),
                    () -> assertTrue(text.contains("97295"),
                            "PLZ '97295' fehlt"),

                    // Bauvorhaben
                    () -> assertTrue(text.contains("Anbau Hauptstraße"),
                            "Bauvorhaben fehlt"),

                    // Leistungen
                    () -> assertTrue(text.contains("Edelstahlgeländer"),
                            "Leistung 'Edelstahlgeländer' fehlt"),
                    () -> assertTrue(text.contains("67,00"),
                            "Preis '67,00' fehlt")
            );
        }
    }
}
