package org.example.kalkulationsprogramm.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.repository.EmailAttachmentRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.service.DatensatzLockService;
import org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService;
import org.example.kalkulationsprogramm.service.GeminiDokumentAnalyseService;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Der Backfill der Lieferanten-Dokumentenketten schreibt über den gesamten
 * Bestand und hat bewusst keinen Button – er darf nur für Admins erreichbar sein.
 */
@WebMvcTest(controllers = LieferantDokumentController.class)
@Import({ SecurityConfig.class, LieferantDokumentBackfillSecurityTest.EchteFilterBeans.class })
class LieferantDokumentBackfillSecurityTest {

    /** Echte Filter-Instanz, siehe ProjektWartungControllerSecurityTest. */
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
    private GeminiDokumentAnalyseService analyseService;
    @MockBean
    private LieferantDokumentRepository dokumentRepository;
    @MockBean
    private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @MockBean
    private LieferantDokumentService dokumentService;
    @MockBean
    private EmailRepository emailRepository;
    @MockBean
    private EmailAttachmentProcessingService emailAttachmentProcessingService;
    @MockBean
    private EmailAttachmentRepository emailAttachmentRepository;
    @MockBean
    private DatensatzLockService dokumentLockService;
    @MockBean
    private org.example.kalkulationsprogramm.service.LieferantDokumentZugriffService zugriffService;
    @MockBean
    private FrontendUserDetailsService frontendUserDetailsService;

    @ParameterizedTest
    @ValueSource(strings = { "/api/lieferant-dokumente/relink-all", "/api/lieferant-dokumente/lieferant/7/relink" })
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf den Backfill nicht starten")
    void normalerBenutzerWirdAbgewiesen(String pfad) throws Exception {
        mockMvc.perform(post(pfad).with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(analyseService);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/lieferant-dokumente/relink-all", "/api/lieferant-dokumente/lieferant/7/relink" })
    @DisplayName("Ohne Anmeldung kein Zugriff")
    void anonymWirdAbgewiesen(String pfad) throws Exception {
        mockMvc.perform(post(pfad).with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(analyseService);
    }

    @org.junit.jupiter.api.Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin darf den Backfill für alle Lieferanten starten")
    void adminDarfAlle() throws Exception {
        given(analyseService.relinkAlleDokumente()).willReturn(4);

        mockMvc.perform(post("/api/lieferant-dokumente/relink-all").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.neuVerknuepft").value(4));
    }

    @org.junit.jupiter.api.Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin darf den Backfill für einen Lieferanten starten")
    void adminDarfEinenLieferanten() throws Exception {
        given(analyseService.relinkDokumenteByLieferant(7L)).willReturn(2);

        mockMvc.perform(post("/api/lieferant-dokumente/lieferant/7/relink").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.neuVerknuepft").value(2));
    }

    private static RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
