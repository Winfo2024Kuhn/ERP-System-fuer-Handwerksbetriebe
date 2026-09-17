package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.dto.SpracheingabeErgebnis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

/**
 * Wandelt eine Sprachaufnahme aus der mobilen Zeiterfassung per Gemini in
 * Text um. Ein Handwerker diktiert damit unterwegs auf der Baustelle einen
 * Bautagebuch-Eintrag, oft in lauter Umgebung.
 *
 * <p><b>Datenschutz.</b> Es handelt sich um Sprachaufnahmen von Mitarbeitern
 * im Arbeitsverhaeltnis, nicht um oeffentliche oder betriebliche Dokumente.
 * Audio und Transkript leben ausschliesslich im Arbeitsspeicher: die
 * Aufnahme wird nie auf Platte geschrieben, nicht zwischengespeichert und in
 * keiner Datenbank-Spalte abgelegt. Nach dem Gemini-Aufruf werden beide
 * verworfen. Weder die Audio-Bytes noch der zurueckgegebene Text duerfen
 * jemals in ein Log gelangen, auch nicht gekuerzt und auch nicht im
 * Fehlerfall. Log-Zeilen dieser Klasse enthalten ausschliesslich technische
 * Metadaten (Bytes, Inhaltstyp, HTTP-Status).
 */
@Slf4j
@Service
public class SpracheingabeService {

    static final String SYSTEM_ANWEISUNG = """
            Du bekommst eine Sprachaufnahme von einem Handwerker einer Bauschlosserei.
            Er diktiert einen Eintrag, oft auf einer lauten Baustelle.

            Deine einzige Aufgabe ist, das Gesagte sauber aufzuschreiben.

            Gib ausschliesslich den Text zurueck, ohne Vorwort und ohne Kommentar.
            Schreibe nur, was gesagt wurde. Erfinde nichts dazu.
            Lass Fuellwoerter weg, also aeh, aehm, halt, sozusagen.
            Korrigiert sich der Sprecher selbst, schreibe nur die korrigierte Fassung.
            Setze Satzzeichen und Gross- und Kleinschreibung nach Sinn.
            Ordne den Inhalt nicht um und fasse ihn nicht zusammen.

            Das Ergebnis ist reiner Text.
            Keine Sternchen, keine Rauten, keine HTML-Tags, keine Markdown-Syntax.
            Absaetze trennst du durch eine Leerzeile.
            Aufzaehlungen schreibst du mit einem fuehrenden Bindestrich.

            Der Sprecher arbeitet im Metallbau. Rechne mit Begriffen wie
            Feuerverzinkung, Pulverbeschichtung, VSG, ESG, Schwerlastanker,
            Klebeduebel, Konterlattung, Absturzsicherung.
            Profile schreibst du als HEB 200, IPE 160, Rohrprofil 40x40x3.
            Normen als DIN EN 1090, DIN 18008.

            Verstehst du eine Stelle akustisch nicht, schreibe dort [unverstaendlich].
            Rate nicht.
            """;

    static final int MAX_AUDIO_BYTES = 10 * 1024 * 1024;

    static final Set<String> ERLAUBTE_INHALTSTYPEN = Set.of(
            "audio/webm", "audio/ogg", "audio/mp4", "audio/m4a",
            "audio/aac", "audio/wav", "audio/mpeg");

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(90);
    private static final int LESE_PUFFER_BYTES = 8192;

    private final SystemSettingsService systemSettingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String modell;

    @Autowired
    public SpracheingabeService(SystemSettingsService systemSettingsService,
                                 ObjectMapper objectMapper,
                                 @Value("${ai.gemini.model.spracheingabe:gemini-flash-latest}") String modell) {
        this(systemSettingsService, objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(),
                modell);
    }

    /** Test-Konstruktor mit injizierbarem HttpClient. */
    SpracheingabeService(SystemSettingsService systemSettingsService,
                          ObjectMapper objectMapper,
                          HttpClient httpClient,
                          String modell) {
        this.systemSettingsService = systemSettingsService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.modell = modell;
    }

    /** Liest den Strom begrenzt in den Speicher, prueft, ruft Gemini, liefert den Text. */
    public SpracheingabeErgebnis transkribiere(InputStream audioStrom, String inhaltstyp) {
        String normalisiert = normalisiereInhaltstyp(inhaltstyp);
        if (!ERLAUBTE_INHALTSTYPEN.contains(normalisiert)) {
            throw new IllegalArgumentException("Dieses Audioformat wird nicht unterstuetzt.");
        }

        byte[] audio = leseHoechstens(audioStrom, MAX_AUDIO_BYTES);
        if (audio.length == 0) {
            throw new IllegalArgumentException("Die Aufnahme ist leer.");
        }

        String apiKey = systemSettingsService.getGeminiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Kein Gemini-Schluessel hinterlegt.");
        }

        return sendeUndLies(baueKoerper(audio, normalisiert), apiKey, audio.length, normalisiert);
    }

    /**
     * Sichtbar fuer den Test: baut den Gemini-Koerper. Genau ein
     * inlineData-Part, kein begleitender Text-Part - die Aufnahme ist der
     * gesamte fachliche Inhalt.
     */
    ObjectNode baueKoerper(byte[] audio, String inhaltstyp) {
        ObjectNode koerper = objectMapper.createObjectNode();

        ObjectNode systemInstruction = koerper.putObject("systemInstruction");
        systemInstruction.putArray("parts").addObject().put("text", SYSTEM_ANWEISUNG);

        ObjectNode runde = koerper.putArray("contents").addObject();
        runde.put("role", "user");
        ObjectNode inlineData = runde.putArray("parts").addObject().putObject("inlineData");
        inlineData.put("mimeType", inhaltstyp);
        inlineData.put("data", Base64.getEncoder().encodeToString(audio));

        ObjectNode konfiguration = koerper.putObject("generationConfig");
        konfiguration.put("temperature", 0.0);
        konfiguration.put("responseMimeType", "text/plain");

        return koerper;
    }

    private SpracheingabeErgebnis sendeUndLies(ObjectNode koerper, String apiKey, int audioBytes, String inhaltstyp) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + modell + ":generateContent?key=" + apiKey;

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(READ_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(koerper), StandardCharsets.UTF_8))
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Anfrage an die Spracherkennung konnte nicht gebaut werden.", e);
        }

        HttpResponse<String> antwort;
        try {
            antwort = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Die Spracherkennung ist nicht erreichbar.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Der Aufruf der Spracherkennung wurde unterbrochen.", e);
        }

        if (antwort.statusCode() >= 400) {
            // DSGVO: NIEMALS antwort.body() mitloggen - der Koerper ist hier das Transkript.
            log.warn("[Spracheingabe] Gemini HTTP {} ({} Bytes, {})", antwort.statusCode(), audioBytes, inhaltstyp);
            throw new IllegalStateException("Die Spracherkennung hat mit einem Fehler geantwortet.");
        }

        try {
            JsonNode wurzel = objectMapper.readTree(antwort.body());
            String text = wurzel.path("candidates").path(0).path("content")
                    .path("parts").path(0).path("text").asText("");
            return new SpracheingabeErgebnis(text.trim());
        } catch (IOException e) {
            throw new IllegalStateException("Die Antwort der Spracherkennung war nicht lesbar.", e);
        }
    }

    /**
     * Liest hoechstens {@code max} Bytes in einen {@link ByteArrayOutputStream}
     * und bricht ab, sobald max + 1 Bytes gelesen wurden. Puffert bewusst
     * NICHT erst alles und prueft danach die Groesse - sonst koennte ein
     * manipulierter Client beliebig viel Speicher belegen.
     */
    private byte[] leseHoechstens(InputStream eingabe, int max) {
        ByteArrayOutputStream puffer = new ByteArrayOutputStream();
        byte[] block = new byte[LESE_PUFFER_BYTES];
        try {
            int gelesen;
            while ((gelesen = eingabe.read(block)) != -1) {
                puffer.write(block, 0, gelesen);
                if (puffer.size() > max) {
                    throw new AufnahmeZuGross();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Die Aufnahme konnte nicht gelesen werden.", e);
        }
        return puffer.toByteArray();
    }

    /** Sichtbar fuer den Test: "audio/webm;codecs=opus" -> "audio/webm". */
    static String normalisiereInhaltstyp(String rohwert) {
        if (rohwert == null) {
            return "";
        }
        int trennzeichen = rohwert.indexOf(';');
        String ohneParameter = trennzeichen < 0 ? rohwert : rohwert.substring(0, trennzeichen);
        return ohneParameter.trim().toLowerCase(Locale.ROOT);
    }

    /** 413 statt 400: eigene Unterklasse, damit der Controller sie trennen kann. */
    public static class AufnahmeZuGross extends IllegalArgumentException {
        AufnahmeZuGross() {
            super("Die Aufnahme ist zu lang. Bitte kuerzer diktieren.");
        }
    }
}
