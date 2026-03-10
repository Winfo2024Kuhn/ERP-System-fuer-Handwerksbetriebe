package org.example.kalkulationsprogramm.controller.miete;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.miete.Kostenstelle;
import org.example.kalkulationsprogramm.domain.miete.Kostenposition;
import org.example.kalkulationsprogramm.domain.miete.Verteilungsschluessel;
import org.example.kalkulationsprogramm.dto.miete.KostenpositionDto;
import org.example.kalkulationsprogramm.dto.miete.KostenstelleDto;
import org.example.kalkulationsprogramm.dto.miete.VerteilungsschluesselDto;
import org.example.kalkulationsprogramm.mapper.MieteMapper;
import org.example.kalkulationsprogramm.service.miete.KostenVerteilungService;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KostenVerteilungController.class)
@AutoConfigureMockMvc(addFilters = false)
class KostenVerteilungControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KostenVerteilungService kostenVerteilungService;

    @MockBean
    private MieteMapper mapper;

    @Nested
    @DisplayName("Kostenstellen CRUD")
    class KostenstellenCrud {

        @Test
        @DisplayName("GET /api/miete/mietobjekte/{id}/kostenstellen gibt Liste zurück")
        void listKostenstellen() throws Exception {
            Kostenstelle entity = new Kostenstelle();
            entity.setId(1L);
            given(kostenVerteilungService.getKostenstellen(1L)).willReturn(List.of(entity));
            KostenstelleDto dto = new KostenstelleDto();
            dto.setId(1L);
            dto.setName("Heizung");
            given(mapper.toDto(any(Kostenstelle.class))).willReturn(dto);

            mockMvc.perform(get("/api/miete/mietobjekte/1/kostenstellen"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Heizung"));
        }

        @Test
        @DisplayName("POST erstellt Kostenstelle (201)")
        void createKostenstelle() throws Exception {
            Kostenstelle entity = new Kostenstelle();
            entity.setId(1L);
            given(mapper.toEntity(any(KostenstelleDto.class))).willReturn(entity);
            given(kostenVerteilungService.saveKostenstelle(eq(1L), any(Kostenstelle.class))).willReturn(entity);
            KostenstelleDto dto = new KostenstelleDto();
            dto.setId(1L);
            dto.setName("Wasser");
            given(mapper.toDto(any(Kostenstelle.class))).willReturn(dto);

            KostenstelleDto request = new KostenstelleDto();
            request.setName("Wasser");

            mockMvc.perform(post("/api/miete/mietobjekte/1/kostenstellen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Wasser"));
        }

        @Test
        @DisplayName("PUT aktualisiert Kostenstelle")
        void updateKostenstelle() throws Exception {
            Kostenstelle entity = new Kostenstelle();
            entity.setId(1L);
            given(mapper.toEntity(any(KostenstelleDto.class))).willReturn(entity);
            given(kostenVerteilungService.saveKostenstelle(eq(1L), any(Kostenstelle.class))).willReturn(entity);
            KostenstelleDto dto = new KostenstelleDto();
            dto.setId(1L);
            dto.setName("Strom");
            given(mapper.toDto(any(Kostenstelle.class))).willReturn(dto);

            KostenstelleDto request = new KostenstelleDto();
            request.setName("Strom");

            mockMvc.perform(put("/api/miete/mietobjekte/1/kostenstellen/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Strom"));
        }

        @Test
        @DisplayName("DELETE löscht Kostenstelle (204)")
        void deleteKostenstelle() throws Exception {
            doNothing().when(kostenVerteilungService).deleteKostenstelle(1L);

            mockMvc.perform(delete("/api/miete/kostenstellen/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("SQL Injection im Kostenstellen-Name wird nicht interpretiert")
        void sqlInjectionImName() throws Exception {
            Kostenstelle entity = new Kostenstelle();
            entity.setId(1L);
            given(mapper.toEntity(any(KostenstelleDto.class))).willReturn(entity);
            given(kostenVerteilungService.saveKostenstelle(eq(1L), any(Kostenstelle.class))).willReturn(entity);
            KostenstelleDto dto = new KostenstelleDto();
            dto.setId(1L);
            dto.setName("'; DROP TABLE kostenstellen; --");
            given(mapper.toDto(any(Kostenstelle.class))).willReturn(dto);

            KostenstelleDto request = new KostenstelleDto();
            request.setName("'; DROP TABLE kostenstellen; --");

            mockMvc.perform(post("/api/miete/mietobjekte/1/kostenstellen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("XML Content-Type wird abgelehnt")
        void xmlContentTypeWirdAbgelehnt() throws Exception {
            mockMvc.perform(post("/api/miete/mietobjekte/1/kostenstellen")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<kostenstelle />"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("Kostenpositionen CRUD")
    class KostenpositionenCrud {

        @Test
        @DisplayName("GET listet Kostenpositionen")
        void listKostenpositionen() throws Exception {
            Kostenposition entity = new Kostenposition();
            entity.setId(1L);
            given(kostenVerteilungService.getKostenpositionen(1L, null)).willReturn(List.of(entity));
            KostenpositionDto dto = new KostenpositionDto();
            dto.setId(1L);
            dto.setBetrag(new BigDecimal("100.00"));
            given(mapper.toDto(any(Kostenposition.class))).willReturn(dto);

            mockMvc.perform(get("/api/miete/kostenstellen/1/kostenpositionen"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].betrag").value(100.00));
        }

        @Test
        @DisplayName("GET mit Jahr-Filter")
        void listKostenpositionenMitJahr() throws Exception {
            given(kostenVerteilungService.getKostenpositionen(1L, 2024)).willReturn(List.of());

            mockMvc.perform(get("/api/miete/kostenstellen/1/kostenpositionen")
                            .param("jahr", "2024"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("POST erstellt Kostenposition (201)")
        void createKostenposition() throws Exception {
            Kostenposition entity = new Kostenposition();
            entity.setId(1L);
            given(mapper.toEntity(any(KostenpositionDto.class))).willReturn(entity);
            given(kostenVerteilungService.saveKostenposition(eq(1L), any(Kostenposition.class))).willReturn(entity);
            KostenpositionDto dto = new KostenpositionDto();
            dto.setId(1L);
            dto.setBetrag(new BigDecimal("250.00"));
            given(mapper.toDto(any(Kostenposition.class))).willReturn(dto);

            KostenpositionDto request = new KostenpositionDto();
            request.setBetrag(new BigDecimal("250.00"));

            mockMvc.perform(post("/api/miete/kostenstellen/1/kostenpositionen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.betrag").value(250.00));
        }

        @Test
        @DisplayName("PUT aktualisiert Kostenposition")
        void updateKostenposition() throws Exception {
            Kostenposition entity = new Kostenposition();
            entity.setId(1L);
            given(mapper.toEntity(any(KostenpositionDto.class))).willReturn(entity);
            given(kostenVerteilungService.saveKostenposition(eq(1L), any(Kostenposition.class))).willReturn(entity);
            KostenpositionDto dto = new KostenpositionDto();
            dto.setId(1L);
            given(mapper.toDto(any(Kostenposition.class))).willReturn(dto);

            KostenpositionDto request = new KostenpositionDto();
            request.setBetrag(new BigDecimal("300.00"));

            mockMvc.perform(put("/api/miete/kostenstellen/1/kostenpositionen/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DELETE löscht Kostenposition (204)")
        void deleteKostenposition() throws Exception {
            doNothing().when(kostenVerteilungService).deleteKostenposition(1L);

            mockMvc.perform(delete("/api/miete/kostenpositionen/1"))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Verteilungsschlüssel CRUD")
    class VerteilungsschluesselCrud {

        @Test
        @DisplayName("GET listet Verteilungsschlüssel")
        void listVerteilungsschluessel() throws Exception {
            Verteilungsschluessel entity = new Verteilungsschluessel();
            entity.setId(1L);
            given(kostenVerteilungService.getVerteilungsschluessel(1L)).willReturn(List.of(entity));
            VerteilungsschluesselDto dto = new VerteilungsschluesselDto();
            dto.setId(1L);
            dto.setName("Wohnfläche");
            given(mapper.toDto(any(Verteilungsschluessel.class))).willReturn(dto);

            mockMvc.perform(get("/api/miete/mietobjekte/1/verteilungsschluessel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Wohnfläche"));
        }

        @Test
        @DisplayName("POST erstellt Verteilungsschlüssel (201)")
        void createVerteilungsschluessel() throws Exception {
            Verteilungsschluessel entity = new Verteilungsschluessel();
            entity.setId(1L);
            given(mapper.toEntity(any(VerteilungsschluesselDto.class))).willReturn(entity);
            given(kostenVerteilungService.saveVerteilungsschluessel(eq(1L), any(Verteilungsschluessel.class)))
                    .willReturn(entity);
            VerteilungsschluesselDto dto = new VerteilungsschluesselDto();
            dto.setId(1L);
            dto.setName("Personenzahl");
            given(mapper.toDto(any(Verteilungsschluessel.class))).willReturn(dto);

            VerteilungsschluesselDto request = new VerteilungsschluesselDto();
            request.setName("Personenzahl");

            mockMvc.perform(post("/api/miete/mietobjekte/1/verteilungsschluessel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Personenzahl"));
        }

        @Test
        @DisplayName("PUT aktualisiert Verteilungsschlüssel")
        void updateVerteilungsschluessel() throws Exception {
            Verteilungsschluessel entity = new Verteilungsschluessel();
            entity.setId(1L);
            given(mapper.toEntity(any(VerteilungsschluesselDto.class))).willReturn(entity);
            given(kostenVerteilungService.saveVerteilungsschluessel(eq(1L), any(Verteilungsschluessel.class)))
                    .willReturn(entity);
            VerteilungsschluesselDto dto = new VerteilungsschluesselDto();
            dto.setId(1L);
            dto.setName("Einheiten");
            given(mapper.toDto(any(Verteilungsschluessel.class))).willReturn(dto);

            VerteilungsschluesselDto request = new VerteilungsschluesselDto();
            request.setName("Einheiten");

            mockMvc.perform(put("/api/miete/mietobjekte/1/verteilungsschluessel/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Einheiten"));
        }

        @Test
        @DisplayName("DELETE löscht Verteilungsschlüssel (204)")
        void deleteVerteilungsschluessel() throws Exception {
            doNothing().when(kostenVerteilungService).deleteVerteilungsschluessel(1L);

            mockMvc.perform(delete("/api/miete/verteilungsschluessel/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("XSS im Name wird nicht interpretiert")
        void xssImName() throws Exception {
            Verteilungsschluessel entity = new Verteilungsschluessel();
            entity.setId(1L);
            given(mapper.toEntity(any(VerteilungsschluesselDto.class))).willReturn(entity);
            given(kostenVerteilungService.saveVerteilungsschluessel(eq(1L), any(Verteilungsschluessel.class)))
                    .willReturn(entity);
            VerteilungsschluesselDto dto = new VerteilungsschluesselDto();
            dto.setId(1L);
            dto.setName("<img onerror=alert(1) src=x>");
            given(mapper.toDto(any(Verteilungsschluessel.class))).willReturn(dto);

            VerteilungsschluesselDto request = new VerteilungsschluesselDto();
            request.setName("<img onerror=alert(1) src=x>");

            mockMvc.perform(post("/api/miete/mietobjekte/1/verteilungsschluessel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("Copy Vorjahr")
    class CopyVorjahr {

        @Test
        @DisplayName("POST copy-vorjahr gibt Ergebnis zurück")
        void copyVorjahrErfolgreich() throws Exception {
            given(kostenVerteilungService.copyKostenpositionenVonVorjahr(1L, 2025)).willReturn(5);

            mockMvc.perform(post("/api/miete/mietobjekte/1/kostenpositionen/copy-vorjahr")
                            .param("zielJahr", "2025"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kopiert").value(5))
                    .andExpect(jsonPath("$.zielJahr").value(2025));
        }
    }
}
