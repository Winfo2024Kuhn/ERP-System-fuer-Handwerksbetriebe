package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpRequest;

import org.junit.jupiter.api.Test;

/** Der Schlüssel reist im Header, nie in der URL. Nur Dummy-Werte. */
class GeminiAnfrageTest {

    private static final String SCHLUESSEL = "dummy-test-key";

    @Test
    void generateContentHatSchluesselNurImHeader() {
        HttpRequest anfrage = GeminiAnfrage.generateContent("gemini-test-modell", SCHLUESSEL)
                .POST(HttpRequest.BodyPublishers.noBody()).build();

        assertThat(anfrage.uri().toString()).isEqualTo(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-test-modell:generateContent");
        assertThat(anfrage.headers().firstValue("x-goog-api-key")).hasValue(SCHLUESSEL);
        assertThat(anfrage.headers().allValues("Content-Type")).containsExactly("application/json");
    }

    @Test
    void modellMethodeUndModellListeOhneSchluesselInDerUrl() {
        HttpRequest einbetten = GeminiAnfrage.modellMethode("gemini-embedding-test", ":batchEmbedContents", SCHLUESSEL)
                .POST(HttpRequest.BodyPublishers.noBody()).build();
        HttpRequest liste = GeminiAnfrage.modellListe(SCHLUESSEL).GET().build();

        assertThat(einbetten.uri().toString()).endsWith("/models/gemini-embedding-test:batchEmbedContents");
        assertThat(liste.uri().toString()).isEqualTo("https://generativelanguage.googleapis.com/v1beta/models");
        assertThat(einbetten.uri().toString() + liste.uri()).doesNotContain(SCHLUESSEL);
        assertThat(liste.headers().firstValue(GeminiAnfrage.SCHLUESSEL_HEADER)).hasValue(SCHLUESSEL);
    }

    @Test
    void fehlerantwortWirdFuersLogGekuerzt() {
        String lang = "x".repeat(GeminiAnfrage.LOG_LAENGE + 50);

        assertThat(GeminiAnfrage.fuerLog(null)).isEmpty();
        assertThat(GeminiAnfrage.fuerLog("{\"error\":403}")).isEqualTo("{\"error\":403}");
        assertThat(GeminiAnfrage.fuerLog("x".repeat(GeminiAnfrage.LOG_LAENGE)))
                .hasSize(GeminiAnfrage.LOG_LAENGE);
        assertThat(GeminiAnfrage.fuerLog(lang)).hasSize(GeminiAnfrage.LOG_LAENGE + 1).endsWith("…");
    }
}
