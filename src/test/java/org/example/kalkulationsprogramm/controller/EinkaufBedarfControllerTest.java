package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.einkauf.EinkaufBerechtigung;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBedarfService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufBerechtigungService;
import org.example.kalkulationsprogramm.service.einkauf.EinkaufZeichnungsbedarfService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.mock.web.MockPart;
import org.springframework.http.MediaType;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EinkaufBedarfController.class)
@Import({SecurityConfig.class, EinkaufBedarfControllerTest.FilterBeans.class})
class EinkaufBedarfControllerTest {
    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @Autowired MockMvc mockMvc;
    @MockBean EinkaufBedarfService bedarfService;
    @MockBean EinkaufBerechtigungService berechtigungService;
    @MockBean EinkaufZeichnungsbedarfService zeichnungsbedarfe;
    @MockBean FrontendUserDetailsService userDetailsService;

    @Test
    void anonymerZugriffAufBedarfeIstGesperrt() throws Exception {
        mockMvc.perform(get("/api/einkauf/bedarf")).andExpect(status().isUnauthorized());
        verifyNoInteractions(bedarfService, berechtigungService);
    }

    @Test
    void listeErfordertLeserechtUndGibtEineSeiteZurueck() throws Exception {
        when(berechtigungService.verlange(any(Authentication.class), eq(EinkaufBerechtigung.LESEN))).thenReturn(7L);
        when(bedarfService.suche(eq("Schraube"), eq(42L), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/einkauf/bedarf?q=Schraube&projektId=42")
                        .with(authentication(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(bedarfService).suche(eq("Schraube"), eq(42L), any());
    }

    @Test
    void schreibzugriffErfordertBearbeitungsrecht() throws Exception {
        when(berechtigungService.verlange(any(Authentication.class), eq(EinkaufBerechtigung.BEARBEITEN)))
                .thenThrow(new AccessDeniedException("keine Berechtigung"));

        mockMvc.perform(post("/api/einkauf/bedarf")
                        .with(authentication(7L))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content("{\"position\":null,\"liefergruppe\":null,\"artikelInProjektId\":null}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bedarfService);
    }

    @Test
    void validierungsfehlerHabenFieldErrorsUndHttp400() throws Exception {
        when(berechtigungService.verlange(any(Authentication.class), eq(EinkaufBerechtigung.BEARBEITEN)))
                .thenReturn(7L);
        doThrow(new IllegalArgumentException("Bitte geben Sie die Liefergruppe an."))
                .when(bedarfService).anlegen(any(), eq(7L));

        mockMvc.perform(post("/api/einkauf/bedarf")
                        .with(authentication(7L))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType("application/json")
                        .content("{\"position\":null,\"liefergruppe\":null,\"artikelInProjektId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte geben Sie die Liefergruppe an."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("request"));
    }

    @Test
    void zeichnungsteilErstanlageErfordertBearbeitungsrechtUndMultipartDatei() throws Exception {
        when(berechtigungService.verlange(any(Authentication.class), eq(EinkaufBerechtigung.BEARBEITEN))).thenReturn(7L);
        when(zeichnungsbedarfe.anlegen(any(), any(), eq("B"), eq(7L))).thenReturn(null);
        MockPart bedarf = new MockPart("bedarf", "{\"position\":{\"art\":\"ZEICHNUNGSTEIL\",\"interneReferenz\":\"ZT-4\",\"zeichnungsnummer\":\"Z-4\",\"zeichnungsrevision\":\"B\",\"bezeichnung\":\"Träger\",\"basis\":{\"menge\":2,\"einheit\":\"STUECK\",\"stueckzahl\":2},\"dokumente\":[],\"anlageVersionIds\":[]},\"liefergruppe\":{\"projektId\":9}}".getBytes());
        bedarf.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/einkauf/bedarf/zeichnungsteil")
                        .file("datei", "%PDF-1.7 Dummy".getBytes()).part(bedarf).param("revision", "B")
                        .with(authentication(7L)).with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isCreated());
        verify(zeichnungsbedarfe).anlegen(any(), any(), eq("B"), eq(7L));
    }

    private static RequestPostProcessor authentication(long id) {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(id, "test@example.com", "Max Mustermann",
                "{noop}dummy", true, Set.of(FrontendUserRole.USER));
        Authentication auth = new UsernamePasswordAuthenticationToken(principal, principal.getPassword(),
                principal.getAuthorities());
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
    }
}
