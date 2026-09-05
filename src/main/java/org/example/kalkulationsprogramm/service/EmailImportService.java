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

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailProcessingStatus;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.repository.EmailAttachmentRepository;
import org.example.kalkulationsprogramm.repository.EmailBlacklistRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
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

    /**
     * Wie viele Mails vor {@code email.sentAt} der Subject-Fallback beim Live-
     * Import maximal scannt. Default 500 deckt bei normalem Mailaufkommen
     * mehrere Tage ab. Bei sehr aktiven Mailboxen über
     * {@code email.threading.subject-fallback-lookback} hochsetzen.
     */
    @Value("${email.threading.subject-fallback-lookback:500}")
    private int subjectFallbackLookback;

    private final EmailRepository emailRepository;
    private final EmailAttachmentRepository attachmentRepository;
    private final EmailAutoAssignmentService emailAutoAssignmentService;
    private final EmailAttachmentProcessingService emailAttachmentProcessingService;
    private final SpamFilterService spamFilterService;
    private final SteuerberaterEmailProcessingService steuerberaterEmailProcessingService;
    private final LieferantenRepository lieferantenRepository;
    private final SystemSettingsService systemSettingsService;
    private final OutOfOfficeResponder outOfOfficeResponder;
    private final EmailBlacklistRepository emailBlacklistRepository;
    private final BounceErkennungService bounceErkennungService;
    private final org.example.kalkulationsprogramm.repository.SeenSenderDomainRepository seenSenderDomainRepository;

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
     * Schaltet abgelaufene Abwesenheitspläne (endAt < heute) automatisch aus.
     * Läuft einmal pro Stunde, damit der "active=true"-Status nicht über
     * das eingestellte Enddatum hinaus stehen bleibt.
     */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 60_000L)
    public void deactivateExpiredOutOfOffice() {
        if (!emailFeaturesEnabled) {
            return;
        }
        try {
            outOfOfficeResponder.deactivateExpiredSchedules();
        } catch (Exception e) {
            log.warn("[OOO] Auto-Deaktivierung abgelaufener Pläne fehlgeschlagen: {}", e.getMessage());
        }
    }

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
                            // Erst NACH dem Commit der Import-Transaktion,
                            // sonst sperren sich beide gegenseitig.
                            pruefeRuecklaeufer(msg, direction);
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
     * Wertet eine gerade importierte Eingangsmail als moegliche
     * Unzustellbarkeits-Meldung aus und markiert die betroffene Ausgangsmail.
     * Ohne diesen Schritt bliebe im ERP "versendet" stehen, obwohl beim
     * Empfaenger nie etwas ankam — der Rueckläufer selbst landet nur als
     * weitere Mail im Posteingang und faellt im Alltag durch.
     *
     * <p><strong>Warum erst hier und nicht in {@link #importMessage}:</strong>
     * Der Bounce-Service laeuft in {@code REQUIRES_NEW}, also in einer eigenen
     * Datenbank-Transaktion. Aus der noch offenen Import-Transaktion heraus
     * aufgerufen, blockieren sich beide gegenseitig: Der Rueckläufer ist ueber
     * {@code parent_email_id} mit genau der Ausgangsmail verknuepft, die er als
     * unzustellbar markieren soll. Deren noch nicht committeter INSERT haelt
     * wegen des Fremdschluessels eine Lesesperre auf der Ausgangsmail, waehrend
     * das UPDATE der inneren Transaktion darauf eine Schreibsperre braucht.
     * InnoDB erkennt darin keinen Deadlock — die aeussere Transaktion wartet
     * nicht auf eine Sperre, sondern auf diesen Anwendungscode — und laeuft
     * stattdessen nach {@code innodb_lock_wait_timeout} (Standard: 50 s) in
     * "Lock wait timeout exceeded". Der Import scheitert, wird zurueckgerollt
     * und beim naechsten Lauf erneut versucht: eine Endlosschleife, in der der
     * Rueckläufer nie ankommt und jeder Durchlauf 50 Sekunden blockiert. Nach
     * dem Commit sind die Sperren frei.
     *
     * <p>Fehler werden hier geschluckt: Der Service faengt seine eigenen
     * Ausnahmen zwar ab, der Commit seiner Transaktion passiert aber erst
     * hinter der Methode am Spring-Proxy — ein Fehler von dort wuerde sonst
     * den Import der uebrigen Mails abbrechen.
     */
    private void pruefeRuecklaeufer(Message msg, EmailDirection direction) {
        if (direction != EmailDirection.IN) {
            return;
        }
        try {
            bounceErkennungService.verarbeiteRuecklaeufer(msg);
        } catch (Exception e) {
            log.warn("[EmailImport] Rueckläufer-Pruefung fehlgeschlagen: {} – {}",
                    e.getClass().getSimpleName(), e.getMessage());
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

        // Eigene Sent-Kopie? Das ERP legt jede versendete Mail zusaetzlich im
        // IMAP-Sent-Ordner ab (unabhaengiger Nachweis auf dem Provider-Server,
        // siehe SentMailArchiver). Der Regelfall ist bereits oben erledigt: die
        // Ausgangsmail steht mit derselben Message-ID lokal in der DB, die
        // Deduplizierung greift, und das originale HTML bleibt unangetastet.
        //
        // Kommen wir hier trotzdem an, ist die Mail NICHT lokal gespeichert
        // worden — die Archivierung ist also fehlgeschlagen. Dann wird sie
        // bewusst importiert: eine Mail mit vom Server veraendertem Layout ist
        // deutlich besser als eine, die im ERP gar nicht auftaucht. Der Marker
        // dient hier als Diagnose, nicht als Filter.
        if (EmailService.ERP_ORIGIN_WERT.equals(firstHeader(msg, EmailService.ERP_ORIGIN_HEADER))) {
            log.warn("[EmailImport] Eigene Ausgangsmail {} war nicht lokal archiviert "
                    + "und wird aus dem Sent-Ordner nachgeholt — Archivierung pruefen", messageId);
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
        // Eigene gesendete Mails gelten automatisch als gelesen.
        if (direction == EmailDirection.OUT) {
            email.setRead(true);
        }

        // Absender
        Address[] fromArr = msg.getFrom();
        if (fromArr != null && fromArr.length > 0) {
            if (fromArr[0] instanceof InternetAddress ia) {
                email.setFromAddress(ia.getAddress());
            } else {
                email.setFromAddress(fromArr[0].toString());
            }
        }

        // Blacklist-Check: Gesperrte Absender werden bereits beim Import komplett
        // verworfen — keine DB-Zeile, keine Spam-Klassifikation, kein ML-Training.
        // Damit pollutet der Absender weder Posteingang noch Bayes-Modell.
        // Hinweis: Mail bleibt auf dem IMAP-Server liegen (Folder ist READ_ONLY);
        // der Scheduler-Tick wiederholt den Check pro Mail. Daher debug-Level,
        // damit kein Log-Müll entsteht. Subject wird bewusst weggelassen (DSGVO).
        if (direction == EmailDirection.IN && email.getFromAddress() != null
                && emailBlacklistRepository.existsByEmailAddress(
                        email.getFromAddress().toLowerCase(java.util.Locale.ROOT))) {
            log.debug("[EmailImport] Absender {} ist gesperrt – Mail wird verworfen (kein DB-Eintrag)",
                    email.getFromAddress());
            return false;
        }

        // Empfänger
        email.setRecipient(extractAddresses(msg.getRecipients(Message.RecipientType.TO)));
        email.setCc(extractAddresses(msg.getRecipients(Message.RecipientType.CC)));

        // Reply-To (für Spam-Filter: From ≠ Reply-To ist klassischer Phishing-Marker).
        // jakarta.mail.Message#getReplyTo() liefert per Spec From zurück, wenn der
        // Reply-To-Header NICHT gesetzt ist. Wir wollen aber nur den echten Header
        // persistieren, damit das Feld semantisch sauber bleibt (für ML-Features später).
        if (firstHeader(msg, "Reply-To") != null) {
            try {
                Address[] replyTo = msg.getReplyTo();
                if (replyTo != null && replyTo.length > 0 && replyTo[0] instanceof InternetAddress ria) {
                    email.setReplyToAddress(ria.getAddress());
                }
            } catch (MessagingException ignored) {
                // Reply-To ist optional
            }
        }

        // Authentication-Results (SPF, DKIM, DMARC) für strukturelle Spam-Erkennung
        String authResults = joinHeaders(msg, "Authentication-Results");
        if (authResults != null && !authResults.isBlank()) {
            // Auf 2000 Zeichen begrenzen — manche Mailserver hängen sehr lange Strings an
            email.setAuthenticationResults(authResults.length() > 2000
                    ? authResults.substring(0, 2000)
                    : authResults);
        }

        // Subject
        email.setSubject(msg.getSubject());

        // Datum
        if (msg.getSentDate() != null) {
            email.setSentAt(msg.getSentDate().toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime());
        }

        // Parent-Email via In-Reply-To oder References Header finden.
        // Fallback: Subject-Matching (z.B. t-online-Client setzt In-Reply-To
        // nicht zuverlässig, oder die Original-Message-ID liegt nicht in der DB).
        Email parentEmail = findParentEmail(msg);
        if (parentEmail == null) {
            parentEmail = findParentBySubject(email);
            if (parentEmail != null) {
                log.info("[EmailImport] Parent via Subject-Fallback gefunden für '{}' → '{}'",
                        email.getSubject(), parentEmail.getSubject());
            }
        }
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
                // Bewusst KEINE Vererbung des Lieferanten vom Parent: Wer geschrieben hat,
                // sagt allein die Absender-Adresse (oben geprüft). Antwortet der Kunde in
                // einem Thread, in dem wir vorher einen Lieferanten angeschrieben haben,
                // wäre sein Anhang sonst eine Eingangsrechnung dieses Lieferanten.
            }
        }

        // Auto-Reply-Header für OOO-Loop-Schutz puffern (vor dem späteren Versand)
        String autoSubmittedHeader = firstHeader(msg, "Auto-Submitted");
        String precedenceHeader = firstHeader(msg, "Precedence");
        String listIdHeader = firstHeader(msg, "List-Id");

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

        // Abwesenheitsnotiz (OOO) – nur für eingehende Mails, die nach
        // Klassifikation NICHT als Spam/Newsletter markiert wurden und vom
        // einem Kunden stammen. Greift in allen IMAP-Ordnern. Dedup pro
        // Absender + Plan erfolgt im Responder selbst.
        if (direction == EmailDirection.IN
                && !email.isSpam()
                && !email.isNewsletter()) {
            try {
                outOfOfficeResponder.handleIncomingEmail(new OutOfOfficeResponder.IncomingMail(
                        email.getFromAddress(),
                        email.getSubject(),
                        email.getSentAt(),
                        email.isSpam(),
                        email.isNewsletter(),
                        autoSubmittedHeader,
                        precedenceHeader,
                        listIdHeader));
            } catch (Exception ex) {
                log.warn("[EmailImport] OOO-Auto-Reply für Email {} fehlgeschlagen: {}",
                        email.getId(), ex.getMessage());
            }
        }

        // Die Auswertung als Unzustellbarkeits-Meldung passiert bewusst erst
        // NACH dem Commit dieser Transaktion — siehe pruefeRuecklaeufer(..).

        return true;
    }

    /**
     * Liest den ersten Wert eines Headers; gibt null zurück wenn nicht vorhanden.
     */
    private String firstHeader(Message msg, String name) {
        try {
            String[] values = msg.getHeader(name);
            return (values != null && values.length > 0) ? values[0] : null;
        } catch (MessagingException e) {
            return null;
        }
    }

    /**
     * Liest alle Werte eines Headers und joined sie mit Newlines.
     * Authentication-Results kann mehrfach vorkommen (jeder MTA hängt seine an).
     */
    private String joinHeaders(Message msg, String name) {
        try {
            String[] values = msg.getHeader(name);
            if (values == null || values.length == 0) {
                return null;
            }
            return String.join("\n", values);
        } catch (MessagingException e) {
            return null;
        }
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
     * Subject-basiertes Fallback-Matching beim Live-Import: greift, wenn
     * {@link #findParentEmail(Message)} kein Header-Match liefert (Client setzt
     * {@code In-Reply-To} nicht, oder Original-Mail liegt ohne passende
     * Message-ID in der DB).
     *
     * <p>Verwendet dieselbe Normalisierung wie der Startup-Backfill
     * ({@link #normalizeSubject(String)} + {@link #isReplyOrForward(String)}),
     * scannt aber nur die {@link #subjectFallbackLookback} jüngsten Mails
     * vor {@code email.sentAt}, damit der Import nicht über die gesamte
     * Mailbox läuft.
     *
     * <p>Self-Match-Schutz: bei reimportierten Mails kann ein Kandidat
     * dieselbe ID haben — wird explizit ausgeschlossen.
     */
    Email findParentBySubject(Email email) {
        if (email == null || email.getSubject() == null || email.getSentAt() == null) {
            return null;
        }
        if (!isReplyOrForward(email.getSubject())) {
            return null;
        }
        String normalized = normalizeSubject(email.getSubject());
        if (normalized.isBlank()) {
            return null;
        }
        List<Email> candidates = emailRepository.findRecentBefore(
                email.getSentAt(),
                PageRequest.of(0, subjectFallbackLookback));
        Email best = null;
        for (Email candidate : candidates) {
            if (candidate.getSubject() == null || candidate.getSentAt() == null) {
                continue;
            }
            if (email.getId() != null && email.getId().equals(candidate.getId())) {
                continue;
            }
            if (!normalized.equals(normalizeSubject(candidate.getSubject()))) {
                continue;
            }
            if (best == null || candidate.getSentAt().isAfter(best.getSentAt())) {
                best = candidate;
            }
        }
        return best;
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

        // 2. Spam-Prüfung (berücksichtigt bereits die Zuordnung).
        //    WICHTIG: Erstkontakt-Lookup laeuft hier drinnen, deshalb erst
        //    DANACH die Domain als "schon gesehen" markieren — sonst wuerde
        //    die allererste Mail einer Domain ihren eigenen Erstkontakt-Bonus
        //    selbst entwerten.
        spamFilterService.analyzeAndMarkSpam(email);
        markDomainSeen(email);

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
     * Markiert die Absender-Domain als "schon gesehen" — fuer die
     * Erstkontakt-Heuristik im Spam-Filter. Nur eingehende Mails. Failures
     * (DB hin, parallele Imports) duerfen den Import-Pfad nicht abbrechen,
     * deshalb defensiv: alles was nicht passt, geht an log.debug.
     */
    private void markDomainSeen(Email email) {
        if (email.getDirection() != EmailDirection.IN) return;
        String domain = email.getSenderDomain();
        if (domain == null || domain.isBlank()) return;
        try {
            LocalDateTime firstSeen = email.getSentAt() != null ? email.getSentAt() : LocalDateTime.now();
            seenSenderDomainRepository.upsertSeen(domain, firstSeen);
        } catch (Exception e) {
            log.debug("[EmailImport] Erstkontakt-Tracking fuer Domain {} fehlgeschlagen: {}",
                    domain, e.getMessage());
        }
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

        // Index nach normalisiertem Subject (ohne AW:/RE:/FWD: Prefix).
        // Mails mit leerem Normalisat (z. B. Subject "RE:" allein) NICHT in den
        // Index aufnehmen — sonst wuerden zwei voellig unverbundene Mails
        // ohne echtes Subject faelschlich denselben Thread bilden.
        Map<String, List<Email>> byNormalizedSubject = new HashMap<>();
        for (Email email : allEmails) {
            String normalized = normalizeSubject(email.getSubject());
            if (normalized.isBlank()) {
                continue;
            }
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
            if (normalized.isBlank()) {
                continue;
            }
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
     * Erkennungs-Pattern für Reply-/Forward-Prefixe (case-insensitive).
     * Erfordert mindestens ein Whitespace nach dem Doppelpunkt — schliesst
     * exotische Subjects wie {@code "WG:Foo"} (ohne Space) aus, ohne echten
     * Mailclients in die Quere zu kommen (Outlook/Gmail/t-online setzen
     * immer ein Space). Wird in {@link #isReplyOrForward(String)} verwendet.
     */
    private static final String REPLY_FWD_DETECT_REGEX =
            "(?i)(aw|re|fwd|fw|wg|antw|antwort):\\s+";

    /**
     * Strip-Pattern für die Normalisierung: lockerer als das Detect-Pattern,
     * weil hier bereits feststeht, dass es sich um eine Reply handelt
     * (Aufrufer prüft vorher mit Detect-Pattern). Erlaubt deshalb auch
     * trailing-getrimmte Subjects wie {@code "RE:"} → {@code ""}.
     */
    private static final String REPLY_FWD_STRIP_REGEX =
            "(?i)(aw|re|fwd|fw|wg|antw|antwort):\\s*";

    /** Klammer-Prefix wie {@code [Ticket#1234]} oder {@code [External]} am Anfang. */
    private static final String BRACKET_PREFIX_REGEX = "\\[[^\\]]+\\]\\s*";

    /**
     * Prüft ob ein Betreff eine Antwort oder Weiterleitung ist.
     *
     * <p>Toleriert führende Klammer-Tags wie {@code [Ticket#…]}:
     * sowohl {@code "RE: Foo"} als auch {@code "[Ticket#1] RE: Foo"} und
     * {@code "RE: [Ticket#1] Foo"} werden korrekt als Reply erkannt.
     */
    boolean isReplyOrForward(String subject) {
        if (subject == null)
            return false;
        String s = subject.trim();
        // Klammer-Tags am Anfang abschälen (z. B. "[Ticket#…] RE: …")
        while (s.startsWith("[")) {
            int end = s.indexOf(']');
            if (end < 0)
                break;
            s = s.substring(end + 1).trim();
        }
        return s.matches("(?is)^" + REPLY_FWD_DETECT_REGEX + ".*");
    }

    /**
     * Normalisiert Betreff für Subject-basiertes Thread-Matching:
     * Entfernt AW/RE/FWD/WG/Antw-Prefixe (auch wenn sie nach einem
     * {@code [Ticket#…]}-Klammer-Tag stehen) und lowercased das Ergebnis.
     *
     * <p>Klammer-Tags selbst bleiben erhalten, damit Mails zu unterschiedlichen
     * Tickets nicht fälschlich in denselben Thread fallen.
     *
     * <p>Beispiele (vereinheitlichen sich auf {@code "[ticket#1] transfer"}):
     * <ul>
     *   <li>{@code "[Ticket#1] Transfer"}</li>
     *   <li>{@code "RE: [Ticket#1] Transfer"}</li>
     *   <li>{@code "[Ticket#1] RE: Transfer"}</li>
     *   <li>{@code "AW: [Ticket#1] Re: Transfer"}</li>
     * </ul>
     */
    String normalizeSubject(String subject) {
        if (subject == null)
            return "";
        String s = subject.trim();
        // Wechselweise Reply-Prefix am Anfang ODER nach Klammer-Tag entfernen,
        // bis sich nichts mehr ändert. So werden auch mehrfach verschachtelte
        // Kombinationen ("RE: [Ticket#1] AW: …") sauber aufgelöst.
        String prev;
        do {
            prev = s;
            // 1) Reply-Prefix direkt am Anfang: "RE: rest"
            s = s.replaceFirst("(?i)^" + REPLY_FWD_STRIP_REGEX, "").trim();
            // 2) Reply-Prefix unmittelbar nach einem Klammer-Tag: "[Ticket#1] RE: rest"
            //    Ersetze nur den Reply-Prefix, behalte das Klammer-Tag.
            s = s.replaceFirst(
                    "(?i)^(" + BRACKET_PREFIX_REGEX + ")" + REPLY_FWD_STRIP_REGEX,
                    "$1").trim();
        } while (!s.equals(prev));
        return s.toLowerCase();
    }

    /**
     * Löscht eine E-Mail permanent vom IMAP-Server.
     * IMAP-Zugangsdaten werden – wie beim Import – aus den System-Einstellungen (DB)
     * gelesen, nicht mehr aus Umgebungsvariablen.
     */
    public void deleteEmailFromServer(Email email) {
        if (email.getMessageId() == null) {
            log.warn("[EmailDeletion] Email {} hat null Message-ID, überspringe Server-Löschung", email.getId());
            return;
        }

        String user = systemSettingsService.getImapUsername();
        String pass = systemSettingsService.getImapPassword();
        String host = systemSettingsService.getImapHost();
        int port = systemSettingsService.getImapPort();
        if (user == null || user.isBlank() || pass == null || pass.isBlank() || host == null || host.isBlank()) {
            log.debug("[EmailDeletion] IMAP-Zugangsdaten fehlen (System-Einstellungen), überspringe Server-Löschung");
            return;
        }

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "5000");
        props.put("mail.imaps.timeout", "5000");

        try {
            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            if (port > 0) {
                store.connect(host, port, user, pass);
            } else {
                store.connect(host, user, pass);
            }

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
