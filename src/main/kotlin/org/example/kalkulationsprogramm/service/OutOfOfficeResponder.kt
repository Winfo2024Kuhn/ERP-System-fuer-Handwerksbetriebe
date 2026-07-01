package org.example.kalkulationsprogramm.service

import jakarta.mail.MessagingException
import org.example.email.ImapAppendService
import org.example.kalkulationsprogramm.domain.EmailSignature
import org.example.kalkulationsprogramm.domain.OooReplyLog
import org.example.kalkulationsprogramm.domain.OutOfOfficeSchedule
import org.example.kalkulationsprogramm.repository.OooReplyLogRepository
import org.example.kalkulationsprogramm.repository.OutOfOfficeScheduleRepository
import org.example.kalkulationsprogramm.service.mail.HtmlMailSender
import org.example.kalkulationsprogramm.util.EmailHtmlSanitizer
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Objects
import java.util.regex.Pattern

@Component
class OutOfOfficeResponder(
    private val scheduleRepository: OutOfOfficeScheduleRepository,
    private val emailSignatureService: EmailSignatureService,
    private val mailSender: HtmlMailSender,
    private val systemSettingsService: SystemSettingsService,
    private val imapAppendService: ImapAppendService,
    private val replyLogRepository: OooReplyLogRepository,
) {
    data class IncomingMail(
        val fromAddress: String?,
        val subject: String?,
        val sentAt: LocalDateTime?,
        val spam: Boolean,
        val newsletter: Boolean,
        val autoSubmittedHeader: String?,
        val precedenceHeader: String?,
        val listIdHeader: String?,
    ) {
        fun fromAddress(): String? = fromAddress
        fun subject(): String? = subject
        fun sentAt(): LocalDateTime? = sentAt
        fun spam(): Boolean = spam
        fun newsletter(): Boolean = newsletter
        fun autoSubmittedHeader(): String? = autoSubmittedHeader
        fun precedenceHeader(): String? = precedenceHeader
        fun listIdHeader(): String? = listIdHeader
    }

    fun handleIncomingEmail(incoming: IncomingMail?) {
        if (incoming == null || !StringUtils.hasText(incoming.fromAddress)) {
            return
        }
        if (incoming.spam || incoming.newsletter) {
            return
        }
        if (isAutoReplyHeader(incoming)) {
            return
        }
        val sender = normalize(incoming.fromAddress!!)
        if (isSystemAddress(sender)) {
            return
        }
        if (isReservedTestAddress(sender)) {
            log.debug("OOO-Antwort an Test-Adresse {} unterdrueckt.", maskAddress(sender))
            return
        }
        val defaultFromAddress = systemSettingsService.smtpUsername
        if (StringUtils.hasText(defaultFromAddress) && sender.equals(defaultFromAddress, ignoreCase = true)) {
            return
        }

        val referenceDate = incoming.sentAt
            ?.atZone(ZoneId.systemDefault())
            ?.withZoneSameInstant(BUSINESS_ZONE)
            ?.toLocalDate()
            ?: LocalDate.now(BUSINESS_ZONE)

        val scheduleOpt = scheduleRepository
            .findFirstByActiveTrueAndStartAtLessThanEqualAndEndAtGreaterThanEqualOrderByStartAtDesc(
                referenceDate,
                referenceDate,
            )
        if (scheduleOpt.isEmpty) {
            return
        }
        val schedule = scheduleOpt.get()
        if (replyLogRepository.existsByScheduleIdAndSenderAddressIgnoreCase(schedule.id, sender)) {
            log.debug("OOO-Antwort an {} bereits versendet (Plan {}).", maskAddress(sender), schedule.id)
            return
        }

        sendReply(schedule, sender, incoming.subject, defaultFromAddress)
    }

    private fun sendReply(
        schedule: OutOfOfficeSchedule,
        sender: String,
        originalSubject: String?,
        defaultFromAddress: String?,
    ) {
        val signature = schedule.signature
        val subject = buildSubject(schedule, originalSubject)
        val htmlBody = buildBodyHtml(schedule, signature, originalSubject)
        val inlineImages: Map<String, File> = signature?.let { emailSignatureService.buildInlineCidFileMap(it) } ?: emptyMap()
        try {
            mailSender.send(defaultFromAddress, sender, subject, htmlBody, inlineImages)
            log.info("Automatische Abwesenheitsantwort an {} versendet.", maskAddress(sender))
            try {
                imapAppendService.appendToSent(
                    defaultFromAddress,
                    listOf(sender),
                    subject,
                    htmlBody,
                    null,
                    LocalDateTime.now(),
                )
                log.debug("OOO-Antwort im Gesendet-Ordner abgelegt.")
            } catch (ex: Exception) {
                log.warn("Konnte OOO-Antwort nicht im Gesendet-Ordner ablegen: {}", ex.message)
            }
            recordReply(schedule.id, sender)
        } catch (ex: MessagingException) {
            log.warn("Abwesenheitsantwort konnte nicht gesendet werden: {}", ex.message)
        }
    }

    private fun recordReply(scheduleId: Long?, senderAddress: String) {
        try {
            val entry = OooReplyLog()
            entry.scheduleId = scheduleId
            entry.senderAddress = senderAddress
            entry.repliedAt = LocalDateTime.now()
            replyLogRepository.save(entry)
        } catch (race: DataIntegrityViolationException) {
            log.debug(
                "OOO-Reply-Log Race-Condition für {} (Plan {}): {}",
                maskAddress(senderAddress),
                scheduleId,
                race.mostSpecificCause?.message ?: race.message,
            )
        }
    }

    @Transactional
    fun deactivateExpiredSchedules(): Int {
        val today = LocalDate.now(BUSINESS_ZONE)
        val expired = scheduleRepository.findAll()
            .filter { it.isActive() }
            .filter { it.endAt != null && it.endAt!!.isBefore(today) }
        if (expired.isEmpty()) {
            return 0
        }
        for (schedule in expired) {
            schedule.active = false
        }
        scheduleRepository.saveAll(expired)
        log.info("{} abgelaufene Abwesenheitspläne automatisch deaktiviert.", expired.size)
        return expired.size
    }

    private fun isAutoReplyHeader(incoming: IncomingMail): Boolean {
        val autoSubmitted = lower(incoming.autoSubmittedHeader)
        if (autoSubmitted != null && autoSubmitted != "no") {
            return true
        }
        val precedence = lower(incoming.precedenceHeader)
        if (precedence != null &&
            (precedence.contains("bulk") || precedence.contains("list") || precedence.contains("junk"))
        ) {
            return true
        }
        return StringUtils.hasText(incoming.listIdHeader)
    }

    private fun isSystemAddress(address: String): Boolean {
        val local = address.lowercase(Locale.ROOT).trim()
        val at = local.indexOf('@')
        val localPart = if (at < 0) local else local.substring(0, at)
        return localPart == "mailer-daemon" ||
            localPart == "postmaster" ||
            localPart == "noreply" ||
            localPart == "no-reply" ||
            localPart == "donotreply" ||
            localPart == "do-not-reply" ||
            localPart.startsWith("bounce") ||
            localPart.startsWith("bounces")
    }

    private fun buildSubject(schedule: OutOfOfficeSchedule, originalSubject: String?): String {
        val template = schedule.subjectTemplate
            ?.takeIf(StringUtils::hasText)
            ?: "Automatische Antwort: {{subject}}"
        var subject = applyTokens(template, schedule, originalSubject)
        if (!StringUtils.hasText(subject.trim())) {
            subject = "Automatische Antwort: " + (schedule.title ?: "")
        }
        return subject
    }

    private fun buildBodyHtml(schedule: OutOfOfficeSchedule, signature: EmailSignature?, originalSubject: String?): String {
        val template = schedule.bodyTemplate
            ?.takeIf(StringUtils::hasText)
            ?: "Ich bin vom {{start}} bis {{ende}} nicht erreichbar."
        val message = applyTokens(template, schedule, originalSubject)
        var htmlBody = if (looksLikeHtml(message)) {
            EmailHtmlSanitizer.sanitizeDetailHtml(message)
        } else {
            EmailHtmlSanitizer.plainTextToHtml(message)
        } ?: ""
        if (signature != null) {
            val signatureHtml = emailSignatureService.renderSignatureHtmlForEmail(signature, null)
            if (StringUtils.hasText(signatureHtml)) {
                htmlBody += signatureHtml
            }
        }
        return htmlBody
    }

    private fun applyTokens(template: String?, schedule: OutOfOfficeSchedule, originalSubject: String?): String {
        if (template == null) {
            return ""
        }
        var value = template
        val start = formatDate(schedule.startAt)
        val end = formatDate(schedule.endAt)
        val title = schedule.title ?: ""
        val subject = originalSubject ?: ""
        value = START_TOKEN.matcher(value).replaceAll(Objects.requireNonNullElse(start, ""))
        value = END_TOKEN.matcher(value).replaceAll(Objects.requireNonNullElse(end, ""))
        value = TITLE_TOKEN.matcher(value).replaceAll(title)
        value = SUBJECT_TOKEN.matcher(value).replaceAll(subject)
        return value
    }

    private fun formatDate(value: LocalDate?): String =
        value?.let { DATE_TIME_FORMATTER.format(it) } ?: ""

    private fun normalize(address: String): String {
        var value = address.trim()
        val lt = value.lastIndexOf('<')
        val gt = value.lastIndexOf('>')
        if (lt >= 0 && gt > lt) {
            value = value.substring(lt + 1, gt)
        }
        return value.lowercase(Locale.ROOT).trim()
    }

    private fun lower(value: String?): String? =
        value?.lowercase(Locale.ROOT)?.trim()

    private fun isReservedTestAddress(address: String): Boolean {
        val at = address.lastIndexOf('@')
        if (at < 0 || at == address.length - 1) return false
        val domain = address.substring(at + 1)
        return domain == "example.com" ||
            domain == "example.org" ||
            domain == "example.net" ||
            domain.endsWith(".example") ||
            domain.endsWith(".test") ||
            domain.endsWith(".invalid") ||
            domain.endsWith(".localhost") ||
            domain == "localhost"
    }

    private fun maskAddress(address: String?): String {
        if (!StringUtils.hasText(address)) {
            return ""
        }
        val at = address!!.indexOf('@')
        return if (at <= 1) address else address[0] + "***" + address.substring(at)
    }

    companion object {
        private val log = LoggerFactory.getLogger(OutOfOfficeResponder::class.java)
        private val BUSINESS_ZONE: ZoneId = ZoneId.of("Europe/Berlin")
        private val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN)
        private val START_TOKEN: Pattern = Pattern.compile("(?i)\\{\\{start}}")
        private val END_TOKEN: Pattern = Pattern.compile("(?i)\\{\\{ende}}")
        private val TITLE_TOKEN: Pattern = Pattern.compile("(?i)\\{\\{title}}")
        private val SUBJECT_TOKEN: Pattern = Pattern.compile("(?i)\\{\\{subject}}")

        @JvmStatic
        fun looksLikeHtml(value: String?): Boolean {
            if (value == null) {
                return false
            }
            return value.matches(Regex("(?is).*<\\s*[a-zA-Z][^>]*>.*"))
        }
    }
}
