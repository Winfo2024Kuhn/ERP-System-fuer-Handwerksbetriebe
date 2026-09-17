package org.example.kalkulationsprogramm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.dto.SpracheingabeErgebnis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prueft vor allem, WAS in der Systemanweisung steht (Metallbau-Begriffe,
 * Reintext-Regel, "[unverstaendlich]" statt Raten) und dass weder die
 * Audio-Bytes noch das Transkript jemals im Klartext einer Fehlermeldung
 * oder eines Logs landen (DSGVO, Sprachaufnahmen von Mitarbeitern im
 * Arbeitsverhaeltnis).
 *
 * DSGVO: ausschliesslich erfundene Dummy-Inhalte, keine echten Aufnahmen.
 */
@ExtendWith(MockitoExtension.class)
class SpracheingabeServiceTest {

    private SystemSettingsService systemSettingsService;
    private HttpClient httpClient;
    private SpracheingabeService service;

    @BeforeEach
    void setUp() {
        systemSettingsService = mock(SystemSettingsService.class);
        httpClient = mock(HttpClient.class);
        service = new SpracheingabeService(
                systemSettingsService, new ObjectMapper(), httpClient, "gemini-flash-latest");
    }

    private static byte[] audioBytes(int laenge) {
        byte[] daten = new byte[laenge];
        Arrays.fill(daten, (byte) 42);
        return daten;
    }

    @SuppressWarnings("unchecked")
    private void mockGeminiAntwort(int status, String body) throws IOException, InterruptedException {
        when(systemSettingsService.getGeminiApiKey()).thenReturn("dummy-test-key");
        HttpResponse<String> antwort = mock(HttpResponse.class);
        when(antwort.statusCode()).thenReturn(status);
        // lenient: bei HTTP >= 400 liest der Dienst absichtlich NIE antwort.body()
        // (DSGVO - der Koerper waere hier das Transkript). Fuer den Erfolgsfall
        // (200) wird der Stub dagegen tatsaechlich gebraucht.
        Mockito.lenient().when(antwort.body()).thenReturn(body);
        when(httpClient.send(Mockito.any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(antwort);
    }

    @Test
    void systemanweisungEnthaeltDieKernregeln() {
        String anweisung = SpracheingabeService.SYSTEM_ANWEISUNG;

        assertThat(anweisung).contains("[unverstaendlich]");
        assertThat(anweisung).contains("Fuellwoerter");
        assertThat(anweisung).contains("reiner Text");
        assertThat(anweisung).contains("Erfinde nichts dazu");
        assertThat(anweisung).contains("HEB 200");
    }

    @Test
    void koerperHatGenauEinenInlineDataPartUndKeinenText() {
        byte[] audio = {1, 2, 3, 4};

        JsonNode koerper = service.baueKoerper(audio, "audio/webm");
        JsonNode contents = koerper.path("contents");

        assertThat(contents.size()).isEqualTo(1);
        JsonNode parts = contents.get(0).path("parts");
        assertThat(parts.size()).isEqualTo(1);
        assertThat(parts.get(0).path("inlineData").path("mimeType").asText()).isEqualTo("audio/webm");
        assertThat(parts.get(0).path("inlineData").path("data").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(audio));
    }

    @Test
    void generationConfigHatTemperaturNullUndKeinSchema() {
        JsonNode koerper = service.baueKoerper(new byte[] {1}, "audio/webm");
        JsonNode konfiguration = koerper.path("generationConfig");

        assertThat(konfiguration.path("temperature").asDouble()).isEqualTo(0.0);
        assertThat(konfiguration.path("responseMimeType").asText()).isEqualTo("text/plain");
        assertThat(konfiguration.has("responseSchema")).isFalse();
    }

    @Test
    void codecZusatzWirdAbgeschnitten() {
        assertThat(SpracheingabeService.normalisiereInhaltstyp("audio/webm;codecs=opus")).isEqualTo("audio/webm");
        assertThat(SpracheingabeService.normalisiereInhaltstyp(null)).isEqualTo("");
    }

    @Test
    void fremderInhaltstypWirdAbgewiesen() {
        assertThatThrownBy(() -> service.transkribiere(new ByteArrayInputStream(new byte[0]), "image/png"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.transkribiere(new ByteArrayInputStream(new byte[0]), "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zuGrosseAufnahmeWirdAbgewiesen() {
        InputStream zuGross = new ByteArrayInputStream(audioBytes(SpracheingabeService.MAX_AUDIO_BYTES + 1));

        assertThatThrownBy(() -> service.transkribiere(zuGross, "audio/webm"))
                .isInstanceOf(SpracheingabeService.AufnahmeZuGross.class);
    }

    @Test
    void ohneApiSchluesselFliegtEinEhrlicherFehler() {
        when(systemSettingsService.getGeminiApiKey()).thenReturn("");
        InputStream audio = new ByteArrayInputStream(audioBytes(10));

        assertThatThrownBy(() -> service.transkribiere(audio, "audio/webm"))
                .isInstanceOf(IllegalStateException.class);

        Mockito.verifyNoInteractions(httpClient);
    }

    @Test
    void geminiFehlerAntwortLandetNichtInDerFehlermeldung() throws IOException, InterruptedException {
        mockGeminiAntwort(500, "{\"error\":\"Geheimes Transkript von Max Mustermann\"}");
        InputStream audio = new ByteArrayInputStream(audioBytes(10));

        assertThatThrownBy(() -> service.transkribiere(audio, "audio/webm"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("Geheimes Transkript");
    }

    @Test
    void antwortTextKommtGetrimmtZurueck() throws IOException, InterruptedException {
        mockGeminiAntwort(200, """
                {"candidates":[{"content":{"parts":[{"text":" Gelaender montiert.\\n"}]}}]}
                """);
        InputStream audio = new ByteArrayInputStream(audioBytes(10));

        SpracheingabeErgebnis ergebnis = service.transkribiere(audio, "audio/webm");

        assertThat(ergebnis.text()).isEqualTo("Gelaender montiert.");
    }
}
