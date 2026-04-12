package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KiHilfeService {

    /** Frontend context sent alongside chat messages for RAG enrichment. */
    public record PageContext(String route, String pageTitle, String visibleContent, String errorMessages, Double latitude, Double longitude) {}

    /** Result of a chat call including optional web sources from Google Search grounding. */
    public record ChatResult(String reply, List<SourceLink> sources) {}
    public record SourceLink(String title, String url) {}

    private static final int MAX_TOOL_ITERATIONS = 17; // Vermeidung von Endlosschleifen bei Tool-Nutzung
    private static final int MAX_RATE_LIMIT_RETRIES = 2;
    private static final long DEFAULT_RATE_LIMIT_WAIT_MS = 30_000;

    private static final String BASE_SYSTEM_PROMPT = """
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
            
            ## Werkzeuge (Function Calling)
            Du hast Zugriff auf folgende Werkzeuge, die du bei Bedarf aufrufen kannst:
            
            1. **search_code(query)** - Durchsucht den Quellcode per Vektor-Suche. Nutze dies wenn du
               herausfinden willst, welche Buttons/Formulare auf einer Seite existieren, oder Backend-Logik finden willst.
            2. **read_file(path)** - Liest eine einzelne Datei. Nutze dies wenn du den genauen Quellcode
               einer bestimmten Komponente oder Konfigurationsdatei brauchst.
            3. **list_files(directory)** - Listet Dateien in einem Verzeichnis. Nutze dies um die Projektstruktur
               zu erkunden.
            4. **query_database(sql)** - Fuehrt ein READ-ONLY SELECT auf der Datenbank aus. Nutze dies um
               Schema-Informationen oder konkrete Daten abzufragen (z.B. Tabellenstruktur, Anzahl Datensaetze).
            5. **search_emails(query)** - Durchsucht E-Mails nach Betreff, Absender, Empfaenger oder Inhalt.
               Gibt Treffer mit Direkt-Links zum EmailCenter zurueck. Nutze dies IMMER wenn der Benutzer
               nach einer bestimmten E-Mail fragt oder E-Mails zu einem Thema sucht.
               Die Links im Format [E-Mail oeffnen](/emails/folder/id) werden als klickbare Links dargestellt.
            6. **run_python(code)** - Fuehrt ein Python-Skript aus und gibt die Ausgabe zurueck.
               Nutze dies fuer:
               - Mathematische Berechnungen (Kalkulation, Prozentrechnung, Flaechenberechnung, Materialkosten)
               - Statistische Auswertungen (Durchschnitt, Median, Standardabweichung)
               - Datenverarbeitung von query_database-Ergebnissen (Summen, Gruppierungen, Pivots)
               - Einheitenumrechnungen (mm→m, kg→t, etc.)
               - Datumsberechnungen (Arbeitstage, Fristen, Differenzen)
               Verfuegbare Module: math, statistics, datetime, decimal, fractions, json, csv, re, collections, itertools.
               Verwende print() fuer die Ausgabe. Kein Dateizugriff, kein Netzwerk.
            
            REGELN fuer Werkzeug-Nutzung:
            - Nutze search_code ZUERST, bevor du eine Behauptung ueber UI-Elemente aufstellst
            - Nutze read_file wenn du den EXAKTEN Code einer Komponente brauchst
            - Nutze query_database NUR wenn der Benutzer nach Datenbankstruktur oder Daten fragt
            - Nutze search_emails wenn der Benutzer nach E-Mails fragt oder Mails zu einem Thema sucht
            - Nutze run_python fuer Berechnungen, Auswertungen und Datenverarbeitung
            - POWER-KOMBINATION: query_database + run_python = Daten abfragen UND direkt auswerten/berechnen
            - Du kannst mehrere Werkzeuge nacheinander aufrufen um deine Antwort zu verbessern
            - Wenn du bereits relevanten Code im Kontext hast, musst du NICHT nochmal suchen
            
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
            """;

    private final ObjectMapper objectMapper;
    private final CodebaseIndexService codebaseIndexService;
    private final LocalRagService localRagService;
    private final KiToolService kiToolService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.ki-hilfe.model:gemini-3-flash-preview}")
    private String model;

    @Value("${ai.ki-hilfe.temperature:0.4}")
    private double temperature;

    @Value("${ai.ki-hilfe.max-output-tokens:8192}")
    private int maxOutputTokens;

    @Value("${ai.ki-hilfe.web-search-enabled:true}")
    private boolean webSearchEnabled;

    public record ChatMessage(String role, String text) {}

    /**
     * Build system prompt with RAG context (if available) or full codebase fallback.
     */
    private String buildSystemPrompt(String ragContext) {
        if (ragContext != null && !ragContext.isBlank()) {
            return BASE_SYSTEM_PROMPT +
                    "\n\n## Relevanter Quellcode (per Vektor-Suche gefunden)\n" +
                    "Die folgenden Code-Abschnitte wurden automatisch als relevant fuer die aktuelle Frage identifiziert. " +
                    "Abschnitte mit '>>> AKTUELLE SEITE DES BENUTZERS <<<' zeigen den EXAKTEN Quellcode der Seite, " +
                    "auf der sich der Benutzer GERADE befindet. Diese Abschnitte haben HOECHSTE Prioritaet: " +
                    "Nur Buttons, Tabs, Formulare und Funktionen die IN DIESEM CODE vorkommen, existieren auf dieser Seite. " +
                    "Beschreibe NUR das, was du im Code der aktuellen Seite tatsaechlich siehst.\n\n" +
                    ragContext;
        }

        // Fallback: full codebase index
        String index = codebaseIndexService.getIndex();
        if (index == null || index.isEmpty()) {
            return BASE_SYSTEM_PROMPT;
        }
        return BASE_SYSTEM_PROMPT +
                "\n\n## Frontend-Quellcode & Dokumentation (Read-Only Wissensbasis)\n" +
                "Im Folgenden findest du den KOMPLETTEN Frontend-Quellcode des Kalkulationsprogramms: " +
                "Alle React-Seiten (Pages), UI-Komponenten, Navigation (App.tsx Routing), " +
                "Hooks, Hilfsfunktionen und die Projekt-Dokumentation. " +
                "Nutze diesen Code, um Benutzern zu erklären, wie sie sich im Programm " +
                "zurechtfinden, welche Funktionen auf welcher Seite verfügbar sind, " +
                "und wie Workflows Schritt für Schritt ablaufen.\n\n" +
                index;
    }

    /**
     * Chat without page context (backward-compatible).
     */
    public ChatResult chat(List<ChatMessage> messages) throws IOException, InterruptedException {
        return chat(messages, null);
    }

    /**
     * Chat with optional page context for RAG-enhanced responses.
     */
    public ChatResult chat(List<ChatMessage> messages, PageContext pageContext) throws IOException, InterruptedException {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new IOException("Gemini API Key fehlt (ai.gemini.api-key)");
        }

        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Keine Nachrichten angegeben");
        }

        // Limit conversation history to prevent abuse
        List<ChatMessage> limitedMessages = messages.size() > 20
                ? messages.subList(messages.size() - 20, messages.size())
                : messages;

        // Try RAG: embed the latest user message and search for relevant code
        String ragContext = null;
        boolean ragUsed = false;
        if (localRagService.isAvailable()) {
            log.info("  -> Lokales RAG ist verfuegbar, starte Vektor-Suche...");
            try {
                String latestUserMessage = limitedMessages.stream()
                        .filter(m -> "user".equals(m.role()))
                        .reduce((first, second) -> second)
                        .map(ChatMessage::text)
                        .orElse("");

                String contextHint = pageContext != null
                        ? "Seite: %s (%s)".formatted(
                                pageContext.pageTitle() != null ? pageContext.pageTitle() : "",
                                pageContext.route() != null ? pageContext.route() : "")
                        : null;

                String currentRoute = pageContext != null ? pageContext.route() : null;

                var results = localRagService.search(latestUserMessage, contextHint, currentRoute);
                if (!results.isEmpty()) {
                    ragContext = localRagService.buildContextFromResults(results);
                    ragUsed = true;
                    log.info("  [OK] RAG: {} relevante Code-Chunks gefunden ({} Zeichen Kontext)",
                            results.size(), ragContext.length());
                    for (var r : results) {
                        log.info("    - [{}] {} -- {} ({})",
                                String.format("%.2f", r.score()), r.filePath(), r.name(), r.chunkType());
                    }
                } else {
                    log.info("  [X] RAG: Keine passenden Chunks gefunden, Fallback auf Full-Index");
                }
            } catch (Exception e) {
                log.warn("  [X] RAG-Suche fehlgeschlagen, Fallback auf Full-Index: {}", e.getMessage());
            }
        } else {
            log.info("  -> RAG nicht verfuegbar (enabled={}, ready={}), nutze Full-Codebase-Index",
                    localRagService.isEnabled(), localRagService.isReady());
        }

        // Build page context string for the user message
        String pageContextInfo = buildPageContextInfo(pageContext);
        if (pageContextInfo != null) {
            log.info("  -> Seitenkontext ({} Zeichen) wird an Nachricht angehaengt", pageContextInfo.length());
        }

        String promptMode = ragUsed ? "RAG (Vektor-Suche)" : "Full-Codebase-Index";
        log.info("  -> Prompt-Modus: {}", promptMode);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"
                .formatted(model, geminiApiKey);

        ObjectNode requestBody = objectMapper.createObjectNode();

        // System Prompt with RAG context or full codebase fallback
        ObjectNode systemInstruction = objectMapper.createObjectNode();
        ArrayNode sysParts = objectMapper.createArrayNode();
        sysParts.add(objectMapper.createObjectNode().put("text", buildSystemPrompt(ragContext)));
        systemInstruction.set("parts", sysParts);
        requestBody.set("systemInstruction", systemInstruction);

        // Conversation history (inject page context into the latest user message)
        ArrayNode contents = objectMapper.createArrayNode();
        for (int i = 0; i < limitedMessages.size(); i++) {
            ChatMessage msg = limitedMessages.get(i);
            String role = "user".equals(msg.role()) ? "user" : "model";
            ObjectNode msgNode = objectMapper.createObjectNode().put("role", role);
            ArrayNode parts = objectMapper.createArrayNode();

            // Append page context to the last user message
            String text = msg.text();
            boolean isLastUserMessage = "user".equals(msg.role()) && i == limitedMessages.size() - 1;
            if (isLastUserMessage && pageContextInfo != null && !pageContextInfo.isBlank()) {
                text = text + "\n\n" + pageContextInfo;
            }

            parts.add(objectMapper.createObjectNode().put("text", text));
            msgNode.set("parts", parts);
            contents.add(msgNode);
        }
        requestBody.set("contents", contents);

        // Generation config
        ObjectNode config = objectMapper.createObjectNode();
        config.put("temperature", temperature);
        config.put("maxOutputTokens", maxOutputTokens);
        requestBody.set("generationConfig", config);

        // Tools: Google Search grounding + agentic function calling tools
        ArrayNode tools = objectMapper.createArrayNode();
        if (webSearchEnabled) {
            ObjectNode searchTool = objectMapper.createObjectNode();
            searchTool.set("googleSearch", objectMapper.createObjectNode());
            tools.add(searchTool);
        }
        // Add function calling tools (search_code, read_file, list_files, query_database)
        ObjectNode functionTool = objectMapper.createObjectNode();
        functionTool.set("functionDeclarations", kiToolService.buildFunctionDeclarations());
        tools.add(functionTool);
        requestBody.set("tools", tools);

        // Required when combining built-in tools (googleSearch) with function calling
        if (webSearchEnabled) {
            ObjectNode toolConfig = objectMapper.createObjectNode();
            toolConfig.put("includeServerSideToolInvocations", true);
            requestBody.set("toolConfig", toolConfig);
        }

        // Agentic loop: call Gemini, execute tool calls, feed results back
        List<SourceLink> allSources = new ArrayList<>();

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            String body = objectMapper.writeValueAsString(requestBody);

            HttpResponse<String> response = null;
            for (int retry = 0; retry <= MAX_RATE_LIMIT_RETRIES; retry++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() != 429 || retry == MAX_RATE_LIMIT_RETRIES) {
                    break;
                }

                long waitMs = parseRetryDelay(response.body());
                log.warn("  -> Rate-Limit (429) bei Iteration {}, warte {} ms vor Retry {}/{}",
                        iteration + 1, waitMs, retry + 1, MAX_RATE_LIMIT_RETRIES);
                Thread.sleep(waitMs);
            }

            if (response.statusCode() != 200) {
                log.error("Gemini KI-Hilfe API Error {}: {}", response.statusCode(), response.body());
                throw new IOException("KI-Hilfe nicht verfügbar (Fehler " + response.statusCode() + ")");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new IOException("Keine Antwort von der KI erhalten");
            }

            JsonNode candidate = candidates.get(0);

            // Collect grounding sources from this iteration
            allSources.addAll(extractGroundingSources(candidate));

            // Log grounding metadata
            JsonNode gm = candidate.path("groundingMetadata");
            if (!gm.isMissingNode()) {
                JsonNode queries = gm.path("webSearchQueries");
                if (queries.isArray() && !queries.isEmpty()) {
                    log.info("  -> Google Search Queries: {}", queries);
                }
            }

            JsonNode partsNode = candidate.path("content").path("parts");
            if (!partsNode.isArray() || partsNode.isEmpty()) {
                throw new IOException("Keine Antwort von der KI erhalten");
            }

            // Check if any part contains a function call
            List<JsonNode> functionCalls = new ArrayList<>();
            StringBuilder textReply = new StringBuilder();
            for (JsonNode part : partsNode) {
                if (part.has("functionCall")) {
                    functionCalls.add(part.get("functionCall"));
                }
                String partText = part.path("text").asText("");
                if (!partText.isEmpty()) {
                    if (!textReply.isEmpty()) textReply.append("\n");
                    textReply.append(partText);
                }
            }

            // No function calls → return final text response
            if (functionCalls.isEmpty()) {
                if (textReply.isEmpty()) {
                    throw new IOException("Keine Textantwort von der KI erhalten");
                }

                if (!allSources.isEmpty()) {
                    log.info("  -> {} Web-Quellen aus Google Search Grounding extrahiert", allSources.size());
                    allSources.forEach(s -> log.info("    - [{}] {}", s.title(), s.url()));
                }
                log.info("  -> Agentic loop beendet nach {} Iteration(en)", iteration + 1);
                return new ChatResult(textReply.toString(), allSources);
            }

            // Execute function calls and build response
            log.info("  -> Iteration {}: {} Function Call(s) empfangen", iteration + 1, functionCalls.size());

            // Append the model's response (with function calls) to contents
            ObjectNode modelMsg = objectMapper.createObjectNode().put("role", "model");
            modelMsg.set("parts", partsNode.deepCopy());
            contents.add(modelMsg);

            // Build function response parts
            ObjectNode functionResponseMsg = objectMapper.createObjectNode().put("role", "user");
            ArrayNode responseParts = objectMapper.createArrayNode();

            for (JsonNode fc : functionCalls) {
                String toolName = fc.path("name").asText();
                JsonNode args = fc.path("args");
                log.info("    -> Fuehre Tool '{}' aus mit Args: {}", toolName, args);

                String result = kiToolService.executeTool(toolName, args);

                // Truncate very large results
                if (result.length() > 15_000) {
                    result = result.substring(0, 15_000) + "\n... (abgeschnitten)";
                }
                log.info("    <- Tool '{}' Ergebnis: {} Zeichen", toolName, result.length());

                ObjectNode frPart = objectMapper.createObjectNode();
                ObjectNode functionResponse = objectMapper.createObjectNode();
                functionResponse.put("name", toolName);
                ObjectNode responseContent = objectMapper.createObjectNode();
                responseContent.put("result", result);
                functionResponse.set("response", responseContent);
                frPart.set("functionResponse", functionResponse);
                responseParts.add(frPart);
            }

            functionResponseMsg.set("parts", responseParts);
            contents.add(functionResponseMsg);

            // Update requestBody contents for next iteration
            requestBody.set("contents", contents);
        }

        // Max iterations reached - one final call without tools to force a text response
        log.warn("  -> Max Tool-Iterationen ({}) erreicht, erzwinge Text-Antwort", MAX_TOOL_ITERATIONS);
        requestBody.remove("tools");
        requestBody.remove("toolConfig");

        // Add explicit instruction to summarize findings as text
        ObjectNode forceTextMsg = objectMapper.createObjectNode().put("role", "user");
        ArrayNode forceTextParts = objectMapper.createArrayNode();
        forceTextParts.add(objectMapper.createObjectNode().put("text",
                "Bitte fasse jetzt deine bisherigen Erkenntnisse zusammen und antworte dem Benutzer in normalem Text. Rufe KEINE weiteren Tools auf."));
        forceTextMsg.set("parts", forceTextParts);
        contents.add(forceTextMsg);
        requestBody.set("contents", contents);

        String body = objectMapper.writeValueAsString(requestBody);
        HttpRequest finalRequest = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> finalResponse = httpClient.send(finalRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (finalResponse.statusCode() != 200) {
            throw new IOException("KI-Hilfe nicht verfügbar (Fehler " + finalResponse.statusCode() + ")");
        }
        JsonNode finalRoot = objectMapper.readTree(finalResponse.body());
        JsonNode finalParts = finalRoot.path("candidates").path(0).path("content").path("parts");
        StringBuilder finalText = new StringBuilder();
        if (finalParts.isArray()) {
            for (JsonNode part : finalParts) {
                String t = part.path("text").asText("");
                if (!t.isEmpty()) {
                    if (!finalText.isEmpty()) finalText.append("\n");
                    finalText.append(t);
                }
            }
        }
        if (finalText.isEmpty()) {
            throw new IOException("Keine Antwort von der KI erhalten");
        }
        allSources.addAll(extractGroundingSources(finalRoot.path("candidates").path(0)));
        return new ChatResult(finalText.toString(), allSources);
    }

    /**
     * Formats page context information from the frontend into a string
     * appended to the user message so the LLM knows what screen the user is on.
     */
    private String buildPageContextInfo(PageContext ctx) {
        if (ctx == null) return null;

        StringBuilder sb = new StringBuilder("[Aktueller Bildschirmkontext]\n");
        if (ctx.route() != null && !ctx.route().isBlank()) {
            sb.append("Seite: ").append(ctx.route()).append("\n");
        }
        if (ctx.pageTitle() != null && !ctx.pageTitle().isBlank()) {
            sb.append("Titel: ").append(ctx.pageTitle()).append("\n");
        }
        if (ctx.errorMessages() != null && !ctx.errorMessages().isBlank()) {
            sb.append("Fehlermeldungen: ").append(ctx.errorMessages()).append("\n");
        }
        if (ctx.visibleContent() != null && !ctx.visibleContent().isBlank()) {
            // Limit visible content to prevent abuse
            String content = ctx.visibleContent();
            if (content.length() > 2000) {
                content = content.substring(0, 2000) + "…";
            }
            sb.append("Sichtbare Daten:\n").append(content).append("\n");
        }
        if (ctx.latitude() != null && ctx.longitude() != null) {
            sb.append("Standort (Geodaten): Breitengrad ").append(ctx.latitude())
              .append(", Laengengrad ").append(ctx.longitude()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Extracts source links from Gemini Google Search grounding metadata.
     */
    private List<SourceLink> extractGroundingSources(JsonNode candidate) {
        JsonNode groundingMetadata = candidate.path("groundingMetadata");
        if (groundingMetadata.isMissingNode()) {
            return Collections.emptyList();
        }

        JsonNode chunks = groundingMetadata.path("groundingChunks");
        if (!chunks.isArray() || chunks.isEmpty()) {
            return Collections.emptyList();
        }

        List<SourceLink> sources = new ArrayList<>();
        for (JsonNode chunk : chunks) {
            JsonNode web = chunk.path("web");
            String uri = web.path("uri").asText("");
            String title = web.path("title").asText("");
            if (!uri.isEmpty()) {
                sources.add(new SourceLink(title.isEmpty() ? uri : title, uri));
            }
        }
        return sources;
    }

    /**
     * Parses the retryDelay from a Gemini 429 response body.
     * Expects format like "28s" or "15.784s" in the RetryInfo detail.
     */
    private long parseRetryDelay(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode details = root.path("error").path("details");
            if (details.isArray()) {
                for (JsonNode detail : details) {
                    if ("type.googleapis.com/google.rpc.RetryInfo".equals(detail.path("@type").asText())) {
                        String delay = detail.path("retryDelay").asText("");
                        if (delay.endsWith("s")) {
                            double seconds = Double.parseDouble(delay.substring(0, delay.length() - 1));
                            return Math.max(1000, (long) (seconds * 1000) + 1000); // add 1s buffer
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Konnte retryDelay nicht parsen: {}", e.getMessage());
        }
        return DEFAULT_RATE_LIMIT_WAIT_MS;
    }
}
