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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mistral AI (Frankreich), Verarbeitung nach Angabe von Mistral in der EU.
 *
 * <p>Text und Bilder gehen direkt an den Chat. PDFs liest zuerst die
 * Texterkennung von Mistral ({@code /v1/ocr}); deren Text bekommt der Chat.
 * Ton geht als {@code input_audio} an ein Sprachmodell (Voxtral,
 * {@code ai.mistral.model.sprache}), weil die Chat-Modelle der Stufen keinen Ton
 * verstehen. Werkzeuge laufen über {@code tools}/{@code tool_calls} wie bei OpenAI.
 * Eine Internet-Suche bietet der Chat von Mistral nicht: Der Wunsch danach
 * ({@link KiAnfrage#websuche()}) entfällt still, die Einstellungsseite sagt es dazu.
 */
@Slf4j
@Component
public class MistralKiAnbieter implements KiAnbieter {

    static final String NAME = "Mistral";
    static final String BASIS_URL = "https://api.mistral.ai/v1";
    private static final Duration LISTE_ZEITLIMIT = Duration.ofSeconds(10);
    /** Bildformate, die Mistral annimmt. */
    static final Set<String> BILDFORMATE = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    /**
     * Aufnahmeformate, die Voxtral laut Mistral-Doku liest: WAV und MP3 für den Chat belegt,
     * FLAC, OGG und WebM laut „Known limitations“ der Transkription. MP4/M4A/AAC (iPhone) sind
     * nicht belegt und werden deshalb mit klarer Meldung abgelehnt, statt still zu scheitern.
     */
    static final Set<String> TONFORMATE = Set.of("audio/wav", "audio/x-wav", "audio/wave", "audio/mpeg",
            "audio/mp3", "audio/flac", "audio/ogg", "audio/webm");

    /**
     * Obergrenze für {@code max_tokens}. Laut Mistral-API-Doku (Chat Completion, Feld {@code max_tokens},
     * https://docs.mistral.ai/api/#tag/chat) dürfen Eingabe plus {@code max_tokens} die Kontextlänge des
     * Modells nicht überschreiten, sonst lehnt Mistral die Anfrage mit HTTP 400 ab. Die Dienste setzen
     * ihr Limit für Gemini (Dokument-Analyse bis 65.536); bei langen PDFs bringt der OCR-Text dazu
     * leicht zehntausende Token mit. 16.384 reicht für jede Antwort im Programm (auch große
     * Rechnungen als JSON) und lässt dem Eingabetext genug Platz. Kleinere Werte bleiben unverändert.
     */
    static final int MAX_AUSGABE = 16_384;

    /** So lange gilt ein Texterkennungs-Ergebnis; Dienste schicken dieselbe PDF über mehrere Runden und Versuche. */
    static final Duration TEXTERKENNUNG_GUELTIG = Duration.ofMinutes(15);
    /** Höchstens so viele erkannte Dokumente bleiben im Speicher. */
    static final int TEXTERKENNUNG_MAX = 32;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, GemerkterText> texterkennung = new ConcurrentHashMap<>();
    private final String ocrModell;
    private final String sprachModell;
    private final Map<KiModellStufe, String> standardModelle;

    @Autowired
    public MistralKiAnbieter(ObjectMapper objectMapper,
                             @Value("${ai.mistral.model.schnell:mistral-small-latest}") String schnell,
                             @Value("${ai.mistral.model.standard:mistral-large-latest}") String standard,
                             @Value("${ai.mistral.model.gruendlich:mistral-large-latest}") String gruendlich,
                             @Value("${ai.mistral.model.texterkennung:mistral-ocr-latest}") String ocrModell,
                             @Value("${ai.mistral.model.sprache:voxtral-small-latest}") String sprachModell) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(),
                schnell, standard, gruendlich, ocrModell, sprachModell);
    }

    /** Test-Konstruktor mit injizierbarem HttpClient. */
    MistralKiAnbieter(ObjectMapper objectMapper, HttpClient httpClient,
                      String schnell, String standard, String gruendlich, String ocrModell, String sprachModell) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.ocrModell = ocrModell;
        this.sprachModell = sprachModell;
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
        return EnumSet.of(KiFaehigkeit.TEXT, KiFaehigkeit.BILD, KiFaehigkeit.PDF, KiFaehigkeit.AUDIO,
                KiFaehigkeit.WERKZEUGE);
    }

    /** Bilder nur in den belegten Formaten, Ton nur in den von Voxtral gelesenen; PDFs und Klartext immer. */
    @Override
    public boolean liest(KiTeil.Datei datei) {
        if (datei.istBild()) {
            return BILDFORMATE.contains(datei.mimeType());
        }
        if (datei.istAudio()) {
            return TONFORMATE.contains(datei.mimeType());
        }
        return datei.istPdf() || datei.istKlartext();
    }

    /** Ton versteht nur das Sprachmodell (Voxtral); es kann auch Text und Werkzeuge. */
    @Override
    public Optional<String> modellFuer(Set<KiFaehigkeit> gebraucht) {
        return gebraucht.contains(KiFaehigkeit.AUDIO) ? Optional.of(sprachModell) : Optional.empty();
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
            switch (nachricht.rolle()) {
                case KI -> haengeKiZugAn(messages, nachricht);
                case WERKZEUG -> haengeWerkzeugErgebnisseAn(messages, nachricht);
                case NUTZER -> {
                    ArrayNode inhalt = messages.addObject().put("role", "user").putArray("content");
                    for (KiTeil teil : nachricht.teile()) {
                        if (teil instanceof KiTeil.Text text) {
                            inhalt.addObject().put("type", "text").put("text", text.text());
                        } else if (teil instanceof KiTeil.Datei datei) {
                            haengeDateiAn(inhalt, datei, pdfLeser);
                        }
                    }
                }
            }
        }
        haengeWerkzeugeAn(koerper, anfrage);
        if (anfrage.temperatur() != null) {
            koerper.put("temperature", anfrage.temperatur());
        }
        if (anfrage.maxAusgabe() != null) {
            koerper.put("max_tokens", Math.min(anfrage.maxAusgabe(), MAX_AUSGABE));
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
            throw new KiInhaltNichtLesbar(NAME + " kann dieses Bildformat nicht lesen (" + datei.mimeType()
                    + "). Bitte als JPG, PNG, WebP oder GIF speichern.");
        }
        if (datei.istBild()) {
            inhalt.addObject().put("type", "image_url").put("image_url",
                    "data:" + datei.mimeType() + ";base64," + Base64.getEncoder().encodeToString(datei.daten()));
        } else if (datei.istAudio()) {
            // Laut Mistral-Doku („Audio in chat“): die Datei base64-kodiert, ohne Formatangabe.
            if (!TONFORMATE.contains(datei.mimeType())) {
                throw new KiInhaltNichtLesbar(NAME + " kann dieses Aufnahmeformat nicht lesen (" + datei.mimeType()
                        + ").");
            }
            inhalt.addObject().put("type", "input_audio")
                    .put("input_audio", Base64.getEncoder().encodeToString(datei.daten()));
        } else if (datei.istPdf()) {
            inhalt.addObject().put("type", "text").put("text",
                    "Inhalt des PDF-Dokuments (per Texterkennung gelesen):\n\n" + pdfLeser.lies(datei));
        } else if (datei.istKlartext()) {
            inhalt.addObject().put("type", "text").put("text",
                    "Inhalt der Datei (" + datei.mimeType() + "):\n\n"
                            + new String(datei.daten(), StandardCharsets.UTF_8));
        } else {
            throw new KiInhaltNichtLesbar(NAME + " kann diese Dateiart in diesem Programm noch nicht lesen ("
                    + datei.mimeType() + ").");
        }
    }

    /** Zug der KI: Text und Werkzeug-Aufrufe, die Argumente als JSON-Text. */
    private void haengeKiZugAn(ArrayNode messages, KiNachricht nachricht) {
        ObjectNode eintrag = messages.addObject().put("role", "assistant");
        String text = nachricht.text();
        List<KiTeil.WerkzeugAufruf> aufrufe = nachricht.werkzeugAufrufe();
        if (!text.isEmpty() || aufrufe.isEmpty()) {
            eintrag.put("content", text);
        }
        if (!aufrufe.isEmpty()) {
            ArrayNode calls = eintrag.putArray("tool_calls");
            for (KiTeil.WerkzeugAufruf aufruf : aufrufe) {
                ObjectNode call = calls.addObject().put("id", aufruf.id()).put("type", "function");
                call.putObject("function").put("name", aufruf.name())
                        .put("arguments", alsText(aufruf.argumente()));
            }
        }
    }

    /** Jedes Ergebnis als eigene Nachricht mit der Rolle „tool“ und der Kennung seines Aufrufs. */
    private void haengeWerkzeugErgebnisseAn(ArrayNode messages, KiNachricht nachricht) {
        for (KiTeil teil : nachricht.teile()) {
            if (teil instanceof KiTeil.WerkzeugErgebnis ergebnis) {
                messages.addObject().put("role", "tool")
                        .put("tool_call_id", ergebnis.id())
                        .put("name", ergebnis.name())
                        .put("content", alsText(ergebnis.ergebnis()));
            }
        }
    }

    /** Eigene Werkzeuge; die Internet-Suche kann Mistral im Chat nicht – der Wunsch entfällt. */
    private void haengeWerkzeugeAn(ObjectNode koerper, KiAnfrage anfrage) {
        if (anfrage.werkzeuge().isEmpty()) {
            return;
        }
        ArrayNode tools = koerper.putArray("tools");
        for (KiWerkzeug werkzeug : anfrage.werkzeuge()) {
            ObjectNode funktion = tools.addObject().put("type", "function").putObject("function");
            funktion.put("name", werkzeug.name());
            funktion.put("description", werkzeug.beschreibung());
            funktion.set("parameters", alsJsonSchema(werkzeug.parameter()));
        }
        koerper.put("tool_choice", anfrage.werkzeugModus() == KiAnfrage.WerkzeugModus.KEINE ? "none" : "auto");
    }

    private String alsText(JsonNode json) {
        try {
            return objectMapper.writeValueAsString(json);
        } catch (JacksonException e) {
            throw new KiAnbieterFehler("Die Anfrage an " + NAME + " konnte nicht gebaut werden.", false, e);
        }
    }

    /**
     * Liest eine PDF-Datei mit der Texterkennung von Mistral; Ergebnis als Markdown je Seite.
     * Dasselbe Dokument wird nur einmal gelesen ({@link #TEXTERKENNUNG_GUELTIG}): Die Dienste
     * schicken es über mehrere Werkzeug-Runden und Wiederholungsversuche immer wieder mit.
     */
    private String leseText(KiTeil.Datei datei, String schluessel, Duration zeitlimit) {
        String kennung = fingerabdruck(datei.daten()) + "|" + ocrModell;
        Instant jetzt = Instant.now();
        GemerkterText gemerkt = texterkennung.get(kennung);
        if (gemerkt != null && gemerkt.gueltigUm(jetzt)) {
            return gemerkt.text();
        }
        String text = erkenneText(datei, schluessel, zeitlimit);
        if (!text.isBlank()) {
            // Leer heißt meist: Störung oder gescannt ohne Text – ein neuer Versuch soll neu lesen dürfen.
            merkeText(kennung, text, jetzt);
        }
        return text;
    }

    private record GemerkterText(String text, Instant geladen) {
        boolean gueltigUm(Instant jetzt) {
            return geladen.plus(TEXTERKENNUNG_GUELTIG).isAfter(jetzt);
        }
    }

    private void merkeText(String kennung, String text, Instant jetzt) {
        texterkennung.values().removeIf(eintrag -> !eintrag.gueltigUm(jetzt));
        while (texterkennung.size() >= TEXTERKENNUNG_MAX) {
            texterkennung.entrySet().stream()
                    .min(Map.Entry.comparingByValue(java.util.Comparator.comparing(GemerkterText::geladen)))
                    .ifPresent(aeltester -> texterkennung.remove(aeltester.getKey()));
        }
        texterkennung.put(kennung, new GemerkterText(text, jetzt));
    }

    /** SHA-256 der Datei als Schlüssel für den Zwischenspeicher – der Inhalt selbst bleibt draußen. */
    private static String fingerabdruck(byte[] daten) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(daten));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 fehlt in dieser Java-Umgebung.", e);
        }
    }

    private String erkenneText(KiTeil.Datei datei, String schluessel, Duration zeitlimit) {
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
        List<KiTeil.WerkzeugAufruf> aufrufe = new ArrayList<>();
        boolean fehlerhaft = false;
        for (JsonNode call : wahl.path("message").path("tool_calls")) {
            JsonNode funktion = call.path("function");
            JsonNode argumente = leseArgumente(funktion.path("arguments"));
            if (argumente == null) {
                // Wie Gemini bei MALFORMED_FUNCTION_CALL: Ein kaputter Aufruf wird nicht ausgeführt (mit
                // leeren Argumenten liefe er womöglich falsch), sondern nur gemeldet – der Dienst fragt nach.
                fehlerhaft = true;
                continue;
            }
            aufrufe.add(new KiTeil.WerkzeugAufruf(call.path("id").asString(""),
                    funktion.path("name").asString(""), argumente));
        }
        return new KiErgebnis(text, abgeschnitten, KiAnbieterTyp.MISTRAL, modell, aufrufe, fehlerhaft,
                List.of(), List.of(), null);
    }

    /** Argumente kommen als JSON-Text (manchmal schon als Objekt); {@code null}, wenn unlesbar. */
    private JsonNode leseArgumente(JsonNode argumente) {
        if (argumente.isObject()) {
            return argumente;
        }
        String text = argumente.asString("");
        if (text.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode gelesen = objectMapper.readTree(text);
            return gelesen.isObject() ? gelesen : null;
        } catch (JacksonException e) {
            return null;
        }
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
            if ("nullable".equals(name) || "propertyOrdering".equals(name)
                    || ("format".equals(name) && "enum".equals(wert.asString("")))) {
                // Gemini-Eigenheiten; „format: enum“ ergibt sich in JSON Schema schon aus „enum“.
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
                Boolean ton = verstehtTon(id, faehig);
                List<String> aliase = new ArrayList<>();
                for (JsonNode alias : modell.path("aliases")) {
                    aliase.add(alias.asString(""));
                }
                modelle.add(new KiModellInfo(id, modell.path("name").asString(id), bilder, ton, chat, aliase));
            }
        } catch (JacksonException e) {
            throw new KiAnbieterFehler("Die Modellliste von " + NAME + " war nicht lesbar.", false, e);
        }
        return modelle;
    }

    /**
     * Versteht das Modell Ton im Chat ({@code input_audio})? Die Modellliste von Mistral meldet das in
     * {@code capabilities.audio} (so im offiziellen SDK, {@code ModelCapabilities}). Fehlt das Feld
     * (ältere Antworten), gilt der Name: Voxtral ist das Sprachmodell von Mistral; bei allen anderen
     * bleibt es offen ({@code null}), statt „kein Ton“ zu behaupten.
     */
    private static Boolean verstehtTon(String modell, JsonNode faehig) {
        if (faehig.has("audio")) {
            return faehig.path("audio").asBoolean(false);
        }
        return modell.toLowerCase(Locale.ROOT).contains("voxtral") ? Boolean.TRUE : null;
    }

    /** Texterkennung, Vektoren, Moderation und Sprache taugen nicht für Auswertungen im Chat. */
    private static boolean istSondermodell(String modell) {
        String klein = modell.toLowerCase(Locale.ROOT);
        return klein.contains("ocr") || klein.contains("embed") || klein.contains("moderation")
                || klein.contains("transcribe") || klein.contains("tts");
    }

    /** Aufnahmen vom iPhone (MP4/AAC) liest Voxtral nach heutigem Stand der Doku nicht ({@link #TONFORMATE}). */
    @Override
    public Optional<String> einschraenkung(KiFunktion funktion) {
        return funktion.braucht().contains(KiFaehigkeit.AUDIO)
                ? Optional.of("Aufnahmen vom iPhone (MP4) kann " + NAME + " nicht lesen – sie laufen nur, "
                        + "wenn ein anderer eingerichteter Anbieter einspringen darf.")
                : Optional.empty();
    }

    /** Prüft zusätzlich die Modelle der Texterkennung (PDFs) und der Sprache. */
    @Override
    public TestResult teste(String schluessel, Collection<String> modelle) {
        List<String> zuPruefen = new ArrayList<>(modelle);
        zuPruefen.add(ocrModell);
        zuPruefen.add(sprachModell);
        return KiAnbieter.super.teste(schluessel, zuPruefen);
    }
}
