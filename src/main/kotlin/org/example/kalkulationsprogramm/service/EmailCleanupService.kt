package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class EmailCleanupService(
    private val emailRepository: EmailRepository,
    private val emailImportService: EmailImportService,
    private val spamBayesService: SpamBayesService,
) {
    @Value("\${email.features.enabled:true}")
    private var emailFeaturesEnabled: Boolean = true

    /**
     * Bereinigt Papierkorb, Spam und Newsletter: Loescht Elemente, die aelter als 30
     * Tage sind.
     * Laeuft taeglich um 03:00 Uhr.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun cleanupEmails() {
        if (!emailFeaturesEnabled) {
            return
        }
        log.info("Starte Email-Bereinigung (Trash/Spam/Newsletter > 30 Tage)...")

        val retentionLimit = LocalDateTime.now().minusDays(30)
        var deletedCount = 0
        var errorCount = 0

        val trashEmails = emailRepository.findByDeletedAtIsNotNullOrderByDeletedAtDesc()
        for (email in trashEmails) {
            val deletedAt = readField(email, "deletedAt") as? LocalDateTime ?: continue
            if (deletedAt.isBefore(retentionLimit)) {
                val spamScore = readField(email, "spamScore") as? Int ?: 0
                val spam = readField(email, "spam") as? Boolean ?: false
                if (spamScore >= 85 && !spam) {
                    writeField(email, "spam", true)
                    emailRepository.save(email)
                    log.debug(
                        "[EmailCleanup] Email {} mit spamScore={} vor Loeschung als Spam klassifiziert",
                        readField(email, "id"),
                        spamScore,
                    )
                }
                if (deleteEmail(email)) deletedCount++ else errorCount++
            }
        }

        val spamEmails = emailRepository.findSpam()
        for (email in spamEmails) {
            if (isAssigned(email)) {
                log.debug("Email-Cleanup uebersprungen (zugeordnet): id={}", readField(email, "id"))
                continue
            }
            val sentAt = readField(email, "sentAt") as? LocalDateTime
            if (sentAt != null && sentAt.isBefore(retentionLimit)) {
                if (deleteEmail(email)) deletedCount++ else errorCount++
            }
        }

        val newsletters = emailRepository.findNewsletter()
        for (email in newsletters) {
            if (isAssigned(email)) {
                log.debug("Email-Cleanup uebersprungen (zugeordnet): id={}", readField(email, "id"))
                continue
            }
            val sentAt = readField(email, "sentAt") as? LocalDateTime
            if (sentAt != null && sentAt.isBefore(retentionLimit)) {
                if (deleteEmail(email)) deletedCount++ else errorCount++
            }
        }

        log.info("Email-Bereinigung abgeschlossen. Geloescht: {}, Fehler: {}", deletedCount, errorCount)
    }

    /**
     * Implizites Ham-Training: Emails die mindestens 2 Monate im Posteingang geblieben sind
     * ohne als Spam markiert zu werden, werden einmalig als Ham trainiert.
     * Laeuft taeglich um 03:10 Uhr (nach dem Cleanup-Job).
     */
    @Scheduled(cron = "0 10 3 * * *")
    @Transactional
    fun trainImplicitHam() {
        if (!emailFeaturesEnabled) {
            return
        }
        if (!spamBayesService.isModelReady) {
            log.debug("[ImplizitHAM] Modell noch nicht bereit, implizites Training uebersprungen")
            return
        }

        val cutoff = LocalDateTime.now().minusMonths(2)
        val candidates = emailRepository.findLongLivedInboxEmailsWithoutVerdict(cutoff)

        if (candidates.isEmpty()) {
            return
        }

        var trained = 0
        for (email in candidates) {
            try {
                spamBayesService.train(email, false)
                writeField(email, "userSpamVerdict", "HAM_IMPLICIT")
                trained++
            } catch (e: Exception) {
                log.warn("[ImplizitHAM] Fehler beim Training fuer Email {}: {}", readField(email, "id"), e.message)
            }
        }
        emailRepository.saveAll(candidates)
        log.info("[ImplizitHAM] {} Posteingang-Emails (>2 Monate) implizit als Ham trainiert", trained)
    }

    private fun deleteEmail(email: Email): Boolean {
        return try {
            emailImportService.deleteEmailFromServer(email)

            val id = readField(email, "id") as? Long
            if (id != null) {
                emailRepository.detachRepliesFromParent(id)
                emailRepository.flush()
            }

            emailRepository.delete(email)
            true
        } catch (e: Exception) {
            log.error("Fehler beim Loeschen von Email {}: {}", readField(email, "id"), e.message)
            false
        }
    }

    private fun isAssigned(email: Email): Boolean =
        readField(email, "lieferant") != null ||
            readField(email, "projekt") != null ||
            readField(email, "anfrage") != null

    private fun readField(target: Any, fieldName: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(target)
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        return null
    }

    private fun writeField(target: Any, fieldName: String, value: Any?) {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailCleanupService::class.java)
    }
}
