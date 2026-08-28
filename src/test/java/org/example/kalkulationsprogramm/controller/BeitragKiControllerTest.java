package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragKiAnfrage;
import org.example.kalkulationsprogramm.dto.Beitraege.BeitragKiEntwurf;
import org.example.kalkulationsprogramm.service.BeitragKiService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * Deckt {@link BeitragKiController} ab: Happy-Path und Fehlerfall fuer
 * {@code POST /api/beitraege/ki/entwurf}, dazu die Sicherheits-
 * Pflichtcheckliste aus TESTING_SECURITY.md (XSS, ueberlange Eingabe,
 * ungueltige Projekt-IDs).
 *
 * <p>{@link BeitragKiService} ist hier komplett gemockt. Getestet wird nur
 * die Controller-Schicht: Multipart-Bindung, Bean-Validation von
 * {@code projektId} (@Positive) und die beiden {@code @ExceptionHandler} in
 * {@link BeitragKiController}.
 */
@WebMvcTest(BeitragKiController.class)
@AutoConfigureMockMvc(addFilters = false)
class BeitragKiControllerTest {

    private static final String URL = "/api/beitraege/ki/entwurf";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BeitragKiService beitragKiService;

    private MockMultipartFile anfrageTeil(BeitragKiAnfrage anfrage) throws Exception {
        return new MockMultipartFile("anfrage", "anfrage.json", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsString(anfrage).getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile bildTeil() {
        return new MockMultipartFile("bilder", "baustelle.jpg", MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3, 4});
    }

    private BeitragKiEntwurf entwurf() {
        return new BeitragKiEntwurf("Neuer Titel", "Kurzfassung", "Text des Beitrags.", "Vorschlag erstellt.");
    }

    // --- Happy-Path ---

    @Test
    void entwurf_happyPathMitAnfrageUndBild_liefertVorschlag() throws Exception {
        given(beitragKiService.erzeugeEntwurf(any(BeitragKiAnfrage.class), any())).willReturn(entwurf());

        BeitragKiAnfrage anfrage = new BeitragKiAnfrage(1L, null, "Alter Titel", "Alter Text");

        mockMvc.perform(multipart(URL).file(anfrageTeil(anfrage)).file(bildTeil()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titel").value("Neuer Titel"))
                .andExpect(jsonPath("$.kurzbeschreibung").value("Kurzfassung"))
                .andExpect(jsonPath("$.text").value("Text des Beitrags."))
                .andExpect(jsonPath("$.antwort").value("Vorschlag erstellt."));
    }

    // --- Fehlerfall ---

    @Test
    void entwurf_dienstWirftIllegalState_liefertSauberen502OhneInterna() throws Exception {
        given(beitragKiService.erzeugeEntwurf(any(BeitragKiAnfrage.class), any()))
                .willThrow(new IllegalStateException(
                        "Verbindung zu internal-gemini-proxy.intern:8443 fehlgeschlagen, Stacktrace folgt"));

        BeitragKiAnfrage anfrage = new BeitragKiAnfrage(1L, null, null, null);

        mockMvc.perform(multipart(URL).file(anfrageTeil(anfrage)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Die KI konnte gerade keinen Vorschlag erstellen."))
                .andExpect(content().string(not(containsString("internal-gemini-proxy"))))
                .andExpect(content().string(not(containsString("Stacktrace"))));
    }

    // --- Sicherheits-Pflichtcheckliste (TESTING_SECURITY.md) ---

    @Test
    void entwurf_xssInTitelUndText_wirdAlsReinerTextDurchgereicht() throws Exception {
        ArgumentCaptor<BeitragKiAnfrage> captor = ArgumentCaptor.forClass(BeitragKiAnfrage.class);
        given(beitragKiService.erzeugeEntwurf(captor.capture(), any())).willReturn(entwurf());

        String script = "<script>alert(1)</script>";
        BeitragKiAnfrage anfrage = new BeitragKiAnfrage(1L, null, script, script);

        // Der Endpunkt gibt aktuellerTitel/aktuellerText nie an den Client
        // zurueck (die Antwort kommt komplett vom - hier gemockten - KI-
        // Dienst). Wichtig ist deshalb: kein Serverfehler, und der Wert kommt
        // unveraendert beim Dienst an statt irgendwo kaputt escaped zu werden.
        mockMvc.perform(multipart(URL).file(anfrageTeil(anfrage)))
                .andExpect(status().isOk());

        assertThat(captor.getValue().aktuellerTitel()).isEqualTo(script);
        assertThat(captor.getValue().aktuellerText()).isEqualTo(script);
    }

    @Test
    void entwurf_ueberlangerText_wirdTrotzdemAngenommen() throws Exception {
        given(beitragKiService.erzeugeEntwurf(any(BeitragKiAnfrage.class), any())).willReturn(entwurf());

        String ueberlang = "A".repeat(10_001);
        BeitragKiAnfrage anfrage = new BeitragKiAnfrage(1L, null, "Titel", ueberlang);

        mockMvc.perform(multipart(URL).file(anfrageTeil(anfrage)))
                .andExpect(status().isOk());
    }

    @Test
    void entwurf_projektId0_wirdAlsUngueltigAbgelehnt() throws Exception {
        BeitragKiAnfrage anfrage = new BeitragKiAnfrage(0L, null, null, null);

        mockMvc.perform(multipart(URL).file(anfrageTeil(anfrage)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(beitragKiService);
    }

    @Test
    void entwurf_projektIdNegativ_wirdAlsUngueltigAbgelehnt() throws Exception {
        BeitragKiAnfrage anfrage = new BeitragKiAnfrage(-1L, null, null, null);

        mockMvc.perform(multipart(URL).file(anfrageTeil(anfrage)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(beitragKiService);
    }

    @Test
    void entwurf_projektIdMaxValue_gibtFehlermeldungWoertlichZurueck() throws Exception {
        given(beitragKiService.erzeugeEntwurf(any(BeitragKiAnfrage.class), any()))
                .willThrow(new IllegalArgumentException("Projekt nicht gefunden: " + Long.MAX_VALUE));

        BeitragKiAnfrage anfrage = new BeitragKiAnfrage(Long.MAX_VALUE, null, null, null);

        // Dokumentiert bewusstes Verhalten: BeitragKiController.handleUngueltig
        // gibt ex.getMessage() woertlich an den Client zurueck. Nur Admins
        // erreichen diesen Endpunkt (SecurityConfig.java:224), das Risiko ist
        // klein - das soll aber eine bewusste Entscheidung bleiben und nicht
        // unbemerkt kippen.
        mockMvc.perform(multipart(URL).file(anfrageTeil(anfrage)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Projekt nicht gefunden: " + Long.MAX_VALUE));
    }
}
