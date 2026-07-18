package org.example.kalkulationsprogramm.service.mail

import jakarta.activation.DataHandler
import jakarta.activation.FileDataSource
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import org.example.kalkulationsprogramm.service.SystemSettingsService
import org.springframework.stereotype.Component
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Properties

@Component
class SmtpHtmlMailSender(
    private val systemSettingsService: SystemSettingsService,
) : HtmlMailSender {

    @Throws(MessagingException::class)
    override fun send(
        fromAddress: String?,
        toAddress: String?,
        subject: String?,
        htmlBody: String?,
        inlineAttachments: Map<String, File>,
    ) {
        if (toAddress.isNullOrBlank()) {
            return
        }

        val smtpHost = systemSettingsService.smtpHost
        val smtpPort = systemSettingsService.smtpPort
        val smtpUsername = systemSettingsService.smtpUsername
        val smtpPassword = systemSettingsService.smtpPassword

        val props = Properties()
        props["mail.smtp.host"] = smtpHost
        props["mail.smtp.port"] = smtpPort.toString()
        props["mail.smtp.auth"] = "true"
        props["mail.smtp.ssl.enable"] = "true"
        props["mail.smtp.socketFactory.port"] = smtpPort.toString()
        props["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(smtpUsername, smtpPassword)
            }
        })

        val message = MimeMessage(session)
        message.setFrom(InternetAddress(if (fromAddress.isNullOrBlank()) smtpUsername else fromAddress))
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress))
        message.setSubject(subject ?: "", StandardCharsets.UTF_8.name())

        val mixed = MimeMultipart("mixed")
        val relatedHolder = MimeBodyPart()
        val related = MimeMultipart("related")
        relatedHolder.setContent(related)
        mixed.addBodyPart(relatedHolder)

        val htmlPart = MimeBodyPart()
        htmlPart.setContent(htmlBody ?: "", "text/html; charset=utf-8")
        related.addBodyPart(htmlPart)

        inlineAttachments.forEach { (contentId, file) ->
            val attachment = MimeBodyPart()
            attachment.dataHandler = DataHandler(FileDataSource(file))
            attachment.setHeader("Content-ID", "<$contentId>")
            attachment.disposition = MimeBodyPart.INLINE
            related.addBodyPart(attachment)
        }

        message.setContent(mixed)
        Transport.send(message)
    }
}
