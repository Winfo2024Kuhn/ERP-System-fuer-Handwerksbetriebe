package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Betriebsmittel;
import org.example.kalkulationsprogramm.domain.BetriebsmittelPruefung;
import org.example.kalkulationsprogramm.service.BetriebsmittelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BetriebsmittelController.class)
@AutoConfigureMockMvc(addFilters = false)
class BetriebsmittelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BetriebsmittelService betriebsmittelService;

    // --- GET ---

    @Test
    void getAll_gibtListeZurueck() throws Exception {
        Betriebsmittel bm = createBm(1L, "Bohrmaschine");
        when(betriebsmittelService.findAll()).thenReturn(List.of(bm));

        mockMvc.perform(get("/api/betriebsmittel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bezeichnung").value("Bohrmaschine"));
    }

    @Test
    void getById_gefunden() throws Exception {
        when(betriebsmittelService.findById(1L)).thenReturn(Optional.of(createBm(1L, "Bohrmaschine")));

        mockMvc.perform(get("/api/betriebsmittel/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bezeichnung").value("Bohrmaschine"));
    }

    @Test
    void getById_nichtGefunden() throws Exception {
        when(betriebsmittelService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/betriebsmittel/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByBarcode_gefunden() throws Exception {
        Betriebsmittel bm = createBm(1L, "Bohrmaschine");
        bm.setBarcode("BC-001");
        when(betriebsmittelService.findByBarcode("BC-001")).thenReturn(Optional.of(bm));

        mockMvc.perform(get("/api/betriebsmittel/barcode/BC-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barcode").value("BC-001"));
    }

    @Test
    void getByBarcode_nichtGefunden() throws Exception {
        when(betriebsmittelService.findByBarcode("X")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/betriebsmittel/barcode/X"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFaellig_gibtListeZurueck() throws Exception {
        when(betriebsmittelService.findFaellig()).thenReturn(List.of(createBm(1L, "Fällig")));

        mockMvc.perform(get("/api/betriebsmittel/faellig"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bezeichnung").value("Fällig"));
    }

    // --- POST ---

    @Test
    void create_erstelltBetriebsmittel() throws Exception {
        Betriebsmittel saved = createBm(1L, "Neu");
        when(betriebsmittelService.save(any())).thenReturn(saved);

        mockMvc.perform(post("/api/betriebsmittel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bezeichnung\":\"Neu\",\"pruefIntervallMonate\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // --- PUT ---

    @Test
    void update_gefunden() throws Exception {
        Betriebsmittel existing = createBm(1L, "Alt");
        Betriebsmittel updated = createBm(1L, "Aktualisiert");
        when(betriebsmittelService.findById(1L)).thenReturn(Optional.of(existing));
        when(betriebsmittelService.save(any())).thenReturn(updated);

        mockMvc.perform(put("/api/betriebsmittel/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bezeichnung\":\"Aktualisiert\",\"pruefIntervallMonate\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bezeichnung").value("Aktualisiert"));
    }

    @Test
    void update_nichtGefunden() throws Exception {
        when(betriebsmittelService.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/betriebsmittel/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bezeichnung\":\"X\",\"pruefIntervallMonate\":12}"))
                .andExpect(status().isNotFound());
    }

    // --- DELETE ---

    @Test
    void delete_gibtNoContentZurueck() throws Exception {
        when(betriebsmittelService.findById(1L)).thenReturn(Optional.of(createBm(1L, "X")));

        mockMvc.perform(delete("/api/betriebsmittel/1"))
                .andExpect(status().isNoContent());
    }

    // --- Prüfungen ---

    @Test
    void getPruefungen_gibtListeZurueck() throws Exception {
        BetriebsmittelPruefung p = new BetriebsmittelPruefung();
        p.setPruefDatum(LocalDate.of(2024, 1, 1));
        p.setBestanden(true);
        when(betriebsmittelService.findPruefungen(1L)).thenReturn(List.of(p));

        mockMvc.perform(get("/api/betriebsmittel/1/pruefungen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bestanden").value(true));
    }

    @Test
    void getOffenePruefungen_gibtListeZurueck() throws Exception {
        when(betriebsmittelService.findOffenePruefungen()).thenReturn(List.of());

        mockMvc.perform(get("/api/betriebsmittel/pruefungen/offen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void pruefungErfassen_erstelltProtokoll() throws Exception {
        BetriebsmittelPruefung saved = new BetriebsmittelPruefung();
        saved.setBestanden(true);
        saved.setPruefDatum(LocalDate.now());
        when(betriebsmittelService.pruefungErfassen(eq(1L), isNull(), any())).thenReturn(saved);

        mockMvc.perform(post("/api/betriebsmittel/1/pruefungen")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bestanden\":true,\"schutzklasse\":\"SK I\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bestanden").value(true));
    }

    @Test
    void verifizieren_setzt_flag() throws Exception {
        BetriebsmittelPruefung verifiziert = new BetriebsmittelPruefung();
        verifiziert.setVonElektrikerVerifiziert(true);
        when(betriebsmittelService.elektrikerVerifizieren(1L)).thenReturn(verifiziert);

        mockMvc.perform(post("/api/betriebsmittel/pruefungen/1/verifizieren"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vonElektrikerVerifiziert").value(true));
    }

    // --- Hilfs-Methode ---

    private Betriebsmittel createBm(Long id, String bezeichnung) {
        Betriebsmittel bm = new Betriebsmittel();
        bm.setId(id);
        bm.setBezeichnung(bezeichnung);
        bm.setPruefIntervallMonate(12);
        return bm;
    }
}
