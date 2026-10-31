package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.Textbaustein;
import org.example.kalkulationsprogramm.repository.TextbausteinRepository;
import org.example.kalkulationsprogramm.service.TextbausteinKatalogService;
import org.example.kalkulationsprogramm.service.TextbausteinService;
import org.example.kalkulationsprogramm.testsupport.SitzungsRechteImTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Textbausteine: alle Angemeldeten dürfen lesen und pflegen, mit CSRF. Echte Services, damit
 * Meldungen und Kataloge bis in die JSON-Antwort geprüft werden.
 */
@WebMvcTest(controllers = TextbausteinController.class)
@Import({SecurityConfig.class, TextbausteinService.class, TextbausteinKatalogService.class,
        TextbausteinControllerSecurityTest.Filters.class})
class TextbausteinControllerSecurityTest {

    @TestConfiguration
    static class Filters {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @Autowired MockMvc mvc;
    @MockitoBean FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean TextbausteinRepository repository;

    private static final String GUELTIG = """
            {"name":"Angebot Einleitung","typ":"VORTEXT","beschreibung":"Für Max Mustermann",
             "html":"<p>{{Anrede}} {{KUNDENNAME}}</p>","placeholders":["{{Anrede}}","{{KUNDENNAME}}"],
             "dokumenttypen":["Angebot","1. Mahnung"]}""";

    @BeforeEach
    void sitzungsRechteUnveraendert() {
        SitzungsRechteImTest.rechteUnveraendert(frontendUserDetailsService);
    }

    private static String mitDokumentart(String dokumentart) {
        return "{\"name\":\"Test\",\"typ\":\"FREITEXT\",\"dokumenttypen\":[\"" + dokumentart + "\"]}";
    }

    @Test
    void ohneAnmeldungKeinZugriff() throws Exception {
        mvc.perform(get("/api/textbausteine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/textbausteine/placeholders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/textbausteine/dokumenttyp-gruppen")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/textbausteine").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test
    @WithMockUser(roles = "USER")
    void ohneCsrfKeineAenderung() throws Exception {
        mvc.perform(post("/api/textbausteine").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/textbausteine/1").contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/textbausteine/1")).andExpect(status().isForbidden());
        verifyNoInteractions(repository);
    }

    @Test
    @WithMockUser(roles = "USER")
    void platzhalterMitBeispielUndGruppe() throws Exception {
        mvc.perform(get("/api/textbausteine/placeholders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(16))
                .andExpect(jsonPath("$[1].token").value("{{KUNDENNAME}}"))
                .andExpect(jsonPath("$[1].label").value("Kundenname"))
                .andExpect(jsonPath("$[1].beschreibung").value("Name bzw. Firma des Kunden"))
                .andExpect(jsonPath("$[1].beispiel").value("Max Mustermann"))
                .andExpect(jsonPath("$[1].gruppe").value("Kunde"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void dokumentartenGruppiert() throws Exception {
        mvc.perform(get("/api/textbausteine/dokumenttyp-gruppen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gruppe").value("Angebote & Aufträge"))
                .andExpect(jsonPath("$[0].dokumenttypen[2]").value("Auftragsbestätigung"))
                .andExpect(jsonPath("$[2].dokumenttypen[1]").value("1. Mahnung"))
                .andExpect(jsonPath("$.length()").value(4));
    }

    @Test
    @WithMockUser(roles = "USER")
    void anlegenLiefertGespeichertenBaustein() throws Exception {
        when(repository.save(any(Textbaustein.class))).thenAnswer(a -> {
            Textbaustein t = a.getArgument(0);
            t.setId(42L);
            return t;
        });

        mvc.perform(post("/api/textbausteine").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.typ").value("VORTEXT"))
                .andExpect(jsonPath("$.beschreibung").value("Für Max Mustermann"))
                .andExpect(jsonPath("$.dokumenttypen[0]").value("Angebot"))
                .andExpect(jsonPath("$.dokumenttypen[1]").value("1. Mahnung"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"'; DROP TABLE textbaustein; --", "<script>alert(1)</script>", "Rechnungen"})
    @WithMockUser(roles = "USER")
    void unbekannteDokumentartIst400(String dokumentart) throws Exception {
        mvc.perform(post("/api/textbausteine").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mitDokumentart(dokumentart.replace("\"", "\\\""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unbekannte Dokumentart: " + dokumentart));
        verify(repository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void leererOderUeberlangerNameIst400() throws Exception {
        mvc.perform(post("/api/textbausteine").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"typ\":\"FREITEXT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("name"));
        mvc.perform(post("/api/textbausteine").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(10_001) + "\",\"typ\":\"FREITEXT\"}"))
                .andExpect(status().isBadRequest());
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MAX_VALUE})
    @WithMockUser(roles = "USER")
    void unbekannteIdIst404(long id) throws Exception {
        when(repository.findById(id)).thenReturn(Optional.empty());

        mvc.perform(put("/api/textbausteine/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Diesen Textbaustein gibt es nicht mehr."));
        verify(repository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void nichtNumerischeIdIst400() throws Exception {
        mvc.perform(put("/api/textbausteine/abc").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(GUELTIG))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    @WithMockUser(roles = "USER")
    void loeschenMitCsrf() throws Exception {
        mvc.perform(delete("/api/textbausteine/5").with(csrf())).andExpect(status().isNoContent());
        verify(repository).deleteById(5L);
    }
}
