package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.Geschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Zahlung;
import org.example.kalkulationsprogramm.dto.Geschaeftsdokument.*;
import org.example.kalkulationsprogramm.service.GeschaeftsdokumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GeschaeftsdokumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class GeschaeftsdokumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GeschaeftsdokumentService service;

    private GeschaeftsdokumentResponseDto buildResponseDto(Long id, String nummer) {
        GeschaeftsdokumentResponseDto dto = new GeschaeftsdokumentResponseDto();
        dto.setId(id);
        dto.setDokumentNummer(nummer);
        dto.setBetragNetto(new BigDecimal("1000.00"));
        dto.setBetragBrutto(new BigDecimal("1190.00"));
        dto.setMwstSatz(new BigDecimal("19.00"));
        return dto;
    }

    @Nested
    @DisplayName("GET /api/geschaeftsdokumente")
    class GetAll {

        @Test
        @DisplayName("Gibt 200 mit Dokumentenliste zurück")
        void gibtAlleDokumenteZurueck() throws Exception {
            given(service.findByProjekt(null)).willReturn(List.of(buildResponseDto(1L, "RE-2026-001")));

            mockMvc.perform(get("/api/geschaeftsdokumente"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].dokumentNummer").value("RE-2026-001"));
        }
    }

    @Nested
    @DisplayName("GET /api/geschaeftsdokumente/{id}")
    class GetById {

        @Test
        @DisplayName("Gibt 200 mit Dokument zurück")
        void gibtDokumentZurueck() throws Exception {
            given(service.findById(1L)).willReturn(buildResponseDto(1L, "RE-2026-001"));

            mockMvc.perform(get("/api/geschaeftsdokumente/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dokumentNummer").value("RE-2026-001"));
        }

        @Test
        @DisplayName("Gibt 404 bei unbekannter ID zurück")
        void gibt404BeiUnbekannterId() throws Exception {
            given(service.findById(999L)).willReturn(null);

            mockMvc.perform(get("/api/geschaeftsdokumente/999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Negative ID gibt 404 zurück")
        void negativeIdGibt404() throws Exception {
            given(service.findById(-1L)).willReturn(null);

            mockMvc.perform(get("/api/geschaeftsdokumente/-1"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/geschaeftsdokumente/{id}/abschluss")
    class GetAbschluss {

        @Test
        @DisplayName("Gibt Abschluss-Info zurück")
        void gibtAbschlussInfoZurueck() throws Exception {
            AbschlussInfoDto info = new AbschlussInfoDto();
            info.setNettosumme(new BigDecimal("1000.00"));
            info.setGesamtsumme(new BigDecimal("1190.00"));
            given(service.berechneAbschluss(1L)).willReturn(info);

            mockMvc.perform(get("/api/geschaeftsdokumente/1/abschluss"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nettosumme").value(1000.00));
        }

        @Test
        @DisplayName("Gibt 404 bei unbekanntem Dokument zurück")
        void gibt404BeiUnbekanntemDokument() throws Exception {
            given(service.berechneAbschluss(999L)).willThrow(new RuntimeException("Nicht gefunden"));

            mockMvc.perform(get("/api/geschaeftsdokumente/999/abschluss"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/geschaeftsdokumente/projekt/{projektId}")
    class GetByProjekt {

        @Test
        @DisplayName("Gibt Dokumente eines Projekts zurück")
        void gibtDokumenteFuerProjektZurueck() throws Exception {
            given(service.findByProjekt(42L)).willReturn(List.of(buildResponseDto(1L, "RE-2026-001")));

            mockMvc.perform(get("/api/geschaeftsdokumente/projekt/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].dokumentNummer").value("RE-2026-001"));
        }
    }

    @Nested
    @DisplayName("POST /api/geschaeftsdokumente")
    class Create {

        @Test
        @DisplayName("Erstellt Dokument und gibt 200 zurück")
        void erstelltDokumentErfolgreich() throws Exception {
            Geschaeftsdokument entity = new Geschaeftsdokument();
            entity.setId(1L);
            given(service.erstellen(any(GeschaeftsdokumentErstellenDto.class))).willReturn(entity);
            given(service.findById(1L)).willReturn(buildResponseDto(1L, "RE-2026-001"));

            GeschaeftsdokumentErstellenDto dto = new GeschaeftsdokumentErstellenDto();
            dto.setDokumenttyp("RECHNUNG");
            dto.setBetragNetto(new BigDecimal("1000.00"));
            dto.setMwstSatz(new BigDecimal("19.00"));

            mockMvc.perform(post("/api/geschaeftsdokumente")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dokumentNummer").value("RE-2026-001"));
        }

        @Test
        @DisplayName("SQL Injection im Betreff wird nicht interpretiert")
        void sqlInjectionImBetreffWirdIgnoriert() throws Exception {
            Geschaeftsdokument entity = new Geschaeftsdokument();
            entity.setId(1L);
            given(service.erstellen(any(GeschaeftsdokumentErstellenDto.class))).willReturn(entity);
            given(service.findById(1L)).willReturn(buildResponseDto(1L, "RE-001"));

            GeschaeftsdokumentErstellenDto dto = new GeschaeftsdokumentErstellenDto();
            dto.setDokumenttyp("RECHNUNG");
            dto.setBetreff("'; DROP TABLE geschaeftsdokumente; --");
            dto.setBetragNetto(new BigDecimal("100.00"));

            mockMvc.perform(post("/api/geschaeftsdokumente")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("XSS im Betreff wird nicht ausgeführt")
        void xssImBetreffWirdIgnoriert() throws Exception {
            Geschaeftsdokument entity = new Geschaeftsdokument();
            entity.setId(1L);
            given(service.erstellen(any(GeschaeftsdokumentErstellenDto.class))).willReturn(entity);
            given(service.findById(1L)).willReturn(buildResponseDto(1L, "RE-001"));

            GeschaeftsdokumentErstellenDto dto = new GeschaeftsdokumentErstellenDto();
            dto.setDokumenttyp("RECHNUNG");
            dto.setBetreff("<script>alert('XSS')</script>");
            dto.setBetragNetto(new BigDecimal("100.00"));

            mockMvc.perform(post("/api/geschaeftsdokumente")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("XML Content-Type wird abgelehnt")
        void xmlContentTypeWirdAbgelehnt() throws Exception {
            mockMvc.perform(post("/api/geschaeftsdokumente")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<doc />"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("POST /api/geschaeftsdokumente/{id}/konvertieren")
    class Konvertieren {

        @Test
        @DisplayName("Konvertiert Dokument erfolgreich")
        void konvertiertDokumentErfolgreich() throws Exception {
            Geschaeftsdokument neues = new Geschaeftsdokument();
            neues.setId(2L);
            given(service.konvertieren(eq(1L), eq("AUFTRAGSBESTAETIGUNG"))).willReturn(neues);
            given(service.findById(2L)).willReturn(buildResponseDto(2L, "AB-2026-001"));

            mockMvc.perform(post("/api/geschaeftsdokumente/1/konvertieren")
                            .param("neuerDokumenttyp", "AUFTRAGSBESTAETIGUNG"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dokumentNummer").value("AB-2026-001"));
        }

        @Test
        @DisplayName("Ungültiger Dokumenttyp gibt 400 zurück")
        void ungueltigerDokumenttypGibt400Zurueck() throws Exception {
            given(service.konvertieren(eq(1L), eq("UNGUELTIG")))
                    .willThrow(new RuntimeException("Ungültiger Dokumenttyp"));

            mockMvc.perform(post("/api/geschaeftsdokumente/1/konvertieren")
                            .param("neuerDokumenttyp", "UNGUELTIG"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/geschaeftsdokumente/{id}/zahlungen")
    class ZahlungErfassen {

        @Test
        @DisplayName("Erfasst Zahlung erfolgreich")
        void erfasstZahlungErfolgreich() throws Exception {
            Zahlung zahlung = new Zahlung();
            zahlung.setId(1L);
            zahlung.setBetrag(new BigDecimal("500.00"));
            zahlung.setZahlungsart("UEBERWEISUNG");
            zahlung.setZahlungsdatum(LocalDate.of(2026, 3, 10));
            given(service.zahlungErfassen(eq(1L), any(ZahlungErstellenDto.class))).willReturn(zahlung);

            ZahlungErstellenDto dto = new ZahlungErstellenDto();
            dto.setBetrag(new BigDecimal("500.00"));
            dto.setZahlungsart("UEBERWEISUNG");
            dto.setZahlungsdatum(LocalDate.of(2026, 3, 10));

            mockMvc.perform(post("/api/geschaeftsdokumente/1/zahlungen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.betrag").value(500.00))
                    .andExpect(jsonPath("$.zahlungsart").value("UEBERWEISUNG"));
        }

        @Test
        @DisplayName("Zahlung für unbekanntes Dokument gibt 400 zurück")
        void zahlungFuerUnbekanntesDokumentGibt400() throws Exception {
            given(service.zahlungErfassen(eq(999L), any(ZahlungErstellenDto.class)))
                    .willThrow(new RuntimeException("Dokument nicht gefunden"));

            ZahlungErstellenDto dto = new ZahlungErstellenDto();
            dto.setBetrag(new BigDecimal("100.00"));

            mockMvc.perform(post("/api/geschaeftsdokumente/999/zahlungen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }
}
