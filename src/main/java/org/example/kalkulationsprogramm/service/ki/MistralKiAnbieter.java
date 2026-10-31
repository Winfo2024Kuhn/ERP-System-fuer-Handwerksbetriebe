package org.example.kalkulationsprogramm.service.ki;

import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Mistral AI (Frankreich), Verarbeitung nach Angabe von Mistral in der EU.
 *
 * <p>Text und Bilder gehen direkt an den Chat. PDFs liest zuerst die
 * Texterkennung von Mistral ({@code /v1/ocr}); deren Text bekommt der Chat.
 * Sprache und Werkzeuge sind hier noch nicht gebaut – die Funktionen dafür
 * laufen über einen Ersatz-Anbieter oder sind aus ({@link KiDienst}).
 */
@Slf4j
@Component
public class MistralKiAnbieter implements KiAnbieter {

    static final String NAME = "Mistral";
    static final String BASIS_URL = "https://api.mistral.ai/v1";
    private static final Duration LISTE_ZEITLIMIT = Duration.ofSeconds(10);
    /** Bildformate, die Mistral annimmt. */
    static final Set<String> BILDFORMATE = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String ocrModell;
    private final Map<KiModellStufe, String> standardModelle;

    @Autowired
    public MistralKiAnbieter(ObjectMapper objectMapper,
                             @Value("${ai.mistral.model.schnell:mistral-small-latest}") String schnell,
                             @Value("${ai.mistral.model.standard:mistral-large-latest}") String standard,
                             @Value("${ai.mistral.model.gruendlich:mistral-large-latest}") String gruendlich,
                             @Value("${ai.mistral.model.texterkennung:mistral-ocr-latest}") String ocrModell) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(),
                schnell, standard, gruendlich, ocrModell);
    }

    /** Test-Konstruktor mit injizierbarem HttpClient. */
    MistralKiAnbieter(ObjectMapper objectMapper, HttpClient httpClient,
                      String schnell, String standard, String gruendlich, String ocrModell) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.ocrModell = ocrModell;
        Map<KiModellStufe, String> modelle = new EnumMap<>(KiModellStufe.class);
        modelle.put(KiModellStufe.SCHNELL, schnell);
        modelle.put(KiModellStufe.STANDARD, standard);
        modelle.put(KiModellStufe.GRUENDLICH, gruendlich);
        this.standardModelle = Map.copyOf(modelle);
    }

    @Override
    public KiAnbieterTyp typ() {
        return KiAnbieterTyp.MISTRAL;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String beschreibung() {
        return "Europäischer Anbieter – die Daten werden nach Angabe von Mistral in der EU verarbeitet.";
    }

    @Override
    public KiDatenstandort datenstandort() {
        return KiDatenstandort.EU;
    }

    @Override
    public Set<KiFaehigkeit> faehigkeiten() {
        return EnumSet.of(KiFaehigkeit.TEXT, KiFaehigkeit.BILD, KiFaehigkeit.PDF);
    }

    @Override
    public boolean schluesselVerschluesselt() {
        return true;
    }

    @Override
    public Map<KiModellStufe, String> standardModelle() {
        return standardModelle;
    }

    @Override
    public KiErgebnis sende(KiAnfrage anfrage, String modell, String schluessel) {
        ObjectNode koerper = baueKoerper(anfrage, modell, datei -> leseText(datei, schluessel, anfrage.zeitlimit()));
        HttpResponse<String> antwort = post("/chat/completions", koerper, schluessel, anfrage.zeitlimit());
        return liesAntwort(antwort.body(), modell);
    }

    /** Liefert den Text einer PDF-Datei; im Test ersetzbar. */
    @FunctionalInterface
    interface PdfLeser {
        String lies(KiTeil.Datei datei);
    }

    /** Sichtbar für den Test: der Chat-Körper zur neutralen Anfrage. */
    ObjectNode baueKoerper(KiAnfrage anfrage, String modell, PdfLeser pdfLeser) {
        ObjectNode koerper = objectMapper.createObjectNode();
        koerper.put("model", modell);
        ArrayNode messages = koerper.putArray("messages");
        if (!anfrage.systemAnweisung().isBlank()) {
            messages.addObject().put("role", "system").put("content", anfrage.systemAnweisung());
        }
        for (KiNachricht nachricht : anfrage.nachrichten()) {
            if (nachricht.rolle() == KiNachricht.Rolle.KI) {
                messages.addObject().put("role", "assistant").put("content", nurText(nachricht));
                continue;
            }
            ObjectNode eintrag = messages.addObject().put("role", "user");
            ArrayNode inhalt = eintrag.putArray("content");
            for (KiTeil teil : nachricht.teile()) {
                if (teil instanceof KiTeil.Text text) {
                    inhalt.addObject().put("type", "text").put("text", text.text());
                } else if (teil instanceof KiTeil.Datei datei) {
                    haengeDateiAn(inhalt, datei, pdfLeser);
                }
            }
        }
        if (anfrage.temperatur() != null) {
            koerper.put("temperature", anfrage.temperatur());
        }
        if (anfrage.maxAusgabe() != null) {
            koerper.put("max_tokens", anfrage.maxAusgabe());
        }
        if (anfrage.schema() != null) {
            ObjectNode format = koerper.putObject("response_format").put("type", "json_schema");
            ObjectNode jsonSchema = format.putObject("json_schema");
            jsonSchema.put("name", "antwort");
            jsonSchema.set("schema", alsJsonSchema(anfrage.schema()));
            jsonSchema.put("strict", false);
        } else if (anfrage.jsonAntwort()) {
            koerper.putObject("response_format").put("type", "json_object");
        }
        return koerper;
    }

    private void haengeDateiAn(ArrayNode inhalt, KiTeil.Datei datei, PdfLeser pdfLeser) {
        if (datei.istBild() && !BILDFORMATE.contains(datei.mimeType())) {
            throw new KiAnbieterFehler(NAME + " kann dieses Bildformat nicht lesen (" + datei.mimeType()
                    + "). Bitte als JPG, PNG, WebP oder GIF speichern.", 0, false);
        }
        if (datei.istBild()) {
            inhalt.addObject().put("type", "image_url").put("image_url",
                    "data:" + datei.mimeType() + ";base64," + Base64.getEncoder().encodeToString(datei.daten()));
        } else if (datei.istPdf()) {
            inhalt.addObject().put("type", "text").put("text",
                    "Inhalt des PDF-Dokuments (per Texterkennung gelesen):\n\n" + pdfLeser.lies(datei));
        } else if (datei.istKlartext()) {
            inhalt.addObject().put("type", "text").put("text",
                    "Inhalt der Datei (" + datei.mimeType() + "):\n\n"
                            + new String(datei.daten(), StandardCharsets.UTF_8));
        } else {
            throw new KiAnbieterFehler(NAME + " kann diese Dateiart in diesem Programm noch nicht lesen ("
                    + datei.mimeType() + ").", 0, false);
        }
    }

    private static String nurText(KiNachricht nachricht) {
        StringBuilder text = new StringBuilder();
        for (KiTeil teil : nachricht.teile()) {
            if (teil instanceof KiTeil.Text t) {
                text.append(t.text());
            }
        }
        return text.toString();
    }

    /** Liest eine PDF-Datei mit der Texterkennung von Mistral; Ergebnis als Markdown je Seite. */
    private String leseText(KiTeil.Datei datei, String schluessel, Duration zeitlimit) {
        ObjectNode koerper = objectMapper.createObjectNode();
        koerper.put("model", ocrModell);
        ObjectNode dokument = koerper.putObject("document");
        dokument.put("type", "document_url");
        dokument.put("document_url",
                "data:application/pdf;base64," + Base64.getEncoder().encodeToString(datei.daten()));
        koerper.put("include_image_base64", false);

        HttpResponse<String> antwort = post("/ocr", koerper, schluessel, zeitlimit);
        try {
            List<String> seiten = new ArrayList<>();
            for (JsonNode seite : objectMapper.readTree(antwort.body()).path("pages")) {
                seiten.add(seite.path("markdown").asString(""));
            }
            return String.join("\n\n", seiten);
        } catch (JacksonException e) {
            throw new KiAnbieterFehler("Die Texterkennung von " + NAME + " lieferte eine unlesbare Antwort.",
                    false, e);
        }
    }

    private HttpResponse<String> post(String pfad, ObjectNode koerper, String schluessel, Duration zeitlimit) {
        String json;
        try {
            json = objectMapper.writeValueAsString(koerper);
        } catch (JacksonException e) {
            throw new KiAnbieterFehler("Die Anfrage an " + NAME + " konnte nicht gebaut werden.", false, e);
        }
        HttpRequest request;
        try {
            request = mitSchluessel(pfad, schluessel)
                    .timeout(zeitlimit)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException e) {
            // Die JDK-Meldung zitiert den Header-Wert, also den Schlüssel: nicht weiterreichen.
            throw new KiAnbieterFehler("Der Schlüssel für " + NAME + " enthält ungültige Zeichen.", 0, false);
        }
        return KiHttp.sende(httpClient, request, NAME);
    }

    private static HttpRequest.Builder mitSchluessel(String pfad, String schluessel) {
        return HttpRequest.newBuilder(URI.create(BASIS_URL + pfad))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + schluessel.strip());
    }

    private KiErgebnis liesAntwort(String body, String modell) {
        JsonNode wahl;
        try {
            wahl = objectMapper.readTree(body).path("choices").path(0);
        } catch (JacksonException e) {
            throw new KiAnbieterFehler("Die Antwort von " + NAME + " war nicht lesbar.", false, e);
        }
        if (wahl.isMissingNode()) {
            throw new KiAnbieterFehler(NAME + " hat keine Antwort geliefert.", 200, false);
        }
        JsonNode inhalt = wahl.path("message").path("content");
        String text;
        if (inhalt.isArray()) {
            // Denk-Modelle liefern Abschnitte; zur Antwort gehören nur die Text-Abschnitte.
            StringBuilder sb = new StringBuilder();
            for (JsonNode abschnitt : inhalt) {
                if ("text".equals(abschnitt.path("type").asString(""))) {
                    sb.append(abschnitt.path("text").asString(""));
                }
            }
            text = sb.toString();
        } else {
            text = inhalt.asString("");
        }
        boolean abgeschnitten = "length".equals(wahl.path("finish_reason").asString(""));
        return new KiErgebnis(text, abgeschnitten, KiAnbieterTyp.MISTRAL, modell);
    }

    /**
     * Übersetzt ein Schema im Gemini-Stil ({@code "type": "OBJECT"}, {@code nullable})
     * in JSON Schema, wie Mistral es erwartet.
     */
    JsonNode alsJsonSchema(JsonNode gemini) {
        if (gemini.isArray()) {
            ArrayNode liste = objectMapper.createArrayNode();
            gemini.forEach(eintrag -> liste.add(alsJsonSchema(eintrag)));
            return liste;
        }
        if (!gemini.isObject()) {
            return gemini;
        }
        ObjectNode ergebnis = objectMapper.createObjectNode();
        boolean nullable = gemini.path("nullable").asBoolean(false);
        for (Map.Entry<String, JsonNode> feld : gemini.properties()) {
            String name = feld.getKey();
            JsonNode wert = feld.getValue();
            if ("nullable".equals(name) || "propertyOrdering".equals(name)) {
                continue;
            }
            if ("type".equals(name) && wert.isString()) {
                String typ = wert.asString("").toLowerCase(Locale.ROOT);
                if (nullable) {
                    ergebnis.putArray("type").add(typ).add("null");
                } else {
                    ergebnis.put("type", typ);
                }
            } else {
                ergebnis.set(name, alsJsonSchema(wert));
            }
        }
        return ergebnis;
    }

    @Override
    public List<KiModellInfo> ladeModelle(String schluessel) {
        HttpRequest request;
        try {
            request = mitSchluessel("/models", schluessel).timeout(LISTE_ZEITLIMIT).GET().build();
        } catch (IllegalArgumentException e) {
            // Die JDK-Meldung zitiert den Header-Wert, also den Schlüssel: nicht weiterreichen.
            throw new KiAnbieterFehler("Der Schlüssel enthält ungültige Zeichen. Bitte neu kopieren.", 0, false);
        }
        HttpResponse<String> antwort = KiHttp.sende(httpClient, request, NAME);
        List<KiModellInfo> modelle = new ArrayList<>();
        try {
            for (JsonNode modell : objectMapper.readTree(antwort.body()).path("data")) {
                String id = modell.path("id").asString("");
                if (id.isBlank()) {
                    continue;
                }
                JsonNode faehig = modell.path("capabilities");
                boolean chat = faehig.path("completion_chat").asBoolean(true) && !istSondermodell(id);
                Boolean bilder = faehig.has("vision") ? faehig.path("vision").asBoolean(false) : null;
                List<String> aliase = new ArrayList<>();
                for (JsonNode alias : modell.path("aliases")) {
                    aliase.add(alias.asString(""));
                }
                modelle.add(new KiModellInfo(id, modell.path("name").asString(id), bilder, chat, aliase));
            }
        } catch (JacksonException e) {
            throw new KiAnbieterFehler("Die Modellliste von " + NAME + " war nicht lesbar.", false, e);
        }
        return modelle;
    }

    /** Texterkennung, Vektoren, Moderation und Sprache taugen nicht für Auswertungen im Chat. */
    private static boolean istSondermodell(String modell) {
        String klein = modell.toLowerCase(Locale.ROOT);
        return klein.contains("ocr") || klein.contains("embed") || klein.contains("moderation")
                || klein.contains("transcribe") || klein.contains("tts");
    }

    /** Prüft zusätzlich das Modell der Texterkennung, das PDFs liest. */
    @Override
    public TestResult teste(String schluessel, Collection<String> modelle) {
        List<String> zuPruefen = new ArrayList<>(modelle);
        zuPruefen.add(ocrModell);
        return KiAnbieter.super.teste(schluessel, zuPruefen);
    }
}
