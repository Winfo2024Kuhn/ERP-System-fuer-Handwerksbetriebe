package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiVerlaufTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode json(String text) throws Exception {
        return objectMapper.readTree(text);
    }

    @Test
    @DisplayName("Werkzeug-Aufrufe: alle, auch mehrere gleichzeitig")
    void funktionsaufrufe() throws Exception {
        JsonNode antwort = json("""
                {"candidates":[{"content":{"role":"model","parts":[
                  {"text":"Ich schaue nach."},
                  {"functionCall":{"id":"a1","name":"suchen","args":{"q":"Storno"}},"thoughtSignature":"sig-1"},
                  {"functionCall":{"id":"a2","name":"lesen","args":{}}},
                  {"functionCall":"kaputt"}
                ]}}]}""");

        List<JsonNode> aufrufe = GeminiVerlauf.funktionsaufrufe(GeminiVerlauf.kandidat(antwort));

        assertThat(aufrufe).extracting(a -> a.path("id").asText()).containsExactly("a1", "a2");
        assertThat(GeminiVerlauf.funktionsaufrufe(GeminiVerlauf.kandidat(json("{}")))).isEmpty();
    }

    @Test
    @DisplayName("Text: sichtbare Teile verbunden, Gedanken weggelassen")
    void text() throws Exception {
        JsonNode kandidat = GeminiVerlauf.kandidat(json("""
                {"candidates":[{"content":{"parts":[
                  {"text":"geheimer Gedanke","thought":true},
                  {"text":"Erster Teil"},
                  {"functionCall":{"name":"x"}},
                  {"text":"Zweiter Teil"}
                ]}}]}"""));

        assertThat(GeminiVerlauf.text(kandidat)).isEqualTo("Erster Teil\nZweiter Teil");
        assertThat(GeminiVerlauf.text(GeminiVerlauf.kandidat(json("{\"candidates\":[]}")))).isEmpty();
    }

    @Test
    @DisplayName("Modell-Zug: alle Teile unveraendert, auch Signaturen und Suchaufrufe")
    void modellZug() throws Exception {
        JsonNode kandidat = GeminiVerlauf.kandidat(json("""
                {"candidates":[{"content":{"role":"model","parts":[
                  {"toolCall":{"id":"s1","toolType":"GOOGLE_SEARCH_WEB"}},
                  {"functionCall":{"id":"a1","name":"suchen"},"thoughtSignature":"sig-1"}
                ]}}]}"""));

        ObjectNode zug = GeminiVerlauf.modellZug(kandidat);

        assertThat(zug.path("role").asText()).isEqualTo("model");
        assertThat(zug.path("parts")).isEqualTo(kandidat.path("content").path("parts"));
        assertThat(zug.path("parts").path(1).path("thoughtSignature").asText()).isEqualTo("sig-1");
        assertThat(GeminiVerlauf.modellZug(json("{}")).path("parts").isArray()).isTrue();
    }

    @Test
    @DisplayName("Antwort auf Aufrufe: id und Name zurueck, Rolle user")
    void antwortZug() throws Exception {
        ObjectNode ergebnis = objectMapper.createObjectNode().put("hinweis", "ok");

        ObjectNode mitId = GeminiVerlauf.antwortTeil(json("{\"id\":\"a1\",\"name\":\"suchen\"}"), ergebnis);
        ObjectNode ohneId = GeminiVerlauf.antwortTeil(json("{\"name\":\"lesen\"}"), ergebnis);
        ObjectNode zug = GeminiVerlauf.antwortZug(List.of(mitId, ohneId));

        assertThat(zug.path("role").asText()).isEqualTo("user");
        assertThat(zug.path("parts").path(0).path("functionResponse").path("id").asText()).isEqualTo("a1");
        assertThat(zug.path("parts").path(0).path("functionResponse").path("name").asText()).isEqualTo("suchen");
        assertThat(zug.path("parts").path(0).path("functionResponse").path("response").path("hinweis").asText())
                .isEqualTo("ok");
        assertThat(zug.path("parts").path(1).path("functionResponse").has("id")).isFalse();
    }
}
