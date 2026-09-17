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

    /**
     * Liefert bis zu {@code gesamtlaenge} Bytes und zaehlt dabei mit, wie viel
     * tatsaechlich gelesen wurde. Damit laesst sich schwarzbox pruefen, DASS
     * der Dienst das Lesen abbricht, statt nur, DASS er am Ende die richtige
     * Ausnahme wirft - eine Implementierung, die erst alles puffert und die
     * Groesse erst danach prueft, wuerde hier viel mehr als
     * {@code MAX_AUDIO_BYTES} Bytes lesen.
     */
    private static final class ZaehlenderStrom extends InputStream {
        private long verbleibend;
        private long gelesen = 0;

        ZaehlenderStrom(long gesamtlaenge) {
            this.verbleibend = gesamtlaenge;
        }

        @Override
        public int read() {
            if (verbleibend <= 0) {
                return -1;
            }
            verbleibend--;
            gelesen++;
            return 42;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (verbleibend <= 0) {
                return -1;
            }
            int anzahl = (int) Math.min(len, verbleibend);
            Arrays.fill(b, off, off + anzahl, (byte) 42);
            verbleibend -= anzahl;
            gelesen += anzahl;
            return anzahl;
        }

        long geleseneBytes() {
            return gelesen;
        }
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockGeminiAntwort(int status, String body) throws IOException, InterruptedException {
        when(systemSettingsService.getGeminiApiKey()).thenReturn("dummy-test-key");
        HttpResponse<String> antwort = mock(HttpResponse.class);
        when(antwort.statusCode()).thenReturn(status);
        // lenient() unterdrueckt hier NUR Mockitos "Stub nie benutzt"-Warnung,
        // weil der 200-Erfolgsfall den Stub braucht und der Fehlerfall nicht.
        // lenient() belegt fuer sich allein NICHTS ueber das Verhalten des
        // Dienstes - der eigentliche Beweis, dass antwort.body() im Fehlerfall
        // nie gelesen wird, ist die explizite verify(antwort, never()).body()
        // im aufrufenden Test.
        Mockito.lenient().when(antwort.body()).thenReturn(body);
        when(httpClient.send(Mockito.any(HttpRequest.class), Mockito.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(antwort);
        return antwort;
    }

    @Test
    void systemanweisungEnthaeltDieKernregeln() {
        String anweisung = SpracheingabeService.SYSTEM_ANWEISUNG;

        assertThat(anweisung).contains("[unverstaendlich]");
        assertThat(anweisung).contains("Fuellwoerter");
        assertThat(anweisung).contains("reiner Text");
        assertThat(anweisung).contains("Erfinde nichts dazu");
        assertThat(anweisung).contains("HEB 200");
        // Reintext-Verbot: eigene Zeile, nicht durch "reiner Text" oben abgedeckt.
        assertThat(anweisung).contains("Keine Sternchen, keine Rauten, keine HTML-Tags, keine Markdown-Syntax.");
        // Metallbau-Vokabelblock: eigene Zeile, nicht durch "HEB 200" (Profile-Zeile) abgedeckt.
        assertThat(anweisung).contains("Feuerverzinkung, Pulverbeschichtung, VSG, ESG, Schwerlastanker,");
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
        // Absichtlich NICHT-leere Stroeme: Mit einem leeren Stream waere die
        // IllegalArgumentException auch bei einer durchgelassenen Whitelist
        // ueber die "Aufnahme ist leer"-Pruefung gekommen - der Test haette
        // dann gar nichts ueber die Whitelist selbst ausgesagt. Zusaetzlich
        // die konkrete Meldung pruefen, damit nur genau die Whitelist-Pruefung
        // (und keine andere IllegalArgumentException) den Test gruen macht.
        assertThatThrownBy(() -> service.transkribiere(
                new ByteArrayInputStream(audioBytes(10)), "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Dieses Audioformat wird nicht unterstuetzt.");
        assertThatThrownBy(() -> service.transkribiere(
                new ByteArrayInputStream(audioBytes(10)), "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Dieses Audioformat wird nicht unterstuetzt.");
    }

    @Test
    void zuGrosseAufnahmeWirdAbgewiesen() {
        InputStream zuGross = new ByteArrayInputStream(audioBytes(SpracheingabeService.MAX_AUDIO_BYTES + 1));

        assertThatThrownBy(() -> service.transkribiere(zuGross, "audio/webm"))
                .isInstanceOf(SpracheingabeService.AufnahmeZuGross.class);
    }

    @Test
    void grenzeBrichtDasLesenSofortAbStattAllesVorherZuPuffern() {
        // 5 MB mehr als erlaubt anbieten. Bricht der Dienst NICHT waehrend des
        // Lesens ab, sondern puffert er (fehlerhaft) erst alles und prueft die
        // Groesse erst danach, liest er weit mehr als MAX_AUDIO_BYTES - genau
        // das soll dieser Test aufdecken.
        long angebotenGesamt = (long) SpracheingabeService.MAX_AUDIO_BYTES + 5_000_000;
        ZaehlenderStrom strom = new ZaehlenderStrom(angebotenGesamt);

        assertThatThrownBy(() -> service.transkribiere(strom, "audio/webm"))
                .isInstanceOf(SpracheingabeService.AufnahmeZuGross.class);

        assertThat(strom.geleseneBytes())
                .as("der Dienst darf nur knapp ueber die Grenze lesen, nicht die kompletten %d angebotenen Bytes",
                        angebotenGesamt)
                .isGreaterThan((long) SpracheingabeService.MAX_AUDIO_BYTES)
                .isLessThan((long) SpracheingabeService.MAX_AUDIO_BYTES + 1_000_000);
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
        HttpResponse<String> antwort = mockGeminiAntwort(500, "{\"error\":\"Geheimes Transkript von Max Mustermann\"}");
        InputStream audio = new ByteArrayInputStream(audioBytes(10));

        assertThatThrownBy(() -> service.transkribiere(audio, "audio/webm"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("Geheimes Transkript");

        // Der eigentliche Beweis (nicht nur die Fehlermeldung): der Dienst darf
        // den Antwortkoerper im Fehlerfall nicht einmal LESEN, egal wohin er
        // ihn danach schreiben wuerde. Sonst koennte das Transkript z.B. per
        // Debugger, APM-Tool oder einer spaeteren Codeaenderung doch noch nach
        // aussen dringen, ohne dass ein Test das je gemerkt haette.
        Mockito.verify(antwort, Mockito.never()).body();
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
