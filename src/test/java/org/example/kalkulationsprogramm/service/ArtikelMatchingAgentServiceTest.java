package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelVorschlagTyp;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Werkstoff;
import org.example.kalkulationsprogramm.service.ArtikelMatchingAgentService.Ergebnis;
import org.example.kalkulationsprogramm.service.ArtikelMatchingAgentService.MatchingResult;
import org.example.kalkulationsprogramm.service.ArtikelMatchingToolService.MatchingToolContext;
import org.example.kalkulationsprogramm.service.ArtikelMatchingToolService.ToolSideEffect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Unit-Tests für {@link ArtikelMatchingAgentService}.
 * Mockt den HttpClient per ReflectionTestUtils, um die Agent-Loop ohne Netzwerk zu prüfen.
 */
@ExtendWith(MockitoExtension.class)
class ArtikelMatchingAgentServiceTest {

    @Mock private ArtikelMatchingToolService matchingToolService;
    @Mock private ArtikelMatchingService artikelMatchingService;
    @Mock private HttpClient httpClient;
    @SuppressWarnings("rawtypes")
    @Mock private HttpResponse httpResponse;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ArtikelMatchingAgentService service;

    @BeforeEach
    void setUp() {
        service = new ArtikelMatchingAgentService(objectMapper, matchingToolService, artikelMatchingService);
        ReflectionTestUtils.setField(service, "geminiApiKey", "test-key");
        ReflectionTestUtils.setField(service, "model", "gemini-flash-latest");
        ReflectionTestUtils.setField(service, "konfidenzSchwelle", 0.85);
        ReflectionTestUtils.setField(service, "temperature", 0.2);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
    }

    @Test
    void lieferant_null_wird_geskippt() {
        JsonNode position = objectMapper.createObjectNode();
        MatchingResult result = service.matcheOderSchlageAn(position, null, null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.SKIPPED);
        assertThat(result.hinweis()).isEqualTo("Kein Lieferant");
    }

    @Test
    void fehlender_api_key_gibt_fehler() {
        ReflectionTestUtils.setField(service, "geminiApiKey", "");

        JsonNode position = objectMapper.createObjectNode();
        MatchingResult result = service.matcheOderSchlageAn(position, lieferant(), null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.FEHLER);
        assertThat(result.hinweis()).contains("API Key");
    }

    @Test
    void null_api_key_gibt_fehler() {
        ReflectionTestUtils.setField(service, "geminiApiKey", (String) null);

        MatchingResult result = service.matcheOderSchlageAn(objectMapper.createObjectNode(), lieferant(), null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.FEHLER);
    }

    @Test
    void http_fehler_gibt_fehler() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        mockHttpResponse(500, "{\"error\":\"boom\"}");

        MatchingResult result = service.matcheOderSchlageAn(objectMapper.createObjectNode(), lieferant(), null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.FEHLER);
        assertThat(result.hinweis()).contains("500");
    }

    @Test
    void leere_parts_werfen_fehler() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        mockHttpResponse(200, """
                {"candidates":[{"content":{"parts":[]}}]}
                """);

        MatchingResult result = service.matcheOderSchlageAn(objectMapper.createObjectNode(), lieferant(), null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.FEHLER);
    }

    @Test
    void text_antwort_ohne_tool_calls_fuehrt_zu_skipped() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        mockHttpResponse(200, """
                {"candidates":[{"content":{"parts":[{"text":"SKIPPED: Fracht"}]}}]}
                """);

        MatchingResult result = service.matcheOderSchlageAn(objectMapper.createObjectNode(), lieferant(), null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.SKIPPED);
        assertThat(result.hinweis()).contains("SKIPPED: Fracht");
    }

    @Test
    void mehrere_text_parts_werden_konkateniert() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        mockHttpResponse(200, """
                {"candidates":[{"content":{"parts":[
                  {"text":"Teil 1"},
                  {"text":"Teil 2"}
                ]}}]}
                """);

        MatchingResult result = service.matcheOderSchlageAn(objectMapper.createObjectNode(), lieferant(), null);

        assertThat(result.hinweis()).contains("Teil 1").contains("Teil 2");
    }

    @Test
    void text_antwort_mit_preis_update_effekt_gibt_aktualisiert() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        // Simuliere: zuerst Tool-Call, der Preis aktualisiert; dann finale Text-Antwort
        HttpResponse<String> r1 = response(200, """
                        {"candidates":[{"content":{"parts":[
                          {"functionCall":{"name":"update_artikel_preis","args":{}}}
                        ]}}]}
                        """);
        HttpResponse<String> r2 = response(200, """
                        {"candidates":[{"content":{"parts":[{"text":"UPDATED: Match perfekt"}]}}]}
                        """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(r1).thenReturn(r2);

        when(matchingToolService.executeTool(eq("update_artikel_preis"), any(JsonNode.class), any()))
                .thenAnswer(inv -> {
                    MatchingToolContext ctx = inv.getArgument(2);
                    ctx.setLastEffect(new ToolSideEffect.PreisAktualisiert(77L, new BigDecimal("2.5"), true));
                    return "OK.";
                });

        MatchingResult result = service.matcheOderSchlageAn(objectMapper.createObjectNode(), lieferant(), null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.PREIS_AKTUALISIERT);
        assertThat(result.hinweis()).contains("77").contains("externeNummerGelernt=true");
    }

    @Test
    void text_antwort_mit_vorschlag_effekt_gibt_angelegt() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        HttpResponse<String> r1 = response(200, """
                        {"candidates":[{"content":{"parts":[
                          {"functionCall":{"name":"propose_new_artikel","args":{}}}
                        ]}}]}
                        """);
        HttpResponse<String> r2 = response(200, """
                        {"candidates":[{"content":{"parts":[{"text":"PROPOSED: neu"}]}}]}
                        """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(r1).thenReturn(r2);
        when(matchingToolService.executeTool(eq("propose_new_artikel"), any(JsonNode.class), any()))
                .thenAnswer(inv -> {
                    MatchingToolContext ctx = inv.getArgument(2);
                    ctx.setLastEffect(new ToolSideEffect.VorschlagAngelegt(99L, ArtikelVorschlagTyp.NEU_ANLAGE));
                    return "OK.";
                });

        MatchingResult result = service.matcheOderSchlageAn(objectMapper.createObjectNode(), lieferant(), null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.VORSCHLAG_ANGELEGT);
        assertThat(result.hinweis()).contains("99").contains("NEU_ANLAGE");
    }

    @Test
    void lange_tool_antwort_wird_getrimmt() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        HttpResponse<String> r1 = response(200, """
                        {"candidates":[{"content":{"parts":[
                          {"functionCall":{"name":"list_werkstoffe","args":{}}}
                        ]}}]}
                        """);
        HttpResponse<String> r2 = response(200, """
                        {"candidates":[{"content":{"parts":[{"text":"SKIPPED"}]}}]}
                        """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(r1).thenReturn(r2);
        String lang = "x".repeat(9000);
        when(matchingToolService.executeTool(eq("list_werkstoffe"), any(JsonNode.class), any()))
                .thenReturn(lang);

        MatchingResult result = service.matcheOderSchlageAn(objectMapper.createObjectNode(), lieferant(), null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.SKIPPED);
    }

    @Test
    void max_iterationen_ergibt_skipped() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        // Alle Calls geben functionCall zurück → Loop wird maxIter mal durchlaufen
        HttpResponse<String> r = response(200, """
                        {"candidates":[{"content":{"parts":[
                          {"functionCall":{"name":"list_werkstoffe","args":{}}}
                        ]}}]}
                        """);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(r);
        when(matchingToolService.executeTool(anyString(), any(JsonNode.class), any()))
                .thenReturn("OK");

        MatchingResult result = service.matcheOderSchlageAn(objectMapper.createObjectNode(), lieferant(), null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.SKIPPED);
        assertThat(result.hinweis()).contains("Max-Iterationen");
    }

    @Test
    void ioexception_wird_zu_fehler() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Netzwerk weg"));

        MatchingResult result = service.matcheOderSchlageAn(objectMapper.createObjectNode(), lieferant(), null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.FEHLER);
        assertThat(result.hinweis()).contains("Netzwerk weg");
    }

    @Test
    void benutzernachricht_enthaelt_lieferant_und_jaro_kandidaten() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        Artikel kandidat = artikel(1L, "Flachstahl 30x5", "FL", werkstoff("S235JR"));
        when(artikelMatchingService.findeBesteTreffer(eq("Flachstahl 30x5"), isNull()))
                .thenReturn(List.of(kandidat));
        mockHttpResponse(200, """
                {"candidates":[{"content":{"parts":[{"text":"SKIPPED"}]}}]}
                """);

        ObjectNode position = objectMapper.createObjectNode();
        position.put("bezeichnung", "Flachstahl 30x5");
        position.put("externeArtikelnummer", "FL30X5");
        position.put("menge", "10");
        position.put("mengeneinheit", "kg");
        position.put("einzelpreis", "95.50");
        position.put("preiseinheit", "100kg");
        position.put("positionsTyp", "ARTIKEL");

        service.matcheOderSchlageAn(position, lieferant(), null);

        // Prüfe: Request-Body enthält Lieferantennamen und Jaro-Winkler-Kandidat
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        String body = extractBody(captor.getValue());
        assertThat(body).contains("Test-Lieferant");
        assertThat(body).contains("FL30X5");
        assertThat(body).contains("Flachstahl 30x5");
        assertThat(body).contains("Jaro-Winkler");
        assertThat(body).contains("S235JR");
    }

    @Test
    void jaro_winkler_exception_wird_toleriert() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        when(artikelMatchingService.findeBesteTreffer(anyString(), any()))
                .thenThrow(new RuntimeException("Index kaputt"));
        mockHttpResponse(200, """
                {"candidates":[{"content":{"parts":[{"text":"SKIPPED"}]}}]}
                """);

        ObjectNode position = objectMapper.createObjectNode();
        position.put("bezeichnung", "Test");

        MatchingResult result = service.matcheOderSchlageAn(position, lieferant(), null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.SKIPPED);
    }

    @Test
    void leere_bezeichnung_ruft_kein_jaro_winkler_auf() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        mockHttpResponse(200, """
                {"candidates":[{"content":{"parts":[{"text":"SKIPPED"}]}}]}
                """);

        ObjectNode position = objectMapper.createObjectNode();
        position.put("bezeichnung", "");

        service.matcheOderSchlageAn(position, lieferant(), null);

        org.mockito.Mockito.verifyNoInteractions(artikelMatchingService);
    }

    @Test
    void lieferant_ohne_namen_wird_toleriert() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        mockHttpResponse(200, """
                {"candidates":[{"content":{"parts":[{"text":"SKIPPED"}]}}]}
                """);

        Lieferanten lief = new Lieferanten();
        lief.setId(100L);
        // Kein Name gesetzt

        MatchingResult result = service.matcheOderSchlageAn(objectMapper.createObjectNode(), lief, null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.SKIPPED);
    }

    @Test
    void lange_agent_antwort_wird_in_hinweis_gekuerzt() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        String langeAntwort = "A".repeat(500);
        mockHttpResponse(200, String.format(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"%s\"}]}}]}", langeAntwort));

        MatchingResult result = service.matcheOderSchlageAn(objectMapper.createObjectNode(), lieferant(), null);

        assertThat(result.hinweis()).endsWith("…");
        assertThat(result.hinweis().length()).isLessThan(500);
    }

    @Test
    void leere_kandidaten_fuehren_zu_fehler() throws Exception {
        when(matchingToolService.buildFunctionDeclarations()).thenReturn(objectMapper.createArrayNode());
        // Kein candidates-Feld → partsNode ist MissingNode
        mockHttpResponse(200, "{}");

        MatchingResult result = service.matcheOderSchlageAn(objectMapper.createObjectNode(), lieferant(), null);

        assertThat(result.ergebnis()).isEqualTo(Ergebnis.FEHLER);
    }

    // ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void mockHttpResponse(int status, String body) throws Exception {
        HttpResponse<String> r = response(status, body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(r);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        HttpResponse<String> r = org.mockito.Mockito.mock(HttpResponse.class);
        org.mockito.Mockito.doReturn(status).when(r).statusCode();
        org.mockito.Mockito.doReturn(body).when(r).body();
        return r;
    }

    private String extractBody(HttpRequest req) {
        // Wir hängen einen BodySubscriber an um den Body zu extrahieren – einfacher: die Methode
        // toString() liefert nur den URI, also nutzen wir den bekannten PublisherContent.
        // Workaround: Publisher-inspection
        return req.bodyPublisher().map(bp -> {
            java.util.concurrent.Flow.Publisher<java.nio.ByteBuffer> pub = bp;
            StringBuilder sb = new StringBuilder();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            pub.subscribe(new java.util.concurrent.Flow.Subscriber<>() {
                @Override public void onSubscribe(java.util.concurrent.Flow.Subscription s) { s.request(Long.MAX_VALUE); }
                @Override public void onNext(java.nio.ByteBuffer buf) {
                    byte[] arr = new byte[buf.remaining()];
                    buf.get(arr);
                    sb.append(new String(arr, java.nio.charset.StandardCharsets.UTF_8));
                }
                @Override public void onError(Throwable t) { latch.countDown(); }
                @Override public void onComplete() { latch.countDown(); }
            });
            try { latch.await(2, java.util.concurrent.TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return sb.toString();
        }).orElse("");
    }

    private Lieferanten lieferant() {
        Lieferanten l = new Lieferanten();
        l.setId(100L);
        l.setLieferantenname("Test-Lieferant");
        return l;
    }

    private Werkstoff werkstoff(String name) {
        Werkstoff w = new Werkstoff();
        w.setName(name);
        return w;
    }

    private Artikel artikel(Long id, String name, String linie, Werkstoff w) {
        ArtikelWerkstoffe a = new ArtikelWerkstoffe();
        a.setId(id);
        a.setProduktname(name);
        a.setProduktlinie(linie);
        a.setWerkstoff(w);
        return a;
    }
}
