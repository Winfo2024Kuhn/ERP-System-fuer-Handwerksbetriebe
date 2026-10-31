package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.DateiOrdnerService;
import org.example.kalkulationsprogramm.service.PostfachUmzugService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die alten Gemini-Endpunkte unter {@code /api/settings/gemini} gibt es nicht mehr: Schlüssel
 * pflegt die Oberfläche über {@code /api/settings/ki/**} (prüft auch die Schlüsselzeichen, siehe
 * {@code KiEinstellungenControllerTest}). Auch ein Admin mit CSRF-Token erreicht hier nichts mehr.
 */
@WebMvcTest(SystemSettingsController.class)
@Import({ SecurityConfig.class, SystemSettingsAlteGeminiEndpunkteTest.Filters.class })
class SystemSettingsAlteGeminiEndpunkteTest {

    @TestConfiguration
    static class Filters {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private FrontendUserDetailsService users;
    @MockitoBean private FrontendUserProfileRepository profiles;
    @MockitoBean private SystemSettingsService settings;
    @MockitoBean private DateiOrdnerService dateiOrdnerService;
    @MockitoBean private PostfachUmzugService postfachUmzugService;
    @MockitoBean private org.example.kalkulationsprogramm.service.openfile.ContainerFreigabe containerFreigabe;

    @Test
    @WithMockUser(roles = "ADMIN")
    void alteGeminiEndpunkteSindWeg() throws Exception {
        mvc.perform(get("/api/settings/gemini")).andExpect(status().isNotFound());
        mvc.perform(put("/api/settings/gemini").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"test-gemini\\n456\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/settings/gemini").with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/settings/gemini/test").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"test-gemini-456\"}")).andExpect(status().isNotFound());

        verifyNoInteractions(settings);
    }
}
