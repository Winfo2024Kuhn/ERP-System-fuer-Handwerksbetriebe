package org.example.email

import jakarta.activation.DataHandler
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import org.example.kalkulationsprogramm.service.SystemSettingsService
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.Properties

@Service
open class ImapAppendService(
    private val systemSettingsService: SystemSettingsService,
) {

    open fun appendToSent(
        from: String?,
        to: List<String>?,
        subject: String?,
        htmlBody: String?,
        attachments: List<File>?,
        sentAt: LocalDateTime?,
    ) {
        if (!systemSettingsService.isImapConfigured) {
            return
        }
        val user = systemSettingsService.imapUsername
        val pass = systemSettingsService.imapPassword
        val host = systemSettingsService.imapHost
        val port = systemSettingsService.imapPort

        try {
            val props = Properties()
            props["mail.store.protocol"] = "imaps"
            props["mail.imaps.ssl.enable"] = "true"
            props["mail.mime.address.strict"] = "false"
            val session = Session.getInstance(props)

            val message = MimeMessage(session)
            if (!from.isNullOrBlank()) {
                message.setFrom(InternetAddress(from))
            }
            if (!to.isNullOrEmpty()) {
                val tos = to
                    .filter { it.isNotBlank() }
                    .map { InternetAddress(it.trim()) }
                if (tos.isNotEmpty()) {
                    message.setRecipients(Message.RecipientType.TO, tos.toTypedArray())
                }
            }
            if (subject != null) {
                message.setSubject(subject, Charsets.UTF_8.name())
            }

            val mixed = MimeMultipart("mixed")
            val relatedHolder = MimeBodyPart()
            val related = MimeMultipart("related")
            relatedHolder.setContent(related)
            mixed.addBodyPart(relatedHolder)

            val htmlPart = MimeBodyPart()
            val body = htmlBody ?: ""
            htmlPart.setContent(body, "text/html; charset=utf-8")
            related.addBodyPart(htmlPart)

            if (body.contains("cid:Firmenlogo")) {
                ImapAppendService::class.java.getResourceAsStream("/static/firmenlogo.png").use { input ->
                    if (input != null) {
                        val logoPart = MimeBodyPart()
                        logoPart.dataHandler = DataHandler(ByteArrayDataSource(input, "image/png"))
                        logoPart.fileName = "image001.png"
                        logoPart.disposition = MimeBodyPart.INLINE
                        logoPart.setHeader("Content-ID", "<Firmenlogo>")
                        related.addBodyPart(logoPart)
                    }
                }
            }

            attachments?.forEach { file ->
                if (!file.exists()) return@forEach
                FileInputStream(file).use { input ->
                    val att = MimeBodyPart()
                    att.dataHandler = DataHandler(ByteArrayDataSource(input, guessMimeType(file.name)))
                    att.fileName = file.name
                    mixed.addBodyPart(att)
                }
            }
            message.setContent(mixed)
            if (sentAt != null) {
                message.sentDate = Date.from(sentAt.atZone(ZoneId.systemDefault()).toInstant())
            }
            message.saveChanges()

            session.getStore("imaps").use { store ->
                store.connect(host, port, user, pass)
                val sent = getSentFolder(store)
                if (sent != null) {
                    sent.open(Folder.READ_WRITE)
                    sent.appendMessages(arrayOf<Message>(message))
                    sent.close(false)
                }
            }
        } catch (_: Exception) {
            // Do not disrupt the mail sending flow when IMAP append fails.
        }
    }

    @Throws(MessagingException::class)
    private fun getSentFolder(store: Store): Folder? {
        val candidates = arrayOf("INBOX.Sent", "Sent", "Sent Items", "INBOX.Sent Items")
        for (name in candidates) {
            try {
                val folder = store.getFolder(name)
                if (folder != null && folder.exists()) {
                    return folder
                }
            } catch (_: MessagingException) {
            }
        }
        return findFolderRecursive(store.defaultFolder, "sent")
    }

    companion object {
        @JvmStatic
        @Throws(MessagingException::class)
        private fun findFolderRecursive(parent: Folder?, query: String): Folder? {
            if (parent == null) return null
            val children = try {
                parent.list()
            } catch (_: MessagingException) {
                parent.list("*")
            }
            val q = query.lowercase(Locale.ROOT)
            for (folder in children) {
                val full = folder.fullName.lowercase(Locale.ROOT)
                val name = folder.name.lowercase(Locale.ROOT)
                if (full == q || name == q || full.endsWith(q) || full.contains(q)) {
                    return folder
                }
                if (folder.type and Folder.HOLDS_FOLDERS != 0) {
                    val hit = findFolderRecursive(folder, q)
                    if (hit != null) return hit
                }
            }
            return null
        }

        @JvmStatic
        private fun guessMimeType(filename: String?): String {
            if (filename == null) return "application/octet-stream"
            val lower = filename.lowercase(Locale.ROOT)
            return when {
                lower.endsWith(".pdf") -> "application/pdf"
                lower.endsWith(".png") -> "image/png"
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
                lower.endsWith(".txt") -> "text/plain"
                lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
                else -> "application/octet-stream"
            }
        }
    }
}
