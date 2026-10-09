package org.example.kalkulationsprogramm.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.EmailAttachmentProcessingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Das Neuverarbeiten aller Anhänge eines Lieferanten kostet KI-Aufrufe – nur Admins
 * mit gültigem CSRF-Token dürfen es starten. Früher lag der Endpunkt unter
 * {@code /api/lieferanten/**} und war damit ohne Anmeldung erreichbar.
 */
@WebMvcTest(controllers = LieferantWartungController.class)
@Import({ SecurityConfig.class, LieferantWartungControllerSecurityTest.EchteFilterBeans.class })
class LieferantWartungControllerSecurityTest {

    private static final String PFAD = "/api/admin/lieferanten/5/reprocess-attachments";

    /** Der Cloudflare-Filter muss eine echte Instanz sein (siehe ProjektWartungControllerSecurityTest). */
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
    private LieferantenRepository lieferantenRepository;

    @MockBean
    private EmailRepository emailRepository;

    @MockBean
    private EmailAttachmentProcessingService emailAttachmentProcessingService;

    @MockBean
    private FrontendUserDetailsService frontendUserDetailsService;

    @Test
    @DisplayName("Ohne Anmeldung kein Zugriff")
    void anonymWirdAbgewiesen() throws Exception {
        mockMvc.perform(post(PFAD).with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(lieferantenRepository, emailAttachmentProcessingService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf den Lauf nicht starten")
    void normalerBenutzerWirdAbgewiesen() throws Exception {
        mockMvc.perform(post(PFAD).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(lieferantenRepository, emailAttachmentProcessingService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Ohne CSRF-Token kein Zugriff, auch als Admin")
    void ohneCsrfTokenAbgewiesen() throws Exception {
        mockMvc.perform(post(PFAD)).andExpect(status().isForbidden());
        verifyNoInteractions(lieferantenRepository, emailAttachmentProcessingService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Nur POST – ein GET startet nichts")
    void getStartetNichts() throws Exception {
        mockMvc.perform(get(PFAD)).andExpect(status().is4xxClientError());
        verifyNoInteractions(lieferantenRepository, emailAttachmentProcessingService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin startet den Lauf")
    void adminDarf() throws Exception {
        given(lieferantenRepository.findById(5L)).willReturn(Optional.of(new Lieferanten()));
        given(emailRepository.findByLieferantIdOrderBySentAtDesc(5L)).willReturn(List.of());

        mockMvc.perform(post(PFAD).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lieferantId").value(5))
                .andExpect(jsonPath("$.totalAttachments").value(0));
    }

    private static RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
