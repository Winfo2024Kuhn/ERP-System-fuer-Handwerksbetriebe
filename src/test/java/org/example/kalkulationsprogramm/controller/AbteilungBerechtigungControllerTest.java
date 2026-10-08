package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AbteilungBerechtigungController.class)
@AutoConfigureMockMvc(addFilters = false)
class AbteilungBerechtigungControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AbteilungRepository abteilungRepository;

    @MockBean
    private AbteilungDokumentBerechtigungRepository berechtigungRepository;

    private Abteilung abteilung(boolean webseitenPush, boolean freigabePush) {
        Abteilung abteilung = new Abteilung();
        abteilung.setId(3L);
        abteilung.setName("Büro");
        abteilung.setDarfWebseitenAnfragenPushen(webseitenPush);
        abteilung.setDarfFreigabeAnnahmePushen(freigabePush);
        return abteilung;
    }

    @Test
    @DisplayName("Liste liefert das Push-Häkchen für Webseiten-Anfragen mit aus")
    void listeEnthaeltWebseitenPush() throws Exception {
        when(abteilungRepository.findAll()).thenReturn(List.of(abteilung(true, false)));
        when(berechtigungRepository.findByAbteilungId(3L)).thenReturn(List.of());

        mockMvc.perform(get("/api/abteilungen/berechtigungen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].abteilungId").value(3))
            .andExpect(jsonPath("$[0].darfWebseitenAnfragenPushen").value(true))
            .andExpect(jsonPath("$[0].darfFreigabeAnnahmePushen").value(false));
    }

    @Test
    @DisplayName("Liste zeigt ein abgeschaltetes Webseiten-Push als false, nicht als fehlend")
    void listeZeigtAbgeschaltetenWebseitenPush() throws Exception {
        when(abteilungRepository.findAll()).thenReturn(List.of(abteilung(false, true)));
        when(berechtigungRepository.findByAbteilungId(3L)).thenReturn(List.of());

        mockMvc.perform(get("/api/abteilungen/berechtigungen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].darfWebseitenAnfragenPushen").value(false));
    }

    @Test
    @DisplayName("Einzelabruf liefert dasselbe Push-Häkchen wie die Liste")
    void einzelabrufEnthaeltWebseitenPush() throws Exception {
        when(abteilungRepository.findById(3L)).thenReturn(Optional.of(abteilung(true, false)));
        when(berechtigungRepository.findByAbteilungId(3L)).thenReturn(List.of());

        mockMvc.perform(get("/api/abteilungen/3/berechtigungen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.darfWebseitenAnfragenPushen").value(true));
    }

    @Test
    @DisplayName("Einzelabruf: unbekannte Abteilung liefert 404")
    void einzelabrufUnbekannt() throws Exception {
        when(abteilungRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/abteilungen/99/berechtigungen"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Speichern übernimmt das Webseiten-Push und liefert es zurück")
    void speichernUebernimmtWebseitenPush() throws Exception {
        Abteilung gespeichert = abteilung(false, true);
        when(abteilungRepository.findById(3L)).thenReturn(Optional.of(gespeichert));
        when(abteilungRepository.save(any(Abteilung.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
        when(berechtigungRepository.findByAbteilungId(3L)).thenReturn(List.of());

        mockMvc.perform(put("/api/abteilungen/3/berechtigungen")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"berechtigungen\":[],\"darfWebseitenAnfragenPushen\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.darfWebseitenAnfragenPushen").value(true));
    }
}
