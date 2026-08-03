package org.example.kalkulationsprogramm.controller;

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
}
