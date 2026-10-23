package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.WerkstoffService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Werkstoffe ändern Stammdaten für alle Artikel: nur angemeldet und (bei Änderungen) nur mit CSRF-Token. Die Handy-App kommt nicht heran. */
@WebMvcTest(controllers = WerkstoffController.class)
@Import({ SecurityConfig.class, WerkstoffControllerSecurityTest.EchteFilterBeans.class })
class WerkstoffControllerSecurityTest {

    @TestConfiguration
    static class EchteFilterBeans {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private WerkstoffService service;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean private FrontendUserProfileRepository frontendUserProfileRepository;

    private static final String BASIS = "/api/werkstoffe";
    private static final String KOERPER = "{\"name\":\"S235JR\"}";

    @Test
    @DisplayName("Ohne Anmeldung: 401 bei Lesen, Anlegen, Ändern und Löschen")
    void ohneAnmeldung() throws Exception {
        mockMvc.perform(get(BASIS)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASIS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KOERPER)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASIS + "/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KOERPER)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(BASIS + "/1").with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    @DisplayName("Angemeldet, aber ohne CSRF-Token: Anlegen, Ändern und Löschen werden mit 403 abgelehnt")
    void ohneCsrf() throws Exception {
        mockMvc.perform(post(BASIS).contentType(MediaType.APPLICATION_JSON).content(KOERPER)).andExpect(status().isForbidden());
        mockMvc.perform(put(BASIS + "/1").contentType(MediaType.APPLICATION_JSON).content(KOERPER)).andExpect(status().isForbidden());
        mockMvc.perform(delete(BASIS + "/1")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser
    @DisplayName("Angemeldet mit CSRF-Token: Lesen, Anlegen und Löschen gehen")
    void angemeldetMitCsrf() throws Exception {
        given(service.alle()).willReturn(List.of());
        given(service.anlegen(any())).willReturn(null);

        mockMvc.perform(get(BASIS)).andExpect(status().isOk());
        mockMvc.perform(post(BASIS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KOERPER)).andExpect(status().isCreated());
        mockMvc.perform(delete(BASIS + "/1").with(csrf())).andExpect(status().isNoContent());
    }
}
