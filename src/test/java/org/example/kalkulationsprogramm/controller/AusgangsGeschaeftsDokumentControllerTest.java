package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentErstellenDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentResponseDto;
import org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AusgangsGeschaeftsDokumentUpdateDto;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AusgangsGeschaeftsDokumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class AusgangsGeschaeftsDokumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AusgangsGeschaeftsDokumentService service;

    private AusgangsGeschaeftsDokumentResponseDto buildResponseDto(Long id, String nummer) {
        AusgangsGeschaeftsDokumentResponseDto dto = new AusgangsGeschaeftsDokumentResponseDto();
        dto.setId(id);
        dto.setDokumentNummer(nummer);
        dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
        dto.setBetragNetto(new BigDecimal("1000.00"));
        dto.setBetragBrutto(new BigDecimal("1190.00"));
        dto.setBearbeitbar(true);
        return dto;
    }

    @Nested
    @DisplayName("GET /api/ausgangs-dokumente/{id}")
    class GetById {

        @Test
        @DisplayName("Gibt 200 mit Dokument zurück")
        void gibtDokumentZurueck() throws Exception {
            given(service.findById(1L)).willReturn(buildResponseDto(1L, "RE-2026-001"));

            mockMvc.perform(get("/api/ausgangs-dokumente/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dokumentNummer").value("RE-2026-001"));
        }

        @Test
        @DisplayName("Gibt 404 bei unbekannter ID")
        void gibt404BeiUnbekannterId() throws Exception {
            given(service.findById(999L)).willReturn(null);

            mockMvc.perform(get("/api/ausgangs-dokumente/999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Ungültige ID Long.MAX_VALUE")
        void ungueltigeIdMaxValue() throws Exception {
            given(service.findById(Long.MAX_VALUE)).willReturn(null);

            mockMvc.perform(get("/api/ausgangs-dokumente/" + Long.MAX_VALUE))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/ausgangs-dokumente/projekt/{projektId}")
    class GetByProjekt {

        @Test
        @DisplayName("Gibt Dokumente eines Projekts zurück")
        void gibtDokumenteFuerProjektZurueck() throws Exception {
            given(service.findByProjekt(42L)).willReturn(List.of(buildResponseDto(1L, "RE-2026-001")));

            mockMvc.perform(get("/api/ausgangs-dokumente/projekt/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].dokumentNummer").value("RE-2026-001"));
        }
    }

    @Nested
    @DisplayName("GET /api/ausgangs-dokumente/anfrage/{anfrageId}")
    class GetByAnfrage {

        @Test
        @DisplayName("Gibt Dokumente eines Anfrages zurück")
        void gibtDokumenteFuerAnfrageZurueck() throws Exception {
            given(service.findByAnfrage(10L)).willReturn(List.of(buildResponseDto(1L, "RE-2026-001")));

            mockMvc.perform(get("/api/ausgangs-dokumente/anfrage/10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].dokumentNummer").value("RE-2026-001"));
        }
    }

    @Nested
    @DisplayName("POST /api/ausgangs-dokumente")
    class Create {

        @Test
        @DisplayName("Erstellt Dokument und gibt 200 zurück")
        void erstelltDokumentErfolgreich() throws Exception {
            AusgangsGeschaeftsDokument entity = new AusgangsGeschaeftsDokument();
            entity.setId(1L);
            given(service.erstellen(any(AusgangsGeschaeftsDokumentErstellenDto.class))).willReturn(entity);
            given(service.findById(1L)).willReturn(buildResponseDto(1L, "RE-2026-001"));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dto.setBetragNetto(new BigDecimal("1000.00"));
            dto.setMwstSatz(new BigDecimal("19.00"));

            mockMvc.perform(post("/api/ausgangs-dokumente")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dokumentNummer").value("RE-2026-001"));
        }

        @Test
        @DisplayName("Erstellung mit ungültigen Daten gibt 400 zurück")
        void erstellungMitUngueltigemDtoGibt400() throws Exception {
            given(service.erstellen(any(AusgangsGeschaeftsDokumentErstellenDto.class)))
                    .willThrow(new RuntimeException("Betrag muss positiv sein"));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dto.setBetragNetto(new BigDecimal("-100.00"));

            mockMvc.perform(post("/api/ausgangs-dokumente")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("SQL Injection im Betreff")
        void sqlInjectionImBetreff() throws Exception {
            AusgangsGeschaeftsDokument entity = new AusgangsGeschaeftsDokument();
            entity.setId(1L);
            given(service.erstellen(any(AusgangsGeschaeftsDokumentErstellenDto.class))).willReturn(entity);
            given(service.findById(1L)).willReturn(buildResponseDto(1L, "RE-001"));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dto.setBetreff("'; DROP TABLE dokumente; --");
            dto.setBetragNetto(new BigDecimal("100.00"));

            mockMvc.perform(post("/api/ausgangs-dokumente")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("XSS in HTML-Inhalt")
        void xssInHtmlInhalt() throws Exception {
            AusgangsGeschaeftsDokument entity = new AusgangsGeschaeftsDokument();
            entity.setId(1L);
            given(service.erstellen(any(AusgangsGeschaeftsDokumentErstellenDto.class))).willReturn(entity);
            given(service.findById(1L)).willReturn(buildResponseDto(1L, "RE-001"));

            AusgangsGeschaeftsDokumentErstellenDto dto = new AusgangsGeschaeftsDokumentErstellenDto();
            dto.setTyp(AusgangsGeschaeftsDokumentTyp.RECHNUNG);
            dto.setHtmlInhalt("<script>alert('XSS')</script>");
            dto.setBetragNetto(new BigDecimal("100.00"));

            mockMvc.perform(post("/api/ausgangs-dokumente")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("XML Content-Type wird abgelehnt")
        void xmlContentTypeWirdAbgelehnt() throws Exception {
            mockMvc.perform(post("/api/ausgangs-dokumente")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<doc />"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("PUT /api/ausgangs-dokumente/{id}")
    class Update {

        @Test
        @DisplayName("Aktualisiert Dokument erfolgreich")
        void aktualisiertDokumentErfolgreich() throws Exception {
            AusgangsGeschaeftsDokument entity = new AusgangsGeschaeftsDokument();
            entity.setId(1L);
            given(service.aktualisieren(eq(1L), any(AusgangsGeschaeftsDokumentUpdateDto.class))).willReturn(entity);
            given(service.findById(1L)).willReturn(buildResponseDto(1L, "RE-2026-001"));

            AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();
            dto.setBetreff("Aktualisiert");
            dto.setBetragNetto(new BigDecimal("2000.00"));

            mockMvc.perform(put("/api/ausgangs-dokumente/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Update eines gebuchten Dokuments gibt 400 zurück")
        void updateGebuchtesDokumentGibt400() throws Exception {
            given(service.aktualisieren(eq(1L), any(AusgangsGeschaeftsDokumentUpdateDto.class)))
                    .willThrow(new RuntimeException("Dokument ist gebucht"));

            AusgangsGeschaeftsDokumentUpdateDto dto = new AusgangsGeschaeftsDokumentUpdateDto();
            dto.setBetreff("Versuch");

            mockMvc.perform(put("/api/ausgangs-dokumente/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/ausgangs-dokumente/{id}/buchen")
    class Buchen {

        @Test
        @DisplayName("Bucht Dokument erfolgreich")
        void buchtDokumentErfolgreich() throws Exception {
            AusgangsGeschaeftsDokument entity = new AusgangsGeschaeftsDokument();
            entity.setId(1L);
            given(service.buchen(1L)).willReturn(entity);
            given(service.findById(1L)).willReturn(buildResponseDto(1L, "RE-2026-001"));

            mockMvc.perform(post("/api/ausgangs-dokumente/1/buchen"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Doppeltes Buchen gibt 400 zurück")
        void doppeltessBuchenGibt400() throws Exception {
            given(service.buchen(1L)).willThrow(new RuntimeException("Bereits gebucht"));

            mockMvc.perform(post("/api/ausgangs-dokumente/1/buchen"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/ausgangs-dokumente/{id}/email-versendet")
    class EmailVersendet {

        @Test
        @DisplayName("Bucht nach E-Mail-Versand erfolgreich")
        void buchtNachEmailVersandErfolgreich() throws Exception {
            given(service.buchenNachEmailVersand(1L)).willReturn(new AusgangsGeschaeftsDokument());
            given(service.findById(1L)).willReturn(buildResponseDto(1L, "RE-2026-001"));

            mockMvc.perform(post("/api/ausgangs-dokumente/1/email-versendet"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /api/ausgangs-dokumente/{id}/pdf-speichern")
    class PdfSpeichern {

        @Test
        @DisplayName("Speichert PDF erfolgreich")
        void speichertPdfErfolgreich() throws Exception {
            doNothing().when(service).speicherePdfFuerDokument(eq(1L), any(byte[].class));

            mockMvc.perform(post("/api/ausgangs-dokumente/1/pdf-speichern")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content(new byte[]{0x25, 0x50, 0x44, 0x46}))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Leere PDF-Daten gibt 400 zurück")
        void leerePdfDatenGibt400() throws Exception {
            mockMvc.perform(post("/api/ausgangs-dokumente/1/pdf-speichern")
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .content(new byte[0]))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/ausgangs-dokumente/{id}/pdf")
    class GetPdf {

        @Test
        @DisplayName("Gibt 404 zurück (Fallback-Endpoint)")
        void gibt404Zurueck() throws Exception {
            mockMvc.perform(get("/api/ausgangs-dokumente/1/pdf"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/ausgangs-dokumente/{id}/storno")
    class Stornieren {

        @Test
        @DisplayName("Storniert Dokument erfolgreich")
        void storniertDokumentErfolgreich() throws Exception {
            AusgangsGeschaeftsDokument storno = new AusgangsGeschaeftsDokument();
            storno.setId(2L);
            given(service.stornieren(1L)).willReturn(storno);
            given(service.findById(2L)).willReturn(buildResponseDto(2L, "ST-2026-001"));

            mockMvc.perform(post("/api/ausgangs-dokumente/1/storno"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dokumentNummer").value("ST-2026-001"));
        }

        @Test
        @DisplayName("Stornierung eines bereits stornierten Dokuments gibt 400")
        void doppelteStornierungGibt400() throws Exception {
            given(service.stornieren(1L)).willThrow(new RuntimeException("Bereits storniert"));

            mockMvc.perform(post("/api/ausgangs-dokumente/1/storno"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/ausgangs-dokumente/{id}")
    class Delete {

        @Test
        @DisplayName("Löscht Dokument mit Begründung")
        void loeschtDokumentMitBegruendung() throws Exception {
            given(service.findById(1L)).willReturn(buildResponseDto(1L, "RE-2026-001"));
            doNothing().when(service).loeschen(eq(1L), eq("Falsch erstellt"));

            mockMvc.perform(delete("/api/ausgangs-dokumente/1")
                            .param("begruendung", "Falsch erstellt"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Löschen eines unbekannten Dokuments gibt 404")
        void loeschenUnbekanntGibt404() throws Exception {
            given(service.findById(999L)).willReturn(null);

            mockMvc.perform(delete("/api/ausgangs-dokumente/999")
                            .param("begruendung", "Test"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Löschen eines gebuchten Dokuments gibt 400")
        void loeschenGebuchtGibt400() throws Exception {
            given(service.findById(1L)).willReturn(buildResponseDto(1L, "RE-001"));
            doThrow(new RuntimeException("Gebuchte Dokumente können nicht gelöscht werden"))
                    .when(service).loeschen(eq(1L), any());

            mockMvc.perform(delete("/api/ausgangs-dokumente/1")
                            .param("begruendung", "Test"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/ausgangs-dokumente/{id}/abrechnungsverlauf")
    class Abrechnungsverlauf {

        @Test
        @DisplayName("Gibt Abrechnungsverlauf zurück")
        void gibtAbrechnungsverlaufZurueck() throws Exception {
            given(service.getAbrechnungsverlauf(1L)).willReturn(new org.example.kalkulationsprogramm.dto.AusgangsGeschaeftsDokument.AbrechnungsverlaufDto());

            mockMvc.perform(get("/api/ausgangs-dokumente/1/abrechnungsverlauf"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Unbekanntes Dokument gibt 400")
        void unbekanntGibt400() throws Exception {
            given(service.getAbrechnungsverlauf(999L)).willThrow(new RuntimeException("Nicht gefunden"));

            mockMvc.perform(get("/api/ausgangs-dokumente/999/abrechnungsverlauf"))
                    .andExpect(status().isBadRequest());
        }
    }
}
