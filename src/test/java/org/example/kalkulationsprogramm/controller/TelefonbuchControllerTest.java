package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonbuchAbgleichErgebnisDto;
import org.example.kalkulationsprogramm.dto.Telefon.TelefonbuchStatusDto;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchAbgleichPlaner;
import org.example.kalkulationsprogramm.service.telefon.telefonbuch.TelefonbuchAbgleichService;
import org.example.kalkulationsprogramm.testsupport.SitzungsRechteImTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TelefonbuchController.class)
@Import({SecurityConfig.class, TelefonbuchControllerTest.Filters.class})
class TelefonbuchControllerTest {

    @TestConfiguration
    static class Filters {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired MockMvc mvc;
    @MockitoBean FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean FrontendUserProfileRepository profiles;
    @MockitoBean TelefonbuchAbgleichService abgleich;
    @MockitoBean TelefonbuchAbgleichPlaner planer;

    private static final TelefonbuchStatusDto STATUS = new TelefonbuchStatusDto(true, true, "ERP-Kontakte",
            LocalDateTime.of(2026, 10, 21, 9, 30), 42, null, false, false, null);
    private static final TelefonbuchStatusDto LAEUFT = new TelefonbuchStatusDto(true, true, "ERP-Kontakte",
            LocalDateTime.of(2026, 10, 21, 9, 30), 42, null, false, true,
            new TelefonbuchAbgleichErgebnisDto(true, false, "Telefonbuch abgeglichen.", 3, 1, 2, 0, 40));

    @BeforeEach
    void sitzungsRechteUnveraendert() {
        SitzungsRechteImTest.rechteUnveraendert(frontendUserDetailsService);
    }

    @Test
    @DisplayName("Anonym 401, normale Benutzer 403 – nichts wird aufgerufen")
    void nurAdmin() throws Exception {
        mvc.perform(get("/api/telefon/einstellungen/telefonbuch")).andExpect(status().isUnauthorized());
        verifyNoInteractions(abgleich);
        verify(planer, never()).sofort();
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normale Benutzer: Status, Schalter und Abgleich gesperrt")
    void benutzerGesperrt() throws Exception {
        mvc.perform(get("/api/telefon/einstellungen/telefonbuch")).andExpect(status().isForbidden());
        mvc.perform(put("/api/telefon/einstellungen/telefonbuch").with(csrf()).contentType("application/json")
                .content("{\"aktiv\":true}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/telefon/einstellungen/telefonbuch/abgleichen").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(abgleich);
        verify(planer, never()).sofort();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin: Status lesen")
    void statusLesen() throws Exception {
        when(abgleich.status()).thenReturn(STATUS);
        mvc.perform(get("/api/telefon/einstellungen/telefonbuch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktiv").value(true))
                .andExpect(jsonPath("$.anzahlEintraege").value(42))
                .andExpect(jsonPath("$.telefonbuchName").value("ERP-Kontakte"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin: Schalter umlegen startet sofort einen Lauf (Abgleich bzw. Löschen); ohne Angabe 400")
    void schalten() throws Exception {
        when(abgleich.schalte(anyBoolean())).thenReturn(STATUS);
        when(abgleich.istBereit()).thenReturn(true);
        when(abgleich.status()).thenReturn(LAEUFT);
        mvc.perform(put("/api/telefon/einstellungen/telefonbuch").with(csrf()).contentType("application/json")
                .content("{\"aktiv\":true}")).andExpect(status().isOk()).andExpect(jsonPath("$.laeuftGerade").value(true));
        verify(abgleich).schalte(true);

        mvc.perform(put("/api/telefon/einstellungen/telefonbuch").with(csrf()).contentType("application/json")
                .content("{\"aktiv\":false}")).andExpect(status().isOk());
        verify(abgleich).schalte(false);
        verify(planer, times(2)).sofort();

        // Box nicht eingerichtet: Schalter wird trotzdem gespeichert, aber kein Lauf
        when(abgleich.istBereit()).thenReturn(false);
        mvc.perform(put("/api/telefon/einstellungen/telefonbuch").with(csrf()).contentType("application/json")
                .content("{\"aktiv\":false}")).andExpect(status().isOk());
        verify(planer, times(2)).sofort();

        mvc.perform(put("/api/telefon/einstellungen/telefonbuch").with(csrf()).contentType("application/json")
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(put("/api/telefon/einstellungen/telefonbuch").with(csrf()).contentType("application/json")
                .content("{\"aktiv\":\"'; DROP TABLE x; --\"}")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin: Jetzt abgleichen startet im Hintergrund und antwortet sofort mit 202 und dem Status")
    void abgleichen() throws Exception {
        when(abgleich.grundGegenLauf()).thenReturn(null);
        when(planer.sofort()).thenReturn(true);
        when(abgleich.status()).thenReturn(LAEUFT);
        mvc.perform(post("/api/telefon/einstellungen/telefonbuch/abgleichen").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.laeuftGerade").value(true))
                .andExpect(jsonPath("$.letztesErgebnis.neu").value(3));
        verify(planer).sofort();
        verify(abgleich, never()).abgleichen(anyBoolean());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin: Jetzt abgleichen geht nicht → 409 mit Grund; Start scheitert → 503")
    void abgleichenNichtMoeglich() throws Exception {
        when(abgleich.grundGegenLauf()).thenReturn("Die Telefon-Anbindung ist ausgeschaltet.");
        mvc.perform(post("/api/telefon/einstellungen/telefonbuch/abgleichen").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Die Telefon-Anbindung ist ausgeschaltet."));
        verify(planer, never()).sofort();

        when(abgleich.grundGegenLauf()).thenReturn(null);
        when(planer.sofort()).thenReturn(false);
        mvc.perform(post("/api/telefon/einstellungen/telefonbuch/abgleichen").with(csrf()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Ohne CSRF-Token wird nichts geändert")
    void ohneCsrf() throws Exception {
        mvc.perform(post("/api/telefon/einstellungen/telefonbuch/abgleichen")).andExpect(status().isForbidden());
        verifyNoInteractions(abgleich);
    }
}
