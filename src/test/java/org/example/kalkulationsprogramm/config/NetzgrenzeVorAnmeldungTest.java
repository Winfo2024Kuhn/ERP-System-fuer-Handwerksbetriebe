package org.example.kalkulationsprogramm.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Die Netzgrenze muss vor Spring Security laufen: Der Login-Filter beantwortet
 * {@code POST /api/auth/login} selbst. Über einen Tunnel auf localhost darf niemand
 * Passwörter durchprobieren.
 */
@WebMvcTest(controllers = NetzgrenzeVorAnmeldungTest.Leer.class)
@Import({ SecurityConfig.class, ZeiterfassungSecurityFilter.class, NetzgrenzeVorAnmeldungTest.FilterBeans.class,
        NetzgrenzeVorAnmeldungTest.Leer.class })
class NetzgrenzeVorAnmeldungTest {

    /** Kein Controller nötig: Die Anmeldung beantwortet Spring Security selbst. */
    @org.springframework.web.bind.annotation.RestController
    static class Leer {
    }

    @TestConfiguration
    static class FilterBeans {
        @Bean CloudflareAccessJwtFilter cloudflareAccessJwtFilter() { return new CloudflareAccessJwtFilter(); }
    }

    @Autowired MockMvc mvc;
    @MockBean FrontendUserDetailsService users;
    @MockBean MitarbeiterRepository mitarbeiter;

    @Test
    void anmeldungUeberTunnelWirdVorDerPasswortpruefungAbgewiesen() throws Exception {
        mvc.perform(post("/api/auth/login")
                .with(r -> { r.setRemoteAddr("127.0.0.1"); return r; })
                .header("CF-Connecting-IP", "203.0.113.8")
                .param("username", "max.mustermann").param("password", "falsch"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @Test
    void anmeldungAusDemBueroErreichtDiePasswortpruefung() throws Exception {
        org.mockito.Mockito.when(users.loadUserByUsername(anyString()))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("unbekannt"));
        mvc.perform(post("/api/auth/login")
                .with(r -> { r.setRemoteAddr("192.168.1.20"); return r; })
                .param("username", "max.mustermann").param("password", "falsch"))
                .andExpect(status().isUnauthorized());
    }
}
