package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Bausteine fuer Gespraeche mit Werkzeug-Aufrufen (Gemini "Function Calling"), fuer alle KI-Dienste gleich.
 * <p>
 * Gemini 3 haengt an Teile seiner Antwort Gedanken-Signaturen ({@code thoughtSignature}) und bei der
 * Google-Suche eigene Aufruf-Teile ({@code toolCall}/{@code toolResponse}). Beides muss im naechsten
 * Zug unveraendert zurueck - deshalb wird der Modell-Zug immer als Ganzes kopiert, nie neu gebaut.
 */
public final class GeminiVerlauf {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private GeminiVerlauf() {
    }

    /** Erster Antwortkandidat der Gemini-Antwort (leerer Knoten, wenn es keinen gibt). */
    public static JsonNode kandidat(JsonNode antwort) {
        return antwort.path("candidates").path(0);
    }

    /** Alle Werkzeug-Aufrufe ({@code functionCall}) eines Kandidaten - Gemini kann mehrere zugleich stellen. */
    public static List<JsonNode> funktionsaufrufe(JsonNode kandidat) {
        List<JsonNode> aufrufe = new ArrayList<>();
        for (JsonNode teil : kandidat.path("content").path("parts")) {
            JsonNode aufruf = teil.get("functionCall");
            if (aufruf != null && aufruf.isObject()) aufrufe.add(aufruf);
        }
        return aufrufe;
    }

    /** Der sichtbare Antworttext; Gedanken-Teile ({@code thought: true}) gehoeren nicht dazu. */
    public static String text(JsonNode kandidat) {
        StringBuilder text = new StringBuilder();
        for (JsonNode teil : kandidat.path("content").path("parts")) {
            if (teil.path("thought").asBoolean(false)) continue;
            String stueck = teil.path("text").asString("");
            if (stueck.isEmpty()) continue;
            if (!text.isEmpty()) text.append('\n');
            text.append(stueck);
        }
        return text.toString();
    }

    /** Der Zug des Modells fuer den Verlauf: alle Teile unveraendert (Signaturen, Suchaufrufe). */
    public static ObjectNode modellZug(JsonNode kandidat) {
        ObjectNode zug = JSON.objectNode();
        zug.put("role", "model");
        JsonNode teile = kandidat.path("content").path("parts");
        if (teile.isArray()) {
            zug.set("parts", teile.deepCopy());
        } else {
            zug.putArray("parts");
        }
        return zug;
    }

    /**
     * Antwort auf einen Werkzeug-Aufruf. Die {@code id} des Aufrufs geht mit zurueck - so ordnet
     * Gemini bei mehreren gleichzeitigen Aufrufen jede Antwort ihrem Aufruf zu.
     */
    public static ObjectNode antwortTeil(JsonNode aufruf, JsonNode ergebnis) {
        ObjectNode teil = JSON.objectNode();
        ObjectNode antwort = teil.putObject("functionResponse");
        String id = aufruf.path("id").asString("");
        if (!id.isEmpty()) antwort.put("id", id);
        antwort.put("name", aufruf.path("name").asString(""));
        antwort.set("response", ergebnis);
        return teil;
    }

    /** Zug mit den Werkzeug-Antworten; Gemini erwartet ihn mit der Rolle "user". */
    public static ObjectNode antwortZug(List<ObjectNode> antwortTeile) {
        ObjectNode zug = JSON.objectNode();
        zug.put("role", "user");
        zug.putArray("parts").addAll(antwortTeile);
        return zug;
    }
}
