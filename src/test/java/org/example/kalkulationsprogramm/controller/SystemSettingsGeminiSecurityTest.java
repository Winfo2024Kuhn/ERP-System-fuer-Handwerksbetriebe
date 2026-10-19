package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** KI-Schlüssel entfernen (Issue #274): nur Admins, nur mit CSRF-Token. */
@WebMvcTest(SystemSettingsController.class)
@Import({ SecurityConfig.class, SystemSettingsGeminiSecurityTest.Filters.class })
class SystemSettingsGeminiSecurityTest {

    @TestConfiguration
    static class Filters {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mvc;
    @MockBean private FrontendUserDetailsService users;
    @MockBean private FrontendUserProfileRepository profiles;
    @MockBean private SystemSettingsService settings;
    @MockBean private DateiOrdnerService dateiOrdnerService;
    @MockBean private PostfachUmzugService postfachUmzugService;

    @Test
    void ohneAnmeldungAbgewiesen() throws Exception {
        mvc.perform(delete("/api/settings/gemini").with(csrf())).andExpect(status().isUnauthorized());
        verify(settings, never()).entferneGeminiApiKey();
    }

    @Test
    @WithMockUser(roles = "USER")
    void ohneAdminrechtVerboten() throws Exception {
        mvc.perform(delete("/api/settings/gemini").with(csrf())).andExpect(status().isForbidden());
        verify(settings, never()).entferneGeminiApiKey();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void ohneCsrfVerboten() throws Exception {
        mvc.perform(delete("/api/settings/gemini")).andExpect(status().isForbidden());
        verify(settings, never()).entferneGeminiApiKey();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEntferntSchluessel() throws Exception {
        mvc.perform(delete("/api/settings/gemini").with(csrf())).andExpect(status().isOk());
        verify(settings).entferneGeminiApiKey();
    }
}
