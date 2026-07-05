package org.example.kalkulationsprogramm.service

import jakarta.annotation.PostConstruct
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.SpamModelStats
import org.example.kalkulationsprogramm.repository.SpamModelStatsRepository
import org.example.kalkulationsprogramm.repository.SpamTokenCountRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

@Service
class SpamBayesService(
    private val tokenRepo: SpamTokenCountRepository,
    private val statsRepo: SpamModelStatsRepository,
) {
    @Volatile
    private var tokenCache: MutableMap<String, IntArray> = ConcurrentHashMap()

    @Volatile
    var totalSpam: Long = 0

    @Volatile
    var totalHam: Long = 0

    @Volatile
    private var totalSpamTokens: Long = 0

    @Volatile
    private var totalHamTokens: Long = 0

    @Volatile
    var vocabularySize: Int = 0

    @PostConstruct
    fun loadModel() {
        refreshModel()
        log.info("[SpamBayes] Modell geladen: {} Tokens, {} Spam-Docs, {} Ham-Docs, ready={}", vocabularySize, totalSpam, totalHam, isModelReady)
    }

    @Scheduled(fixedDelay = 300_000)
    fun refreshModel() {
        try {
            totalSpam = statsRepo.findByStatKey("total_spam").map(SpamModelStats::statValue).orElse(0L)
            totalHam = statsRepo.findByStatKey("total_ham").map(SpamModelStats::statValue).orElse(0L)
            val newCache: MutableMap<String, IntArray> = ConcurrentHashMap()
            var spamTokenSum = 0L
            var hamTokenSum = 0L
            for (tc in tokenRepo.findAll()) {
                val token = tc.token ?: continue
                newCache[token] = intArrayOf(tc.spamCount, tc.hamCount)
                spamTokenSum += tc.spamCount.toLong()
                hamTokenSum += tc.hamCount.toLong()
            }
            tokenCache = newCache
            totalSpamTokens = spamTokenSum
            totalHamTokens = hamTokenSum
            vocabularySize = newCache.size
        } catch (e: Exception) {
            log.error("[SpamBayes] Fehler beim Laden des Modells", e)
        }
    }

    fun tokenize(subject: String?, body: String?): Set<String> {
        val sb = StringBuilder()
        if (subject != null) sb.append(subject).append(" ")
        if (body != null) sb.append(HTML_TAGS.matcher(body).replaceAll(" "))
        val parts = TOKEN_SPLIT.split(sb.toString().lowercase())
        val tokens = LinkedHashSet<String>()
        for (part in parts) {
            if (part.length in MIN_TOKEN_LENGTH..MAX_TOKEN_LENGTH && !PURE_NUMBER.matcher(part).matches()) {
                tokens.add(part)
                if (tokens.size >= MAX_TOKENS_PER_EMAIL) break
            }
        }
        return tokens
    }

    fun tokenize(email: Email): Set<String> {
        val tokens = tokenize(email.subject, combineBody(email)).toMutableSet()
        addSenderTokens(tokens, email.fromAddress)
        return tokens
    }

    private fun addSenderTokens(tokens: MutableSet<String>, fromAddress: String?) {
        if (fromAddress.isNullOrBlank()) return
        var email = fromAddress
        val lt = fromAddress.lastIndexOf('<')
        val gt = fromAddress.lastIndexOf('>')
        if (lt >= 0 && gt > lt) email = fromAddress.substring(lt + 1, gt)
        val at = email.indexOf('@')
        if (at <= 0 || at >= email.length - 1) return
        val localPart = email.substring(0, at)
        val domain = email.substring(at + 1).lowercase().trim()
        if (domain.isBlank() || domain.length > MAX_TOKEN_LENGTH + 10) return
        if (tokens.size < MAX_TOKENS_PER_EMAIL) tokens.add("from_domain_$domain")
        val dot = domain.lastIndexOf('.')
        if (dot > 0 && dot < domain.length - 1 && tokens.size < MAX_TOKENS_PER_EMAIL) {
            tokens.add("from_tld_" + domain.substring(dot + 1))
        }
        if (looksRandomLocalPart(localPart) && tokens.size < MAX_TOKENS_PER_EMAIL) {
            tokens.add("from_random_local")
        }
    }

    private fun looksRandomLocalPart(value: String?): Boolean {
        if (value == null || value.length < 12) return false
        var caseSwitches = 0
        var prevWasUpper: Boolean? = null
        for (c in value) {
            if (c.isUpperCase()) {
                if (prevWasUpper == false) caseSwitches++
                prevWasUpper = true
            } else if (c.isLowerCase()) {
                if (prevWasUpper == true) caseSwitches++
                prevWasUpper = false
            }
            if (caseSwitches >= 4) return true
        }
        return false
    }

    private fun combineBody(email: Email): String {
        val sb = StringBuilder()
        if (email.body != null) sb.append(email.body).append(" ")
        if (email.htmlBody != null) sb.append(email.htmlBody)
        return sb.toString()
    }

    @Transactional
    fun train(email: Email, isSpam: Boolean) {
        trainTokens(tokenize(email), isSpam)
    }

    @Transactional
    fun trainText(text: String, isSpam: Boolean) {
        trainTokens(tokenize(null, text), isSpam)
    }

    private fun trainTokens(tokens: Set<String>, isSpam: Boolean) {
        if (tokens.isEmpty()) return
        val spamInc = if (isSpam) 1 else 0
        val hamInc = if (isSpam) 0 else 1
        for (token in tokens) tokenRepo.upsertToken(token, spamInc, hamInc)
        statsRepo.incrementStat(if (isSpam) "total_spam" else "total_ham")
        for (token in tokens) {
            tokenCache.compute(token) { _, counts ->
                val updated = counts ?: intArrayOf(0, 0)
                updated[if (isSpam) 0 else 1]++
                updated
            }
        }
        if (isSpam) {
            totalSpam++
            totalSpamTokens += tokens.size.toLong()
        } else {
            totalHam++
            totalHamTokens += tokens.size.toLong()
        }
        vocabularySize = tokenCache.size
    }

    @Transactional
    fun untrain(email: Email, wasSpam: Boolean) {
        untrainTokens(tokenize(email), wasSpam)
    }

    private fun untrainTokens(tokens: Set<String>, wasSpam: Boolean) {
        if (tokens.isEmpty()) return
        val spamDec = if (wasSpam) 1 else 0
        val hamDec = if (wasSpam) 0 else 1
        for (token in tokens) tokenRepo.decrementToken(token, spamDec, hamDec)
        statsRepo.decrementStat(if (wasSpam) "total_spam" else "total_ham")
        for (token in tokens) {
            tokenCache.computeIfPresent(token) { _, counts ->
                counts[if (wasSpam) 0 else 1] = max(0, counts[if (wasSpam) 0 else 1] - 1)
                counts
            }
        }
        if (wasSpam) {
            totalSpam = max(0L, totalSpam - 1)
            totalSpamTokens = max(0L, totalSpamTokens - tokens.size)
        } else {
            totalHam = max(0L, totalHam - 1)
            totalHamTokens = max(0L, totalHamTokens - tokens.size)
        }
        vocabularySize = tokenCache.size
    }

    fun predict(tokens: Set<String>): Double {
        if (!isModelReady || tokens.isEmpty()) return -1.0
        val totalDocs = totalSpam + totalHam
        val logPriorSpam = ln(totalSpam.toDouble() / totalDocs)
        val logPriorHam = ln(totalHam.toDouble() / totalDocs)
        var logLikelihoodSpam = 0.0
        var logLikelihoodHam = 0.0
        val vocabSize = max(vocabularySize, 1)

        for (token in tokens) {
            val counts = tokenCache[token]
            val sc = counts?.get(0) ?: 0
            val hc = counts?.get(1) ?: 0
            logLikelihoodSpam += ln((sc + LAPLACE_ALPHA) / (totalSpamTokens + LAPLACE_ALPHA * vocabSize))
            logLikelihoodHam += ln((hc + LAPLACE_ALPHA) / (totalHamTokens + LAPLACE_ALPHA * vocabSize))
        }

        val logSpam = logPriorSpam + logLikelihoodSpam
        val logHam = logPriorHam + logLikelihoodHam
        val maxLog = max(logSpam, logHam)
        val expSpam = exp(logSpam - maxLog)
        val expHam = exp(logHam - maxLog)
        return expSpam / (expSpam + expHam)
    }

    val isModelReady: Boolean
        get() = totalSpam + totalHam >= MIN_TRAINING_SAMPLES

    @Transactional
    @Throws(IOException::class)
    fun bootstrapFromCsv(csvStream: InputStream): IntArray {
        var spamCount = 0
        var hamCount = 0
        val batchCounts = HashMap<String, IntArray>()

        BufferedReader(InputStreamReader(csvStream, StandardCharsets.UTF_8)).use { reader ->
            var firstLine = true
            var tabSeparated: Boolean? = null
            var labelLast = false
            var line = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) {
                    if (firstLine) {
                        firstLine = false
                        tabSeparated = line.contains("\t")
                        val headerLower = line.lowercase()
                        if (headerLower.startsWith("text,") && headerLower.contains("label")) {
                            labelLast = true
                            line = reader.readLine()
                            continue
                        }
                        if (headerLower.contains("v1") || headerLower.contains("label")) {
                            line = reader.readLine()
                            continue
                        }
                    }
                    val parts = if (tabSeparated == true) {
                        val tabIndex = line.indexOf('\t')
                        if (tabIndex < 0) {
                            line = reader.readLine()
                            continue
                        }
                        arrayOf(line.substring(0, tabIndex), line.substring(tabIndex + 1))
                    } else {
                        parseCsvLine(line)
                    }
                    if (parts.size >= 2) {
                        val label: String
                        val text: String
                        if (labelLast) {
                            label = parts[parts.size - 1].trim().lowercase()
                            text = parts.dropLast(1).joinToString(",").trim()
                        } else {
                            label = parts[0].trim().lowercase()
                            text = parts[1].trim()
                        }
                        if (text.isNotEmpty()) {
                            val isSpam = when (label) {
                                "spam" -> {
                                    spamCount++
                                    true
                                }
                                "ham", "not_spam" -> {
                                    hamCount++
                                    false
                                }
                                else -> null
                            }
                            if (isSpam != null) {
                                for (token in tokenize(null, text)) {
                                    val counts = batchCounts.computeIfAbsent(token) { intArrayOf(0, 0) }
                                    counts[if (isSpam) 0 else 1]++
                                }
                            }
                        }
                    }
                }
                line = reader.readLine()
            }
        }

        log.info("[SpamBayes] Batch-Import: {} unique Tokens in DB schreiben...", batchCounts.size)
        var batchIdx = 0
        for ((token, counts) in batchCounts) {
            tokenRepo.upsertToken(token, counts[0], counts[1])
            batchIdx++
            if (batchIdx % 1000 == 0) {
                log.info("[SpamBayes] ... {}/{} Tokens geschrieben", batchIdx, batchCounts.size)
            }
        }
        repeat(spamCount) { statsRepo.incrementStat("total_spam") }
        repeat(hamCount) { statsRepo.incrementStat("total_ham") }
        for ((token, counts) in batchCounts) {
            tokenCache[token] = intArrayOf(counts[0], counts[1])
        }
        totalSpam = spamCount.toLong()
        totalHam = hamCount.toLong()
        vocabularySize = tokenCache.size
        refreshModel()
        log.info("[SpamBayes] CSV-Bootstrap abgeschlossen: {} Spam, {} Ham, {} unique Tokens", spamCount, hamCount, batchCounts.size)
        return intArrayOf(spamCount, hamCount)
    }

    private fun parseCsvLine(line: String): Array<String> {
        val fields = ArrayList<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    current.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString())
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields.toTypedArray()
    }

    companion object {
        private val log = LoggerFactory.getLogger(SpamBayesService::class.java)
        private const val MIN_TRAINING_SAMPLES = 20
        private const val LAPLACE_ALPHA = 1.0
        private const val MAX_TOKENS_PER_EMAIL = 500
        private const val MIN_TOKEN_LENGTH = 3
        private const val MAX_TOKEN_LENGTH = 30
        private val TOKEN_SPLIT: Pattern = Pattern.compile("[^a-zA-ZäöüÄÖÜß0-9]+")
        private val PURE_NUMBER: Pattern = Pattern.compile("\\d+")
        private val HTML_TAGS: Pattern = Pattern.compile("<[^>]++>")
    }
}
