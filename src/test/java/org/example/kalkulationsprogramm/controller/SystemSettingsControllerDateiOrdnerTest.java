package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.DateiOrdnerService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
class SystemSettingsControllerDateiOrdnerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemSettingsService settingsService;
    @MockBean
    private DateiOrdnerService dateiOrdnerService;

    @Test
    void getDateiOrdner_liefertPfadUndFlag() throws Exception {
        when(settingsService.getDateiOrdnerPfad()).thenReturn("C:\\Zeichnungen");
        when(settingsService.getDateiOrdnerNetworkUrl()).thenReturn("\\\\server\\zeichnungen");
        when(settingsService.isDateiOrdnerConfigured()).thenReturn(true);

        mockMvc.perform(get("/api/settings/datei-ordner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pfad").value("C:\\Zeichnungen"))
                .andExpect(jsonPath("$.networkUrl").value("\\\\server\\zeichnungen"))
                .andExpect(jsonPath("$.konfiguriert").value(true));
    }

    @Test
    void putDateiOrdner_erfolgLiefert200() throws Exception {
        when(dateiOrdnerService.speichereOrdner("C:\\Zeichnungen", ""))
                .thenReturn(TestResult.success("Datei-Ordner gespeichert."));

        mockMvc.perform(put("/api/settings/datei-ordner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pfad\":\"C:\\\\Zeichnungen\",\"networkUrl\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Datei-Ordner gespeichert."));
    }

    @Test
    void putDateiOrdner_fehlerLiefert400() throws Exception {
        when(dateiOrdnerService.speichereOrdner(anyString(), anyString()))
                .thenReturn(TestResult.failure("Der Pfad darf keine '..'-Bestandteile enthalten."));

        mockMvc.perform(put("/api/settings/datei-ordner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pfad\":\"..\\\\boese\",\"networkUrl\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testDateiOrdner_liefertTestResult() throws Exception {
        when(dateiOrdnerService.pruefeOrdner("C:\\Zeichnungen"))
                .thenReturn(TestResult.success("Ordner gefunden und beschreibbar: C:\\Zeichnungen"));

        mockMvc.perform(post("/api/settings/datei-ordner/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pfad\":\"C:\\\\Zeichnungen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void freigeben_liefertTestResult() throws Exception {
        when(dateiOrdnerService.gebeOrdnerFrei())
                .thenReturn(TestResult.failure("Dafür sind Administrator-Rechte nötig."));

        mockMvc.perform(post("/api/settings/datei-ordner/freigeben"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }
}
