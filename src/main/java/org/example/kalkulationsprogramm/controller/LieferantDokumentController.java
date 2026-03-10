package org.example.kalkulationsprogramm.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lieferant-dokumente")
@RequiredArgsConstructor
@Slf4j
public class LieferantDokumentController {

    private final LieferantDokumentRepository dokumentRepository;
    private final LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    private final LieferantDokumentService dokumentService;
    private final GeminiDokumentAnalyseService analyseService;
    private final EmailRepository emailRepository;
    private final EmailAttachmentProcessingService emailAttachmentProcessingService;
    private final org.example.kalkulationsprogramm.repository.EmailAttachmentRepository emailAttachmentRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.mail-attachment-dir}")
    private String mailAttachmentDir;

    /**
     * Aktualisiert ein Lieferanten-Dokument und dessen Geschäftsdaten.
     */
    @PutMapping("/{dokumentId}")
    @Transactional
    public ResponseEntity<LieferantDokumentDto.Response> updateDokument(
            @PathVariable Long dokumentId,
            @RequestBody UpdateDokumentRequest request) {

        var dokument = dokumentRepository.findById(dokumentId).orElse(null);
        if (dokument == null) {
            return ResponseEntity.notFound().build();
        }

        // Dokumenttyp aktualisieren
        if (request.typ != null) {
            try {
                dokument.setTyp(LieferantDokumentTyp.valueOf(request.typ));
            } catch (IllegalArgumentException ignored) {
                // Ungültiger Typ - ignorieren
            }
        }

        // Geschäftsdaten aktualisieren oder erstellen
        LieferantGeschaeftsdokument gd = dokument.getGeschaeftsdaten();
        if (gd == null) {
            gd = new LieferantGeschaeftsdokument();
            gd.setDokument(dokument);
            gd.setAnalysiertAm(LocalDateTime.now());
            dokument.setGeschaeftsdaten(gd);
        }

        if (request.geschaeftsdaten != null) {
            var gdReq = request.geschaeftsdaten;

            // Grunddaten
            if (gdReq.dokumentNummer != null) {
                gd.setDokumentNummer(gdReq.dokumentNummer);
            }
            if (gdReq.dokumentDatum != null && !gdReq.dokumentDatum.isEmpty()) {
                gd.setDokumentDatum(LocalDate.parse(gdReq.dokumentDatum));
            }
            if (gdReq.liefertermin != null && !gdReq.liefertermin.isEmpty()) {
                gd.setLiefertermin(LocalDate.parse(gdReq.liefertermin));
            }

            // Beträge
            if (gdReq.betragNetto != null) {
                gd.setBetragNetto(BigDecimal.valueOf(gdReq.betragNetto));
            }
            if (gdReq.betragBrutto != null) {
                gd.setBetragBrutto(BigDecimal.valueOf(gdReq.betragBrutto));
            }
            if (gdReq.mwstSatz != null) {
                gd.setMwstSatz(BigDecimal.valueOf(gdReq.mwstSatz));
            }

            // Referenzen
            if (gdReq.referenzNummer != null) {
                gd.setReferenzNummer(gdReq.referenzNummer);
            }
            if (gdReq.bestellnummer != null) {
                gd.setBestellnummer(gdReq.bestellnummer);
            }

            // Zahlungsziel
            if (gdReq.zahlungsziel != null && !gdReq.zahlungsziel.isEmpty()) {
                gd.setZahlungsziel(LocalDate.parse(gdReq.zahlungsziel));
            }

            // Skonto-Konditionen
            if (gdReq.skontoTage != null) {
                gd.setSkontoTage(gdReq.skontoTage);
            }
            if (gdReq.skontoProzent != null) {
                gd.setSkontoProzent(BigDecimal.valueOf(gdReq.skontoProzent));
            }
            if (gdReq.nettoTage != null) {
                gd.setNettoTage(gdReq.nettoTage);
            }

            // Zahlungsstatus
            if (gdReq.bezahlt != null) {
                gd.setBezahlt(gdReq.bezahlt);
                if (gdReq.bezahlt && gd.getBezahltAm() == null) {
                    gd.setBezahltAm(LocalDate.now());
                }
            }
            if (gdReq.bezahltAm != null && !gdReq.bezahltAm.isEmpty()) {
                gd.setBezahltAm(LocalDate.parse(gdReq.bezahltAm));
            }
            if (gdReq.tatsaechlichGezahlt != null) {
                gd.setTatsaechlichGezahlt(BigDecimal.valueOf(gdReq.tatsaechlichGezahlt));
            }
            if (gdReq.mitSkonto != null) {
                gd.setMitSkonto(gdReq.mitSkonto);
            }
        }

        // Speichern
        geschaeftsdokumentRepository.save(gd);
        dokumentRepository.save(dokument);

        // Als DTO zurückgeben
        return ResponseEntity.ok(dokumentService.getDokumentById(dokumentId));
    }

    /**
     * Request-DTO für Dokument-Update.
     */
    public static class UpdateDokumentRequest {
        public String typ;
        public GeschaeftsdatenRequest geschaeftsdaten;
    }

    public static class GeschaeftsdatenRequest {
        // Grunddaten
        public String dokumentNummer;
        public String dokumentDatum;
        public String liefertermin;

        // Beträge
        public Double betragNetto;
        public Double betragBrutto;
        public Double mwstSatz;

        // Referenzen
        public String referenzNummer;
        public String bestellnummer;

        // Zahlungsziel
        public String zahlungsziel;

        // Skonto-Konditionen
        public Integer skontoTage;
        public Double skontoProzent;
        public Integer nettoTage;

        // Zahlungsstatus
        public Boolean bezahlt;
        public String bezahltAm;
        public Double tatsaechlichGezahlt;
        public Boolean mitSkonto;
    }

    // ==================== RE-ANALYSE ENDPOINTS ====================

    /**
     * Re-analysiert alle Dokumente eines Lieferanten.
     * Nützlich, um nach Verbesserungen am Extractor/KI alle Dokumente neu zu
     * verarbeiten.
     */
    @PostMapping("/lieferant/{lieferantId}/reanalyze")
    public ResponseEntity<Map<String, Object>> reanalyzeByLieferant(@PathVariable Long lieferantId) {
        log.info("Starte Re-Analyse aller Dokumente für Lieferant {}", lieferantId);

        // Nur IDs laden um Session-Konflikte zu vermeiden
        List<Long> dokumentIds = dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(lieferantId)
                .stream()
                .map(LieferantDokument::getId)
                .toList();

        int erfolgreich = 0;
        int fehlgeschlagen = 0;

        for (int i = 0; i < dokumentIds.size(); i++) {
            Long dokumentId = dokumentIds.get(i);
            try {
                log.info("Re-Analyse Dokument {}/{} (ID: {}) für Lieferant {}",
                        i + 1, dokumentIds.size(), dokumentId, lieferantId);
                // Jedes Dokument einzeln analysieren (in eigener Transaktion via Service)
                var result = analyseService.reanalysiereDokumentById(dokumentId);
                if (result != null) {
                    erfolgreich++;
                    log.info("Re-Analyse erfolgreich für Dokument {}: {}", dokumentId, result.getDokumentNummer());
                } else {
                    fehlgeschlagen++;
                    log.warn("Re-Analyse ohne Ergebnis für Dokument {}", dokumentId);
                }
            } catch (Exception e) {
                fehlgeschlagen++;
                log.error("Re-Analyse fehlgeschlagen für Dokument {}: {}", dokumentId, e.getMessage());
            }
        }

        log.info("Re-Analyse abgeschlossen für Lieferant {}: {} erfolgreich, {} fehlgeschlagen",
                lieferantId, erfolgreich, fehlgeschlagen);

        return ResponseEntity.ok(Map.of(
                "lieferantId", lieferantId,
                "gesamt", dokumentIds.size(),
                "erfolgreich", erfolgreich,
                "fehlgeschlagen", fehlgeschlagen));
    }

    /**
     * Re-analysiert ein einzelnes Dokument.
     */
    @PostMapping("/{dokumentId}/reanalyze")
    public ResponseEntity<LieferantDokumentDto.Response> reanalyzeDokument(@PathVariable Long dokumentId) {
        log.info("Starte Re-Analyse für Dokument {}", dokumentId);

        var dokument = dokumentRepository.findById(dokumentId).orElse(null);
        if (dokument == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            var result = analyseService.analysiereDokument(dokument);
            if (result != null) {
                log.info("Re-Analyse erfolgreich für Dokument {}: {}", dokumentId, result.getDokumentNummer());
                return ResponseEntity.ok(dokumentService.getDokumentById(dokumentId));
            } else {
                log.warn("Re-Analyse ohne Ergebnis für Dokument {}", dokumentId);
                return ResponseEntity.ok(dokumentService.getDokumentById(dokumentId));
            }
        } catch (Exception e) {
            log.error("Re-Analyse fehlgeschlagen für Dokument {}: {}", dokumentId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Führt eine einmalige Neuverknüpfung aller Dokumente mit normalisierter Nummern-Logik durch.
     * Sollte nach Einführung der normalisierten Verknüpfungslogik einmal ausgeführt werden.
     * 
     * @return Statistik über verknüpfte Dokumente
     */
    @PostMapping("/relink-all")
    public ResponseEntity<Map<String, Object>> relinkAlleDokumente() {
        log.info("Starte einmalige Neuverknüpfung aller Dokumente...");
        
        try {
            int verknuepft = analyseService.relinkAlleDokumente();
            
            return ResponseEntity.ok(Map.of(
                    "message", "Neuverknüpfung abgeschlossen",
                    "neuVerknuepft", verknuepft));
        } catch (Exception e) {
            log.error("Neuverknüpfung fehlgeschlagen: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()));
        }
    }

    /**
     * Führt eine Neuverknüpfung aller Dokumente eines bestimmten Lieferanten durch.
     * 
     * @param lieferantId ID des Lieferanten
     * @return Statistik über verknüpfte Dokumente
     */
    @PostMapping("/lieferant/{lieferantId}/relink")
    public ResponseEntity<Map<String, Object>> relinkByLieferant(@PathVariable Long lieferantId) {
        log.info("Starte Neuverknüpfung für Lieferant {}...", lieferantId);
        
        try {
            int verknuepft = analyseService.relinkDokumenteByLieferant(lieferantId);
            
            return ResponseEntity.ok(Map.of(
                    "lieferantId", lieferantId,
                    "message", "Neuverknüpfung abgeschlossen",
                    "neuVerknuepft", verknuepft));
        } catch (Exception e) {
            log.error("Neuverknüpfung fehlgeschlagen für Lieferant {}: {}", lieferantId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()));
        }
    }

    // ==================== EMAIL ATTACHMENT PROCESSING ====================

    /**
     * Verarbeitet alle Anhänge von Lieferanten-zugeordneten E-Mails.
     * Erstellt LieferantDokumente für PDF/XML-Anhänge und analysiert sie mit KI.
     * 
     * Dieser Endpoint ist nützlich, wenn E-Mails bereits Lieferanten zugeordnet
     * wurden,
     * aber die Anhänge noch nicht als LieferantDokumente verarbeitet wurden.
     * 
     * @return Statistik über verarbeitete E-Mails und erstellte Dokumente
     */
    @PostMapping("/process-assigned-emails")
    public ResponseEntity<Map<String, Object>> processAssignedEmailAttachments() {
        log.info("Starte Verarbeitung aller Lieferanten-E-Mail-Anhänge...");

        // Finde alle E-Mails die einem Lieferanten zugeordnet sind
        List<Email> lieferantEmails = emailRepository.findLieferantEmails();

        int emailsVerarbeitet = 0;
        int dokumenteErstellt = 0;
        int emailsUebersprungen = 0;
        int fehler = 0;

        for (Email email : lieferantEmails) {
            try {
                // Prüfe ob E-Mail überhaupt Anhänge hat
                if (email.getAttachments() == null || email.getAttachments().isEmpty()) {
                    emailsUebersprungen++;
                    continue;
                }

                // Verarbeite die Anhänge
                int created = emailAttachmentProcessingService.processLieferantAttachments(email);
                if (created > 0) {
                    dokumenteErstellt += created;
                    emailsVerarbeitet++;
                    log.info("E-Mail {} verarbeitet: {} Dokumente erstellt (Lieferant: {})",
                            email.getId(), created,
                            email.getLieferant() != null ? email.getLieferant().getLieferantenname() : "?");
                } else {
                    emailsUebersprungen++;
                }
            } catch (Exception e) {
                fehler++;
                log.error("Fehler bei Verarbeitung von E-Mail {}: {}", email.getId(), e.getMessage());
            }
        }

        log.info(
                "E-Mail-Anhang-Verarbeitung abgeschlossen: {} E-Mails verarbeitet, {} Dokumente erstellt, {} übersprungen, {} Fehler",
                emailsVerarbeitet, dokumenteErstellt, emailsUebersprungen, fehler);

        return ResponseEntity.ok(Map.of(
                "emailsGesamt", lieferantEmails.size(),
                "emailsVerarbeitet", emailsVerarbeitet,
                "dokumenteErstellt", dokumenteErstellt,
                "emailsUebersprungen", emailsUebersprungen,
                "fehler", fehler));
    }

    /**
     * Verarbeitet alle Anhänge einer bestimmten Lieferanten-E-Mail.
     * Erstellt LieferantDokumente für PDF/XML-Anhänge und analysiert sie mit KI.
     */
    @PostMapping("/process-email/{emailId}")
    public ResponseEntity<Map<String, Object>> processEmailAttachments(@PathVariable Long emailId) {
        log.info("Starte Verarbeitung von E-Mail-Anhängen für E-Mail {}", emailId);

        Email email = emailRepository.findById(emailId).orElse(null);
        if (email == null) {
            return ResponseEntity.notFound().build();
        }

        if (email.getLieferant() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "E-Mail ist keinem Lieferanten zugeordnet",
                    "emailId", emailId));
        }

        try {
            int dokumenteErstellt = emailAttachmentProcessingService.processLieferantAttachments(email);

            return ResponseEntity.ok(Map.of(
                    "emailId", emailId,
                    "lieferantId", email.getLieferant().getId(),
                    "lieferantName", email.getLieferant().getLieferantenname(),
                    "dokumenteErstellt", dokumenteErstellt));
        } catch (Exception e) {
            log.error("Fehler bei Verarbeitung von E-Mail {}: {}", emailId, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage(),
                    "emailId", emailId));
        }
    }

    /**
     * Verarbeitet alle E-Mail-Anhänge eines bestimmten Lieferanten.
     * Erstellt LieferantDokumente für PDF/XML-Anhänge und analysiert sie mit KI.
     * 
     * @param lieferantId ID des Lieferanten
     * @return Statistik über verarbeitete E-Mails und erstellte Dokumente
     */
    @PostMapping("/lieferant/{lieferantId}/process-emails")
    public ResponseEntity<Map<String, Object>> processLieferantEmailAttachments(@PathVariable Long lieferantId) {
        log.info("Starte Verarbeitung aller E-Mail-Anhänge für Lieferant {}", lieferantId);

        // Finde alle E-Mails die diesem Lieferanten zugeordnet sind
        List<Email> lieferantEmails = emailRepository.findByLieferantIdOrderBySentAtDesc(lieferantId);

        if (lieferantEmails.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "lieferantId", lieferantId,
                    "message", "Keine E-Mails für diesen Lieferanten gefunden",
                    "emailsGesamt", 0,
                    "dokumenteErstellt", 0));
        }

        String lieferantName = lieferantEmails.getFirst().getLieferant() != null
                ? lieferantEmails.getFirst().getLieferant().getLieferantenname()
                : "Unbekannt";

        int emailsVerarbeitet = 0;
        int dokumenteErstellt = 0;
        int emailsUebersprungen = 0;
        int fehler = 0;

        for (Email email : lieferantEmails) {
            try {
                // Prüfe ob E-Mail überhaupt Anhänge hat
                if (email.getAttachments() == null || email.getAttachments().isEmpty()) {
                    emailsUebersprungen++;
                    continue;
                }

                // Verarbeite die Anhänge
                int created = emailAttachmentProcessingService.processLieferantAttachments(email);
                if (created > 0) {
                    dokumenteErstellt += created;
                    emailsVerarbeitet++;
                    log.info("E-Mail {} verarbeitet: {} Dokumente erstellt", email.getId(), created);
                } else {
                    emailsUebersprungen++;
                }
            } catch (Exception e) {
                fehler++;
                log.error("Fehler bei Verarbeitung von E-Mail {}: {}", email.getId(), e.getMessage());
            }
        }

        log.info("E-Mail-Verarbeitung für Lieferant {} abgeschlossen: {} E-Mails verarbeitet, {} Dokumente erstellt",
                lieferantId, emailsVerarbeitet, dokumenteErstellt);

        return ResponseEntity.ok(Map.of(
                "lieferantId", lieferantId,
                "lieferantName", lieferantName,
                "emailsGesamt", lieferantEmails.size(),
                "emailsVerarbeitet", emailsVerarbeitet,
                "dokumenteErstellt", dokumenteErstellt,
                "emailsUebersprungen", emailsUebersprungen,
                "fehler", fehler));
    }

    // ==================== DUPLIKAT-BEREINIGUNG ====================

    /**
     * Zeigt alle doppelten Dokumente (gleiche dokumentNummer + Lieferant).
     * Gruppiert nach dokumentNummer + lieferantId, zeigt welche gelöscht werden würden.
     */
    @GetMapping("/duplicates")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> findDuplicates() {
        List<Object[]> duplicates = geschaeftsdokumentRepository.findAllDuplicates();

        if (duplicates.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "message", "Keine Duplikate gefunden",
                    "duplikateGesamt", 0,
                    "gruppen", List.of()));
        }

        // Gruppieren nach lieferantId + dokumentNummer
        java.util.Map<String, java.util.List<Map<String, Object>>> gruppen = new java.util.LinkedHashMap<>();
        for (Object[] row : duplicates) {
            Long id = ((Number) row[0]).longValue();
            String dokumentNummer = (String) row[1];
            Long lieferantId = ((Number) row[2]).longValue();
            String lieferantName = (String) row[3];

            String key = lieferantId + "_" + dokumentNummer;

            gruppen.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(Map.of(
                    "id", id,
                    "dokumentNummer", dokumentNummer,
                    "lieferantId", lieferantId,
                    "lieferantName", lieferantName));
        }

        // Für jede Gruppe: erstes behalten, Rest als "zu löschen" markieren
        java.util.List<Map<String, Object>> gruppenListe = new java.util.ArrayList<>();
        int zuLoeschenGesamt = 0;

        for (var entry : gruppen.entrySet()) {
            var items = entry.getValue();
            Map<String, Object> gruppe = new java.util.LinkedHashMap<>();
            gruppe.put("dokumentNummer", items.get(0).get("dokumentNummer"));
            gruppe.put("lieferantName", items.get(0).get("lieferantName"));
            gruppe.put("behalteId", items.get(0).get("id"));
            
            java.util.List<Long> loescheIds = items.stream()
                    .skip(1) // erstes (ältestes) behalten
                    .map(m -> (Long) m.get("id"))
                    .toList();

            gruppe.put("loescheIds", loescheIds);
            gruppe.put("anzahlDuplikate", items.size());
            gruppenListe.add(gruppe);
            zuLoeschenGesamt += loescheIds.size();
        }

        return ResponseEntity.ok(Map.of(
                "message", zuLoeschenGesamt + " Duplikate in " + gruppenListe.size() + " Gruppen gefunden",
                "duplikateGesamt", zuLoeschenGesamt,
                "gruppen", gruppenListe));
    }

    /**
     * Löscht alle doppelten Dokumente. Pro dokumentNummer + Lieferant wird nur das
     * älteste (kleinste ID) behalten – alle anderen werden gelöscht.
     */
    @DeleteMapping("/duplicates")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteDuplicates() {
        log.info("Starte Duplikat-Bereinigung...");

        List<Object[]> duplicates = geschaeftsdokumentRepository.findAllDuplicates();

        if (duplicates.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "message", "Keine Duplikate gefunden",
                    "geloescht", 0));
        }

        // Gruppieren nach lieferantId + dokumentNummer
        java.util.Map<String, java.util.List<Long>> gruppen = new java.util.LinkedHashMap<>();
        for (Object[] row : duplicates) {
            Long id = ((Number) row[0]).longValue();
            String dokumentNummer = (String) row[1];
            Long lieferantId = ((Number) row[2]).longValue();

            String key = lieferantId + "_" + dokumentNummer;
            gruppen.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(id);
        }

        int geloescht = 0;
        int fehler = 0;

        for (var entry : gruppen.entrySet()) {
            var ids = entry.getValue();
            // Erstes (ältestes, kleinste ID) behalten – Rest löschen
            for (int i = 1; i < ids.size(); i++) {
                Long idToDelete = ids.get(i);
                try {
                    var dokument = dokumentRepository.findById(idToDelete).orElse(null);
                    if (dokument != null) {
                        log.info("Lösche Duplikat: ID={}, DokNr={}, Lieferant={}", 
                                idToDelete, entry.getKey().split("_")[1],
                                dokument.getLieferant() != null ? dokument.getLieferant().getLieferantenname() : "?");
                        
                        // FK-Referenzen in EmailAttachment lösen
                        var attachments = emailAttachmentRepository.findByLieferantDokumentId(idToDelete);
                        for (var att : attachments) {
                            att.setLieferantDokument(null);
                            att.setAiProcessed(false);
                            emailAttachmentRepository.save(att);
                        }
                        
                        // Verknüpfungen lösen bevor gelöscht wird
                        dokument.getVerknuepfteDokumente().clear();
                        dokument.getVerknuepftVon().forEach(vd -> vd.getVerknuepfteDokumente().remove(dokument));
                        dokument.getProjektAnteile().clear();
                        
                        // Attachment-Referenz lösen (LieferantDokument -> EmailAttachment)
                        dokument.setAttachment(null);
                        dokumentRepository.saveAndFlush(dokument);
                        
                        dokumentRepository.delete(dokument);
                        geloescht++;
                    }
                } catch (Exception e) {
                    fehler++;
                    log.error("Fehler beim Löschen von Duplikat {}: {}", idToDelete, e.getMessage());
                }
            }
        }

        log.info("Duplikat-Bereinigung abgeschlossen: {} gelöscht, {} Fehler", geloescht, fehler);

        return ResponseEntity.ok(Map.of(
                "message", geloescht + " Duplikate gelöscht",
                "geloescht", geloescht,
                "fehler", fehler,
                "gruppenBereinigt", gruppen.size()));
    }

    // ==================== DOWNLOAD ENDPOINT ====================

    /**
     * Download eines LieferantDokuments über seine ID.
     * Fallback-Endpoint für die Bestellungsübersicht.
     */
    @GetMapping("/{dokumentId}/download")
    public ResponseEntity<byte[]> downloadDokument(@PathVariable Long dokumentId) {
        LieferantDokument dokument = dokumentRepository.findById(dokumentId).orElse(null);
        if (dokument == null) {
            return ResponseEntity.notFound().build();
        }

        // Versuche Datei zu finden
        Path filePath = resolveDokumentPath(dokument);
        if (filePath == null || !Files.exists(filePath)) {
            log.warn("Datei nicht gefunden für Dokument {}", dokumentId);
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String filename = dokument.getEffektiverDateiname();
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(bytes);
        } catch (IOException e) {
            log.error("Fehler beim Lesen der Datei für Dokument {}: {}", dokumentId, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Path resolveDokumentPath(LieferantDokument dokument) {
        String dateiname = dokument.getEffektiverGespeicherterDateiname();
        if (dateiname == null && dokument.getAttachment() != null) {
            dateiname = dokument.getAttachment().getStoredFilename();
        }
        if (dateiname == null) {
            return null;
        }

        Long lieferantId = dokument.getLieferant() != null ? dokument.getLieferant().getId() : null;

        // 1. E-Mail-Attachments im Lieferant-Ordner
        if (lieferantId != null) {
            Path pfad = Path.of(mailAttachmentDir)
                    .resolve("email")
                    .resolve(lieferantId.toString())
                    .resolve(dateiname);
            if (Files.exists(pfad)) {
                return pfad;
            }
        }

        // 2. Manuell hochgeladene Dokumente
        if (lieferantId != null) {
            Path pfad = Path.of(uploadDir)
                    .resolve("lieferanten")
                    .resolve(lieferantId.toString())
                    .resolve(dateiname);
            if (Files.exists(pfad)) {
                return pfad;
            }
        }

        // 3. Attachments im attachments-Ordner
        if (lieferantId != null) {
            Path pfad = Path.of(uploadDir)
                    .resolve("attachments")
                    .resolve("lieferanten")
                    .resolve(lieferantId.toString())
                    .resolve(dateiname);
            if (Files.exists(pfad)) {
                return pfad;
            }
        }

        // 4. Vendor-Invoices
        Path pfad = Path.of(uploadDir)
                .resolve("attachments")
                .resolve("vendor-invoices")
                .resolve(dateiname);
        if (Files.exists(pfad)) {
            return pfad;
        }

        // 5. Generischer Mail-Attachment-Ordner
        pfad = Path.of(mailAttachmentDir).resolve(dateiname);
        if (Files.exists(pfad)) {
            return pfad;
        }

        return null;
    }
}
