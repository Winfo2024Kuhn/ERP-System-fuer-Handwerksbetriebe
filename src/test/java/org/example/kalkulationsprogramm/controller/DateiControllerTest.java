package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DateiController.class)
class DateiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DateiSpeicherService dateiSpeicherService;

    @ParameterizedTest
    @ValueSource(strings = {"test.sza", "test.tcd", "TEST.SZA", "TEST.TCD"})
    void returnsProtocolUrlForHiCADFiles(String filename) throws Exception {
        ProjektDokument doc = new ProjektDokument();
        doc.setGespeicherterDateiname("pfad mit leerzeichen");
        when(dateiSpeicherService.ladeDokumentMetadaten(anyString()))
                .thenReturn(doc);
        when(dateiSpeicherService.holeNetzwerkPfad(anyString()))
                .thenReturn("pfad mit leerzeichen");

        String expectedUrl = "openfile://open?path=" +
                URLEncoder.encode("pfad mit leerzeichen", StandardCharsets.UTF_8)
                        .replace("+", "%20");

        mockMvc.perform(get("/api/dokumente/" + filename))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.protocolUrl").value(expectedUrl))
                .andExpect(jsonPath("$.type").value("openExternal"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=f.txt"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"test.sza", "test.tcd", "TEST.SZA", "TEST.TCD"})
    void appendsTokenToProtocolUrl(String filename) throws Exception {
        ProjektDokument doc = new ProjektDokument();
        doc.setGespeicherterDateiname("pfad");
        when(dateiSpeicherService.ladeDokumentMetadaten(anyString()))
                .thenReturn(doc);
        when(dateiSpeicherService.holeNetzwerkPfad(anyString()))
                .thenReturn("pfad");

        String token = "mein token";
        String expectedUrl = "openfile://open?path=" +
                URLEncoder.encode("pfad", StandardCharsets.UTF_8)
                        .replace("+", "%20") +
                "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/dokumente/" + filename).param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.protocolUrl").value(expectedUrl))
                .andExpect(jsonPath("$.type").value("openExternal"))
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=f.txt"));
    }

    @org.junit.jupiter.api.Test
    void returns404WhenDocumentMissing() throws Exception {
        when(dateiSpeicherService.ladeDokumentMetadaten(anyString()))
                .thenThrow(new NotFoundException("nicht gefunden"));

        mockMvc.perform(get("/api/dokumente/fehlend.sza"))
                .andExpect(status().isNotFound());
    }

    @org.junit.jupiter.api.Test
    void servesFileWhenMetadataMissingButFileExists() throws Exception {
        String filename = "bild.jpg";
        when(dateiSpeicherService.ladeDokumentMetadaten(anyString()))
                .thenThrow(new NotFoundException("nicht gefunden"));
        ByteArrayResource resource = new ByteArrayResource("data".getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        when(dateiSpeicherService.ladeDokumentAlsResource(filename)).thenReturn(resource);

        mockMvc.perform(get("/api/dokumente/" + filename))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + filename + "\""));
    }

    @org.junit.jupiter.api.Test
    void fallsBackToRequestedFilenameWhenStoredFileMissing() throws Exception {
        String requested = "ca5b0c48-4000-412a-ad2c-a8c8ce62b533.jpg";
        ProjektDokument doc = new ProjektDokument();
        doc.setGespeicherterDateiname("a266c9d7-0e34-4bb0-9ca6-1a61aa2d9997.JPEG");
        when(dateiSpeicherService.ladeDokumentMetadaten(anyString()))
                .thenReturn(doc);
        when(dateiSpeicherService.ladeDokumentAlsResource(doc.getGespeicherterDateiname()))
                .thenThrow(new RuntimeException("nicht gefunden"));
        ByteArrayResource resource = new ByteArrayResource("data".getBytes()) {
            @Override
            public String getFilename() {
                return requested;
            }
        };
        when(dateiSpeicherService.ladeDokumentAlsResource(requested)).thenReturn(resource);

        mockMvc.perform(get("/api/dokumente/" + requested))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + requested + "\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"test.xlsx", "TEST.XLS", "kalkulation.csv", "liste.ods"})
    void returnsProtocolUrlForExcelFilesWhenInHicad(String filename) throws Exception {
        org.example.kalkulationsprogramm.domain.ProjektDokument doc = new org.example.kalkulationsprogramm.domain.ProjektDokument();
        doc.setGespeicherterDateiname("pfad mit leerzeichen");
        when(dateiSpeicherService.ladeDokumentMetadaten(anyString()))
                .thenReturn(doc);
        when(dateiSpeicherService.liegtInHicadSpeicher(anyString()))
                .thenReturn(true);
        when(dateiSpeicherService.holeNetzwerkPfad(anyString()))
                .thenReturn("pfad mit leerzeichen");

        String expectedUrl = "openfile://open?path=" +
                URLEncoder.encode("pfad mit leerzeichen", StandardCharsets.UTF_8)
                        .replace("+", "%20");

        mockMvc.perform(get("/api/dokumente/" + filename))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.protocolUrl").value(expectedUrl))
                .andExpect(jsonPath("$.type").value("openExternal"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=f.txt"));

        verify(dateiSpeicherService, never()).holeWindowsLaufwerkPfad(anyString());
    }
}

