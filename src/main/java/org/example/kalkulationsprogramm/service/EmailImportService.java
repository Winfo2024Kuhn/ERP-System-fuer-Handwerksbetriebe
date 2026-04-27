package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailProcessingStatus;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.repository.EmailAttachmentRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sun.mail.imap.IMAPFolder;

import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.FetchProfile;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Service für den Import von E-Mails aus IMAP in die neue unified
 * Email-Tabelle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailImportService {

    @Value("${email.features.enabled:true}")
    private boolean emailFeaturesEnabled;

    private final EmailRepository emailRepository;
    private final EmailAttachmentRepository attachmentRepository;
    private final EmailAutoAssignmentService emailAutoAssignmentService;
    private final EmailAttachmentProcessingService emailAttachmentProcessingService;
    private final SpamFilterService spamFilterService;
    private final SteuerberaterEmailProcessingService steuerberaterEmailProcessingService;
    private final LieferantenRepository lieferantenRepository;
    private final SystemSettingsService systemSettingsService;

    // Self-Injection für transactional proxy: importMessage muss durch den
    // Spring-Proxy laufen, damit @Transactional pro Mail eine eigene
    // Mini-Transaktion startet (nicht eine Riesen-Transaktion über alle Mails).
    @Setter(onMethod_ = { @Autowired, @Lazy })
    private EmailImportService self;

    // IMAP-Ordner für eingehende E-Mails
    private static final List<String> INCOMING_FOLDERS = List.of(
            "INBOX",
            "INBOX.Archives (2).Eingangsanfragen",
            "INBOX.Archives (2).Eingangs Ab's",
            "INBOX.Archives (2).Eingangsrechnungen",
            "INBOX.Archives (2).Gedruckte Eingangsrechnungen",
            "INBOX.Archives (2).Werkstoffzeugnisse");

    // IMAP-Ordner für ausgehende E-Mails
    private static final List<String> OUTGOING_FOLDERS = List.of(
            "INBOX.Sent",
            "INBOX.Sent Items");

    /**
     * Importiert alle neuen E-Mails aus IMAP.
     * Wird alle 60 Sekunden automatisch ausgeführt.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 10_000)
    public void importNewEmails() {
        if (!emailFeaturesEnabled) {
            return;
        }
        if (!systemSettingsService.isImapConfigured()) {
            log.debug("[EmailImport] IMAP nicht konfiguriert (siehe System-Einstellungen → E-Mail)");
            return;
        }

        try {
            int imported = doImport();
            if (imported > 0) {
                log.info("[EmailImport] {} neue E-Mails importiert", imported);
            }
        } catch (Exception e) {
            log.error("[EmailImport] Fehler beim Import: {}", e.getMessage());
        }
    }

    /**
     * Führt den IMAP-Import durch.
     * Bewusst NICHT @Transactional: jede Mail bekommt via importMessage()
     * (über self-Proxy) eine eigene Mini-Transaktion. Sonst würde der gesamte
     * Import (alle Ordner, alle Mails, alle KI-Calls) in einer einzigen
     * minutenlangen Transaktion laufen und Connections blockieren.
     */
    public int doImport() {
        String user = systemSettingsService.getImapUsername();
        String pass = systemSettingsService.getImapPassword();
        String host = systemSettingsService.getImapHost();
        int port = systemSettingsService.getImapPort();
        if (user == null || user.isBlank() || pass == null || pass.isBlank()) {
            log.debug("[EmailImport] IMAP-Zugangsdaten fehlen");
            return 0;
        }

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.mime.address.strict", "false");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "15000");
        props.put("mail.imaps.timeout", "30000");

        Session session = Session.getInstance(props);
        int totalImported = 0;

        try (Store store = session.getStore("imaps")) {
            store.connect(host, port, user, pass);
            log.info("[EmailImport] IMAP-Verbindung hergestellt");

            // Eingehende E-Mails
            for (String folderName : INCOMING_FOLDERS) {
                totalImported += importFromFolder(store, folderName, EmailDirection.IN);
            }

            // Ausgehende E-Mails
            for (String folderName : OUTGOING_FOLDERS) {
                totalImported += importFromFolder(store, folderName, EmailDirection.OUT);
            }

        } catch (MessagingException e) {
            log.error("[EmailImport] IMAP-Fehler: {}", e.getMessage());
        }

        return totalImported;
    }

    /**
     * Importiert E-Mails aus einem einzelnen IMAP-Ordner.
     */
    private int importFromFolder(Store store, String folderName, EmailDirection direction) {
        try {
            Folder genericFolder = store.getFolder(folderName);
            if (!(genericFolder instanceof IMAPFolder folder) || !folder.exists()) {
                return 0;
            }

            folder.open(Folder.READ_ONLY);
            try {
                Message[] messages = folder.getMessages();

                // Envelope und Flags vorladen (Performance)
                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.ENVELOPE);
                fp.add(FetchProfile.Item.FLAGS);
                folder.fetch(messages, fp);

                int imported = 0;
                for (Message msg : messages) {
                    try {
                        // Über self-Proxy aufrufen, damit @Transactional auf
                        // importMessage greift (eigene Tx pro Mail).
                        if (self.importMessage(msg, folder, direction)) {
                            imported++;
                        }
                    } catch (Exception e) {
                        // Versuche Kontext-Infos aus dem Envelope zu extrahieren (bereits vorgeladen)
                        String msgSubject = "<unbekannt>";
                        String msgFrom = "<unbekannt>";
                        try {
                            if (msg.getSubject() != null) msgSubject = msg.getSubject();
                            Address[] from = msg.getFrom();
                            if (from != null && from.length > 0) msgFrom = from[0].toString();
                        } catch (Exception ignored) {}
                        log.warn("[EmailImport] Fehler beim Verarbeiten einer Nachricht in Ordner '{}'" +
                                " (Betreff: '{}', Von: '{}'): {} – {}",
                                folderName, msgSubject, msgFrom,
                                e.getClass().getSimpleName(), e.getMessage());
                    }
                }

                if (imported > 0) {
                    log.info("[EmailImport] Ordner {}: {} E-Mails importiert", folderName, imported);
                }
                return imported;

            } finally {
                try {
                    folder.close(false);
                } catch (MessagingException ignored) {
                }
            }

        } catch (MessagingException e) {
            log.warn("[EmailImport] Ordner '{}' nicht verfügbar: {}", folderName, e.getMessage());
            return 0;
        }
    }

    /**
     * Importiert eine einzelne E-Mail.
     * Gibt true zurück wenn neu importiert, false wenn bereits vorhanden.
     */
    @Transactional
    public boolean importMessage(Message msg, IMAPFolder folder, EmailDirection direction)
            throws MessagingException, IOException {

        // Message-ID extrahieren
        String[] ids = msg.getHeader("Message-ID");
        String messageId = (ids != null && ids.length > 0) ? ids[0] : null;

        boolean fallbackId = false;
        if (messageId == null) {
            // Fallback: IMAP-UID + Ordner als deterministische Message-ID
            long uid = folder.getUID(msg);
            messageId = "<no-msgid-uid-" + uid + "@" + folder.getFullName().replace(" ", "_") + ">";
            fallbackId = true;
        }

        // Bereits importiert?
        if (emailRepository.existsByMessageId(messageId)) {
            return false;
        }

        // Nur beim ersten Import warnen (nach Deduplizierungsprüfung)
        if (fallbackId) {
            try {
                String fallbackSubject = msg.getSubject() != null ? msg.getSubject() : "<kein Betreff>";
                Address[] fallbackFrom = msg.getFrom();
                String fallbackFromStr = (fallbackFrom != null && fallbackFrom.length > 0) ? fallbackFrom[0].toString() : "<unbekannt>";
                log.warn("[EmailImport] Neue Email ohne Message-ID in Ordner '{}', Von: '{}', Betreff: '{}' – Fallback-ID: {}",
                        folder.getFullName(), fallbackFromStr, fallbackSubject, messageId);
            } catch (Exception ignored) {
                log.warn("[EmailImport] Neue Email ohne Message-ID in Ordner '{}' – Fallback-ID: {}", folder.getFullName(), messageId);
            }
        }

        // Email erstellen
        Email email = new Email();
        email.setMessageId(messageId);
        email.setDirection(direction);
        email.setImapFolder(folder.getFullName());
        email.setImapUid(folder.getUID(msg));

        // Absender
        Address[] fromArr = msg.getFrom();
        if (fromArr != null && fromArr.length > 0) {
            if (fromArr[0] instanceof InternetAddress ia) {
                email.setFromAddress(ia.getAddress());
            } else {
                email.setFromAddress(fromArr[0].toString());
            }
        }

        // Empfänger
        email.setRecipient(extractAddresses(msg.getRecipients(Message.RecipientType.TO)));
        email.setCc(extractAddresses(msg.getRecipients(Message.RecipientType.CC)));

        // Subject
        email.setSubject(msg.getSubject());

        // Datum
        if (msg.getSentDate() != null) {
            email.setSentAt(msg.getSentDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime());
        }

        // Parent-Email via In-Reply-To oder References Header finden
        Email parentEmail = findParentEmail(msg);
        if (parentEmail != null) {
            email.setParentEmail(parentEmail);
            // Zuordnung vom Parent übernehmen, ABER: Lieferant-Domain hat Vorrang
            // vor Projekt/Anfrage-Vererbung. Grund: Lieferanten senden Rechnungen
            // oft als Antwort auf Projekt-Threads, sollen aber dem Lieferanten
            // zugeordnet werden (nicht dem Projekt).
            boolean assignedToLieferantByDomain = false;
            if (parentEmail.getProjekt() != null || parentEmail.getAnfrage() != null) {
                // Prüfe ob Absender ein bekannter Lieferant ist
                String senderDomain = email.getSenderDomain();
                if (senderDomain == null && email.getFromAddress() != null && email.getFromAddress().contains("@")) {
                    senderDomain = email.getFromAddress()
                            .substring(email.getFromAddress().lastIndexOf('@') + 1).toLowerCase();
                }
                if (senderDomain != null) {
                    List<org.example.kalkulationsprogramm.domain.Lieferanten> lieferantMatches =
                            lieferantenRepository.findByEmailDomain(senderDomain);
                    if (!lieferantMatches.isEmpty()) {
                        email.assignToLieferant(lieferantMatches.getFirst());
                        assignedToLieferantByDomain = true;
                        log.info("[EmailImport] Lieferant-Domain {} hat Vorrang vor Parent-Zuordnung (Projekt/Anfrage) für Email von {}",
                                senderDomain, email.getFromAddress());
                    }
                }
            }
            if (!assignedToLieferantByDomain) {
                if (parentEmail.getProjekt() != null) {
                    email.assignToProjekt(parentEmail.getProjekt());
                } else if (parentEmail.getAnfrage() != null) {
                    email.assignToAnfrage(parentEmail.getAnfrage());
                } else if (parentEmail.getLieferant() != null) {
                    email.assignToLieferant(parentEmail.getLieferant());
                }
            }
        }

        // Newsletter Detection (via Header)
        // WICHTIG: Nur als Newsletter markieren wenn der Absender NICHT zu einem
        // bekannten Lieferanten gehört! Viele Lieferanten nutzen Newsletter-Tools
        // (Mailchimp etc.) die automatisch List-Unsubscribe Header setzen,
        // auch für Rechnungen und Geschäftskorrespondenz.
        String[] listUnsubscribe = msg.getHeader("List-Unsubscribe");
        if (listUnsubscribe != null && listUnsubscribe.length > 0) {
            boolean isLieferantDomain = false;
            if (email.getFromAddress() != null && email.getFromAddress().contains("@")) {
                String domain = email.getFromAddress()
                        .substring(email.getFromAddress().lastIndexOf('@') + 1).toLowerCase();
                isLieferantDomain = lieferantenRepository.existsByEmailDomain(domain);
            }
            if (!isLieferantDomain) {
                email.setNewsletter(true);
            } else {
                log.info("[EmailImport] List-Unsubscribe Header ignoriert für Lieferanten-Email: {}",
                        email.getFromAddress());
            }
        }

        // Body extrahieren
        extractBody(msg, email);

        // Status
        email.setProcessingStatus(EmailProcessingStatus.DONE);
        email.setProcessedAt(LocalDateTime.now());

        // Speichern (ohne Attachments erstmal)
        emailRepository.save(email);

        // Attachments verarbeiten
        processAttachments(msg, email);

        // Zuordnung versuchen und ggf. Attachments verarbeiten
        // (nur wenn noch nicht durch Parent zugeordnet)
        if (email.getZuordnungTyp() == EmailZuordnungTyp.KEINE) {
            postProcessEmail(email);
        } else {
            // Nur Lieferant-Attachments verarbeiten für bereits zugeordnete
            if (email.getLieferant() != null && email.getDirection() == EmailDirection.IN) {
                emailAttachmentProcessingService.processLieferantAttachments(email);
            }
        }

        return true;
    }

    /**
     * Extrahiert Adressen aus einem Address-Array.
     */
    private String extractAddresses(Address[] addresses) {
        if (addresses == null || addresses.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < addresses.length; i++) {
            if (i > 0)
                sb.append(", ");
            if (addresses[i] instanceof InternetAddress ia) {
                sb.append(ia.getAddress());
            } else {
                sb.append(addresses[i].toString());
            }
        }
        return sb.toString();
    }

    /**
     * Findet die Parent-Email anhand von In-Reply-To oder References Header.
     * Gibt die am besten passende Email zurück (In-Reply-To priorisiert).
     */
    private Email findParentEmail(Message msg) throws MessagingException {
        List<String> candidateIds = new ArrayList<>();

        // 1. In-Reply-To Header (höchste Priorität - direkte Antwort)
        String[] inReplyTo = msg.getHeader("In-Reply-To");
        if (inReplyTo != null) {
            for (String id : inReplyTo) {
                if (id != null && !id.isBlank()) {
                    candidateIds.add(id.trim());
                }
            }
        }

        // 2. References Header (Email-Thread-Kette)
        String[] references = msg.getHeader("References");
        if (references != null) {
            for (String refString : references) {
                if (refString != null) {
                    // References können mehrere IDs enthalten, getrennt durch Whitespace
                    String[] ids = refString.split("\\s+");
                    for (String id : ids) {
                        if (!id.isBlank() && !candidateIds.contains(id.trim())) {
                            candidateIds.add(id.trim());
                        }
                    }
                }
            }
        }

        if (candidateIds.isEmpty()) {
            return null;
        }

        // In DB nach diesen Message-IDs suchen
        List<Email> foundParents = emailRepository.findByMessageIdIn(candidateIds);

        if (foundParents.isEmpty()) {
            return null;
        }

        // Priorisiere In-Reply-To Match (erste candidateId)
        for (String candidateId : candidateIds) {
            for (Email parent : foundParents) {
                if (candidateId.equals(parent.getMessageId())) {
                    return parent;
                }
            }
        }

        // Fallback: Neueste aus References
        return foundParents.getFirst();
    }

    /**
     * Extrahiert Body (Plain + HTML) aus einer Message.
     */
    private void extractBody(Message msg, Email email) throws MessagingException, IOException {
        Object content = msg.getContent();

        if (content instanceof String text) {
            // Einfacher Text
            if (msg.isMimeType("text/html")) {
                email.setHtmlBody(text);
            } else {
                email.setBody(text);
            }
        } else if (content instanceof MimeMultipart multipart) {
            extractBodyFromMultipart(multipart, email);
        }
    }

    private void extractBodyFromMultipart(MimeMultipart multipart, Email email)
            throws MessagingException, IOException {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            String disposition = part.getDisposition();

            // Inline-Content (nicht Attachments)
            if (disposition == null || disposition.equalsIgnoreCase(Part.INLINE)) {
                if (part.isMimeType("text/plain") && email.getBody() == null) {
                    email.setBody((String) part.getContent());
                } else if (part.isMimeType("text/html") && email.getHtmlBody() == null) {
                    email.setHtmlBody((String) part.getContent());
                } else if (part.getContent() instanceof MimeMultipart nested) {
                    extractBodyFromMultipart(nested, email);
                }
            }
        }
    }

    /**
     * Verarbeitet Attachments einer Message.
     */
    private void processAttachments(Message msg, Email email) throws MessagingException, IOException {
        Object content = msg.getContent();
        if (content instanceof MimeMultipart multipart) {
            processAttachmentsFromMultipart(multipart, email);
        }
    }

    private void processAttachmentsFromMultipart(MimeMultipart multipart, Email email)
            throws MessagingException, IOException {
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            String disposition = part.getDisposition();

            if (Part.ATTACHMENT.equalsIgnoreCase(disposition) ||
                    Part.INLINE.equalsIgnoreCase(disposition)) {

                String filename = decodeFilename(part.getFileName());
                if (filename != null) {
                    saveAttachment(part, email, Part.INLINE.equalsIgnoreCase(disposition));
                }
            }

            // Rekursiv für nested Multiparts
            if (part.getContent() instanceof MimeMultipart nested) {
                processAttachmentsFromMultipart(nested, email);
            }
        }
    }

    /**
     * Speichert ein Attachment auf der Festplatte und in der DB.
     */
    @org.springframework.beans.factory.annotation.Value("${file.mail-attachment-dir}")
    private String mailAttachmentDir;

    private void saveAttachment(BodyPart part, Email email, boolean inline)
            throws MessagingException, IOException {

        String originalFilename = decodeFilename(part.getFileName());
        String storedFilename = UUID.randomUUID().toString() + "_" + sanitizeFilename(originalFilename);

        // Verzeichnis: Flat Structure (User Request)
        Path baseDir = Path.of(mailAttachmentDir).toAbsolutePath().normalize();

        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }

        // Datei speichern
        Path filePath = baseDir.resolve(storedFilename);
        try (InputStream is = part.getInputStream()) {
            Files.copy(is, filePath);
        }

        // Entity erstellen
        EmailAttachment attachment = new EmailAttachment();
        attachment.setOriginalFilename(originalFilename);
        attachment.setStoredFilename(storedFilename);
        attachment.setMimeType(part.getContentType());
        attachment.setInlineAttachment(inline);
        attachment.setSizeBytes(Files.size(filePath));

        // Content-ID für Inline-Bilder
        if (part instanceof MimeBodyPart mbp) {
            String[] cidHeaders = mbp.getHeader("Content-ID");
            if (cidHeaders != null && cidHeaders.length > 0) {
                attachment.setContentId(cidHeaders[0].replaceAll("[<>]", ""));
            }
        }

        email.addAttachment(attachment);
        attachmentRepository.save(attachment);
    }

    /**
     * Backfill: Dekodiert alle EmailAttachment-Einträge in der DB, deren
     * originalFilename noch MIME encoded-word Strings enthält (=?...?=).
     *
     * @return Anzahl der aktualisierten Datensätze
     */
    @Transactional
    public int backfillAttachmentFilenames() {
        List<EmailAttachment> all = attachmentRepository.findAll();
        int updated = 0;
        for (EmailAttachment att : all) {
            String raw = att.getOriginalFilename();
            if (raw != null && raw.contains("=?")) {
                String decoded = decodeFilename(raw);
                if (!decoded.equals(raw)) {
                    att.setOriginalFilename(decoded);
                    attachmentRepository.save(att);
                    updated++;
                    log.debug("Backfill: '{}' → '{}'", raw, decoded);
                }
            }
        }
        log.info("Backfill attachment filenames: {} von {} Einträgen aktualisiert", updated, all.size());
        return updated;
    }

    /**
     * Dekodiert MIME encoded-word Dateinamen (RFC 2047), z.B.
     * {@code =?iso-8859-1?Q?Stahltr=E4ger.pdf?=} → {@code Stahlträger.pdf}.
     * Gibt den Originalstring zurück wenn die Dekodierung fehlschlägt.
     */
    private String decodeFilename(String filename) {
        if (filename == null) return null;
        try {
            return MimeUtility.decodeText(filename);
        } catch (Exception e) {
            log.debug("MIME filename decoding failed for '{}': {}", filename, e.getMessage());
            return filename;
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null)
            return "unknown";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Nachverarbeitung einer importierten Email:
     * 0. Steuerberater-Erkennung (Lohnabrechnungen, BWA)
     * 1. Spam-Prüfung
     * 2. Automatische Zuordnung
     * 3. Bei Lieferanten: Attachment-Analyse
     */
    @Transactional
    public void postProcessEmail(Email email) {
        // 0. Steuerberater-Erkennung (höchste Priorität)
        if (steuerberaterEmailProcessingService.processSteuerberaterEmail(email)) {
            log.info("[EmailImport] E-Mail {} als Steuerberater-Mail verarbeitet", email.getId());
            return; // Wurde vollständig verarbeitet
        }

        // 1. Versuche ZUERST Zuordnung über Lieferant-Domain
        // WICHTIG: Zuordnung VOR Spam-Prüfung, da zugeordnete Emails
        // niemals als Spam markiert werden sollen
        emailAutoAssignmentService.tryAutoAssign(email);

        // 2. Spam-Prüfung (berücksichtigt bereits die Zuordnung)
        spamFilterService.analyzeAndMarkSpam(email);

        // 3. Bei Lieferanten-Emails: Spam/Newsletter-Flags bereinigen und Attachments analysieren
        if (email.getZuordnungTyp() == EmailZuordnungTyp.LIEFERANT && email.getLieferant() != null) {
            // Zugeordnete Lieferanten-Emails sind NIEMALS Spam/Newsletter
            if (email.isSpam() || email.isNewsletter()) {
                email.setSpam(false);
                email.setNewsletter(false);
                email.setSpamScore(0);
                log.info("[EmailImport] Spam/Newsletter-Flags bereinigt für Lieferanten-Email {}", email.getId());
            }

            try {
                int created = emailAttachmentProcessingService.processLieferantAttachments(email);
                if (created > 0) {
                    log.info("[EmailImport] {} Geschäftsdokumente für Email {} erstellt", created, email.getId());
                }
            } catch (Exception e) {
                log.error("[EmailImport] Fehler bei Attachment-Verarbeitung für Email {}: {}",
                        email.getId(), e.getMessage(), e);
            }
        }

        // Spam-Score und Zuordnungs-Änderungen persistieren
        emailRepository.save(email);
    }

    /**
     * Manueller Import-Trigger (z.B. für REST-API).
     */
    public int triggerImport() {
        int imported = doImport();
        int reclassified = reprocessSpam();
        return imported + reclassified;
    }

    /**
     * Re-analyzes existing emails for spam/newsletter (e.g. after rule updates).
     */
    @Transactional
    public int reprocessSpam() {
        // Wir nehmen alle aktiven Emails, die noch nicht als Spam/Newsletter markiert
        // sind
        // oder wir überprüfen einfach alle "unsicheren" Kandidaten
        List<Email> candidates = emailRepository.findAll();

        int updated = 0;
        for (Email e : candidates) {
            if (e.getDeletedAt() != null)
                continue;
            // Wir checken auch welche, die schon flaggen haben?
            // Nein, wir wollen nur false negatives finden, nicht false positives
            // korrigieren (optional)
            if (e.isNewsletter() || e.isSpam())
                continue;

            boolean wasNewsletter = e.isNewsletter();
            boolean wasSpam = e.isSpam();

            spamFilterService.analyzeAndMarkSpam(e);

            if (e.isNewsletter() != wasNewsletter || e.isSpam() != wasSpam) {
                emailRepository.save(e);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("[SpamReprocess] {} Emails neu klassifiziert", updated);
        }
        return updated;
    }

    /**
     * Statistiken.
     */
    public Map<String, Long> getStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", emailRepository.count());
        stats.put("projekt", emailRepository.countByZuordnungTyp(EmailZuordnungTyp.PROJEKT));
        stats.put("anfrage", emailRepository.countByZuordnungTyp(EmailZuordnungTyp.ANFRAGE));
        stats.put("lieferant", emailRepository.countByZuordnungTyp(EmailZuordnungTyp.LIEFERANT));
        stats.put("unassigned", emailRepository.countByZuordnungTyp(EmailZuordnungTyp.KEINE));
        stats.put("unread", emailRepository.countUnread());
        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // BACKFILL: PARENT-EMAIL VERKNÜPFUNG
    // ═══════════════════════════════════════════════════════════════

    /**
     * Backfill: Prüft ALLE existierenden E-Mails auf Steuerberater-Dokumente (Lohnabrechnungen, BWAs).
     * Nutzt die idempotent implementierte Prozessierung (vermeidet Duplikate).
     */
    @Transactional
    public int backfillSteuerberaterEmails() {
        log.info("[Backfill] Starte Steuerberater-Dokumente Scan...");
        int processed = 0;
        List<Email> allEmails = emailRepository.findAll();

        for (Email email : allEmails) {
            // Nur eingehende Emails prüfen
            if (email.getDirection() != EmailDirection.IN) {
                continue;
            }
            
            // Wenn bereits als Steuerberater zugeordnet, prüfen wir trotzdem auf fehlende Dokumente?
            // Ja, weil vllt neue Dokumenttypen erkannt werden.
            // Der Service prüft Duplikate bzgl. Attachments selbst.
            
            try {
                boolean result = steuerberaterEmailProcessingService.processSteuerberaterEmail(email);
                if (result) {
                    processed++;
                }
            } catch (Exception e) {
                log.error("[Backfill] Fehler bei Email {}: {}", email.getId(), e.getMessage());
            }
        }
        log.info("[Backfill] {} E-Mails als Steuerberater-Emails verifiziert/verarbeitet", processed);
        return processed;
    }

    /**
     * Backfill: Verknüpft bestehende Emails nachträglich mit ihren Parent-Emails.
     * Basiert auf Subject-Pattern-Matching (AW:/RE:/FWD:) für Emails ohne Parent.
     * Erbt auch die Zuordnung (Projekt/Anfrage/Lieferant) vom Parent.
     * 
     * @return Anzahl der verknüpften Emails
     */
    @Transactional
    public int backfillParentEmails() {
        log.info("[Backfill] Starte Parent-Email Backfill...");
        int updated = 0;

        List<Email> allEmails = emailRepository.findAll();

        // Index nach normalisiertem Subject (ohne AW:/RE:/FWD: Prefix)
        Map<String, List<Email>> byNormalizedSubject = new HashMap<>();
        for (Email email : allEmails) {
            String normalized = normalizeSubject(email.getSubject());
            byNormalizedSubject.computeIfAbsent(normalized, k -> new ArrayList<>()).add(email);
        }

        for (Email email : allEmails) {
            if (email.getParentEmail() != null) {
                continue; // Hat schon Parent
            }

            String subject = email.getSubject();
            if (subject == null)
                continue;

            // Ist dies eine Antwort/Weiterleitung?
            if (!isReplyOrForward(subject)) {
                continue;
            }

            String normalized = normalizeSubject(subject);
            List<Email> candidates = byNormalizedSubject.get(normalized);

            if (candidates == null || candidates.size() < 2) {
                continue;
            }

            // Finde die beste Parent-Kandidaten (älter als diese Email)
            Email bestParent = null;
            for (Email candidate : candidates) {
                if (candidate.getId().equals(email.getId())) {
                    continue; // Nicht sich selbst
                }
                if (candidate.getSentAt() != null && email.getSentAt() != null
                        && candidate.getSentAt().isBefore(email.getSentAt())) {
                    // Bevorzuge die neuste Email vor dieser
                    if (bestParent == null || candidate.getSentAt().isAfter(bestParent.getSentAt())) {
                        bestParent = candidate;
                    }
                }
            }

            if (bestParent != null) {
                email.setParentEmail(bestParent);

                // Zuordnung übernehmen falls diese Email noch keine hat
                if (email.getZuordnungTyp() == EmailZuordnungTyp.KEINE) {
                    if (bestParent.getProjekt() != null) {
                        email.assignToProjekt(bestParent.getProjekt());
                    } else if (bestParent.getAnfrage() != null) {
                        email.assignToAnfrage(bestParent.getAnfrage());
                    } else if (bestParent.getLieferant() != null) {
                        email.assignToLieferant(bestParent.getLieferant());
                    }
                }

                emailRepository.save(email);
                updated++;
            }
        }

        log.info("[Backfill] {} Emails mit Parent verknüpft", updated);
        return updated;
    }

    /**
     * Prüft ob ein Betreff eine Antwort oder Weiterleitung ist.
     */
    private boolean isReplyOrForward(String subject) {
        if (subject == null)
            return false;
        String lower = subject.toLowerCase().trim();
        return lower.startsWith("aw:") || lower.startsWith("re:")
                || lower.startsWith("fwd:") || lower.startsWith("wg:");
    }

    /**
     * Normalisiert Betreff durch Entfernen von AW:/RE:/FWD:/WG: Prefixen.
     */
    private String normalizeSubject(String subject) {
        if (subject == null)
            return "";
        String normalized = subject.trim();
        // Entferne alle Prefixe rekursiv
        String pattern = "(?i)^(aw:|re:|fwd:|wg:)\\s*";
        while (normalized.matches(pattern + ".*")) {
            normalized = normalized.replaceFirst(pattern, "").trim();
        }
        return normalized.toLowerCase();
    }

    /**
     * Löscht eine E-Mail permanent vom IMAP-Server.
     */
    public void deleteEmailFromServer(Email email) {
        if (email.getMessageId() == null) {
            log.warn("[EmailDeletion] Email {} hat null Message-ID, überspringe Server-Löschung", email.getId());
            return;
        }

        String user = System.getenv("IMAP_USER");
        String pass = System.getenv("IMAP_PASSWORD");
        if (user == null || user.isBlank() || pass == null || pass.isBlank()) {
            log.debug("[EmailDeletion] IMAP_USER/PASSWORD nicht konfiguriert, überspringe Server-Löschung");
            return;
        }
        String host = System.getenv("IMAP_HOST");
        if (host == null || host.isBlank()) {
            host = "secureimap.t-online.de";
        }

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "5000");
        props.put("mail.imaps.timeout", "5000");

        try {
            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect(host, user, pass);

            boolean deleted = false;

            // 1. VERSUCH: Direktzugriff über Folder + UID (Viel schneller & genauer)
            if (email.getImapFolder() != null && email.getImapUid() != null) {
                try {
                    Folder folder = store.getFolder(email.getImapFolder());
                    if (folder.exists()) {
                        folder.open(Folder.READ_WRITE);
                        if (folder instanceof IMAPFolder imapFolder) {
                            Message msg = imapFolder.getMessageByUID(email.getImapUid());
                            if (msg != null) {
                                // Sicherheitscheck: Message-ID vergleichen
                                String[] ids = msg.getHeader("Message-ID");
                                String tempMsgId = (ids != null && ids.length > 0) ? ids[0] : null;

                                // Vergleich toleriert null oder exakten Match (manche Server liefern Header
                                // vllt nicht performant)
                                // Aber besser wir prüfen es, um nicht falsche Mails zu löschen falls UID
                                // recycled wurde (sehr selten)
                                if (tempMsgId != null && tempMsgId.equals(email.getMessageId())) {
                                    msg.setFlag(Flags.Flag.DELETED, true);
                                    deleted = true;
                                    log.info("[EmailDeletion] E-Mail {} via UID {} in Ordner {} gelöscht",
                                            email.getMessageId(), email.getImapUid(), email.getImapFolder());
                                } else {
                                    log.debug(
                                            "[EmailDeletion] UID-Treffer aber Message-ID Mismatch. Erwarte: {}, gefunden: {}",
                                            email.getMessageId(), tempMsgId);
                                }
                            }
                        }
                        folder.close(true); // Expunge
                    }
                } catch (Exception e) {
                    log.debug("[EmailDeletion] Direktzugriff fehlgeschlagen: {}", e.getMessage());
                }
            }

            if (deleted) {
                store.close();
                return;
            }

            // 2. VERSUCH: Fallback Suche über Message-ID in allen Ordnern
            log.debug("[EmailDeletion] Fallback: Suche nach Message-ID {} in allen Ordnern", email.getMessageId());

            // Alle Ordner durchsuchen
            List<String> foldersToCheck = new ArrayList<>(INCOMING_FOLDERS);
            foldersToCheck.addAll(OUTGOING_FOLDERS);

            for (String folderName : foldersToCheck) {
                try {
                    Folder folder = store.getFolder(folderName);
                    if (!folder.exists())
                        continue;

                    folder.open(Folder.READ_WRITE);

                    // Suche nach Message-ID
                    jakarta.mail.search.SearchTerm term = new jakarta.mail.search.HeaderTerm("Message-ID",
                            email.getMessageId());
                    Message[] messages = folder.search(term);

                    if (messages != null && messages.length > 0) {
                        for (Message msg : messages) {
                            msg.setFlag(Flags.Flag.DELETED, true);
                            log.info("[EmailDeletion] E-Mail {} in Ordner {} über Suche gefunden und gelöscht",
                                    email.getMessageId(), folderName);
                            deleted = true;
                        }
                    }

                    // Schließen und Expunge (Löschung anwenden)
                    folder.close(true);

                    if (deleted)
                        break;
                } catch (Exception ex) {
                    log.warn("[EmailDeletion] Fehler beim Zugriff auf Ordner {}: {}", folderName, ex.getMessage());
                }
            }

            store.close();

            if (!deleted) {
                log.info("[EmailDeletion] E-Mail {} auf dem Server nicht gefunden (bereits gelöscht?)",
                        email.getMessageId());
            }

        } catch (Exception e) {
            log.error("[EmailDeletion] Fehler bei Server-Löschung: {}", e.getMessage());
        }
    }
}
