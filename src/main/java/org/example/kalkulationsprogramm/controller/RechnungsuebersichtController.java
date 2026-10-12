package org.example.kalkulationsprogramm.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import org.example.kalkulationsprogramm.service.LieferantDokumentDateiService;
import org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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

    /**
     * Dateiendungen, die der manuelle Upload annimmt – dieselben wie im Upload-Dialog
     * (PDF, E-Rechnung als XML, Foto). Alles andere (exe, bat, js, html, svg …) wird abgelehnt.
     */
    private static final Set<String> ERLAUBTE_ENDUNGEN = Set.of("pdf", "xml", "jpg", "jpeg", "png");

    /** Größte Datei, die der manuelle Upload annimmt (wie bei Belegen). */
    private static final long MAX_DATEI_BYTES = 25L * 1024 * 1024;

    /**
     * Höchstlänge des Dateinamens in Bytes. Zusammen mit UUID-Präfix bleibt der gespeicherte
     * Name unter 255 Bytes – der Grenze von ext4/NTFS und der Datenbank-Spalte.
     */
    private static final int MAX_DATEINAME_BYTES = 200;

    private static final String MELDUNG_DATEI_NICHT_ERLAUBT =
            "Diese Datei kann hier nicht hochgeladen werden. Erlaubt sind PDF, XML (E-Rechnung), JPG und PNG.";

    /**
     * Antwort-Header des Gesamt-PDFs: IDs der Eingangsrechnungen (kommagetrennt), die nicht
     * enthalten sind, weil es zu ihnen keine PDF-Datei gibt. Das Frontend nennt sie dem Nutzer.
     */
    static final String HEADER_NICHT_ENTHALTEN = "X-Nicht-Enthalten";

    private static final String MELDUNG_KEINE_PDF =
            "Zu den ausgewählten Rechnungen gibt es keine PDF-Datei. Das Gesamt-PDF kann nicht erstellt werden.";

    private final RechnungsuebersichtService rechnungsuebersichtService;
    private final LieferantGeschaeftsdokumentRepository lieferantGeschaeftsdokumentRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final LieferantenRepository lieferantenRepository;
    private final GeminiDokumentAnalyseService geminiService;
    private final LieferantDokumentRepository lieferantDokumentRepository;
    private final LieferantDokumentZugriffService zugriffService;
    private final LieferantDokumentDateiService dokumentDateiService;

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
            @RequestParam(required = false) String search,
            Authentication authentication) {

        // Nur Dokumenttypen, die der Angemeldete laut Abteilungsrechten sehen darf (Admin: alle).
        var sichtbareTypen = zugriffService.sichtbareTypen(null, authentication);
        if (sichtbareTypen.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

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

        rechnungen = rechnungen.stream()
                .filter(r -> r.getDokument() != null && sichtbareTypen.get().contains(r.getDokument().getTyp()))
                .collect(Collectors.toList());

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
    public ResponseEntity<?> mergePdfs(@Valid @RequestBody MergePdfRequest request, Authentication authentication) {
        var sichtbareTypen = zugriffService.sichtbareTypen(null, authentication);
        if (sichtbareTypen.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
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

            // Eingangsrechnungen hinzufügen. Eine sichtbare Rechnung ohne PDF-Datei (Datei fehlt,
            // Foto, XML-E-Rechnung) bleibt draußen und wird dem Browser per Header gemeldet – ein
            // Foto im Merger würde sonst das ganze Gesamt-PDF scheitern lassen. Unbekannte und
            // gesperrte IDs bleiben gleichermaßen stumm, damit der Header nichts über sie verrät.
            List<Long> nichtEnthalten = new ArrayList<>();
            if (request.getEingangIds() != null) {
                for (Long id : request.getEingangIds()) {
                    log.info("[merge-pdf] Looking up Eingangsrechnung ID={}", id);
                    var gdOpt = lieferantGeschaeftsdokumentRepository.findById(id);
                    if (gdOpt.isPresent()) {
                        var gd = gdOpt.get();
                        if (gd.getDokument() != null && !sichtbareTypen.get().contains(gd.getDokument().getTyp())) {
                            // Nicht sichtbarer Typ: wie "nicht gefunden" behandeln, nichts preisgeben.
                            log.warn("[merge-pdf] Eingangsrechnung ID={} nicht sichtbar", id);
                        } else if (gd.getDokument() != null) {
                            Path pdfPath = dokumentDateiService.findePdf(gd.getDokument()).orElse(null);
                            if (pdfPath != null) {
                                byte[] pdfBytes = Files.readAllBytes(pdfPath);
                                RandomAccessReadBuffer buffer = new RandomAccessReadBuffer(pdfBytes);
                                buffers.add(buffer);
                                merger.addSource(buffer);
                            } else {
                                // Nur die ID: der Dateiname kann Namen aus der Rechnung enthalten (DSGVO).
                                log.warn("[merge-pdf] Eingangsrechnung ID={} liegt nicht als PDF vor", id);
                                nichtEnthalten.add(id);
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
                return ResponseEntity.badRequest().body(Map.of("message", MELDUNG_KEINE_PDF));
            }

            // Merge to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            merger.setDestinationStream(outputStream);
            merger.mergeDocuments(null);

            // Generate filename
            String filename = "Rechnungen_" + LocalDate.now() + ".pdf";

            var antwort = ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF);
            if (!nichtEnthalten.isEmpty()) {
                antwort.header(HEADER_NICHT_ENTHALTEN,
                        nichtEnthalten.stream().map(String::valueOf).collect(Collectors.joining(",")));
            }
            return antwort.body(outputStream.toByteArray());

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
        String originalFilename = bereinigeDateiname(datei.getOriginalFilename());
        if (originalFilename == null || datei.isEmpty() || datei.getSize() > MAX_DATEI_BYTES) {
            return ResponseEntity.badRequest().build();
        }
        Path tempPath = null;
        try {
            // Temporär speichern für Analyse. Im Temp-Ordner steht nur die Endung, nicht der
            // Dateiname des Nutzers (der enthält oft Namen), und nur der Server darf die Datei lesen.
            String endung = originalFilename.substring(originalFilename.lastIndexOf('.'));
            tempPath = Files.createTempFile("rechnung-analyse-", endung);
            datei.transferTo(tempPath);

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
        } catch (Exception e) {
            // Nur der Fehlertyp: Meldung und Dateiname können Namen aus der Rechnung enthalten (DSGVO).
            log.error("Analyse einer hochgeladenen Rechnung fehlgeschlagen: {}", e.getClass().getSimpleName());
            return ResponseEntity.internalServerError().build();
        } finally {
            loescheStill(tempPath);
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
        String originalFilename = bereinigeDateiname(datei.getOriginalFilename());
        if (originalFilename == null) {
            return ResponseEntity.badRequest().body(Map.of("message", MELDUNG_DATEI_NICHT_ERLAUBT));
        }
        if (datei.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Die Datei ist leer."));
        }
        if (datei.getSize() > MAX_DATEI_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("message", "Die Datei ist zu groß. Erlaubt sind höchstens 25 MB."));
        }

        Path targetPath = null;
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
            String storedFilename = java.util.UUID.randomUUID() + "_" + originalFilename;

            Path lieferantDir = Path.of(uploadPath, "lieferanten", lieferant.getId().toString());
            targetPath = zielpfadImOrdner(lieferantDir, storedFilename);
            if (targetPath == null) {
                return ResponseEntity.badRequest().body(Map.of("message", MELDUNG_DATEI_NICHT_ERLAUBT));
            }
            Files.createDirectories(targetPath.getParent());
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
            // Nur der Fehlertyp: Meldung und Dateiname können Namen aus der Rechnung enthalten (DSGVO).
            // Aus demselben Grund geht die interne Fehlermeldung auch nicht an den Browser.
            log.error("Import einer hochgeladenen Rechnung für Lieferant {} fehlgeschlagen: {}",
                    request.getLieferantId(), e.getClass().getSimpleName());
            // Ohne Datenbank-Eintrag findet niemand die Datei wieder – also nicht liegen lassen.
            loescheStill(targetPath);
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Die Rechnung konnte nicht gespeichert werden. Bitte noch einmal versuchen."));
        }
    }

    /**
     * Macht aus dem Dateinamen des Browsers einen reinen Dateinamen ohne Ordner-Anteil
     * ({@code ../../x.pdf} wird zu {@code x.pdf}) und ersetzt Zeichen, die in einem
     * Dateinamen nichts zu suchen haben. Zu lange Namen werden am Ende des Namensteils
     * (direkt vor der Endung) gekürzt, die Endung bleibt. Gibt {@code null} zurück, wenn kein Name übrig bleibt oder die Endung nicht
     * in {@link #ERLAUBTE_ENDUNGEN} steht.
     */
    private static String bereinigeDateiname(String rohName) {
        if (!StringUtils.hasText(rohName)) {
            return null;
        }
        // Backslash zählt überall als Ordnertrenner, und was Windows in Pfaden verbietet, wird
        // vorher ersetzt – so kommt auf jedem Server derselbe Name heraus. Unsichtbare
        // Formatzeichen (z. B. Schreibrichtungs-Umkehr) fliegen mit raus.
        String einheitlich = rohName.trim().replace('\\', '/')
                .replaceAll("[:*?\"<>|\\p{Cntrl}\\p{Cf}]", "_");
        Path nurName;
        try {
            nurName = Path.of(einheitlich).getFileName();
        } catch (InvalidPathException e) {
            return null;
        }
        if (nurName == null) {
            return null;
        }
        String name = nurName.toString().replaceAll("[\\\\/:*?\"<>|]", "_");
        int punkt = name.lastIndexOf('.');
        String endung = punkt < 0 ? "" : name.substring(punkt + 1).toLowerCase(Locale.ROOT);
        if (!ERLAUBTE_ENDUNGEN.contains(endung)) {
            return null;
        }
        return kuerzeAufHoechstlaenge(name.substring(0, punkt), name.substring(punkt));
    }

    /**
     * Kürzt den Namensteil so weit, dass Name und Endung zusammen höchstens
     * {@link #MAX_DATEINAME_BYTES} Bytes (UTF-8) lang sind.
     */
    private static String kuerzeAufHoechstlaenge(String namensteil, String punktUndEndung) {
        int erlaubt = MAX_DATEINAME_BYTES - punktUndEndung.length();
        String gekuerzt = namensteil;
        // Jedes Zeichen braucht mindestens ein Byte: erst grob, dann Zeichen für Zeichen kürzen.
        if (gekuerzt.length() > erlaubt) {
            gekuerzt = gekuerzt.substring(0, erlaubt);
        }
        while (gekuerzt.getBytes(StandardCharsets.UTF_8).length > erlaubt) {
            gekuerzt = gekuerzt.substring(0, gekuerzt.length() - 1);
        }
        // Kein halbes Zeichen (z. B. halbes Emoji) am Ende stehen lassen.
        if (!gekuerzt.isEmpty() && Character.isHighSurrogate(gekuerzt.charAt(gekuerzt.length() - 1))) {
            gekuerzt = gekuerzt.substring(0, gekuerzt.length() - 1);
        }
        return gekuerzt + punktUndEndung;
    }

    /** Löscht eine Datei, falls es sie gibt; ein Fehler dabei wird nur protokolliert. */
    private static void loescheStill(Path datei) {
        if (datei == null) {
            return;
        }
        try {
            Files.deleteIfExists(datei);
        } catch (IOException e) {
            log.warn("Hochgeladene Datei konnte nicht aufgeräumt werden: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * Bildet den Zielpfad für {@code dateiname} im {@code ordner} und prüft, dass er direkt
     * darin liegt – nicht darüber und nicht in einem Unterordner. Sonst {@code null}.
     */
    private static Path zielpfadImOrdner(Path ordner, String dateiname) {
        Path basis = ordner.toAbsolutePath().normalize();
        Path ziel = basis.resolve(dateiname).normalize();
        if (!ziel.startsWith(basis) || !basis.equals(ziel.getParent())) {
            return null;
        }
        return ziel;
    }
}
