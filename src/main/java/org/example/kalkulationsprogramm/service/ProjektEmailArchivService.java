package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Archiviert bereits versendete Projekt-E-Mails samt Anhang im ERP. */
@Service
public class ProjektEmailArchivService
{
    private final EmailRepository emailRepository;
    private final Path mailAttachmentDir;

    public ProjektEmailArchivService(EmailRepository emailRepository,
            @Value("${file.mail-attachment-dir}") String mailAttachmentDir)
    {
        this.emailRepository = emailRepository;
        if (mailAttachmentDir == null || mailAttachmentDir.isBlank())
        {
            throw new IllegalArgumentException("file.mail-attachment-dir darf nicht leer sein");
        }
        this.mailAttachmentDir = Path.of(mailAttachmentDir).toAbsolutePath().normalize();
    }

    /**
     * Kopiert zuerst den Anhang und persistiert danach E-Mail und Metadaten in
     * einer Transaktion. Bei DB-/Commit-Fehlern wird die kopierte Datei wieder
     * entfernt, damit keine halbfertige Archivierung zurueckbleibt.
     *
     * <p>{@code REQUIRES_NEW}, damit ein Fehler hier (z.B. Lock-Timeout auf der
     * email-Tabelle durch den parallelen IMAP-Import) niemals die umgebende
     * Transaktion des Aufrufers rollback-only markiert — die SMTP-Mail ist zu
     * diesem Zeitpunkt bereits versendet, und z.B. das Versand-Datum der
     * Auto-AB muss den Archivierungsfehler ueberleben. Fuer den Mahnlauf
     * (kein umgebender TX-Kontext) aendert sich nichts.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Email archiviereVersandteEmail(Projekt projekt,
            String empfaenger,
            String absender,
            String subject,
            String htmlBody,
            String messageId,
            Path pdfQuelle,
            String dateiname)
    {
        Objects.requireNonNull(projekt, "Projekt fehlt");
        Objects.requireNonNull(pdfQuelle, "PDF-Quelle fehlt");

        String sichererDateiname = sichereDateikomponente(dateiname);
        String gespeicherterDateiname = UUID.randomUUID() + "_" + sichererDateiname;
        Path ziel = mailAttachmentDir.resolve(gespeicherterDateiname).normalize();
        if (!ziel.startsWith(mailAttachmentDir))
        {
            throw new IllegalArgumentException("Ungueltiger Attachment-Pfad");
        }

        try
        {
            Files.createDirectories(mailAttachmentDir);
            Files.copy(pdfQuelle, ziel, StandardCopyOption.REPLACE_EXISTING);
            registriereRollbackBereinigung(ziel);

            Email email = new Email();
            email.assignToProjekt(projekt);
            email.setFromAddress(absender);
            email.extractSenderDomain();
            email.setRecipient(empfaenger);
            email.setSubject(subject);
            email.setHtmlBody(htmlBody);
            email.setRawBody(htmlBody);
            email.setBody(EmailHtmlSanitizer.htmlToPlainText(htmlBody));
            email.setSentAt(LocalDateTime.now());
            email.setDirection(EmailDirection.OUT);
            email.setRead(true);
            email.setMessageId(messageId);

            EmailAttachment attachment = new EmailAttachment();
            attachment.setOriginalFilename(sichererDateiname);
            attachment.setStoredFilename(gespeicherterDateiname);
            attachment.setSizeBytes(Files.size(ziel));
            attachment.setMimeType("application/pdf");
            email.addAttachment(attachment);

            return emailRepository.save(email);
        }
        catch (Exception e)
        {
            loescheStill(ziel);
            throw new IllegalStateException("Projekt-E-Mail konnte nicht archiviert werden", e);
        }
    }

    private void registriereRollbackBereinigung(Path ziel)
    {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
        {
            @Override
            public void afterCompletion(int status)
            {
                if (status != TransactionSynchronization.STATUS_COMMITTED)
                {
                    loescheStill(ziel);
                }
            }
        });
    }

    private static String sichereDateikomponente(String dateiname)
    {
        String wert = dateiname == null ? "Mahnung.pdf" : dateiname;
        String sicher = wert.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sicher.isBlank() ? "Mahnung.pdf" : sicher;
    }

    private static void loescheStill(Path datei)
    {
        try
        {
            Files.deleteIfExists(datei);
        }
        catch (IOException ignored)
        {
            // Der urspruengliche Archivierungsfehler bleibt die Hauptursache.
        }
    }
}
