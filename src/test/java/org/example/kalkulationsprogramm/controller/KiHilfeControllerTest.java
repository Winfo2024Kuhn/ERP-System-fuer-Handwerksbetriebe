package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.service.KiHilfeService;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatMessage;
import org.example.kalkulationsprogramm.service.KiHilfeService.ChatResult;
import org.example.kalkulationsprogramm.service.KiHilfeService.PageContext;
import org.example.kalkulationsprogramm.service.KiHilfeService.SourceLink;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deckt {@link KiHilfeController} ab: die Antwort als Server-Sent Events (Schritte, dann Antwort
 * oder Fehler) und die Pruefung der Eingaben vor dem Start, inkl. der Sicherheits-Pflichtcheckliste
 * aus TESTING_SECURITY.md. Der Dienst ist gemockt; die Anmeldung pruefen die Filterketten.
 */
@WebMvcTest(KiHilfeController.class)
// print = NONE: Der Standard-Ausgabehandler geht im Test-Thread alle Response-Header durch, während der
// Hintergrund-Thread des Streams beim ersten Senden schon Header setzt - das gab ab und zu eine
// ConcurrentModificationException (etwa 1 von 1500 Läufen).
@AutoConfigureMockMvc(addFilters = false, print = MockMvcPrint.NONE)
class KiHilfeControllerTest {

    private static final String URL = "/api/ki-hilfe/chat";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KiHilfeController controller;

    @MockBean
    private KiHilfeService kiHilfeService;

    private String anfrage(List<Map<String, String>> nachrichten, Map<String, Object> kontext) throws Exception {
        return objectMapper.writeValueAsString(kontext == null
                ? Map.of("messages", nachrichten)
                : Map.of("messages", nachrichten, "context", kontext));
    }

    private static List<Map<String, String>> frage(String text) {
        return List.of(Map.of("role", "user", "text", text));
    }

    /** Startet die Anfrage und wartet, bis der Hintergrund-Thread den Stream beendet hat. */
    private String streamLesen(String body) throws Exception {
        MvcResult result = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(5_000);
        // Header erst lesen, wenn der Hintergrund-Thread fertig ist - vorher schreibt er evtl. noch
        // andere Header (Content-Type) in dieselbe Map.
        assertThat(result.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-cache");
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    @Test
    void chat_sendetSchritteUndDannDieAntwort() throws Exception {
        given(kiHilfeService.chat(anyList(), any(), any())).willAnswer(aufruf -> {
            Consumer<String> schritt = aufruf.getArgument(2);
            schritt.accept("Passende Stellen im Programm gesucht");
            schritt.accept("Schaut nach: Wie man eine Rechnung storniert");
            return new ChatResult("Klicke auf **Stornieren**.",
                    List.of(new SourceLink("DIN", "https://example.org/din")),
                    List.of("Passende Stellen im Programm gesucht", "Schaut nach: Wie man eine Rechnung storniert"));
        });

        String stream = streamLesen(anfrage(frage("Wie storniere ich eine Rechnung?"),
                Map.of("route", "/projekte/7", "pageTitle", "Projekte", "visibleContent", "Max Mustermann")));

        assertThat(stream)
                .containsSubsequence(
                        "event:schritt", "data:{\"text\":\"Passende Stellen im Programm gesucht\"}",
                        "event:schritt", "data:{\"text\":\"Schaut nach: Wie man eine Rechnung storniert\"}",
                        "event:antwort")
                .contains("\"reply\":\"Klicke auf **Stornieren**.\"", "\"url\":\"https://example.org/din\"",
                        "\"schritte\":[\"Passende Stellen im Programm gesucht\"")
                .doesNotContain("event:fehler");

        ArgumentCaptor<List<ChatMessage>> nachrichten = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<PageContext> seite = ArgumentCaptor.forClass(PageContext.class);
        verify(kiHilfeService).chat(nachrichten.capture(), seite.capture(), any());
        assertThat(nachrichten.getValue()).containsExactly(new ChatMessage("user", "Wie storniere ich eine Rechnung?"));
        assertThat(seite.getValue().route()).isEqualTo("/projekte/7");
        assertThat(seite.getValue().visibleContent()).isEqualTo("Max Mustermann");
    }

    @Test
    void chat_ohneSeitenkontext_reichtNullWeiter() throws Exception {
        given(kiHilfeService.chat(anyList(), any(), any())).willReturn(new ChatResult("Hallo", List.of(), List.of()));

        assertThat(streamLesen(anfrage(frage("Hallo"), null))).contains("event:antwort", "\"reply\":\"Hallo\"");
        verify(kiHilfeService).chat(anyList(), org.mockito.ArgumentMatchers.isNull(), any());
    }

    @Test
    void chat_dienstScheitert_sendetFehlerOhneInterna() throws Exception {
        given(kiHilfeService.chat(anyList(), any(), any()))
                .willThrow(new IOException("Gemini KI-Hilfe API Error 403: geheimer-schluessel"));

        String stream = streamLesen(anfrage(frage("Frage"), null));

        assertThat(stream).contains("event:fehler", KiHilfeController.FEHLER_ALLGEMEIN)
                .doesNotContain("geheimer-schluessel", "event:antwort");
    }

    @Test
    void chat_unterbrochen_sendetFehler() throws Exception {
        given(kiHilfeService.chat(anyList(), any(), any())).willThrow(new InterruptedException());

        assertThat(streamLesen(anfrage(frage("Frage"), null))).contains("event:fehler", KiHilfeController.FEHLER_ALLGEMEIN);
    }

    // --- Pruefung vor dem Start: 400 mit verstaendlicher Meldung, der Dienst wird nicht gerufen ---

    @Test
    void chat_ohneNachrichten_400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"messages\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Keine Nachricht angegeben"));
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(kiHilfeService);
    }

    @Test
    void chat_zuVieleNachrichten_400() throws Exception {
        List<Map<String, String>> viele = new ArrayList<>();
        for (int i = 0; i <= KiHilfeController.MAX_NACHRICHTEN; i++) viele.add(Map.of("role", "user", "text", "x"));

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(anfrage(viele, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Zu viele Nachrichten"));
        verifyNoInteractions(kiHilfeService);
    }

    @Test
    void chat_leereOderUeberlangeNachricht_400() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(anfrage(frage("   "), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Leere Nachricht"));
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(anfrage(frage("x".repeat(KiHilfeController.MAX_ZEICHEN + 1)), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nachricht zu lang (max. 5000 Zeichen)"));
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\"}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(kiHilfeService);
    }

    @Test
    void chat_ungueltigeRolle_400OhneDieEingabeZuSpiegeln() throws Exception {
        String xss = "<script>alert(1)</script>";

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(anfrage(List.of(Map.of("role", xss, "text", "Hallo")), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ungültige Rolle"))
                .andExpect(content().string(not(containsString("<script>"))));
        verifyNoInteractions(kiHilfeService);
    }

    @Test
    void chat_sqlUndHtmlInDerFrage_gehenUnveraendertAlsTextAnDenDienst() throws Exception {
        String frage = "'; DROP TABLE kunde; -- <img src=x onerror=alert(1)>";
        given(kiHilfeService.chat(anyList(), any(), any())).willReturn(new ChatResult("ok", List.of(), List.of()));

        assertThat(streamLesen(anfrage(frage(frage), null))).contains("event:antwort");
        verify(kiHilfeService).chat(org.mockito.ArgumentMatchers.eq(List.of(new ChatMessage("user", frage))), any(), any());
    }

    // --- Seitenkontext: Route muss passen, lange Texte werden gekuerzt ---

    @Test
    void chat_ungueltigeRoute_400() throws Exception {
        for (String route : List.of("javascript:alert(1)", "/projekte\r\nGEFAELSCHT", "/" + "x".repeat(KiHilfeController.MAX_ROUTE))) {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                            .content(anfrage(frage("Hallo"), Map.of("route", route))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Ungültige Seite"));
        }
        verifyNoInteractions(kiHilfeService);
    }

    @Test
    void chat_langeSeitentexteWerdenGekuerzt() throws Exception {
        given(kiHilfeService.chat(anyList(), any(), any())).willReturn(new ChatResult("ok", List.of(), List.of()));

        streamLesen(anfrage(frage("Hallo"), Map.of("route", "/projekte", "pageTitle", "T".repeat(500),
                "visibleContent", "v".repeat(20_000), "errorMessages", "e".repeat(5_000))));

        ArgumentCaptor<PageContext> seite = ArgumentCaptor.forClass(PageContext.class);
        verify(kiHilfeService).chat(anyList(), seite.capture(), any());
        assertThat(seite.getValue().pageTitle()).hasSize(KiHilfeController.MAX_SEITENTITEL);
        assertThat(seite.getValue().visibleContent()).hasSize(KiHilfeController.MAX_SICHTBARER_INHALT);
        assertThat(seite.getValue().errorMessages()).hasSize(KiHilfeController.MAX_FEHLERMELDUNGEN);
    }

    // --- Last, Zeitlimit, Abbruch ---

    @Test
    void chat_zuVieleGleichzeitig_429() throws Exception {
        Semaphore plaetze = (Semaphore) ReflectionTestUtils.getField(controller, "plaetze");
        int frei = plaetze.drainPermits();
        try {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(anfrage(frage("Hallo"), null)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.message").value(KiHilfeController.FEHLER_AUSGELASTET));
            verifyNoInteractions(kiHilfeService);
        } finally {
            plaetze.release(frei);
        }
    }

    /** Dienst, der haengt, bis sein Thread unterbrochen wird; meldet die Unterbrechung. */
    private CountDownLatch haengenderDienst() throws Exception {
        CountDownLatch unterbrochen = new CountDownLatch(1);
        given(kiHilfeService.chat(anyList(), any(), any())).willAnswer(aufruf -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                unterbrochen.countDown();
                throw e;
            }
            return new ChatResult("zu spaet", List.of(), List.of());
        });
        return unterbrochen;
    }

    private static List<AsyncListener> zuhoerer(MvcResult result) {
        return ((MockAsyncContext) result.getRequest().getAsyncContext()).getListeners();
    }

    @Test
    void chat_gesamtfristDesDienstes_meldetZuLange() throws Exception {
        given(kiHilfeService.chat(anyList(), any(), any())).willThrow(new KiHilfeService.ZeitlimitErreicht());

        assertThat(streamLesen(anfrage(frage("Frage"), null)))
                .contains("event:fehler", KiHilfeController.FEHLER_ZU_LANGE);
    }

    @Test
    void chat_streamZeitlimit_brichtDenDienstAb() throws Exception {
        CountDownLatch unterbrochen = haengenderDienst();
        MvcResult result = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(anfrage(frage("Hallo"), null)))
                .andExpect(request().asyncStarted())
                .andReturn();

        for (AsyncListener l : zuhoerer(result)) {
            l.onTimeout(new AsyncEvent(result.getRequest().getAsyncContext()));
        }

        assertThat(unterbrochen.await(5, TimeUnit.SECONDS)).as("Dienst unterbrochen").isTrue();
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("event:antwort");
    }

    @Test
    void chat_browserWeg_brichtDenDienstAbUndGibtDenPlatzFrei() throws Exception {
        CountDownLatch unterbrochen = haengenderDienst();
        Semaphore plaetze = (Semaphore) ReflectionTestUtils.getField(controller, "plaetze");
        MvcResult result = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(anfrage(frage("Hallo"), null)))
                .andExpect(request().asyncStarted())
                .andReturn();

        for (AsyncListener l : zuhoerer(result)) {
            l.onError(new AsyncEvent(result.getRequest().getAsyncContext(), new IOException("Broken pipe")));
        }

        assertThat(unterbrochen.await(5, TimeUnit.SECONDS)).as("Dienst unterbrochen").isTrue();
        for (int i = 0; i < 50 && plaetze.availablePermits() < KiHilfeController.MAX_GLEICHZEITIG; i++) Thread.sleep(20);
        assertThat(plaetze.availablePermits()).isEqualTo(KiHilfeController.MAX_GLEICHZEITIG);
    }

    @Test
    void chat_nachDemZeitlimit_kommtKeinSchrittMehrDurch() throws Exception {
        CountDownLatch schrittAbgelehnt = new CountDownLatch(1);
        given(kiHilfeService.chat(anyList(), any(), any())).willAnswer(aufruf -> {
            Consumer<String> schritt = aufruf.getArgument(2);
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                try {
                    schritt.accept("Zu spaeter Schritt");
                } catch (RuntimeException verbindungZu) {
                    schrittAbgelehnt.countDown();
                    throw verbindungZu;
                }
            }
            return new ChatResult("nie", List.of(), List.of());
        });
        MvcResult result = mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(anfrage(frage("Hallo"), null)))
                .andExpect(request().asyncStarted())
                .andReturn();

        for (AsyncListener l : zuhoerer(result)) {
            l.onTimeout(new AsyncEvent(result.getRequest().getAsyncContext()));
        }

        assertThat(schrittAbgelehnt.await(5, TimeUnit.SECONDS)).as("Schritt nach dem Ende abgelehnt").isTrue();
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain("Zu spaeter Schritt");
    }
}
