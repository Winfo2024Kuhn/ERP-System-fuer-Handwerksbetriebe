package org.example.kalkulationsprogramm.controller;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.En1090Rolle;
import org.example.kalkulationsprogramm.dto.En1090RolleDto;
import org.example.kalkulationsprogramm.repository.En1090RolleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(En1090RolleController.class)
@AutoConfigureMockMvc(addFilters = false)
class En1090RolleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private En1090RolleRepository repository;

    private En1090Rolle buildEn1090Rolle(Long id, String kurztext) {
        En1090Rolle rolle = new En1090Rolle();
        rolle.setId(id);
        rolle.setKurztext(kurztext);
        rolle.setBeschreibung("Beschreibung " + kurztext);
        rolle.setSortierung(1);
        rolle.setAktiv(true);
        return rolle;
    }

    @Nested
    @DisplayName("GET /api/en1090/rollen")
    class List {

        @Test
        @DisplayName("Gibt alle Rollen sortiert zurück")
        void gibtAlleRollenZurueck() throws Exception {
            given(repository.findAllByOrderBySortierungAsc()).willReturn(
                    java.util.List.of(
                            buildEn1090Rolle(1L, "SFI"),
                            buildEn1090Rolle(2L, "IWE")));

            mockMvc.perform(get("/api/en1090/rollen"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].kurztext").value("SFI"))
                    .andExpect(jsonPath("$[1].kurztext").value("IWE"))
                    .andExpect(jsonPath("$[0].aktiv").value(true));
        }

        @Test
        @DisplayName("Gibt leere Liste zurück wenn keine Rollen vorhanden")
        void gibtLeereListeZurueck() throws Exception {
            given(repository.findAllByOrderBySortierungAsc()).willReturn(java.util.List.of());

            mockMvc.perform(get("/api/en1090/rollen"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Nested
    @DisplayName("POST /api/en1090/rollen")
    class Create {

        @Test
        @DisplayName("Erstellt neue Rolle und gibt sie zurück")
        void erstelltNeueRolle() throws Exception {
            En1090RolleDto dto = new En1090RolleDto();
            dto.setKurztext("SFI");
            dto.setBeschreibung("Schweißfachingenieur");
            dto.setSortierung(1);
            dto.setAktiv(true);

            given(repository.save(any(En1090Rolle.class)))
                    .willAnswer(inv -> {
                        En1090Rolle saved = inv.getArgument(0);
                        saved.setId(10L);
                        return saved;
                    });

            mockMvc.perform(post("/api/en1090/rollen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.kurztext").value("SFI"))
                    .andExpect(jsonPath("$.aktiv").value(true));
        }

        @Test
        @DisplayName("Setzt Standardwerte wenn Felder null sind")
        void setztStandardwerteBeiNull() throws Exception {
            En1090RolleDto dto = new En1090RolleDto();
            dto.setKurztext("IWE");

            given(repository.save(any(En1090Rolle.class)))
                    .willAnswer(inv -> {
                        En1090Rolle saved = inv.getArgument(0);
                        saved.setId(20L);
                        return saved;
                    });

            mockMvc.perform(post("/api/en1090/rollen")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.kurztext").value("IWE"))
                    .andExpect(jsonPath("$.aktiv").value(true));
        }
    }

    @Nested
    @DisplayName("PUT /api/en1090/rollen/{id}")
    class Update {

        @Test
        @DisplayName("Aktualisiert vorhandene Rolle")
        void aktualisiertVorhandeneRolle() throws Exception {
            En1090Rolle existing = buildEn1090Rolle(1L, "SFI");
            En1090RolleDto dto = new En1090RolleDto();
            dto.setKurztext("SFI");
            dto.setBeschreibung("Geänderter Text");
            dto.setSortierung(2);
            dto.setAktiv(false);

            given(repository.findById(1L)).willReturn(Optional.of(existing));
            given(repository.save(any(En1090Rolle.class))).willReturn(existing);

            mockMvc.perform(put("/api/en1090/rollen/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.kurztext").value("SFI"));
        }

        @Test
        @DisplayName("Gibt 404 zurück wenn Rolle nicht gefunden")
        void gibt404WennRolleNichtGefunden() throws Exception {
            given(repository.findById(999L)).willReturn(Optional.empty());

            En1090RolleDto dto = new En1090RolleDto();
            dto.setKurztext("XXX");

            mockMvc.perform(put("/api/en1090/rollen/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/en1090/rollen/{id}")
    class Delete {

        @Test
        @DisplayName("Löscht vorhandene Rolle und gibt 204 zurück")
        void loeschtVorhandeneRolle() throws Exception {
            given(repository.existsById(1L)).willReturn(true);
            doNothing().when(repository).deleteById(1L);

            mockMvc.perform(delete("/api/en1090/rollen/1"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Gibt 404 zurück wenn Rolle nicht gefunden")
        void gibt404WennRolleNichtGefunden() throws Exception {
            given(repository.existsById(999L)).willReturn(false);

            mockMvc.perform(delete("/api/en1090/rollen/999"))
                    .andExpect(status().isNotFound());
        }
    }
}
