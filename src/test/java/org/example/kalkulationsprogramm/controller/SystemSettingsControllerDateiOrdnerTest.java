package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.DateiOrdnerService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.SystemSettingsService.TestResult;
import org.example.kalkulationsprogramm.service.openfile.ContainerFreigabe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemSettingsController.class)
@AutoConfigureMockMvc(addFilters = false)
class SystemSettingsControllerDateiOrdnerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SystemSettingsService settingsService;
    @MockitoBean
    private DateiOrdnerService dateiOrdnerService;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.PostfachUmzugService postfachUmzugService;
    @MockitoBean
    private ContainerFreigabe containerFreigabe;

    @Test
    void getDateiOrdner_liefertPfadUndFlag() throws Exception {
        when(settingsService.getDateiOrdnerPfad()).thenReturn("C:\\Zeichnungen");
        when(settingsService.getDateiOrdnerNetworkUrl()).thenReturn("\\\\server\\zeichnungen");
        when(settingsService.isDateiOrdnerConfigured()).thenReturn(true);

        mockMvc.perform(get("/api/settings/datei-ordner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pfad").value("C:\\Zeichnungen"))
                .andExpect(jsonPath("$.networkUrl").value("\\\\server\\zeichnungen"))
                .andExpect(jsonPath("$.konfiguriert").value(true))
                // Ohne Ordner-Freigabe aus dem Docker-Paket
                .andExpect(jsonPath("$.containerFreigabe").doesNotExist())
                .andExpect(jsonPath("$.containerFreigabePfad").doesNotExist());
    }

    @Test
    void getDateiOrdner_nenntDieAdresseDerContainerFreigabeAberNieDasPasswort() throws Exception {
        when(settingsService.getDateiOrdnerNetworkUrl()).thenReturn("\\\\BUERO_PC\\CADdrawings");
        when(containerFreigabe.ordnerAufDemServer()).thenReturn(java.util.Optional.of("/app/uploads/CADdrawings"));
        when(containerFreigabe.adresse()).thenReturn(java.util.Optional.of("\\\\erp-dateien.tail1234.ts.net\\CADdrawings"));
        when(containerFreigabe.zugang()).thenReturn(java.util.Optional.of(new ContainerFreigabe.Zugang(
                "\\\\erp-dateien.tail1234.ts.net\\CADdrawings", "erp-dateien.tail1234.ts.net", "erp-dateien",
                "MusterPasswort2345abcdefgh")));

        mockMvc.perform(get("/api/settings/datei-ordner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containerFreigabe").value("\\\\erp-dateien.tail1234.ts.net\\CADdrawings"))
                .andExpect(jsonPath("$.containerFreigabePfad").value("/app/uploads/CADdrawings"))
                .andExpect(content().string(not(containsString("MusterPasswort"))));
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
