package org.example.kalkulationsprogramm.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.exception.NotFoundException;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DateiController.class)
@AutoConfigureMockMvc(addFilters = false)
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

    @org.junit.jupiter.api.Test
    void downloadParamForcesFileDeliveryForExcelInHicad() throws Exception {
        String filename = "kalkulation.xlsx";
        ProjektDokument doc = new ProjektDokument();
        doc.setGespeicherterDateiname(filename);
        doc.setOriginalDateiname("Kalkulation.xlsx");
        when(dateiSpeicherService.ladeDokumentMetadaten(anyString()))
                .thenReturn(doc);
        when(dateiSpeicherService.liegtInHicadSpeicher(anyString()))
                .thenReturn(true);
        ByteArrayResource resource = new ByteArrayResource("fake-xlsx-content".getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        when(dateiSpeicherService.ladeDokumentAlsResource(filename)).thenReturn(resource);

        mockMvc.perform(get("/api/dokumente/" + filename).param("download", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Kalkulation.xlsx\""));

        verify(dateiSpeicherService, never()).holeNetzwerkPfad(anyString());
    }

    // ============== THUMBNAILS ==============
    // Hinweis: Der Thumbnail-Cache lebt auf Controller-Instanz-Ebene und wird von allen
    // Tests dieser Klasse geteilt. Jeder Test nutzt daher einen eigenen Dateinamen.

    /** Erzeugt ein echtes JPEG der gewünschten Größe (einfarbig, keine Personendaten). */
    private static byte[] erzeugeJpeg(int breite, int hoehe) throws Exception {
        java.awt.image.BufferedImage bild =
                new java.awt.image.BufferedImage(breite, hoehe, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = bild.createGraphics();
        g.setColor(java.awt.Color.GRAY);
        g.fillRect(0, 0, breite, hoehe);
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(bild, "jpg", out);
        return out.toByteArray();
    }

    private static ByteArrayResource resourceMitNamen(byte[] daten, String name) {
        return new ByteArrayResource(daten) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

    @org.junit.jupiter.api.Test
    void thumbnailVerkleinertGrossesBild() throws Exception {
        String filename = "gross-foto.jpg";
        byte[] original = erzeugeJpeg(2000, 1500);
        when(dateiSpeicherService.ladeDokumentAlsResource(filename))
                .thenReturn(resourceMitNamen(original, filename));

        byte[] thumbnail = mockMvc.perform(get("/api/dokumente/" + filename + "/thumbnail"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                // "private": Bilder sind personenbezogen, kein geteilter Proxy-Cache
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=86400, private"))
                .andReturn().getResponse().getContentAsByteArray();

        java.awt.image.BufferedImage verkleinert =
                javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(thumbnail));
        org.assertj.core.api.Assertions.assertThat(verkleinert.getWidth()).isEqualTo(300);
        org.assertj.core.api.Assertions.assertThat(verkleinert.getHeight()).isEqualTo(225);
        org.assertj.core.api.Assertions.assertThat(thumbnail.length).isLessThan(original.length);
    }

    @org.junit.jupiter.api.Test
    void thumbnailWirdBeimZweitenAufrufAusDemCacheGeliefert() throws Exception {
        String filename = "gecacht-foto.jpg";
        when(dateiSpeicherService.ladeDokumentAlsResource(filename))
                .thenReturn(resourceMitNamen(erzeugeJpeg(1200, 1200), filename));

        mockMvc.perform(get("/api/dokumente/" + filename + "/thumbnail")).andExpect(status().isOk());
        mockMvc.perform(get("/api/dokumente/" + filename + "/thumbnail")).andExpect(status().isOk());

        // Nur der erste Aufruf liest die Datei von der Platte
        verify(dateiSpeicherService, org.mockito.Mockito.times(1)).ladeDokumentAlsResource(filename);
    }

    @org.junit.jupiter.api.Test
    void thumbnailFaelltAufBilderSpeicherZurueck() throws Exception {
        String filename = "notiz-bild.jpg";
        when(dateiSpeicherService.ladeDokumentAlsResource(filename))
                .thenThrow(new RuntimeException("nicht im Dokumentenspeicher"));
        when(dateiSpeicherService.ladeBildAlsResource(filename))
                .thenReturn(resourceMitNamen(erzeugeJpeg(800, 600), filename));

        mockMvc.perform(get("/api/dokumente/" + filename + "/thumbnail"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));

        verify(dateiSpeicherService).ladeBildAlsResource(filename);
    }

    @org.junit.jupiter.api.Test
    void thumbnailLiefertNichtBilderUnveraendertZurueck() throws Exception {
        String filename = "vertrag.pdf";
        byte[] pdfDaten = "%PDF-1.4 Dummy".getBytes(StandardCharsets.UTF_8);
        when(dateiSpeicherService.ladeDokumentAlsResource(filename))
                .thenReturn(resourceMitNamen(pdfDaten, filename));

        byte[] antwort = mockMvc.perform(get("/api/dokumente/" + filename + "/thumbnail"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        org.assertj.core.api.Assertions.assertThat(antwort).isEqualTo(pdfDaten);
    }

    @org.junit.jupiter.api.Test
    void thumbnailGibtKleinesBildOhneVergroesserungZurueck() throws Exception {
        String filename = "klein-icon.jpg";
        when(dateiSpeicherService.ladeDokumentAlsResource(filename))
                .thenReturn(resourceMitNamen(erzeugeJpeg(120, 90), filename));

        byte[] thumbnail = mockMvc.perform(get("/api/dokumente/" + filename + "/thumbnail"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn().getResponse().getContentAsByteArray();

        java.awt.image.BufferedImage ergebnis =
                javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(thumbnail));
        org.assertj.core.api.Assertions.assertThat(ergebnis.getWidth()).isEqualTo(120);
        org.assertj.core.api.Assertions.assertThat(ergebnis.getHeight()).isEqualTo(90);
    }

    @org.junit.jupiter.api.Test
    void thumbnailMeldet404WennDateiNirgendsExistiert() throws Exception {
        String filename = "gibt-es-nicht.jpg";
        when(dateiSpeicherService.ladeDokumentAlsResource(filename))
                .thenThrow(new RuntimeException("weg"));
        when(dateiSpeicherService.ladeBildAlsResource(filename))
                .thenThrow(new RuntimeException("auch weg"));

        mockMvc.perform(get("/api/dokumente/" + filename + "/thumbnail"))
                .andExpect(status().isNotFound());
    }
}

