package org.example.kalkulationsprogramm.controller.miete;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kalkulationsprogramm.domain.miete.Mietobjekt;
import org.example.kalkulationsprogramm.domain.miete.Mietpartei;
import org.example.kalkulationsprogramm.domain.miete.MietparteiRolle;
import org.example.kalkulationsprogramm.dto.miete.MietobjektDto;
import org.example.kalkulationsprogramm.dto.miete.MietparteiDto;
import org.example.kalkulationsprogramm.mapper.MieteMapper;
import org.example.kalkulationsprogramm.service.miete.MietobjektService;
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
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MietobjektController.class)
@AutoConfigureMockMvc(addFilters = false)
class MietobjektControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MietobjektService mietobjektService;

    @MockBean
    private MieteMapper mapper;

    private MietobjektDto buildMietobjektDto(Long id, String name) {
        MietobjektDto dto = new MietobjektDto();
        dto.setId(id);
        dto.setName(name);
        dto.setStrasse("Hauptstr. 1");
        dto.setPlz("12345");
        dto.setOrt("Berlin");
        return dto;
    }

    private MietparteiDto buildMietparteiDto(Long id, String name) {
        MietparteiDto dto = new MietparteiDto();
        dto.setId(id);
        dto.setName(name);
        dto.setRolle(MietparteiRolle.MIETER);
        dto.setMonatlicherVorschuss(new BigDecimal("250.00"));
        return dto;
    }

    @Nested
    @DisplayName("GET /api/miete/mietobjekte")
    class ListMietobjekte {

        @Test
        @DisplayName("Gibt 200 mit Mietobjektliste zurück")
        void gibtAlleMietobjekteZurueck() throws Exception {
            Mietobjekt entity = new Mietobjekt();
            entity.setId(1L);
            given(mietobjektService.findAll()).willReturn(List.of(entity));
            given(mapper.toDto(any(Mietobjekt.class))).willReturn(buildMietobjektDto(1L, "Haus A"));

            mockMvc.perform(get("/api/miete/mietobjekte"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Haus A"));
        }
    }

    @Nested
    @DisplayName("POST /api/miete/mietobjekte")
    class CreateMietobjekt {

        @Test
        @DisplayName("Erstellt Mietobjekt und gibt 201 zurück")
        void erstelltMietobjektErfolgreich() throws Exception {
            Mietobjekt entity = new Mietobjekt();
            entity.setId(1L);
            given(mapper.toEntity(any(MietobjektDto.class))).willReturn(entity);
            given(mietobjektService.save(any(Mietobjekt.class))).willReturn(entity);
            given(mapper.toDto(any(Mietobjekt.class))).willReturn(buildMietobjektDto(1L, "Haus B"));

            MietobjektDto dto = buildMietobjektDto(null, "Haus B");

            mockMvc.perform(post("/api/miete/mietobjekte")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Haus B"));
        }

        @Test
        @DisplayName("SQL Injection im Namen wird nicht interpretiert")
        void sqlInjectionImNameWirdIgnoriert() throws Exception {
            Mietobjekt entity = new Mietobjekt();
            entity.setId(1L);
            given(mapper.toEntity(any(MietobjektDto.class))).willReturn(entity);
            given(mietobjektService.save(any(Mietobjekt.class))).willReturn(entity);
            given(mapper.toDto(any(Mietobjekt.class))).willReturn(buildMietobjektDto(1L, "'; DROP TABLE mietobjekte; --"));

            MietobjektDto dto = new MietobjektDto();
            dto.setName("'; DROP TABLE mietobjekte; --");

            mockMvc.perform(post("/api/miete/mietobjekte")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("XSS im Namen")
        void xssImNamen() throws Exception {
            Mietobjekt entity = new Mietobjekt();
            entity.setId(1L);
            given(mapper.toEntity(any(MietobjektDto.class))).willReturn(entity);
            given(mietobjektService.save(any(Mietobjekt.class))).willReturn(entity);
            given(mapper.toDto(any(Mietobjekt.class)))
                    .willReturn(buildMietobjektDto(1L, "<script>alert(1)</script>"));

            MietobjektDto dto = new MietobjektDto();
            dto.setName("<script>alert(1)</script>");

            mockMvc.perform(post("/api/miete/mietobjekte")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("XML Content-Type wird abgelehnt")
        void xmlContentTypeWirdAbgelehnt() throws Exception {
            mockMvc.perform(post("/api/miete/mietobjekte")
                            .contentType(MediaType.APPLICATION_XML)
                            .content("<mietobjekt />"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    @Nested
    @DisplayName("PUT /api/miete/mietobjekte/{id}")
    class UpdateMietobjekt {

        @Test
        @DisplayName("Aktualisiert Mietobjekt erfolgreich")
        void aktualisiertMietobjektErfolgreich() throws Exception {
            Mietobjekt entity = new Mietobjekt();
            entity.setId(1L);
            given(mapper.toEntity(any(MietobjektDto.class))).willReturn(entity);
            given(mietobjektService.save(any(Mietobjekt.class))).willReturn(entity);
            given(mapper.toDto(any(Mietobjekt.class))).willReturn(buildMietobjektDto(1L, "Haus C"));

            MietobjektDto dto = buildMietobjektDto(1L, "Haus C");

            mockMvc.perform(put("/api/miete/mietobjekte/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Haus C"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/miete/mietobjekte/{id}")
    class DeleteMietobjekt {

        @Test
        @DisplayName("Löscht Mietobjekt und gibt 204 zurück")
        void loeschtMietobjektErfolgreich() throws Exception {
            doNothing().when(mietobjektService).delete(1L);

            mockMvc.perform(delete("/api/miete/mietobjekte/1"))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("Parteien Endpoints")
    class Parteien {

        @Test
        @DisplayName("GET /api/miete/mietobjekte/{id}/parteien gibt Liste zurück")
        void gibtParteienZurueck() throws Exception {
            Mietpartei entity = new Mietpartei();
            entity.setId(1L);
            given(mietobjektService.getParteien(1L)).willReturn(List.of(entity));
            given(mapper.toDto(any(Mietpartei.class))).willReturn(buildMietparteiDto(1L, "Schmidt"));

            mockMvc.perform(get("/api/miete/mietobjekte/1/parteien"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Schmidt"));
        }

        @Test
        @DisplayName("POST /api/miete/mietobjekte/{id}/parteien erstellt Partei (201)")
        void erstelltParteiErfolgreich() throws Exception {
            Mietpartei entity = new Mietpartei();
            entity.setId(1L);
            given(mapper.toEntity(any(MietparteiDto.class))).willReturn(entity);
            given(mietobjektService.savePartei(eq(1L), any(Mietpartei.class))).willReturn(entity);
            given(mapper.toDto(any(Mietpartei.class))).willReturn(buildMietparteiDto(1L, "Müller"));

            MietparteiDto dto = buildMietparteiDto(null, "Müller");

            mockMvc.perform(post("/api/miete/mietobjekte/1/parteien")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Müller"));
        }

        @Test
        @DisplayName("PUT /api/miete/mietobjekte/{id}/parteien/{parteiId} aktualisiert Partei")
        void aktualisiertParteiErfolgreich() throws Exception {
            Mietpartei entity = new Mietpartei();
            entity.setId(1L);
            given(mapper.toEntity(any(MietparteiDto.class))).willReturn(entity);
            given(mietobjektService.savePartei(eq(1L), any(Mietpartei.class))).willReturn(entity);
            given(mapper.toDto(any(Mietpartei.class))).willReturn(buildMietparteiDto(1L, "Müller"));

            MietparteiDto dto = buildMietparteiDto(1L, "Müller");

            mockMvc.perform(put("/api/miete/mietobjekte/1/parteien/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("DELETE /api/miete/parteien/{parteiId} löscht Partei (204)")
        void loeschtParteiErfolgreich() throws Exception {
            doNothing().when(mietobjektService).deletePartei(1L);

            mockMvc.perform(delete("/api/miete/parteien/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Überlanger Parteiname wird akzeptiert")
        void ueberlangerParteinameWirdAkzeptiert() throws Exception {
            Mietpartei entity = new Mietpartei();
            entity.setId(1L);
            given(mapper.toEntity(any(MietparteiDto.class))).willReturn(entity);
            given(mietobjektService.savePartei(eq(1L), any(Mietpartei.class))).willReturn(entity);
            given(mapper.toDto(any(Mietpartei.class))).willReturn(buildMietparteiDto(1L, "A".repeat(10001)));

            MietparteiDto dto = new MietparteiDto();
            dto.setName("A".repeat(10001));

            mockMvc.perform(post("/api/miete/mietobjekte/1/parteien")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated());
        }
    }
}
