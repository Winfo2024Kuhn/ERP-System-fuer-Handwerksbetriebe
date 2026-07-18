package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailAttachment
import org.example.kalkulationsprogramm.domain.EmailDirection
import org.example.kalkulationsprogramm.domain.Projekt
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.util.UUID

@Service
open class ProjektEmailArchivService(
    private val emailRepository: EmailRepository,
    @Value("\${file.mail-attachment-dir}") mailAttachmentDir: String,
) {
    private val mailAttachmentDir: Path = Path.of(mailAttachmentDir).toAbsolutePath().normalize()

    init {
        require(mailAttachmentDir.isNotBlank()) { "file.mail-attachment-dir darf nicht leer sein" }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    open fun archiviereVersandteEmail(
        projekt: Projekt,
        empfaenger: String?,
        absender: String?,
        subject: String?,
        htmlBody: String?,
        messageId: String?,
        pdfQuelle: Path,
        dateiname: String?,
    ): Email {
        val sichererDateiname = sichereDateikomponente(dateiname)
        val gespeicherterDateiname = "${UUID.randomUUID()}_$sichererDateiname"
        val ziel = mailAttachmentDir.resolve(gespeicherterDateiname).normalize()
        require(ziel.startsWith(mailAttachmentDir)) { "Ungueltiger Attachment-Pfad" }

        try {
            Files.createDirectories(mailAttachmentDir)
            Files.copy(pdfQuelle, ziel, StandardCopyOption.REPLACE_EXISTING)
            registriereRollbackBereinigung(ziel)

            val email = Email().apply {
                assignToProjekt(projekt)
                fromAddress = absender
                extractSenderDomain()
                recipient = empfaenger
                this.subject = subject
                htmlBody?.let {
                    this.htmlBody = it
                    rawBody = it
                    body = EmailHtmlSanitizer.htmlToPlainText(it)
                }
                sentAt = LocalDateTime.now()
                direction = EmailDirection.OUT
                isRead = true
                this.messageId = messageId
            }
            email.addAttachment(
                EmailAttachment().apply {
                    originalFilename = sichererDateiname
                    storedFilename = gespeicherterDateiname
                    sizeBytes = Files.size(ziel)
                    mimeType = "application/pdf"
                },
            )
            return emailRepository.save(email)
        } catch (e: Exception) {
            loescheStill(ziel)
            throw IllegalStateException("Projekt-E-Mail konnte nicht archiviert werden", e)
        }
    }

    private fun registriereRollbackBereinigung(ziel: Path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        loescheStill(ziel)
                    }
                }
            },
        )
    }

    private fun sichereDateikomponente(dateiname: String?): String {
        val wert = dateiname ?: "Mahnung.pdf"
        val sicher = wert.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return sicher.ifBlank { "Mahnung.pdf" }
    }

    private fun loescheStill(datei: Path) {
        runCatching { Files.deleteIfExists(datei) }
    }
}
