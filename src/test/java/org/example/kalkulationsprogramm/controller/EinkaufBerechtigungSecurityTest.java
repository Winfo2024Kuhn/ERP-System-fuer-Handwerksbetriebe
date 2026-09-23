package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = EinkaufBerechtigungController.class)
@Import({SecurityConfig.class, EinkaufBerechtigungSecurityTest.FilterBeans.class})
class EinkaufBerechtigungSecurityTest {

    @TestConfiguration
    static class FilterBeans {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EinkaufBerechtigungService service;

    @MockBean
    private FrontendUserDetailsService userDetailsService;

    @Test
    void anonymerZugriffAufEinkaufsrechteIstGesperrt() throws Exception {
        mockMvc.perform(get("/api/einkauf/berechtigungen")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void userDarfEigenesRechtLesenUndRequestIdsWerdenIgnoriert() throws Exception {
        when(service.rechte(any(Authentication.class))).thenReturn(Set.of(EinkaufBerechtigung.LESEN));

        mockMvc.perform(get("/api/einkauf/berechtigungen?frontendUserId=99&mitarbeiterId=99")
                        .with(authentication(7L, FrontendUserRole.USER)))
                .andExpect(status().isOk());

        verify(service).rechte(org.mockito.ArgumentMatchers.argThat(auth ->
                auth.getPrincipal() instanceof FrontendUserPrincipal principal && principal.getId().equals(7L)));
    }

    @Test
    void userDarfBerechtigungenNichtAendernUndCsrfSchuetztSchreibzugriff() throws Exception {
        mockMvc.perform(put("/api/settings/einkauf-berechtigungen/7")
                        .with(authentication(7L, FrontendUserRole.USER))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json").content("{\"rechte\":[\"LESEN\"]}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void adminKannBerechtigungenAendernNurMitCsrfToken() throws Exception {
        mockMvc.perform(put("/api/settings/einkauf-berechtigungen/8")
                        .with(authentication(7L, FrontendUserRole.ADMIN))
                        .contentType("application/json").content("{\"rechte\":[\"LESEN\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/settings/einkauf-berechtigungen/8")
                        .with(authentication(7L, FrontendUserRole.ADMIN))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json").content("{\"rechte\":[\"LESEN\"]}"))
                .andExpect(status().isOk());
        verify(service).setzeRechte(any(Authentication.class), eq(8L), eq(Set.of(EinkaufBerechtigung.LESEN)));
    }

    private static RequestPostProcessor authentication(long id, FrontendUserRole role) {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(id, "test@example.com", "Max Mustermann",
                "{noop}secret", true, Set.of(role));
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, principal.getPassword(),
                principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}
