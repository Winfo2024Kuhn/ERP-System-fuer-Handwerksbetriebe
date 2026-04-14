package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Werkstoffzeugnis;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.WerkstoffzeugnisRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WerkstoffzeugnisController.class)
@AutoConfigureMockMvc(addFilters = false)
class WerkstoffzeugnisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WerkstoffzeugnisRepository repository;

    @MockBean
    private LieferantenRepository lieferantenRepository;

    @MockBean
    private ProjektRepository projektRepository;

    @Test
    void getAll_gibtListeZurueck() throws Exception {
        when(repository.findAll()).thenReturn(List.of(createWz(1L)));

        mockMvc.perform(get("/api/werkstoffzeugnisse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schmelzNummer").value("SCH-001"))
                .andExpect(jsonPath("$[0].normTyp").value("3.1"));
    }

    @Test
    void getById_gefunden() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(createWz(1L)));

        mockMvc.perform(get("/api/werkstoffzeugnisse/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materialGuete").value("S355J2"));
    }

    @Test
    void getById_nichtGefunden() throws Exception {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/werkstoffzeugnisse/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByProjekt_gibtListeZurueck() throws Exception {
        when(repository.findByProjektId(1L)).thenReturn(List.of(createWz(1L)));

        mockMvc.perform(get("/api/werkstoffzeugnisse/projekt/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].schmelzNummer").value("SCH-001"));
    }

    @Test
    void create_mitLieferant() throws Exception {
        Lieferanten lief = new Lieferanten();
        lief.setId(10L);
        lief.setLieferantenname("Muster Stahl GmbH");
        when(lieferantenRepository.findById(10L)).thenReturn(Optional.of(lief));
        when(repository.save(any())).thenAnswer(inv -> {
            Werkstoffzeugnis w = inv.getArgument(0);
            w.setId(1L);
            w.setErstelltAm(LocalDateTime.now());
            return w;
        });

        mockMvc.perform(post("/api/werkstoffzeugnisse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "lieferantId": 10,
                                    "schmelzNummer": "SCH-002",
                                    "materialGuete": "S235JR",
                                    "normTyp": "3.2",
                                    "pruefstelle": "TÜV Nord"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schmelzNummer").value("SCH-002"))
                .andExpect(jsonPath("$.normTyp").value("3.2"))
                .andExpect(jsonPath("$.lieferantName").value("Muster Stahl GmbH"));
    }

    @Test
    void create_ohneNormTyp_default31() throws Exception {
        when(repository.save(any())).thenAnswer(inv -> {
            Werkstoffzeugnis w = inv.getArgument(0);
            w.setId(1L);
            w.setErstelltAm(LocalDateTime.now());
            return w;
        });

        mockMvc.perform(post("/api/werkstoffzeugnisse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "schmelzNummer": "SCH-003",
                                    "materialGuete": "S235"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.normTyp").value("3.1"));
    }

    @Test
    void update_gefunden() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(createWz(1L)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/werkstoffzeugnisse/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "schmelzNummer": "SCH-UPD",
                                    "materialGuete": "S355"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schmelzNummer").value("SCH-UPD"));
    }

    @Test
    void update_nichtGefunden() throws Exception {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/werkstoffzeugnisse/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schmelzNummer\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_existiert() throws Exception {
        when(repository.existsById(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/werkstoffzeugnisse/1"))
                .andExpect(status().isNoContent());

        verify(repository).deleteById(1L);
    }

    @Test
    void delete_nichtGefunden() throws Exception {
        when(repository.existsById(999L)).thenReturn(false);

        mockMvc.perform(delete("/api/werkstoffzeugnisse/999"))
                .andExpect(status().isNotFound());

        verify(repository, never()).deleteById(any());
    }

    // --- Hilfsmethode ---

    private Werkstoffzeugnis createWz(Long id) {
        Werkstoffzeugnis wz = new Werkstoffzeugnis();
        wz.setId(id);
        wz.setSchmelzNummer("SCH-001");
        wz.setMaterialGuete("S355J2");
        wz.setNormTyp("3.1");
        wz.setPruefstelle("TÜV Süd");
        wz.setErstelltAm(LocalDateTime.now());
        Lieferanten lief = new Lieferanten();
        lief.setId(10L);
        lief.setLieferantenname("Muster Stahl GmbH");
        wz.setLieferant(lief);
        return wz;
    }
}
