package org.example.kalkulationsprogramm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.example.kalkulationsprogramm.domain.Beleg
import org.example.kalkulationsprogramm.domain.Kostenstelle
import org.example.kalkulationsprogramm.domain.Sachkonto
import org.example.kalkulationsprogramm.repository.BelegRepository
import org.example.kalkulationsprogramm.repository.KostenstelleRepository
import org.example.kalkulationsprogramm.repository.SachkontoRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Service
class BelegKiKostenkontoService(
    private val kostenstelleRepository: KostenstelleRepository,
    private val sachkontoRepository: SachkontoRepository,
    private val belegRepository: BelegRepository,
    private val systemSettingsService: SystemSettingsService,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(BelegKiKostenkontoService::class.java)
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()

    @Value("\${ai.gemini.model.dokument-analyse:gemini-3-flash-preview}")
    private lateinit var geminiModel: String

    fun klassifiziereBeleg(beleg: Beleg?) {
        if (beleg == null) return
        if (beleg.kostenstelle != null) {
            log.debug("Beleg {} hat schon Kostenstelle {} — Agent uebersprungen", beleg.id, beleg.kostenstelle?.id)
            return
        }
        val apiKey = systemSettingsService.geminiApiKey
        if (apiKey.isNullOrBlank()) {
            log.warn("Kein Gemini-API-Key konfiguriert — KI-Agent uebersprungen fuer Beleg {}", beleg.id)
            return
        }

        val contents = objectMapper.createArrayNode()
        contents.add(userTurn(buildInitialPrompt(beleg)))
        var ergebnis: AgentErgebnis? = null
        var iteration = 0
        do {
            iteration++
            log.debug("KI-Agent Beleg {} — Iteration {}", beleg.id, iteration)
            val antwort = callGemini(apiKey, contents)
            if (antwort == null) {
                log.warn("KI-Agent Beleg {}: leere Antwort in Iteration {}", beleg.id, iteration)
                break
            }
            val functionCall = findeFunctionCall(antwort)
            if (functionCall == null) {
                ergebnis = parseFreienText(findeText(antwort))
                break
            }
            contents.add(modelTurn(antwort))
            val toolName = functionCall.path("name").asText("")
            val args = functionCall.path("args")
            if (toolName == "finale_zuordnung") {
                ergebnis = parseFinaleZuordnung(args)
                break
            }
            val toolResult = dispatch(toolName, args, beleg)
            if (toolResult != null && toolResult.has("fehler")) {
                log.info("KI-Agent Beleg {}: unbekanntes Tool '{}' — Loop abgebrochen", beleg.id, toolName)
                break
            }
            contents.add(toolResponseTurn(toolName, toolResult))
        } while (iteration < MAX_ITERATIONS)

        if (ergebnis == null) {
            log.info("KI-Agent Beleg {}: keine finale_zuordnung nach {} Iterationen", beleg.id, iteration)
            return
        }
        wendeErgebnisAn(beleg, ergebnis)
    }

    private fun dispatch(name: String, args: JsonNode, beleg: Beleg): JsonNode =
        when (name) {
            "liste_kostenstellen" -> toolListeKostenstellen()
            "liste_sachkonten" -> toolListeSachkonten()
            "aehnliche_belege" -> toolAehnlicheBelege(beleg)
            else -> objectMapper.createObjectNode().put("fehler", "Unbekanntes Tool: $name")
        }

    private fun toolListeKostenstellen(): JsonNode {
        val arr = objectMapper.createArrayNode()
        for (k in kostenstelleRepository.findByAktivTrueOrderBySortierungAsc()) {
            val o = arr.addObject()
            o.put("id", k.id)
            o.put("bezeichnung", safe(k.bezeichnung))
            o.put("typ", k.typ?.name)
            o.put("istFixkosten", k.isIstFixkosten())
            o.put("istInvestition", k.isIstInvestition())
            if (k.beschreibung != null) o.put("beschreibung", safe(k.beschreibung))
        }
        return arr
    }

    private fun toolListeSachkonten(): JsonNode {
        val arr = objectMapper.createArrayNode()
        for (s in sachkontoRepository.findByAktivTrueOrderBySortierungAscBezeichnungAsc()) {
            val o = arr.addObject()
            o.put("id", s.id)
            if (s.nummer != null) o.put("nummer", safe(s.nummer))
            o.put("bezeichnung", safe(s.bezeichnung))
            o.put("typ", s.kontoTyp?.name)
            if (s.beschreibung != null) o.put("beschreibung", safe(s.beschreibung))
        }
        return arr
    }

    private fun toolAehnlicheBelege(beleg: Beleg): JsonNode {
        val arr = objectMapper.createArrayNode()
        val lieferantId = beleg.lieferant?.id ?: return arr
        val historie = belegRepository.findAehnlicheBelegeByLieferant(lieferantId, PageRequest.of(0, AEHNLICHE_BELEGE_LIMIT))
        for (h in historie) {
            if (h.id != null && h.id == beleg.id) continue
            val o = arr.addObject()
            o.put("belegId", h.id)
            o.put("belegDatum", h.belegDatum?.toString())
            o.put("beschreibung", safe(h.beschreibung))
            o.put("betragBrutto", h.betragBrutto?.toPlainString())
            h.kostenstelle?.let {
                o.put("kostenstelleId", it.id)
                o.put("kostenstelleBezeichnung", safe(it.bezeichnung))
            }
            h.sachkonto?.let {
                o.put("sachkontoId", it.id)
                o.put("sachkontoBezeichnung", safe(it.bezeichnung))
            }
        }
        return arr
    }

    private fun parseFinaleZuordnung(args: JsonNode?): AgentErgebnis? {
        if (args == null || args.isMissingNode || args.isNull) return null
        return AgentErgebnis(
            optionalLong(args, "kostenstelleId"),
            optionalLong(args, "sachkontoId"),
            optionalBigDecimal(args, "confidence"),
            optionalString(args, "begruendung"),
        )
    }

    private fun parseFreienText(text: String?): AgentErgebnis? {
        if (text.isNullOrBlank()) return null
        return try {
            val root = objectMapper.readTree(stripFences(text))
            AgentErgebnis(optionalLong(root, "kostenstelleId"), optionalLong(root, "sachkontoId"), optionalBigDecimal(root, "confidence"), optionalString(root, "begruendung"))
        } catch (e: Exception) {
            log.debug("Konnte freien Text der KI nicht parsen: {}", e.message)
            null
        }
    }

    private fun wendeErgebnisAn(beleg: Beleg, ergebnis: AgentErgebnis) {
        beleg.kiVorgeschlagenerKostenstelleId = ergebnis.kostenstelleId
        beleg.kiVorgeschlagenerSachkontoId = ergebnis.sachkontoId
        val clampedConfidence = clampConfidence(ergebnis.confidence)
        beleg.kiKostenkontoConfidence = clampedConfidence?.setScale(2, RoundingMode.HALF_UP)
        var begr = ergebnis.begruendung
        if (begr != null && begr.length > 500) begr = begr.substring(0, 500)
        beleg.kiKostenkontoBegruendung = begr

        val highConfidence = clampedConfidence != null && clampedConfidence.compareTo(AUTO_APPLY_THRESHOLD) >= 0
        var autoAppliedKostenstelle = false
        if (highConfidence && ergebnis.kostenstelleId != null) {
            val ks = kostenstelleRepository.findById(ergebnis.kostenstelleId).orElse(null)
            if (ks != null && ks.isAktiv() && (ks.isIstFixkosten() || ks.isIstInvestition())) {
                beleg.kostenstelle = ks
                autoAppliedKostenstelle = true
            } else if (ks != null) {
                log.info(
                    "Beleg {}: KI-Vorschlag ks={} ({}) ist KEINE aktive Fixkosten/Investitions-Kostenstelle — kein Auto-Apply, Beleg bleibt im Bestellungs-Workflow",
                    beleg.id,
                    ks.id,
                    ks.bezeichnung,
                )
            }
        }
        if (autoAppliedKostenstelle && ergebnis.sachkontoId != null && beleg.sachkonto == null) {
            sachkontoRepository.findById(ergebnis.sachkontoId).filter(Sachkonto::isAktiv).ifPresent { beleg.sachkonto = it }
        }
        log.info("KI-Agent Beleg {}: ks={} sk={} conf={} auto={}", beleg.id, ergebnis.kostenstelleId, ergebnis.sachkontoId, clampedConfidence, autoAppliedKostenstelle)
    }

    private fun callGemini(apiKey: String, contents: ArrayNode): JsonNode? {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$geminiModel:generateContent?key=$apiKey"
        val requestBody = try {
            val body = objectMapper.createObjectNode()
            body.set<JsonNode>("contents", contents.deepCopy())
            body.set<JsonNode>("tools", buildToolDeclarations())
            val systemInstruction = body.putObject("systemInstruction")
            systemInstruction.putArray("parts").addObject().put("text", SYSTEM_INSTRUCTION)
            objectMapper.writeValueAsString(body)
        } catch (e: Exception) {
            log.warn("KI-Agent Gemini-Call: Request konnte nicht serialisiert werden: {}", e.message)
            return null
        }
        val req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(45))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()

        for (attempt in 1..MAX_GEMINI_ATTEMPTS) {
            try {
                val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
                val status = resp.statusCode()
                if (status == 200) return objectMapper.readTree(resp.body())
                var errBody = resp.body()
                if (errBody != null && errBody.length > 500) errBody = errBody.substring(0, 500) + "..."
                if (isRetryableStatus(status) && attempt < MAX_GEMINI_ATTEMPTS) {
                    val backoff = RETRY_BACKOFF_MS[attempt - 1]
                    log.warn("KI-Agent Gemini-Call Versuch {}/{}: HTTP {} — retry in {} ms", attempt, MAX_GEMINI_ATTEMPTS, status, backoff)
                    sleepQuiet(backoff)
                    continue
                }
                log.warn("KI-Agent Gemini-Call (Versuch {}/{}): HTTP {} — {}", attempt, MAX_GEMINI_ATTEMPTS, status, errBody)
                return null
            } catch (ioe: java.io.IOException) {
                if (attempt < MAX_GEMINI_ATTEMPTS) {
                    val backoff = RETRY_BACKOFF_MS[attempt - 1]
                    log.warn("KI-Agent Gemini-Call Versuch {}/{}: IO-Fehler ({}) — retry in {} ms", attempt, MAX_GEMINI_ATTEMPTS, ioe.message, backoff)
                    sleepQuiet(backoff)
                    continue
                }
                log.warn("KI-Agent Gemini-Call fehlgeschlagen nach {} Versuchen: {}", MAX_GEMINI_ATTEMPTS, ioe.message)
                return null
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn("KI-Agent Gemini-Call unterbrochen: {}", ie.message)
                return null
            } catch (e: Exception) {
                log.warn("KI-Agent Gemini-Call fehlgeschlagen: {}", e.message)
                return null
            }
        }
        return null
    }

    private fun buildToolDeclarations(): ArrayNode {
        val tools = objectMapper.createArrayNode()
        val decls = tools.addObject().putArray("functionDeclarations")
        decls.add(decl("liste_kostenstellen", "Liefert die Liste aller aktiven Kostenstellen aus der Datenbank (id, bezeichnung, typ, istFixkosten, istInvestition).", emptyObjectSchema()))
        decls.add(decl("liste_sachkonten", "Liefert die Liste aller aktiven Sachkonten aus der Datenbank (id, nummer, bezeichnung, typ).", emptyObjectSchema()))
        decls.add(decl("aehnliche_belege", "Liefert die letzten Belege desselben Lieferanten, die schon eine Kostenstellen-Zuordnung haben. Nutze das, um historische Zuordnungen als Lernmaterial zu sehen.", emptyObjectSchema()))
        val finalSchema = objectMapper.createObjectNode()
        finalSchema.put("type", "OBJECT")
        val props = finalSchema.putObject("properties")
        props.putObject("kostenstelleId").put("type", "INTEGER").put("description", "Die gewaehlte Kostenstellen-ID aus liste_kostenstellen. null erlaubt, wenn keine passt.")
        props.putObject("sachkontoId").put("type", "INTEGER").put("description", "Die gewaehlte Sachkonto-ID aus liste_sachkonten. null erlaubt, wenn keine passt.")
        props.putObject("confidence").put("type", "NUMBER").put("description", "0.0 bis 1.0 — wie sicher du dir bist.")
        props.putObject("begruendung").put("type", "STRING").put("description", "Ein kurzer deutscher Satz, warum diese Wahl.")
        finalSchema.putArray("required").add("confidence").add("begruendung")
        decls.add(decl("finale_zuordnung", "Beendet den Agent-Lauf und liefert die finale Kostenstellen- und Sachkonto-Zuordnung. Rufe das erst auf, NACHDEM du die Listen geladen hast.", finalSchema))
        return tools
    }

    private fun decl(name: String, description: String, parameters: ObjectNode): ObjectNode =
        objectMapper.createObjectNode().apply {
            put("name", name)
            put("description", description)
            set<JsonNode>("parameters", parameters)
        }

    private fun emptyObjectSchema(): ObjectNode =
        objectMapper.createObjectNode().apply {
            put("type", "OBJECT")
            putObject("properties")
        }

    private fun userTurn(text: String): ObjectNode =
        objectMapper.createObjectNode().apply {
            put("role", "user")
            putArray("parts").addObject().put("text", text)
        }

    private fun modelTurn(antwort: JsonNode): ObjectNode {
        val turn = objectMapper.createObjectNode()
        turn.put("role", "model")
        val content = antwort.path("candidates").path(0).path("content")
        if (content.isObject && content.has("parts")) {
            turn.set<JsonNode>("parts", content.get("parts").deepCopy())
        } else {
            turn.putArray("parts")
        }
        return turn
    }

    private fun toolResponseTurn(toolName: String, result: JsonNode): ObjectNode {
        val turn = objectMapper.createObjectNode()
        turn.put("role", "user")
        val functionResponse = turn.putArray("parts").addObject().putObject("functionResponse")
        functionResponse.put("name", toolName)
        val responseWrapper = functionResponse.putObject("response")
        responseWrapper.put("name", toolName)
        responseWrapper.set<JsonNode>("content", result)
        return turn
    }

    private fun findeFunctionCall(antwort: JsonNode): JsonNode? {
        val parts = antwort.path("candidates").path(0).path("content").path("parts")
        if (!parts.isArray) return null
        for (part in parts) {
            val fc = part.get("functionCall")
            if (fc != null && !fc.isNull) return fc
        }
        return null
    }

    private fun findeText(antwort: JsonNode): String? {
        val parts = antwort.path("candidates").path(0).path("content").path("parts")
        if (!parts.isArray) return null
        val sb = StringBuilder()
        for (part in parts) {
            if (part.has("text")) sb.append(part.get("text").asText())
        }
        return sb.toString()
    }

    private fun buildInitialPrompt(beleg: Beleg): String {
        val lieferantName = beleg.lieferant?.lieferantenname ?: beleg.kiVorgeschlagenerLieferant
        val hatLieferantId = beleg.lieferant?.id != null
        return """
            Klassifiziere diesen Beleg.

            BELEG-DATEN:
            - Lieferant: ${nullToDash(lieferantName)}${if (hatLieferantId) "" else " (kein Lieferant in der DB verknuepft — aehnliche_belege ist dann leer)"}
            - Belegnummer: ${nullToDash(beleg.belegNummer)}
            - Belegdatum: ${nullToDash(beleg.belegDatum?.toString())}
            - Brutto: ${nullToDash(beleg.betragBrutto?.toPlainString())} EUR
            - Netto: ${nullToDash(beleg.betragNetto?.toPlainString())} EUR
            - MwSt-Satz: ${nullToDash(beleg.mwstSatz?.toPlainString())}
            - Beschreibung: ${nullToDash(beleg.beschreibung)}
            - Dokumenttyp: ${nullToDash(beleg.dokumentTyp?.name)}
            - Original-Dateiname: ${nullToDash(beleg.originalDateiname)}

            Beginne jetzt mit den Tool-Aufrufen wie in der System-Instruktion beschrieben.
        """.trimIndent()
    }

    private data class AgentErgebnis(
        val kostenstelleId: Long?,
        val sachkontoId: Long?,
        val confidence: BigDecimal?,
        val begruendung: String?,
    )

    companion object {
        private val AUTO_APPLY_THRESHOLD = BigDecimal("0.95")
        private const val MAX_ITERATIONS = 6
        private const val AEHNLICHE_BELEGE_LIMIT = 8
        const val MAX_GEMINI_ATTEMPTS = 3
        private val RETRY_BACKOFF_MS = longArrayOf(1_000L, 3_000L)

        @JvmStatic
        fun isRetryableStatus(status: Int): Boolean = status == 429 || status in 500..599

        private fun sleepQuiet(ms: Long) {
            try {
                Thread.sleep(ms)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        private fun clampConfidence(raw: BigDecimal?): BigDecimal? {
            if (raw == null) return null
            if (raw < BigDecimal.ZERO) return BigDecimal.ZERO
            if (raw > BigDecimal.ONE) return BigDecimal.ONE
            return raw
        }

        private fun stripFences(text: String?): String {
            if (text == null) return ""
            val t = text.trim()
            if (t.startsWith("```json")) {
                val start = t.indexOf("```json") + 7
                val end = t.indexOf("```", start)
                if (end > start) return t.substring(start, end).trim()
            } else if (t.startsWith("```")) {
                val start = t.indexOf("```") + 3
                val end = t.indexOf("```", start)
                if (end > start) return t.substring(start, end).trim()
            }
            return t
        }

        private fun optionalLong(root: JsonNode, field: String): Long? {
            val n = root.get(field) ?: return null
            if (n.isNull) return null
            if (n.isNumber) return n.asLong()
            val s = n.asText().trim()
            if (s.isEmpty() || s.equals("null", ignoreCase = true)) return null
            return s.toLongOrNull()
        }

        private fun optionalBigDecimal(root: JsonNode, field: String): BigDecimal? {
            val n = root.get(field) ?: return null
            if (n.isNull) return null
            return try {
                BigDecimal(n.asText())
            } catch (_: Exception) {
                null
            }
        }

        private fun optionalString(root: JsonNode, field: String): String? {
            val n = root.get(field) ?: return null
            if (n.isNull) return null
            return n.asText()?.trim()?.takeIf { it.isNotBlank() }
        }

        private fun safe(s: String?): String = s?.replace('\n', ' ')?.replace('\r', ' ')?.trim() ?: ""
        private fun nullToDash(s: String?): String = if (s.isNullOrBlank()) "-" else s

        private const val SYSTEM_INSTRUCTION = """
Du bist ein autonomer Buchhaltungs-Agent fuer einen deutschen Handwerksbetrieb.
Du arbeitest in mehreren Schritten: erst Informationen sammeln, dann entscheiden.

VERPFLICHTENDE ARBEITSWEISE:
1. Rufe zuerst liste_kostenstellen auf.
2. Rufe danach liste_sachkonten auf.
3. Wenn der Beleg einen Lieferanten hat, rufe aehnliche_belege auf.
4. Erst dann darfst du finale_zuordnung mit deiner Entscheidung aufrufen.

EISERNE REGEL: Du darfst eine Kostenstelle NUR vorschlagen, wenn der Beleg EINDEUTIG ein laufender Fixkosten-/Gemeinkosten-Posten ist. Projekt-Material, Baustellen-Geraetemieten und Fremdleistungen muessen kostenstelleId=null bekommen.

FORMAT-REGELN:
1. Verwende NUR IDs aus den Tool-Ergebnissen.
2. Setze IDs auf null bei Unsicherheit.
3. Kostenstellen vom Typ PROJEKT NIEMALS waehlen.
4. Confidence >= 0.95 nur bei 100% klaren Treffern.
5. Antworte ausschliesslich ueber Tool-Aufrufe.
"""
    }
}
