package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.dto.LieferantDokumentDto;
import org.example.kalkulationsprogramm.repository.EmailAttachmentRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LieferantDokumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class LieferantDokumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LieferantDokumentRepository dokumentRepository;

    @MockBean
    private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;

    @MockBean
    private LieferantDokumentService dokumentService;

    @MockBean
    private GeminiDokumentAnalyseService analyseService;

    @MockBean
    private EmailRepository emailRepository;

    @MockBean
    private EmailAttachmentProcessingService emailAttachmentProcessingService;

    @MockBean
    private EmailAttachmentRepository emailAttachmentRepository;

    @Nested
    @DisplayName("PUT /api/lieferant-dokumente/{dokumentId}")
    class UpdateDokument {

        @Test
        @DisplayName("Aktualisiert Dokument erfolgreich")
        void aktualisiertDokumentErfolgreich() throws Exception {
            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(1L);
            dokument.setTyp(LieferantDokumentTyp.RECHNUNG);
            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setId(1L);
            dokument.setGeschaeftsdaten(gd);
            gd.setDokument(dokument);

            given(dokumentRepository.findById(1L)).willReturn(Optional.of(dokument));
            given(geschaeftsdokumentRepository.save(any())).willReturn(gd);
            given(dokumentRepository.save(any())).willReturn(dokument);

            LieferantDokumentDto.Response response = new LieferantDokumentDto.Response();
            response.setId(1L);
            given(dokumentService.getDokumentById(1L)).willReturn(response);

            String body = """
                    {
                        "typ": "RECHNUNG",
                        "geschaeftsdaten": {
                            "dokumentNummer": "RE-001",
                            "betragBrutto": 1190.00
                        }
                    }
                    """;

            mockMvc.perform(put("/api/lieferant-dokumente/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }

        @Test
        @DisplayName("Unbekanntes Dokument gibt 404")
        void unbekanntesDokumentGibt404() throws Exception {
            given(dokumentRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(put("/api/lieferant-dokumente/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"typ\": \"RECHNUNG\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("SQL Injection in Dokumentnummer wird als String gespeichert")
        void sqlInjectionInDokumentnummer() throws Exception {
            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(1L);
            LieferantGeschaeftsdokument gd = new LieferantGeschaeftsdokument();
            gd.setId(1L);
            dokument.setGeschaeftsdaten(gd);
            gd.setDokument(dokument);

            given(dokumentRepository.findById(1L)).willReturn(Optional.of(dokument));
            given(geschaeftsdokumentRepository.save(any())).willReturn(gd);
            given(dokumentRepository.save(any())).willReturn(dokument);

            LieferantDokumentDto.Response response = new LieferantDokumentDto.Response();
            response.setId(1L);
            given(dokumentService.getDokumentById(1L)).willReturn(response);

            String body = """
                    {
                        "geschaeftsdaten": {
                            "dokumentNummer": "'; DROP TABLE lieferant_dokumente; --"
                        }
                    }
                    """;

            mockMvc.perform(put("/api/lieferant-dokumente/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("XML Content-Type wird abgelehnt")
        void xmlContentTypeWirdAbgelehnt() throws Exception {
            mockMvc.perform(put("/api/lieferant-dokumente/1")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<dokument />"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("POST /api/lieferant-dokumente/{dokumentId}/reanalyze")
    class ReanalyzeDokument {

        @Test
        @DisplayName("Re-Analyse für unbekanntes Dokument gibt 404")
        void unbekanntesDokumentGibt404() throws Exception {
            given(dokumentRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(post("/api/lieferant-dokumente/999/reanalyze"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Re-Analyse erfolgreich")
        void reanalyseErfolgreich() throws Exception {
            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(1L);
            given(dokumentRepository.findById(1L)).willReturn(Optional.of(dokument));

            LieferantGeschaeftsdokument result = new LieferantGeschaeftsdokument();
            result.setDokumentNummer("RE-001");
            given(analyseService.analysiereDokument(any())).willReturn(result);

            LieferantDokumentDto.Response response = new LieferantDokumentDto.Response();
            response.setId(1L);
            given(dokumentService.getDokumentById(1L)).willReturn(response);

            mockMvc.perform(post("/api/lieferant-dokumente/1/reanalyze"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1));
        }
    }

    @Nested
    @DisplayName("POST /api/lieferant-dokumente/lieferant/{lieferantId}/reanalyze")
    class ReanalyzeByLieferant {

        @Test
        @DisplayName("Re-Analyse für Lieferant gibt Zusammenfassung")
        void reanalyseByLieferant() throws Exception {
            given(dokumentRepository.findByLieferantIdOrderByUploadDatumDesc(1L)).willReturn(List.of());

            mockMvc.perform(post("/api/lieferant-dokumente/lieferant/1/reanalyze"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lieferantId").value(1))
                    .andExpect(jsonPath("$.gesamt").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/lieferant-dokumente/relink-all")
    class RelinkAll {

        @Test
        @DisplayName("Relink gibt Ergebnis zurück")
        void relinkAll() throws Exception {
            given(analyseService.relinkAlleDokumente()).willReturn(3);

            mockMvc.perform(post("/api/lieferant-dokumente/relink-all"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.neuVerknuepft").value(3));
        }
    }

    @Nested
    @DisplayName("GET /api/lieferant-dokumente/{dokumentId}/download")
    class Download {

        @Test
        @DisplayName("Unbekanntes Dokument gibt 404")
        void unbekanntesDokumentGibt404() throws Exception {
            given(dokumentRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(get("/api/lieferant-dokumente/999/download"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Dokument ohne Datei gibt 404")
        void dokumentOhneDateiGibt404() throws Exception {
            LieferantDokument dokument = new LieferantDokument();
            dokument.setId(1L);
            // No gespeicherterDateiname and no attachment → resolveDokumentPath returns null
            given(dokumentRepository.findById(1L)).willReturn(Optional.of(dokument));

            mockMvc.perform(get("/api/lieferant-dokumente/1/download"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/lieferant-dokumente/duplicates")
    class Duplicates {

        @Test
        @DisplayName("Keine Duplikate gefunden")
        void keineDuplikate() throws Exception {
            given(geschaeftsdokumentRepository.findAllDuplicates()).willReturn(List.of());

            mockMvc.perform(get("/api/lieferant-dokumente/duplicates"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.duplikateGesamt").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/lieferant-dokumente/process-email/{emailId}")
    class ProcessEmail {

        @Test
        @DisplayName("Unbekannte Email gibt 404")
        void unbekannteEmailGibt404() throws Exception {
            given(emailRepository.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(post("/api/lieferant-dokumente/process-email/999"))
                    .andExpect(status().isNotFound());
        }
    }
}
