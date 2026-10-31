package org.example.kalkulationsprogramm.service.ki;

import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.service.GeminiAnfrage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Google Gemini. Kann alles, was das Programm braucht; Werkzeuge, Programm-Wissen
 * und Sprache laufen bislang nur hier (die Dienste dafür sprechen Gemini direkt an
 * und holen Schlüssel und Modell über {@link KiDienst#geminiZugang(KiFunktion)}).
 */
@Slf4j
@Component
public class GeminiKiAnbieter implements KiAnbieter {

    static final String NAME = "Google Gemini";
    private static final Duration LISTE_ZEITLIMIT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<KiModellStufe, String> standardModelle;

    @Autowired
    public GeminiKiAnbieter(ObjectMapper objectMapper,
                            @Value("${ai.gemini.model.email-classification:gemini-3.5-flash-lite}") String schnell,
                            @Value("${ai.gemini.model.dokument-analyse:gemini-3.8-flash}") String standard,
                            @Value("${ai.gemini.model.pro:gemini-3.8-flash}") String gruendlich) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(),
                schnell, standard, gruendlich);
    }

    /** Test-Konstruktor mit injizierbarem HttpClient. */
    GeminiKiAnbieter(ObjectMapper objectMapper, HttpClient httpClient,
                     String schnell, String standard, String gruendlich) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        Map<KiModellStufe, String> modelle = new EnumMap<>(KiModellStufe.class);
        modelle.put(KiModellStufe.SCHNELL, schnell);
        modelle.put(KiModellStufe.STANDARD, standard);
        modelle.put(KiModellStufe.GRUENDLICH, gruendlich);
        this.standardModelle = Map.copyOf(modelle);
    }

    @Override
    public KiAnbieterTyp typ() {
        return KiAnbieterTyp.GEMINI;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String beschreibung() {
        return "KI von Google – die Daten können auch außerhalb der EU verarbeitet werden.";
    }

    @Override
    public KiDatenstandort datenstandort() {
        return KiDatenstandort.WELTWEIT;
    }

    @Override
    public Set<KiFaehigkeit> faehigkeiten() {
        return EnumSet.allOf(KiFaehigkeit.class);
    }

    @Override
    public boolean schluesselVerschluesselt() {
        // Historisch im Klartext in system_setting (CLAUDE.md, Sicherheitsregel 1).
        return false;
    }

    @Override
    public Map<KiModellStufe, String> standardModelle() {
        return standardModelle;
    }

    @Override
    public KiErgebnis sende(KiAnfrage anfrage, String modell, String schluessel) {
        String koerper;
        try {
            koerper = objectMapper.writeValueAsString(baueKoerper(anfrage));
        } catch (JacksonException e) {
            throw new KiAnbieterFehler("Die Anfrage an " + NAME + " konnte nicht gebaut werden.", false, e);
        }
        HttpRequest request;
        try {
            request = GeminiAnfrage.generateContent(modell, schluessel)
                    .timeout(anfrage.zeitlimit())
                    .POST(HttpRequest.BodyPublishers.ofString(koerper, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException e) {
            // Die JDK-Meldung zitiert den Header-Wert, also den Schlüssel: nicht weiterreichen.
            throw new KiAnbieterFehler("Der Schlüssel für " + NAME + " enthält ungültige Zeichen.", 0, false);
        }
        HttpResponse<String> antwort = KiHttp.sende(httpClient, request, NAME);
        return liesAntwort(antwort.body(), modell);
    }

    /** Sichtbar für den Test: der Gemini-Körper zur neutralen Anfrage. */
    ObjectNode baueKoerper(KiAnfrage anfrage) {
        ObjectNode koerper = objectMapper.createObjectNode();
        if (!anfrage.systemAnweisung().isBlank()) {
            koerper.putObject("systemInstruction").putArray("parts").addObject()
                    .put("text", anfrage.systemAnweisung());
        }
        ArrayNode contents = koerper.putArray("contents");
        for (KiNachricht nachricht : anfrage.nachrichten()) {
            ObjectNode eintrag = contents.addObject();
            eintrag.put("role", nachricht.rolle() == KiNachricht.Rolle.KI ? "model" : "user");
            ArrayNode parts = eintrag.putArray("parts");
            for (KiTeil teil : nachricht.teile()) {
                if (teil instanceof KiTeil.Text text) {
                    parts.addObject().put("text", text.text());
                } else if (teil instanceof KiTeil.Datei datei) {
                    ObjectNode inline = parts.addObject().putObject("inlineData");
                    inline.put("mimeType", datei.mimeType());
                    inline.put("data", Base64.getEncoder().encodeToString(datei.daten()));
                }
            }
        }
        ObjectNode konfiguration = koerper.putObject("generationConfig");
        if (anfrage.temperatur() != null) {
            konfiguration.put("temperature", anfrage.temperatur());
        }
        konfiguration.put("responseMimeType", anfrage.jsonAntwort() ? "application/json" : "text/plain");
        if (anfrage.schema() != null) {
            konfiguration.set("responseSchema", anfrage.schema());
        }
        if (anfrage.maxAusgabe() != null) {
            konfiguration.put("maxOutputTokens", anfrage.maxAusgabe());
        }
        return koerper;
    }

    private KiErgebnis liesAntwort(String body, String modell) {
        JsonNode wurzel;
        try {
            wurzel = objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw new KiAnbieterFehler("Die Antwort von " + NAME + " war nicht lesbar.", false, e);
        }
        JsonNode kandidat = wurzel.path("candidates").path(0);
        if (kandidat.isMissingNode()) {
            // Meist eine Sperre durch den Inhaltsfilter; promptFeedback enthält nur Kategorien.
            log.warn("[KI {}] Keine Antwort erhalten, promptFeedback: {}", NAME,
                    KiHttp.fuerLog(wurzel.path("promptFeedback").toString()));
            throw new KiAnbieterFehler(NAME + " hat keine Antwort geliefert (womöglich vom Inhaltsfilter gesperrt).",
                    200, false);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode teil : kandidat.path("content").path("parts")) {
            // Gedanken-Teile der Denk-Modelle gehören nicht zur Antwort.
            if (!teil.path("thought").asBoolean(false)) {
                text.append(teil.path("text").asString(""));
            }
        }
        boolean abgeschnitten = "MAX_TOKENS".equals(kandidat.path("finishReason").asString(""));
        return new KiErgebnis(text.toString(), abgeschnitten, KiAnbieterTyp.GEMINI, modell);
    }

    @Override
    public List<KiModellInfo> ladeModelle(String schluessel) {
        HttpRequest request;
        try {
            request = GeminiAnfrage.modellListe(schluessel.strip(), 1000).timeout(LISTE_ZEITLIMIT).GET().build();
        } catch (IllegalArgumentException e) {
            // Die JDK-Meldung zitiert den Header-Wert, also den Schlüssel: nicht weiterreichen.
            throw new KiAnbieterFehler("Der Schlüssel enthält ungültige Zeichen. Bitte neu kopieren.", 0, false);
        }
        HttpResponse<String> antwort = KiHttp.sende(httpClient, request, NAME);
        List<KiModellInfo> modelle = new ArrayList<>();
        try {
            for (JsonNode modell : objectMapper.readTree(antwort.body()).path("models")) {
                String name = modell.path("name").asString("");
                String kurz = name.startsWith("models/") ? name.substring("models/".length()) : name;
                if (kurz.isBlank()) {
                    continue;
                }
                boolean erzeugtText = false;
                for (JsonNode methode : modell.path("supportedGenerationMethods")) {
                    erzeugtText |= "generateContent".equals(methode.asString(""));
                }
                boolean chat = erzeugtText && !istSondermodell(kurz);
                modelle.add(new KiModellInfo(kurz, modell.path("displayName").asString(kurz),
                        kurz.startsWith("gemini") ? Boolean.TRUE : null, chat, List.of()));
            }
        } catch (JacksonException e) {
            throw new KiAnbieterFehler("Die Modellliste von Google war nicht lesbar.", false, e);
        }
        return modelle;
    }

    /** Modelle für Bilder, Videos, Sprachausgabe oder Vektoren taugen nicht für Auswertungen. */
    private static boolean istSondermodell(String modell) {
        String klein = modell.toLowerCase(Locale.ROOT);
        return klein.contains("embedding") || klein.contains("imagen") || klein.contains("veo")
                || klein.contains("tts") || klein.contains("image-generation") || klein.contains("aqa");
    }
}
