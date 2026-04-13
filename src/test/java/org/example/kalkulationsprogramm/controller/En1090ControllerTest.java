package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.En1090ReportService;
import org.example.kalkulationsprogramm.service.En1090ReportService.WpkStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(En1090Controller.class)
@AutoConfigureMockMvc(addFilters = false)
class En1090ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private En1090ReportService reportService;

    @Test
    void getWpkStatus_alleOk() throws Exception {
        WpkStatus status = new WpkStatus();
        status.schweisser = "OK";
        status.schweisserHinweis = "3 gültige Zertifikate";
        status.wps = "OK";
        status.wpsHinweis = "2 WPS";
        status.werkstoffzeugnisse = "OK";
        status.werkstoffzeugnisseHinweis = "5 Zeugnis(se) vorhanden";
        status.echeck = "OK";
        status.echeckHinweis = "Alle E-Checks aktuell";
        when(reportService.getWpkStatus(1L)).thenReturn(status);

        mockMvc.perform(get("/api/en1090/wpk/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schweisser").value("OK"))
                .andExpect(jsonPath("$.wps").value("OK"))
                .andExpect(jsonPath("$.werkstoffzeugnisse").value("OK"))
                .andExpect(jsonPath("$.echeck").value("OK"))
                .andExpect(jsonPath("$.echeckHinweis").value("Alle E-Checks aktuell"));
    }

    @Test
    void getWpkStatus_mitFehler() throws Exception {
        WpkStatus status = new WpkStatus();
        status.schweisser = "FEHLER";
        status.schweisserHinweis = "2 Zertifikat(e) abgelaufen";
        status.echeck = "WARNUNG";
        status.echeckHinweis = "1 Betriebsmittel in <60 Tagen fällig";
        when(reportService.getWpkStatus(42L)).thenReturn(status);

        mockMvc.perform(get("/api/en1090/wpk/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schweisser").value("FEHLER"))
                .andExpect(jsonPath("$.echeck").value("WARNUNG"));
    }
}
