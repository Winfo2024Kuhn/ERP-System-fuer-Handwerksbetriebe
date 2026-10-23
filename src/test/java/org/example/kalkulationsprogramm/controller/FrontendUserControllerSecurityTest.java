package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * „Benutzer verwalten“ ist reine Admin-Sache: Normale Benutzer kommen weder an die
 * Benutzerliste noch können sie Benutzer anlegen oder löschen.
 */
@WebMvcTest(controllers = FrontendUserController.class)
@Import({ SecurityConfig.class, FrontendUserControllerSecurityTest.EchteFilterBeans.class })
class FrontendUserControllerSecurityTest {

    /** Echter Cloudflare-Filter: MockMvc initialisiert jeden Filter der Kette. */
    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean private FrontendUserProfileService profileService;

    @Test
    @DisplayName("Ohne Anmeldung: 401")
    void anonym() throws Exception {
        mockMvc.perform(get("/api/frontend-users")).andExpect(status().isUnauthorized());
        verifyNoInteractions(profileService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer: weder lesen noch anlegen noch löschen")
    void normalerBenutzer() throws Exception {
        mockMvc.perform(get("/api/frontend-users")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/frontend-users").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Max Mustermann\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/frontend-users/1").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(profileService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin sieht die Benutzerliste")
    void admin() throws Exception {
        given(profileService.list()).willReturn(List.of());
        mockMvc.perform(get("/api/frontend-users")).andExpect(status().isOk());
    }
}
