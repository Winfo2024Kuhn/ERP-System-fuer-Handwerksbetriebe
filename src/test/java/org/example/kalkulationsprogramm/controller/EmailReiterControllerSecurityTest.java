package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.dto.Email.EmailReiterEintragDto;
import org.example.kalkulationsprogramm.dto.Email.EmailReiterSeiteDto;
import org.example.kalkulationsprogramm.service.mail.reiter.EmailReiterService;
import org.example.kalkulationsprogramm.testsupport.SitzungsRechteImTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * E-Mail-Reiter ({@code GET /api/emails/reiter/{bezug}/{id}}) mit echter SecurityConfig: Anmeldung,
 * nur lesend, Antwortform, Standardwerte und Fehlermeldungen. Regeln und Grenzen prüfen
 * {@code EmailReiterServiceTest} und {@code DatenbankKompatibilitaetEmailReiterTest}.
 */
@WebMvcTest(controllers = EmailReiterController.class)
@Import({ SecurityConfig.class, EmailReiterControllerSecurityTest.EchteFilterBeans.class })
class EmailReiterControllerSecurityTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    private static final String URL = "/api/emails/reiter/projekt/5";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean private EmailReiterService reiterService;

    @BeforeEach
    void vorbereiten() {
        Mockito.reset(reiterService);
        SitzungsRechteImTest.rechteUnveraendert(frontendUserDetailsService);
    }

    @Test
    void ohneAnmeldung401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(reiterService);
    }

    @Test
    @WithMockUser(username = "max", roles = "USER")
    @DisplayName("Nur lesend: POST gibt es nicht (ohne CSRF 403, mit CSRF 405)")
    void nurLesend() throws Exception {
        mockMvc.perform(post(URL)).andExpect(status().isForbidden());
        mockMvc.perform(post(URL).with(csrf())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(reiterService);
    }

    @Test
    @WithMockUser(username = "max", roles = "USER")
    void antwortform() throws Exception {
        EmailReiterEintragDto eintrag = new EmailReiterEintragDto(123L, "AW: Angebot Geländer",
                "max.mustermann@example.com", "info@example.com", EmailDirection.IN,
                LocalDateTime.of(2026, 11, 3, 10, 15), "<p>Danke</p>", 3, true, "UNZUSTELLBAR", "unknown user");
        given(reiterService.seite(eq("projekt"), eq(5L), eq(1), eq(10), eq("geländer"), eq("IN"), any()))
                .willReturn(new EmailReiterSeiteDto(List.of(eintrag), 140L, 1, 10));

        mockMvc.perform(get(URL).param("page", "1").param("size", "10").param("q", "geländer")
                .param("richtung", "IN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eintraege[0].id").value(123))
                .andExpect(jsonPath("$.eintraege[0].subject").value("AW: Angebot Geländer"))
                .andExpect(jsonPath("$.eintraege[0].fromAddress").value("max.mustermann@example.com"))
                .andExpect(jsonPath("$.eintraege[0].recipient").value("info@example.com"))
                .andExpect(jsonPath("$.eintraege[0].direction").value("IN"))
                .andExpect(jsonPath("$.eintraege[0].sentAt").value("2026-11-03T10:15:00"))
                .andExpect(jsonPath("$.eintraege[0].bodyHtml").value("<p>Danke</p>"))
                .andExpect(jsonPath("$.eintraege[0].nachrichtenAnzahl").value(3))
                .andExpect(jsonPath("$.eintraege[0].mitAnhang").value(true))
                .andExpect(jsonPath("$.eintraege[0].zustellStatus").value("UNZUSTELLBAR"))
                .andExpect(jsonPath("$.eintraege[0].zustellFehler").value("unknown user"))
                .andExpect(jsonPath("$.gesamt").value(140))
                .andExpect(jsonPath("$.seite").value(1))
                .andExpect(jsonPath("$.seitenGroesse").value(10));
    }

    @Test
    @WithMockUser(username = "max", roles = "USER")
    @DisplayName("Ohne Angaben: Seite 0, 25 je Seite, keine Suche, alle Richtungen")
    void standardwerte() throws Exception {
        given(reiterService.seite(anyString(), anyLong(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(new EmailReiterSeiteDto(List.of(), 0L, 0, 25));

        mockMvc.perform(get("/api/emails/reiter/kunde/7")).andExpect(status().isOk())
                .andExpect(jsonPath("$.eintraege").isEmpty());
        verify(reiterService).seite(eq("kunde"), eq(7L), eq(0), eq(25), isNull(), isNull(), any());
    }

    @Test
    @WithMockUser(username = "max", roles = "USER")
    @DisplayName("Grenzwerte und Angriffstexte gehen unverändert an den Service (er begrenzt und prüft)")
    void durchreichen() throws Exception {
        given(reiterService.seite(anyString(), anyLong(), anyInt(), anyInt(), any(), any(), any()))
                .willReturn(new EmailReiterSeiteDto(List.of(), 0L, 0, 50));

        mockMvc.perform(get(URL).param("page", "-1").param("size", "100000")
                .param("q", "'; DROP TABLE email; --")).andExpect(status().isOk());
        mockMvc.perform(get(URL).param("q", "<script>alert(1)</script>")).andExpect(status().isOk());
        mockMvc.perform(get("/api/emails/reiter/lieferant/" + Long.MAX_VALUE)).andExpect(status().isOk());

        verify(reiterService).seite(eq("projekt"), eq(5L), eq(-1), eq(100000), eq("'; DROP TABLE email; --"),
                isNull(), any());
        verify(reiterService).seite(eq("projekt"), eq(5L), eq(0), eq(25), eq("<script>alert(1)</script>"),
                isNull(), any());
        verify(reiterService).seite(eq("lieferant"), eq(Long.MAX_VALUE), eq(0), eq(25), isNull(), isNull(), any());
    }

    @Test
    @WithMockUser(username = "max", roles = "USER")
    @DisplayName("Fehler des Service kommen als {message} an")
    void fehlermeldungen() throws Exception {
        given(reiterService.seite(eq("rechnung"), anyLong(), anyInt(), anyInt(), any(), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unbekannter Bereich für E-Mails."));
        given(reiterService.seite(eq("projekt"), eq(0L), anyInt(), anyInt(), any(), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Projekt nicht gefunden."));
        given(reiterService.seite(eq("lieferant"), eq(3L), anyInt(), anyInt(), any(), any(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bitte erneut anmelden."));

        mockMvc.perform(get("/api/emails/reiter/rechnung/5")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unbekannter Bereich für E-Mails."));
        mockMvc.perform(get("/api/emails/reiter/projekt/0")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Projekt nicht gefunden."));
        mockMvc.perform(get("/api/emails/reiter/lieferant/3")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "max", roles = "USER")
    @DisplayName("Keine Zahl als ID oder Seite → 400, Service wird nicht gefragt")
    void keineZahl400() throws Exception {
        mockMvc.perform(get("/api/emails/reiter/projekt/abc")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/emails/reiter/projekt/1 OR 1=1")).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).param("size", "viele")).andExpect(status().isBadRequest());
        verifyNoInteractions(reiterService);
    }
}
