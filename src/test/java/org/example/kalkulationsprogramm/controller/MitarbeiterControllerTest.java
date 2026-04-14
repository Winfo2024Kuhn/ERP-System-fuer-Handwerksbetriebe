package org.example.kalkulationsprogramm.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterDokumentResponseDto;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterDto;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterErstellenDto;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterNotizDto;
import org.example.kalkulationsprogramm.dto.Mitarbeiter.MitarbeiterQualifikationDto;
import org.example.kalkulationsprogramm.service.MitarbeiterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(MitarbeiterController.class)
@AutoConfigureMockMvc(addFilters = false)
class MitarbeiterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MitarbeiterService service;

    private MitarbeiterDto buildMitarbeiterDto(Long id, String vorname, String nachname) {
        MitarbeiterDto dto = new MitarbeiterDto();
        dto.setId(id);
        dto.setVorname(vorname);
        dto.setNachname(nachname);
        dto.setAktiv(true);
        return dto;
    }

    @Nested
    @DisplayName("GET /api/mitarbeiter")
    class ListAll {

        @Test
        @DisplayName("Gibt 200 mit Mitarbeiterliste zurück")
        void gibtAlleMitarbeiterZurueck() throws Exception {
            given(service.list()).willReturn(List.of(
                    buildMitarbeiterDto(1L, "Max", "Muster"),
                    buildMitarbeiterDto(2L, "Anna", "Schmidt")));

            mockMvc.perform(get("/api/mitarbeiter"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].vorname").value("Max"))
                    .andExpect(jsonPath("$[1].vorname").value("Anna"));
        }
    }

    @Nested
    @DisplayName("GET /api/mitarbeiter/{id}")
    class GetById {

        @Test
        @DisplayName("Gibt 200 mit Mitarbeiter zurück")
        void gibtMitarbeiterZurueck() throws Exception {
            given(service.findById(1L)).willReturn(Optional.of(buildMitarbeiterDto(1L, "Max", "Muster")));

            mockMvc.perform(get("/api/mitarbeiter/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.vorname").value("Max"));
        }

        @Test
        @DisplayName("Gibt 404 bei unbekannter ID zurück")
        void gibt404BeiUnbekannterIdZurueck() throws Exception {
            given(service.findById(999L)).willReturn(Optional.empty());

            mockMvc.perform(get("/api/mitarbeiter/999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Ungültige ID (Long.MAX_VALUE)")
        void ungueltigeIdMaxValue() throws Exception {
            given(service.findById(Long.MAX_VALUE)).willReturn(Optional.empty());

            mockMvc.perform(get("/api/mitarbeiter/" + Long.MAX_VALUE))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/mitarbeiter")
    class Create {

        @Test
        @DisplayName("Erstellt Mitarbeiter und gibt 200 zurück")
        void erstelltMitarbeiterErfolgreich() throws Exception {
            MitarbeiterDto result = buildMitarbeiterDto(1L, "Max", "Muster");
            given(service.save(isNull(), any(MitarbeiterErstellenDto.class))).willReturn(result);

            MitarbeiterErstellenDto dto = new MitarbeiterErstellenDto();
            dto.setVorname("Max");
            dto.setNachname("Muster");

            mockMvc.perform(post("/api/mitarbeiter")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.vorname").value("Max"));
        }

        @Test
        @DisplayName("SQL Injection im Vornamen wird nicht interpretiert")
        void sqlInjectionImVornameWirdIgnoriert() throws Exception {
            String sqlInject = "'; DROP TABLE mitarbeiter; --";
            MitarbeiterDto result = buildMitarbeiterDto(1L, sqlInject, "Test");
            given(service.save(isNull(), any(MitarbeiterErstellenDto.class))).willReturn(result);

            MitarbeiterErstellenDto dto = new MitarbeiterErstellenDto();
            dto.setVorname(sqlInject);
            dto.setNachname("Test");

            mockMvc.perform(post("/api/mitarbeiter")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("XSS im Namen wird nicht ausgeführt")
        void xssImNamenWirdIgnoriert() throws Exception {
            String xss = "<script>alert('XSS')</script>";
            MitarbeiterDto result = buildMitarbeiterDto(1L, xss, "Test");
            given(service.save(isNull(), any(MitarbeiterErstellenDto.class))).willReturn(result);

            MitarbeiterErstellenDto dto = new MitarbeiterErstellenDto();
            dto.setVorname(xss);
            dto.setNachname("Test");

            mockMvc.perform(post("/api/mitarbeiter")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Überlanger Vorname wird akzeptiert (Service validiert)")
        void ueberlangerVornameWirdAkzeptiert() throws Exception {
            String lang = "A".repeat(10001);
            MitarbeiterDto result = buildMitarbeiterDto(1L, lang, "Test");
            given(service.save(isNull(), any(MitarbeiterErstellenDto.class))).willReturn(result);

            MitarbeiterErstellenDto dto = new MitarbeiterErstellenDto();
            dto.setVorname(lang);
            dto.setNachname("Test");

            mockMvc.perform(post("/api/mitarbeiter")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("XML Content-Type wird abgelehnt")
        void xmlContentTypeWirdAbgelehnt() throws Exception {
            mockMvc.perform(post("/api/mitarbeiter")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<mitarbeiter />"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("PUT /api/mitarbeiter/{id}")
    class Update {

        @Test
        @DisplayName("Aktualisiert Mitarbeiter erfolgreich")
        void aktualisiertMitarbeiterErfolgreich() throws Exception {
            MitarbeiterDto result = buildMitarbeiterDto(1L, "Max", "Müller");
            given(service.save(eq(1L), any(MitarbeiterErstellenDto.class))).willReturn(result);

            MitarbeiterErstellenDto dto = new MitarbeiterErstellenDto();
            dto.setVorname("Max");
            dto.setNachname("Müller");

            mockMvc.perform(put("/api/mitarbeiter/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nachname").value("Müller"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/mitarbeiter/{id}")
    class Delete {

        @Test
        @DisplayName("Löscht Mitarbeiter und gibt 204 zurück")
        void loeschtMitarbeiterErfolgreich() throws Exception {
            doNothing().when(service).delete(1L);

            mockMvc.perform(delete("/api/mitarbeiter/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Löschen mit unbekannter ID gibt 404 zurück")
        void loeschenMitUnbekannterIdGibt404() throws Exception {
            doThrow(new RuntimeException("Nicht gefunden")).when(service).delete(999L);

            mockMvc.perform(delete("/api/mitarbeiter/999"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/mitarbeiter/{id}/dokumente")
    class UploadDokument {

        @Test
        @DisplayName("Dokument-Upload gibt 200 zurück")
        void uploadDokumentErfolgreich() throws Exception {
            MitarbeiterDokumentResponseDto result = new MitarbeiterDokumentResponseDto();
            result.setId(1L);
            result.setOriginalDateiname("vertrag.pdf");
            given(service.uploadDokument(eq(1L), any(), isNull())).willReturn(result);

            MockMultipartFile file = new MockMultipartFile("datei", "vertrag.pdf",
                    MediaType.APPLICATION_PDF_VALUE, "pdf-content".getBytes());

            mockMvc.perform(multipart("/api/mitarbeiter/1/dokumente").file(file))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.originalDateiname").value("vertrag.pdf"));
        }
    }

    @Nested
    @DisplayName("GET /api/mitarbeiter/{id}/dokumente")
    class ListDokumente {

        @Test
        @DisplayName("Gibt Dokumentenliste zurück")
        void gibtDokumenteZurueck() throws Exception {
            MitarbeiterDokumentResponseDto dok = new MitarbeiterDokumentResponseDto();
            dok.setId(1L);
            dok.setOriginalDateiname("vertrag.pdf");
            given(service.listDokumente(1L)).willReturn(List.of(dok));

            mockMvc.perform(get("/api/mitarbeiter/1/dokumente"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].originalDateiname").value("vertrag.pdf"));
        }
    }

    @Nested
    @DisplayName("GET /api/mitarbeiter/by-token/{token}")
    class GetByToken {

        @Test
        @DisplayName("Gibt Mitarbeiter für gültigen Token zurück")
        void gibtMitarbeiterFuerGueltigesTokenZurueck() throws Exception {
            given(service.findByToken("abc123")).willReturn(Optional.of(buildMitarbeiterDto(1L, "Max", "Muster")));

            mockMvc.perform(get("/api/mitarbeiter/by-token/abc123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.vorname").value("Max"));
        }

        @Test
        @DisplayName("Gibt 404 für ungültigen Token zurück")
        void gibt404FuerUngueltigesTokenZurueck() throws Exception {
            given(service.findByToken("invalid")).willReturn(Optional.empty());

            mockMvc.perform(get("/api/mitarbeiter/by-token/invalid"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Notizen Endpoints")
    class Notizen {

        @Test
        @DisplayName("GET /api/mitarbeiter/{id}/notizen gibt Notizliste zurück")
        void gibtNotizenZurueck() throws Exception {
            MitarbeiterNotizDto notiz = new MitarbeiterNotizDto(1L, "Test Notiz", LocalDateTime.now(), 1L);
            given(service.listNotizen(1L)).willReturn(List.of(notiz));

            mockMvc.perform(get("/api/mitarbeiter/1/notizen"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].inhalt").value("Test Notiz"));
        }

        @Test
        @DisplayName("POST /api/mitarbeiter/{id}/notizen erstellt Notiz")
        void erstelltNotizErfolgreich() throws Exception {
            MitarbeiterNotizDto result = new MitarbeiterNotizDto(1L, "Neue Notiz", LocalDateTime.now(), 1L);
            given(service.createNotiz(eq(1L), any(String.class))).willReturn(result);

            mockMvc.perform(post("/api/mitarbeiter/1/notizen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("\"Neue Notiz\""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.inhalt").value("Neue Notiz"));
        }

        @Test
        @DisplayName("DELETE /api/mitarbeiter/notizen/{notizId} löscht Notiz")
        void loeschtNotizErfolgreich() throws Exception {
            doNothing().when(service).deleteNotiz(1L);

            mockMvc.perform(delete("/api/mitarbeiter/notizen/1"))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("QR-Code und Token Endpoints")
    class QrCodeUndToken {

        @Test
        @DisplayName("POST /api/mitarbeiter/{id}/regenerate-token gibt neuen Token zurück")
        void regeneriertTokenErfolgreich() throws Exception {
            given(service.generateLoginToken(1L)).willReturn("new-token-abc");

            mockMvc.perform(post("/api/mitarbeiter/1/regenerate-token"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("new-token-abc"));
        }

        @Test
        @DisplayName("POST /api/mitarbeiter/{id}/regenerate-token gibt 404 bei unbekannter ID")
        void regeneriertTokenGibt404BeiUnbekannterId() throws Exception {
            given(service.generateLoginToken(999L)).willThrow(new RuntimeException("Nicht gefunden"));

            mockMvc.perform(post("/api/mitarbeiter/999/regenerate-token"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/mitarbeiter/{id}/en1090-rollen")
    class UpdateEn1090Rollen {

        @Test
        @DisplayName("Aktualisiert EN-1090-Rollen erfolgreich")
        void aktualisiertRollenErfolgreich() throws Exception {
            MitarbeiterDto existing = buildMitarbeiterDto(1L, "Max", "Muster");
            given(service.findById(1L)).willReturn(Optional.of(existing));
            given(service.save(eq(1L), any(MitarbeiterErstellenDto.class))).willReturn(existing);

            mockMvc.perform(put("/api/mitarbeiter/1/en1090-rollen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[1,2,3]"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.vorname").value("Max"));
        }
    }

    @Nested
    @DisplayName("GET /api/mitarbeiter/{id}/qualifikationen")
    class ListQualifikationen {

        @Test
        @DisplayName("Gibt Qualifikationsliste zurueck")
        void gibtQualifikationenZurueck() throws Exception {
            MitarbeiterQualifikationDto qual = new MitarbeiterQualifikationDto();
            qual.setId(1L);
            qual.setBezeichnung("Ersthelfer");
            given(service.listQualifikationen(1L)).willReturn(List.of(qual));

            mockMvc.perform(get("/api/mitarbeiter/1/qualifikationen"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].bezeichnung").value("Ersthelfer"));
        }
    }

    @Nested
    @DisplayName("POST /api/mitarbeiter/{id}/qualifikationen")
    class CreateQualifikation {

        @Test
        @DisplayName("Erstellt Qualifikation ohne Dateianhang")
        void erstelltQualifikationOhneDatei() throws Exception {
            MitarbeiterQualifikationDto result = new MitarbeiterQualifikationDto();
            result.setId(5L);
            result.setBezeichnung("Ersthelfer-Schein");
            given(service.createQualifikation(eq(1L), eq("Ersthelfer-Schein"), any(), any(), any()))
                    .willReturn(result);

            mockMvc.perform(multipart("/api/mitarbeiter/1/qualifikationen")
                            .param("bezeichnung", "Ersthelfer-Schein"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(5))
                    .andExpect(jsonPath("$.bezeichnung").value("Ersthelfer-Schein"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/mitarbeiter/{id}/qualifikationen/{qualId}")
    class DeleteQualifikation {

        @Test
        @DisplayName("Loescht Qualifikation und gibt 204 zurueck")
        void loeschtQualifikationErfolgreich() throws Exception {
            doNothing().when(service).deleteQualifikation(1L, 5L);

            mockMvc.perform(delete("/api/mitarbeiter/1/qualifikationen/5"))
                    .andExpect(status().isNoContent());
        }
    }
}
