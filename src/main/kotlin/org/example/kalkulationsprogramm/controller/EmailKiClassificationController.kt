package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp
import org.example.kalkulationsprogramm.domain.Projekt
import org.example.kalkulationsprogramm.repository.AnfrageRepository
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.example.kalkulationsprogramm.repository.ProjektRepository
import org.example.kalkulationsprogramm.service.EmailClassificationGeminiClient
import org.example.kalkulationsprogramm.service.EmailKiClassificationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Locale

@RestController
@RequestMapping("/api/email-ki")
class EmailKiClassificationController(
    private val geminiClient: EmailClassificationGeminiClient,
    private val classificationService: EmailKiClassificationService,
    private val emailRepository: EmailRepository,
    private val projektRepository: ProjektRepository,
    private val anfrageRepository: AnfrageRepository,
) {
    @GetMapping("/status")
    fun status(): ResponseEntity<Map<String, Any>> {
        val enabled = geminiClient.isEnabled()
        return ResponseEntity.ok(
            mapOf(
                "provider" to "gemini",
                "enabled" to enabled,
                "available" to enabled,
            ),
        )
    }

    @PostMapping("/classify/{emailId}")
    fun classifyEmail(@PathVariable emailId: Long): ResponseEntity<Map<String, Any>> {
        val email = emailRepository.findById(emailId).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Email nicht gefunden: $emailId"))
        val fromAddress = readString(email, "fromAddress")
        if (fromAddress.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Email hat keine Absender-Adresse"))
        }

        val emailLower = fromAddress.lowercase(Locale.getDefault()).trim()
        val projekte = projektRepository.findByKundenEmail(emailLower)
        val anfragen = anfrageRepository.findByKundenEmail(emailLower)

        if (projekte.isEmpty() && anfragen.isEmpty()) {
            return ResponseEntity.ok(
                mapOf(
                    "result" to "NO_CANDIDATES",
                    "message" to "Keine Projekte/Anfragen mit Email-Adresse $fromAddress gefunden",
                ),
            )
        }

        val result = classificationService.classify(email, projekte, anfragen)
        return ResponseEntity.ok(
            mapOf(
                "emailId" to emailId,
                "subject" to nullSafe(readString(email, "subject")),
                "fromAddress" to nullSafe(readString(email, "fromAddress")),
                "candidateCount" to (projekte.size + anfragen.size),
                "candidates" to buildCandidateList(projekte, anfragen),
                "result" to resultMap(result),
            ),
        )
    }

    @PostMapping("/classify-and-assign/{emailId}")
    fun classifyAndAssign(@PathVariable emailId: Long): ResponseEntity<Map<String, Any>> {
        val email = emailRepository.findById(emailId).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Email nicht gefunden: $emailId"))
        val fromAddress = readString(email, "fromAddress")
        if (fromAddress.isNullOrBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Email hat keine Absender-Adresse"))
        }

        val emailLower = fromAddress.lowercase(Locale.getDefault()).trim()
        val projekte = projektRepository.findByKundenEmail(emailLower)
        val anfragen = anfrageRepository.findByKundenEmail(emailLower)
        val result = classificationService.classify(email, projekte, anfragen)

        var applied = false
        if (result.isAssigned() && result.confidence() >= 0.6) {
            if (result.zuordnungTyp() == EmailZuordnungTyp.PROJEKT) {
                projekte.firstOrNull { readLong(it, "id") == result.entityId() }?.let {
                    email.assignToProjekt(it)
                    emailRepository.save(email)
                    applied = true
                }
            } else if (result.zuordnungTyp() == EmailZuordnungTyp.ANFRAGE) {
                anfragen.firstOrNull { readLong(it, "id") == result.entityId() }?.let {
                    email.assignToAnfrage(it)
                    emailRepository.save(email)
                    applied = true
                }
            }
        }

        return ResponseEntity.ok(
            mapOf(
                "emailId" to emailId,
                "result" to mapOf(
                    "key" to result.key(),
                    "confidence" to result.confidence(),
                    "reason" to nullSafe(result.reason()),
                    "assigned" to result.isAssigned(),
                ),
                "applied" to applied,
                "minConfidence" to 0.6,
            ),
        )
    }

    @GetMapping("/debug-prompt/{emailId}")
    fun debugPrompt(@PathVariable emailId: Long): ResponseEntity<Map<String, Any>> {
        val email = emailRepository.findById(emailId).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Email nicht gefunden: $emailId"))
        val fromAddress = readString(email, "fromAddress")
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "Kein Absender"))

        val emailLower = fromAddress.lowercase(Locale.getDefault()).trim()
        val projekte = projektRepository.findByKundenEmail(emailLower)
        val anfragen = anfrageRepository.findByKundenEmail(emailLower)
        val prompt = classificationService.buildUserPrompt(email, projekte, anfragen)

        return ResponseEntity.ok(
            mapOf(
                "emailId" to emailId,
                "subject" to nullSafe(readString(email, "subject")),
                "candidateCount" to (projekte.size + anfragen.size),
                "promptLength" to prompt.length,
                "prompt" to prompt,
            ),
        )
    }

    private fun resultMap(result: EmailKiClassificationService.ClassificationResult): Map<String, Any> =
        mapOf(
            "key" to result.key(),
            "zuordnungTyp" to result.zuordnungTyp().name,
            "entityId" to (result.entityId() ?: "null"),
            "confidence" to result.confidence(),
            "reason" to nullSafe(result.reason()),
            "assigned" to result.isAssigned(),
        )

    private fun buildCandidateList(projekte: List<Projekt>, anfragen: List<Anfrage>): List<Map<String, String>> {
        val list = ArrayList<Map<String, String>>()
        projekte.forEach {
            list.add(
                mapOf(
                    "key" to "PROJEKT_${readLong(it, "id")}",
                    "typ" to "PROJEKT",
                    "bauvorhaben" to nullSafe(readString(it, "bauvorhaben")),
                ),
            )
        }
        anfragen.forEach {
            list.add(
                mapOf(
                    "key" to "ANFRAGE_${readLong(it, "id")}",
                    "typ" to "ANFRAGE",
                    "bauvorhaben" to nullSafe(readString(it, "bauvorhaben")),
                ),
            )
        }
        return list
    }

    private fun nullSafe(value: String?): String = value ?: ""

    private fun readString(target: Any, fieldName: String): String? =
        readField(target, fieldName) as? String

    private fun readLong(target: Any, fieldName: String): Long? =
        (readField(target, fieldName) as? Number)?.toLong()

    private fun readField(target: Any, fieldName: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(target)
            } catch (_: NoSuchFieldException) {
                type = type.superclass
            }
        }
        return null
    }
}
