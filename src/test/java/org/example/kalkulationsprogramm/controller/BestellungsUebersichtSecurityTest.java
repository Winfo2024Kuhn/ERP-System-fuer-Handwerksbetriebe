package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.BelegKostenstellenAnteilRepository;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentProjektAnteilRepository;
import org.example.kalkulationsprogramm.repository.LieferantDokumentRepository;
import org.example.kalkulationsprogramm.repository.LieferantGeschaeftsdokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.BelegAuditService;
import org.example.kalkulationsprogramm.service.BelegService;
import org.example.kalkulationsprogramm.service.LieferantDokumentService;
import org.example.kalkulationsprogramm.service.RechnungsVorschlagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Abhängen und Rechnung hochladen ändern Daten – nur angemeldet und nur mit
 * CSRF-Token.
 */
@WebMvcTest(controllers = BestellungsUebersichtController.class)
@Import({ SecurityConfig.class, BestellungsUebersichtSecurityTest.EchteFilterBeans.class })
class BestellungsUebersichtSecurityTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class EchteFilterBeans {
        @org.springframework.context.annotation.Bean
        CloudflareAccessJwtFilter cloudflareAccessJwtFilter() {
            return new CloudflareAccessJwtFilter();
        }
    }

    @Autowired private MockMvc mockMvc;

    @MockBean private LieferantDokumentRepository dokumentRepository;
    @MockBean private LieferantGeschaeftsdokumentRepository geschaeftsdokumentRepository;
    @MockBean private ProjektRepository projektRepository;
    @MockBean private ProjektDokumentRepository projektDokumentRepository;
    @MockBean private LieferantDokumentProjektAnteilRepository projektAnteilRepository;
    @MockBean private KostenstelleRepository kostenstelleRepository;
    @MockBean private FrontendUserProfileRepository frontendUserProfileRepository;
    @MockBean private BelegRepository belegRepository;
    @MockBean private BelegKostenstellenAnteilRepository belegKostenstellenAnteilRepository;
    @MockBean private BelegService belegService;
    @MockBean private BelegAuditService belegAuditService;
    @MockBean private RechnungsVorschlagService rechnungsVorschlagService;
    @MockBean private LieferantDokumentService lieferantDokumentService;
    @MockBean private FrontendUserDetailsService frontendUserDetailsService;

    private static final String ABHAENGEN = "/api/bestellungen-uebersicht/abhaengen";
    private static final String HOCHLADEN = "/api/bestellungen-uebersicht/rechnung-hochladen";

    private final MockMultipartFile pdf = new MockMultipartFile("datei", "rechnung.pdf", "application/pdf",
            "%PDF-1.7".getBytes());

    @Test
    @DisplayName("Abhängen ohne Anmeldung: 401")
    void abhaengenOhneLogin() throws Exception {
        mockMvc.perform(post(ABHAENGEN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dokumentId\": 5}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(rechnungsVorschlagService);
    }

    @Test
    @WithMockUser
    @DisplayName("Abhängen ohne CSRF-Token: 403")
    void abhaengenOhneCsrf() throws Exception {
        mockMvc.perform(post(ABHAENGEN).contentType(MediaType.APPLICATION_JSON).content("{\"dokumentId\": 5}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(rechnungsVorschlagService);
    }

    @Test
    @WithMockUser
    @DisplayName("Abhängen angemeldet mit CSRF: 200")
    void abhaengenErlaubt() throws Exception {
        given(rechnungsVorschlagService.haengeAb(eq(5L), any())).willReturn(2);

        mockMvc.perform(post(ABHAENGEN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dokumentId\": 5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geloest").value(2));
    }

    @Test
    @WithMockUser
    @DisplayName("Abhängen mit ungültiger ID: 400")
    void abhaengenUngueltigeId() throws Exception {
        mockMvc.perform(post(ABHAENGEN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dokumentId\": -1}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rechnungsVorschlagService);
    }

    @Test
    @DisplayName("Hochladen ohne Anmeldung: 401")
    void hochladenOhneLogin() throws Exception {
        mockMvc.perform(multipart(HOCHLADEN).file(pdf).param("bestellDokumentId", "10").with(csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(lieferantDokumentService);
    }

    @Test
    @WithMockUser
    @DisplayName("Hochladen ohne CSRF-Token: 403")
    void hochladenOhneCsrf() throws Exception {
        mockMvc.perform(multipart(HOCHLADEN).file(pdf).param("bestellDokumentId", "10"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(lieferantDokumentService);
    }

    @Test
    @WithMockUser
    @DisplayName("Hochladen angemeldet mit CSRF: 200 mit der neuen Rechnung")
    void hochladenErlaubt() throws Exception {
        Lieferanten lieferant = new Lieferanten();
        lieferant.setId(4L);
        LieferantDokument rechnung = new LieferantDokument();
        rechnung.setId(77L);
        rechnung.setTyp(LieferantDokumentTyp.RECHNUNG);
        rechnung.setLieferant(lieferant);
        rechnung.setOriginalDateiname("rechnung.pdf");
        rechnung.setGespeicherterDateiname("x_rechnung.pdf");
        rechnung.setUploadDatum(LocalDateTime.of(2026, 10, 1, 9, 0));
        given(lieferantDokumentService.rechnungZuBestellungHochladen(eq(10L), any(), any())).willReturn(rechnung);

        mockMvc.perform(multipart(HOCHLADEN).file(pdf).param("bestellDokumentId", "10").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.typ").value("RECHNUNG"))
                .andExpect(jsonPath("$.ausgeblendet").value(false));
    }

    private static RequestPostProcessor csrf() {
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf();
    }
}
