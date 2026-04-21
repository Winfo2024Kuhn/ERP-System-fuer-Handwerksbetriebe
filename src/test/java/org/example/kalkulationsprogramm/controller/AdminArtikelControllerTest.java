package org.example.kalkulationsprogramm.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.service.ArtikelDurchschnittspreisService;
import org.example.kalkulationsprogramm.service.ArtikelDurchschnittspreisService.BackfillErgebnis;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminArtikelController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminArtikelControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ArtikelDurchschnittspreisService durchschnittspreisService;

    @Test
    void backfillDurchschnittspreis_liefertErgebnisAlsJson() throws Exception {
        given(durchschnittspreisService.backfillAlle())
                .willReturn(new BackfillErgebnis(1243, 17, 582L));

        mockMvc.perform(post("/api/admin/artikel/durchschnittspreis/backfill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verarbeitet").value(1243))
                .andExpect(jsonPath("$.uebersprungen").value(17))
                .andExpect(jsonPath("$.dauerMs").value(582));
    }

    @Test
    void backfillDurchschnittspreis_leereDatenbank_liefertNullen() throws Exception {
        given(durchschnittspreisService.backfillAlle())
                .willReturn(new BackfillErgebnis(0, 0, 3L));

        mockMvc.perform(post("/api/admin/artikel/durchschnittspreis/backfill"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verarbeitet").value(0))
                .andExpect(jsonPath("$.uebersprungen").value(0))
                .andExpect(jsonPath("$.dauerMs").value(3));
    }
}
