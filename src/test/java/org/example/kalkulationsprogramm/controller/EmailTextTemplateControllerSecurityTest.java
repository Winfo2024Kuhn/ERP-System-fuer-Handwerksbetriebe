package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.service.EmailTextTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Mailtexte für Dokumente gelten für alle künftigen Mails: lesen dürfen alle Angemeldeten,
 * anlegen, ändern und löschen nur Admins (Issue #271).
 */
@WebMvcTest(controllers = EmailTextTemplateController.class)
@Import({ SecurityConfig.class, EmailTextTemplateControllerSecurityTest.EchteFilterBeans.class })
class EmailTextTemplateControllerSecurityTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    private static final String VORLAGE = "{\"name\":\"Rechnung\",\"subject\":\"Ihre Rechnung\",\"htmlBody\":\"<p>Hallo</p>\"}";

    @Autowired private MockMvc mockMvc;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockBean private EmailTextTemplateService service;

    @Test
    @DisplayName("Ohne Anmeldung: 401")
    void anonym() throws Exception {
        mockMvc.perform(get("/api/email-textvorlagen")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Benutzer darf lesen, aber nicht anlegen, ändern oder löschen")
    void benutzerNurLesen() throws Exception {
        mockMvc.perform(get("/api/email-textvorlagen")).andExpect(status().isOk());
        mockMvc.perform(post("/api/email-textvorlagen").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(VORLAGE)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/email-textvorlagen/1").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(VORLAGE)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/email-textvorlagen/1").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin darf löschen – aber nur mit CSRF-Token")
    void adminMitCsrf() throws Exception {
        mockMvc.perform(delete("/api/email-textvorlagen/1")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        mockMvc.perform(delete("/api/email-textvorlagen/1").with(csrf())).andExpect(status().is2xxSuccessful());
    }
}
