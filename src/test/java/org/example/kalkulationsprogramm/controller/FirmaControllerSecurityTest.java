package org.example.kalkulationsprogramm.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    @MockitoBean
    private FirmeninformationService firmeninformationService;

    @MockitoBean
    private KostenstelleService kostenstelleService;

    @MockitoBean
    private SteuerberaterKontaktService steuerberaterKontaktService;

    @MockitoBean
    private org.example.kalkulationsprogramm.service.FeiertageVorschauService feiertageVorschauService;

    @MockitoBean
    private EmailAbsenderService emailAbsenderService;

    @MockitoBean
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
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf das Firmenlogo laden")
    void normalerBenutzerDarfLogoLaden() throws Exception {
        BDDMockito.given(firmeninformationService.loadLogoBytes()).willReturn(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        BDDMockito.given(firmeninformationService.ermittleLogoContentType()).willReturn("image/png");

        mockMvc.perform(get("/api/firma/logo"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer darf das Firmenlogo weder hochladen noch loeschen")
    void normalerBenutzerDarfLogoNichtAendern() throws Exception {
        mockMvc.perform(multipart("/api/firma/logo")
                        .file(new MockMultipartFile("datei", "logo.png", "image/png", new byte[] {1, 2, 3}))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/firma/logo").with(csrf()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(firmeninformationService);
    }

    @Test
    @DisplayName("Ohne Anmeldung bleibt das Firmenlogo gesperrt")
    void anonymBekommtKeinLogo() throws Exception {
        mockMvc.perform(get("/api/firma/logo"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(firmeninformationService);
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

    // ==================== Feiertage-Vorschau (Issue #222) ====================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin bekommt den Bundesland-Vorschlag aus der Postleitzahl")
    void adminBekommtFeiertageVorschau() throws Exception {
        BDDMockito.given(feiertageVorschauService.vorschau("97259", null, 2026)).willReturn(
                new org.example.kalkulationsprogramm.dto.Feiertag.FeiertageVorschauDto("BY", "BY", "Bayern", 2026,
                        List.of(), List.of("Hinweis")));

        mockMvc.perform(get("/api/firma/feiertage-vorschau").param("plz", "97259").param("jahr", "2026"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.vorgeschlagenesBundesland").value("BY"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.bundeslandName").value("Bayern"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Normaler Benutzer bekommt keine Feiertage-Vorschau (nur Firmendaten-Pflege)")
    void normalerBenutzerBekommtKeineFeiertageVorschau() throws Exception {
        mockMvc.perform(get("/api/firma/feiertage-vorschau").param("plz", "97259"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(feiertageVorschauService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Ungültige Eingaben in der Vorschau werden als 400 mit Meldung beantwortet")
    void feiertageVorschauUngueltigGibt400() throws Exception {
        BDDMockito.given(feiertageVorschauService.vorschau(null, "'; DROP TABLE x; --", null)).willThrow(
                new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Bitte ein gültiges Bundesland wählen."));

        mockMvc.perform(get("/api/firma/feiertage-vorschau").param("bundesland", "'; DROP TABLE x; --"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value("Bitte ein gültiges Bundesland wählen."));
    }

    @Test
    @DisplayName("Ohne Anmeldung keine Feiertage-Vorschau")
    void anonymBekommtKeineFeiertageVorschau() throws Exception {
        mockMvc.perform(get("/api/firma/feiertage-vorschau"))
                .andExpect(status().isUnauthorized());
    }
}
