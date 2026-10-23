package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.mapper.ArbeitsgangMapper;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.AbteilungService;
import org.example.kalkulationsprogramm.service.ArbeitsgangManagementService;
import org.example.kalkulationsprogramm.testsupport.SitzungsRechteImTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

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

/**
 * Abteilungen und Arbeitsgänge: lesen dürfen alle Angemeldeten, anlegen/ändern/löschen nur Admins.
 * Mit echtem {@link AbteilungService}, damit die Meldungen bis in die JSON-Antwort geprüft werden.
 */
@WebMvcTest(controllers = {AbteilungController.class, ArbeitsgangController.class})
@Import({SecurityConfig.class, AbteilungService.class, AbteilungControllerSecurityTest.Filters.class})
class AbteilungControllerSecurityTest {

    @TestConfiguration
    static class Filters {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @Autowired MockMvc mvc;
    @MockitoBean FrontendUserDetailsService frontendUserDetailsService;
    @MockitoBean AbteilungRepository abteilungen;
    @MockitoBean MitarbeiterRepository mitarbeiter;
    @MockitoBean ArbeitsgangRepository arbeitsgangRepository;
    @MockitoBean AbteilungDokumentBerechtigungRepository dokumentRechte;
    @MockitoBean ArbeitsgangManagementService arbeitsgangService;
    @MockitoBean ArbeitsgangMapper arbeitsgangMapper;

    private static final String NAME_MONTAGE = "{\"name\":\"Montage\"}";

    @BeforeEach
    void sitzungsRechteUnveraendert() {
        SitzungsRechteImTest.rechteUnveraendert(frontendUserDetailsService);
    }

    private static Abteilung abteilung(long id, String name) {
        Abteilung a = new Abteilung();
        a.setId(id);
        a.setName(name);
        return a;
    }

    @Test
    void ohneAnmeldungKeinZugriff() throws Exception {
        mvc.perform(get("/api/abteilungen")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/abteilungen").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(NAME_MONTAGE))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/arbeitsgaenge").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(abteilungen, arbeitsgangService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void benutzerDarfLesenAberNichtsAendern() throws Exception {
        when(abteilungen.findAll()).thenReturn(List.of(abteilung(1, "Montage")));
        mvc.perform(get("/api/abteilungen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Montage"));
        mvc.perform(get("/api/arbeitsgaenge")).andExpect(status().isOk());

        mvc.perform(post("/api/abteilungen").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(NAME_MONTAGE))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/abteilungen/1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(NAME_MONTAGE))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/abteilungen/1").with(csrf())).andExpect(status().isForbidden());

        mvc.perform(post("/api/arbeitsgaenge").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"beschreibung\":\"Montage Geländer\",\"abteilungId\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/arbeitsgaenge/7").with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/arbeitsgaenge/7/stundensatz").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stundensatz\":65}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/arbeitsgaenge/stundensaetze").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isForbidden());

        verify(abteilungen, never()).save(any());
        verify(abteilungen, never()).delete(any());
        verify(arbeitsgangService, never()).erstelleArbeitsgang(any());
        verify(arbeitsgangService, never()).loescheArbeitsgang(any());
        verify(arbeitsgangService, never()).aktualisiereEinzelnenStundensatz(any(), any());
        verify(arbeitsgangService, never()).aktualisiereStundensaetze(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void csrfIstAuchFuerAdminPflicht() throws Exception {
        mvc.perform(post("/api/abteilungen").contentType(MediaType.APPLICATION_JSON).content(NAME_MONTAGE))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/abteilungen/1").contentType(MediaType.APPLICATION_JSON).content(NAME_MONTAGE))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/abteilungen/1")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/arbeitsgaenge/7")).andExpect(status().isForbidden());
        verifyNoInteractions(abteilungen, arbeitsgangService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminLegtAnBenenntUmUndLoescht() throws Exception {
        when(abteilungen.save(any(Abteilung.class))).thenAnswer(aufruf -> {
            Abteilung a = aufruf.getArgument(0);
            if (a.getId() == null) a.setId(5L);
            return a;
        });
        mvc.perform(post("/api/abteilungen").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  Montage \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Montage"));

        when(abteilungen.findById(5L)).thenReturn(Optional.of(abteilung(5, "Montage")));
        mvc.perform(put("/api/abteilungen/5").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Baustelle\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Baustelle"));

        mvc.perform(delete("/api/abteilungen/5").with(csrf())).andExpect(status().isNoContent());
        verify(abteilungen).delete(any(Abteilung.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminDarfArbeitsgaengePflegen() throws Exception {
        mvc.perform(delete("/api/arbeitsgaenge/7").with(csrf())).andExpect(status().isNoContent());
        verify(arbeitsgangService).loescheArbeitsgang(7L);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void fehlermeldungenKommenAlsVerstaendlicherSatz() throws Exception {
        mvc.perform(post("/api/abteilungen").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bitte einen Namen für die Abteilung eingeben."));

        String zuLang = "x".repeat(10_001);
        mvc.perform(post("/api/abteilungen").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + zuLang + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Der Name ist zu lang – höchstens 100 Zeichen."));

        when(abteilungen.existsByNameIgnoreCase("Montage")).thenReturn(true);
        mvc.perform(post("/api/abteilungen").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(NAME_MONTAGE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Eine Abteilung „Montage“ gibt es schon."));

        when(abteilungen.findById(4L)).thenReturn(Optional.of(abteilung(4, "Montage")));
        when(mitarbeiter.countByAbteilungen_Id(4L)).thenReturn(2L);
        mvc.perform(delete("/api/abteilungen/4").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "„Montage“ kann nicht gelöscht werden: 2 Mitarbeiter sind noch zugeordnet. "
                                + "Bitte zuerst beim Mitarbeiter (auch bei ausgeschiedenen) eine andere Abteilung wählen."));
        verify(abteilungen, never()).delete(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "9223372036854775807"})
    @WithMockUser(roles = "ADMIN")
    void ungueltigeIdsSind404(String id) throws Exception {
        mvc.perform(put("/api/abteilungen/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(NAME_MONTAGE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Diese Abteilung gibt es nicht mehr."));
        mvc.perform(delete("/api/abteilungen/" + id).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void sqlUndSkriptImNamenWerdenNurAlsTextGespeichert() throws Exception {
        when(abteilungen.save(any(Abteilung.class))).thenAnswer(aufruf -> aufruf.getArgument(0));
        mvc.perform(post("/api/abteilungen").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"'; DROP TABLE abteilung; --\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("'; DROP TABLE abteilung; --"));
        mvc.perform(post("/api/abteilungen").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"<script>alert(1)</script>\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("<script>alert(1)</script>"));
    }
}
