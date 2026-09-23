package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufPdfService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EinkaufPdfController.class)
@Import({ SecurityConfig.class, EinkaufPdfControllerTest.FilterBeans.class })
class EinkaufPdfControllerTest {
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
    private EinkaufPdfService pdfService;

    @MockBean
    private EinkaufBerechtigungService berechtigungService;

    @MockBean
    private FrontendUserDetailsService userDetailsService;

    @Test
    void liefertPdfNurMitLesenRecht() throws Exception {
        byte[] pdf = "%PDF-1.4 dummy".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        doReturn(7L).when(berechtigungService).verlange(any(Authentication.class), eq(EinkaufBerechtigung.LESEN));
        when(pdfService.entnahmeliste(List.of(12L))).thenReturn(new ByteArrayResource(pdf));

        mockMvc.perform(get("/api/einkauf/lagerentnahmen/pdf").param("bedarfIds", "12")
                        .with(authentication(7L)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().bytes(pdf));

        verify(berechtigungService).verlange(any(Authentication.class), eq(EinkaufBerechtigung.LESEN));
        verify(pdfService).entnahmeliste(List.of(12L));
    }

    @Test
    void blockiertAnonymeAufrufe() throws Exception {
        mockMvc.perform(get("/api/einkauf/lagerentnahmen/pdf").param("bedarfIds", "12"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(berechtigungService, pdfService);
    }

    @Test
    void blockiertBenutzerOhneLesenRecht() throws Exception {
        doThrow(new AccessDeniedException("Einkaufsrecht fehlt"))
                .when(berechtigungService).verlange(any(Authentication.class), eq(EinkaufBerechtigung.LESEN));

        mockMvc.perform(get("/api/einkauf/lagerentnahmen/pdf").param("bedarfIds", "12")
                        .with(authentication(7L)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(pdfService);
    }

    @Test
    void liefert400MitFeldfehlerBeiUngueltigerBedarfsId() throws Exception {
        doReturn(7L).when(berechtigungService).verlange(any(Authentication.class), eq(EinkaufBerechtigung.LESEN));
        when(pdfService.entnahmeliste(List.of(0L))).thenThrow(new IllegalArgumentException("Die Bedarfs-ID ist ungültig."));

        mockMvc.perform(get("/api/einkauf/lagerentnahmen/pdf").param("bedarfIds", "0")
                        .with(authentication(7L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Die Bedarfs-ID ist ungültig."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("bedarfIds"));

        verify(pdfService).entnahmeliste(List.of(0L));
    }

    private static RequestPostProcessor authentication(long id) {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(id, "test@example.com", "Max Mustermann",
                "{noop}dummy", true, Set.of(FrontendUserRole.USER));
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, principal.getPassword(),
                principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}
