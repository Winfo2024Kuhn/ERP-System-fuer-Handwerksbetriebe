package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
        Objects.requireNonNull(pdfQuelle, "PDF-Quelle fehlt");
        return archiviereVersandteEmail(projekt, empfaenger, absender, subject, htmlBody, messageId,
                List.of(new PdfAnhang(pdfQuelle, dateiname)));
    }

    /** Ein PDF-Anhang der archivierten Mail: Quelldatei und angezeigter Dateiname. */
    public record PdfAnhang(Path quelle, String dateiname)
    {
    }

    /**
     * Wie {@link #archiviereVersandteEmail(Projekt, String, String, String, String, String, Path, String)},
     * aber mit mehreren PDF-Anhängen (z. B. Auftragsbestätigung und Widerrufsbelehrung). Schlägt
     * ein Anhang fehl, werden alle bereits kopierten Dateien wieder entfernt.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Email archiviereVersandteEmail(Projekt projekt,
            String empfaenger,
            String absender,
            String subject,
            String htmlBody,
            String messageId,
            List<PdfAnhang> anhaenge)
    {
        Objects.requireNonNull(projekt, "Projekt fehlt");
        if (anhaenge == null || anhaenge.isEmpty() || anhaenge.stream().anyMatch(a -> a == null || a.quelle() == null))
        {
            throw new IllegalArgumentException("PDF-Quelle fehlt");
        }

        List<Path> kopiert = new ArrayList<>();
        try
        {
            Files.createDirectories(mailAttachmentDir);

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

            for (PdfAnhang anhang : anhaenge)
            {
                String sichererDateiname = sichereDateikomponente(anhang.dateiname());
                String gespeicherterDateiname = UUID.randomUUID() + "_" + sichererDateiname;
                Path ziel = mailAttachmentDir.resolve(gespeicherterDateiname).normalize();
                if (!ziel.startsWith(mailAttachmentDir))
                {
                    throw new IllegalArgumentException("Ungueltiger Attachment-Pfad");
                }
                Files.copy(anhang.quelle(), ziel, StandardCopyOption.REPLACE_EXISTING);
                kopiert.add(ziel);
                registriereRollbackBereinigung(ziel);

                EmailAttachment attachment = new EmailAttachment();
                attachment.setOriginalFilename(sichererDateiname);
                attachment.setStoredFilename(gespeicherterDateiname);
                attachment.setSizeBytes(Files.size(ziel));
                attachment.setMimeType("application/pdf");
                email.addAttachment(attachment);
            }

            return emailRepository.save(email);
        }
        catch (Exception e)
        {
            kopiert.forEach(ProjektEmailArchivService::loescheStill);
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
