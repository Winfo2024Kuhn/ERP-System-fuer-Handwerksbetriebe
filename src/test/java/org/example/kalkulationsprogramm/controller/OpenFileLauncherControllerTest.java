package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.config.ZeiterfassungSecurityFilter;
import org.example.kalkulationsprogramm.controller.advice.RestExceptionHandler;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.openfile.OpenFileLauncherService;
import org.example.kalkulationsprogramm.dto.OpenFileLauncherPaketDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Läuft durch die echten Security-Ketten: Die Einrichtungs-Dateien gibt es nur für Admins,
 * die Update-Abfrage des Launchers auch ohne Anmeldung (nur lesend).
 */
@WebMvcTest(controllers = OpenFileLauncherController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({SecurityConfig.class, RestExceptionHandler.class, OpenFileLauncherControllerTest.FilterBeans.class})
class OpenFileLauncherControllerTest {

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    private static final String ADRESSE = "https://localhost";

    @Autowired MockMvc mvc;
    @MockBean OpenFileLauncherService service;
    @MockBean FrontendUserDetailsService users;
    @MockBean MitarbeiterRepository mitarbeiter;

    @Test
    void windowsEinrichtungAlsDownload() throws Exception {
        when(service.einrichtungWindows(ADRESSE, "localhost")).thenReturn("@echo off\r\n".getBytes());

        mvc.perform(get("/api/openfile-launcher/einrichtung/windows").param("adresse", ADRESSE)
                        .with(user("chef").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ERP-Dateien-oeffnen-Windows.bat\""))
                .andExpect(header().string("Content-Type", "application/octet-stream"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().bytes("@echo off\r\n".getBytes()));
    }

    @Test
    void macEinrichtungAlsZip() throws Exception {
        when(service.einrichtungMac(ADRESSE, "localhost")).thenReturn(new byte[] {'P', 'K'});

        mvc.perform(get("/api/openfile-launcher/einrichtung/mac").param("adresse", ADRESSE)
                        .with(user("chef").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"ERP-Dateien-oeffnen-Mac.zip\""))
                .andExpect(header().string("Content-Type", "application/zip"));
    }

    @Test
    void einrichtungOhneAnmeldungIst401() throws Exception {
        mvc.perform(get("/api/openfile-launcher/einrichtung/windows").param("adresse", ADRESSE))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/openfile-launcher/einrichtung/mac").param("adresse", ADRESSE))
                .andExpect(status().isUnauthorized());
        verify(service, never()).einrichtungWindows(anyString(), anyString());
    }

    @Test
    void normalerBenutzerBekommtKeineEinrichtung() throws Exception {
        mvc.perform(get("/api/openfile-launcher/einrichtung/windows").param("adresse", ADRESSE)
                        .with(user("max.mustermann").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/openfile-launcher/einrichtung/mac").param("adresse", ADRESSE)
                        .with(user("max.mustermann").roles("USER")))
                .andExpect(status().isForbidden());
        verify(service, never()).einrichtungMac(anyString(), anyString());
    }

    @Test
    void fehlendeNetzwerkAdresseKommtAlsVerstaendliche400() throws Exception {
        when(service.einrichtungWindows(anyString(), anyString())).thenThrow(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Bitte zuerst die Netzwerk-Adresse des Datei-Ordners eintragen und speichern."));

        mvc.perform(get("/api/openfile-launcher/einrichtung/windows").param("adresse", "'; DROP TABLE x; --")
                        .with(user("chef").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Bitte zuerst die Netzwerk-Adresse des Datei-Ordners eintragen und speichern."));
    }

    @Test
    void ohneAdresseIst400() throws Exception {
        mvc.perform(get("/api/openfile-launcher/einrichtung/mac").with(user("chef").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        verify(service, never()).einrichtungMac(any(), any());
    }

    @Test
    void updateOhneAnmeldungLiefertPaket() throws Exception {
        when(service.aktuellesPaket("windows", "alt", ADRESSE, "localhost"))
                .thenReturn(Optional.of(new OpenFileLauncherPaketDto("abc123", "param()", "{}", "c2ln")));

        mvc.perform(get("/openfile-launcher/aktuell")
                        .param("system", "windows").param("version", "alt").param("adresse", ADRESSE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.version").value("abc123"))
                .andExpect(jsonPath("$.skript").value("param()"))
                .andExpect(jsonPath("$.configJson").value("{}"))
                .andExpect(jsonPath("$.signatur").value("c2ln"));
    }

    @Test
    void aktuellerLauncherBekommt204() throws Exception {
        when(service.aktuellesPaket(eq("mac"), isNull(), eq(ADRESSE), eq("localhost"))).thenReturn(Optional.empty());

        mvc.perform(get("/openfile-launcher/aktuell").param("system", "mac").param("adresse", ADRESSE))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateIstNurLesend() throws Exception {
        mvc.perform(post("/openfile-launcher/aktuell").param("system", "mac").param("adresse", ADRESSE))
                .andExpect(status().isMethodNotAllowed());
    }
}
