package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.dto.Zugferd.ZugferdDaten;
import org.example.kalkulationsprogramm.service.RechnungPdfService;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.FormBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.KopfdatenDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.LayoutDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.RechnungDto;
import org.example.kalkulationsprogramm.service.ZugferdErstellService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller für den Dokument-Generator (/document-builder).
 * Nimmt Dokument-Daten vom Frontend entgegen und generiert PDFs.
 */
@RestController
@RequestMapping("/api/dokument-generator")
@RequiredArgsConstructor
@Slf4j
public class DokumentGeneratorController {

    private final RechnungPdfService rechnungPdfService;
    private final ZugferdErstellService zugferdErstellService;

    // ======================= Request DTOs =======================

    /**
     * Haupt-Request vom Frontend DocumentBuilder
     */
    public record GeneratePdfRequest(
            String dokumentTyp, // ANFRAGE, RECHNUNG, etc.
            String templateName, // Name der verwendeten Vorlage
            KopfdatenRequest kopfdaten,
            List<FormBlockRequest> layoutBlocks, // FormBlocks aus der Vorlage
            List<DocBlockRequest> contentBlocks, // Inhalt (TEXT/SERVICE)
            String schlusstext,
            String backgroundImagePage1, // Base64-encoded Hintergrundbild Seite 1
            String backgroundImagePage2, // Base64-encoded Hintergrundbild Seite 2+
            Double globalRabattProzent, // Globaler Rabatt in % (0-100)
            AbrechnungsverlaufRequest abrechnungsverlauf, // Abrechnungsverlauf für Rechnungsabzüge
            Double betragNetto, // Überschreibt berechnete Nettosumme (z.B. für Abschlagsrechnungen)
            AbschlagInfoRequest abschlagInfo) { // Info zum Abschlag-Eingabemodus
    }

    public record AbschlagInfoRequest(
            String modus,       // "prozent", "netto", "brutto"
            Double eingabeWert) { // Originalwert der Eingabe (z.B. 30 bei 30%)
    }

    public record AbrechnungsverlaufRequest(
            String basisdokumentNummer,
            String basisdokumentTyp,
            String basisdokumentDatum,
            Double basisdokumentBetragNetto,
            List<AbrechnungspositionRequest> positionen) {
    }

    public record AbrechnungspositionRequest(
            String dokumentNummer,
            String typ,
            String datum, // ISO-String
            Double betragNetto,
            Integer abschlagsNummer) {
    }

    public record KopfdatenRequest(
            String dokumentnummer,
            String rechnungsDatum, // ISO-String "2024-12-10"
            String leistungsDatum,
            String kundenName,
            String kundenAdresse,
            String betreff,
            String kundennummer,
            String bezugsdokument,
            String projektnummer,
            String bauvorhaben,
            String bezugsdokumentTyp,
            String bezugsdokumentDatum,
            Integer zahlungszielTage) {
    }

    public record FormBlockRequest(
            String id,
            String type,
            int page,
            float x,
            float y,
            float width,
            float height,
            String content,
            java.util.Map<String, Object> styles) {
    }

    public record DocBlockRequest(
            String id,
            String type, // TEXT, SERVICE, CLOSURE, SEPARATOR, SECTION_HEADER, SUBTOTAL
            String content, // HTML-Content für TEXT
            String pos, // Positionsnummer für SERVICE
            String title, // Titel für SERVICE
            Double quantity, // Menge
            String unit, // Einheit
            Double price, // Einzelpreis
            String description, // Beschreibung
            Boolean optional, // Ist alternativ/optional
            Integer fontSize, // Schriftgröße für TEXT
            Boolean fett, // Fett für TEXT
            String sectionLabel, // Label für SECTION_HEADER
            Double discount // Rabatt in % pro Position (0-100)
    ) {
    }

    // ======================= Endpoints =======================

    /**
     * POST /api/dokument-generator/pdf
     * Generiert ein PDF aus den übergebenen Dokument-Daten
     */
    @PostMapping("/pdf")
    public ResponseEntity<byte[]> generatePdf(@RequestBody GeneratePdfRequest request) {
        try {
            log.info("PDF-Generierung für {} gestartet", request.dokumentTyp());

            // 1. Layout aus FormBlocks konvertieren
            List<FormBlockDto> formBlocks = request.layoutBlocks().stream()
                    .map(fb -> new FormBlockDto(
                            fb.id(), fb.type(), fb.page(),
                            fb.x(), fb.y(), fb.width(), fb.height(),
                            fb.content(), fb.styles()))
                    .toList();

            LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(formBlocks, 595f, 842f);

            // 2. Kopfdaten konvertieren
            // Dokumenttyp-Label ermitteln
            String dokumentTypLabel = switch (request.dokumentTyp() != null ? request.dokumentTyp() : "") {
                case "ANGEBOT" -> "Angebot";
                case "AUFTRAGSBESTAETIGUNG" -> "Auftragsbestätigung";
                case "RECHNUNG" -> "Rechnung";
                case "TEILRECHNUNG" -> "Teilrechnung";
                case "ABSCHLAGSRECHNUNG" -> "Abschlagsrechnung";
                case "SCHLUSSRECHNUNG" -> "Schlussrechnung";
                case "GUTSCHRIFT" -> "Gutschrift";
                case "STORNO" -> "Stornorechnung";
                default -> request.dokumentTyp() != null ? request.dokumentTyp() : "";
            };

            KopfdatenDto kopfdaten = new KopfdatenDto(
                    request.kopfdaten().dokumentnummer(),
                    parseDate(request.kopfdaten().rechnungsDatum()),
                    parseDate(request.kopfdaten().leistungsDatum()),
                    request.kopfdaten().kundenName(),
                    request.kopfdaten().kundenAdresse(),
                    request.kopfdaten().betreff(),
                    request.kopfdaten().kundennummer(),
                    dokumentTypLabel,
                    request.kopfdaten().bezugsdokument(),
                    request.kopfdaten().projektnummer(),
                    request.kopfdaten().bauvorhaben(),
                    request.kopfdaten().bezugsdokumentTyp(),
                    request.kopfdaten().bezugsdokumentDatum(),
                    request.kopfdaten().zahlungszielTage());

            // 3. Content-Blöcke in originaler Reihenfolge konvertieren (TEXT + SERVICE
            // gemischt)
            List<ContentBlockDto> contentBlocks = new java.util.ArrayList<>();

            for (var b : request.contentBlocks()) {
                if ("TEXT".equals(b.type())) {
                    contentBlocks.add(new ContentBlockDto(
                            "TEXT",
                            b.content(), // HTML beibehalten (wird im Service geparst)
                            Boolean.TRUE.equals(b.fett()),
                            b.fontSize() != null ? b.fontSize() : 10,
                            null, null, null, null, null, null, null, false, null, null));
                } else if ("SERVICE".equals(b.type())) {
                    BigDecimal menge = b.quantity() != null ? BigDecimal.valueOf(b.quantity()) : BigDecimal.ONE;
                    BigDecimal einzelpreis = b.price() != null ? BigDecimal.valueOf(b.price()) : BigDecimal.ZERO;
                    BigDecimal gesamt = menge.multiply(einzelpreis);

                    String titel = b.title() != null ? b.title() : "";
                    String beschreibungHtml = b.description(); // HTML mit Formatierung behalten

                    // Use hierarchical position from frontend (e.g. "1.1", "2")
                    String posStr = b.pos() != null ? b.pos() : "";

                    // Rabatt berechnen
                    BigDecimal rabattProzent = b.discount() != null && b.discount() > 0
                            ? BigDecimal.valueOf(b.discount()) : null;
                    if (rabattProzent != null) {
                        BigDecimal rabattFaktor = BigDecimal.ONE.subtract(
                                rabattProzent.divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP));
                        gesamt = gesamt.multiply(rabattFaktor).setScale(2, java.math.RoundingMode.HALF_UP);
                    }

                    contentBlocks.add(new ContentBlockDto(
                            "SERVICE",
                            null, false, 0,
                            posStr,
                            titel,
                            beschreibungHtml,
                            menge,
                            b.unit() != null ? b.unit() : "Stk",
                            einzelpreis,
                            gesamt,
                            Boolean.TRUE.equals(b.optional()),
                            null,
                            rabattProzent));
                } else if ("CLOSURE".equals(b.type())) {
                    contentBlocks.add(new ContentBlockDto(
                            "CLOSURE",
                            null, false, 0,
                            null, null, null, null, null, null, null, false, null, null));
                } else if ("SEPARATOR".equals(b.type())) {
                    contentBlocks.add(new ContentBlockDto(
                            "SEPARATOR",
                            null, false, 0,
                            null, null, null, null, null, null, null, false, null, null));
                } else if ("SECTION_HEADER".equals(b.type())) {
                    String sectionPos = b.pos() != null ? b.pos() : "";
                    contentBlocks.add(new ContentBlockDto(
                            "SECTION_HEADER",
                            null, false, 0,
                            sectionPos, null, null, null, null, null, null, false,
                            b.sectionLabel() != null ? b.sectionLabel() : "", null));
                } else if ("SUBTOTAL".equals(b.type())) {
                    contentBlocks.add(new ContentBlockDto(
                            "SUBTOTAL",
                            null, false, 0,
                            null, null, null, null, null, null, null, false, null, null));
                }
            }

            // 4. RechnungDto zusammenbauen
            BigDecimal globalRabatt = request.globalRabattProzent() != null && request.globalRabattProzent() > 0
                    ? BigDecimal.valueOf(request.globalRabattProzent()) : null;

            // Abrechnungsverlauf konvertieren (falls vorhanden)
            RechnungPdfService.AbrechnungsverlaufPdfDto abrechnungsverlaufPdf = null;
            if (request.abrechnungsverlauf() != null && request.abrechnungsverlauf().basisdokumentBetragNetto() != null
                    && request.abrechnungsverlauf().basisdokumentBetragNetto() > 0) {
                var avReq = request.abrechnungsverlauf();
                List<RechnungPdfService.AbrechnungspositionPdfDto> posList = avReq.positionen() != null ? avReq.positionen().stream().map(p -> new RechnungPdfService.AbrechnungspositionPdfDto(
                        p.dokumentNummer(),
                        p.typ(),
                        p.datum() != null ? LocalDate.parse(p.datum()) : null,
                        p.betragNetto() != null ? BigDecimal.valueOf(p.betragNetto()) : BigDecimal.ZERO,
                        p.abschlagsNummer()
                )).toList() : List.of();
                abrechnungsverlaufPdf = new RechnungPdfService.AbrechnungsverlaufPdfDto(
                        avReq.basisdokumentNummer(),
                        avReq.basisdokumentTyp(),
                        avReq.basisdokumentDatum() != null && !avReq.basisdokumentDatum().isBlank() ? LocalDate.parse(avReq.basisdokumentDatum()) : null,
                        avReq.basisdokumentBetragNetto() != null ? BigDecimal.valueOf(avReq.basisdokumentBetragNetto()) : BigDecimal.ZERO,
                        posList
                );
            }

            // AbschlagInfo konvertieren (falls vorhanden)
            RechnungPdfService.AbschlagInfoPdfDto abschlagInfoPdf = null;
            if (request.abschlagInfo() != null && request.abschlagInfo().modus() != null) {
                abschlagInfoPdf = new RechnungPdfService.AbschlagInfoPdfDto(
                        request.abschlagInfo().modus(),
                        request.abschlagInfo().eingabeWert() != null ? BigDecimal.valueOf(request.abschlagInfo().eingabeWert()) : null
                );
            }

            RechnungDto rechnungDto = new RechnungDto(
                    layout,
                    kopfdaten,
                    contentBlocks,
                    formBlocks,
                    request.schlusstext(),
                    request.backgroundImagePage1(),
                    request.backgroundImagePage2(),
                    globalRabatt,
                    abrechnungsverlaufPdf,
                    request.betragNetto() != null ? BigDecimal.valueOf(request.betragNetto()) : null,
                    abschlagInfoPdf);

            // 5. PDF generieren
            byte[] pdfBytes = rechnungPdfService.generatePdfBytes(rechnungDto);

            // 6. Response mit PDF
            String rawFilename = request.dokumentTyp().toLowerCase() + "_" +
                    request.kopfdaten().dokumentnummer() + ".pdf";
            String filename = rawFilename.replaceAll("[\\\\/:*?\"<>|]", "_");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.builder("attachment")
                    .filename(filename)
                    .build());
            headers.setContentLength(pdfBytes.length);

            log.info("PDF {} erfolgreich generiert ({} bytes)", filename, pdfBytes.length);
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Fehler bei PDF-Generierung", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * GET /api/dokument-generator/preview
     * Generiert eine PDF-Vorschau (inline statt download)
     */
    @PostMapping("/preview")
    public ResponseEntity<byte[]> previewPdf(@RequestBody GeneratePdfRequest request) {
        ResponseEntity<byte[]> response = generatePdf(request);
        if (response.getStatusCode() == HttpStatus.OK) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.builder("inline").build());
            headers.setContentLength(response.getBody().length);
            return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
        }
        return response;
    }

    /**
     * POST /api/dokument-generator/zugferd-pdf
     * Generiert ein ZUGFeRD-PDF (PDF mit eingebetteten maschinenlesbaren Rechnungsdaten).
     */
    @PostMapping("/zugferd-pdf")
    public ResponseEntity<byte[]> generateZugferdPdf(@RequestBody GeneratePdfRequest request) {
        Path tempPdf = null;
        Path zugferdPdf = null;
        try {
            log.info("ZUGFeRD-PDF-Generierung für {} gestartet", request.dokumentTyp());

            // 1. Normale PDF generieren
            ResponseEntity<byte[]> pdfResponse = generatePdf(request);
            if (pdfResponse.getStatusCode() != HttpStatus.OK || pdfResponse.getBody() == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            // 2. PDF in Temp-Datei speichern
            tempPdf = Files.createTempFile("zugferd-source-", ".pdf");
            Files.write(tempPdf, pdfResponse.getBody());

            // 3. ZugferdDaten aus Request ableiten
            ZugferdDaten daten = new ZugferdDaten();
            daten.setGeschaeftsdokumentart(request.dokumentTyp());
            daten.setRechnungsnummer(request.kopfdaten().dokumentnummer());
            daten.setRechnungsdatum(parseDate(request.kopfdaten().rechnungsDatum()));
            daten.setKundenName(request.kopfdaten().kundenName());
            daten.setKundennummer(request.kopfdaten().kundennummer());

            // Betrag aus Content-Blöcken berechnen
            BigDecimal betrag = BigDecimal.ZERO;
            if (request.betragNetto() != null) {
                // Für Abschlagsrechnungen: expliziten Betrag verwenden
                betrag = BigDecimal.valueOf(request.betragNetto());
            } else if (request.contentBlocks() != null) {
                for (var block : request.contentBlocks()) {
                    if ("SERVICE".equals(block.type()) && !Boolean.TRUE.equals(block.optional())) {
                        BigDecimal menge = block.quantity() != null ? BigDecimal.valueOf(block.quantity()) : BigDecimal.ONE;
                        BigDecimal preis = block.price() != null ? BigDecimal.valueOf(block.price()) : BigDecimal.ZERO;
                        BigDecimal pos = menge.multiply(preis);
                        if (block.discount() != null && block.discount() > 0) {
                            BigDecimal rabattFaktor = BigDecimal.ONE.subtract(
                                    BigDecimal.valueOf(block.discount()).divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP));
                            pos = pos.multiply(rabattFaktor).setScale(2, java.math.RoundingMode.HALF_UP);
                        }
                        betrag = betrag.add(pos);
                    }
                }
            }
            // Globalen Rabatt anwenden (nur wenn kein expliziter betragNetto)
            if (request.betragNetto() == null && request.globalRabattProzent() != null && request.globalRabattProzent() > 0) {
                BigDecimal rabattFaktor = BigDecimal.ONE.subtract(
                        BigDecimal.valueOf(request.globalRabattProzent()).divide(new BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP));
                betrag = betrag.multiply(rabattFaktor).setScale(2, java.math.RoundingMode.HALF_UP);
            }
            // MwSt. 19% für Brutto
            BigDecimal brutto = betrag.multiply(new BigDecimal("1.19")).setScale(2, java.math.RoundingMode.HALF_UP);
            daten.setBetrag(brutto);

            // Fälligkeitsdatum: 14 Tage nach Rechnungsdatum
            LocalDate rDatum = parseDate(request.kopfdaten().rechnungsDatum());
            daten.setFaelligkeitsdatum(rDatum.plusDays(14));

            // 4. ZUGFeRD-PDF erzeugen
            zugferdPdf = zugferdErstellService.erzeuge(tempPdf.toString(), daten);
            byte[] zugferdBytes = Files.readAllBytes(zugferdPdf);

            // 5. Response
            String rawZugferdFilename = "zugferd_" + request.dokumentTyp().toLowerCase() + "_" +
                    request.kopfdaten().dokumentnummer() + ".pdf";
            String filename = rawZugferdFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.builder("attachment")
                    .filename(filename)
                    .build());
            headers.setContentLength(zugferdBytes.length);

            log.info("ZUGFeRD-PDF {} erfolgreich generiert ({} bytes)", filename, zugferdBytes.length);
            return new ResponseEntity<>(zugferdBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Fehler bei ZUGFeRD-PDF-Generierung", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            // Temp-Dateien aufräumen
            try { if (tempPdf != null) Files.deleteIfExists(tempPdf); } catch (Exception ignored) {}
            try { if (zugferdPdf != null) Files.deleteIfExists(zugferdPdf); } catch (Exception ignored) {}
        }
    }

    // ======================= Helper Methods =======================

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    /**
     * Entfernt HTML-Tags aus dem Content
     */
    private String stripHtml(String html) {
        if (html == null)
            return "";
        return html
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("<p>", "")
                .replaceAll("</p>", "\n")
                .replaceAll("<[^>]+>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .trim();
    }
}
