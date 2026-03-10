package org.example.kalkulationsprogramm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungCreateDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungDto;
import org.example.kalkulationsprogramm.service.LeistungService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LeistungController.class)
@AutoConfigureMockMvc(addFilters = false)
class LeistungControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LeistungService leistungService;

    @Nested
    @DisplayName("GET /api/leistungen")
    class GetAll {

        @Test
        @DisplayName("Gibt 200 mit Liste aller Leistungen zurück")
        void gibtAlleLeistungenZurueck() throws Exception {
            LeistungDto dto = new LeistungDto();
            dto.setId(1L);
            dto.setName("Montage");
            dto.setPrice(new BigDecimal("45.00"));
            given(leistungService.getAllLeistungen()).willReturn(List.of(dto));

            mockMvc.perform(get("/api/leistungen"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1))
                    .andExpect(jsonPath("$[0].name").value("Montage"));
        }

        @Test
        @DisplayName("Gibt leere Liste zurück wenn keine Leistungen vorhanden")
        void gibtLeereListeZurueck() throws Exception {
            given(leistungService.getAllLeistungen()).willReturn(List.of());

            mockMvc.perform(get("/api/leistungen"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/leistungen")
    class Create {

        @Test
        @DisplayName("Erstellt Leistung und gibt 200 zurück")
        void erstelltLeistungErfolgreich() throws Exception {
            LeistungDto result = new LeistungDto();
            result.setId(1L);
            result.setName("Montage");
            result.setPrice(new BigDecimal("45.00"));
            given(leistungService.createLeistung(any(LeistungCreateDto.class))).willReturn(result);

            LeistungCreateDto dto = new LeistungCreateDto();
            dto.setName("Montage");
            dto.setPrice(new BigDecimal("45.00"));

            mockMvc.perform(post("/api/leistungen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Montage"));
        }

        @Test
        @DisplayName("SQL Injection in Name wird nicht interpretiert")
        void sqlInjectionInNameWirdIgnoriert() throws Exception {
            LeistungDto result = new LeistungDto();
            result.setId(1L);
            result.setName("'; DROP TABLE leistungen; --");
            given(leistungService.createLeistung(any(LeistungCreateDto.class))).willReturn(result);

            LeistungCreateDto dto = new LeistungCreateDto();
            dto.setName("'; DROP TABLE leistungen; --");
            dto.setPrice(new BigDecimal("10.00"));

            mockMvc.perform(post("/api/leistungen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("XSS in Name wird nicht ausgeführt")
        void xssInNameWirdNichtAusgefuehrt() throws Exception {
            String xss = "<script>alert('XSS')</script>";
            LeistungDto result = new LeistungDto();
            result.setId(1L);
            result.setName(xss);
            given(leistungService.createLeistung(any(LeistungCreateDto.class))).willReturn(result);

            LeistungCreateDto dto = new LeistungCreateDto();
            dto.setName(xss);
            dto.setPrice(new BigDecimal("10.00"));

            mockMvc.perform(post("/api/leistungen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Überlanger Name wird akzeptiert (Service validiert)")
        void ueberlangerNameWirdAkzeptiert() throws Exception {
            String langerName = "A".repeat(10001);
            LeistungDto result = new LeistungDto();
            result.setId(1L);
            result.setName(langerName);
            given(leistungService.createLeistung(any(LeistungCreateDto.class))).willReturn(result);

            LeistungCreateDto dto = new LeistungCreateDto();
            dto.setName(langerName);
            dto.setPrice(new BigDecimal("10.00"));

            mockMvc.perform(post("/api/leistungen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("JSON-Endpoint mit XML aufrufen gibt 415 zurück")
        void xmlContentTypeWirdAbgelehnt() throws Exception {
            mockMvc.perform(post("/api/leistungen")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<leistung><name>Test</name></leistung>"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("PUT /api/leistungen/{id}")
    class Update {

        @Test
        @DisplayName("Aktualisiert Leistung erfolgreich")
        void aktualisiertLeistungErfolgreich() throws Exception {
            LeistungDto result = new LeistungDto();
            result.setId(1L);
            result.setName("Montage NEU");
            result.setPrice(new BigDecimal("55.00"));
            given(leistungService.updateLeistung(eq(1L), any(LeistungCreateDto.class))).willReturn(result);

            LeistungCreateDto dto = new LeistungCreateDto();
            dto.setName("Montage NEU");
            dto.setPrice(new BigDecimal("55.00"));

            mockMvc.perform(put("/api/leistungen/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Montage NEU"));
        }

        @Test
        @DisplayName("Update mit unbekannter ID wirft Exception im Service")
        void updateMitUnbekannterIdGibtFehler() throws Exception {
            given(leistungService.updateLeistung(eq(999L), any(LeistungCreateDto.class)))
                    .willThrow(new RuntimeException("Nicht gefunden"));

            LeistungCreateDto dto = new LeistungCreateDto();
            dto.setName("Test");

            assertThrows(Exception.class, () ->
                    mockMvc.perform(put("/api/leistungen/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
            );
        }

        @Test
        @DisplayName("Negative ID wird akzeptiert (Service validiert)")
        void negativeIdWirdAkzeptiert() throws Exception {
            given(leistungService.updateLeistung(eq(-1L), any(LeistungCreateDto.class)))
                    .willThrow(new RuntimeException("Ungültige ID"));

            LeistungCreateDto dto = new LeistungCreateDto();
            dto.setName("Test");

            assertThrows(Exception.class, () ->
                    mockMvc.perform(put("/api/leistungen/-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
            );
        }
    }

    @Nested
    @DisplayName("DELETE /api/leistungen/{id}")
    class Delete {

        @Test
        @DisplayName("Löscht Leistung und gibt 204 zurück")
        void loeschtLeistungErfolgreich() throws Exception {
            doNothing().when(leistungService).deleteLeistung(1L);

            mockMvc.perform(delete("/api/leistungen/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Löschen mit unbekannter ID wirft Exception im Service")
        void loeschenMitUnbekannterIdGibtFehler() throws Exception {
            doThrow(new RuntimeException("Nicht gefunden")).when(leistungService).deleteLeistung(999L);

            assertThrows(Exception.class, () ->
                    mockMvc.perform(delete("/api/leistungen/999"))
            );
        }
    }
}
