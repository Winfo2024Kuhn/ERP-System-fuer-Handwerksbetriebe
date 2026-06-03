package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.controller.advice.RestExceptionHandler;
import org.example.kalkulationsprogramm.domain.DokumentFreigabe;
import org.example.kalkulationsprogramm.domain.FreigabeStatus;
import org.example.kalkulationsprogramm.dto.Freigabe.FreigabeAkzeptierenRequest;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.DokumentFreigabeService;
import org.example.kalkulationsprogramm.util.ConstraintMessageResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-Tests für POST /api/internal/freigabe/{uuid}/akzeptieren.
 *
 * <p>Beweissicherungs-Erweiterung: Vor- und Nachname sind seit V317 Pflicht.
 * Validierungsfehler werden vom {@link RestExceptionHandler} als HTTP 400 mit
 * Feldnamen ausgeliefert — getestet werden hier Happy-Path und die typischen
 * Verstöße (fehlend, zu kurz).</p>
 */
@WebMvcTest(FreigabeInternalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(RestExceptionHandler.class)
class FreigabeInternalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DokumentFreigabeService freigabeService;

    @MockBean
    private DateiSpeicherService dateiSpeicherService;

    /**
     * Wird vom {@link RestExceptionHandler}-Konstruktor verlangt (nullable). Wir
     * geben einen leeren Mock rein, damit Spring den Bean deterministisch auflöst —
     * sonst hängt das Verhalten an der konkreten Spring-Version, ob {@code @Nullable}
     * auf Konstruktorparametern für eine Null-Injection ausreicht.
     */
    @MockBean
    private ConstraintMessageResolver constraintMessageResolver;

    @Test
    void akzeptieren_gueltigerRequest_gibt200MitUnterzeichnerNameZurueck() throws Exception {
        DokumentFreigabe saved = new DokumentFreigabe();
        saved.setUuid("uuid-ok");
        saved.setDokumentNummer("ANG-2026-0001");
        saved.setDokumentArt("Angebot");
        saved.setStatus(FreigabeStatus.ACCEPTED);
        saved.setAkzeptiertAm(LocalDateTime.of(2026, 5, 12, 10, 30));
        saved.setHashAcceptance("hash-xyz");
        saved.setUnterzeichnerVorname("Max");
        saved.setUnterzeichnerNachname("Mustermann");
        saved.setUnterzeichnerName("Max Mustermann");
        given(freigabeService.akzeptiere(
                eq("uuid-ok"), anyString(), anyString(), anyString(),
                eq("Max"), eq("Mustermann"), eq("Max Mustermann"), any()))
                .willReturn(saved);

        String json = objectMapper.writeValueAsString(validRequest());

        mockMvc.perform(post("/api/internal/freigabe/uuid-ok/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value("uuid-ok"))
                .andExpect(jsonPath("$.unterzeichnerName").value("Max Mustermann"));
    }

    @Test
    void akzeptieren_reichtAusgewaehlteAlternativenAnService() throws Exception {
        DokumentFreigabe saved = new DokumentFreigabe();
        saved.setUuid("uuid-alt");
        saved.setDokumentNummer("ANG-2026-0002");
        saved.setDokumentArt("Angebot");
        saved.setStatus(FreigabeStatus.ACCEPTED);
        saved.setAkzeptiertAm(LocalDateTime.of(2026, 5, 12, 10, 30));
        saved.setUnterzeichnerName("Max Mustermann");
        given(freigabeService.akzeptiere(
                eq("uuid-alt"), anyString(), anyString(), anyString(),
                eq("Max"), eq("Mustermann"), eq("Max Mustermann"), any()))
                .willReturn(saved);

        FreigabeAkzeptierenRequest req = validRequest();
        req.setAusgewaehlteAlternativen(List.of("alt-1", "alt-2"));
        String json = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/internal/freigabe/uuid-alt/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(freigabeService).akzeptiere(
                eq("uuid-alt"), anyString(), anyString(), anyString(),
                eq("Max"), eq("Mustermann"), eq("Max Mustermann"), captor.capture());
        assertThat(captor.getValue()).containsExactly("alt-1", "alt-2");
    }

    @Test
    void akzeptieren_ohneVorname_gibt400UndZeigtFeldnamen() throws Exception {
        FreigabeAkzeptierenRequest req = validRequest();
        req.setVorname(null);
        String json = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/internal/freigabe/uuid-x/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='vorname')]").exists());
    }

    @Test
    void akzeptieren_zuKurzerNachname_gibt400() throws Exception {
        FreigabeAkzeptierenRequest req = validRequest();
        req.setNachname("X");
        String json = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/internal/freigabe/uuid-x/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='nachname')]").exists());
    }

    @Test
    void akzeptieren_leererUnterzeichnerName_gibt400() throws Exception {
        FreigabeAkzeptierenRequest req = validRequest();
        req.setUnterzeichnerName("");
        String json = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/internal/freigabe/uuid-x/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[?(@.field=='unterzeichnerName')]").exists());
    }

    @Test
    void akzeptieren_ohneBestaetigung_gibt400() throws Exception {
        FreigabeAkzeptierenRequest req = validRequest();
        req.setBestaetigung(false);
        String json = objectMapper.writeValueAsString(req);

        mockMvc.perform(post("/api/internal/freigabe/uuid-x/akzeptieren")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    private static FreigabeAkzeptierenRequest validRequest() {
        FreigabeAkzeptierenRequest req = new FreigabeAkzeptierenRequest();
        req.setEmail("max@mustermann.de");
        req.setVorname("Max");
        req.setNachname("Mustermann");
        req.setUnterzeichnerName("Max Mustermann");
        req.setBestaetigung(true);
        req.setClientIp("1.2.3.4");
        req.setUserAgent("Mozilla/5.0 Test");
        return req;
    }
}
