package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.example.kalkulationsprogramm.service.KostenstelleService;
import org.example.kalkulationsprogramm.service.SteuerberaterKontaktService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Pruefungen fuer die neuen Logo-Endpoints in {@link FirmaController}.
 * Die bestehenden CRUD-Endpoints sind nicht Teil dieses Tests.
 */
@WebMvcTest(FirmaController.class)
@AutoConfigureMockMvc(addFilters = false)
class FirmaControllerLogoTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FirmeninformationService firmeninformationService;

    @MockBean
    private KostenstelleService kostenstelleService;

    @MockBean
    private SteuerberaterKontaktService steuerberaterKontaktService;

    @Test
    void uploadLogo_happyPath_liefertAktualisierteFirmeninfo() throws Exception {
        MockMultipartFile datei = new MockMultipartFile(
                "datei", "firmenlogo.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1, 2, 3 });
        FirmeninformationDto dto = new FirmeninformationDto();
        dto.setLogoDateiname("logo.png");
        when(firmeninformationService.speichereLogoDatei(any())).thenReturn(dto);

        mockMvc.perform(multipart("/api/firma/logo").file(datei))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoDateiname").value("logo.png"));
    }

    @Test
    void uploadLogo_ungueltigerMimeType_liefert400() throws Exception {
        MockMultipartFile datei = new MockMultipartFile(
                "datei", "schadcode.sh", "application/x-sh", new byte[] { 1, 2, 3 });
        when(firmeninformationService.speichereLogoDatei(any()))
                .thenThrow(new IllegalArgumentException("Ungültiger Dateityp – erlaubt sind PNG, JPEG und WebP"));

        mockMvc.perform(multipart("/api/firma/logo").file(datei))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Ungültiger Dateityp")));
    }

    @Test
    void uploadLogo_pfadTraversal_imDateinamen_wirdVomService_ignoriert() throws Exception {
        // Der Client-Dateiname mit Traversal darf den Controller nicht aushebeln –
        // der Service entscheidet alleine ueber den Zielnamen.
        MockMultipartFile datei = new MockMultipartFile(
                "datei", "../../etc/passwd.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1, 2, 3 });
        FirmeninformationDto dto = new FirmeninformationDto();
        dto.setLogoDateiname("logo.png");
        when(firmeninformationService.speichereLogoDatei(any())).thenReturn(dto);

        mockMvc.perform(multipart("/api/firma/logo").file(datei))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoDateiname").value("logo.png"));
    }

    @Test
    void uploadLogo_ioFehler_liefert500() throws Exception {
        MockMultipartFile datei = new MockMultipartFile(
                "datei", "logo.png", MediaType.IMAGE_PNG_VALUE, new byte[] { 1, 2, 3 });
        when(firmeninformationService.speichereLogoDatei(any())).thenThrow(new IOException("disk full"));

        mockMvc.perform(multipart("/api/firma/logo").file(datei))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getLogo_liefertBinary() throws Exception {
        byte[] bytes = new byte[] { 9, 8, 7 };
        when(firmeninformationService.loadLogoBytes()).thenReturn(bytes);
        when(firmeninformationService.ermittleLogoContentType()).thenReturn(MediaType.IMAGE_PNG_VALUE);

        mockMvc.perform(get("/api/firma/logo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void getLogo_keinesHinterlegt_liefert404() throws Exception {
        when(firmeninformationService.loadLogoBytes()).thenReturn(null);

        mockMvc.perform(get("/api/firma/logo"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteLogo_liefertAktualisierteFirmeninfo() throws Exception {
        FirmeninformationDto dto = new FirmeninformationDto();
        dto.setLogoDateiname(null);
        when(firmeninformationService.loescheLogoDatei()).thenReturn(dto);

        mockMvc.perform(delete("/api/firma/logo"))
                .andExpect(status().isOk());
        verify(firmeninformationService).loescheLogoDatei();
    }
}
