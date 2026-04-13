package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.SchweisserZertifikat;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.SchweisserZertifikatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SchweisserZertifikatController.class)
@AutoConfigureMockMvc(addFilters = false)
class SchweisserZertifikatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SchweisserZertifikatRepository repository;

    @MockBean
    private MitarbeiterRepository mitarbeiterRepository;

    @Test
    void getAll_gibtListeZurueck() throws Exception {
        SchweisserZertifikat z = createZertifikat(1L);
        when(repository.findAll()).thenReturn(List.of(z));

        mockMvc.perform(get("/api/schweisser-zertifikate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].zertifikatsnummer").value("ZERT-001"));
    }

    @Test
    void getById_gefunden() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(createZertifikat(1L)));

        mockMvc.perform(get("/api/schweisser-zertifikate/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zertifikatsnummer").value("ZERT-001"));
    }

    @Test
    void getById_nichtGefunden() throws Exception {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/schweisser-zertifikate/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByMitarbeiter_gibtListeZurueck() throws Exception {
        when(repository.findByMitarbeiterId(5L)).thenReturn(List.of(createZertifikat(1L)));

        mockMvc.perform(get("/api/schweisser-zertifikate/mitarbeiter/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mitarbeiterId").value(5));
    }

    @Test
    void getAblaufend_gibtListeZurueck() throws Exception {
        when(repository.findAblaufendBis(any())).thenReturn(List.of(createZertifikat(1L)));

        mockMvc.perform(get("/api/schweisser-zertifikate/ablaufend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void create_erstelltZertifikat() throws Exception {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(5L);
        m.setVorname("Max");
        m.setNachname("Mustermann");
        when(mitarbeiterRepository.findById(5L)).thenReturn(Optional.of(m));
        when(repository.save(any())).thenAnswer(inv -> {
            SchweisserZertifikat z = inv.getArgument(0);
            z.setId(1L);
            z.setErstelltAm(LocalDateTime.now());
            return z;
        });

        mockMvc.perform(post("/api/schweisser-zertifikate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "mitarbeiterId": 5,
                                    "zertifikatsnummer": "ZERT-002",
                                    "norm": "EN ISO 9606-1",
                                    "schweissProzes": "MAG (135)",
                                    "grundwerkstoff": "S235",
                                    "pruefstelle": "TÜV Süd"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zertifikatsnummer").value("ZERT-002"))
                .andExpect(jsonPath("$.mitarbeiterName").value("Max Mustermann"));
    }

    @Test
    void update_gefunden() throws Exception {
        SchweisserZertifikat existing = createZertifikat(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/schweisser-zertifikate/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "zertifikatsnummer": "ZERT-UPD",
                                    "norm": "EN ISO 9606-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zertifikatsnummer").value("ZERT-UPD"));
    }

    @Test
    void update_nichtGefunden() throws Exception {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/schweisser-zertifikate/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"zertifikatsnummer\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_existiert() throws Exception {
        when(repository.existsById(1L)).thenReturn(true);

        mockMvc.perform(delete("/api/schweisser-zertifikate/1"))
                .andExpect(status().isNoContent());

        verify(repository).deleteById(1L);
    }

    @Test
    void delete_nichtGefunden() throws Exception {
        when(repository.existsById(999L)).thenReturn(false);

        mockMvc.perform(delete("/api/schweisser-zertifikate/999"))
                .andExpect(status().isNotFound());

        verify(repository, never()).deleteById(any());
    }

    // --- Hilfsmethode ---

    private SchweisserZertifikat createZertifikat(Long id) {
        SchweisserZertifikat z = new SchweisserZertifikat();
        z.setId(id);
        z.setZertifikatsnummer("ZERT-001");
        z.setNorm("EN ISO 9606-1");
        z.setSchweissProzes("MAG (135)");
        z.setGrundwerkstoff("S235");
        z.setAblaufdatum(LocalDate.now().plusYears(1));
        z.setErstelltAm(LocalDateTime.now());
        Mitarbeiter m = new Mitarbeiter();
        m.setId(5L);
        m.setVorname("Max");
        m.setNachname("Mustermann");
        z.setMitarbeiter(m);
        return z;
    }
}
