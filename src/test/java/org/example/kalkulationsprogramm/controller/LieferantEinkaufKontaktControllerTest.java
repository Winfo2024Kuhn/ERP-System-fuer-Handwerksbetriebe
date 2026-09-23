package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.LieferantEinkaufKontaktService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LieferantEinkaufKontaktControllerTest {
    private LieferantEinkaufKontaktService service;
    private EinkaufBerechtigungService berechtigungen;
    private MockMvc mvc;

    @BeforeEach void setup() {
        service = mock(LieferantEinkaufKontaktService.class);
        berechtigungen = mock(EinkaufBerechtigungService.class);
        mvc = MockMvcBuilders.standaloneSetup(new LieferantEinkaufKontaktController(service, berechtigungen)).build();
    }

    @Test void putErfordertBearbeitungsrechtUndAktuelleIdPasstZuPfad() throws Exception {
        when(berechtigungen.verlange(any(), any())).thenReturn(31L);
        mvc.perform(put("/api/lieferanten/7/einkauf-kontakte/14")
                .principal(new UsernamePasswordAuthenticationToken("user", "", java.util.List.of()))
                .contentType("application/json")
                .content("{\"id\":14,\"version\":3,\"name\":\"Max Mustermann\",\"anrede\":\"Herr\",\"email\":\"test@example.com\",\"standardAnfrage\":true,\"standardBestellung\":false,\"aktiv\":true}"))
                .andExpect(status().isOk());
        verify(berechtigungen).verlange(any(), eq(EinkaufBerechtigung.BEARBEITEN));
        verify(service).speichern(eq(7L), any(), eq(31L));
    }

    @Test void kontaktIdAusAnderemPfadWirdAbgelehnt() throws Exception {
        when(berechtigungen.verlange(any(), any())).thenReturn(31L);
        mvc.perform(put("/api/lieferanten/7/einkauf-kontakte/14")
                .principal(new UsernamePasswordAuthenticationToken("user", "", java.util.List.of()))
                .contentType("application/json")
                .content("{\"id\":99,\"version\":3,\"name\":\"Max Mustermann\",\"email\":\"test@example.com\",\"standardAnfrage\":false,\"standardBestellung\":false,\"aktiv\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
