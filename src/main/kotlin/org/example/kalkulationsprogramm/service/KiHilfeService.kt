package org.example.kalkulationsprogramm.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

@Service
class KiHilfeService(
    private val objectMapper: ObjectMapper,
    private val codebaseIndexService: CodebaseIndexService,
    private val localRagService: LocalRagService,
    private val systemSettingsService: SystemSettingsService,
) {
    data class PageContext(
        val route: String?,
        val pageTitle: String?,
        val visibleContent: String?,
        val errorMessages: String?,
        val latitude: Double?,
        val longitude: Double?,
    )

    data class ChatResult(val reply: String, val sources: List<SourceLink>) {
        fun reply(): String = reply
        fun sources(): List<SourceLink> = sources
    }

    data class SourceLink(val title: String, val url: String)

    data class ChatMessage(val role: String?, val text: String?) {
        fun role(): String? = role
        fun text(): String? = text
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    @Value("\${ai.ki-hilfe.model:gemini-3-flash-preview}")
    private lateinit var model: String

    @Value("\${ai.ki-hilfe.temperature:0.4}")
    private var temperature: Double = 0.4

    @Value("\${ai.ki-hilfe.max-output-tokens:8192}")
    private var maxOutputTokens: Int = 8192

    @Value("\${ai.ki-hilfe.web-search-enabled:true}")
    private var webSearchEnabled: Boolean = true

    fun chat(messages: List<ChatMessage>): ChatResult = chat(messages, null)

    @Throws(IOException::class, InterruptedException::class)
    fun chat(messages: List<ChatMessage>?, pageContext: PageContext?): ChatResult {
        val geminiApiKey = systemSettingsService.geminiApiKey
        if (geminiApiKey.isNullOrBlank()) {
            throw IOException("Gemini API Key fehlt (ai.gemini.api-key)")
        }
        require(!messages.isNullOrEmpty()) { "Keine Nachrichten angegeben" }

        val limitedMessages = if (messages.size > 20) messages.subList(messages.size - 20, messages.size) else messages

        var ragContext: String? = null
        var ragUsed = false
        if (localRagService.isAvailable()) {
            log.info("  -> Lokales RAG ist verfuegbar, starte Vektor-Suche...")
            try {
                val latestUserMessage = limitedMessages
                    .filter { it.role == "user" }
                    .lastOrNull()
                    ?.text
                    .orEmpty()

                val contextHint = pageContext?.let {
                    "Seite: %s (%s)".format(it.pageTitle.orEmpty(), it.route.orEmpty())
                }

                val results = localRagService.search(latestUserMessage, contextHint, pageContext?.route)
                if (results.isNotEmpty()) {
                    ragContext = localRagService.buildContextFromResults(results)
                    ragUsed = true
                    log.info("  [OK] RAG: {} relevante Code-Chunks gefunden ({} Zeichen Kontext)", results.size, ragContext.length)
                    for (r in results) {
                        log.info("    - [{}] {} -- {} ({})", "%.2f".format(r.score()), r.filePath(), r.name(), r.chunkType())
                    }
                } else {
                    log.info("  [X] RAG: Keine passenden Chunks gefunden, Fallback auf Full-Index")
                }
            } catch (e: Exception) {
                log.warn("  [X] RAG-Suche fehlgeschlagen, Fallback auf Full-Index: {}", e.message)
            }
        } else {
            log.info(
                "  -> RAG nicht verfuegbar (enabled={}, ready={}), nutze Full-Codebase-Index",
                localRagService.isEnabled(),
                localRagService.isReady(),
            )
        }

        val pageContextInfo = buildPageContextInfo(pageContext)
        if (!pageContextInfo.isNullOrBlank()) {
            log.info("  -> Seitenkontext ({} Zeichen) wird an Nachricht angehaengt", pageContextInfo.length)
        }

        log.info("  -> Prompt-Modus: {}", if (ragUsed) "RAG (Vektor-Suche)" else "Full-Codebase-Index")

        val url = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"
            .format(model, geminiApiKey)

        val requestBody = objectMapper.createObjectNode()
        val systemInstruction = objectMapper.createObjectNode()
        val sysParts = objectMapper.createArrayNode()
        sysParts.add(objectMapper.createObjectNode().put("text", buildSystemPrompt(ragContext)))
        systemInstruction.set<ArrayNode>("parts", sysParts)
        requestBody.set<ObjectNode>("systemInstruction", systemInstruction)

        val contents = objectMapper.createArrayNode()
        for (i in limitedMessages.indices) {
            val msg = limitedMessages[i]
            val role = if (msg.role == "user") "user" else "model"
            val msgNode = objectMapper.createObjectNode().put("role", role)
            val parts = objectMapper.createArrayNode()
            var text = msg.text.orEmpty()
            val isLastUserMessage = msg.role == "user" && i == limitedMessages.size - 1
            if (isLastUserMessage && !pageContextInfo.isNullOrBlank()) {
                text += "\n\n$pageContextInfo"
            }
            parts.add(objectMapper.createObjectNode().put("text", text))
            msgNode.set<ArrayNode>("parts", parts)
            contents.add(msgNode)
        }
        requestBody.set<ArrayNode>("contents", contents)

        val config = objectMapper.createObjectNode()
        config.put("temperature", temperature)
        config.put("maxOutputTokens", maxOutputTokens)
        requestBody.set<ObjectNode>("generationConfig", config)

        if (webSearchEnabled) {
            val tools = objectMapper.createArrayNode()
            val searchTool = objectMapper.createObjectNode()
            searchTool.set<ObjectNode>("googleSearch", objectMapper.createObjectNode())
            tools.add(searchTool)
            requestBody.set<ArrayNode>("tools", tools)
        }

        val body = objectMapper.writeValueAsString(requestBody)
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() != 200) {
            log.error("Gemini KI-Hilfe API Error {}: {}", response.statusCode(), response.body())
            throw IOException("KI-Hilfe nicht verfügbar (Fehler ${response.statusCode()})")
        }

        val root = objectMapper.readTree(response.body())
        val candidates = root.path("candidates")
        if (candidates.isArray && !candidates.isEmpty) {
            val candidate = candidates[0]
            val gm = candidate.path("groundingMetadata")
            if (!gm.isMissingNode) {
                val queries = gm.path("webSearchQueries")
                if (queries.isArray && !queries.isEmpty) {
                    log.info("  -> Google Search Queries: {}", queries)
                }
            }

            val partsNode = candidate.path("content").path("parts")
            if (partsNode.isArray && !partsNode.isEmpty) {
                val replyBuilder = StringBuilder()
                for (part in partsNode) {
                    val partText = part.path("text").asText("")
                    if (partText.isNotEmpty()) {
                        if (replyBuilder.isNotEmpty()) {
                            replyBuilder.append("\n")
                        }
                        replyBuilder.append(partText)
                    }
                }

                val replyText = replyBuilder.toString()
                if (replyText.isEmpty()) {
                    throw IOException("Keine Textantwort von der KI erhalten")
                }

                val sources = extractGroundingSources(candidate)
                if (sources.isNotEmpty()) {
                    log.info("  -> {} Web-Quellen aus Google Search Grounding extrahiert", sources.size)
                    sources.forEach { log.info("    - [{}] {}", it.title, it.url) }
                }
                return ChatResult(replyText, sources)
            }
        }

        throw IOException("Keine Antwort von der KI erhalten")
    }

    private fun buildSystemPrompt(ragContext: String?): String {
        if (!ragContext.isNullOrBlank()) {
            return BASE_SYSTEM_PROMPT +
                "\n\n## Relevanter Quellcode (per Vektor-Suche gefunden)\n" +
                "Die folgenden Code-Abschnitte wurden automatisch als relevant fuer die aktuelle Frage identifiziert. " +
                "Abschnitte mit '>>> AKTUELLE SEITE DES BENUTZERS <<<' zeigen den EXAKTEN Quellcode der Seite, " +
                "auf der sich der Benutzer GERADE befindet. Diese Abschnitte haben HOECHSTE Prioritaet: " +
                "Nur Buttons, Tabs, Formulare und Funktionen die IN DIESEM CODE vorkommen, existieren auf dieser Seite. " +
                "Beschreibe NUR das, was du im Code der aktuellen Seite tatsaechlich siehst.\n\n" +
                ragContext
        }

        val index = codebaseIndexService.index
        if (index.isNullOrEmpty()) {
            return BASE_SYSTEM_PROMPT
        }
        return BASE_SYSTEM_PROMPT +
            "\n\n## Frontend-Quellcode & Dokumentation (Read-Only Wissensbasis)\n" +
            "Im Folgenden findest du den KOMPLETTEN Frontend-Quellcode des Kalkulationsprogramms: " +
            "Alle React-Seiten (Pages), UI-Komponenten, Navigation (App.tsx Routing), " +
            "Hooks, Hilfsfunktionen und die Projekt-Dokumentation. " +
            "Nutze diesen Code, um Benutzern zu erklären, wie sie sich im Programm " +
            "zurechtfinden, welche Funktionen auf welcher Seite verfügbar sind, " +
            "und wie Workflows Schritt für Schritt ablaufen.\n\n" +
            index
    }

    private fun buildPageContextInfo(ctx: PageContext?): String? {
        if (ctx == null) {
            return null
        }
        val sb = StringBuilder("[Aktueller Bildschirmkontext]\n")
        if (!ctx.route.isNullOrBlank()) {
            sb.append("Seite: ").append(ctx.route).append("\n")
        }
        if (!ctx.pageTitle.isNullOrBlank()) {
            sb.append("Titel: ").append(ctx.pageTitle).append("\n")
        }
        if (!ctx.errorMessages.isNullOrBlank()) {
            sb.append("Fehlermeldungen: ").append(ctx.errorMessages).append("\n")
        }
        if (!ctx.visibleContent.isNullOrBlank()) {
            var content = ctx.visibleContent
            if (content.length > 2000) {
                content = content.substring(0, 2000) + "…"
            }
            sb.append("Sichtbare Daten:\n").append(content).append("\n")
        }
        if (ctx.latitude != null && ctx.longitude != null) {
            sb.append("Standort (Geodaten): Breitengrad ").append(ctx.latitude)
                .append(", Laengengrad ").append(ctx.longitude).append("\n")
        }
        return sb.toString()
    }

    private fun extractGroundingSources(candidate: JsonNode): List<SourceLink> {
        val groundingMetadata = candidate.path("groundingMetadata")
        if (groundingMetadata.isMissingNode) {
            return emptyList()
        }
        val chunks = groundingMetadata.path("groundingChunks")
        if (!chunks.isArray || chunks.isEmpty) {
            return emptyList()
        }
        val sources = ArrayList<SourceLink>()
        for (chunk in chunks) {
            val web = chunk.path("web")
            val uri = web.path("uri").asText("")
            val title = web.path("title").asText("")
            if (uri.isNotEmpty()) {
                sources.add(SourceLink(if (title.isEmpty()) uri else title, uri))
            }
        }
        return sources
    }

    companion object {
        private val log = LoggerFactory.getLogger(KiHilfeService::class.java)

        private val BASE_SYSTEM_PROMPT = """
            Du bist der KI-Assistent für das Kalkulationsprogramm der Bauschlosserei Kuhn.
            Du hilfst Mitarbeitern bei Fragen zur Bedienung und Navigation des Programms,
            sowie bei allgemeinen Fachfragen (Normen, Vorschriften, Wetter, etc.).
            Antworte immer auf Deutsch, freundlich und präzise.
            
            ## Antwortstil
            - Fasse dich KURZ und KOMPAKT. Maximal 5-8 Saetze fuer einfache Fragen.
            - Nutze Tabellen und Aufzaehlungen statt langer Fliesstext-Absaetze.
            - Komme direkt zum Punkt, keine langen Einleitungen oder Wiederholungen der Frage.
            - Bei Fachfragen (DIN, EN, ISO): Gib die Kerninfo als Tabelle, dann maximal 2-3 Saetze Erlaeuterung.
            - Vermeide redundante Tipps und "Falls du noch Fragen hast"-Floskeln.
            - Nur wenn der Benutzer explizit nach Details fragt, antworte ausfuehrlicher.
            - Wenn der user eine Frage außerhalb des Programms stellt, nutze die Web-Suche um eine aktuelle und fundierte Antwort zu geben.
            
            ## Verhaltensregeln
            - Du hilfst Benutzern, sich im Programm zurechtzufinden: Wo finde ich was? Wie mache ich X?
            - Du hast Zugriff auf den VOLLSTÄNDIGEN Frontend-Quellcode (alle React-Seiten, Komponenten, Navigation)
            - Erkläre Schritt für Schritt, wie der Benutzer zu einer Funktion navigiert
            - Beschreibe welche Buttons, Menüpunkte und Formulare auf jeder Seite verfügbar sind
            - Wenn du nach einem Workflow gefragt wirst (z.B. "Wie erstelle ich ein Anfrage?"), erkläre die Schritte aus Benutzersicht
            - Nutze die nachfolgende Navigationsstruktur um EXAKT zu beschreiben, wo eine Funktion zu finden ist
            - Nenne IMMER den übergeordneten Hauptreiter (z.B. "Projektmanagement") bevor du einen Untermenüpunkt nennst
            - Wenn du etwas nicht finden kannst, sage es ehrlich
            - Verweise bei technischen Problemen auf den Administrator
            - Formatiere Antworten mit Markdown für bessere Lesbarkeit
            - Nutze Tabellen fuer strukturierte Daten (z.B. Zeugnistypen, Anforderungen, Vergleiche)
            - Nutze Aufzählungen und Nummerierungen für Schritt-für-Schritt-Anleitungen
            
            ## Kontextsensitive Hilfe (Laufzeitdaten)
            Du erhältst zusammen mit der Benutzerfrage den AKTUELLEN SEITENKONTEXT:
            - **Route:** Auf welcher Seite der Benutzer sich befindet (z.B. /projekte/123)
            - **Seitentitel:** Überschrift der aktuellen Seite
            - **Projekt-Status:** Status des Projekts selbst (z.B. "Bezahlt", "Offen") — das ist NICHT der Status einzelner Dokumente!
            - **Kennzahlen:** Brutto, Netto, Arbeitskosten, Material, Gewinn
            - **Aktiver Tab:** Der aktuell sichtbare Tab (z.B. "Geschäftsdokumente", "Zeiten", "E-Mails")
            - **Geschäftsdokumente:** Auflistung aller Dokumente mit Typ, Status und Nummer.
              Format: "Typ | Status Nr. Dokumentnummer 'Betreff' Betrag"
              WICHTIG: Jedes Dokument hat seinen EIGENEN Status! z.B.:
              - "Anfrage | Entwurf Nr. 2026/03/00018" = Anfrage ist noch ein Entwurf
              - "Rechnung | Gebucht Nr. 2026/03/00022" = Rechnung wurde gebucht (gesperrt!)
              Verwechsle NIEMALS den Projekt-Status mit dem Dokument-Status!
            - **Seitenleiste:** Kundendaten, Auftragsnummer, Ansprechpartner etc.
            - **Deaktivierte Buttons:** Welche Aktionen sind gesperrt und warum
            - **Fehlermeldungen:** Aktuell angezeigte Fehler auf dem Bildschirm
            - **Hinweise:** Info-Karten mit Geschäftsregeln
            
            Nutze diese KONKRETEN LAUFZEITDATEN um dem Benutzer SPEZIFISCHE Antworten zu geben:
            - Statt "Prüfe ob die Rechnung gebucht ist" → "Deine Rechnung 2026/03/00022 hat den Status 'Gebucht', daher kann sie nicht gelöscht werden."
            - Statt "Fülle alle Pflichtfelder aus" → "Das Feld 'Projektname' ist leer — trage einen Namen ein, dann kannst du speichern."
            - Statt "Der Button könnte deaktiviert sein" → "Der Button 'Löschen' ist deaktiviert, weil erst das Projekt abgeschlossen werden muss."
            - Achte auf den UNTERSCHIED zwischen Projekt-Status und Dokument-Status:
              FALSCH: "Die Rechnung hat den Status Bezahlt" (wenn Bezahlt nur der Projekt-Status ist)
              RICHTIG: "Die Rechnung 2026/03/00022 hat den Status 'Gebucht'. Das Projekt selbst ist 'Bezahlt'."
            - Erkläre Zusammenhänge: "Gebuchte Rechnungen können nicht gelöscht werden — du kannst sie nur stornieren. Klick auf die Rechnung und wähle 'Stornieren'."
            
            ## STRENGE ANTI-HALLUZINATIONS-REGEL (ABSOLUT WICHTIG!)
            
            Du darfst NUR Buttons, Icons, Formulare, Dialoge und Funktionen beschreiben, die du
            TATSÄCHLICH im bereitgestellten **FRONTEND-Quellcode (React/TSX)** finden kannst.
            
            KRITISCHE UNTERSCHEIDUNG:
            - **Backend-Code** (Java Controller, Services) zeigt dir, welche API-Endpunkte existieren.
            - **Frontend-Code** (React TSX-Komponenten) zeigt dir, was der Benutzer TATSÄCHLICH auf dem Bildschirm sieht.
            - NUR weil ein Backend-Endpunkt existiert, heißt das NICHT, dass es dafür auf JEDER Seite UI gibt!
            - Beispiel: Es gibt Backend-Endpunkte für Zeitbuchungen, aber der "Zeiten"-Tab in der Projektansicht 
              ist NUR eine Anzeige — die Bearbeitung findet auf einer ANDEREN Seite statt (Zeiterfassungs-Kalender).
            
            VERBOTEN:
            - Erfinde NIEMALS UI-Elemente die nicht im Frontend-Code stehen (z.B. Stift-Symbole, Bearbeiten-Buttons,
              Löschen-Icons, Dialoge oder Formulare die du dir nur vorstellst)
            - Sage NIEMALS "Klicke auf [Button X]" oder "Wähle dort die Buchung aus" wenn du diesen Button/diese
              Interaktion NICHT in einer React-Komponente (TSX-Datei) finden kannst
            - Schließe NIEMALS von einem Backend-Endpunkt darauf, dass eine bestimmte Seite Bearbeitungsfunktionen hat
            - Erfinde KEINE "Möglichkeit 2" oder alternative Wege, wenn du nur EINEN Weg im Code findest
            - Sage NIEMALS "Du kannst Zeiten auch direkt im Projekt anpassen" — prüfe ERST ob das im TSX-Code steht!
            
            WENN DU UNSICHER BIST:
            - Sage ehrlich: "Ich bin mir nicht sicher, ob diese Funktion auf dieser Seite verfügbar ist."
            - Beschreibe NUR den einen Weg, den du sicher im Code findest
            - Nenne KEINE Alternativen die du nur vermutest
            
            PRINZIP: Nur Fakten aus dem FRONTEND-Code. Backend-Code ≠ UI-Verfügbarkeit. Lieber nur einen
            korrekten Weg nennen als zwei Wege, von denen einer erfunden ist.
            
            ## Web-Suche & Allgemeinwissen
            Du hast Zugriff auf die Google-Suche (googleSearch Tool) und kannst aktuelle Informationen aus dem Internet abrufen.
            
            REGEL: Wenn eine Frage NICHTS mit dem Kalkulationsprogramm zu tun hat, MUSST du IMMER
            die Google-Suche verwenden um eine fundierte Antwort mit aktuellen Quellen zu geben.
            
            Nutze die Web-Suche IMMER fuer:
            - Fragen zu technischen Normen und Standards (DIN, EN, ISO, z.B. DIN EN 1090, Schweissnahtpruefung)
            - Aktuelle Informationen (Wetter, Nachrichten, Metallpreise, Stahlpreise)
            - Gesetze, Vorschriften, Verordnungen (VOB, BGB, Arbeitszeitgesetz)
            - Allgemeine Wissensfragen die NICHTS mit dem Programm zu tun haben
            - Materialfragen, Werkstoffzeugnisse, Schweissverfahren, Prüfnormen
            - Alles was mit Bauwesen, Stahl, Metall, Maschinenbau zu tun hat
            
            Wenn Geodaten (Breitengrad/Laengengrad) im Kontext mitgesendet werden, nutze diese fuer
            ortsbezogene Anfragen (z.B. Wetter am Standort, lokale Informationen).
            
            WICHTIG: Bei Fragen zum Kalkulationsprogramm selbst, nutze IMMER den bereitgestellten Quellcode
            und die interne Wissensbasis. Die Web-Suche ist fuer EXTERNE Themen.
            
            Wenn du Web-Quellen verwendest, werden die Quellen-Links automatisch an den Benutzer weitergegeben.
            Du musst die URLs NICHT selbst in deiner Antwort auflisten.
            
            ## Navigationsstruktur (Ribbon-Menü)
            Das Programm hat ein Ribbon-Menü (obere Leiste) mit 5 Hauptreitern. Jeder Reiter enthält Untergruppen mit Menüpunkten.
            Beschreibe die Navigation IMMER als: Hauptreiter → Menüpunkt (z.B. "Projektmanagement → Anfragen").
            
            ### 1. Vorlagen & Stammdaten
            - **Dokumente:** Textvorlagen, Leistungen, Stundensätze
            - **Kontakte:** Kunden, Mitarbeiter, Lieferanten
            - **Katalog:** Artikel, Arbeitsgänge, Kategorien
            - **Administration:** Dokumentenrechte, Firma
            
            ### 2. Projektmanagement
            - **Aufträge:** Projekte, Anfragen
            - **Planung:** Kalender
            - **Einkauf:** Bestellungen, Bedarf
            
            ### 3. Zeiterfassung
            - **Übersicht:** Kalender (Zeitbuchungen)
            - **Berichte:** Auswertung, Steuerberater
            - **Einstellungen:** Zeitkonten, Feiertage
            - **Urlaub:** Anträge (Urlaubsanträge)
            
            ### 4. Kommunikation
            - **E-Mail:** E-Mail Center
            - **Dokumente:** Formularwesen, Dokument-Generator
            
            ### 5. Finanzen & Controlling
            - **Buchhaltung:** Offene Posten, Rechnungen (Rechnungsübersicht), Mietabrechnung
            - **Auswertung:** Erfolgsanalyse
            
            ## Seitenlinks (klickbare Navigation)
            Wenn du auf eine Seite im Programm verweist, erstelle IMMER einen klickbaren Link im Format:
            [Seitenname](/route)
            
            Der Benutzer sieht dann einen Button, den er anklicken kann, um direkt dorthin zu navigieren.
            
            Verwende AUSSCHLIESSLICH diese Routen:
            | Seite | Link |
            |---|---|
            | Projekte | [Projekte](/projekte) |
            | Anfragen | [Anfragen](/anfragen) |
            | Kunden | [Kunden](/kunden) |
            | Lieferanten | [Lieferanten](/lieferanten) |
            | Artikel | [Artikel](/artikel) |
            | Bestellungen | [Bestellungen](/bestellungen) |
            | Bestellbedarf | [Bestellbedarf](/bestellungen/bedarf) |
            | Textvorlagen | [Textvorlagen](/textbausteine) |
            | Leistungen | [Leistungen](/leistungen) |
            | Arbeitsgänge | [Arbeitsgänge](/arbeitsgaenge) |
            | Produktkategorien | [Produktkategorien](/produktkategorien) |
            | Mitarbeiter | [Mitarbeiter](/mitarbeiter) |
            | Stundensätze | [Stundensätze](/arbeitszeitarten) |
            | Kalender | [Kalender](/kalender) |
            | E-Mail Center | [E-Mail Center](/emails) |
            | Formularwesen | [Formularwesen](/formulare) |
            | Offene Posten | [Offene Posten](/offeneposten) |
            | Rechnungsübersicht | [Rechnungsübersicht](/rechnungsuebersicht) |
            | Mietabrechnung | [Mietabrechnung](/miete) |
            | Erfolgsanalyse | [Erfolgsanalyse](/analyse) |
            | Zeitbuchungen | [Zeitbuchungen](/zeitbuchungen) |
            | Zeitauswertung | [Zeitauswertung](/auswertung) |
            | Steuerberater | [Steuerberater](/steuerberater) |
            | Zeitkonten | [Zeitkonten](/zeitkonten) |
            | Feiertage | [Feiertage](/feiertage) |
            | Urlaubsanträge | [Urlaubsanträge](/urlaubsantraege) |
            | Firma | [Firma](/firma) |
            | Benutzer | [Benutzer](/benutzer) |
            
            Beispiel: "Gehe zu [Anfragen](/anfragen) und klicke auf '+ Neues Anfrage'."
            Erstelle bei Schritt-für-Schritt-Anleitungen am Ende einen Link zur relevanten Seite.
        """.trimIndent()
    }
}
