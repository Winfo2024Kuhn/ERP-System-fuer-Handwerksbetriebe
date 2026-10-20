package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeLoeschPruefungDto;
import org.example.kalkulationsprogramm.mapper.KundeMapper;
import org.example.kalkulationsprogramm.repository.KundeNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.service.KundeDatenschutzService;
import org.example.kalkulationsprogramm.service.KundeDuplikatService;
import org.example.kalkulationsprogramm.service.KundenDetailService;
import org.example.kalkulationsprogramm.service.KundennummerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** Kunde löschen bzw. aus Datenschutzgründen sperren (Issue #264); Rechte siehe {@link KundeDatenschutzSecurityTest}. */
@WebMvcTest(KundeController.class)
@AutoConfigureMockMvc(addFilters = false)
class KundeDatenschutzControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private KundeRepository kundeRepository;
    @MockBean private KundeMapper kundeMapper;
    @MockBean private KundenDetailService kundenDetailService;
    @MockBean private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @MockBean private KundennummerService kundennummerService;
    @MockBean private KundeDuplikatService kundeDuplikatService;
    @MockBean private KundeNotizRepository kundeNotizRepository;
    @MockBean private KundeDatenschutzService datenschutzService;

    private static Kunde gesperrterKunde() {
        Kunde kunde = new Kunde();
        kunde.setId(7L);
        kunde.setKundennummer("1007");
        kunde.setName("Max Mustermann");
        kunde.setGesperrtAm(LocalDateTime.of(2026, 10, 20, 9, 0));
        kunde.setAufbewahrenBis(LocalDate.of(2034, 12, 31));
        return kunde;
    }

    @Test
    @DisplayName("Löschen: 204; Hindernisse kommen als 409 mit Klartext")
    void loeschen() throws Exception {
        mockMvc.perform(delete("/api/kunden/7")).andExpect(status().isNoContent());

        willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Der Kunde kann nicht gelöscht werden."))
                .given(datenschutzService).loesche(eq(8L), any());
        mockMvc.perform(delete("/api/kunden/8"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Der Kunde kann nicht gelöscht werden."));
    }

    @Test
    @DisplayName("Ungültige IDs: 400 bzw. 404")
    void ungueltigeIds() throws Exception {
        mockMvc.perform(delete("/api/kunden/abc")).andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/kunden/{id}", "1' OR '1'='1")).andExpect(status().isBadRequest());
        willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Kunde wurde nicht gefunden."))
                .given(datenschutzService).loesche(eq(Long.MAX_VALUE), any());
        mockMvc.perform(delete("/api/kunden/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sperre: Grund ist optional, darf aber höchstens 500 Zeichen haben")
    void sperreGrund() throws Exception {
        given(kundenDetailService.loadDetails(7L)).willReturn(Optional.of(new KundeDetailDto()));

        mockMvc.perform(post("/api/kunden/7/datenschutz-sperre")).andExpect(status().isOk());
        verify(datenschutzService).sperre(eq(7L), eq(null), any());

        String xss = "<script>alert(1)</script>'; DROP TABLE kunde; --";
        mockMvc.perform(post("/api/kunden/7/datenschutz-sperre").contentType(MediaType.APPLICATION_JSON)
                .content("{\"grund\":\"" + xss.replace("\"", "\\\"") + "\"}")).andExpect(status().isOk());
        verify(datenschutzService).sperre(eq(7L), eq(xss), any());

        mockMvc.perform(post("/api/kunden/9/datenschutz-sperre").contentType(MediaType.APPLICATION_JSON)
                .content("{\"grund\":\"" + "x".repeat(501) + "\"}")).andExpect(status().isBadRequest());
        verify(datenschutzService, never()).sperre(eq(9L), any(), any());
    }

    @Test
    @DisplayName("Gesperrte Kunden lassen sich nicht ändern, keine Notizen und keine E-Mail-Adressen anlegen")
    void gesperrtUnveraenderlich() throws Exception {
        given(kundeRepository.findById(7L)).willReturn(Optional.of(gesperrterKunde()));

        mockMvc.perform(put("/api/kunden/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Max Mustermann\",\"kundenEmails\":[]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Dieser Kunde ist aus Datenschutzgründen gesperrt und kann nicht mehr geändert werden."));
        mockMvc.perform(post("/api/kunden/7/notizen").contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Rückruf\"}")).andExpect(status().isConflict());
        mockMvc.perform(post("/api/kunden/7/emails").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"max@example.com\"}")).andExpect(status().isConflict());
        verify(kundeRepository, never()).save(any());
        verify(kundeNotizRepository, never()).save(any());
    }
}
