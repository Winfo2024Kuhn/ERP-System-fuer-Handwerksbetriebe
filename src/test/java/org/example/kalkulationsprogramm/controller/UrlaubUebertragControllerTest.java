package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.service.UrlaubUebertragService;
import org.example.kalkulationsprogramm.service.UrlaubUebertragService.Stand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** Resturlaub von Hand festlegen: Happy Path und Fehlerfälle. Dummy-Daten. */
@WebMvcTest(UrlaubUebertragController.class)
@AutoConfigureMockMvc(addFilters = false)
class UrlaubUebertragControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UrlaubUebertragService service;

    @Test
    void ladenLiefertStand() throws Exception {
        when(service.laden(1L, 2027)).thenReturn(
                new Stand(2027, new BigDecimal("4.5"), new BigDecimal("4.5"), false, "2027-03-31",
                        new BigDecimal("20"), "MINDESTURLAUB_EINTRITT", "2026-06-01"));

        mockMvc.perform(get("/api/urlaub/uebertrag").param("mitarbeiterId", "1").param("jahr", "2027"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tage").value(4.5))
                .andExpect(jsonPath("$.berechnet").value(4.5))
                .andExpect(jsonPath("$.manuell").value(false))
                .andExpect(jsonPath("$.verfallsdatum").value("2027-03-31"))
                .andExpect(jsonPath("$.anspruch").value(20))
                .andExpect(jsonPath("$.anspruchArt").value("MINDESTURLAUB_EINTRITT"))
                .andExpect(jsonPath("$.eintrittsdatum").value("2026-06-01"));
    }

    @Test
    void speichernUndZuruecksetzen() throws Exception {
        when(service.speichern(1L, 2027, new BigDecimal("2.5"), null)).thenReturn(
                new Stand(2027, new BigDecimal("2.5"), new BigDecimal("4"), true, null, new BigDecimal("30"), "VOLL", null));

        mockMvc.perform(put("/api/urlaub/uebertrag").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mitarbeiterId\":1,\"jahr\":2027,\"tage\":2.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manuell").value(true))
                .andExpect(jsonPath("$.berechnet").value(4));

        mockMvc.perform(put("/api/urlaub/uebertrag").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mitarbeiterId\":1,\"jahr\":2027,\"tage\":null}"));
        verify(service).speichern(eq(1L), eq(2027), isNull(), isNull());
    }

    @Test
    void fehlerKommenAlsVerstaendlicheMeldung() throws Exception {
        when(service.speichern(anyLong(), anyInt(), any(), any())).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Resturlaub muss zwischen 0 und 365 Tagen liegen."));

        mockMvc.perform(put("/api/urlaub/uebertrag").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mitarbeiterId\":1,\"jahr\":2027,\"tage\":-1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/urlaub/uebertrag").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mitarbeiterId\":1,\"jahr\":2027,\"tage\":\"<script>alert(1)</script>\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/urlaub/uebertrag").param("mitarbeiterId", "'; DROP TABLE x; --").param("jahr", "2027"))
                .andExpect(status().isBadRequest());
    }
}
