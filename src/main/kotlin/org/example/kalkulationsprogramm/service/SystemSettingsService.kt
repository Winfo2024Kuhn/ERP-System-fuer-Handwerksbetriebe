package org.example.kalkulationsprogramm.service

import jakarta.mail.AuthenticationFailedException
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.example.kalkulationsprogramm.domain.SystemSetting
import org.example.kalkulationsprogramm.repository.SystemSettingRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.Properties

@Service
class SystemSettingsService(
    private val repository: SystemSettingRepository,
) {
    @Value("\${smtp.host:}")
    private lateinit var defaultSmtpHost: String

    @Value("\${smtp.port:465}")
    private var defaultSmtpPort: Int = 465

    @Value("\${smtp.username:}")
    private lateinit var defaultSmtpUsername: String

    @Value("\${smtp.password:}")
    private lateinit var defaultSmtpPassword: String

    @Value("\${imap.host:secureimap.t-online.de}")
    private lateinit var defaultImapHost: String

    @Value("\${imap.port:993}")
    private var defaultImapPort: Int = 993

    @Value("\${imap.username:}")
    private lateinit var defaultImapUsername: String

    @Value("\${imap.password:}")
    private lateinit var defaultImapPassword: String

    @Value("\${ai.gemini.api-key:}")
    private lateinit var defaultGeminiApiKey: String

    val smtpHost: String
        get() = sanitizeValue(get("smtp.host", defaultSmtpHost))
    val smtpPort: Int
        get() = get("smtp.port", defaultSmtpPort.toString()).toIntOrNull() ?: defaultSmtpPort
    val smtpUsername: String
        get() = sanitizeValue(get("smtp.username", defaultSmtpUsername))
    val smtpPassword: String
        get() = sanitizeValue(get("smtp.password", defaultSmtpPassword))
    val mailFromAddress: String
        get() {
            val value = sanitizeValue(get("mail.from-address", ""))
            return if (value.isBlank() || !value.contains("@")) smtpUsername else value
        }
    val imapHost: String
        get() {
            val value = sanitizeValue(get("imap.host", defaultImapHost))
            return if (value.isBlank()) "secureimap.t-online.de" else value
        }
    val imapPort: Int
        get() {
            val port = get("imap.port", defaultImapPort.toString()).toIntOrNull()
            return if (port != null && port > 0) port else 993
        }
    val imapUsername: String
        get() {
            val value = sanitizeValue(get("imap.username", defaultImapUsername))
            return if (value.isBlank()) smtpUsername else value
        }
    val imapPassword: String
        get() {
            val value = sanitizeValue(get("imap.password", defaultImapPassword))
            return if (value.isBlank()) smtpPassword else value
        }
    val geminiApiKey: String
        get() = sanitizeValue(get("ai.gemini.api-key", defaultGeminiApiKey))
    val allSettings: Map<String, String>
        get() = linkedMapOf(
            "smtp.host" to smtpHost,
            "smtp.port" to smtpPort.toString(),
            "smtp.username" to smtpUsername,
            "smtp.password" to maskValue(smtpPassword),
            "imap.host" to imapHost,
            "imap.port" to imapPort.toString(),
            "imap.username" to imapUsername,
            "imap.password" to maskValue(imapPassword),
            "ai.gemini.api-key" to maskValue(geminiApiKey),
            "mail.from-address" to mailFromAddress,
        )
    val isImapConfigured: Boolean
        get() = hasConfiguredValue(imapHost) &&
            imapPort > 0 &&
            hasConfiguredValue(imapUsername) &&
            hasConfiguredValue(imapPassword)
    val isInitialConfigurationRequired: Boolean
        get() = !isSmtpConfigured()
    val isAnfrageFunnelSpamFilterAktiv: Boolean
        get() {
            val value = get("anfrage.funnel.spamfilter.aktiv", "true")
            return value.isBlank() || value.toBoolean()
        }
    val anfrageFunnelSpamFilterProvider: String
        get() {
            val value = get("anfrage.funnel.spamfilter.provider", "lokal")
            return if (value.isBlank()) "lokal" else value.trim().lowercase(Locale.ROOT)
        }

    fun get(key: String, defaultValue: String): String =
        repository.findById(key).map { it.value ?: defaultValue }.orElse(defaultValue)

    @Transactional
    fun saveAnfrageFunnelSpamFilterAktiv(aktiv: Boolean) {
        save("anfrage.funnel.spamfilter.aktiv", aktiv.toString(), "KI-Spam-Filter für Webseiten-Anfragen (Funnel) aktiv")
        log.info("Funnel-Spam-Filter umgeschaltet: aktiv={}", aktiv)
    }

    @Transactional
    fun saveAnfrageFunnelSpamFilterProvider(provider: String?) {
        val value = provider?.trim()?.lowercase(Locale.ROOT) ?: "lokal"
        if (value != "lokal" && value != "extern" && value != "aus") {
            throw IllegalArgumentException("Ungültiger Provider: $provider")
        }
        save("anfrage.funnel.spamfilter.provider", value, "LLM-Backend für Funnel-Spam-Filter (lokal/extern/aus)")
        log.info("Funnel-Spam-Filter-Backend umgeschaltet: provider={}", value)
    }

    fun isSmtpConfigured(): Boolean =
        hasConfiguredValue(smtpHost) &&
            smtpPort > 0 &&
            hasConfiguredValue(smtpUsername) &&
            hasConfiguredValue(smtpPassword)

    @Transactional
    fun saveMailFromAddress(address: String?) {
        val value = address?.trim() ?: ""
        save("mail.from-address", value, "Sichtbare Absender-Adresse für automatische System-Mails (leer = SMTP-Benutzer)")
        log.info("Mail-Absender-Adresse aktualisiert: {}", if (value.isBlank()) "(leer -> SMTP-User)" else value)
    }

    @Transactional
    fun save(key: String, value: String?, beschreibung: String?) {
        val setting = repository.findById(key).orElse(SystemSetting(key, null, beschreibung))
        setting.value = value
        if (beschreibung != null) {
            setting.beschreibung = beschreibung
        }
        repository.save(setting)
    }

    @Transactional
    fun saveSmtpSettings(host: String, port: Int, username: String, password: String) {
        save("smtp.host", host, "SMTP Mail-Server Hostname")
        save("smtp.port", port.toString(), "SMTP Port (465 = SSL, 587 = STARTTLS)")
        save("smtp.username", username, "SMTP Benutzername / E-Mail-Adresse")
        save("smtp.password", password, "SMTP Passwort")
        log.info("SMTP-Einstellungen aktualisiert (Host: {}, Port: {}, User: {})", host, port, username)
    }

    @Transactional
    fun saveImapSettings(host: String, port: Int, username: String, password: String) {
        save("imap.host", host, "IMAP Mail-Server Hostname")
        save("imap.port", port.toString(), "IMAP Port (993 = SSL)")
        save("imap.username", username, "IMAP Benutzername / E-Mail-Adresse")
        save("imap.password", password, "IMAP Passwort")
        log.info("IMAP-Einstellungen aktualisiert (Host: {}, Port: {}, User: {})", host, port, username)
    }

    @Transactional
    fun saveEmailAccount(email: String, password: String?) {
        save("smtp.username", email, "SMTP Benutzername / E-Mail-Adresse")
        save("imap.username", email, "IMAP Benutzername / E-Mail-Adresse")
        if (!password.isNullOrBlank()) {
            save("smtp.password", password, "SMTP Passwort")
            save("imap.password", password, "IMAP Passwort")
        }
        log.info("E-Mail-Konto aktualisiert (User: {})", email)
    }

    @Transactional
    fun saveGeminiApiKey(apiKey: String?) {
        save("ai.gemini.api-key", apiKey, "Google Gemini API Key")
        log.info("Gemini API Key aktualisiert")
    }

    fun testSmtp(host: String, port: Int, username: String, password: String, testRecipient: String?): TestResult {
        val props = Properties()
        props["mail.smtp.host"] = host
        props["mail.smtp.port"] = port.toString()
        props["mail.smtp.auth"] = "true"
        props["mail.smtp.ssl.enable"] = "true"
        props["mail.smtp.socketFactory.port"] = port.toString()
        props["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
        props["mail.smtp.connectiontimeout"] = "10000"
        props["mail.smtp.timeout"] = "10000"

        return try {
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(username, password)
            })
            val transport = session.getTransport("smtp")
            transport.connect(host, port, username, password)

            if (!testRecipient.isNullOrBlank()) {
                val msg = MimeMessage(session)
                msg.setFrom(InternetAddress(username))
                msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(testRecipient))
                msg.subject = "Kalkulationsprogramm - SMTP Test"
                msg.setText(
                    "Diese E-Mail bestätigt, dass die SMTP-Einstellungen korrekt konfiguriert sind.\n\n" +
                        "Server: $host:$port\nBenutzer: $username",
                )
                jakarta.mail.Transport.send(msg)
                transport.close()
                TestResult.success("Verbindung erfolgreich! Test-E-Mail an $testRecipient gesendet.")
            } else {
                transport.close()
                TestResult.success("SMTP-Verbindung erfolgreich hergestellt.")
            }
        } catch (e: AuthenticationFailedException) {
            log.warn("SMTP-Auth fehlgeschlagen: {}", e.message)
            TestResult.failure("Authentifizierung fehlgeschlagen. Benutzername oder Passwort falsch.")
        } catch (e: MessagingException) {
            log.warn("SMTP-Verbindung fehlgeschlagen: {}", e.message)
            TestResult.failure("Verbindung fehlgeschlagen: ${e.message}")
        }
    }

    fun testImap(host: String?, port: Int, username: String?, password: String?): TestResult {
        if (host.isNullOrBlank()) return TestResult.failure("Bitte einen IMAP-Server angeben.")
        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            return TestResult.failure("Benutzername und Passwort sind erforderlich.")
        }

        val props = Properties()
        props["mail.store.protocol"] = "imaps"
        props["mail.imaps.ssl.enable"] = "true"
        props["mail.imaps.connectiontimeout"] = "10000"
        props["mail.imaps.timeout"] = "10000"
        props["mail.mime.address.strict"] = "false"

        return try {
            val session = Session.getInstance(props)
            session.getStore("imaps").use { store ->
                store.connect(host, port, username, password)
                TestResult.success("IMAP-Verbindung erfolgreich hergestellt.")
            }
        } catch (e: AuthenticationFailedException) {
            log.warn("IMAP-Auth fehlgeschlagen: {}", e.message)
            TestResult.failure("Authentifizierung fehlgeschlagen. Benutzername oder Passwort falsch.")
        } catch (e: MessagingException) {
            log.warn("IMAP-Verbindung fehlgeschlagen: {}", e.message)
            TestResult.failure("Verbindung fehlgeschlagen: ${e.message}")
        }
    }

    fun testGeminiApiKey(apiKey: String?): TestResult {
        if (apiKey.isNullOrBlank()) {
            return TestResult.failure("Kein API Key angegeben.")
        }

        return try {
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            when (response.statusCode()) {
                200 -> TestResult.success("API Key gültig! Verbindung zu Google Gemini erfolgreich.")
                400, 403 -> TestResult.failure("API Key ungültig oder keine Berechtigung (HTTP ${response.statusCode()}).")
                else -> TestResult.failure("Unerwartete Antwort von Google (HTTP ${response.statusCode()}).")
            }
        } catch (e: Exception) {
            log.warn("Gemini API Test fehlgeschlagen: {}", e.message)
            TestResult.failure("Verbindungsfehler: ${e.message}")
        }
    }

    private fun maskValue(value: String?): String {
        if (value.isNullOrBlank() || value == "OVERRIDE_IN_LOCAL") return ""
        if (value.length <= 6) return "***"
        return value.substring(0, 3) + "***" + value.substring(value.length - 3)
    }

    private fun sanitizeValue(value: String?): String {
        val trimmed = value?.trim() ?: return ""
        if (trimmed.isBlank()) return ""
        return when (trimmed.lowercase(Locale.ROOT)) {
            "override_in_local", "smtp.example.com", "change_me_strong_password" -> ""
            else -> trimmed
        }
    }

    private fun hasConfiguredValue(value: String?): Boolean = sanitizeValue(value).isNotBlank()

    data class TestResult(val success: Boolean, val message: String) {
        companion object {
            @JvmStatic
            fun success(message: String): TestResult = TestResult(true, message)

            @JvmStatic
            fun failure(message: String): TestResult = TestResult(false, message)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SystemSettingsService::class.java)
    }
}
