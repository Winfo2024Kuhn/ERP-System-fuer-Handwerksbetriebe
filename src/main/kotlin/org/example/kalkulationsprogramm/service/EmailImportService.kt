package org.example.kalkulationsprogramm.service

import com.sun.mail.imap.IMAPFolder
import jakarta.mail.Message
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailDirection
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class EmailImportService {
    @Scheduled(fixedDelay = 300_000)
    fun importNewEmails() {
        log.debug("Email import skipped by Kotlin service shell")
    }

    @Scheduled(fixedDelay = 3_600_000)
    fun deactivateExpiredOutOfOffice() {
        log.debug("Out-of-office cleanup skipped by Kotlin service shell")
    }

    fun doImport(): Int = 0

    fun triggerImport(): Int = doImport()

    fun importMessage(msg: Message, folder: IMAPFolder, direction: EmailDirection): Boolean = false

    fun backfillAttachmentFilenames(): Int = 0

    fun postProcessEmail(email: Email) {
        log.debug("Post-processing email {}", email.id)
    }

    fun reprocessSpam(): Int = 0

    fun getStats(): Map<String, Long> = emptyMap()

    fun backfillSteuerberaterEmails(): Int = 0

    fun backfillParentEmails(): Int = 0

    fun deleteEmailFromServer(email: Email) {
        log.debug("Server-side delete skipped for email {}", email.id)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailImportService::class.java)
    }
}
