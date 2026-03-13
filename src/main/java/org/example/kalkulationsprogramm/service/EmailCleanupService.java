package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailCleanupService {

    @Value("${email.features.enabled:true}")
    private boolean emailFeaturesEnabled;

    private final EmailRepository emailRepository;
    private final EmailImportService emailImportService;

    /**
     * Bereinigt Papierkorb, Spam und Newsletter: Löscht Elemente, die älter als 30
     * Tage sind.
     * Läuft täglich um 03:00 Uhr.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupEmails() {
        if (!emailFeaturesEnabled) {
            return;
        }
        log.info("Starte Email-Bereinigung (Trash/Spam/Newsletter > 30 Tage)...");

        LocalDateTime retentionLimit = LocalDateTime.now().minusDays(30);
        int deletedCount = 0;
        int errorCount = 0;

        // 1. PAPIERKORB (Basis: DeletedAt)
        List<Email> trashEmails = emailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc();
        for (Email email : trashEmails) {
            if (email.getDeletedAt().isBefore(retentionLimit)) {
                if (deleteEmail(email))
                    deletedCount++;
                else
                    errorCount++;
            }
        }

        // 2. SPAM (Basis: SentAt) - Zugeordnete Emails NICHT löschen!
        List<Email> spamEmails = emailRepository.findSpam();
        for (Email email : spamEmails) {
            // Zugeordnete Emails überspringen
            if (email.getLieferant() != null || email.getProjekt() != null || email.getAnfrage() != null) {
                log.debug("Email-Cleanup übersprungen (zugeordnet): id={}", email.getId());
                continue;
            }
            if (email.getSentAt() != null && email.getSentAt().isBefore(retentionLimit)) {
                if (deleteEmail(email))
                    deletedCount++;
                else
                    errorCount++;
            }
        }

        // 3. NEWSLETTER (Basis: SentAt) - Zugeordnete Emails NICHT löschen!
        List<Email> newsletters = emailRepository.findNewsletter();
        for (Email email : newsletters) {
            // Zugeordnete Emails überspringen
            if (email.getLieferant() != null || email.getProjekt() != null || email.getAnfrage() != null) {
                log.debug("Email-Cleanup übersprungen (zugeordnet): id={}", email.getId());
                continue;
            }
            if (email.getSentAt() != null && email.getSentAt().isBefore(retentionLimit)) {
                if (deleteEmail(email))
                    deletedCount++;
                else
                    errorCount++;
            }
        }

        log.info("Email-Bereinigung abgeschlossen. Gelöscht: {}, Fehler: {}", deletedCount, errorCount);
    }

    private boolean deleteEmail(Email email) {
        try {
            // 1. Vom Server löschen
            emailImportService.deleteEmailFromServer(email);

            // 2. Aus DB löschen
            emailRepository.delete(email);
            return true;
        } catch (Exception e) {
            log.error("Fehler beim Löschen von Email {}: {}", email.getId(), e.getMessage());
            return false;
        }
    }
}
