package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailAttachment;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.repository.AnfrageDokumentRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Speichert eine gerade verschickte Mail aus dem E-Mail-Center: Zuordnung zu Projekt,
 * Anfrage oder Lieferant, Postfach und Anhänge.
 *
 * <p>Jede Mail in einer <strong>eigenen, kurzen Transaktion</strong>
 * ({@code REQUIRES_NEW}): Die Mail ist sofort festgeschrieben – der minütliche Abruf
 * erkennt ihre Gesendet-Kopie dann als bekannt, statt am Unique-Index der Message-ID zu
 * warten. Beim Einzelversand bricht ein Speicherfehler bei einem Empfänger die anderen
 * nicht ab und lässt die äußere Anfrage nicht scheitern.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AusgangsmailService {

    private final EmailRepository emailRepository;
    private final ProjektRepository projektRepository;
    private final AnfrageRepository anfrageRepository;
    private final LieferantenRepository lieferantenRepository;
    private final EmailLieferantVerknuepfungService emailLieferantVerknuepfungService;
    private final DateiSpeicherService dateiSpeicherService;
    private final ProjektDokumentRepository projektDokumentRepository;
    private final AnfrageDokumentRepository anfrageDokumentRepository;

    @Value("${file.mail-attachment-dir}")
    private String mailAttachmentDir;

    /** Anhänge einer Ausgangsmail: verknüpftes Dokument und hochgeladene Dateien. */
    public record Anhaenge(String dokumentGespeichert, String dokumentOriginal, String dokumentMimeType,
            MultipartFile[] dateien) {

        public static Anhaenge keine() {
            return new Anhaenge(null, null, null, null);
        }
    }

    /**
     * Alles, was zu einer verschickten Mail gespeichert wird.
     *
     * @param antwortAuf Original-Mail bei einer Antwort; liefert die Zuordnung, wenn keine
     *                   angegeben ist
     */
    public record Ausgangsmail(
            String messageId,
            String absender,
            EmailAbsender postfach,
            String empfaenger,
            String cc,
            String betreff,
            String htmlBody,
            Long projektId,
            Long anfrageId,
            Long lieferantId,
            Email antwortAuf,
            Anhaenge anhaenge) {
    }

    /**
     * Speichert die Mail. Dateifehler werden als {@link UncheckedIOException} gemeldet,
     * damit Aufrufer sie wie jeden anderen Speicherfehler behandeln können.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Email speichere(Ausgangsmail mail) {
        Email email = new Email();
        email.setMessageId(mail.messageId());
        email.setFromAddress(mail.absender());
        email.setRecipient(mail.empfaenger());
        email.setCc(mail.cc());
        email.setSubject(mail.betreff());
        email.setBody(EmailHtmlSanitizer.htmlToPlainText(mail.htmlBody()));
        email.setHtmlBody(mail.htmlBody());
        email.setRawBody(mail.htmlBody());
        email.setSentAt(LocalDateTime.now());
        email.setDirection(EmailDirection.OUT);
        email.setRead(true);
        email.setParentEmail(mail.antwortAuf());
        email.ordnePostfachZu(mail.postfach(), null, null);

        ordneZu(email, mail);

        // Der Lieferant kommt bewusst NICHT von der Original-Mail, sondern aus dem Empfänger:
        // Antworten wir in einem Verlauf dem Kunden, gehört die Mail nicht auf die Karte des
        // Lieferanten, den wir vorher im selben Verlauf angeschrieben haben.
        emailLieferantVerknuepfungService.verknuepfeAusEmpfaenger(email, mail.empfaenger(), mail.cc());

        // saveAndFlush, damit die Message-ID sofort steht und der Abruf die Gesendet-Kopie erkennt.
        emailRepository.saveAndFlush(email);

        try {
            speichereAnhaenge(email, mail.anhaenge() == null ? Anhaenge.keine() : mail.anhaenge());
        } catch (IOException e) {
            throw new UncheckedIOException("Anhänge der gesendeten Mail konnten nicht gespeichert werden.", e);
        }
        emailRepository.save(email);
        return email;
    }

    /** Versanddatum auf dem angehängten Projekt- bzw. Anfrage-Dokument setzen. */
    @Transactional
    public void vermerkeVersand(ProjektDokument projektDokument, AnfrageDokument anfrageDokument) {
        if (projektDokument != null) {
            projektDokument.setEmailVersandDatum(LocalDate.now());
            projektDokumentRepository.save(projektDokument);
            log.info("Versanddatum für ProjektDokument {} gesetzt", projektDokument.getId());
        }
        if (anfrageDokument != null) {
            anfrageDokument.setEmailVersandDatum(LocalDate.now());
            anfrageDokumentRepository.save(anfrageDokument);
            log.info("Versanddatum für AnfrageDokument {} gesetzt", anfrageDokument.getId());
        }
    }

    /** Ausdrückliche Zuordnung gewinnt, sonst die der beantworteten Mail. */
    private void ordneZu(Email email, Ausgangsmail mail) {
        if (mail.projektId() != null) {
            projektRepository.findById(mail.projektId()).ifPresent(email::assignToProjekt);
        } else if (mail.anfrageId() != null) {
            anfrageRepository.findById(mail.anfrageId()).ifPresent(email::assignToAnfrage);
        } else if (mail.lieferantId() != null) {
            lieferantenRepository.findById(mail.lieferantId()).ifPresent(email::assignToLieferant);
        } else if (mail.antwortAuf() != null) {
            Email original = mail.antwortAuf();
            if (original.getProjekt() != null) {
                email.assignToProjekt(original.getProjekt());
            } else if (original.getAnfrage() != null) {
                email.assignToAnfrage(original.getAnfrage());
            } else if (original.getLieferant() != null) {
                email.assignToLieferant(original.getLieferant());
            }
        }
    }

    /**
     * Legt verknüpftes Dokument und hochgeladene Dateien als Anhänge ab. Jede Mail bekommt
     * eigene Dateien – beim Einzelversand berührt das Löschen einer Mail die anderen nicht.
     */
    private void speichereAnhaenge(Email email, Anhaenge anhaenge) throws IOException {
        Path baseDir = Path.of(mailAttachmentDir);
        Files.createDirectories(baseDir);

        if (anhaenge.dokumentGespeichert() != null && anhaenge.dokumentOriginal() != null) {
            try {
                Resource resource = dateiSpeicherService.ladeDokumentAlsResource(anhaenge.dokumentGespeichert());
                if (resource != null && resource.exists()) {
                    Path dst = sichererZielpfad(baseDir, anhaenge.dokumentOriginal());
                    try (var in = resource.getInputStream()) {
                        Files.copy(in, dst, StandardCopyOption.REPLACE_EXISTING);
                    }
                    EmailAttachment att = new EmailAttachment();
                    att.setEmail(email);
                    att.setOriginalFilename(anhaenge.dokumentOriginal());
                    att.setStoredFilename(dst.getFileName().toString());
                    att.setSizeBytes(Files.size(dst));
                    att.setMimeType(anhaenge.dokumentMimeType());
                    email.addAttachment(att);
                    log.info("Dokument '{}' als EmailAttachment gespeichert ({})", anhaenge.dokumentOriginal(),
                            anhaenge.dokumentMimeType());
                }
            } catch (Exception e) {
                log.warn("Konnte Dokument nicht als EmailAttachment speichern: {}", e.getMessage());
            }
        }

        if (anhaenge.dateien() != null) {
            for (MultipartFile file : anhaenge.dateien()) {
                if (file.isEmpty()) {
                    continue;
                }
                String rohName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "attachment";
                Path dst = sichererZielpfad(baseDir, rohName);
                try (var in = file.getInputStream()) {
                    Files.copy(in, dst, StandardCopyOption.REPLACE_EXISTING);
                }
                EmailAttachment att = new EmailAttachment();
                att.setEmail(email);
                att.setOriginalFilename(file.getOriginalFilename());
                att.setStoredFilename(dst.getFileName().toString());
                att.setSizeBytes(file.getSize());
                att.setMimeType(file.getContentType());
                email.addAttachment(att);
            }
        }
    }

    /** Bereinigter Dateiname mit UUID-Präfix; bleibt garantiert im Anhang-Ordner. */
    static Path sichererZielpfad(Path baseDir, String rohName) {
        String sicher = Path.of(rohName).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_");
        Path dst = baseDir.resolve(UUID.randomUUID() + "_" + sicher).normalize();
        if (!dst.startsWith(baseDir.normalize())) {
            throw new IllegalArgumentException("Ungültiger Dateiname");
        }
        return dst;
    }
}
