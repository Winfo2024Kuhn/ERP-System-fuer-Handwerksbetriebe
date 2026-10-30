package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
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

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.KostenstellenTyp;
import org.example.kalkulationsprogramm.dto.KostenstelleDto;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.KostenstelleBerechtigungService;
import org.example.kalkulationsprogramm.service.KostenstelleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Kostenstellen sieht jeder Angemeldete; verwalten nur, wer das Recht „Kostenstellen verwalten“ hat (Admins immer).
 * Früher stand der ganze Pfad auf ROLE_ADMIN - normale Benutzer sahen den Knopf „Neue Kostenstelle“, bekamen aber 403.
 * Nur Dummy-Daten.
 */
@WebMvcTest(controllers = KostenstelleController.class)
@Import({ SecurityConfig.class, KostenstelleControllerSecurityTest.EchteFilterBeans.class })
class KostenstelleControllerSecurityTest {

    @TestConfiguration
    static class EchteFilterBeans {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockitoBean private KostenstelleService service;
    @MockitoBean private KostenstelleBerechtigungService berechtigung;
    @MockitoBean private FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean private FrontendUserProfileRepository frontendUserProfileRepository;

    private static final String BASIS = "/api/firma/kostenstellen";
    private static final String KOERPER = "{\"bezeichnung\":\"Musterlager\",\"typ\":\"LAGER\"}";

    private static KostenstelleDto dto(Long id, String name, boolean aktiv) {
        KostenstelleDto dto = new KostenstelleDto();
        dto.setId(id);
        dto.setBezeichnung(name);
        dto.setTyp(KostenstellenTyp.LAGER);
        dto.setAktiv(aktiv);
        return dto;
    }

    private void ohneRecht() {
        doThrow(new AccessDeniedException("Du darfst Kostenstellen nicht verwalten.")).when(berechtigung).verlange(any());
    }

    @Test
    @DisplayName("Ohne Anmeldung: 401 beim Lesen, bei der Berechtigung und beim Anlegen")
    void ohneAnmeldung() throws Exception {
        mockMvc.perform(get(BASIS)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASIS + "/berechtigung")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASIS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KOERPER))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf lesen und nach seinem Recht fragen")
    void normalerBenutzerDarfLesen() throws Exception {
        given(service.findAll()).willReturn(List.of(dto(1L, "Musterlager", true)));
        given(berechtigung.darfVerwalten(any())).willReturn(false);

        mockMvc.perform(get(BASIS)).andExpect(status().isOk()).andExpect(jsonPath("$[0].bezeichnung").value("Musterlager"));
        mockMvc.perform(get(BASIS + "/berechtigung")).andExpect(status().isOk()).andExpect(jsonPath("$.darfAendern").value(false));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Ohne Recht: 403 mit Meldung bei allen Änderungen und der Liste der gelöschten")
    void ohneRechtGibt403() throws Exception {
        ohneRecht();

        mockMvc.perform(post(BASIS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KOERPER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Du darfst Kostenstellen nicht verwalten."));
        mockMvc.perform(put(BASIS + "/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KOERPER))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(BASIS + "/1").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post(BASIS + "/1/wiederherstellen").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post(BASIS + "/init").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(get(BASIS + "/geloescht")).andExpect(status().isForbidden());

        verify(service, org.mockito.Mockito.never()).speichern(any());
        verify(service, org.mockito.Mockito.never()).loeschen(any());
        verify(service, org.mockito.Mockito.never()).wiederherstellen(any());
        verify(service, org.mockito.Mockito.never()).findGeloeschte();
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Ohne CSRF-Token: 403, auch mit Recht")
    void ohneCsrfGibt403() throws Exception {
        mockMvc.perform(post(BASIS).contentType(MediaType.APPLICATION_JSON).content(KOERPER))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASIS + "/1/wiederherstellen")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Mit Abteilungs-Recht: anlegen, ändern, löschen, wiederherstellen, gelöschte sehen")
    void mitRechtGehtAlles() throws Exception {
        given(service.speichern(any())).willReturn(dto(5L, "Musterlager", true));
        given(service.wiederherstellen(5L)).willReturn(dto(5L, "Musterlager", true));
        given(service.findGeloeschte()).willReturn(List.of(dto(6L, "Musterauto", false)));

        mockMvc.perform(post(BASIS).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":99,\"bezeichnung\":\"Musterlager\",\"typ\":\"LAGER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(5));
        verify(service).speichern(org.mockito.ArgumentMatchers.argThat(d -> d.getId() == null));

        mockMvc.perform(put(BASIS + "/5").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KOERPER))
                .andExpect(status().isOk());
        mockMvc.perform(delete(BASIS + "/5").with(csrf())).andExpect(status().isNoContent());
        mockMvc.perform(post(BASIS + "/5/wiederherstellen").with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aktiv").value(true));
        mockMvc.perform(get(BASIS + "/geloescht"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].bezeichnung").value("Musterauto"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Fehler aus dem Service kommen als 409/404/400 mit Meldung an")
    void fehlermeldungenKommenAn() throws Exception {
        given(service.speichern(any()))
                .willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Eine Kostenstelle mit diesem Namen gibt es schon."));
        given(service.wiederherstellen(Long.MAX_VALUE))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Diese Kostenstelle gibt es nicht mehr."));

        mockMvc.perform(post(BASIS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(KOERPER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Eine Kostenstelle mit diesem Namen gibt es schon."));
        mockMvc.perform(post(BASIS + "/" + Long.MAX_VALUE + "/wiederherstellen").with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASIS + "/abc/wiederherstellen").with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASIS).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"typ\":\"UNBEKANNT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Andere Firmen-Pfade bleiben Admin-Sache")
    void andereFirmenPfadeBleibenGesperrt() throws Exception {
        mockMvc.perform(post("/api/firma/steuerberater").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }
}
