package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.NoSuchElementException;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachDto;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachSpeichernRequest;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachTestErgebnis;
import org.example.kalkulationsprogramm.service.PostfachService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Einstellungen → E-Mail → Postfächer: nur Admins, CSRF Pflicht, Fehler in Handwerker-Sprache.
 * Läuft mit der echten SecurityConfig.
 */
@WebMvcTest(controllers = PostfachController.class)
@Import({ SecurityConfig.class, PostfachControllerTest.EchteFilterBeans.class })
class PostfachControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockBean private PostfachService postfachService;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;

    /** Verschachtelte Testklassen teilen den Kontext – Mock vor jedem Test leeren. */
    @org.junit.jupiter.api.BeforeEach
    void mockLeeren() {
        org.mockito.Mockito.reset(postfachService);
    }

    private static final String GUELTIG = """
            {"emailAdresse":"max@example.com","anzeigename":"Max Mustermann","aktiv":true,
             "hauptpostfach":false,"fuerGeschaeftsdokumente":false,"passwort":"geheim",
             "smtpHost":"mail.example.com","smtpPort":465,"imapHost":"mail.example.com","imapPort":993}
            """;

    private static PostfachDto dto(long id, String adresse) {
        return new PostfachDto(id, adresse, null, true, 0, false, false, null, true, "mail.example.com", 465,
                "mail.example.com", 993, true, null, null, List.of());
    }

    @Nested
    @DisplayName("Zugriff")
    class Zugriff {

        @Test
        void ohneAnmeldung401() throws Exception {
            mockMvc.perform(get("/api/postfaecher")).andExpect(status().isUnauthorized());
            verifyNoInteractions(postfachService);
        }

        @Test
        @WithMockUser(roles = "USER")
        void normalerBenutzer403() throws Exception {
            mockMvc.perform(get("/api/postfaecher")).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/postfaecher").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(postfachService);
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        void ohneCsrf403() throws Exception {
            mockMvc.perform(post("/api/postfaecher").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/postfaecher/5")).andExpect(status().isForbidden());
            verifyNoInteractions(postfachService);
        }
    }

    @Nested
    @DisplayName("Admin")
    @WithMockUser(roles = "ADMIN")
    class Admin {

        @Test
        void listeOhnePasswort() throws Exception {
            given(postfachService.alle()).willReturn(List.of(dto(1, "info@example.com")));

            mockMvc.perform(get("/api/postfaecher"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].emailAdresse").value("info@example.com"))
                    .andExpect(jsonPath("$[0].passwortGesetzt").value(true))
                    .andExpect(jsonPath("$[0].passwort").doesNotExist())
                    .andExpect(jsonPath("$[0].passwortVerschluesselt").doesNotExist());
        }

        @Test
        void anlegen201() throws Exception {
            given(postfachService.anlegen(any())).willReturn(dto(5, "max@example.com"));

            mockMvc.perform(post("/api/postfaecher").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(5));
        }

        @Test
        void ungueltigeEingabe400MitMeldung() throws Exception {
            given(postfachService.anlegen(any()))
                    .willThrow(new IllegalArgumentException("Bitte eine gültige E-Mail-Adresse eintragen."));

            mockMvc.perform(post("/api/postfaecher").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"emailAdresse\":\"<script>alert(1)</script>\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Bitte eine gültige E-Mail-Adresse eintragen."));
        }

        @Test
        void aendern() throws Exception {
            given(postfachService.aendern(eq(5L), any(PostfachSpeichernRequest.class))).willReturn(dto(5, "max@example.com"));

            mockMvc.perform(put("/api/postfaecher/5").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emailAdresse").value("max@example.com"));
        }

        @Test
        void unbekannt404() throws Exception {
            given(postfachService.aendern(eq(Long.MAX_VALUE), any()))
                    .willThrow(new NoSuchElementException("Postfach nicht gefunden."));

            mockMvc.perform(put("/api/postfaecher/" + Long.MAX_VALUE).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Postfach nicht gefunden."));
        }

        @Test
        void sqlInjectionAlsIdIst400() throws Exception {
            mockMvc.perform(delete("/api/postfaecher/'; DROP TABLE email_absender; --").with(csrf()))
                    .andExpect(status().isBadRequest());
            verify(postfachService, never()).loeschen(any());
        }

        @Test
        void loeschen204() throws Exception {
            mockMvc.perform(delete("/api/postfaecher/5").with(csrf())).andExpect(status().isNoContent());
            verify(postfachService).loeschen(5L);
        }

        @Test
        void loeschenMitMails400() throws Exception {
            willThrow(new IllegalArgumentException("Dieses Postfach hat schon E-Mails – bitte stattdessen ausschalten."))
                    .given(postfachService).loeschen(6L);

            mockMvc.perform(delete("/api/postfaecher/6").with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Dieses Postfach hat schon E-Mails – bitte stattdessen ausschalten."));
        }

        @Test
        void verbindungTesten() throws Exception {
            given(postfachService.teste(any())).willReturn(new PostfachTestErgebnis(true, false, "Versand: ok · Abruf: Anmeldung fehlgeschlagen"));

            mockMvc.perform(post("/api/postfaecher/test").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":5,\"smtpHost\":\"mail.example.com\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.versandOk").value(true))
                    .andExpect(jsonPath("$.abrufOk").value(false));
        }
    }
}
