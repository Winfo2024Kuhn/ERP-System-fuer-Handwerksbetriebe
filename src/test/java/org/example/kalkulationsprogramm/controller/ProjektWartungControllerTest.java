package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProjektWartungController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjektWartungControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;

    @Test
    void tragePreiseNach_liefertAnzahlGeprueterUndNachgetragenerProjekte() throws Exception {
        when(ausgangsGeschaeftsDokumentService.trageFehlendePreiseNach())
                .thenReturn(new AusgangsGeschaeftsDokumentService.PreisNachtragErgebnis(7, 4));

        mockMvc.perform(post("/api/admin/projekte/preise-nachtragen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geprueft").value(7))
                .andExpect(jsonPath("$.nachgetragen").value(4));

        verify(ausgangsGeschaeftsDokumentService).trageFehlendePreiseNach();
    }

    @Test
    void tragePreiseNach_meldetNullWennEsNichtsNachzutragenGibt() throws Exception {
        when(ausgangsGeschaeftsDokumentService.trageFehlendePreiseNach())
                .thenReturn(new AusgangsGeschaeftsDokumentService.PreisNachtragErgebnis(0, 0));

        mockMvc.perform(post("/api/admin/projekte/preise-nachtragen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geprueft").value(0))
                .andExpect(jsonPath("$.nachgetragen").value(0));
    }

    @Test
    void korrigiereRabattBetraege_liefertGeprueftKorrigiertUndProjektzahl() throws Exception {
        when(ausgangsGeschaeftsDokumentService.korrigiereRabattBetraege(any(), any()))
                .thenReturn(new AusgangsGeschaeftsDokumentService.RabattKorrekturErgebnis(12, 5, 3));

        mockMvc.perform(post("/api/admin/projekte/rabatt-betraege-korrigieren"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geprueft").value(12))
                .andExpect(jsonPath("$.korrigiert").value(5))
                .andExpect(jsonPath("$.projektePreisNeu").value(3));

        verify(ausgangsGeschaeftsDokumentService).korrigiereRabattBetraege(any(), any());
    }

    @Test
    void korrigiereRabattBetraege_meldetNullWennNichtsZuKorrigierenIst() throws Exception {
        when(ausgangsGeschaeftsDokumentService.korrigiereRabattBetraege(any(), any()))
                .thenReturn(new AusgangsGeschaeftsDokumentService.RabattKorrekturErgebnis(0, 0, 0));

        mockMvc.perform(post("/api/admin/projekte/rabatt-betraege-korrigieren"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geprueft").value(0))
                .andExpect(jsonPath("$.korrigiert").value(0))
                .andExpect(jsonPath("$.projektePreisNeu").value(0));
    }
}
