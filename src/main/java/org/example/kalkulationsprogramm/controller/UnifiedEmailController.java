package org.example.kalkulationsprogramm.controller;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.Angebot;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailBlacklistEntry;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.dto.ContactDto;
import org.example.kalkulationsprogramm.dto.Email.UnifiedEmailDto;
import org.example.kalkulationsprogramm.dto.EmailThreadDto;
import org.example.kalkulationsprogramm.dto.ProjektEmail.ProjektEmailDto;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.AngebotRepository;
import org.example.kalkulationsprogramm.repository.EmailBlacklistRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.ContactService;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.EmailAutoAssignmentService;
import org.example.kalkulationsprogramm.service.EmailImportService;
import org.example.kalkulationsprogramm.service.EmailThreadService;
import org.example.kalkulationsprogramm.service.InquiryDetectionService;
import org.example.kalkulationsprogramm.service.SpamBayesService;
import org.example.kalkulationsprogramm.service.SpamFilterService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Unified Email API Controller.
 * 
 * Ersetzt die alten ProjektEmailController, AnfrageEmailController.
 * Bietet einheitlichen Zugriff auf das neue Email-System.
 */
@RestController
@RequestMapping("/api/emails")
@RequiredArgsConstructor
@Slf4j
public class UnifiedEmailController {

    private final EmailRepository emailRepository;
    private final ProjektRepository projektRepository;
    private final AnfrageRepository anfrageRepository;
    private final AngebotRepository angebotRepository;
    private final LieferantenRepository lieferantenRepository;
    private final EmailAutoAssignmentService emailAutoAssignmentService;
    private final EmailImportService emailImportService;
    private final SpamFilterService spamFilterService;
    private final InquiryDetectionService inquiryDetectionService;
    private final EmailBlacklistRepository emailBlacklistRepository;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final AnfrageDokumentRepository anfrageDokumentRepository;
    private final DateiSpeicherService dateiSpeicherService;
    private final ContactService contactService;
    private final SpamBayesService spamBayesService;
    private final EmailThreadService emailThreadService;

    @org.springframework.beans.factory.annotation.Value("${file.mail-attachment-dir}")
    private String mailAttachmentDir;

    @GetMapping("/{emailId}/attachments/{attachmentId}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadAttachment(
            @PathVariable Long emailId,
            @PathVariable Long attachmentId) {

        log.info("Requesting attachment: emailId={}, attachmentId={}", emailId, attachmentId);

        Email email = emailRepository.findById(emailId).orElse(null);
        if (email == null) {
            log.warn("Email not found: {}", emailId);
            return ResponseEntity.notFound().build();
        }

        EmailAttachment attachment = email.getAttachments().stream()
                .filter(a -> a.getId().equals(attachmentId))
                .findFirst()
                .orElse(null);

        if (attachment == null) {
            log.warn("Attachment not found in email {}: {}", emailId, attachmentId);
            return ResponseEntity.notFound().build();
        }

        try {
            java.nio.file.Path baseDir = Path.of(mailAttachmentDir).toAbsolutePath().normalize();

            // 1. Strategie: Flache Struktur (Neu & Legacy) - Priorisiert (User Request)
            java.nio.file.Path path = baseDir.resolve(attachment.getStoredFilename());

            // 2. Strategie: Unterordner pro Email-ID (Zwischenlösung)
            if (!java.nio.file.Files.exists(path)) {
                path = baseDir.resolve(String.valueOf(emailId)).resolve(attachment.getStoredFilename());
            }

            // 3. Strategie: Extra 'attachments' Unterordner (Fehlerhafter Pfad aus
            // vorheriger Version, falls schon Daten so gespeichert wurden)
            if (!java.nio.file.Files.exists(path)) {
                path = baseDir.resolve("attachments").resolve(String.valueOf(emailId))
                        .resolve(attachment.getStoredFilename());
            }

            if (!java.nio.file.Files.exists(path)) {
                log.error("Attachment file found in neither location!");
                return ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(path.toUri());

            // Bestimme MIME-Type intelligent:
            // 1. Versuche den gespeicherten MIME-Type zu verwenden
            // 2. Wenn null oder octet-stream: Verwende Files.probeContentType
            // 3. Fallback auf Dateinamensbasierte Erkennung für PDFs
            // HINWEIS: MIME-Types können Parameter enthalten wie "application/octet-stream;
            // name=file.pdf"
            // daher verwenden wir startsWith statt equals
            String mimeType = attachment.getMimeType();

            if (mimeType == null || mimeType.startsWith("application/octet-stream")) {
                try {
                    mimeType = java.nio.file.Files.probeContentType(path);
                } catch (Exception ignored) {
                    // probeContentType kann fehlschlagen
                }
            }

            // Fallback: Erkennung anhand des Dateinamens
            if (mimeType == null || mimeType.startsWith("application/octet-stream")) {
                String filename = attachment.getOriginalFilename();
                if (filename != null) {
                    String lower = filename.toLowerCase();
                    if (lower.endsWith(".pdf")) {
                        mimeType = "application/pdf";
                    } else if (lower.endsWith(".png")) {
                        mimeType = "image/png";
                    } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                        mimeType = "image/jpeg";
                    } else if (lower.endsWith(".gif")) {
                        mimeType = "image/gif";
                    } else if (lower.endsWith(".xml")) {
                        mimeType = "application/xml";
                    } else if (lower.endsWith(".txt")) {
                        mimeType = "text/plain";
                    }
                }
            }

            // Letzter Fallback
            if (mimeType == null) {
                mimeType = "application/octet-stream";
            }

            log.debug("Serving attachment {} with MIME-Type: {}", attachmentId, mimeType);

            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(mimeType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + attachment.getOriginalFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("Fehler beim Download des Attachments", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/import")
    public ResponseEntity<String> triggerImport() {
        int count = emailImportService.triggerImport();
        return ResponseEntity.ok(count + " Emails processed (imported + reclassified)");
    }

    @PostMapping("/{emailId}/block-sender")
    @Transactional
    public ResponseEntity<String> blockSender(@PathVariable Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Email not found"));

        String sender = email.getFromAddress();
        if (sender == null || sender.isBlank()) {
            return ResponseEntity.badRequest().body("Email has no sender");
        }

        sender = sender.toLowerCase();

        // 1. Add to Blacklist
        if (!emailBlacklistRepository.existsByEmailAddress(sender)) {
            emailBlacklistRepository.save(new EmailBlacklistEntry(sender));
        }

        // 2. Retroactive Spam Marking
        List<Email> existingEmails = emailRepository
                .findByFromAddressIgnoreCase(sender);
        int count = 0;
        for (org.example.kalkulationsprogramm.domain.Email e : existingEmails) {
            e.setSpam(true);
            e.setSpamScore(100);
            count++;
        }
        emailRepository.saveAll(existingEmails);

        return ResponseEntity.ok("Sender blocked and " + count + " emails moved to spam.");
    }

    // ═══════════════════════════════════════════════════════════════
    // SPAM ML-FEEDBACK (Supervised Learning)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Implizites Ham-Training: Wenn ein User eine Email zuordnet oder beantwortet,
     * ist das ein starkes Signal, dass die Email kein Spam ist.
     */
    private void trainImplicitHam(Email email) {
        if (email.getUserSpamVerdict() != null) return; // User hat schon explizit entschieden
        if (spamBayesService.isModelReady()) {
            spamBayesService.train(email, false);
            email.setUserSpamVerdict("HAM_IMPLICIT");
            log.debug("[SpamML] Implizites Ham-Training für Email {}, subject='{}'",
                    email.getId(), email.getSubject());
        }
    }

    @PostMapping("/{emailId}/mark-spam")
    @Transactional
    public ResponseEntity<String> markAsSpam(@PathVariable Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Email not found"));

        email.setUserSpamVerdict("SPAM");
        email.setSpam(true);
        email.setSpamScore(100);
        emailRepository.save(email);

        spamBayesService.train(email, true);

        log.info("[SpamML] Email {} als Spam markiert (User-Feedback), subject='{}'",
                emailId, email.getSubject());
        return ResponseEntity.ok("Email als Spam markiert und Modell trainiert.");
    }

    @PostMapping("/{emailId}/mark-not-spam")
    @Transactional
    public ResponseEntity<String> markAsNotSpam(@PathVariable Long emailId) {
        Email email = emailRepository.findById(emailId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Email not found"));

        email.setUserSpamVerdict("HAM");
        email.setSpam(false);
        email.setNewsletter(false);
        email.setSpamScore(0);
        emailRepository.save(email);

        spamBayesService.train(email, false);

        log.info("[SpamML] Email {} als Nicht-Spam markiert (User-Feedback), subject='{}'",
                emailId, email.getSubject());
        return ResponseEntity.ok("Email als kein Spam markiert und Modell trainiert.");
    }

    @PostMapping("/spam-model/bootstrap")
    public ResponseEntity<Map<String, Object>> bootstrapSpamModel(
            @RequestParam("file") MultipartFile file) {
        try {
            int[] result = spamBayesService.bootstrapFromCsv(file.getInputStream());
            return ResponseEntity.ok(Map.of(
                    "message", "Modell mit " + (result[0] + result[1]) + " Datensätzen trainiert.",
                    "spamImported", result[0],
                    "hamImported", result[1],
                    "modelReady", spamBayesService.isModelReady()
            ));
        } catch (Exception e) {
            log.error("[SpamML] Fehler beim CSV-Bootstrap", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Fehler beim Import: " + e.getMessage()));
        }
    }

    @GetMapping("/spam-model/stats")
    public ResponseEntity<Map<String, Object>> getSpamModelStats() {
        return ResponseEntity.ok(Map.of(
                "totalSpam", spamBayesService.getTotalSpam(),
                "totalHam", spamBayesService.getTotalHam(),
                "vocabularySize", spamBayesService.getVocabularySize(),
                "modelReady", spamBayesService.isModelReady(),
                "minTrainingSamples", 20
        ));
    }

    @PostMapping("/spam-model/rescore")
    @Transactional
    public ResponseEntity<Map<String, Object>> rescoreAllEmails() {
        if (!spamBayesService.isModelReady()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Spam-Modell noch nicht trainiert."));
        }
        List<Email> allEmails = emailRepository.findAll();
        int rescored = 0;
        for (Email email : allEmails) {
            spamFilterService.analyzeAndMarkSpam(email);
            emailRepository.save(email);
            rescored++;
        }
        return ResponseEntity.ok(Map.of(
                "message", rescored + " Emails neu bewertet.",
                "rescored", rescored
        ));
    }

    // ═══════════════════════════════════════════════════════════════
    // EMAILS FÜR PROJEKTE
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/projekt/{projektId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UnifiedEmailDto>> getEmailsByProjekt(
            @PathVariable Long projektId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        Projekt projekt = projektRepository.findById(projektId).orElse(null);
        if (projekt == null) {
            return ResponseEntity.notFound().build();
        }
        List<Email> emails = emailRepository.findByProjektOrderBySentAtDesc(projekt);
        return ResponseEntity.ok(emails.stream()
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList()));
    }

    // ═══════════════════════════════════════════════════════════════
    // EMAILS FÜR ANGEBOTE (über zugehöriges Projekt)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/angebot/{angebotId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UnifiedEmailDto>> getEmailsByAngebot(
            @PathVariable Long angebotId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        Angebot angebot = angebotRepository.findById(angebotId).orElse(null);
        if (angebot == null) {
            return ResponseEntity.notFound().build();
        }
        Projekt projekt = angebot.getProjekt();
        if (projekt == null) {
            return ResponseEntity.ok(List.of());
        }
        List<Email> emails = emailRepository.findByProjektOrderBySentAtDesc(projekt);
        return ResponseEntity.ok(emails.stream()
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList()));
    }

    // ═══════════════════════════════════════════════════════════════
    // EMAILS FÜR ANFRAGEN
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/anfrage/{anfrageId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UnifiedEmailDto>> getEmailsByAnfrage(
            @PathVariable Long anfrageId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        Anfrage anfrage = anfrageRepository.findById(anfrageId).orElse(null);
        if (anfrage == null) {
            return ResponseEntity.notFound().build();
        }
        List<Email> emails = emailRepository.findByAnfrageOrderBySentAtDesc(anfrage);
        return ResponseEntity.ok(emails.stream()
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList()));
    }

    // ═══════════════════════════════════════════════════════════════
    // EMAILS FÜR LIEFERANTEN
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/lieferant/{lieferantId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<UnifiedEmailDto>> getEmailsByLieferant(
            @PathVariable Long lieferantId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        Lieferanten lieferant = lieferantenRepository.findById(lieferantId).orElse(null);
        if (lieferant == null) {
            return ResponseEntity.notFound().build();
        }
        List<Email> emails = emailRepository.findByLieferantOrderBySentAtDesc(lieferant);
        return ResponseEntity.ok(emails.stream()
                .limit(limit)
                .map(this::toDto)
                .collect(Collectors.toList()));
    }

    // ═══════════════════════════════════════════════════════════════
    // NICHT ZUGEORDNETE EMAILS (EmailCenter)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/unassigned")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> getUnassignedEmails(
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return emailRepository.findUnassigned().stream()
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // ANFRAGEN (Inquiries)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/inquiries")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> getInquiryEmails(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return emailRepository.findPotentialInquiries().stream()
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // "NEUE" EMAILS (Nach Typ sortiert, für Inbox-Tabs)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/new/projekt")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> getNewProjektEmails(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return emailRepository.findByZuordnungTypOrderBySentAtDesc(EmailZuordnungTyp.PROJEKT).stream()
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/new/anfrage")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> getNewAnfrageEmails(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return emailRepository.findByZuordnungTypOrderBySentAtDesc(EmailZuordnungTyp.ANFRAGE).stream()
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/new/lieferant")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> getNewLieferantEmails(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return emailRepository.findByZuordnungTypOrderBySentAtDesc(EmailZuordnungTyp.LIEFERANT).stream()
                .filter(e -> e.getDeletedAt() == null)
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // GLOBALE SUCHE
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> searchEmails(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        return emailRepository.searchGlobal(query.trim()).stream()
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // POSTEINGANG / GESENDET / PAPIERKORB
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/inbox")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> getInboxEmails(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        // Get IDs of "Nicht zugeordnet" emails to exclude from inbox
        Set<Long> unassignedIds = emailRepository.findUnassigned().stream()
                .map(Email::getId)
                .collect(Collectors.toSet());

        return emailRepository.findInboxFiltered().stream()
                .filter(e -> !unassignedIds.contains(e.getId())) // Exclude "Nicht zugeordnet"
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/projects")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> getProjectFolderEmails(
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return emailRepository.findProjectEmails().stream()
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/offers")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> getOfferFolderEmails(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return emailRepository.findAnfrageEmails().stream()
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/suppliers")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> getSupplierFolderEmails(
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return emailRepository.findLieferantEmails().stream()
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/sent")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> getSentEmails(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return emailRepository.findByDirectionOrderBySentAtDesc(EmailDirection.OUT).stream()
                .filter(e -> e.getDeletedAt() == null)
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/trash")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> getTrashEmails(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return emailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc().stream()
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/spam")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> getSpamEmails(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return emailRepository.findSpam().stream()
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/newsletter")
    @Transactional(readOnly = true)
    public List<UnifiedEmailDto> getNewsletterEmails(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return emailRepository.findNewsletter().stream()
                .limit(limit)
                .map(this::toListDto)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // EINZELNE EMAIL
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<UnifiedEmailDto> getEmailById(@PathVariable Long id) {
        return emailRepository.findById(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Liefert den vollstaendigen E-Mail-Thread fuer eine gegebene E-Mail-ID.
     * Die angeklickte E-Mail ist als focusedEmailId markiert und wird im Frontend
     * auto-expandiert und hervorgehoben.
     *
     * @param emailId ID der fokussierten E-Mail
     * @return EmailThreadDto mit chronologisch sortierten Eintraegen
     */
    @GetMapping("/{emailId}/thread")
    @Transactional(readOnly = true)
    public ResponseEntity<EmailThreadDto> getEmailThread(@PathVariable Long emailId) {
        log.debug("Thread requested for emailId={}", emailId);
        EmailThreadDto thread = emailThreadService.loadThreadFor(emailId);
        return ResponseEntity.ok(thread);
    }

    /**
     * Einmaliger Backfill: Dekodiert alle EmailAttachment-Dateinamen in der DB,
     * die noch als MIME encoded-word vorliegen (=?iso-8859-1?Q?...?=).
     * Idempotent – bereits dekodierte Namen werden nicht erneut angefasst.
     */
    @PostMapping("/admin/backfill-attachment-filenames")
    public ResponseEntity<Map<String, Object>> backfillAttachmentFilenames() {
        log.info("Backfill attachment filenames gestartet");
        int updated = emailImportService.backfillAttachmentFilenames();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "updated", updated
        ));
    }

    @GetMapping("/{id}/mark-viewed")
    @Transactional
    public ResponseEntity<Void> markAsViewed(@PathVariable Long id) {
        Email email = emailRepository.findById(id).orElse(null);
        if (email == null) {
            return ResponseEntity.notFound().build();
        }
        if (email.getFirstViewedAt() == null) {
            email.setFirstViewedAt(LocalDateTime.now());
            emailRepository.save(email);
        }
        return ResponseEntity.ok().build();
    }

    // ═══════════════════════════════════════════════════════════════
    // ZUORDNUNG ÄNDERN
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/{id}/assign/projekt/{projektId}")
    @Transactional
    public ResponseEntity<UnifiedEmailDto> assignToProjekt(
            @PathVariable Long id,
            @PathVariable Long projektId) {
        Email email = emailRepository.findById(id).orElse(null);
        Projekt projekt = projektRepository.findById(projektId).orElse(null);
        if (email == null || projekt == null) {
            return ResponseEntity.notFound().build();
        }
        email.assignToProjekt(projekt);
        trainImplicitHam(email);
        emailRepository.save(email);
        return ResponseEntity.ok(toDto(email));
    }

    @PostMapping("/{id}/assign/anfrage/{anfrageId}")
    @Transactional
    public ResponseEntity<UnifiedEmailDto> assignToAnfrage(
            @PathVariable Long id,
            @PathVariable Long anfrageId) {
        Email email = emailRepository.findById(id).orElse(null);
        Anfrage anfrage = anfrageRepository.findById(anfrageId).orElse(null);
        if (email == null || anfrage == null) {
            return ResponseEntity.notFound().build();
        }
        email.assignToAnfrage(anfrage);
        trainImplicitHam(email);
        emailRepository.save(email);
        return ResponseEntity.ok(toDto(email));
    }

    @PostMapping("/{id}/assign/lieferant/{lieferantId}")
    @Transactional
    public ResponseEntity<UnifiedEmailDto> assignToLieferant(
            @PathVariable Long id,
            @PathVariable Long lieferantId) {
        Email email = emailRepository.findById(id).orElse(null);
        Lieferanten lieferant = lieferantenRepository.findById(lieferantId).orElse(null);
        if (email == null || lieferant == null) {
            return ResponseEntity.notFound().build();
        }
        email.assignToLieferant(lieferant);
        trainImplicitHam(email);
        emailRepository.save(email);
        return ResponseEntity.ok(toDto(email));
    }

    @PostMapping("/{id}/unassign")
    @Transactional
    public ResponseEntity<UnifiedEmailDto> removeAssignment(@PathVariable Long id) {
        Email email = emailRepository.findById(id).orElse(null);
        if (email == null) {
            return ResponseEntity.notFound().build();
        }
        email.clearAssignment();
        emailRepository.save(email);
        return ResponseEntity.ok(toDto(email));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteEmail(@PathVariable Long id) {
        Email email = emailRepository.findById(id).orElse(null);
        if (email == null) {
            return ResponseEntity.notFound().build();
        }

        // Soft delete (Papierkorb)
        email.setDeletedAt(LocalDateTime.now());
        emailRepository.save(email);

        log.info("Email {} in den Papierkorb verschoben", id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/permanent")
    @Transactional
    public ResponseEntity<Void> deleteEmailPermanently(@PathVariable Long id) {
        Email email = emailRepository.findById(id).orElse(null);
        if (email == null) {
            return ResponseEntity.notFound().build();
        }

        // Hard delete
        // Optional: Löschen vom Mailserver hier triggern
        try {
            emailImportService.deleteEmailFromServer(email);
        } catch (Exception e) {
            log.error("Fehler beim Löschen vom Mailserver: {}", e.getMessage());
            // Wir machen weiter mit DB-Löschung
        }

        emailRepository.delete(email);
        log.info("Email {} permanent gelöscht", id);
        return ResponseEntity.noContent().build();
    }

    /** Alle E-Mails eines Ordners auf gelesen setzen. */
    @PostMapping("/mark-all-read")
    @Transactional
    public ResponseEntity<Map<String, Integer>> markAllRead(@RequestParam String folder) {
        List<Email> emails = switch (folder) {
            case "inbox"      -> emailRepository.findInboxFiltered();
            case "spam"       -> emailRepository.findSpam();
            case "newsletter" -> emailRepository.findNewsletter();
            case "unassigned" -> emailRepository.findUnassigned();
            case "inquiries"  -> emailRepository.findPotentialInquiries();
            case "projects"   -> emailRepository.findProjectEmails();
            case "offers"     -> emailRepository.findAnfrageEmails();
            case "suppliers"  -> emailRepository.findLieferantEmails();
            case "trash"      -> emailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc();
            case "sent"       -> java.util.Collections.emptyList();
            default           -> java.util.Collections.emptyList();
        };

        int count = 0;
        for (Email email : emails) {
            if (!email.isRead()) {
                email.setRead(true);
                if (email.getFirstViewedAt() == null) email.setFirstViewedAt(LocalDateTime.now());
                count++;
            }
        }
        if (count > 0) emailRepository.saveAll(emails);
        return ResponseEntity.ok(Map.of("updated", count));
    }

    @PostMapping("/{id}/mark-read")
    @Transactional
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        Email email = emailRepository.findById(id).orElse(null);
        if (email == null) {
            return ResponseEntity.notFound().build();
        }

        if (!email.isRead()) {
            email.setRead(true);
            email.setFirstViewedAt(LocalDateTime.now());
            emailRepository.save(email);
        }

        return ResponseEntity.ok().build();
    }

    // ═══════════════════════════════════════════════════════════════
    // STATISTIKEN
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/stats")
    public FolderStatsDto getStats() {
        FolderStatsDto stats = new FolderStatsDto();

        // Get unassigned email IDs to exclude from inbox count
        Set<Long> unassignedIds = emailRepository.findUnassigned().stream()
                .map(Email::getId)
                .collect(Collectors.toSet());

        // Count unread in inbox, excluding "Nicht zugeordnet" emails
        long inboxUnread = emailRepository.findInboxFiltered().stream()
                .filter(e -> !unassignedIds.contains(e.getId()))
                .filter(e -> !e.isRead())
                .count();
        stats.setInboxCount(inboxUnread);

        // Other unread counts
        stats.setProjectCount(emailRepository.countProjectEmailsUnread());
        stats.setOfferCount(emailRepository.countAnfrageEmailsUnread());
        stats.setSupplierCount(emailRepository.countLieferantEmailsUnread());

        stats.setNewsletterCount(emailRepository.countNewsletterUnread());
        stats.setSpamCount(emailRepository.countSpamUnread());

        // Total Counts (Gesendet, Papierkorb, Nicht zugeordnet, Anfragen)
        stats.setSentCount(emailRepository.countByDirectionAndIsReadFalse(EmailDirection.OUT));
        // Trash count
        stats.setTrashCount(emailRepository.countByDeletedAtIsNotNullAndIsReadFalse());

        stats.setUnassignedCount(emailRepository.countUnassigned());
        stats.setInquiriesCount(emailRepository.countPotentialInquiries());

        return stats;
    }

    /**
     * Führt den Spam-Filter rückwirkend auf alle noch nicht analysierten Emails
     * aus.
     * Nur nicht-zugeordnete, eingehende Emails werden gescannt.
     */
    @PostMapping("/scan-spam")
    @Transactional
    public SpamFilterService.ScanResult scanSpamRetroactive() {
        List<Email> unanalyzed = emailRepository.findUnanalyzedForSpam();
        int spamFound = 0;
        int notSpam = 0;

        for (Email email : unanalyzed) {
            spamFilterService.analyzeAndMarkSpam(email);
            emailRepository.save(email);
            if (email.isSpam()) {
                spamFound++;
            } else {
                notSpam++;
            }
        }

        log.info("[SpamFilter] Rückwirkender Scan: {} Emails analysiert, {} Spam gefunden", unanalyzed.size(),
                spamFound);
        return new SpamFilterService.ScanResult(unanalyzed.size(), spamFound, notSpam);
    }

    /**
     * Rückwirkender Anfrage-Scan für alle noch nicht analysierten Emails.
     * Setzt inquiryScore und isPotentialInquiry basierend auf Keyword-Scoring.
     */
    @PostMapping("/scan-inquiries")
    @Transactional
    public InquiryDetectionService.ScanResult scanInquiriesRetroactive() {
        List<Email> unanalyzed = emailRepository.findUnanalyzedForInquiry();
        int inquiriesFound = 0;
        int notInquiries = 0;

        for (Email email : unanalyzed) {
            inquiryDetectionService.analyzeAndMarkInquiry(email);
            emailRepository.save(email);
            if (email.isPotentialInquiry()) {
                inquiriesFound++;
            } else {
                notInquiries++;
            }
        }

        log.info("[InquiryDetection] Rückwirkender Scan: {} Emails analysiert, {} Anfragen gefunden", unanalyzed.size(),
                inquiriesFound);
        return new InquiryDetectionService.ScanResult(unanalyzed.size(), inquiriesFound, notInquiries);
    }

    /**
     * Startet die automatische Zuordnung für alle unzugeordneten E-Mails im
     * Posteingang erneut.
     */
    @PostMapping("/scan-assignments")
    @Transactional
    public ResponseEntity<Map<String, Object>> scanAssignments() {
        log.info("Starte manuelle Zuordnung und Spam-Prüfung...");

        // 1. Spam & Newsletter neu prüfen
        int scannedSpam = emailImportService.reprocessSpam();

        // 2. Zuordnung prüfen
        List<Email> unassigned = emailRepository.findByZuordnungTypOrderBySentAtDesc(EmailZuordnungTyp.KEINE).stream()
                .filter(e -> e.getDirection() == EmailDirection.IN && e.getDeletedAt() == null)
                .collect(Collectors.toList());

        int assignedCount = 0;

        for (Email email : unassigned) {
            if (emailAutoAssignmentService.tryAutoAssign(email)) {
                assignedCount++;
            }
        }

        log.info("Zuordnung abgeschlossen. {} zugeordnet. {} Spam/Newsletter geprüft.", assignedCount, scannedSpam);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "scanned", unassigned.size(),
                "assigned", assignedCount,
                "checkedSpam", scannedSpam,
                "message", assignedCount + " zugeordnet. " + scannedSpam + " auf Spam/Newsletter geprüft."));
    }

    @GetMapping("/contacts")
    public List<ContactDto> searchContacts(@RequestParam("q") String query) {
        return contactService.searchContacts(query);
    }

    @GetMapping("/{id}/possible-assignments")
    public ResponseEntity<EmailAutoAssignmentService.PossibleAssignments> getPossibleAssignments(
            @PathVariable Long id) {
        Email email = emailRepository.findById(id)
                .orElseThrow(() -> new org.example.kalkulationsprogramm.exception.NotFoundException(
                        "Email nicht gefunden: " + id));

        return ResponseEntity.ok(emailAutoAssignmentService.findPossibleAssignments(email));
    }

    /**
     * Backfill: Verknüpft bestehende Emails nachträglich mit ihren Parent-Emails.
     * Erbt auch die Zuordnung (Projekt/Anfrage/Lieferant) vom Parent.
     */
    @PostMapping("/backfill-parents")
    public ResponseEntity<Map<String, Object>> backfillParentEmails() {
        int updated = emailImportService.backfillParentEmails();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "updatedCount", updated,
                "message", updated + " Emails wurden mit ihren Parent-Emails verknüpft"));
    }

    /**
     * Rückwirkender Steuerberater-Scan (Lohnabrechnungen, BWAs).
     */
    @PostMapping("/scan-steuerberater")
    public ResponseEntity<Map<String, Object>> scanSteuerberaterRetroactive() {
        int processed = emailImportService.backfillSteuerberaterEmails();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "processedCount", processed,
                "message", processed + " Emails als Steuerberater-Emails verifiziert/verarbeitet"));
    }

    // ═══════════════════════════════════════════════════════════════
    // E-MAIL SENDEN / ANTWORTEN
    // ═══════════════════════════════════════════════════════════════

    @org.springframework.beans.factory.annotation.Value("${spring.mail.host:}")
    private String smtpHost;
    @org.springframework.beans.factory.annotation.Value("${spring.mail.port:587}")
    private int smtpPort;
    @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}")
    private String smtpUsername;
    @org.springframework.beans.factory.annotation.Value("${spring.mail.password:}")
    private String smtpPassword;

    @PostMapping(value = "/send", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<UnifiedEmailDto> sendEmail(
            @RequestPart("dto") ProjektEmailDto dto,
            @RequestPart(value = "attachments", required = false) MultipartFile[] attachments,
            @RequestPart(value = "dokumentId", required = false) String dokumentIdStr) {

        try {
            // E-Mail senden via SMTP
            org.example.email.EmailService emailService = new org.example.email.EmailService(smtpHost, smtpPort,
                    smtpUsername, smtpPassword);

            // Attachments vorbereiten
            List<String> attachmentPaths = new ArrayList<>();
            List<java.io.File> tempFiles = new ArrayList<>();

            // 1. Dokument anhängen (wenn dokumentId angegeben)
            // WICHTIG: Basierend auf DTO-Kontext das RICHTIGE Repository verwenden!
            ProjektDokument projektDokument = null;
            AnfrageDokument anfrageDokument = null;
            String attachedOriginalFilename = null;
            String attachedStoredFilename = null;

            if (dokumentIdStr != null && !dokumentIdStr.isBlank()) {
                try {
                    Long dokumentId = Long.parseLong(dokumentIdStr);

                    // Kontext-basierte Suche: Anfrage vs Projekt
                    if (dto.getAnfrageId() != null) {
                        // Anfrage-Kontext: NUR in AnfrageDokumentRepository suchen
                        anfrageDokument = anfrageDokumentRepository.findById(dokumentId).orElse(null);
                        if (anfrageDokument != null && anfrageDokument.getGespeicherterDateiname() != null) {
                            attachedOriginalFilename = anfrageDokument.getOriginalDateiname();
                            attachedStoredFilename = anfrageDokument.getGespeicherterDateiname();
                            log.info("Anfrage-Kontext: Dokument '{}' gefunden (ID: {})", attachedOriginalFilename,
                                    dokumentId);
                        } else {
                            log.warn("Anfrage-Kontext: Dokument mit ID {} nicht in AnfrageDokument gefunden",
                                    dokumentId);
                        }
                    } else {
                        // Projekt-Kontext: NUR in ProjektDokumentRepository suchen
                        projektDokument = projektDokumentRepository.findById(dokumentId).orElse(null);
                        if (projektDokument != null && projektDokument.getGespeicherterDateiname() != null) {
                            attachedOriginalFilename = projektDokument.getOriginalDateiname();
                            attachedStoredFilename = projektDokument.getGespeicherterDateiname();
                            log.info("Projekt-Kontext: Dokument '{}' gefunden (ID: {})", attachedOriginalFilename,
                                    dokumentId);
                        } else {
                            log.warn("Projekt-Kontext: Dokument mit ID {} nicht in ProjektDokument gefunden",
                                    dokumentId);
                        }
                    }

                    // Dokument als Anhang laden
                    if (attachedStoredFilename != null) {
                        try {
                            org.springframework.core.io.Resource resource = dateiSpeicherService
                                    .ladeDokumentAlsResource(attachedStoredFilename);
                            if (resource != null && resource.exists()) {
                                // Wichtig: Kopiere mit Originaldateiname in Temp-Verzeichnis
                                // damit der SMTP-Anhang den korrekten Namen hat
                                java.io.File tempFile = java.io.File.createTempFile("email_dok_",
                                        "_" + attachedOriginalFilename);
                                // Umbenennen zu Originaldateiname im gleichen Verzeichnis
                                java.io.File renamedFile = new java.io.File(tempFile.getParent(),
                                        attachedOriginalFilename);
                                if (renamedFile.exists())
                                    renamedFile.delete();
                                java.nio.file.Files.copy(resource.getFile().toPath(), renamedFile.toPath(),
                                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                tempFile.delete(); // Original temp file löschen
                                attachmentPaths.add(renamedFile.getAbsolutePath());
                                tempFiles.add(renamedFile); // Für spätere Aufräumung
                                log.info("Dokument '{}' als Anhang hinzugefügt", attachedOriginalFilename);
                            }
                        } catch (Exception e) {
                            log.warn("Konnte Dokument {} nicht laden: {}", dokumentId, e.getMessage());
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("Ungültige dokumentId: {}", dokumentIdStr);
                }
            }

            // 2. Hochgeladene Dateien als Anhänge
            if (attachments != null) {
                for (MultipartFile file : attachments) {
                    if (!file.isEmpty()) {
                        String safeFilename = file.getOriginalFilename() != null
                                ? file.getOriginalFilename().replaceAll("[\\\\/:*?\"<>|]", "_")
                                : "attachment";
                        java.io.File tempFile = java.io.File.createTempFile("email_", "_" + safeFilename);
                        file.transferTo(tempFile);
                        attachmentPaths.add(tempFile.getAbsolutePath());
                        tempFiles.add(tempFile);
                    }
                }
            }

            String htmlBody = dto.getBody() != null ? dto.getBody() : "";

            // Join recipients (TO)
            String recipient = dto.getRecipients() != null && !dto.getRecipients().isEmpty()
                    ? String.join(",", dto.getRecipients())
                    : null;

            if (recipient == null || recipient.isBlank()) {
                return ResponseEntity.badRequest().build();
            }

            // Join CCs
            String cc = dto.getCc() != null && !dto.getCc().isEmpty()
                    ? String.join(",", dto.getCc())
                    : null;

            String sender = dto.getSender() != null ? dto.getSender() : "bauschlosserei-kuhn@t-online.de";

            String messageId = emailService.sendEmailWithMultipleAttachments(
                    recipient,
                    cc, // Pass CC here
                    sender,
                    dto.getSubject(),
                    htmlBody,
                    Map.of(),
                    attachmentPaths);

            // Email-Entität speichern
            Email email = new Email();
            email.setMessageId(messageId);
            email.setFromAddress(sender);
            email.setRecipient(recipient);
            email.setSubject(dto.getSubject());
            email.setBody(org.example.kalkulationsprogramm.util.EmailHtmlSanitizer.htmlToPlainText(htmlBody));
            email.setHtmlBody(htmlBody);
            email.setRawBody(htmlBody);
            email.setSentAt(LocalDateTime.now());
            email.setDirection(EmailDirection.OUT);

            // Zuordnung (optional)
            if (dto.getProjektId() != null) {
                projektRepository.findById(dto.getProjektId()).ifPresent(email::assignToProjekt);
            } else if (dto.getAnfrageId() != null) {
                anfrageRepository.findById(dto.getAnfrageId()).ifPresent(email::assignToAnfrage);
            } else if (dto.getLieferantId() != null) {
                lieferantenRepository.findById(dto.getLieferantId()).ifPresent(email::assignToLieferant);
            }

            // WICHTIG: saveAndFlush statt save, damit messageId sofort committed ist
            // und der IMAP-Import die E-Mail als Duplikat erkennt
            emailRepository.saveAndFlush(email);

            // Versanddatum auf dem angehängten Dokument setzen
            if (projektDokument != null) {
                projektDokument.setEmailVersandDatum(java.time.LocalDate.now());
                projektDokumentRepository.save(projektDokument);
                log.info("Versanddatum für ProjektDokument {} gesetzt", projektDokument.getId());
            }
            if (anfrageDokument != null) {
                anfrageDokument.setEmailVersandDatum(java.time.LocalDate.now());
                anfrageDokumentRepository.save(anfrageDokument);
                log.info("Versanddatum für AnfrageDokument {} gesetzt", anfrageDokument.getId());
            }

            // Attachments speichern
            java.nio.file.Path baseDir = Path.of(mailAttachmentDir);
            java.nio.file.Files.createDirectories(baseDir);

            // 1. Dokument als Attachment speichern (Projekt ODER Anfrage)
            if (attachedStoredFilename != null && attachedOriginalFilename != null) {
                try {
                    org.springframework.core.io.Resource resource = dateiSpeicherService
                            .ladeDokumentAlsResource(attachedStoredFilename);
                    if (resource != null && resource.exists()) {
                        String safeAttachedName = attachedOriginalFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
                        String storedName = java.util.UUID.randomUUID() + "_" + safeAttachedName;
                        java.nio.file.Path dst = baseDir.resolve(storedName);
                        java.nio.file.Files.copy(resource.getFile().toPath(), dst,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                        EmailAttachment att = new EmailAttachment();
                        att.setEmail(email);
                        att.setOriginalFilename(attachedOriginalFilename);
                        att.setStoredFilename(storedName);
                        att.setSizeBytes(java.nio.file.Files.size(dst));
                        att.setMimeType("application/pdf");
                        email.addAttachment(att);
                        log.info("Dokument '{}' als EmailAttachment gespeichert", attachedOriginalFilename);
                    }
                } catch (Exception e) {
                    log.warn("Konnte Dokument nicht als EmailAttachment speichern: {}", e.getMessage());
                }
            }

            // 2. Hochgeladene Dateien als Attachments speichern
            if (attachments != null) {
                for (int i = 0; i < attachments.length; i++) {
                    MultipartFile file = attachments[i];
                    if (!file.isEmpty()) {
                        String safeOrigName = file.getOriginalFilename() != null
                                ? file.getOriginalFilename().replaceAll("[\\\\/:*?\"<>|]", "_")
                                : "attachment";
                        String storedName = java.util.UUID.randomUUID() + "_" + safeOrigName;
                        java.nio.file.Path dst = baseDir.resolve(storedName);
                        java.nio.file.Files.copy(tempFiles.get(i).toPath(), dst,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                        EmailAttachment att = new EmailAttachment();
                        att.setEmail(email);
                        att.setOriginalFilename(file.getOriginalFilename());
                        att.setStoredFilename(storedName);
                        att.setSizeBytes(file.getSize());
                        att.setMimeType(file.getContentType());
                        email.addAttachment(att);
                    }
                }
            }
            emailRepository.save(email);

            // Temp files aufräumen
            for (java.io.File f : tempFiles) {
                try {
                    f.delete();
                } catch (Exception ignored) {
                }
            }

            return ResponseEntity.ok(toDto(email));
        } catch (Exception e) {
            log.error("Fehler beim Senden der Email", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/{emailId}/reply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<UnifiedEmailDto> replyToEmail(
            @PathVariable Long emailId,
            @RequestPart("dto") ProjektEmailDto dto,
            @RequestPart(value = "attachments", required = false) MultipartFile[] attachments) {

        Email parentEmail = emailRepository.findById(emailId).orElse(null);
        if (parentEmail == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            // E-Mail senden via SMTP
            org.example.email.EmailService emailService = new org.example.email.EmailService(smtpHost, smtpPort,
                    smtpUsername, smtpPassword);

            // Attachments vorbereiten
            List<String> attachmentPaths = new ArrayList<>();
            List<java.io.File> tempFiles = new ArrayList<>();

            if (attachments != null) {
                for (MultipartFile file : attachments) {
                    if (!file.isEmpty()) {
                        String safeFilename = file.getOriginalFilename() != null
                                ? file.getOriginalFilename().replaceAll("[\\\\/:*?\"<>|]", "_")
                                : "attachment";
                        java.io.File tempFile = java.io.File.createTempFile("email_", "_" + safeFilename);
                        file.transferTo(tempFile);
                        attachmentPaths.add(tempFile.getAbsolutePath());
                        tempFiles.add(tempFile);
                    }
                }
            }

            String htmlBody = dto.getBody() != null ? dto.getBody() : "";
            String recipient = dto.getRecipients() != null && !dto.getRecipients().isEmpty()
                    ? dto.getRecipients().get(0)
                    : parentEmail.getFromAddress();
            String sender = dto.getSender() != null ? dto.getSender() : "bauschlosserei-kuhn@t-online.de";

            String messageId = emailService.sendEmailWithMultipleAttachments(
                    recipient,
                    null, // cc
                    sender,
                    dto.getSubject(),
                    htmlBody,
                    Map.of(), // keine Inline-CID-Bilder
                    attachmentPaths);

            // Email-Entität speichern
            Email email = new Email();
            email.setMessageId(messageId);
            email.setFromAddress(sender);
            email.setRecipient(recipient);
            email.setSubject(dto.getSubject());
            email.setBody(org.example.kalkulationsprogramm.util.EmailHtmlSanitizer.htmlToPlainText(htmlBody));
            email.setHtmlBody(htmlBody);
            email.setRawBody(htmlBody);
            email.setSentAt(LocalDateTime.now());
            email.setDirection(EmailDirection.OUT);
            email.setParentEmail(parentEmail); // Verknüpfung zur Original-Email

            // Zuordnung: Priorität DTO > Parent
            if (dto.getProjektId() != null) {
                projektRepository.findById(dto.getProjektId()).ifPresent(email::assignToProjekt);
            } else if (dto.getAnfrageId() != null) {
                anfrageRepository.findById(dto.getAnfrageId()).ifPresent(email::assignToAnfrage);
            } else if (dto.getLieferantId() != null) {
                lieferantenRepository.findById(dto.getLieferantId()).ifPresent(email::assignToLieferant);
            } else {
                // Fallback: Parent
                if (parentEmail.getProjekt() != null) {
                    email.assignToProjekt(parentEmail.getProjekt());
                } else if (parentEmail.getAnfrage() != null) {
                    email.assignToAnfrage(parentEmail.getAnfrage());
                } else if (parentEmail.getLieferant() != null) {
                    email.assignToLieferant(parentEmail.getLieferant());
                }
            }

            emailRepository.save(email);

            // Implizites Ham-Training: Wer antwortet, bestätigt, dass die Email kein Spam ist
            trainImplicitHam(parentEmail);
            emailRepository.save(parentEmail);

            // Attachments speichern
            if (attachments != null) {
                java.nio.file.Path baseDir = Path.of(mailAttachmentDir);
                java.nio.file.Files.createDirectories(baseDir);

                for (int i = 0; i < attachments.length; i++) {
                    MultipartFile file = attachments[i];
                    if (!file.isEmpty()) {
                        String safeOrigName = file.getOriginalFilename() != null
                                ? file.getOriginalFilename().replaceAll("[\\\\/:*?\"<>|]", "_")
                                : "attachment";
                        String storedName = java.util.UUID.randomUUID() + "_" + safeOrigName;
                        java.nio.file.Path dst = baseDir.resolve(storedName);
                        java.nio.file.Files.copy(tempFiles.get(i).toPath(), dst,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                        EmailAttachment att = new EmailAttachment();
                        att.setEmail(email);
                        att.setOriginalFilename(file.getOriginalFilename());
                        att.setStoredFilename(storedName);
                        att.setSizeBytes(file.getSize());
                        att.setMimeType(file.getContentType());
                        email.addAttachment(att);
                    }
                }
                emailRepository.save(email);
            }

            // Temp files aufräumen
            for (java.io.File f : tempFiles) {
                try {
                    f.delete();
                } catch (Exception ignored) {
                }
            }

            return ResponseEntity.ok(toDto(email));
        } catch (Exception e) {
            log.error("Fehler beim Senden der Antwort-Email", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MAPPER
    // ═══════════════════════════════════════════════════════════════

    /**
     * Lightweight DTO for list endpoints – excludes htmlBody and trims body to 200 chars.
     * Skips expensive CID-rewriting; only returns attachment metadata.
     */
    private UnifiedEmailDto toListDto(Email email) {
        UnifiedEmailDto dto = new UnifiedEmailDto();
        dto.setId(email.getId());
        dto.setFromAddress(email.getFromAddress());
        dto.setRecipient(email.getRecipient());
        dto.setSubject(email.getSubject());

        // Trim body for preview (list only needs ~200 chars)
        String body = email.getBody();
        if (body != null && body.length() > 200) {
            body = body.substring(0, 200);
        }
        dto.setBody(body);
        // htmlBody deliberately omitted for list performance

        dto.setSentAt(email.getSentAt());
        dto.setRead(email.isRead());
        dto.setDirection(email.getDirection() != null ? email.getDirection().name() : null);
        dto.setZuordnungTyp(email.getZuordnungTyp() != null ? email.getZuordnungTyp().name() : null);
        dto.setSpamScore(email.getSpamScore());

        // Zuordnungs-Info
        if (email.getProjekt() != null) {
            dto.setProjektId(email.getProjekt().getId());
            dto.setProjektName(email.getProjekt().getBauvorhaben());
        }
        if (email.getAnfrage() != null) {
            dto.setAnfrageId(email.getAnfrage().getId());
            dto.setAnfrageName(email.getAnfrage().getBauvorhaben());
        }
        if (email.getLieferant() != null) {
            dto.setLieferantId(email.getLieferant().getId());
            dto.setLieferantName(email.getLieferant().getLieferantenname());
        }

        // Thread
        if (email.getParentEmail() != null) {
            dto.setParentEmailId(email.getParentEmail().getId());
        }
        dto.setReplyCount(countAncestors(email) + countAllReplies(email));

        // Attachments – metadata only, no CID rewriting
        dto.setHasAttachments(email.getAttachments() != null && !email.getAttachments().isEmpty());
        if (email.getAttachments() != null && !email.getAttachments().isEmpty()) {
            dto.setAttachments(email.getAttachments().stream().map(att -> {
                UnifiedEmailDto.AttachmentDto attDto = new UnifiedEmailDto.AttachmentDto();
                attDto.setId(att.getId());
                attDto.setOriginalFilename(att.getOriginalFilename());
                attDto.setMimeType(att.getMimeType());
                attDto.setFileSize(att.getSizeBytes());
                attDto.setContentId(att.getContentId());
                attDto.setInline(Boolean.TRUE.equals(att.getInlineAttachment()));
                return attDto;
            }).collect(Collectors.toList()));
        }

        return dto;
    }

    /** Full DTO with htmlBody + CID-rewriting – for detail/thread views only. */
    private UnifiedEmailDto toDto(Email email) {
        UnifiedEmailDto dto = new UnifiedEmailDto();
        dto.setId(email.getId());
        dto.setMessageId(email.getMessageId());
        dto.setFromAddress(email.getFromAddress());
        dto.setSenderDomain(email.getSenderDomain());
        dto.setRecipient(email.getRecipient());
        dto.setCc(email.getCc());
        dto.setSubject(email.getSubject());
        dto.setBody(email.getBody());
        dto.setSentAt(email.getSentAt());
        dto.setFirstViewedAt(email.getFirstViewedAt());
        dto.setRead(email.isRead()); // Use actual isRead field from entity
        dto.setDirection(email.getDirection() != null ? email.getDirection().name() : null);
        dto.setZuordnungTyp(email.getZuordnungTyp() != null ? email.getZuordnungTyp().name() : null);
        dto.setSpamScore(email.getSpamScore());

        // Zuordnungs-Info
        if (email.getProjekt() != null) {
            dto.setProjektId(email.getProjekt().getId());
            dto.setProjektName(email.getProjekt().getBauvorhaben());
        }
        if (email.getAnfrage() != null) {
            dto.setAnfrageId(email.getAnfrage().getId());
            dto.setAnfrageName(email.getAnfrage().getBauvorhaben());
        }
        if (email.getLieferant() != null) {
            dto.setLieferantId(email.getLieferant().getId());
            dto.setLieferantName(email.getLieferant().getLieferantenname());
        }

        // Thread-Informationen
        if (email.getParentEmail() != null) {
            dto.setParentEmailId(email.getParentEmail().getId());
        }
        dto.setReplyCount(countAncestors(email) + countAllReplies(email));

        // Attachments
        dto.setHasAttachments(email.getAttachments() != null && !email.getAttachments().isEmpty());
        if (email.getAttachments() != null && !email.getAttachments().isEmpty()) {
            dto.setAttachments(email.getAttachments().stream().map(att -> {
                UnifiedEmailDto.AttachmentDto attDto = new UnifiedEmailDto.AttachmentDto();
                attDto.setId(att.getId());
                attDto.setOriginalFilename(att.getOriginalFilename());
                attDto.setMimeType(att.getMimeType());
                attDto.setFileSize(att.getSizeBytes());
                attDto.setContentId(att.getContentId());
                attDto.setInline(Boolean.TRUE.equals(att.getInlineAttachment()));
                return attDto;
            }).collect(Collectors.toList()));

            // Rewrite cid: URLs to actual download URLs for inline images
            String htmlBody = email.getHtmlBody();
            if (htmlBody != null && !htmlBody.isBlank()) {
                final Long emailId = email.getId();
                htmlBody = org.example.kalkulationsprogramm.util.InlineAttachmentUtil.rewriteCidSources(
                        htmlBody,
                        email.getAttachments(),
                        att -> Boolean.TRUE.equals(att.getInlineAttachment())
                                || (att.getContentId() != null && !att.getContentId().isBlank()),
                        EmailAttachment::getContentId,
                        att -> "/api/emails/" + emailId + "/attachments/" + att.getId());
            }
            dto.setHtmlBody(htmlBody);
        } else {
            // Keine Attachments - htmlBody direkt setzen
            dto.setHtmlBody(email.getHtmlBody());
        }

        return dto;
    }

    /** Zählt rekursiv alle Nachfolger-Emails (Kinder, Kindeskinder, …). */
    private int countAllReplies(Email email) {
        if (email.getReplies() == null || email.getReplies().isEmpty()) return 0;
        int count = email.getReplies().size();
        for (org.example.kalkulationsprogramm.domain.Email reply : email.getReplies()) {
            count += countAllReplies(reply);
        }
        return count;
    }

    /** Zählt alle Vorfahren (Eltern, Großeltern, …) bis zur Thread-Wurzel. */
    private int countAncestors(Email email) {
        int count = 0;
        java.util.Set<Long> visited = new java.util.HashSet<>();
        Email current = email.getParentEmail();
        while (current != null && !visited.contains(current.getId())) {
            visited.add(current.getId());
            count++;
            current = current.getParentEmail();
        }
        return count;
    }
}
