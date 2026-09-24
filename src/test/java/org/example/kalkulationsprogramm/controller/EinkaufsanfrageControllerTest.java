package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Test void lieferantenstatusAendernErfordertBearbeitungsrecht() throws Exception {
        when(berechtigungen.verlange(any(Authentication.class), eq(EinkaufBerechtigung.BEARBEITEN))).thenReturn(4L);
        when(service.aktualisiereLieferantenstatus(eq(22L), eq(41L), any(), eq(4L)))
                .thenReturn(new org.example.kalkulationsprogramm.dto.Einkauf.EinkaufsanfrageDto.Lieferantenbeteiligung(41L, 3L, "Lieferant C", "ABGESAGT", 3));
        mockMvc.perform(patch("/api/einkauf/anfragen/22/lieferanten/41/status").with(authentication())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json")
                        .content("{\"version\":2,\"status\":\"ABSAGE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ABGESAGT"));
        verify(service).aktualisiereLieferantenstatus(eq(22L), eq(41L), any(), eq(4L));
    }

    @Test void anfrageLoeschenVerlangtBearbeitungsrechtUndVersionsstand() throws Exception {
        when(berechtigungen.verlange(any(Authentication.class), eq(EinkaufBerechtigung.BEARBEITEN))).thenReturn(4L);
        mockMvc.perform(delete("/api/einkauf/anfragen/22?version=0").with(authentication())
                        .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNoContent());
        org.mockito.Mockito.verify(service).loeschen(22L, 0L, 4L);
    }

    @Test void historischeFassungUndListeVerlangenLeserecht() throws Exception {
        when(berechtigungen.verlange(any(Authentication.class), eq(EinkaufBerechtigung.LESEN)))
                .thenThrow(new AccessDeniedException("keine Berechtigung"));
        mockMvc.perform(get("/api/einkauf/anfragen/22/revisionen").with(authentication()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/einkauf/anfragen/22/revisionen/31").with(authentication()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private static RequestPostProcessor authentication() {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(4L, "test@example.com", "Max Mustermann", "{noop}dummy", true, Set.of(FrontendUserRole.USER));
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}
