package org.example.kalkulationsprogramm.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.dto.WerkstoffzeugnisNachleseErgebnis;
import org.example.kalkulationsprogramm.service.WerkstoffzeugnisNachleseService;
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
 * Das Nachlesen der Werkstoffzeugnisse kostet KI-Aufrufe und fasst den ganzen
 * Bestand an – nur Admins mit gültigem CSRF-Token dürfen es starten.
 */
@WebMvcTest(controllers = LieferantDokumentWartungController.class)
@Import({ SecurityConfig.class, LieferantDokumentWartungControllerSecurityTest.EchteFilterBeans.class })
class LieferantDokumentWartungControllerSecurityTest {

    private static final String PFAD = "/api/admin/lieferant-dokumente/werkstoffzeugnisse/nachlesen";

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
    private WerkstoffzeugnisNachleseService nachleseService;

    @MockBean
    private FrontendUserDetailsService frontendUserDetailsService;

    @Test
    @DisplayName("Ohne Anmeldung kein Zugriff")
    void anonymWirdAbgewiesen() throws Exception {
        mockMvc.perform(post(PFAD).with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(nachleseService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf den Lauf nicht starten")
    void normalerBenutzerWirdAbgewiesen() throws Exception {
        mockMvc.perform(post(PFAD).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(nachleseService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Ohne CSRF-Token kein Zugriff, auch als Admin")
    void ohneCsrfTokenAbgewiesen() throws Exception {
        mockMvc.perform(post(PFAD)).andExpect(status().isForbidden());
        verifyNoInteractions(nachleseService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Nur POST – ein GET startet nichts")
    void getStartetNichts() throws Exception {
        mockMvc.perform(get(PFAD)).andExpect(status().is4xxClientError());
        verifyNoInteractions(nachleseService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin startet den Lauf und bekommt die Zahlen zurück")
    void adminDarf() throws Exception {
        given(nachleseService.liesZeugnisseNach(org.mockito.ArgumentMatchers.any()))
                .willReturn(new WerkstoffzeugnisNachleseErgebnis(3, 2, 1, 2, List.of(42L)));

        mockMvc.perform(post(PFAD).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gesamt").value(3))
                .andExpect(jsonPath("$.erfolgreich").value(2))
                .andExpect(jsonPath("$.fehlgeschlagen").value(1))
                .andExpect(jsonPath("$.verknuepft").value(2))
                .andExpect(jsonPath("$.fehlgeschlageneIds[0]").value(42));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Läuft schon ein Lauf, kommt 409 mit verständlicher Meldung")
    void laufenderLaufGibt409() throws Exception {
        given(nachleseService.liesZeugnisseNach(org.mockito.ArgumentMatchers.any()))
                .willThrow(new WerkstoffzeugnisNachleseService.LaufAktivException());

        mockMvc.perform(post(PFAD).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("gerade schon")));
    }

    private static RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
