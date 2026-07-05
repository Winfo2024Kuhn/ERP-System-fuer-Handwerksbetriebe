package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailDirection
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SpamFilterService(
    private val spamBayesService: SpamBayesService? = null,
) {
    fun analyzeAndMarkSpam(email: Email) {
        val score = calculateSpamScore(email)
        email.spamScore = score
        email.isSpam = score >= SPAM_THRESHOLD
        if (!email.isNewsletter) {
            email.isNewsletter = checkForNewsletter(email)
        }
    }

    fun calculateSpamScore(email: Email): Int {
        if (email.direction == EmailDirection.OUT) return 0
        val subject = email.subject.orEmpty().lowercase()
        val body = listOfNotNull(email.body, email.htmlBody).joinToString(" ").lowercase()
        val from = email.fromAddress.orEmpty().lowercase()
        val combined = "$subject $body $from"

        var score = 0
        SPAM_KEYWORDS.forEach { (keyword, weight) ->
            if (combined.contains(keyword)) score += weight
        }
        if (subject.isBlank()) score += 15
        if (combined.contains("rechnung") && hasRelevantAttachment(email)) score = 0
        if (email.attachments.orEmpty().any { DANGEROUS_EXTENSIONS.contains(it.originalFilename?.substringAfterLast('.', "")?.lowercase()) }) {
            score += 100
            log.warn("[SpamFilter] Gefaehrlicher Anhang gefunden in Email {}", email.id)
        }

        val bayesScore = spamBayesService
            ?.takeIf { it.isModelReady }
            ?.predict(spamBayesService.tokenize(email))
            ?.takeIf { it >= 0.0 }
            ?.times(100)
            ?.toInt()
        if (bayesScore != null) {
            score = ((score * 0.4) + (bayesScore * 0.6)).toInt()
        }
        return score.coerceIn(0, 100)
    }

    fun isSpam(email: Email): Boolean = calculateSpamScore(email) >= SPAM_THRESHOLD

    private fun checkForNewsletter(email: Email): Boolean {
        val text = "${email.fromAddress.orEmpty()} ${email.subject.orEmpty()} ${email.body.orEmpty()} ${email.htmlBody.orEmpty()}".lowercase()
        return NEWSLETTER_KEYWORDS.any(text::contains)
    }

    private fun hasRelevantAttachment(email: Email): Boolean =
        email.attachments.orEmpty().any { it.isPdf() || it.isXml() }

    data class ScanResult(
        @JvmField var totalScanned: Int,
        @JvmField var spamFound: Int,
        @JvmField var notSpam: Int,
    )

    private data class SpamKeyword(val keyword: String, val weight: Int)

    companion object {
        private val log = LoggerFactory.getLogger(SpamFilterService::class.java)
        private const val SPAM_THRESHOLD = 50
        private val NEWSLETTER_KEYWORDS = listOf(
            "newsletter", "unsubscribe", "abmelden", "abbestellen", "online ansehen",
            "linkedin", "xing", "news", "marketing", "noreply", "no-reply"
        )
        private val DANGEROUS_EXTENSIONS = listOf("exe", "bat", "cmd", "scr", "js", "vbs", "jar", "com")
        private val SPAM_KEYWORDS = listOf(
            SpamKeyword("gewinnspiel", 30),
            SpamKeyword("sie haben gewonnen", 45),
            SpamKeyword("viagra", 50),
            SpamKeyword("casino", 35),
            SpamKeyword("online casino", 45),
            SpamKeyword("bitcoin opportunity", 35),
            SpamKeyword("investment opportunity", 25),
            SpamKeyword("konto verifizieren", 30),
            SpamKeyword("account suspended", 30),
            SpamKeyword("urgent action required", 25),
            SpamKeyword("pharmacy", 30),
            SpamKeyword("free gift", 25),
            SpamKeyword("limited time offer", 20),
            SpamKeyword("cloud-speicher", 40),
            SpamKeyword("aktion erforderlich", 40),
            SpamKeyword("porno", 50),
            SpamKeyword("xxx", 50),
            SpamKeyword("i hacked your", 50),
            SpamKeyword("pay bitcoin", 45),
            SpamKeyword("branchenbuch eintrag", 30),
            SpamKeyword("dsgvo abmahnung", 30),
        )
    }
}
