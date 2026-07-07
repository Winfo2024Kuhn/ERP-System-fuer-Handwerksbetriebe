package org.example.kalkulationsprogramm.controller

import org.example.kalkulationsprogramm.service.SystemSettingsService
import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult
import org.example.kalkulationsprogramm.service.DateiOrdnerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.Locale
import java.util.regex.Pattern

@RestController
@RequestMapping("/api/settings")
class SystemSettingsController(
    private val settingsService: SystemSettingsService,
    private val dateiOrdnerService: DateiOrdnerService,
) {
    @GetMapping
    fun getAll(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(settingsService.allSettings)
    }

    @GetMapping("/smtp")
    fun getSmtp(): ResponseEntity<SmtpSettingsResponse> {
        return ResponseEntity.ok(
            SmtpSettingsResponse(
                settingsService.smtpHost,
                settingsService.smtpPort,
                settingsService.smtpUsername,
                hasValue(settingsService.smtpPassword),
            ),
        )
    }

    @PutMapping("/smtp")
    fun saveSmtp(@RequestBody req: SmtpSettingsRequest): ResponseEntity<Map<String, String>> {
        if (!hasValue(req.host)) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Bitte einen gültigen SMTP-Server eintragen."))
        }
        if (!hasValue(req.username)) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Bitte einen gültigen SMTP-Benutzernamen eintragen."))
        }

        val effectivePassword = req.password?.takeUnless { it.isBlank() } ?: settingsService.smtpPassword
        settingsService.saveSmtpSettings(req.host, req.port, req.username, effectivePassword)
        return ResponseEntity.ok(mapOf("message" to "SMTP-Einstellungen gespeichert."))
    }

    @PostMapping("/smtp/test")
    fun testSmtp(@RequestBody req: SmtpTestRequest): ResponseEntity<TestResult> {
        val host = req.host ?: settingsService.smtpHost
        val port = if (req.port > 0) req.port else settingsService.smtpPort
        val username = req.username ?: settingsService.smtpUsername
        val password = req.password ?: settingsService.smtpPassword
        return ResponseEntity.ok(settingsService.testSmtp(host, port, username, password, req.testRecipient))
    }

    @GetMapping("/imap")
    fun getImap(): ResponseEntity<ImapSettingsResponse> {
        return ResponseEntity.ok(
            ImapSettingsResponse(
                settingsService.imapHost,
                settingsService.imapPort,
                settingsService.imapUsername,
                hasValue(settingsService.imapPassword),
            ),
        )
    }

    @PutMapping("/imap")
    fun saveImap(@RequestBody req: ImapSettingsRequest): ResponseEntity<Map<String, String>> {
        if (!hasValue(req.host)) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Bitte einen gültigen IMAP-Server eintragen."))
        }
        if (!hasValue(req.username)) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Bitte einen gültigen IMAP-Benutzernamen eintragen."))
        }

        val effectivePassword = req.password?.takeUnless { it.isBlank() } ?: settingsService.imapPassword
        val port = if (req.port > 0) req.port else 993
        settingsService.saveImapSettings(req.host, port, req.username, effectivePassword)
        return ResponseEntity.ok(mapOf("message" to "IMAP-Einstellungen gespeichert."))
    }

    @PostMapping("/imap/test")
    fun testImap(@RequestBody req: ImapTestRequest): ResponseEntity<TestResult> {
        val host = if (hasValue(req.host)) req.host else settingsService.imapHost
        val port = if (req.port > 0) req.port else settingsService.imapPort
        val username = if (hasValue(req.username)) req.username else settingsService.imapUsername
        val password = if (!req.password.isNullOrBlank()) req.password else settingsService.imapPassword
        return ResponseEntity.ok(settingsService.testImap(host, port, username, password))
    }

    @PutMapping("/email-account")
    fun saveEmailAccount(@RequestBody req: EmailAccountRequest): ResponseEntity<Map<String, String>> {
        if (!hasValue(req.email)) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Bitte eine gültige E-Mail-Adresse eintragen."))
        }
        settingsService.saveEmailAccount(req.email, req.password)
        return ResponseEntity.ok(mapOf("message" to "E-Mail-Konto gespeichert."))
    }

    @GetMapping("/gemini")
    fun getGemini(): ResponseEntity<GeminiSettingsResponse> {
        return ResponseEntity.ok(GeminiSettingsResponse(hasValue(settingsService.geminiApiKey)))
    }

    @PutMapping("/gemini")
    fun saveGemini(@RequestBody req: GeminiSettingsRequest): ResponseEntity<Map<String, String>> {
        settingsService.saveGeminiApiKey(req.apiKey)
        return ResponseEntity.ok(mapOf("message" to "Gemini API Key gespeichert."))
    }

    @PostMapping("/gemini/test")
    fun testGemini(@RequestBody req: GeminiTestRequest): ResponseEntity<TestResult> {
        val apiKey = req.apiKey ?: settingsService.geminiApiKey
        return ResponseEntity.ok(settingsService.testGeminiApiKey(apiKey))
    }

    @GetMapping("/mail-from")
    fun getMailFrom(): ResponseEntity<MailFromResponse> {
        return ResponseEntity.ok(MailFromResponse(settingsService.mailFromAddress, settingsService.smtpUsername))
    }

    @PutMapping("/mail-from")
    fun saveMailFrom(@RequestBody req: MailFromRequest): ResponseEntity<Map<String, String>> {
        val address = req.address?.trim() ?: ""
        if (address.isNotBlank() && !EMAIL_PATTERN.matcher(address).matches()) {
            return ResponseEntity.badRequest().body(mapOf("message" to "Bitte eine gültige E-Mail-Adresse eintragen."))
        }
        settingsService.saveMailFromAddress(address)
        return ResponseEntity.ok(
            mapOf(
                "message" to if (address.isBlank()) {
                    "Absender zurückgesetzt – Auto-Mails nutzen wieder den SMTP-Benutzer."
                } else {
                    "Absender für automatische Mails gespeichert."
                },
            ),
        )
    }

    @GetMapping("/anfrage-funnel-spamfilter")
    fun getFunnelSpamFilter(): ResponseEntity<FunnelSpamFilterResponse> {
        return ResponseEntity.ok(FunnelSpamFilterResponse(settingsService.isAnfrageFunnelSpamFilterAktiv))
    }

    @PutMapping("/anfrage-funnel-spamfilter")
    fun saveFunnelSpamFilter(@RequestBody req: FunnelSpamFilterRequest): ResponseEntity<Map<String, String>> {
        settingsService.saveAnfrageFunnelSpamFilterAktiv(req.aktiv)
        return ResponseEntity.ok(
            mapOf(
                "message" to if (req.aktiv) {
                    "Spam-Filter für Webseiten-Anfragen aktiviert."
                } else {
                    "Spam-Filter für Webseiten-Anfragen deaktiviert."
                },
            ),
        )
    }

    @GetMapping("/datei-ordner")
    fun getDateiOrdner(): ResponseEntity<DateiOrdnerResponse> =
        ResponseEntity.ok(
            DateiOrdnerResponse(
                settingsService.dateiOrdnerPfad,
                settingsService.dateiOrdnerNetworkUrl,
                settingsService.isDateiOrdnerConfigured,
            ),
        )

    @PutMapping("/datei-ordner")
    fun saveDateiOrdner(@RequestBody req: DateiOrdnerRequest): ResponseEntity<Map<String, String>> {
        val result = dateiOrdnerService.speichereOrdner(req.pfad, req.networkUrl)
        if (!result.success) {
            return ResponseEntity.badRequest().body(mapOf("message" to result.message))
        }
        return ResponseEntity.ok(mapOf("message" to result.message))
    }

    @PostMapping("/datei-ordner/test")
    fun testDateiOrdner(@RequestBody req: DateiOrdnerTestRequest): ResponseEntity<TestResult> =
        ResponseEntity.ok(dateiOrdnerService.pruefeOrdner(req.pfad))

    @PostMapping("/datei-ordner/freigeben")
    fun gebeDateiOrdnerFrei(): ResponseEntity<TestResult> =
        ResponseEntity.ok(dateiOrdnerService.gebeOrdnerFrei())

    private fun hasValue(value: String?): Boolean {
        if (value == null) return false
        val normalized = value.trim().lowercase(Locale.ROOT)
        return normalized.isNotBlank() &&
            normalized != "override_in_local" &&
            normalized != "smtp.example.com" &&
            normalized != "change_me_strong_password"
    }

    data class SmtpSettingsResponse(val host: String, val port: Int, val username: String, val passwordSet: Boolean)
    data class SmtpSettingsRequest(val host: String, val port: Int, val username: String, val password: String?)
    data class SmtpTestRequest(
        val host: String?,
        val port: Int,
        val username: String?,
        val password: String?,
        val testRecipient: String?,
    )
    data class ImapSettingsResponse(val host: String, val port: Int, val username: String, val passwordSet: Boolean)
    data class ImapSettingsRequest(val host: String, val port: Int, val username: String, val password: String?)
    data class ImapTestRequest(val host: String?, val port: Int, val username: String?, val password: String?)
    data class EmailAccountRequest(val email: String, val password: String?)
    data class GeminiSettingsResponse(val apiKeySet: Boolean)
    data class GeminiSettingsRequest(val apiKey: String?)
    data class GeminiTestRequest(val apiKey: String?)
    data class FunnelSpamFilterResponse(val aktiv: Boolean)
    data class FunnelSpamFilterRequest(val aktiv: Boolean)
    data class MailFromResponse(val address: String?, val smtpUsername: String)
    data class MailFromRequest(val address: String?)
    data class DateiOrdnerResponse(val pfad: String, val networkUrl: String, val konfiguriert: Boolean)
    data class DateiOrdnerRequest(val pfad: String?, val networkUrl: String?)
    data class DateiOrdnerTestRequest(val pfad: String?)

    companion object {
        private val EMAIL_PATTERN: Pattern = Pattern.compile("^[^@\\s]+@[^@\\s.]+(?:\\.[^@\\s.]+)+$")
    }
}
