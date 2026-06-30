package org.example.kalkulationsprogramm.service

import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.repository.LieferantenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
open class InquiryDetectionService(
    private val lieferantenRepository: LieferantenRepository,
) {

    open fun analyzeAndMarkInquiry(email: Email) {
        val score = calculateInquiryScore(email)
        invokeSetter(email, "setInquiryScore", Integer::class.java, score)
        invokeSetter(email, "setPotentialInquiry", java.lang.Boolean.TYPE, score >= INQUIRY_THRESHOLD)

        if (invokeBoolean(email, "isPotentialInquiry")) {
            log.debug(
                "[InquiryDetection] Email als Anfrage markiert: score={}, subject='{}'",
                score,
                invokeString(email, "getSubject"),
            )
        }
    }

    open fun calculateInquiryScore(email: Email): Int {
        val subject = invokeString(email, "getSubject")?.lowercase() ?: ""
        val body = invokeString(email, "getBody")?.lowercase() ?: ""
        val fromAddress = invokeString(email, "getFromAddress")?.lowercase() ?: ""
        val combinedText = "$subject $body"

        var score = 0

        if (isFromLieferant(fromAddress)) {
            log.debug("[InquiryDetection] Ausschluss: Email von Lieferant: {}", fromAddress)
            return 0
        }

        if (invokeBoolean(email, "isSpam")) {
            return 0
        }

        for (keyword in STRONG_INQUIRY_KEYWORDS) {
            score += when {
                subject.contains(keyword.keyword) -> (keyword.weight * 1.5).toInt()
                body.contains(keyword.keyword) -> keyword.weight
                else -> 0
            }
        }

        for (keyword in MEDIUM_INQUIRY_KEYWORDS) {
            score += when {
                subject.contains(keyword.keyword) -> (keyword.weight * 1.3).toInt()
                body.contains(keyword.keyword) -> keyword.weight
                else -> 0
            }
        }

        for (keyword in NEGATIVE_KEYWORDS) {
            if (combinedText.contains(keyword.keyword)) {
                score += keyword.weight
            }
        }

        return score.coerceIn(0, 100)
    }

    private fun isFromLieferant(fromAddress: String?): Boolean {
        if (fromAddress.isNullOrBlank()) {
            return false
        }

        var email = fromAddress
        if (fromAddress.contains("<") && fromAddress.contains(">")) {
            val start = fromAddress.indexOf('<') + 1
            val end = fromAddress.indexOf('>')
            if (start < end) {
                email = fromAddress.substring(start, end)
            }
        }
        email = email.lowercase().trim()

        if (lieferantenRepository.findByEmail(email).isPresent) {
            return true
        }

        if (email.contains("@")) {
            val domain = email.substring(email.lastIndexOf("@") + 1)
            return lieferantenRepository.existsByEmailDomain(domain)
        }

        return false
    }

    private fun invokeString(target: Any, methodName: String): String? =
        target.javaClass.getMethod(methodName).invoke(target) as? String

    private fun invokeBoolean(target: Any, methodName: String): Boolean =
        target.javaClass.getMethod(methodName).invoke(target) as? Boolean ?: false

    private fun invokeSetter(target: Any, methodName: String, parameterType: Class<*>, value: Any?) {
        target.javaClass.getMethod(methodName, parameterType).invoke(target, value)
    }

    private data class InquiryKeyword(
        val keyword: String,
        val weight: Int,
    )

    class ScanResult(
        @JvmField var totalScanned: Int,
        @JvmField var inquiriesFound: Int,
        @JvmField var notInquiries: Int,
    )

    companion object {
        private val log = LoggerFactory.getLogger(InquiryDetectionService::class.java)
        private const val INQUIRY_THRESHOLD = 40

        private val STRONG_INQUIRY_KEYWORDS = listOf(
            InquiryKeyword("anfrage fuer", 35),
            InquiryKeyword("anfrage für", 35),
            InquiryKeyword("anfrage bezueglich", 35),
            InquiryKeyword("anfrage bezüglich", 35),
            InquiryKeyword("bitte um anfrage", 40),
            InquiryKeyword("anfrage erstellen", 35),
            InquiryKeyword("preisanfrage", 40),
            InquiryKeyword("kostenanfrage", 40),
            InquiryKeyword("was wuerde", 30),
            InquiryKeyword("was würde", 30),
            InquiryKeyword("kosten fuer", 25),
            InquiryKeyword("kosten für", 25),
            InquiryKeyword("haetten sie interesse", 30),
            InquiryKeyword("hätten sie interesse", 30),
            InquiryKeyword("koennten sie mir", 25),
            InquiryKeyword("könnten sie mir", 25),
            InquiryKeyword("bitte um ein anfrage", 40),
            InquiryKeyword("unverbindliches anfrage", 35),
            InquiryKeyword("preisvorstellung", 30),
            InquiryKeyword("kostenvoranschlag", 40),
            InquiryKeyword("bauvorhaben", 25),
            InquiryKeyword("projekt geplant", 30),
            InquiryKeyword("auftrag erteilen", 30),
        )

        private val MEDIUM_INQUIRY_KEYWORDS = listOf(
            InquiryKeyword("anfrage", 15),
            InquiryKeyword("anfrage", 10),
            InquiryKeyword("interesse", 15),
            InquiryKeyword("benoetigen", 10),
            InquiryKeyword("benötigen", 10),
            InquiryKeyword("suchen", 10),
            InquiryKeyword("moechten beauftragen", 20),
            InquiryKeyword("möchten beauftragen", 20),
            InquiryKeyword("metallbau", 15),
            InquiryKeyword("gelaender", 20),
            InquiryKeyword("geländer", 20),
            InquiryKeyword("treppe", 20),
            InquiryKeyword("balkon", 20),
            InquiryKeyword("carport", 20),
            InquiryKeyword("zaun", 15),
            InquiryKeyword("tor", 15),
            InquiryKeyword("schweissarbeiten", 20),
            InquiryKeyword("schweißarbeiten", 20),
        )

        private val NEGATIVE_KEYWORDS = listOf(
            InquiryKeyword("newsletter", -80),
            InquiryKeyword("abmelden", -50),
            InquiryKeyword("unsubscribe", -60),
            InquiryKeyword("rechnung", -40),
            InquiryKeyword("lieferschein", -30),
            InquiryKeyword("mahnung", -50),
            InquiryKeyword("auftragsbestaetigung", -40),
            InquiryKeyword("auftragsbestätigung", -40),
            InquiryKeyword("tracking", -30),
            InquiryKeyword("sendungsverfolgung", -30),
            InquiryKeyword("bestellung eingegangen", -40),
            InquiryKeyword("ihre bestellung", -30),
            InquiryKeyword("automatisch generiert", -50),
            InquiryKeyword("nicht antworten", -40),
            InquiryKeyword("noreply", -40),
            InquiryKeyword("no-reply", -40),
        )
    }
}
