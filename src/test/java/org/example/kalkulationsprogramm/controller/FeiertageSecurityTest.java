package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.Feiertag;
import org.example.kalkulationsprogramm.service.EmailAbsenderService;
import org.example.kalkulationsprogramm.service.FeiertagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feiertage pflegen (Issue #222): Anlegen, Anpassen und Löschen wirkt auf Soll und Lohn
 * aller Mitarbeiter und ist deshalb Admins vorbehalten. Lesen darf jeder Angemeldete.
 */
@WebMvcTest(controllers = ZeitverwaltungController.class)
@Import({ SecurityConfig.class, FeiertageSecurityTest.EchteFilterBeans.class })
class FeiertageSecurityTest {

    @TestConfiguration
    static class EchteFilterBeans {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    private static final String URL = "/api/zeitverwaltung/feiertage";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private FeiertagService feiertagService;
    @MockBean private EmailAbsenderService emailAbsenderService;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockBean private org.example.kalkulationsprogramm.repository.ZeitbuchungRepository zeitbuchungRepository;
    @MockBean private org.example.kalkulationsprogramm.repository.AbwesenheitRepository abwesenheitRepository;
    @MockBean private org.example.kalkulationsprogramm.repository.MitarbeiterRepository mitarbeiterRepository;
    @MockBean private org.example.kalkulationsprogramm.service.ZeitkontoService zeitkontoService;
    @MockBean private org.example.kalkulationsprogramm.service.ZeitkontoWechselService zeitkontoWechselService;
    @MockBean private org.example.kalkulationsprogramm.service.ProjektAuswertungPdfService projektAuswertungPdfService;
    @MockBean private org.example.kalkulationsprogramm.repository.ProjektRepository projektRepository;
    @MockBean private org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository arbeitsgangStundensatzRepository;
    @MockBean private org.example.kalkulationsprogramm.repository.ArbeitsgangRepository arbeitsgangRepository;
    @MockBean private org.example.kalkulationsprogramm.service.ZeitbuchungAuditService auditService;
    @MockBean private org.example.kalkulationsprogramm.service.AngemeldeterMitarbeiterService angemeldeterMitarbeiter;
    @MockBean private org.example.kalkulationsprogramm.service.MonatsSaldoService monatsSaldoService;
    @MockBean private org.example.kalkulationsprogramm.service.MonatsSaldoWarmupService monatsSaldoWarmupService;
    @MockBean private org.example.kalkulationsprogramm.service.TagesSollService tagesSollService;

    private static Feiertag feiertag(long id, boolean eigener) {
        Feiertag f = new Feiertag(LocalDate.of(2026, 12, 24), "Heiligabend", "BY", true);
        f.setId(id);
        f.setEigener(eigener);
        return f;
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Jeder Angemeldete sieht die Feiertage mit allen Feldern")
    void lesenFuerAlle() throws Exception {
        Feiertag abgeschaltet = feiertag(3, false);
        abgeschaltet.setAktiv(false);
        given(feiertagService.getFeiertageZurVerwaltung(2026)).willReturn(List.of(abgeschaltet));

        mockMvc.perform(get(URL).param("jahr", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].datum").value("2026-12-24"))
                .andExpect(jsonPath("$[0].halbTag").value(true))
                .andExpect(jsonPath("$[0].aktiv").value(false))
                .andExpect(jsonPath("$[0].eigener").value(false))
                .andExpect(jsonPath("$[0].bundesland").value("BY"))
                .andExpect(jsonPath("$[0].angepasst").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf Feiertage weder anlegen noch ändern noch löschen")
    void schreibenNurAdmin() throws Exception {
        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datum\":\"2026-12-24\",\"bezeichnung\":\"Heiligabend\",\"halbTag\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(URL + "/1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"halbTag\":true,\"aktiv\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(URL + "/1").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(feiertagService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Ohne CSRF-Token wird nichts geändert")
    void ohneCsrf403() throws Exception {
        mockMvc.perform(put(URL + "/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"halbTag\":true,\"aktiv\":true}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(URL + "/1"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(feiertagService);
    }

    @Test
    @DisplayName("Ohne Anmeldung keine Feiertage")
    void anonym401() throws Exception {
        mockMvc.perform(get(URL).param("jahr", "2026")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin legt einen eigenen Feiertag an; Text wird unverändert an den Service gereicht")
    void adminLegtAn() throws Exception {
        given(feiertagService.anlegen(LocalDate.of(2026, 12, 24), "<script>alert(1)</script>", true))
                .willReturn(feiertag(9, true));

        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datum\":\"2026-12-24\",\"bezeichnung\":\"<script>alert(1)</script>\",\"halbTag\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.eigener").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Fachliche Fehler kommen als 400 mit Meldung")
    void fehlerMitMeldung() throws Exception {
        given(feiertagService.anlegen(any(), eq("'; DROP TABLE feiertag; --"), anyBoolean()))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Am 24.12.2026 ist schon ein Feiertag eingetragen."));

        mockMvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datum\":\"2026-12-24\",\"bezeichnung\":\"'; DROP TABLE feiertag; --\",\"halbTag\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Am 24.12.2026 ist schon ein Feiertag eingetragen."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin ändert und löscht; ungültige IDs geben 404 ohne Service-Aufruf")
    void adminAendertUndLoescht() throws Exception {
        Feiertag geaendert = feiertag(1, false);
        geaendert.setAktiv(false);
        given(feiertagService.aendern(1L, true, false)).willReturn(geaendert);

        mockMvc.perform(put(URL + "/1").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"halbTag\":true,\"aktiv\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktiv").value(false));
        mockMvc.perform(delete(URL + "/1").with(csrf()))
                .andExpect(status().isNoContent());
        verify(feiertagService).loeschen(1L);

        mockMvc.perform(delete(URL + "/0").with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(delete(URL + "/-5").with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(put(URL + "/0").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"halbTag\":true,\"aktiv\":true}"))
                .andExpect(status().isNotFound());
        verify(feiertagService, never()).loeschen(0L);
        verify(feiertagService, never()).aendern(eq(0L), anyBoolean(), anyBoolean());

        given(feiertagService.aendern(eq(Long.MAX_VALUE), anyBoolean(), anyBoolean()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Feiertag nicht gefunden."));
        mockMvc.perform(put(URL + "/" + Long.MAX_VALUE).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"halbTag\":true,\"aktiv\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Neu laden wirkt firmenweit und ist Admins vorbehalten")
    void regenerierenNurAdmin() throws Exception {
        mockMvc.perform(post(URL + "/regenerieren").with(csrf()).param("vonJahr", "2026").param("bisJahr", "2027"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(feiertagService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Neu laden: Zeitraum wird geprüft")
    void regenerierenPrueftZeitraum() throws Exception {
        mockMvc.perform(post(URL + "/regenerieren").with(csrf()).param("vonJahr", "2027").param("bisJahr", "2026"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(URL + "/regenerieren").with(csrf()).param("vonJahr", "2000").param("bisJahr", "2100"))
                .andExpect(status().isBadRequest());
        verify(feiertagService, never()).regeneriereFeiertage(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());

        given(feiertagService.regeneriereFeiertage(2026, 2027)).willReturn(List.of(feiertag(2, false)));
        mockMvc.perform(post(URL + "/regenerieren").with(csrf()).param("vonJahr", "2026").param("bisJahr", "2027"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2));
        verify(feiertagService, never()).loeschen(anyLong());
    }
}
