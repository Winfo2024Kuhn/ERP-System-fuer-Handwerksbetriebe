package org.example.kalkulationsprogramm.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.FrontendUserProfileService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die Oberfläche entscheidet anhand von {@code /api/auth/me}, ob ein Admin auf die
 * Ersteinrichtung muss ({@code requiresInitialSetup}) und ob KI-Knöpfe aktiv sind
 * ({@code kiAktiv}). Der Gemini-Schlüssel ist dabei keine Pflicht mehr.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerErsteinrichtungTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FrontendUserProfileService profileService;

    @MockBean
    private FrontendUserProfileRepository profileRepository;

    @MockBean
    private SystemSettingsService settingsService;

    private static FrontendUserProfile profil(FrontendUserRole rolle) {
        FrontendUserProfile profil = new FrontendUserProfile();
        profil.setId(42L);
        profil.setDisplayName("Max Mustermann");
        profil.setUsername("max.mustermann");
        profil.setActive(true);
        profil.setRoleSet(Set.of(rolle));
        return profil;
    }

    private UsernamePasswordAuthenticationToken anmeldung(FrontendUserRole rolle) {
        given(profileService.findById(42L)).willReturn(Optional.of(profil(rolle)));
        FrontendUserPrincipal principal = new FrontendUserPrincipal(
                42L, "max.mustermann", "Max Mustermann", "hash", true, Set.of(rolle));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Nested
    @DisplayName("GET /api/auth/me")
    class Me {

        @Test
        @DisplayName("Admin mit Mailserver, ohne Gemini: keine Ersteinrichtung, KI aus")
        void adminOhneGeminiMussNichtZurErsteinrichtung() throws Exception {
            given(settingsService.isInitialConfigurationRequired()).willReturn(false);
            given(settingsService.isGeminiConfigured()).willReturn(false);

            mockMvc.perform(get("/api/auth/me").principal(anmeldung(FrontendUserRole.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.admin").value(true))
                    .andExpect(jsonPath("$.requiresInitialSetup").value(false))
                    .andExpect(jsonPath("$.kiAktiv").value(false));
        }

        @Test
        @DisplayName("Admin ohne Mailserver: muss zur Ersteinrichtung")
        void adminOhneMailserverMussZurErsteinrichtung() throws Exception {
            given(settingsService.isInitialConfigurationRequired()).willReturn(true);
            given(settingsService.isGeminiConfigured()).willReturn(true);

            mockMvc.perform(get("/api/auth/me").principal(anmeldung(FrontendUserRole.ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requiresInitialSetup").value(true))
                    .andExpect(jsonPath("$.kiAktiv").value(true));
        }

        @Test
        @DisplayName("Normaler Nutzer: nie Ersteinrichtung, KI-Status kommt trotzdem mit")
        void nutzerSiehtKiStatusAberNieErsteinrichtung() throws Exception {
            given(settingsService.isInitialConfigurationRequired()).willReturn(true);
            given(settingsService.isGeminiConfigured()).willReturn(true);

            mockMvc.perform(get("/api/auth/me").principal(anmeldung(FrontendUserRole.USER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.admin").value(false))
                    .andExpect(jsonPath("$.requiresInitialSetup").value(false))
                    .andExpect(jsonPath("$.kiAktiv").value(true));
        }

        @Test
        @DisplayName("Ohne Anmeldung: 401")
        void ohneAnmeldung401() throws Exception {
            mockMvc.perform(get("/api/auth/me"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @DisplayName("GET /api/auth/bootstrap-status meldet die Pflicht nur am Mailserver")
    void bootstrapStatusOhneGemini() throws Exception {
        given(profileRepository.countByUsernameIsNotNull()).willReturn(1L);
        given(settingsService.isInitialConfigurationRequired()).willReturn(false);

        mockMvc.perform(get("/api/auth/bootstrap-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasLoginUsers").value(true))
                .andExpect(jsonPath("$.setupRequired").value(false));
    }
}
