package org.example.kalkulationsprogramm.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageFunnelRequestDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import java.util.Optional

@Service
open class AnfrageFunnelSpamFilterService(
    private val backends: List<SpamFilterChatBackend>,
    private val objectMapper: ObjectMapper,
    private val systemSettingsService: SystemSettingsService,
) {

    open fun pruefe(dto: AnfrageFunnelRequestDto?): Result {
        if (dto == null) {
            return Result.ok()
        }
        if (!systemSettingsService.isAnfrageFunnelSpamFilterAktiv) {
            log.debug("Funnel-Spam-Filter uebersprungen (im Firma-Editor deaktiviert)")
            return Result.ok()
        }

        val backendOpt = waehleBackend()
        if (backendOpt.isEmpty) {
            log.debug("Funnel-Spam-Filter uebersprungen (kein Backend konfiguriert)")
            return Result.ok()
        }
        val backend = backendOpt.get()

        try {
            val raw = backend.chat(SYSTEM_PROMPT, baueUserPayload(dto))
            return parseAntwort(raw)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warn(
                "Funnel-Spam-Filter Backend '{}' unterbrochen, lasse Anfrage durch",
                backend.identifier(),
            )
            return Result.ok()
        } catch (e: Exception) {
            log.warn(
                "Funnel-Spam-Filter Fehler ueber Backend '{}' ({}), lasse Anfrage durch",
                backend.identifier(),
                e.message,
            )
            return Result.ok()
        }
    }

    private fun waehleBackend(): Optional<SpamFilterChatBackend> {
        val gewuenscht = systemSettingsService.anfrageFunnelSpamFilterProvider
        if ("aus".equals(gewuenscht, ignoreCase = true)) {
            return Optional.empty()
        }
        for (backend in backends) {
            if (backend.identifier().equals(gewuenscht, ignoreCase = true) && backend.isEnabled()) {
                return Optional.of(backend)
            }
        }
        for (backend in backends) {
            if (LocalSpamFilterChatBackend.ID == backend.identifier() && backend.isEnabled()) {
                return Optional.of(backend)
            }
        }
        for (backend in backends) {
            if (backend.isEnabled()) {
                return Optional.of(backend)
            }
        }
        return Optional.empty()
    }

    private fun baueUserPayload(dto: AnfrageFunnelRequestDto): String {
        val sb = StringBuilder()
        sb.append("Vorname: ").append(safe(dto.vorname)).append('\n')
        sb.append("Nachname: ").append(safe(dto.nachname)).append('\n')
        sb.append("E-Mail: ").append(safe(dto.email)).append('\n')
        sb.append("Telefon: ").append(safe(dto.telefon)).append('\n')
        sb.append("Projekt-Anschrift: ").append(safe(dto.projektAnschrift)).append('\n')
        sb.append("Service-Typ: ").append(safe(dto.serviceTyp)).append('\n')
        val projektarten = dto.projektarten
        if (!projektarten.isNullOrEmpty()) {
            sb.append("Projektarten: ").append(projektarten.joinToString(", ")).append('\n')
        }
        sb.append("Nachricht: ").append(safe(dto.nachricht)).append('\n')
        return sb.toString()
    }

    private fun parseAntwort(raw: String?): Result {
        if (!StringUtils.hasText(raw)) {
            return Result.ok()
        }
        return try {
            val node = objectMapper.readTree(raw!!.trim())
            val spam = node.path("spam").asBoolean(false)
            if (!spam) {
                Result.ok()
            } else {
                val grund = node.path("grund").asText("Anfrage wirkt nicht ernst gemeint")
                Result.spam(grund)
            }
        } catch (e: Exception) {
            log.warn("Funnel-Spam-Filter konnte Antwort nicht parsen: '{}'", raw)
            Result.ok()
        }
    }

    data class Result(
        val spam: Boolean,
        val grund: String?,
    ) {
        fun spam(): Boolean = spam

        fun grund(): String? = grund

        companion object {
            @JvmStatic
            fun ok(): Result = Result(false, null)

            @JvmStatic
            fun spam(grund: String?): Result = Result(true, grund)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AnfrageFunnelSpamFilterService::class.java)

        private fun safe(value: String?): String = value?.trim() ?: ""

        private const val SYSTEM_PROMPT = """
            Du bist ein Spam-Filter fuer das Kontaktformular eines Handwerksbetriebs.
            Du bekommst eine Anfrage von der Webseite. Entscheide, ob es sich um
            eine ERNST GEMEINTE Anfrage handelt oder um Muell/Spass/Beleidigung.

            Markiere als SPAM, wenn:
            - Name, E-Mail oder Nachricht offensichtlicher Unsinn sind
              ("test", "asdf", "qwertz", "111", "lol", "leck mich", "fick dich" o.ae.)
            - Die E-Mail-Adresse beleidigend oder unsinnig wirkt
              (z.B. "leckmichaa@test.de", "asdf@asdf.de", "test123@xyz")
            - Die Nachricht beleidigend, sexuell, drohend oder klar ironisch ist
            - Der gesamte Inhalt nach automatisiertem Spam aussieht
              (Werbung, SEO-Keywords, Links zu fremden Seiten)

            Behandle als ECHT, wenn auch nur grob plausibel:
            - Realistischer Name + plausible E-Mail + minimaler Bezug zum Handwerk
            - Auch kurze Anfragen sind OK ("Treppe gewuenscht, bitte Rueckruf")
            - Bei Zweifel: ECHT.

            Antworte AUSSCHLIESSLICH mit kompaktem JSON in genau diesem Format:
            {"spam": true|false, "grund": "kurzer deutscher Grund, max. 80 Zeichen"}
            """
    }
}
