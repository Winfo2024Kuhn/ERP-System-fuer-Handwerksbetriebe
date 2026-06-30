package org.example.kalkulationsprogramm.service

import org.example.email.EmailService
import org.example.kalkulationsprogramm.domain.EmailTextTemplate
import org.example.kalkulationsprogramm.dto.Email.EmailTextTemplateDto
import org.example.kalkulationsprogramm.repository.EmailTextTemplateRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Comparator
import java.util.Optional
import java.util.regex.Matcher
import java.util.regex.Pattern

@Service
class EmailTextTemplateService(
    private val repository: EmailTextTemplateRepository,
    private val firmeninformationService: FirmeninformationService,
) {
    @Transactional(readOnly = true)
    fun list(): List<EmailTextTemplate> =
        repository.findAll()
            .sortedWith(compareBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.dokumentTyp })

    @Transactional(readOnly = true)
    fun get(id: Long?): EmailTextTemplate =
        if (id == null) {
            throw IllegalArgumentException("E-Mail-Textvorlage nicht gefunden")
        } else {
            repository.findById(id)
                .orElseThrow { IllegalArgumentException("E-Mail-Textvorlage nicht gefunden") }
        }

    @Transactional(readOnly = true)
    fun findByDokumentTyp(dokumentTyp: String?): Optional<EmailTextTemplate> {
        if (dokumentTyp.isNullOrBlank()) {
            return Optional.empty()
        }
        return repository.findByDokumentTyp(dokumentTyp.trim().uppercase())
    }

    fun create(dto: EmailTextTemplateDto): EmailTextTemplate {
        val entity = EmailTextTemplate()
        dto.applyToEntity(entity)
        return repository.save(entity)
    }

    fun update(id: Long?, dto: EmailTextTemplateDto): EmailTextTemplate {
        val entity = get(id)
        dto.applyToEntity(entity)
        return repository.save(entity)
    }

    fun delete(id: Long?) {
        if (id == null) {
            return
        }
        repository.deleteById(id)
    }

    @Transactional(readOnly = true)
    fun render(dokumentTyp: String?, context: Map<String, String>?): EmailService.EmailContent? {
        val mergedContext = withFirmenPlatzhalter(context)
        return findByDokumentTyp(dokumentTyp)
            .filter { it.isAktiv() }
            .map {
                EmailService.EmailContent(
                    replacePlaceholders(it.subjectTemplate, mergedContext),
                    replacePlaceholders(it.htmlBody, mergedContext),
                )
            }
            .orElse(null)
    }

    private fun withFirmenPlatzhalter(context: Map<String, String>?): Map<String, String> {
        val merged = HashMap<String, String>()
        try {
            val firma = firmeninformationService.getFirmeninformation()
            if (firma != null) {
                merged["BANK"] = nullToEmpty(firma.bankName)
                merged["IBAN"] = nullToEmpty(firma.iban)
                merged["BIC"] = nullToEmpty(firma.bic)
            }
        } catch (ex: RuntimeException) {
            log.warn(
                "Bank-Platzhalter (BANK/IBAN/BIC) konnten nicht aus den Firmenstammdaten geladen werden – sie bleiben in der E-Mail leer: {}",
                ex.message,
            )
        }
        if (context != null) {
            merged.putAll(context)
        }
        return merged
    }

    private fun nullToEmpty(value: String?): String =
        value ?: ""

    private fun replacePlaceholders(input: String?, context: Map<String, String>?): String? {
        if (input.isNullOrEmpty()) {
            return input
        }
        val matcher = PLACEHOLDER_PATTERN.matcher(input)
        val out = StringBuilder()
        while (matcher.find()) {
            val token = matcher.group(1)
            val value = context?.getOrDefault(token, "") ?: ""
            matcher.appendReplacement(out, Matcher.quoteReplacement(value))
        }
        matcher.appendTail(out)
        return out.toString()
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailTextTemplateService::class.java)
        private val PLACEHOLDER_PATTERN: Pattern = Pattern.compile("\\{\\{\\s*([A-Z0-9_]+)\\s*\\}\\}")
    }
}
