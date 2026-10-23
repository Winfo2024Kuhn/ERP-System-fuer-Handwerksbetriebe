package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.RechtstextArt;
import org.example.kalkulationsprogramm.mapper.RechtstextMapper;
import org.example.kalkulationsprogramm.service.RechtstextService;
import org.example.kalkulationsprogramm.service.WiderrufsbelehrungPdfService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rechtstexte gehören zum Formularwesen: lesen alle Angemeldeten, ändern nur der Admin – mit CSRF. */
@WebMvcTest(controllers = RechtstextController.class)
@Import({ SecurityConfig.class, RechtstextMapper.class, RechtstextControllerSecurityTest.EchteFilterBeans.class })
class RechtstextControllerSecurityTest {

    /** Echter Cloudflare-Filter: MockMvc initialisiert jeden Filter der Kette. */
    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    private static final String BASIS = "/api/formulare/rechtstexte/WIDERRUFSBELEHRUNG";
    private static final String BODY = "{\"html\":\"<p>Max Mustermann</p>\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean private RechtstextService rechtstextService;
    @MockitoBean private WiderrufsbelehrungPdfService widerrufsbelehrungPdfService;

    @Test
    @DisplayName("Ohne Anmeldung: 401")
    void anonym() throws Exception {
        mockMvc.perform(get("/api/formulare/rechtstexte")).andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASIS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(rechtstextService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Benutzer darf lesen, aber nicht speichern, zurücksetzen oder Vorschau erzeugen")
    void benutzer() throws Exception {
        given(rechtstextService.aktuell(RechtstextArt.WIDERRUFSBELEHRUNG)).willReturn(new RechtstextService.Rechtstext(
                RechtstextArt.WIDERRUFSBELEHRUNG, 0, LocalDate.of(2026, 10, 13), null, "<p>x</p>", true));
        mockMvc.perform(get(BASIS)).andExpect(status().isOk());
        mockMvc.perform(put(BASIS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASIS + "/standard").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post(BASIS + "/vorschau").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(widerrufsbelehrungPdfService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin ohne CSRF-Token: 403")
    void adminOhneCsrf() throws Exception {
        mockMvc.perform(put(BASIS).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASIS + "/standard")).andExpect(status().isForbidden());
        verifyNoInteractions(rechtstextService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin mit CSRF darf speichern")
    void adminSpeichert() throws Exception {
        given(rechtstextService.speichere(RechtstextArt.WIDERRUFSBELEHRUNG, "<p>Max Mustermann</p>"))
                .willReturn(new RechtstextService.Rechtstext(RechtstextArt.WIDERRUFSBELEHRUNG, 1,
                        LocalDate.of(2026, 11, 2), null, "<p>Max Mustermann</p>", false));
        mockMvc.perform(put(BASIS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }
}
