package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.mapper.KundeMapper;
import org.example.kalkulationsprogramm.repository.KundeNotizRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.KundeDatenschutzService;
import org.example.kalkulationsprogramm.service.KundeDuplikatService;
import org.example.kalkulationsprogramm.service.KundenDetailService;
import org.example.kalkulationsprogramm.service.KundennummerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Kunden löschen, aus Datenschutzgründen sperren bzw. anonymisieren dürfen nur Admins (Issue #264). Die Pfade
 * liegen in der gemeinsamen Kette mit der Handy-App ({@code /api/kunden/**}).
 */
@WebMvcTest(controllers = KundeController.class)
@Import({ SecurityConfig.class, KundeDatenschutzSecurityTest.EchteFilterBeans.class })
class KundeDatenschutzSecurityTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockBean private MitarbeiterRepository mitarbeiterRepository;
    @MockBean private KundeRepository kundeRepository;
    @MockBean private KundeMapper kundeMapper;
    @MockBean private KundenDetailService kundenDetailService;
    @MockBean private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @MockBean private KundennummerService kundennummerService;
    @MockBean private KundeDuplikatService kundeDuplikatService;
    @MockBean private KundeNotizRepository kundeNotizRepository;
    @MockBean private KundeDatenschutzService datenschutzService;

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Gesperrte Kunden auflisten und Lösch-Prüfung: nur für Admins")
    void gesperrteNurFuerAdmins() throws Exception {
        mockMvc.perform(get("/api/kunden").param("gesperrte", "true"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Nur Admins sehen gesperrte Kunden."));
        org.mockito.BDDMockito.given(kundenDetailService.loadDetails(7L))
                .willReturn(java.util.Optional.of(new org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto()));
        mockMvc.perform(get("/api/kunden/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loeschPruefung").doesNotExist());
        verifyNoInteractions(datenschutzService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Gesperrter Kunde: auch Nicht-Admins sehen das aktuelle Fristende, aber keine Lösch-Prüfung")
    void gesperrtAktuellesFristende() throws Exception {
        org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto detail = new org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto();
        detail.setGesperrt(true);
        detail.setAufbewahrenBis(java.time.LocalDate.of(2033, 12, 31));
        org.mockito.BDDMockito.given(kundenDetailService.loadDetails(7L)).willReturn(java.util.Optional.of(detail));
        org.mockito.BDDMockito.given(datenschutzService.aktuellesFristende(7L)).willReturn(java.time.LocalDate.of(2035, 12, 31));

        mockMvc.perform(get("/api/kunden/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aufbewahrenBis").value("2035-12-31"))
                .andExpect(jsonPath("$.loeschPruefung").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admins sehen gesperrte Kunden und die Lösch-Prüfung")
    void adminSiehtGesperrte() throws Exception {
        org.mockito.BDDMockito.given(kundeRepository.findAll(
                        org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<org.example.kalkulationsprogramm.domain.Kunde>>any(),
                        any(org.springframework.data.domain.Pageable.class)))
                .willReturn(org.springframework.data.domain.Page.empty());
        mockMvc.perform(get("/api/kunden").param("gesperrte", "true")).andExpect(status().isOk());

        org.mockito.BDDMockito.given(kundenDetailService.loadDetails(7L))
                .willReturn(java.util.Optional.of(new org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto()));
        org.mockito.BDDMockito.given(datenschutzService.pruefe(7L)).willReturn(
                new org.example.kalkulationsprogramm.dto.Kunde.KundeLoeschPruefungDto(false, java.util.List.of("2 Projekte"),
                        java.time.LocalDate.of(2034, 12, 31), false));
        mockMvc.perform(get("/api/kunden/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loeschPruefung.hindernisse[0]").value("2 Projekte"))
                .andExpect(jsonPath("$.loeschPruefung.aufbewahrenBis").value("2034-12-31"))
                .andExpect(jsonPath("$.loeschPruefung.anonymisierbar").value(false));
    }

    @Test
    @DisplayName("Ohne Anmeldung: 401")
    void anonym() throws Exception {
        mockMvc.perform(delete("/api/kunden/7").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/kunden/7/datenschutz-sperre").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/kunden/7/anonymisieren").with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(datenschutzService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normale Benutzer dürfen weder löschen noch sperren: 403")
    void benutzer() throws Exception {
        mockMvc.perform(delete("/api/kunden/7").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/kunden/7/datenschutz-sperre").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/kunden/7/anonymisieren").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(datenschutzService);
    }

    @Test
    @WithMockUser(username = "chef", roles = "ADMIN")
    @DisplayName("Admin ohne CSRF-Token: 403")
    void adminOhneCsrf() throws Exception {
        mockMvc.perform(delete("/api/kunden/7")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/kunden/7/anonymisieren")).andExpect(status().isForbidden());
        verifyNoInteractions(datenschutzService);
    }

    @Test
    @WithMockUser(username = "chef", roles = "ADMIN")
    @DisplayName("Admin mit CSRF-Token darf löschen; protokolliert wird der angemeldete Benutzer")
    void adminLoescht() throws Exception {
        mockMvc.perform(delete("/api/kunden/7").with(csrf())).andExpect(status().isNoContent());
        verify(datenschutzService).loesche(eq(7L), eq("chef"));
    }

    @Test
    @WithMockUser(username = "chef", roles = "ADMIN")
    @DisplayName("Admin darf sperren")
    void adminSperrt() throws Exception {
        org.mockito.BDDMockito.given(kundenDetailService.loadDetails(7L))
                .willReturn(java.util.Optional.of(new org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto()));
        mockMvc.perform(post("/api/kunden/7/datenschutz-sperre").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"grund\":\"Löschwunsch per E-Mail\"}"))
                .andExpect(status().isOk());
        verify(datenschutzService).sperre(eq(7L), eq("Löschwunsch per E-Mail"), eq("chef"));
        verify(datenschutzService).pruefe(any());
    }

    @Test
    @WithMockUser(username = "chef", roles = "ADMIN")
    @DisplayName("Admin darf anonymisieren; Antwort ist die aktualisierte Akte")
    void adminAnonymisiert() throws Exception {
        org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto detail = new org.example.kalkulationsprogramm.dto.Kunde.KundeDetailDto();
        detail.setName("Gelöschter Kunde 1007");
        detail.setAnonymisiertAm(java.time.LocalDateTime.of(2026, 10, 20, 9, 15));
        org.mockito.BDDMockito.given(kundenDetailService.loadDetails(7L)).willReturn(java.util.Optional.of(detail));

        mockMvc.perform(post("/api/kunden/7/anonymisieren").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gelöschter Kunde 1007"))
                .andExpect(jsonPath("$.anonymisiertAm").value("2026-10-20T09:15:00"));
        verify(datenschutzService).anonymisiere(eq(7L), eq("chef"));
    }

    @Test
    @WithMockUser(username = "chef", roles = "ADMIN")
    @DisplayName("Frist läuft noch: 409 mit Klartext")
    void anonymisierenZuFrueh() throws Exception {
        org.mockito.BDDMockito.willThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT, "Die Unterlagen dieses Kunden müssen noch bis 31.12.2034 aufbewahrt werden."))
                .given(datenschutzService).anonymisiere(eq(7L), eq("chef"));

        mockMvc.perform(post("/api/kunden/7/anonymisieren").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Die Unterlagen dieses Kunden müssen noch bis 31.12.2034 aufbewahrt werden."));
    }

    @Test
    @WithMockUser(username = "chef", roles = "ADMIN")
    @DisplayName("Ungültige IDs (Text, SQL-Injection): 400, Service wird nicht aufgerufen")
    void anonymisierenUngueltigeId() throws Exception {
        mockMvc.perform(post("/api/kunden/abc/anonymisieren").with(csrf())).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/kunden/{id}/anonymisieren", "<script>alert(1)</script>").with(csrf()))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/kunden/{id}/anonymisieren", "'; DROP TABLE kunde; --").with(csrf()))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(datenschutzService);
    }
}
