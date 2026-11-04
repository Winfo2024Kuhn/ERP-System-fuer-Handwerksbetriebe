package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.dto.Email.UnifiedEmailDto;
import org.example.kalkulationsprogramm.service.mail.EmailPapierkorbService;
import org.example.kalkulationsprogramm.dto.Email.EmailSuchErgebnis;
import org.example.kalkulationsprogramm.service.mail.suche.EmailSucheService;
import org.example.kalkulationsprogramm.testsupport.SitzungsRechteImTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Suche ({@code GET /api/emails/suche}) und Papierkorb ({@code POST /api/emails/papierkorb},
 * {@code …/zurueckholen}) mit echter SecurityConfig: Anmeldung, CSRF, Antwortform und Fehlermeldungen.
 * Sichtbarkeit und Grenzen prüfen {@code EmailSucheServiceTest}, {@code EmailPapierkorbServiceTest}
 * und {@code DatenbankKompatibilitaetEmailSucheTest}.
 */
@WebMvcTest(controllers = { EmailSucheController.class, EmailPapierkorbController.class })
@Import({ SecurityConfig.class, EmailSucheUndPapierkorbSecurityTest.EchteFilterBeans.class })
class EmailSucheUndPapierkorbSecurityTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean private EmailSucheService sucheService;
    @MockitoBean private EmailPapierkorbService papierkorbService;

    private static final String IDS = "{\"ids\":[12,13]}";

    @BeforeEach
    void vorbereiten() {
        Mockito.reset(sucheService, papierkorbService);
        SitzungsRechteImTest.rechteUnveraendert(frontendUserDetailsService);
    }

    @Nested
    @DisplayName("Anmeldung und CSRF")
    class Zugriff {

        @Test
        void ohneAnmeldung401() throws Exception {
            mockMvc.perform(get("/api/emails/suche").param("q", "geländer")).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/emails/papierkorb").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(IDS)).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/emails/papierkorb/zurueckholen").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(IDS)).andExpect(status().isUnauthorized());
            verifyNoInteractions(sucheService, papierkorbService);
        }

        @Test
        @WithMockUser(username = "erika", roles = "USER")
        void schreibenOhneCsrf403() throws Exception {
            mockMvc.perform(post("/api/emails/papierkorb").contentType(MediaType.APPLICATION_JSON).content(IDS))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/emails/papierkorb/zurueckholen").contentType(MediaType.APPLICATION_JSON)
                    .content(IDS)).andExpect(status().isForbidden());
            verifyNoInteractions(papierkorbService);
        }
    }

    @Nested
    @DisplayName("GET /api/emails/suche")
    @WithMockUser(username = "erika", roles = "USER")
    class Suche {

        @Test
        void antwortform() throws Exception {
            UnifiedEmailDto dto = new UnifiedEmailDto();
            dto.setId(12L);
            dto.setSubject("Angebot Geländer");
            dto.setFolder("spam");
            given(sucheService.suche(eq("müller geländer"), eq("spam"), eq(1), eq(10), any()))
                    .willReturn(new EmailSuchErgebnis(List.of(dto), true, 1, 10));

            mockMvc.perform(get("/api/emails/suche").param("q", "müller geländer").param("ordner", "spam")
                    .param("page", "1").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eintraege[0].id").value(12))
                    .andExpect(jsonPath("$.eintraege[0].folder").value("spam"))
                    .andExpect(jsonPath("$.hatMehr").value(true))
                    .andExpect(jsonPath("$.seite").value(1))
                    .andExpect(jsonPath("$.seitenGroesse").value(10));
        }

        @Test
        @DisplayName("Ohne Angaben: q fehlt, Ordner leer, Seite 0, 25 je Seite – der Service entscheidet")
        void standardwerte() throws Exception {
            given(sucheService.suche(isNull(), isNull(), eq(0), eq(25), any()))
                    .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte mindestens 2 Zeichen eingeben."));

            mockMvc.perform(get("/api/emails/suche"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Bitte mindestens 2 Zeichen eingeben."));
        }

        @Test
        @DisplayName("Angriffstexte gehen unverändert als Parameter an den Service (keine Verkettung im Controller)")
        void angriffstexte() throws Exception {
            given(sucheService.suche(anyString(), any(), anyInt(), anyInt(), any()))
                    .willReturn(new EmailSuchErgebnis(List.of(), false, 0, 25));

            mockMvc.perform(get("/api/emails/suche").param("q", "'; DROP TABLE email; --"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.eintraege").isEmpty());
            mockMvc.perform(get("/api/emails/suche").param("q", "<script>alert(1)</script>"))
                    .andExpect(status().isOk());
            verify(sucheService).suche(eq("'; DROP TABLE email; --"), isNull(), eq(0), eq(25), any());
            verify(sucheService).suche(eq("<script>alert(1)</script>"), isNull(), eq(0), eq(25), any());
        }

        @Test
        void ungueltigeSeitenzahl400() throws Exception {
            mockMvc.perform(get("/api/emails/suche").param("q", "geländer").param("page", "abc"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(sucheService);
        }
    }

    @Nested
    @DisplayName("POST /api/emails/papierkorb und /zurueckholen")
    @WithMockUser(username = "erika", roles = "USER")
    class Papierkorb {

        @Test
        void inPapierkorb() throws Exception {
            given(papierkorbService.inPapierkorb(eq(List.of(12L, 13L)), any())).willReturn(2);

            mockMvc.perform(post("/api/emails/papierkorb").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content(IDS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verschoben").value(2));
        }

        @Test
        void zurueckholen() throws Exception {
            given(papierkorbService.zurueckholen(eq(List.of(12L, 13L)), any())).willReturn(1);

            mockMvc.perform(post("/api/emails/papierkorb/zurueckholen").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(IDS))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.zurueckgeholt").value(1));
        }

        @Test
        @DisplayName("Fehlender Body oder fehlendes ids: der Service meldet 400 in Handwerker-Sprache")
        void ohneIds() throws Exception {
            given(papierkorbService.inPapierkorb(isNull(), any()))
                    .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keine E-Mails ausgewählt."));

            mockMvc.perform(post("/api/emails/papierkorb").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Keine E-Mails ausgewählt."));
            mockMvc.perform(post("/api/emails/papierkorb").with(csrf()).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Keine E-Mails ausgewählt."));
        }

        @Test
        @DisplayName("Ungültige IDs (0, negativ, Long.MAX_VALUE) reicht der Controller durch – der Service übergeht sie")
        void ungueltigeIds() throws Exception {
            given(papierkorbService.zurueckholen(anyList(), any())).willReturn(0);

            mockMvc.perform(post("/api/emails/papierkorb/zurueckholen").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ids\":[0,-1," + Long.MAX_VALUE + "]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.zurueckgeholt").value(0));
            verify(papierkorbService).zurueckholen(eq(Arrays.asList(0L, -1L, Long.MAX_VALUE)), any());
        }

        @Test
        void keineZahlen400() throws Exception {
            mockMvc.perform(post("/api/emails/papierkorb").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"ids\":[\"'; DROP TABLE email; --\"]}"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(papierkorbService);
        }
    }
}
