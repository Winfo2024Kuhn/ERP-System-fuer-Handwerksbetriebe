package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailAttachment
import org.example.kalkulationsprogramm.domain.EmailDraft
import org.example.kalkulationsprogramm.dto.EmailThreadDto
import org.example.kalkulationsprogramm.dto.EmailThreadEntryDto
import org.example.kalkulationsprogramm.repository.EmailDraftRepository
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.example.kalkulationsprogramm.util.InlineAttachmentUtil
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Queue

@Service
class EmailThreadService(
    private val emailRepository: EmailRepository,
    private val emailDraftRepository: EmailDraftRepository,
) {
    @Transactional(readOnly = true)
    fun loadThreadFor(emailId: Long): EmailThreadDto {
        val focusedEmail = emailRepository.findById(emailId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Email not found: $emailId") }

        val root = findRoot(focusedEmail)
        val allInThread = collectThread(root)
        val entries = allInThread.map { toEntryDto(it) }.toMutableList()

        val threadEmailIds = allInThread.mapNotNull { it.id }.toSet()
        val threadDrafts = emailDraftRepository.findByReplyEmailIdIn(threadEmailIds)
            .sortedWith(compareBy<EmailDraft, LocalDateTime?>(nullsFirst()) { it.updatedAt })
        for (draft in threadDrafts) {
            entries.add(toDraftEntryDto(draft))
        }

        val dto = EmailThreadDto()
        dto.rootEmailId = root.id
        dto.focusedEmailId = emailId
        dto.emails = entries

        log.debug("Thread for emailId={}: root={}, size={}", emailId, root.id, entries.size)
        return dto
    }

    fun computeThreadLastActivityAt(email: Email?): LocalDateTime? {
        if (email == null) return null
        val noParent = email.parentEmail == null
        val noReplies = email.replies.isEmpty()
        if (noParent && noReplies) {
            return email.sentAt
        }
        val root = findRoot(email)
        var max = maxSentAtInSubtree(root)
        val ownSentAt = email.sentAt
        if (ownSentAt != null && (max == null || ownSentAt.isAfter(max))) {
            max = ownSentAt
        }
        return max
    }

    private fun maxSentAtInSubtree(email: Email): LocalDateTime? {
        var max = email.sentAt
        val visited = Collections.newSetFromMap(IdentityHashMap<Email, Boolean>())
        val stack = ArrayDeque<Email>()
        stack.push(email)
        while (!stack.isEmpty()) {
            val current = stack.pop()
            if (!visited.add(current)) continue
            val ts = current.sentAt
            if (ts != null && (max == null || ts.isAfter(max))) {
                max = ts
            }
            current.replies.forEach { stack.push(it) }
        }
        return max
    }

    internal fun findRoot(email: Email): Email {
        val visited = HashSet<Long?>()
        var current = email
        while (current.parentEmail != null) {
            if (visited.contains(current.id)) {
                log.warn("Cycle detected in email thread at id={}", current.id)
                break
            }
            visited.add(current.id)
            current = current.parentEmail!!
        }
        return current
    }

    internal fun collectThread(root: Email): List<Email> {
        val result = ArrayList<Email>()
        val visited = HashSet<Long?>()
        val queue: Queue<Email> = ArrayDeque()
        queue.add(root)

        while (!queue.isEmpty()) {
            val current = queue.poll()
            if (visited.contains(current.id)) {
                continue
            }
            visited.add(current.id)
            result.add(current)
            queue.addAll(current.replies)
        }

        result.sortWith(compareBy(nullsFirst()) { it.sentAt })
        return result
    }

    private fun toEntryDto(email: Email): EmailThreadEntryDto {
        val dto = EmailThreadEntryDto()
        dto.id = email.id
        dto.subject = email.subject
        dto.fromAddress = email.fromAddress
        dto.recipient = email.recipient
        dto.sentAt = email.sentAt?.format(ISO_FORMATTER)
        dto.direction = email.direction?.name
        val forwarded = isForwardedEmail(email)
        dto.isForwarded = forwarded
        dto.snippet = buildSnippet(email, forwarded)

        val attachmentDtos = ArrayList<EmailThreadEntryDto.AttachmentDto>()
        email.attachments.forEach { att ->
            val attDto = EmailThreadEntryDto.AttachmentDto()
            attDto.id = att.id
            attDto.originalFilename = att.originalFilename
            attDto.mimeType = att.mimeType
            attDto.sizeBytes = att.sizeBytes
            attDto.contentId = att.contentId
            attDto.isInline = att.inlineAttachment == true
            attachmentDtos.add(attDto)
        }
        dto.attachments = attachmentDtos
        dto.htmlBody = buildHtmlBody(email)
        return dto
    }

    private fun buildHtmlBody(email: Email): String? {
        var html = email.htmlBody
        if (!html.isNullOrBlank() && !email.attachments.isNullOrEmpty()) {
            val emailId = email.id
            html = InlineAttachmentUtil.rewriteCidSources(
                html,
                email.attachments,
                { att: EmailAttachment -> att.inlineAttachment == true || !att.contentId.isNullOrBlank() },
                { att: EmailAttachment -> att.contentId ?: "" },
                { att: EmailAttachment -> "/api/emails/$emailId/attachments/${att.id}" },
            )
        }
        if (html.isNullOrBlank()) {
            val plain = email.body
            if (!plain.isNullOrBlank()) {
                html = "<pre style=\"white-space:pre-wrap;font-family:inherit;margin:0\">" +
                    plain
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;") +
                    "</pre>"
            }
        }
        return html
    }

    private fun isForwardedEmail(email: Email): Boolean {
        val subject = email.subject
        if (subject != null) {
            val normalized = subject.trimStart().lowercase()
            if (
                normalized.startsWith("fwd:") ||
                normalized.startsWith("fw:") ||
                normalized.startsWith("wg:") ||
                normalized.startsWith("weitergeleitet:")
            ) {
                return true
            }
        }
        val body = email.body
        return body != null && body.trimStart().startsWith("---------- Forwarded message")
    }

    private fun buildSnippet(email: Email, forwarded: Boolean): String {
        var text = email.body
        if (!text.isNullOrBlank()) {
            text = text.trim()
            if (forwarded) {
                text = stripForwardedHeaders(text)
            }
            return if (text.length > SNIPPET_MAX_LENGTH) {
                text.substring(0, SNIPPET_MAX_LENGTH) + "..."
            } else {
                text
            }
        }

        val html = email.htmlBody
        if (html.isNullOrBlank()) return ""

        text = html
            .replace(Regex("(?si)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?si)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), " ")
            .replace(Regex("(?i)</(p|div|tr|li|h[1-6])>"), " ")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (text.length > SNIPPET_MAX_LENGTH) {
            text.substring(0, SNIPPET_MAX_LENGTH) + "..."
        } else {
            text
        }
    }

    private fun stripForwardedHeaders(text: String): String {
        val lines = text.split(Regex("\\r?\\n"))
        var inHeader = false
        val real = StringBuilder()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("---------- Forwarded message") || trimmed.startsWith("-------- Weitergeleitete")) {
                inHeader = true
                continue
            }
            if (inHeader) {
                when {
                    trimmed.isEmpty() -> inHeader = false
                    trimmed.matches(Regex("(?i)(von|from|date|datum|subject|betreff|to|an):.*")) -> continue
                    else -> {
                        inHeader = false
                        real.append(trimmed).append(" ")
                    }
                }
            } else if (trimmed.isNotEmpty()) {
                real.append(trimmed).append(" ")
            }
        }
        val result = real.toString().trim()
        return result.ifEmpty { "[Weitergeleitet]" }
    }

    private fun toDraftEntryDto(draft: EmailDraft): EmailThreadEntryDto {
        val dto = EmailThreadEntryDto()
        dto.id = draft.id
        dto.draftId = draft.id
        dto.isDraft = true
        dto.subject = draft.subject
        dto.fromAddress = draft.fromAddress
        dto.recipient = draft.recipient
        dto.sentAt = draft.updatedAt?.format(ISO_FORMATTER)
        dto.direction = "OUT"

        val body = draft.body
        if (!body.isNullOrBlank()) {
            val text = body
                .replace(Regex("(?si)<style[^>]*>.*?</style>"), " ")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            dto.snippet = if (text.length > SNIPPET_MAX_LENGTH) {
                text.substring(0, SNIPPET_MAX_LENGTH) + "..."
            } else {
                text
            }
        } else {
            dto.snippet = "[Entwurf]"
        }
        dto.htmlBody = body
        dto.attachments = emptyList()
        return dto
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailThreadService::class.java)
        private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
        private const val SNIPPET_MAX_LENGTH = 120
    }
}
