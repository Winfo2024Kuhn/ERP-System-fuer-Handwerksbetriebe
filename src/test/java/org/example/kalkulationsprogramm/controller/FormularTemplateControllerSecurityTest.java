package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.service.FormularTemplateService;
import org.example.kalkulationsprogramm.service.FormularTextbausteinDefaultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Formularwesen liegt im Admin-Bereich: Vorlagen ändern, anlegen und löschen darf nur der Admin.
 * Lesen bleibt für alle angemeldeten Benutzer – der Dokument-Editor lädt die Vorlagen.
 */
@WebMvcTest(controllers = FormularTemplateController.class)
@Import({ SecurityConfig.class, FormularTemplateControllerSecurityTest.EchteFilterBeans.class })
class FormularTemplateControllerSecurityTest {

    /** Echter Cloudflare-Filter: MockMvc initialisiert jeden Filter der Kette. */
    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean private FormularTemplateService formularTemplateService;
    @MockitoBean private FormularTextbausteinDefaultService textbausteinDefaultService;

    @Test
    @DisplayName("Ohne Anmeldung: 401")
    void anonym() throws Exception {
        mockMvc.perform(get("/api/formulare/templates")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Benutzer darf Vorlagen lesen (Dokument-Editor)")
    void benutzerLiest() throws Exception {
        given(formularTemplateService.listNamedTemplates()).willReturn(List.of());
        mockMvc.perform(get("/api/formulare/templates")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Benutzer darf Vorlagen weder anlegen, ändern noch löschen")
    void benutzerAendertNichts() throws Exception {
        mockMvc.perform(post("/api/formulare/templates").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Max\",\"html\":\"<p>x</p>\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/formulare/template").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/formulare/templates/Standard/rename").with(csrf()).param("newName", "X"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/formulare/templates/Standard").with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/formulare/templates/selection").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(formularTemplateService, textbausteinDefaultService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Dokumentnummer vergeben bleibt für Benutzer erlaubt")
    void dokumentnummer() throws Exception {
        given(formularTemplateService.generateDokumentnummer()).willReturn("2026/10/00001");
        mockMvc.perform(post("/api/formulare/dokumentnummer").with(csrf())).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin darf Vorlagen löschen")
    void adminLoescht() throws Exception {
        mockMvc.perform(delete("/api/formulare/templates/Standard").with(csrf()))
                .andExpect(status().is2xxSuccessful());
    }
}
