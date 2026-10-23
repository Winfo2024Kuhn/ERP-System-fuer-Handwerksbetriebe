package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import tools.jackson.databind.json.JsonMapper;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatMessage;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatResult;
import org.example.kalkulationsprogramm.service.KiHilfeService.PageContext;
import org.example.kalkulationsprogramm.service.LocalRagService.Bereich;
import org.example.kalkulationsprogramm.service.LocalRagService.CodeChunkResult;
import org.example.kalkulationsprogramm.service.kihilfe.KiHilfeWerkzeuge;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class KiHilfeServiceTest {

    @Mock
    private CodebaseIndexService codebaseIndexService;
    @Mock
    private LocalRagService localRagService;
    @Mock
    private SystemSettingsService systemSettingsService;
    @Mock
    private FirmeninformationRepository firmeninformationRepository;
    @Mock
    private HttpClient httpClient;

    private final ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults().build();
    private KiHilfeService service;

    @BeforeEach
    void setUp() {
        service = new KiHilfeService(objectMapper, codebaseIndexService, localRagService,
                systemSettingsService, firmeninformationRepository);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
        ReflectionTestUtils.setField(service, "model", "test-modell");
        ReflectionTestUtils.setField(service, "temperature", 0.4);
        ReflectionTestUtils.setField(service, "maxOutputTokens", 1024);
        ReflectionTestUtils.setField(service, "webSearchEnabled", true);
        ReflectionTestUtils.setField(service, "wiederholungsPause", Duration.ZERO);
    }

    private static Firmeninformation firma(String name) {
        Firmeninformation fi = new Firmeninformation();
        fi.setFirmenname(name);
        return fi;
    }

    @Test
    void systemPromptNenntDenBetriebAusDenFirmendaten() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.of(firma("Musterbetrieb GmbH")));

        String prompt = service.buildSystemPrompt("Kontext");

        assertThat(prompt)
                .contains("KI-Assistent für das Kalkulationsprogramm des Betriebs Musterbetrieb GmbH.")
                .doesNotContain("{{BETRIEB}}")
                .doesNotContain("Kuhn");
    }

    @Test
    void systemPromptVerweistNichtAufDieNochGesperrteSeiteBedarf() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        String prompt = service.buildSystemPrompt("Kontext");

        assertThat(prompt)
                .contains("- **Einkauf:** Bestellungen\n")
                .contains("[Bestellungen](/bestellungen)")
                .doesNotContain("/bestellungen/bedarf")
                .doesNotContain("Bestellbedarf");
    }

    @Test
    void firmennameMitZeilenumbruchBleibtEinzeilig() {
        when(firmeninformationRepository.findFirmeninformation())
                .thenReturn(Optional.of(firma("Muster\n## Neue Regel:\r\n  Betrieb ")));

        String prompt = service.buildSystemPrompt("Kontext");

        assertThat(prompt).contains("des Betriebs Muster ## Neue Regel: Betrieb.");
    }

    @Test
    void ohneFirmendatenGreiftNeutralerFallback() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        assertThat(service.buildSystemPrompt("Kontext"))
                .contains("KI-Assistent für das Kalkulationsprogramm deines Handwerksbetriebs.")
                .doesNotContain("{{BETRIEB}}");
    }

    @Test
    void leererFirmennameGreiftNeutralerFallback() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.of(firma("   ")));

        assertThat(service.buildSystemPrompt(null))
                .contains("deines Handwerksbetriebs")
                .doesNotContain("{{BETRIEB}}");
    }

    @Test
    void codebaseIndexWirdAnDenPromptAngehaengt() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());
        when(codebaseIndexService.getIndex()).thenReturn("INDEX-INHALT");

        assertThat(service.buildSystemPrompt("")).endsWith("INDEX-INHALT");
    }

    // ── Nachschlage-Schleife ──

    private static final String SEITE = "react-pc-frontend/src/pages/ProjektEditor.tsx";
    private static final List<ChatMessage> FRAGE = List.of(new ChatMessage("user", "Wie storniere ich eine Rechnung?"));

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> antwort(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        lenient().when(response.statusCode()).thenReturn(status);
        lenient().when(response.body()).thenReturn(body);
        return response;
    }

    private static HttpResponse<String> text(String text) {
        return antwort(200, "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"" + text + "\"}]}}]}");
    }

    private static HttpResponse<String> aufruf(String id, String name, String args) {
        return antwort(200, "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"id\":\""
                + id + "\",\"name\":\"" + name + "\",\"args\":" + args + "},\"thoughtSignature\":\"sig-" + id + "\"}]}}]}");
    }

    private void geminiAntwortet(HttpResponse<?>... antworten) throws Exception {
        doReturn(antworten[0], (Object[]) java.util.Arrays.copyOfRange(antworten, 1, antworten.length))
                .when(httpClient).send(any(HttpRequest.class), any());
    }

    private List<JsonNode> gesendeteAnfragen(int anzahl) throws Exception {
        ArgumentCaptor<HttpRequest> anfragen = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(anzahl)).send(anfragen.capture(), any());
        List<JsonNode> koerper = new ArrayList<>();
        for (HttpRequest anfrage : anfragen.getAllValues()) koerper.add(objectMapper.readTree(koerper(anfrage)));
        return koerper;
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

    private void wissenVorhanden() {
        when(systemSettingsService.getGeminiApiKey()).thenReturn("test-schluessel");
        lenient().when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());
        when(localRagService.isAvailable()).thenReturn(true);
        when(localRagService.search(anyString(), any(), any())).thenReturn(List.of(
                new CodeChunkResult(SEITE + "::a", "Kopf", SEITE, "frontend-page", "component", "Teil 1", true)));
        when(localRagService.buildContextFromResults(any())).thenReturn("AUTOMATISCHER-KONTEXT");
    }

    private void ohneWissen() {
        when(systemSettingsService.getGeminiApiKey()).thenReturn("test-schluessel");
        lenient().when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());
        when(localRagService.isAvailable()).thenReturn(false);
    }

    @Test
    void ohneWissensbasisAntwortetDieKiDirektMitWebsucheUndQuellen() throws Exception {
        ohneWissen();
        geminiAntwortet(antwort(200, """
                {"candidates":[{"content":{"parts":[{"text":"Laut DIN EN 1090 ..."}]},
                  "groundingMetadata":{"webSearchQueries":["DIN EN 1090"],
                    "groundingChunks":[{"web":{"uri":"https://example.org/din","title":"DIN"}},
                                       {"web":{"uri":"https://example.org/din","title":"doppelt"}},
                                       {"web":{"uri":"","title":"leer"}}]}}]}"""));
        List<String> live = new ArrayList<>();

        ChatResult result = service.chat(FRAGE, null, live::add);

        assertThat(result.reply()).isEqualTo("Laut DIN EN 1090 ...");
        assertThat(result.sources()).containsExactly(new KiHilfeService.SourceLink("DIN", "https://example.org/din"));
        assertThat(result.schritte()).containsExactly("Sucht im Internet: DIN EN 1090");
        assertThat(live).isEqualTo(result.schritte());
        JsonNode anfrage = gesendeteAnfragen(1).get(0);
        assertThat(anfrage.path("tools").toString()).contains("googleSearch").doesNotContain("functionDeclarations");
        assertThat(anfrage.has("toolConfig")).isFalse();
        assertThat(anfrage.path("systemInstruction").toString()).doesNotContain("Selbst nachschlagen");
    }

    @Test
    void kiSchlaegtSelbstNachUndAntwortetDann() throws Exception {
        wissenVorhanden();
        when(localRagService.suchen(eq("Storno stornieren"), eq(Bereich.BEDIENUNG), eq(4), any())).thenReturn(List.of(
                new CodeChunkResult(SEITE + "::b", "<Button>Stornieren</Button>", SEITE, "frontend-page", "component",
                        "Teil 2", false)));
        geminiAntwortet(
                aufruf("a1", KiHilfeWerkzeuge.SUCHEN, "{\"suchbegriffe\":\"Storno stornieren\",\"hinweis\":\"Wie man storniert\"}"),
                text("Klicke auf Stornieren."));
        List<String> live = new ArrayList<>();

        ChatResult result = service.chat(FRAGE, new PageContext("/projekte/7", "Projekte", null, null, null, null), live::add);

        assertThat(result.reply()).isEqualTo("Klicke auf Stornieren.");
        assertThat(live).containsExactly(KiHilfeService.SCHRITT_ERSTE_SUCHE, "Schaut nach: Wie man storniert");
        assertThat(result.schritte()).isEqualTo(live);
        verify(localRagService).search(eq("Wie storniere ich eine Rechnung?"), eq("Seite: Projekte (/projekte/7)"),
                eq("/projekte/7"));

        List<JsonNode> anfragen = gesendeteAnfragen(2);
        JsonNode erste = anfragen.get(0);
        assertThat(erste.path("systemInstruction").toString()).contains("Selbst nachschlagen", "AUTOMATISCHER-KONTEXT");
        assertThat(erste.path("tools").toString()).contains("googleSearch", KiHilfeWerkzeuge.SUCHEN, KiHilfeWerkzeuge.LESEN);
        assertThat(erste.path("toolConfig").path("includeServerSideToolInvocations").asBoolean(false)).isTrue();
        assertThat(erste.path("toolConfig").has("functionCallingConfig")).isFalse();

        JsonNode verlauf = anfragen.get(1).path("contents");
        assertThat(verlauf).hasSize(3);
        assertThat(verlauf.get(0).path("parts").get(0).path("text").asString(""))
                .startsWith("Wie storniere ich eine Rechnung?").contains("Seite: /projekte/7");
        // Modell-Zug unveraendert zurueck, inkl. Gedanken-Signatur
        assertThat(verlauf.get(1).path("role").asString("")).isEqualTo("model");
        assertThat(verlauf.get(1).path("parts").get(0).path("thoughtSignature").asString("")).isEqualTo("sig-a1");
        JsonNode antwortTeil = verlauf.get(2).path("parts").get(0).path("functionResponse");
        assertThat(antwortTeil.path("id").asString("")).isEqualTo("a1");
        assertThat(antwortTeil.path("response").path("treffer").get(0).path("inhalt").asString("")).contains("Stornieren");
    }

    @Test
    void nachVierRundenMussDieKiAntworten() throws Exception {
        wissenVorhanden();
        when(localRagService.suchen(anyString(), any(), anyInt(), any())).thenReturn(List.of());
        String args = "{\"suchbegriffe\":\"x\",\"hinweis\":\"x\"}";
        geminiAntwortet(aufruf("1", KiHilfeWerkzeuge.SUCHEN, args), aufruf("2", KiHilfeWerkzeuge.SUCHEN, args),
                aufruf("3", KiHilfeWerkzeuge.SUCHEN, args), aufruf("4", KiHilfeWerkzeuge.SUCHEN, args),
                text("Das weiss ich nicht sicher."));

        ChatResult result = service.chat(FRAGE, null);

        assertThat(result.reply()).isEqualTo("Das weiss ich nicht sicher.");
        List<JsonNode> anfragen = gesendeteAnfragen(KiHilfeService.MAX_NACHSCHLAGE_RUNDEN + 1);
        for (int i = 0; i < KiHilfeService.MAX_NACHSCHLAGE_RUNDEN; i++) {
            assertThat(anfragen.get(i).path("toolConfig").has("functionCallingConfig")).isFalse();
        }
        JsonNode letzte = anfragen.get(KiHilfeService.MAX_NACHSCHLAGE_RUNDEN);
        assertThat(letzte.path("toolConfig").path("functionCallingConfig").path("mode").asString("")).isEqualTo("NONE");
        assertThat(letzte.path("tools").toString()).contains(KiHilfeWerkzeuge.SUCHEN);
    }

    @Test
    void ruftDieKiInDerLetztenRundeTrotzdemNurWerkzeugeAufGibtEsEinenFehler() throws Exception {
        wissenVorhanden();
        ReflectionTestUtils.setField(service, "webSearchEnabled", false);
        when(localRagService.suchen(anyString(), any(), anyInt(), any())).thenReturn(List.of());
        String args = "{\"suchbegriffe\":\"x\"}";
        geminiAntwortet(aufruf("1", KiHilfeWerkzeuge.SUCHEN, args), aufruf("2", KiHilfeWerkzeuge.SUCHEN, args),
                aufruf("3", KiHilfeWerkzeuge.SUCHEN, args), aufruf("4", KiHilfeWerkzeuge.SUCHEN, args),
                aufruf("5", KiHilfeWerkzeuge.SUCHEN, args));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(IOException.class)
                .hasMessage("Keine Textantwort von der KI erhalten");
        List<JsonNode> anfragen = gesendeteAnfragen(5);
        // Ohne Websuche: nur eigene Werkzeuge, toolConfig erst in der letzten Runde
        assertThat(anfragen.get(0).path("tools").toString()).doesNotContain("googleSearch");
        assertThat(anfragen.get(0).has("toolConfig")).isFalse();
        assertThat(anfragen.get(4).path("toolConfig").has("includeServerSideToolInvocations")).isFalse();
        assertThat(anfragen.get(4).path("toolConfig").path("functionCallingConfig").path("mode").asString("")).isEqualTo("NONE");
    }

    @Test
    void mehrAlsDreiGleichzeitigeAufrufeWerdenNichtAusgefuehrtAberBeantwortet() throws Exception {
        wissenVorhanden();
        when(localRagService.suchen(anyString(), any(), anyInt(), any())).thenReturn(List.of());
        StringBuilder teile = new StringBuilder();
        for (int i = 1; i <= 4; i++) {
            if (i > 1) teile.append(',');
            teile.append("{\"functionCall\":{\"id\":\"c").append(i).append("\",\"name\":\"").append(KiHilfeWerkzeuge.SUCHEN)
                    .append("\",\"args\":{\"suchbegriffe\":\"wort").append(i).append("\"}}}");
        }
        geminiAntwortet(antwort(200, "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[" + teile + "]}}]}"),
                text("Fertig."));

        service.chat(FRAGE, null);

        verify(localRagService, times(KiHilfeService.MAX_AUFRUFE_JE_RUNDE)).suchen(anyString(), any(), anyInt(), any());
        JsonNode antworten = gesendeteAnfragen(2).get(1).path("contents").get(2).path("parts");
        assertThat(antworten).hasSize(4);
        assertThat(antworten.get(3).path("functionResponse").path("id").asString("")).isEqualTo("c4");
        assertThat(antworten.get(3).path("functionResponse").path("response").path("hinweis").asString(""))
                .startsWith("Zu viele Suchen");
    }

    @Test
    void websuchenWerdenHoechstensDreimalUndOhneDoppelteGemeldet() throws Exception {
        ohneWissen();
        geminiAntwortet(antwort(200, """
                {"candidates":[{"content":{"parts":[{"text":"ok"}]},
                  "groundingMetadata":{"webSearchQueries":["Wetter Berlin","Wetter Berlin"," ","Regen","Wind","Sturm"]}}]}"""));

        ChatResult result = service.chat(FRAGE, null);

        assertThat(result.schritte()).containsExactly("Sucht im Internet: Wetter Berlin", "Sucht im Internet: Regen",
                "Sucht im Internet: Wind");
    }

    @Test
    void scheitertDieErsteSucheAntwortetDieKiTrotzdem() throws Exception {
        when(systemSettingsService.getGeminiApiKey()).thenReturn("test-schluessel");
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());
        when(localRagService.isAvailable()).thenReturn(true);
        when(localRagService.search(anyString(), isNull(), isNull())).thenThrow(new IllegalStateException("kaputt"));
        geminiAntwortet(text("Antwort"));

        ChatResult result = service.chat(FRAGE, null);

        assertThat(result.reply()).isEqualTo("Antwort");
        assertThat(result.schritte()).isEmpty();
        assertThat(gesendeteAnfragen(1).get(0).path("systemInstruction").toString()).contains("Selbst nachschlagen");
    }

    @Test
    void ohneTrefferDerErstenSucheKeinSchrittUndKeinKontext() throws Exception {
        when(systemSettingsService.getGeminiApiKey()).thenReturn("test-schluessel");
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());
        when(localRagService.isAvailable()).thenReturn(true);
        when(localRagService.search(anyString(), any(), any())).thenReturn(List.of());
        geminiAntwortet(text("Antwort"));

        ChatResult result = service.chat(FRAGE, null);

        assertThat(result.schritte()).isEmpty();
        assertThat(gesendeteAnfragen(1).get(0).path("systemInstruction").toString())
                .doesNotContain("Automatisch gefundene Abschnitte");
    }

    @Test
    void beiUeberlastungEinZweiterVersuch() throws Exception {
        ohneWissen();
        geminiAntwortet(antwort(503, "{\"error\":\"overloaded\"}"), text("Hat geklappt"));

        assertThat(service.chat(FRAGE, null).reply()).isEqualTo("Hat geklappt");
        gesendeteAnfragen(2);
    }

    @Test
    void dauerhafteFehlerWerdenNichtWiederholt() throws Exception {
        ohneWissen();
        geminiAntwortet(antwort(400, "{\"error\":\"bad request\"}"));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(IOException.class)
                .hasMessageContaining("Fehler 400");
        gesendeteAnfragen(1);
    }

    @Test
    void zweimalUeberlastetGibtEinenFehler() throws Exception {
        ohneWissen();
        geminiAntwortet(antwort(429, ""), antwort(503, ""));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(IOException.class)
                .hasMessageContaining("Fehler 503");
    }

    @Test
    void leereAntwortenSindFehler() throws Exception {
        ohneWissen();
        geminiAntwortet(antwort(200, "{\"candidates\":[]}"),
                antwort(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"\"}]}}]}"));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).hasMessage("Keine Antwort von der KI erhalten");
        assertThatThrownBy(() -> service.chat(FRAGE, null)).hasMessage("Keine Textantwort von der KI erhalten");
    }

    @Test
    void ohneSchluesselOderNachrichtenKeinAufruf() {
        when(systemSettingsService.getGeminiApiKey()).thenReturn(" ", "test-schluessel");

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> service.chat(List.of(), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seitenkontextHaengtGekuerztAnDerLetztenFrage() throws Exception {
        ohneWissen();
        geminiAntwortet(text("ok"));
        List<ChatMessage> verlauf = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            verlauf.add(new ChatMessage(i % 2 == 0 ? "user" : "assistant", "Nachricht " + i));
        }
        verlauf.add(new ChatMessage("user", "Letzte Frage"));
        PageContext seite = new PageContext("/projekte", "Projekte", "x".repeat(3_000), "Pflichtfeld fehlt", 52.5, 13.4);

        service.chat(verlauf, seite);

        JsonNode contents = gesendeteAnfragen(1).get(0).path("contents");
        assertThat(contents).hasSize(20);
        assertThat(contents.get(19).path("role").asString("")).isEqualTo("user");
        String letzte = contents.get(19).path("parts").get(0).path("text").asString("");
        assertThat(letzte).startsWith("Letzte Frage").contains("[Aktueller Bildschirmkontext]", "Titel: Projekte",
                "Fehlermeldungen: Pflichtfeld fehlt", "Breitengrad 52.5", "x".repeat(2_000) + "…")
                .doesNotContain("x".repeat(2_001));
        assertThat(contents.get(18).path("role").asString("")).isEqualTo("model");
    }

    @Test
    void nachfrageNimmtDieVorigeFrageMit() {
        ChatMessage erste = new ChatMessage("user", "Wie storniere ich eine Rechnung?");
        ChatMessage antwort = new ChatMessage("assistant", "So geht es ...");

        assertThat(KiHilfeService.suchfrage(List.of(erste, antwort, new ChatMessage("user", "Und bei Anfragen?"))))
                .isEqualTo("Wie storniere ich eine Rechnung?\nUnd bei Anfragen?");
        String neueFrage = "Wo kann ich bei einem Mitarbeiter die Stundensaetze fuer Wochenenden aendern?";
        assertThat(KiHilfeService.suchfrage(List.of(erste, antwort, new ChatMessage("user", neueFrage))))
                .isEqualTo(neueFrage);
        assertThat(KiHilfeService.suchfrage(List.of(erste))).isEqualTo("Wie storniere ich eine Rechnung?");
        assertThat(KiHilfeService.suchfrage(List.of(antwort))).isEmpty();
    }

    @Test
    void ohneWerkzeugeKeinNachschlageAbschnittImPrompt() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        assertThat(service.buildSystemPrompt("Kontext", true))
                .contains("## Selbst nachschlagen", "Nach höchstens " + KiHilfeService.MAX_NACHSCHLAGE_RUNDEN + " Runden",
                        "Nenne NIE Dateinamen")
                .doesNotContain("{{RUNDEN}}");
        assertThat(service.buildSystemPrompt("Kontext")).doesNotContain("## Selbst nachschlagen");
    }

    @Test
    void lehntGeminiDieWerkzeugeAbAntwortetDieKiOhneNachschlagen() throws Exception {
        wissenVorhanden();
        geminiAntwortet(antwort(400, "{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}"), text("Antwort wie frueher"));

        ChatResult result = service.chat(FRAGE, null);

        assertThat(result.reply()).isEqualTo("Antwort wie frueher");
        List<JsonNode> anfragen = gesendeteAnfragen(2);
        assertThat(anfragen.get(1).path("tools").toString()).contains("googleSearch").doesNotContain("functionDeclarations");
        assertThat(anfragen.get(1).has("toolConfig")).isFalse();
        assertThat(anfragen.get(1).path("systemInstruction").toString())
                .contains("AUTOMATISCHER-KONTEXT").doesNotContain("Selbst nachschlagen");
        // Der Verlauf beginnt neu, ohne halbe Werkzeug-Runden
        assertThat(anfragen.get(1).path("contents")).hasSize(1);
    }

    @Test
    void andereAblehnungenGehenOhneSicherheitsnetzDurch() throws Exception {
        wissenVorhanden();
        geminiAntwortet(antwort(403, "{\"error\":\"PERMISSION_DENIED\"}"));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(IOException.class)
                .hasMessageContaining("Fehler 403");
        gesendeteAnfragen(1);
    }

    @Test
    void gesamtfristAbgelaufenOhneAufrufBeiGemini() throws Exception {
        ohneWissen();
        ReflectionTestUtils.setField(service, "gesamtZeitlimit", Duration.ZERO);

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(KiHilfeService.ZeitlimitErreicht.class);
        verify(httpClient, times(0)).send(any(HttpRequest.class), any());
    }

    @Test
    void zeitueberschreitungNachDerFristWirdZumZeitlimit() throws Exception {
        ohneWissen();
        ReflectionTestUtils.setField(service, "gesamtZeitlimit", Duration.ofMillis(200));
        when(httpClient.send(any(HttpRequest.class), any())).thenAnswer(aufruf -> {
            Thread.sleep(250);
            throw new java.net.http.HttpTimeoutException("zu langsam");
        });

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(KiHilfeService.ZeitlimitErreicht.class);
    }

    @Test
    void zeitueberschreitungVorDerFristBleibtEinNormalerFehler() throws Exception {
        ohneWissen();
        when(httpClient.send(any(HttpRequest.class), any())).thenThrow(new java.net.http.HttpTimeoutException("zu langsam"));

        assertThatThrownBy(() -> service.chat(FRAGE, null)).isInstanceOf(java.net.http.HttpTimeoutException.class);
    }

    @Test
    void schliesstDerNutzerDenChatBrichtDieErsteSucheAb() throws Exception {
        wissenVorhanden();
        IllegalStateException verbindungZu = new IllegalStateException("Browser weg");

        assertThatThrownBy(() -> service.chat(FRAGE, null, schritt -> { throw verbindungZu; })).isSameAs(verbindungZu);
        verify(httpClient, times(0)).send(any(HttpRequest.class), any());
    }

    @Test
    void mitWerkzeugenOhneTrefferKeinVollerCodebaseIndex() {
        when(firmeninformationRepository.findFirmeninformation()).thenReturn(Optional.empty());

        assertThat(service.buildSystemPrompt(null, true)).contains("Selbst nachschlagen");
        verify(codebaseIndexService, times(0)).getIndex();
    }

    @Test
    void kaputterWerkzeugAufrufFuehrtZurAntwortOhneWerkzeuge() throws Exception {
        wissenVorhanden();
        geminiAntwortet(antwort(200, "{\"candidates\":[{\"finishReason\":\"MALFORMED_FUNCTION_CALL\",\"content\":{\"parts\":[]}}]}"),
                text("Trotzdem eine Antwort"));

        assertThat(service.chat(FRAGE, null).reply()).isEqualTo("Trotzdem eine Antwort");
        List<JsonNode> anfragen = gesendeteAnfragen(2);
        assertThat(anfragen.get(1).path("toolConfig").path("functionCallingConfig").path("mode").asString("")).isEqualTo("NONE");
    }

    @Test
    void fehltDieZeitFuerDieAntwortWirdNichtMehrNachgeschlagen() throws Exception {
        wissenVorhanden();
        // Weniger als die Reserve fuer die Antwort: gleich die erste Runde muss antworten
        ReflectionTestUtils.setField(service, "gesamtZeitlimit", Duration.ofSeconds(59));
        geminiAntwortet(text("Kurze Antwort"));

        assertThat(service.chat(FRAGE, null).reply()).isEqualTo("Kurze Antwort");
        assertThat(gesendeteAnfragen(1).get(0).path("toolConfig").path("functionCallingConfig").path("mode").asString(""))
                .isEqualTo("NONE");
    }
}
