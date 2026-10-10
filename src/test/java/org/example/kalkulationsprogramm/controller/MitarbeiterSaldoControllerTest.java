package org.example.kalkulationsprogramm.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.config.CloudflareAccessJwtFilter;
import org.example.kalkulationsprogramm.config.FrontendUserDetailsService;
import org.example.kalkulationsprogramm.config.SecurityConfig;
import org.example.kalkulationsprogramm.config.ZeiterfassungSecurityFilter;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.ZeiterfassungApiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Büro-Saldo über die Mitarbeiter-ID: nur mit Büro-Anmeldung, nie mit einem Handy-Code. */
@WebMvcTest(controllers = MitarbeiterSaldoController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ZeiterfassungSecurityFilter.class))
@Import({ SecurityConfig.class, MitarbeiterSaldoControllerTest.FilterBeans.class })
class MitarbeiterSaldoControllerTest {

    private static final String TOKEN = "12345678-1234-4234-8234-123456789abc";

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @Autowired MockMvc mvc;
    @MockBean ZeiterfassungApiService service;
    @MockBean FrontendUserDetailsService users;
    @MockBean MitarbeiterRepository mitarbeiter;

    @Test
    void ohneAnmeldung401() throws Exception {
        mvc.perform(get("/api/zeitverwaltung/mitarbeiter/1/saldo")).andExpect(status().isUnauthorized());
        verify(service, never()).getSaldoFuerMitarbeiter(anyLong(), any());
    }

    @Test
    void handyCodeReichtNicht() throws Exception {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(7L);
        m.setAktiv(true);
        when(mitarbeiter.findByLoginTokenAndAktivTrue(TOKEN)).thenReturn(Optional.of(m));
        mvc.perform(get("/api/zeitverwaltung/mitarbeiter/1/saldo").header("X-Auth-Token", TOKEN))
                .andExpect(status().isUnauthorized());
        verify(service, never()).getSaldoFuerMitarbeiter(anyLong(), any());
    }

    @Test
    @WithMockUser
    void bueroBekommtSaldo() throws Exception {
        when(service.getSaldoFuerMitarbeiter(1L, 2025)).thenReturn(Map.of("gesamt", Map.of("saldo", 3)));
        mvc.perform(get("/api/zeitverwaltung/mitarbeiter/1/saldo").param("jahr", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gesamt.saldo").value(3));
    }

    @Test
    @WithMockUser
    void ungueltigeIds() throws Exception {
        mvc.perform(get("/api/zeitverwaltung/mitarbeiter/0/saldo")).andExpect(status().isNotFound());
        mvc.perform(get("/api/zeitverwaltung/mitarbeiter/-1/saldo")).andExpect(status().isNotFound());
        mvc.perform(get("/api/zeitverwaltung/mitarbeiter/abc/saldo")).andExpect(status().isBadRequest());
        verify(service, never()).getSaldoFuerMitarbeiter(anyLong(), any());
    }
}
