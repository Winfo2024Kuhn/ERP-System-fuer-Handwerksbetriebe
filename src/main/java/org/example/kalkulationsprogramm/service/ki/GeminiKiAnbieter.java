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
 * Google Gemini. Kann alles, was das Programm braucht, auch die Google-Suche.
 *
 * <p>Werkzeuge: Gemini 3 hängt an Teile seiner Antwort Gedanken-Signaturen
 * ({@code thoughtSignature}) und bei der Google-Suche eigene Aufruf-Teile
 * ({@code toolCall}/{@code toolResponse}). Beides muss im nächsten Zug unverändert
 * zurück – deshalb geht der Zug des Modells als {@link KiRohZug} mit und wird nie
 * neu gebaut, solange er von Gemini stammt.
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
        return liesAntwort(antwort.body(), modell, anfrage.mitWerkzeugen());
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
            KiRohZug roh = nachricht.rohVon(KiAnbieterTyp.GEMINI);
            if (roh != null) {
                contents.add(roh.inhalt().deepCopy());
                continue;
            }
            ObjectNode eintrag = contents.addObject();
            // Gemini erwartet die Werkzeug-Ergebnisse mit der Rolle "user".
            eintrag.put("role", nachricht.rolle() == KiNachricht.Rolle.KI ? "model" : "user");
            ArrayNode parts = eintrag.putArray("parts");
            for (KiTeil teil : nachricht.teile()) {
                haengeTeilAn(parts, teil);
            }
        }
        ObjectNode konfiguration = koerper.putObject("generationConfig");
        if (anfrage.temperatur() != null) {
            konfiguration.put("temperature", anfrage.temperatur());
        }
        if (anfrage.jsonAntwort()) {
            konfiguration.put("responseMimeType", "application/json");
        } else if (!anfrage.mitWerkzeugen()) {
            // Mit Werkzeugen oder Google-Suche bleibt das Format offen – so schickte es die
            // Programm-Hilfe schon immer, und Gemini lehnt feste Formate dort teils ab.
            konfiguration.put("responseMimeType", "text/plain");
        }
        if (anfrage.schema() != null) {
            konfiguration.set("responseSchema", anfrage.schema());
        }
        if (anfrage.maxAusgabe() != null) {
            konfiguration.put("maxOutputTokens", anfrage.maxAusgabe());
        }
        haengeWerkzeugeAn(koerper, anfrage);
        return koerper;
    }

    private void haengeTeilAn(ArrayNode parts, KiTeil teil) {
        switch (teil) {
            case KiTeil.Text text -> parts.addObject().put("text", text.text());
            case KiTeil.Datei datei -> {
                ObjectNode inline = parts.addObject().putObject("inlineData");
                inline.put("mimeType", datei.mimeType());
                inline.put("data", Base64.getEncoder().encodeToString(datei.daten()));
            }
            case KiTeil.WerkzeugAufruf aufruf -> {
                ObjectNode call = parts.addObject().putObject("functionCall");
                if (!aufruf.id().isEmpty()) {
                    call.put("id", aufruf.id());
                }
                call.put("name", aufruf.name());
                call.set("args", aufruf.argumente());
            }
            case KiTeil.WerkzeugErgebnis ergebnis -> {
                // Die id des Aufrufs geht mit zurück - so ordnet Gemini bei mehreren
                // gleichzeitigen Aufrufen jede Antwort ihrem Aufruf zu.
                ObjectNode antwort = parts.addObject().putObject("functionResponse");
                if (!ergebnis.id().isEmpty()) {
                    antwort.put("id", ergebnis.id());
                }
                antwort.put("name", ergebnis.name());
                // Gemini nimmt als Antwort nur ein Objekt an.
                antwort.set("response", ergebnis.ergebnis().isObject() ? ergebnis.ergebnis()
                        : objectMapper.createObjectNode().set("ergebnis", ergebnis.ergebnis()));
            }
        }
    }

    /** Google-Suche und eigene Werkzeuge ({@code functionDeclarations}) samt Modus. */
    private void haengeWerkzeugeAn(ObjectNode koerper, KiAnfrage anfrage) {
        ArrayNode tools = objectMapper.createArrayNode();
        if (anfrage.websuche()) {
            tools.addObject().putObject("googleSearch");
        }
        boolean eigene = !anfrage.werkzeuge().isEmpty();
        if (eigene) {
            ArrayNode deklarationen = tools.addObject().putArray("functionDeclarations");
            for (KiWerkzeug werkzeug : anfrage.werkzeuge()) {
                deklarationen.addObject()
                        .put("name", werkzeug.name())
                        .put("description", werkzeug.beschreibung())
                        .set("parameters", werkzeug.parameter());
            }
        }
        if (!tools.isEmpty()) {
            koerper.set("tools", tools);
        }
        boolean antwortErzwingen = anfrage.werkzeugModus() == KiAnfrage.WerkzeugModus.KEINE;
        if (eigene && (anfrage.websuche() || antwortErzwingen)) {
            ObjectNode toolConfig = koerper.putObject("toolConfig");
            if (anfrage.websuche()) {
                // Google-Suche und eigene Werkzeuge in einer Anfrage (Gemini 3): Die Suchaufrufe
                // kommen dann im Verlauf mit und gehen im nächsten Zug unverändert zurück.
                toolConfig.put("includeServerSideToolInvocations", true);
            }
            if (antwortErzwingen) {
                toolConfig.putObject("functionCallingConfig").put("mode", "NONE");
            }
        }
    }

    private KiErgebnis liesAntwort(String body, String modell, boolean mitWerkzeugen) {
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
        JsonNode teile = kandidat.path("content").path("parts");
        // Gespräche mit Werkzeugen liefern Text in Abschnitten zwischen Aufrufen: eine Zeile dazwischen.
        String trenner = mitWerkzeugen ? "\n" : "";
        StringBuilder text = new StringBuilder();
        List<KiTeil.WerkzeugAufruf> aufrufe = new ArrayList<>();
        for (JsonNode teil : teile) {
            JsonNode aufruf = teil.get("functionCall");
            if (aufruf != null && aufruf.isObject()) {
                aufrufe.add(new KiTeil.WerkzeugAufruf(aufruf.path("id").asString(""),
                        aufruf.path("name").asString(""), aufruf.path("args")));
            }
            // Gedanken-Teile der Denk-Modelle gehören nicht zur Antwort.
            String stueck = teil.path("thought").asBoolean(false) ? "" : teil.path("text").asString("");
            if (!stueck.isEmpty()) {
                if (!text.isEmpty()) {
                    text.append(trenner);
                }
                text.append(stueck);
            }
        }
        String ende = kandidat.path("finishReason").asString("");
        return new KiErgebnis(text.toString(), "MAX_TOKENS".equals(ende), KiAnbieterTyp.GEMINI, modell,
                aufrufe, "MALFORMED_FUNCTION_CALL".equals(ende), websuchen(kandidat), quellen(kandidat),
                new KiRohZug(KiAnbieterTyp.GEMINI, modellZug(teile)));
    }

    /** Der Zug des Modells für den Verlauf: alle Teile unverändert (Signaturen, Suchaufrufe). */
    private ObjectNode modellZug(JsonNode teile) {
        ObjectNode zug = objectMapper.createObjectNode();
        zug.put("role", "model");
        if (teile.isArray()) {
            zug.set("parts", teile.deepCopy());
        } else {
            zug.putArray("parts");
        }
        return zug;
    }

    private static List<String> websuchen(JsonNode kandidat) {
        List<String> suchen = new ArrayList<>();
        for (JsonNode suche : kandidat.path("groundingMetadata").path("webSearchQueries")) {
            suchen.add(suche.asString(""));
        }
        return suchen;
    }

    private static List<KiQuelle> quellen(JsonNode kandidat) {
        List<KiQuelle> quellen = new ArrayList<>();
        for (JsonNode stueck : kandidat.path("groundingMetadata").path("groundingChunks")) {
            JsonNode web = stueck.path("web");
            String adresse = web.path("uri").asString("");
            if (!adresse.isEmpty()) {
                quellen.add(new KiQuelle(web.path("title").asString(""), adresse));
            }
        }
        return quellen;
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
