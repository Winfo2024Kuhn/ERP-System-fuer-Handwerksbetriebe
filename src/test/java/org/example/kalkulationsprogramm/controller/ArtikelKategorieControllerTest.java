package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.LieferantRolle;
import org.example.kalkulationsprogramm.dto.Artikel.KategorieResponseDto;
import org.example.kalkulationsprogramm.service.KategorieService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-Tests fuer die neuen Lieferanten-Rollen-Endpoints der Artikel-Kategorien
 * (PUT .../rollen, GET .../effektive-rollen). Happy-Path + Fehlerfall gemaess
 * TESTING_SECURITY.md (ungueltige IDs, kein SQL-Injection-Risiko da Integer-PathVariable).
 */
@WebMvcTest(ArtikelKategorieController.class)
@AutoConfigureMockMvc(addFilters = false)
class ArtikelKategorieControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KategorieService kategorieService;

    @Test
    void aktualisiereRollenSpeichertUndLiefertDto() throws Exception {
        KategorieResponseDto dto = new KategorieResponseDto();
        dto.setId(5);
        dto.setBezeichnung("Schrauben");
        dto.setTypischeRollen(Set.of(LieferantRolle.SCHRAUBEN_NORMTEILE));
        when(kategorieService.aktualisiereTypischeRollen(eq(5), any())).thenReturn(dto);

        mockMvc.perform(put("/api/artikel/kategorien/5/rollen")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"SCHRAUBEN_NORMTEILE\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.typischeRollen[0]").value("SCHRAUBEN_NORMTEILE"));
    }

    @Test
    void aktualisiereRollenLiefert404BeiUnbekannterKategorie() throws Exception {
        when(kategorieService.aktualisiereTypischeRollen(eq(999), any()))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Kategorie nicht gefunden."));

        mockMvc.perform(put("/api/artikel/kategorien/999/rollen")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aktualisiereRollenLiefert400BeiUngueltigerId() throws Exception {
        mockMvc.perform(put("/api/artikel/kategorien/keine-zahl/rollen")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aktualisiereRollenLehntUnbekannteRolleAb() throws Exception {
        mockMvc.perform(put("/api/artikel/kategorien/5/rollen")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"'; DROP TABLE kategorie; --\"]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void effektiveRollenLiefertGeerbteRollen() throws Exception {
        when(kategorieService.findeEffektiveRollen(7)).thenReturn(Set.of(LieferantRolle.EDELSTAHL));

        mockMvc.perform(get("/api/artikel/kategorien/7/effektive-rollen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("EDELSTAHL"));
    }

    @Test
    void effektiveRollenLiefertLeeresArrayWennNichtsHinterlegt() throws Exception {
        when(kategorieService.findeEffektiveRollen(8)).thenReturn(Set.of());

        mockMvc.perform(get("/api/artikel/kategorien/8/effektive-rollen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void effektiveRollenLiefert400BeiUngueltigerId() throws Exception {
        mockMvc.perform(get("/api/artikel/kategorien/-1abc/effektive-rollen"))
                .andExpect(status().isBadRequest());
    }
}
