package org.example.kalkulationsprogramm.service.mail;

import java.io.ByteArrayOutputStream;
import java.util.Properties;
import java.util.UUID;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.config.LocalTestMailPolicy;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.ServerZugang;
import org.example.kalkulationsprogramm.dto.Einkauf.MailkontoDto.Verschluesselung;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Nachricht;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Status;
import org.example.kalkulationsprogramm.dto.Einkauf.MailTransportDto.Testverbindung;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sun.mail.smtp.SMTPTransport;
import com.sun.mail.smtp.SMTPAddressFailedException;

import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

/** Account-aware SMTP and IMAP transport. SMTP and archival outcomes are deliberately independent. */
@Component
@Slf4j
public class KontoMailTransport {
    private final LocalTestMailPolicy localTestMailPolicy;
    private final int maxMimeBytes;

    @Autowired
    public KontoMailTransport(LocalTestMailPolicy localTestMailPolicy,
            @Value("${einkauf.mail.max-mime-bytes:20971520}") int maxMimeBytes) {
        this.localTestMailPolicy = java.util.Objects.requireNonNull(localTestMailPolicy, "localTestMailPolicy");
        if (maxMimeBytes < 1) throw new IllegalArgumentException("Das MIME-Limit muss positiv sein.");
        this.maxMimeBytes = maxMimeBytes;
    }

    KontoMailTransport(LocalTestMailPolicy localTestMailPolicy) { this(localTestMailPolicy, 20 * 1024 * 1024); }

    public byte[] vorbereiten(MailkontoService.KontoZugang konto, Nachricht nachricht) {
        if (konto == null || nachricht == null) throw new IllegalArgumentException("Mailkonto und Nachricht sind erforderlich.");
        try {
            String messageId = nachricht.messageId();
            if (messageId == null || messageId.isBlank()) messageId = "<" + UUID.randomUUID() + "@erp.local>";
            if (!messageId.startsWith("<") || !messageId.endsWith(">")) {
                throw new IllegalArgumentException("Die Message-ID ist ungültig.");
            }
            var recipients = jakarta.mail.internet.InternetAddress.parse(nachricht.to(), true);
            if (recipients.length != 1 || recipients[0].isGroup()) {
                throw new jakarta.mail.internet.AddressException("Genau eine Empfängeradresse ist erforderlich.");
            }
            Session session = Session.getInstance(new Properties());
            MimeMessage message = EmailService.baueMimeNachricht(session, konto.fromAddress(), konto.fromName(),
                    nachricht.to(), nachricht.subject(), nachricht.html(), nachricht.inReplyTo(),
                    nachricht.references(), nachricht.anlagen());
            message.setSentDate(new java.util.Date());
            message.saveChanges();
            message.setHeader("Message-ID", messageId);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            message.writeTo(output);
            if (output.size() > maxMimeBytes) throw new IllegalArgumentException("Die gesamte E-Mail überschreitet das MIME-Limit.");
            return output.toByteArray();
        } catch (EmailService.AnlageValidierungsException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Die E-Mail konnte nicht vorbereitet werden.", ex);
        }
    }

    public MailTransportDto.Versandergebnis senden(MailkontoService.KontoZugang konto, Nachricht nachricht) {
        byte[] mime;
        try {
            mime = vorbereiten(konto, nachricht);
        } catch (RuntimeException ex) {
            return new MailTransportDto.Versandergebnis(Status.SICHER_FEHLGESCHLAGEN,
                    nachricht == null ? null : nachricht.messageId(), "MIME_UNGUELTIG", null);
        }
        return sendenVorbereitet(konto, mime);
    }

    /** Sends exactly the supplied MIME bytes. Once sendMessage starts, failures are conservatively UNKLAR. */
    public MailTransportDto.Versandergebnis sendenVorbereitet(MailkontoService.KontoZugang konto, byte[] mime) {
        String messageId = null;
        SMTPTransport transport = null;
        try {
            pruefeKonto(konto);
            localTestMailPolicy.pruefeNetzwerkzugriff(konto.id());
            if (mime == null || mime.length == 0) throw new IllegalArgumentException("MIME-Inhalt fehlt.");
            Session session = Session.getInstance(smtpEigenschaften(konto.smtp()));
            MimeMessage message = EmailService.ausEingefrorenemMime(session, mime);
            messageId = message.getMessageID();
            transport = (SMTPTransport) session.getTransport(konto.smtp().tls() == Verschluesselung.TLS ? "smtps" : "smtp");
            ServerZugang smtp = konto.smtp();
            transport.connect(smtp.host(), smtp.port(), smtp.username(), smtp.password());
        } catch (Exception ex) {
            schliesse(transport);
            log.info("[EinkaufMail] SMTP-Verbindung vor DATA fehlgeschlagen: {}", ex.getClass().getSimpleName());
            return new MailTransportDto.Versandergebnis(Status.SICHER_FEHLGESCHLAGEN, messageId, fehlerCode(ex, "SMTP_VERBINDUNG"), mime);
        }
        try {
            MimeMessage message = EmailService.ausEingefrorenemMime(Session.getInstance(new Properties()), mime);
            messageId = message.getMessageID();
            transport.sendMessage(message, message.getAllRecipients());
            return new MailTransportDto.Versandergebnis(Status.ANGENOMMEN, messageId, null, mime);
        } catch (Exception ex) {
            if (sicherVorDataAbgewiesen(ex)) {
                log.info("[EinkaufMail] Empfänger vor DATA abgelehnt für Message-ID {}", messageId);
                return new MailTransportDto.Versandergebnis(Status.SICHER_FEHLGESCHLAGEN,
                        messageId, "SMTP_EMPFAENGER_ABGELEHNT", mime);
            }
            log.warn("[EinkaufMail] SMTP-Ausgang unklar für Message-ID {}: {}", messageId, ex.getClass().getSimpleName());
            return new MailTransportDto.Versandergebnis(Status.UNKLAR, messageId, "SMTP_ANTWORT_UNKLAR", mime);
        } finally {
            schliesse(transport);
        }
    }

    /** Authenticates to both services without issuing SMTP DATA or sending a message. */
    public Testverbindung pruefeVerbindung(MailkontoService.KontoZugang konto) {
        boolean smtpOk = false;
        boolean imapOk = false;
        Transport smtp = null;
        Store imapStore = null;
        String error = null;
        try {
            pruefeKonto(konto);
            localTestMailPolicy.pruefeNetzwerkzugriff(konto.id());
            Session smtpSession = Session.getInstance(smtpEigenschaften(konto.smtp()));
            smtp = smtpSession.getTransport(konto.smtp().tls() == Verschluesselung.TLS ? "smtps" : "smtp");
            smtp.connect(konto.smtp().host(), konto.smtp().port(), konto.smtp().username(), konto.smtp().password());
            smtpOk = true;
        } catch (Exception ex) {
            error = fehlerCode(ex, "SMTP_VERBINDUNG");
            log.info("[EinkaufMail] SMTP-Verbindungstest fehlgeschlagen: {}", ex.getClass().getSimpleName());
        }
        finally { try { if (smtp != null && smtp.isConnected()) smtp.close(); } catch (Exception ignored) { } }
        try {
            localTestMailPolicy.pruefeNetzwerkzugriff(konto.id());
            Session imapSession = Session.getInstance(imapEigenschaften(konto.imap()));
            imapStore = imapSession.getStore(konto.imap().tls() == Verschluesselung.TLS ? "imaps" : "imap");
            imapStore.connect(konto.imap().host(), konto.imap().port(), konto.imap().username(), konto.imap().password());
            imapOk = true;
        } catch (Exception ex) {
            if (error == null) error = fehlerCode(ex, "IMAP_VERBINDUNG");
            log.info("[EinkaufMail] IMAP-Verbindungstest fehlgeschlagen: {}", ex.getClass().getSimpleName());
        }
        finally { try { if (imapStore != null && imapStore.isConnected()) imapStore.close(); } catch (Exception ignored) { } }
        return new Testverbindung(smtpOk, imapOk, error);
    }

    private boolean sicherVorDataAbgewiesen(Exception fehler) {
        java.util.Set<Throwable> gesehen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Throwable aktuell = fehler;
        while (aktuell != null && gesehen.add(aktuell)) {
            if (aktuell instanceof SMTPAddressFailedException abgelehnt) {
                String kommando = abgelehnt.getCommand();
                int code = abgelehnt.getReturnCode();
                if (kommando != null && kommando.regionMatches(true, 0, "RCPT", 0, 4)
                        && code >= 400 && code < 600) return true;
            }
            if (aktuell instanceof jakarta.mail.MessagingException mailFehler
                    && mailFehler.getNextException() != null) aktuell = mailFehler.getNextException();
            else aktuell = aktuell.getCause();
        }
        return false;
    }

    private Properties smtpEigenschaften(ServerZugang zugang) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "30000");
        props.put("mail.smtp.writetimeout", "30000");
        props.put("mail.smtp.ssl.checkserveridentity", "true");
        props.put("mail.smtps.auth", "true");
        props.put("mail.smtps.connectiontimeout", "15000");
        props.put("mail.smtps.timeout", "30000");
        props.put("mail.smtps.writetimeout", "30000");
        props.put("mail.smtps.ssl.checkserveridentity", "true");
        if (zugang.tls() == Verschluesselung.STARTTLS) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        } else {
            props.put("mail.smtps.ssl.enable", "true");
        }
        return props;
    }

    private Properties imapEigenschaften(ServerZugang zugang) {
        Properties props = new Properties();
        String prefix = zugang.tls() == Verschluesselung.TLS ? "mail.imaps" : "mail.imap";
        props.put(prefix + ".connectiontimeout", "15000");
        props.put(prefix + ".timeout", "30000");
        props.put(prefix + ".ssl.checkserveridentity", "true");
        if (zugang.tls() == Verschluesselung.STARTTLS) {
            props.put("mail.imap.starttls.enable", "true");
            props.put("mail.imap.starttls.required", "true");
        } else props.put("mail.imaps.ssl.enable", "true");
        return props;
    }

    private void pruefeKonto(MailkontoService.KontoZugang konto) {
        if (konto == null || !konto.aktiv() || konto.id() == null) throw new IllegalArgumentException("Das Mailkonto ist nicht aktiv.");
        pruefeServer(konto.smtp());
        pruefeServer(konto.imap());
        if (konto.sent() == null || konto.sent().isBlank()) throw new IllegalArgumentException("Der Gesendet-Ordner fehlt.");
    }

    private void pruefeServer(ServerZugang server) {
        if (server == null || server.host() == null || server.host().isBlank() || server.username() == null
                || server.username().isBlank() || server.password() == null || server.password().isBlank()
                || server.port() < 1 || server.port() > 65535 || server.tls() == null) {
            throw new IllegalArgumentException("Die Mailkonto-Verbindung ist unvollständig.");
        }
    }

    private String fehlerCode(Exception ex, String fallback) {
        if (ex instanceof jakarta.mail.AuthenticationFailedException) return "AUTHENTIFIZIERUNG_FEHLGESCHLAGEN";
        if (ex instanceof IllegalArgumentException) return "KONTO_UNGUELTIG";
        return fallback;
    }

    private void schliesse(Transport transport) {
        try { if (transport != null && transport.isConnected()) transport.close(); }
        catch (Exception ex) { log.debug("[EinkaufMail] SMTP-Verbindung konnte nicht sauber geschlossen werden: {}", ex.getClass().getSimpleName()); }
    }

}
