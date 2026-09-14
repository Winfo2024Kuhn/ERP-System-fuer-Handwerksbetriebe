package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.SteuerberaterPaketDto;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.SteuerberaterExportService;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SteuerberaterExportController.class)
@AutoConfigureMockMvc(addFilters = false)
class SteuerberaterExportControllerTest {
    @Autowired MockMvc mvc;
    @MockBean BelegService belege;
    @MockBean SteuerberaterExportService export;
    @MockBean FirmeninformationRepository firmen;

    @Test void sperrtOhneRechtUndUngültigenMonat() throws Exception {
        mvc.perform(get("/api/buchhaltung/steuerberater/vorpruefung?jahr=2026&monat=2")).andExpect(status().isForbidden());
        Mitarbeiter caller = new Mitarbeiter(); when(belege.findCaller(any(), any())).thenReturn(caller); when(belege.darfSehen(caller)).thenReturn(true);
        mvc.perform(get("/api/buchhaltung/steuerberater/vorpruefung?jahr=2026&monat=13")).andExpect(status().isBadRequest());
    }

    @Test void liefertOffenePunkteAlsConflictUndExportAlsZip() throws Exception {
        Mitarbeiter caller = new Mitarbeiter(); when(belege.findCaller(any(), any())).thenReturn(caller); when(belege.darfSehen(caller)).thenReturn(true);
        SteuerberaterPaketDto.Vorpruefung offen = SteuerberaterPaketDto.Vorpruefung.builder().anzahlBelege(1).offenePunkte(List.of(SteuerberaterPaketDto.OffenerPunkt.builder().belegId(7L).wasFehlt("Konto fehlt").build())).build();
        when(export.pruefe(2026, 2)).thenReturn(offen);
        mvc.perform(get("/api/buchhaltung/steuerberater/paket?jahr=2026&monat=2&trotzdem=false")).andExpect(status().isConflict()).andExpect(jsonPath("$.offenePunkte[0].belegId").value(7));
        when(export.erzeugeZip(2026, 2, caller, true)).thenReturn(new byte[]{1, 2});
        mvc.perform(get("/api/buchhaltung/steuerberater/paket?jahr=2026&monat=2&trotzdem=true")).andExpect(status().isOk()).andExpect(content().contentType("application/zip")).andExpect(header().string("Content-Disposition", "attachment; filename=\"2026-02_Kasse_Betrieb.zip\""));
    }
}
