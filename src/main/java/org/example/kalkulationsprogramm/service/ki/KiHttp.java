package org.example.kalkulationsprogramm.service.ki;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Gemeinsamer HTTP-Teil der Anbieter: Senden, Fehler in {@link KiAnbieterFehler}
 * übersetzen, von Fehlerantworten nur die Fehlerart loggen. Hier steht nie ein Schlüssel im Log.
 */
@Slf4j
final class KiHttp {

    private static final ObjectMapper LESER = JsonMapper.builderWithJackson2Defaults().build();

    /** So viele Zeichen einer Fehlerantwort kommen höchstens ins Log. */
    static final int LOG_LAENGE = 200;

    private KiHttp() {
    }

    /**
     * Schickt die Anfrage. Eine Fehlerantwort (ab 400) wird zu einem
     * {@link KiAnbieterFehler}; 429 und 5xx gelten als vorübergehend.
     */
    static HttpResponse<String> sende(HttpClient client, HttpRequest anfrage, String anbieterName) {
        HttpResponse<String> antwort;
        try {
            antwort = client.send(anfrage, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new KiAnbieterFehler(anbieterName + " hat nicht rechtzeitig geantwortet.", true, e);
        } catch (IOException e) {
            throw new KiAnbieterFehler(anbieterName + " ist gerade nicht erreichbar.", true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KiAnbieterFehler("Der Aufruf bei " + anbieterName + " wurde unterbrochen.", false, e);
        }
        int status = antwort.statusCode();
        if (status >= 400) {
            log.warn("[KI {}] HTTP {} - {}", anbieterName, status, fehlerart(antwort.body()));
            throw new KiAnbieterFehler(meldungFuerStatus(anbieterName, status), status, istVoruebergehend(status));
        }
        return antwort;
    }

    /** Bei Überlastung (429) und Störungen beim Anbieter (5xx) lohnt ein zweiter Versuch. */
    static boolean istVoruebergehend(int status) {
        return status == 429 || (status >= 500 && status < 600);
    }

    static String meldungFuerStatus(String anbieterName, int status) {
        if (status == 401 || status == 403) {
            return anbieterName + " nimmt den Schlüssel nicht an (HTTP " + status + ").";
        }
        if (status == 429) {
            return anbieterName + " ist gerade ausgelastet oder das Kontingent ist aufgebraucht (HTTP 429).";
        }
        if (status >= 500) {
            return anbieterName + " hat gerade eine Störung (HTTP " + status + ").";
        }
        return anbieterName + " hat die Anfrage abgelehnt (HTTP " + status + ").";
    }

    /** Fehlerantwort gekürzt fürs Log. Nur für Antworten ohne Anfrageinhalt (z. B. promptFeedback). */
    static String fuerLog(String antwort) {
        if (antwort == null) {
            return "";
        }
        return antwort.length() <= LOG_LAENGE ? antwort : antwort.substring(0, LOG_LAENGE) + "…";
    }

    /**
     * Fehlerart einer Fehlerantwort fürs Log – nie der Körper selbst: Anbieter wiederholen
     * darin Teile der Anfrage (Mistral bei 422 im Feld {@code input}), also E-Mail- oder
     * Rechnungstext (DSGVO). Gelesen werden nur Code, Status und Typ.
     */
    static String fehlerart(String antwort) {
        if (antwort == null || antwort.isBlank()) {
            return "ohne Inhalt";
        }
        try {
            JsonNode wurzel = LESER.readTree(antwort);
            JsonNode fehler = wurzel.path("error").isObject() ? wurzel.path("error") : wurzel;
            List<String> teile = new ArrayList<>();
            for (String feld : List.of("code", "status", "type")) {
                JsonNode wert = fehler.path(feld);
                if (wert.isValueNode() && !wert.asString("").isBlank()) {
                    teile.add(feld + "=" + kurz(wert.asString("")));
                }
            }
            for (JsonNode eintrag : wurzel.path("detail")) {
                teile.add("detail=" + kurz(eintrag.path("type").asString("?")) + "@" + kurz(eintrag.path("loc").toString()));
            }
            return teile.isEmpty() ? antwort.length() + " Zeichen" : String.join(", ", teile);
        } catch (JacksonException e) {
            return antwort.length() + " Zeichen, kein JSON";
        }
    }

    private static String kurz(String wert) {
        return wert.length() <= 60 ? wert : wert.substring(0, 60) + "…";
    }
}
