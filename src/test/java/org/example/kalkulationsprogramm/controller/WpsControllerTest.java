package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.domain.Wps;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.WpsProjektZuweisungRepository;
import org.example.kalkulationsprogramm.repository.WpsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WpsController.class)
@AutoConfigureMockMvc(addFilters = false)
class WpsControllerTest {

    @TempDir
    static Path tempUploadDir;

    @DynamicPropertySource
    static void fileProperties(DynamicPropertyRegistry registry) {
        registry.add("file.upload-dir", () -> tempUploadDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WpsRepository repository;

    @MockBean
    private ProjektRepository projektRepository;

    @MockBean
    private WpsProjektZuweisungRepository zuweisungRepository;

    @MockBean
    private MitarbeiterRepository mitarbeiterRepository;

    @Test
    void getAll_gibtListeZurueck() throws Exception {
        when(repository.findAll()).thenReturn(List.of(createWps(1L)));

        mockMvc.perform(get("/api/wps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].wpsNummer").value("WPS-001"));
    }

    @Test
    void getById_gefunden() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(createWps(1L)));

        mockMvc.perform(get("/api/wps/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wpsNummer").value("WPS-001"))
                .andExpect(jsonPath("$.norm").value("EN ISO 15614-1"));
    }

    @Test
    void getById_nichtGefunden() throws Exception {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/wps/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByProjekt_gibtListeZurueck() throws Exception {
        when(repository.findByProjektId(1L)).thenReturn(List.of(createWps(1L)));

        mockMvc.perform(get("/api/wps/projekt/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].wpsNummer").value("WPS-001"));
    }

    @Test
    void create_erstelltWps() throws Exception {
        when(repository.save(any())).thenAnswer(inv -> {
            Wps w = inv.getArgument(0);
            w.setId(1L);
            w.setErstelltAm(LocalDateTime.now());
            return w;
        });

        mockMvc.perform(post("/api/wps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "wpsNummer": "WPS-002",
                                    "bezeichnung": "MAG S235 Stumpfnaht",
                                    "norm": "EN ISO 15614-1",
                                    "schweissProzes": "MAG (135)",
                                    "grundwerkstoff": "S235JR",
                                    "blechdickeMin": 3.0,
                                    "blechdickeMax": 20.0
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wpsNummer").value("WPS-002"));
    }

    @Test
    void update_gefunden() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(createWps(1L)));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/wps/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "wpsNummer": "WPS-001-REV",
                                    "norm": "EN ISO 15614-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wpsNummer").value("WPS-001-REV"));
    }

    @Test
    void update_nichtGefunden() throws Exception {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/wps/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"wpsNummer\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_existiert() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(createWps(1L)));

        mockMvc.perform(delete("/api/wps/1"))
                .andExpect(status().isNoContent());

        verify(repository).deleteById(1L);
    }

    @Test
    void delete_nichtGefunden() throws Exception {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/wps/999"))
                .andExpect(status().isNotFound());

        verify(repository, never()).deleteById(any());
    }

    // --- PDF-Upload Endpoint ---

    @Test
    void uploadPdfWps_erfolgreich() throws Exception {
        when(repository.save(any())).thenAnswer(inv -> {
            Wps w = inv.getArgument(0);
            w.setId(42L);
            w.setErstelltAm(LocalDateTime.now());
            return w;
        });

        MockMultipartFile datei = new MockMultipartFile(
                "datei",
                "schweissanweisung.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4 Dummy-PDF-Inhalt".getBytes());

        mockMvc.perform(multipart("/api/wps/upload")
                        .file(datei)
                        .param("wpsNummer", "WPS-IMPORT-001")
                        .param("bezeichnung", "Extern erstellte WPS")
                        .param("grundwerkstoff", "S355J2")
                        .param("nahtart", "Kehlnaht")
                        .param("gueltigBis", "2027-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wpsNummer").value("WPS-IMPORT-001"))
                .andExpect(jsonPath("$.quelle").value("UPLOAD"))
                .andExpect(jsonPath("$.norm").value("EN ISO 15614-1"))
                .andExpect(jsonPath("$.schweissProzes").value("extern"))
                .andExpect(jsonPath("$.originalDateiname").value("schweissanweisung.pdf"));
    }

    @Test
    void uploadPdfWps_ablehntNichtPdf() throws Exception {
        MockMultipartFile datei = new MockMultipartFile(
                "datei",
                "falsch.docx",
                "application/msword",
                "nicht-pdf".getBytes());

        mockMvc.perform(multipart("/api/wps/upload")
                        .file(datei)
                        .param("wpsNummer", "WPS-X"))
                .andExpect(status().isUnsupportedMediaType());

        verify(repository, never()).save(any());
    }

    @Test
    void uploadPdfWps_fehlendeWpsNummer() throws Exception {
        MockMultipartFile datei = new MockMultipartFile(
                "datei",
                "wps.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4".getBytes());

        mockMvc.perform(multipart("/api/wps/upload")
                        .file(datei)
                        .param("wpsNummer", "  "))
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    @Test
    void uploadPdfWps_fehlendeDatei() throws Exception {
        MockMultipartFile leereDatei = new MockMultipartFile(
                "datei",
                "leer.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                new byte[0]);

        mockMvc.perform(multipart("/api/wps/upload")
                        .file(leereDatei)
                        .param("wpsNummer", "WPS-Y"))
                .andExpect(status().isBadRequest());

        verify(repository, never()).save(any());
    }

    // --- PDF-Stream Endpoint ---

    @Test
    void streamDokument_liefertPdf() throws Exception {
        String storedName = "stored_wps.pdf";
        Path file = tempUploadDir.resolve(storedName);
        Files.write(file, "%PDF-1.4 Dummy".getBytes());

        Wps w = createWps(5L);
        w.setGespeicherterDateiname(storedName);
        w.setOriginalDateiname("original.pdf");
        when(repository.findById(5L)).thenReturn(Optional.of(w));

        mockMvc.perform(get("/api/wps/5/dokument"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("original.pdf")));
    }

    @Test
    void streamDokument_nichtGefunden() throws Exception {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/wps/999/dokument"))
                .andExpect(status().isNotFound());
    }

    @Test
    void streamDokument_keineDateiHinterlegt() throws Exception {
        Wps w = createWps(6L);
        w.setGespeicherterDateiname(null);
        when(repository.findById(6L)).thenReturn(Optional.of(w));

        mockMvc.perform(get("/api/wps/6/dokument"))
                .andExpect(status().isNotFound());
    }

    @Test
    void streamDokument_dateiPhysischFehlt() throws Exception {
        Wps w = createWps(7L);
        w.setGespeicherterDateiname("nicht-existent.pdf");
        when(repository.findById(7L)).thenReturn(Optional.of(w));

        mockMvc.perform(get("/api/wps/7/dokument"))
                .andExpect(status().isNotFound());
    }

    // --- Hilfsmethode ---

    private Wps createWps(Long id) {
        Wps wps = new Wps();
        wps.setId(id);
        wps.setWpsNummer("WPS-001");
        wps.setBezeichnung("MAG S235 Stumpfnaht");
        wps.setNorm("EN ISO 15614-1");
        wps.setSchweissProzes("MAG (135)");
        wps.setGrundwerkstoff("S235JR");
        wps.setBlechdickeMin(BigDecimal.valueOf(3));
        wps.setBlechdickeMax(BigDecimal.valueOf(20));
        wps.setGueltigBis(LocalDate.now().plusYears(2));
        wps.setErstelltAm(LocalDateTime.now());
        return wps;
    }
}
