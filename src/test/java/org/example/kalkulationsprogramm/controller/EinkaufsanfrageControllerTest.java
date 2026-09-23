package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufsanfrageService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = EinkaufsanfrageController.class)
@Import({SecurityConfig.class, EinkaufsanfrageControllerTest.FilterBeans.class})
class EinkaufsanfrageControllerTest {
    @TestConfiguration static class FilterBeans { @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); } }
    @Autowired MockMvc mockMvc;
    @MockBean EinkaufsanfrageService service;
    @MockBean EinkaufBerechtigungService berechtigungen;
    @MockBean FrontendUserDetailsService userDetailsService;

    @Test void anonymerZugriffIstGesperrt() throws Exception {
        mockMvc.perform(get("/api/einkauf/anfragen")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service, berechtigungen);
    }
    @Test void anlegenVerlangtBearbeitungsrecht() throws Exception {
        when(berechtigungen.verlange(any(Authentication.class), eq(EinkaufBerechtigung.BEARBEITEN))).thenThrow(new AccessDeniedException("keine Berechtigung"));
        mockMvc.perform(post("/api/einkauf/anfragen").with(authentication()).with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void listeVerlangtLeserechtUndIstPaginiert() throws Exception {
        when(berechtigungen.verlange(any(Authentication.class), eq(EinkaufBerechtigung.LESEN))).thenReturn(4L);
        when(service.suchen(any())).thenReturn(org.springframework.data.domain.Page.empty());
        mockMvc.perform(get("/api/einkauf/anfragen").with(authentication())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray()).andExpect(jsonPath("$.totalElements").value(0));
        verify(service).suchen(any());
    }
    private static RequestPostProcessor authentication() {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(4L, "test@example.com", "Max Mustermann", "{noop}dummy", true, Set.of(FrontendUserRole.USER));
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}
