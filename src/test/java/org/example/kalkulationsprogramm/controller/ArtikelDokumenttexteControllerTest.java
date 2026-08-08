package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.example.kalkulationsprogramm.service.ArtikelImportService;
import org.example.kalkulationsprogramm.service.ArtikelMatchingService;
import org.example.kalkulationsprogramm.service.ArtikelServiceContract;
import org.example.kalkulationsprogramm.service.KategorieService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ArtikelController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArtikelDokumenttexteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ArtikelServiceContract artikelService;

    @MockBean
    private ArtikelImportService artikelImportService;

    @MockBean
    private LieferantenRepository lieferantenRepository;

    @MockBean
    private WerkstoffRepository werkstoffRepository;

    @MockBean
    private ArtikelMatchingService artikelMatchingService;

    @MockBean
    private KategorieService kategorieService;

    @Test
    void schreibtDieTexteUndAntwortetMitDemArtikel() throws Exception {
        Artikel gespeichert = new Artikel();
        gespeichert.setId(7L);
        gespeichert.setKurzbeschreibung("Rundrohr 42,4 Lager");
        when(artikelService.aktualisiereDokumenttexte(eq(7L), any())).thenReturn(gespeichert);

        mockMvc.perform(patch("/api/artikel/7/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kurzbeschreibung":"Rundrohr 42,4 Lager",
                                 "beschreibung":"<p>Rundrohr</p>",
                                 "verkaufsaufschlagProzent":40.00}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kurzbeschreibung").value("Rundrohr 42,4 Lager"));
    }

    @Test
    void antwortetMit404BeiUnbekannterId() throws Exception {
        when(artikelService.aktualisiereDokumenttexte(eq(999L), any()))
                .thenThrow(new IllegalArgumentException("Artikel nicht gefunden: 999"));

        mockMvc.perform(patch("/api/artikel/999/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kurzbeschreibung\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void antwortetMit400BeiUeberlangerEingabe() throws Exception {
        when(artikelService.aktualisiereDokumenttexte(eq(7L), any()))
                .thenThrow(new IllegalArgumentException("Die Beschreibung darf hoechstens 10000 Zeichen lang sein."));

        mockMvc.perform(patch("/api/artikel/7/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beschreibung\":\"" + "x".repeat(10_001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reichtSkriptMarkupAnDenServiceWeiterDerEsSaeubert() throws Exception {
        Artikel gespeichert = new Artikel();
        gespeichert.setId(7L);
        gespeichert.setBeschreibung("<p>Rohr</p>");
        when(artikelService.aktualisiereDokumenttexte(eq(7L), any())).thenReturn(gespeichert);

        mockMvc.perform(patch("/api/artikel/7/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beschreibung\":\"<p>Rohr</p><script>alert(1)</script>\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.beschreibung").value(not(containsString("script"))));
    }

    @Test
    void antwortetMit404BeiNegativerId() throws Exception {
        when(artikelService.aktualisiereDokumenttexte(eq(-1L), any()))
                .thenThrow(new IllegalArgumentException("Artikel nicht gefunden: -1"));

        mockMvc.perform(patch("/api/artikel/-1/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kurzbeschreibung\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void behandeltSqlInjectionAlsGewoehnlichenText() throws Exception {
        Artikel gespeichert = new Artikel();
        gespeichert.setId(7L);
        gespeichert.setKurzbeschreibung("'; DROP TABLE artikel; --");
        when(artikelService.aktualisiereDokumenttexte(eq(7L), any())).thenReturn(gespeichert);

        mockMvc.perform(patch("/api/artikel/7/dokumenttexte")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kurzbeschreibung\":\"'; DROP TABLE artikel; --\"}"))
                .andExpect(status().isOk());
    }
}
