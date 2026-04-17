package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.service.ArtikelMatchingToolService.MatchingToolContext;
import org.example.kalkulationsprogramm.service.ArtikelMatchingToolService.ToolSideEffect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestriert den KI-Matching-Agent: bekommt eine extrahierte Artikel-Position
 * aus einer Lieferantenrechnung und entscheidet autonom via Gemini-Function-Calling,
 * ob ein bestehender Artikel aktualisiert oder ein neuer Vorschlag angelegt wird.
 *
 * <p>Eigener, schlanker Gemini-Call-Loop statt Overload auf {@link KiHilfeService}:
 * der Matching-Agent hat einen völlig anderen System-Prompt, keine RAG-/Google-
 * Search-Integration und eine geschlossene Tool-Liste. Separate Loops halten
 * beide Flows unabhängig und einfach zu warten.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArtikelMatchingAgentService {

    private static final int MAX_TOOL_ITERATIONS = 8;

    /** Ergebnis eines Matching-Versuchs für eine einzelne Rechnungs-Position. */
    public enum Ergebnis { PREIS_AKTUALISIERT, VORSCHLAG_ANGELEGT, SKIPPED, FEHLER }

    public record MatchingResult(Ergebnis ergebnis, String hinweis) {}

    private static final String SYSTEM_PROMPT = """
            Du bist ein Materialstamm-Matching-Agent für einen Bauschlosserei-Stahlhandel.
            Deine einzige Aufgabe: Für eine gegebene Rechnungs-Position entscheiden, ob sie
            einem bestehenden Artikel im Materialstamm entspricht oder neu angelegt werden muss.

            ARBEITSWEISE:
            1. Rufe list_werkstoffe und list_kategorien auf, um das Vokabular zu kennen.
            2. Bestimme aus der Rechnungs-Position den passenden Werkstoff (z.B. 'S235JR') und die
               Kategorie (z.B. 'Flachstahl', 'Rundrohr').
            3. Rufe search_artikel mit werkstoffId + kategorieId + aussagekräftigem Suchtext (Abmessungen!)
               auf. Prüfe die Treffer gründlich mit get_artikel_details.
            4a. Wenn du einen eindeutigen Treffer hast (Werkstoff und Abmessungen identisch):
                update_artikel_preis mit hoher Konfidenz (>= 0.85). Das System lernt dabei die
                externe Artikelnummer dauerhaft.
            4b. Wenn du kein klares Match findest und die Position ein echter Material-Artikel ist:
                propose_new_artikel mit allen bekannten Feldern.
            4c. Wenn die Position KEIN Material-Artikel ist, sondern eine DIENSTLEISTUNG oder NEBENKOSTEN
                (Zuschnitt, Brennschnitt, Sägen, Verzinken, Feuerverzinken, Pulverbeschichten, Lackieren,
                Strahlen, Beschriften, Transport/Fracht, Verpackung, Palette, Mindermenge,
                Pauschale, Gebühr, Rabatt, Skonto):
                Tu NICHTS — antworte mit 'SKIPPED: <Grund>'. Solche Posten landen NIEMALS im Materialstamm.

            REGELN:
            - Rufe NIEMALS update_artikel_preis mit Konfidenz < 0.85 auf — lieber propose_new_artikel
              oder nichts tun.
            - Abmessungen (z.B. 30x5, ⌀25) MÜSSEN exakt übereinstimmen, sonst KEIN Match.
            - Werkstoff MUSS übereinstimmen ziemlich genau, sonst KEIN Match. Manchmal steht bei Stahl S235JRH, manchmal S235 — das ist akzeptabel. Aber S355 ist ein anderer Werkstoff, also kein Match zu S235.
            - Maximal 6 Tool-Aufrufe pro Position. Sei effizient.
            - Antworte am Ende mit EINER Textzeile:
              'UPDATED: <kurze Begründung>' oder 'PROPOSED: <kurze Begründung>' oder 'SKIPPED: <Grund>'.
            """;

    private final ObjectMapper objectMapper;
    private final ArtikelMatchingToolService matchingToolService;
    private final ArtikelMatchingService artikelMatchingService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Value("${ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${ai.materialstamm.auto-matching.model:gemini-flash-latest}")
    private String model;

    @Value("${ai.materialstamm.auto-matching.konfidenz-schwelle:0.85}")
    private double konfidenzSchwelle;

    @Value("${ai.materialstamm.auto-matching.temperature:0.2}")
    private double temperature;

    /**
     * Hauptfunktion: matche oder schlage an für eine einzelne Rechnungs-Position.
     * Idempotent im Sinne "keine doppelten Vorschläge" soweit als sinnvoll: bestehender
     * Preis wird überschrieben (ist beabsichtigt), Konflikte werden erkannt.
     */
    public MatchingResult matcheOderSchlageAn(JsonNode position, Lieferanten lieferant, LieferantDokument quelle) {
        String externeNr = textOrEmpty(position, "externeArtikelnummer");
        String bez = textOrEmpty(position, "bezeichnung");
        log.info("[Agent] Start: externeNr='{}' bezeichnung='{}' lieferant={} quelleDok={}",
                externeNr, bez,
                lieferant != null ? lieferant.getId() + "/" + lieferant.getLieferantenname() : "NULL",
                quelle != null ? quelle.getId() : "NULL");

        if (lieferant == null) {
            log.warn("[Agent] SKIP: Kein Lieferant");
            return new MatchingResult(Ergebnis.SKIPPED, "Kein Lieferant");
        }
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.warn("[Agent] FEHLER: Gemini API Key fehlt — Matching-Agent deaktiviert");
            return new MatchingResult(Ergebnis.FEHLER, "Gemini API Key fehlt");
        }

        log.info("[Agent] Modell={} konfidenzSchwelle={} temperature={}", model, konfidenzSchwelle, temperature);

        MatchingToolContext ctx = new MatchingToolContext(
                lieferant, quelle, BigDecimal.valueOf(konfidenzSchwelle));

        String userMessage = baueBenutzerNachricht(position, lieferant);
        log.debug("[Agent] User-Message:\n{}", userMessage);

        try {
            long startMs = System.currentTimeMillis();
            String agentAntwort = fuehreAgentLoopAus(userMessage, ctx);
            long elapsed = System.currentTimeMillis() - startMs;
            log.info("[Agent] Loop beendet nach {}ms, Antwort (gekürzt): {}", elapsed, kurz(agentAntwort));
            MatchingResult res = auswerten(ctx, agentAntwort);
            log.info("[Agent] Endergebnis für '{}': {} — {}", externeNr, res.ergebnis(), res.hinweis());
            return res;
        } catch (Exception e) {
            log.error("[Agent] Fehler bei '{}': {}", externeNr, e.getMessage(), e);
            return new MatchingResult(Ergebnis.FEHLER, "Agent-Fehler: " + e.getMessage());
        }
    }

    private String baueBenutzerNachricht(JsonNode position, Lieferanten lieferant) {
        StringBuilder sb = new StringBuilder();
        sb.append("Neue Rechnungs-Position vom Lieferant '")
          .append(lieferant.getLieferantenname() != null ? lieferant.getLieferantenname() : "?")
          .append("' (Lieferant-ID wird intern im Kontext gesetzt):\n\n");

        sb.append("Externe Artikelnummer: ").append(textOrEmpty(position, "externeArtikelnummer")).append("\n");
        sb.append("Bezeichnung:           ").append(textOrEmpty(position, "bezeichnung")).append("\n");
        sb.append("Menge:                 ").append(textOrEmpty(position, "menge")).append("\n");
        sb.append("Mengeneinheit:         ").append(textOrEmpty(position, "mengeneinheit")).append("\n");
        sb.append("Einzelpreis:           ").append(textOrEmpty(position, "einzelpreis")).append("\n");
        sb.append("Preiseinheit:          ").append(textOrEmpty(position, "preiseinheit")).append("\n");
        if (position.has("positionsTyp")) {
            sb.append("positionsTyp:          ").append(position.get("positionsTyp").asText("")).append("\n");
        }

        // Jaro-Winkler-Hints als Startpunkt für den Agenten
        String bezeichnung = textOrEmpty(position, "bezeichnung");
        if (!bezeichnung.isBlank()) {
            try {
                List<Artikel> kandidaten = artikelMatchingService.findeBesteTreffer(bezeichnung, null);
                if (!kandidaten.isEmpty()) {
                    sb.append("\nTop-5 Jaro-Winkler-Kandidaten (Hinweis, nicht zwingend Match!):\n");
                    for (Artikel a : kandidaten) {
                        sb.append("  - id=").append(a.getId())
                          .append(" name='").append(nullSafe(a.getProduktname())).append("'")
                          .append(" linie='").append(nullSafe(a.getProduktlinie())).append("'");
                        if (a.getWerkstoff() != null) sb.append(" werkstoff='").append(nullSafe(a.getWerkstoff().getName())).append("'");
                        sb.append("\n");
                    }
                }
            } catch (Exception e) {
                log.debug("Jaro-Winkler Hints fehlgeschlagen: {}", e.getMessage());
            }
        }

        sb.append("\nEntscheide jetzt. Befolge die ARBEITSWEISE streng.");
        return sb.toString();
    }

    private String fuehreAgentLoopAus(String userMessage, MatchingToolContext ctx) throws IOException, InterruptedException {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"
                .formatted(model, geminiApiKey);

        ObjectNode requestBody = objectMapper.createObjectNode();

        ObjectNode systemInstruction = objectMapper.createObjectNode();
        ArrayNode sysParts = objectMapper.createArrayNode();
        sysParts.add(objectMapper.createObjectNode().put("text", SYSTEM_PROMPT));
        systemInstruction.set("parts", sysParts);
        requestBody.set("systemInstruction", systemInstruction);

        ArrayNode contents = objectMapper.createArrayNode();
        ObjectNode userMsg = objectMapper.createObjectNode().put("role", "user");
        ArrayNode userParts = objectMapper.createArrayNode();
        userParts.add(objectMapper.createObjectNode().put("text", userMessage));
        userMsg.set("parts", userParts);
        contents.add(userMsg);
        requestBody.set("contents", contents);

        ObjectNode config = objectMapper.createObjectNode();
        config.put("temperature", temperature);
        config.put("maxOutputTokens", 2048);
        requestBody.set("generationConfig", config);

        ArrayNode tools = objectMapper.createArrayNode();
        ObjectNode functionTool = objectMapper.createObjectNode();
        functionTool.set("functionDeclarations", matchingToolService.buildFunctionDeclarations());
        tools.add(functionTool);
        requestBody.set("tools", tools);

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                log.error("Matching-Agent Gemini Error {}: {}", response.statusCode(), response.body());
                throw new IOException("Gemini API Error " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode candidate = root.path("candidates").path(0);
            JsonNode partsNode = candidate.path("content").path("parts");

            if (!partsNode.isArray() || partsNode.isEmpty()) {
                throw new IOException("Leere Antwort von Gemini");
            }

            List<JsonNode> functionCalls = new ArrayList<>();
            StringBuilder textReply = new StringBuilder();
            for (JsonNode part : partsNode) {
                if (part.has("functionCall")) functionCalls.add(part.get("functionCall"));
                String t = part.path("text").asText("");
                if (!t.isEmpty()) {
                    if (!textReply.isEmpty()) textReply.append("\n");
                    textReply.append(t);
                }
            }

            if (functionCalls.isEmpty()) {
                return textReply.toString();
            }

            ObjectNode modelMsg = objectMapper.createObjectNode().put("role", "model");
            modelMsg.set("parts", partsNode.deepCopy());
            contents.add(modelMsg);

            ObjectNode fnRespMsg = objectMapper.createObjectNode().put("role", "user");
            ArrayNode respParts = objectMapper.createArrayNode();
            for (JsonNode fc : functionCalls) {
                String toolName = fc.path("name").asText();
                JsonNode args = fc.path("args");
                log.info("Matching-Agent ruft Tool '{}' mit args: {}", toolName, args);

                String result = matchingToolService.executeTool(toolName, args, ctx);
                if (result.length() > 8000) {
                    result = result.substring(0, 8000) + "\n... (abgeschnitten)";
                }

                ObjectNode frPart = objectMapper.createObjectNode();
                ObjectNode functionResponse = objectMapper.createObjectNode();
                functionResponse.put("name", toolName);
                ObjectNode responseContent = objectMapper.createObjectNode();
                responseContent.put("result", result);
                functionResponse.set("response", responseContent);
                frPart.set("functionResponse", functionResponse);
                respParts.add(frPart);
            }
            fnRespMsg.set("parts", respParts);
            contents.add(fnRespMsg);

            requestBody.set("contents", contents);
        }

        log.warn("Matching-Agent hat max Iterationen ({}) erreicht", MAX_TOOL_ITERATIONS);
        return "SKIPPED: Max-Iterationen erreicht";
    }

    private MatchingResult auswerten(MatchingToolContext ctx, String agentAntwort) {
        ToolSideEffect effect = ctx.lastEffect();
        if (effect instanceof ToolSideEffect.PreisAktualisiert p) {
            return new MatchingResult(Ergebnis.PREIS_AKTUALISIERT,
                    "Artikel " + p.artikelId() + " aktualisiert, externeNummerGelernt=" + p.externeNummerGelernt()
                            + ", Agent: " + kurz(agentAntwort));
        }
        if (effect instanceof ToolSideEffect.VorschlagAngelegt v) {
            return new MatchingResult(Ergebnis.VORSCHLAG_ANGELEGT,
                    "Vorschlag " + v.vorschlagId() + " (" + v.typ() + "), Agent: " + kurz(agentAntwort));
        }
        return new MatchingResult(Ergebnis.SKIPPED, "Agent: " + kurz(agentAntwort));
    }

    // ─────────────────────────────────────────────────────────────

    private static String textOrEmpty(JsonNode n, String field) {
        return n != null && n.has(field) && !n.get(field).isNull() ? n.get(field).asText("") : "";
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String kurz(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }
}
