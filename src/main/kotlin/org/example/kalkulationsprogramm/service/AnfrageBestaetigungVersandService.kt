package org.example.kalkulationsprogramm.service

import org.example.email.EmailService
import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailDirection
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.web.util.HtmlUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Versendet die automatische Bestätigungsmail an Leads, die über den
 * öffentlichen Webseiten-Funnel eine Anfrage abschicken.
 */
@Service
class AnfrageBestaetigungVersandService(
    private val emailTextTemplateService: EmailTextTemplateService,
    private val emailSignatureService: EmailSignatureService,
    private val systemSettingsService: SystemSettingsService,
    private val outboundPersistenceService: EmailOutboundPersistenceService,
) {
    fun versendeBestaetigung(anfrage: Anfrage?, vorname: String?, nachname: String?, nachricht: String?): Boolean {
        if (anfrage == null) {
            return false
        }
        val empfaenger = ersterEmpfaenger(anfrage)
        if (empfaenger == null) {
            log.info("Anfrage-Bestaetigung uebersprungen: keine Empfaenger-Mail in Anfrage id={}", anfrage.id)
            return false
        }

        return try {
            val ctx = baueKontext(anfrage, vorname, nachname, nachricht)
            val content = emailTextTemplateService.render(VORLAGE_DOKUMENT_TYP, ctx)
            if (content == null || content.subject().isNullOrBlank()) {
                log.warn("Anfrage-Bestaetigung uebersprungen: keine aktive Vorlage '{}'", VORLAGE_DOKUMENT_TYP)
                return false
            }

            if (!systemSettingsService.isSmtpConfigured()) {
                log.warn("Anfrage-Bestaetigung uebersprungen: SMTP ist nicht konfiguriert")
                return false
            }

            val htmlMitSignatur = emailSignatureService.appendSystemSignatureIfConfigured(content.htmlBody())
            val absender = systemSettingsService.mailFromAddress

            val emailService = baueEmailService()
            val messageId = emailService.sendEmailAndReturnMessageId(
                empfaenger,
                null,
                absender,
                content.subject(),
                htmlMitSignatur,
                null,
                null,
            )

            persistiereAusgangsEmail(anfrage, empfaenger, absender, content.subject(), htmlMitSignatur, messageId)

            log.info("Anfrage-Bestaetigung an {} versendet (anfrageId={})", empfaenger, anfrage.id)
            true
        } catch (e: Exception) {
            log.error("Anfrage-Bestaetigung fuer Anfrage {} fehlgeschlagen: {}", anfrage.id, e.message, e)
            false
        }
    }

    private fun persistiereAusgangsEmail(
        anfrage: Anfrage,
        empfaenger: String,
        absender: String?,
        subject: String?,
        htmlBody: String?,
        messageId: String?,
    ) {
        if (messageId.isNullOrBlank()) {
            log.warn(
                "Anfrage-Bestaetigung ohne Message-ID - kein DB-Eintrag, IMAP-Poll uebernimmt (anfrageId={})",
                anfrage.id,
            )
            return
        }
        try {
            if (outboundPersistenceService.existsByMessageId(messageId)) {
                return
            }
            val email = Email()
            email.assignToAnfrage(anfrage)
            email.messageId = messageId
            email.direction = EmailDirection.OUT
            email.fromAddress = absender
            email.recipient = empfaenger
            email.subject = subject
            email.htmlBody = htmlBody
            email.body = EmailHtmlSanitizer.htmlToPlainText(htmlBody)
            email.sentAt = LocalDateTime.now()
            email.isRead = true
            email.imapFolder = "INBOX.Sent"
            outboundPersistenceService.speichereOutEmail(email)
        } catch (race: DataIntegrityViolationException) {
            log.info(
                "Anfrage-Bestaetigung bereits vom IMAP-Sent-Poll persistiert (race) - anfrageId={}, messageId={}",
                anfrage.id,
                messageId,
            )
        } catch (e: Exception) {
            log.error(
                "Konnte Anfrage-Bestaetigung nicht in email-Tabelle persistieren (anfrageId={}, messageId={}): {}",
                anfrage.id,
                messageId,
                e.message,
                e,
            )
        }
    }

    internal fun baueEmailService(): EmailService =
        EmailService(
            systemSettingsService.smtpHost,
            systemSettingsService.smtpPort,
            systemSettingsService.smtpUsername,
            systemSettingsService.smtpPassword,
        )

    companion object {
        const val VORLAGE_DOKUMENT_TYP: String = "WEBSITE_ANFRAGE_BESTAETIGUNG"
        private val DATUM_DE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        private val log = LoggerFactory.getLogger(AnfrageBestaetigungVersandService::class.java)

        private fun ersterEmpfaenger(anfrage: Anfrage): String? =
            anfrage.kundenEmails
                ?.asSequence()
                ?.filterNotNull()
                ?.map(String::trim)
                ?.firstOrNull { it.isNotBlank() }

        private fun baueKontext(anfrage: Anfrage, vorname: String?, nachname: String?, nachricht: String?): Map<String, String> {
            val ctx = HashMap<String, String>()
            val voll = (safe(vorname) + " " + safe(nachname)).trim()
            ctx["ANREDE"] = if (voll.isEmpty()) "Hallo" else "Hallo " + escape(voll)
            ctx["KUNDENNAME"] = escape(voll)
            ctx["VORNAME"] = escape(safe(vorname))
            ctx["NACHNAME"] = escape(safe(nachname))
            ctx["BAUVORHABEN"] = escape(safe(anfrage.bauvorhaben))
            ctx["NACHRICHT"] = escape(safe(nachricht))
            ctx["ANFRAGE_DATUM"] = anfrage.anlegedatum?.format(DATUM_DE) ?: ""
            ctx["ANFRAGENUMMER"] = anfrage.id?.toString() ?: ""
            return ctx
        }

        private fun escape(value: String?): String = HtmlUtils.htmlEscape(value ?: "")

        private fun safe(value: String?): String = value?.trim() ?: ""
    }
}
