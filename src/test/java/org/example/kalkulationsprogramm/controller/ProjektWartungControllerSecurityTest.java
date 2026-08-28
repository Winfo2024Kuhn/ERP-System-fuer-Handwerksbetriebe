package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Hält fest, dass der Preis-Nachlauf wirklich nur für Admins erreichbar ist.
 *
 * <p>Der Endpoint liegt bewusst unter {@code /api/admin/...}: Der Pfad
 * {@code /api/projekte/**} ist für die Zeiterfassungs-PWA ohne Anmeldung
 * freigeschaltet. Dieser Test würde brechen, falls jemand den Endpoint dorthin
 * zurückverschiebt oder die Rollenregel entfernt.</p>
 */
@WebMvcTest(controllers = ProjektWartungController.class)
@Import({ SecurityConfig.class, ProjektWartungControllerSecurityTest.EchteFilterBeans.class })
class ProjektWartungControllerSecurityTest {

    /**
     * Der Cloudflare-Filter muss eine echte Instanz sein: MockMvc initialisiert jeden
     * Filter der Kette, und ein Mockito-Mock eines {@code OncePerRequestFilter} hat
     * dabei keinen Logger. Per Default ist der Filter abgeschaltet und reicht durch.
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AusgangsGeschaeftsDokumentService ausgangsGeschaeftsDokumentService;

    @MockBean
    private FrontendUserDetailsService frontendUserDetailsService;

    @Test
    @DisplayName("Ohne Anmeldung kein Zugriff")
    void anonymWirdAbgewiesen() throws Exception {
        mockMvc.perform(post("/api/admin/projekte/preise-nachtragen").with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ausgangsGeschaeftsDokumentService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf den Nachlauf nicht starten")
    void normalerBenutzerWirdAbgewiesen() throws Exception {
        mockMvc.perform(post("/api/admin/projekte/preise-nachtragen").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ausgangsGeschaeftsDokumentService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin darf")
    void adminDarf() throws Exception {
        org.mockito.BDDMockito.given(ausgangsGeschaeftsDokumentService.trageFehlendePreiseNach())
                .willReturn(new AusgangsGeschaeftsDokumentService.PreisNachtragErgebnis(3, 1));

        mockMvc.perform(post("/api/admin/projekte/preise-nachtragen").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Ohne CSRF-Token kein Zugriff, auch als Admin")
    void ohneCsrfTokenAbgewiesen() throws Exception {
        mockMvc.perform(post("/api/admin/projekte/preise-nachtragen"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ausgangsGeschaeftsDokumentService);
    }

    @Test
    @DisplayName("Rabatt-Korrektur: ohne Anmeldung kein Zugriff")
    void rabattKorrekturAnonymWirdAbgewiesen() throws Exception {
        mockMvc.perform(post("/api/admin/projekte/rabatt-betraege-korrigieren").with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ausgangsGeschaeftsDokumentService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Rabatt-Korrektur: normaler Benutzer darf den Bestand nicht umrechnen")
    void rabattKorrekturNormalerBenutzerWirdAbgewiesen() throws Exception {
        mockMvc.perform(post("/api/admin/projekte/rabatt-betraege-korrigieren").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ausgangsGeschaeftsDokumentService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Rabatt-Korrektur: Admin darf")
    void rabattKorrekturAdminDarf() throws Exception {
        org.mockito.BDDMockito.given(ausgangsGeschaeftsDokumentService
                        .korrigiereRabattBetraege(org.mockito.ArgumentMatchers.any(),
                                org.mockito.ArgumentMatchers.any()))
                .willReturn(new AusgangsGeschaeftsDokumentService.RabattKorrekturErgebnis(2, 1, 1));

        mockMvc.perform(post("/api/admin/projekte/rabatt-betraege-korrigieren").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Rabatt-Korrektur: ohne CSRF-Token kein Zugriff, auch als Admin")
    void rabattKorrekturOhneCsrfTokenAbgewiesen() throws Exception {
        mockMvc.perform(post("/api/admin/projekte/rabatt-betraege-korrigieren"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ausgangsGeschaeftsDokumentService);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
