package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.example.kalkulationsprogramm.dto.OpenFileLauncherBefehlDto;
import org.example.kalkulationsprogramm.dto.OpenFileLauncherPaketDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    @MockitoBean OpenFileLauncherService service;
    @MockitoBean FrontendUserDetailsService users;
    @MockitoBean MitarbeiterRepository mitarbeiter;

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

    private static final String MAC_BEFEHL = "/api/openfile-launcher/einrichtung/mac/befehl";
    private static final String SCHLUESSEL = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcd";

    @Test
    void macBefehlFuerAdmin() throws Exception {
        when(service.einrichtungMacBefehl(ADRESSE, "localhost")).thenReturn(new OpenFileLauncherBefehlDto(
                "/bin/bash -c \"$(curl -fSsL 'https://localhost/openfile-launcher/mac/" + SCHLUESSEL + "')\"", 10));

        mvc.perform(post(MAC_BEFEHL).param("adresse", ADRESSE).with(csrf())
                        .with(user("chef").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.befehl").value(
                        "/bin/bash -c \"$(curl -fSsL 'https://localhost/openfile-launcher/mac/" + SCHLUESSEL + "')\""))
                .andExpect(jsonPath("$.gueltigMinuten").value(10));
    }

    @Test
    void macBefehlOhneCsrfTokenIst403() throws Exception {
        mvc.perform(post(MAC_BEFEHL).param("adresse", ADRESSE).with(user("chef").roles("ADMIN")))
                .andExpect(status().isForbidden());
        verify(service, never()).einrichtungMacBefehl(anyString(), anyString());
    }

    @Test
    void macBefehlOhneAdresseIst400() throws Exception {
        mvc.perform(post(MAC_BEFEHL).with(csrf()).with(user("chef").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        verify(service, never()).einrichtungMacBefehl(any(), any());
    }

    @Test
    void macBefehlMitFremderAdresseKommtAlsVerstaendliche400() throws Exception {
        when(service.einrichtungMacBefehl(anyString(), anyString())).thenThrow(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Die Adresse des ERP passt nicht zu dieser Anfrage."));

        mvc.perform(post(MAC_BEFEHL).param("adresse", "<script>alert(1)</script>").with(csrf())
                        .with(user("chef").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Die Adresse des ERP passt nicht zu dieser Anfrage."));
    }

    @Test
    void einmalLinkLiefertSkriptOhneAnmeldung() throws Exception {
        when(service.einrichtungMac(SCHLUESSEL, "localhost")).thenReturn(Optional.of("#!/bin/bash\necho Fertig!\n"));

        mvc.perform(get("/openfile-launcher/mac/" + SCHLUESSEL))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().string("#!/bin/bash\necho Fertig!\n"));
    }

    @Test
    void abgelaufenerOderUnbekannterLinkIst404() throws Exception {
        when(service.einrichtungMac(anyString(), anyString())).thenReturn(Optional.empty());

        mvc.perform(get("/openfile-launcher/mac/" + SCHLUESSEL))
                .andExpect(status().isNotFound());
        mvc.perform(get("/openfile-launcher/mac/..%2F..%2Fetc%2Fpasswd"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void einmalLinkIstNurLesend() throws Exception {
        mvc.perform(post("/openfile-launcher/mac/" + SCHLUESSEL))
                .andExpect(status().isMethodNotAllowed());
        verify(service, never()).einrichtungMac(anyString(), anyString());
    }

    @Test
    void alterZipDownloadGibtEsNichtMehr() throws Exception {
        mvc.perform(get("/api/openfile-launcher/einrichtung/mac").param("adresse", ADRESSE)
                        .with(user("chef").roles("ADMIN")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void einrichtungOhneAnmeldungIst401() throws Exception {
        mvc.perform(get("/api/openfile-launcher/einrichtung/windows").param("adresse", ADRESSE))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(MAC_BEFEHL).param("adresse", ADRESSE).with(csrf()))
                .andExpect(status().isUnauthorized());
        verify(service, never()).einrichtungWindows(anyString(), anyString());
        verify(service, never()).einrichtungMacBefehl(anyString(), anyString());
    }

    @Test
    void normalerBenutzerBekommtKeineEinrichtung() throws Exception {
        mvc.perform(get("/api/openfile-launcher/einrichtung/windows").param("adresse", ADRESSE)
                        .with(user("max.mustermann").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(post(MAC_BEFEHL).param("adresse", ADRESSE).with(csrf())
                        .with(user("max.mustermann").roles("USER")))
                .andExpect(status().isForbidden());
        verify(service, never()).einrichtungMacBefehl(anyString(), anyString());
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
        mvc.perform(get("/api/openfile-launcher/einrichtung/windows").with(user("chef").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        verify(service, never()).einrichtungWindows(any(), any());
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
