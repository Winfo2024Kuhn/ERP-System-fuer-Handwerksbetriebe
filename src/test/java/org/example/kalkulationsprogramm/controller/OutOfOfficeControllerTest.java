package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz.AbwesenheitsPostfachDto;
import org.example.kalkulationsprogramm.dto.Abwesenheitsnotiz.AbwesenheitsnotizDto;
import org.example.kalkulationsprogramm.dto.Postfach.PostfachRefDto;
import org.example.kalkulationsprogramm.service.AbwesenheitsnotizService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * E-Mail-Einstellungen → Abwesenheitsnotizen: jeder angemeldete Benutzer, CSRF Pflicht,
 * Fehler in Handwerker-Sprache. Läuft mit der echten SecurityConfig.
 */
@WebMvcTest(controllers = OutOfOfficeController.class)
@Import({ SecurityConfig.class, OutOfOfficeControllerTest.EchteFilterBeans.class })
class OutOfOfficeControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AbwesenheitsnotizService service;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;

    /** Verschachtelte Testklassen teilen den Kontext – Mock vor jedem Test leeren. */
    @BeforeEach
    void mockLeeren() {
        org.mockito.Mockito.reset(service);
    }

    private static final String GUELTIG = """
            {"title":"Urlaub","startDate":"2026-12-21","endDate":"2027-01-06","active":true,
             "subject":"Automatische Antwort: {{subject}}","message":"Bin im Urlaub.","postfachIds":[7]}
            """;

    private static AbwesenheitsnotizDto notiz(long id) {
        return new AbwesenheitsnotizDto(id, "Urlaub", LocalDate.of(2026, 12, 21), LocalDate.of(2027, 1, 6), true,
                "Automatische Antwort: {{subject}}", "Bin im Urlaub.", null,
                List.of(new PostfachRefDto(7L, "max.mustermann@example.com", "Max Mustermann")));
    }

    @Nested
    @DisplayName("Zugriff")
    class Zugriff {

        @Test
        void ohneAnmeldung401() throws Exception {
            mockMvc.perform(get("/api/email/outofoffice")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/email/outofoffice/postfaecher")).andExpect(status().isUnauthorized());
            verifyNoInteractions(service);
        }

        @Test
        @WithMockUser(username = "max", roles = "USER")
        void ohneCsrf403() throws Exception {
            mockMvc.perform(post("/api/email/outofoffice").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/email/outofoffice/5")).andExpect(status().isForbidden());
            verifyNoInteractions(service);
        }

        @Test
        @WithMockUser(username = "max", roles = "USER")
        void alterEndpunktActiveGibtEsNichtMehr() throws Exception {
            mockMvc.perform(get("/api/email/outofoffice/active")).andExpect(status().is4xxClientError());
            verifyNoInteractions(service);
        }
    }

    @Nested
    @DisplayName("Angemeldeter Mitarbeiter")
    @WithMockUser(username = "max", roles = "USER")
    class Mitarbeiter {

        @Test
        void waehlbarePostfaecher() throws Exception {
            given(service.waehlbarePostfaecher(any())).willReturn(List.of(
                    new AbwesenheitsPostfachDto(7L, "max.mustermann@example.com", "Max Mustermann", true),
                    new AbwesenheitsPostfachDto(1L, "info@example.com", null, false)));

            mockMvc.perform(get("/api/email/outofoffice/postfaecher"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(7))
                    .andExpect(jsonPath("$[0].eigenes").value(true))
                    .andExpect(jsonPath("$[1].emailAdresse").value("info@example.com"))
                    .andExpect(jsonPath("$[1].eigenes").value(false));
            verify(service).waehlbarePostfaecher(argThat(a -> "max".equals(a.getName())));
        }

        @Test
        void listeMitPostfaechern() throws Exception {
            given(service.liste(any())).willReturn(List.of(notiz(3)));

            mockMvc.perform(get("/api/email/outofoffice"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(3))
                    .andExpect(jsonPath("$[0].startAt").value("2026-12-21"))
                    .andExpect(jsonPath("$[0].endAt").value("2027-01-06"))
                    .andExpect(jsonPath("$[0].subjectTemplate").value("Automatische Antwort: {{subject}}"))
                    .andExpect(jsonPath("$[0].postfaecher[0].emailAdresse").value("max.mustermann@example.com"));
        }

        @Test
        void speichern200() throws Exception {
            given(service.speichern(any(), any())).willReturn(notiz(3));

            mockMvc.perform(post("/api/email/outofoffice").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(3))
                    .andExpect(jsonPath("$.postfaecher[0].id").value(7));
            verify(service).speichern(argThat(r -> r.postfachIds().equals(List.of(7L))
                    && "Urlaub".equals(r.title())
                    && LocalDate.of(2026, 12, 21).equals(r.startDate())), argThat(a -> "max".equals(a.getName())));
        }

        @Test
        void ungueltigeAngaben400MitMeldung() throws Exception {
            given(service.speichern(any(), any()))
                    .willThrow(new IllegalArgumentException("Bitte mindestens ein Postfach auswählen."));

            mockMvc.perform(post("/api/email/outofoffice").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Bitte mindestens ein Postfach auswählen."));
        }

        @Test
        void fremdesPostfach403MitMeldung() throws Exception {
            given(service.speichern(any(), any())).willThrow(new AccessDeniedException(
                    "Abwesenheitsnotizen gehen nur für Ihr eigenes Postfach und allgemeine Postfächer."));

            mockMvc.perform(post("/api/email/outofoffice").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(
                            "Abwesenheitsnotizen gehen nur für Ihr eigenes Postfach und allgemeine Postfächer."));
        }

        @Test
        void unbekannteNotiz404() throws Exception {
            given(service.speichern(any(), any()))
                    .willThrow(new NoSuchElementException("Diese Abwesenheitsnotiz gibt es nicht mehr."));

            mockMvc.perform(post("/api/email/outofoffice").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(GUELTIG.replaceFirst("\\{", "{\"id\":999,")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Diese Abwesenheitsnotiz gibt es nicht mehr."));
        }

        @Test
        void schadhafteTexteWerdenNurAlsTextWeitergereicht() throws Exception {
            given(service.speichern(any(), any())).willReturn(notiz(3));
            String body = GUELTIG
                    .replace("\"Urlaub\"", "\"'; DROP TABLE out_of_office_schedule; --\"")
                    .replace("\"Bin im Urlaub.\"", "\"<script>alert(1)</script>\"");

            mockMvc.perform(post("/api/email/outofoffice").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isOk());
            verify(service).speichern(argThat(r -> r.title().startsWith("'; DROP TABLE")
                    && r.message().equals("<script>alert(1)</script>")), any());
        }

        @Test
        void kaputtesJson400() throws Exception {
            mockMvc.perform(post("/api/email/outofoffice").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":\"kein-datum\"}"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(service);
        }

        @Test
        void loeschen200() throws Exception {
            mockMvc.perform(delete("/api/email/outofoffice/3").with(csrf())).andExpect(status().isOk());
            verify(service).loeschen(eq(3L), argThat(a -> "max".equals(a.getName())));
        }

        @Test
        void loeschenUngueltigeIds404() throws Exception {
            for (long id : new long[] { -1L, 0L, Long.MAX_VALUE }) {
                willThrow(new NoSuchElementException("Diese Abwesenheitsnotiz gibt es nicht mehr."))
                        .given(service).loeschen(eq(id), any());
                mockMvc.perform(delete("/api/email/outofoffice/" + id).with(csrf()))
                        .andExpect(status().isNotFound());
            }
        }

        @Test
        void loeschenMitTextAlsId400() throws Exception {
            mockMvc.perform(delete("/api/email/outofoffice/abc").with(csrf())).andExpect(status().isBadRequest());
            verifyNoInteractions(service);
        }
    }
}
