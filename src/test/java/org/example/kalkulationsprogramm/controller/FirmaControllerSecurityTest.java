package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.dto.SteuerberaterKontaktDto;
import org.example.kalkulationsprogramm.service.EmailAbsenderService;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.example.kalkulationsprogramm.service.KostenstelleService;
import org.example.kalkulationsprogramm.service.SteuerberaterKontaktService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Haelt fest, wer die Firmen-Endpoints lesen und wer sie aendern darf.
 *
 * <p>Hintergrund: In der Stundenuebermittlung an den Steuerberater und im
 * Belegexport waehlt der Benutzer einen Ansprechpartner aus. Die Liste kommt
 * von {@code GET /api/firma/steuerberater}. Solange der ganze Pfad
 * {@code /api/firma/**} auf {@code ROLE_ADMIN} stand, bekamen normale Benutzer
 * dort ein 403 — die Auswahl blieb leer und die Maske meldete faelschlich
 * "kein Steuerberater hinterlegt". Lesen ist deshalb fuer alle angemeldeten
 * Benutzer frei, Schreiben bleibt Admin-Sache.</p>
 */
@WebMvcTest(controllers = FirmaController.class)
@Import({ SecurityConfig.class, FirmaControllerSecurityTest.EchteFilterBeans.class })
class FirmaControllerSecurityTest {

    /**
     * Der Cloudflare-Filter muss eine echte Instanz sein: MockMvc initialisiert jeden
     * Filter der Kette, und ein Mockito-Mock eines {@code OncePerRequestFilter} hat
     * dabei keinen Logger. Per Default ist der Filter abgeschaltet und reicht durch.
     */
    @TestConfiguration
    static class EchteFilterBeans {
        @Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FirmeninformationService firmeninformationService;

    @MockBean
    private KostenstelleService kostenstelleService;

    @MockBean
    private SteuerberaterKontaktService steuerberaterKontaktService;

    @MockBean
    private EmailAbsenderService emailAbsenderService;

    @MockBean
    private FrontendUserDetailsService frontendUserDetailsService;

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf die Steuerberater-Liste fuer die Ansprechpartner-Auswahl lesen")
    void normalerBenutzerDarfSteuerberaterListeLesen() throws Exception {
        BDDMockito.given(steuerberaterKontaktService.findAll()).willReturn(List.of());

        mockMvc.perform(get("/api/firma/steuerberater"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf einen einzelnen Steuerberater lesen")
    void normalerBenutzerDarfEinzelnenSteuerberaterLesen() throws Exception {
        BDDMockito.given(steuerberaterKontaktService.findById(1L))
                .willReturn(new SteuerberaterKontaktDto());

        mockMvc.perform(get("/api/firma/steuerberater/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf die Firmendaten lesen (Firmenname im Betreff)")
    void normalerBenutzerDarfFirmendatenLesen() throws Exception {
        BDDMockito.given(firmeninformationService.getFirmeninformation())
                .willReturn(new FirmeninformationDto());

        mockMvc.perform(get("/api/firma"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ohne Anmeldung bleibt die Steuerberater-Liste gesperrt")
    void anonymWirdAbgewiesen() throws Exception {
        mockMvc.perform(get("/api/firma/steuerberater"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(steuerberaterKontaktService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf keinen Steuerberater anlegen")
    void normalerBenutzerDarfNichtAnlegen() throws Exception {
        mockMvc.perform(post("/api/firma/steuerberater")
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Muster Steuerberatung\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(steuerberaterKontaktService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf keinen Steuerberater loeschen")
    void normalerBenutzerDarfNichtLoeschen() throws Exception {
        mockMvc.perform(delete("/api/firma/steuerberater/1").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(steuerberaterKontaktService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer kommt weiterhin nicht an die Kostenstellen")
    void normalerBenutzerDarfKostenstellenNichtLesen() throws Exception {
        mockMvc.perform(get("/api/firma/kostenstellen"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(kostenstelleService);
    }

    private static RequestPostProcessor csrf() {
        return SecurityMockMvcRequestPostProcessors.csrf();
    }
}
