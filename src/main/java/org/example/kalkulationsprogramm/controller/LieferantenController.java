package org.example.kalkulationsprogramm.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantNotiz;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantArtikelpreisCreateRequest;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantArtikelpreisDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantArtikelpreisPageDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantArtikelpreisUpdateRequest;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantCreateRequestDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantDetailDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantEmailDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantListItemDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantNotizDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantSearchResponseDto;
import org.example.kalkulationsprogramm.dto.Lieferant.LieferantUpdateRequestDto;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.dto.Projekt.ProjektEmailDto;
import org.example.kalkulationsprogramm.mapper.LieferantMapper;
import org.example.kalkulationsprogramm.repository.LieferantNotizRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.LieferantArtikelpreisService;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.example.kalkulationsprogramm.service.LieferantEmailResolver;
import org.example.kalkulationsprogramm.service.LieferantenDetailService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.ListJoin;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/lieferanten")
@RequiredArgsConstructor
public class LieferantenController {

    private final LieferantenRepository lieferantenRepository;
    private final MitarbeiterRepository mitarbeiterRepository;
    private final org.example.kalkulationsprogramm.repository.KostenstelleRepository kostenstelleRepository;
    private final org.example.kalkulationsprogramm.service.LieferantStandardKostenstelleAutoAssigner standardKostenstelleAutoAssigner;
    private final LieferantMapper lieferantMapper;
    private final LieferantEmailResolver lieferantEmailResolver;
    private final LieferantenDetailService lieferantenDetailService;
    private final LieferantArtikelpreisService artikelpreisService;
    private final LieferantDokumentService dokumentService;
    private final org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService emailAttachmentProcessingService;
    private final org.example.kalkulationsprogramm.repository.LieferantDokumentRepository lieferantDokumentRepository;
    private final org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    private final org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService geminiService;
    private final LieferantNotizRepository notizRepository;

    // New dependencies for Email Sending
    private final org.example.kalkulationsprogramm.repository.EmailRepository emailRepository;
    private final org.example.kalkulationsprogramm.service.FrontendUserProfileService frontendUserProfileService;
    private final org.example.kalkulationsprogramm.service.EmailSignatureService emailSignatureService;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Value("${smtp.host}")
    private String smtpHost;
    @org.springframework.beans.factory.annotation.Value("${smtp.port}")
    private int smtpPort;
    @org.springframework.beans.factory.annotation.Value("${smtp.username}")
    private String smtpUsername;
    @org.springframework.beans.factory.annotation.Value("${smtp.password}")
    private String smtpPassword;
    @org.springframework.beans.factory.annotation.Value("${file.mail-attachment-dir}")
    private String mailAttachmentDir;

    private static final int MAX_PAGE_SIZE = 1000;
    private static final Map<String, String> ARTIKELPREIS_SORT_FIELDS = Map.of(
            "produktname", "artikel.produktname",
            "werkstoff", "artikel.werkstoff.name",
            "externeArtikelnummer", "externeArtikelnummer",
            "preis", "preis",
            "preisDatum", "preisAenderungsdatum");

    @GetMapping
    public LieferantSearchResponseDto sucheLieferanten(@RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "typ", required = false) String typ,
            @RequestParam(value = "ort", required = false) String ort,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        int pageIndex = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Specification<Lieferanten> specs = Specification.where(null);
        if (StringUtils.hasText(query)) {
            final String likeValue = wrapLike(query).toLowerCase(Locale.ROOT);
            specs = specs.and((root, cq, cb) -> {
                cq.distinct(true);
                return cb.or(
                        cb.like(cb.lower(root.get("lieferantenname")), likeValue),
                        cb.like(cb.lower(root.get("lieferantenTyp")), likeValue),
                        cb.like(cb.lower(root.get("vertreter")), likeValue),
                        cb.like(cb.lower(root.get("ort")), likeValue),
                        cb.like(cb.lower(root.get("strasse")), likeValue));
            });
        }
        specs = specs.and(buildSpec("lieferantenname", name));
        specs = specs.and(buildSpec("lieferantenTyp", typ));
        specs = specs.and(buildSpec("ort", ort));
        if (StringUtils.hasText(email)) {
            final String likeValue = wrapLike(email).toLowerCase(Locale.ROOT);
            specs = specs.and((root, cq, cb) -> {
                cq.distinct(true);
                ListJoin<Lieferanten, String> join = root.joinList("kundenEmails", JoinType.LEFT);
                return cb.like(cb.lower(join), likeValue);
            });
        }

        Page<Lieferanten> result = lieferantenRepository.findAll(
                specs,
                PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Order.asc("lieferantenname").ignoreCase())));

        LieferantSearchResponseDto response = new LieferantSearchResponseDto();
        response.setLieferanten(result.stream().map(lieferantMapper::toListItem).toList());
        response.setGesamt(result.getTotalElements());
        response.setSeite(pageIndex);
        response.setSeitenGroesse(pageSize);
        return response;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public LieferantListItemDto createLieferant(@Valid @RequestBody LieferantCreateRequestDto request) {
        final String name = request.getLieferantenname().trim();
        lieferantenRepository.findByLieferantennameIgnoreCase(name)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Lieferant existiert bereits.");
                });

        Lieferanten lieferant = new Lieferanten();
        lieferant.setLieferantenname(name);
        lieferant.setEigeneKundennummer(trimToNull(request.getEigeneKundennummer()));
        lieferant.setLieferantenTyp(trimToNull(request.getLieferantenTyp()));
        lieferant.setVertreter(trimToNull(request.getVertreter()));
        lieferant.setStrasse(trimToNull(request.getStrasse()));
        lieferant.setPlz(trimToNull(request.getPlz()));
        lieferant.setOrt(trimToNull(request.getOrt()));
        lieferant.setTelefon(trimToNull(request.getTelefon()));
        lieferant.setMobiltelefon(trimToNull(request.getMobiltelefon()));
        lieferant.setIstAktiv(request.getIstAktiv() != null ? request.getIstAktiv() : Boolean.TRUE);
        lieferant.setStartZusammenarbeit(toDate(request.getStartZusammenarbeit()));
        lieferant.setKundenEmails(new ArrayList<>(normalizeEmails(request.getKundenEmails())));
        lieferant.setStandardKostenstelle(resolveKostenstelle(request.getStandardKostenstelleId()));

        Lieferanten saved = lieferantenRepository.save(lieferant);
        try {
            lieferantEmailResolver.refresh();
            // Trigger auto-backfill for retro-active assignment
            eventPublisher.publishEvent(org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.forNewEntity(
                    org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.EntityType.LIEFERANT,
                    saved.getId(),
                    saved.getKundenEmails()));
        } catch (Exception ignored) {
        }
        return lieferantMapper.toListItem(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<LieferantDetailDto> updateLieferant(@PathVariable Long id,
            @Valid @RequestBody LieferantUpdateRequestDto request) {
        Lieferanten lieferant = lieferantenRepository.findById(id).orElse(null);
        if (lieferant == null) {
            return ResponseEntity.notFound().build();
        }

        final String name = request.getLieferantenname().trim();
        if (!lieferant.getLieferantenname().equalsIgnoreCase(name)) {
            lieferantenRepository.findByLieferantennameIgnoreCase(name)
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Lieferant existiert bereits.");
                    });
        }
        lieferant.setLieferantenname(name);
        lieferant.setEigeneKundennummer(trimToNull(request.getEigeneKundennummer()));
        lieferant.setLieferantenTyp(trimToNull(request.getLieferantenTyp()));
        lieferant.setVertreter(trimToNull(request.getVertreter()));
        lieferant.setStrasse(trimToNull(request.getStrasse()));
        lieferant.setPlz(trimToNull(request.getPlz()));
        lieferant.setOrt(trimToNull(request.getOrt()));
        lieferant.setTelefon(trimToNull(request.getTelefon()));
        lieferant.setMobiltelefon(trimToNull(request.getMobiltelefon()));
        lieferant.setIstAktiv(request.getIstAktiv() != null ? request.getIstAktiv() : Boolean.TRUE);
        lieferant.setStartZusammenarbeit(toDate(request.getStartZusammenarbeit()));
        lieferant.setKundenEmails(new ArrayList<>(normalizeEmails(request.getKundenEmails())));
        lieferant.setStandardKostenstelle(resolveKostenstelle(request.getStandardKostenstelleId()));

        lieferantenRepository.save(lieferant);
        try {
            lieferantEmailResolver.refresh();
            // Trigger auto-backfill for retro-active assignment
            eventPublisher
                    .publishEvent(org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.forAddressChange(
                            org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.EntityType.LIEFERANT,
                            lieferant.getId(),
                            lieferant.getKundenEmails(),
                            lieferant.getKundenEmails()));
        } catch (Exception ignored) {
        }

        LieferantDetailDto detail = lieferantenDetailService.loadDetails(id);
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LieferantDetailDto> getById(@PathVariable Long id) {
        LieferantDetailDto detail = lieferantenDetailService.loadDetails(id);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/{id}/artikelpreise")
    public ResponseEntity<LieferantArtikelpreisPageDto> listArtikelpreise(@PathVariable Long id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size,
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "sort", defaultValue = "preisDatum") String sort,
            @RequestParam(value = "dir", defaultValue = "desc") String direction) {
        int pageIndex = Math.max(page, 0);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String sortKey = ARTIKELPREIS_SORT_FIELDS.getOrDefault(sort, "preisAenderungsdatum");
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Page<LieferantArtikelpreisDto> result = artikelpreisService.suche(id, query,
                PageRequest.of(pageIndex, pageSize, Sort.by(new Sort.Order(sortDirection, sortKey))));
        LieferantArtikelpreisPageDto response = new LieferantArtikelpreisPageDto();
        response.setArtikelpreise(result.getContent());
        response.setGesamt(result.getTotalElements());
        response.setSeite(result.getNumber());
        response.setSeitenGroesse(result.getSize());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/emails")
    public ResponseEntity<List<LieferantEmailDto>> listEmails(@PathVariable Long id,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            @RequestParam(value = "q", required = false) String query) {
        Lieferanten exists = lieferantenRepository.findById(id).orElse(null);
        if (exists == null) {
            return ResponseEntity.notFound().build();
        }
        List<LieferantEmailDto> emails = lieferantenDetailService.loadEmails(id, limit, query);
        return ResponseEntity.ok(emails);
    }

    @PostMapping(value = "/{id}/emails", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LieferantEmailDto> sendEmail(@PathVariable Long id,
            @RequestPart("dto") ProjektEmailDto dto,
            @RequestPart(value = "attachments", required = false) MultipartFile[] attachments) {
        Lieferanten lieferant = lieferantenRepository.findById(id).orElse(null);
        if (lieferant == null) {
            return ResponseEntity.notFound().build();
        }

        org.example.email.EmailService emailService = new org.example.email.EmailService(smtpHost, smtpPort,
                smtpUsername, smtpPassword);

        String htmlBody = dto.getBody() != null ? dto.getBody() : "";
        String userName = resolveUserName(dto.getBenutzer(), dto.getFrontendUserId());

        // Handle Signature
        java.util.Map<String, java.io.File> inline = new java.util.HashMap<>();
        try {
            var sigOpt = getSignatureForFrontendUser(dto.getFrontendUserId(), dto.getBenutzer());
            if (sigOpt.isPresent()) {
                htmlBody = emailSignatureService.ensureSignaturePresentOnce(htmlBody, sigOpt.get(), userName);
                inline = emailSignatureService.buildInlineCidFileMap(sigOpt.get());
            }
        } catch (Exception e) {
            // Ignore signature errors
        }

        // Handle Attachments (Send & Save)
        List<java.io.File> tempFiles = new ArrayList<>();
        try {
            // 1. Send Email
            // Note: Our EmailService might need adaptation for attachments.
            // For now assuming we can send WITHOUT attachments in this step OR we use a
            // version that supports it.
            // The logic here is simplified: we save first, then send? Or send then save?
            // Sending with attachments from MultipartFile requires streaming or temp files.

            // For saving to unified storage:
            org.example.kalkulationsprogramm.domain.Email emailContext = new org.example.kalkulationsprogramm.domain.Email();
            emailContext.assignToLieferant(lieferant);
            emailContext.setFromAddress(dto.getSender());
            emailContext.setRecipient(String.join(",", dto.getRecipients()));
            emailContext.setSubject(dto.getSubject());
            emailContext.setHtmlBody(htmlBody);
            emailContext.setRawBody(htmlBody); // Plain version?
            emailContext.setBody(org.example.kalkulationsprogramm.util.EmailHtmlSanitizer
                    .htmlToPlainText(htmlBody));
            emailContext.setSentAt(java.time.LocalDateTime.now());
            emailContext.setDirection(org.example.kalkulationsprogramm.domain.EmailDirection.OUT);
            emailContext = emailRepository.save(emailContext); // Save to get ID

            // Save Attachments
            if (attachments != null) {
                java.nio.file.Path dstDir = Path.of(mailAttachmentDir).toAbsolutePath().normalize()
                        .resolve("attachments").resolve(String.valueOf(emailContext.getId()));
                java.nio.file.Files.createDirectories(dstDir);

                for (MultipartFile mpf : attachments) {
                    if (mpf.isEmpty())
                        continue;
                    String original = mpf.getOriginalFilename();
                    String storedName = java.util.UUID.randomUUID() + "_" + original;
                    java.nio.file.Path dst = dstDir.resolve(storedName);
                    mpf.transferTo(dst);

                    var att = new org.example.kalkulationsprogramm.domain.EmailAttachment();
                    att.setEmail(emailContext);
                    att.setOriginalFilename(original);
                    att.setStoredFilename(storedName);
                    att.setSizeBytes(mpf.getSize());
                    att.setMimeType(mpf.getContentType());

                    emailContext.addAttachment(att);

                    // Add to send list
                    tempFiles.add(dst.toFile());
                }
                emailContext = emailRepository.save(emailContext);
            }

            // Actually Send
            String recipients = String.join(",", dto.getRecipients());
            List<String> attachmentPaths = tempFiles.stream().map(java.io.File::getAbsolutePath).toList();

            String msgId = emailService.sendEmailWithMultipleAttachments(
                    recipients,
                    null,
                    dto.getSender(),
                    dto.getSubject(),
                    htmlBody,
                    inline,
                    attachmentPaths);

            emailContext.setMessageId(msgId);
            emailRepository.save(emailContext);

            // Return DTO (mapped)
            LieferantEmailDto ret = new LieferantEmailDto();
            ret.setId(emailContext.getId());
            ret.setSubject(emailContext.getSubject());
            ret.setFrom(emailContext.getFromAddress());
            ret.setTo(emailContext.getRecipient());
            ret.setSentAt(emailContext.getSentAt());
            ret.setDirection(org.example.kalkulationsprogramm.domain.EmailDirection.OUT);
            return ResponseEntity.ok(ret);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    private Optional<EmailSignature> getSignatureForFrontendUser(Long frontendUserId, String displayName) {
        try {
            return emailSignatureService.getDefaultForFrontendUser(frontendUserId, displayName);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String resolveUserName(String providedName, Long frontendUserId) {
        if (providedName != null && !providedName.isBlank()) {
            return providedName;
        }
        return frontendUserProfileService.findById(frontendUserId)
                .map(FrontendUserProfile::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(null);
    }

    @PutMapping("/{id}/artikelpreise/{artikelId}")
    public ResponseEntity<LieferantArtikelpreisDto> updateArtikelpreis(@PathVariable Long id,
            @PathVariable Long artikelId,
            @Valid @RequestBody LieferantArtikelpreisUpdateRequest request) {
        return artikelpreisService.aktualisiere(id, artikelId, request.preis(), request.externeArtikelnummer())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/artikelpreise")
    public ResponseEntity<LieferantArtikelpreisDto> createArtikelpreis(@PathVariable Long id,
            @Valid @RequestBody LieferantArtikelpreisCreateRequest request) {
        return artikelpreisService.anlegen(id, request.artikelId(), request.preis(), request.externeArtikelnummer())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/emails")
    public List<String> getAllEmails() {
        return lieferantenRepository.findAll()
                .stream()
                .flatMap(l -> l.getKundenEmails().stream())
                .distinct()
                .toList();
    }

    /**
     * Fügt eine einzelne E-Mail-Adresse zu den Lieferanten-E-Mails hinzu.
     */
    @PostMapping(value = "/{id}/emails", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Map<String, Object>> addLieferantEmail(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (!StringUtils.hasText(email)) {
            return ResponseEntity.badRequest().body(Map.of("error", "E-Mail-Adresse fehlt"));
        }

        Lieferanten lieferant = lieferantenRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lieferant nicht gefunden"));

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (lieferant.getKundenEmails() == null) {
            lieferant.setKundenEmails(new ArrayList<>());
        }
        if (lieferant.getKundenEmails().contains(normalized)) {
            return ResponseEntity.ok(Map.of("message", "E-Mail-Adresse bereits vorhanden", "added", false));
        }

        lieferant.getKundenEmails().add(normalized);
        lieferantenRepository.save(lieferant);

        try {
            lieferantEmailResolver.refresh();
            eventPublisher.publishEvent(org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.forAddressChange(
                    org.example.kalkulationsprogramm.event.EmailAddressChangedEvent.EntityType.LIEFERANT,
                    lieferant.getId(),
                    List.of(normalized),
                    new ArrayList<>(lieferant.getKundenEmails())));
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(Map.of("message", "E-Mail-Adresse gespeichert", "added", true));
    }

    private Specification<Lieferanten> buildSpec(String field, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        final String likeValue = wrapLike(value).toLowerCase(Locale.ROOT);
        return (root, cq, cb) -> cb.like(cb.lower(root.get(field)), likeValue);
    }

    private String wrapLike(String value) {
        return "%" + value.trim() + "%";
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Date toDate(LocalDate value) {
        if (value == null) {
            return null;
        }
        return Date.from(value.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private org.example.kalkulationsprogramm.domain.Kostenstelle resolveKostenstelle(Long id) {
        if (id == null) {
            return null;
        }
        return kostenstelleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Kostenstelle mit ID " + id + " nicht gefunden."));
    }

    private List<String> normalizeEmails(List<String> emails) {
        if (emails == null) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String email : emails) {
            if (!StringUtils.hasText(email)) {
                continue;
            }
            normalized.add(email.trim().toLowerCase(Locale.ROOT));
        }
        return new ArrayList<>(normalized);
    }

    // ==================== ATTACHMENT REPROCESSING ====================

    /**
     * Reprocessiert alle Attachments eines Lieferanten.
     * Setzt aiProcessed Flag zurück und verarbeitet erneut.
     */
    @PostMapping("/{id}/reprocess-attachments")
    // KEIN @Transactional - jeder innere saveAndFlush soll sofort committen
    public ResponseEntity<Map<String, Object>> reprocessAttachments(@PathVariable Long id) {
        Lieferanten lieferant = lieferantenRepository.findById(id).orElse(null);
        if (lieferant == null) {
            return ResponseEntity.notFound().build();
        }

        // Finde alle Emails des Lieferanten
        List<org.example.kalkulationsprogramm.domain.Email> emails = emailRepository
                .findByLieferantIdOrderBySentAtDesc(id);

        int totalAttachments = 0;
        int processed = 0;

        for (var email : emails) {
            for (var attachment : email.getAttachments()) {
                // Nur PDFs verarbeiten - ZUGFeRD-XML ist in PDFs eingebettet
                if (attachment.getOriginalFilename() != null &&
                        attachment.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
                    totalAttachments++;

                    // Flag zurücksetzen
                    attachment.setAiProcessed(false);
                    attachment.setAiProcessedAt(null);
                }
            }
        }

        // Trigger Reprocessing via Event
        if (totalAttachments > 0) {
            for (var email : emails) {
                try {
                    processed += emailAttachmentProcessingService.processLieferantAttachments(email);
                } catch (Exception e) {
                    // Log and continue
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "lieferantId", id,
                "totalAttachments", totalAttachments,
                "processed", processed));
    }

    /**
     * Führt nachträgliche Verknüpfung von Dokumenten durch.
     * Nutzt existierende geschaeftsdaten - keine KI-Analyse.
     */
    @PostMapping("/{id}/relink-dokumente")
    @Transactional
    public ResponseEntity<Map<String, Object>> relinkDokumente(@PathVariable Long id) {
        Lieferanten lieferant = lieferantenRepository.findById(id).orElse(null);
        if (lieferant == null) {
            return ResponseEntity.notFound().build();
        }

        // Hole alle Dokumente des Lieferanten die geschaeftsdaten haben
        List<org.example.kalkulationsprogramm.domain.LieferantDokument> dokumente = lieferantDokumentRepository
                .findByLieferantIdOrderByUploadDatumDesc(id);

        int totalMitGeschaeftsdaten = 0;
        int verknuepftCount = 0;

        for (var dokument : dokumente) {
            if (dokument.getGeschaeftsdaten() != null) {
                totalMitGeschaeftsdaten++;
                int vorVar = dokument.getVerknuepfteDokumente().size();

                // Nutze zentralen Service für Verknüpfung (inkl. Gutschrift & Fallback-Logik)
                geminiService.performRelink(dokument, dokumente);

                int nachVar = dokument.getVerknuepfteDokumente().size();
                verknuepftCount += (nachVar - vorVar);
            }
        }

        return ResponseEntity.ok(Map.of(
                "lieferantId", id,
                "totalMitGeschaeftsdaten", totalMitGeschaeftsdaten,
                "neuVerknuepft", verknuepftCount));
    }

    /**
     * Führt nachträgliche Verknüpfung für ALLE Lieferanten durch.
     */
    @PostMapping("/relink-all")
    @Transactional
    public ResponseEntity<Map<String, Object>> relinkAllDokumente() {
        List<Lieferanten> alleLieferanten = lieferantenRepository.findAll();
        int totalLieferanten = 0;
        int totalVerknuepft = 0;

        for (Lieferanten lieferant : alleLieferanten) {
            List<org.example.kalkulationsprogramm.domain.LieferantDokument> dokumente = lieferantDokumentRepository
                    .findByLieferantIdOrderByUploadDatumDesc(lieferant.getId());

            if (dokumente.isEmpty())
                continue;
            totalLieferanten++;

            int lieferantVerknuepft = 0;

            for (var dokument : dokumente) {
                if (dokument.getGeschaeftsdaten() != null) {
                    int vorVar = dokument.getVerknuepfteDokumente().size();

                    // Nutze zentralen Service
                    geminiService.performRelink(dokument, dokumente);

                    int nachVar = dokument.getVerknuepfteDokumente().size();
                    lieferantVerknuepft += (nachVar - vorVar);
                }
            }

            totalVerknuepft += lieferantVerknuepft;
        }

        return ResponseEntity.ok(Map.of(
                "totalLieferanten", totalLieferanten,
                "totalVerknuepft", totalVerknuepft));
    }

    // ==================== DOKUMENT ENDPOINTS ====================

    /**
     * Lädt Dokumente eines Lieferanten.
     * Token ist optional - wenn nicht vorhanden, werden alle Dokumente geladen.
     */
    @GetMapping("/{id}/dokumente")
    public ResponseEntity<List<LieferantDokumentDto.Response>> listDokumente(
            @PathVariable Long id,
            @RequestParam(value = "typ", required = false) LieferantDokumentTyp typ,
            @RequestParam(value = "token", required = false) String token) {
        // Prüfe ob Lieferant existiert
        if (!lieferantenRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        // Wenn Token vorhanden, filtere nach Berechtigungen
        if (StringUtils.hasText(token)) {
            var mitarbeiter = mitarbeiterByToken(token);
            if (mitarbeiter != null) {
                var dokumente = dokumentService.getDokumenteFiltered(id, mitarbeiter.getId(), typ);
                return ResponseEntity.ok(dokumente);
            }
        }

        // Ohne Token: Alle Dokumente des Lieferanten laden
        var dokumente = dokumentService.getDokumenteByLieferant(id, typ);
        return ResponseEntity.ok(dokumente);
    }

    /**
     * Lädt ein neues Dokument hoch.
     */
    @PostMapping(value = "/{id}/dokumente", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LieferantDokumentDto.Response> uploadDokument(
            @PathVariable Long id,
            @RequestPart("datei") MultipartFile datei,
            @RequestPart("dto") LieferantDokumentDto.UploadRequest request,
            @RequestParam("token") String token,
            @RequestParam(value = "useProModel", defaultValue = "false") boolean useProModel) throws IOException {
        var mitarbeiter = mitarbeiterByToken(token);
        if (mitarbeiter == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            // Die Analyse erfolgt jetzt SYNCHRON im Service
            var result = dokumentService.uploadDokument(id, datei, request, mitarbeiter.getId(), useProModel);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // ==================== MANUELLER DOKUMENT-IMPORTER ====================

    /**
     * Analysiert ein Dokument und gibt die extrahierten Metadaten zurück.
     * Wird für den manuellen Dokument-Importer verwendet (Schritt 1: Upload &
     * Analyse).
     * Das Dokument wird temporär analysiert aber NICHT gespeichert.
     * Bei Multi-Invoice-PDFs (z.B. Amazon) werden alle erkannten Rechnungen
     * zurückgegeben.
     */
    @PostMapping(value = "/{id}/dokumente/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<java.util.List<LieferantDokumentDto.MultiInvoiceAnalyzeResponse>> analyzeDokument(
            @PathVariable Long id,
            @RequestPart("datei") MultipartFile datei) {

        // Prüfe ob Lieferant existiert
        if (!lieferantenRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        try {
            // Temporär speichern für Analyse
            String originalFilename = org.springframework.util.StringUtils.cleanPath(
                    java.util.Objects.requireNonNull(datei.getOriginalFilename()));
            if (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger Dateiname");
            }
            java.nio.file.Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
            String tempFilename = java.util.UUID.randomUUID() + "_" + originalFilename;
            java.nio.file.Path tempPath = tempDir.resolve(tempFilename);

            datei.transferTo(tempPath);

            try {
                // Multi-Invoice-Analyse durchführen
                var results = geminiService.analyzeFileForMultipleInvoices(tempPath, originalFilename);

                if (results.isEmpty()) {
                    // Fallback: Leere Antwort mit Defaults
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
                // Temp-Datei aufräumen
                java.nio.file.Files.deleteIfExists(tempPath);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Importiert ein Dokument mit den vom Benutzer bearbeiteten Metadaten.
     * Wird für den manuellen Dokument-Importer verwendet (Schritt 2: Speichern).
     */
    @PostMapping(value = "/{id}/dokumente/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> importDokument(
            @PathVariable Long id,
            @RequestPart("datei") MultipartFile datei,
            @RequestPart("metadata") LieferantDokumentDto.ImportRequest request,
            @RequestParam(value = "token", required = false) String token) {

        Lieferanten lieferant = lieferantenRepository.findById(id).orElse(null);
        if (lieferant == null) {
            return ResponseEntity.notFound().build();
        }

        // Duplikat-Prüfung: Dokumentnummer muss pro Lieferant eindeutig sein
        if (StringUtils.hasText(request.getDokumentNummer())) {
            boolean exists = geschaeftsdokumentRepository.existsByLieferantIdAndDokumentNummer(
                    id, request.getDokumentNummer());
            if (exists) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Ein Dokument mit der Nummer '" +
                                request.getDokumentNummer() + "' existiert bereits für diesen Lieferanten."));
            }
        }

        try {
            // 1. Datei speichern
            String originalFilename = org.springframework.util.StringUtils.cleanPath(
                    java.util.Objects.requireNonNull(datei.getOriginalFilename()));
            String storedFilename = java.util.UUID.randomUUID() + "_" + originalFilename;

            java.nio.file.Path lieferantDir = Path.of("uploads", "lieferanten", id.toString());
            java.nio.file.Files.createDirectories(lieferantDir);
            java.nio.file.Path targetPath = lieferantDir.resolve(storedFilename);
            datei.transferTo(targetPath);

            // 2. LieferantDokument erstellen
            var dokument = new org.example.kalkulationsprogramm.domain.LieferantDokument();
            dokument.setLieferant(lieferant);
            dokument.setTyp(
                    request.getDokumentTyp() != null ? request.getDokumentTyp() : LieferantDokumentTyp.RECHNUNG);
            dokument.setOriginalDateiname(originalFilename);
            dokument.setGespeicherterDateiname(storedFilename);
            dokument.setUploadDatum(java.time.LocalDateTime.now());

            // Uploader setzen falls Token vorhanden
            if (token != null) {
                var mitarbeiter = mitarbeiterByToken(token);
                if (mitarbeiter != null) {
                    dokument.setUploadedBy(mitarbeiter);
                }
            }

            dokument = lieferantDokumentRepository.save(dokument);

            // 3. Geschäftsdaten erstellen mit den benutzer-bearbeiteten Werten
            var geschaeftsdaten = new org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument();
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
            geschaeftsdaten.setAiConfidence(1.0); // Benutzer-verifiziert = 100%
            geschaeftsdaten.setAnalysiertAm(java.time.LocalDateTime.now());

            // Geschäftsdaten speichern (mit @MapsId muss zuerst das Child gespeichert
            // werden)
            geschaeftsdaten = geschaeftsdokumentRepository.saveAndFlush(geschaeftsdaten);
            dokument.setGeschaeftsdaten(geschaeftsdaten);
            dokument = lieferantDokumentRepository.save(dokument);

            // 4. Automatische Verknüpfung versuchen
            try {
                geminiService.performRelink(dokument);
            } catch (Exception e) {
                // Log but don't fail
            }

            // 5. Auto-Zuweisung der Standard-Kostenstelle (falls beim Lieferanten hinterlegt)
            try {
                standardKostenstelleAutoAssigner.applyIfApplicable(dokument);
            } catch (Exception e) {
                // Log but don't fail
            }

            // 6. DTO zurückgeben
            return ResponseEntity.ok(dokumentService.getDokumentById(dokument.getId()));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Verknüpft ein Dokument mit anderen Dokumenten (Dokumentenkette).
     */
    @PostMapping("/{lieferantId}/dokumente/{dokumentId}/verknuepfen")
    public ResponseEntity<LieferantDokumentDto.Response> addVerknuepfungen(
            @PathVariable Long lieferantId,
            @PathVariable Long dokumentId,
            @RequestBody Set<Long> verknuepfteIds) {
        try {
            var result = dokumentService.addVerknuepfungen(dokumentId, verknuepfteIds);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Gibt die Berechtigungen des Mitarbeiters für Lieferanten-Dokumente zurück.
     */
    @GetMapping("/berechtigungen")
    public ResponseEntity<LieferantDokumentDto.BerechtigungenResponse> getBerechtigungen(
            @RequestParam("token") String token) {
        var mitarbeiter = mitarbeiterByToken(token);
        if (mitarbeiter == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        var berechtigungen = dokumentService.getBerechtigungen(mitarbeiter.getId());
        return ResponseEntity.ok(berechtigungen);
    }

    private org.example.kalkulationsprogramm.domain.Mitarbeiter mitarbeiterByToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        return mitarbeiterRepository.findByLoginToken(token).orElse(null);
    }

    /**
     * Download eines Lieferanten-Dokuments (für manuell hochgeladene Dateien).
     */
    @GetMapping("/{lieferantId}/dokumente/{dokumentId}/download")
    public ResponseEntity<byte[]> downloadDokument(
            @PathVariable Long lieferantId,
            @PathVariable Long dokumentId) {
        var dokument = dokumentService.findById(dokumentId);
        if (dokument == null) {
            return ResponseEntity.notFound().build();
        }

        // Datei finden
        java.nio.file.Path filePath = resolveDokumentPath(dokument, lieferantId);
        if (filePath == null || !java.nio.file.Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(filePath);
            String filename = dokument.getEffektiverDateiname();
            String contentType = java.nio.file.Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + filename + "\"")
                    .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, contentType)
                    .body(bytes);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private java.nio.file.Path resolveDokumentPath(org.example.kalkulationsprogramm.domain.LieferantDokument dokument,
            Long lieferantId) {
        String dateiname = dokument.getEffektiverGespeicherterDateiname();
        if (dateiname == null) {
            return null;
        }

        String basePath = "uploads";

        // 1. Manuell hochgeladene Dokumente: uploads/lieferanten/{lieferantId}/
        java.nio.file.Path pfad = Path.of(basePath, "lieferanten", lieferantId.toString(), dateiname);
        if (java.nio.file.Files.exists(pfad)) {
            return pfad;
        }

        // 2. E-Mail-Attachments: uploads/attachments/lieferanten/{lieferantId}/
        pfad = Path.of(basePath, "attachments", "lieferanten", lieferantId.toString(), dateiname);
        if (java.nio.file.Files.exists(pfad)) {
            return pfad;
        }

        // 3. Vendor-Invoices: uploads/attachments/vendor-invoices/
        pfad = Path.of(basePath, "attachments", "vendor-invoices", dateiname);
        if (java.nio.file.Files.exists(pfad)) {
            return pfad;
        }

        // 4. Fallback: uploads/email/ (falls Datei dort liegt)
        pfad = Path.of(mailAttachmentDir, dateiname);
        if (java.nio.file.Files.exists(pfad)) {
            return pfad;
        }

        return null;
    }

    // ==================== NOTIZEN ENDPOINTS ====================

    @GetMapping("/{id}/notizen")
    public ResponseEntity<List<LieferantNotizDto>> listNotizen(
            @PathVariable Long id,
            @RequestParam(value = "q", required = false) String query) {
        if (!lieferantenRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<LieferantNotiz> notizen;
        if (StringUtils.hasText(query)) {
            notizen = notizRepository.findByLieferantIdAndTextContainingIgnoreCaseOrderByErstelltAmDesc(id, query);
        } else {
            notizen = notizRepository.findByLieferantIdOrderByErstelltAmDesc(id);
        }
        List<LieferantNotizDto> dtos = notizen.stream().map(n -> {
            LieferantNotizDto dto = new LieferantNotizDto();
            dto.setId(n.getId());
            dto.setText(n.getText());
            dto.setErstelltAm(n.getErstelltAm());
            return dto;
        }).toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/{id}/notizen")
    @Transactional
    public ResponseEntity<LieferantNotizDto> createNotiz(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Lieferanten lieferant = lieferantenRepository.findById(id).orElse(null);
        if (lieferant == null) {
            return ResponseEntity.notFound().build();
        }
        String text = body.get("text");
        if (!StringUtils.hasText(text)) {
            return ResponseEntity.badRequest().build();
        }
        LieferantNotiz notiz = new LieferantNotiz();
        notiz.setLieferant(lieferant);
        notiz.setText(text.trim());
        notiz = notizRepository.save(notiz);

        LieferantNotizDto dto = new LieferantNotizDto();
        dto.setId(notiz.getId());
        dto.setText(notiz.getText());
        dto.setErstelltAm(notiz.getErstelltAm());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{id}/notizen/{notizId}")
    @Transactional
    public ResponseEntity<Void> deleteNotiz(
            @PathVariable Long id,
            @PathVariable Long notizId) {
        if (!lieferantenRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        LieferantNotiz notiz = notizRepository.findById(notizId).orElse(null);
        if (notiz == null || !notiz.getLieferant().getId().equals(id)) {
            return ResponseEntity.notFound().build();
        }
        notizRepository.delete(notiz);
        return ResponseEntity.noContent().build();
    }

    // ==================== BILDER ENDPOINTS (Reklamationsbilder)
    // ====================

    private final org.example.kalkulationsprogramm.repository.LieferantBildRepository bildRepository;

    @GetMapping("/{id}/bilder")
    public ResponseEntity<List<LieferantBildDto>> listBilder(@PathVariable Long id) {
        if (!lieferantenRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<org.example.kalkulationsprogramm.domain.LieferantBild> bilder = bildRepository
                .findByLieferantIdOrderByErstelltAmDesc(id);
        List<LieferantBildDto> dtos = bilder.stream().map(b -> {
            LieferantBildDto dto = new LieferantBildDto();
            dto.id = b.getId();
            dto.originalDateiname = b.getOriginalDateiname();
            dto.url = "/api/lieferanten/bilder/file/" + b.getGespeicherterDateiname();
            dto.beschreibung = b.getBeschreibung();
            dto.erstelltAm = b.getErstelltAm();
            if (b.getHochgeladenVon() != null) {
                dto.mitarbeiterVorname = b.getHochgeladenVon().getVorname();
                dto.mitarbeiterNachname = b.getHochgeladenVon().getNachname();
            }
            return dto;
        }).toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/bilder/file/{dateiname:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getBildDatei(@PathVariable String dateiname) {
        var bildOpt = bildRepository.findByGespeicherterDateiname(dateiname);
        if (bildOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var bild = bildOpt.get();
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("uploads", "lieferanten",
                    bild.getLieferant().getId().toString(), "bilder", bild.getGespeicherterDateiname());

            if (!java.nio.file.Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }

            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(path.toUri());
            String contentType = java.nio.file.Files.probeContentType(path);
            if (contentType == null) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/{id}/bilder", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<LieferantBildDto> uploadBild(
            @PathVariable Long id,
            @RequestPart("datei") MultipartFile datei,
            @RequestPart(value = "beschreibung", required = false) String beschreibung,
            @RequestParam(value = "token", required = false) String token) {
        Lieferanten lieferant = lieferantenRepository.findById(id).orElse(null);
        if (lieferant == null) {
            return ResponseEntity.notFound().build();
        }

        // Mitarbeiter ermitteln
        var mitarbeiter = mitarbeiterByToken(token);
        if (mitarbeiter == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            // Datei speichern
            String originalFilename = org.springframework.util.StringUtils.cleanPath(
                    java.util.Objects.requireNonNull(datei.getOriginalFilename()));
            String storedFilename = java.util.UUID.randomUUID() + "_" + originalFilename;

            java.nio.file.Path lieferantDir = Path.of("uploads", "lieferanten", id.toString(),
                    "bilder");
            java.nio.file.Files.createDirectories(lieferantDir);
            java.nio.file.Path targetPath = lieferantDir.resolve(storedFilename);
            datei.transferTo(targetPath);

            // Bild-Entity erstellen
            var bild = new org.example.kalkulationsprogramm.domain.LieferantBild();
            bild.setLieferant(lieferant);
            bild.setOriginalDateiname(originalFilename);
            bild.setGespeicherterDateiname(storedFilename);
            bild.setBeschreibung(beschreibung);
            bild.setErstelltAm(java.time.LocalDateTime.now());
            bild.setHochgeladenVon(mitarbeiter);

            bild = bildRepository.save(bild);

            // Response erstellen
            LieferantBildDto dto = new LieferantBildDto();
            dto.id = bild.getId();
            dto.originalDateiname = bild.getOriginalDateiname();
            dto.url = "/api/lieferanten/bilder/file/" + bild.getGespeicherterDateiname();
            dto.beschreibung = bild.getBeschreibung();
            dto.erstelltAm = bild.getErstelltAm();
            dto.mitarbeiterVorname = mitarbeiter.getVorname();
            dto.mitarbeiterNachname = mitarbeiter.getNachname();

            return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{id}/bilder/{bildId}")
    @Transactional
    public ResponseEntity<Void> deleteBild(
            @PathVariable Long id,
            @PathVariable Long bildId) {
        if (!lieferantenRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        var bild = bildRepository.findById(bildId).orElse(null);
        if (bild == null || !bild.getLieferant().getId().equals(id)) {
            return ResponseEntity.notFound().build();
        }
        try {
            // Datei löschen
            java.nio.file.Path filePath = java.nio.file.Paths.get("uploads", "lieferanten", id.toString(), "bilder",
                    bild.getGespeicherterDateiname());
            java.nio.file.Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
        }

        bildRepository.delete(bild);
        return ResponseEntity.noContent().build();
    }

    public static class LieferantBildDto {
        public Long id;
        public String originalDateiname;
        public String url;
        public String beschreibung;
        public java.time.LocalDateTime erstelltAm;
        public String mitarbeiterVorname;
        public String mitarbeiterNachname;
    }
}
