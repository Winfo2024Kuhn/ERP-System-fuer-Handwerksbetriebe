package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.service.benachrichtigung.BenachrichtigungService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Glocke: Einträge und ganze Spalten erledigen – Anmeldung, CSRF und Eingabeprüfung. */
@WebMvcTest(controllers = NotificationController.class)
@Import({ SecurityConfig.class, NotificationErledigtEndpointTest.EchteFilterBeans.class })
class NotificationErledigtEndpointTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean private BenachrichtigungService benachrichtigungService;

    private org.springframework.test.web.servlet.ResultActions erledigt(String json) throws Exception {
        return mockMvc.perform(post("/api/notifications/erledigt").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void ohneAnmeldung401() throws Exception {
        erledigt("{\"ids\":[\"TERMIN:1\"]}").andExpect(status().isUnauthorized());
        verify(benachrichtigungService, never()).erledigen(any(), any());
    }

    @Test
    @WithMockUser(username = "max")
    void ohneCsrf403() throws Exception {
        mockMvc.perform(post("/api/notifications/erledigt").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"TERMIN:1\"]}")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/notifications/erledigt/kategorien").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kategorien\":[\"TERMINE\"]}")).andExpect(status().isForbidden());
        verify(benachrichtigungService, never()).erledigen(any(), any());
    }

    @Test
    @WithMockUser(username = "max")
    void eintraegeErledigen204() throws Exception {
        erledigt("{\"ids\":[\"TERMIN:1\",\"EMAIL:456\",\"MONATSABSCHLUSS:2026-09\"]}").andExpect(status().isNoContent());
        verify(benachrichtigungService).erledigen(eq(List.of("TERMIN:1", "EMAIL:456", "MONATSABSCHLUSS:2026-09")), any());
    }

    @ParameterizedTest
    @WithMockUser(username = "max")
    @ValueSource(strings = {
            "{\"ids\":[]}",
            "{}",
            "{\"ids\":[\"'; DROP TABLE email; --\"]}",
            "{\"ids\":[\"<script>alert(1)</script>\"]}",
            "{\"ids\":[\"TERMIN:../../etc/passwd\"]}",
            "{\"ids\":[\"termin:1\"]}",
            "{\"ids\":[null]}" })
    void ungueltigeEintraege400(String json) throws Exception {
        erledigt(json).andExpect(status().isBadRequest());
        verify(benachrichtigungService, never()).erledigen(any(), any());
    }

    @Test
    @WithMockUser(username = "max")
    void zuLangeOderZuVieleEintraege400() throws Exception {
        erledigt("{\"ids\":[\"TERMIN:" + "1".repeat(10_000) + "\"]}").andExpect(status().isBadRequest());
        String viele = String.join(",", java.util.Collections.nCopies(201, "\"TERMIN:1\""));
        erledigt("{\"ids\":[" + viele + "]}").andExpect(status().isBadRequest());
        verify(benachrichtigungService, never()).erledigen(any(), any());
    }

    @Test
    @WithMockUser(username = "geloescht")
    void ohneProfil401() throws Exception {
        willThrow(new BenachrichtigungService.NichtAngemeldetException()).given(benachrichtigungService)
                .erledigen(any(), any());
        erledigt("{\"ids\":[\"TERMIN:1\"]}").andExpect(status().isUnauthorized());

        given(benachrichtigungService.kategorienErledigen(any(), any()))
                .willThrow(new BenachrichtigungService.NichtAngemeldetException());
        mockMvc.perform(post("/api/notifications/erledigt/kategorien").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"kategorien\":[\"TERMINE\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "max")
    void kategorienErledigen200() throws Exception {
        given(benachrichtigungService.kategorienErledigen(eq(List.of("EMAILS_SPAM", "TERMINE")), any())).willReturn(12);
        mockMvc.perform(post("/api/notifications/erledigt/kategorien").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"kategorien\":[\"EMAILS_SPAM\",\"TERMINE\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erledigt").value(12));
    }

    @ParameterizedTest
    @WithMockUser(username = "max")
    @ValueSource(strings = {
            "{\"kategorien\":[]}",
            "{\"kategorien\":[\"GIBT_ES_NICHT\"]}",
            "{\"kategorien\":[\"'; DROP TABLE email; --\"]}",
            "{\"kategorien\":[\"<script>alert(1)</script>\"]}" })
    void ungueltigeKategorien400(String json) throws Exception {
        mockMvc.perform(post("/api/notifications/erledigt/kategorien").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest());
        verify(benachrichtigungService, never()).kategorienErledigen(any(), any());
    }

    @Test
    @WithMockUser(username = "max")
    void sicherungUndUpdateLassenSichAlsSpalteErledigen() throws Exception {
        given(benachrichtigungService.kategorienErledigen(eq(List.of("SICHERUNG", "UPDATE")), any())).willReturn(2);
        mockMvc.perform(post("/api/notifications/erledigt/kategorien").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"kategorien\":[\"SICHERUNG\",\"UPDATE\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.erledigt").value(2));
        erledigt("{\"ids\":[\"SICHERUNG:20261011-020017\",\"SICHERUNG:ALT-20261009-020000\",\"UPDATE:20261011-030119\"]}")
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "max")
    void seiteHoltAlleEintraege() throws Exception {
        given(benachrichtigungService.zusammenfassung(any(), any(), eq(true))).willReturn(
                new org.example.kalkulationsprogramm.dto.Benachrichtigung.BenachrichtigungZusammenfassungDto(0, List.of(), List.of()));
        mockMvc.perform(get("/api/notifications/summary").param("alle", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
        verify(benachrichtigungService).zusammenfassung(any(), any(), eq(true));

        mockMvc.perform(get("/api/notifications/summary")).andExpect(status().isOk());
        verify(benachrichtigungService).zusammenfassung(any(), any(), eq(false));
    }

    @ParameterizedTest
    @WithMockUser(username = "max")
    @ValueSource(strings = { "vielleicht", "'; DROP TABLE x; --", "<script>alert(1)</script>" })
    void ungueltigesAlle400(String wert) throws Exception {
        mockMvc.perform(get("/api/notifications/summary").param("alle", wert)).andExpect(status().isBadRequest());
        verify(benachrichtigungService, never()).zusammenfassung(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void summaryOhneAnmeldung401() throws Exception {
        mockMvc.perform(get("/api/notifications/summary").param("alle", "true")).andExpect(status().isUnauthorized());
        verify(benachrichtigungService, never()).zusammenfassung(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    @WithMockUser(username = "max")
    void unbekannteKategorieMitVerstaendlicherMeldung() throws Exception {
        mockMvc.perform(post("/api/notifications/erledigt/kategorien").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"kategorien\":[\"GIBT_ES_NICHT\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Diese Kategorie gibt es nicht."));
    }
}
