package org.example.kalkulationsprogramm.service.kihilfe;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeminiEinbetterTest {

    private static final String KEY = "test-schluessel";

    private final ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults().build();
    private final HttpClient httpClient = mock(HttpClient.class);
    private final List<Duration> pausen = new ArrayList<>();
    private String key = KEY;
    private GeminiEinbetter einbetter;

    @BeforeEach
    void setUp() {
        einbetter = new GeminiEinbetter(httpClient, objectMapper, () -> key, pausen::add);
    }

    private static String werte(float erster) {
        StringBuilder sb = new StringBuilder("[").append(erster);
        for (int i = 1; i < RagIndexer.DIMENSIONEN; i++) sb.append(",0.5");
        return sb.append("]").toString();
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> antwort(int status, String body) {
        HttpResponse<String> antwort = mock(HttpResponse.class);
        when(antwort.statusCode()).thenReturn(status);
        when(antwort.body()).thenReturn(body);
        return antwort;
    }

    private HttpRequest gesendeteAnfrage() throws Exception {
        ArgumentCaptor<HttpRequest> anfrage = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(anfrage.capture(), any());
        return anfrage.getValue();
    }

    /** Liest den JSON-Koerper einer HttpRequest. */
    private static String koerper(HttpRequest anfrage) {
        StringBuilder text = new StringBuilder();
        anfrage.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                text.append(StandardCharsets.UTF_8.decode(item));
            }

            @Override
            public void onError(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }

            @Override
            public void onComplete() {
                // fertig
            }
        });
        return text.toString();
    }

    @Test
    @DisplayName("bettet Abschnitte als Dokumente ein, Schluessel nur im Header")
    void einbettenStapel() throws Exception {
        doReturn(antwort(200, "{\"embeddings\":[{\"values\":" + werte(0.1f) + "},{\"values\":" + werte(0.2f) + "}]}"))
                .when(httpClient).send(any(), any());

        List<float[]> vektoren = einbetter.einbetten(List.of("Rechnung schreiben", "Projekt anlegen"));

        assertThat(vektoren).hasSize(2);
        assertThat(vektoren.get(0)).hasSize(RagIndexer.DIMENSIONEN);
        assertThat(vektoren.get(1)[0]).isEqualTo(0.2f);
        HttpRequest anfrage = gesendeteAnfrage();
        assertThat(anfrage.uri().toString()).endsWith("gemini-embedding-001:batchEmbedContents").doesNotContain(KEY);
        assertThat(anfrage.headers().firstValue("x-goog-api-key")).contains(KEY);
        assertThat(koerper(anfrage)).contains("RETRIEVAL_DOCUMENT", "models/gemini-embedding-001",
                "\"outputDimensionality\":768", "Rechnung schreiben");
    }

    @Test
    @DisplayName("bettet eine Frage als Suchanfrage ein und kuerzt ueberlange Texte")
    void anfrageEinbetten() throws Exception {
        doReturn(antwort(200, "{\"embedding\":{\"values\":" + werte(0.3f) + "}}")).when(httpClient).send(any(), any());

        float[] vektor = einbetter.anfrageEinbetten("x".repeat(9000));

        assertThat(vektor[0]).isEqualTo(0.3f);
        HttpRequest anfrage = gesendeteAnfrage();
        assertThat(anfrage.uri().toString()).endsWith(":embedContent");
        String body = koerper(anfrage);
        assertThat(body).contains("RETRIEVAL_QUERY").contains("x".repeat(8000)).doesNotContain("x".repeat(8001));
    }

    @Test
    @DisplayName("Wissensbasis: wartet bei 429 das Minutenfenster ab und versucht es erneut")
    void wiederholtBei429() throws Exception {
        HttpResponse<String> zuViel = antwort(429, "{\"error\":\"RESOURCE_EXHAUSTED\"}");
        HttpResponse<String> ok = antwort(200, "{\"embeddings\":[{\"values\":" + werte(1f) + "}]}");
        doReturn(zuViel, ok).when(httpClient).send(any(), any());

        List<float[]> vektoren = einbetter.einbetten(List.of("Text"));

        assertThat(vektoren.get(0)[0]).isEqualTo(1f);
        assertThat(pausen).containsExactly(Duration.ofSeconds(20));
    }

    @Test
    @DisplayName("Frage im Chat: nur ein kurzer zweiter Versuch, kein Warten aufs Minutenfenster")
    void frageWiederholtNurKurz() throws Exception {
        doReturn(antwort(429, "{\"error\":\"RESOURCE_EXHAUSTED\"}")).when(httpClient).send(any(), any());

        assertThatThrownBy(() -> einbetter.anfrageEinbetten("Wie schreibe ich eine Rechnung?"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 429 nach 2 Versuchen");
        assertThat(pausen).containsExactly(Duration.ofSeconds(1));
        verify(httpClient, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("gibt nach fuenf Versuchen bei Serverfehlern auf")
    void gibtNachFuenfVersuchenAuf() throws Exception {
        doReturn(antwort(503, "nicht verfuegbar")).when(httpClient).send(any(), any());

        assertThatThrownBy(() -> einbetter.einbetten(List.of("Text")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 503 nach 5 Versuchen");
        assertThat(pausen).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4),
                Duration.ofSeconds(8), Duration.ofSeconds(16));
        verify(httpClient, times(5)).send(any(), any());
    }

    @Test
    @DisplayName("wiederholt nach einer Zeitueberschreitung")
    void wiederholtNachTimeout() throws Exception {
        HttpResponse<String> ok = antwort(200, "{\"embedding\":{\"values\":" + werte(2f) + "}}");
        doThrow(new HttpTimeoutException("zu langsam")).doReturn(ok).when(httpClient).send(any(), any());

        assertThat(einbetter.anfrageEinbetten("Frage")[0]).isEqualTo(2f);
        assertThat(pausen).containsExactly(Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("ein Fehler wie 400 wird nicht wiederholt und nennt die Antwort gekuerzt")
    void keinWiederholenBei400() throws Exception {
        doReturn(antwort(400, "{\"error\":\"" + "x".repeat(500) + "\"}")).when(httpClient).send(any(), any());

        assertThatThrownBy(() -> einbetter.einbetten(List.of("Text")))
                .isInstanceOf(IOException.class)
                .hasMessageStartingWith("Gemini Embedding Fehler 400")
                .hasMessageEndingWith("…");
        verify(httpClient, times(1)).send(any(), any());
        assertThat(pausen).isEmpty();
    }

    @Test
    @DisplayName("ohne Schluessel wird gar nicht erst angefragt")
    void ohneSchluessel() throws Exception {
        key = " ";

        assertThatThrownBy(() -> einbetter.anfrageEinbetten("Frage"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Key fehlt");
        verify(httpClient, never()).send(any(), any());
    }

    @Test
    @DisplayName("zu kurzer Vektor ist ein Fehler")
    void zuKurzerVektor() throws Exception {
        doReturn(antwort(200, "{\"embedding\":{\"values\":[0.1,0.2]}}")).when(httpClient).send(any(), any());

        assertThatThrownBy(() -> einbetter.anfrageEinbetten("Frage"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("2 statt 768");
    }
}
