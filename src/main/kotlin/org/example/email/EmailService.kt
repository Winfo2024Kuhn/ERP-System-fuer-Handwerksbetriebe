package org.example.email

import jakarta.activation.DataHandler
import jakarta.mail.Authenticator
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties

class EmailService(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
) {
    private val log = LoggerFactory.getLogger(EmailService::class.java)

    fun sendEmail(
        recipient: String?,
        cc: String?,
        fromAddress: String?,
        subject: String?,
        htmlBody: String?,
        attachmentFilePath: String?,
        attachmentFileName: String?,
    ) {
        val session = createSession()
        try {
            val message = buildMessage(session, recipient, cc, fromAddress, subject, htmlBody, attachmentFilePath, attachmentFileName)
            Transport.send(message)
            appendArchiveCopy(session, message, attachmentFileName)
            log.info("Email sent to {}", recipient)
        } catch (e: MessagingException) {
            log.error("Failed to send email to {}", recipient, e)
        } catch (e: IOException) {
            log.error("Failed to send email to {}", recipient, e)
        }
    }

    @Throws(MessagingException::class, IOException::class)
    fun sendEmailAndReturnMessageId(
        recipient: String?,
        cc: String?,
        fromAddress: String?,
        subject: String?,
        htmlBody: String?,
        attachmentFilePath: String?,
        attachmentFileName: String?,
    ): String {
        val session = createSession()
        val message = buildMessage(session, recipient, cc, fromAddress, subject, htmlBody, attachmentFilePath, attachmentFileName)
        message.saveChanges()
        val messageId = message.messageID
        Transport.send(message)
        return messageId
    }

    @Throws(MessagingException::class, IOException::class)
    fun sendEmailAndReturnMessageIdWithInline(
        recipient: String?,
        cc: String?,
        fromAddress: String?,
        subject: String?,
        htmlBody: String?,
        inlineCidToFile: Map<String, File>?,
        attachmentFilePath: String?,
        attachmentFileName: String?,
    ): String {
        val session = createSession()
        val message = buildMessage(session, recipient, cc, fromAddress, subject, htmlBody, attachmentFilePath, attachmentFileName, inlineCidToFile)
        message.saveChanges()
        val messageId = message.messageID
        Transport.send(message)
        return messageId
    }

    @Throws(MessagingException::class, IOException::class)
    fun sendEmailWithMultipleAttachments(
        recipient: String?,
        cc: String?,
        fromAddress: String?,
        subject: String?,
        htmlBody: String?,
        inlineCidToFile: Map<String, File>?,
        attachmentFilePaths: List<String>?,
    ): String {
        val session = createSession()
        val message = buildBaseMessage(session, recipient, cc, fromAddress, subject)
        val related = addHtmlContainer(message, htmlBody)
        addInlineParts(related, inlineCidToFile)
        val mixed = message.content as MimeMultipart
        attachmentFilePaths.orEmpty().forEach { path ->
            if (path.isNotBlank()) {
                val attachFile = File(path)
                if (attachFile.exists()) {
                    val attachmentPart = MimeBodyPart()
                    attachmentPart.attachFile(attachFile)
                    attachmentPart.fileName = attachFile.name
                    mixed.addBodyPart(attachmentPart)
                }
            }
        }
        message.saveChanges()
        val messageId = message.messageID
        Transport.send(message)
        return messageId
    }

    private fun createSession(): Session {
        val props = Properties()
        props["mail.smtp.host"] = host
        props["mail.smtp.port"] = port.toString()
        props["mail.smtp.auth"] = "true"
        props["mail.smtp.ssl.enable"] = "true"
        props["mail.smtp.socketFactory.port"] = port.toString()
        props["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication = PasswordAuthentication(username, password)
        })
    }

    @Throws(MessagingException::class, IOException::class)
    private fun buildMessage(
        session: Session,
        recipient: String?,
        cc: String?,
        fromAddress: String?,
        subject: String?,
        htmlBody: String?,
        attachmentFilePath: String?,
        attachmentFileName: String?,
        inlineCidToFile: Map<String, File>? = null,
    ): MimeMessage {
        val message = buildBaseMessage(session, recipient, cc, fromAddress, subject)
        val related = addHtmlContainer(message, htmlBody)
        addDefaultLogoIfReferenced(related, htmlBody)
        addInlineParts(related, inlineCidToFile)
        addAttachment(message.content as MimeMultipart, attachmentFilePath, attachmentFileName)
        return message
    }

    @Throws(MessagingException::class)
    private fun buildBaseMessage(session: Session, recipient: String?, cc: String?, fromAddress: String?, subject: String?): MimeMessage {
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(fromAddress ?: ""))
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient ?: ""))
        if (!cc.isNullOrBlank()) {
            message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc))
        }
        message.setSubject(subject ?: "", StandardCharsets.UTF_8.name())
        return message
    }

    @Throws(MessagingException::class)
    private fun addHtmlContainer(message: MimeMessage, htmlBody: String?): MimeMultipart {
        val mixed = MimeMultipart("mixed")
        val relatedHolder = MimeBodyPart()
        val related = MimeMultipart("related")
        relatedHolder.setContent(related)
        mixed.addBodyPart(relatedHolder)

        val htmlPart = MimeBodyPart()
        htmlPart.setContent(htmlBody ?: "", "text/html; charset=utf-8")
        related.addBodyPart(htmlPart)
        message.setContent(mixed)
        return related
    }

    @Throws(MessagingException::class, IOException::class)
    private fun addDefaultLogoIfReferenced(related: MimeMultipart, htmlBody: String?) {
        if (htmlBody?.contains("cid:Firmenlogo") != true) return
        EmailService::class.java.getResourceAsStream("/static/firmenlogo.png").use { input ->
            if (input == null) throw IOException("Logo /static/firmenlogo.png nicht im Klassenpfad gefunden.")
            val logoPart = MimeBodyPart()
            logoPart.dataHandler = DataHandler(ByteArrayDataSource(input, "image/png"))
            logoPart.fileName = "image001.png"
            logoPart.disposition = MimeBodyPart.INLINE
            logoPart.setHeader("Content-ID", "<Firmenlogo>")
            related.addBodyPart(logoPart)
        }
    }

    @Throws(MessagingException::class, IOException::class)
    private fun addInlineParts(related: MimeMultipart, inlineCidToFile: Map<String, File>?) {
        inlineCidToFile.orEmpty().forEach { (cid, file) ->
            if (cid.isBlank() || !file.exists()) return@forEach
            val ctype = try {
                Files.probeContentType(file.toPath())
            } catch (_: IOException) {
                null
            }.takeUnless { it.isNullOrBlank() } ?: "application/octet-stream"
            val inlinePart = MimeBodyPart()
            FileInputStream(file).use { input ->
                inlinePart.dataHandler = DataHandler(ByteArrayDataSource(input, ctype))
            }
            inlinePart.fileName = file.name
            inlinePart.disposition = MimeBodyPart.INLINE
            inlinePart.setHeader("Content-ID", "<$cid>")
            related.addBodyPart(inlinePart)
        }
    }

    @Throws(MessagingException::class, IOException::class)
    private fun addAttachment(mixed: MimeMultipart, attachmentFilePath: String?, attachmentFileName: String?) {
        if (attachmentFilePath.isNullOrBlank()) return
        val attachmentPart = MimeBodyPart()
        attachmentPart.attachFile(File(attachmentFilePath))
        if (!attachmentFileName.isNullOrBlank()) {
            attachmentPart.fileName = attachmentFileName
        }
        mixed.addBodyPart(attachmentPart)
    }

    private fun appendArchiveCopy(session: Session, message: Message, attachmentFileName: String?) {
        try {
            val store = session.getStore("imaps")
            store.connect("secureimap.t-online.de", 993, username, password)
            val filename = attachmentFileName?.lowercase(Locale.ROOT).orEmpty()
            val folderName = when {
                filename.contains("rechnung") -> "INBOX.Archives (2).Ausgangsrechnungen"
                filename.contains("auftragsbestaetigung") || filename.contains("auftragsbestätigung") -> "INBOX.Archives (2).Ausgangs Ab's"
                filename.contains("anfrage") -> "INBOX.Archives (2).Ausgangsanfragen"
                filename.contains("zeichnung") || filename.contains("entwurf") -> "INBOX.Archives (2).Ausgangszeichnungen"
                else -> null
            }
            if (folderName != null) {
                val folder = store.getFolder(folderName)
                folder.open(Folder.READ_WRITE)
                folder.appendMessages(arrayOf(message))
                folder.close(false)
                log.info("Email copy stored in archive folder {}", folderName)
            }
            store.close()
        } catch (me: MessagingException) {
            log.warn("Could not store email copy in archive folder", me)
        }
    }

    data class EmailContent(
        val subject: String?,
        val htmlBody: String?,
    ) {
        fun subject(): String = subject.orEmpty()
        fun htmlBody(): String = htmlBody.orEmpty()
    }

    private enum class InvoiceType(val displayName: String) {
        ABSCHLAGSRECHNUNG("Abschlagsrechnung"),
        TEILRECHNUNG("Teilrechnung"),
        SCHLUSSRECHNUNG("Schlussrechnung"),
        MAHNUNG("Mahnung"),
        RECHNUNG("Rechnung"),
    }

    companion object {
        @JvmStatic
        fun buildInvoiceEmail(
            invoiceFilePath: String?,
            anredeGeehrte: String?,
            kundenName: String?,
            bauvorhaben: String?,
            projektnummer: String?,
            rechnungsnummer: String?,
            rechnungsdatum: LocalDate?,
            faelligkeitsdatum: LocalDate?,
            betrag: String?,
            benutzer: String?,
        ): EmailContent {
            val type = detectInvoiceType(invoiceFilePath ?: "rechnung")
            val subject = "${type.displayName}: (BV: $bauvorhaben) Rechnungsnummer: $rechnungsnummer"
            val fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            val rechnungsdatumStr = rechnungsdatum?.format(fmt).orEmpty()
            val faelligkeitsdatumStr = faelligkeitsdatum?.format(fmt).orEmpty()
            val body = StringBuilder()
            body.append(anredeGeehrte)
            if (!kundenName.isNullOrBlank()) body.append(" ").append(kundenName)
            body.append(",<br><br>")
            when (type) {
                InvoiceType.TEILRECHNUNG -> body.append("anbei sende ich Ihnen eine Teilrechnung fuer unsere bereits erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.<br><br>")
                InvoiceType.SCHLUSSRECHNUNG -> body.append("anbei sende ich Ihnen die Schlussrechnung fuer unsere erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.<br><br>")
                    .append("Wir wuerden uns sehr ueber eine Bewertung freuen: ")
                    .append("<a href='https://www.google.com/search?tbm=lcl&q=Bauschlosserei+Thomas+Kuhn+Rezensionen'>Jetzt Bewertung abgeben</a><br><br>")
                InvoiceType.MAHNUNG -> body.append("leider haben wir festgestellt, dass die Rechnung mit der Nummer ")
                    .append(rechnungsnummer).append(" fuer das Bauvorhaben ").append(bauvorhaben)
                    .append(" noch nicht beglichen wurde.<br><br>")
                    .append("Der Betrag in Hoehe von ").append(betrag).append(" war am ")
                    .append(faelligkeitsdatumStr).append(" faellig.<br><br>")
                    .append("Bitte ueberweisen Sie den ausstehenden Betrag umgehend, um zusaetzliche Mahngebuehren zu vermeiden.<br><br>")
                InvoiceType.ABSCHLAGSRECHNUNG -> body.append("anbei sende ich Ihnen eine Abschlagsrechnung gemaess unserem Anfrage. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.<br><br>")
                InvoiceType.RECHNUNG -> body.append("anbei sende ich Ihnen die Rechnung fuer unsere erbrachten Leistungen. Die detaillierte Rechnung finden Sie als PDF-Datei im Anhang dieser E-Mail.<br><br>")
            }
            body.append("<b>Bauvorhaben:</b> <span style=\"color:#C00000\">").append(bauvorhaben).append("</span><br>")
                .append("<b>Projektnummer:</b> <span style=\"color:#C00000\">").append(projektnummer).append("</span><br>")
                .append("<b>Rechnungsnummer:</b> <span style=\"color:#C00000\">").append(rechnungsnummer).append("</span><br>")
                .append("<b>Rechnungsdatum:</b> <span style=\"color:#C00000\">").append(rechnungsdatumStr).append("</span><br>")
                .append("<b>Faelligkeitsdatum:</b> <span style=\"color:#C00000\">").append(faelligkeitsdatumStr).append("</span><br>")
                .append("<b>Gesamtbetrag:</b> <span style=\"color:#C00000\">").append(betrag).append("</span><br><br>")
                .append("Zahlungsinformationen:<br>")
                .append("Bank: Sparkasse Mainfranken<br>")
                .append("IBAN: DE 68 790 500 00 0010 1114 58<br>")
                .append("BIC/SWIFT: BYLADEM1SWU<br><br>")
                .append("Bitte ueberweisen Sie den Gesamtbetrag bis spaetestens <span style=\"color:#C00000\">")
                .append(faelligkeitsdatumStr)
                .append("</span> auf das oben genannte Konto. Bei Fragen oder Unklarheiten stehe ich Ihnen gerne zur Verfuegung.<br>")
                .append("<b>Bitte geben Sie im Verwendungszweck die Projektnummer und die Rechnungsnummer an.</b><br><br>")
            return EmailContent(subject, body.toString())
        }

        @JvmStatic
        fun buildInvoiceEmailWithTypeHints(
            invoiceFilePath: String?,
            anredeGeehrte: String?,
            kundenName: String?,
            bauvorhaben: String?,
            projektnummer: String?,
            rechnungsnummer: String?,
            rechnungsdatum: LocalDate?,
            faelligkeitsdatum: LocalDate?,
            betrag: String?,
            benutzer: String?,
            vararg typeHints: String?,
        ): EmailContent {
            val overrideToken = resolveInvoiceTypeToken(*typeHints)
            val detectionSeed = overrideToken ?: invoiceFilePath?.takeIf { it.isNotBlank() } ?: "rechnung"
            return buildInvoiceEmail(detectionSeed, anredeGeehrte, kundenName, bauvorhaben, projektnummer, rechnungsnummer, rechnungsdatum, faelligkeitsdatum, betrag, benutzer)
        }

        @JvmStatic
        fun buildOrderConfirmationEmail(
            filePath: String?,
            anredeGeehrte: String?,
            kundenName: String?,
            bauvorhaben: String?,
            projektnummer: String?,
            auftragsnummer: String?,
            betrag: String?,
            benutzer: String?,
        ): EmailContent {
            val subject = "Auftragsbestaetigung: (BV: $bauvorhaben) Auftragsnummer: $auftragsnummer"
            val body = StringBuilder()
            body.append(anredeGeehrte)
            if (!kundenName.isNullOrBlank()) body.append(" ").append(kundenName)
            body.append(",<br><br>")
                .append("anbei sende ich Ihnen die Auftragsbestaetigung. Die detaillierte Auftragsbestaetigung finden Sie als PDF-Datei im Anhang dieser E-Mail.<br><br>")
                .append("<b>Bauvorhaben:</b> <span style=\"color:#C00000\">").append(bauvorhaben).append("</span><br>")
                .append("<b>Projektnummer:</b> <span style=\"color:#C00000\">").append(projektnummer).append("</span><br>")
                .append("<b>Auftragsnummer:</b> <span style=\"color:#C00000\">").append(auftragsnummer).append("</span><br>")
            if (!betrag.isNullOrBlank()) body.append("<b>Auftragssumme:</b> <span style=\"color:#C00000\">").append(betrag).append("</span><br>")
            return EmailContent(subject, body.toString())
        }

        @JvmStatic
        fun buildOfferEmail(anredeGeehrte: String?, kundenName: String?, bauvorhaben: String?, anfragesnummer: String?, benutzer: String?, position: String?): EmailContent {
            val subject = "Anfrage: (BV: $bauvorhaben) Anfragesnummer: $anfragesnummer"
            val body = StringBuilder()
            body.append(anredeGeehrte)
            if (!kundenName.isNullOrBlank()) body.append(" ").append(kundenName)
            body.append(",<br><br>")
                .append("Im Anhang finden Sie das besprochene Anfrage.<br>")
                .append("Bei Rueckfragen koennen Sie sich gerne telefonisch oder per E-Mail bei uns melden.<br><br>")
                .append("Bei Auftragserteilung wird von uns eine 3D Zeichnung mit genauen Massen erstellt.<br>")
                .append("Nach Freigabe der Zeichnung gehen wir in die Produktion.<br><br>")
                .append("<b>Bauvorhaben:</b> <span style=\"color:#C00000\">").append(bauvorhaben).append("</span><br>")
                .append("<b>Anfragesnummer:</b> <span style=\"color:#C00000\">").append(anfragesnummer).append("</span><br><br>")
            return EmailContent(subject, body.toString())
        }

        @JvmStatic
        fun buildDrawingEmail(anredeGeehrte: String?, benutzer: String?, bauvorhaben: String?): EmailContent {
            val subject = "Kundenzeichnung BV:($bauvorhaben )"
            val body = StringBuilder()
            body.append(anredeGeehrte).append(",<br><br>")
                .append("anbei finden Sie die PDF mit dem ersten Entwurf Ihres Bauprojekts.<br>")
                .append("Bitte nehmen Sie sich etwas Zeit, um das Design sorgfaeltig zu ueberpruefen.<br>")
                .append("Sollten Sie weitere Aenderungswuensche haben oder Fragen auftauchen, stehe ich Ihnen gerne zur Verfuegung.<br>")
                .append("Wir moechten Sie darauf hinweisen, dass groessere Zeichnungsaenderungen, wie beispielsweise eine Aenderung der Machart, die gravierend vom Anfragestext abweicht, aufgrund des damit verbundenen Zeitaufwands zusaetzliche Kosten verursachen koennen.<br>")
                .append("Wir bitten um Ihr Verstaendnis dafuer.<br>")
                .append("Falls dies im Anfrage so vereinbart war, wird nach Abschluss der Planung eine Abschlagsrechnung erstellt.<br>")
                .append("Bei Fragen oder weiteren Anliegen stehe ich Ihnen jederzeit zur Verfuegung.<br>")
                .append("Vielen Dank fuer Ihre Zusammenarbeit und Ihr Verstaendnis.<br><br>")
            return EmailContent(subject, body.toString())
        }

        @JvmStatic
        fun getEmailBody(benutzer: String?): String =
            StringBuilder().append("<br><br>")
                .append("Mit freundlichen Gruessen,<br><br>")
                .append("$benutzer<br>")
                .append("Bauschlosserei Kuhn<br>Friedenstr. 17<br>97259 Greussenheim<br>")
                .append("Tel.: 09369-23 23<br><br>")
                .append("<a href=\"mailto:bauschlosserei-kuhn@t-online.de\">Email</a><br>")
                .append("<a href=\"https://www.instagram.com/bauschlossereikuhn/\">Instagram</a><br>")
                .append("<a href=\"https://bauschlosserei-kuhn.de/\">Website</a><br><br>")
                .append("<a href=\"https://bauschlosserei-kuhn.de/\"><img src=\"/firmenlogo.png\" width=\"250\" height=\"120\"></a>")
                .toString()

        @JvmStatic
        fun main(args: Array<String>) {
            val service = EmailService("securesmtp.t-online.de", 465, "info-bauschlosserei-kuhn@t-online.de", "Lini+marviTkom")
            val attachmentPath = "C:/Users/bausc/Downloads/Rechnung2025_05_00004(1.AbschlagsrechnungzuAnfrage2025_05_00001).Pdf"
            val content = buildInvoiceEmail(
                attachmentPath,
                "Sehr geehrte Damen und Herren",
                "Musterkunde",
                "Musterbauvorhaben",
                "2025-01",
                "Rechnung2025_06_00007",
                LocalDate.now(),
                LocalDate.now().plusDays(14),
                "1.234,56 EUR",
                "Max Mustermann",
            )
            service.sendEmail("mkuhn864@gmail.com", null, "bauschlosserei-kuhn@t-online.de", content.subject(), content.htmlBody(), attachmentPath, Path.of(attachmentPath).fileName.toString())
        }

        private fun detectInvoiceType(fileName: String): InvoiceType {
            val name = File(fileName).name.lowercase(Locale.ROOT)
            return when {
                name.contains("schlussrechnung") -> InvoiceType.SCHLUSSRECHNUNG
                name.contains("teilrechnung") -> InvoiceType.TEILRECHNUNG
                name.contains("abschlagsrechnung") -> InvoiceType.ABSCHLAGSRECHNUNG
                name.contains("mahnung") -> InvoiceType.MAHNUNG
                else -> InvoiceType.RECHNUNG
            }
        }

        private fun resolveInvoiceTypeToken(vararg typeHints: String?): String? {
            typeHints.forEach { hint ->
                val normalized = hint?.trim()?.lowercase(Locale.ROOT).orEmpty()
                if (normalized.isEmpty()) return@forEach
                when {
                    normalized.contains("mahn") || normalized.contains("erinnerung") -> return "mahnung"
                    normalized.contains("abschlags") -> return "abschlagsrechnung"
                    normalized.contains("teil") -> return "teilrechnung"
                    normalized.contains("schluss") -> return "schlussrechnung"
                    normalized.contains("rechnung") -> return "rechnung"
                }
            }
            return null
        }
    }
}
