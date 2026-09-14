package org.example.kalkulationsprogramm.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.dto.Rechnungsuebersicht.AusgangsrechnungDto;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.example.kalkulationsprogramm.service.RechnungsuebersichtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller für Rechnungsübersicht - Buchhaltungsansicht aller Rechnungen.
 * Ermöglicht Filterung nach Monat/Jahr und PDF-Export für Steuerberater.
 */
@RestController
@RequestMapping("/api/rechnungsuebersicht")
@RequiredArgsConstructor
@Slf4j
public class RechnungsuebersichtController {

    private final RechnungsuebersichtService rechnungsuebersichtService;
    private final LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final LieferantenRepository lieferantenRepository;
    private final GeminiDokumentAnalyseService geminiService;
    private final LieferantDokumentRepository lieferantDokumentRepository;

    @Value("${upload.path:uploads}")
    private String uploadPath;

    // ==================== Ausgangsrechnungen (AusgangsGeschaeftsDokument)
    // ====================

    /**
     * Gibt alle Ausgangsrechnungen zurück, optional gefiltert nach Jahr/Monat.
     * Filterung basiert auf rechnungsdatum.
     */
    @GetMapping("/ausgang")
    public ResponseEntity<List<AusgangsrechnungDto>> getAusgangsrechnungen(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String search) {

        return ResponseEntity.ok(rechnungsuebersichtService.getAusgangsrechnungen(year, month, search));
    }

    // ==================== Eingangsrechnungen (LieferantGeschaeftsdokument)
    // ====================

    /**
     * Gibt alle Eingangsrechnungen zurück, optional gefiltert nach Jahr/Monat.
     * Filterung basiert auf dokumentDatum.
     */
    @GetMapping("/eingang")
    public ResponseEntity<List<EingangsrechnungDto>> getEingangsrechnungen(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String search) {

        List<LieferantGeschaeftsdokument> rechnungen;

        if (year != null && month != null) {
            // Filter by year and month
            YearMonth ym = YearMonth.of(year, month);
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();
            rechnungen = lieferantGeschaeftsdokumentRepository.findRechnungenByDatumBetween(start, end);
        } else if (year != null) {
            // Filter by year only
            LocalDate start = LocalDate.of(year, 1, 1);
            LocalDate end = LocalDate.of(year, 12, 31);
            rechnungen = lieferantGeschaeftsdokumentRepository.findRechnungenByDatumBetween(start, end);
        } else {
            // All invoices
            rechnungen = lieferantGeschaeftsdokumentRepository.findAllEingangsrechnungen();
        }

        // Apply search filter if present
        if (search != null && !search.isBlank()) {
            String lowerSearch = search.toLowerCase();
            rechnungen = rechnungen.stream()
                    .filter(r -> matchesEingangSearch(r, lowerSearch))
                    .collect(Collectors.toList());
        }

        // Sort by dokumentDatum descending
        rechnungen.sort(Comparator.comparing(
                LieferantGeschaeftsdokument::getDokumentDatum,
                Comparator.nullsLast(Comparator.reverseOrder())));

        List<EingangsrechnungDto> dtos = rechnungen.stream()
                .map(this::toEingangsrechnungDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    private boolean matchesEingangSearch(LieferantGeschaeftsdokument r, String search) {
        if (r.getDokumentNummer() != null && r.getDokumentNummer().toLowerCase().contains(search))
            return true;
        if (r.getDokumentDatum() != null && r.getDokumentDatum().toString().contains(search))
            return true;
        if (r.getBetragNetto() != null && String.valueOf(r.getBetragNetto()).contains(search))
            return true;
        if (r.getBetragBrutto() != null && String.valueOf(r.getBetragBrutto()).contains(search))
            return true;

        if (r.getDokument() != null && r.getDokument().getLieferant() != null) {
            if (r.getDokument().getLieferant().getLieferantenname() != null &&
                    r.getDokument().getLieferant().getLieferantenname().toLowerCase().contains(search))
                return true;
        }
        return false;
    }

    // ==================== PDF-Merge Endpoint ====================

    /**
     * Führt mehrere PDF-Rechnungen zu einer Datei zusammen.
     * Akzeptiert separate Listen für Ausgangs- und Eingangsrechnungen.
     */
    @PostMapping("/merge-pdf")
    public ResponseEntity<?> mergePdfs(@Valid @RequestBody MergePdfRequest request) {
        log.info("[merge-pdf] Request received: ausgangIds={}, eingangIds={}",
                request.getAusgangIds(), request.getEingangIds());
        List<RandomAccessReadBuffer> buffers = new ArrayList<>();
        try {
            PDFMergerUtility merger = new PDFMergerUtility();

            // Die Auswahl enthält IDs aus AusgangsGeschaeftsDokument.
            if (request.getAusgangIds() != null) {
                for (Long id : request.getAusgangIds()) {
                    byte[] pdfBytes = rechnungsuebersichtService.readAusgangsPdf(id);
                    RandomAccessReadBuffer buffer = new RandomAccessReadBuffer(pdfBytes);
                    buffers.add(buffer);
                    merger.addSource(buffer);
                }
            }

            // Eingangsrechnungen hinzufügen
            if (request.getEingangIds() != null) {
                for (Long id : request.getEingangIds()) {
                    log.info("[merge-pdf] Looking up Eingangsrechnung ID={}", id);
                    var gdOpt = lieferantGeschaeftsdokumentRepository.findById(id);
                    if (gdOpt.isPresent()) {
                        var gd = gdOpt.get();
                        if (gd.getDokument() != null) {
                            Path pdfPath = resolveLieferantDokumentPath(gd.getDokument());
                            log.info("[merge-pdf] Resolved path={}, exists={}", pdfPath,
                                    pdfPath != null && Files.exists(pdfPath));
                            if (pdfPath != null && Files.exists(pdfPath)) {
                                byte[] pdfBytes = Files.readAllBytes(pdfPath);
                                RandomAccessReadBuffer buffer = new RandomAccessReadBuffer(pdfBytes);
                                buffers.add(buffer);
                                merger.addSource(buffer);
                            }
                        } else {
                            log.warn("[merge-pdf] Eingangsrechnung ID={} has no associated dokument", id);
                        }
                    } else {
                        log.warn("[merge-pdf] Eingangsrechnung ID={} not found", id);
                    }
                }
            }

            log.info("[merge-pdf] Total buffers collected: {}", buffers.size());
            if (buffers.isEmpty()) {
                log.warn("[merge-pdf] No valid PDFs found, returning 400");
                return ResponseEntity.badRequest().build();
            }

            // Merge to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            merger.setDestinationStream(outputStream);
            merger.mergeDocuments(null);

            // Generate filename
            String filename = "Rechnungen_" + LocalDate.now() + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(outputStream.toByteArray());

        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",
                    e.getReason() == null ? "Die ausgewählte Rechnung ist nicht verfügbar." : e.getReason()));
        } catch (Exception e) {
            log.error("Fehler beim Zusammenführen der PDFs", e);
            return ResponseEntity.internalServerError().build();
        } finally {
            for (var buffer : buffers) {
                try { buffer.close(); } catch (IOException ignored) { }
            }
        }
    }

    // ==================== Hilfsmethoden ====================

    private Path resolveLieferantDokumentPath(LieferantDokument dok) {
        if (dok.getGespeicherterDateiname() != null) {
            // Priority 1: Check root upload path
            Path rootPath = Path.of(uploadPath, dok.getGespeicherterDateiname());
            if (Files.exists(rootPath)) {
                return rootPath;
            }

            // Priority 2: Check attachments/ (flat)
            Path attachmentsPath = Path.of(uploadPath, "attachments", dok.getGespeicherterDateiname());
            if (Files.exists(attachmentsPath)) {
                return attachmentsPath;
            }

            // Priority 3: Check attachments/{emailId}/ (if email context exists)
            if (dok.getAttachment() != null && dok.getAttachment().getEmail() != null) {
                Path emailPath = Path.of(uploadPath, "attachments",
                        dok.getAttachment().getEmail().getId().toString(),
                        dok.getGespeicherterDateiname());
                if (Files.exists(emailPath)) {
                    return emailPath;
                }
            }

            return rootPath; // Return default
        }

        // Fallback for documents without gespeicherterDateiname but with attachment
        // info
        if (dok.getAttachment() != null && dok.getAttachment().getStoredFilename() != null) {
            return Path.of(uploadPath, "attachments", dok.getAttachment().getEmail().getId().toString(),
                    dok.getAttachment().getStoredFilename());
        }
        return null;
    }

    private EingangsrechnungDto toEingangsrechnungDto(LieferantGeschaeftsdokument gd) {
        EingangsrechnungDto dto = new EingangsrechnungDto();
        dto.id = gd.getId();
        dto.dokumentNummer = gd.getDokumentNummer();
        dto.dokumentDatum = gd.getDokumentDatum();
        dto.betragNetto = gd.getBetragNetto() != null ? gd.getBetragNetto().doubleValue() : null;
        dto.betragBrutto = gd.getBetragBrutto() != null ? gd.getBetragBrutto().doubleValue() : null;
        dto.bezahlt = Boolean.TRUE.equals(gd.getBezahlt());
        dto.zahlungsart = gd.getZahlungsart();

        if (gd.getDokument() != null) {
            dto.dokumentId = gd.getDokument().getId();
            dto.originalDateiname = gd.getDokument().getEffektiverDateiname();

            if (gd.getDokument().getLieferant() != null) {
                dto.lieferantId = gd.getDokument().getLieferant().getId();
                dto.lieferantName = gd.getDokument().getLieferant().getLieferantenname();
            }

            // PDF-URL - use the correct endpoint without lieferant prefix.
            // Reihenfolge:
            //  1. Auto-erzeugt aus Mobile-Beleg-Scan (beleg-FK gesetzt) -> Beleg-Endpoint
            //     bedient die Vorschau direkt und respektiert die Buchhaltungs-Permissions.
            //  2. E-Mail-Anhang -> Mail-Attachment-Endpoint
            //  3. Manuell hochgeladen -> Lieferanten-Dokument-Endpoint
            if (gd.getDokument().getBeleg() != null) {
                dto.pdfUrl = "/api/buchhaltung/belege/" + gd.getDokument().getBeleg().getId() + "/datei";
            } else if (gd.getDokument().getAttachment() != null) {
                var att = gd.getDokument().getAttachment();
                if (att.getEmail() != null) {
                    dto.pdfUrl = "/api/emails/" + att.getEmail().getId() +
                            "/attachments/" + att.getId();
                }
            } else if (gd.getDokument().getGespeicherterDateiname() != null) {
                dto.pdfUrl = "/api/lieferanten/" + dto.lieferantId +
                        "/dokumente/" + gd.getDokument().getId() + "/download";
            }
        }

        return dto;
    }

    // ==================== DTOs ====================

    public static class EingangsrechnungDto {
        public Long id;
        public Long dokumentId;
        public Long lieferantId;
        public String lieferantName;
        public String dokumentNummer;
        public LocalDate dokumentDatum;
        public Double betragNetto;
        public Double betragBrutto;
        public boolean bezahlt;
        public String zahlungsart;
        public String originalDateiname;
        public String pdfUrl;
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MergePdfRequest {
        @Size(max = 500)
        private List<@NotNull @Positive Long> ausgangIds;
        @Size(max = 500)
        private List<@NotNull @Positive Long> eingangIds;
    }

    // ==================== MANUELLER UPLOAD (RECHNUNGSÜBERSICHT)
    // ====================

    /**
     * Analysiert ein Dokument ohne Lieferantenbezug (für manuellen Upload).
     */
    @PostMapping(value = "/analyze-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<LieferantDokumentDto.MultiInvoiceAnalyzeResponse>> analyzeUpload(
            @RequestPart("datei") MultipartFile datei) {
        try {
            // Temporär speichern für Analyse
            String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(datei.getOriginalFilename()));
            if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }
            java.nio.file.Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
            String tempFilename = java.util.UUID.randomUUID() + "_" + originalFilename;
            java.nio.file.Path tempPath = tempDir.resolve(tempFilename);

            datei.transferTo(tempPath);

            try {
                // Multi-Invoice-Analyse durchführen
                var results = geminiService.analyzeFileForMultipleInvoices(tempPath, originalFilename);

                if (results.isEmpty()) {
                    // Fallback: Leere Antwort
                    results.add(LieferantDokumentDto.MultiInvoiceAnalyzeResponse.builder()
                            .pageRange("alle")
                            .analyzeResponse(LieferantDokumentDto.AnalyzeResponse.builder()
                                    .dokumentTyp(LieferantDokumentTyp.RECHNUNG)
                                    .dokumentDatum(LocalDate.now())
                                    .aiConfidence(0.0)
                                    .analyseQuelle("KEINE")
                                    .build())
                            .build());
                }
                return ResponseEntity.ok(results);
            } finally {
                java.nio.file.Files.deleteIfExists(tempPath);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Speichert ein manuell hochgeladenes Dokument.
     * Wenn lieferantId gesetzt ist, wird es diesem zugeordnet.
     * Wenn nicht, muss eine Logik greifen (z.B. Fehler, oder "Sonstige").
     */
    @PostMapping(value = "/import-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> importUpload(
            @RequestPart("datei") MultipartFile datei,
            @RequestPart("metadata") LieferantDokumentDto.ImportRequest request,
            @RequestParam(value = "token", required = false) String token) {

        if (request.getLieferantId() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bitte wählen Sie einen Lieferanten aus."));
        }

        try {
            // Wiederverwenden der Logik aus LieferantenController via Service-Call oder
            // hier
            // duplizieren (da Controller-Methoden nicht direkt aufrufbar/schön sind).
            // Besser: wir implementieren es hier direkt, da es leicht abweicht ("check if
            // exists")

            var lieferant = lieferantenRepository.findById(request.getLieferantId()).orElse(null);
            if (lieferant == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Lieferant nicht gefunden."));
            }

            // Duplikat-Check: Dokumentnummer muss pro Lieferant eindeutig sein
            if (StringUtils.hasText(request.getDokumentNummer())) {
                boolean exists = lieferantGeschaeftsdokumentRepository
                        .existsByLieferantIdAndDokumentNummer(
                                lieferant.getId(), request.getDokumentNummer());
                if (exists) {
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("message", "Ein Dokument mit dieser Nummer existiert bereits für diesen Lieferanten."));
                }
            }

            // 1. Datei speichern
            String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(datei.getOriginalFilename()));
            String storedFilename = java.util.UUID.randomUUID() + "_" + originalFilename;

            java.nio.file.Path lieferantDir = java.nio.file.Paths.get("uploads", "lieferanten",
                    lieferant.getId().toString());
            java.nio.file.Files.createDirectories(lieferantDir);
            java.nio.file.Path targetPath = lieferantDir.resolve(storedFilename);
            datei.transferTo(targetPath);

            // 2. LieferantDokument erstellen
            var dokument = new LieferantDokument();
            dokument.setLieferant(lieferant);
            dokument.setTyp(request.getDokumentTyp() != null ? request.getDokumentTyp()
                    : LieferantDokumentTyp.RECHNUNG);
            dokument.setOriginalDateiname(originalFilename);
            dokument.setGespeicherterDateiname(storedFilename);
            dokument.setUploadDatum(java.time.LocalDateTime.now());

            // Uploader
            if (token != null) {
                // Simple Token lookup (reused from other controllers)
                // Optional: move to a AuthService
                // ... logic skipped for brevity, assuming standard token handling or null
                var mitarbeiter = mitarbeiterRepository.findByLoginToken(token).orElse(null);
                if (mitarbeiter != null) {
                    dokument.setUploadedBy(mitarbeiter);
                }
            }

            dokument = lieferantDokumentRepository.save(dokument);

            // 3. Geschäftsdaten erstellen
            var geschaeftsdaten = new LieferantGeschaeftsdokument();
            geschaeftsdaten.setDokument(dokument);
            geschaeftsdaten.setDokumentNummer(request.getDokumentNummer());
            geschaeftsdaten.setDokumentDatum(request.getDokumentDatum());
            geschaeftsdaten.setBetragNetto(request.getBetragNetto());
            geschaeftsdaten.setBetragBrutto(request.getBetragBrutto());
            geschaeftsdaten.setMwstSatz(request.getMwstSatz());
            geschaeftsdaten.setLiefertermin(request.getLiefertermin());
            geschaeftsdaten.setZahlungsziel(request.getZahlungsziel());
            geschaeftsdaten.setBestellnummer(request.getBestellnummer());
            geschaeftsdaten.setReferenzNummer(request.getReferenzNummer());
            geschaeftsdaten.setSkontoTage(request.getSkontoTage());
            geschaeftsdaten.setSkontoProzent(request.getSkontoProzent());
            geschaeftsdaten.setNettoTage(request.getNettoTage());
            geschaeftsdaten.setBereitsGezahlt(request.getBereitsGezahlt());
            geschaeftsdaten.setZahlungsart(request.getZahlungsart());
            geschaeftsdaten.setAiConfidence(1.0); // Verifiziert
            geschaeftsdaten.setAnalysiertAm(java.time.LocalDateTime.now());

            // Speichern
            lieferantGeschaeftsdokumentRepository.save(geschaeftsdaten);

            return ResponseEntity.ok(Map.of("id", dokument.getId(), "message", "Import erfolgreich"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Fehler beim Import: " + e.getMessage()));
        }
    }
}
