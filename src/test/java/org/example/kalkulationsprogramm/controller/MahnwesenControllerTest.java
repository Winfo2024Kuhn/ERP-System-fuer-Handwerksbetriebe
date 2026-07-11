package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.AutoMahnVersandService;
import org.example.kalkulationsprogramm.service.AutoMahnVersandService.MahnlaufErgebnis;
import org.example.kalkulationsprogramm.service.AutoMahnVersandService.MahnlaufStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;

/**
 * Slice-Test fuer den manuellen Mahn-Lauf-Trigger. Die Zugriffsbeschraenkung
 * (nur Admin) liegt in der SecurityConfig und ist hier bewusst abgeschaltet
 * (addFilters = false, Projekt-Konvention) — getestet wird das
 * Antwort-Mapping der vier Ergebnis-Faelle.
 */
@WebMvcTest(MahnwesenController.class)
@AutoConfigureMockMvc(addFilters = false)
class MahnwesenControllerTest
{
    @Autowired MockMvc mockMvc;

    @MockBean AutoMahnVersandService autoMahnVersandService;

    @Test
    @DisplayName("Lauf mit Versand: meldet Anzahl und Erfolgs-Message")
    void lauf_mitVersand_meldetAnzahl() throws Exception
    {
        when(autoMahnVersandService.fuehreMahnlaufAus())
                .thenReturn(new MahnlaufErgebnis(MahnlaufStatus.AUSGEFUEHRT, 3, 0));

        mockMvc.perform(post("/api/mahnwesen/lauf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.status").value("AUSGEFUEHRT"))
                .andExpect(jsonPath("$.versendet").value(3))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("3 Zahlungserinnerung(en)")));
    }

    @Test
    @DisplayName("Lauf ohne faellige Rechnungen: versendet=0 mit erklaerender Message")
    void lauf_ohneFaelligeRechnungen_meldetNull() throws Exception
    {
        when(autoMahnVersandService.fuehreMahnlaufAus())
                .thenReturn(new MahnlaufErgebnis(MahnlaufStatus.AUSGEFUEHRT, 0, 0));

        mockMvc.perform(post("/api/mahnwesen/lauf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versendet").value(0))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("keine Rechnung")));
    }

    @Test
    @DisplayName("Mahnverfahren ausgeschaltet: weist auf Einstellungen hin")
    void lauf_verfahrenAusgeschaltet_weistDaraufHin() throws Exception
    {
        when(autoMahnVersandService.fuehreMahnlaufAus())
                .thenReturn(new MahnlaufErgebnis(MahnlaufStatus.VERFAHREN_INAKTIV, 0, 0));

        mockMvc.perform(post("/api/mahnwesen/lauf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERFAHREN_INAKTIV"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("ausgeschaltet")));
    }

    @Test
    @DisplayName("Lauf mit Fehlern: meldet Fehleranzahl statt falscher Entwarnung")
    void lauf_mitFehlern_meldetFehleranzahl() throws Exception
    {
        when(autoMahnVersandService.fuehreMahnlaufAus())
                .thenReturn(new MahnlaufErgebnis(MahnlaufStatus.AUSGEFUEHRT, 0, 2));

        mockMvc.perform(post("/api/mahnwesen/lauf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.versendet").value(0))
                .andExpect(jsonPath("$.fehlgeschlagen").value(2))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("2 Rechnung(en)")))
                .andExpect(jsonPath("$.message").value(
                        Matchers.not(Matchers.containsString("keine Rechnung"))));
    }

    @Test
    @DisplayName("Lauf laeuft bereits: 409 Conflict, kein zweiter Start")
    void lauf_laeuftBereits_liefert409() throws Exception
    {
        when(autoMahnVersandService.fuehreMahnlaufAus())
                .thenReturn(new MahnlaufErgebnis(MahnlaufStatus.LAEUFT_BEREITS, 0, 0));

        mockMvc.perform(post("/api/mahnwesen/lauf"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.status").value("LAEUFT_BEREITS"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("läuft gerade schon")));
    }
}
