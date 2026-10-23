package org.example.kalkulationsprogramm.service.kihilfe;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.service.GeminiAnfrage;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Embeddings ueber die Gemini-API ({@value RagIndexer#MODELL}, {@value RagIndexer#DIMENSIONEN}
 * Dimensionen). Abschnitte der Wissensbasis als RETRIEVAL_DOCUMENT, Fragen als RETRIEVAL_QUERY.
 * <p>
 * Der Schluessel geht im Header mit, nicht in der URL - so taucht er in keinem Log auf.
 * Ueberlastung (429/5xx) und Zeitueberschreitungen werden wiederholt: beim Aufbau der
 * Wissensbasis geduldig, bei einer Frage nur einmal kurz - der Nutzer wartet im Chat.
 */
@Slf4j
public class GeminiEinbetter implements RagIndexer.Einbetter {

    private static final int MAX_TEXT = 8000;
    private static final Set<Integer> WIEDERHOLBAR = Set.of(429, 500, 502, 503, 504);

    /** Wie oft und wie geduldig wiederholt wird. */
    private record Wiederholung(int versuche, boolean geduldig) {}

    /** Wissensbasis (Image-Bau): wartet bei 429 das Minutenfenster des Gratis-Kontingents ab. */
    private static final Wiederholung INDEX = new Wiederholung(5, true);
    /** Frage im Chat: ein zweiter Versuch nach einer Sekunde, dann Rueckfall ohne RAG. */
    private static final Wiederholung FRAGE = new Wiederholung(2, false);

    /** Pause zwischen zwei Versuchen; in Tests ohne echtes Warten. */
    @FunctionalInterface
    interface Pause {
        void warten(Duration dauer) throws InterruptedException;
    }

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Supplier<String> apiKey;
    private final Pause pause;

    public GeminiEinbetter(ObjectMapper objectMapper, Supplier<String> apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build(), objectMapper, apiKey,
                dauer -> Thread.sleep(dauer.toMillis()));
    }

    GeminiEinbetter(HttpClient httpClient, ObjectMapper objectMapper, Supplier<String> apiKey, Pause pause) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.pause = pause;
    }

    @Override
    public List<float[]> einbetten(List<String> texte) throws IOException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode requests = body.putArray("requests");
        for (String text : texte) {
            ObjectNode request = anfrageKoerper(text, "RETRIEVAL_DOCUMENT");
            request.put("model", "models/" + RagIndexer.MODELL);
            requests.add(request);
        }
        JsonNode antwort = senden(":batchEmbedContents", body, Duration.ofSeconds(120), INDEX);
        List<float[]> vektoren = new ArrayList<>();
        for (JsonNode embedding : antwort.path("embeddings")) {
            vektoren.add(vektor(embedding.path("values")));
        }
        return vektoren;
    }

    /** Vektor fuer eine Frage an die KI-Hilfe. */
    public float[] anfrageEinbetten(String text) throws IOException, InterruptedException {
        JsonNode antwort = senden(":embedContent", anfrageKoerper(text, "RETRIEVAL_QUERY"), Duration.ofSeconds(15), FRAGE);
        return vektor(antwort.path("embedding").path("values"));
    }

    private ObjectNode anfrageKoerper(String text, String aufgabe) {
        ObjectNode request = objectMapper.createObjectNode();
        request.putObject("content").putArray("parts").addObject()
                .put("text", text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT));
        request.put("taskType", aufgabe);
        request.put("outputDimensionality", RagIndexer.DIMENSIONEN);
        return request;
    }

    private JsonNode senden(String methode, ObjectNode body, Duration zeitlimit, Wiederholung regel)
            throws IOException, InterruptedException {
        String key = apiKey.get();
        if (key == null || key.isBlank()) {
            throw new IOException("Gemini API Key fehlt");
        }
        HttpRequest request = GeminiAnfrage.modellMethode(RagIndexer.MODELL, methode, key)
                .timeout(zeitlimit)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        for (int versuch = 1; ; versuch++) {
            String grund;
            Duration warten = regel.geduldig() ? Duration.ofSeconds(2L << (versuch - 1)) : Duration.ofSeconds(1); // 2, 4, 8, 16 s
            try {
                HttpResponse<String> antwort = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (antwort.statusCode() == 200) {
                    return objectMapper.readTree(antwort.body());
                }
                if (!WIEDERHOLBAR.contains(antwort.statusCode())) {
                    throw new IOException("Gemini Embedding Fehler " + antwort.statusCode() + ": " + GeminiAnfrage.fuerLog(antwort.body()));
                }
                grund = "HTTP " + antwort.statusCode();
                if (antwort.statusCode() == 429 && regel.geduldig()) {
                    // Kontingent je Minute erschoepft (Gratis-Schluessel): erst das Minutenfenster abwarten
                    warten = Duration.ofSeconds(20L * versuch);
                }
            } catch (HttpTimeoutException e) {
                grund = "Zeitueberschreitung";
            }
            if (versuch >= regel.versuche()) {
                throw new IOException("Gemini Embedding: " + grund + " nach " + regel.versuche() + " Versuchen");
            }
            log.warn("Gemini Embedding: {} (Versuch {}/{}), neuer Versuch in {} s",
                    grund, versuch, regel.versuche(), warten.toSeconds());
            pause.warten(warten);
        }
    }

    private static float[] vektor(JsonNode werte) throws IOException {
        if (werte.size() < RagIndexer.DIMENSIONEN) {
            throw new IOException("Gemini lieferte einen Vektor mit " + werte.size() + " statt "
                    + RagIndexer.DIMENSIONEN + " Werten");
        }
        float[] vektor = new float[RagIndexer.DIMENSIONEN];
        for (int i = 0; i < vektor.length; i++) {
            vektor[i] = (float) werte.get(i).asDouble(0.0);
        }
        return vektor;
    }
}
