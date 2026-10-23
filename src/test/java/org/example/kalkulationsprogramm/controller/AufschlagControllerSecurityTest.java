package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.AufschlagBerechtigungService;
import org.example.kalkulationsprogramm.service.AufschlagService;
import org.example.kalkulationsprogramm.service.AufschlagService.Uebersicht;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Aufschläge sind eine Preisregel des Betriebs: nur angemeldet, nur mit CSRF-Token und nur mit dem Recht „Aufschläge ändern“. Die Handy-App kommt nicht heran. */
@WebMvcTest(controllers = AufschlagController.class)
@Import({ SecurityConfig.class, AufschlagControllerSecurityTest.EchteFilterBeans.class })
class AufschlagControllerSecurityTest {

    @TestConfiguration
    static class EchteFilterBeans {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AufschlagService service;
    @MockitoBean private AufschlagBerechtigungService berechtigung;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean private FrontendUserProfileRepository frontendUserProfileRepository;

    private static final String BASIS = "/api/aufschlaege";
    private static final String KOERPER = "{\"prozent\":10}";

    @Test
    @DisplayName("Ohne Anmeldung: 401 bei Berechtigung, Übersicht und Änderungen")
    void ohneAnmeldung() throws Exception {
        mockMvc.perform(get(BASIS + "/berechtigung")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASIS)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASIS + "/allgemein").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KOERPER)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASIS + "/kategorien/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KOERPER)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, berechtigung);
    }

    @Test
    @WithMockUser
    @DisplayName("Angemeldet, aber ohne CSRF-Token: Änderungen werden mit 403 abgelehnt")
    void ohneCsrf() throws Exception {
        mockMvc.perform(put(BASIS + "/allgemein").contentType(MediaType.APPLICATION_JSON).content(KOERPER)).andExpect(status().isForbidden());
        mockMvc.perform(put(BASIS + "/kategorien/1").contentType(MediaType.APPLICATION_JSON).content(KOERPER)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    @DisplayName("Angemeldet mit CSRF-Token, aber ohne das Recht: Übersicht und Änderungen werden mit 403 abgelehnt, nur die Berechtigung ist abrufbar")
    void ohneRecht() throws Exception {
        given(berechtigung.darfAendern(any())).willReturn(false);
        doThrow(new AccessDeniedException("Du darfst die Aufschläge nicht ändern.")).when(berechtigung).verlange(any());

        mockMvc.perform(get(BASIS + "/berechtigung")).andExpect(status().isOk());
        mockMvc.perform(get(BASIS)).andExpect(status().isForbidden());
        mockMvc.perform(put(BASIS + "/allgemein").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KOERPER)).andExpect(status().isForbidden());
        mockMvc.perform(put(BASIS + "/kategorien/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KOERPER)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    @DisplayName("Angemeldet, mit CSRF-Token und mit dem Recht: es geht")
    void mitRecht() throws Exception {
        given(service.uebersicht()).willReturn(new Uebersicht(null, List.of()));
        given(service.setzeAllgemein(any())).willReturn(new Uebersicht(null, List.of()));

        mockMvc.perform(get(BASIS)).andExpect(status().isOk());
        mockMvc.perform(put(BASIS + "/allgemein").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KOERPER)).andExpect(status().isOk());
    }
}
