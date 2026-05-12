package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Beleg;
import org.example.kalkulationsprogramm.domain.Kostenstelle;
import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * KI-Agent fuer Beleg-Kostenkontierung — laeuft in einer echten do-while-Schleife
 * mit Gemini Function-Calling als Werkzeug-Schnittstelle.
 *
 * Ablauf jedes Durchgangs:
 *   1. Agent bekommt den aktuellen Konversations-Stand (System-Prompt + Beleg-
 *      Daten + bisherige Tool-Aufrufe und -Antworten).
 *   2. Agent entscheidet selbst: welches Tool aufrufen?
 *        - liste_kostenstellen  : holt aktive Kostenstellen aus der DB
 *        - liste_sachkonten     : holt aktive Sachkonten aus der DB
 *        - aehnliche_belege     : holt vergangene Belege desselben Lieferanten
 *                                 inkl. ihrer fruheren Kostenstellen-Zuordnung
 *        - finale_zuordnung     : Ergebnis, beendet die Schleife
 *   3. Tool wird ausgefuehrt, Ergebnis als functionResponse in den Verlauf
 *      gehaengt, naechste Iteration startet.
 *
 * Sicherheitsnetz: maximal {@value #MAX_ITERATIONS} Durchlaeufe — falls die KI
 * sich verheddert oder kein finale_zuordnung-Tool waehlt, brechen wir ab und
 * lassen den Buchhalter manuell entscheiden.
 *
 * Bei Confidence >= AUTO_APPLY_THRESHOLD wird die echte Kostenstelle gesetzt,
 * sodass der Beleg automatisch in den Gemeinkosten-Topf des Verrechnungslohn-
 * Rechners einfliesst.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BelegKiKostenkontoService {

    /**
     * Strenger Schwellwert: nur bei nahezu sicheren Treffern (Tankquittung,
     * Telefonrechnung, Internet, Strom) wird die Kostenstelle automatisch
     * zugewiesen. Alles darunter bleibt reiner Vorschlag — der Buchhalter
     * entscheidet manuell. Material- und Werkstoff-Rechnungen, die einem
     * Projekt zuzuordnen sind, fallen so automatisch wieder in den
     * Bestellungs-Workflow ("Einkauf > Bestellungen > Abgeschlossen") und
     * werden nicht faelschlich in den Gemeinkosten-Topf gebucht.
     */
    private static final BigDecimal AUTO_APPLY_THRESHOLD = new BigDecimal("0.95");
    private static final int MAX_ITERATIONS = 6;
    private static final int AEHNLICHE_BELEGE_LIMIT = 8;

    /**
     * Max. Anzahl Versuche pro Gemini-Call bei transienten Fehlern (HTTP 429
     * Rate-Limit, HTTP 5xx Service-Storungen). Beim ersten Schluckauf von
     * Google reicht meist ein einziger Retry, bei laenger andauernden
     * Outages liefert der dritte Versuch ebenfalls keinen Erfolg mehr —
     * dann uebernimmt der Buchhalter manuell.
     */
    static final int MAX_GEMINI_ATTEMPTS = 3;
    /**
     * Backoff vor Versuch 2 und 3 (Versuch 1 hat keinen Wait). Exponentiell:
     * 1s, 3s. Halt kurz genug, dass der async-Worker nicht zu lange blockt.
     */
    private static final long[] RETRY_BACKOFF_MS = { 1_000L, 3_000L };

    private final KostenstelleRepository kostenstelleRepository;
    private final SachkontoRepository sachkontoRepository;
    private final BelegRepository belegRepository;
    private final SystemSettingsService systemSettingsService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Value("${ai.gemini.model.dokument-analyse:gemini-3-flash-preview}")
    private String geminiModel;

    /**
     * Klassifiziert den Beleg via KI-Agent und schreibt die Vorschlaege direkt
     * an das Beleg-Objekt (Aufrufer ist transaktional und persistiert).
     */
    public void klassifiziereBeleg(Beleg beleg) {
        if (beleg == null) {
            return;
        }
        if (beleg.getKostenstelle() != null) {
            log.debug("Beleg {} hat schon Kostenstelle {} — Agent uebersprungen",
                    beleg.getId(), beleg.getKostenstelle().getId());
            return;
        }

        String apiKey = systemSettingsService.getGeminiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Kein Gemini-API-Key konfiguriert — KI-Agent uebersprungen fuer Beleg {}", beleg.getId());
            return;
        }

        // Konversations-Verlauf: wird mit jedem Tool-Cycle erweitert.
        ArrayNode contents = objectMapper.createArrayNode();
        contents.add(userTurn(buildInitialPrompt(beleg)));

        AgentErgebnis ergebnis = null;
        int iteration = 0;
        do {
            iteration++;
            log.debug("KI-Agent Beleg {} — Iteration {}", beleg.getId(), iteration);
            JsonNode antwort = callGemini(apiKey, contents);
            if (antwort == null) {
                log.warn("KI-Agent Beleg {}: leere Antwort in Iteration {}", beleg.getId(), iteration);
                break;
            }

            JsonNode functionCall = findeFunctionCall(antwort);
            if (functionCall == null) {
                // Modell antwortet ohne Tool-Aufruf → versuche, das Ergebnis aus dem
                // freien Text zu parsen (Fallback fuer Modelle, die das End-Tool
                // ignorieren) und beende die Schleife.
                String text = findeText(antwort);
                ergebnis = parseFreienText(text);
                break;
            }

            // Modell-Turn aufzeichnen, damit der nachfolgende functionResponse
            // im Konversations-Strom korrekt referenziert wird.
            contents.add(modelTurn(antwort));

            String toolName = functionCall.path("name").asText("");
            JsonNode args = functionCall.path("args");

            if ("finale_zuordnung".equals(toolName)) {
                ergebnis = parseFinaleZuordnung(args);
                break;
            }

            JsonNode toolResult = dispatch(toolName, args, beleg);
            if (toolResult != null && toolResult.has("fehler")) {
                // Modell halluziniert einen Tool-Namen, den wir nicht bedienen
                // koennen. Loop sofort verlassen, statt 6 Iterationen lang
                // dieselbe Tool-Antwort zu wiederholen.
                log.info("KI-Agent Beleg {}: unbekanntes Tool '{}' — Loop abgebrochen",
                        beleg.getId(), toolName);
                break;
            }
            contents.add(toolResponseTurn(toolName, toolResult));

        } while (iteration < MAX_ITERATIONS);

        if (ergebnis == null) {
            log.info("KI-Agent Beleg {}: keine finale_zuordnung nach {} Iterationen", beleg.getId(), iteration);
            return;
        }
        wendeErgebnisAn(beleg, ergebnis);
    }

    // ---------------------------------------------------------------------
    // Tool-Dispatch
    // ---------------------------------------------------------------------

    private JsonNode dispatch(String name, JsonNode args, Beleg beleg) {
        return switch (name) {
            case "liste_kostenstellen" -> toolListeKostenstellen();
            case "liste_sachkonten" -> toolListeSachkonten();
            case "aehnliche_belege" -> toolAehnlicheBelege(beleg);
            default -> {
                ObjectNode err = objectMapper.createObjectNode();
                err.put("fehler", "Unbekanntes Tool: " + name);
                yield err;
            }
        };
    }

    private JsonNode toolListeKostenstellen() {
        ArrayNode arr = objectMapper.createArrayNode();
        for (Kostenstelle k : kostenstelleRepository.findByAktivTrueOrderBySortierungAsc()) {
            ObjectNode o = arr.addObject();
            o.put("id", k.getId());
            o.put("bezeichnung", safe(k.getBezeichnung()));
            o.put("typ", k.getTyp() != null ? k.getTyp().name() : null);
            o.put("istFixkosten", k.isIstFixkosten());
            o.put("istInvestition", k.isIstInvestition());
            if (k.getBeschreibung() != null) {
                o.put("beschreibung", safe(k.getBeschreibung()));
            }
        }
        return arr;
    }

    private JsonNode toolListeSachkonten() {
        ArrayNode arr = objectMapper.createArrayNode();
        for (Sachkonto s : sachkontoRepository.findByAktivTrueOrderBySortierungAscBezeichnungAsc()) {
            ObjectNode o = arr.addObject();
            o.put("id", s.getId());
            if (s.getNummer() != null) {
                o.put("nummer", safe(s.getNummer()));
            }
            o.put("bezeichnung", safe(s.getBezeichnung()));
            o.put("typ", s.getKontoTyp() != null ? s.getKontoTyp().name() : null);
            if (s.getBeschreibung() != null) {
                o.put("beschreibung", safe(s.getBeschreibung()));
            }
        }
        return arr;
    }

    private JsonNode toolAehnlicheBelege(Beleg beleg) {
        ArrayNode arr = objectMapper.createArrayNode();
        if (beleg.getLieferant() == null || beleg.getLieferant().getId() == null) {
            // Kein Lieferant zugeordnet → leere Liste als Tool-Antwort.
            return arr;
        }
        List<Beleg> historie = belegRepository.findAehnlicheBelegeByLieferant(
                beleg.getLieferant().getId(),
                PageRequest.of(0, AEHNLICHE_BELEGE_LIMIT));
        for (Beleg h : historie) {
            if (h.getId() != null && h.getId().equals(beleg.getId())) continue;
            ObjectNode o = arr.addObject();
            o.put("belegId", h.getId());
            o.put("belegDatum", h.getBelegDatum() != null ? h.getBelegDatum().toString() : null);
            o.put("beschreibung", safe(h.getBeschreibung()));
            o.put("betragBrutto", h.getBetragBrutto() != null ? h.getBetragBrutto().toPlainString() : null);
            if (h.getKostenstelle() != null) {
                o.put("kostenstelleId", h.getKostenstelle().getId());
                o.put("kostenstelleBezeichnung", safe(h.getKostenstelle().getBezeichnung()));
            }
            if (h.getSachkonto() != null) {
                o.put("sachkontoId", h.getSachkonto().getId());
                o.put("sachkontoBezeichnung", safe(h.getSachkonto().getBezeichnung()));
            }
        }
        return arr;
    }

    // ---------------------------------------------------------------------
    // Endergebnis verarbeiten
    // ---------------------------------------------------------------------

    private AgentErgebnis parseFinaleZuordnung(JsonNode args) {
        if (args == null || args.isMissingNode() || args.isNull()) {
            return null;
        }
        return new AgentErgebnis(
                optionalLong(args, "kostenstelleId"),
                optionalLong(args, "sachkontoId"),
                optionalBigDecimal(args, "confidence"),
                optionalString(args, "begruendung"));
    }

    private AgentErgebnis parseFreienText(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(stripFences(text));
            return new AgentErgebnis(
                    optionalLong(root, "kostenstelleId"),
                    optionalLong(root, "sachkontoId"),
                    optionalBigDecimal(root, "confidence"),
                    optionalString(root, "begruendung"));
        } catch (Exception e) {
            log.debug("Konnte freien Text der KI nicht parsen: {}", e.getMessage());
            return null;
        }
    }

    private void wendeErgebnisAn(Beleg beleg, AgentErgebnis ergebnis) {
        // Vorschlaege immer protokollieren — selbst bei niedriger Confidence
        // dient das als Hinweis im UI ("KI haette das so eingeordnet").
        beleg.setKiVorgeschlagenerKostenstelleId(ergebnis.kostenstelleId());
        beleg.setKiVorgeschlagenerSachkontoId(ergebnis.sachkontoId());
        // Confidence auf [0,1] clampen — die DB-Spalte ist DECIMAL(3,2) und
        // wuerde Hallucination-Werte wie 1.5 oder -0.2 anstandslos schlucken.
        BigDecimal clampedConfidence = clampConfidence(ergebnis.confidence());
        beleg.setKiKostenkontoConfidence(clampedConfidence != null
                ? clampedConfidence.setScale(2, RoundingMode.HALF_UP) : null);
        String begr = ergebnis.begruendung();
        if (begr != null && begr.length() > 500) {
            begr = begr.substring(0, 500);
        }
        beleg.setKiKostenkontoBegruendung(begr);

        // Auto-Apply nur unter STRENGEN Bedingungen:
        //  1. Confidence >= 0.95 (siehe AUTO_APPLY_THRESHOLD).
        //  2. Gewaehlte Kostenstelle existiert wirklich (KI haette halluziniert?).
        //  3. Kostenstelle ist Fixkosten ODER Investition. Projekt-Material
        //     (egal wie sicher die KI ist) darf NIE auto-zugeordnet werden —
        //     solche Rechnungen muessen im Bestellungs-Workflow bleiben, damit
        //     der Buchhalter sie einem Projekt zuordnen kann.
        boolean highConfidence = clampedConfidence != null
                && clampedConfidence.compareTo(AUTO_APPLY_THRESHOLD) >= 0;
        boolean autoAppliedKostenstelle = false;

        if (highConfidence && ergebnis.kostenstelleId() != null) {
            Kostenstelle ks = kostenstelleRepository.findById(ergebnis.kostenstelleId()).orElse(null);
            if (ks != null && ks.isAktiv() && (ks.isIstFixkosten() || ks.isIstInvestition())) {
                beleg.setKostenstelle(ks);
                autoAppliedKostenstelle = true;
            } else if (ks != null) {
                log.info("Beleg {}: KI-Vorschlag ks={} ({}) ist KEINE aktive Fixkosten/"
                        + "Investitions-Kostenstelle — kein Auto-Apply, Beleg bleibt "
                        + "im Bestellungs-Workflow",
                        beleg.getId(), ks.getId(), ks.getBezeichnung());
            }
        }
        // Sachkonto nur uebernehmen, wenn auch die Kostenstelle auto-uebernommen
        // wurde — sonst hat der Buchhalter nichts zum Korrigieren mehr. Zusaetzlich
        // pruefen wir, dass das Sachkonto wirklich aktiv ist (KI darf nicht ein
        // deaktiviertes Konto setzen, das es in liste_sachkonten gar nicht gab).
        if (autoAppliedKostenstelle && ergebnis.sachkontoId() != null && beleg.getSachkonto() == null) {
            sachkontoRepository.findById(ergebnis.sachkontoId())
                    .filter(Sachkonto::isAktiv)
                    .ifPresent(beleg::setSachkonto);
        }

        log.info("KI-Agent Beleg {}: ks={} sk={} conf={} auto={}",
                beleg.getId(), ergebnis.kostenstelleId(), ergebnis.sachkontoId(),
                clampedConfidence, autoAppliedKostenstelle);
    }

    /**
     * Clampt einen KI-gelieferten Confidence-Wert auf das gueltige Intervall [0, 1].
     * Halluzinierte Werte wie 1.5 oder -0.2 wuerden sonst direkt persistiert.
     */
    private static BigDecimal clampConfidence(BigDecimal raw) {
        if (raw == null) return null;
        if (raw.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        if (raw.compareTo(BigDecimal.ONE) > 0) return BigDecimal.ONE;
        return raw;
    }

    // ---------------------------------------------------------------------
    // Gemini-API mit Function-Calling
    // ---------------------------------------------------------------------

    private JsonNode callGemini(String apiKey, ArrayNode contents) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel
                + ":generateContent?key=" + apiKey;

        String requestBody;
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.set("contents", contents.deepCopy());
            body.set("tools", buildToolDeclarations());
            ObjectNode systemInstruction = body.putObject("systemInstruction");
            ArrayNode sysParts = systemInstruction.putArray("parts");
            sysParts.addObject().put("text", SYSTEM_INSTRUCTION);
            requestBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.warn("KI-Agent Gemini-Call: Request konnte nicht serialisiert werden: {}", e.getMessage());
            return null;
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        // Retry-Loop: bei HTTP 429 / 5xx und IOExceptions bis zu MAX_GEMINI_ATTEMPTS
        // Versuche. 4xx (auser 429) sind echte Client-Fehler und werden NICHT
        // wiederholt — ein Retry wuerde nur dieselbe Antwort liefern.
        for (int attempt = 1; attempt <= MAX_GEMINI_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();
                if (status == 200) {
                    return objectMapper.readTree(resp.body());
                }

                String errBody = resp.body();
                if (errBody != null && errBody.length() > 500) {
                    errBody = errBody.substring(0, 500) + "...";
                }

                if (isRetryableStatus(status) && attempt < MAX_GEMINI_ATTEMPTS) {
                    long backoff = RETRY_BACKOFF_MS[attempt - 1];
                    log.warn("KI-Agent Gemini-Call Versuch {}/{}: HTTP {} — retry in {} ms",
                            attempt, MAX_GEMINI_ATTEMPTS, status, backoff);
                    sleepQuiet(backoff);
                    continue;
                }

                log.warn("KI-Agent Gemini-Call (Versuch {}/{}): HTTP {} — {}",
                        attempt, MAX_GEMINI_ATTEMPTS, status, errBody);
                return null;

            } catch (java.io.IOException ioe) {
                // Netzwerk-/Timeout-Fehler: ebenfalls retry-bar.
                if (attempt < MAX_GEMINI_ATTEMPTS) {
                    long backoff = RETRY_BACKOFF_MS[attempt - 1];
                    log.warn("KI-Agent Gemini-Call Versuch {}/{}: IO-Fehler ({}) — retry in {} ms",
                            attempt, MAX_GEMINI_ATTEMPTS, ioe.getMessage(), backoff);
                    sleepQuiet(backoff);
                    continue;
                }
                log.warn("KI-Agent Gemini-Call fehlgeschlagen nach {} Versuchen: {}",
                        MAX_GEMINI_ATTEMPTS, ioe.getMessage());
                return null;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("KI-Agent Gemini-Call unterbrochen: {}", ie.getMessage());
                return null;
            } catch (Exception e) {
                log.warn("KI-Agent Gemini-Call fehlgeschlagen: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }

    /**
     * HTTP-Status, bei dem ein Retry sinnvoll ist: Rate-Limit (429) und
     * serverseitige Storungen (5xx, inkl. dem von Gemini gelieferten 503
     * UNAVAILABLE). 4xx-Antworten (Bad Request, Unauthorized, Not Found)
     * sind deterministisch und werden nicht wiederholt.
     */
    static boolean isRetryableStatus(int status) {
        return status == 429 || (status >= 500 && status < 600);
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private ArrayNode buildToolDeclarations() {
        ArrayNode tools = objectMapper.createArrayNode();
        ObjectNode toolGroup = tools.addObject();
        ArrayNode decls = toolGroup.putArray("functionDeclarations");

        decls.add(decl("liste_kostenstellen",
                "Liefert die Liste aller aktiven Kostenstellen aus der Datenbank (id, bezeichnung, typ, istFixkosten, istInvestition).",
                emptyObjectSchema()));

        decls.add(decl("liste_sachkonten",
                "Liefert die Liste aller aktiven Sachkonten aus der Datenbank (id, nummer, bezeichnung, typ).",
                emptyObjectSchema()));

        decls.add(decl("aehnliche_belege",
                "Liefert die letzten Belege desselben Lieferanten, die schon eine Kostenstellen-Zuordnung haben. Nutze das, um historische Zuordnungen als Lernmaterial zu sehen.",
                emptyObjectSchema()));

        ObjectNode finalSchema = objectMapper.createObjectNode();
        finalSchema.put("type", "OBJECT");
        ObjectNode props = finalSchema.putObject("properties");
        props.putObject("kostenstelleId").put("type", "INTEGER")
                .put("description", "Die gewaehlte Kostenstellen-ID aus liste_kostenstellen. null erlaubt, wenn keine passt.");
        props.putObject("sachkontoId").put("type", "INTEGER")
                .put("description", "Die gewaehlte Sachkonto-ID aus liste_sachkonten. null erlaubt, wenn keine passt.");
        props.putObject("confidence").put("type", "NUMBER")
                .put("description", "0.0 bis 1.0 — wie sicher du dir bist.");
        props.putObject("begruendung").put("type", "STRING")
                .put("description", "Ein kurzer deutscher Satz, warum diese Wahl.");
        ArrayNode required = finalSchema.putArray("required");
        required.add("confidence");
        required.add("begruendung");
        decls.add(decl("finale_zuordnung",
                "Beendet den Agent-Lauf und liefert die finale Kostenstellen- und Sachkonto-Zuordnung. Rufe das erst auf, NACHDEM du die Listen geladen hast.",
                finalSchema));

        return tools;
    }

    private ObjectNode decl(String name, String description, ObjectNode parameters) {
        ObjectNode d = objectMapper.createObjectNode();
        d.put("name", name);
        d.put("description", description);
        d.set("parameters", parameters);
        return d;
    }

    private ObjectNode emptyObjectSchema() {
        ObjectNode s = objectMapper.createObjectNode();
        s.put("type", "OBJECT");
        s.putObject("properties");
        return s;
    }

    // ---------------------------------------------------------------------
    // Konversations-Bausteine
    // ---------------------------------------------------------------------

    private ObjectNode userTurn(String text) {
        ObjectNode turn = objectMapper.createObjectNode();
        turn.put("role", "user");
        ArrayNode parts = turn.putArray("parts");
        parts.addObject().put("text", text);
        return turn;
    }

    private ObjectNode modelTurn(JsonNode antwort) {
        // Wir uebernehmen den ersten Candidate-Content unveraendert, damit
        // functionCall + ggf. begleitender Text identisch im naechsten Turn
        // wieder eingespielt werden.
        ObjectNode turn = objectMapper.createObjectNode();
        turn.put("role", "model");
        JsonNode content = antwort.path("candidates").path(0).path("content");
        if (content.isObject() && content.has("parts")) {
            turn.set("parts", content.get("parts").deepCopy());
        } else {
            turn.putArray("parts");
        }
        return turn;
    }

    private ObjectNode toolResponseTurn(String toolName, JsonNode result) {
        ObjectNode turn = objectMapper.createObjectNode();
        // Gemini erwartet "user"-Role fuer functionResponse-Parts.
        turn.put("role", "user");
        ArrayNode parts = turn.putArray("parts");
        ObjectNode part = parts.addObject();
        ObjectNode functionResponse = part.putObject("functionResponse");
        functionResponse.put("name", toolName);
        ObjectNode responseWrapper = functionResponse.putObject("response");
        responseWrapper.put("name", toolName);
        responseWrapper.set("content", result);
        return turn;
    }

    private JsonNode findeFunctionCall(JsonNode antwort) {
        JsonNode parts = antwort.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) return null;
        for (JsonNode part : parts) {
            JsonNode fc = part.get("functionCall");
            if (fc != null && !fc.isNull()) {
                return fc;
            }
        }
        return null;
    }

    private String findeText(JsonNode antwort) {
        JsonNode parts = antwort.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.has("text")) {
                sb.append(part.get("text").asText());
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------
    // Initial-Prompt
    // ---------------------------------------------------------------------

    private static final String SYSTEM_INSTRUCTION = """
            Du bist ein autonomer Buchhaltungs-Agent fuer einen deutschen Handwerksbetrieb.
            Du arbeitest in mehreren Schritten: erst Informationen sammeln, dann entscheiden.

            VERPFLICHTENDE ARBEITSWEISE:
            1. Rufe zuerst liste_kostenstellen auf, um die verfuegbaren Kostenstellen zu sehen.
            2. Rufe danach liste_sachkonten auf, um die Sachkonto-Optionen zu kennen.
            3. Wenn der Beleg einen Lieferanten hat, rufe aehnliche_belege auf — vergangene
               Zuordnungen sind das beste Lernmaterial.
            4. Erst dann darfst du finale_zuordnung mit deiner Entscheidung aufrufen.

            EISERNE REGEL — WANN DU DICH ENTHALTEN MUSST:
            Du darfst eine Kostenstelle NUR vorschlagen, wenn der Beleg EINDEUTIG ein
            laufender Fixkosten-/Gemeinkosten-Posten ist. Beispiele, wo du JA zuordnen darfst:
              - Tankquittung, Sprit, Diesel, AdBlue
              - Telefonrechnung, Internet, Mobilfunk (Telekom, Vodafone, 1&1, o2)
              - Strom, Gas, Wasser, Heizung
              - Miete, Pacht, Leasing-Rate Fuhrpark
              - Versicherung, Steuerberater, Werkstatt-Versicherung
              - Bueromaterial, Druckerpatronen, Briefmarken
              - Werkzeug-Kleinkram (Bohrer, Schraubendreher unter Geringwertgrenze)
              - Wartung Werkstattgeraete

            Beispiele, wo du DICH ENTHALTEN MUSST (kostenstelleId=null, confidence<0.5):
              - Material wie Roh-/Halbzeug, Stahl, Bleche, Rohre, Profile (z.B. "Rohr 40x40x3",
                "Blech S235JR", "Schrauben M8x40", "Schweissdraht 1.2mm") — solche Posten
                gehoeren zu einem KONKRETEN Projekt und werden im Bestellungs-Workflow
                vom Buchhalter dem richtigen Projekt zugeordnet.
              - Baustellen-Geraetemieten, die fuer einen einzelnen Auftrag gemietet wurden.
              - Fremdleistungen / Subunternehmer-Rechnungen.
              - Allgemein: alles, was mit hoher Wahrscheinlichkeit ein Projekt-Material ist
                und nicht klar zu einem laufenden Gemeinkosten-Topf gehoert.

            FORMAT-REGELN:
            1. Verwende NUR IDs aus den Tool-Ergebnissen, die du wirklich gesehen hast.
            2. Setze IDs auf null bei Unsicherheit — niedrige confidence ist BESSER als
               ein falscher Treffer. Lieber 5 unsichere Belege, die der Buchhalter sieht,
               als 1 falsch zugewiesener Beleg, der spaeter mit Aufwand zurueckgebucht wird.
            3. Kostenstellen vom Typ PROJEKT NIEMALS waehlen.
            4. Confidence-Skala:
                  >= 0.95  Beleg passt zu 100% (Tankquittung an Esso, ARAL etc.)
                  0.70-0.94  wahrscheinlich, aber nicht eindeutig
                  0.30-0.69  geraten, du bist unsicher
                  <  0.30  bitte null/null als IDs zurueckgeben
            5. Antworte ausschliesslich ueber Tool-Aufrufe — keinen Fliesstext zwischendurch.
            """;

    private String buildInitialPrompt(Beleg beleg) {
        String lieferantName = beleg.getLieferant() != null
                ? beleg.getLieferant().getLieferantenname()
                : beleg.getKiVorgeschlagenerLieferant();
        boolean hatLieferantId = beleg.getLieferant() != null && beleg.getLieferant().getId() != null;

        return """
                Klassifiziere diesen Beleg.

                BELEG-DATEN:
                - Lieferant: %s%s
                - Belegnummer: %s
                - Belegdatum: %s
                - Brutto: %s EUR
                - Netto: %s EUR
                - MwSt-Satz: %s
                - Beschreibung: %s
                - Dokumenttyp: %s
                - Original-Dateiname: %s

                Beginne jetzt mit den Tool-Aufrufen wie in der System-Instruktion beschrieben.
                """.formatted(
                        nullToDash(lieferantName),
                        hatLieferantId ? "" : " (kein Lieferant in der DB verknuepft — aehnliche_belege ist dann leer)",
                        nullToDash(beleg.getBelegNummer()),
                        nullToDash(beleg.getBelegDatum() != null ? beleg.getBelegDatum().toString() : null),
                        nullToDash(beleg.getBetragBrutto() != null ? beleg.getBetragBrutto().toPlainString() : null),
                        nullToDash(beleg.getBetragNetto() != null ? beleg.getBetragNetto().toPlainString() : null),
                        nullToDash(beleg.getMwstSatz() != null ? beleg.getMwstSatz().toPlainString() : null),
                        nullToDash(beleg.getBeschreibung()),
                        nullToDash(beleg.getDokumentTyp() != null ? beleg.getDokumentTyp().name() : null),
                        nullToDash(beleg.getOriginalDateiname()));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private record AgentErgebnis(Long kostenstelleId,
                                 Long sachkontoId,
                                 BigDecimal confidence,
                                 String begruendung) {}

    private static String stripFences(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.startsWith("```json")) {
            int start = t.indexOf("```json") + 7;
            int end = t.indexOf("```", start);
            if (end > start) return t.substring(start, end).trim();
        } else if (t.startsWith("```")) {
            int start = t.indexOf("```") + 3;
            int end = t.indexOf("```", start);
            if (end > start) return t.substring(start, end).trim();
        }
        return t;
    }

    private static Long optionalLong(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) return null;
        if (n.isNumber()) return n.asLong();
        String s = n.asText().trim();
        if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal optionalBigDecimal(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) return null;
        try { return new BigDecimal(n.asText()); } catch (Exception e) { return null; }
    }

    private static String optionalString(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) return null;
        String s = n.asText();
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String safe(String s) {
        if (s == null) return "";
        return s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
