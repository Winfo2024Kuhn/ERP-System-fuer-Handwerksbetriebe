package org.example.kalkulationsprogramm.controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.AnfrageGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailSignature;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.ProjektGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.Email.EmailBeautifyRequest;
import org.example.kalkulationsprogramm.dto.Email.EmailBeautifyResponse;
import org.example.kalkulationsprogramm.dto.Email.EmailPreviewRequest;
import org.example.kalkulationsprogramm.dto.Email.EmailSendRequest;
import org.example.kalkulationsprogramm.dto.Email.ReklamationEmailGenerateRequest;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.EmailAiService;
import org.example.kalkulationsprogramm.service.EmailSignatureService;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
@Slf4j
public class EmailController {
    private final ProjektDokumentRepository dokumentRepository;
    private final AnfrageDokumentRepository anfrageDokumentRepository;
    private final AnfrageRepository anfrageRepository;
    private final org.example.kalkulationsprogramm.repository.EmailRepository emailRepository;
    private final EmailAiService emailAiService;
    private final EmailSignatureService emailSignatureService;
    private final FrontendUserProfileService frontendUserProfileService;
    private final DateiSpeicherService dateiSpeicherService;

    @Value("${smtp.host}")
    private String smtpHost;

    @Value("${smtp.port}")
    private int smtpPort;

    @Value("${smtp.username}")
    private String smtpUsername;

    @Value("${smtp.password}")
    private String smtpPassword;

    @Value("${file.mail-attachment-dir}")
    private String mailAttachmentDir;

    @PostMapping("/beautify")
    public ResponseEntity<EmailBeautifyResponse> beautifyEmail(@RequestBody EmailBeautifyRequest request) {
        if (request == null || request.getBody() == null || request.getBody().trim().isEmpty()) {
            EmailBeautifyResponse response = new EmailBeautifyResponse();
            response.setBody("");
            return ResponseEntity.ok(response);
        }
        try {
            String result = emailAiService.beautify(request.getBody(), request.getContext());
            EmailBeautifyResponse response = new EmailBeautifyResponse();
            response.setBody(result);
            return ResponseEntity.ok(response);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("KI-Formulierung unterbrochen", ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (Exception ex) {
            log.warn("KI-Formulierung fehlgeschlagen", ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    @PostMapping("/generate-reklamation")
    public ResponseEntity<?> generateReklamationEmail(
            @RequestBody @jakarta.validation.Valid ReklamationEmailGenerateRequest request) {
        if (request.getBeschreibung() == null || request.getBeschreibung().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Beschreibung darf nicht leer sein");
        }

        try {
            // Resolve image paths from URLs
            List<Path> bildPaths = new ArrayList<>();
            if (request.getBildUrls() != null) {
                for (String bildUrl : request.getBildUrls()) {
                    String filename = bildUrl;
                    if (filename.contains("/")) {
                        filename = filename.substring(filename.lastIndexOf('/') + 1);
                    }
                    Path possiblePath = findBildPath(filename);
                    if (possiblePath != null) {
                        bildPaths.add(possiblePath);
                    }
                }
            }

            String jsonResult = emailAiService.generateReklamationEmail(
                    request.getBeschreibung(), request.getLieferantName(), bildPaths);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(jsonResult);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("KI Reklamation-Email-Generierung unterbrochen", ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        } catch (Exception ex) {
            log.warn("KI Reklamation-Email-Generierung fehlgeschlagen", ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private Path findBildPath(String gespeicherterDateiname) {
        try {
            // Search in uploads/lieferanten/*/bilder/ for the file
            Path uploadsDir = Paths.get("uploads", "lieferanten");
            if (!java.nio.file.Files.exists(uploadsDir)) return null;

            try (var stream = java.nio.file.Files.walk(uploadsDir, 3)) {
                return stream
                        .filter(p -> p.getFileName().toString().equals(gespeicherterDateiname))
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("Konnte Bild-Pfad nicht auflösen: {}", gespeicherterDateiname, e);
            return null;
        }
    }

    @PostMapping("/preview")
    public ResponseEntity<EmailService.EmailContent> previewInvoiceEmail(@RequestBody EmailPreviewRequest request) {
        ProjektDokument doc = dokumentRepository.findById(request.getDokumentId()).orElse(null);
        String userName = resolveUserName(request.getBenutzer(), request.getFrontendUserId());

        // 1. Fallback: Anfrage oder Zeichnung (wenn doc null)
        if (doc == null) {
            var anfrageDocOpt = anfrageDokumentRepository.findById(request.getDokumentId());
            if (anfrageDocOpt.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            var anfrageDoc = anfrageDocOpt.get();
            String name = anfrageDoc.getOriginalDateiname();
            if (name != null) {
                String lowerCase = name.toLowerCase();
                if (lowerCase.contains("zeichnung") || lowerCase.contains("entwurf")) {
                    String bv = "";
                    if (anfrageDoc instanceof AnfrageGeschaeftsdokument agd && agd.getAnfrage() != null) {
                        bv = agd.getAnfrage().getBauvorhaben();
                    }
                    if (bv == null)
                        bv = "";

                    EmailService.EmailContent content = EmailService.buildDrawingEmail(
                            request.getAnrede(),
                            userName,
                            bv);
                    try {
                        var sigOpt = getSignatureForFrontendUser(request.getFrontendUserId(), request.getBenutzer());
                        if (sigOpt.isPresent()) {
                            String html = content.htmlBody()
                                    + emailSignatureService.renderSignatureHtmlForPreview(sigOpt.get(), userName);
                            content = new EmailService.EmailContent(content.subject(), html);
                        }
                    } catch (Exception ignored) {
                    }
                    return ResponseEntity.ok(content);
                }
            }
            return ResponseEntity.notFound().build();
        }

        // 2. Technische Zeichnung (Projekt-Dokument)
        if (doc.getOriginalDateiname() != null) {
            String lowerCase = doc.getOriginalDateiname().toLowerCase();
            if (lowerCase.contains("zeichnung") || lowerCase.contains("entwurf")) {
                String bv = doc.getProjekt() != null ? doc.getProjekt().getBauvorhaben() : "";
                if (bv == null)
                    bv = "";

                EmailService.EmailContent content = EmailService.buildDrawingEmail(
                        request.getAnrede(),
                        userName,
                        bv);
                try {
                    var sigOpt = getSignatureForFrontendUser(request.getFrontendUserId(), request.getBenutzer());
                    if (sigOpt.isPresent()) {
                        String html = content.htmlBody()
                                + emailSignatureService.renderSignatureHtmlForPreview(sigOpt.get(), userName);
                        content = new EmailService.EmailContent(content.subject(), html);
                    }
                } catch (Exception ignored) {
                }
                return ResponseEntity.ok(content);
            }
        }

        // 3. Projekt-Geschäftsdokument (Rechnung / Anfrage / AB)
        if (!(doc instanceof ProjektGeschaeftsdokument gesDoc)) {
            return ResponseEntity.notFound().build();
        }

        Path storedPath = resolveStoredPath(gesDoc.getGespeicherterDateiname());
        if (storedPath == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        String path = storedPath.toString();
        var projekt = gesDoc.getProjekt();
        String bauvorhaben = projekt != null ? projekt.getBauvorhaben() : "";
        String projektnummer = projekt != null ? projekt.getAuftragsnummer() : "";
        String kundenName = "";
        if (projekt != null && projekt.getKundenId() != null) {
            var kunde = projekt.getKundenId();
            kundenName = (kunde.getAnsprechspartner() != null && !kunde.getAnsprechspartner().isBlank())
                    ? kunde.getAnsprechspartner()
                    : (kunde.getName() != null ? kunde.getName() : "");
        }
        String betrag = gesDoc.getBruttoBetrag() != null
                ? NumberFormat.getCurrencyInstance(Locale.GERMANY).format(gesDoc.getBruttoBetrag())
                : "";

        // Zeichnung anhand der Geschäftsart (Legacy Check)
        if (gesDoc.getGeschaeftsdokumentart() != null
                && gesDoc.getGeschaeftsdokumentart().toLowerCase().contains("zeichnung")) {
            EmailService.EmailContent content = EmailService.buildDrawingEmail(
                    request.getAnrede(),
                    userName,
                    bauvorhaben);
            try {
                var sigOpt = getSignatureForFrontendUser(request.getFrontendUserId(), request.getBenutzer());
                if (sigOpt.isPresent()) {
                    String html = content.htmlBody()
                            + emailSignatureService.renderSignatureHtmlForPreview(sigOpt.get(), userName);
                    content = new EmailService.EmailContent(content.subject(), html);
                }
            } catch (Exception ignored) {
            }
            return ResponseEntity.ok(content);
        }

        EmailService.EmailContent content;
        if (gesDoc.getGeschaeftsdokumentart() != null &&
                gesDoc.getGeschaeftsdokumentart().toLowerCase().contains("angebot")) {
            content = EmailService.buildOfferEmail(
                    request.getAnrede(),
                    "",
                    bauvorhaben,
                    gesDoc.getDokumentid(),
                    userName,
                    request.getPosition());
        } else if (gesDoc.getGeschaeftsdokumentart() != null &&
                gesDoc.getGeschaeftsdokumentart().toLowerCase().contains("auftragsbest")) {
            content = EmailService.buildOrderConfirmationEmail(
                    path,
                    request.getAnrede(),
                    kundenName,
                    bauvorhaben,
                    projektnummer,
                    gesDoc.getDokumentid(),
                    betrag,
                    userName);
        } else {
            LocalDate rechnungsdatum = gesDoc.getRechnungsdatum() != null ? gesDoc.getRechnungsdatum()
                    : LocalDate.now();
            LocalDate faelligkeitsdatum = gesDoc.getFaelligkeitsdatum() != null ? gesDoc.getFaelligkeitsdatum()
                    : rechnungsdatum;
            String dokumentartHint = gesDoc.getGeschaeftsdokumentart();
            String mahnstufeHint = gesDoc.getMahnstufe() != null ? gesDoc.getMahnstufe().name() : null;
            content = EmailService.buildInvoiceEmailWithTypeHints(
                    path,
                    request.getAnrede(),
                    kundenName,
                    bauvorhaben,
                    projektnummer,
                    gesDoc.getDokumentid(),
                    rechnungsdatum,
                    faelligkeitsdatum,
                    betrag,
                    userName,
                    dokumentartHint,
                    mahnstufeHint);
        }

        // Append configured signature for preview
        try {
            var sigOpt = getSignatureForFrontendUser(request.getFrontendUserId(), request.getBenutzer());
            if (sigOpt.isPresent()) {
                String html = content.htmlBody()
                        + emailSignatureService.renderSignatureHtmlForPreview(sigOpt.get(), userName);
                content = new EmailService.EmailContent(content.subject(), html);
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(content);
    }

    @PostMapping("/preview/anfrage")
    public ResponseEntity<EmailService.EmailContent> previewOfferEmail(@RequestBody EmailPreviewRequest request) {
        AnfrageDokument doc = anfrageDokumentRepository.findById(request.getDokumentId()).orElse(null);

        if (!(doc instanceof AnfrageGeschaeftsdokument gesDoc)) {
            return ResponseEntity.notFound().build();
        }
        Anfrage anfrage = gesDoc.getAnfrage();

        String userName = resolveUserName(request.getBenutzer(), request.getFrontendUserId());
        // Zeichnung/Entwurf beim Anfragesdokument -> Zeichnungs-E-Mail
        if (doc.getOriginalDateiname() != null) {
            String lowerCase = doc.getOriginalDateiname().toLowerCase();
            if (lowerCase.contains("zeichnung") || lowerCase.contains("entwurf")) {
                EmailService.EmailContent content = EmailService.buildDrawingEmail(
                        request.getAnrede(),
                        userName,
                        anfrage.getBauvorhaben() != null ? anfrage.getBauvorhaben() : "");

                return ResponseEntity.ok(content);
            }
        }

        if (gesDoc.getGeschaeftsdokumentart() != null
                && gesDoc.getGeschaeftsdokumentart().toLowerCase().contains("zeichnung")) {
            EmailService.EmailContent content = EmailService.buildDrawingEmail(
                    request.getAnrede(),
                    userName,
                    request.getBauvorhaben());
            return ResponseEntity.ok(content);
        }
        String bauvorhaben = request.getBauvorhaben() != null ? request.getBauvorhaben()
                : (anfrage.getBauvorhaben() != null ? anfrage.getBauvorhaben() : "");
        EmailService.EmailContent content = EmailService.buildOfferEmail(
                request.getAnrede(),
                anfrage.getKunde() != null ? anfrage.getKunde().getName() : "",
                bauvorhaben,
                gesDoc.getDokumentid(),
                userName,
                request.getPosition() != null ? request.getPosition() : "");
        try {
            var sigOpt = getSignatureForFrontendUser(request.getFrontendUserId(), request.getBenutzer());
            if (sigOpt.isPresent()) {
                String html = content.htmlBody()
                        + emailSignatureService.renderSignatureHtmlForPreview(sigOpt.get(), userName);
                content = new EmailService.EmailContent(content.subject(), html);
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(content);
    }

    @PostMapping("/send")
    public ResponseEntity<Void> sendInvoiceEmail(@RequestBody EmailSendRequest request) {
        ProjektDokument doc = dokumentRepository.findById(request.getDokumentId()).orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }

        Path storedPath = resolveStoredPath(doc.getGespeicherterDateiname());
        if (storedPath == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        String path = storedPath.toString();
        String userName = resolveUserName(request.getBenutzer(), request.getFrontendUserId());

        EmailService service = new EmailService(smtpHost, smtpPort, smtpUsername, smtpPassword);
        String messageId;
        try {
            String finalHtml = Optional.ofNullable(request.getHtmlBody()).orElse("");
            java.util.Map<String, java.io.File> inline = new java.util.HashMap<>();
            var sigOpt = getSignatureForFrontendUser(request.getFrontendUserId(), request.getBenutzer());
            if (sigOpt.isPresent()) {
                finalHtml = emailSignatureService.ensureSignaturePresentOnce(finalHtml, sigOpt.get(), userName);
                inline = emailSignatureService.buildInlineCidFileMap(sigOpt.get());
            }
            messageId = service.sendEmailAndReturnMessageIdWithInline(
                    request.getRecipient(),
                    request.getCc(),
                    request.getFromAddress(),
                    request.getSubject(),
                    finalHtml,
                    inline,
                    path,
                    doc.getOriginalDateiname());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
        // ProcessedEmail-Logik entfernt - wird nun über das neue unified Email-System
        // gehandhabt
        doc.setEmailVersandDatum(LocalDate.now());
        dokumentRepository.save(doc);

        // Persist using Unified Email Entity
        try {
            if (doc instanceof ProjektGeschaeftsdokument gesDoc) {
                var projekt = gesDoc.getProjekt();
                if (projekt != null) {
                    var email = new Email();
                    email.assignToProjekt(projekt);
                    email.setFromAddress(request.getFromAddress());
                    email.extractSenderDomain();
                    email.setRecipient(request.getRecipient());
                    email.setCc(request.getCc());
                    email.setSubject(request.getSubject());
                    email.setHtmlBody(request.getHtmlBody());
                    email.setRawBody(request.getHtmlBody());
                    email.setBody(org.example.kalkulationsprogramm.util.EmailHtmlSanitizer
                            .htmlToPlainText(request.getHtmlBody()));
                    email.setSentAt(java.time.LocalDateTime.now());
                    email.setDirection(EmailDirection.OUT);
                    email.setMessageId(messageId);
                    email = emailRepository.save(email);

                    // Copy attachment into email directory
                    java.nio.file.Path src = storedPath.toAbsolutePath().normalize();
                    java.nio.file.Path dstDir = Path.of(mailAttachmentDir).toAbsolutePath().normalize()
                            .resolve("attachments").resolve(String.valueOf(email.getId()));
                    try {
                        java.nio.file.Files.createDirectories(dstDir);
                    } catch (Exception ignored) {
                    }
                    String storedName = java.util.UUID.randomUUID() + "_" + doc.getOriginalDateiname();
                    java.nio.file.Path dst = dstDir.resolve(storedName);
                    try {
                        java.nio.file.Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {
                    }

                    var att = new EmailAttachment();
                    att.setEmail(email);
                    att.setOriginalFilename(doc.getOriginalDateiname());
                    att.setStoredFilename(storedName);
                    try {
                        att.setSizeBytes(java.nio.file.Files.size(dst));
                    } catch (Exception e) {
                        att.setSizeBytes(0L);
                    }
                    att.setMimeType(java.nio.file.Files.probeContentType(dst));

                    email.addAttachment(att);
                    emailRepository.save(email);
                }
            }
        } catch (Exception ignored) {
            log.error("Fehler beim Speichern der gesendeten Projekt-Email", ignored);
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/send/anfrage")
    public ResponseEntity<Void> sendOfferEmail(@RequestBody EmailSendRequest request) {
        AnfrageDokument doc = anfrageDokumentRepository.findById(request.getDokumentId()).orElse(null);
        if (!(doc instanceof AnfrageGeschaeftsdokument gesDoc)) {
            return ResponseEntity.notFound().build();
        }
        Anfrage anfrage = gesDoc.getAnfrage();
        Path storedPath = resolveStoredPath(gesDoc.getGespeicherterDateiname());
        if (storedPath == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        String path = storedPath.toString();
        String userName = resolveUserName(request.getBenutzer(), request.getFrontendUserId());
        EmailService service = new EmailService(smtpHost, smtpPort, smtpUsername, smtpPassword);
        String messageId;
        try {
            String finalHtml = Optional.ofNullable(request.getHtmlBody()).orElse("");
            java.util.Map<String, java.io.File> inline = new java.util.HashMap<>();
            var sigOpt = getSignatureForFrontendUser(request.getFrontendUserId(), request.getBenutzer());
            if (sigOpt.isPresent()) {
                finalHtml = emailSignatureService.ensureSignaturePresentOnce(finalHtml, sigOpt.get(), userName);
                inline = emailSignatureService.buildInlineCidFileMap(sigOpt.get());
            }
            messageId = service.sendEmailAndReturnMessageIdWithInline(
                    request.getRecipient(),
                    request.getCc(),
                    request.getFromAddress(),
                    request.getSubject(),
                    finalHtml,
                    inline,
                    path,
                    gesDoc.getOriginalDateiname());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
        // ProcessedEmail-Logik entfernt - wird nun über das neue unified Email-System
        // gehandhabt
        if (request.getRecipient() != null && !request.getRecipient().isBlank()) {
            boolean inKunde = anfrage.getKunde() != null && anfrage.getKunde().getKundenEmails() != null
                    && anfrage.getKunde().getKundenEmails().contains(request.getRecipient());
            boolean inAnfrage = anfrage.getKundenEmails() != null
                    && anfrage.getKundenEmails().contains(request.getRecipient());

            if (!inKunde && !inAnfrage) {
                if (anfrage.getKundenEmails() == null) {
                    anfrage.setKundenEmails(new java.util.ArrayList<>());
                }
                anfrage.getKundenEmails().add(request.getRecipient());
            }
        }
        if (request.getBauvorhaben() != null) {
            anfrage.setBauvorhaben(request.getBauvorhaben());
        }
        anfrage.setEmailVersandDatum(LocalDate.now());
        anfrageRepository.save(anfrage);
        gesDoc.setEmailVersandDatum(LocalDate.now());
        anfrageDokumentRepository.save(gesDoc);
        // Persist using Unified Email Entity
        try {
            if (anfrage != null) {
                var email = new Email();
                email.assignToAnfrage(anfrage);
                email.setFromAddress(request.getFromAddress());
                email.extractSenderDomain();
                email.setRecipient(request.getRecipient());
                email.setCc(request.getCc());
                email.setSubject(request.getSubject());
                email.setHtmlBody(request.getHtmlBody());
                email.setRawBody(request.getHtmlBody());
                email.setBody(org.example.kalkulationsprogramm.util.EmailHtmlSanitizer
                        .htmlToPlainText(request.getHtmlBody()));
                email.setSentAt(java.time.LocalDateTime.now());
                email.setDirection(EmailDirection.OUT);
                email.setMessageId(messageId);
                email = emailRepository.save(email);

                java.nio.file.Path src = storedPath.toAbsolutePath().normalize();

                // New structure: emails/<id>/attachments
                java.nio.file.Path dstDir = Path.of(mailAttachmentDir).toAbsolutePath().normalize()
                        .resolve("attachments").resolve(String.valueOf(email.getId()));

                try {
                    java.nio.file.Files.createDirectories(dstDir);
                } catch (Exception ignored) {
                }
                String storedName = java.util.UUID.randomUUID() + "_" + gesDoc.getOriginalDateiname();
                java.nio.file.Path dst = dstDir.resolve(storedName);
                try {
                    java.nio.file.Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {
                }

                var att = new EmailAttachment();
                att.setEmail(email);
                att.setOriginalFilename(gesDoc.getOriginalDateiname());
                att.setStoredFilename(storedName);
                try {
                    att.setSizeBytes(java.nio.file.Files.size(dst));
                } catch (Exception e) {
                    att.setSizeBytes(0L);
                }
                att.setMimeType(java.nio.file.Files.probeContentType(dst));

                email.addAttachment(att);
                emailRepository.save(email);
            }
        } catch (Exception ignored) {
            log.error("Fehler beim Speichern der gesendeten Anfrage-Email", ignored);
        }
        return ResponseEntity.ok().build();
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
                .or(() -> frontendUserProfileService.findByDisplayName(providedName))
                .map(FrontendUserProfile::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .orElse(null);
    }

    private java.nio.file.Path resolveStoredPath(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return null;
        }
        try {
            Resource resource = dateiSpeicherService.ladeDokumentAlsResource(storedFileName);
            if (resource != null && resource.exists()) {
                return resource.getFile().toPath();
            }
        } catch (Exception ex) {
            log.warn("Konnte gespeicherten Pfad für {} nicht ermitteln", storedFileName, ex);
        }
        return null;
    }
}
