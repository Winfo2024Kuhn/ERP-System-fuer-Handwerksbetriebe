package org.example.kalkulationsprogramm.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.example.kalkulationsprogramm.domain.Anfrage
import org.example.kalkulationsprogramm.domain.Email
import org.example.kalkulationsprogramm.domain.EmailDirection
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp
import org.example.kalkulationsprogramm.domain.Projekt
import org.example.kalkulationsprogramm.repository.EmailRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Collections

@Service
class EmailKiClassificationService(
    private val geminiClient: EmailClassificationGeminiClient,
    private val emailRepository: EmailRepository,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun classify(email: Email, projekte: List<Projekt>, anfragen: List<Anfrage>): ClassificationResult {
        if (!geminiClient.isEnabled()) {
            log.debug("[KI-Classify] Gemini deaktiviert (kein API-Key) - überspringe KI-Zuordnung")
            return ClassificationResult.none("Gemini deaktiviert")
        }

        if (projekte.isEmpty() && anfragen.isEmpty()) {
            return ClassificationResult.none("Keine Kandidaten")
        }

        return try {
            val userPrompt = buildUserPrompt(email, projekte, anfragen)
            val response = geminiClient.chat(CLASSIFICATION_SYSTEM_PROMPT, userPrompt)
            parseResponse(response, projekte, anfragen)
        } catch (e: Exception) {
            log.warn("[KI-Classify] Fehler bei KI-Klassifizierung: {}", e.message)
            ClassificationResult.none("KI-Fehler: " + e.message)
        }
    }

    fun buildUserPrompt(email: Email, projekte: List<Projekt>, anfragen: List<Anfrage>): String {
        val sb = StringBuilder()

        sb.append("═══ EINGEHENDE E-MAIL ═══\n")
        sb.append("Betreff: ").append(nullSafe(email.subject)).append("\n")
        sb.append("Von: ").append(nullSafe(email.fromAddress)).append("\n")
        sb.append("Datum: ").append(email.sentAt?.toString() ?: "unbekannt").append("\n")
        sb.append("Text:\n").append(truncate(nullSafe(email.body), 2000)).append("\n\n")

        sb.append("═══ KANDIDATEN ═══\n\n")

        for (projekt in projekte) {
            val key = "PROJEKT_${projekt.id}"
            sb.append("--- Kandidat: ").append(key).append(" ---\n")
            sb.append("Typ: Projekt\n")
            sb.append("Bauvorhaben: ").append(nullSafe(projekt.bauvorhaben)).append("\n")
            sb.append("Kurzbeschreibung: ").append(nullSafe(projekt.kurzbeschreibung)).append("\n")
            if (projekt.auftragsnummer != null) {
                sb.append("Auftragsnummer: ").append(projekt.auftragsnummer).append("\n")
            }
            sb.append("Kunden-Emails: ").append(projekt.getAllEmails()).append("\n")

            val verlauf = emailRepository.findByProjektOrderBySentAtDesc(projekt)
            appendEmailVerlauf(sb, verlauf)
            sb.append("\n")
        }

        for (anfrage in anfragen) {
            val key = "ANFRAGE_${anfrage.id}"
            sb.append("--- Kandidat: ").append(key).append(" ---\n")
            sb.append("Typ: Anfrage\n")
            sb.append("Bauvorhaben: ").append(nullSafe(anfrage.bauvorhaben)).append("\n")
            sb.append("Kurzbeschreibung: ").append(nullSafe(anfrage.kurzbeschreibung)).append("\n")
            sb.append("Kunden-Emails: ").append(anfrage.kundenEmails).append("\n")

            val verlauf = emailRepository.findByAnfrageOrderBySentAtDesc(anfrage)
            appendEmailVerlauf(sb, verlauf)
            sb.append("\n")
        }

        sb.append("═══ ENDE DER KANDIDATEN ═══\n\n")
        sb.append("Ordne die eingehende E-Mail dem passendsten Kandidaten zu. Antworte NUR mit JSON.")

        return sb.toString()
    }

    private fun appendEmailVerlauf(sb: StringBuilder, verlauf: List<Email>) {
        if (verlauf.isEmpty()) {
            sb.append("Email-Verlauf: (keine bisherigen Emails)\n")
            return
        }

        val limited = verlauf.asSequence().take(10).toMutableList()
        Collections.reverse(limited)

        sb.append("Email-Verlauf (letzte ").append(limited.size).append(" von ").append(verlauf.size).append("):\n")
        for (email in limited) {
            sb.append("  [").append(if (email.direction == EmailDirection.IN) "EINGANG" else "AUSGANG").append("] ")
            sb.append(email.sentAt?.toLocalDate() ?: "?").append(" | ")
            sb.append("Von: ").append(nullSafe(email.fromAddress)).append(" | ")
            sb.append("Betreff: ").append(nullSafe(email.subject)).append("\n")
            sb.append("  ").append(truncate(nullSafe(email.body), 300)).append("\n")
        }
    }

    fun parseResponse(response: String, projekte: List<Projekt>, anfragen: List<Anfrage>): ClassificationResult {
        return try {
            val jsonStr = extractJson(response)
            val json = objectMapper.readTree(jsonStr)

            val key = json.path("key").asText("NONE")
            val confidence = json.path("confidence").asDouble(0.0)
            val reason = json.path("reason").asText("")

            log.info("[KI-Classify] Ergebnis: key={}, confidence={}, reason={}", key, confidence, reason)

            if (key == "NONE" || confidence < 0.5) {
                return ClassificationResult.none(reason)
            }

            if (key.startsWith("PROJEKT_")) {
                val id = key.substring("PROJEKT_".length).toLong()
                val match = projekte.firstOrNull { it.id == id }
                if (match != null) {
                    return ClassificationResult(EmailZuordnungTyp.PROJEKT, match.id, confidence, reason, key)
                }
            } else if (key.startsWith("ANFRAGE_")) {
                val id = key.substring("ANFRAGE_".length).toLong()
                val match = anfragen.firstOrNull { it.id == id }
                if (match != null) {
                    return ClassificationResult(EmailZuordnungTyp.ANFRAGE, match.id, confidence, reason, key)
                }
            }

            log.warn("[KI-Classify] KI hat unbekannten Key zurückgegeben: {}", key)
            ClassificationResult.none("Ungültiger Key: $key")
        } catch (e: Exception) {
            log.warn("[KI-Classify] Fehler beim Parsen der KI-Antwort: {} - Raw: {}", e.message, response)
            ClassificationResult.none("Parse-Fehler: " + e.message)
        }
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    class ClassificationResult(
        private val zuordnungTyp: EmailZuordnungTyp,
        private val entityId: Long?,
        private val confidence: Double,
        private val reason: String?,
        private val key: String,
    ) {
        fun zuordnungTyp(): EmailZuordnungTyp = zuordnungTyp
        fun entityId(): Long? = entityId
        fun confidence(): Double = confidence
        fun reason(): String? = reason
        fun key(): String = key

        fun isAssigned(): Boolean = zuordnungTyp != EmailZuordnungTyp.KEINE && entityId != null

        companion object {
            @JvmStatic
            fun none(reason: String?): ClassificationResult =
                ClassificationResult(EmailZuordnungTyp.KEINE, null, 0.0, reason, "NONE")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailKiClassificationService::class.java)

        private const val CLASSIFICATION_SYSTEM_PROMPT = """
            Du bist ein hochspezialisierter Klassifizierungs-Agent für ein ERP-System eines deutschen Handwerksbetriebs.
            Deine EINZIGE Aufgabe: Ordne eine eingehende E-Mail EXAKT EINEM Projekt oder einer Anfrage zu.
            
            ═══════════════════════════════════════════
            KONTEXT
            ═══════════════════════════════════════════
            
            Du erhältst:
            1. DIE EINGEHENDE E-MAIL (Betreff, Absender, Text)
            2. EINE LISTE VON KANDIDATEN – jeder Kandidat ist ein Projekt oder eine Anfrage mit:
               - Key (eindeutige ID, z.B. "PROJEKT_42" oder "ANFRAGE_17")
               - Bauvorhaben (Name des Bauprojekts/Auftrags)
               - Kurzbeschreibung (was gemacht wird)
               - Auftragsnummer (falls vorhanden)
               - Kunden-Emails (welche Email-Adressen zu diesem Projekt gehören)
               - Bisheriger Email-Verlauf (die letzten Emails in diesem Thread, chronologisch)
            
            ═══════════════════════════════════════════
            ENTSCHEIDUNGSREGELN (absteigend nach Priorität)
            ═══════════════════════════════════════════
            
            1. DIREKTER BEZUG: Wird im Betreff oder Text eine Auftragsnummer, ein Bauvorhaben-Name,
               oder ein spezifisches Detail (Adresse, Stockwerk, Raum, Material) aus einem Kandidaten
               EXPLIZIT genannt? → Dieser Kandidat gewinnt.
            
            2. THEMATISCHE ÜBEREINSTIMMUNG: Passt der Inhalt der Email (beschriebene Arbeiten,
               Materialien, Probleme, Terminvereinbarungen) thematisch zu genau einem der Kandidaten?
               Vergleiche mit Bauvorhaben + Kurzbeschreibung + bisherigem Email-Verlauf.
            
            3. KONVERSATIONS-KONTEXT: Ist die E-Mail offensichtlich eine Antwort auf oder Fortsetzung
               einer Konversation, die im Email-Verlauf eines Kandidaten sichtbar ist?
               Gleiche Themen, gleiche Ansprechpartner, gleicher Tonfall = starkes Signal.
            
            4. ZEITLICHE NÄHE: Wenn der Email-Verlauf eines Kandidaten kürzlich aktiv war und
               die neue Email thematisch dazu passt, bevorzuge diesen Kandidaten.
            
            5. ABSENDER-KONTEXT: Wenn der Absender in nur einem Kandidaten-Verlauf vorkommt,
               ist das ein starkes Indiz (aber NICHT allein ausreichend, wenn die Email
               inhaltlich offensichtlich zu einem anderen Kandidaten gehört).
            
            ═══════════════════════════════════════════
            ANTWORTFORMAT – STRIKT EINHALTEN
            ═══════════════════════════════════════════
            
            Antworte AUSSCHLIESSLICH mit einem JSON-Objekt. Kein Fließtext. Keine Erklärung außerhalb des JSON.
            
            Format:
            {
              "key": "<EXAKTER Key des besten Kandidaten>",
              "confidence": <Zahl 0.0-1.0>,
              "reason": "<1 kurzer Satz auf Deutsch warum>"
            }
            
            - "key": MUSS exakt einem der übergebenen Keys entsprechen (z.B. "PROJEKT_42")
            - "confidence": Deine Zuversicht (0.5 = unsicher, 0.8+ = sehr sicher)
            - "reason": Maximal 1 Satz Begründung
            
            WENN du dir NICHT sicher bist (confidence < 0.5):
            {
              "key": "NONE",
              "confidence": 0.0,
              "reason": "Keine eindeutige Zuordnung möglich"
            }
            
            ═══════════════════════════════════════════
            VERBOTEN
            ═══════════════════════════════════════════
            
            - Erfinde NIEMALS einen Key, der nicht in der Kandidaten-Liste steht.
            - Gib NIEMALS mehr als einen Key zurück.
            - Schreibe NICHTS außer dem JSON-Objekt.
            - Lass dich NICHT durch Email-Signaturen, Werbung oder Disclaimer im Email-Text ablenken.
            - Bewerte NICHT den Inhalt der Email moralisch oder rechtlich.
            """

        private fun nullSafe(value: String?): String = value ?: ""

        private fun truncate(text: String, maxLen: Int): String =
            if (text.length <= maxLen) text else text.substring(0, maxLen) + "…"
    }
}
